#!/usr/bin/env bash
# =============================================================================
# flyway-rpc-concurrent.sh —— **L11 lane**：真 rpc 进程的 Flyway 并发验证
# =============================================================================
#
# 【它验的是什么】
#   L4 lane（`check-flyway-concurrent.sh`）已经证明「三本独立账本可以在同一个库、同一个
#   schema 上并发跑」—— 但它起的是**最小 Spring 上下文**（只 import DataSource + Flyway
#   两个 auto-configuration）⇒ **不是「真 rpc 服务同时起」那个形态**。
#   ⇒ 本脚本补 L4 的**未覆盖 #1**：把**真 rpc 进程**拉起来并发跑（同库）。
#
#   同时补 L4 的**未覆盖 #2**（迁移中途失败 / 回滚 / 同模块两副本）与
#   **未覆盖 #3**（多轮压力 + 随机延迟注入）。
#
# 【「真 rpc 进程」怎么起 —— 一个必须绕开的坑（不改生产代码）】
#   驱动 `tools/difftest/flyway-rpc-boot.java` 用 `--app=<该模块自己的 *RpcApplication>`
#   起上下文 ⇒ **真 gRPC server + 真 Kafka + 真 Redis + 真 Flyway + 真连库**都在里面
#   （不是最小上下文）。启动后驱动再做一次 **TCP 连通性探测** ⇒ 记录 gRPC server
#   是不是真的在监听（§3.3：不能凭「进程活着」推断）。
#   ⚠️ 一切「绕开」都走**启动参数**（system property 优先级高于 application.yml），
#      **不改任何生产 yaml / pom / .sql**（本 lane 无 `service/**` 写权）：
#      · `--grpc.server.port=<端口>`      ⇒ 同一台机器上多进程不撞端口
#      · `--spring.cloud.nacos.discovery.server-addr=127.0.0.1:1` ⇒ Nacos 不在线
#        （注册失败只记日志；Go 侧 etcd 注册失败也是只打日志）
#      · `--spring.flyway.table=<表名>`   ⇒ **只给反向注入用**（见下）
#
# 【怎么做到「可证的真并发」—— advisory-lock 屏障】
#   Flyway 在 PG 上取的是 **session 级 advisory lock**，key =
#   `LOCK_MAGIC_NUM + javaStringHashCode(quote(schema, 账本表名))`
#   （`flyway-database-postgresql-10.22.0` `PostgreSQLConnection.lock(Table)`；
#     `flyway-core-10.22.0` `SchemaObject.toString()` = `database.quote(schema, name)`）。
#   ⇒ **预先从外部占住这个锁** ⇒ 目标模块的 Flyway 会卡在
#   `pg_try_advisory_xact_lock(<key>)` 上（实测证据见台账）⇒
#   **多个进程同时卡在同一点** ⇒ 放锁后**同时**进入迁移 ⇒ **可证的真并发**。
#   ⇒ 屏障期间脚本会轮询 `pg_stat_activity`，确认目标进程**真的**停在 lock 查询上
#      （否则「并发」只是墙钟凑巧，AGENTS.md §3.3 的老坑）。
#
# 【四个场景】
#   S1 三域真 rpc 进程并发同库（follow ‖ task ‖ message）⇒ 边界 ①
#   S2 迁移中途失败（注入一个必然失败的 V_N）⇒ 看另一个进程与库的状态 ⇒ 边界 ②
#   S3 同一模块起两个副本（同账本同表）⇒ 边界 ②
#   S4 N 轮 + 随机启动延迟 ⇒ 边界 ③
#
# 【判定器（命名断言）—— 守门必须「可红」】
#   A1-进程启动      每个域的进程 ok=true
#   A2-gRPC-监听     真 gRPC server 真的在监听（TCP 探测 + 日志里的 started 行）
#   A3-表建出        该域迁移脚本里的表**全部**建出来了
#   A4-账本一致      账本行数 == 该域的迁移条数（没有重复应用 / 没有被覆盖）
#   A5-账本独立      生效账本表名 == 该域自己的独立账本（**反向注入模式下故意违反**）
#   A6-无残留        失败迁移不留残表
#   ⇒ 任一断言红 ⇒ 退出码 1。
#   ⇒ `--mutate-shared-ledger` **反向注入**：把 message 与 task 的
#      `spring.flyway.table` 改成**同一个** `flyway_schema_history_shared`
#      ⇒ 复现 AGENTS.md §6.3 问题 4 的**原始病根**（两模块共用一本账）
#      ⇒ 判定器**必须红**（表没建出来 / 迁移失败）⇒ 证明守门**非空转**（§3.3）。
#
# 【退出码】
#   0 = 所有场景都没有差异（所有命名断言都绿）
#   1 = 有差异（某个域的表没建出来 / 账本被互相覆盖 / 迁移失败 / 屏障没生效）
#   2 = 环境不满足（没有 docker / mvn / javac / java …）⇒ 不算「有差异」
#
# 【复现】
#   bash tools/difftest/flyway-rpc-concurrent.sh
#   bash tools/difftest/flyway-rpc-concurrent.sh --rounds=5        # 边界 ③ 的轮数
#   bash tools/difftest/flyway-rpc-concurrent.sh --mutate-shared-ledger   # 反向注入
#   bash tools/difftest/flyway-rpc-concurrent.sh --keep              # 保留容器便于现场看
# =============================================================================

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

PG_IMAGE="${L11_PG_IMAGE:-postgres:16-alpine}"
PG_USER="rag"
PG_PASS="rag"
KEEP=0
ROUNDS=3
MUTATE=0
for a in "$@"; do
  case "$a" in
    --keep) KEEP=1 ;;
    --rounds=*) ROUNDS="${a#*=}" ;;
    --mutate-shared-ledger) MUTATE=1 ;;
    -h|--help) sed -n '2,60p' "$0"; exit 0 ;;
    *) echo "unknown arg: $a" >&2; exit 2 ;;
  esac
