package com.rag.common.protection;

import com.rag.common.observability.GoStyleRequestDeadline;
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
import io.grpc.stub.AbstractBlockingStub;
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
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>守门：devh 的「{@code @GrpcClient} stub ⇒ 出向调用继承请求 deadline」这条链，真跑一次。</b>
 *
 * <h2>🔴 它补的是哪一个洞（2026-10-30 · G4 lane · 第十一次订正）</h2>
 * <p>本层此前的落码与守门链条是：
 * <pre>
 *   ① {@code GoStyleOutboundDeadlineInterceptor} 是 {@code @GrpcGlobalClientInterceptor}
 *      ⇒ 进了 devh 的 {@code GlobalClientInterceptorRegistry}
 *      ⇒ 【已有守门】GoStyleOutboundDeadlineRpcAssemblyTest（断言 registry 里确实有它）
 *   ② devh 会把 registry 里的拦截器接到 channel 上
 *      ⇒ 【此前只有推断，没有运行期证据】本类的断言原文写的是「channel factory <b>会</b>装配它」
 * </pre>
 * ⇒ ⇒ <b>第 ② 步此前是一个「由 devh 的实现细节推出的结论」</b> ⇒ ⇒ 而
 * <b>AGENTS.md §3.3.1：框架机制必须验证实际生效</b> ⇒ ⇒ 缺一条运行期证据。
 *
 * <h2>🔴 为什么这条链必须成立（它决定 E65 的残余面到底是什么）</h2>
 * <p>有一份回执把 E65 的残余面写成「<b>各域出向调用点没接载体</b>（裸 stub ⇒ 不继承）
 * ⇒ ⇒ 域侧改用 {@code applyTo(stub)} 即可继承」。
 * ⇒ ⇒ <b>那一条根因判断不成立</b>：devh 把全局拦截器接在 <b>channel</b> 上 ⇒ ⇒
 * <b>凡建在 devh channel 上的 stub —— 哪怕是「裸」写法、不调 {@code applyTo} —— 都经过拦截器链</b>
 * ⇒ ⇒ 域侧出向调用<b>本来就继承</b>请求 deadline。
 * ⇒ ⇒ 本类就是这条判断的<b>运行期证据</b>（不是字节码推导，也不是转述）。
 *
 * <h2>本类用「行为差分」而不是反射读拦截器链</h2>
 * <p>三条 channel 形态，<b>只有一条变量不同</b>（走没走 devh 的 channel factory）：
 * <ul>
 *   <li><b>形态①</b>：devh 的 {@code @GrpcClient} stub ⇒ ⇒ <b>快速失败</b>（DEADLINE_EXCEEDED）;</li>
 *   <li><b>形态②</b>：<b>裸 channel</b>（不经 devh 装配）⇒ ⇒ <b>挂满</b>下游的 5s;</li>
 *   <li><b>形态③</b>：devh stub 的 channel <b>被 {@code ClientInterceptors} 包过</b>（不是裸
 *       {@code ManagedChannel}）⇒ ⇒ 装配面证据。</li>
 * </ul>
 * ⇒ ⇒ 形态①与形态②<b>只差「有没有经过 devh」这一个变量</b> ⇒ ⇒ 差异被隔离到 devh 装配
 * ⇒ ⇒ 形态①的绿<b>不是</b>「in-process 本身会传播 deadline」造成的 ⇒ ⇒ 证明是<b>拦截器</b>干的。
 *
 * <h2>⚠️ 形态② 就是「探针当前的处境」</h2>
 * <p>{@code tools/semantic-probe/deadline-downstream/java/DeadlineDownstreamProbe.java:86}
 * 用 {@code NettyChannelBuilder} <b>自建裸 netty channel</b>，{@code :198} 又用
 * {@code CallOptions.DEFAULT} ⇒ ⇒ <b>完全绕开 devh 的拦截器装配</b>
 * ⇒ ⇒ 那份探针实测到的 {@code failed_fast=false} / {@code code=0}
 * ⇒ ⇒ 是<b>探针构造的产物</b>，<b>不是</b>域侧 stub 的行为。
 *
 * <h2>Go 出处（为什么出向 deadline 该在拦截器级）</h2>
 * <pre>
 *   go-zero@v1.10.2/zrpc/internal/client.go:115-116
 *       if c.middlewares.Timeout {
 *           interceptors = append(interceptors, clientinterceptors.TimeoutInterceptor(timeout))
 *       }
 *   go-zero@v1.10.2/zrpc/internal/clientinterceptors/timeoutinterceptor.go:25
 *       ⇒ context.WithTimeout(ctx, t) ⇒ 取更早者（min 语义）
 * </pre>
 * ⇒ ⇒ Go 侧是<b>拦截器级</b> ⇒ ⇒ RAG 侧同形也该是<b>拦截器级</b>
 * ⇒ ⇒ <b>域侧零改动</b>（这正是本类要证明的那件事）。
 */
