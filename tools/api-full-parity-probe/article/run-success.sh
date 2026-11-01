#!/usr/bin/env bash
# article 域 **详情端点成功路径** 双侧活体对拍（Go 真源栈 ⇄ RAG 栈 ⇒ 逐字节）
#
# 【为什么单独一个脚本 · 不动 run.sh】
#   `run.sh` 的 `expected.txt` 冻结了 3 条 L4 双侧活体证据 ⇒ 改它会让那 3 条失效。
#   本脚本**独立**起栈 + 独立种子 + 独立期望值文件（`expected-success.txt`）
#   ⇒ 老证据不受影响。本脚本**只复用 run.sh 已验证过的启动手法**（sed 块 / 环境变量 /
#   etcd 前缀键 / Nacos 残留注销 / 就绪闸门），**不改 run.sh 一个字节**。
#
# 【本脚本补的是什么缺口】
#   `run.sh` 的 `get_article_exists` 钉的是「**降级路径**两侧一致」（两侧都 5001）。
#   ⚠️ **根因不是 `user.rpc` 不可达**（2026-11-01 · G3 订正）：
#     Go `service/article/api/internal/logic/article/get_article_logic.go:78`
#     `enrichArticleAuthor` 在 rpc **成功返回之后**才填 `AuthorName`；
#     失败走 `helper.go:98-103` `fallbackAuthorName` ⇒ **永不抛异常** ⇒ 不影响端点成败。
#     RAG `ArticleAuthorResolver.resolveAuthorName` javadoc 自述「**永不抛异常**」，四分支全回落。
#   ⇒ **降级路径的真实根因 = legacy 分支读 MinIO 失败**：
#     Go `service/article/rpc/internal/logic/get_article_logic.go:139-153`
#     `published.Revision == nil` ⇒ `MinioClient.GetObject(bucket, article.Content)` 失败
#     ⇒ `status.Error(codes.Internal, "read legacy article failed")` ⇒ api 层 5001。
#     触发条件 = 种子行 `content='正文'` **不是合法 MinIO object key**。
#   ⇒ **成功路径的开关 = 有没有 revision 指针**（有 ⇒ 不读 MinIO）。
#
# 【本脚本怎么做】
#   插 `article_publication`（state=published + current_revision）+ `article_revision`
#   ⇒ 命中 Go `service/article/rpc/internal/model/article_public.go:20-26`
#     `publicArticlePredicate` 的**第一支**
#   ⇒ 走 revision 分支（Go `get_article_logic.go:137-139` /
#     RAG `ArticlePublicDomainService:130-132`）⇒ **正文不读 MinIO**
#   ⇒ 详情端点返回**完整字段** ⇒ 成功路径的双侧逐字节证据。
#
# 【「确实走到了 revision 分支」的四个独立信号】（不靠「两侧一致」自证）
#   S1 `ext_info` = `{"published_revision_id":"rev-succ-0001"}`
#      （legacy 走 `{"publication_gap":"legacy_revision_missing"}` ⇒ Go get_article_logic.go:180/186）
#   S2 `update_time` = **revision.published_at**（不是 article.updated_at）
#      （Go get_article_logic.go:185 `response.UpdateTime = revision.PublishedAt.UnixMilli()`）
#   S3 `content` = revision.markdown（**不是** article.content）
#   S4 `title` / `brief` / `cover_image_url` / `manual_type_tag` = **revision 的值**
#      （Go get_article_logic.go:183-184）
#   ⇒ 种子里 article 行与 revision 行的这些字段**故意填成不同值** ⇒ 4 个信号互相独立。
#
# 用法：
#   bash tools/api-full-parity-probe/article/run-success.sh
#   FREEZE=1 bash tools/api-full-parity-probe/article/run-success.sh   # 首次冻结期望值
#
# 退出码：0 = 两侧逐字节一致（且 4 个信号自检通过）· 1 = 有差异 · 2 = 环境问题 / 真源漂移 / 依赖缺失。
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAG_ROOT="$(cd "${HERE}/../../.." && pwd)"
GO_REPO="${GO_REPO:-/Users/pr/Documents/java&golang/Sea-RideTheWind_golang}"

