#!/usr/bin/env bash
# 变异验证：证明 tools/g3-anchor-check/check-anchors.py **不是假绿**（AGENTS.md §3.3.3）。
# 每条变异都对应一类**机器可判**的漂移 ⇒ 跑完必须**精确转红**，且**还原后必须转绿**。
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAG_ROOT="$(cd "${HERE}/../.." && pwd)"
GATE="${HERE}/check-anchors.py"

say() { printf '%s\n' "$*"; }

# run <期望分类> —— 跑一次守门，把输出落到临时文件
run_gate() {
  ( cd "${RAG_ROOT}" && python3 "${GATE}" ) >"${TMP_OUT}" 2>&1
  echo $?
}

TMP_OUT="$(mktemp)"
trap 'rm -f "${TMP_OUT}"' EXIT

# mutate <文件> <旧串> <新串>
mutate() {
  python3 - "${RAG_ROOT}/$1" "$2" "$3" <<'PY'
import sys, io
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = io.open(p, encoding="utf-8").read()
assert old in s, f"未命中: {old}"
io.open(p, "w", encoding="utf-8").write(s.replace(old, new, 1))
PY
}

FAILED=0
run_case() {
  local name="$1" expect="$2" file="$3" old="$4" new="$5"
  say ""
  say "== ${name} =="
  mutate "${file}" "${old}" "${new}"
  local rc; rc="$(run_gate)"
  if [ "${rc}" = "1" ] && grep -q "${expect}" "${TMP_OUT}"; then
    say "   ✅ 精确转红（exit 1 · 分类 ${expect}）"
    grep -A3 -- "--- ${expect}" "${TMP_OUT}" | sed 's/^/   | /'
  else
    say "   ❌ 没按预期转红（exit ${rc}）"
    tail -12 "${TMP_OUT}" | sed 's/^/   | /'
    FAILED=1
  fi
  mutate "${file}" "${new}" "${old}"   # 还原
}

say "== 基线（未变异）必须转绿 =="
BASE_RC="$(run_gate)"
if [ "${BASE_RC}" != "0" ]; then
  say "❌ 基线就不绿（exit ${BASE_RC}）⇒ 后续变异判定无意义"
  tail -20 "${TMP_OUT}" | sed 's/^/   | /'
  exit 1
fi
say "   ✅ 基线绿（exit 0）"

run_case "M1 行号越界（末尾越过 EOF）" "LINE_OOB" \
  "service/article/article-common/src/main/java/com/rag/article/article/common/errmsg/ErrorCode.java" \
  "get_article_handler.go:28-32" "get_article_handler.go:35-40"

run_case "M2 文件指错（行号对 · 文件错）" "LINE_OOB" \
  "service/article/article-rpc/src/main/java/com/rag/article/article/rpc/domain/ArticleEntity.java" \
  "update_article_logic.go:184" "list_articles_logic.go:184"

run_case "M3 版本写错（引的不是真源 pin 的那一份）" "VERSION_MISMATCH" \
  "service/article/article-rpc/src/main/java/com/rag/article/article/rpc/config/ArticleMetaObjectHandler.java" \
  "gorm@v1.31.1/callbacks/update.go:238-241" "gorm@v1.31.2/callbacks/update.go:243-259"

run_case "M4 出处缺文件名" "FILE_MISSING" \
  "service/hot/hot-api/src/test/java/com/rag/hot/hot/api/client/CapturingClientInterceptor.java" \
  "getHotArticlesLogic_test.go:35-37" "_test.go:35-37"

say ""
say "== 还原后必须重新转绿 =="
RC="$(run_gate)"
if [ "${RC}" = "0" ]; then
  say "   ✅ 还原后绿（exit 0）"
else
  say "   ❌ 还原后仍红（exit ${RC}）⇒ 还原没生效"
  FAILED=1
fi

say ""
if [ "${FAILED}" = "0" ]; then
  say "✅ 四条变异全部精确转红 + 还原后转绿 ⇒ 守门非假绿"
  exit 0
fi
say "❌ 有变异没转红 ⇒ 守门是假绿"
exit 1
