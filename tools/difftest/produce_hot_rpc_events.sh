#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════════════
#  hot-rpc 双侧活体对拍（D1）的事件生产者
# ═══════════════════════════════════════════════════════════════════════════
#  把 tools/difftest/hot_rpc_events.py 生成的固定事件集投进 Kafka。
#
#  【为什么要 key】
#    以 article_id 作 key ⇒ Kafka 默认分区器（murmur2(key) % 分区数）把**同一篇文章
#    的全部事件落到同一分区** ⇒ 每篇文章只出现在一个分片的 sketch 里，
#    合并时不会被重复求和；同时 3 个分区都会被用到（两侧都只读 p0..p2）。
#    ⚠️ 分区归属由**生产时**决定 ⇒ 两侧 consumer group 读到的分区布局天然一致。
#
#  【为什么用容器内的 kafka-console-producer.sh】
#    不引入任何新依赖（无 confluent-kafka / kafka-python / grpcurl），
#    且事件内容按行原样投递，不做任何再序列化。
#
#  【前提】两侧 consumer 必须**已经加入消费组并拿到分区**才能收到消息：
#    Go 是 sarama.OffsetNewest、RAG 是 auto-offset-reset=latest
#    ⇒ 事件必须在两侧都起来**之后**投。
# ═══════════════════════════════════════════════════════════════════════════
set -euo pipefail

EVENTS_FILE="${1:-/tmp/hot-rpc-difftest/events/events.tsv}"
KAFKA_CONTAINER="${KAFKA_CONTAINER:-hotrpcdifftest-kafka}"
TOPIC="${TOPIC:-article-hot-events}"
BOOTSTRAP="${BOOTSTRAP:-localhost:19092}"

if [[ ! -f "$EVENTS_FILE" ]]; then
  echo "ERROR: events file not found: $EVENTS_FILE" >&2
  exit 2
fi

COUNT=$(wc -l < "$EVENTS_FILE" | tr -d ' ')
echo "producing $COUNT events into topic '$TOPIC' (container=$KAFKA_CONTAINER)"

# ⚠️ **parse.key 与 key.separator 是 console-producer 的「message reader」属性，
#    要用 `--property` 传 —— 不是 `--producer-property`，也不是 JVM 系统属性。**
#    （`kafka-console-producer.sh --help` 里它们列在 `--property` 那一节：
#      "A mechanism to pass user-defined properties in the form key=value to
#       the message reader."）
#    踩过的坑（连踩两次）：
#      ① `--producer-property parse.key=true` ⇒ 只是「未知 producer 配置」告警
#         ⇒ parse.key 实际没生效 ⇒ 整行进 value、key=null
#      ② 改用 JVM 系统属性 `-Dparse.key=true`（KAFKA_OPTS）⇒ 同样没生效
#    ⇒ 两次的共同症状：**整行（含分隔符）进了 value、key=null**
#    ⇒ 消费者拿到 "art-021|{...}" 这种非 JSON ⇒ 两侧 unmarshal 全失败
#    ⇒ 症状是「两侧都空」，**看起来像业务没跑**，实际是投递格式错了。
#    ⇒ 正确写法：`--property parse.key=true --property key.separator='|'`。
#
# ⚠️ 另一个坑：**容器内的 bootstrap 地址要用 INTERNAL 监听器**。
#    compose 里 PLAINTEXT 的 advertised 地址是 `127.0.0.1:23902`（宿主映射端口），
#    容器内连 127.0.0.1:23902 连不上 ⇒ 必须用 `localhost:19092`（INTERNAL）。
tr '\t' '|' < "$EVENTS_FILE" | docker exec -i \
  "$KAFKA_CONTAINER" \
  /opt/kafka/bin/kafka-console-producer.sh \
    --bootstrap-server "$BOOTSTRAP" \
    --topic "$TOPIC" \
    --property parse.key=true \
    --property key.separator='|' \
    --producer-property acks=all

echo "produced $COUNT events"
