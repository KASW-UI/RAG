#!/usr/bin/env bash
# =============================================================================
# ci/verify-triggers-server.sh —— **触发面服务端验证**（真实 Concourse + 真实 commit）
#
# 【它验什么】
#   任务书第五阶段·3：「修改单个微服务 ⇒ 只触发应受影响的 Pipeline；
#   修改共享模块 ⇒ 触发所有依赖它的 Pipeline；……**必须记录实际触发结果，
#   不要只根据 YAML 推断**。」
#   ⇒ 本脚本对**同一组改动**依次推送 commit ⇒ **从 Concourse 服务端**
#     读出「哪些 Pipeline 真的产生了新 build」⇒ 与期望逐条比对。
#
# 【为什么要用「轻量版」流水线】
#   实测 2026-10-31（两条硬事实）：
#   ① **暂停的 job 不会产生 build** —— 13 条流水线的 job 全部 `pause-job` 后，
#      推送 3 个 probe commit ⇒ **一条 build 都没产生**（调度器对暂停的 job
#      连资源检测都不做）⇒ 触发面**完全不可观测**。
#   ② **放开 job** ⇒ 每条流水线真跑 `mvn package` + `mvn test`（分钟级 × 13 条）
#      ⇒ 7 个场景 ≈ 50+ 次构建 ⇒ 算力与磁盘都不可接受
#      （且宿主磁盘曾因满盘导致 Docker/Concourse 反复崩溃）。
#   ⇒ 于是用**轻量版**：把每条流水线的 **`jobs:` 段**换成
#     「同样的 `get: repo` + `trigger: true` + 一个空任务」，
#     而 **`resources:` 段（含 `paths:`）逐字节照抄**。
#   ⇒ 被测性质 =「git 资源的 paths 过滤 + trigger」⇒ 与 job 体无关。
#   ⇒ 生成器 `ci/scripts/make-trigger-probe-pipelines.py` 会**逐字节断言**
#     paths 块一致 ⇒ 漂了立刻报错，不会拿假触发面做验证。
#
# 【前置】
#   · Concourse 服务端在跑（本地 8.3.2 @ localhost:8080，target `concourse`）
#   · 演练仓可推送：/private/tmp/gitd-rag.git（bare）+ /private/tmp/rag-ci-src（工作副本）
#   · ⚠️ 演练仓工作副本的 `origin` **误指向主仓** ⇒ 推送必须用**显式路径**
#
# 【用法】
#   bash ci/verify-triggers-server.sh            # 全流程（部署 → 验证 → 还原）
#   bash ci/verify-triggers-server.sh --keep     # 验证后**不**还原（便于继续排查）
# =============================================================================
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET="${CONCOURSE_TARGET:-concourse}"
GIT_URI="${CONCOURSE_GIT_URI:-git://172.17.0.1:5012/gitd-rag.git}"
GIT_BRANCH="${CONCOURSE_GIT_BRANCH:-ci-verify}"
REGISTRY="${CONCOURSE_REGISTRY:-172.17.0.1:5010}"
MAVEN_MIRROR="${CONCOURSE_MAVEN_MIRROR:-http://172.17.0.1:5013}"
CANONICAL_GIT_URI="${CANONICAL_GIT_URI:-https://github.com/KASW-UI/RAG.git}"
CANONICAL_GIT_BRANCH="${CANONICAL_GIT_BRANCH:-do-my-own-gh}"
KEEP=0
[[ "${1:-}" == "--keep" ]] && KEEP=1

LITE_DIR="$(mktemp -d)"
RESTORE_NEEDED=0

restore_canonical() {
  [ "$RESTORE_NEEDED" = "1" ] || return 0
  echo
  echo "==> 还原 canonical 流水线（指向 ${CANONICAL_GIT_URI} @ ${CANONICAL_GIT_BRANCH} 且 paused）"
  for f in "${REPO_ROOT}"/ci/pipelines/rag-*.yml; do
    n="$(basename "$f" .yml)"
    fly -t "$TARGET" set-pipeline -p "$n" -c "$f" \
      -v registry="$REGISTRY" -v maven_mirror="$MAVEN_MIRROR" \
      -v git_uri="$CANONICAL_GIT_URI" -v git_branch="$CANONICAL_GIT_BRANCH" -n >/dev/null 2>&1 \
      && fly -t "$TARGET" pause-pipeline -p "$n" >/dev/null 2>&1 \
      && echo "   ✅ $n → canonical + paused" \
      || echo "   ❌ $n 还原失败（**需要人工处理**）"
  done
  RESTORE_NEEDED=0
}
trap 'restore_canonical' EXIT INT TERM

echo "==> 1/4 生成轻量版流水线（paths 块逐字节照抄）"
python3 "${REPO_ROOT}/ci/scripts/make-trigger-probe-pipelines.py" \
        "${REPO_ROOT}/ci/pipelines" "$LITE_DIR" || exit 1

echo
echo "==> 2/4 部署轻量版（同名覆盖）→ 演练仓"
RESTORE_NEEDED=1
for f in "$LITE_DIR"/rag-*.yml; do
  n="$(basename "$f" .yml)"
  fly -t "$TARGET" set-pipeline -p "$n" -c "$f" \
    -v registry="$REGISTRY" -v maven_mirror="$MAVEN_MIRROR" \
    -v git_uri="$GIT_URI" -v git_branch="$GIT_BRANCH" -n >/dev/null 2>&1 \
    || { echo "   ❌ $n 部署失败"; exit 1; }
  fly -t "$TARGET" unpause-pipeline -p "$n" >/dev/null 2>&1
  fly -t "$TARGET" unpause-job -j "$n/$(python3 - "$f" <<'PY'
import re, sys
print(re.search(r"- name: (\S+)", open(sys.argv[1], encoding="utf-8").read().split("jobs:")[1]).group(1))
PY
)" >/dev/null 2>&1
done
echo "   ✅ 13 条轻量版已部署并放开"

echo
echo "==> 3/4 服务端触发面验证（真实 commit ⇒ 真实 build）"
( cd "$REPO_ROOT" && CONCOURSE_TARGET="$TARGET" bash ci/verify-triggers.sh )
RC=$?

echo
echo "==> 4/4 收尾"
if [ "$KEEP" = "1" ]; then
  echo "   ⚠️ --keep：**不还原** canonical（当前部署的是轻量版）"
  RESTORE_NEEDED=0
else
  restore_canonical
fi
echo
echo "==> 服务端触发面验证：EXIT=$RC"
exit "$RC"