# ── 宿主中间件端口（与 run.sh 同一套实测值）────────────────────────────────────
ETCD_PORT="${ETCD_PORT:-49319}"      # Sea-etcd    49319→2379
# ⚠️ etcdctl 走**容器内**端点 ⇒ 必须是 2379（用宿主映射口会 connection refused）。
ETCD_CTL_ENDPOINT="${ETCD_CTL_ENDPOINT:-http://127.0.0.1:2379}"
KAFKA_PORT="${KAFKA_PORT:-49992}"    # Sea-kafka   49992→9093
MINIO_PORT="${MINIO_PORT:-49100}"    # Sea-minio   49100→9000
JAEGER_PORT="${JAEGER_PORT:-49317}"  # Sea-jaeger  49317→4317
NACOS_PORT="${NACOS_PORT:-49848}"    # Sea-nacos   49848→8848

# ── 本脚本的端口（article 域预分配 22400 段 ⇒ 用 22411~22417）─────────────────
# ⚠️ 与 run.sh 的 8889/18889/9002/9001/19092 **刻意错开** ⇒ 两个脚本可先后/并行跑。
GO_API_PORT="${GO_API_PORT:-22411}"
GO_RPC_PORT="${GO_RPC_PORT:-22412}"
GO_PROM_PORT="${GO_PROM_PORT:-22413}"
RAG_API_PORT="${RAG_API_PORT:-22415}"
RAG_RPC_PORT="${RAG_RPC_PORT:-22416}"
RAG_RPC_MGMT_PORT="${RAG_RPC_MGMT_PORT:-22414}"   # article-rpc 的 Actuator 载体口
# ⚠️ Prometheus 是**另一个独立监听者**（go-zero `core/prometheus/agent.go:39` 绑 `Host:Port`）
#    ⇒ 与 `ListenOn` 是**两个**端口，**不能改成同一个** ⇒ 否则 `zrpc/server.go:91` panic。
# ── 两侧各自的 Postgres（端口阶梯**避开 run.sh 的 55570~55581**）──────────────
GO_PG_CONTAINER="${GO_PG_CONTAINER:-g3-article-succ-go-pg-$$}"
RAG_PG_CONTAINER="${RAG_PG_CONTAINER:-g3-article-succ-rag-pg-$$}"
GO_PG_CANDIDATES=(${GO_PG_PORT:+${GO_PG_PORT}} 55590 55592 55594 55596 55598 55600)
RAG_PG_CANDIDATES=(${RAG_PG_PORT:+${RAG_PG_PORT}} 55591 55593 55595 55597 55599 55601)
# ── 验签密钥（必须与签发方 user-api 一致；RAG 侧缺它 ⇒ 拒启动）───────────────
JWT_SECRET="${JWT_SECRET:-ae0536f9-6450-4606-8e13-5a19ed505da0}"

# ── 种子（article 行与 revision 行的字段**故意不同** ⇒ 可证明走了 revision 分支）──
AID="${AID:-9007199254740994}"
REV="${REV:-rev-succ-0001}"
EXPECTED="${HERE}/expected-success.txt"

WORK="$(mktemp -d)"
GO_PIDS=(); RAG_PIDS=()
say()  { printf '%s\n' "$*"; }
step() { printf '\n== %s ==\n' "$*"; }
die()  { printf '!! %s\n' "$*" >&2; dump_logs; exit 2; }
dump_logs() {
  local f
  for f in go-rpc.log go-api.log rag-rpc.log rag-api.log; do
    [ -f "${WORK:-/nonexistent}/${f}" ] || continue
    printf '\n---- %s（末 30 行）----\n' "${f}" >&2
    tail -30 "${WORK}/${f}" | sed 's/^/   | /' >&2
  done
}
cleanup() {
  local p
  for p in ${GO_PIDS[@]+"${GO_PIDS[@]}"} ${RAG_PIDS[@]+"${RAG_PIDS[@]}"}; do
    kill "$p" 2>/dev/null || true
  done
  sleep 1
  for p in ${GO_PIDS[@]+"${GO_PIDS[@]}"} ${RAG_PIDS[@]+"${RAG_PIDS[@]}"}; do
    kill -9 "$p" 2>/dev/null || true
  done
  docker rm -f "${GO_PG_CONTAINER}" >/dev/null 2>&1 || true
  docker rm -f "${RAG_PG_CONTAINER}" >/dev/null 2>&1 || true
  # ⚠️ **不删临时目录**：`rm -rf` 在本机会被回收站守卫整体拦下 ⇒ 留给系统清理。
  if [ "${KEEP_WORK:-0}" = "1" ]; then say "   · KEEP_WORK=1 ⇒ 保留 ${WORK}"; fi
}
trap cleanup EXIT

