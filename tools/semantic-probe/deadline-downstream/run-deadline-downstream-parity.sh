#!/usr/bin/env bash
#
# 下游调用 deadline 传播 Go ↔ RAG **双侧活体对拍**（AGENTS.md §3.3 规则 2 的第四个落地）。
#
# 定位
# ----
# E38② 重估：测「上游请求的 deadline 已经过期 ⇒ 业务代码再发一次下游 gRPC 调用 ⇒ 会怎样？」
#
#   Go 半边 = **冻结仓真源**（observability.NewUnaryServerInterceptor + 真 grpc-go + 真 ctx）
#   RAG 半边 = **生产类**（RpcInterceptor + 真 grpc-java Netty + 真 ThreadLocal 载体）
#
# 本脚本**不是**「两侧必须相等」的等值守门，而是**漂移形态守门**：
#
#   · **parity 键**（sql_carrier）⇒ 两侧**必须相等**
#     ⇒ 它证明「SQL 路径两侧同款」（E38① + E62 之后成立）⇒ 漂移**不在** SQL 上。
#   · **drift 键**（failed_fast / code / outgoing_carrier）⇒ 断言**当前已登记的漂移形态**
#     ⇒ 漂移还在 ⇒ 绿；漂移形态变了（被修好了 / Go 侧前提变了）⇒ **红** ⇒ 逼着人更新台账。
#
# ⚠️ 这与 `run-rpc-timeout-parity.sh` 的 KNOWN_GAP 机制**方向相反**：
#    KNOWN_GAP 是「这个差异存在我也认了，差异变了我也不管」；
#    本脚本的 drift 键是「这个差异**必须长成这个样子**，否则出事」。
#
# 用法：bash tools/semantic-probe/deadline-downstream/run-deadline-downstream-parity.sh
# 退出码：0 = 形态与已登记一致；1 = 形态变了（漂移被修好 / 前提变了）；2 = 环境不满足
#
# ══════════════════════════════════════════════════════════════════════════
# 三条纪律
# ① **Go 半边直接引用冻结仓**（`replace sea-try-go => <冻结仓>`）
#    ⇒ 不复制、不改写冻结仓一个字节 ⇒ 跑的就是真源。
#    ⚠️ 冻结仓路径是**机器相关**的 ⇒ 由本脚本在运行时生成 go.mod，
#       仓库里只放 main.go（不提交任何绝对路径）。
#
# ② **CGO_ENABLED=0** —— 本机没有 Xcode 头文件 ⇒ cgo 会报 `'stdlib.h' file not found`。
#
# ③ **漂移键的期望值必须能指到台账**（见下方 EXPECTED_DRIFT 的出处列）。
#    没有台账出处的漂移键 = 无出处的断言 ⇒ 不许进来。
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAG_ROOT="$(cd "${HERE}/../../.." && pwd)"

GO_TRUTH_REPO="${GO_TRUTH_REPO:-/Users/pr/Documents/java&golang/Sea-RideTheWind_golang}"

TMP="$(mktemp -d)"
trap 'rm -rf "${TMP}"' EXIT

GO_OUT="${TMP}/go.txt"
RAG_OUT="${TMP}/rag.txt"
CP_FILE="${TMP}/cp.txt"

say() { printf '%s\n' "$*"; }

# ══════════════════════════════════════════════════════════════════════════
# 期望的漂移形态（每行：key|Go 期望值|RAG 期望值|一句话理由|台账出处）
# ══════════════════════════════════════════════════════════════════════════
# ⚠️ 这些值是**实测出来的**（2026-10-26 第四十六轮），不是设计出来的。
#    Go 侧的 code=4 = grpc-go 的 codes.DeadlineExceeded。
EXPECTED_DRIFT_FILE="${TMP}/expected-drift.txt"
cat >"${EXPECTED_DRIFT_FILE}" <<'EOF'
failed_fast|true|false|Go 的出向调用吃 handler 的 ctx（已到期）⇒ grpc-go 立刻失败；RAG 的出向调用没有任何 deadline ⇒ 挂住|docs/DRIFT/global/_README.md E38② 重估（第四十六轮）
code|4|0|Go 侧拿到 DEADLINE_EXCEEDED(4)；RAG 侧根本没有失败（调用悬着 ⇒ 无码）|docs/DRIFT/global/_README.md E38② 重估（第四十六轮）
outgoing_carrier|present_expired|absent|Go 的 ctx 一直带 deadline；RAG 出向调用用的 GoStyleRequestDeadline 在 gRPC 路径**从未绑定**⇒ 缺席|docs/DRIFT/global/_README.md E38② 重估（第四十六轮）
EOF

# ══════════════════════════════════════════════════════════════════════════
# 1) Go 半边：引用冻结仓 → 离线构建 → 跑
# ══════════════════════════════════════════════════════════════════════════
say "== 1/4 构建并跑 Go 半边（真源拦截器 + 真 gRPC server + 真 ctx）=="
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

( cd "${GO_DIR}" && CGO_ENABLED=0 GOFLAGS=-mod=mod GOPROXY=off GOSUMDB=off GOPRIVATE='*' \
    go build -o probe . ) >"${TMP}/gobuild.log" 2>&1 || {
  say "❌ Go 半边**构建失败** ⇒ 探针没跑起来（不是「不一致」，是「没跑」）"
  tail -30 "${TMP}/gobuild.log"
  exit 2
}
"${GO_DIR}/probe" >"${GO_OUT}" 2>"${TMP}/go.err" || {
  say "❌ Go 半边**运行失败**"
  tail -30 "${TMP}/go.err"
  exit 2
}
if ! grep -q "^PROBE|" "${GO_OUT}"; then
  say "❌ Go 半边没有产出 PROBE 行 ⇒ 探针没跑到"
  tail -30 "${TMP}/go.err"
  exit 2
