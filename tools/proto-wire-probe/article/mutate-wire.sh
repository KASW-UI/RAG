#!/usr/bin/env bash
# 变异验证：证明 tools/proto-wire-probe/article/run.sh **不是假绿**。
#
# 对应两条纪律：
#   · AGENTS.md §3.3.3「凡引入新的框架机制，必须配一条能真正触发该机制的测试」——
#     这里的形态是「**探针自己也得能被证伪**」；
#   · docs/PhaseGoal-judge.md §1.1 的 **R4 判据 ⑧**「有**变异验证**记录（改实现 ⇒ 探针必红）」
#     —— 本脚本就是 article-proto 的那条记录（此前**台账未见变异验证 ⇒ R4 不成立**）。
#
# 这条链有三段，每一段都必须能被变异打红，否则探针就只是「自己跟自己比」：
#   ① Go 真源生成码 → expected.txt   （run.sh 第 3 步的 diff）
#   ② Go 探针程序   → expected.txt   （改了 main.go 的取值 ⇒ 字节必变）
#   ③ expected.txt  → Java 侧断言    （改了 RAG 侧 proto 的**字段号** ⇒ RAG 侧字节必变 ⇒ 断言必红）
#
# 退出码：0 = 三条变异都精确转红 + 还原后转绿 + 三个文件内容哈希回到变异前；1 = 有变异没转红。
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAG_ROOT="$(cd "${HERE}/../../.." && pwd)"
PROBE="${HERE}/run.sh"
PROTO="service/article/article-proto/src/main/proto/article.proto"
EXPECTED="tools/proto-wire-probe/article/expected.txt"
GOMAIN="tools/proto-wire-probe/article/go/main.go"
PROTO_MODULE="service/article/article-proto"

cd "${RAG_ROOT}"

TMP_OUT="$(mktemp)"
BACKUP="$(mktemp -d)"
trap 'rm -f "${TMP_OUT}"; rm -rf "${BACKUP}"' EXIT

say() { printf '%s\n' "$*"; }

# mutate <文件> <旧串> <新串>
mutate() {
  python3 - "$1" "$2" "$3" <<'PY'
import sys, io
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = io.open(p, encoding="utf-8").read()
assert old in s, f"未命中: {old}"
io.open(p, "w", encoding="utf-8").write(s.replace(old, new, 1))
PY
}

FAILED=0

sha_of() { shasum -a 256 "$1" | awk '{print $1}'; }

# ⚠️ 还原**不用** `git checkout`（它会连未提交的落码一起回滚）⇒ 用「备份 + 复制」还原。
#    还原是否生效，用**内容哈希**判定（不能用 `git status`：这几个文件在首次提交前是
#    **未跟踪的新文件** ⇒ `git status` 只报「?? 路径」⇒ 看不出内容变没变 ⇒ 恒绿假绿）。
BEFORE_PROTO="$(sha_of "${PROTO}")"
BEFORE_EXPECTED="$(sha_of "${EXPECTED}")"
BEFORE_GOMAIN="$(sha_of "${GOMAIN}")"

cp "${PROTO}"    "${BACKUP}/article.proto"
cp "${EXPECTED}" "${BACKUP}/expected.txt"
cp "${GOMAIN}"   "${BACKUP}/main.go"

restore_proto()    { cp "${BACKUP}/article.proto" "${PROTO}"; }
restore_expected() { cp "${BACKUP}/expected.txt"    "${EXPECTED}"; }
restore_gomain()   { cp "${BACKUP}/main.go"          "${GOMAIN}"; }

run_probe() {
  bash "${PROBE}" >"${TMP_OUT}" 2>&1
  echo $?
}

say "== 基线（未变异）必须转绿 =="
BASE_RC="$(run_probe)"
if [ "${BASE_RC}" = "0" ]; then
  say "   ✅ 基线绿（exit 0）"
else
  say "❌ 基线就不绿（exit ${BASE_RC}）⇒ 后续变异判定无意义"
  tail -25 "${TMP_OUT}" | sed 's/^/   | /'
  exit 1
fi

# ─────────────────────────────────────────────────────────────
# M1：改 expected.txt 的一枚 hex ⇒ 第 3 步（Go 侧 diff）必须转红
# ─────────────────────────────────────────────────────────────
say ""
say "== M1 冻结的 expected.txt 被改 ⇒ Go 侧 diff 必须转红 =="
mutate "${EXPECTED}" "WIRE|upload_req|0a030102ff1205612e706e67" "WIRE|upload_req|0a030102fe1205612e706e67"
RC="$(run_probe)"
if [ "${RC}" = "1" ] && grep -q "Go 侧输出与 expected.txt 不一致" "${TMP_OUT}"; then
  say "   ✅ 精确转红（exit 1 · Go 侧 diff 失败）"
  grep -A4 -- "Go 侧输出与 expected.txt 不一致" "${TMP_OUT}" | sed 's/^/   | /'
else
  say "   ❌ 没按预期转红（exit ${RC}）⇒ 第 3 步没牙齿"
  tail -20 "${TMP_OUT}" | sed 's/^/   | /'
  FAILED=1
