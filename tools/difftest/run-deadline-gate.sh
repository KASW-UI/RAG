#!/usr/bin/env bash
# 🔴 **客户端 deadline 档探针的编排入口**（E48 · 2026-10-26 第三十九轮）
#
# ═══ 为什么单独一个 wrapper ═══
#   1. **它把「接线」从 run.sh 里抽出来** ⇒ 守门可以用**假探针**（`EXP_PROBE_STUB`）
#      验证「探针红了 ⇒ 入口红了 ⇒ run.sh 会红」—— 不需要起整套栈。
#   2. **它只做两件事**：把 run.sh 算好的端口/目录/容器名前缀传给探针 + **透传退出码**。
#      任何判定逻辑都不在这里（判定在 tools/difftest/verdict-grpc-admin-deadline.py）。
#
# ═══ 退出码语义（与判定器一致）═══
#   0 = PASS（未复现差异）· 1 = FAIL（复现了语义不一致）· 2 = INCONCLUSIVE（环境不可靠）
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

export GO_ADMIN_PORT RAG_ADMIN_PORT RUN_DIR DIFTEST_CONTAINER_PREFIX

echo "==> [deadline-gate] 客户端 deadline 档探针（E38① 的运行期回归网）"
echo "    Go  admin-rpc : $GO_ADMIN_PORT"
echo "    RAG admin-rpc : $RAG_ADMIN_PORT"
echo "    RUN_DIR       : $RUN_DIR"
echo "    容器名前缀    : $DIFTEST_CONTAINER_PREFIX"
echo "    探针          : $PROBE"
echo

"$PROBE" --stacks-up
RC=$?

echo
echo "==> [deadline-gate] 探针退出码 = ${RC}（0=PASS / 1=FAIL / 2=INCONCLUSIVE）"
exit $RC
