package com.rag.common.protection;

import com.rag.common.observability.RpcInterceptor;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ServerCalls;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>守门：出向 gRPC 调用真的继承了「请求 deadline」（E65 的核心）。</b>
 *
 * <h2>这条守门钉住的是什么</h2>
 * <p>E65：<b>rpc handler 路径上的出向 gRPC 调用不继承请求 deadline</b>
 * ⇒ 下游挂住时，请求跟着挂住（Go 侧则是 ctx 到点立刻失败）。
 *
 * <h2>为什么必须是「真 gRPC + 真挂起」而不是 mock</h2>
 * <p>AGENTS.md §3.3：纯 Mockito mock 数据层的单测<b>不算</b>；「Bean 存在」也不等于「机制生效」。
 * ⇒ 本守门用<b>两个真 in-process gRPC server</b>：
 * <ol>
 *   <li><b>下游</b>：handler 挂起 5s（模拟「下游慢」）⇒ <b>不返回</b>；</li>
 *   <li><b>上游</b>：装上真正的 {@link RpcInterceptor}（服务端超时<b>故意给 5s</b>），
 *       它的 handler 里用<b>裸 stub</b>（<b>不调</b> {@code applyTo}）去调下游
 *       ⇒ ⇒ 测的正是「<b>调用点不改一行</b>，出向调用能不能拿到 deadline」。</li>
 * </ol>
 * 客户端给上游的 deadline = <b>300ms</b> ⇒ 上游的有效期限 = min(5s, 300ms) = 300ms。
 *
 * <h2>判据为什么有鉴别力（变异前必红）</h2>
 * <ul>
 *   <li><b>落码前</b>：出向调用<b>没有</b> deadline ⇒ 挂到下游的 5s 才返回
 *       ⇒ 耗时 ≈ 5000ms ⇒ 断言「耗时 &lt; 2s」<b>必红</b>；</li>
 *   <li><b>落码后</b>：出向调用在 ≈300ms 处以 {@code DEADLINE_EXCEEDED} 失败
 *       ⇒ 断言全绿 ⇒ ⇒ <b>红绿两向都有鉴别力</b>。</li>
 * </ul>
 *
 * <h2>Go 出处</h2>
 * <pre>
 * // go-zero@v1.10.2 zrpc/internal/serverinterceptors/timeoutinterceptor.go:34,53
 * ctx, cancel := context.WithTimeout(ctx, t)   // 服务端：min(客户端 deadline, 服务端 timeout)
 * resp, err = handler(ctx, req)               // handler 拿到的 ctx 已带 deadline
 * // go-zero@v1.10.2 zrpc/internal/clientinterceptors/timeoutinterceptor.go:25
 * ctx, cancel := context.WithTimeout(ctx, t)   // 出向：再 min 一次 ⇒ 父 deadline 参与
 * </pre>
 */
class GoStyleOutboundDeadlinePropagationGateTest {

    /** 上游服务端超时：故意<b>给得很大</b> ⇒ 这样「300ms 到期」只可能来自<b>客户端 deadline</b>。 */
    private static final Duration UPSTREAM_SERVER_TIMEOUT = Duration.ofSeconds(5);
    /** 客户端给上游的 deadline。 */
    private static final Duration CLIENT_DEADLINE = Duration.ofMillis(300);
    /** 下游挂起多久（≫ 客户端 deadline ⇒ 落码前一定会挂满）。 */
    private static final long DOWNSTREAM_HANG_MS = 5_000L;
    /** 判据上限：出向调用必须在 2s 内结束（下游要挂 5s ⇒ 落码前必红）。 */
    private static final long OUTBOUND_BUDGET_MS = 2_000L;

    private static final MethodDescriptor<byte[], byte[]> DOWNSTREAM_HANG =
            MethodDescriptor.<byte[], byte[]>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName("test.OutboundDeadlineSvc/Hang")
                    .setRequestMarshaller(new BytesMarshaller())
                    .setResponseMarshaller(new BytesMarshaller())
                    .build();

