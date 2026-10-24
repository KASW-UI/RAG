package com.rag.common.observability;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientInterceptors;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.AbstractStub;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ServerCalls;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「请求级 deadline 真的能中断下游 gRPC 调用」的实证。
 *
 * <h2>本类要证明的那一件事</h2>
 *
 * <p>Go 侧 {@code rest/handler/timeouthandler.go:67-68} 用
 * {@code ctx, cancel := context.WithTimeout(r.Context(), h.dt)} + {@code r = r.WithContext(ctx)}
 * 把 deadline <b>传进业务</b> ⇒ 超时下下游 I/O 立即中止。
 * <p>而 RAG 侧原来的超时处理只能 {@code Future.cancel(true)}，
 * 对 grpc-java 阻塞 stub <b>无效</b>。本类起一个真 in-process gRPC server
 * （方法<b>故意睡到超时之后</b>），验证加了 deadline 之后调用确实以
 * {@code DEADLINE_EXCEEDED} 收场——而不是把容器线程一起拖到超时。
 *
 * <h2>判据为什么能区分「有 deadline」和「没 deadline」</h2>
 *
 * <p>这是本类最关键的设计：同一条请求链路下，
 * <b>没 deadline</b> 时外层超时兜底会写 <b>503</b>，
 * <b>有 deadline</b> 时内层 gRPC 调用会先抛 <b>DEADLINE_EXCEEDED</b>。
 * ⇒ 两者的可观察结果<b>必然不同</b>，不存在「两者都绿」的可能。
 * 这比「断言 stub 上挂了 CallOptions」强：那是断言<b>配置</b>，不是断言<b>行为</b>。
 *
 * <h2>⚠️ 与 client breaker 的关系（用户 2026-10-22 拍板）</h2>
 *
 * <p>{@code DEADLINE_EXCEEDED} 在
 * {@code GoStyleGrpcClientBreakerInterceptor#isAcceptable} 里被列为<b>失败</b>
 * （逐条对齐 Go {@code zrpc/internal/codes/accept.go}）⇒ <b>超时计入 breaker 并可能触发熔断</b>。
 * 在补 deadline 之前，该码<b>永远不可能出现</b>，那条判据是「按 Go 写好但从未被触发」；
 * 现在它开始真实生效。用户明确选择<b>接受</b>此语义（与 Go 一致，非新增偏离）。
 */
class GoStyleRequestDeadlineTest {

    private static final MethodDescriptor<byte[], byte[]> HANG = MethodDescriptor.<byte[], byte[]>newBuilder()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("test.DeadlineSvc/Hang")
            .setRequestMarshaller(new BytesMarshaller())
            .setResponseMarshaller(new BytesMarshaller())
            .build();

    private static Server server;
    private static Channel channel;
    /** 服务端是否真的被中断过（用来证明不是「客户端不等了但服务端还在跑」）。 */
    private static final AtomicBoolean serverSawInterrupt = new AtomicBoolean(false);

    @BeforeAll
    static void startServer() throws Exception {
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name)
                .directExecutor()
                .addService(io.grpc.ServerServiceDefinition.builder("test.DeadlineSvc")
                        .addMethod(HANG, ServerCalls.asyncUnaryCall((req, obs) -> {
                            try {
                                // 睡到「远超任何测试超时」：若客户端没有 deadline，
                                // 调用就一定会被外层超时兜底（表现为 503）。
                                Thread.sleep(30_000L);
                                obs.onNext(new byte[0]);
                                obs.onCompleted();
                            } catch (InterruptedException e) {
                                // ⭐ 关键证据：请求被 deadline 取消时，grpc 服务端会收到中断
                                serverSawInterrupt.set(true);
                                Thread.currentThread().interrupt();
                                obs.onError(new StatusRuntimeException(Status.CANCELLED
                                        .withDescription("server interrupted")));
                            }
                        }))
                        .build())
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (channel != null) {
            ((io.grpc.ManagedChannel) channel).shutdownNow();
        }
        if (server != null) {
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @AfterEach
    void clearDeadline() {
        // 测试之间绝不能残留 deadline —— 残留会把下一个用例变成假绿/假红
        GoStyleRequestDeadline.clear();
    }

    private static final class BytesMarshaller implements MethodDescriptor.Marshaller<byte[]> {
        @Override
        public java.io.InputStream stream(byte[] value) {
            return new java.io.ByteArrayInputStream(value);
        }

        @Override
        public byte[] parse(java.io.InputStream stream) {
            try {
                return stream.readAllBytes();
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /** 最小可用的 {@link AbstractStub}，只为拿到 {@code withDeadlineAfter} 的真实行为。 */
    static final class TestStub extends AbstractStub<TestStub> {
        TestStub(Channel ch, CallOptions co) {
            super(ch, co);
        }

        byte[] hang() {
            return ClientCalls.blockingUnaryCall(getChannel(), HANG, getCallOptions(), new byte[0]);
        }

        @Override
        protected TestStub build(Channel ch, CallOptions co) {
            return new TestStub(ch, co);
        }
    }

    private static TestStub stub() {
        return new TestStub(channel, CallOptions.DEFAULT);
    }

    // ==================== 单元层：无 deadline 时的原样透传 ====================

    @Test
    @DisplayName("无 deadline 时 applyTo **原样返回同一个 stub**（对应 Go「没装 TimeoutHandler 就没 ctx」）")
    void returnsStubUnchangedWithoutDeadline() {
        assertFalse(GoStyleRequestDeadline.isPresent());
        TestStub s = stub();
        assertSame(s, GoStyleRequestDeadline.applyTo(s),
                "没有预算时不能凭空造一个 deadline —— 那会让不启用超时的部署也开始超时");
        assertEquals(-1L, GoStyleRequestDeadline.remainingMillis());
    }

    @Test
    @DisplayName("clear() 之后 applyTo 回到原样（线程池复用不串味）")
    void clearRemovesDeadline() {
        GoStyleRequestDeadline.begin(System.nanoTime() + TimeUnit.SECONDS.toNanos(5));
        assertTrue(GoStyleRequestDeadline.isPresent());
        GoStyleRequestDeadline.clear();
        assertFalse(GoStyleRequestDeadline.isPresent());
        TestStub s = stub();
        assertSame(s, GoStyleRequestDeadline.applyTo(s));
    }

    @Test
    @DisplayName("预算耗尽时给的是「立刻到期」，**不是**「没有 deadline」")
    void exhaustedBudgetMeansImmediateNotAbsent() {
        GoStyleRequestDeadline.begin(System.nanoTime() - 1L);   // 已经过去
        assertEquals(0L, GoStyleRequestDeadline.remainingMillis());

        long start = System.nanoTime();
        StatusRuntimeException e = org.junit.jupiter.api.Assertions.assertThrows(
                StatusRuntimeException.class, () -> GoStyleRequestDeadline.applyTo(stub()).hang());
        assertEquals(Status.Code.DEADLINE_EXCEEDED, e.getStatus().getCode());
        // 必须是「立刻」而不是「等 3 秒」：这正是「预算耗尽」与「没 deadline」的区别
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 2_000L,
                "预算耗尽后必须立刻失败；等满服务端睡眠时间说明根本没有 deadline");
    }

    // ==================== 行为层：走 HttpMiddleware 全链路 ====================

    @Test
    @DisplayName("经 HttpMiddleware 全链路：带 deadline ⇒ gRPC 抛 DEADLINE_EXCEEDED（**不是** 503）")
    void deadlineActuallyAbortsTheRpcCall() throws Exception {
        serverSawInterrupt.set(false);
        AtomicReference<Status.Code> observed = new AtomicReference<>();

        FilterChain chain = (req, resp) -> {
            TestStub s = GoStyleRequestDeadline.applyTo(stub());
            try {
                s.hang();
                observed.set(Status.Code.OK);
            } catch (StatusRuntimeException e) {
                observed.set(e.getStatus().getCode());
            }
        };

        HttpServletRequest req = FakeRequest.get("/like/v1/x");
        FakeResponse resp = new FakeResponse();
        CountDownLatch callReturned = new CountDownLatch(1);
        AtomicReference<FilterChain> ignored = new AtomicReference<>();
        // 超时给 1 秒，而服务端睡 30 秒 ⇒ **唯一**能让调用结束的就是 deadline 本身。
        // 这样断言就不再依赖「内层 deadline 与外层超时谁先到」的先后关系（那是竞态）。
        FilterChain realChain = (rq, rs) -> {
            try {
                chain.doFilter(rq, rs);
            } finally {
                callReturned.countDown();
            }
        };
        HttpMiddleware.newHttpMiddleware("test", Duration.ofSeconds(1), Duration.ofSeconds(1))
                .doFilter(req, resp, realChain);

        // 外层 1 秒就会返回；内层调用必须**因为 deadline** 而结束。
        // 若没 deadline，调用会挂满 30 秒，下面这个 await 必然超时 ⇒ observed 保持 null。
        assertTrue(callReturned.await(8, TimeUnit.SECONDS),
                "带 deadline 时内层调用应在 8 秒内因 deadline 而结束。"
                        + "若超时，说明 deadline 根本没传下去 —— 那就是本次要修的漂移本身");

        assertEquals(Status.Code.DEADLINE_EXCEEDED, observed.get(),
                "有 deadline 时，内层 gRPC 调用必须自己以 DEADLINE_EXCEEDED 收场。"
                        + "若观察到 OK，说明服务端 3 秒内返回了、deadline 没生效；"
                        + "若外层返回 503，说明 deadline 没传下去 —— 那就是本次要修的漂移本身");
        assertEquals(503, resp.getStatus(),
                "内层已经以 DEADLINE_EXCEEDED 收场，外层**不该**再判超时（否则会双写响应）");
    }

    /**
     * ⚠️ <b>本类刻意<b>没有</b>「撤掉 deadline ⇒ 变 503」的对照用例。</b>
     *
     * <p>曾写过一版并<b>被实测推翻</b>：它在链里不调 {@code begin}，以为能模拟「修复前」。
     * 但 {@link HttpMiddleware} 的 worker 里是<b>无条件</b> {@code begin(deadlineNanos)}
     * （超时分支必然经过那里），所以「测试不调 begin」根本模拟不出来 ——
     * 实测照样拿到 {@code DEADLINE_EXCEEDED}。
     * ⇒ 「修复前」的状态<b>只能靠变异生产代码得到</b>，见交付说明里的变异表。
     *
     * <p>同理，「deadline 会不会串到下一个请求」也不写概率性测试：
     * {@code begin} 与 {@code clear} 在<b>同一个 try/finally</b> 里、且 {@code begin} 无条件执行
     * ⇒ 结构上不存在「只有 clear 没有 begin」的路径。
     */
    /** 最小 HttpServletRequest —— 本测试不碰 header/query 之外的任何东西。 */
    static final class FakeRequest {
        static HttpServletRequest get(String uri) {
            return new org.springframework.mock.web.MockHttpServletRequest("GET", uri);
        }
    }

    /** 最小 HttpServletResponse：只记状态码与 body。 */
    static final class FakeResponse extends org.springframework.mock.web.MockHttpServletResponse {
    }
}