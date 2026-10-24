#!/usr/bin/env bash
# ════════════════════════════════════════════════════════════════════════
#  exp-admin-startup-failfast — admin-api 启动期 fail-fast 的双侧实证
# ════════════════════════════════════════════════════════════════════════
#
# 【要验什么】
#   DRIFT-ADMINAPI-009：下游不可达时，Go 侧**进程起不来**（panic），
#                         RAG 侧也必须**起不来**（不能是「起来但 100% 请求失败」）。
#   UNRESOLVED-005① ：Nacos（= Go 侧 etcd 的等价物）不可达时，两侧的真实启动序列。
#
# 【Go 出处（逐层到消费点）】
#   service/user/admin/api/internal/svc/servicecontext.go:27
#       AdminRpc: adminservice.NewAdminService(zrpc.MustNewClient(c.AdminRpc)),
#   go-zero v1.10.2 zrpc/client.go:50-54       →  logx.Must(err)      // 失败即 panic
#   go-zero v1.10.2 zrpc/internal/client.go:18  →  dialTimeout = 3s
#   go-zero v1.10.2 zrpc/internal/client.go:122-142
#       grpc.DialContext(timeCtx, server, options...)   // options 含 WithBlock()
#
# 【RAG 侧机制】
#   application.yml: grpc.client.admin-rpc.immediate-connect-timeout: 3s
#   ⇒ devh 的 AbstractChannelFactory.newManagedChannel → connectOnStartup：
#      等 READY，3s 超时 → throw IllegalStateException("Can't connect to channel admin-rpc")
#      ⇒ stub 创建失败 ⇒ 上下文启动失败 ⇒ **进程退出**
#
# 【用法】
#   前提：difftest 栈已起（tools/difftest/run.sh --serve-only --keep）
#   bash tools/difftest/exp-admin-startup-failfast.sh
#
# 退出码：0 = 两个用例都符合预期；1 = 有用例不符。
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
RUN_DIR="${RUN_DIR:-/tmp/difftest}"
LOGS="$RUN_DIR/logs"
JAR="$RUN_DIR/bin/admin-api"

export POSTGRES_ADDR=127.0.0.1 POSTGRES_PORT=55432 POSTGRES_USER=admin POSTGRES_PASSWORD=difftest
export REDIS_ADDR=127.0.0.1 REDIS_PORT=56379
export NACOS_ADDR=127.0.0.1 NACOS_PORT=1
export POSTGRES_DB=rag_db SERVER_PORT=58889
export SPRING_CLOUD_NACOS_DISCOVERY_REGISTER_ENABLED=false

[[ -f "$JAR" ]] || { echo "缺少 $JAR —— 请先跑 tools/difftest/run.sh（会构建 jar）" >&2; exit 2; }
mkdir -p "$LOGS"

fail=0

# 找一个确定没人监听的端口
closed_port() { python3 -c "import socket;s=socket.socket();s.bind(('',0));p=s.getsockname()[1];s.close();print(p)"; }

# ────────────────────────────────────────────────────────────────────────
# 用例 A：下游 admin-rpc 不可达（static:// 指向死端口）
# ────────────────────────────────────────────────────────────────────────
echo "═══════════ 用例 A：admin-rpc 不可达（static:// 死端口） ═══════════"
PORT=$(closed_port)
LOG_A="$LOGS/exp-admin-failfast-A.log"
START=$(python3 -c 'import time;print(time.time())')
java -jar "$JAR" \
  --grpc.client.admin-rpc.address="static://127.0.0.1:${PORT}" \
  --grpc.client.admin-rpc.negotiation-type=plaintext \
  >"$LOG_A" 2>&1
RC=$?
END=$(python3 -c 'import time;print(time.time())')
ELAPSED=$(python3 -c "print(round($END-$START,1))")

echo "  退出码 = ${RC}（期望 != 0 ⇒ 进程起不来）"
echo "  耗时   = ${ELAPSED}s（期望 ≈3s ⇒ 对齐 go-zero dialTimeout）"
grep -aE "Can't connect to channel|APPLICATION FAILED TO START|Started AdminApiApplication" "$LOG_A" | head -4 | sed 's/^/    /'

