#!/bin/sh
# =============================================================================
# ci/scripts/dind-run.sh —— DinD 外壳：在任务容器里起内层 dockerd，再在内层
#                            daemon 上跑真正的构建容器。
#
# 【为什么要有这层壳】
#   本仓 128 个测试文件直接起 Testcontainers 容器 ⇒ 任务容器里必须有一个
#   Docker API 可达的 daemon。而 Concourse 8 的 task 配置**没有**"挂载宿主
#   路径"的声明项（实测 concourse 二进制里 container_spec 零命中）⇒
#   "把宿主 docker.sock 挂进任务容器"这条 wormhole 路走不通
#   ⇒ 唯一官方支持的形态是 DinD（task step 开 privileged: true）。
#
# 【执行链】
#   任务容器（本脚本，privileged）
#     └─ 起内层 dockerd（unix:///var/run/docker.sock）
#          └─ docker run 嵌套构建容器
#               ├─ -v $PWD:$PWD          源码与缓存，**两侧路径必须完全一致**
#               │                       （Testcontainers 靠它推导 volume 挂载路径）
#               ├─ -v /var/run/docker.sock:...   把内层 socket 给嵌套容器
#               └─ DOCKER_HOST=unix:///var/run/docker.sock
#
# 【为什么两侧路径必须一致】
#   Testcontainers 创建容器时会按"自己所在的工作目录"推导要挂进去的路径
#   ⇒ 路径不一致 ⇒ 挂载错位 ⇒ 容器里看到的是空目录。
#
# 【为什么内层 daemon 发布端口后测试连得上】
#   内层 daemon 把固定宿主口（如 22760）发布在**本任务容器**的 0.0.0.0；
#   嵌套容器的默认网关 = 内层 bridge 网关 = 本任务容器的 IP
#   ⇒ Testcontainers 自动改用网关地址 ⇒ 可达。
#   ⇒ 顺带好处：固定端口段落在**每个任务容器自己的网络命名空间**里
#     ⇒ 11 条流水线并行也互不抢端口。
set -eu

IMAGE="${IMAGE:-}"
STAGE="${STAGE:-build}"
DIND_SOCK=/var/run/docker.sock

if [ -z "$IMAGE" ]; then
  echo "[ci] 错误：IMAGE 参数为空（嵌套构建容器镜像）" >&2
  exit 2
fi

echo "[ci] ============================================================"
echo "[ci] 阶段外壳：DinD（起内层 dockerd → 跑嵌套构建容器）"
echo "[ci] 嵌套镜像: ${IMAGE}"
echo "[ci] 内层阶段: ci/scripts/${STAGE}.sh"
echo "[ci] 工作目录: $(pwd)"
echo "[ci] ============================================================"

# --- 1. 起内层 dockerd -------------------------------------------------------
# ⚠️ 内层 daemon 必须有 registry-mirrors：本机直连 Docker Hub 被墙
#    （实测 auth.docker.io / registry-1.docker.io 均 15s 超时无响应），
#    而 Testcontainers 起的容器镜像是**裸名**（postgres:16-alpine /
#    redis:7-alpine / apache/kafka:3.7.2）⇒ 内层 daemon 拉不到它们
#    ⇒ 测试就会以"拉镜像超时"的形式失败。
#
#    两种模式：
#      ① 公网镜像源（REGISTRY 不含端口）
#         mirrors = [公网源, 公网源2]   —— 首选当前正在用的那个
#      ② 本地 registry（REGISTRY 含端口，如 172.17.0.1:5000）
#         mirrors = [本地, 公网源, 公网源2]
#         insecure-registries = [本地]  —— 本地 registry 走 HTTP
#         ⇒ 裸名（postgres:16-alpine）会经镜像机制落到本地 registry 的
#            /v2/library/postgres/... ⇒ 不依赖公网。
#
#    ⚠️ 宿主 daemon 那份配了 3 个源，其中 cloudfront-docker-cf.mrs.1ms.run
#       的 blob 下载会 EOF（实测拉 concourse 镜像 12 次全挂在这一个层）
#       ⇒ 这里把可用源按实测可用性排序。
mkdir -p /etc/docker
REGISTRY="${REGISTRY:-docker.m.daocloud.io}"
case "$REGISTRY" in
  *:*) REG_IS_LOCAL=1 ;;
  *)   REG_IS_LOCAL=0 ;;
