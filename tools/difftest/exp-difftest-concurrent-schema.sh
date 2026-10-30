#!/usr/bin/env bash
# ════════════════════════════════════════════════════════════════════════════
#  exp-difftest-concurrent-schema — UNRESOLVED-USERRPC-005 的双侧对称实验
# ════════════════════════════════════════════════════════════════════════════
#
# 【它回答什么问题】
#   docs/DRIFT/users/user/rpc.md §9 的 **UNRESOLVED-USERRPC-005**：
#     「**建表责任交叉** —— `users_user` 由 **user-rpc 与 admin-rpc 都建**」
#     台账原文写着「**本组件单侧验不完**：必须与 admin-rpc 的结论合并判读」。
#
#   本脚本把「单侧」补成**双侧对称**：
#     · Go 侧：go-user-rpc + go-admin-rpc **并发**对**同一个空库**启动
#     · RAG 侧：user-rpc     + admin-rpc     **并发**对**同一个空库**启动
#     ⇒ 比「两侧起没起来 + 库里有几张表 + 表里有没有对方建的那张」。
#
# 【两侧的机制差异（本脚本要比的就是这个）】
#   Go  ：user-rpc → AutoMigrate(&User{}, &UserAvatarHistory{})   ⇒ users, user_avatar_history
#         admin-rpc → AutoMigrate(&Admin{}, &AdminInvite{}, &User{}) ⇒ admins, admin_invites, users
#         ⇒ **两个服务都建 `users`**（共享表，靠 GORM 的 HasTable + CREATE TABLE IF NOT EXISTS）
#   RAG ：两个服务都调 SchemaInitializer.applyAll（**全部 4 张 DDL**）
#         ⇒ **两个服务都建全部 4 张表**，靠 **PG advisory lock（键 8_675_309）串行化**
#
# 【⚠️ 三条纪律】
#   1. **绝不修改冻结的 Go 仓库**：Go 的运行配置全部来自本脚本在 /tmp 里现生成的 yaml 副本。
#   2. **绝不碰共享的 go_db / rag_db**：本脚本只 DROP/CREATE 自己的 uu_both_* 库。
#      ⚠️ 而且**自带容器**（uudifftest-*）—— 共享容器会被别的会话回收（见 PIT-USERS-SHARED-009）。
#   3. **端口独占**：Go 19005/19015/19105/19115 · RAG 59211/59311 · PG 55434 · Redis 56382。
#
# 【用法】
#   ./exp-difftest-concurrent-schema.sh              # 全流程
#   ./exp-difftest-concurrent-schema.sh --no-build   # 复用已有 jar
set -euo pipefail
# 🔴 RAG 侧 **fail-closed 密钥**（P0 #8）：占位符解析不出来 ⇒ 进程起不来
#    ⇒ 探针「起 RAG 侧」这一步必挂 ⇒ 看起来像产品缺陷，其实是探针自己没给密钥。
#    （2026-10-30 活体对拍轮实测 · 12 个探针全中招 ⇒ 收敛到这一个共享件）
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/rag-secrets.sh"
# 共享件：PG 容器 + 建库（**建库失败必须响** ⇒ setup 阶段不许 fail-open）
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/rag-pg.sh"


HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
GO_REPO="${GO_REPO:-/Users/pr/Documents/java&golang/Sea-RideTheWind_golang}"

RUN_DIR="${RUN_DIR:-/tmp/uuboth}"
LOGS="$RUN_DIR/logs"

PG_CONTAINER="${PG_CONTAINER:-uudifftest-postgres}"
REDIS_CONTAINER="${REDIS_CONTAINER:-uudifftest-redis}"
PG_PORT="${PG_PORT:-55434}"
REDIS_PORT="${REDIS_PORT:-56382}"
PG_USER="${PG_USER:-admin}"
PG_PASSWORD="${PG_PASSWORD:-difftest}"

GO_DB="${GO_DB:-uu_both_go_db}"
RAG_DB="${RAG_DB:-uu_both_rag_db}"

GO_USER_RPC_PORT="${GO_USER_RPC_PORT:-19005}"
GO_ADMIN_RPC_PORT="${GO_ADMIN_RPC_PORT:-19015}"
RAG_USER_RPC_PORT="${RAG_USER_RPC_PORT:-59211}"
RAG_ADMIN_RPC_PORT="${RAG_ADMIN_RPC_PORT:-59311}"

