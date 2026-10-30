#!/usr/bin/env bash
# ════════════════════════════════════════════════════════════════════════════
#  exp-difftest-redis-blacklist — user-api 侧 U-004 的真 Redis 故障注入实验
# ════════════════════════════════════════════════════════════════════════════
#
# 【它回答什么问题】
#   docs/DRIFT/users/user/api.md 的 **UNRESOLVED-004**：
#     「Redis 故障 / Redis 很慢时，黑名单检查失败后两侧的**对外形状**是否一致？」
#   台账原话（§6.x）：「Go 侧 `err == nil && exists` ⇒ **fail-open 放行**；
#   RAG 侧……**最终行为见 U-004**」—— 也就是说 **RAG 侧到底是 fail-open 还是 503，一直没有活体证据**。
#
#   已知静态证据：
#     Go  ：`service/user/user/api/internal/middleware/checkblacklistmiddleware.go:36`
#           `if err == nil && exists { …401… }` ⇒ **err != nil 时不拦截 ⇒ fail-open**
#     RAG  ：`JwtAuthInterceptor.isBlacklisted`（:242-252）`catch (Throwable t) { exists = null; }`
#           ⇒ `return exists != null && exists` ⇒ **fail-open**
#     ⚠️ 两边都写着 fail-open，但那是**读代码读出来的** ⇒ 本脚本要把它变成**活体证据**。
#
# 【实验设计】
#   两套栈（Go / RAG）**共用同一个 Redis 容器** ⇒ 一条故障注入同时打到两侧。
#   三种故障 × 两侧 × 同一个受保护端点，比 **HTTP 状态码 + 响应体 + 耗时**：
#     ① 正常（基线）
#     ② Redis **不可用**（容器停掉 ⇒ connection refused）
#     ③ Redis **很慢**（`CLIENT PAUSE 8000` ⇒ 所有命令阻塞 8 秒）
#
# 【⚠️⚠️ 必须有「被拉黑的 token」对照组，否则结论无效】
#   只用**有效 token** 探测时，「两侧都返回 200」有**两种**可能：
#     (a) 真的 fail-open（Redis 查不到 ⇒ 放行）—— 这是要证明的
#     (b) 两侧**根本没在查黑名单**（查了但功能是坏的 / 没挂上）—— 这是假绿
#   ⇒ 本脚本额外用 `logout` 把 token 拉黑，再拿**同一个 token** 探测：
#     Redis 健康 ⇒ **必须 401**（这才是「黑名单机制真的在跑」的证据）
#     Redis 故障 ⇒ **必须 200**（这才是「故障时被 fail-open 放行」的证据）
#   ⇒ **401 / 200 这对组合才是 U-004 的完整证据**，单看 200 无意义。
#   （2026-10-24 首版脚本缺这个对照组，等于只有半个证据。）
#
# 【⚠️ 为什么自带 Redis 容器】
#   故障注入需要「把 Redis 弄坏」—— 而本机多个会话**共用** Redis 容器，
#   停它会影响别人 ⇒ 本脚本**自建** `uudifftest-redis-bl`，想停就停，零副作用。
#
# 【端口（全部与共享栈错开）】
#   Go  user-rpc :19005 · Go  user-api :18899
#   RAG user-rpc :59211 · RAG user-api :19900
#   PG :55434 · 本脚本专用 Redis :56390
#
# 【⚠️ 踩过的坑（都写在脚本注释里）】
#   1. Go 的路由带前缀 **`/usercenter/v1`**（routes.go 的 `rest.WithPrefix`），
#      RAG 是 `/user/v1` ⇒ 路径本来就不同（已签字偏离）。
#   2. **RAG 的 api 会因 Nacos 注册失败而整个起不来**
#      （`NacosServiceRegistry.register` ⇒ `failFast=true` ⇒ `Client not connected` ⇒
#      `Failed to start bean 'webServerStartStop'`）；
#      而 **rpc 侧只是打条 ERROR 日志继续** ⇒ 同一个 Nacos 配置，两边的失败语义不同。
#      ⇒ 本脚本用 `--spring.cloud.nacos.discovery.enabled=false` 直接关掉注册
#         （gRPC 已走 `static://` 地址，不需要服务发现）。
#   3. `wait_port` 只证明「有东西在监听」⇒ 端口被**上一轮的残留进程**占着时会误判 OK
#      （见 PIT-USERS-SHARED-010）⇒ 起之前先 `wait_port_free`。
#   4. ⚠️ **两侧探测必须并行**，不能串行（2026-10-24 实测踩到）：
#      `CLIENT PAUSE 4000` 的窗口是**全局**的 ⇒ 串行时 Go 侧那一探测**把整个窗口耗完**
#      （实测 Go 耗时 2177ms = 剩余窗口），轮到 RAG 探测时 PAUSE **早已结束**
#      ⇒ RAG 侧拿到的是「Redis 正常」的假数据（实测 0.042s 且**日志里没有**任何失败告警）。
#      ⇒ 本脚本把两个 curl 放后台**同时**发起，再 `wait` 各自的 PID。
#   5. ⚠️ **裸 `wait` 会把服务进程也一起等**（它们永不退出）⇒ 脚本会卡死。
#      ⇒ 只 `wait "$具体PID"`。
#   6. ⚠️ **暖机不能只看「返回 200」**：Lettuce 断连时会**秒拒**（fail-open ⇒ 200），
#      与「连着且正常」在**状态码上完全一样** ⇒ 必须用**被拉黑的 token** 暖机：
#      拿到 **401** 才证明「客户端真的连上了 Redis 且查到了黑名单」。
#
# 【用法】
#   ./exp-difftest-redis-blacklist.sh
set -uo pipefail
# 🔴 RAG 侧 **fail-closed 密钥**（P0 #8）：占位符解析不出来 ⇒ 进程起不来
#    ⇒ 探针「起 RAG 侧」这一步必挂 ⇒ 看起来像产品缺陷，其实是探针自己没给密钥。
#    （2026-10-30 活体对拍轮实测 · 12 个探针全中招 ⇒ 收敛到这一个共享件）
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/rag-secrets.sh"
# 🔴 **PG 容器自给自足**（2026-10-30 活体对拍轮实测）：
#    本脚本**不建** `uudifftest-postgres` ⇒ 隐式依赖别的探针的残留容器
#    ⇒ 批跑器做「逐探针前置清理」时它被删掉 ⇒ 本脚本在 **setup 阶段**就挂
#    ⇒ 假红（报出来像「RAG 起不来」，实际是编排缺陷）。
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/rag-pg.sh"


HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"

RUN_DIR="${RUN_DIR:-/tmp/uublacklist}"
LOGS="$RUN_DIR/logs"
mkdir -p "$RUN_DIR/conf/go" "$LOGS"

PG_CONTAINER="${PG_CONTAINER:-uudifftest-postgres}"
REDIS_CONTAINER="${REDIS_CONTAINER:-uudifftest-redis-bl}"
PG_PORT="${PG_PORT:-55434}"
REDIS_PORT="${REDIS_PORT:-56390}"
PG_USER="${PG_USER:-admin}"
PG_PASSWORD="${PG_PASSWORD:-difftest}"

GO_RPC_PORT="${GO_RPC_PORT:-19005}"
GO_API_PORT="${GO_API_PORT:-18899}"
RAG_RPC_PORT="${RAG_RPC_PORT:-59211}"
RAG_API_PORT="${RAG_API_PORT:-19900}"

GO_DB="uu_bl_go_db"; RAG_DB="uu_bl_rag_db"
U="uu_bl_$(date +%s)_$RANDOM"

PIDS=()
cleanup() {
  for p in "${PIDS[@]:-}"; do kill "$p" 2>/dev/null || true; done
  for p in "${PIDS[@]:-}"; do wait "$p" 2>/dev/null || true; done
  docker start "$REDIS_CONTAINER" >/dev/null 2>&1 || true
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

echo "==> [1/9] 准备容器与库"
docker rm -f "$REDIS_CONTAINER" >/dev/null 2>&1
docker run -d --name "$REDIS_CONTAINER" -p "127.0.0.1:${REDIS_PORT}:6379" redis:7-alpine >/dev/null
docker exec "$REDIS_CONTAINER" redis-cli ping >/dev/null 2>&1 || { sleep 3; docker exec "$REDIS_CONTAINER" redis-cli ping >/dev/null; }
echo "  ✓ 专用 Redis ${REDIS_CONTAINER} :${REDIS_PORT} 就绪"
ensure_pg_container "$PG_CONTAINER" "$PG_PORT" "$PG_USER" "$PG_PASSWORD"
wait_pg_ready "$PG_CONTAINER" "$PG_USER" || exit 2
reset_pg_dbs "$PG_CONTAINER" "$PG_USER" "$GO_DB" "$RAG_DB" || exit 2

echo "==> [2/9] 生成配置副本"
sed -e "s/0.0.0.0:19004/0.0.0.0:${GO_RPC_PORT}/" \
    -e "s/Port: \"55432\"/Port: \"${PG_PORT}\"/" \
    -e "s#Host: 127.0.0.1:56379#Host: 127.0.0.1:${REDIS_PORT}#" \
    -e "s/Port: 19094/Port: 19195/" \
    -e "s/DBName: \"go_db\"/DBName: \"${GO_DB}\"/" \
    "$HERE/conf/go/user-rpc.yaml" > "$RUN_DIR/conf/go/user-rpc.yaml"
sed -e "s/Port: 18888/Port: ${GO_API_PORT}/" \
    -e "s/Port: 19095/Port: 19199/" \
    -e "s#Host: 127.0.0.1:56379#Host: 127.0.0.1:${REDIS_PORT}#" \
    -e "s#127.0.0.1:19004#127.0.0.1:${GO_RPC_PORT}#" \
    "$HERE/conf/go/user-api.yaml" > "$RUN_DIR/conf/go/user-api.yaml"

echo "==> [3/9] 起 Go 栈（user-rpc :${GO_RPC_PORT} / user-api :${GO_API_PORT}）"
for pp in "$GO_RPC_PORT" "$GO_API_PORT" "$RAG_RPC_PORT" "$RAG_API_PORT"; do wait_port_free "$pp" ":${pp}" 30; done
/tmp/difftest/bin/go-user-rpc -f "$RUN_DIR/conf/go/user-rpc.yaml" >"$LOGS/go-rpc.log" 2>&1 & PIDS+=($!)
/tmp/difftest/bin/go-user-api -f "$RUN_DIR/conf/go/user-api.yaml" >"$LOGS/go-api.log" 2>&1 & PIDS+=($!)
wait_port "$GO_RPC_PORT" "go  user-rpc" 90
wait_port "$GO_API_PORT" "go  user-api" 90

echo "==> [4/9] 起 RAG 栈（user-rpc :${RAG_RPC_PORT} / user-api :${RAG_API_PORT}）"
env POSTGRES_ADDR=127.0.0.1 POSTGRES_PORT="$PG_PORT" POSTGRES_USER="$PG_USER" POSTGRES_PASSWORD="$PG_PASSWORD" \
    POSTGRES_DB="$RAG_DB" REDIS_ADDR=127.0.0.1 REDIS_PORT="$REDIS_PORT" \
    NACOS_ADDR=127.0.0.1 NACOS_PORT=1 GRPC_SERVER_PORT="$RAG_RPC_PORT" \
    java -jar "$ROOT/service/users/user/user-rpc/target/user-rpc-0.0.1-SNAPSHOT.jar" >"$LOGS/rag-rpc.log" 2>&1 & PIDS+=($!)
env POSTGRES_ADDR=127.0.0.1 POSTGRES_PORT="$PG_PORT" POSTGRES_USER="$PG_USER" POSTGRES_PASSWORD="$PG_PASSWORD" \
    POSTGRES_DB="$RAG_DB" REDIS_ADDR=127.0.0.1 REDIS_PORT="$REDIS_PORT" \
    NACOS_ADDR=127.0.0.1 NACOS_PORT=1 \
    java -jar "$ROOT/service/users/user/user-api/target/user-api-0.0.1-SNAPSHOT.jar" \
    --server.port="$RAG_API_PORT" \
    --spring.cloud.nacos.discovery.enabled=false \
    --grpc.client.user-rpc.address="static://127.0.0.1:${RAG_RPC_PORT}" \
    --grpc.client.user-rpc.negotiation-type=plaintext >"$LOGS/rag-api.log" 2>&1 & PIDS+=($!)
wait_port "$RAG_RPC_PORT" "rag user-rpc" 150
wait_port "$RAG_API_PORT" "rag user-api" 150

# ⚠️ 两侧的 HTTP path **本来就不同**（已签字偏离，见 Intentional_Deviations）：
#     Go  = /usercenter/v1/user/...   （routes.go 的 rest.WithPrefix("/usercenter/v1")）
#     RAG = /user/v1/...
GO_BASE="http://127.0.0.1:${GO_API_PORT}/usercenter/v1"
RAG_BASE="http://127.0.0.1:${RAG_API_PORT}/user/v1"

# token 可能裹在 data 里（两侧的响应包装不同）⇒ 顶层与 data 都取
pick_token() { python3 -c "
import sys,json
try:
    j=json.load(sys.stdin)
except Exception:
    print(''); raise SystemExit
d=j.get('data') if isinstance(j.get('data'),dict) else {}
print(j.get('token') or d.get('token') or '')
"; }

echo "==> [5/9] 两侧各注册 + 登录 ×2，造出「有效 token」与「被拉黑的 token」"
REG_GO=$(curl -s -m 10 -X POST "${GO_BASE}/user/register" \
  -H 'Content-Type: application/json' -d "{\"username\":\"${U}_go\",\"password\":\"pw123456\",\"email\":\"${U}_go@e\"}")
echo "  Go  register → ${REG_GO}"
REG_RAG=$(curl -s -m 10 -X POST "${RAG_BASE}/register" \
  -H 'Content-Type: application/json' -d "{\"username\":\"${U}_rag\",\"password\":\"pw123456\",\"email\":\"${U}_rag@e\"}")
echo "  RAG register → ${REG_RAG}"

RAW_GO=$(curl -s -m 10 -X POST "${GO_BASE}/user/login" \
  -H 'Content-Type: application/json' -d "{\"username\":\"${U}_go\",\"password\":\"pw123456\"}")
RAW_RAG=$(curl -s -m 10 -X POST "${RAG_BASE}/login" \
  -H 'Content-Type: application/json' -d "{\"username\":\"${U}_rag\",\"password\":\"pw123456\"}")
TOK_GO_BL=$(printf '%s' "$RAW_GO"  | pick_token)
TOK_RAG_BL=$(printf '%s' "$RAW_RAG" | pick_token)
if [[ -z "$TOK_GO_BL" || -z "$TOK_RAG_BL" ]]; then
  echo "  FAIL 没拿到 token（go=${#TOK_GO_BL} / rag=${#TOK_RAG_BL}）" >&2
  echo "  Go  login 原文 → ${RAW_GO}" >&2
  echo "  RAG login 原文 → ${RAW_RAG}" >&2
  exit 1
fi

# ⚠️ 用 logout 把上面这两个 token **拉黑**：
#    Go  ：`POST /usercenter/v1/user/logout`（routes.go:52-55，POST）
#          logoutlogic.go 从 ctx 的 jwt_token 取 token ⇒ Authorization 头即可
#    RAG ：`POST /user/v1/logout`（UserController.java:153）
#          UserLogoutService.java:75 从 request 属性 jwt_token 取 ⇒ 同样靠 Authorization 头
#    两侧的 rpc logout 都写 **`user:jwt_blacklist:<token>`**（1:1 对齐）
echo "  ── 拉黑（logout）──"
OUT_GO=$(curl -s -m 10 -X POST "${GO_BASE}/user/logout" \
  -H "Authorization: Bearer ${TOK_GO_BL}" -H 'Content-Type: application/json' -d '{}')
echo "    Go  logout → ${OUT_GO}"
OUT_RAG=$(curl -s -m 10 -X POST "${RAG_BASE}/logout" \
  -H "Authorization: Bearer ${TOK_RAG_BL}" -H 'Content-Type: application/json' -d '{}')
echo "    RAG logout → ${OUT_RAG}"

# 再登录一次拿「有效 token」（logout 不删用户，只是把 token 拉黑）
RAW_GO2=$(curl -s -m 10 -X POST "${GO_BASE}/user/login" \
  -H 'Content-Type: application/json' -d "{\"username\":\"${U}_go\",\"password\":\"pw123456\"}")
RAW_RAG2=$(curl -s -m 10 -X POST "${RAG_BASE}/login" \
  -H 'Content-Type: application/json' -d "{\"username\":\"${U}_rag\",\"password\":\"pw123456\"}")
TOK_GO=$(printf '%s' "$RAW_GO2"  | pick_token)
TOK_RAG=$(printf '%s' "$RAW_RAG2" | pick_token)
if [[ -z "$TOK_GO" || -z "$TOK_RAG" ]]; then
  echo "  FAIL 第二次 login 没拿到 token（go=${#TOK_GO} / rag=${#TOK_RAG}）" >&2
  exit 1
fi
echo "  ✓ 有效 token / 被拉黑 token 各一对（go ${#TOK_GO}/${#TOK_GO_BL} · rag ${#TOK_RAG}/${#TOK_RAG_BL}）"
# 确认黑名单 key 真的写进 Redis 了（避免「logout 根本没生效 ⇒ 对照组无意义」）
for k in "user:jwt_blacklist:${TOK_GO_BL}" "user:jwt_blacklist:${TOK_RAG_BL}"; do
  n=$(docker exec "$REDIS_CONTAINER" redis-cli exists "$k" 2>/dev/null | tr -d '\r')
  echo "    Redis exists(user:jwt_blacklist:…) = ${n}"
done

# 单侧探测：$1=side(go|rag)  $2=token  $3=输出前缀
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

# 双侧**并行**探测：$1=label  $2=token_pair(valid|bl)
probe_both() {
  local label="$1" pair="$2"
  local tok_go="$TOK_GO" tok_rag="$TOK_RAG"
  if [[ "$pair" == "bl" ]]; then
    tok_go="$TOK_GO_BL"; tok_rag="$TOK_RAG_BL"
  fi
  echo "  ── $label ──"
  probe_side go  "$tok_go"  /tmp/_bl_go  & local pid_go=$!
  probe_side rag "$tok_rag" /tmp/_bl_rag & local pid_rag=$!
  wait "$pid_go"; wait "$pid_rag"
  local gc="?" gt="?" rc="?" rt="?"
  if [[ -s /tmp/_bl_go.meta  ]]; then read -r gc gt < /tmp/_bl_go.meta  || true; fi
  if [[ -s /tmp/_bl_rag.meta ]]; then read -r rc rt < /tmp/_bl_rag.meta || true; fi
  echo "    Go  HTTP=${gc}  耗时=${gt}s  body=$(head -c 150 /tmp/_bl_go.body 2>/dev/null)"
  echo "    RAG HTTP=${rc}  耗时=${rt}s  body=$(head -c 150 /tmp/_bl_rag.body 2>/dev/null)"
}

echo
echo "==> [6/9] 场景①：Redis 正常（基线）"
probe_both "①a Redis 正常 · 有效 token（期望两侧 200）" valid
probe_both "①b Redis 正常 · 被拉黑 token（期望两侧 401）← 对照组" bl

echo "==> [7/9] 场景②：Redis **不可用**（容器停掉）"
docker stop "$REDIS_CONTAINER" >/dev/null
sleep 2
probe_both "②a Redis 不可用 · 有效 token（期望两侧 200 = fail-open）" valid
probe_both "②b Redis 不可用 · 被拉黑 token（期望两侧 200 = fail-open，不是 401）" bl
docker start "$REDIS_CONTAINER" >/dev/null
sleep 3
docker exec "$REDIS_CONTAINER" redis-cli ping >/dev/null 2>&1 && echo "    （Redis 已恢复）"

echo "==> [8/9] 暖机：证明两侧客户端**真的重连上了**"
echo "    ⚠️ 必须用「被拉黑的 token」当暖机探针：拿到 401 才说明查到了 Redis；"
echo "       用有效 token 只能拿到 200，而「断连秒拒」也是 200 ⇒ 区分不了。"
probe_both "暖机 · 被拉黑 token（期望两侧 401 = 真连上了）" bl
probe_both "暖机 · 有效 token（期望两侧 200）" valid
sleep 2

echo "==> [9/9] 场景③：Redis **很慢**（CLIENT PAUSE 8000 · 双侧并行探测）"
# ⚠️ PAUSE 必须**先生效再探测**，且窗口要足够长 ⇒ 两侧**同时**落在窗口里。
#    `CLIENT PAUSE 8000` 这条命令自己会阻塞 ⇒ 放后台发。
( docker exec "$REDIS_CONTAINER" redis-cli CLIENT PAUSE 8000 >/dev/null 2>&1 ) &
PAUSE_PID=$!
sleep 2   # 等 PAUSE 真正生效（此刻所有 Redis 命令都会被阻塞，剩余窗口 ~6s）
probe_both "③ Redis 很慢 · 有效 token" valid
wait "$PAUSE_PID" 2>/dev/null || true

echo
echo "  ⇒ 判定要点："
echo "    ①b 必须 401 ⇒ 证明黑名单机制真在跑（否则 ② 的 200 是假绿）"
echo "    ②b 必须 200 ⇒ 证明 Redis 故障时是 fail-open 放行（不是 401 误拦，也不是 503）"
echo "    ③ 的两侧状态码必须相同（这是 3s 预算与 Redis 超时谁先触发的正面回答）"
