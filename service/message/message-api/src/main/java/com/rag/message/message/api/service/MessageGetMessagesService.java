package com.rag.message.message.api.service;

import com.rag.message.message.api.client.MessageRpcClient;
import com.rag.message.message.api.dto.ConversationMessagesReq;
import com.rag.message.message.api.dto.ConversationMessagesResp;
import com.rag.message.message.api.metrics.MessageApiMetrics;
import com.rag.message.message.proto.SenderRole;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * 会话消息列表（user 侧） —— 对应 Go
 * {@code logic/message/getconversationmessageslogic.go} 的 {@code GetConversationMessagesLogic}。
 *
 * <p><b>Go body</b>：同样是「取 uid → 透传 conversation_id / offset / limit →
 * operator_role=USER → RPCError → ToConversationMessagesResp」。
 *
 * <p><b>本端点没有额外的 ownership 检查</b>：会话是否属于该用户由 message-rpc 判定
 * （Go {@code rpc/internal/logic/getconversationmessageslogic.go} 会校验
 * operator 是否为会话参与方，失败返回 {@code ErrorConversationMiss}=5202）。
 * api 层<b>不得</b>提前查库判断 —— 那会引入 Go 侧不存在的额外读与 TOCTOU 窗口。
 */
@Service
public class MessageGetMessagesService extends AbstractMessageApiService {

    /** Go: {@code const route = "/message/v1/conversations/messages"}。 */
    static final String ROUTE = "/message/v1/conversations/messages";

    public MessageGetMessagesService(MessageRpcClient rpcClient,
                                     MessageApiSupport support,
                                     MessageApiMetrics metrics) {
        super(rpcClient, support, metrics);
    }

    public ConversationMessagesResp getConversationMessages(ConversationMessagesReq req,
                                                             HttpServletRequest httpRequest) {
        return invoke(ROUTE, () -> {
            long uid = requireIdentity(ROUTE, REASON_USER_ID_MISSING, httpRequest);
            return getConversationMessages(ROUTE, REASON_USER_ID_MISSING, uid,
                    roleValue(SenderRole.USER), req);
        });
    }
}
