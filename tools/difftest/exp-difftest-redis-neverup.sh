#!/usr/bin/env bash
#
# exp-difftest-redis-neverup — 「Redis **从未连上过**」这一故障形态的双侧对拍
#
# ════════════════════════════════════════════════════════════════════════════
# 【它回答什么问题】
#   docs/DRIFT/users/user/api.md 的 **DRIFT-USERAPI-014**（Redis 不可用 ⇒ RAG 503 / Go 200）
#   的**修法覆盖边界**：已修的那条只覆盖「**连上之后断连**」，
#   本脚本补的是「**从头就没连上过**」—— 两者在客户端里的状态机不同：
#
#     连上之后断连：endpoint 已进入 disconnected ⇒ Lettuce 的
#                   DisconnectedBehavior=REJECT_COMMANDS 能秒拒 ⇒ 命令立刻失败
#     从未连上    ：endpoint 还在 CONNECTING ⇒ REJECT_COMMANDS **管不着**，
#                   命令被排队等连接 ⇒ 撞上 commandTimeout
#
#   ⇒ 本脚本回答：「**Redis 从未连上**时，两侧黑名单查询的失败耗时与对外形状。」
#
# 【为什么这个形态值得单独测 —— 它是本轮写守网测试时意外撞出来的】
#   2026-10-24 写 user-api 保护层守网测试（UserApiProtectionMvcTest）时，
#   测试**没有 Redis** ⇒ 首个受保护请求耗时 3.3s ⇒ 撞 3s HTTP 预算 ⇒ 503。
#   而同一次实测里 Go 侧同一故障形态是 ~1.7s（仍在预算内 ⇒ fail-open 200）。
#   ⇒ 但那两条数据**分属不同形态**（Go 测的是「连上后断连」）⇒ 不能直接比。
#   ⇒ 本脚本把「从未连上」这一格补上，才凑成 2×2 的可比较矩阵。
#
# 【⚠️ 必须分次探测：首个请求 ≠ 稳态】
#   Lettuce 首次连接失败后，endpoint 转入 disconnected 态 ⇒ 之后的命令
#   可能被**秒拒**（REJECT_COMMANDS）⇒ 首个请求慢、后续请求快。
#   ⇒ 只测一次会得出「Redis 故障恒 3.3s」的错误结论 ⇒ 本脚本连测 3 轮。
#
# 【⚠️ 为什么用「没人监听的端口」而不是「起个容器再停掉」】
#   「起容器再停掉」= 连上之后断连（已覆盖）；「端口没人监听」= 从未连上。
#   本场景要的就是后者 ⇒ **故意不创建任何 Redis 容器**。
#
# 【⚠️ 为什么 register/login 不算「第一次碰黑名单」】
#   黑名单中间件在**没有 Authorization 头**时直接放行（Go：
#   checkblacklistmiddleware.go:25-28 `if authHeader == "" { next(...) }`）
#   ⇒ register/login 不带 Authorization ⇒ 不碰 Redis。
#   ⇒ 脚本**同时打印 login 的耗时**作为「login 没碰 Redis」的旁证。
#
# 【Go 出处】
#   service/user/user/api/internal/middleware/checkblacklistmiddleware.go:35
#     exists, err := m.Redis.ExistsCtx(r.Context(), blackListKey)
#   :36  if err == nil && exists { …401… }   ⇒ err != nil ⇒ **fail-open 放行**
#   service/user/user/api/internal/svc/servicecontext.go:23
#     redisDb := redis.MustNewRedis(c.BizRedis)   ⇒ 客户端由 go-zero 构造
#   go-zero@v1.10.2 core/stores/redis/conf.go:25
#     NonBlock bool `json:",default=true"`  ⇒ **启动不 ping** ⇒ Redis 挂了也能起
#
# 【端口（全部与共享栈错开）】
#   Go  user-rpc :19005 · Go  user-api :18899
#   RAG user-rpc :59211 · RAG user-api :19900
#   PG :55434 · **假 Redis（没人监听）:56391**
#
# 【用法】
#   ./exp-difftest-redis-neverup.sh
# ════════════════════════════════════════════════════════════════════════════
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"

RUN_DIR="${RUN_DIR:-/tmp/uuneverup}"
LOGS="$RUN_DIR/logs"
mkdir -p "$RUN_DIR/conf/go" "$LOGS"

