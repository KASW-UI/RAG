#!/usr/bin/env bash
# =============================================================================
# ci/scripts/check-errmsg-contract.sh —— R5 错误码契约守门（**纯 Java 半边**）
#
# 【守什么】
#   每个域的 Java 侧错误码契约（码值 / 文案 / 未知码回落 / wire 载体 / gRPC-HTTP 语义）
#   与 ci/contracts/errmsg/<domain>.txt 里的**基准**逐字节一致。
#
# 【为什么需要基准，而不是直接跟 Go 比】
#   ⚠️ 本门**绝不**拉 Go 仓、**绝不**装 Go 工具链、**绝不**跑 Go 半边探针。
#   Go 真源（Sea-RideTheWind_golang）已冻结且**离线** ⇒ 把它的错误码语义
#   **一次性、离线**提取成机器可读基准固化在 ci/contracts/errmsg/ 下，
#   CI 只跑 **Java 半边**与基准比对 ⇒ Go 仓不需要在线、Go 工具链不需要进镜像。
#   （提取与裁定的过程见各基准文件头的「来源 / 裁定依据 / 已批准差异清单」。）
#
# 【为什么 Java 半边探针可以单独跑】
#   tools/errmsg-probe/<域>/java/Probe.java 只 import **Java 真实产物**
#   （<域>-common 的编译产物），遍历真实枚举、读真实文案表、调真实错误载体，
#   输出规范化 dump ⇒ 它**不依赖 Go**，也不依赖 PG / Redis / Kafka / 任何容器。
#
# 【退出码（三态 · 必须分得开）】
#   0 = PASS     全部目标域的 Java 侧 dump 与基准**逐字节**一致
#   1 = FAIL     检查执行了，发现 Java 侧与基准不一致 ⇒ **契约漂移**
#   2 = BLOCKED  环境/依赖不满足，**检查没做成** ⇒ 基准缺失 / Maven 编译失败 /
#                探针跑不起来 / dump 形态非法 / 基准文件格式损坏
#   ⚠️ **环境问题绝不能报成 FAIL**：否则「CI 环境没准备好」会被伪装成「契约漂移」
#      ⇒ 维护者会去查代码，而真因在环境（AGENTS.md §3.3「绿测试可能是错误答案的守卫」）。
#   ⚠️ **BLOCKED 一律不得计入 PASS**（ci/contracts/README.md 第四节）。
#
# 【FAIL 与 BLOCKED 的优先级】
#   有 FAIL ⇒ 退出 1（真漂移最需要被看见）；否则有 BLOCKED ⇒ 退出 2；
#   全 PASS ⇒ 退出 0。汇总表里两类都会**单列**，不混进「通过」一句话带过。
#
# 【用法】
#   bash ci/scripts/check-errmsg-contract.sh              # 全部 9 个域
#   bash ci/scripts/check-errmsg-contract.sh like users   # 只跑指定域（CI 按域拆 task 用）
#
# 【环境变量】
#   RAG_REPO_ROOT        仓根（默认 = 本脚本上两级）
#   ERRSMSG_DOMAINS      目标域列表（默认全部；也可直接用命令行参数）
#   ERRSMSG_MVN_OFFLINE  0=**默认**（Maven 可联网解析，靠 CI 侧 ~/.m2/settings.xml 的镜像源）；
#                        1=强制离线（`mvn -o`）—— 适合缓存已备好的本地/气隙环境。
#                        ⚠️ CI 侧的 `ci/scripts/dind-run.sh` 当前**只**转发 ERRSMSG_DOMAINS 与
#                           ERRSMSG_SKIP_COMPILE ⇒ 要在 CI 里切到离线，需给 dind-run.sh 的 `-e` 列表
#                           补一行（**不在本脚本写权内**）。
#   ERRSMSG_SKIP_COMPILE 1=跳过 mvn compile（**仅**用于本地调试已有 target/ 的场景；
#                        CI 里**不得**设置 —— 跳过编译就等于不验「产物是不是新的」）
# =============================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="${RAG_REPO_ROOT:-$(cd "${SCRIPT_DIR}/../.." && pwd)}"
CONTRACTS_DIR="${REPO_ROOT}/ci/contracts/errmsg"
PROBE_DIR="${REPO_ROOT}/tools/errmsg-probe"
POM="${REPO_ROOT}/pom.xml"

