#!/usr/bin/env bash
#
# 变异验证：证明 `TaskApiFailurePathMatrixTest` **不是假绿**（AGENTS.md §3.3.3）。
#
# 矩阵里的断言是「真实错误码 / HTTP 状态码」⇒ 必须证明它们**真的有牙齿**：
#   ① 改 RAG 的 `user_id invalid` 文案       ⇒ 参数类断言必须转红   ⇒ T-M1
#   ② 改 RAG 的 `X-Content-Type-Options` 值  ⇒ nosniff 断言必须转红  ⇒ T-M2
#   ③ 还原后必须重新转绿                    ⇒ 还原生效               ⇒ 收尾
#
# ⚠️ **还原一律用「文件备份 + 复制回来」** —— 绝不用 `git checkout --`：
#    本 lane 的改动**还没提交** ⇒ `git checkout` 会把它们一起回滚掉。
#
# ⚠️ **只变异 RAG 侧**：Go 真源冻结只读，矩阵里每条断言的期望值都来自 Go 出处（见测试 javadoc）。
#
# 退出码：0 = 两条变异都精确转红 + 还原后转绿；1 = 有变异没转红。
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# src/test/probe/<name>/run.sh ⇒ 上溯 5 层到模块根
MODULE_ROOT="$(cd "${HERE}/../../../.." && pwd)"
RAG_ROOT="$(cd "${MODULE_ROOT}/../../.." && pwd)"

# M1 落点：Go service/task/api/internal/types/types.go:40-47 的两段判定的文案
REQ_FILE="${MODULE_ROOT}/src/main/java/com/rag/task/task/api/dto/GetTaskReq.java"
# M2 落点：Go service/task/api/internal/handler/routes.go 用的 http.Error / http.NotFound（都写死 nosniff）
HANDLER_FILE="${MODULE_ROOT}/src/main/java/com/rag/task/task/api/advice/GlobalExceptionHandler.java"

cd "${RAG_ROOT}"

TMP_OUT="$(mktemp)"
BAK_DIR="$(mktemp -d)"
# ⚠️ **不删临时目录**：`rm -rf` 在本机会被回收站守卫整体拒绝 ⇒ 留给系统清理。
trap 'rm -f "${TMP_OUT}"' EXIT

say() { printf '%s\n' "$*"; }

FAILED=0

backup() { cp "$1" "${BAK_DIR}/$2"; }
restore() { cp "${BAK_DIR}/$2" "$1"; }

mutate() {
  python3 - "$1" "$2" "$3" <<'PY'
import sys, io
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = io.open(p, encoding="utf-8").read()
assert old in s, u"未命中: %s" % old[:60]
io.open(p, "w", encoding="utf-8").write(s.replace(old, new, 1))
PY
}

# 跑矩阵测试；把退出码与「Tests run」摘要行打印出来
# ⚠️ **诊断输出必须走 stderr** —— stdout 只放退出码，否则 `RC="$(run_matrix)"` 会把
#    摘要行一起吞进去 ⇒ `RC` 变成多行文本 ⇒ 永远 != "0" ⇒ 判定失真。
run_matrix() {
  mvn -o -B -pl service/task/task-api -am -DargLine=-Xmx2g \
      -Dtest=TaskApiFailurePathMatrixTest -Dsurefire.failIfNoSpecifiedTests=false \
      test >"${TMP_OUT}" 2>&1
  local rc=$?
  grep -E "^\[INFO\] Tests run:" "${TMP_OUT}" | tail -1 | sed 's/^/   | /' >&2
  grep -E "BUILD SUCCESS|BUILD FAILURE" "${TMP_OUT}" | tail -1 | sed 's/^/   | /' >&2
  echo "${rc}"
}

backup "${REQ_FILE}"    GetTaskReq.java
backup "${HANDLER_FILE}" GlobalExceptionHandler.java

say "== 基线（未变异）必须转绿 =="
RC="$(run_matrix)"
if [ "${RC}" = "0" ]; then
  say "   ✅ 基线绿（exit 0）"
else
  say "❌ 基线就不绿（exit ${RC}）⇒ 后续变异判定无意义"
  tail -40 "${TMP_OUT}" | sed 's/^/   | /'
  exit 1
fi

# ─────────────────────────────────────────────────────────────
# T-M1：改 RAG 的 `user_id invalid` 文案 ⇒ 参数类断言必须转红
#        ⇒ 证明「错误码文案」这条断言真的有牙齿（不是恒真）
# ─────────────────────────────────────────────────────────────
say ""
say "== T-M1 RAG 的 USER_ID_INVALID 文案被改 ⇒ 必须转红 =="
mutate "${REQ_FILE}" \
  'public static final String USER_ID_INVALID = "user_id invalid";' \
  'public static final String USER_ID_INVALID = "user_id invalid MUTATED";'
RC="$(run_matrix)"
if [ "${RC}" != "0" ]; then
  say "   ✅ 精确转红（exit ${RC} · 文案漂移被抓到）"
  grep -E "FAIL|expected:|but was:|AssertionFailedError" "${TMP_OUT}" | head -8 | sed 's/^/   | /'
else
  say "   ❌ 没按预期转红（exit 0）⇒ 矩阵没在断言真实文案"
  tail -30 "${TMP_OUT}" | sed 's/^/   | /'
  FAILED=1
fi
restore "${REQ_FILE}" GetTaskReq.java

# ─────────────────────────────────────────────────────────────
# T-M2：改 RAG 的 X-Content-Type-Options 值 ⇒ nosniff 断言必须转红
#        ⇒ 证明「Go 的 http.Error 写死 nosniff」这条断言真的有牙齿
# ─────────────────────────────────────────────────────────────
say ""
say "== T-M2 RAG 的 nosniff 头被改 ⇒ 必须转红 =="
mutate "${HANDLER_FILE}" \
  '.header("X-Content-Type-Options", "nosniff")' \
  '.header("X-Content-Type-Options", "sniff")'
RC="$(run_matrix)"
if [ "${RC}" != "0" ]; then
  say "   ✅ 精确转红（exit ${RC} · 响应头漂移被抓到）"
  grep -E "FAIL|expected:|but was:|AssertionFailedError" "${TMP_OUT}" | head -8 | sed 's/^/   | /'
else
  say "   ❌ 没按预期转红（exit 0）⇒ 矩阵没在断言 nosniff"
  tail -30 "${TMP_OUT}" | sed 's/^/   | /'
  FAILED=1
fi
restore "${HANDLER_FILE}" GlobalExceptionHandler.java

say ""
say "== 还原后必须重新转绿 =="
RC="$(run_matrix)"
if [ "${RC}" = "0" ]; then
  say "   ✅ 还原后绿（exit 0）"
else
  say "   ❌ 还原后仍红（exit ${RC}）⇒ 还原没生效"
  tail -40 "${TMP_OUT}" | sed 's/^/   | /'
  FAILED=1
fi

say ""
if [ "${FAILED}" = "0" ]; then
  say "✅ 两条变异全部精确转红 + 还原后转绿 ⇒ 矩阵非假绿"
  exit 0
fi
say "❌ 有变异没转红 ⇒ 矩阵是假绿"
exit 1
