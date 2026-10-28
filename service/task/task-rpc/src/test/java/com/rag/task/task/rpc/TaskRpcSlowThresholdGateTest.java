package com.rag.task.task.rpc;

import com.google.common.util.concurrent.ListenableFuture;
import com.rag.task.task.proto.GetTaskReq;
import com.rag.task.task.proto.GetTaskResp;
import com.rag.task.task.proto.TaskServiceGrpc;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.rag.common.observability.ObservabilityConstants.ATTR_DURATION;
import static com.rag.common.observability.ObservabilityConstants.ATTR_SLOW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.rag.task.task.rpc.support.RealContainerPort;
import com.rag.task.task.rpc.support.GrpcTestPort;

/**
 * 🔴 <b>slow threshold 分支的独立守门</b>（global L9 · task-rpc / follow-rpc 的 X-5 之外的另一半）。
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
 * <h2>Go 出处（逐字对照，行为层，每条带行号）</h2>
 * <pre>
 *   service/task/rpc/task.go:44
 *       s.AddUnaryInterceptors(observability.NewUnaryServerInterceptor(rpcTimeout, observability.SlowThreshold()))
 *   service/common/observability/rpc.go:76-99  markRPCResult
 *       if err == nil { MarkDurationAndSlow(ctx, method, err, duration, slowThreshold); return }   ← :86-89
 *   service/common/observability/observability.go:131-144  MarkDurationAndSlow
 *       base := []attribute.KeyValue{ attribute.Float64(AttrDuration, durationMilliseconds(duration)) }  ← :137-139
 *       if slowThreshold &gt; 0 &amp;&amp; duration &gt; slowThreshold {                                  ← :140
 *           base = append(base, attribute.Bool(AttrSlow, true))                                        ← :141
 *       }
 * </pre>
 * ⇒ 语义 = <b>「只有成功（err == nil）的调用才走 slow 判定；且只有耗时<b>严格超过</b>阈值才打 slow 标记」</b>。
 * <p>⚠️ Go 的阈值来源：{@code observability.SlowThreshold()}（{@code observability.go:41-54}）
 * ⇒ env var {@code SEA_OBSERVABILITY_SLOW_THRESHOLD[_MS]} ⇒ 兜底 {@code DefaultSlowThreshold = 500ms}
 * （{@code observability.go:21}）。RAG 侧多一层 Spring 配置 {@code observability.slow-threshold}
 * （{@code ObservabilityProperties#getSlowThreshold()} → {@code RpcServerInterceptorFactory:77-81}）。
 *
 * <h2>为什么必须走「真 server + 真链 + 真 span」</h2>
 * <p>AGENTS.md §3.3：纯 Mockito 单测不算「机制生效」的证据。
 * ⇒ 本类起一个<b>真 gRPC server</b>，用容器里那个 {@link GrpcServerConfigurer}
 * （= recover → breaker → shedding → 业务观测拦截器，<b>整条链</b>）装配它，
 * 注册一个「处理函数在飞一段时间且成功返回」的服务，装一个<b>真 OTel SDK</b>
 * + 收集型 exporter ⇒ 断言<b>导出的 span 上真的有 {@code sea.slow}</b>。
 *
 * <h2>两条断言缺一不可</h2>
 * <ol>
 *   <li><b>慢的那条</b>：在飞 400ms &gt; 阈值 100ms ⇒ {@code sea.slow} 必须为 {@code true}；</li>
 *   <li><b>快的那条</b>：在飞 ≪ 阈值 ⇒ {@code sea.slow} 必须<b>不存在</b>
 *       ⇒ 证明这条路径是<b>真比较</b>出来的，而不是「恒为 true」。</li>
 * </ol>
 *
 * <h2>🔴 为什么慢服务用 latch 而不是 {@code Thread.sleep}</h2>
 * <p>grpc-java 的 <b>标准派发入口</b>是 {@link ServerCalls#asyncUnaryCall}（见下）；
 * 用裸 {@link ServerCallHandler} 时 grpc-java <b>不会</b>把 {@code onHalfClose} 派发下去
 * ⇒ 方法体一次都不执行 ⇒ 「慢」这件事<b>从未发生</b> ⇒ 守门空转。
 * <p>本类的「慢业务」用 {@link CountDownLatch} 表达（{@code probe.slowGate}）：
 * handler 阻塞在 latch 上，<b>测试侧决定何时开闸</b>
 * ⇒ ① 没有 {@code Thread.sleep}；② {@code finally} 里能<b>立刻</b>开闸，不拖长测试；
 * ③ 能<b>精确</b>观测「开闸之前 handler 仍在跑」（下面第三条断言）。
 *
 * <h2>🔴 三条「不能退化」的判别式断言</h2>
 * <ol>
 *   <li>用 {@link ServerCalls#asyncUnaryCall} 注册（标准派发入口）；</li>
 *   <li>断言「<b>handler 确实被调用过</b>」（{@code probe.entered.await(...)}）—— 少了这条，
 *       「handler 压根没执行」时其余断言<b>同样成立</b>；</li>
 *   <li>断言「<b>开闸之前它仍在跑</b>」（{@code assertFalse(probe.completed.get())}）
 *       —— 这是 slow 轴版本的「取消发生时它仍在跑」：只有 handler <b>真的在飞</b>，
 *       span 的 duration 才会反映业务耗时。</li>
 * </ol>
 */
