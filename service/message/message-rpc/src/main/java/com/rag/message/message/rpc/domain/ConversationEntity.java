package com.rag.message.message.rpc.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * 会话表 ORM 实体。
 *
 * <p>对应 Go 端 {@code service/message/rpc/internal/model/data_model.go: Conversation}。
 * <br>表结构：V2__create_message_conversation.sql（含 uk_conversation_pair 联合唯一索引 +
 * 4 个普通索引）。
 *
 * <p>字段命名 1:1；主键雪花；createdAt / updatedAt 由 MyBatis-Plus FieldFill 维护。
 */
@TableName("message_conversation")
public class ConversationEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 对应 Go {@code Participant1Role int32 gorm:"column:participant1_role;not null"}。 */
    private int participant1Role;

    /** 对应 Go {@code Participant1ID int64 gorm:"column:participant1_id;not null"}。 */
    private long participant1Id;

    /** 对应 Go {@code Participant2Role int32 gorm:"column:participant2_role;not null"}。 */
    private int participant2Role;

    /** 对应 Go {@code Participant2ID int64 gorm:"column:participant2_id;not null"}。 */
    private long participant2Id;

    /** 对应 Go {@code Status int32 gorm:"column:status;not null;default:1"}（pb.ConversationStatus）。 */
    private int status;

    /** 对应 Go {@code PendingSenderRole int32 gorm:"column:pending_sender_role;not null;default:0"}。 */
    private int pendingSenderRole;

    /** 对应 Go {@code PendingSenderID int64 gorm:"column:pending_sender_id;not null;default:0"}。 */
    private long pendingSenderId;

    /** 对应 Go {@code CreatedByRole int32 gorm:"column:created_by_role;not null"}。 */
    private int createdByRole;

    /** 对应 Go {@code CreatedByID int64 gorm:"column:created_by_id;not null"}。 */
    private long createdById;

    /** 对应 Go {@code LastMessageID int64 gorm:"column:last_message_id;not null;default:0"}。 */
    private long lastMessageId;

    /**
     * 对应 Go {@code LastMessagePreview string gorm:"column:last_message_preview;type:varchar(255);not null;default:''"}。
     */
    private String lastMessagePreview = "";

    /** 对应 Go {@code LastMessageAt time.Time gorm:"column:last_message_at;not null;index"}。 */
    private OffsetDateTime lastMessageAt;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public int getParticipant1Role() { return participant1Role; }
    public void setParticipant1Role(int participant1Role) { this.participant1Role = participant1Role; }
    public long getParticipant1Id() { return participant1Id; }
    public void setParticipant1Id(long participant1Id) { this.participant1Id = participant1Id; }
    public int getParticipant2Role() { return participant2Role; }
    public void setParticipant2Role(int participant2Role) { this.participant2Role = participant2Role; }
    public long getParticipant2Id() { return participant2Id; }
    public void setParticipant2Id(long participant2Id) { this.participant2Id = participant2Id; }
    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }
    public int getPendingSenderRole() { return pendingSenderRole; }
    public void setPendingSenderRole(int pendingSenderRole) { this.pendingSenderRole = pendingSenderRole; }
    public long getPendingSenderId() { return pendingSenderId; }
    public void setPendingSenderId(long pendingSenderId) { this.pendingSenderId = pendingSenderId; }
    public int getCreatedByRole() { return createdByRole; }
    public void setCreatedByRole(int createdByRole) { this.createdByRole = createdByRole; }
    public long getCreatedById() { return createdById; }
    public void setCreatedById(long createdById) { this.createdById = createdById; }
    public long getLastMessageId() { return lastMessageId; }
    public void setLastMessageId(long lastMessageId) { this.lastMessageId = lastMessageId; }
    public String getLastMessagePreview() { return lastMessagePreview; }
    public void setLastMessagePreview(String lastMessagePreview) { this.lastMessagePreview = lastMessagePreview; }
    public OffsetDateTime getLastMessageAt() { return lastMessageAt; }
    public void setLastMessageAt(OffsetDateTime lastMessageAt) { this.lastMessageAt = lastMessageAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}