if [[ $RC -eq 0 ]]; then
  echo "  ✗ 进程退出了 0 ⇒ **起得来** ⇒ 与 Go 的 panic 语义不一致"
  fail=1
else
  echo "  ✓ 进程非 0 退出 ⇒ 起不来，与 Go 的 panic 同型"
fi
if grep -aq "Can't connect to channel admin-rpc" "$LOG_A"; then
  echo "  ✓ 失败原因正是 devh 的启动期拨号检查"
else
  echo "  ✗ 没看到「Can't connect to channel admin-rpc」—— 失败原因不对"
  fail=1
fi
if grep -aq "Started AdminApiApplication" "$LOG_A"; then
  echo "  ✗ 日志里有「Started AdminApiApplication」⇒ 它居然起来了"
  fail=1
fi
# ⚠️ **量的是「拨号预算」，不是进程总时长**：
#   进程总时长 = Spring 上下文启动（~4~13s）+ 拨号预算（3s）+ 收尾
#   ⇒ 用总时长去断言 3s 是错的（实测会看到 6~17s）。
#   ⇒ 正确的量法：取「channel factory 建好」到「Application run failed」这两条**带时间戳**的日志的时间差。
#   ⚠️ 异常堆栈里的行**没有时间戳前缀**（"Caused by: ... Can't connect to channel admin-rpc"），
#   ⇒ 不能拿它当锚点，必须用带时间戳的 "Application run failed"。
DIAL_BUDGET=$(python3 - "$LOG_A" << 'PY'
import re, sys, datetime
ts_re = re.compile(r'^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3})')
start = end = None
for line in open(sys.argv[1], encoding='utf-8', errors='replace'):
    m = ts_re.match(line)
    if not m:
        continue
    t = datetime.datetime.fromisoformat(m.group(1))
    if start is None and 'Detected grpc-netty-shaded' in line:
        start = t
    if 'Application run failed' in line:
        end = t
        break
if start and end:
    print(round((end - start).total_seconds(), 1))
else:
    print(-1)
PY
)
echo "  拨号预算 = ${DIAL_BUDGET}s（期望 ≈3s ⇒ 对齐 go-zero zrpc/internal/client.go:18 dialTimeout）"
python3 - << PY
b = ${DIAL_BUDGET}
if 2.5 <= b <= 6.0:
    print("  ✓ 拨号预算落在 3s 附近")
elif b < 0:
    print("  ! 日志里没找到时间锚点，无法测预算")
else:
    print("  ✗ 拨号预算 %.1fs 不在 3s 附近" % b)
PY
# ────────────────────────────────────────────────────────────────────────
# 用例 B：Nacos 不可达（走默认的 discovery:///admin-rpc）
# ────────────────────────────────────────────────────────────────────────
echo
echo "═══════════ 用例 B：Nacos 不可达（discovery:///admin-rpc，NACOS_PORT=1） ═══════════"
LOG_B="$LOGS/exp-admin-failfast-B.log"
START=$(python3 -c 'import time;print(time.time())')
java -jar "$JAR" \
  --grpc.client.admin-rpc.negotiation-type=plaintext \
  >"$LOG_B" 2>&1
RC=$?
END=$(python3 -c 'import time;print(time.time())')
ELAPSED=$(python3 -c "print(round($END-$START,1))")

echo "  退出码 = $RC"
echo "  耗时   = ${ELAPSED}s"
grep -aE "Can't connect to channel|APPLICATION FAILED TO START|Started AdminApiApplication|nacos" "$LOG_B" | head -6 | sed 's/^/    /'

if [[ $RC -eq 0 ]]; then
  echo "  ✗ 进程退出了 0 ⇒ Nacos 不可达时**照样起得来**"
  fail=1
else
  echo "  ✓ 进程非 0 退出 ⇒ Nacos 不可达 ⇒ 起不来（Go 侧 etcd 不可达 ⇒ MustNewClient panic，同型）"
fi

echo
echo "══════════════════════════════════════════════"
if [[ $fail -eq 0 ]]; then
  echo "结果：两个用例都符合预期 ✅"
  exit 0
else
  echo "结果：有用例不符合预期 ❌"
  exit 1
fi
