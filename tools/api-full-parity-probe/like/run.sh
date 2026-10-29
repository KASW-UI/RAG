#!/usr/bin/env bash
#
# like 域 **双侧活体对拍探针**（Go 真源栈 + RAG 栈 ⇒ 同一请求 ⇒ 逐字节比对）。
#
# 真源：Sea-RideTheWind_golang/service/like/{api,rpc,cmd/fact-dispatch}
#       （**只读**：Go 侧用真源真正在用的生成码/二进制，真源仓一个字节都不改）
# 被测：RAG/service/like/{like-api,like-rpc}
#
# 本探针回答三个问题（每个都有独立的一段）：
#   ① HTTP 业务面（6 个端点 + 2 条失败路径）两侧**逐字节**是否一致？   → 阶段 A
#   ② 事实投递链路（DRIFT-LIKE-003-A）两侧投给 DC 的**字节**是否一致？ → 阶段 B
#   ③ 双失败错误传播（E60）两侧的**业务错误码**是否一致？             → 阶段 C
#
# ⚠️ **真源冻结 ⇒ 只能改「副本」**：Go 侧的配置副本把真源 yaml 里写死的主机名
#   （`etcd:2379` / `redis:6379` / `kafka:9092` / `jaeger:4317`）改成宿主可达地址。
#   ⇒ 真源仓全程 `git status` 干净（脚本会断言）。
#
# 用法：
#   bash tools/api-full-parity-probe/like/run.sh
#   GO_REPO=/path/to/Sea-RideTheWind_golang bash tools/api-full-parity-probe/like/run.sh
#
# 退出码：0 = 三段全绿；1 = 有差异；2 = 环境问题 / 真源漂移 / 依赖缺失。
#
# ─────────────────────────────────────────────────────────────────────────────
# 🩹 2026-10-29 · R4 lane 修的两个**真缺陷**（上一轮收工时登记）
#
# 🔴 缺陷 1：**没给 RAG 的 like-api 传验签密钥 ⇒ 进程起不来 ⇒ 探针 EXIT=2**
#   现象：`service/like/like-api/src/main/resources/application.yml:85` 写的是
#         `access-secret: ${USER_JWT_SECRET}`（**故意没有默认值** ⇒ P0 #8），
#         守卫 `LikeApiProperties#verifyAccessSecret()`（@PostConstruct）fail-closed
#         ⇒ 缺变量 ⇒ `IllegalStateException` ⇒ **应用起不来**。
#   事实：上一版脚本只导了 `JWT_SECRET`（**签发侧**）⇒ 从没给 **验签侧** 传过密钥。
#   修法：起 like-api 时补 `USER_JWT_SECRET="${USER_JWT_SECRET}"`（与签发同值）。
#         ⚠️ **只有 like-api 需要它** —— like-rpc 全模块零 JWT 引用，
#            它的两个 token 占位符都有默认值（空串）且对应功能默认关闭。
#
# 🔴 缺陷 2：cleanup 只收容器 ⇒ **Go 的 api/rpc 变孤儿**、继续占端口
#   事实：`( cd X && cmd ) &` ⇒ `$!` 是**子 shell** 的 PID（bash 对异步子 shell
#         会再 fork 一次）⇒ `kill $!` 杀的是子 shell ⇒ 真服务进程活着 ⇒ 端口不释放
#         ⇒ 实测残留占住 :18121 / :18122 ⇒ 污染下一轮。
#   修法：① 启动侧改 `exec`（子 shell 被**替换**成服务进程 ⇒ `$!` 就是服务 PID）；
#         ② 收尾侧**按端口复核**（kill 之后端口还在 ⇒ 再按签名清一遍）；
#         ③ 收尾后**断言** `lsof -nP -iTCP:<端口> -sTCP:LISTEN` 为空 ⇒ 不绿就不算跑完。
#
# ⚠️ 端口**自动挑**：在窗口 18161-18170 内挑 9 个空闲口（5 服务口 + 4 容器宿主口）
#    ⇒ 不硬编码撞车；窗口不够 ⇒ **exit 2**（环境问题），不越界抢别人的口。
# ─────────────────────────────────────────────────────────────────────────────
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAG_ROOT="$(cd "${HERE}/../../.." && pwd)"

# ── 宿主中间件端口（可用 env 覆盖；默认值 = 本机实测）────────────────────────
ETCD_PORT="${ETCD_PORT:-49319}"      # Sea-etcd    49319→2379
# ⚠️ etcdctl 是**在容器里跑**（`docker exec Sea-etcd etcdctl`）⇒ 端点必须是**容器内端口** 2379，
#    用宿主映射口 49319 会 connection refused。
ETCD_CTL_ENDPOINT="${ETCD_CTL_ENDPOINT:-http://127.0.0.1:2379}"
KAFKA_PORT="${KAFKA_PORT:-49992}"    # Sea-kafka   49992→9093
NACOS_PORT="${NACOS_PORT:-49848}"    # Sea-nacos   49848→8848
REDIS_HOST_PORT="${REDIS_HOST_PORT:-49380}"
JAEGER_PORT="${JAEGER_PORT:-49268}"  # Sea-jaeger  49268→4317

# ── 端口：**自动挑空闲的**（不许硬编码撞车）──────────────────────────────────
# ⚠️ 本探针需要 **9 个**宿主端口：
#    · 5 个服务口：Go like-api · Go like-rpc · RAG like-api · RAG like-rpc · 假 DC
#    · 4 个容器宿主口：两侧各一个 Postgres + 一个 Redis
#      （**两侧必须物理隔离**：阶段 C 要在**各自**的 Redis 上单独禁 ZCARD、
#        在**各自**的 PG 上单独删 like_record；阶段 A 的 likeaction 是**写路径**，
#        共用一个 Redis 会让 Go 的写和 RAG 的写打到同一批键上 ⇒ 响应必然不同 ⇒ 假漂移）
# ⇒ 本 lane 的端口窗口 = 18161-18170（10 个 ⇒ 留 1 个当「容器起不来」时的替补）。
# ⚠️ 不用 OS 临时段 49152-65535（宿主中间件 Sea-etcd/kafka/nacos/redis/jaeger 在那儿）。
# ⚠️ 不用 like 域的 Testcontainers 段 22790-22819（已被域内测试类逐个占死，
#    `LikeContainerFixedHostPortSegmentGateTest` 守门「全域不得重号」）。
PORT_WINDOW_START="${PORT_WINDOW_START:-18161}"
PORT_WINDOW_END="${PORT_WINDOW_END:-18170}"
# ⚠️ **上一版脚本写死的端口段**（18121-18125 / 18131-18134）在这里**只作一件事**：
#    把上一轮遗留的**孤儿进程**腾干净（缺陷 2：Go 的 api/rpc 变孤儿后仍占着 :18121/:18122
#    ⇒ 污染下一轮）。⚠️ 只杀**签名匹配本探针**的进程 ⇒ 不越界动别人的东西。
LEGACY_PORTS="18121 18122 18123 18124 18125 18131 18132 18133 18134"

# 端口全部由 alloc_ports() 在**运行时**分配（下面这行只是占位，避免 set -u 报未定义）
GO_API_PORT=""; GO_RPC_PORT=""; RAG_API_PORT=""; RAG_RPC_PORT=""; DC_PORT=""
GO_PG_PORT=""; GO_REDIS_PORT=""; RAG_PG_PORT=""; RAG_REDIS_PORT=""
# 容器宿主口的**候选池**（运行时分配；容器口不预分配 ⇒ 见 alloc_ports 的注释）
CONTAINER_PORT_POOL=""
# ⚠️ 容器名带 `$$`（每轮唯一）⇒ 别的轮/别的 lane 的 cleanup 绝不可能把本轮的库删掉。
GO_PG_CONTAINER="${GO_PG_CONTAINER:-r2like-go-pg-$$}"
GO_REDIS_CONTAINER="${GO_REDIS_CONTAINER:-r2like-go-redis-$$}"
RAG_PG_CONTAINER="${RAG_PG_CONTAINER:-r2like-rag-pg-$$}"
RAG_REDIS_CONTAINER="${RAG_REDIS_CONTAINER:-r2like-rag-redis-$$}"

# ── 验签密钥（必须与签发方一致；真源 likecenter.yaml:6 的原值）──────────────
# Go 出处（**消费点**）：`service/like/api/internal/handler/routes.go:49`
#   `rest.WithJwt(serverCtx.Config.UserAuth.AccessSecret)`
#   ⇒ AccessSecret 的声明点 = `service/like/api/etc/likecenter.yaml:6`
JWT_SECRET="${JWT_SECRET:-ae0536f9-6450-4606-8e13-5a19ed505da0}"
# 🔴🔴 **同一个值必须同时喂给 RAG 的 like-api 进程**（缺陷 1 · 2026-10-29）：
#   RAG 侧 `service/like/like-api/src/main/resources/application.yml:85` 写的是
#   `access-secret: ${USER_JWT_SECRET}`（**没有默认值** ⇒ P0 #8 的 fail-closed 守卫），
#   守卫 = `LikeApiProperties#verifyAccessSecret()`（@PostConstruct）
#   ⇒ **不传 `USER_JWT_SECRET` ⇒ 进程起不来**（`IllegalStateException`）⇒ 探针 EXIT=2。
#   ⇒ 本探针「签发用 JWT_SECRET / 验签用 USER_JWT_SECRET」，**两者同值**是既定设计。
#   ⚠️ **只有 like-api 需要它**：like-rpc 全模块**零** JWT 引用（grep 实证），
#      它的 `DC_PLATFORM_SERVICE_TOKEN` / `LIKE_FACT_AUTHORITY_TOKEN` 两处都是
#      `${…:}`（**有默认值 = 空串**）且对应功能默认**关闭**
#      （`like.fact.dispatch-enabled: false` / `like.authority.enabled: false`）⇒ 不会 fail-closed。
USER_JWT_SECRET="${USER_JWT_SECRET:-${JWT_SECRET}}"
# ── 假 DC 的 token（Go `cmd/fact-dispatch/main.go:55` 要求 **≥32 字节**）──────
DC_TOKEN="${DC_TOKEN:-r2like-probe-data-center-service-token-0000000001}"

WORK="$(mktemp -d)"
GO_PIDS=(); RAG_PIDS=(); DC_PIDS=()
say()  { printf '%s\n' "$*"; }
step() { printf '\n== %s ==\n' "$*"; }
dump_logs() {
  local f
  for f in go-rpc.log go-api.log rag-rpc.log rag-api.log rag-rpc2.log go-fact-dispatch.log; do
    [ -f "${WORK:-/nonexistent}/${f}" ] || continue
    printf '\n---- %s（末 30 行）----\n' "${f}" >&2
    tail -30 "${WORK}/${f}" | sed 's/^/   | /' >&2
  done
}
die()  { printf '!! %s\n' "$*" >&2; dump_logs; exit 2; }

host_port_open() { nc -z -G 2 127.0.0.1 "$1" >/dev/null 2>&1; }

