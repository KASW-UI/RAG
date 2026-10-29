#!/usr/bin/env bash
#
# task 域错误契约：Go ↔ Java 双侧逐字节对拍（AGENTS.md §3.3 要求的「两侧同时跑的探针」）。
#
# 真源：Sea-RideTheWind_golang/service/task/{rpc,api}（**只读**，冻结）
# 被测：RAG/service/task/{task-rpc,task-api} 的**生产编译产物**
#
# 定位是【回归守门】：任何人改了 task 域的 gRPC code / description / HTTP 状态码 /
# text/plain 文案 / 「rpc 错误往上抛时保不保得住 code」，本脚本立刻 exit 1。
#
# 用法：
#   bash tools/errmsg-probe/task/run-parity.sh
#   GO_REPO=/path/to/Sea-RideTheWind_golang bash tools/errmsg-probe/task/run-parity.sh
#
# 退出码：0 = 双侧逐字节一致；1 = 有真漂移；2 = 环境问题（输入不可见 / 探针跑不起来）。
#
# 依赖：mvn（离线 -o）、JDK 21+、Go toolchain（≥1.25）、本地 Go module cache
#       （需含 grpc v1.81.1 / gorm v1.31.1 / gorm postgres driver v1.6.0 / go-redis v9.20.0）。
# **不需要** PG / Redis / Kafka / 任何容器（本探针只在「错误契约层」，不碰数据面）。
#
# ⚠️ 与 tools/errmsg-probe/{follow,message} 的**四点结构差异**（本域没有 -common 模块）：
#   1. **没有码值表** ⇒ 本探针比的是「**gRPC 状态 + description**」与
#      「**HTTP 状态码 + text/plain body**」两层，不是 like/message 那种业务码 ↔ gRPC code 表。
#   2. **Go 侧要复制整棵 `service/task` 树**（follow 只需一个 errmsg.go）：
#      因为 task 域的**错误出口散在 logic / handler 里**，必须**直调真源逻辑包**才能验。
#      ⚠️ 临时模块的 module 行**必须保持 `sea-try-go`**（否则 Go 的 internal 可见性规则
#      不允许 import `service/task/rpc/internal/logic`）。
#   3. **两套 Go 探针 + 两套 Java 探针**（rpc 层 / api 层各一套）⇒ 输出**拼接**后比对。
#   4. **OBS 段有 4 条**（2 条跨语言形态 + 2 条库/框架措辞差异），
#      差异「消失」时 exit 2 逼人更新台账。
#
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAG_ROOT="$(cd "${HERE}/../../.." && pwd)"

# 真源与被测的相对路径（**先定义**，下面的解析函数要用）。
GO_TREE="service/task"
GO_RPC_LOGIC="service/task/rpc/internal/logic/get_task_logic.go"
GO_API_TYPES="service/task/api/internal/types/types.go"
MODULE_RPC="service/task/task-rpc"
MODULE_API="service/task/task-api"
PROBE_PKG="com.rag.task.task.probe"

say() { printf '%s\n' "$*"; }
die() { printf '!! %s\n' "$*" >&2; exit 2; }

# Go 真源仓库：默认**向上逐层查找**（可用 GO_REPO 覆盖）。
#
# ⚠️ 为什么不写死「RAG 的同级兄弟目录」：
#   在 **git worktree** 里（RAG.worktrees/<lane>/），RAG 的父目录是 `RAG.worktrees/`
#   ⇒ 「同级兄弟」会解析成 `RAG.worktrees/Sea-RideTheWind_golang` ⇒ **不存在** ⇒ 探针 exit 2。
#   ⇒ 逐级向上找 6 层，找不到再报错并提示用 GO_REPO=... 指定。
default_go_repo() {
  local d
  d="$(cd "${RAG_ROOT}" && pwd)"
  for _ in 1 2 3 4 5 6; do
    if [ -f "${d}/Sea-RideTheWind_golang/${GO_RPC_LOGIC}" ]; then
      (cd "${d}/Sea-RideTheWind_golang" && pwd); return 0
    fi
    d="$(dirname "${d}")"
  done
  return 1
}
GO_REPO="${GO_REPO:-$(default_go_repo || true)}"
[ -n "${GO_REPO}" ] || die "找不到 Go 真源（向上 6 层内没有 Sea-RideTheWind_golang）—— 用 GO_REPO=... 指定"