done

now_ms() { python3 -c 'import time;print(int(time.time()*1000))'; }
log()    { printf '[L11] %s\n' "$*"; }
fail_env() { printf '[L11] ❌ 环境不满足：%s\n' "$*" >&2; exit 2; }

for bin in docker mvn javac java python3; do
  command -v "$bin" >/dev/null 2>&1 || fail_env "缺少命令：$bin"
done
docker info >/dev/null 2>&1 || fail_env "docker daemon 不可用"

TMP="$(mktemp -d /tmp/l11-flyway-XXXXXX)"
RUNID="$$"
CONTAINER="rag-l11-flyway-pg-${RUNID}"
NETWORK="rag-l11-flyway-net-${RUNID}"
PROBLEMS="$TMP/problems.txt"
: > "$PROBLEMS"

cleanup() {
  rc=$?
  set +u
  if [ "$KEEP" = "1" ]; then
    log "KEEP=1 ⇒ 保留容器 ${CONTAINER}（网络 ${NETWORK}），临时目录 ${TMP}"
  else
    docker rm -f "$CONTAINER" >/dev/null 2>&1
    docker network rm "$NETWORK" >/dev/null 2>&1
    python3 - "$TMP" <<'PY' >/dev/null 2>&1
import shutil, sys
shutil.rmtree(sys.argv[1], ignore_errors=True)
PY
  fi
  exit $rc
}
trap cleanup EXIT INT TERM

mkdir -p "$TMP/runs" "$TMP/logs" "$TMP/driver" "$TMP/mig"

# ---------------------------------------------------------------- 工具
psql_q() { docker exec "$CONTAINER" psql -U "$PG_USER" -d "$1" -t -A -c "$2" 2>/dev/null; }
# db_create <db> ⇒ 建库并**校验真的建出来了**
#   ⚠️ **postgres 镜像的初始化会先起一个「临时 server」**（initdb → 起临时实例 → 建初始库 → 停掉
#      → 再用真实 datadir 起正式实例）⇒ 在那个窗口里 `pg_isready` 是成功的，
#      但**在临时实例上建的库会被丢掉**（实测踩过：`l11_s1` 建完就没了 ⇒
#      三个进程报 `FATAL: database "l11_s1" does not exist`）。
#   ⇒ 所以必须「建完再查 pg_database 确认还在」，不在就重试。
db_create() {
  local db="$1"
  local i
  for i in $(seq 1 40); do
    docker exec "$CONTAINER" psql -U "$PG_USER" -d postgres -q -c "CREATE DATABASE \"$db\"" >/dev/null 2>&1
    if [ "$(psql_q postgres "SELECT count(*) FROM pg_database WHERE datname='$db'")" = "1" ]; then
      return 0
    fi
    sleep 1
  done
  printf '[L11] ❌ 建库失败（40 次重试后仍然不在）：%s\n' "$db" >&2
  return 1
}

# add_problem <场景> <断言名> <细节> ⇒ 记一条红断言（判定器输入）
add_problem() {
  printf '%s | %s | %s\n' "$1" "$2" "$3" >> "$PROBLEMS"
  printf '[L11]   ❌ 断言红：%s %s —— %s\n' "$1" "$2" "$3"
}

# Flyway 的 advisory lock key = LOCK_MAGIC_NUM + javaStringHashCode("\"schema\".\"table\"")
# ⚠️ **实测校准过**（2026-10-26）：message-rpc 的账本
#    `flyway_schema_history_message` ⇒ locknum=77432090236453
#    ⇒ 外部用 `pg_try_advisory_xact_lock(77432090236453)` 能把真 rpc 进程
#       卡在迁移之前（实测 pg_stat_activity 里能看到它在跑这条查询）。
LOCK_MAGIC_NUM=77431708279161
locknum_of() {  # $1 = 账本表名 ⇒ 打印 locknum
  python3 -c "
M=$LOCK_MAGIC_NUM
h=0
for c in '\"public\".\"$1\"': h=(31*h+ord(c))&0xFFFFFFFF
if h>=2**31: h-=2**32
print(M+h)"
}

# ---------------------------------------------------------------- 模块清单
# 三个域：**都能用真 Spring Boot 上下文 + 真 Flyway + 真连库起来**的 rpc 模块
#   （2026-10-26 逐个实测：真 `*RpcApplication` + 真 fat jar/classpath 都起得来）。
#   follow  : gRPC 8082 · 2 条迁移 · 账本 flyway_schema_history_follow
#             ✅ **不用任何绕开参数**（它的 yaml 里是三斜杠 discovery:///user-rpc）
#   task    : gRPC 9005 · 2 条迁移 · 账本 flyway_schema_history_task
#             ✅ **不用任何绕开参数**（task-rpc 没有 @GrpcClient 客户端）
#   message : gRPC 8082 · 5 条迁移 · 账本 flyway_schema_history_message
#             ⚠️ **必须绕开两处**（见下）⇒ **这是绕开已登记缺陷，不是缺陷已修**
# ⚠️ **端口要错开**（message 与 follow 默认都是 8082）⇒ 启动参数里逐进程指定。
MODULES="follow task message"

# 每个模块的 gRPC 端口（**只在本机测试用**，与生产 yaml 无关）
grpc_port_of() {
  case "$1" in
    follow)  echo 19182 ;;
    message) echo 19183 ;;
    comment) echo 19184 ;;
    task)    echo 19185 ;;
    *)       echo 19186 ;;
  esac
}

# app_class <模块> ⇒ 该模块**自己的** *RpcApplication 全限定名
#   ⇒ 驱动用它起**真 rpc 进程**（真 gRPC server + 真 Kafka + 真 Redis + 真 Flyway）
app_class() {
  case "$1" in
    follow)  echo "com.rag.follow.follow.rpc.FollowRpcApplication" ;;
    message) echo "com.rag.message.message.rpc.MessageRpcApplication" ;;
    comment) echo "com.rag.comment.comment.rpc.CommentRpcApplication" ;;
    task)    echo "com.rag.task.task.rpc.TaskRpcApplication" ;;
    *)       echo "" ;;
  esac
}

