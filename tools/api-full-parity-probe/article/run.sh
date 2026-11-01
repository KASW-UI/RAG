#!/usr/bin/env bash
#
# article 域 **HTTP 层**双侧活体对拍探针。
#
# 真源：Sea-RideTheWind_golang/service/article/{api,rpc}
#       （**只读**：Go 侧用真源真正在用的生成码/二进制，真源仓一个字节都不改）
# 被测：RAG/service/article/{article-api,article-rpc}
#
# 定位：补 `docs/PhaseGoal-judge.md` §九 **G-3**（article 域双侧活体 **0 条**）——
#   之前只有「两侧都起服务」这一步从没做过 ⇒ ⇒ 字段序 / 缺失字段 / 错误码这类
#   **只有真跑才看得见**的差异，一条都没被发现。
#   ⇒ 本探针让**两侧同时起服务**（Go 真源栈 + RAG 栈）⇒ 同一请求 ⇒ 比对**字节**。
#
# ⚠️ **真源冻结 ⇒ 只能改「副本」**：Go 侧的配置副本把真源 yaml 里写死的主机名
#   （`etcd:2379` / `postgres` / `kafka:9092` / `minio:9000` / `jaeger:4317`）改成宿主可达地址。
#   ⇒ 真源仓全程 `git status` 干净（脚本第 2 步会断言）。
#
# 用法：
#   bash tools/api-full-parity-probe/article/run.sh
#   GO_REPO=/path/to/Sea-RideTheWind_golang bash tools/api-full-parity-probe/article/run.sh
#
# 依赖：docker（宿主已在跑 Sea-etcd/kafka/minio/jaeger/nacos）· Go toolchain（≥1.25）·
#       JDK 21+ · mvn（离线 -o）· curl · xxd。
#
# 退出码：0 = 双侧逐字节一致；1 = 不一致；2 = 环境问题 / 真源漂移。
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAG_ROOT="$(cd "${HERE}/../../.." && pwd)"

# ── 「每轮独立」机制（工具面 · **不改产品代码**）────────────────────────────────
# ⚠️ 为什么要有它：并发跑两份时，Nacos public 命名空间 / etcd `article.rpc` 键 /
#    宿主端口 / `target/` 都是**共享面** ⇒ 两份互相污染 ⇒ 探针报 INTERNAL / 5001
#    ⇒ ⇒ 「跑红」被误读成「RAG 有缺陷」⇒ ⇒ 一类**假红**。
# ⇒ 四个隔离向量（Nacos 命名空间 · etcd 键前缀 · 端口租约 · 构建锁）的说明见
#    `probe-isolation.sh` 文件头（含 Go 真源 / 框架侧 `文件:行号` 依据）。
# ⇒ **默认（RUN_ID 为空）= 现有单跑行为逐字节不变** ⇒ 已冻结的 expected.txt 不受影响。
source "${HERE}/probe-isolation.sh"
PROBE_PG_BIAS=100   # run.sh 的 PG 阶梯基准比 run-success 低一段 ⇒ 同块号下两族不重叠
# ⚠️ **基准端口要先登记** ⇒ 租约才知道「这一块会占哪些端口」⇒ 才能避开危险端口
#    （2026-11-01 实测：偏移 20000 时 19092+20000 = 39092 = **`kafka` 容器的宿主发布口**
#      ⇒ 老的「占着就杀」会把 **Docker Desktop backend** 一起杀掉 ⇒ Docker 失联）。
PROBE_BASE_APP_PORTS="8889 18889 9002 9001 19092 9092"
PROBE_BASE_PG_PORTS="55570 55572 55574 55576 55578 55580 55571 55573 55575 55577 55579 55581"
probe_iso_init run || exit 2

