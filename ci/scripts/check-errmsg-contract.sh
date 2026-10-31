#!/usr/bin/env bash
# =============================================================================
# ci/scripts/check-errmsg-contract.sh —— R5 错误码契约守门（**基准 = Go 真源派生**）
#
# 【守什么】
#   每个域的 Java 侧错误码契约（码值 / 文案 / 未知码回落 / 逐调用点的码与文案）
#   与 `ci/contracts/errmsg/<domain>.txt` 的 **Go 权威段**（`# >>> go-authority >>>`）**双向一致**。
#
# 【基准的方向（2026-10-31 纠正）】
#   ⚠️ 旧版基准存的是 **Java 侧探针的 dump** ⇒ 权威内容其实是「Java 当前现状」
#      ⇒ 方向反了（拿 Java 现状定义标准，再让 CI 检查 Java 是否符合它）。
#   ⇒ 现在基准的权威段**从 Go 真源机械提取**（`ci/scripts/extract-errmsg-authority.py`），
#     Java 侧 dump 是**被测对象**，不是基准。
#   ⇒ Go 仓**不需要**在线、Go 工具链**不需要**进 CI 镜像（本门**绝不**拉 Go 仓、
#     **绝不**装 Go 工具链、**绝不**跑 Go 半边探针）。
#
# 【比对方式（双向）】
#   ① Go 有、Java 没有的码   ⇒ **FAIL**（Java 缺实现）
#   ② Java 有、Go 没有的码   ⇒ **FAIL**（Java 擅自新增错误码）
#   ③ 同一码文案不同         ⇒ **FAIL**，除非该差异在 `approved`/`closed`/`technical` 名单里
#   ④ 回落规则不同           ⇒ **FAIL**，除非在名单里
#   ⑤ `pending`（已登记未签字）⇒ **不判 FAIL**，但**逐条显式报出**（文件名 + 键 + 两侧值），
#      且**该域不得计入 PASS**
#
# 【差异台账（`# >>> deviations >>>` 段）】
#   DEVIATION|<段>|<键>|<go值>|<java值>|<status>|<依据id>|<依据出处>|<理由>
#   status ∈ approved | closed | technical | pending
#     · approved  ⇒ 依据 id 必能在 `docs/Intentional_Deviations/` 的**被引文件**里查到
#     · closed    ⇒ 依据 id 必能在 `docs/DRIFT/` 的**被引文件**里查到
#     · technical ⇒ 依据出处必含 **Go 侧 `文件:行号`**（不可达 / 结构性差异的证据）
#     · pending   ⇒ 未经批准、需用户拍板
#   ⚠️ **自证豁免的检查**：`approved` 的依据 id 查不到 ⇒ **FAIL**（不许自行豁免）。
#
# 【退出码（四态 · 必须分得开）】
#   0 = PASS     Java 侧与 **Go 基准**一致（或差异全部已签字/已销号/技术判定）
#   1 = FAIL     检查执行了，发现 Java 侧与 Go 基准**真漂移**
#   2 = BLOCKED  环境/依赖不满足，**检查没做成** ⇒ 基准缺失 / 基准格式损坏 /
#                Maven 编译失败 / 探针跑不起来 / dump 形态非法
#   3 = PENDING  存在**已登记未签字**（`pending`）的差异 ⇒ **不计入 PASS**，须显式报出
#   ⚠️ **环境问题绝不能报成 FAIL**：否则「CI 环境没准备好」会被伪装成「契约漂移」
#      ⇒ 维护者会去查代码，而真因在环境（AGENTS.md §3.3「绿测试可能是错误答案的守卫」）。
#   ⚠️ **BLOCKED / PENDING 一律不得计入 PASS**（ci/contracts/README.md 第四节）。
#
# 【优先级】
#   FAIL(1) > BLOCKED(2) > PENDING(3) > PASS(0)
#   ⇒ 有真漂移先报漂移；没有漂移但检查没做成 ⇒ 报 BLOCKED；都干净但有未签字差异 ⇒ 报 PENDING。
#
# 【用法】
#   bash ci/scripts/check-errmsg-contract.sh              # 全部 10 个域（9 个域 + task 的两层）
#   bash ci/scripts/check-errmsg-contract.sh like users   # 只跑指定域（CI 按域拆 task 用）
#
# 【环境变量】
#   RAG_REPO_ROOT           仓根（默认 = 本脚本上两级）
#   ERRSMSG_DOMAINS         目标域列表（默认全部；也可直接用命令行参数）
#   ERRSMSG_CONTRACTS_DIR   基准目录（默认 ${RAG_REPO_ROOT}/ci/contracts/errmsg）
#                           ⚠️ **仅供变异测试/离线复验**在副本上跑 ⇒ CI **不得**设置
#   ERRSMSG_PROBE_DIR       探针目录（默认 ${RAG_REPO_ROOT}/tools/errmsg-probe）
#                           ⚠️ **仅供变异测试**注入坏探针 ⇒ CI **不得**设置
#   ERRSMSG_REPLAY_DUMP_DIR 离线复验：直接读 <dir>/<label>.dump 当作 Java 侧产物
#                           （**不跑探针**）⇒ ⚠️ **仅供变异测试/离线复验** ⇒ CI **不得**设置
#   ERRSMSG_DUMP_OUT_DIR    把 Java 侧 dump 落盘到 <dir>/<label>.dump
#                           ⇒ ⚠️ **仅供变异测试/离线复算** ⇒ CI **不得**设置
#   ERRSMSG_MVN_OFFLINE     0=**默认**（Maven 可联网解析）；1=强制离线（`mvn -o`）
#   ERRSMSG_SKIP_COMPILE    1=跳过 mvn compile（**仅**用于本地调试已有 target/ 的场景；
#                           CI 里**不得**设置 —— 跳过编译就等于不验「产物是不是新的」）
#   ERRSMSG_PHASE           本脚本要跑哪个阶段（默认 `full`）：
#                             full    = 编译 + 跑探针 + 比对（本地开发 / 变异测试用）
#                             dump    = 编译 + 跑探针，**只产出 Java 侧 dump**，不比对
#                             compare = **只比对**（从 ERRSMSG_REPLAY_DUMP_DIR 读 dump）
#
# 【为什么要拆成两个阶段（2026-10-31 · R5 在 Concourse Task 内首跑实测）】
#   跑探针需要 **JDK + Maven**（探针 import 的是各域 **编译产物**里的 ErrorCode
#   ⇒ 不是源码解析 ⇒ 必须真编译），而比对逻辑（解析两段式基准、反转义、
#   四态分类、回指 docs/Intentional_Deviations/）写的是 **python3**。
#   ⚠️ **CI 的 maven 镜像里没有 python3**（实测 `maven:3.9.11-eclipse-temurin-21`
#      = Ubuntu 24.04，`command -v python3` ⇒ MISSING）
#   ⇒ 单阶段跑 ⇒ `python3: command not found` ⇒ 每个域都被记成 BLOCKED。
#   ⇒ CI 因此拆成**两个内层容器**（见 ci/scripts/dind-run.sh 的 STAGE2）：
#       内层① maven 镜像  ⇒ PHASE=dump    ⇒ 产出 Java 侧 dump
#       内层② python 镜像 ⇒ PHASE=compare ⇒ 比对
#   ⇒ 两者共用同一个 bind mount ⇒ dump 在**同一次 build 内**传递，不落进仓内。
#   ⚠️ 「CI 不得设置 DUMP_OUT_DIR / REPLAY_DUMP_DIR」那条规则的**本意**是
#      「不许拿**上一次**的 dump 当本次产物」⇒ 跨阶段交接是**同一次 build 内**的，
#      dump 目录每次 build 重建 ⇒ 不违反该规则的本意（脚本里对此有显式断言）。
# =============================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="${RAG_REPO_ROOT:-$(cd "${SCRIPT_DIR}/../.." && pwd)}"
CONTRACTS_DIR="${ERRSMSG_CONTRACTS_DIR:-${REPO_ROOT}/ci/contracts/errmsg}"
PROBE_DIR="${ERRSMSG_PROBE_DIR:-${REPO_ROOT}/tools/errmsg-probe}"
REPLAY_DIR="${ERRSMSG_REPLAY_DUMP_DIR:-}"
DUMP_OUT_DIR="${ERRSMSG_DUMP_OUT_DIR:-}"
POM="${REPO_ROOT}/pom.xml"

