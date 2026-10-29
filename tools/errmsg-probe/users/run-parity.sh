#!/usr/bin/env bash
#
# users 域错误码契约：Go ↔ Java 双侧逐字节对拍（AGENTS.md §3.3 要求的「两侧同时跑的探针」）。
#
# 真源：Sea-RideTheWind_golang/service/user/common/errmsg/errmsg.go
#       ⚠️ 注意是 **user** 不是 users —— Go 侧 users 域 = service/user/（user 与 admin 共用这份 errmsg）。
# 被测：RAG/service/users/users-common/src/main/java/com/rag/users/common/errmsg/ErrorCode.java
#       ⚠️ 该目录下**只有这一个类** ⇒ RAG 侧**没有独立的文案表类**：枚举本身就是那张表
#          （每个常量同时带 code 与 msg），查表入口 = ErrorCode.getMsg(int)。
#
# 定位是【回归守门】：任何人改了 users 域的码值 / 文案 / 未知码回落语义，
# 本脚本立刻 exit 1。
#
# ── Go 出处 + 语义等价论证（AGENTS.md §3.2 要求的留痕）────────────────────────
#   · 码表      ：Go service/user/common/errmsg/errmsg.go:3-36（const 块，**31** 个码）
#                 ↔ RAG ErrorCode.java:7-38（枚举，**31** 个码）
#                 语义等价论证：两侧码集合逐条相等（CONST 层，升序）；差异若有 ⇒ 对拍红。
#   · 文案表    ：Go errmsg.go:38-68（var codeMsg，**29** 条）
#                 ↔ RAG ErrorCode.java:7-38（**枚举自带 msg** ⇒ 31 条）
#                 语义等价论证：TABLE 层按**码集合**（31 个码）逐条比对中文文案（UTF-8 字节级）。
#                 ⚠️ 两侧条数差 2（Go 的 1017 / 1018 没登记文案）⇒ 那两行必然不同 ⇒ 白名单逐条豁免。
#   · 未知码回落：Go errmsg.go:70-76（GetErrMsg：未登记 ⇒ codeMsg[Error] = "FAIL"）
#                 ↔ RAG ErrorCode.java:88-95（of(code)：未登记 ⇒ 返回 ERROR ⇒ getMsg ⇒ "FAIL"）
#                 语义等价论证：FALLBACK 层用「跨域撞车码」逐条比对**码值 + 文案**两样。
#
# 用法：
#   bash tools/errmsg-probe/users/run-parity.sh
#   GO_REPO=/path/to/Sea-RideTheWind_golang bash tools/errmsg-probe/users/run-parity.sh
#
# 依赖：mvn（离线 -o）、JDK 21+、Go toolchain（≥1.25）。
# ⚠️ **不需要 Go module cache**：真源 errmsg.go **零 import** ⇒ 临时 Go 模块零外部依赖
#    ⇒ 不存在「module cache 间歇性不可读」的影响面（脚本仍会打印 GOMODCACHE 以便复核）。
#
# 退出码：0 = 双侧逐字节一致；1 = **真漂移**（对拍红了）；2 = 环境/契约问题（真源缺失、
#         豁免失效……）—— 两者必须分得开，否则守门者分不清「守门失效」和「环境没准备好」。
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
# ⚠️ 为什么要搜索而不是「RAG_ROOT/..」：
#   主工作区是 <root>/RAG，真源在 <root>/Sea-RideTheWind_golang（深 1 层）；
#   而 worktree 是 <root>/RAG.worktrees/<域>（深 2 层）⇒ 固定层数在两种布局下会失效。
default_go_repo() {
  local d
  d="$(cd "${RAG_ROOT}" && pwd)"
  for _ in 1 2 3 4; do
    if [ -f "${d}/Sea-RideTheWind_golang/service/user/common/errmsg/errmsg.go" ]; then
      printf '%s' "${d}/Sea-RideTheWind_golang"
      return 0
    fi
    d="$(dirname "${d}")"
  done
  return 1
}

GO_REPO="${GO_REPO:-$(default_go_repo || true)}"
[ -n "${GO_REPO}" ] || die "找不到 Go 真源（向上 4 层内没有 Sea-RideTheWind_golang）—— 用 GO_REPO=... 指定"
GO_SRC="service/user/common/errmsg/errmsg.go"
MODULE="service/users/users-common"

# OBS 段：两侧**必然不同**的语义差异（case 名必须与两侧探针完全一致）。
# ⚠️ 本域**没有** OBS 条目 —— 理由见 README §3：users 域的 errmsg 契约只有 3 层
#    （const / codeMsg / GetErrMsg），**没有** gRPC 错误载体类（真源也不 import grpc）
#    ⇒ 不存在「两侧行为必然不同、但语义等价」的不可达差异；
#    唯一的已知差异（1017 / 1018 的文案）属**已登记的 primitive 级差异**
#    ⇒ 走**白名单**（逐条豁免 + 逐条理由），而不是 OBS 段。
OBS_CASES=""
OBS_GO_SHA=""
OBS_JAVA_SHA=""

