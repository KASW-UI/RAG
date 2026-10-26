// ============================================================================
// schemaprep —— 🔴 **探针代码**（不是真源复制件）
// ----------------------------------------------------------------------------
// 目的：按**真源文档指定的 sql-first 部署顺序**，在 Go 侧库里建出与真源一致的 schema。
//
// 为什么需要它：
//   真源 favorite-rpc 启动时的 model.InitDB（internal/model/db.go:20-41）只做
//   「AutoMigrate(folder, item, outbox) + target_id 修型」。
//   但 AutoMigrate **建不出** 手写 SQL 里的东西：CHECK、部分索引、两个不可变触发器、
//   以及 favorite_legacy_fact_marker 整张表。
//   ⇒ 若让应用先起（app-first），001 的 `CREATE TABLE IF NOT EXISTS favorite_fact_outbox`
//     面对已存在的表变成 no-op ⇒ 001 内联的 CHECK 建不出来。
//   ⇒ 真源文档（DELIVERY_ACCEPTANCE.md:7）指定的是 **sql-first**：
//     「先在现有 Favorite PostgreSQL 库执行 001…再执行 002」⇒ 应用后起。
//
//   本探针的判定基准 = **sql-first**（与 tools/ddl-parity-probe/favorite 的
//   SCENARIO=sql-first 完全一致，那一轮已把两者的 DDL 差异逐条钉死）。
//
// 步骤（与真源文档同款）：
//   1) AutoMigrate(favorite_folder, favorite_item)  ← 存量老表（真源文档说的「现有 PG」）
//   2) 依次执行真源的 001 / 002 / 003 / 004
//   3) 之后 favorite-rpc 进程启动时的 InitDB 会 AutoMigrate 三张表（幂等）
//
// ⚠️ 003 要 ALTER favorite_item ⇒ 必须先有 favorite_item ⇒ 步骤 1 不能省。
// ⚠️ 执行 .sql 必须用 **simple protocol**：pgx 的扩展协议不允许一条 Exec 里多语句，
//    `$$ ... $$` 的函数体必须整条一起送进去，不能按分号切。
// ============================================================================

package main

import (
	"fmt"
	"os"
	"path/filepath"

	"gorm.io/driver/postgres"
	"gorm.io/gorm"

	"sea-try-go/service/favorite/rpc/internal/model"
)

func main() {
	if err := run(); err != nil {
		fmt.Fprintf(os.Stderr, "PROBE-ERROR: %v\n", err)
		os.Exit(1)
	}
}

func env(k, def string) string {
	if v := os.Getenv(k); v != "" {
		return v
	}
	return def
}

func run() error {
	host := env("PG_HOST", "127.0.0.1")
	port := env("PG_PORT", "5432")
	user := env("PG_USER", "postgres")
	pass := env("PG_PASSWORD", "probe")
	dbname := env("PG_DB", "godb")
	sslmode := env("PG_SSLMODE", "disable")

	// 与真源 db.go:21-22 的 DSN 拼装**逐字一致**（含 sslmode 来自 conf.Mode）
	dsn := fmt.Sprintf("host=%s user=%s password=%s dbname=%s port=%s sslmode=%s TimeZone=Asia/Shanghai",
		host, user, pass, dbname, port, sslmode)

	say("sql-first 建表：host=%s port=%s db=%s", host, port, dbname)

	// ── 步骤 1：存量老表（只有 favorite_folder / favorite_item）────────────────
	db, err := gorm.Open(postgres.Open(dsn), &gorm.Config{})
	if err != nil {
		return fmt.Errorf("open: %w", err)
	}
	if err := db.AutoMigrate(&model.FavoriteFolder{}, &model.FavoriteItem{}); err != nil {
		return fmt.Errorf("automigrate(legacy tables): %w", err)
	}
	say("  步骤 1 ✔ 存量老表 AutoMigrate(favorite_folder, favorite_item)")

	// ── 步骤 2：运维执行真源的 4 个 .sql ---------------------------------------
	sqlDir := env("SQL_DIR", "")
	if sqlDir == "" {
		return fmt.Errorf("必须给 SQL_DIR（指向真源 model/*.sql 的目录）")
	}
	if err := runSqlFiles(dsn, sqlDir); err != nil {
		return err
	}

	// ── 步骤 3：不代劳 —— 由 favorite-rpc 进程启动时的真源 InitDB 完成 ----------
	say("  步骤 3 ⇒ 交给 favorite-rpc 进程启动时的真源 InitDB（AutoMigrate 三张表）")
	return nil
}

// runSqlFiles 按 001→004 顺序执行真源的 4 个 .sql（模拟 psql 手工执行）
func runSqlFiles(dsn, sqlDir string) error {
	gdb, err := gorm.Open(postgres.New(postgres.Config{
		DSN:                   dsn,
		PreferSimpleProtocol: true,
	}), &gorm.Config{})
	if err != nil {
		return fmt.Errorf("gorm open simple: %w", err)
	}
	for _, f := range []string{
		"001_favorite_fact_outbox.sql",
		"002_favorite_fact_delivery.sql",
		"003_favorite_target_revision.sql",
		"004_favorite_legacy_fact_marker.sql",
	} {
		b, err := os.ReadFile(filepath.Join(sqlDir, f))
		if err != nil {
			return fmt.Errorf("读 %s: %w", f, err)
		}
		if err := gdb.Exec(string(b)).Error; err != nil {
			return fmt.Errorf("执行 %s: %w", f, err)
		}
		say("  步骤 2 ✔ 执行 %s", f)
	}
	return nil
}

func say(format string, args ...any) {
	fmt.Fprintf(os.Stderr, "[schemaprep] "+format+"\n", args...)
}
