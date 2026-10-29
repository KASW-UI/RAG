#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""**RFC 9110 感知的 HTTP/1.1 客户端** —— 遇到 interim 1xx 会**跳过**它继续读最终响应。

═══════════════════════════════════════════════════════════════════════════════
【为什么必须有这个模块（这是 users 域探针能不能收干净的关键）】
═══════════════════════════════════════════════════════════════════════════════

RAG 共享层的 `GoStyleClientDisconnectProbe`（**已签字偏离 `SD-2026-10-25-04`**）
会在响应提交**之前**往 socket 裸写 `HTTP/1.1 103 Early Hints\\r\\n\\r\\n`：

  * 出处：`service/common/src/main/java/com/rag/common/observability/GoStyleClientDisconnectProbe.java:234`
    （`PROBE_BYTES`）+ `:378-380`（`WRITE` + `FLUSH` 成对）
  * 间隔：`GoStyleClientDisconnectProbe.java:187` `DEFAULT_PROBE_INTERVAL_MILLIS = 500L`
  * 行数上界：`:361-369`（每请求最多 `maxProbeLines()` 行，默认 4 ⇒ 有效窗口 4×500ms = 2s）

⇒ **凡是耗时 > 500ms 的请求，最终响应之前都会先收到 ≥1 行 103**。

而 **Python 的 `http.client` 3.11 不跳过 1xx**：
实测（`tools/api-full-parity-probe/users/` 落仓前的复算脚本）——
服务端发 `103 Early Hints` 再发 `200 OK`，`urllib.request.urlopen` 得到的是：

    HTTPError STATUS: 103   HEADERS: {}   BODY: b''

⇒ **1 行即失效**，最终响应被整个丢掉 ⇒ 这正是「RAG 侧返回 103 空 body」的机制。
（RAG 仓自己的 javadoc 已写明这一点：`GoStyleClientDisconnectProbe.java` 类注释
  「⚠️ 把 1xx 当最终响应的客户端（实测：Python http.client 3.11 看到 status=103、
  body 为空 ⇒ 1 行即失效）」。）

⇒ JDK `java.net.http` / Go `net/http` / curl / 浏览器 **都会跳过 1xx 再读最终响应**
⇒ ⇒ **本模块按 RFC 9110 §15.2 实现「跳过 interim 1xx」**，
   让探针看到的与真实客户端一致（= 最终响应），同时**把跳过的 1xx 记下来**（留痕）。

⚠️ **但这不等于「1xx 不算差异」**：
  * 本模块**记录**每个请求看到的 interim 1xx（`interim` 字段）⇒ 报告里能数出来；
  * 探针的**重试**有上界（默认 1 次）⇒ **重试后仍不一致 ⇒ 必须红**；
  * 1xx 是**已签字偏离**，不是「允许任意差异」⇒ 它的处置是「显式识别 + 留痕 + 有界重试」。

【与 `tools/difftest/diff.py` 原 `http()` 的差别（为什么不能直接改原文件）】
  * 原文件用 `urllib.request` ⇒ 1xx 即终态（见上）；
  * 原文件的端口表 `BASE` 是**硬编码**的 ⇒ 并行实例会打到别人的服务上；
