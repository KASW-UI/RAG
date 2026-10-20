package com.rag.message.message.rpc.service;

import com.rag.message.message.common.errmsg.MessageErrorCode;
import com.rag.message.message.common.errmsg.MessageErrorMessages;
import com.rag.message.message.proto.BaseResp;
import com.rag.message.message.proto.ChatMessageItem;
import com.rag.message.message.proto.ConversationItem;
import com.rag.message.message.proto.ConversationStatus;
import com.rag.message.message.proto.NotificationItem;
import com.rag.message.message.proto.NotificationKind;
import com.rag.message.message.proto.SenderRole;
import com.rag.message.message.rpc.domain.ConversationEntity;
import com.rag.message.message.rpc.domain.ConversationMessageEntity;
import com.rag.message.message.rpc.domain.NotificationEntity;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 跨 RPC 共享业务工具 —— 对应镜像 Go 端 {@code service/message/rpc/internal/logic/helper.go}（MessageHelpers）。
 *
 * <p>承担：
 * <ul>
 *   <li>响应工厂：successBaseResp / toNotificationItem / toMessageItem / buildConversationItem</li>
 *   <li>校验 / 归一化：validRole / normalizeLimit / trimContent / runeLen</li>
 *   <li>参与者规范化：canonicalParticipants / isParticipant / peerOf / canSend</li>
 *   <li>错误构造：grpcRuntimeError</li>
 *   <li>工具：notificationKindLabel / dedupeRecipients / newNotificationRecord / trimPreview</li>
 * </ul>
 *
 * <p>用 {@code @Component} 让 Spring 注入到 DomainService；包成类是为 Java 没有 package-level function。
 * <br>ServiceContext 中"依赖外部 stub 的两个方法"（loadProfile / collectBroadcastRecipients）已迁到
 * {@link com.rag.message.message.rpc.client.UserRpcClient} 与
 * {@link com.rag.message.message.rpc.client.AdminRpcClient}，避免本工具类持外部依赖。
 *
 * <p>RAG 端"1 DomainService 9 方法"模式：所有 Logic 调用都走 MessageRpcSupport + 注入的
 * Mapper / Client / Metrics / Logger，**不引入 ServiceContext 中间层**。
 */
@Component
public class MessageRpcSupport {

    // =============================================================================
    // 响应工厂 —— successBaseResp / toNotificationItem / toMessageItem / buildConversationItem
    // =============================================================================

    /** 对应 Go {@code successBaseResp()}：code=200 / msg="OK"。 */
    public static BaseResp successBaseResp() {
        return BaseResp.newBuilder()
                .setCode(MessageErrorCode.SUCCESS.getCode())
                .setMsg(MessageErrorMessages.getMsg(MessageErrorCode.SUCCESS.getCode()))
                .build();
    }

    /**
     * 对应 Go {@code toNotificationItem(item model.Notification) *pb.NotificationItem}：
     * Notification entity → wire DTO 转换。{@code Extra} 走 {@link Map} 1:1 输出。
     *
     * <p>{@code CreatedAt.toEpochSecond(UTC)} 对应 proto {@code int64 created_at} 秒级时间戳，
     * 与 Go 端 {@code CreatedAt.Unix()} 1:1。
     */
    public static NotificationItem toNotificationItem(NotificationEntity item) {
        if (item == null) {
            return null;
        }
        return NotificationItem.newBuilder()
                .setId(item.getId())
                .setRecipientId(item.getRecipientId())
                .setSenderId(item.getSenderId())
                .setSenderRole(SenderRole.forNumber(item.getSenderRole()))
                .setKind(NotificationKind.forNumber(item.getKind()))
                .setTitle(item.getTitle())
                .setContent(item.getContent())
                .setIsRead(item.isRead())
                .setCreatedAt(item.getCreatedAt() != null ? item.getCreatedAt().toEpochSecond(java.time.ZoneOffset.UTC) : 0L)
                .putAllExtra(safeMap(item.getExtra()))
                .build();
    }

    /** 对应 Go {@code toMessageItem(item model.ConversationMessage) *pb.ChatMessageItem}。 */
    public static ChatMessageItem toMessageItem(ConversationMessageEntity item) {
        if (item == null) {
            return null;
        }
        return ChatMessageItem.newBuilder()
                .setId(item.getId())
                .setConversationId(item.getConversationId())
                .setSenderId(item.getSenderId())
                .setSenderRole(SenderRole.forNumber(item.getSenderRole()))
                .setRecipientId(item.getRecipientId())
                .setRecipientRole(SenderRole.forNumber(item.getRecipientRole()))
                .setContent(item.getContent())
                .setIsRead(item.isRead())
                .setCreatedAt(item.getCreatedAt() != null ? item.getCreatedAt().toEpochSecond(java.time.ZoneOffset.UTC) : 0L)
                .build();
    }