esac

if [ "$REG_IS_LOCAL" -eq 1 ]; then
  cat >/etc/docker/daemon.json <<JSONEOF
{
  "registry-mirrors": ["http://${REGISTRY}", "https://docker.m.daocloud.io", "https://docker.1ms.run"],
  "insecure-registries": ["${REGISTRY}"]
}
JSONEOF
else
  cat >/etc/docker/daemon.json <<JSONEOF
{
  "registry-mirrors": ["https://${REGISTRY}", "https://docker.m.daocloud.io", "https://docker.1ms.run"]
}
JSONEOF
fi
echo "[ci] 内层 daemon registry 配置：$(cat /etc/docker/daemon.json | tr -d '\n ')"

# Concourse 会用 run 命令覆盖镜像的 ENTRYPOINT ⇒ dockerd 不会自动起 ⇒ 自己起。
#
# 🔴 --bip / --default-address-pool 必须显式指定，**不能用默认值**：
#   dockerd 默认给 docker0 分 172.17.0.0/16。而本仓 CI 跑在 Concourse 的
#   containerd worker 里，worker 容器本身就在**外层 Docker 的 docker0**（172.17.0.0/16）上
#   ⇒ 内层 dockerd 一起来，任务容器 netns 里就多了一条 172.17.0.0/16 的直连路由，
#   把「去外层 docker0 网关 172.17.0.1」的流量**劫持**到内层 docker0（上面没人听 5010）
#   ⇒ 内层 daemon 拉镜像报 `dial tcp 172.17.0.1:5010: connect: connection refused`
#   （实测 2026-10-30 本地演练首次运行命中）。
#   ⇒ 换一个与外层网段不冲突的段（默认 10.250.0.0/16；
#      与 worker 的 CNI 网段 10.80.0.0/16、外层 docker0 172.17.0.0/16 都不重叠）。
#   ⇒ 冲突时可用 DIND_BIP / DIND_POOL 覆盖。
#   ⚠️ --default-address-pool 的值必须是 **key=value** 形式（dockerd 的 pool-options
#      语法，实测直接给 "10.250.0.0/16" 会报 "must be a key=value pair" 并退出 125）；
#      且必须带 **size**（实测只给 base 会报
#      "network at index 0 (.../16 0) is not in canonical form" —— size=0 即非 canonical）。
DIND_BIP="${DIND_BIP:-10.250.0.1/16}"
DIND_POOL="${DIND_POOL:-10.250.0.0/16}"
DIND_POOL_SIZE="${DIND_POOL_SIZE:-24}"
dockerd --host="unix://${DIND_SOCK}" \
        --bip="${DIND_BIP}" \
        --default-address-pool="base=${DIND_POOL},size=${DIND_POOL_SIZE}" \
        >/tmp/dockerd.log 2>&1 &
DIND_PID=$!