# expected_ledger <模块> ⇒ 该模块**自己的**账本表名（从它的 application.yml 读）
expected_ledger() {
  python3 - "$ROOT" "$1" <<'PY'
import sys, yaml, os
root, mod = sys.argv[1], sys.argv[2]
p = os.path.join(root, "service", mod, f"{mod}-rpc", "src", "main", "resources", "application.yml")
doc = yaml.safe_load(open(p, encoding="utf-8")) or {}
fw = ((doc.get("spring") or {}).get("flyway")) or {}
print(fw.get("table") or "flyway_schema_history")
PY
}

# module_args() —— 每个模块的启动参数（**只针对启动期的外部依赖，不改任何生产 yaml**）
#
# ⚠️ **message 的两处绕开（必须写清楚：绕开 ≠ 修好）**
#   message-rpc 的 yaml 里 gRPC client 写的是**两斜杠** `discovery://user-rpc` /
#   `discovery://admin-rpc` ⇒ devh 的 `DiscoveryClientResolverFactory.newNameResolver`
#   判 `uri.getPath()` 长度 > 1 ⇒ path 是空串 ⇒ 抛
#   `IllegalArgumentException: Incorrectly formatted target uri`。
#   ⇒ **真 rpc 进程在 context refresh 阶段就炸**（`grpcClientBeanPostProcessor` 的
#      `@PostConstruct` 里建 channel）⇒ **连 Flyway 都跑不到**（实测，2026-10-26）。
#   ⇒ 本脚本用**启动参数**把它改回三斜杠（`--grpc.client.X.address=discovery:///X`），
#      **不改任何生产 yaml** ⇒ 该缺陷**仍然存在**，只是被绕开了。
#
# ⚠️ **--mutate-shared-ledger**（反向注入）：把 message 与 task 的账本表名
#   **改成同一个** `flyway_schema_history_shared` ⇒ 复现 AGENTS.md §6.3 问题 4
#   的**原始病根**（两模块共用一本账 ⇒ 后启动者把自己的迁移判为「已应用」而静默跳过）
#   ⇒ 判定器**必须红**在「表没建出来 / 账本被覆盖」这条断言上
#   ⇒ 这是「守门非空转」的证据（AGENTS.md §3.3）。
module_args() {
  # $1 = 模块名 · $2 = 场景名（默认 S1）
  # ⚠️ **反向注入只作用于 S1**（message ‖ task 共用一本账）
  #    ⇒ S2/S3/S4 保持「各自独立账本」⇒ 红条**只**落在被注入的那一处
  local sc="${2:-S1}"
  local mutate=0
  if [ "$MUTATE" = "1" ] && [ "$sc" = "S1" ]; then mutate=1; fi
  case "$1" in
    follow)  echo "" ;;
    task)
      if [ "$mutate" = "1" ]; then
        echo "--spring.flyway.table=flyway_schema_history_shared"
      else
        echo ""
      fi ;;
    message)
      local a="--grpc.client.user-rpc.address=discovery:///user-rpc --grpc.client.admin-rpc.address=discovery:///admin-rpc"
      if [ "$mutate" = "1" ]; then
        echo "$a --spring.flyway.table=flyway_schema_history_shared"
      else
        echo "$a"
      fi ;;
    *)       echo "" ;;
  esac
}

# ---------------------------------------------------------------- classpath
CP_CACHE="$TMP/cp.txt"
cp_for() {  # $1 = 模块名 ⇒ 打印该模块的 classpath（自己的 classes + resources + 依赖 + grpc-inprocess）
  local m="$1"
  local out="$TMP/cp-$m.txt"
  if [ ! -f "$out" ]; then
    log "解析 classpath：service/${m}/${m}-rpc"
    ( cd "$ROOT" && mvn -o -q -pl "service/$m/$m-rpc" dependency:build-classpath \
        -Dmdep.outputFile="$out" -DincludeScope=runtime ) \
      || fail_env "mvn dependency:build-classpath 失败：$m"
    # devh 的 InProcessOrAlternativeChannelFactory 需要 io.grpc:inprocess
    # ⚠️ like/article 的 pom 里它是 **test scope** ⇒ 不在 runtime classpath ⇒ 真启动炸
    #    `NoClassDefFoundError: io/grpc/inprocess/InProcessChannelBuilder`
    # ⇒ 这里**显式补上**（不改 pom —— 本 lane 无 service/** 写权）
    local inproc
    inproc="$(ls "$HOME"/.m2/repository/io/grpc/grpc-inprocess/*/grpc-inprocess-*.jar 2>/dev/null \
              | grep -v sources | head -1)"
    [ -n "$inproc" ] && printf '%s' "$(cat "$out"):$inproc" > "$out"
  fi
  # ⚠️ 还要带上**模块自己的 classes**（真 rpc 进程的 `*RpcApplication` 在里面）
  printf '%s:%s' "$(cat "$out")" "$ROOT/service/$m/$m-rpc/target/classes:$ROOT/service/$m/$m-rpc/src/main/resources"
}

FIRST_CP="$(cp_for follow)"
for m in $MODULES; do cp_for "$m" >/dev/null; done
log "编译驱动 tools/difftest/flyway-rpc-boot.java"
javac -cp "$FIRST_CP" -d "$TMP/driver" "$ROOT/tools/difftest/flyway-rpc-boot.java" \
  || fail_env "驱动编译失败"

