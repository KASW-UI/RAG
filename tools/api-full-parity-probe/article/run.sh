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
GO_API_PORT="${GO_API_PORT:-8889}"
RAG_API_PORT="${RAG_API_PORT:-18889}"
GO_RPC_PORT="${GO_RPC_PORT:-9002}"   # 9001 留给 RAG（它的 grpc.server.port 写死）
# ⚠️ **Prometheus 是另一个独立监听者**（go-zero v1.10.2 `core/service/serviceconf.go:62`
#    `prometheus.StartAgent(sc.Prometheus)` ⇒ `core/prometheus/agent.go:39` 绑 `Host:Port`）
#    ⇒ 它与 `ListenOn` 是**两个**端口，**不能改成同一个**（2026-10-29 实测踩过：
#    把 Prometheus 也改成 9002 ⇒ 它先绑住 9002 ⇒ gRPC 的 net.Listen 撞车
#    ⇒ `zrpc/server.go:91` panic: address already in use ⇒ 探针假挂 40s 后才报）。
GO_PROM_PORT="${GO_PROM_PORT:-19092}"
RAG_RPC_PORT=9001
# ── 两侧各自的 Postgres（Go 侧要真源的 admin/first_db；RAG 侧要它自己的 Flyway 账本）──
# ⚠️ **容器名带 $$**（每轮唯一）⇒ 别的轮/别的 lane 的 cleanup 绝不可能把本轮的库删掉
#    （2026-10-29 实测踩过：固定名 + 上一轮 cleanup 延迟执行 ⇒ 本轮容器被删 ⇒
#    宿主端口 55570 拒连 ⇒ Go rpc 连续刷「connection refused」40s 才报「没注册进 etcd」）。
GO_PG_CONTAINER="${GO_PG_CONTAINER:-g3-article-go-pg-$$}"
GO_PG_PORT="${GO_PG_PORT:-}"
RAG_PG_CONTAINER="${RAG_PG_CONTAINER:-g3-article-rag-pg-$$}"
RAG_PG_PORT="${RAG_PG_PORT:-}"
# ── 验签密钥（必须与签发方 user-api 一致；RAG 侧缺它 ⇒ 拒启动）─────────────────
JWT_SECRET="${JWT_SECRET:-ae0536f9-6450-4606-8e13-5a19ed505da0}"

