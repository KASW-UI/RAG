#!/usr/bin/env bash
#
# 变异验证：证明 tools/errmsg-probe/users/run-parity.sh **不是假绿**（AGENTS.md §3.3.3）。
#
# 做法：把 **RAG 侧（Java）** 的 `ErrorCode.java`（users 域的码表 + 文案表，**同一个类**）
#       **复制到临时目录** ⇒ 在**副本**上改坏一条文案 / 一个码值 / 一处回落语义
#       ⇒ 编译成「影子类」目录 ⇒ 把该目录**前置**到探针的 classpath ⇒ 跑对拍
#       ⇒ 必须**精确转红**（exit 1）。
#
# ⚠️ 为什么用「影子类」而不是直接改仓内源码（hot 版 mutate-parity.sh 的做法）：
#   ① 本 lane 的写权**不含** service/**（生产代码一个字都不能动）⇒ 改仓内源码越权；
#   ② 仓内源码在变异窗口内处于「被改坏」状态 ⇒ 一旦进程被杀，仓里就留下坏代码；
#   ③ 影子类只在临时目录里存在 ⇒ 跑完即弃，**仓内零残留**（本脚本可反复跑、可并发跑）。
#   ⇒ 变异对象是「RAG 侧实现」这件事没变（影子类与真实类同 FQN，classpath 前置即覆盖）。
#
# ⚠️ 三条变异覆盖三类不同的漂移：
#   M1 文案漂移      —— 枚举里的一条中文文案被改（**任务书指定的那一条**）
#   M2 码值漂移      —— 枚举里的数值被改
#   M3 回落语义漂移  —— 未知码不再回落到 "FAIL"
#
# 用法：bash tools/errmsg-probe/users/mutate-parity.sh
#
# 退出码（⚠️ 与仓内 hot 版约定**不同**，按本任务书的要求）：
#   1 = ✅ 全部变异都精确转红（**这就是「守门有牙齿」的证据**；变异测试按设计以 1 退出）
#   2 = ❌ 基线不绿 / 有变异没转红（⇒ 守门是假绿）/ 环境问题
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAG_ROOT="$(cd "${HERE}/../../.." && pwd)"
MODULE="service/users/users-common"
PKG_DIR="${RAG_ROOT}/${MODULE}/src/main/java/com/rag/users/common/errmsg"
CODE_FILE="${PKG_DIR}/ErrorCode.java"

TMP="$(mktemp -d)"
trap 'rm -rf "${TMP}"' EXIT

say() { printf '%s\n' "$*"; }
die() { printf '!! %s\n' "$*" >&2; exit 2; }

# mutate <file> <python 语句>
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
PROBE_LOG="${TMP}/probe.log"
run_probe() {
  ( cd "${RAG_ROOT}" && bash tools/errmsg-probe/users/run-parity.sh ) >"${PROBE_LOG}" 2>&1
  echo $?
}

