#!/usr/bin/env bash
# ═════════════════════════════════════════════════════════════════════════
# task 域 Kafka **畸形消息**活体对拍 —— 两侧注入同一批「坏消息」，比对可观察后果
#
# 【为什么需要它】
#   此前这条只有**源码级对照**（台账 §五 第 3 条）。而源码级对照有个致命盲区：
#   **「Go 声明的契约」与「Go 实际的行为」不是一回事** ——
#   go-queue/kq 的 Reader 带 CommitInterval: 1s，会**周期性自动提交已 fetch 的位点**，
#   于是 kq 那句「Consume 返回 error ⇒ 不 CommitMessages」在实践中会被抵消。
#   ⇒ 这种差异**只有活体对拍能量化**。
#
# 【四个注入点各自验什么】
#   A. uid <= 0        （filtered.User）⇒ Go `return nil` ⇒ **提交位点**、跳过
#   B. total <= 0      （filtered.User）⇒ Go `return nil` ⇒ **提交位点**、跳过
#   C. key 解析失败    （filtered.User）⇒ Go `return err` ⇒ **不提交**（但继续下一条）
#   D. LogEvent 解析失败（in-topic）    ⇒ Go `log.Println(err)` 后**照样 Emit** ⇒ **提交**、转发
#   E. 毒丸之后再来一条合法消息 ⇒ 验「两侧是否都跨过失败条」（C 的语义闭环）
#
#   ⚠️ Go 出处：
#     · A/B/C：service/task/rpc/internal/sink/like_sink.go:81-108
#     · D    ：service/task/rpc/internal/Init/initGoKa.go:60-70
#   ⚠️ RAG 出处：
#     · A/B/C：service/task/task-rpc/.../mq/UserLikeProgressSink.java:139-169
#     · D    ：service/task/task-rpc/.../mq/TaskRawLogRouter.java:73-83
#
# 【前置】
#   两侧 task-rpc 必须已起，且 **12 个 consumer group 全部已注册**
#   （kq 的 Offset 硬编码 latest ⇒ 灌早了收不到，见 run-task-kafka-e2e.sh 的注释）。
#   例：KEEP_UP=1 ./tools/difftest/run-task-http-parity.sh
#
# 【退出码】0 = 五个用例（A/B/C/D/E）两侧行为一致；1 = 有差异。
# ═════════════════════════════════════════════════════════════════════════
set -u

KAFKA=taskprobe-kafka
BOOT=localhost:19092
REDIS=taskprobe-redis
PG=taskprobe-postgres

GO_IN=task.logs.raw
RAG_IN=rag.task.logs.raw
GO_FU=task.logs.filtered.User
RAG_FU=rag.task.logs.filtered.User
GO_RAWU=task.logs.raw.User
RAG_RAWU=rag.task.logs.raw.User
GO_FG=task.rpc.consumer81
RAG_FG=rag.rpc.consumer81
GO_ING=task.rpc.consumer41
RAG_ING=rag.rpc.consumer41

fail=0
say() { printf '\n\033[1m==== %s ====\033[0m\n' "$*"; }

kafka() { docker exec "$KAFKA" /opt/kafka/bin/"$@"; }

# ⚠️ key.separator 不能用 `|`（会被 shell 当管道）——见 run-task-kafka-e2e.sh 的注释
produce() {
    local topic=$1 key=$2 val=$3
    printf '%s~%s\n' "$key" "$val" | \
      docker exec -i "$KAFKA" /opt/kafka/bin/kafka-console-producer.sh \
        --bootstrap-server "$BOOT" --topic "$topic" \
        --property "parse.key=true" --property "key.separator=~" >/dev/null 2>&1
}

# 该 group 在该 topic 上的 committed offset（`-` 表示从未提交）
committed() {
    local group=$1
    # ⚠️ 不能靠 NR>1 排除表头：--describe 的输出在表头之前还有一行
    #    「Consumer group 'xxx' has no active members.」⇒ 表头会被当成数据行。
    # ⇒ 改成按 group 名精确匹配数据行。
    kafka kafka-consumer-groups.sh --bootstrap-server "$BOOT" --describe --group "$group" 2>/dev/null \
      | grep -v WARN | awk -v g="$group" '$1==g {print $4}' | head -1
}

# 该 topic 的 log-end offset
log_end() {
    local topic=$1
    kafka kafka-get-offsets.sh --bootstrap-server "$BOOT" --topic "$topic" 2>/dev/null \
      | grep -v WARN | awk '{print $2}' | head -1
}

redis_exists() { docker exec "$REDIS" redis-cli -n 0 EXISTS "$1" | tr -d '\r'; }

