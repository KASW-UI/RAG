#!/usr/bin/env bash
#
# exp-userapi-redis-dns-connect-timeout — DRIFT-USERAPI-015 的**候选②先验**
#
# ════════════════════════════════════════════════════════════════════════════
# 【它回答什么问题】
#   docs/DRIFT/users/user/api.md 的 **DRIFT-USERAPI-015**：
#     Redis **主机名解析不出来**（生产默认形态 ${REDIS_ADDR:redis}）时
#     Go 恒 200 fail-open（~1.8s），RAG 在 **200/503 之间摇摆**（~2.93~3.03s）。
#
#   成因（已核到属性消费点，不是猜）：
#     · `spring.data.redis.timeout: 3s`
#         → DataRedisProperties.getTimeout()
#         → LettuceClientConfigurationBuilder.commandTimeout(3s)
#       ⇒ 观察到的 ~3s 失败耗时来自**命令超时**，不是连接超时；
#     · DNS 解析失败时 Lettuce 仍在建连 ⇒ 命令被排队等连接
#       ⇒ 直到 commandTimeout 到点才失败 ⇒ 与 api 侧 3s HTTP 预算**竞速**
#       ⇒ 偶尔踩线 ⇒ 200(fail-open) / 503(预算耗尽) 摇摆。
#
#   **候选②**：`spring.data.redis.connect-timeout=1s`
#     → DataRedisProperties.getConnectTimeout()
#     → LettuceConnectionConfiguration.createClientOptions()
#     → SocketOptions.builder().connectTimeout(1s) → ClientOptions
#     ⇒ 性质：**把连接建立的失败上限压到 1s** ⇒ 命令更早失败 ⇒ 落回 3s 预算内
#       ⇒ 预期恒 200 fail-open（与 Go 同形）。
#
# 【本脚本做什么】—— 三个变体，同一形态（redis 主机名不可解析），同一次运行：
#   A. Go  api（真源基线）      —— 预期恒 200 / ~1.8s
#   B. RAG api（现配置基线）    —— 预期 200/503 摇摆 / ~2.9~3.0s
#   C. RAG api（+connect-timeout=1s）—— 本脚本要测的候选②
#
# 【为什么两个 RAG 变体要「顺序跑」而不是同时跑】
#   同时跑 ⇒ 两边的连接建立/超时**互相抢 CPU 与事件循环** ⇒ 延迟数据不可比。
#   顺序跑 ⇒ 每个变体独占栈 ⇒ 测到的差异只能归因于那个变体自己。
#
# 【为什么 token 可以跨变体复用】
#   JWT 自包含且带过期时间；黑名单查询是**只读**的；
#   ⇒ 注册/登录只做一次（登录本身不碰 Redis：Go 侧 checkblacklistmiddleware.go:25-28
#     无 Authorization 头即放行 ⇒ RAG 侧同理）⇒ 三个变体用同一个 token。
#
# 【⚠️ 前提自检（任一不成立就退出，别测）】
#   ① 主机名 `redis` 在本机**必须解析失败**（否则测的是「能连上」，不是本条）；
#   ② 死端口**必须没人监听**（否则不是「连不上」）；
#   ③ PG 容器必须在跑（要真库注册/登录）。
#
# 【端口（与共享栈错开）】
#   Go  user-rpc :19005 · Go  user-api :18899
#   RAG user-rpc :59211 · RAG user-api :19900
#   PG :55434 · **假 Redis（没人监听）:56391**
#
# 【2026-10-26 实测结论（已回写台账，此处只留指针）】
#   FORM=dns        ⇒ 两侧都 200（同形）⇒ 原始那条「RAG 200/503 摇摆」复现不出来
#   FORM=blackhole  ⇒ 两侧都 503（同形）⇒ 候选②把它变成 RAG 200 / Go 503 ⇒ **反向差异**
#   ⇒ 候选②（connect-timeout=1s）**建议否决**；015 降级为「条件性成立 · 证据不足」。
#
# 【jar 级 A/B（复现 014 修法之前的行为）】
#   git worktree add --detach <路径> ea8f01e^
#   cd <路径> && mvn -o -pl service/users/user/user-api -am package -DskipTests
#   RAG_API_JAR=<该 worktree 的 user-api jar> ./exp-userapi-redis-dns-connect-timeout.sh
#
# 【用法】
#   ./exp-userapi-redis-dns-connect-timeout.sh              # 默认 FORM=dns
#   FORM=blackhole ./exp-userapi-redis-dns-connect-timeout.sh
# ════════════════════════════════════════════════════════════════════════════
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"

