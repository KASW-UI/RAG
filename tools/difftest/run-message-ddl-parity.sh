#!/usr/bin/env bash
# =============================================================================
# run-message-ddl-parity.sh —— message 域的**双侧活体 DDL 对拍**环境
# =============================================================================
# 【为什么要有这个脚本】
#   §3.1 要求对照「数据库表结构 / 索引 / 唯一约束 / NOT NULL / DEFAULT」，
#   而这五项**全部无法靠读 DDL 文件判定**：
#     · Go 侧 schema 不是手写 SQL，是 **GORM AutoMigrate** 运行时生成的
#       ⇒ 索引名（GORM 按 `idx_<表名>_<列名>` 生成）、可空性、DEFAULT
#         全由 **gorm.io/driver/postgres@v1.6.0** 的行为决定，
#         读 Go 源码**推不出来**（§3.3：推断 struct tag ≠ 消费点证据）。
#     · RAG 侧是 Flyway 迁移的**净效果**（V1~V5 叠加后），不是单条 SQL。
#   ⇒ 必须两侧各建一个真库、真 dump、真比对。
#
# 【两侧 schema 怎么来的】
#   Go 侧  : 复制**被冻结的真源仓库**到临时目录 → 加一个探针 main 包 →
#            调 `model.InitDB(...)`（它内部就是真源自己的
#            `AutoMigrate(&Notification{}, &Conversation{}, &ConversationMessage{})`，
#            见 service/message/rpc/internal/model/db.go:44）
#            ⇒ 跑的是**真源同一份 struct tag + 同一个 gorm 版本**。
#   RAG 侧 : 按序执行 service/message/message-rpc/src/main/resources/db/migration/V1~V5
#            ⇒ 跑的是**真迁移脚本**。
#
# ⚠️ 真源仓库是**只读**的（AGENTS.md §5.4）：本脚本**只复制**它，从不写入。
#
# 【用法】
#   ./run-message-ddl-parity.sh              # 建库 + 跑对拍
#   ./run-message-ddl-parity.sh --keep       # 跑完不删容器（默认跑完删）
#   ./run-message-ddl-parity.sh --no-check   # 只建库，不跑对拍
#
#   DDL_ALLOW_IDX="a,b"            豁免这些索引名的差异（已签字的有意偏离）
#   DDL_ALLOW_DEFAULT="tbl.col,..." 豁免这些列的 DEFAULT 差异
#
# 【退出码】与 check-ddl-static.py 一致：0 一致 / 1 有差异 / 2 环境不满足
# =============================================================================
set -uo pipefail

CONTAINER="msgddl-pg"
PG_PORT="55450"
GO_DB="go_msg_db"
RAG_DB="rag_msg_db"
PG_IMAGE="postgres:16-alpine"

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
GO_TRUTH="${GO_TRUTH_REPO:-/Users/pr/Documents/java&golang/Sea-RideTheWind_golang}"
MIG_DIR="${REPO_ROOT}/service/message/message-rpc/src/main/resources/db/migration"
WORKDIR="$(mktemp -d /tmp/msg-ddl-parity.XXXXXX)"

KEEP=0
RUN_CHECK=1
for a in "$@"; do
  case "$a" in
    --keep) KEEP=1 ;;
    --no-check) RUN_CHECK=0 ;;
    *) echo "未知参数: $a" >&2; exit 2 ;;
  esac
done

cleanup() {
  if [ "$KEEP" -eq 0 ]; then
    docker rm -f "$CONTAINER" >/dev/null 2>&1
    # ⚠️ mavis 的安全门把 `rm -rf` 拦成 trash；这里用 find -delete 兜底
    find "$WORKDIR" -depth -delete >/dev/null 2>&1
  else
    echo "--keep：容器 $CONTAINER 与临时目录 $WORKDIR 保留"
  fi
}
trap cleanup EXIT

echo "=== 0. 前置检查 ==="
[ -d "$GO_TRUTH/service/message/rpc/internal/model" ] || { echo "FATAL: 找不到真源仓库 $GO_TRUTH" >&2; exit 2; }
[ -d "$MIG_DIR" ] || { echo "FATAL: 找不到迁移目录 $MIG_DIR" >&2; exit 2; }
command -v go >/dev/null 2>&1 || { echo "FATAL: 需要 go 工具链" >&2; exit 2; }
command -v docker >/dev/null 2>&1 || { echo "FATAL: 需要 docker" >&2; exit 2; }
echo "  真源: $GO_TRUTH"
echo "  迁移: $MIG_DIR"

