#!/usr/bin/env bash
# ════════════════════════════════════════════════════════════════════════════
#  共享件：探针自建 PostgreSQL 容器（消除「隐式依赖别的探针的残留容器」）
# ════════════════════════════════════════════════════════════════════════════
#
# 【为什么需要这个共享件】（2026-10-30 活体对拍轮实测）
#   7 个 exp-* 探针都把 PG_CONTAINER 默认成 `uudifftest-postgres`（:55434），
#   但 **只有 exp-difftest-grpc-user9.sh 会自建它**
#   ⇒ 其余 6 个 **隐式依赖「上一个探针跑完留下的容器」**
#   ⇒ 批跑器做「逐探针前置清理」时把容器删了
#   ⇒ 后续探针 **在 setup 阶段就失败**（`docker exec` 报 No such container）
#   ⇒ 假红（看起来像「RAG 起不来」这种产品缺陷，其实是**编排缺陷**）。
#   ⇒ 收敛成共享件：**每个探针自己保证 PG 容器存在** ⇒ 不再依赖执行顺序。
#
# 【⚠️ 失败必须响 —— setup 阶段不许 fail-open】
#   原实现（各探针内联）：
#       docker exec "$PG_CONTAINER" psql ... -c "DROP DATABASE IF EXISTS $db" || true
#       docker exec "$PG_CONTAINER" psql ... -c "CREATE DATABASE $db"          # 失败被吞
#       echo "  ✓ ${GO_DB} / ${RAG_DB} 已重建"                                  # 无条件报成功
#   ⇒ 容器不存在时 **CREATE 失败但仍打印「✓ 已重建」**
#   ⇒ 后面以「连不上库」的形式炸在**别处** ⇒ 报告出来的是误导性结论。
#   ⇒ 本共享件里 `reset_pg_dbs` 失败 ⇒ **非零退出**（且不打印「✓ 已重建」）。
#
# 【用法】
#   source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib/rag-pg.sh"
#   ensure_pg_container "$PG_CONTAINER" "$PG_PORT" "$PG_USER" "$PG_PASSWORD"
#   reset_pg_dbs "$PG_CONTAINER" "$PG_USER" "$GO_DB" "$RAG_DB"
#
# 依赖：Docker（与其余 exp-* 探针一致）。
# ⚠️ 端口由调用方决定（各探针端口段互斥）⇒ 本件**不自带端口**，避免两处写死。

# ── ensure_pg_container ────────────────────────────────────────────────────
#   $1=容器名  $2=宿主端口  $3=PG 用户  $4=PG 密码
#   容器不存在 ⇒ 新建；存在但没跑 ⇒ start；已在跑 ⇒ 原样复用。
ensure_pg_container() {
  local name="$1" hport="$2" puser="$3" ppass="$4"
  if docker ps -a --format '{{.Names}}' | grep -qx "${name}"; then
    if docker ps --format '{{.Names}}' | grep -qx "${name}"; then
      echo "  · 容器 ${name} 已在运行"
    else
      docker start "${name}" >/dev/null
      echo "  · 启动已有容器 ${name}"
    fi
  else
    docker run -d --name "${name}" \
      -e POSTGRES_USER="${puser}" -e POSTGRES_PASSWORD="${ppass}" -e POSTGRES_DB=postgres \
      -p "127.0.0.1:${hport}:5432" postgres:16-alpine >/dev/null
    echo "  · 新建容器 ${name}（postgres:16-alpine → :${hport}）"
  fi
}

# ── wait_pg ───────────────────────────────────────────────────────────────
#   $1=容器名  $2=PG 用户
#   等 PG **真的能连**（容器 running ≠ psql 可用）。
#   ⚠️ 名字带 `_ready` 后缀：避免与探针里原有的 `wait_pg` **同名覆盖**。
wait_pg_ready() {
  local name="$1" puser="$2"
  for _ in $(seq 1 60); do
    if docker exec "${name}" psql -U "${puser}" -d postgres -c 'select 1' >/dev/null 2>&1; then
      echo "  OK   ${name} 已可连"; return 0
    fi
    sleep 1
  done
  echo "  FAIL ${name} 超时不可连" >&2
  return 1
}

# ── reset_pg_dbs ──────────────────────────────────────────────────────────
#   $1=容器名  $2=PG 用户  $3...=要重建的库名
#   ⚠️ **任一 CREATE 失败 ⇒ 非零返回且不打印「✓ 已重建」**（见文件头说明）。
reset_pg_dbs() {
  local name="$1" puser="$2"; shift 2
  local db
  for db in "$@"; do
    docker exec "${name}" psql -U "${puser}" -d postgres \
      -c "DROP DATABASE IF EXISTS ${db} WITH (FORCE)" >/dev/null 2>&1 || true
    if ! docker exec "${name}" psql -U "${puser}" -d postgres \
         -c "CREATE DATABASE ${db}" >/dev/null 2>&1; then
      echo "  FAIL 建库 ${db} 失败（容器 ${name} 是否在运行？）" >&2
      return 1
    fi
  done
  echo "  ✓ $* 已重建"
  return 0
}