PG_CONTAINER="${PG_CONTAINER:-uudifftest-postgres}"
PG_PORT="${PG_PORT:-55434}"
PG_USER="${PG_USER:-admin}"
PG_PASSWORD="${PG_PASSWORD:-difftest}"
# ⚠️ 故意**不创建**任何 Redis 容器：这个端口从头到尾没人监听。
DEAD_REDIS_PORT="${DEAD_REDIS_PORT:-56391}"
# ⚠️ 两种「连不上」的形态，**必须分开测**（2026-10-24 实测：两者耗时差一个数量级）：
#   ip       = 127.0.0.1  ⇒ 端口拒连（**docker-compose 部署里 Redis 容器在、进程死**就是这个形态）
#   hostname = redis      ⇒ **主机名解析失败**（**这是生产默认值**：application.yml 的 ${REDIS_ADDR:redis}）
#                          ⇒ 而 MockMvc 测试里那个 3.31s 就是这个形态造成的
REDIS_HOST="${REDIS_HOST:-127.0.0.1}"

GO_RPC_PORT="${GO_RPC_PORT:-19005}"
GO_API_PORT="${GO_API_PORT:-18899}"
RAG_RPC_PORT="${RAG_RPC_PORT:-59211}"
RAG_API_PORT="${RAG_API_PORT:-19900}"

GO_DB="uu_nu_go_db"; RAG_DB="uu_nu_rag_db"
U="uu_nu_$(date +%s)_$RANDOM"

PIDS=()
cleanup() {
  for p in "${PIDS[@]:-}"; do kill "$p" 2>/dev/null || true; done
  for p in "${PIDS[@]:-}"; do wait "$p" 2>/dev/null || true; done
}
trap cleanup EXIT

wait_port() {
  local port=$1 name=$2 tries=${3:-120}
  for _ in $(seq 1 "$tries"); do
    lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1 && { echo "  OK   ${name} 已监听 :${port}"; return 0; }
    sleep 1
  done
  echo "  FAIL ${name} 超时（:${port}）" >&2; return 1
}
wait_port_free() {
  local port=$1 name=$2 tries=${3:-30}
  for _ in $(seq 1 "$tries"); do
    lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1 || { echo "  OK   ${name} 端口 :${port} 已释放"; return 0; }
    sleep 1
  done
  echo "  FAIL ${name} 端口 :${port} 仍被占用" >&2; return 1
}

echo "==> [0/7] 检查构件"
for b in /tmp/difftest/bin/go-user-rpc /tmp/difftest/bin/go-user-api; do
  [[ -x "$b" ]] || { echo "  FAIL 缺 Go 二进制 $b（先跑 exp-difftest-redis-blacklist.sh 建栈）" >&2; exit 2; }
done
for j in "$ROOT/service/users/user/user-rpc/target/user-rpc-0.0.1-SNAPSHOT.jar" \
         "$ROOT/service/users/user/user-api/target/user-api-0.0.1-SNAPSHOT.jar"; do
  [[ -f "$j" ]] || { echo "  FAIL 缺 jar $j" >&2; exit 2; }
done
docker ps --format '{{.Names}}' | grep -qx "$PG_CONTAINER" || {
  echo "  FAIL PG 容器 ${PG_CONTAINER} 不在运行" >&2; exit 2; }
# ⚠️ 断言：假 Redis 端口必须**没人监听**，否则「从未连上」不成立。
if lsof -nP -iTCP:"$DEAD_REDIS_PORT" -sTCP:LISTEN >/dev/null 2>&1; then
  echo "  FAIL 端口 ${DEAD_REDIS_PORT} 已被监听 ⇒ 本场景不成立" >&2; exit 2
fi
echo "  ✓ 构件齐全；${DEAD_REDIS_PORT} 确认无人监听（= Redis 从未连上）"

echo "==> [1/7] 重建两侧数据库"
for db in "$GO_DB" "$RAG_DB"; do
  docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d postgres -c "DROP DATABASE IF EXISTS $db WITH (FORCE)" >/dev/null 2>&1 || true
  docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d postgres -c "CREATE DATABASE $db" >/dev/null
done
echo "  ✓ ${GO_DB} / ${RAG_DB} 已重建"

echo "==> [2/7] 生成配置副本（两侧 Redis 都指向 ${REDIS_HOST}:${DEAD_REDIS_PORT} —— 形态：${REDIS_HOST}）"
sed -e "s/0.0.0.0:19004/0.0.0.0:${GO_RPC_PORT}/" \
    -e "s/Port: \"55432\"/Port: \"${PG_PORT}\"/" \
    -e "s#Host: 127.0.0.1:56379#Host: ${REDIS_HOST}:${DEAD_REDIS_PORT}#" \
    -e "s/Port: 19094/Port: 19195/" \
    -e "s/DBName: \"go_db\"/DBName: \"${GO_DB}\"/" \
    "$HERE/conf/go/user-rpc.yaml" > "$RUN_DIR/conf/go/user-rpc.yaml"
