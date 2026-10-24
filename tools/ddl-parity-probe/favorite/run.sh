#!/usr/bin/env bash
# ============================================================================
# DDL 双侧活体对拍探针 · favorite 域
# ----------------------------------------------------------------------------
# 目的：把「favorite 域的 DDL 是否两侧一致」从**源码级推理**升级为**双侧活体对拍**。
#
# 背景（为什么以前没做）：
#   · tools/difftest/check-ddl-static.py 只有 users / task / message **三个**域预设
#     ⇒ favorite **不在**其中。
#   · favorite 自己的 FavoriteSchemaParityTest（13 例真 PG）只**抽查**了 3 项
#     ⇒ 其余列的类型/长度/可空性/DEFAULT **从未被逐列比过**。
#
# 两侧怎么建 schema：
#   · Go 侧 —— **真源**的 GORM model（逐字节复制 + sha256 校验）+ **真源**的 4 个 .sql
#     + **真源**的 InitDB（AutoMigrate）⇒ 在真 PG 上建表。
#   · RAG 侧 —— favorite-rpc 的 **真 Flyway** 迁移脚本 V1..V7 ⇒ 在真 PG 上建表。
#
# 🔴 两个执行顺序都跑（关键！）：
#   sql-first —— 存量老表先在 → 运维跑 001..004 → 应用后起
#                 ⇒ **这是真源文档指定的部署顺序**（DELIVERY_ACCEPTANCE.md:7
#                   「先在现有 Favorite PostgreSQL 库执行 001…再执行 002」）
#                 ⇒ **判定基准用这个**
#   app-first —— 应用先起（AutoMigrate 建出 favorite_fact_outbox）→ 运维后跑 001..004
#                 ⇒ 001 的 `CREATE TABLE IF NOT EXISTS` 变成 **no-op**
#                 ⇒ 001 里**内联**的 `CHECK (aggregate_version IN (1,2))` 建不出来
#                 ⇒ **顺序是行为的一部分**（本探针的实测结论）
#
# 判定口径（探针自动分类）：
#   · 语义行（S）不一致 ⇒ 🔴 **真漂移** ⇒ exit 1
#   · 仅 RAG 多出的语义对象 ⇒ OBS（已登记的有意偏离）⇒ 必须与 expected.txt 的 OBS 段**逐条相同**
#   · 仅名字不同（语义相同）⇒ OBS（命名层）⇒ 同样必须与 expected.txt 逐条相同
#
# 用法：
#   bash tools/ddl-parity-probe/favorite/run.sh
#   echo $?   # 0 = 一致；1 = 有漂移；2 = 环境问题
#
# 可覆盖：
#   GO_REPO=<path>   Go 真源仓库（默认向上搜索 Sea-RideTheWind_golang）
# ============================================================================
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROBE_ROOT="$(cd "${HERE}/../.." && pwd)"
RAG_ROOT="$(cd "${HERE}/../../.." && pwd)"

say()  { printf '\033[1;36m[ddl-probe]\033[0m %s\n' "$*"; }
ok()   { printf '\033[1;32m  ✅ %s\033[0m\n' "$*"; }
bad()  { printf '\033[1;31m  ❌ %s\033[0m\n' "$*"; }
die()  { bad "$*"; exit 2; }

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

