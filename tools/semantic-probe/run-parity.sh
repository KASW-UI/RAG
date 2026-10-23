#!/usr/bin/env bash
#
# Go ↔ Spring Boot 解析语义双侧对拍（AGENTS.md §3.3 要求的「两侧同时跑的探针」）。
#
# 定位是【回归守门】：DRIFT-MSGAPI-001/002 已修完，此后新增用例一律纳入基线。
# 任何人把 DTO 的 ,string setter 改回 String 类型，本脚本会立刻 exit 1。
#
# 用法：bash tools/semantic-probe/run-parity.sh
# 依赖：mvn（离线 -o）、JDK、Go toolchain + 本地 module cache 里的 go-zero v1.10.2
#
# ══════════════════════════════════════════════════════════════════════
# 两条 2026-10-23 新增的机制（都是 §3.3 纪律的落地，不是可选功能）
# ══════════════════════════════════════════════════════════════════════
# ① **值比对**：判定从「只比 ACCEPTED/REJECTED」升级为「同时比对解析出的值 / 序列化结果」。
#    首轮探针自认只比判定，于是「两侧都接受但落的值不同」这一整类漂移看不见 ——
#    `affected` 的出参类型漂移（DRIFT-MSGAPI-009）就是这么漏掉的。
#
# ② **签字偏离白名单（known-dev）**：`docs/Intentional_Deviations/` 里已签字的差异
#    **必然**在探针里为红。若让它们计入失败，守门人迟早会去「修绿」——
#    那正是把已签字决策改回未受控漂移的最短路径（AGENTS.md §3.2 / §5.3b）。
#    ⇒ 白名单里的用例标成 `SD-xx 预期差异`，**不计入失败**，但**始终打印出来**，
#      让人看得见它还在那儿。
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
KNOWN_DEV_FILE="${TMP}/known-dev.txt"

say() { printf '%s\n' "$*"; }

# ── 已知签字偏离清单（每行：用例名|SD 编号|一句话理由）──────────────────
# ⚠️ 加白名单是**有后果的动作**：它等于承认「这条差异不修」。
#    ⇒ 每条都必须能在 docs/Intentional_Deviations/ 找到对应签字记录；
#      没有签字记录的一律不许进来（否则就是拿白名单掩盖真漂移）。
cat >"${KNOWN_DEV_FILE}" <<'EOF'
X1 出参 affected=3|SD-2026-10-23-03|Go tag 无 ,string 输出数字；RAG 按签字结论输出字符串
X2 出参 affected=0|SD-2026-10-23-03|同上（零值形态，确认不是「只有非零才转字符串」）
EOF

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
#    不过滤会让两侧计数变成 N+1 vs N。
# ⚠️ 判定标准：① ACCEPTED/REJECTED 是否相同；② 相同且都是 ACCEPTED 时，
#    **比对值/序列化结果是否逐字相同**。
#    原因文本天生不同（required 显式 null：Go "mustn-t be nil" vs RAG "is not set"），不参与判定。
awk -F'|' -v countfile="${COUNT_FILE}" -v knowndev="${KNOWN_DEV_FILE}" '
function verdict(s) { return (s ~ /^ACCEPTED/) ? "ACCEPTED" : "REJECTED" }
# ⚠️ payload 只对 ACCEPTED 有意义：REJECTED 后面跟的是**原因文本**，
#    两侧天生不同（Go "mustn-t be nil" vs RAG "is not set"），比它必然全红。
#    ⚠️ 改这个文件时注意：整个 awk 程序在 shell 单引号里，注释里不要写英文撇号。
function payload(s,   v) {
    if (s !~ /^ACCEPTED/) return ""
    v = s; sub(/^ACCEPTED/, "", v); return trim(v)
}
function trim(s)     { gsub(/^[ \t]+|[ \t]+$/, "", s); return s }
function short(s,   n) { s = trim(s); n = length(s); if (n > 46) return substr(s, 1, 43) "..."; return s }
function cell(s,   v, r, p) {
    v = verdict(s)
    p = payload(s)
    if (v == "ACCEPTED") return (p == "") ? v : v " " short(p)
    r = s; sub(/^REJECTED[ \t]*/, "", r)   # 剥掉前缀，避免出现 "REJECTED (REJECTED ...)"
    r = trim(r)
    return (r == "") ? v : v " (" short(r) ")"
}
# 值不同但判定同为 ACCEPTED 时的专用标记（让「绿但错」一眼可见）
function valcell(s) { return cell(s) }

BEGIN {
    while ((getline line < knowndev) > 0) {
        if (line ~ /^#/ || line == "") continue
        n = split(line, f, "|")
        if (n >= 2) { sd[f[1]] = f[2]; sdwhy[f[1]] = (n >= 3 ? f[3] : "") }
    }
    close(knowndev)
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
    ok = 0; bad = 0; sdc = 0; seen = 0
    printf "%s | %s | %s | %s\n", "用例名", "Go 结果", "RAG 结果", "判定"
    printf "%s\n", "----------------------------------------------------------------------------------------------------"
    for (i = 1; i <= gn; i++) {
        k = gorder[i]; seen++
        if (!(k in r)) { printf "%s | %s | %s | %s\n", k, cell(g[k]), "<<缺失>>", "XX"; bad++; continue }
        if (verdict(g[k]) != verdict(r[k])) {
            verdictTxt = "XX"
        } else if (payload(g[k]) != payload(r[k])) {
            # 判定同为 ACCEPTED 但值/序列化结果不同 —— 「绿但错」的典型形态
            verdictTxt = "XX(值不同)"
        } else {
            verdictTxt = "OK"
        }
        # ⚠️ 已签字偏离：预期为红，不计入失败，但必须打印出来（别让它悄悄消失）。
        #    用标记而非 next —— END 里不允许 next。
        if (verdictTxt != "OK" && (k in sd)) {
            printf "%s | %s | %s | %s\n", k, cell(g[k]), cell(r[k]), sd[k] " 预期差异(" sdwhy[k] ")"
            sdc++
            continue
        }
        if (verdictTxt == "OK") ok++; else bad++
        printf "%s | %s | %s | %s\n", k, cell(g[k]), cell(r[k]), verdictTxt
    }
    for (i = 1; i <= rn; i++) {
        k = rorder[i]
        if (!(k in g)) { printf "%s | %s | %s | %s\n", k, "<<缺失>>", cell(r[k]), "XX"; bad++; seen++ }
    }
    if (bad > 0 && gn != rn)
        printf "\n!! 两侧用例名集合不同：Go %d 例 / RAG %d 例 —— 标签必须逐字对齐\n", gn, rn
    printf "\n总计 %d 例：一致 %d，不一致 %d，已签字偏离 %d\n", seen, ok, bad, sdc
    printf "%d %d\n", seen, bad > countfile
}
' "${GO_OUT}" "${RAG_OUT}"

read -r TOTAL BAD <"${COUNT_FILE}"
if [ "${BAD}" -gt 0 ]; then
    exit 1
fi
exit 0