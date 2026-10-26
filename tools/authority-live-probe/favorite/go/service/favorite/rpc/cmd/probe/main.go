// 权威读 HTTP 面 · 双侧活体对拍 · Go 侧驱动
//
// ⚠️ 本文件是**探针代码**（不是真源复制件）。
//    真源复制件在 go/service/favorite/rpc/internal/{model,authority}/ 下 ——
//    那些文件是**从 Go 真源逐字节复制**的（run.sh 做 sha256 校验），**零修改**。
//
// 目的：把「权威读端点（favorite.fact.authority）的 HTTP 行为」从
//      「源码级推理 + RAG 侧单侧断言」升级为**双侧活体对拍**。
//
// 为什么以前做不了（2026-10-25 第十二轮推翻了这个判定）：
//   此前判「环境受限」的理由是「Go 侧 favorite-rpc 依赖 etcd + Kafka + Redis +
//   article-rpc / user-rpc」⇒ 但**那是对 gRPC 整域成立**，对**权威读 HTTP 面不成立**：
//   · Go cmd/fact-authority/main.go 只要 PG + 端口 + token；
//   · RAG FavoriteAuthorityEndpointConfig 只要 PG；
//   · 两边的读取链路（AuthoritativeFavoriteFact / FavoriteAuthorityLookupService）
//     **只查 favorite_fact_outbox + favorite_item 两张表** ⇒ 不碰 user-rpc / article-rpc。
//
// 两种模式：
//   --emit-seed   用**真源写入路径**（model.InsertFolder / model.InsertFavorite）
//                 造出合法的收藏 + 事实行，然后把 3 张表的 INSERT 打到 stdout
//                 ⇒ run.sh 把它冻结成 seed.sql（两侧共用同一份数据）。
//   （默认）       应用 seed.sql → 用**真 model** + **真 authority.NewHandler**
//                 跑请求矩阵 ⇒ 每行输出 `case_id\tvalue`。
//
// 输出格式（两侧**完全同格式**，便于逐行比对）：
//   case_id \t status|content-type|cache-control|location|allow|nosniff|body
//   body 用 Go 的 %q 转义（单行、无真实换行）⇒ 两侧都能逐字节比。
package main

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"time"

	jsoncanonicalizer "github.com/cyberphone/json-canonicalization/go/src/webpki.org/jsoncanonicalizer"
	"gorm.io/driver/postgres"
	"gorm.io/gorm"
	"gorm.io/gorm/clause"

	"sea-try-go/service/favorite/rpc/internal/authority"
	"sea-try-go/service/favorite/rpc/internal/model"
)

// ── 探针常量（与真源 / RAG 侧共享的测试数据）──────────────────────────────
const (
	probeToken     = "0123456789abcdef0123456789abcdef" // 32 字节（Go :35 要求 ≥32）
	probeUserID    = 1001
	probeFolderID  = 2001
	probeFavoriteID = 3001
	probeTargetID  = "article-77"
	probeTargetTyp = "article"
	probeTitle     = "seed title"
	probeCover     = "https://example.invalid/cover.png"
	probeProducer  = "rtw.community.favorite"
)

// probeSender 实现真源的 model.FavoriteFactSender —— 扮演 **DC 投递端**。
//
// ⚠️ 它返回的 InputHash = sha256(JCS(payload))，用的**是跟真源
//    `favoriteJCSHash`（favorite_fact.go:57）完全相同的库与算法**
//    （jsoncanonicalizer + sha256 + hex）⇒ 逐字一致。
// ⚠️ 而**真源自己**会在权威读时用 `favoriteJCSHash(row.Payload)` 复核
//    （favorite_authority.go:160-165）⇒ 本 stub 算错的话，真源会直接 404
//    ⇒ **探针自证**，不需要探针自己断言「hash 对不对」。
type probeSender struct {
	offset int64
}

