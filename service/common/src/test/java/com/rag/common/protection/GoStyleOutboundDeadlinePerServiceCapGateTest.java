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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>守门：「按下游 gRPC 服务全名覆盖」的客户端硬上界，真跑一次。</b>
 *
 * <h2>🔴 它补的是哪一个洞（2026-10-31 · 合并收口执行者 · 代 G4 落 per-client 上界）</h2>
 * <p>共享层的 {@link GoStyleOutboundDeadlineInterceptor} 带 devh 的
 * {@code @GrpcGlobalClientInterceptor} ⇒ ⇒ 它装在<b>每一个</b>出向调用上
 * ⇒ ⇒ 而 {@code cap-millis} 是<b>组件级一个值</b> ⇒ ⇒
 * <b>同一个进程里，不同出向方向无法有不同上界</b>。
 * <p>实测反例（{@code article-api} · Go 真源
 * {@code service/article/api/etc/article-api.yaml}）：
 * <pre>
 *   ArticleRpcConf.Timeout: 70000  ⇒ article-rpc 方向 = 70s
 *   UserRpcConf:（无 Timeout）      ⇒ 走 zrpc struct tag 默认 2000 ⇒ user-rpc 方向 = 2s
 * </pre>
 * ⇒ ⇒ 组件级配 <b>2000</b> ⇒ 把 article-rpc 方向从 70s <b>改严 35 倍</b>；
 * ⇒ ⇒ 组件级配 <b>70000</b> ⇒ 把 user-rpc 方向从 2s <b>放宽 35 倍</b>
 * ⇒ ⇒ <b>两个值都不对</b> ⇒ ⇒ 该组件只能「刻意不配 + 域侧内联副本」⇒ ⇒ 那正是漂移面。
 * <p>⇒ ⇒ 本类钉住新增的 {@code cap-millis-by-service} 这条路：
 * <b>共享层给唯一实现 ＋ 域侧只配参数</b>（与组件级那条同形，域侧仍<b>零代码改动</b>）。
 *
 * <h2>⚠️ 本类最关键的一格 = 形态③：<b>「覆盖」不是「min」</b></h2>
 * <p>若 per-service 与组件级之间取 <b>min</b> ⇒ 组件级 2000 + per-service 70000
 * ⇒ 结果仍是 2000 ⇒ ⇒ <b>「按方向分档」根本表达不出来</b>（article-rpc 方向仍被改严 35 倍）
 * ⇒ ⇒ 那这个机制就是<b>假绿</b>：加了参数、跑起来也绿，但**解决不了它要解决的那个问题**。
 * <p>⇒ ⇒ 形态③ 断言：组件级 2000 ＋ per-service 70000 ⇒ 出向调用<b>挂满下游</b>
 * ⇒ ⇒ 证明 per-service <b>取代</b>（而非 min）组件级。
 *
 * <h2>四个形态</h2>
 * <ul>
 *   <li><b>形态①</b>：组件级 {@code cap-millis=0}（不启用）＋ per-service 2000 ⇒
 *       ⇒ 出向调用在 <b>2000ms</b> 处失败 ⇒ ⇒ <b>per-service 自己就能封顶</b>；</li>
 *   <li><b>形态②（负控）</b>：组件级 {@code cap-millis=0} ＋ <b>没有</b> per-service
 *       ⇒ ⇒ 出向调用<b>挂满</b>下游的 5s ⇒ ⇒ <b>形态①的绿来自 per-service，不是「in-process 自己会超时」</b>；</li>
 *   <li><b>形态③</b>：组件级 {@code cap-millis=2000} ＋ per-service 70000
 *       ⇒ ⇒ 出向调用<b>挂满</b>（= 上界被<b>放宽</b>到 70000）⇒ ⇒ <b>覆盖</b>，不是 min；</li>
 *   <li><b>形态④（单位级）</b>：per-service <b>收紧</b>（组件级 70000 ＋ per-service 2000）
 *       ⇒ ⇒ 命中该服务 ⇒ 2000ms；<b>不</b>命中该服务 ⇒ 回落组件级 70000ms
 *       ⇒ ⇒ 证明「命中 ⇒ 取代；不命中 ⇒ 回落组件级」这条规则本身。</li>
 * </ul>
 *
 * <h2>Go 出处（为什么是「每方向一个上界」）</h2>
 * <pre>
 *   go-zero@v1.10.2/zrpc/client.go:70-71
 *       if c.Timeout &gt; 0 { opts = append(opts, WithTimeout(time.Duration(c.Timeout)*time.Millisecond)) }
 *   go-zero@v1.10.2/zrpc/config.go:31
 *       Timeout int64 `json:",default=2000"` ⇒ 默认值，**各 client 段可各自覆盖**
 * </pre>
 * ⇒ ⇒ Go 侧本就是<b>每个 client 段一个 Timeout</b> ⇒ ⇒ RAG 侧按下游服务名分档同形。
 *
 * <h2>⚠️ 本类<b>不</b>证明什么（诚实边界）</h2>
 * <ul>
 *   <li>本类只证明<b>共享层这一个入口</b>真按 per-service 封顶
 *       ⇒ ⇒ <b>各域是否真的配上 {@code cap-millis-by-service}</b> 属<b>域侧 yml</b>，本类管不到；</li>
 *   <li>本类用 devh 的 <b>in-process</b> channel factory（与生产同一 {@code createChannel} 实现）
 *       ⇒ ⇒ 上游 hop 的传输层与生产不同 ⇒ ⇒ <b>传输层不对称</b>如实记（结论不受影响）。</li>
 * </ul>
 */
