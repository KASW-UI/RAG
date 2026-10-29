#!/usr/bin/env bash
#
# 变异验证：证明 tools/api-full-parity-probe/follow/run.sh **不是假绿**（AGENTS.md §3.3.3）。
#
# 这条链有三段，每段都必须能被变异打红，否则探针就只是「自己跟自己比」：
#   ① RAG 侧的**真实字节**（Jackson 的字段序）
#   ② Go 侧的真实字节（expected.txt 是从真源跑出来后冻结的）
#   ③ **两侧各自的 DB 数据**（否则「两侧都空」分不出「相同」与「都空」）
#   ④ **失败路径**：一侧挂掉时探针必须转红（= FAILING-010 的守卫）
#
# M1 打 ①：去掉 RecommendUser 的 @JsonPropertyOrder ⇒ 字段序漂移 ⇒ 必须转红
# M2 打 ②：改 expected.txt 里一枚 hex ⇒ Go 侧与冻结值不符 ⇒ 必须转红
# M3 打 ③：让 RAG 侧多灌一行 ⇒ 两侧 DB 不同 ⇒ 数据用例必须转红
# M4 打 ④：RAG 侧中途挂掉 ⇒ 探针必须转红（**不能**沿用上一次的残留 body）
# M5 复现 FAILING-010：把 run.sh 改回「固定 -o 文件 + 去掉 000 守卫」
#    ⇒ 同样的挂掉场景下探针会**报 ✅（假绿）** ⇒ 证明 M4 的红是守卫带来的
#
# ⚠️ **还原一律用「文件备份 + 复制回来」** —— 绝不用 `git checkout --`：
#    本 lane 的改动可能还没提交 ⇒ `git checkout` 会把它们一起回滚掉。
#
# 退出码：0 = 全部变异按预期表现 + 还原后转绿；1 = 有变异没按预期表现。
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAG_ROOT="$(cd "${HERE}/../../.." && pwd)"
PROBE="${HERE}/run.sh"
EXPECTED="${HERE}/expected.txt"
DTO="${RAG_ROOT}/service/follow/follow-api/src/main/java/com/rag/follow/follow/api/dto/RecommendUser.java"

cd "${RAG_ROOT}"

TMP_OUT="$(mktemp)"
BAK_DIR="$(mktemp -d)"
# ⚠️ **不删临时目录**：`rm -rf` 在本机会被回收站守卫拦下 ⇒ 留着由系统清理。
trap 'rm -f "${TMP_OUT}" >/dev/null 2>&1' EXIT

say() { printf '%s\n' "$*"; }

FAILED=0

run_probe() {   # $1.. = 传给 run.sh 的额外 env
  env "$@" bash "${PROBE}" >"${TMP_OUT}" 2>&1
  echo $?
}

backup()  { cp "$1" "${BAK_DIR}/$2"; }
restore() { cp "${BAK_DIR}/$2" "$1"; }

mutate() {   # mutate <文件> <旧串> <新串>
  python3 - "$1" "$2" "$3" <<'PY'
import sys, io
p, old, new = sys.argv[1], sys.argv[2], sys.argv[3]
s = io.open(p, encoding="utf-8").read()
assert old in s, f"未命中: {old[:60]}"
io.open(p, "w", encoding="utf-8").write(s.replace(old, new, 1))
PY
}

case_is_red() {   # $1 = 用例名 ⇒ 该用例在输出里被判为 ❌
  grep -E "❌ ${1}" "${TMP_OUT}" >/dev/null 2>&1
}

backup "${DTO}"       recommend-user.java
backup "${EXPECTED}"  expected.txt
backup "${PROBE}"     run.sh

say "== 基线（未变异）必须转绿 =="
BASE_RC="$(run_probe)"
if [ "${BASE_RC}" = "0" ]; then
  say "   ✅ 基线绿（exit 0）"
else
  say "❌ 基线就不绿（exit ${BASE_RC}）⇒ 后续变异判定无意义"
  tail -40 "${TMP_OUT}" | sed 's/^/   | /'
  exit 1
fi

