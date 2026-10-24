#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
════════════════════════════════════════════════════════════════════════════
 probe-hot-latency.py —— U10：8 并发 × 100 items 的**延迟分布**双侧对拍
════════════════════════════════════════════════════════════════════════════

【U10 到底要回答什么】
  台账原话：*「8 并发请求 × 100 items 的压测，比 p99 而非成功率；
  确认没有请求被 `CallerRunsPolicy` 拖成串行」*。

  ⇒ 两个问题，**都必须用「分布」而不是「成功率」来答**：
  ① **延迟分布**：极端并发下补全的 p99 两侧可比吗？
  ② **背压形态**：RAG 侧的补全池满时，是「排队」（变慢）还是
     `CallerRunsPolicy`（**在请求线程上串行跑**）？
     两者在 p99 上都表现为「变慢」，但**代价形态不同**：
     排队 = 延迟涨；CallerRuns = 请求线程被占住，**并发度被偷偷抬高**。

【为什么必须用桩的「事件日志」算实际并发度】
  光看 p99 分不清「池满了在排队」和「CallerRuns 在请求线程上跑」。
  ⇒ 桩在 `many-100` 模式下打 **ENTER/EXIT 带时间戳**的事件日志
  ⇒ 事后算出「下游同时在途数」的**峰值**：
     · 峰值 = 64（8 并发 × 每请求 8 窗口）⇒ 并发窗口**没被腰斩**，Go 形态；
     · 峰值 = 32 ⇒ 被 RAG 侧**共享池**（`HotHydrationExecutorConfiguration` 的 POOL_SIZE=32）
       腰斩 ⇒ **排队**（延迟涨，但并发度没偷偷抬高）；
     · 峰值 **> 32** ⇒ **CallerRunsPolicy 触发了**（任务跑在请求线程上）
       ⇒ 并发度被偷偷抬高，**这才是要盯的那个形态**。

【方法学纪律】
  · 两侧**分别**跑（不同时跑）⇒ 互不抢 CPU，延迟分布可比；
  · 跑完把桩的 mode 复位；
  · 桩的事件日志**按侧分开**（`calls-go.log.events` / `calls-rag.log.events`）⇒ 可分别算。

【用法】
  python3 probe-hot-latency.py --concurrency 8 --iterations 20