TMP="$(mktemp -d)"
trap 'rm -rf "${TMP}"' EXIT

[ -f "${GO_REPO}/${GO_SRC}" ] || die "找不到 Go 真源：${GO_REPO}/${GO_SRC}（用 GO_REPO=... 指定）"
[ -f "${GO_REPO}/go.mod" ] || die "找不到 Go 真源 go.mod：${GO_REPO}/go.mod"

# ── 未登记码清单（回落语义的观察点）──────────────────────────────────────────
# ⚠️ 这份清单是**跨域撞车**的高价值样本：这些码值在别的域（like / comment / favorite /
#    follow / message / article / hot）是真实存在的业务码，但在 users 域**没有登记**
#    ⇒ 两侧都必须回落到 "FAIL"（Go errmsg.go:70-76 的 `if !ok { return codeMsg[Error] }`）。
# ⚠️ users 域**自己**登记了 1012 / 1016~1018 / 3001~3003 ⇒ 不能拿它们当回落样本，
#    否则测的是「已登记码」而不是「回落语义」。
# ⚠️ 边界值 2147483647 / -2147483648：Go 的 int 是 64 位、Java 的 int 是 32 位
#    ⇒ 只取两侧都放得下的值（int32 边界）。
CASES="${TMP}/cases.txt"
cat >"${CASES}" <<'EOF'
0
-1
1
999
1013
2001
2002
5011
5012
5101
5102
5103
5104
5105
5201
5202
5203
5204
5205
5207
5301
5304
2147483647
-2147483648
EOF

# ── 白名单（逐条豁免，每条带类别 + 理由 + 出处）──────────────────────────────
# ⚠️ 白名单是**假绿源** ⇒ 每一条都必须能审：
#   ① 类别写清楚是「已签字偏离」还是「已登记（已销号）的差异」；
#   ② 理由写清楚**为什么两侧必然不同**；
#   ③ 出处写清楚差异登记在哪个文件；
#   ④ 脚本会**断言豁免项的差异仍然存在**——差异消失 ⇒ exit 2 ⇒ 逼人更新台账。
WHITELIST="${TMP}/whitelist.txt"
cat >"${WHITELIST}" <<'EOF'
TABLE|1017|DRIFT-REGISTERED|DRIFT-USERSCOMMON-001（2026-10-27 已销号·证伪关闭）Go 真源的 const 块声明了 ErrorRequestParam=1017，但 codeMsg 表里**没有登记**它 ⇒ GetErrMsg 回落到 "FAIL"；RAG 侧的枚举给每个码都带了文案（"请求参数错误"）。⇒ 两侧 1017/1018 在 wire 上都**不可观察**（Go 侧是零调用点的死码、RAG 侧仅日志 / HTTP 入口不可达）⇒ 该差异已被判为「不成立」并销号。出处：Go service/user/common/errmsg/errmsg.go:30（常量）+ :38-68（表里没有）+ :70-76（回落）；RAG service/users/users-common/src/main/java/com/rag/users/common/errmsg/ErrorCode.java:34；销号依据 docs/DRIFT/users/common.md 的 DRIFT-USERSCOMMON-001。
TABLE|1018|DRIFT-REGISTERED|DRIFT-USERSCOMMON-001（2026-10-27 已销号·证伪关闭）同上：Go 真源声明了 ErrorAvatarHistoryNotExist=1018 但 codeMsg 表里**没有登记** ⇒ GetErrMsg 回落到 "FAIL"；RAG 侧枚举带文案（"头像历史不存在"）。⇒ 两侧均不可观察 ⇒ 差异不成立（已销号）。出处：Go service/user/common/errmsg/errmsg.go:31（常量）+ :38-68（表里没有）+ :70-76（回落）；RAG service/users/users-common/src/main/java/com/rag/users/common/errmsg/ErrorCode.java:35；销号依据 docs/DRIFT/users/common.md 的 DRIFT-USERSCOMMON-001。
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

# ⚠️ 本域真源**零 import** ⇒ 临时模块零外部依赖 ⇒ 不需要 module cache。
#    （仍打印 GOMODCACHE 便于复核；与 like 版不同，这里没有「缓存不可读 ⇒ exit 2」这条判据，
#      因为**根本用不到**缓存 —— 不该给一个不存在的风险装一个假探针。）
say "   真源零 import ⇒ 临时 Go 模块零外部依赖 ⇒ 无需 module cache（GOMODCACHE=$(go env GOMODCACHE 2>/dev/null || echo '?')）"

cat >"${TMP}/src/go.mod" <<'EOF'
module usererrmsgprobe

go 1.25
EOF

say "== 2/7 跑 Go 侧（真源 errmsg.go 同包编译 · 零外部依赖）=="
( cd "${TMP}/src" && GOPROXY=off GOFLAGS=-mod=mod GOSUMDB=off GOTOOLCHAIN=local go run . "${CASES}" ) \
  >"${TMP}/go.txt" 2>"${TMP}/go.err" \
  || die "Go 侧执行失败（exit $?）—— 见上方输出"