ALL_DOMAINS="article comment favorite follow hot like message users task"
TARGETS=("$@")
if [ "${#TARGETS[@]}" -eq 0 ]; then
  # shellcheck disable=SC2206
  TARGETS=(${ERRSMSG_DOMAINS:-$ALL_DOMAINS})
fi

MVN_OFFLINE="${ERRSMSG_MVN_OFFLINE:-0}"
SKIP_COMPILE="${ERRSMSG_SKIP_COMPILE:-0}"

WORK="$(mktemp -d)"
trap 'rm -rf "${WORK}"' EXIT

# ⚠️ 全部消息走 stdout（不是 stderr）：本脚本是**守门**，结论由退出码承载；
#    走 stdout 才能保证「日志里的先后顺序 == 事件先后顺序」，便于 CI 复盘。
say()  { printf '%s\n' "$*"; }
warn() { printf '%s\n' "$*"; }

# ── 汇总计数 ────────────────────────────────────────────────────────────────
PASS_N=0; FAIL_N=0; BLOCKED_N=0
FAILED_DOMAINS=""; BLOCKED_DOMAINS=""

# -----------------------------------------------------------------------------
# 域规格表
# -----------------------------------------------------------------------------
# MODULES     : mvn -pl 的模块列表（逗号分隔）
# AM          : 是否需要 -am（reactor 内的 SNAPSHOT 依赖 ⇒ 不带会拿到 ~/.m2 的陈旧 jar）
# PROBE_SRC   : Java 半边探针（相对 tools/errmsg-probe/）
# RUNPARITY   : 用例表来源（相对 tools/errmsg-probe/）—— 只读它的 heredoc 取「观察点」
# PROBE_CLASS : 探针主类（含 package 的全限定名）
# CASE_VARS   : 从 RUNPARITY 里抽取的用例表变量名（空格分隔）
# CP_MODE     : classes = 只用模块 target/classes；classes+deps = 再叠加真实依赖集
# BASELINE    : 基准文件名（相对 ci/contracts/errmsg/）；'|' 分隔多个
# -----------------------------------------------------------------------------
spec_for() {
  local d="$1"
  MODULES=""; AM="no"; PROBE_SRC=""; RUNPARITY=""; PROBE_CLASS=""
  CASE_VARS=""; CP_MODE="classes"; BASELINE=""; IS_TASK=0
  case "$d" in
    article)
      MODULES="service/article/article-common"
      PROBE_SRC="java/Probe.java"; RUNPARITY="run-parity.sh"
      PROBE_CLASS="Probe"; CASE_VARS="CASES"; BASELINE="article.txt" ;;
    hot)
      MODULES="service/hot/hot-common"
      PROBE_SRC="hot/java/Probe.java"; RUNPARITY="hot/run-parity.sh"
      PROBE_CLASS="com.rag.hot.hot.common.errmsg.Probe"; CASE_VARS="CASES"
      BASELINE="hot.txt" ;;
    like)
      MODULES="service/like/like-common"; AM="yes"
      PROBE_SRC="like/java/Probe.java"; RUNPARITY="like/run-parity.sh"
      PROBE_CLASS="Probe"; CASE_VARS="CASES"; CP_MODE="classes+deps"
      BASELINE="like.txt" ;;
    users)
      MODULES="service/users/users-common"; AM="yes"
      PROBE_SRC="users/java/Probe.java"; RUNPARITY="users/run-parity.sh"
      PROBE_CLASS="Probe"; CASE_VARS="CASES"; CP_MODE="classes+deps"
      BASELINE="users.txt" ;;
    favorite)
      MODULES="service/favorite/favorite-common"; AM="yes"
      PROBE_SRC="favorite/java/Probe.java"; RUNPARITY="favorite/run-parity.sh"
      PROBE_CLASS="Probe"; CASE_VARS="CASES"; CP_MODE="classes+deps"
      BASELINE="favorite.txt" ;;
    comment)
      MODULES="service/comment/comment-common"; AM="yes"
      PROBE_SRC="comment/java/Probe.java"; RUNPARITY="comment/run-parity.sh"
      PROBE_CLASS="com.rag.comment.comment.common.errmsg.Probe"; CASE_VARS="CASES"
      CP_MODE="classes+deps"; BASELINE="comment.txt" ;;
    follow)
      MODULES="service/follow/follow-common"; AM="yes"
      PROBE_SRC="follow/java/Probe.java"; RUNPARITY="follow/run-parity.sh"
      PROBE_CLASS="com.rag.follow.follow.common.errmsg.Probe"; CASE_VARS="CASES"
      CP_MODE="classes+deps"; BASELINE="follow.txt" ;;
    message)
      MODULES="service/message/message-common"; AM="yes"
      PROBE_SRC="message/java/Probe.java"; RUNPARITY="message/run-parity.sh"
      PROBE_CLASS="com.rag.message.message.common.errmsg.Probe"; CASE_VARS="CASES"
      CP_MODE="classes+deps"; BASELINE="message.txt" ;;
    task)
      MODULES="service/task/task-rpc,service/task/task-api"; AM="yes"
      PROBE_SRC="task/java/RpcProbe.java,task/java/ApiProbe.java"
      RUNPARITY="task/run-parity.sh"
      PROBE_CLASS="com.rag.task.task.probe.RpcProbe,com.rag.task.task.probe.ApiProbe"
      CASE_VARS="UID_CASES BARE_CASES API_CASES"; CP_MODE="classes+deps"
      BASELINE="task-rpc.txt|task-api.txt"; IS_TASK=1 ;;
    *)
      return 1 ;;
  esac
  return 0
}