show_probe_log() {
  say "   --- 探针输出（末尾 20 行）---"
  tail -20 "${PROBE_LOG}" 2>/dev/null | sed 's/^/   | /'
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

# ── 取一次真实 classpath（供影子类编译用）────────────────────────────────
# ⚠️ 用探针自己落盘的 classpath（ERRSMSG_PROBE_CP_OUT），而不是自己再跑一遍 mvn ——
#    这样影子类编译用的依赖集与探针跑的时候**完全一致**。
CP_OUT="${TMP}/cp.txt"
( cd "${RAG_ROOT}" && ERRSMSG_PROBE_CP_OUT="${CP_OUT}" bash tools/errmsg-probe/users/run-parity.sh ) \
  >"${TMP}/probe-cp.log" 2>&1 || true
[ -s "${CP_OUT}" ] || die "没能拿到探针的 classpath（ERRSMSG_PROBE_CP_OUT）—— 见 ${TMP}/probe-cp.log"
CP="$(cat "${CP_OUT}")"
say "   classpath 已取（长度 ${#CP}）"

FAILED=0

# run_mutation <名称> <源文件> <python 语句> <期望出现在红色输出里的文本>
run_mutation() {
  local name="$1" src="$2" pyscript="$3" expect="$4"
  local mut_dir="${TMP}/mut-${name}"
  local mut_src="${mut_dir}/$(basename "${src}")"
  local mut_classes="${mut_dir}/classes"
  local rc

  mkdir -p "${mut_dir}" "${mut_classes}"
  cp "${src}" "${mut_src}"
  mutate "${mut_src}" "${pyscript}"

  # 变异必须真的生效（否则测的是「没变异」）。
  if cmp -s "${src}" "${mut_src}"; then
    say "   ❌ 变异脚本没改到任何东西（${name}）⇒ 变异无效"
    FAILED=1
    return
  fi

  # 编译影子类（javac 按包名建目录 ⇒ FQN 与真实类一致 ⇒ classpath 前置即覆盖）
  if ! javac -nowarn -encoding UTF-8 -cp "${CP}" -d "${mut_classes}" "${mut_src}" >"${mut_dir}/javac.log" 2>&1; then
    say "   ❌ 影子类编译失败（${name}）—— 见 ${mut_dir}/javac.log"
    tail -5 "${mut_dir}/javac.log" | sed 's/^/   | /'
    FAILED=1
    return
  fi

  rc="$( cd "${RAG_ROOT}" && ERRSMSG_PROBE_EXTRA_CP="${mut_classes}" \
          bash tools/errmsg-probe/users/run-parity.sh >"${mut_dir}/probe.log" 2>&1; echo $? )"
  cp "${mut_dir}/probe.log" "${PROBE_LOG}"

  if [ "${rc}" != "1" ]; then
    say "   ❌ 没转红（exit ${rc}）⇒ 守门失效（${name}）"
    show_probe_log
    FAILED=1
    return
  fi
  if ! grep -q -- "${expect}" "${PROBE_LOG}"; then
    say "   ❌ 转红了，但红色输出里找不到被改的那条文案「${expect}」⇒ 守门的报错指向不够（${name}）"
    show_probe_log
    FAILED=1
    return
  fi
  say "   ✅ 精确转红（exit 1），红色输出明确指出「${expect}」"
  say "      --- 红色输出关键行 ---"
  grep -n -- "${expect}" "${PROBE_LOG}" | head -3 | sed 's/^/      | /'
}

# ── M1 文案漂移（任务书指定的变异：改一条错误码文案）──────────────────────
say ""
say "== M1 文案漂移：「查询数据库失败」→「查询数据库失败（变异）」=="
run_mutation "m1-msg" "${CODE_FILE}" \
  'src = src.replace("\"查询数据库失败\"", "\"查询数据库失败（变异）\"", 1)' \
  "查询数据库失败（变异）"

# ── M2 码值漂移 ────────────────────────────────────────────────────────────
say ""
say "== M2 码值漂移：ERROR_DB_SELECT(5003) → (5013) =="
run_mutation "m2-code" "${CODE_FILE}" \
  'src = src.replace("ERROR_DB_SELECT(5003,", "ERROR_DB_SELECT(5013,", 1)' \
  "5013"

# ── M3 回落语义漂移 ────────────────────────────────────────────────────────
say ""
say "== M3 回落语义漂移：未知码回落 \"FAIL\" → \"\" =="
run_mutation "m3-fallback" "${CODE_FILE}" \
  'src = src.replace("return of(code).msg;", "return \"\";", 1)' \
  "-FALLBACK|0|FAIL"

say ""
if [ "${FAILED}" = "0" ]; then
  say "✅ 三条变异全部精确转红 ⇒ 探针非假绿（本脚本按约定以 exit 1 表示『变异测试通过』）"
  exit 1
fi
say "❌ 有变异没转红（或红色输出指向不够）⇒ 探针是假绿"
exit 2
