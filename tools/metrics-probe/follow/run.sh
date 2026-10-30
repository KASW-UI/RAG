#!/usr/bin/env bash
#
# follow 域 **指标抓取面**双侧活体对拍探针（DRIFT-FOLLOW-006 落地 · 2026-10-29）。
#
# 真源：Sea-RideTheWind_golang/service/follow/rpc（**只读**，冻结）
#       + Sea-RideTheWind_golang/service/user/user/rpc（下游 user-rpc，同样是真源）
# 被测：RAG/service/follow/follow-rpc
#
# 它验什么：
#   ① **RAG 侧真的抓得到 `follow_rpc_*`**（此前 follow-rpc 是纯 gRPC 进程 ⇒ 零抓取出口）；
#   ② 两侧在**同一批请求**下产生的 `follow_rpc_*` **series 集合**是否一致
#      （series = 指标名 + 标签集合；直方图**值**不比 —— 那取决于真实耗时）；
#   ③ 计数型 / gauge 型的**值**是否一致（同一批请求 ⇒ 同一批计数）。
#
# 退出码：0 = 一致（实际差异与冻结期望逐条相同）；1 = 守门失效（差异变了）；2 = 环境问题。
#
# 用法：
#   bash tools/metrics-probe/follow/run.sh
#   GO_REPO=/path/to/Sea-RideTheWind_golang bash tools/metrics-probe/follow/run.sh
#   SKIP_BUILD=1 bash tools/metrics-probe/follow/run.sh    # 复用已构建的 jar / 二进制
#
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAG_ROOT="$(cd "${HERE}/../../.." && pwd)"
GO_REPO="${GO_REPO:-/Users/pr/Documents/java&golang/Sea-RideTheWind_golang}"

# ── 宿主中间件 ───────────────────────────────────────────────────────────────
ETCD_PORT="${ETCD_PORT:-49319}"       # Sea-etcd    49319→2379
ETCD_CTL_ENDPOINT="${ETCD_CTL_ENDPOINT:-http://127.0.0.1:2379}"   # etcdctl 在容器里跑 ⇒ 用容器内端口
NACOS_PORT="${NACOS_PORT:-49848}"     # Sea-nacos   49848→8848
REDIS_PORT="${REDIS_PORT:-49380}"     # Sea-redis   49380→6379（Go user-rpc 的 BizRedis）
# ⚠️ **2026-10-30 活体对拍轮订正**：本行原先写死 `49250` ⇒ **实测陈旧**
#    （本机 `Sea-jaeger` 的宿主映射是 **49317**；其余探针也都是 49317）
JAEGER_PORT="${JAEGER_PORT:-49317}"   # Sea-jaeger  49317→4317（OTLP gRPC）

# ── 端口段（**本 lane 专用段 18171-18180**）─────────────────────────────────
# ⚠️ 探针**自动挑空闲端口**（不硬编码撞车）：先 bind 试占用，再 lsof 复查，
#    并且**独占登记**（本轮用过的口不再复用）。
# ⚠️ **不用关联数组**：macOS 自带 bash 是 **3.2**（`declare -A` 是 bash 4.0+ 才有）
#    ⇒ 用「空格分隔的已用口列表」代替。
PORT_SEG_START="${PORT_SEG_START:-18171}"
PORT_SEG_END="${PORT_SEG_END:-18180}"
USED_PORTS=" "
GO_RPC_PORT=""; GO_DEV_PORT=""; RAG_RPC_PORT=""; RAG_MGMT_PORT=""
GO_USER_RPC_PORT=""; GO_USER_DEV_PORT=""; GO_PG_PORT=""; RAG_PG_PORT=""

# ── 容器名带 $$ ⇒ 别的 lane 的 cleanup 绝不可能删掉本轮的库 ─────────────────
PG_PREFIX="${PG_PREFIX:-r4mprobe-}"

WORK="$(mktemp -d)"
GO_PIDS=(); RAG_PIDS=(); KEEPALIVE_PID=""
say()  { printf '%s\n' "$*"; }
step() { printf '\n== %s ==\n' "$*"; }
die()  { printf '!! %s\n' "$*" >&2; dump_logs; exit 2; }
dump_logs() {
  local f
  for f in go-user-rpc.log go-rpc.log rag-rpc.log; do
    [ -f "${WORK:-/nonexistent}/${f}" ] || continue
    printf '\n---- %s（末 25 行）----\n' "${f}" >&2
    tail -25 "${WORK}/${f}" | sed 's/^/   | /' >&2
  done
}

