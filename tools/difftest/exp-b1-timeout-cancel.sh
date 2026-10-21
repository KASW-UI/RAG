#!/usr/bin/env bash
# E-4 取证：超时后 Go 与 RAG 的底层资源释放行为差异（AGENTS.md §6.2.6 E-4 / §3.4.1 B-1）
#
# 问题
# ----
# B-1 说：Go 的 context.WithTimeout 到点后所有 ctx-aware IO 立即返回、连接池立刻释放；
#         Java 只能 Future.cancel(true) 发 InterruptedException，而 JDBC 驱动不响应
#         线程 interrupt ⇒ 同一慢查询 Go 3s 释放、Java 可能占用到查询自行结束。
#         **这条至今没有任何运行证据**，只有静态分析。
#
# 怎么造真阻塞
# ------------
# 不用 pg_sleep（需要一个能执行任意 SQL 的端点，两个栈都没有）。
# 用 **行锁**：起一个后台 psql 会话 BEGIN + SELECT ... FOR UPDATE 锁住目标行，
# 两端的 user.login（SELECT ... WHERE username=?）就会阻塞在 PG 上。
# 这是真实的生产形态（行竞争 / 长事务），且不碰任何代码。
#
# 观测两个指标
# ------------
#   1) 客户端收到响应耗时   —— 两侧都应在各自的 http-timeout(3s) 左右返回
#   2) PG 里该查询的存活时长 —— 这才是 B-1 的核心：
#      Go 的 ctx cancel 会让 PG **立刻**收到 CancelRequest、查询消失；
#      RAG 的 cancel(true) 发不到 JDBC，查询会**一直挂到锁释放**。
#
# 用法
# ----
#   ./exp-b1-timeout-cancel.sh          # 自己起栈、跑实验、收摊
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN_DIR=/tmp/difftest
LOGS="$RUN_DIR/logs"
PG_PORT=55432
PG_U=admin
# 24s 而非 12s：本轮要跑 **4 次**探测（Go/RAG × user/admin），
# 串行下来约 11s。锁留得越久，越能证明「查询消失」不是锁自己释放造成的 ——
# 若实现没生效，查询会一直活到 24s，观测循环先超时并报「未见消失」。
LOCK_SECONDS=24

USERNAME="e4_probe"
PASSWORD="pw123456"
ADMIN_USERNAME="admin"
ADMIN_PASSWORD="admin"

cleanup() {
  for p in ${LOCK_PIDS:-}; do kill "$p" 2>/dev/null || true; done
  for p in ${SERVE_PID:-}; do kill "$p" 2>/dev/null || true; done
  wait 2>/dev/null || true
}
trap cleanup EXIT

# ⚠️ SQL 必须走 -c：当位置参数传给 psql 会被当成 **dbname**（静默连不上、输出为空）。
#    这个坑在本次第一版就踩了：造用户的行数一直是空的。
psql_go()  { docker exec difftest-postgres psql -U "$PG_U" -d go_db  -qAt -c "$1"; }
psql_rag() { docker exec difftest-postgres psql -U "$PG_U" -d rag_db -qAt -c "$1"; }

now_ms() { python3 -c 'import time;print(int(time.time()*1000))'; }
stamp()  { python3 -c 'import time;print(time.strftime("%H:%M:%S",time.localtime())+"."+str(int(time.time()*1000)%1000).zfill(3))'; }

# 该库里「正在等锁」的查询明细 —— 只要把 state / wait_event_type / query_start 打出来，
# 就能把它和客户端时间戳、worker 日志时间戳放到同一条时间轴上比。
# ⚠️ 早期版本只统计 count(*) 并在 count 掉到 0 时判定「查询消失」，
#    那样既看不出是哪条查询、也看不出它什么时候开始的：
#    2026-10-21 注入组就出现过「客户端 2121ms 返回 / 查询 398ms 消失 /
#    worker 却 6860ms 后才结束」的三方矛盾，只看 count 根本无从下手。
pg_lock_rows() {  # db -> "pid:state:waitEventType:query_start + ..."
  docker exec difftest-postgres psql -U "$PG_U" -d "$1" -qAt -c \
    "SELECT coalesce(string_agg(pid||':'||state||':'||coalesce(wait_event_type,'-')||':'
       ||to_char(query_start,'HH24:MI:SS.MS'),' + '),'(无等锁查询)')
     FROM pg_stat_activity WHERE datname='$1' AND wait_event_type='Lock'" \
    2>/dev/null | tr -d '[:space:]'
}
pg_lock_count() {  # db
  docker exec difftest-postgres psql -U "$PG_U" -d "$1" -qAt -c \
    "SELECT count(*) FROM pg_stat_activity WHERE datname='$1' AND wait_event_type='Lock'" \
    2>/dev/null | tr -d '[:space:]'
}

