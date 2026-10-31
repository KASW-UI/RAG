#!/usr/bin/env bash
# =============================================================================
# ci/deploy.sh —— 把 13 条流水线（11 日常 + 2 契约）注册到本地 Concourse（幂等）
#
# 用法：
#   ci/deploy.sh                 # 校验 + 注册 + 解除暂停
#   ci/deploy.sh --validate-only # 只跑 fly validate-pipeline（不需要服务端）
#
# 依赖：fly 已登录到 target（默认 concourse）
#   fly -t concourse login -c http://localhost:8080 -u test -p test
#
# ⚠️ **本脚本会解除暂停** ⇒ 适合「本地起流、随便跑跑」。
#    ⛔ **canonical（指向 GitHub `do-my-own-gh`）的最终状态必须是 paused**
#    ⇒ 否则别的 lane 一推 commit 就会触发 build 并在第一步就红 ⇒ 是噪声不是信号。
#    ⇒ 收尾时用 `ci/verify-triggers-server.sh`（它自带 trap ⇒ 无论成败都还原 paused）。
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

# git 资源地址（Concourse pipeline var `git_uri` / `git_branch`）：
#   ⚠️ 走 var 而不是写死在流水线里 —— 触发面验证要用**真实服务端 + 真实 commit**
#   （任务书第五阶段·3），而验证需要一个**可推送的远端** ⇒ 指向本地演练仓。
#   默认 = GitHub 上的生产仓与分支；演练时用 CONCOURSE_GIT_URI / CONCOURSE_GIT_BRANCH 覆盖。
GIT_URI="${CONCOURSE_GIT_URI:-https://github.com/KASW-UI/RAG.git}"
GIT_BRANCH="${CONCOURSE_GIT_BRANCH:-do-my-own-gh}"

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

echo "==> 2/3 set-pipeline（registry var = ${REGISTRY}｜maven_mirror var = ${MAVEN_MIRROR:-<空，走 Central>}｜git = ${GIT_URI}#${GIT_BRANCH}）"
for f in "$PIPELINE_DIR"/*.yml; do
  name="$(basename "$f" .yml)"
  fly -t "$TARGET" set-pipeline -p "$name" -c "$f" \
    -v "registry=${REGISTRY}" -v "maven_mirror=${MAVEN_MIRROR}" \
    -v "git_uri=${GIT_URI}" -v "git_branch=${GIT_BRANCH}" -n
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
