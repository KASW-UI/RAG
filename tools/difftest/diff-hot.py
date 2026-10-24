#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
════════════════════════════════════════════════════════════════════════════
 diff-hot.py —— hot 域端到端双侧活体对拍（U1 / D1）
════════════════════════════════════════════════════════════════════════════

【对的是什么】
  Go 真源 service/hot/api（go-zero REST 栈）  vs  RAG service/hot/hot-api（Spring MVC 栈）
  同一个请求 → **响应体逐字节** + **响应头逐项** 比对。

【为什么下游是桩（而不是真 hot-rpc / 真 article-rpc）】
  hot-api 的链路是 HTTP → hot-rpc.GetHotArticles(100) → article-rpc.GetArticle(每条)。
  若用真下游，两侧会引入两个**与 hot-api 无关**的不确定源：
    ① 两侧 Kafka 消费进度不同 ⇒ 热榜内容/顺序天然可能不同；
    ② 两侧 Redis 快照写入时机不同 ⇒ 同一时刻读到的榜单可能不同。
  那是「对拍环境」的漂移，不是 hot-api 的漂移 ⇒ 判别力归零。
  ⇒ 下游改成**同构双桩**（配置完全相同、固定数据），任何字节差异都 100% 归因于 hot-api。
  代价：hot-rpc 自身的行为不在本脚本的覆盖范围内（那属 hot/rpc.md 的 D1）。

【三层断言（逐 case 都做）】
  ① 跨侧**状态码**相同
  ② 跨侧**响应体逐字节**相同（不是 JSON 语义比对 —— JSON 比对会放过字段顺序、
     null vs []、uint32 溢出这类差异，而那正是本域最常踩的坑）
  ③ 跨侧**关键响应头**相同（content-type / x-content-type-options / allow …）
  外加：
  ④ 每条 case 的**期望值**（来自 Go 真源语义，台账 CASE 表）必须在**两侧**都成立
  ⑤ 两侧桩的**下游调用序列**（多重集）相同 ⇒ 证明两侧调的下游方法/参数/条数一致

【响应头比对的取舍】
  **硬比对**：content-type / x-content-type-options / allow
      —— x-content-type-options 正是 D14（Go 的 http.Error 带 nosniff，RAG 的
         503/499 缺）——它必须进硬比对，否则 D14 永远发现不了。
  **软比对**（只报不判）：content-length
      —— 框架对小响应是否预填 Content-Length 属于传输层细节；
         但长度不同必然意味着 body 不同，body 那层已经硬比对了。
  **忽略**：date / server / connection / keep-alive / transfer-encoding
      —— 与业务语义无关的逐请求随机/实现相关头。

【用法】
  python3 diff-hot.py            # 跑全部 case
  python3 diff-hot.py --only CASE-05
