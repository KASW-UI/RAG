package com.rag.message.message.rpc.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rag.message.message.rpc.domain.ConversationMessageEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Insert;

import java.time.LocalDateTime;
import java.util.List;

/**
 * message_conversation_message 表 Mapper 接口（MyBatis-Plus）。
 *
 * <p>对应镜像 Go 端 ConversationMessage 的 CRUD + 单条消息读已读状态。
 * <br>idx_message_conversation_time（conversation_id, created_at）覆盖
 * GetConversationMessages 的 ORDER BY created_at。
 */
@Mapper
public interface ConversationMessageMapper extends BaseMapper<ConversationMessageEntity> {

    /**
     * 对应 Go 端 GetConversationMessages：按 conversation_id 拉历史消息，
     * {@code beforeMessageId > 0} 时再加 "id < ?" 条件做 cursor 分页。
     */
    default List<ConversationMessageEntity> listByConversation(long conversationId,
                                                              long beforeMessageId,
                                                              int limit) {
        LambdaQueryWrapper<ConversationMessageEntity> w = new LambdaQueryWrapper<>();
        w.eq(ConversationMessageEntity::getConversationId, conversationId);
        if (beforeMessageId > 0L) {
            w.lt(ConversationMessageEntity::getId, beforeMessageId);
        }
        w.orderByAsc(ConversationMessageEntity::getId);
        w.last("LIMIT " + limit);
        return selectList(w);
    }

    /**
     * 对应 Go 端 GetUnreadSummary 内的 "unread_messages count"：operator 作为 recipient
     * 的所有 conversation_message 的未读总数。
     */
    @Select("""
            SELECT COUNT(*) FROM message_conversation_message
             WHERE recipient_id   = #{recipientId}
               AND recipient_role = #{recipientRole}
               AND is_read        = FALSE
            """)
    long countUnreadByRecipient(@Param("recipientId") long recipientId,
                               @Param("recipientRole") int recipientRole);

    /**
     * 对应 Go 端 markConversationReadTx 内的 UPDATE：
     * UPDATE message_conversation_message SET is_read=true, read_at=?
     *  WHERE conversation_id = ? AND recipient_id = ? AND recipient_role = ? AND is_read = false
     */
    @Update("""
            UPDATE message_conversation_message
                SET is_read = TRUE, read_at = #{readAt}
              WHERE conversation_id = #{conversationId}
                AND recipient_id    = #{recipientId}
                AND recipient_role  = #{recipientRole}
                AND is_read         = FALSE
            """)
    int markReadByConversationAndRecipient(@Param("conversationId") long conversationId,
                                            @Param("recipientId") long recipientId,
                                            @Param("recipientRole") int recipientRole,
                                            @Param("readAt") LocalDateTime readAt);

    /**
     * 对应 Go 端 markAllConversationsReadTx 内的 UPDATE：
     * UPDATE ... SET is_read=true, read_at=? WHERE recipient_id=? AND recipient_role=? AND is_read=false。
     */
    default int markAllReadByRecipient(long recipientId, int recipientRole, LocalDateTime readAt) {
        LambdaUpdateWrapper<ConversationMessageEntity> w = new LambdaUpdateWrapper<>();
        w.eq(ConversationMessageEntity::getRecipientId, recipientId)
                .eq(ConversationMessageEntity::getRecipientRole, recipientRole)
                .eq(ConversationMessageEntity::isRead, false)
                .set(ConversationMessageEntity::isRead, true)
                .set(ConversationMessageEntity::getReadAt, readAt);
        return update(null, w);
    }

    /**
     * 对应 Go 端 ListConversations 的 unreadTotal：operator 作为 recipient 的所有未读消息数。
     */
    @Select("""
            SELECT COUNT(*) FROM message_conversation_message
             WHERE recipient_id   = #{recipientId}
               AND recipient_role = #{recipientRole}
               AND is_read        = FALSE
            """)
    long countUnreadByRecipientGlobal(@Param("recipientId") long recipientId,
                                      @Param("recipientRole") int recipientRole);

    /**
     * 对应 Go 端 GetConversationMessages 用 offset + limit 分页：
     * 与 {@link #listByConversation} 不同在于这是 "用 offset 不带 cursor"。
     *
     * <p><b>⚠️ DRIFT-MSG-V-004（2026-10-22 修）：排序键必须是 {@code created_at}，不是 {@code id}</b>
     * <pre>
     * Go   message.go:159-174
     *      db := Model(&amp;ConversationMessage{}).Where("conversation_id = ?", conversationID).
     *           Order("created_at asc")                       ← created_at
     * RAG  修复前
     *      w.orderByAsc(ConversationMessageEntity::getId)     ← 错用了 id
     * </pre>
     * 两者<b>看起来等价</b>（id 是雪花、时间单调），实际不等价：雪花 id 在<b>同一毫秒内</b>
     * 按 (机器号, 序列号) 排序，与 {@code created_at} 的并列顺序不必一致。
     * ⇒ {@code created_at} 并列时两侧返回顺序不同，offset 分页的翻页结果也不同。
     *
     * <p><b>一个交叉证据</b>：DDL {@code V3__create_message_conversation_message.sql:20-23}
     * 建的是 {@code idx_message_conversation_time (conversation_id, created_at)}，
     * 注释写着「用于 GetConversationMessages 的 ORDER BY created_at ASC」——
     * <b>索引与注释都指向 created_at，只有代码用了 id</b>。这本身就是漂移的可查痕迹。
     *
     * <p><b>刻意不加 id 作为 tie-breaker</b>：加了确实能让分页更稳（Go 侧
     * {@code created_at} 并列时顺序不确定，offset 分页会漏/重），
     * 但那是<b>新增 Go 不存在的行为</b>，属于 AGENTS.md §3.2 禁止的「顺手改好」。
     * Go 的不确定性照原样保留，等 Go 侧先改再对齐。
     */
    default List<ConversationMessageEntity> listByConversationPage(long conversationId,
                                                                   int offset, int limit) {
        LambdaQueryWrapper<ConversationMessageEntity> w = new LambdaQueryWrapper<>();
        w.eq(ConversationMessageEntity::getConversationId, conversationId);
        w.orderByAsc(ConversationMessageEntity::getCreatedAt);
        w.last("LIMIT " + limit + " OFFSET " + offset);
        return selectList(w);
    }

    /**
     * 对应 Go 端 createConversationWithMessageTx / createOrSendMessageTx 的 message INSERT。
     */
    @Insert("""
            INSERT INTO message_conversation_message (
              id, conversation_id, sender_id, sender_role, recipient_id, recipient_role,
              content, is_read, read_at, created_at
            ) VALUES (
              #{id}, #{conversationId}, #{senderId}, #{senderRole}, #{recipientId}, #{recipientRole},
              #{content}, #{isRead}, #{readAt}, #{createdAt}
            )
            """)
    int insertMsg(com.rag.message.message.rpc.domain.ConversationMessageEntity msg);
}