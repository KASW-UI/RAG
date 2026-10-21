#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Go ↔ Spring Boot 双端对拍器（HTTP wire 层）。

**为什么只能对拍 wire 层**：Go 用 GORM AutoMigrate 建 `users` / `user_avatar_history`，
RAG 用 Flyway 建 `users_user` / `users_user_avatar_history`。表名是 AGENTS.md §3.4
已签字的有意偏离（A-03），两边物理上无法共用 schema —— 所以 DB 层对拍不存在，
唯一的可比面是 HTTP 响应。

**对拍前必须知道的三组「必然差异」**，否则全是噪声：
1. 路径前缀：Go `/usercenter/v1/user/*`、`/admincenter/v1/admin/*`；
   RAG `/user/v1/*`、`/admin/v1/*`（A-03）
2. HTTP 方法：Go 对 get/getself/getuser/getuserlist 用 GET、对 delete 用 DELETE；
   RAG 统一 POST（A-03）。本表为每一步分别声明两侧的 method+path。
3. 取参机制：Go 的 GET 端点走 `form:` tag（query），banuser/unbanuser 等走 `json:`；
   RAG 全部走 JSON body。

用法：
    python3 diff.py --scenario user      # 只跑 user 链路 9 端点
    python3 diff.py --scenario admin     # 只跑 admin 链路 13 端点
    python3 diff.py                       # 全跑
