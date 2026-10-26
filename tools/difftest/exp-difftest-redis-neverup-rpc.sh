#!/usr/bin/env bash
#
# exp-difftest-redis-neverup-rpc — 「Redis **从未连上**」形态下的 **rpc 侧**双侧对拍
#
# ════════════════════════════════════════════════════════════════════════════
# 【它回答什么问题】
#   docs/DRIFT/users/user/rpc.md 的 **DRIFT-USERAPI-015**（Redis 命令超时 ≥ HTTP 预算
#   ⇒ RAG 503 / Go 200）目前只覆盖 **api 侧**（黑名单查询）。
#   本脚本补 **rpc 侧对应面**：user-rpc 的 **Logout**（写黑名单 key）在 Redis 不可达时，
#   两侧的 **gRPC 对外形状**（code / message / 耗时）是否同形。
#
# 【为什么必须直连 gRPC，不能经 api】
#   api 侧有 3s HTTP 预算：RAG 的**黑名单查询**自己就要 ~3s ⇒ 请求**根本走不到** logout 的 rpc 调用
#   ⇒ 经 api 观测 rpc 侧会被 api 侧预算**遮蔽** ⇒ 只能直连 gRPC。
#
# 【判决点：Go 的 Redis 失败延迟 vs 2s deadline】
#   Go 真源：
#     service/common/observability/rpc.go:38   timeoutCtx, cancel := context.WithTimeout(ctx, timeout)
#     service/common/observability/rpc.go:55   resp, err = handler(timeoutCtx, req)
#     ⇒ handler 拿到的 ctx **带 2000ms deadline**
#     service/user/user/rpc/internal/logic/logoutlogic.go:37
#       err := l.svcCtx.BizRedis.SetexCtx(l.ctx, blackListKey, "banned", int(expireTime))
#     logoutlogic.go:38-41 ⇒ 写失败 ⇒ codes.Internal "注销失败,Redis写入异常"
#     rpc.go:59-72 ⇒ select{ <-done ; <-timeoutCtx.Done() } ⇒ **谁先到谁说了算**
#   ⇒ Go 的 Redis 失败延迟 **< 2s** ⇒ 客户端看到 **INTERNAL**；
#     **≥ 2s** ⇒ ctx 先到期 ⇒ INTERNAL / DEADLINE_EXCEEDED **不定**。
#   RAG 侧：Redis 写只受 `spring.data.redis.timeout: 3s` 约束、**不受 gRPC deadline 约束**
#   ⇒ 拦截器的 2s 必然先到 ⇒ 客户端**确定**看到 DEADLINE_EXCEEDED。
#
# 【两种「连不上」的形态，**必须分开测】】
#   ip       = 127.0.0.1 ⇒ 端口拒连（连接被拒）
#   hostname = redis     ⇒ **主机名解析失败**（**这是生产默认值**：application.yml 的 ${REDIS_ADDR:redis}）
#
# 【第三种形态：REDIS_MODE=hanging —— 「连上了但不回包」】
#   前两种是「**快速失败**」（拨号阶段就挂）；这一种是「**命令超时**」路径：
#   TCP 能连上、但 Redis 协议层永不回应 ⇒ 客户端只能等**命令超时**。
#   ⇒ 它是唯一能让 **RAG 的 3s command timeout** 真正生效的形态
#     （前两种形态里 RAG 命中的是「连接失败」，远快于 3s）。
#   ⇒ 这一格决定「**Go 与 RAG 会不会在命令超时路径上分叉**」：
#     Go  ：SetexCtx 的 ctx deadline 2s 与 go-redis ReadTimeout 默认 3s ⇒ **2s 先到** ⇒ 返回 ctx 错误
#           ⇒ handler 返回 INTERNAL「注销失败,Redis写入异常」——但**与拦截器的 2s 同时** ⇒ 不定
#     RAG ：命令超时 3s，但**不受 gRPC deadline 约束** ⇒ **拦截器 2s 必然先到** ⇒ DEADLINE_EXCEEDED
#   ⇒ 若实测 Go=INTERNAL 而 RAG=DEADLINE_EXCEEDED ⇒ **命令超时路径上 rpc 侧有漂移**。
#
# 【Go 半边用什么客户端】
#   `tools/difftest/redis-logout-rpc/go` —— go.mod 里 `replace sea-try-go => <冻结仓>`，
#   直接 import **真源生成的 grpc stub**（`sea-try-go/service/user/user/rpc/pb`，不在 internal/ 下）
#   ⇒ 两侧用**同一份**客户端代码 ⇒ wire 契约一致 ⇒ 结果可直接比。
#   ⚠️ 探针与真源二进制都用 **CGO_ENABLED=0** 构建（`go version -m /tmp/difftest/bin/go-user-rpc` 实测）
#      ⇒ 纯 Go 解析器 ⇒ DNS 形态的耗时可比。
#
# 【端口（全部与共享栈错开）】
#   Go user-rpc :19005 · RAG user-rpc :59211 · PG :55434 · **假 Redis（没人监听）:56391**
#
# 【用法】
#   ./exp-difftest-redis-neverup-rpc.sh                 # 默认形态：127.0.0.1（端口拒连）
#   REDIS_HOST=redis ./exp-difftest-redis-neverup-rpc.sh # 形态：主机名解析失败
# ════════════════════════════════════════════════════════════════════════════
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"