    private static final MethodDescriptor<byte[], byte[]> UPSTREAM_CALL =
            MethodDescriptor.<byte[], byte[]>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName("test.OutboundDeadlineSvc/Call")
                    .setRequestMarshaller(new BytesMarshaller())
                    .setResponseMarshaller(new BytesMarshaller())
                    .build();

    /** 上游 handler 里那次出向调用的结果（状态码名）。 */
    private static final AtomicReference<String> OUTBOUND_STATUS = new AtomicReference<>();
    /** 上游 handler 里那次出向调用的耗时（毫秒）。 */
    private static final AtomicLong OUTBOUND_ELAPSED_MS = new AtomicLong();
    /** 下游 handler 是否真的跑完了（挂起结束）。 */
    private static final AtomicBoolean DOWNSTREAM_FINISHED = new AtomicBoolean();
    /** 上游 handler 入口处看到的「请求载体剩余毫秒」（用来证明载体确实传到了 handler 线程）。 */
    private static final AtomicLong REQUEST_BUDGET_AT_HANDLER = new AtomicLong(-1L);
    /** 上游 handler 入口处看到的「出向载体剩余纳秒」（= Go 的 ctx deadline 剩余）。 */
    private static final AtomicLong OUTBOUND_CARRIER_NANOS_AT_HANDLER = new AtomicLong(-1L);
    /** 上游 handler 入口处看到的「SQL 载体剩余毫秒」（= {@code DeadlineState}）。 */
    private static final AtomicLong SQL_CARRIER_MILLIS_AT_HANDLER = new AtomicLong(-1L);
    /** 「无预算」上游（服务端 timeout 关闭 + 客户端不带 deadline）handler 里**是否**存在出向载体。 */
    private static final AtomicBoolean NO_BUDGET_CARRIER_PRESENT = new AtomicBoolean(true);
    /** 「无预算」上游 handler 里看到的出向载体剩余纳秒（-1 = 载体不存在）。 */
    private static final AtomicLong NO_BUDGET_CARRIER_NANOS = new AtomicLong(Long.MIN_VALUE);

    private static Server downstream;
    private static Server upstream;
    /** 「无预算」上游：服务端 timeout 关闭（{@link Duration#ZERO}）⇒ Go 侧 ctx 无 deadline。 */
    private static Server noBudgetUpstream;
    private static Channel clientToUpstream;
    private static Channel clientToNoBudgetUpstream;
    private static Channel upstreamToDownstream;