# ── 从 run-parity.sh 的 heredoc 里抽「观察点」用例表 ────────────────────────
# ⚠️ 为什么不把用例表复制一份到 ci/ 下：
#    用例表是**探针的观察面**，它和探针源码是同一个单元。
#    复制一份 ⇒ 两份会悄悄错位 ⇒ 基准验的是「另一组观察点」下的 dump。
#    ⇒ 这里在运行时**从既有的 run-parity.sh 里读**（只读），
#      用例表一旦变化 ⇒ dump 变化 ⇒ 与基准不符 ⇒ 契约变更必须重新裁定。
extract_cases() {
  local script="$1" var="$2" out="$3"
  awk -v marker="cat >\"\${${var}}\" <<'EOF'" '
    index($0, marker) > 0 { inblk = 1; next }
    inblk == 1 {
      if ($0 == "EOF") { inblk = 0; next }
      print
    }
  ' "$script" >"$out" 2>/dev/null
  [ -s "$out" ]
}

# ── dump 形态自检：每一行都必须是已知段前缀 ────────────────────────────────
# ⚠️ 目的：日志 / 警告混进 stdout 时立刻判 BLOCKED（环境问题），
#    而不是变成一堆「与基准不一致」（看起来像真漂移）⇒ 假红会掩盖真问题。
KNOWN_PREFIX='^(CONST|TABLE|FALLBACK|CODEERR|CODEERRMSG|WIRE|WIREX|WIRECODE|BIZCODE|OBS|RPCUID|RPCBARE|API)\|'
validate_dump() {
  local f="$1" label="$2" total bad
  total="$(wc -l <"$f" | tr -d ' ')"
  [ "${total}" -gt 0 ] || { warn "   ✗ ${label} dump 是空的 ⇒ 探针没跑到"; return 1; }
  bad="$(grep -vcE "${KNOWN_PREFIX}" "$f" || true)"
  if [ "${bad}" != "0" ]; then
    warn "   ✗ ${label} dump 有 ${bad} 行不是已知段前缀（多半是日志混进了 stdout）："
    grep -nvE "${KNOWN_PREFIX}" "$f" | head -5 | sed 's/^/   | /'
    return 1
  fi
  return 0
}

