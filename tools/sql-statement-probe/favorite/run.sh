#!/usr/bin/env bash
# ============================================================================
# SQL 语句层 · **双侧活体对拍**探针 · favorite 域
# ----------------------------------------------------------------------------
# 目的：把台账 §29 的最后一层补上 ——
#      「两侧为了让同一个业务结果，各自对数据库说了什么话」。
#
#   已闭的三层（本探针**不重复**）：
#     · DDL 层          → tools/ddl-parity-probe/favorite/
#     · 错误码契约层    → tools/errmsg-probe/
#     · 事务语义层      → FavoriteFactTransactionTest + 第二轮落库等价性
#   本探针补的第四层：**语句层**（锁 / 事务边界 / WHERE / 写集合 / 投影）。
#
# 为什么必须做（不是「已经有落库等价性了」）：
#   落库等价性只证伪「**结果**一样」，证不了「**过程**一样」。
#   少一句 FOR UPDATE、多一次全表扫、事务边界切得不一样 ——
#   这些在单次运行里可能看不出差别，却会在**并发**下变成死锁或脏写。
#
# 取证方式（**零侵入**，这是选它的唯一理由）：
#   · Go 侧是**冻结**的真源 ⇒ 不能改它的 logger 配置；
#   · RAG 侧改 MyBatis 日志级别会**改变被测面**。
#   ⇒ 只有 **PG 服务端日志**能让两侧都「一行不改」地暴露真实 SQL，
#     而且顺带能看到 BEGIN / COMMIT / ROLLBACK（事务边界）与真语句文本。
#
# 两侧怎么分流：
#   · Go 侧 → **PGAPPNAME=go_side 环境变量**
#     （真源 DSN 硬编码在 internal/model/db.go:21-28，**没有** application_name 字段；
#      pgx v5 支持 PGAPPNAME → application_name：pgconn/config.go:456）
#   · RAG 侧 → JDBC URL 挂 ApplicationName=rag_side
#   · 驱动自己 → 独立连接 ApplicationName=probe_marker
#
# 怎么按用例切段：
#   每个用例调用前，驱动发一条 SELECT 'SQLMARK::<side>::<case-id>::<seq>'
#   ⇒ 服务端日志里出现可识别标记 ⇒ 解析时按 marker 切段 ⇒ **逐用例**比对。
#
# 用法：
#   bash tools/sql-statement-probe/favorite/run.sh
#   GO_REPO=/path/to/Sea-RideTheWind_golang bash tools/sql-statement-probe/favorite/run.sh
#
# 退出码：0 = 两侧语句层语义一致（差异均在已登记的 ORM 噪声档内）；
#         1 = 有**语义**差异；2 = 环境问题 / 真源漂移。
# ============================================================================
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAG_ROOT="$(cd "${HERE}/../../.." && pwd)"

say() { printf '\033[1;36m[sql-statement-probe]\033[0m %s\n' "$*"; }
ok()  { printf '\033[1;32m  ✅ %s\033[0m\n' "$*"; }
bad() { printf '\033[1;31m  ❌ %s\033[0m\n' "$*"; }
die() { bad "$*"; exit 2; }

# ── 0. 定位 Go 真源 ────────────────────────────────────────────────────────
default_go_repo() {
  local d
  d="$(cd "${RAG_ROOT}" && pwd)"
  for _ in 1 2 3 4 5 6; do
    if [ -f "${d}/Sea-RideTheWind_golang/service/favorite/rpc/internal/model/db.go" ]; then
      (cd "${d}/Sea-RideTheWind_golang" && pwd); return 0
    fi
    d="$(dirname "${d}")"
  done
  return 1
}
GO_REPO="${GO_REPO:-$(default_go_repo || true)}"
[ -n "${GO_REPO}" ] || die "找不到 Go 真源（向上 6 层内没有 Sea-RideTheWind_golang）—— 用 GO_REPO=... 指定"
[ -f "${GO_REPO}/go.mod" ] || die "Go 真源没有 go.mod：${GO_REPO}/go.mod"
say "Go 真源：${GO_REPO}"

command -v go >/dev/null 2>&1 || die "没装 go toolchain"
command -v mvn >/dev/null 2>&1 || die "没装 mvn"
command -v docker >/dev/null 2>&1 || die "没装 docker（探针需要真 PG）"