RUN_DIR="${RUN_DIR:-/tmp/uuneverup_rpc}"
LOGS="$RUN_DIR/logs"
mkdir -p "$RUN_DIR/conf/go" "$LOGS"

PG_CONTAINER="${PG_CONTAINER:-uudifftest-postgres}"
PG_PORT="${PG_PORT:-55434}"
PG_USER="${PG_USER:-admin}"
PG_PASSWORD="${PG_PASSWORD:-difftest}"
# ⚠️ 故意**不创建**任何 Redis 容器：这个端口从头到尾没人监听。
DEAD_REDIS_PORT="${DEAD_REDIS_PORT:-56391}"
REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
# REDIS_MODE: never（没人监听 = 快速失败） | hanging（连上但永不回包 = 命令超时路径）
REDIS_MODE="${REDIS_MODE:-never}"

GO_RPC_PORT="${GO_RPC_PORT:-19005}"
RAG_RPC_PORT="${RAG_RPC_PORT:-59211}"

# 真源（冻结仓，只读）—— 探针用它 `replace` 引真源的真实 grpc stub。
GO_TRUTH_REPO="${GO_TRUTH_REPO:-/Users/pr/Documents/java&golang/Sea-RideTheWind_golang}"

GO_DB="uu_nr_go_db"; RAG_DB="uu_nr_rag_db"
PROBE="${PROBE:-/tmp/redis-logout-rpc}"
PROBE_SRC="$HERE/redis-logout-rpc/go"

PIDS=()
cleanup() {
  for p in "${PIDS[@]:-}"; do kill "$p" 2>/dev/null || true; done
  for p in "${PIDS[@]:-}"; do wait "$p" 2>/dev/null || true; done
}
trap cleanup EXIT

wait_port() {
  local port="$1" name="$2" limit="${3:-90}" i=0
  while (( i < limit )); do
    if lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then return 0; fi
    sleep 1; i=$((i+1))
  done
  echo "  FAIL 等待端口超时：${name} (:${port})" >&2; return 1
}
wait_port_free() {
  local port="$1" name="$2" limit="${3:-30}" i=0
  while (( i < limit )); do
    if ! lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then return 0; fi
    sleep 1; i=$((i+1))
  done
  echo "  FAIL 端口被占：${name} (:${port})" >&2; return 1
}

echo "==> [0/6] 构件检查"
for b in /tmp/difftest/bin/go-user-rpc "$ROOT/service/users/user/user-rpc/target/user-rpc-0.0.1-SNAPSHOT.jar"; do
  if [[ ! -f "$b" ]]; then echo "  FAIL 缺构件：$b" >&2; exit 2; fi
