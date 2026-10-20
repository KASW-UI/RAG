package com.rag.message.message.rpc.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 会话内单条消息表 ORM 实体（空骨架，仅占位字段）。
 *
 * <p>对应 Go 端 {@code service/message/rpc/internal/model/data_model.go: ConversationMessage}。
 * <br>表结构：V3__create_message_conversation_message.sql（已搬）。
 * <br>Phase 1 补全字段：conversationId / senderId / senderRole / recipientId / recipientRole /
 * content / isRead / readAt / createdAt。
 */
@TableName("message_conversation_message")
public class ConversationMessageEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    // Phase 1 补全字段

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
