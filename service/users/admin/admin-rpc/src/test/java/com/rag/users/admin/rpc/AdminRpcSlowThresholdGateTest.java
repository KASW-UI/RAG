package com.rag.users.admin.rpc;

import com.rag.users.admin.proto.GetUserReq;
import com.rag.users.admin.proto.GetUserResp;
import com.rag.users.admin.proto.AdminServiceGrpc;
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
import java.util.concurrent.TimeUnit;

import static com.rag.common.observability.ObservabilityConstants.ATTR_DURATION;
import static com.rag.common.observability.ObservabilityConstants.ATTR_SLOW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.rag.users.admin.rpc.support.GoStyleHostPortPinning.pinContainerPort;

/**
 * 🔴 <b>slow threshold 分支的独立守门</b>（global L2 · X-5 残留的第二半）。
 *
 * <h2>为什么它必须是「独立」的一条</h2>
 * <p>「服务端超时打断在飞处理」与「slow threshold 标记慢调用」是<b>两条不同的路径</b>：
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
 *   <li><b>慢的那条</b>：耗时 400ms &gt; 阈值 100ms ⇒ {@code sea.slow} 必须为 {@code true}；</li>
 *   <li><b>快的那条</b>：耗时 ≪ 阈值 ⇒ {@code sea.slow} 必须<b>不存在</b>
 *       ⇒ 证明这条路径是<b>真比较</b>出来的，而不是「恒为 true」。</li>
 * </ol>
 */
@SpringBootTest(
        classes = AdminRpcApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "grpc.server.port=0",
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN",
                // 绕过 Redis：Lettuce 连接懒建，不真正连。
                "spring.data.redis.host=127.0.0.1",
                "spring.data.redis.port=1",
                // ⚠️ **被测参数**：慢调用阈值压到 100ms（生产侧走 DefaultSlowThreshold=500ms，
                //    见 Go observability.go:21）。下面的 handler 睡 400ms ⇒ 必然超过它。
                "observability.slow-threshold=100ms",
                // ⚠️ 关掉 CPU 自适应降载，让判据**只**取决于「慢调用标记」这一条。
                "rag.protection.cpu-shedding.enabled=false"
        })
