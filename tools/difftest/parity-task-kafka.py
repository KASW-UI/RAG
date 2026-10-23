#!/usr/bin/env python3
"""
Kafka 端到端对拍 —— 比对 Go 真源侧与 RAG 侧的**最终可观测状态**。

用法（在 task 对拍基建已起的前提下）：
    python3 tools/difftest/parity-task-kafka.py

⚠️ 为什么要三组归一化（否则全是噪声，把真差异埋掉）
----------------------------------------------------
1. **时间戳**：两侧是**两套独立进程**，写入时刻天然不同（实测差 4 秒）。
   Redis Hash 的 `createAt`/`updateAt`/`doneAt` 与奖励流的 `ts` 一律
   只比「在不在」，不比字面值（比值则允许 ≤ `TS_TOLERANCE` 秒）。
2. **Redis Stream 顺序**：`flushRedis` 在两侧都是**遍历 map**
   （Go `like_sink.go:270` 的 `for uid, total := range batch` —— Go 的 map 序**随机化**；
   RAG 是 `HashMap` 序）⇒ 事件顺序**两侧都不是契约**，按集合比。
3. **`task:goka:count:*` 是结构性差异**：RAG 的 goka 计数存在 **Redis**（DRIFT-TASK-RPC-001 的修复），
   Go 的存在 **Kafka compacted table topic**（`task.rpc.consumer*-table`）⇒ 两侧 key 集合**必然不等**。
   ⇒ 这几个 key 单列，并**拿 RAG 的值去对 Go 的 table topic**（真正该比的是计数值）。

对拍维度（都是「客户端能看到的」东西）：
  - Redis：`task:init:*` / `task:inits:*` / `task:progress:*` / `task:done:*` /
            `like:total:*` / `article:like:cnt:*` / `stream:task:reward`
  - Postgres：`task_progress` / `user_like_count`
  - Kafka ：Go 的 goka 计数表 vs RAG 的 Redis 计数
"""
import re
import subprocess
import sys

REDIS_PORT = "56380"
PG_CONTAINER = "taskprobe-postgres"
KAFKA_CONTAINER = "taskprobe-kafka"
KAFKA_BOOT = "localhost:19092"

GO_REDIS_DB = "0"
RAG_REDIS_DB = "1"
GO_DB = "task_go_db"
RAG_DB = "task_rag_db"

TS_TOLERANCE = 120  # 秒

# Redis Hash 里属于「写入时刻」的字段
TS_FIELDS = {"createat", "updateat", "doneat"}
# Redis Stream 里属于「写入时刻」的字段
STREAM_TS_FIELDS = {"ts"}
# RAG 独有的结构性 key（Go 侧对应物在 Kafka compacted table topic 里）
RAG_ONLY_KEY_PREFIX = "task:goka:count:"


def sh(cmd):
    r = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    return r.stdout.strip(), r.stderr.strip(), r.returncode


def redis_keys(db):
    out, _, _ = sh(f"redis-cli -p {REDIS_PORT} -n {db} --scan")
    return sorted(k for k in out.splitlines() if k)


def redis_entry(db, key):
    typ, _, _ = sh(f"redis-cli -p {REDIS_PORT} -n {db} TYPE {key}")
    ttl, _, _ = sh(f"redis-cli -p {REDIS_PORT} -n {db} TTL {key}")
    if typ == "string":
        val, _, _ = sh(f"redis-cli -p {REDIS_PORT} -n {db} GET {key}")
        body = val
    elif typ == "hash":
        out, _, _ = sh(f"redis-cli -p {REDIS_PORT} -n {db} HGETALL {key}")
        lines = out.splitlines()
        body = dict(zip(lines[0::2], lines[1::2]))
    elif typ == "none":
        body = None
    else:
        body = typ
    return {"type": typ, "value": body, "ttl": int(ttl) if ttl.lstrip("-").isdigit() else ttl}


def redis_state(db):
    return {k: redis_entry(db, k) for k in redis_keys(db)}


