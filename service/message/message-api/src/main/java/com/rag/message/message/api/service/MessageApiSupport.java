package com.rag.message.message.api.service;

import com.rag.common.exception.BusinessException;
import com.rag.message.message.api.dto.AdminSendNotificationResp;
import com.rag.message.message.api.dto.ChatMessageItem;
import com.rag.message.message.api.dto.ConversationItem;
import com.rag.message.message.api.dto.ConversationListResp;
import com.rag.message.message.api.dto.ConversationMessagesResp;
import com.rag.message.message.api.dto.NotificationItem;
import com.rag.message.message.api.dto.NotificationListResp;
import com.rag.message.message.api.dto.SendChatResp;
import com.rag.message.message.api.dto.UnreadSummaryResp;
import com.rag.message.message.api.interceptor.AbstractMessageJwtAuthInterceptor;
import com.rag.message.message.common.errmsg.MessageErrorCode;
import com.rag.message.message.common.errmsg.MessageErrorMessages;
import com.rag.message.message.common.errmsg.MessageGrpcError;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * message-api 跨 13 个 Service 共享的行为 —— 对应 Go 端
 * {@code service/message/api/internal/logic/shared/helper.go}（161 行）。
 *
 * <p><b>Go 出处逐条对应</b>：
 * <table border="1">
 *   <caption>helper.go → 本类</caption>
 *   <tr><th>Go</th><th>Java</th></tr>
 *   <tr><td>{@code UserIDFromContext}</td><td>{@link #userId}</td></tr>
 *   <tr><td>{@code RPCError}</td><td>{@link #rpcError}</td></tr>
 *   <tr><td>{@code ToNotificationListResp}</td><td>{@link #toNotificationListResp}</td></tr>
 *   <tr><td>{@code ToUnreadSummaryResp}</td><td>{@link #toUnreadSummaryResp}</td></tr>
 *   <tr><td>{@code ToConversationListResp}</td><td>{@link #toConversationListResp}</td></tr>
 *   <tr><td>{@code ToConversationMessagesResp}</td><td>{@link #toConversationMessagesResp}</td></tr>
 *   <tr><td>{@code ToSendChatResp}</td><td>{@link #toSendChatResp}</td></tr>
 *   <tr><td>{@code AdminNotificationKind}</td><td>{@link #adminNotificationKind}</td></tr>
 *   <tr><td>{@code InvalidContextError}</td><td><b>不迁</b>：Go 侧全树 grep 零调用方（见下）</td></tr>
 * </table>
 *
 * <p><b>关于 {@code InvalidContextError}</b>：Go 端定义了
 * {@code fmt.Errorf("%s not found in context", label)}，但
 * {@code api/internal/logic/**} 13 个 logic 里<b>没有任何一处调用</b>（grep 零命中）。
 * 按「零消费者的代码不能算已对齐」的既有处置（users 域 DEAD-CODE-001 先例），
 * 本次<b>不创建</b>对应物 —— 建一个没人调用的方法等于给后人第二个入口。
 *
 * <p><b>框架职责</b>：Spring {@code @Component}；proto → DTO 的映射逻辑收口在<b>唯一一份</b>，
 * 13 个 Service 里不重复出现字段拷贝（Go 侧也是收口在 shared/helper.go 的）。
 */
@Component
public class MessageApiSupport {

    /**
     * 对应 Go {@code shared.UserIDFromContext}：
     * <pre>
     * func UserIDFromContext(ctx context.Context) (int64, error) {
     *     userID, ok := ctx.Value("userId").(json.Number)
     *     if !ok { return 0, NewErrCode(ErrorUnauthorized) }
     *     uid, err := userID.Int64()
     *     if err != nil { return 0, NewErrCode(ErrorUnauthorized) }
     *     return uid, nil
     * }
     * </pre>
     *
     * <h3>三条必须逐条复刻的规则</h3>
     * <ol>
     *   <li><b>claim 不存在 ⇒ 5004</b>（不是 401）。鉴权层已经验过签名，
     *       能走到这里说明 token 有效；Go 把它归成「业务上的未鉴权」，
     *       输出 <b>HTTP 200 + {code:5004}</b>。若改成 401 就与 Go 不同。</li>
     *   <li><b>claim 不是数字 ⇒ 5004</b>：Go 的类型断言目标是 {@code json.Number}，
     *       字符串 claim（{@code "userId":"abc"}）直接落 {@code !ok} 分支。</li>
     *   <li><b>数字不是整数 ⇒ 5004</b>：Go 的 {@code json.Number.Int64()}
     *       内部是 {@code strconv.ParseInt(s,10,64)}，对 {@code "1.5"} / {@code "1e2"}
     *       一律失败。这里因此要求 <b>整型</b>（Byte/Short/Integer/Long/BigInteger），
     *       {@code Double}/{@code BigDecimal} 一律拒 —— 否则 RAG 会把 Go 判 5004 的输入
     *       静默截断成合法 uid，那是实打实的越权风险。</li>
     * </ol>
     *
     * <p>注意本方法<b>不</b>拒绝 {@code uid <= 0}：Go 侧同样不拒绝，
     * {@code uid=0} 会原样透传给 message-rpc，由 rpc 的存在性校验判 5101/5102。
     * api 层提前拒会把业务码换成 5004。
     */
    public long userId(HttpServletRequest request) {
        Object claim = request.getAttribute(AbstractMessageJwtAuthInterceptor.CLAIM_ATTRIBUTE);
        if (claim instanceof Byte b) {
            return b.longValue();
        }
        if (claim instanceof Short s) {
            return s.longValue();
        }
        if (claim instanceof Integer i) {
            return i.longValue();
        }
        if (claim instanceof Long l) {
            return l;
        }
        if (claim instanceof java.math.BigInteger bi && bi.bitLength() < 64) {
            return bi.longValue();
        }
        throw new BusinessException(
                MessageErrorCode.ERROR_UNAUTHORIZED.getCode(),
                MessageErrorMessages.getMsg(MessageErrorCode.ERROR_UNAUTHORIZED.getCode()));
    }

    /**
     * 对应 Go {@code shared.RPCError}：
     * <pre>
     * func RPCError(err error) error {
     *     if err == nil { return nil }
     *     code := messagecommon.BizCodeFromError(err)
     *     if st, ok := status.FromError(err); ok &amp;&amp; st.Message() != "" {
     *         return messagecommon.NewErrCodeMsg(code, st.Message())
     *     }
     *     return messagecommon.NewErrCode(code)
     * }
     * </pre>
     *
     * <p><b>两条语义要分开看</b>：
     * <ul>
     *   <li><b>code</b> 由 {@link MessageGrpcError#bizCodeFromError} 决定 ——
     *       先按 gRPC status 的 description 反查业务码表，查不到再按 gRPC code class 兜底。
     *       Go 与 RAG 共用同一张表与同一套规则（message-common 已有 focused tests）。</li>
     *   <li><b>msg</b> 优先用 gRPC 的 description <b>原文</b>；只有在拿不到非空 description 时
     *       才退回按 code 查表。Go 侧 message-rpc 把业务码藏在 description 里
     *       （{@code GRPCError(codes.X, bizCode)} → {@code status.Error(X, GetErrMsg(bizCode))}），
     *       所以「有非空 description」是常态，msg 就是那张表里的英文文案。</li>
     * </ul>
     *
     * <p>Go 的 {@code status.FromError} 对非 status 错误返回 {@code ok=false}，
     * 此时走 {@code NewErrCode(code)}（msg 查表）。本方法用
     * {@link #statusDescription} 返回 {@code null} 来表达同一件事。
     */
    public BusinessException rpcError(Throwable err) {
        int code = MessageGrpcError.bizCodeFromError(err);
        String description = statusDescription(err);
        if (description != null && !description.isEmpty()) {
            return new BusinessException(code, description);
        }
        return new BusinessException(code, MessageErrorMessages.getMsg(code));
    }

    /**
     * 取 gRPC status 的 description；不是 status 异常时返回 {@code null}。
     *
     * <p>对应 Go {@code status.FromError(err)} 的 {@code ok} 判定。
     */
    private static String statusDescription(Throwable err) {
        if (err instanceof StatusRuntimeException sre) {
            return sre.getStatus().getDescription();
        }
        if (err instanceof StatusException se) {
            return se.getStatus().getDescription();
        }
        return null;
    }

    /**
     * 对应 Go {@code shared.AdminNotificationKind}：
     * <pre>
     * func AdminNotificationKind(broadcast bool, raw int32) pb.NotificationKind {
     *     if raw &gt; 0 { return pb.NotificationKind(raw) }
     *     if broadcast { return pb.NotificationKind_ADMIN_BROADCAST }
     *     return pb.NotificationKind_ADMIN_NOTICE
     * }
     * </pre>
     *
     * <p><b>三条规则</b>：{@code raw > 0} 原值优先（客户端可指定 ARTICLE_COMMENT 等）；
     * 否则 broadcast ⇒ {@code ADMIN_BROADCAST}(1)，非广播 ⇒ {@code ADMIN_NOTICE}(2)。
     * {@code raw < 0} 与 {@code raw == 0} 一样落到推断分支（Go 的判断是 {@code raw > 0}，
     * 不是 {@code raw != 0}）。
     *
     * <p><b>返回原始 int 而不是 proto 枚举对象</b>：{@code raw > 0} 可能是
     * protobuf 尚未定义的值（如 99），Java 的 {@code NotificationKind.forNumber(99)}
     * 会返回 {@code null}，而 Go 的 proto3 开放枚举允许未知值。
     * 因此 Service 侧必须用 {@code setKindValue(int)} 而不是 {@code setKind(enum)}。
     */
    public int adminNotificationKind(boolean broadcast, int raw) {
        if (raw > 0) {
            return raw;
        }
        if (broadcast) {
            return com.rag.message.message.proto.NotificationKind.ADMIN_BROADCAST.getNumber();
        }
        return com.rag.message.message.proto.NotificationKind.ADMIN_NOTICE.getNumber();
    }

    /**
     * 对应 Go {@code recipientIDs}：
     * <pre>
     * func recipientIDs(req *types.AdminSendNotificationReq) []int64 {
     *     if req.Broadcast { return nil }
     *     return []int64{req.TargetId}
     * }
     * </pre>
     *
     * <p>广播返回 <b>空列表</b>（Go 的 nil slice 在 protobuf 里就是「不重复 recipients」），
     * 定向返回单元素列表。调用方（Service）已在前面完成
     * {@code !broadcast && target_id <= 0 ⇒ 5003} 的校验，因此这里不会产出空 target。
     */
    public List<Long> recipientIds(boolean broadcast, Long targetId) {
        if (broadcast) {
            return Collections.emptyList();
        }
        List<Long> ids = new ArrayList<>(1);
        ids.add(targetId == null ? 0L : targetId);
        return ids;
    }

    // ============== proto → DTO 映射（对应 shared/helper.go 的 5 个 To*Resp） ==============

    /** 对应 Go {@code shared.ToNotificationListResp}。 */
    public NotificationListResp toNotificationListResp(
            com.rag.message.message.proto.NotificationListResp in) {
        NotificationListResp out = new NotificationListResp();
        for (com.rag.message.message.proto.NotificationItem item : in.getItemsList()) {
            NotificationItem dto = new NotificationItem();
            dto.setId(item.getId());
            dto.setRecipientId(item.getRecipientId());
            dto.setSenderId(item.getSenderId());
            // Go: int32(item.SenderRole) —— 取原始数值，不是枚举名
            dto.setSenderRole(item.getSenderRoleValue());
            dto.setKind(item.getKindValue());
            dto.setTitle(item.getTitle());
            dto.setContent(item.getContent());
            dto.setRead(item.getIsRead());
            dto.setCreatedAt(item.getCreatedAt());
            // Go: Extra: item.Extra —— nil map 会序列化成 "extra":null，这里同样不排除 null
            dto.setExtra(item.getExtraCount() == 0 ? null : new java.util.LinkedHashMap<>(item.getExtraMap()));
            out.getItems().add(dto);
        }
        out.setTotal(in.getTotal());
        out.setUnreadCount(in.getUnreadCount());
        return out;
    }

    /** 对应 Go {@code shared.ToUnreadSummaryResp}。 */
    public UnreadSummaryResp toUnreadSummaryResp(
            com.rag.message.message.proto.UnreadSummaryResp in) {
        UnreadSummaryResp out = new UnreadSummaryResp();
        out.setNotificationUnread(in.getNotificationUnread());
        out.setConversationUnread(in.getConversationUnread());
        out.setTotalUnread(in.getTotalUnread());
        return out;
    }

    /** 对应 Go {@code shared.ToConversationListResp}。 */
    public ConversationListResp toConversationListResp(
            com.rag.message.message.proto.ConversationListResp in) {
        ConversationListResp out = new ConversationListResp();
        for (com.rag.message.message.proto.ConversationItem item : in.getItemsList()) {
            ConversationItem dto = new ConversationItem();
            dto.setConversationId(item.getConversationId());
            dto.setPeerId(item.getPeerId());
            dto.setPeerRole(item.getPeerRoleValue());
            dto.setPeerName(item.getPeerName());
            dto.setPeerEmail(item.getPeerEmail());
            dto.setStatus(item.getStatusValue());
            dto.setUnreadCount(item.getUnreadCount());
            dto.setLatestMessage(item.getLatestMessage());
            dto.setLatestMessageAt(item.getLatestMessageAt());
            dto.setUpdatedAt(item.getUpdatedAt());
            dto.setCanSend(item.getCanSend());
            out.getItems().add(dto);
        }
        out.setTotal(in.getTotal());
        out.setUnreadCount(in.getUnreadCount());
        return out;
    }

    /** 对应 Go {@code shared.ToConversationMessagesResp}。 */
    public ConversationMessagesResp toConversationMessagesResp(
            com.rag.message.message.proto.ConversationMessageListResp in) {
        ConversationMessagesResp out = new ConversationMessagesResp();
        for (com.rag.message.message.proto.ChatMessageItem item : in.getItemsList()) {
            out.getItems().add(toChatMessageItem(item));
        }
        out.setStatus(in.getStatusValue());
        out.setCanSend(in.getCanSend());
        out.setPendingSenderId(in.getPendingSenderId());
        out.setPendingSenderRole(in.getPendingSenderRoleValue());
        return out;
    }

    /**
     * 对应 Go {@code shared.ToSendChatResp}。
     *
     * <p><b>{@code message} 不会被置 null</b>：Go 的写法是
     * <pre>
     *   message := types.ChatMessageItem{}
     *   if in.Message != nil { message = ... }
     * </pre>
     * 即 proto 消息缺失时用<b>全零值</b>占位。Java 侧 DTO 的 {@code message}
     * 字段已默认 new，一个 proto 的 {@code hasMessage()} 为 false 时保留零值对象。
     */
    public SendChatResp toSendChatResp(com.rag.message.message.proto.SendChatMessageResp in) {
        SendChatResp out = new SendChatResp();
        out.setConversationId(in.getConversationId());
        if (in.hasMessage()) {
            out.setMessage(toChatMessageItem(in.getMessage()));
        }
        out.setStatus(in.getStatusValue());
        out.setCanSend(in.getCanSend());
        return out;
    }

    /** proto ChatMessageItem → DTO（两个 To*Resp 共用，Go 侧也是同一段字段拷贝）。 */
    private static ChatMessageItem toChatMessageItem(
            com.rag.message.message.proto.ChatMessageItem in) {
        ChatMessageItem dto = new ChatMessageItem();
        dto.setId(in.getId());
        dto.setConversationId(in.getConversationId());
        dto.setSenderId(in.getSenderId());
        dto.setSenderRole(in.getSenderRoleValue());
        dto.setRecipientId(in.getRecipientId());
        dto.setRecipientRole(in.getRecipientRoleValue());
        dto.setContent(in.getContent());
        dto.setRead(in.getIsRead());
        dto.setCreatedAt(in.getCreatedAt());
        return dto;
    }

    /** 对应 Go {@code return &types.AdminSendNotificationResp{Affected: rpcResp.Affected}, nil}。 */
    public AdminSendNotificationResp toAdminSendNotificationResp(
            com.rag.message.message.proto.SendNotificationResp in) {
        AdminSendNotificationResp out = new AdminSendNotificationResp();
        out.setAffected(in.getAffected());
        return out;
    }
}