@Testcontainers
class AdminRpcSlowThresholdGateTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("admin_rpc_slow_threshold_it")
                    .withUsername("rag")
                    .withPassword("rag").withCreateContainerCmdModifier(cmd -> pinContainerPort(cmd, 5432));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    /** 自建收集型 exporter（不引 {@code opentelemetry-sdk-testing}，与 AdminSpanWiringTest 同款）。 */
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

    /**
     * 本测试装进 {@link GlobalOpenTelemetry} 的那个 SDK —— 用来**断言前提成立**。
     *
     * <p>🔴 <b>为什么要断言</b>（第二十七轮 L6 · 缺口④ 的结论）：
     * 本守门的前提是「{@code GlobalOpenTelemetry} 在 Spring 上下文 refresh <b>之前</b>被换成本测试的 SDK」。
     * 但 {@code GoStyleOtelAgent} 里有一条「<b>已 set 就放弃</b>」的分支 ⇒ 这个前提今天是
     * <b>侥幸成立</b>（靠「没人先 set」），**不是时序保证**。
     * ⇒ 一旦将来有人先装了别的全局 SDK，本测试的 exporter 就会收不到 span ⇒ 用例以
     * 「找不到 span」这种**看不懂的形态**变红。
     * ⇒ 所以把前提<b>显式断言</b>：前提不成立时，失败信息直接说「前提不成立」。
     * <p>⚠️ <b>断言只能比 {@code getTracerProvider()}</b>，**不能**比 {@code GlobalOpenTelemetry.get()}
     * 本身 —— OTel Java 1.55.0 的 {@code set()} 存的是 {@code new ObfuscatedOpenTelemetry(sdk)}
     * <b>包装器</b>（{@code globalOpenTelemetry = new ObfuscatedOpenTelemetry(openTelemetry)}）
     * ⇒ {@code get()} 永远**不等于** sdk 本身 ⇒ 那样断言<b>恒红</b>（自曝错账 #4）。
     */
    private static OpenTelemetrySdk testSdk;

    @BeforeAll
    static void installSdk() {
        // 🔴 关键：把 GlobalOpenTelemetry 指向本测试的收集器，否则 span 是 no-op。
        //    必须在**上下文创建之前**装好 —— RpcInterceptor 在 Bean 创建期就取了 Tracer
        //    （RpcInterceptor.java:154），而 Spring 上下文在第一个用例实例化时才创建。
        GlobalOpenTelemetry.resetForTest();
        testSdk = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(EXPORTER))
                        .build())
                .build();
        GlobalOpenTelemetry.set(testSdk);
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
     * {@code "service/method"}（<b>无前导斜杠</b>，与 {@code getServiceName() + "/" + getBareMethodName()} 同形）
     * ⇒ 不是 {@code "/admin.AdminService/GetUser"}。
     * <p>（实测踩坑：先前写成带斜杠的形态 ⇒ 断言「找不到 span」⇒ 两个用例都红在<b>更早</b>的断言上。）
     */
    private static final String METHOD = "admin.AdminService/GetUser";

    @Test
    @DisplayName("慢调用（400ms > 阈值 100ms）必须被打上 sea.slow=true"
            + "（Go: observability.go:140-142 duration > slowThreshold ⇒ AttrSlow）")
    void slowCallMustBeMarkedSlow() throws Exception {
        SpanData span = callAndGetSpan(400L);

        assertEquals(Boolean.TRUE, span.getAttributes().get(AttributeKey.booleanKey(ATTR_SLOW)),
                "耗时 400ms > 阈值 100ms ⇒ 必须打 sea.slow=true。"
                        + "实际 span 属性 = " + span.getAttributes());
        assertNotNull(span.getAttributes().get(AttributeKey.doubleKey(ATTR_DURATION)),
                "Go MarkDurationAndSlow 无条件打 AttrDuration（observability.go:138）⇒ RAG 也必须有 sea.duration_ms");
    }

    @Test
    @DisplayName("快调用（≪ 阈值）必须**不带** sea.slow ⇒ 证明这条路径是真比较出来的")
    void fastCallMustNotBeMarkedSlow() throws Exception {
        SpanData span = callAndGetSpan(0L);

        assertNull(span.getAttributes().get(AttributeKey.booleanKey(ATTR_SLOW)),
                "耗时远小于阈值 100ms ⇒ 不该打 sea.slow。若为 true，说明 slow 标记是「恒真」的，"
                        + "或阈值没被真正比较（Go: observability.go:140-142 要求 duration > slowThreshold 才打）。"
                        + "实际 span 属性 = " + span.getAttributes());
    }

    /**
     * 起一个真 server、调一次、返回该方法对应的 span。
     *
     * @param sleepMillis handler 的耗时（0 = 立即返回）
     */
    private SpanData callAndGetSpan(long sleepMillis) throws Exception {
        GrpcServerConfigurer configurer = ctx.getBean("rpcInterceptorConfigurer", GrpcServerConfigurer.class);
        assertNotNull(configurer, "rpcInterceptorConfigurer 必须产出 GrpcServerConfigurer Bean");

        ServerBuilder<?> builder = ServerBuilder.forPort(0);
        configurer.accept(builder);                  // ← 整条链（recover→breaker→shedding→业务拦截器）
        Server server = builder.addService(slowAdminService(sleepMillis)).build().start();

        // ⚠️ **必须显式写 dns:/// scheme**：Nacos discovery 在全局 NameResolverRegistry 里
        //    注册了一个高优先级 provider，会把任何无 scheme 的 target 当作 serviceId 去 Nacos 拉实例。
        ManagedChannel channel = io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
                .forTarget("dns:///127.0.0.1:" + server.getPort()).usePlaintext().build();
        try {
            var stub = AdminServiceGrpc.newBlockingStub(channel);

            // 🔴🔴 **预热调用（必须有）**：JVM 里的**第一次** gRPC 调用要把
            //    protobuf 编解码 / netty 缓冲 / 拦截器链的类全部加载一遍
            //    ⇒ 这些成本落在 span 的 duration 里
            //    ⇒ 「快调用」那条会被误判成慢调用（**首调就红**）。
            //    实测：follow-rpc 首调 duration = 138.333ms > 本模块阈值 100ms。
            //    ⇒ 没有预热 ⇒ 本用例的绿**依赖「慢用例先跑」**（JUnit 默认按声明序）
            //    ⇒ 换个执行顺序就会红 ⇒ 那是**顺序依赖的假稳定**，不是守门。
            //    ⇒ 先调一次把这条路径预热，再清空 exporter，然后才做**被测**的那一次。
            stub.withDeadlineAfter(10, TimeUnit.SECONDS)
                    .getUser(GetUserReq.newBuilder().setUid(1L).build());

            EXPORTER.clear();

            // 🔴 前提断言：GlobalOpenTelemetry 必须**此刻**仍是本测试装的 SDK。
            //    （否则 exporter 收不到 span ⇒ 用例会以「找不到 span」这种看不懂的形态变红）
            assertSame(testSdk.getTracerProvider(), GlobalOpenTelemetry.get().getTracerProvider(),
                    "前提不成立：全局 TracerProvider 不是本测试装的 SDK ⇒ 本守门测不到 slow 标记。"
                            + "（GoStyleOtelAgent 有「已 set 就放弃」的分支 ⇒ 今天的成立是侥幸，不是保证。）"
                            + "⚠️ 只能比 TracerProvider，**不能**比 GlobalOpenTelemetry.get() 本身 ——"
                            + " OTel Java 的 set() 存的是 new ObfuscatedOpenTelemetry(sdk) 包装器（1.55.0）"
                            + " ⇒ get() 永远不等于 sdk 本身（自曝错账 #4）。");

            // 必须**成功**返回：Go 的 slow 判定只在 err == nil 时走（rpc.go:86-89）。
            GetUserResp resp = stub.withDeadlineAfter(10, TimeUnit.SECONDS)
                    .getUser(GetUserReq.newBuilder().setUid(1L).build());
            assertNotNull(resp, "本用例要的是一次**成功**的调用（Go: markRPCResult 只在 err==nil 时走 slow 判定）");

            Optional<SpanData> found = EXPORTER.spans.stream()
                    .filter(s -> METHOD.equals(s.getName()))
                    .findFirst();
            assertTrue(found.isPresent(),
                    "必须导出名为 " + METHOD + " 的 span。实际导出的 = "
                            + EXPORTER.spans.stream().map(SpanData::getName).toList());
            return found.get();
        } finally {
            channel.shutdownNow();
            server.shutdownNow();
        }
    }

    /**
     * 注册一个「处理函数耗时 {@code sleepMillis} 且成功返回」的服务。
     *
     * <p>⚠️ **必须**用 grpc-java 的标准派发入口 {@link ServerCalls#asyncUnaryCall}：
     * 裸 {@link ServerCallHandler} 不会被派发 {@code onHalfClose} ⇒ 方法体一次都不执行
     * ⇒ span 永不 end ⇒ 导出列表为空（这正是 §3.3「绿测试可能是错误答案的守卫」的形态）。
     */
    private static ServerServiceDefinition slowAdminService(long sleepMillis) {
        ServerCallHandler<GetUserReq, GetUserResp> handler =
                ServerCalls.asyncUnaryCall((req, observer) -> {
                    if (sleepMillis > 0) {
                        try {
                            Thread.sleep(sleepMillis);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    observer.onNext(GetUserResp.getDefaultInstance());
                    observer.onCompleted();
                });
        // ⚠️ 自建一个只含 GetUser 这一个方法的 ServiceDescriptor：
        //    ① 必须用生成代码里的**同一个** getGetUserMethod() 实例，否则报
        //       "Bound method ... not same instance as method in service descriptor"；
        //    ② 描述符里的每个方法都必须有绑定。
        io.grpc.ServiceDescriptor onlyGetUser = io.grpc.ServiceDescriptor
                .newBuilder(AdminServiceGrpc.getServiceDescriptor().getName())
                .addMethod(AdminServiceGrpc.getGetUserMethod())
                .build();
        return ServerServiceDefinition.builder(onlyGetUser)
                .addMethod(AdminServiceGrpc.getGetUserMethod(), handler)
                .build();
    }
}