host_port_open() { nc -z -G 2 127.0.0.1 "$1" >/dev/null 2>&1; }

# ── etcd 访问（键 = `<key>/<leaseID>` **前缀族**）────────────────────────────────
# go-zero v1.10.2 `core/discov/publisher.go:191-196` ⇒ 实际键形如 `article.rpc/7587...`
# ⇒ **必须按前缀查**（精确键查 ⇒ 永远查不到 ⇒ 假阴性）。
etcd_get_value() {
  docker exec Sea-etcd etcdctl --endpoints="${ETCD_CTL_ENDPOINT}" get "$1" --prefix --print-value-only 2>/dev/null | head -1
}
etcd_has_key() { [ -n "$(etcd_get_value "$1")" ]; }

# ── 注销 Nacos 里的**残留实例**（上一轮残留实例在心跳超时前仍「健康」）──────────
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

# 🔴 **就绪判据必须精确到「本轮的端口」**（2026-11-01 实测踩过）：
#   只查 `service/list` 里**有没有这个服务名** ⇒ **上一轮的残留实例**也会命中
#   ⇒ 脚本在 java **还在启动**（日志 0 字节）时就去查端口 ⇒ 误判「没监听」⇒ 假失败。
#   ⇒ 改成数「**注册端口 == 本轮 gRPC 端口**」的实例个数 ⇒ 只有本轮的实例才算数。
#   （RAG 侧注册的是 **gRPC 端口**：`bootstrap/NacosRegistration.java` 用
#     `GrpcServerProperties.getPort()` 注册 ⇒ 与 Go 侧注册 `ListenOn` 同语义。）
nacos_instance_count() {   # $1 = 端口 ⇒ 打印该端口上的实例个数
  curl -s -m 10 "http://127.0.0.1:${NACOS_PORT}/nacos/v1/ns/instance/list?serviceName=article-rpc&groupName=DEFAULT_GROUP&namespaceId=public" 2>/dev/null \
  | python3 -c '
import sys, json
port = sys.argv[1]
try:
    d = json.load(sys.stdin)
except Exception:
    print(0); sys.exit(0)
n = 0
for h in (d.get("hosts") or []):
    if str(h.get("port")) == port:
        n += 1
print(n)
' "$1"
}

