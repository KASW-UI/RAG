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
import io.grpc.StatusRuntimeException;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.rag.message.message.rpc.support.RealContainerPort;
import com.rag.message.message.rpc.support.GrpcTestLifecycle;
import com.rag.message.message.rpc.support.GrpcTestPort;

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
                "rag.test.gate.context-key=message-slow-threshold",

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
/**
     * 本类的真容器**固定宿主口**（message 域段 22760–22789 内 · 全局段表 §4.10.19.3 / §4.10.39.2 分配）。
     *
     * <p>🔴 为什么不用 Testcontainers 的默认随机口：随机宿主口落在 **OS 临时段 49152–65535**
     * （= Docker published 段）⇒ ⇒ 与 {@code tools/difftest/ddl-parity.sh} 的固定口 {@code 55560–55569}
     * 同属一个池子 ⇒ ⇒ 全局段表把该段标为**禁占**。
     *
     * <p>🔴 为什么端口写死在本类里、而不是轮询分配：Testcontainers 的 surefire **每个 fork 一个独立 JVM**
     * ⇒ ⇒ 静态计数器在每个 fork 里都从头开始 ⇒ ⇒ 两个并发 fork 会拿到同一个端口 ⇒ ⇒ 撞车。
     */
    private static final int FIXED_HOST_PORT = 22766;
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
    /**
     * 🔴🔴 **预热调用（必须有）**：JVM 里的**第一次** gRPC 调用要把
     *    protobuf 编解码 / netty 缓冲 / 拦截器链的类全部加载一遍
     *    ⇒ 这些成本落在 span 的 duration 里
     *    ⇒ 「快调用」那条会被误判成慢调用（**首调就红**）。
     *    实测：follow-rpc 首调 duration = 138.333ms > 阈值 100ms。
     *    ⇒ 没有预热 ⇒ 本用例的绿**依赖「慢用例先跑」**（JUnit 默认按声明序）
     *    ⇒ 换个执行顺序就会红 ⇒ 那是**顺序依赖的假稳定**，不是守门。
     *
     * <p>⚠️ 预热必须用**独立的 server + 独立的 probe**：
     *    本类的 {@code probe.entered} 是一次性的 CountDownLatch ⇒ 被预热消耗掉的话，
     *    「handler 真的被调用过」那条断言就**永远为真** ⇒ 守门变空转。
     *
     * <h2>🔴🔴 为什么这里的拆除要「优雅关闭」而不是 {@code shutdownNow()}（V-032 加固）</h2>
     * <p>{@code DRIFT-MSG-V-032} 登记的现象 = 本用例在**全量并发回归**里偶发
     * {@code StatusRuntime UNAVAILABLE: Network closed for unknown reason}
     * ⇒ 2026-10-30 复核确认根因在<b>拆除时序</b>：
     * <ul>
     *   <li>旧写法 = 「发一次调用 ⇒ <b>立刻</b> {@code ch.shutdownNow()} + {@code server.shutdownNow()}」
     *       ⇒ 客户端 channel 在服务端连接拆除的**同时**还有在途帧 ⇒
     *       ⇒ netty 侧读到「连接已关」⇒ 客户端抛 {@code UNAVAILABLE: Network closed}。</li>
     *   <li>注意：这里的 {@code UNAVAILABLE} <b>不是</b>被测行为坏了（不是 slow 标记、不是阈值比较），
     *       而是**被测环境自己拆除得太急** ⇒ ⇒ 表现为「这条用例偶发红」⇒
     *       ⇒ 威胁的是<b>阶段一验收证据本身</b>（全量回归当场 BUILD FAILURE）。</li>
     * </ul>
     * ⇒ 修法 = <b>优雅关闭</b>：{@code shutdown()}（等在途调用自然收尾）⇒
     * {@code awaitTermination(...)}（等真的终止）⇒ 超时后才降级成 {@code shutdownNow()}。
     * <p>⚠️ 与同仓已有写法一致（favorite-api {@code FavoriteApiSpanWiringTest:288-298}、
     * hot-rpc {@code HotRpcFailurePathMatrixTest:117-121} 都是 {@code shutdownNow()+awaitTermination}；
     * 本类改用 <b>{@code shutdown()}</b> 优先，是<b>更严</b>的一侧 ⇒ 只影响测试自身时序、不改被测语义）。
     */
    private void warmUp() throws Exception {
        GrpcServerConfigurer configurer = ctx.getBean("rpcInterceptorConfigurer", GrpcServerConfigurer.class);
        assertNotNull(configurer, "rpcInterceptorConfigurer 必须产出 GrpcServerConfigurer Bean");
        // 🔴 V-032 加固（第二半）：建连本身也可能瞬时失败 ⇒ **重试**。
        //    「探测式端口」已治「撞口」（BindException），但治不了「端口在探测与 bind 之间被抢」
        //    （TOCTOU 窗口）⇒ 那会表现为 server 起来后客户端连不上 / 连接被立刻关。
        //    ⇒ 预热对「本类要验的 slow 标记」是**辅助动作** ⇒ 失败可重试（重试上限 3 次）。
        //    ⚠️ 重试**只包住预热**，不包住被测断言 ⇒ 重试不会掩盖被测行为缺陷。
        StatusRuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= WARMUP_MAX_ATTEMPTS; attempt++) {
            try {
                warmUpOnce(configurer);
                return;
            } catch (StatusRuntimeException e) {
                lastFailure = e;
                // 只重试「传输层」的失败（UNAVAILABLE / 未知原因）⇒
                // 其它状态码（业务错误 / 参数错误）重试无意义 ⇒ 直接抛。
                if (e.getStatus().getCode() != io.grpc.Status.Code.UNAVAILABLE
                        && e.getStatus().getCode() != io.grpc.Status.Code.UNKNOWN) {
                    throw e;
                }
                Thread.sleep(WARMUP_RETRY_BACKOFF_MILLIS * attempt);
            }
        }
        throw new IllegalStateException("预热连续 " + WARMUP_MAX_ATTEMPTS
                + " 次都因传输层失败（UNAVAILABLE/UNKNOWN）⇒ 本用例无法建立可测环境。"
                + "最后一次异常 = " + lastFailure, lastFailure);
    }

    /** 预热重试上限（V-032 加固）。 */
    private static final int WARMUP_MAX_ATTEMPTS = 3;

    /** 预热重试的退避基数（毫秒）—— 第 n 次等待 n × 本值。 */
    private static final long WARMUP_RETRY_BACKOFF_MILLIS = 200L;

    /**
     * 真正跑一次预热（独立 server + 独立 probe + 优雅拆除）。
     *
     * <p>⚠️ 预热必须用**独立的 server + 独立的 probe**：本类的 {@code probe.entered}
     * 是一次性的 CountDownLatch ⇒ 被预热消耗掉的话，「handler 真的被调用过」那条断言
     * 就**永远为真** ⇒ 守门变空转。
     */
    private void warmUpOnce(GrpcServerConfigurer configurer) throws Exception {
        // 🔴 不能用 forPort(0)：那是【OS 盲选口】⇒ 可能正好选到【Docker 发布口】
        //    （本机实测：Sea-redis 发布 0.0.0.0:49380->6379，而 macOS 临时段是 49152–65535 ⇒ 同池）
        //    ⇒ ⇒ gRPC server 在 IPv6 空间绑上了、而客户端走 IPv4 连到 Docker 代理 ⇒
        //    ⇒ 客户端收到 Redis 的 "-ERR " ⇒ ⇒ http2 首帧不是 SETTINGS ⇒
        //    ⇒ 报成「http2 exception」，看起来像 gRPC 坏了，实为【被测环境撞口】。
        // ⇒ 改成探测式：GrpcTestPort 取候选后会【显式再绑一次】确认 ⇒ 绑不上（被 Docker 占着）就换下一个。
        ServerBuilder<?> builder = ServerBuilder.forPort(GrpcTestPort.freePort());
        configurer.accept(builder);
        SlowHandlerProbe warm = new SlowHandlerProbe();
        warm.release.countDown();                 // 闸门直接开 ⇒ 立即返回
        Server server = builder.addService(slowMessageService(warm, 0L)).build().start();
        ManagedChannel ch = io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
                .forTarget("dns:///127.0.0.1:" + server.getPort()).usePlaintext().build();
        try {
            var stub = MessageServiceGrpc.newBlockingStub(ch);
            stub.withDeadlineAfter(20, TimeUnit.SECONDS).markNotificationRead(MarkNotificationReq.newBuilder().setUserId(1L).setNotificationId(2L).build());
        } finally {
            // 🔴 V-032 加固：优雅关闭 ⇒ 先 shutdown()（不在途帧自然收尾）⇒ 再 awaitTermination
            //    ⇒ 只有超时才降级 shutdownNow()。
            //    ⚠️ 旧写法（shutdownNow() 立刻拔网线）是本条偶发 UNAVAILABLE 的直接来源。
            shutdownQuietly(ch, server);
        }
    }

    /**
     * 🔴 **优雅关闭**（V-032 加固共用）—— 实现已抽到
     * {@link com.rag.message.message.rpc.support.GrpcTestLifecycle#shutdownGracefully}。
     *
     * <p>⚠️ <b>为什么不是本类的 private 方法</b>：§3.3 —— 守门必须测「生产用的那个函数」⇒
     * 若逻辑留在本类 private 方法里，守门只能测一份<b>副本</b> ⇒ 副本与正身可分叉
     * ⇒ 「你测的是 A、跑的是 B」⇒ 故抽到 {@code support} 包 ⇒ 守门与本用例<b>共用同一个函数</b>。
     */
    private static void shutdownQuietly(ManagedChannel channel, Server server) {
        GrpcTestLifecycle.shutdownGracefully(channel, server);
    }

    private SpanData callAndGetSpan(long holdMillis) throws Exception {
        // ⚠️ 必须按 Bean 名取：devh starter 自己还注册了 compressionServerConfigurer /
        //    decompressionServerConfigurer 两个同类型 Bean，ctx.getBean(GrpcServerConfigurer.class)
        //    会抛 NoUniqueBeanDefinitionException。我们要验的只有本模块自己那个。
        GrpcServerConfigurer configurer = ctx.getBean("rpcInterceptorConfigurer", GrpcServerConfigurer.class);
        assertNotNull(configurer, "rpcInterceptorConfigurer 必须产出 GrpcServerConfigurer Bean");

        // 🔴 不能用 forPort(0)：那是【OS 盲选口】⇒ 可能正好选到【Docker 发布口】
        //    （本机实测：Sea-redis 发布 0.0.0.0:49380->6379，而 macOS 临时段是 49152–65535 ⇒ 同池）
        //    ⇒ ⇒ gRPC server 在 IPv6 空间绑上了、而客户端走 IPv4 连到 Docker 代理 ⇒
        //    ⇒ 客户端收到 Redis 的 "-ERR " ⇒ ⇒ http2 首帧不是 SETTINGS ⇒
        //    ⇒ 报成「http2 exception」，看起来像 gRPC 坏了，实为【被测环境撞口】。
        // ⇒ 改成探测式：GrpcTestPort 取候选后会【显式再绑一次】确认 ⇒ 绑不上（被 Docker 占着）就换下一个。
        ServerBuilder<?> builder = ServerBuilder.forPort(GrpcTestPort.freePort());
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
            // 🔴🔴 预热（见 warmUp() 的说明）⇒ 否则「快调用」那条会因首调开销而首调就红。
            warmUp();

            EXPORTER.clear();
            var stub = MessageServiceGrpc.newBlockingStub(channel);

            // 🔴 前提断言：GlobalOpenTelemetry 必须**此刻**仍是本测试装的 SDK。
            //    （否则 exporter 收不到 span ⇒ 用例会以「找不到 span」这种看不懂的形态变红）
            assertSame(testSdk.getTracerProvider(), GlobalOpenTelemetry.get().getTracerProvider(),
                    "前提不成立：全局 TracerProvider 不是本测试装的 SDK ⇒ 本守门测不到 slow 标记。"
                            + "（GoStyleOtelAgent 有「已 set 就放弃」的分支 ⇒ 今天的成立是侥幸，不是保证。）"
                            + "⚠️ 只能比 TracerProvider，**不能**比 GlobalOpenTelemetry.get() 本身 ——"
                            + " OTel Java 的 set() 存的是 new ObfuscatedOpenTelemetry(sdk) 包装器（1.55.0）"
                            + " ⇒ get() 永远不等于 sdk 本身（自曝错账 #4）。");
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
            // 🔴 V-032 加固：同 warmUp() ⇒ 优雅关闭（先 channel 后 server）⇒ 不再「拔网线」。
            //    ⚠️ 旧写法 `channel.shutdownNow(); server.shutdownNow();` 是本条偶发
            //    `UNAVAILABLE: Network closed for unknown reason` 的直接来源。
            shutdownQuietly(channel, server);
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