# ── 1. 校验复制件 sha256（防真源漂移后探针还按老副本跑）────────────────────
say "校验 model/ 复制件 sha256（12 个文件）"
MISMATCH=0
for f in "${HERE}"/go/model/*; do
  b="$(basename "$f")"
  a="$(shasum -a 256 "$f" | awk '{print $1}')"
  e="$(shasum -a 256 "${GO_REPO}/service/favorite/rpc/internal/model/${b}" | awk '{print $1}')"
  if [ "$a" != "$e" ]; then bad "${b} 与真源不一致（真源已变 ⇒ 探针必须重新复制）"; MISMATCH=1; fi
done
[ "$MISMATCH" = "0" ] || die "复制件 sha256 校验失败"
ok "12 个文件 sha256 全部一致"

# ── 2. 准备 Go 侧临时模块（继承真源 go.mod / go.sum）────────────────────────
TMP="$(mktemp -d)"
cleanup() {
  [ -n "${CLEANED:-}" ] && return 0
  CLEANED=1
  [ -n "${PG_CONTAINER:-}" ] && docker rm -f "${PG_CONTAINER}" >/dev/null 2>&1
  rm -rf "${TMP}"
}
trap cleanup EXIT

mkdir -p "${TMP}/probe"
cp -R "${HERE}/go/." "${TMP}/probe/"
cp -R "${HERE}/sql" "${TMP}/probe/sql"
sed 's|^module sea-try-go$|module ddlparityprobe|' "${GO_REPO}/go.mod" > "${TMP}/probe/go.mod"
cp "${GO_REPO}/go.sum" "${TMP}/probe/go.sum"
export DUMP_SQL="${TMP}/probe/sql/dump.sql"
export NAMES_SQL="${TMP}/probe/sql/names.sql"
GORM_VER="$(awk '/^[[:space:]]*gorm\.io\/gorm v/ {print $2; exit}' "${GO_REPO}/go.mod")"
PGSQL_VER="$(awk '/^[[:space:]]*gorm\.io\/driver\/postgres v/ {print $2; exit}' "${GO_REPO}/go.mod")"
[ -n "${GORM_VER}" ] && [ -n "${PGSQL_VER}" ] || die "无法从真源 go.mod 解析 gorm 版本"
say "真源 go.mod：gorm ${GORM_VER} / driver/postgres ${PGSQL_VER}（go.mod + go.sum 整体继承，不 tidy）"

# ── 3. 起真 PG ─────────────────────────────────────────────────────────────
command -v docker >/dev/null 2>&1 || die "没装 docker"
PG_CONTAINER="ddl-parity-favorite-$$"
say "起 postgres:16-alpine 容器：${PG_CONTAINER}"
docker run -d --rm --name "${PG_CONTAINER}" \
  -e POSTGRES_PASSWORD=probe \
  -e POSTGRES_HOST_AUTH_METHOD=trust \
  -e POSTGRES_USER=postgres \
  -p 0:5432 postgres:16-alpine >/dev/null || die "容器启动失败"

PG_PORT="$(docker port "${PG_CONTAINER}" 5432/tcp | head -1 | sed 's/.*://')"
[ -n "${PG_PORT}" ] || die "拿不到映射端口"
say "PG 端口：${PG_PORT}"

export PGPASSWORD=probe
# ⚠️ 必须探 **TCP**（-h 127.0.0.1）：PG 官方镜像的初始化阶段会起一个**临时 server**，
#    那个临时 server 只监听 unix socket（listen_addresses=''）⇒ 走 socket 的探活会
#    **过早地**报 ready ⇒ 紧接着的 CREATE DATABASE 会撞上"系统正在启动"而随机失败。
for i in $(seq 1 60); do
  if docker exec "${PG_CONTAINER}" pg_isready -U postgres -h 127.0.0.1 -p 5432 >/dev/null 2>&1; then break; fi
  sleep 1
  [ "$i" = "60" ] && die "PG 60 秒内没就绪"
done
ok "PG 就绪（TCP 探活）"

psql_q() { docker exec -i "${PG_CONTAINER}" psql -U postgres -v ON_ERROR_STOP=1 "$@"; }
for db in godb godb_sql ragdb; do
  ok_db=0
  for _ in $(seq 1 15); do
    if psql_q -d postgres -c "CREATE DATABASE ${db};" >/dev/null 2>&1; then ok_db=1; break; fi
    sleep 2
  done
  [ "$ok_db" = "1" ] || die "建 ${db} 失败（重试 15 次仍失败）"
done
ok "三个库建好：godb / godb_sql / ragdb"

# ── 4. Go 侧（两种执行顺序）───────────────────────────────────────────────
export PG_HOST=127.0.0.1 PG_PORT PG_USER=postgres PG_PASSWORD=probe PG_SSLMODE=disable
for sc in sql-first app-first; do
  case "$sc" in
    sql-first) export PG_DB=godb_sql ;;
    app-first) export PG_DB=godb ;;
  esac
  export SCENARIO="$sc"
  say "Go 侧跑 scenario=${sc}（db=${PG_DB}）"
  ( cd "${TMP}/probe" && CGO_ENABLED=0 GOPROXY=off GOFLAGS=-mod=mod GOTOOLCHAIN=local \
      go run . ) > "${TMP}/go_${sc}.txt" 2> "${TMP}/go_${sc}.log"
  rc=$?
  if [ $rc -ne 0 ]; then
    bad "Go 侧 ${sc} 失败（exit ${rc}）"; tail -25 "${TMP}/go_${sc}.log"; exit 2
  fi
  ok "Go 侧 ${sc}：$(wc -l < "${TMP}/go_${sc}.txt" | tr -d ' ') 行 dump"
done

# ── 5. RAG 侧（真 Flyway 迁移脚本）────────────────────────────────────────
say "RAG 侧建表（favorite-rpc 的 V1..V7，与真 Flyway 执行同一批脚本）"
MIG_DIR="${RAG_ROOT}/service/favorite/favorite-rpc/src/main/resources/db/migration"
for v in V1__create_favorite_folder V2__create_favorite_item V3__create_favorite_fact_outbox \
         V4__create_favorite_legacy_fact_marker V5__add_favorite_fact_delivery \
         V6__align_favorite_schema_with_go V7__align_favorite_outbox_time_columns_with_go; do
  f="${MIG_DIR}/${v}.sql"
  [ -f "$f" ] || die "缺迁移脚本：$f"
  psql_q -d ragdb -f - < "$f" >/dev/null || die "执行 ${v} 失败"
  say "  ✔ ${v}"
done
ok "RAG 侧 7 个迁移脚本执行完毕"

say "RAG 侧 dump"
psql_q -d ragdb -t -A -f - < "${HERE}/sql/dump.sql"  > "${TMP}/rag.txt"
psql_q -d ragdb -t -A -f - < "${HERE}/sql/names.sql" >> "${TMP}/rag.txt"
grep -v '^$' "${TMP}/rag.txt" > "${TMP}/rag.clean" && mv "${TMP}/rag.clean" "${TMP}/rag.txt"
[ -s "${TMP}/rag.txt" ] || die "RAG 侧 dump 为空"

# ── 6. 比对（按配对键自动分类）────────────────────────────────────────────
cat > "${TMP}/classify.py" <<'PYEOF'
import sys, hashlib

def load(path):
    sem, nam = {}, {}
    for line in open(path, encoding="utf-8"):
        line = line.strip()
        if not line:
            continue
        if line.startswith("S|"):
            sem[line] = True
        elif line.startswith("N|"):
            p = line.split("|")
            # N|table|kind|name|payload...  ⇒ key = (table, kind, payload)
            nam[(p[1], p[2], "|".join(p[4:]))] = p[3]
    return sem, nam

go_sem, go_nam = load(sys.argv[1])
rag_sem, rag_nam = load(sys.argv[2])

# ① 语义行：Go 有而 RAG 没有 ⇒ 🔴 真漂移（RAG 缺东西）
only_go = sorted(set(go_sem) - set(rag_sem))
# ② 语义行：仅 RAG 多出 ⇒ OBS（有意偏离）
only_rag = sorted(set(rag_sem) - set(go_sem))

# ③ 命名行：配对
obs = []
for key in sorted(set(go_nam) | set(rag_nam)):
    g = go_nam.get(key)
    r = rag_nam.get(key)
    if g is None:
        continue                      # 仅 RAG 有 ⇒ 语义 OBS 已由 only_rag 覆盖
    if r is None:
        print("FATAL: 命名行配对缺口（Go 有 RAG 无）: %s" % (key,))
        sys.exit(3)
    if g != r:
        # 用 \t 分隔（payload 里含 '|'，用 tab 才无歧义）⇒ Java 侧可复用同一份 expected.txt
        obs.append("OBS\tname\t%s\t%s\t%s\t%s\t%s" % (key[1], key[0], key[2], g, r))

for line in only_rag:
    p = line.split("|")
    # S|table|kind|payload... ⇒ payload 从 p[3] 起
    obs.append("OBS\tsemantic\t%s\t%s\t%s" % (p[2], p[1], "|".join(p[3:])))

if only_go:
    print("FATAL: 语义漂移 —— Go 有而 RAG 没有 %d 条：" % len(only_go))
    for l in only_go[:20]:
        print("   " + l)
    sys.exit(3)

# MAIN 指纹：语义行（去掉 OBS 项）+ 名字相同的命名行
main_lines = sorted(set(rag_sem) - set(only_rag))
fingerprint = hashlib.sha256("\n".join(main_lines).encode()).hexdigest()
print("MAIN|" + fingerprint)
for l in sorted(obs):
    print(l)
PYEOF

say "分类比对（Go(sql-first) vs RAG）"
python3 "${TMP}/classify.py" "${TMP}/go_sql-first.txt" "${TMP}/rag.txt" > "${TMP}/classified.txt" 2> "${TMP}/classified.err"
rc=$?
if [ $rc -ne 0 ]; then
  bad "分类阶段失败（exit ${rc}）"
  cat "${TMP}/classified.err"
  exit 1
fi

MAIN_FP="$(grep '^MAIN|' "${TMP}/classified.txt" | head -1)"
# ⚠️ macOS 的 grep **不支持 -P** ⇒ 用 awk 分隔 tab
OBS_ACTUAL="$(awk -F'\t' '$1=="OBS" && $2=="name"' "${TMP}/classified.txt" | sort)"
OBS_SEMANTIC="$(awk -F'\t' '$1=="OBS" && $2=="semantic"' "${TMP}/classified.txt" | sort)"

echo
say "═══ MAIN 指纹（两侧共同的语义内容）═══"
echo "  ${MAIN_FP}"
echo
say "═══ OBS · 命名层差异（语义相同，仅名字不同）═══"
if [ -z "${OBS_ACTUAL}" ]; then echo "  （无）"; else echo "${OBS_ACTUAL}" | sed 's/^/  /'; fi
echo
say "═══ OBS · 语义差异（RAG 多出的对象）═══"
if [ -z "${OBS_SEMANTIC}" ]; then echo "  （无）"; else echo "${OBS_SEMANTIC}" | sed 's/^/  /'; fi

# ── 7. 与冻结的 expected.txt 比对 ─────────────────────────────────────────
EXPECTED="${HERE}/expected.txt"
if [ -f "${EXPECTED}" ]; then
  EXP_MAIN="$(grep '^MAIN|' "${EXPECTED}" | head -1)"
  EXP_OBS="$(awk -F'\t' '$1=="OBS"' "${EXPECTED}" | sort)"
  echo
  say "═══ 与 expected.txt 比对 ═══"
  RC=0
  if [ "${MAIN_FP}" = "${EXP_MAIN}" ]; then
    ok "MAIN 指纹一致"
  else
    bad "MAIN 指纹不一致"
    echo "  期望：${EXP_MAIN}"
    echo "  实际：${MAIN_FP}"
    RC=1
  fi
  ACT_OBS_ALL="$(printf '%s\n%s\n' "${OBS_ACTUAL}" "${OBS_SEMANTIC}" | grep -v '^$' | sort)"
  if [ "${ACT_OBS_ALL}" = "${EXP_OBS}" ]; then
    ok "OBS 段逐条一致（$(echo "${EXP_OBS}" | grep -c . ) 条）"
  else
    bad "OBS 段不一致"
    diff <(echo "${EXP_OBS}") <(echo "${ACT_OBS_ALL}") | sed 's/^/    /' | head -40
    RC=1
  fi
  [ "$RC" = "0" ] || exit 1
else
  say "expected.txt 还不存在 ⇒ 本次为首次冻结（下面的 MAIN 指纹 + OBS 清单即冻结内容）"
  { echo "# DDL 双侧活体对拍 · favorite 域 · 冻结期望"
    echo "# 生成方式：bash tools/ddl-parity-probe/favorite/run.sh（首次运行自动产出）"
    echo "# 判定基准：Go 侧 = sql-first（运维先跑 001..004，应用后起 ⇒ 真源文档指定的顺序）"
    echo
    echo "${MAIN_FP}"
    echo
    printf '%s\n' "${OBS_ACTUAL}" | grep -v '^$' | sort
    printf '%s\n' "${OBS_SEMANTIC}" | grep -v '^$' | sort
  } > "${EXPECTED}"
  ok "已冻结 expected.txt"
fi

# ── 8. 顺序敏感性（记录用，不参与退出码）──────────────────────────────────
echo
say "═══ 顺序敏感性：Go(app-first) vs Go(sql-first) ═══"
say "（下面是两者的**原始 dump** 差异 —— 只作记录，不参与退出码）"
diff -u "${TMP}/go_sql-first.txt" "${TMP}/go_app-first.txt" | grep '^[+-][^+-]' | head -30 || echo "  （无差异）"
say "⇒ 结论：**执行顺序是行为的一部分** —— app-first 下 001 的 CREATE TABLE 变 no-op"
say "  ⇒ 001 里**内联**的 CHECK (aggregate_version IN (1,2)) 建不出来；"
say "  ⇒ 且 favorite_fact_outbox 的**列顺序**也不同（AutoMigrate 按 struct 序，001 按 DDL 序）。"
say "  ⇒ RAG 侧与 **sql-first** 一致 ⇒ 与真源文档指定的部署顺序一致。"

# ── 9. 留档 ───────────────────────────────────────────────────────────────
OUT_DIR="${HERE}/out"
mkdir -p "${OUT_DIR}"
cp "${TMP}/go_sql-first.txt" "${OUT_DIR}/go_sql-first.txt"
cp "${TMP}/go_app-first.txt" "${OUT_DIR}/go_app-first.txt"
cp "${TMP}/rag.txt"          "${OUT_DIR}/rag.txt"
say "原始 dump 已留档到 ${OUT_DIR}/"

echo
ok "DDL 双侧活体对拍通过"
exit 0
