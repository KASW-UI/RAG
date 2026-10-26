#!/usr/bin/env bash
# ============================================================================
# 权威读 HTTP 面 · **双侧活体对拍**探针 · favorite 域
# ----------------------------------------------------------------------------
# 目的：把「favorite.fact.authority 权威读端点的 HTTP 行为」从
#      「源码级推理 + RAG 侧单侧断言」升级为**两侧真代码 + 真 PG 的活体对拍**。
#
# 为什么以前做不了（2026-10-25 第十二轮推翻了这个判定）：
#   此前 favorite 域判「环境受限 ⇒ 拿不到双侧活体对拍」，理由是
#   「Go 侧 favorite-rpc 依赖 etcd + Kafka + Redis + article-rpc / user-rpc」。
#   ⚠️ **那个理由只对 gRPC 整域成立**，对**权威读 HTTP 面不成立**：
#     · Go  cmd/fact-authority/main.go:32-37 只要 PG + 端口 + token；
#     · RAG FavoriteAuthorityEndpointConfig 只要 PG；
#     · 两边的读取链路（AuthoritativeFavoriteFact / FavoriteAuthorityLookupService）
#       **只查 favorite_fact_outbox + favorite_item 两张表**
#       ⇒ 不碰 user-rpc / article-rpc ⇒ 不需要 etcd / Kafka / Redis。
#
# 两侧怎么跑：
#   · Go  侧 = **真源**的 internal/authority/http.go + internal/model/*.go
#              （逐字节复制件 + sha256 校验）+ **真源**的写入/投递路径生成种子
#              + **真 PG** + httptest recorder
#   · Java 侧 = **真** FavoriteAuthorityLookupService（真 MyBatis + 真 PG）
#              + **真** FavoriteAuthorityServlet + MockHttpServletRequest/Response
#
# 🔴 两侧共用**同一份** seed.sql ⇒ 读到**同一批行** ⇒ 响应体可逐字节比。
# 🔴 expected.txt = **Go 侧实测值**（冻结）⇒ 它同时是：
#     ① 「Go 真源变了探针立刻响」的哨兵（run.sh 逐行比对 Go 输出）；
#     ② Java 侧断言的唯一真源（FavoriteAuthorityLiveParityTest 读它）。
#
# 用法：
#   bash tools/authority-live-probe/favorite/run.sh
#   GO_REPO=/path/to/Sea-RideTheWind_golang bash tools/authority-live-probe/favorite/run.sh
#
# 退出码：0 = 两侧逐字节一致；1 = 有差异；2 = 环境问题 / 真源漂移。
# ============================================================================
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAG_ROOT="$(cd "${HERE}/../../.." && pwd)"

say() { printf '\033[1;36m[auth-live-probe]\033[0m %s\n' "$*"; }
ok()  { printf '\033[1;32m  ✅ %s\033[0m\n' "$*"; }
bad() { printf '\033[1;31m  ❌ %s\033[0m\n' "$*"; }
die() { bad "$*"; exit 2; }