class GoStyleOutboundDeadlinePerServiceCapGateTest {

    /** devh 里的 channel 名 = in-process 服务端名 = {@code @GrpcClient} 的值。 */
    private static final String CHANNEL_NAME = "devh-outbound-deadline-per-service-cap-downstream";

    /**
     * 下游 gRPC <b>服务全名</b> —— 它同时是
     * {@code MethodDescriptor#getServiceName()} 的值 ⇒ ⇒ 也是 per-service 覆盖表的键。
     */
    private static final String SERVICE_NAME = "test.DevhPerSvcCapSvc";

    /** 下游挂起时长（毫秒）—— 远大于 2000ms 的 cap ⇒ 挂满就说明「没有上界」。 */
    private static final long HANG_MS = 5_000L;

    /** per-service 上界（毫秒）—— 形态① 用。 */
    private static final long PER_SERVICE_CAP_MS = 2_000L;

    /** 组件级上界（毫秒）—— 形态③ 用（比 per-service 小 ⇒ 只有「覆盖」才能放宽）。 */
    private static final long COMPONENT_CAP_MS = 2_000L;

    /** 形态③ 的 per-service 值（毫秒）—— 比组件级大 ⇒ 覆盖后应挂满下游。 */
    private static final long PER_SERVICE_RELAX_MS = 70_000L;

    /** 形态④ 的 per-service 值（毫秒）—— 比组件级小 ⇒ 覆盖后应收紧。 */
    private static final long PER_SERVICE_TIGHTEN_MS = 2_000L;

    /** 形态④ 的组件级值（毫秒）。 */
    private static final long COMPONENT_RELAX_MS = 70_000L;

    /** 「超过这个毫秒数就算挂满」的判据。 */
    private static final long HANG_TOLERANCE_MS = 500L;

    /** 形态① 的上界判据：不得早于 cap 的 3/4（⇒ 排除「别的机制提前掐断」）。 */
    private static final long CAP_LOWER_BOUND_MS = PER_SERVICE_CAP_MS * 3 / 4;

    /** 形态标识（下游按请求首字节回填 ⇒ 避免形态之间互相污染）。 */
    private static final byte SCENARIO_PER_SERVICE_ONLY = 1;
    private static final byte SCENARIO_NO_CAP = 2;
    private static final byte SCENARIO_OVERRIDE_RELAX = 3;

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

    /** 形态① 的上下文：组件级 cap=0 ＋ per-service cap=2000。 */
    private static ConfigurableApplicationContext ctxPerServiceOnly;

    /** 形态②（负控）的上下文：组件级 cap=0 ＋ 没有 per-service。 */
    private static ConfigurableApplicationContext ctxWithoutCap;

    /** 形态③ 的上下文：组件级 cap=2000 ＋ per-service cap=70000（= 覆盖放宽）。 */
    private static ConfigurableApplicationContext ctxOverrideRelax;

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

        // ② 形态①：组件级**不启用**（0）＋ 只配 per-service
        ctxPerServiceOnly = new SpringApplicationBuilder(DevhClientApp.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "management.otlp.tracing.export.enabled=false",
                        "rag.protection.outbound-deadline.cap-millis=0",
                        "rag.protection.outbound-deadline.cap-millis-by-service." + SERVICE_NAME + "="
                                + PER_SERVICE_CAP_MS)
                .run();

        // ③ 负控：**只**差一个属性（没有 per-service 那一项）
        ctxWithoutCap = new SpringApplicationBuilder(DevhClientApp.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "management.otlp.tracing.export.enabled=false",
                        "rag.protection.outbound-deadline.cap-millis=0")
                .run();

