#!/usr/bin/env bash
#
# trace 链路 Go ↔ RAG **双侧活体对拍**（AGENTS.md §3.3 规则 2 的第二个落地）。
#
# 定位
# ----
# T-11（全局 OpenTelemetry）此前只有 RAG 侧的守门，Go 侧是「逐行读源码」——
# 而 AGENTS.md §3.3 明确要求「凡写『与 Go 一致』，必须有**两侧同时跑的探针**」。
# 本脚本把 Go 真源**跑起来**，与 RAG 侧生产代码**逐条对拍**。
#
# 用法：bash tools/semantic-probe/trace/run-trace-parity.sh
# 退出码：0 = 两侧一致；1 = 有漂移；2 = 环境不满足
#
# 依赖：mvn（离线 -o）、JDK、Go toolchain + 本地 module cache 里的 go-zero v1.10.2
#
# ══════════════════════════════════════════════════════════════════════════
# 三条纪律（都是踩出来的）
# ══════════════════════════════════════════════════════════════════════════
# ① **Go 半边必须进包内**（package trace）：
#    `createExporter` / `startAgent` 是**未导出**的 ⇒ 外部包调不到
#    ⇒ 「unknown exporter」「端点为空不校验」「四种 exporter 都能构造」这三条
#      **只能**靠包内测试活体证。
#
# ② **模块缓存是只读的，且 `go -overlay` 不允许替换 GOMODCACHE 下的文件**
#    （实测报 `Files beneath GOMODCACHE must not be replaced`）
#    ⇒ 做法：把缓存里的 go-zero **整份拷到临时目录** → 放入包内探针 → 跑 `go test`。
#    ⇒ **拷完必须逐字节校验**（diff -r）⇒ 确认「跑的就是缓存里那份真源」。
#
# ③ **已知能力缺口不计入失败，但必须始终打印**
#    RAG 只实现了 otlpgrpc；Go 还支持 otlphttp / zipkin / file
#    ⇒ 这三行**必然**不一致 ⇒ 放进 KNOWN_GAP 白名单（每条都要有台账出处）
#      ⇒ 标成「预期差异」，不计入失败，但**始终打印出来**，让人看得见它还在那儿。
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAG_ROOT="$(cd "${HERE}/../../.." && pwd)"

GOZERO_VERSION="v1.10.2"
GOMODCACHE="$(go env GOMODCACHE)"
GZ_CACHE="${GOMODCACHE}/github.com/zeromicro/go-zero@${GOZERO_VERSION}"

TMP="$(mktemp -d)"
trap 'rm -rf "${TMP}"' EXIT

GO_OUT="${TMP}/go.txt"
RAG_OUT="${TMP}/rag.txt"
CP_FILE="${TMP}/cp.txt"

say() { printf '%s\n' "$*"; }

# ── 已知能力缺口清单（每行：用例名|一句话理由|台账出处）──────────────────
# ⚠️ 加白名单是**有后果的动作**：它等于承认「这条差异不修」。
#    ⇒ 每条都必须能在 global 台账里找到出处；没有出处的**不许**进来。
KNOWN_GAP_FILE="${TMP}/known-gap.txt"
cat >"${KNOWN_GAP_FILE}" <<'EOF'
G7_exporter_otlphttp|RAG 未实现 otlphttp（Go 能装）⇒ 能力缺口，非静默降级|global 台账 T-11 / 第二十三轮 §11.9④
G7_exporter_zipkin|RAG 未实现 zipkin（Go 能装）⇒ 能力缺口，非静默降级|global 台账 T-11 / 第二十三轮 §11.9④
G7_exporter_file|RAG 未实现 file（Go 能装）⇒ 能力缺口，非静默降级|global 台账 T-11 / 第二十三轮 §11.9④
EOF

# ══════════════════════════════════════════════════════════════════════════
# 1) Go 半边：拷真源 → 字节校验 → 跑包内探针
# ══════════════════════════════════════════════════════════════════════════
say "== 1/4 准备 Go 真源（go-zero ${GOZERO_VERSION}）=="
if [ ! -d "${GZ_CACHE}" ]; then
  say "!! module cache 里没有 go-zero ${GOZERO_VERSION}：${GZ_CACHE}"
  say "!! 需要 Go toolchain + 已缓存的 go-zero 源码 ⇒ 本探针无法运行"
  exit 2
fi

GZ_TMP="${TMP}/gozero"
cp -R "${GZ_CACHE}" "${GZ_TMP}"
chmod -R u+w "${GZ_TMP}"

say "== 2/4 字节校验（确认跑的就是缓存里那份真源）=="
# ⚠️ -x 排除我们放进去的探针本身
if ! diff -r -x 'liveprobe_test.go' "${GZ_CACHE}" "${GZ_TMP}" >"${TMP}/diff.txt" 2>&1; then
  say "❌ 拷贝后的 go-zero 与 module cache **不一致** ⇒ 中止"
  head -20 "${TMP}/diff.txt"
  exit 2
