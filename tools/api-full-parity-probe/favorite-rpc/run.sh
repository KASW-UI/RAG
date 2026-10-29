#!/usr/bin/env bash
#
# favorite 域 **gRPC 层（rpc 层）**双侧活体对拍探针。
#
# 真源：Sea-RideTheWind_golang/service/favorite/rpc （**只读**，全程 git status 断言干净）
# 被测：RAG/service/favorite/favorite-rpc （真 fat jar）
#
# ⚠️ **与 tools/api-full-parity-probe/favorite/（HTTP 层）完全分开**：
#   那条链验的是 **api 面**（路由 / JWT / 响应头 / JSON 字节）；
#   本链验的是 **rpc 面**（gRPC status code + description + **响应消息在 wire 上的原始字节**）。
#   两条链的证据**不混记**。
#
# ⚠️ **比的是字节，不是「字段看起来一样」**：
#   成功路径取的是 **gRPC 响应消息的原始字节**（在 grpc-java 把消息交给上层之前截获），
#   不是「解析后重新序列化」的字节 ⇒ 字段序漂移不会被客户端库抹平。
#   失败路径取 **status code + description**（本域把业务码藏在 description 文案里 ⇒ 必须逐字节相同）。
#
# 用法：
#   bash tools/api-full-parity-probe/favorite-rpc/run.sh            # EXIT=0 = 两侧逐字节一致
#   FREEZE=1 bash tools/api-full-parity-probe/favorite-rpc/run.sh     # 用 Go 侧观测值刷新 expected.txt
#
# 退出码：0 = 两侧逐字节一致；1 = 有差异；2 = 环境问题 / 真源漂移 / 依赖缺失。
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAG_ROOT="$(cd "${HERE}/../../.." && pwd)"
GO_REPO="${GO_REPO:-/Users/pr/Documents/java&golang/Sea-RideTheWind_golang}"

# ── 宿主中间件（env 可覆盖；默认值 = 本机实测 2026-10-29）────────────────────
ETCD_PORT="${ETCD_PORT:-49319}"        # Sea-etcd  49319→2379（宿主映射口 ⇒ 给 Go 侧配置副本用）
# ⚠️ etcdctl 是**在容器里跑**（`docker exec Sea-etcd etcdctl`）⇒ 端点必须是**容器内端口** 2379，
#    用宿主映射口 49319 会 connection refused（article 探针 2026-10-29 实测踩过 ⇒ 静默等到超时才发现）。
ETCD_CTL_ENDPOINT="${ETCD_CTL_ENDPOINT:-http://127.0.0.1:2379}"
JAEGER_PORT="${JAEGER_PORT:-49317}"    # Sea-jaeger 49317→4317（OTLP gRPC）
NACOS_PORT="${NACOS_PORT:-49848}"      # Sea-nacos  49848→8848

# ── 本 lane 的端口 ────────────────────────────────────────────────────────
# 🔴 2026-10-29 实测两件事：
#    ① 任务书给的 18131-18135 **已被别的 lane 占用**（容器 `r2like-go-pg` / `r2like-go-redis` /
#       `r2like-rag-pg` / `r2like-rag-redis` 正好发布 18131/18132/18133/18134）⇒ **别人的容器不能删**；
#    ② 宿主机上**多个 lane 的探针同时跑** ⇒ 端口是**抢占式**的 ⇒ 选完端口到真正 bind 之间
#       仍可能被抢走（2026-10-29 实测：RAG 侧 bind 18142 时报 `BindException: Address already in use`）。
#    ⇒ 处置：**自动挑一段全空闲的 5 连号端口**（起点按本进程 PID 打散，避免与别的 lane 撞同一段），
#      **并且**每个服务 bind 失败时**换端口重试**（3 次）。
#    ⇒ 显式设了 GO_RPC_PORT / RAG_RPC_PORT / GO_PG_PORT / RAG_PG_PORT / GO_DEV_PORT
#      任意一个 ⇒ 原样使用（占用时直接报错，不静默改端口）。
PORT_BLOCK_START="${PORT_BLOCK_START:-18141}"
GO_RPC_PORT="${GO_RPC_PORT:-}"
RAG_RPC_PORT="${RAG_RPC_PORT:-}"
GO_DEV_PORT="${GO_DEV_PORT:-}"
GO_PG_PORT="${GO_PG_PORT:-}"
RAG_PG_PORT="${RAG_PG_PORT:-}"
PORT_AUTO=0
if [ -z "${GO_RPC_PORT}" ] && [ -z "${RAG_RPC_PORT}" ] && [ -z "${GO_DEV_PORT}" ] \
   && [ -z "${GO_PG_PORT}" ] && [ -z "${RAG_PG_PORT}" ]; then
  PORT_AUTO=1
fi

# ⚠️ **容器名带 $$**（每轮唯一）⇒ 别的轮/别的 lane 的 cleanup 绝不可能把本轮的库删掉。
GO_PG_CONTAINER="${GO_PG_CONTAINER:-r2fav-go-pg-$$}"
RAG_PG_CONTAINER="${RAG_PG_CONTAINER:-r2fav-rag-pg-$$}"

EXPECTED="${HERE}/expected.txt"
SEED="${HERE}/seed.sql"
CLIENT_SRC="${HERE}/java/FavoriteRpcParityClient.java"

WORK="$(mktemp -d)"
GO_PIDS=(); RAG_PIDS=()
say()  { printf '%s\n' "$*"; }
step() { printf '\n== %s ==\n' "$*"; }