# ── 宿主中间件端口（可用 env 覆盖；默认值 = 本机实测）────────────────────────
ETCD_PORT="${ETCD_PORT:-49319}"      # Sea-etcd    49319→2379（宿主映射口 ⇒ 给 Go 侧配置副本用）
# ⚠️ etcdctl 是 **在容器里跑**（`docker exec Sea-etcd etcdctl`）⇒ 端点必须是**容器内端口** 2379，
#    用宿主映射口 49319 会 connection refused（2026-10-29 实测踩过 ⇒ 静默等到超时才发现）。
ETCD_CTL_ENDPOINT="${ETCD_CTL_ENDPOINT:-http://127.0.0.1:2379}"
KAFKA_PORT="${KAFKA_PORT:-49992}"    # Sea-kafka   49992→9093
MINIO_PORT="${MINIO_PORT:-49100}"    # Sea-minio   49100→9000
JAEGER_PORT="${JAEGER_PORT:-49317}"  # Sea-jaeger  49317→4317
NACOS_PORT="${NACOS_PORT:-49848}"    # Sea-nacos   49848→8848
# ── 两侧端口（同机并行必须错开：Go api 8889 / RAG api 8889，Go rpc 9001 / RAG rpc 9001）──
# ⚠️ **端口偏移来自「端口租约」**（`probe-isolation.sh` 的 V3）⇒ 并发跑两份时**保证**不撞；
#    `RUN_ID` 为空时偏移 = 0 ⇒ 与改造前**逐字节一致**。
#    显式给了 env（`GO_API_PORT=…`）⇒ **不偏移**（显式值 = 调用方自负其责）。
GO_API_PORT="${GO_API_PORT:-$(( 8889 + PROBE_PORT_OFFSET ))}"
RAG_API_PORT="${RAG_API_PORT:-$(( 18889 + PROBE_PORT_OFFSET ))}"
GO_RPC_PORT="${GO_RPC_PORT:-$(( 9002 + PROBE_PORT_OFFSET ))}"   # 9001 留给 RAG（它的 grpc.server.port 写死）
# ⚠️ **Prometheus 是另一个独立监听者**（go-zero v1.10.2 `core/service/serviceconf.go:62`
#    `prometheus.StartAgent(sc.Prometheus)` ⇒ `core/prometheus/agent.go:39` 绑 `Host:Port`）
#    ⇒ 它与 `ListenOn` 是**两个**端口，**不能改成同一个**（2026-10-29 实测踩过：
#    把 Prometheus 也改成 9002 ⇒ 它先绑住 9002 ⇒ gRPC 的 net.Listen 撞车
#    ⇒ `zrpc/server.go:91` panic: address already in use ⇒ 探针假挂 40s 后才报）。
GO_PROM_PORT="${GO_PROM_PORT:-$(( 19092 + PROBE_PORT_OFFSET ))}"
RAG_RPC_PORT="${RAG_RPC_PORT:-$(( 9001 + PROBE_PORT_OFFSET ))}"
# 🔴 **RAG article-rpc 的「观测端口」（Actuator 载体）也必须每轮唯一**：
#   `service/article/article-rpc/src/main/resources/application.yml:166` `server.port: ${ARTICLE_RPC_MANAGEMENT_PORT:9092}`
#   ⇒ 原脚本**从未传**这个变量（端口写死 9001 时凑巧能跑）⇒ 并发两轮时**两轮都占 9092**
#   ⇒ 后一轮启动即 fatal：`Web server failed to start. Port 9092 was already in use.`
RAG_RPC_MGMT_PORT="${RAG_RPC_MGMT_PORT:-$(( 9092 + PROBE_PORT_OFFSET ))}"
# ── 两侧各自的 Postgres（Go 侧要真源的 admin/first_db；RAG 侧要它自己的 Flyway 账本）──
# ⚠️ **容器名带 $$**（每轮唯一）⇒ 别的轮/别的 lane 的 cleanup 绝不可能把本轮的库删掉
#    （2026-10-29 实测踩过：固定名 + 上一轮 cleanup 延迟执行 ⇒ 本轮容器被删 ⇒
#    宿主端口 55570 拒连 ⇒ Go rpc 连续刷「connection refused」40s 才报「没注册进 etcd」）。
# ⚠️ 但 `$$` 在**子 shell** 里不变 ⇒ 显式再拼 RUN_TAG 更稳（并发两轮必是不同 bash ⇒ 不同 $$，
#    但拼上 RUN_TAG 后容器名自带轮次标识，排查残留时一眼能认出来）。
GO_PG_CONTAINER="${GO_PG_CONTAINER:-g3-article-go-pg-${PROBE_RUN_TAG:+${PROBE_RUN_TAG}-}$$}"
GO_PG_PORT="${GO_PG_PORT:-}"
RAG_PG_CONTAINER="${RAG_PG_CONTAINER:-g3-article-rag-pg-${PROBE_RUN_TAG:+${PROBE_RUN_TAG}-}$$}"
RAG_PG_PORT="${RAG_PG_PORT:-}"
# ── 验签密钥（必须与签发方 user-api 一致；RAG 侧缺它 ⇒ 拒启动）─────────────────
JWT_SECRET="${JWT_SECRET:-ae0536f9-6450-4606-8e13-5a19ed505da0}"

# ⚠️ **目录名带探针标记**（`rag-article-parity.`）⇒ 端口预检能认出「哪些进程是探针自己的」
#    ⇒ 只踢探针进程，不误伤宿主上的其它服务（见 `probe-isolation.sh` 的 `probe_free_port`）。
WORK="$(mktemp -d "${TMPDIR:-/tmp}/rag-article-parity.XXXXXX")"
GO_PIDS=(); RAG_PIDS=()
say()  { printf '%s\n' "$*"; }
step() { printf '\n== %s ==\n' "$*"; }
# ── 失败时**自动打印两侧日志**（否则 exit 2 之后什么都查不到）────────────────────
dump_logs() {
  local f
  for f in go-rpc.log go-api.log rag-rpc.log rag-api.log; do
    [ -f "${WORK:-/nonexistent}/${f}" ] || continue
    printf '\n---- %s（末 30 行）----\n' "${f}" >&2
    tail -30 "${WORK}/${f}" | sed 's/^/   | /' >&2
  done
}
die()  { printf '!! %s\n' "$*" >&2; dump_logs; exit 2; }

# ── 注销 Nacos 里的**残留实例** ────────────────────────────────────────────────
# ⚠️ 上一轮探针被 kill 时，Nacos 里的**旧实例**还会存活一段时间（心跳超时前）
#    ⇒ 客户端可能选中**死实例** ⇒ 调用超时 ⇒ 两侧返回 1015「服务繁忙」
#    ⇒ **必须先注销残留**再起新的（2026-10-29 实测踩过）。
# ⚠️ **命名空间由 `probe-isolation.sh` 提供**（本轮专属 ⇒ 绝不碰别的轮次的实例）。
nacos_deregister_all() { probe_nacos_deregister_all "$@"; }