# ── 就绪闸门：等**列表端点**真的答得上（不是 1015、不是空）────────────────────
# ⚠️ 「端口已监听」≠「下游 rpc 已就绪」⇒ 首请求可能超时 ⇒ 1015 ⇒ 误判成「两侧漂移」。
wait_ready() {
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

# ── 端口预检：踢掉**上一轮残留**的探针进程（否则 Go rpc panic: address already in use）
free_probe_ports() {
  local p pid
  for p in "${GO_API_PORT}" "${RAG_API_PORT}" "${GO_RPC_PORT}" "${RAG_RPC_PORT}" \
           "${GO_PROM_PORT}" "${RAG_RPC_MGMT_PORT}"; do
    for pid in $(lsof -ti tcp:"${p}" 2>/dev/null); do
      kill -9 "${pid}" 2>/dev/null
      say "   · 踢掉占用 :${p} 的残留进程 ${pid}"
    done
  done
  sleep 1
}

# ══════════════════════════ 0. 前置检查 ══════════════════════════
step "0/9 前置检查"
for cmd in docker go mvn curl xxd python3 lsof nc; do
  command -v "${cmd}" >/dev/null 2>&1 || die "缺命令：${cmd}"
done
[ -d "${GO_REPO}/service/article" ] || die "找不到 Go 真源：${GO_REPO}/service/article（用 GO_REPO=... 指定）"
for pair in "Sea-etcd:${ETCD_PORT}" "Sea-kafka:${KAFKA_PORT}" "Sea-minio:${MINIO_PORT}" "Sea-nacos:${NACOS_PORT}"; do
  c="${pair%%:*}"; p="${pair##*:}"
  docker ps --format '{{.Names}}' | grep -qx "${c}" || die "容器 ${c} 没在跑（宿主中间件缺失）"
  nc -z -G 2 127.0.0.1 "${p}" || die "宿主端口 ${p} 不通（${c} 的映射口）"
done
docker exec Sea-etcd etcdctl --endpoints="${ETCD_CTL_ENDPOINT}" endpoint health >/dev/null 2>&1 \
  || die "容器内 etcd 端点 ${ETCD_CTL_ENDPOINT} 不可用（ETCD_CTL_ENDPOINT 可覆盖）"
free_probe_ports
say "   ✓ 命令齐 / 中间件在跑 / 端口已腾空"

# ══════════════════════════ 1. 两侧各自的 Postgres ══════════════════════════
step "1/9 两侧 Postgres（Go 侧用真源 DDL · RAG 侧让它自己的 Flyway 建表）"
start_pg() {   # $1 = 容器名  $2 = 宿主端口 ⇒ 成功返回 0（**宿主端口**已验证可连）
  local c="$1" p="$2"
  docker rm -f "${c}" >/dev/null 2>&1
  docker run -d --name "$c" -e POSTGRES_USER=admin -e POSTGRES_PASSWORD=sea \
    -e POSTGRES_DB=first_db -p "${p}:5432" postgres:16-alpine >/dev/null 2>&1 || return 1
  for _ in $(seq 1 40); do
    docker exec "$c" pg_isready -U admin -d first_db >/dev/null 2>&1 && break
    sleep 1
  done
  docker exec "$c" pg_isready -U admin -d first_db >/dev/null 2>&1 || return 1
  for _ in $(seq 1 15); do
    host_port_open "${p}" && return 0
    sleep 1
  done
  return 1
}
# ⚠️ 端口用**固定阶梯**而不是「让 OS 挑空闲端口」：后台任务里 `bind(("0.0.0.0",0))`
#    会报 `EADDRNOTAVAIL` ⇒ 不用 python 挑端口。
pick_pg_port() {
  local c="$1"; shift
  local p
  for p in "$@"; do
    if start_pg "${c}" "${p}"; then printf '%s' "${p}"; return 0; fi
    say "   · :${p} 起不来（容器内没就绪 或 宿主端口没通）⇒ 换下一个"
    docker rm -f "${c}" >/dev/null 2>&1
  done
  return 1
}
GO_PG_PORT="$(pick_pg_port "${GO_PG_CONTAINER}" ${GO_PG_CANDIDATES[@]+${GO_PG_CANDIDATES[@]}})" \
  || die "Go 侧 PG 起不来（候选：${GO_PG_CANDIDATES[*]}）"
RAG_PG_PORT="$(pick_pg_port "${RAG_PG_CONTAINER}" ${RAG_PG_CANDIDATES[@]+${RAG_PG_CANDIDATES[@]}})" \
  || die "RAG 侧 PG 起不来（候选：${RAG_PG_CANDIDATES[*]}）"
say "   ✓ 两侧 Postgres 就绪（Go :${GO_PG_PORT} · RAG :${RAG_PG_PORT}）"

# ══════════════════════════ 2. Go 侧：构建 + 真源 DDL ══════════════════════════
step "2/9 构建 Go 侧二进制（真源**只读** ⇒ 产物落临时目录）"
( cd "${GO_REPO}" && CGO_ENABLED=0 GOPROXY=off GOTOOLCHAIN=local \
    go build -o "${WORK}/" ./service/article/api ./service/article/rpc ) || die "Go 侧构建失败"
[ -x "${WORK}/api" ] && [ -x "${WORK}/rpc" ] || die "Go 侧二进制没产出"
say "   ✓ api/rpc 构建完成"
# ⚠️ 真源 DDL：必须**先**跑 002_community_revision.sql，否则真源 rpc 启动即 fatal
apply_go_ddl() {
  local f
  for f in article.sql 002_community_revision.sql; do
    docker exec -i "${GO_PG_CONTAINER}" psql -U admin -d first_db -v ON_ERROR_STOP=1 \
      < "${GO_REPO}/service/article/rpc/internal/model/${f}" >/dev/null || die "真源 ${f} 执行失败"
  done
}
apply_go_ddl
say "   ✓ 真源 DDL 已灌（article.sql + 002_community_revision.sql）"

# ══════════════════════════ 3. Go 侧配置副本（真源一个字都不改）═══════════════════
step "3/9 生成 Go 侧配置副本"
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
# ⚠️ 逐条断言「改了哪项」——缩进写错的 sed 会**静默不命中** ⇒ 副本里仍是旧值 ⇒ 探针假挂。
grep -q "^Port: ${GO_API_PORT}$"                  "${WORK}/etc/article-api.yaml" || die "副本 article-api 的 Port 没改成 ${GO_API_PORT}（sed 没命中？）"
grep -q "^ListenOn: 0.0.0.0:${GO_RPC_PORT}$"      "${WORK}/etc/article.yaml"     || die "副本 article-rpc 的 ListenOn 没改成 ${GO_RPC_PORT}（sed 没命中？）"
grep -q "^  Port: \"${GO_PG_PORT}\"$"             "${WORK}/etc/article.yaml"     || die "副本 article-rpc 的 Postgres Port 没改成 ${GO_PG_PORT}（sed 没命中？）"
grep -q "^  Port: ${GO_PROM_PORT}$"               "${WORK}/etc/article.yaml"     || die "副本 article-rpc 的 Prometheus Port 没改成 ${GO_PROM_PORT}（sed 没命中？）"
grep -q "^  Host: 127.0.0.1$"                     "${WORK}/etc/article.yaml"     || die "副本 article-rpc 的 Postgres Host 没改成 127.0.0.1（sed 没命中？）"
grep -q "^  Password: \"sea\"$"                   "${WORK}/etc/article.yaml"     || die "副本 article-rpc 的 Postgres 口令没改成字面 sea（Go 的 conf.MustLoad **不**展开 \${POSTGRES_PASSWORD}！）"
grep -q "127.0.0.1:${KAFKA_PORT}"                 "${WORK}/etc/article.yaml"     || die "副本 article-rpc 的 kafka 没改（sed 没命中？）"
grep -q "127.0.0.1:${MINIO_PORT}"                 "${WORK}/etc/article.yaml"     || die "副本 article-rpc 的 minio 没改（sed 没命中？）"
[ "${GO_PROM_PORT}" != "${GO_RPC_PORT}" ] || die "Prometheus 端口与 gRPC ListenOn 撞了（=${GO_RPC_PORT}）⇒ 必 panic: address already in use"
grep -q "etcd:2379" "${WORK}/etc/article.yaml"     && die "副本里还残留 etcd:2379（sed 没命中？）"
grep -q "etcd:2379" "${WORK}/etc/article-api.yaml" && die "副本里还残留 etcd:2379（sed 没命中？）"
( cd "${GO_REPO}" && git status --porcelain -- service/article | grep -q . ) \
  && die "真源仓 service/article 有改动 ⇒ 本探针**不得**改真源"
say "   ✓ 配置副本已生成（真源 service/article 仍然干净）"

# ══════════════════════════ 4. 起 Go 侧栈 ══════════════════════════
step "4/9 起 Go 侧 article-rpc（注册 etcd）+ article-api（:${GO_API_PORT}）"
docker exec Sea-etcd etcdctl --endpoints="${ETCD_CTL_ENDPOINT}" del "article.rpc" --prefix >/dev/null 2>&1
# ⚠️ **必须用 `exec`** —— 否则 `$!` 是子 shell 的 PID ⇒ cleanup 杀不掉真正的二进制 ⇒ 端口残留。
( cd "${WORK}" && exec "${WORK}/rpc" -f "${WORK}/etc/article.yaml" >"${WORK}/go-rpc.log" 2>&1 ) &
GO_PIDS+=($!)
for _ in $(seq 1 40); do etcd_has_key article.rpc && break; sleep 1; done
etcd_has_key article.rpc || die "Go article-rpc 40s 内没注册进 etcd（见 go-rpc.log）"
# 🔴 同一条纪律：**注册值里必须带本轮的 gRPC 端口** ⇒ 排除「上一轮残留键」自匹配
#    （本脚本已在起 Go rpc 前 `del "article.rpc" --prefix`，此处再加一道 ⇒ 双保险）
case "$(etcd_get_value article.rpc)" in
  *":${GO_RPC_PORT}"*) : ;;
  *) die "etcd 上 article.rpc 的值不是本轮端口 :${GO_RPC_PORT}（= $(etcd_get_value article.rpc)）⇒ 残留键自匹配" ;;
