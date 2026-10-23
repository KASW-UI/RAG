#!/usr/bin/env bash
# ═════════════════════════════════════════════════════════════════════════
# task 域 Go ↔ RAG 双侧活体对拍（HTTP wire 层）—— 一键编排
#
#   同时跑：Go 真源 task-rpc + task-api ／ RAG task-rpc + task-api
#   对同一串请求矩阵逐条比对（状态码 / Content-Type / nosniff / 响应体）
#
# 用法：
#   ./tools/difftest/run-task-http-parity.sh                # 完整跑一遍
#   KEEP_UP=1 ./tools/difftest/run-task-http-parity.sh       # 跑完不拆服务与容器
#   GO_REPO=/path/to/Sea-RideTheWind_golang ./…              # 自定义真源路径
#
# ⚠️ 与 run-task-kafka-e2e.sh 的分工：
#    · 本脚本对拍 **HTTP wire 层**（api → rpc 的读路径），灌消息那条链在 Kafka 脚本里。
#    · 两者的基建与踩坑处理**同源**（同一份 docker-compose.task-kafka.yml、同一组 goka 缓存处理）。
#
# ⚠️ 本脚本把「这一轮实测踩过的坑」固化在注释里了，别随手改：
#   ① 容器内 Kafka CLI 必须用 **INTERNAL 监听器 localhost:19092**
#   ② Go 侧要 **CGO_ENABLED=0** 才能编
#   ③ RAG 侧**不是 fat jar**，要用 `java -cp <classes>:<deps>`
#   ④ 探针必须**拒绝 000 / 404**（把「打到别人服务」误判成就绪，见 README §4.1）
#   ⑤ goka 的本地 LevelDB 缓存必须移走，否则读的是上一轮的旧计数
# ═════════════════════════════════════════════════════════════════════════
set -u

HERE=$(cd "$(dirname "$0")" && pwd)
RAG_REPO=${RAG_REPO:-$(cd "$HERE/../.." && pwd)}
WORK=${WORK:-${TMPDIR:-/tmp}/task-http-parity}
KEEP_UP=${KEEP_UP:-0}

