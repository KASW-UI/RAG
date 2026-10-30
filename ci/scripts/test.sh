#!/bin/sh
# =============================================================================
# ci/scripts/test.sh —— 通用测试阶段（所有域流水线复用）
#
# 命令：mvn -pl <本域模块> -am test
#
# 【为什么测试在本仓是"连带跑"的】
#   本仓的模块依赖是 reactor 内的 SNAPSHOT 依赖（没有制品仓库，没有
#   distributionManagement）⇒ 单模块测试需要依赖先 install 到 ~/.m2，
#   而本仓自己的守门（*SharedLayerArtifactFreshnessGateTest）明确把
#   「不带 -am 的单模块构建」标为陈旧产物风险 ⇒ 因此这里**坚持 -am**。
#
#   代价（已知且接受）：`-am` 会把依赖模块（多数是 service/common 与
#   *-proto）的测试也带进来一起跑。收益：不会静默拿到陈旧产物，
#   且「共享层坏了 ⇒ 依赖它的域也红」本来就是应该发生的事
#   （共享层流水线是更快的先声，不是唯一真相）。
#
#   如果将来要消掉这份额外工作，**正确做法不是**去掉 -am，而是
#   把共享层的测试从域流水线里摘出去并给出一条独立断言
#   （共享层测试结果作为域流水线的输入/前置门）—— 那需要改本仓 pom
#   的 surefire 分组，属于项目代码改动，不在 CI 侧能单方面决定的范围。
#
# 用法：
#   MODULES=service/article/article-api,service/article/article-rpc sh ci/scripts/test.sh
set -eu

cd repo

MODULES="${MODULES:-}"
if [ -z "$MODULES" ]; then
  echo "[ci] 错误：MODULES 参数为空" >&2
  exit 2
fi

echo "[ci] ============================================================"
echo "[ci] 阶段：测试（test）"
echo "[ci] 目标模块: ${MODULES}"
echo "[ci] ============================================================"

# Testcontainers 环境（Docker 可达性前置断言 + host override + Ryuk 关闭）
# shellcheck source=ci/scripts/testcontainers-env.sh
. ci/scripts/testcontainers-env.sh

# shellcheck disable=SC2086
mvn -B --no-transfer-progress -pl "${MODULES}" -am test

echo "[ci] 测试阶段完成（用例明细见 report 步骤）。"
