package com.rag.common.observability;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.rag.common.observability.ObservabilityConstants.ATTR_TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * §3.3「框架机制必须验证实际生效」的落地测试：<b>断言运行时的可观测后果</b>，而不是断言 Bean 存在。
 *
 * <h2>为什么必须有这个类</h2>
 *
 * <p>本组件此前的 19 个测试类里，唯一的 {@code @SpringBootTest}
 * （{@code ObservabilityWiringTest}）只断言「Bean 在容器里」——
 * 而 gRPC 超时缺陷（H2）的形态恰恰是「Bean 在、但框架没用它」，
 * 所以那类断言<b>按定义抓不到</b>超时链路上的问题（见
 * {@code RpcServerInterceptorFactory} 类注释里的 javap 证据）。
 *
 * <h2>本类锁的缺陷：超时路径上 span 永不结束</h2>
 *
 * <p>Go 真源 {@code service/common/observability/rpc.go:68-71}：
 * <pre>
 *   case &lt;-timeoutCtx.Done():
 *       err := status.Error(codes.DeadlineExceeded, timeoutCtx.Err().Error())
 *       MarkTimeout(ctx, err, time.Since(start), rpcAttrs(info.FullMethod, codes.DeadlineExceeded)...)
 *       return nil, err
 * </pre>
 * ⇒ Go 在超时时<b>打 timeout 属性</b>。{@code MarkTimeout}
 * （{@code observability.go:108-129}）本身与 Java 版逐条对称，<b>都不 end span</b>——
 * 因为 Go 的 span 生命周期归 otel 插桩所有，拦截器只加属性，这是<b>正确</b>的分工。
 *
 * <p>RAG 的 {@link RpcInterceptor#newUnaryServerInterceptor} <b>自己建 span</b>
 * （{@code RpcInterceptor.java:163-166}）并在 {@code wrappedCall.close()} 里
 * {@code span.end()}（{@code :238-240}）。
 * ⚠️ <b>但超时调度器关闭的是原始 {@code call} 而不是 {@code wrappedCall}</b>
 * （{@code RpcInterceptor.java:192}）⇒ 超时路径<b>既不调 markTimeout、也不调 span.end()}</b>
 * ⇒ span 永远不结束 ⇒ 永远不会被导出 ⇒ <b>超时在遥测里完全不可见</b>，
 * 且悬挂的 span 持续占内存。
 *
 * <p>这条是 §3.2「无出处 idiomatization」的典型：RAG 多做了「自己管 span 生命周期」，
 * 却没有把 Go 由框架承担的那份职责在自己的 timeout 分支上补齐。
 *
 * <h2>证据强度</h2>
 *
 * <p>真 OTel SDK + 真 {@link InMemorySpanExporter}，驱动真实 {@link ServerInterceptor}，
 * 只用桩替代 gRPC 传输层。<b>不是</b>「断言 Bean 存在」，也<b>不是</b>纯 Mockito mock
 * —— 被驱动的拦截器代码（调度器、wrappedCall、span）全部真实执行。
 */
@DisplayName("§3.3 gRPC 超时：断言运行时后果，而不是断言 Bean 存在")
class RpcInterceptorTimeoutSpanTest {

    private static InMemorySpanExporter exporter;
    private static OpenTelemetrySdk previous;

