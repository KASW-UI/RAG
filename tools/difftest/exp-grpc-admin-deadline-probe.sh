#!/usr/bin/env bash
# ════════════════════════════════════════════════════════════════════════
#  exp-grpc-admin-deadline-probe — UNRESOLVED-ARPC-002 的**运行期**取证
# ════════════════════════════════════════════════════════════════════════
#
# 【问题】
#   两侧 rpc 服务端超时都是 2s。当**客户端**带一个更短的 deadline（100ms）时，
#   两侧的**服务端工作寿命**是否相同？
#
#     Go  : service/common/observability/rpc.go:38
#             context.WithTimeout(ctx, timeout)  ← ctx 已含客户端 deadline
#             ⇒ 有效期限 = min(客户端, 2s)
#     RAG : RpcInterceptor.java:170-172
#             deadlineEpochMs = now + timeout.toMillis()  ← 只算服务端自己的 2s
#             ⇒ 有效期限 = 2s（客户端 deadline 被忽略）
#
# 【为什么「客户端返回时刻」量不出差异】
#   客户端看到的返回时刻，两侧都由**客户端自己的 deadline** 说了算（都 ≈100ms）。
#   ⇒ 差异只体现在 **PG 侧那条阻塞查询活了多久**：
#       Go  → ctx 到期 → pgx CancelRequest → 查询 ≈100ms 消失
#       RAG → setQueryTimeout(2)（按自己的 2s deadline 注入）→ 查询 ≈2s 才消失
#
# 【怎么造真阻塞】
#   不用 pg_sleep（两个栈都没有能执行任意 SQL 的端点）。
#   用 **ACCESS EXCLUSIVE 表锁**：后台 psql 事务 `LOCK TABLE <admin 表>`，
#   两侧的 admin.login（SELECT ... WHERE username=?）会阻塞在 PG 上。
#   ⚠️ **必须是表级 ACCESS EXCLUSIVE，不能用 SELECT ... FOR UPDATE 行锁** ——
#      普通 SELECT 走 MVCC 快照读，**不会被行锁挡住**（exp-b1-timeout-cancel.sh 的注释
#      里记了这个坑：第一版用行锁，两侧 login 都在 150ms 内成功返回，实验毫无意义）。
#   ⚠️ **每个 (库, 表) 用独立 psql 进程** —— 塞进同一个事务会让一张表的 LOCK 报错
#      把整个事务 abort，另一张表的锁也一起没了（exp-b1 的同一个坑）。
#
# 【表名两侧不同】
#   Go : admins        （data_model.go:16-18 的 TableName()）
#   RAG : admin_admin  （users 域「表名加前缀」约定）
#
# 【观测】
#   ① 客户端返回时刻 + status（由 GrpcAdminDeadlineProbe 打）
#   ② **PG 里那条阻塞查询的存活时长**（本脚本采样 pg_stat_activity）
#   ③ 对照组：客户端 deadline = 5000ms（> 服务端 2s）⇒ 两侧都该 ≈2s
#      ⇒ 这一档证明「差异确实来自客户端 deadline 是否被继承」，而不是别的什么。
#
# 【判定口径】
#   exit 1 ⟺ 客户端 deadline(100ms) 档下，两侧 **PG 查询存活时长**差异 > 1s
#             （即 Go ≈100ms 而 RAG ≈2s ⇒ CONFIRMED SEMANTIC MISMATCH 的运行期证据）
#   exit 0 ⟺ 未观测到该差异（此时结论应改写为「未复现」）
#
# 【用法】
#   ./exp-grpc-admin-deadline-probe.sh              # 自己起栈、跑实验、收摊
#   ./exp-grpc-admin-deadline-probe.sh --stacks-up  # 假设栈已起来，只跑实验
#   EXP_NO_BUILD=1 ./exp-grpc-admin-deadline-probe.sh   # 跳过重建 jar
#
# ⚠️⚠️ **写这个脚本时踩的一个 bash 陷阱（已修，但要知道）**：
#   在 UTF-8 locale 下，**`$VAR` 后面紧跟一个多字节字符时，bash 会把那个字符
#   并进变量名**。本脚本第一版写的是：
#       echo "... RAG $RAG_ADMIN_PORT）"      ← 全角右括号
#   ⇒ bash 去找名为 `RAG_ADMIN_PORT）` 的变量 ⇒ `unbound variable` 直接退出。
#   （实测：`bash -c 'FOO=1; echo "$FOO）"'` 输出为空，而 `"${FOO}）"` 正常。）
#   ⇒ **凡 `$VAR` 后面紧跟中文/全角标点，一律写成 `${VAR}`。**
#   本脚本已扫过一遍：现在只有这一处 `$VAR` + 多字节字符的组合，已用花括号修掉。
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$HERE/../.."
# ⚠️ **必须可覆盖**（E48 · 2026-10-26 第三十九轮）：多域并发时两个 harness 会撞同一个
#    /tmp/difftest ⇒ 日志/构建产物互踩 ⇒ 与 run.sh 的 RUN_DIR / 端口偏移保持同一套约定。
RUN_DIR="${RUN_DIR:-/tmp/difftest}"
LOGS="$RUN_DIR/logs"
# 🔴 **容器名必须跟 run.sh 的前缀走**（run.sh 用 DIFTEST_CONTAINER_PREFIX 隔离 compose project）
#    ⇒ 写死 "difftest-postgres" 会在第二个实例里**锁错容器**（拿到别人的 PG ⇒ 实验偏）。
PG_CONTAINER="${DIFTEST_CONTAINER_PREFIX:-difftest}-postgres"
PG_U=admin
# 🔴 **端口必须跟随 `DIFTEST_PORT_OFFSET`**（E48 · 第四十轮实测踩到）：
#    `run.sh` 用偏移算端口（`GO_ADMIN_RPC_PORT = 18081 + OFFSET`），而探针默认仍是 18081
#    ⇒ 多域并发时探针会**连到别人的栈** ⇒ 实验结论作废（且端口预检直接失败）。
PORT_OFFSET="${DIFTEST_PORT_OFFSET:-0}"
GO_ADMIN_PORT="${GO_ADMIN_PORT:-$((18081 + PORT_OFFSET))}"
RAG_ADMIN_PORT="${RAG_ADMIN_PORT:-$((58081 + PORT_OFFSET))}"
HOST=127.0.0.1

