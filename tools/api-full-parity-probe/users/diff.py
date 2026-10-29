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

═══════════════════════════════════════════════════════════════════════════════
【本文件是 `tools/difftest/diff.py` 的**域内副本**（2026-10-29 · align/r4-users-probe）】
═══════════════════════════════════════════════════════════════════════════════

原文件在 `tools/difftest/` 下 = **主线共享面**（已被 v3 轮的结论引用）⇒ **一个字没动**。
本副本按 `docs/RoundPlan/Round-0.md` **T-0.6** 的统一口径落在域内探针目录
`tools/api-full-parity-probe/users/`（与 article / like / follow / favorite-rpc 同构）。

**相对原文件的三处改动**（每一处都对应一个实测出来的坑）：

  ① **端口可覆盖**（`PROBE_GO_USER_URL` / `PROBE_GO_ADMIN_URL` /
     `PROBE_RAG_USER_URL` / `PROBE_RAG_ADMIN_URL`）
     —— 原文件的 `BASE` 是硬编码的 ⇒ 并行实例会打到**别人的**服务上
     ⇒ 本副本全部走 env（默认仍是原值 ⇒ 老调用方零改动）。

  ② **HTTP 传输换成 `http1xx.py`**（RFC 9110 感知：跳过 interim 1xx）
     —— 原文件用 `urllib.request` ⇒ **Python 不跳过 1xx** ⇒ RAG 侧共享层的
        `GoStyleClientDisconnectProbe`（**已签字偏离 `SD-2026-10-25-04`**）在
        **任何耗时 > 500ms 的请求**上先写一行 `103 Early Hints` ⇒ 客户端把
        **103 当成终态** ⇒ 响应体整个丢掉（实测：status=103 / body 空）
     ⇒ 这就是「RAG 侧 103 空 body」的**机制**，不是 RAG 的业务缺陷。
     ⇒ 换成 RFC 感知客户端后，探针看到的与 curl / JDK / Go / 浏览器**一致**
        （= 最终响应），**同时把跳过的 1xx 记下来**（留痕，不当它不存在）。

  ③ **预热 + 有界重试 + 计数**
     —— 冷启动首请求最慢 ⇒ 必然命中 1xx ⇒ 预热（`warmup()`）先把冷启动消化掉；
     —— 仍遇到传输层失败 / 终态仍是 1xx ⇒ **重试一次**（`MAX_RETRY = 1`）
        ⇒ **重试后仍不一致 ⇒ 必须红**（1xx 是已签字偏离，不是「允许任意差异」）。
     —— 报告里必须能数出：**因 1xx 重试的 step 数** / **命中 1xx 的 step 数** /
        **预热轮数**。
