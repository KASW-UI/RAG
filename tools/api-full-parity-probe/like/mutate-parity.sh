#!/usr/bin/env bash
#
# 变异验证：证明 tools/api-full-parity-probe/like/run.sh **不是假绿**（AGENTS.md §3.3.3）。
#
# 这条链有三段，每段都必须能被变异打红，否则探针就只是「自己跟自己比」：
#   ① 冻结的期望值（expected.txt）参与判定         ⇒ M1
#   ② RAG 侧的**字节**参与判定（Go == RAG 不是恒等） ⇒ M2
#   ③ E60 的「记业务错误」这条分支参与判定           ⇒ M3
#
# ⚠️ **还原一律用「文件备份 + 复制回来」** —— 绝不用 `git checkout --`：
#    本 lane 的修复**还没提交** ⇒ `git checkout` 会把它们一起回滚掉
#    ⇒ 变异跑完代码就不是原来的代码了。
#
# ⚠️ **绝不动 Go 真源**：真源是只读的（run.sh 第 3 步会断言真源仓干净）。
#    ⇒ 三条变异全部落在 **RAG 侧**或 **冻结的期望值**上。
#
# 退出码：0 = 三条变异都精确转红 + 还原后转绿；1 = 有变异没转红。
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAG_ROOT="$(cd "${HERE}/../../.." && pwd)"
PROBE="${HERE}/run.sh"
EXPECTED="${HERE}/expected.txt"
# M2 的落点：like 域的 code→msg 查表（Go service/like/common/errmsg/errmsg.go:41 对应的那一行）
MSG_FILE="${RAG_ROOT}/service/like/like-common/src/main/java/com/rag/like/like/common/errmsg/LikeErrorMessages.java"
# M3 的落点：E60 的修复本体（Go likeactionlogic.go:313-320 的 Java 对应物）
PUBLISHER="${RAG_ROOT}/service/like/like-rpc/src/main/java/com/rag/like/like/rpc/service/LikeTaskProgressPublisher.java"

cd "${RAG_ROOT}"

TMP_OUT="$(mktemp)"
BAK_DIR="$(mktemp -d)"
# ⚠️ **不删临时目录**：`rm -rf` 在本机会被回收站守卫整体拒绝
# ⇒ 留着 `/var/folders/.../tmp.*` 由系统清理（比让脚本自杀强）。
trap 'rm -f "${TMP_OUT}"' EXIT

say() { printf '%s\n' "$*"; }

FAILED=0

run_probe() {
  bash "${PROBE}" >"${TMP_OUT}" 2>&1
  echo $?
}

# backup <文件> <备份名>
backup() { cp "$1" "${BAK_DIR}/$2"; }
# restore <文件> <备份名>
restore() { cp "${BAK_DIR}/$2" "$1"; }

# mutate <文件> <旧串> <新串>
mutate() {
  python3 - "$1" "$2" "$3" <<'PY'
import sys, io
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = io.open(p, encoding="utf-8").read()
assert old in s, f"未命中: {old[:60]}"
io.open(p, "w", encoding="utf-8").write(s.replace(old, new, 1))
PY
}

backup "${EXPECTED}"   expected.txt
backup "${MSG_FILE}"    messages.java
backup "${PUBLISHER}"   publisher.java

say "== 基线（未变异）必须转绿 =="
BASE_RC="$(run_probe)"
if [ "${BASE_RC}" = "0" ]; then
  say "   ✅ 基线绿（exit 0）"
else
  say "❌ 基线就不绿（exit ${BASE_RC}）⇒ 后续变异判定无意义"
  tail -30 "${TMP_OUT}" | sed 's/^/   | /'
  exit 1
fi

# ─────────────────────────────────────────────────────────────
# M1：改冻结的 expected.txt 一枚 hex ⇒ Go 侧与冻结值不同 ⇒ 必须转红
#      ⇒ 证明「冻结的期望值」确实在参与判定（不是摆设）
# ─────────────────────────────────────────────────────────────
say ""
say "== M1 冻结的 expected.txt 被改 ⇒ 必须转红 =="
mutate "${EXPECTED}" 'CASE|getlikecount|200|7b22' 'CASE|getlikecount|200|7b23'
RC="$(run_probe)"
if [ "${RC}" = "1" ]; then
  say "   ✅ 精确转红（exit 1 · Go 侧与冻结值不符）"
  grep -E "❌|!= expected" "${TMP_OUT}" | head -6 | sed 's/^/   | /'
