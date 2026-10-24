package com.rag.message.message.rpc;

import com.rag.message.message.proto.BaseResp;
import com.rag.message.message.proto.MarkNotificationReq;
import com.rag.message.message.proto.MessageServiceGrpc;
import com.rag.common.observability.GoStyleRecoverInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>DRIFT-MSG-V-016（recover 半边）真触发测试</b>：真 in-process gRPC server +
 * 必 panic 的 handler ⇒ 客户端真的看到 Go 侧那套契约。
 *
 * <h3>🔴 本类刻意放在 message-rpc 而不是 service/common</h3>
 * <p>① 它需要 {@code message-proto} 的类型（common 不依赖它）；
 * ② 更重要的是：它验的是 <b>生产装配</b> {@link com.rag.common.protection.GoStyleRpcProtectionChain#recoverThen}，
 * 而不是测试自己拼的链 —— 后者会出现「测试全绿、生产漏挂」的假闭环（§3.3）。
 *
 * <h3>为什么必须真触发，而不是断言 Bean 存在</h3>
 * <p>§3.3.3「断言 Bean 存在 ≠ 断言机制生效」在本条上的具体危险：
 * recover 拦截器<b>挂上了但没包住 {@code onHalfClose}</b> 时，
 * {@code getBean(ServerInterceptor.class)} 照样非空、{@code interceptCall} 照样被调用，
 * <b>而 handler 里的 panic 一条都兜不住</b> —— grpc-java 里 unary handler 是在
 * {@code onHalfClose()} 里执行的，不是在 {@code interceptCall} 里。
 *
 * <h3>Go 基准（{@code recoverinterceptor.go:24-42}）</h3>
 * <pre>
 *   func UnaryRecoverInterceptor(...) { defer handleCrash(func(r any){ err = toPanicError(ctx, r) }); … }
 *   func toPanicError(ctx, r any) error {
 *       logc.Errorf(ctx, "%+v\n\n%s", r, debug.Stack())
 *       return status.Errorf(codes.Internal, "panic: %v", r)
 *   }
 * </pre>
 */
@DisplayName("DRIFT-MSG-V-016 recover：panic 必须变成 INTERNAL + \"panic: \" 且进程存活")
// ⚠️ 兜底护栏：本类第一版漏了 `observer.onCompleted()`（grpc-java 的 unary 响应必须显式完成），
//    结果 call 永不关闭 ⇒ blocking stub 永久挂起 ⇒ 整轮 surefire 挂死 4 分钟。
//    两道防线：① stub 上的 10s deadline（会抛 DEADLINE_EXCEEDED，失败而非挂死）
//              ② 本类的 60s 硬超时（连 deadline 都没生效时的最后一道）。
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class MessageRpcRecoverInterceptorWiringTest {

    private static final String SERVER_NAME = "recover-parity-message-rpc";

    private Server server;
    private ManagedChannel channel;
    private MessageServiceGrpc.MessageServiceBlockingStub stub;
    private final AtomicInteger invocations = new AtomicInteger();
    private volatile boolean panicMode = true;

    private MarkNotificationReq req() {
        return MarkNotificationReq.newBuilder().setNotificationId(1L).build();
    }

    /** 起一个 server；{@code inner} 是业务观测拦截器（此处用一个记录调用次数的空实现）。 */
    private void startServer(ServerInterceptor inner) throws Exception {
        server = InProcessServerBuilder.forName(SERVER_NAME)
                .directExecutor()
                // ⚠️ 用**生产的** recoverThen，不是测试自己拼的链
                .intercept(com.rag.common.protection.GoStyleRpcProtectionChain.recoverThen(inner))
                .addService(new MessageServiceGrpc.MessageServiceImplBase() {
                    @Override
                    public void markNotificationRead(MarkNotificationReq request,
                                                     StreamObserver<BaseResp> observer) {
                        invocations.incrementAndGet();
                        if (panicMode) {
                            throw new IllegalStateException("测试用 panic");
                        }
                        observer.onNext(BaseResp.newBuilder().setCode(200).build());
                        // ⚠️ grpc-java 的 unary 响应**必须**显式 onCompleted()：
                        //    ServerCalls.java:394 `onCompleted()` 才 `call.close(Status.OK)`，
                        //    只 onNext 的话 call 永不关闭 ⇒ 客户端 blocking stub **永久挂起**。
                        //    （本类第一版就漏了这一句，整轮构建挂死了 4 分钟。）
                        observer.onCompleted();
                    }
                })
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(SERVER_NAME).directExecutor().build();
        stub = MessageServiceGrpc.newBlockingStub(channel).withDeadlineAfter(10, TimeUnit.SECONDS);
    }

    private static ServerInterceptor noopInner() {
        return new ServerInterceptor() {
            @Override
            public <Q, S> ServerCall.Listener<Q> interceptCall(ServerCall<Q, S> call, Metadata headers,
                                                               ServerCallHandler<Q, S> next) {
                return next.startCall(call, headers);
            }
        };
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

    // ==================================================================
    // 1. 核心契约
    // ==================================================================

    @Test
    @DisplayName("🔴 handler panic ⇒ 客户端看到 INTERNAL，且 description 以 \"panic: \" 开头")
    void panicBecomesInternalWithGoCompatibleDescription() throws Exception {
        startServer(noopInner());

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class,
                () -> stub.markNotificationRead(req()));

        assertEquals(Status.Code.INTERNAL, e.getStatus().getCode(),
                "Go: status.Errorf(codes.Internal, …) ⇒ 必须 INTERNAL");
        String desc = e.getStatus().getDescription();
        assertNotNull(desc, "description 必须存在（Go 显式写了 \"panic: %v\"）");
        assertTrue(desc.startsWith(GoStyleRecoverInterceptor.PANIC_DESCRIPTION_PREFIX),
                "description 必须以 \"panic: \" 开头，实际=" + desc);
        assertTrue(desc.contains("测试用 panic"),
                "description 必须带上 panic 值（Go 的 %v），实际=" + desc);
        assertEquals(1, invocations.get(), "前置条件：请求确实进到了 handler（否则本用例是假绿）");
    }

    /**
     * Go 的 {@code %v} 对 {@code panic("boom")} 打印 {@code boom}，<b>不带类型名</b>；
     * Java 直接 {@code toString()} 会变成 {@code java.lang.IllegalStateException: boom}。
     * 这是 <b>wire 可见</b>的差异，必须钉死。
     */
    @Test
    @DisplayName("description 里不带 Java 类型名（对齐 Go %v 对 panic 值的语义）")
    void descriptionDoesNotLeakJavaTypeName() throws Exception {
        startServer(noopInner());

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class,
                () -> stub.markNotificationRead(req()));
        String desc = e.getStatus().getDescription();
        assertTrue(!desc.contains("IllegalStateException"),
                "Go 的 panic 值不带类型名；RAG 直接 toString() 会多出类型名：" + desc);
        assertTrue(!desc.contains("java.lang."), "description 不应出现 Java 包名，实际=" + desc);
    }

    // ==================================================================
    // 2. 进程存活
    // ==================================================================

    @Test
    @DisplayName("🔴 panic 之后进程存活，且能继续服务下一个正常请求")
    void processSurvivesAndServesNextRequest() throws Exception {
        startServer(noopInner());

        assertThrows(StatusRuntimeException.class, () -> stub.markNotificationRead(req()));

        panicMode = false;
        BaseResp resp = stub.markNotificationRead(req());
        assertEquals(200, resp.getCode(),
                "Go 的 recover 只影响**当前**这次调用；服务必须继续可用");
        assertEquals(2, invocations.get());
    }

    // ==================================================================
    // 3. 关闭 UNRESOLVED-MSG-006：非 RuntimeException 的 Error
    // ==================================================================

    @Test
    @DisplayName("抛 java.lang.Error（StackOverflowError）同样被兜住 ⇒ 关闭 UNRESOLVED-MSG-006")
    void nonRuntimeExceptionErrorIsAlsoRecovered() throws Exception {
        server = InProcessServerBuilder.forName(SERVER_NAME)
                .directExecutor()
                .intercept(com.rag.common.protection.GoStyleRpcProtectionChain.recoverThen(noopInner()))
                .addService(new MessageServiceGrpc.MessageServiceImplBase() {
                    @Override
                    public void markNotificationRead(MarkNotificationReq request,
                                                     StreamObserver<BaseResp> observer) {
                        throw new StackOverflowError();
                    }
                })
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(SERVER_NAME).directExecutor().build();
        stub = MessageServiceGrpc.newBlockingStub(channel).withDeadlineAfter(10, TimeUnit.SECONDS);

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class,
                () -> stub.markNotificationRead(req()));
        assertEquals(Status.Code.INTERNAL, e.getStatus().getCode(),
                "Error 也必须被兜住（Go 的 recover 同样能接 runtime.Error 类 panic）");
        assertTrue(e.getStatus().getDescription().startsWith(GoStyleRecoverInterceptor.PANIC_DESCRIPTION_PREFIX),
                "实际=" + e.getStatus().getDescription());
    }

    // ==================================================================
    // 4. 装配顺序：recover 必须在业务拦截器之外
    // ==================================================================

    /**
     * Go 的注册序（{@code zrpc/internal/server.go:58} 是 append 到尾部 ⇒ 先注册的最外层）：
     * Trace → Recover → … → Breaker → Shedding → Timeout → observability。
     * ⇒ <b>recover 在最外层</b>；挂到里面的话，业务拦截器自己抛的 panic 就漏出去了。
     *
     * <p>本用例让<b>内层拦截器</b>抛，只有 recover 在外层时才兜得住 ——
     * 这把「顺序」从读代码变成可断言的事实。
     */
    @Test
    @DisplayName("🔴 顺序：recover 在外层 ⇒ 内层拦截器抛的 panic 也被兜住")
    void recoverWrapsTheInnerInterceptor() throws Exception {
        ServerInterceptor explodingInner = new ServerInterceptor() {
            @Override
            public <Q, S> ServerCall.Listener<Q> interceptCall(ServerCall<Q, S> call, Metadata headers,
                                                               ServerCallHandler<Q, S> next) {
                throw new IllegalStateException("内层拦截器炸了");
            }
        };

        server = InProcessServerBuilder.forName(SERVER_NAME)
                .directExecutor()
                .intercept(com.rag.common.protection.GoStyleRpcProtectionChain.recoverThen(explodingInner))
                .addService(new MessageServiceGrpc.MessageServiceImplBase() {
                    @Override
                    public void markNotificationRead(MarkNotificationReq request,
                                                     StreamObserver<BaseResp> observer) {
                        observer.onNext(BaseResp.getDefaultInstance());
                        observer.onCompleted();
                    }
                })
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(SERVER_NAME).directExecutor().build();
        stub = MessageServiceGrpc.newBlockingStub(channel).withDeadlineAfter(10, TimeUnit.SECONDS);

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class,
                () -> stub.markNotificationRead(req()));
        assertEquals(Status.Code.INTERNAL, e.getStatus().getCode(),
                "内层拦截器的 panic 必须被外层 recover 兜住，否则会漏成 executor 崩溃");
        assertTrue(e.getStatus().getDescription().contains("内层拦截器炸了"),
                "实际=" + e.getStatus().getDescription());
    }
}