esac
# ⚠️ go-zero **先注册 etcd 再 bind 端口** ⇒ 「etcd 有键」≠「端口已监听」⇒ 必须再等一次端口。
for _ in $(seq 1 20); do nc -z -G 1 127.0.0.1 "${GO_RPC_PORT}" && break; sleep 1; done
nc -z -G 2 127.0.0.1 "${GO_RPC_PORT}" || die "Go article-rpc 注册了但端口没监听（见 go-rpc.log）"
say "   ✓ Go article-rpc 已注册 article.rpc（:${GO_RPC_PORT} 已监听）"
( cd "${WORK}" && exec "${WORK}/api" -f "${WORK}/etc/article-api.yaml" >"${WORK}/go-api.log" 2>&1 ) &
GO_PIDS+=($!)
for _ in $(seq 1 40); do nc -z -G 1 127.0.0.1 "${GO_API_PORT}" && break; sleep 1; done
nc -z -G 2 127.0.0.1 "${GO_API_PORT}" || die "Go article-api 没起来（见 go-api.log）"
say "   ✓ Go 侧已监听 :${GO_API_PORT}"

# ══════════════════════════ 5. 起 RAG 侧栈 ══════════════════════════
step "5/9 构建 + 起 RAG 侧 article-rpc + article-api"
( cd "${RAG_ROOT}" && mvn -o -q -pl service/article/article-rpc,service/article/article-api -am package -DskipTests ) \
  || die "RAG 侧构建失败"