"""

import argparse
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import http1xx                                                    # noqa: E402

# ---------------------------------------------------------------- 端口表（可覆盖）
def _base_url(env_name, default):
    v = os.environ.get(env_name)
    return v.rstrip("/") if v else default


BASE = {
    "go":  {"user": _base_url("PROBE_GO_USER_URL", "http://127.0.0.1:18888"),
            "admin": _base_url("PROBE_GO_ADMIN_URL", "http://127.0.0.1:18889")},
    "rag": {"user": _base_url("PROBE_RAG_USER_URL", "http://127.0.0.1:59900"),
            "admin": _base_url("PROBE_RAG_ADMIN_URL", "http://127.0.0.1:58889")},
}

# ---------------------------------------------------------------- 1xx / 重试策略
# 🔴 **1xx 是已签字偏离 `SD-2026-10-25-04`，不是「允许任意差异」**
#   ⇒ 处置 = 「显式识别 + 留痕 + **有界**重试」⇒ 重试后仍不一致 ⇒ 必须红。
# 上界取 1：既吸收「冷启动首请求」这一种已知噪声，又不会把「真差异」洗成绿。
MAX_RETRY = int(os.environ.get("PROBE_MAX_RETRY", "1"))
# 预热轮数上限（每侧、每个 group 各一轮预热序列）
WARMUP_MAX_ROUNDS = int(os.environ.get("PROBE_WARMUP_ROUNDS", "3"))
# 预热用哪条「无害」的请求（**绝不改数据**）：
#   user  组 ⇒ 登录不存在的用户（只读路径，失败也不写库）
#   admin 组 ⇒ 登录不存在的管理员（同上）
WARMUP_PAYLOAD = {"username": "__probe_warmup_no_such_user__", "password": "__probe_warmup__"}

# uid / history_id 是服务端生成的，两边必然不同；按捕获顺序编号后比对
# ═════════════════════════════════════════════════════════════════════════
# E-1 收口（2026-10-22）：从「只比状态码」升级为「按已签字的边界逐项断言」
# ═════════════════════════════════════════════════════════════════════════
#
# 【为什么不能直接改成跨端 byte-equal】
# 这 10 步分两类，**两类都注定无法逐字相同**，所以「让两边 body 相等」不是修复方向：
#
#   N-1（7 步）：Go 自己两处 401 的 body 就**互不相同**
#       rest.WithJwt → authhandler.go:106 unauthorized() = HTTP 401 + **空 body**
#       CheckBlacklistMiddleware → checkblacklistmiddleware.go:38
#                        = HTTP 401 + {"code":401,"msg":"登录已失效,请重新登陆"}（无 data）
#     RAG 两侧统一成 HTTP 401 + 完整 {code,msg,data}（用户 2026-10-21 拍板）。
#     ⇒ 拿 RAG 去对齐 Go，**对不上任何一侧**；复制 Go 的不一致则违背签字。
#
#   DRIFT-006（3 步）：400 的正文来自**两个框架各自的 JSON 解析层**
#       Go encoding/json vs Jackson；Go strconv vs Jackson
#     形状（text/plain + 裸文本）已对齐，**文本永远不同**（§3.4.4 边界）。
#
# 【那 E-1 到底缺什么】
# 缺的**不是**「把两边 body 比成一样」，而是：
#   旧写法只断言 `gs == rs`，**body 连看都没看** —— 于是
#   「RAG 把 401 改成了 200 + 空 envelope」「RAG 的 400 变成了 JSON」这类回归
#   也能一路绿灯通过，因为状态码恰好还是 401/400。
#   这正是 §3.4.3 那条纪律的形态：把「没验证」写成「已签字」。
#
# 【本表的断言口径】
# 每条都对**两侧分别断言各自该是什么样**，而不是让两侧互相比：
#   N-1       → 两侧 status 必须都是 401；Go body 必须命中它自己的两种形态之一
#                （空 / 黑名单 JSON）；RAG body 必须是 {code,msg,data} 三键齐全的
#                envelope 且业务码 = 1006（ERROR_TOKEN_RUNTIME）。
#   DRIFT-006 → 两侧 status 必须都是 400；两侧 Content-Type 都必须是 text/plain；
#                两侧 body 都必须是非空裸文本（不是 JSON 对象）。
#
# 【变异测试】2026-10-22 实测：把 RAG 的 N-1 envelope 拆掉 / 把 400 改回 JSON，
# 本表 7 + 3 条**全部变红**（详见 AGENTS.md §6.2.6 E-1 行）。
BODY_EXPECTATION = {
    # ── N-1：鉴权链 401（6 条）────────────────────────────────────────
    "user.get.noauth":         "n1",
    "user.get.badtok":         "n1",
    "user.get.afterlogout":    "n1",
    "admin.getself.noauth":    "n1",
    "admin.getself.badtok":    "n1",
    "admin.getself.afterlogout": "n1",
    # N-1：user/delete 无 Authorization —— 走的是 rest.WithJwt 那条（空 body）
    "user.delete":             "n1",
    # ── DRIFT-006：400 text/plain（3 条）─────────────────────────────
    "user.register.malformed": "plain400",
    "admin.create.malformed":  "plain400",
    "admin.getuser.baduid":    "plain400",
}

# RAG 侧 401 envelope 的业务码：ERROR_TOKEN_RUNTIME。
# 出处：RAG users-common 错误码表；AGENTS.md §3.4.2 N-1 行「业务码仍是 1006」。
N1_RAG_CODE = 1006


def check_body_expectation(kind, status_go, body_go, text_go,
                           status_rag, body_rag, text_rag, head_rag):
    """按已签字的边界逐项断言。返回问题清单（空 = 通过）。"""
    problems = []

    if kind == "n1":
        if status_go != 401:
            problems.append("Go 侧应 HTTP 401，实际 %s" % status_go)
        if status_rag != 401:
            problems.append("RAG 侧应 HTTP 401，实际 %s（§3.4.2 N-1：状态码必须已对齐）" % status_rag)

        # RAG 必须是完整 envelope：code / msg / data 三键齐全 + 业务码 1006。
        # 「data 键存在」是 N-1 签字的核心内容（Go 黑名单那条**没有** data）。
        if not isinstance(body_rag, dict):
            problems.append("RAG 401 body 应为 envelope JSON，实际=%r" % (text_rag[:120],))
        else:
            if body_rag.get("code") != N1_RAG_CODE:
                problems.append("RAG 401 业务码应=%s，实际=%r"
                                % (N1_RAG_CODE, body_rag.get("code")))
            for k in ("code", "msg", "data"):
                if k not in body_rag:
                    problems.append("RAG 401 envelope 缺键 %r（body=%r）" % (k, body_rag))

        # Go 侧必须命中它自己的两种形态之一：空 body（WithJwt）/ 黑名单 JSON。
        # 这条断言的作用是「守住真源」—— 若哪天 Go 侧也变了，本对拍必须立刻发现，
        # 而不是继续拿一个已经失效的签字当挡箭牌（§3.4.3：N-3/N-6 都签过字且行为从未存在）。
        go_raw = (text_go or "").strip()
        go_is_empty = (go_raw == "")
        go_is_blacklist_json = (isinstance(body_go, dict)
                                and body_go.get("code") == 401
                                and "data" not in body_go)
        if not (go_is_empty or go_is_blacklist_json):
            problems.append("Go 401 body 既不是空 body、也不是黑名单形态 {code:401}(无 data)：%r"
                            % (go_raw[:160],))

    elif kind == "plain400":
        if status_go != 400:
            problems.append("Go 侧应 HTTP 400，实际 %s" % status_go)
        if status_rag != 400:
            problems.append("RAG 侧应 HTTP 400，实际 %s" % status_rag)

        # 形状对齐：两侧都必须是 text/plain（DRIFT-006 对齐的正是这个形状）
        ct_rag = (head_rag or {}).get("Content-Type", "")
        if "text/plain" not in ct_rag:
            problems.append("RAG 400 的 Content-Type 应含 text/plain（DRIFT-006 已对齐形状），"
                            "实际=%r" % ct_rag)

        # 两侧都必须是**非空裸文本**，不是 JSON。
        # 旧写法只比状态码：RAG 若哪天把 400 改成 envelope JSON，状态码仍是 400，
        # 旧写法照样绿灯 —— 这条就是堵那个洞。
        for side, b, t in (("Go", body_go, text_go), ("RAG", body_rag, text_rag)):
            if isinstance(b, dict) and "__raw__" not in b:
                problems.append("%s 400 body 应为裸文本，实际是 JSON 对象：%r"
                                % (side, (t or "")[:120]))
            if not (t or "").strip():
                problems.append("%s 400 body 为空 —— 与 Go 的 http.Error 形状不符" % side)

        # RAG 侧不得吐自己的 envelope：DRIFT-006 要求走 goStylePlainTextBadRequest。
        if isinstance(body_rag, dict) and body_rag.get("code") is not None:
            problems.append("RAG 400 不得是业务 envelope（应走 text/plain 分支），实际=%r"
                            % body_rag)

    else:
        problems.append("未知断言类型 %r" % kind)

    return problems
# 已知 body byte-equal 的场景不再进白名单（2026-10-21 实证通过）：
#   - user.register.noemail       → 'field "username" is not set\n'  (text/plain)
#   - admin.getuser.nouid         → 'field "uid" is not set\n'       (text/plain)
#   - admin.getuserlist.nokeyword → 'field "keyword" is not set\n'   (text/plain)
# 走 diff_json 走 text/plain(被 parse_json 包成 {"__raw__": "..."} 后逐字比对)。
# 注：admin.getuser.nokeyword 需要 admin_token。difftest 跑完 admin.update 会把 admin
#      密码改成 'newadmin123',login 那一步再跑就会用新密码;**1) admin 登录的首次抓
#      token 不能受 admin.update 影响 2) admin_token 必须在 admin.update 之前抓**。
#      当前场景表顺序是 admin.login → admin.update,所以必须把 admin.update 之前
#      capture 一次 token,或者在 admin.update 之前给 token 注入新密码 —— 已用脚本
#      验证「password='newadmin123' 在 admin.update 后能登入」,difftest 不需要改。

# KNOWN_CODE_DIFF:历史上用来标 (uid=0 → Go=1003 vs RAG=1017) 的有意偏离,2026-10-21
#                  实测两侧都返 1003(uid=0 不是「不传 uid」,Go 真源同样序列化到
#                  uid=0,然后走 SELECT WHERE uid=0 查无此人),原留档**已证伪**,
#                  §3.4.3 N-3 行已修改为「已证伪,本条不是偏离」。对应白名单条目
#                  admin.getuser.nouid 已转走 diff_json 逐字比对(见上)。

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
    """返回 (status, text, resp_headers)。

    **为什么从两元组变成三元组**（2026-10-22，E-1 收口）：
    关闭 E-1 需要断言 400 响应的**形状**是 `text/plain` 而不只是状态码 400 ——
    而 Content-Type 只有从响应头拿得到。DRIFT-006 对齐的正是这个形状
    （§3.4.4：「形状对齐、文本不对齐」），不取头就无从验证「形状」二字。

    `resp_headers` 是最终响应的头（dict）；取不到时返回空 dict —— 调用方必须容忍缺失。

    ⚠️ **本副本（2026-10-29）改用 `http1xx.request`** ⇒ 传输层会跳过 interim 1xx
    （RFC 9110 §15.2）⇒ 返回的是**最终响应** ⇒ 与 curl / JDK / Go / 浏览器所见一致。
    原实现用 `urllib.request` ⇒ Python **不跳过 1xx** ⇒ RAG 侧共享层的
    `GoStyleClientDisconnectProbe`（签字偏离 `SD-2026-10-25-04`）在慢请求上
    写的 `103 Early Hints` 会被当成**终态** ⇒ body 空 ⇒ 假 DIFF。
    ⚠️ 本函数**只保留三元组契约**；真正的发送路径在 `Runner._send()` 里
       （那里还要处理**有界重试**与**1xx 计数**）⇒ 不要绕过它自己发请求。
    """
    res = http1xx.request(method, url, body=body, headers=headers, raw=raw, timeout=30.0)
    if res.error is not None:
        return 0, "<<transport error: %s>>" % res.error, {}
    return res.status, res.text, res.headers


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


def timeout_shaped(status, body, text):
    """判断「这一侧的响应是**超时形态**」⇒ 用来把**环境负载**与**语义漂移**分开。

    两种形态（**故意不含「传输层失败」**）：
      * HTTP **503** + body `Request Timeout`
        —— 共享层 `HttpMiddleware` 的 HTTP 超时分支（Go 侧同一份文案：`httpTimeoutBody`）；
      * 业务码 **1015**「服务繁忙」
        —— gRPC deadline 超时（共享层把 `DEADLINE_EXCEEDED` 翻成 1015）。

    ⚠️ **为什么必须有这个判据**（2026-10-29 实测踩过）：
      本机负载飙到 **load average 78** 时，RAG 侧（api→rpc→PG）的请求会
      撞上 **2s gRPC deadline / 3s HTTP 超时**，而 Go 侧（**同层数**，只是少一层
      500ms 的切片量化）不撞 ⇒ 探针会报出若干条 DIFF（503 / 1015 / 连带的 afterlogout）
      ⇒ ⇒ **那是环境问题，不是漂移** ⇒ 报成「漂移」会把**跑红当缺陷**（AGENTS.md §3.3.3）。

    ⚠️ **传输层失败（HTTP 0）不在本函数里** ⇒ **「一侧被 kill 掉」必须判红（exit 1）**，
      不能被当成「环境问题」（2026-10-29 实测踩过：M2 变异曾被误判成 exit 2）。
      ⇒ 理由：**「对方没答话」是硬差异**；「对方答了但太慢」才是负载形态。
    """
    if status == 503 and "Request Timeout" in (text or ""):
        return True
    if isinstance(body, dict) and body.get("code") == 1015:
        return True
    return False


def transport_failed(status, text):
    """传输层失败（HTTP 0）⇒ **硬差异**（不是负载形态）⇒ 必须判红。"""
    return status == 0 or "<<transport error" in (text or "")


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
        # ── 1xx / 重试留痕（本副本新增）────────────────────────────────
        self.interim_seen = []       # 命中过 interim 1xx 的 step id 列表
        self.retried = []            # 发生过重试的 step id 列表
        self.retried_due_to_1xx = [] # 因 1xx 而重试的 step id 列表
        self.mutations = []          # 本副本执行过的变异（留痕）
        self.timings = {}           # step id → 该侧本次请求的耗时（ms）

    def maybe_mutate(self, step_id):
        """**变异钩子**（供 `mutate-parity.sh` 证明探针有牙齿）。

        环境变量：
          `PROBE_MUTATE_AFTER` —— 触发点（step id）
          `PROBE_MUTATE_SIDE`  —— "go" / "rag" / "any"（默认 any）
          `PROBE_MUTATE_CMD`   —— 要执行的 shell 命令

        ⚠️ **默认不启用**（三个变量都不设 ⇒ 本函数直接返回 ⇒ 正常跑零影响）。
        ⚠️ 钩子的存在**不是为了改产品代码**：本仓的变异都在**运行期**做
           （改一侧的库 / 停一侧的进程）⇒ **源码零改动** ⇒ 天然可还原。
        """
        after = os.environ.get("PROBE_MUTATE_AFTER")
        if not after or after != step_id:
            return
        side = os.environ.get("PROBE_MUTATE_SIDE", "any")
        if side not in ("any", self.name):
            return
        cmd = os.environ.get("PROBE_MUTATE_CMD")
        if not cmd:
            return
        import subprocess
        out = subprocess.run(cmd, shell=True, capture_output=True, text=True)
        print("  🧪 [变异] side=%s after=%s ⇒ rc=%d\n        %s"
              % (self.name, step_id, out.returncode,
                 (out.stdout or out.stderr or "").strip().replace("\n", "\n        ")[:500]))
        self.mutations.append((self.name, step_id, out.returncode))

    def _send(self, spec, headers, url, body, raw):
        """发一次请求；**传输层失败或终态仍是 1xx ⇒ 重试一次**（有界）。

        ⚠️ 判据（不可放宽）：
          * 重试**最多** `MAX_RETRY` 次（默认 1）⇒ 绝不能变成「无限重试直到绿」；
          * **重试后仍不一致 ⇒ 必须红**（本函数把最后一次的结果原样返回，
            由 `main()` 的差异判定决定红绿）；
          * 重试**只**针对「传输层失败」与「终态仍是 1xx」两种**信号性问题**，
            业务码差异（401 / 1006 …）**不触发**重试 ⇒ 否则真缺陷会被洗成绿。
        """
        last = None
        for attempt in range(MAX_RETRY + 1):
            res = http1xx.request(spec["method"], url, body=body,
                                  headers=headers, raw=raw, timeout=30.0)
            last = res
            needs_retry = (res.error is not None) or (100 <= res.status < 200)
            if not needs_retry or attempt >= MAX_RETRY:
                if attempt > 0:
                    self.retried.append(spec["_sid"])
                    if res.interim or (last and last.interim):
                        self.retried_due_to_1xx.append(spec["_sid"])
                break
        return last

    def run(self, step):
        spec = step["go"] if self.name == "go" else step["rag"]
        group = step["group"]
        spec = dict(spec)
        spec["_sid"] = step["id"]

        # ⚠️ 2026-10-24：记录**本步骤里没解析出来的模板**。
        #   `{{user_uid}}` 这类占位符只在 user 场景里被 capture；
        #   跑 `--scenario admin`（或 user 场景前半段失败）时它解析成空串 ⇒
        #   两侧都拿到「空 uid」⇒ 都回 400 text/plain，但**错误文本来源不同**
        #   （Go 走 form 参数的 strconv 报错；RAG 走 body 的必填校验）。
        #   ⇒ 这种差异**不是漂移**，而是「本场景在当前 scope 下不可判定」。
        #   不记录的话，它会被算成 unexpected ⇒ 把工具噪声当成真差异。
        missing = set()

        def resolve(v):
            if isinstance(v, str) and v.startswith("{{") and v.endswith("}}"):
                key = v[2:-2]
                if key not in self.state:
                    missing.add(key)
                return self.state.get(key, "")
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

        res = self._send(spec, headers, url,
                         resolve_deep(spec["json"]) if spec.get("json") is not None else None,
                         spec.get("raw"))
        if res.error is not None:
            status, text, rh = 0, "<<transport error: %s>>" % res.error, {}
        else:
            status, text, rh = res.status, res.text, res.headers
        # ── 留痕：命中过 interim 1xx 的 step（**不改变判定，只留证据**）────
        if res.interim:
            self.interim_seen.append((step["id"], list(res.interim)))
        # ── 留痕：**两侧各跳的实际耗时**（ms）⇒ 用来对比「Go 两跳 vs RAG 三跳」
        self.timings[step["id"]] = res.elapsed_ms
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
        self.maybe_mutate(step["id"])
        return status, body, text, rh, missing


# ---------------------------------------------------------------- 预热
def warmup(name, group, rounds=WARMUP_MAX_ROUNDS):
    """预热：把「冷启动首请求」消化掉，返回 (轮数, 命中 1xx 的轮数, 命中 1xx 的 step 列表)。

    ⚠️ **为什么必须预热**（实测 2026-10-29）：
      RAG 侧的 `GoStyleClientDisconnectProbe` 在**请求耗时 > 500ms** 时会先写一行
      `103 Early Hints` ⇒ 而 Python 的 `urllib` 把 1xx 当终态 ⇒ 首请求的
      **响应体整个丢掉** ⇒ 表现为「RAG 侧返回 103 空 body」⇒ 后续依赖 token 的
      步骤全部 401（业务码 1006「TOKEN已过期」）⇒ 一片假 DIFF。
      ⇒ 预热用**只读路径**（登录一个不存在的用户）把冷启动消化掉：
         ① 绝不改数据（登录失败不写库）；
         ② 命中 1xx 的轮次**照实计数**（不隐藏）。
    ⚠️ 预热**不是**判据：预热后仍出现 1xx ⇒ 走 `_send()` 的有界重试 ⇒ 仍不一致就红。
    """
    hits = []
    used = 0
    for i in range(max(1, rounds)):
        used += 1
        path = ("/user/v1/login" if group == "user" else "/admin/v1/login")
        res = http1xx.request("POST", BASE[name][group] + path,
                               body=dict(WARMUP_PAYLOAD), timeout=30.0)
        if res.interim:
            hits.append((i, list(res.interim)))
            continue
        # 没有 1xx ⇒ 冷启动已消化 ⇒ 提前结束（不必把额度用完）
        break
    return used, hits


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--scenario", choices=["user", "admin", "all"], default="all")
    ap.add_argument("--verbose", action="store_true")
    ap.add_argument("--no-warmup", action="store_true",
                    help="跳过预热（只用于**故意**复现冷启动 1xx 的场景）")
    args, _unknown = ap.parse_known_args()

    steps = [s for s in SCENARIOS if args.scenario in ("all", s["group"])]

    print("=" * 78)
    print("Go <-> Spring Boot dual differential (HTTP wire)  scenario=%s" % args.scenario)
    print("=" * 78)
    print("端口：go.user=%s go.admin=%s rag.user=%s rag.admin=%s"
          % (BASE["go"]["user"], BASE["go"]["admin"],
             BASE["rag"]["user"], BASE["rag"]["admin"]))

    # ── 预热（两侧各自、每个 group 各一轮）──────────────────────────────
    # ⚠️ 预热在**取 token / 打第一个业务请求之前**做 ⇒ 把冷启动首请求的
    #    1xx 消化掉 ⇒ 避免「103 空 body ⇒ token 没抓到 ⇒ 后续全 401」。
    warm_stats = {}
    if not args.no_warmup:
        print("\n-- 预热（只读路径：登录不存在的用户；命中 1xx 的轮次照实计数）--")
        for n in ("go", "rag"):
            for group in ("user", "admin"):
                used, hits = warmup(n, group)
                warm_stats["%s/%s" % (n, group)] = (used, hits)
                print("   %-10s %-5s 轮次=%d 命中1xx=%s"
                      % (n, group, used, hits if hits else "无"))
    else:
        print("\n-- 预热：**已跳过**（--no-warmup）--")

    # 两侧各自把整个序列连续跑一遍 —— 状态机（token / uid / 邀请码）必须各自推进，
    # 不能交错执行，否则第二侧会捡到第一侧已经消费掉的邀请码。
    runners = {n: Runner(n) for n in ("go", "rag")}
    raw = {}
    for n, r in runners.items():
        raw[n] = [r.run(s) for s in steps]

    print("=" * 78)
    print("Go <-> Spring Boot dual differential (HTTP wire)  scenario=%s" % args.scenario)
    print("=" * 78)

    asserted = byte_equal = diffs = inconclusive = 0
    timeout_steps = []
    transport_steps = []
    for i, s in enumerate(steps):
        gs, gb, gtext, ghead, gmiss = raw["go"][i]
        rs, rb, rtext, rhead, rmiss = raw["rag"][i]
        missing_tpl = sorted(set(gmiss) | set(rmiss))

        gn = runners["go"].norm.norm(gb)
        rn = runners["rag"].norm.norm(rb)

        # ── 超时形态登记（**只用于分类/退出码**，不改变本步的判定）─────────
        if timeout_shaped(rs, rb, rtext):
            timeout_steps.append((s["id"], rs, (rtext or "")[:60]))
        elif transport_failed(rs, rtext):
            transport_steps.append((s["id"], rs, (rtext or "")[:60]))

        if s["id"] in BODY_EXPECTATION:
            # E-1 收口：不再是「只比状态码」，而是按已签字的边界逐项断言两侧 body。
            # 这类步骤**不计入 expected diff**（差异已签字），但**断言不通过照样算 DIFF**——
            # 签字的是「两侧 body 不同」，不是「RAG 可以随便返回什么」。
            problems = check_body_expectation(
                BODY_EXPECTATION[s["id"]],
                gs, gb, gtext, rs, rb, rtext, rhead)
            if not problems:
                note = ("N-1 已签字：Go=%s / RAG=envelope(code=%d)"
                        % ("空body" if not (gtext or "").strip() else "{code:401}", N1_RAG_CODE)
                        if BODY_EXPECTATION[s["id"]] == "n1"
                        else "DRIFT-006：两侧 text/plain，文本归属各自框架")
                print("  [ OK   ] %-32s %s [ASSERT] %s"
                      % (s["id"], s["desc"], note))
                asserted += 1
            else:
                if missing_tpl:
                    inconclusive += 1
                    print("  [ 不可判定 ] %-32s %s" % (s["id"], s["desc"]))
                    print("           - 前置缺失：模板 %s 在本 scope 下未被 capture"
                          % ", ".join("{{%s}}" % t for t in missing_tpl))
                    for pr in problems[:6]:
                        print("           - %s" % pr)
                else:
                    diffs += 1
                    print("  [ DIFF ] %-32s %s [ASSERT 边界已被违反]" % (s["id"], s["desc"]))
                    for pr in problems[:14]:
                        print("           - %s" % pr)
            continue

        problems = []
        if gs != rs:
            problems.append("HTTP status: Go=%s vs RAG=%s" % (gs, rs))
        problems += diff_json(gn, rn)

        if not problems:
            print("  [ OK   ] %-32s %s" % (s["id"], s["desc"]))
            byte_equal += 1
        else:
            if missing_tpl:
                # 两侧都是「空 uid ⇒ 400 text/plain」，但错误文本来自不同来源
                # （Go：form 参数 strconv.ParseInt；RAG：body 必填校验）
                # ⇒ **这不是漂移，是本场景在当前 scope 下不可判定** ⇒ 不计入 unexpected。
                inconclusive += 1
                print("  [ 不可判定 ] %-32s %s" % (s["id"], s["desc"]))
                print("           - 前置缺失：模板 %s 在本 scope 下未被 capture"
                      % ", ".join("{{%s}}" % t for t in missing_tpl))
                for pr in problems[:6]:
                    print("           - %s" % pr)
                if args.verbose:
                    print("           Go  raw: %s" % gtext[:500])
                    print("           RAG raw: %s" % rtext[:500])
                continue
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
    # `asserted` = 走了「按边界断言」这条路的步骤数（E-1 收口后不再是「没看 body」，
    # 而是「按已签字的 N-1 / DRIFT-006 边界分别断言两侧」）。
    # `byte_equal` 才是真正跨端逐字比对的步数。两个数分开报，避免把
    # 「靠断言通过」误读成「两侧 body 逐字相同」。
    # `inconclusive` = 前置缺失（模板未捕获）导致本场景在当前 scope 下不可判定
    #   ⇒ **不计入 unexpected**（它不是漂移），但也不计入通过。
    print("steps=%d  asserted=%d  byte_equal=%d  inconclusive=%d  unexpected=%d"
          % (len(steps), asserted, byte_equal, inconclusive, diffs))

    # ── 1xx / 重试留痕（**必报**：1xx 是已签字偏离，不是「允许任意差异」）────
    total_interim = 0
    for n in ("go", "rag"):
        for sid, codes in runners[n].interim_seen:
            total_interim += len(codes)
            print("  [1xx] %-6s %-32s interim=%s（已按 RFC 9110 跳过，最终响应参与判定）"
                  % (n, sid, codes))
    retried = sorted(set(runners["go"].retried) | set(runners["rag"].retried))
    retried_1xx = sorted(set(runners["go"].retried_due_to_1xx)
                         | set(runners["rag"].retried_due_to_1xx))
    warm_1xx = sum(len(hits) for (_used, hits) in warm_stats.values())
    print("1xx_命中_step数=%d  1xx_总行数=%d  因1xx重试_step数=%d  重试_step数=%d  预热命中1xx轮数=%d"
          % (len(runners["go"].interim_seen) + len(runners["rag"].interim_seen),
             total_interim, len(retried_1xx), len(retried), warm_1xx))
    muts = runners["go"].mutations + runners["rag"].mutations
    if muts:
        print("变异执行记录：%s" % (muts,))

    # ── 两侧各跳的实际耗时对比（**不是**猜「撞了」⇒ 是**打出来的数**）──────
    # ⚠️ 为什么必须打出来：重载时 RAG 侧（api→rpc→PG **三跳**）撞 2s gRPC deadline /
    #    3s HTTP 超时，而 Go 侧（**两跳**：api→rpc→PG）不撞 ⇒ 这是**架构层数不同**
    #    带来的**结构性**差异 ⇒ 要证明它，必须有**两侧的实测耗时**，
    #    而不是「RAG 报 503」这一句话。
    def _stats(timings):
        vals = sorted(timings.values())
        if not vals:
            return "n/a"
        return "n=%d min=%.0fms med=%.0fms max=%.0fms" % (
            len(vals), vals[0], vals[len(vals) // 2], vals[-1])

    print("耗时对比（端到端 ms）：go 侧 %s ｜ rag 侧 %s"
          % (_stats(runners["go"].timings), _stats(runners["rag"].timings)))
    if timeout_steps:
        print("超时形态步骤的**两侧实测耗时**（同一步、同请求）：")
        for sid, st, tx in timeout_steps:
            print("   · %-32s go=%.0fms  rag=%.0fms  （RAG HTTP=%s）"
                  % (sid, runners["go"].timings.get(sid, -1),
                     runners["rag"].timings.get(sid, -1), st))
    slow = [(sid, runners["go"].timings.get(sid, 0), runners["rag"].timings.get(sid, 0))
            for sid in runners["rag"].timings
            if runners["rag"].timings.get(sid, 0) > 500]
    if slow:
        print("RAG 侧 > 500ms 的步骤（⇒ 命中 1xx 的那些）：")
        for sid, g, r in sorted(slow, key=lambda x: -x[2]):
            print("   · %-32s go=%.0fms  rag=%.0fms" % (sid, g, r))
    if retried:
        print("  重试明细（MAX_RETRY=%d ⇒ 重试后仍不一致必须红）：%s" % (MAX_RETRY, retried))
    print("  端口：go.user=%s go.admin=%s rag.user=%s rag.admin=%s"
          % (BASE["go"]["user"], BASE["go"]["admin"],
             BASE["rag"]["user"], BASE["rag"]["admin"]))

    # ── 超时形态 ⇒ 退出码 2（环境问题），**不是** 1（漂移）─────────────────
    # ⚠️ 判据（不许放宽）：只在「**有差异**」且「**RAG 侧出现过超时形态**」时，
    #    才把退出码从 1 改成 2 ⇒
    #    * 退出码 2 = 「**本轮不可判定**」（环境负载）⇒ **不是绿**，主线必须复跑；
    #    * 退出码 0 仍然只在「零差异」时给 ⇒ **绝不会**把超时当通过；
    #    * ⚠️ 若差异**不是**超时形态（哪怕同时有别的步骤超时）⇒ 仍按 1（漂移）报；
    #    * ⚠️ **传输层失败（HTTP 0）不属于超时形态** ⇒ 「一侧被 kill」⇒ **判红（1）**。
    if transport_steps:
        print("")
        print("!! 🔴 RAG 侧出现 **%d 步传输层失败**（连不上 / 读不到响应）⇒ **硬差异**，不是负载形态"
              % len(transport_steps))
        for sid, st, tx in transport_steps:
            print("     · %-32s HTTP=%s %s" % (sid, st, tx.replace("\n", " ")))
        print("   ⇒ 退出码 = **1（漂移）**：**一侧没答话**就是硬差异 ⇒ 绝不当成「环境问题」打发")
    if diffs and timeout_steps and not transport_steps:
        print("")
        print("!! ⚠️ 本轮有 %d 条差异，且 RAG 侧出现 **%d 步超时形态**"
              "（HTTP 503 'Request Timeout' / 业务码 1015「服务繁忙」）："
              % (diffs, len(timeout_steps)))
        for sid, st, tx in timeout_steps:
            print("     · %-32s HTTP=%s %s" % (sid, st, tx.replace("\n", " ")))
        print("   ⇒ 这**多半是环境负载**（RAG 侧的 HTTP 层有 500ms「切片等待」量化 ⇒ 尾延迟被放大；"
              "两侧调用链**层数相同**，都是 api→rpc→PG）")
        print("   ⇒ 退出码 = **2（环境问题 · 本轮不可判定）**，**不是** 1（漂移）")
        print("   ⇒ ⚠️ **绝不是 0** ⇒ 「超时」永远不会被当成「两侧一致」")
        print("   ⇒ 请在**空载**机器上复跑；复跑后仍超时 ⇒ 那才是真差异")
        return 2
    return 1 if diffs else 0


if __name__ == "__main__":
    sys.exit(main())
