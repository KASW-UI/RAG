#!/bin/sh
# =============================================================================
# ci/scripts/build.sh —— 通用构建阶段（所有域流水线复用）
#
# 【为什么命令长这样】
#   mvn -pl <本域模块> -am -DskipTests package
#
#   -pl  只构建本流水线负责的模块（域级隔离 ⇒ 改一个域不重编全仓）
#   -am  **必须带**：本仓所有跨模块依赖（common / users-common / *-proto）都是
#        reactor 内的 SNAPSHOT 依赖 ⇒ 不带 -am 时 Maven 会去 ~/.m2 取**陈旧**的
#        jar。本仓自带 *SharedLayerArtifactFreshnessGateTest 就是在守这条
#        （见 service/users/admin/admin-rpc/.../AdminRpcSharedLayerArtifactFreshnessGateTest
#        的类注释：单模块构建会拿到 2026-10-27 的旧 common ⇒ fat jar 里嵌旧共享层
#        ⇒ Boot 4 下进程启动即挂）。**不要**改成本仓没验证过的 install+单测两段式。
#   -DskipTests  只编译测试代码、不执行 ⇒ 编译期错误在这一步就能暴露，
#        而执行留给下一个 task（便于在 Concourse 上区分「构建失败」与「测试失败」）。
#
# 【工具链】
#   镜像固定 docker.m.daocloud.io/library/maven:3.9.11-eclipse-temurin-21
#   ⇒ 版本与本仓 pom 的 java.version=21 / Maven 3.9.x 对齐；
#   ⇒ **不能用 alpine 变体**：protobuf-maven-plugin 要执行 protoc 原生二进制，
#      那是 glibc 链接的，musl 跑不起来。
#   ⇒ **必须带镜像源前缀**：本机 daemon 的 registry-mirrors 链路里
#      cloudfront-docker-cf.mrs.1ms.run 的 blob 下载会 EOF（实测 alpine/maven 拉取失败）。
#
# 用法（Concourse task 调用）：
#   MODULES=service/article/article-api,service/article/article-rpc sh ci/scripts/build.sh
set -eu

cd repo

MODULES="${MODULES:-}"
if [ -z "$MODULES" ]; then
  echo "[ci] 错误：MODULES 参数为空（要构建的模块目录，逗号分隔，相对仓库根）" >&2
  exit 2
fi

echo "[ci] ============================================================"
echo "[ci] 阶段：构建（package -DskipTests）"
echo "[ci] 目标模块: ${MODULES}"
echo "[ci] 提交: $(cat .git/ref 2>/dev/null || git rev-parse HEAD 2>/dev/null || echo unknown)"
echo "[ci] ============================================================"

# shellcheck disable=SC2086
mvn -B --no-transfer-progress -pl "${MODULES}" -am -DskipTests package

echo "[ci] 构建阶段完成。"
