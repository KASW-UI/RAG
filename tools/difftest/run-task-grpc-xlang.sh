#!/usr/bin/env bash
# ═════════════════════════════════════════════════════════════════════════
# task 域 gRPC 层 **跨语言**活体对拍 —— Go 客户端同时打 Go 服务端与 RAG 服务端
#
#   Go client ──▶ Go  task-rpc (:19005)
#   Go client ──▶ RAG task-rpc (:59005)
#
# 【为什么需要它】
#   仓库里已有的 tools/difftest/GrpcTaskDualPortRunner.java 是 **Java 客户端**打两侧：
#     RAG client → Go server  （跨语言的一种方向）
#     RAG client → RAG server （同语言）
#   ⇒ **缺**「Go 客户端 → RAG 服务端」这一方向。
#   而这一方向测的是**另一种互操作风险**：grpc-go 与 grpc-java 的 wire 行为
#   （metadata / 压缩 / deadline / error 传播）、go-zero zrpc 客户端能不能把
#   RAG 端的错误码与描述原样带回来。⇒ 两个方向的互操作风险**不是同一件事**。
#
# 【为什么要在真源之外写这个程序】
#   冻结真源（Sea-RideTheWind_golang）**零写入**。
#   ⇒ 程序放在 tools/difftest/grpcxlang/main.go，用 go.mod 的 `replace` 引用真源的生成桩；
#     go.mod 的 replace 路径是**绝对路径**，不能写成相对路径（worktree 布局不稳定），
#     所以由本脚本**按本机路径生成** go.mod + 复制真源 go.sum。
#
# 【前置】
#   两侧 task-rpc 必须已起（tools/difftest/run-task-http-parity.sh 或
#   run-task-kafka-e2e.sh 的产物；两者都可用 KEEP_UP=1 保留）。
#
# 用法：
#   ./tools/difftest/run-task-grpc-xlang.sh
#   GO_REPO=/path/to/Sea-RideTheWind_golang ./…   # 自定义真源路径
#   GO_PORT=19005 RAG_PORT=59005 ./…              # 自定义端口
# ═════════════════════════════════════════════════════════════════════════
set -u

HERE=$(cd "$(dirname "$0")" && pwd)
WORK=${WORK:-${TMPDIR:-/tmp}/task-grpc-xlang}
GO_PORT=${GO_PORT:-19005}
RAG_PORT=${RAG_PORT:-59005}
HOST=${HOST:-127.0.0.1}

say() { printf '\n\033[1m==== %s ====\033[0m\n' "$*"; }

# ⚠️ Go 真源路径的推导**不能**假设「与 RAG 同级」：
#    worktree 的布局是 `<RAG.worktrees>/task`，与主仓 `<RAG>` 不同级。
find_go_repo() {
  local cands=()
  [ -n "${GO_REPO:-}" ] && cands+=("$GO_REPO")
  cands+=("$HERE/../../../Sea-RideTheWind_golang")
  cands+=("$HERE/../../../../Sea-RideTheWind_golang")
  local common
  common=$(git -C "$HERE/../.." rev-parse --git-common-dir 2>/dev/null) && \
    cands+=("$(cd "$(dirname "$common")/.." && pwd)/Sea-RideTheWind_golang")
  for c in "${cands[@]}"; do
    [ -d "$c/service/task/rpc/pb" ] && { (cd "$c" && pwd); return 0; }
  done
  return 1
}

say "0. 前置检查"
for c in docker go java; do
  command -v "$c" >/dev/null 2>&1 || { echo "  ✗ 缺命令：$c"; exit 1; }
done
GO_REPO=$(GO_REPO="${GO_REPO:-}" find_go_repo) \
  || { echo "  ✗ 找不到 Go 真源（可用 GO_REPO=... 指定）"; exit 1; }
echo "  GO_REPO = $GO_REPO"
mkdir -p "$WORK"

say "1. 生成 go.mod（replace 指向真源）+ 复制真源 go.sum"
cat > "$WORK/go.mod" <<EOF
module taskgrpcxlang

go 1.25.11

require (
	google.golang.org/grpc v1.81.1
	sea-try-go v0.0.0
)

replace sea-try-go => $GO_REPO
EOF
cp "$GO_REPO/go.sum" "$WORK/go.sum"
cp "$HERE/grpcxlang/main.go" "$WORK/main.go"
echo "  ✓ $WORK/go.mod"

say "2. 编译"
( cd "$WORK" && GOFLAGS=-mod=mod GOPROXY=off go build -o runner . ) \
  || { echo "  ✗ Go 编译失败（离线依赖没在 module cache 里？）"; exit 1; }
echo "  ✓ $WORK/runner"

say "3. 确认两侧 task-rpc 在监听"
for p in "$GO_PORT" "$RAG_PORT"; do
  if lsof -nP -iTCP:"$p" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "  ✓ :$p 在监听"
  else
    echo "  ✗ :$p 没在监听 —— 先起两侧 task-rpc"
    echo "    （例：KEEP_UP=1 ./tools/difftest/run-task-http-parity.sh）"
    exit 1
  fi
done

say "4. 跑跨语言对拍"
GO_PORT="$GO_PORT" RAG_PORT="$RAG_PORT" HOST="$HOST" "$WORK/runner"
RC=$?

say "5. 收摊"
echo "  ✓ 二进制保留在 $WORK/runner（复跑：GO_PORT=$GO_PORT RAG_PORT=$RAG_PORT $WORK/runner）"
exit $RC
