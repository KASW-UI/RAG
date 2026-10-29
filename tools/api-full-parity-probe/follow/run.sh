#!/usr/bin/env bash
#
# follow 域 **HTTP 层**双侧活体对拍探针。
#
# 真源：Sea-RideTheWind_golang/service/follow/{api,rpc}
#       + Sea-RideTheWind_golang/service/user/user/rpc（**下游 user-rpc**，也是真源）
#       （**只读**：Go 侧用真源真正在用的生成码/二进制，真源仓一个字节都不改）
# 被测：RAG/service/follow/{follow-api,follow-rpc}
#
# 定位：补 RoundPlan `docs/RoundPlan/Round-2.md` T-2.5 / 证据缺口 G-1 ——
#   台账写「52/52 逐字节一致」，但探针脚本全在 /tmp 且**已消失** ⇒ 证据不可复算。
#   ⇒ 本探针把「两侧同时起服务 + 播种同一行 + 同一请求 + 比对字节」搬进版本控制。
#
# ⚠️ **真源冻结 ⇒ 只能改「副本」**：Go 侧的配置副本把真源 yaml 里写死的主机名
#   （`etcd:2379` / `postgres` / `redis:6379` / `jaeger:4317`）改成宿主可达地址。
#   ⇒ 真源仓全程 `git status` 干净（脚本第 3 步会断言）。
#
# 用法：
#   bash tools/api-full-parity-probe/follow/run.sh
#   GO_REPO=/path/to/Sea-RideTheWind_golang bash tools/api-full-parity-probe/follow/run.sh
#   RECORD=1 bash tools/api-full-parity-probe/follow/run.sh   # 用 Go 侧真源重录 expected.txt
#
# 依赖：docker（宿主已在跑 Sea-etcd/kafka/minio/jaeger/nacos/redis）· Go toolchain（≥1.25）·
#       JDK 21+ · mvn（离线 -o）· curl · xxd · python3。
#
# 退出码：0 = 双侧逐字节一致；1 = 不一致；2 = 环境问题 / 真源漂移。
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAG_ROOT="$(cd "${HERE}/../../.." && pwd)"

# ── 宿主中间件端口（可用 env 覆盖；默认值 = 本机实测）────────────────────────
ETCD_PORT="${ETCD_PORT:-49319}"      # Sea-etcd    49319→2379
# ⚠️ etcdctl 是 **在容器里跑**（`docker exec Sea-etcd etcdctl`）⇒ 端点必须是**容器内端口** 2379
ETCD_CTL_ENDPOINT="${ETCD_CTL_ENDPOINT:-http://127.0.0.1:2379}"
REDIS_PORT="${REDIS_PORT:-49380}"    # Sea-redis   49380→6379
JAEGER_PORT="${JAEGER_PORT:-49317}"  # Sea-jaeger  49317→4317（OTLP gRPC）
NACOS_PORT="${NACOS_PORT:-49848}"    # Sea-nacos   49848→8848

# ── 端口（**本 lane 专用段 18101-18110**，别的 lane 用别的段）──────────────────
# ⚠️ 本探针比 article 探针多起**两个**进程（Go user-rpc 下游 + RAG follow-rpc），
#    所以专用段比 article 多用几个；**全部落在 18101-18110 内**，不与他人重叠。
# ⚠️ 两个 Postgres 另有**端口阶梯**兜底（18116/18117/18126/18127）——
#    只在**首选端口被别人占住**时才会用到（占口的容器**绝不**动）。
GO_API_PORT="${GO_API_PORT:-18101}"        # Go follow-api（HTTP）
RAG_API_PORT="${RAG_API_PORT:-18102}"      # RAG follow-api（HTTP）
GO_RPC_PORT="${GO_RPC_PORT:-18103}"        # Go follow-rpc（gRPC，etcd 注册 follow.rpc）
GO_USER_RPC_PORT="${GO_USER_RPC_PORT:-18104}"  # Go user-rpc（gRPC，etcd 注册 user.rpc）
RAG_RPC_PORT="${RAG_RPC_PORT:-18105}"      # RAG follow-rpc（gRPC，Nacos 注册 follow-rpc）
GO_PG_PORT="${GO_PG_PORT:-18106}"          # Go 侧 Postgres（user-rpc + follow-rpc 共用）
RAG_PG_PORT="${RAG_PG_PORT:-18107}"        # RAG 侧 Postgres（follow-rpc 的 Flyway 建表）
GO_API_DEV_PORT="${GO_API_DEV_PORT:-18108}"    # Go follow-api 的 DevServer(/metrics,/pprof)
GO_RPC_DEV_PORT="${GO_RPC_DEV_PORT:-18109}"    # Go follow-rpc 的 DevServer
GO_USER_DEV_PORT="${GO_USER_DEV_PORT:-18110}"  # Go user-rpc 的 DevServer

# ── 两侧各自的 Postgres（容器名带 $$ ⇒ 别的 lane 的 cleanup 绝不可能删掉本轮的库）──
GO_PG_CONTAINER="${GO_PG_CONTAINER:-r2follow-go-pg-$$}"
RAG_PG_CONTAINER="${RAG_PG_CONTAINER:-r2follow-rag-pg-$$}"

# ── 验签密钥（必须与 Go followcenter.yaml 的 UserAuth.AccessSecret 一致）────────
JWT_SECRET="${JWT_SECRET:-ae0536f9-6450-4606-8e13-5a19ed505da0}"
# ── 登录用户（JWT 的 userId claim）───────────────────────────────────────────
JWT_USER_ID="${JWT_USER_ID:-1001}"

WORK="$(mktemp -d)"
GO_PIDS=(); RAG_PIDS=()
# RAG 侧 api 进程号（第 7 步起进程时填）⇒ 只给测试钩子 `PROBE_STOP_RAG_AFTER` 用
RAG_API_PID=""
say()  { printf '%s\n' "$*"; }
step() { printf '\n== %s ==\n' "$*"; }
dump_logs() {
  local f
  for f in go-user-rpc.log go-rpc.log go-api.log rag-rpc.log rag-api.log; do
    [ -f "${WORK:-/nonexistent}/${f}" ] || continue
    printf '\n---- %s（末 30 行）----\n' "${f}" >&2
    tail -30 "${WORK}/${f}" | sed 's/^/   | /' >&2
  done
}
die()  { printf '!! %s\n' "$*" >&2; dump_logs; exit 2; }

host_port_open() { nc -z -G 2 127.0.0.1 "$1" >/dev/null 2>&1; }