# 🔴 **就绪判据必须精确到「本轮的端口」**（`run-success.sh` 已踩过并修好）：
#   只查 `service/list` 里**有没有这个服务名** ⇒ **别的轮次/上一轮的残留实例**也会命中
#   ⇒ 脚本在 java **还在启动**（日志 0 字节）时就去查端口 ⇒ 误判「没监听」⇒ 假失败。
#   ⇒ 改成数「**注册端口 == 本轮 gRPC 端口**」的实例个数（且只在本轮命名空间里数）。
nacos_instance_count() { probe_nacos_instance_count "$@"; }

# ── 就绪闸门：等两侧的**列表端点**真的答得上（不是 1015、不是空）────────────────
# ⚠️ 「服务已监听」≠「下游 rpc 已就绪」⇒ 客户端可能拿到一个还没准备好的实例
#    ⇒ 首请求超时 ⇒ 1015 ⇒ 误判成「两侧有漂移」（2026-10-29 实测踩过）。
wait_ready() {   # $1 = 端口  $2 = 标签
  local p="$1" tag="$2" body
  for _ in $(seq 1 30); do
    body=$(curl -s -m 5 "http://127.0.0.1:${p}/v1/articles" 2>/dev/null)
    case "${body}" in
      ''|*'"code":1015'*) sleep 2 ;;
      *) say "   ✓ ${tag} 就绪（首个非 1015 响应已到）"; return 0 ;;
    esac
  done
  say "   ❌ ${tag} 轮询 30 次仍拿不到正常响应 ⇒ 不进请求矩阵"
  return 1
}

# ── etcd 访问（**容器内**端点；键 = `<key>/<leaseID>` 前缀族）────────────────────────
# ⚠️ go-zero v1.10.2 `core/discov/publisher.go:191-196`：
#      lease := resp.ID
#      if p.id > 0 { p.fullKey = makeEtcdKey(p.key, p.id) }
#      else        { p.fullKey = makeEtcdKey(p.key, int64(lease)) }
#    `core/discov/clients.go:39-41` `makeEtcdKey` = `fmt.Sprintf("%s%c%d", key, Delimiter, id)`
#    ⇒ **未显式配 `etcd.ID` 时，键 = `<key>/<leaseID>`**（实测 `article.rpc/7587898349098767861`）
#    ⇒ **必须按前缀查**（精确键查 ⇒ 永远查不到 ⇒ 假阴性「没注册进 etcd」）。
etcd_get_value() {   # $1 = 键前缀（如 article.rpc）⇒ 打印第一个匹配的值（不存在 ⇒ 空）
  docker exec Sea-etcd etcdctl --endpoints="${ETCD_CTL_ENDPOINT}" get "$1" --prefix --print-value-only 2>/dev/null | head -1
}
etcd_has_key() {     # $1 = 键前缀 ⇒ 命中返回 0
  [ -n "$(etcd_get_value "$1")" ]
}

# ── 端口预检：把**上一轮残留**的探针进程踢掉，否则 Go rpc 会 panic: address already in use ──
# ⚠️ **只踢「探针进程」**（见 `probe-isolation.sh` 的 `probe_free_port`）
#    ⇒ 绝不误杀宿主上的其它服务（实测：占着端口的可能是 **Docker Desktop backend** ⇒ 杀了 Docker 就没了）。
free_probe_ports() {
  local p
  for p in "${GO_API_PORT}" "${RAG_API_PORT}" "${GO_RPC_PORT}" "${RAG_RPC_PORT}" "${GO_PROM_PORT}" "${RAG_RPC_MGMT_PORT}"; do
    probe_free_port "${p}"
  done
  sleep 1
}

# ⚠️ 曾用「python 让 OS 挑空闲端口」⇒ **后台任务里 `bind(("0.0.0.0",0))` 报
#    `OSError: [Errno 49] Can't assign requested address`**（2026-10-29 实测）
#    ⇒ 改成**固定端口阶梯**（见 step 1 的 pick_pg_port）⇒ 不再依赖 python 挑端口。
host_port_open() { nc -z -G 2 127.0.0.1 "$1" >/dev/null 2>&1; }

cleanup() {
  local p
  # ⚠️ macOS 自带 bash 3.2：`set -u` 下展开**空数组**会报 `unbound variable`
  #    ⇒ 用 `${A[@]+"${A[@]}"}` 形式（2026-10-29 实测踩过）。
  for p in ${GO_PIDS[@]+"${GO_PIDS[@]}"} ${RAG_PIDS[@]+"${RAG_PIDS[@]}"}; do kill "$p" 2>/dev/null; done
  sleep 1
  for p in ${GO_PIDS[@]+"${GO_PIDS[@]}"} ${RAG_PIDS[@]+"${RAG_PIDS[@]}"}; do kill -9 "$p" 2>/dev/null; done
  docker rm -f "${GO_PG_CONTAINER}" >/dev/null 2>&1
  docker rm -f "${RAG_PG_CONTAINER}" >/dev/null 2>&1
  # ⚠️ **Nacos 残留**：本轮命名空间里的实例必须注销（否则下一轮会自匹配 ⇒ 假失败）。
  #    命名空间本身**只在本轮创建时**才删（`KEEP_NACOS_NAMESPACE=1` 可保留）。
  probe_nacos_cleanup || true
  # ⚠️ **端口租约必须释放** ⇒ 否则下一轮会跳到下一块端口（默认轮就不再是 8889/18889/… 了）。
  probe_iso_release
  # ⚠️ **不删临时目录**：`rm -rf` 在本机会被回收站守卫拦下（mavis-trash 失效 ⇒ 命令被整体拒绝）
  #    ⇒ 留着 `/var/folders/.../tmp.*` 由系统清理（比让脚本自杀强）。
  if [ "${KEEP_WORK:-0}" = "1" ]; then
    say "   · KEEP_WORK=1 ⇒ 保留 ${WORK}"
  fi
}
trap cleanup EXIT