# 客户端 deadline 档位：短档（< 服务端 2s）与长档（> 服务端 2s，作对照）
# ⚠️ **短档默认 300ms**（E48 · 第三十九轮）：判定阈值是 1000ms ⇒
#    若短档取 1000ms，「RAG 不继承」时的信号差恰好 ≈1000ms ⇒ **卡在阈值边上 ⇒ 假绿**；
#    取 300ms ⇒ 信号 ≈1700ms ⇒ 与阈值差 700ms ⇒ 判定有余量。
DEADLINE_SHORT="${DEADLINE_SHORT:-300}"
DEADLINE_LONG="${DEADLINE_LONG:-5000}"

# 锁的时长：要 > 观测窗口（最长跑 2 档 × 2 侧 ≈ 4s + 余量），
# 否则「查询消失」可能是锁自己释放造成的，测不出「谁取消了查询」。
LOCK_SECONDS=20

# ⚠️ set -u 下必须先初始化，否则 `LOCK_PIDS="$LOCK_PIDS $x"` 会报 unbound variable
LOCK_PIDS=""
SERVE_PID=""

BRING_UP=1
for a in "$@"; do
  case "$a" in
    --stacks-up) BRING_UP=0 ;;
    *) echo "未知参数:$a(可选:--stacks-up)"; exit 2 ;;
  esac
done

now_ms() { python3 -c 'import time;print(int(time.time()*1000))'; }
stamp()  { python3 -c 'import time;print(time.strftime("%H:%M:%S",time.localtime())+"."+str(int(time.time()*1000)%1000).zfill(3))'; }

