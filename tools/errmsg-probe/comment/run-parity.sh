#!/usr/bin/env bash
#
# comment 域错误码契约：Go ↔ Java 双侧逐字节对拍（AGENTS.md §3.3 要求的「两侧同时跑的探针」）。
#
# 真源：Sea-RideTheWind_golang/service/comment/common/errmsg/errmsg.go（package errmsg，103 行，**只读**）
# 被测：RAG/service/comment/comment-common/src/main/java/com/rag/comment/comment/common/errmsg/
#       {CommentErrorCode,CommentErrorMessages,CommentCodeError,CommentGrpcError}.java
#
# 定位是【回归守门】：任何人改了 comment 的码值 / 文案 / 未知码回落语义 / wire 形态 /
# 业务码反查语义，本脚本立刻 exit 1。
#
# 用法：
#   bash tools/errmsg-probe/comment/run-parity.sh
#   GO_REPO=/path/to/Sea-RideTheWind_golang bash tools/errmsg-probe/comment/run-parity.sh
#
# 退出码：0 = 双侧逐字节一致；1 = 有真漂移；2 = 环境问题（输入不可见 / 探针跑不起来）。
#
# 依赖：mvn（离线 -o）、JDK 21+、Go toolchain（≥1.25）、本地 Go module cache（需含 grpc）。
#
# ⚠️ 与 tools/errmsg-probe（article 版）的三点差异：
#   1. comment 的 errmsg.go **import 了 grpc**（codes + status）⇒ 临时模块**不能零依赖**
#      ⇒ 继承真源 go.mod / go.sum（只改 module 行），grpc 版本**从真源 go.mod 动态读取**
#      ⇒ 真源升版本时本脚本自动跟随（不写死 ⇒ 不会指向另一份内容不同的副本）。
#   2. 多验三层 article 版没有的语义：
#      · WIRE  —— 每个码经 GRPCError 构造出的 wire 形态（业务码在 description、不在 grpc-status）
#      · WIREX —— grpc 码 × 业务码 的 wire 矩阵（8 个 grpc 码 × 3 个业务码）
#      · BIZCODE —— BizCodeFromError 的还原矩阵（含「文案反查优先于 gRPC 码类别」）
#   3. 多一个 OBS 段 —— **两侧必然不同**的已知不可达差异，单列不参与逐字节对拍，
#      但差异「消失」时会报错（逼人更新台账，而不是让它静默漂移）。
#
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAG_ROOT="$(cd "${HERE}/../../.." && pwd)"

# 真源与被测的相对路径（**先定义**，下面的解析函数要用）。
GO_SRC="service/comment/common/errmsg/errmsg.go"
MODULE="service/comment/comment-common"
PROBE_MODULE="commenterrmsgprobe"

say() { printf '%s\n' "$*"; }
die() { printf '!! %s\n' "$*" >&2; exit 2; }

# Go 真源仓库：默认**向上逐层找**（可用 GO_REPO 覆盖）。
#
# ⚠️ 为什么不写死「RAG 的同级兄弟目录」：
#   在 **git worktree** 里（RAG.worktrees/<lane>/），RAG 的父目录是 `RAG.worktrees/`
#   ⇒ 「同级兄弟」会解析成 `RAG.worktrees/Sea-RideTheWind_golang` ⇒ **不存在** ⇒ 探针 exit 2。
#   ⇒ 逐级向上找 6 层，找不到再报错并提示用 GO_REPO=... 指定。
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
[ -n "${GO_REPO}" ] || die "找不到 Go 真源（向上 6 层内没有 Sea-RideTheWind_golang）—— 用 GO_REPO=... 指定"

# OBS 段：两侧**必然不同**的已知不可达差异（case 名必须与两侧探针完全一致）。
# ⚠️ comment 域与 follow / message 域的 OBS **内容不同**：
#    follow / message 域的 OBS 是「反查语义的实现层差异」；
#    comment 域的 Go 侧**根本没有** BizCodeFromError ⇒ OBS 记的是
#    「**业务码在 wire 上的载体不同**」（Go 写进 grpc-status，Java 只能放自定义 trailer）。
OBS_CASES="obs_wire_grpcstatus_1012 obs_wire_grpcstatus_2001 obs_wire_grpcstatus_5003"

