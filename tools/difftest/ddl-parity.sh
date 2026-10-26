#!/usr/bin/env bash
# =============================================================================
# ddl-parity.sh —— **双侧活体** DDL 逐列对照的环境（global 域 L3 / X-7）
# =============================================================================
#
# 【为什么要有这个脚本】
#   AGENTS.md §3.1 要求对照「数据库表结构 / 索引 / 唯一约束 / NOT NULL / DEFAULT」，
#   而这五项**全部无法靠读 DDL 文件判定**：
#     · Go 侧 schema 不是手写 SQL，是 **GORM AutoMigrate** 运行时生成的
#       ⇒ 索引名、可空性、DEFAULT、整数位宽全由 **gorm.io/driver/postgres@v1.6.0**
#         的行为决定，读 Go 源码**推不出来**（§3.3.1：推断 struct tag ≠ 消费点证据）。
#     · RAG 侧是 Flyway 迁移的**净效果**（V1~Vn 叠加后），不是单条 SQL。
#   ⇒ 必须两侧各建一个真库、真 dump、真比对。
#
# 【两侧 schema 怎么来的】
#   Go 侧  : 复制**被冻结的真源仓库**到临时目录 → 加一个探针 main 包 →
#            调 `model.InitDB(...)`（它内部就是真源自己的 AutoMigrate）
#            ⇒ 跑的是**真源同一份 struct tag + 同一个 gorm 版本**。
#            ⚠️ 部分域还有**手写 .sql**（AutoMigrate 覆盖不到的那一半），
#               按生产顺序在 AutoMigrate **之后**补放。
#   RAG 侧 : 按序执行该域的迁移脚本 ⇒ 跑的是**真迁移脚本**。
#
# ⚠️ 真源仓库是**只读**的（AGENTS.md §5.4）：本脚本**只复制**它，从不写入。
#
# 【用法】
#   ./ddl-parity.sh <域> [<域> ...]     建库 + 跑对拍
#            （域：article like hot follow favorite users comment task message）
#   ./ddl-parity.sh all                 串行跑全部 9 个域
#   ./ddl-parity.sh like --no-check     只建库，不跑对拍
#   ./ddl-parity.sh like --cleanup      跑完删掉容器（默认**保留**，方便直接重跑对拍）
#
#   DDL_ALLOW_IDX="a,b"              豁免这些索引名的差异（已签字的有意偏离）
#   DDL_ALLOW_DEFAULT="tbl.col,..."  豁免这些列的 DEFAULT 差异
#
# 【退出码】与 check-ddl-static.py 一致：
#   0 一致 / 1 有实质差异 / 2 环境不满足 / 3 本域不适用（N/A）
# =============================================================================
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
GO_TRUTH="${GO_TRUTH_REPO:-/Users/pr/Documents/java&golang/Sea-RideTheWind_golang}"
PG_IMAGE="postgres:16-alpine"
PG_USER="admin"
PG_PASS="admin"

RUN_CHECK=1
CLEANUP=0
WITH_GO_SQL=0
DOMAINS=()

for a in "$@"; do
  case "$a" in
    --no-check) RUN_CHECK=0 ;;
    --cleanup)  CLEANUP=1 ;;
    --with-go-sql) WITH_GO_SQL=1 ;;
    all)        DOMAINS=(article like hot follow favorite users comment task message) ;;
    -h|--help)  sed -n '1,37p' "$0"; exit 0 ;;   # 37 = 头部注释的结束行
    *)          DOMAINS+=("$a") ;;
  esac
done
[ "${#DOMAINS[@]}" -gt 0 ] || { echo "用法: $0 <域>|all [--no-check] [--cleanup]" >&2; exit 2; }

ALL_DOMAINS=(article like hot follow favorite users comment task message)

# ── 每个域一个独立容器 / 端口（串行跑，避免与其他 lane 抢 Docker 资源）──────
port_for() {
  case "$1" in
    article)  echo 55560 ;;
    like)     echo 55561 ;;
    hot)      echo 55562 ;;
    follow)   echo 55563 ;;
    favorite) echo 55564 ;;
    users)    echo 55565 ;;
    comment)  echo 55566 ;;
    task)     echo 55568 ;;
    message)  echo 55569 ;;
    *)        echo 55567 ;;
  esac
}

