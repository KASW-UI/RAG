#!/usr/bin/env bash
#
# Go ↔ Spring Boot 熔断语义双侧对拍（AGENTS.md §3.3「两侧同时跑的探针」）。
#
# 对拍对象：go-zero `core/breaker`（真源） ↔ RAG `com.rag.common.protection.GoStyleBreaker`
#
# ══════════════════════════════════════════════════════════════════════
# 三组用例，两种比对口径（**为什么不全是逐字节**）
# ══════════════════════════════════════════════════════════════════════
# ① **A 组（判据）逐条相同** —— Go 的 `zrpc/internal/codes.Acceptable` 的 6 码白名单
#    vs RAG 的 `GoStyleRpcFailurePolicy.isAcceptable`。
#    ⇒ 这是「熔断会不会误开/漏开」的**唯一决定因素**，必须精确一致。
#
# ② **B 组（锚点）逐字相同** —— 冷启动保护窗口（Go: protection=5）内必须全部放行。
#    这一段 dropRatio<=0，**与随机源无关** ⇒ 可以逐字节对拍。
#
# ③ **C 组（分布）只能按统计比对** —— Go 的随机源 `mathx.NewProba()` 用
#    `rand.NewSource(time.Now().UnixNano())` 播种 ⇒ **不可复现、不可播种**；
#    且算法按**墙钟分桶**（250ms）⇒ 两侧的桶边界不可能对齐。
#    ⇒ 逐次序列对拍**在原理上就不可行**，只能比对拒绝率（Go 自己的
#    `core/mathx/proba_test.go` 也是这么做的：assert.InEpsilon）。
#
# ⚠️ 另有一处**必须机械校验**的地方：Go 的判据在 `zrpc/internal/...`（internal 包）
#    ⇒ 外部模块**无法 import** ⇒ 探针里只能放一份**逐字副本**。
#    ⇒ 本脚本第 2 步会**从 module cache 里把 accept.go 原样抽出来逐字比对**，
#    不一致就 exit 1 ⇒ go-zero 改了判据时探针立刻炸，不会悄悄失配。
#
# 用法：bash tools/breaker-probe/run-parity.sh
# 依赖：mvn（离线 -o）、JDK、Go toolchain + 本地 module cache 里的 go-zero v1.10.2
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAG_ROOT="$(cd "${HERE}/../.." && pwd)"
MODULE="service/common"
CLASSES="${RAG_ROOT}/${MODULE}/target/classes"

TMP="$(mktemp -d)"
trap 'rm -rf "${TMP}"' EXIT

say() { printf '%s\n' "$*"; }

# ── 1/5 构建 service/common ────────────────────────────────────────────────
say "== 1/5 构建 ${MODULE} =="
mvn -o -q -f "${RAG_ROOT}/pom.xml" -pl "${MODULE}" compile
if [ ! -d "${CLASSES}" ]; then
    say "!! ${CLASSES} 不存在"; exit 2
fi

# ── 2/5 机械校验：探针里的 acceptable 副本必须与真源逐字一致 ───────────────
say "== 2/5 校验 acceptable 副本与真源 zrpc/internal/codes/accept.go 逐字一致 =="
GOMODCACHE="$(cd "${HERE}/go" && go env GOMODCACHE)"
TRUTH="${GOMODCACHE}/github.com/zeromicro/go-zero@v1.10.2/zrpc/internal/codes/accept.go"
if [ ! -f "${TRUTH}" ]; then
    say "!! 真源 accept.go 不存在：${TRUTH}"
    say "   （go-zero v1.10.2 不在本地 module cache 里 ⇒ 无法对拍）"
    exit 2