BUILD=1
for a in "$@"; do
  case "$a" in
    --no-build) BUILD=0 ;;
    *) echo "未知参数：${a}（可选：--no-build）" >&2; exit 2 ;;
  esac
done

PIDS=()
cleanup() {
  for p in "${PIDS[@]:-}"; do kill "$p" 2>/dev/null || true; done
  # ⚠️ **必须等进程真的退出**（2026-10-24 实测踩到）：
  #    kill 只是发信号，JVM 还要几百毫秒才退。
  #    下一轮 start 时端口还被上一个进程占着 ⇒ `wait_port` 会**误判 OK**（旧进程在听），
  #    而新进程绑端口失败 ⇒ 于是出现「两个进程都『起来了』、但库里 0 张表」的假象。
  for p in "${PIDS[@]:-}"; do wait "$p" 2>/dev/null || true; done
  sleep 1
}
trap cleanup EXIT

mkdir -p "$RUN_DIR/conf/go" "$LOGS"

wait_port() {  # port, name, tries
  local port=$1 name=$2 tries=${3:-120}
  for _ in $(seq 1 "$tries"); do
    if lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
      echo "  OK   ${name} 已监听 :${port}"; return 0
    fi
    sleep 1
  done
  echo "  FAIL ${name} 超时（:${port}）"; return 1
}

wait_port_free() {  # port, name, tries
  local port=$1 name=$2 tries=${3:-30}
  for _ in $(seq 1 "$tries"); do
    if ! lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
      echo "  OK   ${name} 端口 :${port} 已释放"; return 0
    fi
    sleep 1
  done
  echo "  FAIL ${name} 端口 :${port} 仍被占用（上一轮没退干净？）" >&2; return 1
}

ensure_container() {  # name image host_port container_port [docker run 额外参数...]
  local name="$1" image="$2" hport="$3" cport="$4"; shift 4
  if docker ps -a --format '{{.Names}}' | grep -qx "$name"; then
    docker ps --format '{{.Names}}' | grep -qx "$name" \
      || { docker start "$name" >/dev/null; echo "  · 启动已有容器 ${name}"; }
  else
    docker run -d --name "$name" "$@" -p "127.0.0.1:${hport}:${cport}" "$image" >/dev/null
    echo "  · 新建容器 ${name}（${image} → :${hport}）"
  fi
}

psql_q() {  # db, sql
  docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d "$1" -At -c "$2"
}

echo "==> [1/6] 准备自己的容器与两个空库（**不碰共享的 go_db / rag_db**）"
ensure_pg_container "$PG_CONTAINER" "$PG_PORT" "$PG_USER" "$PG_PASSWORD"
ensure_container "$REDIS_CONTAINER" redis:7-alpine "$REDIS_PORT" 6379
wait_pg_ready "$PG_CONTAINER" "$PG_USER" || exit 2
# ⚠️ 必须 WITH (FORCE)：上一轮的服务进程可能还连着这个库，
#    普通 DROP 会报 "database is being accessed by other users" —— 而它被 `|| true` 吞掉，
#    随后 CREATE DATABASE 才失败 ⇒ 报出来的错**指向错误的行**（本轮实测踩到，2026-10-24）。
#    WITH (FORCE) 直接踢掉现有连接（PG 13+）。
# ⚠️ **建库失败必须响**（2026-10-30 活体对拍轮）：原来 CREATE 失败仍打印「✓ 已重建」
#    ⇒ setup 阶段 fail-open ⇒ 后面以「连不上库」的形式炸在别处 ⇒ 误导性结论。
reset_pg_dbs "$PG_CONTAINER" "$PG_USER" "$GO_DB" "$RAG_DB" || exit 2
echo "  ✓ ${GO_DB} / ${RAG_DB} 已重建（**空库**）"