# ── cleanup（**必须最早装 trap**）──────────────────────────────────────────
# ⚠️ 装在脚本末尾 ⇒ 前置检查 `die` 时**不会**跑 cleanup ⇒ 留下孤儿进程 + 孤儿容器
#    ⇒ 孤儿容器**仍占着发布端口**（docker-proxy 还在）⇒ 下一轮探针假故障。
cleanup() {
  local p
  [ -n "${KEEPALIVE_PID:-}" ] && kill "${KEEPALIVE_PID}" 2>/dev/null
  for p in ${GO_PIDS[@]+"${GO_PIDS[@]}"} ${RAG_PIDS[@]+"${RAG_PIDS[@]}"}; do kill "$p" 2>/dev/null; done
  sleep 1
  for p in ${GO_PIDS[@]+"${GO_PIDS[@]}"} ${RAG_PIDS[@]+"${RAG_PIDS[@]}"}; do kill -9 "$p" 2>/dev/null; done
  # 只注销**本探针自己注册**的实例（ip+port 精确匹配）
  if command -v nacos_deregister >/dev/null 2>&1 && [ -n "${GO_USER_RPC_PORT:-}" ]; then
    nacos_deregister user-rpc "${GO_USER_RPC_PORT}"
  fi
  [ -n "${GO_PG_C:-}" ]  && docker rm -f "${GO_PG_C}"  >/dev/null 2>&1
  [ -n "${RAG_PG_C:-}" ] && docker rm -f "${RAG_PG_C}" >/dev/null 2>&1
  # ⚠️ **容器删掉后 docker-proxy 释放监听口要一会儿** ⇒ 必须等 lsof 空
  #    （`nc -z` 会误报空闲 —— 2026-10-29 实测踩过）
  [ -n "${GO_PG_PORT:-}" ]  && wait_port_released "${GO_PG_PORT}"  20
  [ -n "${RAG_PG_PORT:-}" ] && wait_port_released "${RAG_PG_PORT}" 20
  for p in "${GO_RPC_PORT:-}" "${GO_DEV_PORT:-}" "${RAG_RPC_PORT:-}" "${RAG_MGMT_PORT:-}" \
           "${GO_USER_RPC_PORT:-}" "${GO_USER_DEV_PORT:-}"; do
    [ -n "${p}" ] && wait_port_released "$p" 10
  done
  if [ "${KEEP_WORK:-0}" = "1" ]; then say "   · KEEP_WORK=1 ⇒ 保留 ${WORK}"; fi
}
trap cleanup EXIT

# ── 端口空闲判定：**必须用 lsof** ───────────────────────────────────────────
# ⚠️ `nc -z` 会误报空闲（它只做一次 connect 探测，不等于「没人 LISTEN」）
#    ⇒ 端口释放一律以 lsof 无输出为准。
port_free() { ! lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1; }
port_open() { lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1; }

pick_port() {   # ⇒ 把选中的端口写进全局 PICKED（**不能**用命令替换：
                  #    子 shell 里登记的「已用口」不会回流父 shell ⇒ 会重复选同一个口）
  local p
  for p in $(seq "${PORT_SEG_START}" "${PORT_SEG_END}"); do
    case "${USED_PORTS}" in *" ${p} "*) continue ;; esac
    port_free "${p}" || continue
    USED_PORTS="${USED_PORTS}${p} "
    PICKED="${p}"
    return 0
  done
  return 1
}

# ── 端口释放等待（docker rm -f 之后 docker-proxy 要一会儿才退）────────────
wait_port_released() {   # $1=端口 $2=秒
  local p="$1" i
  for i in $(seq 1 "$2"); do
    port_free "${p}" && return 0
    sleep 1
  done
  return 1
}

# ══════════════════════════ 0. 前置检查（缺 ⇒ exit 2，绝不静默降级）══════════
step "0/8 前置检查"
for cmd in docker go mvn curl python3 lsof nc; do
  command -v "${cmd}" >/dev/null 2>&1 || die "缺命令：${cmd}"
done
[ -d "${GO_REPO}/service/follow/rpc" ] || die "找不到 Go 真源：${GO_REPO}/service/follow/rpc（用 GO_REPO=... 指定）"
[ -d "${GO_REPO}/service/user/user/rpc" ] || die "找不到 Go 真源：${GO_REPO}/service/user/user/rpc"
for pair in "Sea-etcd:${ETCD_PORT}" "Sea-nacos:${NACOS_PORT}" "Sea-redis:${REDIS_PORT}"; do
  c="${pair%%:*}"; p="${pair##*:}"
  docker ps --format '{{.Names}}' | grep -qx "${c}" || die "容器 ${c} 没在跑（宿主中间件缺失）"
  nc -z -G 2 127.0.0.1 "${p}" || die "宿主端口 ${p} 不通（${c} 的映射口）"
done
docker exec Sea-etcd etcdctl --endpoints="${ETCD_CTL_ENDPOINT}" endpoint health >/dev/null 2>&1 \
  || die "容器内 etcd 端点 ${ETCD_CTL_ENDPOINT} 不可用（ETCD_CTL_ENDPOINT 可覆盖）"
say "   ✓ 命令齐 / 中间件在跑"

# ⚠️ **先清掉上一轮遗留的同前缀容器**（Exited 的容器仍占着发布端口）
for c in $(docker ps -aq --filter "name=${PG_PREFIX}" 2>/dev/null); do
  docker rm -f "${c}" >/dev/null 2>&1
  say "   · 已清理上一轮遗留的探针容器 ${c}"
