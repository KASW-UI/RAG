#!/usr/bin/env bash
#
# hot 域错误码契约：Go ↔ Java 双侧逐字节对拍（AGENTS.md §3.3 要求的「两侧同时跑的探针」）。
#
# 真源：Sea-RideTheWind_golang/service/hot/common/errmsg.go（package common）
# 被测：RAG/service/hot/hot-common/src/main/java/.../errmsg/{HotErrorCode,HotErrorMessages}.java
#
# 定位是【回归守门】：任何人改了 hot 的码值 / 文案 / 未知码回落语义，本脚本立刻 exit 1。
#
# ⚠️ 为什么本探针存在（基线 §九 G-5）：
#   hot-common 域**早就**做过一次双侧逐字节对拍（结论：sha256 一致），
#   但那次探针**放在 /tmp** ⇒ 不可复算 ⇒ 结论只剩一行字、既没有载体也跑不出来
#   ⇒ 该组件的 R4（真·双侧活体）判定不成立、基线已降为 R3。
#   ⇒ 本脚本把那次结论**变成可复算的仓内证据**（AGENTS.md §3.3：证据的载体必须在版本控制内）。
#
# 用法：
#   bash tools/errmsg-probe/hot/run-parity.sh
#   GO_REPO=/path/to/Sea-RideTheWind_golang bash tools/errmsg-probe/hot/run-parity.sh
#
# 依赖：mvn（离线 -o）、JDK 21+、Go toolchain（≥1.25）。
#       ⚠️ hot 的 errmsg.go **import gRPC** ⇒ 临时模块必须继承真源 go.mod/go.sum
#          ⇒ 依赖从 **Go module cache** 离线解析（需要 grpc v1.81.1 已在缓存里）。
#
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAG_ROOT="$(cd "${HERE}/../../.." && pwd)"

# 真源与被测的相对路径（**先定义**，下面的解析函数要用）。
GO_SRC="service/hot/common/errmsg.go"
MODULE="service/hot/hot-common"

# Go 真源仓库：默认**向上逐层找**（可用 GO_REPO 覆盖）。
#
# ⚠️ 为什么不写死「RAG 的同级兄弟目录」：
#   在 **git worktree** 里（RAG.worktrees/<lane>/），RAG 的父目录是 `RAG.worktrees/`
#   ⇒ 「同级兄弟」会解析成 `RAG.worktrees/Sea-RideTheWind_golang` ⇒ **不存在** ⇒ 探针 exit 2。
#   ⇒ 本仓已有一例同类缺陷：`tools/difftest/check-surface-parity.py` 的 GO_REPO 默认路径
#      在工作树里指错（已登记为 G4-10）⇒ ⇒ 这里按「向上找 6 层」实现，与
#      `tools/ddl-parity-probe/favorite/run.sh` 的 default_go_repo 同款。
default_go_repo() {
  local d
  d="$(cd "${RAG_ROOT}" && pwd)"
  for _ in 1 2 3 4 5 6; do
    if [ -f "${d}/Sea-RideTheWind_golang/${GO_SRC}" ]; then
      (cd "${d}/Sea-RideTheWind_golang" && pwd); return 0
    fi
    d="$(dirname "${d}")"
  done
  return 1
}
GO_REPO="${GO_REPO:-$(default_go_repo || true)}"

TMP="$(mktemp -d)"
trap 'rm -rf "${TMP}"' EXIT

say() { printf '%s\n' "$*"; }
die() { printf '!! %s\n' "$*" >&2; exit 2; }

[ -f "${GO_REPO}/${GO_SRC}" ] || die "找不到 Go 真源：${GO_REPO}/${GO_SRC}（用 GO_REPO=... 指定）"

# ── 未登记码清单（回落语义的观察点）──────────────────────────────────────────
# ⚠️ 这份清单刻意挑**跨域撞车**的码值：这些码在 users / like / article 域是真实存在的
#    业务码，但在 hot 域**没有登记** ⇒ 两侧都必须回落到 "FAIL"。
#      1016/1017/1018  users: 管理员邀请码错误 / 请求参数错误 / 头像历史不存在
#      2001/2002        like:   输入错误 / 类型转换错误
#      3001             users:  积分余量不足   · like: 构建 outbox 失败
#      5009/5010        hot 域只登记到 5008 ⇒ 这两个必须回落
# ⚠️ 走 int 边界值：Go 的 int 是 64 位、Java 的 int 是 32 位，
#    跨语言比较只能用两侧都放得下的值，否则比的不是同一个输入。
CASES="${TMP}/cases.txt"
cat >"${CASES}" <<'EOF'
0
-1
1
999
1016
2001
3001
5009
5010
9999
2147483647
-2147483648
EOF