# ---------------------------------------------------------------- 起 PG
log "起独立 PG 16 容器：${CONTAINER}（网络 ${NETWORK}）"
docker network create "$NETWORK" >/dev/null || fail_env "docker network create 失败"
docker run -d --name "$CONTAINER" --network "$NETWORK" \
  -e POSTGRES_USER="$PG_USER" -e POSTGRES_PASSWORD="$PG_PASS" -e POSTGRES_DB=postgres \
  -p 127.0.0.1::5432 "$PG_IMAGE" >/dev/null || fail_env "docker run 失败"

PORT=""
for _ in $(seq 1 90); do
  PORT="$(docker port "$CONTAINER" 5432/tcp 2>/dev/null | head -1 | sed 's/.*://')"
  if [ -n "$PORT" ] && docker exec "$CONTAINER" pg_isready -U "$PG_USER" >/dev/null 2>&1; then
    # ⚠️ **必须等初始化真正做完**：postgres 镜像先起一个「临时 server」跑 initdb，
    #    `pg_isready` 在那个窗口里也是成功的 ⇒ 那时建的库会被丢掉（实测踩过）。
    #    镜像在临时实例停掉后会打这一行 ⇒ 看到它才说明正式实例马上起来。
    if docker logs "$CONTAINER" 2>&1 | grep -q "PostgreSQL init process complete"; then
      break
    fi
  fi
  PORT=""
  sleep 1
done
[ -n "$PORT" ] || fail_env "PG 容器没就绪"
log "PG 16 就绪：127.0.0.1:$PORT"

# ---------------------------------------------------------------- 真 rpc 进程
# start_rpc <模块> <db> <start_at_epoch_ms> <额外参数...>
#   ⇒ 起**真 rpc 进程**（该模块自己的 *RpcApplication ⇒ 真 gRPC server + 真 Flyway + 真连库）
start_rpc() {
  local m="$1"; local db="$2"; local at="$3"; shift 3
  local extra; extra="$(module_args "$m" S1)"
  local gp; gp="$(grpc_port_of "$m")"
  # shellcheck disable=SC2086
  ( cd "$ROOT" && \
    POSTGRES_HOST=127.0.0.1 POSTGRES_PORT="$PORT" POSTGRES_DB="$db" \
    POSTGRES_USER="$PG_USER" POSTGRES_PASSWORD="$PG_PASS" \
    NACOS_ADDR=127.0.0.1 NACOS_PORT=1 KAFKA_BROKERS=127.0.0.1:9092 \
    REDIS_HOST=127.0.0.1 REDIS_PORT=6379 \
    java -cp "$TMP/driver:$(cp_for "$m")" FlywayRpcBoot migrate \
      --module="$m" --app="$(app_class "$m")" --start-at="$at" --out="$TMP/runs/$m.json" \
      --grpc-port="$gp" --grpc.server.port="$gp" \
      --spring.cloud.nacos.discovery.server-addr=127.0.0.1:1 \
      $extra "$@" \
      >"$TMP/logs/$m.out" 2>"$TMP/logs/$m.err"
    echo "$?" > "$TMP/runs/$m.rc" )
}

wait_rpc() {  # $@ = 结果文件名（不含 .rc）
  local pids=("$@")
  local i
  for i in "${!pids[@]}"; do
    while [ ! -f "$TMP/runs/${pids[$i]}.rc" ]; do sleep 0.2; done
  done
}

# ---------------------------------------------------------------- 屏障
# hold_lock <db> <locknum> <秒> ⇒ 从**另一个** psql 会话占住 advisory lock
#   ⚠️ 用 **session 级** `pg_advisory_lock`（不是 xact 级）——
#   实测：Flyway 那边用的是 `pg_try_advisory_xact_lock`（flyway-database-postgresql
#   `PostgreSQLConfigurationExtension.isTransactionalLock()`），
#   而 PG 的 **session 级锁与 xact 级锁互相冲突**（同一个 key）⇒ session 级足够当屏障。
#   ⚠️ 释放必须由**持锁的那个会话**做 —— 实测从别的会话调 `pg_advisory_unlock` 会报
#   "you don't own a lock of type ExclusiveLock" ⇒ 改成 **杀掉持锁的 psql 会话**
#   （psql 断开 ⇒ 连接释放 ⇒ 锁自动释放）。
hold_lock() {
  local db="$1"; local lk="$2"; local secs="$3"
  docker exec -d "$CONTAINER" psql -U "$PG_USER" -d "$db" -q \
    -c "SELECT pg_advisory_lock($lk); SELECT pg_sleep($secs);" >/dev/null 2>&1
}

# barrier_probe <db> <locknum> ⇒ 目标进程**真的**卡在 lock 查询上吗？
#   ⇒ 打印 "yes"/"no"；这是「并发是真的」的证据（AGENTS.md §3.3）
barrier_probe() {
  local db="$1"; local lk="$2"
  local n
  n="$(psql_q "$db" "SELECT count(*) FROM pg_stat_activity WHERE datname='$db' AND query LIKE '%pg_try_advisory_xact_lock($lk)%'")"
  [ "$n" -ge 1 ] 2>/dev/null && echo yes || echo no
}

# barrier_probe_all <db> <lk1> <lk2> ... ⇒ 一条 SQL 查所有锁（避免每轮多次 docker exec）
barrier_probe_all() {
  local db="$1"; shift
  local cond=""
  for lk in "$@"; do
    [ -n "$cond" ] && cond="$cond OR "
    cond="${cond}query LIKE '%pg_try_advisory_xact_lock(${lk})%'"
  done
  psql_q "$db" "SELECT count(*) FROM pg_stat_activity WHERE datname='$db' AND ($cond)"
}

release_lock() {  # $1 = db, $2 = locknum ⇒ 放锁（杀掉持锁的 psql 会话）
  local db="$1"; local lk="$2"
  docker exec "$CONTAINER" psql -U "$PG_USER" -d "$db" -q \
    -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='$db' AND query LIKE '%pg_advisory_lock($lk)%' AND pid<>pg_backend_pid()" \
    >/dev/null 2>&1
}

