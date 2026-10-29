#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成 like 域**事实投递链路**（DRIFT-LIKE-003-A）双侧对拍的播种数据。

产出
----
1. `INSERT INTO like_domain_fact_outbox ...` —— 一条**可投递**的冻结事实（两侧同一条）；
2. `envelope.json` —— 播种用的信封原始字节（供假 DC 侧自检哈希用）。

字段判据（**逐条抄 Go `service/like/rpc/internal/model/fact_delivery.go:23-50`**）
----------------------------------------------------------------------------
| 判据 | Go 出处 | 本脚本怎么满足 |
|---|---|---|
| `event_id` == 行 `event_id` | `fact_delivery.go:25` | 同一个常量 |
| `event_type` == 行 `event_type` | `fact_delivery.go:25` | 同一个常量 |
| `schema_version == 1` | `fact_delivery.go:26` | 信封里写整数 `1` |
| `producer == "rtw.like-mq"` | `fact_delivery.go:26` | `fact_delivery.go:17` 的常量 |
| `aggregate_id` == 行 `aggregate_id` | `fact_delivery.go:27` | 同一个常量 |
| `aggregate_version` == 行 `fact_version` | `fact_delivery.go:27` | 同一个常量 |
| `operation_id` 非空 | `fact_delivery.go:28` | 非空常量 |
| `occurred_at` 可按 RFC3339Nano 解析 | `fact_delivery.go:31-34` | `2026-01-02T03:04:05.123456789Z` |
| 载荷 `schema_version` == `rtw.community-fact.v1` | `fact_delivery.go:37` | 常量 |
| 载荷 `event_id`/`event_type`/`producer`/`operation_id` 与信封一致 | `fact_delivery.go:38-40` | 同一个常量 |
| 载荷 `aggregate_version` **为 nil** | `fact_delivery.go:39` | 载荷里**不写**这个键 |
| 载荷 `subject_ref`/`target_type`/`target_id` 非空 | `fact_delivery.go:40` | 非空常量 |
| 载荷 `occurred_at` 与信封 `occurred_at` **同一瞬时** | `fact_delivery.go:41` | 同一个字符串 |
| 载荷 `event_time`/`available_at` 可解析（RAG 侧额外判据） | `LikeDeliveryValidator.java:230-232` | 两个 RFC3339 串 |
| 载荷的 JCS 哈希 == 行 `payload` 列的 JCS 哈希 | `fact_delivery.go:44-49` | 载荷**逐字段同值**（键序无所谓 ⇒ JCS 归一） |

⚠️ 载荷必须**恰好 17 个键**
--------------------------
Go 的 `likeFact`（`domain_fact.go:54-74`）与 RAG 的 `LikeFact`
（`LikeDeliveryValidator.java:263-312`）都是 **19 个字段**，但其中
`aggregate_version`（Go `*int64`）与 `target_revision`（Go `*string`）
是**指针** ⇒ Go 从不赋值 ⇒ 解出来恒为 `nil`
⇒ **本脚本刻意不写这两个键**（缺席 ⇒ 两侧都是 nil ⇒ 满足
`fact_delivery.go:39` 的 `payload.AggregateVersion != nil` 判据）。

⇒ 剩下 17 个键**一个不多一个不少**：
多一个 ⇒ Go 的 `DisallowUnknownFields` 让**Go 自己**解码失败（该行 BLOCKED）；
少一个 ⇒ RAG 侧的必填判据（`event_time` / `available_at`）会 BLOCKED。
"""

import argparse
import hashlib
import json
import sys

EVENT_ID = "rtw.like.9007199254740993001"
EVENT_TYPE = "community.target.interaction"
PRODUCER = "rtw.like-mq"
AGGREGATE_ID = "like-state/9007199254740993/article/9007199254740994"
FACT_VERSION = 1
OPERATION_ID = "9007199254740993001"
SUBJECT_REF = "rtw.identity/platform/9007199254740993"
TARGET_TYPE = "article"
TARGET_ID = "9007199254740994"
SCHEMA_VERSION = "rtw.community-fact.v1"
OCCURRED_AT = "2026-01-02T03:04:05.123456789Z"
EVENT_TIME = "2026-01-02T03:04:05.123456789Z"
AVAILABLE_AT = "2026-01-02T03:04:05.123456789Z"

# ⚠️ 19 个键 —— Go `likeFact` 与 RAG `LikeFact` 的字段集合（多一个 / 少一个都会被 BLOCKED）。
PAYLOAD = {
    "schema_version": SCHEMA_VERSION,
    "event_id": EVENT_ID,
    "event_type": EVENT_TYPE,
    "producer": PRODUCER,
    "aggregate_id": "article/9007199254740994",
    "operation_id": OPERATION_ID,
    "subject_ref": SUBJECT_REF,
    "target_type": TARGET_TYPE,
    "target_id": TARGET_ID,
    "revision_status": "unknown",
    "operation": "like",
    "source_ref": EVENT_ID,
    "old_state": 0,
    "new_state": 1,
    "event_time": EVENT_TIME,
    "occurred_at": OCCURRED_AT,
    "available_at": AVAILABLE_AT,
}

ENVELOPE = {
    "event_id": EVENT_ID,
    "event_type": EVENT_TYPE,
    "schema_version": 1,
    "producer": PRODUCER,
    "aggregate_id": AGGREGATE_ID,
    "aggregate_version": FACT_VERSION,
    "operation_id": OPERATION_ID,
    "occurred_at": OCCURRED_AT,
    "payload": PAYLOAD,
}


def canonical_bytes(raw: bytes) -> bytes:
    return json.dumps(
        json.loads(raw.decode("utf-8")),
        sort_keys=True,
        separators=(",", ":"),
        ensure_ascii=False,
    ).encode("utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description="生成 like 事实投递链路的播种数据")
    parser.add_argument("--sql-out", required=True, help="输出 INSERT 语句的文件")
    parser.add_argument("--envelope-out", required=True, help="输出信封原始字节的文件")
    args = parser.parse_args()

    payload_text = json.dumps(PAYLOAD, separators=(",", ":"), ensure_ascii=False)
    envelope_text = json.dumps(ENVELOPE, separators=(",", ":"), ensure_ascii=False)

    def lit(text: str) -> str:
        return "'" + text.replace("'", "''") + "'"

    sql = (
        "INSERT INTO like_domain_fact_outbox "
        "(event_id, event_type, payload, aggregate_id, fact_version, delivery_envelope, "
        "delivery_status, retry_count, technical_receipt_id, technical_input_hash, technical_offset) "
        "VALUES ("
        + lit(EVENT_ID) + ", "
        + lit(EVENT_TYPE) + ", "
        + lit(payload_text) + "::jsonb, "
        + lit(AGGREGATE_ID) + ", "
        + str(FACT_VERSION) + ", "
        + lit(envelope_text) + "::jsonb, "
        "0, 0, '', '', 0"
        ");"
    )

    with open(args.sql_out, "w", encoding="utf-8") as fh:
        fh.write(sql + "\n")
    with open(args.envelope_out, "wb") as fh:
        fh.write(envelope_text.encode("utf-8"))

    digest = hashlib.sha256(canonical_bytes(envelope_text.encode("utf-8"))).hexdigest()
    sys.stderr.write(
        "[seed-fact] event_id=%s payload_fields=%d envelope_canonical_sha256=%s\n"
        % (EVENT_ID, len(PAYLOAD), digest)
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
