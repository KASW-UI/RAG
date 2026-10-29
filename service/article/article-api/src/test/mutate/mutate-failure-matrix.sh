#!/usr/bin/env bash
# =============================================================================
# 变异验证：证明 ArticleApiFailurePathMatrixTest **不是假绿**（AGENTS.md §3.3.3）。
#
# 为什么需要它：
#   「矩阵里有 104 条断言」本身**不证明**这些断言有判别力 ——
#   一条恒真的断言（assertNotNull(body)）也能贡献 104 条「绿」。
#   ⇒ 必须**逐条把判据改错**，看它**恰好**把对应的那几条用例打红。
#
# 本脚本的判定口径（比「跑一遍红了」严格）：
#   ① 基线（未变异）必须**全绿**；
#   ② 每条变异必须**恰好**把**指定的那几条用例**打红
#      —— 不是「至少红一条」，而是**红的方法集合必须与预期完全相等**
#      （多红一条 = 判据之间有意外的耦合；少红一条 = 这条断言是摆设）；
#   ③ 还原后必须**重新全绿**。
#
# ⚠️ **还原一律用「文件备份 + 复制回来」** —— 绝不用 `git checkout --`：
#    本轮的新增文件**还没提交** ⇒ `git checkout` 会把它们一起回滚掉
#    ⇒ 变异跑完代码就不是原来的代码了。
#
# ⚠️ **不用 `rm -rf`**：本机有回收站守卫会把含 `rm -rf` 的命令**整体拒绝**
#    ⇒ 临时目录留着由系统清理。
#
# 退出码：0 = 全部变异精确转红 + 还原后转绿；1 = 有变异没按预期转红。
# =============================================================================
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# HERE = <repo>/service/article/article-api/src/test/mutate
MODULE="$(cd "${HERE}/../../.." && pwd)"          # <repo>/service/article/article-api
RAG_ROOT="$(cd "${MODULE}/../../.." && pwd)"      # <repo>

cd "${RAG_ROOT}"

TEST_CLASS="ArticleApiFailurePathMatrixTest"
REPORT="${MODULE}/target/surefire-reports/TEST-com.rag.article.article.api.${TEST_CLASS}.xml"

TMP_OUT="$(mktemp)"
BAK_DIR="$(mktemp -d)"
trap 'restore_all' EXIT

say() { printf '%s\n' "$*"; }

FAILED=0

# ---------- 备份 / 还原 ----------
BACKED_UP=""

backup() {
  local src="$1"
  local name="$2"
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
  local src="$1"
  local name="$2"
  cp "${BAK_DIR}/${name}" "${src}"
}

# ---------- 变异 ----------
# mutate <文件> <旧串> <新串> —— 只替换**第一处**命中。
mutate() {
  python3 - "$1" "$2" "$3" <<'PY'
import sys, io
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = io.open(p, encoding="utf-8").read()
assert old in s, f"未命中: {old[:80]}"
io.open(p, "w", encoding="utf-8").write(s.replace(old, new, 1))
PY
}

# ---------- 跑测试 ----------
run_tests() {
  mvn -o -B -pl service/article/article-api \
      -DargLine=-Xmx2g \
      -Dtest="${TEST_CLASS}" -DfailIfNoSpecifiedTests=false \
      test >"${TMP_OUT}" 2>&1
  echo $?
}

# 从 surefire XML 里抽出「失败/错误的用例所属的**测试方法名**」。
# ⚠️ 只取方法名（去掉 `(Endpoint)[N]` 后缀）⇒ 断言的是「哪一类用例红了」，
#    不受参数化序号变化影响。
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

# check_mutation <编号> <说明> <预期红的方法集合（空格分隔）>
check_mutation() {
  local id="$1"; local desc="$2"; local expect="$3"
  say ""
  say "== ${id} ${desc} ⇒ 必须精确转红 =="
  rm -f "${REPORT}"
  local rc
  rc="$(run_tests)"
  if [ ! -f "${REPORT}" ]; then
    say "   ❌ 变异后测试**没跑起来**（编译失败 / 上下文起不来）⇒ 判定无意义"
    grep -E 'ERROR|BUILD FAILURE' "${TMP_OUT}" | head -8 | sed 's/^/   | /'
    FAILED=1
    return
  fi
  local got
  got="$(red_methods "${REPORT}")"
  local want
  want="$(printf '%s\n' ${expect} | sort -u | tr '\n' ' ' | sed 's/ *$//')"
  local gotn
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
API="${MODULE}/src/main/java/com/rag/article/article/api"
backup "${API}/service/ArticleCreateService.java"    ArticleCreateService.java
backup "${API}/service/ArticleGetService.java"        ArticleGetService.java
backup "${API}/service/ArticleApiSupport.java"        ArticleApiSupport.java
backup "${API}/http/GoStyleGrpcStatusMapper.java"     GoStyleGrpcStatusMapper.java
backup "${API}/advice/GlobalExceptionHandler.java"    GlobalExceptionHandler.java

# =============================================================================
# 基线（未变异）必须转绿
# =============================================================================
say "== 基线（未变异）必须转绿 =="
BASE_RC="$(run_tests)"
if [ "${BASE_RC}" = "0" ]; then
  say "   ✅ 基线绿（exit 0）"
else
  say "❌ 基线就不绿（exit ${BASE_RC}）⇒ 后续变异判定无意义"
  tail -30 "${TMP_OUT}" | sed 's/^/   | /'
  exit 1
fi

# =============================================================================
# M1 —— create 的 AlreadyExists 分支：1001 ⇒ 5001
# =============================================================================
mutate "${API}/service/ArticleCreateService.java" \
  'throw business(ErrorCode.ERROR_ARTICLE_EXIST);' \
  'throw business(ErrorCode.ERROR_SERVER_COMMON);'
check_mutation "M1" "create 的 AlreadyExists 分支 1001 ⇒ 5001" "createConflictMatrix"
restore_one "${API}/service/ArticleCreateService.java" ArticleCreateService.java

# =============================================================================
# M2 —— public 读的 NotFound 分支：1002 ⇒ 1015
# =============================================================================
mutate "${API}/service/ArticleGetService.java" \
  'if (code == Status.Code.NOT_FOUND) {
                throw business(ErrorCode.ERROR_ARTICLE_NONE);
            }' \
  'if (code == Status.Code.NOT_FOUND) {
                throw business(ErrorCode.CODE_SERVER_BUSY);
            }'
