#!/usr/bin/env bash
#
# Go ↔ Spring Boot 「Go 是否读 body」双侧对拍（AGENTS.md §3.3 的「两侧同时跑的探针」）。
#
# 定位有两个，缺一不可：
#   ① **取基准**：Go 半边直接调 go-zero v1.10.2 的 rest/httpx.Parse ⇒
#      Java 半边的期望值不是人写的，是这份输出
#      ⇒ 避免 AGENTS.md §3.1 明令禁止的「拿 RAG 现有实现当对照基准」。
#   ② **回归守门**：任何人把共享真源 GoStyleJsonBodyConverter.goReadsBody 改坏、
#      或让 hot/article/favorite 退回自己抄一份判据 ⇒ 本脚本立刻 exit 1。
#
# 用法：bash tools/json-body-probe/run-parity.sh
# 依赖：mvn（离线 -o）、JDK 21、Go toolchain + 本地 module cache 里的 go-zero v1.10.2
#
# ⚠️ 本脚本**没有** known-dev 白名单 —— 与 tools/semantic-probe 不同。
#    理由：semantic-probe 面对的是「已签字偏离」（差异是决策、不可修）；
#    而本探针的 20 条用例全部是「Go 的字面语义」，两侧必须**逐条一致**，
#    任何一条不一致都是**真漂移** ⇒ 一律计入失败，不允许白名单。
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAG_ROOT="$(cd "${HERE}/../.." && pwd)"

# RAG 半边走 hot 域的真实实现：
#   - com.rag.hot.hot.api.form.GoFormValues#requireParsableJsonBody（hot 域拒绝路径）
#   - com.rag.common.http.GoStyleJsonBodyConverter#goReadsBody（共享层唯一真源）
MODULES=("service/common" "service/hot/hot-common" "service/hot/hot-api")
API_MODULE="service/hot/hot-api"

TMP="$(mktemp -d)"
trap 'rm -rf "${TMP}"' EXIT

GO_OUT="${TMP}/go.txt"
RAG_OUT="${TMP}/rag.txt"

say() { printf '%s\n' "$*"; }

say "== 1/5 编译 RAG 侧模块 =="
mvn -o -q -f "${RAG_ROOT}/pom.xml" -pl "${MODULES[0]},${MODULES[1]},${MODULES[2]}" -am -DskipTests compile

# ⚠️ classpath 必须用 **target/classes** 而不是 ~/.m2 里的 jar：
#    环境坑（见 tools/semantic-probe/run-parity.sh 的同款注释）——
#    dependency:build-classpath 会把 com.rag.common 解析成 ~/.m2 里的旧 jar，
#    而 goReadsBody 的新重载只存在于本工作树的 target/classes 里 ⇒ 验证的是旧实现。
CP=""
for m in "${MODULES[@]}"; do
    d="${RAG_ROOT}/${m}/target/classes"
    if [ ! -d "${d}" ]; then
        say "!! ${d} 不存在 —— 请先构建：mvn -o -pl ${MODULES[*]} -am install"
        exit 2
    fi
    CP="${CP}:${d}"
done
CP="${CP#:}"

say "== 2/5 解析 classpath =="
mvn -o -q -f "${RAG_ROOT}/pom.xml" -pl "${API_MODULE}" \
    dependency:build-classpath -Dmdep.outputFile="${TMP}/cp.txt" -Dmdep.includeScope=test
CP="${CP}:$(cat "${TMP}/cp.txt")"

say "== 3/5 跑 Go 侧（真源 go-zero v1.10.2 rest/httpx.Parse）=="
(cd "${HERE}/go" && GOPROXY=off GOFLAGS=-mod=mod go run .) >"${GO_OUT}"

say "== 4/5 跑 RAG 侧（hot 域真实实现 + 共享层唯一真源）=="
javac -nowarn -cp "${CP}" -d "${TMP}" "${HERE}/java/Probe.java"
java -cp "${TMP}:${CP}" Probe >"${RAG_OUT}"

say "== 5/5 逐行对拍 =="

# ⚠️ 判定口径：只比「读不读 body」这一层的可观察结果（ACCEPTED / REJECTED）。
#    **不比**错误文本 —— 两侧天生不同（Go 是 `unexpected EOF` / `type mismatch`，
#    RAG 是 hot 的 5001 业务信封）⇒ 比它必然全红。
#    ⇒ 「判定相同但细节不同」是**预期**的；「判定不同」才是漂移。
awk -F'|' '
function verdict(s) { return (s ~ /^ACCEPTED/) ? "ACCEPTED" : "REJECTED" }
BEGIN { printf "%-58s | %-9s | %-9s | %s\n", "用例名", "Go", "RAG", "判定"; print "---------------------------------------------------------------------------------------------------" }
FILENAME == ARGV[1] { if (index($0, "|") == 0) next; g[$1] = $2; order[++n] = $1; next }
{ if (index($0, "|") == 0) next; r[$1] = $2 }
END {
    ok = 0; bad = 0
    for (i = 1; i <= n; i++) {
        k = order[i]
        if (!(k in r)) { printf "%-58s | %-9s | %-9s | XX 缺失\n", k, verdict(g[k]), "<<缺失>>"; bad++; continue }
        if (verdict(g[k]) != verdict(r[k])) {
            printf "%-58s | %-9s | %-9s | XX 判定不一致\n", k, verdict(g[k]), verdict(r[k]); bad++
        } else {
            printf "%-58s | %-9s | %-9s | OK\n", k, verdict(g[k]), verdict(r[k]); ok++
        }
    }
    for (k in r) { if (!(k in g)) { printf "%-58s | %-9s | %-9s | XX Go 侧缺失\n", k, "<<缺失>>", verdict(r[k]); bad++ } }
    printf "\n总计 %d 例：一致 %d，不一致 %d\n", ok + bad, ok, bad
    if (bad > 0) { print "\n!! 有不一致 —— 那是真漂移，不是签字偏离（本探针无白名单）"; exit 1 }
}
' "${GO_OUT}" "${RAG_OUT}"

say ""
say "== 全部一致 =="
exit 0
