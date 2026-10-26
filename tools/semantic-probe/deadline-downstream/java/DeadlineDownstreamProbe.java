import com.rag.common.observability.GoStyleRequestDeadline;
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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 【global 域 · 第四十六轮】E38② 重估的 **RAG 半边活体探针**。
 *
 * <p>测的行为（与 Go 半边逐字对齐）：
 * <b>上游请求的 deadline 已经过期 ⇒ 业务代码再发一次下游 gRPC 调用 ⇒ 会怎样？</b>
 *
 * <p>🔴 跑的是**生产类** {@link RpcInterceptor}（不是自己 new 一个等价物）
 * ⇒ 与 Go 半边（真源 {@code observability.NewUnaryServerInterceptor}）逐键对拍。
 *
 * <p>🔴 真传输层：真 Netty server（随机端口）+ 真 channel（{@code dns:///} 直连，
 * 绕开 Nacos 服务发现）⇒ 观测的是**网络上真正传过来的字节**。
 *
 * <p>⚠️ <b>为什么 RAG 侧要观测两个载体</b>：
 * RAG 有<b>两个</b>互不相通的 deadline 载体，而 Go 只有<b>一个</b>（ctx）：
 * <ul>
 *   <li>{@link GoStyleRequestDeadline} —— <b>出向 gRPC 调用</b>实际使用的那个
 *       （favorite-rpc / follow-rpc 的 {@code applyTo(stub)} 读它）</li>
 *   <li>{@code RpcInterceptor.DeadlineState} —— <b>SQL 路径</b>使用的那个
 *       （{@code DeadlineQueryTimeoutInjector} 读它）</li>
 * </ul>
 * ⇒ <b>出向调用与 SQL 走的是两套载体</b>，这正是本探针要暴露的东西。
 *
 * <p>输出格式与 Go 半边一致：{@code PROBE|<case>|<key>=<value>|...}
 */
public final class DeadlineDownstreamProbe {

    /** 下游服务的全方法名（两边一致；Go 半边用 pb.LikeService/GetLikeCount）。 */
    private static final String DOWNSTREAM_METHOD_NAME = "probe.Downstream/Hang";

    /** 上游服务的全方法名（⚠️ 必须与上游服务名同前缀 ⇒ grpc-java 的硬约束）。 */
    private static final String UPSTREAM_METHOD_NAME = "probe.Upstream/Hang";

    /** 与 Go 半边同一组参数。 */
    private static final Duration TIMEOUT = Duration.ofMillis(300);
    private static final Duration SLOW_THRESHOLD = Duration.ofMillis(750);

    /**
     * 「出向调用是否立刻失败」的判定窗口（与 Go 半边同值 400ms）。
     *
     * <p>⚠️ 必须**远大于**一次本地回环 RPC 的正常耗时（~1-5ms），
     * 又必须**小于**下游 hang server 的响应时间（30s）⇒ 400ms 是安全档。
     */
    private static final long DOWNSTREAM_FAIL_FAST_WINDOW_MS = 400L;

    /** 等到 deadline 过期之后再多等这么久，确保真的到期。 */
    private static final long EXPIRY_GRACE_MS = 50L;

    public static void main(String[] args) throws Exception {
        try {
            GlobalOpenTelemetry.resetForTest();
        } catch (NoSuchMethodError ignored) {
            // 老版本没有 resetForTest
        }
        GlobalOpenTelemetry.set(io.opentelemetry.sdk.OpenTelemetrySdk.builder().build());

        // ── 下游 hang server：永不 onNext / onCompleted ──────────────────────
        Server downstream = ServerBuilder.forPort(0)
                .addService(ServerServiceDefinition.builder("probe.Downstream")
                        .addMethod(HANG_METHOD, ServerCalls.asyncUnaryCall((req, obs) -> {
                            // 永不响应 ⇒ 出向调用若没有 deadline 就会挂住
                        }))
                        .build())
                .build().start();
        ManagedChannel downstreamChannel = NettyChannelBuilder
                .forTarget("dns:///127.0.0.1:" + downstream.getPort())
                .usePlaintext()
                .build();

        // ── 上游 server（生产拦截器）────────────────────────────────────────
        CountDownLatch entered = new CountDownLatch(1);
        // ⚠️ handler 要先等 deadline 过期（~350ms）再跑那次**挂住**的下游调用（400ms）
        //    ⇒ 主线程必须等它**做完**，否则读到的是 null（不是「没观测到」）
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> outcome = new AtomicReference<>();
        ServerBuilder<?> builder = ServerBuilder.forPort(0);
        // ← 被测的那一行：生产拦截器
        builder.intercept(RpcInterceptor.newUnaryServerInterceptor(TIMEOUT, SLOW_THRESHOLD));
        ServerServiceDefinition svc = ServerServiceDefinition.builder("probe.Upstream")
                .addMethod(UPSTREAM_METHOD, ServerCalls.asyncUnaryCall((req, obs) -> {
                    entered.countDown();
                    // ⚠️ handler 跑在 grpc 线程上 ⇒ DeadlineState 绑定在**跑 handler 的线程**
                    outcome.set(measureDownstream(downstreamChannel));
                    done.countDown();
                    // 永不 onNext / onCompleted ⇒ 让超时调度器去关 call
                }))
                .build();
        Server upstream = builder.addService(svc).build().start();

        ManagedChannel upstreamChannel = NettyChannelBuilder
                .forTarget("dns:///127.0.0.1:" + upstream.getPort())
                .usePlaintext()
                .build();
        try {
            // 客户端**不带** deadline ⇒ 让服务端自己的超时来触发
            //   ⚠️ 这样才能观测到「拦截器在 deadline 时刻返回，而业务线程继续跑」
            try {
                ClientCalls.blockingUnaryCall(upstreamChannel, UPSTREAM_METHOD, CallOptions.DEFAULT, new byte[0]);
                System.err.println("probe: upstream call unexpectedly succeeded");
                System.exit(2);
            } catch (StatusRuntimeException ignored) {
                // 预期：服务端超时关闭 ⇒ DEADLINE_EXCEEDED
            }
            if (!entered.await(2, TimeUnit.SECONDS)) {
                System.err.println("probe: handler was never entered — the probe measured nothing");
                System.exit(2);
            }
            if (!done.await(10, TimeUnit.SECONDS)) {
                System.err.println("probe: handler never finished the downstream measurement");
                System.exit(2);
            }
            String result = outcome.get();
            if (result == null) {
                System.err.println("probe: handler never produced the downstream outcome");
                System.exit(2);
            }
            System.out.printf("PROBE|downstream_deadline_failfast|%s%n", result);
        } finally {
            upstreamChannel.shutdownNow();
            upstream.shutdownNow();
            downstreamChannel.shutdownNow();
            downstream.shutdownNow();
        }
    }

    /**
     * deadline 过期后发一次下游调用，返回观测串。
     *
     * <p>观测四个键：
     * <ul>
     *   <li>{@code failed_fast} —— 下游调用是否**立刻**返回（= 拿到 error）</li>
     *   <li>{@code code} —— 那次调用的 gRPC 状态码（未失败则为 0）</li>
     *   <li>{@code outgoing_carrier} —— <b>出向调用实际使用</b>的载体
     *       （{@link GoStyleRequestDeadline}）的状态三档</li>
     *   <li>{@code sql_carrier} —— <b>SQL 路径</b>使用的载体
     *       （{@code RpcInterceptor.DeadlineState}）的状态三档</li>
     * </ul>
     *
     * <p>🔴 <b>出向调用必须用生产写法</b>：RAG 的 rpc 组件里出向 gRPC 有两种写法 ——
     * ① {@code GoStyleRequestDeadline.applyTo(stub)}（favorite-rpc / follow-rpc）
     * ② 裸 stub（like-rpc / message-rpc / comment-rpc）。
     * 本探针用 ② 做行为观测（它能直接测到「调用有没有上限」），
     * 并用 {@code outgoing_carrier} 把 ① 的命运也观测出来
     * ⇒ {@code applyTo} 就是 {@code DEADLINE_NANOS.get()} ⇒ 载体缺席 ⇒ 它原样返回 stub。
     */
    private static String measureDownstream(ManagedChannel downstreamChannel) {
        // ① 等到有效期限过去
        //    ⚠️ 读的是**生产拦截器算出来的**有效期限（DeadlineState）⇒ 不用猜
        long left = RpcInterceptor.DeadlineState.remainingMillis();
        if (left > 0) {
            try {
                Thread.sleep(left + EXPIRY_GRACE_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } else {
            try {
                Thread.sleep(EXPIRY_GRACE_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // ② 两个载体的状态（三档，与 Go 半边逐字一致）
        String outgoingCarrier = carrierState(
                GoStyleRequestDeadline.isPresent(),
                GoStyleRequestDeadline.isPresent() ? GoStyleRequestDeadline.remainingMillis() : -1L);
        String sqlCarrier = carrierState(
                RpcInterceptor.DeadlineState.remainingMillis() >= 0,
                RpcInterceptor.DeadlineState.remainingMillis());

        // ③ 出向调用：**裸 stub + CallOptions.DEFAULT**（= like-rpc / message-rpc / comment-rpc 的生产写法）
        //    ⚠️ **不给它任何 deadline** ⇒ 若生产代码也不给，它就真的没有上限
        AtomicReference<StatusRuntimeException> thrown = new AtomicReference<>();
        Thread caller = new Thread(() -> {
            try {
                ClientCalls.blockingUnaryCall(downstreamChannel, HANG_METHOD, CallOptions.DEFAULT, new byte[0]);
            } catch (StatusRuntimeException e) {
                thrown.set(e);
            }
        }, "probe-downstream-caller");
        caller.setDaemon(true);
        caller.start();

        boolean failedFast;
        int code = 0;
        try {
            caller.join(DOWNSTREAM_FAIL_FAST_WINDOW_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        failedFast = !caller.isAlive();
        if (failedFast) {
            StatusRuntimeException e = thrown.get();
            if (e != null && e.getStatus() != null) {
                code = e.getStatus().getCode().value();
            }
        }

        return String.format("failed_fast=%b|code=%d|outgoing_carrier=%s|sql_carrier=%s",
                failedFast, code, outgoingCarrier, sqlCarrier);
    }

    /**
     * 载体状态三档。
     *
     * @param present   载体是否存在于当前线程
     * @param remaining 剩余毫秒（-1 表示「没有载体」）
     */
    private static String carrierState(boolean present, long remaining) {
        if (!present) {
            return "absent";
        }
        return remaining <= 0 ? "present_expired" : "present_live";
    }

    private static final MethodDescriptor<byte[], byte[]> HANG_METHOD =
            MethodDescriptor.<byte[], byte[]>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(DOWNSTREAM_METHOD_NAME)
                    .setRequestMarshaller(ByteMarshaller.INSTANCE)
                    .setResponseMarshaller(ByteMarshaller.INSTANCE)
                    .build();

    private static final MethodDescriptor<byte[], byte[]> UPSTREAM_METHOD =
            MethodDescriptor.<byte[], byte[]>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(UPSTREAM_METHOD_NAME)
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

    private DeadlineDownstreamProbe() {
    }
}
