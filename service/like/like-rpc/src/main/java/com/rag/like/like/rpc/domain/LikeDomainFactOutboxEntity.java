package com.rag.like.like.rpc.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

/**
 * 领域事实发件箱 ORM 实体 —— 对应 Go 端
 * {@code service/like/rpc/internal/model/domain_fact.go: LikeDomainFactOutbox}。
 *
 * <p>表结构：{@code V4__create_like_domain_fact_outbox.sql}。
 *
 * <p><b>三列组合定义了本表的语义边界</b>：
 * <ul>
 *   <li>{@code payload} — 事实正文，业务侧读它；</li>
 *   <li>{@code delivery_envelope} — <b>冻结信封</b>，投递后不可变；
 *       fact_dispatch 在投递前会逐字段校验它与 payload / 行内元数据一致，
 *       不一致就把该行标成「阻塞」并要求人工迁移（不重试、不丢）；</li>
 *   <li>technical_* 四列 — DataCenter 的技术回执，authority 权威读依赖它们证明「确实送到了」。</li>
 * </ul>
 *
 * <p>两个 JSON 列都是 <b>jsonb</b>（与 {@link LikeOutboxEventEntity#getPayload()} 的 json 不同）。
 */
@TableName("like_domain_fact_outbox")
public class LikeDomainFactOutboxEntity {

    /** 对应 Go {@code EventID string `column:event_id;primaryKey;type:varchar(128)`}。 */
    @TableId(type = IdType.INPUT)
    private String eventId;

    /** 对应 Go {@code EventType string `column:event_type;type:varchar(64);not null`}。 */
    private String eventType;

    /**
     * 对应 Go {@code Payload string `column:payload;type:jsonb;not null`}。
     *
     * <p><b>刻意保持 String，不映射成 POJO</b>：Go 的 {@code validLikeDelivery} 拿
     * {@code canonicalHash(event.Payload)}（信封里内嵌的那段原始字节）与
     * {@code canonicalHash([]byte(row.Payload))}（本列原始字节）<b>逐字比对</b>，
     * 只在「JSON 规范化」之后相等。若这里走 POJO 往返，信封内嵌的 payload 会由 Jackson
     * 重新序列化，字段顺序、null 被省略还是被写成 null 都可能与本列不同 ⇒ 校验必然失败。
     * 按 Go 的做法：存原文，在 {@code LikeDeliveryValidator} 里再 StrictDecode 成 POJO。
     */
    private String payload;

    /**
     * 对应 Go {@code AggregateID *string `column:aggregate_id;type:varchar(128);uniqueIndex:uk_like_fact_stream`}。
     *
     * <p><b>Go 侧写入值是 state 流的 id</b>（{@code like-state/{userId}/{type}/{id}}），
     * 而 <b>payload 里那个 aggregate_id 是 target</b>（{@code {type}/{id}}）—— 两者刻意不同，
     * 见 {@code domain_fact.go:97-100} 的注释。不要以为它们该相等。
     */
    private String aggregateId;

    /** 对应 Go {@code FactVersion *int64 `column:fact_version;uniqueIndex:uk_like_fact_stream`}。 */
    private Long factVersion;

    /**
     * 对应 Go {@code DeliveryEnvelope *string `column:delivery_envelope;type:jsonb`}。冻结信封。
     *
     * <p>同样保持 String，理由见 {@link #payload}。可空：Go 侧只有 appendLikeFact 写入时才非空。
     */
    private String deliveryEnvelope;

    /**
     * 对应 Go {@code DeliveryStatus int32 `column:delivery_status;not null;default:0`}。
     *
     * <p>⚠️ DDL 里是 <b>BIGINT</b>（Go 的 int32 无 type tag ⇒ GORM 取 bigint）。
     * 取值用 {@code Wire.DeliveryStatus} 的 code：0 待投递 / 1 已接受 / 2 失败 / 3 阻塞。
     */
    private int deliveryStatus;

    /** 对应 Go {@code RetryCount int32 `column:retry_count;not null;default:0`}。 */
    private int retryCount;

    /** 对应 Go {@code TechnicalReceiptID string `column:technical_receipt_id;type:varchar(128);not null;default:''`}。 */
    private String technicalReceiptId;

    /** 对应 Go {@code TechnicalInputHash string `column:technical_input_hash;type:char(64);not null;default:''`}。 */
    private String technicalInputHash;

    /** 对应 Go {@code TechnicalOffset int64 `column:technical_offset;not null;default:0`}。 */
    private long technicalOffset;

    /** 对应 Go {@code TechnicalReceivedAt *time.Time `column:technical_received_at`}。 */
    private OffsetDateTime technicalReceivedAt;

    /** 对应 Go {@code DeliveredAt *time.Time `column:delivered_at`}。 */
    private OffsetDateTime deliveredAt;

