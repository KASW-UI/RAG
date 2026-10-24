#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
════════════════════════════════════════════════════════════════════════════
 probe-hot-protection.py —— U5：保护层四闸的**活体触发**与双侧对拍
════════════════════════════════════════════════════════════════════════════

【为什么要单独一个脚本，而不是并进 diff-hot.py】
  diff-hot.py 比的是**业务语义**（正常路径的响应体逐字节 + 响应头 + 下游调用序列）。
  保护层四闸是**「在到达业务逻辑之前就把请求拒掉」**的路径：
    · 触发它需要**超限输入**（不是业务参数）；
    · 它的响应**没有信封**（裸状态码 + 空 body）⇒ 复用 diff-hot.py 的信封比对逻辑反而会误判；
    · 熔断那一闸是**有状态**的（滑动窗口）⇒ 单独跑、单独收摊。
  ⇒ 本脚本自带一套「裸 HTTP」取数与分类逻辑。

【四闸各自的 Go 出处与触发方式】
  闸            Go 出处                                    触发方式                 期望（两侧同）
  ─────────────────────────────────────────────────────────────────────────────────────────
  MaxBytes     rest/handler/maxbyteshandler.go:10-25      Content-Length > 1 MiB   413 + 空 body
  MaxConns     rest/handler/maxconnshandler.go:12-36       并发在途 > 阈值          503 + 空 body
  CpuShedding  rest/handler/sheddinghandler.go:20-52       CPU ≥ 阈值 **且** 在途超上限  503 + 空 body
  Breaker      rest/handler/breakerhandler.go:19-44        连续 5xx 把熔断打开        503 + 空 body

【⚠️ 两个「结构性不可达」必须先说清，否则会把「测不出来」误读成「两边一样」】
  ① **CpuShedding 在 macOS 上 Go 侧结构性不可达**：
     go-zero v1.10.2 `core/stat/internal/cpu_other.go` 带 `//go:build !linux`，
     `RefreshCpu()` **恒返回 0** ⇒ `stat.CpuUsage()` 恒为 0 ⇒
     `adaptiveshedder.go:245-256` 的 `systemOverloaded()` 恒为 false ⇒
     `shouldDrop()` 的第一个分支永远不成立 ⇒ `droppedRecently` 永远为 false ⇒
     `stillHot()` 也永远为 false ⇒ **Go 侧的降载闸在 darwin 上永远不会触发**。
     ⇒ 本脚本在 darwin 上**跳过**这一闸，并打印上述依据（不是「测了没差异」）。
  ② **MaxConns 在 RAG 侧默认阈值下结构性不可达**：
     Go 的 MaxConns 是**路由链内**的一层，`MaxConnsHandler` 每次 `Allow()` 占一个名额、
     handler 结束才归还 ⇒ 默认 10000 需要 **10001 个同时在途**的请求。
     RAG 侧 Tomcat 默认 `max-threads=200` ⇒ 同时在途（进入 Filter）的请求**最多 200** ⇒
     10000 的阈值**永远达不到**。
     ⇒ 本脚本用**两侧同值**的降低阈值来证明「闸门机制本身」在两侧等价，
       并把「默认阈值下的可达性差异」单独记进台账（不是同一个结论）。

【本脚本不做的事】
  · 不改冻结的 Go 仓库（配置走副本 + `-f`）；
  · 不判定「两边一样」就算过 —— 每条断言都写明**期望值来自 Go 真源的哪一行**。

【用法】
  python3 probe-hot-protection.py --probe maxbytes
  python3 probe-hot-protection.py --probe breaker --concurrent 12
  python3 probe-hot-protection.py --probe maxconns --concurrent 16
  python3 probe-hot-protection.py --probe cpushedding
  python3 probe-hot-protection.py --probe all