ALL_DOMAINS="article comment favorite follow hot like message users task"
TARGETS=("$@")
if [ "${#TARGETS[@]}" -eq 0 ]; then
  # shellcheck disable=SC2206
  TARGETS=(${ERRSMSG_DOMAINS:-$ALL_DOMAINS})
fi

MVN_OFFLINE="${ERRSMSG_MVN_OFFLINE:-0}"
SKIP_COMPILE="${ERRSMSG_SKIP_COMPILE:-0}"
PHASE="${ERRSMSG_PHASE:-full}"
case "${PHASE}" in
  full|dump|compare) ;;
  *)
    warn "✗ 未知 ERRSMSG_PHASE='${PHASE}'（合法值：full / dump / compare）"
    exit 2
    ;;
esac
# ⚠️ compare 阶段**必须**有 dump 来源；dump 阶段**必须**有 dump 落盘目录。
#   ⇒ 缺了就立刻 BLOCKED ⇒ 绝不静默地「没比对就当通过」。
if [ "${PHASE}" = "compare" ] && [ -z "${REPLAY_DIR}" ]; then
  warn "✗ PHASE=compare 必须设置 ERRSMSG_REPLAY_DUMP_DIR（Java 侧 dump 的来源）"
  exit 2
fi
if [ "${PHASE}" = "dump" ] && [ -z "${DUMP_OUT_DIR}" ]; then
  warn "✗ PHASE=dump 必须设置 ERRSMSG_DUMP_OUT_DIR（Java 侧 dump 的落盘位置）"
  exit 2
fi

WORK="$(mktemp -d)"
trap 'rm -rf "${WORK}"' EXIT

# ⚠️ 全部消息走 stdout（不是 stderr）：本脚本是**守门**，结论由退出码承载；
#    走 stdout 才能保证「日志里的先后顺序 == 事件先后顺序」，便于 CI 复盘。
say()  { printf '%s\n' "$*"; }
warn() { printf '%s\n' "$*"; }

