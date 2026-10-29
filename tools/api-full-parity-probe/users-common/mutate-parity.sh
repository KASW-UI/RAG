#!/usr/bin/env bash
#
# 变异验证：证明 `tools/api-full-parity-probe/users-common/run.sh` **不是假绿**
#（AGENTS.md §3.3.3：新增的每个探针都必须配一个能真正触发它的变异）。
#
# ── 设计原则（本脚本最重要的部分）──────────────────────────────────────────
#   ⚠️ **绝不写共享层源码，也绝不写真源仓**。
#   `service/users/users-common/**` 是 **G4 的写权面**（AGENTS.md §5.3f）；
#   Go 真源是**冻结只读**的。
#   ⇒ 因此所有「打实现」的变异都用**影子化**（shadowing）实现：
#      · RAG 侧：把目标类的**副本**编译到临时目录，放在 classpath **最前面**
#        ⇒ JVM 加载副本，仓内源码**一个字节都没动**。
#      · Go 侧：用 `go build -overlay` 把真源的**某个文件**替换成变异副本
#        ⇒ 真源仓 `git status` 仍然干净（run.sh 每次跑都断言）。
#   ⇒ 连 `expected.txt` 都不改：走 `EXPECTED_FILE` 钩子指向临时副本。
#
# 退出码：0 = 每条变异都精确转红 + 还原后转绿；1 = 有变异没转红。
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAG_ROOT="$(cd "${HERE}/../../.." && pwd)"
PROBE="${HERE}/run.sh"
EXPECTED="${HERE}/expected.txt"
UC_SRC="${RAG_ROOT}/service/users/users-common/src/main/java/com/rag/users/common"
GO_REPO="${GO_REPO:-/Users/pr/Documents/java&golang/Sea-RideTheWind_golang}"

WORK="$(mktemp -d)"
TMP_OUT="$(mktemp)"
say() { printf '%s\n' "$*"; }
# ⚠️ **不删临时目录**：`rm -rf` 在本机会被回收站守卫拦下（mavis-trash 失效 ⇒ 命令被整体拒绝）
#    ⇒ 留着 `/var/folders/.../tmp.*` 由系统清理（比让脚本自杀强）。
trap 'rm -f "${TMP_OUT}"' EXIT

FAILED=0

# ── 先算一次 classpath（供影子化编译用）⇒ 后续每次跑探针都复用（省时间）──────
step_cp() {
  ( cd "${RAG_ROOT}" && ./mvnw -o -q -pl service/users/users-common package -DskipTests ) \
    || die "users-common 构建失败"
  ( cd "${RAG_ROOT}" && ./mvnw -o -q -pl service/users/users-common dependency:build-classpath \
      -Dmdep.outputFile="${WORK}/uc-cp.txt" -DincludeScope=runtime ) || die "取 classpath 失败"
  ( cd "${RAG_ROOT}" && ./mvnw -o -q -pl service/users/user/user-rpc dependency:build-classpath \
      -Dmdep.outputFile="${WORK}/rpc-cp.txt" -DincludeScope=runtime ) || die "取 classpath 失败"
  PG_DRIVER="$(tr ':' '\n' < "${WORK}/rpc-cp.txt" | grep -E '/postgresql-[0-9.]+\.jar$' | head -1)"
  [ -n "${PG_DRIVER}" ] || die "classpath 里找不到 postgresql 驱动"
  BASE_CP="${RAG_ROOT}/service/users/users-common/target/classes:$(cat "${WORK}/uc-cp.txt"):${PG_DRIVER}"
}
die() { printf '!! %s\n' "$*" >&2; exit 2; }

# run_probe [额外 env...] ⇒ 打印退出码
run_probe() {
  env "$@" bash "${PROBE}" >"${TMP_OUT}" 2>&1
  echo $?
}

# red_cases ⇒ 从探针输出里抓出「红了哪些 case」
red_cases() {
  grep -E "^   ❌ " "${TMP_OUT}" | sed -E 's/^   ❌ ([^ ]+).*/\1/' | sort -u | head -20
}

expect_red() {   # $1 = 期望红掉的 case（子串匹配）  $2 = 变异名
  local hit
  hit="$(red_cases | grep -F -- "$1" | head -1)"
  if [ -n "${hit}" ]; then
    say "   ✅ 精确转红：${hit}"
  else
    say "   ❌ 没按预期转红（期望含 '${1}'）⇒ 实际红掉的 case："
    red_cases | sed 's/^/      | /'
    FAILED=1
  fi
}

say "== 准备（classpath / 基线） =="
step_cp

say ""
say "== 基线（未变异）必须转绿 =="
BASE_RC="$(run_probe "UC_CP_OVERRIDE=${BASE_CP}")"
if [ "${BASE_RC}" = "0" ]; then
  say "   ✅ 基线绿（exit 0）"
else
  say "❌ 基线就不绿（exit ${BASE_RC}）⇒ 后续变异判定无意义"
  tail -40 "${TMP_OUT}" | sed 's/^/   | /'
  exit 1
fi

