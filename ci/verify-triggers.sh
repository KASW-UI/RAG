#!/usr/bin/env bash
# =============================================================================
# ci/verify-triggers.sh —— **变更触发验证**
#
# 【两种模式】
#   --static   纯静态：从 ci/pipelines/*.yml 的 git resource `paths` 算出
#              「哪些流水线会被这次改动触发」⇒ 与**场景表里的期望集合**逐条比对。
#              ⛔ 不连 Concourse ⇒ 只证明「流水线定义内部自洽」；
#              ✅ 优点：秒级、可进静态守门 ⇒ 能挡住「生成器改了但场景表没改」的漂移。
#   （默认）   真实服务端：对同一组改动依次推送 commit ⇒ **从 Concourse 服务端**
#              读出「哪些 Pipeline 真的产生了新 build」⇒ 与期望逐条比对。
#              ✅ 这才是任务书要求的「记录实际触发结果，不要只根据 YAML 推断」。
#
# 【场景表 = 意图】
#   场景表里的期望集合是**手工推导的「应该触发谁」**（依据 Go/Java 的 pom 依赖图
#   与跨域调用边的分析）⇒ 它代表**意图**。
#   而 ci/pipelines/*.yml 的 paths 是**生成器算出来的实现**。
#   ⇒ --static 模式 = 「实现 vs 意图」的一致性检查 ⇒ 生成器漂移会被当场抓住。
#   ⇒ 服务端模式 = 「Concourse 实际行为 vs 意图」 ⇒ 端到端证据。
#
# 【前提（仅服务端模式）】
#   · Concourse 服务端已跑（本地 8.3.2 @ localhost:8080，target `concourse`）
#   · 流水线已部署且 **git_uri / git_branch 指向演练仓**（可推送）且 **已 unpause**
#     （⚠️ `fly set-pipeline` 保留暂停态 ⇒ 忘了 unpause ⇒ build 永远 pending）
#   · 演练仓：/private/tmp/gitd-rag.git（bare）+ /private/tmp/rag-ci-src（工作副本）
#   · ⚠️ 演练仓工作副本的 `origin` **误指向主仓** ⇒ 推送必须用**显式路径**
#     （`git push /private/tmp/gitd-rag.git <branch>:<branch>`），不能用 origin。
#
# 【用法】
#   bash ci/verify-triggers.sh --static    # 只做静态一致性检查（不连服务端）
#   bash ci/verify-triggers.sh --dry       # 只打印场景，不推送
#   bash ci/verify-triggers.sh             # 跑全部场景（真实服务端）
# =============================================================================
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET="${CONCOURSE_TARGET:-concourse}"
BARE="${CONCOURSE_BARE:-/private/tmp/gitd-rag.git}"
WORK="${CONCOURSE_WORK:-/private/tmp/rag-ci-src}"
BRANCH="${CONCOURSE_BRANCH:-ci-verify}"
MODE="server"
[[ "${1:-}" == "--dry" ]] && MODE="dry"
[[ "${1:-}" == "--static" ]] && MODE="static"

# ---- 场景表：标题 | 要改的路径 | 期望触发的 pipeline 集合（空格分隔；逗号=全部）----
# ⚠️ 期望集合是**意图**的机器可读表达 ⇒ 改它等于改需求，必须同步改文档。
# ⚠️ 「ALL」保留给「改 ci/** ⇒ 所有流水线」这种真·全量场景；
#    其余一律写**精确集合** ⇒ 这样才能验出「多触发」和「少触发」两种漂移。
scenarios=(
  "改共享层 service/common/**|service/common/src/main/java/com/rag/common/Probe.java|rag-article rag-comment rag-favorite rag-follow rag-hot rag-like rag-message rag-shared-common rag-task rag-users-admin rag-users-user"
  "改 users-common|service/users/users-common/src/main/java/com/rag/users/common/Probe.java|rag-article rag-comment rag-favorite rag-follow rag-like rag-message rag-shared-common rag-users-admin rag-users-user"
  "改跨域 proto（users/user）|service/users/user/user-proto/src/main/proto/users.proto|rag-article rag-favorite rag-follow rag-message rag-users-admin rag-users-user"
  "改跨域 proto（article）|service/article/article-proto/src/main/proto/article.proto|rag-article rag-comment rag-favorite rag-hot"
  "改上游域祖先 pom（service/users/pom.xml）|service/users/pom.xml|rag-article rag-comment rag-favorite rag-follow rag-like rag-message rag-shared-common rag-users-admin rag-users-user"
  "改 ci/**|ci/scripts/collect-surefire.sh|ALL"
  "改无关目录|docs/Stage1/01-原始项目工程基线报告.md|NONE"
)