# OBS 段：两侧**必然不同**的已知不可达差异（case 名必须与两侧探针完全一致）。
# ⚠️ 这里**只锁 case 名集合**，不锁数值 —— 数值由探针从真源现算并打印。
#    断言的是「差异仍然存在」（Go 值 != Java 值）；若哪天差异消失了 ⇒ exit 2 提示更新台账。
OBS_CASES="obs_ok_status obs_wrapped_status obs_api_empty_body obs_api_malformed_json"

TMP="$(mktemp -d)"
trap 'rm -rf "${TMP}"' EXIT

for f in "${GO_REPO}/${GO_RPC_LOGIC}" "${GO_REPO}/${GO_API_TYPES}" \
         "${GO_REPO}/go.mod" "${GO_REPO}/go.sum"; do
  [ -f "$f" ] || die "找不到 Go 真源必需文件：${f}（用 GO_REPO=... 指定）"
done

# ═══════════════════════════════════════════════════════════════════════════
# 1/8 输入根探针
# ═══════════════════════════════════════════════════════════════════════════
# ⚠️ 为什么必须硬失败（而不是「跳过这个根继续跑」）：
#    本机 ~/go/pkg/mod 的可见性**会间歇**（同一路径反复「可读 → 不可读 → 可读」）
#    ⇒ 探不到依赖时 go build 会失败（exit 2 已覆盖），但**更危险的是**
#    「拿不到就悄悄换个来源」⇒ 本脚本只允许从 module cache 读 ⇒ 探不到即 exit 2。
# ⚠️ 头尾各探一次：尾探针与头探针不一致 ⇒ 可见性在扫描期间变了 ⇒ 本次结果不可复算。
GOMODCACHE="$(go env GOMODCACHE 2>/dev/null || true)"
[ -n "${GOMODCACHE}" ] || die "无法读 go env GOMODCACHE"

# ⚠️ 依赖版本从真源 go.mod **动态读取**（不写死）⇒ 真源升版本时本脚本自动跟随。
dep_version() { awk -v m="$1" '$1 == m {print $2; exit}' "${GO_REPO}/go.mod"; }
GRPC_VER="$(dep_version google.golang.org/grpc)"
GORM_VER="$(dep_version gorm.io/gorm)"
PGDRV_VER="$(dep_version gorm.io/driver/postgres)"
REDIS_VER="$(dep_version github.com/redis/go-redis/v9)"
[ -n "${GRPC_VER}" ] || die "无法从 ${GO_REPO}/go.mod 解析 google.golang.org/grpc 版本"
[ -n "${GORM_VER}" ] || die "无法从 ${GO_REPO}/go.mod 解析 gorm.io/gorm 版本"
[ -n "${PGDRV_VER}" ] || die "无法从 ${GO_REPO}/go.mod 解析 gorm.io/driver/postgres 版本"
[ -n "${REDIS_VER}" ] || die "无法从 ${GO_REPO}/go.mod 解析 github.com/redis/go-redis/v9 版本"

PROBE_ROOTS="${GOMODCACHE}/google.golang.org/grpc@${GRPC_VER}/status/status.go
${GOMODCACHE}/google.golang.org/grpc@${GRPC_VER}/codes/code_string.go
${GOMODCACHE}/gorm.io/gorm@${GORM_VER}/gorm.go
${GOMODCACHE}/gorm.io/driver/postgres@${PGDRV_VER}/postgres.go
${GOMODCACHE}/github.com/redis/go-redis/v9@${REDIS_VER}/redis.go
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

