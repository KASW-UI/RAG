#!/usr/bin/env bash
# ════════════════════════════════════════════════════════════════════════
#  probe-go-omitempty — 判定 Go 的 `omitempty` 对 **struct** 是否生效
# ════════════════════════════════════════════════════════════════════════
#
# 【要判定什么】
#   Go 真源 service/user/admin/api/internal/types/types.go:71-74
#       type GetUserResp struct {
#           User  UserInfo `json:"user,omitempty"`   ← UserInfo 是 **struct**
#           Found bool     `json:"found"`
#       }
#   ⇒ Go 的 `encoding/json` 里 `omitempty` 对 **struct 无效**
#     （它只对 slice/map/string/number/bool/pointer/interface 生效），
#     所以零值 `UserInfo` **一定会被输出**。
#   ⇒ 这是 DRIFT-ADMINAPI-005（下游未 set user 时字段整个消失）的判定支点。
#
# 【为什么不能自己写一个 struct 来测】
#   自己定义一个同名同 tag 的 struct，等于「把自己的期望当成被验证对象」——
#   测的就不是真源的类型定义了。
#   ⇒ 本脚本**逐字节复制真源的 types.go** 再测，并打印两边的 sha256 供核对。
#   ⚠️ 真源仓库是**冻结只读**的（AGENTS.md §5.4），所以不能在里面建 main 包
#      （`internal/types` 只能被 `service/user/admin/api/` 及其子目录 import）。
#   ⇒ 复制到临时模块是唯一可行且不污染真源的做法。
#
# 【实测输出（2026-10-24，go1.25.11 darwin/arm64）】
#   SCENARIO=zero_getUserResp  JSON={"user":{"uid":"0","username":"","status":0},"found":false}
#   SCENARIO=found_only        JSON={"user":{"uid":"0","username":"","status":0},"found":true}
#   SCENARIO=user_set          JSON={"user":{"uid":"7701","username":"x","status":1},"found":true}
#   SCENARIO=map_empty         JSON={}          ← 对照：map 形态才会被省略
#
# 【结论】
#   零值 user 必输出，且其中 uid/username/status 三个字段**无 omitempty** ⇒ 也输出零值；
#   只有 email / extra_info 因带 omitempty 且为空而省略。
#   ⇒ 与 RAG 侧 `UserInfo` DTO 的字段级行为**逐条一致**
#     （uid 转字符串、email/extra_info 用 NON_NULL 对应 omitempty）；
#   ⇒ 唯一差异是**外层 `user` 字段**：Go 必输出、RAG 的 `@JsonInclude(NON_NULL)` 会省略。
#   ⇒ 但该路径**前提不可达**（admin-rpc 查不到用户时返回 NotFound ⇒ api 层转
#     `ErrorUserNotExist`，根本不会返回零值 user）⇒ 不构成可观察差异。
#
# 用法：
#   bash tools/difftest/probe-go-omitempty.sh
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"

# Go 真源仓库的位置。⚠️ **不能用 `$ROOT/../Sea-RideTheWind_golang` 算** ——
#   worktree 挂在 `RAG.worktrees/<域>/` 下，它的父目录不是主仓位置 ⇒ 路径会算错。
#   ⇒ 依次探测候选路径，也允许用环境变量 GO_REPO 显式指定。
GO_REPO="${GO_REPO:-}"
if [[ -z "$GO_REPO" ]]; then
  for cand in \
      "/Users/pr/Documents/java&golang/Sea-RideTheWind_golang" \
      "$ROOT/../Sea-RideTheWind_golang" \
      "$HOME/Documents/java&golang/Sea-RideTheWind_golang"; do
    if [[ -d "$cand/service/user/admin/api/internal/types" ]]; then
      GO_REPO="$cand"
      break
    fi
  done
fi
GO_SRC="$GO_REPO/service/user/admin/api/internal/types/types.go"
WORK=/tmp/goprobe-omitempty

if [[ ! -f "$GO_SRC" ]]; then
  echo "找不到 Go 真源的 types.go：$GO_SRC" >&2
  echo "  用 GO_REPO=<Sea-RideTheWind_golang 路径> 指定即可" >&2
  exit 2
fi
if ! command -v go >/dev/null 2>&1; then
  echo "本机没有 go 可执行文件，跳过" >&2
  exit 2
fi

rm -rf "$WORK"
mkdir -p "$WORK/types"
cp "$GO_SRC" "$WORK/types/types.go"

echo "== 副本与真源的一致性（逐字节）=="
shasum -a 256 "$GO_SRC" "$WORK/types/types.go" | sed 's/^/  /'
if ! diff -q "$GO_SRC" "$WORK/types/types.go" >/dev/null; then
  echo "  ✗ 副本与真源不一致 —— 判定无效" >&2
  exit 1
fi
echo "  ✅ 逐字节相同"

cat > "$WORK/go.mod" << 'EOF'
module omitemptyprobe

go 1.25
EOF

cat > "$WORK/main.go" << 'EOF'
package main

import (
	"encoding/json"
	"fmt"

	"omitemptyprobe/types"
)

func main() {
	b, _ := json.Marshal(types.GetUserResp{})
	fmt.Printf("SCENARIO=zero_getUserResp  JSON=%s\n", string(b))

	b2, _ := json.Marshal(types.GetUserResp{Found: true})
	fmt.Printf("SCENARIO=found_only        JSON=%s\n", string(b2))

	b3, _ := json.Marshal(types.GetUserResp{
		User:  types.UserInfo{Uid: 7701, Username: "x", Status: 1},
		Found: true,
	})
	fmt.Printf("SCENARIO=user_set          JSON=%s\n", string(b3))

	m := map[string]types.UserInfo{}
	b4, _ := json.Marshal(m)
	fmt.Printf("SCENARIO=map_empty         JSON=%s\n", string(b4))
}
EOF

echo "== go run =="
(cd "$WORK" && go run .)