if [ -s "${TMP}/go.err" ]; then
  say "   Go 侧提示（stderr）："
  sed 's/^/   | /' "${TMP}/go.err"
fi

say "== 3/7 编译 users-common =="
mvn -o -q -f "${RAG_ROOT}/pom.xml" -pl "${MODULE}" -am compile
CLASSES="${RAG_ROOT}/${MODULE}/target/classes"
[ -d "${CLASSES}" ] || die "${CLASSES} 不存在"

# ⚠️ 探针只 import 本模块自己的类 ⇒ classpath 上不需要 grpc 之类的外部库；
#    但仍用 dependency:build-classpath 取该模块的**真实**依赖集（不手工 find ~/.m2），
#    这样「探针跑在哪个依赖集上」是可复算的。
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

say "== 4/7 跑 Java 侧（真实 ErrorCode 枚举 = 码表 + 文案表）=="
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
  say "   行数 = $(wc -l <"${TMP}/go-main.txt" | tr -d ' ')（CONST + TABLE + FALLBACK）"
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
if [ -z "${OBS_CASES}" ]; then
  say "   （本域无 OBS 条目：users 域的 errmsg 契约只有 const / codeMsg / GetErrMsg 三层，"
  say "     没有 gRPC 错误载体类 ⇒ 无「两侧行为必然不同、语义却等价」的不可达差异；"
  say "     唯一的已知差异（1017 / 1018 的文案）走白名单 ⇒ 见第 6 步）"
else
  OBS_OK=0
  GO_OBS_NAMES="$(awk -F'\t' '$1 ~ /^OBS\|/ { split($1, p, "|"); print p[2] }' "${TMP}/go.kv" | sort | tr '\n' ' ' | sed 's/ $//')"
  JAVA_OBS_NAMES="$(awk -F'\t' '$1 ~ /^OBS\|/ { split($1, p, "|"); print p[2] }' "${TMP}/java.kv" | sort | tr '\n' ' ' | sed 's/ $//')"
  EXPECT_OBS_NAMES="$(printf '%s\n' ${OBS_CASES} | sort | tr '\n' ' ' | sed 's/ $//')"
  if [ "${GO_OBS_NAMES}" != "${EXPECT_OBS_NAMES}" ]; then
    say "   ❌ Go 侧 OBS case 集合与脚本声明不符（${GO_OBS_NAMES} != ${EXPECT_OBS_NAMES}）"
    OBS_OK=2
  fi
  if [ "${JAVA_OBS_NAMES}" != "${EXPECT_OBS_NAMES}" ]; then
    say "   ❌ Java 侧 OBS case 集合与脚本声明不符（${JAVA_OBS_NAMES} != ${EXPECT_OBS_NAMES}）"
    OBS_OK=2
  fi
  while read -r name; do
    [ -n "${name}" ] || continue
    g="$(awk -F'\t' -v k="OBS|${name}" '$1 == k {print $2; exit}' "${TMP}/go.kv")"
    j="$(awk -F'\t' -v k="OBS|${name}" '$1 == k {print $2; exit}' "${TMP}/java.kv")"
    if [ "${g}" = "${j}" ]; then
      say "   ⚠️  OBS|${name}: Go=${g} Java=${j} —— **差异已消失**，请重新评估并更新台账"
      OBS_OK=2
    else
      say "   OBS|${name}: Go=${g} Java=${j}  （两侧必然不同的语义差异）"
    fi
  done <<EOF
$(printf '%s\n' ${OBS_CASES})
EOF
  GO_OBS_SHA="$(awk -F'\t' '$1 ~ /^OBS\|/ { print $1 "|" $2 }' "${TMP}/go.kv" | shasum -a 256 | awk '{print $1}')"
  JAVA_OBS_SHA="$(awk -F'\t' '$1 ~ /^OBS\|/ { print $1 "|" $2 }' "${TMP}/java.kv" | shasum -a 256 | awk '{print $1}')"
  say "   Go   OBS sha256 = ${GO_OBS_SHA}（期望 ${OBS_GO_SHA}）"
  say "   Java OBS sha256 = ${JAVA_OBS_SHA}（期望 ${OBS_JAVA_SHA}）"
  if [ "${GO_OBS_SHA}" != "${OBS_GO_SHA}" ] || [ "${JAVA_OBS_SHA}" != "${OBS_JAVA_SHA}" ]; then
    say "   ❌ OBS 段的取值**变形了**（不是消失）⇒ 请重新评估并更新台账 / 本脚本里的冻结 sha256"
    OBS_OK=2
  fi
fi

say ""
if [ "${MAIN_OK}" = "0" ] && [ "${WL_OK}" = "0" ] && { [ -z "${OBS_CASES}" ] || [ "${OBS_OK}" = "0" ]; }; then
  say "✅ 双侧逐字节一致（sha256 相同，diff 无输出）；白名单 ${WL_COUNT} 条豁免项差异仍在；本域无 OBS 条目（理由见 README §3）"
  exit 0
fi
[ "${MAIN_OK}" = "0" ] || exit 1
exit 2
