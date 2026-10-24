#!/usr/bin/env python3
"""
task 域 Go ↔ RAG 双侧活体对拍（HTTP wire 层）。

纪律（照 tools/difftest/README.md）：
  · 两侧各自独立的 PG 库 / 独立的进程，只共享「同一串请求序列」；
  · 易变值做归一化，但**类型差异不抹平**；
  · 探针必须显式拒绝 000 / 404（把「打到别人服务」误判成就绪，见 README §4.1）。

与 user 域不同：task-api **无状态机**（没有邀请码/会被消费掉的 token），
所以两侧可以交错执行，不存在「后一侧捡到前一侧的残局」。
"""
import json
import http.client
import subprocess
import sys
import urllib.request
import urllib.error
from urllib.parse import urlparse

GO_API = "http://127.0.0.1:18886"
RAG_API = "http://127.0.0.1:58886"
GO_DB = "task_go_db"
RAG_DB = "task_rag_db"
REDIS = "taskprobe-redis"

results = []
# 已知全域共性差异（不计入 DIFF，但必须单独列出来 —— 不藏）
known_shared = []


def psql(db, sql):
    r = subprocess.run(
        ["docker", "exec", "taskprobe-postgres", "psql", "-U", "admin", "-d", db,
         "-v", "ON_ERROR_STOP=1", "-tAc", sql],
        capture_output=True, text=True)
    if r.returncode != 0:
        raise RuntimeError(f"psql({db}) failed: {r.stderr.strip()}")
    return r.stdout.strip()


def redis(*args):
    return subprocess.run(["docker", "exec", REDIS, "redis-cli", *args],
                          capture_output=True, text=True).stdout.strip()


def post(base, body, path="/taskcenter/v1/task/get", method="POST",
         content_type="application/json"):
    """返回 (status, content_type, nosniff, body_text)。异常路径也返回结构化结果。"""
    url = base + path
    data = body.encode() if isinstance(body, str) else body
    req = urllib.request.Request(url, data=data, method=method)
    if content_type is not None:
        req.add_header("Content-Type", content_type)
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            return (resp.status, resp.headers.get("Content-Type"),
                    resp.headers.get("X-Content-Type-Options"),
                    resp.read().decode("utf-8", "replace"))
    except urllib.error.HTTPError as e:
        return (e.code, e.headers.get("Content-Type"),
                e.headers.get("X-Content-Type-Options"),
                e.read().decode("utf-8", "replace"))
    except Exception as e:  # noqa: BLE001
        return (0, None, None, f"TRANSPORT_ERROR: {e}")


def post_bare(base, body, path="/taskcenter/v1/task/get", method="POST"):
    """**真的**不带 Content-Type 发一个 POST。

    ⚠️ 为什么不能用 urllib：它在没有显式 Content-Type 时会**自动补**
       application/x-www-form-urlencoded ⇒ 那样测到的其实是「带 form-urlencoded」，
       不是「不带 Content-Type」⇒ **探针自欺**（README §4.1 同款）。
       http.client 只自动补 Host / Content-Length，**不会**补 Content-Type ✔。
    """
    u = urlparse(base)
    conn = http.client.HTTPConnection(u.hostname, u.port or 80, timeout=15)
    try:
        conn.request(method, path, body=body.encode())
        resp = conn.getresponse()
        data = resp.read().decode("utf-8", "replace")
        return (resp.status, resp.getheader("Content-Type"),
                resp.getheader("X-Content-Type-Options"), data)
    except Exception as e:  # noqa: BLE001
        return (0, None, None, f"TRANSPORT_ERROR: {e}")
    finally:
        conn.close()


def norm_ct(ct):
    """把 Content-Type 归一到「媒体类型」形态：小写、去空白、去掉 charset 参数。

    ⚠️ **为什么必须归一**：DRIFT-TASK-API-004 记的是**全域共性**差异 ——
    Go 的 `httpx.WriteJsonCtx` 写 `application/json; charset=utf-8`，
    RAG 写 `application/json`（少了 charset，且 `;` 后无空格）。
    按 RFC 9110，`;` 后的 OWS 本就允许省略 ⇒ 这不是语义差异。
    ⇒ 归一后判定，**但原值差异要单独列出来**（不藏）。
    ⚠️ 反过来：归一只吃 charset 与空白，**媒体类型本身不同**（如 application/json vs text/plain）
       一律仍算差异。
    """
    if not ct:
        return ""
    parts = [p.strip() for p in ct.split(";")]
    media = parts[0].strip().lower()
    params = set()
    for p in parts[1:]:
        if "=" in p:
            k, v = p.split("=", 1)
            params.add(k.strip().lower())
        else:
            params.add(p.strip().lower())
    params.discard("charset")
    return media + (";" + ";".join(sorted(params)) if params else "")