# ── 域配置：Go 侧探针包 / Go 侧手写 SQL / RAG 侧迁移脚本 ────────────────────
# 探针种类：dsn（InitDB(dsn)）· dbconf（InitDB(DBConf)）· config（InitDB(config.Config)）
domain_config() {
  GO_SQL_FIXTURE=""
  case "$1" in
    article)
      PROBE_PKGS="article:config:service/article/rpc"
      GO_SQL="service/article/rpc/internal/model/002_community_revision.sql"
      RAG_SQL="service/article/article-rpc/src/main/resources/db/migration/V8__create_article.sql
service/article/article-rpc/src/main/resources/db/migration/V9__create_article_sync_outbox.sql
service/article/article-rpc/src/main/resources/db/migration/V10__create_article_community_revision.sql"
      ;;
    like)
      PROBE_PKGS="like:dsn:service/like/rpc"
      # ⚠️ Go 侧 migrations/001~003 **默认不放**：它们在 `internal/model/migrations/` 下
      #    （与 comment 域的 001~003 **同一路径形态**），而 comment 域那三份已被签字
      #    认定为「**测试夹具**，不进生产」
      #    （docs/Intentional_Deviations/comment/rpc.md §SD-2026-10-24-CAND-02 的
      #      「根因」表：001/002/003 只被 test-community-postgres.sh 执行）。
      #    ⇒ like 的生产权威 DDL = **AutoMigrate 产物**。
      #    ⚠️ 001/002/003 里的 CHECK / 部分索引 / 触发器在 **生产 Go 侧根本不存在**
      #    （003 全仓无任何引用）⇒ 用 --with-go-sql 可复现「连测试夹具一起放」的那一档。
      GO_SQL=""
      GO_SQL_FIXTURE="service/like/rpc/internal/model/migrations/001_like_domain_fact_outbox.sql
service/like/rpc/internal/model/migrations/002_like_dc_wire.sql
service/like/rpc/internal/model/migrations/003_like_fact_delivery.sql"
      RAG_SQL="service/like/like-rpc/src/main/resources/db/migration/V1__create_like_record.sql
service/like/like-rpc/src/main/resources/db/migration/V2__create_like_consume_inbox.sql
service/like/like-rpc/src/main/resources/db/migration/V3__create_like_outbox_event.sql
service/like/like-rpc/src/main/resources/db/migration/V4__create_like_domain_fact_outbox.sql
service/like/like-rpc/src/main/resources/db/migration/V5__create_like_fact_stream.sql
service/like/like-rpc/src/main/resources/db/migration/V6__align_like_domain_fact_outbox_with_go.sql"
      ;;
    hot)
      # ⚠️ hot 域**根本没有数据库**：Go 侧 service/hot 全域无 gorm / 无 AutoMigrate
      #    （只有 Redis + Kafka）；RAG 侧 hot-rpc 无 db/migration 目录、无 Mapper/Entity。
      PROBE_PKGS=""
      GO_SQL=""
      RAG_SQL=""
      ;;
    follow)
      PROBE_PKGS="follow:dbconf:service/follow/rpc"
      GO_SQL=""
      RAG_SQL="service/follow/follow-rpc/src/main/resources/db/migration/V1__create_follow_relation.sql
service/follow/follow-rpc/src/main/resources/db/migration/V2__create_block_relation.sql"
      ;;
    favorite)
      PROBE_PKGS="favorite:dbconf:service/favorite/rpc"
      GO_SQL="service/favorite/rpc/internal/model/001_favorite_fact_outbox.sql
service/favorite/rpc/internal/model/002_favorite_fact_delivery.sql
service/favorite/rpc/internal/model/003_favorite_target_revision.sql
service/favorite/rpc/internal/model/004_favorite_legacy_fact_marker.sql"
      RAG_SQL="service/favorite/favorite-rpc/src/main/resources/db/migration/V1__create_favorite_folder.sql
service/favorite/favorite-rpc/src/main/resources/db/migration/V2__create_favorite_item.sql
service/favorite/favorite-rpc/src/main/resources/db/migration/V3__create_favorite_fact_outbox.sql
service/favorite/favorite-rpc/src/main/resources/db/migration/V4__create_favorite_legacy_fact_marker.sql
service/favorite/favorite-rpc/src/main/resources/db/migration/V5__add_favorite_fact_delivery.sql
service/favorite/favorite-rpc/src/main/resources/db/migration/V6__align_favorite_schema_with_go.sql"
      ;;
    users)
      # ⚠️ users 域要**两个** Go 探针：admin 两张表由 admin/rpc 自己的 InitDB 产出
      #    （service/user/admin/rpc/internal/model/db.go:15）
      PROBE_PKGS="user:dbconf:service/user/user/rpc
