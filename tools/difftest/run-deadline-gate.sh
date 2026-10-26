#!/usr/bin/env bash
# 🔴 **客户端 deadline 档探针的编排入口**（E48 · 2026-10-26 第三十九轮）
#
# ═══ 为什么单独一个 wrapper ═══
#   1. **它把「接线」从 run.sh 里抽出来** ⇒ 守门可以用**假探针**（`EXP_PROBE_STUB`）
#      验证「探针红了 ⇒ 入口红了 ⇒ run.sh 会红」—— 不需要起整套栈。
#   2. **它只做两件事**：把 run.sh 算好的端口/目录/容器名前缀传给探针 + **透传退出码**。
#      任何判定逻辑都不在这里（判定在 tools/difftest/verdict-grpc-admin-deadline.py）。
#
# ═══ 退出码语义 ═══
#   0 = PASS（未复现差异）· 1 = FAIL（复现了语义不一致）· 2 = INCONCLUSIVE（观测不可靠）
#   3 = 栈未就绪 · 4 = **实验前提未成立**（锁没拿到 ⇒ 观测无效）
#     —— 后两者是**探针自身的前置失败**，不进判定器，但对 run.sh 来说同样判红。
#
# 用法（run.sh 内部调用；也可手工跑）：
#   GO_ADMIN_PORT=18081 RAG_ADMIN_PORT=58081 RUN_DIR=/tmp/difftest \
#   DIFTEST_CONTAINER_PREFIX=difftest ./run-deadline-gate.sh
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ── 参数：端口 / 目录 / 容器名前缀（与 run.sh 的默认值保持一致）──
GO_ADMIN_PORT="${GO_ADMIN_PORT:-18081}"
RAG_ADMIN_PORT="${RAG_ADMIN_PORT:-58081}"
RUN_DIR="${RUN_DIR:-/tmp/difftest}"
DIFTEST_CONTAINER_PREFIX="${DIFTEST_CONTAINER_PREFIX:-difftest}"

# ── 探针本体：EXP_PROBE_STUB 供守门做「假探针」变异（只验接线，不起栈）──
PROBE="${EXP_PROBE_STUB:-$HERE/exp-grpc-admin-deadline-probe.sh}"

# 🔴 **闸门自己的输出留档**（第四十四轮）：第三步判据要读探针的输出 ⇒ 必须落盘
GATE_OUT="${RUN_DIR:-/tmp/difftest}/deadline-gate.out"
mkdir -p "$(dirname "$GATE_OUT")" 2>/dev/null || true

export GO_ADMIN_PORT RAG_ADMIN_PORT RUN_DIR DIFTEST_CONTAINER_PREFIX

echo "==> [deadline-gate] 客户端 deadline 档探针（E38① 的运行期回归网）"
echo "    Go  admin-rpc : $GO_ADMIN_PORT"
echo "    RAG admin-rpc : $RAG_ADMIN_PORT"
echo "    RUN_DIR       : $RUN_DIR"
echo "    容器名前缀    : $DIFTEST_CONTAINER_PREFIX"
echo "    探针          : $PROBE"
echo

"$PROBE" --stacks-up 2>&1 | tee "$GATE_OUT"
RC=${PIPESTATUS[0]}

# 🔴🔴 **第三步判据**（第四十四轮 · users/admin 域 owner 建议 · 出处：AGENTS.md §3.3 2026-10-21 补
#    「判断有没有真生效不能看启动日志，唯一可靠的判据是运行时证据」）：
#    **「退出码 0」不等于「探针真跑过」** —— 脚本什么都没干就退 0 也是 0 ⇒ **假闸门**
#    ⇒ 只有当探针**声称 PASS（退出码 0）**时，才要求它**交出观测证据**（两个 Δ 值）
#    ⇒ 探针非 0 时**不**要求（它已经红了 ⇒ 不该用证据去掩盖它的失败码）
if [[ $RC -eq 0 ]]; then
  n_start=$(grep -c 'Δ_start=' "$GATE_OUT" 2>/dev/null || true)
  n_end=$(grep -c 'Δ_end=' "$GATE_OUT" 2>/dev/null || true)
  if [[ "${n_start:-0}" -lt 1 || "${n_end:-0}" -lt 1 ]]; then
    echo "  ❌ 探针退出码 0，但输出里没有 Δ_start / Δ_end ⇒ **它没真跑** ⇒ 假闸门"
    RC=5
  else
    echo "  ✓ 探针真的跑过并交出了观测证据（Δ_start × ${n_start} · Δ_end × ${n_end}）"
  fi
fi

echo
echo "==> [deadline-gate] 探针退出码 = ${RC}（0=PASS / 1=FAIL / 2=INCONCLUSIVE / 3=栈未就绪 / 4=前提未成立 / 5=没真跑·假闸门）"
exit $RC
