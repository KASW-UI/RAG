#!/bin/bash
# comment 域 Go ↔ RAG 双侧活体对拍编排。
#
# 用法：bash tools/difftest/comment/run_diff.sh
#
# 前置（见同目录 README.md「怎么跑」）：
#   1. docker compose -f docker-compose.yml -f docker-compose.kafka.yml up -d
#   2. Go 侧产物放进 bin/：go-comment-rpc / go-comment-api（CGO_ENABLED=0 go build）
#   3. RAG 侧产物放进 bin/：rag-comment-rpc.jar
#      + api-cp.txt（mvn dependency:build-classpath -Dmdep.includeScope=runtime）
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
cd "$HERE" || exit 1
mkdir -p bin logs

WT="${RAG_COMMENT_WT:-/Users/pr/Documents/java&golang/RAG.worktrees/comment}"
PG="docker exec cdifftest-postgres"
REDIS="docker exec cdifftest-redis redis-cli"

for f in bin/go-comment-rpc bin/go-comment-api bin/rag-comment-rpc.jar api-cp.txt; do
  if [ ! -e "$f" ]; then
    echo "❌ 缺前置产物：$f（见同目录 README.md「怎么跑」）"; exit 1
  fi
done

# 等端口释放 / 等端口就绪（进程被 pkill 后优雅退出会花几秒，直接起会撞端口）
wait_port_free() {
  local port="$1" i
  for i in $(seq 1 40); do
    lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1 || return 0
    sleep 1
  done
  echo "  ⚠️ 端口 ${port} 60s 内未释放"; return 1
}
wait_port_up() {
  local port="$1" i
  for i in $(seq 1 90); do
    lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1 && return 0
    sleep 1
  done
  echo "  ❌ 端口 ${port} 90s 内未监听"; return 1
}

echo "== [0/5] 重置两侧的 Kafka topic + 消费组（保证本轮从干净位点开始）=="
# ⚠️ 必须「先删后建」，不能只删：
#   消费者在 topic 不存在时 subscribe，Kafka 4.x 的 rebalance 会走指数退避
#   （group.initial.rebalance.delay.ms=3000 起，上限 group.rebalance.backoff.max.ms=30000），
#   实测 topic 缺失 13s ⇒ 消费者组 19s 后才 join 成功、才拿到分区 ⇒ create→可见 迟到 19s。
#   Go 侧用 kafka-go Reader（不走组协调）⇒ 同样的条件下是毫秒级。
#   ⇒ 这不是 comment 域语义差异，是 Kafka 客户端实现差异；harness 必须把 topic 预先建好，两侧才可比。
for t in comment-audit-topic-go comment-audit-topic-rag; do
  docker exec cdifftest-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:19092 \
      --delete --topic "$t" >/dev/null 2>&1
done
sleep 3
for t in comment-audit-topic-go comment-audit-topic-rag; do
  docker exec cdifftest-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:19092 \
      --create --topic "$t" --partitions 1 --replication-factor 1 >/dev/null 2>&1
done
# 删消费组 committed offset：topic 重建后 group 的旧 offset 可能越界/残留
for g in comment-audit-group-go comment-audit-group-rag; do
  docker exec cdifftest-kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:19092 \
      --delete --group "$g" >/dev/null 2>&1
done
sleep 2

# ⚠️⚠️ **前置健康检查**：基础设施挂掉时**必须立刻失败**，不能跑出一堆假差异。
#   实测踩过：Kafka 容器被 OOM kill（exit 137）⇒ 两侧审计管道全断 ⇒
#   「create 返回 200 但库里没有」+「RAG 侧 2s deadline exceeded」⇒ **全是基础设施故障的假象**，
#   而 diff 报告会把它们当成语义差异列出来 ⇒ 必须先验基础设施。
echo "  —— 前置健康检查 ——"
infra_ok=1
for c in cdifftest-postgres cdifftest-redis cdifftest-kafka; do
  st=$(docker inspect -f '{{.State.Status}}' "$c" 2>/dev/null || echo "missing")
  if [ "$st" != "running" ]; then
    echo "  ❌ 容器 $c 状态 = $st（不是 running）"; infra_ok=0
  fi
done
$PG psql -t -A -U admin -d go_comment -c "select 1" >/dev/null 2>&1 || { echo "  ❌ PG 不可达"; infra_ok=0; }
$REDIS -n 0 PING 2>/dev/null | grep -q PONG || { echo "  ❌ Redis 不可达"; infra_ok=0; }
TOPICS=$(docker exec cdifftest-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:19092 --list 2>/dev/null | grep -c "comment-audit-topic")
[ "$TOPICS" -ge 2 ] || { echo "  ❌ Kafka 里 comment-audit-topic* 只有 $TOPICS 个（应为 2）"; infra_ok=0; }
if [ "$infra_ok" -ne 1 ]; then
  echo "❌ 基础设施不健康 ⇒ **中止**，不跑矩阵（否则产出的全是假差异）"; exit 4
fi
echo "  ✅ PG / Redis / Kafka 均可用，comment-audit-topic* 已就位"