fi
say "   ✅ 拷贝逐字节一致（diff -r 无差异）"

cp "${HERE}/go/liveprobe_test.go" "${GZ_TMP}/core/trace/liveprobe_test.go"

say "== 3/4 跑 Go 半边（真源包内探针）=="
( cd "${GZ_TMP}" && GOFLAGS=-mod=mod GOPROXY=off go test ./core/trace/ -run 'TestLiveProbe' -v ) \
  >"${GO_OUT}" 2>&1 || true
# ⚠️ PIT-GLOBAL-018 三道闸：编译错误 ⇒ 测试压根没跑 ⇒ surefire/go-test 里的
#    结果是**上一轮**的 ⇒ 必须先看有没有编译错误，再看结果。
if grep -qE "build failed|cannot use|undefined:|syntax error" "${GO_OUT}"; then
  say "❌ Go 半边**编译失败** ⇒ 探针没跑起来（不是「不一致」，是「没跑」）"
  grep -E "\.go:[0-9]+:" "${GO_OUT}" | head -20
  exit 2
fi
grep -E "^\s+liveprobe_test\.go:[0-9]+: PROBE\|" "${GO_OUT}" \
  | sed -E 's/^.*PROBE\|/PROBE|/' >"${GO_OUT}.probe" || true
if ! grep -q "^PROBE|" "${GO_OUT}.probe"; then
  say "❌ Go 半边没有产出 PROBE 行 ⇒ 探针没跑到"
  tail -30 "${GO_OUT}"
  exit 2
fi
say "   Go 半边产出 $(wc -l <"${GO_OUT}.probe" | tr -d ' ') 行"

# ══════════════════════════════════════════════════════════════════════════
# 2) RAG 半边：编译 + 跑（用**生产类**）
# ══════════════════════════════════════════════════════════════════════════
say "== 4/4 跑 RAG 半边（生产类 GoStyleOtelAgent / GoStyleOtelConfig）=="
COMMON_CLASSES="${RAG_ROOT}/service/common/target/classes"
mvn -o -q -f "${RAG_ROOT}/pom.xml" -pl service/common compile
if [ ! -d "${COMMON_CLASSES}" ]; then
  say "!! ${COMMON_CLASSES} 不存在 ⇒ 请先 mvn -o -pl service/common compile"
  exit 2
fi
mvn -o -q -f "${RAG_ROOT}/pom.xml" -pl service/common dependency:build-classpath \
  -Dmdep.outputFile="${CP_FILE}" -Dmdep.includeScope=runtime
# ⚠️ 探针必须跑**当前**的 service/common 源码 ⇒ target/classes 前置到 classpath 最前
CP="${COMMON_CLASSES}:$(cat "${CP_FILE}")"
mkdir -p "${TMP}/classes"
javac -nowarn -cp "${CP}" -d "${TMP}/classes" "${HERE}/java/TraceProbe.java" 2>"${TMP}/javac.log" || {
  say "❌ RAG 半边编译失败"
  head -30 "${TMP}/javac.log"
  exit 2
}
java -cp "${TMP}/classes:${CP}" TraceProbe 2>"${TMP}/java.err" | grep -E "^PROBE\|" >"${RAG_OUT}" || true
if ! grep -q "^PROBE|" "${RAG_OUT}"; then
  say "❌ RAG 半边没有产出 PROBE 行 ⇒ 探针没跑到"
  head -30 "${TMP}/java.err"
  exit 2
fi
say "   RAG 半边产出 $(wc -l <"${RAG_OUT}" | tr -d ' ') 行"

# ══════════════════════════════════════════════════════════════════════════
# 3) 按用例名配对逐条比对
# ══════════════════════════════════════════════════════════════════════════
python3 - "$GO_OUT.probe" "$RAG_OUT" "$KNOWN_GAP_FILE" <<'PY'
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
print("trace 链路 Go ↔ RAG 双侧活体对拍")
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
            print(f"        {k}: Go={gv} | RAG={rv}")
        print(f"        理由：{reason}")
        print(f"        出处：{src}")
        continue

    fails.append(f"❌ {case}: " + "; ".join(f"{k}: Go={gv} ≠ RAG={rv}" for k, gv, rv in diffs))
    print(f"  ❌ {case:<40} 不一致")
    for k, gv, rv in diffs:
        print(f"        {k}: Go={gv} | RAG={rv}")

print("-" * 78)
if gaps_hit:
    print(f"已知能力缺口 {len(gaps_hit)} 条（不计入失败，但仍在上面逐条列出）：")
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
