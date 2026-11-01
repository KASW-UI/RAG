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
#      ⚠️ **不能依赖 docker CLI**（2026-10-31 实测踩到）：
#        嵌套构建容器用的是 maven:3.9.11-eclipse-temurin-21（Debian）
#        ⇒ 镜像里**没有 docker 客户端**（实测 command -v docker → MISSING；
#           而 curl / java 都有）。
#        ⇒ CLI 路径不可用 ⇒ 改读 **/proc/net/route 的默认网关**
#          （嵌套容器的默认网关 = 内层 bridge 网关 = 固定宿主口的实际发布位置 ✓）。
#
#   ③ TESTCONTAINERS_RYUK_DISABLED=true
#      Ryuk 是 Testcontainers 的回收器容器。本仓 17 个模块的 surefire 已经设了它
#      （省掉一次额外镜像拉取）⇒ 这里再兜一层，保证 CI 里一致。
#
# 【Docker 可达性怎么探】
#   **不**用 `docker info` —— 嵌套镜像没有 CLI ⇒ 那个命令必然失败，
#   而失败信息会误导成"daemon 不可达"（实际 daemon 是好的）。
#   Testcontainers 真正需要的是 **Docker API over unix socket**
#   ⇒ 直接用 curl 打 /_ping（这正是 docker-java 走的那条路）：
#       curl -sf --unix-socket /var/run/docker.sock http://localhost/_ping
#   curl 在 maven 镜像里存在（实测 /usr/bin/curl）⇒ 这条探针与被测对象同构。
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

# DOCKER_HOST 形如 unix:///var/run/docker.sock ⇒ 取出 socket 路径
DOCKER_SOCK="${DOCKER_HOST#unix://}"
case "$DOCKER_SOCK" in
  /*) : ;;
  *)  DOCKER_SOCK="/${DOCKER_SOCK}" ;;
esac

# --- 网关自发现（不依赖 docker CLI）--------------------------------------------
# 优先级 ① docker CLI（若镜像里恰好有）② /proc/net/route 的默认路由
discover_gateway() {
  if command -v docker >/dev/null 2>&1; then
    _gw="$(docker network inspect bridge --format '{{(index .IPAM.Config 0).Gateway}}' 2>/dev/null || true)"
    if [ -n "$_gw" ]; then
      echo "$_gw"
      return 0
    fi
  fi
  # /proc/net/route 的 Gateway 字段是**小端十六进制**
  #   例：内层 bridge 网关 10.250.0.1 ⇒ 0x0100FA0A ⇒ 存储为 "0100FA0A"
  #      ⇒ 输出顺序为 substr(7,2) substr(5,2) substr(3,2) substr(1,2)
  awk '$2 == "00000000" {
        printf "%d.%d.%d.%d", "0x"substr($3,7,2), "0x"substr($3,5,2), "0x"substr($3,3,2), "0x"substr($3,1,2)
        exit
      }' /proc/net/route 2>/dev/null || true
}

if [ -z "${TESTCONTAINERS_HOST_OVERRIDE:-}" ]; then
  _gw="$(discover_gateway)"
  if [ -n "$_gw" ]; then
    TESTCONTAINERS_HOST_OVERRIDE="$_gw"
    export TESTCONTAINERS_HOST_OVERRIDE
  fi
fi

TESTCONTAINERS_RYUK_DISABLED="${TESTCONTAINERS_RYUK_DISABLED:-true}"
export TESTCONTAINERS_RYUK_DISABLED

# --- 前置断言：Docker 不可用就立刻说清楚，而不是让 128 个测试文件各报一次 ---
# 探针与被测对象同构：Testcontainers 走的就是 Docker API over unix socket。
docker_api_ping() {
  if command -v curl >/dev/null 2>&1; then
    curl -sf --max-time 10 --unix-socket "$DOCKER_SOCK" http://localhost/_ping >/dev/null 2>&1
  elif command -v docker >/dev/null 2>&1; then
    docker info >/dev/null 2>&1
  else
    return 2   # 既没 curl 也没 docker ⇒ 无法探测（区别于"探测到不可达"）
  fi
}

set +e
docker_api_ping
_ping_rc=$?
set -e

if [ "$_ping_rc" -eq 2 ]; then
  echo "[ci] ❌ 无法探测 Docker：嵌套镜像里既没有 curl 也没有 docker 客户端。" >&2
  # ⚠️ 一律用 ${VAR} 显式界定变量名边界：macOS bash 3.2 会把 $VAR 后紧跟的多字节字符
    #    （此处是全角括号）算进变量名 ⇒ set -u 下报 unbound variable ⇒ **这条报错行本身崩掉**。
  echo "[ci]    Testcontainers 需要 Docker API over unix socket（${DOCKER_HOST}）⇒ 镜像必须至少有 curl。" >&2
  exit 1
fi

if [ "$_ping_rc" -ne 0 ]; then
  echo "[ci] ❌ Docker daemon 不可达（DOCKER_HOST=${DOCKER_HOST}，socket=${DOCKER_SOCK}）。" >&2
  if [ ! -S "$DOCKER_SOCK" ]; then
    echo "[ci]    socket 文件都不是 socket ⇒ 内层 dockerd 的 socket 没被挂进嵌套容器。" >&2
    echo "[ci]    检查 ci/scripts/dind-run.sh 里的 -v \"\${DIND_SOCK}:\${DIND_SOCK}\"。" >&2
  else
    echo "[ci]    socket 在，但 /_ping 没响应 ⇒ 内层 daemon 没起来或已挂。" >&2
  fi
  echo "[ci]    本仓 128 个测试文件直接起 Testcontainers 容器且不探测可用性，" >&2
  echo "[ci]    因此本流水线**必须**有 Docker。检查：" >&2
  echo "[ci]      1) ci/scripts/dind-run.sh 是否成功起了内层 dockerd" >&2
  echo "[ci]      2) task step 是否带 privileged: true（内层 dockerd 需要）" >&2
  echo "[ci]      3) worker 是否以 privileged 方式运行" >&2
  exit 1
fi

echo "[ci] Docker 可达：DOCKER_HOST=${DOCKER_HOST}（Docker API /_ping 有响应）"
echo "[ci] TESTCONTAINERS_HOST_OVERRIDE=${TESTCONTAINERS_HOST_OVERRIDE:-（未设置 => 用 localhost）}"
echo "[ci] TESTCONTAINERS_RYUK_DISABLED=${TESTCONTAINERS_RYUK_DISABLED}"