# ── 取基准的 dump 段（跳过 '#' 注释头）─────────────────────────────────────
DUMP_MARKER='# >>> dump >>>'
extract_baseline_dump() {
  local baseline="$1" out="$2"
  [ -f "$baseline" ] || return 1
  awk -v m="${DUMP_MARKER}" '
    index($0, m) == 1 { indump = 1; next }
    indump == 1 { print }
  ' "$baseline" >"$out"
  [ -s "$out" ]
}

# ── OBS 段的 case 名集合（基线 vs 实际）────────────────────────────────────
obs_case_names() {
  awk -F'|' '$1 == "OBS" { print $2 }' "$1" | sort -u
}

# ── 编译模块（失败 ⇒ BLOCKED，绝不报成 FAIL）──────────────────────────────
compile_module() {
  local modules="$1" logf="$2"
  [ "${SKIP_COMPILE}" = "1" ] && return 0
  local args=()
  [ "${MVN_OFFLINE}" = "1" ] && args+=(-o)
  args+=(-B --no-transfer-progress -f "${POM}" -pl "${modules}")
  [ "${AM}" = "yes" ] && args+=(-am)
  args+=(compile)
  mvn "${args[@]}" >"${logf}" 2>&1
}

# ── 取模块的真实依赖集（探针 import io.grpc 时必需）────────────────────────
# ⚠️ 用 dependency:build-classpath 取**模块的真实依赖集**，
#    而不是手工 find ~/.m2 里的某个版本（那会让探针跑到与生产不同的 grpc 版本上）。
build_dep_classpath() {
  local module="$1" out="$2" logf="$3"
  local args=()
  [ "${MVN_OFFLINE}" = "1" ] && args+=(-o)
  args+=(-B --no-transfer-progress -f "${POM}" -pl "${module}")
  [ "${AM}" = "yes" ] && args+=(-am)
  args+=(dependency:build-classpath "-Dmdep.outputFile=${out}" -Dmdep.includeScope=test)
  mvn "${args[@]}" >"${logf}" 2>&1 || return 1
  [ -s "${out}" ] || return 1
  return 0
}

# ── 单探针域的完整流程 ────────────────────────────────────────────────────
run_single_probe_domain() {
  local d="$1"
  local dwork="${WORK}/${d}"
  mkdir -p "${dwork}/java"

  # ① 基准必须在
  local baseline="${CONTRACTS_DIR}/${BASELINE}"
  if [ ! -f "${baseline}" ]; then
    warn "   ✗ 基准文件缺失：${baseline}"
    warn "     ⇒ BLOCKED（不是 FAIL：契约基准没固化 ⇒ 这道门没做成，不是代码漂移）"
    return 2
  fi
  local base_dump="${dwork}/baseline.dump"
  if ! extract_baseline_dump "${baseline}" "${base_dump}"; then
    warn "   ✗ 基准文件里找不到 dump 段（缺 '${DUMP_MARKER}' 标记或 dump 为空）：${baseline}"
    warn "     ⇒ BLOCKED（基准格式损坏）"
    return 2
  fi

  # ② 观察点用例表（从只读的 run-parity.sh 抽）
  local rp="${PROBE_DIR}/${RUNPARITY}"
  if [ ! -f "${rp}" ]; then
    warn "   ✗ 找不到用例表来源：${rp}"
    warn "     ⇒ BLOCKED"
    return 2
  fi
  local cases="${dwork}/cases.txt"
  if ! extract_cases "${rp}" "${CASE_VARS}" "${cases}"; then
    warn "   ✗ 从 ${rp} 抽不出用例表（变量 ${CASE_VARS}）"
    warn "     ⇒ BLOCKED"
    return 2
  fi

  # ③ 编译
  local mlog="${dwork}/mvn.log"
  if ! compile_module "${MODULES}" "${mlog}"; then
    warn "   ✗ mvn compile 失败（${MODULES}）"
    tail -25 "${mlog}" | sed 's/^/   | /'
    warn "     ⇒ BLOCKED（构建失败是环境/代码状态问题，不是契约漂移）"
    return 2
  fi
  local classes="${REPO_ROOT}/${MODULES}/target/classes"
  if [ ! -d "${classes}" ]; then
    warn "   ✗ 编译产物目录不存在：${classes}"
    warn "     ⇒ BLOCKED"
    return 2
  fi

  # ④ classpath
  local cp="${classes}"
  if [ "${CP_MODE}" = "classes+deps" ]; then
    local cpfile="${dwork}/cp.txt"
    if ! build_dep_classpath "${MODULES}" "${cpfile}" "${dwork}/mvn-cp.log"; then
      warn "   ✗ dependency:build-classpath 失败（${MODULES}）"
      tail -25 "${dwork}/mvn-cp.log" | sed 's/^/   | /'
      warn "     ⇒ BLOCKED"
      return 2
    fi
    cp="${classes}:$(cat "${cpfile}")"
  fi

  # ⑤ 编译并跑探针
  if ! javac -nowarn -encoding UTF-8 -cp "${cp}" -d "${dwork}/java" \
       "${PROBE_DIR}/${PROBE_SRC}" >"${dwork}/javac.log" 2>&1; then
    warn "   ✗ javac 失败（探针源码：${PROBE_SRC}）"
    tail -25 "${dwork}/javac.log" | sed 's/^/   | /'
    warn "     ⇒ BLOCKED"
    return 2
  fi
  local actual="${dwork}/actual.txt"
  if ! java -cp "${dwork}/java:${cp}" "${PROBE_CLASS}" "${cases}" >"${actual}" 2>"${dwork}/java.err"; then
    warn "   ✗ 探针执行失败（${PROBE_CLASS}）"
    tail -25 "${dwork}/java.err" | sed 's/^/   | /'
    warn "     ⇒ BLOCKED"
    return 2
  fi
  if ! validate_dump "${actual}" "Java 侧"; then
    warn "     ⇒ BLOCKED"
    return 2
  fi

  # ⑥ 与基准逐字节比对
  compare_with_baseline "${baseline}" "${base_dump}" "${actual}" "${d}"
}