# ---------------------------------------------------------------- 表存在性检查
# expected_tables <模块> ⇒ 打印该模块迁移脚本里 CREATE TABLE 的表名（逐行）
expected_tables() {
  python3 - "$ROOT" "$1" <<'PY'
import os, re, sys
root, mod = sys.argv[1], sys.argv[2]
d = os.path.join(root, "service", mod, f"{mod}-rpc", "src", "main", "resources", "db", "migration")
rx = re.compile(r"create\s+(?:unlogged\s+)?table\s+(?:if\s+not\s+exists\s+)?\"?([A-Za-z0-9_]+)\"?", re.I)
out = []
for fn in sorted(os.listdir(d)):
    if not fn.endswith(".sql"):
        continue
    for m in rx.finditer(open(os.path.join(d, fn), encoding="utf-8").read()):
        t = m.group(1).lower()
        if t not in out:
            out.append(t)
print(" ".join(out))
PY
}

# check_tables <db> <模块> ⇒ 打印缺失的表（空 ⇒ 全建出来了）
check_tables() {
  local db="$1"; local m="$2"
  local exp miss=""
  exp="$(expected_tables "$m")"
  for t in $exp; do
    local n
    n="$(psql_q "$db" "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='$t'")"
    [ "$n" = "0" ] && miss="$miss $t"
  done
  echo "$miss"
}

# ---------------------------------------------------------------- 断言检查器
# check_module <场景> <db> <结果文件基名> <模块>
#   ⇒ 对一个域的结果跑 A1/A2/A3/A4/A5 五条命名断言
check_module() {
  local sc="$1"; local db="$2"; local base="$3"; local m="$4"
  local f="$TMP/runs/$base.json"
  [ -f "$f" ] || { add_problem "$sc" "A1-进程启动" "${base}：没有结果文件"; return; }

  # A1 进程启动
  local ok; ok="$(python3 - "$f" <<'PYEOF'
import json, sys
print('1' if json.load(open(sys.argv[1], encoding='utf-8')).get('ok') else '0')
PYEOF
)"
  if [ "$ok" != "1" ]; then
    local err; err="$(python3 - "$f" <<'PYEOF'
import json, sys
d = json.load(open(sys.argv[1], encoding='utf-8'))
print(((d.get('error') or '') + ' | ' + str(d.get('errorType') or ''))[:300].replace(chr(10), ' '))
PYEOF
)"
    add_problem "$sc" "A1-进程启动" "${base}：ok=false ⇒ $err"
    # ⚠️ **不提前 return**：继续查 A2/A3 ⇒ 反向注入时红条会**同时**落在
    #    「进程起不来」与「表没建出来」上 —— 后者才是 AGENTS.md §6.3 问题 4 的病根形态
    #    （共用一本账 ⇒ 后启动者的迁移被静默跳过 ⇒ **它的表压根没建出来**）
  fi

  # A2 真 gRPC server 在监听（TCP 探测 + 日志里的 started 行）
  local listen; listen="$(python3 -c "
import json;print(json.load(open('$f',encoding='utf-8')).get('grpcServerListening'))")"
  if [ "$listen" != "True" ]; then
    add_problem "$sc" "A2-gRPC-监听" "${base}：TCP 探测 127.0.0.1:<${m} 的 gRPC 端口> 未监听（grpcServerListening=${listen}）"
  fi
  if ! grep -q "gRPC Server started" "$TMP/logs/$base.out" 2>/dev/null; then
    add_problem "$sc" "A2-gRPC-监听" "${base}：日志里没有 'gRPC Server started' 行"
  fi

  # A3 表全部建出来
  local miss; miss="$(check_tables "$db" "$m")"
  if [ -n "$miss" ]; then
    add_problem "$sc" "A3-表建出" "${base}：表没建出来 ⇒$miss"
  fi

  # A4 账本一致：账本行数 == 该域的 SQL 迁移条数（不重复应用、不被覆盖）
  #    ⚠️ 期望值 = **SQL 条数 + BASELINE 条数**
  #       （共享库里并发启动时，后启动者会遇到「schema 已非空」⇒ baseline-on-migrate
  #         在 version 0 处补一条 BASELINE 记录 ⇒ 账本多一行，但**没有跳过任何迁移**）
  local led; led="$(python3 - "$f" <<'PYEOF'
import json, sys
d = json.load(open(sys.argv[1], encoding='utf-8'))
mig = d.get('migrations') or []
sql = [x for x in mig if x.get('type') == 'SQL']
base = [x for x in mig if x.get('type') == 'BASELINE']
print((d.get('effectiveTable') or '') + '|' + str(len(sql) + len(base)))
PYEOF
)"
  local tbl="${led%%|*}"; local nsql="${led##*|}"
  if [ -n "$tbl" ]; then
    local rows; rows="$(psql_q "$db" "SELECT count(*) FROM public.\"$tbl\"" 2>/dev/null)"
    if [ "$rows" != "$nsql" ]; then
      add_problem "$sc" "A4-账本一致" "${base}：账本 $tbl 行数=${rows}，期望=${nsql}（迁移条数）⇒ 重复应用或被覆盖"
    fi
  fi

  # A5 账本独立：生效账本表名 == 该域自己的账本
  #    ⚠️ **反向注入模式下故意违反**（message/task 被改成共享账本）⇒ 那时跳过本条
  if [ "$MUTATE" != "1" ]; then
    local exp; exp="$(expected_ledger "$m")"
    if [ "$tbl" != "$exp" ]; then
      add_problem "$sc" "A5-账本独立" "${base}：生效账本=${tbl}，期望=$exp"
    fi
  fi
}

