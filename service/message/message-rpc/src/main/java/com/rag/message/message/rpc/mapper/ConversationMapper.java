package com.rag.message.message.rpc.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rag.message.message.rpc.domain.ConversationEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * message_conversation 表 Mapper 接口（MyBatis-Plus）。
 *
 * <p>对应镜像 Go 端 Conversation 的 CRUD + 状态机查询。
 * <br>uk_conversation_pair 联合唯一索引（p1_role, p1_id, p2_role, p2_id）保证同一对
 * 规范化参与者只能有一个会话。
 *
 * <p>事务方法（{@code createConversationWithMessageTx} / {@code createOrSendMessageTx}）
 * 在 DomainService 上 @Transactional 编排，本接口只暴露"单条 SQL 的原子操作"。
 */
@Mapper
public interface ConversationMapper extends BaseMapper<ConversationEntity> {

    /**
     * 对应 Go 端 findConversationById：SELECT WHERE id=?。
     * 返回 Optional（与 Go 的 sql.ErrNoRows → Optional.empty 行为对齐）。
     */
    default Optional<ConversationEntity> findByIdOpt(long id) {
        return Optional.ofNullable(selectById(id));
    }

    /**
     * 对应 Go 端 findConversationByParticipants：用 uk_conversation_pair 命中规范化 (p1, p2)。
     * 调用方已用 canonicalParticipants 规范化顺序。
     */
    @Select("""
            SELECT * FROM message_conversation
             WHERE participant1_role = #{p1Role}
               AND participant1_id   = #{p1Id}
               AND participant2_role = #{p2Role}
               AND participant2_id   = #{p2Id}
             LIMIT 1
            """)
    Optional<ConversationEntity> findByPair(@Param("p1Role") int p1Role,
                                             @Param("p1Id") long p1Id,
                                             @Param("p2Role") int p2Role,
                                             @Param("p2Id") long p2Id);

    /**
     * 对应 Go 端 ListConversations：分页拉某 operator 作为 p1 OR p2 的会话，按 last_message_at DESC。
     * 用 idx_conversation_p1 / idx_conversation_p2 两侧索引命中。
     */
    default List<ConversationEntity> listByParticipant(int role, long id, int limit, int offset) {
        LambdaQueryWrapper<ConversationEntity> w = new LambdaQueryWrapper<>();
        w.and(q -> q.and(qq -> qq.eq(ConversationEntity::getParticipant1Role, role)
                                .eq(ConversationEntity::getParticipant1Id, id))
                     .or(qq -> qq.eq(ConversationEntity::getParticipant2Role, role)
                                 .eq(ConversationEntity::getParticipant2Id, id)));
        w.orderByDesc(ConversationEntity::getLastMessageAt);
        w.last("LIMIT " + limit + " OFFSET " + offset);
        return selectList(w);
    }

    /**
     * 对应 Go 端 CountUnreadMessagesByConversation：统计某会话中
     * operator 作为 recipient 的未读消息数。用 idx_message_recipient_read 命中。
     *
     * <p>这里实际是聚合 message_conversation_message 表，SQL 跨表；但
     * 接口放在 ConversationMapper 上与 Go 端 data_model.go 的"消息相关"方法归位一致。
     */
    @Select("""
            SELECT COUNT(*) FROM message_conversation_message
             WHERE conversation_id = #{conversationId}
               AND recipient_id    = #{recipientId}
               AND recipient_role  = #{recipientRole}
               AND is_read         = FALSE
            """)
    long countUnreadMessagesByConversation(@Param("conversationId") long conversationId,
                                           @Param("recipientId") long recipientId,
                                           @Param("recipientRole") int recipientRole);

    /**
     * 对应 Go 端 createOrSendMessageTx 内的 conversation UPDATE：
     * 更新 last_message_* + status + pending_sender_*。
     * 用 wrapper + 手写条件 UPDATE。
     */
    default int updateLastMessage(long conversationId, long lastMessageId,
                                   String lastMessagePreview, LocalDateTime lastMessageAt,
                                   int newStatus, int pendingSenderRole, long pendingSenderId) {
        LambdaUpdateWrapper<ConversationEntity> w = new LambdaUpdateWrapper<>();
        w.eq(ConversationEntity::getId, conversationId)
                .set(ConversationEntity::getLastMessageId, lastMessageId)
                .set(ConversationEntity::getLastMessagePreview, lastMessagePreview)
                .set(ConversationEntity::getLastMessageAt, lastMessageAt)
                .set(ConversationEntity::getStatus, newStatus)
                .set(ConversationEntity::getPendingSenderRole, pendingSenderRole)
                .set(ConversationEntity::getPendingSenderId, pendingSenderId)
                .set(ConversationEntity::getUpdatedAt, lastMessageAt);
        return update(null, w);
    }

    /**
     * 对应 Go 端 markConversationReadTx 内的 conversation UPDATE：
     * 仅更新 conversation.updated_at，message 表由 ConversationMessageMapper 在同一事务内批量更新。
     */
    @Update("""
            UPDATE message_conversation
                SET updated_at = #{now}
              WHERE id = #{conversationId}
            """)
    int touchUpdatedAt(@Param("conversationId") long conversationId,
                       @Param("now") LocalDateTime now);
}