# 等锁查询的明细（pid:state:wait_event_type:query_start + query 片段）
pg_lock_rows() {  # db
  docker exec "$PG_CONTAINER" psql -U "$PG_U" -d "$1" -qAt -c \
    "SELECT coalesce(string_agg(pid||':'||state||':'||coalesce(wait_event_type,'-')||':'
       ||to_char(query_start,'HH24:MI:SS.MS')||':'||left(regexp_replace(query,E'[\n\r\t ]+',' ','g'),60)
       ,' + '),'(无等锁查询)')
     FROM pg_stat_activity WHERE datname='$1' AND wait_event_type='Lock'" \
    2>/dev/null | tr -d '[:space:]'
}
pg_lock_count() {  # db
  docker exec "$PG_CONTAINER" psql -U "$PG_U" -d "$1" -qAt -c \
    "SELECT count(*) FROM pg_stat_activity WHERE datname='$1' AND wait_event_type='Lock'" \
    2>/dev/null | tr -d '[:space:]'
}


# ── PG 服务端日志（**主仪器**）────────────────────────────────────────
# 为什么用日志而不是 pg_stat_activity 采样：
#   · 采样每次要起一个 psql 进程（~200ms 开销），**采样窗口比事件本身还粗** ——
#     第一版就因为这个漏掉了 RAG 档位 A 的那条查询（客户端 100ms 返回，采样点没赶上）。
#   · PG 的错误日志给出**取消的确切时刻 + 被取消的语句原文**，且
#     「没有日志条目」本身就是强证据（= 那条查询根本没到 PG）。
#   · ⚠️ PG 日志用 **UTC**，本机用 UTC+8 ⇒ 打印时统一换算，别混着比。
pg_log_lines() { docker logs "$PG_CONTAINER" 2>&1 | wc -l | tr -d ' '; }

# 取 [from_line+1, ] 区间里与取消相关的行，并算相对 T0_UTC 的毫秒偏移
pg_log_cancel_events() {  # from_line → 输出 "<pid> <cancel_epoch_ms> <stmt>"
  docker logs "$PG_CONTAINER" 2>&1 | tail -n "+$(( $1 + 1 ))" | python3 -c '
import sys, re, datetime
pend = None
for raw in sys.stdin:
    m = re.match(r"^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d+) UTC \[(\d+)\] (.*)$", raw.rstrip("\n"))
    if not m:
        continue
    ts = datetime.datetime.strptime(m.group(1), "%Y-%m-%d %H:%M:%S.%f").replace(tzinfo=datetime.timezone.utc)
    ms = ts.timestamp() * 1000
    body = m.group(3)
    if "canceling statement due to user request" in body:
        pend = (m.group(2), ms)
    elif pend and body.startswith("STATEMENT:"):
        stmt = body[len("STATEMENT:"):].strip()[:60]
        print("%s %.0f %s" % (pend[0], ms, stmt))
        pend = None
    elif pend and ms - pend[1] > 2000:
        pend = None
'
}

cleanup() {
  for p in ${LOCK_PIDS:-}; do kill "$p" 2>/dev/null || true; done
  for p in ${SERVE_PID:-}; do kill "$p" 2>/dev/null || true; done
  wait 2>/dev/null || true
}
trap cleanup EXIT

