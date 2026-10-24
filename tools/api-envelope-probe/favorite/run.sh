#!/usr/bin/env bash
#
# favorite 域 **api 响应字节**的双侧对拍探针（AGENTS.md §3.3 要求的「两侧同时跑的探针」）。
#
# 真源：Sea-RideTheWind_golang/service/common/response/response.go
#       Sea-RideTheWind_golang/service/favorite/api/internal/types/types.go
#       Sea-RideTheWind_golang/service/favorite/common/errmsg.go
# 被测：RAG/service/favorite/favorite-api 的 GlobalResponseAdvice + com.rag.common.response.Response
#       + favorite-api 的 dto/*
#
# 定位：**补 api.md §4 证据边界里的未验项** ——
#   ① 「Go 侧「非空值 >2048」时的逐字节响应」原为 🟡 源码级 ⇒ 本探针升级为活体对拍；
#   ② 「两种 int64 编码方式」（,string ⇒ 字符串 / 无 ,string ⇒ 数字）原为读源码判定 ⇒ 本探针实测；
#   ③ 「go-zero 的 ,optional 不是 omitempty」原为读源码判定 ⇒ 本探针实测；
#   ④ 「Go 的 RecoverHandler 写 500 + text/plain」**原结论错误** ⇒ 本探针用真库取证（实为空 body）。
#
# 用法：
#   bash tools/api-envelope-probe/favorite/run.sh
#   GO_REPO=/path/to/Sea-RideTheWind_golang bash tools/api-envelope-probe/favorite/run.sh
#
# 依赖：mvn（离线 -o）、JDK 21+、Go toolchain（≥1.25）、本地 Go module cache
#       （需含 go-zero 与 grpc —— 真源 go.mod pin 的版本）。
#
# 退出码：0 = 双侧逐字节一致；1 = 不一致；2 = 环境问题 / 真源漂移。
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAG_ROOT="$(cd "${HERE}/../../.." && pwd)"

# Go 真源仓库：默认**向上搜索**真源目录（主工作区与 worktree 两种布局的深度不同），可用 GO_REPO 覆盖。
default_go_repo() {
  local d
  d="$(cd "${RAG_ROOT}" && pwd)"
  for _ in 1 2 3 4; do
    if [ -f "${d}/Sea-RideTheWind_golang/service/favorite/common/errmsg.go" ]; then
      printf '%s' "${d}/Sea-RideTheWind_golang"
      return 0
    fi
    d="$(dirname "${d}")"
  done
  return 1
}

GO_REPO="${GO_REPO:-$(default_go_repo || true)}"
[ -n "${GO_REPO}" ] || die "找不到 Go 真源（向上 4 层内没有 Sea-RideTheWind_golang）—— 用 GO_REPO=... 指定"

# 真源文件（相对真源仓库根）
SRC_RESPONSE="service/common/response/response.go"
SRC_TYPES="service/favorite/api/internal/types/types.go"
SRC_ERRMSG="service/favorite/common/errmsg.go"

MODULE="service/favorite/favorite-api"

TMP="$(mktemp -d)"
trap 'rm -rf "${TMP}"' EXIT

say() { printf '%s\n' "$*"; }
die() { printf '!! %s\n' "$*" >&2; exit 2; }

for f in "${SRC_RESPONSE}" "${SRC_TYPES}" "${SRC_ERRMSG}"; do
  [ -f "${GO_REPO}/${f}" ] || die "找不到 Go 真源：${GO_REPO}/${f}（用 GO_REPO=... 指定）"
done
[ -f "${GO_REPO}/go.mod" ] || die "找不到 Go 真源 go.mod：${GO_REPO}/go.mod"

say "== 1/4 复制真源文件（逐字节复制 + sha256 校验）=="
# ⚠️ 为什么必须复制而不是 import 真源模块：
#    真源是**冻结只读**的商用架构仓库（AGENTS.md §1），探针不该往它里面写任何东西；
#    而且复制件可以与真源做 sha256 比对 ⇒ **真源变了探针立刻响**，
#    避免探针变成「拿自己的一份副本自证」（AGENTS.md §3.3）。
mkdir -p "${TMP}/src/common/response" "${TMP}/src/types" "${TMP}/src/favoritecommon"
cp "${GO_REPO}/${SRC_RESPONSE}" "${TMP}/src/common/response/response.go"
cp "${GO_REPO}/${SRC_TYPES}"     "${TMP}/src/types/types.go"
cp "${GO_REPO}/${SRC_ERRMSG}"    "${TMP}/src/favoritecommon/errmsg.go"
cp "${HERE}/go/main.go"          "${TMP}/src/main.go"

for f in "${SRC_RESPONSE}" "${SRC_TYPES}" "${SRC_ERRMSG}"; do
  case "${f}" in
    "${SRC_RESPONSE}") dst="${TMP}/src/common/response/response.go" ;;
    "${SRC_TYPES}")    dst="${TMP}/src/types/types.go" ;;
    *)                dst="${TMP}/src/favoritecommon/errmsg.go" ;;
  esac
  a="$(shasum -a 256 "${GO_REPO}/${f}"   | awk '{print $1}')"
  b="$(shasum -a 256 "${dst}"             | awk '{print $1}')"
  [ "${a}" = "${b}" ] || die "复制件与真源 sha256 不一致：${f}（${a} != ${b}）"
  say "   ✓ ${f}  sha256=${a:0:16}…"
