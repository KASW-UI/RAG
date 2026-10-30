#!/bin/sh
# =============================================================================
# ci/scripts/testcontainers-env.sh —— Testcontainers 环境准备（被 test.sh source）
#
# 【执行位置】
#   本脚本运行在**嵌套构建容器**里（DinD 的内层 daemon 上的那个容器），
#   不是任务容器本身。
#
# 【为什么需要这一段】
#   本仓 479 个测试文件里有 128 个**直接** new PostgreSQLContainer/GenericContainer，
#   且**没有一个**探测 Docker 可用性 ⇒ Docker 不可用时它们会报一堆看不懂的
#   ClassNotFound / 连接超时，而不是一句"没有 Docker"。
#   ⇒ 这里把 Docker 的可达性变成**一个前置断言 + 一组确定的环境变量**。
#
# 【三条环境变量各自解决什么】
#
#   ① DOCKER_HOST=unix:///var/run/docker.sock
#      由 ci/scripts/dind-run.sh 用 `-e` 传进来（内层 daemon 的 socket）。
#      Testcontainers 的发现顺序：先 DOCKER_HOST，再 ~/.testcontainers.properties。
#
#   ② TESTCONTAINERS_HOST_OVERRIDE=<内层 bridge 网关>
#      Testcontainers 生成连接串（getJdbcUrl）时默认用 "localhost"，
#      但内层 daemon 发布端口落在**任务容器**的网络栈上 ⇒ 嵌套容器里的
#      localhost 不是它 ⇒ 连不上。
#      这里**运行时自发现**（不是写死 172.17.0.1）：
#          docker network inspect bridge --format '{{(index .IPAM.Config 0).Gateway}}'
#      该网关 = 任务容器在内层 bridge 上的 IP = 固定宿主口实际发布的位置 ✓
#      ⇒ 与 Testcontainers 自己的"检测到在容器里就改用默认网关"的行为一致，
#        显式写出来只是把隐式行为变成确定行为。
#
#   ③ TESTCONTAINERS_RYUK_DISABLED=true
#      Ryuk 是 Testcontainers 的回收器容器。本仓 17 个模块的 surefire 已经设了它
#      （省掉一次额外镜像拉取）⇒ 这里再兜一层，保证 CI 里一致。
#
# 【端口段】
#   本仓有全局端口段表：22700–22999 是 Testcontainers 宿主口段，**按域互斥**
#   （message 22760-22789 / like 22790-22819 / task 22820-22849 /
#     article 22850-22879 / comment 22880-22909 / favorite 22910-22939 /
#     hot 22940-22969）⇒ 不同域的流水线**可以并行**。
#   ⚠️ DinD 形态下这些端口落在**每个任务容器自己的网络命名空间**里
#     ⇒ 并行流水线之间也不会互相抢口（同域并发两次仍会撞，已由 Concourse
#     的"同一 job 同时只跑一个 build"天然规避）。
set -eu

: "${DOCKER_HOST:=unix:///var/run/docker.sock}"
export DOCKER_HOST

if [ -z "${TESTCONTAINERS_HOST_OVERRIDE:-}" ]; then
  _gw="$(docker network inspect bridge --format '{{(index .IPAM.Config 0).Gateway}}' 2>/dev/null || true)"
  if [ -n "$_gw" ]; then
    TESTCONTAINERS_HOST_OVERRIDE="$_gw"
    export TESTCONTAINERS_HOST_OVERRIDE
  fi
fi

TESTCONTAINERS_RYUK_DISABLED="${TESTCONTAINERS_RYUK_DISABLED:-true}"
export TESTCONTAINERS_RYUK_DISABLED

# --- 前置断言：Docker 不可用就立刻说清楚，而不是让 128 个测试文件各报一次 ---
if ! docker info >/dev/null 2>&1; then
  echo "[ci] ❌ Docker daemon 不可达（DOCKER_HOST=${DOCKER_HOST}）。" >&2
  echo "[ci]    本仓 128 个测试文件直接起 Testcontainers 容器且不探测可用性，" >&2
  echo "[ci]    因此本流水线**必须**有 Docker。检查：" >&2
  echo "[ci]      1) ci/scripts/dind-run.sh 是否成功起了内层 dockerd" >&2
  echo "[ci]      2) task step 是否带 privileged: true（内层 dockerd 需要）" >&2
  echo "[ci]      3) worker 是否以 privileged 方式运行" >&2
  exit 1
fi

echo "[ci] Docker 可达：DOCKER_HOST=${DOCKER_HOST}"
echo "[ci] TESTCONTAINERS_HOST_OVERRIDE=${TESTCONTAINERS_HOST_OVERRIDE:-（未设置 => 用 localhost）}"
echo "[ci] TESTCONTAINERS_RYUK_DISABLED=${TESTCONTAINERS_RYUK_DISABLED}"