# ── 汇总计数 ────────────────────────────────────────────────────────────────
PASS_N=0; FAIL_N=0; BLOCKED_N=0; PENDING_N=0
APPROVED_N=0; CLOSED_N=0; TECHNICAL_N=0; PENDING_DEV_N=0
FAILED_DOMAINS=""; BLOCKED_DOMAINS=""; PENDING_DOMAINS=""

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
# ⇒ 这里在运行时**从既有的 run-parity.sh 里读**（只读），
#    用例表一旦变化 ⇒ dump 变化 ⇒ 与基准不符 ⇒ 契约变更必须重新裁定。
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

# =============================================================================
# 比对核心（Go 权威段 ⇄ Java 侧 dump · 双向）
# =============================================================================
# 用法：compare_domain <基准文件> <Java 侧 dump> <标签>
#   ⇒ 退出码 0=PASS / 1=FAIL / 2=BLOCKED / 3=PENDING
#   ⇒ 末行打印 `RESULT|<verdict>|<approved>|<closed>|<technical>|<pending>`
# =============================================================================
compare_domain() {
  python3 - "$1" "$2" "$3" "${REPO_ROOT}" <<'PYEOF'
# -*- coding: utf-8 -*-
import os
import re
import sys

BASELINE, DUMP, LABEL, REPO = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
AUTH_MARK = "# >>> go-authority >>>"
DEV_MARK = "# >>> deviations >>>"
VALID_STATUS = ("approved", "closed", "technical", "pending")
_UNESC = {"25": "%", "7C": "|", "5C": "\\", "0A": "\n", "0D": "\r", "09": "\t"}
GO_SITE_RE = re.compile(r"[A-Za-z0-9_./-]+\.go:\d+")


def unesc(s):
    out, i = [], 0
    while i < len(s):
        if s[i] == "%" and s[i + 1:i + 3] in _UNESC:
            out.append(_UNESC[s[i + 1:i + 3]])
            i += 3
        else:
            out.append(s[i])
            i += 1
    return "".join(out)


def out(msg):
    print(msg)


def blocked(reason):
    out("   ✗ BLOCKED：%s" % reason)
    out("RESULT|BLOCKED|0|0|0|0")
    sys.exit(2)


def parse_sections(path):
    """⇒ {'go-authority': [...], 'deviations': [...]}（段缺失 ⇒ 该 key 不存在）"""
    secs = {}
    cur = None
    with open(path, encoding="utf-8") as f:
        for line in f.read().splitlines():
            s = line.strip()
            if s.startswith("# >>>") and s.endswith(">>>"):
                cur = s[5:-4].strip()
                secs.setdefault(cur, [])
                continue
            if cur is None:
                continue
            if s and not s.startswith("#"):
                secs[cur].append(s)
    return secs


# ── 基准解析 ────────────────────────────────────────────────────────────────
if not os.path.isfile(BASELINE):
    blocked("基准文件缺失：%s" % BASELINE)
secs = parse_sections(BASELINE)
if "go-authority" not in secs or not secs["go-authority"]:
    blocked("基准里没有 '%s' 段（或该段为空）⇒ 基准格式损坏：%s" % (AUTH_MARK, BASELINE))

go_const, go_table, go_calls = {}, {}, []
go_fallback = None
for ln in secs["go-authority"]:
    p = ln.split("|")
    tag = p[0]
    if tag == "GOCONST" and len(p) >= 2:
        go_const[p[1]] = p[2] if len(p) > 2 else ""
    elif tag == "GOTABLE" and len(p) >= 3:
        go_table[p[1]] = "|".join(p[2:])
    elif tag == "GOFALLBACK" and len(p) >= 2:
        go_fallback = "|".join(p[1:])
    elif tag == "GOCALL" and len(p) >= 6:
        go_calls.append((p[1], p[2], p[3], p[4], "|".join(p[5:])))
    else:
        blocked("基准的 go-authority 段有无法识别的行：%s" % ln)
if go_fallback is None and not go_calls:
    blocked("基准的 go-authority 段没有 GOFALLBACK ⇒ Go 回落规则缺失")
if not go_const and not go_calls:
    blocked("基准的 go-authority 段既没有 GOCONST 也没有 GOCALL ⇒ 权威值缺失")

# ── 差异台账解析 ────────────────────────────────────────────────────────────
registry = {}
for ln in secs.get("deviations", []):
    p = ln.split("|")
    if len(p) != 9 or p[0] != "DEVIATION":
        blocked("基准的 deviations 段有无法识别的行（应为 9 段 DEVIATION）：%s" % ln)
    seg, key, gv, jv, status, bid, src, reason = p[1], p[2], p[3], p[4], p[5], p[6], p[7], p[8]
    if status not in VALID_STATUS:
        blocked("差异台账的 status 非法：%s（允许 %s）" % (status, "/".join(VALID_STATUS)))
    k = (seg, key)
    if k in registry:
        blocked("差异台账有重复条目：%s|%s" % (seg, key))
    registry[k] = {"go": unesc(gv), "java": unesc(jv), "status": status,
                   "id": unesc(bid), "src": unesc(src), "reason": unesc(reason)}

# ── Java 侧 dump 解析 ──────────────────────────────────────────────────────
if not os.path.isfile(DUMP):
    blocked("Java 侧 dump 不存在：%s" % DUMP)
facts, j_fallback_rows = {}, {}
with open(DUMP, encoding="utf-8") as f:
    for line in f.read().splitlines():
        if not line.strip():
            continue
        p = line.split("|")
        seg = p[0]
        if seg == "FALLBACK" and len(p) >= 3:
            j_fallback_rows[p[1]] = "|".join(p[2:])
            continue
        if len(p) < 2:
            continue
        facts[(seg, p[1])] = "|".join(p[2:])

j_const = {k[1] for k in facts if k[0] == "CONST"}
j_table = {k[1]: v for k, v in facts.items() if k[0] == "TABLE"}

# ═════════════════════════════════════════════════════════════════════════════
# ① Go 权威 ⇄ Java 侧 的双向差异
# ═════════════════════════════════════════════════════════════════════════════
diffs = []   # (段, 键, go值, java值)
if go_const:
    def go_eff(code):
        """Go 侧对该码的**有效文案**（在 codeMsg 表里 ⇒ 表里的文案；否则 ⇒ 回落文案）"""
        return go_table.get(code, go_fallback)

    for code in sorted(go_const, key=lambda c: (len(c), c)):
        go_msg = go_eff(code)
        if code not in j_const:
            diffs.append(("CONST", code, go_msg, "<absent>"))
            continue
        if code not in j_table:
            # Java 有这个码但**没有文案条目** ⇒ Java 查表会回落到自己的回落文案
            diffs.append(("TABLE", code, go_msg, j_fallback_rows.get(code, "<no-entry>")))
        elif j_table[code] != go_msg:
            diffs.append(("TABLE", code, go_msg, j_table[code]))
    for code in sorted(j_const):
        if code not in go_const:
            diffs.append(("CONST", code, "<absent>", j_table.get(code, "<absent>")))
    for code in sorted(j_table):
        if code not in go_table and code not in go_const:
            diffs.append(("TABLE", code, "<absent>", j_table[code]))
    # 回落规则：只针对「两侧都未登记」的码（探针的 case 码恰好已登记 ⇒ 由 TABLE 比较覆盖）
    for code in sorted(j_fallback_rows):
        if code in j_table or code in go_table or code in go_const:
            continue
        if j_fallback_rows[code] != go_fallback:
            diffs.append(("FALLBACK", code, go_fallback, j_fallback_rows[code]))
else:
    # ── task 域：Go 侧没有码表 ⇒ 权威 = 逐调用点（GOCALL）────────────────────
    groups = {}
    for site, segment, prefix, value, msg in go_calls:
        groups.setdefault((segment, prefix), []).append((site, value, msg))
    for (segment, prefix), entries in sorted(groups.items()):
        rows = sorted([(k, v) for (s, k), v in facts.items()
                       if s == segment and k.startswith(prefix)])
        if not rows:
            diffs.append((segment, prefix + "*", entries[0][1], "<no-observation>"))
            continue
        for key, val in rows:
            code, _, rest = val.partition("|")
            hit = False
            for _site, value, msg in entries:
                if code != value:
                    continue
                if msg != "*" and msg not in rest:
                    continue
                hit = True
                break
            if not hit:
                want = " / ".join("%s=%s" % (v, m) for _s, v, m in entries)
                diffs.append((segment, key, want, "%s|%s" % (code, rest)))
        # 反向：Go 调用点里非通配的文案，Java 侧至少要出现一次
        for _site, _value, msg in entries:
            if msg == "*":
                continue
            seen = any(msg in v.partition("|")[2]
                       for (s, k), v in facts.items()
                       if s == segment and k.startswith(prefix))
            if not seen:
                diffs.append((segment, prefix + "*", msg, "<absent>"))

# ═════════════════════════════════════════════════════════════════════════════
# ② 差异台账的自洽性 + 依据可查性
# ═════════════════════════════════════════════════════════════════════════════
problems = []      # ⇒ FAIL
stale = []         # ⇒ 仅提示（差异已消失 ⇒ 台账该清理，不是失败）
pending = []       # ⇒ 显式报出 + 不计入 PASS
by_status = {s: [] for s in VALID_STATUS}
for (seg, key), e in sorted(registry.items()):
    by_status[e["status"]].append((seg, key, e))

for (seg, key), e in sorted(registry.items()):
    # ②a 依据可查性
    src = e["src"]
    md_paths = re.findall(r"[A-Za-z0-9_./-]+\.md(?::\d+)?", src)
    md_path = md_paths[0] if md_paths else None
    if md_path:
        md_file = md_path.split(":")[0]
        full = os.path.join(REPO, md_file)
        if not os.path.isfile(full):
            problems.append("REGISTRY|MISSING_LEDGER|%s|%s|依据出处指向的文件不存在：%s"
                            % (seg, key, md_file))
        else:
            with open(full, encoding="utf-8", errors="replace") as f:
                body = f.read()
            if e["id"] not in ("-", "") and e["id"] not in body:
                if e["status"] == "approved":
                    problems.append(
                        "REGISTRY|BAD_APPROVAL|%s|%s|依据 id `%s` 在 %s 里查不到"
                        " ⇒ 不得自行豁免（approved 必须有**已签字**条目）"
                        % (seg, key, e["id"], md_file))
                elif e["status"] == "closed":
                    problems.append(
                        "REGISTRY|BAD_CLOSURE|%s|%s|依据 id `%s` 在 %s 里查不到"
                        " ⇒ closed 必须回指**已销号**条目" % (seg, key, e["id"], md_file))
                else:
                    out("   ⚠️ 台账提示：%s|%s 的依据 id `%s` 在 %s 里查不到"
                        % (seg, key, e["id"], md_file))
            if e["status"] == "approved" and not md_file.startswith("docs/Intentional_Deviations/"):
                problems.append(
                    "REGISTRY|BAD_APPROVAL|%s|%s|approved 的依据必须指向 docs/Intentional_Deviations/"
                    "（当前 %s）" % (seg, key, md_file))
            if e["status"] == "closed" and not md_file.startswith("docs/DRIFT/"):
                problems.append(
                    "REGISTRY|BAD_CLOSURE|%s|%s|closed 的依据必须指向 docs/DRIFT/"
                    "（当前 %s）" % (seg, key, md_file))
    if e["status"] == "technical" and not GO_SITE_RE.search(src):
        problems.append(
            "REGISTRY|BAD_TECHNICAL|%s|%s|technical 的依据出处必须给出 Go 侧 `文件:行号`"
            "（当前 %s）" % (seg, key, src))

    # ②b 台账里记的 Java 侧值必须与**当前** Java 侧一致（否则 Java 漂移了）
    cur = facts.get((seg, key))
    if cur is None:
        stale.append("STALE|台账条目 %s|%s 在当前 Java 侧 dump 里**已不存在**" % (seg, key))
    elif cur != e["java"]:
        problems.append("DRIFT|JAVA_VALUE_CHANGED|%s|%s|台账记的 java 值 `%s` ≠ 当前 Java 侧 `%s`"
                        % (seg, key, e["java"], cur))

# ②c 每一条**实测差异**都必须在台账里落笔
covered = set()
for seg, key, gv, jv in diffs:
    e = registry.get((seg, key))
    if e is None:
        problems.append("DRIFT|UNREGISTERED|%s|%s|Go=%s / Java=%s ⇒ 差异**未在台账落笔**"
                        % (seg, key, gv, jv))
        continue
    covered.add((seg, key))
    if e["go"] != gv or e["java"] != jv:
        problems.append(
            "DRIFT|REGISTRY_MISMATCH|%s|%s|台账记 Go=%s/Java=%s ⇒ 实测 Go=%s/Java=%s"
            % (seg, key, e["go"], e["java"], gv, jv))
    if e["status"] == "pending":
        pending.append((seg, key, gv, jv, e))

# ②d 台账里 Go 侧的取值必须与 Go 权威段一致（不许台账改写 Go 的事实）
for (seg, key), e in sorted(registry.items()):
    if seg == "TABLE" and key in go_const:
        want = go_table.get(key, go_fallback)
        if e["go"] != want:
            problems.append(
                "DRIFT|REGISTRY_GO_MISMATCH|%s|%s|台账记 Go=%s ⇒ Go 权威段是 %s"
                % (seg, key, e["go"], want))
    if (seg, key) not in covered and seg in ("CONST", "TABLE", "FALLBACK"):
        stale.append("STALE|台账条目 %s|%s 的差异已**不存在**（Go 与 Java 已一致）"
                     % (seg, key))

# ═════════════════════════════════════════════════════════════════════════════
# ③ 判定
# ═════════════════════════════════════════════════════════════════════════════
for st in VALID_STATUS:
    for seg, key, e in by_status[st]:
        out("   📋 %-9s %s|%s  依据=%s  出处=%s"
            % (st, seg, key, e["id"], e["src"]))
for s in stale:
    out("   ⚠️ %s" % s)
for p in problems:
    out("   ❌ %s" % p)
for seg, key, gv, jv, e in pending:
    out("   ⏳ PENDING  文件=%s  段=%s  键=%s  Go=%s  Java=%s  依据=%s（%s）"
        % (os.path.basename(BASELINE), seg, key, gv, jv, e["id"], e["src"]))

n_appr = len(by_status["approved"])
n_closed = len(by_status["closed"])
n_tech = len(by_status["technical"])
n_pend = len(by_status["pending"])
if problems:
    verdict = "FAIL"
elif n_pend:
    verdict = "PENDING"
else:
    verdict = "PASS"
out("   ⇒ 域判定: %s（差异台账 approved=%d closed=%d technical=%d pending=%d）"
    % (verdict, n_appr, n_closed, n_tech, n_pend))
out("RESULT|%s|%d|%d|%d|%d" % (verdict, n_appr, n_closed, n_tech, n_pend))
sys.exit({"PASS": 0, "FAIL": 1, "BLOCKED": 2, "PENDING": 3}[verdict])
PYEOF
}