done
if [[ ! -x "$PROBE" ]]; then
  echo "  · 探针不存在 ⇒ 现在构建（运行时生成 go.mod ⇒ 仓里不留机器相关路径）"
  if [[ ! -d "$GO_TRUTH_REPO" ]]; then
    echo "  FAIL 真源仓库不存在：$GO_TRUTH_REPO（可用 GO_TRUTH_REPO=… 指定）" >&2; exit 2
  fi
  BUILD_DIR="$(mktemp -d)/goprobe"
  mkdir -p "$BUILD_DIR"
  cp "$PROBE_SRC/main.go" "$BUILD_DIR/main.go"
  cat >"$BUILD_DIR/go.mod" <<EOF
module redislogoutrpc

go 1.24.0

require (
	google.golang.org/grpc v1.81.1
	sea-try-go v0.0.0
)

replace sea-try-go => ${GO_TRUTH_REPO}
EOF
  # ⚠️ CGO_ENABLED=0：必须与真源二进制一致（go version -m /tmp/difftest/bin/go-user-rpc 实测为 CGO_ENABLED=0）
  #    ⇒ 纯 Go 解析器 ⇒ DNS 形态的耗时两侧可比
  if ! ( cd "$BUILD_DIR" && CGO_ENABLED=0 GOFLAGS=-mod=mod GOPROXY=off GOSUMDB=off GOPRIVATE='*' \
           go build -o "$PROBE" . ) >"$LOGS/probe-build.log" 2>&1; then
    echo "  FAIL 探针构建失败（见 $LOGS/probe-build.log）" >&2
    tail -20 "$LOGS/probe-build.log" >&2
    exit 2
  fi
  echo "  ✓ 探针已构建 → $PROBE"
fi
if ! docker ps --format '{{.Names}}' | grep -qx "$PG_CONTAINER"; then
  echo "  FAIL PG 容器 ${PG_CONTAINER} 不在运行" >&2; exit 2
fi
if [[ "$REDIS_MODE" == "never" ]] && lsof -nP -iTCP:"$DEAD_REDIS_PORT" -sTCP:LISTEN >/dev/null 2>&1; then
  echo "  FAIL 端口 ${DEAD_REDIS_PORT} 已被监听 ⇒「从未连上」不成立" >&2; exit 2
fi
echo "  ✓ 构件齐全（模式：${REDIS_MODE}）"
if [[ "$REDIS_MODE" == "hanging" ]]; then
  # 挂起监听器：接受连接、**永不回任何字节** ⇒ 客户端只能等命令超时。
  python3 - "$DEAD_REDIS_PORT" <<'PY' >"$LOGS/hanging-redis.log" 2>&1 &
import socket, sys, time
port = int(sys.argv[1])
s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
s.bind(("0.0.0.0", port))
s.listen(64)
conns = []
while True:
    try:
        c, _ = s.accept()
        conns.append(c)          # 收了连接就**不读不写**
    except Exception:
        time.sleep(0.05)
PY
  PIDS+=($!)
  sleep 1
  if ! lsof -nP -iTCP:"$DEAD_REDIS_PORT" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "  FAIL 挂起监听器没起来（:${DEAD_REDIS_PORT}）" >&2; exit 2
  fi
  echo "  ✓ 挂起监听器已起（:${DEAD_REDIS_PORT}）—— 接受连接但永不回包 ⇒ 命中命令超时路径"
fi

echo "==> [1/6] 重建两侧数据库"
for db in "$GO_DB" "$RAG_DB"; do
  docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d postgres -c "DROP DATABASE IF EXISTS $db WITH (FORCE)" >/dev/null 2>&1 || true
  docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d postgres -c "CREATE DATABASE $db" >/dev/null
done
echo "  ✓ ${GO_DB} / ${RAG_DB} 已重建"

echo "==> [2/6] 生成配置副本（两侧 Redis 都指向 ${REDIS_HOST}:${DEAD_REDIS_PORT} —— 形态：${REDIS_HOST}）"
sed -e "s/0.0.0.0:19004/0.0.0.0:${GO_RPC_PORT}/" \
    -e "s/Port: \"55432\"/Port: \"${PG_PORT}\"/" \
    -e "s#Host: 127.0.0.1:56379#Host: ${REDIS_HOST}:${DEAD_REDIS_PORT}#" \
    -e "s/Port: 19094/Port: 19196/" \
    -e "s/DBName: \"go_db\"/DBName: \"${GO_DB}\"/" \
    "$HERE/conf/go/user-rpc.yaml" > "$RUN_DIR/conf/go/user-rpc.yaml"