# ── Nacos 实例管理（只动**本探针自己注册**的实例，不误伤别的 lane）────────────
# ⚠️ 只删「端口已经没人监听」的**死实例** —— 别的 lane 若有活着的同名实例，
#    删掉它会让对端的 client 随机选中死实例 ⇒ 探针结果不确定。
nacos_list_instances() {   # $1 = 服务名
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
nacos_deregister_dead() {   # $1 = 服务名
  local svc="$1" ip port
  while read -r ip port; do
    [ -n "${ip}" ] && [ -n "${port}" ] || continue
    if ! host_port_open "${port}"; then
      curl -s -m 5 -X DELETE "http://127.0.0.1:${NACOS_PORT}/nacos/v1/ns/instance?serviceName=${svc}&groupName=DEFAULT_GROUP&namespaceId=public&ip=${ip}&port=${port}" >/dev/null 2>&1
      say "   · 已注销 Nacos 死实例 ${svc} ${ip}:${port}"
    fi
  done <<EOF
$(nacos_list_instances "$1")
EOF
}
nacos_register() {   # $1 = 服务名  $2 = 端口
  # ⚠️ **ephemeral=true + 保活循环**（不是 persistent）：
  #    ① persistent（ephemeral=false）走 Nacos 的 **Raft** 持久化组
  #       ⇒ 本机 Nacos 实测报 `The Raft Group [naming_persistent_service_v2] did not find
  #       the Leader node` ⇒ **持久实例根本注册不进去**（2026-10-29 实测）。
  #    ② ephemeral 实例靠**客户端心跳**保活；这里是 curl 手工注册 ⇒ 没人发心跳
  #       ⇒ 约 15s 后被 Nacos 摘掉 ⇒ 表现为「服务名在、hosts 为空」
  #       ⇒ RAG 侧 `No servers found for user-rpc` ⇒ 业务码塌成 5001（实测）。
  #    ⇒ 解法：**每 5s 重新 POST 一次**（注册会刷新 lastBeat ⇒ 永不过期）。
  curl -s -m 10 -X POST "http://127.0.0.1:${NACOS_PORT}/nacos/v1/ns/instance" \
    --data-urlencode "serviceName=$1" \
    --data-urlencode "groupName=DEFAULT_GROUP" \
    --data-urlencode "namespaceId=public" \
    --data-urlencode "ip=127.0.0.1" \
    --data-urlencode "port=$2" \
    --data-urlencode "ephemeral=true" >/dev/null 2>&1
}
# ⚠️ **保活循环**：后台每 5s 重注册一次（见 nacos_register 的注释）。
nacos_keepalive() {   # $1 = 服务名  $2 = 端口
  while true; do
    nacos_register "$1" "$2"
    sleep 5
  done
}
# ⚠️ **注册完必须断言实例真的在列表里**（hosts 非空且端口匹配）——
#    「服务名存在」≠「实例存在」（临时实例被摘掉时就是这个样子）。
nacos_assert_instance() {   # $1 = 服务名  $2 = 端口
  local i
  for i in $(seq 1 20); do
    nacos_list_instances "$1" | grep -q " ${2}$" && return 0
    sleep 1
  done
  return 1
}
nacos_deregister() {   # $1 = 服务名  $2 = 端口
  curl -s -m 5 -X DELETE "http://127.0.0.1:${NACOS_PORT}/nacos/v1/ns/instance?serviceName=$1&groupName=DEFAULT_GROUP&namespaceId=public&ip=127.0.0.1&port=$2" >/dev/null 2>&1
}

# ── etcd：键是**前缀族** `<key>/<leaseID>`（go-zero core/discov）────────────────
# ⚠️ 未显式配 `etcd.ID` 时键 = `<key>/<leaseID>`（`core/discov/publisher.go:191-196`
#    + `core/discov/clients.go:39-41` `makeEtcdKey`）⇒ **精确键查永远查不到**。
etcd_get_value() {   # $1 = 键前缀 ⇒ 打印第一个匹配的值
  docker exec Sea-etcd etcdctl --endpoints="${ETCD_CTL_ENDPOINT}" get "$1" --prefix --print-value-only 2>/dev/null | head -1
}
etcd_has_key() { [ -n "$(etcd_get_value "$1")" ]; }
# ⚠️ **只删死实例的注册键**：别的 lane 若有活的同名 Go 实例，删掉它的 etcd 注册
#    会让它的 client 失去下游 ⇒ 那不是本探针能做的事。
etcd_del_dead() {   # $1 = 键前缀
  local k v ep
  for k in $(docker exec Sea-etcd etcdctl --endpoints="${ETCD_CTL_ENDPOINT}" get "$1" --prefix --keys-only 2>/dev/null); do
    [ -n "${k}" ] || continue
    v=$(docker exec Sea-etcd etcdctl --endpoints="${ETCD_CTL_ENDPOINT}" get "$k" --print-value-only 2>/dev/null | head -1)
    ep="${v##*:}"
    if ! host_port_open "${ep}"; then
      docker exec Sea-etcd etcdctl --endpoints="${ETCD_CTL_ENDPOINT}" del "$k" >/dev/null 2>&1
      say "   · 已清理 etcd 死实例键 ${k}（${v}）"
    else
      say "!! etcd 前缀 ${1} 下有**活实例** ${v} ⇒ 本探针不与之共用（避免 client 随机选中别人的实例）"
      exit 2
    fi
  done
}

# ── 端口预检：踢掉上一轮残留的探针进程，否则 Go 侧 panic: address already in use ──
free_probe_ports() {
  local p pid
  for p in "${GO_API_PORT}" "${RAG_API_PORT}" "${GO_RPC_PORT}" "${GO_USER_RPC_PORT}" \
           "${RAG_RPC_PORT}" "${GO_API_DEV_PORT}" "${GO_RPC_DEV_PORT}" "${GO_USER_DEV_PORT}"; do
    for pid in $(lsof -ti tcp:"${p}" 2>/dev/null); do
      kill -9 "${pid}" 2>/dev/null
      say "   · 踢掉占用 :${p} 的残留进程 ${pid}"
    done
  done
  sleep 1
}

# ── 就绪闸门：等端点真的答得上（**必须 code=200**，不是 1015 / 不是 5001）────────
# ⚠️ 「端口已监听」≠「下游 rpc 已就绪」⇒ 首请求可能超时 ⇒ 业务码塌成 1015
#    ⇒ 那不是「两侧漂移」，是「没准备好」⇒ 必须先等就绪，再判对错。
# ⚠️ **5001 也不算就绪**：本域的 5001 = 「下游 user-rpc 挂了/找不到」
#    （Go helper.go:83-93 的分支②）⇒ 用它当就绪判据会把「下游没起来」当成「两侧一致」。
JWT_TOKEN=""
wait_ready() {   # $1 = 端口  $2 = 标签
  local p="$1" tag="$2" body
  for _ in $(seq 1 40); do
    body=$(curl -s -m 10 -X POST "http://127.0.0.1:${p}/follow/v1/follow/list" \
             -H "Authorization: Bearer ${JWT_TOKEN}" -H 'Content-Type: application/json' \
             --data-binary '{"user_id":"1001"}' 2>/dev/null)
    case "${body}" in
      *'"code":200'*) say "   ✓ ${tag} 就绪（首个 code=200 响应已到）"; return 0 ;;
      *) sleep 2 ;;
    esac
  done
  say "   ❌ ${tag} 轮询 40 次仍拿不到 code=200 响应 ⇒ 不进请求矩阵"
  return 1
}