# ── 取基准（缺失 ⇒ BLOCKED）─────────────────────────────────────────────────
baseline_for() {
  local name="$1"
  [ -f "${CONTRACTS_DIR}/${name}" ] || return 1
  printf '%s' "${CONTRACTS_DIR}/${name}"
}

# ── 单探针域的完整流程 ────────────────────────────────────────────────────
run_single_probe_domain() {
  local d="$1"
  local dwork="${WORK}/${d}"
  mkdir -p "${dwork}/java"

  # ① 基准必须在
  local baseline
  if ! baseline="$(baseline_for "${BASELINE}")"; then
    warn "   ✗ 基准文件缺失：${CONTRACTS_DIR}/${BASELINE}"
    warn "     ⇒ BLOCKED（不是 FAIL：契约基准没固化 ⇒ 这道门没做成，不是代码漂移）"
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

  # ③ 编译（compare 阶段不需要：它只比对上一阶段产出的 dump）
  local classes="" cp=""
  if [ "${PHASE}" != "compare" ]; then
    local mlog="${dwork}/mvn.log"
    if ! compile_module "${MODULES}" "${mlog}"; then
      warn "   ✗ mvn compile 失败（${MODULES}）"
      tail -25 "${mlog}" | sed 's/^/   | /'
      warn "     ⇒ BLOCKED（构建失败是环境/代码状态问题，不是契约漂移）"
      return 2
    fi
    classes="${REPO_ROOT}/${MODULES}/target/classes"
    if [ ! -d "${classes}" ]; then
      warn "   ✗ 编译产物目录不存在：${classes}"
      warn "     ⇒ BLOCKED"
      return 2
    fi

    # ④ classpath
    cp="${classes}"
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
  fi

  # ⑤ 产出/取用 Java 侧 dump
  local actual="${dwork}/actual.txt"
  if [ "${PHASE}" = "compare" ]; then
    local rp_dump="${REPLAY_DIR}/${d}.dump"
    if [ ! -s "${rp_dump}" ]; then
      warn "   ✗ compare 阶段：找不到 Java 侧 dump ${rp_dump}"
      warn "     ⇒ BLOCKED（上一阶段没产出 ⇒ 不是契约漂移）"
      return 2
    fi
    cp "${rp_dump}" "${actual}"
  elif [ -n "${REPLAY_DIR}" ]; then
    local rp_dump="${REPLAY_DIR}/${d}.dump"
    if [ ! -s "${rp_dump}" ]; then
      warn "   ✗ 离线复验模式：找不到 dump 文件 ${rp_dump}"
      warn "     ⇒ BLOCKED"
      return 2
    fi
    cp "${rp_dump}" "${actual}"
    warn "   ⚠️ 离线复验模式（ERRSMSG_REPLAY_DUMP_DIR）⇒ **没有跑探针** ⇒ 仅供变异测试/离线复算"
  else
    if ! javac -nowarn -encoding UTF-8 -cp "${cp}" -d "${dwork}/java" \
         "${PROBE_DIR}/${PROBE_SRC}" >"${dwork}/javac.log" 2>&1; then
      warn "   ✗ javac 失败（探针源码：${PROBE_SRC}）"
      tail -25 "${dwork}/javac.log" | sed 's/^/   | /'
      warn "     ⇒ BLOCKED"
      return 2
    fi
    if ! java -cp "${dwork}/java:${cp}" "${PROBE_CLASS}" "${cases}" \
         >"${actual}" 2>"${dwork}/java.err"; then
      warn "   ✗ 探针执行失败（${PROBE_CLASS}）"
      tail -25 "${dwork}/java.err" | sed 's/^/   | /'
      warn "     ⇒ BLOCKED"
      return 2
    fi
  fi
  if ! validate_dump "${actual}" "Java 侧"; then
    warn "     ⇒ BLOCKED"
    return 2
  fi

  # ── dump 阶段到此为止：只产出 Java 侧 dump，比对交给下一个内层阶段 ──────────
  if [ "${PHASE}" = "dump" ]; then
    mkdir -p "${DUMP_OUT_DIR}"
    cp "${actual}" "${DUMP_OUT_DIR}/${d}.dump"
    say "   ✔ Java 侧 dump 已产出（比对在下一内层阶段执行）"
    return 0
  fi

  # 落盘（供变异测试 / 离线复算复用）⇒ CI **不得**设置
  if [ -n "${DUMP_OUT_DIR}" ]; then
    mkdir -p "${DUMP_OUT_DIR}"
    cp "${actual}" "${DUMP_OUT_DIR}/${d}.dump"
  fi

  # ⑥ 与 Go 权威段双向比对
  compare_domain "${baseline}" "${actual}" "${d}"
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

  local actual_rpc="${dwork}/actual-rpc.txt" actual_api="${dwork}/actual-api.txt"
  if [ -n "${DUMP_OUT_DIR}" ]; then
    mkdir -p "${DUMP_OUT_DIR}"
  fi
  # ── compare 阶段：只取上一阶段产出的 dump（不编译、不跑探针）──────────────
  if [ "${PHASE}" = "compare" ]; then
    [ -s "${REPLAY_DIR}/task-rpc.dump" ] || { warn "   ✗ compare 阶段：缺 task-rpc.dump ⇒ BLOCKED"; return 2; }
    [ -s "${REPLAY_DIR}/task-api.dump" ] || { warn "   ✗ compare 阶段：缺 task-api.dump ⇒ BLOCKED"; return 2; }
    cp "${REPLAY_DIR}/task-rpc.dump" "${actual_rpc}"
    cp "${REPLAY_DIR}/task-api.dump" "${actual_api}"
  else
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

    if [ -n "${REPLAY_DIR}" ]; then
      [ -s "${REPLAY_DIR}/task-rpc.dump" ] || { warn "   ✗ 离线复验：缺 task-rpc.dump ⇒ BLOCKED"; return 2; }
      [ -s "${REPLAY_DIR}/task-api.dump" ] || { warn "   ✗ 离线复验：缺 task-api.dump ⇒ BLOCKED"; return 2; }
      cp "${REPLAY_DIR}/task-rpc.dump" "${actual_rpc}"
      cp "${REPLAY_DIR}/task-api.dump" "${actual_api}"
      warn "   ⚠️ 离线复验模式（ERRSMSG_REPLAY_DUMP_DIR）⇒ **没有跑探针** ⇒ 仅供变异测试/离线复算"
    else
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
    fi
  fi
  validate_dump "${actual_rpc}" "Java 侧（task-rpc）" || { warn "     ⇒ BLOCKED"; return 2; }
  validate_dump "${actual_api}" "Java 侧（task-api）" || { warn "     ⇒ BLOCKED"; return 2; }

  # ── dump 阶段到此为止 ──────────────────────────────────────────────────────
  if [ "${PHASE}" = "dump" ]; then
    mkdir -p "${DUMP_OUT_DIR}"
    cp "${actual_rpc}" "${DUMP_OUT_DIR}/task-rpc.dump"
    cp "${actual_api}" "${DUMP_OUT_DIR}/task-api.dump"
    say "   ✔ Java 侧 dump 已产出（比对在下一内层阶段执行）"
    return 0
  fi

  if [ -n "${DUMP_OUT_DIR}" ]; then
    cp "${actual_rpc}" "${DUMP_OUT_DIR}/task-rpc.dump"
    cp "${actual_api}" "${DUMP_OUT_DIR}/task-api.dump"
  fi

  local r1=0 r2=0
  compare_domain "${base_rpc}" "${actual_rpc}" "task-rpc"; r1=$?
  compare_domain "${base_api}" "${actual_api}" "task-api"; r2=$?
  # FAIL(1) > BLOCKED(2) > PENDING(3)
  if [ "${r1}" = "1" ] || [ "${r2}" = "1" ]; then return 1; fi
  if [ "${r1}" = "2" ] || [ "${r2}" = "2" ]; then return 2; fi
  if [ "${r1}" = "3" ] || [ "${r2}" = "3" ]; then return 3; fi
  return 0
}

