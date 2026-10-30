package com.rag.common.protection;

import com.rag.common.observability.GoStyleRequestDeadline;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>守门：客户端硬上界（{@code cap-millis}）这条「<b>域侧只配参数</b>」的路，真跑一次。</b>
 *
 * <h2>🔴 它补的是哪一个洞（2026-10-31 · G4 lane · P2-T04 共享层那半）</h2>
 * <p>P2-T04 的派工面要把「零 deadline 的 11 个类补 2000ms 硬上界」交给<b>各域</b>。
 * ⇒ ⇒ 若域侧各自实现一份封顶逻辑 ⇒ ⇒ 每补一个域就多一份副本 ⇒ ⇒ **副本数 = 漂移面**
 * ⇒ ⇒ 而且每份副本都要各自保证「取 min」语义 ⇒ ⇒ 极易与 Go 的
 * {@code context.WithTimeout}（取更早者）反向。
 * <p>⇒ ⇒ 本仓<b>已经有</b>等价能力：共享层的
 * {@link GoStyleOutboundDeadlineInterceptor}（带 devh 的
 * {@code @GrpcGlobalClientInterceptor} ⇒ 进 devh 的全局客户端拦截器 registry
 * ⇒ ⇒ 装在<b>每个</b>出向 devh 调用上）。
 * <p>⇒ ⇒ <b>但此前只有「属性默认值」的断言</b>
 * （{@code GoStyleOutboundDeadlineRpcAssemblyTest}：{@code enabled=true} ＋
 * {@code capMillis=0}）⇒ ⇒ <b>没有一条证据说「把 cap 配上去，它真的在运行期封顶」</b>
 * ⇒ ⇒ AGENTS.md §3.3.1「框架机制必须验证实际生效」＋ §3.3.3「配了却没生效」同族
 * ⇒ ⇒ <b>这就是本类要钉住的那一格</b>。
 *
 * <h2>三个形态（只有「cap 有没有配」这一个变量不同）</h2>
 * <ul>
 *   <li><b>形态①</b>：{@code cap-millis=2000} ＋ <b>没有入向请求预算</b>
 *       ⇒ ⇒ 出向调用在 <b>2000ms</b> 处失败（DEADLINE_EXCEEDED）⇒ ⇒ <b>封顶生效</b>；</li>
 *   <li><b>形态②（负控）</b>：{@code cap-millis=0}（不启用）＋ 同样<b>没有</b>入向预算
 *       ⇒ ⇒ 出向调用<b>挂满</b>下游的 5s ⇒ ⇒ <b>形态①的绿来自 cap，不是「in-process 自己会超时」</b>；</li>
 *   <li><b>形态③</b>：{@code cap-millis=2000} ＋ 入向预算 300ms
 *       ⇒ ⇒ 出向调用在 <b>300ms</b> 处失败 ⇒ ⇒ <b>取 min</b>（不是「cap 覆盖请求预算」）。</li>
 * </ul>
 *
 * <h2>Go 出处（为什么「客户端硬上界」该是拦截器级、且取 min）</h2>
 * <pre>
 *   go-zero@v1.10.2/zrpc/client.go:70-71
 *       if c.Timeout &gt; 0 { opts = append(opts, WithTimeout(time.Duration(c.Timeout)*time.Millisecond)) }
 *   go-zero@v1.10.2/zrpc/internal/client.go:115-116
 *       if c.middlewares.Timeout { interceptors = append(interceptors, clientinterceptors.TimeoutInterceptor(timeout)) }
 *   go-zero@v1.10.2/zrpc/internal/clientinterceptors/timeoutinterceptor.go:25
 *       ⇒ context.WithTimeout(ctx, t) ⇒ 【取更早者】（min 语义）
 *   go-zero@v1.10.2/zrpc/config.go:31
 *       Timeout int64 `json:",default=2000"` ⇒ 2000ms 是**默认值**，各域可覆盖
 * </pre>
 * ⇒ ⇒ Go 侧是<b>拦截器级</b>、<b>每个</b>出向调用都带 ⇒ ⇒ RAG 侧同形也该是拦截器级
 * ⇒ ⇒ **域侧零代码改动，只需配一个参数** ⇒ ⇒ 这正是本类要证明的事。
 *
 * <h2>⚠️ 本类<b>不</b>证明什么（诚实边界）</h2>
 * <ul>
 *   <li>本类只证明<b>共享层这一个入口</b>在 devh 客户端上真的封顶
 *       ⇒ ⇒ <b>各域是否真的配上 {@code cap-millis}</b> 属<b>域侧 yml</b>（P2-T04 本体），
 *       本类管不到；</li>
 *   <li>本类用 devh 的 <b>in-process</b> channel factory（与生产同一 {@code createChannel} 实现）
 *       ⇒ ⇒ 上游 hop 的传输层与生产不同 ⇒ ⇒ <b>传输层不对称</b>如实记（结论不受影响：
 *       测的是「deadline 到点 ⇒ 立即失败」这个语义，不依赖传输层）。</li>
 * </ul>
 */
class GoStyleOutboundDeadlineClientCapGateTest {