sed -e "s/Port: 18888/Port: ${GO_API_PORT}/" \
    -e "s/Port: 19095/Port: 19199/" \
    -e "s#Host: 127.0.0.1:56379#Host: ${REDIS_HOST}:${DEAD_REDIS_PORT}#" \
    -e "s#127.0.0.1:19004#127.0.0.1:${GO_RPC_PORT}#" \
    "$HERE/conf/go/user-api.yaml" > "$RUN_DIR/conf/go/user-api.yaml"
grep -n "BizRedis" -A2 "$RUN_DIR/conf/go/user-api.yaml" | sed 's/^/    /'

echo "==> [3/7] 起 Go 栈"
for pp in "$GO_RPC_PORT" "$GO_API_PORT" "$RAG_RPC_PORT" "$RAG_API_PORT"; do wait_port_free "$pp" ":${pp}" 30; done
/tmp/difftest/bin/go-user-rpc -f "$RUN_DIR/conf/go/user-rpc.yaml" >"$LOGS/go-rpc.log" 2>&1 & PIDS+=($!)
/tmp/difftest/bin/go-user-api -f "$RUN_DIR/conf/go/user-api.yaml" >"$LOGS/go-api.log" 2>&1 & PIDS+=($!)
wait_port "$GO_RPC_PORT" "go  user-rpc" 90
wait_port "$GO_API_PORT" "go  user-api" 90

echo "==> [4/7] 起 RAG 栈"
env POSTGRES_ADDR=127.0.0.1 POSTGRES_PORT="$PG_PORT" POSTGRES_USER="$PG_USER" POSTGRES_PASSWORD="$PG_PASSWORD" \
    POSTGRES_DB="$RAG_DB" REDIS_ADDR="$REDIS_HOST" REDIS_PORT="$DEAD_REDIS_PORT" \
    NACOS_ADDR=127.0.0.1 NACOS_PORT=1 GRPC_SERVER_PORT="$RAG_RPC_PORT" \
    java -jar "$ROOT/service/users/user/user-rpc/target/user-rpc-0.0.1-SNAPSHOT.jar" >"$LOGS/rag-rpc.log" 2>&1 & PIDS+=($!)
env POSTGRES_ADDR=127.0.0.1 POSTGRES_PORT="$PG_PORT" POSTGRES_USER="$PG_USER" POSTGRES_PASSWORD="$PG_PASSWORD" \
    POSTGRES_DB="$RAG_DB" REDIS_ADDR="$REDIS_HOST" REDIS_PORT="$DEAD_REDIS_PORT" \
    NACOS_ADDR=127.0.0.1 NACOS_PORT=1 \
    java -jar "$ROOT/service/users/user/user-api/target/user-api-0.0.1-SNAPSHOT.jar" \
    --server.port="$RAG_API_PORT" \
    --spring.cloud.nacos.discovery.enabled=false \
    --grpc.client.user-rpc.address="static://127.0.0.1:${RAG_RPC_PORT}" \
    --grpc.client.user-rpc.negotiation-type=plaintext >"$LOGS/rag-api.log" 2>&1 & PIDS+=($!)
wait_port "$RAG_RPC_PORT" "rag user-rpc" 150
wait_port "$RAG_API_PORT" "rag user-api" 150

GO_BASE="http://127.0.0.1:${GO_API_PORT}/usercenter/v1"
RAG_BASE="http://127.0.0.1:${RAG_API_PORT}/user/v1"