# ── 「这个口还有人在**监听**吗」（**lsof 判据**，不是 nc -z）────────────────────
# ⚠️ **为什么「等端口释放」不能用 `nc -z`**（2026-10-29 实测踩出来的）：
#    `nc -z` 只问「能不能连上」⇒ **docker-proxy 保留了宿主口、但没在 accept** 时
#    它会说「没人监听」⇒ 端口**看着是空的**、实际 `docker run -p` 照样报
#    "port is already allocated" ⇒ 探针在第 1 步就死 ⇒ 看起来像「环境坏了」。
#    ⇒ `lsof -nP -iTCP:<port> -sTCP:LISTEN` 直接问「**谁在 LISTEN**」⇒
#      docker-proxy 保留着的时候**看得见**（`com.docker.backend`）⇒ 才是可靠判据。
#    ⚠️ 本机（2026-10-29 实测）docker 的口**通常是瞬时释放**的 ⇒ 轮询一般一轮就过；
#       但**不能**因为「通常」就不等 ⇒ 判据换成 lsof 后等的就是**真的**释放。
port_listening() { lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1; }

# ── 这个口是被 **docker 保留**着，还是被**别的进程**占着？────────────────────
# ⇒ docker 保留 ⇒ 等它放出来就还能用；别人占着 ⇒ 等也没用 ⇒ 直接换口。
# ⚠️ 判据 = 持有者的**命令行**里有没有 docker（macOS Docker Desktop 的
#    `com.docker.backend` / `vpnkit`）⇒ 不用 PID（PID 每次都变）。
port_holder_is_docker() {   # $1 = 端口 ⇒ 0 = 持有者是 docker
  local p="$1" pid cmd
  for pid in $(lsof -ti tcp:"${p}" -sTCP:LISTEN 2>/dev/null); do
    cmd="$(ps -p "${pid}" -o command= 2>/dev/null)"
    case "${cmd}" in
      *com.docker*|*docker*|*vpnkit*) return 0 ;;
    esac
  done
  return 1
}

# ── 等一个口**真的**被放出来（lsof 判据；最多等 $2 秒）⇒ 0 = 已释放 ──────────
wait_port_released() {   # $1 = 端口  $2 = 最多等多少秒
  local p="$1" max="${2:-20}" i
  for i in $(seq 1 "${max}"); do
    port_listening "${p}" || return 0
    # 持有者**不是 docker** ⇒ 等也没用（是别人的业务进程）⇒ 立刻判失败
    port_holder_is_docker "${p}" || return 1
    sleep 1
  done
  return 1
}

# ── 注销 Nacos 里的**残留实例**（上一轮的旧实例在心跳超时前仍是「健康」的）──
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

# ── etcd 访问（**容器内**端点；键 = `<key>/<leaseID>` 前缀族）────────────────
etcd_get_value() {
  docker exec Sea-etcd etcdctl --endpoints="${ETCD_CTL_ENDPOINT}" get "$1" --prefix --print-value-only 2>/dev/null | head -1
}
etcd_has_key() { [ -n "$(etcd_get_value "$1")" ]; }

# ── 「这个进程是不是本探针的？」─────────────────────────────────────────────
# 🔴 **绝不按端口无差别 kill**：`lsof -ti tcp:<port>` 在端口被**容器发布**时
#    返回的是 `com.docker.backend services` ⇒ 无差别 kill 会**当场杀掉 Docker daemon**
#    （2026-10-29 实测：favorite-rpc 探针第一次跑就是这么报废的）。
#    ⇒ 只杀签名匹配本探针的；**别人的进程 ⇒ 报错退出，不越界动手**。
is_own_probe_process() {
  case "$1" in
    # 本探针自己的工作目录（Go 二进制、配置副本、假 DC 的输出目录都在里面）
    *api-full-parity-probe*|*"${WORK}"*) return 0 ;;
    # RAG 侧的两个 fat jar
    *like-api-*SNAPSHOT.jar*|*like-rpc-*SNAPSHOT.jar*) return 0 ;;
    # ⚠️ **上一轮遗留的孤儿**（缺陷 2）：Go 二进制落在 `mktemp -d` 出来的
    #    `/var/folders/…/tmp.XXXX/{api,rpc,fact-dispatch}` 下 ⇒ 旧 WORK 也匹配得上。
    */tmp.*/api|*/tmp.*/rpc|*/tmp.*/fact-dispatch) return 0 ;;
  esac
  return 1
}

# ── 回收**本 lane 自己的**残留容器（名字前缀 r2like-）────────────────────────
# 🔴 为什么必须**先回收容器、再挑端口**：宿主上**发布端口**的容器会让
#    `com.docker.backend services` 这个进程**持有该端口** ⇒ `lsof -ti tcp:<port>`
#    看到的是 docker 后端，不是探针的进程 ⇒ 「按端口无差别 kill」会误杀 docker 后端。
#    ⇒ 正确顺序：先删掉**自己前缀**的容器（端口随之释放），再查端口。
reclaim_own_containers() {
  local c ports="" p i
  for c in $(docker ps -a --format '{{.Names}}' 2>/dev/null | grep '^r2like-' || true); do
    ports="${ports} $(docker inspect -f '{{range $q, $conf := .NetworkSettings.Ports}}{{range $conf}}{{.HostPort}} {{end}}{{end}}' "${c}" 2>/dev/null)"
    docker rm -f "${c}" >/dev/null 2>&1 && say "   · 回收本 lane 残留容器 ${c}"
  done
  # ⚠️ docker-proxy 释放宿主口**不是瞬时**的 ⇒ 轮询等它真的还回来
  #    （判据用 **lsof** 而不是 `nc -z` ⇒ 后者在 docker 保留着口时会误报「已释放」）
  for p in ${ports}; do
    [ -n "${p}" ] || continue
    for i in $(seq 1 20); do port_listening "${p}" || break; sleep 1; done
  done
}

# ── 本探针**真正占用**的全部端口（5 个服务口 + 4 个容器宿主口）────────────────
# ⚠️ **只算「真正用过的」**：`CONTAINER_PORT_POOL` 是**候选池**——本轮不一定每个都绑上
#    ⇒ 把它算进来 ⇒ 若**别的 lane 的容器**恰好占着候选池里的某个口
#    ⇒ 收尾断言会把**别人的**占用当成「本探针的残留」⇒ **假红**。
#    ⇒ 候选池只进「开跑前清残留」（`free_probe_ports`），**不进**收尾断言。
all_probe_ports() {
  printf '%s\n' "${GO_API_PORT} ${GO_RPC_PORT} ${RAG_API_PORT} ${RAG_RPC_PORT} ${DC_PORT} ${GO_PG_PORT} ${GO_REDIS_PORT} ${RAG_PG_PORT} ${RAG_REDIS_PORT}"
}

# ── 容器宿主口的**候选池**（本轮没绑上的候选口也在内）────────────────────────
candidate_ports() { printf '%s\n' "${CONTAINER_PORT_POOL}"; }

# ── 端口预检：把**上一轮残留**的探针进程踢掉（否则 Go rpc 会 panic）──────────
free_probe_ports() {
  local p pid cmd
  # ① **服务口**：必须干净 ⇒ 别人的进程占着 ⇒ **报错退出**（不越界动手）
  for p in "${GO_API_PORT}" "${GO_RPC_PORT}" "${RAG_API_PORT}" "${RAG_RPC_PORT}" "${DC_PORT}"; do
    [ -n "${p}" ] || continue
    for pid in $(lsof -ti tcp:"${p}" -sTCP:LISTEN 2>/dev/null); do
      cmd="$(ps -p "${pid}" -o command= 2>/dev/null)"
      if is_own_probe_process "${cmd}"; then
        kill -9 "${pid}" 2>/dev/null
        say "   · 踢掉占用 :${p} 的**本探针**残留进程 ${pid}"
      else
        die "端口 :${p} 被**无关**进程占用（pid ${pid}：${cmd}）⇒ 本探针不越界杀进程；请改端口窗口后重跑"
      fi
    done
  done
  # ② **容器候选口**：⚠️ 这里**绝不 die** ——
  #    候选口可能是 **docker 保留着**的（`com.docker.backend` 持有它的 LISTEN socket）
  #    ⇒ 那不是「别人的业务进程」⇒ 真正的判据交给 `pick_port`（真起一次容器才知道）
  for p in $(candidate_ports); do
    [ -n "${p}" ] || continue
    for pid in $(lsof -ti tcp:"${p}" -sTCP:LISTEN 2>/dev/null); do
      cmd="$(ps -p "${pid}" -o command= 2>/dev/null)"
      if is_own_probe_process "${cmd}"; then
        kill -9 "${pid}" 2>/dev/null
        say "   · 踢掉占用 :${p} 的**本探针**残留进程 ${pid}"
      else
        say "   · 端口 :${p} 被非本探针进程持有（pid ${pid}：${cmd}）⇒ 留给 pick_port 真试时跳过" >&2
      fi
    done
  done
  # ⚠️ **上一版脚本写死的端口段**：把上一轮遗留的孤儿（Go api/rpc 变孤儿后仍占着
  #    :18121/:18122）腾干净 ⇒ 否则它们会一直占着宿主端口、污染下一轮。
  for p in ${LEGACY_PORTS}; do
    for pid in $(lsof -ti tcp:"${p}" -sTCP:LISTEN 2>/dev/null); do
      cmd="$(ps -p "${pid}" -o command= 2>/dev/null)"
      if is_own_probe_process "${cmd}"; then
        kill -9 "${pid}" 2>/dev/null
        say "   · 清掉上一版脚本遗留的孤儿进程 ${pid}（:${p}）"
      fi
    done
  done
  sleep 1
}

# ── 在端口窗口内自动挑空闲端口（**不许硬编码撞车**）────────────────────────
# ⚠️ **两类端口的「空闲」判据不一样**（2026-10-29 实测踩出来的）：
#   · **服务口**（普通 TCP bind）⇒ `nc -z` 判据**准确** ⇒ 窗口里扫一遍就行。
#   · **容器宿主口** ⇒ `nc -z` **不准确**：macOS Docker Desktop 的 `com.docker.backend`
#     在**容器被删之后**仍会**保留宿主口的占用** ⇒ `nc -z` 说「没人监听」、
#     端口**看着是空的** ⇒ 但 `docker run -p` 报 "port is already allocated"
#     ⇒ 探针在第 1 步就死 ⇒ 看起来像「环境坏了」。
#     ⇒ 唯一可靠的判据 = **真起一次容器**（`pick_port` 就是干这个的）
#     ⇒ ⇒ 容器口**不预分配**，改成「候选池 + 逐个真试」。
alloc_ports() {
  local p free="" n=0 pool="" used=""
  # ① 5 个服务口
  for p in $(seq "${PORT_WINDOW_START}" "${PORT_WINDOW_END}"); do
    host_port_open "${p}" && continue
    free="${free} ${p}"
    n=$((n + 1))
    [ "${n}" -ge 5 ] && break
  done
  [ "${n}" -ge 5 ] || die "端口窗口 ${PORT_WINDOW_START}-${PORT_WINDOW_END} 里只剩 ${n} 个空闲的**服务口**（要 5 个）⇒ 别的 lane 占了本段 ⇒ 换段重跑（PORT_WINDOW_START=… PORT_WINDOW_END=…）"
  # shellcheck disable=SC2086
  set -- ${free}
  GO_API_PORT="$1"; GO_RPC_PORT="$2"; RAG_API_PORT="$3"; RAG_RPC_PORT="$4"; DC_PORT="$5"
  # ② 容器口的候选池 = 窗口里**没被服务口占用**的其余端口
  for p in $(seq "${PORT_WINDOW_START}" "${PORT_WINDOW_END}"); do
    case " ${free} " in *" ${p} "*) continue ;; esac
    pool="${pool} ${p}"
  done
  n=$(printf '%s' "${pool}" | wc -w | tr -d '[:space:]')
  [ "${n}" -ge 4 ] || die "端口窗口里只剩 ${n} 个可作容器宿主口的候选（要 4 个）⇒ 换段重跑"
  # ⚠️ **轮转起点**：按 `$$` 打散 ⇒ 连续两轮**不会**从同一个候选开始试
  #    ⇒ 上一轮被 docker 保留的那个口 ⇒ 这一轮排在后面 ⇒ 撞车概率大降。
  local off=$(( $$ % n )) i=0 head="" tail=""
  # shellcheck disable=SC2086
  for p in ${pool}; do
    if [ "${i}" -lt "${off}" ]; then tail="${tail} ${p}"; else head="${head} ${p}"; fi
    i=$((i + 1))
  done
  CONTAINER_PORT_POOL="${head}${tail}"
  say "   · 端口自动选择：服务口 ${GO_API_PORT}/${GO_RPC_PORT}/${RAG_API_PORT}/${RAG_RPC_PORT}/${DC_PORT}"
  say "   ·                 容器口候选池 ${CONTAINER_PORT_POOL}（真起容器才知能不能用）"
}