say "== 1/8 输入根探针（module cache 可见性）=="
say "   GOMODCACHE = ${GOMODCACHE}"
say "   真源 go.mod 的依赖版本：grpc=${GRPC_VER} gorm=${GORM_VER} postgres=${PGDRV_VER} go-redis=${REDIS_VER}"
probe_roots || die "输入根探针失败（module cache 不可读）—— 不可静默降级成「这条不可核验」"

# ═══════════════════════════════════════════════════════════════════════════
# 2/8 搭临时 Go 模块（真源 service/task 树 + 两个探针）
# ═══════════════════════════════════════════════════════════════════════════
say ""
say "== 2/8 搭临时 Go 模块（真源 service/task 树逐字节复制）=="
# ⚠️ module 行**保持 `sea-try-go` 不变**：真源的 `service/task/{rpc,api}/internal/**`
#    只有在**同一棵 import 路径**下才可见 ⇒ 改 module 名会让 internal 包全部不可导入。
mkdir -p "${TMP}/src"
cp "${GO_REPO}/go.mod" "${GO_REPO}/go.sum" "${TMP}/src/"
mkdir -p "${TMP}/src/service/task"
cp -R "${GO_REPO}/${GO_TREE}/rpc" "${TMP}/src/service/task/"
cp -R "${GO_REPO}/${GO_TREE}/api" "${TMP}/src/service/task/"
mkdir -p "${TMP}/src/service/task/rpc/probe" "${TMP}/src/service/task/api/probe"
cp "${HERE}/go/rpc_probe.go" "${TMP}/src/service/task/rpc/probe/main.go"
cp "${HERE}/go/api_probe.go"  "${TMP}/src/service/task/api/probe/main.go"
# ⚠️ 复制件必须与真源**逐字节相同**：临时模块里的源码一旦与真源漂移，
#    本脚本验的就不是真源了 ⇒ 这里比 sha256。
for rel in "${GO_RPC_LOGIC}" "${GO_API_TYPES}"; do
  a="$(shasum -a 256 "${GO_REPO}/${rel}" | awk '{print $1}')"
  b="$(shasum -a 256 "${TMP}/src/${rel}" | awk '{print $1}')"
  [ "${a}" = "${b}" ] || die "复制件与真源 sha256 不一致（${rel}: ${a} != ${b}）"
  say "   ✓ ${rel} 复制件与真源 sha256 一致（${a}）"
done
say "   临时模块 module = $(awk '/^module /{print $2; exit}' "${TMP}/src/go.mod")（**必须**是 sea-try-go，否则 internal 包不可导入）"

# ═══════════════════════════════════════════════════════════════════════════
# 3/8 跑 Go 侧（直调真源 logic / handler）
# ═══════════════════════════════════════════════════════════════════════════
say ""
say "== 3/8 跑 Go 侧（真源 logic.GetTaskLogic + 真源 handler.GetTaskHandler）=="