RUN_DIR="${RUN_DIR:-/tmp/uu015ct}"
LOGS="$RUN_DIR/logs"
mkdir -p "$RUN_DIR/conf/go" "$LOGS"

PG_CONTAINER="${PG_CONTAINER:-uudifftest-postgres}"
PG_PORT="${PG_PORT:-55434}"
PG_USER="${PG_USER:-admin}"
PG_PASSWORD="${PG_PASSWORD:-difftest}"
DEAD_REDIS_PORT="${DEAD_REDIS_PORT:-56391}"
# ⚠️ 必须是**主机名**（不是 IP）：本条就是「主机名解析失败」这一形态。
#
# 🔴🔴 **每个变体必须各用一个「全新」主机名**（2026-10-26 第二次跑的教训）：
#   第一次跑（三个变体共用 `redis`）⇒ **基线没有复现 200/503 摇摆**，全 200。
#   根因（事后核实）：本机此前已反复解析 `redis` 失败 ⇒ **DNS 负缓存命中**
#   ⇒ 解析立即失败（0.29s）⇒ 根本没走到「连接建立 → 命令排队 → commandTimeout 3s」
#   ⇒ 而 2026-10-24 第十轮那次是**首次解析**（无负缓存）⇒ 踩到了 3s。
#   ⇒ 复现条件必须是「**首次解析**」⇒ 每次运行用**新的随机主机名**。
#   ⇒ 同时，每个变体用**各自**的新名字：基线变体跑完会给它的名字**留下负缓存**
#      ⇒ 候选变体若复用同一名字，条件就变了（这正是第一次跑的第二个坑）。
# FORM=dns        —— 主机名解析失败（**快速失败**）⇒ 名义条件
# FORM=blackhole  —— IP 直指「SYN 被丢」的地址 ⇒ 连接建立**确定性卡住**
#                    （2026-10-26 实测：本机对 10.255.255.1 / 192.0.2.1 / 198.51.100.1
#                      / 203.0.113.1 全部 6s 超时、无路由错误 ⇒ 卡得住、可复现）
FORM="${FORM:-dns}"
BLACKHOLE_IP="${BLACKHOLE_IP:-10.255.255.1}"
HOST_TAG="$(date +%s)$RANDOM"
if [[ "$FORM" == "blackhole" ]]; then
  GO_HOST="$BLACKHOLE_IP"; B_HOST="$BLACKHOLE_IP"; C_HOST="$BLACKHOLE_IP"; RPC_HOST="$BLACKHOLE_IP"
else
  GO_HOST="${GO_HOST:-redis${HOST_TAG}g}"
  B_HOST="${B_HOST:-redis${HOST_TAG}b}"
  C_HOST="${C_HOST:-redis${HOST_TAG}c}"
  RPC_HOST="${RPC_HOST:-redis${HOST_TAG}r}"
fi

GO_RPC_PORT="${GO_RPC_PORT:-19005}"
GO_API_PORT="${GO_API_PORT:-18899}"
RAG_RPC_PORT="${RAG_RPC_PORT:-59211}"
RAG_API_PORT="${RAG_API_PORT:-19900}"

# 候选②的取值（秒）。改成 2s 就能测「1s 是不是刚好」。
CONNECT_TIMEOUT="${CONNECT_TIMEOUT:-1s}"
ROUNDS="${ROUNDS:-6}"

