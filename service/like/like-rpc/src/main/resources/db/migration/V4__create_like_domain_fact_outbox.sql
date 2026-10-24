--
-- V4__create_like_domain_fact_outbox.sql
-- 对应 Go 版 service/like/rpc/internal/model/domain_fact.go: LikeDomainFactOutbox
--
-- 与 like_outbox_event 的区别（两者不是一回事，别合并）：
--   like_outbox_event      → 给**热点排名系统**推 ArticleHotEvent（业务遗留通道）
--   like_domain_fact_outbox→ 给**DataCenter 事实平台**推 community.target.interaction
--                             （append-only、冻结信封、有版本号、有技术回执）
--
-- ⚠️ payload 是 **jsonb**（Go tag 就是 type:jsonb），与 V3 的 json 不同。
--    appendLikeFact 里用 `payload->>'subject_ref'` 做过查询，jsonb 才有可用的操作符。
--
-- ⚠️ DeliveryStatus / RetryCount 是 int32 且**没有 type tag**：
--    GORM 对「无 size 的整数」统一取 bigint（不是 integer / smallint）。照抄成 smallint
--    会与 Go 侧 AutoMigrate 建的表结构不一致。
--

CREATE TABLE IF NOT EXISTS like_domain_fact_outbox (
    event_id               VARCHAR(128) PRIMARY KEY,  -- Go: EventID string `column:event_id;primaryKey;type:varchar(128)`
    event_type             VARCHAR(64)  NOT NULL,     -- Go: EventType string
    payload                JSONB        NOT NULL,     -- Go: Payload string `type:jsonb;not null`
    aggregate_id           VARCHAR(128),              -- Go: AggregateID *string（可空；非空时与 fact_version 组成唯一键）
    fact_version           BIGINT,                    -- Go: FactVersion *int64
    delivery_envelope      JSONB,                     -- Go: DeliveryEnvelope *string `type:jsonb`（冻结信封，投递前不可变）
    delivery_status        BIGINT       NOT NULL DEFAULT 0,  -- Go: DeliveryStatus int32（无 tag ⇒ bigint）；0待投递 1已接受 2失败 3阻塞
    retry_count            BIGINT       NOT NULL DEFAULT 0,  -- Go: RetryCount int32（无 tag ⇒ bigint）
    technical_receipt_id   VARCHAR(128) NOT NULL DEFAULT '',  -- Go: TechnicalReceiptID string `type:varchar(128);not null;default:''`
    technical_input_hash   CHAR(64)     NOT NULL DEFAULT '',  -- Go: TechnicalInputHash string `type:char(64);not null;default:''`
    technical_offset       BIGINT       NOT NULL DEFAULT 0,   -- Go: TechnicalOffset int64
    technical_received_at  TIMESTAMPTZ,               -- Go: TechnicalReceivedAt *time.Time
    delivered_at           TIMESTAMPTZ,               -- Go: DeliveredAt *time.Time
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Go: `uniqueIndex:uk_like_fact_stream`（AggregateID 与 FactVersion 两列都带 tag ⇒ 复合唯一索引）
-- 语义作用：同一「用户-目标」状态流内，版本号必须唯一；
-- appendLikeFact 用 INSERT..ON CONFLICT DO UPDATE SET version = version + 1 RETURNING
-- 原子取号，依赖的就是这个唯一键做冲突仲裁。
CREATE UNIQUE INDEX IF NOT EXISTS uk_like_fact_stream
    ON like_domain_fact_outbox (aggregate_id, fact_version);

-- Go: `index` on DeliveryStatus
-- 覆盖 fact_dispatch 的 `WHERE delivery_status IN (0,2) AND delivery_envelope IS NOT NULL ... FOR UPDATE SKIP LOCKED`
CREATE INDEX IF NOT EXISTS idx_like_domain_fact_outbox_delivery_status
    ON like_domain_fact_outbox (delivery_status);