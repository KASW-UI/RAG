#!/usr/bin/env bash
#
# users-common 双侧活体对拍探针（**库面** + **DDL 面**）。
#
# 真源（**只读**）：Sea-RideTheWind_golang/service/user/common/{errmsg,jwt,cryptx}
#                   + service/user/{user,admin}/rpc/internal/model（DDL 真源路径）
# 被测：RAG/service/users/users-common（**真实类**，不重写任何实现）
#
# 定位：补 `docs/DRIFT/users/common.md` §0 的证据缺口 —— 台账声称「176 条双侧活体」，
#   但探针在 `/tmp/commonprobe/**`（**已消失**）⇒ 该结论**不可复算**。
#   本脚本把探针**落进仓库**（AGENTS.md §3.3.3：探针自己也要能被验 ⇒ 配 mutate-parity.sh）。
#
# ⚠️ **真源冻结 ⇒ 只能改「副本」**：Go 侧的探针源码用 `go build -overlay=<json>`
#   **虚拟挂进**真源模块树 ⇒ 真源仓一个字节都不改（脚本第 1 步与第 7 步各断言一次）。
#
# 用法：
#   bash tools/api-full-parity-probe/users-common/run.sh
#   GO_REPO=/path/to/Sea-RideTheWind_golang bash tools/api-full-parity-probe/users-common/run.sh
#
# 退出码：0 = 双侧逐字节一致；1 = 有差异；2 = 环境问题 / 真源漂移 / 依赖缺失。
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAG_ROOT="$(cd "${HERE}/../../.." && pwd)"

GO_REPO="${GO_REPO:-/Users/pr/Documents/java&golang/Sea-RideTheWind_golang}"

# ── 变异钩子（只给 mutate-parity.sh 用；正常运行不设）────────────────────────
# RAG_CP_PREFIX  : 放在 classpath **最前面**的目录 ⇒ 用来「影子化」users-common 的
#                   某个类（**不改共享层源码**，只让探针跑到变异副本上）。
# GO_OVERLAY_EXTRA: 额外的 overlay JSON（与主 overlay 合并）⇒ 用来影子化真源的某个文件。
RAG_CP_PREFIX="${RAG_CP_PREFIX:-}"
GO_OVERLAY_EXTRA="${GO_OVERLAY_EXTRA:-}"
# EXPECTED_FILE    : 换掉冻结值文件（只给 mutate-parity.sh 用 ⇒ 改动**不落仓**）
# UC_CP_OVERRIDE   : 直接给出 users-common 的 classpath（省掉两次 dependency:build-classpath）
EXPECTED_FILE="${EXPECTED_FILE:-${HERE}/expected.txt}"
UC_CP_OVERRIDE="${UC_CP_OVERRIDE:-}"

# ── Postgres（两侧各自一个库 ⇒ 互不污染）────────────────────────────────────
# PROBE_PG_MODE=auto  ⇒ docker 可用就用容器（与 article 探针同款），否则回落宿主本地 PG
# PROBE_PG_MODE=docker / local ⇒ 强制
PROBE_PG_MODE="${PROBE_PG_MODE:-auto}"
# docker 模式：本 lane 的**专属端口段**（18111-18115）与**专属容器名前缀**（r2uc）
GO_PG_CONTAINER="${GO_PG_CONTAINER:-r2uc-go-pg-$$}"
RAG_PG_CONTAINER="${RAG_PG_CONTAINER:-r2uc-rag-pg-$$}"
GO_PG_PORT="${GO_PG_PORT:-18111}"
RAG_PG_PORT="${RAG_PG_PORT:-18112}"
DOCKER_PG_USER="${DOCKER_PG_USER:-admin}"
DOCKER_PG_PASS="${DOCKER_PG_PASS:-sea}"
DOCKER_PG_DB="${DOCKER_PG_DB:-first_db}"
# local 模式：宿主上跑着的 PG（默认 Homebrew postgresql@18，trust 认证）
LOCAL_PG_HOST="${LOCAL_PG_HOST:-127.0.0.1}"
LOCAL_PG_PORT="${LOCAL_PG_PORT:-5432}"
LOCAL_PG_USER="${LOCAL_PG_USER:-$(id -un)}"
LOCAL_PG_PASS="${LOCAL_PG_PASS:-}"
LOCAL_PG_DB_GO="${LOCAL_PG_DB_GO:-r2uc_go_probe}"
LOCAL_PG_DB_RAG="${LOCAL_PG_DB_RAG:-r2uc_rag_probe}"

