--
-- V3__create_like_outbox_event.sql
-- 对应 Go 版 service/like/rpc/internal/model/data_model.go: LikeOutboxEvent
--
-- 「首次点赞」事件的本地事务发件箱：MQ 消费侧在**同一个 DB 事务内**写入本表，
-- 由 like-mq 进程的 outbox relay 每秒扫一次 status IN (0,2) AND retry_count < 3
-- 推进 HotEventPusher（topic=article-hot-events）。
--
-- ⚠️ payload 列类型是 **json**，不是 jsonb。
--    Go tag 是 `type:json`（like_domain_fact_outbox 才是 jsonb），两者不可混用：
--    json 保留输入原文，jsonb 会重排/去重键。虽然 ArticleHotEvent 序列化结果一致，
--    但列类型属于 L1 schema 契约，灰度期两侧读写的是同一张表。
--

CREATE TABLE IF NOT EXISTS like_outbox_event (
    event_id     VARCHAR(64)  PRIMARY KEY,   -- Go: EventID string `primaryKey;type:varchar(64)`
    event_key    VARCHAR(128) NOT NULL,      -- Go: EventKey string `uniqueIndex:uk_event_key`
    event_type   VARCHAR(64)  NOT NULL,      -- Go: EventType string `index`
    aggregate_id VARCHAR(64)  NOT NULL,      -- Go: AggregateID string `index`
    payload      JSON         NOT NULL,      -- Go: Payload string `type:json;not null`
    status       SMALLINT     NOT NULL DEFAULT 0,   -- Go: Status int32 `type:smallint;default:0`（0待发送 1已发送 2发送失败）
    retry_count  INTEGER      NOT NULL DEFAULT 0,   -- Go: RetryCount int32 `type:int;default:0`
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL
);

-- Go: `uniqueIndex:uk_event_key` —— 事件业务幂等键（形如 first_like:{userId}:{type}:{id}）
CREATE UNIQUE INDEX IF NOT EXISTS uk_event_key
    ON like_outbox_event (event_key);

CREATE INDEX IF NOT EXISTS idx_like_outbox_event_event_type
    ON like_outbox_event (event_type);

CREATE INDEX IF NOT EXISTS idx_like_outbox_event_aggregate_id
    ON like_outbox_event (aggregate_id);

CREATE INDEX IF NOT EXISTS idx_like_outbox_event_status
    ON like_outbox_event (status);