════════════════════════════════════════════════════════════════════════════
"""

import argparse
import json
import os
import sys
import urllib.error
import urllib.request

# ── 对拍端点 ────────────────────────────────────────────────────────────────
GO_BASE = os.environ.get("GO_HOT_API_BASE", "http://127.0.0.1:18903")
RAG_BASE = os.environ.get("RAG_HOT_API_BASE", "http://127.0.0.1:58903")
HOT_PATH = "/hot/v1/articles"

# ── 桩的控制面 ──────────────────────────────────────────────────────────────
RUN_DIR = os.environ.get("HOT_DIFFTEST_DIR", "/tmp/hot-difftest")
MODE_FILE = os.path.join(RUN_DIR, "stub-mode")
GO_CALLS = os.path.join(RUN_DIR, "calls-go.log")
RAG_CALLS = os.path.join(RUN_DIR, "calls-rag.log")

# ── 响应头分层 ──────────────────────────────────────────────────────────────
# content-type 在比对时**拆成两段**（媒体类型 / 参数）单独判，见 split_media 的说明。
# x-content-type-options 正是 D14（Go 的 http.Error 带 nosniff，RAG 的 503/499 缺）
#   ⇒ 它必须进硬比对，否则 D14 永远发现不了。
HARD_HEADERS = ("content-type", "x-content-type-options", "allow")
SOFT_HEADERS = ("content-length",)
IGNORED_HEADERS = ("date", "server", "connection", "keep-alive",
                   "transfer-encoding", "content-encoding")

# 媒体类型归一：只保留「媒体类型」与「参数」两段，便于分开比对。
#
# ⚠️ **为什么 Content-Type 要拆成两段判**
#   Go 侧 httpx 写死 `header.ContentTypeJson = "application/json; charset=utf-8"`
#   （go-zero v1.10.2 rest/internal/header/headers.go:17，**分号后有空格**）；
#   Spring 的 MappingJackson2HttpMessageConverter 写 `application/json`（**无 charset**）。
#   ⇒ 媒体类型（application/json）**相同**，差的是 charset 参数。
#   ⇒ 媒体类型不同 = 语义不同 ⇒ 硬失败；
#      参数不同 = 已知差异 ⇒ 归入「已记录差异」，不判失败但**必须出现在报告里**。
#      （另注：即使 RAG 侧显式补 charset，Spring 的 MediaType.toString() 产出的是
#        `application/json;charset=utf-8`（**无空格**），与 Go 的**有空格**仍不逐字节相同 ⇒
#        这条差异在 Spring 侧不可逐字节复刻 ⇒ 属"有意偏离"候选，不是"忘了修"。）
_MEDIA_WS = ("; ", " ;")


def split_media(value):
    """返回 (媒体类型, 参数串)。媒体类型小写；参数保留原样（去掉分隔符两侧空白）。"""
    if value is None:
        return (None, None)
    parts = value.split(";")
    media = parts[0].strip().lower()
    params = ";".join(p.strip() for p in parts[1:]).strip()
    return (media, params if params else None)




# ── HTTP 取数 ──────────────────────────────────────────────────────────────
def http(method, url, body=None, headers=None, timeout=30):
    """返回 (status, body_bytes, headers_dict)。HTTP 错误状态也正常返回，不抛。

    ⚠️ headers 的键一律**小写**化：`dict(resp.headers)` 保留服务端原始大小写
    （"Content-Type" / "content-type" 都可能），直接按小写查会漏。
    """
    data = body
    if isinstance(data, str):
        data = data.encode("utf-8")
    req = urllib.request.Request(url, data=data, headers=headers or {}, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, resp.read(), lower_keys(dict(resp.headers))
    except urllib.error.HTTPError as e:
        return e.code, e.read(), lower_keys(dict(e.headers))


def lower_keys(headers):
    return {k.lower(): v for k, v in headers.items()}


def jget(obj, dotted):
    """按 "data.items.0.rank" 这样的点路径取值；取不到返回 None。

    ⚠️ 中间可以穿数组（"items.0"）—— 期望值里要断言到具体条目时用得到。
    """
    cur = obj
    for part in dotted.split("."):
        if isinstance(cur, dict) and part in cur:
            cur = cur[part]
        elif isinstance(cur, list) and part.isdigit() and int(part) < len(cur):
            cur = cur[int(part)]
        else:
            return None
    return cur


def as_json(raw):
    try:
        return json.loads(raw.decode("utf-8"))
    except Exception:
        return None


# ── 桩控制 ────────────────────────────────────────────────────────────────
def set_mode(mode):
    with open(MODE_FILE, "w", encoding="utf-8") as f:
        f.write(mode)


def clear_call_logs():
    for p in (GO_CALLS, RAG_CALLS):
        try:
            if os.path.exists(p):
                os.remove(p)
        except OSError:
            pass


def read_call_log(path):
    try:
        with open(path, "r", encoding="utf-8") as f:
            return sorted(l.strip() for l in f if l.strip())
    except OSError:
        return []


# ── case 定义 ─────────────────────────────────────────────────────────────
# 字段说明：
#   id       用例号（对齐台账 docs/DRIFT/hot/api.md §8 的 CASE 表）
#   desc     描述
#   mode     桩模式（决定下游行为）
#   method   HTTP 方法
#   path     请求路径（默认 /hot/v1/articles）
#   query    query 串（**不含**前导 ?）；可以是 str 或 (str, int) 元组表示重复键
#   body     请求体 bytes/str
#   headers  额外请求头
#   expect   期望值：{"点路径": 值}，必须在**两侧**都成立
#   status   期望状态码
#   body_eq  期望响应体（逐字节）
#   no_call_log  True ⇒ 不比对桩调用日志（超时等场景调用还在飞）
def q(pairs):
    """构造 query 串；pairs 可以是 str 或 [(k,v), ...]（重复键）。"""
    if isinstance(pairs, str):
        return pairs
    parts = []
    for k, v in pairs:
        parts.append("%s=%s" % (k, v))
    return "&".join(parts)


CASES = [
    # ── CASE-01 正常路径（非空热榜）────────────────────────────────────────
    dict(id="CASE-01", desc="正常成功：非空热榜 + 补全 + 分页信封",
         mode="normal", method="GET",
         expect={"code": 200, "msg": "OK", "data.total": 3, "data.page": 1,
                 "data.page_size": 20, "data.scope": "rolling",
                 "data.items.0.rank": 1,
                 "data.items.2.rank": 3}),

    # CASE-01b：与 CASE-01 的唯一差别 = a-4 的补全也成功 ⇒ total 4（少 1 的对照）
    dict(id="CASE-01b", desc="正常成功：全部补全成功（对照 CASE-01 ⇒ total 少 1）",
         mode="all-ok", method="GET",
         expect={"code": 200, "data.total": 4, "data.page": 1, "data.page_size": 20}),

    # ── CASE-02 空值（Go：空值被整个丢弃 ⇒ 取默认值，不报错）────────────────
    dict(id="CASE-02a", desc="空值 ?page= ⇒ 取默认 1，不报错",
         mode="normal", method="GET", query="page=",
         expect={"code": 200, "data.page": 1, "data.page_size": 20}),
    dict(id="CASE-02b", desc="无值 ?page ⇒ 取默认 1，不报错",
         mode="normal", method="GET", query="page",
         expect={"code": 200, "data.page": 1, "data.page_size": 20}),
    dict(id="CASE-02c", desc="空值 ?page_size= ⇒ 取默认 20",
         mode="normal", method="GET", query="page_size=",
         expect={"code": 200, "data.page": 1, "data.page_size": 20}),

    # ── CASE-03 空热榜（items 必须是非 null 的空数组）──────────────────────
    dict(id="CASE-03", desc="空热榜：items=[] 且 total=0（不是 null）",
         mode="hot-rpc-empty", method="GET",
         expect={"code": 200, "data.total": 0, "data.items": []}),

    # ── CASE-04 同名多值（Go 取首值，不是 "5,3"）──────────────────────────
    dict(id="CASE-04a", desc="重复键 ?page=5&page=3 ⇒ 取首值 5",
         mode="normal", method="GET", query=[("page", "5"), ("page", "3")],
         expect={"code": 200, "data.page": 5, "data.page_size": 20}),
    dict(id="CASE-04b", desc="重复键（数组记法）?page[]=5&page[]=9 ⇒ 取首值 5",
         mode="normal", method="GET", query=[("page[]", "5"), ("page[]", "9")],
         expect={"code": 200, "data.page": 5, "data.page_size": 20}),
    dict(id="CASE-04c", desc="单值数组记法 ?page[]=5 ⇒ 5",
         mode="normal", method="GET", query=[("page[]", "5")],
         expect={"code": 200, "data.page": 5, "data.page_size": 20}),

    # ── CASE-05 hot-rpc fatal（整包 5001，HTTP 仍 200）─────────────────────
    dict(id="CASE-05", desc="hot-rpc 失败 ⇒ fatal：整包 5001，HTTP 200",
         mode="hot-rpc-error", method="GET",
         expect={"code": 5001, "msg": "internal server error", "data": None}),

    # ── CASE-05b~e article-rpc 的 4 条 best-effort skip 分支 ───────────────
    dict(id="CASE-05b", desc="article-rpc 报错 ⇒ 跳过该条（总数减 1）",
         mode="normal", method="GET",
         expect={"code": 200, "data.total": 3}),
    dict(id="CASE-05c", desc="article-rpc 全部报错 ⇒ 全部跳过 ⇒ 空榜",
         mode="article-error", method="GET",
         expect={"code": 200, "data.total": 0, "data.items": []}),
    dict(id="CASE-05d", desc="article 缺失 ⇒ 跳过该条",
         mode="article-missing", method="GET",
         expect={"code": 200, "data.total": 0, "data.items": []}),
    dict(id="CASE-05e", desc="article 未发布（DRAFT）⇒ 跳过该条",
         mode="article-draft", method="GET",
         expect={"code": 200, "data.total": 0, "data.items": []}),

    # ── 分页归一化（Go normalizePagination）────────────────────────────────
    dict(id="CASE-07", desc="page_size=999 ⇒ 归一化为 50",
         mode="normal", method="GET", query="page_size=999",
         expect={"code": 200, "data.page_size": 50}),
    dict(id="CASE-08", desc="page=0 ⇒ 归一化为 1",
         mode="normal", method="GET", query="page=0",
         expect={"code": 200, "data.page": 1}),
    dict(id="CASE-09", desc="page=-1 ⇒ 归一化为 1",
         mode="normal", method="GET", query="page=-1",
         expect={"code": 200, "data.page": 1}),

    # ── 整数解析语义（Go strconv.ParseInt(str,10,32)）───────────────────────
    dict(id="CASE-10", desc="page=010 ⇒ base 10 ⇒ 10（不是八进制 8）",
         mode="normal", method="GET", query="page=010",
         expect={"code": 200, "data.page": 10}),
    dict(id="CASE-11", desc="page=0x10 ⇒ 解析失败 ⇒ 5001",
         mode="normal", method="GET", query="page=0x10",
         expect={"code": 5001, "msg": "internal server error"}),
    dict(id="CASE-12", desc="page=99999999999 ⇒ 超 int32 ⇒ 5001",
         mode="normal", method="GET", query="page=99999999999",
         expect={"code": 5001, "msg": "internal server error"}),

    # ── CASE-13 int32 乘法溢出 ⇒ 空页（不是报错、也不是大页码）──────────────
    dict(id="CASE-13", desc="int32 溢出分页 page=42949674&page_size=50 ⇒ 空页",
         mode="normal", method="GET", query="page=42949674&page_size=50",
         expect={"code": 200, "data.total": 3, "data.items": [],
                 "data.page": 42949674, "data.page_size": 50}),

    # ── CASE-14 rank 跨页连续（第 2 页首条 rank=2，不是 1）──────────────────
    dict(id="CASE-14", desc="page=2&page_size=1 ⇒ rank 跨页连续（=2）",
         mode="normal", method="GET", query="page=2&page_size=1",
         expect={"code": 200, "data.total": 3, "data.page": 2,
                 "data.page_size": 1, "data.items.0.rank": 2}),

    # ── CASE-15/16 form 值个数上限 2048（Go httpx/util.go:15）──────────────
    # ⚠️ 这两条的 query 串约 8KB —— 已逼近 Tomcat 的 8KB header 上限，
    #    而 Go 的 net/http 上限是 1MB。两侧的**拒绝层**可能不同：
    #    Go 在**应用层**拒（⇒ 5001），Tomcat 在**传输层**拒（⇒ 400）。
    #    这条差异本身就是对拍要抓的东西，不预设结论。
    dict(id="CASE-15", desc="form 值 2048 个 ⇒ 上限内",
         mode="normal", method="GET",
         query=("&".join("p%d=1" % i for i in range(2048)))),
    dict(id="CASE-16", desc="form 值 2049 个 ⇒ 超上限",
         mode="normal", method="GET",
         query=("&".join("p%d=1" % i for i in range(2049)))),

    # ── CASE-17/18 body（Go httpx.ParseJsonBody）───────────────────────────
    dict(id="CASE-17", desc="合法 JSON body ⇒ 字段被忽略，正常返回",
         mode="normal", method="GET",
         body=b'{"page": 99, "page_size": 99}',
         headers={"Content-Type": "application/json"},
         expect={"code": 200, "data.page": 1, "data.page_size": 20}),
    dict(id="CASE-18", desc="畸形 JSON body ⇒ 5001",
         mode="normal", method="GET",
         body=b'{"page": ',
         headers={"Content-Type": "application/json"},
         expect={"code": 5001, "msg": "internal server error"}),

    # ── CASE-19/20 路由层（不进任何 handler ⇒ 裸响应）──────────────────────
    dict(id="CASE-19", desc="不存在的路径 ⇒ 404 裸响应（text/plain + nosniff）",
         mode="normal", method="GET", path="/hot/v1/nope",
         status=404, body_eq=b"404 page not found\n"),
    dict(id="CASE-20", desc="方法不匹配 ⇒ 405 裸状态码（无 body）",
         mode="normal", method="POST", status=405, body_eq=b""),

    # ── CASE-21 HTTP 3s 超时（Go observability http.go:88）────────────────
    # ⚠️ 放最后：慢模式下 gRPC 调用仍在飞，会污染之后的桩调用日志。
    dict(id="CASE-21", desc="下游拖过 3s ⇒ 503 Request Timeout（校验 nosniff）",
         mode="article-slow", method="GET",
         status=503, body_eq=b"Request Timeout\n", no_call_log=True),
]


# ── 已知漂移（case id → 根因 + 台账编号）──────────────────────────────────
# ⚠️ 这些用例**故意保持 FAIL**：它们是**真实漂移**，不是 harness 的噪声。
#   标注只是让报告自带解释，避免每次都要翻台账才知道「这 5 条为什么红」。
#   ⇒ 修完（且改完对拍配方）后，请把对应条目从这里移除 —— 那是「漂移已闭合」的记号。
KNOWN_DIFFS = {
    "CASE-04b": "Tomcat 请求目标字符校验拒绝 `[` `]` ⇒ 400 + HTML 错误页"
                "（DRIFT-HOTAPI-021；修法 server.tomcat.relaxed-query-chars='[,]'）",
    "CASE-04c": "同上（DRIFT-HOTAPI-021）",
    "CASE-15": "Tomcat maxHttpHeaderSize 8 KiB ⇒ 400 + HTML 错误页"
               "（DRIFT-HOTAPI-023；修法 server.max-http-request-header-size=1MB）",
    "CASE-16": "Tomcat maxParameterCount 1000 ⇒ 1001 起就 5001"
               "（DRIFT-HOTAPI-022；修法 server.tomcat.max-parameter-count=2048）",
    "CASE-21": "503 响应缺 X-Content-Type-Options: nosniff"
               "（D14，代码在 service/common ⇒ 已移交 common 域）",
}


# ── 主流程 ────────────────────────────────────────────────────────────────
def build_url(base, case):
    path = case.get("path", HOT_PATH)
    url = base + path
    query = case.get("query")
    if query:
        url += "?" + q(query)
    return url


def run_case(case):
    """返回 (ok, problems, notes)。notes = 已记录差异（不判失败，但必须进报告）。"""
    problems = []
    notes = []
    set_mode(case["mode"])
    clear_call_logs()

    go_url = build_url(GO_BASE, case)
    rag_url = build_url(RAG_BASE, case)
    method = case.get("method", "GET")
    body = case.get("body")
    headers = case.get("headers")

    go_status, go_body, go_head = http(method, go_url, body=body, headers=headers)
    rag_status, rag_body, rag_head = http(method, rag_url, body=body, headers=headers)

    # ① 状态码
    if go_status != rag_status:
        problems.append("状态码不同：Go=%s RAG=%s" % (go_status, rag_status))
    if "status" in case and go_status != case["status"]:
        problems.append("Go 状态码与期望不符：期望=%s 实际=%s" % (case["status"], go_status))
    if "status" in case and rag_status != case["status"]:
        problems.append("RAG 状态码与期望不符：期望=%s 实际=%s" % (case["status"], rag_status))

    # ② 响应体逐字节
    if go_body != rag_body:
        problems.append("响应体逐字节不同：\n"
                        "        Go  = %r\n"
                        "        RAG = %r" % (go_body[:400], rag_body[:400]))
    if "body_eq" in case and go_body != case["body_eq"]:
        problems.append("Go 响应体与期望不符：期望=%r 实际=%r" % (case["body_eq"], go_body[:200]))
    if "body_eq" in case and rag_body != case["body_eq"]:
        problems.append("RAG 响应体与期望不符：期望=%r 实际=%r" % (case["body_eq"], rag_body[:200]))

    # ③ 关键响应头
    #    Content-Type 拆两段：媒体类型硬比、参数差异记为 note（见 split_media 的说明）
    go_media, go_params = split_media(go_head.get("content-type"))
    rag_media, rag_params = split_media(rag_head.get("content-type"))
    if go_media != rag_media:
        problems.append("响应头 content-type 的媒体类型不同：Go=%r RAG=%r"
                        % (go_media, rag_media))
    elif go_params != rag_params:
        notes.append("content-type 参数不同：Go=%r RAG=%r" % (go_params, rag_params))
    for h in ("x-content-type-options", "allow"):
        gv = go_head.get(h)
        rv = rag_head.get(h)
        if gv != rv:
            problems.append("响应头 %s 不同：Go=%r RAG=%r" % (h, gv, rv))
    for h in SOFT_HEADERS:
        gv = go_head.get(h)
        rv = rag_head.get(h)
        if gv != rv:
            notes.append("[软] 响应头 %s 不同：Go=%r RAG=%r" % (h, gv, rv))

    # ④ 期望值（两侧都要成立）
    expect = case.get("expect")
    if expect:
        for path, want in expect.items():
            for label, raw in (("Go", go_body), ("RAG", rag_body)):
                obj = as_json(raw)
                if obj is None:
                    problems.append("%s 响应体不是合法 JSON，无法校验 %s" % (label, path))
                    break
                got = jget(obj, path)
                if got != want:
                    problems.append("%s 的 %s 与期望不符：期望=%r 实际=%r"
                                    % (label, path, want, got))

    # ⑤ 桩调用序列（多重集）
    if not case.get("no_call_log"):
        go_calls = read_call_log(GO_CALLS)
        rag_calls = read_call_log(RAG_CALLS)
        if go_calls != rag_calls:
            problems.append("下游调用序列不同：\n"
                            "        Go  = %s\n"
                            "        RAG = %s" % (go_calls, rag_calls))

    return (len(problems) == 0), problems, notes


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", help="只跑某个用例号")
    args = ap.parse_args()

    cases = CASES
    if args.only:
        cases = [c for c in CASES if c["id"] == args.only]
        if not cases:
            print("没有匹配的用例：%s" % args.only)
            return 2

    print("=" * 78)
    print("hot 域端到端双侧活体对拍")
    print("  Go  = %s" % GO_BASE)
    print("  RAG = %s" % RAG_BASE)
    print("  用例数 = %d" % len(cases))
    print("=" * 78)

    failed = []
    note_set = []
    for case in cases:
        ok, problems, notes = run_case(case)
        if ok:
            print("  ✓ %-10s %s" % (case["id"], case["desc"]))
        else:
            print("  ✗ %-10s %s" % (case["id"], case["desc"]))
            for p in problems:
                print("        · %s" % p)
            if case["id"] in KNOWN_DIFFS:
                print("        ↳ 已知漂移：%s" % KNOWN_DIFFS[case["id"]])
            failed.append(case["id"])
        for n in notes:
            if n not in note_set:
                note_set.append(n)

    print("-" * 78)
    if note_set:
        print("已记录差异（不判失败，但需登记台账）：")
        for n in note_set:
            print("  · %s" % n)
        print("-" * 78)
    if failed:
        print("结果：%d/%d 通过；失败用例：%s" % (len(cases) - len(failed), len(cases),
                                                 ", ".join(failed)))
        unknown = [c for c in failed if c not in KNOWN_DIFFS]
        if unknown:
            print("  ⚠️ 其中**未登记**的失败：%s ⇒ 属新查出的漂移，需回填台账"
                  % ", ".join(unknown))
        else:
            print("  ⇒ 全部失败项均已在台账登记（见每条的「已知漂移」标注）")
        return 1
    print("结果：%d/%d 全部通过（响应体逐字节 + 关键响应头 + 期望值 + 下游调用序列）"
          % len(cases))
    return 0


if __name__ == "__main__":
    sys.exit(main())