func (s *probeSender) Send(_ context.Context, event model.FavoriteWireEvent,
	payload json.RawMessage) (model.FavoriteTechnicalReceipt, error) {
	canonical, err := jsoncanonicalizer.Transform(payload)
	if err != nil {
		return model.FavoriteTechnicalReceipt{}, err
	}
	sum := sha256.Sum256(canonical)
	s.offset++
	return model.FavoriteTechnicalReceipt{
		EventID:         event.EventID,
		Producer:        event.Producer,
		TechnicalStatus: "accepted",
		ReceiptID:       fmt.Sprintf("probe-receipt-%04d", s.offset),
		InputHash:       hex.EncodeToString(sum[:]),
		Offset:          s.offset,
		ReceivedAt:      time.Now().UTC().Format(time.RFC3339Nano),
	}, nil
}

// ── 种子行的固定标识（case 用它寻址）──────────────────────────────────────
var (
	// ⚠️ event_id 的形状 = `favorite.<favorite_id>.v<aggregate_version>`（真源
	//    favorite_authority.go:154）⇒ 第二条收藏（id=3002）的 assert 行（聚合版本=1）
	//    的 event_id 是 `favorite.3002.v1`，**不是** `favorite.3001.v2`
	//    ⇒ v1 与 v2 的**差别在 payload 里的 schema_version**，不在 event_id。
	v1EventID = fmt.Sprintf("favorite.%d.v1", probeFavoriteID)
	v2EventID = fmt.Sprintf("favorite.%d.v1", probeFavoriteID+1)
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
	dsn := fmt.Sprintf("host=%s user=%s password=%s dbname=%s port=%s sslmode=%s TimeZone=Asia/Shanghai",
		env("PG_HOST", "127.0.0.1"), env("PG_USER", "postgres"), env("PG_PASSWORD", "postgres"),
		env("PG_DB", "godb"), env("PG_PORT", "5432"), env("PG_SSLMODE", "disable"))
	conf := model.DBConf{
		Host: env("PG_HOST", "127.0.0.1"), Port: env("PG_PORT", "5432"),
		User: env("PG_USER", "postgres"), Password: env("PG_PASSWORD", "postgres"),
		DBName: env("PG_DB", "godb"), Mode: env("PG_SSLMODE", "disable"),
	}

	if len(os.Args) > 1 && os.Args[1] == "--emit-seed" {
		return emitSeed(conf, dsn)
	}
	return runMatrix(conf, dsn)
}