"""

import argparse
import json
import os
import socket
import sys
import threading
import time

GO_BASE = os.environ.get("GO_HOT_API_BASE", "http://127.0.0.1:18903")
RAG_BASE = os.environ.get("RAG_HOT_API_BASE", "http://127.0.0.1:58903")
HOT_PATH = "/hot/v1/articles"
RUN_DIR = os.environ.get("HOT_DIFFTEST_DIR", "/tmp/hot-difftest")
MODE_FILE = os.path.join(RUN_DIR, "stub-mode")

# ── Go 真源常量（每一处都标出处）──────────────────────────────────────────────
GO_MAX_BYTES = 1048576        # rest/config.go:53  MaxBytes int64 json:",default=1048576"
GO_MAX_CONNS = 10000          # rest/config.go:52  MaxConns int  json:",default=10000"
GO_CPU_THRESHOLD = 900        # rest/config.go:56  CpuThreshold int64 json:",default=900,range=[0:1000)"
GO_TIMEOUT_BODY = b"Request Timeout\n"   # rest/handler/timeouthandler.go 的 http.Error 文案

# ── 结果收集 ─────────────────────────────────────────────────────────────────
RESULTS = []


def record(probe, side, ok, detail):
    RESULTS.append({"probe": probe, "side": side, "ok": bool(ok), "detail": detail})
    mark = "PASS" if ok else "FAIL"
    print(f"  [{mark}] {probe:<14} {side:<4} {detail}")


def note(probe, side, detail):
    """不判成败的观察（结构性不可达、平台事实等）。"""
    RESULTS.append({"probe": probe, "side": side, "ok": None, "detail": detail})
    print(f"  [NOTE] {probe:<14} {side:<4} {detail}")


def base_of(url):
    """http://host:port → (host, port)"""
    rest = url.split("://", 1)[1].rstrip("/")
    host, _, port = rest.partition(":")
    return host, int(port or 80)


def set_mode(mode):
    with open(MODE_FILE, "w", encoding="utf-8") as f:
        f.write(mode)


class RawResponse:
    __slots__ = ("status", "reason", "headers", "body", "error", "conn_reset")

    def __init__(self, status=None, reason="", headers=None, body=b"", error=None,
                 conn_reset=None):
        self.status = status
        self.reason = reason
        self.headers = headers or {}
        self.body = body
        self.error = error
        #: 服务端在响应之后**重置连接**（RST）—— 这是独立于响应内容的可观察差异
        self.conn_reset = conn_reset

    def header(self, name):
        return self.headers.get(name.lower())

    def has_body(self):
        return len(self.body) > 0

    def describe(self):
        if self.error:
            return f"ERR({self.error})"
        return f"{self.status} body={len(self.body)}B"


def dechunk(body):
    """解 HTTP/1.1 chunked 传输编码，返回 (明文体, 是否是 chunked)。

    ⚠️ **为什么必须解**：RAG 侧的超时响应走 `Transfer-Encoding: chunked`
    （body 是 `10\\r\\nRequest Timeout\\n\\r\\n0\\r\\n\\r\\n`），
    Go 侧走 `Content-Length: 16` ⇒ **不解 chunk 就没法逐字节比 body**，
    分类也会误判（把超时当成「其它」）。
    """
    if not body:
        return body, False
    out = bytearray()
    i = 0
    saw_chunk = False
    while i < len(body):
        j = body.find(b"\r\n", i)
        if j < 0:
            return body, False
        line = body[i:j].split(b";", 1)[0].strip()
        try:
            size = int(line, 16)
        except ValueError:
            return body, False
        saw_chunk = True
        if size == 0:
            return bytes(out), True
        i = j + 2
        if i + size > len(body):
            # 截断（服务端超时后可能没写完）⇒ 取能取的部分
            out += body[i:]
            return bytes(out), True
        out += body[i:i + size]
        i += size + 2  # 跳过 chunk 后的 CRLF
    return bytes(out), saw_chunk


