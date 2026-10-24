#!/usr/bin/env python3
"""comment 域 Go ↔ RAG 双侧活体对拍：请求矩阵 + 逐字段差分。

设计（照 tools/difftest/diff.py 的既有形态）：
  - 两侧**各自**跑一遍完整序列（不能交错 —— 交错会让后一侧捡到前一侧已消费的状态）
  - 易变值（雪花 ID / 时间戳）归一化成占位符后再比
  - **类型差异不抹平**：Go 是 int64，RAG 若给字符串，会作为真实差异报出来
"""
import base64
import hashlib
import hmac
import json
import sys
import time
import urllib.request
import urllib.error

SECRET = b"ae0536f9-6450-4606-8e13-5a19ed505da0"
GO_API = "http://127.0.0.1:18888"
RAG_API = "http://127.0.0.1:58888"

VOLATILE_KEYS = {"id", "created_at", "updated_at"}


def b64u(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode()


def make_token(user_id: int, exp_delta: int = 3600, iat_delta: int = 0):
    """HS256 JWT —— claim 名必须是 userId（Go 的 ctx.Value("userId") / RAG 的 CLAIM_ATTRIBUTE）。

    ⚠️ iat_delta 可为正 ⇒ 造「iat 在未来」的 token：Go 的 MapClaims.Valid() 会拒，
       而 jjwt **不校验 iat** ⇒ 这一条专门验 RAG 侧补上的 iat 校验。
    """
    header = {"alg": "HS256", "typ": "JWT"}
    now = int(time.time())
    payload = {"userId": user_id, "exp": now + exp_delta, "iat": now + iat_delta}
    signing_input = (b64u(json.dumps(header, separators=(",", ":")).encode()) + "."
                     + b64u(json.dumps(payload, separators=(",", ":")).encode()))
    sig = hmac.new(SECRET, signing_input.encode(), hashlib.sha256).digest()
    return signing_input + "." + b64u(sig)


def post(base: str, path: str, body, token: str = None, raw_body: str = None,
         content_type: str = "application/json", method: str = "POST",
         raw_auth: str = None, body_bytes: int = None):
    """发一次请求并返回 (http_status, body_text)。

    ⚠️ 额外参数是为本轮新增的四个维度服务的：
       content_type（非 JSON ⇒ 验 415 vs 200+5001）
       method（GET ⇒ 验 405）
       raw_auth（原样传 Authorization 头 ⇒ 验 Bearer 长度边界）
       body_bytes（指定 body 字节数 ⇒ 验未注册路径上的 MaxBytes 保护层覆盖）
    """
    url = base + path
    if body_bytes is not None:
        data = b"x" * body_bytes
    else:
        data = raw_body.encode() if raw_body is not None else json.dumps(body).encode()
    req = urllib.request.Request(url, data=data, method=method)
    if content_type is not None:
        req.add_header("Content-Type", content_type)
    if raw_auth is not None:
        req.add_header("Authorization", raw_auth)
    elif token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.status, resp.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()
    except Exception as e:  # noqa: BLE001
        return 0, "TRANSPORT_ERROR: %r" % (e,)


def normalize(obj, ids: dict, timestamps: dict):
    """把易变值映射成稳定占位符（**不抹平类型**）。"""
    if isinstance(obj, dict):
        out = {}
        for k, v in obj.items():
            if k in VOLATILE_KEYS and v is not None and not isinstance(v, (dict, list)):
                if isinstance(v, int) and v != 0:
                    ids.setdefault(v, "<ID#%d>" % (len(ids) + 1))
                    out[k] = ids[v]
                    continue
                if isinstance(v, str) and v:
                    timestamps.setdefault(v, "<TS#%d>" % (len(timestamps) + 1))
                    out[k] = timestamps[v]
                    continue
            out[k] = normalize(v, ids, timestamps)
        return out
    if isinstance(obj, list):
        return [normalize(v, ids, timestamps) for v in obj]
    return obj


def build_sequence(user_id: int):
    """返回 (名称, 路径, body, 是否需要上一次创建的 comment_id)。"""
    return [
        ("T01-list-empty", "/comment/v1/comment/list",
         {"target_type": "article", "target_id": "a-diff", "sort_type": 0,
          "root_id": 0, "page": 1, "page_size": 20}, None),
        ("T02-create", "/comment/v1/comment/create",
         {"target_type": "article", "target_id": "a-diff", "root_id": 0, "parent_id": 0,
          "content": "hello diff", "meta": "{}"}, None),
        ("T03-list-after-create", "/comment/v1/comment/list",
         {"target_type": "article", "target_id": "a-diff", "sort_type": 0,
          "root_id": 0, "page": 1, "page_size": 20}, None),
        ("T04-like", "/comment/v1/comment/like",
         {"target_type": "article", "target_id": "a-diff", "comment_id": 0, "action_type": 1}, "CID"),
        ("T05-like-again-noop", "/comment/v1/comment/like",
         {"target_type": "article", "target_id": "a-diff", "comment_id": 0, "action_type": 1}, "CID"),
        ("T06-list-after-like", "/comment/v1/comment/list",
         {"target_type": "article", "target_id": "a-diff", "sort_type": 0,
          "root_id": 0, "page": 1, "page_size": 20}, None),
        ("T07-manage-pin", "/comment/v1/comment/manage",
         {"comment_id": 0, "target_type": "article", "target_id": "a-diff", "action_type": 2}, "CID"),
        ("T08-report", "/comment/v1/comment/report",
         {"comment_id": 0, "target_type": "article", "target_id": "a-diff",
          "reason": 1, "detail": "spam"}, "CID"),
        ("T09-create-empty-content", "/comment/v1/comment/create",
         {"target_type": "article", "target_id": "a-diff", "root_id": 0, "parent_id": 0,
          "content": "", "meta": "{}"}, None),
        ("T10-create-missing-fields", "/comment/v1/comment/create",
         {"target_type": "article"}, None),
        ("T11-list-page0", "/comment/v1/comment/list",
         {"target_type": "article", "target_id": "a-diff", "sort_type": 1,
          "root_id": 0, "page": 0, "page_size": 0}, None),
        ("T12-delete", "/comment/v1/comment/delete",
         {"comment_id": 0, "target_type": "article", "target_id": "a-diff"}, "CID"),
        ("T13-list-after-delete", "/comment/v1/comment/list",
         {"target_type": "article", "target_id": "a-diff", "sort_type": 0,
          "root_id": 0, "page": 1, "page_size": 20}, None),
        ("T14-subject-state", "/comment/v1/subject/state",
         {"target_type": "article", "target_id": "a-diff", "state": 1}, None),
        ("T15-like-unknown-comment", "/comment/v1/comment/like",
         {"target_type": "article", "target_id": "a-diff", "comment_id": 987654321, "action_type": 1}, None),
    ]


def settle(base, token, comment_id, want_visible=True, timeout=40.0):
    """轮询 list 接口直到「新建的评论可见 / 已删除」——create 与 delete 都是异步链路。

    ⚠️ 为什么必须轮询而不是固定 sleep：
      create 的落库在 Kafka 消费端（Go: mqs/audit_consumer.go；RAG: CommentAuditListener），
      请求返回 ≠ 已落库。实测 RAG 侧因「消费者组 rebalance 退避」迟到 19s，
      固定 sleep(3) 会把「还没落库」误判成语义差异 —— 那是 harness 假差异，不是漂移。
    """
    body = {"target_type": "article", "target_id": "a-diff", "sort_type": 0,
            "root_id": 0, "page": 1, "page_size": 20}
    deadline = time.time() + timeout
    while time.time() < deadline:
        st, text = post(base, "/comment/v1/comment/list", body, token)
        try:
            parsed = json.loads(text)
        except Exception:  # noqa: BLE001
            parsed = None
        data = (parsed or {}).get("data") or {}
        items = data.get("comment") or []
        hit = any(c.get("id") == comment_id for c in items if isinstance(c, dict))
        if hit == want_visible:
            return True, round(time.time() % 1000, 3)
        time.sleep(0.3)
    return False, round(time.time() % 1000, 3)


def run_side(base: str, label: str, user_id: int):
    token = make_token(user_id)
    results = []
    comment_id = None
    for name, path, body, needs_cid in build_sequence(user_id):
        payload = dict(body)
        if needs_cid == "CID" and comment_id is not None:
            payload["comment_id"] = comment_id
        status, text = post(base, path, payload, token)
        parsed = None
        try:
            parsed = json.loads(text)
        except Exception:  # noqa: BLE001
            pass
        if name == "T02-create" and isinstance(parsed, dict):
            data = parsed.get("data") or {}
            if isinstance(data, dict) and isinstance(data.get("id"), int):
                comment_id = data["id"]
        results.append((name, status, text, parsed))
        # 创建/删除是「推 Kafka → 消费端落库」的异步链路 ⇒ 轮询等落库，不能用固定 sleep
        if name == "T02-create" and comment_id is not None:
            ok, _ = settle(base, token, comment_id, want_visible=True)
            print("  [%s] 等 create 落库：%s" % (label, "✅" if ok else "❌ 超时"))
        if name == "T12-delete" and comment_id is not None:
            ok, _ = settle(base, token, comment_id, want_visible=False, timeout=15.0)
            print("  [%s] 等 delete 落库：%s" % (label, "✅" if ok else "❌ 超时"))
    # 无 token / 坏 token
    for label, tk in (("T16-no-token", None), ("T17-bad-token", "not.a.jwt")):
        st, tx = post(base, "/comment/v1/comment/list",
                      {"target_type": "article", "target_id": "a-diff",
                       "sort_type": 0, "root_id": 0, "page": 1, "page_size": 20}, token=tk)
        try:
            pr = json.loads(tx)
        except Exception:  # noqa: BLE001
            pr = None
        results.append((label, st, tx, pr))

    results.extend(run_extra_cases(base, label, token))
    return results


# ====================================================================================
# 2026-10-24 新增的四个维度（把此前的「未知项」变成活体证据）
# ====================================================================================
#
# ① 未注册路径 / 错误方法上的**保护层覆盖范围**
#    Go：rest/engine.go:103-118 bindRoute 把 chain（含 MaxBytes/MaxConns/Breaker/Shedding）
#         只挂在**匹配上的路由**上 ⇒ 未注册路径走 router 自己的 404/405 分支 ⇒ 不经保护层。
#    RAG：4 个保护是**全局 servlet Filter**（@Component + @Order）⇒ **所有路径**都经过。
#    ⇒ 预期差异：未注册路径 + 超大 body ⇒ Go 404 / RAG 413（MaxBytes 闸门）。
# ② 非 application/json 的 Content-Type ⇒ Go 200+{code:5001} / RAG 415（CAND-04）
# ③ Bearer 前缀剥离的长度边界（"Bearer " 恰好 7 字符）⇒ 两侧结局应相同（都是 401）
# ④ iat 在未来的 token ⇒ Go 拒（MapClaims.Valid）⇒ RAG 本轮补了 iat 校验后也应拒
def run_extra_cases(base: str, label: str, token: str):
    out = []

    def record(name, status, text):
        try:
            parsed = json.loads(text)
        except Exception:  # noqa: BLE001
            parsed = None
        out.append((name, status, text, parsed))

    list_body = {"target_type": "article", "target_id": "a-diff",
                 "sort_type": 0, "root_id": 0, "page": 1, "page_size": 20}

    # ①-a 未注册路径 + 正常 body（对照组：两侧都该是 404）
    st, tx = post(base, "/comment/v1/nope", list_body, token=token)
    record("T18-404-unregistered", st, tx)

    # ①-b 错误方法（GET 打在本端点）⇒ 两侧都该 405
    st, tx = post(base, "/comment/v1/comment/list", None, token=token, method="GET")
    record("T19-405-wrong-method", st, tx)

    # ①-c 未注册路径 + 超大 body（> Go/RAG 的 MaxBytes 1 MiB）⇒ 保护层覆盖范围的差异
    st, tx = post(base, "/comment/v1/nope", None, token=token, body_bytes=1500000)
    record("T20-404-oversized-body", st, tx)

    # ② 非 JSON 的 Content-Type
    st, tx = post(base, "/comment/v1/comment/list", list_body, token=token,
                  content_type="text/plain", raw_body="{}")
    record("T21-415-content-type", st, tx)

    # ③ Bearer 前缀恰好 7 字符（"Bearer " + 空 token）⇒ 两侧结局应相同（都是 401）
    st, tx = post(base, "/comment/v1/comment/list", list_body, raw_auth="Bearer ")
    record("T22-bearer-length-7", st, tx)

    # ④ iat 在未来的 token ⇒ Go 拒（MapClaims.Valid）⇒ RAG 本轮补了 iat 校验后也应拒
    future_token = make_token(1001, exp_delta=3600, iat_delta=3600)
    st, tx = post(base, "/comment/v1/comment/list", list_body, token=future_token)
    record("T23-iat-in-future", st, tx)

    return out


def diff_value(path, a, b, out):
    if type(a) is not type(b) and not (isinstance(a, (int, float)) and isinstance(b, (int, float))):
        out.append("%s: 类型不同 go=%s(%r) rag=%s(%r)" % (path, type(a).__name__, a, type(b).__name__, b))
        return
    if isinstance(a, dict):
        for k in sorted(set(a) | set(b)):
            if k not in a:
                out.append("%s.%s: 仅 RAG 有 → %r" % (path, k, b[k]))
            elif k not in b:
                out.append("%s.%s: 仅 Go 有 → %r" % (path, k, a[k]))
            else:
                diff_value("%s.%s" % (path, k), a[k], b[k], out)
    elif isinstance(a, list):
        if len(a) != len(b):
            out.append("%s: 数组长度不同 go=%d rag=%d" % (path, len(a), len(b)))
            return
        for i, (x, y) in enumerate(zip(a, b)):
            diff_value("%s[%d]" % (path, i), x, y, out)
    elif a != b:
        out.append("%s: 值不同 go=%r rag=%r" % (path, a, b))


def main():
    go = run_side(GO_API, "go", 1001)
    rag = run_side(RAG_API, "rag", 1001)

    go_ids, rag_ids = {}, {}
    go_ts, rag_ts = {}, {}
    same = 0
    diffs = []
    for (n1, s1, t1, p1), (n2, s2, t2, p2) in zip(go, rag):
        assert n1 == n2
        if s1 != s2:
            diffs.append("%s: HTTP 状态不同 go=%s rag=%s" % (n1, s1, s2))
        if p1 is None or p2 is None:
            if t1 != t2:
                diffs.append("%s: 原始响应不同\n    go =%r\n    rag=%r" % (n1, t1, t2))
            else:
                same += 1
            continue
        n1_ = normalize(p1, go_ids, go_ts)
        n2_ = normalize(p2, rag_ids, rag_ts)
        local = []
        diff_value(n1, n1_, n2_, local)
        if local:
            diffs.extend(local)
        else:
            same += 1

    print("=" * 78)
    print("comment 域双侧活体对拍：Go 18888 ↔ RAG 58888")
    print("步数 %d / 一致 %d / 差异 %d" % (len(go), same, len(diffs)))
    print("=" * 78)
    if diffs:
        print("\n🔴 差异明细：")
        for d in diffs:
            print("  - " + d)
    else:
        print("\n🟢 无差异")
    return 1 if diffs else 0


if __name__ == "__main__":
    sys.exit(main())