# ══════════════════════════ 0. 前置检查（缺 ⇒ exit 2，绝不静默降级）══════════════════════════
step "0/8 前置检查"
for cmd in docker go mvn curl xxd shasum lsof nc; do
  command -v "${cmd}" >/dev/null 2>&1 || die "缺命令：${cmd}"
done
GO_REPO="${GO_REPO:-/Users/pr/Documents/java&golang/Sea-RideTheWind_golang}"
[ -d "${GO_REPO}/service/article" ] || die "找不到 Go 真源：${GO_REPO}/service/article（用 GO_REPO=... 指定）"
for pair in "Sea-etcd:${ETCD_PORT}" "Sea-kafka:${KAFKA_PORT}" "Sea-minio:${MINIO_PORT}" "Sea-nacos:${NACOS_PORT}"; do
  c="${pair%%:*}"; p="${pair##*:}"
  docker ps --format '{{.Names}}' | grep -qx "${c}" || die "容器 ${c} 没在跑（宿主中间件缺失）"
  nc -z -G 2 127.0.0.1 "${p}" || die "宿主端口 ${p} 不通（${c} 的映射口）"
done
say "   ✓ 命令齐 / 中间件在跑"
# ⚠️ etcdctl 走容器内端点 ⇒ 先探一次；探不过就 **exit 2**（绝不静默降级成「等超时」）。
docker exec Sea-etcd etcdctl --endpoints="${ETCD_CTL_ENDPOINT}" endpoint health >/dev/null 2>&1 \
  || die "容器内 etcd 端点 ${ETCD_CTL_ENDPOINT} 不可用（ETCD_CTL_ENDPOINT 可覆盖）"
# ⚠️ **本轮 Nacos 命名空间**（RUN_ID 非空时 = 专属命名空间）⇒ 先建后用。
#    建命名空间的接口形态见 `probe-isolation.sh` 的 `probe_nacos_ensure_namespace`（实测 HTTP 200）。
probe_nacos_ensure_namespace || die "本轮 Nacos 命名空间 ${PROBE_NS} 不可用"
say "   ✓ 本轮隔离身份：命名空间=${PROBE_NS} · etcd 键=${PROBE_ETCD_KEY} · 端口块=${PROBE_PORT_BLOCK}（偏移 ${PROBE_PORT_OFFSET}）· PG 偏移 ${PROBE_PG_OFFSET}"
# 端口预检（2026-10-29 实测：上一轮残留进程会让 Go rpc panic: address already in use ⇒ 探针假挂）
free_probe_ports

# ══════════════════════════ 1. 两侧各自的 Postgres ══════════════════════════
step "1/8 两侧 Postgres（Go 侧用真源的 admin/first_db；RAG 侧让它自己的 Flyway 建表）"

