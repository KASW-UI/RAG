package com.rag.message.message.api.service;

import com.rag.message.message.api.client.MessageRpcClient;
import com.rag.message.message.api.dto.UnreadSummaryResp;
import com.rag.message.message.api.metrics.MessageApiMetrics;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * 未读汇总 —— 对应 Go
 * {@code logic/message/getunreadsummarylogic.go} 的 {@code GetUnreadSummaryLogic}。
 *
 * <p><b>⚠️ 本端点是 13 个里唯一的 {@code GET}</b>：
 * <pre>
 *   Go 出处：routes.go 的
 *       { Method: http.MethodGet, Path: "/unread", Handler: message.GetUnreadSummaryHandler(serverCtx) }
 *   其余 12 个端点全是 POST。
 * </pre>
 * 因此 Controller 上必须用 {@code @GetMapping}，写成 POST 会让这一条路由 405 ——
 * 这是 L1 wire 契约，不是风格选择。
 *
 * <p><b>GET 也走 {@code httpx.Parse}</b>，但 {@code EmptyReq} 零字段 ⇒ 零校验；
 * query 参数在 Go 侧由 {@code ParseForm} 处理，对空 struct 无影响。
 * Java 侧不读任何 query 参数。
 *
 * <p>返回的三个计数字段都是 JSON <b>数字</b>（Go tag 无 {@code ,string}），
 * 见 {@link UnreadSummaryResp} 的说明。
 */
@Service
public class MessageGetUnreadSummaryService extends AbstractMessageApiService {

    /** Go: {@code const route = "/message/v1/unread"}。 */
    static final String ROUTE = "/message/v1/unread";

    public MessageGetUnreadSummaryService(MessageRpcClient rpcClient,
                                          MessageApiSupport support,
                                          MessageApiMetrics metrics) {
        super(rpcClient, support, metrics);
    }

    public UnreadSummaryResp getUnreadSummary(HttpServletRequest httpRequest) {
        return invoke(ROUTE, () -> {
            long uid = requireIdentity(ROUTE, REASON_USER_ID_MISSING, httpRequest);
            var builder = com.rag.message.message.proto.UnreadSummaryReq.newBuilder()
                    .setUserId(uid);
            try {
                return support().toUnreadSummaryResp(rpc().messageRpc().getUnreadSummary(builder.build()));
            } catch (StatusRuntimeException e) {
                throw support().rpcError(e);
            }
        });
    }
}