cleanup() {
  if kill -0 "$DIND_PID" 2>/dev/null; then
    kill "$DIND_PID" 2>/dev/null || true
    wait "$DIND_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT INT TERM

# --- 2. 等 daemon 就绪 -------------------------------------------------------
ready=0
i=0
while [ "$i" -lt 60 ]; do
  if docker -H "unix://${DIND_SOCK}" info >/dev/null 2>&1; then
    ready=1
    break
  fi
  i=$((i + 1))
  sleep 1
done

if [ "$ready" -ne 1 ]; then
  echo "[ci] ❌ 内层 dockerd 60 秒内没就绪，日志尾部：" >&2
  tail -60 /tmp/dockerd.log >&2 || true
  exit 1
fi
echo "[ci] ✅ 内层 dockerd 已就绪。"

# --- 3. 可选的 Maven 镜像源 ---------------------------------------------------
# 【为什么需要】
#   本机到 Maven Central 只有 ~115 KB/s（实测），整棵依赖树要几十分钟；
#   内网/离线环境通常也有自己的 Nexus 镜像 ⇒ 需要一个**可配置**的镜像源。
#
# 【怎么生效】
#   嵌套容器的 /root/.m2 是从 $PWD/.m2 bind 进去的（见下面的 -v）
#   ⇒ 把 settings.xml 写进 $PWD/.m2，Maven 就会自动读到（默认位置）。
#   ⇒ 不需要改 mvn 命令行，也不需要改仓内 pom。
#
# 【为什么用「额外仓库」而不是 <mirror>】
#   <mirrorOf>*</mirrorOf> 会把 Central **完全顶掉** ⇒ 镜像缺件就直接构建失败，
#   没有回退。改成"额外仓库"后：先查镜像（快），404 再回退 Central（慢但兜底）。
#   本仓 pom 未声明任何 <repositories> ⇒ 镜像仓库会排在 Central 前面。
MAVEN_MIRROR_URL="${MAVEN_MIRROR_URL:-}"
if [ -n "$MAVEN_MIRROR_URL" ]; then
  mkdir -p "$PWD/.m2"
  cat >"$PWD/.m2/settings.xml" <<XMLEOF
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">
  <profiles>
    <profile>
      <id>ci-maven-mirror</id>
      <repositories>
        <repository>
          <id>ci-maven-mirror</id>
          <url>${MAVEN_MIRROR_URL}</url>
          <releases><enabled>true</enabled><updatePolicy>never</updatePolicy></releases>
          <snapshots><enabled>false</enabled></snapshots>
        </repository>
      </repositories>
      <pluginRepositories>
        <pluginRepository>
          <id>ci-maven-mirror-plugins</id>
          <url>${MAVEN_MIRROR_URL}</url>
          <releases><enabled>true</enabled><updatePolicy>never</updatePolicy></releases>
          <snapshots><enabled>false</enabled></snapshots>
        </pluginRepository>
      </pluginRepositories>
    </profile>
  </profiles>
  <activeProfiles>
    <activeProfile>ci-maven-mirror</activeProfile>
  </activeProfiles>
</settings>
XMLEOF
  echo "[ci] Maven 镜像源: ${MAVEN_MIRROR_URL}（写入 \$PWD/.m2/settings.xml）"
else
  echo "[ci] Maven 镜像源: 未配置（走默认 Central）"
fi

# --- 4. 在内层 daemon 上跑嵌套构建容器 --------------------------------------
# 注意 $PWD 既是任务容器的工作目录，也是嵌套容器的工作目录 ⇒ 路径一致。
#
# ⚠️ 阶段脚本的路径必须带 repo/ 前缀：
#   任务容器的工作目录里，源码在 $PWD/repo（git 资源的输入目录），
#   而 build.sh / test.sh 内部第一行是 `cd repo` ⇒ 它们是**站在 repo 的父目录**执行的。
#   ⇒ 嵌套容器里对应地要执行 repo/ci/scripts/<stage>.sh（实测 2026-10-30：
#     写成 ci/scripts/<stage>.sh 会报 "cannot open ci/scripts/build.sh"）。
exec docker -H "unix://${DIND_SOCK}" run --rm \
  -v "$PWD:$PWD" \
  -w "$PWD" \
  -v "${DIND_SOCK}:${DIND_SOCK}" \
  -v "$PWD/.m2:/root/.m2" \
  -e DOCKER_HOST="unix://${DIND_SOCK}" \
  -e TESTCONTAINERS_RYUK_DISABLED=true \
  -e MODULES="${MODULES:-}" \
  "$IMAGE" \
  sh "repo/ci/scripts/${STAGE}.sh"
