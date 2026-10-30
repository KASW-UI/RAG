package com.rag.common.protection;

import com.rag.common.observability.RpcInterceptor;
import io.grpc.CallOptions;
import io.grpc.Channel;
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
import net.devh.boot.grpc.client.autoconfigure.GrpcClientAutoConfiguration;
import net.devh.boot.grpc.client.channelfactory.GrpcChannelConfigurer;
import net.devh.boot.grpc.client.channelfactory.GrpcChannelFactory;
import net.devh.boot.grpc.client.channelfactory.InProcessChannelFactory;
import net.devh.boot.grpc.client.config.GrpcChannelsProperties;
import net.devh.boot.grpc.client.interceptor.GlobalClientInterceptorRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>守门：「{@code RpcInterceptor} 绑的请求 deadline 载体 ⇒ 被 devh 的出向拦截器读到
 * ⇒ 出向调用在预算处失败」这条链，真跑一次。</b>
 *
 * <h2>🔴 它补的是哪一个洞（2026-10-30 · G4 lane · 第十三次订正）</h2>
 * <p>本层已有的两条守门<b>各覆盖半条链</b>：
 * <pre>
 *   ① GoStyleDevhOutboundDeadlineAssemblyGateTest（第十一次订正）
 *      ⇒ 覆盖「devh 把全局拦截器接到 channel 上」＋「拦截器读得到 ThreadLocal 载体」
 *      ⇒ 但载体是**手工** {@code GoStyleRequestDeadline.bindAbsoluteNanos(...)} 绑的
 *      ⇒ ⇒ **没有**覆盖「生产上真正绑载体的那个组件」＝ {@link RpcInterceptor}
 *   ② GoStyleOutboundDeadlineRpcAssemblyTest（更早）
 *      ⇒ 只断言「拦截器在 devh 的 registry 里」＝ 静态断言
 * </pre>
 * ⇒ ⇒ <b>缺的那一截</b>：<b>生产链路的起点</b> —— 真实服务端拦截器
 * {@link RpcInterceptor#onHalfClose} 绑出来的载体，能不能被<b>下游</b>的出向拦截器读到。
 *
 * <h2>🔴 为什么这一截是探针改造的必经之路</h2>
 * <p>{@code tools/semantic-probe/deadline-downstream} 那个探针的 RAG 半边，
 * 改完要用<b>服务端</b> {@code RpcInterceptor} ＋ <b>客户端</b> devh channel 才能对上 Go：
 * <pre>
 *   Go：handler 收到的 ctx 已过期 ⇒ 出向 gRPC 立即 {@code DeadlineExceeded}
 *        ⇒ failed_fast=true / code=4 / outgoing_carrier=present_expired
 *   RAG（要改成的形态）：{@code RpcInterceptor} 绑载体 ⇒ handler 里发下游
 *        ⇒ devh 出向拦截器读到「已过期」⇒ 立即 {@code DEADLINE_EXCEEDED}
 * </pre>
 * ⇒ ⇒ 若这条链中间有一环不通（载体没绑 / 跨线程被清 / 过期值被当成「无预算」）
 * ⇒ ⇒ 探针改完会<b>仍然红</b> ⇒ ⇒ 而那会<b>被误判成「探针改错了」</b>。
 * ⇒ ⇒ 本类就是提前把这条链钉死用的。
 *
 * <h2>本类用「行为差分」：3×2 矩阵，每次只变一个变量</h2>
 * <table border="1">
 *   <caption>形态矩阵</caption>
 *   <tr><th>形态</th><th>上游（服务端）</th><th>下游（客户端）</th><th>期望</th></tr>
 *   <tr><td>①</td><td>挂 {@code RpcInterceptor}</td><td>devh channel</td>
 *       <td><b>立即</b> DEADLINE_EXCEEDED（载体已过期）</td></tr>
 *   <tr><td>②</td><td>挂 {@code RpcInterceptor}</td><td><b>裸</b> channel</td>
 *       <td>挂满下游（载体在，但<b>没人</b>读它）</td></tr>
 *   <tr><td>③</td><td><b>不挂</b> {@code RpcInterceptor}</td><td>devh channel</td>
 *       <td>挂满下游（拦截器在，但<b>没有载体</b>）</td></tr>
 * </table>
 * ⇒ ⇒ ①相对②<b>只差「下游走没走 devh」</b>；①相对③<b>只差「上游绑没绑载体」</b>
 * ⇒ ⇒ 差异被隔离到<b>两个变量各自的作用点</b>上 ⇒ ⇒ ①的绿<b>不是</b>任何单边效应。
 *
 * <h2>形态④：互补分支 —— 「未过期」长什么样</h2>
 * <p>形态①测的是「载体<b>已过期</b>」⇒ 立即失败。形态④测「载体<b>未过期</b>」
 * ⇒ ⇒ 出向调用应当<b>继承剩余预算</b>（在预算处失败，<b>不是</b>立即、<b>不是</b>挂满）
 * ⇒ ⇒ 两个分支一起钉死，避免「只有过期这一条路能走通」的半边实现蒙混过关。
 *
 * <h2>⚠️ 一处极易判错的机制细节（本类断言它，且它是变异 M2 的锚点）</h2>
 * <p>{@code GoStyleRequestDeadline.remainingNanos()} 在<b>已过期</b>时返回的是
 * <b>{@code 0}</b>（{@code Math.max(0L, d - now)}）⇒ <b>不是负数</b>
 * ⇒ ⇒ 出向拦截器里 {@code if (requestRemaining >= 0L)} <b>成立</b>
 * ⇒ ⇒ 预算被压成 {@code 0} ⇒ ⇒ 命中「预算已耗尽 ⇒ 立即失败」分支
 * ⇒ ⇒ <b>「已过期 ⇒ 立即失败」这条路径是已实现的</b>（若读成「过期被 >= 0 跳过 ⇒ 不设限」
 * ⇒ ⇒ 就会把期望值写反 ⇒ ⇒ 探针的期望文件会写错）。
 *
 * <h2>Go 出处</h2>
 * <pre>
 *   go-zero@v1.10.2/zrpc/internal/client.go:115-116
 *       if c.middlewares.Timeout { interceptors = append(interceptors, clientinterceptors.TimeoutInterceptor(timeout)) }
 *   go-zero@v1.10.2/zrpc/internal/clientinterceptors/timeoutinterceptor.go:25
 *       ⇒ context.WithTimeout(ctx, t) ⇒ 取更早者（min 语义）
 *   Go 侧 ctx 已过期 ⇒ 子 ctx（WithTimeout）立即 Done ⇒ 调用立即 DeadlineExceeded
 * </pre>
 */
class GoStyleRpcInterceptorOutboundDeadlineChainGateTest {

    /** 上游（服务端）in-process 名 —— <b>挂</b> {@code RpcInterceptor} 的那个。 */
    private static final String UPSTREAM_WITH_RPC = "chain-upstream-with-rpc";

    /** 上游（服务端）in-process 名 —— <b>不挂</b> {@code RpcInterceptor} 的那个（形态③用）。 */
    private static final String UPSTREAM_WITHOUT_RPC = "chain-upstream-without-rpc";

    /** 下游（服务端）in-process 名 ＝ devh 里的 channel 名。 */
    private static final String DOWNSTREAM_NAME = "chain-interceptor-downstream";

    private static final String SERVICE_NAME = "test.ChainDeadlineSvc";

    /** 上游 {@code RpcInterceptor} 的服务端预算（毫秒）＝ Go 侧 {@code context.WithTimeout} 的 d。 */
    private static final long SERVER_TIMEOUT_MS = 300L;

    /** 下游挂起时长（毫秒）—— 远大于请求预算 ⇒ 挂满就说明「没有 deadline」。 */
    private static final long HANG_MS = 5_000L;

    /** 「超过这个毫秒数就算挂满」的判据。 */
    private static final long HANG_TOLERANCE_MS = 500L;

    /** 形态①：载体已过期 ⇒ 出向调用必须「立即」失败（远小于挂满时长）。 */
    private static final long EXPIRED_IMMEDIATE_TOLERANCE_MS = 200L;

    /** 形态④：载体未过期 ⇒ 出向调用至少要继承这么多预算（不许「立即」失败）。 */
    private static final long INHERIT_MIN_MS = 120L;

    /** 形态①/②：handler 先等载体过期，再发下游调用。 */
    private static final long WAIT_UNTIL_EXPIRED_MS = 400L;

    private static final MethodDescriptor<byte[], byte[]> HANG_METHOD = MethodDescriptor.<byte[], byte[]>newBuilder()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName(SERVICE_NAME + "/Hang")
            .setRequestMarshaller(new ByteArrayMarshaller())
            .setResponseMarshaller(new ByteArrayMarshaller())
            .build();

    private static final MethodDescriptor<byte[], byte[]> UPSTREAM_METHOD =
            MethodDescriptor.<byte[], byte[]>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(SERVICE_NAME + "/Upstream")
                    .setRequestMarshaller(new ByteArrayMarshaller())
                    .setResponseMarshaller(new ByteArrayMarshaller())
                    .build();

    private static Server downstream;
    private static Server upstreamWithRpc;
    private static Server upstreamWithoutRpc;
    private static ConfigurableApplicationContext ctx;

    // ── 每测例可变状态（handler 线程读它）────────────────────────────────────
    /** 本测例里 handler 要不要先睡到载体过期。 */
    private static volatile boolean sleepUntilExpired = true;
    /** 本测例里 handler 用哪个 channel 发出向调用（true = devh 装配面）。 */
    private static volatile boolean useDevhChannel = true;
    /** handler 侧出向调用的结果。 */
    private static final AtomicReference<DownstreamOutcome> outcome = new AtomicReference<>();
    /** handler 跑完的信号。 */
    private static volatile CountDownLatch handlerDone;

    /** 出向调用的观测结果。 */
    private static final class DownstreamOutcome {
        /** 出向调用的状态码（{@code OK} = 没抛异常）。 */
        Status.Code code;
        /** 出向调用的失败码（没抛异常时为 null）。 */
        Status.Code failureCode;
        /** 出向调用耗时（毫秒）。 */
        long elapsedMs;
        /** 非 gRPC 异常。 */
        Throwable error;
    }

    @BeforeAll
    static void setUp() throws Exception {
        // ① 下游：真 in-process gRPC 服务端，方法挂起 HANG_MS 才返回
        downstream = InProcessServerBuilder.forName(DOWNSTREAM_NAME)
                .addService(ServerServiceDefinition.builder(SERVICE_NAME)
                        .addMethod(HANG_METHOD, ServerCalls.asyncUnaryCall((req, obs) -> {
                            try {
                                Thread.sleep(HANG_MS);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            obs.onError(Status.CANCELLED
                                    .withDescription("downstream finished").asRuntimeException());
                        }))
                        .build())
                .build()
                .start();

        // ② 上游 A：挂**生产**的 RpcInterceptor（形态①/②/④ 用）
        upstreamWithRpc = InProcessServerBuilder.forName(UPSTREAM_WITH_RPC)
                .addService(upstreamService())
                .intercept(RpcInterceptor.newUnaryServerInterceptor(
                        Duration.ofMillis(SERVER_TIMEOUT_MS), Duration.ofMillis(1)))
                .build()
                .start();

        // ③ 上游 B：**不挂** RpcInterceptor（形态③ 用）⇒ ⇒ 没有载体
        upstreamWithoutRpc = InProcessServerBuilder.forName(UPSTREAM_WITHOUT_RPC)
                .addService(upstreamService())
                .build()
                .start();

        // ④ devh 的**真**客户端装配面（真 Spring 容器 + devh 的自动配置）
        ctx = new SpringApplicationBuilder(DevhClientApp.class)
                .web(WebApplicationType.NONE)
                .properties("management.otlp.tracing.export.enabled=false")
                .run();
    }

    @AfterAll
    static void tearDown() {
        if (ctx != null) {
            ctx.close();
        }
        if (downstream != null) {
            downstream.shutdownNow();
        }
        if (upstreamWithRpc != null) {
            upstreamWithRpc.shutdownNow();
        }
        if (upstreamWithoutRpc != null) {
            upstreamWithoutRpc.shutdownNow();
        }
    }

    @BeforeEach
    void resetPerCase() {
        outcome.set(null);
        handlerDone = new CountDownLatch(1);
        sleepUntilExpired = true;
        useDevhChannel = true;
    }

    // ────────────────────────────────────────────────────────────────────────────
    // 形态①：上游挂 RpcInterceptor ＋ 下游走 devh channel ⇒ 载体已过期 ⇒ 立即失败
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("形态①：RpcInterceptor 绑的载体（已过期）⇒ 被 devh 出向拦截器读到 ⇒ 出向调用立即 DEADLINE_EXCEEDED")
    void expiredCarrierReachesDevhOutboundInterceptor() throws Exception {
        sleepUntilExpired = true;
        useDevhChannel = true;

        DownstreamOutcome result = runUpstream(UPSTREAM_WITH_RPC);

        assertThat(result.error)
                .as("出向调用不应抛出非 gRPC 异常")
                .isNull();
        assertThat(result.failureCode)
                .as("载体已过期 ⇒ 出向调用必须在 deadline 处失败（而不是挂满下游的 %ds）", HANG_MS / 1000)
                .isNotNull();
        assertThat(result.failureCode)
                .isEqualTo(Status.Code.DEADLINE_EXCEEDED);
        assertThat(result.elapsedMs)
                .as("载体已过期 ⇒ 应当**立即**失败（实际 %dms）⇒ 不是「继承剩余」也不是「挂满」",
                        result.elapsedMs)
                .isLessThan(EXPIRED_IMMEDIATE_TOLERANCE_MS);
    }

    // ────────────────────────────────────────────────────────────────────────────
    // 形态②（负控 A）：同上，但下游走**裸** channel ⇒ 载体在，没人读 ⇒ 挂满
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("形态②（负控 A · 只差「下游走没走 devh」）：裸 channel ⇒ 挂满 ⇒ 形态①的绿来自 devh 拦截器")
    void rawChannelIgnoresCarrier() throws Exception {
        sleepUntilExpired = true;
        useDevhChannel = false;

        DownstreamOutcome result = runUpstream(UPSTREAM_WITH_RPC);

        assertThat(result.elapsedMs)
                .as("裸 channel 不经 devh 的全局拦截器 ⇒ 没有任何 deadline ⇒ 必然挂满下游的 %ds"
                        + "（形态①与本形态只差「走没走 devh」）", HANG_MS / 1000)
                .isGreaterThanOrEqualTo(HANG_MS - HANG_TOLERANCE_MS);
        assertThat(result.failureCode)
                .as("挂满之后是下游自己返回的 CANCELLED（不是 deadline）")
                .isEqualTo(Status.Code.CANCELLED);
    }

    // ────────────────────────────────────────────────────────────────────────────
    // 形态③（负控 B）：上游**不挂** RpcInterceptor ⇒ 没有载体 ⇒ devh channel 也挂满
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("形态③（负控 B · 只差「上游绑没绑载体」）：无载体 ⇒ devh channel 也挂满 ⇒ 形态①的绿来自 RpcInterceptor")
    void noCarrierMeansDevhChannelAlsoHangs() throws Exception {
        sleepUntilExpired = true;
        useDevhChannel = true;

        DownstreamOutcome result = runUpstream(UPSTREAM_WITHOUT_RPC);

        assertThat(result.elapsedMs)
                .as("上游不挂 RpcInterceptor ⇒ 载体不存在 ⇒ devh 的出向拦截器读不到预算 ⇒ 挂满"
                        + "（形态①与本形态只差「上游绑没绑载体」）")
                .isGreaterThanOrEqualTo(HANG_MS - HANG_TOLERANCE_MS);
        assertThat(result.failureCode)
                .isEqualTo(Status.Code.CANCELLED);
    }

    // ────────────────────────────────────────────────────────────────────────────
    // 形态④（互补分支）：载体**未过期** ⇒ 出向调用继承剩余预算（在预算处失败）
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("形态④（互补分支）：载体未过期 ⇒ 出向调用**继承剩余预算**（在预算处失败，不是立即、不是挂满）")
    void freshCarrierIsInherited() throws Exception {
        sleepUntilExpired = false;
        useDevhChannel = true;

        DownstreamOutcome result = runUpstream(UPSTREAM_WITH_RPC);

        assertThat(result.failureCode)
                .as("载体未过期 ⇒ 出向调用继承剩余预算 ⇒ 在预算处失败")
                .isEqualTo(Status.Code.DEADLINE_EXCEEDED);
        assertThat(result.elapsedMs)
                .as("必须**继承**到预算（≈%dms）⇒ 不能立即失败（实际 %dms）",
                        SERVER_TIMEOUT_MS, result.elapsedMs)
                .isGreaterThanOrEqualTo(INHERIT_MIN_MS);
        assertThat(result.elapsedMs)
                .as("必须**继承**到预算（≈%dms）⇒ 不能挂满下游（实际 %dms）",
                        SERVER_TIMEOUT_MS, result.elapsedMs)
                .isLessThan(HANG_MS - HANG_TOLERANCE_MS);
    }

    // ────────────────────────────────────────────────────────────────────────────
    // 测试装置
    // ────────────────────────────────────────────────────────────────────────────

    /**
     * 上游服务定义：handler 里（可选地）等载体过期，然后发出向调用并记录结果。
     */
    private static ServerServiceDefinition upstreamService() {
        return ServerServiceDefinition.builder(SERVICE_NAME)
                .addMethod(UPSTREAM_METHOD, ServerCalls.asyncUnaryCall((req, obs) -> {
                    try {
                        if (sleepUntilExpired) {
                            // 等到服务端预算（300ms）过去 ⇒ 载体**已过期**
                            Thread.sleep(WAIT_UNTIL_EXPIRED_MS);
                        }
                        Channel channel = useDevhChannel
                                ? ctx.getBean(GrpcChannelFactory.class).createChannel(DOWNSTREAM_NAME)
                                : null;
                        DownstreamOutcome result = new DownstreamOutcome();
                        long start = System.nanoTime();
                        try {
                            if (channel != null) {
                                // devh 装配面 ⇒ 全局拦截器在链上
                                ClientCalls.blockingUnaryCall(
                                        channel, HANG_METHOD, CallOptions.DEFAULT, new byte[0]);
                            } else {
                                // 裸 channel ⇒ 没有全局拦截器
                                ManagedChannel raw = InProcessChannelBuilder.forName(DOWNSTREAM_NAME).build();
                                try {
                                    ClientCalls.blockingUnaryCall(
                                            raw, HANG_METHOD, CallOptions.DEFAULT, new byte[0]);
                                } finally {
                                    raw.shutdownNow();
                                }
                            }
                            result.code = Status.Code.OK;
                        } catch (StatusRuntimeException e) {
                            result.code = e.getStatus().getCode();
                            result.failureCode = e.getStatus().getCode();
                        } catch (Exception e) {
                            result.error = e;
                        }
                        result.elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
                        outcome.set(result);
                        // ⚠️ 上游的响应**故意不返回**（永不 onNext/onCompleted）
                        //    ⇒ 与探针的形态一致：上游靠服务端超时被 cancel
                    } catch (Exception e) {
                        DownstreamOutcome result = new DownstreamOutcome();
                        result.error = e;
                        outcome.set(result);
                    } finally {
                        handlerDone.countDown();
                    }
                }))
                .build();
    }

    /**
     * 调上游 ⇒ 等 handler 跑完 ⇒ 取回出向调用的观测结果。
     *
     * <p>⚠️ <b>为什么上游调用要放后台线程</b>：挂了 {@code RpcInterceptor} 的上游会在
     * 服务端预算到点时<b>主动 cancel</b> server call ⇒ 客户端那次 blocking 调用会<b>先</b>返回
     * ⇒ ⇒ 而 handler 线程仍在跑（要等它把出向调用的结果记下来）⇒ ⇒ 两个时间线必须分开等。
     */
    private static DownstreamOutcome runUpstream(String upstreamName) throws Exception {
        ManagedChannel client = InProcessChannelBuilder.forName(upstreamName).build();
        Thread caller = new Thread(() -> {
            try {
                // ⚠️ 客户端**不带** deadline ⇒ 服务端预算才是唯一的预算来源
                //    （带了的话 client deadline 会参与 min，改变本类要测的语义）
                ClientCalls.blockingUnaryCall(client, UPSTREAM_METHOD, CallOptions.DEFAULT, new byte[0]);
            } catch (Exception ignored) {
                // 上游被服务端超时 cancel ⇒ 客户端这里抛 DEADLINE_EXCEEDED 是预期的
            }
        }, "upstream-caller");
        caller.setDaemon(true);
        caller.start();
        try {
            assertThat(handlerDone.await(30, TimeUnit.SECONDS))
                    .as("handler 必须在 30s 内跑完")
                    .isTrue();
            DownstreamOutcome result = outcome.get();
            assertThat(result)
                    .as("handler 必须记录到出向调用的结果")
                    .isNotNull();
            return result;
        } finally {
            client.shutdownNow();
        }
    }

    /** devh 的客户端装配面：{@code @ImportAutoConfiguration} 拉起 devh 的自动配置。 */
    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration(GrpcClientAutoConfiguration.class)
    @Import(GoStyleProtectionConfiguration.class)
    static class DevhClientApp {

        /**
         * <b>用 devh 自己的 in-process 工厂</b>（它与生产的 netty 工厂<b>同属</b>
         * {@code AbstractChannelFactory} ⇒ ⇒ <b>走的是同一条 {@code createChannel} 实现</b>
         * ⇒ ⇒ 装全局拦截器的那一步<b>一字不差</b>）。
         */
        @Bean
        GrpcChannelFactory grpcChannelFactory(GrpcChannelsProperties props,
                                              GlobalClientInterceptorRegistry registry,
                                              List<GrpcChannelConfigurer> configurers) {
            return new InProcessChannelFactory(props, registry, configurers);
        }
    }

    /** {@code byte[]} 的编解码器。 */
    private static final class ByteArrayMarshaller implements MethodDescriptor.Marshaller<byte[]> {
        @Override
        public InputStream stream(byte[] value) {
            return new ByteArrayInputStream(value == null ? new byte[0] : value);
        }

        @Override
        public byte[] parse(InputStream stream) {
            try {
                return stream.readAllBytes();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
