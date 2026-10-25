package com.rag.message.message.rpc;

import com.rag.message.message.proto.BaseResp;
import com.rag.message.message.proto.MarkNotificationReq;
import com.rag.message.message.proto.MessageServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerCallHandler;
import io.grpc.ServerServiceDefinition;
import io.grpc.stub.ServerCalls;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import net.devh.boot.grpc.server.serverfactory.GrpcServerConfigurer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.rag.common.observability.ObservabilityConstants.ATTR_DURATION;
import static com.rag.common.observability.ObservabilityConstants.ATTR_SLOW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 <b>slow threshold 分支的独立守门</b>（global L8 · 逐域推广，样板 = admin-rpc 那份）。
 *
 * <h2>为什么它必须是「独立」的一条</h2>
 * <p>「服务端超时打断在飞处理」（X-5 轴，已有 {@link MessageRpcTimeoutGateTest} 覆盖）
 * 与「slow threshold 标记慢调用」是<b>两条不同的路径</b>：
 * <ul>
 *   <li>超时路径 ⇒ {@code close(DEADLINE_EXCEEDED)} ⇒ {@code markTimeout}（打 {@code sea.timeout}）；</li>
 *   <li>slow 路径 ⇒ {@code close(OK)} ⇒ {@code markDurationAndSlow}（打 {@code sea.duration_ms}
 *       <b>与</b> {@code sea.slow}）。</li>
 * </ul>
 * ⇒ 「超时那条守门全绿」<b>证明不了</b> slow threshold 这条路径被走过。
 *
 * <h2>Go 出处（逐字对照，行为层）</h2>
 * <pre>
 *   service/common/observability/rpc.go:76-99  markRPCResult
 *       if err == nil { MarkDurationAndSlow(ctx, duration, slowThreshold, attrs...); return }
 *   service/common/observability/observability.go:131-144  MarkDurationAndSlow
 *       base := []attribute.KeyValue{ attribute.Float64(AttrDuration, durationMilliseconds(duration)) }
 *       if slowThreshold &gt; 0 &amp;&amp; duration &gt; slowThreshold {
 *           base = append(base, attribute.Bool(AttrSlow, true))
 *       }
 *       span.SetAttributes(append(base, attrs...)...)
 *   service/message/rpc/message.go:44
 *       s.AddUnaryInterceptors(observability.NewUnaryServerInterceptor(rpcTimeout, observability.SlowThreshold()))
 * </pre>
 * ⇒ 语义 = <b>「只有成功（OK）的调用才走 slow 判定；且只有耗时超过阈值才打 slow 标记」</b>。
 * <p>⚠️ Go 的阈值来源：{@code observability.SlowThreshold()}（{@code observability.go:41-55}）
 * ⇒ env var {@code SEA_OBSERVABILITY_SLOW_THRESHOLD[_MS]} ⇒ 兜底 {@code DefaultSlowThreshold = 500ms}
 * （{@code observability.go:21}）。RAG 侧多一层 Spring 配置 {@code observability.slow-threshold}
 * （{@code ObservabilityProperties#getSlowThreshold()} → {@code RpcServerInterceptorFactory:77-79}）。
 *
 * <h2>为什么必须走「真 server + 真链 + 真 span」</h2>
 * <p>§3.3：纯 Mockito 单测不算「机制生效」的证据。
 * ⇒ 本类起一个<b>真 gRPC server</b>，用容器里那个 {@link GrpcServerConfigurer}
 * （= recover → breaker → shedding → 业务观测拦截器，<b>整条链</b>）装配它，
 * 注册一个「处理函数耗时 N ms 且成功返回」的服务，装一个<b>真 OTel SDK</b>
 * + 收集型 exporter ⇒ 断言<b>导出的 span 上真的有 {@code sea.slow}</b>。
 *
 * <h2>两条断言缺一不可</h2>
 * <ol>
 *   <li><b>慢的那条</b>：耗时 600ms &gt; 阈值 200ms ⇒ {@code sea.slow} 必须为 {@code true}；</li>
 *   <li><b>快的那条</b>：耗时 ≪ 阈值 ⇒ {@code sea.slow} 必须<b>不存在</b>
 *       ⇒ 证明这条路径是<b>真比较</b>出来的，而不是「恒为 true」。</li>
 * </ol>
 *
 * <h2>🔴 为什么慢调用占 <b>300ms</b> 而不是「远大于阈值」</h2>
 * <p>阈值配 200ms、慢调用占 300ms ⇒ 这个取值<b>同时</b>守住两条边：
 * <ol>
 *   <li><b>上边</b>：300ms &gt; 200ms ⇒ 必然被判为 slow ⇒ 走「配置生效」的那一侧
 *       （耗时由 latch 的等待时长<b>结构性地</b>保证 ≥ 300ms，不依赖机器快慢）；</li>
 *   <li><b>下边</b>：300ms &lt; <b>500ms</b>（= Go 的 {@code DefaultSlowThreshold}，
 *       {@code observability.go:21}）⇒ 若某一域的 {@code observability.slow-threshold}
 *       <b>配置被忽略</b>（回退到 500ms 兜底），本用例就会<b>红</b>
 *       ⇒ 这条守门顺带把「本域的阈值配置真的被线程到拦截器里」也钉住了。</li>
 * </ol>
 * <p>⚠️ <b>由此得到一条硬约束</b>：慢调用的占用时长必须 <b>&lt; 500ms</b>。
 * 若有人把它改成 600ms（远超 500ms），「配置被忽略」这条退化就会变成<b>假绿</b>
 * （600ms &gt; 500ms ⇒ 连兜底阈值也判 slow ⇒ 断言照样绿）。
 * <p>（admin-rpc 那份样板用 100ms 阈值 + 400ms 占用，同样满足「&lt; 500ms」
 * ⇒ 两份守门在这一点上口径一致；本类把阈值放到 200ms / 占用放到 300ms，
 * 是为了让「配置生效」与「配置被忽略」两侧各留 100ms 的余量。）
 *
 * <h2>🔴 本类对「空转」的额外封堵（L8 补齐的三条判别式断言）</h2>
 * <p>任务书 §3.1 的六条硬性要求在本类的落地形态：
 * <ol>
 *   <li>① 慢服务用 {@link ServerCalls#asyncUnaryCall}（标准派发入口）注册，<b>不是</b>裸
 *       {@link ServerCallHandler} ⇒ grpc-java 会把 {@code onHalfClose} 派发下去；</li>
 *   <li>② <b>断言 handler 真的被调用过</b>（{@code probe.entered.await(...)}）⇒ ① 一旦退化立刻红；</li>
 *   <li>③ <b>断言「客户端调用仍在飞时 handler 仍在跑」</b>（{@code assertFalse(probe.completed.get())}）
 *       ⇒ 「慢」必须对应<b>真实耗时</b>，而不是凭空来的标记；</li>
 *   <li>④ 慢服务用 <b>latch</b>（{@code probe.release.await(holdMillis, …)}）而不是
 *       {@code Thread.sleep(10_000)} ⇒ {@code finally} 里能<b>立刻</b>放行，不挂在 30s 上；</li>
 *   <li>⑤ {@link GrpcServerConfigurer} <b>按 Bean 名取</b>；</li>
 *   <li>⑥ channel target <b>显式写 {@code dns:///} scheme</b>。</li>
 * </ol>
 */
@SpringBootTest(
        classes = MessageRpcApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "grpc.server.port=0",
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN",
                // ⚠️ @GrpcClient 在 Bean 初始化期就建 channel（不是懒建）⇒ discovery://user-rpc
                //    在没有 Nacos 的测试环境里解析失败 ⇒ 改指到必然拒连的静态地址。
                "grpc.client.user-rpc.address=static://127.0.0.1:1",
                "grpc.client.admin-rpc.address=static://127.0.0.1:1",
                // ⚠️ **被测参数**：慢调用阈值压到 200ms（生产侧走 DefaultSlowThreshold=500ms，
                //    见 Go observability.go:21）。下面的 handler 占 600ms ⇒ 必然超过它。
                "observability.slow-threshold=200ms",
                // ⚠️ 关掉 CPU 自适应降载，让判据**只**取决于「慢调用标记」这一条。
                "rag.protection.cpu-shedding.enabled=false"
        })
@Testcontainers
class MessageRpcSlowThresholdGateTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("message_rpc_slow_threshold_it")
                    .withUsername("rag")
                    .withPassword("rag");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    /** 自建收集型 exporter（不引 {@code opentelemetry-sdk-testing}）。 */
    static final class CollectingExporter implements SpanExporter {
        final List<SpanData> spans = Collections.synchronizedList(new ArrayList<>());

        @Override
        public CompletableResultCode export(Collection<SpanData> batch) {
            spans.addAll(batch);
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }

        void clear() {
            spans.clear();
        }
    }

    private static final CollectingExporter EXPORTER = new CollectingExporter();

    @BeforeAll
    static void installSdk() {
        // 🔴 关键：把 GlobalOpenTelemetry 指向本测试的收集器，否则 span 是 no-op。
        //    必须在**上下文创建之前**装好 —— RpcInterceptor 在 Bean 创建期就取了 Tracer
        //    （RpcInterceptor.java:154），而 Spring 上下文在第一个用例实例化时才创建。
        GlobalOpenTelemetry.resetForTest();
        GlobalOpenTelemetry.set(OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(EXPORTER))
                        .build())
                .build());
    }

    @AfterAll
    static void uninstallSdk() {
        GlobalOpenTelemetry.resetForTest();
    }

    @Autowired private ApplicationContext ctx;

    /**
     * 被测的方法全名 —— span 名 = 方法全名（RpcInterceptor.java:162,164）。
     *
     * <p>⚠️ grpc-java 的 {@code MethodDescriptor#getFullMethodName()} 返回的是
     * {@code "service/method"}（<b>无前导斜杠</b>）⇒ 不是 {@code "/message.MessageService/…"}.
     */
    private static final String METHOD = "message.MessageService/MarkNotificationRead";

    /** 被测阈值（毫秒）—— 必须与上面 {@code observability.slow-threshold=200ms} 一致。 */
    private static final long SLOW_THRESHOLD_MILLIS = 200L;

    /** 慢服务占用的时长（毫秒）—— 必须显著大于阈值。 */
    private static final long SLOW_HOLD_MILLIS = 300L;

    @Test
    @DisplayName("慢调用（300ms > 阈值 200ms）必须被打上 sea.slow=true"
            + "（Go: observability.go:140-142 duration > slowThreshold ⇒ AttrSlow）")
    void slowCallMustBeMarkedSlow() throws Exception {
        SpanData span = callAndGetSpan(SLOW_HOLD_MILLIS);

        assertEquals(Boolean.TRUE, span.getAttributes().get(AttributeKey.booleanKey(ATTR_SLOW)),
                "耗时 " + SLOW_HOLD_MILLIS + "ms > 阈值 " + SLOW_THRESHOLD_MILLIS
                        + "ms ⇒ 必须打 sea.slow=true。"
                        + "实际 span 属性 = " + span.getAttributes());
        assertNotNull(span.getAttributes().get(AttributeKey.doubleKey(ATTR_DURATION)),
                "Go MarkDurationAndSlow 无条件打 AttrDuration（observability.go:138）⇒ RAG 也必须有 sea.duration_ms");
        double durationMs = span.getAttributes().get(AttributeKey.doubleKey(ATTR_DURATION));
        assertTrue(durationMs >= SLOW_HOLD_MILLIS - 50,
                "sea.duration_ms 必须反映 handler 的真实耗时（≈" + SLOW_HOLD_MILLIS + "ms），实际 = " + durationMs
                        + "ms。若远小于，说明比较用的不是真实耗时 ⇒ 这条守门是空的。");
    }

    @Test
    @DisplayName("快调用（≪ 阈值）必须**不带** sea.slow ⇒ 证明这条路径是真比较出来的")
    void fastCallMustNotBeMarkedSlow() throws Exception {
        SpanData span = callAndGetSpan(0L);

        assertNull(span.getAttributes().get(AttributeKey.booleanKey(ATTR_SLOW)),
                "耗时远小于阈值 " + SLOW_THRESHOLD_MILLIS + "ms ⇒ 不该打 sea.slow。若为 true，说明 slow 标记是"
                        + "「恒真」的，或阈值没被真正比较（Go: observability.go:140-142 要求 duration > slowThreshold 才打）。"
                        + "实际 span 属性 = " + span.getAttributes());
    }

    /**
     * 起一个真 server、调一次、返回该方法对应的 span。
     *
     * @param holdMillis handler 占用的时长（0 = 立即返回）
     */
    private SpanData callAndGetSpan(long holdMillis) throws Exception {
        // ⚠️ 必须按 Bean 名取：devh starter 自己还注册了 compressionServerConfigurer /
        //    decompressionServerConfigurer 两个同类型 Bean，ctx.getBean(GrpcServerConfigurer.class)
        //    会抛 NoUniqueBeanDefinitionException。我们要验的只有本模块自己那个。
        GrpcServerConfigurer configurer = ctx.getBean("rpcInterceptorConfigurer", GrpcServerConfigurer.class);
        assertNotNull(configurer, "rpcInterceptorConfigurer 必须产出 GrpcServerConfigurer Bean");

        ServerBuilder<?> builder = ServerBuilder.forPort(0);
        configurer.accept(builder);                  // ← 整条链（recover→breaker→shedding→业务拦截器）
        SlowHandlerProbe probe = new SlowHandlerProbe();
        Server server = builder.addService(slowMessageService(probe, holdMillis)).build().start();

        // ⚠️ **必须显式写 dns:/// scheme**：Nacos discovery 在全局 NameResolverRegistry 里
        //    注册了一个高优先级 provider，会把任何无 scheme 的 target 当作 serviceId 去 Nacos 拉实例。
        ManagedChannel channel = io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
                .forTarget("dns:///127.0.0.1:" + server.getPort()).usePlaintext().build();
        // ⚠️ 客户端调用放到**另一个线程** ⇒ 主线程才能在「调用仍在飞」时观察 handler 的状态（③）。
        ExecutorService client = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "slow-gate-client");
            t.setDaemon(true);
            return t;
        });
        try {
            EXPORTER.clear();
            var stub = MessageServiceGrpc.newBlockingStub(channel);
            // 必须**成功**返回：Go 的 slow 判定只在 err == nil 时走（rpc.go:86-89）。
            Future<BaseResp> pending = client.submit(() -> stub.withDeadlineAfter(20, TimeUnit.SECONDS)
                    .markNotificationRead(MarkNotificationReq.newBuilder()
                            .setUserId(1L).setNotificationId(2L).build()));

            // ② handler 必须**真的被调用过**（裸 ServerCallHandler 就是「压根没被调用」的那种情形）。
            assertTrue(probe.entered.await(10, TimeUnit.SECONDS),
                    "handler 必须**真的被调用过**。若为 false，说明 grpc-java 根本没把 onHalfClose "
                            + "派发下去（裸 ServerCallHandler 就是这种情形）⇒ 本用例验的不是慢调用标记，而是空调用。");

            // ③ 客户端调用仍在飞时，handler 必须**仍在跑** ⇒ 「慢」对应真实耗时。
            if (holdMillis > 0) {
                assertFalse(probe.completed.get(),
                        "客户端调用仍在飞时，handler 必须**仍在跑**（未完成）。"
                                + "若为 true，说明「慢」是凭空来的 ⇒ sea.slow 没有对应真实耗时。");
            }

            BaseResp resp = pending.get(20, TimeUnit.SECONDS);
            assertNotNull(resp, "本用例要的是一次**成功**的调用（Go: markRPCResult 只在 err==nil 时走 slow 判定）");
            assertTrue(probe.completed.get(), "响应回来时 handler 必须已经跑完");

            Optional<SpanData> found = EXPORTER.spans.stream()
                    .filter(s -> METHOD.equals(s.getName()))
                    .findFirst();
            assertTrue(found.isPresent(),
                    "必须导出名为 " + METHOD + " 的 span。实际导出的 = "
                            + EXPORTER.spans.stream().map(SpanData::getName).toList());
            return found.get();
        } finally {
            probe.release.countDown();               // 放行 handler，让它别挂在超时上
            client.shutdownNow();
            channel.shutdownNow();
            server.shutdownNow();
        }
    }

    /**
     * 注册一个「处理函数耗时 {@code holdMillis} 且成功返回」的服务。
     *
     * <p>⚠️ **必须**用 grpc-java 的标准派发入口 {@link ServerCalls#asyncUnaryCall}：
     * 裸 {@link ServerCallHandler} 不会被派发 {@code onHalfClose} ⇒ 方法体一次都不执行
     * ⇒ span 永不 end ⇒ 导出列表为空（这正是 §3.3「绿测试可能是错误答案的守卫」的形态）。
     */
    private static ServerServiceDefinition slowMessageService(SlowHandlerProbe probe, long holdMillis) {
        ServerCallHandler<MarkNotificationReq, BaseResp> handler =
                ServerCalls.asyncUnaryCall((req, observer) -> {
                    probe.entered.countDown();
                    try {
                        // 🔴 用 **latch**（带超时）而不是 Thread.sleep ⇒
                        // ① finally 里可以**立刻**放行，不必等满整个占用时长；
                        // ② 「调用在飞时它还在跑」这条断言写得出来（③）。
                        probe.release.await(holdMillis, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    probe.completed.set(true);
                    observer.onNext(BaseResp.getDefaultInstance());
                    observer.onCompleted();
                });
        // ⚠️ 自建一个只含 MarkNotificationRead 这一个方法的 ServiceDescriptor：
        //    ① 必须用生成代码里的**同一个** getMarkNotificationReadMethod() 实例，否则报
        //       "Bound method ... not same instance as method in service descriptor"；
        //    ② 描述符里的每个方法都必须有绑定。
        io.grpc.ServiceDescriptor onlyMarkRead = io.grpc.ServiceDescriptor
                .newBuilder(MessageServiceGrpc.getServiceDescriptor().getName())
                .addMethod(MessageServiceGrpc.getMarkNotificationReadMethod())
                .build();
        return ServerServiceDefinition.builder(onlyMarkRead)
                .addMethod(MessageServiceGrpc.getMarkNotificationReadMethod(), handler)
                .build();
    }

    /**
     * 「处理函数确实被调用过」+「调用在飞时它仍在跑」这两个证据。
     *
     * <p>⚠️ 这两个信号是<b>空转守门的解药</b>：只看「客户端拿到什么 status、耗时多久」，
     * 而那两件事在「handler 压根没执行」时<b>同样成立</b>。
     */
    private static final class SlowHandlerProbe {
        /** handler 真的被调用 ⇒ countDown。 */
        final CountDownLatch entered = new CountDownLatch(1);
        /** 测试收尾时放行 ⇒ 让 handler 立刻退出。 */
        final CountDownLatch release = new CountDownLatch(1);
        /** handler 跑完了。 */
        final AtomicBoolean completed = new AtomicBoolean(false);
    }
}
