#!/bin/sh
# =============================================================================
# ci/scripts/contract-schema.sh —— R6 Schema 契约门的「内层阶段」脚本
#
# 【形态】
#   由 ci/scripts/dind-run.sh 以 STAGE=contract-schema 调起 ⇒ 在**内层容器**里执行。
#   ⚠️ 内层镜像用 `python:3.12-bookworm`（**不是** maven 镜像）：
#     R6 只需要 python3 + Docker API + PG 镜像，**不需要 Maven / JDK**。
#
# 【为什么没有 docker CLI 也能起 PG 容器】
#   嵌套构建容器里**没有 docker 客户端**（实测 2026-10-31：镜像 = maven ⇒
#   `command -v docker` → MISSING），但它**能**通过挂载进来的 unix socket
#   访问 Docker API ⇒ `tools/difftest/docker_api.py`（**纯标准库**，无第三方依赖）
#   实现了「起容器 / 等就绪 / exec（含 stdin）/ 停容器 / 删容器 / 拉镜像」全套能力
#   ⇒ **有 CLI 走 CLI、没有 CLI 走 Docker API** ⇒ 两条路能力对齐。
#
# 【不需要什么】
#   ❌ Go 工具链　❌ Go 仓在线可访问　❌ go 命令　❌ Maven / JDK
#   ⇒ 契约基准已离线提取固化在 ci/contracts/schema/ 下。
#
# 【退出码】由 ci/scripts/check-schema-contract.py 给出：
#   0 = PASS / 1 = FAIL(契约漂移) / 2 = BLOCKED(环境不满足) / 3 = NOT_RUN(本域 N/A)
#   ⚠️ BLOCKED 与 NOT_RUN 一律不得计入 PASS。
set -eu

cd repo

echo "[ci] ============================================================"
echo "[ci] 阶段：R6 数据库 Schema 契约一致性（Flyway 迁移 vs 语义基准）"
echo "[ci] ============================================================"

exec python3 ci/scripts/check-schema-contract.py --all