// ══════════════════════════════════════════════════════════════════════════
// --emit-seed：用**真源写入路径**造数据 → 导出 3 张表的 INSERT
// ══════════════════════════════════════════════════════════════════════════
func emitSeed(conf model.DBConf, dsn string) error {
	if err := buildGoSchema(conf); err != nil {
		return fmt.Errorf("建 Go 侧 schema: %w", err)
	}
	db, err := gorm.Open(postgres.Open(dsn), &gorm.Config{})
	if err != nil {
		return fmt.Errorf("open: %w", err)
	}
	store := model.NewFavoriteModel(db)
	ctx := context.Background()

	// ⚠️ 用**真源**的写入路径造数据 ⇒ payload 的信封形状由真源决定，不用手写。
	folder := &model.FavoriteFolder{FolderId: probeFolderID, UserId: probeUserID, Name: "seed-folder"}
	if err := store.InsertFolder(ctx, folder); err != nil {
		return fmt.Errorf("InsertFolder: %w", err)
	}
	item := &model.FavoriteItem{
		FavoriteId: probeFavoriteID, FolderId: probeFolderID, UserId: probeUserID,
		TargetId: probeTargetID, TargetType: probeTargetTyp,
		Title: probeTitle, Cover: probeCover,
	}
	if err := store.InsertFavorite(ctx, item); err != nil {
		return fmt.Errorf("InsertFavorite: %w", err)
	}

	// ⚠️ 第二条：**schema=2** 的事实（给 v2 端点用）。
	//    Go 的 v2 入口要求 event.SchemaVersion == 2 ⇒ 用 WithSubjectRefV2Facts 的 model 写。
	storeV2 := model.NewFavoriteModel(db, model.WithSubjectRefV2Facts())
	item2 := &model.FavoriteItem{
		FavoriteId: probeFavoriteID + 1, FolderId: probeFolderID, UserId: probeUserID,
		TargetId: probeTargetID + "-v2", TargetType: probeTargetTyp,
		Title: probeTitle + " v2", Cover: probeCover,
	}
	if err := storeV2.InsertFavorite(ctx, item2); err != nil {
		return fmt.Errorf("InsertFavorite(v2): %w", err)
	}

	// ⚠️⚠️ 关键：**InsertFavorite 写的是 status=0（pending）**，
	//    而权威读要求 status=1（sent）+ delivered_at + technical_receipt_id
	//    + technical_offset ≥ 1 + technical_received_at + **technical_input_hash**
	//    （favorite_authority.go:127-129）。
	//    ⇒ 这里走**真源的投递路径** `DispatchFavoriteFactOnce`（不是手写 UPDATE）：
	//    它锁行、校验信封、提交技术回执的 5 个列 —— 其中
	//    **technical_input_hash 由 DC 端给出**（DC 的算法 = JCS(payload) 的 sha256）。
	//    ⇒ 探针实现一个 FavoriteFactSender stub，用**与真源同一个库**
	//    （jsoncanonicalizer）算 hash ⇒ 真源随后自己用
	//    `favoriteJCSHash(row.Payload)` 复核（favorite_authority.go:160-165）
	//    ⇒ **hash 算错的话真源自己就会拒绝这行**（探针自证，不靠我断言）。
	sender := &probeSender{}
	for i := 0; i < 10; i++ {
		ok, err := store.DispatchFavoriteFactOnce(ctx, sender)
		if err != nil {
			return fmt.Errorf("DispatchFavoriteFactOnce: %w", err)
		}
		if !ok {
			break
		}
	}

	// ── 导出 INSERT（用 PG 自己的 quoting，避免手写转义出错）──────────────
	// ⚠️ **逐表显式列**：不能用 `to_jsonb(row)` 之类 —— jsonb 会**按 key 排序**，
	//    与列清单的 ordinal 顺序不一致 ⇒ 值与列会错位。
	// ⚠️ payload 用 `payload::text`：PG 的 jsonb 会重排键，但真源的
	//    `technical_input_hash` 是 **JCS 规范化**后的 sha256（JCS 幂等且消除键序差异）
	//    ⇒ 重排后的文本算出的 hash 仍与真源写入的一致。
	var seed []string
	for _, spec := range []struct {
		table string
		sql   string
	}{
		{"favorite_folder", `SELECT format(
			'INSERT INTO favorite_folder (id, folder_id, user_id, name, create_time, update_time) VALUES (%L, %L, %L, %L, %L, %L);',
			id, folder_id, user_id, name, create_time, update_time) FROM favorite_folder;`},
		{"favorite_item", `SELECT format(
			'INSERT INTO favorite_item (id, favorite_id, folder_id, user_id, target_id, target_type, target_revision, title, cover, create_time) VALUES (%L, %L, %L, %L, %L, %L, %L, %L, %L, %L);',
			id, favorite_id, folder_id, user_id, target_id, target_type, target_revision, title, cover, create_time) FROM favorite_item;`},
		{"favorite_fact_outbox", `SELECT format(
			'INSERT INTO favorite_fact_outbox (event_id, favorite_id, aggregate_version, payload, status, retry_count, technical_receipt_id, technical_input_hash, technical_offset, technical_received_at, delivered_at, created_at, updated_at) VALUES (%L, %L, %L, %L, %L, %L, %L, %L, %L, %L, %L, %L, %L);',
			event_id, favorite_id, aggregate_version, payload::text, status, retry_count, technical_receipt_id, technical_input_hash, technical_offset, technical_received_at, delivered_at, created_at, updated_at) FROM favorite_fact_outbox ORDER BY event_id;`},
	} {
		var rows []string
		if err := db.Raw(spec.sql).Scan(&rows).Error; err != nil {
			return fmt.Errorf("导出 %s: %w", spec.table, err)
		}
		if len(rows) == 0 {
			return fmt.Errorf("导出 %s 为空（没有行？）", spec.table)
		}
		for _, r := range rows {
			seed = append(seed, r)
		}
	}
	// ⚠️ **写文件而不是打 stdout** —— GORM 默认把 "record not found" 之类的日志写到
	//    **stdout**（不是 stderr）⇒ 混进 stdout 会污染种子文件。
	//    ⇒ stdout 只留诊断信息，结果一律走文件。
	out := env("PROBE_SEED_OUT", "")
	if out == "" {
		return errors.New("缺少 PROBE_SEED_OUT（--emit-seed 模式必须指定输出文件）")
	}
	if err := os.WriteFile(out, []byte(strings.Join(seed, "\n")+"\n"), 0o644); err != nil {
		return fmt.Errorf("写种子文件 %s: %w", out, err)
	}
	fmt.Fprintf(os.Stderr, "[auth-live-probe] 种子已写入 %s（%d 条 INSERT）\n", out, len(seed))
	return nil
}