# jar 路径可覆盖 ⇒ 用来做 **jar 级 A/B**（例如拿「014 修法之前」的 jar 复现 015 摇摆）
RAG_API_JAR="${RAG_API_JAR:-$ROOT/service/users/user/user-api/target/user-api-0.0.1-SNAPSHOT.jar}"
RAG_RPC_JAR="${RAG_RPC_JAR:-$ROOT/service/users/user/user-rpc/target/user-rpc-0.0.1-SNAPSHOT.jar}"

GO_DB="uu015_go_db"; RAG_DB="uu015_rag_db"
U="uu015_$(date +%s)_$RANDOM"

PIDS=()
cleanup() {
  for p in "${PIDS[@]:-}"; do kill "$p" 2>/dev/null || true; done
  for p in "${PIDS[@]:-}"; do wait "$p" 2>/dev/null || true; done
}
trap cleanup EXIT

wait_port() {
  local port="$1" name="$2" tries="${3:-120}"
  for _ in $(seq 1 "$tries"); do
    lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1 && { echo "  OK   ${name} 已监听 :${port}"; return 0; }
    sleep 1
  done
  echo "  FAIL ${name} 超时 (:${port})" >&2; return 1
}
wait_port_free() {
  local port="$1" name="$2" tries="${3:-30}"
  for _ in $(seq 1 "$tries"); do
    lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1 || { echo "  OK   ${name} 端口 :${port} 已释放"; return 0; }
    sleep 1
  done
  echo "  FAIL ${name} 端口 :${port} 仍被占用" >&2; return 1
}

echo "==> [0/7] 前提自检 + 构件检查"
if [[ "$FORM" == "blackhole" ]]; then
  echo "  ✓ 形态 = blackhole（IP ${BLACKHOLE_IP} · SYN 被丢 ⇒ 连接建立确定性卡住）"
  python3 - "$BLACKHOLE_IP" <<'PYEOF'
import socket, sys
s = socket.socket(); s.settimeout(4)
try:
    s.connect((sys.argv[1], 6379)); print("  FAIL 该 IP 可连上 ⇒ 黑洞形态不成立"); sys.exit(2)
except socket.timeout:
    print("  ✓ 实测该 IP 连接 4s 内不返回（卡住形态成立）")
except OSError as e:
    print(f"  FAIL 该 IP 立即报错（{e}）⇒ 不是卡住，是快速失败"); sys.exit(2)
finally:
    s.close()
PYEOF
else
  for h in "$GO_HOST" "$B_HOST" "$C_HOST" "$RPC_HOST"; do
    if getent hosts "$h" >/dev/null 2>&1; then
      echo "  FAIL 主机名 ${h} 在本机可解析 ⇒ 本条形态不成立" >&2; exit 2
    fi
  done
  echo "  ✓ 四个主机名均解析失败（本次形态成立，且都是**首次解析**、无负缓存）"
fi
echo "    Go=${GO_HOST} · 变体B=${B_HOST} · 变体C=${C_HOST} · rpc=${RPC_HOST}"
if lsof -nP -iTCP:"$DEAD_REDIS_PORT" -sTCP:LISTEN >/dev/null 2>&1; then
  echo "  FAIL 端口 ${DEAD_REDIS_PORT} 已被监听 ⇒ 本场景不成立" >&2; exit 2
fi
echo "  ✓ 端口 ${DEAD_REDIS_PORT} 确认无人监听"
for b in /tmp/difftest/bin/go-user-rpc /tmp/difftest/bin/go-user-api; do
  [[ -x "$b" ]] || { echo "  FAIL 缺 Go 二进制 ${b}" >&2; exit 2; }
done
for j in "$RAG_RPC_JAR" "$RAG_API_JAR"; do
  [[ -f "$j" ]] || { echo "  FAIL 缺 jar $j" >&2; exit 2; }