TMP="$(mktemp -d)"
trap 'rm -rf "${TMP}"' EXIT

[ -f "${GO_REPO}/${GO_SRC}" ] || die "找不到 Go 真源：${GO_REPO}/${GO_SRC}（用 GO_REPO=... 指定）"
[ -f "${GO_REPO}/go.mod" ] || die "找不到 Go 真源 go.mod：${GO_REPO}/go.mod"
[ -f "${GO_REPO}/go.sum" ] || die "找不到 Go 真源 go.sum：${GO_REPO}/go.sum"

# ── 未登记码清单（回落语义的观察点）──────────────────────────────────────────
# ⚠️ 这份清单刻意挑**跨域撞车**的码值：这些码在别的域是真实存在的业务码，
#    但在 comment 域**没有登记** ⇒ 两侧都必须回落到 "FAIL"。
#      1001/1012/1013  comment/users: 用户名已存在 / 评论区不存在 / 评论不存在
#      2001            comment: 输入有误
#      3001            users: 积分余量不足   · like: 构建 outbox 失败
#      5009/5010       comment: Kafka 消息发送失败 / 延时消息发送失败
#      5102/5205/5206/5207  message: 管理员不存在 / 只有管理员能初始化该会话 / 首条私聊过长 / 不支持的发送者角色
# ⚠️ comment 域自己已登记 200 / 500 / 1015 / 5001-5008 / 5101 / 5201-5204
#    ⇒ 上面的值**都不能**出现在本域已登记集合里，否则测的是「已登记码」而不是「回落」。
# ⚠️ 走 int 边界值：Go 的 int 是 64 位、Java 的 int 是 32 位，
#    跨语言比较只能用两侧都放得下的值，否则比的不是同一个输入。
CASES="${TMP}/cases.txt"
cat >"${CASES}" <<'EOF'
0
-1
1
999
1014
1016
1017
1018
2002
3001
5000
5011
5012
5101
5201
5204
5301
9999
2147483647
-2147483648
EOF

# ── 输入根探针 ───────────────────────────────────────────────────────────────
# ⚠️ 为什么必须硬失败（而不是「跳过这个根继续跑」）：
#    本机 ~/go/pkg/mod 的可见性**会间歇**（同一路径反复「可读 → 不可读 → 可读」）
#    ⇒ 探不到 grpc 时 go run 会失败（exit 2 已覆盖），但**更危险的是**
#    「拿不到就悄悄换个来源」⇒ 本脚本只允许从 module cache 读 ⇒ 探不到即 exit 2。
# ⚠️ 头尾各探一次：尾探针与头探针不一致 ⇒ 可见性在扫描期间变了 ⇒ 本次结果不可复算。
GOMODCACHE="$(go env GOMODCACHE 2>/dev/null || true)"
[ -n "${GOMODCACHE}" ] || die "无法读 go env GOMODCACHE"

# ⚠️ grpc 版本从真源 go.mod **动态读取**（不写死）⇒ 真源升版本时本脚本自动跟随。
GRPC_VER="$(awk '/^[[:space:]]*google\.golang\.org\/grpc v/ {print $2; exit}' "${GO_REPO}/go.mod")"
[ -n "${GRPC_VER}" ] || die "无法从 ${GO_REPO}/go.mod 解析 google.golang.org/grpc 版本"

# ⚠️ GRPC_VER 形如 "v1.81.1"（**已带 v 前缀**）⇒ module cache 的目录名是
#    `grpc@v1.81.1`、下载缓存的文件名是 `v1.81.1.mod` ⇒ 都直接用 ${GRPC_VER}，不要再加 v。
PROBE_ROOTS="${GOMODCACHE}/google.golang.org/grpc@${GRPC_VER}/status/status.go
${GOMODCACHE}/google.golang.org/grpc@${GRPC_VER}/codes/code_string.go
${GOMODCACHE}/cache/download/google.golang.org/grpc/@v/${GRPC_VER}.mod"

probe_roots() {
  local rc=0 f
  while IFS= read -r f; do
    [ -n "$f" ] || continue
    if [ -r "$f" ]; then
      say "   ✓ 输入根可见：${f}"
    else
      say "   ✗ 输入根不可见：${f}"
      rc=1
    fi
  done <<EOF
${PROBE_ROOTS}
EOF
  return $rc
}