# ── 0b. 复用件：真源逐字节复制件 + 探针专属 cmd ───────────────────────────
# ⚠️ 本探针**不复制**一份真源文件，而是复用同域 grpc-contract-probe 的那份
#    **已逐字节校验**的复制件（含探针专属的 schemaprep / upstreamstub 两个 cmd）。
#    理由：复制件有 sha256 校验 ⇒ 漂移会被当场抓住；再复制一份只会多一处漂移源。
#    ⚠️ 本探针**不新增**任何 Go 代码 ⇒ 复制件清单与那个探针**完全一致**。
GO_COPY_SRC="${RAG_ROOT}/tools/grpc-contract-probe/favorite/go"
[ -d "${GO_COPY_SRC}" ] || die "找不到复用件目录：${GO_COPY_SRC}（同域 grpc-contract-probe 的 go/ 目录）"
COPY_LIST="${GO_COPY_SRC}/filelist.txt"
[ -f "${COPY_LIST}" ] || die "缺复制件清单：${COPY_LIST}"

TMP="$(mktemp -d)"
PG_CONTAINER="fav-sqlstmt-probe-pg-$$"
GO_PID=""
STUB_PID=""
cleanup() {
  [ -n "${GO_PID}" ] && kill "${GO_PID}" >/dev/null 2>&1
  [ -n "${STUB_PID}" ] && kill "${STUB_PID}" >/dev/null 2>&1
  docker rm -f "${PG_CONTAINER}" >/dev/null 2>&1 || true
  # KEEP_TMP=1 ⇒ 保留临时目录（排查用：pg.log / go-server.log / mvn.log）
  if [ "${KEEP_TMP:-0}" = "1" ]; then
    say "KEEP_TMP=1 ⇒ 临时目录保留在 ${TMP}"
  else
    rm -rf "${TMP}"
  fi
}
trap cleanup EXIT

# ── 1. 校验真源复制件的 sha256（防真源漂移后探针还按老副本跑）──────────────
say "校验真源复制件 sha256（复用 grpc-contract-probe 的复制件）"
MISMATCH=0
COPY_COUNT=0
while IFS= read -r rel; do
  [ -z "${rel}" ] && continue
  case "${rel}" in \#*) continue ;; esac
  src="${GO_REPO}/${rel}"
  dst="${GO_COPY_SRC}/${rel}"
  [ -f "${src}" ] || die "真源缺文件：${rel}"
  [ -f "${dst}" ] || die "复制件缺文件：${rel}"
  a="$(shasum -a 256 "${src}" | awk '{print $1}')"
  b="$(shasum -a 256 "${dst}" | awk '{print $1}')"
  if [ "${a}" != "${b}" ]; then
    bad "复制件与真源不一致：${rel}"
    MISMATCH=1
  fi
  COPY_COUNT=$((COPY_COUNT + 1))
done < "${COPY_LIST}"
[ "${MISMATCH}" -eq 0 ] || die "复制件与真源不一致 ⇒ 请重新复制（不要手改复制件）"
ok "${COPY_COUNT} 个复制件与真源逐字节一致"

# ── 2. 组装临时 module（module 名沿用真源的 sea-try-go ⇒ import 路径零改动）──
say "组装临时 module（module=sea-try-go，go.mod/go.sum 整体继承真源）"
mkdir -p "${TMP}/src"
cp -R "${GO_COPY_SRC}/service" "${TMP}/src/"
cp "${GO_REPO}/go.mod" "${GO_REPO}/go.sum" "${TMP}/src/"