RAG_RPC_JAR="${RAG_ROOT}/service/article/article-rpc/target/article-rpc-0.0.1-SNAPSHOT.jar"
RAG_API_JAR="${RAG_ROOT}/service/article/article-api/target/article-api-0.0.1-SNAPSHOT.jar"
[ -f "${RAG_RPC_JAR}" ] && [ -f "${RAG_API_JAR}" ] || die "RAG 侧 jar 没产出"
nacos_deregister_all article-rpc
# 🔴 **启动前必须确认「本轮端口上的残留实例」真的清干净了**（否则下面的就绪判据会自匹配）
for _ in $(seq 1 30); do
  [ "$(nacos_instance_count "${RAG_RPC_PORT}")" = "0" ] && break
  sleep 1
done
[ "$(nacos_instance_count "${RAG_RPC_PORT}")" = "0" ] \
  || die "Nacos 上 :${RAG_RPC_PORT} 的残留实例清不掉 ⇒ 就绪判据会自匹配（本轮必然假失败）"
# ⚠️ RAG 侧全部走**环境变量**（与 run.sh 已验证的启动方式一致）——
#    `--grpc.client.article-rpc.address` 这类 CLI 覆盖**不要**用：
#    组件默认走 `discovery:///article-rpc` + Nacos（application.yml:69-73）⇒ 覆盖它反而偏离被测形态。
POSTGRES_HOST=127.0.0.1 POSTGRES_PORT="${RAG_PG_PORT}" POSTGRES_DB=first_db \
POSTGRES_USER=admin POSTGRES_PASSWORD=sea \
KAFKA_BROKERS="127.0.0.1:${KAFKA_PORT}" NACOS_ADDR="127.0.0.1:${NACOS_PORT}" \
MINIO_ENDPOINT="127.0.0.1:${MINIO_PORT}" ARTICLE_RPC_PORT="${RAG_RPC_PORT}" \
ARTICLE_RPC_MANAGEMENT_PORT="${RAG_RPC_MGMT_PORT}" \
  java -jar "${RAG_RPC_JAR}" >"${WORK}/rag-rpc.log" 2>&1 &
RAG_PIDS+=($!)
for _ in $(seq 1 90); do
  if [ "$(nacos_instance_count "${RAG_RPC_PORT}")" != "0" ] && nc -z -G 1 127.0.0.1 "${RAG_RPC_PORT}"; then break; fi
  sleep 1
done
# ⚠️ **两个条件都要**：注册了**且**端口在监听（devh 的 gRPC 端口与 Nacos 注册是两条独立时序）
[ "$(nacos_instance_count "${RAG_RPC_PORT}")" != "0" ] \
  || die "RAG article-rpc 90s 内没以端口 ${RAG_RPC_PORT} 注册进 Nacos（见 rag-rpc.log · 日志行数 $(wc -l < "${WORK}/rag-rpc.log" 2>/dev/null || echo 0)）"
nc -z -G 2 127.0.0.1 "${RAG_RPC_PORT}" \
  || die "RAG article-rpc 注册了 :${RAG_RPC_PORT} 但端口没监听（见 rag-rpc.log · 日志行数 $(wc -l < "${WORK}/rag-rpc.log" 2>/dev/null || echo 0)）"