# ── 0. 定位 Go 真源 ────────────────────────────────────────────────────────
default_go_repo() {
  local d
  d="$(cd "${RAG_ROOT}" && pwd)"
  for _ in 1 2 3 4 5 6; do
    if [ -f "${d}/Sea-RideTheWind_golang/service/favorite/rpc/internal/authority/http.go" ]; then
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

TMP="$(mktemp -d)"
PG_CONTAINER="fav-auth-live-probe-pg-$$"
cleanup() {
  docker rm -f "${PG_CONTAINER}" >/dev/null 2>&1 || true
  rm -rf "${TMP}"
}
trap cleanup EXIT

# ── 1. 校验真源复制件的 sha256（防真源漂移后探针还按老副本跑）──────────────
say "校验真源复制件 sha256（13 个文件）"
MISMATCH=0
while IFS= read -r rel; do
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
done <<'EOF'
service/favorite/rpc/internal/authority/http.go
service/favorite/rpc/internal/model/data_model.go
service/favorite/rpc/internal/model/db.go
service/favorite/rpc/internal/model/favorite.go
service/favorite/rpc/internal/model/favorite_authority.go
service/favorite/rpc/internal/model/favorite_delivery.go
service/favorite/rpc/internal/model/favorite_fact.go
service/favorite/rpc/internal/model/favorite_legacy_preflight.go
service/favorite/rpc/internal/model/vars.go
service/favorite/rpc/internal/model/001_favorite_fact_outbox.sql
service/favorite/rpc/internal/model/002_favorite_fact_delivery.sql
service/favorite/rpc/internal/model/003_favorite_target_revision.sql
service/favorite/rpc/internal/model/004_favorite_legacy_fact_marker.sql
EOF
[ "${MISMATCH}" -eq 0 ] || die "复制件与真源不一致 ⇒ 请重新复制（不要手改复制件）"
ok "13 个复制件与真源逐字节一致"

# ── 2. 组装临时 module ────────────────────────────────────────────────────
# ⚠️ module 名**就用真源的 sea-try-go** ⇒ 复制件的 import 路径**一个字都不用改**
#    ⇒ internal 包的可见性规则仍然成立（探针 main 位于 sea-try-go/service/favorite/rpc/cmd/probe/）
#    ⇒ 「逐字节复制 + sha256 校验」这个纪律才成立。
say "组装临时 module（module=sea-try-go，go.mod/go.sum 整体继承真源）"
mkdir -p "${TMP}/src"
cp -R "${HERE}/go/service" "${TMP}/src/"
cp "${GO_REPO}/go.mod" "${GO_REPO}/go.sum" "${TMP}/src/"
GOZERO_VER="$(awk '/^[[:space:]]*github\.com\/zeromicro\/go-zero v/ {print $2; exit}' "${GO_REPO}/go.mod")"
GORM_VER="$(awk '/^[[:space:]]*gorm\.io\/gorm v/ {print $2; exit}' "${GO_REPO}/go.mod")"
say "   真源 go.mod：go-zero ${GOZERO_VER} / gorm ${GORM_VER}（不写死版本、不 tidy）"

# ── 3. 起真 PG ────────────────────────────────────────────────────────────
command -v docker >/dev/null 2>&1 || die "没装 docker（探针需要真 PG）"
say "起 postgres:16-alpine 容器：${PG_CONTAINER}"
# ⚠️ PG 就绪等待三件套（2026-10-25 第十四轮修 —— 批跑时机器高负载 ⇒ **假红**）：
#   ① 必须探 **TCP**（-h 127.0.0.1）：PG 官方镜像初始化阶段只起一个**临时 server**，
#      它只监听 unix socket（listen_addresses=''）⇒ 走 socket 探活会**假就绪**。
#      本探针原先就是探 socket（`pg_isready -U postgres` 没带 -h）⇒ 已修。
#   ② 上限 **180s**（不是 40s）：机器高负载时 initdb 本身就要几十秒。
#   ③ 没就绪就**重建容器重来一次**：容器起不来（资源竞争 / 端口映射失败）时重来更稳。
wait_pg_tcp() {
  local tries="$1" i
  for i in $(seq 1 "${tries}"); do
    docker exec "${PG_CONTAINER}" pg_isready -U postgres -h 127.0.0.1 >/dev/null 2>&1 && return 0
    # 容器自己退出了就别白等满超时（--rm ⇒ 退出会被删掉 ⇒ inspect 失败）
    [ "$(docker inspect -f '{{.State.Running}}' "${PG_CONTAINER}" 2>/dev/null || echo false)" = "true" ] || return 1
    sleep 1
  done
  return 1
}
PG_PORT=""
PG_READY=0
for attempt in 1 2; do
  docker rm -f "${PG_CONTAINER}" >/dev/null 2>&1 || true
  if ! docker run -d --rm --name "${PG_CONTAINER}" \
      -e POSTGRES_PASSWORD=probe -e POSTGRES_USER=postgres \
      -p 0:5432 postgres:16-alpine >/dev/null; then
    say "   PG 容器第 ${attempt} 次启动失败 ⇒ 重来"
    continue
  fi
  PG_PORT="$(docker port "${PG_CONTAINER}" 5432/tcp | head -1 | sed 's/.*://')"
  if [ -z "${PG_PORT}" ]; then
    say "   第 ${attempt} 次拿不到 PG 映射端口 ⇒ 重来"
    continue
  fi
  if wait_pg_tcp 180; then PG_READY=1; break; fi
  say "   PG 第 ${attempt} 次 180s 内没就绪 ⇒ 重建容器重来"
done
[ "${PG_READY}" = "1" ] || die "PG 没起来（已重建容器重试 1 次）"
docker exec "${PG_CONTAINER}" psql -U postgres -h 127.0.0.1 -c "CREATE DATABASE godb;" >/dev/null || die "建库失败"
ok "PG 就绪（127.0.0.1:${PG_PORT} / godb）"

# ── 4. Go 侧：建 Go schema + 应用冻结种子 + 跑矩阵 ────────────────────────
say "跑 Go 侧（真 authority handler + 真 model + 真 PG）"
GO_OUT="${TMP}/go.txt"
( cd "${TMP}/src/service/favorite/rpc/cmd/probe" && \
  PG_HOST=127.0.0.1 PG_PORT="${PG_PORT}" PG_USER=postgres PG_PASSWORD=probe \
  PG_DB=godb PG_SSLMODE=disable \
  CGO_ENABLED=0 GOPROXY=off GOFLAGS=-mod=mod GOTOOLCHAIN=local \
  PROBE_SEED_IN="${HERE}/seed.sql" PROBE_OUT="${GO_OUT}" \
  go run . ) || die "Go 侧执行失败（见上方输出）"
[ -s "${GO_OUT}" ] || die "Go 侧没有产出结果文件"
ok "Go 侧产出 $(wc -l < "${GO_OUT}" | tr -d ' ') 个 case"

# ── 5. Go 侧输出 vs 冻结的 expected.txt（Go 真源变了 ⇒ 这里立刻响）────────
say "Go 侧输出 vs 冻结的 expected.txt（守「Go 真源变了探针立刻响」）"
# ⚠️ 两边列数不同：Go 输出是 2 列（case_id\tvalue），expected.txt 是 3 列（case_id\tkind\tvalue）
awk -F'\t' '{print $1"\t"$2}' "${GO_OUT}" > "${TMP}/go-flat.txt"
grep -vE '^\s*(#|$)' "${HERE}/expected.txt" | awk -F'\t' '{print $1"\t"$3}' > "${TMP}/exp-flat.txt"
if diff -u "${TMP}/exp-flat.txt" "${TMP}/go-flat.txt" > "${TMP}/go-diff.txt"; then
  ok "Go 侧输出与 expected.txt 逐字节一致（sha256=$(shasum -a 256 "${GO_OUT}" | awk '{print $1}' | cut -c1-16)…）"
else
  bad "Go 侧输出与 expected.txt 不一致 ⇒ **Go 真源的权威读行为变了**（- = 冻结值 / + = 实测）："
  cat "${TMP}/go-diff.txt"
  exit 1
fi

# ── 6. Java 侧：真 PG + 真 MyBatis + 真 servlet + 同一份 seed ─────────────
say "跑 Java 侧（真 PG + 真 MyBatis + 真 servlet + 同一份 seed.sql）"
RAG_OUT="${TMP}/rag.txt"
( cd "${RAG_ROOT}" && PROBE_OUT="${RAG_OUT}" PROBE_EXPECTED="${HERE}/expected.txt" \
  mvn -o -q -pl service/favorite/favorite-rpc -am test \
  -Dtest=FavoriteAuthorityLiveParityTest -DfailIfNoTests=false \
  -Dsurefire.failIfNoSpecifiedTests=false ) \
  || die "Java 侧 FavoriteAuthorityLiveParityTest 失败（见上方输出）"
[ -s "${RAG_OUT}" ] || die "Java 侧没有产出结果文件"
ok "Java 侧产出 $(wc -l < "${RAG_OUT}" | tr -d ' ') 个 case"

# ── 7. 两侧逐条比对（按 expected.txt 的 kind 判定）─────────────────────────
say "Go 侧 vs Java 侧逐条比对"
python3 - "${HERE}/expected.txt" "${GO_OUT}" "${RAG_OUT}" <<'PY'
import sys
exp_path, go_path, rag_path = sys.argv[1], sys.argv[2], sys.argv[3]

def load(p):
    out = {}
    for line in open(p, encoding="utf-8"):
        line = line.rstrip("\n")
        if not line.strip():
            continue
        parts = line.split("\t", 2)
        if len(parts) < 2:
            continue
        out[parts[0]] = parts[1] if len(parts) == 2 else parts[2]
    return out

kinds = {}
frozen = {}
for line in open(exp_path, encoding="utf-8"):
    line = line.rstrip("\n")
    if not line.strip() or line.lstrip().startswith("#"):
        continue
    cols = line.split("\t", 2)
    if len(cols) < 3:
        continue
    kinds[cols[0]] = cols[1]
    frozen[cols[0]] = cols[2]

go = load(go_path)
rag = load(rag_path)

problems = []
same = obs = skip = 0
for cid, kind in kinds.items():
    g, r = go.get(cid), rag.get(cid)
    if g is None or r is None:
        problems.append(f"  · {cid} —— 有一侧没跑这个 case")
        continue
    if kind == "SKIP":
        skip += 1
        continue
    if kind == "OBS":
        obs += 1
        if g == r:
            problems.append(f"  · {cid} —— OBS 段期望两侧不同，但变成一样了 ⇒ 请重评台账")
        continue
    same += 1
    if g != r:
        problems.append(f"  · {cid} —— 两侧不一致\n      Go : {g}\n      RAG: {r}")

print(f"  SAME {same} 条 / OBS {obs} 条 / SKIP {skip} 条（共 {len(kinds)} 个 case）")
if problems:
    print("❌ 两侧不一致：")
    print("\n".join(problems))
    sys.exit(1)
print("✅ 两侧逐字节一致（SAME 段全部相同；OBS 段仍然不同；SKIP 段已显式登记理由）")
PY
RC=$?
[ "${RC}" -eq 0 ] || exit 1

echo
ok "双侧活体对拍通过：20 个 case 逐字节一致 + 1 个 SKIP（容器层，已显式登记理由）"
echo "   证据边界见 tools/authority-live-probe/favorite/README.md"
exit 0