# ⚠️ 就绪判据必须是「**宿主端口**通」，不是「容器内 pg_isready」——
#    `docker exec psql` / `pg_isready` 走的是**容器内 socket**，与 `-p` 发布**无关**
#    ⇒ 容器活着 ≠ 宿主端口通（2026-10-29 实测踩过：容器健康但宿主口拒连
#    ⇒ Go rpc 连续刷 connection refused ⇒ 被误判成「没注册进 etcd」）。
# ⚠️ 端口用**固定阶梯**而不是「让 OS 挑一个空闲端口」：后台任务里
#    `bind(("0.0.0.0", 0))` 会报 `EADDRNOTAVAIL`（2026-10-29 实测）⇒ 不用 python 挑端口。
start_pg() {   # $1 = 容器名  $2 = 宿主端口 ⇒ 成功返回 0（宿主端口已验证可连）
  local c="$1" p="$2"
  docker rm -f "${c}" >/dev/null 2>&1
  docker run -d --name "${c}" -e POSTGRES_USER=admin -e POSTGRES_PASSWORD=sea \
    -e POSTGRES_DB=first_db -p "${p}:5432" postgres:16-alpine >/dev/null 2>&1 \
    || return 1
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
# 端口阶梯：前一个被残留 docker-proxy 占住 / 发布不起来 ⇒ 换下一个
pick_pg_port() {   # $1 = 容器名  $2.. = 候选端口 ⇒ 成功时把端口打到 stdout
  local c="$1"; shift
  local p
  for p in "$@"; do
    if start_pg "${c}" "${p}"; then printf '%s' "${p}"; return 0; fi
    # 🔴 **这条必须写 stderr**（2026-11-01 实测踩过）：
    #   本函数的 stdout 被 `GO_PG_PORT="$(pick_pg_port …)"` **捕获** ⇒
    #   若这里也写 stdout ⇒ 提示文字会**混进端口变量** ⇒ 端口变成
    #   `   · :55570 起不来…55572` ⇒ 后续 sed 报「unescaped newline inside substitute pattern」
    #   ⇒ 配置副本生成失败 ⇒ 探针 exit 2。
    #   ⇒ 单跑时（候选一次就中）**看不出来** ⇒ **并发争用同一个端口阶梯**时才暴露。
    say "   · :${p} 起不来（容器内没就绪 或 宿主端口没通）⇒ 换下一个" >&2
    docker rm -f "${c}" >/dev/null 2>&1
  done
  return 1
}
# ⚠️ PG 阶梯也按**本轮块号**偏移（`PROBE_PG_OFFSET`）⇒ 并发两份时两族的 PG 端口也互不重叠。
GO_PG_CANDIDATES=(${GO_PG_PORT:+${GO_PG_PORT}} $(probe_pg_ladder 55570 2 6))
RAG_PG_CANDIDATES=(${RAG_PG_PORT:+${RAG_PG_PORT}} $(probe_pg_ladder 55571 2 6))
GO_PG_PORT="$(pick_pg_port "${GO_PG_CONTAINER}" ${GO_PG_CANDIDATES[@]+${GO_PG_CANDIDATES[@]}})" \
  || die "Go 侧 PG 起不来（候选端口：${GO_PG_CANDIDATES[*]}）"
RAG_PG_PORT="$(pick_pg_port "${RAG_PG_CONTAINER}" ${RAG_PG_CANDIDATES[@]+${RAG_PG_CANDIDATES[@]}})" \
  || die "RAG 侧 PG 起不来（候选端口：${RAG_PG_CANDIDATES[*]}）"
say "   ✓ 两侧 Postgres 就绪（Go :${GO_PG_PORT} · RAG :${RAG_PG_PORT}，**宿主端口已验证可连**）"

# ══════════════════════════ 2. Go 侧：构建 + 真源 DDL ══════════════════════════
step "2/8 构建 Go 侧二进制（真源**只读** ⇒ 产物落临时目录）"
( cd "${GO_REPO}" && CGO_ENABLED=0 GOPROXY=off GOTOOLCHAIN=local \
    go build -o "${WORK}/" ./service/article/api ./service/article/rpc ) || die "Go 侧构建失败"
[ -x "${WORK}/api" ] && [ -x "${WORK}/rpc" ] || die "Go 侧二进制没产出"
say "   ✓ api/rpc 构建完成"
# 真源 DDL：必须**先**跑 002_community_revision.sql，否则真源 rpc 启动即 fatal
apply_go_ddl() {
  local f
  for f in article.sql 002_community_revision.sql; do
    docker exec -i "${GO_PG_CONTAINER}" psql -U admin -d first_db -v ON_ERROR_STOP=1 \
      < "${GO_REPO}/service/article/rpc/internal/model/${f}" >/dev/null || die "真源 ${f} 执行失败"
  done
}
apply_go_ddl
say "   ✓ 真源 DDL 已灌（article.sql + 002_community_revision.sql —— 真源 rpc 启动前**必须**有后者）"

# 起 Go rpc 前**再验一次**宿主端口（构建耗时 2 分钟，容器/端口可能中途没了）
# ⚠️ 必须在**生成配置副本之前**跑：端口一变，副本里的 Postgres Port 就得跟着变。
ensure_go_pg() {
  if host_port_open "${GO_PG_PORT}"; then return 0; fi
  say "   · Go 侧 PG 宿主端口 :${GO_PG_PORT} 不通 ⇒ 换端口重建 + 重灌真源 DDL"
  GO_PG_PORT="$(pick_pg_port "${GO_PG_CONTAINER}" ${GO_PG_CANDIDATES[@]+${GO_PG_CANDIDATES[@]}})" \
    || die "Go 侧 PG 重建失败"
  apply_go_ddl
}
ensure_go_pg

# ══════════════════════════ 3. Go 侧配置副本（真源 yaml 写死主机名 ⇒ 只改副本）══════════════════════════
step "3/8 生成 Go 侧配置副本"
mkdir -p "${WORK}/etc" "${WORK}/log"
sed -e "s|etcd:2379|127.0.0.1:${ETCD_PORT}|g" \
    -e "s|^  Host: postgres$|  Host: 127.0.0.1|" \
    -e "s|^  Port: \"5432\"$|  Port: \"${GO_PG_PORT}\"|" \
    -e "s|kafka:9092|127.0.0.1:${KAFKA_PORT}|g" \
    -e "s|minio:9000|127.0.0.1:${MINIO_PORT}|g" \
    -e "s|jaeger:4317|127.0.0.1:${JAEGER_PORT}|g" \
    -e "s|^  Password: \"\${POSTGRES_PASSWORD}\"$|  Password: \"sea\"|" \
    -e "s|Path: ../../../log/article-rpc|Path: ${WORK}/log/article-rpc|" \
    -e "s|^  Port: 9092$|  Port: ${GO_PROM_PORT}|" \
    -e "s|^ListenOn: 0.0.0.0:9001$|ListenOn: 0.0.0.0:${GO_RPC_PORT}|" \
    -e "s|^  Key: article\.rpc$|  Key: ${PROBE_ETCD_KEY}|" \
    "${GO_REPO}/service/article/rpc/etc/article.yaml" > "${WORK}/etc/article.yaml"
sed -e "s|etcd:2379|127.0.0.1:${ETCD_PORT}|g" \
    -e "s|jaeger:4317|127.0.0.1:${JAEGER_PORT}|g" \
    -e "s|Path: ../../../log/article-api|Path: ${WORK}/log/article-api|" \
    -e "s|^Port: 8889$|Port: ${GO_API_PORT}|" \
    -e "s|^    Key: article\.rpc$|    Key: ${PROBE_ETCD_KEY}|" \
    "${GO_REPO}/service/article/api/etc/article-api.yaml" > "${WORK}/etc/article-api.yaml"
grep -q '127.0.0.1' "${WORK}/etc/article.yaml" || die "配置副本没改成宿主地址（sed 没命中？）"
# ⚠️ 逐条断言「改了哪项」——2026-10-29 实测：`^  Port: 8889$` 这种**缩进写错**的 sed 会静默不命中
#    ⇒ 副本里仍是 8889 ⇒ 端口一被占就 panic ⇒ 探针假挂（此处曾踩）。
grep -q "^Port: ${GO_API_PORT}$"           "${WORK}/etc/article-api.yaml" || die "副本里 article-api 的 Port 没改成 ${GO_API_PORT}（sed 没命中？）"
grep -q "^ListenOn: 0.0.0.0:${GO_RPC_PORT}$" "${WORK}/etc/article.yaml"    || die "副本里 article-rpc 的 ListenOn 没改成 ${GO_RPC_PORT}（sed 没命中？）"
grep -q "^  Port: \"${GO_PG_PORT}\"$"       "${WORK}/etc/article.yaml"        || die "副本里 article-rpc 的 Postgres Port 没改成 ${GO_PG_PORT}（sed 没命中？）"
grep -q "^  Port: ${GO_PROM_PORT}$"         "${WORK}/etc/article.yaml"        || die "副本里 article-rpc 的 Prometheus Port 没改成 ${GO_PROM_PORT}（sed 没命中？）"
[ "${GO_PROM_PORT}" != "${GO_RPC_PORT}" ]  || die "Prometheus 端口与 gRPC ListenOn 撞了（=${GO_RPC_PORT}）⇒ 必 panic: address already in use"
grep -q "^  Host: 127.0.0.1$"              "${WORK}/etc/article.yaml"        || die "副本里 article-rpc 的 Postgres Host 没改成 127.0.0.1（sed 没命中？）"
grep -q "etcd:2379"                        "${WORK}/etc/article.yaml"        && die "副本里还残留 etcd:2379（sed 没命中？）"
grep -q "etcd:2379"                        "${WORK}/etc/article-api.yaml"    && die "副本里还残留 etcd:2379（sed 没命中？）"
# ⚠️ **etcd 键必须两侧一致**（rpc 发布键 = api 订阅前缀）⇒ 否则 Go api 发现不到 Go rpc。
#    真源出处：service/article/rpc/etc/article.yaml:7 / service/article/api/etc/article-api.yaml:11
grep -q "^  Key: ${PROBE_ETCD_KEY}$"  "${WORK}/etc/article.yaml"        || die "副本 article-rpc 的 etcd 键没改成 ${PROBE_ETCD_KEY}（sed 没命中？）"
grep -q "^    Key: ${PROBE_ETCD_KEY}$" "${WORK}/etc/article-api.yaml"    || die "副本 article-api 的 etcd 键没改成 ${PROBE_ETCD_KEY}（sed 没命中？）"
( cd "${GO_REPO}" && git status --porcelain -- service/article | grep -q . ) \
  && die "真源仓 service/article 有改动 ⇒ 本探针**不得**改真源"
say "   ✓ 配置副本已生成（真源 service/article 仍然干净）"

# ══════════════════════════ 4. 起 Go 侧 rpc + api ══════════════════════════
step "4/8 起 Go 侧 article-rpc（注册 etcd ${PROBE_ETCD_KEY}）"
# 残留注册先清掉（上一轮的 lease 可能还在 ⇒ 客户端可能选中死实例）
# ⚠️ **只删本轮的前缀** ⇒ 绝不碰别的轮次的注册。
docker exec Sea-etcd etcdctl --endpoints="${ETCD_CTL_ENDPOINT}" del "${PROBE_ETCD_KEY}" --prefix >/dev/null 2>&1
# ⚠️ **必须用 `exec`** —— 否则 `$!` 是**子 shell 的 PID**，而真正的二进制是它的**子进程**
#    ⇒ `cleanup()` 杀的是 wrapper ⇒ **二进制 survive ⇒ 占着 :8889 / :9002 / :19092**
#    ⇒ 下一轮 `free_probe_ports` 才补刀 ⇒ 脚本化连跑时容易撞上「端口/GPRC 实例被上一轮占着」
#    ⇒ **2026-10-29 实测踩过**（变异脚本 M1 报 exit 2 = 环境问题，根因即此）
( cd "${WORK}" && exec "${WORK}/rpc" -f "${WORK}/etc/article.yaml" >"${WORK}/go-rpc.log" 2>&1 ) &
GO_PIDS+=($!)
# ⚠️ go-zero 的 etcd 键是**前缀族** `<key>/<leaseID>`（真源 yaml `Key: article.rpc`
#    ⇒ `core/discov/publisher.go:191-196` + `core/discov/clients.go:39-41`
#    ⇒ 实际键形如 `article.rpc/7587898349098767861`）
#    ⇒ 这里按**前缀**查（2026-10-29 实测踩过：用精确键查 ⇒ 永远查不到 ⇒ 假阴性）。
for _ in $(seq 1 40); do
  etcd_has_key "${PROBE_ETCD_KEY}" && break
  sleep 1
done
etcd_has_key "${PROBE_ETCD_KEY}" || die "Go article-rpc 40s 内没注册进 etcd（见 ${WORK}/go-rpc.log）"
# ⚠️ go-zero **先注册 etcd 再 bind 端口**（`zrpc/internal/rpcpubserver.go:33` registerEtcd
#    在 `Server.Start` 之前）⇒ 「etcd 有键」≠ 「端口已监听」⇒ 必须再等一次端口。
for _ in $(seq 1 20); do nc -z -G 1 127.0.0.1 "${GO_RPC_PORT}" && break; sleep 1; done
nc -z -G 2 127.0.0.1 "${GO_RPC_PORT}" || die "Go article-rpc 注册了但端口没监听（见 ${WORK}/go-rpc.log）"
# 🔴 注册值里必须带**本轮**的 gRPC 端口 ⇒ 排除「别的轮次/上一轮的残留键」自匹配。
case "$(etcd_get_value "${PROBE_ETCD_KEY}")" in
  *":${GO_RPC_PORT}"*) : ;;
  *) die "etcd 上 ${PROBE_ETCD_KEY} 的值不是本轮端口 :${GO_RPC_PORT} ⇒ 残留键自匹配" ;;
