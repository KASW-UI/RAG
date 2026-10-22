package com.rag.article.article.rpc.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * 文章同步发件箱 ORM 实体 —— 对应 Go 端
 * {@code service/article/rpc/internal/model/article_sync_outbox.go: ArticleSyncOutboxEvent}。
 *
 * <p>表结构：{@code V9__create_article_sync_outbox.sql}。
 *
 * <p>⚠️ {@link #payload} 是<b>整段冻结的 JSON 文本</b>，不是结构化对象。
 * 原因：relay 推送的是 {@code event.Payload} <b>原文</b>
 * （{@code article_sync_outbox_sender.go:30}），与审核消费者写库时序列化的那一份<b>逐字节相同</b>。
 * 若这里改成结构化字段并在推送时重新序列化，key 顺序 / 空串 vs 缺省 的差异会让
 * 下游 {@code handleUpsertResult} 的「冻结输入比对」失败。
 * 锁死用例：{@code ArticleSyncEventFreezeTest}。
 */
@TableName("article_sync_outbox")
public class ArticleSyncOutboxEntity {

    /** 对应 Go {@code EventID string `gorm:"primaryKey;type:varchar(64)"`}（雪花 ID 的十进制字符串）。 */
    @TableId(type = IdType.INPUT)
    private String eventId;

    /**
     * 对应 Go {@code EventKey string `gorm:"...;uniqueIndex:uk_article_sync_outbox_event_key"`}。
     *
     * <p>Go 侧 {@code ArticleSyncEventKey(articleID, op, eventID)} 拼成
     * {@code "<articleID>:<op>:<eventID>"}（{@code article_sync_types.go:90-92}，
     * 三段各自 {@code strings.TrimSpace}）。唯一索引阻止同一逻辑事件重复入队。
     */
    private String eventKey;

    /** 对应 Go {@code EventType string `gorm:"...;index"`}。Go 侧固定 {@code "article_sync"}。 */
    private String eventType;

    /** 对应 Go {@code AggregateID string `gorm:"...;index"`}。也是 Kafka 消息 key。 */
    private String aggregateId;

    /** 冻结的 {@code ArticleSyncEvent} JSON 文本（见类级说明）。 */
    private String payload;

    /** 对应 Go {@code Status int32 `gorm:"smallint;not null;default:0;index"`}：0 待发 / 1 已发 / 2 失败。 */
    private int status;

    /** 对应 Go {@code RetryCount int32 `gorm:"type:int;not null;default:0"`}。relay 靠 {@code < maxRetry} 停止重试。 */
    private int retryCount;

    /** 对应 Go {@code LastError string `gorm:"type:text"`}。 */
    private String lastError;

    /** 对应 Go {@code CreatedAt time.Time `gorm:"autoCreateTime;not null"`}。relay 的 {@code ORDER BY created_at asc} 用它。 */
    private OffsetDateTime createdAt;

    /** 对应 Go {@code UpdatedAt time.Time `gorm:"autoUpdateTime;not null"`}。 */
    private OffsetDateTime updatedAt;

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventKey() {
        return eventKey;
    }

    public void setEventKey(String eventKey) {
        this.eventKey = eventKey;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
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

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
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