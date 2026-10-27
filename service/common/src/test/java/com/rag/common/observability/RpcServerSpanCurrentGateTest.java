package com.rag.common.observability;

import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerServiceDefinition;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ServerCalls;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 守门：<b>业务 handler 执行期必须能拿到当前的 server span</b>（G2 反馈单 FB-04 · A9 轴）。
 *
 * <h2>为什么需要这条</h2>
 *
 * <p><b>Go 侧</b>（机制来源，逐行）：
 * <pre>
 *   go-zero v1.10.2 zrpc/internal/serverinterceptors/tracinginterceptor.go:18-24
 *       ctx, span := startSpan(ctx, info.FullMethod)
 *       resp, err := handler(ctx, req)      ← handler 拿到的 ctx **携带 span**
 *   同文件 :95-107（startSpan）
 *       return tr.Start(trace.ContextWithRemoteSpanContext(ctx, spanCtx), name, …)
 *       ⇒ span 是**从 ctx 派生**并**放回 ctx**的
 *   ⇒ 业务代码 trace.SpanFromContext(l.ctx) 取得到 ⇒ 属性真落
 * </pre>
 *
 * <p><b>RAG 侧（修前）</b>：Java 的 gRPC handler <b>不接收 ctx</b>
 * ⇒ 业务代码唯一的等价物是 {@code Span.current()}
 * ⇒ 而拦截器此前<b>只在</b> {@code close()} / {@code onCancel()} 里 {@code makeCurrent()}
 * ⇒ <b>handler 执行期 {@code Context.current()} 是根 context</b>
 * ⇒ {@code Span.current()} = <b>invalid（no-op）span</b> ⇒ 属性<b>静默丢弃</b>
 * ⇒ ⇒ 域侧若按 {@code Span.current().setAttribute(...)} 落码 ⇒ <b>跑绿但零效果</b>。
 *
 * <h2>为什么必须是「真 server + 真 OTel SDK」</h2>
 *
 * <p>要证明的是「<b>span 真的在 handler 执行期是 current 的</b>」且「<b>业务设的属性真的落到了导出的那个 span 上</b>」
 * ⇒ 只断言「拦截器建了 span」不够（那正是「注解存在 ≠ 机制生效」）；
 * ⇒ ⇒ 用<b>真 gRPC server</b>（netty，端口 0）＋ <b>真 OTel SDK</b>（{@code InMemorySpanExporter}）
 * ⇒ 断言落在<b>导出的 span 数据</b>上。
 *
 * <p>⚠️ 全局单例：{@code GlobalOpenTelemetry} 只能设一次 ⇒ 用 {@code resetForTest()}（与
 * {@code GoStyleOtelAgentGateTest} 同一套办法）⇒ 且本类**在结束时复位**，不污染同 JVM 的其它测试。
 */
@DisplayName("共享层 gRPC 拦截器：业务 handler 执行期能拿到当前 server span（A9）")
class RpcServerSpanCurrentGateTest {

    private static final String SERVICE_NAME = "probe.SpanCurrent";
    /** ⚠️ 不带前导斜杠（grpc-java 约定）。 */
    private static final String METHOD_NAME = "probe.SpanCurrent/Echo";
    private static final String BUSINESS_ATTR_KEY = "like.query.count";

    private static InMemorySpanExporter exporter;

    /** handler 执行期看到的 span（每次调用一条）。 */
    private static final List<Span> observedInHandler = new CopyOnWriteArrayList<>();

    /** handler 执行期的**实时**状态（span 结束后 isRecording() 恒为 false ⇒ 必须当场取）。 */
    private static final java.util.concurrent.atomic.AtomicBoolean observedValidInHandler =
            new java.util.concurrent.atomic.AtomicBoolean();
    private static final java.util.concurrent.atomic.AtomicBoolean observedRecordingInHandler =
            new java.util.concurrent.atomic.AtomicBoolean();

    @BeforeAll
    static void installRealOtelSdk() {
        GlobalOpenTelemetry.resetForTest();
        exporter = InMemorySpanExporter.create();
        SdkTracerProvider provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        GlobalOpenTelemetry.set(io.opentelemetry.sdk.OpenTelemetrySdk.builder()
                .setTracerProvider(provider)
                .build());
    }

    @AfterAll
    static void resetGlobalOtel() {
        GlobalOpenTelemetry.resetForTest();
    }

    @Test
    @DisplayName("① handler 里 Span.current() 必须是<b>有效且 recording</b> 的 span（否则属性全丢）")
    void handlerCanSeeTheServerSpan() throws Exception {
        runCalls(1);

        assertThat(observedInHandler).as("handler 必须真的被调用过").hasSize(1);
        Span current = observedInHandler.get(0);
        assertThat(observedValidInHandler.get())
                .as("handler 里的 Span.current() 必须是有效 span（修前是 invalid no-op ⇒ 属性静默丢弃）")
                .isTrue();
        assertThat(observedRecordingInHandler.get())
                .as("必须是 recording span（no-op span 的 setAttribute 是静默 no-op）")
                .isTrue();
    }