esac
say "   ✓ 已注册 ${PROBE_ETCD_KEY} → $(etcd_get_value "${PROBE_ETCD_KEY}")（:${GO_RPC_PORT} 已监听）"
step "5/8 起 Go 侧 article-api（:${GO_API_PORT}）"
# ⚠️ 同上：**必须 `exec`**（否则 `$!` 不是二进制 PID ⇒ cleanup 杀不掉）
( cd "${WORK}" && exec "${WORK}/api" -f "${WORK}/etc/article-api.yaml" >"${WORK}/go-api.log" 2>&1 ) &
GO_PIDS+=($!)
for _ in $(seq 1 40); do nc -z -G 1 127.0.0.1 "${GO_API_PORT}" && break; sleep 1; done
nc -z -G 2 127.0.0.1 "${GO_API_PORT}" || die "Go article-api 没起来（见 ${WORK}/go-api.log）"
say "   ✓ Go 侧已监听 :${GO_API_PORT}"

# ══════════════════════════ 5. 起 RAG 侧 ══════════════════════════
step "6/8 起 RAG 侧 article-rpc（注册 Nacos ${NACOS_PORT} · 命名空间 ${PROBE_NS}）+ article-api（:${RAG_API_PORT}）"
# ⚠️ **构建必须串行化**（V4）：两个模块的 `target/` 是同一份 ⇒ 并发写**必然互踩**。
#    实测 `-Dproject.build.directory=<dir>` **不生效**（jar 仍落在 `service/article/*/target/`）
#    ⇒ 只能「**持锁构建 + 把 fat jar 拷进本轮 WORK**」⇒ 本轮跑的是**自己的副本**，
#    下一轮再重建 `target/` 也**影响不到本轮**。
probe_mvn_lock_acquire || die "等构建锁超时（另一轮还在构建？）"
( cd "${RAG_ROOT}" && mvn -o -q -pl service/article/article-rpc,service/article/article-api -am package -DskipTests ) \
  || { probe_mvn_lock_release; die "RAG 侧构建失败"; }