def record(case, desc, go, rag, *, expect_body_equal=True):
    gs, gct, gns, gb = go
    rs, rct, rns, rb = rag
    diffs = []
    known = []
    if gs != rs:
        diffs.append(f"status Go={gs} RAG={rs}")
    if norm_ct(gct) != norm_ct(rct):
        # 媒体类型本身不同 ⇒ 语义差异
        diffs.append(f"content-type Go={gct!r} RAG={rct!r}")
    elif (gct or "").lower() != (rct or "").lower():
        # 归一后一致、原值不同 ⇒ **已知的全域共性**（DRIFT-TASK-API-004）
        known.append(f"content-type 格式 Go={gct!r} RAG={rct!r}（DRIFT-TASK-API-004 全域共性，不在本域修）")
    if (gns or "") != (rns or ""):
        diffs.append(f"nosniff Go={gns!r} RAG={rns!r}")
    if expect_body_equal and gb != rb:
        diffs.append(f"body Go={gb!r} RAG={rb!r}")
    verdict = "MATCH" if not diffs else "DIFF"
    results.append((verdict, case, desc, diffs, go, rag))
    if known:
        known_shared.append((case, known))
    print(f"[{verdict:4}] {case:<34} {desc}")
    for d in diffs:
        print(f"        ↳ {d}")
        print(f"          Go  = {gb!r}" if "body" in d else "", end="")
        print(f"\n          RAG = {rb!r}" if "body" in d else "" if "body" in d else "")


# ---------------------------------------------------------------- 种子数据
SEED = """
TRUNCATE task_progress;
INSERT INTO task_progress (user_id, task_id, status, progress, target, done_at) VALUES
  (1001, 190,  'doing', 2, 3, NULL),
  (1001, 1902, 'done',  5, 5, now()),
  (1002, 190,  'doing', 1, 3, NULL),
  (1004, 190,  NULL,    NULL, NULL, NULL),
  (1005, 190,  'doing', 9, 3, NULL);
"""

print("=== 0. 灌种子数据（两侧各一份，完全相同）===")
for db in (GO_DB, RAG_DB):
    psql(db, SEED)
    n = psql(db, "SELECT count(*) FROM task_progress;")
    print(f"  {db}: task_progress {n} 行")

print("\n=== 1. 缓存清空（强制走 DB 回源路径）===")
redis("FLUSHALL")
print("  Redis FLUSHALL done")

print("\n=== 2. 请求矩阵 ===")

# ---- 2.1 id 参数解析矩阵（都在 api 层就判定，不到 rpc）----
PARSE_CASES = [
    ("id-null",            '{"id":null}'),
    ("id-absent",          '{}'),
    ("id-string-plain",    '{"id":"1001"}'),
    ("id-string-padded",   '{"id":"  1001  "}'),
    ("id-string-empty",    '{"id":"   "}'),
    ("id-int",             '{"id":1001}'),
    ("id-int-zero",        '{"id":0}'),
    ("id-int-negative",    '{"id":-5}'),
    ("id-string-zero",     '{"id":"0"}'),
    ("id-string-negative", '{"id":"-5"}'),
    ("id-float",           '{"id":1.5}'),
    ("id-float-whole",     '{"id":1.0}'),
    ("id-bool",            '{"id":true}'),
    ("id-array",           '{"id":[1,2]}'),
    ("id-object",          '{"id":{"a":1}}'),
    ("id-abc",             '{"id":"abc"}'),
    ("id-oversize",        '{"id":"99999999999999999999999"}'),
    ("id-plus-signed",     '{"id":"+1001"}'),
]
for case, body in PARSE_CASES:
    record(case, f"解析矩阵 {body}", post(GO_API, body), post(RAG_API, body))

# ---- 2.2 正常读路径（DB 回源）----
for uid in (1001, 1002, 1003, 1004, 1005):
    record(f"db-path-uid{uid}", "DB 回源 + 任务定义合并",
           post(GO_API, json.dumps({"id": uid})),
           post(RAG_API, json.dumps({"id": uid})))

# ---- 2.3 Redis 缓存命中路径：手工塞「打包值」，覆盖 unpackProgress 的分段/解析分支 ----
#  Go: packProgress = "status|progress|target"；unpackProgress 要求**恰好 3 段**
CACHE_CASES = {
    # uid -> 该 uid 的 hash 内容（field=taskId）
    2001: {"190": "doing|2|3", "1902": "done|5|5"},   # 正常三段
    2002: {"190": "doing|5"},                          # 两段 => Go 返回 0/0/0 => progress=0
    2003: {"190": "doing|5|3|extra"},                  # 四段 => 同上
    2004: {"190": "||"},                               # 空 status/空 progress
    2005: {"190": "doing||3"},                         # progress 空串 => 回落 0
    2006: {"190": "doing|abc|3"},                      # progress 非法 => 回落 0
    2007: {"190": "doing|-7|3"},                       # 负数（注意 unpack 不夹取）
    2008: {"190": "done|99|3"},                        # 超 target（GetTask 不夹取，直接回 99）
    2009: {},                                          # 空 hash => 等价于 miss
}
for uid, fields in CACHE_CASES.items():
    redis("DEL", f"task:progress:{uid}")
    for fid, val in fields.items():
        redis("HSET", f"task:progress:{uid}", fid, val)
    record(f"cache-path-uid{uid}", f"Redis 命中 {fields or '{}'}",
           post(GO_API, json.dumps({"id": uid})),
           post(RAG_API, json.dumps({"id": uid})))

