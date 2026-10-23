package com.rag.message.message.rpc;

import com.rag.message.message.proto.BaseResp;
import com.rag.message.message.proto.MarkNotificationReq;
import com.rag.message.message.proto.MessageServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.serverfactory.GrpcServerConfigurer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>DRIFT-MSG-V-016（breaker 半边）生产装配的真 gRPC 证据</b>。
 *
 * <h3>为什么必须真起一个 server</h3>
 * <p>§3.3 的核心命题：<b>「Bean 存在」≠「机制生效」</b>。
 * 本类用的链是<b>生产的</b> {@code MessageRpcApplication#rpcInterceptorConfigurer}
 * （它内部走 {@code GoStyleRpcProtectionChain#build}），
 * 不是测试自己拼的链 ⇒ 能证明「熔断器真的挂在生产链上、真的会跳闸」。
 *
 * <h3>本类验什么</h3>
 * <ol>
 *   <li>连续失败 ⇒ breaker 打开 ⇒ 客户端真的看到
 *       {@code UNAVAILABLE + "circuit breaker is open"}（Go 的 wire 契约）；</li>
 *   <li>被熔断拒绝时<b>handler 不再执行</b>（Go: accept() 失败时 req() 从未被调用）。</li>
 * </ol>
 *
 * <h3>⚠️ 用例必须跨桶等待</h3>
 * <p>Go 的 {@code history()} <b>忽略当前未完成桶</b>
 * （{@code collection/rollingwindow.go:70-72}「ignore the current bucket, because of partial data」）
 * ⇒ 同一个 250ms 桶里的失败<b>根本不进窗口</b>。
 * ⇒ 不跨桶就断言「熔断没开」，会得到一个**假绿**。
 */
@DisplayName("DRIFT-MSG-V-016 breaker：生产装配下，连续失败真的会熔断并返回 Go 的 wire 契约")
@Timeout(value = 120, unit = TimeUnit.SECONDS)
class MessageRpcBreakerWiringTest {

    private static final String SERVER_NAME = "breaker-parity-message-rpc";

    private Server server;
    private ManagedChannel channel;
    private MessageServiceGrpc.MessageServiceBlockingStub stub;
    private final AtomicInteger handlerInvocations = new AtomicInteger();

    private MarkNotificationReq req() {
        return MarkNotificationReq.newBuilder().setNotificationId(1L).build();
    }

    /** 起一个 server；用的链是**生产的** rpcInterceptorConfigurer。 */
    private void startServer() throws Exception {
        // 生产装配：breaker 开、降载关（降载会短路，让本用例的断言不确定）
        com.rag.common.protection.GoStyleProtectionProperties props =
                new com.rag.common.protection.GoStyleProtectionProperties();
        props.getCpuShedding().setEnabled(false);

        ServerInterceptor business = new ServerInterceptor() {
            @Override
            public <ReqT, RespT> io.grpc.ServerCall.Listener<ReqT> interceptCall(
                    io.grpc.ServerCall<ReqT, RespT> call, Metadata headers,
                    io.grpc.ServerCallHandler<ReqT, RespT> next) {
                return next.startCall(call, headers);
            }
        };
        GrpcServerConfigurer configurer =
                new MessageRpcApplication().rpcInterceptorConfigurer(business, props);

        InProcessServerBuilder builder = InProcessServerBuilder.forName(SERVER_NAME).directExecutor();
        configurer.accept(builder);
        server = builder
                .addService(new MessageServiceGrpc.MessageServiceImplBase() {
                    @Override
                    public void markNotificationRead(MarkNotificationReq request,
                                                     StreamObserver<BaseResp> observer) {
                        handlerInvocations.incrementAndGet();
                        // 每次都失败 ⇒ 给熔断器喂失败
                        observer.onError(new StatusRuntimeException(Status.INTERNAL.withDescription("boom")));
                    }
                })
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(SERVER_NAME).directExecutor().build();
        stub = MessageServiceGrpc.newBlockingStub(channel).withDeadlineAfter(10, TimeUnit.SECONDS);
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

    @Test
    @DisplayName("🔴 连续失败跨过桶边界后，客户端真的看到 UNAVAILABLE + \"circuit breaker is open\"")
    void continuousFailuresEventuallyOpenTheBreaker() throws Exception {
        startServer();

        // ① 先跨桶喂失败（每个桶 30 次 + 睡一个桶 ⇒ 失败真正进入窗口）
        for (int round = 0; round < 3; round++) {
            for (int i = 0; i < 30; i++) {
                assertThrows(StatusRuntimeException.class, () -> stub.markNotificationRead(req()));
            }
            sleepOneBucket();
        }

        // ② 继续打，期望出现熔断拒绝
        boolean sawUnavailable = false;
        int callsAfterWarmup = 0;
        for (int i = 0; i < 80 && !sawUnavailable; i++) {
            StatusRuntimeException e = assertThrows(StatusRuntimeException.class,
                    () -> stub.markNotificationRead(req()));
            if (e.getStatus().getCode() == Status.Code.UNAVAILABLE) {
                sawUnavailable = true;
            }
            callsAfterWarmup++;
        }

        assertTrue(sawUnavailable,
                "连续失败后必须出现熔断拒绝（实际尝试 " + callsAfterWarmup + " 次）。"
                        + "若始终是 INTERNAL，说明 breaker 没挂在生产链上 —— "
                        + "这正是 §3.3「Bean 存在 ≠ 机制生效」要防的形态。");

        // wire 契约：Go: core/breaker/breaker.go:19 ErrServiceUnavailable 的文案
        StatusRuntimeException last = null;
        for (int i = 0; i < 20; i++) {
            last = assertThrows(StatusRuntimeException.class, () -> stub.markNotificationRead(req()));
            if (last.getStatus().getCode() == Status.Code.UNAVAILABLE) {
                break;
            }
        }
        assertEquals(Status.Code.UNAVAILABLE, last.getStatus().getCode());
        assertEquals("circuit breaker is open", last.getStatus().getDescription(),
                "Go: status.Error(gcodes.Unavailable, ErrServiceUnavailable.Error()) 的 description");
    }

    @Test
    @DisplayName("熔断拒绝时 handler 不再执行（Go: accept() 失败时 req() 从未被调用）")
    void rejectedCallsDoNotReachTheHandler() throws Exception {
        startServer();

        for (int round = 0; round < 3; round++) {
            for (int i = 0; i < 30; i++) {
                assertThrows(StatusRuntimeException.class, () -> stub.markNotificationRead(req()));
            }
            sleepOneBucket();
        }
        for (int i = 0; i < 40; i++) {
            assertThrows(StatusRuntimeException.class, () -> stub.markNotificationRead(req()));
        }

        int before = handlerInvocations.get();
        for (int i = 0; i < 10; i++) {
            assertThrows(StatusRuntimeException.class, () -> stub.markNotificationRead(req()));
        }
        int after = handlerInvocations.get();

        assertTrue(after - before < 10,
                "熔断打开后 handler 的调用次数必须显著少于调用次数（before=" + before
                        + " after=" + after + "）");
    }

    private static void sleepOneBucket() {
        try {
            Thread.sleep(260L);     // Go 的桶宽 250ms（googlebreaker.go:12-21）
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted", e);
        }
    }
}
