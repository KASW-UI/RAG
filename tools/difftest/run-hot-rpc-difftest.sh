#!/usr/bin/env bash
# ═════════════════════════════════════════════════════════════════════════════
#  run-hot-rpc-difftest.sh —— hot-rpc 组件的**双侧活体端到端对拍**（D1 / U7）
# ═════════════════════════════════════════════════════════════════════════════
#
# 【它回答什么】
#   · D1：Go hot-rpc 与 RAG hot-rpc，对**同一批 Kafka 事件**、**各自的 Redis**，
#         最终产出的 gRPC 响应是否**逐字节一致**。
#   · U7：TopK=100 被 hot-rpc 如何解释（是否 clamp 到 100）？items 是否按热度降序？
#
# 【为什么必须「各起各的 Redis」】
#   Redis key 两侧都硬编码（hot:articles / hot:articles:p{i} / hot:articles:shadow /
#   hot:merge:lock），共用一个 Redis ⇒ 两侧互相覆盖对方的快照 ⇒ 比出来的「相同」毫无意义。
#
# 【为什么两侧都必须「重启后再投事件」】
#   ① HeavyKeeper 的 sketch 是**进程内内存态** ⇒ 重投事件前必须清空，否则旧事件被计两次；
#   ② 两侧 consumer group 不同 ⇒ 消费进度天然不对称 ⇒ 必须用栅栏等两侧都合并完再比；
#   ③ 两侧都要「先起来、再投事件」：Go 是 sarama.OffsetNewest、RAG 是 latest。
#
# 【⚠️ 踩过的坑 ①：不要用「编译产物可能是旧的」去启动进程】
#   2026-10-24 实测：曾用一份**编译期缺文件**的 classes 启动 RAG hot-rpc，
#   结果 JVM 里**根本没有 HotMergeScheduler / HotServiceGrpcImpl 这两个类**
#   （`jcmd <pid> GC.class_histogram` 可查）⇒ 合并器从未被装配、热榜恒空
#   ⇒ 一度被误判成「RAG 侧合并有漂移」。
#   ⇒ 本脚本启动前**强制重编**，并在启动后**断言关键类已被加载**。
#
# 【⚠️ 踩过的坑 ②：清 Redis 必须在旧进程**真死之后**】
#   旧 hot-rpc 的 30s 快照 ticker 会把内存里的 sketch **重新写回 Redis**。
#   若「先清 Redis、再杀进程」（或杀完没等它死就清），旧进程会在清空之后
#   再写一轮 ⇒ 新进程启动时 {@code loadInto} 回灌到**上一轮的分数** ⇒
#   **每个文章的热度恰好翻倍**（实测 60 → 120），而 Go 侧是 60。
#   ⇒ 症状极具迷惑性：条目集合、顺序、条数**全都对**，只有分数整体 ×2。
#   ⇒ 本脚本：**杀进程 → 等端口释放 → 清 Redis → 断言 DBSIZE==0 → 才启动**。
#
# 【纪律】
#   1. 绝不修改冻结的 Go 仓库（配置走副本 + -f）。
#   2. Go 不写任何文件进仓库（副本已把 Log.Mode 改成 console）。
#   3. 端口独占段 23xxx，绝不碰 8081/8889/49210/49900 等本机既有服务。
#
# 【用法】
#   bash tools/difftest/run-hot-rpc-difftest.sh          # 完整跑
#   bash tools/difftest/run-hot-rpc-difftest.sh --keep   # 跑完保留进程
# ═════════════════════════════════════════════════════════════════════════════
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
RUN_DIR="${RUN_DIR:-/tmp/hot-rpc-difftest}"
LOGS="$RUN_DIR/logs"
GO_REPO="${GO_REPO:-/Users/pr/Documents/java&golang/Sea-RideTheWind_golang}"
GO_CONF="${GO_CONF:-$HERE/conf/go/hot-rpc.yaml}"

GO_RPC_PORT=23983
RAG_RPC_PORT=23984
KAFKA_PORT=23902
GO_REDIS_PORT=23979
RAG_REDIS_PORT=23980
TOPIC=article-hot-events

KEEP=0
for a in "$@"; do
  case "$a" in
    --keep) KEEP=1 ;;
    *) echo "未知参数：${a}" >&2; exit 2 ;;
  esac