# ─────────────────────────────────────────────────────────────────────────────
# M1：冻结的 expected.txt 被改（走 EXPECTED_FILE 钩子 ⇒ **仓内文件不动**）
#     ⇒ 证明「expected.txt 真的在参与判定」，不是「自己跟自己比」
# ─────────────────────────────────────────────────────────────────────────────
say ""
say "== M1 冻结的期望值被改（c.errmsg.200：OK → Ok）⇒ 必须转红 =="
M1_EXPECTED="${WORK}/expected.m1.txt"
# ⚠️ **不能用 sed**：期望值里含 `|`（字段分隔符）⇒ 与 sed 的分隔符打架
#    （2026-10-29 实测：`sed: bad flag in substitute command: 'B'`）⇒ 改用 python3。
python3 - "${EXPECTED}" "${M1_EXPECTED}" <<'PY'
import io, sys
src, dst = sys.argv[1], sys.argv[2]
s = io.open(src, encoding="utf-8").read()
old = "CASE|c.errmsg.200|BOTH|OK"
new = "CASE|c.errmsg.200|BOTH|Ok"
assert old in s, "变异锚点没命中"
io.open(dst, "w", encoding="utf-8").write(s.replace(old, new, 1))
PY
if cmp -s "${EXPECTED}" "${M1_EXPECTED}"; then
  say "   ❌ 变异没生效（sed 没命中）"
  FAILED=1
else
  RC="$(run_probe "UC_CP_OVERRIDE=${BASE_CP}" "EXPECTED_FILE=${M1_EXPECTED}")"
  if [ "${RC}" = "1" ]; then
    say "   ✅ 转红（exit 1）"
    expect_red "c.errmsg.200"
  else
    say "   ❌ 没按预期转红（exit ${RC}）⇒ 探针没在比冻结值"
    tail -25 "${TMP_OUT}" | sed 's/^/   | /'
    FAILED=1
  fi
fi

# ─────────────────────────────────────────────────────────────────────────────
# M2：RAG 侧 `JwtUtil` 的**影子副本**（不钉死 HS256 ⇒ 复现 DRIFT-USERSCOMMON-004
#     的历史形态）⇒ 必须红在 alg / siglen 上
# ─────────────────────────────────────────────────────────────────────────────
say ""
say "== M2 RAG 侧 JwtUtil 影子副本（不再钉死 HS256）⇒ 必须转红 =="
mkdir -p "${WORK}/mut2/src/com/rag/users/common/jwt" "${WORK}/mut2/classes"
cp "${UC_SRC}/jwt/JwtUtil.java" "${WORK}/mut2/src/com/rag/users/common/jwt/JwtUtil.java"
python3 - "${WORK}/mut2/src/com/rag/users/common/jwt/JwtUtil.java" <<'PY'
import io, sys
p = sys.argv[1]
s = io.open(p, encoding="utf-8").read()
old_key = 'SecretKeySpec key = new SecretKeySpec(padToHmacKeySize(secretKey), "HmacSHA256");'
new_key = 'SecretKey key = io.jsonwebtoken.security.Keys.hmacShaKeyFor(padToHmacKeySize(secretKey));'
old_sig = '.signWith(key, SignatureAlgorithm.HS256)'
new_sig = '.signWith(key)'
assert old_key in s and old_sig in s, "变异锚点没命中"
s = s.replace(old_key, new_key).replace(old_sig, new_sig)
# SecretKey 需要 import（副本里加一行；原文件用的是 SecretKeySpec）
s = s.replace("import javax.crypto.spec.SecretKeySpec;",
              "import javax.crypto.SecretKey;\nimport javax.crypto.spec.SecretKeySpec;")
io.open(p, "w", encoding="utf-8").write(s)
PY
M2_OK=1
javac -proc:none -nowarn -cp "${BASE_CP}" -d "${WORK}/mut2/classes" \
  "${WORK}/mut2/src/com/rag/users/common/jwt/JwtUtil.java" 2>"${WORK}/mut2/javac.log" \
  || { say "   ❌ 影子副本编译失败"; tail -20 "${WORK}/mut2/javac.log" | sed 's/^/   | /'; M2_OK=0; FAILED=1; }
if [ "${M2_OK}" = "1" ]; then
  RC="$(run_probe "UC_CP_OVERRIDE=${BASE_CP}" "RAG_CP_PREFIX=${WORK}/mut2/classes")"
  if [ "${RC}" = "1" ]; then
    say "   ✅ 转红（exit 1）"
    expect_red "c.jwt.alg.48"
    expect_red "c.jwt.siglen.48"
  else
    say "   ❌ 没按预期转红（exit ${RC}）⇒ 探针看不见 alg 分叉"
    tail -25 "${TMP_OUT}" | sed 's/^/   | /'
    FAILED=1
  fi
fi