    /** devh 里的 channel 名 = in-process 服务端名 = {@code @GrpcClient} 的值。 */
    private static final String CHANNEL_NAME = "devh-outbound-deadline-cap-downstream";

    private static final String SERVICE_NAME = "test.DevhCapSvc";

    /** 下游挂起时长（毫秒）—— 远大于 cap ⇒ 挂满就说明「没有上界」。 */
    private static final long HANG_MS = 5_000L;

    /** 客户端硬上界（毫秒）—— 本类的被测参数。 */
    private static final long CAP_MS = 2_000L;

    /** 入向请求预算（毫秒）—— 形态③ 用。 */
    private static final long INBOUND_BUDGET_MS = 300L;

    /** 「超过这个毫秒数就算挂满」的判据。 */
    private static final long HANG_TOLERANCE_MS = 500L;

    /** 形态①/③ 的上界判据：不得早于 cap 的 3/4（⇒ 排除「别的机制提前掐断」）。 */
    private static final long CAP_LOWER_BOUND_MS = CAP_MS * 3 / 4;

    /** 形态③ 的判据：必须明显早于 cap（⇒ 证明取 min 而不是覆盖）。 */
    private static final long MIN_SEMANTICS_MAX_MS = CAP_MS / 2;

    /** 形态标识（下游按请求首字节回填 ⇒ 避免形态之间互相污染）。 */
    private static final byte SCENARIO_CAP_ONLY = 1;
    private static final byte SCENARIO_NO_CAP = 2;
    private static final byte SCENARIO_MIN = 3;

    /** 下游已跑完的场景（按形态标识分桶）。 */
    private static final Set<Byte> completedScenarios = ConcurrentHashMap.newKeySet();

    private static final MethodDescriptor<byte[], byte[]> HANG_METHOD =
            MethodDescriptor.<byte[], byte[]>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(SERVICE_NAME + "/Hang")
                    .setRequestMarshaller(new ByteArrayMarshaller())
                    .setResponseMarshaller(new ByteArrayMarshaller())
                    .build();

    private static Server downstream;

    /** 形态①/③ 的上下文：{@code cap-millis=2000}。 */
    private static ConfigurableApplicationContext ctxWithCap;