def parse_http(raw):
    """把原始响应字节解成 (status, reason, headers, body)。"""
    head, sep, body = raw.partition(b"\r\n\r\n")
    if not sep:
        return RawResponse(error="no-header-terminator")
    lines = head.split(b"\r\n")
    if not lines or not lines[0]:
        return RawResponse(error="empty-status-line")
    parts = lines[0].split(None, 2)
    if len(parts) < 2 or not parts[1].isdigit():
        return RawResponse(error=f"bad-status-line:{lines[0][:60]!r}")
    status = int(parts[1])
    reason = parts[2].decode("latin-1") if len(parts) > 2 else ""
    headers = {}
    for line in lines[1:]:
        if b":" not in line:
            continue
        k, _, v = line.partition(b":")
        headers[k.decode("latin-1").strip().lower()] = v.decode("latin-1").strip()
    decoded, was_chunked = dechunk(body)
    resp = RawResponse(status=status, reason=reason, headers=headers, body=decoded)
    if was_chunked:
        resp.headers["x-observed-transfer-encoding"] = "chunked"
    return resp


def raw_request(host, port, method="GET", path=HOT_PATH, body=None,
                headers=None, timeout=30.0, send_body=True):
    """裸 socket 发一个请求，返回解析好的响应。

    ⚠️ **为什么用裸 socket 而不是 urllib**：
      保护层的拒绝响应是「裸状态码 + 空 body」，urllib 在 4xx/5xx 上会抛异常并
      丢掉响应体与响应头；而且 urllib **不会**在请求体写失败时继续把响应读出来
      —— 而 MaxBytes 这一闸恰恰是「服务端先拒、客户端还在传体」的典型场景
      ⇒ 用 urllib 会把「服务端已经回了 413」误判成「连接被重置」。
    """
    hdrs = {"Host": f"{host}:{port}", "Connection": "close"}
    if headers:
        hdrs.update(headers)
    payload = body or b""
    if payload:
        hdrs.setdefault("Content-Length", str(len(payload)))
    head = f"{method} {path} HTTP/1.1\r\n" + "".join(
        f"{k}: {v}\r\n" for k, v in hdrs.items()) + "\r\n"

    s = socket.create_connection((host, port), timeout=timeout)
    s.settimeout(timeout)
    conn_reset = None
    try:
        s.sendall(head.encode("latin-1"))
        if payload and send_body:
            # 体单独一个线程写：服务端可能在收完头就拒绝并关闭连接 ⇒ 写侧会 BrokenPipe，
            # 但**响应侧仍能读到 413** —— 这正是要观测的。
            def _writer():
                try:
                    s.sendall(payload)
                except OSError:
                    pass
            th = threading.Thread(target=_writer, daemon=True)
            th.start()
        chunks = []
        while True:
            try:
                b = s.recv(65536)
            except socket.timeout:
                conn_reset = "read-timeout"
                break
            except OSError as e:
                # ⚠️ **连接重置不等于「没拿到响应」**
                #   服务端「先回响应、再带着未读数据关连接」⇒ 客户端会收到 RST，
                #   但**响应字节通常已经到手**。⇒ 这里只记下重置，不丢弃已收到的字节。
                conn_reset = f"reset:{e.__class__.__name__}"
                break
            if not b:
                conn_reset = conn_reset or "closed"
                break
            chunks.append(b)
        raw = b"".join(chunks)
    finally:
        try:
            s.close()
        except OSError:
            pass
    if b"__TIMEOUT__" in raw:
        return RawResponse(error="timeout", conn_reset=conn_reset)
    if not raw:
        return RawResponse(error="no-bytes", conn_reset=conn_reset)
    resp = parse_http(raw)
    resp.conn_reset = conn_reset
    if resp.error:
        resp.error = f"{resp.error} (conn={conn_reset})"
    return resp