# -----------------------------------------------------------------------------
# 静态模式：算出「按 paths 应该被触发」的集合
# -----------------------------------------------------------------------------
static_expected_set() {
  local changed_path="$1"
  python3 - "$REPO_ROOT" "$changed_path" <<'PY'
import re, sys, pathlib, fnmatch

root = pathlib.Path(sys.argv[1])
changed = sys.argv[2]

def glob_to_re(p: str) -> re.Pattern:
    """把 Concourse git resource 的 paths glob 转成正则（doublestar 语义）。"""
    i, out, n = 0, [], len(p)
    while i < n:
        c = p[i]
        if c == '*':
            if i + 1 < n and p[i + 1] == '*':
                j = i + 2
                if j < n and p[j] == '/':      # `**/` ⇒ 匹配零或多层目录
                    out.append(r'(?:.*/)?')
                    i = j + 1
                    continue
                out.append(r'.*')              # 结尾或独立 `**` ⇒ 跨分隔符
                i = j
                continue
            out.append(r'[^/]*')                # 单个 `*` 不跨 `/`
            i += 1
            continue
        if c == '?':
            out.append(r'[^/]')
            i += 1
            continue
        out.append(re.escape(c))
        i += 1
    return re.compile(r'\A' + ''.join(out) + r'\Z')

hits = []
for f in sorted(root.glob('ci/pipelines/rag-*.yml')):
    txt = f.read_text(encoding='utf-8')
    m = re.search(r'\n\s*paths:\n((?:\s*-\s*\S+\n)+)', txt)
    if not m:
        continue
    paths = [ln.strip().lstrip('- ').strip().strip('"')
             for ln in m.group(1).strip().splitlines()]
    if any(glob_to_re(p).match(changed) for p in paths):
        hits.append(f.stem)
print(' '.join(sorted(hits)))
PY
}

all_pipeline_names() {
  python3 - "$REPO_ROOT" <<'PY'
import sys, pathlib
root = pathlib.Path(sys.argv[1])
print(' '.join(sorted(f.stem for f in root.glob('ci/pipelines/rag-*.yml'))))
PY
}

normalize_set() {
  echo "$1" | tr ' ' '\n' | grep -v '^$' | sort -u | tr '\n' ' ' | sed 's/ $//'
}

# -----------------------------------------------------------------------------
# 服务端模式：读 Concourse 的 build 列表
# -----------------------------------------------------------------------------
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

# ── 等 build 集合「稳定」 ────────────────────────────────────────────────────
# ⚠️ 见主流程里的说明：13 条流水线各有自己的 git resource 实例 ⇒ 检查时刻散开
#   ⇒ 必须等集合**稳定**，不能等「第一个 build 出现」。
SETTLE_MIN_SECS="${SETTLE_MIN_SECS:-90}"
SETTLE_STABLE_CHECKS="${SETTLE_STABLE_CHECKS:-3}"
SETTLE_POLL_SECS="${SETTLE_POLL_SECS:-10}"
SETTLE_MAX_SECS="${SETTLE_MAX_SECS:-240}"

wait_for_settle() {
  local start now prev="" cur stable=0
  start="$(date +%s)"
  prev="$(snapshot_builds)"
  while true; do
    sleep "$SETTLE_POLL_SECS"
    cur="$(snapshot_builds)"
    now="$(date +%s)"
    if [ "$cur" == "$prev" ]; then
      stable=$((stable + 1))
    else
      stable=0
    fi
    prev="$cur"
    local elapsed=$((now - start))
    if [ "$stable" -ge "$SETTLE_STABLE_CHECKS" ] && [ "$elapsed" -ge "$SETTLE_MIN_SECS" ]; then
      echo "    （已稳定：${elapsed}s，集合连续 ${stable} 次无变化）"
      return 0
    fi
    if [ "$elapsed" -ge "$SETTLE_MAX_SECS" ]; then
      echo "    ⚠️ 达到上限 ${SETTLE_MAX_SECS}s 仍未判稳（集合连续 ${stable} 次无变化）⇒ 用当前读数"
      return 0
    fi
  done
}

# -----------------------------------------------------------------------------
# 统一比对
# -----------------------------------------------------------------------------
# 期望集合 → 具体集合；ALL ⇒ 全量；NONE ⇒ 空
resolve_expect() {
  case "$1" in
    ALL)  all_pipeline_names ;;
    NONE) echo "" ;;
    *)    echo "$1" ;;
  esac
}

FAILURES=0

