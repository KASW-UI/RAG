#!/usr/bin/env bash
# ════════════════════════════════════════════════════════════════════════════
#  exp-difftest-redis-failfast — UNRESOLVED-USERRPC-004② 的真故障注入实验
# ════════════════════════════════════════════════════════════════════════════
#
# 【它回答什么问题】
#   docs/DRIFT/users/user/rpc.md 的 §9 **UNRESOLVED-USERRPC-004②**：
#     「进程启动时 Redis 不可用时两侧的失败时机（CASE-35）」
#   台账原话：「本轮未做『**停掉 Redis 再起进程**』的注入实验」。
#
#   已知静态证据：
#     Go  ：`service/user/user/rpc/internal/svc/servicecontext.go:29`
#           `redis.MustNewRedis(c.BizRedis)` —— 在**建 ServiceContext 时**（早于 `MustNewServer`）
#           ⇒ go-zero `core/stores/redis/redis.go:109-113` → `NewRedis` → `checkConnection(PingTimeout)`
#              → 失败则 `logx.Must(err)` ⇒ **启动即 fatal**。
#     RAG  ：Spring `StringRedisTemplate` **懒连接** ⇒ 启动期不碰 Redis。
#
#   本脚本把「静态推断」变成**双侧活体证据**：把两侧的 Redis 都指到一个**死端口**，
#   然后看**进程到底起没起来**。
#
# 【⚠️ 为什么用「死端口」而不是「停掉 Redis 容器」】
#   停容器会**同时影响别的会话**（本机多个会话共用 Redis）；
#   死端口只影响本脚本自己起的进程 ⇒ 零副作用。
#   ⚠️ 但要记住这测的是「**连不上**」这一类故障；
#     「**连得上但慢**」是另一种故障（见 api 侧的 U-004），两者不可互相替代。
#
# 【用法】
#   ./exp-difftest-redis-failfast.sh
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"

RUN_DIR="${RUN_DIR:-/tmp/uuredisfail}"
LOGS="$RUN_DIR/logs"
mkdir -p "$RUN_DIR/conf/go" "$LOGS"

DEAD_REDIS_PORT="${DEAD_REDIS_PORT:-56999}"   # 没人监听的端口 ⇒ connection refused
PG_PORT="${PG_PORT:-55434}"
PG_USER="${PG_USER:-admin}"
PG_PASSWORD="${PG_PASSWORD:-difftest}"
REDIS_PORT="${REDIS_PORT:-56382}"
PG_CONTAINER="${PG_CONTAINER:-uudifftest-postgres}"

GO_RPC_PORT="${GO_RPC_PORT:-19005}"
RAG_RPC_PORT="${RAG_RPC_PORT:-59211}"

echo "==> [0/5] 前置检查"
if lsof -nP -iTCP:"$DEAD_REDIS_PORT" -sTCP:LISTEN >/dev/null 2>&1; then
  echo "  FAIL 端口 ${DEAD_REDIS_PORT} 居然有人在听（不能当死端口用）" >&2; exit 2
fi
echo "  ✓ 死端口 ${DEAD_REDIS_PORT} 确认无监听者"

echo "==> [1/5] 准备数据库"
docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d postgres \
  -c "DROP DATABASE IF EXISTS uu_redis_fail_db WITH (FORCE)" >/dev/null 2>&1 || true
docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d postgres \
  -c "CREATE DATABASE uu_redis_fail_db" >/dev/null
echo "  ✓ uu_redis_fail_db 已建"

echo "==> [2/5] Go 侧：把 BizRedis 指到死端口后启动 go-user-rpc"
sed -e "s/0.0.0.0:19004/0.0.0.0:${GO_RPC_PORT}/" \
    -e "s/Port: \"55432\"/Port: \"${PG_PORT}\"/" \
    -e "s#Host: 127.0.0.1:56379#Host: 127.0.0.1:${DEAD_REDIS_PORT}#" \
    -e "s/Port: 19094/Port: 19105/" \
    -e "s/DBName: \"go_db\"/DBName: \"uu_redis_fail_db\"/" \
    "$HERE/conf/go/user-rpc.yaml" > "$RUN_DIR/conf/go/user-rpc.yaml"
