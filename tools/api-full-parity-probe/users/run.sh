#!/usr/bin/env bash
# ════════════════════════════════════════════════════════════════════════════
#  users 域 **HTTP 层**双侧活体对拍探针（Go 真源 ↔ RAG）
#
#  定位：补 `docs/RoundPlan/Round-0.md` **T-0.4** —— P0 #4 的「探针覆盖清单」
#        必须按**实跑**结果改写 ⇒ 本探针是其中 users 域那一格的**可复算**入口。
#
#  ⚠️ **为什么不是 `tools/difftest/run.sh`**：
#    `tools/difftest/**` 是**主线共享面**（`check-*.py` 会动 §6.3 守门计数口径 = B14 缺陷）
#    ⇒ **一个字都不改**。本探针是**域内副本**，落在
#    `tools/api-full-parity-probe/users/`（Round-0 **T-0.6** 的统一口径：
#    按探针种类分层 `tools/<kind>/<域>/` ⇒ 域内探针不混进 `tools/difftest/`）。
#
#  三条纪律（改脚本前先读）：
#   1. **绝不修改冻结的 Go 仓库**。Go 的运行时配置全部来自 conf/go/*.yaml 的**副本**；
#      脚本会断言真源仓仍然干净。
#   2. **Go 不写任何文件进仓库**。真源 yaml 的 `Log.Path` 指向仓库里的 log 目录 ⇒
#      副本已改成 `Mode: console`。
#   3. **两侧端口都必须独占**，且落在 18211~18224（其它 lane 在用别的段）。
#
#  用法：
#    bash tools/api-full-parity-probe/users/run.sh              # 完整跑（含构建）
#    bash tools/api-full-parity-probe/users/run.sh --no-build   # 复用已有 jar / Go 二进制
#    bash tools/api-full-parity-probe/users/run.sh --keep       # 跑完不收摊（便于手工 curl）
#    bash tools/api-full-parity-probe/users/run.sh --serve-only # 起完两套栈就停（供取证脚本用）
#
#  退出码：0 = 两侧一致（EXIT=0）；1 = 有未预期的差异；2 = 环境问题 / 真源漂移。
# ════════════════════════════════════════════════════════════════════════════
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAG_ROOT="$(cd "${HERE}/../../.." && pwd)"
GO_REPO="${GO_REPO:-/Users/pr/Documents/java&golang/Sea-RideTheWind_golang}"
RUN_DIR="${RUN_DIR:-/tmp/r4upprobe}"
LOGS="$RUN_DIR/logs"

BUILD=1
KEEP=0
SERVE_ONLY=0
for a in "$@"; do
  case "$a" in
    --no-build)  BUILD=0 ;;
    --keep)      KEEP=1 ;;
    --serve-only) SERVE_ONLY=1 ;;
    *) echo "未知参数：${a}（可选：--no-build / --keep / --serve-only）" >&2; exit 2 ;;
  esac
done

# ── 端口（全部可覆盖；默认落在 18211~18224，避开其它 lane）──────────────────
UPPROBE_CONTAINER_PREFIX="${UPPROBE_CONTAINER_PREFIX:-r4upprobe}"
PG_PORT="${UPPROBE_PG_PORT:-18211}"
REDIS_PORT="${UPPROBE_REDIS_PORT:-18212}"

GO_USER_RPC_PORT="${UPPROBE_GO_USER_RPC_PORT:-18213}"
GO_ADMIN_RPC_PORT="${UPPROBE_GO_ADMIN_RPC_PORT:-18214}"
GO_USER_API_PORT="${UPPROBE_GO_USER_API_PORT:-18215}"
GO_ADMIN_API_PORT="${UPPROBE_GO_ADMIN_API_PORT:-18216}"

RAG_USER_RPC_PORT="${UPPROBE_RAG_USER_RPC_PORT:-18217}"
RAG_ADMIN_RPC_PORT="${UPPROBE_RAG_ADMIN_RPC_PORT:-18218}"
RAG_USER_API_PORT="${UPPROBE_RAG_USER_API_PORT:-18219}"
RAG_ADMIN_API_PORT="${UPPROBE_RAG_ADMIN_API_PORT:-18220}"