# ── 起栈 ────────────────────────────────────────────────────────────────
if [[ $BRING_UP -eq 1 ]]; then
  echo "==> [0/3] 起两套栈（run.sh --serve-only --keep）"
  mkdir -p "$LOGS"
  # ⚠️ **必须带 --keep**：run.sh 的 EXIT trap 默认会 `docker compose down -v` 收摊。
  #   本脚本若在中途失败（编译错、表名不对…）而把 run.sh 一起带走，
  #   容器会随之被删 ⇒ 下一轮迭代要从头再起一次栈。
  #   带 --keep 后：容器与栈进程都留着，脚本自己负责在最后收摊。
  SERVE_ARGS=(--serve-only --keep)
  [[ "${EXP_NO_BUILD:-0}" == "1" ]] && SERVE_ARGS+=(--no-build)
  "$HERE/run.sh" "${SERVE_ARGS[@]}" >"$LOGS/exp-deadline-serve.log" 2>&1 &
  SERVE_PID=$!
  READY=0
  for _ in $(seq 1 240); do
    g=$( (echo > /dev/tcp/$HOST/$GO_ADMIN_PORT) >/dev/null 2>&1 && echo up || echo "")
    r=$( (echo > /dev/tcp/$HOST/$RAG_ADMIN_PORT) >/dev/null 2>&1 && echo up || echo "")
    if [[ "$g" == "up" && "$r" == "up" ]]; then READY=1; break; fi
    sleep 1
  done
  # 🔴 **栈没起来 ⇒ 必须立刻失败**（第四十轮实测踩到）：旧写法**无条件**打印「✓ 就绪」，
  #    端口预检失败后照样往下跑 ⇒ 观测全是无效值 ⇒ **假绿** ⇒ 必须 fail-fast。
  if [[ $READY -ne 1 ]]; then
    echo "    ❌ 两套 admin-rpc 未就绪（Go ${GO_ADMIN_PORT} / RAG ${RAG_ADMIN_PORT}）"
    echo "       ⇒ 多半是**端口段被占**（另一个域在并发跑 difftest）⇒ 换端口重试："
    echo "          DIFTEST_PORT_OFFSET=10000 ./$(basename "$0")"
    echo "       ⇒ 起栈日志：$LOGS/exp-deadline-serve.log"
    exit 3
  fi
  echo "    ✓ 两套 admin-rpc 就绪（Go ${GO_ADMIN_PORT} / RAG ${RAG_ADMIN_PORT}）"
else
  echo "==> [0/3] --stacks-up：假设栈已就绪"
fi

# ── 编译 ────────────────────────────────────────────────────────────────
echo "==> [1/3] 编译 admin-proto 桩 + GrpcAdminDeadlineProbe"
PROTO_CP="$ROOT/service/users/admin/admin-proto/target/classes"
if [[ ! -d "$PROTO_CP" ]]; then
  (cd "$ROOT" && mvn -q -o -pl service/users/admin/admin-proto -am -DskipTests compile) \
    || { echo "  FAIL proto 编译失败"; exit 1; }
fi
# ⚠️ **必须带 -am**（PIT-GLOBAL-019）：不带则 classpath 解析的是**本地仓库里的旧 service/common jar**
#    ⇒ 若共享层改过，探针会编译/运行在**旧字节码**上 ⇒ 实验结论作废。
LIBCP_FILE="$RUN_DIR/grpc-admin-cp.txt"
[[ -f "$LIBCP_FILE" ]] || (cd "$ROOT" && mvn -q -o -pl service/users/admin/admin-rpc -am \
    dependency:build-classpath -Dmdep.outputFile="$LIBCP_FILE" >/dev/null 2>&1) \
  || { echo "  FAIL dependency:build-classpath 失败"; exit 1; }
LIBCP="$(cat "$LIBCP_FILE")"
BUILD_DIR="$RUN_DIR/deadline-probe-build"
rm -rf "$BUILD_DIR" && mkdir -p "$BUILD_DIR"
javac -d "$BUILD_DIR" -cp "$PROTO_CP:$LIBCP" "$HERE/GrpcAdminDeadlineProbe.java" \
  || { echo "  FAIL 探针编译失败"; exit 1; }

run_probe() {  # side, deadline_ms
  java -cp "$BUILD_DIR:$PROTO_CP:$LIBCP" \
       -Dgo.host="$HOST" -Dgo.port="$GO_ADMIN_PORT" \
       -Drag.host="$HOST" -Drag.port="$RAG_ADMIN_PORT" \
       -Ddeadline.ms="$2" -Dsides="$1" \
       tools.difftest.GrpcAdminDeadlineProbe 2>&1
}