done

# ══════════════════════════ 1. 选端口 ════════════════════════════════════════
step "1/8 选端口（段 ${PORT_SEG_START}-${PORT_SEG_END} 内自动挑空闲口）"
PICKED=""
pick_port || die "端口段用尽"; GO_RPC_PORT="${PICKED}"
pick_port || die "端口段用尽"; GO_DEV_PORT="${PICKED}"
pick_port || die "端口段用尽"; RAG_RPC_PORT="${PICKED}"
pick_port || die "端口段用尽"; RAG_MGMT_PORT="${PICKED}"
pick_port || die "端口段用尽"; GO_USER_RPC_PORT="${PICKED}"
pick_port || die "端口段用尽"; GO_USER_DEV_PORT="${PICKED}"
say "   ✓ Go rpc=:${GO_RPC_PORT} dev=:${GO_DEV_PORT} · RAG rpc=:${RAG_RPC_PORT} mgmt=:${RAG_MGMT_PORT}"
say "     Go user-rpc=:${GO_USER_RPC_PORT} dev=:${GO_USER_DEV_PORT}（两个 PG 端口在第 4 步于段内挑）"

# ══════════════════════════ 2. 构建 ═════════════════════════════════════════
step "2/8 构建（Go 二进制 + gRPC 客户端 + RAG jar）"
mkdir -p "${WORK}/bin" "${WORK}/etc" "${WORK}/log" "${WORK}/client/pb"

# ⚠️ **pb 从冻结真源现拷现用**（不复制进版本库）⇒ 并对两个文件做 sha256 断言
#    ⇒ 真源改了 pb，探针立刻响（不会「拿着自己的一份副本自证」）。
cp "${GO_REPO}/service/follow/rpc/pb/follow.pb.go" "${WORK}/client/pb/"
cp "${GO_REPO}/service/follow/rpc/pb/follow_grpc.pb.go" "${WORK}/client/pb/"
cp "${HERE}/go/client/main.go" "${WORK}/client/"
cat >"${WORK}/client/go.mod" <<'EOF'
module followmetricsprobe

go 1.25

require (
	google.golang.org/grpc v1.81.1
	google.golang.org/protobuf v1.36.11
)
EOF
cp "${GO_REPO}/go.sum" "${WORK}/client/go.sum"
( cd "${WORK}/client" && GOFLAGS=-mod=mod GOPROXY=off GOTOOLCHAIN=local go build -o "${WORK}/bin/client" . ) \
  || die "gRPC 客户端构建失败（Go 模块缓存不可见？重试即可）"
[ -x "${WORK}/bin/client" ] || die "gRPC 客户端没产出"

if [ "${SKIP_BUILD:-0}" != "1" ]; then
  # ⚠️ **只有 RAG 的 maven 构建可以被跳过**（最慢的一步）；
  #    Go 二进制与 gRPC 客户端**每次都重建** ⇒ 不会「拿旧二进制自证」。
  ( cd "${RAG_ROOT}" && mvn -o -q -pl service/follow/follow-rpc -am package -DskipTests ) \
    || die "RAG follow-rpc 构建失败"
fi
( cd "${GO_REPO}" && CGO_ENABLED=0 GOPROXY=off GOTOOLCHAIN=local go build \
    -o "${WORK}/bin/follow-rpc" ./service/follow/rpc \
  && CGO_ENABLED=0 GOPROXY=off GOTOOLCHAIN=local go build \
    -o "${WORK}/bin/user-rpc"   ./service/user/user/rpc ) || die "Go 侧构建失败"
RAG_JAR="${RAG_ROOT}/service/follow/follow-rpc/target/follow-rpc-0.0.1-SNAPSHOT.jar"
[ -f "${RAG_JAR}" ] || die "RAG jar 不存在：${RAG_JAR}（别用 SKIP_BUILD=1）"
say "   ✓ 构建完成"

