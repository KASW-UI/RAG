--
-- V5__create_like_fact_stream.sql
-- 对应 Go 版 service/like/rpc/internal/model/domain_fact.go: LikeFactStream
--
-- 只有两列，但它是**事实流版本号的唯一权威**：appendLikeFact 每次发事实前
--   INSERT INTO like_fact_stream (aggregate_id, version) VALUES (?, 1)
--   ON CONFLICT (aggregate_id) DO UPDATE SET version = like_fact_stream.version + 1
--   RETURNING aggregate_id, version
-- 原子递增并取回新版本。
--
-- ⚠️ 这条 SQL 是 Go 侧 appendLikeFact 手写的裸 SQL，MyBatis-Plus 的任何 Wrapper 都表达不了
--    （RETURNING + ON CONFLICT + 自引用递增），必须用 @Select 注解原生 SQL。
--    见 LikeDomainFactMapper#bumpFactStreamVersion。
--

CREATE TABLE IF NOT EXISTS like_fact_stream (
    aggregate_id  VARCHAR(128) PRIMARY KEY,  -- Go: AggregateID string `column:aggregate_id;primaryKey;type:varchar(128)`
    version       BIGINT        NOT NULL     -- Go: Version int64
);