say "   ✓ RAG article-rpc 已注册 Nacos（端口 ${RAG_RPC_PORT}）· :${RAG_RPC_PORT} 已监听"
NACOS_ADDR="127.0.0.1:${NACOS_PORT}" ARTICLE_API_PORT="${RAG_API_PORT}" USER_JWT_SECRET="${JWT_SECRET}" \
  java -jar "${RAG_API_JAR}" >"${WORK}/rag-api.log" 2>&1 &
RAG_PIDS+=($!)
for _ in $(seq 1 60); do nc -z -G 1 127.0.0.1 "${RAG_API_PORT}" && break; sleep 1; done
nc -z -G 2 127.0.0.1 "${RAG_API_PORT}" || die "RAG article-api 没起来（见 rag-api.log）"
say "   ✓ RAG 侧已监听 :${RAG_API_PORT}"

# ══════════════════════════ 6. 两侧灌同一份种子 ══════════════════════════
step "6/9 两侧灌种子（article 行 + revision 行 + publication 指针 ⇒ 走 revision 分支）"
# ⚠️ article 行与 revision 行的 title/brief/cover/manual_type_tag/content/updated_at
#    **故意填成不同值** ⇒ 响应里出现 revision 的值 ⇒ 可证明确实走了 revision 分支。
# ⚠️ article.content 是**不存在的 MinIO key** ⇒ 若代码误走 legacy 分支 ⇒ 必 5001
#    ⇒ 这条种子本身就是「有没有误走 legacy」的判据。
SEED_SQL="INSERT INTO article (id,title,brief,content,cover_image_url,manual_type_tag,secondary_tags,author_id,status,view_count,like_count,comment_count,share_count,ext_info,created_at,updated_at)
VALUES ('${AID}','旧行标题（旧 article 行）','旧行摘要','object-success/articles/${AID}.md','https://example.invalid/old.png','golang','[]','1001',2,1,2,3,4,'{}','2026-01-02 03:04:05+00','2026-01-02 03:04:05+00');
INSERT INTO article_revision (article_id,revision,revision_id,author_id,source_object,content_sha256,title,brief,cover_image_url,manual_type_tag,secondary_tags,markdown,published_at,sync_event_id)
VALUES ('${AID}',1,'${REV}','1001','object-success/articles/${AID}.md',repeat('a',64),'成功路径标题','成功路径摘要','https://example.invalid/rev.png','tech','[]','# 成功路径正文','2026-01-02 09:04:05+00','evt-succ-0001');
INSERT INTO article_publication (article_id,current_revision,pointer_version,state,last_event_id,updated_at)
VALUES ('${AID}','${REV}',1,'published','evt-succ-0001','2026-01-02 09:04:05+00');"
for c in "${GO_PG_CONTAINER}" "${RAG_PG_CONTAINER}"; do
  docker exec -i "$c" psql -U admin -d first_db -v ON_ERROR_STOP=1 <<SQL >/dev/null || die "灌种子失败：${c}"
${SEED_SQL}
SQL
done
say "   ✓ 两侧同一行（${AID}）＋ revision 指针（${REV}）"

# ══════════════════════════ 7. 请求矩阵 ══════════════════════════
step "7/9 就绪闸门 + 请求矩阵（Go vs RAG vs 冻结期望值 ⇒ 逐字节）"
wait_ready "${GO_API_PORT}"  "Go  侧" || die "Go 侧没就绪（见日志）"
wait_ready "${RAG_API_PORT}" "RAG 侧" || die "RAG 侧没就绪（见日志）"

FAILED=0
# ⚠️ FREEZE 模式：先把期望值文件**截断**再逐条追加（否则多轮追加会留重复行）。
if [ "${FREEZE:-0}" = "1" ]; then
  : > "${EXPECTED}"
  say "   ⓘ FREEZE=1 ⇒ 已清空 ${EXPECTED}（本轮将逐条追加）"