# ─────────────────────────────────────────────────────────────
# M1：去掉 RecommendUser 的 @JsonPropertyOrder ⇒ 推荐项的键序变回字母序 ⇒ 必须转红
# ─────────────────────────────────────────────────────────────
say ""
say "== M1 去掉 RecommendUser 的 @JsonPropertyOrder ⇒ 必须转红 =="
mutate "${DTO}" '@JsonPropertyOrder({"target_id", "mutual_score"})
' ''
RC="$(run_probe)"
if [ "${RC}" = "1" ] && case_is_red "recommendations_seeded"; then
  say "   ✅ 精确转红（exit 1 · recommendations_seeded 那条被判不等）"
  grep -E "❌ recommendations_seeded|Go != RAG" "${TMP_OUT}" | head -4 | sed 's/^/   | /'
else
  say "   ❌ 没按预期转红（exit ${RC}；exit 2 = **环境问题**，不是变异没生效）⇒ 探针看不见字段序漂移"
  tail -25 "${TMP_OUT}" | sed 's/^/   | /'
  FAILED=1
fi
restore "${DTO}" recommend-user.java

# ─────────────────────────────────────────────────────────────
# M2：改冻结的 expected.txt 一枚 hex ⇒ Go 侧与冻结值不同 ⇒ 必须转红
#      ⇒ 证明 expected.txt 不是「谁都能写出来」的摆设，它**参与判定**
# ─────────────────────────────────────────────────────────────
say ""
say "== M2 冻结的 expected.txt 被改 ⇒ 必须转红 =="
mutate "${EXPECTED}" 'CASE|follow_list_seeded|200|7b22' 'CASE|follow_list_seeded|200|7b23'
RC="$(run_probe)"
if [ "${RC}" = "1" ] && case_is_red "follow_list_seeded"; then
  say "   ✅ 精确转红（exit 1 · follow_list_seeded 与冻结值不符）"
  grep -E "❌ follow_list_seeded|Go != expected" "${TMP_OUT}" | head -4 | sed 's/^/   | /'
else
  say "   ❌ 没按预期转红（exit ${RC}；exit 2 = **环境问题**，不是变异没生效）⇒ 探针没在比冻结值"
  tail -25 "${TMP_OUT}" | sed 's/^/   | /'
  FAILED=1
fi
restore "${EXPECTED}" expected.txt

# ─────────────────────────────────────────────────────────────
# M3：让 **RAG 侧**多灌一行 ⇒ 两侧 DB 内容不同 ⇒ 数据用例必须转红
#      ⇒ 证明探针真的在读**两侧各自的库**，而不是「两边都空」
# ─────────────────────────────────────────────────────────────
say ""
say "== M3 RAG 侧多灌一行（两侧库不同）⇒ 必须转红 =="
mutate "${PROBE}" 'for c in "${GO_PG_CONTAINER}" "${RAG_PG_CONTAINER}"; do
  docker exec "${c}" psql -U admin -d first_db -v ON_ERROR_STOP=1 -c "${SEED_FOLLOW}" >/dev/null || die "灌 follow_relation 失败：${c}"' \
'for c in "${GO_PG_CONTAINER}" "${RAG_PG_CONTAINER}"; do
  docker exec "${c}" psql -U admin -d first_db -v ON_ERROR_STOP=1 -c "${SEED_FOLLOW}" >/dev/null || die "灌 follow_relation 失败：${c}"
  if [ "${c}" = "${RAG_PG_CONTAINER}" ]; then
    docker exec "${c}" psql -U admin -d first_db -v ON_ERROR_STOP=1 -c "INSERT INTO follow_relation (user_id,target_id,create_time,update_time) VALUES (1001,1009,now(),now());" >/dev/null
  fi'
RC="$(run_probe)"
if [ "${RC}" = "1" ] && case_is_red "follow_list_seeded"; then
  say "   ✅ 精确转红（exit 1 · 两侧库不同被抓到）"
  grep -E "❌ follow_list_seeded|Go != RAG" "${TMP_OUT}" | head -4 | sed 's/^/   | /'
else
  say "   ❌ 没按预期转红（exit ${RC}；exit 2 = **环境问题**，不是变异没生效）⇒ 探针没在读两侧各自的库"
  tail -25 "${TMP_OUT}" | sed 's/^/   | /'
  FAILED=1
fi
restore "${PROBE}" run.sh

