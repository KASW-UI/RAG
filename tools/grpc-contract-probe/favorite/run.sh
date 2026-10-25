#!/usr/bin/env bash
# ============================================================================
# gRPC 级可观测契约 · **双侧活体对拍**探针 · favorite 域
# ----------------------------------------------------------------------------
# 目的：把台账里那条「🔴 未验：帧层（HTTP2 / length-prefix / gRPC status 帧）」，
#      拆成两半并分别给出结论：
#
#   ① **帧层原始字节**（HPACK 编码 / 帧序 / WINDOW_UPDATE / PING）
#      ⇒ **库级差异，不比对**（grpc-go vs grpc-java 必然不同，且客户端不可观测）
#   ② **帧层之上、消息层之下的可观测契约**
#      （gRPC status code / description / 响应消息字节 / metadata key 集合）
#      ⇒ **本域该管** ⇒ 本探针做双侧活体对拍
#
# 为什么现在能做（2026-10-25 第十三轮推翻了旧的环境受限判定）：
#   · Go 侧 favorite-rpc **不需要 etcd**：
#       zrpc/config.go:92-114 BuildTarget() 优先级 = Endpoints > Target > Etcd
#       ⇒ UserRpc / ArticleRpc 填 Endpoints 即完全绕开 etcd
#       zrpc/server.go:42-43  if c.HasEtcd()  ⇒ Etcd.Key 留空就不注册 etcd
#   · zrpc.MustNewClient 默认 NonBlock=true ⇒ **启动不需要对方在线**
#   · Go 侧 favorite-rpc 的 7 个 RPC 里**只有 helper.go** 引用 UserRpc / ArticleRpc
#       ⇒ 6 个 RPC 不需要任何外部 rpc
#   · 本探针**故意不接** user-rpc / article-rpc ⇒ 走的是「下游不可用」这条契约分支
#
# 两侧怎么跑：
#   · Go 侧 = **真源**的 favorite.go / pb / logic / model / common（42 个逐字节复制件，
#            run.sh 逐个 sha256 校验）+ 真源自己的 zrpc.MustNewServer ⇒ **真 gRPC server**
#            + **真 PG**（sql-first 部署顺序）
#   · RAG 侧 = favorite-rpc 的**真 Spring 上下文**（devh 真 netty gRPC server）
#            + 真 Flyway 迁移 + 真 MyBatis + **真 PG**
#   · **同一个 grpc-java 客户端**分别打两侧 ⇒ 消掉客户端自身差异
#
# 用法：
#   bash tools/grpc-contract-probe/favorite/run.sh
#   GO_REPO=/path/to/Sea-RideTheWind_golang bash tools/grpc-contract-probe/favorite/run.sh
#
# 退出码：0 = 契约一致；1 = 有差异；2 = 环境问题 / 真源漂移。
# ============================================================================
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAG_ROOT="$(cd "${HERE}/../../.." && pwd)"

say() { printf '\033[1;36m[grpc-contract-probe]\033[0m %s\n' "$*"; }
ok()  { printf '\033[1;32m  ✅ %s\033[0m\n' "$*"; }
bad() { printf '\033[1;31m  ❌ %s\033[0m\n' "$*"; }
die() { bad "$*"; exit 2; }

# ── 0. 定位 Go 真源 ────────────────────────────────────────────────────────
default_go_repo() {
  local d
  d="$(cd "${RAG_ROOT}" && pwd)"
  for _ in 1 2 3 4 5 6; do
    if [ -f "${d}/Sea-RideTheWind_golang/service/favorite/rpc/internal/model/db.go" ]; then
      (cd "${d}/Sea-RideTheWind_golang" && pwd); return 0
    fi
    d="$(dirname "${d}")"
  done
  return 1
}
GO_REPO="${GO_REPO:-$(default_go_repo || true)}"
[ -n "${GO_REPO}" ] || die "找不到 Go 真源（向上 6 层内没有 Sea-RideTheWind_golang）—— 用 GO_REPO=... 指定"
[ -f "${GO_REPO}/go.mod" ] || die "Go 真源没有 go.mod：${GO_REPO}/go.mod"
say "Go 真源：${GO_REPO}"

command -v go >/dev/null 2>&1 || die "没装 go toolchain"
command -v mvn >/dev/null 2>&1 || die "没装 mvn"
command -v docker >/dev/null 2>&1 || die "没装 docker（探针需要真 PG）"