# ── 等一批端口**真的**被释放 ────────────────────────────────────────────────
# ⚠️ 两种等法：
#   · 服务口（strict）：还开着 ⇒ 看是谁占的；**不是本探针的** ⇒ 等也没用 ⇒ 不等
#     （例如别的 lane 抢了这个口 ⇒ 我们无权动它）。
#   · 容器口（plain）：宿主口由 **docker-proxy** 持有 ⇒ 删了容器才还回来 ⇒ 无条件等。
wait_ports_closed() {   # $1 = strict|plain   $2.. = 端口列表
  local mode="$1"; shift
  local p pid cmd i foreign
  for p in "$@"; do
    [ -n "${p}" ] || continue
    for i in $(seq 1 30); do
      port_listening "${p}" || break
      if [ "${mode}" = "strict" ]; then
        foreign=0
        for pid in $(lsof -ti tcp:"${p}" -sTCP:LISTEN 2>/dev/null); do
          cmd="$(ps -p "${pid}" -o command= 2>/dev/null)"
          is_own_probe_process "${cmd}" || foreign=1
        done
        [ "${foreign}" = "1" ] && break
      fi
      sleep 1
    done
  done
}

# ── 收尾：把服务**真正**停掉（不是「kill 了就算」）──────────────────────────
# 🔴🔴 **缺陷 2 的本体**：上一版 cleanup 只 `kill` 掉 `${GO_PIDS[@]}` 里的 PID ⇒
#    而那批 PID 是 `( cd X && cmd ) &` 的**子 shell**（实测：bash 对 `&` 的异步子 shell
#    会**再 fork 一次**跑命令 ⇒ `$!` ≠ 服务 PID）⇒ **真正的 Go api/rpc 进程变孤儿**
#    ⇒ 继续占着 :18121/:18122 ⇒ 污染下一轮。
#    ⇒ 修法有两半：① 启动侧改用 `exec`（让 `$!` 就是服务 PID）；
#                   ② 收尾侧**按端口复核**（kill 之后端口还在 ⇒ 再按签名清一遍）。
teardown() {
  local p pid cmd
  # ① 先礼后兵：SIGTERM → SIGKILL
  for p in ${GO_PIDS[@]+"${GO_PIDS[@]}"} ${RAG_PIDS[@]+"${RAG_PIDS[@]}"} ${DC_PIDS[@]+"${DC_PIDS[@]}"}; do kill "$p" 2>/dev/null; done
  sleep 1
  for p in ${GO_PIDS[@]+"${GO_PIDS[@]}"} ${RAG_PIDS[@]+"${RAG_PIDS[@]}"} ${DC_PIDS[@]+"${DC_PIDS[@]}"}; do kill -9 "$p" 2>/dev/null; done
  # ② **按端口复核**：服务口必须真的关了（这一步才是「不留孤儿」的判据）
  wait_ports_closed strict "${GO_API_PORT}" "${GO_RPC_PORT}" "${RAG_API_PORT}" "${RAG_RPC_PORT}" "${DC_PORT}"
  # ③ 收容器（容器的宿主口由 docker-proxy 持有 ⇒ 删了容器才还回来）
  docker rm -f "${GO_PG_CONTAINER}" >/dev/null 2>&1
  docker rm -f "${GO_REDIS_CONTAINER}" >/dev/null 2>&1
  docker rm -f "${RAG_PG_CONTAINER}" >/dev/null 2>&1
  docker rm -f "${RAG_REDIS_CONTAINER}" >/dev/null 2>&1
  wait_ports_closed plain "${GO_PG_PORT}" "${GO_REDIS_PORT}" "${RAG_PG_PORT}" "${RAG_REDIS_PORT}"
  # ④ 兜底：端口上还有东西 ⇒ **只杀签名匹配本探针的**（不越界动别人的）
  for p in $(all_probe_ports); do
    [ -n "${p}" ] || continue
    for pid in $(lsof -ti tcp:"${p}" -sTCP:LISTEN 2>/dev/null); do
      cmd="$(ps -p "${pid}" -o command= 2>/dev/null)"
      if is_own_probe_process "${cmd}"; then
        kill -9 "${pid}" 2>/dev/null
        say "   · 收尾：清掉残留的**本探针**进程 ${pid}（:${p}）"
      else
        say "   ⚠️ 端口 :${p} 仍有**非本探针**进程（pid ${pid}：${cmd}）⇒ 不越界杀" >&2
      fi
    done
  done
  GO_PIDS=(); RAG_PIDS=(); DC_PIDS=()
}

# ── 收尾后**断言**：本探针的端口上一个残留都不能有 ──────────────────────────
# ⚠️ 判据 = `lsof -nP -iTCP:<端口> -sTCP:LISTEN` 为空（= 「没人还在监听」）。
#    ⚠️ 刻意**只查 LISTEN**：一个进程可能只是**连出**到这个口（客户端 socket）⇒
#    那不是「占了端口」⇒ 不该算残留。
assert_no_leftover() {
  local p pid cmd bad=0
  for p in $(all_probe_ports); do
    [ -n "${p}" ] || continue
    for pid in $(lsof -ti tcp:"${p}" -sTCP:LISTEN 2>/dev/null); do
      cmd="$(ps -p "${pid}" -o command= 2>/dev/null)"
      say "   ❌ 端口 :${p} 仍有残留（pid ${pid}：${cmd}）"
      bad=1
    done
  done
  [ "${bad}" = "0" ] && say "   ✓ 收尾复核：本探针的 9 个端口**零残留**"
  return "${bad}"
}

cleanup() {
  local p
  # ⚠️ macOS 自带 bash 3.2：`set -u` 下展开**空数组**会报 `unbound variable`
  #    ⇒ 用 `${A[@]+"${A[@]}"}` 形式。
  teardown
  # ⚠️ **不删临时目录**：`rm -rf` 在本机会被回收站守卫整体拒绝
  #    ⇒ 留着 `/var/folders/.../tmp.*` 由系统清理（比让脚本自杀强）。
  if [ "${KEEP_WORK:-0}" = "1" ]; then
    say "   · KEEP_WORK=1 ⇒ 保留 ${WORK}"
  else
    say "   · 临时目录 ${WORK}（本机禁 rm -rf ⇒ 留给系统清理）"
  fi
}
trap cleanup EXIT

# ══════════════════════════ 0. 前置检查（缺 ⇒ exit 2，绝不静默降级）══════════════
step "0/11 前置检查"
for cmd in docker go mvn java curl xxd shasum lsof nc python3; do
  command -v "${cmd}" >/dev/null 2>&1 || die "缺命令：${cmd}"
done
GO_REPO="${GO_REPO:-/Users/pr/Documents/java&golang/Sea-RideTheWind_golang}"
[ -d "${GO_REPO}/service/like" ] || die "找不到 Go 真源：${GO_REPO}/service/like（用 GO_REPO=... 指定）"
for pair in "Sea-etcd:${ETCD_PORT}" "Sea-kafka:${KAFKA_PORT}" "Sea-nacos:${NACOS_PORT}" "Sea-redis:${REDIS_HOST_PORT}" "Sea-jaeger:${JAEGER_PORT}"; do
  c="${pair%%:*}"; p="${pair##*:}"
  docker ps --format '{{.Names}}' | grep -qx "${c}" || die "容器 ${c} 没在跑（宿主中间件缺失）"
  nc -z -G 2 127.0.0.1 "${p}" || die "宿主端口 ${p} 不通（${c} 的映射口）"
done
docker exec Sea-etcd etcdctl --endpoints="${ETCD_CTL_ENDPOINT}" endpoint health >/dev/null 2>&1 \
  || die "容器内 etcd 端点 ${ETCD_CTL_ENDPOINT} 不可用（ETCD_CTL_ENDPOINT 可覆盖）"
# ⚠️ **顺序不能颠倒**：先回收自己前缀的**残留容器**（容器发布端口时是 docker 后端
#    持有该端口 ⇒ 不先删容器就去「按端口无差别 kill」⇒ **会误杀 Docker daemon**），
#    再在窗口内**自动挑空闲端口**，最后才清端口上的残留进程。
reclaim_own_containers
alloc_ports
free_probe_ports
say "   ✓ 命令齐 / 中间件在跑"

# ══════════════════════════ 1. 两侧各自的 Postgres + Redis ══════════════════════
step "1/11 两侧 Postgres + Redis（各一个容器，互不污染）"

