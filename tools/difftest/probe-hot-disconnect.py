#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
════════════════════════════════════════════════════════════════════════════
 probe-hot-disconnect.py —— 客户端断连（499 / gRPC 取消）双侧活体取证
════════════════════════════════════════════════════════════════════════════

【要回答的两个问题】
  Q1 客户端断连时，服务端**真的**产出 499 吗？
  Q2 断连时，**在飞的 gRPC 调用会被取消吗**？

【为什么 Q2 才是重点】
  「499」这件事两侧的**代码路径**是同构的：
    Go  ：observability/http.go:84-88 —— `errors.Is(ctx.Err(), context.Canceled)` → 499
    RAG ：HttpMiddleware.java:332 —— 写响应失败（IOException，客户端已走）→ 499
  且两侧都把 499 **写给一个已经走了的客户端** ⇒ **客户端永远看不到它**，
  499 只落在 span 属性里（两侧同款，没有可比性）。
  ⇒ 真正**可跨侧观测、且有语义差别**的是 Q2：
     · Go 把 **HTTP 请求 ctx** 一路传进 zrpc ⇒ 断连 ⇒ gRPC **立刻**被取消；
     · RAG 的 gRPC 调用走的是 `withDeadlineAfter`（**不绑 request ctx**），
       且 Servlet 断连**不会**取消在飞的阻塞 gRPC ⇒ 只能等 **2s 调用 deadline** 才结束。
  ⇒ 这正是台账 D3 已留痕、但**至今没有活体证据**的那条差异。

【怎么测】
  ① 桩切到 `article-slow`（article-rpc 睡 30s ⇒ 请求一定在飞）
  ② 用**裸 socket** 发请求，0.5s 后用 SO_LINGER=0 强制 **RST**（最粗暴的断连）
     ⇒ urllib / httpclient 都做不到「发到一半就消失」
  ③ 读桩的**事件日志**（带时间戳）：ARTICLE_CALL_ENTER / ARTICLE_CALL_CANCELLED
     ⇒ **取消的时刻**才是判据：
        · 取消发生在断连后 ~50ms 内 ⇒ **被断连取消**（Go 侧）
        · 取消发生在 ~2s（gRPC deadline）⇒ **不是被断连取消**（RAG 侧）
  ④ 两侧服务日志里 grep 499 / broken pipe，作为 Q1 的旁证

【前置】
  需要两侧 hot-api + 两个桩已经在跑（`run-hot.sh --keep --serve-only`）。

【用法】
  python3 probe-hot-disconnect.py