# Go 的 DevServer(prometheus) 也占端口 ⇒ 一起搬走（2026-10-29 实测踩过：
# Prometheus 与 ListenOn 是**两个**端口，改成同一个会 panic: address already in use）
GO_USER_RPC_PROM="${UPPROBE_GO_USER_RPC_PROM:-18221}"
GO_USER_API_PROM="${UPPROBE_GO_USER_API_PROM:-18222}"
GO_ADMIN_API_PROM="${UPPROBE_GO_ADMIN_API_PROM:-18223}"
GO_ADMIN_RPC_PROM="${UPPROBE_GO_ADMIN_RPC_PROM:-18224}"

for _p in "$PG_PORT" "$REDIS_PORT" \
          "$GO_USER_RPC_PORT" "$GO_ADMIN_RPC_PORT" "$GO_USER_API_PORT" "$GO_ADMIN_API_PORT" \
          "$RAG_USER_RPC_PORT" "$RAG_ADMIN_RPC_PORT" "$RAG_USER_API_PORT" "$RAG_ADMIN_API_PORT" \
          "$GO_USER_RPC_PROM" "$GO_USER_API_PROM" "$GO_ADMIN_API_PROM" "$GO_ADMIN_RPC_PROM"; do
  if (( _p < 1024 || _p > 65535 )); then
    echo "端口越界：$_p 不在 [1024,65535]。" >&2; exit 2
  fi
done

export UPPROBE_CONTAINER_PREFIX UPPROBE_PG_PORT="$PG_PORT" UPPROBE_REDIS_PORT="$REDIS_PORT"

say()  { printf '%s\n' "$*"; }
step() { printf '\n== %s ==\n' "$*"; }
die()  { printf '!! %s\n' "$*" >&2; dump_logs; exit 2; }