# ── 单域结果记账 ────────────────────────────────────────────────────────────
# ⚠️ task 域会产出**两行** RESULT（task-rpc + task-api）⇒ 这里按行**汇总**，
#    域级判定取最严重的一个（FAIL > BLOCKED > PENDING > PASS）。
record() {
  local d="$1" rc="$2" out="$3"
  local agg verdict approved closed technical pending
  agg="$(printf '%s\n' "${out}" | awk -F'|' '
    $1=="RESULT" {
      v=$2; ap+=$3; cl+=$4; te+=$5; pe+=$6;
      if (v=="FAIL") f=1; else if (v=="BLOCKED") b=1; else if (v=="PENDING") p=1;
      n++
    }
    END {
      verdict = (n==0) ? "NONE" : (f ? "FAIL" : (b ? "BLOCKED" : (p ? "PENDING" : "PASS")));
      printf "%s %d %d %d %d\n", verdict, ap, cl, te, pe;
    }')"
  read -r verdict approved closed technical pending <<<"${agg}"
  if [ "${verdict}" = "NONE" ]; then
    # 没走到比对那一步 ⇒ 按函数的退出码归类（2=BLOCKED / 1=FAIL / 3=PENDING / 0=PASS）
    case "${rc}" in
      0) verdict="PASS" ;; 1) verdict="FAIL" ;; 3) verdict="PENDING" ;; *) verdict="BLOCKED" ;;
    esac
  fi
  APPROVED_N=$((APPROVED_N + ${approved:-0}))
  CLOSED_N=$((CLOSED_N + ${closed:-0}))
  TECHNICAL_N=$((TECHNICAL_N + ${technical:-0}))
  PENDING_DEV_N=$((PENDING_DEV_N + ${pending:-0}))
  case "${verdict}" in
    PASS)    PASS_N=$((PASS_N + 1)) ;;
    FAIL)    FAIL_N=$((FAIL_N + 1));   FAILED_DOMAINS="${FAILED_DOMAINS} ${d}" ;;
    BLOCKED) BLOCKED_N=$((BLOCKED_N + 1)); BLOCKED_DOMAINS="${BLOCKED_DOMAINS} ${d}" ;;
    PENDING) PENDING_N=$((PENDING_N + 1)); PENDING_DOMAINS="${PENDING_DOMAINS} ${d}" ;;
    *)       BLOCKED_N=$((BLOCKED_N + 1)); BLOCKED_DOMAINS="${BLOCKED_DOMAINS} ${d}" ;;
  esac
}