# ═════════════════════════════════════════════════════════════════════════════
# 探针 1：MaxBytes —— Content-Length 头超限
# ═════════════════════════════════════════════════════════════════════════════
def probe_maxbytes(side, url, over=1):
    """Go: maxbyteshandler.go:19 `if r.ContentLength > n` ⇒ 413 + 裸 WriteHeader。

    ⚠️ **只判 Content-Length 头，不判实际读取的字节数** —— 这是 Go 的真实语义
    （RAG 侧 GoStyleMaxBytesFilter 的 javadoc 已照搬）。
    ⇒ 本探针必须**真的把体送出去**，否则测的是「没送体」而不是「体超限」。
    """
    host, port = base_of(url)
    set_mode("normal")          # ⚠️ 必须显式复位：mode 文件是**上一次探针留下的全局状态**
    time.sleep(0.2)
    n = GO_MAX_BYTES
    body = b"x" * (n + over)
    resp = raw_request(host, port, method="GET", path=HOT_PATH, body=body,
                       headers={"Content-Type": "application/octet-stream"}, timeout=30.0)
    if resp.error:
        record("maxbytes", side, False, f"请求失败：{resp.error}")
        return None
    ok = (resp.status == 413) and (not resp.has_body())
    record("maxbytes", side, ok,
           f"Content-Length={len(body)}（阈值 {n}）⇒ HTTP {resp.status}"
           f"，body {len(resp.body)}B，Content-Type={resp.header('content-type')!r}"
           f"，conn={resp.conn_reset}"
           f"（期望：413 + 空 body + 无 Content-Type）")
    return resp


def probe_maxbytes_boundary(side, url):
    """边界对照：Content-Length **恰好等于**阈值 ⇒ 不该被拒。

    ⚠️ Go 的判据是严格大于（`> n`）⇒ 等于阈值时放行。
    """
    host, port = base_of(url)
    set_mode("normal")          # ⚠️ 同上：mode 是全局残留状态
    time.sleep(0.2)
    body = b"x" * GO_MAX_BYTES
    resp = raw_request(host, port, method="GET", path=HOT_PATH, body=body,
                       headers={"Content-Type": "application/octet-stream"}, timeout=30.0)
    if resp.error:
        record("maxbytes-edge", side, False, f"请求失败：{resp.error}")
        return
    ok = resp.status != 413
    record("maxbytes-edge", side, ok,
           f"Content-Length={GO_MAX_BYTES}（= 阈值，严格大于才拒）⇒ HTTP {resp.status}"
           f"，body {len(resp.body)}B {resp.body[:40]!r}"
           f"（期望：非 413）")


# ═════════════════════════════════════════════════════════════════════════════
# 探针 2：MaxConns —— 并发在途超过阈值
# ═════════════════════════════════════════════════════════════════════════════
def probe_maxconns(side, url, concurrent, mode="article-slow-short"):
    """Go: maxconnshandler.go:22-35 `latch.TryBorrow()` 拿不到 ⇒ 503 + 裸 WriteHeader。

    ⚠️ **阈值必须两侧同值**（靠启动参数配），否则测的是「阈值不同」而不是「闸门行为」。
    ⚠️ 慢模式让每个请求在途约 3s（HTTP 超时）⇒ 并发窗口足够宽，
       否则请求秒回、名额立刻归还 ⇒ 永远撞不到上限。
    """
    host, port = base_of(url)
    set_mode(mode)
    time.sleep(0.2)
    results = []
    lock = threading.Lock()

    def one():
        r = raw_request(host, port, method="GET", path=HOT_PATH, timeout=20.0)
        with lock:
            results.append(r)

    threads = [threading.Thread(target=one) for _ in range(concurrent)]
    t0 = time.time()
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    elapsed = time.time() - t0

    rejected = [r for r in results if r.status == 503 and not r.has_body()]
    passed = [r for r in results if r.status != 503]
    errors = [r for r in results if r.error]
    ok = len(rejected) > 0
    record("maxconns", side, ok,
           f"{concurrent} 并发在途 ⇒ 裸 503（空 body）{len(rejected)} 个 / "
           f"放行 {len(passed)} 个 / 传输错误 {len(errors)} 个，耗时 {elapsed:.1f}s"
           f"（期望：至少 1 个裸 503）")
    set_mode("normal")
    return results


