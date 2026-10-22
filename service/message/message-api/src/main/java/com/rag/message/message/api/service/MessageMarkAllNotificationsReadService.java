package com.rag.message.message.api.service;

import com.rag.message.message.api.client.MessageRpcClient;
import com.rag.message.message.api.dto.EmptyReq;
import com.rag.message.message.api.metrics.MessageApiMetrics;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * 全部通知标记已读 —— 对应 Go
 * {@code logic/message/markallnotificationsreadlogic.go} 的 {@code MarkAllNotificationsReadLogic}。
 *
 * <p><b>Go body</b>：
 * <pre>
 *   _, err = l.svcCtx.MessageRpc.MarkAllNotificationsRead(l.ctx, &amp;pb.MarkAllNotificationsReq{UserId: uid})
 *   return &amp;types.EmptyReq{}, nil
 * </pre>
 *
 * <p><b>请求 DTO 是空 struct</b>（Go handler 用 {@code var req types.EmptyReq}），
 * 所以本端点<b>不需要任何 body</b>；Java 侧对应
 * {@code @RequestBody(required = false) EmptyReq}，缺失即零值、零校验。
 *
 * <p><b>无条件成功</b>：即使当前用户没有未读通知，Go 也返回成功（受影响行数为 0 但不报错）。
 * api 层不额外查询「有没有未读」，避免多一次读与新的失败路径。
 */
@Service
public class MessageMarkAllNotificationsReadService extends AbstractMessageApiService {

    /** Go: {@code const route = "/message/v1/notifications/readall"}。 */
    static final String ROUTE = "/message/v1/notifications/readall";

    public MessageMarkAllNotificationsReadService(MessageRpcClient rpcClient,
                                                  MessageApiSupport support,
                                                  MessageApiMetrics metrics) {
        super(rpcClient, support, metrics);
    }

    public EmptyReq markAllNotificationsRead(HttpServletRequest httpRequest) {
        return invoke(ROUTE, () -> {
            long uid = requireIdentity(ROUTE, REASON_USER_ID_MISSING, httpRequest);
            var builder = com.rag.message.message.proto.MarkAllNotificationsReq.newBuilder()
                    .setUserId(uid);
            try {
                rpc().messageRpc().markAllNotificationsRead(builder.build());
            } catch (StatusRuntimeException e) {
                throw support().rpcError(e);
            }
            return new EmptyReq();
        });
    }
}
