#!/usr/bin/env bash
#
# like 域错误码契约：Go ↔ Java 双侧逐字节对拍（AGENTS.md §3.3 要求的「两侧同时跑的探针」）。
#
# 真源：Sea-RideTheWind_golang/service/like/common/errmsg/errmsg.go
# 被测：RAG/service/like/like-common/src/main/java/.../errmsg/
#       {LikeErrorCode,LikeErrorMessages,LikeGrpcError,CodeError}.java
#
# 定位是【回归守门】：任何人改了 like 的码值 / 文案 / 未知码回落语义 / 业务码载体语义，
# 本脚本立刻 exit 1。
#
# ── Go 出处 + 语义等价论证（AGENTS.md §3.2 要求的留痕）────────────────────────
#   · 码表      ：Go service/like/common/errmsg/errmsg.go:37-67（const 块，29 个码）
#                 ↔ RAG LikeErrorCode.java:18-97（枚举，29 个码）
#                 语义等价论证：枚举顺序 = Go 常量书写顺序；两者的「码 → 业务含义」由
#                 TABLE 层的 29 条逐字节比对证明，不靠注释。
#   · 文案表    ：Go errmsg.go:69-99（var codeMsg）↔ RAG LikeErrorMessages.java:37-74（static 块）
#                 语义等价论证：TABLE 层逐条比对中文文案（UTF-8 字节级）。
#   · 未知码回落：Go errmsg.go:101-107（GetErrMsg：未登记 ⇒ codeMsg[Error] = "FAIL"）
#                 ↔ RAG LikeErrorMessages.java:82-88（getMsg：未登记 ⇒ MSG_BY_CODE.get(ERROR) = "FAIL"）
#                 语义等价论证：FALLBACK 层用「跨域撞车码」逐条比对**码值 + 文案**两样。
#   · wire 载体 ：Go errmsg.go:33-35（NewGrpcErr：业务码 ⇒ grpc-status 数值）
#                 ↔ RAG LikeGrpcError.java:138-144（grpcError：业务码 ⇒ 自定义 trailer）
#                 ⚠️ **两侧必然不同**（grpc-java 无法构造越界 grpc-status）⇒ 白名单豁免，
#                    依据 = docs/Intentional_Deviations/like/rpc.md 的 SD-2026-10-23-06。
#   · 业务码还原：Go service/like/api/internal/logic/like/likeactionlogic.go:63-68
#                 （st, ok := status.FromError(err) ⇒ int(st.Code())；否则 5001）
#                 ↔ RAG LikeGrpcError.java:170-200（bizCodeFromError）
#                 语义等价论证：BIZCODE 矩阵两条同构路径（本域自己的错误）必须还原出同一业务码；
#                 「下游发来的非本域错误」两侧形态本就不同 ⇒ 白名单豁免（依据 D-7）。
#
# 用法：
#   bash tools/errmsg-probe/like/run-parity.sh
#   GO_REPO=/path/to/Sea-RideTheWind_golang bash tools/errmsg-probe/like/run-parity.sh
#
# 依赖：mvn（离线 -o）、JDK 21+、Go toolchain（≥1.25）、本地 Go module cache（需含 grpc）。
#
# ⚠️ 与 tools/errmsg-probe/favorite 版的差异：
#   1. 真源文件在 service/like/common/errmsg/errmsg.go ⇒ 包名 errmsg（favorite 那份是 common）；
#   2. 多一层 WIRECODE（业务码**放在哪个载体上**）—— favorite 的业务码在 description 里，
#      like 的业务码在 grpc-status / trailer 里 ⇒ 两侧必然不同 ⇒ 走白名单而不是逐字节对拍；
#   3. 白名单 + OBS 两套机制并存：
#      · 白名单 = 「**已知并登记在案**的差异」逐条豁免（每条带类别 + 理由 + 出处），
#        并**断言差异仍然存在**（豁免失效 ⇒ exit 2，逼人更新台账，而不是让它静默漂移）；
#      · OBS    = 「**两侧行为必然不同**」的语义差异（case 名两侧一致 + 值必须仍然不同 + 值被 sha256 钉死）。
#
# 退出码：0 = 双侧逐字节一致；1 = **真漂移**（对拍红了）；2 = 环境/契约问题（真源缺失、
#         module cache 不可读、豁免失效、OBS 段变形……）—— 两者必须分得开，否则守门者分不清
#         「守门失效」和「环境没准备好」。
#
# ⚠️ 两个**仅供 mutate-parity.sh 使用**的环境变量（普通调用不要设）：
#   ERRSMSG_PROBE_CP_OUT=<file>   把本次用的 classpath 写进该文件（变异脚本据此编译影子类）
#   ERRSMSG_PROBE_EXTRA_CP=<dir>  把该目录**放在 classpath 最前面**（影子类覆盖同名类 ⇒ 模拟漂移）
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAG_ROOT="$(cd "${HERE}/../../.." && pwd)"