// buildGoSchema 按**真源文档指定的部署顺序**建 schema（sql-first）：
//
//	AutoMigrate(folder,item)[存量老表] → 001 → 002 → 003 → 004 → AutoMigrate(all)
//
// ⚠️ 与 tools/ddl-parity-probe/favorite 同口径 —— 那边已实测「顺序是行为的一部分」。
func buildGoSchema(conf model.DBConf) error {
	db, err := gorm.Open(postgres.Open(fmt.Sprintf(
		"host=%s user=%s password=%s dbname=%s port=%s sslmode=%s",
		conf.Host, conf.User, conf.Password, conf.DBName, conf.Port, conf.Mode)), &gorm.Config{})
	if err != nil {
		return err
	}
	if err := db.AutoMigrate(&model.FavoriteFolder{}, &model.FavoriteItem{}); err != nil {
		return fmt.Errorf("automigrate(legacy): %w", err)
	}
	if err := runSqlFiles(); err != nil {
		return err
	}
	model.InitDB(conf) // 真源 db.go：失败是 log.Fatalln
	return nil
}

// applySeedFile 应用冻结的种子 —— **逐条执行**（每行一条 INSERT）。
// ⚠️ 不用 simple protocol 整文件送：那会让 pgx 的扩展协议拒绝多语句。
func applySeedFile(path string) error {
	b, err := os.ReadFile(path)
	if err != nil {
		return fmt.Errorf("读种子 %s: %w", path, err)
	}
	db, err := gorm.Open(postgres.New(postgres.Config{
		DSN: fmt.Sprintf("host=%s user=%s password=%s dbname=%s port=%s sslmode=%s",
			env("PG_HOST", "127.0.0.1"), env("PG_USER", "postgres"), env("PG_PASSWORD", "postgres"),
			env("PG_DB", "godb"), env("PG_PORT", "5432"), env("PG_SSLMODE", "disable")),
		PreferSimpleProtocol: true,
	}), &gorm.Config{})
	if err != nil {
		return err
	}
	n := 0
	for _, line := range strings.Split(string(b), "\n") {
		s := strings.TrimSpace(line)
		if s == "" || strings.HasPrefix(s, "--") {
			continue
		}
		if err := db.Exec(s).Error; err != nil {
			return fmt.Errorf("执行种子语句失败：%w\nSQL: %.120s", err, s)
		}
		n++
	}
	fmt.Fprintf(os.Stderr, "[auth-live-probe] 已应用冻结种子 %s（%d 条 INSERT）\n", path, n)
	return nil
}