done

mkdir -p "$LOGS"
PIDS=()
cleanup() {
  if [[ $KEEP -eq 1 ]]; then
    echo "[run-hot-rpc-difftest.sh] --keep：保留进程（PID ${PIDS[*]:-}）"
    return
  fi
  echo "[run-hot-rpc-difftest.sh] 收摊…"
  for p in "${PIDS[@]:-}"; do kill "$p" 2>/dev/null || true; done
  wait 2>/dev/null || true
}
trap cleanup EXIT

port_busy() { lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1; }

wait_port_up() {  # port, name, tries
  local port=$1 name=$2 tries=${3:-60}
  for _ in $(seq 1 "$tries"); do
    if port_busy "$port"; then echo "  OK   ${name} 已监听 :${port}"; return 0; fi
    sleep 1
  done
  echo "  FAIL ${name} 未监听 :${port}"; return 1
}

wait_port_down() {  # port, name, tries
  local port=$1 name=$2 tries=${3:-30}
  for _ in $(seq 1 "$tries"); do
    if ! port_busy "$port"; then echo "  OK   ${name} 已退出 (:${port})"; return 0; fi
    sleep 1
  done
  echo "  FAIL ${name} 仍在监听 (:${port})"; return 1
}

echo "==> [1/7] 清场：停掉旧的 hot-rpc 进程"
pkill -f 'go-hot-rpc -f' 2>/dev/null || true
pkill -f 'com.rag.hot.hot.rpc.HotRpcApplication' 2>/dev/null || true
wait_port_down $GO_RPC_PORT  "go  hot-rpc" 30 || exit 1
wait_port_down $RAG_RPC_PORT "rag hot-rpc" 30 || exit 1

echo "==> [2/7] 清空两个 Redis（**必须等旧进程死后**，否则它会把旧 sketch 写回来 ⇒ 分数翻倍）"
docker exec hotrpcdifftest-redis-go  redis-cli FLUSHALL >/dev/null
docker exec hotrpcdifftest-redis-rag redis-cli FLUSHALL >/dev/null
for pair in "hotrpcdifftest-redis-go:$GO_REDIS_PORT" "hotrpcdifftest-redis-rag:$RAG_REDIS_PORT"; do
  cont="${pair%%:*}"; prt="${pair##*:}"
  size=$(docker exec "$cont" redis-cli DBSIZE 2>/dev/null | tr -d '\r')
  if [[ "$size" != "0" ]]; then
    echo "  ❌ ${cont}:6379 DBSIZE=${size}（应为 0）⇒ 有旧数据残留，停下"
    docker exec "$cont" redis-cli KEYS '*' | head -20
    exit 1
  fi
  echo "  ✓ ${cont}:${prt} 为空（DBSIZE=0）"
done

echo "==> [3/7] 重编 RAG hot-rpc（强制：避免用旧/残缺的 classes 启动）"
( cd "$ROOT" && mvn -o -q -pl service/hot/hot-rpc -am -DskipTests compile ) || exit 1
CP="$(cat "$RUN_DIR/rag-cp.txt")"
for d in service/hot/hot-rpc service/hot/hot-proto service/common; do
  [[ -d "$ROOT/$d/target/classes" ]] || { echo "  FAIL 缺 $ROOT/$d/target/classes"; exit 1; }
done

echo "==> [4/7] 起两侧 hot-rpc"
"$RUN_DIR/bin/go-hot-rpc" -f "$GO_CONF" >"$LOGS/go-hot-rpc.log" 2>&1 & PIDS+=($!)

(
  cd "$ROOT" && \
  OTEL_EXPORTER_OTLP_ENDPOINT=http://127.0.0.1:1 \
  SPRING_KAFKA_CONSUMER_GROUP_ID=rag-hot-group \
  REDIS_HOST=127.0.0.1 \
  REDIS_PORT=$RAG_REDIS_PORT \
  KAFKA_BROKERS=127.0.0.1:$KAFKA_PORT \
  NACOS_ADDR=127.0.0.1:1 \
  HOT_RPC_GRPC_PORT=$RAG_RPC_PORT \
  SPRING_CLOUD_NACOS_DISCOVERY_REGISTER_ENABLED=false \
  java -cp "$CP" com.rag.hot.hot.rpc.HotRpcApplication \
  >"$LOGS/rag-hot-rpc.log" 2>&1
) & PIDS+=($!)