start_pg() {   # $1 = 容器名  $2 = 宿主端口
  local c="$1" p="$2" out state deadline
  docker rm -f "${c}" >/dev/null 2>&1
  # ⚠️ **不吞掉 docker 的报错**：上一轮探针的容器刚被删掉时，docker-proxy 还会占着宿主口
  #    ⇒ `docker run -p` 会报 "port is already allocated" ⇒ 这里把它打出来才能定位。
  # ⚠️ 返回码分工（2026-10-29 加）：**1 = docker run 失败（口的问题）** · **2 = 起来了但没就绪（不是口的问题）**
  #    ⇒ 区分开 ⇒ 「口被 docker 保留」才换口，「PG 慢」不该换口（换口也是慢 ⇒ 白等）。
  if ! out=$(docker run -d --name "${c}" -e POSTGRES_USER=admin -e POSTGRES_PASSWORD=sea \
    -e POSTGRES_DB=first_db -p "${p}:5432" postgres:16-alpine 2>&1); then
    say "   · docker run 失败（:${p}）：${out}" >&2
    return 1
  fi
  # 🔴🔴 **按墙钟等就绪，不按「迭代次数」**（2026-10-29 实测踩过）：
  #    机器负载高时（load average 一度打到 **36**）`docker exec` 本身就很慢（单次 1~3s）
  #    ⇒ 「60 次 × sleep 1」**远小于** 60 秒 ⇒ PG 还没 init 完就被判「没就绪」
  #    ⇒ 探针**误判成「环境坏了」**（EXIT=2）⇒ 变异套件那一轮白跑。
  #    ⇒ 改成 `deadline`（墙钟秒）＋ 轮询里顺带看**容器状态**：
  #    容器 exited/dead ⇒ 真的起不来 ⇒ 立刻失败（别白等）；created/running ⇒ 继续等。
  deadline=$(( $(date +%s) + ${PG_READY_TIMEOUT:-240} ))
  while [ "$(date +%s)" -lt "${deadline}" ]; do
    if docker exec "${c}" pg_isready -U admin -d first_db >/dev/null 2>&1; then
      for _ in $(seq 1 30); do host_port_open "${p}" && return 0; sleep 1; done
      return 2
    fi
    state="$(docker inspect -f '{{.State.Status}}' "${c}" 2>/dev/null)"
    case "${state}" in
      exited|dead)
        say "   · :${p} 的 PG 容器状态=${state} ⇒ 起不来（不是端口问题）" >&2
        docker logs "${c}" 2>&1 | tail -5 | sed 's/^/      | /' >&2
        return 2 ;;
    esac
    sleep 2
  done
  say "   · :${p} 的 PG 在 ${PG_READY_TIMEOUT:-240}s 内没就绪" >&2
  return 2
}
start_redis() {  # $1 = 容器名  $2 = 宿主端口
  local c="$1" p="$2" out
  docker rm -f "${c}" >/dev/null 2>&1
  # ⚠️ 返回码分工：1 = docker run 失败（口的问题） · 2 = 起来了但连不上（不是口的问题）
  if ! out=$(docker run -d --name "${c}" -p "${p}:6379" redis:7-alpine 2>&1); then
    say "   · docker run 失败（:${p}）：${out}" >&2
    return 1
  fi
  # ⚠️ 就绪等待**按墙钟**（见 start_redis_wait）
  start_redis_wait "${c}" "${p}"
}
# ⚠️ Redis 的就绪也**按墙钟等**（负载高时 `nc -z` + `docker` 调用都会变慢 ⇒ 迭代次数不等于秒）
#    ⇒ 并且轮询里看**容器状态**：exited/dead ⇒ 真的起不来 ⇒ 立刻失败（别白等）。
start_redis_wait() {   # $1 = 容器名  $2 = 宿主端口
  local c="$1" p="$2" state deadline
  deadline=$(( $(date +%s) + ${REDIS_READY_TIMEOUT:-60} ))
  while [ "$(date +%s)" -lt "${deadline}" ]; do
    host_port_open "${p}" && return 0
    state="$(docker inspect -f '{{.State.Status}}' "${c}" 2>/dev/null)"
    case "${state}" in
      exited|dead)
        say "   · :${p} 的 Redis 容器状态=${state} ⇒ 起不来（不是端口问题）" >&2
        return 2 ;;
    esac
    sleep 1
  done
  return 2
}
# 端口阶梯：宿主口被**上一轮的 docker-proxy** 占着时换下一个
# （2026-10-29 实测踩过：容器刚删，端口还没释放 ⇒ `docker run -p` 报
#   "port is already allocated" ⇒ 探针在第 1 步就死 ⇒ 看起来像「环境坏了」）
#
# 🔴🔴 **本函数里的 `say` 必须走 stderr**：`pick_port` 是在 `$( )` 里跑的
#    ⇒ `say` 写 stdout 会**混进命令替换的捕获值** ⇒ 端口变量变成
#    "   · :18133 起不来 ⇒ 换下一个\n18143" ⇒ 后续的 JDBC URL 变成
#    `jdbc:postgresql://127.0.0.1:   · :18133 …` ⇒ 启动失败 ⇒
#    症状是「RAG like-rpc 起不来」，而真正的原因藏在**端口变量**里（2026-10-29 实测）。
# 这个口是不是已经被**本轮别的容器**用掉了？⇒ 跳过（否则两个容器抢一个口）
port_taken_by_sibling() {   # $1 = 端口 ⇒ 0 = 已被占用
  local c
  for c in "${GO_PG_CONTAINER}" "${GO_REDIS_CONTAINER}" "${RAG_PG_CONTAINER}" "${RAG_REDIS_CONTAINER}"; do
    if docker inspect -f '{{range $q, $conf := .NetworkSettings.Ports}}{{range $conf}}{{.HostPort}} {{end}}{{end}}' "${c}" 2>/dev/null \
        | tr ' ' '\n' | grep -qx "${1}"; then
      return 0
    fi
  done
  return 1
}
pick_port() {   # $1 = 类型(pg|redis)  $2 = 容器名  $3.. = 候选端口 ⇒ 成功时把端口打到 stdout
  local kind="$1" c="$2"; shift 2
  local p attempt rc
  for p in "$@"; do
    if port_taken_by_sibling "${p}"; then
      say "   · :${p} 已被本轮别的容器占用 ⇒ 跳过" >&2
      continue
    fi
    for attempt in 1 2 3; do
      if [ "${kind}" = "pg" ]; then
        start_pg "${c}" "${p}"; rc=$?
      else
        start_redis "${c}" "${p}"; rc=$?
      fi
      [ "${rc}" = "0" ] && { printf '%s' "${p}"; return 0; }
      # ⚠️ 起来了但没就绪 ⇒ **不是口的问题** ⇒ 换口也没用 ⇒ 直接放弃
      [ "${rc}" = "2" ] && { say "   · :${p} 起来了但没就绪（不是端口问题）⇒ 放弃这个口" >&2; return 2; }
      # docker run 失败 ⇒ 大概率是**这个口被 docker 保留着** ⇒
      #   ① 删掉半拉子容器；② **等它真的被放出来**（判据 = lsof 看不见 LISTEN）
      #   （⚠️ 用 `nc -z` 等是**等不到的**：docker 保留着口时 `nc -z` 误报「已释放」
      #     ⇒ 上一轮「还原后转绿那一跑」就撞在这个坑上 ⇒ 看着像环境坏了）
      say "   · :${p} docker run 失败（第 ${attempt}/3 次）⇒ 等 docker 真正放出口" >&2
      docker rm -f "${c}" >/dev/null 2>&1
      if wait_port_released "${p}" 20; then
        say "   · :${p} 已释放 ⇒ 重试同一个口" >&2
      else
        say "   · :${p} 仍被占（持有者不是 docker ⇒ 等也没用）⇒ 换下一个候选口" >&2
        break
      fi
    done
    say "   · :${p} 起不来 ⇒ 换下一个" >&2
  done
  return 1
}
# ⚠️ **残留容器已在第 0 步按前缀清过**（`reclaim_own_containers`）—— 必须放在
#    「自动挑端口」**之前**：容器发布端口时是 docker 后端持有该端口 ⇒ 不先删容器
#    就去按端口无差别 kill ⇒ 会误杀 Docker daemon。
# ⚠️ 容器口的**替补**来自窗口里剩下的空闲口（`CONTAINER_PORT_POOL`）⇒ 容器刚被删、
#    docker-proxy 还没把宿主口还回来时，`docker run -p` 会报 "port is already allocated"
#    ⇒ 这里换下一个候选口（而不是撞车或静默换口）。
# ⚠️ 容器口**不预分配** ⇒ 直接从候选池里**真起一次**试出来（见 alloc_ports 的注释）
#    ⇒ 返回码 2 = 起来了但没就绪（**不是**口的问题）⇒ 与「口不够」分开报。
GO_PG_PORT="$(pick_port pg "${GO_PG_CONTAINER}" ${CONTAINER_PORT_POOL})"; rc=$?
[ "${rc}" = "0" ] || { [ "${rc}" = "2" ] && die "Go 侧 PG 起来了但没就绪（不是端口问题）"; die "Go 侧 PG 起不来（候选口 ${CONTAINER_PORT_POOL} 全部不可用）"; }
GO_REDIS_PORT="$(pick_port redis "${GO_REDIS_CONTAINER}" ${CONTAINER_PORT_POOL})"; rc=$?
[ "${rc}" = "0" ] || { [ "${rc}" = "2" ] && die "Go 侧 Redis 起来了但没就绪（不是端口问题）"; die "Go 侧 Redis 起不来（候选口 ${CONTAINER_PORT_POOL} 全部不可用）"; }
RAG_PG_PORT="$(pick_port pg "${RAG_PG_CONTAINER}" ${CONTAINER_PORT_POOL})"; rc=$?
[ "${rc}" = "0" ] || { [ "${rc}" = "2" ] && die "RAG 侧 PG 起来了但没就绪（不是端口问题）"; die "RAG 侧 PG 起不来（候选口 ${CONTAINER_PORT_POOL} 全部不可用）"; }
RAG_REDIS_PORT="$(pick_port redis "${RAG_REDIS_CONTAINER}" ${CONTAINER_PORT_POOL})"; rc=$?
[ "${rc}" = "0" ] || { [ "${rc}" = "2" ] && die "RAG 侧 Redis 起来了但没就绪（不是端口问题）"; die "RAG 侧 Redis 起不来（候选口 ${CONTAINER_PORT_POOL} 全部不可用）"; }
say "   ✓ 两侧 Postgres + Redis 就绪（宿主端口已验证可连）"

# ══════════════════════════ 2. 构建 Go 侧二进制（真源**只读**）══════════════════
step "2/11 构建 Go 侧二进制（api / rpc / fact-dispatch）"
( cd "${GO_REPO}" && CGO_ENABLED=0 GOPROXY=off GOFLAGS=-mod=mod GOTOOLCHAIN=local \
    go build -o "${WORK}/" ./service/like/api ./service/like/rpc ./service/like/rpc/cmd/fact-dispatch ) \
  || die "Go 侧构建失败"
[ -x "${WORK}/api" ] && [ -x "${WORK}/rpc" ] && [ -x "${WORK}/fact-dispatch" ] || die "Go 侧二进制没产出"
say "   ✓ api / rpc / fact-dispatch 构建完成"

# ══════════════════════════ 3. Go 侧配置副本（真源 yaml 写死主机名）══════════════
step "3/11 生成 Go 侧配置副本"
mkdir -p "${WORK}/etc" "${WORK}/log"
# ⚠️ **DevServer 必须关掉**：go-zero v1.10.2 `zrpc/config.go` 的 `RpcServerConf.SetUp()`
#    会调 `ServiceConf.SetUp()` ⇒ `core/service/serviceconf.go:79` `devserver.StartAgent(sc.DevServer)`
#    ⇒ **rpc 会额外绑一个 9098**（DevServer 的 Host:Port）⇒ 与探针端口段抢位。
#    DevServer 只提供 /metrics 与 /healthz，**不在本探针的比较面内** ⇒ 关掉是安全且必要的。
sed -e "s|etcd:2379|127.0.0.1:${ETCD_PORT}|g" \
    -e "s|jaeger:4317|127.0.0.1:${JAEGER_PORT}|g" \
    -e "s|^Port: 18887$|Port: ${GO_API_PORT}|" \
    -e "s|Path: ../../../log/like$|Path: ${WORK}/log/like|" \
    -e "s|^  Enabled: true$|  Enabled: false|" \
    "${GO_REPO}/service/like/api/etc/likecenter.yaml" > "${WORK}/etc/likecenter.yaml"