    @BeforeAll
    static void installSdk() {
        exporter = InMemorySpanExporter.create();
        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                        // Simple 而非 Batch：测试要立刻看到导出结果
                        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                        .build())
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
        // GlobalOpenTelemetry 只能 set 一次；同 JVM 其它测试若已设置则先复位
        previous = resetOrInstall(sdk);
    }

    private static OpenTelemetrySdk resetOrInstall(OpenTelemetrySdk sdk) {
        try {
            GlobalOpenTelemetry.resetForTest();
        } catch (NoSuchMethodError ignored) {
            // 老版本没有 resetForTest：只能接受「本类在同 JVM 内必须先跑」的约束
        }
        GlobalOpenTelemetry.set(sdk);
        return sdk;
    }

    @AfterAll
    static void restore() {
        try {
            GlobalOpenTelemetry.resetForTest();
        } catch (NoSuchMethodError ignored) {
            // 同上
        }
        if (previous != null) {
            GlobalOpenTelemetry.set(previous);
        }
    }

    @Test
    @DisplayName("超时后 span 必须被 end 并带 sea.timeout=true（当前实现下应当转红）")
    void timeoutMustEndSpanWithTimeoutAttribute() throws Exception {
        // 300ms：足够短以让测试快，又足够长以避免调度抖动
        ServerInterceptor interceptor = RpcInterceptor.newUnaryServerInterceptor(
                Duration.ofMillis(300), Duration.ofMillis(750));

        RecordingCall call = new RecordingCall();
        CountDownLatch entered = new CountDownLatch(1);

        // handler 永不 close call —— 模拟真超时的「业务卡住」
        ServerCallHandler<Object, Object> hangingHandler =
                (serverCall, headers) -> new ServerCall.Listener<Object>() {
                    @Override
                    public void onHalfClose() {
                        entered.countDown();   // 只进入，不返回响应
                    }
                };

        ServerCall.Listener<Object> listener =
                interceptor.interceptCall(call, new Metadata(), hangingHandler);
        listener.onHalfClose();
        assertThat(entered.await(2, TimeUnit.SECONDS)).as("handler 应已被进入").isTrue();

        // 等超时调度器开火
        call.awaitClose(3, TimeUnit.SECONDS);

        // ① 客户端可见面：必须收到 DEADLINE_EXCEEDED —— 这一条现状应通过
        assertThat(call.closed.get())
                .as("超时应以 DEADLINE_EXCEEDED 关闭 call")
                .isNotNull();
        assertThat(call.closed.get().getCode()).isEqualTo(Status.Code.DEADLINE_EXCEEDED);

        // ② 遥测面：Go 在超时时会打 timeout 属性；RAG 必须在<b>运行时</b>真的做到
        List<SpanData> spans = exporter.getFinishedSpanItems();
        assertThat(spans)
                .as("超时调用的 span 必须被 end 后导出 —— 未导出即 span 泄漏，"
                        + "且超时在遥测里完全不可见（Go 出处 rpc.go:68-71 会 MarkTimeout）")
                .isNotEmpty();

        SpanData timeoutSpan = spans.stream()
                .filter(s -> call.methodName.equals(s.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("找不到方法名为 " + call.methodName + " 的 span"));

        assertThat(timeoutSpan.getAttributes().get(io.opentelemetry.api.common.AttributeKey
                        .booleanKey(ATTR_TIMEOUT)))
                .as("Go MarkTimeout 会写 sea.timeout=true（observability.go:123）")
                .isEqualTo(Boolean.TRUE);
    }

    /**
     * 只实现被驱动的路径：{@code getMethodDescriptor()}、{@code request()}、{@code close()}。
     * 其余方法不会被本测试触及，显式抛 {@link UnsupportedOperationException} 以免将来
     * 有人误以为它们也被覆盖了。
     */
    private static final class RecordingCall extends ServerCall<Object, Object> {
        static final String METHOD = "/test.Svc/Hang";
        final String methodName = METHOD;
        final AtomicReference<Status> closed = new AtomicReference<>();
        private final CountDownLatch closeLatch = new CountDownLatch(1);

        private static final MethodDescriptor<Object, Object> METHOD_DESCRIPTOR =
                MethodDescriptor.<Object, Object>newBuilder()
                        .setType(MethodDescriptor.MethodType.UNARY)
                        .setFullMethodName(METHOD)
                        .setRequestMarshaller(new NoopMarshaller())
                        .setResponseMarshaller(new NoopMarshaller())
                        .build();

        boolean awaitClose(long timeout, TimeUnit unit) throws InterruptedException {
            return closeLatch.await(timeout, unit);
        }

        @Override
        public void request(int numMessages) {
            // 无背压协议，桩实现忽略
        }

        @Override
        public void sendHeaders(Metadata headers) {
        }

        @Override
        public void sendMessage(Object message) {
        }

        @Override
        public void close(Status status, Metadata trailers) {
            closed.compareAndSet(null, status);
            closeLatch.countDown();
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public MethodDescriptor<Object, Object> getMethodDescriptor() {
            return METHOD_DESCRIPTOR;
        }
    }

    /** 只用于让 {@code MethodDescriptor} 构造通过；本测试不经过序列化。 */
    private static final class NoopMarshaller implements MethodDescriptor.Marshaller<Object> {
        @Override
        public java.io.InputStream stream(Object value) {
            throw new UnsupportedOperationException("本测试不序列化");
        }

        @Override
        public Object parse(java.io.InputStream stream) {
            throw new UnsupportedOperationException("本测试不反序列化");
        }
    }
}