# ---- 2.4 status 里带 '|' 的值（Go packProgress 注释明确禁止）----
redis("DEL", "task:progress:2010")
redis("HSET", "task:progress:2010", "190", "do|ing|2|3")   # 4 段
record("cache-status-with-pipe", "status 含 '|' ⇒ 4 段 ⇒ 回落 0",
       post(GO_API, json.dumps({"id": 2010})),
       post(RAG_API, json.dumps({"id": 2010})))

# ---- 2.5 路由层：方法 / 路径 ----
record("route-get-instead-of-post", "GET /taskcenter/v1/task/get",
       post(GO_API, "", method="GET"), post(RAG_API, "", method="GET"),
       expect_body_equal=False)
record("route-unknown-path", "POST /taskcenter/v1/task/nope",
       post(GO_API, "{}", path="/taskcenter/v1/task/nope"),
       post(RAG_API, "{}", path="/taskcenter/v1/task/nope"),
       expect_body_equal=False)
record("route-no-prefix", "POST /task/get（缺 /taskcenter/v1）",
       post(GO_API, "{}", path="/task/get"),
       post(RAG_API, "{}", path="/task/get"),
       expect_body_equal=False)

# ---- 2.5b Content-Type 矩阵（Go handler 完全不看 Content-Type）----
for ct in ["application/json", "text/plain", "application/xml",
           "application/x-www-form-urlencoded", "multipart/form-data"]:
    record(f"ctype-{ct}", f"POST Content-Type: {ct}",
           post(GO_API, json.dumps({"id": 1001}), content_type=ct),
           post(RAG_API, json.dumps({"id": 1001}), content_type=ct))

# ---- 2.6 畸形 JSON / 缺 body ----
record("body-malformed", "畸形 JSON",
       post(GO_API, "{not json"), post(RAG_API, "{not json"),
       expect_body_equal=False)
record("body-empty", "空 body",
       post(GO_API, ""), post(RAG_API, ""),
       expect_body_equal=False)

# ---- 2.7 无 Content-Type（Go 的 handler 不校验 Content-Type）----
# ⚠️ 不能用 urllib：它会在没有显式 Content-Type 时**自动补** application/x-www-form-urlencoded，
#    那样测的就不是「不带 Content-Type」了（探针自欺，README §4.1 同款）。
#    这里用 http.client：它只自动补 Host / Content-Length，**不会**补 Content-Type。
record("ctype-none", "POST 完全不带 Content-Type",
       post_bare(GO_API, json.dumps({"id": 1001})),
       post_bare(RAG_API, json.dumps({"id": 1001})))

# ---------------------------------------------------------------- 汇总
print("\n" + "=" * 78)
match = [r for r in results if r[0] == "MATCH"]
diff = [r for r in results if r[0] == "DIFF"]
print(f"总计 {len(results)} 条：MATCH {len(match)} / DIFF {len(diff)}")
print(f"（判定口径：状态码 + **归一后**的 Content-Type 媒体类型 + nosniff + 响应体）")
if known_shared:
    print(f"\n已知全域共性差异 {len(known_shared)} 条（**不计入 DIFF**，但列出，不藏）：")
    for case, ks in known_shared:
        for k in ks:
            print(f"  · [{case}] {k}")
    print("  ⇒ 归 DRIFT-TASK-API-004：Go `httpx.WriteJsonCtx` 写 `application/json; charset=utf-8`，")
    print("    RAG 写 `application/json`（少 charset、且 `;` 后无空格）。")
    print("    按 RFC 9110，`;` 后的 OWS 本就允许省略 ⇒ 非语义差异。")
    print("    ⇒ 归**全域共性**，不在 task 域单独修（单独改会让本域与其它域不一致）。")
if diff:
    print("\n--- DIFF 明细 ---")
    for verdict, case, desc, diffs, go, rag in diff:
        print(f"\n[{case}] {desc}")
        for d in diffs:
            print(f"  · {d}")
        print(f"  Go  = {go}")
        print(f"  RAG = {rag}")
    # ⚠️ 有差异必须返回非零 —— 否则这个脚本没法当门禁用
    #    （对齐 tools/difftest/parity-task-kafka.py 的 return 1 约定）。
    print("=" * 78)
    sys.exit(1)
print("=" * 78)
print("✅ HTTP wire 层：Go 与 RAG 全部用例一致")
sys.exit(0)