cleanup() {
  local p
  [ -n "${KEEPALIVE_PID:-}" ] && kill "${KEEPALIVE_PID}" 2>/dev/null
  for p in ${GO_PIDS[@]+"${GO_PIDS[@]}"} ${RAG_PIDS[@]+"${RAG_PIDS[@]}"}; do kill "$p" 2>/dev/null; done
  sleep 1
  for p in ${GO_PIDS[@]+"${GO_PIDS[@]}"} ${RAG_PIDS[@]+"${RAG_PIDS[@]}"}; do kill -9 "$p" 2>/dev/null; done
  # 只注销**本探针自己注册**的实例（ip+port 精确匹配）
  nacos_deregister user-rpc "${GO_USER_RPC_PORT}"
  nacos_deregister follow-rpc "${RAG_RPC_PORT}"
  docker rm -f "${GO_PG_CONTAINER}" >/dev/null 2>&1
  docker rm -f "${RAG_PG_CONTAINER}" >/dev/null 2>&1
  # ⚠️ **不删临时目录**：`rm -rf` 在本机会被回收站守卫拦下 ⇒ 留着由系统清理。
  if [ "${KEEP_WORK:-0}" = "1" ]; then
    say "   · KEEP_WORK=1 ⇒ 保留 ${WORK}"
  fi
}
trap cleanup EXIT

# ══════════════════════════ 0. 前置检查（缺 ⇒ exit 2，绝不静默降级）═════════════════
step "0/9 前置检查"
for cmd in docker go mvn curl xxd python3 lsof nc; do
  command -v "${cmd}" >/dev/null 2>&1 || die "缺命令：${cmd}"
done
GO_REPO="${GO_REPO:-/Users/pr/Documents/java&golang/Sea-RideTheWind_golang}"
[ -d "${GO_REPO}/service/follow" ] || die "找不到 Go 真源：${GO_REPO}/service/follow（用 GO_REPO=... 指定）"
[ -d "${GO_REPO}/service/user/user/rpc" ] || die "找不到 Go 真源：${GO_REPO}/service/user/user/rpc"
for pair in "Sea-etcd:${ETCD_PORT}" "Sea-redis:${REDIS_PORT}" "Sea-nacos:${NACOS_PORT}" "Sea-jaeger:${JAEGER_PORT}"; do
  c="${pair%%:*}"; p="${pair##*:}"
  docker ps --format '{{.Names}}' | grep -qx "${c}" || die "容器 ${c} 没在跑（宿主中间件缺失）"
  nc -z -G 2 127.0.0.1 "${p}" || die "宿主端口 ${p} 不通（${c} 的映射口）"
done
say "   ✓ 命令齐 / 中间件在跑"
docker exec Sea-etcd etcdctl --endpoints="${ETCD_CTL_ENDPOINT}" endpoint health >/dev/null 2>&1 \
  || die "容器内 etcd 端点 ${ETCD_CTL_ENDPOINT} 不可用（ETCD_CTL_ENDPOINT 可覆盖）"
free_probe_ports
# 死实例清理（活实例 ⇒ exit 2，见函数注释）
etcd_del_dead follow.rpc
etcd_del_dead user.rpc
nacos_deregister_dead follow-rpc
nacos_deregister_dead user-rpc

# ══════════════════════════ 1. 两侧各自的 Postgres ══════════════════════════════
step "1/9 两侧 Postgres"
# 🔴 **必须先清掉「上一轮遗留的同前缀容器」**（2026-10-29 实测踩过）：
#    容器名带 `$$` ⇒ 每轮唯一 ⇒ 上一轮的容器**不会被本轮的 cleanup 删掉**
#    （上一轮若被强杀 ⇒ cleanup 没跑 ⇒ 容器残留）。
#    ⚠️ **Exited 的容器仍然占着发布端口**（docker-proxy 还在）⇒
#    `docker run -p 18106:5432` 直接失败（`port is already allocated`）
#    ⇒ 表现为「Go 侧 PG 起不来（:18106）」⇒ **与真源/代码无关的假故障**。
#    ⇒ 只删**本探针自己的前缀**（`r2follow-`）⇒ 别的 lane 的容器绝不动。
purge_stale_my_pg() {
  local c
  for c in $(docker ps -aq --filter "name=r2follow-" 2>/dev/null); do
    docker rm -f "${c}" >/dev/null 2>&1
    say "   · 已清理上一轮遗留的探针容器 ${c}"
  done
  # ⚠️ 容器删掉后 **docker-proxy 释放监听口要一会儿** ⇒ 立刻重绑同一端口会失败
  #    （实测：清掉残留容器后立刻 `docker run -p 18106:5432` ⇒ 端口仍被占 ⇒
  #    报 `port is already allocated` ⇒ 探针假故障）。
  sleep 3
}
purge_stale_my_pg
# ⚠️ 就绪判据必须是「**宿主端口**通」：`docker exec psql` / `pg_isready` 走的是**容器内 socket**，
#    与 `-p` 发布**无关** ⇒ 容器活着 ≠ 宿主端口通。
start_pg() {   # $1 = 容器名  $2 = 宿主端口
  local c="$1" p="$2"
  docker rm -f "${c}" >/dev/null 2>&1
  docker run -d --name "${c}" -e POSTGRES_USER=admin -e POSTGRES_PASSWORD=sea \
    -e POSTGRES_DB=first_db -p "${p}:5432" postgres:16-alpine >/dev/null 2>&1 || return 1
  for _ in $(seq 1 40); do
    docker exec "${c}" pg_isready -U admin -d first_db >/dev/null 2>&1 && break
    sleep 1
  done
  docker exec "${c}" pg_isready -U admin -d first_db >/dev/null 2>&1 || return 1
  for _ in $(seq 1 15); do
    host_port_open "${p}" && return 0
    sleep 1
  done
  return 1
}
# 端口阶梯：宿主端口被**别人**占着 ⇒ 换下一个（**绝不**动别人的容器）
# ⚠️ **诊断信息必须走 stderr**：本函数的值是**命令替换**取回的
#    ⇒ `say`（stdout）会把端口号污染成「诊断文本 + 端口号」⇒
#    后续 sed 的替换串里带**换行** ⇒ `sed: unescaped newline inside substitute pattern`
#    ⇒ 整轮探针崩在第 3 步（2026-10-29 实测踩过）。
GO_PG_CANDIDATES=(${GO_PG_PORT:+${GO_PG_PORT}} 18106 18116 18126)
RAG_PG_CANDIDATES=(${RAG_PG_PORT:+${RAG_PG_PORT}} 18107 18117 18127)
pick_pg_port() {   # $1 = 容器名  $2.. = 候选端口 ⇒ 成功时把端口打到 stdout
  local c="$1"; shift
  local p
  for p in "$@"; do
    if start_pg "${c}" "${p}"; then printf '%s' "${p}"; return 0; fi
    printf '   · :%s 起不来（容器内没就绪 / 宿主端口没通 / 端口被占）⇒ 换下一个\n' "${p}" >&2
    docker rm -f "${c}" >/dev/null 2>&1
  done
  return 1
}
GO_PG_PORT="$(pick_pg_port "${GO_PG_CONTAINER}" ${GO_PG_CANDIDATES[@]+${GO_PG_CANDIDATES[@]}})" \
  || die "Go 侧 PG 起不来（候选端口：${GO_PG_CANDIDATES[*]}）"