# ── task 域（两套探针 / 两个模块 / 两个基准）──────────────────────────────
run_task_domain() {
  local d="task"
  local dwork="${WORK}/${d}"
  mkdir -p "${dwork}/java"

  local base_rpc="${CONTRACTS_DIR}/task-rpc.txt"
  local base_api="${CONTRACTS_DIR}/task-api.txt"
  local missing=""
  [ -f "${base_rpc}" ] || missing="${missing} task-rpc.txt"
  [ -f "${base_api}" ] || missing="${missing} task-api.txt"
  if [ -n "${missing}" ]; then
    warn "   ✗ 基准文件缺失：${missing}"
    warn "     ⇒ BLOCKED"
    return 2
  fi

  local rp="${PROBE_DIR}/task/run-parity.sh"
  if [ ! -f "${rp}" ]; then
    warn "   ✗ 找不到用例表来源：${rp}"
    warn "     ⇒ BLOCKED"
    return 2
  fi
  local uid_c="${dwork}/uid-cases.txt" bare_c="${dwork}/bare-cases.txt" api_c="${dwork}/api-cases.txt"
  extract_cases "${rp}" "UID_CASES"  "${uid_c}"  || { warn "   ✗ 抽不出 UID_CASES ⇒ BLOCKED"; return 2; }
  extract_cases "${rp}" "BARE_CASES" "${bare_c}" || { warn "   ✗ 抽不出 BARE_CASES ⇒ BLOCKED"; return 2; }
  extract_cases "${rp}" "API_CASES"  "${api_c}"  || { warn "   ✗ 抽不出 API_CASES ⇒ BLOCKED"; return 2; }

  local mlog="${dwork}/mvn.log"
  if ! compile_module "service/task/task-rpc,service/task/task-api" "${mlog}"; then
    warn "   ✗ mvn compile 失败（task-rpc,task-api）"
    tail -25 "${mlog}" | sed 's/^/   | /'
    warn "     ⇒ BLOCKED"
    return 2
  fi

  local classes_rpc="${REPO_ROOT}/service/task/task-rpc/target/classes"
  local classes_api="${REPO_ROOT}/service/task/task-api/target/classes"
  [ -d "${classes_rpc}" ] || { warn "   ✗ 缺编译产物：${classes_rpc} ⇒ BLOCKED"; return 2; }
  [ -d "${classes_api}" ] || { warn "   ✗ 缺编译产物：${classes_api} ⇒ BLOCKED"; return 2; }

  local cp_rpc_file="${dwork}/cp-rpc.txt" cp_api_file="${dwork}/cp-api.txt"
  if ! build_dep_classpath "service/task/task-rpc" "${cp_rpc_file}" "${dwork}/mvn-cp-rpc.log"; then
    warn "   ✗ dependency:build-classpath（task-rpc）失败 ⇒ BLOCKED"
    tail -20 "${dwork}/mvn-cp-rpc.log" | sed 's/^/   | /'
    return 2
  fi
  if ! build_dep_classpath "service/task/task-api" "${cp_api_file}" "${dwork}/mvn-cp-api.log"; then
    warn "   ✗ dependency:build-classpath（task-api）失败 ⇒ BLOCKED"
    tail -20 "${dwork}/mvn-cp-api.log" | sed 's/^/   | /'
    return 2
  fi
  local cp_rpc="${classes_rpc}:$(cat "${cp_rpc_file}")"
  local cp_api="${classes_api}:$(cat "${cp_api_file}")"

  if ! javac -nowarn -encoding UTF-8 -cp "${cp_rpc}" -d "${dwork}/java" \
       "${PROBE_DIR}/task/java/RpcProbe.java" >"${dwork}/javac-rpc.log" 2>&1; then
    warn "   ✗ javac（RpcProbe）失败"
    tail -25 "${dwork}/javac-rpc.log" | sed 's/^/   | /'
    warn "     ⇒ BLOCKED"
    return 2
  fi
  if ! javac -nowarn -encoding UTF-8 -cp "${cp_api}" -d "${dwork}/java" \
       "${PROBE_DIR}/task/java/ApiProbe.java" >"${dwork}/javac-api.log" 2>&1; then
    warn "   ✗ javac（ApiProbe）失败"
    tail -25 "${dwork}/javac-api.log" | sed 's/^/   | /'
    warn "     ⇒ BLOCKED"
    return 2
  fi

  local actual_rpc="${dwork}/actual-rpc.txt" actual_api="${dwork}/actual-api.txt"
  if ! java -cp "${dwork}/java:${cp_rpc}" com.rag.task.task.probe.RpcProbe \
        "${uid_c}" "${bare_c}" >"${actual_rpc}" 2>"${dwork}/java-rpc.err"; then
    warn "   ✗ 探针执行失败（RpcProbe）"
    tail -25 "${dwork}/java-rpc.err" | sed 's/^/   | /'
    warn "     ⇒ BLOCKED"
    return 2
  fi
  if ! java -cp "${dwork}/java:${cp_api}" com.rag.task.task.probe.ApiProbe \
        "${api_c}" >"${actual_api}" 2>"${dwork}/java-api.err"; then
    warn "   ✗ 探针执行失败（ApiProbe）"
    tail -25 "${dwork}/java-api.err" | sed 's/^/   | /'
    warn "     ⇒ BLOCKED"
    return 2
  fi
  validate_dump "${actual_rpc}" "Java 侧（task-rpc）" || { warn "     ⇒ BLOCKED"; return 2; }
  validate_dump "${actual_api}" "Java 侧（task-api）" || { warn "     ⇒ BLOCKED"; return 2; }

  local r1=0 r2=0
  compare_with_baseline "${base_rpc}" "${dwork}/baseline-rpc.dump" "${actual_rpc}" "task-rpc"; r1=$?
  compare_with_baseline "${base_api}" "${dwork}/baseline-api.dump" "${actual_api}" "task-api"; r2=$?
  # FAIL(1) 优先于 BLOCKED(2)：真漂移最需要被看见
  if [ "${r1}" = "1" ] || [ "${r2}" = "1" ]; then return 1; fi
  if [ "${r1}" = "2" ] || [ "${r2}" = "2" ]; then return 2; fi
  return 0
}

