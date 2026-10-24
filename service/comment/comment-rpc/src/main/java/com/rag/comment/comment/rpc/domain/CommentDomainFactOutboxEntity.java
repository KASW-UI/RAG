package com.rag.comment.comment.rpc.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.rag.comment.comment.rpc.mapper.JsonbTypeHandler;

import java.time.OffsetDateTime;

/**
 * 评论域事实 outbox ORM 实体 —— 对应 Go 端
 * {@code service/comment/rpc/internal/model/domain_fact.go:15-32} 的 {@code CommentDomainFactOutbox}。
 *
 * <p>表结构：{@code V6__create_comment_domain_fact_outbox.sql} + {@code V8__align_…}。
 *
 * <p><b>Go 的类注释就是它的设计契约</b>（domain_fact.go:13-14，逐字引用）：
 * <pre>
 * // CommentDomainFactOutbox is an append-only RTW fact source. Delivery to DC is
 * // deliberately separate from committing the comment's business transaction.
 * </pre>
 * ⇒ 业务事务只负责**冻结并追加**一条事实（含信封与版本号），
 * 投递给 DataCenter 是另一条独立的、可重试的链路。
 * 这条「提交 ≠ 投递」的分离正是 outbox 模式，也是本表<b>不能</b>被缓存 / 批量合并的原因。
 *
 * <p><b>三个 jsonb 列</b>：{@code payload}（业务载荷）、{@code delivery_envelope}（冻结信封）。
 * 都用 {@link JsonbTypeHandler} 按字符串原样存取 ——
 * {@code CommentDeliveryValidator} 要对 payload 列与信封内嵌 payload 做
 * <b>规范化哈希比对</b>（fact_delivery.go:42-47），只有「以字符串原样存取」才能保证
 * 二者来自同一份字节。
 *
 * <p><b>delivery_status 与 retry_count 用 {@code Integer}</b>：
 * Go 的 int32 无 type tag ⇒ GORM 建出的是 {@code integer}（不是 003 脚本字面写的 smallint，
 * 也不是 bigint —— 那段 ALTER 是空操作，详见 V8 的注释）。
 *
 * <p><b>投递元数据（technical_* / delivered_at）可被 UPDATE，信封身份不可被 UPDATE</b>：
 * 由 V8 装的 plpgsql 触发器 {@code comment_fact_identity_immutable} 强制。
 */
@TableName(value = "comment_domain_fact_outbox", autoResultMap = true)
public class CommentDomainFactOutboxEntity {

    /** 对应 Go {@code EventID string `primaryKey;type:varchar(128)`}。 */
    @TableId(type = IdType.INPUT)
    private String eventId;

    /** 对应 Go {@code EventType string `type:varchar(64);not null`}。 */
    private String eventType;

    /** 对应 Go {@code Payload string `type:jsonb;not null`}。 */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String payload;

    /** 对应 Go {@code AggregateID *string `type:varchar(128}` —— 聚合根（= 评论 ID 的字符串形式）。 */
    private String aggregateId;

    /** 对应 Go {@code FactVersion *int64} —— 事务内分配的 DC 版本号。 */
    private Long factVersion;

    /** 对应 Go {@code DeliveryEnvelope *string `type:jsonb`} —— 冻结信封（投递前不可变）。 */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String deliveryEnvelope;

    /** 对应 Go {@code DeliveryStatus int32 `not null;default:0;index`}：0 待投递 1 已接受 2 失败 3 阻塞。 */
    private int deliveryStatus;

    /** 对应 Go {@code RetryCount int32 `not null;default:0`}。 */
    private int retryCount;

    /** 对应 Go {@code TechnicalReceiptID string `type:varchar(128);not null;default:''`}。 */
    private String technicalReceiptId;

    /** 对应 Go {@code TechnicalInputHash string `type:char(64);not null;default:''`}。 */
    private String technicalInputHash;

    /** 对应 Go {@code TechnicalOffset int64 `not null;default:0`}。 */
    private long technicalOffset;

    /** 对应 Go {@code TechnicalReceivedAt *time.Time}。 */
    private OffsetDateTime technicalReceivedAt;

    /** 对应 Go {@code DeliveredAt *time.Time}。 */
    private OffsetDateTime deliveredAt;

    /** 对应 Go {@code CreatedAt time.Time `not null;default:CURRENT_TIMESTAMP`}。 */
    private OffsetDateTime createdAt;

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
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

    public String getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }

    public Long getFactVersion() {
        return factVersion;
    }

    public void setFactVersion(Long factVersion) {
        this.factVersion = factVersion;
    }

    public String getDeliveryEnvelope() {
        return deliveryEnvelope;
    }

    public void setDeliveryEnvelope(String deliveryEnvelope) {
        this.deliveryEnvelope = deliveryEnvelope;
    }

    public int getDeliveryStatus() {
        return deliveryStatus;
    }

    public void setDeliveryStatus(int deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public String getTechnicalReceiptId() {
        return technicalReceiptId;
    }

    public void setTechnicalReceiptId(String technicalReceiptId) {
        this.technicalReceiptId = technicalReceiptId;
    }

    public String getTechnicalInputHash() {
        return technicalInputHash;
    }

    public void setTechnicalInputHash(String technicalInputHash) {
        this.technicalInputHash = technicalInputHash;
    }

    public long getTechnicalOffset() {
        return technicalOffset;
    }

    public void setTechnicalOffset(long technicalOffset) {
        this.technicalOffset = technicalOffset;
    }

    public OffsetDateTime getTechnicalReceivedAt() {
        return technicalReceivedAt;
    }

    public void setTechnicalReceivedAt(OffsetDateTime technicalReceivedAt) {
        this.technicalReceivedAt = technicalReceivedAt;
    }

    public OffsetDateTime getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(OffsetDateTime deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}