@SpringBootTest(
        classes = TaskRpcApplication.class,
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
                "rag.test.gate.context-key=task-slow-threshold",

                "grpc.server.port=0",
                // 绕过 Nacos：注册在 ApplicationReadyEvent 且已 try/catch，但 devh 的发现配置仍会连
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN",
                // 绕过 Kafka：指向不可达地址（不是 exclude 自动配置 —— 那会让 KafkaTemplate 消失、上下文起不来）
                "spring.kafka.bootstrap-servers=127.0.0.1:1",
                // 绕过 Redis：Lettuce 连接懒建，不真正连
                "spring.data.redis.host=127.0.0.1",
                "spring.data.redis.port=1",
                // ⚠️ **被测参数**：慢调用阈值压到 100ms（生产侧走 DefaultSlowThreshold=500ms，
                //    见 Go observability.go:21）。下面的慢业务在飞 400ms ⇒ 必然超过它。
                "observability.slow-threshold=100ms",
                // ⚠️ 关掉 CPU 自适应降载，让判据**只**取决于「慢调用标记」这一条。
                //    否则链上的 shedding 层会先返回 RESOURCE_EXHAUSTED，把判据盖掉 ⇒ 变成 flaky。
                "rag.protection.cpu-shedding.enabled=false"
        })
@Testcontainers
class TaskRpcSlowThresholdGateTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("task_rpc_slow_threshold_it")
                    .withUsername("rag")
                    .withPassword("rag");