# =============================================================================
# S1 —— 三域真 rpc 进程并发同库（边界 ①）
# =============================================================================
run_s1() {
  local db="l11_s1"
  log "──────────────────────────────────────────────────────────"
  log "S1 三域真 rpc 进程并发同库（follow ‖ task ‖ message）"
  if [ "$MUTATE" = "1" ]; then
    log "  ⚠️ 反向注入模式：message 与 task 的账本表被改成同一个 flyway_schema_history_shared"
    log "     ⇒ 预期：判定器**红**在「表没建出来 / 迁移失败」上（证明守门非空转）"
  fi
  db_create "$db" || fail_env "建库失败：$db"

  # 屏障：预先占住三个模块各自的账本锁 ⇒ 三个进程都卡在 Flyway 迁移入口
  local lks=()
  local m
  for m in $MODULES; do
    local tbl
    tbl="$(expected_ledger "$m")"
    # 反向注入模式下，message/task 的**生效**账本表被改成了共享表
    if [ "$MUTATE" = "1" ] && { [ "$m" = "message" ] || [ "$m" = "task" ]; }; then
      tbl="flyway_schema_history_shared"
    fi
    local lk; lk="$(locknum_of "$tbl")"
    lks+=("$lk")
    log "  屏障：$m 账本表=$tbl ⇒ advisory locknum=$lk"
  done

  local lk
  for lk in "${lks[@]}"; do hold_lock "$db" "$lk" 600; done
  sleep 2

  local at=$(( $(now_ms) + 9000 ))
  for m in $MODULES; do start_rpc "$m" "$db" "$at" & done

  # 等三个进程都真的卡在 barrier 上
  log "  等三个进程都卡在 advisory lock 上（这是「真并发」的证据）…"
  local ok=1 i
  for i in $(seq 1 120); do
    local n
    n="$(barrier_probe_all "$db" "${lks[@]}")"
    if [ "${n:-0}" -ge "${#lks[@]}" ] 2>/dev/null; then ok=0; break; fi
    sleep 1
  done
  if [ "$ok" = "0" ]; then
    log "  ✅ 三个进程**同时**卡在 Flyway 的 advisory lock 上 ⇒ 屏障生效"
  else
    add_problem "S1" "A0-屏障生效" "没观察到进程停在 pg_try_advisory_xact_lock 上 ⇒ 并发只是墙钟凑巧"
  fi

  # 放锁 ⇒ 三个进程同时进入迁移
  for lk in "${lks[@]}"; do release_lock "$db" "$lk"; done
  log "  已放锁 ⇒ 三个进程同时进入迁移"
  wait_rpc $MODULES

  for m in $MODULES; do
    if [ -f "$TMP/runs/$m.json" ]; then
      python3 - "$TMP/runs/$m.json" <<'PY'
import json, sys
d = json.load(open(sys.argv[1], encoding="utf-8"))
sql = [x for x in (d.get("migrations") or []) if x.get("type") == "SQL"]
base = [x for x in (d.get("migrations") or []) if x.get("type") == "BASELINE"]
if d.get("ok"):
    print("    %s %-10s ok 账本=%-34s baseline=%-3s SQL=%d 条 BASELINE行=%s gRPC监听=%s 用时=%sms"
          % ("✅" if d.get("grpcServerListening") else "⚠️", d.get("module"), d.get("effectiveTable"),
             d.get("effectiveBaselineVersion"), len(sql), [b.get("version") for b in base],
             d.get("grpcServerListening"), d.get("durationMs")))
else:
    print("    ❌ %-10s FAILED %s" % (d.get("module"), (d.get("error") or "")[:200].replace("\n", " ")))
PY
    fi
    check_module "S1" "$db" "$m" "$m"
  done
}

