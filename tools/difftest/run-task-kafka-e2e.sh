#!/usr/bin/env bash
# ═════════════════════════════════════════════════════════════════════════
# task 域 Kafka 端到端对拍 —— 一键编排
#
#   两侧同时跑：Go 真源 task-rpc + RAG task-rpc
#   往两侧的 in-topic 灌**同一批** raw 日志 → 比对最终可观测状态
#   （Redis 全 key + 奖励流 + PG 两表 + goka 计数值）
#
# 用法：
#   ./tools/difftest/run-task-kafka-e2e.sh              # 完整跑一遍
#   KEEP_UP=1 ./tools/difftest/run-task-kafka-e2e.sh     # 跑完不拆服务与容器
#   GO_REPO=/path/to/Sea-RideTheWind_golang ./…          # 自定义真源路径
#
# ⚠️ 本脚本把「这一轮实测踩过的坑」全部固化在注释里了，别随手改：
#   ① 容器内 Kafka CLI 必须用 **INTERNAL 监听器 localhost:19092**
#   ② 重置 Kafka 必须**重建容器**（删 topic 不删 __consumer_offsets 里的已提交位点）
#   ③ 还必须移走 goka 的**本地 LevelDB 缓存**
#   ④ Go 侧要 **CGO_ENABLED=0** 才能编
#   ⑤ RAG 侧**不是 fat jar**，要用 `java -cp <classes>:<deps>`
#   ⑥ 容器内 CLI 的 key.separator **不能用 `|`**（会被 shell 当管道）
# ═════════════════════════════════════════════════════════════════════════
set -u

HERE=$(cd "$(dirname "$0")" && pwd)
RAG_REPO=${RAG_REPO:-$(cd "$HERE/../.." && pwd)}
WORK=${WORK:-${TMPDIR:-/tmp}/task-kafka-e2e}
KEEP_UP=${KEEP_UP:-0}

# ⚠️ Go 真源路径的推导**不能**假设「与 RAG 同级」：
#    worktree 的布局是 `<RAG.worktrees>/task`，与主仓 `<RAG>` 不同级 ⇒
#    `dirname(RAG_REPO)/Sea-RideTheWind_golang` 会指向不存在的路径。
#    ⇒ 多候选搜索，取第一个含 service/task/rpc 的。
find_go_repo() {
  local cands=()
  [ -n "${GO_REPO:-}" ] && cands+=("$GO_REPO")
  cands+=("$RAG_REPO/../Sea-RideTheWind_golang")
  cands+=("$RAG_REPO/../../Sea-RideTheWind_golang")
  # worktree 的 git-common-dir 指向**主仓**的 .git ⇒ 两级 dirname 得到主仓的同级目录
  local common
  common=$(git -C "$RAG_REPO" rev-parse --git-common-dir 2>/dev/null) && \
    cands+=("$(cd "$(dirname "$common")/.." && pwd)/Sea-RideTheWind_golang")
  for c in "${cands[@]}"; do
    [ -d "$c/service/task/rpc" ] && { (cd "$c" && pwd); return 0; }
  done
  return 1
}
GO_REPO_CANDIDATE=${GO_REPO:-}

COMPOSE="$HERE/docker-compose.task-kafka.yml"
KAFKA_BOOT=localhost:19092          # ① 容器内必须用 INTERNAL 监听器
PG_PORT=55433
REDIS_PORT=56380
KAFKA_PORT=59992

GO_TOPICS=(task.logs.raw task.logs.raw.User task.logs.raw.Article
           task.logs.filtered.User task.logs.filtered.Article)
RAG_TOPICS=(rag.task.logs.raw rag.task.logs.raw.User rag.task.logs.raw.Article
            rag.task.logs.filtered.User rag.task.logs.filtered.Article)
GOKA_GROUPS=(task.rpc.consumer61 task.rpc.consumer71)
GOKA_CACHE_DIR=${GOKA_CACHE_DIR:-/private/tmp/goka/processor}

# ⚠️ 必须用函数而不是字符串变量：
#    写成 KB="docker exec ... /opt/kafka/bin" 再 `$KB xxx.sh`
#    ⇒ 实际执行的是「把 /opt/kafka/bin 当可执行文件」⇒ 删/建 topic 两步**静默失败**
#    （OCI permission denied 被 2>/dev/null 吞掉）⇒ goka 计数表没清零 ⇒ 两侧计数对不上。
kafka() { docker exec taskprobe-kafka /opt/kafka/bin/"$@"; }

say() { printf '\n\033[1m==== %s ====\033[0m\n' "$*"; }

# ═══════════════════════════════════════════════════════════════════════
say "0. 前置检查"
for c in docker go mvn java; do
  command -v "$c" >/dev/null 2>&1 || { echo "  ✗ 缺命令：$c"; exit 1; }
