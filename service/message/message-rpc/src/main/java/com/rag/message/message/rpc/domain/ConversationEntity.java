package com.rag.message.message.rpc.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 会话表 ORM 实体（空骨架，仅占位字段）。
 *
 * <p>对应 Go 端 {@code service/message/rpc/internal/model/data_model.go: Conversation}。
 * <br>表结构：V2__create_message_conversation.sql（已搬，含 uk_conversation_pair 联合唯一索引）。
 * <br>Phase 1 补全字段：participant1Role / participant1Id / participant2Role / participant2Id /
 * status / pendingSenderRole / pendingSenderId / createdByRole / createdById /
 * lastMessageId / lastMessagePreview / lastMessageAt / createdAt / updatedAt。
 */
@TableName("message_conversation")
public class ConversationEntity {

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