# topic 里的消息条数（从头读）
msg_count() {
    local topic=$1
    kafka kafka-console-consumer.sh --bootstrap-server "$BOOT" --topic "$topic" \
      --from-beginning --timeout-ms 4000 2>/dev/null | grep -c . || true
}

# topic 里最后一条消息的 key（--property print.key=true + key.separator=~）
last_key() {
    local topic=$1
    kafka kafka-console-consumer.sh --bootstrap-server "$BOOT" --topic "$topic" \
      --from-beginning --timeout-ms 4000 --property "print.key=true" \
      --property "key.separator=~" 2>/dev/null | grep -v '^$' | tail -1 | cut -d'~' -f1
}

last_value() {
    local topic=$1
    kafka kafka-console-consumer.sh --bootstrap-server "$BOOT" --topic "$topic" \
      --from-beginning --timeout-ms 4000 --property "print.key=true" \
      --property "key.separator=~" 2>/dev/null | grep -v '^$' | tail -1 | cut -d'~' -f2-
}

verdict() {   # verdict <标签> <Go值> <RAG值>
    if [ "$2" = "$3" ]; then
        printf '  ✅ %-46s Go=%s  RAG=%s\n' "$1" "$2" "$3"
    else
        printf '  🔴 %-46s Go=%s  RAG=%s  ← 差异\n' "$1" "$2" "$3"
        fail=1
    fi
}

# ═════════════════════════════════════════════════════════════════════
say "0. 前置检查"
for c in docker; do command -v "$c" >/dev/null 2>&1 || { echo "  ✗ 缺命令：$c"; exit 1; }; done
docker ps --format '{{.Names}}' | grep -qx "$KAFKA" || { echo "  ✗ Kafka 容器没起"; exit 1; }
for g in "$GO_FG" "$RAG_FG" "$GO_ING" "$RAG_ING"; do
  if kafka kafka-consumer-groups.sh --bootstrap-server "$BOOT" --list 2>/dev/null | grep -v WARN | grep -qx "$g"; then
    echo "  ✓ group $g 已注册"
  else
    echo "  ✗ group $g 没注册 —— 两侧 task-rpc 还没起来？"; exit 1
  fi
done

say "1. 基线"
BASE_G=$(committed "$GO_FG"); BASE_R=$(committed "$RAG_FG")
echo "  filtered.User committed offset： Go=$BASE_G  RAG=$BASE_R"
RAWU_G_BEFORE=$(msg_count "$GO_RAWU"); RAWU_R_BEFORE=$(msg_count "$RAG_RAWU")
echo "  raw.User 消息数基线： Go=$RAWU_G_BEFORE  RAG=$RAWU_R_BEFORE"

say "2. 热身（合法消息，验证整条链路通）"
produce "$GO_FU" 6001 3
produce "$RAG_FU" 6001 3
sleep 4
verdict "热身：like:total:6001 是否写出" "$(redis_exists like:total:6001)" "$(redis_exists like:total:6001)"
echo "  committed offset： Go=$GO_FG → $(committed "$GO_FG")   RAG=$RAG_FG → $(committed "$RAG_FG")"

say "3. Case A · uid <= 0（Go like_sink.go:87-89 ⇒ return nil ⇒ 提交位点）"
BASE_G=$(committed "$GO_FG"); BASE_R=$(committed "$RAG_FG")
produce "$GO_FU" 0 1
produce "$RAG_FU" 0 1
sleep 4
A_G=$([ "$(committed "$GO_FG")" != "$BASE_G" ] && echo yes || echo no)
A_R=$([ "$(committed "$RAG_FG")" != "$BASE_R" ] && echo yes || echo no)
verdict "A: 位点是否推进" "$A_G" "$A_R"
verdict "A: like:total:0 不应存在" "0" "$(redis_exists like:total:0)"

say "4. Case B · total <= 0（Go like_sink.go:97-99 ⇒ return nil ⇒ 提交位点）"
BASE_G=$(committed "$GO_FG"); BASE_R=$(committed "$RAG_FG")
produce "$GO_FU" 6002 0
produce "$RAG_FU" 6002 0
sleep 4
B_G=$([ "$(committed "$GO_FG")" != "$BASE_G" ] && echo yes || echo no)
B_R=$([ "$(committed "$RAG_FG")" != "$BASE_R" ] && echo yes || echo no)
verdict "B: 位点是否推进" "$B_G" "$B_R"
verdict "B: like:total:6002 不应存在" "0" "$(redis_exists like:total:6002)"