done
GO_REPO=$(GO_REPO="$GO_REPO_CANDIDATE" find_go_repo) \
  || { echo "  ✗ 找不到 Go 真源（可用 GO_REPO=... 指定）"; exit 1; }
echo "  RAG_REPO = $RAG_REPO"
echo "  GO_REPO  = $GO_REPO"
echo "  WORK     = $WORK"
mkdir -p "$WORK/bin" "$WORK/logs"

# ═══════════════════════════════════════════════════════════════════════
say "1. 编 Go 真源二进制（④ CGO_ENABLED=0）"
( cd "$GO_REPO" && CGO_ENABLED=0 go build -o "$WORK/bin/go-task-rpc" ./service/task/rpc ) \
  || { echo "  ✗ Go 编译失败"; exit 1; }
echo "  ✓ $WORK/bin/go-task-rpc"

# ═══════════════════════════════════════════════════════════════════════
say "2. 编 RAG 侧（⑤ 不是 fat jar ⇒ classes + 依赖 classpath）"
( cd "$RAG_REPO" && mvn -o -q -pl service/task/task-rpc -am compile -DskipTests ) \
  || { echo "  ✗ RAG 编译失败"; exit 1; }
( cd "$RAG_REPO" && mvn -o -q -pl service/task/task-rpc dependency:build-classpath \
    -Dmdep.outputFile="$WORK/rag-cp.txt" -DincludeScope=runtime ) \
  || { echo "  ✗ classpath 生成失败"; exit 1; }
RAG_CP="$RAG_REPO/service/task/task-rpc/target/classes:$(cat "$WORK/rag-cp.txt")"
echo "  ✓ classpath $(wc -c < "$WORK/rag-cp.txt" | tr -d ' ') 字节"

# ═══════════════════════════════════════════════════════════════════════
say "3. 起基建（postgres + redis + kafka）"
# ⚠️ 先拆干净：现存同名容器若是**手工 docker run** 建的（不是本 compose 管的），
#    compose 会直接报 name conflict；而 postgres 的数据卷也必须一起丢
#    （initdb-task.sql 只在**首次**初始化时跑，卷留着就永远补建不出 task_rag_db）。
docker compose -f "$COMPOSE" down -v >/dev/null 2>&1
for c in taskprobe-postgres taskprobe-redis taskprobe-kafka; do
  if docker ps -a --format '{{.Names}}' | grep -qx "$c"; then
    docker stop "$c" >/dev/null 2>&1
    docker rm "$c" >/dev/null 2>&1
    echo "  ✓ 移除同名遗留容器 $c"
  fi
done

docker compose -f "$COMPOSE" up -d
for i in $(seq 1 60); do
  st=$(docker inspect -f '{{.State.Health.Status}}' taskprobe-kafka 2>/dev/null)
  [ "$st" = "healthy" ] && { echo "  ✓ Kafka healthy（${i} 次轮询）"; break; }
  sleep 2
done
docker compose -f "$COMPOSE" ps --format '  {{.Name}}\t{{.Status}}'

# ═══════════════════════════════════════════════════════════════════════
say "4. 重置状态（② 重建 Kafka 容器 + ③ 移走 goka 本地缓存 + 清 Redis/PG）"
docker compose -f "$COMPOSE" up -d --force-recreate kafka >/dev/null
for i in $(seq 1 60); do
  st=$(docker inspect -f '{{.State.Health.Status}}' taskprobe-kafka 2>/dev/null)
  [ "$st" = "healthy" ] && break
  sleep 2
done
echo "  ✓ Kafka 容器已重建（⇒ 无 topic、无已提交位点、无 consumer group）"

STAMP=$(date +%s)
for g in "${GOKA_GROUPS[@]}"; do
  d="$GOKA_CACHE_DIR/$g"
  if [ -d "$d" ]; then
    # ⚠️ 用 mv 不用 rm：/private/tmp/goka 下可能有别的会话在用的 group
    mv "$d" "$GOKA_CACHE_DIR/${g}.stale.${STAMP}"
    echo "  ✓ 移走 goka 本地缓存 $g"
  fi
done

redis-cli -p "$REDIS_PORT" -n 0 FLUSHDB >/dev/null
redis-cli -p "$REDIS_PORT" -n 1 FLUSHDB >/dev/null
for db in task_go_db task_rag_db; do
  docker exec taskprobe-postgres psql -U admin -d "$db" -c \
    "TRUNCATE task_progress, user_like_count;" >/dev/null 2>&1
done
echo "  ✓ Redis db0/db1 已清、两张 PG 表已 TRUNCATE"

for t in "${GO_TOPICS[@]}" "${RAG_TOPICS[@]}"; do
  kafka kafka-topics.sh --bootstrap-server "$KAFKA_BOOT" --create --topic "$t" \
      --partitions 1 --replication-factor 1 >/dev/null 2>&1