echo "==> [2/6] 生成 Go 运行配置副本（两份：user-rpc / admin-rpc）"
DSN="postgres://${PG_USER}:${PG_PASSWORD}@127.0.0.1:${PG_PORT}/${GO_DB}?sslmode=disable&TimeZone=Asia/Shanghai"
# ⚠️ sed 的**替换部分**里 `&` 表示「整个匹配」⇒ DSN 里的 `&` 必须转义，
#    否则 `?sslmode=disable&TimeZone=...` 会被替换成 `...disable<原行>TimeZone=...`。
DSN_SED="${DSN//&/\\&}"
sed -e "s/0.0.0.0:19004/0.0.0.0:${GO_USER_RPC_PORT}/" \
    -e "s/Port: \"55432\"/Port: \"${PG_PORT}\"/" \
    -e "s/Host: 127.0.0.1:56379/Host: 127.0.0.1:${REDIS_PORT}/" \
    -e "s/Port: 19094/Port: 19105/" \
    -e "s/DBName: \"go_db\"/DBName: \"${GO_DB}\"/" \
    "$HERE/conf/go/user-rpc.yaml" > "$RUN_DIR/conf/go/user-rpc.yaml"

sed -e "s/0.0.0.0:8081/0.0.0.0:${GO_ADMIN_RPC_PORT}/" \
    -e "s#^DataSource:.*#DataSource: \"${DSN_SED}\"#" \
    -e "s#Host: redis:6379#Host: 127.0.0.1:${REDIS_PORT}#" \
    -e "s/Port: 9097/Port: 19115/" \
    -e "s/Mode: file/Mode: console/" \
    -e "/^Etcd:/,/^$/d" \
    "$GO_REPO/service/user/admin/rpc/etc/admin.yaml" > "$RUN_DIR/conf/go/admin-rpc.yaml"

grep -q "DBName: \"${GO_DB}\"" "$RUN_DIR/conf/go/user-rpc.yaml" \
  || { echo "user-rpc 配置副本生成失败" >&2; exit 1; }
grep -qF "DataSource: \"${DSN}\"" "$RUN_DIR/conf/go/admin-rpc.yaml" \
  || { echo "admin-rpc DSN 改写失败（锚点没命中）" >&2; exit 1; }
# ⚠️ Etcd 整块必须去掉：go-zero 的 zrpc/server.go:90 `if c.HasEtcd()` 成立 ⇒ 会去注册，
#    而本机没有 etcd ⇒ `dial tcp: lookup etcd: no such host` ⇒ **panic: context deadline exceeded**，
#    进程直接挂掉（实测 2026-10-24）。user-rpc 的副本用的是同款做法。
grep -q "^Etcd:" "$RUN_DIR/conf/go/admin-rpc.yaml" \
  && { echo "admin-rpc 配置副本里 Etcd 块没去掉（会导致启动 panic）" >&2; exit 1; }

if [[ $BUILD -eq 1 ]]; then
  echo "==> [3/6] 构建（Go 二进制 + 两个 RAG jar）"
  ( cd "$GO_REPO" && CGO_ENABLED=0 go build -o /tmp/difftest/bin/go-admin-rpc ./service/user/admin/rpc )
  ( cd "$ROOT" && mvn -q -o -DskipTests package -pl service/users/admin/admin-rpc -am )
else
  echo "==> [3/6] 跳过构建（--no-build）"
fi

echo "==> [4/6] Go 侧：go-user-rpc 与 go-admin-rpc **并发**对同一个空库启动"
# ⚠️ 端口必须先释放：否则上一轮残留的进程会让 wait_port 误判「已监听」。
for pp in "$GO_USER_RPC_PORT" "$GO_ADMIN_RPC_PORT" "$RAG_USER_RPC_PORT" "$RAG_ADMIN_RPC_PORT"; do
  wait_port_free "$pp" ":${pp}" 30
done
/tmp/difftest/bin/go-user-rpc  -f "$RUN_DIR/conf/go/user-rpc.yaml"  >"$LOGS/go-user-rpc.log"  2>&1 & PIDS+=($!)
/tmp/difftest/bin/go-admin-rpc -f "$RUN_DIR/conf/go/admin-rpc.yaml" >"$LOGS/go-admin-rpc.log" 2>&1 & PIDS+=($!)
wait_port "$GO_USER_RPC_PORT"  "go  user-rpc"  90
wait_port "$GO_ADMIN_RPC_PORT" "go  admin-rpc" 90
sleep 2
echo "  --- Go 侧建出来的表 ---"
psql_q "$GO_DB" "select table_name from information_schema.tables where table_schema='public' order by 1;" | sed 's/^/    /'
GO_TABLES="$(psql_q "$GO_DB" "select count(*) from information_schema.tables where table_schema='public';")"
echo "  Go 侧表数 = ${GO_TABLES}"

