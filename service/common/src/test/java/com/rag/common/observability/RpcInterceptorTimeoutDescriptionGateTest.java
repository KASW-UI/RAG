package com.rag.common.observability;

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
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.rag.common.observability.ObservabilityConstants.ATTR_BIZ_MSG;
import static com.rag.common.observability.ObservabilityConstants.ATTR_TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 🔴 **E30 守门** —— gRPC 超时路径上，<b>客户端真正收到</b>的 {@code status.description}
 * 与遥测的 {@code sea.biz_msg} 必须与 Go 真源<b>逐字节一致</b>。
 *
 * <h2>Go 出处（真源，冻结仓）</h2>
 * <pre>
 * Sea-RideTheWind_golang/service/common/observability/rpc.go:68-71
 *   case &lt;-timeoutCtx.Done():
 *       err := status.Error(codes.DeadlineExceeded, timeoutCtx.Err().Error())
 *       MarkTimeout(ctx, err, time.Since(start), rpcAttrs(info.FullMethod, codes.DeadlineExceeded)...)
 *       return nil, err
 * </pre>
 * ⇒ <b>① wire 面</b>：{@code timeoutCtx.Err().Error()} =
 * {@code context.DeadlineExceeded.Error()} = 字面量 <b>{@code "context deadline exceeded"}</b>
 * ⇒ 客户端拿到的 {@code grpc-message} 就是这一串。
 * <br>⇒ <b>② 遥测面</b>：{@code MarkTimeout}（{@code observability.go:108-112}）取
 * {@code msg = err.Error()}，而 Go 的 {@code status.Error(...).Error()} 格式是
 * {@code "rpc error: code = DeadlineExceeded desc = context deadline exceeded"}
 * ⇒ 这就是 {@code sea.biz_msg} 的值。
 *
 * <h2>🔴 旧实现为什么是漂的</h2>
 * <p>旧实现把两处都写成 {@code "rpc timeout after " + timeoutMs + "ms"} ⇒
 * <b>wire 的 description 与遥测的 biz_msg 两侧都不同</b>。
 * <br>⇒ 这条漂移被<b>三个域各自独立</b>记过：{@code comment/rpc.md} U10 ·
 * {@code users/user/rpc.md} V-007（判为「仍成立，但不在本组件判定权内」）·
 * {@code users/admin/rpc.md} 的活体测量 ⇒ 落点全在 {@code service/common} ⇒ 归 global 收口。
 *
 * <h2>⚠️ 修完之后<b>仍剩</b>的差异 = 「表述差异 C-1」（已登记 ⇒ 不得顺手改）</h2>
 * <p>Go 的 {@code codes.Code.String()} 是 **CamelCase**（{@code DeadlineExceeded}），
 * RAG 的 {@code Status.Code.name()} 是 **SCREAMING_SNAKE**（{@code DEADLINE_EXCEEDED}）
 * ⇒ 这一半已登记为 <b>C-1</b>（{@code docs/Intentional_Deviations/common/_signed-deviations.md}
 * C 类，注明「仅常量表示不同，不做臆造映射」）⇒ <b>不改</b>。
 * <br>⇒ 本测试<b>刻意把 C-1 的形态钉住</b>（code 段 = {@code DEADLINE_EXCEEDED}），
 * 以免后人把它当成新漂移去「修」—— 那会违反「签字/已登记偏离不得顺手修正回旧行为」。
 * <br>⇒ 换句话说：<b>desc 段是本轮的 E30（已修）</b>，<b>code 段是 C-1（不动）</b>。
 *
 * <h2>🔴 为什么必须用<b>真传输层</b>（而不是 stub {@code ServerCall}）</h2>
 * <p>本条断言的是「<b>客户端收到什么</b>」⇒ 用 {@code ServerCall} 桩只能证明
 * 「{@code close(status)} 被调用时传了什么」⇒ 中间隔了一层 grpc-java 的
 * {@code Status} → {@code grpc-message} 头的序列化。
 * <br>⇒ 本测试起<b>真的 Netty server</b>（随机端口）+ <b>真的 channel</b>
 * （{@code dns:///127.0.0.1:<port>} 直连，绕开 Nacos 服务发现）⇒ 断言的是
 * <b>网络上真正传过来的那串字节</b>。
 *
 * <h2>证据强度</h2>
 * <p>真 gRPC 传输 + 真客户端 stub + 真 {@link RpcInterceptor}（生产类）
 * + 真 OTel SDK + 真 {@link InMemorySpanExporter}。
 * ⇒ <b>不是</b>纯 mock，<b>不是</b>「断言 Bean 存在」。
 */
@DisplayName("E30：gRPC 超时 —— 客户端收到的 description / 遥测 biz_msg 必须与 Go 逐字节一致")
class RpcInterceptorTimeoutDescriptionGateTest {

    /** Go 的 {@code context.DeadlineExceeded.Error()} 字面量。 */
    private static final String GO_DESCRIPTION = "context deadline exceeded";

    /**
     * 🔴 <b>刻意锁住「C-1 已登记的表述差异」的形态</b>，不是 Go 的字面量。
     *
     * <p>Go 侧的 {@code status.Error(codes.DeadlineExceeded, "context deadline exceeded").Error()}
     * = {@code "rpc error: code = DeadlineExceeded desc = context deadline exceeded"}
     * （grpc-go 的 {@code codes.Code.String()} 是 **CamelCase**，
     * 见 {@code google.golang.org/grpc@v1.81.1/codes/code_string.go:31-59}）。
     * <br>⇒ 而 RAG 的 code 段用 grpc-java 的 {@code Code.name()} = {@code DEADLINE_EXCEEDED}
     * ⇒ <b>这一半是「表述差异 C-1」</b>
     * （{@code docs/Intentional_Deviations/common/_signed-deviations.md} C 类 C-1：
     * 「code 与 desc 均已保留…仅常量表示不同，不做臆造映射」）
     * ⇒ <b>已登记 ⇒ 不得顺手改回</b> ⇒ 本断言把它**钉住**，
     * 以免后人把它当成新漂移去「修」。
     * <br>⇒ 而 {@code desc} 段（{@code context deadline exceeded}）是 <b>E30 的真漂移</b> ⇒ 已修。
     */
    private static final String EXPECTED_BIZ_MSG =
            "rpc error: code = DEADLINE_EXCEEDED desc = context deadline exceeded";

    private static final String SERVICE_NAME = "probe.Timeout";
    /**
     * ⚠️ **不带前导斜杠** —— 这是 grpc-java 的约定（生成的 proto 代码是
     * {@code SERVICE_NAME + '/' + methodName}，见 {@code ArticleServiceGrpc}）⇒
     * {@code MethodDescriptor.extractFullServiceName} 用 {@code lastIndexOf('/')} 截断，
     * 带斜杠会导致它截出 {@code "/probe.Timeout"} ⇒ 与 {@code ServerServiceDefinition}
     * 的 serviceName 对不上 ⇒ grpc 直接抛
     * 「Method name should be prefixed with service name」。
     */
    private static final String METHOD_NAME = "probe.Timeout/Hang";

    private static InMemorySpanExporter exporter;
    private static OpenTelemetrySdk previous;

    @BeforeAll
    static void installSdk() {
        exporter = InMemorySpanExporter.create();
        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                        .build())
                .build();
        // 🔴 必须在**创建拦截器之前**装好 —— RpcInterceptor 在 newUnaryServerInterceptor()
        //   里就取 GlobalOpenTelemetry.get().getTracer("rpc-server")（持住了 Tracer）
        previous = resetOrInstall(sdk);
    }

    private static OpenTelemetrySdk resetOrInstall(OpenTelemetrySdk sdk) {
        try {
            GlobalOpenTelemetry.resetForTest();
        } catch (NoSuchMethodError ignored) {
            // 老版本没有 resetForTest
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
    @DisplayName("客户端收到的 status.description 必须是 Go 的 \"context deadline exceeded\"（不是 \"rpc timeout after Nms\"）")
    void clientVisibleDescriptionMustMatchGoByteForByte() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        Server server = startRealServer(entered);
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

            // ① handler 必须真的被调用过 —— 否则本用例验的是「空调用」
            assertThat(entered.await(2, TimeUnit.SECONDS))
                    .as("handler 必须真的被调用过（否则测的不是超时，是空调用）")
                    .isTrue();

            assertThat(thrown)
                    .as("服务端超时必须让客户端拿到 DEADLINE_EXCEEDED")
                    .isNotNull();
            Status status = thrown.getStatus();
            assertThat(status.getCode())
                    .as("code 两侧都是 4（DEADLINE_EXCEEDED）")
                    .isEqualTo(Status.Code.DEADLINE_EXCEEDED);

            // 🔴 **本用例的核心断言**：客户端**网络上真正收到**的 description
            assertThat(status.getDescription())
                    .as("Go 真源 rpc.go:69 ⇒ description = timeoutCtx.Err().Error() = "
                            + "\"context deadline exceeded\"；旧实现写的是 \"rpc timeout after Nms\" ⇒ 漂移")
                    .isEqualTo(GO_DESCRIPTION);
        } finally {
            channel.shutdownNow();
            server.shutdownNow();
        }
    }

    @Test
    @DisplayName("遥测 sea.biz_msg 必须是 Go 的 \"rpc error: code = DeadlineExceeded desc = ...\"")
    void telemetryBizMsgMustMatchGoByteForByte() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        Server server = startRealServer(entered);
        ManagedChannel channel = NettyChannelBuilder
                .forTarget("dns:///127.0.0.1:" + server.getPort())
                .usePlaintext()
                .build();
        try {
            assertThatThrownBy(() ->
                    ClientCalls.blockingUnaryCall(channel, HANG_METHOD, CallOptions.DEFAULT, new byte[0]))
                    .isInstanceOf(StatusRuntimeException.class);

            // span 在服务端 close() 里 end —— 与客户端收到 status 之间有一个极小的竞态
            // ⇒ 轮询等待导出，不写死 sleep
            SpanData timeoutSpan = awaitSpan(METHOD_NAME, 3, TimeUnit.SECONDS);
            assertThat(timeoutSpan)
                    .as("超时的 span 必须被 end 后导出（Go rpc.go:70 会 MarkTimeout）")
                    .isNotNull();
            assertThat(timeoutSpan.getAttributes().get(
                            io.opentelemetry.api.common.AttributeKey.booleanKey(ATTR_TIMEOUT)))
                    .as("Go MarkTimeout 会写 sea.timeout=true（observability.go:123）")
                    .isEqualTo(Boolean.TRUE);

            // 🔴 遥测面的核心断言
            // ⚠️ desc 段必须是 Go 的 "context deadline exceeded"（E30，已修）
            // ⚠️ code 段是 grpc-java 的 "DEADLINE_EXCEEDED" ⇒ **C-1 已登记的表述差异**，不是漂移
            //    ⇒ 这里刻意钉住 C-1 的形态，防止后人把它当新漂移改掉
            assertThat(timeoutSpan.getAttributes().get(
                            io.opentelemetry.api.common.AttributeKey.stringKey(ATTR_BIZ_MSG)))
                    .as("desc 段必须是 Go 的 \"context deadline exceeded\"（E30 已修）；"
                            + "code 段是 grpc-java 的 Code.name() ⇒ C-1 已登记的表述差异"
                            + "（docs/Intentional_Deviations/common/_signed-deviations.md C 类 C-1），"
                            + "Go 侧同名位置是 grpc-go 的 codes.Code.String() = \"DeadlineExceeded\"")
                    .isEqualTo(EXPECTED_BIZ_MSG);
        } finally {
            if (channel != null) {
                channel.shutdownNow();
            }
            server.shutdownNow();
        }
    }

    // ── 基础设施 ────────────────────────────────────────────────────────────

    /**
     * 起一个<b>真的</b> Netty gRPC server，随机端口，挂上生产拦截器。
     *
     * <p>⚠️ 拦截器在**这里**创建（不是静态字段）⇒ 保证它取到的是本测试装的那个 SDK 的 Tracer。
     */
    private static Server startRealServer(CountDownLatch entered) throws Exception {
        ServerBuilder<?> builder = ServerBuilder.forPort(0);
        builder.intercept(RpcInterceptor.newUnaryServerInterceptor(
                java.time.Duration.ofMillis(300), java.time.Duration.ofMillis(750)));
        ServerServiceDefinition svc = ServerServiceDefinition.builder(SERVICE_NAME)
                .addMethod(HANG_METHOD, ServerCalls.asyncUnaryCall((req, obs) -> {
                    entered.countDown();
                    // 永不 onNext / onCompleted ⇒ 模拟「业务卡住」⇒ 只有服务端超时能结束它
                }))
                .build();
        return builder.addService(svc).build().start();
    }

    private static SpanData awaitSpan(String name, long timeout, TimeUnit unit) throws InterruptedException {
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

    /** 只为让 MethodDescriptor 构造通过；本测试传空字节，不做业务序列化。 */
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
}