WORK="$(mktemp -d)"
PIDS=()
say()  { printf '%s\n' "$*"; }
step() { printf '\n== %s ==\n' "$*"; }
die()  { printf '!! %s\n' "$*" >&2; exit 2; }

# ── cleanup：⚠️ **不用 `rm -rf`**（本机被回收站守卫拦下）⇒ 只删文件、临时目录留给 OS ──
cleanup() {
  local p
  for p in ${PIDS[@]+"${PIDS[@]}"}; do kill "$p" 2>/dev/null; done
  sleep 1
  for p in ${PIDS[@]+"${PIDS[@]}"}; do kill -9 "$p" 2>/dev/null; done
  if [ "${PG_MODE:-}" = "docker" ]; then
    docker rm -f "${GO_PG_CONTAINER}" >/dev/null 2>&1
    docker rm -f "${RAG_PG_CONTAINER}" >/dev/null 2>&1
  fi
  if [ "${KEEP_WORK:-0}" = "1" ]; then
    say "   · KEEP_WORK=1 ⇒ 保留 ${WORK}"
  fi
}
trap cleanup EXIT

# ══════════════════════════ 0. 前置检查（缺 ⇒ exit 2，绝不静默降级）══════════════════════════
step "0/7 前置检查"
for cmd in go javac java mvn python3 curl psql git; do
  command -v "${cmd}" >/dev/null 2>&1 || die "缺命令：${cmd}"
done
[ -d "${GO_REPO}/service/user/common" ] || die "找不到 Go 真源：${GO_REPO}/service/user/common（用 GO_REPO=... 指定）"
[ -d "${RAG_ROOT}/service/users/users-common" ] || die "找不到 RAG users-common 模块"

# 真源冻结断言（**跑之前**）：service/user 有任何改动 ⇒ 本探针不得运行
( cd "${GO_REPO}" && git status --porcelain -- service/user | grep -q . ) \
  && die "真源仓 service/user 有改动 ⇒ 本探针**不得**在脏真源上跑"
say "   ✓ 命令齐 / 真源仓 service/user 干净"

# PG 模式判定
if [ "${PROBE_PG_MODE}" = "auto" ]; then
  if docker info >/dev/null 2>&1; then
    PG_MODE="docker"
  else
    PG_MODE="local"
    say "   · docker daemon 不可用 ⇒ 回落「宿主本地 PG」模式"
  fi
else
  PG_MODE="${PROBE_PG_MODE}"
fi
if [ "${PG_MODE}" = "docker" ]; then
  docker ps >/dev/null 2>&1 || die "PROBE_PG_MODE=docker 但 docker daemon 不可用"
  docker image inspect postgres:16-alpine >/dev/null 2>&1 \
    || die "本地没有 postgres:16-alpine 镜像（PROBE_PG_MODE=local 可绕过）"
fi
say "   ✓ PG 模式 = ${PG_MODE}"

# ══════════════════════════ 1. 构建 Go 侧探针（overlay ⇒ 真源只读）══════════════════════════
step "1/7 构建 Go 侧探针（go build -overlay ⇒ 虚拟挂进真源模块树，真源仓零改动）"
mkdir -p "${WORK}/overlay"
cat > "${WORK}/overlay/overlay.json" <<EOF
{"Replace": {
  "${GO_REPO}/service/user/user/rpc/cmd/userscommonprobe/main.go": "${HERE}/go/main.go",
  "${GO_REPO}/service/user/admin/rpc/cmd/userscommonprobe/main.go": "${HERE}/go/admin_main.go"
}}
EOF
# 变异钩子：把额外的 overlay 合并进主 overlay（**只改副本**，真源仓仍零改动）
if [ -n "${GO_OVERLAY_EXTRA}" ] && [ -f "${GO_OVERLAY_EXTRA}" ]; then
  python3 - "${WORK}/overlay/overlay.json" "${GO_OVERLAY_EXTRA}" <<'PY'
import json, sys
main_path, extra_path = sys.argv[1], sys.argv[2]
with open(main_path, encoding="utf-8") as f:
    main = json.load(f)
with open(extra_path, encoding="utf-8") as f:
    extra = json.load(f)
main["Replace"].update(extra.get("Replace", {}))
with open(main_path, "w", encoding="utf-8") as f:
    json.dump(main, f, ensure_ascii=False, indent=2)