# ── 单侧实验 ────────────────────────────────────────────────────────────
# 一侧一次：**只锁那一张表**，避免两侧查询混在同一段采样里无法归因。
one_side() {  # side, db, table, deadline_ms
  local side=$1 db=$2 tbl=$3 dl=$4
  echo
  echo "---- [$side] 客户端 deadline=${dl}ms · 锁 $db.$tbl ----"

  # 校验表存在（表名映射写错时，LOCK 会报错、事务 abort，实验会「假通过」）
  if ! docker exec "$PG_CONTAINER" psql -U "$PG_U" -d "$db" -qAt \
       -c "SELECT to_regclass('public.$tbl') IS NOT NULL" 2>/dev/null | grep -q t; then
    echo "  ✗ $db.$tbl 不存在 —— 无法加锁（本次实验会偏）"; return 1
  fi

  # 起锁（独立 psql 进程：塞进同一个事务会让一张表的 LOCK 报错把整个事务 abort）
  ( echo "BEGIN;"
    echo "LOCK TABLE $tbl IN ACCESS EXCLUSIVE MODE;"
    echo "SELECT pg_sleep($LOCK_SECONDS);"
    echo "ROLLBACK;" ) \
    | docker exec -i difftest-postgres psql -U "$PG_U" -d "$db" -qAt >/dev/null 2>&1 &
  local lock_pid=$!
  LOCK_PIDS="${LOCK_PIDS:+$LOCK_PIDS }$lock_pid"
  sleep 2

  # ── 主仪器：PG 服务端日志 ──
  # 记录「改动前」的日志行号（之后只取新增部分）
  local log_from; log_from=$(pg_log_lines)

  local out; out=$(mktemp)
  run_probe "$side" "$dl" >"$out" 2>&1
  # 等 PG 把取消事件写进日志（最多 4s）
  sleep 4

  echo "  探针输出："
  sed 's/^/    /' "$out"
  echo

  # 从探针输出里取 RPC 的**绝对墙钟起止**（epoch ms）
  local rpc_start rpc_end
  rpc_start=$(sed -n 's/.*rpc_start_ms=\([0-9]*\).*/\1/p' "$out" | head -1)
  rpc_end=$(sed -n 's/.*rpc_end_ms=\([0-9]*\).*/\1/p' "$out" | head -1)

  echo "  ★ PG 服务端日志里的取消事件："
  local ev; ev=$(pg_log_cancel_events "$log_from")
  if [[ -z "$ev" ]]; then
    echo "    ⚠️ **没有任何取消事件** ⇒ 这一侧**根本没向 PG 发出那条查询**"
    echo "       （不是「查询没被取消」，是「查询压根没到 PG」）"
    V_EV_GO_SHORT="(无)"; V_EV_RAG_SHORT="(无)"; V_EV_GO_LONG="(无)"; V_EV_RAG_LONG="(无)"
    V_DST_GO_SHORT=""; V_DST_RAG_SHORT=""; V_DST_GO_LONG=""; V_DST_RAG_LONG=""
    V_DEND_GO_SHORT=""; V_DEND_RAG_SHORT=""; V_DEND_GO_LONG=""; V_DEND_RAG_LONG=""
    dst=""; dend=""
  else
    echo "$ev" | sed 's/^/    /'
    # 算两个决定性的差值（全部用 epoch ms，**不混 JVM 启动时间**）：
    #   Δ_start = 取消时刻 - RPC 发起时刻  ⇒ 「查询活了多久」
    #   Δ_end   = 取消时刻 - 客户端返回时刻  ⇒ 「客户端走了之后，查询还占着 PG 多久」
    local calc
    calc=$(echo "$ev" | python3 -c '
import sys
rpc_start = float(sys.argv[1]); rpc_end = float(sys.argv[2])
for line in sys.stdin:
    parts = line.split(None, 2)
    if len(parts) < 2: continue
    pid, cancel = parts[0], float(parts[1])
    stmt = parts[2].strip() if len(parts) > 2 else ""
    print("      ⇒ 查询存活 Δ_start=%+.0fms   客户端返回后仍占用 PG Δ_end=%+.0fms   pid=%s"
          % (cancel - rpc_start, cancel - rpc_end, pid))
' "$rpc_start" "$rpc_end")
    echo "$calc"
    # 🔴 **判定要用数**（E48 · 第三十九轮）：只打印不算 ⇒ 探针永远不会红。
    #    同一侧可能有多条取消事件 ⇒ 取**最大** Δ_end（= 客户端走后仍占着 PG 最久的那条）。
    local dst dend
    dst=$(echo "$calc" | sed -n 's/.*Δ_start=\([+-][0-9]*\)ms.*/\1/p' | sort -n | tail -1)
    dend=$(echo "$calc" | sed -n 's/.*Δ_end=\([+-][0-9]*\)ms.*/\1/p' | sort -n | tail -1)
    echo "$calc" | grep -q 'Δ_end=' || { dst=""; dend=""; }
    local ev1; ev1=$(echo "$ev" | tr "\n" ";" )
    case "$side/$dl" in
      "go/$DEADLINE_SHORT")   V_EV_GO_SHORT="$ev1" ;;
      "rag/$DEADLINE_SHORT")  V_EV_RAG_SHORT="$ev1" ;;
      "go/$DEADLINE_LONG")    V_EV_GO_LONG="$ev1" ;;
      "rag/$DEADLINE_LONG")   V_EV_RAG_LONG="$ev1" ;;
    esac
  fi
  rm -f "$out"

  # 记结果（普通变量；bash 3.2 不支持关联数组）
  local ev_txt="$ev"
  [[ -z "$ev_txt" ]] && ev_txt="(无取消事件)"
  case "$side/$dl" in
    "go/$DEADLINE_SHORT")   V_GO_SHORT="$ev_txt" ;;
    "rag/$DEADLINE_SHORT")  V_RAG_SHORT="$ev_txt" ;;
    "go/$DEADLINE_LONG")    V_GO_LONG="$ev_txt" ;;
    "rag/$DEADLINE_LONG")   V_RAG_LONG="$ev_txt" ;;
  esac
  case "$side/$dl" in
    "go/$DEADLINE_SHORT")   V_DST_GO_SHORT="$dst";  V_DEND_GO_SHORT="$dend" ;;
    "rag/$DEADLINE_SHORT")  V_DST_RAG_SHORT="$dst"; V_DEND_RAG_SHORT="$dend" ;;
    "go/$DEADLINE_LONG")    V_DST_GO_LONG="$dst";  V_DEND_GO_LONG="$dend" ;;
    "rag/$DEADLINE_LONG")   V_DST_RAG_LONG="$dst"; V_DEND_RAG_LONG="$dend" ;;
  esac

  kill "$lock_pid" 2>/dev/null || true
  wait "$lock_pid" 2>/dev/null || true
  LOCK_PIDS="${LOCK_PIDS/$lock_pid/}"
  return 0
}

