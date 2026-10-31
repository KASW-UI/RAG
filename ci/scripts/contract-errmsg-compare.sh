#!/bin/sh
# =============================================================================
# ci/scripts/contract-errmsg-compare.sh —— R5 错误码契约门 · **内层阶段②（compare）**
#
# 【形态】
#   由 ci/scripts/dind-run.sh 以 **STAGE2=contract-errmsg-compare** 调起
#   ⇒ 在**内层 python 容器**里执行（与阶段①同一个 bind mount ⇒ 读得到 dump）。
#
# 【为什么阶段②要用 python 镜像】
#   比对逻辑（解析两段式基准、percent 反转义、Go↔Java 双向比对、差异四态分类、
#   回指 docs/Intentional_Deviations/）是 **python3** 写的。
#   ⚠️ CI 的 maven 镜像**没有 python3**（实测 2026-10-31：
#      `maven:3.9.11-eclipse-temurin-21` = Ubuntu 24.04 ⇒ `command -v python3` ⇒ MISSING）
#      ⇒ 阶段①（需要 JDK+Maven 跑探针）与阶段②（需要 python3 做比对）必须分属两个镜像。
#
# 【本阶段不需要什么】
#   ❌ JDK / Maven（不编译、不跑探针 —— 只读阶段①产出的 dump）
#   ❌ Go 工具链　❌ Go 仓在线可访问　❌ go 命令
#   ❌ 数据库 / Redis / Kafka
#
# 【退出码】由 ci/scripts/check-errmsg-contract.sh 给出：
#   0 = PASS / 1 = FAIL(契约漂移) / 2 = BLOCKED(环境不满足) / 3 = PENDING(有未签字差异)
#   ⚠️ BLOCKED / PENDING 一律不得计入 PASS。
# =============================================================================
set -eu

# ⚠️ dump 目录与阶段①**完全一致**（两个内层容器挂的是同一个 $PWD）
DUMP_DIR="${ERRSMSG_DUMP_DIR:-${PWD}/errmsg-dump}"

cd repo

echo "[ci] ============================================================"
echo "[ci] 阶段②：R5 错误码契约 · 比对（Java 侧 dump vs Go 真源基准）"
echo "[ci] 目标域  : ${ERRSMSG_DOMAINS:-（全部）}"
echo "[ci] dump 目录: ${DUMP_DIR}"
echo "[ci] 基准目录: ${ERRSMSG_CONTRACTS_DIR:-ci/contracts/errmsg}"
echo "[ci] ============================================================"

# ⚠️ **必须**带 PHASE=compare：
#   · 跳过编译与探针（阶段①已经跑过 ⇒ 这里再跑一次就是重复且无意义）
#   · 强制要求 REPLAY_DUMP_DIR 有值（缺了 ⇒ 立刻 BLOCKED，绝不「没比对就当通过」）
rc=0
ERRSMSG_PHASE=compare \
ERRSMSG_REPLAY_DUMP_DIR="${DUMP_DIR}" \
ERRSMSG_SKIP_COMPILE=1 \
  bash ci/scripts/check-errmsg-contract.sh || rc=$?
exit "$rc"