# ══════════════════════════ 3. 两侧 Postgres（**各一个** ⇒ 初始状态完全一致）═══
step "3/8 两侧 Postgres（各自独立 ⇒ 写路径互不干扰 ⇒ 两侧看到同一份初始状态）"
# ⚠️ **必须是两个库**：共用一个库时，Go 侧先跑完写路径 ⇒ RAG 侧再跑同一批请求
#    会撞上「已存在 / 已删除」⇒ 两侧结果标签不同 ⇒ **伪造出指标差异**。
# ⚠️ **端口要能换**：同机别的 lane 也在抢端口（实测：别的探针正好占着 18177
#    ⇒ `docker run` 报 "port is already allocated" ⇒ 本探针假故障）
#    ⇒ 起不来就**换段内下一个口**重试（与 tools/api-full-parity-probe/follow 同款阶梯）。
start_pg() {   # $1=容器名 ⇒ 成功时把**实际用的**宿主端口写进全局 PICKED
                  #    （**不能**用命令替换：子 shell 里登记的「已用口」不会回流父 shell
                  #     ⇒ 第二个 PG 会挑到同一个口 ⇒ 两个容器抢一个发布口）
  local c="$1" p run_log
  run_log="$(mktemp "${WORK:-/tmp}/pgrun.XXXXXX")"
  for p in $(seq "${PORT_SEG_START}" "${PORT_SEG_END}"); do
    case "${USED_PORTS}" in *" ${p} "*) continue ;; esac
    port_free "${p}" || continue
    docker rm -f "${c}" >/dev/null 2>&1
    wait_port_released "${p}" 20 || continue
    if ! docker run -d --name "${c}" -e POSTGRES_USER=admin -e POSTGRES_PASSWORD=password \
         -e POSTGRES_DB=first_db -p "${p}:5432" postgres:16-alpine >"${run_log}" 2>&1; then
      say "   · :${p} 起不来（$(tr -d '\n' <"${run_log}" | tail -c 120)）⇒ 换下一个"
      docker rm -f "${c}" >/dev/null 2>&1
      continue
    fi
    local i
    for i in $(seq 1 40); do
      docker exec "${c}" pg_isready -U admin -d first_db >/dev/null 2>&1 && break
      sleep 1
    done
    if ! docker exec "${c}" pg_isready -U admin -d first_db >/dev/null 2>&1; then
      say "   · :${p} 容器内没就绪 ⇒ 换下一个"
      docker rm -f "${c}" >/dev/null 2>&1
      continue
    fi
    # ⚠️ 就绪判据必须是「**宿主端口**通」：容器内 socket 通 ≠ 宿主映射口通
    for i in $(seq 1 15); do
      nc -z -G 2 127.0.0.1 "${p}" >/dev/null 2>&1 && { USED_PORTS="${USED_PORTS}${p} "; PICKED="${p}"; return 0; }
      sleep 1
    done
    say "   · :${p} 宿主端口没通 ⇒ 换下一个"
    docker rm -f "${c}" >/dev/null 2>&1
  done
  return 1
}
GO_PG_C="${PG_PREFIX}go-pg-$$"; RAG_PG_C="${PG_PREFIX}rag-pg-$$"
start_pg "${GO_PG_C}"   || die "Go 侧 PG 起不来（段 ${PORT_SEG_START}-${PORT_SEG_END} 全试完）"
GO_PG_PORT="${PICKED}"
start_pg "${RAG_PG_C}"  || die "RAG 侧 PG 起不来（段 ${PORT_SEG_START}-${PORT_SEG_END} 全试完）"
RAG_PG_PORT="${PICKED}"
say "   ✓ 两侧 Postgres 就绪（Go :${GO_PG_PORT} · RAG :${RAG_PG_PORT}）"

# ══════════════════════════ 4. 配置副本（真源 yaml 只读 ⇒ 只改副本）═════════
step "4/8 生成 Go 侧配置副本"
sed -e "s|^ListenOn: 0.0.0.0:8082$|ListenOn: 0.0.0.0:${GO_RPC_PORT}|" \
    -e "s|etcd:2379|127.0.0.1:${ETCD_PORT}|g" \
    -e "s|^  Path: ../../../../log/follow-rpc$|  Path: ${WORK}/log/follow-rpc|" \
    -e "s|jaeger:4317|127.0.0.1:${JAEGER_PORT}|g" \
    -e "s|^  Port: 16065$|  Port: ${GO_DEV_PORT}|" \
    -e "s|^  Host: \"postgres\"$|  Host: \"127.0.0.1\"|" \
    -e "s|^  Port: \"5432\"$|  Port: \"${GO_PG_PORT}\"|" \
    -e "s|^  Password: \"\${POSTGRES_PASSWORD}\"$|  Password: \"password\"|" \
    "${GO_REPO}/service/follow/rpc/etc/follow.yaml" > "${WORK}/etc/follow.yaml"
sed -e "s|^ListenOn: 0.0.0.0:9004$|ListenOn: 0.0.0.0:${GO_USER_RPC_PORT}|" \
    -e "s|^  Path: ../../../../log/user-rpc$|  Path: ${WORK}/log/user-rpc|" \
    -e "s|^  Host: \"postgres\"$|  Host: \"127.0.0.1\"|" \
    -e "s|^  Port: \"5432\"$|  Port: \"${GO_PG_PORT}\"|" \
    -e "s|^  Password: \"\${POSTGRES_PASSWORD}\"$|  Password: \"password\"|" \
    -e "s|redis:6379|127.0.0.1:${REDIS_PORT}|g" \
    -e "s|jaeger:4317|127.0.0.1:${JAEGER_PORT}|g" \
    -e "s|^  Port: 9094$|  Port: ${GO_USER_DEV_PORT}|" \
    -e "s|etcd:2379|127.0.0.1:${ETCD_PORT}|g" \
    "${GO_REPO}/service/user/user/rpc/etc/user.yaml" > "${WORK}/etc/user.yaml"
