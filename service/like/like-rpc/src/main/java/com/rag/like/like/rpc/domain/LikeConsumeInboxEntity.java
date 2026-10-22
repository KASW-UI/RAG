package com.rag.like.like.rpc.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * 消费收件箱 ORM 实体 —— 对应 Go 端
 * {@code service/like/rpc/internal/model/data_model.go: LikeConsumeInbox}。
 *
 * <p>表结构：{@code V2__create_like_consume_inbox.sql}。
 *
 * <p><b>主键是字符串 msg_id</b>（不是自增序列）—— 幂等完全建立在 Kafka 消息的雪花 msg_id 上。
 * 插入走 {@code INSERT ... ON CONFLICT (msg_id) DO NOTHING}，靠 {@code RowsAffected == 0}
 * 判断「这条消息之前来过」，见 {@code LikeMessageIngestService}。
 */
@TableName("like_consume_inbox")
public class LikeConsumeInboxEntity {

    /** 对应 Go {@code MsgId string `primaryKey;type:varchar(64)`}。 */
    @TableId(type = IdType.INPUT)
    private String msgId;

    /**
     * 对应 Go {@code PayloadHash string `varchar(64);not null;default:''`}。
     *
     * <p>语义：同一条 msg_id 若带着<b>不同业务载荷</b>重投，视为消息 ID 被复用，
     * 消费侧直接报错（Go: {@code "like message %s reused with conflicting payload"}）。
     * 由 {@code LikeMessageDigest} 计算。
     */
    private String payloadHash;

    /** 对应 Go {@code Topic string `varchar(64);not null`}。Go 侧固定字面量 {@code "like-topic"}。 */
    private String topic;

    /** 对应 Go {@code Consumer string `varchar(64);not null`}。Go 侧固定字面量 {@code "like_update_service"}。 */
    private String consumer;

    /** 对应 Go {@code Status int32 `smallint;not null;default:0`}：0 处理中、1 已完成。 */
    private int status;

    /** 对应 Go {@code CreatedAt time.Time `autoCreateTime`}。 */
    private OffsetDateTime createdAt;

    /** 对应 Go {@code UpdatedAt time.Time `autoUpdateTime`}。 */
    private OffsetDateTime updatedAt;

    public String getMsgId() {
        return msgId;
    }

    public void setMsgId(String msgId) {
        this.msgId = msgId;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public void setPayloadHash(String payloadHash) {
        this.payloadHash = payloadHash;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getConsumer() {
        return consumer;
    }

    public void setConsumer(String consumer) {
        this.consumer = consumer;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
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