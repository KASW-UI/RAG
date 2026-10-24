#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
§6.2.6 **E-3** 关闭工具 —— DTO 字段边界矩阵,Go 真源 ↔ RAG 双端对拍。

════════════════════════════════════════════════════════════════════════
【为什么有这个工具】
════════════════════════════════════════════════════════════════════════
`tools/difftest/diff.py` 是「每个端点 1~3 个典型场景」,22 个端点全覆盖,
但每端点只跑少数路径 —— 这正是 §6.2.6 **E-3** 留档的原话:
「39 步不是状态空间穷举 …… 边界组合(字段全缺 / 部分缺 / 类型错 / 超长 /
特殊字符)需要按 DTO 笛卡尔展开」。

本工具按 DTO **逐字段**展开边界组合,对每一组同时打到 Go 与 RAG 两端,
逐项断言 HTTP status 与业务码一致。

════════════════════════════════════════════════════════════════════════
【第一版的三处结构性缺陷(2026-10-21 修正,记下来防止再犯)】
════════════════════════════════════════════════════════════════════════
1. **token 跨端复用** —— 初版把同一个 user token 同时发给 Go 与 RAG,
   246 条里 86 条失败,其中一大片是 RAG 侧 1003。
   根因是 §3.4.3 **N-5**:两侧 HMAC 密钥长度不同(padded 到 32B vs Go 的 15B),
   **Go 签的 token 在 RAG 验不过,反之亦然**。这是已签字的有意偏离、不是缺陷,
   但对拍工具必须据此**分别登录、各用各的 token**。
2. **method 一刀切 POST** —— 初版给两端发同一个 method。
   但 §3.4.1「HTTP method | 统一 POST(Go 为 GET/DELETE 混合)」是**已签字偏离**:
   Go 真源的 `admin/getuserlist`、`admin/getuser`、`admin/getself`、
   `user/get`、`user/avatar/history` 都是 **GET**;
   且 `GetUserListReq` / `GetUserReq` 的字段是 `form:` tag —— Go 从 **query string**
   收参,RAG 从 **JSON body** 收参。初版统一 POST 打 Go ⇒ Go 返 405,38 条全被方法
   差异污染。本版**按端点声明各自的方法与参数通道**。
3. **字段列表手抄** —— 初版把字段名与类型硬编码在 SPECS 里。手抄 = 第二份
   可能漂移的副本,违反 §3.1「只留一个源」的精神。本版**直接解析 Go 真源的
   `types.go`**:字段名、Go 类型、`form`/`json` 通道、必填性(有无 `optional` /
   `default=`)、`,string` wire 标记**全部来自真源文件**。真源改了矩阵自动跟着变。

════════════════════════════════════════════════════════════════════════
【`,string` 是本工具最想压出来的东西】
════════════════════════════════════════════════════════════════════════
Go 的 `types.go` 里有多个字段带 `json:"...,string"` 标记
(`BanUserReq.Uid`、`UpdateUserReq.Uid`、`GetUserReq.Uid`、`UserInfo.Uid` …)。
`encoding/json` 的 `,string` 语义是:**线上必须是 JSON 字符串**,写 JSON 数字直接报错。
而 RAG 侧对应字段是 Java `Long` / `String`,Jackson 默认允许
string→Long 与 number→Long 双向强转。
⇒ 同一个 `uid` 字段,Go 只接受 `{"uid":"123"}`,RAG 数字和字符串都收。
这类差异静态读代码容易漏(两边「看起来」都是整数),只有真打才能压出来。

════════════════════════════════════════════════════════════════════════
【不做什么】
════════════════════════════════════════════════════════════════════════
- **不比对 body 字节级文本**。4xx 的错误文案来自两端不同的 JSON 解析层
  (go-zero / Go stdlib vs Jackson),逐字比对会产生大量假阳性 ——
  §3.4.4 **DRIFT-006** 与 §3.4.3 **N-6** 已确立「形状对齐、文本不对齐」的边界。
  本工具比 **HTTP status + 业务码 + body 形状**。
- **不做 401 鉴权失败行的 body 比对**(§3.4.2 **N-1** 有意偏离)。
- **不启动任何容器**。必须先起两套栈:
      ./tools/difftest/run.sh --no-build --keep --serve-only

════════════════════════════════════════════════════════════════════════
【与 diff.py 共存的一条硬约束:账号名不许以 dt_ 开头】
════════════════════════════════════════════════════════════════════════
`diff.py` 的 `admin.getuserlist.keyword` 场景用 **keyword=`dt_`** 做前缀过滤
(`admin.go:154` 是 `username LIKE %kw% OR email LIKE %kw%`),
并且它只对**自己在本次运行里注册过**的 uid 建 alias(`Norm.walk` 里的 `self.alias`)。

⇒ 只要库里存在一行「非 diff.py 自建、但用户名或邮箱含 `dt_`」的记录,
   那一行两侧 uid 天然不同、又没有 alias 可映射 ⇒ 这一步必然 DIFF。
   第一版矩阵的固定账号 `dt_matrix_user` / `dt_matrix_user_E3` / `dt_matrix_e3_probe`
   正好命中,把 difftest 从 39/39 打成 **1 unexpected**。

⇒ 本工具所有自建账号一律用 **`mx_` 前缀 + run_id**,与 `dt_` 永不相交。
   **改账号命名时务必记住这条**,否则会以为是自己引入的代码回归。
   已实测:全新 cluster 上先跑本矩阵、再跑 `diff.py` ⇒ 仍 **39/39 unexpected=0**。

