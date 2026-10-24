package com.rag.message.message.api.service;

import com.rag.message.message.api.client.MessageRpcClient;
import com.rag.message.message.api.dto.ConversationListResp;
import com.rag.message.message.api.dto.PageReq;
import com.rag.message.message.api.metrics.MessageApiMetrics;
import com.rag.message.message.proto.SenderRole;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * 会话列表（user 侧） —— 对应 Go
 * {@code service/message/api/internal/logic/message/listconversationslogic.go} 的
 * {@code ListConversationsLogic}。
 *
 * <p><b>Go body 逐行对照</b>：
 * <pre>
 *   uid, err := shared.UserIDFromContext(l.ctx)                 → requireIdentity
 *   rpcResp, err := l.svcCtx.MessageRpc.ListConversations(l.ctx, &amp;pb.ConversationListReq{
 *       OperatorId:   uid,
 *       OperatorRole: pb.SenderRole_USER,                      ← 与 admin 端点的唯一差别
 *       Offset:       req.Offset,
 *       Limit:        req.Limit,
 *   })
 *   if err != nil { return nil, shared.RPCError(err) }         → support.rpcError
 *   return shared.ToConversationListResp(rpcResp), nil
 * </pre>
 *
 * <p><b>分页参数不在本层归一化</b>：{@code offset=0 / limit=0} 原样透传，
 * 由 message-rpc 用 {@code message.list.default-limit=20 / max-limit=100} 处理
 * （Go 侧同样在 rpc 的 {@code helper.go}）。api 层提前兜会与 rpc 的规则分叉。
 *
 * <p>无缓存、无事务、无幂等键 —— Go 侧就是一次纯读 RPC，没有可丢失的本地状态。
 */
@Service
public class MessageListConversationsService extends AbstractMessageApiService {

    /** Go: {@code const route = "/message/v1/conversations/list"}。 */
    static final String ROUTE = "/message/v1/conversations/list";

    public MessageListConversationsService(MessageRpcClient rpcClient,
                                            MessageApiSupport support,
                                            MessageApiMetrics metrics) {
        super(rpcClient, support, metrics);
    }

    public ConversationListResp listConversations(PageReq req, HttpServletRequest httpRequest) {
        return invoke(ROUTE, () -> {
            long uid = requireIdentity(ROUTE, REASON_USER_ID_MISSING, httpRequest);
            return listConversations(ROUTE, REASON_USER_ID_MISSING, uid,
                    roleValue(SenderRole.USER), req);
        });
    }
}