check_mutation "M2" "public 读的 NotFound 分支 1002 ⇒ 1015" "rpcNotFoundMatrix"
restore_one "${API}/service/ArticleGetService.java" ArticleGetService.java

# =============================================================================
# M3 —— public 读的「未发布 ⇒ 1002」闸门：1002 ⇒ 1015
# =============================================================================
mutate "${API}/service/ArticleGetService.java" \
  'if (res == null || !res.hasArticle()
                || !ArticleApiSupport.isPublished(res.getArticle().getStatusValue())) {
            throw business(ErrorCode.ERROR_ARTICLE_NONE);
        }' \
  'if (res == null || !res.hasArticle()
                || !ArticleApiSupport.isPublished(res.getArticle().getStatusValue())) {
            throw business(ErrorCode.CODE_SERVER_BUSY);
        }'
check_mutation "M3" "public 读的「未发布 ⇒ 1002」闸门 1002 ⇒ 1015" "nonPublishedArticleIsAlso1002"
restore_one "${API}/service/ArticleGetService.java" ArticleGetService.java

# =============================================================================
# M4 —— upload 的 gRPC→HTTP 状态映射：Unavailable ⇒ 503 改成 400
# =============================================================================
mutate "${API}/http/GoStyleGrpcStatusMapper.java" \
  'case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;' \
  'case UNAVAILABLE -> HttpStatus.BAD_REQUEST;'
check_mutation "M4" "upload 的 Unavailable ⇒ 503 改成 400" "rpcUnavailableMatrix"
restore_one "${API}/http/GoStyleGrpcStatusMapper.java" GoStyleGrpcStatusMapper.java

# =============================================================================
# M5 —— claim 取不到：1005 ⇒ 1006
# =============================================================================
mutate "${API}/service/ArticleApiSupport.java" \
  'throw new BusinessException(ErrorCode.ERROR_UNAUTHORIZED.getCode(), msg(ErrorCode.ERROR_UNAUTHORIZED));' \
  'throw new BusinessException(ErrorCode.ERROR_ARTICLE_FORBIDDEN.getCode(), msg(ErrorCode.ERROR_ARTICLE_FORBIDDEN));'
check_mutation "M5" "claim 取不到的 1005 ⇒ 1006" "tokenWithoutUserIdClaimIs200With1005"
restore_one "${API}/service/ArticleApiSupport.java" ArticleApiSupport.java

# =============================================================================
# M6 —— 鉴权失败的状态码：401 ⇒ 403
# =============================================================================
mutate "${API}/advice/GlobalExceptionHandler.java" \
  'return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();' \
  'return ResponseEntity.status(HttpStatus.FORBIDDEN).build();'
check_mutation "M6" "鉴权失败 401 ⇒ 403" \
  "missingTokenIs401WithEmptyBody invalidSignatureIs401WithEmptyBody routerErrorsBypassAuth"
restore_one "${API}/advice/GlobalExceptionHandler.java" GlobalExceptionHandler.java

# =============================================================================
# 还原后必须重新转绿
# =============================================================================
say ""
say "== 还原后必须重新转绿 =="
RC="$(run_tests)"
if [ "${RC}" = "0" ]; then
  say "   ✅ 还原后绿（exit 0）"
else
  say "   ❌ 还原后仍红（exit ${RC}）⇒ 还原没生效"
  tail -30 "${TMP_OUT}" | sed 's/^/   | /'
  FAILED=1
fi

say ""
if [ "${FAILED}" = "0" ]; then
  say "✅ 全部变异精确转红 + 还原后转绿 ⇒ 失败路径矩阵非假绿"
  exit 0
fi
say "❌ 有变异没按预期转红 ⇒ 失败路径矩阵是假绿"
exit 1