RAG_RPC_JAR="${RAG_ROOT}/service/article/article-rpc/target/article-rpc-0.0.1-SNAPSHOT.jar"
RAG_API_JAR="${RAG_ROOT}/service/article/article-api/target/article-api-0.0.1-SNAPSHOT.jar"
[ -f "${RAG_RPC_JAR}" ] && [ -f "${RAG_API_JAR}" ] || { probe_mvn_lock_release; die "RAG 侧 jar 没产出"; }
mkdir -p "${WORK}/jars"
cp "${RAG_RPC_JAR}" "${WORK}/jars/article-rpc.jar" || { probe_mvn_lock_release; die "拷 rpc jar 失败"; }
cp "${RAG_API_JAR}"  "${WORK}/jars/article-api.jar"  || { probe_mvn_lock_release; die "拷 api jar 失败"; }
probe_mvn_lock_release
RAG_RPC_JAR="${WORK}/jars/article-rpc.jar"
RAG_API_JAR="${WORK}/jars/article-api.jar"
say "   ✓ RAG 侧构建完成（jar 已拷进本轮 WORK ⇒ 与别的轮次互不干扰）"
# ⚠️ 先注销 Nacos 里的**残留实例**（上一轮的旧实例在心跳超时前仍是"健康"的）
nacos_deregister_all article-rpc
# ⚠️ `NACOS_NAMESPACE` **必须显式传**：本轮命名空间 = 隔离面 V1
#    （`service/article/article-rpc/src/main/resources/application.yml:122`
#      与 `service/article/article-api/src/main/resources/application.yml:33`
#      都是 `namespace: ${NACOS_NAMESPACE:public}` ⇒ 命名空间由**客户端配置**决定）。
# ⚠️ `NACOS_ADDR` **与** `NACOS_PORT` **都传**：
#    `server-addr` 的写法有两种（R1-ENV-001 收口轮正在把 rpc 从旧写法改成新写法）：
#      旧：`server-addr: ${NACOS_ADDR:nacos:8848}`   ⇒ `NACOS_ADDR` 里要带端口
#      新：`server-addr: ${NACOS_ADDR:nacos}:${NACOS_PORT:8848}` ⇒ `NACOS_ADDR` 只给主机名
#    ⇒ 两个都传 ⇒ 两种写法下拼出来的都是 `127.0.0.1:<port>`（Nacos 客户端按 `host:port[:context]`
#    解析 ⇒ 第三段当 context path 忽略）⇒ **两种写法都能连对**。
POSTGRES_HOST=127.0.0.1 POSTGRES_PORT="${RAG_PG_PORT}" POSTGRES_DB=first_db \
POSTGRES_USER=admin POSTGRES_PASSWORD=sea \
KAFKA_BROKERS="127.0.0.1:${KAFKA_PORT}" NACOS_ADDR="127.0.0.1:${NACOS_PORT}" \
NACOS_PORT="${NACOS_PORT}" NACOS_NAMESPACE="${PROBE_NS}" \
ARTICLE_RPC_PORT="${RAG_RPC_PORT}" ARTICLE_RPC_MANAGEMENT_PORT="${RAG_RPC_MGMT_PORT}" \
  java -jar "${RAG_RPC_JAR}" >"${WORK}/rag-rpc.log" 2>&1 &
