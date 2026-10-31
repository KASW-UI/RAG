#!/bin/sh
# =============================================================================
# ci/scripts/contract-errmsg.sh —— R5 错误码契约门 · **内层阶段①（dump）**
#
# 【形态】
#   由 ci/scripts/dind-run.sh 以 STAGE=contract-errmsg 调起 ⇒ 在**内层 maven
#   容器**里执行（与 build.sh / test.sh 同一形态 ⇒ 复用同一份 ~/.m2 缓存、
#   同一个 Maven 镜像源 settings.xml、同一套镜像源机制）。
#
# 【为什么需要 Maven】
#   R5 的被测对象是 **Java 侧真实产物**（`<域>-common` 的编译产物）：
#   探针遍历真实枚举、读真实文案表、调真实错误载体 ⇒ 编译不过 = 检查做不成。
#   ⚠️ 正因为跑的是真实产物，它才能发现「改了枚举但没改文案表」这类真问题。
#
# 【本阶段只产出 Java 侧 dump，不做比对 —— 为什么】
#   实测 2026-10-31（R5 在 Concourse Task 内首跑）：CI 的 maven 镜像里**没有
#   python3**（`maven:3.9.11-eclipse-temurin-21` = Ubuntu 24.04 ⇒
#   `command -v python3` ⇒ MISSING），而比对逻辑（解析两段式基准、反转义、
#   四态分类、回指 docs/Intentional_Deviations/）是 python3 写的。
#   ⇒ 单阶段跑 ⇒ `python3: command not found` ⇒ 每个域都被记成 BLOCKED。
#   ⇒ 拆成两个内层容器：
#       内层①（本阶段 · maven 镜像）  ⇒ 编译 + 跑探针 ⇒ 产出 Java 侧 dump
#       内层②（contract-errmsg-compare.sh · python 镜像）⇒ 比对
#   ⇒ 两者共用同一个 bind mount（$PWD）⇒ dump 在**同一次 build 内**传递。
#   ⚠️ dump 目录放在 $PWD 下（**不在 repo/ 里**）⇒ 不进 task 的 outputs ⇒ 不会跨 build 残留。
#
# 【不需要什么】
#   ❌ Go 工具链　❌ Go 仓在线可访问　❌ go 命令
#   ❌ 数据库 / Redis / Kafka（错误码契约与活体依赖无关）
#
# 【退出码】
#   0 = 全部域的 dump 已产出 / 2 = 有域没产出 dump（⇒ BLOCKED）
#   ⚠️ 本阶段**不产生 PASS/FAIL 结论**（还没比对）⇒ 不得计入 PASS。
#   ⚠️ 真正的 PASS/FAIL/PENDING 结论由内层阶段②给出。
# =============================================================================
set -eu

# ⚠️ dump 目录：**必须在 repo/ 之外**（否则会被 task 的 outputs 持久化 ⇒ 跨 build 残留）
#   ⚠️ 必须在 `cd repo` **之前**算：此时 $PWD 是任务目录（内层容器的 -w），
#      `cd repo` 之后 $PWD 就变成 <任务目录>/repo 了。
DUMP_DIR="${ERRSMSG_DUMP_DIR:-${PWD}/errmsg-dump}"

cd repo
mkdir -p "${DUMP_DIR}"

echo "[ci] ============================================================"
echo "[ci] 阶段①：R5 错误码契约 · 产出 Java 侧 dump（maven 镜像）"
echo "[ci] 目标域  : ${ERRSMSG_DOMAINS:-（全部）}"
echo "[ci] dump 目录: ${DUMP_DIR}"
echo "[ci] ============================================================"

# ⚠️ **不设 ERRSMSG_SKIP_COMPILE**：跳过编译就等于不验「产物是不是新的」
#   ⇒ CI 里绝不能跳过（只有本地调试已有 target/ 时才用）。
#
# 【为什么 BLOCKED 要重试一次】
#   实测 2026-10-31：`protobuf-maven-plugin` 有**临时文件清理竞态**
#   （`Unable to clean up temporary proto file directory: … unable to be deleted`）
#   ⇒ 首次 `mvn compile` 失败、**重跑即成功** ⇒ 属环境级抖动，不是代码问题。
#   ⇒ 若不重试，这道门在 CI 里会**随机红** ⇒ 每次红都要人查一遍 ⇒ 噪声。
#   ⇒ 只对 **BLOCKED(2)** 重试（编译失败/探针跑不起来）；
#   ⇒ **FAIL(1) 一律不重试**（契约漂移必须立刻暴露，重试只会掩盖）。
#   ⇒ 重试后仍是 BLOCKED ⇒ 最终仍 BLOCKED ⇒ 不会把真问题洗成绿。
rc=0
ERRSMSG_PHASE=dump \
ERRSMSG_DUMP_OUT_DIR="${DUMP_DIR}" \
  bash ci/scripts/check-errmsg-contract.sh || rc=$?
if [ "$rc" = "2" ]; then
  echo "[ci] ⚠️ dump 阶段返回 BLOCKED(2) ⇒ 重试一次（protobuf-maven-plugin 临时文件竞态属已知抖动）"
  rc=0
  ERRSMSG_PHASE=dump \
  ERRSMSG_DUMP_OUT_DIR="${DUMP_DIR}" \
    bash ci/scripts/check-errmsg-contract.sh || rc=$?
fi
exit "$rc"