fi
restore_expected

# ─────────────────────────────────────────────────────────────
# M2：改 Go 探针程序的一个取值 ⇒ 第 3 步（Go 侧 diff）必须转红
#      ⇒ 证明 expected.txt 不是「谁都能写出来」的摆设
# ─────────────────────────────────────────────────────────────
say ""
say "== M2 Go 探针程序取值被改 ⇒ Go 侧 diff 必须转红 =="
mutate "${GOMAIN}" "LikeCount:       7," "LikeCount:       8,"
RC="$(run_probe)"
if [ "${RC}" = "1" ] && grep -q "Go 侧输出与 expected.txt 不一致" "${TMP_OUT}"; then
  say "   ✅ 精确转红（exit 1 · Go 侧 diff 失败）"
  grep -A4 -- "Go 侧输出与 expected.txt 不一致" "${TMP_OUT}" | sed 's/^/   | /'
else
  say "   ❌ 没按预期转红（exit ${RC}）⇒ 第 3 步没牙齿"
  tail -20 "${TMP_OUT}" | sed 's/^/   | /'
  FAILED=1
fi
restore_gomain

# ─────────────────────────────────────────────────────────────
# M3：改 RAG 侧 proto 的**字段号** ⇒ 第 4 步（Java 侧断言）必须转红
#      ⇒ 这是整条链最重要的一条：它证明「Java 侧真的在拿字节跟 Go 真源比」，
#        而不是在比「自己和自己」。
#      ⚠️ 只改**字段号**、不改字段名 ⇒ RAG 侧代码照常编译 ⇒ 红的原因**只可能**是字节不同
#         （若改成字段名 ⇒ 编译失败 ⇒ 那是另一种红，不能证明本条）。
#      ⚠️ 字段号必须取**未被占用**的：CreateArticleRequest 占了 1..7 ⇒ 取 8。
#      ⚠️ 选 `cover_image_url` 是因为它**是 optional** ⇒ 这一条同时证明
#         「**presence** 的那条断言也有牙齿」（设了值的 optional 落 wire）。
# ─────────────────────────────────────────────────────────────
say ""
say "== M3 RAG 侧 proto 字段号被改 ⇒ Java 侧断言必须转红 =="
mutate "${PROTO}" "  optional string cover_image_url = 4;" "  optional string cover_image_url = 8;"
# ⚠️ 必须先清掉 article-proto 的生成产物 ⇒ 否则 protoc 可能不重新生成 ⇒ 变异「没打中」
#    （PIT-HOTAPI-25：变异代码被静默沿用 ⇒ 假绿）
mvn -o -q -pl "${PROTO_MODULE}" clean >/dev/null 2>&1
RC="$(run_probe)"
if [ "${RC}" != "0" ] && grep -q "Java 侧 ArticleProtoWireParityTest 失败" "${TMP_OUT}"; then
  say "   ✅ 精确转红（exit ${RC} · Java 侧断言失败）"
  grep -E "ArticleProtoWireParityTest|expected:|but was:" "${TMP_OUT}" | head -8 | sed 's/^/   | /'
else
  say "   ❌ 没按预期转红（exit ${RC}）⇒ Java 侧断言没牙齿（变异可能没打中：proto 改动没重新生成）"
  tail -25 "${TMP_OUT}" | sed 's/^/   | /'
  FAILED=1
fi
restore_proto
mvn -o -q -pl "${PROTO_MODULE}" clean >/dev/null 2>&1

say ""
say "== 还原后必须重新转绿 =="
RC="$(run_probe)"
if [ "${RC}" = "0" ]; then
  say "   ✅ 还原后绿（exit 0）"
else
  say "   ❌ 还原后仍红（exit ${RC}）⇒ 还原没生效"
  tail -25 "${TMP_OUT}" | sed 's/^/   | /'
  FAILED=1
fi

say ""
say "== 还原校验：三个被变异的文件，内容哈希必须回到变异前 =="
RESTORED=1
check_restored() {
  local label="$1" file="$2" before="$3" now
  now="$(sha_of "${file}")"
  if [ "${now}" = "${before}" ]; then
    say "   ✅ ${label} 已还原（sha256=${now:0:16}…）"
  else
    say "   ❌ ${label} **没还原**（变异前 ${before:0:16}… → 现在 ${now:0:16}…）"
    RESTORED=0
  fi
}
check_restored "article.proto" "${PROTO}"    "${BEFORE_PROTO}"
check_restored "expected.txt"   "${EXPECTED}" "${BEFORE_EXPECTED}"
check_restored "go/main.go"     "${GOMAIN}"   "${BEFORE_GOMAIN}"
[ "${RESTORED}" = "1" ] || FAILED=1

say ""
if [ "${FAILED}" = "0" ]; then
  say "✅ 三条变异全部精确转红 + 还原后转绿 ⇒ 探针非假绿"
  exit 0
fi
say "❌ 有变异没转红 ⇒ 探针是假绿"
exit 1
