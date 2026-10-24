#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
════════════════════════════════════════════════════════════════════════════
 probe-hot-u5open1.py —— U5-OPEN-1 隔离实验 **E1**
════════════════════════════════════════════════════════════════════════════

【要回答的问题】
  第八轮发现：同一场景（article-rpc 慢 ⇒ 补全拖过 3s HTTP 超时）下，
  **两侧 HTTP 熔断的「打开时机」不稳定**，且 5xx 累积速率不同。
  台账列了两条候选原因，结论是「后果相同、修法不同 ⇒ 不隔离就动手 = 盲修」：

    ① 客户端熔断（gRPC 层）的开启时机不同
    ② 两侧补全的「失败快慢 / 剩余预算取样时刻」不同

  本脚本做的是**候选 ② 的判决实验**，而且是**判决「有没有差异」**的那一步：
  用**下游桩的服务端视角**直接观测「每个在飞 article RPC 是在什么时刻死的」。

【为什么桩的服务端视角是决定性的】
  gRPC 客户端的 deadline 到期 ⇒ 客户端取消 call ⇒ **服务端立刻收到 cancel**
  ⇒ 桩打出 `ARTICLE_CALL_CANCELLED`（带毫秒时间戳）。
  ⇒ 「调用死在哪一刻」在服务端是**可直接观测**的，不需要相信客户端的自述。
  ⇒ 而 HTTP 响应只有 503，看不出「是第几个补全死的、什么时候死的」。

【两侧的机制差异（本次要判决的对象）】
  Go（service/common/observability/http.go:56-57 + zrpc）：
      ctx, cancel := context.WithTimeout(r.Context(), timeout)   ← **绝对** deadline = t0+3s
      每个 article RPC 的实际 deadline = min(ctx 的绝对 deadline, 发起时刻+2s)
      ⇒ **后发的调用仍被请求的绝对 deadline 卡住**
  RAG（HotArticleQueryService.java:94 + GoStyleGrpcCallDeadline.java:59-63）：
      long remainingBudgetMs = GoStyleRequestDeadline.remainingMillis();  ← **请求开始时取一次快照**
      每个 article RPC 的 deadline = 发起时刻 + min(2000, 那个**快照**)
      ⇒ **后发的调用拿到的是「全新的 2s」，不被请求 deadline 卡住**

  ⇒ 可判决的差异：**第 9、10 个补全（并发窗口 8 之后才发出的那批）**
     在 Go 侧应死在 **t≈3.0s**（请求 deadline），在 RAG 侧应死在 **t≈4.1s**（自己的 2s）。

【判读】
  · 两侧「最后一批补全的死亡时刻」相差 ≈ 1.1s ⇒ **候选 ② 成立**（RAG 的 deadline 语义确实不等价）
  · 两侧完全一致 ⇒ 候选 ② 被证伪 ⇒ 差异只能来自候选 ①（客户端熔断的开启时机）

【前置条件】
  两套栈已经在跑（`bash tools/difftest/run-hot.sh --serve-only`）。
  本脚本**只读**桩的事件日志 + 发 HTTP 请求，不改任何一侧。

【用法】
  python3 tools/difftest/probe-hot-u5open1.py
  python3 tools/difftest/probe-hot-u5open1.py --settle 3.0   # 请求后再等几秒收尾事件
