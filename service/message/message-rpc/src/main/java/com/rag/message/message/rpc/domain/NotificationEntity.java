package com.rag.message.message.rpc.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 通知表 ORM 实体（空骨架，仅占位字段）。
 *
 * <p>对应 Go 端 {@code service/message/rpc/internal/model/data_model.go: Notification}。
 * <br>表结构：V1__create_message_notification.sql（已搬）。
 * <br>Phase 1 补全字段：recipientId / senderId / senderRole / kind / title / content /
 * isRead / readAt / extra / createdAt / updatedAt。
 */
@TableName("message_notification")
public class NotificationEntity {

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
