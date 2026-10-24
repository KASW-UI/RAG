package com.rag.message.message.api.service;

import com.rag.message.message.api.client.MessageRpcClient;
import com.rag.message.message.api.dto.AdminSendChatReq;
import com.rag.message.message.api.dto.SendChatResp;
import com.rag.message.message.api.metrics.MessageApiMetrics;
import com.rag.message.message.proto.SendChatMessageReq;
import com.rag.message.message.proto.SenderRole;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * 管理员发私信 —— 对应 Go
 * {@code logic/messageadmin/sendadminchatmessagelogic.go} 的 {@code SendAdminChatMessageLogic}。
 *
 * <p><b>Go body</b>：
 * <pre>
 *   rpcResp, err := l.svcCtx.MessageRpc.SendChatMessage(l.ctx, &amp;pb.SendChatMessageReq{
 *       SenderId:       uid,
 *       SenderRole:     pb.SenderRole_ADMIN,
 *       RecipientId:    req.RecipientId,
 *       RecipientRole:  pb.SenderRole_USER,      ← 收件人恒为 USER
 *       ConversationId: req.ConversationId,
 *       Content:        req.Content,
 *   })
 * </pre>
 *
 * <p><b>{@code RecipientRole} 硬编码 USER 是行为</b>：管理员只能给用户发私信，
 * 不能给管理员发（Go 侧写死）。与 {@link MessageSendUserChatService} 的差别
 * 就是 sender 角色不同 + {@code recipient_id} 必填。
 *
 * <p><b>「只有管理员能发起会话」</b>（{@code ErrorAdminOnlyInit}=5205）由 rpc 判定：
 * 用户发起 PENDING 会话后再发第二条会被拒，而管理员可以。
 * api 层不预判。
 */
@Service
public class MessageAdminSendChatService extends AbstractMessageApiService {

    /** Go: {@code const route = "/message/v1/admin/conversations/send"}。 */
    static final String ROUTE = "/message/v1/admin/conversations/send";

    public MessageAdminSendChatService(MessageRpcClient rpcClient,
                                       MessageApiSupport support,
                                       MessageApiMetrics metrics) {
        super(rpcClient, support, metrics);
    }

    public SendChatResp sendAdminChatMessage(AdminSendChatReq req, HttpServletRequest httpRequest) {
        return invoke(ROUTE, () -> {
            long uid = requireIdentity(ROUTE, REASON_ADMIN_ID_MISSING, httpRequest);

            SendChatMessageReq.Builder builder = SendChatMessageReq.newBuilder()
                    .setSenderId(uid)
                    .setSenderRoleValue(roleValue(SenderRole.ADMIN))
                    .setRecipientRoleValue(roleValue(SenderRole.USER))
                    .setRecipientId(req.recipientId())
                    .setContent(req.content() == null ? "" : req.content());
            if (req.conversationId() != null) {
                builder.setConversationId(req.conversationId());
            }
            try {
                return support().toSendChatResp(rpc().messageRpc().sendChatMessage(builder.build()));
            } catch (StatusRuntimeException e) {
                throw support().rpcError(e);
            }
        });
    }
}