"""

import argparse
import http.client
import json
import os
import sys
import time

RUN_DIR = os.environ.get("HOT_DIFFTEST_DIR", "/tmp/hot-difftest")
MODE_FILE = os.path.join(RUN_DIR, "stub-mode")
HOT_PATH = "/hot/v1/articles"

# 事件日志：run-hot.sh 里每个桩各一份（带时间戳，与主调用日志分开）
GO_EVENTS = os.path.join(RUN_DIR, "calls-go.log.events")
RAG_EVENTS = os.path.join(RUN_DIR, "calls-rag.log.events")

# 事件类型（hot-stub-server.java:345/348/356/359）
EV_ENTER = "ARTICLE_CALL_ENTER"
EV_CANCEL = "ARTICLE_CALL_CANCELLED"
EV_ABORT = "ARTICLE_CALL_ABORTED"
EV_EXIT = "ARTICLE_CALL_EXIT"


def set_mode(mode):
    with open(MODE_FILE, "w", encoding="utf-8") as f:
        f.write(mode + "\n")


def event_count(path):
    if not os.path.isfile(path):
        return 0
    with open(path, encoding="utf-8", errors="replace") as f:
        return sum(1 for _ in f)


def read_events_since(path, skip):
    """读出第 skip 行之后的事件行。"""
    out = []
    if not os.path.isfile(path):
        return out
    with open(path, encoding="utf-8", errors="replace") as f:
        for i, line in enumerate(f):
            if i < skip:
                continue
            line = line.rstrip("\n")
            if not line:
                continue
            parts = line.split("\t", 1)
            if len(parts) != 2:
                continue
            try:
                ts = int(parts[0])
            except ValueError:
                continue
            out.append((ts, parts[1]))
    return out


def http_get(host, port, path=HOT_PATH, timeout=30.0):
    """返回 (status, body, latency_ms)。非 2xx 也返回（不抛）。"""
    t0 = time.time()
    conn = http.client.HTTPConnection(host, port, timeout=timeout)
    try:
        conn.request("GET", path)
        resp = conn.getresponse()
        body = resp.read()
        return resp.status, body, (time.time() - t0) * 1000.0
    finally:
        conn.close()


def parse_article_events(events):
    """把事件流折叠成「每个 article id 的进入/死亡时刻」。

    死亡 = CANCELLED（客户端取消：deadline 到期 / ctx 取消）
         或 ABORTED（取消后服务端再写响应被拒）
         或 EXIT（服务端正常写完）
    """
    per_id = {}
    for ts, line in events:
        for kind in (EV_ENTER, EV_CANCEL, EV_ABORT, EV_EXIT):
            if line.startswith(kind + " "):
                rest = line[len(kind) + 1:]
                aid = rest.split(" ", 1)[0].replace("id=", "")
                slot = per_id.setdefault(aid, {"enter": None, "death": None, "death_kind": None})
                if kind == EV_ENTER and slot["enter"] is None:
                    slot["enter"] = ts
                elif slot["death"] is None:
                    slot["death"] = ts
                    slot["death_kind"] = kind
                break
    return per_id


def summarize(side, host, port, events_path, settle):
    before = event_count(events_path)
    status, body, latency = http_get(host, port)
    # 请求返回后**还要再等**：慢调用的取消事件可能晚于 HTTP 响应
    # （RAG 侧 HTTP 3s 超时，但第 3 批补全的死亡在 t≈4.1s ⇒ 晚 1.1s）
    time.sleep(settle)
    events = read_events_since(events_path, before)
    per_id = parse_article_events(events)

    base = None
    for aid, slot in per_id.items():
        if slot["enter"] is not None:
            base = slot["enter"] if base is None else min(base, slot["enter"])
    rows = []
    for aid, slot in sorted(per_id.items(), key=lambda kv: (kv[1]["enter"] or 0, kv[0])):
        rows.append({
            "id": aid,
            "enter_ms": None if slot["enter"] is None else slot["enter"] - base,
            "death_ms": None if slot["death"] is None else slot["death"] - base,
            "death_kind": slot["death_kind"],
        })

    deaths = [r["death_ms"] for r in rows if r["death_ms"] is not None]
    return {
        "side": side,
        "http_status": status,
        "http_body": body.decode("utf-8", errors="replace")[:120],
        "http_latency_ms": round(latency, 1),
        "article_calls": len(rows),
        "death_times_ms": sorted(deaths),
        "last_death_ms": max(deaths) if deaths else None,
        "rows": rows,
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--go-port", type=int, default=18903)
    ap.add_argument("--rag-port", type=int, default=58903)
    ap.add_argument("--settle", type=float, default=3.0,
                    help="HTTP 响应之后再等几秒，收齐晚到的取消事件")
    ap.add_argument("--mode", default="article-slow-short")
    args = ap.parse_args()

    if not os.path.isfile(MODE_FILE):
        print(f"❌ 找不到 mode 文件 {MODE_FILE} —— 栈没起来？先跑 run-hot.sh --serve-only")
        return 2

    print(f"==> 桩模式切到 {args.mode}")
    set_mode(args.mode)

    results = []
    for side, port, events_path in (
        ("go", args.go_port, GO_EVENTS),
        ("rag", args.rag_port, RAG_EVENTS),
    ):
        print(f"==> {side} 侧发 1 个请求（HTTP {args.go_port if side=='go' else args.rag_port}）")
        r = summarize(side, "127.0.0.1", port, events_path, args.settle)
        results.append(r)
        print(f"    HTTP {r['http_status']}  latency={r['http_latency_ms']}ms  body={r['http_body']!r}")
        print(f"    article 调用数 = {r['article_calls']}")
        print(f"    死亡时刻(ms，相对首个 ENTER) = {r['death_times_ms']}")
        print(f"    最后一次死亡 = {r['last_death_ms']}ms")

    go, rag = results
    print("\n══════════ 判决 ══════════")
    print(f"  Go  侧最后一批补全死于 t = {go['last_death_ms']}ms")
    print(f"  RAG 侧最后一批补全死于 t = {rag['last_death_ms']}ms")
    delta = None
    if go["last_death_ms"] is not None and rag["last_death_ms"] is not None:
        delta = rag["last_death_ms"] - go["last_death_ms"]
        print(f"  差值(RAG − Go) = {delta:+.0f}ms")

    verdict = "INCONCLUSIVE"
    if delta is not None:
        if delta >= 700:
            verdict = "CANDIDATE_2_PRESENT"
            print("  ⇒ 两侧在飞调用的死亡时刻显著不同 ⇒ **候选 ②（剩余预算取样时刻）成立**")
            print("     RAG 的后发补全没有被请求的绝对 deadline 卡住 ⇒ deadline 语义与 Go 不等价")
        elif delta <= -700:
            verdict = "CANDIDATE_2_PRESENT_REVERSED"
            print("  ⇒ 差异存在但方向相反 ⇒ 仍属候选 ②，需按实测方向定位")
        else:
            verdict = "IN_FLIGHT_DEADLINES_EQUIVALENT"
            print("  ⇒ 两侧在飞调用的死亡时刻**一致**（差 %+.0fms）⇒ 在飞调用的 deadline 语义**等价**" % delta)
            print("     ⇒ 候选 ② 在当前实现上**不成立**")
            print("     ⚠️ 判读注意：本判据只回答「在飞调用何时死」；")
            print("        它**不**回答「HTTP 熔断是否打开」—— 后者由 probe-hot-protection.py 的 breaker 探针单独判。")
            print("        ⚠️ 另注：若某次运行前**尚未修**候选 ②，本项会显示 CANDIDATE_2_PRESENT（实测差值 +1028ms）；")
            print("           修完后应显示本行。⇒ 跨轮比较请看「本轮 vs 修前」两份输出，别只看单次结论。")

    out = {"results": results, "delta_ms": delta, "verdict": verdict}
    out_path = os.path.join(RUN_DIR, "u5-open-1-e1.json")
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=2)
    print(f"  明细：{out_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