# =============================================================================
# 主流程
# =============================================================================
say "[ci] ============================================================"
say "[ci] 阶段：错误码契约守门（R5 · 基准 = **Go 真源派生**）"
say "[ci] 基准目录: ${CONTRACTS_DIR}"
say "[ci] 目标域  : ${TARGETS[*]}"
say "[ci] 本次阶段: ${PHASE}"
say "[ci] ============================================================"

# ── dump 阶段专用计数（**不进入** PASS/FAIL 账：比对在下一阶段才发生）──────
DUMP_OK_N=0
DUMP_FAILED_DOMAINS=""

for d in "${TARGETS[@]}"; do
  say ""
  say "===== 域: ${d} ====="
  if ! spec_for "${d}"; then
    warn "   ✗ 未知域：${d}（已知域：${ALL_DOMAINS}）"
    if [ "${PHASE}" = "dump" ]; then
      DUMP_FAILED_DOMAINS="${DUMP_FAILED_DOMAINS} ${d}"
    else
      BLOCKED_N=$((BLOCKED_N + 1))
      BLOCKED_DOMAINS="${BLOCKED_DOMAINS} ${d}"
    fi
    continue
  fi
  if [ "${IS_TASK}" = "1" ]; then
    out="$(run_task_domain "${d}" 2>&1)"; rc=$?
  else
    out="$(run_single_probe_domain "${d}" 2>&1)"; rc=$?
  fi
  printf '%s\n' "${out}"
  if [ "${PHASE}" = "dump" ]; then
    # ⚠️ dump 阶段**不记账**：此时还没比对 ⇒ 无从判 PASS/FAIL。
    #   「没比对就当通过」正是任务书禁止的（缺少检查不许伪装成 PASS）。
    if [ "${rc}" -eq 0 ]; then
      DUMP_OK_N=$((DUMP_OK_N + 1))
    else
      DUMP_FAILED_DOMAINS="${DUMP_FAILED_DOMAINS} ${d}"
    fi
    continue
  fi
  record "${d}" "${rc}" "${out}"