TMP="$(mktemp -d)"
PG_CONTAINER="fav-grpc-contract-probe-pg-$$"
GO_PID=""
cleanup() {
  [ -n "${GO_PID}" ] && kill "${GO_PID}" >/dev/null 2>&1
  docker rm -f "${PG_CONTAINER}" >/dev/null 2>&1 || true
  # KEEP_TMP=1 ⇒ 保留临时目录（排查用：go-server.log / schemaprep.log / mvn.log）
  if [ "${KEEP_TMP:-0}" = "1" ]; then
    say "KEEP_TMP=1 ⇒ 临时目录保留在 ${TMP}"
  else
    rm -rf "${TMP}"
  fi
}
trap cleanup EXIT

# ── 1. 校验真源复制件的 sha256（防真源漂移后探针还按老副本跑）──────────────
say "校验真源复制件 sha256"
MISMATCH=0
COPY_LIST="${HERE}/go/filelist.txt"
[ -f "${COPY_LIST}" ] || die "缺复制件清单：${COPY_LIST}"
while IFS= read -r rel; do
  [ -z "${rel}" ] && continue
  case "${rel}" in \#*) continue ;; esac
  src="${GO_REPO}/${rel}"
  dst="${HERE}/go/${rel}"
  [ -f "${src}" ] || die "真源缺文件：${rel}"
  [ -f "${dst}" ] || die "复制件缺文件：${rel}"
  a="$(shasum -a 256 "${src}" | awk '{print $1}')"
  b="$(shasum -a 256 "${dst}" | awk '{print $1}')"
  if [ "${a}" != "${b}" ]; then
    bad "复制件与真源不一致：${rel}"
    MISMATCH=1
  fi
done < "${COPY_LIST}"
[ "${MISMATCH}" -eq 0 ] || die "复制件与真源不一致 ⇒ 请重新复制（不要手改复制件）"
COPY_COUNT="$(grep -cvE '^\s*(#|$)' "${COPY_LIST}")"
ok "${COPY_COUNT} 个复制件与真源逐字节一致"

# ── 2. 组装临时 module（module 名沿用真源的 sea-try-go ⇒ import 路径零改动）──
say "组装临时 module（module=sea-try-go，go.mod/go.sum 整体继承真源）"
mkdir -p "${TMP}/src"
cp -R "${HERE}/go/service" "${TMP}/src/"
cp "${GO_REPO}/go.mod" "${GO_REPO}/go.sum" "${TMP}/src/"
GOZERO_VER="$(awk '/^[[:space:]]*github\.com\/zeromicro\/go-zero v/ {print $2; exit}' "${GO_REPO}/go.mod")"
say "   真源 go.mod：go-zero ${GOZERO_VER}（不写死版本、不 tidy）"

# ── 3. 起真 PG（两个库：godb = Go 侧 / first_db = RAG 侧）──────────────────
say "起 postgres:16-alpine 容器：${PG_CONTAINER}"
docker run -d --rm --name "${PG_CONTAINER}" \
  -e POSTGRES_PASSWORD=probe -e POSTGRES_USER=postgres \
  -p 0:5432 postgres:16-alpine >/dev/null || die "容器启动失败"
PG_PORT="$(docker port "${PG_CONTAINER}" 5432/tcp | head -1 | sed 's/.*://')"
[ -n "${PG_PORT}" ] || die "拿不到 PG 映射端口"
# ⚠️ 必须探 TCP（-h 127.0.0.1）：PG 官方镜像初始化阶段的临时 server 只监听 unix socket
for _ in $(seq 1 60); do
  docker exec "${PG_CONTAINER}" pg_isready -U postgres -h 127.0.0.1 >/dev/null 2>&1 && break
  sleep 1
done
docker exec "${PG_CONTAINER}" pg_isready -U postgres -h 127.0.0.1 >/dev/null 2>&1 || die "PG 没就绪"
for db in godb first_db; do
  ok_db=0
  for _ in $(seq 1 15); do
    if docker exec "${PG_CONTAINER}" psql -U postgres -h 127.0.0.1 -c "CREATE DATABASE ${db};" >/dev/null 2>&1; then
      ok_db=1; break
    fi
    sleep 2
  done
  [ "${ok_db}" = "1" ] || die "建库 ${db} 失败"
done
ok "PG 就绪（127.0.0.1:${PG_PORT}）· 两个库：godb（Go 侧）/ first_db（RAG 侧）"

# ── 4. Go 侧：sql-first 建表（真源文档指定的部署顺序）──────────────────────
say "Go 侧建表（sql-first：AutoMigrate(老表) → 真源 001..004 → 应用启动时 InitDB）"
( cd "${TMP}/src" && CGO_ENABLED=0 GOPROXY=off GOFLAGS=-mod=mod GOTOOLCHAIN=local \
    PG_HOST=127.0.0.1 PG_PORT="${PG_PORT}" PG_USER=postgres PG_PASSWORD=probe \
    PG_DB=godb PG_SSLMODE=disable \
    SQL_DIR="${TMP}/src/service/favorite/rpc/internal/model" \
    go run ./service/favorite/rpc/cmd/schemaprep ) > "${TMP}/schemaprep.log" 2>&1 \
  || { bad "Go 侧建表失败"; tail -25 "${TMP}/schemaprep.log"; exit 2; }