PY
  say "   · 已合并额外 overlay（变异钩子）"
fi
# ⚠️ 必须**只读引用**真源：readonly 模式 ⇒ go.mod / go.sum 一个字节都不改
( cd "${GO_REPO}" && CGO_ENABLED=0 GOPROXY=off GOTOOLCHAIN=local \
    go build -overlay="${WORK}/overlay/overlay.json" -o "${WORK}/probe" \
    sea-try-go/service/user/user/rpc/cmd/userscommonprobe ) || die "Go 侧探针（user）构建失败"
( cd "${GO_REPO}" && CGO_ENABLED=0 GOPROXY=off GOTOOLCHAIN=local \
    go build -overlay="${WORK}/overlay/overlay.json" -o "${WORK}/probe-admin" \
    sea-try-go/service/user/admin/rpc/cmd/userscommonprobe ) || die "Go 侧探针（admin）构建失败"
[ -x "${WORK}/probe" ] && [ -x "${WORK}/probe-admin" ] || die "Go 侧探针没产出"
( cd "${GO_REPO}" && git status --porcelain -- service/user | grep -q . ) \
  && die "构建后真源仓 service/user 出现改动 ⇒ overlay 用法有问题"
say "   ✓ Go 侧探针构建完成（真源仓仍干净）"

# ══════════════════════════ 2. 构建 RAG 侧探针（调用 users-common 真实类）══════════════════════
step "2/7 构建 RAG 侧探针（javac + users-common 的真实 classes）"
( cd "${RAG_ROOT}" && ./mvnw -o -q -pl service/users/users-common -am package -DskipTests ) \
  || die "users-common 构建失败"
UC_CLASSES="${RAG_ROOT}/service/users/users-common/target/classes"
[ -d "${UC_CLASSES}/com/rag/users/common" ] || die "users-common 的 classes 没产出"
if [ -n "${UC_CP_OVERRIDE}" ]; then
  say "   · 使用外部给定的 classpath（变异钩子）"
else
( cd "${RAG_ROOT}" && ./mvnw -o -q -pl service/users/users-common dependency:build-classpath \
    -Dmdep.outputFile="${WORK}/uc-cp.txt" -DincludeScope=runtime ) || die "取 users-common 依赖 classpath 失败"
fi
# JDBC 驱动：users-common 自己**不依赖**驱动（它只执行 DDL 脚本）
#   ⇒ 从 users 域自己的 rpc 模块的运行时 classpath 里取（版本由本仓 pom 管理 ⇒ 可复算）
if [ -n "${UC_CP_OVERRIDE}" ]; then
  CP="${RAG_CP_PREFIX:+${RAG_CP_PREFIX}:}${UC_CP_OVERRIDE}"
else
( cd "${RAG_ROOT}" && ./mvnw -o -q -pl service/users/user/user-rpc dependency:build-classpath \
    -Dmdep.outputFile="${WORK}/rpc-cp.txt" -DincludeScope=runtime ) || die "取 users rpc 依赖 classpath 失败"
PG_DRIVER="$(tr ':' '\n' < "${WORK}/rpc-cp.txt" | grep -E '/postgresql-[0-9.]+\.jar$' | head -1)"
[ -n "${PG_DRIVER}" ] || die "classpath 里找不到 postgresql 驱动"
CP="${RAG_CP_PREFIX:+${RAG_CP_PREFIX}:}${UC_CLASSES}:$(cat "${WORK}/uc-cp.txt"):${PG_DRIVER}"
fi
mkdir -p "${WORK}/classes"
javac -proc:none -nowarn -cp "${CP}" -d "${WORK}/classes" "${HERE}/java/Probe.java" \
  || die "RAG 侧探针编译失败"
[ -f "${WORK}/classes/Probe.class" ] || die "RAG 侧探针没产出"
say "   ✓ RAG 侧探针编译完成（classpath 含 users-common 真实 classes）"