# ⚠️ 逐条断言「改了哪项」—— sed 没命中 ⇒ 副本里仍是旧值 ⇒ 端口一被占就 panic ⇒ 探针假挂
grep -q "^ListenOn: 0.0.0.0:${GO_RPC_PORT}$"      "${WORK}/etc/follow.yaml" || die "副本 follow.yaml 的 ListenOn 没改（sed 没命中？）"
grep -q "^  Port: ${GO_DEV_PORT}$"                "${WORK}/etc/follow.yaml" || die "副本 follow.yaml 的 DevServer Port 没改"
grep -q "^  Port: \"${GO_PG_PORT}\"$"              "${WORK}/etc/follow.yaml" || die "副本 follow.yaml 的 Postgres Port 没改"
grep -q "^ListenOn: 0.0.0.0:${GO_USER_RPC_PORT}$" "${WORK}/etc/user.yaml"   || die "副本 user.yaml 的 ListenOn 没改"
grep -q "^  Port: ${GO_USER_DEV_PORT}$"           "${WORK}/etc/user.yaml"   || die "副本 user.yaml 的 DevServer Port 没改"
for f in follow.yaml user.yaml; do
  grep -q "etcd:2379" "${WORK}/etc/${f}" && die "副本 ${f} 里还残留 etcd:2379（sed 没命中？）"
done
( cd "${GO_REPO}" && git status --porcelain -- service/follow service/user | grep -q . ) \
  && die "真源仓 service/follow|service/user 有改动 ⇒ 本探针**不得**改真源"
say "   ✓ 配置副本已生成（真源 service/follow|service/user 仍然干净）"

# ══════════════════════════ 5. Nacos / etcd 实例管理 ═════════════════════════
nacos_list_instances() {
  curl -s -m 10 "http://127.0.0.1:${NACOS_PORT}/nacos/v1/ns/instance/list?serviceName=$1&groupName=DEFAULT_GROUP&namespaceId=public" 2>/dev/null \
  | python3 -c '
import sys, json
try:
    d = json.load(sys.stdin)
except Exception:
    sys.exit(0)
for h in (d.get("hosts") or []):
    ip, port = h.get("ip"), h.get("port")
    if ip and port:
        print("%s %s" % (ip, port))
'
}
nacos_register() {
  # ⚠️ ephemeral=true（persistent 走 Raft 组 ⇒ 本机 Nacos 实测「did not find the Leader」⇒ 注册不进去）
  # ⚠️ ephemeral 实例靠**客户端心跳**保活 ⇒ 这里用 curl 手工注册 ⇒ 没人发心跳
  #    ⇒ 约 15s 后被摘掉 ⇒ 表现为「服务名在、hosts 为空」⇒ 必须**每 5s 重注册**
  curl -s -m 10 -X POST "http://127.0.0.1:${NACOS_PORT}/nacos/v1/ns/instance" \
    --data-urlencode "serviceName=$1" --data-urlencode "groupName=DEFAULT_GROUP" \
    --data-urlencode "namespaceId=public" --data-urlencode "ip=127.0.0.1" \
    --data-urlencode "port=$2" --data-urlencode "ephemeral=true" >/dev/null 2>&1
}
nacos_keepalive() {
  while true; do nacos_register "$1" "$2"; sleep 5; done
}
nacos_deregister() {
  curl -s -m 5 -X DELETE "http://127.0.0.1:${NACOS_PORT}/nacos/v1/ns/instance?serviceName=$1&groupName=DEFAULT_GROUP&namespaceId=public&ip=127.0.0.1&port=$2" >/dev/null 2>&1
}
etcd_get_value() {
  docker exec Sea-etcd etcdctl --endpoints="${ETCD_CTL_ENDPOINT}" get "$1" --prefix --print-value-only 2>/dev/null | head -1
}
etcd_has_key() { [ -n "$(etcd_get_value "$1")" ]; }