class GoStyleDevhOutboundDeadlineAssemblyGateTest {

    /** devh 里的 channel 名 = in-process 服务端名 = {@code @GrpcClient} 的值。 */
    private static final String CHANNEL_NAME = "devh-outbound-deadline-downstream";

    private static final String SERVICE_NAME = "test.DevhDeadlineSvc";

    /** 下游挂起时长（毫秒）—— 远大于请求预算 ⇒ 挂满就说明「没有 deadline」。 */
    private static final long HANG_MS = 5_000L;

    /** 请求预算（毫秒）。 */
    private static final long BUDGET_MS = 300L;

    /** 「超过这个毫秒数就算挂满」的判据。 */
    private static final long HANG_TOLERANCE_MS = 500L;

    private static final MethodDescriptor<byte[], byte[]> HANG_METHOD = MethodDescriptor.<byte[], byte[]>newBuilder()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName(SERVICE_NAME + "/Hang")
            .setRequestMarshaller(new ByteArrayMarshaller())
            .setResponseMarshaller(new ByteArrayMarshaller())
            .build();

    private static Server downstream;
    private static ConfigurableApplicationContext ctx;
    private static final AtomicBoolean downstreamFinished = new AtomicBoolean(false);

    @BeforeAll
    static void setUp() throws Exception {
        // ① 下游：真 in-process gRPC 服务端，方法挂起 HANG_MS 才返回
        downstream = InProcessServerBuilder.forName(CHANNEL_NAME)
                .addService(ServerServiceDefinition.builder(SERVICE_NAME)
                        .addMethod(HANG_METHOD, ServerCalls.asyncUnaryCall((req, obs) -> {
                            try {
                                Thread.sleep(HANG_MS);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            downstreamFinished.set(true);
                            obs.onError(Status.CANCELLED
                                    .withDescription("downstream finished").asRuntimeException());
                        }))
                        .build())
                .build()
                .start();

        // ② devh 的**真**客户端装配面（真 Spring 容器 + devh 的自动配置）
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
    }

    // ────────────────────────────────────────────────────────────────────────────
    // 形态①：devh 的 @GrpcClient stub（**不调 applyTo**）⇒ 出向调用继承请求预算
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("形态①：devh 的 @GrpcClient stub（裸写法、不调 applyTo）⇒ 出向调用在请求预算处失败，不挂满下游")
    void devhStubInheritsRequestDeadline() {
        StubHolder holder = ctx.getBean(StubHolder.class);

        long start = System.nanoTime();
        StatusRuntimeException thrown = null;
        try (GoStyleRequestDeadline.Scope ignored = GoStyleRequestDeadline.bindAbsoluteNanos(
                System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(BUDGET_MS))) {
            // ⚠️ **不调 applyTo(stub)** —— 测的正是「域侧零改动」那个形态
            holder.stub.hang();
        } catch (StatusRuntimeException e) {
            thrown = e;
        }
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertThat(thrown)
                .as("出向调用必须在请求预算处失败（而不是挂满下游的 %ds）", HANG_MS / 1000)
                .isNotNull();
        assertThat(thrown.getStatus().getCode())
                .as("失败码必须是 DEADLINE_EXCEEDED（= deadline 到点）")
                .isEqualTo(Status.Code.DEADLINE_EXCEEDED);
        assertThat(elapsedMs)
                .as("出向调用必须在 %dms 附近结束（实际 %dms）", BUDGET_MS, elapsedMs)
                .isLessThan(HANG_MS - HANG_TOLERANCE_MS);
        assertThat(downstreamFinished.get())
                .as("下游此刻还没跑完（⇒ 失败来自 deadline，不是下游提前返回）")
                .isFalse();
    }

    // ────────────────────────────────────────────────────────────────────────────
    // 形态②：负控 —— 裸 channel（不经 devh 装配）⇒ 挂满
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("形态②（负控）：同一条调用走裸 channel（不经 devh 装配）⇒ 挂满下游 ⇒ 形态①的绿来自 devh 装配")
    void rawChannelDoesNotInheritRequestDeadline() {
        ManagedChannel raw = InProcessChannelBuilder.forName(CHANNEL_NAME).build();
        try {
            long start = System.nanoTime();
            StatusRuntimeException thrown = null;
            try (GoStyleRequestDeadline.Scope ignored = GoStyleRequestDeadline.bindAbsoluteNanos(
                    System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(BUDGET_MS))) {
                ClientCalls.blockingUnaryCall(raw, HANG_METHOD, CallOptions.DEFAULT, new byte[0]);
            } catch (StatusRuntimeException e) {
                thrown = e;
            }
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            assertThat(elapsedMs)
                    .as("裸 channel 不经 devh 的全局拦截器 ⇒ 没有任何 deadline ⇒ 必然挂满下游的 %ds"
                            + "（这正是 tools/semantic-probe/deadline-downstream 探针当前的处境）", HANG_MS / 1000)
                    .isGreaterThanOrEqualTo(HANG_MS - HANG_TOLERANCE_MS);
            assertThat(thrown)
                    .as("挂满之后是下游自己返回的 CANCELLED（不是 deadline）")
                    .isNotNull();
            assertThat(thrown.getStatus().getCode())
                    .isEqualTo(Status.Code.CANCELLED);
        } finally {
            raw.shutdownNow();
        }
    }

    // ────────────────────────────────────────────────────────────────────────────
    // 形态③：装配面 —— devh 交出去的 channel 被包装过，且与 stub 用的是同一条
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("形态③：devh stub 的 channel 被 ClientInterceptors 包过（不是裸 ManagedChannel）")
    void devhChannelIsWrappedByGlobalInterceptors() {
        StubHolder holder = ctx.getBean(StubHolder.class);

        assertThat(holder.stub.getChannel())
                .as("devh 的 channel factory 会用 ClientInterceptors.interceptForward 包一层"
                        + " ⇒ 交给 stub 的 channel 不是裸 ManagedChannel"
                        + "（域侧 AdminRpcClient.java:104-107 记的就是这个现象）")
                .isNotInstanceOf(ManagedChannel.class);
    }

    @Test
    @DisplayName("形态④：devh channel factory 交出来的那条 channel 直接发调用 ⇒ 同样继承 deadline（装配点 = channel factory）")
    void devhChannelFactoryChannelItselfInheritsDeadline() {
        Channel fromFactory = ctx.getBean(GrpcChannelFactory.class).createChannel(CHANNEL_NAME);

        long start = System.nanoTime();
        StatusRuntimeException thrown = null;
        try (GoStyleRequestDeadline.Scope ignored = GoStyleRequestDeadline.bindAbsoluteNanos(
                System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(BUDGET_MS))) {
            ClientCalls.blockingUnaryCall(fromFactory, HANG_METHOD, CallOptions.DEFAULT, new byte[0]);
        } catch (StatusRuntimeException e) {
            thrown = e;
        }
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertThat(thrown)
                .as("devh channel factory 造的 channel 必须带全局拦截器 ⇒ 出向调用在预算处失败")
                .isNotNull();
        assertThat(thrown.getStatus().getCode())
                .isEqualTo(Status.Code.DEADLINE_EXCEEDED);
        assertThat(elapsedMs)
                .as("出向调用必须在 %dms 附近结束（实际 %dms）", BUDGET_MS, elapsedMs)
                .isLessThan(HANG_MS - HANG_TOLERANCE_MS);
    }

    // ────────────────────────────────────────────────────────────────────────────
    // 测试装置
    // ────────────────────────────────────────────────────────────────────────────

    /** devh 的客户端装配面：{@code @ImportAutoConfiguration} 拉起 devh 的自动配置。 */
    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration(GrpcClientAutoConfiguration.class)
    @Import(GoStyleProtectionConfiguration.class)
    static class DevhClientApp {

        /**
         * <b>用 devh 自己的 in-process 工厂</b>（它与生产的 netty 工厂<b>同属</b>
         * {@code AbstractChannelFactory} ⇒ ⇒ <b>走的是同一条 {@code createChannel} 实现</b>
         * ⇒ ⇒ 装全局拦截器的那一步<b>一字不差</b>）。
         *
         * <p>⚠️ 提供这个 Bean 会让 devh 的 netty 工厂（{@code @ConditionalOnMissingBean}）退让。
         */
        @Bean
        GrpcChannelFactory grpcChannelFactory(GrpcChannelsProperties props,
                                              GlobalClientInterceptorRegistry registry,
                                              List<GrpcChannelConfigurer> configurers) {
            return new InProcessChannelFactory(props, registry, configurers);
        }

        @Bean
        StubHolder stubHolder() {
            return new StubHolder();
        }
    }

    /** {@code @GrpcClient} 字段注入（devh 的标准用法）。 */
    static class StubHolder {
        @net.devh.boot.grpc.client.inject.GrpcClient(CHANNEL_NAME)
        DevhDeadlineServiceGrpc.DevhDeadlineBlockingStub stub;
    }

    /**
     * 最小 gRPC 服务端工厂 + 阻塞 stub。
     *
     * <p>⚠️ <b>为什么手写而不复用某个域的 proto</b>：共享层的测试<b>不允许依赖域侧 jar</b>
     * ⇒ ⇒ 且 devh 的 stub 工厂要求「<b>静态 {@code newBlockingStub(Channel)} 工厂方法</b>
     * ＋ 类名以 {@code BlockingStub} 结尾 ＋ 继承 {@code AbstractBlockingStub}」。
     */
    public static final class DevhDeadlineServiceGrpc {

        public static DevhDeadlineBlockingStub newBlockingStub(Channel channel) {
            return new DevhDeadlineBlockingStub(channel, CallOptions.DEFAULT);
        }

        /** 阻塞 stub（形态与生成的 proto stub 同款：{@code getChannel().newCall(...)}）。 */
        public static final class DevhDeadlineBlockingStub
                extends AbstractBlockingStub<DevhDeadlineBlockingStub> {

            DevhDeadlineBlockingStub(Channel channel, CallOptions callOptions) {
                super(channel, callOptions);
            }

            @Override
            protected DevhDeadlineBlockingStub build(Channel channel, CallOptions callOptions) {
                return new DevhDeadlineBlockingStub(channel, callOptions);
            }

            /** 调下游那个挂起的方法。 */
            public byte[] hang() {
                return ClientCalls.blockingUnaryCall(
                        getChannel(), HANG_METHOD, getCallOptions(), new byte[0]);
            }
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
