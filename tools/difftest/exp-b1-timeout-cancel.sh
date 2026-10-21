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
LOCK_SECONDS=12          # 持锁时长：必须 >> 3s 超时，否则看不出差异

USERNAME="e4_probe"
PASSWORD="pw123456"

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

# ---------------------------------------------------------------- 起栈
echo "==> [1/4] 起两套栈（run.sh --serve-only，复用既有编排）"
mkdir -p "$LOGS"
"$HERE/run.sh" --serve-only >"$LOGS/exp-b1-serve.log" 2>&1 &
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
echo "==> [3/4] 起两个后台事务，各持锁 ${LOCK_SECONDS}s"
LOCK_PIDS=""
# ⚠️ 表名两侧不同：Go 是 GORM 默认名 `users`，RAG 是 `users_user`（§3.4 wire 契约偏离）。
# ⚠️ 必须用 ACCESS EXCLUSIVE 的**表级锁**，不能用 `SELECT ... FOR UPDATE` 行锁 ——
#    PostgreSQL 的普通 SELECT 走 MVCC 快照读，**不会被 FOR UPDATE 持有的行锁挡住**。
#    第一版就是死在这里：两侧 login 都在 150ms 内成功返回，实验毫无意义。
#    ACCESS EXCLUSIVE 挡住一切访问（含 SELECT），才是真实的长事务形态。
for db in go_db rag_db; do
  if [ "$db" = go_db ]; then TBL=users; else TBL=users_user; fi
  ( echo "BEGIN;"
    echo "LOCK TABLE $TBL IN ACCESS EXCLUSIVE MODE;"
    echo "SELECT pg_sleep($LOCK_SECONDS);"
    echo "ROLLBACK;" ) \
  | docker exec -i difftest-postgres psql -U "$PG_U" -d "$db" -qAt >/dev/null 2>&1 &
  LOCK_PIDS="$LOCK_PIDS $!"
done
sleep 2
echo "  ✓ 行锁已持有（两侧各一个事务）"

# ---------------------------------------------------------------- 观测
echo "==> [4/4] 发请求并同时观测客户端耗时 + PG 查询存活时长"
echo

probe() {  # label, db, url, path
  local label=$1 db=$2 url=$3 path=$4
  # 该库中正在等锁的查询数（wait_event_type='Lock'）
  local before after
  before=$(docker exec difftest-postgres psql -U "$PG_U" -d "$db" -qAt \
    -c "SELECT count(*) FROM pg_stat_activity WHERE datname='$db' AND wait_event_type='Lock'")

  local t0 t1 body
  t0=$(now_ms)
  body=$(curl -s -m 30 -X POST "$url$path" -H 'Content-Type: application/json' \
         -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}" 2>/dev/null)
  t1=$(now_ms)
  local client_ms=$(( t1 - t0 ))

  # 客户端返回后，连采几次看查询多久才从 pg_stat_activity 消失
  local vanished_at="未见消失(>15000ms)"
  for _ in $(seq 1 40); do
    sleep 0.25
    local now
    now=$(docker exec difftest-postgres psql -U "$PG_U" -d "$db" -qAt \
      -c "SELECT count(*) FROM pg_stat_activity WHERE datname='$db' AND wait_event_type='Lock'")
    if [[ "$now" -lt "$before" || "$now" == "0" ]]; then
      vanished_at="$(( $(now_ms) - t1 ))ms"
      break
    fi
  done

  printf '%-8s 客户端返回 %sms   响应体=%s\n' "$label" "$client_ms" "$(echo "$body" | head -c 90)"
  printf '%-8s 客户端返回后，PG 里的阻塞查询又存活了 %s\n' "" "$vanished_at"
}

probe "Go"  go_db  http://127.0.0.1:18888 /usercenter/v1/user/login
echo
probe "RAG" rag_db http://127.0.0.1:59900 /user/v1/login

echo
echo "======================================================================"
echo "判读（见 AGENTS.md §3.4.1 B-1）："
echo "  两侧客户端返回耗时应当都接近各自的 http-timeout(3s) ——"
echo "  RpcInterceptor 的 catch(TimeoutException) 是【立即】返回 DEADLINE_EXCEEDED 的，"
echo "  不等 worker 退出。"
echo "  真正的差异在第二行：Go 的 PG 查询应当【立刻】消失（ctx cancel 触达 PG），"
echo "  RAG 的应当【继续存活】到持锁事务释放为止（JDBC 不响应线程 interrupt）。"
echo "======================================================================"