else
  say "   ❌ 没按预期转红（exit ${RC}）⇒ 探针没在比冻结值"
  tail -25 "${TMP_OUT}" | sed 's/^/   | /'
  FAILED=1
fi
restore "${EXPECTED}" expected.txt

# ─────────────────────────────────────────────────────────────
# M2：改 RAG 侧的 5001 文案 ⇒ RAG 的字节与 Go / 冻结值不同 ⇒ 必须转红
#      ⇒ 证明「Go == RAG」不是恒等式（RAG 侧真的参与了比对）
# ─────────────────────────────────────────────────────────────
say ""
say "== M2 RAG 侧的 5001 文案被改 ⇒ 必须转红 =="
mutate "${MSG_FILE}" 'map.put(LikeErrorCode.ERROR_SERVER_COMMON.getCode(), "系统内部错误");' \
                  'map.put(LikeErrorCode.ERROR_SERVER_COMMON.getCode(), "内部错误");'
RC="$(run_probe)"
if [ "${RC}" = "1" ]; then
  say "   ✅ 精确转红（exit 1 · RAG 侧字节漂移被抓到）"
  grep -E "❌|!= expected" "${TMP_OUT}" | head -6 | sed 's/^/   | /'
else
  say "   ❌ 没按预期转红（exit ${RC}）⇒ 探针看不见 RAG 侧的字节漂移"
  tail -25 "${TMP_OUT}" | sed 's/^/   | /'
  FAILED=1
fi
restore "${MSG_FILE}" messages.java

# ─────────────────────────────────────────────────────────────
# M3：把 E60 的「记业务错误」改成普通 warn
#      ⇒ 这**正是 E60 修前的行为**（修前 `currentUserLikeTotal` 只 `LOG.warn` + `return 0L`
#        ⇒ 双失败**根本没有业务错误记录**）⇒ 必须转红
#      ⇒ 证明「E60 已销案」这条结论不是台账上的空话
# ─────────────────────────────────────────────────────────────
say ""
say "== M3 E60 的业务错误日志被降级成普通 warn（≈ 还原 E60 修前行为）⇒ 必须转红 =="
# ⚠️ 替换串**绝不能**含 "business_error" 子串 —— 探针阶段 C 就是 grep 这个标记
#    （service/common/logger/Logger.java:273 `setMessage("business_error")`）
#    ⇒ 若替换串还留着它，变异后日志里仍能 grep 到 ⇒ 探针照样绿 ⇒ 变异「不精确」。
#    这里改成 "e60 path degraded to plain warn" ⇒ 该标记彻底消失 ⇒ 必然转红。
mutate "${PUBLISHER}" 'logger.logBusinessErr(Context.current(),' 'LOG.warn("e60 path degraded to plain warn", '
RC="$(run_probe)"
if [ "${RC}" = "1" ]; then
  say "   ✅ 精确转红（exit 1 · E60 的业务错误分支没被观测到）"
  grep -E "❌|双失败业务错误" "${TMP_OUT}" | head -6 | sed 's/^/   | /'
else
  say "   ❌ 没按预期转红（exit ${RC}）⇒ 探针看不见 E60 分支被删掉"
  tail -25 "${TMP_OUT}" | sed 's/^/   | /'
  FAILED=1
fi
restore "${PUBLISHER}" publisher.java

say ""
say "== 还原后必须重新转绿 =="
RC="$(run_probe)"
if [ "${RC}" = "0" ]; then
  say "   ✅ 还原后绿（exit 0）"
else
  say "   ❌ 还原后仍红（exit ${RC}）⇒ 还原没生效"
  tail -30 "${TMP_OUT}" | sed 's/^/   | /'
  FAILED=1
fi

say ""
if [ "${FAILED}" = "0" ]; then
  say "✅ 三条变异全部精确转红 + 还原后转绿 ⇒ 探针非假绿"
  exit 0
fi
say "❌ 有变异没转红 ⇒ 探针是假绿"
exit 1
