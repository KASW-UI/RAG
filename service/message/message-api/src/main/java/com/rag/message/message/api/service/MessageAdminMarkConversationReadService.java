package com.rag.message.message.api.service;

import com.rag.message.message.api.client.MessageRpcClient;
import com.rag.message.message.api.dto.EmptyReq;
import com.rag.message.message.api.dto.MarkConversationReadReq;
import com.rag.message.message.api.metrics.MessageApiMetrics;
import com.rag.message.message.proto.SenderRole;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * 标记会话已读（admin 侧） —— 对应 Go
 * {@code logic/messageadmin/markadminconversationreadlogic.go} 的
 * {@code MarkAdminConversationReadLogic}。
 *
 * <p>与 user 侧的差别：{@code OperatorRole=ADMIN} + reject reason
 * {@code "admin_id_missing"}。
 *
 * <p>注意「标记已读」的收件人角色由 rpc 端按 operator_role 推导，
 * api 层不传 recipient —— Go 侧同样只传三个字段。
 */
@Service
public class MessageAdminMarkConversationReadService extends AbstractMessageApiService {

    /** Go: {@code const route = "/message/v1/admin/conversations/read"}。 */
    static final String ROUTE = "/message/v1/admin/conversations/read";

    public MessageAdminMarkConversationReadService(MessageRpcClient rpcClient,
                                                   MessageApiSupport support,
                                                   MessageApiMetrics metrics) {
        super(rpcClient, support, metrics);
    }

    public EmptyReq markAdminConversationRead(MarkConversationReadReq req,
                                              HttpServletRequest httpRequest) {
        return invoke(ROUTE, () -> {
            long uid = requireIdentity(ROUTE, REASON_ADMIN_ID_MISSING, httpRequest);
            return markConversationRead(ROUTE, REASON_ADMIN_ID_MISSING, uid,
                    roleValue(SenderRole.ADMIN), req);
        });
    }
}