sed -e "s|etcd:2379|127.0.0.1:${ETCD_PORT}|g" \
    -e "s|jaeger:4317|127.0.0.1:${JAEGER_PORT}|g" \
    -e "s|redis:6379|127.0.0.1:${GO_REDIS_PORT}|g" \
    -e "s|kafka:9092|127.0.0.1:${KAFKA_PORT}|g" \
    -e "s|^ListenOn: 0.0.0.0:8082$|ListenOn: 0.0.0.0:${GO_RPC_PORT}|" \
    -e "s|Path: ../../../log/like-rpc$|Path: ${WORK}/log/like-rpc|" \
    -e "s|^  Enabled: true$|  Enabled: false|" \
    -e "s|\${POSTGRES_USER}|admin|g" \
    -e "s|\${POSTGRES_PASSWORD}|sea|g" \
    -e "s|\${POSTGRES_ADDR}|127.0.0.1|g" \
    -e "s|\${POSTGRES_PORT}|${GO_PG_PORT}|g" \
    -e "s|\${POSTGRES_DB}|first_db|g" \
    "${GO_REPO}/service/like/rpc/etc/like.yaml" > "${WORK}/etc/like.yaml"
# ⚠️ 逐条断言「改了哪项」——sed 缩进写错会**静默不命中** ⇒ 副本没改 ⇒ 端口被占 ⇒ 探针假挂。
grep -q "^Port: ${GO_API_PORT}$"                "${WORK}/etc/likecenter.yaml" || die "副本里 likecenter 的 Port 没改成 ${GO_API_PORT}（sed 没命中？）"
grep -q "^ListenOn: 0.0.0.0:${GO_RPC_PORT}$"     "${WORK}/etc/like.yaml"        || die "副本里 like-rpc 的 ListenOn 没改成 ${GO_RPC_PORT}（sed 没命中？）"
grep -q "^    Host: \"127.0.0.1:${GO_REDIS_PORT}\"$" "${WORK}/etc/like.yaml"     || die "副本里 like-rpc 的 Redis Host 没改（sed 没命中？）"
grep -q "127.0.0.1:${KAFKA_PORT}"                "${WORK}/etc/like.yaml"        || die "副本里 like-rpc 的 Kafka 没改（sed 没命中？）"
grep -q "127.0.0.1:${ETCD_PORT}"                 "${WORK}/etc/like.yaml"        || die "副本里 like-rpc 的 etcd 没改（sed 没命中？）"
grep -q "127.0.0.1:${ETCD_PORT}"                 "${WORK}/etc/likecenter.yaml"  || die "副本里 likecenter 的 etcd 没改（sed 没命中？）"
grep -q "127.0.0.1:${JAEGER_PORT}"               "${WORK}/etc/likecenter.yaml"  || die "副本里 likecenter 的 jaeger 没改（sed 没命中？）"
grep -q "etcd:2379"                              "${WORK}/etc/like.yaml"        && die "副本里还残留 etcd:2379（sed 没命中？）"
grep -q "etcd:2379"                              "${WORK}/etc/likecenter.yaml"  && die "副本里还残留 etcd:2379（sed 没命中？）"
grep -q "redis:6379"                             "${WORK}/etc/like.yaml"        && die "副本里还残留 redis:6379（sed 没命中？）"
grep -q "kafka:9092"                             "${WORK}/etc/like.yaml"        && die "副本里还残留 kafka:9092（sed 没命中？）"
# 🔴 **真源的 `${POSTGRES_*}` 占位符 go-zero 自己不会展开**
#    （`core/conf/config.go:60-66`：`Load` 只在 `conf.WithEnv()` 时才 `os.ExpandEnv`；
#    而真源 `service/like/rpc/like.go:28` 调的是 `conf.MustLoad(file, &c)` **没带** `WithEnv()`）
#    ⇒ 占位符必须由**部署侧**预替换 ⇒ 本探针在**配置副本**里替换（真源一个字都不改）。
grep -q 'POSTGRES_USER}'                        "${WORK}/etc/like.yaml"        && die "副本里还残留 \${POSTGRES_USER}（占位符没替换？）"
grep -q "postgres://admin:sea@127.0.0.1:${GO_PG_PORT}/first_db" "${WORK}/etc/like.yaml" \
  || die "副本里的 Postgres DSN 没替换成实际值（sed 没命中？）"
# 🔴 **真源冻结断言**：真源仓 service/like 必须干净 ⇒ 本探针一个字都没改真源。
( cd "${GO_REPO}" && git status --porcelain -- service/like | grep -q . ) \
  && die "真源仓 service/like 有改动 ⇒ 本探针**不得**改真源"
say "   ✓ 配置副本已生成（真源 service/like 仍然干净）"

# ══════════════════════════ 4. 起 Go 侧 rpc + api ═══════════════════════════════
step "4/11 起 Go 侧 like-rpc（注册 etcd ${ETCD_PORT}）"
docker exec Sea-etcd etcdctl --endpoints="${ETCD_CTL_ENDPOINT}" del "like.rpc" --prefix >/dev/null 2>&1
# ⚠️ Go 的 Postgres DSN 用的是 **环境变量占位符**（真源 like.yaml 的
#    `postgres://${POSTGRES_USER}:${POSTGRES_PASSWORD}@${POSTGRES_ADDR}:${POSTGRES_PORT}/${POSTGRES_DB}`）
#    ⇒ 只需导环境变量，**不用改 yaml**。
# 🔴🔴 **必须用 `exec`**（缺陷 2 · 2026-10-29 实测）：`( cd X && cmd ) &` ⇒ `$!` 是
#    **子 shell** 的 PID —— bash 对**异步**子 shell 会**再 fork 一次**去跑命令
#    ⇒ 真正的服务进程是它的**儿子** ⇒ `kill $!` 只杀得掉子 shell
#    ⇒ **Go 的 api/rpc 变孤儿**、继续占着端口 ⇒ 污染下一轮。
#    （实测：`$!`=71845（bash），真服务=71846；`kill 71845` 之后 71846 仍活着。）
#    ⇒ `exec` 把子 shell **替换**成服务进程 ⇒ `$!` 就是服务 PID ⇒ kill 才有效。
#    ⇒ 环境变量用 `exec env VAR=… cmd` 传（`VAR=… exec cmd` 在 bash 里是
#      「特殊内置 + 赋值」⇒ 赋值留在**被替换掉**的 shell 里 ⇒ 不可靠）。
( cd "${WORK}" && exec env POSTGRES_USER=admin POSTGRES_PASSWORD=sea POSTGRES_ADDR=127.0.0.1 \
    POSTGRES_PORT="${GO_PG_PORT}" POSTGRES_DB=first_db \
    "${WORK}/rpc" -f "${WORK}/etc/like.yaml" ) >"${WORK}/go-rpc.log" 2>&1 &
GO_PIDS+=($!)
# ⚠️ go-zero 的 etcd 键是**前缀族** `<key>/<leaseID>` ⇒ 必须按前缀查。
for _ in $(seq 1 40); do etcd_has_key like.rpc && break; sleep 1; done
etcd_has_key like.rpc || die "Go like-rpc 40s 内没注册进 etcd（见 ${WORK}/go-rpc.log）"
# ⚠️ go-zero **先注册 etcd 再 bind 端口** ⇒ 「etcd 有键」≠「端口已监听」⇒ 必须再等一次端口。
for _ in $(seq 1 20); do nc -z -G 1 127.0.0.1 "${GO_RPC_PORT}" && break; sleep 1; done
nc -z -G 2 127.0.0.1 "${GO_RPC_PORT}" || die "Go like-rpc 注册了但端口没监听（见 ${WORK}/go-rpc.log）"
say "   ✓ 已注册 like.rpc → $(etcd_get_value like.rpc)（:${GO_RPC_PORT} 已监听）"

step "5/11 起 Go 侧 like-api（:${GO_API_PORT}）"
# ⚠️ 同样**必须 `exec`**（理由同上：否则 `$!` 是子 shell ⇒ Go api 变孤儿）
( cd "${WORK}" && exec "${WORK}/api" -f "${WORK}/etc/likecenter.yaml" ) >"${WORK}/go-api.log" 2>&1 &
GO_PIDS+=($!)
for _ in $(seq 1 40); do nc -z -G 1 127.0.0.1 "${GO_API_PORT}" && break; sleep 1; done
nc -z -G 2 127.0.0.1 "${GO_API_PORT}" || die "Go like-api 没起来（见 ${WORK}/go-api.log）"
say "   ✓ Go 侧已监听 :${GO_API_PORT}"

# ══════════════════════════ 5. 起 RAG 侧 ═══════════════════════════════════════
step "6/11 起 RAG 侧 like-rpc（注册 Nacos ${NACOS_PORT}）+ like-api（:${RAG_API_PORT}）"
( cd "${RAG_ROOT}" && mvn -o -q -pl service/like/like-rpc,service/like/like-api -am package -DskipTests ) \
  || die "RAG 侧构建失败"
RAG_RPC_JAR="${RAG_ROOT}/service/like/like-rpc/target/like-rpc-0.0.1-SNAPSHOT.jar"
RAG_API_JAR="${RAG_ROOT}/service/like/like-api/target/like-api-0.0.1-SNAPSHOT.jar"
[ -f "${RAG_RPC_JAR}" ] && [ -f "${RAG_API_JAR}" ] || die "RAG 侧 jar 没产出"
nacos_deregister_all like-rpc
# ⚠️ `SPRING_KAFKA_LISTENER_AUTO_STARTUP=false`：**RAG 的 like-rpc 带一个
#    `@KafkaListener`（消费 `like_promotion_topic`），而 Go 的 like-rpc 是**纯生产者**
#    ⇒ 共用宿主 Kafka 时，RAG 会把 **Go 侧推的消息**消费进 RAG 的库 ⇒ 两侧状态串味。
#    ⇒ 关掉 RAG 的 listener ⇒ 两边只各自生产，不互相消费。
#    （被排除的只是「Kafka 消费」这一段，**不在本探针的比较面内**。）
POSTGRES_HOST=127.0.0.1 POSTGRES_PORT="${RAG_PG_PORT}" POSTGRES_DB=first_db \
POSTGRES_USER=admin POSTGRES_PASSWORD=sea \
REDIS_HOST=127.0.0.1 REDIS_PORT="${RAG_REDIS_PORT}" \
KAFKA_BROKERS="127.0.0.1:${KAFKA_PORT}" NACOS_ADDR="127.0.0.1:${NACOS_PORT}" \
GRPC_SERVER_PORT="${RAG_RPC_PORT}" \
OTEL_EXPORTER_OTLP_ENDPOINT="127.0.0.1:${JAEGER_PORT}" \
  java -jar "${RAG_RPC_JAR}" --spring.kafka.listener.auto-startup=false >"${WORK}/rag-rpc.log" 2>&1 &
