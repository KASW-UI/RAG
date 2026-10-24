package com.rag.like.like.rpc.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * 热点事件发件箱 ORM 实体 —— 对应 Go 端
 * {@code service/like/rpc/internal/model/data_model.go: LikeOutboxEvent}。
 *
 * <p>表结构：{@code V3__create_like_outbox_event.sql}。
 *
 * <p><b>与 {@link LikeDomainFactOutboxEntity} 的区别</b>：本表只服务「首次点赞 → 热点排名系统」
 * 这条业务通道（Go: {@code internal/mqs/like_outbox_sender.go}，topic=article-hot-events），
 * 没有版本号、没有冻结信封、没有技术回执；事实流那张表才���。
 *
 * <p>⚠️ payload 列是 {@code json}（不是 jsonb），见 DDL 注释。
 */
@TableName("like_outbox_event")
public class LikeOutboxEventEntity {

    /** 对应 Go {@code EventID string `primaryKey;type:varchar(64)`}。 */
    @TableId(type = IdType.INPUT)
    private String eventId;

    /**
     * 对应 Go {@code EventKey string `uniqueIndex:uk_event_key`}。
     *
     * <p>Go 构造：{@code fmt.Sprintf("first_like:%d:%s:%s", userID, targetType, targetID)}。
     * 唯一索引保证「同一用户对同一目标的首次点赞」只发一次。
     */
    private String eventKey;

    /** 对应 Go {@code EventType string `varchar(64);not null;index`}。Go 侧固定 {@code "article_hot_like"}。 */
    private String eventType;

    /** 对应 Go {@code AggregateID string `varchar(64);not null;index`}。Go 侧取 {@code record.TargetID}。 */
    private String aggregateId;

    /**
     * 对应 Go {@code Payload string `type:json;not null`} —— 已是序列化好的 JSON 文本。
     *
     * <p><b>不</b>用 JsonbTypeHandler：本字段在 Go 侧就是 string（整段 {@code json.Marshal} 结果），
     * 读写都按字符串处理，保持一致。
     */
    private String payload;

    /** 对应 Go {@code Status int32 `smallint;not null;default:0`}：0 待发送 / 1 已发送 / 2 发送失败。 */
    private int status;

    /** 对应 Go {@code RetryCount int32 `type:int;not null;default:0`}。relay 靠 {@code retry_count < 3} 停止重试。 */
    private int retryCount;

    /** 对应 Go {@code CreatedAt time.Time `autoCreateTime`}。relay 的 {@code ORDER BY created_at asc} 用它。 */
    private OffsetDateTime createdAt;

    /** 对应 Go {@code UpdatedAt time.Time `autoUpdateTime`}。 */
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