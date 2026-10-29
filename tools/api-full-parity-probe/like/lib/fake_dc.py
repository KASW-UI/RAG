#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""**假 DataCenter 接收端** —— like 域事实投递链路（DRIFT-LIKE-003-A）双侧对拍的接收方。

为什么需要它
------------
Go 真源 `service/like/rpc/cmd/fact-dispatch/main.go:51-77` 要求三样环境变量：

    LIKE_DATABASE_URL / DC_PLATFORM_EVENT_URL / DC_PLATFORM_SERVICE_TOKEN（≥32 字节）

其中 `DC_PLATFORM_EVENT_URL` 指向 **DataCenter 的事件接收端** —— 而
**真 DC 接收服务不在任何仓里**（`docs/DRIFT/like/rpc.md` §12.2 的原话）。
⇒ 没有它，「投递链路」这一段**根本无法取证** ⇒ 本文件就是那缺失的另一半。

本进程实现的是 **wire 契约**（Go `service/common/communityfact/wire.go`）：

| 方向 | 契约 | 出处 |
|---|---|---|
| 请求 | `POST {endpoint}`，body = **信封列的原始字节** | `wire.go:97-104` |
| 请求头 | `Content-Type: application/json` · `Idempotency-Key: {event_id}` · `Authorization: Bearer {token}` | `wire.go:101-103` |
| 响应码 | `201` 或 `200`（其余 ⇒ 投递失败） | `wire.go:110-113` |
| 响应体 | `TechnicalReceipt`：**恰好 7 个字段**（`DisallowUnknownFields` ⇒ 多一个就解码失败） | `wire.go:50-58` + `wire.go:115-118` |

⚠️ `input_hash` 的格式约束
---------------------------
`ValidateReceipt`（`wire.go:142-150`）只校验「64 位小写十六进制」**格式**，
**不**校验它等于 payload 的哈希 ⇒ 理论上可以返回任意 64 hex。
⇒ 但本进程**真的按收到的字节算 JCS 哈希**（`wire.go:133-131` 的 `CanonicalHash`
= RFC 8785 JCS 规范化 + SHA-256），理由有两条：
① 探针要能证明「DC 收到的字节 == 探针播种的字节」（哈希相等即可证）；
② 探针把 `payload` 列的键序**故意排成与信封内嵌 payload 不同的顺序**
   ⇒ 只有「两侧的 JCS 规范化都按键排序」时两侧才会 ACCEPTED（而不是 BLOCKED）
   ⇒ **跨语言的规范化一致性因此被这条用例钉住**。

JCS 的适用范围
--------------
本探针播种的 payload **刻意只用 ASCII 键名与整数值** ⇒
Python 的 `json.dumps(sort_keys=True, separators=(",", ":"), ensure_ascii=False)`
与 RFC 8785 的输出**逐字节相同**（无数值格式化差异、无非 ASCII 转义）。
⇒ 探针脚本里有一条**自检**：本进程算出的哈希必须等于探针本地用同一算法算出的哈希。
"""

import argparse
import hashlib
import json
import os
import sys
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

# 探针播种的 payload 只含 ASCII + 整数 ⇒ Python 的 canonical dumps == RFC 8785 JCS。
def canonical_bytes(raw: bytes) -> bytes:
    return json.dumps(
        json.loads(raw.decode("utf-8")),
        sort_keys=True,
        separators=(",", ":"),
        ensure_ascii=False,
    ).encode("utf-8")


def canonical_hash(raw: bytes) -> str:
    return hashlib.sha256(canonical_bytes(raw)).hexdigest()


class Handler(BaseHTTPRequestHandler):
    # 由 main() 注入
    out_dir = "."
    counter = 0

    def log_message(self, fmt, *args):  # noqa: A003 —— 静默默认 stderr 日志
        sys.stderr.write("[fake-dc] " + (fmt % args) + "\n")

    def do_POST(self):  # noqa: N802 —— BaseHTTPRequestHandler 的命名约定
        if self.path != "/v1/events":
            self.send_response(404)
            self.send_header("Content-Length", "0")
            self.end_headers()
            return

        length = int(self.headers.get("Content-Length", "0"))
        raw = self.rfile.read(length) if length else b""

        seq = Handler.counter
        Handler.counter += 1
        body_path = os.path.join(Handler.out_dir, "received-%02d.bin" % seq)
        with open(body_path, "wb") as fh:
            fh.write(raw)
        header_path = os.path.join(Handler.out_dir, "received-%02d.headers" % seq)
        with open(header_path, "w", encoding="utf-8") as fh:
            for key in ("Content-Type", "Idempotency-Key", "Authorization"):
                fh.write("%s: %s\n" % (key, self.headers.get(key, "")))

        try:
            event = json.loads(raw.decode("utf-8"))
        except Exception as exc:  # noqa: BLE001 —— 坏信封 ⇒ 500，让投递方按失败处理
            self.send_response(500)
            self.send_header("Content-Length", "0")
            self.end_headers()
            self.log_message("bad envelope: %s", exc)
            return

        digest = canonical_hash(raw)
        receipt = {
            "event_id": event.get("event_id", ""),
            "producer": event.get("producer", ""),
            "technical_status": "accepted",
            "receipt_id": "rcpt-" + digest[:16],
            "input_hash": digest,
            "offset": 1,
            "received_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%f000Z"),
        }
        payload = json.dumps(receipt, separators=(",", ":")).encode("utf-8")
        self.send_response(201)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)
        self.log_message(
            "accepted event_id=%s input_hash=%s bytes=%d -> %s",
            receipt["event_id"], digest, len(raw), body_path,
        )


def main() -> int:
    parser = argparse.ArgumentParser(description="like 域事实投递链路的假 DataCenter 接收端")
    parser.add_argument("--port", type=int, required=True)
    parser.add_argument("--out-dir", required=True)
    args = parser.parse_args()

    os.makedirs(args.out_dir, exist_ok=True)
    Handler.out_dir = args.out_dir
    server = ThreadingHTTPServer(("127.0.0.1", args.port), Handler)
    sys.stderr.write("[fake-dc] listening on 127.0.0.1:%d, out-dir=%s\n" % (args.port, args.out_dir))
    sys.stderr.flush()
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    return 0


if __name__ == "__main__":
    sys.exit(main())