echo "==> [5/6] RAG 侧：user-rpc 与 admin-rpc **并发**对同一个空库启动"
USER_JAR="$ROOT/service/users/user/user-rpc/target/user-rpc-0.0.1-SNAPSHOT.jar"
ADMIN_JAR="$ROOT/service/users/admin/admin-rpc/target/admin-rpc-0.0.1-SNAPSHOT.jar"
[[ -f "$USER_JAR"  ]] || { echo "缺少 jar：${USER_JAR}" >&2; exit 1; }
[[ -f "$ADMIN_JAR" ]] || { echo "缺少 jar：${ADMIN_JAR}" >&2; exit 1; }

env POSTGRES_ADDR=127.0.0.1 POSTGRES_PORT="$PG_PORT" \
    POSTGRES_USER="$PG_USER" POSTGRES_PASSWORD="$PG_PASSWORD" POSTGRES_DB="$RAG_DB" \
    REDIS_ADDR=127.0.0.1 REDIS_PORT="$REDIS_PORT" \
    NACOS_ADDR=127.0.0.1 NACOS_PORT=1 \
    GRPC_SERVER_PORT="$RAG_USER_RPC_PORT" \
    java -jar "$USER_JAR"  >"$LOGS/rag-user-rpc.log"  2>&1 & PIDS+=($!)
env POSTGRES_ADDR=127.0.0.1 POSTGRES_PORT="$PG_PORT" \
    POSTGRES_USER="$PG_USER" POSTGRES_PASSWORD="$PG_PASSWORD" POSTGRES_DB="$RAG_DB" \
    REDIS_ADDR=127.0.0.1 REDIS_PORT="$REDIS_PORT" \
    NACOS_ADDR=127.0.0.1 NACOS_PORT=1 \
    GRPC_SERVER_PORT="$RAG_ADMIN_RPC_PORT" \
    java -jar "$ADMIN_JAR" >"$LOGS/rag-admin-rpc.log" 2>&1 & PIDS+=($!)
wait_port "$RAG_USER_RPC_PORT"  "rag user-rpc"  150
wait_port "$RAG_ADMIN_RPC_PORT" "rag admin-rpc" 150
sleep 2
echo "  --- RAG 侧建出来的表 ---"
psql_q "$RAG_DB" "select table_name from information_schema.tables where table_schema='public' order by 1;" | sed 's/^/    /'
RAG_TABLES="$(psql_q "$RAG_DB" "select count(*) from information_schema.tables where table_schema='public';")"
echo "  RAG 侧表数 = ${RAG_TABLES}"

echo "==> [6/6] 判定"
echo "  ── Go 侧的建表竞争痕迹（AutoMigrate / HasTable / duplicate table）──"
grep -i -E "AutoMigrate|already exists|duplicate table|HasTable" "$LOGS"/go-*.log | head -8 | sed 's/^/    /' || true
echo "  ── RAG 侧的 advisory lock 证据 ──"
grep -i -E "advisory|schema init" "$LOGS"/rag-*.log | head -8 | sed 's/^/    /' || true
echo "  ── 两侧启动是否有致命错误 ──"
# ⚠️ 只扫**本脚本自己写的这 4 个**日志：$LOGS 里可能还留着别的脚本的产物
#    （本轮实测：诊断脚本的 d-*.log 混进来，把致命错误计数算成 1）。
for f in go-user-rpc.log go-admin-rpc.log rag-user-rpc.log rag-admin-rpc.log; do
  n=$(grep -c -i -E "FATAL|Application run failed|panic:" "$LOGS/$f" 2>/dev/null || echo 0)
  echo "    ${f}: 致命错误 ${n} 处"
done
echo
echo "  结论对照："
echo "    Go  侧表数 = ${GO_TABLES}，RAG 侧表数 = ${RAG_TABLES}"
echo "    （表名本来就不同：Go users/admins/admin_invites/user_avatar_history"
echo "      vs RAG users_user/admin_admin/admin_invite/users_user_avatar_history）"
echo "    ⇒ 关键判定是**能不能起来**与**共享表有没有建出来**，不是表名。"