RAG_PG_PORT="$(pick_pg_port "${RAG_PG_CONTAINER}" ${RAG_PG_CANDIDATES[@]+${RAG_PG_CANDIDATES[@]}})" \
  || die "RAG 侧 PG 起不来（候选端口：${RAG_PG_CANDIDATES[*]}）"
say "   ✓ 两侧 Postgres 就绪（Go :${GO_PG_PORT} · RAG :${RAG_PG_PORT}，**宿主端口已验证可连**）"

# ══════════════════════════ 2. 构建 Go 侧二进制（真源**只读**）══════════════════════
step "2/9 构建 Go 侧二进制（真源只读 ⇒ 产物落临时目录）"
mkdir -p "${WORK}/bin" "${WORK}/etc" "${WORK}/log"
( cd "${GO_REPO}" && CGO_ENABLED=0 GOPROXY=off GOTOOLCHAIN=local go build \
    -o "${WORK}/bin/follow-api"  ./service/follow/api \
  && CGO_ENABLED=0 GOPROXY=off GOTOOLCHAIN=local go build \
    -o "${WORK}/bin/follow-rpc"  ./service/follow/rpc \
  && CGO_ENABLED=0 GOPROXY=off GOTOOLCHAIN=local go build \
    -o "${WORK}/bin/user-rpc"    ./service/user/user/rpc ) || die "Go 侧构建失败"
[ -x "${WORK}/bin/follow-api" ] && [ -x "${WORK}/bin/follow-rpc" ] && [ -x "${WORK}/bin/user-rpc" ] \
  || die "Go 侧二进制没产出"
say "   ✓ follow-api / follow-rpc / user-rpc 构建完成"

# ══════════════════════════ 3. Go 侧配置副本（真源 yaml 写死主机名 ⇒ 只改副本）══════
step "3/9 生成 Go 侧配置副本"
# ⚠️ 逐条断言「改了哪项」——2026-10-29 实测（article 探针）：`^  Port: 8889$` 这种**缩进写错**
#    的 sed 会静默不命中 ⇒ 副本里仍是旧端口 ⇒ 端口一被占就 panic ⇒ 探针假挂。
sed -e "s|^ListenOn: 0.0.0.0:8082$|ListenOn: 0.0.0.0:${GO_RPC_PORT}|" \
    -e "s|etcd:2379|127.0.0.1:${ETCD_PORT}|g" \
    -e "s|^  Path: ../../../../log/follow-rpc$|  Path: ${WORK}/log/follow-rpc|" \
    -e "s|jaeger:4317|127.0.0.1:${JAEGER_PORT}|g" \
    -e "s|^  Port: 16065$|  Port: ${GO_RPC_DEV_PORT}|" \
    -e "s|^  Host: \"postgres\"$|  Host: \"127.0.0.1\"|" \
    -e "s|^  Port: \"5432\"$|  Port: \"${GO_PG_PORT}\"|" \
    -e "s|^  Password: \"\${POSTGRES_PASSWORD}\"$|  Password: \"sea\"|" \
    "${GO_REPO}/service/follow/rpc/etc/follow.yaml" > "${WORK}/etc/follow.yaml"

sed -e "s|^Port: 8891$|Port: ${GO_API_PORT}|" \
    -e "s|^  Path: ../../../../log/follow-api$|  Path: ${WORK}/log/follow-api|" \
    -e "s|redis:6379|127.0.0.1:${REDIS_PORT}|g" \
    -e "s|jaeger:4317|127.0.0.1:${JAEGER_PORT}|g" \
    -e "s|^  Port: 16064$|  Port: ${GO_API_DEV_PORT}|" \
    -e "s|etcd:2379|127.0.0.1:${ETCD_PORT}|g" \
    "${GO_REPO}/service/follow/api/etc/followcenter.yaml" > "${WORK}/etc/followcenter.yaml"

sed -e "s|^ListenOn: 0.0.0.0:9004$|ListenOn: 0.0.0.0:${GO_USER_RPC_PORT}|" \
    -e "s|^  Path: ../../../../log/user-rpc$|  Path: ${WORK}/log/user-rpc|" \
    -e "s|^  Host: \"postgres\"$|  Host: \"127.0.0.1\"|" \
    -e "s|^  Port: \"5432\"$|  Port: \"${GO_PG_PORT}\"|" \
    -e "s|^  Password: \"\${POSTGRES_PASSWORD}\"$|  Password: \"sea\"|" \
    -e "s|redis:6379|127.0.0.1:${REDIS_PORT}|g" \
    -e "s|jaeger:4317|127.0.0.1:${JAEGER_PORT}|g" \
    -e "s|^  Port: 9094$|  Port: ${GO_USER_DEV_PORT}|" \
    -e "s|etcd:2379|127.0.0.1:${ETCD_PORT}|g" \
    "${GO_REPO}/service/user/user/rpc/etc/user.yaml" > "${WORK}/etc/user.yaml"