done
docker ps --format '{{.Names}}' | grep -qx "$PG_CONTAINER" || {
  echo "  FAIL PG 容器 ${PG_CONTAINER} 不在运行" >&2; exit 2; }
echo "  ✓ 构件齐全"
echo "    RAG api jar = ${RAG_API_JAR}"
echo "    RAG rpc jar = ${RAG_RPC_JAR}"

echo "==> [1/7] 重建两侧数据库"
for db in "$GO_DB" "$RAG_DB"; do
  docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d postgres -c "DROP DATABASE IF EXISTS $db WITH (FORCE)" >/dev/null 2>&1 || true
  docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d postgres -c "CREATE DATABASE $db" >/dev/null
done
echo "  ✓ ${GO_DB} / ${RAG_DB} 已重建"

echo "==> [2/7] 生成 Go 配置副本（Go 侧 Redis 指向 ${GO_HOST}:${DEAD_REDIS_PORT}）"
sed -e "s/0.0.0.0:19004/0.0.0.0:${GO_RPC_PORT}/" \
    -e "s/Port: \"55432\"/Port: \"${PG_PORT}\"/" \
    -e "s#Host: 127.0.0.1:56379#Host: ${GO_HOST}:${DEAD_REDIS_PORT}#" \
    -e "s/Port: 19094/Port: 19195/" \
    -e "s/DBName: \"go_db\"/DBName: \"${GO_DB}\"/" \
    "$HERE/conf/go/user-rpc.yaml" > "$RUN_DIR/conf/go/user-rpc.yaml"
sed -e "s/Port: 18888/Port: ${GO_API_PORT}/" \
    -e "s/Port: 19095/Port: 19199/" \
    -e "s#Host: 127.0.0.1:56379#Host: ${GO_HOST}:${DEAD_REDIS_PORT}#" \
    -e "s#127.0.0.1:19004#127.0.0.1:${GO_RPC_PORT}#" \
    "$HERE/conf/go/user-api.yaml" > "$RUN_DIR/conf/go/user-api.yaml"
grep -n "BizRedis" -A2 "$RUN_DIR/conf/go/user-api.yaml" | sed 's/^/    /'

echo "==> [3/7] 起 Go 栈 + RAG user-rpc + RAG user-api（变体 B · 现配置基线）"
for pp in "$GO_RPC_PORT" "$GO_API_PORT" "$RAG_RPC_PORT" "$RAG_API_PORT"; do wait_port_free "$pp" ":${pp}" 30; done
/tmp/difftest/bin/go-user-rpc -f "$RUN_DIR/conf/go/user-rpc.yaml" >"$LOGS/go-rpc.log" 2>&1 & PIDS+=($!)
/tmp/difftest/bin/go-user-api -f "$RUN_DIR/conf/go/user-api.yaml" >"$LOGS/go-api.log" 2>&1 & PIDS+=($!)
wait_port "$GO_RPC_PORT" "go  user-rpc" 90
wait_port "$GO_API_PORT" "go  user-api" 90
env POSTGRES_ADDR=127.0.0.1 POSTGRES_PORT="$PG_PORT" POSTGRES_USER="$PG_USER" POSTGRES_PASSWORD="$PG_PASSWORD" \
    POSTGRES_DB="$RAG_DB" REDIS_ADDR="$RPC_HOST" REDIS_PORT="$DEAD_REDIS_PORT" \
    NACOS_ADDR=127.0.0.1 NACOS_PORT=1 GRPC_SERVER_PORT="$RAG_RPC_PORT" \
    java -jar "$RAG_RPC_JAR" >"$LOGS/rag-rpc.log" 2>&1 & PIDS+=($!)
