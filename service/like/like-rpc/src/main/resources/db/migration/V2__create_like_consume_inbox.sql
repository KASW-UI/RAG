--
-- V2__create_like_consume_inbox.sql
-- 对应 Go 版 service/like/rpc/internal/model/data_model.go: LikeConsumeInbox
--
-- 这张表是**消费幂等的唯一权威**：MQ 每条消息先在这里落一行 status=0，
-- 整条业务写入（inbox / like_record / domain_fact）成功后才把 status 改成 1。
-- 重复投递时 ProcessLikeMessageBatch 靠 msg_id 主键冲突 + status 判定来决定是跳过还是重放。
--

CREATE TABLE IF NOT EXISTS like_consume_inbox (
    msg_id        VARCHAR(64)  PRIMARY KEY,   -- Go: MsgId string `primaryKey;type:varchar(64)`
    payload_hash  VARCHAR(64)  NOT NULL DEFAULT '',  -- Go: PayloadHash string `type:varchar(64);not null;default:''`
    topic         VARCHAR(64)  NOT NULL,      -- Go: Topic string `type:varchar(64);not null`
    consumer      VARCHAR(64)  NOT NULL,      -- Go: Consumer string `type:varchar(64);not null`
    status        SMALLINT     NOT NULL DEFAULT 0,   -- Go: Status int32 `type:smallint;not null;default:0`（0处理中 1已完成）
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL
);

-- Go: `index:idx_topic_consumer`（Topic 与 Consumer 两列都带 tag ⇒ 复合索引）
CREATE INDEX IF NOT EXISTS idx_topic_consumer
    ON like_consume_inbox (topic, consumer);