grep -q "^ListenOn: 0.0.0.0:${GO_RPC_PORT}$"       "${WORK}/etc/follow.yaml"      || die "副本里 follow-rpc 的 ListenOn 没改成 ${GO_RPC_PORT}（sed 没命中？）"
grep -q "^  Port: \"${GO_PG_PORT}\"$"               "${WORK}/etc/follow.yaml"      || die "副本里 follow-rpc 的 Postgres Port 没改成 ${GO_PG_PORT}（sed 没命中？）"
grep -q "^  Host: \"127.0.0.1\"$"                  "${WORK}/etc/follow.yaml"      || die "副本里 follow-rpc 的 Postgres Host 没改成 127.0.0.1（sed 没命中？）"
grep -q "^  Password: \"sea\"$"                     "${WORK}/etc/follow.yaml"      || die "副本里 follow-rpc 的 Postgres Password 没改成 sea（sed 没命中？）"
grep -q "^Port: ${GO_API_PORT}$"                   "${WORK}/etc/followcenter.yaml" || die "副本里 follow-api 的 Port 没改成 ${GO_API_PORT}（sed 没命中？）"
grep -q "127.0.0.1:${REDIS_PORT}"                  "${WORK}/etc/followcenter.yaml" || die "副本里 follow-api 的 Redis Host 没改成 127.0.0.1（sed 没命中？）"
grep -q "^ListenOn: 0.0.0.0:${GO_USER_RPC_PORT}$"  "${WORK}/etc/user.yaml"        || die "副本里 user-rpc 的 ListenOn 没改成 ${GO_USER_RPC_PORT}（sed 没命中？）"
grep -q "^  Port: \"${GO_PG_PORT}\"$"               "${WORK}/etc/user.yaml"        || die "副本里 user-rpc 的 Postgres Port 没改成 ${GO_PG_PORT}（sed 没命中？）"
for f in follow.yaml followcenter.yaml user.yaml; do
  grep -q "etcd:2379" "${WORK}/etc/${f}" && die "副本 ${f} 里还残留 etcd:2379（sed 没命中？）"
done
( cd "${GO_REPO}" && git status --porcelain -- service/follow service/user | grep -q . ) \
  && die "真源仓 service/follow|service/user 有改动 ⇒ 本探针**不得**改真源"
say "   ✓ 配置副本已生成（真源 service/follow|service/user 仍然干净）"

# ══════════════════════════ 4. JWT（自签；两侧用同一把 secret 验签）══════════════════
step "4/9 签发 JWT（claim userId=${JWT_USER_ID}，两侧同一把 secret）"
JWT_TOKEN="$(python3 "${HERE}/mint-jwt.py" "${JWT_SECRET}" "${JWT_USER_ID}")"
[ -n "${JWT_TOKEN}" ] || die "JWT 签发失败"
say "   ✓ token 已签发（${#JWT_TOKEN} 字节）"

# ══════════════════════════ 5. 起 Go 侧 user-rpc（下游，两侧共用同一个）══════════════
step "5/9 起 Go 侧 user-rpc（etcd 注册 user.rpc；并注册进 Nacos 供 RAG 侧解析）"
docker exec Sea-etcd etcdctl --endpoints="${ETCD_CTL_ENDPOINT}" del "user.rpc" --prefix >/dev/null 2>&1
( cd "${WORK}" && "${WORK}/bin/user-rpc" -f "${WORK}/etc/user.yaml" >"${WORK}/go-user-rpc.log" 2>&1 ) &
GO_PIDS+=($!)
for _ in $(seq 1 40); do etcd_has_key user.rpc && break; sleep 1; done
etcd_has_key user.rpc || die "Go user-rpc 40s 内没注册进 etcd（见 ${WORK}/go-user-rpc.log）"
# ⚠️ go-zero **先注册 etcd 再 bind 端口** ⇒ 「etcd 有键」≠「端口已监听」⇒ 必须再等一次端口。
for _ in $(seq 1 20); do nc -z -G 1 127.0.0.1 "${GO_USER_RPC_PORT}" && break; sleep 1; done
nc -z -G 2 127.0.0.1 "${GO_USER_RPC_PORT}" || die "Go user-rpc 注册了但端口没监听（见 ${WORK}/go-user-rpc.log）"
# ⚠️ RAG 侧 follow-rpc 用 **Nacos**（discovery:///user-rpc）找下游 ⇒ 同一个进程
#    必须**同时**在 Nacos 里可见 ⇒ 这里手工注册（ip+port 精确匹配，cleanup 只删自己那条）。
nacos_register user-rpc "${GO_USER_RPC_PORT}"
nacos_assert_instance user-rpc "${GO_USER_RPC_PORT}" \
  || die "user-rpc 没出现在 Nacos 实例列表里（服务名在、hosts 为空 ⇒ 临时实例被摘掉？）"
# ⚠️ 起保活循环（否则约 15s 后实例被摘 ⇒ RAG 侧 `No servers found`）
nacos_keepalive user-rpc "${GO_USER_RPC_PORT}" &
KEEPALIVE_PID=$!
say "   ✓ 已注册 user.rpc → $(etcd_get_value user.rpc)（:${GO_USER_RPC_PORT} 已监听；Nacos user-rpc 已登记）"

# ══════════════════════════ 6. 起 Go 侧 follow-rpc + follow-api ═════════════════════
step "6/9 起 Go 侧 follow-rpc（etcd 注册 follow.rpc）与 follow-api（:${GO_API_PORT}）"
docker exec Sea-etcd etcdctl --endpoints="${ETCD_CTL_ENDPOINT}" del "follow.rpc" --prefix >/dev/null 2>&1
( cd "${WORK}" && "${WORK}/bin/follow-rpc" -f "${WORK}/etc/follow.yaml" >"${WORK}/go-rpc.log" 2>&1 ) &
GO_PIDS+=($!)
for _ in $(seq 1 40); do etcd_has_key follow.rpc && break; sleep 1; done
etcd_has_key follow.rpc || die "Go follow-rpc 40s 内没注册进 etcd（见 ${WORK}/go-rpc.log）"
for _ in $(seq 1 20); do nc -z -G 1 127.0.0.1 "${GO_RPC_PORT}" && break; sleep 1; done
nc -z -G 2 127.0.0.1 "${GO_RPC_PORT}" || die "Go follow-rpc 注册了但端口没监听（见 ${WORK}/go-rpc.log）"
say "   ✓ 已注册 follow.rpc → $(etcd_get_value follow.rpc)（:${GO_RPC_PORT} 已监听）"
# ⚠️ 同上：Go 侧 `zrpc.MustNewClient` 启动时**阻塞拨号 3s** ⇒ 下游还没完全就绪就起 api
#    ⇒ api 进程直接 panic（`logx.Must`）⇒ 探针假挂。⇒ 等一下再起。
sleep 3
( cd "${WORK}" && "${WORK}/bin/follow-api" -f "${WORK}/etc/followcenter.yaml" >"${WORK}/go-api.log" 2>&1 ) &
GO_PIDS+=($!)
for _ in $(seq 1 40); do nc -z -G 1 127.0.0.1 "${GO_API_PORT}" && break; sleep 1; done
nc -z -G 2 127.0.0.1 "${GO_API_PORT}" || die "Go follow-api 没起来（见 ${WORK}/go-api.log）"
say "   ✓ Go 侧已监听 :${GO_API_PORT}"