compare() {
  local title="$1" actual="$2" expect_raw="$3"
  local expect_set actual_set ok
  expect_set="$(normalize_set "$(resolve_expect "$expect_raw")")"
  actual_set="$(normalize_set "$actual")"
  if [ "$expect_set" == "$actual_set" ]; then
    ok=1
  else
    ok=0
  fi
  echo "    实际触发: ${actual_set:-（无）}"
  echo "    期望触发: ${expect_set:-（无）}"
  if [ "$ok" == "1" ]; then
    echo "    ✅ 符合预期"
  else
    echo "    ❌ 不符"
    local only_actual only_expect
    only_actual="$(comm -13 <(echo "$expect_set" | tr ' ' '\n') <(echo "$actual_set" | tr ' ' '\n') | tr '\n' ' ')"
    only_expect="$(comm -23 <(echo "$expect_set" | tr ' ' '\n') <(echo "$actual_set" | tr ' ' '\n') | tr '\n' ' ')"
    [ -n "$only_actual" ] && echo "    ↳ 多触发: $only_actual"
    [ -n "$only_expect" ] && echo "    ↳ 少触发: $only_expect"
    FAILURES=$((FAILURES + 1))
  fi
}

# -----------------------------------------------------------------------------
# 主流程
# -----------------------------------------------------------------------------
if [ "$MODE" == "static" ]; then
  echo "==> 静态模式：从 ci/pipelines/*.yml 的 paths 计算触发集合，与场景表（意图）比对"
  for entry in "${scenarios[@]}"; do
    IFS='|' read -r title path expect <<<"$entry"
    echo
    echo "==> 场景: $title   (改 $path)"
    actual="$(static_expected_set "$path")"
    compare "$title" "$actual" "$expect"
  done
  echo
  echo "==> 静态触发面一致性：失败 $FAILURES 个场景"
  [ "$FAILURES" -eq 0 ] || exit 1
  exit 0
fi

echo "==> 0/4 记录基线 build 列表"
BASE_BUILDS="$(snapshot_builds)"
echo "    当前 build 总数: $(echo "$BASE_BUILDS" | grep -c . || true)"
# ⚠️ 先等基线**稳定**再开跑：否则上一轮遗留的 build 会在第一个场景里陆续出现
#   ⇒ 会被误算成「本次触发的」⇒ 读数串场（实测踩过）。
echo "    等待基线稳定..."
wait_for_settle
BASE_BUILDS="$(snapshot_builds)"
echo "    稳定后 build 总数: $(echo "$BASE_BUILDS" | grep -c . || true)"

for entry in "${scenarios[@]}"; do
  IFS='|' read -r title path expect <<<"$entry"
  echo
  echo "==> 场景: $title   (改 $path)"
  if [ "$MODE" == "dry" ]; then echo "    [dry] 期望触发: $(resolve_expect "$expect")"; continue; fi

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
  ( cd "$WORK" && git push -q "$BARE" "$BRANCH:$BRANCH" ) || { echo "    ❌ push 失败"; FAILURES=$((FAILURES+1)); continue; }

  # 3) 等 Concourse 检出并排 build
  # ⚠️⚠️ **必须等「集合稳定」，不能等「第一个 build 出现」**（实测 2026-10-31 踩过）：
  #   · 13 条流水线各有**自己的** git resource 实例 ⇒ 检查时刻**散开**
  #     ⇒ 「等第一个 build」会在只有一部分流水线检出时就快照 ⇒ 得到**残缺**的触发集合；
  #   · 更糟的是**上一场景的 build 会在下一场景的窗口里陆续出现**
  #     ⇒ 实际触发集合会**串场**（实测读数整体错位一格）。
  #   ⇒ 判据 =「集合**连续 3 次快照（30s）无变化**」**且**「距推送已过 ≥90s（≥1.5 个资源检查周期）」
  #   ⇒ 上限 240s ⇒ 宁可等久一点，不要测出一个假的不符。
  echo "    等待 Concourse 的 build 集合**稳定**（≥90s 且连续 30s 无变化，上限 240s）..."
  wait_for_settle
  ACTUAL="$(snapshot_builds | comm -13 <(echo "$BASE_BUILDS") - | awk -F'#' '{print $1}' | sort -u | tr '\n' ' ' | sed 's/ $//')"
  BASE_BUILDS="$(snapshot_builds)"

  # 4) 比对
  compare "$title" "$ACTUAL" "$expect"
done

echo
echo "==> 服务端触发面：失败 $FAILURES 个场景"
[ "$FAILURES" -eq 0 ] || exit 1
exit 0
