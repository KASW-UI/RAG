#!/usr/bin/env bash
# =============================================================================
# 变异验证：证明 hot 域的失败路径矩阵（hot-api + hot-rpc）**不是假绿**
#            （AGENTS.md §3.3.3）。
#
# 为什么需要它：
#   「矩阵里有 N 条断言」本身**不证明**这些断言有判别力 ——
#   一条恒真的断言也能贡献 N 条「绿」。
#   ⇒ 必须**逐条把判据改错**，看它**恰好**把对应的那几条用例打红。
#
# 本脚本的判定口径（比「跑一遍红了」严格）：
#   ① 基线（未变异）必须**全绿**；
#   ② 每条变异必须**恰好**把**指定的那几条用例**打红
#      —— 不是「至少红一条」，而是**红的方法集合必须与预期完全相等**；
#   ③ 还原后必须**重新全绿**。
#
# ⚠️ **还原一律用「文件备份 + 复制回来」** —— 绝不用 `git checkout --`：
#    本轮的新增文件**还没提交** ⇒ `git checkout` 会把它们一起回滚掉。
# ⚠️ **不用 `rm -rf`**：本机回收站守卫会把含 `rm -rf` 的命令**整体拒绝**。
#
# 退出码：0 = 全部变异精确转红 + 还原后转绿；1 = 有变异没按预期转红。
# =============================================================================
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# HERE = <repo>/service/hot/hot-api/src/test/mutate
API_MODULE="$(cd "${HERE}/../../.." && pwd)"        # <repo>/service/hot/hot-api
RPC_MODULE="$(cd "${API_MODULE}/../hot-rpc" && pwd)"  # <repo>/service/hot/hot-rpc
RAG_ROOT="$(cd "${API_MODULE}/../../.." && pwd)"      # <repo>

cd "${RAG_ROOT}"

API_TEST_CLASS="HotApiFailurePathMatrixTest"
API_REPORT="${API_MODULE}/target/surefire-reports/TEST-com.rag.hot.hot.api.${API_TEST_CLASS}.xml"
RPC_TEST_CLASS="HotRpcFailurePathMatrixTest"
RPC_REPORT="${RPC_MODULE}/target/surefire-reports/TEST-com.rag.hot.hot.rpc.${RPC_TEST_CLASS}.xml"

TMP_OUT="$(mktemp)"
BAK_DIR="$(mktemp -d)"
BACKED_UP=""
trap 'restore_all' EXIT

say() { printf '%s\n' "$*"; }

FAILED=0

backup() {
  local src="$1"; local name="$2"
  cp "${src}" "${BAK_DIR}/${name}"
  BACKED_UP="${BACKED_UP} ${src}"
}

restore_all() {
  for f in ${BACKED_UP}; do
    local base
    base="$(basename "${f}")"
    if [ -f "${BAK_DIR}/${base}" ]; then
      cp "${BAK_DIR}/${base}" "${f}"
    fi
  done
}

restore_one() {
  cp "${BAK_DIR}/$2" "$1"
}

mutate() {
  python3 - "$1" "$2" "$3" <<'PY'
import sys, io
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = io.open(p, encoding="utf-8").read()
assert old in s, f"未命中: {old[:80]}"
io.open(p, "w", encoding="utf-8").write(s.replace(old, new, 1))
PY
}

run_api_tests() {
  mvn -o -B -pl service/hot/hot-api \
      -DargLine=-Xmx2g \
      -Dtest="${API_TEST_CLASS}" -DfailIfNoSpecifiedTests=false \
      test >"${TMP_OUT}" 2>&1
  echo $?
}

run_rpc_tests() {
  mvn -o -B -pl service/hot/hot-rpc \
      -DargLine=-Xmx2g \
      -Dtest="${RPC_TEST_CLASS}" -DfailIfNoSpecifiedTests=false \
      test >"${TMP_OUT}" 2>&1
  echo $?
}

red_methods() {
  python3 - "$1" <<'PY'
import sys, xml.etree.ElementTree as ET
tree = ET.parse(sys.argv[1])
names = set()
for tc in tree.getroot().iter('testcase'):
    if tc.find('failure') is not None or tc.find('error') is not None:
        names.add((tc.get('name') or '').split('(')[0])
print('\n'.join(sorted(names)))
PY
}

