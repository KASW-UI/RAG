#!/usr/bin/env bash
# ════════════════════════════════════════════════════════════════════════════
#  exp-difftest-grpc-user9 — UNRESOLVED-USERRPC-007 的编排脚本
# ════════════════════════════════════════════════════════════════════════════
#
# 【它做什么】
#   起 **Go 真源 user-rpc** 与 **RAG user-rpc** 两套（只起 rpc，不起 api/admin/message），
#   两者连**同一个 PG 实例上的两个独立库**（uu_go_db / uu_rag_db），
#   然后用同 stub 双端口打 **9 个 RPC** 的：
#     ① 字段级响应（happy path）
#     ② error message **逐字**（gRPC 的 grpc-message 头，wire 可见）
#     ③ 边界输入（空串 / 0 / -1 / 超大值 / 不存在的 id …）
#   实际执行体是 grpc-user9-dual-port-runner.java。
#
# 【与 run.sh 的关系】
#   run.sh 起的是**全栈**（user/admin/message 的 rpc+api 共 5 对），用于 HTTP 层的
#   diff.py 对拍与 message 域的 grpc-message 对拍。
#   本脚本是**只针对 user 域 9 个 RPC 的字段级对拍**，刻意更窄：
#     · 起得少（2 个进程，不是 10 个）⇒ 在本机 Docker 内存只有 7.65 GiB、
#       且同时有别的会话在跑的环境里，这是**能不能跑起来**的关键；
#     · 库是**自己的**（uu_go_db / uu_rag_db）⇒ 不污染共享的 go_db / rag_db。
#
# 【⚠️ 四条纪律】
#   1. **绝不修改冻结的 Go 仓库**：Go 的运行配置全部来自本脚本在 /tmp 里现生成的
#      yaml 副本（端口与库名都指向本脚本自己的资源）。
#   2. **绝不碰共享的 go_db / rag_db**：那两个库由 tools/difftest/run.sh 管理，
#      **别的会话正在用**。本脚本只 DROP/CREATE 自己的 uu_* 库。
#      ⚠️ 同理，**本脚本自带容器**（uudifftest-postgres / uudifftest-redis），
#      也不用共享的 difftest-* —— 2026-10-24 实测共享容器被别的会话删了两次。
#   3. **端口独占**：Go 19005 / RAG 59211 / DevServer 19195 / PG 55434 / Redis 56382，
#      与 run.sh 的 19004 / 59210 / 19094 错开，也与本机既有服务错开。
#      ⚠️ **DevServer 端口实测会撞**：19095 是共享栈里 Go user-api 的 DevServer 端口
#      （2026-10-24 实测撞过），所以本脚本用 19195。
#   4. **变量后紧跟全角字符会出事**：bash 在 UTF-8 locale 下会把全角字符算进标识符，
#      `"$RAG_PORT）"` 会被解析成变量 `RAG_PORT）` ⇒ `unbound variable`。
#      ⇒ 本脚本里所有变量引用一律写 `${VAR}`。
#
# 【用法】
#   ./exp-difftest-grpc-user9.sh              # 全流程（含构建 RAG jar）
#   ./exp-difftest-grpc-user9.sh --no-build   # 复用已有 jar
#   ./exp-difftest-grpc-user9.sh --keep       # 跑完保留两套栈，便于手工再打
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
GO_REPO="${GO_REPO:-/Users/pr/Documents/java&golang/Sea-RideTheWind_golang}"

RUN_DIR="${RUN_DIR:-/tmp/uudifftest}"
LOGS="$RUN_DIR/logs"
GO_BIN="${GO_BIN:-/tmp/difftest/bin/go-user-rpc}"

PG_CONTAINER="${PG_CONTAINER:-uudifftest-postgres}"
REDIS_CONTAINER="${REDIS_CONTAINER:-uudifftest-redis}"
PG_PORT="${PG_PORT:-55434}"
REDIS_PORT="${REDIS_PORT:-56382}"
PG_USER="${PG_USER:-admin}"
PG_PASSWORD="${PG_PASSWORD:-difftest}"

GO_DB="${GO_DB:-uu_go_db}"
RAG_DB="${RAG_DB:-uu_rag_db}"

GO_PORT="${GO_PORT:-19005}"
RAG_PORT="${RAG_PORT:-59211}"
# ⚠️ 19195 而不是 19095 —— 19095 是共享栈里 Go user-api 的 DevServer 端口
#    （2026-10-24 实测撞过：共享栈重启后正好占着 19095）。
#    本脚本必须与共享栈**完全错开**，否则两边抢端口、谁也跑不稳。
GO_DEVSERVER_PORT="${GO_DEVSERVER_PORT:-19195}"

