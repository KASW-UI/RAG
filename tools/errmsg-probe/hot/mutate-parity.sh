#!/usr/bin/env bash
#
# 变异验证：证明 tools/errmsg-probe/hot/run-parity.sh **不是假绿**（AGENTS.md §3.3.3）。
#
# 做法：逐条把 RAG 侧（Java）改坏 ⇒ 探针必须**精确转红**（exit 1）；
#       改完必须**逐字节还原** ⇒ 还原后探针必须重新转绿（exit 0）。
#
# ⚠️ 只变异 **RAG 侧**（Java）：本探针的职责是「RAG 偏离 Go 时立刻响」。
#    Go 侧是真源的逐字节复制件，动了它 sha256 校验就会 exit 2（那是**另一条**判据）。
#
# 四条变异覆盖四类不同的漂移：
#   M1 码值漂移      —— enum 里的数值被改
#   M2 文案漂移      —— codeMsg 表里的文案被改
#   M3 回落语义漂移  —— 未知码不再回落到 "FAIL"
#   M4 表多一条      —— 表里多出一个 Go 侧没有的码
#
# 用法：bash tools/errmsg-probe/hot/mutate-parity.sh
# 退出码：0 = 四条变异全部精确转红且还原后转绿；1 = 有变异没转红（守门失效）。
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAG_ROOT="$(cd "${HERE}/../../.." && pwd)"
MODULE="service/hot/hot-common"
PKG_DIR="${RAG_ROOT}/${MODULE}/src/main/java/com/rag/hot/hot/common/errmsg"
CODE_FILE="${PKG_DIR}/HotErrorCode.java"
MSG_FILE="${PKG_DIR}/HotErrorMessages.java"

BACKUP="$(mktemp -d)"
cp "${CODE_FILE}" "${BACKUP}/HotErrorCode.java"
cp "${MSG_FILE}" "${BACKUP}/HotErrorMessages.java"

restore() {
  cp "${BACKUP}/HotErrorCode.java" "${CODE_FILE}"
  cp "${BACKUP}/HotErrorMessages.java" "${MSG_FILE}"
}
cleanup() {
  restore
  rm -rf "${BACKUP}"
}
trap cleanup EXIT

say() { printf '%s\n' "$*"; }

# mutate <file> <python-replacement-script>
mutate() {
  local file="$1" script="$2"
  python3 - "${file}" <<PYEOF
import sys, io
path = sys.argv[1]
src = io.open(path, encoding="utf-8").read()
$script
io.open(path, "w", encoding="utf-8").write(src)
PYEOF
}

# ── 跑一次探针，返回 exit code ────────────────────────────────────────────
# ⚠️ 探针输出**必须留档**：否则 exit 2（环境问题）与 exit 1（真漂移）
#    在日志里长得一模一样 ⇒ 守门者分不清「守门失效」和「环境没准备好」。
PROBE_LOG="${BACKUP}/probe.log"
run_probe() {
  # ⚠️ **不要**在这里自己算 GO_REPO 默认值 —— 探针内部已按「向上找 6 层」解析（worktree 安全）。
  #    这里再算一遍就会复现「worktree 里指错」的同类缺陷。
  ( cd "${RAG_ROOT}" && bash tools/errmsg-probe/hot/run-parity.sh ) >"${PROBE_LOG}" 2>&1
  echo $?
}

# 探针没按预期转红时，把原因打出来（否则无法区分「变异没生效」和「环境坏了」）。
show_probe_log() {
  say "   --- 探针输出（末尾 15 行）---"
  tail -15 "${PROBE_LOG}" 2>/dev/null | sed 's/^/   | /'
  say "   --- 结束 ---"
}

say "== 基线（未变异）必须转绿 =="
restore
BASE_RC="$(run_probe)"
if [ "${BASE_RC}" != "0" ]; then
  say "❌ 基线就不绿（exit ${BASE_RC}）⇒ 后续变异判定无意义，先修基线"
  show_probe_log
  exit 1
fi
say "   ✅ 基线绿（exit 0）"

FAILED=0

# ── M1 码值漂移 ────────────────────────────────────────────────────────────
say ""
say "== M1 码值漂移：ERROR_NOT_FOUND(5002) → (5012) =="
restore
mutate "${CODE_FILE}" 'src = src.replace("ERROR_NOT_FOUND(5002)", "ERROR_NOT_FOUND(5012)", 1)'
RC="$(run_probe)"
if [ "${RC}" = "1" ]; then
  say "   ✅ 精确转红（exit 1）"
else
  say "   ❌ 没转红（exit ${RC}）⇒ 守门失效"
  show_probe_log
  FAILED=1
fi

# ── M2 文案漂移 ────────────────────────────────────────────────────────────
say ""
say "== M2 文案漂移：\"record not found\" → \"record not found.\" =="
restore
mutate "${MSG_FILE}" 'src = src.replace("\"record not found\"", "\"record not found.\"", 1)'
RC="$(run_probe)"
if [ "${RC}" = "1" ]; then
  say "   ✅ 精确转红（exit 1）"
else
  say "   ❌ 没转红（exit ${RC}）⇒ 守门失效"
  show_probe_log
  FAILED=1
fi

# ── M3 回落语义漂移 ────────────────────────────────────────────────────────
say ""
say "== M3 回落语义漂移：未知码回落 \"FAIL\" → \"\" =="
restore
mutate "${MSG_FILE}" 'src = src.replace("return MSG_BY_CODE.get(HotErrorCode.ERROR.getCode());", "return \"\";", 1)'
RC="$(run_probe)"
if [ "${RC}" = "1" ]; then
  say "   ✅ 精确转红（exit 1）"
else
  say "   ❌ 没转红（exit ${RC}）⇒ 守门失效"
  show_probe_log
  FAILED=1
fi

# ── M4 表多一条 ────────────────────────────────────────────────────────────
say ""
say "== M4 表多一条：加一条 Go 侧没有的 9999 =="
restore
mutate "${MSG_FILE}" 'src = src.replace("        map.put(HotErrorCode.ERROR_DB_UPDATE.getCode(), \"database update failed\");", "        map.put(HotErrorCode.ERROR_DB_UPDATE.getCode(), \"database update failed\");\n        map.put(9999, \"extra entry not in go\");", 1)'
RC="$(run_probe)"
if [ "${RC}" = "1" ]; then
  say "   ✅ 精确转红（exit 1）"
else
  say "   ❌ 没转红（exit ${RC}）⇒ 守门失效"
  show_probe_log
  FAILED=1
fi

# ── 还原后必须重新转绿 ─────────────────────────────────────────────────────
say ""
say "== 还原后必须重新转绿 =="
restore
RC="$(run_probe)"
if [ "${RC}" = "0" ]; then
  say "   ✅ 还原后绿（exit 0）"
else
  say "   ❌ 还原后仍红（exit ${RC}）⇒ 还原没生效"
  FAILED=1
fi

say ""
if [ "${FAILED}" = "0" ]; then
  say "✅ 四条变异全部精确转红 + 还原后转绿 ⇒ 探针非假绿"
  exit 0
fi
say "❌ 有变异没转红 ⇒ 探针是假绿"
exit 1