wait_port "$RAG_RPC_PORT" "rag user-rpc" 150
env POSTGRES_ADDR=127.0.0.1 POSTGRES_PORT="$PG_PORT" POSTGRES_USER="$PG_USER" POSTGRES_PASSWORD="$PG_PASSWORD" \
    POSTGRES_DB="$RAG_DB" REDIS_ADDR="$B_HOST" REDIS_PORT="$DEAD_REDIS_PORT" \
    NACOS_ADDR=127.0.0.1 NACOS_PORT=1 \
    java -jar "$RAG_API_JAR" \
    --server.port="$RAG_API_PORT" \
    --spring.cloud.nacos.discovery.enabled=false \
    --grpc.client.user-rpc.address="static://127.0.0.1:${RAG_RPC_PORT}" \
    --grpc.client.user-rpc.negotiation-type=plaintext >"$LOGS/rag-api-B.log" 2>&1 & RAG_API_PID=$!
PIDS+=($RAG_API_PID)
wait_port "$RAG_API_PORT" "rag user-api (变体B)" 150

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

echo "==> [4/7] 注册 + 登录（不带 Authorization ⇒ 不碰黑名单）"
curl -s -m 20 -o /dev/null -X POST "${GO_BASE}/user/register" \
  -H 'Content-Type: application/json' -d "{\"username\":\"${U}_go\",\"password\":\"pw123456\",\"email\":\"${U}_go@e\"}"
curl -s -m 20 -o /dev/null -X POST "${RAG_BASE}/register" \
  -H 'Content-Type: application/json' -d "{\"username\":\"${U}_rag\",\"password\":\"pw123456\",\"email\":\"${U}_rag@e\"}"
RAW_GO=$(curl -s -m 20 -X POST "${GO_BASE}/user/login" \
  -H 'Content-Type: application/json' -d "{\"username\":\"${U}_go\",\"password\":\"pw123456\"}")
RAW_RAG=$(curl -s -m 20 -X POST "${RAG_BASE}/login" \
  -H 'Content-Type: application/json' -d "{\"username\":\"${U}_rag\",\"password\":\"pw123456\"}")
TOK_GO=$(printf '%s' "$RAW_GO"  | pick_token)
TOK_RAG=$(printf '%s' "$RAW_RAG" | pick_token)
if [[ -z "$TOK_GO" || -z "$TOK_RAG" ]]; then
  echo "  FAIL 没拿到 token" >&2; echo "  Go  login 原文 → ${RAW_GO}" >&2; echo "  RAG login 原文 → ${RAW_RAG}" >&2
  exit 1
fi
echo "  ✓ token 各就位（go ${#TOK_GO} · rag ${#TOK_RAG}）"

probe_go() {
  local out="$1"
  curl -s -m 30 -o "${out}.body" -w "%{http_code} %{time_total}" \
    -H "Authorization: Bearer ${TOK_GO}" "${GO_BASE}/user/get" > "${out}.meta" 2>&1
}
probe_rag() {
  local out="$1"
  curl -s -m 30 -o "${out}.body" -w "%{http_code} %{time_total}" \
    -X POST -H "Authorization: Bearer ${TOK_RAG}" -H 'Content-Type: application/json' \
    -d '{}' "${RAG_BASE}/get" > "${out}.meta" 2>&1
}

run_rounds() {
  # $1 = 标签 · $2 = go|rag
  local tag="$1" side="$2"
  local codes="" times=""
  for i in $(seq 1 "$ROUNDS"); do
    local out="$RUN_DIR/${tag}.${i}"
    if [[ "$side" == "go" ]]; then probe_go "$out"; else probe_rag "$out"; fi
    local meta; meta=$(cat "$out.meta")
    local code; code=$(printf '%s' "$meta" | awk '{print $1}')
    local tm;   tm=$(printf '%s' "$meta" | awk '{print $2}')
    codes="${codes} ${code}"
    times="${times} ${tm}"
    printf '    %-14s 第%s轮  code=%-4s %ss\n' "$tag" "$i" "$code" "$tm"
  done
  echo "  ${tag} 汇总：codes =${codes}"
  echo "  ${tag} 汇总：耗时 =${times}"
}

echo "==> [5/7] 变体 A：Go api（真源基线）"
run_rounds "A-go" go