JAR="$ROOT/service/users/user/user-rpc/target/user-rpc-0.0.1-SNAPSHOT.jar"

BUILD=1
KEEP=0
for a in "$@"; do
  case "$a" in
    --no-build) BUILD=0 ;;
    --keep)     KEEP=1 ;;
    *) echo "未知参数：${a}（可选：--no-build / --keep）" >&2; exit 2 ;;
  esac
done

PIDS=()
cleanup() {
  if [[ $KEEP -eq 1 ]]; then
    echo "[exp-user9] --keep：保留两套栈。手动收摊：kill ${PIDS[*]:-}"
    return
  fi
  for p in "${PIDS[@]:-}"; do kill "$p" 2>/dev/null || true; done
}
trap cleanup EXIT

mkdir -p "$RUN_DIR/conf/go" "$LOGS"

wait_port() {  # port, name, tries
  local port=$1 name=$2 tries=${3:-90}
  for _ in $(seq 1 "$tries"); do
    if lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
      echo "  OK   ${name} 已监听 :${port}"; return 0
    fi
    sleep 1
  done
  echo "  FAIL ${name} 超时（:${port}）"; return 1
}

echo "==> [1/5] 准备自己的容器与两个库（**不碰共享的 go_db / rag_db**）"

# ⚠️ **自带容器**，不复用共享的 difftest-* ——
#    2026-10-24 实测：共享容器被别的会话**删了两次**（连同本会话的 uu_* 库一起没了），
#    共享栈的进程也被一起收走 ⇒ 对拍跑到一半就没了。
#    自己的容器（PG ~40 MiB / Redis ~9 MiB）成本可忽略，且生命周期只属于自己。
ensure_container() {  # name image host_port container_port [docker run 额外参数...]
  local name="$1" image="$2" hport="$3" cport="$4"; shift 4
  if docker ps -a --format '{{.Names}}' | grep -qx "$name"; then
    if docker ps --format '{{.Names}}' | grep -qx "$name"; then
      echo "  · 容器 ${name} 已在运行"
    else
      docker start "$name" >/dev/null
      echo "  · 启动已有容器 ${name}"
    fi
  else
    docker run -d --name "$name" "$@" -p "127.0.0.1:${hport}:${cport}" "$image" >/dev/null
    echo "  · 新建容器 ${name}（${image} → :${hport}）"
  fi
}

wait_pg() {  # 等 PG 真正能连上（容器 healthy ≠ psql 可用）
  for _ in $(seq 1 60); do
    if docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d postgres -c 'select 1' >/dev/null 2>&1; then
      echo "  OK   ${PG_CONTAINER} 已可连"; return 0
    fi
    sleep 1
  done
  echo "  FAIL ${PG_CONTAINER} 超时不可连" >&2; return 1
}

ensure_container "$PG_CONTAINER" postgres:16-alpine "$PG_PORT" 5432 \
  -e POSTGRES_USER="$PG_USER" -e POSTGRES_PASSWORD="$PG_PASSWORD" -e POSTGRES_DB=postgres
ensure_container "$REDIS_CONTAINER" redis:7-alpine "$REDIS_PORT" 6379
wait_pg

for db in "$GO_DB" "$RAG_DB"; do
  docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d postgres \
    -c "DROP DATABASE IF EXISTS $db" >/dev/null 2>&1 || true
  docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d postgres \
    -c "CREATE DATABASE $db" >/dev/null
done
echo "  ✓ $GO_DB / $RAG_DB 已重建"

echo "==> [2/5] 生成 Go 运行配置副本（端口与库名都指向本脚本自己的资源）"
# ⚠️ 除了监听端口/库名，**PG 端口与 Redis 端口也要改** ——
#    本脚本用的是**自己的容器**（uudifftest-*），不是共享的 difftest-*。
#    共享容器会被别的会话回收（2026-10-24 实测被删过两次）⇒ 本脚本自带容器，
#    才不会被别人的生命周期打断。
sed -e "s/0.0.0.0:19004/0.0.0.0:$GO_PORT/" \
    -e "s/DBName: \"go_db\"/DBName: \"$GO_DB\"/" \
    -e "s/Port: 19094/Port: $GO_DEVSERVER_PORT/" \
    -e "s/Port: \"55432\"/Port: \"$PG_PORT\"/" \
    -e "s/Host: 127.0.0.1:56379/Host: 127.0.0.1:$REDIS_PORT/" \
    "$HERE/conf/go/user-rpc.yaml" > "$RUN_DIR/conf/go/user-rpc.yaml"
