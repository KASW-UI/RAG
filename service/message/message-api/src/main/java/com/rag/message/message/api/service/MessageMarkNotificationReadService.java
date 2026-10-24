package com.rag.message.message.api.service;

import com.rag.message.message.api.client.MessageRpcClient;
import com.rag.message.message.api.dto.EmptyReq;
import com.rag.message.message.api.dto.MarkNotificationReq;
import com.rag.message.message.api.metrics.MessageApiMetrics;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * 标记单条通知已读 —— 对应 Go
 * {@code logic/message/marknotificationreadlogic.go} 的 {@code MarkNotificationReadLogic}。
 *
 * <p><b>Go body</b>：
 * <pre>
 *   _, err = l.svcCtx.MessageRpc.MarkNotificationRead(l.ctx, &amp;pb.MarkNotificationReq{
 *       UserId:         uid,          ← 归属条件随请求下发
 *       NotificationId: req.NotificationId,
 *   })
 * </pre>
 *
 * <p><b>⚠️ 不要在 api 层加「先查后写」</b>：归属判定必须在 rpc 端<b>单条原子 UPDATE</b> 的
 * WHERE 条件里（Go 出处 {@code rpc/internal/logic/marknotificationreadlogic.go}：
 * {@code UPDATE ... WHERE id = ? AND recipient_id = ?} + {@code RowsAffected == 0 → ErrRecordNotFound}）。
 * api 层先查一遍再写会引入 TOCTOU：两次调用之间通知可能已被他人操作，
 * 结果是「校验时属于我、写入时不属于我」。Go 从未这样做过。
 *
 * <p><b>通知不存在 / 不属于当前用户</b>：Go 侧两者都返回 5203
 * （{@code ErrorNotificationMiss}），api 层不区分。
 */
@Service
public class MessageMarkNotificationReadService extends AbstractMessageApiService {

    /** Go: {@code const route = "/message/v1/notifications/read"}。 */
    static final String ROUTE = "/message/v1/notifications/read";

    public MessageMarkNotificationReadService(MessageRpcClient rpcClient,
                                              MessageApiSupport support,
                                              MessageApiMetrics metrics) {
        super(rpcClient, support, metrics);
    }

    public EmptyReq markNotificationRead(MarkNotificationReq req, HttpServletRequest httpRequest) {
        return invoke(ROUTE, () -> {
            long uid = requireIdentity(ROUTE, REASON_USER_ID_MISSING, httpRequest);
            var builder = com.rag.message.message.proto.MarkNotificationReq.newBuilder()
                    .setUserId(uid)
                    .setNotificationId(req.notificationId());
            try {
                rpc().messageRpc().markNotificationRead(builder.build());
            } catch (StatusRuntimeException e) {
                throw support().rpcError(e);
            }
            return new EmptyReq();
        });
    }
}
