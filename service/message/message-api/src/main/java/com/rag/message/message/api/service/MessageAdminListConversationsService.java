package com.rag.message.message.api.service;

import com.rag.message.message.api.client.MessageRpcClient;
import com.rag.message.message.api.dto.ConversationListResp;
import com.rag.message.message.api.dto.PageReq;
import com.rag.message.message.api.metrics.MessageApiMetrics;
import com.rag.message.message.proto.SenderRole;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * 会话列表（admin 侧） —— 对应 Go
 * {@code logic/messageadmin/listadminconversationslogic.go} 的 {@code ListAdminConversationsLogic}。
 *
 * <p><b>与 user 侧 {@link MessageListConversationsService} 的差别只有两处</b>：
 * <ol>
 *   <li>{@code OperatorRole: pb.SenderRole_ADMIN}（proto 字段相同，值不同）；</li>
 *   <li>reject reason 是 {@code "admin_id_missing"}（Go 字面量）。</li>
 * </ol>
 * 其余（RPC 方法、请求字段、响应映射）Go 侧两个 logic 逐字相同，
 * 因此复用了基类的 {@code listConversations}，避免同一段映射出现两份。
 *
 * <p><b>路由</b>：{@code /message/v1/admin/conversations/list}（POST）。
 */
@Service
public class MessageAdminListConversationsService extends AbstractMessageApiService {

    /** Go: {@code const route = "/message/v1/admin/conversations/list"}。 */
    static final String ROUTE = "/message/v1/admin/conversations/list";

    public MessageAdminListConversationsService(MessageRpcClient rpcClient,
                                                MessageApiSupport support,
                                                MessageApiMetrics metrics) {
        super(rpcClient, support, metrics);
    }

    public ConversationListResp listAdminConversations(PageReq req, HttpServletRequest httpRequest) {
        return invoke(ROUTE, () -> {
            long uid = requireIdentity(ROUTE, REASON_ADMIN_ID_MISSING, httpRequest);
            return listConversations(ROUTE, REASON_ADMIN_ID_MISSING, uid,
                    roleValue(SenderRole.ADMIN), req);
        });
    }
}