# ─────────────────────────────────────────────────────────────
# M4：RAG 侧**中途挂掉** ⇒ 后续用例必须转红
#      ⇒ 证明「请求失败」不会被当成「两侧一致」（= FAILING-010 的守卫）
# ─────────────────────────────────────────────────────────────
say ""
say "== M4 RAG 侧在矩阵中途挂掉 ⇒ 必须转红（不能沿用上一次的残留 body） =="
RC="$(run_probe PROBE_STOP_RAG_AFTER=follow_list_limit_zero)"
if [ "${RC}" = "1" ] && case_is_red "follow_list_limit_max"; then
  say "   ✅ 精确转红（exit 1 · 请求失败被当成失败，而不是读残留 body）"
  grep -E "❌ follow_list_limit_max|请求失败" "${TMP_OUT}" | head -4 | sed 's/^/   | /'
else
  say "   ❌ 没按预期转红（exit ${RC}；exit 2 = **环境问题**，不是守卫失效）⇒ 探针会把失败请求当成通过"
  tail -25 "${TMP_OUT}" | sed 's/^/   | /'
  FAILED=1
fi

# ─────────────────────────────────────────────────────────────
# M5：**复现 FAILING-010** —— 把 run.sh 改回旧写法
#      （固定 -o 文件 + 去掉 http_code==000 守卫）
#      ⇒ 同样的挂掉场景下探针会把**下一次**请求读成上一次的残留 body
#      ⇒ `follow_list_limit_max`（与上一条 body 相同）会被**误判成 ✅（假绿）**
#      ⇒ 证明 M4 的红确实是守卫带来的，不是碰巧
# ─────────────────────────────────────────────────────────────
say ""
say "== M5 复现 FAILING-010（固定 -o 文件 + 去掉 000 守卫 + 只比 body）⇒ 必须出现假绿 =="
# ⚠️ **三处一起改，才等于「旧探针」**（否则复现不出来）：
#   ① 固定 `-o` 文件（**FAILING-010 本体**）
#   ② 去掉 `http_code == 000` 守卫（请求失败不再被当成失败）
#   ③ **只比 body**（旧探针就是只比 body —— 见 article 探针的 `run_case`：
#      `if [ "${g}" = "${r}" ] && [ "${gh}" = "${eh}" ]`，其中 g/r 是 body）
#      ⚠️ 本探针比旧探针**多比一个 HTTP 状态码** ⇒ 若只改 ①②，
#      `g`("200|hex") 与 `r`("000|hex") 仍不等 ⇒ 假绿复现不出来。
mutate "${PROBE}" '  out="$(mktemp "${WORK}/body.XXXXXX")" || return 1' \
                    '  out="${WORK}/body.FIXED"'
mutate "${PROBE}" '  if [ "${gcode}" = "000" ] || [ "${rcode}" = "000" ]; then
    FAILED=1
    say "   ❌ ${name}：请求失败（Go http_code=${gcode} · RAG http_code=${rcode}）"
    return
  fi' \
'  if false; then
    FAILED=1
    return
  fi'
mutate "${PROBE}" '  if [ "${g}" = "${r}" ] && [ "${g}" = "${eh}" ]; then' \
                  '  if [ "${gh}" = "${rh}" ] && [ "${gh}" = "${eh##*|}" ]; then'
run_probe PROBE_STOP_RAG_AFTER=follow_list_limit_zero >/dev/null
if grep -qE "✅ follow_list_limit_max" "${TMP_OUT}"; then
  say "   ✅ 假绿被复现（follow_list_limit_max 被误判成 ✅ ⇒ 读到了上一次的残留 body）"
  grep -E "✅ follow_list_limit_max" "${TMP_OUT}" | head -2 | sed 's/^/   | /'
else
  say "   ❌ 没能复现假绿 ⇒ M4 的红可能不是守卫带来的"
  grep -E "follow_list_limit_max" "${TMP_OUT}" | head -4 | sed 's/^/   | /'
  FAILED=1
fi
restore "${PROBE}" run.sh

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
  say "✅ 全部变异按预期表现 + 还原后转绿 ⇒ 探针非假绿"
  exit 0
fi
say "❌ 有变异没按预期表现 ⇒ 探针是假绿"
exit 1