# ── 3. 起真 PG（**开语句日志**）───────────────────────────────────────────
# 🔴 log_statement=all 是本探针的**取证开关**：
#    · 'all'  ⇒ 简单协议记 "statement: …"，扩展协议记 "execute <name>: …"
#    · log_line_prefix 里带 db=%d app=%a ⇒ 两侧靠「库 + application_name」分流
#      （db 用来兜底：万一某个连接的 application_name 没生效，还能靠库区分）
#    · log_min_duration_statement=-1（默认）⇒ **不要** duration 前缀，
#      否则每行都变成 "duration: 1.234 ms  statement: …"，解析要多剥一层。
# ⚠️ 只开这一个容器 ⇒ 代价可忽略；且每次都是全新容器 ⇒ 不会有历史噪声。
say "起 postgres:16-alpine 容器（开 log_statement=all）：${PG_CONTAINER}"
wait_pg_tcp() {
  local tries="$1" i
  for i in $(seq 1 "${tries}"); do
    docker exec "${PG_CONTAINER}" pg_isready -U postgres -h 127.0.0.1 >/dev/null 2>&1 && return 0
    [ "$(docker inspect -f '{{.State.Running}}' "${PG_CONTAINER}" 2>/dev/null || echo false)" = "true" ] || return 1
    sleep 1
  done
  return 1
}
PG_PORT=""
PG_READY=0
for attempt in 1 2; do
  docker rm -f "${PG_CONTAINER}" >/dev/null 2>&1 || true
  if ! docker run -d --rm --name "${PG_CONTAINER}" \
      -e POSTGRES_PASSWORD=probe -e POSTGRES_USER=postgres \
      -p 0:5432 postgres:16-alpine \
      -c log_statement=all \
      -c "log_line_prefix=%m [%p] db=%d app=%a " \
      -c log_min_duration_statement=-1 \
      >/dev/null; then
    say "   PG 容器第 ${attempt} 次启动失败 ⇒ 重来"
    continue
  fi
  PG_PORT="$(docker port "${PG_CONTAINER}" 5432/tcp | head -1 | sed 's/.*://')"
  if [ -z "${PG_PORT}" ]; then
    say "   第 ${attempt} 次拿不到 PG 映射端口 ⇒ 重来"
    continue
  fi
  if wait_pg_tcp 180; then PG_READY=1; break; fi
  say "   PG 第 ${attempt} 次 180s 内没就绪 ⇒ 重建容器重来"
done
[ "${PG_READY}" = "1" ] || die "PG 没就绪（已重建容器重试 1 次）"
for db in godb first_db; do
  ok_db=0
  for _ in $(seq 1 15); do
    if docker exec "${PG_CONTAINER}" psql -U postgres -h 127.0.0.1 -c "CREATE DATABASE ${db};" >/dev/null 2>&1; then
      ok_db=1; break
    fi
    sleep 2
  done
  [ "${ok_db}" = "1" ] || die "建库 ${db} 失败"
done
ok "PG 就绪（127.0.0.1:${PG_PORT}）· 两个库：godb（Go 侧）/ first_db（RAG 侧）· 语句日志已开"

# ── 4. Go 侧：sql-first 建表（真源文档指定的部署顺序）──────────────────────
say "Go 侧建表（sql-first：AutoMigrate(老表) → 真源 001..004 → 应用启动时 InitDB）"
( cd "${TMP}/src" && CGO_ENABLED=0 GOPROXY=off GOFLAGS=-mod=mod GOTOOLCHAIN=local \
    PG_HOST=127.0.0.1 PG_PORT="${PG_PORT}" PG_USER=postgres PG_PASSWORD=probe \
    PG_DB=godb PG_SSLMODE=disable \
    SQL_DIR="${TMP}/src/service/favorite/rpc/internal/model" \
    go run ./service/favorite/rpc/cmd/schemaprep ) > "${TMP}/schemaprep.log" 2>&1 \
  || { bad "Go 侧建表失败"; tail -25 "${TMP}/schemaprep.log"; exit 2; }
ok "Go 侧 schema 就绪"

# ── 5. 端口分配（专用端口段 + 双绑定自检）────────────────────────────────
# ⚠️ 端口纪律（2026-10-25 实测踩出来的坑）：
#   OS 随机端口在临时端口段 49152–65535 ⇒ 正是 Docker 随机发布容器端口的段；
#   macOS 上 `127.0.0.1:P` 会**抢占**通配地址 `0.0.0.0:P` ⇒ 客户端连 127.0.0.1:P
#   会打到**别的容器**。⇒ 专用端口段 21400–21599，且两个地址都要能绑。
pick_free_port() {
  python3 - "$@" <<'PY'
import socket, sys
FIRST, LAST = 21400, 21599
used = {int(x) for x in sys.argv[1:] if x.strip()}
for p in range(FIRST, LAST + 1):
    if p in used:
        continue
    socks = []
    try:
        for addr in (("0.0.0.0", p), ("127.0.0.1", p)):
            s = socket.socket()
            s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            s.bind(addr)
            s.listen(1)
            socks.append(s)
        print(p)
        break
    except OSError:
        for s in socks:
            s.close()
else:
    sys.exit("端口段 %d-%d 已占满（并行会话太多？）" % (FIRST, LAST))
PY
}
GO_PORT="$(pick_free_port)"
STUB_PORT="$(pick_free_port "${GO_PORT}")"
RAG_PORT="$(pick_free_port "${GO_PORT}" "${STUB_PORT}")"
UNIQUE_PORTS="$(printf '%s\n' "${GO_PORT}" "${STUB_PORT}" "${RAG_PORT}" | sort -u | wc -l | tr -d ' ')"
[ "${UNIQUE_PORTS}" = "3" ] || die "3 个端口里有撞号（唯一值只有 ${UNIQUE_PORTS} 个）⇒ 端口分配被污染"
say "Go server 端口 ${GO_PORT} / 上游桩端口 ${STUB_PORT} / RAG server 端口 ${RAG_PORT}"