grep -q "DBName: \"$GO_DB\"" "$RUN_DIR/conf/go/user-rpc.yaml" \
  || { echo "配置副本生成失败（锚点没命中）" >&2; exit 1; }
grep -q "Port: \"$PG_PORT\"" "$RUN_DIR/conf/go/user-rpc.yaml" \
  || { echo "PG 端口改写失败（锚点没命中）" >&2; exit 1; }
grep -q "127.0.0.1:$REDIS_PORT" "$RUN_DIR/conf/go/user-rpc.yaml" \
  || { echo "Redis 端口改写失败（锚点没命中）" >&2; exit 1; }

if [[ $BUILD -eq 1 ]]; then
  echo "==> [3/5] 构建（Go 二进制 + RAG jar）"
  if [[ ! -x "$GO_BIN" ]]; then
    ( cd "$GO_REPO" && CGO_ENABLED=0 go build -o "$GO_BIN" ./service/user/user/rpc )
  else
    echo "  · 复用已有 Go 二进制 $GO_BIN"
  fi
  ( cd "$ROOT" && mvn -q -o -DskipTests package -pl service/users/user/user-rpc -am )
else
  echo "==> [3/5] 跳过构建（--no-build）"
fi
[[ -f "$JAR" ]] || { echo "缺少 jar：$JAR" >&2; exit 1; }

echo "==> [4/5] 起两套 user-rpc（Go :${GO_PORT} / RAG :${RAG_PORT}）"
"$GO_BIN" -f "$RUN_DIR/conf/go/user-rpc.yaml" >"$LOGS/go-user-rpc.log" 2>&1 & PIDS+=($!)
sleep 3
env POSTGRES_ADDR=127.0.0.1 POSTGRES_PORT="$PG_PORT" \
    POSTGRES_USER="$PG_USER" POSTGRES_PASSWORD="$PG_PASSWORD" POSTGRES_DB="$RAG_DB" \
    REDIS_ADDR=127.0.0.1 REDIS_PORT="$REDIS_PORT" \
    NACOS_ADDR=127.0.0.1 NACOS_PORT=1 \
    GRPC_SERVER_PORT="$RAG_PORT" \
    java -jar "$JAR" >"$LOGS/rag-user-rpc.log" 2>&1 & PIDS+=($!)
wait_port "$GO_PORT"  "go  user-rpc" 60
wait_port "$RAG_PORT" "rag user-rpc" 120

echo "==> [5/5] 编译并跑双端口对拍"
PROTO_CP="$ROOT/service/users/user/user-proto/target/classes"
if [[ ! -d "$PROTO_CP" ]]; then
  ( cd "$ROOT" && mvn -q -o -DskipTests package -pl service/users/user/user-proto )
fi
mvn -q -o -pl service/users/user/user-rpc dependency:build-classpath \
    -Dmdep.outputFile="$RUN_DIR/cp.txt" >/dev/null 2>&1
TEST_CP="$(cat "$RUN_DIR/cp.txt")"
mkdir -p "$RUN_DIR/build"
javac -nowarn -proc:none -d "$RUN_DIR/build" "$HERE/grpc-user9-dual-port-runner.java" \
      -cp "$PROTO_CP:$TEST_CP"
set +e
# ⚠️ -Dgo.db.url / -Drag.db.url 必须透传：runner 靠它**清空两侧业务表**才能幂等。
#    不透传时 runner 用的是默认值（uu_go_db / uu_rag_db）——
#    在本脚本改了库名的场景下，runner 会去清**别的库**（或根本清不到）⇒ 假漂移。
java -cp "$RUN_DIR/build:$PROTO_CP:$TEST_CP" \
     -Dgo.user.port="$GO_PORT" -Drag.user.port="$RAG_PORT" \
     -Dgo.db.url="jdbc:postgresql://127.0.0.1:$PG_PORT/$GO_DB" \
     -Drag.db.url="jdbc:postgresql://127.0.0.1:$PG_PORT/$RAG_DB" \
     -Ddb.user="$PG_USER" -Ddb.password="$PG_PASSWORD" \
     -Dcleanup=true \
     tools.difftest.GrpcUserNineRpcDualPortRunner | tee "$LOGS/user9-results.txt"
RC=${PIPESTATUS[0]}
set -e
echo
echo "[exp-user9] 结果已存 ${LOGS}/user9-results.txt（exit=${RC}）"
exit "$RC"