grep -q "127.0.0.1:${DEAD_REDIS_PORT}" "$RUN_DIR/conf/go/user-rpc.yaml" \
  || { echo "  FAIL 死端口没写进配置副本" >&2; exit 1; }

/tmp/difftest/bin/go-user-rpc -f "$RUN_DIR/conf/go/user-rpc.yaml" >"$LOGS/go.log" 2>&1 &
GO_PID=$!
GO_ALIVE=0
for _ in $(seq 1 25); do
  if ! kill -0 "$GO_PID" 2>/dev/null; then break; fi          # 已退出
  if lsof -nP -iTCP:"$GO_RPC_PORT" -sTCP:LISTEN >/dev/null 2>&1; then GO_ALIVE=1; break; fi
  sleep 1
done
kill "$GO_PID" 2>/dev/null || true
wait "$GO_PID" 2>/dev/null || true

echo "==> [3/5] RAG 侧：把 REDIS_PORT 指到死端口后启动 user-rpc"
JAR="$ROOT/service/users/user/user-rpc/target/user-rpc-0.0.1-SNAPSHOT.jar"
env POSTGRES_ADDR=127.0.0.1 POSTGRES_PORT="$PG_PORT" \
    POSTGRES_USER="$PG_USER" POSTGRES_PASSWORD="$PG_PASSWORD" POSTGRES_DB=uu_redis_fail_db \
    REDIS_ADDR=127.0.0.1 REDIS_PORT="$DEAD_REDIS_PORT" \
    NACOS_ADDR=127.0.0.1 NACOS_PORT=1 \
    GRPC_SERVER_PORT="$RAG_RPC_PORT" \
    java -jar "$JAR" >"$LOGS/rag.log" 2>&1 &
RAG_PID=$!
RAG_ALIVE=0
for _ in $(seq 1 60); do
  if ! kill -0 "$RAG_PID" 2>/dev/null; then break; fi
  if lsof -nP -iTCP:"$RAG_RPC_PORT" -sTCP:LISTEN >/dev/null 2>&1; then RAG_ALIVE=1; break; fi
  sleep 1
done
kill "$RAG_PID" 2>/dev/null || true
wait "$RAG_PID" 2>/dev/null || true

echo "==> [4/5] 判定"
echo "  ── Go 侧 ──"
if [[ $GO_ALIVE -eq 1 ]]; then
  echo "    ❌ Go 侧**起来了**（与静态证据矛盾！）"
else
  echo "    ✅ Go 侧**启动失败**（与静态证据一致：MustNewRedis fail-fast）"
fi
grep -i -E "redis|fatal|panic|must" "$LOGS/go.log" | head -6 | sed 's/^/      /' || true

echo "  ── RAG 侧 ──"
if [[ $RAG_ALIVE -eq 1 ]]; then
  echo "    ✅ RAG 侧**起来了**（懒连接 ⇒ 与静态证据一致）"
else
  echo "    ❌ RAG 侧**启动失败**（与静态证据矛盾！）"
fi
grep -i -E "redis|ERROR|Application run failed" "$LOGS/rag.log" | head -6 | sed 's/^/      /' || true

echo "==> [5/5] 汇总"
printf "  %-10s 启动结果=%s\n" "Go"  "$([[ $GO_ALIVE  -eq 1 ]] && echo '起来了' || echo '启动失败')"
printf "  %-10s 启动结果=%s\n" "RAG" "$([[ $RAG_ALIVE -eq 1 ]] && echo '起来了' || echo '启动失败')"
echo
echo "  ⇒ 若 Go 失败 / RAG 成功 ⇒ 这就是 UNRESOLVED-USERRPC-004② 的**实测形态**："
echo "    「Go 在启动期 fail-fast，RAG 懒连接」—— 一个**可观察的差异**（部署期 Redis 挂了就起不来 vs 起得来）。"