    /** 形态②（负控）的上下文：{@code cap-millis=0}（= 不启用）。 */
    private static ConfigurableApplicationContext ctxWithoutCap;

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
                            completedScenarios.add(req.length > 0 ? req[0] : (byte) -1);
                            obs.onError(Status.CANCELLED
                                    .withDescription("downstream finished").asRuntimeException());
                        }))
                        .build())
                .build()
                .start();

        // ② devh 的**真**客户端装配面（真 Spring 容器 + devh 的自动配置）
        ctxWithCap = new SpringApplicationBuilder(DevhClientApp.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "management.otlp.tracing.export.enabled=false",
                        "rag.protection.outbound-deadline.cap-millis=" + CAP_MS)
                .run();

        // ③ 负控上下文：**只**差一个属性（cap 不启用）
        ctxWithoutCap = new SpringApplicationBuilder(DevhClientApp.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "management.otlp.tracing.export.enabled=false",
                        "rag.protection.outbound-deadline.cap-millis=0")
                .run();
    }

    @AfterAll
    static void tearDown() {
        if (ctxWithCap != null) {
            ctxWithCap.close();
        }
        if (ctxWithoutCap != null) {
            ctxWithoutCap.close();
        }
        if (downstream != null) {
            downstream.shutdownNow();
        }
    }

    // ────────────────────────────────────────────────────────────────────────────
    // 形态①：只有 cap（没有入向预算）⇒ 在 cap 处失败
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("形态①：cap-millis=2000 且【没有入向请求预算】⇒ 出向调用在 2000ms 处失败 ⇒「域侧只配参数」这条路真的通")
    void clientCapAppliesWithoutInboundBudget() {
        StubHolder holder = ctxWithCap.getBean(StubHolder.class);

        long start = System.nanoTime();
        StatusRuntimeException thrown = null;
        try {
            // ⚠️ **不绑**任何入向预算 ⇒ ⇒ 这条调用上**只有** cap 一个约束
            holder.stub.hang(new byte[] {SCENARIO_CAP_ONLY});
        } catch (StatusRuntimeException e) {
            thrown = e;
        }
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertThat(thrown)
                .as("出向调用必须在客户端硬上界处失败（而不是挂满下游的 %ds）", HANG_MS / 1000)
                .isNotNull();
        assertThat(thrown.getStatus().getCode())
                .as("失败码必须是 DEADLINE_EXCEEDED（= deadline 到点）")
                .isEqualTo(Status.Code.DEADLINE_EXCEEDED);
        assertThat(elapsedMs)
                .as("必须在 cap（%dms）附近结束（实际 %dms）⇒ 既不能提前（别的机制），也不能挂满", CAP_MS, elapsedMs)
                .isGreaterThanOrEqualTo(CAP_LOWER_BOUND_MS)
                .isLessThan(HANG_MS - HANG_TOLERANCE_MS);
        assertThat(completedScenarios.contains(SCENARIO_CAP_ONLY))
                .as("下游此刻还没跑完（⇒ 失败来自 cap，不是下游提前返回）")
                .isFalse();
    }

    // ────────────────────────────────────────────────────────────────────────────
    // 形态②（负控）：cap 不启用 ⇒ 挂满
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("形态②（负控）：cap-millis=0（不启用）⇒ 出向调用挂满下游 ⇒ 形态①的绿来自 cap 本身")
    void withoutClientCapTheCallHangs() {
        StubHolder holder = ctxWithoutCap.getBean(StubHolder.class);

        long start = System.nanoTime();
        StatusRuntimeException thrown = null;
        try {
            // 同样**不绑**入向预算 ⇒ ⇒ 唯一差别就是 cap 没配
            holder.stub.hang(new byte[] {SCENARIO_NO_CAP});
        } catch (StatusRuntimeException e) {
            thrown = e;
        }
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertThat(thrown)
                .as("负控里下游会跑完并回 CANCELLED（而不是 DEADLINE_EXCEEDED）")
                .isNotNull();
        assertThat(thrown.getStatus().getCode())
                .as("cap 不启用 ⇒ 没有 deadline ⇒ 只能等下游自己结束（CANCELLED）")
                .isEqualTo(Status.Code.CANCELLED);
        assertThat(elapsedMs)
                .as("负控必须挂满下游的 %dms（实际 %dms）⇒ 否则形态①的绿就没有鉴别力", HANG_MS, elapsedMs)
                .isGreaterThanOrEqualTo(HANG_MS - HANG_TOLERANCE_MS);
        assertThat(completedScenarios.contains(SCENARIO_NO_CAP))
                .as("下游确实跑完了（⇒ 这一条确实没有 deadline）")
                .isTrue();
    }

    // ────────────────────────────────────────────────────────────────────────────
    // 形态③：cap 与入向预算并存 ⇒ 取 min
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("形态③：cap-millis=2000 且入向预算 300ms ⇒ 在 300ms 处失败 ⇒ 取 min（Go 的 WithTimeout 语义），不是覆盖")
    void clientCapTakesMinWithInboundBudget() {
        StubHolder holder = ctxWithCap.getBean(StubHolder.class);

        long start = System.nanoTime();
        StatusRuntimeException thrown = null;
        try (GoStyleRequestDeadline.Scope ignored = GoStyleRequestDeadline.bindAbsoluteNanos(
                System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(INBOUND_BUDGET_MS))) {
            holder.stub.hang(new byte[] {SCENARIO_MIN});
        } catch (StatusRuntimeException e) {
            thrown = e;
        }
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertThat(thrown)
                .as("出向调用必须在请求预算处失败")
                .isNotNull();
        assertThat(thrown.getStatus().getCode())
                .as("失败码必须是 DEADLINE_EXCEEDED")
                .isEqualTo(Status.Code.DEADLINE_EXCEEDED);
        assertThat(elapsedMs)
                .as("必须在入向预算（%dms）附近结束（实际 %dms）⇒ 取 min ⇒ 不能被 cap（%dms）放宽",
                        INBOUND_BUDGET_MS, elapsedMs, CAP_MS)
                .isLessThan(MIN_SEMANTICS_MAX_MS);
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
         * <b>用 devh 自己的 in-process 工厂</b>（它与生产的 netty 工厂同属
         * {@code AbstractChannelFactory} ⇒ ⇒ 走的是同一条 {@code createChannel} 实现
         * ⇒ ⇒ 装全局拦截器的那一步一字不差）。
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
        DevhCapServiceGrpc.DevhCapBlockingStub stub;
    }

    /**
     * 最小 gRPC 服务端工厂 + 阻塞 stub。
     *
     * <p>⚠️ <b>为什么手写而不复用某个域的 proto</b>：共享层的测试<b>不允许依赖域侧 jar</b>
     * ⇒ ⇒ 且 devh 的 stub 工厂要求「静态 {@code newBlockingStub(Channel)} 工厂方法
     * ＋ 类名以 {@code BlockingStub} 结尾 ＋ 继承 {@code AbstractBlockingStub}」。
     */
    public static final class DevhCapServiceGrpc {

        public static DevhCapBlockingStub newBlockingStub(Channel channel) {
            return new DevhCapBlockingStub(channel, CallOptions.DEFAULT);
        }

        /** 阻塞 stub（形态与生成的 proto stub 同款：{@code getChannel().newCall(...)}）。 */
        public static final class DevhCapBlockingStub extends AbstractBlockingStub<DevhCapBlockingStub> {

            DevhCapBlockingStub(Channel channel, CallOptions callOptions) {
                super(channel, callOptions);
            }

            @Override
            protected DevhCapBlockingStub build(Channel channel, CallOptions callOptions) {
                return new DevhCapBlockingStub(channel, callOptions);
            }

            /**
             * 调下游那个挂起的方法。
             *
             * @param payload 形态标识（下游按它的首字节回填「本形态跑完了」）
             */
            public byte[] hang(byte[] payload) {
                return ClientCalls.blockingUnaryCall(
                        getChannel(), HANG_METHOD, getCallOptions(),
                        payload == null ? new byte[0] : payload);
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