# ⚠️ Go 真源路径的推导**不能**假设「与 RAG 同级」：
#    worktree 的布局是 `<RAG.worktrees>/task`，与主仓 `<RAG>` 不同级。
find_go_repo() {
  local cands=()
  [ -n "${GO_REPO:-}" ] && cands+=("$GO_REPO")
  cands+=("$RAG_REPO/../Sea-RideTheWind_golang")
  cands+=("$RAG_REPO/../../Sea-RideTheWind_golang")
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
KAFKA_BOOT=localhost:19092
PG_PORT=55433
REDIS_PORT=56380
KAFKA_PORT=59992
GO_API_PORT=18886
RAG_API_PORT=58886
GO_RPC_PORT=19005
RAG_RPC_PORT=59005

GO_TOPICS=(task.logs.raw task.logs.raw.User task.logs.raw.Article
           task.logs.filtered.User task.logs.filtered.Article)
RAG_TOPICS=(rag.task.logs.raw rag.task.logs.raw.User rag.task.logs.raw.Article
            rag.task.logs.filtered.User rag.task.logs.filtered.Article)
GOKA_GROUPS=(task.rpc.consumer61 task.rpc.consumer71)
GOKA_CACHE_DIR=${GOKA_CACHE_DIR:-/private/tmp/goka/processor}

kafka() { docker exec taskprobe-kafka /opt/kafka/bin/"$@"; }
say() { printf '\n\033[1m==== %s ====\033[0m\n' "$*"; }

# ═════════════════════════════════════════════════════════════════════════
say "0. 前置检查"
for c in docker go mvn java curl; do
  command -v "$c" >/dev/null 2>&1 || { echo "  ✗ 缺命令：$c"; exit 1; }
done
GO_REPO=$(GO_REPO="$GO_REPO_CANDIDATE" find_go_repo) \
  || { echo "  ✗ 找不到 Go 真源（可用 GO_REPO=... 指定）"; exit 1; }
echo "  RAG_REPO = $RAG_REPO"
echo "  GO_REPO  = $GO_REPO"
echo "  WORK     = $WORK"
mkdir -p "$WORK/bin" "$WORK/logs"

say "1. 编 Go 真源二进制（② CGO_ENABLED=0）"
( cd "$GO_REPO" && CGO_ENABLED=0 go build -o "$WORK/bin/go-task-rpc" ./service/task/rpc ) \
  || { echo "  ✗ Go task-rpc 编译失败"; exit 1; }
( cd "$GO_REPO" && CGO_ENABLED=0 go build -o "$WORK/bin/go-task-api" ./service/task/api ) \
  || { echo "  ✗ Go task-api 编译失败"; exit 1; }
echo "  ✓ go-task-rpc / go-task-api"

say "2. 编 RAG 侧（③ 不是 fat jar ⇒ classes + 依赖 classpath）"
for m in task-rpc task-api; do
  ( cd "$RAG_REPO" && mvn -o -q -pl "service/task/$m" -am compile -DskipTests ) \
    || { echo "  ✗ RAG $m 编译失败"; exit 1; }
  ( cd "$RAG_REPO" && mvn -o -q -pl "service/task/$m" dependency:build-classpath \
      -Dmdep.outputFile="$WORK/$m-cp.txt" -DincludeScope=runtime ) \
    || { echo "  ✗ $m classpath 生成失败"; exit 1; }
done
RPC_CP="$RAG_REPO/service/task/task-rpc/target/classes:$(cat "$WORK/task-rpc-cp.txt")"
API_CP="$RAG_REPO/service/task/task-api/target/classes:$(cat "$WORK/task-api-cp.txt")"
echo "  ✓ classpath 就绪"

say "3. 起基建（postgres + redis + kafka）"
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

say "4. 建 topic + 清状态"
for t in "${GO_TOPICS[@]}" "${RAG_TOPICS[@]}"; do
  kafka kafka-topics.sh --bootstrap-server "$KAFKA_BOOT" --create --topic "$t" \
      --partitions 1 --replication-factor 1 >/dev/null 2>&1
done
echo "  ✓ 10 个 topic 已建"
STAMP=$(date +%s)
for g in "${GOKA_GROUPS[@]}"; do
  d="$GOKA_CACHE_DIR/$g"
  if [ -d "$d" ]; then
    # ⚠️ 用 mv 不用 rm：/private/tmp/goka 下可能有别的会话在用的 group
    mv "$d" "$GOKA_CACHE_DIR/${g}.stale.${STAMP}"
    echo "  ✓ 移走 goka 本地缓存 ${g}（⑤）"
  fi
done
redis-cli -p "$REDIS_PORT" -n 0 FLUSHDB >/dev/null
redis-cli -p "$REDIS_PORT" -n 1 FLUSHDB >/dev/null

say "5. 停掉可能残留的旧进程"
pkill -f 'go-task-rpc -f .*task-rpc.yaml' 2>/dev/null
pkill -f 'go-task-api -f .*task-api.yaml' 2>/dev/null
pkill -f 'com.rag.task.task.rpc.TaskRpcApplication' 2>/dev/null
pkill -f 'com.rag.task.task.api.TaskApiApplication' 2>/dev/null
sleep 2
echo "  剩余：$(pgrep -f 'go-task-rpc|go-task-api|TaskRpcApplication|TaskApiApplication' | wc -l | tr -d ' ')"

say "6. 起 Go 真源 task-rpc (:$GO_RPC_PORT) + task-api (:$GO_API_PORT)"
( cd "$WORK" && nohup "$WORK/bin/go-task-rpc" \
      -f "$HERE/conf/go/task-rpc.yaml" > "$WORK/logs/go-task-rpc.log" 2>&1 & )
sleep 3
( cd "$WORK" && nohup "$WORK/bin/go-task-api" \
      -f "$HERE/conf/go/task-api.yaml" > "$WORK/logs/go-task-api.log" 2>&1 & )
echo "  ✓ 已起"

say "7. 起 RAG task-rpc (:$RAG_RPC_PORT) + task-api (:$RAG_API_PORT)"
# ⚠️⚠️ Redis **两侧必须用同一个 db（db0）**，与 Go 侧的 `LikeRedis.DB: 0` 一致
#    （tools/difftest/conf/go/task-rpc.yaml 里写死的）。
#    为什么：HTTP 矩阵要测的正是「**同一段 Redis 值**两侧解析结果是否一致」——
#    矩阵脚本用 `redis-cli`（默认 db0）手工 HSET 缓存值，Go 读 db0，
#    若 RAG 读 db1 就变成「RAG 读了个空库」⇒ 缓存路径全线回落 0，
#    **看起来像漂移，其实是探针配错**（探针自欺，README §4.1 同款）。
#    ⚠️ Kafka E2E 脚本用 db1 是**另一回事**：那边两侧 rpc 同时往 Redis 写，
#    必须隔离；这边只有 api→rpc 的读路径，且要「读同一份缓存值」，所以必须共用。
POSTGRES_HOST=127.0.0.1 POSTGRES_PORT="$PG_PORT" POSTGRES_DB=task_rag_db \
POSTGRES_USER=admin POSTGRES_PASSWORD=difftest \
REDIS_HOST=127.0.0.1 REDIS_PORT="$REDIS_PORT" \
KAFKA_BROKERS=127.0.0.1:"$KAFKA_PORT" \
NACOS_ADDR=127.0.0.1:1 OTEL_EXPORTER_OTLP_ENDPOINT=127.0.0.1:1 \
nohup java -cp "$RPC_CP" com.rag.task.task.rpc.TaskRpcApplication \
    --grpc.server.port="$RAG_RPC_PORT" \
    --spring.cloud.nacos.discovery.register-enabled=false \
    --spring.data.redis.database=0 \
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
sleep 5
NACOS_ADDR=127.0.0.1:1 OTEL_EXPORTER_OTLP_ENDPOINT=127.0.0.1:1 \
TASK_RPC_ADDRESS="static://127.0.0.1:$RAG_RPC_PORT" \
nohup java -cp "$API_CP" com.rag.task.task.api.TaskApiApplication \
    --server.port="$RAG_API_PORT" \
    --grpc.client.task-rpc.address="static://127.0.0.1:$RAG_RPC_PORT" \
    --spring.cloud.nacos.discovery.register-enabled=false \
    --management.otlp.tracing.export.enabled=false \
    > "$WORK/logs/rag-task-api.log" 2>&1 &
echo "  ✓ 已起"

say "8. 等两侧 api 就绪（④ 必须拒绝 000 / 404）"
# ⚠️ 判据是「**真实业务请求**返回 200」，不是「端口能连上」——
#    连上端口只能证明进程在，404 同样能连上（打到别人服务 / 路由没注册）。
probe_ready() {
  local base=$1 name=$2 log=$3
  for i in $(seq 1 60); do
    code=$(curl -s -o /dev/null -w '%{http_code}' -m 5 \
             -H 'Content-Type: application/json' -d '{"id":1001}' \
             "$base/taskcenter/v1/task/get" 2>/dev/null)
    if [ "$code" = "200" ]; then
      echo "  ✓ $name 就绪（${i} 次轮询）"
      return 0
    fi
    if [ "$code" = "000" ] || [ "$code" = "404" ]; then
      echo "  [$i] $name code=$code —— 继续等（首次启动要拉起 goka consumer）"
    else
      echo "  [$i] $name code=$code"
    fi
    sleep 2
  done
  echo "  ✗ $name 未就绪；日志尾部："
  tail -20 "$log" 2>/dev/null | sed 's/^/     /'
  return 1
}
probe_ready "http://127.0.0.1:$GO_API_PORT" "Go api" "$WORK/logs/go-task-api.log" || exit 1
probe_ready "http://127.0.0.1:$RAG_API_PORT" "RAG api" "$WORK/logs/rag-task-api.log" || exit 1

say "9. 跑请求矩阵的双侧对拍"
REPORT="$WORK/logs/parity-task-http.txt"
python3 "$HERE/parity-task.py" 2>&1 | tee "$REPORT"
RC=${PIPESTATUS[0]}
echo "  ✓ 报告已落盘：$REPORT"
grep -E '^总计 ' "$REPORT" || true

say "10. 收摊"
if [ "$KEEP_UP" = "1" ]; then
  echo "KEEP_UP=1 ⇒ 服务与容器保留。日志：$WORK/logs/"
else
  pkill -f 'go-task-rpc -f .*task-rpc.yaml' 2>/dev/null
  pkill -f 'go-task-api -f .*task-api.yaml' 2>/dev/null
  pkill -f 'com.rag.task.task.rpc.TaskRpcApplication' 2>/dev/null
  pkill -f 'com.rag.task.task.api.TaskApiApplication' 2>/dev/null
  sleep 1
  docker compose -f "$COMPOSE" down >/dev/null 2>&1
  echo "  ✓ 服务与容器已停（日志保留在 $WORK/logs/）"
fi

exit $RC