# ── 6. 上游桩（user-rpc + article-rpc 的最小实现，两侧共同上游）────────────
say "编译并启动上游桩"
( cd "${TMP}/src" && CGO_ENABLED=0 GOPROXY=off GOFLAGS=-mod=mod GOTOOLCHAIN=local \
    go build -o "${TMP}/upstreamstub" ./service/favorite/rpc/cmd/upstreamstub ) > "${TMP}/stubbuild.log" 2>&1 \
  || { bad "上游桩编译失败"; tail -30 "${TMP}/stubbuild.log"; exit 2; }
"${TMP}/upstreamstub" -addr "127.0.0.1:${STUB_PORT}" > "${TMP}/stub.log" 2>&1 &
STUB_PID=$!
UP=0
for _ in $(seq 1 30); do
  if python3 -c "import socket,sys;s=socket.socket();s.settimeout(0.5);sys.exit(0 if s.connect_ex(('127.0.0.1',${STUB_PORT}))==0 else 1)"; then
    UP=1; break
  fi
  kill -0 "${STUB_PID}" 2>/dev/null || break
  sleep 1
done
[ "${UP}" = "1" ] || { bad "上游桩没起来"; cat "${TMP}/stub.log"; exit 2; }
ok "上游桩已在 127.0.0.1:${STUB_PORT} 监听"

# ── 7. 起 Go 真源 favorite-rpc server（**带 PGAPPNAME=go_side**）──────────
cat > "${TMP}/probe.yaml" <<YAML
Name: favorite.rpc
ListenOn: 127.0.0.1:${GO_PORT}

Log:
  ServiceName: favorite-rpc
  Mode: console
  Encoding: plain

Postgres:
  Host: "127.0.0.1"
  Port: "${PG_PORT}"
  User: "postgres"
  Password: "probe"
  DBName: "godb"
  Mode: "disable"

# Endpoints 优先于 Etcd（zrpc/config.go:92-114 BuildTarget）⇒ 完全不需要 etcd
UserRpc:
  Endpoints:
    - "127.0.0.1:${STUB_PORT}"

ArticleRpc:
  Endpoints:
    - "127.0.0.1:${STUB_PORT}"
YAML

say "编译并启动 Go 真源 favorite-rpc server（PGAPPNAME=go_side）"
( cd "${TMP}/src" && CGO_ENABLED=0 GOPROXY=off GOFLAGS=-mod=mod GOTOOLCHAIN=local \
    go build -o "${TMP}/favorite-rpc" ./service/favorite/rpc ) > "${TMP}/gobuild.log" 2>&1 \
  || { bad "Go 侧编译失败"; tail -30 "${TMP}/gobuild.log"; exit 2; }
# 🔴🔴 **PGAPPNAME 只加在这一条命令上** —— 不能 export 到整个脚本：
#    否则 psql 灌种子、schemaprep 建表也会被标成 go_side ⇒ 污染 Go 桶。
#    （真源 DSN 硬编码在 internal/model/db.go:21-28，没有 application_name 字段 ⇒ 只能走环境变量；
#      pgx v5 把 PGAPPNAME 映射成 application_name：pgconn/config.go:456）
PGAPPNAME=go_side "${TMP}/favorite-rpc" -f "${TMP}/probe.yaml" > "${TMP}/go-server.log" 2>&1 &
GO_PID=$!
UP=0
for _ in $(seq 1 120); do
  if python3 -c "import socket,sys;s=socket.socket();s.settimeout(0.5);sys.exit(0 if s.connect_ex(('127.0.0.1',${GO_PORT}))==0 else 1)"; then
    UP=1; break
  fi
  kill -0 "${GO_PID}" 2>/dev/null || break
  sleep 1