# ══════════════════════════ 6. 起进程 ═════════════════════════════════════════
step "5/8 起进程（Go user-rpc → Go follow-rpc → RAG follow-rpc）"
# ⚠️ go-zero 的键是**前缀族** `<key>/<leaseID>`（core/discov/publisher.go:191-196）
#    ⇒ 精确键查永远查不到 ⇒ 必须 `--prefix`。
docker exec Sea-etcd etcdctl --endpoints="${ETCD_CTL_ENDPOINT}" del "user.rpc"   --prefix >/dev/null 2>&1
docker exec Sea-etcd etcdctl --endpoints="${ETCD_CTL_ENDPOINT}" del "follow.rpc" --prefix >/dev/null 2>&1
# ⚠️ **$! 必须是「真进程」的 PID** ⇒ 子 shell 里用 `exec`（否则 $! 是子 shell 的 PID，
#    kill 它杀不掉真进程 ⇒ 端口被占着不放 —— 2026-10-29 已踩过）
( cd "${WORK}" && exec "${WORK}/bin/user-rpc" -f "${WORK}/etc/user.yaml" >"${WORK}/go-user-rpc.log" 2>&1 ) &
GO_PIDS+=($!)
for _ in $(seq 1 40); do etcd_has_key user.rpc && break; sleep 1; done
etcd_has_key user.rpc || die "Go user-rpc 40s 内没注册进 etcd（见 ${WORK}/go-user-rpc.log）"
for _ in $(seq 1 20); do port_open "${GO_USER_RPC_PORT}" && break; sleep 1; done
port_open "${GO_USER_RPC_PORT}" || die "Go user-rpc 注册了但端口没监听"
# ⚠️ RAG 侧 follow-rpc 用 **Nacos**（discovery:///user-rpc）找下游 ⇒ 同一个进程
#    必须**同时**在 Nacos 里可见 ⇒ 这里手工注册（ip+port 精确匹配，cleanup 只删自己那条）
nacos_register user-rpc "${GO_USER_RPC_PORT}"
nacos_keepalive user-rpc "${GO_USER_RPC_PORT}" &
KEEPALIVE_PID=$!
say "   ✓ Go user-rpc 就绪（etcd user.rpc → $(etcd_get_value user.rpc) · Nacos user-rpc 已登记）"

( cd "${WORK}" && exec "${WORK}/bin/follow-rpc" -f "${WORK}/etc/follow.yaml" >"${WORK}/go-rpc.log" 2>&1 ) &
GO_PIDS+=($!)
for _ in $(seq 1 40); do etcd_has_key follow.rpc && break; sleep 1; done
etcd_has_key follow.rpc || die "Go follow-rpc 40s 内没注册进 etcd（见 ${WORK}/go-rpc.log）"
for _ in $(seq 1 20); do port_open "${GO_RPC_PORT}" && break; sleep 1; done
port_open "${GO_RPC_PORT}" || die "Go follow-rpc 注册了但端口没监听"
say "   ✓ Go follow-rpc 就绪（etcd follow.rpc → $(etcd_get_value follow.rpc)）"

# ⚠️ Go 侧 `zrpc.MustNewClient` 启动时**阻塞拨号 3s** ⇒ 下游还没完全就绪就起 rpc
#    ⇒ rpc 进程可能直接 panic ⇒ 探针假挂 ⇒ 先等一下。
sleep 3
( cd "${RAG_ROOT}" && exec env \
    POSTGRES_HOST=127.0.0.1 POSTGRES_PORT="${RAG_PG_PORT}" \
    POSTGRES_USER=admin POSTGRES_PASSWORD=password POSTGRES_DB=first_db POSTGRES_SSLMODE=disable \
    NACOS_ADDR=127.0.0.1 NACOS_PORT="${NACOS_PORT}" NACOS_NAMESPACE=public NACOS_GROUP=DEFAULT_GROUP \
    FOLLOW_RPC_PORT="${RAG_RPC_PORT}" FOLLOW_RPC_MANAGEMENT_PORT="${RAG_MGMT_PORT}" \
    OTEL_EXPORTER_OTLP_ENDPOINT="127.0.0.1:${JAEGER_PORT}" \
    java -jar "${RAG_JAR}" >"${WORK}/rag-rpc.log" 2>&1 ) &
RAG_RPC_PID=$!
RAG_PIDS+=("${RAG_RPC_PID}")
# ⚠️ 就绪判据 = **真抓到 /actuator/prometheus**（不是「端口开了」）：
#    端口开了但 actuator 没暴露 ⇒ 正是本探针要抓的那个缺陷本身。
RAG_READY=0
for _ in $(seq 1 60); do
  if curl -s -m 5 -o /dev/null -w '%{http_code}' "http://127.0.0.1:${RAG_MGMT_PORT}/actuator/prometheus" 2>/dev/null | grep -q '^200$'; then
    RAG_READY=1; break
  fi
  kill -0 "${RAG_RPC_PID}" 2>/dev/null || break
  sleep 1
done
[ "${RAG_READY}" = "1" ] || die "RAG follow-rpc 的 /actuator/prometheus 没就绪（见 ${WORK}/rag-rpc.log）⇒ **抓取出口仍然是坏的**"
GO_DEV_READY=0
for _ in $(seq 1 30); do
  curl -s -m 5 -o /dev/null -w '%{http_code}' "http://127.0.0.1:${GO_DEV_PORT}/metrics" 2>/dev/null | grep -q '^200$' && { GO_DEV_READY=1; break; }
  sleep 1
done
[ "${GO_DEV_READY}" = "1" ] || die "Go follow-rpc 的 DevServer /metrics 没就绪（见 ${WORK}/go-rpc.log）"
say "   ✓ RAG follow-rpc 就绪（gRPC :${RAG_RPC_PORT} · actuator :${RAG_MGMT_PORT}）· Go DevServer :${GO_DEV_PORT} 就绪"