# ══════════════════════════ 3. 两侧各自的 Postgres ══════════════════════════════════════════════
step "3/7 两侧各自的 Postgres（Go 侧一个库 · RAG 侧另一个库 ⇒ 互不污染）"
if [ "${PG_MODE}" = "docker" ]; then
  start_pg() {  # $1=容器名 $2=宿主端口 ⇒ 成功返回 0（**宿主端口**已验证可连）
    local c="$1" p="$2"
    docker rm -f "${c}" >/dev/null 2>&1
    docker run -d --name "${c}" -e POSTGRES_USER="${DOCKER_PG_USER}" \
      -e POSTGRES_PASSWORD="${DOCKER_PG_PASS}" -e POSTGRES_DB="${DOCKER_PG_DB}" \
      -p "${p}:5432" postgres:16-alpine >/dev/null 2>&1 || return 1
    local i
    for i in $(seq 1 40); do
      docker exec "${c}" pg_isready -U "${DOCKER_PG_USER}" -d "${DOCKER_PG_DB}" >/dev/null 2>&1 && break
      sleep 1
    done
    for i in $(seq 1 15); do
      nc -z -G 2 127.0.0.1 "${p}" && return 0
      sleep 1
    done
    return 1
  }
  start_pg "${GO_PG_CONTAINER}" "${GO_PG_PORT}" || die "Go 侧 PG 起不来（端口 ${GO_PG_PORT}）"
  start_pg "${RAG_PG_CONTAINER}" "${RAG_PG_PORT}" || die "RAG 侧 PG 起不来（端口 ${RAG_PG_PORT}）"
  PG_HOST="127.0.0.1"; PG_USER="${DOCKER_PG_USER}"; PG_PASS="${DOCKER_PG_PASS}"
  GO_PG_DB="${DOCKER_PG_DB}"; RAG_PG_DB="${DOCKER_PG_DB}"
  GO_PG_PORT="${GO_PG_PORT}"; RAG_PG_PORT="${RAG_PG_PORT}"
else
  # local 模式：在宿主 PG 上建**两个互不相干的库**
  psql -h "${LOCAL_PG_HOST}" -p "${LOCAL_PG_PORT}" -U "${LOCAL_PG_USER}" -d postgres \
    -c "DROP DATABASE IF EXISTS ${LOCAL_PG_DB_GO} WITH (FORCE)" \
    -c "CREATE DATABASE ${LOCAL_PG_DB_GO}" >/dev/null 2>&1 || die "建库失败：${LOCAL_PG_DB_GO}"
  psql -h "${LOCAL_PG_HOST}" -p "${LOCAL_PG_PORT}" -U "${LOCAL_PG_USER}" -d postgres \
    -c "DROP DATABASE IF EXISTS ${LOCAL_PG_DB_RAG} WITH (FORCE)" \
    -c "CREATE DATABASE ${LOCAL_PG_DB_RAG}" >/dev/null 2>&1 || die "建库失败：${LOCAL_PG_DB_RAG}"
  PG_HOST="${LOCAL_PG_HOST}"; PG_USER="${LOCAL_PG_USER}"; PG_PASS="${LOCAL_PG_PASS}"
  GO_PG_PORT="${LOCAL_PG_PORT}"; RAG_PG_PORT="${LOCAL_PG_PORT}"
  GO_PG_DB="${LOCAL_PG_DB_GO}"; RAG_PG_DB="${LOCAL_PG_DB_RAG}"
fi
say "   ✓ 两侧库就绪（Go :${GO_PG_PORT}/${GO_PG_DB} · RAG :${RAG_PG_PORT}/${RAG_PG_DB}）"

# ══════════════════════════ 4. phase 1：两侧**同时**跑（库面 + DDL 面 + 播种）══════════════════
step "4/7 phase 1 —— 两侧**同时**起跑（库面 + DDL 面；各自在**自己的库**里建表并播种同一行）"
"${WORK}/probe" --out="${WORK}/go.p1" --tokens="${WORK}/go.tokens" \
  --pg-host="${PG_HOST}" --pg-port="${GO_PG_PORT}" --pg-user="${PG_USER}" \
  --pg-pass="${PG_PASS}" --pg-db="${GO_PG_DB}" >"${WORK}/go.p1.log" 2>&1 &
PIDS+=($!)
( cd "${WORK}" && java -cp "${WORK}/classes:${CP}" Probe --out="${WORK}/rag.p1" \
    --tokens="${WORK}/rag.tokens" --pg-host="${PG_HOST}" --pg-port="${RAG_PG_PORT}" \
    --pg-user="${PG_USER}" --pg-pass="${PG_PASS}" --pg-db="${RAG_PG_DB}" \
    >"${WORK}/rag.p1.log" 2>&1 ) &
