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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🔴 **E38 守门** —— gRPC 超时的**有效期限**必须是
 * {@code min(客户端 deadline, 服务端自己的 timeout)}，与 Go 真源同语义。
 *
 * <h2>Go 出处（真源，冻结仓）</h2>
 * <pre>
 * Sea-RideTheWind_golang/service/common/observability/rpc.go:38
 *   timeoutCtx, cancel := context.WithTimeout(ctx, timeout)
 * </pre>
 * ⇒ {@code ctx} 是 gRPC 传进来的服务端 ctx，<b>携带客户端 deadline</b>
 * ⇒ Go 标准库 {@code WithDeadline} 的语义：父 deadline 更早时<b>直接继承父的</b>
 * ⇒ 有效期限 = {@code min(客户端 deadline, now+timeout)}。
 *
 * <h2>🔴 旧实现为什么是漂的</h2>
 * <p>旧实现只算 {@code System.currentTimeMillis() + timeout.toMillis()}
 * ⇒ <b>客户端 deadline 完全不参与</b>。
 * <br>⇒ 运行期实证（users/admin 实测 · 客户端 deadline 1000ms 档）：
 * <pre>
 * Go  Δ_start=+1005ms / Δ_end=+0ms     （查询随客户端一起死）
 * RAG Δ_start=+2117ms / Δ_end=+1111ms （客户端已放弃，PG 查询仍占着 1.1s）
 * </pre>
 * ⇒ <b>可观察（抓取侧）** ⇒ global 台账 <b>E38</b>（落点在本类 ⇒ global 单写权）。
 *
 * <h2>观测点：为什么读 {@code DeadlineState} 就够</h2>
 * <p>{@code DeadlineState} 是本拦截器绑定到<b>跑 handler 的线程</b>上的 ThreadLocal
 * ⇒ handler 里读到的就是<b>本实现算出来的有效期限</b>
 * ⇒ 断言它 = 「继承了客户端 deadline」，而不是「只算服务端自己的」。
 * <br>⇒ 配套的双侧活体探针在 {@code tools/semantic-probe/rpc-timeout/}
 * （Go 半边直接引用冻结仓真源 ⇒ 跑的就是真源）。
 *
 * <h2>⚠️ 本守门<b>不</b>覆盖什么（别误读成已闭环）</h2>
 * <ul>
 *   <li>🔴 <b>不覆盖 E38 的第 ② 层</b>：「客户端放弃后，服务端在途的 PG 查询随之取消」
 *       —— Go 靠 ctx cancel ⇒ GORM 取消查询；grpc-java 的 {@code ServerCall#close()}
 *       <b>不取消业务线程</b>（框架限制，与 T-14 同根）⇒ <b>Jason 2026-10-26 已拍板「另立项」</b>
 *       ⇒ 本条守门<b>只钉住期限计算</b>；</li>
 *   <li>不覆盖客户端<b>主动 cancel</b>（非 deadline 到点）那一档；</li>
 *   <li>不覆盖 {@code timeout<=0}（不启用服务端超时）那一档（此时<b>刻意不</b>引入客户端 deadline）。</li>
 * </ul>
 *
 * <h2>为什么必须用真传输层</h2>
 * <p>客户端 deadline 是靠<b>入向 {@code grpc-timeout} 头</b>传到服务端的
 * ⇒ stub 掉传输层就测不到它 ⇒ 必须真 Netty server + 真 channel。
 */
@DisplayName("E38：超时的有效期限必须是 min(客户端 deadline, 服务端 timeout) —— 与 Go 的 context.WithTimeout(ctx, d) 同语义")
class RpcInterceptorClientDeadlineGateTest {

    /** 服务端自己的预算（对应 Go 侧的 timeout 参数）。 */
    private static final long SERVER_TIMEOUT_MS = 300L;
    /**
     * 客户端 deadline —— 必须**早于** {@link #SERVER_TIMEOUT_MS}
     * ⇒ 才能区分「继承客户端」与「只算服务端自己的」。
     */
    private static final long CLIENT_DEADLINE_MS = 100L;
    /**
     * 分档阈值：剩余 ≤ 此值 ⇒ 判定为「继承了客户端 deadline」。
     * ⚠️ 取客户端(100ms)与服务端(300ms)之间 ⇒ 两档互斥。
     */
    private static final long CLIENT_DEADLINE_BUCKET_MAX_MS = 150L;

    private static final String SERVICE_NAME = "probe.ClientDeadline";
    /** ⚠️ 不带前导斜杠（grpc-java 约定，见 {@code RpcInterceptorTimeoutDescriptionGateTest}）。 */
    private static final String METHOD_NAME = "probe.ClientDeadline/Hang";

    @Test
    @DisplayName("客户端 deadline(100ms) 早于服务端 timeout(300ms) 时，有效期限必须取客户端的那个")
    void effectiveTimeoutMustInheritClientDeadline() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        AtomicLong observedRemainingMs = new AtomicLong(-1L);

        ServerBuilder<?> builder = ServerBuilder.forPort(0);
        builder.intercept(RpcInterceptor.newUnaryServerInterceptor(
                java.time.Duration.ofMillis(SERVER_TIMEOUT_MS),
                java.time.Duration.ofMillis(750)));
        ServerServiceDefinition svc = ServerServiceDefinition.builder(SERVICE_NAME)
                .addMethod(HANG_METHOD, ServerCalls.asyncUnaryCall((req, obs) -> {
                    entered.countDown();
                    // 🔴 读生产拦截器绑在本线程上的 DeadlineState
                    //    ⇒ 拿到的就是**本实现算出来的有效期限**
                    observedRemainingMs.set(RpcInterceptor.DeadlineState.remainingMillis());
                    // 永不 onNext / onCompleted
                }))
                .build();
        Server server = builder.addService(svc).build().start();
        ManagedChannel channel = NettyChannelBuilder
                .forTarget("dns:///127.0.0.1:" + server.getPort())
                .usePlaintext()
                .build();
        try {
            CallOptions opts = CallOptions.DEFAULT.withDeadlineAfter(CLIENT_DEADLINE_MS, TimeUnit.MILLISECONDS);
            try {
                ClientCalls.blockingUnaryCall(channel, HANG_METHOD, opts, new byte[0]);
            } catch (StatusRuntimeException expected) {
                // 客户端自己的 deadline 到点 ⇒ 预期
            }

            assertThat(entered.await(2, TimeUnit.SECONDS))
                    .as("handler 必须真的被调用过（否则测的不是超时，是空调用）")
                    .isTrue();

            long left = observedRemainingMs.get();
            assertThat(left)
                    .as("DeadlineState 必须已绑定（remainingMillis>=0）⇒ 否则本用例什么都没验到")
                    .isGreaterThanOrEqualTo(0L);
            assertThat(left)
                    .as("Go 真源 rpc.go:38 的 context.WithTimeout(ctx, timeout) 里 ctx 携带客户端 deadline"
                            + " ⇒ 有效期限 = min(客户端 deadline, 服务端 timeout)"
                            + " ⇒ 客户端 " + CLIENT_DEADLINE_MS + "ms 早于服务端 " + SERVER_TIMEOUT_MS + "ms"
                            + " ⇒ 剩余必须落在客户端那一档（≤ " + CLIENT_DEADLINE_BUCKET_MAX_MS + "ms）"
                            + "；若落在服务端那一档（≈ " + SERVER_TIMEOUT_MS + "ms）"
                            + " ⇒ 说明客户端 deadline 没被继承 ⇒ 旧漂移仍在")
                    .isLessThanOrEqualTo(CLIENT_DEADLINE_BUCKET_MAX_MS);
        } finally {
            channel.shutdownNow();
            server.shutdownNow();
        }
    }

    @Test
    @DisplayName("客户端没带 deadline 时，有效期限仍是服务端自己的 timeout（不得被客户端侧影响）")
    void withoutClientDeadlineEffectiveTimeoutStaysServerBudget() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        AtomicLong observedRemainingMs = new AtomicLong(-1L);

        ServerBuilder<?> builder = ServerBuilder.forPort(0);
        builder.intercept(RpcInterceptor.newUnaryServerInterceptor(
                java.time.Duration.ofMillis(SERVER_TIMEOUT_MS),
                java.time.Duration.ofMillis(750)));
        ServerServiceDefinition svc = ServerServiceDefinition.builder(SERVICE_NAME)
                .addMethod(HANG_METHOD, ServerCalls.asyncUnaryCall((req, obs) -> {
                    entered.countDown();
                    observedRemainingMs.set(RpcInterceptor.DeadlineState.remainingMillis());
                }))
                .build();
        Server server = builder.addService(svc).build().start();
        ManagedChannel channel = NettyChannelBuilder
                .forTarget("dns:///127.0.0.1:" + server.getPort())
                .usePlaintext()
                .build();
        try {
            // ⚠️ **不设** deadline ⇒ CallOptions.DEFAULT ⇒ 客户端不传 grpc-timeout
            try {
                ClientCalls.blockingUnaryCall(channel, HANG_METHOD, CallOptions.DEFAULT, new byte[0]);
            } catch (StatusRuntimeException expected) {
                // 服务端自己的 300ms 超时 ⇒ 预期
            }

            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();

            long left = observedRemainingMs.get();
            assertThat(left)
                    .as("客户端没带 deadline ⇒ 有效期限必须仍是服务端自己的 " + SERVER_TIMEOUT_MS + "ms"
                            + "（Go 侧：父 ctx 无 deadline ⇒ WithTimeout 取 now+timeout）"
                            + " ⇒ 若这里变成客户端那一档 ⇒ 说明取值逻辑把「无 deadline」错当成了「有 deadline」")
                    .isGreaterThan(CLIENT_DEADLINE_BUCKET_MAX_MS);
        } finally {
            channel.shutdownNow();
            server.shutdownNow();
        }
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