func runSqlFiles() error {
	dsn := fmt.Sprintf("host=%s user=%s password=%s dbname=%s port=%s sslmode=%s",
		env("PG_HOST", "127.0.0.1"), env("PG_USER", "postgres"), env("PG_PASSWORD", "postgres"),
		env("PG_DB", "godb"), env("PG_PORT", "5432"), env("PG_SSLMODE", "disable"))
	gdb, err := gorm.Open(postgres.New(postgres.Config{
		DSN: dsn, PreferSimpleProtocol: true,
	}), &gorm.Config{})
	if err != nil {
		return err
	}
	// ⚠️ 路径相对于**本文件所在目录**（go/service/favorite/rpc/cmd/probe/）
	//    ⇒ ../../internal/model/*.sql
	base := filepath.Join("..", "..", "internal", "model")
	for _, f := range []string{
		"001_favorite_fact_outbox.sql",
		"002_favorite_fact_delivery.sql",
		"003_favorite_target_revision.sql",
		"004_favorite_legacy_fact_marker.sql",
	} {
		b, err := os.ReadFile(filepath.Join(base, f))
		if err != nil {
			return fmt.Errorf("读 %s: %w", f, err)
		}
		if err := gdb.Exec(string(b)).Error; err != nil {
			return fmt.Errorf("执行 %s: %w", f, err)
		}
	}
	return nil
}

// ══════════════════════════════════════════════════════════════════════════
// 默认模式：跑请求矩阵
// ══════════════════════════════════════════════════════════════════════════
func runMatrix(conf model.DBConf, dsn string) error {
	// ⚠️ 先建 Go 侧 schema（sql-first：AutoMigrate(老表) → 001..004 → AutoMigrate(全)）
	//    ⇒ 与真源文档指定的部署顺序一致（与 tools/ddl-parity-probe/favorite 同口径）。
	if err := buildGoSchema(conf); err != nil {
		return fmt.Errorf("建 Go 侧 schema: %w", err)
	}
	// ⚠️ 再应用**冻结的** seed.sql —— 与 Java 侧**同一份文件** ⇒ 两侧读到同一批行。
	//    （不是每次重新生成：重新生成会带上新的时间戳 ⇒ 两侧数据就不同了。）
	if seedIn := env("PROBE_SEED_IN", ""); seedIn != "" {
		if err := applySeedFile(seedIn); err != nil {
			return err
		}
	}
	db, err := gorm.Open(postgres.Open(dsn), &gorm.Config{})
	if err != nil {
		return fmt.Errorf("open: %w", err)
	}
	store := model.NewFavoriteModel(db)
	handler, err := authority.NewHandler(store, probeToken)
	if err != nil {
		return fmt.Errorf("authority.NewHandler: %w", err)
	}

	cases := buildCases()
	lines := make([]string, 0, len(cases))
	for _, c := range cases {
		req := httptest.NewRequest(c.method, c.target, nil)
		for k, vs := range c.headers {
			for _, v := range vs {
				req.Header.Add(k, v)
			}
		}
		rec := httptest.NewRecorder()
		handler.ServeHTTP(rec, req)
		lines = append(lines, c.id+"\t"+describe(rec))
	}
	// ⚠️ 同上：结果写文件，stdout 只留诊断（GORM 的日志会写 stdout）。
	out := env("PROBE_OUT", "")
	if out == "" {
		return errors.New("缺少 PROBE_OUT（必须指定输出文件）")
	}
	if err := os.WriteFile(out, []byte(strings.Join(lines, "\n")+"\n"), 0o644); err != nil {
		return fmt.Errorf("写结果文件 %s: %w", out, err)
	}
	fmt.Fprintf(os.Stderr, "[auth-live-probe] 矩阵结果已写入 %s（%d 个 case）\n", out, len(lines))
	return nil
}

type probeCase struct {
	id      string
	method  string
	target  string
	headers map[string][]string
}