PIDS+=($!)
GO_PID="${PIDS[0]}"; RAG_PID="${PIDS[1]}"
wait "${GO_PID}";  GO_RC=$?
wait "${RAG_PID}"; RAG_RC=$?
[ "${GO_RC}" = "0" ] || { say "---- go.p1.log ----"; tail -40 "${WORK}/go.p1.log"; die "Go 侧探针 phase 1 失败（exit ${GO_RC}）"; }
[ "${RAG_RC}" = "0" ] || { say "---- rag.p1.log ----"; tail -40 "${WORK}/rag.p1.log"; die "RAG 侧探针 phase 1 失败（exit ${RAG_RC}）"; }
say "   ✓ phase 1 两侧都跑完（Go ${GO_RC} · RAG ${RAG_RC}）"

# 1b：Go 侧的 **admin 模型**探针（**必须排在 1a 之后**）
#     ⚠️ 原因：真源的 admin AutoMigrate（service/user/admin/rpc/internal/model/db.go:15）
#        也包含 `&User{}` ⇒ **会和 1a 的 AutoMigrate 争同一张 `users` 表**
#        ⇒ 并发跑会撞 GORM 的建表竞态 ⇒ 必须串行。
"${WORK}/probe-admin" --out="${WORK}/go.admin" \
  --pg-host="${PG_HOST}" --pg-port="${GO_PG_PORT}" --pg-user="${PG_USER}" \
  --pg-pass="${PG_PASS}" --pg-db="${GO_PG_DB}" >"${WORK}/go.admin.log" 2>&1
[ "$?" = "0" ] || { tail -40 "${WORK}/go.admin.log"; die "Go 侧 admin 探针失败"; }
say "   ✓ Go 侧 admin 探针跑完（admin 两张表的 AutoMigrate）"

# ══════════════════════════ 5. phase 2：两侧**同时**做交叉验签 ═══════════════════════════════════
step "5/7 phase 2 —— 两侧**同时**做交叉验签（Go 签的 ⇄ RAG 验；RAG 签的 ⇄ Go 验）"
"${WORK}/probe" --phase=2 --out="${WORK}/go.p2" --tokens="${WORK}/go.tokens" \
  --peer-tokens="${WORK}/rag.tokens" >"${WORK}/go.p2.log" 2>&1 &
PIDS+=($!)
( cd "${WORK}" && java -cp "${WORK}/classes:${CP}" Probe --phase=2 --out="${WORK}/rag.p2" \
    --tokens="${WORK}/rag.tokens" --peer-tokens="${WORK}/go.tokens" \
    >"${WORK}/rag.p2.log" 2>&1 ) &
PIDS+=($!)
GO_PID="${PIDS[2]}"; RAG_PID="${PIDS[3]}"
wait "${GO_PID}";  GO_RC=$?
wait "${RAG_PID}"; RAG_RC=$?
[ "${GO_RC}" = "0" ] || { tail -40 "${WORK}/go.p2.log"; die "Go 侧探针 phase 2 失败（exit ${GO_RC}）"; }
[ "${RAG_RC}" = "0" ] || { tail -40 "${WORK}/rag.p2.log"; die "RAG 侧探针 phase 2 失败（exit ${RAG_RC}）"; }
say "   ✓ phase 2 两侧都跑完"

# ══════════════════════════ 6. 逐 case 比对 ═══════════════════════════════════════════════════════
step "6/7 逐 case 比对（Go == RAG == expected.txt）"
cat "${WORK}/go.p1" "${WORK}/go.admin" "${WORK}/go.p2"  > "${WORK}/go.out"
cat "${WORK}/rag.p1" "${WORK}/rag.p2" > "${WORK}/rag.out"
say "   · Go 侧 case 数：$(wc -l < "${WORK}/go.out" | tr -d ' ') · RAG 侧：$(wc -l < "${WORK}/rag.out" | tr -d ' ')"
[ -f "${EXPECTED_FILE}" ] || die "缺少冻结的期望值：${EXPECTED_FILE}"
python3 "${HERE}/compare.py" "${WORK}/go.out" "${WORK}/rag.out" "${EXPECTED_FILE}"
CMP_RC=$?

# ══════════════════════════ 7. 收尾：再断言一次真源干净 ═══════════════════════════════════════════
step "7/7 收尾"
( cd "${GO_REPO}" && git status --porcelain -- service/user | grep -q . ) \
  && die "真源仓 service/user 在跑完后出现改动 ⇒ 探针污染了真源"
say "   ✓ 真源仓 service/user 全程干净（跑前 / 构建后 / 跑后各断言一次）"

exit "${CMP_RC}"