# =============================================================================
# S2 —— 迁移中途失败（边界 ② 之一）
# =============================================================================
run_s2() {
  local db="l11_s2"
  log "──────────────────────────────────────────────────────────"
  log "S2 迁移中途失败：给 follow 注入一个必然失败的 V_N（不碰仓库里的 .sql）"
  db_create "$db" || fail_env "建库失败：$db"

  # 人造失败迁移：放在**临时目录**里，用 `filesystem:` location 追加进去
  # ⇒ `spring.flyway.locations` 在**启动参数**里追加 ⇒ 不改任何生产 yaml
  local faildir="$TMP/mig/fail"
  mkdir -p "$faildir"
  cat > "$faildir/V900__l11_injected_failure.sql" <<'SQL'
-- L11 注入的必然失败迁移（只存在于临时目录，不进仓库）
-- 目的：观察「一个模块的迁移中途失败」时，另一个进程与库的状态
CREATE TABLE l11_injected_marker (id BIGINT PRIMARY KEY);
SELECT l11_this_function_does_not_exist();
SQL

  local at=$(( $(now_ms) + 6000 ))
  local gp; gp="$(grpc_port_of follow)"
  # follow 带失败迁移；task 同时起（正常）⇒ 看另一个进程是否受影响
  ( cd "$ROOT" && POSTGRES_HOST=127.0.0.1 POSTGRES_PORT="$PORT" POSTGRES_DB="$db" \
    POSTGRES_USER="$PG_USER" POSTGRES_PASSWORD="$PG_PASS" NACOS_ADDR=127.0.0.1 NACOS_PORT=1 \
    KAFKA_BROKERS=127.0.0.1:9092 REDIS_HOST=127.0.0.1 \
    java -cp "$TMP/driver:$(cp_for follow)" FlywayRpcBoot migrate \
      --module=follow --app="$(app_class follow)" --start-at="$at" --out="$TMP/runs/s2_follow.json" \
      --grpc-port="$gp" --grpc.server.port="$gp" \
      --spring.cloud.nacos.discovery.server-addr=127.0.0.1:1 \
      --extra-locations="filesystem:$faildir" \
      >"$TMP/logs/s2_follow.out" 2>"$TMP/logs/s2_follow.err"
    echo "$?" > "$TMP/runs/s2_follow.rc" ) &
  local gpc; gpc="$(grpc_port_of task)"
  ( cd "$ROOT" && POSTGRES_HOST=127.0.0.1 POSTGRES_PORT="$PORT" POSTGRES_DB="$db" \
    POSTGRES_USER="$PG_USER" POSTGRES_PASSWORD="$PG_PASS" NACOS_ADDR=127.0.0.1 NACOS_PORT=1 \
    KAFKA_BROKERS=127.0.0.1:9092 REDIS_HOST=127.0.0.1 \
    java -cp "$TMP/driver:$(cp_for task)" FlywayRpcBoot migrate \
      --module=task --app="$(app_class task)" --start-at="$at" --out="$TMP/runs/s2_task.json" \
      --grpc-port="$gpc" --grpc.server.port="$gpc" \
      --spring.cloud.nacos.discovery.server-addr=127.0.0.1:1 \
      >"$TMP/logs/s2_task.out" 2>"$TMP/logs/s2_task.err"
    echo "$?" > "$TMP/runs/s2_task.rc" ) &
  wait_rpc s2_follow s2_task

  log "  follow 的结果（**故意**失败）："
  python3 - "$TMP/runs/s2_follow.json" <<'PY'
import json, sys
d = json.load(open(sys.argv[1], encoding="utf-8"))
print("    ok=%s" % d.get("ok"))
if not d.get("ok"):
    print("    错误类型=%s" % d.get("errorType"))
    print("    错误=%s" % (d.get("error") or "")[:300].replace("\n", " "))
    for k in sorted(d):
        if k.startswith("cause"):
            print("    %s=%s" % (k, d[k][:220].replace("\n", " ")))
sql = [x for x in (d.get("migrations") or []) if x.get("type") == "SQL"]
print("    SQL 迁移状态：%s" % [(x.get("version"), x.get("state")) for x in sql])
PY
  log "  task（另一个进程）的结果："
  python3 - "$TMP/runs/s2_task.json" <<'PY'
import json, sys
d = json.load(open(sys.argv[1], encoding="utf-8"))
sql = [x for x in (d.get("migrations") or []) if x.get("type") == "SQL"]
print("    ok=%s 账本=%s SQL=%d 条 状态=%s gRPC监听=%s"
      % (d.get("ok"), d.get("effectiveTable"), len(sql),
         sorted({x.get("state") for x in sql}), d.get("grpcServerListening")))
PY
  log "  库里的状态："
  local fr cr marker rel
  fr="$(psql_q "$db" "SELECT count(*) FROM public.flyway_schema_history_follow" 2>/dev/null)"
  cr="$(psql_q "$db" "SELECT count(*) FROM public.flyway_schema_history_task" 2>/dev/null)"
  marker="$(psql_q "$db" "SELECT count(*) FROM information_schema.tables WHERE table_name='l11_injected_marker'" 2>/dev/null)"
  rel="$(psql_q "$db" "SELECT count(*) FROM information_schema.tables WHERE table_name='follow_relation'" 2>/dev/null)"
  log "    follow 账本行：$fr"
  log "    task 账本行：$cr"
  log "    失败迁移建的表 l11_injected_marker 是否残留：$marker"
  log "    follow 的表是否建出：$rel"

  # A6 无残留：失败迁移不留残表（Flyway 的 DDL 迁移在事务里 ⇒ 回滚干净）
  if [ "$marker" != "0" ]; then
    add_problem "S2" "A6-无残留" "失败迁移建的 l11_injected_marker 残留了 ⇒ 迁移没有回滚干净"
  fi
  # task（另一个进程）必须完全不受影响
  check_module "S2" "$db" "s2_task" "task"
}

# =============================================================================
# S3 —— 同一模块起两个副本（同账本同表）（边界 ② 之二）
# =============================================================================
run_s3() {
  local db="l11_s3"
  log "──────────────────────────────────────────────────────────"
  log "S3 同一模块两个副本同账本同表（follow × 2，同一个库）"
  db_create "$db" || fail_env "建库失败：$db"

  local at=$(( $(now_ms) + 8000 ))
  # 两个副本：同一个模块 ⇒ 同一个账本表 ⇒ 同一个 advisory lock ⇒ 天然串行
  local i
  for i in 1 2; do
    local gp; gp="$(( $(grpc_port_of follow) + i ))"
    ( cd "$ROOT" && POSTGRES_HOST=127.0.0.1 POSTGRES_PORT="$PORT" POSTGRES_DB="$db" \
      POSTGRES_USER="$PG_USER" POSTGRES_PASSWORD="$PG_PASS" NACOS_ADDR=127.0.0.1 NACOS_PORT=1 \
      KAFKA_BROKERS=127.0.0.1:9092 REDIS_HOST=127.0.0.1 \
      java -cp "$TMP/driver:$(cp_for follow)" FlywayRpcBoot migrate \
        --module=follow --app="$(app_class follow)" --start-at="$at" --out="$TMP/runs/s3_follow_$i.json" \
        --grpc-port="$gp" --grpc.server.port="$gp" \
        --spring.cloud.nacos.discovery.server-addr=127.0.0.1:1 \
        >"$TMP/logs/s3_follow_$i.out" 2>"$TMP/logs/s3_follow_$i.err"
      echo "$?" > "$TMP/runs/s3_follow_$i.rc" ) &
  done
  wait_rpc s3_follow_1 s3_follow_2

  for i in 1 2; do
    python3 - "$TMP/runs/s3_follow_$i.json" "$i" <<'PY'
import json, sys
d = json.load(open(sys.argv[1], encoding="utf-8"))
sql = [x for x in (d.get("migrations") or []) if x.get("type") == "SQL"]
print("    副本%s ok=%s 账本=%s SQL=%d 条 状态=%s gRPC监听=%s 用时=%sms"
      % (sys.argv[2], d.get("ok"), d.get("effectiveTable"), len(sql),
         sorted({x.get("state") for x in sql}), d.get("grpcServerListening"), d.get("durationMs")))
if not d.get("ok"):
    print("      错误=%s" % (d.get("error") or "")[:250].replace("\n", " "))
PY
    check_module "S3" "$db" "s3_follow_$i" "follow"
  done
  local rows tbls content
  rows="$(psql_q "$db" "SELECT count(*) FROM public.flyway_schema_history_follow" 2>/dev/null)"
  content="$(psql_q "$db" "SELECT string_agg(version||':'||description, ', ' ORDER BY installed_rank) FROM public.flyway_schema_history_follow" 2>/dev/null)"
  tbls="$(psql_q "$db" "SELECT count(*) FROM information_schema.tables WHERE table_name='follow_relation'" 2>/dev/null)"
  log "  账本行数（应为 2 条 V1/V2，**不是** 4 条 ⇒ 没有重复应用）：$rows"
  log "  账本内容：$content"
  log "  follow_relation 表数（应为 1）：$tbls"
  if [ "$rows" != "2" ]; then
    add_problem "S3" "A4-账本一致" "同模块两副本并发后账本行数=${rows}（期望 2）⇒ 迁移被重复应用"
  fi
  if [ "$tbls" != "1" ]; then
    add_problem "S3" "A3-表建出" "follow_relation 表数=${tbls}（期望 1）"
  fi
}

