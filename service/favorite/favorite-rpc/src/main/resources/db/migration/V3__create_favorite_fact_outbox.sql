--
-- V3__create_favorite_fact_outbox.sql
-- 对应 Go 侧 DDL 事实源之二：001_favorite_fact_outbox.sql（**逐字搬运，不是重设计**）
--   Sea-RideTheWind_golang/service/favorite/rpc/internal/model/001_favorite_fact_outbox.sql:1-15
--
-- ⚠️⚠️ 本文件是「AutoMigrate 覆盖不到的那一半」的起点。Go 侧 db.go:33 的
--   AutoMigrate(&FavoriteFolder{}, &FavoriteItem{}, &FavoriteFactOutbox{})
-- **不会**创建下面两项：
--   ① CHECK (aggregate_version IN (1,2))
--   ② 部分索引 favorite_fact_outbox_pending
-- 两者都只存在于这个手写 SQL 里。漏掉 ⇒ 静默降级（见文件末说明）。
--
-- 本次 V3 只建 001 的内容；002 的 5 个回执列 + 不可变触发器在 V5（保持与 Go 的编号一致）。
CREATE TABLE IF NOT EXISTS favorite_fact_outbox (
    -- Go: EventID string `gorm:"primaryKey;type:varchar(128)"`
    event_id          varchar(128) PRIMARY KEY,
    -- Go: FavoriteID int64 `gorm:"not null;uniqueIndex:uk_favorite_fact_version"`
    favorite_id       bigint      NOT NULL,
    -- Go: AggregateVersion int64 `gorm:"not null;uniqueIndex:uk_favorite_fact_version"`
    aggregate_version bigint      NOT NULL,
    -- Go: Payload string `gorm:"type:jsonb;not null"`
    --   ⚠️ jsonb 而非 json：favorite 从不按 JSON 表达式查这一列，但 DC 的 input_hash
    --   是对**原始字节**做的 JCS+SHA256（favorite_authority.go:179-186），
    --   jsonb 会在 PG 侧规范化存储，读回时已是 PG 的规范化形式 —— 两端都读同一列，所以自洽。
    payload           jsonb       NOT NULL,
    -- Go: Status int32 `gorm:"type:smallint;not null;default:0;index"`
    --   0=pending 1=sent 2=failed 3=blocked（favorite_fact.go:15-20，对齐 Wire.DeliveryStatus）
    status            smallint    NOT NULL DEFAULT 0,
    -- Go: RetryCount int32 `gorm:"not null;default:0"`
    retry_count       integer     NOT NULL DEFAULT 0,
    -- Go: CreatedAt time.Time `gorm:"autoCreateTime;not null"` ← 001 的 DEFAULT now()
    created_at        timestamptz NOT NULL DEFAULT now(),
    -- Go: UpdatedAt time.Time `gorm:"autoUpdateTime;not null"` ← 001 的 DEFAULT now()
    updated_at        timestamptz NOT NULL DEFAULT now(),

    -- 🔴 ① CHECK：aggregate_version 只允许 1（assert）或 2（retract）。
    --    Go 侧逐字：CHECK (aggregate_version IN (1,2))。
    --    语义：FavoriteFactEnvelopeFactory 的前置断言里
    --      (version != 1 && version != 2) ⇒ invalid favorite fact（favorite_fact.go:137）
    --    应用层已经拦了一道；这个 CHECK 是**第二道**，防止任何绕过应用层的写入
    --    （人工 SQL / 未来的第二个写入方）造出 v3 这种无法解释的事实。
    CONSTRAINT favorite_fact_outbox_aggregate_version_range CHECK (aggregate_version IN (1, 2)),

    -- Go 001 的 UNIQUE (favorite_id, aggregate_version)
    -- 🔴 这是「一个收藏聚合的每个版本恰好一个冻结事实」的物理保证，
    --    也是 InsertFavorite 的 23505 会被翻译成 AlreadyExists/5302 的那一个约束。
    CONSTRAINT uk_favorite_fact_version UNIQUE (favorite_id, aggregate_version)
);

-- 🔴 ② 部分索引（Go 001:14-15）：
--      ON favorite_fact_outbox(status,created_at) WHERE status IN (0,2)
--    它精确服务于 DispatchFavoriteFactOnce 的取件查询（favorite_delivery.go:141-147）：
--      WHERE status IN (0,2) AND (aggregate_version=1 OR EXISTS(前驱已 status=1))
--        ORDER BY created_at, event_id ... FOR UPDATE SKIP LOCKED LIMIT 1
--    部分条件把「已接受 / 已阻塞」的行排除在索引外 ⇒ 该查询不会退化成全表扫，
--    且 SKIP LOCKED 在多副本下能各取各的。
CREATE INDEX IF NOT EXISTS favorite_fact_outbox_pending
    ON favorite_fact_outbox (status, created_at)
    WHERE status IN (0, 2);

-- Go struct 的 `index` tag（favorite_fact.go:65）对应的单列索引。
CREATE INDEX IF NOT EXISTS idx_favorite_fact_outbox_status
    ON favorite_fact_outbox (status);