say "== 1/7 输入根探针（module cache 可见性）=="
say "   GOMODCACHE = ${GOMODCACHE}"
say "   真源 go.mod 的 grpc 版本 = ${GRPC_VER}"
probe_roots || die "输入根探针失败（module cache 不可读）—— 不可静默降级成「这条不可核验」"

say ""
say "== 2/7 校验 Go 真源并搭临时模块 =="
# ⚠️ 复制件必须与真源**逐字节相同**：临时模块里的 errmsg.go 一旦与真源漂移，
#    本脚本验的就不是真源了。所以这里比 sha256，不匹配直接退出。
GO_SHA="$(shasum -a 256 "${GO_REPO}/${GO_SRC}" | awk '{print $1}')"
mkdir -p "${TMP}/src/errmsg"
cp "${GO_REPO}/${GO_SRC}" "${TMP}/src/errmsg/errmsg.go"
cp "${HERE}/go/errmsg_probe.go" "${TMP}/src/errmsg/errmsg_probe.go"
cp "${HERE}/go/main.go" "${TMP}/src/main.go"
COPY_SHA="$(shasum -a 256 "${TMP}/src/errmsg/errmsg.go" | awk '{print $1}')"
[ "${GO_SHA}" = "${COPY_SHA}" ] || die "复制件与真源 sha256 不一致（${GO_SHA} != ${COPY_SHA}）"
say "   真源 sha256 = ${GO_SHA}  ✓ 与复制件一致"
# ⚠️ 继承真源 go.mod（只改 module 行）+ go.sum：
#    comment 的 errmsg.go import gRPC ⇒ 零依赖 go.mod 会编译失败。
#    ⚠️ 不走 `go mod tidy`：tidy 会去解析**依赖的测试依赖**（go-zero → yaml.v2 → check.v1 …），
#    那些未必在本地 module cache 里，而本探针根本不需要它们。
sed "s|^module sea-try-go\$|module ${PROBE_MODULE}|" "${GO_REPO}/go.mod" >"${TMP}/src/go.mod"
cp "${GO_REPO}/go.sum" "${TMP}/src/go.sum"
say "   临时模块 = ${PROBE_MODULE}（继承真源 go.mod/go.sum ⇒ gRPC 依赖从 module cache 离线解析）"

say ""
say "== 3/7 跑 Go 侧（真源 errmsg.go 同包编译 + 真 grpc 库）=="
( cd "${TMP}/src" && GOPROXY=off GOFLAGS=-mod=mod GOSUMDB=off GOTOOLCHAIN=local go run . "${CASES}" ) >"${TMP}/go.txt" \
  || die "Go 侧执行失败（exit $?）—— 见上方输出"

say ""
say "== 4/7 编译 comment-common =="
mvn -o -q -f "${RAG_ROOT}/pom.xml" -pl "${MODULE}" -am compile
# ⚠️ RAG 侧的 classpath 根目录。
#    默认 = 模块的 target/classes（**生产编译产物**）。
#    PROBE_JAVA_CLASSES 可覆盖 —— **只给 mutate-parity.sh 用**：它把「变异后的源码副本」
#    编译到临时目录再指过来 ⇒ 变异**不碰仓内任何文件**。
#    ⚠️ 覆盖值会被原样打印 ⇒ 守门日志里一眼能看出「本次跑的不是生产 classpath」。
CLASSES="${PROBE_JAVA_CLASSES:-${RAG_ROOT}/${MODULE}/target/classes}"
# ⚠️ PROBE_JAVA_CLASSES 允许是**冒号分隔的列表**（变异测试会把「变异副本 + 生产 classpath」串起来）
#    ⇒ 必须**逐项**校验存在性，不能对整串做 `[ -d ]`。
CLASSES_OLD_IFS="${IFS}"
IFS=':'
for c in ${CLASSES}; do
  [ -d "$c" ] || { IFS="${CLASSES_OLD_IFS}"; die "classpath 条目不存在：$c"; }
done
IFS="${CLASSES_OLD_IFS}"
say "   RAG 侧 classpath = ${CLASSES}"