def redis_stream(db, key):
    """XRange 全量 ⇒ 解析成 [{field: value, ...}]（字段顺序不敏感）。"""
    out, _, _ = sh(f"redis-cli -p {REDIS_PORT} -n {db} XRANGE {key} - +")
    entries = []
    cur = None
    for line in out.splitlines():
        if re.fullmatch(r"\d+-\d+", line):
            cur = {}
            entries.append(cur)
        elif cur is not None:
            cur.setdefault("_f", []).append(line)
    result = []
    for e in entries:
        fields = e.get("_f", [])
        d = dict(zip(fields[0::2], fields[1::2]))
        d.pop("_f", None)
        result.append(d)
    return result


def pg_rows(db, sql):
    out, err, rc = sh(
        f"docker exec {PG_CONTAINER} psql -U admin -d {db} -At -F'|' -c \"{sql}\""
    )
    if rc != 0:
        print(f"[psql error db={db}] {err}", file=sys.stderr)
        return []
    return sorted(line.split("|") for line in out.splitlines() if line.strip())


def pg_state(db):
    return {
        "task_progress": pg_rows(
            db,
            "SELECT user_id, task_id, status, progress, target, "
            "(done_at IS NULL) FROM task_progress",
        ),
        "user_like_count": pg_rows(db, "SELECT user_id, like_count FROM user_like_count"),
    }


def goka_table_counts(topic):
    """读 Go 侧 goka 的 compacted table topic ⇒ {key: 最后一次的值}。

    ⚠️ 分隔符**不能用 `|`** —— shell 会把它当管道符（`... key.separator=|` ⇒
    实际执行成「把前半段接到 stderr」⇒ 读出来是空的）。
    这里用 `,`（值是纯数字，不会冲突）。
    """
    out, _, _ = sh(
        f"docker exec {KAFKA_CONTAINER} /opt/kafka/bin/kafka-console-consumer.sh "
        f"--bootstrap-server {KAFKA_BOOT} --topic {topic} --from-beginning "
        f"--timeout-ms 8000 --property print.key=true --property print.value=true "
        f"--property key.separator=, 2>/dev/null"
    )
    counts = {}
    for line in out.splitlines():
        if "," not in line:
            continue
        k, v = line.rsplit(",", 1)
        counts[k] = v          # compacted topic 里同一 key 有多版本 ⇒ 取最后一次
    return counts


# ---------------------------------------------------------------- 归一化

def norm_hash_value(value):
    """Hash 值归一：时间戳字段换成 '<ts>'（只保留存在性）。"""
    if not isinstance(value, dict):
        return value
    out = {}
    for k, v in value.items():
        if k.lower() in TS_FIELDS:
            out[k] = "<ts>" if v not in (None, "") else "<empty>"
        else:
            out[k] = v
    return out


def norm_stream_entry(entry):
    out = {}
    for k, v in entry.items():
        out[k] = "<ts>" if k.lower() in STREAM_TS_FIELDS else v
    return out


def norm_stream(entries):
    """Stream 按**集合**比（顺序两侧都不可确定）。"""
    return sorted(
        (tuple(sorted(norm_stream_entry(e).items())) for e in entries),
    )


def ts_close(a, b):
    try:
        return abs(int(a) - int(b)) <= TS_TOLERANCE
    except (TypeError, ValueError):
        return a == b


# ---------------------------------------------------------------- 比对

def diff_redis(go, rag):
    problems = []
    go_keys, rag_keys = set(go), set(rag)
    rag_only = {k for k in rag_keys - go_keys if k.startswith(RAG_ONLY_KEY_PREFIX)}
    for k in sorted(rag_keys - go_keys - rag_only):
        problems.append(f"Redis: 只有 RAG 侧有 {k} = {rag[k]}")
    for k in sorted(go_keys - rag_keys):
        problems.append(f"Redis: 只有 Go 侧有 {k} = {go[k]}")
    for k in sorted(go_keys & rag_keys):
        a, b = go[k], rag[k]
        if a["type"] != b["type"]:
            problems.append(f"Redis {k}: 类型不同 Go={a['type']} RAG={b['type']}")
            continue
        if norm_hash_value(a["value"]) != norm_hash_value(b["value"]):
            problems.append(f"Redis {k}: 值不同 Go={a['value']!r} RAG={b['value']!r}")
        if isinstance(a["ttl"], int) and isinstance(b["ttl"], int):
            if (a["ttl"] < 0) != (b["ttl"] < 0):
                problems.append(f"Redis {k}: TTL 存在性不同 Go={a['ttl']} RAG={b['ttl']}")
            elif a["ttl"] >= 0 and abs(a["ttl"] - b["ttl"]) > TS_TOLERANCE:
                problems.append(
                    f"Redis {k}: TTL 差太多 Go={a['ttl']}s RAG={b['ttl']}s（阈值 {TS_TOLERANCE}s）"
                )
    return problems