# check_mutation <编号> <说明> <runner> <report> <预期红的方法集合>
check_mutation() {
  local id="$1"; local desc="$2"; local runner="$3"; local report="$4"; local expect="$5"
  say ""
  say "== ${id} ${desc} ⇒ 必须精确转红 =="
  rm -f "${report}"
  local rc
  rc="$("${runner}")"
  if [ ! -f "${report}" ]; then
    say "   ❌ 变异后测试**没跑起来**（编译失败 / 上下文起不来）⇒ 判定无意义"
    grep -E 'ERROR|BUILD FAILURE' "${TMP_OUT}" | head -8 | sed 's/^/   | /'
    FAILED=1
    return
  fi
  local got want gotn
  got="$(red_methods "${report}")"
  want="$(printf '%s\n' ${expect} | sort -u | tr '\n' ' ' | sed 's/ *$//')"
  gotn="$(printf '%s' "${got}" | tr '\n' ' ' | sed 's/ *$//')"
  if [ "${gotn}" = "${want}" ]; then
    say "   ✅ 精确转红（红的方法集合 == 预期）"
    say "      预期 = ${want}"
    say "      实际 = ${gotn}"
  else
    say "   ❌ 转红集合与预期不符 ⇒ 判据之间有意外耦合，或这条断言是摆设"
    say "      预期 = ${want}"
    say "      实际 = ${gotn}"
    FAILED=1
  fi
}

# =============================================================================
# 备份
# =============================================================================
API_SRC="${API_MODULE}/src/main/java/com/rag/hot/hot/api"
RPC_SRC="${RPC_MODULE}/src/main/java/com/rag/hot/hot/rpc"
backup "${API_SRC}/service/HotRankPaginator.java"        HotRankPaginator.java
backup "${API_SRC}/service/HotArticleQueryService.java"  HotArticleQueryService.java
backup "${API_SRC}/service/HotArticleHydrator.java"       HotArticleHydrator.java
backup "${RPC_SRC}/service/InteractionStubService.java"   InteractionStubService.java

# =============================================================================
# 基线（未变异）必须转绿
# =============================================================================
say "== 基线 hot-api（未变异）必须转绿 =="
BASE_RC="$(run_api_tests)"
if [ "${BASE_RC}" = "0" ]; then
  say "   ✅ 基线绿（exit 0）"
else
  say "❌ 基线就不绿（exit ${BASE_RC}）⇒ 后续变异判定无意义"
  tail -30 "${TMP_OUT}" | sed 's/^/   | /'
  exit 1
fi

say ""
say "== 基线 hot-rpc（未变异）必须转绿 =="
BASE_RC="$(run_rpc_tests)"
if [ "${BASE_RC}" = "0" ]; then
  say "   ✅ 基线绿（exit 0）"
else
  say "❌ 基线就不绿（exit ${BASE_RC}）⇒ 后续变异判定无意义"
  tail -30 "${TMP_OUT}" | sed 's/^/   | /'
  exit 1
fi

# =============================================================================
# H1 —— page_size 上限：50 ⇒ 51
# =============================================================================
mutate "${API_SRC}/service/HotRankPaginator.java" \
  'public static final int MAX_PAGE_SIZE = 50;' \
  'public static final int MAX_PAGE_SIZE = 51;'
check_mutation "H1" "hot-api 的 page_size 上限 50 ⇒ 51" run_api_tests "${API_REPORT}" \
  "pageSizeIsClampedTo50"
restore_one "${API_SRC}/service/HotRankPaginator.java" HotRankPaginator.java

# =============================================================================
# H2 —— 默认页码：1 ⇒ 2
# =============================================================================
mutate "${API_SRC}/service/HotRankPaginator.java" \
  'public static final int DEFAULT_PAGE = 1;' \
  'public static final int DEFAULT_PAGE = 2;'
check_mutation "H2" "hot-api 的默认页码 1 ⇒ 2" run_api_tests "${API_REPORT}" \
  "nonPositivePaginationFallsBackToDefault"
restore_one "${API_SRC}/service/HotRankPaginator.java" HotRankPaginator.java