say() { printf '%s\n' "$*"; }
die() { printf '!! %s\n' "$*" >&2; exit 2; }

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
    if [ -f "${d}/Sea-RideTheWind_golang/service/like/common/errmsg/errmsg.go" ]; then
      printf '%s' "${d}/Sea-RideTheWind_golang"
      return 0
    fi
    d="$(dirname "${d}")"
  done
  return 1
}

GO_REPO="${GO_REPO:-$(default_go_repo || true)}"
[ -n "${GO_REPO}" ] || die "找不到 Go 真源（向上 4 层内没有 Sea-RideTheWind_golang）—— 用 GO_REPO=... 指定"
GO_SRC="service/like/common/errmsg/errmsg.go"
MODULE="service/like/like-common"
PROBE_PKG_DIR="com/rag/like/like/common/errmsg"

# OBS 段：两侧**必然不同**的语义差异（case 名必须与两侧探针完全一致）。
# ⚠️ 这里**只锁 case 名集合 + 两侧各自的 sha256**，不锁具体数值 ——
#    数值由探针从真源现算并打印；若哪天的数值变了 ⇒ sha256 变了 ⇒ exit 2 提示更新台账。
OBS_CASES="obs_custom_grpcstatus obs_nil_error obs_ok_status"
# ⚠️ 冻结 sha256 = 「OBS 段取值」的指纹（键|值，按两侧各自的发射顺序）。
#    它**不是**抄答案：答案由探针现算；这里钉的是**已知偏离的形状** ——
#    差异消失（值变相等）由第 7 步的逐条断言抓到，差异**变形**（值变了但仍不等）
#    由这两个 sha256 抓到 ⇒ 两者都会逼人重新评估并更新台账。
OBS_GO_SHA="32912e876a4a7f0b02afc6536a3e42d5fb730d89c5a15f1b6f7264c1bd47bc3a"
OBS_JAVA_SHA="11b9bbb62707392ffaf84523397bc8df18d90b035b64198eb61dcc9310f071a0"

TMP="$(mktemp -d)"
trap 'rm -rf "${TMP}"' EXIT

[ -f "${GO_REPO}/${GO_SRC}" ] || die "找不到 Go 真源：${GO_REPO}/${GO_SRC}（用 GO_REPO=... 指定）"
[ -f "${GO_REPO}/go.mod" ] || die "找不到 Go 真源 go.mod：${GO_REPO}/go.mod"

# ── 未登记码清单（回落语义的观察点）──────────────────────────────────────────
# ⚠️ 这份清单是**跨域撞车**的高价值样本：这些码值在别的域（users / comment / favorite /
#    follow / message / article / hot）是真实存在的业务码，但在 like 域**没有登记**
#    ⇒ 两侧都必须回落到 "FAIL"（Go errmsg.go:101-107 的 `if !ok { return codeMsg[Error] }`）。
# ⚠️ like 域**自己**的 1011/1012/2001/2002/3001 等已登记 ⇒ 不能拿它们当回落样本，
#    否则测的是「已登记码」而不是「回落语义」。
# ⚠️ 边界值 2147483647 / -2147483648：Go 的 int 是 64 位、Java 的 int 是 32 位
#    ⇒ 只取两侧都放得下的值（int32 边界）。
CASES="${TMP}/cases.txt"
cat >"${CASES}" <<'EOF'
0
-1
1
999
1012
1013
1016
1017
1018
2002
3002
3003
5101
5102
5103
5201
5203
5204
5301
5304
2147483647
-2147483648
EOF

