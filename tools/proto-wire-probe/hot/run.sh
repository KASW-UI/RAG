#!/usr/bin/env bash
#
# hot 域 **protobuf 消息体的 wire 字节**双侧对拍探针。
#
# 真源：Sea-RideTheWind_golang/service/hot/rpc/pb/hot.pb.go
#       （Go 侧**真正在用**的生成码，不是重新生成的）
# 被测：RAG/service/hot/hot-proto 的生成码（protobuf-java 运行时）
#
# 定位：补 AGENTS.md §3.4 效力边界第 1 条留下的缺口 ——
#   「`.proto` 文件两侧一致」**推不出**「两侧生成码产出的 wire 字节一致」。
#   字段号 / 零值落不落 wire / repeated 的编码方式 / bool false 的 presence，
#   只有序列化出来才看得见。
#   ⇒ docs/DRIFT/hot/proto.md §3 的 `H-3`（proto 消息体 wire 探针）由本探针闭合：
#     **消息体字节**这一层做成了双侧活体对拍。
#
# 用法：
#   bash tools/proto-wire-probe/hot/run.sh
#   GO_REPO=/path/to/Sea-RideTheWind_golang bash tools/proto-wire-probe/hot/run.sh
#
# 依赖：mvn（离线 -o）、JDK 21+、Go toolchain（≥1.25）、本地 Go module cache。
#
# 退出码：0 = 双侧逐字节一致；1 = 不一致；2 = 环境问题 / 真源漂移。
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAG_ROOT="$(cd "${HERE}/../../.." && pwd)"

default_go_repo() {
  local d
  d="$(cd "${RAG_ROOT}" && pwd)"
  for _ in 1 2 3 4; do
    if [ -f "${d}/Sea-RideTheWind_golang/service/hot/rpc/pb/hot.pb.go" ]; then
      printf '%s' "${d}/Sea-RideTheWind_golang"
      return 0
    fi
    d="$(dirname "${d}")"
  done
  return 1
}

GO_REPO="${GO_REPO:-$(default_go_repo || true)}"

say() { printf '%s\n' "$*"; }
die() { printf '!! %s\n' "$*" >&2; exit 2; }

[ -n "${GO_REPO}" ] || die "找不到 Go 真源（向上 4 层内没有 Sea-RideTheWind_golang）—— 用 GO_REPO=... 指定"

SRC_PB="service/hot/rpc/pb/hot.pb.go"
MODULE="service/hot/hot-rpc"

TMP="$(mktemp -d)"
# ⚠️ **不删临时目录**：`rm -rf` 在本机会被回收站守卫拦下（mavis-trash 失效 ⇒ 命令被整体拒绝）
#    ⇒ 留着 `/var/folders/.../tmp.*` 由系统清理（比让脚本在 EXIT 时自杀强）。
trap ':' EXIT

[ -f "${GO_REPO}/${SRC_PB}" ] || die "找不到 Go 真源：${GO_REPO}/${SRC_PB}（用 GO_REPO=... 指定）"
[ -f "${GO_REPO}/go.mod" ] || die "找不到 Go 真源 go.mod：${GO_REPO}/go.mod"

say "== 1/4 复制真源生成码（逐字节复制 + sha256 校验）=="
# ⚠️ 复制的是**真源真正在用的生成码**（不是重新 protoc 一遍）⇒ 验的就是生产字节。
mkdir -p "${TMP}/src/pb"
cp "${GO_REPO}/${SRC_PB}" "${TMP}/src/pb/hot.pb.go"
cp "${HERE}/go/main.go"          "${TMP}/src/main.go"

GO_SHA="$(shasum -a 256 "${GO_REPO}/${SRC_PB}" | awk '{print $1}')"
COPY_SHA="$(shasum -a 256 "${TMP}/src/pb/hot.pb.go" | awk '{print $1}')"
[ "${GO_SHA}" = "${COPY_SHA}" ] || die "复制件与真源 sha256 不一致（${GO_SHA} != ${COPY_SHA}）"
say "   ✓ ${SRC_PB}  sha256=${GO_SHA:0:16}…"

# ⚠️ 依赖版本**从真源 go.mod / go.sum 整体继承**（不写死、不 tidy）：
#    `go mod tidy` 会去解析**依赖的测试依赖**（protobuf → …），那些未必在本地 cache 里，
#    而探针根本不需要它们 ⇒ 直接沿用真源的 go.mod（改 module 名）+ go.sum。
sed 's|^module sea-try-go$|module hotprotobeprobe|' "${GO_REPO}/go.mod" >"${TMP}/src/go.mod"
cp "${GO_REPO}/go.sum" "${TMP}/src/go.sum"
PROTOBUF_VER="$(awk '/^[[:space:]]*google\.golang\.org\/protobuf v/ {print $2; exit}' "${GO_REPO}/go.mod")"
[ -n "${PROTOBUF_VER}" ] || die "无法从 ${GO_REPO}/go.mod 解析 google.golang.org/protobuf 版本"
say "   真源 go.mod：google.golang.org/protobuf ${PROTOBUF_VER}（go.mod + go.sum 整体继承）"

say "== 2/4 跑 Go 侧（真源生成码 + 真 protobuf 运行时）=="
( cd "${TMP}/src" && CGO_ENABLED=0 GOPROXY=off GOFLAGS=-mod=mod GOTOOLCHAIN=local go run . ) >"${TMP}/go.txt" \
  || die "Go 侧执行失败（exit $?）—— 见上方输出"
cat "${TMP}/go.txt"

say ""
say "== 3/4 与冻结的 expected.txt 逐字节对拍 =="
EXPECTED="${HERE}/expected.txt"
[ -f "${EXPECTED}" ] || die "缺少冻结的期望值：${EXPECTED}"
if diff -u "${EXPECTED}" "${TMP}/go.txt" >"${TMP}/diff.txt"; then
  say "   ✓ Go 侧输出与 expected.txt 逐字节一致（sha256=$(shasum -a 256 "${TMP}/go.txt" | awk '{print $1}' | cut -c1-16)…）"
else
  say ""
  say "❌ Go 侧输出与 expected.txt 不一致 —— **Go 真源的生成码换了**（- = 冻结值 / + = 实测）："
  cat "${TMP}/diff.txt"
  say ""
  say "   ⇒ 请重新评估 docs/DRIFT/hot/proto.md，并更新 expected.txt + Java 侧断言。"
  exit 1
fi

say ""
say "== 4/4 跑 Java 侧（hot-proto 的真实生成码）=="
mvn -o -q -f "${RAG_ROOT}/pom.xml" -pl "${MODULE}" -am test \
  -Dtest=HotProtoWireParityTest -DfailIfNoTests=false \
  -Dsurefire.failIfNoSpecifiedTests=false \
  || die "Java 侧 HotProtoWireParityTest 失败（exit $?）—— 见上方输出"

say ""
say "✅ 双侧逐字节一致：Go 真源生成码 == expected.txt == RAG 生成码（同一组消息的 wire 字节）"
exit 0