ok "Go 侧 schema 就绪"

# ── 5. 起 Go 真源 favorite-rpc server ─────────────────────────────────────
GO_PORT="$(python3 -c 'import socket;s=socket.socket();s.bind(("127.0.0.1",0));print(s.getsockname()[1]);s.close()')"
RAG_PORT="$(python3 -c 'import socket;s=socket.socket();s.bind(("127.0.0.1",0));print(s.getsockname()[1]);s.close()')"
say "Go server 端口 ${GO_PORT} / RAG server 端口 ${RAG_PORT}"

cat > "${TMP}/probe.yaml" <<YAML
Name: favorite.rpc
ListenOn: 127.0.0.1:${GO_PORT}

Log:
  ServiceName: favorite-rpc
  Mode: console
  Encoding: plain

Postgres:
  Host: "127.0.0.1"
  Port: "${PG_PORT}"
  User: "postgres"
  Password: "probe"
  DBName: "godb"
  Mode: "disable"

# ⚠️ Endpoints 优先于 Etcd（zrpc/config.go:92-114 BuildTarget）⇒ 完全不需要 etcd
# ⚠️ 127.0.0.1:1 是**故意**的死地址：本探针要测「下游不可用」这条契约分支
UserRpc:
  Endpoints:
    - "127.0.0.1:1"

ArticleRpc:
  Endpoints:
    - "127.0.0.1:1"
YAML

say "编译并启动 Go 真源 favorite-rpc server"
( cd "${TMP}/src" && CGO_ENABLED=0 GOPROXY=off GOFLAGS=-mod=mod GOTOOLCHAIN=local \
    go build -o "${TMP}/favorite-rpc" ./service/favorite/rpc ) > "${TMP}/gobuild.log" 2>&1 \
  || { bad "Go 侧编译失败"; tail -30 "${TMP}/gobuild.log"; exit 2; }
"${TMP}/favorite-rpc" -f "${TMP}/probe.yaml" > "${TMP}/go-server.log" 2>&1 &
GO_PID=$!

# 等端口真的在监听（不能只看进程还在）
UP=0
for _ in $(seq 1 60); do
  if python3 -c "import socket,sys;s=socket.socket();s.settimeout(0.5);sys.exit(0 if s.connect_ex(('127.0.0.1',${GO_PORT}))==0 else 1)"; then
    UP=1; break
  fi
  kill -0 "${GO_PID}" 2>/dev/null || break
  sleep 1
done
[ "${UP}" = "1" ] || { bad "Go server 没起来"; tail -30 "${TMP}/go-server.log"; exit 2; }
ok "Go 真源 favorite-rpc server 已在 127.0.0.1:${GO_PORT} 监听"

# ── 6. 灌种子（Go 侧）────────────────────────────────────────────────────
say "灌种子到 Go 侧库（godb）"
docker exec -i "${PG_CONTAINER}" psql -U postgres -h 127.0.0.1 -d godb -v ON_ERROR_STOP=1 \
  -f - < "${HERE}/seed.sql" > "${TMP}/seed-go.log" 2>&1 \
  || { bad "Go 侧灌种子失败"; cat "${TMP}/seed-go.log"; exit 2; }
ok "Go 侧种子就绪"

# ── 7. 跑 Java 侧（RAG 真 server + 同一个 grpc-java 客户端双打）───────────
say "跑 Java 侧（真 Spring 上下文 + 真 Flyway + 真 MyBatis + 同一客户端双打）"
PROBE_GO_TARGET="127.0.0.1:${GO_PORT}" \
PROBE_RAG_TARGET="127.0.0.1:${RAG_PORT}" \
PROBE_RAG_PORT="${RAG_PORT}" \
PROBE_JDBC_URL="jdbc:postgresql://127.0.0.1:${PG_PORT}/first_db?TimeZone=Asia/Shanghai" \
PROBE_JDBC_USER="postgres" \
PROBE_JDBC_PASSWORD="probe" \
PROBE_SEED="${HERE}/seed.sql" \
PROBE_OUT_GO="${TMP}/go.txt" \
PROBE_OUT_RAG="${TMP}/rag.txt" \
PROBE_EXPECTED="${HERE}/expected.txt" \
  mvn -o -q -pl service/favorite/favorite-rpc -am test \
    -Dtest=FavoriteGrpcContractParityTest -DfailIfNoTests=false \
    -Dsurefire.failIfNoSpecifiedTests=false \
  > "${TMP}/mvn.log" 2>&1
