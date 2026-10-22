#!/usr/bin/env bash
#
# article 域错误码契约：Go ↔ Java 双侧逐字节对拍（AGENTS.md §3.3 要求的「两侧同时跑的探针」）。
#
# 真源：Sea-RideTheWind_golang/service/article/common/errmsg/errmsg.go
# 被测：RAG/service/article/article-common/src/main/java/.../errmsg/ErrorCode.java
#
# 定位是【回归守门】：任何人改了 article 的码值 / 文案 / 未知码回落语义，本脚本立刻 exit 1。
#
# 用法：
#   bash tools/errmsg-probe/run-parity.sh
#   GO_REPO=/path/to/Sea-RideTheWind_golang bash tools/errmsg-probe/run-parity.sh
#
# 依赖：mvn（离线 -o）、JDK 21+、Go toolchain（≥1.25）。**不需要 Go module cache**：
#       探针跑在临时模块里，零外部依赖 ⇒ GOPROXY=off 也能跑。
#
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAG_ROOT="$(cd "${HERE}/../.." && pwd)"

# Go 真源仓库：默认取 RAG 的同级兄弟目录，可用 GO_REPO 覆盖。
GO_REPO="${GO_REPO:-$(cd "${RAG_ROOT}/.." && pwd)/Sea-RideTheWind_golang}"
GO_SRC="service/article/common/errmsg/errmsg.go"
MODULE="service/article/article-common"

TMP="$(mktemp -d)"
trap 'rm -rf "${TMP}"' EXIT

say() { printf '%s\n' "$*"; }
die() { printf '!! %s\n' "$*" >&2; exit 2; }

[ -f "${GO_REPO}/${GO_SRC}" ] || die "找不到 Go 真源：${GO_REPO}/${GO_SRC}（用 GO_REPO=... 指定）"

# ── 未登记码清单（回落语义的观察点）──────────────────────────────────────────
# ⚠️ 这份清单是**跨域撞车**的高价值样本：这些码值在 users / like 域是真实存在的
#    业务码，但在 article 域**没有登记** ⇒ 两侧都必须回落到 "FAIL"。
#      1016/1017/1018  users: 管理员邀请码错误 / 请求参数错误 / 头像历史不存在
#      2001/2002        like:   输入错误 / 类型转换错误
#      3001             users:  积分余量不足   · like: 构建 outbox 失败
#      5007-5012        users:  JSON 序列化/反序列化 / Kafka / 延时消息 / Redis
# ⚠️ 走 int 边界值：Go 的 int 是 64 位、Java 的 int 是 32 位，
#    跨语言比较只能用两侧都放得下的值，否则比的不是同一个输入。
CASES="${TMP}/cases.txt"
cat >"${CASES}" <<'EOF'
0
-1
1
999
1016
1017
1018
2001
2002
3001
5007
5008
5009
5010
5011
5012
9999
2147483647
-2147483648
EOF

say "== 1/5 校验 Go 真源并搭临时模块 =="
# ⚠️ 复制件必须与真源**逐字节相同**：临时模块里的 errmsg.go 一旦与真源漂移，
#    本脚本验的就不是真源了。所以这里比 sha256，不匹配直接退出。
GO_SHA="$(shasum -a 256 "${GO_REPO}/${GO_SRC}" | awk '{print $1}')"
mkdir -p "${TMP}/src/errmsg"
cp "${GO_REPO}/${GO_SRC}" "${TMP}/src/errmsg/errmsg.go"
cp "${HERE}/go/errmsg_probe.go" "${TMP}/src/errmsg/errmsg_probe.go"
cp "${HERE}/go/main.go" "${TMP}/src/main.go"
COPY_SHA="$(shasum -a 256 "${TMP}/src/errmsg/errmsg.go" | awk '{print $1}')"
[ "${GO_SHA}" = "${COPY_SHA}" ] || die "复制件与真源 sha256 不一致（${GO_SHA} != ${COPY_SHA}）"
printf 'module articleerrmsgprobe\n\ngo 1.25\n' >"${TMP}/src/go.mod"
say "   真源 sha256 = ${GO_SHA}  ✓ 与复制件一致"

say "== 2/5 跑 Go 侧（真源 errmsg.go 同包编译）=="
(cd "${TMP}/src" && GOPROXY=off GOFLAGS=-mod=mod go run . "${CASES}") >"${TMP}/go.txt" \
  || die "Go 侧执行失败（exit $?）—— 见上方输出"

say "== 3/5 编译 article-common =="
mvn -o -q -f "${RAG_ROOT}/pom.xml" -pl "${MODULE}" compile
CLASSES="${RAG_ROOT}/${MODULE}/target/classes"
[ -d "${CLASSES}" ] || die "${CLASSES} 不存在"

say "== 4/5 跑 Java 侧（真实 ErrorCode）=="
javac -nowarn -encoding UTF-8 -cp "${CLASSES}" -d "${TMP}/java" "${HERE}/java/Probe.java"
java -cp "${TMP}/java:${CLASSES}" Probe "${CASES}" >"${TMP}/java.txt"

say "== 5/5 逐字节对拍 =="
GO_SHA_OUT="$(shasum -a 256 "${TMP}/go.txt" | awk '{print $1}')"
JAVA_SHA_OUT="$(shasum -a 256 "${TMP}/java.txt" | awk '{print $1}')"

if diff -u "${TMP}/go.txt" "${TMP}/java.txt" >"${TMP}/diff.txt"; then
  say "   Go   sha256 = ${GO_SHA_OUT}"
  say "   Java sha256 = ${JAVA_SHA_OUT}"
  say "   行数 = $(wc -l <"${TMP}/go.txt" | tr -d ' ')（CONST 15 + TABLE 15 + FALLBACK $(grep -c . "${CASES}")）"
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