# ── 用例表（**两侧共用同一份文件** ⇒ 不会各自维护一份而悄悄错位）────────────
UID_CASES="${TMP}/uid-cases.txt"
BARE_CASES="${TMP}/bare-cases.txt"
API_CASES="${TMP}/api-cases.txt"
cat >"${UID_CASES}" <<'EOF'
rpc_uid_zero|0
rpc_uid_neg_one|-1
rpc_uid_neg_min|-9223372036854775808
rpc_uid_neg_big|-1234567890123
EOF
cat >"${BARE_CASES}" <<'EOF'
rpc_bare_simple|boom
rpc_bare_pq|pq: deadlock detected
rpc_bare_empty|
rpc_bare_unicode|用户不存在
rpc_bare_has_pipe|left|right
EOF
# ⚠️ api 用例的 rpcCode 取 grpc 的**数字**码（0=OK 1=CANCELLED 2=UNKNOWN 3=INVALID_ARGUMENT
#    4=DEADLINE_EXCEEDED 5=NOT_FOUND 6=ALREADY_EXISTS 7=PERMISSION_DENIED 8=RESOURCE_EXHAUSTED
#    9=FAILED_PRECONDITION 10=ABORTED 11=OUT_OF_RANGE 12=UNIMPLEMENTED 13=INTERNAL
#    14=UNAVAILABLE 15=DATA_LOSS 16=UNAUTHENTICATED）⇒ 15 个非 OK 码全覆盖
#    （go-zero rest/internal/errcode/grpc.go:12-42 的 15 个分支）。
cat >"${API_CASES}" <<'EOF'
api_id_missing|{}|-|-
api_id_null|{"id":null}|-|-
api_id_other_field|{"other":1}|-|-
api_id_zero_num|{"id":0}|-|-
api_id_negative_num|{"id":-5}|-|-
api_id_zero_str|{"id":"0"}|-|-
api_id_negative_str|{"id":"-5"}|-|-
api_id_abc|{"id":"abc"}|-|-
api_id_empty_str|{"id":""}|-|-
api_id_space_str|{"id":"   "}|-|-
api_id_float|{"id":1.5}|-|-
api_id_float_whole|{"id":1.0}|-|-
api_id_exp|{"id":1e2}|-|-
api_id_huge|{"id":99999999999999999999}|-|-
api_id_bool|{"id":true}|-|-
api_id_array|{"id":[1]}|-|-
api_id_object|{"id":{}}|-|-
api_rpc_invalid_argument|{"id":1}|3|user_id invalid
api_rpc_unknown|{"id":1}|2|pq: deadlock detected
api_rpc_not_found|{"id":1}|5|record not found
api_rpc_permission_denied|{"id":1}|7|forbidden
api_rpc_unauthenticated|{"id":1}|16|no token
api_rpc_deadline_exceeded|{"id":1}|4|context deadline exceeded
api_rpc_unavailable|{"id":1}|14|downstream down
api_rpc_already_exists|{"id":1}|6|duplicated
api_rpc_resource_exhausted|{"id":1}|8|too many requests
api_rpc_aborted|{"id":1}|10|aborted
api_rpc_cancelled|{"id":1}|1|cancelled
api_rpc_failed_precondition|{"id":1}|9|not ready
api_rpc_out_of_range|{"id":1}|11|out of range
api_rpc_internal|{"id":1}|13|internal boom
api_rpc_data_loss|{"id":1}|15|data loss
api_rpc_unimplemented|{"id":1}|12|not implemented
api_rpc_padded_uid_ok|{"id":"  12  "}|3|user_id invalid
EOF

# ⚠️ CGO_ENABLED=0：本机没有可用的 cgo 头文件（`stdlib.h file not found`）
#    ⇒ go build 会在 runtime/cgo 上硬失败。task 域的依赖（pgx / go-redis）全是纯 Go ⇒ 无影响。
export CGO_ENABLED=0 GOPROXY=off GOFLAGS=-mod=mod GOSUMDB=off GOTOOLCHAIN=local
( cd "${TMP}/src" && go build -o "${TMP}/rpcprobe" ./service/task/rpc/probe ) \
  || die "Go 侧（rpc 层）编译失败 —— 见上方输出"
( cd "${TMP}/src" && go build -o "${TMP}/apiprobe" ./service/task/api/probe ) \
  || die "Go 侧（api 层）编译失败 —— 见上方输出"
"${TMP}/rpcprobe" "${UID_CASES}" "${BARE_CASES}" >"${TMP}/go-rpc.txt" \
  || die "Go 侧（rpc 层）执行失败（exit $?）"
"${TMP}/apiprobe" "${API_CASES}" >"${TMP}/go-api.txt" \
  || die "Go 侧（api 层）执行失败（exit $?）"