    @Test
    @DisplayName("② 业务在 handler 里 setAttribute 的属性，<b>必须真的出现在导出的 server span 上</b>")
    void businessAttributesLandOnTheExportedServerSpan() throws Exception {
        runCalls(1);

        var exported = exporter.getFinishedSpanItems();
        assertThat(exported).as("必须有 span 被导出（否则本用例什么都没验到）").isNotEmpty();
        var serverSpan = exported.stream()
                .filter(s -> METHOD_NAME.equals(s.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("没有导出名为 " + METHOD_NAME + " 的 server span ⇒ "
                        + "实际导出了：" + exported.stream().map(s -> s.getName()).toList()));

        assertThat(serverSpan.getAttributes().asMap())
                .as("业务在 handler 里设的属性必须落在**导出的 server span** 上"
                        + "（这正是 Go 侧 trace.SpanFromContext(ctx).SetAttributes(...) 的等价物）")
                .containsEntry(AttributeKey.longKey(BUSINESS_ATTR_KEY), 42L);
    }

    @Test
    @DisplayName("③ handler 里看到的必须<b>就是导出的那个 span</b>（不是子 span）")
    void handlerSeesTheSameSpanThatIsExported() throws Exception {
        runCalls(1);

        Span inHandler = observedInHandler.get(0);
        var serverSpan = exporter.getFinishedSpanItems().stream()
                .filter(s -> METHOD_NAME.equals(s.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("没有导出 server span"));
        assertThat(inHandler.getSpanContext().getSpanId())
                .as("handler 里的 current span 必须与导出的 server span 是**同一个**"
                        + "（若是子 span ⇒ trace 结构与 Go 漂移：Go 是给已有 server span 加属性，不新建）")
                .isEqualTo(serverSpan.getSpanId());
    }

    @Test
    @DisplayName("④ scope 不得泄漏到<b>下一次</b>请求（两次调用必须看到两个不同的 span）")
    void scopeDoesNotLeakAcrossRequests() throws Exception {
        runCalls(2);

        assertThat(observedInHandler).as("两次调用都必须进 handler").hasSize(2);
        assertThat(observedInHandler.get(0).getSpanContext().getSpanId())
                .as("第二次请求必须看到**自己的** server span（scope 泄漏会让它看到上一次的）")
                .isNotEqualTo(observedInHandler.get(1).getSpanContext().getSpanId());
    }

    /**
     * 起一个真 gRPC server（netty · 端口 0）＋ 真客户端调用 {@code count} 次。
     *
     * <p>⚠️ <b>每次调用前清空观测与导出</b>（{@code observedInHandler} / {@code exporter.reset()}）
     * ⇒ 每个用例只看到<b>自己这次</b>的调用 ⇒ ① 的 {@code hasSize(1)} 与 ② ③ 的「取第一个匹配」
     * <b>不依赖 JUnit 的方法执行顺序</b>（JUnit 5 默认按方法名 hash 排序 ⇒ 顺序不保证）。
     * ④ 需要的「两次调用」由 {@code count=2} 在<b>同一次</b>调用内完成 ⇒ 同样不依赖顺序。
     */
    private static void runCalls(int count) throws Exception {
        observedInHandler.clear();
        exporter.reset();
        for (int i = 0; i < count; i++) {
            runOneCall();
        }
    }

    /** 起一个真 gRPC server（netty · 端口 0）＋ 真客户端调用一次。 */
    private static void runOneCall() throws Exception {
        observedValidInHandler.set(false);
        observedRecordingInHandler.set(false);
        ServerBuilder<?> builder = ServerBuilder.forPort(0);
        builder.intercept(RpcInterceptor.newUnaryServerInterceptor(
                Duration.ofSeconds(5), Duration.ofSeconds(750)));
        ServerServiceDefinition svc = ServerServiceDefinition.builder(SERVICE_NAME)
                .addMethod(ECHO_METHOD, ServerCalls.asyncUnaryCall((req, obs) -> {
                    // 🔴 业务代码的写法：给**当前** span 加属性（Go 侧是 SpanFromContext(ctx).SetAttributes）
                    Span current = Span.current();
                    observedInHandler.add(current);
                    // ⚠️ 必须在 handler **内**取实时状态：span 在 close() 里 end 之后
                    //    isRecording() 恒为 false ⇒ 事后再看就分不出「没生效」与「已结束」
                    observedValidInHandler.set(current.getSpanContext().isValid());
                    observedRecordingInHandler.set(current.isRecording());
                    current.setAttribute(BUSINESS_ATTR_KEY, 42L);
                    obs.onNext(new byte[0]);
                    obs.onCompleted();
                }))
                .build();
        Server server = builder.addService(svc).build().start();
        ManagedChannel channel = NettyChannelBuilder
                .forTarget("dns:///127.0.0.1:" + server.getPort())
                .usePlaintext()
                .build();
        try {
            ClientCalls.blockingUnaryCall(channel, ECHO_METHOD, CallOptions.DEFAULT, new byte[0]);
        } catch (StatusRuntimeException e) {
            throw new AssertionError("RPC 应当成功完成，实际失败：" + e.getStatus(), e);
        } finally {
            channel.shutdownNow();
            server.shutdownNow();
        }
        // 等待 span 结束并被导出（close() 里 span.end() ⇒ SimpleSpanProcessor 同步导出）
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (exporter.getFinishedSpanItems().stream()
                .noneMatch(s -> METHOD_NAME.equals(s.getName()))
                && System.nanoTime() < deadline) {
            Thread.sleep(20L);
        }
    }

    private static final MethodDescriptor<byte[], byte[]> ECHO_METHOD =
            MethodDescriptor.<byte[], byte[]>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(METHOD_NAME)
                    .setRequestMarshaller(ByteMarshaller.INSTANCE)
                    .setResponseMarshaller(ByteMarshaller.INSTANCE)
                    .build();

    /** 只为让 MethodDescriptor 构造通过；本测试传空字节，不做业务序列化。 */
    private static final class ByteMarshaller implements MethodDescriptor.Marshaller<byte[]> {
        static final ByteMarshaller INSTANCE = new ByteMarshaller();

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
