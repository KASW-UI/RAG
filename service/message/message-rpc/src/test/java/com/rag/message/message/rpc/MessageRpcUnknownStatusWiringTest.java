package com.rag.message.message.rpc;

import com.rag.message.message.common.errmsg.MessageErrorCode;
import com.rag.message.message.common.errmsg.MessageGrpcError;
import com.rag.message.message.proto.MarkNotificationReq;
import com.rag.message.message.proto.MessageServiceGrpc;
import com.rag.message.message.rpc.service.MessageDomainService;
import com.rag.message.message.rpc.service.impl.MessageRpcServiceImpl;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * <b>DRIFT-MSG-V-024 守门：DB / 未知异常必须走 UNKNOWN，且不得回显原始错误文本。</b>
 *
 * <h3>Go 侧事实（消费点，不是声明点）</h3>
 * <pre>
 *   service/message/rpc/internal/logic/**：共 19 处 ObserveDBError 之后
 *       `return nil, err` —— **原样返回普通 error，不包 status**。
 *   ⇒ grpc-go v1.81.1 的 processUnaryRPC 走 status.FromContextError(appErr)
 *     ⇒ **codes.Unknown + 原始错误文本**。
 *   ⇒ go-zero 的熔断判据 codes.Acceptable(err)（zrpc/internal/codes/accept.go:9-17）
 *     只把这 6 个码判为失败：DEADLINE_EXCEEDED / INTERNAL / UNAVAILABLE /
 *     DATA_LOSS / UNIMPLEMENTED / RESOURCE_EXHAUSTED
 *     ⇒ **UNKNOWN 不在其中 ⇒ Go 的 DB 故障不触发熔断**。
 * </pre>
 *
 * <h3>改之前的 RAG 行为</h3>
 * <p>{@code MessageRpcServiceImpl#invoke} 把 RuntimeException 统一转成
 * {@code INTERNAL + "internal server error"} ⇒ ① 客户端可见码与 Go 不同；
 * ② <b>INTERNAL 在那 6 码名单里 ⇒ RAG 的熔断器会把 DB 故障记成失败</b>
 * ⇒ 同一个 DB 故障，两侧的开路决策分岔。
 *
 * <h3>为什么 description 必须是 null（而不是抄 Go 的原始错误文本）</h3>
 * <p>Go 侧的 api {@code shared.RPCError} 会把 gRPC description <b>原文</b>塞进 HTTP envelope 的 msg
 * ⇒ Go 客户端能直接看到 SQL 错误文本（<b>信息泄露</b>）。
 * 本仓 {@code DRIFT-MSG-V-003} 已就这一点<b>签字保留 RAG 侧</b>（envelope 只回码表文案）。
 * ⇒ 若这里把原始文本抄过来，等于在签字之外又引入一次泄露。
 * ⇒ description 留空 ⇒ api 侧走「反查未命中 ⇒ 按 status code class 兜底」那一支
 * ⇒ 业务码与 Go 一致（1015），且 envelope 的 msg 由码表给出。
 */