fi
extract_body() {   # $1=file $2=funcName
    awk -v fn="$2" '
        $0 ~ "func " fn "\\(" { inside=1 }
        inside { print }
        inside && /^}$/ { exit }
    ' "$1"
}
extract_body "${TRUTH}" "Acceptable" | sed 's/Acceptable/acceptable/' > "${TMP}/truth.txt"
extract_body "${HERE}/go/main.go" "acceptable" > "${TMP}/probe.txt"
if ! diff -u "${TMP}/truth.txt" "${TMP}/probe.txt"; then
    say "!! 🔴 探针里的 acceptable 副本与真源不一致 —— go-zero 改了判据！"
    say "   请把真源的新版本同步到 tools/breaker-probe/go/main.go 后再跑。"
    exit 1
fi
say "   ✔ 与真源逐字一致"

# ── 3/5 跑 Go 侧（真源 core/breaker）──────────────────────────────────────
say "== 3/5 跑 Go 侧（真源 go-zero v1.10.2 core/breaker）=="
(cd "${HERE}/go" && GOPROXY=off GOFLAGS=-mod=mod go run .) > "${TMP}/go.txt"

# ── 4/5 跑 RAG 侧（真实的 GoStyleBreaker + GoStyleRpcFailurePolicy）────────
say "== 4/5 跑 RAG 侧（真实复刻件）=="
mvn -o -q -f "${RAG_ROOT}/pom.xml" -pl "${MODULE}" \
    dependency:build-classpath -Dmdep.outputFile="${TMP}/cp.txt" -Dmdep.includeScope=test
CP="${CLASSES}:$(cat "${TMP}/cp.txt")"
javac -nowarn -cp "${CP}" -d "${TMP}" "${HERE}/java/Probe.java"
java -cp "${TMP}:${CP}" com.rag.common.protection.Probe > "${TMP}/rag.txt"

# ── 5/5 比对 ──────────────────────────────────────────────────────────────
say "== 5/5 比对 =="
FAIL=0

# A 组：逐条必须相同
A_GO=$(grep '^A ' "${TMP}/go.txt" | sort)
A_RAG=$(grep '^A ' "${TMP}/rag.txt" | sort)
if [ "${A_GO}" != "${A_RAG}" ]; then
    say "🔴 A 组（失败判据）不一致："
    diff <(echo "${A_GO}") <(echo "${A_RAG}") || true
    FAIL=1
else
    say "   ✔ A 组：$(echo "${A_GO}" | wc -l | tr -d ' ') 条判据逐条一致"
fi

# B 组：逐字必须相同
B_GO=$(grep '^B ' "${TMP}/go.txt")
B_RAG=$(grep '^B ' "${TMP}/rag.txt")
if [ "${B_GO}" != "${B_RAG}" ]; then
    say "🔴 B 组（冷启动保护窗口）不一致：go=[${B_GO}] rag=[${B_RAG}]"
    FAIL=1
else
    say "   ✔ B 组：${B_GO}"
fi

# C 组：拒绝率按统计比对（随机源不可复现 ⇒ 只能比分布）
RATE_GO=$(grep '^C ' "${TMP}/go.txt" | sed -n 's/.*rate=\([0-9.]*\).*/\1/p')
RATE_RAG=$(grep '^C ' "${TMP}/rag.txt" | sed -n 's/.*rate=\([0-9.]*\).*/\1/p')
if [ -z "${RATE_GO}" ] || [ -z "${RATE_RAG}" ]; then
    say "🔴 C 组缺少 rate 输出"; FAIL=1
else
    DIFF=$(python3 -c "print(abs(float('${RATE_GO}') - float('${RATE_RAG}')))")
    if python3 -c "import sys; sys.exit(0 if float('${DIFF}') <= 0.15 else 1)"; then
        say "   ✔ C 组：go=${RATE_GO} rag=${RATE_RAG}（差 ${DIFF} ≤ 0.15）"
    else
        say "🔴 C 组：go=${RATE_GO} rag=${RATE_RAG}（差 ${DIFF} > 0.15）"
        FAIL=1
    fi
fi

echo
if [ "${FAIL}" -ne 0 ]; then
    say "探针结果：不一致（EXIT=1）"
    exit 1
fi
say "探针结果：一致（EXIT=0）"