done

# ⚠️ 依赖版本**从真源 go.mod / go.sum 整体继承**（不写死、不 tidy）：
#    ① 写死版本 ⇒ 真源升版本时探针会静默用旧版本验新真源；
#    ② `go mod tidy` 会去解析**依赖的测试依赖**（go-zero → yaml.v2 → check.v1 → rogpeppe），
#       那些不在本地 module cache 里 ⇒ tidy 必然失败，而探针**根本不需要它们**。
#    ⇒ 直接沿用真源的 go.mod（改 module 名）+ go.sum ⇒ 版本集与 hash 与真源逐条一致，
#       且 `go run` 离线可跑（只解析**真正 import 到的**包）。
sed 's|^module sea-try-go$|module favoriteapienvelopeprobe|' "${GO_REPO}/go.mod" >"${TMP}/src/go.mod"
cp "${GO_REPO}/go.sum" "${TMP}/src/go.sum"
GOZERO_VER="$(awk '/^[[:space:]]*github\.com\/zeromicro\/go-zero v/ {print $2; exit}' "${GO_REPO}/go.mod")"
GRPC_VER="$(awk '/^[[:space:]]*google\.golang\.org\/grpc v/ {print $2; exit}' "${GO_REPO}/go.mod")"
[ -n "${GOZERO_VER}" ] || die "无法从 ${GO_REPO}/go.mod 解析 go-zero 版本"
[ -n "${GRPC_VER}" ]   || die "无法从 ${GO_REPO}/go.mod 解析 grpc 版本"
say "   真源 go.mod：go-zero ${GOZERO_VER} / grpc ${GRPC_VER}（go.mod + go.sum 整体继承）"

say "== 2/4 跑 Go 侧（真 go-zero 库 + 真源 response/types/errmsg）=="
# ⚠️ GOPROXY=off + 本地 module cache ⇒ 不联网。
#    GOTOOLCHAIN=local 防止 go.mod 的 go 指令触发 toolchain 下载。
#    CGO_ENABLED=0：探针是纯 Go 的，而本机没有可用的 cgo 工具链（stdlib.h 缺失）⇒ 必须关掉。
( cd "${TMP}/src" && CGO_ENABLED=0 GOPROXY=off GOFLAGS=-mod=mod GOTOOLCHAIN=local go run . ) >"${TMP}/go.txt" \
  || die "Go 侧执行失败（exit $?）—— 见上方输出"
cat "${TMP}/go.txt"

say ""
say "== 3/4 与冻结的 expected.txt 逐字节对拍 =="
# ⚠️ expected.txt 是**冻结**的 Go 侧实测值：它是 Java 侧 JUnit 的**唯一真源**，
#    而 run.sh 用它守住「Go 真源变了 ⇒ 探针立刻响」。
EXPECTED="${HERE}/expected.txt"
[ -f "${EXPECTED}" ] || die "缺少冻结的期望值：${EXPECTED}"
if diff -u "${EXPECTED}" "${TMP}/go.txt" >"${TMP}/diff.txt"; then
  say "   ✓ Go 侧输出与 expected.txt 逐字节一致（sha256=$(shasum -a 256 "${TMP}/go.txt" | awk '{print $1}' | cut -c1-16)…）"
else
  say ""
  say "❌ Go 侧输出与 expected.txt 不一致 —— **Go 真源的响应字节变了**（- = 冻结值 / + = 实测）："
  cat "${TMP}/diff.txt"
  say ""
  say "   ⇒ 请重新评估 api.md 的结论，并更新 expected.txt + Java 侧断言。"
  exit 1
fi

say ""
say "== 4/4 跑 Java 侧（真实 advice / Response / DTO / 容器 mapper + 真 HTTP）=="
# ⚠️ Java 侧断言的是**同一个** expected.txt ⇒ 两侧共用一份真源 ⇒ 不会各说一半。
#    其中 OBS 段（500 兜底）断言的是「两侧**仍然不同**」⇒ 哪天差异消失 ⇒ 测试失败 ⇒ 逼人重评台账。
mvn -o -q -f "${RAG_ROOT}/pom.xml" -pl "${MODULE}" test \
  -Dtest=FavoriteApiEnvelopeParityTest -DfailIfNoTests=false \
  || die "Java 侧 FavoriteApiEnvelopeParityTest 失败（exit $?）—— 见上方输出"

say ""
say "✅ 双侧逐字节一致：Go 真源实测 == expected.txt == RAG 实测（body 逐字节；Content-Type 按 RFC 9110 OWS 归一）"
say "   OBS 段（500 兜底）两侧**仍然不同** —— 这是已登记的差异，不是回归"
exit 0