wait_port_up $GO_RPC_PORT  "go  hot-rpc" 60  || exit 1
wait_port_up $RAG_RPC_PORT "rag hot-rpc" 120 || exit 1
sleep 5

echo "==> [5/7] 断言：RAG 进程里**关键类确实已加载**（'旧 classes' 坑的守门）"
RAG_PID=$(lsof -nP -iTCP:$RAG_RPC_PORT -sTCP:LISTEN -t | head -1)
HIST="$(jcmd "$RAG_PID" GC.class_histogram 2>/dev/null || true)"
for cls in HotMergeScheduler HotServiceGrpcImpl HotRedisSnapshotStore; do
  # ⚠️ 必须用 here-string 而不是 `echo "$HIST" | grep -q`：
  #    `grep -q` 命中即退出会让上游 echo 收到 SIGPIPE ⇒ 在 pipefail 下
  #    「找到类」被误判成「没找到类」（实测踩过）。
  if grep -q "com.rag.hot.hot.rpc.*${cls}" <<<"$HIST"; then
    echo "  ✓ ${cls} 已加载"
  else
    echo "  ❌ ${cls} **未加载** ⇒ 该 Bean 不会被装配 ⇒ 组件功能缺失（详见脚本头注释）"
    exit 1
  fi
done
# 同一坑的另一半：启动时回灌必须是 **0**（Redis 已被清空 ⇒ 有残留就说明清场没生效）
LOADED=$(grep -ac "loaded 0 articles" "$LOGS/rag-hot-rpc.log" || true)
echo "  RAG 启动时回灌 'loaded 0 articles' 次数 = ${LOADED}（>0 说明 Redis 干净）"

echo "==> [6/7] 生成事件并投进 Kafka"
python3 "$HERE/hot_rpc_events.py" --out-dir "$RUN_DIR/events" | sed 's/^/  /'
bash "$HERE/produce_hot_rpc_events.sh" "$RUN_DIR/events/events.tsv" | sed 's/^/  /'

echo "==> [7/7] 栅栏：等两侧都合并完（消费进度天然不对称 ⇒ 必须等）"
FENCE_RC=0
python3 "$HERE/hot_rpc_fence.py" \
  --go-redis "127.0.0.1:$GO_REDIS_PORT" --rag-redis "127.0.0.1:$RAG_REDIS_PORT" \
  --manifest "$RUN_DIR/events/manifest.json" --timeout 300 || FENCE_RC=$?

echo
echo "==> 逐字节对拍（gRPC 层，跨多组 TopK）"
# ⚠️ 必须**先编译**再跑：`hot_rpc_compare.java` 是默认包（无 package 声明）、
#    类名全小写 ⇒ 不能用 `java -cp ... <file>.java` 的源码模式，
#    也不能用 `tools.difftest.hot_rpc_compare`（那会去找 package 下的类）。
mkdir -p "$RUN_DIR/classes"
javac -encoding UTF-8 -d "$RUN_DIR/classes" -cp "$CP" "$HERE/hot_rpc_compare.java" || exit 1
CMP_RC=0
java -cp "$RUN_DIR/classes:$CP" hot_rpc_compare \
  --go "127.0.0.1:$GO_RPC_PORT" --rag "127.0.0.1:$RAG_RPC_PORT" || CMP_RC=$?
echo "  对拍退出码 = ${CMP_RC}"

echo
echo "  栅栏退出码 = ${FENCE_RC}"
echo "  对拍退出码 = ${CMP_RC}"

# 纪律 1 自检：冻结的 Go 仓库必须没有 tracked 改动
if [[ -d "$GO_REPO/.git" ]]; then
  GO_DIRTY="$(cd "$GO_REPO" && git status --porcelain --untracked-files=no)"
  if [[ -n "$GO_DIRTY" ]]; then
    echo; echo "  ⚠️ 冻结的 Go 仓库出现 tracked 改动（违反对拍纪律 1）："; echo "$GO_DIRTY" | sed 's/^/      /'
    CMP_RC=1
  else
    echo; echo "  ✓ 冻结的 Go 仓库 tracked diff 为空（纪律 1 守住）"
  fi
fi
echo; echo "日志：$LOGS"
exit $CMP_RC