"""

import argparse
import json
import os
import socket
import statistics
import sys
import threading
import time

GO_BASE = os.environ.get("GO_HOT_API_BASE", "http://127.0.0.1:18903")
RAG_BASE = os.environ.get("RAG_HOT_API_BASE", "http://127.0.0.1:58903")
HOT_PATH = "/hot/v1/articles"
RUN_DIR = os.environ.get("HOT_DIFFTEST_DIR", "/tmp/hot-difftest")
MODE_FILE = os.path.join(RUN_DIR, "stub-mode")
EVENT_LOGS = {"go": os.path.join(RUN_DIR, "calls-go.log.events"),
              "rag": os.path.join(RUN_DIR, "calls-rag.log.events")}


def base_of(url):
    rest = url.split("://", 1)[1].rstrip("/")
    host, _, port = rest.partition(":")
    return host, int(port or 80)


def set_mode(mode):
    with open(MODE_FILE, "w", encoding="utf-8") as f:
        f.write(mode)


def one_request(host, port, timeout=30.0):
    """发一个 GET，返回 (耗时秒, 状态码, body 长度, 错误)。"""
    t0 = time.perf_counter()
    try:
        s = socket.create_connection((host, port), timeout=timeout)
        s.settimeout(timeout)
        head = (f"GET {HOT_PATH} HTTP/1.1\r\nHost: {host}:{port}\r\n"
                "Connection: close\r\n\r\n")
        s.sendall(head.encode())
        chunks = []
        while True:
            b = s.recv(65536)
            if not b:
                break
            chunks.append(b)
        s.close()
        raw = b"".join(chunks)
        dt = time.perf_counter() - t0
        status = None
        if raw.startswith(b"HTTP/1."):
            parts = raw.split(b"\r\n", 1)[0].split()
            if len(parts) >= 2 and parts[1].isdigit():
                status = int(parts[1])
        return dt, status, len(raw), None
    except Exception as e:                                   # noqa: BLE001
        return time.perf_counter() - t0, None, 0, f"{type(e).__name__}: {e}"


def run_side(side, url, concurrency, iterations, mode):
    host, port = base_of(url)
    # ⚠️ 事件日志会**跨轮累积** ⇒ 开跑前必须清空，否则峰值算的是「历次叠加」，
    #    而且 ENTER/EXIT 可能跨轮配对错位。
    ev = EVENT_LOGS.get(side)
    if ev and os.path.isfile(ev):
        open(ev, "w").close()
    set_mode(mode)
    time.sleep(0.5)

    latencies = []
    statuses = {}
    errors = []
    lock = threading.Lock()

    def worker(slot):
        for _ in range(iterations):
            dt, status, blen, err = one_request(host, port)
            with lock:
                latencies.append(dt)
                if err:
                    errors.append(err)
                statuses[status] = statuses.get(status, 0) + 1

    threads = [threading.Thread(target=worker, args=(i,)) for i in range(concurrency)]
    t0 = time.time()
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    wall = time.time() - t0

    set_mode("normal")
    latencies.sort()

    def pct(p):
        if not latencies:
            return None
        # 最近秩法（与 Go 的 metrics 近似），样本量小，用 nearest-rank 最稳
        k = max(1, min(len(latencies), int(round(p / 100.0 * len(latencies) + 0.5))))
        return latencies[k - 1]

    stats = {
        "side": side,
        "requests": len(latencies),
        "concurrency": concurrency,
        "iterations": iterations,
        "wall_seconds": round(wall, 2),
        "statuses": {str(k): v for k, v in sorted(statuses.items(), key=lambda kv: str(kv[0]))},
        "errors": len(errors),
        "error_sample": errors[:3],
        "min_ms": round(latencies[0] * 1000, 1) if latencies else None,
        "p50_ms": round(pct(50) * 1000, 1) if latencies else None,
        "p90_ms": round(pct(90) * 1000, 1) if latencies else None,
        "p95_ms": round(pct(95) * 1000, 1) if latencies else None,
        "p99_ms": round(pct(99) * 1000, 1) if latencies else None,
        "max_ms": round(latencies[-1] * 1000, 1) if latencies else None,
        "mean_ms": round(statistics.mean(latencies) * 1000, 1) if latencies else None,
    }
    return stats


def peak_concurrency(path):
    """从桩的事件日志算「下游同时在途数」的峰值。

    ⚠️ **事件日志的格式是 `<epochMillis>\\t<MESSAGE>`**（桩的 `event()` 写的是
    `System.currentTimeMillis()` + TAB + 消息，**不是** ISO 时间戳）
    ⇒ 解析器必须按第一个字段的**整数毫秒**排序。
    """
    if not os.path.isfile(path):
        return None, 0
    events = []
    with open(path, encoding="utf-8", errors="replace") as f:
        for line in f:
            if "ARTICLE_ENTER" not in line and "ARTICLE_EXIT" not in line:
                continue
            head, _, rest = line.partition("\t")
            try:
                ts = int(head.strip())
            except ValueError:
                continue
            kind = "ENTER" if "ARTICLE_ENTER" in rest else "EXIT"
            events.append((ts, kind))
    if not events:
        return None, 0
    events.sort(key=lambda e: e[0])
    cur = peak = 0
    for _, kind in events:
        cur += 1 if kind == "ENTER" else -1
        peak = max(peak, cur)
    return peak, len(events)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--concurrency", type=int, default=8)
    ap.add_argument("--iterations", type=int, default=20)
    ap.add_argument("--mode", default="many-100")
    ap.add_argument("--rag-pool-size", type=int, default=64,
                    help="RAG 侧共享补全池容量（须与 HotHydrationExecutorConfiguration.POOL_SIZE 一致）")
    args = ap.parse_args()

    results = []
    for side, url in (("go", GO_BASE), ("rag", RAG_BASE)):
        print(f"==> {side}  {url}  并发 {args.concurrency} × {args.iterations} 轮"
              f"（mode={args.mode}）")
        st = run_side(side, url, args.concurrency, args.iterations, args.mode)
        peak, nev = peak_concurrency(EVENT_LOGS[side])
        st["downstream_peak_concurrency"] = peak
        st["downstream_events"] = nev
        print(f"    p50={st['p50_ms']}ms  p90={st['p90_ms']}ms  p95={st['p95_ms']}ms  "
              f"p99={st['p99_ms']}ms  max={st['max_ms']}ms")
        print(f"    状态码分布：{st['statuses']}  传输错误：{st['errors']}")
        print(f"    下游同时在途峰值：{peak}（事件数 {nev}）")
        results.append(st)
        time.sleep(1)

    go, rag = results
    print("\n══════════ 对比 ══════════")
    print(f"  p99：Go {go['p99_ms']}ms  vs  RAG {rag['p99_ms']}ms"
          f"  ⇒ RAG/Go = {round(rag['p99_ms'] / go['p99_ms'], 2) if go['p99_ms'] else 'n/a'}×")
    print(f"  p50：Go {go['p50_ms']}ms  vs  RAG {rag['p50_ms']}ms"
          f"  ⇒ RAG/Go = {round(rag['p50_ms'] / go['p50_ms'], 2) if go['p50_ms'] else 'n/a'}×")
    print(f"  下游并发峰值：Go {go['downstream_peak_concurrency']}  vs  "
          f"RAG {rag['downstream_peak_concurrency']}")
    need = args.concurrency * 8
    # RAG 侧共享补全池的容量（`HotHydrationExecutorConfiguration.POOL_SIZE`）。
    # ⚠️ **这个值必须与代码里的 POOL_SIZE 保持一致** —— 它 2026-10-24 从 32 改成了 64
    #    （DRIFT-HOTAPI-028 修复）⇒ 池容量变了，**判据的阈值也必须跟着变**，
    #    否则「峰值 > 32 ⇒ CallerRuns 触发」会误报。
    # ⚠️ **这个 32 只属于 RAG 侧** —— Go 侧是裸 goroutine，**没有池** ⇒
    #    拿「峰值 > 池容量」去判 Go 侧「CallerRuns 触发」是**判据套错对象**（Go 压根没这个机制）。
    RAG_POOL_SIZE = args.rag_pool_size
    print(f"  期望并发度（{args.concurrency} 请求 × 每请求 8 窗口）= {need}"
          f"；RAG 侧共享池容量 = {RAG_POOL_SIZE}")
    for st in results:
        peak = st["downstream_peak_concurrency"]
        side = st["side"]
        if peak is None:
            verdict = "未知（事件日志为空）"
        elif peak == need:
            verdict = f"✅ 并发窗口**完整保留**（峰值 {peak} = 期望 {need}）"
        elif peak < need:
            extra = ""
            if side == "rag" and peak <= RAG_POOL_SIZE:
                extra = (" ⇒ 瓶颈是**共享池**（排队变慢），"
                         f"且峰值 {peak} ≤ 池容量 {RAG_POOL_SIZE} ⇒ **未**触发 CallerRuns")
            verdict = (f"🟡 并发窗口被**腰斩**（峰值 {peak} < {need}）{extra}")
        else:
            extra = ""
            if side == "rag" and peak > RAG_POOL_SIZE:
                extra = (f" ⇒ 峰值 {peak} > 池容量 {RAG_POOL_SIZE} ⇒ "
                         "**CallerRunsPolicy 触发了**（任务跑在请求线程上，并发度被偷偷抬高）")
            verdict = f"🟡 并发度**超出**期望（峰值 {peak} > {need}）{extra}"
        print(f"  · {side}: {verdict}")

    out = os.path.join(RUN_DIR, "u10-latency.json")
    with open(out, "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    print(f"\n  明细：{out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