admin:dsn:service/user/admin/rpc"
      GO_SQL=""
      RAG_SQL="service/users/users-common/src/main/resources/db/01_create_user.sql
service/users/users-common/src/main/resources/db/02_create_user_avatar_history.sql
service/users/users-common/src/main/resources/db/03_create_admin.sql
service/users/users-common/src/main/resources/db/04_create_admin_invite.sql"
      ;;
    comment)
      PROBE_PKGS="comment:dsn:service/comment/rpc"
      GO_SQL=""
      RAG_SQL="service/comment/comment-rpc/src/main/resources/db/migration/V1__create_comment_subject.sql
service/comment/comment-rpc/src/main/resources/db/migration/V2__create_comment_index.sql
service/comment/comment-rpc/src/main/resources/db/migration/V3__create_comment_content.sql
service/comment/comment-rpc/src/main/resources/db/migration/V4__create_comment_like.sql
service/comment/comment-rpc/src/main/resources/db/migration/V5__create_comment_report_record.sql
service/comment/comment-rpc/src/main/resources/db/migration/V6__create_comment_domain_fact_outbox.sql
service/comment/comment-rpc/src/main/resources/db/migration/V7__create_comment_fact_stream.sql
service/comment/comment-rpc/src/main/resources/db/migration/V8__align_comment_fact_delivery_with_go.sql"
      ;;
    task)
      # ✅ 2026-10-26（global 域 L10）新增：task 域进**本 runner 自管的容器**。
      # ⚠️ task 域**没有** model.InitDB —— 真源是在
      #    service/task/rpc/internal/svc/service_context.go:47 里**直接**
      #    `gdb.AutoMigrate(&Model.TaskProgress{}, &Model.UserLikeCount{})`
      #    ⇒ 探针必须复刻那**同一行**调用（同一份 struct tag + 同一个 gorm 版本），
      #    否则建出来的表就不是真源生产那张。
      # ⚠️ Model 包的路径是 **internal/Model（大写 M）** —— 与其它域的 internal/model 不同。
      PROBE_PKGS="task:automigrate:service/task/rpc"
      GO_SQL=""
      RAG_SQL="service/task/task-rpc/src/main/resources/db/migration/V1__create_task_progress.sql
service/task/task-rpc/src/main/resources/db/migration/V2__create_user_like_count.sql"
      ;;
    message)
      # ✅ 2026-10-26（global 域 L10）新增：message 域进**本 runner 自管的容器**。
      # ⚠️ message 域**有** model.InitDB(DBConf) ⇒ 走 dbconf 探针
      #    （service/message/rpc/internal/model/db.go:20，内部 db.go:44 跑 AutoMigrate）。
      PROBE_PKGS="message:dbconf:service/message/rpc"
      GO_SQL=""
      RAG_SQL="service/message/message-rpc/src/main/resources/db/migration/V1__create_message_notification.sql
service/message/message-rpc/src/main/resources/db/migration/V2__create_message_conversation.sql
service/message/message-rpc/src/main/resources/db/migration/V3__create_message_conversation_message.sql
service/message/message-rpc/src/main/resources/db/migration/V4__conversation_pair_unique_index.sql
service/message/message-rpc/src/main/resources/db/migration/V5__message_time_columns_timestamptz.sql"
      ;;
    *)
      echo "FATAL: 未知域: $1" >&2; exit 2 ;;
  esac
}

# ── 探针源码（只调真源的 model.InitDB ⇒ 跑的是真源自己的 AutoMigrate）──────
write_probe() {
  local kind="$1" pkg="$2" dir="$3"
  mkdir -p "$dir"
  case "$kind" in
    dsn)
      cat > "$dir/main.go" <<'EOF'
// ddlprobe：用真源的 GORM AutoMigrate 在目标库里建出本域的表。
// 只读真源源码的副本，不修改被冻结的真源仓库。
package main

import (
	"fmt"
	"os"

	"sea-try-go/PKG/internal/model"
)

