#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
hot-rpc 双侧活体对拍（D1）的**栅栏**：轮询**两侧** Redis 的 hot:articles，
直到两侧都含**预期条目集合**（用事件集投进去的 article_id 集合核对）。

【为什么必须有栅栏】
  Kafka 消费进度**天然不对称**：两侧用**不同的 consumer group-id** 消费**同一批消息**
  （Go: article-hot-group；RAG: 由 SPRING_KAFKA_CONSUMER_GROUP_ID 覆盖），
  ⇒ 各自的消费进度、快照时刻、合并时刻都不同 ⇒ 直接比会得到**假差异**。
  快照又是**周期**产生的（Go 30s sync / RAG 30s sync；合并两侧都 10s）
  ⇒ 必须等两侧都**完成合并**之后再比，否则比的是"合并时刻"而不是"合并结果"。

【判定口径】
  * 两侧 hot:articles 的 **member 集合**必须 ⊇ 预期 article_id 集合；
  * 且**连续两次轮询内容不变**（分数也稳定）⇒ 说明合并已经收敛，不再是半成品；
  * 超时上限（默认 240s）⇒ **如实报告哪一侧没到齐**，退出码非 0。绝不假装成功。

【依赖】只用标准库（自写最小 RESP 客户端）—— 不依赖 redis-py / docker exec。

用法:
  python3 tools/difftest/hot_rpc_fence.py \
      --go-redis 127.0.0.1:23979 --rag-redis 127.0.0.1:23980 \
      --manifest /tmp/hot-rpc-difftest/events/manifest.json \
      --timeout 240