@DisplayName("DRIFT-MSG-V-024：DB / 未知异常必须走 UNKNOWN（与 Go 的熔断判据对齐）且不回显原始错误文本")
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class MessageRpcUnknownStatusWiringTest {

    private static final String SERVER_NAME = "unknown-status-message-rpc";

    /** Go 侧 DB 失败的原始错误文本（GORM/pq 的真实形态）。 */
    private static final String GO_RAW_DB_ERROR =
            "ERROR: relation \"message_notification\" does not exist (SQLSTATE 42P01)";

    private Server server;
    private ManagedChannel channel;

    private void startServer(RuntimeException thrownByDomain) throws Exception {
        MessageDomainService domain = mock(MessageDomainService.class);
        when(domain.markNotificationRead(any())).thenThrow(thrownByDomain);

        MessageRpcServiceImpl impl = new MessageRpcServiceImpl(domain);

        server = InProcessServerBuilder.forName(SERVER_NAME).directExecutor()
                .addService(new MessageServiceGrpc.MessageServiceImplBase() {
                    @Override
                    public void markNotificationRead(MarkNotificationReq request,
                                                     StreamObserver<com.rag.message.message.proto.BaseResp> observer) {
                        impl.markNotificationRead(request, observer);
                    }
                })
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(SERVER_NAME).directExecutor().build();
    }

    @AfterEach
    void stopServer() throws Exception {
        if (channel != null) {
            channel.shutdownNow();
            channel.awaitTermination(5, TimeUnit.SECONDS);
        }
        if (server != null) {
            server.shutdownNow();
        }
    }

    private StatusRuntimeException call() {
        MessageServiceGrpc.MessageServiceBlockingStub stub =
                MessageServiceGrpc.newBlockingStub(channel).withDeadlineAfter(10, TimeUnit.SECONDS);
        return assertThrows(StatusRuntimeException.class,
                () -> stub.markNotificationRead(MarkNotificationReq.newBuilder().setNotificationId(1L).build()));
    }

    @Test
    @DisplayName("🔴 DB 异常 ⇒ 客户端看到 UNKNOWN（不是 INTERNAL）")
    void dbFailureMustSurfaceAsUnknown() throws Exception {
        startServer(new IllegalStateException(GO_RAW_DB_ERROR));

        StatusRuntimeException e = call();

        assertEquals(Status.Code.UNKNOWN, e.getStatus().getCode(),
                "Go: 普通 error ⇒ grpc-go status.FromContextError ⇒ codes.Unknown。"
                        + "RAG 若给 INTERNAL ⇒ INTERNAL 落在 Go 那 6 码失败名单里 "
                        + "⇒ RAG 的熔断器会比 Go 灵敏得多（这正是 DRIFT-MSG-V-024）。"
                        + "实际=" + e.getStatus().getCode());
    }

    @Test
    @DisplayName("🔴 description 必须是 null ⇒ 原始错误文本不得进 wire")
    void rawErrorTextMustNotLeakIntoTheStatus() throws Exception {
        startServer(new IllegalStateException(GO_RAW_DB_ERROR));

        StatusRuntimeException e = call();

        assertNull(e.getStatus().getDescription(),
                "description 不得携带原始错误文本（Go 的 api 会把它原文塞进 envelope ⇒ 信息泄露；"
                        + "本仓 DRIFT-MSG-V-003 已签字保留 RAG 侧的「只回码表文案」）。"
                        + "实际 description=" + e.getStatus().getDescription());
    }

    @Test
    @DisplayName("✅ api 侧业务码必须是 1015（与 Go 的 CodeServerBusy 一致）")
    void apiSideBizCodeMustBeGoServerBusy() throws Exception {
        startServer(new IllegalStateException(GO_RAW_DB_ERROR));

        StatusRuntimeException e = call();

        int bizCode = MessageGrpcError.bizCodeFromError(e);
        assertEquals(MessageErrorCode.CODE_SERVER_BUSY.getCode(), bizCode,
                "Go: BizCodeFromError 对非 status 错误走 status.FromError ⇒ ok=false ⇒ "
                        + "直接返回 CodeServerBusy(1015)；RAG 侧必须落在同一个码上。"
                        + "实际=" + bizCode);
        assertEquals(1015, bizCode, "Go: CodeServerBusy = 1015");
    }

    @Test
    @DisplayName("✅ 业务错误（StatusRuntimeException）仍然原样转发，不被兜底码覆盖")
    void businessErrorsAreStillForwardedVerbatim() throws Exception {
        startServer(new StatusRuntimeException(
                Status.NOT_FOUND.withDescription("notification not found")));

        StatusRuntimeException e = call();

        assertEquals(Status.Code.NOT_FOUND, e.getStatus().getCode(),
                "业务错误必须走 catch (StatusRuntimeException) 那一支，原样转发。");
        assertNotNull(e.getStatus().getDescription());
        assertEquals("notification not found", e.getStatus().getDescription());
    }
}