done
[ "${UP}" = "1" ] || { bad "Go server 没起来"; tail -30 "${TMP}/go-server.log"; exit 2; }
ok "Go 真源 favorite-rpc server 已在 127.0.0.1:${GO_PORT} 监听（application_name=go_side）"

# ── 8. 灌种子（Go 侧；psql 的 application_name 不是 go_side ⇒ 不进 Go 桶）──
say "灌种子到 Go 侧库（godb）"
docker exec -i "${PG_CONTAINER}" psql -U postgres -h 127.0.0.1 -d godb -v ON_ERROR_STOP=1 \
  -f - < "${RAG_ROOT}/tools/grpc-contract-probe/favorite/seed.sql" > "${TMP}/seed-go.log" 2>&1 \
  || { bad "Go 侧灌种子失败"; cat "${TMP}/seed-go.log"; exit 2; }
ok "Go 侧种子就绪"

# ── 9. 跑 Java 侧（RAG 真 Spring 上下文 + 同一客户端双打 + marker 分段）──
say "跑 Java 侧（真 Spring 上下文 + 真 Flyway + 真 MyBatis + marker 分段）"
PROBE_GO_TARGET="127.0.0.1:${GO_PORT}" \
PROBE_RAG_TARGET="127.0.0.1:${RAG_PORT}" \
PROBE_RAG_PORT="${RAG_PORT}" \
PROBE_USER_RPC_ADDR="static://127.0.0.1:${STUB_PORT}" \
PROBE_ARTICLE_RPC_ADDR="static://127.0.0.1:${STUB_PORT}" \
PROBE_JDBC_URL="jdbc:postgresql://127.0.0.1:${PG_PORT}/first_db?TimeZone=Asia/Shanghai&ApplicationName=rag_side" \
PROBE_JDBC_URL_MARKER="jdbc:postgresql://127.0.0.1:${PG_PORT}/first_db?TimeZone=Asia/Shanghai&ApplicationName=probe_marker" \
PROBE_JDBC_USER="postgres" \
PROBE_JDBC_PASSWORD="probe" \
PROBE_SEED="${RAG_ROOT}/tools/grpc-contract-probe/favorite/seed.sql" \
PROBE_OUT_CASES="${TMP}/cases.tsv" \
  mvn -o -q -pl service/favorite/favorite-rpc -am test \
    -Dtest=FavoriteSqlStatementParityTest -DfailIfNoTests=false \
    -Dsurefire.failIfNoSpecifiedTests=false \
  > "${TMP}/mvn.log" 2>&1
MVN_RC=$?
if [ "${MVN_RC}" -ne 0 ]; then
  bad "Java 侧 FavoriteSqlStatementParityTest 失败（exit ${MVN_RC}）"
  grep -E 'ERROR|FAIL|expected|AssertionError' "${TMP}/mvn.log" | head -40
  echo "---- mvn.log 尾部 ----"
  tail -60 "${TMP}/mvn.log"
  exit 2
fi
[ -s "${TMP}/cases.tsv" ] || die "Java 侧没有产出用例结果文件（cases.tsv）"
ok "用例跑完：$(wc -l < "${TMP}/cases.tsv" | tr -d ' ') 条（两侧业务出口已断言一致）"

# ── 10. 取证：倒出 PG 语句日志 + 表结构 ─────────────────────────────────
say "倒出 PG 语句日志"
docker logs "${PG_CONTAINER}" > "${TMP}/pg.log" 2>&1
ok "PG 日志 ${#TMP} 字节级位置：$(wc -l < "${TMP}/pg.log" | tr -d ' ') 行 → ${TMP}/pg.log"

