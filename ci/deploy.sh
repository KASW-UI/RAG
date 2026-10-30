#!/usr/bin/env bash
# =============================================================================
# ci/deploy.sh —— 把 11 条流水线注册到本地 Concourse（幂等）
#
# 用法：
#   ci/deploy.sh                 # 校验 + 注册 + 解除暂停
#   ci/deploy.sh --validate-only # 只跑 fly validate-pipeline（不需要服务端）
#
# 依赖：fly 已登录到 target（默认 concourse）
#   fly -t concourse login -c http://localhost:8080 -u test -p test
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET="${CONCOURSE_TARGET:-concourse}"
PIPELINE_DIR="$ROOT_DIR/ci/pipelines"

# 镜像源（Concourse pipeline var `registry`）：
#   · 公网镜像源   → docker.m.daocloud.io（默认）
#   · 本地 registry → <host:port>（带端口 ⇒ 内层 DinD daemon 按 HTTP + insecure 处理）
# 环境变量 CONCOURSE_REGISTRY 可覆盖。
REGISTRY="${CONCOURSE_REGISTRY:-docker.m.daocloud.io}"

# Maven 镜像源（Concourse pipeline var `maven_mirror`）：
#   留空 = 走默认 Maven Central；填内网 Nexus / 本地静态镜像则优先生效。
#   环境变量 CONCOURSE_MAVEN_MIRROR 可覆盖。
MAVEN_MIRROR="${CONCOURSE_MAVEN_MIRROR:-}"

validate_only=0
if [[ "${1:-}" == "--validate-only" ]]; then
  validate_only=1
fi

echo "==> 0/3 生成物一致性检查（触发面 vs pom 依赖图）"
python3 "$ROOT_DIR/ci/generate-pipelines.py" --check

echo "==> 1/3 fly validate-pipeline（客户端侧校验，不需要服务端）"
fail=0
for f in "$PIPELINE_DIR"/*.yml; do
  name="$(basename "$f")"
  if out="$(fly validate-pipeline -c "$f" 2>&1)" && grep -q "looks good" <<<"$out"; then
    printf '    ✅ %s\n' "$name"
  else
    printf '    ❌ %s\n%s\n' "$name" "$out"
    fail=1
  fi
done
[[ "$fail" -eq 0 ]] || { echo "校验未通过，终止。"; exit 1; }

if [[ "$validate_only" -eq 1 ]]; then
  echo "==> 只校验模式，结束。"
  exit 0
fi

echo "==> 2/3 set-pipeline（registry var = ${REGISTRY}｜maven_mirror var = ${MAVEN_MIRROR:-<空，走 Central>}）"
for f in "$PIPELINE_DIR"/*.yml; do
  name="$(basename "$f" .yml)"
  fly -t "$TARGET" set-pipeline -p "$name" -c "$f" \
    -v "registry=${REGISTRY}" -v "maven_mirror=${MAVEN_MIRROR}" -n
done

echo "==> 3/3 unpause-pipeline"
for f in "$PIPELINE_DIR"/*.yml; do
  name="$(basename "$f" .yml)"
  fly -t "$TARGET" unpause-pipeline -p "$name"
done

echo
echo "完成。已注册流水线："
fly -t "$TARGET" pipelines
echo
echo "手动触发某一条："
echo "  fly -t $TARGET trigger-job -j rag-article/build-and-test --watch"