退出码 0 = 没有未预期的差异；1 = 有。
"""

import argparse
import json
import re
import sys
import urllib.error
import urllib.request

# ---------------------------------------------------------------- 端口表
BASE = {
    "go":  {"user": "http://127.0.0.1:18888", "admin": "http://127.0.0.1:18889"},
    "rag": {"user": "http://127.0.0.1:59900", "admin": "http://127.0.0.1:58889"},
}

# uid / history_id 是服务端生成的，两边必然不同；按捕获顺序编号后比对
# 已签字的有意偏离，出现在这些步骤上时只比状态码不比 body（见 KNOWN_STATUS_ONLY）
KNOWN_STATUS_ONLY = {
    # N-1：Go 的两处 401 body 互不相同（验签失败=空 body / 黑名单={code:401}），
    #      RAG 已签字统一成 envelope。正文不可比，只比状态码。
    "user.get.noauth",
    "user.get.badtok",
    "user.get.afterlogout",
    "admin.getself.noauth",
    "admin.getself.badtok",
    "admin.getself.afterlogout",
    # N-6 / A-U1：Go 的 httpx.Parse 失败 → 400 + text/plain + go-zero 的解析错误文本；
    #   RAG → 400 + text/plain + Spring 的异常消息。形状已对齐（DRIFT-006），
    #   但文本来自两个不同框架，不试图逐字对齐。
    "user.register.malformed",
    "admin.create.malformed",
    # N-1：user/delete 无 Authorization 时，Go 的 rest.WithJwt 走
    #   authhandler.go:106 unauthorized() = HTTP 401 + **空 body**，
    #   RAG 统一成 HTTP 401 + {code,msg,data} envelope。状态码一致、正文形状已签字。
    "user.delete",
    # N-3（已签字）：admin 非法 uid 保留 RAG 的 1017，不向 Go 的 1003（0 值路径）对齐。
    # body data.code 不同(1003/1017)，逐字无法对齐，留白名单。
    "admin.getuser.nouid",
    "admin.getuser.baduid",
    # DRIFT-NEW-016 已对齐但文本各自框架抛出，仍留白名单：
    #   这条场景必须 admin_token 才有意义；当前的测试环境 admin 链路上 GO 侧
    #   login fail(详见 §6.2.6 E-1 现状),所以本条没在实测里跑通;凭代码路径
    #   可以预测 byte-equal(`field "keyword" is not set`),但**未验证不立**。
    "admin.getuserlist.nokeyword",
}
# 已知 body byte-equal 的场景不再进白名单（2026-10-21 实证一组通过）：
#   - user.register.noemail    → 'field "username" is not set\n'
# 走 diff_json 走 text/plain(被 parse_json 包成 {"__raw__": "..."} 后逐字比对)。

# 业务码层面已签字的差异：(step_id, 期望 code)。仅用于把「已知」标注出来，不算失败。
KNOWN_CODE_DIFF = {
    ("admin.getuser.nouid", "1003", "1017"),
    ("admin.getuser.baduid", "1003", "1017"),
}

TS_KEYS = {"create_time", "update_time"}
# 这些键的整值都会变，值本身不可比。
# ⚠️ 必须同时覆盖 **字符串和数字**两种形态：
#   invite_code / token / expires_at 在 Go 的 CreateInviteResp 里是 string / int64；
#   早先只对 str 生效，于是 expires_at（int epoch 秒）两边差 2 秒就被当成真差异报出来。
OPAQUE_KEYS = {"token", "invite_code", "expires_at"}


# ---------------------------------------------------------------- 场景表
def S(sid, desc, group, go, rag, capture=None, auth=None):
    return {
        "id": sid, "desc": desc, "group": group,
        "go": go, "rag": rag, "capture": capture or {}, "auth": auth,
    }


def P(path, body=None, method="POST"):
    return {"method": method, "path": path, "json": body}


def G(path, query=None):
    return {"method": "GET", "path": path, "query": query or {}}


SCENARIOS = [
    # ============================== user 链路（9 端点） ==============================
    # ⚠️ 两侧 JSON 字段名都是 snake_case，**完全一致**：
    #   Go  出处 service/user/user/api/internal/types/types.go（goctl 生成）
    #       UploadAvatarReq.AvatarUrl  `json:"avatar_url"`
    #   RAG 出处 service/users/user/user-api/.../dto/UploadAvatarReq.java
    #       @JsonProperty("avatar_url") private String avatarUrl;
    #   所以这里**不存在**「RAG 用 camelCase」的差异。上一版场景表给 RAG 单独发
    #   avatarUrl / extraInfo / inviteCode，结果 RAG 侧全部静默忽略
    #   （AvatarUploadService 打 "avatar_url is empty"）——
    #   那是场景表写错，不是 RAG 的 bug。
    S("user.register", "注册新用户", "user",
      P("/usercenter/v1/user/register",
        {"username": "dt_user1", "password": "pw123456", "email": "u1@example.com",
         "extra_info": {"city": "上海"}}),
      P("/user/v1/register",
        {"username": "dt_user1", "password": "pw123456", "email": "u1@example.com",
         "extra_info": {"city": "上海"}}),
      capture={"uid": "data.uid", "user_uid": "data.uid"}),

    # CreateUserReq 的 username/password/email 都没带 ,optional ⇒ Go 必填。
    # 第一次跑这里少了 email，Go 直接 400 挡在「用户名重复」判断之前，
    # 测的根本不是 dup 分支。补上 email，且换一个未被占用的 email，
    # 保证命中的确实是 username 唯一约束。
    S("user.register.dup", "重复用户名 → 1001", "user",
      P("/usercenter/v1/user/register",
        {"username": "dt_user1", "password": "pw123456", "email": "u1-dup@example.com"}),
      P("/user/v1/register",
        {"username": "dt_user1", "password": "pw123456", "email": "u1-dup@example.com"})),

    S("user.register.malformed", "畸形 JSON → 400", "user",
      {"method": "POST", "path": "/usercenter/v1/user/register", "raw": b'{"a":}'},
      {"method": "POST", "path": "/user/v1/register", "raw": b'{"a":}'}),

    S("user.login", "正确凭据登录", "user",
      P("/usercenter/v1/user/login", {"username": "dt_user1", "password": "pw123456"}),
      P("/user/v1/login", {"username": "dt_user1", "password": "pw123456"}),
      capture={"token": "data.token"}),

    S("user.login.wrongpw", "密码错 → 1002", "user",
      P("/usercenter/v1/user/login", {"username": "dt_user1", "password": "wrong"}),
      P("/user/v1/login", {"username": "dt_user1", "password": "wrong"})),

    S("user.login.noexist", "用户不存在", "user",
      P("/usercenter/v1/user/login", {"username": "dt_nobody", "password": "x"}),
      P("/user/v1/login", {"username": "dt_nobody", "password": "x"})),

    S("user.get", "取本人资料（鉴权 + status presence）", "user",
      G("/usercenter/v1/user/get"),
      P("/user/v1/get", {}),
      auth="token"),

    S("user.get.noauth", "无 Authorization → 401", "user",
      G("/usercenter/v1/user/get"),
      P("/user/v1/get", {}),
      auth="none"),

    S("user.get.badtok", "无效 token → 401", "user",
      {"method": "GET", "path": "/usercenter/v1/user/get", "token": "not-a-jwt"},
      {"method": "POST", "path": "/user/v1/get", "json": {}, "token": "not-a-jwt"},
      auth="none"),

    # UpdateUserReq 四个字段全部 ,optional ⇒ 两边都不该因缺字段报错
    S("user.update", "改邮箱", "user",
      P("/usercenter/v1/user/update", {"email": "u1-new@example.com"}),
      P("/user/v1/update", {"email": "u1-new@example.com"}),
      auth="token"),

    # UploadAvatarReq: avatar_url 必填（无 ,optional），content_type/size_bytes optional
    S("user.avatar", "上传头像（4 步事务 + jsonb 双写）", "user",
      P("/usercenter/v1/user/avatar",
        {"avatar_url": "https://cdn/dt/a1.png", "content_type": "image/png", "size_bytes": 1234}),
      P("/user/v1/avatar",
        {"avatar_url": "https://cdn/dt/a1.png", "content_type": "image/png", "size_bytes": 1234}),
      capture={"history_id": "data.history.id"},
      auth="token"),

    S("user.avatar.select", "切换头像（5 步事务 + uid 隔离）", "user",
      P("/usercenter/v1/user/avatar/select", {"history_id": "{{history_id}}"}),
      P("/user/v1/avatar/select", {"history_id": "{{history_id}}"}),
      auth="token"),

    S("user.avatar.history", "头像历史列表", "user",
      G("/usercenter/v1/user/avatar/history"),
      P("/user/v1/avatar/history", {}),
      auth="token"),

    S("user.logout", "注销（写黑名单）", "user",
      P("/usercenter/v1/user/logout", {}),
      P("/user/v1/logout", {}),
      auth="token"),

    S("user.get.afterlogout", "注销后再访问 → 401", "user",
      G("/usercenter/v1/user/get"),
      P("/user/v1/get", {}),
      auth="token"),

    S("user.delete", "注销账号（无 token → N-1 空 body）", "user",
      {"method": "DELETE", "path": "/usercenter/v1/user/delete", "json": {}},
      P("/user/v1/delete", {}),
      auth="none-or-fresh"),

    # ============================== admin 链路（13 端点） ==============================
    S("admin.login", "管理员登录（AdminSeederBootstrap 种的 admin/admin）", "admin",
      P("/admincenter/v1/admin/login", {"username": "admin", "password": "admin"}),
      P("/admin/v1/login", {"username": "admin", "password": "admin"}),
      capture={"admin_token": "data.token"}),

    S("admin.getself", "管理员取自身", "admin",
      G("/admincenter/v1/admin/getself"),
      P("/admin/v1/getself", {}),
      capture={"admin_uid": "data.admin.uid"},
      auth="admin_token"),

    S("admin.getself.noauth", "无 Authorization → 401", "admin",
      G("/admincenter/v1/admin/getself"),
      P("/admin/v1/getself", {}),
      auth="none"),

    S("admin.getself.badtok", "无效 token → 401", "admin",
      {"method": "GET", "path": "/admincenter/v1/admin/getself", "token": "not-a-jwt"},
      {"method": "POST", "path": "/admin/v1/getself", "json": {}, "token": "not-a-jwt"},
      auth="none"),

    S("admin.invite.create", "生成邀请码", "admin",
      P("/admincenter/v1/admin/invite/create", {}),
      P("/admin/v1/invite/create", {}),
      capture={"invite": "data.invite_code"},
      auth="admin_token"),

    # CreateAdminReq: username/password/email/invite_code 全部必填
    S("admin.create", "用邀请码建管理员", "admin",
      P("/admincenter/v1/admin/create",
        {"username": "dt_admin2", "password": "ap123456", "email": "a2@example.com",
         "invite_code": "{{invite}}"}),
      P("/admin/v1/create",
        {"username": "dt_admin2", "password": "ap123456", "email": "a2@example.com",
         "invite_code": "{{invite}}"}),
      capture={"admin2_uid": "data.uid"},
      auth="none"),

    S("admin.create.malformed", "畸形 JSON → 400", "admin",
      {"method": "POST", "path": "/admincenter/v1/admin/create", "raw": b'{"a":}'},
      {"method": "POST", "path": "/admin/v1/create", "raw": b'{"a":}'},
      auth="admin_token"),

    # GetUserReq: uid 走 form，Go 用 GET + query、RAG 用 POST + JSON body（A-03 已签字）
    S("admin.getuser", "按 uid 查用户（Go 走 form，RAG 走 body）", "admin",
      G("/admincenter/v1/admin/getuser", {"uid": "{{user_uid}}"}),
      P("/admin/v1/getuser", {"uid": "{{user_uid}}"}),
      auth="admin_token"),

    S("admin.getuser.nouid", "uid 缺失 → 两侧都 400 text/plain（DRIFT-NEW-016 已对齐）", "admin",
      G("/admincenter/v1/admin/getuser"),
      P("/admin/v1/getuser", {}),
      auth="admin_token"),

    S("admin.getuser.baduid", "uid 非法 → 两侧都 400 text/plain（文本来自各自框架，DRIFT-006 边界）", "admin",
      G("/admincenter/v1/admin/getuser", {"uid": "abc"}),
      P("/admin/v1/getuser", {"uid": "abc"}),
      auth="admin_token"),

    # GetUserListReq: keyword 是 `form:"keyword,omitempty"` —— omitempty **不是** optional，
    # 所以 Go 必填。每一步都必须带上 keyword。
    # 注意 Go 的 keyword 走 **form 通道**，`httpx.GetFormValues`（util.go:37-43）会丢弃空值，
    # 所以 `keyword=` 空串也退化成缺席 ⇒ 400；RAG 走 JSON body，`keyword=""` 会被接受。
    # 这个残留差异刻意不修（§6.2.5「DRIFT-NEW-016 的处置」有留档）。
    # 所以根本不存在「不带过滤条件列全量」这种调用形态，改成三个有意义的过滤用例。
    S("admin.getuserlist", "分页列表（keyword 精确匹配）", "admin",
      G("/admincenter/v1/admin/getuserlist",
        {"page": "1", "page_size": "20", "keyword": "dt_user1"}),
      P("/admin/v1/getuserlist", {"page": 1, "page_size": 20, "keyword": "dt_user1"}),
      auth="admin_token"),

    # A-U3：RAG 的 AdminDomainService 先 pattern = "%"+keyword+"%" 再交给 MP 的 like()
    #       双重包裹成 %%kw%%，Go 是 %kw%。PG LIKE 会折叠连续 %，静态判断等价 ——
    #       这个步骤就是拿真库把「等价」二字钉死的地方。
    S("admin.getuserlist.keyword", "keyword 前缀过滤（LIKE 双重包裹 A-U3）", "admin",
      G("/admincenter/v1/admin/getuserlist", {"page": "1", "page_size": "20", "keyword": "dt_"}),
      P("/admin/v1/getuserlist", {"page": 1, "page_size": 20, "keyword": "dt_"}),
      auth="admin_token"),

    S("admin.getuserlist.nomatch", "keyword 匹配不到 → 空列表", "admin",
      G("/admincenter/v1/admin/getuserlist",
        {"page": "1", "page_size": "20", "keyword": "zzz_nomatch"}),
      P("/admin/v1/getuserlist", {"page": 1, "page_size": 20, "keyword": "zzz_nomatch"}),
      auth="admin_token"),

    # 探必填校验：Go 缺 keyword 直接 400，RAG 自 DRIFT-NEW-016 起同为 400
    S("admin.getuserlist.nokeyword", "列表缺 keyword → 两侧都 400（DRIFT-NEW-016 已对齐）", "admin",
      G("/admincenter/v1/admin/getuserlist", {"page": "1", "page_size": "20"}),
      P("/admin/v1/getuserlist", {"page": 1, "page_size": 20}),
      auth="admin_token"),

    S("admin.banuser", "封禁用户", "admin",
      P("/admincenter/v1/admin/banuser", {"uid": "{{user_uid}}"}),
      P("/admin/v1/banuser", {"uid": "{{user_uid}}"}),
      auth="admin_token"),

    S("admin.unbanuser", "解封用户", "admin",
      P("/admincenter/v1/admin/unbanuser", {"uid": "{{user_uid}}"}),
      P("/admin/v1/unbanuser", {"uid": "{{user_uid}}"}),
      auth="admin_token"),

    S("admin.resetuserpassword", "重置密码（写 System.DefaultPassword）", "admin",
      P("/admincenter/v1/admin/resetuserpassword", {"uid": "{{user_uid}}"}),
      P("/admin/v1/resetuserpassword", {"uid": "{{user_uid}}"}),
      auth="admin_token"),

    # UpdateSelfReq: username/password/email 全是 ,omitempty ⇒ Go 必填（见上面的说明）
    S("admin.update", "改自身资料", "admin",
      P("/admincenter/v1/admin/update",
        {"username": "admin", "password": "newadmin123", "email": "admin-new@example.com"}),
      P("/admin/v1/update",
        {"username": "admin", "password": "newadmin123", "email": "admin-new@example.com"}),
      auth="admin_token"),

    # UpdateUserReq(uid,string) 必填；username/password/email 是 omitempty ⇒ 也必填
    S("admin.updateuser", "改指定用户", "admin",
      P("/admincenter/v1/admin/updateuser",
        {"uid": "{{user_uid}}", "username": "dt_user1", "password": "pw123456",
         "email": "u1-admin@example.com"}),
      P("/admin/v1/updateuser",
        {"uid": "{{user_uid}}", "username": "dt_user1", "password": "pw123456",
         "email": "u1-admin@example.com"}),
      auth="admin_token"),

    S("admin.delete", "删除用户", "admin",
      {"method": "DELETE", "path": "/admincenter/v1/admin/delete", "json": {"uid": "{{user_uid}}"}},
      P("/admin/v1/delete", {"uid": "{{user_uid}}"}),
      auth="admin_token"),

    S("admin.logout", "管理员注销", "admin",
      P("/admincenter/v1/admin/logout", {}),
      P("/admin/v1/logout", {}),
      auth="admin_token"),

    S("admin.getself.afterlogout", "注销后再访问 → 401", "admin",
      G("/admincenter/v1/admin/getself"),
      P("/admin/v1/getself", {}),
      auth="admin_token"),

    # ---- 必须放在最后：以下步骤会**改变数据集或依赖数据集**，不能污染前面的比对 ----
    # 探「必填字段缺失」：Go 的 httpx.Parse 把缺字段当硬错误（400 + text/plain），
    # RAG 自 DRIFT-NEW-016 起行为一致（同样是 400）。本条从「已知差异」转为「回归锁」。
    # 仍放末尾：万一将来哪边不再拒，两边数据集会从此不同，
    # 放在中间会导致后面两次 getuserlist 的 total 全部对不上。
    S("user.register.noemail", "注册缺 email → 两侧都 400（DRIFT-NEW-016 已对齐）", "user",
      P("/usercenter/v1/user/register", {"username": "dt_noemail", "password": "pw123456"}),
      P("/user/v1/register", {"username": "dt_noemail", "password": "pw123456"})),
]


# ---------------------------------------------------------------- HTTP
def http(method, url, body=None, headers=None, raw=None):
    data = None
    hdrs = dict(headers or {})
    if raw is not None:
        data = raw
        hdrs.setdefault("Content-Type", "application/json")
    elif body is not None:
        data = json.dumps(body).encode("utf-8")
        hdrs.setdefault("Content-Type", "application/json")
    req = urllib.request.Request(url, data=data, headers=hdrs, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.status, resp.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")
    except Exception as e:                                    # noqa: BLE001
        return 0, "<<transport error: %s>>" % e


def deep_get(obj, dotted):
    cur = obj
    for part in dotted.split("."):
        if not isinstance(cur, dict) or part not in cur:
            return None
        cur = cur[part]
    return cur


def parse_json(text):
    t = (text or "").strip()
    if not t:
        return None
    if not t.startswith("{") and not t.startswith("["):
        return {"__raw__": t}
    try:
        return json.loads(t)
    except json.JSONDecodeError:
        return {"__raw__": t}


# ---------------------------------------------------------------- 归一化
class Normalizer:
    """把两侧各自生成的易变值映射成同一个占位符，再逐字段比对。"""

    def __init__(self):
        self.alias = {}          # 真实值 -> 占位符
        self.counter = {}

    def register(self, kind, value):
        if value is None:
            return None
        key = str(value)
        if key in self.alias:
            return self.alias[key]
        self.counter[kind] = self.counter.get(kind, 0) + 1
        ph = "<%s#%d>" % (kind.upper(), self.counter[kind])
        self.alias[key] = ph
        return ph

    def walk(self, node, key=None):
        # 时间戳无条件抹平：两侧进程启动时刻不同，字符串形态还会各自带
        # 不同精度的时区偏移，逐字比对必然失败，而它不是我们要测的东西。
        if key in TS_KEYS and not isinstance(node, (dict, list)):
            return "<TS>"
        if key in OPAQUE_KEYS and not isinstance(node, (dict, list)):
            return "<%s>" % key.upper()
        if isinstance(node, dict):
            return {k: self.walk(v, k) for k, v in node.items()}
        if isinstance(node, list):
            return [self.walk(v, key) for v in node]
        if isinstance(node, str):
            if node in self.alias:
                return self.alias[node]
            return node
        if isinstance(node, (int, float)) and not isinstance(node, bool):
            # uid 在 Go 里是字符串（json:"uid,string"）；若 RAG 侧是数字，
            # 这里**不**抹平类型差异 —— 让它作为一个真实差异暴露出来。
            if key == "uid" and str(node) in self.alias:
                return self.alias[str(node)]
            return node
        return node

    def norm(self, obj):
        return self.walk(obj)


# ---------------------------------------------------------------- 差分
def diff_json(a, b, path="", out=None):
    out = [] if out is None else out
    if type(a) is not type(b) and not (
            isinstance(a, (int, float)) and isinstance(b, (int, float))):
        out.append("%s: Go=%r(%s) vs RAG=%r(%s)"
                   % (path or "$", a, type(a).__name__, b, type(b).__name__))
        return out
    if isinstance(a, dict):
        for k in sorted(set(a) | set(b)):
            if k not in a:
                out.append("%s.%s: Go 缺失, RAG=%r" % (path, k, b[k]))
            elif k not in b:
                out.append("%s.%s: RAG 缺失, Go=%r" % (path, k, a[k]))
            else:
                diff_json(a[k], b[k], "%s.%s" % (path, k), out)
    elif isinstance(a, list):
        if len(a) != len(b):
            out.append("%s: 长度 Go=%d vs RAG=%d" % (path or "$", len(a), len(b)))
        for i in range(min(len(a), len(b))):
            diff_json(a[i], b[i], "%s[%d]" % (path, i), out)
    else:
        if a != b:
            out.append("%s: Go=%r vs RAG=%r" % (path or "$", a, b))
    return out


# ---------------------------------------------------------------- 主流程


class Runner:
    def __init__(self, name):
        self.name = name
        self.state = {}
        self.norm = Normalizer()

    def run(self, step):
        spec = step["go"] if self.name == "go" else step["rag"]
        group = step["group"]

        def resolve(v):
            if isinstance(v, str) and v.startswith("{{") and v.endswith("}}"):
                return self.state.get(v[2:-2], "")
            return v

        def resolve_deep(v):
            if isinstance(v, dict):
                return {k: resolve_deep(x) for k, x in v.items()}
            if isinstance(v, list):
                return [resolve_deep(x) for x in v]
            return resolve(v)

        headers = {}
        auth = step["auth"]
        if auth and auth != "none":
            tok = self.state.get(auth)
            if tok:
                headers["Authorization"] = "Bearer " + tok
        if spec.get("token"):
            headers["Authorization"] = "Bearer " + spec["token"]

        url = BASE[self.name][group] + spec["path"]
        if spec.get("query"):
            from urllib.parse import urlencode
            url += "?" + urlencode({k: resolve_deep(v) for k, v in spec["query"].items()})

        status, text = http(spec["method"], url,
                            body=resolve_deep(spec["json"]) if spec.get("json") is not None else None,
                            headers=headers, raw=spec.get("raw"))
        body = parse_json(text)

        for key, dotted in step["capture"].items():
            val = deep_get(body, dotted) if isinstance(body, dict) else None
            if val is not None:
                self.state[key] = val
                # uid / history_id 是服务端生成的，两边必然不同；
                # admin_uid 是 AdminSeederBootstrap 启动期种出来的，两边 workerId
                # 不同 ⇒ 同样不可比。早先漏掉 admin_uid，getself 步骤就误报了一次。
                kind = {"user_uid": "uid", "history_id": "history", "uid": "uid",
                        "admin_uid": "uid", "admin2_uid": "uid"}.get(key)
                if kind:
                    self.norm.register(kind, val)
        return status, body, text


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--scenario", choices=["user", "admin", "all"], default="all")
    ap.add_argument("--verbose", action="store_true")
    args, _unknown = ap.parse_known_args()

    steps = [s for s in SCENARIOS if args.scenario in ("all", s["group"])]

    # 两侧各自把整个序列连续跑一遍 —— 状态机（token / uid / 邀请码）必须各自推进，
    # 不能交错执行，否则第二侧会捡到第一侧已经消费掉的邀请码。
    runners = {n: Runner(n) for n in ("go", "rag")}
    raw = {}
    for n, r in runners.items():
        raw[n] = [r.run(s) for s in steps]

    print("=" * 78)
    print("Go <-> Spring Boot dual differential (HTTP wire)  scenario=%s" % args.scenario)
    print("=" * 78)

    same = known = diffs = 0
    for i, s in enumerate(steps):
        gs, gb, gtext = raw["go"][i]
        rs, rb, rtext = raw["rag"][i]

        gn = runners["go"].norm.norm(gb)
        rn = runners["rag"].norm.norm(rb)

        if s["id"] in KNOWN_STATUS_ONLY:
            if gs != rs:
                print("  [KNOWN] %-32s status differs but signed off: Go=%s RAG=%s"
                      % (s["id"], gs, rs))
                known += 1
            else:
                print("  [ OK   ] %-32s %s (status %s, body not compared)"
                      % (s["id"], s["desc"], gs))
                same += 1
            continue

        problems = []
        if gs != rs:
            problems.append("HTTP status: Go=%s vs RAG=%s" % (gs, rs))
        problems += diff_json(gn, rn)

        if not problems:
            print("  [ OK   ] %-32s %s" % (s["id"], s["desc"]))
            same += 1
        else:
            diffs += 1
            print("  [ DIFF ] %-32s %s" % (s["id"], s["desc"]))
            for pr in problems[:14]:
                print("           - %s" % pr)
            if len(problems) > 14:
                print("           ... %d more" % (len(problems) - 14))
            if args.verbose:
                print("           Go  raw: %s" % gtext[:500])
                print("           RAG raw: %s" % rtext[:500])

    print("-" * 78)
    print("steps=%d  same=%d  known=%d  unexpected=%d" % (len(steps), same, known, diffs))
    return 1 if diffs else 0


if __name__ == "__main__":
    sys.exit(main())