# ═══════════════════════════════════════════════════════════════════════════
# 4/8 编译 Java 侧（生产 classpath）
# ═══════════════════════════════════════════════════════════════════════════
say ""
say "== 4/8 编译 task-rpc / task-api（生产编译产物）=="
mvn -o -q -f "${RAG_ROOT}/pom.xml" -pl "${MODULE_RPC},${MODULE_API}" -am compile \
  || die "mvn compile 失败"

# ⚠️ RAG 侧的 classpath 根目录。默认 = 模块的 target/classes（**生产编译产物**）。
#    PROBE_RPC_CLASSES / PROBE_API_CLASSES 可覆盖 —— **只给 mutate-parity.sh 用**：
#    它把「变异后的源码副本」编译到临时目录再指过来 ⇒ 变异**不碰仓内任何文件**。
#    ⚠️ 覆盖值会被原样打印 ⇒ 守门日志里一眼能看出「本次跑的不是生产 classpath」。
CLASSES_RPC="${PROBE_RPC_CLASSES:-${RAG_ROOT}/${MODULE_RPC}/target/classes}"
CLASSES_API="${PROBE_API_CLASSES:-${RAG_ROOT}/${MODULE_API}/target/classes}"
# ⚠️ 允许是**冒号分隔的列表**（变异测试会把「变异副本 + 生产 classpath」串起来）
#    ⇒ 必须**逐项**校验存在性，不能对整串做 `[ -d ]`。
check_classpath() {
  local list="$1" label="$2" c oldifs
  oldifs="${IFS}"
  IFS=':'
  for c in ${list}; do
    [ -d "$c" ] || { IFS="${oldifs}"; die "${label} classpath 条目不存在：$c"; }
  done
  IFS="${oldifs}"
}
check_classpath "${CLASSES_RPC}" "task-rpc"
check_classpath "${CLASSES_API}" "task-api"
say "   task-rpc classpath 根 = ${CLASSES_RPC}"
say "   task-api classpath 根 = ${CLASSES_API}"

# ⚠️ 用 dependency:build-classpath 取模块的**真实**依赖集，
#    而不是手工 find ~/.m2 里的某个版本（那会让探针跑到与生产不同的 grpc / Jackson 版本上）。
mvn -o -q -f "${RAG_ROOT}/pom.xml" -pl "${MODULE_RPC}" -am \
  dependency:build-classpath -Dmdep.outputFile="${TMP}/cp-rpc.txt" -Dmdep.includeScope=test \
  || die "dependency:build-classpath（task-rpc）失败"
mvn -o -q -f "${RAG_ROOT}/pom.xml" -pl "${MODULE_API}" -am \
  dependency:build-classpath -Dmdep.outputFile="${TMP}/cp-api.txt" -Dmdep.includeScope=test \
  || die "dependency:build-classpath（task-api）失败"
[ -s "${TMP}/cp-rpc.txt" ] || die "dependency:build-classpath（task-rpc）没产出 classpath"
[ -s "${TMP}/cp-api.txt" ] || die "dependency:build-classpath（task-api）没产出 classpath"
CP_RPC="${CLASSES_RPC}:$(cat "${TMP}/cp-rpc.txt")"
CP_API="${CLASSES_API}:$(cat "${TMP}/cp-api.txt")"

# ═══════════════════════════════════════════════════════════════════════════
# 5/8 跑 Java 侧（真实 TaskServiceGrpcImpl / TaskDomainService / TaskController …）
# ═══════════════════════════════════════════════════════════════════════════
say ""
say "== 5/8 跑 Java 侧（真实生产类）=="
mkdir -p "${TMP}/javac"
javac -nowarn -encoding UTF-8 -cp "${CP_RPC}" -d "${TMP}/javac" "${HERE}/java/RpcProbe.java" \
  || die "javac（RpcProbe）失败"
javac -nowarn -encoding UTF-8 -cp "${CP_API}" -d "${TMP}/javac" "${HERE}/java/ApiProbe.java" \
  || die "javac（ApiProbe）失败"