# ── 失败时**自动打印两侧日志**（否则 exit 2 之后什么都查不到）────────────────
dump_logs() {
  local f
  for f in go-rpc.log rag-rpc.log; do
    [ -f "${WORK:-/nonexistent}/${f}" ] || continue
    printf '\n---- %s（末 40 行）----\n' "${f}" >&2
    tail -40 "${WORK}/${f}" | sed 's/^/   | /' >&2
  done
  # ⚠️ **go-zero 的 logx 日志不在 stdout**（真源 yaml `Log.Mode: file`）⇒ 落在
  #    ${WORK}/log/favorite-rpc/ 下 ⇒ 只 dump stdout 会漏掉真正的报错现场。
  if [ -d "${WORK:-/nonexistent}/log" ]; then
    for f in "${WORK}"/log/favorite-rpc/*.log; do
      [ -f "${f}" ] || continue
      printf '\n---- %s（末 40 行 · go-zero logx）----\n' "${f}" >&2
      tail -40 "${f}" | sed 's/^/   | /' >&2
    done
  fi
}
die() { printf '!! %s\n' "$*" >&2; dump_logs; exit 2; }

host_port_open() { [ -n "${1:-}" ] || return 1; nc -z -G 2 127.0.0.1 "$1" >/dev/null 2>&1; }

# ── 找空闲端口（从 $1 起向上找）──────────────────────────────────────────
pick_free_port() {
  local p="$1" i=0
  while [ "${i}" -lt 300 ]; do
    host_port_open "${p}" || { printf '%s' "${p}"; return 0; }
    p=$((p + 1))
    i=$((i + 1))
  done
  return 1
}

# ── 自动挑一段**全空闲**的 5 连号端口 ─────────────────────────────────────
#   段内分配：+0 Go rpc · +1 RAG rpc · +2 Go DevServer · +3 Go PG · +4 RAG PG
# ⚠️ **不用 `seq first step last`**：macOS 的 seq 是 BSD 版，参数序是 `seq [first [incr]] last`
#    （GNU 是 `first last step`）⇒ 写成 GNU 序 ⇒ BSD seq 报 "needs negative decrement"。
pick_port_block() {
  local base p ok
  # ⚠️ 起点按本进程 PID 打散（10 的倍数）⇒ 与**同时在跑**的别的 lane 不会挑到同一段
  base=$((PORT_BLOCK_START + ($$ % 37) * 10))
  while [ "${base}" -lt $((PORT_BLOCK_START + 800)) ]; do
    ok=1
    p="${base}"
    while [ "${p}" -le $((base + 4)) ]; do
      if host_port_open "${p}"; then ok=0; break; fi
      p=$((p + 1))
    done
    [ "${ok}" = "1" ] && { printf '%s' "${base}"; return 0; }
    base=$((base + 10))
  done
  return 1
}

# ── 回收**本 lane 自己的**残留容器（名字前缀 r2fav-）────────────────────────
# 🔴 为什么必须先回收容器、再查端口：宿主上**发布端口**的容器会让
#    `com.docker.backend services` 这个进程**持有该端口** ⇒ `lsof -ti tcp:<port>`
#    看到的是 docker 后端，不是探针的进程 ⇒ 「按端口无差别 kill」会误杀 docker 后端
#    ⇒ **Docker daemon 当场挂掉**（2026-10-29 实测：第一次跑就是这么报废的）。
#    ⇒ 正确顺序：先删掉**自己前缀**的容器（端口随之释放），再查端口。
reclaim_own_containers() {
  local c
  for c in $(docker ps -a --format '{{.Names}}' 2>/dev/null | grep '^r2fav-' || true); do
    docker rm -f "${c}" >/dev/null 2>&1 && say "   · 回收本 lane 残留容器 ${c}"
  done
  sleep 1
}

# ── 端口预检：只踢掉「本探针自己的」残留进程 ──────────────────────────────
# 🔴 **绝不按端口无差别 kill**：见上面的注释 —— 会误杀 docker 后端。
#    别人的进程占端口 ⇒ **报错退出**，不越界动手。
free_probe_ports() {
  local p pid cmd
  for p in "${GO_RPC_PORT}" "${RAG_RPC_PORT}" "${GO_DEV_PORT}" "${GO_PG_PORT}" "${RAG_PG_PORT}"; do
    [ -n "${p}" ] || continue
    for pid in $(lsof -ti tcp:"${p}" 2>/dev/null); do
      cmd="$(ps -p "${pid}" -o command= 2>/dev/null)"
      case "${cmd}" in
        *favorite-rpc*|*api-full-parity-probe*|*"${WORK}"*)
          kill -9 "${pid}" 2>/dev/null
          say "   · 踢掉占用 :${p} 的**本探针**残留进程 ${pid}"
          ;;
        *)
          die "端口 :${p} 被**无关**进程占用（pid ${pid}：${cmd}）⇒ 本探针不越界杀进程；请改端口后重跑"
          ;;
      esac
    done
  done
  sleep 1
}

# ── 停掉一个后台服务，并**确认端口真的释放了** ────────────────────────────
# 🔴 **为什么必须用 `exec` 起服务**（2026-10-29 实测踩过）：
#    `( cd X && cmd ) &` ⇒ `$!` 是**子 shell** 的 PID，不是 `cmd` 的 PID
#    ⇒ `kill $!` 杀的是子 shell，**真正的服务进程还活着** ⇒ 端口不释放
#    ⇒ 第二次启动同一个端口 ⇒ `panic: listen tcp 0.0.0.0:18141: bind: address already in use`
#    ⇒ 而 etcd 键与端口检查**都通过**（因为旧进程还在服务）⇒ 探针以为起来了，其实打的是旧进程。
#    ⇒ 修法：子 shell 里用 `exec`（把子 shell **替换**成服务进程 ⇒ `$!` 就是服务 PID），
#      并且杀完之后**轮询等端口关闭**。
stop_pid_and_wait_port() {   # $1 = pid  $2 = 端口
  local pid="$1" port="$2"
  kill "$pid" 2>/dev/null
  sleep 1
  kill -9 "$pid" 2>/dev/null
  for _ in $(seq 1 30); do
    host_port_open "${port}" || return 0
    sleep 1
  done
  return 1
}

# ── etcd 访问（**容器内**端点；键 = `<key>/<leaseID>` 前缀族）────────────────
# ⚠️ go-zero v1.10.2 `zrpc/internal/rpcpubserver.go:32`：`discov.NewPublisher(etcd.Hosts, etcd.Key, ...)`
#    ⇒ 注册键 = `Etcd.Key`（= `favorite.rpc`）；`core/discov/publisher.go:191-196`
#    ⇒ 未显式配 `etcd.ID` 时键 = `<key>/<leaseID>` ⇒ **必须按前缀查**（精确键查永远查不到）。
etcd_get_value() {
  docker exec Sea-etcd etcdctl --endpoints="${ETCD_CTL_ENDPOINT}" get "$1" --prefix --print-value-only 2>/dev/null | head -1
}
etcd_has_key() { [ -n "$(etcd_get_value "$1")" ]; }

# ── 注销 Nacos 里的**残留实例**（上一轮被 kill 时旧实例在心跳超时前仍「健康」）──
nacos_deregister_all() {
  local svc="$1" ip port
  curl -s -m 10 "http://127.0.0.1:${NACOS_PORT}/nacos/v1/ns/instance/list?serviceName=${svc}&groupName=DEFAULT_GROUP&namespaceId=public" 2>/dev/null \
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
' | while read -r ip port; do
      [ -n "${ip}" ] || continue
      curl -s -m 5 -X DELETE "http://127.0.0.1:${NACOS_PORT}/nacos/v1/ns/instance?serviceName=${svc}&groupName=DEFAULT_GROUP&namespaceId=public&ip=${ip}&port=${port}" >/dev/null 2>&1
      say "   · 已注销 Nacos 残留实例 ${svc} ${ip}:${port}"
    done
}

cleanup() {
  local p
  # ⚠️ macOS 自带 bash 3.2：`set -u` 下展开**空数组**会报 `unbound variable`
  #    ⇒ 用 `${A[@]+"${A[@]}"}` 形式（article 探针 2026-10-29 实测踩过）。
  for p in ${GO_PIDS[@]+"${GO_PIDS[@]}"} ${RAG_PIDS[@]+"${RAG_PIDS[@]}"}; do kill "$p" 2>/dev/null; done
  sleep 1
  for p in ${GO_PIDS[@]+"${GO_PIDS[@]}"} ${RAG_PIDS[@]+"${RAG_PIDS[@]}"}; do kill -9 "$p" 2>/dev/null; done
  docker rm -f "${GO_PG_CONTAINER}" >/dev/null 2>&1
  docker rm -f "${RAG_PG_CONTAINER}" >/dev/null 2>&1
  # ⚠️ **不删临时目录**：`rm -rf` 在本机会被回收站守卫拦下（mavis-trash 失效 ⇒ 命令被整体拒绝）
  #    ⇒ 留着 `/var/folders/.../tmp.*` 由系统清理（比让脚本自杀强）。
  if [ "${KEEP_WORK:-0}" = "1" ]; then
    say "   · KEEP_WORK=1 ⇒ 保留 ${WORK}"
  fi
}
trap cleanup EXIT

# ══════════════════════════ 0. 前置检查（缺 ⇒ exit 2，绝不静默降级）══════════════════════════
step "0/9 前置检查"
for cmd in docker go mvn java javac curl python3 xxd shasum lsof nc; do
  command -v "${cmd}" >/dev/null 2>&1 || die "缺命令：${cmd}"
done
[ -d "${GO_REPO}/service/favorite/rpc" ] || die "找不到 Go 真源：${GO_REPO}/service/favorite/rpc（用 GO_REPO=... 指定）"
for pair in "Sea-etcd:${ETCD_PORT}" "Sea-nacos:${NACOS_PORT}"; do
  c="${pair%%:*}"; p="${pair##*:}"
  docker ps --format '{{.Names}}' | grep -qx "${c}" || die "容器 ${c} 没在跑（宿主中间件缺失）"
  nc -z -G 2 127.0.0.1 "${p}" || die "宿主端口 ${p} 不通（${c} 的映射口）"
done
docker exec Sea-etcd etcdctl --endpoints="${ETCD_CTL_ENDPOINT}" endpoint health >/dev/null 2>&1 \
  || die "容器内 etcd 端点 ${ETCD_CTL_ENDPOINT} 不可用（ETCD_CTL_ENDPOINT 可覆盖）"
reclaim_own_containers
if [ "${PORT_AUTO}" = "1" ]; then
  BASE="$(pick_port_block)" || die "从 ${PORT_BLOCK_START} 起找不到一段全空闲的 5 连号端口"
  GO_RPC_PORT="${BASE}"
  RAG_RPC_PORT="$((BASE + 1))"
  GO_DEV_PORT="$((BASE + 2))"
  GO_PG_PORT="$((BASE + 3))"
  RAG_PG_PORT="$((BASE + 4))"
  say "   · 端口段自动选择：${BASE}-$((BASE + 4))（Go rpc ${GO_RPC_PORT} · RAG rpc ${RAG_RPC_PORT} · Go devserver ${GO_DEV_PORT} · Go PG ${GO_PG_PORT} · RAG PG ${RAG_PG_PORT}）"
fi
free_probe_ports
say "   ✓ 命令齐 / Sea-etcd(${ETCD_PORT}) · Sea-nacos(${NACOS_PORT}) 在跑"

# ══════════════════════════ 1. 两侧各自的 Postgres ══════════════════════════
step "1/9 两侧 Postgres（Go 侧要真源的 admin/first_db；RAG 侧让它自己的 Flyway 建表）"
# ⚠️ **就绪判据 = 容器内 TCP + 宿主映射口，两道都要**（2026-10-29 实测踩了三种假信号）：
#    ① `docker exec pg_isready`（走 unix socket）⇒ PG 初始化阶段的**临时 server**
#       只监听 socket ⇒ **过早**报 ready；
#    ② 只探宿主映射口 ⇒ docker-proxy 起来那一刻就能连（实测 **0s 就 OPEN**）⇒ 同样**过早**；
#    ③ 宿主负载实测 load average **103** ⇒ initdb 本身要几十秒 ⇒ 上限不能是 60s。
#    ⇒ 唯一可靠判据 = `docker exec pg_isready -h 127.0.0.1 -p 5432`（容器内 **TCP**）
#      （tools/ddl-parity-probe/favorite/run.sh 的同款判据），再叠加宿主口。
start_pg() {   # $1 = 容器名  $2 = 宿主端口
  local c="$1" p="$2" attempt ready
  for attempt in 1 2 3; do
    docker rm -f "${c}" >/dev/null 2>&1
    if ! docker run -d --name "${c}" -e POSTGRES_USER=admin -e POSTGRES_PASSWORD=sea \
      -e POSTGRES_DB=first_db -p "${p}:5432" postgres:16-alpine >"${WORK}/pg-run.log" 2>&1; then
      say "   · 第 ${attempt} 次 docker run 失败：$(tr '\n' ' ' < "${WORK}/pg-run.log")"
      continue
    fi
    ready=0
    for _ in $(seq 1 120); do
      if docker exec "${c}" pg_isready -U admin -h 127.0.0.1 -p 5432 >/dev/null 2>&1; then
        ready=1
        break
      fi
      sleep 1.5
    done
    if [ "${ready}" = "1" ]; then
      for _ in $(seq 1 30); do
        host_port_open "${p}" && return 0
        sleep 1
      done
      say "   · 容器内已就绪，但宿主端口 :${p} 没通"
    else
      say "   · 第 ${attempt} 次就绪超时（容器内 TCP 探活 180s）⇒ 容器日志末 5 行："
    fi
    docker logs "${c}" 2>&1 | tail -5 | sed 's/^/       | /'
    docker rm -f "${c}" >/dev/null 2>&1
  done
  return 1
}
start_pg "${GO_PG_CONTAINER}" "${GO_PG_PORT}" \
  || die "Go 侧 PG 起不来（容器 ${GO_PG_CONTAINER} / 宿主端口 ${GO_PG_PORT}）"
start_pg "${RAG_PG_CONTAINER}" "${RAG_PG_PORT}" \
  || die "RAG 侧 PG 起不来（容器 ${RAG_PG_CONTAINER} / 宿主端口 ${RAG_PG_PORT}）"
say "   ✓ 两侧 Postgres 就绪（Go :${GO_PG_PORT} · RAG :${RAG_PG_PORT}）"

# ══════════════════════════ 2. 构建 Go 侧二进制（真源只读）═════════════════════════
step "2/9 构建 Go 侧 favorite-rpc（真源**只读** ⇒ 产物落临时目录）"
( cd "${GO_REPO}" && CGO_ENABLED=0 GOPROXY=off GOTOOLCHAIN=local \
    go build -o "${WORK}/" ./service/favorite/rpc ) || die "Go 侧构建失败（见 ${WORK}/）"
[ -x "${WORK}/rpc" ] || die "Go 侧二进制没产出"
( cd "${GO_REPO}" && git status --porcelain -- service/favorite | grep -q . ) \
  && die "真源仓 service/favorite 有改动 ⇒ 本探针**不得**改真源"
say "   ✓ Go 侧二进制构建完成（真源 service/favorite 仍然干净）"

# ══════════════════════════ 3. Go 侧配置副本 ══════════════════════════
step "3/9 生成 Go 侧配置副本（真源 yaml 写死主机名 ⇒ 只改副本）"
mkdir -p "${WORK}/etc" "${WORK}/log"
# ⚠️ **逐条断言「改了哪项」**（article 探针 2026-10-29 实测：`^  Port: 8889$` 这种**缩进写错**的
#    sed 会静默不命中 ⇒ 副本里仍是旧值 ⇒ 端口一被占就 panic ⇒ 探针假挂）。
write_go_config() {
  sed -e "s|^ListenOn: 0.0.0.0:8082$|ListenOn: 0.0.0.0:${GO_RPC_PORT}|" \
      -e "s|^  Path: ../../../../log/favorite-rpc$|  Path: ${WORK}/log/favorite-rpc|" \
      -e "s|^  Host: \"postgres\"$|  Host: \"127.0.0.1\"|" \
      -e "s|^  Port: \"5432\"$|  Port: \"${GO_PG_PORT}\"|" \
      -e "s|^  Password: \"\${POSTGRES_PASSWORD}\"$|  Password: \"sea\"|" \
      -e "s|jaeger:4317|127.0.0.1:${JAEGER_PORT}|g" \
      -e "s|^  Port: 16062$|  Port: ${GO_DEV_PORT}|" \
      -e "s|etcd:2379|127.0.0.1:${ETCD_PORT}|g" \
      "${GO_REPO}/service/favorite/rpc/etc/favorite.yaml" > "${WORK}/etc/favorite.yaml"
  grep -q "^ListenOn: 0.0.0.0:${GO_RPC_PORT}$"        "${WORK}/etc/favorite.yaml" || die "副本里 ListenOn 没改成 ${GO_RPC_PORT}"
  grep -q "^  Port: \"${GO_PG_PORT}\"$"                "${WORK}/etc/favorite.yaml" || die "副本里 Postgres Port 没改成 ${GO_PG_PORT}"
  grep -q "^  Host: \"127.0.0.1\"$"                   "${WORK}/etc/favorite.yaml" || die "副本里 Postgres Host 没改成 127.0.0.1"
  grep -q "^  Password: \"sea\"$"                     "${WORK}/etc/favorite.yaml" || die "副本里 Postgres Password 没改成 sea"
  grep -q "^  Port: ${GO_DEV_PORT}$"                  "${WORK}/etc/favorite.yaml" || die "副本里 DevServer Port 没改成 ${GO_DEV_PORT}"
  grep -q "127.0.0.1:${ETCD_PORT}"                    "${WORK}/etc/favorite.yaml" || die "副本里 etcd 没改成 127.0.0.1:${ETCD_PORT}"
  grep -q "etcd:2379"                                 "${WORK}/etc/favorite.yaml" && die "副本里还残留 etcd:2379（sed 没命中？）"
  grep -q "jaeger:4317"                               "${WORK}/etc/favorite.yaml" && die "副本里还残留 jaeger:4317（sed 没命中？）"
  # ⚠️ DevServer 与 gRPC 是**两个独立监听者** ⇒ 端口必须不同（article 探针坑 #1：改成同一个 ⇒ panic）
  [ "${GO_DEV_PORT}" != "${GO_RPC_PORT}" ] || die "DevServer 端口与 gRPC ListenOn 撞了 ⇒ 必 panic: address already in use"
}
write_go_config
say "   ✓ 配置副本已生成（逐项断言全过）"

# ══════════════════════════ 4. Go 侧 schema（sql-first）+ 起 Go 侧 favorite-rpc ══════════════════════════
step "4/9 Go 侧 schema（sql-first 基准）+ 起 favorite-rpc（注册 etcd ${ETCD_PORT}）"
# ⚠️ **sql-first 是本仓已钉死的判定基准**（tools/ddl-parity-probe/favorite/README.md:36）：
#    「存量老表(favorite_folder, favorite_item) → 运维跑 001..004 → 应用后起(InitDB)」。
#    真源 `InitDB`（internal/model/db.go:33）只做 AutoMigrate ⇒ **三张表一起建** ⇒
#    001 的 `CREATE TABLE IF NOT EXISTS favorite_fact_outbox` 会变成 no-op
#    ⇒ CHECK / UNIQUE / 部分索引**建不出来** ⇒ **不是** sql-first 形态。
#    ⇒ 修法：先让应用起一次（AutoMigrate 建表）⇒ 停 ⇒ **把 outbox 删掉** ⇒ 跑 001..004 ⇒ 再起。
#    ⇒ 于是 001 执行时，表集合 = {favorite_folder, favorite_item}（与基准完全一致）。
docker exec Sea-etcd etcdctl --endpoints="${ETCD_CTL_ENDPOINT}" del "favorite.rpc" --prefix >/dev/null 2>&1
WARM_PID=""
WARM_OK=0
for attempt in 1 2 3; do
  write_go_config
  ( cd "${WORK}" && exec "${WORK}/rpc" -f "${WORK}/etc/favorite.yaml" >"${WORK}/go-warmup.log" 2>&1 ) &
  WARM_PID=$!
  for _ in $(seq 1 60); do nc -z -G 1 127.0.0.1 "${GO_RPC_PORT}" && break; sleep 1; done
  if nc -z -G 2 127.0.0.1 "${GO_RPC_PORT}" && kill -0 "${WARM_PID}" 2>/dev/null; then
    WARM_OK=1
    break
  fi
  say "   · 预热第 ${attempt} 次失败（端口 :${GO_RPC_PORT} 可能被别的 lane 抢走）⇒ 换端口重试"
  kill -9 "${WARM_PID}" 2>/dev/null
  GO_RPC_PORT="$(pick_free_port $((GO_RPC_PORT + 1)))" || die "找不到空闲端口给 Go favorite-rpc"
  GO_DEV_PORT="$(pick_free_port $((GO_DEV_PORT + 1)))" || die "找不到空闲端口给 Go DevServer"
  write_go_config
done
[ "${WARM_OK}" = "1" ] || die "Go favorite-rpc（建表预热）三次都没起来（见 ${WORK}/go-warmup.log）"
stop_pid_and_wait_port "${WARM_PID}" "${GO_RPC_PORT}" || die "预热进程的端口 :${GO_RPC_PORT} 没释放"
say "   ✓ 预热启动完成（InitDB 的 AutoMigrate 已建出三张表；**端口已确认释放**）"
docker exec "${GO_PG_CONTAINER}" psql -U admin -d first_db -v ON_ERROR_STOP=1 \
  -c "DROP TABLE IF EXISTS favorite_fact_outbox" >/dev/null || die "删掉预热建出的 favorite_fact_outbox 失败"
for f in 001_favorite_fact_outbox.sql 002_favorite_fact_delivery.sql \
         003_favorite_target_revision.sql 004_favorite_legacy_fact_marker.sql; do
  docker exec -i "${GO_PG_CONTAINER}" psql -U admin -d first_db -v ON_ERROR_STOP=1 \
    < "${GO_REPO}/service/favorite/rpc/internal/model/${f}" >/dev/null || die "真源 ${f} 执行失败"
  say "   ✔ 真源 ${f}"
done
say "   ✓ 真源 DDL 已灌（001..004 ⇒ sql-first 基准）"

# ⚠️ **Go 侧的真源进程在「矩阵前」才启动**（2026-10-29 实测踩过）：
#    早先把它放在这一步（早于 RAG 侧构建/启动/灌种子/编译客户端 ⇒ **空闲约 95s**），
#    偶发出现「进程活着 + 端口在监听，但**所有** RPC 返回 `UNAVAILABLE / io exception`」
#    （该文案来自 **grpc-java 客户端**的 netty 传输层 ⇒ 客户端连不上服务端 ⇒ 不是服务端返回的）。
#    ⇒ 处置：把启动挪到矩阵前（空闲窗口压到**秒级**）+ 矩阵前做**存活自检**（失败即重启一次）。
#    ⇒ 本函数因此被拆出来，供 step 8 调用。
start_go_rpc() {
  local attempt
  for attempt in 1 2 3; do
    docker exec Sea-etcd etcdctl --endpoints="${ETCD_CTL_ENDPOINT}" del "favorite.rpc" --prefix >/dev/null 2>&1
    ( cd "${WORK}" && exec "${WORK}/rpc" -f "${WORK}/etc/favorite.yaml" >"${WORK}/go-rpc.log" 2>&1 ) &
    GO_PIDS+=($!)
    # ⚠️ go-zero 的 etcd 键是**前缀族** `<key>/<leaseID>` ⇒ 必须按前缀查
    for _ in $(seq 1 60); do etcd_has_key favorite.rpc && break; sleep 1; done
    # ⚠️ go-zero **先注册 etcd 再 bind 端口** ⇒ 「etcd 有键」≠「端口已监听」⇒ 必须再等一次端口。
    for _ in $(seq 1 30); do nc -z -G 1 127.0.0.1 "${GO_RPC_PORT}" && break; sleep 1; done
    if nc -z -G 2 127.0.0.1 "${GO_RPC_PORT}" && kill -0 "${GO_PIDS[0]}" 2>/dev/null; then
      # 🔴 **必须确认「注册进 etcd 的那个实例」就是本轮起的这个进程**：
      #    残留的旧实例（预热那次）也会注册同一个键前缀 ⇒ 只看「有没有键」会打到旧进程上。
      local v
      v="$(etcd_get_value favorite.rpc)"
      case "${v}" in
        *":${GO_RPC_PORT}"*) : ;;
        *) say "   · etcd 里 favorite.rpc 指向 ${v}（不是本轮 :${GO_RPC_PORT}）⇒ 有残留实例";;
      esac
      say "   ✓ Go favorite-rpc 已注册 favorite.rpc → ${v}（:${GO_RPC_PORT} 已监听 · 本轮进程存活）"
      return 0
    fi
    say "   · Go 启动第 ${attempt} 次失败（端口 :${GO_RPC_PORT}）⇒ 换端口重试"
    kill -9 "${GO_PIDS[0]}" 2>/dev/null
    GO_PIDS=()
    GO_RPC_PORT="$(pick_free_port $((GO_RPC_PORT + 1)))" || die "找不到空闲端口给 Go favorite-rpc"
    GO_DEV_PORT="$(pick_free_port $((GO_DEV_PORT + 1)))" || die "找不到空闲端口给 Go DevServer"
    write_go_config
  done
  return 1
}

# ══════════════════════════ 5. 构建并起 RAG 侧 favorite-rpc ══════════════════════════
step "5/9 构建并起 RAG 侧 favorite-rpc（注册 Nacos ${NACOS_PORT}）"
# ⚠️ `mvn` **必须带 `-am`**（否则用过期上游产物 ⇒ 假红，PIT-FAV-RPC-013）
( cd "${RAG_ROOT}" && mvn -o -q -pl service/favorite/favorite-rpc -am package -DskipTests ) \
  || die "RAG 侧构建失败"
RAG_RPC_JAR="${RAG_ROOT}/service/favorite/favorite-rpc/target/favorite-rpc-0.0.1-SNAPSHOT.jar"
[ -f "${RAG_RPC_JAR}" ] || die "RAG 侧 jar 没产出：${RAG_RPC_JAR}"
say "   ✓ favorite-rpc jar 构建完成"
nacos_deregister_all favorite-rpc
RAG_OK=0
for attempt in 1 2 3; do
  ( cd "${RAG_ROOT}" && exec env \
      POSTGRES_HOST=127.0.0.1 POSTGRES_PORT="${RAG_PG_PORT}" POSTGRES_DB=first_db \
      POSTGRES_USER=admin POSTGRES_PASSWORD=sea \
      NACOS_ADDR="127.0.0.1:${NACOS_PORT}" GRPC_SERVER_PORT="${RAG_RPC_PORT}" \
      java -jar "${RAG_RPC_JAR}" ) >"${WORK}/rag-rpc.log" 2>&1 &
  RAG_PIDS+=($!)
  for _ in $(seq 1 90); do nc -z -G 1 127.0.0.1 "${RAG_RPC_PORT}" && break; sleep 1; done
  if nc -z -G 2 127.0.0.1 "${RAG_RPC_PORT}" && kill -0 "${RAG_PIDS[0]}" 2>/dev/null; then
    RAG_OK=1
    break
  fi
  say "   · RAG 启动第 ${attempt} 次失败（端口 :${RAG_RPC_PORT} 可能被别的 lane 抢走 ⇒ BindException）⇒ 换端口重试"
  kill -9 "${RAG_PIDS[0]}" 2>/dev/null
  RAG_PIDS=()
  RAG_RPC_PORT="$(pick_free_port $((RAG_RPC_PORT + 1)))" || die "找不到空闲端口给 RAG favorite-rpc"
done
[ "${RAG_OK}" = "1" ] || die "RAG favorite-rpc 三次都没起来（见 ${WORK}/rag-rpc.log）"
# Nacos 注册是 RAG 侧**真实部署形态**的一部分（Go 侧是 etcd 注册）⇒ 顺手断言它真的注册了
NACOS_HIT=0
for _ in $(seq 1 60); do
  curl -s "http://127.0.0.1:${NACOS_PORT}/nacos/v1/ns/service/list?pageNo=1&pageSize=50&namespaceId=public" 2>/dev/null \
    | grep -q 'favorite-rpc' && { NACOS_HIT=1; break; }
  sleep 1
done
[ "${NACOS_HIT}" = "1" ] || die "RAG favorite-rpc 60s 内没注册进 Nacos（见 ${WORK}/rag-rpc.log）"
say "   ✓ RAG favorite-rpc 已监听 :${RAG_RPC_PORT}，并已注册进 Nacos（服务名 favorite-rpc）"

# ══════════════════════════ 6. 两侧灌同一份种子 ══════════════════════════
step "6/9 两侧灌同一份种子（**绝不与空库比**：「两侧都空」分不出「一致」与「都缺」）"
for c in "${GO_PG_CONTAINER}" "${RAG_PG_CONTAINER}"; do
  docker exec -i "${c}" psql -U admin -d first_db -v ON_ERROR_STOP=1 < "${SEED}" >/dev/null \
    || die "灌种子失败：${c}"
  n=$(docker exec "${c}" psql -U admin -d first_db -t -A -c "SELECT count(*) FROM favorite_folder" | tr -d ' ')
  m=$(docker exec "${c}" psql -U admin -d first_db -t -A -c "SELECT count(*) FROM favorite_item" | tr -d ' ')
  say "   ✓ ${c}：favorite_folder ${n} 行 / favorite_item ${m} 行"
done

# ══════════════════════════ 7. 编译 gRPC 对拍客户端 ══════════════════════════
step "7/9 编译 gRPC 对拍客户端（同一个客户端打两侧 ⇒ 消掉客户端自身差异）"
CP_FILE="${WORK}/cp.txt"
( cd "${RAG_ROOT}" && mvn -o -q -pl service/favorite/favorite-rpc -am \
    dependency:build-classpath -Dmdep.outputFile="${CP_FILE}" -DincludeScope=runtime ) \
  || die "取 classpath 失败"
PROTO_JAR="${RAG_ROOT}/service/favorite/favorite-proto/target/favorite-proto-0.0.1-SNAPSHOT.jar"
[ -f "${PROTO_JAR}" ] || die "favorite-proto 产物不存在：${PROTO_JAR}（先跑 package）"
# ⚠️ dependency:build-classpath 把 favorite-proto 解析成 ~/.m2 里的**快照 jar** ⇒ 可能是旧的
#    ⇒ 换成本仓刚构建出来的产物（与被测 jar 同一批）。
COMMON_JAR="${RAG_ROOT}/service/favorite/favorite-common/target/favorite-common-0.0.1-SNAPSHOT.jar"
CP="$(PROTO_JAR="${PROTO_JAR}" COMMON_JAR="${COMMON_JAR}" python3 -c '
import os, sys
proto = os.environ["PROTO_JAR"]
common = os.environ["COMMON_JAR"]
cp = [p for p in sys.stdin.read().strip().split(":") if p]
def swap(p, marker, repl):
    return repl if marker in p else p
out = []
for p in cp:
    if "/com/rag/favorite-proto/" in p:
        p = proto
    elif "/com/rag/favorite-common/" in p and os.path.exists(common):
        p = common
    out.append(p)
print(":".join(out))
' < "${CP_FILE}")"
[ -n "${CP}" ] || die "classpath 为空"
javac -encoding UTF-8 -cp "${CP}" -d "${WORK}/classes" "${CLIENT_SRC}" || die "客户端编译失败"
[ -f "${WORK}/classes/probe/FavoriteRpcParityClient.class" ] || die "客户端 class 没产出"
say "   ✓ 客户端编译完成（classpath 含 favorite-proto 本仓产物）"

# ══════════════════════════ 8. 请求矩阵 ══════════════════════════
step "8/9 请求矩阵（同一个客户端 → Go 侧 / RAG 侧 → 逐字节比）"
# 🔴 **矩阵前先做存活自检**（2026-10-29 实测踩过）：
#    「进程还活着 + 端口还在监听」**不等于**「能应答 RPC」——
#    有一轮 Go 侧进程活着、端口也监听，但 26 条 RPC **全部**返回 `UNAVAILABLE / io exception`
#    ⇒ 若不先自检，探针会把**环境故障**报成「26 条漂移」⇒ **假红** ⇒ 比没有网还糟。
#    ⇒ 自检用最轻的一条（user_id=0 ⇒ InvalidArgument，不碰 DB），两侧各发一次；
#      Go 侧自检不过 ⇒ **重启一次**（换端口）再自检。
#
# ⚠️ **Go 侧此刻才启动**（见 start_go_rpc 的注释）：早先早启动 ⇒ 空闲 ~95s ⇒ 偶发全量 UNAVAILABLE。
start_go_rpc || die "Go favorite-rpc 三次都没起来（见 ${WORK}/go-rpc.log）"
precheck_rpc() {   # $1 = 端口  $2 = 标签
  local out code
  out="$(java -cp "${WORK}/classes:${CP}" probe.FavoriteRpcParityClient \
          --target="127.0.0.1:$1" --only=c01_get_folders_uid0 2>/dev/null | head -1)"
  code="$(printf '%s' "${out}" | awk -F'|' '{print $4}')"
  if [ "${code}" != "INVALID_ARGUMENT" ]; then
    say "   !! ${2} 侧在请求矩阵开始前**已不可用**（自检返回：${out:-<无响应>}）"
    return 1
  fi
  say "   ✓ ${2} 侧 gRPC 自检通过（INVALID_ARGUMENT）"
  return 0
}
if ! precheck_rpc "${GO_RPC_PORT}" "Go "; then
  say "   · 重启 Go 侧 favorite-rpc 后再自检一次"
  kill -9 "${GO_PIDS[0]}" 2>/dev/null
  GO_PIDS=()
  start_go_rpc || die "Go favorite-rpc 重启后仍起不来"
  precheck_rpc "${GO_RPC_PORT}" "Go " \
    || die "Go 侧 gRPC 自检两次都失败 ⇒ 本轮是**环境故障**，不是行为漂移（见 go-rpc.log 与 ${WORK}/log/）"
fi
precheck_rpc "${RAG_RPC_PORT}" "RAG" \
  || die "RAG 侧 gRPC 自检失败 ⇒ 本轮是**环境故障**，不是行为漂移（见 rag-rpc.log）"
java -cp "${WORK}/classes:${CP}" probe.FavoriteRpcParityClient --target="127.0.0.1:${GO_RPC_PORT}" \
  > "${WORK}/go.txt" 2>"${WORK}/go-client.err" || die "Go 侧客户端跑失败（见 ${WORK}/go-client.err）"
java -cp "${WORK}/classes:${CP}" probe.FavoriteRpcParityClient --target="127.0.0.1:${RAG_RPC_PORT}" \
  > "${WORK}/rag.txt" 2>"${WORK}/rag-client.err" || die "RAG 侧客户端跑失败（见 ${WORK}/rag-client.err）"
NCASES="$(grep -c '^CASE|' "${WORK}/go.txt")"
NAG="$(grep -c '^CASE|' "${WORK}/rag.txt")"
[ "${NCASES}" = "${NAG}" ] || die "两侧用例数不等（Go ${NCASES} / RAG ${NAG}）⇒ 有一侧漏了用例"
say "   ✓ 两侧各跑 ${NCASES} 条用例"

if [ "${FREEZE:-0}" = "1" ]; then
  # ⚠️ **只冻结 FROZEN 档**：AMBIENT 档的取值依赖「下游 user.rpc 在不在场」这个**环境态**
  #    （两侧注册中心还不同：Go=etcd · RAG=Nacos）⇒ 冻结它 = 冻结一个环境态 ⇒ 下一轮必假红。
  grep '|FROZEN|' "${WORK}/go.txt" > "${EXPECTED}"
  say "   ⚠️ FREEZE=1 ⇒ 已用 **Go 侧**观测值刷新 ${EXPECTED}（本次**未**做比对）"
  say "   · 冻结的 FROZEN 用例（AMBIENT 档不入表）："
  sed 's/^/     /' "${EXPECTED}"
  exit 0
fi

[ -f "${EXPECTED}" ] || die "缺少冻结的期望值：${EXPECTED}（先用 FREEZE=1 跑一次生成）"
FAILED=0
TOTAL=0
AMBIENT_WARN=0
while IFS= read -r gline; do
  [ -n "${gline}" ] || continue
  TOTAL=$((TOTAL + 1))
  name="$(printf '%s' "${gline}" | awk -F'|' '{print $2}')"
  tier="$(printf '%s' "${gline}" | awk -F'|' '{print $3}')"
  rline="$(grep -m1 "^CASE|${name}|" "${WORK}/rag.txt")"
  if [ "${tier}" = "AMBIENT" ]; then
    # ⚠️ AMBIENT 档：只判 Go == RAG；不一致**只告警不判红**（两侧注册中心不同 ⇒ 在场与否是环境态）
    if [ "${gline}" = "${rline}" ]; then
      code="$(printf '%s' "${gline}" | awk -F'|' '{print $4}')"
      say "   ⚠️  ${name}  Go == RAG（AMBIENT 档 · **不冻结**：取值取决于下游 user.rpc 在不在场 · 本轮 ${code}）"
    else
      AMBIENT_WARN=$((AMBIENT_WARN + 1))
      say "   ⚠️  ${name}  Go != RAG（AMBIENT 档 · **不判红**：两侧注册中心不同 ⇒ 可能只是下游在场状态不同）"
      say "      Go : ${gline}"
      say "      RAG: ${rline}"
    fi
    continue
  fi
  eline="$(grep -m1 "^CASE|${name}|" "${EXPECTED}")"
  if [ -z "${eline}" ]; then
    say "   ❌ ${name}：expected.txt 里没有这一条"
    FAILED=1
    continue
  fi
  if [ "${gline}" = "${rline}" ] && [ "${gline}" = "${eline}" ]; then
    body="$(printf '%s' "${gline}" | awk -F'|' '{print $6}')"
    code="$(printf '%s' "${gline}" | awk -F'|' '{print $4}')"
    say "   ✅ ${name}  Go == RAG == expected.txt（${code} · 响应字节 $(( ${#body} / 2 )) B）"
  else
    FAILED=1
    say "   ❌ ${name}"
    [ "${gline}" != "${eline}" ] && say "      Go != expected：${gline}"
    [ "${rline}" != "${eline}" ] && say "      RAG != expected：${rline}"
    say "      —— 人类可读 ——"
    python3 - "${gline}" "${rline}" <<'PY' 2>/dev/null || true
import sys
def show(tag, line):
    p = (line or "").split("|")
    if len(p) < 6:
        print("      %s -> (缺字段) %r" % (tag, line))
        return
    desc = bytes.fromhex(p[4]).decode("utf-8", "replace") if p[4] else ""
    print("      %s -> code=%s desc=%r body=%dB" % (tag, p[3], desc, len(p[5]) // 2))
show("Go ", sys.argv[1])
show("RAG", sys.argv[2])
PY
  fi
done < "${WORK}/go.txt"

# ══════════════════════════ 9. 结论 ══════════════════════════
step "9/9 结论"
if [ "${AMBIENT_WARN}" -gt 0 ]; then
  say "⚠️  ${AMBIENT_WARN} 条 AMBIENT 用例两侧不一致（**不判红**，见上）"
fi
if [ "${FAILED}" = "0" ]; then
  FROZEN_N="$(grep -c '|FROZEN|' "${WORK}/go.txt")"
  AMBIENT_N="$(grep -c '|AMBIENT|' "${WORK}/go.txt")"
  say "✅ 双侧逐字节一致：Go 真源 == RAG == 冻结期望值（FROZEN 档 **${FROZEN_N}** 条逐字节一致 ＋ AMBIENT 档 ${AMBIENT_N} 条只判 Go==RAG · gRPC wire 层）"
  exit 0
fi
say "❌ 双侧存在字节差异（详见上面逐条）"
dump_logs
exit 1
