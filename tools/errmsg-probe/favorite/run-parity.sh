#!/usr/bin/env bash
#
# favorite 域错误码契约：Go ↔ Java 双侧逐字节对拍（AGENTS.md §3.3 要求的「两侧同时跑的探针」）。
#
# 真源：Sea-RideTheWind_golang/service/favorite/common/errmsg.go
# 被测：RAG/service/favorite/favorite-common/src/main/java/.../errmsg/{FavoriteErrorCode,FavoriteErrorMessages,FavoriteGrpcError}.java
#
# 定位是【回归守门】：任何人改了 favorite 的码值 / 文案 / 未知码回落语义 / 业务码反查语义，
# 本脚本立刻 exit 1。
#
# 用法：
#   bash tools/errmsg-probe/favorite/run-parity.sh
#   GO_REPO=/path/to/Sea-RideTheWind_golang bash tools/errmsg-probe/favorite/run-parity.sh
#
# 依赖：mvn（离线 -o）、JDK 21+、Go toolchain（≥1.25）、本地 Go module cache（需含 grpc）。
#
# ⚠️ 与 tools/errmsg-probe（article 版）的三点差异：
#   1. favorite 的 errmsg.go **import 了 grpc**（codes + status）⇒ 临时模块**不能零依赖**
#      ⇒ 用 `go mod tidy`（GOPROXY=off，从本地 module cache 解析）生成 go.sum。
#      grpc 版本**从真源 go.mod 动态读取**，不写死 ⇒ 真源升版本时本脚本自动跟随。
#   2. 多验两层 article 版没有的语义：
#      · WIRE  —— 每个码经 GRPCError 构造出的 wire 形态（业务码在 description、不在 grpc-status）
#      · BIZCODE —— BizCodeFromError 的还原矩阵（含「文案反查优先于 gRPC 码类别」）
#   3. 多一个 OBS 段 —— **两侧必然不同**的已知不可达差异，单列不参与逐字节对拍，
#      但差异「消失」时会报错（逼人更新台账，而不是让它静默漂移）。
#
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAG_ROOT="$(cd "${HERE}/../../.." && pwd)"

# Go 真源仓库：默认**向上搜索**真源目录，可用 GO_REPO 覆盖。
#
# ⚠️ 为什么要搜索而不是「RAG_ROOT/..」：
#   主工作区是 <root>/RAG，真源在 <root>/Sea-RideTheWind_golang（深 1 层）；
#   而 worktree 是 <root>/RAG.worktrees/<域>（深 2 层）⇒ 固定层数在两种布局下会失效。
#   ⇒ 逐级向上找，找不到再报错并提示用 GO_REPO=... 指定。
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
GO_SRC="service/favorite/common/errmsg.go"
MODULE="service/favorite/favorite-common"

# OBS 段：两侧**必然不同**的已知不可达差异（case 名必须与两侧探针完全一致）。
# ⚠️ 这里**只锁 case 名集合**，不锁数值 —— 数值由探针从真源现算并打印。
#    断言的是「差异仍然存在」（Go 值 != Java 值）；若哪天差异消失了 ⇒ exit 2 提示更新台账。
OBS_CASES="obs_custom_grpcstatus obs_ok_status"

TMP="$(mktemp -d)"
trap 'rm -rf "${TMP}"' EXIT

say() { printf '%s\n' "$*"; }
die() { printf '!! %s\n' "$*" >&2; exit 2; }

[ -f "${GO_REPO}/${GO_SRC}" ] || die "找不到 Go 真源：${GO_REPO}/${GO_SRC}（用 GO_REPO=... 指定）"
[ -f "${GO_REPO}/go.mod" ] || die "找不到 Go 真源 go.mod：${GO_REPO}/go.mod"

# ── 未登记码清单（回落语义的观察点）──────────────────────────────────────────
# ⚠️ 这份清单是**跨域撞车**的高价值样本：这些码值在 users / like / hot / article 域
#    是真实存在的业务码，但在 favorite 域**没有登记** ⇒ 两侧都必须回落到 "FAIL"。
# ⚠️ favorite 域**自己**的 5007/5008/5009 已登记（读路径/写路径/雪花 ID）
#    ⇒ 不能拿它们当回落样本，否则测的是「已登记码」。
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
5010
5011
5012
5204
5305
6001
9999
2147483647
-2147483648
EOF