# =============================================================================
# S4 —— N 轮 + 随机启动延迟（边界 ③）
# =============================================================================
run_s4() {
  log "──────────────────────────────────────────────────────────"
  log "S4 N 轮 + 随机启动延迟（rounds=${ROUNDS}）"
  local r
  for r in $(seq 1 "$ROUNDS"); do
    local db="l11_s4_r${r}"
    db_create "$db" || fail_env "建库失败：$db"
    # 随机延迟：每个模块一个 0~4000ms 的随机偏移 ⇒ 制造「极端时序」
    local at=$(( $(now_ms) + 15000 ))
    local m
    for m in $MODULES; do
      local dly; dly="$(python3 -c 'import random;print(random.randint(0,4000))')"
      local gp; gp="$(grpc_port_of "$m")"
      ( cd "$ROOT" && POSTGRES_HOST=127.0.0.1 POSTGRES_PORT="$PORT" POSTGRES_DB="$db" \
        POSTGRES_USER="$PG_USER" POSTGRES_PASSWORD="$PG_PASS" NACOS_ADDR=127.0.0.1 NACOS_PORT=1 \
        KAFKA_BROKERS=127.0.0.1:9092 REDIS_HOST=127.0.0.1 \
        java -cp "$TMP/driver:$(cp_for "$m")" FlywayRpcBoot migrate \
          --module="$m" --app="$(app_class "$m")" --start-at="$((at + dly))" --out="$TMP/runs/s4_r${r}_$m.json" \
          --grpc-port="$gp" --grpc.server.port="$gp" \
          --spring.cloud.nacos.discovery.server-addr=127.0.0.1:1 \
          $(module_args "$m" S4) \
          >"$TMP/logs/s4_r${r}_$m.out" 2>"$TMP/logs/s4_r${r}_$m.err"
        echo "$?" > "$TMP/runs/s4_r${r}_$m.rc" ) &
    done
    wait_rpc $(for m in $MODULES; do echo "s4_r${r}_$m"; done)
    local line="  轮 ${r}（随机延迟 0~4000ms）："
    for m in $MODULES; do
      if [ -f "$TMP/runs/s4_r${r}_$m.json" ]; then
        line="$line $m=$(python3 -c "
import json
d=json.load(open('$TMP/runs/s4_r${r}_$m.json',encoding='utf-8'))
print('ok' if d.get('ok') else 'FAIL')")"
      fi
    done
    log "$line"
    for m in $MODULES; do
      check_module "S4-r${r}" "$db" "s4_r${r}_$m" "$m"
    done
  done
}

# ---------------------------------------------------------------- 跑
run_s1
run_s2
run_s3
run_s4

# ---------------------------------------------------------------- 汇总
log "──────────────────────────────────────────────────────────"
log "汇总结果"
python3 - "$TMP" "$ROOT" <<'PY'
import json, os, sys, glob
tmp, root = sys.argv[1], sys.argv[2]
problems = []
for f in sorted(glob.glob(os.path.join(tmp, "runs", "*.json"))):
    name = os.path.basename(f)[:-5]
    d = json.load(open(f, encoding="utf-8"))
    if name.startswith("s2_follow"):
        continue   # S2 的 follow 是**故意**失败的
    if not d.get("ok"):
        problems.append("%s: 迁移失败 %s" % (name, (d.get("error") or "")[:120]))
        continue
    sql = [x for x in (d.get("migrations") or []) if x.get("type") == "SQL"]
    bad = [x for x in sql if x.get("state") != "SUCCESS"]
    if bad:
        problems.append("%s: 有非 SUCCESS 的迁移 %s" % (name, [(x.get("version"), x.get("state")) for x in bad]))
    if not sql:
        problems.append("%s: 一条迁移都没执行" % name)
print("  检查了 %d 个进程的结果文件" % len(glob.glob(os.path.join(tmp, "runs", "*.json"))))
if problems:
    print("  ❌ 问题：")
    for p in problems:
        print("     · %s" % p)
    sys.exit(1)
print("  ✅ 所有非故意失败的进程：迁移全部 SUCCESS")
PY
RC=$?

if [ -s "$PROBLEMS" ]; then
  log "  命名断言红条："
  while IFS='|' read -r sc an dt; do
    [ -n "$an" ] && log "     · [$sc] $an —— $dt"
  done < "$PROBLEMS"
  RC=1
else
  log "  ✅ 所有命名断言（A1/A2/A3/A4/A5/A6）都绿"
fi

log "──────────────────────────────────────────────────────────"
if [ "$RC" = "0" ]; then
  log "✅ 结论：真 rpc 进程并发同库没有互相踩"
else
  log "❌ 结论：有差异（见上面的红断言）"
fi
exit "$RC"