say "== 1/5 校验 Go 真源并搭临时模块 =="
# ⚠️ 复制件必须与真源**逐字节相同**：临时模块里的 errmsg.go 一旦与真源漂移，
#    本脚本验的就不是真源了。所以这里比 sha256，不匹配直接退出。
GO_SHA="$(shasum -a 256 "${GO_REPO}/${GO_SRC}" | awk '{print $1}')"
mkdir -p "${TMP}/src/common"
cp "${GO_REPO}/${GO_SRC}" "${TMP}/src/common/errmsg.go"
cp "${HERE}/go/errmsg_probe.go" "${TMP}/src/common/errmsg_probe.go"
cp "${HERE}/go/main.go" "${TMP}/src/main.go"
COPY_SHA="$(shasum -a 256 "${TMP}/src/common/errmsg.go" | awk '{print $1}')"
[ "${GO_SHA}" = "${COPY_SHA}" ] || die "复制件与真源 sha256 不一致（${GO_SHA} != ${COPY_SHA}）"
# ⚠️ 继承真源 go.mod（只改 module 行）+ go.sum：
#    hot 的 errmsg.go import gRPC ⇒ 零依赖 go.mod 会编译失败。
sed 's|^module sea-try-go$|module hoterrmsgprobe|' "${GO_REPO}/go.mod" >"${TMP}/src/go.mod"
cp "${GO_REPO}/go.sum" "${TMP}/src/go.sum"
say "   真源 sha256 = ${GO_SHA}  ✓ 与复制件一致"
say "   临时模块 = hoterrmsgprobe（继承真源 go.mod/go.sum ⇒ gRPC 依赖从 module cache 离线解析）"

say "== 2/5 跑 Go 侧（真源 errmsg.go 同包编译）=="
(cd "${TMP}/src" && GOPROXY=off GOFLAGS=-mod=mod GOTOOLCHAIN=local go run . "${CASES}") >"${TMP}/go.txt" \
  || die "Go 侧执行失败（exit $?）—— 见上方输出"

say "== 3/5 编译 hot-common =="
mvn -o -q -f "${RAG_ROOT}/pom.xml" -pl "${MODULE}" compile
CLASSES="${RAG_ROOT}/${MODULE}/target/classes"
[ -d "${CLASSES}" ] || die "${CLASSES} 不存在"

say "== 4/5 跑 Java 侧（真实 HotErrorCode / HotErrorMessages）=="
javac -nowarn -encoding UTF-8 -cp "${CLASSES}" -d "${TMP}/java" "${HERE}/java/Probe.java"
java -cp "${TMP}/java:${CLASSES}" com.rag.hot.hot.common.errmsg.Probe "${CASES}" >"${TMP}/java.txt"

say "== 5/5 逐字节对拍 =="
GO_SHA_OUT="$(shasum -a 256 "${TMP}/go.txt" | awk '{print $1}')"
JAVA_SHA_OUT="$(shasum -a 256 "${TMP}/java.txt" | awk '{print $1}')"

if diff -u "${TMP}/go.txt" "${TMP}/java.txt" >"${TMP}/diff.txt"; then
  say "   Go   sha256 = ${GO_SHA_OUT}"
  say "   Java sha256 = ${JAVA_SHA_OUT}"
  say "   行数 = $(wc -l <"${TMP}/go.txt" | tr -d ' ')（CONST 11 + TABLE 11 + FALLBACK $(grep -c . "${CASES}")）"
  say ""
  say "✅ 双侧逐字节一致（sha256 相同，diff 无输出）"
  exit 0
fi

say "   Go   sha256 = ${GO_SHA_OUT}"
say "   Java sha256 = ${JAVA_SHA_OUT}"
say ""
say "❌ 双侧不一致，diff（- = Go / + = Java）："
cat "${TMP}/diff.txt"
exit 1