# ═════════════════════════════════════════════════════════════════════════════
# 探针 3：Breaker —— 连续 5xx 把熔断打开
# ═════════════════════════════════════════════════════════════════════════════
def probe_breaker(side, url, concurrent, waves=3, mode="article-slow-short"):
    """Go: breakerhandler.go:19-44。

    **判别器（关键）**：熔断打开后，拒绝是 **503 + 空 body**；
    而超时也是 503，但 body 是 `"Request Timeout\n"`。
    ⇒ 只看状态码分不出来；**必须看 body 是否为空**。

    Go 侧熔断算法（core/breaker/googlebreaker.go:52-79）：
      · window 10s / buckets 40 / protection 5 ⇒ **窗口内总请求 > 5** 才可能开始丢；
      · `dropRatio = (total - 5 - weightedAccepts) / (total + 1)`；全失败时 weightedAccepts=0
        ⇒ total=20 时 dropRatio ≈ 0.71、total=40 时 ≈ 0.85；
      · `if rand() < dropRatio` ⇒ **概率性拒绝**（两侧概率源不同 ⇒ 比例只可比量级，不能逐次相同）；
      · `forcePassDuration = 1s` ⇒ 距上次放行超过 1s 的请求**强制放行**
        ⇒ 必须**连续**发请求，否则每波都会被强制放行、永远打不开。
    """
    host, port = base_of(url)
    set_mode(mode)
    time.sleep(0.2)

    waves_data = []
    for w in range(waves):
        results = []
        lock = threading.Lock()

        def one():
            r = raw_request(host, port, method="GET", path=HOT_PATH, timeout=25.0)
            with lock:
                results.append(r)

        threads = [threading.Thread(target=one) for _ in range(concurrent)]
        for t in threads:
            t.start()
        for t in threads:
            t.join()

        timeout_hits = [r for r in results if r.status == 503 and r.body == GO_TIMEOUT_BODY]
        drop_hits = [r for r in results if r.status == 503 and not r.has_body()]
        other = [r for r in results
                 if r.status != 503 or (r.body and r.body != GO_TIMEOUT_BODY)]
        # 「其它」再分一层：这才是**客户端熔断**与**HTTP 熔断**交互后的真实形态
        other_kinds = {}
        for r in other:
            key = f"HTTP {r.status}"
            if r.status == 200 and r.body:
                # 业务信封：取 code 字段
                try:
                    key += f" envelope.code={json.loads(r.body.decode('utf-8')).get('code')}"
                except Exception:
                    key += " envelope(unparseable)"
            elif r.status == 500:
                key += " unhandled-exception"
            elif r.body:
                key += f" body={r.body[:30]!r}"
            other_kinds[key] = other_kinds.get(key, 0) + 1
        waves_data.append({
            "wave": w + 1,
            "timeout": len(timeout_hits),
            "drop": len(drop_hits),
            "other": len(other),
            "other_kinds": other_kinds,
            "errors": len([r for r in results if r.error]),
        })
        print(f"    · {side} 第 {w+1} 波：超时 503 {len(timeout_hits)} / "
              f"熔断裸 503 {len(drop_hits)} / 其它 {len(other)} {other_kinds}")

    set_mode("normal")

    total_drop = sum(d["drop"] for d in waves_data)
    total_timeout = sum(d["timeout"] for d in waves_data)
    ok = total_drop > 0
    detail = (f"{waves} 波 × {concurrent} 并发 ⇒ 熔断裸 503（空 body）共 {total_drop} 个；"
              f"超时 503（body=Request Timeout）共 {total_timeout} 个"
              f"（期望：> 0 个空 body 的 503 —— 这才证明熔断**真的打开了**）")
    record("breaker", side, ok, detail)
    # 逐波明细单独落一份（跨侧逐波对照时要用）
    RESULTS.append({"probe": "breaker-waves", "side": side, "ok": None,
                    "detail": json.dumps(waves_data, ensure_ascii=False)})
    return waves_data