# ══════════════════════════ 7. 起 RAG 侧 ═══════════════════════════════════════════
step "7/9 起 RAG 侧 follow-rpc（Nacos 注册 follow-rpc）与 follow-api（:${RAG_API_PORT}）"
( cd "${RAG_ROOT}" && mvn -o -q -pl service/follow/follow-rpc,service/follow/follow-api -am package -DskipTests ) \
  || die "RAG 侧构建失败"
RAG_RPC_JAR="${RAG_ROOT}/service/follow/follow-rpc/target/follow-rpc-0.0.1-SNAPSHOT.jar"
RAG_API_JAR="${RAG_ROOT}/service/follow/follow-api/target/follow-api-0.0.1-SNAPSHOT.jar"
[ -f "${RAG_RPC_JAR}" ] && [ -f "${RAG_API_JAR}" ] || die "RAG 侧 jar 没产出"
POSTGRES_HOST=127.0.0.1 POSTGRES_PORT="${RAG_PG_PORT}" POSTGRES_DB=first_db \
POSTGRES_USER=admin POSTGRES_PASSWORD=sea \
NACOS_ADDR=127.0.0.1 NACOS_PORT="${NACOS_PORT}" NACOS_NAMESPACE=public NACOS_GROUP=DEFAULT_GROUP \
FOLLOW_RPC_PORT="${RAG_RPC_PORT}" \
OTEL_ADDR=127.0.0.1 OTLP_PORT="${JAEGER_PORT}" \
  java -jar "${RAG_RPC_JAR}" >"${WORK}/rag-rpc.log" 2>&1 &
RAG_PIDS+=($!)
for _ in $(seq 1 60); do
  nacos_list_instances follow-rpc | grep -q " ${RAG_RPC_PORT}$" && break
  sleep 1
done
nacos_list_instances follow-rpc | grep -q " ${RAG_RPC_PORT}$" \
  || die "RAG follow-rpc 60s 内没注册进 Nacos（见 ${WORK}/rag-rpc.log）"
for _ in $(seq 1 30); do nc -z -G 1 127.0.0.1 "${RAG_RPC_PORT}" && break; sleep 1; done
nc -z -G 2 127.0.0.1 "${RAG_RPC_PORT}" || die "RAG follow-rpc 端口没监听（见 ${WORK}/rag-rpc.log）"
say "   ✓ RAG follow-rpc 已注册进 Nacos（:${RAG_RPC_PORT}）"
# ⚠️ **等 Nacos 名字服务把实例推给订阅方**再起 api：
#    follow-api 的 `grpc.client.follow-rpc.immediate-connect-timeout: 3s`
#    （= Go 侧 `zrpc.MustNewClient` 的 3s 阻塞拨号）⇒ 连不上就**直接起不来**。
#    ⇒ 注册完成 ≠ 订阅方已拿到实例 ⇒ 必须给名字服务一点时间（实测偶发）。
sleep 5
# ⚠️ 启动失败**重试**：devh 的 channel 在创建时就要 READY ⇒ 注册可见性有竞态 ⇒
#    重试 3 次（每次都重新起一个进程），仍失败才判环境问题。
RAG_API_STARTED=0
for attempt in 1 2 3; do
  # ⚠️ **`--grpc.client.follow-rpc.immediate-connect-timeout=60s`（探针专用覆盖）**
  #    该项默认 3s（= Go `zrpc.MustNewClient` 的 3s 阻塞拨号，见 DRIFT-G1-002），
  #    但**冷启动的 Nacos 客户端光初始化就吃掉约 3s**（实测日志：Nacos client 建于
  #    08:02:30.5，3s 后 08:02:33.5 即超时）⇒ 探针环境里 api 必然起不来。
  #    ⇒ 这里**只放宽启动期的连通道具**（连接一旦建立，后续请求路径一字未改）。
  REDIS_HOST=127.0.0.1 REDIS_PORT="${REDIS_PORT}" \
  NACOS_ADDR=127.0.0.1 NACOS_PORT="${NACOS_PORT}" NACOS_NAMESPACE=public NACOS_GROUP=DEFAULT_GROUP \
  FOLLOW_API_PORT="${RAG_API_PORT}" USER_JWT_SECRET="${JWT_SECRET}" \
  OTEL_ADDR=127.0.0.1 OTLP_PORT="${JAEGER_PORT}" \
    java -jar "${RAG_API_JAR}" \
      --grpc.client.follow-rpc.immediate-connect-timeout=60s \
      >"${WORK}/rag-api.log" 2>&1 &
  api_pid=$!
  RAG_API_PID="${api_pid}"
  RAG_PIDS+=($!)
  for _ in $(seq 1 60); do
    nc -z -G 1 127.0.0.1 "${RAG_API_PORT}" && { RAG_API_STARTED=1; break; }
    kill -0 "${api_pid}" 2>/dev/null || break
    sleep 1
  done
  if [ "${RAG_API_STARTED}" = "1" ]; then
    say "   ✓ RAG follow-api 第 ${attempt} 次启动成功（:${RAG_API_PORT}）"
    break
  fi
  say "   · RAG follow-api 第 ${attempt} 次启动没起来（见 ${WORK}/rag-api.log）⇒ 重试"
  kill -9 "${api_pid}" 2>/dev/null
  sleep 3
done
[ "${RAG_API_STARTED}" = "1" ] || die "RAG follow-api 三次都没起来（见 ${WORK}/rag-api.log）"