# ── 与基准比对（返回 0=一致 / 1=不一致）───────────────────────────────────
compare_with_baseline() {
  local baseline="$1" base_dump="$2" actual="$3" label="$4"
  extract_baseline_dump "${baseline}" "${base_dump}" || {
    warn "   ✗ 基准 dump 段为空：${baseline} ⇒ BLOCKED"
    return 2
  }

  # OBS 段的 case 名集合必须与基准一致
  # ⚠️ 为什么要单独判：OBS 段是「两侧必然不同的已批准差异」——
  #    它的**形态**（有哪几个 case）是「已批准差异清单」的一部分。
  #    case 名变了 ⇒ 已批准的差异形态变了 ⇒ 必须**重新裁定**，不能静默通过。
  local base_obs="${actual}.baseobs" act_obs="${actual}.actobs"
  obs_case_names "${base_dump}" >"${base_obs}"
  obs_case_names "${actual}"   >"${act_obs}"
  local obs_status=0
  if ! diff -u "${base_obs}" "${act_obs}" >"${actual}.obsdiff" 2>&1; then
    say "   ❌ OBS 段的 case 名集合与基准不一致 ⇒ 已批准的差异**形态**变了 ⇒ 需重新裁定"
    sed 's/^/   | /' "${actual}.obsdiff"
    obs_status=1
  fi

  if diff -u "${base_dump}" "${actual}" >"${actual}.diff" 2>&1; then
    if [ "${obs_status}" = "0" ]; then
      say "   ✅ ${label}：与基准逐字节一致（$(wc -l <"${actual}" | tr -d ' ') 行）"
      return 0
    fi
    return 1
  fi

  say "   ❌ ${label}：与基准不一致（- = 基准 / + = Java 侧实际）"
  sed 's/^/   | /' "${actual}.diff"
  return 1
}