say "== 1/6 校验 Go 真源并搭临时模块 =="
# ⚠️ 复制件必须与真源**逐字节相同**：临时模块里的 errmsg.go 一旦与真源漂移，
#    本脚本验的就不是真源了。所以这里比 sha256，不匹配直接退出。
GO_SHA="$(shasum -a 256 "${GO_REPO}/${GO_SRC}" | awk '{print $1}')"
mkdir -p "${TMP}/src/common"
cp "${GO_REPO}/${GO_SRC}" "${TMP}/src/common/errmsg.go"
cp "${HERE}/go/errmsg_probe.go" "${TMP}/src/common/errmsg_probe.go"
cp "${HERE}/go/main.go" "${TMP}/src/main.go"
COPY_SHA="$(shasum -a 256 "${TMP}/src/common/errmsg.go" | awk '{print $1}')"
[ "${GO_SHA}" = "${COPY_SHA}" ] || die "复制件与真源 sha256 不一致（${GO_SHA} != ${COPY_SHA}）"
say "   真源 sha256 = ${GO_SHA}  ✓ 与复制件一致"

# ⚠️ grpc 版本从真源 go.mod **动态读取**（不写死）⇒ 真源升版本时本脚本自动跟随。
GRPC_VER="$(awk '/^[[:space:]]*google\.golang\.org\/grpc v/ {print $2; exit}' "${GO_REPO}/go.mod")"
[ -n "${GRPC_VER}" ] || die "无法从 ${GO_REPO}/go.mod 解析 google.golang.org/grpc 版本"
say "   真源 go.mod 的 grpc 版本 = ${GRPC_VER}"

cat >"${TMP}/src/go.mod" <<EOF
module favoriteerrmsgprobe

go 1.25

require google.golang.org/grpc ${GRPC_VER}
EOF
# ⚠️ GOPROXY=off + 本地 module cache ⇒ 不联网。go mod tidy 从 cache 解析并生成 go.sum。
#    GOTOOLCHAIN=local 防止 go.mod 的 go 指令触发 toolchain 下载。
( cd "${TMP}/src" && GOPROXY=off GOFLAGS=-mod=mod GOSUMDB=off GOTOOLCHAIN=local go mod tidy ) \
  || die "go mod tidy 失败 —— 本地 module cache 里可能没有 grpc ${GRPC_VER}（需要先 go mod download）"

say "== 2/6 跑 Go 侧（真源 errmsg.go 同包编译 + 真 grpc 库）=="
( cd "${TMP}/src" && GOPROXY=off GOTOOLCHAIN=local go run . "${CASES}" ) >"${TMP}/go.txt" \
  || die "Go 侧执行失败（exit $?）—— 见上方输出"

say "== 3/6 编译 favorite-common =="
mvn -o -q -f "${RAG_ROOT}/pom.xml" -pl "${MODULE}" compile
CLASSES="${RAG_ROOT}/${MODULE}/target/classes"
[ -d "${CLASSES}" ] || die "${CLASSES} 不存在"

# ⚠️ 探针要 import io.grpc.Status / StatusException ⇒ grpc-api 必须在 classpath 上。
#    favorite-common 自己只引 grpc-stub（传递依赖 grpc-api），target/classes 里**没有** grpc 的 jar
#    ⇒ 这里用 dependency:build-classpath 取该模块的**真实**依赖集，
#    而不是手工 find ~/.m2 里的某个版本（那会让探针跑到与生产不同的 grpc 版本上）。
mvn -o -q -f "${RAG_ROOT}/pom.xml" -pl "${MODULE}" \
  dependency:build-classpath -Dmdep.outputFile="${TMP}/cp.txt" -Dmdep.includeScope=test \
  || die "dependency:build-classpath 失败"
[ -s "${TMP}/cp.txt" ] || die "dependency:build-classpath 没产出 classpath"
CP="${CLASSES}:$(cat "${TMP}/cp.txt")"

say "== 4/6 跑 Java 侧（真实 FavoriteErrorCode / FavoriteErrorMessages / FavoriteGrpcError）=="
javac -nowarn -encoding UTF-8 -cp "${CP}" -d "${TMP}/java" "${HERE}/java/Probe.java"
java -cp "${TMP}/java:${CP}" Probe "${CASES}" >"${TMP}/java.txt"

