#!/usr/bin/env bash
#
# 变异验证：证明 tools/errmsg-probe/task/run-parity.sh **不是假绿**（AGENTS.md §3.3.3）。
#
# 做法：把被测对象**复制到临时目录** ⇒ 在**副本**上改一处错误文案 ⇒ 探针必须**精确转红**
#       （run-parity.sh exit 1）⇒ 副本丢弃后必须**重新转绿**（exit 0）。
#
# ⚠️ 为什么不在仓内原地改生产代码：
#   hot 域的 mutate-parity.sh 改的是 `service/hot/hot-common/src/main/java/...`（改完还原）。
#   本脚本**不这么做** —— 原地改生产代码即使还原也留下窗口期（并发跑批时别人会读到坏代码），
#   且 AGENTS.md 的 lane 写权不允许本 lane 改 `service/**`。
#   ⇒ 本脚本把「Go 真源」与「RAG 源码」各复制一份到临时目录，变异**只发生在副本上**：
#     · M1 变异 **Go 侧复制件** —— 造一个「假真源仓」（go.mod/go.sum + service/task 树原样，
#       但 `get_task_logic.go` 的文案被改掉），用 GO_REPO 指过去
#       ⇒ 探针的 sha256 自校验仍然通过（副本 vs 假真源），但两侧文案不再相等 ⇒ 必须红。
#     · M2 变异 **Java 侧源码副本** —— 复制 `TaskDomainService.java` 到临时目录、改文案、
#       用 javac 编副本，用 PROBE_RPC_CLASSES 指过去 ⇒ 生产源码一个字都没动 ⇒ 必须红。
#     · M3 **输入根探针硬失败** —— GOMODCACHE 指到空目录 ⇒ 探针必须 exit 2（不得静默降级）。
#     · M4 还原后必须**重新转绿**。
#
# ⚠️ 退出码（本 lane 的约定，与仓内另外 8 个域的 mutate-parity.sh **不同**，见 README §二）：
#     1 = 全部变异精确转红 + 还原后转绿（**测试通过**）
#     2 = 守门失效 / 环境问题
#     ⚠️ 也就是说：**本脚本「成功」时返回 1**，不是 0。
#        这是本次派单明确指定的约定（run-parity.sh 仍是 0 = 一致 / 1 = 有漂移 / 2 = 环境问题）。
#
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAG_ROOT="$(cd "${HERE}/../../.." && pwd)"

GO_RPC_LOGIC="service/task/rpc/internal/logic/get_task_logic.go"
MODULE_RPC="service/task/task-rpc"
JAVA_PKG="com/rag/task/task/rpc/service"
JAVA_FILE="TaskDomainService.java"

# 变异锚点：**只改这一条文案**（Go 侧与 Java 侧各自对应的那一行）。
GO_ANCHOR_OLD='status.Error(codes.InvalidArgument, "user_id invalid")'
GO_ANCHOR_NEW='status.Error(codes.InvalidArgument, "user_id invalid.")'
JAVA_ANCHOR_OLD='withDescription("user_id invalid")'
JAVA_ANCHOR_NEW='withDescription("user_id invalid.")'

WORK="$(mktemp -d)"
trap 'rm -rf "${WORK}"' EXIT

say() { printf '%s\n' "$*"; }
die() { printf '!! %s\n' "$*" >&2; exit 2; }

# Go 真源仓库：与 run-parity.sh **同款**的逐层向上查找（worktree 里「同级兄弟」不存在）。
# ⚠️ 必须共用同一套解析 —— 否则本脚本会造出一个指向不存在目录的「假真源仓」。
default_go_repo() {
  local d
  d="$(cd "${RAG_ROOT}" && pwd)"
  for _ in 1 2 3 4 5 6; do
    if [ -f "${d}/Sea-RideTheWind_golang/${GO_RPC_LOGIC}" ]; then
      (cd "${d}/Sea-RideTheWind_golang" && pwd); return 0
    fi
    d="$(dirname "${d}")"
  done
  return 1
}
TRUTH_REPO="${GO_REPO:-$(default_go_repo || true)}"
[ -n "${TRUTH_REPO}" ] || die "找不到 Go 真源（向上 6 层内没有 Sea-RideTheWind_golang）—— 用 GO_REPO=... 指定"