done

if [ "${PHASE}" = "dump" ]; then
  say ""
  say "[ci] ============================================================"
  say "[ci] R5 · dump 阶段汇总（**尚未比对** ⇒ 本阶段不产生 PASS/FAIL 结论）"
  say "[ci]   已产出 dump 的域 : ${DUMP_OK_N}"
  say "[ci]   未产出 dump 的域 : ${DUMP_FAILED_DOMAINS:-（无）}"
  say "[ci]   dump 目录        : ${DUMP_OUT_DIR}"
  say "[ci] ============================================================"
  if [ -n "${DUMP_FAILED_DOMAINS}" ]; then
    say "[ci] ❌ 有域没产出 Java 侧 dump ⇒ 下一阶段无法比对 ⇒ BLOCKED（不得计入 PASS）。"
    exit 2
  fi
  say "[ci] ✅ 全部域的 Java 侧 dump 已产出 ⇒ 交给下一内层阶段比对。"
  exit 0
fi

say ""
say "[ci] ============================================================"
say "[ci] R5 错误码契约守门汇总（基准 = Go 真源派生）"
say "[ci]   PASS    : ${PASS_N}"
say "[ci]   FAIL    : ${FAIL_N}${FAILED_DOMAINS:+  (域:${FAILED_DOMAINS})}"
say "[ci]   BLOCKED : ${BLOCKED_N}${BLOCKED_DOMAINS:+  (域:${BLOCKED_DOMAINS})}"
say "[ci]   PENDING : ${PENDING_N}${PENDING_DOMAINS:+  (域:${PENDING_DOMAINS})}"
say "[ci]   ── 差异台账分列（Go↔Java 逐条裁定）──"
say "[ci]   approved : ${APPROVED_N}"
say "[ci]   closed   : ${CLOSED_N}"
say "[ci]   technical: ${TECHNICAL_N}"
say "[ci]   pending  : ${PENDING_DEV_N}   ⬅ 单列（已登记未签字 ⇒ 需用户拍板）"
say "[ci] ============================================================"

if [ "${FAIL_N}" -ne 0 ]; then
  say "[ci] ❌ 契约漂移：Java 侧与 **Go 真源基准**不一致。"
  exit 1
fi
if [ "${BLOCKED_N}" -ne 0 ]; then
  say "[ci] ⚠️  BLOCKED：检查**没做成**（环境/依赖/基准问题）⇒ 不得计入 PASS。"
  exit 2
fi
if [ "${PENDING_N}" -ne 0 ]; then
  say "[ci] ⏳ PENDING：存在**已登记未签字**的差异（pending）⇒ **不计入 PASS** ⇒ 须用户拍板。"
  exit 3
fi
say "[ci] ✅ 错误码契约与 Go 真源基准一致（差异全部已签字/已销号/技术判定）。"
exit 0