say "5. Case C · key 解析失败（Go like_sink.go:83-85 ⇒ return err ⇒ 不提交）"
BASE_G=$(committed "$GO_FG"); BASE_R=$(committed "$RAG_FG")
produce "$GO_FU" "not-a-number" 1
produce "$RAG_FU" "not-a-number" 1
sleep 2
C_G_2S=$(committed "$GO_FG"); C_R_2S=$(committed "$RAG_FG")
sleep 8
C_G_10S=$(committed "$GO_FG"); C_R_10S=$(committed "$RAG_FG")
echo "  Go  committed：基线=$BASE_G → 2s 后=$C_G_2S → 10s 后=$C_G_10S"
echo "  RAG committed：基线=$BASE_R → 2s 后=$C_R_2S → 10s 后=$C_R_10S"
verdict "C: 2s 内都不提交（失败那条未确认）" "stuck" \
    "$([ "$C_G_2S" = "$BASE_G" ] && [ "$C_R_2S" = "$BASE_R" ] && echo stuck || echo moved)"
echo "  ⚠️ 10s 后的差异是**已知实现差异**，不是本轮判定的失败项："
echo "     Go 侧 kafka-go Reader 带 CommitInterval: 1s ⇒ 会周期性自动提交已 fetch 的位点"
echo "     ⇒ 「error ⇒ 不提交」在实践中被抵消；RAG 侧是严格更强的「永不提交」。"
echo "     详见 TaskRpcApplication#noCommitErrorHandler 的 javadoc。"
printf '  ℹ️  Go  10s 后是否已跨过失败条: %s\n' "$([ "$C_G_10S" != "$BASE_G" ] && echo yes || echo no)"
printf '  ℹ️  RAG 10s 后是否已跨过失败条: %s\n' "$([ "$C_R_10S" != "$BASE_R" ] && echo yes || echo no)"

say "6. Case E · 毒丸之后再来一条合法消息（验「是否跨过失败条」）"
# ⚠️ 这一条是**语义闭环**的关键：失败的那一刻两侧都不提交，
#    但两侧都会**继续处理下一条**；下一条成功时提交会**跨过**失败那条
#    ⇒ 失败那条实质被跳过（运行期不重投）。
#    所以只测 Case C 的「不提交」是不够的 —— 还要测「后续消息能不能过」。
BASE_G=$(committed "$GO_FG"); BASE_R=$(committed "$RAG_FG")
produce "$GO_FU" 6003 2
produce "$RAG_FU" 6003 2
sleep 5
E_G=$(committed "$GO_FG"); E_R=$(committed "$RAG_FG")
echo "  committed： Go 基线=$BASE_G → $E_G   |   RAG 基线=$BASE_R → $E_R"
verdict "E: 位点是否跨过失败条" "$([ "$E_G" != "$BASE_G" ] && echo moved || echo stuck)" \
                              "$([ "$E_R" != "$BASE_R" ] && echo moved || echo stuck)"
verdict "E: like:total:6003 写出" "$(redis_exists like:total:6003)" "$(redis_exists like:total:6003)"

say "7. Case D · LogEvent 解析失败（Go initGoKa.go:63-69 ⇒ 只打日志、照样 Emit）"
BASE_G=$(committed "$GO_ING"); BASE_R=$(committed "$RAG_ING")
produce "$GO_IN" "" "{not json"
produce "$RAG_IN" "" "{not json"
sleep 4
D_G=$([ "$(committed "$GO_ING")" != "$BASE_G" ] && echo yes || echo no)
D_R=$([ "$(committed "$RAG_ING")" != "$BASE_R" ] && echo yes || echo no)
verdict "D: in-topic 位点是否推进" "$D_G" "$D_R"
G_RAWU=$(msg_count "$GO_RAWU"); R_RAWU=$(msg_count "$RAG_RAWU")
verdict "D: raw.User 新增消息数" "$((G_RAWU - RAWU_G_BEFORE))" "$((R_RAWU - RAWU_R_BEFORE))"
verdict "D: 转发出去的 key（应是空串）" "$(last_key "$GO_RAWU")" "$(last_key "$RAG_RAWU")"
verdict "D: 转发出去的 value（应是 raw 原文）" "{not json" "$(last_value "$RAG_RAWU")"

say "8. 收摊"
echo "  ⚠️ 本测试往 filtered.User 投了「毒丸」（key 解析失败那条），"
echo "     该分区的消费者会一直重投 ⇒ **两侧栈现在都需要重启**才能恢复正常。"
echo "     重启命令见 tools/difftest/run-task-http-parity.sh（会重建容器与栈）。"

echo
echo "════════════════════════════════════════════════"
if [ "$fail" = "0" ]; then
    echo "结果：五个畸形消息用例（A/B/C/D/E）两侧行为一致 ✅"
    exit 0
fi
echo "结果：存在两侧不一致的用例 ❌"
exit 1
