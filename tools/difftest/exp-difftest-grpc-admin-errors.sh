#!/usr/bin/env bash
# ════════════════════════════════════════════════════════════════════════
#  exp-difftest-grpc-admin-errors — admin-rpc 的 **gRPC 层**双侧活体对拍编排
# ════════════════════════════════════════════════════════════════════════
#
# 【做什么】
#   起两套 admin-rpc（沿用 run.sh 的同一编排），用**同一个 stub 双端口**打
#   admin 域的 13 个 RPC，抓 Status.getCode() 与 Status.getDescription() 逐条比对。
#   实际执行体是 GrpcAdminDualPortRunner.java。
#
# 【与 exp-difftest-grpc-errors.sh（user 域）的关系】
#   同 model、不同域。user 域那个只覆盖 4 档、只比 code；
#   本脚本覆盖 admin 的 13 个 RPC，并且**必须比 description** ——
#   admin 域有一类只有 description 才暴露的差异（DRIFT-ARPC-007）。
#
# 【为什么必须有它】
#   tools/difftest/diff.py 做的是 HTTP wire 层对拍，它证明的是
#   「admin-api 对外行为两侧一致」。但 HTTP 层把 gRPC 状态**码**翻译成业务码，
#   **status description 根本不进响应体**
#   （GlobalExceptionHandler.handleRPC:74-75 用 ErrorCode.getMsg(businessCode)）。
#   ⇒ §3.1 明写「对照必须覆盖」的「proto 字段语义 / 错误码语义」在 HTTP 对拍里被抹掉。
#   ⇒ docs/DRIFT/users/admin/rpc.md 的 UNRESOLVED-ARPC-001（「本轮零条双侧活体对拍」）
#   就是靠本脚本关掉的。
#
# 【判定口径】
#   exit 1 ⟺ 两侧 gRPC **code** 不同（语义级差异）。
#   description 不同**不算失败** —— 逐条打印并单独计数，决策权在用户
#   （理由见 GrpcAdminDualPortRunner.java 的文件头注释）。
#
# 用法:
#   ./exp-difftest-grpc-admin-errors.sh                       # 起栈(若需要)+ 跑实验
#   ./exp-difftest-grpc-admin-errors.sh --stacks-up           # 假设栈已起来,只跑实验
#   GO_ADMIN_PORT=18081 RAG_ADMIN_PORT=58081 ./...            # 自定义端口
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$HERE/../.."

GO_ADMIN_PORT="${GO_ADMIN_PORT:-18081}"
RAG_ADMIN_PORT="${RAG_ADMIN_PORT:-58081}"
GO_HOST="${GO_HOST:-127.0.0.1}"
RAG_HOST="${RAG_HOST:-127.0.0.1}"

BRING_UP=1
for a in "$@"; do
  case "$a" in
    --stacks-up) BRING_UP=0 ;;
    *) echo "未知参数:$a(可选:--stacks-up)"; exit 2 ;;
  esac
done

probe_grpc() {  # host, port
  (echo > /dev/tcp/"$1"/"$2") >/dev/null 2>&1
}

if [[ $BRING_UP -eq 1 ]]; then
  if probe_grpc "$GO_HOST" "$GO_ADMIN_PORT" && probe_grpc "$RAG_HOST" "$RAG_ADMIN_PORT"; then
    echo "==> [0/3] 两套 admin-rpc 都已起来,跳过 run.sh"
  else
    echo "==> [0/3] 起两套栈(run.sh --keep --serve-only)..."
    exec "$HERE/run.sh" --keep --serve-only
  fi
fi

echo "==> [1/4] 编译 admin-proto 桩 + 计算 classpath"
PROTO_CP="$ROOT/service/users/admin/admin-proto/target/classes"
if [[ ! -d "$PROTO_CP" ]]; then
  (cd "$ROOT" && mvn -q -o -pl service/users/admin/admin-proto -am -DskipTests compile) \
    || { echo "  FAIL proto 编译失败"; exit 1; }
fi

LIBCP_FILE="/tmp/difftest-grpc-admin-cp.txt"
(cd "$ROOT" && mvn -q -o -pl service/users/admin/admin-rpc \
    dependency:build-classpath -Dmdep.outputFile="$LIBCP_FILE" >/dev/null 2>&1) \
  || { echo "  FAIL dependency:build-classpath 失败"; exit 1; }
LIBCP="$(cat "$LIBCP_FILE")"

echo "==> [2/4] 编译 GrpcAdminDualPortRunner"
rm -rf /tmp/difftest-grpc-admin-build
mkdir -p /tmp/difftest-grpc-admin-build
javac -d /tmp/difftest-grpc-admin-build \
      -cp "$PROTO_CP:$LIBCP" \
      "$HERE/GrpcAdminDualPortRunner.java" \
  || { echo "  FAIL 编译失败"; exit 1; }

# ── 3. 种 probe user（2026-10-23 新增）────────────────────────────────
#
# 为什么需要：updateUser 作用在 **users_user** 表上，而 seeder 只造 admin（admin_admin）。
# 本 runner 此前只覆盖 updateUser 的**错误路径**（notfound），成功路径从未被对拍 ——
# 而 DRIFT-ARPC-002（UpdateUserResp.success：Go=false / RAG=true）
# **恰好只在成功响应里可见** ⇒ 缺这条种子数据，那条漂移改了也没人能发现。
#
# 两侧表名不同（Go `users` / RAG `users_user`）、uid 取不同值（归一化时 uid 一律不比），
# 但 **username / email / status / score 必须两侧完全一致**，否则 payload_diff 会
# 把「种子数据不同」误报成语义差异。
#
# 幂等：ON CONFLICT (uid) DO NOTHING，可反复重放。
echo "==> [3/4] 种 probe user（go_db.users / rag_db.users_user）"

docker exec -i difftest-postgres psql -U admin -d go_db -q <<'SQL'
INSERT INTO users (uid, username, password, email, status, score, extra_info, create_time, update_time)
VALUES (7700000000000000001, 'probe_updateuser', 'x', 'probe@mail.test', 0, 0, '{}'::jsonb, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (uid) DO NOTHING;
SQL
docker exec -i difftest-postgres psql -U admin -d rag_db -q <<'SQL'
INSERT INTO users_user (uid, username, password, email, status, score, extra_info, create_time, update_time)
VALUES (7700000000000000002, 'probe_updateuser', 'x', 'probe@mail.test', 0, 0, '{}'::jsonb, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (uid) DO NOTHING;
SQL
echo "    ✓ go_db.users uid=7700000000000000001 / rag_db.users_user uid=7700000000000000002"

echo "==> [4/4] 跑实验(写入 grpc-admin-results.txt)"
echo "    Go  admin-rpc = $GO_HOST:$GO_ADMIN_PORT"
echo "    RAG admin-rpc = $RAG_HOST:$RAG_ADMIN_PORT"
java -cp "/tmp/difftest-grpc-admin-build:$PROTO_CP:$LIBCP" \
     -Dgo.host="$GO_HOST"  -Dgo.admin.port="$GO_ADMIN_PORT" \
     -Drag.host="$RAG_HOST" -Drag.admin.port="$RAG_ADMIN_PORT" \
     -Dgo.probe.uid=7700000000000000001 \
     -Drag.probe.uid=7700000000000000002 \
     tools.difftest.GrpcAdminDualPortRunner 2>&1 | tee "$HERE/grpc-admin-results.txt"

RC=${PIPESTATUS[0]}
echo
echo "exit code: $RC (0 = gRPC code 两侧全同 / 1 = 至少一条 code 不同)"
exit "$RC"