# 初始化（普通变量；bash 3.2 不支持 `declare -A`）
V_EV_GO_SHORT="(未跑)"; V_EV_RAG_SHORT="(未跑)"; V_EV_GO_LONG="(未跑)"; V_EV_RAG_LONG="(未跑)"
V_DST_GO_SHORT=""; V_DST_RAG_SHORT=""; V_DST_GO_LONG=""; V_DST_RAG_LONG=""
V_DEND_GO_SHORT=""; V_DEND_RAG_SHORT=""; V_DEND_GO_LONG=""; V_DEND_RAG_LONG=""

# ── 跑实验 ──────────────────────────────────────────────────────────────
echo "==> [2/3] 跑实验"
echo "    档位 A（短 deadline ${DEADLINE_SHORT}ms < 服务端 2s）：考察客户端 deadline 是否被继承"
one_side go  go_db  admins       "$DEADLINE_SHORT"
one_side rag rag_db admin_admin "$DEADLINE_SHORT"

echo
echo "    档位 B（长 deadline ${DEADLINE_LONG}ms > 服务端 2s）：**对照组**，两侧都该 ≈2s"
one_side go  go_db  admins       "$DEADLINE_LONG"
one_side rag rag_db admin_admin "$DEADLINE_LONG"

# ── 判定 ────────────────────────────────────────────────────────────────
echo
echo "==> [3/3] 判定（主仪器 = PG 服务端日志里的取消时刻）"
echo
for label in "A 短($DEADLINE_SHORT)ms:go:$V_EV_GO_SHORT" "A 短($DEADLINE_SHORT)ms:rag:$V_EV_RAG_SHORT" \
             "B 长($DEADLINE_LONG)ms:go:$V_EV_GO_LONG" "B 长($DEADLINE_LONG)ms:rag:$V_EV_RAG_LONG"; do
  head_part="${label%%:*}"
  rest="${label#*:}"
  side_part="${rest%%:*}"
  ev_part="${rest#*:}"
  echo "  ┌─ $head_part  [$side_part] ─────────────────────────────────────────────"
  if [[ "$ev_part" == "(无)" || "$ev_part" == "(未跑)" ]]; then
    echo "  │  (无取消事件)"
  else
    echo "$ev_part" | tr ";" "\n" | python3 -c '
