#!/usr/bin/env bash
# =============================================================================
# ci/verify-triggers.sh —— **变更触发验证**（真实 Concourse 服务端 + 真实 commit）
#
# 【它验什么】
#   任务书第五阶段·3：「修改单个微服务 ⇒ 只触发应受影响的 Pipeline；
#   修改共享模块 ⇒ 触发所有依赖它的 Pipeline；……必须记录实际触发结果，
#   不要只根据 YAML 推断。」
#   ⇒ 本脚本对**同一组改动**依次推送 commit，然后**从 Concourse 服务端**
#     读出「哪些 Pipeline 真的产生了新 build」⇒ 与期望逐条比对。
#
# 【前提】
#   · Concourse 服务端已跑（本地 8.3.2 @ localhost:8080，target `concourse`）
#   · 流水线已部署且 **git_uri / git_branch 指向演练仓**（可推送）
#   · 演练仓：/private/tmp/gitd-rag.git（bare）+ /private/tmp/rag-ci-src（工作副本）
#   · ⚠️ 演练仓工作副本的 `origin` **误指向主仓** ⇒ 推送必须用**显式路径**
#     （`git push /private/tmp/gitd-rag.git <branch>:<branch>`），不能用 origin。
#
# 【用法】
#   bash ci/verify-triggers.sh            # 跑全部场景
#   bash ci/verify-triggers.sh --dry      # 只打印场景，不推送
# =============================================================================
set -uo pipefail

TARGET="${CONCOURSE_TARGET:-concourse}"
BARE="${CONCOURSE_BARE:-/private/tmp/gitd-rag.git}"
WORK="${CONCOURSE_WORK:-/private/tmp/rag-ci-src}"
BRANCH="${CONCOURSE_BRANCH:-ci-verify}"
DRY=0
[[ "${1:-}" == "--dry" ]] && DRY=1

# ---- 场景表：标题 | 要改的路径 | 期望触发的 pipeline（空格分隔，逗号表示全部）----
scenarios=(
  "改共享层 service/common/**|service/common/src/main/java/com/rag/common/Probe.java|ALL"
  "改 users-common|service/users/users-common/src/main/java/com/rag/users/common/Probe.java|rag-article rag-comment rag-favorite rag-follow rag-like rag-message rag-shared-common rag-users-admin rag-users-user"
  "改跨域 proto（users/user）|service/users/user/user-proto/src/main/proto/users.proto|rag-article rag-favorite rag-follow rag-message rag-users-admin rag-users-user"
  "改跨域 proto（article）|service/article/article-proto/src/main/proto/article.proto|rag-article rag-comment rag-favorite rag-hot"
  "改上游域祖先 pom（service/users/pom.xml）|service/users/pom.xml|rag-article rag-comment rag-favorite rag-follow rag-like rag-message rag-shared-common rag-users-admin rag-users-user"
  "改 ci/**|ci/scripts/collect-surefire.sh|ALL"
  "改无关目录|docs/Stage1/01-原始项目工程基线报告.md|NONE"
)

snapshot_builds() {
  # ⚠️ `fly builds --json` 的字段是 **id**（不是 build_id）· pipeline_name 是流水线名
  fly -t "$TARGET" builds --json 2>/dev/null \
    | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
except Exception:
    sys.exit(0)
for b in d:
    print(f\"{b.get('pipeline_name','?')}#{b.get('id','?')}\")
" \
    | sort
}

echo "==> 0/4 记录基线 build 列表"
BASE_BUILDS="$(snapshot_builds)"
echo "    当前 build 总数: $(echo "$BASE_BUILDS" | grep -c . || true)"

for entry in "${scenarios[@]}"; do
  IFS='|' read -r title path expect <<<"$entry"
  echo
  echo "==> 场景: $title   (改 $path)"
  if [[ "$DRY" == "1" ]]; then echo "    [dry] 期望触发: $expect"; continue; fi

  # 1) 在演练仓工作副本里造一次改动
  ( cd "$WORK" && git checkout -q "$BRANCH" 2>/dev/null || git checkout -q -B "$BRANCH" )
  mkdir -p "$(dirname "$WORK/$path")" 2>/dev/null || true
  if [[ -f "$WORK/$path" ]]; then
    printf '\n<!-- trigger-probe %s -->\n' "$(date +%s)" >> "$WORK/$path"
  else
    mkdir -p "$(dirname "$WORK/$path")"
    printf '<!-- trigger-probe %s -->\n' "$(date +%s)" > "$WORK/$path"
  fi
  ( cd "$WORK" && git add -A "$path" 2>/dev/null || git add -A )
  ( cd "$WORK" && git -c user.email=ci@local -c user.name=ci commit -q -m "ci(trigger-probe): $title" )

  # 2) 推给演练仓（⚠️ 必须用显式路径，origin 指向主仓）
  ( cd "$WORK" && git push -q "$BARE" "$BRANCH:$BRANCH" ) || { echo "    ❌ push 失败"; continue; }

  # 3) 等 Concourse 检出并排 build
  echo "    等待 Concourse 产生新 build（最多 45s）..."
  for _ in $(seq 1 45); do
    sleep 1
    NEW="$(snapshot_builds | comm -13 <(echo "$BASE_BUILDS") <(echo "$(snapshot_builds)"))"
    if [[ -n "$NEW" ]]; then break; fi
  done
  ACTUAL="$(snapshot_builds | comm -13 <(echo "$BASE_BUILDS") - | awk -F'#' '{print $1}' | sort -u | tr '\n' ' ' | sed 's/ $//')"
  BASE_BUILDS="$(snapshot_builds)"

  # 4) 比对
  if [[ "$expect" == "ALL" ]]; then
    ok=$([[ -n "$ACTUAL" ]] && echo 1 || echo 0)
  elif [[ "$expect" == "NONE" ]]; then
    ok=$([[ -z "$ACTUAL" ]] && echo 1 || echo 0)
  else
    exp_sorted="$(echo "$expect" | tr ' ' '\n' | sort -u | tr '\n' ' ' | sed 's/ $//')"
    act_sorted="$(echo "$ACTUAL" | tr ' ' '\n' | sort -u | tr '\n' ' ' | sed 's/ $//')"
    ok=$([[ "$exp_sorted" == "$act_sorted" ]] && echo 1 || echo 0)
  fi
  echo "    实际触发: ${ACTUAL:-（无）}"
  echo "    期望触发: ${expect}"
  [[ "$ok" == "1" ]] && echo "    ✅ 符合预期" || echo "    ❌ 不符"
done