pick_token() { python3 -c "
import sys,json
try:
    j=json.load(sys.stdin)
except Exception:
    print(''); raise SystemExit
d=j.get('data') if isinstance(j.get('data'),dict) else {}
print(j.get('token') or d.get('token') or '')
"; }

echo "==> [5/7] 两侧注册 + 登录（不带 Authorization ⇒ 不碰黑名单）"
curl -s -m 20 -o /dev/null -X POST "${GO_BASE}/user/register" \
  -H 'Content-Type: application/json' -d "{\"username\":\"${U}_go\",\"password\":\"pw123456\",\"email\":\"${U}_go@e\"}"
curl -s -m 20 -o /dev/null -X POST "${RAG_BASE}/register" \
  -H 'Content-Type: application/json' -d "{\"username\":\"${U}_rag\",\"password\":\"pw123456\",\"email\":\"${U}_rag@e\"}"

# ⚠️ login 的耗时是「login 没碰 Redis」的旁证：若 login 也 ~3.3s，说明它碰了。
T0=$(python3 -c 'import time;print(time.time())')
RAW_GO=$(curl -s -m 20 -X POST "${GO_BASE}/user/login" \
  -H 'Content-Type: application/json' -d "{\"username\":\"${U}_go\",\"password\":\"pw123456\"}")
T1=$(python3 -c 'import time;print(time.time())')
RAW_RAG=$(curl -s -m 20 -X POST "${RAG_BASE}/login" \
  -H 'Content-Type: application/json' -d "{\"username\":\"${U}_rag\",\"password\":\"pw123456\"}")
T2=$(python3 -c 'import time;print(time.time())')
TOK_GO=$(printf '%s' "$RAW_GO"  | pick_token)
TOK_RAG=$(printf '%s' "$RAW_RAG" | pick_token)
python3 -c "print('  Go  login 耗时 %.3fs' % ($T1-$T0)); print('  RAG login 耗时 %.3fs' % ($T2-$T1))"
if [[ -z "$TOK_GO" || -z "$TOK_RAG" ]]; then
  echo "  FAIL 没拿到 token（go=${#TOK_GO} / rag=${#TOK_RAG}）" >&2
  echo "  Go  login 原文 → ${RAW_GO}" >&2
  echo "  RAG login 原文 → ${RAW_RAG}" >&2
  exit 1
fi
echo "  ✓ token 各就位（go ${#TOK_GO} · rag ${#TOK_RAG}）"

probe_side() {
  local side="$1" token="$2" out="$3"
  if [[ "$side" == "go" ]]; then
    curl -s -m 30 -o "${out}.body" -w "%{http_code} %{time_total}" \
      -H "Authorization: Bearer ${token}" "${GO_BASE}/user/get" > "${out}.meta" 2>&1
  else
    curl -s -m 30 -o "${out}.body" -w "%{http_code} %{time_total}" \
      -X POST -H "Authorization: Bearer ${token}" -H 'Content-Type: application/json' \
      -d '{}' "${RAG_BASE}/get" > "${out}.meta" 2>&1
  fi
}

echo "==> [6/7] 探测 3 轮（每轮双侧并行）—— 首轮 vs 稳态必须分开看"
for i in 1 2 3; do
  ( probe_side go  "$TOK_GO"  "$RUN_DIR/go.$i"  ) &
  PG_PID=$!
  ( probe_side rag "$TOK_RAG" "$RUN_DIR/rag.$i" ) &
  PR_PID=$!
  wait "$PG_PID"; wait "$PR_PID"
  GM=$(cat "$RUN_DIR/go.$i.meta");  GB=$(head -c 120 "$RUN_DIR/go.$i.body" | tr -d '\n')
  RM=$(cat "$RUN_DIR/rag.$i.meta"); RB=$(head -c 120 "$RUN_DIR/rag.$i.body" | tr -d '\n')
  printf '  第%s轮  Go[%s] %s\n'      "$i" "$GM" "$GB"
  printf '          RAG[%s] %s\n'     "$RM" "$RB"
done

echo "==> [7/7] 两侧日志里的 Redis 失败证据（证明确实查了黑名单，而不是压根没查）"
echo "  ── Go 侧 ──"
grep -iE "redis|dial|connect|refused" "$LOGS/go-api.log" | tail -5 | sed 's/^/    /' || true
echo "  ── RAG 侧 ──"
grep -iE "redis|Unable to connect" "$LOGS/rag-api.log" | tail -5 | sed 's/^/    /' || true

cat <<'EOF'

  ═══════════ 判读 ═══════════
  · Go  ：黑名单 EXISTS 失败 ⇒ err != nil ⇒ **fail-open 放行**（200）
          ⇒ 关键看**耗时是否 < 3s**：超过 3s 就撞 Go 的 HTTP 预算（rest/config.go:55 default=3000）
  · RAG ：黑名单查询失败 ⇒ fail-open（JwtAuthInterceptor catch ⇒ exists=null）
          ⇒ 同样关键看耗时：**≥ 3s ⇒ 撞 3s 预算 ⇒ 503 fail-closed**（与 Go 不同形状）
  · 首轮 vs 稳态必须分开：Lettuce 首次连接失败后转入 disconnected 态 ⇒ 后续命令可能被秒拒。
EOF
echo "  日志：$LOGS"
