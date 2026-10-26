import com.rag.common.observability.RpcInterceptor;
import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ServerCalls;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 【global 域 · 第三十轮】E30 的 **RAG 半边活体探针**。
 *
 * <p>跑的是**生产类** {@link RpcInterceptor}（不是自己 new 一个等价物）
 * ⇒ 与 Go 半边（真源 {@code observability.NewUnaryServerInterceptor}）逐键对拍。
 *
 * <p>🔴 **真传输层**：真 Netty server（随机端口）+ 真 channel（{@code dns:///} 直连，
 * 绕开 Nacos 服务发现）⇒ 观测的是**网络上真正传过来的字节**。
 *
 * <p>输出格式与 Go 半边一致：{@code PROBE|<case>|<key>=<value>|...}
 */
public final class TimeoutProbe {

    private static final String SERVICE_NAME = "probe.Timeout";
    /** ⚠️ 不带前导斜杠（grpc-java 约定，见生成的 proto 代码）。 */
    private static final String METHOD_NAME = "probe.Timeout/Hang";

    /** 与 Go 半边同一组参数。 */
    private static final Duration TIMEOUT = Duration.ofMillis(300);
    private static final Duration SLOW_THRESHOLD = Duration.ofMillis(750);

    public static void main(String[] args) throws Exception {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                        .build())
                .build();
        try {
            GlobalOpenTelemetry.resetForTest();
        } catch (NoSuchMethodError ignored) {
            // 老版本没有 resetForTest
        }
        GlobalOpenTelemetry.set(sdk);

        CountDownLatch entered = new CountDownLatch(1);
        ServerBuilder<?> builder = ServerBuilder.forPort(0);
        // ← 被测的那一行：生产拦截器
        builder.intercept(RpcInterceptor.newUnaryServerInterceptor(TIMEOUT, SLOW_THRESHOLD));
        ServerServiceDefinition svc = ServerServiceDefinition.builder(SERVICE_NAME)
                .addMethod(HANG_METHOD, ServerCalls.asyncUnaryCall((req, obs) -> {
                    entered.countDown();
                    // 永不 onNext / onCompleted ⇒ 模拟「业务卡住」
                }))
                .build();
        Server server = builder.addService(svc).build().start();

        ManagedChannel channel = NettyChannelBuilder
                .forTarget("dns:///127.0.0.1:" + server.getPort())
                .usePlaintext()
                .build();
        try {
            StatusRuntimeException thrown = null;
            try {
                ClientCalls.blockingUnaryCall(channel, HANG_METHOD, CallOptions.DEFAULT, new byte[0]);
            } catch (StatusRuntimeException e) {
                thrown = e;
            }
            if (thrown == null) {
                System.err.println("probe: no StatusRuntimeException — server-side timeout did not fire");
                System.exit(2);
            }
            if (!entered.await(2, TimeUnit.SECONDS)) {
                System.err.println("probe: handler was never entered — the probe measured nothing");
                System.exit(2);
            }
            Status status = thrown.getStatus();

            // ── case 1：wire 面 ────────────────────────────────────────────
            System.out.printf("PROBE|timeout_wire|code=%d|desc=%s%n",
                    status.getCode().value(), status.getDescription());

            // ── case 2：遥测面（span 的 sea.biz_msg）────────────────────────
            SpanData span = awaitSpan(exporter, METHOD_NAME, 3, TimeUnit.SECONDS);
            if (span == null) {
                System.err.println("probe: timeout span was not exported — telemetry side unobservable");
                System.exit(2);
            }
            String bizMsg = span.getAttributes().get(AttributeKey.stringKey("sea.biz_msg"));
            System.out.printf("PROBE|timeout_telemetry|code=%d|msg=%s%n",
                    status.getCode().value(), bizMsg);
        } finally {
            channel.shutdownNow();
            server.shutdownNow();
        }

        runClientDeadlineCase();
    }

    /**
     * E38 的 RAG 半边事实采集 —— 「客户端 deadline 早于服务端 timeout」这一档。
     *
     * <p>🔴 观测点：<b>生产类 {@code RpcInterceptor}</b> 传给 handler 的
     * {@code DeadlineState}（ThreadLocal）⇒ 它里面的 deadline 就是
     * <b>本实现算出来的有效期限</b> ⇒ 直接可观测，不用猜。
     *
     * <p>⚠️ 只用「落在哪个区间」做判定，不打印绝对值 ⇒ 两侧可稳定比对。
     */
    private static void runClientDeadlineCase() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        AtomicLong observedRemainingMs = new AtomicLong(-1);
        ServerBuilder<?> builder = ServerBuilder.forPort(0);
        builder.intercept(RpcInterceptor.newUnaryServerInterceptor(TIMEOUT, SLOW_THRESHOLD));
        ServerServiceDefinition svc = ServerServiceDefinition.builder(SERVICE_NAME)
                .addMethod(HANG_METHOD, ServerCalls.asyncUnaryCall((req, obs) -> {
                    entered.countDown();
                    // ⚠️ handler 跑在 grpc 的线程上 ⇒ DeadlineState 绑定在**跑 handler 的线程**
                    //    ⇒ 这里能读到生产拦截器算出来的有效期限
                    long left = com.rag.common.observability.RpcInterceptor.DeadlineState.remainingMillis();
                    observedRemainingMs.set(left);
                }))
                .build();
        Server server = builder.addService(svc).build().start();
        ManagedChannel channel = NettyChannelBuilder
                .forTarget("dns:///127.0.0.1:" + server.getPort())
                .usePlaintext()
                .build();
        try {
            // 客户端 deadline = 1s（早于服务端 300ms？）—— ⚠️ 注意本探针的服务端 timeout 是 300ms
            // ⇒ 要区分「继承客户端」必须让**客户端 deadline 早于服务端 timeout**
            // ⇒ 这里用 100ms 的客户端 deadline（早于服务端 300ms）
            io.grpc.CallOptions opts = io.grpc.CallOptions.DEFAULT.withDeadlineAfter(100, TimeUnit.MILLISECONDS);
            try {
                ClientCalls.blockingUnaryCall(channel, HANG_METHOD, opts, new byte[0]);
            } catch (StatusRuntimeException ignored) {
                // 客户端自己的 deadline 到点 ⇒ 预期
            }
            if (!entered.await(2, TimeUnit.SECONDS)) {
                System.err.println("probe: client-deadline case handler never entered");
                System.exit(2);
            }
            long left = observedRemainingMs.get();
            // 分档：≤150ms ⇒ 继承的是**客户端**的 deadline；否则 ⇒ 用的服务端自己的 300ms
            // ⚠️ bucket 名与 Go 半边**逐字一致**（脚本按 key 配对比对）
            String bucket = left >= 0 && left <= 150 ? "client_100ms" : "server_own";
            System.out.printf("PROBE|client_deadline_ctx|deadline_set=%b|bucket=%s%n",
                    left >= 0, bucket);
        } finally {
            channel.shutdownNow();
            server.shutdownNow();
        }
    }

    private static SpanData awaitSpan(InMemorySpanExporter exporter, String name,
                                      long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (System.nanoTime() < deadline) {
            List<SpanData> spans = exporter.getFinishedSpanItems();
            for (SpanData s : spans) {
                if (name.equals(s.getName()) && s.getKind() == SpanKind.SERVER) {
                    return s;
                }
            }
            Thread.sleep(20);
        }
        return null;
    }

    private static final MethodDescriptor<byte[], byte[]> HANG_METHOD =
            MethodDescriptor.<byte[], byte[]>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(METHOD_NAME)
                    .setRequestMarshaller(ByteMarshaller.INSTANCE)
                    .setResponseMarshaller(ByteMarshaller.INSTANCE)
                    .build();

    private static final class ByteMarshaller implements MethodDescriptor.Marshaller<byte[]> {
        static final ByteMarshaller INSTANCE = new ByteMarshaller();

        @Override
        public InputStream stream(byte[] value) {
            return new ByteArrayInputStream(value == null ? new byte[0] : value);
        }

        @Override
        public byte[] parse(InputStream stream) {
            return new byte[0];
        }
    }

    private TimeoutProbe() {
    }
}
