package com.rag.article.article.rpc.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * H03 社区域事件发件箱 ORM 实体 —— 对应 Go 端
 * {@code service/article/rpc/internal/model/community_revision.go: ArticleDomainOutbox}。
 *
 * <p>表结构：{@code V10__create_article_community_revision.sql}。
 *
 * <h2>⚠️ 与 {@link ArticleSyncOutboxEntity} 是两张不同的表，作用不同</h2>
 * <table border="1">
 *   <caption>两张 outbox 的区别</caption>
 *   <tr><th></th><th>article_sync_outbox</th><th>article_domain_outbox</th></tr>
 *   <tr><td>消费者</td><td>本进程的 relay（推 Kafka）</td><td><b>外部 H03 投递器</b></td></tr>
 *   <tr><td>事件</td><td>ArticleSyncEvent（同步给推荐/检索）</td><td>community.article.{published,retracted}</td></tr>
 *   <tr><td>唯一键</td><td>event_key（业务三元组）</td><td>(aggregate_id, aggregate_version)</td></tr>
 *   <tr><td>本进程是否投递</td><td><b>是</b>（ArticleSyncOutboxRelay）</td><td><b>否</b>（只写不发）</td></tr>
 * </table>
 * ⚠️ 最后一行是 Go 侧的既有事实：{@code article-rpc} 只写不投递 domain 事件，
 * 全仓没有 dispatcher 进程（grep {@code article_domain_outbox} 只命中写入与查询）。
 * 本实现<b>照搬这个事实</b>，不擅自补一个投递器 —— 那属于新增行为，
 * 且投递协议（HTTP 端点 / 鉴权 / 重试）不在本组件的契约内。留待独立组件处理。
 *
 * <h2>L2：写入必须与业务状态变更同事务</h2>
 * Go 测试 {@code TestArticleDomainOutboxFailureRollsBackPublication}
 * （{@code community_article_test.go:248-275}）用触发器注入失败，断言
 * revision / pointer / article.status <b>三者全部回滚</b>。
 * ⇒ 「域事件写失败」不能被吞掉，必须让整个发布事务失败。
 */
@TableName("article_domain_outbox")
public class ArticleDomainOutboxEntity {

    /**
     * 对应 Go {@code EventID string `gorm:"primaryKey;type:varchar(128)"`}。
     *
     * <p>格式：{@code fmt.Sprintf("community.article.%s.%s", syncEventID, operation)}
     * （{@code community_article_event.go:65}），operation ∈ {published, retracted}。
     */
    @TableId(type = IdType.INPUT)
    private String eventId;

    /** 对应 Go {@code AggregateID string `gorm:"...;uniqueIndex:uk_article_domain_version"`}。 */
    private String aggregateId;

    /** 对应 Go {@code AggregateVersion int64 `gorm:"...;uniqueIndex:uk_article_domain_version"`}：即 pointer_version。 */
    private long aggregateVersion;

    /**
     * 对应 Go {@code EventType string `gorm:"type:varchar(64);not null"`}。
     *
     * <p>取值：{@code "community.article.published"} / {@code "community.article.retracted"}
     * （Go {@code community_article_event.go:91}）。
     */
    private String eventType;

    /** 冻结的 community 信封 JSON（{@code communityArticleEnvelope} 全量）。 */
    private String payload;

    /** 对应 Go {@code Status int32 `gorm:"smallint;not null;default:0;index"`}：0 待投递 / 1 已投递 / 2 失败。 */
    private int status;

    /** 对应 Go {@code RetryCount int32 `gorm:"not null;default:0"`}。 */
    private int retryCount;

    /** 对应 Go {@code CreatedAt time.Time `gorm:"autoCreateTime;not null"`}。 */
    private OffsetDateTime createdAt;

    /** 对应 Go {@code UpdatedAt time.Time `gorm:"autoUpdateTime;not null"`}。 */
    private OffsetDateTime updatedAt;

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }

    public long getAggregateVersion() {
        return aggregateVersion;
    }

    public void setAggregateVersion(long aggregateVersion) {
        this.aggregateVersion = aggregateVersion;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}