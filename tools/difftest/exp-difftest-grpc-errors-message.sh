#!/usr/bin/env bash
# ════════════════════════════════════════════════════════════════════════════
#  exp-difftest-grpc-errors-message — message 域 Go ↔ RAG 双端口对拍的编排脚本
# ════════════════════════════════════════════════════════════════════════════
#
# 【做什么】
#   复用 run.sh 的同一套编排起栈，然后用一个 Java 进程同时打 Go 与 RAG 的
#   message-rpc 端口，比对 11 档 gRPC status code + 1 组 happy path 响应体。
#   实际执行体是 grpc-message-dual-port-runner.java。
#
# 【与 exp-difftest-grpc-errors.sh（user 域）的关系】
#   同一 model、同一范式，只是把端口换成 message 域、把断言档换成 message 的 9 个 RPC。
#   之所以另起一个脚本而不是给 user 那个加参数：
#   两者的 proto 桩路径、classpath 来源、端口段都不同，参数化会让两边都变难读。
#
# 【为什么必须有这一层】
#   AGENTS.md §6.6 记录的 3 项 P0 里有 2 项（事务失效 / conversation_id 恒 0）
#   在**纯静态检查下无法证伪** —— 必须真的把请求打到两端。
#   而 message 域此前既没有 @SpringBootTest，也不在 difftest 范围内，
#   等于「从来没人跑过它」。本脚本把这条链路补上。
#
# 【配套】
#   - 静态半前提：tools/difftest/check-proto-static.sh（已跑且 ✓）
#   - 实际执行体：tools/difftest/grpc-message-dual-port-runner.java
#   - 栈编排：tools/difftest/run.sh --keep --serve-only
#
# 【用法】
#   ./exp-difftest-grpc-errors-message.sh                 # 起栈(若需要) + 跑实验
#   ./exp-difftest-grpc-errors-message.sh --stacks-up    # 假设栈已起来，只跑实验
#   GO_MESSAGE_PORT=29082 RAG_MESSAGE_PORT=69210 ./...   # 自定义端口

set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"

GO_MESSAGE_PORT="${GO_MESSAGE_PORT:-18082}"
RAG_MESSAGE_PORT="${RAG_MESSAGE_PORT:-58210}"
GO_HOST="${GO_HOST:-127.0.0.1}"
RAG_HOST="${RAG_HOST:-127.0.0.1}"

BRING_UP=1
for a in "$@"; do
  case "$a" in
    --stacks-up) BRING_UP=0 ;;
    *) echo "未知参数:$a(可选:--stacks-up)" >&2; exit 2 ;;
  esac
done

probe_grpc() {  # host, port
  (echo > /dev/tcp/"$1"/"$2") >/dev/null 2>&1
}

if [[ $BRING_UP -eq 1 ]]; then
  if probe_grpc "$GO_HOST"  "$GO_MESSAGE_PORT" && \
     probe_grpc "$RAG_HOST" "$RAG_MESSAGE_PORT"; then
    echo "==> [0/3] 两套 message-rpc 都已起来，跳过 run.sh"
  else
    echo "==> [0/3] 起两套栈（run.sh --keep --serve-only）..."
    echo "    Go  message-rpc = $GO_HOST:$GO_MESSAGE_PORT"
    echo "    RAG message-rpc = $RAG_HOST:$RAG_MESSAGE_PORT"
    echo "    (该命令前台阻塞；请另开 terminal 跑本脚本，且带 --stacks-up)"
    exec "$HERE/run.sh" --keep --serve-only
  fi
fi

echo "==> [1/3] 生成 proto 桩 + 计算 classpath"
PROTO_CP="$ROOT/service/message/message-proto/target/classes"
if [[ ! -d "$PROTO_CP" ]]; then
  (cd "$ROOT" && mvn -q -o -pl service/message/message-proto -am -DskipTests compile) \
    || { echo "  FAIL proto 编译失败"; exit 1; }
fi

LIBCP_FILE="/tmp/difftest-msg-grpc-cp.txt"
(cd "$ROOT" && mvn -q -o -pl service/message/message-rpc \
    dependency:build-classpath -Dmdep.outputFile="$LIBCP_FILE" >/dev/null 2>&1) \
  || { echo "  FAIL dependency:build-classpath 失败"; exit 1; }
LIBCP="$(cat "$LIBCP_FILE")"

echo "==> [2/3] 编译 GrpcMessageDualPortRunner"
BUILD_DIR="/tmp/difftest-msg-grpc-build"
rm -rf "$BUILD_DIR"; mkdir -p "$BUILD_DIR"
javac -d "$BUILD_DIR" -cp "$PROTO_CP:$LIBCP" \
      "$HERE/grpc-message-dual-port-runner.java" \
  || { echo "  FAIL 编译失败"; exit 1; }

echo "==> [3/3] 跑对拍"
echo "    Go  message-rpc = $GO_HOST:$GO_MESSAGE_PORT"
echo "    RAG message-rpc = $RAG_HOST:$RAG_MESSAGE_PORT"
java -cp "$BUILD_DIR:$PROTO_CP:$LIBCP" \
     -Dgo.host="$GO_HOST"   -Dgo.message.port="$GO_MESSAGE_PORT" \
     -Drag.host="$RAG_HOST" -Drag.message.port="$RAG_MESSAGE_PORT" \
     tools.difftest.GrpcMessageDualPortRunner 2>&1 | tee "$HERE/message-grpc-results.txt"

RC=${PIPESTATUS[0]}
echo
echo "exit code: $RC (0 = message 域双端口等价 / 1 = 至少一档不符)"
exit "$RC"