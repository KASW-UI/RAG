#!/usr/bin/env bash
#
# Go ↔ Spring Boot 解析语义双侧对拍（AGENTS.md §3.3 要求的「两侧同时跑的探针」）。
#
# 定位是【回归守门】：DRIFT-MSGAPI-001/002 已修完，23/23 一致是预期基线。
# 任何人把 DTO 的 ,string setter 改回 String 类型，本脚本会立刻 exit 1。
#
# 用法：bash tools/semantic-probe/run-parity.sh
# 依赖：mvn（离线 -o）、JDK、Go toolchain + 本地 module cache 里的 go-zero v1.10.2
#
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAG_ROOT="$(cd "${HERE}/../.." && pwd)"
MODULE="service/message/message-api"
USERS_COMMON_CLASSES="${RAG_ROOT}/service/users/users-common/target/classes"
MODULE_CLASSES="${RAG_ROOT}/${MODULE}/target/classes"

TMP="$(mktemp -d)"
trap 'rm -rf "${TMP}"' EXIT

GO_OUT="${TMP}/go.txt"
RAG_OUT="${TMP}/rag.txt"
COUNT_FILE="${TMP}/count.txt"

say() { printf '%s\n' "$*"; }

say "== 1/5 编译 message-api =="
mvn -o -q -f "${RAG_ROOT}/pom.xml" -pl "${MODULE}" compile

# ⚠️ 环境坑：dependency:build-classpath 会把 users-common 解析成
#    ~/.m2/.../users-common-0.0.1-SNAPSHOT.jar（旧构建产物）。
#    GoStyleRequired / GoStyleRequiredFields / GoRequiredFieldException 都在那个 jar 里，
#    不前置 target/classes 就可能验证的是旧实现。
if [ ! -d "${USERS_COMMON_CLASSES}" ]; then
  say "!! ${USERS_COMMON_CLASSES} 不存在——请先构建 users-common：" \
      "mvn -o -q -pl service/users/users-common install"
  exit 2
fi

say "== 2/5 解析 classpath =="
mvn -o -q -f "${RAG_ROOT}/pom.xml" -pl "${MODULE}" \
  dependency:build-classpath -Dmdep.outputFile="${TMP}/cp.txt" -Dmdep.includeScope=test
CP="${USERS_COMMON_CLASSES}:${MODULE_CLASSES}:$(cat "${TMP}/cp.txt")"

say "== 3/5 跑 Go 侧（真源 go-zero v1.10.2 core/mapping）=="
# GOPROXY=off 强制离线：缺依赖就报错，不允许悄悄联网拉。
(cd "${HERE}/go" && GOPROXY=off GOFLAGS=-mod=mod go run .) >"${GO_OUT}"

say "== 4/5 跑 RAG 侧（真实 DTO + 真实 MessageGoStyleCoercion）=="
javac -nowarn -cp "${CP}" -d "${TMP}" "${HERE}/java/Probe.java"
java -cp "${TMP}:${CP}" Probe >"${RAG_OUT}"

say "== 5/5 逐行对拍 =="

# ⚠️ 过滤掉不含 "|" 的行：Go 侧首行是 banner（fmt.Println），
#    不过滤会让两侧计数变成 24 vs 23。
# ⚠️ 判定标准只看 ACCEPTED / REJECTED 是否相同，原因文本天生不同
#    （required 显式 null：Go "mustn't be nil" vs RAG "is not set"）。
awk -F'|' -v countfile="${COUNT_FILE}" '
function verdict(s) { return (s ~ /^ACCEPTED/) ? "ACCEPTED" : "REJECTED" }
function trim(s)     { gsub(/^[ \t]+|[ \t]+$/, "", s); return s }
function short(s,   n) { s = trim(s); n = length(s); if (n > 46) return substr(s, 1, 43) "..."; return s }
function cell(s,   v, r) {
    v = verdict(s)
    if (v == "ACCEPTED") return v
    r = s; sub(/^REJECTED[ \t]*/, "", r)   # 剥掉前缀，避免出现 "REJECTED (REJECTED ...)"
    r = trim(r)
    return (r == "") ? v : v " (" short(r) ")"
}

FILENAME == ARGV[1] {
    if (index($0, "|") == 0) next           # ← banner / 空行过滤
    k = $1; sub(/[ \t]+$/, "", k)
    if (!(k in gseen)) { gseen[k] = 1; gorder[++gn] = k }
    g[k] = $2
    next
}
{
    if (index($0, "|") == 0) next
    k = $1; sub(/[ \t]+$/, "", k)
    if (!(k in rseen)) { rseen[k] = 1; rorder[++rn] = k }
    r[k] = $2
}
END {
    ok = 0; bad = 0; seen = 0
    printf "%s | %s | %s | %s\n", "用例名", "Go 结果", "RAG 结果", "判定"
    printf "%s\n", "----------------------------------------------------------------------------------------------------"
    for (i = 1; i <= gn; i++) {
        k = gorder[i]; seen++
        if (!(k in r)) { printf "%s | %s | %s | %s\n", k, cell(g[k]), "<<缺失>>", "XX"; bad++; continue }
        if (verdict(g[k]) == verdict(r[k])) { printf "%s | %s | %s | %s\n", k, cell(g[k]), cell(r[k]), "OK"; ok++ }
        else                                 { printf "%s | %s | %s | %s\n", k, cell(g[k]), cell(r[k]), "XX"; bad++ }
    }
    for (i = 1; i <= rn; i++) {
        k = rorder[i]
        if (!(k in g)) { printf "%s | %s | %s | %s\n", k, "<<缺失>>", cell(r[k]), "XX"; bad++; seen++ }
    }
    if (bad > 0 && gn != rn)
        printf "\n!! 两侧用例名集合不同：Go %d 例 / RAG %d 例 —— 标签必须逐字对齐\n", gn, rn
    printf "\n总计 %d 例：一致 %d，不一致 %d\n", seen, ok, bad
    printf "%d %d\n", seen, bad > countfile
}
' "${GO_OUT}" "${RAG_OUT}"

read -r TOTAL BAD <"${COUNT_FILE}"
if [ "${BAD}" -gt 0 ]; then
  exit 1
fi
exit 0