package com.rag.message.message.rpc.service;

import com.rag.common.snowflake.Snowflake;
import com.rag.common.spring.LoggerFactoryBean;
import com.rag.message.message.proto.ConversationListReq;
import com.rag.message.message.proto.ConversationListResp;
import com.rag.message.message.proto.ConversationMessageListReq;
import com.rag.message.message.proto.ConversationMessageListResp;
import com.rag.message.message.proto.UnreadSummaryReq;
import com.rag.message.message.proto.UnreadSummaryResp;
import com.rag.message.message.proto.NotificationListReq;
import com.rag.message.message.proto.NotificationListResp;
import com.rag.message.message.proto.MarkAllNotificationsReq;
import com.rag.message.message.proto.MarkConversationReadReq;
import com.rag.message.message.proto.MarkNotificationReq;
import com.rag.message.message.proto.SendChatMessageReq;
import com.rag.message.message.proto.SendChatMessageResp;
import com.rag.message.message.proto.SendNotificationReq;
import com.rag.message.message.proto.SendNotificationResp;
import com.rag.message.message.rpc.client.AdminRpcClient;
import com.rag.message.message.rpc.client.UserRpcClient;
import com.rag.message.message.rpc.config.MessageProperties;
import com.rag.message.message.rpc.mapper.ConversationMapper;
import com.rag.message.message.rpc.mapper.ConversationMessageMapper;
import com.rag.message.message.rpc.mapper.NotificationMapper;
import com.rag.message.message.rpc.metrics.MessageMetrics;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * message 域服务 —— RAG 端"1 DomainService 9 方法"模式，对应 Go 端
 * {@code service/message/rpc/internal/logic/} 目录下 9 个 *Logic.go：
 *
 * <pre>
 * sendchatmessagelogic      → sendChatMessage()
 * sendnotificationlogic     → sendNotification()
 * listconversationslogic    → listConversations()
 * getconversationmessageslogic → getConversationMessages()
 * markconversationreadlogic → markConversationRead()
 * listnotificationslogic    → listNotifications()
 * marknotificationreadlogic → markNotificationRead()
 * markallnotificationsreadlogic → markAllNotificationsRead()
 * getunreadsummarylogic     → getUnreadSummary()
 * </pre>
 *
 * <p>框架职责：
 * <ul>
 *   <li>Spring {@code @Service} —— 一个类装一个领域的所有用例方法</li>
 *   <li>取代了原 internal.logic.{SendChatMessageLogic, SendNotificationLogic, ...} 9 个独立 @Service 类</li>
 *   <li>取代了 internal.svc.ServiceContext —— 本类直接注入所有依赖（Mapper / Client / Metrics / Logger / Properties）</li>
 *   <li>9 个方法体一对一从镜像 Logic 翻译（与 user-rpc 的 UserDomainService 1:1 同模式）</li>
 *   <li>每方法 try/finally 包装 observeRpc(method) + observeChat / observeNotification（动作不同）+ observeDbError</li>
 *   <li>事务方法（createConversationWithMessageTx / createOrSendMessageTx / markConversationReadTx /
 *       markAllNotificationsReadTx / markNotificationReadTx / insertNotificationsTx）放本类上 @Transactional，
 *       Mapper 接口只承担单条 SQL 的原子操作</li>
 * </ul>
 *
 * <p>异常映射：捕获自定义 {@link com.rag.message.message.rpc.exception.MessageExceptions.DbSelectException} 等 →
 * observeDbError(action, type) 后 throw；gRPC status error 直接 throw（由
 * {@link com.rag.message.message.rpc.service.impl.MessageRpcServiceImpl#invoke} 转发）。
 *
 * <p>Phase 1 R1：本类 9 方法体 1:1 对照镜像 Logic 实现，业务翻译在 R2 填血肉；
 * 当前 R1 占位（throw UnsupportedOperationException），但**方法签名 / try/finally 模板 / 依赖注入
 * 都已就位**，确保 mvn compile 通过 + 后续 R2 增量填实现不需要重构骨架。
 */
@Service
public class MessageDomainService {

    private final NotificationMapper notificationMapper;
    private final ConversationMapper conversationMapper;
    private final ConversationMessageMapper messageMapper;
    private final UserRpcClient userRpcClient;
    private final AdminRpcClient adminRpcClient;
    private final MessageProperties properties;
    private final MessageMetrics metrics;
    private final LoggerFactoryBean logger;
    private final Tracer tracer;

    public MessageDomainService(NotificationMapper notificationMapper,
                                ConversationMapper conversationMapper,
                                ConversationMessageMapper messageMapper,
                                UserRpcClient userRpcClient,
                                AdminRpcClient adminRpcClient,
                                MessageProperties properties,
                                MessageMetrics metrics,
                                LoggerFactoryBean logger) {
        this.notificationMapper = notificationMapper;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.userRpcClient = userRpcClient;
        this.adminRpcClient = adminRpcClient;
        this.properties = properties;
        this.metrics = metrics;
        this.logger = logger;
        // 对应 Go 端 otel.Tracer("message-rpc")
        this.tracer = GlobalOpenTelemetry.getTracer("message-rpc");
    }

    // ============== 1. sendchatmessagelogic → sendChatMessage() ==============

    /**
     * 对应 Go 端 sendchatmessagelogic.go（业务最复杂）。
     *
     * <p>整体流程分支（与 Go 端 1:1）：
     * <ol>
     *   <li>{@code content trim + senderId>0 + validRole(senderRole)} → 否则 INVALID_ARGUMENT + ErrorInvalidParam</li>
     *   <li>若指定 {@code conversation_id>0}：findConversationByID + isParticipant + peerOf</li>
     *   <li>否则：recipient_id>0 && validRole(recipientRole) + self-chat 检测 + canonicalize</li>
     *   <li>分配 message_id；构造 ConversationMessage</li>
     *   <li>conv==null 分支：ADMIN→USER 直接 OPEN；USER→USER PENDING（首条 ≤ 50 字符）</li>
     *   <li>否则已有会话：处理 PENDING → OPEN 状态切换</li>
     *   <li>{@link #createConversationWithMessageTx} 或 {@link #createOrSendMessageTx} 事务</li>
     * </ol>
     *
     * <p>Phase 1 R1 占位 —— 业务翻译在 R2 落地。骨架已就位：
     * try/finally observeRpc("SendChatMessage") + observeChat("send")。
     */
    public SendChatMessageResp sendChatMessage(SendChatMessageReq req) {
        long start = System.nanoTime();
        Throwable err = null;
        try {
            // ============ R2 业务翻译 ============
            throw new UnsupportedOperationException(
                    "sendChatMessage 待 Phase 1 R2 落地（参考镜像 SendChatMessageLogic）");
        } catch (RuntimeException e) {
            err = e;
            throw e;
        } finally {
            try {
                metrics.observeRpc("SendChatMessage", start, err);
                metrics.observeChat("send", err);
            } catch (Throwable ignore) {
                // ignore
            }
        }
    }

    /**
     * 对应 Go 端 createConversationWithMessageTx：新建会话 + 首条消息，一个事务。
     */
    @Transactional
    public void createConversationWithMessageTx(
            com.rag.message.message.rpc.domain.ConversationEntity conv,
            com.rag.message.message.rpc.domain.ConversationMessageEntity msg) {
        // R2 翻译
        throw new UnsupportedOperationException(
                "createConversationWithMessageTx 待 R2 落地（Mapper.insert + Mapper.insert 同一事务）");
    }

    /**
     * 对应 Go 端 createOrSendMessageTx：已有会话内 append 一条消息 + 更新会话 last_message_*。
     */
    @Transactional
    public void createOrSendMessageTx(
            com.rag.message.message.rpc.domain.ConversationEntity conv,
            com.rag.message.message.rpc.domain.ConversationMessageEntity msg,
            int newStatus, int pendingSenderRole, long pendingSenderId,
            String lastMessagePreview) {
        // R2 翻译
        throw new UnsupportedOperationException(
                "createOrSendMessageTx 待 R2 落地（Mapper.updateLastMessage + Mapper.insert 同一事务）");
    }

    // ============== 2. sendnotificationlogic → sendNotification() ==============

    /**
     * 对应 Go 端 sendnotificationlogic.go：admin 域发系统通知 / 广播。
     * 事务：批量插 message_notification（事务）+ 推送 kafka（异步）。
     */
    public SendNotificationResp sendNotification(SendNotificationReq req) {
        long start = System.nanoTime();
        Throwable err = null;
        try {
            // R2 翻译
            throw new UnsupportedOperationException(
                    "sendNotification 待 Phase 1 R2 落地（参考镜像 SendNotificationLogic）");
        } catch (RuntimeException e) {
            err = e;
            throw e;
        } finally {
            try {
                metrics.observeRpc("SendNotification", start, err);
                // notification_action 标签 kind 在 Logic 内部拿不到（取不到 NotificationKind），
                // 由 R2 在发送处单独调 metrics.observeNotification(kind, err)
            } catch (Throwable ignore) {
                // ignore
            }
        }
    }

    /**
     * 对应 Go 端 insertNotificationsTx：批量插 notification（admin 广播 / system notice）。
     */
    @Transactional
    public void insertNotificationsTx(java.util.List<com.rag.message.message.rpc.domain.NotificationEntity> batch) {
        // R2 翻译
        throw new UnsupportedOperationException(
                "insertNotificationsTx 待 R2 落地（批量 Mapper.insert）");
    }

    // ============== 3. listconversationslogic → listConversations() ==============

    /**
     * 对应 Go 端 listconversationslogic.go：user 域分页拉会话列表，含最近一条消息预览 + unread count + peer profile。
     */
    public ConversationListResp listConversations(ConversationListReq req) {
        long start = System.nanoTime();
        Throwable err = null;
        try {
            // R2 翻译
            throw new UnsupportedOperationException(
                    "listConversations 待 Phase 1 R2 落地（参考镜像 ListConversationsLogic）");
        } catch (RuntimeException e) {
            err = e;
            throw e;
        } finally {
            try {
                metrics.observeRpc("ListConversations", start, err);
            } catch (Throwable ignore) {
                // ignore
            }
        }
    }

    // ============== 4. getconversationmessageslogic → getConversationMessages() ==============

    /**
     * 对应 Go 端 getconversationmessageslogic.go：按 conversation_id 拉历史消息，分页。
     */
    public ConversationMessageListResp getConversationMessages(ConversationMessageListReq req) {
        long start = System.nanoTime();
        Throwable err = null;
        try {
            // R2 翻译
            throw new UnsupportedOperationException(
                    "getConversationMessages 待 Phase 1 R2 落地（参考镜像 GetConversationMessagesLogic）");
        } catch (RuntimeException e) {
            err = e;
            throw e;
        } finally {
            try {
                metrics.observeRpc("GetConversationMessages", start, err);
            } catch (Throwable ignore) {
                // ignore
            }
        }
    }

    // ============== 5. markconversationreadlogic → markConversationRead() ==============

    /**
     * 对应 Go 端 markconversationreadlogic.go：单会话已读位置推进，更新 last_read_message_id。
     * 事务：批量更新 conversation_message is_read + 更新 conversation.updated_at。
     */
    public com.rag.message.message.proto.BaseResp markConversationRead(MarkConversationReadReq req) {
        long start = System.nanoTime();
        Throwable err = null;
        try {
            // R2 翻译
            throw new UnsupportedOperationException(
                    "markConversationRead 待 Phase 1 R2 落地");
        } catch (RuntimeException e) {
            err = e;
            throw e;
        } finally {
            try {
                metrics.observeRpc("MarkConversationRead", start, err);
            } catch (Throwable ignore) {
                // ignore
            }
        }
    }

    /**
     * 对应 Go 端 markConversationReadTx：单会话已读推进，一个事务。
     */
    @Transactional
    public void markConversationReadTx(long conversationId, long recipientId, int recipientRole,
                                       java.time.LocalDateTime readAt) {
        // R2 翻译
        throw new UnsupportedOperationException(
                "markConversationReadTx 待 R2 落地（Mapper.markReadByConversationAndRecipient + Mapper.touchUpdatedAt）");
    }

    // ============== 6. listnotificationslogic → listNotifications() ==============

    /**
     * 对应 Go 端 listnotificationslogic.go：user 域分页拉通知列表，按 kind 过滤。
     */
    public NotificationListResp listNotifications(NotificationListReq req) {
        long start = System.nanoTime();
        Throwable err = null;
        try {
            // R2 翻译
            throw new UnsupportedOperationException(
                    "listNotifications 待 Phase 1 R2 落地");
        } catch (RuntimeException e) {
            err = e;
            throw e;
        } finally {
            try {
                metrics.observeRpc("ListNotifications", start, err);
            } catch (Throwable ignore) {
                // ignore
            }
        }
    }

    // ============== 7. marknotificationreadlogic → markNotificationRead() ==============

    /**
     * 对应 Go 端 marknotificationreadlogic.go：单条通知标记已读。
     */
    public com.rag.message.message.proto.BaseResp markNotificationRead(MarkNotificationReq req) {
        long start = System.nanoTime();
        Throwable err = null;
        try {
            // R2 翻译
            throw new UnsupportedOperationException(
                    "markNotificationRead 待 Phase 1 R2 落地");
        } catch (RuntimeException e) {
            err = e;
            throw e;
        } finally {
            try {
                metrics.observeRpc("MarkNotificationRead", start, err);
            } catch (Throwable ignore) {
                // ignore
            }
        }
    }

    /**
     * 对应 Go 端 markNotificationReadTx：单条通知已读推进。
     */
    @Transactional
    public void markNotificationReadTx(long id, java.time.LocalDateTime readAt) {
        // R2 翻译
        throw new UnsupportedOperationException(
                "markNotificationReadTx 待 R2 落地（Mapper.markRead）");
    }

    // ============== 8. markallnotificationsreadlogic → markAllNotificationsRead() ==============

    /**
     * 对应 Go 端 markallnotificationsreadlogic.go：全部通知标记已读，按 kind 过滤。
     * 事务：批量 UPDATE message_notification SET is_read=true。
     */
    public com.rag.message.message.proto.BaseResp markAllNotificationsRead(MarkAllNotificationsReq req) {
        long start = System.nanoTime();
        Throwable err = null;
        try {
            // R2 翻译
            throw new UnsupportedOperationException(
                    "markAllNotificationsRead 待 Phase 1 R2 落地");
        } catch (RuntimeException e) {
            err = e;
            throw e;
        } finally {
            try {
                metrics.observeRpc("MarkAllNotificationsRead", start, err);
            } catch (Throwable ignore) {
                // ignore
            }
        }
    }

    /**
     * 对应 Go 端 markAllNotificationsReadTx：批量 UPDATE notifications。
     */
    @Transactional
    public void markAllNotificationsReadTx(long recipientId, int senderRole, int kind,
                                            java.time.LocalDateTime readAt) {
        // R2 翻译
        throw new UnsupportedOperationException(
                "markAllNotificationsReadTx 待 R2 落地（Mapper.markAllReadByRecipient）");
    }

    // ============== 9. getunreadsummarylogic → getUnreadSummary() ==============

    /**
     * 对应 Go 端 getunreadsummarylogic.go：聚合未读会话 + 通知数。
     */
    public UnreadSummaryResp getUnreadSummary(UnreadSummaryReq req) {
        long start = System.nanoTime();
        Throwable err = null;
        try {
            // R2 翻译
            throw new UnsupportedOperationException(
                    "getUnreadSummary 待 Phase 1 R2 落地");
        } catch (RuntimeException e) {
            err = e;
            throw e;
        } finally {
            try {
                metrics.observeRpc("GetUnreadSummary", start, err);
            } catch (Throwable ignore) {
                // ignore
            }
        }
    }

    // ============== 辅助：Snowflake ID 生成 ==============

    /**
     * 对应 Go 端 {@code snowflake.GetID()} —— 内部用 service/common 的 SnowflakeBean，
     * Logic 层在需要分配新 ID 时调用。
     */
    public long nextSnowflakeId() {
        return Snowflake.nextId();
    }
}