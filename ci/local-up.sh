#!/usr/bin/env bash
# =============================================================================
# ci/local-up.sh —— 本地 Concourse 环境的一键起流（load 镜像 → 起环境 → 注册流水线）
#
# 【为什么需要这个脚本】
#   本机网络对公网镜像源不稳（直连 Docker Hub 被墙；daocloud 只代理 library/*；
#   1ms 的 blob CDN 对特定 layer 稳定 EOF）⇒ 镜像靠人工下载 tar。
#   ⇒ 这个脚本把"下载产物 → 可运行环境"之间的所有步骤串成一条命令，
#      避免每次手工敲 10 条 docker 命令时漏掉其中一步。
#
# 【用法】
#   bash ci/local-up.sh                    # load 镜像 → 起 Concourse → 注册 11 条流水线
#   bash ci/local-up.sh --registry         # 额外起本地 registry 并把 CI 镜像源指向它
#   bash ci/local-up.sh --trigger rag-task # 注册完顺带触发一条流水线
#   bash ci/local-up.sh --status           # 只看环境状态
#
# 【环境变量】
#   DOWNLOADS_DIR   tar 所在目录（默认 ~/Downloads）
#   CONCOURSE_DIR   本地 Concourse 目录（默认 /Users/pr/concourse-local）
#   CONCOURSE_TARGET  fly target 名（默认 concourse）
#   CONCOURSE_REGISTRY  镜像源（默认 docker.m.daocloud.io；--registry 时自动填本地地址）
#   CONCOURSE_MAVEN_MIRROR  Maven 镜像源（默认空 = 走 Central；填 URL 则优先生效）
#   REG_PORT            本地 registry 端口（默认 5010；5000 被 macOS ControlCenter 占）
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DOWNLOADS_DIR="${DOWNLOADS_DIR:-$HOME/Downloads}"
CONCOURSE_DIR="${CONCOURSE_DIR:-/Users/pr/concourse-local}"
TARGET="${CONCOURSE_TARGET:-concourse}"
REGISTRY="${CONCOURSE_REGISTRY:-docker.m.daocloud.io}"

use_registry=0
trigger_pipeline=""
only_status=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --registry) use_registry=1; shift ;;
    --trigger) trigger_pipeline="${2:-}"; shift 2 ;;
    --status) only_status=1; shift ;;
    *) echo "未知参数: $1"; exit 2 ;;
  esac
done

log() { printf '\n\033[0;36m[local-up]\033[0m %s\n' "$*"; }

# --- 0. daemon 探活 ---------------------------------------------------------
log "0/6 检查 docker daemon"
if ! docker info >/dev/null 2>&1; then
  echo "❌ docker daemon 无响应。" >&2
  echo "   Docker Desktop 卡死时：重启 Docker Desktop 后重跑本脚本。" >&2
  echo "   （重启会停掉其它 lane 的容器 ⇒ 请先确认那些 lane 不在跑任务。）" >&2
  exit 1
fi
echo "  ✅ daemon 正常"

if [[ "$only_status" -eq 1 ]]; then
  log "环境状态"
  docker ps --filter name=concourse --format '  {{.Names}}\t{{.Status}}\t{{.Ports}}'
  fly -t "$TARGET" workers 2>/dev/null || echo "  （fly target 未就绪）"
  exit 0
fi

# --- 1. load 人工下载的镜像 tar --------------------------------------------
log "1/6 load 镜像 tar（${DOWNLOADS_DIR}）"
load_if_present() {
  local tar="$1" img="$2"
  if docker image inspect "$img" >/dev/null 2>&1; then
    echo "  ⏭  已存在，跳过: $img"
    return 0
  fi
  if [[ ! -f "$tar" ]]; then
    echo "  ⚠️  缺少 tar: $tar（跳过 $img）"
    return 1
  fi
  echo "  ⬇️  load $img ..."
  docker load -i "$tar" >/dev/null
  echo "  ✅ $img"
}
load_if_present "$DOWNLOADS_DIR/concourse-8.3.2.tar" "concourse/concourse:8.3.2" || true
load_if_present "$DOWNLOADS_DIR/maven-3.9.11.tar" "maven:3.9.11-eclipse-temurin-21" || true
load_if_present "$DOWNLOADS_DIR/python-3.12.tar" "python:3.12-bookworm" || true