# =============================================================================
# 主流程
# =============================================================================
say "[ci] ============================================================"
say "[ci] 阶段：错误码契约守门（R5 · 纯 Java 半边）"
say "[ci] 基准目录: ${CONTRACTS_DIR}"
say "[ci] 目标域  : ${TARGETS[*]}"
say "[ci] ============================================================"

for d in "${TARGETS[@]}"; do
  say ""
  say "===== 域: ${d} ====="
  if ! spec_for "${d}"; then
    warn "   ✗ 未知域：${d}（已知域：${ALL_DOMAINS}）"
    BLOCKED_N=$((BLOCKED_N + 1))
    BLOCKED_DOMAINS="${BLOCKED_DOMAINS} ${d}"
    continue
  fi
  if [ "${IS_TASK}" = "1" ]; then
    run_task_domain "${d}"; rc=$?
  else
    run_single_probe_domain "${d}"; rc=$?
  fi
  case "${rc}" in
    0) PASS_N=$((PASS_N + 1)) ;;
    1) FAIL_N=$((FAIL_N + 1));   FAILED_DOMAINS="${FAILED_DOMAINS} ${d}" ;;
    *) BLOCKED_N=$((BLOCKED_N + 1)); BLOCKED_DOMAINS="${BLOCKED_DOMAINS} ${d}" ;;
  esac
done

say ""
say "[ci] ============================================================"
say "[ci] R5 错误码契约守门汇总"
say "[ci]   PASS    : ${PASS_N}"
say "[ci]   FAIL    : ${FAIL_N}${FAILED_DOMAINS:+  (域:${FAILED_DOMAINS})}"
say "[ci]   BLOCKED : ${BLOCKED_N}${BLOCKED_DOMAINS:+  (域:${BLOCKED_DOMAINS})}"
say "[ci] ============================================================"

if [ "${FAIL_N}" -ne 0 ]; then
  say "[ci] ❌ 契约漂移：Java 侧与基准不一致。"
  exit 1
fi
if [ "${BLOCKED_N}" -ne 0 ]; then
  say "[ci] ⚠️  BLOCKED：检查**没做成**（环境/依赖/基准问题）⇒ 不得计入 PASS。"
  exit 2
fi
say "[ci] ✅ 错误码契约与基准逐字节一致。"
exit 0