# ═════════════════════════════════════════════════════════════════════════════
# 探针 4：CpuShedding —— 平台事实
# ═════════════════════════════════════════════════════════════════════════════
def probe_cpushedding(side, url):
    """CpuShedding 在 darwin 上**双侧活体对拍不可能**（Go 侧结构性不可达）。

    依据（go-zero v1.10.2）：
      core/stat/internal/cpu_other.go:1-8   `//go:build !linux` ⇒ RefreshCpu() 恒返回 0
      core/stat/usage.go:32-39               usage = prev*0.95 + cur*0.05，cur 恒 0 ⇒ 恒 0
      core/stat/usage.go:51-53               CpuUsage() 恒返回 0
      core/load/adaptiveshedder.go:245-256   systemOverloaded() = CpuUsage() >= threshold
                                            ⇒ 0 >= 900 恒 false
      core/load/adaptiveshedder.go:208-225   shouldDrop() 还要求 highThru()
      core/load/adaptiveshedder.go:227-243   stillHot() 依赖 droppedRecently，
                                            而它只能由一次成功的 drop 置位
                                            ⇒ 第一次 drop 永远不可能发生 ⇒ 死锁在「永不降载」
    ⇒ **不是「两边行为一致」，是「Go 侧这一闸在本平台恒不触发」**。
    """
    if sys.platform == "darwin":
        note("cpushedding", side,
             "darwin 上 Go 侧结构性不可达（core/stat/internal/cpu_other.go 带 "
             "`//go:build !linux` ⇒ RefreshCpu() 恒 0 ⇒ CpuUsage() 恒 0 ⇒ "
             "systemOverloaded() 恒 false ⇒ shouldDrop() 永假）"
             "⇒ 本闸**无法**做双侧活体对拍，只能静态逐条对照 + 跑 go-zero 上游自带测试")
        return False
    note("cpushedding", side,
         f"平台 {sys.platform}：Go 侧 CpuUsage() 可用 ⇒ 可构造 CPU 压力做活体对拍"
         f"（阈值 {GO_CPU_THRESHOLD}‰）")
    return True


# ═════════════════════════════════════════════════════════════════════════════
def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--probe", default="all",
                    choices=["all", "maxbytes", "maxconns", "breaker", "cpushedding"])
    ap.add_argument("--concurrent", type=int, default=12)
    ap.add_argument("--waves", type=int, default=3)
    args = ap.parse_args()

    sides = [("go", GO_BASE), ("rag", RAG_BASE)]
    print(f"Go  = {GO_BASE}")
    print(f"RAG = {RAG_BASE}\n")

    if args.probe in ("all", "maxbytes"):
        print("── 探针 MaxBytes（阈值 1048576，来自 rest/config.go:53 的 tag 默认）")
        for name, url in sides:
            probe_maxbytes(name, url)
            probe_maxbytes_boundary(name, url)
        print()

    if args.probe in ("all", "maxconns"):
        print(f"── 探针 MaxConns（{args.concurrent} 并发在途；阈值两侧同值，由启动配置决定）")
        for name, url in sides:
            probe_maxconns(name, url, args.concurrent)
        print()

    if args.probe in ("all", "breaker"):
        print(f"── 探针 Breaker（{args.waves} 波 × {args.concurrent} 并发超时请求）")
        for name, url in sides:
            probe_breaker(name, url, args.concurrent, args.waves)
        print()

    if args.probe in ("all", "cpushedding"):
        print("── 探针 CpuShedding（平台可达性判定）")
        for name, url in sides:
            probe_cpushedding(name, url)
        print()

    # ── 汇总 ────────────────────────────────────────────────────────────────
    judged = [r for r in RESULTS if r["ok"] is not None]
    failed = [r for r in judged if not r["ok"]]
    notes = [r for r in RESULTS if r["ok"] is None]
    print("══════════ 汇总 ══════════")
    print(f"  判定项 {len(judged)}  失败 {len(failed)}  说明项 {len(notes)}")
    for r in failed:
        print(f"  FAIL {r['probe']}/{r['side']}: {r['detail']}")
    out = os.path.join(RUN_DIR, "u5-protection.json")
    with open(out, "w", encoding="utf-8") as f:
        json.dump(RESULTS, f, ensure_ascii=False, indent=2)
    print(f"  明细：{out}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