java -cp "${TMP}/javac:${CP_RPC}" "${PROBE_PKG}.RpcProbe" "${UID_CASES}" "${BARE_CASES}" \
  >"${TMP}/java-rpc.txt" || die "Java 侧（rpc 层）执行失败（exit $?）"
java -cp "${TMP}/javac:${CP_API}" "${PROBE_PKG}.ApiProbe" "${API_CASES}" \
  >"${TMP}/java-api.txt" || die "Java 侧（api 层）执行失败（exit $?）"

cat "${TMP}/go-rpc.txt"   "${TMP}/go-api.txt"   >"${TMP}/go.txt"
cat "${TMP}/java-rpc.txt" "${TMP}/java-api.txt" >"${TMP}/java.txt"

# ⚠️ **dump 形态自检**：每一行都必须是已知的段前缀。
#    目的：日志 / 警告混进 stdout 时**立刻 exit 2**（环境问题），
#    而不是变成一堆「双侧不一致」（看起来像真漂移）⇒ 假红会掩盖真问题。
validate_dump() {
  local f="$1" label="$2" total bad
  total="$(wc -l <"${f}" | tr -d ' ')"
  [ "${total}" -gt 0 ] || die "${label} dump 是空的 ⇒ 探针没跑到（环境问题）"
  bad="$(grep -vcE '^(RPCUID|RPCBARE|API|OBS)\|' "${f}" || true)"
  if [ "${bad}" != "0" ]; then
    say "   ✗ ${label} dump 有 ${bad} 行不是已知段前缀（多半是日志混进了 stdout）："
    grep -nvE '^(RPCUID|RPCBARE|API|OBS)\|' "${f}" | head -5 | sed 's/^/   | /'
    die "${label} dump 形态非法 ⇒ 环境问题（不是漂移）"
  fi
  say "   ${label} dump：${total} 行，形态自检通过"
}
validate_dump "${TMP}/go.txt" "Go  "
validate_dump "${TMP}/java.txt" "Java"

# ═══════════════════════════════════════════════════════════════════════════
# 6/8 逐字节对拍（OBS 段除外）
# ═══════════════════════════════════════════════════════════════════════════
say ""
say "== 6/8 逐字节对拍（OBS 段除外）=="
grep -v '^OBS|' "${TMP}/go.txt"   >"${TMP}/go-main.txt"
grep -v '^OBS|' "${TMP}/java.txt" >"${TMP}/java-main.txt"
grep    '^OBS|' "${TMP}/go.txt"   >"${TMP}/go-obs.txt"
grep    '^OBS|' "${TMP}/java.txt" >"${TMP}/java-obs.txt"

MAIN_OK=0
if diff -u "${TMP}/go-main.txt" "${TMP}/java-main.txt" >"${TMP}/diff.txt"; then
  say "   Go   sha256 = $(shasum -a 256 "${TMP}/go-main.txt" | awk '{print $1}')"
  say "   Java sha256 = $(shasum -a 256 "${TMP}/java-main.txt" | awk '{print $1}')"
  say "   行数 = $(wc -l <"${TMP}/go-main.txt" | tr -d ' ')（RPCUID + RPCBARE + API）"
else
  MAIN_OK=1
  say "   Go   sha256 = $(shasum -a 256 "${TMP}/go-main.txt" | awk '{print $1}')"
  say "   Java sha256 = $(shasum -a 256 "${TMP}/java-main.txt" | awk '{print $1}')"
  say ""
  say "❌ 双侧不一致，diff（- = Go / + = Java）："
  cat "${TMP}/diff.txt"
fi

# ═══════════════════════════════════════════════════════════════════════════
# 7/8 OBS 段（已知不可达差异，两侧本就不同）
# ═══════════════════════════════════════════════════════════════════════════
say ""
say "== 7/8 OBS 段（已知不可达差异，两侧本就不同）=="
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
    say "   OBS|${name}: Go=${g} Java=${j}  （已知不可达差异，登记在 docs/DRIFT/task/）"
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
