package com.rag.message.message.api.service;

import com.rag.message.message.api.client.MessageRpcClient;
import com.rag.message.message.api.dto.SendChatResp;
import com.rag.message.message.api.dto.UserSendChatReq;
import com.rag.message.message.api.metrics.MessageApiMetrics;
import com.rag.message.message.proto.SendChatMessageReq;
import com.rag.message.message.proto.SenderRole;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * 用户发私信 —— 对应 Go
 * {@code logic/message/senduserchatmessagelogic.go} 的 {@code SendUserChatMessageLogic}。
 *
 * <p><b>Go body</b>：
 * <pre>
 *   rpcResp, err := l.svcCtx.MessageRpc.SendChatMessage(l.ctx, &amp;pb.SendChatMessageReq{
 *       SenderId:       uid,
 *       SenderRole:     pb.SenderRole_USER,
 *       RecipientId:    req.RecipientId,
 *       RecipientRole:  pb.SenderRole_USER,     ← 双向 USER
 *       ConversationId: req.ConversationId,
 *       Content:        req.Content,
 *   })
 * </pre>
 *
 * <p><b>{@code RecipientRole} 在本层硬编码为 USER</b>：Go 侧写死
 * {@code RecipientRole: pb.SenderRole_USER}，用户只能给用户发私信。
 * 硬编码是行为契约，不接受客户端指定。
 *
 * <p><b>本层不做任何业务判定</b>：自发私信（5201）、会话 PENDING 未回复（5204）、
 * 首条消息超 50 字符（5206）、双 id 皆空（5003）全部由 message-rpc 判定，
 * 本层只负责透传 + 把 gRPC 错误按 {@code shared.RPCError} 的规则转成业务码。
 * 在 api 层复制这些判定必然与 rpc 分叉（且 Go 从未这样做过）。
 */
@Service
public class MessageSendUserChatService extends AbstractMessageApiService {

    /** Go: {@code const route = "/message/v1/conversations/send"}。 */
    static final String ROUTE = "/message/v1/conversations/send";

    public MessageSendUserChatService(MessageRpcClient rpcClient,
                                      MessageApiSupport support,
                                      MessageApiMetrics metrics) {
        super(rpcClient, support, metrics);
    }

    public SendChatResp sendUserChatMessage(UserSendChatReq req, HttpServletRequest httpRequest) {
        return invoke(ROUTE, () -> {
            long uid = requireIdentity(ROUTE, REASON_USER_ID_MISSING, httpRequest);

            SendChatMessageReq.Builder builder = SendChatMessageReq.newBuilder()
                    .setSenderId(uid)
                    .setSenderRoleValue(roleValue(SenderRole.USER))
                    .setRecipientRoleValue(roleValue(SenderRole.USER))
                    .setContent(req.content() == null ? "" : req.content());
            // Go: RecipientId: req.RecipientId —— optional 缺席即零值，原样透传
            if (req.recipientId() != null) {
                builder.setRecipientId(req.recipientId());
            }
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