RAG_PIDS+=($!)
# 🔴 **就绪判据精确到「本轮端口」**：数「注册端口 == 本轮 gRPC 端口」的实例个数
#    （且只在本轮命名空间里数）⇒ 别的轮次的实例**不会被数进来**。
for _ in $(seq 1 60); do
  [ "$(nacos_instance_count "${RAG_RPC_PORT}")" != "0" ] && break
  sleep 1
done
[ "$(nacos_instance_count "${RAG_RPC_PORT}")" != "0" ] \
  || die "RAG article-rpc 60s 内没以端口 ${RAG_RPC_PORT} 注册进 Nacos（见 ${WORK}/rag-rpc.log）"
say "   ✓ RAG article-rpc 已注册进 Nacos（命名空间 ${PROBE_NS} · 端口 ${RAG_RPC_PORT}）"
NACOS_ADDR="127.0.0.1:${NACOS_PORT}" NACOS_PORT="${NACOS_PORT}" NACOS_NAMESPACE="${PROBE_NS}" \
ARTICLE_API_PORT="${RAG_API_PORT}" USER_JWT_SECRET="${JWT_SECRET}" \
  java -jar "${RAG_API_JAR}" >"${WORK}/rag-api.log" 2>&1 &
RAG_PIDS+=($!)
for _ in $(seq 1 60); do nc -z -G 1 127.0.0.1 "${RAG_API_PORT}" && break; sleep 1; done
nc -z -G 2 127.0.0.1 "${RAG_API_PORT}" || die "RAG article-api 没起来（见 ${WORK}/rag-api.log）"
say "   ✓ RAG 侧已监听 :${RAG_API_PORT}"

# ══════════════════════════ 6. 两侧灌同一行数据 ══════════════════════════
step "7/8 两侧灌同一行（status=2 PUBLISHED —— 真源的公开列表只放 PUBLISHED）"
SEED="INSERT INTO article (id,title,brief,content,cover_image_url,manual_type_tag,secondary_tags,author_id,status,view_count,like_count,comment_count,share_count,ext_info,created_at,updated_at) VALUES ('9007199254740993','标题','摘要','正文','https://example.invalid/c.png','tech','[]','1001',2,1,2,3,4,'{}','2026-01-02 03:04:05+00','2026-01-02 03:04:06+00');"
for c in "${GO_PG_CONTAINER}" "${RAG_PG_CONTAINER}"; do
  docker exec "${c}" psql -U admin -d first_db -c "${SEED}" >/dev/null || die "灌数据失败：${c}"
done
say "   ✓ 两侧同一行（9007199254740993）"

# ══════════════════════════ 7. 请求矩阵：两侧 vs 冻结期望值，逐字节 ══════════════════════════
step "8/8 请求矩阵（Go vs RAG vs expected.txt ⇒ 逐字节）"
EXPECTED="${HERE}/expected.txt"
[ -f "${EXPECTED}" ] || die "缺少冻结的期望值：${EXPECTED}"
# ⚠️ **先过就绪闸门**：「端口已监听」≠「下游 rpc 已就绪」⇒ 首请求可能超时 ⇒ 1015
#    ⇒ 那不是「两侧漂移」，是「没准备好」⇒ 必须先等就绪，再判对错。
wait_ready "${GO_API_PORT}"  "Go  侧" || die "Go 侧没就绪（见上面的日志）"
wait_ready "${RAG_API_PORT}" "RAG 侧" || die "RAG 侧没就绪（见上面的日志）"
FAILED=0
run_case() {
  local name="$1" path="$2"
  local g r gh rh
  g=$(curl -s -m 20 "http://127.0.0.1:${GO_API_PORT}${path}")
  r=$(curl -s -m 20 "http://127.0.0.1:${RAG_API_PORT}${path}")
  gh=$(printf '%s' "$g" | xxd -p | tr -d '\n')
  rh=$(printf '%s' "$r" | xxd -p | tr -d '\n')
  local eh
  eh=$(awk -F'|' -v n="CASE|${name}" '$1"|"$2==n {print $3}' "${EXPECTED}")
  [ -n "${eh}" ] || { say "❌ ${name}：expected.txt 里没有这一条"; FAILED=1; return; }
  if [ "${g}" = "${r}" ] && [ "${gh}" = "${eh}" ]; then
    say "   ✅ ${name}  Go == RAG == expected.txt（逐字节）"
  else
    FAILED=1
    say "   ❌ ${name}"
    [ "${gh}" != "${eh}" ] && { say "      Go != expected"; say "      Go : ${g}"; }
    [ "${rh}" != "${eh}" ] && { say "      RAG != expected"; say "      RAG: ${r}"; }
  fi
}
run_case "list_articles"        "/v1/articles"
run_case "get_article_exists"  "/v1/article/9007199254740993"
run_case "get_article_missing" "/v1/article/does-not-exist"

echo
if [ "${FAILED}" = "0" ]; then
  say "✅ 双侧逐字节一致（Go 真源 == RAG == 冻结期望值）"
  exit 0
fi
say "❌ 双侧存在字节差异"
dump_logs
exit 1