        // ④ 形态③：组件级 2000 ＋ per-service 70000 ⇒ 只有「覆盖」才能放宽
        ctxOverrideRelax = new SpringApplicationBuilder(DevhClientApp.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "management.otlp.tracing.export.enabled=false",
                        "rag.protection.outbound-deadline.cap-millis=" + COMPONENT_CAP_MS,
                        "rag.protection.outbound-deadline.cap-millis-by-service." + SERVICE_NAME + "="
                                + PER_SERVICE_RELAX_MS)
                .run();
    }

    @AfterAll
    static void tearDown() {
        if (ctxPerServiceOnly != null) {
            ctxPerServiceOnly.close();
        }
        if (ctxWithoutCap != null) {
            ctxWithoutCap.close();
        }
        if (ctxOverrideRelax != null) {
            ctxOverrideRelax.close();
        }
        if (downstream != null) {
            downstream.shutdownNow();
        }
    }

    // ────────────────────────────────────────────────────────────────────────────
    // 形态①：只有 per-service（组件级不启用）⇒ 在 per-service 上界处失败
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("形态①：组件级 cap=0 + per-service=2000 ⇒ 出向调用在 2000ms 处失败 ⇒ per-service 自己就能封顶")
    void perServiceCapAppliesWithoutComponentCap() {
        StubHolder holder = ctxPerServiceOnly.getBean(StubHolder.class);

        long start = System.nanoTime();
        StatusRuntimeException thrown = null;
        try {
            // ⚠️ **不绑**任何入向预算 ⇒ ⇒ 这条调用上**只有** per-service 上界一个约束
            holder.stub.hang(new byte[] {SCENARIO_PER_SERVICE_ONLY});
        } catch (StatusRuntimeException e) {
            thrown = e;
        }
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertThat(thrown)
                .as("出向调用必须在 per-service 上界（%dms）处失败（而不是挂满下游的 %ds）",
                        PER_SERVICE_CAP_MS, HANG_MS / 1000)
                .isNotNull();
        assertThat(thrown.getStatus().getCode())
                .as("失败码必须是 DEADLINE_EXCEEDED（= deadline 到点）")
                .isEqualTo(Status.Code.DEADLINE_EXCEEDED);
        assertThat(elapsedMs)
                .as("必须在 per-service 上界（%dms）附近结束（实际 %dms）⇒ 既不能提前（别的机制），也不能挂满",
                        PER_SERVICE_CAP_MS, elapsedMs)
                .isGreaterThanOrEqualTo(CAP_LOWER_BOUND_MS)
                .isLessThan(HANG_MS - HANG_TOLERANCE_MS);
        assertThat(completedScenarios.contains(SCENARIO_PER_SERVICE_ONLY))
                .as("下游此刻还没跑完（⇒ 失败来自上界，不是下游提前返回）")
                .isFalse();
    }

    // ────────────────────────────────────────────────────────────────────────────
    // 形态②（负控）：没有 per-service ⇒ 挂满
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("形态②（负控）：组件级 cap=0 且没有 per-service ⇒ 出向调用挂满下游 ⇒ 形态①的绿来自 per-service 本身")
    void withoutAnyCapTheCallHangs() {
        StubHolder holder = ctxWithoutCap.getBean(StubHolder.class);

        long start = System.nanoTime();
        StatusRuntimeException thrown = null;
        try {
            holder.stub.hang(new byte[] {SCENARIO_NO_CAP});
        } catch (StatusRuntimeException e) {
            thrown = e;
        }
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertThat(thrown)
                .as("负控里下游会跑完并回 CANCELLED（而不是 DEADLINE_EXCEEDED）")
                .isNotNull();
        assertThat(thrown.getStatus().getCode())
                .as("没有任何上界 ⇒ 没有 deadline ⇒ 只能等下游自己结束（CANCELLED）")
                .isEqualTo(Status.Code.CANCELLED);
        assertThat(elapsedMs)
                .as("负控必须挂满下游的 %dms（实际 %dms）⇒ 否则形态①的绿就没有鉴别力", HANG_MS, elapsedMs)
                .isGreaterThanOrEqualTo(HANG_MS - HANG_TOLERANCE_MS);
        assertThat(completedScenarios.contains(SCENARIO_NO_CAP))
                .as("下游确实跑完了（⇒ 这一条确实没有 deadline）")
                .isTrue();
    }

    // ────────────────────────────────────────────────────────────────────────────
    // 形态③：per-service 覆盖组件级（放宽）⇒ 挂满 —— 这一格证伪「min 语义」
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("形态③：组件级 cap=2000 + per-service=70000 ⇒ 出向调用挂满 ⇒ per-service 是「覆盖」组件级（不是 min）")
    void perServiceCapOverridesComponentCap() {
        StubHolder holder = ctxOverrideRelax.getBean(StubHolder.class);

        long start = System.nanoTime();
        StatusRuntimeException thrown = null;
        try {
            holder.stub.hang(new byte[] {SCENARIO_OVERRIDE_RELAX});
        } catch (StatusRuntimeException e) {
            thrown = e;
        }
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertThat(thrown)
                .as("per-service 覆盖后上界 = %dms（> 下游的 %dms）⇒ ⇒ 下游会先跑完", PER_SERVICE_RELAX_MS, HANG_MS)
                .isNotNull();
        assertThat(thrown.getStatus().getCode())
                .as("失败码必须是 CANCELLED（= 下游自己结束，不是 deadline 到点）")
                .isEqualTo(Status.Code.CANCELLED);
        assertThat(elapsedMs)
                .as("必须挂满下游的 %dms（实际 %dms）⇒ 若在 %dms 处失败 ⇒ 说明实现是 min 语义 ⇒ "
                        + "「按方向分档」根本表达不出来（article-rpc 方向仍被改严 35 倍）",
                        HANG_MS, elapsedMs, COMPONENT_CAP_MS)
                .isGreaterThanOrEqualTo(HANG_MS - HANG_TOLERANCE_MS);
        assertThat(completedScenarios.contains(SCENARIO_OVERRIDE_RELAX))
                .as("下游确实跑完了（⇒ 这一条没有 2000ms 的 deadline）")
                .isTrue();
    }

    // ────────────────────────────────────────────────────────────────────────────
    // 形态④（单位级）：命中 ⇒ 取代；不命中 ⇒ 回落组件级
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("形态④：per-service 收紧（组件级 70000 + per-service 2000）⇒ 命中 ⇒ 2000ms；不命中 ⇒ 回落 70000ms")
    void perServiceHitReplacesComponentCapAndMissFallsBack() {
        GoStyleOutboundDeadlineInterceptor interceptor = new GoStyleOutboundDeadlineInterceptor(
                true, COMPONENT_RELAX_MS, Map.of(SERVICE_NAME, PER_SERVICE_TIGHTEN_MS));

        CallOptions hit = interceptor.effectiveCallOptions(CallOptions.DEFAULT, SERVICE_NAME);
        long hitMs = hit.getDeadline().timeRemaining(TimeUnit.MILLISECONDS);
        assertThat(hitMs)
                .as("命中该服务 ⇒ per-service 取代组件级 ⇒ 应约为 %dms", PER_SERVICE_TIGHTEN_MS)
                .isGreaterThan(PER_SERVICE_TIGHTEN_MS - 500L)
                .isLessThanOrEqualTo(PER_SERVICE_TIGHTEN_MS);

        CallOptions miss = interceptor.effectiveCallOptions(CallOptions.DEFAULT, "test.OtherService");
        long missMs = miss.getDeadline().timeRemaining(TimeUnit.MILLISECONDS);
        assertThat(missMs)
                .as("不命中该服务 ⇒ 回落组件级 ⇒ 应约为 %dms", COMPONENT_RELAX_MS)
                .isGreaterThan(COMPONENT_RELAX_MS - 500L)
                .isLessThanOrEqualTo(COMPONENT_RELAX_MS);

        CallOptions nullService = interceptor.effectiveCallOptions(CallOptions.DEFAULT, null);
        long nullServiceMs = nullService.getDeadline().timeRemaining(TimeUnit.MILLISECONDS);
        assertThat(nullServiceMs)
                .as("服务名未知 ⇒ 回落组件级（不得因「取不到键」而丢掉上界）")
                .isGreaterThan(COMPONENT_RELAX_MS - 500L)
                .isLessThanOrEqualTo(COMPONENT_RELAX_MS);
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
        DevhPerSvcCapServiceGrpc.DevhPerSvcCapBlockingStub stub;
    }

    /**
     * 最小 gRPC 服务端工厂 + 阻塞 stub。
     *
     * <p>⚠️ <b>为什么手写而不复用某个域的 proto</b>：共享层的测试<b>不允许依赖域侧 jar</b>
     * ⇒ ⇒ 且 devh 的 stub 工厂要求「静态 {@code newBlockingStub(Channel)} 工厂方法
     * ＋ 类名以 {@code BlockingStub} 结尾 ＋ 继承 {@code AbstractBlockingStub}」。
     */
    public static final class DevhPerSvcCapServiceGrpc {

        public static DevhPerSvcCapBlockingStub newBlockingStub(Channel channel) {
            return new DevhPerSvcCapBlockingStub(channel, CallOptions.DEFAULT);
        }

        /** 阻塞 stub（形态与生成的 proto stub 同款：{@code getChannel().newCall(...)}）。 */
        public static final class DevhPerSvcCapBlockingStub extends AbstractBlockingStub<DevhPerSvcCapBlockingStub> {

            DevhPerSvcCapBlockingStub(Channel channel, CallOptions callOptions) {
                super(channel, callOptions);
            }

            @Override
            protected DevhPerSvcCapBlockingStub build(Channel channel, CallOptions callOptions) {
                return new DevhPerSvcCapBlockingStub(channel, callOptions);
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