import sys
for line in sys.stdin:
    p = line.split(None, 2)
    if len(p) >= 2:
        print("  │  pid=%s  cancel_epoch_ms=%.0f  %s" % (p[0], float(p[1]), (p[2].strip() if len(p)>2 else "")))
'
  fi
  echo "  └───────────────────────────────────────────────────────────────────────"
done
echo
echo "  判读（★ 关键指标是上面每条里的 Δ_end = 取消时刻 - 客户端返回时刻）："
echo "   · Δ_end ≈ 0   ⇒ 查询随客户端一起结束（Go 的 ctx 继承了客户端 deadline）"
echo "   · Δ_end ≫ 0   ⇒ **客户端已经放弃，服务端的查询还在 PG 上占着**"
echo "                 （RAG 不继承客户端 deadline，只认自己的 2s）"
echo "   · 档位 B（客户端 deadline ${DEADLINE_LONG}ms > 服务端 2s）两侧都该 ≈0"
echo "     ⇒ 证明差异**只**来自「是否继承客户端 deadline」，与 SQL 超时机制本身无关"
echo
echo "==> [3b/3] 判定器（🔴 E48 · 第三十九轮：判定从「注释里的承诺」变成「可执行的代码」）"
echo "    判定器 = tools/difftest/verdict-grpc-admin-deadline.py"
echo "    阈值   = ${DEADLINE_VERDICT_THRESHOLD_MS:-1000} ms"
echo
# 🔴 **必须真的用判定器的退出码** —— 旧写法是**无条件 exit 0** ⇒ 探针永远不会红
#    ⇒ 就算接进 run.sh 也只是「假闸门」（同族 PIT-GLOBAL-048）。
python3 "$HERE/verdict-grpc-admin-deadline.py" \
  --go-short-dend  "${V_DEND_GO_SHORT:-}"  --rag-short-dend "${V_DEND_RAG_SHORT:-}" \
  --go-long-dend   "${V_DEND_GO_LONG:-}"   --rag-long-dend  "${V_DEND_RAG_LONG:-}" \
  --threshold-ms   "${DEADLINE_VERDICT_THRESHOLD_MS:-1000}"
VERDICT_RC=$?
echo
echo "  ⇒ 判定退出码 = ${VERDICT_RC}（0=PASS / 1=FAIL / 2=INCONCLUSIVE）"
exit $VERDICT_RC