echo "== [1/5] 重置两侧数据库 =="
$PG psql -q -U admin -d go_comment -c "TRUNCATE comment_content, comment_index, comment_like, comment_domain_fact_outbox, comment_fact_stream, subject RESTART IDENTITY CASCADE;" >/dev/null 2>&1
$PG psql -q -U admin -d rag_comment -c "TRUNCATE comment_content, comment_index, comment_like, comment_domain_fact_outbox, comment_fact_stream, report_record, subject RESTART IDENTITY CASCADE;" >/dev/null 2>&1
# 预置 subject 行：让 create 走「subject 已存在 ⇒ 不调 ArticleRpc 回落」这条路径。
# 两侧预置**完全相同**的一行 ⇒ 对称；否则 create 两侧都会去调那个死掉的 article-rpc ⇒ 变成超时路径。
$PG psql -q -U admin -d go_comment -c "INSERT INTO subject (target_type, target_id, total_count, root_count, state, attribute, owner_id) VALUES ('article','a-diff',0,0,0,0,2002);" >/dev/null
$PG psql -q -U admin -d rag_comment -c "INSERT INTO subject (target_type, target_id, total_count, root_count, state, attribute, owner_id) VALUES ('article','a-diff',0,0,0,0,2002);" >/dev/null
echo "  go_comment subject: $($PG psql -t -A -U admin -d go_comment -c "select count(*) from subject;")  rag_comment subject: $($PG psql -t -A -U admin -d rag_comment -c "select count(*) from subject;")"

echo "== [2/5] 清空两侧 Redis（Go 用 db0 / RAG 用 db1 —— key 格式两侧逐字一致，必须分库）=="
$REDIS -n 0 FLUSHDB >/dev/null
$REDIS -n 1 FLUSHDB >/dev/null
echo "  db0=$(  $REDIS -n 0 DBSIZE) db1=$($REDIS -n 1 DBSIZE)"

echo "== [3/5] 重启两侧（Go: Offset=first；RAG: Redis db=1 + 隔离 Kafka group/topic）=="
pkill -f "go-comment-rpc" 2>/dev/null
wait_port_free 19001
(nohup ./bin/go-comment-rpc -f "$HERE/conf/go-comment-rpc.yaml" > logs/go-comment-rpc.log 2>&1 & echo "  go-rpc pid=$!")
wait_port_up 19001 || exit 3
pkill -f "go-comment-api" 2>/dev/null
wait_port_free 18888
(nohup ./bin/go-comment-api -f "$HERE/conf/go-comment-api.yaml" > logs/go-comment-api.log 2>&1 & echo "  go-api pid=$!")
wait_port_up 18888 || { echo "  go-api 启动失败，日志尾："; tail -20 logs/go-comment-api.log; exit 3; }
pkill -f "rag-comment-rpc.jar" 2>/dev/null
pkill -f "com.rag.comment.comment.api.CommentApiApplication" 2>/dev/null
wait_port_free 59001
wait_port_free 58888
(nohup env \
  POSTGRES_HOST=127.0.0.1 POSTGRES_PORT=55440 POSTGRES_DB=rag_comment POSTGRES_USER=admin POSTGRES_PASSWORD=difftest \
  REDIS_HOST=127.0.0.1 REDIS_PORT=56395 \
  KAFKA_BROKERS=127.0.0.1:59993 \
  SPRING_CLOUD_NACOS_DISCOVERY_REGISTER_ENABLED=false \
  java -jar bin/rag-comment-rpc.jar \
    --grpc.server.port=59001 \
    --grpc.client.message-rpc.address=static://127.0.0.1:19999 \
    --grpc.client.article-rpc.address=static://127.0.0.1:19999 \
    --comment.kafka.audit-topic=comment-audit-topic-rag \
    --spring.kafka.consumer.group-id=comment-audit-group-rag \
    --spring.kafka.consumer.auto-offset-reset=earliest \
    --spring.data.redis.database=1 \
    > logs/rag-comment-rpc.log 2>&1 & echo "  rag-rpc pid=$!")
wait_port_up 59001 || { echo "  rag-rpc 启动失败，日志尾："; tail -20 logs/rag-comment-rpc.log; exit 3; }
(nohup env \
  COMMENT_API_PORT=58888 \
  SPRING_CLOUD_NACOS_DISCOVERY_REGISTER_ENABLED=false \
  java -cp "$WT/service/comment/comment-api/target/classes:$(cat api-cp.txt)" \
    com.rag.comment.comment.api.CommentApiApplication \
    --grpc.client.comment-rpc.address=static://127.0.0.1:59001 \
    --spring.data.redis.database=1 \
    > logs/rag-comment-api.log 2>&1 & echo "  rag-api pid=$!")
wait_port_up 58888 || { echo "  rag-api 启动失败，日志尾："; tail -20 logs/rag-comment-api.log; exit 3; }

echo "== [4/5] 就绪探针（必须显式拒绝 000/404 —— 见 tools/difftest/README §4.1）=="
probe() {
  local name="$1" url="$2"
  local code
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 -X POST "$url" \
        -H 'Content-Type: application/json' -d '{}' 2>/dev/null) || code=""
  [ -z "$code" ] && code="000"
  if [ "$code" = "000" ] || [ "$code" = "404" ]; then
    echo "  ❌ ${name} 未就绪（code=${code}）"; return 1
  fi
  echo "  ✅ ${name} 就绪（code=${code}）"; return 0
}
probe "go-comment-api  " "http://127.0.0.1:18888/comment/v1/comment/list" || exit 2
probe "rag-comment-api " "http://127.0.0.1:58888/comment/v1/comment/list" || exit 2

echo "== [5/5] 跑请求矩阵 =="
python3 diff_comment.py
rc=$?
echo ""
echo "== 对拍后两侧库内状态（人工核对用）=="
echo "-- go_comment:"
$PG psql -t -A -U admin -d go_comment -c "select 'comment_index', count(*) from comment_index union all select 'comment_content', count(*) from comment_content union all select 'subject', count(*) from subject union all select 'outbox', count(*) from comment_domain_fact_outbox;"
echo "-- rag_comment:"
$PG psql -t -A -U admin -d rag_comment -c "select 'comment_index', count(*) from comment_index union all select 'comment_content', count(*) from comment_content union all select 'subject', count(*) from subject union all select 'outbox', count(*) from comment_domain_fact_outbox union all select 'report_record', count(*) from report_record;"
exit $rc