# ── 白名单（逐条豁免，每条带类别 + 理由 + 出处）──────────────────────────────
# ⚠️ 白名单是**假绿源** ⇒ 每一条都必须能审：
#   ① 类别写清楚是「已签字偏离」还是「已登记未拍板的差异」；
#   ② 理由写清楚**为什么两侧必然不同**；
#   ③ 出处写清楚差异登记在哪个文件；
#   ④ 脚本会**断言豁免项的差异仍然存在**——差异消失 ⇒ exit 2 ⇒ 逼人更新台账。
WHITELIST="${TMP}/whitelist.txt"
cat >"${WHITELIST}" <<'EOF'
WIRECODE|*|SIGNED|SD-2026-10-23-06（已签字·框架限制）gRPC 业务码载体迁移：Go 的 codes.Code 是 int32 命名类型、不做范围校验 ⇒ 业务码以数值形式进 grpc-status；grpc-java 的 Status.Code 只有 17 个成员、无法构造越界码 ⇒ RAG 把业务码迁到自定义 trailer rag-like-biz-code。出处：Go service/like/common/errmsg/errmsg.go:33-35；签字档 docs/Intentional_Deviations/like/rpc.md 的 SD-2026-10-23-06。
BIZCODE|foreign_internal|DRIFT-REGISTERED|D-7（已登记·待拍板）下游非本域错误：Go like-api 直接把原始 gRPC 码当业务码（INTERNAL ⇒ 13），RAG 侧 mapGrpcCodeToBizCode 收敛到 1015。出处：docs/DRIFT/like/vs-common.md 的 D-7。
BIZCODE|foreign_notfound|DRIFT-REGISTERED|D-7（已登记·待拍板）下游非本域错误：Go 取原始 gRPC 码（NOT_FOUND ⇒ 5），RAG 侧收敛到 1015。出处：docs/DRIFT/like/vs-common.md 的 D-7。
BIZCODE|foreign_invalid_argument|DRIFT-REGISTERED|D-7（已登记·待拍板）下游非本域错误：Go 取原始 gRPC 码（INVALID_ARGUMENT ⇒ 3），RAG 侧 mapGrpcCodeToBizCode 映到 2001。出处：docs/DRIFT/like/vs-common.md 的 D-7。
BIZCODE|foreign_unauthenticated|DRIFT-REGISTERED|D-7（已登记·待拍板）下游非本域错误：Go 取原始 gRPC 码（UNAUTHENTICATED ⇒ 16），RAG 侧 mapGrpcCodeToBizCode 映到 1009。出处：docs/DRIFT/like/vs-common.md 的 D-7。
BIZCODE|foreign_plain|DRIFT-REGISTERED|D-7（已登记·待拍板）非 status 错误：Go like-api 兜底 errmsg.ErrorServerCommon（5001），RAG 侧 bizCodeFromError 兜底 CODE_SERVER_BUSY（1015）。出处：docs/DRIFT/like/vs-common.md 的 D-7。
EOF

say "== 1/7 校验 Go 真源并搭临时模块 =="
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

# ⚠️ grpc 版本从真源 go.mod **动态读取**（不写死）⇒ 真源升版本时本脚本自动跟随。
GRPC_VER="$(awk '/^[[:space:]]*google\.golang\.org\/grpc v/ {print $2; exit}' "${GO_REPO}/go.mod")"
[ -n "${GRPC_VER}" ] || die "无法从 ${GO_REPO}/go.mod 解析 google.golang.org/grpc 版本"
say "   真源 go.mod 的 grpc 版本 = ${GRPC_VER}"

# ⚠️ 输入根探针（AGENTS.md §3.3 的「先确认输入可读」）：
#    本机 ~/go/pkg/mod 偶尔**间歇性不可读**（ls 与 os.path.isdir 会互相打脸）
#    ⇒ 这里取「本次真正要用的」两个已知文件做硬探针；读不到就 exit 2，
#      **绝不能静默降级成「这条不可核验」**。
GOMODCACHE="$(go env GOMODCACHE 2>/dev/null || true)"
[ -n "${GOMODCACHE}" ] || die "go env GOMODCACHE 为空 —— 无法确认本地 module cache"
probe_cache_file() {
  local f="$1" i
  for i in 1 2 3; do
    if [ -f "$f" ] && head -c 1 "$f" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  return 1
}
CACHE_MOD="${GOMODCACHE}/cache/download/google.golang.org/grpc/@v/${GRPC_VER}.mod"
CACHE_SRC="${GOMODCACHE}/google.golang.org/grpc@${GRPC_VER}/codes/codes.go"
probe_cache_file "${CACHE_MOD}" \
  || die "本地 module cache 不可读（${CACHE_MOD}）—— 先 go mod download google.golang.org/grpc@${GRPC_VER}"
probe_cache_file "${CACHE_SRC}" \
  || die "本地 module cache 不可读（${CACHE_SRC}）—— 先 go mod download google.golang.org/grpc@${GRPC_VER}"
