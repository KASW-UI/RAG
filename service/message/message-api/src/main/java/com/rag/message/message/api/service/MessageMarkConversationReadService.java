package com.rag.message.message.api.service;

import com.rag.message.message.api.client.MessageRpcClient;
import com.rag.message.message.api.dto.EmptyReq;
import com.rag.message.message.api.dto.MarkConversationReadReq;
import com.rag.message.message.api.metrics.MessageApiMetrics;
import com.rag.message.message.proto.SenderRole;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * 标记会话已读（user 侧） —— 对应 Go
 * {@code logic/message/markconversationreadlogic.go} 的 {@code MarkConversationReadLogic}。
 *
 * <p><b>返回值是 {@code {}} 而不是 null</b>：Go {@code return &types.EmptyReq{}, nil}。
 *
 * <p><b>归属与幂等</b>：重复标记同一条已读在 Go 侧是<b>成功的 no-op</b>
 * （rpc 端按「会话参与方 + recipient_id」条件 UPDATE，重复执行不报错）；
 * api 层不做任何前置判断，也不引入「已读就不发」的短路 ——
 * 那会把「幂等的写」变成「有副作用的读」，与 Go 不同。
 */
@Service
public class MessageMarkConversationReadService extends AbstractMessageApiService {

    /** Go: {@code const route = "/message/v1/conversations/read"}。 */
    static final String ROUTE = "/message/v1/conversations/read";

    public MessageMarkConversationReadService(MessageRpcClient rpcClient,
                                              MessageApiSupport support,
                                              MessageApiMetrics metrics) {
        super(rpcClient, support, metrics);
    }

    public EmptyReq markConversationRead(MarkConversationReadReq req, HttpServletRequest httpRequest) {
        return invoke(ROUTE, () -> {
            long uid = requireIdentity(ROUTE, REASON_USER_ID_MISSING, httpRequest);
            return markConversationRead(ROUTE, REASON_USER_ID_MISSING, uid,
                    roleValue(SenderRole.USER), req);
        });
    }
}