# =============================================================================
# H3 —— fatal 的业务码：5001 ⇒ 5002
# =============================================================================
mutate "${API_SRC}/service/HotArticleQueryService.java" \
  'int code = HotErrorCode.ERROR_SERVER_COMMON.getCode();' \
  'int code = 5002;'
check_mutation "H3" "hot-api 的 fatal 业务码 5001 ⇒ 5002" run_api_tests "${API_REPORT}" \
  "hotRpcNilResponseIsFatal hotRpcUnavailableIsFatal"
restore_one "${API_SRC}/service/HotArticleQueryService.java" HotArticleQueryService.java

# =============================================================================
# H4 —— 「未发布 ⇒ skip」闸门被改坏（PUBLISHED 检查反相 ⇒ 草稿漏进热榜）
#
# ⚠️ **第一版 H4 打偏了（已订正，保留记录）**：原本把
#    「补全失败 ⇒ return null（skip）」改成「throw e（整包冒泡）」，
#    **实测红集合为空** ⇒ 变异**没打中** ⇒ 原因：
#    `HotArticleHydrator#hydrate` 的 `catch (RuntimeException e)`（:127-134）
#    会把任务体抛出的异常**吞掉**（Go 侧 `wg.Wait()` 同样不会失败）
#    ⇒ 「补全失败 ⇒ 整包失败」在当前实现里**结构上不可达**
#    ⇒ 改打**内层的状态闸门**（真正决定输出内容的那一处）。
#    （教训 ⇒ `docs/Pitfalls/article/api.md` **P-21**：「仍全绿」的第一嫌疑是「**变异没打中**」）
# =============================================================================
mutate "${API_SRC}/service/HotArticleHydrator.java" \
  'if (article.getStatus() != ArticleStatus.PUBLISHED) {' \
  'if (article.getStatus() == ArticleStatus.ARTICLE_STATUS_UNSPECIFIED) {'
check_mutation "H4" "hot-api 的「未发布 ⇒ skip」闸门被改坏（草稿漏进热榜）" run_api_tests "${API_REPORT}" \
  "unpublishedOrMissingArticleIsSkipped"
restore_one "${API_SRC}/service/HotArticleHydrator.java" HotArticleHydrator.java

# =============================================================================
# H5 —— like 的 UNIMPLEMENTED 文案：逐字改错
# =============================================================================
mutate "${RPC_SRC}/service/InteractionStubService.java" \
  'public static final String LIKE_UNIMPLEMENTED_MSG = "LikeArticle not implemented yet";' \
  'public static final String LIKE_UNIMPLEMENTED_MSG = "LikeArticle not implemented yet!!";'
check_mutation "H5" "hot-rpc 的 like UNIMPLEMENTED 文案被改错" run_rpc_tests "${RPC_REPORT}" \
  "likeArticleIsUnimplemented"
restore_one "${RPC_SRC}/service/InteractionStubService.java" InteractionStubService.java

# =============================================================================
# 还原后必须重新转绿
# =============================================================================
say ""
say "== 还原后 hot-api 必须重新转绿 =="
RC="$(run_api_tests)"
if [ "${RC}" = "0" ]; then
  say "   ✅ 还原后绿（exit 0）"
else
  say "   ❌ 还原后仍红（exit ${RC}）⇒ 还原没生效"
  tail -30 "${TMP_OUT}" | sed 's/^/   | /'
  FAILED=1
fi

say ""
say "== 还原后 hot-rpc 必须重新转绿 =="
RC="$(run_rpc_tests)"
if [ "${RC}" = "0" ]; then
  say "   ✅ 还原后绿（exit 0）"
else
  say "   ❌ 还原后仍红（exit ${RC}）⇒ 还原没生效"
  tail -30 "${TMP_OUT}" | sed 's/^/   | /'
  FAILED=1
fi

say ""
if [ "${FAILED}" = "0" ]; then
  say "✅ 全部变异精确转红 + 还原后转绿 ⇒ hot 域失败路径矩阵非假绿"
  exit 0
fi
say "❌ 有变异没按预期转红 ⇒ hot 域失败路径矩阵是假绿"
exit 1