done
echo "  ✓ 10 个 topic 已建"

# ═══════════════════════════════════════════════════════════════════════
say "5. 停掉可能残留的旧进程"
pkill -f 'go-task-rpc -f .*task-rpc.yaml' 2>/dev/null
pkill -f 'com.rag.task.task.rpc.TaskRpcApplication' 2>/dev/null
sleep 2
pkill -9 -f 'go-task-rpc -f .*task-rpc.yaml' 2>/dev/null
pkill -9 -f 'com.rag.task.task.rpc.TaskRpcApplication' 2>/dev/null
sleep 1
echo "  剩余：$(pgrep -f 'go-task-rpc|TaskRpcApplication' | wc -l | tr -d ' ')"

# ═══════════════════════════════════════════════════════════════════════
say "6. 起 Go 真源 task-rpc (:19005)"
( cd "$WORK" && nohup "$WORK/bin/go-task-rpc" \
      -f "$HERE/conf/go/task-rpc.yaml" > "$WORK/logs/go-task-rpc.log" 2>&1 & )
echo "  ✓ 已起"

say "7. 起 RAG task-rpc (:59005)"
POSTGRES_HOST=127.0.0.1 POSTGRES_PORT="$PG_PORT" POSTGRES_DB=task_rag_db \
POSTGRES_USER=admin POSTGRES_PASSWORD=difftest \
REDIS_HOST=127.0.0.1 REDIS_PORT="$REDIS_PORT" \
KAFKA_BROKERS=127.0.0.1:"$KAFKA_PORT" \
NACOS_ADDR=127.0.0.1:1 OTEL_EXPORTER_OTLP_ENDPOINT=127.0.0.1:1 \
nohup java -cp "$RAG_CP" com.rag.task.task.rpc.TaskRpcApplication \
    --grpc.server.port=59005 \
    --spring.cloud.nacos.discovery.register-enabled=false \
    --spring.data.redis.database=1 \
    --task.kafka.in-topic=rag.task.logs.raw \
    --task.kafka.raw-user-topic=rag.task.logs.raw.User \
    --task.kafka.raw-article-topic=rag.task.logs.raw.Article \
    --task.kafka.out-user-topic=rag.task.logs.filtered.User \
    --task.kafka.out-article-topic=rag.task.logs.filtered.Article \
    --task.kafka.group-kafka-raw=rag.rpc.consumer31 \
    --task.kafka.group-goka=rag.rpc.consumer41 \
    --task.kafka.group-goka-user=rag.rpc.consumer61 \
    --task.kafka.group-goka-article=rag.rpc.consumer71 \
    --task.kafka.group-filter-user=rag.rpc.consumer81 \
    --task.kafka.group-kafka-filter=rag.rpc.consumer101 \
    > "$WORK/logs/rag-task-rpc.log" 2>&1 &
echo "  ✓ 已起"

say "8. 等两侧消费者认领分区"
# ⚠️ 等待条件必须包含 **kq 的 consumer group**，不能只看 goka：
#   · Go 侧 6 个 group 里有 3 个是 goka（有 "setup generation" 那行日志）、
#     3 个是 kq（**没有**那行日志 ⇒ 只等 goka 会漏）；
#   · kq 的 Offset 是**硬编码 "latest"**（initUserFilter.go:47 / initArticleFilter.go:40）
#     ⇒ 它在 join group 时把起点定在**当时**的队尾 ⇒ 若消息灌得太早，
#     它会从队尾之后开始 ⇒ **一条都收不到**（实测：Go 侧 like:total 恒为 0，
#     而 goka 三段与 RAG 侧全部正常 —— 极具迷惑性）。
# ⇒ 必须等两侧 12 个 group **全部出现在 consumer-groups 列表里**，再留 grace period。
GO_GROUPS=(task.rpc.consumer31 task.rpc.consumer41 task.rpc.consumer61
           task.rpc.consumer71 task.rpc.consumer81 task.rpc.consumer101)
RAG_GROUPS=(rag.rpc.consumer31 rag.rpc.consumer41 rag.rpc.consumer61
            rag.rpc.consumer71 rag.rpc.consumer81 rag.rpc.consumer101)
for i in $(seq 1 60); do
  listing=$(kafka kafka-consumer-groups.sh --bootstrap-server "$KAFKA_BOOT" --list 2>/dev/null | grep -v WARN)
  missing=""
  for g in "${GO_GROUPS[@]}" "${RAG_GROUPS[@]}"; do
    echo "$listing" | grep -qx "$g" || missing="$missing $g"
  done
  go_err=$(grep -c 'error occurred\|local cache' "$WORK/logs/go-task-rpc.log" 2>/dev/null | head -1); go_err=${go_err:-0}
  echo "  [$i] 缺失 group:${missing:- 无}  go_errors=$go_err"
  if [ -z "$missing" ] && [ "$go_err" -eq 0 ]; then
    sleep 3      # grace period：让 kq 完成 offset 解析并把起点定在空队尾
    echo "  ✓ 两侧 12 个 group 全部就位"
    break
  fi
  sleep 2