fi
say "   Go 半边产出 $(grep -c '^PROBE|' "${GO_OUT}") 行"

# ══════════════════════════════════════════════════════════════════════════
# 2) RAG 半边：编译（生产类）→ 跑
# ══════════════════════════════════════════════════════════════════════════
say "== 2/4 跑 RAG 半边（生产类 RpcInterceptor + 真 gRPC server）=="
COMMON_CLASSES="${RAG_ROOT}/service/common/target/classes"
mvn -o -q -f "${RAG_ROOT}/pom.xml" -pl service/common compile
if [ ! -d "${COMMON_CLASSES}" ]; then
  say "!! ${COMMON_CLASSES} 不存在 ⇒ 编译没成功"
  exit 2
fi
# ⚠️ includeScope=test：探针要起**真的** gRPC server（grpc-netty-shaded 是 test scope）
mvn -o -q -f "${RAG_ROOT}/pom.xml" -pl service/common dependency:build-classpath \
  -Dmdep.outputFile="${CP_FILE}" -Dmdep.includeScope=test
CP="${COMMON_CLASSES}:$(cat "${CP_FILE}")"
mkdir -p "${TMP}/classes"
javac -nowarn -cp "${CP}" -d "${TMP}/classes" "${HERE}/java/DeadlineDownstreamProbe.java" 2>"${TMP}/javac.log" || {
  say "❌ RAG 半边编译失败"
  head -30 "${TMP}/javac.log"
  exit 2
}
java -cp "${TMP}/classes:${CP}" DeadlineDownstreamProbe 2>"${TMP}/java.err" | grep -E "^PROBE\|" >"${RAG_OUT}" || true
if ! grep -q "^PROBE|" "${RAG_OUT}"; then
  say "❌ RAG 半边没有产出 PROBE 行 ⇒ 探针没跑到"
  head -30 "${TMP}/java.err"
  exit 2
fi
say "   RAG 半边产出 $(grep -c '^PROBE|' "${RAG_OUT}") 行"

# ══════════════════════════════════════════════════════════════════════════
# 3) 按用例名配对逐键比对
# ══════════════════════════════════════════════════════════════════════════
python3 - "$GO_OUT" "$RAG_OUT" "$EXPECTED_DRIFT_FILE" <<'PY'
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

# ⚠️ **原始 PROBE 行必须打印出来**：
#    PIT-GLOBAL「退出码 0 ≠ 脚本真跑过」⇒ 编排层要能自证「两侧都真的跑了」。
#    ⇒ 门禁（mutate-deadline-downstream.py）就是靠这些行数判定「有一侧没真跑」。
print()
print("── 原始 PROBE 行（两侧各一行 = 一侧真的跑过了）──")
for label, path in (("Go ", sys.argv[1]), ("RAG", sys.argv[2])):
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if line.startswith("PROBE|"):
                print("  %s | %s" % (label, line))

go = load(sys.argv[1])
rag = load(sys.argv[2])
drift = {}
with open(sys.argv[3], encoding="utf-8") as fh:
    for line in fh:
        line = line.strip()
        if not line:
            continue
        key, gv, rv, reason, src = line.split("|", 4)
        drift[key] = (gv, rv, reason, src)

cases = sorted(set(go) | set(rag))
print()
print("=" * 78)
print("下游调用 deadline 传播 Go ↔ RAG 双侧活体对拍（E38② 重估）")
print("=" * 78)

fails = []
for case in cases:
    g = go.get(case)
    r = rag.get(case)
    if g is None or r is None:
        side = "RAG 侧缺失" if r is None else "Go 侧缺失"
        fails.append(f"❌ {case}: {side}")
        print(f"  ❌ {case:<34} {side}")
        continue

    print(f"  · {case}")
    for k in sorted(set(g) | set(r)):
        gv = g.get(k, "<<缺>>")
        rv = r.get(k, "<<缺>>")
        if k in drift:
            exp_g, exp_r, reason, src = drift[k]
            if gv == exp_g and rv == exp_r:
                print(f"      ✅ {k:<18} Go={gv} | RAG={rv}   （已登记的漂移形态）")
            else:
                fails.append(
                    f"❌ {case}.{k}: 漂移形态变了 —— 期望 Go={exp_g} RAG={exp_r}，"
                    f"实测 Go={gv} RAG={rv}")
                print(f"      ❌ {k:<18} Go={gv} | RAG={rv}   （期望 Go={exp_g} RAG={exp_r}）")
                print(f"         理由：{reason}")
                print(f"         出处：{src}")
                print(f"         ⇒ 漂移被修好了、或前提变了 ⇒ **必须更新台账**（别只改探针）")
        else:
            if gv == rv:
                print(f"      ✅ {k:<18} Go={gv} | RAG={rv}   （两侧一致）")
            else:
                fails.append(f"❌ {case}.{k}: 两侧不一致 Go={gv} ≠ RAG={rv}")
                print(f"      ❌ {k:<18} Go={gv} | RAG={rv}   （这一键两侧必须相等）")

print("-" * 78)
if fails:
    print()
    print(f"⇒ {len(fails)} 条不符合预期：")
    for f in fails:
        print("  " + f)
    sys.exit(1)

print()
print(f"✅ PASS —— {len(cases)} 个用例：parity 键两侧一致，drift 键形态与已登记一致")
sys.exit(0)
PY
