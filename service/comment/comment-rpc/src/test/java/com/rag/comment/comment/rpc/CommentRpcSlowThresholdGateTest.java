package com.rag.comment.comment.rpc;

import com.rag.comment.comment.proto.CommentServiceGrpc;
import com.rag.comment.comment.proto.GetCommentReq;
import com.rag.comment.comment.proto.GetCommentResp;
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
import com.rag.comment.comment.rpc.support.RealGrpcPort;
import com.rag.comment.comment.rpc.support.RealContainerHostPort;

import static com.rag.common.observability.ObservabilityConstants.ATTR_DURATION;
import static com.rag.common.observability.ObservabilityConstants.ATTR_SLOW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 <b>slow threshold 分支的独立守门</b>（global 收口域 X-5 残留的第二半 · comment 域）。
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
 * <h2>Go 侧的注册点（本域）</h2>
 * <pre>
 *   service/comment/rpc/comment.go:54
 *       s.AddUnaryInterceptors(observability.NewUnaryServerInterceptor(rpcTimeout, observability.SlowThreshold()))
 * </pre>
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
 *
 * <h2>🔴 为什么 OTel SDK 必须在 Spring 上下文创建之前装</h2>
 * <p>{@code RpcInterceptor#newUnaryServerInterceptor} 在<b>Bean 创建期</b>就取了 Tracer
 * （{@code RpcInterceptor.java:154}）⇒ 上下文起来之后再装 SDK 就来不及了
 * ⇒ 装晚了 span 会是 no-op ⇒ 「导出列表为空」⇒ 守门变成「断言一个恒空的集合」。
 */
@SpringBootTest(
        classes = CommentRpcApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                // 🔴 T-15 ③：**专属上下文缓存键**（global 域 · 第三十轮加）
                //    Spring 的 context cache key **包含** properties，但
                //    **@DynamicPropertySource 的值不进 key** ⇒ 若本类的 properties
                //    与同模块另一个 @SpringBootTest **恰好相同** ⇒ 两者**共享同一个
                //    Spring 上下文** ⇒ 本类 @BeforeAll 装的那套 OTel SDK 装不进那个
                //    **已存在**的上下文 ⇒ 上下文创建时拦截器取走的是**别的测试**的
                //    Tracer ⇒ 本类 exporter 收不到 span ⇒ 用例以「**找不到 span**」
                //    这种看不懂的形态变红（而不是「前提不成立」）。
                //    ⇒ 加一个**本类专属、别的类不可能撞上**的 key ⇒ 上下文必然独立。
                //    ⚠️ 这不是「保险」而是**修掉一个侥幸**：今天的隔离靠的是
                //    「各类的 properties 恰好不同」—— 谁改一下 properties 就可能撞上。
                "rag.test.gate.context-key=comment-slow-threshold",

                // 🔴 gRPC server 端口走**本域专用段**（RealGrpcPort），**不用 `port=0`**：
                //    port=0 会让 OS 从临时端口段（macOS 49152–65535）里挑，而那是
                //    **Docker 随机发布容器端口用的同一段** ⇒ 别的会话的容器先占住
                //    ⇒ EADDRINUSE ⇒ 整个测试类启动失败（表现为「偶尔红、换个时间又绿」的抖动）。
                //    ⇒ 本域专用段 = **22600–22699**（global 段表预分配给 comment 域），
                //    原先的裸硬编码 21500 **落在 favorite 域的探针段 21400–21599 内**
                //    ⇒ ⇒ 两边都以为对方不在自己段里 ⇒ ⇒ 2026-10-27 一并迁出。
                //    ⇒ 端口在 @DynamicPropertySource 里注册（**不进 context cache key**）。
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "grpc.client.message-rpc.address=static://127.0.0.1:1",
                "grpc.client.article-rpc.address=static://127.0.0.1:1",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN",
                "spring.kafka.bootstrap-servers=127.0.0.1:1",
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
class CommentRpcSlowThresholdGateTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("comment_rpc_slow_threshold_it")
                    .withUsername("rag")
                    .withPassword("rag")
                    .withCreateContainerCmdModifier(RealContainerHostPort.bindPg(RealContainerHostPort.COMMENT_RPC_SLOW_THRESHOLD_GATE));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        // 🔴 gRPC server 端口走**本域专用段**（22600–22699），不用 `port=0`
        //    （port=0 落 OS 临时段 ⇒ 与 Docker 发布口同段 ⇒ 环境性抖动）。
        //    ⚠️ **@DynamicPropertySource 的值不进 Spring 的 context cache key**
        //    ⇒ ⇒ 各测试类的**专属 context key**（rag.test.gate.context-key=*）仍是必需的
        //    ⇒ ⇒ 两处都要留：一个是「端口不撞段」，一个是「上下文不共享」。
        r.add("grpc.server.port", RealGrpcPort::pick);
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
     * ⇒ 不是 {@code "/comment.CommentService/GetComment"}。
     */
    private static final String METHOD = "comment.CommentService/GetComment";

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
    /**
     * 🔴🔴 **预热调用（必须有）**：JVM 里的**第一次** gRPC 调用要把
     *    protobuf 编解码 / netty 缓冲 / 拦截器链的类全部加载一遍
     *    ⇒ 这些成本落在 span 的 duration 里
     *    ⇒ 「快调用」那条会被误判成慢调用（**首调就红**）。
     *    实测：follow-rpc 首调 duration = 138.333ms > 阈值 100ms。
     *    ⇒ 没有预热 ⇒ 本用例的绿**依赖「慢用例先跑」**（JUnit 默认按声明序）
     *    ⇒ 换个执行顺序就会红 ⇒ 那是**顺序依赖的假稳定**，不是守门。
     *    ⇒ 先调一次把这条路径预热，再清空 exporter，然后才做**被测**的那一次。
     */
    private void warmUp() throws Exception {
        GrpcServerConfigurer configurer = ctx.getBean("rpcInterceptorConfigurer", GrpcServerConfigurer.class);
        assertNotNull(configurer, "rpcInterceptorConfigurer 必须产出 GrpcServerConfigurer Bean");
        ServerBuilder<?> builder = ServerBuilder.forPort(0);
        configurer.accept(builder);
        Server server = builder.addService(slowCommentService(0L)).build().start();
        ManagedChannel ch = io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
                .forTarget("dns:///127.0.0.1:" + server.getPort()).usePlaintext().build();
        try {
            var stub = CommentServiceGrpc.newBlockingStub(ch);
            stub.withDeadlineAfter(10, TimeUnit.SECONDS).getComment(GetCommentReq.newBuilder().setTargetType("article").setTargetId("t-1").build());
        } finally {
            ch.shutdownNow();
            server.shutdownNow();
        }
    }

    private SpanData callAndGetSpan(long sleepMillis) throws Exception {
        GrpcServerConfigurer configurer = ctx.getBean("rpcInterceptorConfigurer", GrpcServerConfigurer.class);
        assertNotNull(configurer, "rpcInterceptorConfigurer 必须产出 GrpcServerConfigurer Bean");

        ServerBuilder<?> builder = ServerBuilder.forPort(0);
        configurer.accept(builder);                  // ← 整条链（recover→breaker→shedding→业务拦截器）
        Server server = builder.addService(slowCommentService(sleepMillis)).build().start();

        // ⚠️ **必须显式写 dns:/// scheme**：Nacos discovery 在全局 NameResolverRegistry 里
        //    注册了一个高优先级 provider，会把任何无 scheme 的 target 当作 serviceId 去 Nacos 拉实例。
        ManagedChannel channel = io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
                .forTarget("dns:///127.0.0.1:" + server.getPort()).usePlaintext().build();
        try {
            // 🔴🔴 预热（见 warmUp() 的说明）⇒ 否则「快调用」那条会因首调开销而首调就红。
            warmUp();

            EXPORTER.clear();
            var stub = CommentServiceGrpc.newBlockingStub(channel);

            // 🔴 前提断言：全局 TracerProvider 必须**此刻**仍是本测试装的 SDK。
            //    （否则 exporter 收不到 span ⇒ 用例会以「找不到 span」这种看不懂的形态变红）
            assertSame(testSdk.getTracerProvider(), GlobalOpenTelemetry.get().getTracerProvider(),
                    "前提不成立：全局 TracerProvider 不是本测试装的 SDK ⇒ 本守门测不到 slow 标记。"
                            + "（GoStyleOtelAgent 有「已 set 就放弃」的分支 ⇒ 今天的成立是侥幸，不是保证。）"
                            + "⚠️ 只能比 TracerProvider，**不能**比 GlobalOpenTelemetry.get() 本身 ——"
                            + " OTel Java 的 set() 存的是 new ObfuscatedOpenTelemetry(sdk) 包装器（1.55.0）"
                            + " ⇒ get() 永远不等于 sdk 本身（自曝错账 #4）。");

            // 必须**成功**返回：Go 的 slow 判定只在 err == nil 时走（rpc.go:86-89）。
            GetCommentResp resp = stub.withDeadlineAfter(10, TimeUnit.SECONDS)
                    .getComment(GetCommentReq.newBuilder()
                            .setTargetType("article")
                            .setTargetId("t-1")
                            .build());
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
    private static ServerServiceDefinition slowCommentService(long sleepMillis) {
        ServerCallHandler<GetCommentReq, GetCommentResp> handler =
                ServerCalls.asyncUnaryCall((req, observer) -> {
                    if (sleepMillis > 0) {
                        try {
                            Thread.sleep(sleepMillis);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    observer.onNext(GetCommentResp.getDefaultInstance());
                    observer.onCompleted();
                });
        // ⚠️ 自建一个只含 GetComment 这一个方法的 ServiceDescriptor：
        //    ① 必须用生成代码里的**同一个** getGetCommentMethod() 实例，否则报
        //       "Bound method ... not same instance as method in service descriptor"；
        //    ② 描述符里的每个方法都必须有绑定。
        io.grpc.ServiceDescriptor onlyGetComment = io.grpc.ServiceDescriptor
                .newBuilder(CommentServiceGrpc.getServiceDescriptor().getName())
                .addMethod(CommentServiceGrpc.getGetCommentMethod())
                .build();
        return ServerServiceDefinition.builder(onlyGetComment)
                .addMethod(CommentServiceGrpc.getGetCommentMethod(), handler)
                .build();
    }
}