"""

import argparse
import json
import socket
import sys
import time


class RespError(Exception):
    pass


class Redis:
    """最小 RESP 客户端（只实现本脚本需要的命令）。"""

    def __init__(self, host, port, timeout=5.0):
        self.addr = (host, port)
        self.sock = socket.create_connection(self.addr, timeout=timeout)
        self.sock.settimeout(timeout)
        self.buf = b""

    def close(self):
        try:
            self.sock.close()
        except OSError:
            pass

    def _readline(self):
        while b"\r\n" not in self.buf:
            chunk = self.sock.recv(65536)
            if not chunk:
                raise RespError("connection closed by peer")
            self.buf += chunk
        line, self.buf = self.buf.split(b"\r\n", 1)
        return line

    def _read_exact(self, n):
        while len(self.buf) < n:
            chunk = self.sock.recv(65536)
            if not chunk:
                raise RespError("connection closed by peer")
            self.buf += chunk
        data, self.buf = self.buf[:n], self.buf[n:]
        return data

    def _read_reply(self):
        line = self._readline()
        kind, rest = line[:1], line[1:]
        if kind == b"+":
            return rest.decode("utf-8", "replace")
        if kind == b"-":
            raise RespError(rest.decode("utf-8", "replace"))
        if kind == b":":
            return int(rest)
        if kind == b"$":
            n = int(rest)
            if n == -1:
                return None
            data = self._read_exact(n + 2)
            return data[:n].decode("utf-8", "replace")
        if kind == b"*":
            n = int(rest)
            if n == -1:
                return None
            return [self._read_reply() for _ in range(n)]
        raise RespError("unknown reply type %r" % kind)

    def cmd(self, *args):
        out = [b"*%d\r\n" % len(args)]
        for a in args:
            if isinstance(a, str):
                a = a.encode("utf-8")
            out.append(b"$%d\r\n" % len(a))
            out.append(a)
            out.append(b"\r\n")
        self.sock.sendall(b"".join(out))
        return self._read_reply()

    def zrange_withscores(self, key):
        """ZRANGE key 0 -1 WITHSCORES ⇒ [(member, score), ...]（升序，与 ZREVRANGE 相反）。"""
        raw = self.cmd("ZRANGE", key, "0", "-1", "WITHSCORES")
        if not raw:
            return []
        return [(raw[i], float(raw[i + 1])) for i in range(0, len(raw), 2)]

    def zrevrange_withscores(self, key):
        """ZREVRANGE key 0 -1 WITHSCORES ⇒ [(member, score), ...]（降序，与生产侧同一条命令）。"""
        raw = self.cmd("ZREVRANGE", key, "0", "-1", "WITHSCORES")
        if not raw:
            return []
        return [(raw[i], float(raw[i + 1])) for i in range(0, len(raw), 2)]

    def keys(self, pattern):
        return self.cmd("KEYS", pattern) or []

    def ttl(self, key):
        return self.cmd("TTL", key)

    def type_of(self, key):
        return self.cmd("TYPE", key)

    def ping(self):
        return self.cmd("PING")


def snapshot(redis, key):
    """取一个 key 的稳定快照描述（成员 + 分数 + 顺序）。"""
    try:
        entries = redis.zrevrange_withscores(key)
    except RespError as exc:
        return ("error", str(exc))
    return ("zset", tuple((m, s) for m, s in entries))


def fmt(entries):
    if not entries:
        return "    (empty)"
    return "\n".join("    %-12s score=%s" % (m, ("%g" % s)) for m, s in entries)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--go-redis", default="127.0.0.1:23979")
    ap.add_argument("--rag-redis", default="127.0.0.1:23980")
    ap.add_argument("--manifest", default="/tmp/hot-rpc-difftest/events/manifest.json")
    ap.add_argument("--timeout", type=float, default=240.0)
    ap.add_argument("--poll", type=float, default=2.0)
    ap.add_argument("--dump-only", action="store_true",
                    help="不等待，直接打印两侧当前的 hot:articles / 分片快照")
    args = ap.parse_args()

    with open(args.manifest, "r", encoding="utf-8") as fh:
        manifest = json.load(fh)
    expected = set(manifest["articles"])

    go_host, go_port = args.go_redis.split(":")
    rag_host, rag_port = args.rag_redis.split(":")
    go = Redis(go_host, int(go_port))
    rag = Redis(rag_host, int(rag_port))

    print("fence: expected %d article ids, timeout=%.0fs" % (len(expected), args.timeout))
    print("  go  redis = %s" % args.go_redis)
    print("  rag redis = %s" % args.rag_redis)

    started = time.time()
    last_go = None
    last_rag = None
    stable_rounds = 0
    ok = False
    go_ids = set()
    rag_ids = set()

    while not args.dump_only:
        elapsed = time.time() - started
        try:
            go_snap = snapshot(go, "hot:articles")
            rag_snap = snapshot(rag, "hot:articles")
        except (RespError, OSError) as exc:
            print("[%6.1fs] redis read failed: %s" % (elapsed, exc))
            time.sleep(args.poll)
            continue

        go_entries = go_snap[1] if go_snap[0] == "zset" else ()
        rag_entries = rag_snap[1] if rag_snap[0] == "zset" else ()
        go_ids = {m for m, _ in go_entries}
        rag_ids = {m for m, _ in rag_entries}

        go_missing = expected - go_ids
        rag_missing = expected - rag_ids
        go_ok = not go_missing
        rag_ok = not rag_missing

        # 稳定性：两侧内容连续两轮不变 ⇒ 合并已收敛
        if go_snap == last_go and rag_snap == last_rag and go_ok and rag_ok:
            stable_rounds += 1
        else:
            stable_rounds = 0
        last_go, last_rag = go_snap, rag_snap

        print("[%6.1fs] go: %d/%d ids | rag: %d/%d ids | stable=%d%s%s"
              % (elapsed, len(go_ids & expected), len(expected),
                 len(rag_ids & expected), len(expected), stable_rounds,
                 "" if go_ok else "  go-missing=%d" % len(go_missing),
                 "" if rag_ok else "  rag-missing=%d" % len(rag_missing)))

        if go_ok and rag_ok and stable_rounds >= 2:
            ok = True
            break
        if elapsed > args.timeout:
            break
        time.sleep(args.poll)

    elapsed = time.time() - started

    print("\n================ 两侧 Redis 快照 ================")
    for label, cli in (("GO ", go), ("RAG", rag)):
        print("-- %s hot:articles (ZREVRANGE 0 -1 WITHSCORES) --" % label)
        entries = cli.zrevrange_withscores("hot:articles")
        print("  count=%d  ttl=%s" % (len(entries), cli.ttl("hot:articles")))
        print(fmt(entries))
        for pid in range(3):
            key = "hot:articles:p%d" % pid
            ents = cli.zrevrange_withscores(key)
            print("-- %s %s (count=%d ttl=%s) --" % (label, key, len(ents), cli.ttl(key)))
            print(fmt(ents))
        print("-- %s keys --" % label)
        for k in sorted(cli.keys("hot:*")):
            print("    %-28s type=%-6s ttl=%s" % (k, cli.type_of(k), cli.ttl(k)))

    print("\n================ 栅栏结论 ================")
    if ok:
        print("FENCE OK：两侧 hot:articles 都含全部 %d 个预期条目，且连续 %d 轮内容稳定（合并已收敛）" % (len(expected), stable_rounds))
        print("耗时 %.1fs" % elapsed)
        return 0
    print("FENCE FAILED：超时 %.1fs" % elapsed)
    go_missing = sorted(expected - go_ids)
    rag_missing = sorted(expected - rag_ids)
    print("  Go  侧缺失 %d 个：%s" % (len(go_missing), go_missing[:20]))
    print("  RAG 侧缺失 %d 个：%s" % (len(rag_missing), rag_missing[:20]))
    print("  ⇒ 如实报告：至少一侧在超时内未把预期条目集合写进 hot:articles")
    return 1


if __name__ == "__main__":
    sys.exit(main())