# ⚠️ 探针输出**必须留档**：否则 exit 2（环境问题）与 exit 1（真漂移）
#    在日志里长得一模一样 ⇒ 守门者分不清「守门失效」和「环境没准备好」。
PROBE_LOG="${WORK}/probe.log"
run_probe() {
  ( cd "${RAG_ROOT}" && bash tools/errmsg-probe/task/run-parity.sh ) >"${PROBE_LOG}" 2>&1
  echo $?
}
show_probe_log() {
  say "   --- 探针输出（末尾 25 行）---"
  tail -25 "${PROBE_LOG}" 2>/dev/null | sed 's/^/   | /'
  say "   --- 结束 ---"
}
show_red_diff() {
  say "   --- 红色输出（diff 段）---"
  sed -n '/❌ 双侧不一致/,/^== 7\/8/p' "${PROBE_LOG}" 2>/dev/null | head -30 | sed 's/^/   | /'
  say "   --- 结束 ---"
}

say "== 基线（未变异）必须转绿 =="
BASE_RC="$(run_probe)"
if [ "${BASE_RC}" != "0" ]; then
  say "❌ 基线就不绿（exit ${BASE_RC}）⇒ 后续变异判定无意义，先修基线"
  show_probe_log
  exit 2
fi
say "   ✅ 基线绿（exit 0）"

FAILED=0

# ── M1 Go 侧复制件文案漂移 ──────────────────────────────────────────────────
say ""
say "== M1 Go 侧复制件文案漂移：\"user_id invalid\" → \"user_id invalid.\" =="
FAKE_REPO="${WORK}/fakerepo"
mkdir -p "${FAKE_REPO}/service/task"
cp "${TRUTH_REPO}/go.mod" "${TRUTH_REPO}/go.sum" "${FAKE_REPO}/"
cp -R "${TRUTH_REPO}/service/task/rpc" "${FAKE_REPO}/service/task/"
cp -R "${TRUTH_REPO}/service/task/api" "${FAKE_REPO}/service/task/"
REAL_GO_SHA="$(shasum -a 256 "${TRUTH_REPO}/${GO_RPC_LOGIC}" | awk '{print $1}')"
python3 - "${FAKE_REPO}/${GO_RPC_LOGIC}" "${GO_ANCHOR_OLD}" "${GO_ANCHOR_NEW}" <<'PYEOF'
import sys, io
path, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
src = io.open(path, encoding="utf-8").read()
if old not in src:
    sys.stderr.write("!! 变异锚点没找到（Go 侧）：" + old + "\n")
    sys.exit(3)
io.open(path, "w", encoding="utf-8").write(src.replace(old, new, 1))
PYEOF
[ $? -eq 0 ] || die "Go 侧变异锚点没找到（真源结构变了？）"
MUT_GO_SHA="$(shasum -a 256 "${FAKE_REPO}/${GO_RPC_LOGIC}" | awk '{print $1}')"
say "   变异前 sha256（真源）   = ${REAL_GO_SHA}"
say "   变异后 sha256（副本）   = ${MUT_GO_SHA}"
say "   ⚠️ 真源仓未被触碰（变异只发生在 ${FAKE_REPO}）"
GO_REPO="${FAKE_REPO}"; export GO_REPO
RC="$(run_probe)"
unset GO_REPO
if [ "${RC}" = "1" ]; then
  say "   ✅ 精确转红（exit 1）"
  show_red_diff
else
  say "   ❌ 没转红（exit ${RC}）⇒ 守门失效"
  show_probe_log
  FAILED=1
fi

# ── M2 Java 侧源码副本文案漂移 ──────────────────────────────────────────────
say ""
say "== M2 Java 侧源码副本文案漂移：\"user_id invalid\" → \"user_id invalid.\" =="
MUT_SRC="${WORK}/mut-src/${JAVA_PKG}"
MUT_CLASSES="${WORK}/mut-classes"
mkdir -p "${MUT_SRC}" "${MUT_CLASSES}"
PROD_JAVA="${RAG_ROOT}/${MODULE_RPC}/src/main/java/${JAVA_PKG}/${JAVA_FILE}"
[ -f "${PROD_JAVA}" ] || die "找不到生产源码：${PROD_JAVA}"
REAL_JAVA_SHA="$(shasum -a 256 "${PROD_JAVA}" | awk '{print $1}')"
cp "${PROD_JAVA}" "${MUT_SRC}/${JAVA_FILE}"
python3 - "${MUT_SRC}/${JAVA_FILE}" "${JAVA_ANCHOR_OLD}" "${JAVA_ANCHOR_NEW}" <<'PYEOF'
import sys, io
path, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
src = io.open(path, encoding="utf-8").read()
if old not in src:
    sys.stderr.write("!! 变异锚点没找到（Java 侧）：" + old + "\n")
    sys.exit(3)