════════════════════════════════════════════════════════════════════════
【结果随环境状态变化的部分】
════════════════════════════════════════════════════════════════════════
根因 **R3** 的读路径那 2 条(`login.Username.long5000` 报 1002/1003)
**只在 Go 库里已经存在一条超长用户名行时**才出现 —— 那行是更早某轮矩阵写进去的。
全新 cluster ⇒ R3 是 **4** 条;跑过一轮的库 ⇒ R3 是 **6** 条。
**根因分类两处完全一致,不要按总数判断 R3 变了。**

════════════════════════════════════════════════════════════════════════
【结果分桶】
════════════════════════════════════════════════════════════════════════
每条用例落到且仅落到一个桶。判据写在 `classify()` 里,**每个非 MATCH 桶都必须
在 AGENTS.md 里有对应留档编号**,否则一律进 NEW_DRIFT 报出来 ——
不允许「看起来像已知差异就放过」。§3.4.3 末尾那条纪律的教训:
N-3 / N-6 都经过了签字、都有防回退测试,而它们描述的行为**从未存在过**。

  MATCH                两端 status 一致,且 body 形状与业务码也一致
  KNOWN_FORM_EMPTY     §6.2.6 E-1 段已留档:Go 的 form 通道丢弃空值
                       (`httpx/util.go:37-43` GetFormValues),`x=` 退化成「缺席」
  KNOWN_FRAMEWORK_TEXT §3.4.4 DRIFT-006 / §3.4.3 N-6:两端同为 4xx + text/plain,
                       仅错误文本来自不同框架 —— 已确立为接受的边界
  NEW_DRIFT            未登记的行为差异。**不得直接改代码**,须逐条取证后
                       再判断是新缺陷还是需要签字的有意偏离
  TOOL_FAULT           矩阵自己的 fixture 错误(端口不通 / 405 / token 缺失),
                       不代表被测代码的任何性质

════════════════════════════════════════════════════════════════════════
【运行】
════════════════════════════════════════════════════════════════════════
    python3 tools/difftest/matrix.py --list
    python3 tools/difftest/matrix.py
    python3 tools/difftest/matrix.py --dto user.register --verbose