WORK="$(mktemp -d)"
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
nacos_deregister_all() {   # $1 = 服务名
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
free_probe_ports() {
  local p pid
  for p in "${GO_API_PORT}" "${RAG_API_PORT}" "${GO_RPC_PORT}" "${RAG_RPC_PORT}" "${GO_PROM_PORT}"; do
    for pid in $(lsof -ti tcp:"${p}" 2>/dev/null); do
      kill -9 "${pid}" 2>/dev/null
      say "   · 踢掉占用 :${p} 的残留进程 ${pid}"
    done
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
    say "   · :${p} 起不来（容器内没就绪 或 宿主端口没通）⇒ 换下一个"
    docker rm -f "${c}" >/dev/null 2>&1
  done
  return 1
}
GO_PG_CANDIDATES=(${GO_PG_PORT:+${GO_PG_PORT}} 55570 55572 55574 55576 55578 55580)
RAG_PG_CANDIDATES=(${RAG_PG_PORT:+${RAG_PG_PORT}} 55571 55573 55575 55577 55579 55581)
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
    "${GO_REPO}/service/article/rpc/etc/article.yaml" > "${WORK}/etc/article.yaml"
sed -e "s|etcd:2379|127.0.0.1:${ETCD_PORT}|g" \
    -e "s|jaeger:4317|127.0.0.1:${JAEGER_PORT}|g" \
    -e "s|Path: ../../../log/article-api|Path: ${WORK}/log/article-api|" \
    -e "s|^Port: 8889$|Port: ${GO_API_PORT}|" \
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
( cd "${GO_REPO}" && git status --porcelain -- service/article | grep -q . ) \
  && die "真源仓 service/article 有改动 ⇒ 本探针**不得**改真源"
say "   ✓ 配置副本已生成（真源 service/article 仍然干净）"

# ══════════════════════════ 4. 起 Go 侧 rpc + api ══════════════════════════
step "4/8 起 Go 侧 article-rpc（注册 etcd ${ETCD_PORT}）"
# 残留注册先清掉（上一轮的 lease 可能还在 ⇒ 客户端可能选中死实例）
docker exec Sea-etcd etcdctl --endpoints="${ETCD_CTL_ENDPOINT}" del "article.rpc" --prefix >/dev/null 2>&1
( cd "${WORK}" && "${WORK}/rpc" -f "${WORK}/etc/article.yaml" >"${WORK}/go-rpc.log" 2>&1 ) &
GO_PIDS+=($!)
# ⚠️ go-zero 的 etcd 键是**前缀族** `<key>/<leaseID>`（真源 yaml `Key: article.rpc`
#    ⇒ `core/discov/publisher.go:191-196` + `core/discov/clients.go:39-41`
#    ⇒ 实际键形如 `article.rpc/7587898349098767861`）
#    ⇒ 这里按**前缀**查（2026-10-29 实测踩过：用精确键查 ⇒ 永远查不到 ⇒ 假阴性）。
for _ in $(seq 1 40); do
  etcd_has_key article.rpc && break
  sleep 1
done
etcd_has_key article.rpc || die "Go article-rpc 40s 内没注册进 etcd（见 ${WORK}/go-rpc.log）"
# ⚠️ go-zero **先注册 etcd 再 bind 端口**（`zrpc/internal/rpcpubserver.go:33` registerEtcd
#    在 `Server.Start` 之前）⇒ 「etcd 有键」≠ 「端口已监听」⇒ 必须再等一次端口。
for _ in $(seq 1 20); do nc -z -G 1 127.0.0.1 "${GO_RPC_PORT}" && break; sleep 1; done
nc -z -G 2 127.0.0.1 "${GO_RPC_PORT}" || die "Go article-rpc 注册了但端口没监听（见 ${WORK}/go-rpc.log）"
say "   ✓ 已注册 article.rpc → $(etcd_get_value article.rpc)（:${GO_RPC_PORT} 已监听）"
step "5/8 起 Go 侧 article-api（:${GO_API_PORT}）"
( cd "${WORK}" && "${WORK}/api" -f "${WORK}/etc/article-api.yaml" >"${WORK}/go-api.log" 2>&1 ) &
GO_PIDS+=($!)
for _ in $(seq 1 40); do nc -z -G 1 127.0.0.1 "${GO_API_PORT}" && break; sleep 1; done
nc -z -G 2 127.0.0.1 "${GO_API_PORT}" || die "Go article-api 没起来（见 ${WORK}/go-api.log）"
say "   ✓ Go 侧已监听 :${GO_API_PORT}"

# ══════════════════════════ 5. 起 RAG 侧 ══════════════════════════
step "6/8 起 RAG 侧 article-rpc（注册 Nacos ${NACOS_PORT}）+ article-api（:${RAG_API_PORT}）"
( cd "${RAG_ROOT}" && mvn -o -q -pl service/article/article-rpc,service/article/article-api -am package -DskipTests ) \
  || die "RAG 侧构建失败"
RAG_RPC_JAR="${RAG_ROOT}/service/article/article-rpc/target/article-rpc-0.0.1-SNAPSHOT.jar"
RAG_API_JAR="${RAG_ROOT}/service/article/article-api/target/article-api-0.0.1-SNAPSHOT.jar"
[ -f "${RAG_RPC_JAR}" ] && [ -f "${RAG_API_JAR}" ] || die "RAG 侧 jar 没产出"
# ⚠️ 先注销 Nacos 里的**残留实例**（上一轮的旧实例在心跳超时前仍是"健康"的）
nacos_deregister_all article-rpc
POSTGRES_HOST=127.0.0.1 POSTGRES_PORT="${RAG_PG_PORT}" POSTGRES_DB=first_db \
POSTGRES_USER=admin POSTGRES_PASSWORD=sea \
KAFKA_BROKERS="127.0.0.1:${KAFKA_PORT}" NACOS_ADDR="127.0.0.1:${NACOS_PORT}" \
  java -jar "${RAG_RPC_JAR}" >"${WORK}/rag-rpc.log" 2>&1 &
RAG_PIDS+=($!)
for _ in $(seq 1 60); do
  curl -s "http://127.0.0.1:${NACOS_PORT}/nacos/v1/ns/service/list?pageNo=1&pageSize=30&namespaceId=public" 2>/dev/null | grep -q 'article-rpc' && break
  sleep 1
done
curl -s "http://127.0.0.1:${NACOS_PORT}/nacos/v1/ns/service/list?pageNo=1&pageSize=30&namespaceId=public" 2>/dev/null | grep -q 'article-rpc' \
  || die "RAG article-rpc 60s 内没注册进 Nacos（见 ${WORK}/rag-rpc.log）"
say "   ✓ RAG article-rpc 已注册进 Nacos"
NACOS_ADDR="127.0.0.1:${NACOS_PORT}" ARTICLE_API_PORT="${RAG_API_PORT}" USER_JWT_SECRET="${JWT_SECRET}" \
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
