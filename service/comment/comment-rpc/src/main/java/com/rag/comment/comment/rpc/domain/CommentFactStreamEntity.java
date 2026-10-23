package com.rag.comment.comment.rpc.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 评论事实流版本表 ORM 实体 —— 对应 Go 端
 * {@code service/comment/rpc/internal/model/domain_fact.go:36-40} 的 {@code CommentFactStream}。
 *
 * <p>表结构：{@code V7__create_comment_fact_stream.sql}。
 *
 * <p><b>Go 的类注释点明了它是什么</b>（domain_fact.go:34-36，逐字引用）：
 * <pre>
 * // CommentFactStream is the transactionally allocated DC source version for a
 * // comment's fact stream. It is unrelated to a target article revision.
 * </pre>
 * ⇒ 它是「一条评论的事实流」的版本号权威，与目标文章的修订号<b>毫无关系</b>。
 * 混淆这两者是本域最容易犯的概念错误（会导致把两者放进同一个唯一键）。
 *
 * <p><b>它是 appendCommentFact 的原子取号器</b>（domain_fact.go:116-120）：
 * <pre>
 * INSERT INTO comment_fact_stream (aggregate_id, version) VALUES (?, 1)
 * ON CONFLICT (aggregate_id) DO UPDATE SET version = comment_fact_stream.version + 1
 * RETURNING aggregate_id, version
 * </pre>
 * 版本号与 outbox 行的插入<b>同一个事务</b>，因此「提交成功 ⇒ 版本号已分配、事实已冻结」，
 * 不存在「占了号但没发事实」的中间态。
 *
 * <p><b>version 上界 2^53-1</b>：DDL 里的 CHECK 是<b>语义约束不是性能约束</b> ——
 * version 要进 JSON 信封的 {@code aggregate_version}，超出 JSON 安全整数上界会被
 * 下游 JSON 解析器静默丢精度。
 */
@TableName("comment_fact_stream")
public class CommentFactStreamEntity {

    /** 对应 Go {@code AggregateID string `column:aggregate_id;primaryKey;type:varchar(128)`}。 */
    @TableId(type = IdType.INPUT)
    private String aggregateId;

    /** 对应 Go {@code Version int64 `not null`}。 */
    private long version;

    public String getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }
}