/**
     * 本类的真容器**固定宿主口**（task 域段 22820–22849 内 · 全局段表 §4.10.19.3 / §4.10.39.2 分配）。
     *
     * <p>🔴 为什么不用 Testcontainers 的默认随机口：随机宿主口落在 **OS 临时段 49152–65535**
     * （= Docker published 段）⇒ ⇒ 与 {@code tools/difftest/ddl-parity.sh} 的固定口 {@code 55560–55569}
     * 同属一个池子 ⇒ ⇒ 全局段表把该段标为**禁占**。
     *
     * <p>🔴 为什么端口写死在本类里、而不是轮询分配：Testcontainers 的 surefire **每个 fork 一个独立 JVM**
     * ⇒ ⇒ 静态计数器在每个 fork 里都从头开始 ⇒ ⇒ 两个并发 fork 会拿到同一个端口 ⇒ ⇒ 撞车。
     */
    private static final int FIXED_HOST_PORT = 22821;
    static {
        RealContainerPort.withFixedHostPort(POSTGRES, FIXED_HOST_PORT,
                RealContainerPort.PG_CONTAINER_PORT);
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("grpc.server.port", GrpcTestPort::freePort);
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
     * {@code "service/method"}（<b>无前导斜杠</b>）⇒ 不是 {@code "/task.TaskService/GetTask"}。
     */
    private static final String METHOD = "task.TaskService/GetTask";

    /** 慢业务在飞时长（ms）—— 必须 &gt; {@link #SLOW_THRESHOLD_MILLIS}。 */
    private static final long SLOW_MILLIS = 400L;

    /** 被测阈值（ms）—— 与 {@code observability.slow-threshold=100ms} 一致。 */
    private static final long SLOW_THRESHOLD_MILLIS = 100L;

    @Test
    @DisplayName("慢调用（在飞 400ms > 阈值 100ms）必须被打上 sea.slow=true"
            + "（Go: observability.go:140-142 duration > slowThreshold ⇒ AttrSlow）")
    void slowCallMustBeMarkedSlow() throws Exception {
        SpanData span = callAndGetSpan(true);

        assertEquals(Boolean.TRUE, span.getAttributes().get(AttributeKey.booleanKey(ATTR_SLOW)),
                "在飞 400ms > 阈值 100ms ⇒ 必须打 sea.slow=true。"
                        + "实际 span 属性 = " + span.getAttributes());
        assertNotNull(span.getAttributes().get(AttributeKey.doubleKey(ATTR_DURATION)),
                "Go MarkDurationAndSlow 无条件打 AttrDuration（observability.go:138）⇒ RAG 也必须有 sea.duration_ms");
    }

    @Test
    @DisplayName("快调用（在飞 ≪ 阈值）必须**不带** sea.slow ⇒ 证明这条路径是真比较出来的")
    void fastCallMustNotBeMarkedSlow() throws Exception {
        SpanData span = callAndGetSpan(false);

        assertNull(span.getAttributes().get(AttributeKey.booleanKey(ATTR_SLOW)),
                "在飞时间远小于阈值 100ms ⇒ 不该打 sea.slow。若为 true，说明 slow 标记是「恒真」的，"
                        + "或阈值没被真正比较（Go: observability.go:140-142 要求 duration > slowThreshold 才打）。"
                        + "实际 span 属性 = " + span.getAttributes());
        // 诊断用（不改变判定语义）：把实测在飞时长摆出来，便于区分「阈值没生效」与「首调类加载把 duration 撑大」
        Double duration = span.getAttributes().get(AttributeKey.doubleKey(ATTR_DURATION));
        assertNotNull(duration, "Go MarkDurationAndSlow 无条件打 AttrDuration（observability.go:138）");
        assertTrue(duration < (double) SLOW_THRESHOLD_MILLIS,
                "快调用的在飞时长必须 ≪ 阈值 100ms，实测 = " + duration
                        + "ms。若明显偏大 ⇒ 本次调用吃到了「首次类加载 / JIT」的冷启动成本"
                        + "（实测 follow-rpc 首调 138.333ms）⇒ 需要预热调用，不能让冷启动混进判据。");
    }

    /**
     * 起一个真 server、调一次、返回该方法对应的 span。
     *
     * @param slow true = 模拟慢业务（在飞 {@link #SLOW_MILLIS}ms）；false = 立刻返回
     */
    private SpanData callAndGetSpan(boolean slow) throws Exception {
        // ⚠️ 必须按 Bean 名取：devh starter 自己还注册了 compressionServerConfigurer /
        //    decompressionServerConfigurer 两个同类型 Bean，按类型取会抛 NoUniqueBeanDefinitionException。
        GrpcServerConfigurer configurer = ctx.getBean("rpcInterceptorConfigurer", GrpcServerConfigurer.class);
        assertNotNull(configurer, "rpcInterceptorConfigurer 必须产出 GrpcServerConfigurer Bean");

        ServerBuilder<?> builder = ServerBuilder.forPort(0);
        configurer.accept(builder);                  // ← 整条链（recover→breaker→shedding→业务拦截器）
        ProbeHolder holder = new ProbeHolder();
        SlowHandlerProbe probe = new SlowHandlerProbe();
        holder.current.set(probe);
        Server server = builder.addService(slowTaskService(holder)).build().start();

        // ⚠️ **必须显式写 dns:/// scheme**：Nacos discovery 在全局 NameResolverRegistry 里
        //    注册了一个高优先级 provider，会把任何无 scheme 的 target 当作 serviceId 去 Nacos 拉实例。
        ManagedChannel channel = io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
                .forTarget("dns:///127.0.0.1:" + server.getPort()).usePlaintext().build();
        try {
            // ⚠️ 用**异步** stub（future 版）：只有异步才能在「调用还在飞」的当口观察 handler 的状态。
            var stub = TaskServiceGrpc.newFutureStub(channel);

            // 🔴🔴 **预热调用（必须有）**：JVM 里的**第一次** gRPC 调用要把
            //    protobuf 编解码 / netty 缓冲 / 拦截器链的类全部加载一遍 ⇒ 这些成本落在
            //    span 的 duration 里（实测：follow-rpc 首调 duration = 138.333ms）
            //    ⇒ 「快调用」那条会被误判成慢调用（首调就红）。
            //    ⇒ 先调一次把这条路径预热，再清空 exporter，然后才做**被测**的那一次。
            {
                SlowHandlerProbe warm = new SlowHandlerProbe();
                warm.slowGate.countDown();          // 闸门直接开 ⇒ 立即返回
                holder.current.set(warm);
                stub.getTask(GetTaskReq.newBuilder().setUserId(1L).build()).get(10, TimeUnit.SECONDS);
            }
            EXPORTER.clear();

            holder.current.set(probe);
            ListenableFuture<GetTaskResp> call =
                    stub.getTask(GetTaskReq.newBuilder().setUserId(1L).build());

            // 🔴 判别式断言 ①：handler 必须**真的被调用过**。
            //    若为 false，说明 grpc-java 根本没把 onHalfClose 派发下去（裸 ServerCallHandler 就是这种情形）
            //    ⇒ 本测试验的不是「慢调用标记」，而是「一次空调用」。
            assertTrue(probe.entered.await(2, TimeUnit.SECONDS),
                    "handler 必须**真的被调用过**。若为 false，说明 grpc-java 根本没把 onHalfClose "
                            + "派发下去（裸 ServerCallHandler 就是这种情形）⇒ 本测试验的是空调用，不是慢调用。");

            // 🔴 判别式断言 ②：开闸之前 handler 必须**仍在跑**。
            //    handler 阻塞在 slowGate 上（测试还没开闸）⇒ 它不可能已经返回。
            //    若为 true，说明 handler 早已跑完、duration 与业务耗时无关 ⇒ 守门是空的。
            assertFalse(probe.completed.get(),
                    "开闸之前 handler 必须**仍在跑**（未完成）。"
                            + "若为 true，说明处理早就结束了 ⇒ span 的 duration 与业务在飞时长无关。");

            if (slow) {
                // 模拟「慢业务」：等 slowGate 超时（= 业务还在跑），随后 countDown 开闸。
                // ⚠️ 用 latch.await(timeout) 而不是 Thread.sleep —— 本仓 PIT 记录过 sleep 方案下
                //    「处理还在跑」这条断言根本写不出来（等到断言时它早跑完了）。
                probe.slowGate.await(SLOW_MILLIS, TimeUnit.MILLISECONDS);
            }
            probe.slowGate.countDown();              // 开闸 ⇒ handler 继续并成功返回

            // 必须**成功**返回：Go 的 slow 判定只在 err == nil 时走（rpc.go:86-89）。
            GetTaskResp resp = call.get(10, TimeUnit.SECONDS);
            assertNotNull(resp, "本用例要的是一次**成功**的调用（Go: markRPCResult 只在 err==nil 时走 slow 判定）");

            Optional<SpanData> found = EXPORTER.spans.stream()
                    .filter(s -> METHOD.equals(s.getName()))
                    .findFirst();
            assertTrue(found.isPresent(),
                    "必须导出名为 " + METHOD + " 的 span。实际导出的 = "
                            + EXPORTER.spans.stream().map(SpanData::getName).toList());
            return found.get();
        } finally {
            probe.slowGate.countDown();               // 立刻放行 handler，不让它挂在 30s 上
            channel.shutdownNow();
            server.shutdownNow();
        }
    }

    /**
     * 注册一个「处理函数在飞一段时间且成功返回」的服务。
     *
     * <p>⚠️ **必须**用 grpc-java 的标准派发入口 {@link ServerCalls#asyncUnaryCall}：
     * 裸 {@link ServerCallHandler} 不会被派发 {@code onHalfClose} ⇒ 方法体一次都不执行
     * ⇒ 「慢」这件事从未发生 ⇒ 守门空转。
     */
    private static ServerServiceDefinition slowTaskService(ProbeHolder holder) {
        ServerCallHandler<GetTaskReq, GetTaskResp> handler =
                ServerCalls.asyncUnaryCall((req, observer) -> {
                    SlowHandlerProbe probe = holder.current.get();
                    probe.entered.countDown();
                    try {
                        // 「业务处理很慢」：用 latch 而不是 sleep ⇒
                        // ① 测试可以在 finally 里**立刻**开闸，不必等满；
                        // ② 可以**精确**观测「开闸之前它还在跑」。
                        probe.slowGate.await(30, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    probe.completed.set(true);
                    try {
                        observer.onNext(GetTaskResp.getDefaultInstance());
                        observer.onCompleted();
                    } catch (RuntimeException ignored) {
                        // 调用已被关闭 ⇒ 这里再写就会抛。
                    }
                });
        // ⚠️ 自建一个只含 GetTask 这一个方法的 ServiceDescriptor：
        //    ① 必须用生成代码里的**同一个** getGetTaskMethod() 实例，否则报
        //       "Bound method ... not same instance as method in service descriptor"；
        //    ② 描述符里的每个方法都必须有绑定。
        io.grpc.ServiceDescriptor onlyGetTask = io.grpc.ServiceDescriptor
                .newBuilder(TaskServiceGrpc.getServiceDescriptor().getName())
                .addMethod(TaskServiceGrpc.getGetTaskMethod())
                .build();
        return ServerServiceDefinition.builder(onlyGetTask)
                .addMethod(TaskServiceGrpc.getGetTaskMethod(), handler)
                .build();
    }

    /**
     * 「处理函数确实被调用过」+「开闸之前它仍在跑」这两个证据。
     *
     * <p>⚠️ 这两个信号是 slow 轴的判别式断言：只看「span 上有没有 sea.slow」，
     * 而那件事在「handler 压根没执行」时<b>同样成立</b>（压根没有 span，但断言写的是
     * 「span 存在且属性如何」）⇒ 守门是空的。
     */
    private static final class SlowHandlerProbe {
        /** handler 真的被调用 ⇒ countDown。 */
        final CountDownLatch entered = new CountDownLatch(1);
        /** 「慢业务闸门」：handler 阻塞在它上面 ⇒ 测试侧 countDown 才让它继续。 */
        final CountDownLatch slowGate = new CountDownLatch(1);
        /** handler 跑完了（= 开闸之后 handler 已经返回）。 */
        final AtomicBoolean completed = new AtomicBoolean(false);
    }

    /**
     * 探针的可换 holder —— 让「预热调用」与「被测调用」各用一套 latch
     * （{@link CountDownLatch} 不可复用 ⇒ 只能整体换掉）。
     */
    private static final class ProbeHolder {
        final AtomicReference<SlowHandlerProbe> current = new AtomicReference<>();
    }
}
