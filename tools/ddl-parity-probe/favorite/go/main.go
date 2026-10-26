// DDL 双侧活体对拍 · Go 侧驱动
//
// 目的：用**真源**的 GORM model + **真源的 4 个 .sql**，在**真 PG** 上建出
// Go 侧的 favorite 域 schema，然后把规范化后的 schema dump 与 RAG 侧逐行比。
//
// ⚠️ 本文件是**探针代码**（不是真源复制件）。model/ 目录下的 12 个文件才是
//    「从 Go 真源逐字节复制」的（run.sh 会做 sha256 校验）。
//
// 两个场景（关键！）：
//
//	SCENARIO=app-first —— 「应用先起、运维后跑 SQL」
//	    AutoMigrate(folder,item,outbox) → 001 → 002 → 003 → 004 → AutoMigrate(重启)
//	    ⚠️ 这个场景下 001 的 `CREATE TABLE IF NOT EXISTS favorite_fact_outbox`
//	       面对的是**已存在**的表 ⇒ **no-op** ⇒ 001 里的
//	       `CHECK (aggregate_version IN (1,2))` 与部分索引**不会真的建出来**。
//
//	SCENARIO=sql-first —— 「运维先跑 SQL、应用后起」
//	    AutoMigrate(folder,item)[老表] → 001 → 002 → 003 → 004 → AutoMigrate(all three)
//	    ⚠️ 003 要 ALTER favorite_item ⇒ 必须先有 favorite_item
//	       （真源文档说的「现有 PG」就是这种存量老表）。
//
// ⇒ 两个场景都跑，把「Go 侧 live schema 到底长什么样」用**事实**钉死，
//    而不是靠推理（AGENTS.md §3.3）。

package main

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"gorm.io/driver/postgres"
	"gorm.io/gorm"

	"ddlparityprobe/model"
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
	scenario := env("SCENARIO", "app-first")
	host := env("PG_HOST", "127.0.0.1")
	port := env("PG_PORT", "5432")
	user := env("PG_USER", "postgres")
	pass := env("PG_PASSWORD", "postgres")
	dbname := env("PG_DB", "godb")

	// 与真源 db.go:21-22 的 DSN 拼装**逐字一致**（含 sslmode 来自 conf.Mode）
	dsn := fmt.Sprintf("host=%s user=%s password=%s dbname=%s port=%s sslmode=%s TimeZone=Asia/Shanghai",
		host, user, pass, dbname, port, env("PG_SSLMODE", "disable"))

	conf := model.DBConf{
		Host:     host,
		Port:     port,
		User:     user,
		Password: pass,
		DBName:   dbname,
		Mode:     env("PG_SSLMODE", "disable"),
	}

	say("scenario=%s db=%s", scenario, dbname)

	// ── 步骤序列 ────────────────────────────────────────────────
	switch scenario {
	case "app-first":
		// 1) 应用启动：AutoMigrate 三张表（含 favorite_fact_outbox）
		must(initDB(conf))
		// 2) 运维按文档执行 4 个 .sql
		must(runSqlFiles())
		// 3) 应用重启：AutoMigrate 再跑一次（幂等）
		must(initDB(conf))

	case "sql-first":
		// 1) 存量老表：只有 favorite_folder / favorite_item（真源文档说的「现有 PG」）
		db, err := gorm.Open(postgres.Open(dsn), &gorm.Config{})
		if err != nil {
			return fmt.Errorf("open: %w", err)
		}
		if err := db.AutoMigrate(&model.FavoriteFolder{}, &model.FavoriteItem{}); err != nil {
			return fmt.Errorf("automigrate(legacy tables): %w", err)
		}
		say("  legacy tables migrated: favorite_folder, favorite_item")
		// 2) 运维执行 4 个 .sql
		must(runSqlFiles())
		// 3) 应用启动：AutoMigrate 三张表 + target_id 修复
		must(initDB(conf))

	default:
		return fmt.Errorf("未知 SCENARIO=%q（只支持 app-first / sql-first）", scenario)
	}

	// ── dump ────────────────────────────────────────────────────
	// dump.sql（语义行）与 names.sql（命名行）与 RAG 侧**共用同一份**
	//（run.sh 用 env 传绝对路径，避免依赖 cwd）
	dumpPath := env("DUMP_SQL", "sql/dump.sql")
	namesPath := env("NAMES_SQL", "sql/names.sql")
	semSQL, err := os.ReadFile(dumpPath)
	if err != nil {
		return fmt.Errorf("读 dump.sql（%s）: %w", dumpPath, err)
	}
	nameSQL, err := os.ReadFile(namesPath)
	if err != nil {
		return fmt.Errorf("读 names.sql（%s）: %w", namesPath, err)
	}
	semLines, err := queryDump(dsn, string(semSQL))
	if err != nil {
		return fmt.Errorf("dump 语义行: %w", err)
	}
	nameLines, err := queryDump(dsn, string(nameSQL))
	if err != nil {
		return fmt.Errorf("dump 命名行: %w", err)
	}
	for _, l := range semLines {
		fmt.Println(l)
	}
	for _, l := range nameLines {
		fmt.Println(l)
	}
	say("dump 完成：语义 %d 行 + 命名 %d 行", len(semLines), len(nameLines))
	return nil
}

// initDB 直接调用**真源**的 InitDB（model/db.go，逐字节复制件）
// ⇒ AutoMigrate + migrateFavoriteTargetIDToString 都是真源逻辑，不是复刻。
func initDB(conf model.DBConf) error {
	model.InitDB(conf) // 真源里失败是 log.Fatalln ⇒ 这里进程直接退出
	return nil
}

// runSqlFiles 按 001→004 顺序执行真源的 4 个 .sql（**模拟 psql 手工执行**）
// ⇒ 用 simple protocol，否则 pgx 的扩展协议不允许一条 Exec 里多语句
//    （`$$ ... $$` 的函数体必须整条一起送进去，不能按分号切）。
func runSqlFiles() error {
	dsn := fmt.Sprintf("host=%s user=%s password=%s dbname=%s port=%s sslmode=%s",
		env("PG_HOST", "127.0.0.1"), env("PG_USER", "postgres"), env("PG_PASSWORD", "postgres"),
		env("PG_DB", "godb"), env("PG_PORT", "5432"), env("PG_SSLMODE", "disable"))
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
		b, err := os.ReadFile(filepath.Join("model", f))
		if err != nil {
			return fmt.Errorf("读 %s: %w", f, err)
		}
		if err := gdb.Exec(string(b)).Error; err != nil {
			return fmt.Errorf("执行 %s: %w", f, err)
		}
		say("  执行 %s ✔", f)
	}
	return nil
}

func queryDump(dsn, dumpSQL string) ([]string, error) {
	gdb, err := gorm.Open(postgres.Open(dsn), &gorm.Config{})
	if err != nil {
		return nil, err
	}
	var out []string
	if err := gdb.Raw(dumpSQL).Scan(&out).Error; err != nil {
		return nil, err
	}
	// 去掉空行
	var res []string
	for _, l := range out {
		if strings.TrimSpace(l) != "" {
			res = append(res, l)
		}
	}
	return res, nil
}

func must(err error) {
	if err != nil {
		fmt.Fprintf(os.Stderr, "PROBE-ERROR: %v\n", err)
		os.Exit(1)
	}
}

func say(format string, args ...any) {
	fmt.Fprintf(os.Stderr, "[ddl-probe] "+format+"\n", args...)
}