════════════════════════════════════════════════════════════════════════════
"""

import argparse
import os
import socket
import struct
import sys
import time

GO_BASE = os.environ.get("GO_HOT_API_BASE", "http://127.0.0.1:18903")
RAG_BASE = os.environ.get("RAG_HOT_API_BASE", "http://127.0.0.1:58903")
RUN_DIR = os.environ.get("HOT_DIFFTEST_DIR", "/tmp/hot-difftest")
MODE_FILE = os.path.join(RUN_DIR, "stub-mode")
GO_EVENTS = os.path.join(RUN_DIR, "calls-go.log.events")
RAG_EVENTS = os.path.join(RUN_DIR, "calls-rag.log.events")
GO_LOG = os.path.join(RUN_DIR, "logs", "go-hot-api.log")
RAG_LOG = os.path.join(RUN_DIR, "logs", "rag-hot-api.log")

# 断连前等多久再 RST —— 必须 < 3s HTTP 超时，否则量到的是超时不是断连
DISCONNECT_AFTER_MS = 500
# 断连后等多久再看事件日志 —— 必须 > 2s gRPC deadline，否则看不出「等到 deadline 才结束」
SETTLE_MS = 4500


def host_port(base):
    h = base.split("//", 1)[1]
    hp = h.split("/", 1)[0]
    host, port = hp.split(":")
    return host, int(port)


def send_then_rst(base, path="/hot/v1/articles"):
    """发一个 GET 请求，DISCONNECT_AFTER_MS 后用 RST 掐断连接。返回 (t0, t_rst)。"""
    host, port = host_port(base)
    s = socket.create_connection((host, port), timeout=5)
    try:
        # SO_LINGER {onoff=1, linger=0} ⇒ close() 直接发 RST（而不是 FIN）
        # ⇒ 服务端立刻看到「连接没了」，而不是等它下次写响应才发现。
        s.setsockopt(socket.SOL_SOCKET, socket.SO_LINGER, struct.pack("ii", 1, 0))
        req = ("GET %s HTTP/1.1\r\nHost: %s:%d\r\nConnection: close\r\n\r\n"
               % (path, host, port))
        s.sendall(req.encode("ascii"))
        t0 = time.time()
        time.sleep(DISCONNECT_AFTER_MS / 1000.0)
        try:
            s.close()          # ← RST
        except OSError:
            pass
        t_rst = time.time()
        return t0, t_rst
    finally:
        try:
            s.close()
        except OSError:
            pass


def read_events(path):
    out = []
    try:
        with open(path, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                parts = line.split("\t", 1)
                if len(parts) == 2 and parts[0].isdigit():
                    out.append((int(parts[0]) / 1000.0, parts[1]))
                else:
                    out.append((None, line))
    except OSError:
        pass
    return out


def clear_file(path):
    try:
        if os.path.exists(path):
            os.remove(path)
    except OSError:
        pass


def analyze(label, events, t0, t_rst, log_path):
    """把事件时间轴与断连时刻对齐，给出结论。"""
    print("\n── %s ────────────────────────────────" % label)
    print("  断连时刻（相对 t0）: +%.0f ms" % ((t_rst - t0) * 1000))
    if not events:
        print("  ⚠️ 事件日志为空（桩没收到 article-rpc 调用？）")
        return None
    verdict = None
    for ts, ev in sorted(events, key=lambda x: (x[0] is None, x[0])):
        if ts is None:
            print("    %s" % ev)
            continue
        delta = (ts - t0) * 1000
        mark = ""
        if "CANCELLED" in ev:
            mark = "   ← 取消"
            if verdict is None:
                # 与断连时刻的间隔：< 500ms ⇒ 被断连取消；≈2000ms ⇒ 等 deadline
                verdict = "BY_DISCONNECT" if (delta - (t_rst - t0) * 1000) < 500 else "BY_DEADLINE"
        print("    +%7.0f ms  %s%s" % (delta, ev, mark))
    if verdict is None:
        print("  ⇒ 在观察窗口内**没有**观察到取消事件")
        verdict = "NO_CANCEL_OBSERVED"
    else:
        print("  ⇒ 取消原因判定：**%s**" % verdict)
    # 日志旁证
    try:
        with open(log_path, "r", encoding="utf-8", errors="replace") as f:
            tail = f.read()[-20000:]
        hits = [l for l in tail.splitlines()
                if ("499" in l or "broken pipe" in l.lower()
                    or "ClientAbort" in l or "Connection reset" in l)]
        if hits:
            print("  服务端日志旁证（最后 %d 行命中）:" % len(hits))
            for h in hits[-4:]:
                print("    %s" % h[:220])
        else:
            print("  服务端日志：无 499 / broken pipe 字样（499 只落在 span 属性里）")
    except OSError:
        print("  服务端日志：读不到 %s" % log_path)
    return verdict


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--settle-ms", type=int, default=SETTLE_MS)
    args = ap.parse_args()

    with open(MODE_FILE, "w", encoding="utf-8") as f:
        f.write("article-slow")

    print("=" * 78)
    print("客户端断连取证（499 / gRPC 取消）")
    print("  Go  = %s" % GO_BASE)
    print("  RAG = %s" % RAG_BASE)
    print("  断连时机 = 发出请求后 %d ms 强制 RST" % DISCONNECT_AFTER_MS)
    print("=" * 78)

    results = {}
    for label, base, events_path, log_path in (
            ("Go 侧", GO_BASE, GO_EVENTS, GO_LOG),
            ("RAG 侧", RAG_BASE, RAG_EVENTS, RAG_LOG)):
        clear_file(events_path)
        t0, t_rst = send_then_rst(base)
        time.sleep(args.settle_ms / 1000.0)
        results[label] = analyze(label, read_events(events_path), t0, t_rst, log_path)

    print("\n" + "=" * 78)
    print("结论")
    go_v = results.get("Go 侧")
    rag_v = results.get("RAG 侧")
    if go_v == "BY_DISCONNECT" and rag_v == "BY_DEADLINE":
        print("  ⇒ **两侧不等价，且方向与台账 D3 一致**：")
        print("     · Go：客户端断连 ⇒ HTTP ctx 取消 ⇒ **在飞的 gRPC 立刻被取消**")
        print("     · RAG：Servlet 断连**不**取消在飞的阻塞 gRPC ⇒ 只能等 2s 调用 deadline")
        print("  ⇒ DRIFT-HOTAPI 的 **D3「客户端断连的取消不再传播」已由活体证据坐实**")
        return 0
    print("  ⇒ 观测结果与预期不同，**不要直接下结论**（先怀疑探针，见 Pitfalls P17）：")
    print("     Go=%s  RAG=%s" % (go_v, rag_v))
    return 1


if __name__ == "__main__":
    sys.exit(main())