⇒ 本模块是**域内副本**，原文件一个字都没动（`tools/difftest/**` 是主线共享面）。
"""

import os
import socket
import time
import urllib.parse

__all__ = ["HttpResult", "request"]


class HttpResult(object):
    """一次 HTTP 交互的结果。

    字段：
      status       最终响应状态码（int）；传输层失败时为 0
      text         最终响应体（str，utf-8/replace 解码）
      headers      最终响应的头（dict，键名**原样**保留；取值用 `headers.get()`）
      interim      途中跳过的 interim 1xx 状态码列表（int 列表，**留痕用**）
      error        传输层错误描述（str）；None = 无错误
      elapsed_ms   本次请求的**端到端耗时**（ms，float）⇒ 用来把
                   「两侧各跳的实际耗时」打出来对比（**不是**猜「撞了」）
    """

    __slots__ = ("status", "text", "headers", "interim", "error", "elapsed_ms")

    def __init__(self, status=0, text="", headers=None, interim=None, error=None,
                 elapsed_ms=0.0):
        self.status = status
        self.text = text
        self.headers = headers or {}
        self.interim = interim or []
        self.error = error
        self.elapsed_ms = elapsed_ms

    @property
    def ok(self):
        return self.error is None and self.status != 0

    def __repr__(self):
        return "HttpResult(status=%r, interim=%r, error=%r, len(text)=%d, %.1fms)" % (
            self.status, self.interim, self.error, len(self.text or ""), self.elapsed_ms)


def _read_headers(fp):
    """读一段 HTTP 头（到空行为止）⇒ 返回 (dict, 原始行列表)。"""
    headers = {}
    raw_lines = []
    while True:
        line = fp.readline()
        if not line:
            break
        s = line.decode("iso-8859-1", "replace").rstrip("\r\n")
        if s == "":
            break
        raw_lines.append(s)
        if ":" not in s:
            continue
        k, v = s.split(":", 1)
        headers[k.strip()] = v.strip()
    return headers, raw_lines


def _read_body(fp, headers):
    """按 Content-Length / chunked / EOF 三种形态读最终响应体。"""
    te = (headers.get("Transfer-Encoding") or "").lower()
    if "chunked" in te:
        chunks = []
        while True:
            size_line = fp.readline()
            if not size_line:
                break
            size = int(size_line.split(b";")[0].strip() or b"0", 16)
            if size == 0:
                # 读 trailer 直到空行
                while True:
                    t = fp.readline()
                    if not t or t in (b"\r\n", b"\n"):
                        break
                break
            chunks.append(fp.read(size))
            fp.read(2)                      # CRLF
        return b"".join(chunks)

    cl = headers.get("Content-Length")
    if cl is not None:
        try:
            n = int(cl)
        except ValueError:
            n = 0
        if n <= 0:
            return b""
        data = fp.read(n)
        return data if data is not None else b""

    # 无长度信息 ⇒ 读到 EOF（`Connection: close` 语义）
    return fp.read()


def request(method, url, body=None, headers=None, raw=None, timeout=30.0):
    """发一个 HTTP/1.1 请求，**跳过 interim 1xx**，返回最终响应。

    ⚠️ 每个请求**独立开一条连接**（`Connection: close`）⇒
       ① RAG 侧的 1xx 预算是**按请求**计的（`GoStyleClientDisconnectProbe` 的
          `request.setAttribute(PROBE_LINES_ATTRIBUTE, …)`）⇒ 一个请求的行数不会
          污染下一个请求；
       ② 不依赖 keep-alive 的连接状态 ⇒ 不会出现「读到上一次的残留 body」那类假绿。

    ⚠️ 返回**不抛异常**：传输层失败一律折成 `HttpResult(error=…)`（status=0），
       由调用方决定重试还是判红 —— 探针里「抛异常」会被当成「测试崩了」而不是「差异」。
    """
    hdrs = dict(headers or {})
    data = None
    if raw is not None:
        data = raw
        hdrs.setdefault("Content-Type", "application/json")
    elif body is not None:
        import json as _json
        data = _json.dumps(body).encode("utf-8")
        hdrs.setdefault("Content-Type", "application/json")

    parsed = urllib.parse.urlsplit(url)
    host = parsed.hostname or "127.0.0.1"
    port = parsed.port or 80
    target = parsed.path or "/"
    if parsed.query:
        target += "?" + parsed.query

    req = ["%s %s HTTP/1.1" % (method, target),
           "Host: %s:%d" % (host, port),
           "Accept: */*",
           "Connection: close"]
    for k, v in hdrs.items():
        req.append("%s: %s" % (k, v))
    if data is not None:
        req.append("Content-Length: %d" % len(data))
    req.append("")
    req.append("")
    head = ("\r\n".join(req)).encode("iso-8859-1")

    sock = None
    started = time.monotonic()
    try:
        sock = socket.create_connection((host, port), timeout=timeout)
        sock.settimeout(timeout)
        sock.sendall(head)
        if data is not None:
            sock.sendall(data)
        fp = sock.makefile("rb")

        interim = []
        while True:
            status_line = fp.readline()
            if not status_line:
                return HttpResult(0, "", {}, interim,
                                  error="连接在读完最终响应头之前就断了（EOF）",
                                  elapsed_ms=(time.monotonic() - started) * 1000.0)
            parts = status_line.decode("iso-8859-1", "replace").rstrip("\r\n").split(None, 2)
            if len(parts) < 2:
                return HttpResult(0, "", {}, interim,
                                  error="状态行畸形：%r" % status_line[:120],
                                  elapsed_ms=(time.monotonic() - started) * 1000.0)
            try:
                code = int(parts[1])
            except ValueError:
                return HttpResult(0, "", {}, interim,
                                  error="状态码不是数字：%r" % parts[1][:40],
                                  elapsed_ms=(time.monotonic() - started) * 1000.0)
            resp_headers, _ = _read_headers(fp)
            # 🔴 RFC 9110 §15.2：1xx 是 **informational**，客户端**必须忽略**它、
            #    继续等最终响应 ⇒ 这里 continue 而不是当成终态。
            if 100 <= code < 200:
                interim.append(code)
                # ⚠️ **变异开关**（`mutate-parity.sh` 的 M4 用）：
                #    `PROBE_DISABLE_1XX_SKIP=1` ⇒ 退回 **Python urllib 的行为**
                #    （把 1xx 当终态 ⇒ body 空）⇒ 用来证明「探针变绿**确实是**
                #    这个 RFC 感知带来的」，而不是碰巧。
                if os.environ.get("PROBE_DISABLE_1XX_SKIP") == "1":
                    return HttpResult(code, "", resp_headers, interim, error=None,
                                      elapsed_ms=(time.monotonic() - started) * 1000.0)
                continue
            body_bytes = _read_body(fp, resp_headers)
            try:
                fp.close()
            except Exception:
                pass
            return HttpResult(code, body_bytes.decode("utf-8", "replace"), resp_headers, interim,
                              elapsed_ms=(time.monotonic() - started) * 1000.0)
    except Exception as exc:                                  # noqa: BLE001
        return HttpResult(0, "", {}, [], error="%s: %s" % (type(exc).__name__, exc),
                          elapsed_ms=(time.monotonic() - started) * 1000.0)
    finally:
        if sock is not None:
            try:
                sock.close()
            except Exception:
                pass