io.open(path, "w", encoding="utf-8").write(src.replace(old, new, 1))
PYEOF
[ $? -eq 0 ] || die "Java 侧变异锚点没找到（生产源码结构变了？）"
MUT_JAVA_SHA="$(shasum -a 256 "${MUT_SRC}/${JAVA_FILE}" | awk '{print $1}')"
say "   变异前 sha256（生产源码） = ${REAL_JAVA_SHA}"
say "   变异后 sha256（临时副本） = ${MUT_JAVA_SHA}"
say "   ⚠️ 生产源码未被触碰（变异只发生在 ${MUT_SRC}）"

mvn -o -q -f "${RAG_ROOT}/pom.xml" -pl "${MODULE_RPC}" -am \
  dependency:build-classpath -Dmdep.outputFile="${WORK}/cp.txt" -Dmdep.includeScope=test \
  || die "dependency:build-classpath 失败"
BASE_CP="${RAG_ROOT}/${MODULE_RPC}/target/classes:$(cat "${WORK}/cp.txt")"
# ⚠️ 只编**被变异的那一个文件**（其余类从生产 classpath 解析）⇒ 快，且不碰仓内任何文件。
javac -nowarn -encoding UTF-8 -cp "${BASE_CP}" -d "${MUT_CLASSES}" "${MUT_SRC}/${JAVA_FILE}" \
  || die "变异副本编译失败"

PROBE_RPC_CLASSES="${MUT_CLASSES}:${RAG_ROOT}/${MODULE_RPC}/target/classes"; export PROBE_RPC_CLASSES
RC="$(run_probe)"
unset PROBE_RPC_CLASSES
if [ "${RC}" = "1" ]; then
  say "   ✅ 精确转红（exit 1）"
  show_red_diff
else
  say "   ❌ 没转红（exit ${RC}）⇒ 守门失效"
  show_probe_log
  FAILED=1
fi

# ── M3 输入根探针硬失败 ─────────────────────────────────────────────────────
say ""
say "== M3 输入根探针硬失败：GOMODCACHE 指向空目录 ⇒ 必须 exit 2（不得静默降级）=="
EMPTY_CACHE="${WORK}/empty-cache"
mkdir -p "${EMPTY_CACHE}"
GOMODCACHE="${EMPTY_CACHE}"; export GOMODCACHE
RC="$(run_probe)"
unset GOMODCACHE
if [ "${RC}" = "2" ] && grep -q "输入根探针失败" "${PROBE_LOG}"; then
  say "   ✅ 精确转红（exit 2 · 探针硬失败）"
  grep -A3 "输入根探针失败" "${PROBE_LOG}" | head -6 | sed 's/^/   | /'
else
  say "   ❌ 没按预期转红（exit ${RC}）⇒ 输入不可见时会把「我解析不了」伪装成「我核过了」"
  show_probe_log
  FAILED=1
fi

# ── M4 还原后必须重新转绿 ───────────────────────────────────────────────────
say ""
say "== M4 还原后必须重新转绿（真源 + 生产 classpath）=="
RC="$(run_probe)"
if [ "${RC}" = "0" ]; then
  say "   ✅ 还原后绿（exit 0）"
else
  say "   ❌ 还原后仍红（exit ${RC}）⇒ 还原没生效"
  show_probe_log
  FAILED=1
fi

say ""
if [ "${FAILED}" = "0" ]; then
  say "✅ 全部变异精确转红 + 还原后转绿 ⇒ 探针非假绿"
  say "   （退出码约定：**0 = 全部精确转红 = 通过** · 1 = 有变异没转红 = 守门失效 · 2 = 环境/输入根问题）"
  exit 0
fi
say "❌ 有变异没转红 ⇒ 探针是假绿"
exit 1