    private static Map<String, String> safeMap(Map<String, String> in) {
        if (in == null || in.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(in);
    }

    // =============================================================================
    // 校验 / 归一化 —— validRole / normalizeLimit / trimContent / runeLen
    // =============================================================================

    /** 对应 Go {@code validRole(role pb.SenderRole) bool}：只有 USER / ADMIN 合法。 */
    public static boolean validRole(SenderRole role) {
        return role == SenderRole.USER || role == SenderRole.ADMIN;
    }

    /** role int → SenderRole；非法 int 一律返回 UNSPECIFIED。 */
    public static SenderRole senderRoleFromInt(int role) {
        return SenderRole.forNumber(role);
    }

    /**
     * 对应 Go {@code normalizeLimit(limit, defaultLimit, maxLimit int32) int32}：
     * ≤0 视为 default；maxLimit &gt; 0 时把超出的部分截到 maxLimit（&lt;=0 不截断）。
     */
    public static int normalizeLimit(int limit, int defaultLimit, int maxLimit) {
        if (limit <= 0) {
            limit = defaultLimit;
        }
        if (maxLimit > 0 && limit > maxLimit) {
            limit = maxLimit;
        }
        return limit;
    }

    /** 对应 Go {@code trimContent(content string) string}：strings.TrimSpace 等价。 */
    public static String trimContent(String content) {
        return content == null ? "" : content.trim();
    }

    /**
     * 对应 Go {@code runeLen(content string) int}：codepoint 数。
     * SendChatMessage 的"首条消息 ≤ 50 字符"用此方法度量。
     */
    public static int runeLen(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        return content.codePointCount(0, content.length());
    }

    // =============================================================================
    // 参与者规范化 —— canonicalParticipants / isParticipant / peerOf / canSend
    // =============================================================================

    /**
     * 对应 Go {@code canonicalParticipants(role1, id1, role2, id2)}：
     * 先按 role asc，role 相同则按 id asc。让 uk_conversation_pair 唯一索引能找到同一对会话。
     */
    public static CanonicalPair canonicalParticipants(SenderRole role1, long id1,
                                                      SenderRole role2, long id2) {
        int r1 = role1.getNumber();
        int r2 = role2.getNumber();
        if (r1 < r2) {
            return new CanonicalPair(role1, id1, role2, id2);
        }
        if (r1 > r2) {
            return new CanonicalPair(role2, id2, role1, id1);
        }
        if (id1 <= id2) {
            return new CanonicalPair(role1, id1, role2, id2);
        }
        return new CanonicalPair(role2, id2, role1, id1);
    }

    /** 对应 Go {@code isParticipant(conv, role, id) bool}。 */
    public static boolean isParticipant(ConversationEntity conv, SenderRole role, long id) {
        if (conv == null) {
            return false;
        }
        int roleNum = role.getNumber();
        return (conv.getParticipant1Role() == roleNum && conv.getParticipant1Id() == id)
                || (conv.getParticipant2Role() == roleNum && conv.getParticipant2Id() == id);
    }

    /**
     * 对应 Go {@code peerOf(conv, role, id) (SenderRole, int64, bool)}。
     * 找不到 → (UNSPECIFIED, 0, false)。
     */
    public static PeerResult peerOf(ConversationEntity conv, SenderRole role, long id) {
        if (conv == null) {
            return new PeerResult(SenderRole.SENDER_ROLE_UNSPECIFIED, 0L, false);
        }
        int roleNum = role.getNumber();
        if (conv.getParticipant1Role() == roleNum && conv.getParticipant1Id() == id) {
            return new PeerResult(SenderRole.forNumber(conv.getParticipant2Role()),
                    conv.getParticipant2Id(), true);
        }
        if (conv.getParticipant2Role() == roleNum && conv.getParticipant2Id() == id) {
            return new PeerResult(SenderRole.forNumber(conv.getParticipant1Role()),
                    conv.getParticipant1Id(), true);
        }
        return new PeerResult(SenderRole.SENDER_ROLE_UNSPECIFIED, 0L, false);
    }

    /**
     * 对应 Go {@code canSend(conv, role, id) bool}：
     * OPEN 全员可发；PENDING 只有不是 pending_sender 的一方可发。
     */
    public static boolean canSend(ConversationEntity conv, SenderRole role, long id) {
        if (conv == null) {
            return false;
        }
        ConversationStatus status = ConversationStatus.forNumber(conv.getStatus());
        if (status == ConversationStatus.OPEN) {
            return true;
        }
        if (status != ConversationStatus.PENDING) {
            return false;
        }
        return !(conv.getPendingSenderRole() == role.getNumber() && conv.getPendingSenderId() == id);
    }

    // =============================================================================
    // ConversationItem 构造
    // =============================================================================

    /**
     * 对应 Go {@code buildConversationItem(conv, operatorRole, operatorID, unreadCount, peerName, peerEmail)}：
     * 调用方把 unreadCount / peerName / peerEmail 准备好（避免本工具类持外部 stub 依赖）。
     */
    public static ConversationItem buildConversationItem(ConversationEntity conv,
                                                          SenderRole operatorRole,
                                                          long operatorId,
                                                          long unreadCount,
                                                          String peerName,
                                                          String peerEmail) {
        PeerResult peer = peerOf(conv, operatorRole, operatorId);
        if (!peer.found()) {
            throw grpcRuntimeError(Status.Code.PERMISSION_DENIED,
                    MessageErrorCode.ERROR_FORBIDDEN.getCode());
        }
        return ConversationItem.newBuilder()
                .setConversationId(conv.getId())
                .setPeerId(peer.id())
                .setPeerRole(peer.role())
                .setPeerName(peerName == null ? "" : peerName)
                .setPeerEmail(peerEmail == null ? "" : peerEmail)
                .setStatus(ConversationStatus.forNumber(conv.getStatus()))
                .setUnreadCount(unreadCount)
                .setLatestMessage(conv.getLastMessagePreview() == null ? "" : conv.getLastMessagePreview())
                .setLatestMessageAt(conv.getLastMessageAt() != null
                        ? conv.getLastMessageAt().toEpochSecond(java.time.ZoneOffset.UTC) : 0L)
                .setUpdatedAt(conv.getUpdatedAt() != null
                        ? conv.getUpdatedAt().toEpochSecond(java.time.ZoneOffset.UTC) : 0L)
                .setCanSend(canSend(conv, operatorRole, operatorId))
                .build();
    }

    // =============================================================================
    // 错误构造 —— grpcRuntimeError
    // =============================================================================

    /**
     * 对应 Go {@code errWithBizCode(grpcCode codes.Code, bizCode int) error}：
     * 用 bizCode 查表得到 msg，塞进 gRPC status description，构造 {@link StatusRuntimeException}。
     */
    public static StatusRuntimeException grpcRuntimeError(Status.Code grpcCode, int bizCode) {
        return new StatusRuntimeException(
                Status.fromCode(grpcCode)
                        .withDescription(MessageErrorMessages.getMsg(bizCode)));
    }

    // =============================================================================
    // 工具 —— notificationKindLabel / dedupeRecipients / trimPreview / newNotificationRecord
    // =============================================================================

    /** 对应 Go {@code notificationKindLabel(kind pb.NotificationKind) string}。 */
    public static String notificationKindLabel(NotificationKind kind) {
        if (kind == null) {
            return "notification_kind_unspecified";
        }
        return kind.name().toLowerCase(Locale.ROOT);
    }

    /**
     * 对应 Go {@code dedupeRecipients(ids []int64) []int64}：
     * 去除 ≤0 与重复，保留首次出现顺序。
     */
    public static List<Long> dedupeRecipients(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashMap<Long, Boolean> seen = new LinkedHashMap<>();
        List<Long> out = new ArrayList<>(ids.size());
        for (Long id : ids) {
            if (id == null || id <= 0L) {
                continue;
            }
            if (seen.containsKey(id)) {
                continue;
            }
            seen.put(id, Boolean.TRUE);
            out.add(id);
        }
        return out;
    }

    /**
     * 对应 Go 端 conversation.last_message_preview 截断：
     * 超过 255 字符时截断（与列定义 varchar(255) 对齐）。
     */
    public static String trimPreview(String content) {
        if (content == null) {
            return "";
        }
        String trimmed = content.trim();
        if (trimmed.length() <= 255) {
            return trimmed;
        }
        return trimmed.substring(0, 255);
    }

    /**
     * 对应 Go {@code newNotificationRecord(recipientID, senderID, senderRole, kind, title, content, extra, now)}：
     * 构造一条 {@link NotificationEntity}，ID 由调用方提供的 snowflakeId 分配。
     */
    public static NotificationEntity newNotificationRecord(
            long recipientId, long senderId, SenderRole senderRole,
            NotificationKind kind, String title, String content,
            Map<String, String> extra, java.time.LocalDateTime now, long snowflakeId) {
        Map<String, String> safeExtra = (extra == null || extra.isEmpty())
                ? Collections.emptyMap() : extra;
        NotificationEntity n = new NotificationEntity();
        n.setId(snowflakeId);
        n.setRecipientId(recipientId);
        n.setSenderId(senderId);
        n.setSenderRole(senderRole.getNumber());
        n.setKind(kind.getNumber());
        n.setTitle(title);
        n.setContent(content);
        n.setRead(false);
        n.setExtra(safeExtra);
        n.setCreatedAt(now);
        n.setUpdatedAt(now);
        return n;
    }

    // =============================================================================
    // Result records
    // =============================================================================

    /** canonicalParticipants 返回值。 */
    public record CanonicalPair(SenderRole role1, long id1, SenderRole role2, long id2) {}

    /** peerOf 返回值。 */
    public record PeerResult(SenderRole role, long id, boolean found) {}
}