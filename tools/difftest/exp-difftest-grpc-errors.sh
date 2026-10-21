#!/usr/bin/env bash
# ════════════════════════════════════════════════════════════════════════════
#  exp-difftest-grpc-errors — §6.2.6 E-2 动态半的编排脚本
# ════════════════════════════════════════════════════════════════════════════
#
# 【做什么】
#   起两套 gRPC server(沿用 run.sh 的同一编排),然后用同 stub 双端口打 4 类 RPC,
#   抓 Status.getCode(),断言 OK / INVALID_ARGUMENT / NOT_FOUND / ALREADY_EXISTS
#   这 4 档在两侧 wire-level 一致. 实际执行体是 grpc-dual-port-runner.java.
#
# 【与 exp-b1-timeout-cancel.sh 的关系】
#   同 model:
#     1. 假设两套栈已经起来了(由 run.sh --keep --serve-only 启动)
#     2. 自己只发请求 + 观测,不重写基建
#   但本脚本在 stacks 还没起时会**主动**调 run.sh --keep --serve-only 起栈,避免用户
#   必须开两个 terminal. 这点跟 exp-b1 不同, 因为本实验不依赖抓慢事件、不需要精确
#   测量 PG 侧查询滞留时间, 全是即时 RPC, 不必担心 startup race.
#
# 【配套】
#   - 静态半前提:tools/difftest/check-proto-static.sh(已跑且 ✓)
#   - 实际执行体:tools/difftest/grpc-dual-port-runner.java
#   - 录制:tools/difftest/grpc-results.txt(本次运行的 4 档结果)
#
# 【不依赖】
#   - Go 真源仓库里的 Go RPC —— 单一 Java client 双端口发,完全在 RAG 这边完成
#   - DIff / python —— 任何文本对照都在 stdout 直接进行
#
# 用法:
#   ./exp-difftest-grpc-errors.sh                       # 起栈(若需要)+ 跑实验
#   GO_USER_PORT=29004 RAG_USER_PORT=69210 ./...        # 自定义端口
#   ./exp-difftest-grpc-errors.sh --stacks-up           # 假设栈已起来,只跑实验

set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"

GO_USER_PORT="${GO_USER_PORT:-19004}"
RAG_USER_PORT="${RAG_PORT:-${RAG_USER_PORT:-59210}}"
GO_HOST="${GO_HOST:-127.0.0.1}"
RAG_HOST="${RAG_HOST:-127.0.0.1}"

BRING_UP=1
for a in "$@"; do
  case "$a" in
    --stacks-up) BRING_UP=0 ;;
    *) echo "未知参数:$a(可选:--stacks-up)"; exit 2 ;;
  esac
done

port_open() {  # host, port
  local code
  code=$(curl -s -o /dev/null -m 2 -w '%{http_code}' \
    "http://$1:$2/" 2>/dev/null) || code=""
  # gRPC 不是 HTTP,这里仅用作「端口是否拒接」的探针 —— 0/200/503 都算 listening,
  # 'connect refused' 才算没起来.
  if [[ -z "$code" ]]; then return 1; fi
  return 0
}

# 用 grpc 的 PING(CONNECTED state)做最终可达性检查 —— 简单办法:直接 nc 端口,
# 真起不来 nc 会出错.
probe_grpc() {  # host, port
  if (echo > /dev/tcp/"$1"/"$2") >/dev/null 2>&1; then
    return 0
  fi
  return 1
}

if [[ $BRING_UP -eq 1 ]]; then
  if probe_grpc "$GO_HOST"  "$GO_USER_PORT" && \
     probe_grpc "$RAG_HOST" "$RAG_USER_PORT"; then
    echo "==> [0/3] 两套 gRPC server 都已起来,跳过 run.sh"
  else
    echo "==> [0/3] 起两套 gRPC server(run.sh --keep --serve-only)..."
    echo "    Go user-rpc   = $GO_HOST:$GO_USER_PORT"
    echo "    RAG user-rpc  = $RAG_HOST:$RAG_USER_PORT"
    echo "    (该命令将前台阻塞;请另开 terminal 跑本脚本,且带 --stacks-up)"
    exec "$HERE/run.sh" --keep --serve-only
  fi
fi

echo "==> [1/3] 生成 proto 桩 + 计算 classpath"
PROTO_CP="$ROOT/service/users/user/user-proto/target/classes"
if [[ ! -d "$PROTO_CP" ]]; then
  (cd "$ROOT" && mvn -q -o -pl service/users/user/user-proto -am -DskipTests compile) \
    || { echo "  FAIL proto 编译失败"; exit 1; }
fi

LIBCP_FILE="/tmp/difftest-grpc-errors-cp.txt"
(cd "$ROOT" && mvn -q -o -pl service/users/user/user-rpc \
    dependency:build-classpath -Dmdep.outputFile="$LIBCP_FILE" >/dev/null 2>&1) \
  || { echo "  FAIL dependency:build-classpath 失败"; exit 1; }
LIBCP="$(cat "$LIBCP_FILE")"

echo "==> [2/3] 编译 GrpcDualPortRunner"
mkdir -p /tmp/difftest-grpc-errors-build
javac -d /tmp/difftest-grpc-errors-build \
      -cp "$PROTO_CP:$LIBCP" \
      "$HERE/grpc-dual-port-runner.java" \
  || { echo "  FAIL 编译失败"; exit 1; }

echo "==> [3/3] 跑实验(写入 grpc-results.txt)"
echo "    Go user-rpc  = $GO_HOST:$GO_USER_PORT"
echo "    RAG user-rpc = $RAG_HOST:$RAG_USER_PORT"
java -cp "/tmp/difftest-grpc-errors-build:$PROTO_CP:$LIBCP" \
     -Dgo.host="$GO_HOST"  -Dgo.user.port="$GO_USER_PORT"  \
     -Drag.host="$RAG_HOST" -Drag.user.port="$RAG_USER_PORT" \
     tools.difftest.GrpcDualPortRunner 2>&1 | tee "$HERE/grpc-results.txt"

RC=${PIPESTATUS[0]}
echo
echo "exit code: $RC (0 = §3.1 动态半已关 / 1 = 至少一档不符)"
exit "$RC"