RAG_PIDS+=($!)
for _ in $(seq 1 90); do
  curl -s "http://127.0.0.1:${NACOS_PORT}/nacos/v1/ns/service/list?pageNo=1&pageSize=50&namespaceId=public" 2>/dev/null | grep -q 'like-rpc' && break
  sleep 1
done
curl -s "http://127.0.0.1:${NACOS_PORT}/nacos/v1/ns/service/list?pageNo=1&pageSize=50&namespaceId=public" 2>/dev/null | grep -q 'like-rpc' \
  || die "RAG like-rpc 90s 内没注册进 Nacos（见 ${WORK}/rag-rpc.log）"
say "   ✓ RAG like-rpc 已注册进 Nacos"
# 🔴🔴 **必须传 `USER_JWT_SECRET`**（缺陷 1 · 2026-10-29 · P0 #8）：
#   RAG 侧 `service/like/like-api/src/main/resources/application.yml:85` 写的是
#   `access-secret: ${USER_JWT_SECRET}`（**故意没有默认值**）⇒ 占位符解析不出来时，
#   Spring 的 `@ConfigurationProperties` 绑定器**不报错**、而是把**占位符原文**当值绑上去
#   ⇒ ⇒ 守卫 `LikeApiProperties#verifyAccessSecret()`（@PostConstruct）显式拒
#   ⇒ ⇒ **进程起不来**（`IllegalStateException`）⇒ 探针 EXIT=2。
#   ⇒ 本探针的 token 由 `lib/make_jwt.py --secret "${JWT_SECRET}"` 签发
#      ⇒ **签发与验签必须同密钥** ⇒ `USER_JWT_SECRET` = `JWT_SECRET`。
#   （Go 侧对应：声明点 `service/like/api/etc/likecenter.yaml:6`，
#     消费点 `service/like/api/internal/handler/routes.go:49`
#     `rest.WithJwt(serverCtx.Config.UserAuth.AccessSecret)`。）
SERVER_PORT="${RAG_API_PORT}" NACOS_ADDR="127.0.0.1:${NACOS_PORT}" \
USER_JWT_SECRET="${USER_JWT_SECRET}" \
  java -jar "${RAG_API_JAR}" >"${WORK}/rag-api.log" 2>&1 &
RAG_PIDS+=($!)
for _ in $(seq 1 90); do nc -z -G 1 127.0.0.1 "${RAG_API_PORT}" && break; sleep 1; done
nc -z -G 2 127.0.0.1 "${RAG_API_PORT}" || die "RAG like-api 没起来（见 ${WORK}/rag-api.log）"
say "   ✓ RAG 侧已监听 :${RAG_API_PORT}"

# ══════════════════════════ 6. 两侧播种（**绝不用空库当证据**）══════════════════
step "7/11 两侧播种同一批数据（Redis 键 + PG 行）"
TARGET_ID="9007199254740994"
TARGET_TYPE="article"
SEED_USER="1001"
AUTHOR_ID="1002"
TS="1735787045000"

seed_redis() {   # $1 = 容器名
  local c="$1"
  docker exec "${c}" redis-cli HSET "like_count:${TARGET_TYPE}"     "${TARGET_ID}" 2      >/dev/null || die "seed HSET like_count 失败"
  docker exec "${c}" redis-cli HSET "dislike_count:${TARGET_TYPE}"  "${TARGET_ID}" 1      >/dev/null || die "seed HSET dislike_count 失败"
  docker exec "${c}" redis-cli HSET "like_state:${TARGET_TYPE}:${TARGET_ID}" "${SEED_USER}" 1 >/dev/null || die "seed HSET like_state 失败"
  docker exec "${c}" redis-cli ZADD "target_liker_list:${TARGET_TYPE}:${TARGET_ID}" "${TS}" "${SEED_USER}" >/dev/null || die "seed ZADD target_liker_list 失败"
  docker exec "${c}" redis-cli ZADD "user_like_list:${SEED_USER}:${TARGET_TYPE}"      "${TS}" "${TARGET_ID}" >/dev/null || die "seed ZADD user_like_list 失败"
  docker exec "${c}" redis-cli SET  "user_total_like:${SEED_USER}" 5 >/dev/null || die "seed SET user_total_like 失败"
}
seed_redis "${GO_REDIS_CONTAINER}"
seed_redis "${RAG_REDIS_CONTAINER}"

SEED_SQL="INSERT INTO like_record (user_id,target_type,target_id,author_id,state,last_operation_id,created_at,updated_at) VALUES (${SEED_USER},'${TARGET_TYPE}','${TARGET_ID}',${AUTHOR_ID},1,0,'2026-01-02 03:04:05+00','2026-01-02 03:04:05+00');"
for c in "${GO_PG_CONTAINER}" "${RAG_PG_CONTAINER}"; do
  docker exec "${c}" psql -U admin -d first_db -v ON_ERROR_STOP=1 -c "${SEED_SQL}" >/dev/null \
    || die "播种 like_record 失败：${c}"
done
say "   ✓ 两侧同一批数据（Redis 6 个键 + PG 1 行）"

# ══════════════════════════ 7. 阶段 A：HTTP 业务面逐字节对拍 ═══════════════════
step "8/11 阶段 A · HTTP 业务面（Go vs RAG vs expected.txt ⇒ 逐字节）"
EXPECTED="${HERE}/expected.txt"
[ -f "${EXPECTED}" ] || die "缺少冻结的期望值：${EXPECTED}"

TOKEN_1001="$(python3 "${HERE}/lib/make_jwt.py" --secret "${JWT_SECRET}" --user-id "${SEED_USER}")" \
  || die "签发 JWT 失败"
TOKEN_2002="$(python3 "${HERE}/lib/make_jwt.py" --secret "${JWT_SECRET}" --user-id 2002)" \
  || die "签发 JWT 失败"

# ── 就绪闸门：等两侧的**下游 rpc 真的可用**（不是 1015、不是空）────────────────
# ⚠️ 「端口已监听」≠「下游 rpc 已就绪」⇒ 客户端可能拿到一个还没准备好的实例
#    ⇒ 首请求报 1015「No servers found for like-rpc」⇒ 那**不是**「两侧有漂移」
#    ⇒ 必须先等到「首个非 1015 响应」再判对错（2026-10-29 实测踩过）。
wait_ready() {   # $1 = 端口  $2 = 标签
  local p="$1" tag="$2" body
  for _ in $(seq 1 30); do
    body=$(curl -s -m 5 -X POST -H 'Content-Type: application/json' \
      -H "Authorization: Bearer ${TOKEN_1001}" \
      --data-binary "{\"target_type\":\"${TARGET_TYPE}\",\"target_ids\":[\"${TARGET_ID}\"]}" \
      "http://127.0.0.1:${p}/like/v1/getlikecount" 2>/dev/null)
    case "${body}" in
      ''|*'"code":1015'*) sleep 2 ;;
      *) say "   ✓ ${tag} 就绪（首个非 1015 响应已到）"; return 0 ;;
    esac
  done
  say "   ❌ ${tag} 轮询 30 次仍拿不到正常响应 ⇒ 不进请求矩阵"
  return 1
}

FAILED=0
# run_case <name> <path> <body> <token>
# ⚠️ **每个侧只发一次请求**（`curl -w '\n%{http_code}'` 一次拿到 body + 状态码）——
#    `likeaction` 是**写路径** ⇒ 发两次会改两次计数 ⇒ 两次响应必然不同 ⇒ 假红。
run_case() {
  local name="$1" path="$2" body="$3" token="$4"
  local g r gs rs gh rh eh es graw rraw
  local gargs=(-s -m 20 -w '\n%{http_code}' -X POST -H 'Content-Type: application/json')
  local rargs=(-s -m 20 -w '\n%{http_code}' -X POST -H 'Content-Type: application/json')
  if [ -n "${token}" ]; then
    gargs+=(-H "Authorization: Bearer ${token}")
    rargs+=(-H "Authorization: Bearer ${token}")
  fi
  graw=$(curl "${gargs[@]}" --data-binary "${body}" "http://127.0.0.1:${GO_API_PORT}${path}")
  rraw=$(curl "${rargs[@]}" --data-binary "${body}" "http://127.0.0.1:${RAG_API_PORT}${path}")
  gs="${graw##*$'\n'}"; g="${graw%$'\n'*}"
  rs="${rraw##*$'\n'}"; r="${rraw%$'\n'*}"
  gh=$(printf '%s' "$g" | xxd -p | tr -d '\n')
  rh=$(printf '%s' "$r" | xxd -p | tr -d '\n')
  es=$(awk -F'|' -v n="CASE|${name}" '$1"|"$2==n {print $3}' "${EXPECTED}")
  eh=$(awk -F'|' -v n="CASE|${name}" '$1"|"$2==n {print $4}' "${EXPECTED}")
  # ⚠️ **用状态码字段当「有没有这一条」的标记**：hex 字段**允许为空**
  #    （`no_token` 的 body 本来就是空字节 ⇒ hex = ""）。
  #    若拿 hex 当标记 ⇒ 空 body 的用例会被误判成「expected.txt 里没有这一条」。
  [ -n "${es}" ] || { say "❌ ${name}：expected.txt 里没有这一条"; FAILED=1; return; }
  if [ "${g}" = "${r}" ] && [ "${gh}" = "${eh}" ] && [ "${gs}" = "${es}" ]; then
    say "   ✅ ${name}  Go == RAG == expected.txt（逐字节 · HTTP ${gs}）"
  else
    FAILED=1
    say "   ❌ ${name}"
    [ "${gs}" != "${es}" ] && say "      HTTP 状态：Go=${gs} RAG=${rs} expected=${es}"
    [ "${gh}" != "${eh}" ] && { say "      Go != expected"; say "      Go : ${g}"; }
    [ "${rh}" != "${eh}" ] && { say "      RAG != expected"; say "      RAG: ${r}"; }
  fi
}

# ⚠️ `likeaction` 的 `author_id` **取 0**：
#    Go 的 `likeactionlogic.go:151-162` 在 `AuthorId > 0 && UserId != AuthorId` 时会
#    调 `notifyArticleLiked` ⇒ 走 **message.rpc**（etcd 里没有这个服务）
#    ⇒ go-zero 的 resolver 会等到 client timeout 才返回 ⇒ 3s 的 REST 预算被吃掉
#    ⇒ **两侧的耗时不可比**（Go 等 2s、RAG 立刻失败）。
#    ⇒ 本用例取 `author_id="0"` ⇒ 两侧都**不走** notify 分支 ⇒ 只比较可比的字节。
#    （代价：`notifyArticleLiked` 这一段**不在**本探针的比较面内 ⇒ 见 README 的「覆盖边界」。）
wait_ready "${GO_API_PORT}"  "Go  侧" || die "Go 侧没就绪（见上面的日志）"
wait_ready "${RAG_API_PORT}" "RAG 侧" || die "RAG 侧没就绪（见上面的日志）"