    @BeforeAll
    static void startServers() throws Exception {
        // ── 下游：挂起 5s 才返回（模拟「下游慢」）──
        String downstreamName = InProcessServerBuilder.generateName();
        downstream = InProcessServerBuilder.forName(downstreamName)
                .addService(ServerServiceDefinition.builder("test.OutboundDeadlineSvc")
                        .addMethod(DOWNSTREAM_HANG, ServerCalls.asyncUnaryCall((req, obs) -> {
                            try {
                                Thread.sleep(DOWNSTREAM_HANG_MS);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            DOWNSTREAM_FINISHED.set(true);
                            obs.onError(new StatusRuntimeException(
                                    Status.CANCELLED.withDescription("downstream finished")));
                        }))
                        .build())
                .build()
                .start();

        // ── 上游：装上真正的 RpcInterceptor（服务端超时 5s）──
        String upstreamName = InProcessServerBuilder.generateName();
        upstream = InProcessServerBuilder.forName(upstreamName)
                .addService(ServerServiceDefinition.builder("test.OutboundDeadlineSvc")
                        .addMethod(UPSTREAM_CALL, ServerCalls.asyncUnaryCall((req, obs) -> {
                            // 以下跑在 **handler 线程**上 ⇒ 线程局部载体对它可见
                            REQUEST_BUDGET_AT_HANDLER.set(
                                    com.rag.common.observability.GoStyleRequestDeadline.remainingMillis());
                            // 🔴 两个载体必须指向**同一时刻**（算一次、两个共用）：
                            //   ① 出向载体（GoStyleRequestDeadline）= Go 的 ctx deadline
                            //   ② SQL 载体（DeadlineState）= 服务端 SQL 超时
                            OUTBOUND_CARRIER_NANOS_AT_HANDLER.set(
                                    com.rag.common.observability.GoStyleRequestDeadline.remainingNanos());
                            SQL_CARRIER_MILLIS_AT_HANDLER.set(
                                    RpcInterceptor.DeadlineState.remainingMillis());
                            long start = System.nanoTime();
                            try {
                                // ⚠️ 刻意用**裸 stub**（不调 applyTo）⇒ 测的是「域侧零改动」的形态
                                ClientCalls.blockingUnaryCall(
                                        upstreamToDownstream, DOWNSTREAM_HANG, CallOptions.DEFAULT, req);
                                OUTBOUND_STATUS.set("NO_EXCEPTION");
                            } catch (StatusRuntimeException e) {
                                OUTBOUND_STATUS.set(e.getStatus().getCode().name());
                            } catch (RuntimeException e) {
                                OUTBOUND_STATUS.set("OTHER:" + e.getClass().getSimpleName());
                            } finally {
                                OUTBOUND_ELAPSED_MS.set(TimeUnit.NANOSECONDS.toMillis(
                                        System.nanoTime() - start));
                                try {
                                    obs.onError(new StatusRuntimeException(
                                            Status.CANCELLED.withDescription("upstream handler done")));
                                } catch (RuntimeException ignored) {
                                    // 超时可能已经把 call 关了 ⇒ 忽略是预期路径
                                }
                            }
                        }))
                        .build())
                .intercept(RpcInterceptor.newUnaryServerInterceptor(
                        UPSTREAM_SERVER_TIMEOUT, Duration.ofSeconds(5L)))
                .build()
                .start();

        // ── 「无预算」上游：服务端 timeout 关闭（Duration.ZERO）＋ 客户端不带 deadline ──
        //    ⇒ Go 侧 ctx **没有 deadline** ⇒ 出向**也不该有**（Go 的 WithTimeout 只在 t>0 时才加）
        String noBudgetName = InProcessServerBuilder.generateName();
        noBudgetUpstream = InProcessServerBuilder.forName(noBudgetName)
                .addService(ServerServiceDefinition.builder("test.OutboundDeadlineSvc")
                        .addMethod(UPSTREAM_CALL, ServerCalls.asyncUnaryCall((req, obs) -> {
                            NO_BUDGET_CARRIER_PRESENT.set(
                                    com.rag.common.observability.GoStyleRequestDeadline.isPresent());
                            NO_BUDGET_CARRIER_NANOS.set(
                                    com.rag.common.observability.GoStyleRequestDeadline.remainingNanos());
                            obs.onError(new StatusRuntimeException(
                                    Status.CANCELLED.withDescription("no-budget handler done")));
                        }))
                        .build())
                .intercept(RpcInterceptor.newUnaryServerInterceptor(
                        Duration.ZERO, Duration.ofSeconds(5L)))
                .build()
                .start();

        clientToUpstream = InProcessChannelBuilder.forName(upstreamName).directExecutor().build();
        clientToNoBudgetUpstream = InProcessChannelBuilder.forName(noBudgetName).directExecutor().build();

        // ── 上游 → 下游：出向通道上装上**共享层的出向 deadline 拦截器** ──
        Channel rawDownstream = InProcessChannelBuilder.forName(downstreamName).directExecutor().build();
        upstreamToDownstream = ClientInterceptors.intercept(
                rawDownstream, new GoStyleOutboundDeadlineInterceptor(true, 0L));
    }

    @AfterAll
    static void stopServers() throws Exception {
        for (Channel c : new Channel[]{clientToUpstream, clientToNoBudgetUpstream, upstreamToDownstream}) {
            if (c instanceof ManagedChannel) {
                ((ManagedChannel) c).shutdownNow();
            }
        }
        if (upstream != null) {
            upstream.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
        if (noBudgetUpstream != null) {
            noBudgetUpstream.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
        if (downstream != null) {
            downstream.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("上游 handler 的出向调用<b>继承了客户端 deadline</b> ⇒ 300ms 处失败，而不是挂满下游的 5s")
    void outboundCallInheritsRequestDeadline() throws Exception {
        // 客户端给上游 300ms ⇒ 上游的有效期限 = min(服务端 5s, 客户端 300ms) = 300ms
        assertThat(assertThrowsStatus(() -> ClientCalls.blockingUnaryCall(
                clientToUpstream, UPSTREAM_CALL,
                CallOptions.DEFAULT.withDeadlineAfter(CLIENT_DEADLINE.toMillis(), TimeUnit.MILLISECONDS),
                "call-1".getBytes(StandardCharsets.UTF_8))))
                .as("客户端这一侧本来就该看到 DEADLINE_EXCEEDED（上游 300ms 到期）")
                .isEqualTo(Status.Code.DEADLINE_EXCEEDED);

        // 等上游 handler 把它那次出向调用的结果记下来
        long deadline = System.currentTimeMillis() + 10_000L;
        while (OUTBOUND_STATUS.get() == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(50L);
        }

        assertThat(REQUEST_BUDGET_AT_HANDLER.get())
                .as("上游 handler 入口处必须能读到请求载体（>0 且 ≤ 客户端 deadline）"
                        + " ⇒ 否则「继承」无从谈起（实际=" + REQUEST_BUDGET_AT_HANDLER.get() + "）")
                .isGreaterThan(0L)
                .isLessThanOrEqualTo(CLIENT_DEADLINE.toMillis() + 100L);

        // 🔴 两个载体必须指向**同一时刻**（算一次、两个共用）：
        //   出向载体（Go 的 ctx deadline）与 SQL 载体（DeadlineState）若各算各的 now
        //   ⇒ 会出现「SQL 已经过期、但出向还活着」的窗口 ⇒ 这是漂移，必须钉死。
        long outboundCarrierNanos = OUTBOUND_CARRIER_NANOS_AT_HANDLER.get();
        long sqlCarrierMillis = SQL_CARRIER_MILLIS_AT_HANDLER.get();
        assertThat(outboundCarrierNanos)
                .as("出向载体必须在 handler 线程上可见且 >0（实际=" + outboundCarrierNanos + "）")
                .isGreaterThan(0L);
        assertThat(sqlCarrierMillis)
                .as("SQL 载体必须在 handler 线程上可见且 >0（实际=" + sqlCarrierMillis + "）")
                .isGreaterThan(0L);
        long carrierMillis = outboundCarrierNanos / 1_000_000L;
        assertThat(Math.abs(carrierMillis - sqlCarrierMillis))
                .as("两个载体必须指向**同一时刻**（出向=%dms / SQL=%dms ⇒ 差值应 ≤2ms，"
                        + "后者取整到毫秒 ⇒ 允许 1ms 的截断差）"
                        .formatted(carrierMillis, sqlCarrierMillis))
                .isLessThanOrEqualTo(2L);

        assertThat(OUTBOUND_ELAPSED_MS.get())
                .as("出向调用必须在 %dms 内结束（下游要挂 %dms）⇒ 落码前必红（挂满 5s）"
                        .formatted(OUTBOUND_BUDGET_MS, DOWNSTREAM_HANG_MS))
                .isLessThan(OUTBOUND_BUDGET_MS);

        assertThat(OUTBOUND_STATUS.get())
                .as("出向调用应以 DEADLINE_EXCEEDED 失败（Go 侧 ctx 到点即失败）")
                .isEqualTo(Status.Code.DEADLINE_EXCEEDED.name());

        assertThat(DOWNSTREAM_FINISHED.get())
                .as("下游 handler 此刻<b>不该</b>已经跑完 ⇒ 出向调用的失败来自 deadline，不是下游提前返回")
                .isFalse();
    }

    @Test
    @DisplayName("「无预算」时（服务端 timeout 关闭 ＋ 客户端不带 deadline）出向载体<b>不该被绑上</b>")
    void noBudgetMeansNoOutboundCarrierIsBound() throws Exception {
        // 客户端**不带** deadline ⇒ 服务端 timeout 关闭 ⇒ Go 侧 ctx 完全没有 deadline
        assertThrowsStatus(() -> ClientCalls.blockingUnaryCall(
                clientToNoBudgetUpstream, UPSTREAM_CALL, CallOptions.DEFAULT,
                "call-no-budget".getBytes(StandardCharsets.UTF_8)));

        long deadline = System.currentTimeMillis() + 10_000L;
        while (NO_BUDGET_CARRIER_NANOS.get() == Long.MIN_VALUE
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(50L);
        }

        assertThat(NO_BUDGET_CARRIER_PRESENT.get())
                .as("Go 侧 ctx 无 deadline ⇒ 出向**不该**凭空多出一个「292 年后到期」的 deadline"
                        + "（bindAbsoluteNanos 若无条件绑，就会绑出 Long.MAX_VALUE）")
                .isFalse();
        assertThat(NO_BUDGET_CARRIER_NANOS.get())
                .as("无载体时 remainingNanos() 必须是 -1（=「没有预算」）")
                .isEqualTo(-1L);
    }

    @Test
    @DisplayName("三项都没有预算 ⇒ 出向<b>不得凭空加</b> deadline（Go：ctx 无 deadline ⇒ 出向也没有）")
    void noRequestBudgetMeansNoInjectedDeadline() {
        com.rag.common.observability.GoStyleRequestDeadline.clear();
        GoStyleOutboundDeadlineInterceptor interceptor =
                new GoStyleOutboundDeadlineInterceptor(true, 0L);
        CallOptions effective = interceptor.effectiveCallOptions(CallOptions.DEFAULT);
        assertThat(effective.getDeadline())
                .as("没有请求载体、没有调用点 deadline、也没开客户端上界 ⇒ 出向**不得**有 deadline"
                        + "（Go 的 context.WithTimeout 只在 c.Timeout>0 时才加）")
                .isNull();
    }

    @Test
    @DisplayName("开了客户端上界 ⇒ 出向 deadline = 上界（Go 的 zrpc client Timeout 那一面）")
    void clientCapIsAppliedWhenEnabled() {
        com.rag.common.observability.GoStyleRequestDeadline.clear();
        GoStyleOutboundDeadlineInterceptor interceptor =
                new GoStyleOutboundDeadlineInterceptor(true, 2000L);
        CallOptions effective = interceptor.effectiveCallOptions(CallOptions.DEFAULT);
        assertThat(effective.getDeadline())
                .as("开了上界 ⇒ 必须有 deadline（Go：zrpc client Timeout 默认 2000ms）")
                .isNotNull();
        assertThat(effective.getDeadline().timeRemaining(TimeUnit.MILLISECONDS))
                .as("上界必须是 2000ms（且不得更宽）")
                .isGreaterThan(0L)
                .isLessThanOrEqualTo(2000L);
    }

    private static Status.Code assertThrowsStatus(Runnable call) {
        try {
            call.run();
            return null;
        } catch (StatusRuntimeException e) {
            return e.getStatus().getCode();
        }
    }

    /** 极简 byte[] 编解码器（本守门不关心内容，只关心「调用有没有按时失败」）。 */
    private static final class BytesMarshaller implements MethodDescriptor.Marshaller<byte[]> {
        @Override
        public InputStream stream(byte[] value) {
            return new ByteArrayInputStream(value == null ? new byte[0] : value);
        }

        @Override
        public byte[] parse(InputStream stream) {
            try {
                return stream.readAllBytes();
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
