package com.rag.message.message.api.service;

import com.rag.message.message.api.client.MessageRpcClient;
import com.rag.message.message.api.dto.ConversationMessagesReq;
import com.rag.message.message.api.dto.ConversationMessagesResp;
import com.rag.message.message.api.metrics.MessageApiMetrics;
import com.rag.message.message.proto.SenderRole;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * 会话消息列表（admin 侧） —— 对应 Go
 * {@code logic/messageadmin/getadminconversationmessageslogic.go} 的
 * {@code GetAdminConversationMessagesLogic}。
 *
 * <p>与 user 侧的差别：{@code OperatorRole=ADMIN} + reject reason
 * {@code "admin_id_missing"}。Go 侧两个 logic 其余部分逐字相同。
 *
 * <p><b>路由</b>：{@code /message/v1/admin/conversations/messages}（POST）。
 */
@Service
public class MessageAdminGetMessagesService extends AbstractMessageApiService {

    /** Go: {@code const route = "/message/v1/admin/conversations/messages"}。 */
    static final String ROUTE = "/message/v1/admin/conversations/messages";

    public MessageAdminGetMessagesService(MessageRpcClient rpcClient,
                                          MessageApiSupport support,
                                          MessageApiMetrics metrics) {
        super(rpcClient, support, metrics);
    }

    public ConversationMessagesResp getAdminConversationMessages(ConversationMessagesReq req,
                                                                 HttpServletRequest httpRequest) {
        return invoke(ROUTE, () -> {
            long uid = requireIdentity(ROUTE, REASON_ADMIN_ID_MISSING, httpRequest);
            return getConversationMessages(ROUTE, REASON_ADMIN_ID_MISSING, uid,
                    roleValue(SenderRole.ADMIN), req);
        });
    }
}