"""
import argparse
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

GO_ROOT = "/Users/pr/Documents/java&golang/Sea-RideTheWind_golang"
HERE = os.path.dirname(os.path.abspath(__file__))
RESULTS = os.path.join(HERE, "matrix-results.txt")

BASE = {
    "go":  {"user": "http://127.0.0.1:18888", "admin": "http://127.0.0.1:18889"},
    "rag": {"user": "http://127.0.0.1:59900", "admin": "http://127.0.0.1:58889"},
}

GO_TYPES_FILES = {
    "user":  os.path.join(GO_ROOT, "service/user/user/api/internal/types/types.go"),
    "admin": os.path.join(GO_ROOT, "service/user/admin/api/internal/types/types.go"),
}

# difftest 环境里预置的 bootstrap 管理员。只有它用来「换一个本次运行专属的
# 矩阵账号」,矩阵用例本身**绝不去改它** —— 改了就把整个 difftest 环境弄坏。
ADMIN_UW, ADMIN_PW = "admin", "admin"

# admin.updateuser / admin.banuser 的 uid 种子**故意用不存在的 uid**。
# 这两个端点的矩阵只测「解析层边界」(字段缺/类型错/超长),不需要业务成功路径;
# 用真实 uid 会让 banuser 的第一条用例就把那个用户封掉、
# updateuser 的 password 用例会改掉它的密码,污染后续所有用例的结果。
NONEXISTENT_UID = 9999999999
M_USER_PW = "pw123456"
M_ADMIN_PW = "pw123456"

# 每个端点的「无副作用种子值」。**必须是重跑后仍然成立的常量** ——
# 第一版给 user.update 统一种 "seed@x.com" / "seed@x.com"(username/password),
# 跑完就把 fixture 账号的密码改了,第二次跑 build_fixtures() 登录必失败。
#
# {U}/{UPW}/{UEMAIL} 是本次运行专属账号的占位符,在 _identity() 里替换。
# 取值原则:
#   · update 类端点 → 种成该账号**当前的**真实值 ⇒ 空操作,矩阵可重复跑
#   · register/create 类端点 → 种成固定的 probe 名 ⇒ 首跑成功、后续 1001,
#     两端一致因此不影响判定,只是不再无限堆新用户
#   · 带 uid 的端点 → 种成不存在的 uid ⇒ 只验解析层,不产生状态变更
IDENTITY = {
    "user.register":     {"username": "{PU}", "password": M_USER_PW, "email": "{PUE}"},
    "user.login":        {"username": "{U}", "password": "{UPW}"},
    "user.update":       {"username": "{U}", "password": "{UPW}", "email": "{UEMAIL}"},
    "admin.login":       {"username": "{AU}", "password": "{APW}"},
    "admin.create":      {"username": "{PA}", "password": M_ADMIN_PW, "email": "{PAE}",
                          "invite_code": "MATRIX-INVITE-NOT-A-REAL-CODE"},
    "admin.update":      {"username": "{AU}", "password": "{APW}", "email": "{AEMAIL}"},
    "admin.updateuser":  {"username": "no_such_user", "password": "x",
                          "email": "no_such_user@x.com",
                          "uid": str(NONEXISTENT_UID)},
    "admin.getuserlist": {"page": 1, "page_size": 20, "keyword": "{U}"},
    "admin.banuser":     {"uid": str(NONEXISTENT_UID)},
}

# run_id → 占位符替换表。两端用同一个 run_id(库是分开的),所以种子值与端无关。
#
# {PU}/{PA} 是 register/create 的 probe 用户名 —— **也必须按 run_id 隔离**。
# 用固定名的话,第二次跑时 probe 用户已存在 ⇒ baseline 变成「用户名已存在」,
# 而邮箱 UNIQUE 又先撞上 ⇒ RAG 返 5001、Go 返 1001,
# 于是一条纯粹的「列宽差异」被邮箱冲突盖住,查起来会绕远路。
_PLACEHOLDER = {"{U}": "user_username", "{UPW}": "user_password",
                "{UEMAIL}": "user_email", "{AU}": "admin_username",
                "{APW}": "admin_password", "{AEMAIL}": "admin_email",
                "{PU}": "probe_user", "{PUE}": "probe_user_email",
                "{PA}": "probe_admin", "{PAE}": "probe_admin_email"}


def _identity(spec_name, fx):
    ident = dict(IDENTITY.get(spec_name, {}))
    for ph, key in _PLACEHOLDER.items():
        if ph in ident.values():
            ident = {k: fx[key] if v == ph else v for k, v in ident.items()}
    return ident

OMIT = object()
CHINESE, EMOJI, LONG = "中文测试", "🎯🚀🎉", "x" * 5000
I64_MAX, I64_OVER = 9223372036854775807, 9223372036854775808


# ═════════════════ 1. 解析 Go 真源的 types.go —— 矩阵的唯一字段来源 ═════════════════
# 本地文件缺失就硬失败,**绝不退回手抄的默认值**:那等于把第二份副本请回来。

_STRUCT_RE = re.compile(r"^type\s+(\w+)\s+struct\s*\{(.*?)^\}", re.S | re.M)
_FIELD_RE = re.compile(r"^\s*(\w+)\s+([\w\[\]\.\*]+)\s+`([^`]*)`", re.M)
# struct tag 形如 `json:"uid,string"` / `form:"page,default=1"`。
# 注意逗号在引号**内部**,不能对整个 tag 直接 split(",") ——
# 那样 key 会变成 'json:"uid'、选项会变成 'string"'，
# channel 与 wire_string 两个判据会同时失效(已踩过一次)。
_TAG_RE = re.compile(r'^\s*(\w+)\s*:\s*"([^"]*)"\s*(.*)$')


def parse_tag(tag):
    """→ (channel, options, wire_name)。channel 取 json/form/其他。

    引号里的内容是 ``name,opt1,opt2`` 的形式:
        `json:"uid,string"`        → name=uid,   opts=[string]
        `form:"page,default=1"`    → name=page,  opts=[default=1]
        `json:"email,omitempty"`   → name=email, opts=[omitempty]

    踩过的坑:第一版把**整个引号内容**当 wire 名,于是发出去的 key 是
    ``"username,optional"`` —— 两端都不认,body 等于空的,
    register 全变 400、update 全变 200,而矩阵还「全绿」。
    """
    m = _TAG_RE.match(tag)
    if not m:
        return "json", [], ""
    key, value, _rest = m.group(1), m.group(2), m.group(3)
    parts = [p.strip() for p in value.split(",")]
    return (key if key in ("json", "form") else "json"), parts[1:], parts[0]


def parse_go_types(path):
    """types.go → {StructName: {field: info}}。

    info:
      go_type      Go 声明类型(原样,如 "int64" / "map[string]string")
      wire_name    线上字段名(tag 引号里的值,如 "page_size")
      channel      "form" | "json" —— 决定参数走 query 还是 body
      required     go-zero 语义:tag 里既无 ``optional`` 也无 ``default=`` 即必填
      wire_string  tag 带 ``,string`` ⇒ 线上必须是 JSON 字符串
    """
    with open(path, "r", encoding="utf-8") as fh:
        src = fh.read()
    out = {}
    for m in _STRUCT_RE.finditer(src):
        fields = {}
        for fm in _FIELD_RE.finditer(m.group(2)):
            fname, gotype, tag = fm.group(1), fm.group(2), fm.group(3)
            key, opts, wire = parse_tag(tag)
            fields[fname] = {
                "go_type": gotype,
                "tag": tag,
                "wire_name": wire or fname,
                "channel": key,
                "required": ("optional" not in opts)
                            and not any(o.startswith("default=") for o in opts),
                "wire_string": "string" in opts,
            }
        out[m.group(1)] = fields
    return out


def load_types():
    for side, p in GO_TYPES_FILES.items():
        if not os.path.exists(p):
            sys.exit(f"FATAL: Go 真源 types.go 不存在: {p}\n"
                     f"E-3 的字段来源必须是真源文件本身,拒绝用手抄常量兜底。")
    return {side: parse_go_types(p) for side, p in GO_TYPES_FILES.items()}


# ═════════════════ 2. 边界生成器(按 Go 声明类型分派) ═════════════════

def boundaries_for(info):
    gt = info["go_type"]
    if gt.startswith("map"):
        base = [
            ("omit", OMIT), ("null", None), ("empty_obj", {}),
            ("chinese_val", {"city": "北京", "emoji": EMOJI}),
            ("type_err_scalar", "not-a-map"),
        ]
    elif gt == "string":
        base = [
            ("omit", OMIT), ("empty", ""), ("null", None),
            ("chinese", CHINESE), ("emoji", EMOJI), ("long5000", LONG),
            ("type_err_int", 99999), ("type_err_float", 1.5),
            ("type_err_bool", True), ("type_err_obj", {"a": 1}),
        ]
    else:                                       # int64 / uint32 / …
        base = [
            ("omit", OMIT), ("null", None), ("zero", 0), ("negative", -1),
            ("i64_max", I64_MAX), ("i64_overflow", I64_OVER),
            ("type_err_str", "12345"), ("type_err_bool", True),
            ("type_err_float", 1.5),
        ]
        if info["wire_string"]:
            # ,string 字段额外发一档「线上写 JSON 数字」——
            # 用来探测「Go 严格拒绝 / RAG(Jackson) 强转接受」这一类差异。
            base.append(("wire_number_raw", 12345))
    return base


# ═════════════════ 3. 端点声明(go_method 与 rag_method 各自按各自真源) ═════════════════

SPECS = [
    {"name": "user.register", "group": "user",
     "go_path": "/usercenter/v1/user/register", "rag_path": "/user/v1/register",
     "go_method": "POST", "rag_method": "POST", "go_struct": "CreateUserReq", "auth": None,
     "suffix_unique_strings": True},

    {"name": "user.login", "group": "user",
     "go_path": "/usercenter/v1/user/login", "rag_path": "/user/v1/login",
     "go_method": "POST", "rag_method": "POST", "go_struct": "LoginReq", "auth": None},

    {"name": "user.update", "group": "user",
     "go_path": "/usercenter/v1/user/update", "rag_path": "/user/v1/update",
     "go_method": "POST", "rag_method": "POST", "go_struct": "UpdateUserReq", "auth": "user",
     "suffix_unique_strings": True},

    {"name": "admin.login", "group": "admin",
     "go_path": "/admincenter/v1/admin/login", "rag_path": "/admin/v1/login",
     "go_method": "POST", "rag_method": "POST", "go_struct": "LoginReq", "auth": None},

    {"name": "admin.create", "group": "admin",
     "go_path": "/admincenter/v1/admin/create", "rag_path": "/admin/v1/create",
     "go_method": "POST", "rag_method": "POST", "go_struct": "CreateAdminReq", "auth": None,
     "suffix_unique_strings": True},

    {"name": "admin.update", "group": "admin",
     "go_path": "/admincenter/v1/admin/update", "rag_path": "/admin/v1/update",
     "go_method": "POST", "rag_method": "POST", "go_struct": "UpdateSelfReq", "auth": "admin",
     "suffix_unique_strings": True},

    {"name": "admin.updateuser", "group": "admin",
     "go_path": "/admincenter/v1/admin/updateuser", "rag_path": "/admin/v1/updateuser",
     "go_method": "POST", "rag_method": "POST", "go_struct": "UpdateUserReq", "auth": "admin"},

    # ↓ A-03 已签字偏离:Go 是 GET + form(query) 通道,RAG 是 POST + JSON body 通道。
    #   矩阵必须按各自通道发,否则 38 条用例会被 405 全污染。
    {"name": "admin.getuserlist", "group": "admin",
     "go_path": "/admincenter/v1/admin/getuserlist", "rag_path": "/admin/v1/getuserlist",
     "go_method": "GET", "rag_method": "POST", "go_struct": "GetUserListReq", "auth": "admin"},

    {"name": "admin.banuser", "group": "admin",
     "go_path": "/admincenter/v1/admin/banuser", "rag_path": "/admin/v1/banuser",
     "go_method": "POST", "rag_method": "POST", "go_struct": "BanUserReq", "auth": "admin"},
]


# ═════════════════ 4. HTTP ═════════════════

def http(method, url, body=None, query=None, token=None):
    hdrs = {}
    if query:
        url = url + ("&" if "?" in url else "?") + urllib.parse.urlencode(query, doseq=True)
    data = None
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        hdrs["Content-Type"] = "application/json"
    if token:
        hdrs["Authorization"] = "Bearer " + token
    req = urllib.request.Request(url, data=data, headers=hdrs, method=method)
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            return r.status, _decode(r.read())
    except urllib.error.HTTPError as e:
        return e.code, _decode(e.read())
    except Exception as e:                               # noqa: BLE001
        return 0, {"__transport_error__": repr(e)}


def _decode(raw):
    txt = raw.decode("utf-8", "replace")
    if not txt.strip():
        return None
    try:
        return json.loads(txt)
    except json.JSONDecodeError:
        return {"__raw__": txt}


def shape_of(resp):
    """响应归成四种形状:envelope(code) / text / none / other。"""
    if resp is None:
        return ("none", None)
    if not isinstance(resp, dict):
        return ("other", None)
    if "__transport_error__" in resp:
        return ("transport_error", None)
    if "__raw__" in resp:
        return ("text", None)
    if "code" in resp:
        return ("envelope", resp.get("code"))
    return ("other", None)


# ═════════════════ 5. fixture:两端各自注册 / 登录(N-5 ⇒ token 不通用) ═════════════════

def _login(side, group, path, username, password):
    s, b = http("POST", BASE[side][group] + path,
                body={"username": username, "password": password})
    return b["data"]["token"] if (s == 200 and isinstance(b, dict)
                                  and isinstance(b.get("data"), dict)) else None


# 每端各自的用户 / 管理员路由(Go 与 RAG 的 path、method 都不同)
P = {
    "go":  {"user_register": "/usercenter/v1/user/register",
            "user_login":    "/usercenter/v1/user/login",
            "user_update":   "/usercenter/v1/user/update",
            "admin_login":   "/admincenter/v1/admin/login",
            "admin_create":  "/admincenter/v1/admin/create",
            "admin_update":  "/admincenter/v1/admin/update",
            "invite_create": "/admincenter/v1/admin/invite/create"},
    "rag": {"user_register": "/user/v1/register",
            "user_login":    "/user/v1/login",
            "user_update":   "/user/v1/update",
            "admin_login":   "/admin/v1/login",
            "admin_create":  "/admin/v1/create",
            "admin_update":  "/admin/v1/update",
            "invite_create": "/admin/v1/invite/create"},
}


def _make_matrix_admin(side, run_id):
    """建一个**本次运行专属**的 admin,返回 (username, password, token)。

    为什么不能直接用 admin/admin:
      `admin.update` 的边界用例会真的改写这个账号 —— 第二版矩阵跑完后
      admin 的 username 变成 "true"、password 变成 "99999",
      两侧的 admin/admin 双双登录失败,矩阵从第二轮起就整体 TOOL_FAULT。
      更糟的是它**静默污染 difftest 环境**:下一次跑 diff.py 时
      admin_token-using 的场景会全红,而症状与 admin 代码无关。

    流程:admin/admin 登录 → invite/create 拿一次性邀请码 → admin/create
    → 用新账号登录。两端用**同一个** run_id 后缀(库是分开的,不冲突),
    这样 make_values 不需要按端区分种子值。
    """
    boot = _login(side, "admin", P[side]["admin_login"], ADMIN_UW, ADMIN_PW)
    if not boot:
        return None
    s, b = http("POST", BASE[side]["admin"] + P[side]["invite_create"],
                body={}, token=boot)
    invite = (b or {}).get("data", {}).get("invite_code") if isinstance(b, dict) else None
    if not invite:
        return None
    uname, upw = f"mx_admin_{run_id}", M_ADMIN_PW
    http("POST", BASE[side]["admin"] + P[side]["admin_create"],
         body={"username": uname, "password": upw,
               "email": f"{uname}@x.com", "invite_code": invite})
    tok = _login(side, "admin", P[side]["admin_login"], uname, upw)
    if not tok:
        return None
    return {"username": uname, "password": upw, "email": f"{uname}@x.com", "token": tok}


def build_fixtures(run_id):
    """两端各自建 run_id 专属的 user / admin,并各自登录取 token(N-5 ⇒ 不能共用)。"""
    fx = {"run_id": run_id}
    for side in ("go", "rag"):
        fx[side] = {}
        uname, upw = f"mx_user_{run_id}", M_USER_PW
        http("POST", BASE[side]["user"] + P[side]["user_register"],
             body={"username": uname, "password": upw, "email": f"{uname}@x.com"})
        utok = _login(side, "user", P[side]["user_login"], uname, upw)
        adm = _make_matrix_admin(side, run_id)
        fx[side] = {
            "user_username": uname, "user_password": upw,
            "user_email": f"{uname}@x.com", "user_token": utok,
            "admin_username": adm["username"] if adm else None,
            "admin_password": adm["password"] if adm else None,
            "admin_email": adm["email"] if adm else None,
            "admin_token": adm["token"] if adm else None,
            # register / create 的 probe 种子,同样按 run_id 隔离
            "probe_user": f"mx_probe_u_{run_id}",
            "probe_user_email": f"mx_probe_u_{run_id}@x.com",
            "probe_admin": f"mx_probe_a_{run_id}",
            "probe_admin_email": f"mx_probe_a_{run_id}@x.com",
        }
    return fx


# ═════════════════ 6. 载荷构造 ═════════════════

def _seed(spec, wire_name, info, fx):
    """未被本用例改写的字段给一个**无副作用**的合法值,保证每条用例只测一个变量。

    先查 IDENTITY(按 wire_name);查不到再退回类型默认值。
    `,string` 字段必须种成字符串,否则种子本身就是一条非法的 wire 请求。
    """
    ident = _identity(spec["name"], fx)
    if wire_name in ident:
        return ident[wire_name]
    gt = info["go_type"]
    if gt.startswith("map"):
        return {"city": "北京"}
    if gt == "string":
        return "seed@x.com"
    return str(NONEXISTENT_UID) if info["wire_string"] else NONEXISTENT_UID


def struct_channel(fields):
    """Go struct 的参数通道。本仓真源里每个 struct 内部通道是齐的
    (GetUserReq / GetUserListReq 全 form,其余全 json),取首个字段的通道即可。

    ⚠️ **这是 Go 侧的通道,不能套到 RAG 身上。**
    RAG 侧全部走 `@RequestBody`(§3.4.1 已签字的 A-03 偏离:HTTP method 统一 POST、
    参数统一 JSON body)。第一版矩阵只算一个 channel 并同时用于两端,
    于是给 RAG 的 getuserlist 发的是「POST + query、无 body」
    ⇒ RAG 收到空 body ⇒ keyword 必填检查失败 ⇒ 28 条里 14 条假 NEW_DRIFT。
    判据是 AGENTS.md §3.4.3 末尾那条纪律:**分桶结论必须由真实运行证据支撑**,
    而这里的「证据」是矩阵自己造的。
    """
    first = next(iter(fields.values()))
    return first["channel"]


# RAG 侧固定 JSON body —— 见 struct_channel 的说明与 §3.4.1 A-03
RAG_CHANNEL = "json"


def make_values(spec, case, fxs, run_id):
    """产出 {Go字段名: value} 的完整字典,None/OMIT 表示不出现。

    fxs 是某一端(fx["go"] 或 fx["rag"])的 fixture 字典 —— 两端 run_id 相同,
    所以种子值与端无关,固定取 go 侧即可。
    """
    suffix = ("_" + run_id) if spec.get("suffix_unique_strings") else ""
    out = {}
    for fname, info in spec["_fields"].items():
        if fname != case["field"]:
            out[fname] = _seed(spec, info["wire_name"], info, fxs)
            continue
        v = case["value"]
        # 给「会被写进 UNIQUE 列」的字符串边界值加 run 后缀 ——
        # 否则第 2 次跑时同一个 "中文测试" 已经躺在库里,Go 返 1001 而 RAG 返 200,
        # 报出来的却是「行为不一致」,实际只是上一轮自己留下的行。
        # 只加在**字符串**边界值上:int/float/bool 档是用来测**解析层强转**的,
        # 它们的线上形态是 RAG 强转出来的 "99999"/"1.5"/"true",无法加后缀去重,
        # 这一小撮仍会随运行次数抖动 —— 已在结果文件里标注。
        if suffix and isinstance(v, str) and v and case["boundary"] not in ("omit", "null"):
            v = v + suffix
        out[fname] = v
    return out


def to_payload(values, fields, channel):
    """把 values 转成该通道真正会发出去的东西。

    键一律用 `wire_name`(tag 引号里的值),不是 Go 的导出字段名 ——
    `PageSize` / `Extrainfo` 的线上键是 `page_size` / `extra_info`。

    form 通道:值统一编成 query string;None 无法表达为 query ⇒ 退化成「不传」
    json 通道:原样进 body
    """
    pairs = [(fields[k]["wire_name"], v) for k, v in values.items()]
    if channel == "json":
        return {"kind": "body", "body": {w: v for w, v in pairs if v is not OMIT}}
    q = {}
    for w, v in pairs:
        if v is OMIT or v is None:
            continue                       # query 通道表达不了 null
        if isinstance(v, bool):
            q[w] = "true" if v else "false"
        elif isinstance(v, (dict, list)):
            # map 类型在 query 通道下 go-zero 收不到结构(httpx.Parse 对
            # form 只填标量),跳过这一组合,由 RAG 的 JSON body 侧单独覆盖
            continue
        else:
            q[w] = v if isinstance(v, str) else str(v)
    return {"kind": "query", "query": q}


# ═════════════════ 7. 单条用例 ═════════════════

def run_case(case, fx):
    spec = case["spec"]
    res = {}
    for side in ("go", "rag"):
        # 通道按端取:Go 走真源的 form/json,RAG 一律 JSON body(A-03)
        pay = to_payload(case["values"], spec["_fields"], case[side + "_channel"])
        # N-5：两侧 token 互不通用,必须按 side 各取各的
        token = fx[side].get(f"{spec['auth']}_token") if spec["auth"] else None
        if spec["auth"] and not token:
            res[side] = {"status": -1, "shape": "no_token", "code": None,
                         "payload": pay}
            continue
        kw = {"body": pay["body"]} if pay["kind"] == "body" else {"query": pay["query"]}
        st, bd = http(spec[side + "_method"],
                      BASE[side][spec["group"]] + spec[side + "_path"],
                      token=token, **kw)
        sh, code = shape_of(bd)
        res[side] = {"status": st, "shape": sh, "code": code, "payload": pay}
    return res


# ═════════════════ 8. 分桶 ═════════════════

def classify(case, res):
    go, rag = res["go"], res["rag"]
    # 0. 矩阵自身故障:不算被测代码的性质
    if "transport_error" in (go["shape"], rag["shape"]) or "no_token" in (go["shape"], rag["shape"]):
        return "TOOL_FAULT", "端点不可达或 fixture token 缺失"
    if go["status"] == 405 or rag["status"] == 405:
        return "TOOL_FAULT", "405 —— 本矩阵的 method 表与真源不符"

    # 1. 已签字:Go form 通道丢弃空值(GetFormValues len(v)==0 整个丢掉)
    #    ⇒ form 通道 + 边界值是空串/None ⇒ Go 侧表现为「字段缺席」
    if (case["go_channel"] == "form" and case["value"] in ("", None, OMIT)
            and go["status"] != rag["status"]):
        return ("KNOWN_FORM_EMPTY",
                "Go form 通道丢弃空值,空串退化成缺席;RAG JSON body 通道能表达")

    # 2. 已签字:两端同为 4xx + text/plain,仅文本来自不同框架(DRIFT-006 / N-6)
    if go["shape"] == "text" and rag["shape"] == "text" and go["status"] == rag["status"]:
        return "KNOWN_FRAMEWORK_TEXT", "两端同为 text/plain 4xx,仅错误文本来自不同框架"

    # 3. 一致
    if go["status"] == rag["status"]:
        if go["shape"] == rag["shape"] and go["code"] == rag["code"]:
            return "MATCH", ""
        return "NEW_DRIFT", (f"status 同为 {go['status']} 但形状/业务码不同: "
                             f"go={go['shape']}/{go['code']} rag={rag['shape']}/{rag['code']}")

    # 4. 其余 status 不一致 —— 一律 NEW_DRIFT,须逐条取证
    return "NEW_DRIFT", (f"go=({go['status']},{go['shape']},{go['code']}) "
                         f"rag=({rag['status']},{rag['shape']},{rag['code']})")


# ═════════════════ 8b. 根因聚类 ═════════════════
# 分桶只回答「一致吗」,不回答「为什么不一致」。本节把 NEW_DRIFT 归到具名根因上,
# 让结果文件自解释。**没有命中任何一条的,标 UNCLASSIFIED 并单列** ——
# 那意味着出现了四个已知根因解释不了的新行为,必须重新取证。
# 每条根因都写明「独立于矩阵的证据出处」,不是「矩阵这么说」。

ROOT_CAUSES = [
    ("R1", "JSON 标量被 Jackson 静默强转/截断,Go 一律拒",
     "两个面:(a) number/bool → Java **String** 字段,Jackson 默认强转成 \"99999\"/\"true\"；"
     "(b) JSON 浮点 → Java **Long** 字段,Jackson 默认**向零截断**,小数部分直接丢。"
     "Go 侧两条都拒 ⇒ 400 text/plain。"
     "注意 (b) 比 (a) 更危险:(a) 只是把错的值存进去,(b) 是**请求成功执行但作用在错误的行上**。"
     "独立证据(curl 直打,两端同输入):"
     "① page —— rag {page:1.5} 与 {page:1} 响应逐字相同,{page:2.9} 与 {page:2} 相同 ⇒ 截断;"
     "② uid —— rag banuser {uid:1.5} 与 {uid:1} 均返 1003(等价),go banuser {uid:1.5} 返 400 "
     "`strconv.ParseInt: parsing \"1.5\": invalid syntax`。"
     "⚠️ 别把 Go 的 `,string` 标签当成「只收字符串」:go-zero 走 "
     "processNamedFieldWithValueFromString,**先把 JSON 值字符串化再 strconv.ParseInt**,"
     "所以 `{\"uid\":1}` 两端都接受(实测均 1003)。真正的分叉只在浮点。"),

    ("R2", "显式 null ≠ 字段缺席:Go 对 map 字段也判 null",
     "go-zero unmarshaler.go:856-862 processNamedFieldWithValue: mapValue==nil 且非 optional ⇒ "
     "fmt.Errorf(\"field %q mustn't be nil\")，**对 map/slice 一样生效**；"
     "而「缺席」走 processNamedFieldWithoutValue 的 Map 分支不报错。"
     "RAG 的 RegisterReq / CreateAdminReq / UpdateSelfReq / UpdateUserReq 四处注释都写着"
     "「Extrainfo 是 map ⇒ 不校验」，把 null 与缺席混为一谈。"
     "独立证据：curl 直打 Go register {\"extra_info\":null} 得 "
     "400 `field \"extra_info\" mustn't be nil`，而缺席该字段得 200。"),

    ("R3", "DDL 列宽:text vs varchar(255)",
     "Go 的 GORM AutoMigrate 给 Go string 字段生成 **text**（无上限）；"
     "RAG 的 4 份 db/*.sql 是照着**旧 JPA @Column** 写的 VARCHAR(255)。"
     "⇒ 超过 255 字符的 username/email/history_id 在 Go 侧写成功，"
     "在 RAG 侧被 PG 拒（22001 value tolong）⇒ 5001/5003。"
     "**还会反噬读路径**：Go 库里存在一条 5000 字符的用户名，RAG 库里根本存不下，"
     "于是同一登录请求 Go 命中、RAG 报「用户不存在」⇒ 1002/1003 —— "
     "这是 R3 的下游后果，不是独立根因。"
     "独立证据：information_schema.columns 对比 go_db 与 rag_db，35 列里 22 列类型不同。"),

    ("R4", "分页越界:Go 的 GORM 静默抑制负 OFFSET/LIMIT,RAG 直传 PG",
     "Go admin.go:159-160 `offset := (page-1)*pageSize; db.Offset(int(offset)).Limit(int(pageSize))`，"
     "GORM 只在 offset>0 / limit>=0 时才生成子句 ⇒ page=0/-1 与 page_size=-5 都被**静默忽略**，返回首页全量。"
     "RAG 把负数原样交给 PG ⇒ `22023: OFFSET must not be negative` ⇒ 5001。"
     "独立证据：curl 直打 Go GET getuserlist?page=-1&page_size=-5 得 200+code 200。"),
]


def root_cause(case, res):
    """把一条 NEW_DRIFT 归到具名根因。返回 (id, 标题) 或 (None, None)。"""
    b = case["boundary"]
    gt = case["spec"]["_fields"][case["field"]]["go_type"]
    go, rag = res["go"], res["rag"]

    # R1 覆盖两种面:number/bool → String 的强转,以及 float → Long 的截断。
    # 判据只认「Go 拒(400 text)而 RAG 收(200 envelope)」,不按字段类型写死 ——
    # 否则新增字段类型时会静默漏判,落进 UNCLASSIFIED 才发现。
    if b in ("type_err_int", "type_err_float", "type_err_bool") \
            and go["shape"] == "text" and go["status"] == 400 \
            and rag["shape"] == "envelope" and rag["status"] == 200:
        return "R1", ROOT_CAUSES[0][1]

    if b == "null" and gt.startswith("map") and go["status"] == 400 \
            and rag["status"] != 400:
        return "R2", ROOT_CAUSES[1][1]

    # R3 既包括写侧被 PG 拒(5001/5003),也包括读侧「Go 有、RAG 存不下」(1002/1003)
    #
    # ⚠️⚠️ 这条宽规则**已知会误分类**，不要拿「矩阵 R3 = 0」当作
    # 「列宽问题全部消失」的充分证据。
    #
    # 实证(2026-10-22)：`admin.create.Password.long5000` 曾长期被归进 R3，
    # 而它**根本不是列宽问题** —— RAG 的 DDL 已经是 text 了，5001 来自
    # bcrypt 的 72 字节上限**抢在 invite/username 检查之前抛错**(根因 R5)。
    #
    # 根子在于本矩阵的用例是**单变量**的：每个边界用例只扰动一个字段。
    # 「密码超长」与「前置检查该命中」两个条件**同时**成立时，
    # 矩阵只能看到先抛错的那一个，看不到顺序本身。
    #
    # R5 的完整分歧面靠独立探针 `tools/difftest/r5-order-probe.py` 覆盖
    # (它用**真邀请码**跑 A/B/C 三支；矩阵在 admin.create 上恒用假邀请码，
    #  永远走不到「邀请码有效」分支)。
    if b in ("long5000",) and rag["code"] in (5001, 5003, 1002, 1003):
        return "R3", ROOT_CAUSES[2][1]

    if case["spec"]["name"] == "admin.getuserlist" and case["field"] in ("Page", "PageSize") \
            and b in ("zero", "negative", "i64_max") and rag["code"] == 5001:
        return "R4", ROOT_CAUSES[3][1]

    return None, None


# ═════════════════ 9. main ═════════════════

def prepare(types):
    for s in SPECS:
        if s["go_struct"] not in types[s["group"]]:
            sys.exit(f"FATAL: 真源 {GO_TYPES_FILES[s['group']]} 里没有 struct {s['go_struct']}")
        s["_fields"] = types[s["group"]][s["go_struct"]]
        s["_channel"] = struct_channel(s["_fields"])


def gen_cases(spec):
    cases = []
    for fname, info in sorted(spec["_fields"].items()):
        for bname, val in boundaries_for(info):
            cases.append({"spec": spec, "field": fname, "boundary": bname,
                          "value": val,
                          "go_channel": spec["_channel"], "rag_channel": RAG_CHANNEL})
    return cases


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dto", help="只跑某个端点(如 user.register)")
    ap.add_argument("--list", action="store_true")
    ap.add_argument("--verbose", action="store_true", help="打印全部用例(含 MATCH)")
    ap.add_argument("--out", default=RESULTS)
    args = ap.parse_args()

    types = load_types()
    prepare(types)

    if args.list:
        total = 0
        for s in SPECS:
            n = len(gen_cases(s))
            total += n
            print(f"  {s['name']:22s} {s['go_struct']:16s} {s['_channel']:5s} "
                  f"go={s['go_method']:5s} rag={s['rag_method']:5s}  {n:3d} cases")
        print(f"  {'TOTAL':22s} {total} cases")
        return 0

    selected = [s for s in SPECS if not args.dto or s["name"] == args.dto]
    if not selected:
        print(f"no spec matched {args.dto!r}", file=sys.stderr)
        return 2

    # 每次运行用专属账号(run_id),见 _make_matrix_admin 的说明 ——
    # 矩阵用例会真的改写凭据,共用账号会毁掉 difftest 环境里的 admin/admin。
    run_id = str(int(time.time() * 1000))
    fx = build_fixtures(run_id)
    for side in ("go", "rag"):
        missing = [k for k in ("admin_token", "user_token") if not fx[side][k]]
        if missing:
            print(f"FATAL: {side} 侧 fixture 建立失败,缺 {missing}。"
                  f"admin/admin 能否登录?run_id={run_id}", file=sys.stderr)
            return 3

    t0 = time.time()
    rows, buckets = [], {}
    for spec in selected:
        for case in gen_cases(spec):
            case["values"] = make_values(spec, case, fx["go"], run_id)
            res = run_case(case, fx)
            bucket, why = classify(case, res)
            buckets[bucket] = buckets.get(bucket, 0) + 1
            rows.append((f"{spec['name']}.{case['field']}.{case['boundary']}",
                         bucket, why, res, case))
    dt = time.time() - t0

    new = [r for r in rows if r[1] == "NEW_DRIFT"]
    fault = [r for r in rows if r[1] == "TOOL_FAULT"]

    # 给每条 NEW_DRIFT 归根因;归不进去的进 unclassified —— 那是要立刻查的信号
    clusters, unclassified = {}, []
    for r in new:
        rid, title = root_cause(r[4], r[3])
        r_id = rid or "UNCLASSIFIED"
        clusters.setdefault(r_id, []).append(r)
        if rid is None:
            unclassified.append(r)

    out = []
    out.append(f"E-3 DTO 边界矩阵 —— {dt:.1f}s  共 {len(rows)} 条   run_id={run_id}")
    out.append(f"端口 go={{user:{BASE['go']['user']}, admin:{BASE['go']['admin']}}} "
               f"rag={{user:{BASE['rag']['user']}, admin:{BASE['rag']['admin']}}}")
    out.append("")
    for b in ("MATCH", "KNOWN_FORM_EMPTY", "KNOWN_FRAMEWORK_TEXT", "NEW_DRIFT", "TOOL_FAULT"):
        if b in buckets:
            out.append(f"  {b:22s} {buckets[b]:4d}")
    out.append("")
    out.append("  根因聚类:")
    for rid, title, _ev in ROOT_CAUSES:
        n = len(clusters.get(rid, []))
        out.append(f"    {rid}  {n:4d}  {title}")
    if unclassified:
        out.append(f"    !! UNCLASSIFIED  {len(unclassified):4d}  "
                   f"← 四个已知根因解释不了,必须重新取证")
    out.append("")
    if unclassified:
        out.append("── UNCLASSIFIED 明细(优先处理)──")
        for name, _, why, res, _c in unclassified:
            g, r = res["go"], res["rag"]
            out.append(f"  {name:46s} go=({g['status']},{g['shape']},{g['code']}) "
                       f"rag=({r['status']},{r['shape']},{r['code']})")
        out.append("")
    if new:
        out.append("── NEW_DRIFT 明细 ──")
        for rid in list(clusters.keys()):
            out.append(f"  [{rid}] {len(clusters[rid])} 条")
            for name, _, why, res, _c in clusters[rid]:
                g, r = res["go"], res["rag"]
                out.append(f"  {name:46s} go=({g['status']},{g['shape']},{g['code']}) "
                           f"rag=({r['status']},{r['shape']},{r['code']})")
        out.append("")
    if fault:
        out.append("── TOOL_FAULT 明细 ──")
        for name, _, why, res, _c in fault:
            g, r = res["go"], res["rag"]
            out.append(f"  {name:46s} go=({g['status']},{g['shape']}) rag=({r['status']},{r['shape']})")
        out.append("")
    if args.verbose:
        out.append("── 全部用例 ──")
        for name, bucket, why, res, _c in rows:
            g, r = res["go"], res["rag"]
            out.append(f"  {name:46s} {bucket:20s} go=({g['status']},{g['code']}) "
                       f"rag=({r['status']},{r['code']})")

    text = "\n".join(out)
    print(text)
    with open(args.out, "w", encoding="utf-8") as fh:
        fh.write(text + "\n")
    print(f"\n(结果已写入 {args.out})")
    return 1 if new else 0


if __name__ == "__main__":
    sys.exit(main())