package com.rag.message.message.rpc.service;

import com.rag.message.message.rpc.domain.ConversationEntity;
import com.rag.message.message.rpc.domain.ConversationMessageEntity;
import com.rag.message.message.rpc.domain.NotificationEntity;
import com.rag.message.message.rpc.mapper.ConversationMapper;
import com.rag.message.message.rpc.mapper.ConversationMessageMapper;
import com.rag.message.message.rpc.mapper.NotificationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * message-rpc 的多步写事务 —— <b>刻意独立成 Bean</b>。
 *
 * <p><b>为什么必须独立成 Bean</b>（2026-10-22 修，DRIFT-MSG-001，AGENTS.md §6.6.1）：
 * Spring 的 {@code @Transactional} 靠 AOP 代理生效，而代理<b>只拦截从容器外部进来的调用</b>。
 * 修复前这 6 个方法全部挂在 {@link MessageDomainService} 本类上，且<b>全部由同类的私有
 * {@code doXxx(...)} 通过 {@code this.} 调用</b>（self-invocation）——
 * 调用不经过代理，{@code @Transactional} <b>静默失效</b>：代码看着有事务，运行时是
 * auto-commit，异常不回滚，<b>不报任何错</b>。
 *
 * <p>本仓库已有同款教训：{@code service/users/admin/admin-rpc} 的
 * {@code AdminCreateAtomicService} 正是为修这个问题而抽出的独立 Bean，
 * {@code AdminUpdateSelfAndCreateAdminRegressionTest:169} 还留着防回退断言。
 * message-rpc 此前没有做这件事，直接复现了 AGENTS.md §1.1 列出的 11 处回归中的第一处。
 *
 * <p><b>与 Go 的对应关系</b>：Go 侧是 {@code model.MessageModel} 上显式的
 * {@code m.conn.Transaction(func(tx *gorm.DB) error { ... })} 闭包
 * （{@code internal/model/message.go:186} / {@code :196}），由 logic 跨对象调用。
 * 本类是它的 Spring 等价物。
 *
 * <p><b>⚠️ 事务内的语句顺序严格对齐 Go，不是随便排的</b>：
 * <ul>
 *   <li>{@link #createConversationWithMessageTx}：插 conversation → 写回
 *       {@code message.conversationId} → 插 message（Go {@code message.go:186-194}）</li>
 *   <li>{@link #createOrSendMessageTx}：<b>先</b>更新 conversation 的 7 个字段 →
 *       写回 {@code message.conversationId} → 插 message（Go {@code message.go:216-229}）。
 *       Go 是「先 UPDATE 后 INSERT」，修复前的 Java 是「先 INSERT 后 UPDATE」。
 *       在同一事务里最终状态一致，但<b>加锁顺序相反</b> ⇒ 并发追加消息时锁获取顺序相反，
 *       是潜在的死锁来源。已对齐。</li>
 * </ul>
 */
@Service
public class MessageAtomicService {

    /**
     * 批量插入的每片行数 —— 对应 Go {@code CreateInBatches(items, 200)} 的字面量
     * {@code 200}（{@code internal/model/message.go:26}）。
     *
     * <p><b>为什么必须是常量而不是配置项</b>：Go 侧是硬编码字面量，
     * 没有对应的 yaml 字段。把它做成配置等于<b>新增 Go 不存在的开关</b>
     * （AGENTS.md §2「不新增 Go 侧不存在的开关」）。
     *
     * <p><b>为什么不能改大</b>：本 INSERT 12 列/行，Postgres 单条 SQL 绑定参数上限
     * 65535 ⇒ 单片行数必须 {@code ≤ 5461}。取 200 是照抄 Go，同时留足余量。
     */
    static final int INSERT_BATCH_SIZE = 200;

    private final NotificationMapper notificationMapper;
    private final ConversationMapper conversationMapper;
    private final ConversationMessageMapper messageMapper;

    public MessageAtomicService(NotificationMapper notificationMapper,
                                ConversationMapper conversationMapper,
                                ConversationMessageMapper messageMapper) {
        this.notificationMapper = notificationMapper;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
    }

    // ==================================================================
    // 1. createConversationWithMessageTx
    // ==================================================================

    /**
     * 新建会话 + 首条消息，<b>一个事务</b>。
     *
     * <p><b>Go 出处逐字</b>（{@code internal/model/message.go:186-194}）：
     * <pre>
     * func (m *MessageModel) CreateConversationWithMessageTx(ctx, conversation, message) error {
     *     return m.conn.WithContext(ctx).Transaction(func(tx *gorm.DB) error {
     *         if err := tx.Create(conversation).Error; err != nil { return err }
     *         message.ConversationID = conversation.ID     // ← 事务内写回
     *         return tx.Create(message).Error
     *     })
     * }
     * </pre>
     *
     * <p><b>DRIFT-MSG-002 的修复点就在下面那一行 {@code msg.setConversationId}</b>：
     * 修复前 {@code doSendChatMessage} 构造 {@code messageItem} 时设了 id / senderId /
     * senderRole / recipientId / recipientRole / content / read / createdAt，
     * <b>唯独漏了 conversationId</b>；而实体里它是 primitive {@code long}（默认 0），
     * {@code insertMsg} 直接绑 {@code #{conversationId}} ⇒ <b>所有消息都写成
     * conversation_id = 0</b> ⇒ 查不出历史消息、unread_count 恒 0、标记已读更新 0 行。
     * 全仓 5 处 {@code setConversationId} 命中<b>全是 protobuf builder</b>，掩盖了这个缺口。
     *
     * <p>把赋值放在事务内而不是构造点，是为了与 Go 逐行对齐：Go 也是在闭包里写回的。
     */
    @Transactional
    public void createConversationWithMessageTx(ConversationEntity conv,
                                                ConversationMessageEntity msg) {
        conversationMapper.insertConv(conv);
        msg.setConversationId(conv.getId());
        messageMapper.insertMsg(msg);
    }

    // ==================================================================
    // 2. createOrSendMessageTx
    // ==================================================================

    /**
     * 已有会话内 append 一条消息 + 更新会话 last_message_* / status / pending_sender_*，<b>一个事务</b>。
     *
     * <p><b>Go 出处逐字</b>（{@code internal/model/message.go:196-231}）：
     * <pre>
     * tx.Model(&amp;Conversation{}).Where("id = ?", conversation.ID).Updates(map[string]any{
     *     "status": statusAfterSend, "pending_sender_role": …, "pending_sender_id": …,
     *     "last_message_id": message.ID, "last_message_preview": preview,
     *     "last_message_at": now, "updated_at": now,
     * })                                          // ← 先 UPDATE 会话
     * message.ConversationID = conversation.ID      // ← 事务内写回
     * return tx.Create(message).Error               // ← 再 INSERT 消息
     * </pre>
     *
     * <p>7 个更新字段与 Go 的 map 键逐条一致。
     */
    @Transactional
    public void createOrSendMessageTx(ConversationEntity conv,
                                      ConversationMessageEntity msg,
                                      int newStatus, int pendingSenderRole, long pendingSenderId,
                                      String lastMessagePreview) {
        conversationMapper.updateLastMessage(
                conv.getId(), msg.getId(), lastMessagePreview, msg.getCreatedAt(),
                newStatus, pendingSenderRole, pendingSenderId);
        msg.setConversationId(conv.getId());
        messageMapper.insertMsg(msg);
    }

    // ==================================================================
    // 3. insertNotificationsTx
    // ==================================================================

    /**
     * 批量插入通知行，**一个事务**。
     *
     * <p><b>Go 出处</b>：{@code CreateNotifications}（{@code internal/model/message.go:22-27}）：
     * <pre>
     * func (m *MessageModel) CreateNotifications(ctx context.Context, items []*Notification) error {
     *     if len(items) == 0 { return nil }
     *     return m.conn.WithContext(ctx).CreateInBatches(items, 200).Error
     * }
     * </pre>
     * GORM 的 {@code CreateInBatches} 自带事务，所以 Go 侧虽没显式 {@code Transaction}，
     * 批量写同样是原子的（要么全成功要么全回滚）。
     *
     * <p><b>⚠️ DRIFT-MSG-V-002（2026-10-22 修）：为什么必须分批，不能一条 foreach 插到底</b>
     * <pre>
     * 修复前：@Insert("&lt;foreach ...&gt;") 一条多行 INSERT，12 列/行，一次绑全部
     *         recipients × 12 个参数
     * 现在  ：按 INSERT_BATCH_SIZE 切片，逐片调 insertBatch，全部仍在同一事务内
     * </pre>
     * Postgres 协议对<b>单条 SQL 的绑定参数个数</b>有 65535 的硬上限
     * ⇒ {@code floor(65535/12) = 5461}。
     * <b>recipients ≥ 5462 时整条 SQL 直接失败、事务回滚、一条通知都发不出去。</b>
     * 而广播模式下 {@code collectBroadcastRecipients} 会收集<b>全量用户</b>，
     * 这是一个必然被撞上的规模悬崖（Go 侧分批 200 则无此上限）。
     *
     * <p><b>原子性没有被这次修复削弱</b>：切片全部发生在这一个 {@code @Transactional}
     * 方法内部，任一片失败仍会回滚全部 —— 与 Go 的
     * {@code CreateInBatches(items, 200)} 语义一致。
     */
    @Transactional
    public void insertNotificationsTx(List<NotificationEntity> batch) {
        // Go: CreateInBatches(items, 200) 对空切片是 no-op，这里同样直接返回。
        for (int from = 0; from < batch.size(); from += INSERT_BATCH_SIZE) {
            int to = Math.min(from + INSERT_BATCH_SIZE, batch.size());
            notificationMapper.insertBatch(batch.subList(from, to));
        }
    }

    // ==================================================================
    // 4. markConversationReadTx
    // ==================================================================

    /**
     * 标记某会话内「以我为 recipient」的未读消息为已读，一个事务。
     *
     * <p><b>Go 出处</b>：{@code MarkConversationRead}（{@code internal/model/message.go:176-184}）：
     * <pre>
     * return conn.Model(&amp;ConversationMessage{}).
     *     Where("conversation_id = ? AND recipient_role = ? AND recipient_id = ? AND is_read = ?",
     *           conversationID, role, id, false).
     *     Updates(map[string]any{"is_read": true, "read_at": &amp;now}).Error
     * </pre>
     * <b>单条 UPDATE，不触碰 conversation 表。</b>
     *
     * <p><b>⚠️ DRIFT-MSG-V-006（2026-10-22 修）：删掉了多出来的那次写</b>
     * <pre>
     * 修复前：本方法除消息表外还调 conversationMapper.touchUpdatedAt(convId, readAt)
     *        —— 即<b>一个「标记已读」的写操作会顺带改写会话的 updated_at</b>
     * </pre>
     * 删除理由<b>不是</b>「它看起来多余」，而是它<b>当初存在的理由已经不存在了</b>：
     * 修复前它被写成「Java 侧排序缺 updated_at 次级键（DRIFT-MSG-V-005），此处先补写入」——
     * 而 DRIFT-MSG-V-005 已在本轮修好（{@code listByParticipant} 补回
     * {@code orderByDesc(updatedAt)}），补偿对象消失了，补偿本身就成了纯副作用。
     *
     * <p><b>被删掉的可观察影响</b>（这正是要删它的原因）：
     * <ol>
     *   <li>{@code ConversationItem.updated_at} 两端取值不同；</li>
     *   <li>Go 的 {@code ListConversations} 次级排序键 {@code updated_at DESC}
     *       会被「读操作」改动 —— 两端的<b>读副作用集不同</b>。</li>
     * </ol>
     *
     * <p>⚠️ 顺带清掉编号撞车：本文件此前把这条标为「DRIFT-MSG-006」，
     * 而 AGENTS.md §6.6 的 DRIFT-MSG-006 是「缺 spring-boot-flyway」——两个不同缺陷共用一个 ID；
     * 同一段注释还引用了**本文件里不存在的** {@code DRIFT-MSG-008}。
     * 权威台账是 AGENTS.md §0 + §6.6 + §6.7，不是代码注释。见 §6.7.0。
     */
    @Transactional
    public void markConversationReadTx(long conversationId, long recipientId, int recipientRole,
                                       OffsetDateTime readAt) {
        messageMapper.markReadByConversationAndRecipient(
                conversationId, recipientId, recipientRole, readAt);
    }

    // ==================================================================
    // 5. markNotificationReadTx
    // ==================================================================

    /**
     * 标记单条通知为已读，<b>单条 UPDATE</b>，归属条件直接进 WHERE。
     *
     * <p><b>Go 出处</b>：{@code MarkNotificationRead}（{@code internal/model/message.go:72-84}）：
     * <pre>
     * res := conn.Model(&amp;Notification{}).
     *      Where("id = ? AND recipient_id = ?", notificationID, recipientID).
     *      Updates(map[string]any{"is_read": true, "read_at": &amp;now})
     * if res.RowsAffected == 0 { return ErrRecordNotFound }
     * </pre>
     *
     * @return 受影响行数；0 = 不存在或不归属该 recipient，由上层映射成 NOT_FOUND(5203)。
     */
    @Transactional
    public int markNotificationReadTx(long id, long recipientId, OffsetDateTime readAt) {
        return notificationMapper.markRead(id, recipientId, readAt);
    }

    // ==================================================================
    // 6. markAllNotificationsReadTx
    // ==================================================================

    /**
     * 标记某 recipient 的全部未读通知为已读。
     *
     * <p><b>Go 出处</b>：{@code MarkAllNotificationsRead}
     * （{@code internal/model/message.go:86-91}）：
     * {@code Where("recipient_id = ? AND is_read = ?", recipientID, false)}。
     *
     * <p>{@code senderRole} / {@code kind} 传 0 = 不按这两个维度过滤（Go 侧本就没有该维度）。
     */
    @Transactional
    public void markAllNotificationsReadTx(long recipientId, int senderRole, int kind,
                                           OffsetDateTime readAt) {
        notificationMapper.markAllReadByRecipient(recipientId, senderRole, kind, readAt);
    }
}