say "== 5/6 逐字节对拍（OBS 段除外）=="
# ⚠️ OBS 段单独抽出 —— 那一段是**登记在案**的已知差异，不参与逐字节对拍。
grep -v '^OBS|' "${TMP}/go.txt"   >"${TMP}/go-main.txt"
grep -v '^OBS|' "${TMP}/java.txt" >"${TMP}/java-main.txt"
grep    '^OBS|' "${TMP}/go.txt"   >"${TMP}/go-obs.txt"
grep    '^OBS|' "${TMP}/java.txt" >"${TMP}/java-obs.txt"

MAIN_OK=0
if diff -u "${TMP}/go-main.txt" "${TMP}/java-main.txt" >"${TMP}/diff.txt"; then
  say "   Go   sha256 = $(shasum -a 256 "${TMP}/go-main.txt" | awk '{print $1}')"
  say "   Java sha256 = $(shasum -a 256 "${TMP}/java-main.txt" | awk '{print $1}')"
  say "   行数 = $(wc -l <"${TMP}/go-main.txt" | tr -d ' ')（CONST + TABLE + FALLBACK + WIRE + BIZCODE）"
else
  MAIN_OK=1
  say "   Go   sha256 = $(shasum -a 256 "${TMP}/go-main.txt" | awk '{print $1}')"
  say "   Java sha256 = $(shasum -a 256 "${TMP}/java-main.txt" | awk '{print $1}')"
  say ""
  say "❌ 双侧不一致，diff（- = Go / + = Java）："
  cat "${TMP}/diff.txt"
fi

say ""
say "== 6/6 OBS 段（已知不可达差异，两侧本就不同）=="
# ⚠️ 断言 1：case 名集合必须与脚本声明的一致（防止有人悄悄删掉一条差异）。
GO_OBS_NAMES="$(cut -d'|' -f2 "${TMP}/go-obs.txt" | sort | tr '\n' ' ' | sed 's/ $//')"
JAVA_OBS_NAMES="$(cut -d'|' -f2 "${TMP}/java-obs.txt" | sort | tr '\n' ' ' | sed 's/ $//')"
EXPECT_OBS_NAMES="$(printf '%s\n' ${OBS_CASES} | sort | tr '\n' ' ' | sed 's/ $//')"
[ "${GO_OBS_NAMES}" = "${EXPECT_OBS_NAMES}" ] \
  || die "Go 侧 OBS case 集合与脚本声明不符（${GO_OBS_NAMES} != ${EXPECT_OBS_NAMES}）"
[ "${JAVA_OBS_NAMES}" = "${EXPECT_OBS_NAMES}" ] \
  || die "Java 侧 OBS case 集合与脚本声明不符（${JAVA_OBS_NAMES} != ${EXPECT_OBS_NAMES}）"

# ⚠️ 断言 2：每条 OBS 的 Go 值必须**仍然**与 Java 值不同 ——
#    若哪天 Java 侧改成与 Go 一致（差异消失）⇒ 必须重新评估并更新台账 ⇒ exit 2。
OBS_DIFF=0
while read -r name; do
  [ -n "${name}" ] || continue
  g="$(grep "^OBS|${name}|" "${TMP}/go-obs.txt"   | cut -d'|' -f3)"
  j="$(grep "^OBS|${name}|" "${TMP}/java-obs.txt" | cut -d'|' -f3)"
  if [ "${g}" = "${j}" ]; then
    say "   ⚠️  OBS|${name}: Go=${g} Java=${j} —— **差异已消失**，请重新评估并更新台账"
    OBS_DIFF=2
  else
    say "   OBS|${name}: Go=${g} Java=${j}  （已知不可达差异，登记在 docs/DRIFT/favorite/common.md）"
  fi
done <<<"$(printf '%s\n' ${OBS_CASES})"

say ""
if [ "${MAIN_OK}" = "0" ] && [ "${OBS_DIFF}" = "0" ]; then
  say "✅ 双侧逐字节一致（sha256 相同，diff 无输出）；OBS 段 ${OBS_CASES} 差异仍在登记状态"
  exit 0
fi
[ "${MAIN_OK}" = "0" ] || exit 1
exit "${OBS_DIFF}"