func main() {
	dsn := fmt.Sprintf("host=%s port=%s user=%s password=%s dbname=%s sslmode=disable TimeZone=Asia/Shanghai",
		os.Getenv("PGHOST"), os.Getenv("PGPORT"), os.Getenv("PGUSER"),
		os.Getenv("PGPASSWORD"), os.Getenv("PGDATABASE"))
	model.InitDB(dsn)
	fmt.Println("AUTOMIGRATE_OK")
}
EOF
      ;;
    dbconf)
      cat > "$dir/main.go" <<'EOF'
// ddlprobe：用真源的 GORM AutoMigrate 在目标库里建出本域的表。
// 只读真源源码的副本，不修改被冻结的真源仓库。
package main

import (
	"fmt"
	"os"

	"sea-try-go/PKG/internal/model"
)

func main() {
	model.InitDB(model.DBConf{
		Host:     os.Getenv("PGHOST"),
		Port:     os.Getenv("PGPORT"),
		User:     os.Getenv("PGUSER"),
		Password: os.Getenv("PGPASSWORD"),
		DBName:   os.Getenv("PGDATABASE"),
		Mode:     "disable",
	})
	fmt.Println("AUTOMIGRATE_OK")
}
EOF
      ;;
    config)
      cat > "$dir/main.go" <<'EOF'
// ddlprobe：用真源的 GORM AutoMigrate 在目标库里建出本域的表。
// 只读真源源码的副本，不修改被冻结的真源仓库。
//
// ⚠️ article 域的 InitDB 在 AutoMigrate **之后**会检查
//    `article_revision_immutable` 触发器是否存在（model/db.go:61-71），
//    缺迁移就返回 error ⇒ 第一次跑必然报错（这是**真源的启动契约**，不是 bug）。
//    探针把 error 打印出来并以 0 退出，让 runner 能在补放 002 之后重跑。
package main

import (
	"fmt"
	"os"

	"sea-try-go/PKG/internal/config"
	"sea-try-go/PKG/internal/model"
)

func main() {
	conf := config.Config{}
	conf.Postgres.Host = os.Getenv("PGHOST")
	conf.Postgres.Port = os.Getenv("PGPORT")
	conf.Postgres.User = os.Getenv("PGUSER")
	conf.Postgres.Password = os.Getenv("PGPASSWORD")
	conf.Postgres.Dbname = os.Getenv("PGDATABASE")
	if _, err := model.InitDB(conf); err != nil {
		fmt.Println("INITDB_ERR:", err)
		return
	}
	fmt.Println("AUTOMIGRATE_OK")
}
EOF
      ;;
    automigrate)
      # ⚠️ task 域**没有** model.InitDB —— 真源是在
      #    service/task/rpc/internal/svc/service_context.go:47 里**直接**
      #    `gdb.AutoMigrate(&Model.TaskProgress{}, &Model.UserLikeCount{})`
      #    ⇒ 探针复刻那**同一行**调用（同一份 struct tag + 同一个 gorm 版本）。
      cat > "$dir/main.go" <<'EOF'
// ddlprobe：用真源的 GORM AutoMigrate 在目标库里建出本域的表。
// 只读真源源码的副本，不修改被冻结的真源仓库。
//
// ⚠️ 本探针对应真源里**没有** InitDB 包装的域（task）：
//    真源在 service/task/rpc/internal/svc/service_context.go:47 直接调
//    `gdb.AutoMigrate(&Model.TaskProgress{}, &Model.UserLikeCount{})`
//    ⇒ 这里复刻**同一行**调用（gorm 版本与 struct tag 都取自真源副本）。
package main

import (
	"fmt"
	"os"

	"gorm.io/driver/postgres"
	"gorm.io/gorm"

	"sea-try-go/PKG/internal/Model"
)

func main() {
	dsn := fmt.Sprintf("host=%s port=%s user=%s password=%s dbname=%s sslmode=disable TimeZone=Asia/Shanghai",
		os.Getenv("PGHOST"), os.Getenv("PGPORT"), os.Getenv("PGUSER"),
		os.Getenv("PGPASSWORD"), os.Getenv("PGDATABASE"))
	gdb, err := gorm.Open(postgres.Open(dsn), &gorm.Config{})
	if err != nil {
		fmt.Println("GORM_OPEN_ERR:", err)
		return
	}
	if err := gdb.AutoMigrate(&Model.TaskProgress{}, &Model.UserLikeCount{}); err != nil {
		fmt.Println("AUTOMIGRATE_ERR:", err)
		return
	}
	fmt.Println("AUTOMIGRATE_OK")
}
EOF
      ;;
  esac
  # 把占位符 PKG 换成真包路径
  python3 - "$dir/main.go" "$pkg" <<'PY'