grep -n -A2 "BizRedis" "$RUN_DIR/conf/go/user-rpc.yaml" | sed 's/^/    /'

echo "==> [3/6] 起 Go user-rpc"
wait_port_free "$GO_RPC_PORT" "go user-rpc" 30
/tmp/difftest/bin/go-user-rpc -f "$RUN_DIR/conf/go/user-rpc.yaml" >"$LOGS/go-rpc.log" 2>&1 & PIDS+=($!)
wait_port "$GO_RPC_PORT" "go user-rpc" 90 || { tail -20 "$LOGS/go-rpc.log" >&2; exit 1; }

echo "==> [4/6] 起 RAG user-rpc"
wait_port_free "$RAG_RPC_PORT" "rag user-rpc" 30
env POSTGRES_ADDR=127.0.0.1 POSTGRES_PORT="$PG_PORT" POSTGRES_USER="$PG_USER" POSTGRES_PASSWORD="$PG_PASSWORD" \
    POSTGRES_DB="$RAG_DB" REDIS_ADDR="$REDIS_HOST" REDIS_PORT="$DEAD_REDIS_PORT" \
    NACOS_ADDR=127.0.0.1 NACOS_PORT=1 GRPC_SERVER_PORT="$RAG_RPC_PORT" \
    java -jar "$ROOT/service/users/user/user-rpc/target/user-rpc-0.0.1-SNAPSHOT.jar" >"$LOGS/rag-rpc.log" 2>&1 & PIDS+=($!)
wait_port "$RAG_RPC_PORT" "rag user-rpc" 150 || { tail -30 "$LOGS/rag-rpc.log" >&2; exit 1; }

echo "==> [5/6] 直连 Logout（3 轮）—— 两侧同一份真源 stub 客户端"
TOKEN="probe-token-$$"
for i in 1 2 3; do
  G=$("$PROBE" "127.0.0.1:${GO_RPC_PORT}"  "$TOKEN" 10000 2>&1)
  R=$("$PROBE" "127.0.0.1:${RAG_RPC_PORT}" "$TOKEN" 10000 2>&1)
  printf '  第%s轮  Go  %s\n' "$i" "$G"
  printf '          RAG %s\n'    "$R"
done

echo "==> [6/6] 两侧日志里的 Redis 失败证据"
echo "  ── Go 侧 ──"
grep -iE "redis|dial|connect|refused|deadline" "$LOGS/go-rpc.log" | tail -6 | sed 's/^/    /' || true
echo "  ── RAG 侧 ──"
grep -iE "redis|Unable to connect|deadline" "$LOGS/rag-rpc.log" | tail -6 | sed 's/^/    /' || true

cat <<EOF

  ═══════════ 判读 ═══════════
  · 形态：${REDIS_HOST}:${DEAD_REDIS_PORT}（模式 ${REDIS_MODE}）
  · Go  的 SetexCtx 受 handler ctx 的 **2000ms deadline** 约束 ⇒ 失败延迟 < 2s ⇒ INTERNAL 赢
  · RAG 的 Redis 写**不受** gRPC deadline 约束（只受 3s command timeout）⇒ **2s 必然先到** ⇒ DEADLINE_EXCEEDED
  · 若 Go=INTERNAL 而 RAG=DEADLINE_EXCEEDED ⇒ **rpc 侧也有漂移**（与 api 侧的 015 同源）
  · 若两侧都是 DEADLINE_EXCEEDED ⇒ rpc 侧**无漂移**（015 只存在于 api 侧）
  · ⚠️ 形态决定 RAG 命中的是哪条路径：
      never（拒连 / 解析失败）⇒ 命中「**连接失败**」，远快于 3s ⇒ 拦截器不参与
      hanging（连上不回包）  ⇒ 命中「**命令超时**」⇒ 3s > 2s ⇒ **拦截器必然先到**
EOF
echo "  日志：$LOGS"