run_case "getlikecount"         "/like/v1/getlikecount"         "{\"target_type\":\"${TARGET_TYPE}\",\"target_ids\":[\"${TARGET_ID}\"]}" "${TOKEN_1001}"
run_case "getlikestate"         "/like/v1/getlikestate"         "{\"target_type\":\"${TARGET_TYPE}\",\"target_ids\":[\"${TARGET_ID}\"]}" "${TOKEN_1001}"
run_case "gettargetlikerlist"   "/like/v1/gettargetlikerlist"   "{\"target_type\":\"${TARGET_TYPE}\",\"target_id\":\"${TARGET_ID}\",\"cursor\":0,\"page_size\":10}" "${TOKEN_1001}"
run_case "getuserlikelist"      "/like/v1/getuserlikelist"      "{\"target_type\":\"${TARGET_TYPE}\",\"cursor\":0,\"page_size\":10}" "${TOKEN_1001}"
run_case "getusertotallike"     "/like/v1/getusertotallike"     "{}" "${TOKEN_1001}"
run_case "likeaction"           "/like/v1/likeaction"           "{\"target_type\":\"${TARGET_TYPE}\",\"target_id\":\"${TARGET_ID}\",\"action_type\":1,\"author_id\":\"0\"}" "${TOKEN_2002}"
run_case "no_token"             "/like/v1/getlikecount"         "{\"target_type\":\"${TARGET_TYPE}\",\"target_ids\":[\"${TARGET_ID}\"]}" ""
run_case "missing_required"     "/like/v1/getlikecount"         "{\"target_type\":\"${TARGET_TYPE}\"}" "${TOKEN_1001}"

if [ "${FAILED}" != "0" ]; then
  say ""
  say "❌ 阶段 A（HTTP 业务面）存在字节差异"
  dump_logs
  exit 1
fi
say "   ✓ 阶段 A 全绿"

# ══════════════════════════ 7b. 阶段 C：E60 双失败错误传播 ═════════════════════
step "9/11 阶段 C · E60 · Redis 与 DB **同时**失败 ⇒ 业务错误码必须两侧都是 5011"

# ⚠️ **为什么必须注入「Redis 与 DB 同时失败」**（Go
#    `service/like/rpc/internal/logic/likeactionlogic.go:345-364` 的 `currentUserLikeTotal`）：
#    <pre>
#    total, err := ZcardCtx(...)            // err != nil ⇒ Redis 失败
#    if err != nil { total = 0 }
#    dbTotal, dbErr := GetUserActiveLikeCount(...)
#    if dbErr != nil {
#        if err == nil { return int64(total), nil }   // 只有 Redis 成功、DB 失败 ⇒ 不算失败
#        return 0, dbErr                              // **两者都失败** ⇒ error 必须传出去
#    }
#    </pre>
# ⇒ **单边失败都走不到 E60 那条分支**（Redis 单独失败 ⇒ dbErr==nil ⇒ 返回 (max(dbTotal,0), nil)；
#   DB 单独失败 ⇒ err==nil ⇒ 返回 (total, nil)）⇒ **只注入一个故障 ⇒ 探针测不到 E60**。
#
# ⚠️ **怎么让 ZCARD 失败、但 Lua 的 ZADD 照常工作**：
#    `CONFIG SET rename-command` 在 Redis 7 上是 **ERR syntax error**（实测）⇒ 不可用；
#    把 key 换成非 ZSET 类型也不行 —— Lua 的 ZADD 会先失败（那会变成 5012，不是 E60）。
#    ⇒ 用 **ACL**：`ACL SETUSER default -zcard` ⇒ ZCARD 返回
#    "NOPERM User default has no permissions to run the 'zcard' command" ⇒ **确定性失败**；
#    而脚本里的 ZADD / HGET / HINCRBY / INCR / ZREM / GET **不受影响**（Redis 7 实测）
#    ⇒ 点赞主流程照走，只把「读该用户的赞列表」这条打掉 ⇒ 正好落在 E60 的那条分支上。
#
# ⚠️ **怎么让 DB 失败**：把 `like_record` 表**删掉**（两侧同删）。
#    · Go：`model.InitDB` 的 AutoMigrate 只在**启动时**跑 ⇒ 启动后删表 ⇒ 运行期查询必失败；
#    · RAG：Flyway 也只在启动时建表 ⇒ 同理。
#    ⇒ 而 likeaction 的 RPC 路径**不写** PG（写入在 MQ 消费侧）⇒ 删表只影响那两处「读」
#    （`currentArticleLikeTotal` / `currentUserLikeTotal`）⇒ 两侧都走 Go 的同款降级分支。
for side in go rag; do
  if [ "${side}" = "go" ]; then rc="${GO_REDIS_CONTAINER}"; pc="${GO_PG_CONTAINER}"; else rc="${RAG_REDIS_CONTAINER}"; pc="${RAG_PG_CONTAINER}"; fi
  docker exec "${rc}" redis-cli ACL SETUSER default -zcard >/dev/null \
    || die "注入故障失败（${side} 侧 Redis ACL 禁用 ZCARD）"
  docker exec "${pc}" psql -U admin -d first_db -v ON_ERROR_STOP=1 -c "DROP TABLE IF EXISTS like_record;" >/dev/null \
    || die "注入故障失败（${side} 侧 PG 删除 like_record）"
done
say "   ✓ 两侧已注入故障：ZCARD 被 ACL 禁用 ＋ like_record 表被删除"

E60_TOKEN="$(python3 "${HERE}/lib/make_jwt.py" --secret "${JWT_SECRET}" --user-id 3003)" || die "签发 JWT 失败"
# 记下 RAG 日志的行数 ⇒ 后面只看「本次调用之后」新增的行（阶段 A/B 不会产生 business_error，
# 但用行号切一刀更稳 ⇒ 不会把别的阶段的日志算进证据里）。
RAG_LOG_MARK=$(wc -l < "${WORK}/rag-rpc.log" 2>/dev/null | tr -d '[:space:]')
RAG_LOG_MARK="${RAG_LOG_MARK:-0}"
E60_BODY="{\"target_type\":\"${TARGET_TYPE}\",\"target_id\":\"${TARGET_ID}\",\"action_type\":1,\"author_id\":\"0\"}"
E60_G="$(curl -s -m 20 -w '\n%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer ${E60_TOKEN}" --data-binary "${E60_BODY}" "http://127.0.0.1:${GO_API_PORT}/like/v1/likeaction")"
E60_R="$(curl -s -m 20 -w '\n%{http_code}' -X POST -H 'Content-Type: application/json' \
  -H "Authorization: Bearer ${E60_TOKEN}" --data-binary "${E60_BODY}" "http://127.0.0.1:${RAG_API_PORT}/like/v1/likeaction")"
E60_GS="${E60_G##*$'\n'}"; E60_G="${E60_G%$'\n'*}"
E60_RS="${E60_R##*$'\n'}"; E60_R="${E60_R%$'\n'*}"
E60_EH="$(awk -F'|' -v n="CASE|likeaction_double_failure" '$1"|"$2==n {print $4}' "${EXPECTED}")"
E60_ES="$(awk -F'|' -v n="CASE|likeaction_double_failure" '$1"|"$2==n {print $3}' "${EXPECTED}")"
if [ -z "${E60_ES}" ]; then
  say "❌ expected.txt 里没有 likeaction_double_failure 这一条"
  FAILED=1
elif [ "${E60_G}" = "${E60_R}" ] && [ "${E60_GS}" = "${E60_ES}" ] \
     && [ "$(printf '%s' "${E60_G}" | xxd -p | tr -d '\n')" = "${E60_EH}" ]; then
  say "   ✅ 响应字节：Go == RAG == expected.txt（HTTP ${E60_GS}）⇒ ${E60_G}"
else
  FAILED=1
  say "   ❌ 响应字节不一致"
  say "      Go (${E60_GS}) : ${E60_G}"
  say "      RAG(${E60_RS}): ${E60_R}"
fi

# ── 业务错误：两侧都必须走到「记业务错误」这条分支 ──────────────────────────
# ⚠️ **两侧的可观察形态不同**（这不是探针的偷懒，是两侧日志形态的事实）：
#   · Go：go-zero 的文件日志是 **JSON 行** ⇒ 业务码在 `"error_code":5011` 里 ⇒ **码值可直接读**；
#   · RAG：SLF4J 的 key-value（`Logger.java:277` `addKeyValue("error_code", code)`）
#     **默认 pattern 不打印 key-value** ⇒ stdout 里只有 `business_error` 四个字，
#     **码值 5011 不在输出里** ⇒ 只能观测到「走了记业务错误这条分支」。
#     ⇒ 码值本身由**进程内**守门 `LikeUserLikeTotalDoubleFailureGateTest`
#        （Logback ListAppender 抓真实日志行 ⇒ 断言 error_code=5011）钉住。
#     ⇒ ⚠️ 「key-value 不进默认 pattern」是**十个组件共性**（全仓没有任何 logback 配置）
#        ⇒ 属跨域可观测性漂移 ⇒ **不在本域单独修**（单独改会造成本域与其它九域不一致）。
#   · 而 `LikeTaskProgressPublisher#publish` 是 like-rpc 里**唯一**的 `logBusinessErr` 调用点
#     ⇒ 「出现 business_error」**唯一地**说明 E60 那条分支被走到了 ⇒ 判据不含糊。
#
# ⚠️ Go 的文件日志是**异步落盘** ⇒ 要轮询等它写出来（2026-10-29 实测：立刻 grep 会空）。
GO_5011=""
for _ in $(seq 1 20); do
  GO_5011=$(grep -rhoE '"error_code"[[:space:]]*:[[:space:]]*5011' "${WORK}/log" 2>/dev/null | head -1)
  [ -n "${GO_5011}" ] && break
  sleep 1
done
RAG_BIZ=$(tail -n "+$((RAG_LOG_MARK+1))" "${WORK}/rag-rpc.log" 2>/dev/null | grep -c 'business_error' | tr -d '[:space:]')
RAG_BIZ="${RAG_BIZ:-0}"
if [ -n "${GO_5011}" ] && [ "${RAG_BIZ}" -ge 1 ]; then
  say "   ✅ 双失败业务错误：Go 记了 5011（ErrorRedisSelect）${GO_5011} · RAG 记了 business_error × ${RAG_BIZ}"
else
  FAILED=1
  say "   ❌ 双失败业务错误没在两侧都出现（Go='${GO_5011}' · RAG business_error 行数=${RAG_BIZ}）"
  say "      ⇒ Go 的 likeactionlogic.go:313-320 与 RAG 的 LikeTaskProgressPublisher#publish 必须都走「记业务错误」分支"
fi

if [ "${FAILED}" != "0" ]; then
  say ""
  say "❌ 阶段 C（E60 双失败）存在差异"
  dump_logs
  exit 1
fi
say "   ✓ 阶段 C 全绿"

# ══════════════════════════ 8. 阶段 B：事实投递链路（DRIFT-LIKE-003-A）══════════
step "10/11 阶段 B · 事实投递链路（003-A）· 两侧投给 DC 的字节"

