#!/usr/bin/env bash
# Go 侧并发建表现状取证（只读诊断脚本，不改任何东西）
#
# 问题：user-rpc 与 admin-rpc 同时对着**空库**启动，Go 的 GORM AutoMigrate
#       会不会像 RAG 那样撞 PG 的 DDL 竞态？
#
# 背景：RAG 去掉 Flyway 后，difftest 实测到两个进程并发建 users_user 会报
#       duplicate key value violates unique constraint "pg_type_typname_nsp_index"
#       （已用 advisory lock 修掉）。Go 用的是 GORM AutoMigrate，无账本、
#       不需要 advisory lock，所以「Go 为什么没问题」这个说法需要验证。
#
# GORM 的实际路径（gorm.io/gorm@v1.31.1 migrator/migrator.go）：
#     AutoMigrate → if !HasTable(value) { createTable(value) }
#     createTable  发的是 `CREATE TABLE ? (...)`，建表本身**没有 IF NOT EXISTS**
#     （只有 createIndex 是 `IF NOT EXISTS ? ON ?`）
#   ⇒ 典型的 check-then-act，并发下两个进程都可能看到「表不存在」。
#
# 本脚本的产出：Go 侧的实测报错形态（如果有）。
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN_DIR=/tmp/gorace
PG_PORT=55434

PG_USER=admin
PG_PASS=difftest
PG_DB=race_db

cleanup() {
  kill ${P1:-0} ${P2:-0} 2>/dev/null
  docker rm -f gorace-pg >/dev/null 2>&1
}
trap cleanup EXIT

mkdir -p "$RUN_DIR/conf"
# difftest 的 conf 副本里端口/库名是**硬编码**的（user-rpc.yaml 的 Postgres.* 不吃环境变量，
# admin-rpc.yaml 的 DSN 才吃），所以这里另生成一份指向本实验端口的副本。
for f in user-rpc admin-rpc; do
  sed -e "s/55432/${PG_PORT}/" \
      -e "s/go_db/${PG_DB}/g" \
      -e "s/^DataSource:.*/DataSource: \"postgres:\/\/${PG_USER}:${PG_PASS}@127.0.0.1:${PG_PORT}\/${PG_DB}?sslmode=disable\"/" \
      "$HERE/conf/go/$f.yaml" > "$RUN_DIR/conf/$f.yaml"
done

docker rm -f gorace-pg >/dev/null 2>&1
docker run -d --name gorace-pg \
  -e POSTGRES_USER=$PG_USER -e POSTGRES_PASSWORD=$PG_PASS -e POSTGRES_DB=$PG_DB \
  -p "127.0.0.1:${PG_PORT}:5432" postgres:16-alpine >/dev/null
until docker exec gorace-pg pg_isready -U "$PG_USER" >/dev/null 2>&1; do sleep 1; done
echo "== 空库就绪 =="

echo
echo "########## user-rpc 与 admin-rpc **同时**启动（不给任何错开）"
POSTGRES_ADDR=127.0.0.1 POSTGRES_PORT=$PG_PORT POSTGRES_USER=$PG_USER POSTGRES_PASSWORD=$PG_PASS \
  POSTGRES_DB=$PG_DB \
  "$RUN_DIR/go-user-rpc"  -f "$RUN_DIR/conf/user-rpc.yaml"  >"$RUN_DIR/user-rpc.log" 2>&1 &
P1=$!
POSTGRES_ADDR=127.0.0.1 POSTGRES_PORT=$PG_PORT POSTGRES_USER=$PG_USER POSTGRES_PASSWORD=$PG_PASS \
  POSTGRES_DB=$PG_DB \
  "$RUN_DIR/go-admin-rpc" -f "$RUN_DIR/conf/admin-rpc.yaml" >"$RUN_DIR/admin-rpc.log" 2>&1 &
P2=$!
sleep 12

echo
echo "—— 判定 ——"
for f in user-rpc admin-rpc; do
  if grep -q "数据表迁移失败" "$RUN_DIR/$f.log"; then
    echo "  ✗ $f **建表失败**"
  elif grep -q "数据库连接失败" "$RUN_DIR/$f.log"; then
    echo "  ? $f 连库失败（配置问题，另查）"
  else
    echo "  ✓ $f 未报建表失败"
  fi
done
echo
echo "  实际建出来的表："
docker exec gorace-pg psql -U "$PG_USER" -d "$PG_DB" -tAc \
  "select string_agg(tablename,',' order by tablename) from pg_tables where schemaname='public';" 2>/dev/null | sed 's/^/    /'
echo
echo "  两个进程的完整输出："
for f in user-rpc admin-rpc; do
  echo "  --- $f ---"
  grep -vE "^\s*$" "$RUN_DIR/$f.log" | head -8 | sed 's/^/    /'
done