    /** 对应 Go {@code CreatedAt time.Time `column:created_at;not null;default:CURRENT_TIMESTAMP`}。 */
    private OffsetDateTime createdAt;

    /**
     * 事实正文 —— 对应 Go {@code domain_fact.go:54-74} 的 {@code type likeFact struct}。
     *
     * <p>JSON 字段名<b>逐个</b>对应 Go 的 struct tag，顺序不影响解析但名字必须一致，
     * 因为 {@code fact_delivery.go} 的 {@code validLikeDelivery} 会用
     * {@code StrictDecode}（<b>FAIL_ON_UNKNOWN_PROPERTIES</b>）逐字段比对。
     *
     * <p>{@code aggregateVersion} 在 payload 里<b>恒为 null</b> —— 版本号只存在于信封里，
     * Go 侧从不把它写进 payload，{@code validLikeDelivery} 的
     * {@code payload.AggregateVersion != nil} 就是这条断言。
     */
    @JsonIgnoreProperties(ignoreUnknown = false)
    public static class LikeFactPayload {

        /** {@code "rtw.community-fact.v1"}。 */
        @JsonProperty("schema_version")
        public String schemaVersion;

        /** 形如 {@code rtw.like.{msg_id}}。 */
        @JsonProperty("event_id")
        public String eventId;

        /** {@code "community.target.interaction"}。 */
        @JsonProperty("event_type")
        public String eventType;

        /** {@code "rtw.like-mq"}。 */
        @JsonProperty("producer")
        public String producer;

        /** {@code {target_type}/{target_id}}（注意<b>不是</b>行内 aggregate_id）。 */
        @JsonProperty("aggregate_id")
        public String aggregateId;

        /** 恒为 null —— 见类注释。 */
        @JsonProperty("aggregate_version")
        public Long aggregateVersion;

        /** 消费消息的 msg_id。 */
        @JsonProperty("operation_id")
        public String operationId;

        /** 形如 {@code rtw.identity/platform/{userId}}。 */
        @JsonProperty("subject_ref")
        public String subjectRef;

        @JsonProperty("target_type")
        public String targetType;

        @JsonProperty("target_id")
        public String targetId;

        /** 目标内容版本；Go 侧从不赋值 ⇒ 恒 null。 */
        @JsonProperty("target_revision")
        public String targetRevision;

        /** {@code "unknown"}（targetRevision 为 null 时唯一合法值）。 */
        @JsonProperty("revision_status")
        public String revisionStatus;

        /** {@code like} / {@code unlike} / {@code dislike} / {@code undislike}。 */
        @JsonProperty("operation")
        public String operation;

        /** 与 {@code eventId} 恒相等（Go 侧 {@code SourceRef: eventID}）。 */
        @JsonProperty("source_ref")
        public String sourceRef;

        @JsonProperty("old_state")
        public int oldState;

        @JsonProperty("new_state")
        public int newState;

        /** RFC3339Nano 文本。 */
        @JsonProperty("event_time")
        public String eventTime;

        /** RFC3339Nano 文本。 */
        @JsonProperty("occurred_at")
        public String occurredAt;

        /** RFC3339Nano 文本。 */
        @JsonProperty("available_at")
        public String availableAt;
    }

    /**
     * 冻结信封 —— 对应 Go {@code domain_fact.go:42-52} 的 {@code type likeDeliveryEnvelope struct}。
     *
     * <p>注意 {@code schema_version} 在这里是 <b>int</b>（值 1），而在 payload 里是 string
     * （{@code "rtw.community-fact.v1"}）。这是 Go 的事实，不是笔误。
     */
    @JsonIgnoreProperties(ignoreUnknown = false)
    public static class LikeDeliveryEnvelope {

        @JsonProperty("event_id")
        public String eventId;

        @JsonProperty("event_type")
        public String eventType;

        /** 整数 1。 */
        @JsonProperty("schema_version")
        public int schemaVersion;

        @JsonProperty("producer")
        public String producer;

        /** 行内 aggregate_id（state 流 id）。 */
        @JsonProperty("aggregate_id")
        public String aggregateId;

        /** 行内 fact_version。 */
        @JsonProperty("aggregate_version")
        public long aggregateVersion;

        @JsonProperty("operation_id")
        public String operationId;

        /** RFC3339Nano，与 payload.occurred_at 必须逐字相同。 */
        @JsonProperty("occurred_at")
        public String occurredAt;

        /**
         * 原文，即 payload 列的内容（Go 的 {@code json.RawMessage}）。
         * <p>用 String 而非嵌套 POJO，就是为了让「内嵌的字节」与「payload 列的字节」保持同一份来源。
         */
        @JsonProperty("payload")
        public String payload;
    }

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