say "   module cache 输入根探针通过（${CACHE_MOD} / ${CACHE_SRC}）"

cat >"${TMP}/src/go.mod" <<EOF
module likeerrmsgprobe

go 1.25

require google.golang.org/grpc ${GRPC_VER}
EOF
# ⚠️ GOPROXY=off + 本地 module cache ⇒ 不联网。go mod tidy 从 cache 解析并生成 go.sum。
#    GOTOOLCHAIN=local 防止 go.mod 的 go 指令触发 toolchain 下载。
( cd "${TMP}/src" && GOPROXY=off GOFLAGS=-mod=mod GOSUMDB=off GOTOOLCHAIN=local go mod tidy ) \
  || die "go mod tidy 失败 —— 本地 module cache 里可能没有 grpc ${GRPC_VER}（需要先 go mod download）"

say "== 2/7 跑 Go 侧（真源 errmsg.go 同包编译 + 真 grpc 库）=="
( cd "${TMP}/src" && GOPROXY=off GOTOOLCHAIN=local go run . "${CASES}" ) >"${TMP}/go.txt" \
  || die "Go 侧执行失败（exit $?）—— 见上方输出"

say "== 3/7 编译 like-common =="
mvn -o -q -f "${RAG_ROOT}/pom.xml" -pl "${MODULE}" -am compile
CLASSES="${RAG_ROOT}/${MODULE}/target/classes"
[ -d "${CLASSES}" ] || die "${CLASSES} 不存在"

# ⚠️ 探针要 import io.grpc.Status / StatusException ⇒ grpc-api 必须在 classpath 上。
#    like-common 自己只引 grpc-stub（传递依赖 grpc-api），target/classes 里**没有** grpc 的 jar
#    ⇒ 这里用 dependency:build-classpath 取该模块的**真实**依赖集，
#    而不是手工 find ~/.m2 里的某个版本（那会让探针跑到与生产不同的 grpc 版本上）。
mvn -o -q -f "${RAG_ROOT}/pom.xml" -pl "${MODULE}" -am \
  dependency:build-classpath -Dmdep.outputFile="${TMP}/cp.txt" -Dmdep.includeScope=test \
  || die "dependency:build-classpath 失败"
[ -s "${TMP}/cp.txt" ] || die "dependency:build-classpath 没产出 classpath"
CP="${CLASSES}:$(cat "${TMP}/cp.txt")"

# ⚠️ 变异测试专用：把本次用的 classpath 落盘，供 mutate-parity.sh 编译影子类。
if [ -n "${ERRSMSG_PROBE_CP_OUT:-}" ]; then
  printf '%s' "${CP}" >"${ERRSMSG_PROBE_CP_OUT}"
fi
# ⚠️ 变异测试专用：把影子类目录放在 classpath **最前面**（覆盖同名类 ⇒ 模拟 RAG 侧漂移）。
if [ -n "${ERRSMSG_PROBE_EXTRA_CP:-}" ]; then
  say "   ⚠️ ERRSMSG_PROBE_EXTRA_CP 已设置 ⇒ classpath 前置 ${ERRSMSG_PROBE_EXTRA_CP}（变异测试用）"
  CP="${ERRSMSG_PROBE_EXTRA_CP}:${CP}"
fi

say "== 4/7 跑 Java 侧（真实 LikeErrorCode / LikeErrorMessages / LikeGrpcError）=="
javac -nowarn -encoding UTF-8 -cp "${CP}" -d "${TMP}/java" "${HERE}/java/Probe.java"
java -cp "${TMP}/java:${CP}" Probe "${CASES}" >"${TMP}/java.txt"

say "== 5/7 逐字节对拍（白名单豁免项除外）=="
# 记录 → 「key<TAB>value」：key = TYPE|ARG，value = 其余部分。
to_kv() {
  awk '{
    i = index($0, "|"); if (i == 0) next;
    type = substr($0, 1, i - 1);
    rest = substr($0, i + 1);
    j = index(rest, "|");
    if (j == 0) { arg = rest; val = ""; } else { arg = substr(rest, 1, j - 1); val = substr(rest, j + 1); }
    print type "|" arg "\t" val;
  }' "$1"
}
to_kv "${TMP}/go.txt" >"${TMP}/go.kv"
to_kv "${TMP}/java.txt" >"${TMP}/java.kv"

# 白名单键（每行一条 TYPE|ARG，ARG 为 * 表示该类型的全部记录）
awk -F'|' 'NF >= 2 { print $1 "|" $2 }' "${WHITELIST}" >"${TMP}/wl.keys"
WL_COUNT="$(grep -c . "${TMP}/wl.keys" || true)"

