#!/usr/bin/env bash
# 变异验证：证明 tools/api-full-parity-probe/article/run.sh **不是假绿**（AGENTS.md §3.3.3）。
#
# 这条链有两段，每段都必须能被变异打红，否则探针就只是「自己跟自己比」：
#   ① RAG 侧的 JSON **字段序**（@JsonPropertyOrder）—— Go 按 struct 声明序，Jackson 按字母序
#   ② Go 侧的真实字节（expected.txt 是从真源生成码跑出来的）
#
# M1/M2 打 ①：去掉 @JsonPropertyOrder ⇒ RAG 侧字节与 expected.txt 不同 ⇒ 探针必须转红
# M3 打 ②：改 expected.txt 的一枚 hex ⇒ Go 侧与冻结值不同 ⇒ 探针必须转红
#
# ⚠️ **还原一律用「文件备份 + 复制回来」** —— 绝不用 `git checkout --`：
#    本 lane 的修复（@JsonPropertyOrder + javadoc）**还没提交** ⇒ `git checkout` 会
#    把它们一起回滚掉 ⇒ 变异跑完代码就不是原来的代码了。
#
# 退出码：0 = 三条变异都精确转红 + 还原后转绿；1 = 有变异没转红。
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAG_ROOT="$(cd "${HERE}/../../.." && pwd)"
PROBE="${HERE}/run.sh"
EXPECTED="${HERE}/expected.txt"
DTO="${RAG_ROOT}/service/article/article-api/src/main/java/com/rag/article/article/api/dto"

cd "${RAG_ROOT}"

TMP_OUT="$(mktemp)"
BAK_DIR="$(mktemp -d)"
# ⚠️ **不删临时目录**：`rm -rf` 在本机会被回收站守卫拦下（mavis-trash 失效 ⇒ 命令被整体拒绝）
#    ⇒ 留着 `/var/folders/.../tmp.*` 由系统清理（比让脚本自杀强）。
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

backup "${DTO}/Article.java"           article.java
backup "${DTO}/ListArticlesResp.java"  listresp.java
backup "${EXPECTED}"                    expected.txt

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
# M1：去掉 Article 的 @JsonPropertyOrder ⇒ 文章项的键序变回字母序 ⇒ 必须转红
# ─────────────────────────────────────────────────────────────
say ""
say "== M1 去掉 Article 的 @JsonPropertyOrder ⇒ 必须转红 =="
mutate "${DTO}/Article.java" '@JsonPropertyOrder({
        "id", "title", "brief", "content", "cover_image_url", "manual_type_tag",
        "secondary_tags", "author_id", "author_name", "create_time", "update_time",
        "status", "view_count", "like_count", "comment_count", "share_count", "ext_info"
})
' ''
RC="$(run_probe)"
if [ "${RC}" = "1" ]; then
  say "   ✅ 精确转红（exit 1 · 字段序漂移被抓到）"
  grep -E "❌|!= expected" "${TMP_OUT}" | head -6 | sed 's/^/   | /'
else
  say "   ❌ 没按预期转红（exit ${RC}）⇒ 探针看不见字段序漂移"
  tail -25 "${TMP_OUT}" | sed 's/^/   | /'
  FAILED=1
fi
restore "${DTO}/Article.java" article.java

# ─────────────────────────────────────────────────────────────
# M2：去掉 ListArticlesResp 的 @JsonPropertyOrder ⇒ 外层 data 的键序变 ⇒ 必须转红
# ─────────────────────────────────────────────────────────────
say ""
say "== M2 去掉 ListArticlesResp 的 @JsonPropertyOrder ⇒ 必须转红 =="
mutate "${DTO}/ListArticlesResp.java" '@JsonPropertyOrder({"articles", "total", "page", "page_size"})
' ''
RC="$(run_probe)"
if [ "${RC}" = "1" ]; then
  say "   ✅ 精确转红（exit 1 · 外层键序漂移被抓到）"
  grep -E "❌|!= expected" "${TMP_OUT}" | head -6 | sed 's/^/   | /'
else
  say "   ❌ 没按预期转红（exit ${RC}）⇒ 探针看不见外层键序漂移"
  tail -25 "${TMP_OUT}" | sed 's/^/   | /'
  FAILED=1
fi
restore "${DTO}/ListArticlesResp.java" listresp.java

# ─────────────────────────────────────────────────────────────
# M3：改冻结的 expected.txt 一枚 hex ⇒ Go 侧与冻结值不同 ⇒ 必须转红
#      ⇒ 证明 expected.txt 不是「谁都能写出来」的摆设
# ─────────────────────────────────────────────────────────────
say ""
say "== M3 冻结的 expected.txt 被改 ⇒ 必须转红 =="
mutate "${EXPECTED}" 'CASE|list_articles|7b22' 'CASE|list_articles|7b23'
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