func buildCases() []probeCase {
	auth := []string{"Bearer " + probeToken}
	v1Path := "/internal/v1/favorite/facts/" + probeProducer + "/" + v1EventID
	v2Path := "/internal/v2/favorite/facts/" + probeProducer + "/" + v2EventID
	return []probeCase{
		// ── 正常路径 ─────────────────────────────────────────────
		{"v1_ok", "GET", v1Path, map[string][]string{"Authorization": auth}},
		{"v2_ok", "GET", v2Path, map[string][]string{"Authorization": auth}},
		// ── 鉴权（Go http.go:34-39：必须恰好 1 个 + Bearer 前缀 + 恒定时间比较）──
		{"auth_missing", "GET", v1Path, nil},
		{"auth_no_bearer_prefix", "GET", v1Path, map[string][]string{"Authorization": {probeToken}}},
		{"auth_wrong_token", "GET", v1Path, map[string][]string{"Authorization": {"Bearer wrong-token-value-000000000000"}}},
		{"auth_empty_after_bearer", "GET", v1Path, map[string][]string{"Authorization": {"Bearer "}}},
		{"auth_duplicate_header", "GET", v1Path, map[string][]string{"Authorization": {"Bearer " + probeToken, "Bearer " + probeToken}}},
		{"auth_wrong_case_prefix", "GET", v1Path, map[string][]string{"Authorization": {"bearer " + probeToken}}},
		// ── mux 层（Go ServeMux：path 清理 / 模式匹配）────────────
		{"mux_path_clean_redirect", "GET", "/internal/v1//favorite/facts/" + probeProducer + "/" + v1EventID, map[string][]string{"Authorization": auth}},
		{"mux_path_short", "GET", "/internal/v1/favorite/facts/" + probeProducer, map[string][]string{"Authorization": auth}},
		{"mux_path_long", "GET", v1Path + "/extra", map[string][]string{"Authorization": auth}},
		{"mux_wrong_prefix", "GET", "/internal/v9/favorite/facts/" + probeProducer + "/" + v1EventID, map[string][]string{"Authorization": auth}},
		{"mux_method_not_allowed", "POST", v1Path, map[string][]string{"Authorization": auth}},
		// ── handler 层（Go http.go:40-43：带 query ⇒ 404）─────────
		{"query_present", "GET", v1Path + "?x=1", map[string][]string{"Authorization": auth}},
		{"query_empty_value", "GET", v1Path + "?x=", map[string][]string{"Authorization": auth}},
		// ── 数据面（Go favorite_authority.go）─────────────────────
		{"event_not_found", "GET", "/internal/v1/favorite/facts/" + probeProducer + "/favorite.999999.v1", map[string][]string{"Authorization": auth}},
		{"producer_mismatch", "GET", "/internal/v1/favorite/facts/rtw.other.producer/" + v1EventID, map[string][]string{"Authorization": auth}},
		{"v1_endpoint_on_v2_schema", "GET", "/internal/v1/favorite/facts/" + probeProducer + "/" + v2EventID, map[string][]string{"Authorization": auth}},
		{"v2_endpoint_on_v1_schema", "GET", "/internal/v2/favorite/facts/" + probeProducer + "/" + v1EventID, map[string][]string{"Authorization": auth}},
		{"event_id_too_long", "GET", "/internal/v1/favorite/facts/" + probeProducer + "/" + strings.Repeat("x", 129), map[string][]string{"Authorization": auth}},
		{"event_id_with_slash", "GET", "/internal/v1/favorite/facts/" + probeProducer + "/a%2Fb", map[string][]string{"Authorization": auth}},
	}
}

// describe 把响应压成**单行、可逐字节比**的描述。
// ⚠️ 选取的 header 是「Go 的 http.Error / http.NotFound / http.Redirect 真实会写」的那些
//    ⇒ 覆盖 Content-Type / nosniff / Location / Allow / Cache-Control。
func describe(rec *httptest.ResponseRecorder) string {
	h := rec.Header()
	body := rec.Body.String()
	parts := []string{
		fmt.Sprintf("%d", rec.Code),
		h.Get("Content-Type"),
		h.Get("Cache-Control"),
		h.Get("Location"),
		h.Get("Allow"),
		h.Get("X-Content-Type-Options"),
		fmt.Sprintf("%q", body),
	}
	return strings.Join(parts, "|")
}

// 保留 import：clause 在 buildGoSchema 之外暂未使用，但真源 model 用到；
// 这里显式引用以免 go vet 报未使用（探针侧不依赖它做逻辑）。
var _ = clause.Locking{}
var _ = http.MethodGet
