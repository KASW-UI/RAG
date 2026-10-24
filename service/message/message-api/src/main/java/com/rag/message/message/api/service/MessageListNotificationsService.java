package com.rag.message.message.api.service;

import com.rag.message.message.api.client.MessageRpcClient;
import com.rag.message.message.api.dto.NotificationListResp;
import com.rag.message.message.api.dto.PageReq;
import com.rag.message.message.api.metrics.MessageApiMetrics;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * 通知列表 —— 对应 Go
 * {@code logic/message/listnotificationslogic.go} 的 {@code ListNotificationsLogic}。
 *
 * <p><b>Go body</b>：
 * <pre>
 *   rpcResp, err := l.svcCtx.MessageRpc.ListNotifications(l.ctx, &amp;pb.NotificationListReq{
 *       UserId:     uid,      ← 不是 OperatorId：本端点只按收件人过滤，没有操作人概念
 *       Offset:     req.Offset,
 *       Limit:      req.Limit,
 *       UnreadOnly: req.UnreadOnly,
 *   })
 * </pre>
 *
 * <p>注意与 {@code ListConversations} 的字段差异：这里是 {@code UserId} 而非
 * {@code OperatorId + OperatorRole}。proto 里两个 message 的形状不同，
 * 迁移时不能因为「都是列表」就套同一个请求对象。
 *
 * <p>{@code unread_only=false} 表示不限；分页归一化同样在 rpc 侧。
 */
@Service
public class MessageListNotificationsService extends AbstractMessageApiService {

    /** Go: {@code const route = "/message/v1/notifications/list"}。 */
    static final String ROUTE = "/message/v1/notifications/list";

    public MessageListNotificationsService(MessageRpcClient rpcClient,
                                            MessageApiSupport support,
                                            MessageApiMetrics metrics) {
        super(rpcClient, support, metrics);
    }

    public NotificationListResp listNotifications(PageReq req, HttpServletRequest httpRequest) {
        return invoke(ROUTE, () -> {
            long uid = requireIdentity(ROUTE, REASON_USER_ID_MISSING, httpRequest);
            var builder = com.rag.message.message.proto.NotificationListReq.newBuilder()
                    .setUserId(uid)
                    .setOffset(req.offset())
                    .setLimit(req.limit())
                    .setUnreadOnly(req.unreadOnly());
            try {
                return support().toNotificationListResp(rpc().messageRpc().listNotifications(builder.build()));
            } catch (StatusRuntimeException e) {
                throw support().rpcError(e);
            }
        });
    }
}