# ══════════════════════════ 8. 两侧灌同一行 ═══════════════════════════════════════
step "8/9 两侧灌同一行（follow_relation / block_relation）+ user-rpc 的 users 表"
# ⚠️ **两侧都必须有数据**：「两侧都空」分不出「相同」与「都空」⇒ 播种同一批行。
# ⚠️ create_time 全部**互不相同** ⇒ `ORDER BY create_time desc` 的顺序确定
#    （同值时 PG 的顺序不保证 ⇒ 输出顺序会随机 ⇒ 探针会假红）。
SEED_FOLLOW="INSERT INTO follow_relation (user_id,target_id,create_time,update_time) VALUES
 (1001,1002,'2026-01-01 00:00:01+00','2026-01-01 00:00:01+00'),
 (1001,1003,'2026-01-01 00:00:02+00','2026-01-01 00:00:02+00'),
 (1002,1001,'2026-01-01 00:00:03+00','2026-01-01 00:00:03+00'),
 (1002,1004,'2026-01-01 00:00:04+00','2026-01-01 00:00:04+00');"
SEED_BLOCK="INSERT INTO block_relation (user_id,target_id,create_time,update_time) VALUES
 (1001,1005,'2026-01-01 00:00:05+00','2026-01-01 00:00:05+00');"
for c in "${GO_PG_CONTAINER}" "${RAG_PG_CONTAINER}"; do
  docker exec "${c}" psql -U admin -d first_db -v ON_ERROR_STOP=1 -c "${SEED_FOLLOW}" >/dev/null || die "灌 follow_relation 失败：${c}"
  docker exec "${c}" psql -U admin -d first_db -v ON_ERROR_STOP=1 -c "${SEED_BLOCK}"  >/dev/null || die "灌 block_relation 失败：${c}"
done
# user-rpc 的 users 表只在 Go 侧 PG 里（两侧**调用同一个** user-rpc ⇒ 只需一份）
SEED_USERS="INSERT INTO users (uid,username,password,email,status,score,create_time,update_time) VALUES
 (1001,'probe_u1001','x','probe1001@example.invalid',0,0,now(),now()),
 (1002,'probe_u1002','x','probe1002@example.invalid',0,0,now(),now()),
 (1003,'probe_u1003','x','probe1003@example.invalid',0,0,now(),now()),
 (1004,'probe_u1004','x','probe1004@example.invalid',0,0,now(),now()),
 (1005,'probe_u1005','x','probe1005@example.invalid',0,0,now(),now());"
docker exec "${GO_PG_CONTAINER}" psql -U admin -d first_db -v ON_ERROR_STOP=1 -c "${SEED_USERS}" >/dev/null \
  || die "灌 users 失败：${GO_PG_CONTAINER}"
say "   ✓ 两侧同一批行（follow 4 行 / block 1 行）+ users 5 行（user-rpc 侧）"

# ══════════════════════════ 9. 请求矩阵：Go vs RAG vs expected.txt，逐字节 ═══════════
step "9/9 请求矩阵（Go vs RAG vs expected.txt ⇒ 逐字节）"
EXPECTED="${HERE}/expected.txt"
if [ "${RECORD:-0}" != "1" ]; then
  [ -f "${EXPECTED}" ] || die "缺少冻结的期望值：${EXPECTED}"
fi

wait_ready "${GO_API_PORT}"  "Go  侧" || die "Go 侧没就绪（见上面的日志）"
wait_ready "${RAG_API_PORT}" "RAG 侧" || die "RAG 侧没就绪（见上面的日志）"

# ⚠️ **FAILING-010 的修法（关键）**：每个请求用**独立的 mktemp 文件**收 body。
#    旧探针用**固定 -o 文件** ⇒ 请求失败时读到的是**上一次的残留 body** ⇒
#    陈旧数据被当成新结果 ⇒ 假绿。⇒ 这里绝不复用任何固定路径。
request_body() {   # $1=端口 $2=method $3=path $4=body $5=authmode ⇒ 打印 "<http_code>|<hex>"
  local port="$1" method="$2" path="$3" body="$4" auth="$5"
  local out code
  # ⚠️ **唯一文件**（FAILING-010 的修法）：每个请求一个独立 mktemp 文件。
  #    旧探针用**固定 -o 路径** ⇒ 请求失败时读到的是**上一次的残留 body**
  #    ⇒ 陈旧数据被当成新结果 ⇒ 假绿。
  # ⚠️ 文件**不删**：本机的 `rm` 被回收站守卫接管且**往 stdout 打印**
  #    （`mavis-trash: moved to trash: …`）⇒ 会污染本函数的返回值 ⇒ 两侧全被误判为不等。
  #    ⇒ 一律落在 ${WORK}（mktemp -d）里，由系统回收。
  out="$(mktemp "${WORK}/body.XXXXXX")" || return 1
  if [ "${auth}" = "valid" ]; then
    code=$(curl -s -o "${out}" -w '%{http_code}' -m 30 -X "${method}" \
             "http://127.0.0.1:${port}${path}" \
             -H "Authorization: Bearer ${JWT_TOKEN}" \
             -H 'Content-Type: application/json' \
             --data-binary "${body}" 2>/dev/null)
  elif [ "${auth}" = "bad" ]; then
    code=$(curl -s -o "${out}" -w '%{http_code}' -m 30 -X "${method}" \
             "http://127.0.0.1:${port}${path}" \
             -H "Authorization: Bearer not-a-jwt" \
             -H 'Content-Type: application/json' \
             --data-binary "${body}" 2>/dev/null)
  else
    code=$(curl -s -o "${out}" -w '%{http_code}' -m 30 -X "${method}" \
             "http://127.0.0.1:${port}${path}" \
             -H 'Content-Type: application/json' \
             --data-binary "${body}" 2>/dev/null)
  fi
  printf '%s|%s' "${code}" "$(xxd -p "${out}" | tr -d '\n')"
}

