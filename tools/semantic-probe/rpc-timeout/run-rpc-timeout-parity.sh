#!/usr/bin/env bash
#
# gRPC 超时路径 Go ↔ RAG **双侧活体对拍**（AGENTS.md §3.3 规则 2 的第三个落地）。
#
# 定位
# ----
# E30：Go 真源 `service/common/observability/rpc.go:69` 在超时时给客户端的
#       status description 是 `context deadline exceeded`；RAG 旧实现写的是
#       `rpc timeout after 2000ms` ⇒ 客户端拿到的错误文案与 Go 不一致。
# 本脚本把 **Go 真源**跑起来，与 RAG 侧**生产类**逐键对拍。
#
# 用法：bash tools/semantic-probe/rpc-timeout/run-rpc-timeout-parity.sh
# 退出码：0 = 两侧一致；1 = 有漂移；2 = 环境不满足
#
# 依赖：mvn（离线 -o）、JDK、Go toolchain + 本地 module cache（冻结仓的依赖必须已缓存）
#
# ══════════════════════════════════════════════════════════════════════════
# 三条纪律
# ① **Go 半边直接引用冻结仓**（`replace sea-try-go => <冻结仓>`）
#    ⇒ 不复制、不改写冻结仓一个字节 ⇒ 跑的就是真源。
#    ⚠️ 冻结仓路径是**机器相关**的 ⇒ 由本脚本在运行时生成 go.mod，
#       仓库里只放 main.go（不提交任何绝对路径）。
#
# ② **CGO_ENABLED=0**
#    本机没有 Xcode 头文件 ⇒ cgo 会报 `'stdlib.h' file not found` ⇒ 与探针无关的失败。
#
# ③ **已知差异不计入失败，但必须始终打印**
#    `timeout_telemetry` 的 code 段：Go 是 grpc-go 的 `codes.Code.String()`（CamelCase
#    `DeadlineExceeded`），RAG 是 grpc-java 的 `Code.name()`（`DEADLINE_EXCEEDED`）
#    ⇒ 这一半**已登记为「表述差异 C-1」**
#    （`docs/Intentional_Deviations/common/_signed-deviations.md` C 类 C-1）
#    ⇒ 进 KNOWN_GAP（**不等于允许它漂** —— C-1 不在 global 的修改范围内，
#      且 RAG 侧的 JUnit 守门已把 C-1 的形态钉住）
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAG_ROOT="$(cd "${HERE}/../../.." && pwd)"

# 冻结 Go 真源（可用环境变量覆盖；默认本机路径）
GO_TRUTH_REPO="${GO_TRUTH_REPO:-/Users/pr/Documents/java&golang/Sea-RideTheWind_golang}"

TMP="$(mktemp -d)"
trap 'rm -rf "${TMP}"' EXIT

GO_OUT="${TMP}/go.txt"
RAG_OUT="${TMP}/rag.txt"
CP_FILE="${TMP}/cp.txt"

say() { printf '%s\n' "$*"; }

# ── 已知差异清单（每行：用例名|一句话理由|台账出处）──────────────────────
# ⚠️ 加白名单是**有后果的动作**：它等于承认「这条差异不修」。
#    ⇒ 每条都必须能在台账里找到出处；没有出处的**不许**进来。
KNOWN_GAP_FILE="${TMP}/known-gap.txt"
cat >"${KNOWN_GAP_FILE}" <<'EOF'
timeout_telemetry|RAG 的 code 段用 grpc-java 的 Code.name()（DEADLINE_EXCEEDED），Go 用 grpc-go 的 codes.Code.String()（DeadlineExceeded）⇒ 已登记为「表述差异 C-1」，不在 global 修改范围|docs/Intentional_Deviations/common/_signed-deviations.md C 类 C-1（另见 global 台账 E30 的边界说明）
EOF

# ══════════════════════════════════════════════════════════════════════════
# 1) Go 半边：引用冻结仓 → 离线构建 → 跑
# ══════════════════════════════════════════════════════════════════════════
say "== 1/4 准备 Go 真源（${GO_TRUTH_REPO}）=="
if [ ! -d "${GO_TRUTH_REPO}/service/common/observability" ]; then
  say "!! 找不到冻结仓的 service/common/observability：${GO_TRUTH_REPO}"
  say "!! 可用环境变量 GO_TRUTH_REPO 指向真源目录"
  exit 2
fi
if ! command -v go >/dev/null 2>&1; then
  say "!! 没有 Go toolchain ⇒ 本探针无法运行"
  exit 2
fi

GO_DIR="${TMP}/goprobe"
mkdir -p "${GO_DIR}"
cp "${HERE}/go/main.go" "${GO_DIR}/main.go"
cat >"${GO_DIR}/go.mod" <<EOF
module goprobe

go 1.25.11

require sea-try-go v0.0.0

replace sea-try-go => ${GO_TRUTH_REPO}
EOF

say "== 2/4 构建并跑 Go 半边（真源拦截器 + 真 gRPC server）=="
( cd "${GO_DIR}" && CGO_ENABLED=0 GOFLAGS=-mod=mod GOPROXY=off GOSUMDB=off GOPRIVATE='*' \
    go build -o probe . ) >"${TMP}/gobuild.log" 2>&1 || {
  say "❌ Go 半边**构建失败** ⇒ 探针没跑起来（不是「不一致」，是「没跑」）"
  tail -20 "${TMP}/gobuild.log"
  exit 2
}
"${GO_DIR}/probe" >"${GO_OUT}" 2>"${TMP}/go.err" || {
  say "❌ Go 半边**运行失败**"
  tail -20 "${TMP}/go.err"
  exit 2
}
if ! grep -q "^PROBE|" "${GO_OUT}"; then
  say "❌ Go 半边没有产出 PROBE 行 ⇒ 探针没跑到"
  tail -20 "${TMP}/go.err"
  exit 2