# 过滤掉被白名单豁免的记录，再逐字节比对剩下的。
filter_out() {
  local src="$1" dst="$2"
  # ⚠️ 白名单键用**文件**传给 awk（不能用 -v 传多行内容：awk 的 -v 不接受换行）。
  awk -v wlfile="${TMP}/wl.keys" '
    BEGIN {
      n = 0;
      while ((getline line < wlfile) > 0) {
        if (line == "") continue;
        n++; a[n] = line;
      }
      close(wlfile);
    }
    {
      i = index($0, "|"); if (i == 0) { print; next }
      type = substr($0, 1, i - 1);
      rest = substr($0, i + 1);
      j = index(rest, "|");
      if (j == 0) { arg = rest; } else { arg = substr(rest, 1, j - 1); }
      # ⚠️ OBS 段由第 7 步单独处理（断言「差异仍然存在」+ sha256 钉死）⇒ 不进主比对流。
      if (type == "OBS") next;
      skip = 0;
      for (k = 1; k <= n; k++) {
        if (a[k] == "") continue;
        p = index(a[k], "|"); if (p == 0) continue;
        wt = substr(a[k], 1, p - 1);
        wa = substr(a[k], p + 1);
        if (wa == "*") { if (wt == type) { skip = 1; break } }
        else if (wt == type && wa == arg) { skip = 1; break }
      }
      if (!skip) print
    }' "$src" >"$dst"
}
filter_out "${TMP}/go.txt" "${TMP}/go-main.txt"
filter_out "${TMP}/java.txt" "${TMP}/java-main.txt"

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
say "== 6/7 白名单自检（豁免项必须**仍然**有差异）=="
# ⚠️ 这一段是「白名单本身要能审」的落点：
#    · 每条豁免必须两侧都命中记录（key 写错 ⇒ exit 2）；
#    · 每条豁免的差异必须**仍然存在**（差异消失 ⇒ 豁免失效 ⇒ exit 2，逼人更新台账）。
WL_OK=0
while IFS= read -r wl_line; do
  [ -n "${wl_line}" ] || continue
  # ⚠️ 白名单行的格式是「TYPE|ARG|CATEGORY|REASON」⇒ 必须**自己**切前两段，
  #    不能用 `IFS='|' read -r key category reason`（那样 key 只会拿到 TYPE，ARG 会掉进 category）。
  wl_type="${wl_line%%|*}"
  wl_rest="${wl_line#*|}"
  wl_arg="${wl_rest%%|*}"
  wl_rest2="${wl_rest#*|}"
  wl_category="${wl_rest2%%|*}"
  wl_reason="${wl_rest2#*|}"
  [ -n "${wl_type}" ] || continue
  matched="$(awk -F'\t' -v t="${wl_type}" -v a="${wl_arg}" '
    { split($1, p, "|"); if (p[1] != t) next; if (a == "*" || p[2] == a) print $1 }' "${TMP}/go.kv")"
  matched_j="$(awk -F'\t' -v t="${wl_type}" -v a="${wl_arg}" '
    { split($1, p, "|"); if (p[1] != t) next; if (a == "*" || p[2] == a) print $1 }' "${TMP}/java.kv")"
  if [ -z "${matched}" ] || [ -z "${matched_j}" ]; then
    say "   ❌ 白名单条目「${wl_type}|${wl_arg}」在某一侧没命中任何记录 ⇒ 疑似 key 写错（或该记录已被改名）"
    WL_OK=2
    continue
  fi
  if [ "${matched}" != "${matched_j}" ]; then
    say "   ❌ 白名单条目「${wl_type}|${wl_arg}」两侧命中的记录集合不同 ⇒ 疑似 key 写错"
    WL_OK=2
    continue
  fi
  stale=0
  while read -r k; do
    [ -n "${k}" ] || continue
    gv="$(awk -F'\t' -v k="${k}" '$1 == k {print $2; exit}' "${TMP}/go.kv")"
    jv="$(awk -F'\t' -v k="${k}" '$1 == k {print $2; exit}' "${TMP}/java.kv")"
    if [ "${gv}" = "${jv}" ]; then
      say "   ⚠️  白名单条目「${k}」：**差异已消失**（Go = Java = ${gv}）⇒ 豁免失效，请重新评估并更新台账"
      stale=1
    fi
  done <<EOF