MVN_RC=$?
if [ "${MVN_RC}" -ne 0 ]; then
  bad "Java 侧 FavoriteGrpcContractParityTest 失败（exit ${MVN_RC}）"
  grep -E 'ERROR|FAIL|expected|AssertionError' "${TMP}/mvn.log" | head -40
  echo "---- mvn.log 尾部 ----"
  tail -60 "${TMP}/mvn.log"
  exit 1
fi
[ -s "${TMP}/go.txt" ] || die "Java 侧没有产出 Go 侧结果文件"
[ -s "${TMP}/rag.txt" ] || die "Java 侧没有产出 RAG 侧结果文件"
ok "两侧各产出 $(wc -l < "${TMP}/go.txt" | tr -d ' ') 个 case"

# ── 8. 逐条比对 ───────────────────────────────────────────────────────────
say "逐条比对（Go 侧 vs 冻结值 vs RAG 侧）"
python3 - "${HERE}/expected.txt" "${TMP}/go.txt" "${TMP}/rag.txt" <<'PY'
import sys, os

exp_path, go_path, rag_path = sys.argv[1], sys.argv[2], sys.argv[3]

def load(p):
    out = {}
    if not os.path.exists(p):
        return out
    for line in open(p, encoding="utf-8"):
        line = line.rstrip("\n")
        if not line.strip():
            continue
        parts = line.split("\t", 1)
        out[parts[0]] = parts[1] if len(parts) > 1 else ""
    return out

go = load(go_path)
rag = load(rag_path)

if not os.path.exists(exp_path):
    print("  ⚠️ expected.txt 尚不存在 ⇒ 下面是首次运行的逐条实测（请人工分类成 SAME/OBS/SKIP 后冻结）")
    for cid in go:
        g = go.get(cid, "<缺>")
        r = rag.get(cid, "<缺>")
        mark = "SAME" if g == r else "DIFF"
        print(f"  [{mark}] {cid}")
        print(f"      Go : {g}")
        print(f"      RAG: {r}")
    sys.exit(0)

kinds = {}
frozen = {}
for line in open(exp_path, encoding="utf-8"):
    line = line.rstrip("\n")
    if not line.strip() or line.lstrip().startswith("#"):
        continue
    cols = line.split("\t", 2)
    if len(cols) < 3:
        print(f"❌ expected.txt 行格式错误：{line}")
        sys.exit(1)
    kinds[cols[0]] = cols[1]
    frozen[cols[0]] = cols[2]

problems = []
same = same_code = obs = skip = 0
for cid, kind in kinds.items():
    g, r = go.get(cid), rag.get(cid)
    if g is None or r is None:
        problems.append(f"  · {cid} —— 有一侧没跑这个 case")
        continue
    if kind == "SKIP":
        skip += 1
        continue
    if frozen[cid] != g:
        problems.append(f"  · {cid} —— Go 侧与冻结值不一致 ⇒ **Go 真源的 gRPC 契约变了**\n"
                       f"      冻结: {frozen[cid]}\n      实测: {g}")
        continue
    if kind == "OBS":
        obs += 1
        if g == r:
            problems.append(f"  · {cid} —— OBS 段期望两侧不同，但变成一样了 ⇒ 请重评台账")
        continue
    if kind == "SAME_CODE":
        same_code += 1
        def _code(v):
            for part in v.split("|"):
                if part.startswith("code="):
                    return part[len("code="):]
            return ""
        if _code(g) != _code(r):
            problems.append(f"  · {cid} —— 两侧 status code 不一致\n"
                           f"      Go : {g}\n      RAG: {r}")
        continue
    same += 1
    if g != r:
        problems.append(f"  · {cid} —— 两侧不一致\n      Go : {g}\n      RAG: {r}")

print(f"  SAME {same} 条 / SAME_CODE {same_code} 条 / OBS {obs} 条 / SKIP {skip} 条（共 {len(kinds)} 个 case）")
if problems:
    print("❌ 不一致：")
    print("\n".join(problems))
    sys.exit(1)
print("✅ gRPC 级可观测契约两侧一致（SAME 全等；OBS 仍不同；SKIP 已登记理由）")
PY
RC=$?
[ "${RC}" -eq 0 ] || exit 1

echo
ok "gRPC 契约双侧活体对拍通过"
echo "   证据边界见 tools/grpc-contract-probe/favorite/README.md"
exit 0