# ⚠️ **RAG 侧 Nacos 注册的必须是 gRPC 端口**（不是观测端口）：
#    `register-enabled: false` 关掉了自动注册（自动注册注册的是 **web 容器端口**）
#    ⇒ 同一个服务名下会出现「follow-rpc → 观测口」的错误实例 ⇒ follow-api 连过去就是断链。
nacos_reg_ok=0
for _ in $(seq 1 20); do
  nacos_list_instances follow-rpc | grep -q " ${RAG_RPC_PORT}\$" && { nacos_reg_ok=1; break; }
  sleep 1
done
[ "${nacos_reg_ok}" = "1" ] || die "Nacos 里 follow-rpc 没按 gRPC 端口 ${RAG_RPC_PORT} 注册（自动注册没关干净？）"
if nacos_list_instances follow-rpc | grep -q " ${RAG_MGMT_PORT}\$"; then
  die "Nacos 里 follow-rpc 出现了观测端口 ${RAG_MGMT_PORT} ⇒ 自动注册没关干净（会打断 follow-api 的 gRPC 链路）"
fi
say "   ✓ Nacos follow-rpc 只注册了 gRPC 端口 ${RAG_RPC_PORT}（无观测端口污染）"

# ══════════════════════════ 7. 播种 + 请求矩阵 + 故障注入 ═══════════════════
step "6/8 播种两侧同一批数据 + 跑请求矩阵（两侧同序）"
SEED_FOLLOW="INSERT INTO follow_relation (user_id,target_id,create_time,update_time) VALUES
 (1001,1002,'2026-01-01 00:00:01+00','2026-01-01 00:00:01+00'),
 (1001,1003,'2026-01-01 00:00:02+00','2026-01-01 00:00:02+00'),
 (1002,1001,'2026-01-01 00:00:03+00','2026-01-01 00:00:03+00'),
 (1002,1004,'2026-01-01 00:00:04+00','2026-01-01 00:00:04+00');"
SEED_BLOCK="INSERT INTO block_relation (user_id,target_id,create_time,update_time) VALUES
 (1001,1005,'2026-01-01 00:00:05+00','2026-01-01 00:00:05+00');"
for c in "${GO_PG_C}" "${RAG_PG_C}"; do
  docker exec "${c}" psql -U admin -d first_db -v ON_ERROR_STOP=1 -c "${SEED_FOLLOW}" >/dev/null || die "灌 follow_relation 失败：${c}"
  docker exec "${c}" psql -U admin -d first_db -v ON_ERROR_STOP=1 -c "${SEED_BLOCK}"  >/dev/null || die "灌 block_relation 失败：${c}"
done
# users 表只在 Go 侧 PG（下游 user-rpc 读的）
docker exec "${GO_PG_C}" psql -U admin -d first_db -v ON_ERROR_STOP=1 -c "INSERT INTO users (uid,username,password,email,status,score,create_time,update_time) VALUES
 (1001,'probe_u1001','x','p1@example.invalid',0,0,now(),now()),
 (1002,'probe_u1002','x','p2@example.invalid',0,0,now(),now()),
 (1003,'probe_u1003','x','p3@example.invalid',0,0,now(),now()),
 (1004,'probe_u1004','x','p4@example.invalid',0,0,now(),now()),
 (1005,'probe_u1005','x','p5@example.invalid',0,0,now(),now()),
 (1006,'probe_u1006','x','p6@example.invalid',0,0,now(),now());" >/dev/null \
  || die "灌 users 失败（Go 侧 user-rpc 需要它）"
say "   ✓ 两侧同一批行（follow 4 / block 1）+ users 6 行（Go 侧 user-rpc）"

RESP_MISMATCH=0
call_both() {   # $1=method $2=user $3=target
  local m="$1" u="$2" t="$3" g r
  if [ "${t}" = "-" ]; then
    g=$("${WORK}/bin/client" -addr "127.0.0.1:${GO_RPC_PORT}"  -method "$m" -user "$u")
    r=$("${WORK}/bin/client" -addr "127.0.0.1:${RAG_RPC_PORT}" -method "$m" -user "$u")
  else
    g=$("${WORK}/bin/client" -addr "127.0.0.1:${GO_RPC_PORT}"  -method "$m" -user "$u" -target "$t")
    r=$("${WORK}/bin/client" -addr "127.0.0.1:${RAG_RPC_PORT}" -method "$m" -user "$u" -target "$t")
  fi
  if [ "${g}" = "${r}" ]; then
    say "   ✅ ${m}(u=${u},t=${t}) 两侧一致：${g}"
  else
    RESP_MISMATCH=1
    say "   ❌ ${m}(u=${u},t=${t}) 两侧不一致"
    say "      Go : ${g}"
    say "      RAG: ${r}"
  fi
}
# 成功路径（8 个方法各一次成功）
call_both GetFollowList      1001 -
call_both GetBlockList       1001 -
call_both GetFollowerList    1001 -
call_both GetRecommendations 1001 -
call_both Follow             1001 1004
call_both Unfollow           1001 1002
call_both Block              1001 1006
call_both Unblock            1001 1005
# 失败路径（业务级：8 个方法各一次失败 ⇒ result="fail" 标签两侧都出现）
call_both GetFollowList      0 -
call_both GetBlockList       0 -
call_both GetFollowerList    0 -
call_both GetRecommendations 0 -
call_both Follow             1001 0
call_both Unfollow           1001 0
call_both Block              1001 0
call_both Unblock            1001 0
# 额外失败路径（自关系 / 目标用户不存在 ⇒ 走 ensureUserExists 分支）
call_both Follow             1001 1001
call_both Follow             1001 999999
[ "${RESP_MISMATCH}" = "0" ] || die "两侧响应不一致 ⇒ **指标差异不可归因于指标本身**（环境/行为问题）"