import sys
path, pkg = sys.argv[1], sys.argv[2]
s = open(path, encoding='utf-8').read().replace('sea-try-go/PKG/', f'sea-try-go/{pkg}/')
open(path, 'w', encoding='utf-8').write(s)
PY
}

run_domain() {
  local domain="$1"
  local container="ddl-${domain}-pg"
  local port; port="$(port_for "$domain")"
  local go_db="go_${domain}_db" rag_db="rag_${domain}_db"
  local workdir; workdir="$(mktemp -d "/tmp/ddl-parity-${domain}.XXXXXX")"

  domain_config "$domain"

  echo ""
  echo "══════════════════════════════════════════════════════════════"
  echo "== 域 ${domain}  容器 ${container}  端口 ${port}  库 ${go_db} / ${rag_db}"
  echo "══════════════════════════════════════════════════════════════"

  # ---- 0. 前置检查 ----
  command -v docker >/dev/null 2>&1 || { echo "FATAL: 需要 docker" >&2; return 2; }
  if [ -n "$PROBE_PKGS" ]; then
    [ -d "$GO_TRUTH" ] || { echo "FATAL: 找不到真源仓库 $GO_TRUTH" >&2; return 2; }
    command -v go >/dev/null 2>&1 || { echo "FATAL: 需要 go 工具链" >&2; return 2; }
  fi

  # ---- 1. 起 PG ----
  docker rm -f "$container" >/dev/null 2>&1
  docker run -d --name "$container" \
    -e POSTGRES_USER="$PG_USER" -e POSTGRES_PASSWORD="$PG_PASS" -e POSTGRES_DB=postgres \
    -p "${port}:5432" "$PG_IMAGE" >/dev/null || { echo "FATAL: 起容器失败" >&2; return 2; }
  # ⚠️ 就绪判据（踩过的坑）：postgres 镜像的 entrypoint 在 initdb 阶段会**临时起一个
  #    只监听 socket 的服务**跑初始化脚本，`docker exec psql` 连 socket **也能连上它**
  #    ⇒ 单次「psql 能执行查询」会把**临时服务**误判成「真服务已就绪」
  #    （实测 like 域第 2 秒就误判，随后 CREATE DATABASE 撞上 socket 消失）。
  #    ⇒ 判据必须是两条同时成立：
  #      ① 容器日志出现 "PostgreSQL init process complete"（这条出现在**临时服务已停**之后）
  #      ② psql 能连上并执行查询，且**连续 2 次**成功
  local i consec=0
  for i in $(seq 1 90); do
    if docker logs "$container" 2>&1 | grep -q "PostgreSQL init process complete"; then
      if docker exec "$container" psql -U "$PG_USER" -d postgres -tAc 'select 1' >/dev/null 2>&1; then
        consec=$((consec + 1))
        [ "$consec" -ge 2 ] && { echo "  PG ready (${i}s)"; break; }
      else
        consec=0
      fi
    fi
    sleep 1
  done
  [ "$consec" -ge 2 ] || { echo "FATAL: PG 90s 内没就绪" >&2; return 2; }
  docker exec "$container" psql -U "$PG_USER" -d postgres -c "CREATE DATABASE ${go_db};" >/dev/null
  docker exec "$container" psql -U "$PG_USER" -d postgres -c "CREATE DATABASE ${rag_db};" >/dev/null

  # ---- 2. Go 侧：复制真源副本 + 跑真 AutoMigrate ----
  if [ -n "$PROBE_PKGS" ]; then
    echo "  复制真源副本 → ${workdir}/gotruth"
    cp -R "$GO_TRUTH" "${workdir}/gotruth"
    local entry name kind pkg
    while IFS=: read -r name kind pkg; do
      [ -n "$name" ] || continue
      echo "  --- Go 探针: ${pkg} (${kind}) ---"
      write_probe "$kind" "$pkg" "${workdir}/gotruth/${pkg}/cmd/ddlprobe"
      ( cd "${workdir}/gotruth" && \
        CGO_ENABLED=0 GOPROXY=off GOFLAGS=-mod=mod \
        PGHOST=127.0.0.1 PGPORT="$port" PGUSER="$PG_USER" PGPASSWORD="$PG_PASS" PGDATABASE="$go_db" \
        go run "./${pkg}/cmd/ddlprobe/" ) || { echo "FATAL: Go 侧探针失败 (${pkg})" >&2; return 2; }
    done <<< "$PROBE_PKGS"
  else
    echo "  本域 Go 侧无数据库（无 AutoMigrate）⇒ 跳过 Go 侧建库"
  fi

  # ---- 3. Go 侧手写 .sql（AutoMigrate 覆盖不到的那一半）----
  if [ -n "$GO_SQL" ]; then
    local f
    while IFS= read -r f; do
      [ -n "$f" ] || continue
      echo "  --- Go 手写迁移: $(basename "$f") ---"
      docker exec -i "$container" psql -v ON_ERROR_STOP=1 -U "$PG_USER" -d "$go_db" \
        < "${GO_TRUTH}/${f}" >/dev/null || { echo "FATAL: Go 侧迁移失败 $f" >&2; return 2; }
    done <<< "$GO_SQL"
  fi

  # ---- 3b. Go 侧「测试夹具」SQL（默认**不放**；--with-go-sql 才放）----
  if [ "$WITH_GO_SQL" -eq 1 ] && [ -n "$GO_SQL_FIXTURE" ]; then
    echo "  ⚠️ --with-go-sql：额外放 Go 侧**测试夹具**迁移（生产 Go 侧不放这些）"
    local f
    while IFS= read -r f; do
      [ -n "$f" ] || continue
      echo "  --- Go 夹具迁移: $(basename "$f") ---"
      docker exec -i "$container" psql -v ON_ERROR_STOP=1 -U "$PG_USER" -d "$go_db" \
        < "${GO_TRUTH}/${f}" >/dev/null || { echo "FATAL: Go 侧夹具迁移失败 $f" >&2; return 2; }
    done <<< "$GO_SQL_FIXTURE"
  fi

  # ---- 4. RAG 侧：按序执行迁移脚本 ----
  if [ -n "$RAG_SQL" ]; then
    local f
    while IFS= read -r f; do
      [ -n "$f" ] || continue
      echo "  --- RAG 迁移: $(basename "$f") ---"
      docker exec -i "$container" psql -v ON_ERROR_STOP=1 -U "$PG_USER" -d "$rag_db" \
        < "${REPO_ROOT}/${f}" >/dev/null || { echo "FATAL: RAG 侧迁移失败 $f" >&2; return 2; }
    done <<< "$RAG_SQL"
  else
    echo "  本域 RAG 侧无迁移脚本（无数据库）⇒ 跳过 RAG 侧建库"
  fi

  # ---- 5. 双侧活体 DDL 对拍 ----
  local rc=0
  if [ "$RUN_CHECK" -eq 1 ]; then
    echo "  --- 双侧活体 DDL 对拍 ---"
    python3 "${REPO_ROOT}/tools/difftest/check-ddl-static.py" \
      --domain "$domain" --container "$container" --go-db "$go_db" --rag-db "$rag_db" --indexes \
      ${DDL_ALLOW_IDX:+--allow-idx "$DDL_ALLOW_IDX"} \
      ${DDL_ALLOW_DEFAULT:+--allow-default "$DDL_ALLOW_DEFAULT"}
    rc=$?
    echo "  === 对拍退出码: $rc ==="
  else
    echo "  --no-check：跳过对拍"
  fi

  if [ "$CLEANUP" -eq 1 ]; then
    docker rm -f "$container" >/dev/null 2>&1
    find "$workdir" -depth -delete >/dev/null 2>&1
    echo "  --cleanup：容器 ${container} 与临时目录 ${workdir} 已删除"
  else
    echo "  容器 ${container} 与临时目录 ${workdir} 保留（可直接重跑对拍）"
  fi
  return $rc
}

# ── 主流程：串行跑（⚠️ 别与其他 lane 同时起容器，会互抢 Docker 资源）──────
overall=0
for d in "${DOMAINS[@]}"; do
  run_domain "$d"
  rc=$?
  [ "$rc" -eq 0 ] || overall=$rc
  echo ""
done
echo "══════════════════════════════════════════════════════════════"
echo "== 全部域跑完，最后一个非零退出码: ${overall}"
echo "══════════════════════════════════════════════════════════════"
exit $overall