# ⚠️ **两侧各用一个假 DC 输出目录**（而不是共用一个、再按文件名序号猜哪边是谁）：
#    假 DC 会把**收到的每一份**事实都落一个文件 ⇒ 共用一个目录时，
#    序号取决于「谁先投」⇒ 猜错就变成「拿 Go 的字节比 RAG 的字节」的假漂移。
#    ⇒ 分目录 ⇒ 再按 **event_id** 定位我们那条事实 ⇒ 与顺序无关。
start_dc() {   # $1 = 输出目录
  mkdir -p "$1"
  python3 "${HERE}/lib/fake_dc.py" --port "${DC_PORT}" --out-dir "$1" >>"${WORK}/fake-dc.log" 2>&1 &
  DC_PIDS+=($!)
  for _ in $(seq 1 20); do host_port_open "${DC_PORT}" && return 0; sleep 1; done
  return 1
}
stop_dc() {
  local p
  for p in ${DC_PIDS[@]+"${DC_PIDS[@]}"}; do kill "$p" 2>/dev/null; done
  DC_PIDS=()
  sleep 1
}
# 在目录里找出「装着我们那条 event_id 的事实」的那个文件
find_fact_file() {   # $1 = 目录  $2 = event_id ⇒ 打到 stdout（找不到 ⇒ 空）
  local dir="$1" event_id="$2" f
  for f in "${dir}"/received-*.bin; do
    [ -f "${f}" ] || continue
    if grep -q "${event_id}" "${f}" 2>/dev/null; then printf '%s' "${f}"; return 0; fi
  done
  return 1
}

python3 "${HERE}/lib/seed_fact.py" --sql-out "${WORK}/fact-seed.sql" --envelope-out "${WORK}/fact-envelope.json" \
  || die "生成事实播种数据失败"
FACT_EVENT_ID="rtw.like.9007199254740993001"
# ⚠️ 两侧播的是**同一条**事实（同样的 event_id / 信封）⇒ 投给 DC 的字节必须逐字节相同。
for c in "${GO_PG_CONTAINER}" "${RAG_PG_CONTAINER}"; do
  docker exec -i "${c}" psql -U admin -d first_db -v ON_ERROR_STOP=1 < "${WORK}/fact-seed.sql" >/dev/null \
    || die "播种事实 outbox 失败：${c}"
done
say "   ✓ 两侧播了同一条冻结事实（event_id=${FACT_EVENT_ID}）"

# --- Go 侧：跑**真源真正在用的** fact-dispatch 独立进程（-once ⇒ 只投一条）---
# ⚠️ `RTW_SERVICE_VERSION` 必须是 **40 位小写十六进制**（真源
#    `service/common/communityfact/logging.go:19` `processVersion = ^[0-9a-f]{40}$`
#    ⇒ `:30` 的 `meta.Validate()` 硬要求）⇒ 写 "1.0.0" 会让进程**秒退**（exit 1）
#    ⇒ 失败信息只有 "like fact dispatcher stopped · DELIVERY_FAILED"，**看不出原因** ⇒ 踩过。
#    这里取真源仓的 HEAD sha（40 hex）⇒ 与真源同源。
RTW_SERVICE_VERSION="$( cd "${GO_REPO}" && git rev-parse HEAD 2>/dev/null || echo 0000000000000000000000000000000000000000 )"
DC_GO_DIR="${WORK}/dc-go"
start_dc "${DC_GO_DIR}" || die "假 DC（Go 侧）没起来"
( cd "${WORK}" && LIKE_DATABASE_URL="postgres://admin:sea@127.0.0.1:${GO_PG_PORT}/first_db?sslmode=disable" \
    DC_PLATFORM_EVENT_URL="http://127.0.0.1:${DC_PORT}/v1/events" \
    DC_PLATFORM_SERVICE_TOKEN="${DC_TOKEN}" \
    RTW_ENVIRONMENT=test RTW_SERVICE_VERSION="${RTW_SERVICE_VERSION}" RTW_INSTANCE_ID=r2like-probe \
    "${WORK}/fact-dispatch" -once >"${WORK}/go-fact-dispatch.log" 2>&1 )
GO_FACT_RC=$?
stop_dc
[ "${GO_FACT_RC}" = "0" ] || die "Go fact-dispatch 退出码 ${GO_FACT_RC}（见 ${WORK}/go-fact-dispatch.log）"
GO_DC_BIN="$(find_fact_file "${DC_GO_DIR}" "${FACT_EVENT_ID}")" \
  || die "假 DC 没收到 Go 侧的那条事实（${DC_GO_DIR} 里找不到 ${FACT_EVENT_ID}）"
say "   ✓ Go fact-dispatch 退出码 0（DC 收到 $(wc -c < "${GO_DC_BIN}" | tr -d ' ') 字节）"

# --- RAG 侧：起**第二个** like-rpc JVM，**开启**事实派发（once=false ⇒ 循环取件）---
# ⚠️ 先把**第一个** RAG like-rpc 停掉：它占着同一个 gRPC 端口（:18124）
#    ⇒ 第二个 JVM 会 BindException 起不来 ⇒ 派发永远不会发生（2026-10-29 实测踩过：
#    症状是「120s 内没把行置成 ACCEPTED」，而 rag-rpc2.log 里全是 bind 失败）。
if [ "${#RAG_PIDS[@]}" -gt 0 ]; then
  kill "${RAG_PIDS[0]}" 2>/dev/null
  for _ in $(seq 1 20); do
    nc -z -G 1 127.0.0.1 "${RAG_RPC_PORT}" || break
    sleep 1
  done
fi
nacos_deregister_all like-rpc
# ⚠️ 事实派发的属性前缀是 **`like`**（`LikeRpcProperties` 的 `@ConfigurationProperties(prefix = "like")`）
#    ⇒ 属性名 = `like.fact.dispatch-enabled` / `like.fact.dc-endpoint` / `like.fact.dc-token` / `like.fact.once`
#    ⇒ **用命令行参数传**（`--like.fact.…`）而不是 env var：Spring 的 env 变量松弛绑定会把
#    `dispatch-enabled` 映射成 `DISPATCHENABLED`（**没有下划线**）⇒ 写成
#    `RAG_LIKE_FACT_DISPATCH_ENABLED` 会**静默不生效**（前缀也不对）。
DC_RAG_DIR="${WORK}/dc-rag"
start_dc "${DC_RAG_DIR}" || die "假 DC（RAG 侧）没起来"
POSTGRES_HOST=127.0.0.1 POSTGRES_PORT="${RAG_PG_PORT}" POSTGRES_DB=first_db \
POSTGRES_USER=admin POSTGRES_PASSWORD=sea \
REDIS_HOST=127.0.0.1 REDIS_PORT="${RAG_REDIS_PORT}" \
KAFKA_BROKERS="127.0.0.1:${KAFKA_PORT}" NACOS_ADDR="127.0.0.1:${NACOS_PORT}" \
GRPC_SERVER_PORT="${RAG_RPC_PORT}" \
OTEL_EXPORTER_OTLP_ENDPOINT="127.0.0.1:${JAEGER_PORT}" \
  java -jar "${RAG_RPC_JAR}" \
    --spring.kafka.listener.auto-startup=false \
    --like.fact.dispatch-enabled=true \
    --like.fact.dc-endpoint="http://127.0.0.1:${DC_PORT}/v1/events" \
    --like.fact.dc-token="${DC_TOKEN}" \
    --like.fact.once=false \
    >"${WORK}/rag-rpc2.log" 2>&1 &
RAG_PIDS+=($!)
RAG_FACT_OK=0
for _ in $(seq 1 60); do
  RAG_STATUS=$(docker exec "${RAG_PG_CONTAINER}" psql -U admin -d first_db -tAc \
    "SELECT delivery_status FROM like_domain_fact_outbox WHERE event_id = '${FACT_EVENT_ID}';" 2>/dev/null | tr -d '[:space:]')
  if [ "${RAG_STATUS}" = "1" ]; then RAG_FACT_OK=1; break; fi
  sleep 2
done
stop_dc
[ "${RAG_FACT_OK}" = "1" ] || die "RAG 侧事实派发 120s 内没把行置成 ACCEPTED（见 ${WORK}/rag-rpc2.log）"
RAG_DC_BIN="$(find_fact_file "${DC_RAG_DIR}" "${FACT_EVENT_ID}")" \
  || die "假 DC 没收到 RAG 侧的那条事实（${DC_RAG_DIR} 里找不到 ${FACT_EVENT_ID}）"
say "   ✓ RAG 侧事实已投递并结算为 ACCEPTED（DC 收到 $(wc -c < "${RAG_DC_BIN}" | tr -d ' ') 字节）"

# --- 比对：① DC 收到的字节 ② 两侧库的终态 ---
if cmp -s "${GO_DC_BIN}" "${RAG_DC_BIN}"; then
  say "   ✅ DC 收到的字节：Go == RAG（$(wc -c < "${GO_DC_BIN}" | tr -d ' ') bytes · sha256 $(shasum -a 256 "${GO_DC_BIN}" | cut -d' ' -f1)）"
else
  FAILED=1
  say "   ❌ DC 收到的字节：Go != RAG"
  say "      Go : $(xxd -p "${GO_DC_BIN}" | tr -d '\n')"
  say "      RAG: $(xxd -p "${RAG_DC_BIN}" | tr -d '\n')"
fi

FACT_STATE_SQL="SELECT event_id,event_type,aggregate_id,fact_version,delivery_status,retry_count,technical_receipt_id,technical_input_hash,technical_offset FROM like_domain_fact_outbox WHERE event_id = '${FACT_EVENT_ID}';"
docker exec "${GO_PG_CONTAINER}"  psql -U admin -d first_db -tAc "${FACT_STATE_SQL}" > "${WORK}/go-fact-state.txt"  2>/dev/null
docker exec "${RAG_PG_CONTAINER}" psql -U admin -d first_db -tAc "${FACT_STATE_SQL}" > "${WORK}/rag-fact-state.txt" 2>/dev/null
if cmp -s "${WORK}/go-fact-state.txt" "${WORK}/rag-fact-state.txt"; then
  say "   ✅ outbox 终态（不含墙钟列）：Go == RAG ⇒ $(cat "${WORK}/go-fact-state.txt")"
else
  FAILED=1
  say "   ❌ outbox 终态：Go != RAG"
  say "      Go : $(cat "${WORK}/go-fact-state.txt")"
  say "      RAG: $(cat "${WORK}/rag-fact-state.txt")"
fi

if [ "${FAILED}" != "0" ]; then
  say ""
  say "❌ 阶段 B（事实投递链路）存在差异"
  dump_logs
  exit 1
fi
say "   ✓ 阶段 B 全绿"

# ══════════════════════════ 9. 收尾：服务**真正**停掉 + 端口零残留 ═══════════
# ⚠️ 收尾**在断言之前**做：因为「跑绿」的前提之一就是「没给下一轮留垃圾」。
#    ⇒ 先 teardown（kill → 等端口关 → 删容器 → 按签名兜底清），
#      再 assert_no_leftover（`lsof -nP -iTCP:<端口> -sTCP:LISTEN` 必须为空）。
step "11/11 收尾复核（服务真停 · 端口零残留）"
teardown
if ! assert_no_leftover; then
  say ""
  say "❌ 收尾后本探针的端口仍有残留 ⇒ 探针污染了宿主环境（下一轮会撞车）"
  exit 2
fi

say ""
say "✅ 阶段 A（HTTP 业务面）+ 阶段 B（事实投递链路）+ 阶段 C（E60 双失败）双侧逐字节一致"
exit 0