fi
run_case() {
  local name="$1" path="$2"
  local g r gh rh eh
  g=$(curl -s -m 20 "http://127.0.0.1:${GO_API_PORT}${path}")
  r=$(curl -s -m 20 "http://127.0.0.1:${RAG_API_PORT}${path}")
  gh=$(printf '%s' "$g" | xxd -p | tr -d '\n')
  rh=$(printf '%s' "$r" | xxd -p | tr -d '\n')
  if [ "${FREEZE:-0}" = "1" ]; then
    printf 'CASE|%s|%s\n' "${name}" "${gh}" >> "${EXPECTED}"
    say "   ⓘ FREEZE=1 ⇒ 已把 Go 侧输出追加进 ${EXPECTED}"
  else
    eh=$(awk -F'|' -v n="CASE|${name}" '$1"|"$2==n {print $3}' "${EXPECTED}")
    [ -n "${eh}" ] || { say "❌ ${name}：${EXPECTED} 里没有这一条（先跑一次 FREEZE=1 冻结）"; FAILED=1; return; }
    if [ "${gh}" = "${eh}" ]; then
      say "   ✅ ${name}  Go == 冻结期望值（逐字节）"
    else
      FAILED=1
      say "   ❌ ${name}  Go != 冻结期望值"
      say "      Go : ${g}"
    fi
  fi
  if [ "${g}" = "${r}" ]; then
    say "   ✅ ${name}  Go == RAG（逐字节）"
  else
    FAILED=1
    say "   ❌ ${name}  Go != RAG"
    say "      Go : ${g}"
    say "      RAG: ${r}"
    say "      Go hex: ${gh}"
    say "      RAG hex: ${rh}"
  fi
}
run_case "get_article_success"   "/v1/article/${AID}"
run_case "list_articles_success" "/v1/articles"

# ══════════════════════════ 8. 「确实走了 revision 分支」自检 ══════════════════════════
step "8/9 自检：响应里出现的是 revision 的字段（4 个独立信号）"
G_BODY="$(curl -s -m 20 "http://127.0.0.1:${GO_API_PORT}/v1/article/${AID}")"
R_BODY="$(curl -s -m 20 "http://127.0.0.1:${RAG_API_PORT}/v1/article/${AID}")"
G_BODY="${G_BODY}" R_BODY="${R_BODY}" AID="${AID}" REV="${REV}" \
python3 - <<'PY' || FAILED=1
import json, os, sys, datetime

# ⚠️ 偏移写成 `+00:00`（Python < 3.11 的 `fromisoformat` 不接受 `+00`）
rev_ms = int(datetime.datetime.fromisoformat("2026-01-02 09:04:05+00:00").timestamp() * 1000)
art_updated_ms = int(datetime.datetime.fromisoformat("2026-01-02 03:04:05+00:00").timestamp() * 1000)

ok = True
def check(tag, cond, detail):
    global ok
    if cond:
        print(f"   ✅ {tag}")
    else:
        ok = False
        print(f"   ❌ {tag} —— {detail}")

for side, raw in (("Go ", os.environ["G_BODY"]), ("RAG", os.environ["R_BODY"])):
    try:
        d = json.loads(raw)
    except Exception as e:
        check(f"{side} 响应是合法 JSON", False, f"{e}: {raw[:200]}")
        continue
    a = (d.get("data") or {}).get("article") or {}
    check(f"{side} S1 ext_info=published_revision_id",
          a.get("ext_info") == {"published_revision_id": os.environ["REV"]},
          f"ext_info={a.get('ext_info')}")
    check(f"{side} S2 update_time=revision.published_at（≠ article.updated_at）",
          a.get("update_time") == rev_ms and rev_ms != art_updated_ms,
          f"update_time={a.get('update_time')} 期望={rev_ms}（article.updated_at={art_updated_ms}）")
    check(f"{side} S3 content=revision.markdown（≠ article.content）",
          a.get("content") == "# 成功路径正文",
          f"content={a.get('content')!r}")
    check(f"{side} S4 title/brief/cover/manual_type_tag = revision 的值",
          a.get("title") == "成功路径标题" and a.get("brief") == "成功路径摘要"
          and a.get("cover_image_url") == "https://example.invalid/rev.png"
          and a.get("manual_type_tag") == "tech",
          f"title={a.get('title')!r} brief={a.get('brief')!r} "
          f"cover={a.get('cover_image_url')!r} tag={a.get('manual_type_tag')!r}")

sys.exit(0 if ok else 1)
PY

# ══════════════════════════ 9. 结论 ══════════════════════════
echo
if [ "${FAILED}" = "0" ]; then
  say "✅ 详情端点**成功路径**双侧逐字节一致（正文来自 revision ⇒ 未读 MinIO）"
  say "   ⇒ 4 个 revision 分支信号全部通过 ⇒ 确实没走 legacy 分支。"
  exit 0
fi
say "❌ 详情端点成功路径存在差异或自检未通过"
dump_logs
exit 1