def diff_stream(go_entries, rag_entries):
    g, r = norm_stream(go_entries), norm_stream(rag_entries)
    if g == r:
        return []
    return [f"奖励流 stream:task:reward 内容不同\n      Go  ={g}\n      RAG ={r}"]


def diff_counts(go_user, go_article, rag_state):
    """RAG 的 task:goka:count:* vs Go 的 compacted table topic（真正该比的是计数值）。"""
    problems = []
    pairs = [
        ("user 路", "rag.rpc.consumer61", go_user),
        ("article 路", "rag.rpc.consumer71", go_article),
    ]
    for label, group, table in pairs:
        rag_vals = {
            k[len(RAG_ONLY_KEY_PREFIX + group + ":"):]: v["value"]
            for k, v in rag_state.items()
            if k.startswith(RAG_ONLY_KEY_PREFIX + group + ":")
        }
        # Go 的 table topic 里可能有同一 key 的多版本 ⇒ 取最后出现的
        go_last = {}
        for k, v in table.items():
            go_last[k] = v
        if go_last != rag_vals:
            problems.append(
                f"goka 计数（{label}）不同：Go={go_last} RAG={rag_vals}"
            )
    return problems


def main():
    go_r = redis_state(GO_REDIS_DB)
    rag_r = redis_state(RAG_REDIS_DB)
    go_p = pg_state(GO_DB)
    rag_p = pg_state(RAG_DB)
    go_stream = redis_stream(GO_REDIS_DB, "stream:task:reward")
    rag_stream = redis_stream(RAG_REDIS_DB, "stream:task:reward")

    print("=" * 74)
    print("Redis（Go db0 / RAG db1）")
    print("=" * 74)
    for label, st in (("GO ", go_r), ("RAG", rag_r)):
        for k in sorted(st):
            e = st[k]
            tag = "  ← RAG 独有（结构性）" if k.startswith(RAG_ONLY_KEY_PREFIX) else ""
            print(f"  {label} {k}  type={e['type']} ttl={e['ttl']}  value={e['value']}{tag}")
    print()
    print("=" * 74)
    print("奖励流 stream:task:reward")
    print("=" * 74)
    for label, entries in (("GO ", go_stream), ("RAG", rag_stream)):
        for e in entries:
            print(f"  {label} {e}")
    print()
    print("=" * 74)
    print("Postgres")
    print("=" * 74)
    for label, st in (("GO ", go_p), ("RAG", rag_p)):
        for table, rows in st.items():
            for row in rows:
                print(f"  {label} {table}: {row}")
    print()

    problems = []
    problems += diff_redis(go_r, rag_r)
    problems += diff_stream(go_stream, rag_stream)
    for table in go_p:
        if go_p[table] != rag_p.get(table):
            problems.append(f"PG {table}: Go={go_p[table]} RAG={rag_p.get(table)}")
    problems += diff_counts(
        goka_table_counts("task.rpc.consumer61-table"),
        goka_table_counts("task.rpc.consumer71-table"),
        rag_r,
    )

    print("=" * 74)
    if problems:
        print(f"❌ 发现 {len(problems)} 处差异：")
        for p in problems:
            print("   - " + p)
        print("=" * 74)
        return 1
    print("✅ Kafka 端到端最终态：Go 与 RAG 完全一致")
    print("   （Redis 全 key + 奖励流 + PG 两表 + goka 计数值）")
    print("=" * 74)
    return 0


if __name__ == "__main__":
    sys.exit(main())