# ⚠️ 探针要 import io.grpc.Status / StatusException ⇒ grpc-api 必须在 classpath 上。
#    这里用 dependency:build-classpath 取该模块的**真实**依赖集，
#    而不是手工 find ~/.m2 里的某个版本（那会让探针跑到与生产不同的 grpc 版本上）。
mvn -o -q -f "${RAG_ROOT}/pom.xml" -pl "${MODULE}" -am \
  dependency:build-classpath -Dmdep.outputFile="${TMP}/cp.txt" -Dmdep.includeScope=test \
  || die "dependency:build-classpath 失败"
[ -s "${TMP}/cp.txt" ] || die "dependency:build-classpath 没产出 classpath"
CP="${CLASSES}:$(cat "${TMP}/cp.txt")"

say ""
say "== 5/7 跑 Java 侧（真实 CommentErrorCode / CommentErrorMessages / CommentCodeError / CommentGrpcError）=="
javac -nowarn -encoding UTF-8 -cp "${CP}" -d "${TMP}/java" "${HERE}/java/Probe.java"
java -cp "${TMP}/java:${CP}" com.rag.comment.comment.common.errmsg.Probe "${CASES}" >"${TMP}/java.txt"

say ""
say "== 6/7 逐字节对拍（OBS 段除外）=="
# ⚠️ OBS 段单独抽出 —— 那一段是**登记在案**的已知差异，不参与逐字节对拍。
grep -v '^OBS|' "${TMP}/go.txt"   >"${TMP}/go-main.txt"
grep -v '^OBS|' "${TMP}/java.txt" >"${TMP}/java-main.txt"
grep    '^OBS|' "${TMP}/go.txt"   >"${TMP}/go-obs.txt"
grep    '^OBS|' "${TMP}/java.txt" >"${TMP}/java-obs.txt"

MAIN_OK=0
if diff -u "${TMP}/go-main.txt" "${TMP}/java-main.txt" >"${TMP}/diff.txt"; then
  say "   Go   sha256 = $(shasum -a 256 "${TMP}/go-main.txt" | awk '{print $1}')"
  say "   Java sha256 = $(shasum -a 256 "${TMP}/java-main.txt" | awk '{print $1}')"
  say "   行数 = $(wc -l <"${TMP}/go-main.txt" | tr -d ' ')（CONST + TABLE + FALLBACK + CODEERR + CODEERRMSG + WIRE）"
else
  MAIN_OK=1
  say "   Go   sha256 = $(shasum -a 256 "${TMP}/go-main.txt" | awk '{print $1}')"
  say "   Java sha256 = $(shasum -a 256 "${TMP}/java-main.txt" | awk '{print $1}')"
  say ""
  say "❌ 双侧不一致，diff（- = Go / + = Java）："
  cat "${TMP}/diff.txt"
fi

say ""
say "== 7/7 OBS 段（已知不可达差异，两侧本就不同）=="
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
  g="$(grep "^OBS|${name}|" "${TMP}/go-obs.txt"   | cut -d'|' -f3-)"
  j="$(grep "^OBS|${name}|" "${TMP}/java-obs.txt" | cut -d'|' -f3-)"
  if [ "${g}" = "${j}" ]; then
    say "   ⚠️  OBS|${name}: Go=${g} Java=${j} —— **差异已消失**，请重新评估并更新台账"
    OBS_DIFF=2
  else
    say "   OBS|${name}: Go=${g} Java=${j}  （已知不可达差异，登记在 docs/DRIFT/comment/common.md）"
  fi
done <<<"$(printf '%s\n' ${OBS_CASES})"

say ""
say "== 尾输入根探针（扫描期间可见性是否变化）=="
probe_roots || die "尾探针失败：扫描期间 module cache 可见性变了 ⇒ 本次结果不可复算"

say ""
if [ "${MAIN_OK}" = "0" ] && [ "${OBS_DIFF}" = "0" ]; then
  say "✅ 双侧逐字节一致（sha256 相同，diff 无输出）；OBS 段 ${OBS_CASES} 差异仍在登记状态"
  exit 0
fi
[ "${MAIN_OK}" = "0" ] || exit 1
exit "${OBS_DIFF}"
