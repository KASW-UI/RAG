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
# 退出码：0 = 三条变异都精确转红 + 还原后转绿；1 = 有变异没转红（探针是假绿）；
#         2 = 环境问题导致本轮判定无效（**不等于假绿** ⇒ 见 run_probe 处的说明）。
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
ENV_BAD=0

# ⚠️ **exit 2 = 环境问题（不是「有差异」）** —— 判据来自 run.sh：`die()` 一律 exit 2
#    （前置检查不过 / Go 侧起不来 / 配置副本 sed 没命中 / 真源被改 …）。
#    🔴 **exit 2 的已知常见成因**：上一轮探针被 kill 后，**Nacos 里的旧实例还会存活一段时间**
#    （心跳超时前）⇒ 本轮 RAG 侧可能发现**陈旧实例** ⇒ 打到死实例 ⇒ INTERNAL ⇒ 熔断 ⇒ 5001
#    ⇒ ⇒ **脚本化连跑 5 次时，第 1 次之后最容易撞上** ⇒ ⇒ **exit 2 必须重试，不能当「假绿」**。
run_probe() {
  bash "${PROBE}" >"${TMP_OUT}" 2>&1
  local rc=$?
  if [ "${rc}" = "2" ]; then
    say "   · 探针报 exit 2（环境问题，不是差异）⇒ 等 6s 让 Nacos 心跳超时后再试一次"
    sleep 6
    bash "${PROBE}" >"${TMP_OUT}" 2>&1
    rc=$?
  fi
  echo ${rc}
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
elif [ "${RC}" = "2" ]; then
  say "   ⚠️ 环境问题（exit 2 · 重试后仍不可用）⇒ **本轮判定无效**，不等于「探针看不见漂移」"
  tail -25 "${TMP_OUT}" | sed 's/^/   | /'
  ENV_BAD=1
else
  say "   ❌ 没按预期转红（exit 0 = 两侧仍一致）⇒ **探针是假绿**"
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
elif [ "${RC}" = "2" ]; then
  say "   ⚠️ 环境问题（exit 2 · 重试后仍不可用）⇒ **本轮判定无效**，不等于「探针看不见漂移」"
  tail -25 "${TMP_OUT}" | sed 's/^/   | /'
  ENV_BAD=1
else
  say "   ❌ 没按预期转红（exit 0 = 两侧仍一致）⇒ **探针是假绿**"
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
elif [ "${RC}" = "2" ]; then
  say "   ⚠️ 环境问题（exit 2 · 重试后仍不可用）⇒ **本轮判定无效**，不等于「探针没在比冻结值」"
  tail -25 "${TMP_OUT}" | sed 's/^/   | /'
  ENV_BAD=1
else
  say "   ❌ 没按预期转红（exit 0 = Go 侧与冻结值仍一致）⇒ **探针没在比冻结值**"
  tail -25 "${TMP_OUT}" | sed 's/^/   | /'
  FAILED=1
fi
restore "${EXPECTED}" expected.txt

say ""
say "== 还原后必须重新转绿 =="
RC="$(run_probe)"
if [ "${RC}" = "0" ]; then
  say "   ✅ 还原后绿（exit 0）"
elif [ "${RC}" = "2" ]; then
  say "   ⚠️ 还原后仍报 exit 2（环境问题）⇒ **本轮判定无效**，不等于「还原没生效」"
  tail -30 "${TMP_OUT}" | sed 's/^/   | /'
  ENV_BAD=1
else
  say "   ❌ 还原后仍红（exit ${RC} = 两侧仍不一致）⇒ 还原没生效"
  tail -30 "${TMP_OUT}" | sed 's/^/   | /'
  FAILED=1
fi

say ""
if [ "${FAILED}" != "0" ]; then
  say "❌ 有变异没转红（两侧仍一致）⇒ 探针是假绿"
  exit 1
fi
if [ "${ENV_BAD}" != "0" ]; then
  say "⚠️ 有变异因**环境问题**（exit 2）未判定 ⇒ **本轮变异验证无效**（≠ 假绿）"
  say "   ⇒ 请在**无其它探针并发**时重跑（本探针的 Go 侧端口固定 + Nacos 共享 ⇒ 并发时易撞）"
  exit 2
fi
say "✅ 三条变异全部精确转红 + 还原后转绿 ⇒ 探针非假绿"
exit 0