# ---------------------------------------------------------------- 起栈
echo "==> [1/4] 起两套栈（run.sh --serve-only，复用既有编排）"
mkdir -p "$LOGS"
# EXP_NO_BUILD=1 跳过重建 jar。改 run.sh / 本脚本都不改变 jar 产物，
# 实验迭代时能省 ~90s；只有改过 Java 代码或产物缺失时才不要用。
SERVE_ARGS=(--serve-only)
[[ "${EXP_NO_BUILD:-0}" == "1" ]] && SERVE_ARGS+=(--no-build)
"$HERE/run.sh" "${SERVE_ARGS[@]}" >"$LOGS/exp-b1-serve.log" 2>&1 &
SERVE_PID=$!

for _ in $(seq 1 180); do
  g=$(curl -s -o /dev/null -w '%{http_code}' -m 2 http://127.0.0.1:18888/usercenter/v1/user/login 2>/dev/null || echo "")
  r=$(curl -s -o /dev/null -w '%{http_code}' -m 2 http://127.0.0.1:59900/user/v1/login 2>/dev/null || echo "")
  [[ "$g" == "405" && "$r" == "405" ]] && break
  sleep 1
done
echo "  ✓ 两套栈就绪（Go 18888 / RAG 59900）"

# ---------------------------------------------------------------- 造用户
echo "==> [2/4] 两端各造一个同名用户（login 走 SELECT username=?，会被行锁挡住）"
for side in go rag; do
  if [ "$side" = go ]; then BASE=http://127.0.0.1:18888; else BASE=http://127.0.0.1:59900; fi
  curl -s -X POST "$BASE/$( [ "$side" = go ] && echo usercenter/v1/user/register || echo user/v1/register )" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\",\"email\":\"e4@example.com\"}" >/dev/null
  # 注意：这里在函数外的循环里，不能用 local（bash 会报 "can only be used in a function"）
  TBL=$([ "$side" = go ] && echo users || echo users_user)
  echo "  · $side($TBL): $( [ "$side" = go ] && psql_go "SELECT count(*) FROM $TBL WHERE username='$USERNAME'" || psql_rag "SELECT count(*) FROM $TBL WHERE username='$USERNAME'" ) 行"
done

# ---------------------------------------------------------------- 持锁
echo "==> [3/4] 起后台事务，各持锁 ${LOCK_SECONDS}s（user 表 + admin 表）"
LOCK_PIDS=""
# ⚠️ 表名两侧不同：Go 是 GORM 默认名 `users`，RAG 是 `users_user`（§3.4 wire 契约偏离）；
#    `admin_admin` 两侧同名。
# ⚠️ 必须用 ACCESS EXCLUSIVE 的**表级锁**，不能用 `SELECT ... FOR UPDATE` 行锁 ——
#    PostgreSQL 的普通 SELECT 走 MVCC 快照读，**不会被 FOR UPDATE 持有的行锁挡住**。
#    第一版就是死在这里：两侧 login 都在 150ms 内成功返回，实验毫无意义。
#    ACCESS EXCLUSIVE 挡住一切访问（含 SELECT），才是真实的长事务形态。
# ⚠️⚠️ **每个 (库, 表) 必须用独立 psql 进程**，不能塞进同一个事务。
# 第一版把两张表的 LOCK 写在一个 BEGIN…ROLLBACK 里，于是 go_db 上
# 「admins 不存在」那条 LOCK 报错 → 整个事务 abort → **users 的锁也一起没了**，
# 表现为 Go/user 在 363ms 就登录成功（根本没被挡住）。
# 而 RAG 侧看着一切正常 —— 这种「一侧假成功」的偏差极难从结果反推，必须靠进程隔离杜绝。
#
# ⚠️ 表名两侧**全部不同**（AGENTS.md §3.4 的 wire 契约偏离 A-03）。
# Go 真源 service/user/admin/rpc/internal/model/data_model.go 的 TableName()：
#   Admin → "admins"      AdminInvite → "admin_invites"      User → "users"
# RAG 侧是 §3.4 记录的 admin_admin / users_user。
# 最初这里两边都写 admin_admin，于是 go_db 报「不存在」——校验拦下了它，否则又是一轮假数据。
for db in go_db rag_db; do
  if [ "$db" = go_db ]; then USER_TBL=users; ADMIN_TBL=admins; else USER_TBL=users_user; ADMIN_TBL=admin_admin; fi
  for t in "$USER_TBL" "$ADMIN_TBL"; do
    if ! docker exec difftest-postgres psql -U "$PG_U" -d "$db" -qAt \
         -c "SELECT to_regclass('public.$t') IS NOT NULL" 2>/dev/null | grep -q t; then
      echo "  ✗ $db.$t 不存在 —— 该库该表无法加锁（本次实验会偏，务必先修表名映射）" >&2
      exit 1
    fi
    ( echo "BEGIN;"
      echo "LOCK TABLE $t IN ACCESS EXCLUSIVE MODE;"
      echo "SELECT pg_sleep($LOCK_SECONDS);"
      echo "ROLLBACK;" ) \
    | docker exec -i difftest-postgres psql -U "$PG_U" -d "$db" -qAt >/dev/null 2>&1 &
    LOCK_PIDS="$LOCK_PIDS $!"
  done
done
sleep 2
echo "  ✓ 表锁已持有（2 库 × 2 表 = 4 个独立事务）"

# ---------------------------------------------------------------- 观测
echo "==> [4/4] 发请求并同时观测客户端耗时 + PG 查询存活时长"
echo

probe() {  # label, db, url, path, json_body
  local label=$1 db=$2 url=$3 path=$4 body=$5
  local before
  before=$(pg_lock_count "$db")
  echo "  [$label] 发起前该库等锁查询数 = $before"

  # curl 放后台：主线程同步采样 PG，这样「客户端在飞」和「PG 查询生死」
  # 落在同一条时间轴上，而不是先后测两个量再靠猜拼起来。
  local out; out=$(mktemp)
  local t0 t1
  t0=$(now_ms)
  ( curl -s -m 30 -X POST "$url$path" -H 'Content-Type: application/json' \
         -d "$body" >"$out" 2>/dev/null ) &
  local cpid=$!

  # ---- 请求在飞期间：逐次采样
  while kill -0 "$cpid" 2>/dev/null; do
    printf '  [%s] %s  等锁明细: %s\n' "$label" "$(stamp)" "$(pg_lock_rows "$db")"
    sleep 0.25
  done
  wait "$cpid" 2>/dev/null
  t1=$(now_ms)
  printf '  [%s] %s  ← 客户端此刻返回（耗时 %sms），等锁明细: %s\n' \
         "$label" "$(stamp)" "$(( t1 - t0 ))" "$(pg_lock_rows "$db")"

  # ---- 请求返回后：继续采样到查询消失（上限 15s > 观测窗口）
  local vanished_at="未见消失(>15000ms)"
  local now
  for _ in $(seq 1 60); do
    now=$(pg_lock_count "$db")
    if [[ "$now" -lt "$before" || "$now" == "0" ]]; then
      vanished_at="$(( $(now_ms) - t1 ))ms"
      break
    fi
    printf '  [%s] %s  等锁明细: %s\n' "$label" "$(stamp)" "$(pg_lock_rows "$db")"
    sleep 0.25
  done

  printf '%-9s 客户端返回 %sms   响应体=%s\n' "$label" "$(( t1 - t0 ))" "$(head -c 100 "$out")"
  printf '%-9s 客户端返回后，PG 里的阻塞查询又存活了 %s\n' "" "$vanished_at"
  rm -f "$out"
}

USER_BODY="{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}"
ADMIN_BODY="{\"username\":\"$ADMIN_USERNAME\",\"password\":\"$ADMIN_PASSWORD\"}"

echo "---- user 域（user.login → SELECT users/users_user WHERE username=?）----"
probe "Go/user"  go_db  http://127.0.0.1:18888 /usercenter/v1/user/login "$USER_BODY"
echo
probe "RAG/user" rag_db http://127.0.0.1:59900 /user/v1/login "$USER_BODY"
echo
# ⚠️ admin 域必须单独跑：admin-rpc 有 29 处 catch(Exception) 放行分支 + 13 处 checkDeadline，
#    user-rpc 全绿完全不能推出 admin 侧也成立（两个类、两套装配、两套 catch 集合）。
echo "---- admin 域（admin.login → SELECT admin_admin WHERE username=?）----"
probe "Go/admin"  go_db  http://127.0.0.1:18889 /admincenter/v1/admin/login "$ADMIN_BODY"
echo
probe "RAG/admin" rag_db http://127.0.0.1:58889 /admin/v1/login "$ADMIN_BODY"

# ---------------------------------------------------------------- 异常形态
# 用户 2026-10-21 拍板「先不定性映射，先把观察到的异常形态记下来」。
# setQueryTimeout 生效时，pgjdbc 会抛 PSQLException(57014 query_canceled)，
# 它在 worker 线程上冒泡（客户端早已收到 DEADLINE_EXCEEDED），
# 最终被业务层的 catch (Exception) 接住 —— 这里把真实形态抓出来备查。
echo
echo "---- RAG worker 线程的业务起止时刻（business_info / business_error）----"
echo "     用来把 worker 生命周期和上面那条时间轴对齐 —— 客户端返回、"
echo "     PG 查询消失、worker 退出是三件不同的事，历史上在这里混错过。"
grep -hE "business_(info|error)|RPC failed" "$LOGS/rag-user-rpc.log" "$LOGS/rag-admin-rpc.log" 2>/dev/null \
  | cut -c1-120 | tail -6 | sed 's/^/  /' \
  || echo "  （日志里没有匹配行）"

echo
echo "---- RAG rpc 侧日志里的取消 / 超时相关行（供定性用，暂不映射）----"
grep -hiE "cancel|timeout|deadline|57014|query_canceled|SQLException" \
     "$LOGS/rag-user-rpc.log" "$LOGS/rag-admin-rpc.log" 2>/dev/null | tail -12 \
  | sed 's/^/  /' || echo "  （日志里没有匹配行）"
echo "----（以上是 worker 线程的真实异常形态；客户端侧早已返回，不影响客户端）----"

echo
echo "======================================================================"
echo "判读（见 AGENTS.md §3.4.1「B-1 补记」）："
echo "  两侧客户端返回耗时都应当接近各自的 rpc timeout（Go 2s / RAG 2s）——"
echo "  RpcInterceptor 的 catch(TimeoutException) 是【立即】返回 DEADLINE_EXCEEDED 的，"
echo "  不等 worker 退出。"
echo
echo "  ① PG 查询存活时长 = B-1 的核心。**user 与 admin 两侧都应当 ≈ 0ms**"
echo "     （Go 靠 ctx cancel；RAG 靠 DeadlineQueryTimeoutInjector 按 deadline 注入的"
echo "      setQueryTimeout）。若 RAG 这里又是数千毫秒 ⇒ 拦截器没被注册 / 没生效。"
echo
echo "  ② 响应体。**user 与 admin 两侧都应当是 {\"code\":1015,\"msg\":\"服务繁忙\"}**"
echo "     RAG 若仍是 text/plain \"Request Timeout\" ⇒ DEADLINE_EXCEEDED 没能穿过"
echo "     业务层的 catch(Exception)；"
echo "     若变成 {\"code\":5001} ⇒ 57014 翻译没生效（被吞成了 INTERNAL）。"
echo
echo "  ③ RAG worker 的 business_info → business_error 时刻差应当 ≪ ${LOCK_SECONDS}s"
echo "     （超时在 deadline 处触发、worker 随即退出，而不是挂到持锁事务释放）。"
echo "======================================================================"