step "7/8 故障注入（制造 DB 错误 ⇒ 覆盖 db_error_total 的 (action,type) 维度）"
# ⚠️ 改名（不是删表）⇒ 每次只影响一个查询点，且**可原样恢复**。
#    覆盖 8/11 个 type（list_layer_follow_targets / list_blocker_users / —— 见 README 的边界说明）。
fault_both() {   # $1=表名 $2=method $3=user $4=target
  local tbl="$1" m="$2" u="$3" t="$4" c g r
  for c in "${GO_PG_C}" "${RAG_PG_C}"; do
    docker exec "${c}" psql -U admin -d first_db -c "ALTER TABLE ${tbl} RENAME TO ${tbl}_x;" >/dev/null 2>&1 \
      || die "改名 ${tbl} 失败：${c}"
  done
  g=$("${WORK}/bin/client" -addr "127.0.0.1:${GO_RPC_PORT}"  -method "$m" -user "$u" -target "$t")
  r=$("${WORK}/bin/client" -addr "127.0.0.1:${RAG_RPC_PORT}" -method "$m" -user "$u" -target "$t")
  for c in "${GO_PG_C}" "${RAG_PG_C}"; do
    docker exec "${c}" psql -U admin -d first_db -c "ALTER TABLE ${tbl}_x RENAME TO ${tbl};" >/dev/null 2>&1 \
      || die "恢复 ${tbl} 失败：${c}"
  done
  if [ "${g}" = "${r}" ]; then
    say "   ✅ 故障[${tbl}] ${m} 两侧一致：$(echo "${g}" | cut -d'|' -f3,6)"
  else
    RESP_MISMATCH=1
    say "   ❌ 故障[${tbl}] ${m} 两侧不一致"
    say "      Go : ${g}"
    say "      RAG: ${r}"
  fi
}
fault_both follow_relation GetFollowList      1001 0
fault_both block_relation  GetBlockList       1001 0
fault_both follow_relation GetFollowerList    1001 0
fault_both follow_relation GetRecommendations 1001 0
fault_both block_relation  GetRecommendations 1001 0
fault_both block_relation  Follow             1001 1004
fault_both follow_relation Follow             1001 1004
fault_both follow_relation Unfollow           1001 1003
fault_both block_relation  Block              1001 1006
fault_both block_relation  Unblock            1001 1005
[ "${RESP_MISMATCH}" = "0" ] || die "故障注入期间两侧响应不一致（环境/行为问题）"

# ══════════════════════════ 8. 抓取 + 比对 ═══════════════════════════════════
step "8/8 抓取两侧指标 + 比对 series 集合"
curl -s -m 20 "http://127.0.0.1:${GO_DEV_PORT}/metrics"                > "${WORK}/go-metrics.txt"  || die "抓 Go /metrics 失败"
curl -s -m 20 "http://127.0.0.1:${RAG_MGMT_PORT}/actuator/prometheus"  > "${WORK}/rag-metrics.txt" || die "抓 RAG /actuator/prometheus 失败"
GO_N=$(grep -c '^follow_rpc_' "${WORK}/go-metrics.txt"  || true)
RAG_N=$(grep -c '^follow_rpc_' "${WORK}/rag-metrics.txt" || true)
say "   Go  follow_rpc_* 行数 = ${GO_N} · RAG follow_rpc_* 行数 = ${RAG_N}"
[ "${GO_N}" -gt 0 ] || die "Go 侧没抓到 follow_rpc_* （DevServer 没开？）"
[ "${RAG_N}" -gt 0 ] || die "RAG 侧没抓到 follow_rpc_* ⇒ **抓取出口仍然是坏的**"

python3 "${HERE}/compare.py" "${WORK}/go-metrics.txt" "${WORK}/rag-metrics.txt" "${HERE}/expected-diff.txt"
CMP_EXIT=$?

# ══════════════════════════ cleanup（已在脚本开头装 trap）═══════════════════
echo
if [ "${CMP_EXIT}" = "0" ]; then
  say "✅ 两侧 follow_rpc_* series 集合一致（差异与冻结期望逐条相同）"
  exit 0
fi
say "❌ 两侧 follow_rpc_* series 集合存在未登记差异（详见上面的判定段）"
exit 1