# 表结构 + 约束 + 唯一索引（用于把 SELECT * 展开成真实列清单 ⇒ 投影可比；
#                以及判定「ORDER BY 差异是否等价」——需要唯一键证据）
# 🔴 **唯一索引必须单独查**：CREATE UNIQUE INDEX 建的**不是约束**
#    ⇒ information_schema.table_constraints 里**看不到**
#    ⇒ 只查约束会漏掉 uk_favorite_folder_folder_id / uk_folder_target
#       ⇒ 「WHERE 钉住唯一键」判不出来 ⇒ GORM 的 ORDER BY <pk> 会被误报成语义差异。
# 🔴 **必须导全部表**（不能只导业务三张）：Go 侧的 `SELECT *` 会打到
#    `favorite_legacy_fact_marker`（favoriteAssertionSchema 的 legacy 判定）
#    ⇒ 快照漏了它 ⇒ `SELECT *` 展开成空集 ⇒ 投影被误报成「Go 少投影了 8 列」。
# 🔴 **2026-10-26 第二十三轮**：COL 行的第 4/5 槽改带 **DDL 元信息**
#    （第 4 槽 = column_default，无 DEFAULT 时 <NODEFAULT>；第 5 槽 = is_nullable）
#    ⇒ 比对器才能判「少写一列 ⇒ DB 用 DEFAULT 补 ⇒ 落库值其实相同」
#    （此前只知道列名 ⇒ 把 technical_offset（DEFAULT 0）误报成「落库值不同」）。
docker exec "${PG_CONTAINER}" psql -U postgres -h 127.0.0.1 -d first_db -At -F $'\t' \
  -c "SELECT 'COL', c.table_name, c.ordinal_position::text,
             coalesce(c.column_default, '<NODEFAULT>'), c.is_nullable, c.column_name
        FROM information_schema.columns c
       WHERE c.table_schema='public'
      UNION ALL
      SELECT 'UC', k.table_name, k.ordinal_position::text, k.constraint_name, t.constraint_type, k.column_name
        FROM information_schema.key_column_usage k
        JOIN information_schema.table_constraints t
          ON t.constraint_name = k.constraint_name AND t.table_schema = k.table_schema
       WHERE k.table_schema='public'
         AND t.constraint_type IN ('PRIMARY KEY','UNIQUE')
      UNION ALL
      SELECT 'UI', c.relname::text, k.ord::text, ic.relname::text, 'UNIQUE', a.attname::text
        FROM pg_index x
        JOIN pg_class c ON c.oid = x.indrelid
        JOIN pg_class ic ON ic.oid = x.indexrelid
        JOIN LATERAL unnest(x.indkey) WITH ORDINALITY AS k(attnum, ord) ON true
        JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum = k.attnum
       WHERE x.indisunique AND NOT x.indisprimary
         AND c.relnamespace = 'public'::regnamespace
      ORDER BY 1,2,4,3" > "${TMP}/schema.tsv" 2>&1
ok "表结构快照：$(grep -c '^COL' "${TMP}/schema.tsv" | tr -d ' ') 列 / $(grep -c '^UC' "${TMP}/schema.tsv" | tr -d ' ') 约束列 / $(grep -c '^UI' "${TMP}/schema.tsv" | tr -d ' ') 唯一索引列"

# ── 11. 抽取 + 比对 ───────────────────────────────────────────────────────
say "抽取语句序列并按 marker 分段"
python3 "${HERE}/extract.py" "${TMP}/pg.log" "${TMP}/schema.tsv" "${TMP}/extracted.json" \
  || { bad "抽取失败"; exit 2; }

say "比对两侧语义签名"
# 🔴 第二十三轮：导出 GO_REPO ⇒ 比对器能**校验等价台账里的 Go 出处**
#    （文件不存在 / 行号越界 ⇒ 该条登记作废 ⇒ 差异照旧报成 SEMANTIC）
export GO_REPO
python3 "${HERE}/compare.py" "${TMP}/extracted.json" "${TMP}/cases.tsv" "${TMP}/report.md"
RC=$?
say "报告已写入 ${TMP}/report.md（KEEP_TMP=1 可保留）"

# 报告固定落到工具目录下（供 review 引用）
OUT_DIR="${RAG_ROOT}/tools/sql-statement-probe/favorite/out"
mkdir -p "${OUT_DIR}"
cp "${TMP}/report.md" "${OUT_DIR}/report.md"
cp "${TMP}/extracted.json" "${OUT_DIR}/extracted.json"
ok "报告已落盘：${OUT_DIR}/report.md"

if [ "${RC}" -ne 0 ]; then
  bad "存在**语义**差异（退出码 ${RC}）⇒ 见报告"
  exit 1
fi
ok "两侧 SQL 语句层语义一致（差异均在已登记的 ORM 噪声档内）"
exit 0