done

# ═══════════════════════════════════════════════════════════════════════
say "9. 灌同一批 raw 日志（LogEvent JSON，Go initGoKa.go:19-27 的字段）"
cat > "$WORK/raw-batch.jsonl" <<'EOF'
{"ts":"2026-10-23T15:00:01.000Z","service":"like-service","event_id":"b01","user_id":"5001","trace_id":"tb1","article_id":"9001","msg":"like"}
{"ts":"2026-10-23T15:00:02.000Z","service":"like-service","event_id":"b02","user_id":"5001","trace_id":"tb2","article_id":"9001","msg":"like"}
{"ts":"2026-10-23T15:00:03.000Z","service":"like-service","event_id":"b03","user_id":"5001","trace_id":"tb3","article_id":"9001","msg":"like"}
{"ts":"2026-10-23T15:00:04.000Z","service":"like-service","event_id":"b04","user_id":"5001","trace_id":"tb4","article_id":"9001","msg":"like"}
{"ts":"2026-10-23T15:00:05.000Z","service":"like-service","event_id":"b05","user_id":"5001","trace_id":"tb5","article_id":"9001","msg":"like"}
{"ts":"2026-10-23T15:00:06.000Z","service":"like-service","event_id":"b06","user_id":"5002","trace_id":"tb6","article_id":"9002","msg":"like"}
{"ts":"2026-10-23T15:00:07.000Z","service":"like-service","event_id":"b07","user_id":"5002","trace_id":"tb7","article_id":"9002","msg":"like"}
EOF
echo "  批次：user 5001 × 5（恰好达到 target=5 ⇒ 完成分支）、user 5002 × 2（doing 分支）"
for t in task.logs.raw rag.task.logs.raw; do
  docker exec -i taskprobe-kafka /opt/kafka/bin/kafka-console-producer.sh \
      --bootstrap-server "$KAFKA_BOOT" --topic "$t" < "$WORK/raw-batch.jsonl"
  echo "  ✓ 已灌入 $t"
done

say "10. 等 sink 的 1 秒 flush 落库"
for i in $(seq 1 12); do
  sleep 2
  n=$(redis-cli -p "$REDIS_PORT" -n 1 --scan | grep -c '^like:total:' | head -1); n=${n:-0}
  m=$(redis-cli -p "$REDIS_PORT" -n 0 --scan | grep -c '^like:total:' | head -1); m=${m:-0}
  echo "  [$i] like:total 数量 Go=$m RAG=$n"
  [ "$m" -ge 2 ] && [ "$n" -ge 2 ] && { echo "  ✓ 两侧都已有 like:total"; break; }
done

say "11. 各 topic 消息数（应逐个相等）"
for t in "${GO_TOPICS[@]}" "${RAG_TOPICS[@]}"; do
  printf '  %-32s ' "$t"
  kafka kafka-get-offsets.sh --bootstrap-server "$KAFKA_BOOT" --topic "$t" 2>/dev/null \
    | grep -v WARN | tr '\n' ' '
  echo
done

say "12. 两侧 Redis 异常计数（都应为 0）"
c=$(grep -c "EXEC without MULTI" "$WORK/logs/rag-task-rpc.log" 2>/dev/null | head -1)
echo "  RAG ERR EXEC without MULTI : ${c:-0}"
g=$(grep -c 'error occurred\|local cache' "$WORK/logs/go-task-rpc.log" 2>/dev/null | head -1)
echo "  Go  goka 错误               : ${g:-0}"

# ═══════════════════════════════════════════════════════════════════════
say "13. 比对最终态"
DIFFTEST_REDIS_PORT="$REDIS_PORT" \
DIFFTEST_PG_CONTAINER=taskprobe-postgres \
DIFFTEST_KAFKA_CONTAINER=taskprobe-kafka \
DIFFTEST_KAFKA_BOOT="$KAFKA_BOOT" \
  python3 "$HERE/parity-task-kafka.py"
RC=$?

if [ "$KEEP_UP" = "1" ]; then
  echo
  echo "KEEP_UP=1 ⇒ 服务与容器保留。日志：$WORK/logs/"
else
  say "14. 收摊"
  pkill -f 'go-task-rpc -f .*task-rpc.yaml' 2>/dev/null
  pkill -f 'com.rag.task.task.rpc.TaskRpcApplication' 2>/dev/null
  sleep 1
  docker compose -f "$COMPOSE" down >/dev/null 2>&1
  echo "  ✓ 服务与容器已停（日志保留在 $WORK/logs/）"
fi

exit $RC