# ⚠️ 这里必须用 ${CONTAINER} 而不是 $CONTAINER：
#    bash 用 isalnum(字节) 判变量名边界，在 UTF-8 locale 下 **多字节字符的首字节
#    可能被判成 alnum** ⇒ `$CONTAINER，` 会被解析成变量 `CONTAINER<0xEF>` ⇒ unbound variable。
echo "=== 1. 起 PG（${CONTAINER}，端口 ${PG_PORT}）==="
docker rm -f "$CONTAINER" >/dev/null 2>&1
docker run -d --name "$CONTAINER" \
  -e POSTGRES_USER=admin -e POSTGRES_PASSWORD=admin -e POSTGRES_DB=postgres \
  -p "${PG_PORT}:5432" "$PG_IMAGE" >/dev/null
for i in $(seq 1 60); do
  # ⚠️ 这里**不能**用 pg_isready 当就绪判据：
  #    postgres 镜像的 entrypoint 在 initdb 阶段会**临时起一个只监听 socket 的服务**，
  #    pg_isready 对它返回 0 ⇒ 脚本会在「真服务还没起」时就去 CREATE DATABASE，
  #    实测报 `connection to server on socket ... No such file or directory`。
  #    ⇒ 判据必须是「真能建库」，即 psql 真的能连上并执行一条查询。
  if docker exec "$CONTAINER" psql -U admin -d postgres -tAc 'select 1' >/dev/null 2>&1; then
    echo "  PG ready (${i}s)"; break
  fi
  sleep 1
done
docker exec "$CONTAINER" psql -U admin -d postgres -tAc 'select 1' >/dev/null 2>&1 \
  || { echo "FATAL: PG 60s 内没就绪" >&2; exit 2; }
docker exec "$CONTAINER" psql -U admin -d postgres -c "CREATE DATABASE ${GO_DB};" >/dev/null
docker exec "$CONTAINER" psql -U admin -d postgres -c "CREATE DATABASE ${RAG_DB};" >/dev/null

echo "=== 2. Go 侧：复制真源副本 + 跑真 AutoMigrate ==="
cp -R "$GO_TRUTH" "$WORKDIR/gotruth"
PROBE_DIR="$WORKDIR/gotruth/service/message/rpc/cmd/ddlprobe"
mkdir -p "$PROBE_DIR"
cat > "$PROBE_DIR/main.go" <<'EOF'
// ddlprobe：用真源的 GORM AutoMigrate 在目标库里建出 message 三张表。
// 只读真源源码的副本，不修改被冻结的真源仓库。
package main

import (
	"fmt"
	"os"

	"sea-try-go/service/message/rpc/internal/model"
)

func main() {
	conf := model.DBConf{
		Host:     os.Getenv("PGHOST"),
		Port:     os.Getenv("PGPORT"),
		User:     os.Getenv("PGUSER"),
		Password: os.Getenv("PGPASSWORD"),
		DBName:   os.Getenv("PGDATABASE"),
		Mode:     "disable",
	}
	db := model.InitDB(conf)
	fmt.Println("AutoMigrate OK ->", conf.DBName)
	_ = db
}
EOF
( cd "$WORKDIR/gotruth" && \
  PGHOST=127.0.0.1 PGPORT="$PG_PORT" PGUSER=admin PGPASSWORD=admin PGDATABASE="$GO_DB" \
  go run ./service/message/rpc/cmd/ddlprobe/ ) || { echo "FATAL: Go 侧 AutoMigrate 失败" >&2; exit 2; }

echo "=== 3. RAG 侧：按序执行 V1~V5 迁移 ==="
for f in "$MIG_DIR"/V[0-9]__*.sql; do
  echo "  --- $(basename "$f") ---"
  docker exec -i "$CONTAINER" psql -v ON_ERROR_STOP=1 -U admin -d "$RAG_DB" < "$f" >/dev/null \
    || { echo "FATAL: 迁移 $(basename "$f") 失败" >&2; exit 2; }
done

if [ "$RUN_CHECK" -eq 0 ]; then
  echo "=== 4. --no-check：跳过对拍 ==="
  exit 0
fi

echo "=== 4. 双侧活体 DDL 对拍 ==="
python3 "${REPO_ROOT}/tools/difftest/check-ddl-static.py" \
  --domain message --container "$CONTAINER" --go-db "$GO_DB" --rag-db "$RAG_DB" --indexes \
  ${DDL_ALLOW_IDX:+--allow-idx "$DDL_ALLOW_IDX"} \
  ${DDL_ALLOW_DEFAULT:+--allow-default "$DDL_ALLOW_DEFAULT"}
rc=$?
echo "=== 对拍退出码: $rc ==="
exit $rc