FAILED=0
RECORD_FILE=""
# ⚠️ **测试钩子**（只给 `mutate-parity.sh` 用，正常跑不用）：
#    PROBE_STOP_RAG_AFTER=<case 名> ⇒ 该用例跑完后**杀掉 RAG 侧 api 进程**。
#    用途：验证「请求失败时探针必须转红，而不是读到上一次的残留 body」
#    ⇒ 这正是 FAILING-010（固定 `-o` 文件 ⇒ 读到残留 body ⇒ 假绿）的守卫。
PROBE_STOP_RAG_AFTER="${PROBE_STOP_RAG_AFTER:-}"
# ⚠️ **不要在这里把 RAG_API_PID 重新置空** —— 它在第 7 步（起 RAG api 时）才有值；
#    在这里清空 ⇒ 测试钩子永远拿不到 PID ⇒ 「杀 RAG 侧」不发生 ⇒ M4 假绿（2026-10-29 实测）。
# 🧪 测试钩子：指定用例跑完后杀掉 RAG 侧 api（见 PROBE_STOP_RAG_AFTER 的说明）
maybe_stop_rag() {   # $1 = case 名
  [ -n "${PROBE_STOP_RAG_AFTER}" ] || return 0
  [ "$1" = "${PROBE_STOP_RAG_AFTER}" ] || return 0
  [ -n "${RAG_API_PID}" ] || return 0
  say "   🧪 测试钩子：杀掉 RAG 侧 api 进程（PROBE_STOP_RAG_AFTER=${PROBE_STOP_RAG_AFTER}）"
  kill -9 "${RAG_API_PID}" 2>/dev/null
}
run_case() {   # $1=name $2=method $3=path $4=body $5=authmode
  run_case_impl "$@"
  maybe_stop_rag "$1"
}
run_case_impl() {   # $1=name $2=method $3=path $4=body $5=authmode
  local name="$1" method="$2" path="$3" body="$4" auth="$5"
  local g r gh rh eh
  g="$(request_body "${GO_API_PORT}"  "${method}" "${path}" "${body}" "${auth}")"
  r="$(request_body "${RAG_API_PORT}" "${method}" "${path}" "${body}" "${auth}")"
  gh="${g##*|}"; rh="${r##*|}"
  local gcode="${g%%|*}"; rcode="${r%%|*}"
  if [ "${gcode}" = "000" ] || [ "${rcode}" = "000" ]; then
    FAILED=1
    say "   ❌ ${name}：请求失败（Go http_code=${gcode} · RAG http_code=${rcode}）"
    return
  fi
  eh=$(awk -F'|' -v n="CASE|${name}" '$1"|"$2==n {print $3"|"$4}' "${EXPECTED}")
  if [ "${RECORD_FILE}" != "" ]; then
    # RECORD 模式：期望值**取自 Go 真源**，同时仍然要求两侧一致（否则录的是错的）
    printf 'CASE|%s|%s|%s\n' "${name}" "${gcode}" "${gh}" >> "${RECORD_FILE}"
    if [ "${g}" != "${r}" ]; then
      FAILED=1
      say "   ❌ ${name}（RECORD：Go != RAG ⇒ 期望值不可信）"
      say "      raw Go  : [${g}]"
      say "      raw RAG : [${r}]"
      say "      Go : $(printf '%s' "${gh}" | xxd -r -p)"
      say "      RAG: $(printf '%s' "${rh}" | xxd -r -p)"
    else
      say "   📝 ${name}  Go == RAG（HTTP ${gcode} · ${#gh} 字节）⇒ 已记录待固化"
    fi
    return
  fi
  if [ "${g}" = "${r}" ] && [ "${g}" = "${eh}" ]; then
    say "   ✅ ${name}  Go == RAG == expected.txt（HTTP ${gcode} · ${#gh} 字节）"
  else
    FAILED=1
    say "   ❌ ${name}"
    [ "${gcode}" != "${rcode}" ] && say "      HTTP 状态码不同：Go=${gcode} · RAG=${rcode}"
    [ "${gh}" != "${rh}" ] && {
      say "      Go != RAG"
      say "      Go : $(printf '%s' "${gh}" | xxd -r -p)"
      say "      RAG: $(printf '%s' "${rh}" | xxd -r -p)"
    }
    [ "${g}" != "${eh}" ] && {
      say "      Go != expected.txt"
      say "      Go  : $(printf '%s' "${gh}" | xxd -r -p)"
      say "      期望: $(printf '%s' "${eh##*|}" | xxd -r -p)"
    }
  fi
}

if [ "${RECORD:-0}" = "1" ]; then
  RECORD_FILE="${WORK}/expected.record"
  : > "${RECORD_FILE}"
  say "   ⚠️ RECORD=1 ⇒ 期望值取自 **Go 真源**（跑完会打印待固化内容）"
fi

# ── 读路径（不改状态）────────────────────────────────────────────────────────
run_case "follow_list_seeded"      POST "/follow/v1/follow/list"    '{"user_id":"1001"}' valid
run_case "follower_list_seeded"    POST "/follow/v1/follower/list"  '{"user_id":"1001"}' valid
run_case "block_list_seeded"       POST "/follow/v1/block/list"     '{"user_id":"1001"}' valid
run_case "follow_list_empty"       POST "/follow/v1/follow/list"    '{"user_id":"1005"}' valid
run_case "recommendations_seeded"  POST "/follow/v1/recommendations" '{"user_id":"1001"}' valid
run_case "follow_list_limit_zero"  POST "/follow/v1/follow/list"    '{"user_id":"1001","limit":0}' valid
run_case "follow_list_limit_max"   POST "/follow/v1/follow/list"    '{"user_id":"1001","limit":1000}' valid
run_case "follow_list_offset_neg"  POST "/follow/v1/follow/list"    '{"user_id":"1001","offset":-1}' valid
run_case "block_list_wrong_owner"  POST "/follow/v1/block/list"     '{"user_id":"1002"}' valid
# ── 协议层（不改状态）────────────────────────────────────────────────────────
run_case "no_auth_header"          POST "/follow/v1/follow/list"    '{"user_id":"1001"}' none
run_case "bad_jwt"                 POST "/follow/v1/follow/list"    '{"user_id":"1001"}' bad
run_case "follow_missing_target"   POST "/follow/v1/follow"         '{}' valid
run_case "follow_bad_target_type"  POST "/follow/v1/follow"         '{"target_id":"1.5"}' valid
run_case "follow_unknown_path"     POST "/follow/v1/nope"           '{}' valid
run_case "follow_wrong_method"     GET  "/follow/v1/follow"         '' none
# ── 写路径（**顺序敏感**：放在最后，且各用不同的 target）───────────────────────
run_case "follow_self_relation"    POST "/follow/v1/follow"         '{"target_id":"1001"}' valid
run_case "follow_target_not_found" POST "/follow/v1/follow"         '{"target_id":"999999"}' valid
run_case "follow_new_relation"     POST "/follow/v1/follow"         '{"target_id":"1004"}' valid
run_case "unfollow_seeded"         POST "/follow/v1/unfollow"       '{"target_id":"1003"}' valid
run_case "unblock_seeded"          POST "/follow/v1/unblock"        '{"target_id":"1005"}' valid

if [ "${RECORD_FILE}" != "" ]; then
  say ""
  say "---- RECORD 产物（请人工核对后固化到 expected.txt）----"
  cat "${RECORD_FILE}"
  say "---- 固化命令：cp ${RECORD_FILE} ${EXPECTED} ----"
fi

echo
if [ "${FAILED}" = "0" ]; then
  say "✅ 双侧逐字节一致（Go 真源 == RAG == 冻结期望值）"
  exit 0
fi
say "❌ 双侧存在字节差异"
dump_logs
exit 1