fi
say "   Go 半边产出 $(grep -c '^PROBE|' "${GO_OUT}") 行"

# ══════════════════════════════════════════════════════════════════════════
# 2) RAG 半边：编译（生产类）→ 跑
# ══════════════════════════════════════════════════════════════════════════
say "== 3/4 跑 RAG 半边（生产类 RpcInterceptor + 真 gRPC server）=="
COMMON_CLASSES="${RAG_ROOT}/service/common/target/classes"
mvn -o -q -f "${RAG_ROOT}/pom.xml" -pl service/common compile
if [ ! -d "${COMMON_CLASSES}" ]; then
  say "!! ${COMMON_CLASSES} 不存在 ⇒ 编译没成功"
  exit 2
fi
# ⚠️ includeScope=test：探针要起**真的** gRPC server（grpc-netty-shaded 是 test scope）
#    + 要 InMemorySpanExporter（opentelemetry-sdk-testing 也是 test scope）
mvn -o -q -f "${RAG_ROOT}/pom.xml" -pl service/common dependency:build-classpath \
  -Dmdep.outputFile="${CP_FILE}" -Dmdep.includeScope=test
# ⚠️ 探针必须跑**当前**的 service/common 源码 ⇒ target/classes 前置到 classpath 最前
CP="${COMMON_CLASSES}:$(cat "${CP_FILE}")"
mkdir -p "${TMP}/classes"
javac -nowarn -cp "${CP}" -d "${TMP}/classes" "${HERE}/java/TimeoutProbe.java" 2>"${TMP}/javac.log" || {
  say "❌ RAG 半边编译失败"
  head -30 "${TMP}/javac.log"
  exit 2
}
java -cp "${TMP}/classes:${CP}" TimeoutProbe 2>"${TMP}/java.err" | grep -E "^PROBE\|" >"${RAG_OUT}" || true
if ! grep -q "^PROBE|" "${RAG_OUT}"; then
  say "❌ RAG 半边没有产出 PROBE 行 ⇒ 探针没跑到"
  head -30 "${TMP}/java.err"
  exit 2
fi
say "   RAG 半边产出 $(grep -c '^PROBE|' "${RAG_OUT}") 行"

# ══════════════════════════════════════════════════════════════════════════
# 3) 按用例名配对逐条比对
# ══════════════════════════════════════════════════════════════════════════
python3 - "$GO_OUT" "$RAG_OUT" "$KNOWN_GAP_FILE" <<'PY'
import sys

def load(path):
    out = {}
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if not line.startswith("PROBE|"):
                continue
            parts = line.split("|")
            case = parts[1]
            kv = {}
            for p in parts[2:]:
                if "=" in p:
                    k, v = p.split("=", 1)
                    kv[k] = v
            out[case] = kv
    return out

go = load(sys.argv[1])
rag = load(sys.argv[2])
gaps = {}
with open(sys.argv[3], encoding="utf-8") as fh:
    for line in fh:
        line = line.strip()
        if not line:
            continue
        case, reason, src = line.split("|", 2)
        gaps[case] = (reason, src)

cases = sorted(set(go) | set(rag))
print()
print("=" * 78)
print("gRPC 超时路径 Go ↔ RAG 双侧活体对拍")
print("=" * 78)

fails = []
gaps_hit = []
for case in cases:
    g = go.get(case)
    r = rag.get(case)
    if g is None or r is None:
        side = "RAG 侧缺失" if r is None else "Go 侧缺失"
        fails.append(f"❌ {case}: {side}")
        print(f"  ❌ {case:<40} {side}")
        continue

    keys = sorted(set(g) | set(r))
    diffs = []
    for k in keys:
        gv = g.get(k, "<<缺>>")
        rv = r.get(k, "<<缺>>")
        if gv != rv:
            diffs.append((k, gv, rv))

    if not diffs:
        print(f"  ✅ {case:<40} 一致")
        continue

    if case in gaps:
        reason, src = gaps[case]
        gaps_hit.append(case)
        print(f"  ⚠️  {case:<40} 预期差异（不计入失败）")
        for k, gv, rv in diffs:
            print(f"        {k}: Go={gv}")
            print(f"        {k}: RAG={rv}")
        print(f"        理由：{reason}")
        print(f"        出处：{src}")
        continue

    fails.append(f"❌ {case}: " + "; ".join(f"{k}: Go={gv} ≠ RAG={rv}" for k, gv, rv in diffs))
    print(f"  ❌ {case:<40} 不一致")
    for k, gv, rv in diffs:
        print(f"        {k}: Go={gv} | RAG={rv}")

print("-" * 78)
if gaps_hit:
    print(f"已知差异 {len(gaps_hit)} 条（不计入失败，但仍在上面逐条列出）：")
    for c in gaps_hit:
        print(f"  · {c}")
if fails:
    print()
    print(f"⇒ {len(fails)} 条不一致：")
    for f in fails:
        print("  " + f)
    sys.exit(1)

print()
print(f"✅ PASS —— {len(cases)} 个用例两侧一致")
sys.exit(0)
PY