# --- 2.（可选）本地 registry -------------------------------------------------
if [[ "$use_registry" -eq 1 ]]; then
  log "2/6 起本地 registry 并推入 CI 所需镜像"
  # ⚠️ 默认端口 5010 而不是 5000：5000 被 macOS 的 ControlCenter 占用（实测 2026-10-30）
  REG_PORT="${REG_PORT:-5010}"
  GW="$(docker network inspect bridge --format '{{(index .IPAM.Config 0).Gateway}}')"
  REGISTRY="${GW}:${REG_PORT}"
  echo "  registry 地址: ${REGISTRY}"

  if ! docker image inspect registry:2 >/dev/null 2>&1; then
    echo "  ⬇️  拉 registry:2 ..."
    docker pull docker.m.daocloud.io/library/registry:2 >/dev/null 2>&1 \
      && docker tag docker.m.daocloud.io/library/registry:2 registry:2 \
      || echo "  ⚠️  registry:2 拉取失败（该模式不可用）"
  fi

  docker rm -f ci-registry >/dev/null 2>&1 || true
  docker run -d --name ci-registry -p "${REG_PORT}:5000" --restart unless-stopped \
    registry:2 >/dev/null
  sleep 4
  echo "  ✅ registry 容器已起"

  push_img() {
    local src="$1" repo="$2" tag="$3"
    if ! docker image inspect "$src" >/dev/null 2>&1; then
      echo "  ⚠️  缺镜像 $src（跳过）"; return 0
    fi
    docker tag "$src" "127.0.0.1:${REG_PORT}/${repo}:${tag}"
    docker push "127.0.0.1:${REG_PORT}/${repo}:${tag}" >/dev/null
    echo "  ✅ pushed ${repo}:${tag}"
  }
  # CI 的 task 镜像（worker 的 containerd 拉）
  push_img "docker:27-dind"                              library/docker       27-dind
  push_img "maven:3.9.11-eclipse-temurin-21"             library/maven        3.9.11-eclipse-temurin-21
  push_img "python:3.12-bookworm"                        library/python       3.12-bookworm
  # Testcontainers 用的裸名镜像（内层 daemon 经 mirror 机制落到 library/ 路径）
  push_img "postgres:16-alpine"                          library/postgres     16-alpine
  push_img "redis:7-alpine"                              library/redis        7-alpine
  # ⚠️ apache/kafka **不是** library/ 下的官方镜像 ⇒ registry 里按**原路径**查
  #    （实测 2026-10-30：推成 library/apache/kafka 时内层 daemon 报 404）
  push_img "apache/kafka:3.7.2"                          apache/kafka        3.7.2
else
  log "2/6 跳过本地 registry（用公网镜像源: ${REGISTRY}）"
fi

# --- 3. 起本地 Concourse ----------------------------------------------------
log "3/6 起本地 Concourse"
cd "$CONCOURSE_DIR"
docker compose up -d
for i in $(seq 1 60); do
  if curl -fsS --max-time 3 http://localhost:8080/api/v1/info >/dev/null 2>&1; then
    echo "  ✅ Concourse 已就绪"
    break
  fi
  [[ "$i" -eq 60 ]] && { echo "  ❌ 60 秒内没就绪"; docker compose logs --tail 50 concourse; exit 1; }
  sleep 2
done

# --- 4. fly 登录 ------------------------------------------------------------
log "4/6 fly 登录 target=${TARGET}"
if ! fly -t "$TARGET" targets 2>/dev/null | grep -q "$TARGET"; then
  fly -t "$TARGET" login -c http://localhost:8080 -u test -p test
fi
fly -t "$TARGET" status 2>/dev/null | head -5 || true

# --- 5. 注册 11 条流水线 ----------------------------------------------------
log "5/6 注册流水线（registry=${REGISTRY}）"
CONCOURSE_REGISTRY="$REGISTRY" bash "$ROOT_DIR/ci/deploy.sh"

# --- 6.（可选）触发 ---------------------------------------------------------
if [[ -n "$trigger_pipeline" ]]; then
  log "6/6 触发 ${trigger_pipeline}"
  fly -t "$TARGET" trigger-job -j "${trigger_pipeline}/build-and-test" --watch || true
else
  log "6/6 跳过触发（要触发请加 --trigger <pipeline>）"
  echo "  示例: bash ci/local-up.sh --trigger rag-task"
fi

echo
echo "完成。Web UI: http://localhost:8080 （test/test）"
echo "查看 worker: fly -t $TARGET workers"
echo "查看流水线: fly -t $TARGET pipelines"