# ─────────────────────────────────────────────────────────────────────────────
# M3：Go 侧**真源** `jwt.go` 的 overlay 影子副本（HS256 → HS384）
#     ⇒ 真源仓零改动（run.sh 每次都断言 `git status` 干净）
#     ⇒ 必须红在 alg 上 ⇒ 证明「对拍不是自己跟自己」
# ─────────────────────────────────────────────────────────────────────────────
say ""
say "== M3 Go 侧真源 jwt.go 的 overlay 影子副本（HS256 → HS384）⇒ 必须转红 =="
mkdir -p "${WORK}/mut3"
cp "${GO_REPO}/service/user/common/jwt/jwt.go" "${WORK}/mut3/jwt.go"
python3 - "${WORK}/mut3/jwt.go" <<'PY'
import io, sys
p = sys.argv[1]
s = io.open(p, encoding="utf-8").read()
old = "jwt.New(jwt.SigningMethodHS256)"
new = "jwt.New(jwt.SigningMethodHS384)"
assert old in s, "变异锚点没命中"
io.open(p, "w", encoding="utf-8").write(s.replace(old, new))
PY
cat > "${WORK}/mut3/overlay.json" <<EOF
{"Replace": {"${GO_REPO}/service/user/common/jwt/jwt.go": "${WORK}/mut3/jwt.go"}}
EOF
RC="$(run_probe "UC_CP_OVERRIDE=${BASE_CP}" "GO_OVERLAY_EXTRA=${WORK}/mut3/overlay.json")"
if [ "${RC}" = "1" ]; then
  say "   ✅ 转红（exit 1）"
  expect_red "c.jwt.alg.15"
else
  say "   ❌ 没按预期转红（exit ${RC}）⇒ 探针没在比真源"
  tail -25 "${TMP_OUT}" | sed 's/^/   | /'
  FAILED=1
fi

# ─────────────────────────────────────────────────────────────────────────────
# M4：RAG 侧 `GoStyleStringInt64` 的**影子副本**（不再接受 JSON 数字字面量）
#     ⇒ 必须红在 `,string` int64 的那两条（JSON 数字 123 / int64 max）
# ─────────────────────────────────────────────────────────────────────────────
say ""
say "== M4 RAG 侧 GoStyleStringInt64 影子副本（不再接受 JSON 数字字面量）⇒ 必须转红 =="
mkdir -p "${WORK}/mut4/src/com/rag/users/common/http" "${WORK}/mut4/classes"
cp "${UC_SRC}/http/GoStyleStringInt64.java" "${WORK}/mut4/src/com/rag/users/common/http/GoStyleStringInt64.java"
python3 - "${WORK}/mut4/src/com/rag/users/common/http/GoStyleStringInt64.java" <<'PY'
import io, sys
p = sys.argv[1]
s = io.open(p, encoding="utf-8").read()
old = """        if (raw instanceof Byte || raw instanceof Short || raw instanceof Integer || raw instanceof Long) {
            return ((Number) raw).longValue();
        }"""
new = """        if (false) {
            return ((Number) raw).longValue();
        }"""
assert old in s, "变异锚点没命中"
io.open(p, "w", encoding="utf-8").write(s.replace(old, new))
PY
M4_OK=1
javac -proc:none -nowarn -cp "${BASE_CP}" -d "${WORK}/mut4/classes" \
  "${WORK}/mut4/src/com/rag/users/common/http/GoStyleStringInt64.java" 2>"${WORK}/mut4/javac.log" \
  || { say "   ❌ 影子副本编译失败"; tail -20 "${WORK}/mut4/javac.log" | sed 's/^/   | /'; M4_OK=0; FAILED=1; }
if [ "${M4_OK}" = "1" ]; then
  RC="$(run_probe "UC_CP_OVERRIDE=${BASE_CP}" "RAG_CP_PREFIX=${WORK}/mut4/classes")"
  if [ "${RC}" = "1" ]; then
    say "   ✅ 转红（exit 1）"
    expect_red "c.int64.1"
  else
    say "   ❌ 没按预期转红（exit ${RC}）⇒ 探针看不见 `,string` int64 的判定漂移"
    tail -25 "${TMP_OUT}" | sed 's/^/   | /'
    FAILED=1
  fi
fi

# ─────────────────────────────────────────────────────────────────────────────
# 还原后必须重新转绿
# ─────────────────────────────────────────────────────────────────────────────
say ""
say "== 还原后必须重新转绿（影子副本全部在临时目录 ⇒ 仓内源码从未被改） =="
RC="$(run_probe "UC_CP_OVERRIDE=${BASE_CP}")"
if [ "${RC}" = "0" ]; then
  say "   ✅ 还原后绿（exit 0）"
else
  say "   ❌ 还原后仍红（exit ${RC}）"
  tail -30 "${TMP_OUT}" | sed 's/^/   | /'
  FAILED=1
fi
( cd "${GO_REPO}" && git status --porcelain -- service/user | grep -q . ) \
  && { say "   ❌ 真源仓 service/user 有改动 ⇒ 变异脚本污染了真源"; FAILED=1; }
( cd "${RAG_ROOT}" && git status --porcelain -- service/users/users-common | grep -q . ) \
  && { say "   ❌ users-common 有改动 ⇒ 变异脚本污染了共享层"; FAILED=1; }

say ""
if [ "${FAILED}" = "0" ]; then
  say "✅ 四条变异全部精确转红 + 还原后转绿 ⇒ 探针非假绿"
  exit 0
fi
say "❌ 有变异没转红 ⇒ 探针是假绿"
exit 1
