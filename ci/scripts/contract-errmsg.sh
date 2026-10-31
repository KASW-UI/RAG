#!/bin/sh
# =============================================================================
# ci/scripts/contract-errmsg.sh —— R5 错误码契约门的「内层阶段」脚本
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
# 【不需要什么】
#   ❌ Go 工具链　❌ Go 仓在线可访问　❌ go 命令
#   ❌ 数据库 / Redis / Kafka（错误码契约与活体依赖无关）
#
# 【退出码】由 ci/scripts/check-errmsg-contract.sh 给出：
#   0 = PASS / 1 = FAIL(契约漂移) / 2 = BLOCKED(环境不满足)
#   ⚠️ BLOCKED 不得计入 PASS。
set -eu

cd repo

echo "[ci] ============================================================"
echo "[ci] 阶段：R5 错误码契约一致性（Java 侧 vs 仓内基准）"
echo "[ci] 目标域: ${ERRSMSG_DOMAINS:-（全部）}"
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
bash ci/scripts/check-errmsg-contract.sh || rc=$?
if [ "$rc" = "2" ]; then
  echo "[ci] ⚠️ R5 检查返回 BLOCKED(2) ⇒ 重试一次（protobuf-maven-plugin 临时文件竞态属已知抖动）"
  rc=0
  bash ci/scripts/check-errmsg-contract.sh || rc=$?
fi
exit "$rc"
