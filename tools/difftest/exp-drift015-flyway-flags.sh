#!/usr/bin/env bash
# exp-drift015-flyway-flags.sh —— **被证伪方案**的实证记录，不要当修复用。
#
#   被证伪的方案：给 admin-rpc 加
#       --spring.flyway.validate-on-migrate=false
#       --spring.flyway.out-of-order=true
#   让它「容忍」user-rpc 写下的账本。
#
#   这是最省事、看起来最该先试的路线。本脚本用真 jar + 真 PostgreSQL 16
#   证明它**行不通**，把推理换成证据（AGENTS.md §3.3）。
#
# 实测结果（2026-10-21）：
#   1) user-rpc 先起，账本写下 V1=create user、V2=create user avatar history
#   2) admin-rpc 带上面两个开关启动，Flyway 报
#        Current version of schema "public": 2
#        Migrating schema "public" to version "3 - create user"
#      ⇒ **它自己的 V1/V2 被静默跳过**（版本号 1、2 已被 user-rpc 占用），
#        只应用了 V3
#   3) 终态：
#        账本 = [1|create user, 2|create user avatar history, 3|create user]
#        admin_admin  缺失   ← V1__create_admin.sql 根本没跑
#        admin_invite 缺失   ← V2__create_admin_invite.sql 根本没跑
#        admin-rpc 启动失败：relation "admin_admin" does not exist
#        （AdminSeederBootstrap 是 @PostConstruct，起来就查这张表）
#
#   换句话说：这两个开关不但修不好问题，还往账本里塞了一条
#   **语义错误的记录**（version 3 = "create user"，与 version 1 做的事重复），
#   把 schema 永久钉在错误版本上 —— 后续再想正修都得先人工清账本。
#
# 真正的修法见 AGENTS.md §6.2.5 DRIFT-NEW-015（需用户拍板，本仓库不自行决定）。
#
# 用法：./exp-drift015-flyway-flags.sh    （会自建 55433 端口的一次性 PG 容器，结束即清理）

# 要回答的唯一问题：
#   「给 admin-rpc 加 --spring.flyway.validate-on-migrate=false
#     --spring.flyway.out-of-order=true，让它容忍 user-rpc 的账本，行不行？」
#
# 这是最省事、看起来最该先试的方案。本脚本用真 jar + 真 PG 实证它行不行，
# 而不是靠推理下结论（AGENTS.md §3.3 的纪律）。
set -uo pipefail
# 🔴 RAG 侧 **fail-closed 密钥**（P0 #8）：占位符解析不出来 ⇒ 进程起不来
#    ⇒ 探针「起 RAG 侧」这一步必挂 ⇒ 看起来像产品缺陷，其实是探针自己没给密钥。
#    （2026-10-30 活体对拍轮实测 · 12 个探针全中招 ⇒ 收敛到这一个共享件）
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/rag-secrets.sh"


HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
RUN_DIR=/tmp/drift015
PG_PORT=55433
PG_USER=admin
PG_PASS=difftest
PG_DB=rdb

cd "$ROOT/service/users"
mvn -q -o -DskipTests package >/dev/null 2>&1
for m in user/user-rpc:user-rpc admin/admin-rpc:admin-rpc; do
  cp "$ROOT/service/users/${m%%:*}/target/${m##*:}-0.0.1-SNAPSHOT.jar" "$RUN_DIR/${m##*:}" 2>/dev/null \
    || { mkdir -p "$RUN_DIR"; cp "$ROOT/service/users/${m%%:*}/target/${m##*:}-0.0.1-SNAPSHOT.jar" "$RUN_DIR/${m##*:}"; }
done

cleanup() {
  kill ${P1:-0} ${P2:-0} 2>/dev/null
  docker rm -f drift015-pg >/dev/null 2>&1
}
trap cleanup EXIT

docker rm -f drift015-pg >/dev/null 2>&1
docker run -d --name drift015-pg -e POSTGRES_USER=$PG_USER -e POSTGRES_PASSWORD=$PG_PASS -e POSTGRES_DB=$PG_DB \
  -p "127.0.0.1:${PG_PORT}:5432" postgres:16-alpine >/dev/null
until docker exec drift015-pg pg_isready -U "$PG_USER" >/dev/null 2>&1; do sleep 1; done
echo "== PG 就绪 =="

tables() {
  docker exec drift015-pg psql -U "$PG_USER" -d "$PG_DB" -tAc \
    "select string_agg(tablename,',' order by tablename) from pg_tables where schemaname='public';" 2>/dev/null
}

echo
echo "########## 第 1 步：user-rpc 先起（Flyway 正常，写下 V1=create_user / V2=create_user_avatar_history）"
POSTGRES_ADDR=127.0.0.1 POSTGRES_PORT=$PG_PORT POSTGRES_USER=$PG_USER POSTGRES_PASSWORD=$PG_PASS \
  POSTGRES_DB=$PG_DB GRPC_SERVER_PORT=59311 NACOS_ADDR=127.0.0.1 NACOS_PORT=1 \
  java -jar "$RUN_DIR/user-rpc" >"$RUN_DIR/user-rpc.log" 2>&1 &
P1=$!
for _ in $(seq 1 60); do
  [[ "$(docker exec drift015-pg psql -U "$PG_USER" -d "$PG_DB" -tAc "select count(*) from information_schema.tables where table_schema='public' and table_name='users_user_avatar_history';" 2>/dev/null | tr -d '[:space:]')" == "1" ]] && break
  sleep 1
done
echo "  表：$(tables)"
echo "  账本："
docker exec drift015-pg psql -U "$PG_USER" -d "$PG_DB" -tAc \
  "select version||' | '||description from flyway_schema_history order by installed_rank;" 2>/dev/null | sed 's/^/    /'

echo
echo "########## 第 2 步：admin-rpc 起，带上「最省事方案」的两个开关"
POSTGRES_ADDR=127.0.0.1 POSTGRES_PORT=$PG_PORT POSTGRES_USER=$PG_USER POSTGRES_PASSWORD=$PG_PASS \
  POSTGRES_DB=$PG_DB GRPC_SERVER_PORT=58311 NACOS_ADDR=127.0.0.1 NACOS_PORT=1 \
  java -jar "$RUN_DIR/admin-rpc" \
    --spring.flyway.validate-on-migrate=false \
    --spring.flyway.out-of-order=true \
  >"$RUN_DIR/admin-rpc.log" 2>&1 &
P2=$!
sleep 25

echo "  admin-rpc 是否起来：$(grep -c 'Started AdminRpcApplication' "$RUN_DIR/admin-rpc.log") （1=起来）"
echo "  表：$(tables)"
echo "  账本："
docker exec drift015-pg psql -U "$PG_USER" -d "$PG_DB" -tAc \
  "select version||' | '||description||' | success='||success from flyway_schema_history order by installed_rank;" 2>/dev/null | sed 's/^/    /'
echo
echo "  —— 判定 ——"
for t in admin_admin admin_invite users_user users_user_avatar_history; do
  n=$(docker exec drift015-pg psql -U "$PG_USER" -d "$PG_DB" -tAc \
    "select count(*) from information_schema.tables where table_schema='public' and table_name='$t';" 2>/dev/null | tr -d '[:space:]')
  [[ "$n" == "1" ]] && echo "  ✓ $t 存在" || echo "  ✗ $t **缺失**"
done
echo
echo "  admin-rpc 启动日志关键行："
grep -nE "Started AdminRpcApplication|Application run failed|does not exist|Migrating schema|Current version|newer than|checksum" \
  "$RUN_DIR/admin-rpc.log" | head -12 | sed 's/^/    /'