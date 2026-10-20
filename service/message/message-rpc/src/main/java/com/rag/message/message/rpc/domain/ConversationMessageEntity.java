package com.rag.message.message.rpc.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 会话内单条消息表 ORM 实体。
 *
 * <p>对应 Go 端 {@code service/message/rpc/internal/model/data_model.go: ConversationMessage}。
 * <br>表结构：V3__create_message_conversation_message.sql（含 2 个复合索引）。
 */
@TableName("message_conversation_message")
public class ConversationMessageEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 对应 Go {@code ConversationID int64 gorm:"column:conversation_id;not null"}。 */
    private long conversationId;

    /** 对应 Go {@code SenderID int64 gorm:"column:sender_id;not null"}。 */
    private long senderId;

    /** 对应 Go {@code SenderRole int32 gorm:"column:sender_role;not null"}。 */
    private int senderRole;

    /** 对应 Go {@code RecipientID int64 gorm:"column:recipient_id;not null"}。 */
    private long recipientId;

    /** 对应 Go {@code RecipientRole int32 gorm:"column:recipient_role;not null"}。 */
    private int recipientRole;

    /** 对应 Go {@code Content string gorm:"column:content;type:text;not null"}。 */
    private String content;

    /** 对应 Go {@code IsRead bool gorm:"column:is_read;not null;default:false"}。 */
    private boolean isRead;

    /** 对应 Go {@code ReadAt *time.Time gorm:"column:read_at"}。 */
    private LocalDateTime readAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public long getConversationId() { return conversationId; }
    public void setConversationId(long conversationId) { this.conversationId = conversationId; }
    public long getSenderId() { return senderId; }
    public void setSenderId(long senderId) { this.senderId = senderId; }
    public int getSenderRole() { return senderRole; }
    public void setSenderRole(int senderRole) { this.senderRole = senderRole; }
    public long getRecipientId() { return recipientId; }
    public void setRecipientId(long recipientId) { this.recipientId = recipientId; }
    public int getRecipientRole() { return recipientRole; }
    public void setRecipientRole(int recipientRole) { this.recipientRole = recipientRole; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }
    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}