dump_logs() {
  local f
  for f in "$LOGS"/*.log; do
    [ -f "$f" ] || continue
    printf '\n---- %s（末 40 行）----\n' "$(basename "$f")" >&2
    tail -40 "$f" | sed 's/^/   | /' >&2
  done
}

# ── 端口预检：起栈前确认「我要用的端口都空着」─────────────────────────────
# ⚠️ **占用者属于自己 ⇒ 复用，不算冲突**：
#    ① **容器**：`--keep` 留下的**同一个 compose project** 的容器仍持有 PG/Redis 端口
#       ⇒ 上一轮残留 ⇒ 预检失败 ⇒ 同一个前缀永远跑不了第二次；
#    ② **进程**：上一轮残留的本 harness 栈进程（命令行含 `${RUN_DIR}/`）也占着 app 端口。
# ⚠️ **口径不能放宽成「端口被占就放过」**（那正是原意：防别人踩我们）⇒ 只豁免「自己的」。
preflight_ports() {
  local p who pid cmd busy=0
  local own_container_ports
  own_container_ports="$(docker ps --filter "label=com.docker.compose.project=$UPPROBE_CONTAINER_PREFIX" \
                          --format '{{.Ports}}' 2>/dev/null || true)"
  for p in "$PG_PORT" "$REDIS_PORT" \
           "$GO_USER_RPC_PORT" "$GO_ADMIN_RPC_PORT" "$GO_USER_API_PORT" "$GO_ADMIN_API_PORT" \
           "$RAG_USER_RPC_PORT" "$RAG_ADMIN_RPC_PORT" "$RAG_USER_API_PORT" "$RAG_ADMIN_API_PORT" \
           "$GO_USER_RPC_PROM" "$GO_USER_API_PROM" "$GO_ADMIN_API_PROM" "$GO_ADMIN_RPC_PROM"; do
    who="$( { lsof -nP -iTCP:"$p" -sTCP:LISTEN 2>/dev/null | tail -n +2 | head -1 | awk '{print $1}'; } || true )"
    [[ -n "$who" ]] || continue
    # ① 容器口径：占用者是**我们这个 compose project** 的容器 ⇒ 复用
    if [[ -n "$own_container_ports" && "$own_container_ports" == *":${p}->"* ]]; then
      say "  端口 $p 由**自己的**容器占用 ⇒ 复用，不算冲突"
      continue
    fi
    # ② 进程口径：占用者是**本 harness 的栈进程**（命令行含 ${RUN_DIR}/）⇒ 复用
    pid="$( { lsof -nP -iTCP:"$p" -sTCP:LISTEN -t 2>/dev/null | head -1; } || true )"
    cmd=""
    [[ -n "$pid" ]] && cmd="$(ps -o command= -p "$pid" 2>/dev/null || true)"
    if [[ "$cmd" == *"$RUN_DIR/"* ]]; then
      say "  端口 $p 由**自己的**栈进程占用 ⇒ 复用，不算冲突"
      continue
    fi
    busy=1
    say "  端口 $p 已被占用（${who:-?}）"
  done
  [[ $busy -eq 0 ]] || { say "端口预检失败 ⇒ 换端口（UPPROBE_* env）后重试。"; exit 2; }
  say "  ✓ 端口预检通过"
}

# ⚠️ **预检之前先把**上一轮**残留的容器拆掉**：
#    否则上一轮的 PG/Redis 容器还活着（仍占着发布端口）⇒ 预检把它们当成「别人占用」⇒
#    报「端口已被占用」⇒ 直接 exit 2 ⇒ **同一个前缀永远跑不了第二次**
#    （2026-10-29 实测踩过：变异套件连跑时第二轮就撞这个）。
docker compose -p "$UPPROBE_CONTAINER_PREFIX" -f "$HERE/docker-compose.yml" down -v >/dev/null 2>&1 || true
preflight_ports

# ⚠️ **PG / Redis 的连接信息必须导出给 RAG 侧进程**（env 形态）——
#   RAG 的 rpc 模块的 jdbc url 占位符名字**不统一**：
#     ${POSTGRES_ADDR:postgres} —— user-rpc / admin-rpc
#     ${POSTGRES_HOST:postgres}  —— 其余 rpc 模块
#   ⇒ 两个名字都导，值相同。
export POSTGRES_ADDR=127.0.0.1 POSTGRES_HOST=127.0.0.1 POSTGRES_PORT="$PG_PORT" \
       POSTGRES_USER=admin POSTGRES_PASSWORD=difftest \
       REDIS_ADDR=127.0.0.1 REDIS_PORT="$REDIS_PORT" \
       NACOS_ADDR=127.0.0.1 NACOS_PORT=1

# 🔴 **P0 #8 的 fail-closed 守卫**：users 域的 user-api / user-rpc 用
#    `${USER_JWT_SECRET}`、admin-api / admin-rpc 用 `${ADMIN_JWT_SECRET}`，
#    **两个都没有默认值**（默认明文已在 2026-10-29 清除）
#    ⇒ 缺变量 ⇒ **进程起不来**（@PostConstruct 判空 ⇒ IllegalStateException）。
#    ⇒ 这里**每次运行现生成**一对随机密钥（不落仓、不进 git）：
#       · 同一域内 api 与 rpc 必须**同一个**（api 签发、rpc 验签）；
#       · 用户域与管理员域**互不相同**（域间隔离）。
#    ⇒ 为什么不用 Go 侧那份固定值：Go 侧那份是**已提交的对拍夹具**（真源 yaml 副本），
#       RAG 侧用随机值更能证明「验签链路真的在跑」。
USER_JWT_SECRET="${USER_JWT_SECRET:-$(python3 -c 'import secrets;print(secrets.token_hex(16))')}"
ADMIN_JWT_SECRET="${ADMIN_JWT_SECRET:-$(python3 -c 'import secrets;print(secrets.token_hex(16))')}"
export USER_JWT_SECRET ADMIN_JWT_SECRET

PIDS=()

# ── 等端口真的空出来 ──────────────────────────────────────────────────────
# ⚠️ **必须等**（2026-10-29 实测踩过 ⇒ 变异套件连跑时第二轮直接 EXIT=2）：
#   ① `docker compose down -v` 之后，**docker-proxy** 还占着 PG/Redis 的发布端口
#      （容器即使 Exited 也**仍然占着端口**）⇒ 下一轮的端口预检直接失败；
#   ② JVM 收到 **SIGTERM** 后要几秒才优雅退尽（Spring Boot 的 shutdown hook）。
#   ⇒ 逐个 `lsof` 轮询到空 ⇒ ⚠️ **不能用 `nc -z`**（它会误报空闲）。
wait_ports_free() {
  local p deadline=$((SECONDS + 45)) busy
  while (( SECONDS < deadline )); do
    busy=0
    for p in "$PG_PORT" "$REDIS_PORT" \
             "$GO_USER_RPC_PORT" "$GO_ADMIN_RPC_PORT" "$GO_USER_API_PORT" "$GO_ADMIN_API_PORT" \
             "$RAG_USER_RPC_PORT" "$RAG_ADMIN_RPC_PORT" "$RAG_USER_API_PORT" "$RAG_ADMIN_API_PORT"; do
      if { lsof -nP -iTCP:"$p" -sTCP:LISTEN -t >/dev/null 2>&1; }; then busy=1; break; fi
    done
    [[ $busy -eq 0 ]] && return 0
    sleep 1
  done
  say "  ⚠️ 45s 内端口未全部释放 ⇒ 下一轮预检可能失败"
  return 1
}

cleanup() {
  if [[ $KEEP -eq 1 ]]; then
    say "[run.sh] --keep：保留容器与进程。手动收摊："
    say "  docker compose -p $UPPROBE_CONTAINER_PREFIX -f $HERE/docker-compose.yml down -v"
    say "  kill ${PIDS[*]:-}"
    return
  fi
  say "[run.sh] 收摊…"
  for p in "${PIDS[@]:-}"; do kill "$p" 2>/dev/null || true; done
  docker compose -p "$UPPROBE_CONTAINER_PREFIX" -f "$HERE/docker-compose.yml" down -v >/dev/null 2>&1 || true
  wait_ports_free || true
}
trap cleanup EXIT

# ── 就绪闸门：等 HTTP 真的答得上（不是 404、不是 000）──────────────────────
wait_http() {  # url, name, tries
  local url=$1 name=$2 tries=${3:-120} code=""
  for _ in $(seq 1 "$tries"); do
    code=$(curl -s -o /dev/null -w '%{http_code}' -m 3 "$url" 2>/dev/null) || code=""
    if [[ -n "$code" && "$code" != "000" && "$code" != "404" ]]; then
      say "  OK   ${name} 就绪（HTTP ${code}）"; return 0
    fi
    sleep 1
  done
  say "  FAIL ${name} 超时（${url}，最后状态码 ${code:-none}）"; return 1
}

assert_started() {  # logfile, name
  local name=$2
  if grep -qE 'APPLICATION FAILED TO START|was already in use|BeanCreationException' "$1" 2>/dev/null; then
    say "  FAIL ${name} 启动失败："
    grep -E 'Description:|Port .* was already in use|APPLICATION FAILED' -A 2 "$1" | head -8 | sed 's/^/      /'
    return 1
  fi
  return 0
}

mkdir -p "$RUN_DIR" "$LOGS" "$RUN_DIR/bin" "$RUN_DIR/conf/go"

# ── 渲染 Go 配置副本：把字面量端口整体换成本轮端口 ─────────────────────────
# ⚠️ 真源冻结 ⇒ 只改副本。替换用「整串数字替换」（macOS 的 sed 是 BSD 版，不支持 \b）。
render_go_conf() {
  local dst="$RUN_DIR/conf/go" f base
  mkdir -p "$dst"
  local pairs="55432:$PG_PORT 56379:$REDIS_PORT \
19004:$GO_USER_RPC_PORT 18081:$GO_ADMIN_RPC_PORT \
18888:$GO_USER_API_PORT 18889:$GO_ADMIN_API_PORT \
19094:$GO_USER_RPC_PROM 19095:$GO_USER_API_PROM 19096:$GO_ADMIN_API_PROM 19097:$GO_ADMIN_RPC_PROM"
  for f in "$HERE"/conf/go/*.yaml; do
    base="$(basename "$f")"
    cp "$f" "$dst/$base"
    for pair in $pairs; do
      sed -i '' "s/${pair%%:*}/${pair##*:}/g" "$dst/$base"
    done
  done
  # 🔴 **逐条断言**（2026-10-29 实测踩过：缩进写错 ⇒ sed 静默不命中 ⇒ 端口没换 ⇒ 撞端口）
  local chk
  for chk in "$PG_PORT" "$REDIS_PORT" "$GO_USER_RPC_PORT" "$GO_ADMIN_RPC_PORT" \
             "$GO_USER_API_PORT" "$GO_ADMIN_API_PORT" \
             "$GO_USER_RPC_PROM" "$GO_USER_API_PROM" "$GO_ADMIN_API_PROM" "$GO_ADMIN_RPC_PROM"; do
    grep -q "$chk" "$dst"/*.yaml || { say "渲染断言失败：$chk 未出现在副本里"; exit 2; }
  done
  say "  ✓ Go 配置副本已渲染到 ${dst}"
}
render_go_conf
GO_CONF="$RUN_DIR/conf/go"

step "[0/7] 断言 Go 真源仓仍然干净（冻结真源一个字都没被改）"
if [[ -d "$GO_REPO/.git" ]]; then
  dirty=$(cd "$GO_REPO" && git status --porcelain -- service/ | head -5)
  if [[ -n "$dirty" ]]; then
    say "  FAIL 真源仓 service/ 有改动："; say "$dirty"; exit 2
  fi
  say "  ✓ 真源仓 service/ 干净"
else
  say "  ⚠️ 找不到真源仓 ${GO_REPO}（跳过干净性断言）"
fi

step "[1/7] 基建（postgres + redis）"
docker compose -p "$UPPROBE_CONTAINER_PREFIX" -f "$HERE/docker-compose.yml" down -v >/dev/null 2>&1 || true
docker compose -p "$UPPROBE_CONTAINER_PREFIX" -f "$HERE/docker-compose.yml" up -d >/dev/null
until docker exec "$UPPROBE_CONTAINER_PREFIX-postgres" pg_isready -U admin -d go_db >/dev/null 2>&1; do sleep 1; done
until docker exec "$UPPROBE_CONTAINER_PREFIX-redis" redis-cli ping >/dev/null 2>&1; do sleep 1; done
for db in go_db rag_db; do
  until docker exec "$UPPROBE_CONTAINER_PREFIX-postgres" psql -U admin -lqt 2>/dev/null | cut -d'|' -f1 | grep -qw "$db"; do sleep 1; done
done
say "  ✓ postgres(go_db,rag_db) + redis"

step "[2/7] 构建 Go 栈（CGO_ENABLED=0，产物落 ${RUN_DIR}，不进任何仓库）"
# ⚠️ `--no-build` 只表示「**能复用就复用**」⇒ 产物不在就**照样构建**
#    （否则下一句的存在性检查会以一条看不懂的 die 收场）。
for gomod in service/user/user/rpc:go-user-rpc service/user/user/api:go-user-api \
              service/user/admin/rpc:go-admin-rpc service/user/admin/api:go-admin-api; do
  pkg="./${gomod%%:*}"
  out="$RUN_DIR/bin/${gomod##*:}"
  if [[ $BUILD -eq 1 || ! -x "$out" ]]; then
    ( cd "$GO_REPO" && CGO_ENABLED=0 go build -o "$out" "$pkg" ) || die "Go 构建失败：$pkg"
  fi
done

step "[3/7] 构建 RAG 栈（只构建 users 域四个组件 + 依赖）"
if [[ $BUILD -eq 1 ]]; then
  ( cd "$RAG_ROOT" && mvn -o -q -DskipTests \
      -pl service/users/user/user-api,service/users/user/user-rpc,service/users/admin/admin-api,service/users/admin/admin-rpc \
      -am package ) || die "RAG 栈构建失败"
fi
# ⚠️ **拷贝 jar 与 BUILD 无关**：产物在仓内 `target/` 里 ⇒ 拷进 ${RUN_DIR}/bin 是零成本的
for m in user/user-rpc:user-rpc user/user-api:user-api \
         admin/admin-rpc:admin-rpc admin/admin-api:admin-api; do
  src="$RAG_ROOT/service/users/${m%%:*}/target/${m##*:}-0.0.1-SNAPSHOT.jar"
  [[ -f "$src" ]] || die "缺少 jar：${src}（去掉 --no-build 再跑一次构建）"
  cp "$src" "$RUN_DIR/bin/${m##*:}"
done
for j in go-user-rpc go-user-api go-admin-rpc go-admin-api user-rpc user-api admin-rpc admin-api; do
  [[ -e "${RUN_DIR}/bin/${j}" ]] || die "缺少产物 ${RUN_DIR}/bin/${j}（去掉 --no-build 再跑）"
done

step "[4/7] 启动 Go 栈（Endpoints 直连，免 etcd）"
# ⚠️ **用 exec 起**：`( cd X && cmd ) &` 的 $! 是**子 shell** 的 PID ⇒ kill 不到真进程。
#   这里用 `exec` 让子 shell **替换成**目标进程 ⇒ $! 就是真进程的 PID。
( cd "$RUN_DIR" && exec env POSTGRES_DB=go_db "$RUN_DIR/bin/go-user-rpc"  -f "$GO_CONF/user-rpc.yaml"  ) >"$LOGS/go-user-rpc.log" 2>&1 & PIDS+=($!)
( cd "$RUN_DIR" && exec env POSTGRES_DB=go_db "$RUN_DIR/bin/go-admin-rpc" -f "$GO_CONF/admin-rpc.yaml" ) >"$LOGS/go-admin-rpc.log" 2>&1 & PIDS+=($!)
sleep 3
# admin-api 必须在 admin-rpc 之后起（Go 侧 zrpc.MustNewClient 启动期阻塞拨号 3s）
( cd "$RUN_DIR" && exec env POSTGRES_DB=go_db "$RUN_DIR/bin/go-user-api"  -f "$GO_CONF/user-api.yaml"  ) >"$LOGS/go-user-api.log" 2>&1 & PIDS+=($!)
( cd "$RUN_DIR" && exec env POSTGRES_DB=go_db "$RUN_DIR/bin/go-admin-api" -f "$GO_CONF/admin-api.yaml" ) >"$LOGS/go-admin-api.log" 2>&1 & PIDS+=($!)
sleep 3
assert_started "$LOGS/go-user-api.log"  "go user-api"  || true
assert_started "$LOGS/go-admin-api.log" "go admin-api" || true
wait_http "http://127.0.0.1:$GO_USER_API_PORT/usercenter/v1/user/login"  "go user-api"  90
wait_http "http://127.0.0.1:$GO_ADMIN_API_PORT/admincenter/v1/admin/login" "go admin-api" 90

step "[5/7] 启动 RAG 栈（独占端口；gRPC 走 static:// 绕开 Nacos）"
# ⚠️ **USER_JWT_SECRET / ADMIN_JWT_SECRET 必须传**（P0 #8 fail-closed，见上）。
( cd "$RUN_DIR" && exec env POSTGRES_DB=rag_db GRPC_SERVER_PORT="$RAG_USER_RPC_PORT" \
    USER_JWT_SECRET="$USER_JWT_SECRET" \
    java -jar "$RUN_DIR/bin/user-rpc" ) >"$LOGS/rag-user-rpc.log" 2>&1 & PIDS+=($!)
( cd "$RUN_DIR" && exec env POSTGRES_DB=rag_db GRPC_SERVER_PORT="$RAG_ADMIN_RPC_PORT" \
    ADMIN_JWT_SECRET="$ADMIN_JWT_SECRET" \
    java -jar "$RUN_DIR/bin/admin-rpc" ) >"$LOGS/rag-admin-rpc.log" 2>&1 & PIDS+=($!)
sleep 20
assert_started "$LOGS/rag-user-rpc.log"  "rag user-rpc"
assert_started "$LOGS/rag-admin-rpc.log" "rag admin-rpc"

# ⚠️ **register-enabled=false**（只关自动注册，starter 的 bean 还在）：
#   不关的话 api 进程会死在 `Failed to start bean 'webServerStartStop'`。
# ⚠️ **negotiation-type=plaintext 是必要的 harness 固定值**：
#   devh client 的默认值是 TLS，而两端 rpc 都是 plain gRPC（DRIFT-NEW-014）。
( cd "$RUN_DIR" && exec env POSTGRES_DB=rag_db SERVER_PORT="$RAG_USER_API_PORT" \
    USER_JWT_SECRET="$USER_JWT_SECRET" \
    SPRING_CLOUD_NACOS_DISCOVERY_REGISTER_ENABLED=false \
    java -jar "$RUN_DIR/bin/user-api" \
    --grpc.client.user-rpc.address=static://127.0.0.1:$RAG_USER_RPC_PORT \
    --grpc.client.user-rpc.negotiation-type=plaintext ) >"$LOGS/rag-user-api.log" 2>&1 & PIDS+=($!)
sleep 5
assert_started "$LOGS/rag-user-api.log" "rag user-api"
( cd "$RUN_DIR" && exec env POSTGRES_DB=rag_db SERVER_PORT="$RAG_ADMIN_API_PORT" \
    ADMIN_JWT_SECRET="$ADMIN_JWT_SECRET" \
    SPRING_CLOUD_NACOS_DISCOVERY_REGISTER_ENABLED=false \
    java -jar "$RUN_DIR/bin/admin-api" \
    --grpc.client.admin-rpc.address=static://127.0.0.1:$RAG_ADMIN_RPC_PORT \
    --grpc.client.admin-rpc.negotiation-type=plaintext \
    --grpc.client.user-rpc.address=static://127.0.0.1:$RAG_USER_RPC_PORT \
    --grpc.client.user-rpc.negotiation-type=plaintext ) >"$LOGS/rag-admin-api.log" 2>&1 & PIDS+=($!)
sleep 5
assert_started "$LOGS/rag-admin-api.log" "rag admin-api"
wait_http "http://127.0.0.1:$RAG_USER_API_PORT/user/v1/login"  "rag user-api"  150
wait_http "http://127.0.0.1:$RAG_ADMIN_API_PORT/admin/v1/login" "rag admin-api" 150

# 「进程起来了但 schema 是空的」是一类静默失败 ⇒ 必须查表
assert_tables() {  # db, table, label
  local n
  n=$(docker exec "$UPPROBE_CONTAINER_PREFIX-postgres" psql -U admin -d "$1" -tAc \
      "select count(*) from information_schema.tables where table_schema='public' and table_name='$2';" 2>/dev/null | tr -d '[:space:]')
  if [[ "$n" != "1" ]]; then
    say "  FAIL ${3}：表 ${1}.${2} 不存在"
    docker exec "$UPPROBE_CONTAINER_PREFIX-postgres" psql -U admin -d "$1" -tAc \
      "select tablename from pg_tables where schemaname='public' order by 1;" 2>/dev/null | sed 's/^/        /' || true
    return 1
  fi
  say "  OK   ${3} ${1}.${2}"
}
step "[5b/7] 校验表真的建出来了（不只看进程活着）"
assert_tables rag_db users_user                "rag user-rpc"
assert_tables rag_db admin_admin               "rag admin-rpc"
assert_tables go_db  users                     "go user-rpc"

if [[ $SERVE_ONLY -eq 1 ]]; then
  step "[serve-only]：两套栈保持运行（PID ${PIDS[*]}）"
  say "     日志：$LOGS"
  wait
  exit 0
fi

step "[6/7] user.register 落库对照（HTTP 响应 vs 两侧库里的行）"
# ⚠️ 这一步**独立于矩阵**：用**专用**用户名 `dt_probe_landed` ⇒ 不碰矩阵用的 `dt_user1`
#    ⇒ 既不污染矩阵，也不被矩阵污染。
python3 "$HERE/check_register_landed.py" \
  --go-url "http://127.0.0.1:$GO_USER_API_PORT" \
  --rag-url "http://127.0.0.1:$RAG_USER_API_PORT" \
  --pg-container "$UPPROBE_CONTAINER_PREFIX-postgres"
LAND_RC=$?

step "[7/7] 双侧对拍（diff.py）"
PROBE_GO_USER_URL="http://127.0.0.1:$GO_USER_API_PORT" \
PROBE_GO_ADMIN_URL="http://127.0.0.1:$GO_ADMIN_API_PORT" \
PROBE_RAG_USER_URL="http://127.0.0.1:$RAG_USER_API_PORT" \
PROBE_RAG_ADMIN_URL="http://127.0.0.1:$RAG_ADMIN_API_PORT" \
python3 "$HERE/diff.py" "$@"
RC=$?

say ""
say "日志：$LOGS"
# ⚠️ 落库对照没过 ⇒ 整体判红（它是「register 是否真的写进库」的判据）
if [[ $LAND_RC -ne 0 ]]; then
  say "==> ❌ 落库对照未通过（退出码 ${LAND_RC}）⇒ 对拍整体判红"
  [[ $RC -ne 0 ]] || RC=$LAND_RC
fi
exit $RC
