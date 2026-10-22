package com.rag.message.message.rpc.service;

import com.rag.common.snowflake.Snowflake;
import com.rag.common.spring.LoggerFactoryBean;
import com.rag.message.message.common.errmsg.MessageErrorCode;
import com.rag.message.message.common.errmsg.MessageErrorMessages;
import com.rag.message.message.proto.BaseResp;
import com.rag.message.message.proto.ChatMessageItem;
import com.rag.message.message.proto.ConversationItem;
import com.rag.message.message.proto.ConversationListReq;
import com.rag.message.message.proto.ConversationListResp;
import com.rag.message.message.proto.ConversationMessageListReq;
import com.rag.message.message.proto.ConversationMessageListResp;
import com.rag.message.message.proto.ConversationStatus;
import com.rag.message.message.proto.MarkAllNotificationsReq;
import com.rag.message.message.proto.MarkConversationReadReq;
import com.rag.message.message.proto.MarkNotificationReq;
import com.rag.message.message.proto.NotificationItem;
import com.rag.message.message.proto.NotificationListReq;
import com.rag.message.message.proto.NotificationListResp;
import com.rag.message.message.proto.SenderRole;
import com.rag.message.message.proto.SendChatMessageReq;
import com.rag.message.message.proto.SendChatMessageResp;
import com.rag.message.message.proto.SendNotificationReq;
import com.rag.message.message.proto.SendNotificationResp;
import com.rag.message.message.proto.UnreadSummaryReq;
import com.rag.message.message.proto.UnreadSummaryResp;
import com.rag.message.message.rpc.client.AdminRpcClient;
import com.rag.message.message.rpc.client.UserRpcClient;
import com.rag.message.message.rpc.config.MessageProperties;
import com.rag.message.message.rpc.domain.ConversationEntity;
import com.rag.message.message.rpc.domain.ConversationMessageEntity;
import com.rag.message.message.rpc.domain.NotificationEntity;
import com.rag.message.message.rpc.mapper.ConversationMapper;
import com.rag.message.message.rpc.mapper.ConversationMessageMapper;
import com.rag.message.message.rpc.mapper.NotificationMapper;
import com.rag.message.message.rpc.metrics.MessageMetrics;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * message 域服务 —— RAG 端"1 DomainService 9 方法"模式，对应 Go 端
 * {@code service/message/rpc/internal/logic/} 目录下 9 个 *Logic.go：
 *
 * <pre>
 * sendchatmessagelogic           → sendChatMessage()
 * sendnotificationlogic          → sendNotification()
 * listconversationslogic         → listConversations()
 * getconversationmessageslogic   → getConversationMessages()
 * markconversationreadlogic     → markConversationRead()
 * listnotificationslogic         → listNotifications()
 * marknotificationreadlogic      → markNotificationRead()
 * markallnotificationsreadlogic  → markAllNotificationsRead()
 * getunreadsummarylogic          → getUnreadSummary()
 * </pre>
 *
 * <p>框架职责：
 * <ul>
 *   <li>Spring {@code @Service} —— 1 个类装 1 个领域的所有用例方法</li>
 *   <li>取代了原 internal.logic.{XxxLogic} 9 个独立 @Service 类</li>
 *   <li>取代了 internal.svc.ServiceContext —— 本类直接注入所有依赖</li>
 *   <li>9 个方法体一对一从镜像 Logic 翻译，**每方法保留独立 try/finally observeRpc + 独立 metrics + 独立 span**</li>
 *   <li>事务方法（createConversationWithMessageTx / createOrSendMessageTx / markConversationReadTx /
 *       markNotificationReadTx / markAllNotificationsReadTx / insertNotificationsTx）<b>2026-10-22 已迁到
 *       独立 Bean {@link MessageAtomicService}</b>（DRIFT-MSG-001）。此前它们挂在本类上并由同类
 *       {@code doXxx(...)} 通过 {@code this.} 调用 —— self-invocation 不经 Spring 代理，
 *       {@code @Transactional} <b>静默失效</b>，6 处事务全部退化成 auto-commit。</li>
 * </ul>
 *
 * <p>关键 Decision（沿用镜像 Go parity + AGENTS.md 锁定）：
 * <ul>
 *   <li>strict HUMAN_ONLY：MyBatis-Plus + 3 个 Mapper，取代 JPA + Hibernate</li>
 *   <li>9 Logic 合并为 1 个类（grpc-java 框架约束：单 service 单 Impl 类）</li>
 *   <li>ServiceContext 模式废除：DI 直接进 DomainService（参考 user-rpc 的 UserDomainService）</li>
 * </ul>
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
    private final MessageAtomicService atomicService;
    private final Tracer tracer;

    public MessageDomainService(NotificationMapper notificationMapper,
                                ConversationMapper conversationMapper,
                                ConversationMessageMapper messageMapper,
                                UserRpcClient userRpcClient,
                                AdminRpcClient adminRpcClient,
                                MessageProperties properties,
                                MessageMetrics metrics,
                                LoggerFactoryBean logger,
                                MessageAtomicService atomicService) {
        this.notificationMapper = notificationMapper;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.userRpcClient = userRpcClient;
        this.adminRpcClient = adminRpcClient;
        this.properties = properties;
        this.metrics = metrics;
        this.logger = logger;
        // 2026-10-22 修 DRIFT-MSG-001：多步写事务必须经由**独立 Bean** 调用，
        // 否则同类 self-invocation 会让 @Transactional 静默失效。详见 MessageAtomicService 类注释。
        this.atomicService = atomicService;
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
     *   <li>若指定 {@code conversation_id>0}：
     *       <ul>
     *         <li>findConversationByID；不存在 → NOT_FOUND + ErrorConversationMiss</li>
     *         <li>isParticipant(sender) 检查；不是 → PERMISSION_DENIED + ErrorForbidden</li>
     *         <li>{@code peerOf(conv, sender)} → 取对端；找不到 → PERMISSION_DENIED + ErrorForbidden</li>
     *       </ul>
     *   </li>
     *   <li>否则（不指定 conversation_id）：
     *       <ul>
     *         <li>校验 recipient_id>0 && validRole(recipient_role) → 否则 INVALID_ARGUMENT + ErrorInvalidParam</li>
     *         <li>self-chat 检测（senderId==recipientId && senderRole==recipientRole）→ INVALID_ARGUMENT + ErrorSelfChat</li>
     *         <li>canonicalize 参与者 → 按规范化对找现有会话；不存在就准备新建</li>
     *       </ul>
     *   </li>
     *   <li>snowflake 分配 message_id；构造 ConversationMessage</li>
     *   <li>若 {@code conv==null}：
     *       <ul>
     *         <li>ADMIN→USER：直接 OPEN；要求 recipient_role == USER，否则 INVALID_ARGUMENT + ErrorUnsupportedRole</li>
     *         <li>USER→USER：PENDING；首条 ≤ 50 字符（runeLen）；
     *             非 ADMIN 角色创建 USER→非USER → PERMISSION_DENIED + ErrorAdminOnlyInit</li>
     *       </ul>
     *   </li>
     *   <li>否则（已有会话）：
     *       <ul>
     *         <li>处理 PENDING → OPEN 状态切换；如果当前 sender 就是 pending_sender，阻止（同方连发）
     *             → FAILED_PRECONDITION + ErrorChatPending</li>
     *         <li>UNSPECIFIED 状态当作 OPEN 处理</li>
     *       </ul>
     *   </li>
     *   <li>{@link #createConversationWithMessageTx} 或 {@link #createOrSendMessageTx} 提交事务</li>
     *   <li>返回 SendChatMessageResp，含 conversation_id / message / status / can_send</li>
     * </ol>
     */
    public SendChatMessageResp sendChatMessage(SendChatMessageReq req) {
        long start = System.nanoTime();
        Throwable err = null;
        try {
            return doSendChatMessage(req);
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

    private SendChatMessageResp doSendChatMessage(SendChatMessageReq in) {
        // ① validate
        String content = MessageRpcSupport.trimContent(in.getContent());
        if (in.getSenderId() <= 0L
                || content.isEmpty()
                || !MessageRpcSupport.validRole(in.getSenderRole())) {
            throw MessageRpcSupport.grpcRuntimeError(Status.Code.INVALID_ARGUMENT,
                    MessageErrorCode.ERROR_INVALID_PARAM.getCode());
        }

        // ② 加载 conv + 推导 peer
        Optional<ConversationEntity> convOpt;
        SenderRole recipientRole;
        long recipientId;
        if (in.getConversationId() > 0L) {
            try {
                convOpt = conversationMapper.findByIdOpt(in.getConversationId());
            } catch (RuntimeException e) {
                metrics.observeDbError("chat", "find_conversation");
                throw e;
            }
            if (convOpt.isEmpty()) {
                throw new StatusRuntimeException(
                        Status.NOT_FOUND.withDescription(MessageErrorMessages.getMsg(
                                MessageErrorCode.ERROR_CONVERSATION_MISS.getCode())));
            }
            ConversationEntity conv = convOpt.get();
            if (!MessageRpcSupport.isParticipant(conv, in.getSenderRole(), in.getSenderId())) {
                throw MessageRpcSupport.grpcRuntimeError(Status.Code.PERMISSION_DENIED,
                        MessageErrorCode.ERROR_FORBIDDEN.getCode());
            }
            MessageRpcSupport.PeerResult peer = MessageRpcSupport.peerOf(conv,
                    in.getSenderRole(), in.getSenderId());
            if (!peer.found()) {
                throw MessageRpcSupport.grpcRuntimeError(Status.Code.PERMISSION_DENIED,
                        MessageErrorCode.ERROR_FORBIDDEN.getCode());
            }
            recipientRole = peer.role();
            recipientId = peer.id();
        } else {
            recipientRole = in.getRecipientRole();
            recipientId = in.getRecipientId();
            if (recipientId <= 0L || !MessageRpcSupport.validRole(recipientRole)) {
                throw MessageRpcSupport.grpcRuntimeError(Status.Code.INVALID_ARGUMENT,
                        MessageErrorCode.ERROR_INVALID_PARAM.getCode());
            }
            if (recipientId == in.getSenderId() && recipientRole == in.getSenderRole()) {
                throw MessageRpcSupport.grpcRuntimeError(Status.Code.INVALID_ARGUMENT,
                        MessageErrorCode.ERROR_SELF_CHAT.getCode());
            }

            MessageRpcSupport.CanonicalPair canon = MessageRpcSupport.canonicalParticipants(
                    in.getSenderRole(), in.getSenderId(), recipientRole, recipientId);
            Optional<ConversationEntity> existing;
            try {
                existing = conversationMapper.findByPair(
                        canon.role1().getNumber(), canon.id1(),
                        canon.role2().getNumber(), canon.id2());
            } catch (RuntimeException e) {
                metrics.observeDbError("chat", "find_by_pair");
                throw e;
            }
            convOpt = existing;
        }

        // ③ 分配 message_id，构造 ConversationMessage
        long messageId = nextSnowflakeId();
        LocalDateTime now = LocalDateTime.now();
        ConversationMessageEntity messageItem = new ConversationMessageEntity();
        messageItem.setId(messageId);
        messageItem.setSenderId(in.getSenderId());
        messageItem.setSenderRole(in.getSenderRole().getNumber());
        messageItem.setRecipientId(recipientId);
        messageItem.setRecipientRole(recipientRole.getNumber());
        messageItem.setContent(content);
        messageItem.setRead(false);
        messageItem.setCreatedAt(now);

        // ④ conv == null 分支（新建会话）
        if (convOpt == null || convOpt.isEmpty()) {
            if (in.getSenderRole() == SenderRole.ADMIN) {
                if (recipientRole != SenderRole.USER) {
                    throw MessageRpcSupport.grpcRuntimeError(Status.Code.INVALID_ARGUMENT,
                            MessageErrorCode.ERROR_UNSUPPORTED_ROLE.getCode());
                }
                long conversationId = nextSnowflakeId();
                MessageRpcSupport.CanonicalPair canon = MessageRpcSupport.canonicalParticipants(
                        in.getSenderRole(), in.getSenderId(), recipientRole, recipientId);
                ConversationEntity conv = new ConversationEntity();
                conv.setId(conversationId);
                conv.setParticipant1Role(canon.role1().getNumber());
                conv.setParticipant1Id(canon.id1());
                conv.setParticipant2Role(canon.role2().getNumber());
                conv.setParticipant2Id(canon.id2());
                conv.setStatus(ConversationStatus.OPEN.getNumber());
                conv.setPendingSenderRole(SenderRole.SENDER_ROLE_UNSPECIFIED.getNumber());
                conv.setPendingSenderId(0L);
                conv.setCreatedByRole(in.getSenderRole().getNumber());
                conv.setCreatedById(in.getSenderId());
                conv.setLastMessageId(messageId);
                conv.setLastMessagePreview(MessageRpcSupport.trimPreview(content));
                conv.setLastMessageAt(now);
                conv.setCreatedAt(now);
                conv.setUpdatedAt(now);

                try {
                    atomicService.createConversationWithMessageTx(conv, messageItem);
                } catch (RuntimeException e) {
                    metrics.observeDbError("chat", "create_conversation");
                    throw e;
                }
                return SendChatMessageResp.newBuilder()
                        .setCode(MessageErrorCode.SUCCESS.getCode())
                        .setMsg(MessageErrorMessages.getMsg(MessageErrorCode.SUCCESS.getCode()))
                        .setConversationId(conv.getId())
                        .setMessage(MessageRpcSupport.toMessageItem(messageItem))
                        .setStatus(ConversationStatus.OPEN)
                        .setCanSend(true)
                        .build();
            }

            if (in.getSenderRole() != SenderRole.USER || recipientRole != SenderRole.USER) {
                throw MessageRpcSupport.grpcRuntimeError(Status.Code.PERMISSION_DENIED,
                        MessageErrorCode.ERROR_ADMIN_ONLY_INIT.getCode());
            }
            if (MessageRpcSupport.runeLen(content) > 50) {
                throw MessageRpcSupport.grpcRuntimeError(Status.Code.INVALID_ARGUMENT,
                        MessageErrorCode.ERROR_FIRST_MSG_TOO_LONG.getCode());
            }
            long conversationId = nextSnowflakeId();
            MessageRpcSupport.CanonicalPair canon = MessageRpcSupport.canonicalParticipants(
                    in.getSenderRole(), in.getSenderId(), recipientRole, recipientId);
            ConversationEntity conv = new ConversationEntity();
            conv.setId(conversationId);
            conv.setParticipant1Role(canon.role1().getNumber());
            conv.setParticipant1Id(canon.id1());
            conv.setParticipant2Role(canon.role2().getNumber());
            conv.setParticipant2Id(canon.id2());
            conv.setStatus(ConversationStatus.PENDING.getNumber());
            conv.setPendingSenderRole(in.getSenderRole().getNumber());
            conv.setPendingSenderId(in.getSenderId());
            conv.setCreatedByRole(in.getSenderRole().getNumber());
            conv.setCreatedById(in.getSenderId());
            conv.setLastMessageId(messageId);
            conv.setLastMessagePreview(MessageRpcSupport.trimPreview(content));
            conv.setLastMessageAt(now);
            conv.setCreatedAt(now);
            conv.setUpdatedAt(now);

            try {
                atomicService.createConversationWithMessageTx(conv, messageItem);
            } catch (RuntimeException e) {
                metrics.observeDbError("chat", "create_pending_conversation");
                throw e;
            }
            return SendChatMessageResp.newBuilder()
                    .setCode(MessageErrorCode.SUCCESS.getCode())
                    .setMsg(MessageErrorMessages.getMsg(MessageErrorCode.SUCCESS.getCode()))
                    .setConversationId(conv.getId())
                    .setMessage(MessageRpcSupport.toMessageItem(messageItem))
                    .setStatus(ConversationStatus.PENDING)
                    .setCanSend(false)
                    .build();
        }

        // ⑤ conv 存在，append 一条新消息
        ConversationEntity conv = convOpt.get();
        ConversationStatus statusAfterSend = ConversationStatus.forNumber(conv.getStatus());
        SenderRole pendingSenderRole = SenderRole.forNumber(conv.getPendingSenderRole());
        long pendingSenderId = conv.getPendingSenderId();

        if (statusAfterSend == ConversationStatus.PENDING) {
            if (pendingSenderRole == in.getSenderRole()
                    && pendingSenderId == in.getSenderId()) {
                throw MessageRpcSupport.grpcRuntimeError(Status.Code.FAILED_PRECONDITION,
                        MessageErrorCode.ERROR_CHAT_PENDING.getCode());
            }
            statusAfterSend = ConversationStatus.OPEN;
            pendingSenderRole = SenderRole.SENDER_ROLE_UNSPECIFIED;
            pendingSenderId = 0L;
        }
        if (statusAfterSend == ConversationStatus.CONVERSATION_STATUS_UNSPECIFIED) {
            statusAfterSend = ConversationStatus.OPEN;
        }

        try {
            atomicService.createOrSendMessageTx(conv, messageItem,
                    statusAfterSend.getNumber(),
                    pendingSenderRole.getNumber(),
                    pendingSenderId,
                    MessageRpcSupport.trimPreview(content));
        } catch (RuntimeException e) {
            metrics.observeDbError("chat", "append_message");
            throw e;
        }

        ChatMessageItem chatMsgItem = MessageRpcSupport.toMessageItem(messageItem);
        return SendChatMessageResp.newBuilder()
                .setCode(MessageErrorCode.SUCCESS.getCode())
                .setMsg(MessageErrorMessages.getMsg(MessageErrorCode.SUCCESS.getCode()))
                .setConversationId(conv.getId())
                .setMessage(chatMsgItem)
                .setStatus(statusAfterSend)
                .setCanSend(statusAfterSend == ConversationStatus.OPEN)
                .build();
    }

    // ============== 2. sendnotificationlogic → sendNotification() ==============

    /**
     * 对应 Go 端 sendnotificationlogic.go：admin 域发系统通知 / 广播。
     *
     * <p>行为 1:1：
     * <ol>
     *   <li>{@code title / content trim} 后校验非空 → 空则 INVALID_ARGUMENT + ErrorInvalidParam</li>
     *   <li>{@code recipients = dedupeRecipients(in.RecipientIds)}</li>
     *   <li>若 {@code broadcast=true}：从 adminRpc 拉所有 user_id 覆盖之前的 recipients；
     *       RPC 失败 → INTERNAL + ErrorDbSelect（与 Go 端 metrics.ObserveDBError 类型对齐）</li>
     *   <li>recipients 为空 → INVALID_ARGUMENT + ErrorInvalidParam</li>
     *   <li>逐个用 {@link MessageRpcSupport#newNotificationRecord} 构造 Notification，ID 由 snowflake 分配</li>
     *   <li>{@link #insertNotificationsTx} 批量插入；DB 失败 → ErrorDbUpdate</li>
     *   <li>返回 SendNotificationResp{code=200, msg="OK", affected=items.size()}</li>
     * </ol>
     */
    public SendNotificationResp sendNotification(SendNotificationReq req) {
        long start = System.nanoTime();
        Throwable err = null;
        String kindLabel = MessageRpcSupport.notificationKindLabel(req.getKind());
        try {
            return doSendNotification(req);
        } catch (RuntimeException e) {
            err = e;
            throw e;
        } finally {
            try {
                metrics.observeRpc("SendNotification", start, err);
                metrics.observeNotification(kindLabel, err);
            } catch (Throwable ignore) {
                // ignore
            }
        }
    }

    private SendNotificationResp doSendNotification(SendNotificationReq in) {
        // ① validate
        String title = MessageRpcSupport.trimContent(in.getTitle());
        String content = MessageRpcSupport.trimContent(in.getContent());
        if (title.isEmpty() || content.isEmpty()) {
            throw MessageRpcSupport.grpcRuntimeError(Status.Code.INVALID_ARGUMENT,
                    MessageErrorCode.ERROR_INVALID_PARAM.getCode());
        }

        // ② recipients
        List<Long> recipients;
        if (in.getBroadcast()) {
            try {
                recipients = adminRpcClient.collectBroadcastRecipients(
                        properties.getBroadcast().getPageSize());
            } catch (RuntimeException e) {
                metrics.observeDbError("notification", "broadcast_user_list");
                throw e;
            }
        } else {
            recipients = MessageRpcSupport.dedupeRecipients(in.getRecipientIdsList());
        }
        if (recipients.isEmpty()) {
            throw MessageRpcSupport.grpcRuntimeError(Status.Code.INVALID_ARGUMENT,
                    MessageErrorCode.ERROR_INVALID_PARAM.getCode());
        }

        // ③ 构造 records
        LocalDateTime now = LocalDateTime.now();
        List<NotificationEntity> items = new ArrayList<>(recipients.size());
        for (Long rid : recipients) {
            long snowflakeId = nextSnowflakeId();
            items.add(MessageRpcSupport.newNotificationRecord(
                    rid, in.getSenderId(), in.getSenderRole(),
                    in.getKind(), title, content, in.getExtraMap(),
                    now, snowflakeId));
        }

        // ④ 批量插入
        try {
            atomicService.insertNotificationsTx(items);
        } catch (RuntimeException e) {
            metrics.observeDbError("notification", "insert");
            throw e;
        }

        return SendNotificationResp.newBuilder()
                .setCode(MessageErrorCode.SUCCESS.getCode())
                .setMsg(MessageErrorMessages.getMsg(MessageErrorCode.SUCCESS.getCode()))
                .setAffected(items.size())
                .build();
    }

    // ============== 3. listconversationslogic → listConversations() ==============

    /**
     * 对应 Go 端 listconversationslogic.go。
     */
    public ConversationListResp listConversations(ConversationListReq req) {
        long start = System.nanoTime();
        Throwable err = null;
        try {
            return doListConversations(req);
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

    private ConversationListResp doListConversations(ConversationListReq in) {
        // ① validate
        if (in.getOperatorId() <= 0L || !MessageRpcSupport.validRole(in.getOperatorRole())) {
            throw MessageRpcSupport.grpcRuntimeError(Status.Code.INVALID_ARGUMENT,
                    MessageErrorCode.ERROR_INVALID_PARAM.getCode());
        }

        // ② normalize limit
        int limit = MessageRpcSupport.normalizeLimit(
                in.getLimit(),
                properties.getList().getDefaultLimit(),
                properties.getList().getMaxLimit());

        // ③ query conversations
        List<ConversationEntity> items;
        long total;
        try {
            // 🔴 DRIFT-MSG-V-012（2026-10-22 修）：limit / offset 传反了。
            //    Mapper 签名是 listByParticipant(role, id, limit, offset)，
            //    而这里传的是 (…, in.getOffset(), limit) ⇒ 实参落到 (limit=offset, offset=limit)。
            //    结果 SQL 变成 "LIMIT <offset> OFFSET <limit>"，offset=0 时即 **LIMIT 0**
            //    ⇒ ListConversations **永远返回空列表**（会话列表页全空）。
            //    这与 DRIFT-MSG-002（conversation_id 恒 0）是同款形态：
            //    一个被写错的普通值，让整条功能路径静默失效、且不产生任何错误日志。
            items = conversationMapper.listByParticipant(
                    in.getOperatorRole().getNumber(), in.getOperatorId(),
                    limit, in.getOffset());
            total = conversationMapper.countByParticipant(
                    in.getOperatorRole().getNumber(), in.getOperatorId());
        } catch (RuntimeException e) {
            metrics.observeDbError("conversation", "list");
            throw e;
        }

        // ④ total unread
        long unreadTotal;
        try {
            unreadTotal = messageMapper.countUnreadByRecipientGlobal(
                    in.getOperatorId(), in.getOperatorRole().getNumber());
        } catch (RuntimeException e) {
            metrics.observeDbError("conversation", "count_unread");
            throw e;
        }

        // ⑤ build items
        List<ConversationItem> itemDtos = new ArrayList<>(items.size());
        for (var conv : items) {
            long unread = conversationMapper.countUnreadMessagesByConversation(
                    conv.getId(), in.getOperatorId(), in.getOperatorRole().getNumber());
            MessageRpcSupport.PeerResult peer = MessageRpcSupport.peerOf(conv,
                    in.getOperatorRole(), in.getOperatorId());
            String[] profile;
            if (peer.role() == SenderRole.USER) {
                profile = userRpcClient.loadProfile(peer.role(), peer.id());
            } else {
                profile = adminRpcClient.loadProfile(peer.role(), peer.id());
            }
            itemDtos.add(MessageRpcSupport.buildConversationItem(conv,
                    in.getOperatorRole(), in.getOperatorId(),
                    unread, profile[0], profile[1]));
        }

        return ConversationListResp.newBuilder()
                .setCode(MessageErrorCode.SUCCESS.getCode())
                .setMsg(MessageErrorMessages.getMsg(MessageErrorCode.SUCCESS.getCode()))
                .addAllItems(itemDtos)
                .setTotal(total)
                .setUnreadCount(unreadTotal)
                .build();
    }

    // ============== 4. getconversationmessageslogic → getConversationMessages() ==============

    /**
     * 对应 Go 端 getconversationmessageslogic.go。
     */
    public ConversationMessageListResp getConversationMessages(ConversationMessageListReq req) {
        long start = System.nanoTime();
        Throwable err = null;
        try {
            return doGetConversationMessages(req);
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

    private ConversationMessageListResp doGetConversationMessages(ConversationMessageListReq in) {
        // ① validate
        if (in.getOperatorId() <= 0L
                || in.getConversationId() <= 0L
                || !MessageRpcSupport.validRole(in.getOperatorRole())) {
            throw MessageRpcSupport.grpcRuntimeError(Status.Code.INVALID_ARGUMENT,
                    MessageErrorCode.ERROR_INVALID_PARAM.getCode());
        }

        // ② find conversation
        Optional<ConversationEntity> convOpt;
        try {
            convOpt = conversationMapper.findByIdOpt(in.getConversationId());
        } catch (RuntimeException e) {
            metrics.observeDbError("conversation", "find");
            throw e;
        }
        if (convOpt.isEmpty()) {
            throw new StatusRuntimeException(
                    Status.NOT_FOUND.withDescription(
                            MessageErrorMessages.getMsg(
                                    MessageErrorCode.ERROR_CONVERSATION_MISS.getCode())));
        }
        ConversationEntity conv = convOpt.get();

        // ③ permission
        if (!MessageRpcSupport.isParticipant(conv, in.getOperatorRole(), in.getOperatorId())) {
            throw MessageRpcSupport.grpcRuntimeError(Status.Code.PERMISSION_DENIED,
                    MessageErrorCode.ERROR_FORBIDDEN.getCode());
        }

        // ④ normalize limit
        int limit = MessageRpcSupport.normalizeLimit(
                in.getLimit(),
                properties.getList().getDefaultLimit(),
                properties.getList().getMaxLimit());

        // ⑤ list messages
        List<ConversationMessageEntity> messages;
        try {
            messages = messageMapper.listByConversationPage(
                    in.getConversationId(), in.getOffset(), limit);
        } catch (RuntimeException e) {
            metrics.observeDbError("conversation", "list_messages");
            throw e;
        }

        List<ChatMessageItem> itemDtos = new ArrayList<>(messages.size());
        for (var m : messages) {
            itemDtos.add(MessageRpcSupport.toMessageItem(m));
        }

        return ConversationMessageListResp.newBuilder()
                .setCode(MessageErrorCode.SUCCESS.getCode())
                .setMsg(MessageErrorMessages.getMsg(MessageErrorCode.SUCCESS.getCode()))
                .addAllItems(itemDtos)
                .setStatus(ConversationStatus.forNumber(conv.getStatus()))
                .setCanSend(MessageRpcSupport.canSend(conv, in.getOperatorRole(), in.getOperatorId()))
                .setPendingSenderId(conv.getPendingSenderId())
                .setPendingSenderRole(SenderRole.forNumber(conv.getPendingSenderRole()))
                .build();
    }

    // ============== 5. markconversationreadlogic → markConversationRead() ==============

    /**
     * 对应 Go 端 markconversationreadlogic.go。
     */
    public BaseResp markConversationRead(MarkConversationReadReq req) {
        long start = System.nanoTime();
        Throwable err = null;
        try {
            return doMarkConversationRead(req);
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

    private BaseResp doMarkConversationRead(MarkConversationReadReq in) {
        // ① validate
        if (in.getOperatorId() <= 0L
                || in.getConversationId() <= 0L
                || !MessageRpcSupport.validRole(in.getOperatorRole())) {
            throw MessageRpcSupport.grpcRuntimeError(Status.Code.INVALID_ARGUMENT,
                    MessageErrorCode.ERROR_INVALID_PARAM.getCode());
        }

        // ② find conversation
        Optional<ConversationEntity> convOpt;
        try {
            convOpt = conversationMapper.findByIdOpt(in.getConversationId());
        } catch (RuntimeException e) {
            metrics.observeDbError("conversation", "find");
            throw e;
        }
        if (convOpt.isEmpty()) {
            throw new StatusRuntimeException(
                    Status.NOT_FOUND.withDescription(
                            MessageErrorMessages.getMsg(
                                    MessageErrorCode.ERROR_CONVERSATION_MISS.getCode())));
        }
        ConversationEntity conv = convOpt.get();

        // ③ permission
        if (!MessageRpcSupport.isParticipant(conv, in.getOperatorRole(), in.getOperatorId())) {
            throw MessageRpcSupport.grpcRuntimeError(Status.Code.PERMISSION_DENIED,
                    MessageErrorCode.ERROR_FORBIDDEN.getCode());
        }

        // ④ mark read
        try {
            atomicService.markConversationReadTx(in.getConversationId(), in.getOperatorId(),
                    in.getOperatorRole().getNumber(), LocalDateTime.now());
        } catch (RuntimeException e) {
            metrics.observeDbError("conversation", "mark_read");
            throw MessageRpcSupport.grpcRuntimeError(Status.Code.INTERNAL,
                    MessageErrorCode.ERROR_DB_UPDATE.getCode());
        }

        return MessageRpcSupport.successBaseResp();
    }

    // ============== 6. listnotificationslogic → listNotifications() ==============

    /**
     * 对应 Go 端 listnotificationslogic.go。
     */
    public NotificationListResp listNotifications(NotificationListReq req) {
        long start = System.nanoTime();
        Throwable err = null;
        try {
            return doListNotifications(req);
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

    private NotificationListResp doListNotifications(NotificationListReq in) {
        // ① validate
        if (in.getUserId() <= 0L) {
            throw MessageRpcSupport.grpcRuntimeError(Status.Code.INVALID_ARGUMENT,
                    MessageErrorCode.ERROR_INVALID_PARAM.getCode());
        }

        // ② normalize limit
        int limit = MessageRpcSupport.normalizeLimit(
                in.getLimit(),
                properties.getList().getDefaultLimit(),
                properties.getList().getMaxLimit());

        // ③ query
        // ⚠️ DRIFT-MSG-V-001（2026-10-22 修）：total 必须与 items 共用同一个 unreadOnly 过滤。
        //    Go（message.go:40-48）先给 query 加 is_read=false 再 query.Count()，
        //    所以 unread_only=true 时 total 只数未读。修复前这里是
        //    selectCount(QueryWrapper.eq("recipient_id", uid)) —— 无 is_read，
        //    与 listByRecipient 的过滤不一致 ⇒ total 恒为全部条数 ⇒ 前端分页多翻空页。
        //    见 §6.7.2 与 §6.7.5 纪律 A「分页接口的 total 与 items 必须成对断言」。
        List<NotificationEntity> items;
        long total;
        long unread;
        try {
            items = notificationMapper.listByRecipient(
                    in.getUserId(), 0, 0, in.getUnreadOnly(),
                    limit, in.getOffset());
            total = notificationMapper.countByRecipient(
                    in.getUserId(), 0, 0, in.getUnreadOnly());
            unread = notificationMapper.countUnreadByRecipient(
                    in.getUserId(), 0, 0);
        } catch (RuntimeException e) {
            metrics.observeDbError("notification", "list");
            throw e;
        }

        // ④ DTO convert
        List<NotificationItem> itemDtos = new ArrayList<>(items.size());
        for (var n : items) {
            itemDtos.add(MessageRpcSupport.toNotificationItem(n));
        }

        return NotificationListResp.newBuilder()
                .setCode(MessageErrorCode.SUCCESS.getCode())
                .setMsg(MessageErrorMessages.getMsg(MessageErrorCode.SUCCESS.getCode()))
                .addAllItems(itemDtos)
                .setTotal(total)
                .setUnreadCount(unread)
                .build();
    }

    // ============== 7. marknotificationreadlogic → markNotificationRead() ==============

    /**
     * 对应 Go 端 marknotificationreadlogic.go。
     */
    public BaseResp markNotificationRead(MarkNotificationReq req) {
        long start = System.nanoTime();
        Throwable err = null;
        try {
            return doMarkNotificationRead(req);
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

    private BaseResp doMarkNotificationRead(MarkNotificationReq in) {
        // ① validate（Go 出处 marknotificationreadlogic.go:40-43，逐条一致）
        if (in.getUserId() <= 0L || in.getNotificationId() <= 0L) {
            throw MessageRpcSupport.grpcRuntimeError(Status.Code.INVALID_ARGUMENT,
                    MessageErrorCode.ERROR_INVALID_PARAM.getCode());
        }

        // ②③ 归属校验 + mark read 合并为<b>单条</b> UPDATE（Go 出处 message.go:72-86）：
        //     Where("id = ? AND recipient_id = ?") + RowsAffected == 0 → ErrRecordNotFound
        // 修复前这里是 selectById 校验 + updateById 写入两条语句（TOCTOU 竞态），
        // 且 updateById 生成的 WHERE 只有 id、不含 recipient_id。
        int affected;
        try {
            affected = atomicService.markNotificationReadTx(in.getNotificationId(), in.getUserId(), LocalDateTime.now());
        } catch (RuntimeException e) {
            metrics.observeDbError("notification", "mark_read");
            throw MessageRpcSupport.grpcRuntimeError(Status.Code.INTERNAL,
                    MessageErrorCode.ERROR_DB_UPDATE.getCode());
        }

        // Go: if res.RowsAffected == 0 { return ErrRecordNotFound } → logic 映射成
        //     codes.NotFound + ErrorNotificationMiss（marknotificationreadlogic.go:47-51）
        if (affected == 0) {
            throw new StatusRuntimeException(
                    Status.NOT_FOUND.withDescription(
                            MessageErrorMessages.getMsg(
                                    MessageErrorCode.ERROR_NOTIFICATION_MISS.getCode())));
        }

        return MessageRpcSupport.successBaseResp();
    }

    // ============== 8. markallnotificationsreadlogic → markAllNotificationsRead() ==============

    /**
     * 对应 Go 端 markallnotificationsreadlogic.go。
     */
    public BaseResp markAllNotificationsRead(MarkAllNotificationsReq req) {
        long start = System.nanoTime();
        Throwable err = null;
        try {
            return doMarkAllNotificationsRead(req);
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

    private BaseResp doMarkAllNotificationsRead(MarkAllNotificationsReq in) {
        if (in.getUserId() <= 0L) {
            throw MessageRpcSupport.grpcRuntimeError(Status.Code.INVALID_ARGUMENT,
                    MessageErrorCode.ERROR_INVALID_PARAM.getCode());
        }

        try {
            atomicService.markAllNotificationsReadTx(in.getUserId(), 0, 0, LocalDateTime.now());
        } catch (RuntimeException e) {
            metrics.observeDbError("notification", "mark_all_read");
            throw MessageRpcSupport.grpcRuntimeError(Status.Code.INTERNAL,
                    MessageErrorCode.ERROR_DB_UPDATE.getCode());
        }

        return MessageRpcSupport.successBaseResp();
    }

    // ============== 9. getunreadsummarylogic → getUnreadSummary() ==============

    /**
     * 对应 Go 端 getunreadsummarylogic.go。
     */
    public UnreadSummaryResp getUnreadSummary(UnreadSummaryReq req) {
        long start = System.nanoTime();
        Throwable err = null;
        try {
            return doGetUnreadSummary(req);
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

    private UnreadSummaryResp doGetUnreadSummary(UnreadSummaryReq in) {
        // ① validate
        if (in.getUserId() <= 0L) {
            throw MessageRpcSupport.grpcRuntimeError(Status.Code.INVALID_ARGUMENT,
                    MessageErrorCode.ERROR_INVALID_PARAM.getCode());
        }

        // ② notification unread
        long notificationUnread;
        try {
            notificationUnread = notificationMapper.countUnreadByRecipient(in.getUserId(), 0, 0);
        } catch (RuntimeException e) {
            metrics.observeDbError("unread", "notification");
            throw e;
        }

        // ③ conversation unread
        long conversationUnread;
        try {
            conversationUnread = messageMapper.countUnreadByRecipientGlobal(
                    in.getUserId(), SenderRole.USER.getNumber());
        } catch (RuntimeException e) {
            metrics.observeDbError("unread", "conversation");
            throw e;
        }

        return UnreadSummaryResp.newBuilder()
                .setCode(MessageErrorCode.SUCCESS.getCode())
                .setMsg(MessageErrorMessages.getMsg(MessageErrorCode.SUCCESS.getCode()))
                .setNotificationUnread(notificationUnread)
                .setConversationUnread(conversationUnread)
                .setTotalUnread(notificationUnread + conversationUnread)
                .build();
    }

    // ============== 辅助：Snowflake ID 生成 ==============

    public long nextSnowflakeId() {
        return Snowflake.nextId();
    }
}