${matched}
EOF
  if [ "${stale}" = "1" ]; then
    WL_OK=2
  else
    n="$(printf '%s\n' "${matched}" | grep -c . || true)"
    say "   ✅ 白名单「${wl_type}|${wl_arg}」[${wl_category}] 命中 ${n} 条记录，差异仍在 ⇒ ${wl_reason}"
  fi
done <"${WHITELIST}"

say ""
say "== 7/7 OBS 段（两侧必然不同的语义差异）=="
# ⚠️ 断言 1：case 名集合必须与脚本声明的一致（防止有人悄悄删掉一条差异）。
GO_OBS_NAMES="$(awk -F'\t' '$1 ~ /^OBS\|/ { split($1, p, "|"); print p[2] }' "${TMP}/go.kv" | sort | tr '\n' ' ' | sed 's/ $//')"
JAVA_OBS_NAMES="$(awk -F'\t' '$1 ~ /^OBS\|/ { split($1, p, "|"); print p[2] }' "${TMP}/java.kv" | sort | tr '\n' ' ' | sed 's/ $//')"
EXPECT_OBS_NAMES="$(printf '%s\n' ${OBS_CASES} | sort | tr '\n' ' ' | sed 's/ $//')"
OBS_OK=0
if [ "${GO_OBS_NAMES}" != "${EXPECT_OBS_NAMES}" ]; then
  say "   ❌ Go 侧 OBS case 集合与脚本声明不符（${GO_OBS_NAMES} != ${EXPECT_OBS_NAMES}）"
  OBS_OK=2
fi
if [ "${JAVA_OBS_NAMES}" != "${EXPECT_OBS_NAMES}" ]; then
  say "   ❌ Java 侧 OBS case 集合与脚本声明不符（${JAVA_OBS_NAMES} != ${EXPECT_OBS_NAMES}）"
  OBS_OK=2
fi

# ⚠️ 断言 2：每条 OBS 的 Go 值必须**仍然**与 Java 值不同 ——
#    若哪天 Java 侧改成与 Go 一致（差异消失）⇒ 必须重新评估并更新台账 ⇒ exit 2。
while read -r name; do
  [ -n "${name}" ] || continue
  g="$(awk -F'\t' -v k="OBS|${name}" '$1 == k {print $2; exit}' "${TMP}/go.kv")"
  j="$(awk -F'\t' -v k="OBS|${name}" '$1 == k {print $2; exit}' "${TMP}/java.kv")"
  if [ "${g}" = "${j}" ]; then
    say "   ⚠️  OBS|${name}: Go=${g} Java=${j} —— **差异已消失**，请重新评估并更新台账"
    OBS_OK=2
  else
    say "   OBS|${name}: Go=${g} Java=${j}  （两侧必然不同的语义差异，登记在 docs/DRIFT/like/vs-common.md）"
  fi
done <<EOF
$(printf '%s\n' ${OBS_CASES})
EOF

# ⚠️ 断言 3：OBS 段的两侧取值被 sha256 钉死 —— 差异**变形**（不是消失）也要红。
GO_OBS_SHA="$(awk -F'\t' '$1 ~ /^OBS\|/ { print $1 "|" $2 }' "${TMP}/go.kv" | shasum -a 256 | awk '{print $1}')"
JAVA_OBS_SHA="$(awk -F'\t' '$1 ~ /^OBS\|/ { print $1 "|" $2 }' "${TMP}/java.kv" | shasum -a 256 | awk '{print $1}')"
say "   Go   OBS sha256 = ${GO_OBS_SHA}（期望 ${OBS_GO_SHA}）"
say "   Java OBS sha256 = ${JAVA_OBS_SHA}（期望 ${OBS_JAVA_SHA}）"
if [ "${GO_OBS_SHA}" != "${OBS_GO_SHA}" ] || [ "${JAVA_OBS_SHA}" != "${OBS_JAVA_SHA}" ]; then
  say "   ❌ OBS 段的取值**变形了**（不是消失）⇒ 请重新评估并更新台账 / 本脚本里的冻结 sha256"
  OBS_OK=2
fi

say ""
if [ "${MAIN_OK}" = "0" ] && [ "${WL_OK}" = "0" ] && [ "${OBS_OK}" = "0" ]; then
  say "✅ 双侧逐字节一致（sha256 相同，diff 无输出）；白名单 ${WL_COUNT} 条豁免项差异仍在；OBS 段 ${OBS_CASES} 差异仍在登记状态"
  exit 0
fi
[ "${MAIN_OK}" = "0" ] || exit 1
exit 2