echo "==> [6/7] 变体 B：RAG api（现配置基线 · 已在 [3/7] 起好）"
run_rounds "B-rag-baseline" rag
kill "$RAG_API_PID" 2>/dev/null || true
wait "$RAG_API_PID" 2>/dev/null || true
wait_port_free "$RAG_API_PORT" "rag user-api (变体B)" 60

echo "==> [7/7] 变体 C：RAG api + spring.data.redis.connect-timeout=${CONNECT_TIMEOUT}"
env POSTGRES_ADDR=127.0.0.1 POSTGRES_PORT="$PG_PORT" POSTGRES_USER="$PG_USER" POSTGRES_PASSWORD="$PG_PASSWORD" \
    POSTGRES_DB="$RAG_DB" REDIS_ADDR="$C_HOST" REDIS_PORT="$DEAD_REDIS_PORT" \
    NACOS_ADDR=127.0.0.1 NACOS_PORT=1 \
    java -jar "$RAG_API_JAR" \
    --server.port="$RAG_API_PORT" \
    --spring.cloud.nacos.discovery.enabled=false \
    --grpc.client.user-rpc.address="static://127.0.0.1:${RAG_RPC_PORT}" \
    --grpc.client.user-rpc.negotiation-type=plaintext \
    "--spring.data.redis.connect-timeout=${CONNECT_TIMEOUT}" >"$LOGS/rag-api-C.log" 2>&1 & RAG_API_PID2=$!
PIDS+=($RAG_API_PID2)
wait_port "$RAG_API_PORT" "rag user-api (变体C)" 150
run_rounds "C-rag-ct${CONNECT_TIMEOUT}" rag

echo "==> 日志里的 Redis 失败证据（证明确实查了黑名单，不是压根没查）"
echo "  ── Go 侧 ──"
grep -iE "redis|dial|connect|refused" "$LOGS/go-api.log" | tail -4 | sed 's/^/    /' || true
echo "  ── RAG 变体 B ──"
grep -iE "redis|connect|Unable to connect|timeout" "$LOGS/rag-api-B.log" | tail -4 | sed 's/^/    /' || true
echo "  ── RAG 变体 C ──"
grep -iE "redis|connect|Unable to connect|timeout" "$LOGS/rag-api-C.log" | tail -4 | sed 's/^/    /' || true

cat <<'EOF'

  ═══════════ 判读（2026-10-26 实测已把口径写死，照此读）═══════════
  FORM=dns（主机名解析失败 · 快速失败）：
    · Go  ≈ 1.75~1.80s（前几轮，5 次拨号）→ 之后连接池记住失败 ≈ 0.10/0.07/0.002s
    · RAG ≈ 0.09~0.11s ⇒ **两侧都 200 fail-open ⇒ 同形 ⇒ 不是漂移**
    · 候选② ≈ 0.10~0.12s（偶有 1~2.4s）⇒ **本形态下无区分度**

  FORM=blackhole（连接建立确定性卡住）：
    · Go  = **503** ×4（3.00s，撞 HTTP 预算）
    · RAG = **503** ×4（3.00s，撞 HTTP 预算）⇒ **两侧同形 ⇒ 也不是漂移**
    · 候选② = **200** ×4（1.01~1.71s）⇒ ⚠️ **凭空造出反向差异（RAG 200 vs Go 503）**

  ⇒ **结论（已回写 docs/DRIFT/users/user/api.md 的 §7 DRIFT-USERAPI-015）**：
    1) 台账那条「RAG 200/503 摇摆」**复现不出来** ⇒ 定性降级为「条件性成立 · 证据不足」；
    2) 候选② **确实生效**（Boot 消费点：LettuceConnectionConfiguration.createClientOptions
       → SocketOptions.builder().connectTimeout），但**生效 ≠ 该做** ⇒ 建议**否决**。

  ⚠️ 这是**先验**，不是落地：它只回答「候选② 在这两种形态下会怎样」。
EOF
echo "  日志：$LOGS"
