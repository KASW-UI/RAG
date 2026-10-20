package com.rag.message.message.rpc.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rag.message.message.rpc.domain.NotificationEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * message_notification 表 Mapper 接口（MyBatis-Plus）。
 *
 * <p>对应镜像 Go 端 {@code service/message/rpc/internal/model/data_model.go} 里
 * Notification 的 CRUD。Human_ONLY 强制：BaseMapper 默认方法（insert/selectById/
 * updateById/deleteById）+ 自定义业务方法签名；事务在 DomainService 上 @Transactional，
 * 不在本接口上。
 */
@Mapper
public interface NotificationMapper extends BaseMapper<NotificationEntity> {

    /**
     * 对应 Go 端 ListNotifications：分页拉某 recipient 的通知列表，按 kind / is_read 过滤。
     *
     * @param recipientId    对应 Go req.UserRecipientId
     * @param role           对应 Go req.UserRole（pb.SenderRole int 形式）；UNSPECIFIED(0) 视为不过滤
     * @param kind           对应 Go req.Kind（pb.NotificationKind int 形式）；0 视为不过滤
     * @param unreadOnly     对应 Go req.UnreadOnly
     * @param limit          LIMIT 子句大小
     * @param offset         OFFSET 子句
     * @return 按 created_at DESC 排序
     */
    default List<NotificationEntity> listByRecipient(long recipientId, int role, int kind,
                                                     boolean unreadOnly, int limit, int offset) {
        LambdaQueryWrapper<NotificationEntity> w = new LambdaQueryWrapper<>();
        w.eq(NotificationEntity::getRecipientId, recipientId);
        if (role > 0) {
            w.eq(NotificationEntity::getSenderRole, role);
        }
        if (kind > 0) {
            w.eq(NotificationEntity::getKind, kind);
        }
        if (unreadOnly) {
            w.eq(NotificationEntity::isRead, false);
        }
        w.orderByDesc(NotificationEntity::getCreatedAt);
        w.last("LIMIT " + limit + " OFFSET " + offset);
        return selectList(w);
    }

    /**
     * 对应 Go 端 CountNotifications(unreadOnly=true)：recipient 未读通知总数。
     * 用 idx_notification_recipient_read 复合索引命中。
     */
    default long countUnreadByRecipient(long recipientId, int role, int kind) {
        LambdaQueryWrapper<NotificationEntity> w = new LambdaQueryWrapper<>();
        w.eq(NotificationEntity::getRecipientId, recipientId);
        if (role > 0) {
            w.eq(NotificationEntity::getSenderRole, role);
        }
        if (kind > 0) {
            w.eq(NotificationEntity::getKind, kind);
        }
        w.eq(NotificationEntity::isRead, false);
        return selectCount(w);
    }

    /**
     * 对应 Go 端 markNotificationReadTx 内的 UPDATE：
     * markRead(id, readAt) WHERE id = ? AND is_read = false。
     * 返回受影响行数（0 / 1）；用于判断"是否曾经未读 → 现在已读"。
     */
    default int markRead(long id, java.time.LocalDateTime readAt) {
        NotificationEntity patch = new NotificationEntity();
        patch.setId(id);
        patch.setRead(true);
        patch.setReadAt(readAt);
        return updateById(patch);
    }

    /**
     * 对应 Go 端 markAllNotificationsReadTx 内的 UPDATE：
     * UPDATE ... SET is_read=true, read_at=? WHERE recipient_id=? AND is_read=false。
     * 返回受影响行数。
     */
    default int markAllReadByRecipient(long recipientId, int role, int kind,
                                       java.time.LocalDateTime readAt) {
        // 用 wrapper + 手写 SQL（MyBatis-Plus 的 updateById 不支持条件 UPDATE）
        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<NotificationEntity> w =
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
        w.eq(NotificationEntity::getRecipientId, recipientId)
                .eq(NotificationEntity::isRead, false);
        if (role > 0) {
            w.eq(NotificationEntity::getSenderRole, role);
        }
        if (kind > 0) {
            w.eq(NotificationEntity::getKind, kind);
        }
        w.set(NotificationEntity::isRead, true);
        w.set(NotificationEntity::getReadAt, readAt);
        return update(null, w);
    }

    /**
     * 对应 Go 端 MessageModel.createNotifications(items []Notification)：
     * 批量 INSERT notification 行；与 markAllNotificationsReadTx 同一事务。
     *
     * <p>用 foreach + 多行 VALUES 把多条 INSERT 合并成一条 SQL（性能优于逐条 insert）。
     */
    @Insert({
            "<script>",
            "INSERT INTO message_notification (",
            "  id, recipient_id, sender_id, sender_role, kind, title, content,",
            "  is_read, read_at, extra, created_at, updated_at",
            ") VALUES ",
            "<foreach collection='items' item='item' separator=','>",
            "  (#{item.id}, #{item.recipientId}, #{item.senderId}, #{item.senderRole},",
            "   #{item.kind}, #{item.title}, #{item.content},",
            "   #{item.isRead}, #{item.readAt},",
            "   #{item.extra,typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},",
            "   #{item.createdAt}, #{item.updatedAt})",
            "</foreach>",
            "</script>"
    })
    int insertBatch(@Param("items") List<NotificationEntity> items);
}