#!/usr/bin/env bash
# go-repo.sh —— **Go 真源仓定位的共享实现**（shell 版）。
#
# 🔴 为什么有它（2026-10-29 · G4 lane）：
#   同一个根因被三个 lane 独立报出（G1 `S1-FOLLOW-008`② / G2 假红 / G3 `G4-10`），
#   且 **G3 本轮新写的探针初版也踩了同一个坑**
#   ⇒ ⇒ **逐个脚本修 = 下一个新写的探针还会重犯**
#   ⇒ ⇒ **修法必须落在共享解析函数上**。
#
# 🔴 原始 bug 的形态：
#   旧默认路径 = `<RAG 的同级兄弟目录>/Sea-RideTheWind_golang`
#   ⇒ 在**主工作区**正确，在**工作树**（`.../RAG.worktrees/<lane>`）里恒指错
#   ⇒ ⇒ 「找不到 Go 仓」被当成「Go 侧 0 个端点」⇒ 假红 / 假绿。
#
# ✅ 判定口径（与 Python 版 `tools/difftest/go_repo.py` **逐条一致**）：
#   ① `GO_REPO` 环境变量优先 ⇒ 但**必须校验**它确实像 Go 真源仓
#      ⇒ 校验不过 ⇒ **显式报错退出**（不是「忽略它、继续用默认值」）。
#   ② 逐级向上搜索（最多 8 层）：每层先看 `Sea-RideTheWind_golang`（标准布局），
#      再看该层的其它子目录（worktree 布局）。
#   ③ 找不到 ⇒ **退出码 2**（绝不退化成「Go 侧 0 个端点」）。
#
# ⚠️ **判定标志只用「与域无关」的结构性文件**（`go.mod` + `proto/` + `service/`）
#    ⇒ ⇒ 不能用某个域专属文件当标志
#    ⇒ 否则「只跑 article 域的脚本」与「只跑 favorite 域的脚本」判据不同
#    ⇒ ⇒ 又变成「同一个仓、两个结论」（同族陷阱）。
#
# 【用法】
#   source "$(dirname "$0")/../../difftest/lib/go-repo.sh"     # 按需调整相对路径
#   GO_REPO="$(g4_resolve_go_repo "$RAG_ROOT")" || exit 2
#
# ⚠️ **路径里有 `&` 时必须加引号**（本仓路径含 `java&golang`）
#    ⇒ `GO_REPO="/Users/pr/Documents/java&golang/Sea-RideTheWind_golang"`

G4_GO_REPO_DIR_NAME="Sea-RideTheWind_golang"
G4_GO_REPO_MAX_LEVELS=8

# 判据：三个与域无关的结构性标志都要在。
g4_looks_like_go_repo() {
  local p="${1:-}"
  [ -n "$p" ] || return 1
  [ -f "${p}/go.mod" ] || return 1
  [ -d "${p}/proto" ] || return 1
  [ -d "${p}/service" ] || return 1
  return 0
}

# 定位 Go 真源仓 ⇒ stdout 打印路径；失败 ⇒ stderr 打印原因并 return 2。
# 用法：GO_REPO="$(g4_resolve_go_repo "$RAG_ROOT")" || exit 2
g4_resolve_go_repo() {
  local start="${1:-}"
  local d cand entry

  # ① 环境变量优先（**校验**）
  if [ -n "${GO_REPO:-}" ]; then
    if g4_looks_like_go_repo "$GO_REPO"; then
      printf '%s' "$GO_REPO"
      return 0
    fi
    echo "❌ GO_REPO 环境变量指向的目录**不是 Go 真源仓**：${GO_REPO}" >&2
    echo "   判据：必须同时含 go.mod / proto/ / service/。" >&2
    echo "   ⇒ 请把它指向 ${G4_GO_REPO_DIR_NAME} 的真实路径。" >&2
    return 2
  fi

  [ -n "$start" ] || start="$(pwd)"
  d="$(cd "$start" 2>/dev/null && pwd)" || d="$start"

  # ② 逐级向上搜索
  local i=0
  while [ "$i" -lt "$G4_GO_REPO_MAX_LEVELS" ]; do
    # 标准布局
    if g4_looks_like_go_repo "${d}/${G4_GO_REPO_DIR_NAME}"; then
      printf '%s' "${d}/${G4_GO_REPO_DIR_NAME}"
      return 0
    fi
    # 其它子目录（worktree 布局：RAG 与主仓同级）
    #   🔴 **必须要求目录名精确等于 Sea-RideTheWind_golang**
    #   ⇒ 否则会命中「名字里含这串的**别的**副本」（实测：/tmp/g1_go_probe_v1）
    #   ⇒ ⇒ 那不是本项目的真源 ⇒ ⇒ 拿它做对照 = **对着另一份代码判一致性** = 假绿。
    cand="${d}/${G4_GO_REPO_DIR_NAME}"
    if g4_looks_like_go_repo "$cand"; then
      printf '%s' "$cand"
      return 0
    fi
    local parent
    parent="$(dirname "$d")"
    [ "$parent" = "$d" ] && break
    d="$parent"
    i=$((i + 1))
  done

  # ③ 找不到 ⇒ 显式报错（**退出码 2**），绝不静默退化
  echo "❌ **找不到 Go 真源仓**（${G4_GO_REPO_DIR_NAME}）⇒ 无法做双侧对照。" >&2
  echo "   搜索起点：${start}" >&2
  echo "   ⇒ 请显式指定：GO_REPO=/绝对路径/${G4_GO_REPO_DIR_NAME}" >&2
  echo "   ⚠️ **不得在找不到真源时给出绿灯** —— 那会把「没查」读成「一致」。" >&2
  return 2
}
