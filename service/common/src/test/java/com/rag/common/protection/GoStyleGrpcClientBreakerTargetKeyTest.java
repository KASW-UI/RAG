package com.rag.common.protection;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h2>本类钉死什么</h2>
 *
 * <p><b>T-9（2026-10-25 修复）</b>：Go 的<b>客户端</b>熔断键是
 * {@code path.Join(cc.Target(), method)} ⇒ 键里带 <b>channel 身份</b>
 * ⇒ <b>每个下游服务一份独立熔断器</b>。
 *
 * <p>而修复前的 RAG 只用 {@code method.getFullMethodName()} ⇒ 两个不同下游服务
 * 调用<b>同名方法</b>时<b>共用一份</b>熔断器 ⇒ A 服务的失败会把 B 服务的熔断器也拖开
 * （<b>跨服务污染</b>）。
 *
 * <h2>🔴 本类必须驱动**真实的 {@code interceptCall} 路径**</h2>
 *
 * <p>⚠️ <b>踩过的坑（已修正）</b>：第一版把「键」在测试里<b>自己算</b>出来，
 * 再拿它去查 breaker ⇒ 结果<b>变异测试不转红</b>（把实现的键改回纯方法名，测试照样绿）
 * ⇒ 那是 §3.3.3 说的「<b>绿测试锁死了错误答案</b>」—— 它守的是<b>辅助方法</b>，不是<b>实现</b>。
 * ⇒ 现在改为：<b>只调 {@code interceptCall}</b>，然后从
 * {@link GoStyleGrpcClientBreakerInterceptor#trackedMethods()} 观察<b>实现真的建了几份 breaker</b>。
 *
 * <h2>Go 出处（真源 = L2 框架层 go-zero@v1.10.2）</h2>
 *
 * <pre>
 * // zrpc/internal/clientinterceptors/breakerinterceptor.go:13-18  ← 【客户端】
 * func BreakerInterceptor(ctx context.Context, method string, req, reply any,
 *     cc *grpc.ClientConn, invoker grpc.UnaryInvoker, opts ...grpc.CallOption) error {
 *     breakerName := path.Join(cc.Target(), method)          // ★ 带 target
 *     return breaker.DoWithAcceptableCtx(ctx, breakerName, func() error { ... }, codes.Acceptable)
 * }
 *
 * // zrpc/internal/serverinterceptors/breakerinterceptor.go:29  ← 【服务端，对照】
 *     breakerName := info.FullMethod                        // ★ 不带 target
 * </pre>
 *
 * ⇒ ⚠️ <b>客户端与服务端不同</b>：客户端按「服务 + 方法」分组，服务端只按「方法」分组。
 * ⇒ 本类只测客户端侧；服务端侧由 {@code GoStyleRpcBreakerInterceptor} 负责
 * （它<b>本来就没有</b> target 前缀 ⇒ 与 Go 一致，<b>不要</b>给它加）。
 *
 * <h2>「channel 身份」从哪来（为什么是 {@code authority()}）</h2>
 *
 * <p>grpc-java 1.63 的 {@code ManagedChannel} <b>没有</b> {@code getTarget()}
 * （javap 实测：只有 {@code authority()} 与 {@code toString()}）⇒ 只能用 {@code authority()}。
 * 而 {@code authority()} 在本项目的两种生产配置下<b>都标识「哪个服务」</b>：
 * <ul>
 *   <li>{@code discovery:///message-rpc} ⇒ devh 的 {@code DiscoveryClientNameResolver}
 *       用 <b>URI path 的服务名</b>作 authority
 *       （javap 实测：{@code newNameResolver} 取 {@code uri.getPath().substring(1)}，
 *       {@code getServiceAuthority()} 直接返回该 {@code name} 字段）
 *       ⇒ authority = {@code "message-rpc"} ≡ Go 的 {@code key=message.rpc}；</li>
 *   <li>直连地址 ⇒ authority = {@code "host:port"} ≡ Go 的 {@code direct:///host:port}。</li>
 * </ul>
 *
 * <p>⚠️ <b>已知例外（仅测试环境）</b>：<b>in-process</b> channel 的 authority 恒为
 * {@code "localhost"}（实测）⇒ 多个 in-process channel 会塌缩成同一个身份。
 * ⇒ 身份断言用<b>直连地址的真 channel</b>；行为断言用可控 authority 的轻量 channel
 * （避免真实网络等待）—— 两者合起来覆盖「身份来源」与「分组语义」两个面。
 */
@DisplayName("T-9：客户端熔断键必须带 channel 身份（Go: path.Join(cc.Target(), method)）")
class GoStyleGrpcClientBreakerTargetKeyTest {

    private static final MethodDescriptor<byte[], byte[]> METHOD_DESCRIPTOR =
            MethodDescriptor.<byte[], byte[]>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName("test.FaultySvc/Fail")
                    .setRequestMarshaller(new BytesMarshaller())
                    .setResponseMarshaller(new BytesMarshaller())
                    .build();

    /** 真实 channel 的身份（直连地址 ⇒ authority = host:port）。 */
    private static final List<Channel> OPENED_CHANNELS = new ArrayList<>();

    // ==================== ① 实现真的建了几份 breaker ====================

    @Test
    @DisplayName("① 两个**真实**channel（不同地址）⇒ 实现建了**两份** breaker")
    void twoRealChannelsCreateTwoIndependentBreakers() {
        GoStyleGrpcClientBreakerInterceptor interceptor =
                new GoStyleGrpcClientBreakerInterceptor(true, () -> 0.0D);

        Channel channelA = realChannel(19001);
        Channel channelB = realChannel(19002);

        // ⚠️ 只调 interceptCall ⇒ 观察**实现**建了几份 breaker（不自己算键）
        interceptor.interceptCall(METHOD_DESCRIPTOR, CallOptions.DEFAULT, channelA);
        interceptor.interceptCall(METHOD_DESCRIPTOR, CallOptions.DEFAULT, channelB);

        assertEquals(2, interceptor.trackedMethods(),
                "🔴 两个不同下游服务 ⇒ 必须建两份 breaker（Go: 键含 cc.Target() ⇒ 各自独立）");
    }

    @Test
    @DisplayName("① 同一 channel 调两次 ⇒ 仍然只有**一份** breaker")
    void sameChannelCreatesOnlyOneBreaker() {
        GoStyleGrpcClientBreakerInterceptor interceptor =
                new GoStyleGrpcClientBreakerInterceptor(true, () -> 0.0D);
        Channel channel = realChannel(19003);

        interceptor.interceptCall(METHOD_DESCRIPTOR, CallOptions.DEFAULT, channel);
        interceptor.interceptCall(METHOD_DESCRIPTOR, CallOptions.DEFAULT, channel);

        assertEquals(1, interceptor.trackedMethods(),
                "同一 (服务, 方法) 必须复用同一份 breaker");
    }

    @Test
    @DisplayName("① 两个**真实**channel ⇒ 各自的 breaker **不是同一个对象**")
    void twoRealChannelsGetTwoDistinctBreakerObjects() {
        GoStyleGrpcClientBreakerInterceptor interceptor =
                new GoStyleGrpcClientBreakerInterceptor(true, () -> 0.0D);
        Channel channelA = realChannel(19004);
        Channel channelB = realChannel(19005);

        interceptor.interceptCall(METHOD_DESCRIPTOR, CallOptions.DEFAULT, channelA);
        interceptor.interceptCall(METHOD_DESCRIPTOR, CallOptions.DEFAULT, channelB);

        String keyA = GoStyleGrpcClientBreakerInterceptor.goBreakerName(
                GoStyleGrpcClientBreakerInterceptor.targetOf(channelA),
                METHOD_DESCRIPTOR.getFullMethodName());
        String keyB = GoStyleGrpcClientBreakerInterceptor.goBreakerName(
                GoStyleGrpcClientBreakerInterceptor.targetOf(channelB),
                METHOD_DESCRIPTOR.getFullMethodName());

        assertNotSame(keyA, keyB, "两个不同下游服务的键必须不同");
        assertNotSame(interceptor.breakerForName(keyA), interceptor.breakerForName(keyB),
                "两个不同下游服务的同名方法必须用**两份**熔断器");
    }

    // ==================== ② 键的格式（辅助方法的契约） ====================

    @Test
    @DisplayName("② 键里必须**含** channel 身份（不能只按方法名）")
    void breakerKeyContainsChannelIdentity() {
        String key = GoStyleGrpcClientBreakerInterceptor.goBreakerName(
                "svc-a", METHOD_DESCRIPTOR.getFullMethodName());
        assertTrue(key.contains("svc-a"), "键必须含 channel 身份，实际=" + key);
        assertTrue(key.endsWith(METHOD_DESCRIPTOR.getFullMethodName()),
                "键必须以方法名结尾，实际=" + key);
        // Go 的 path.Join 会折叠连续斜杠 ⇒ 本实现同样折叠
        assertEquals("svc-a/test.FaultySvc/Fail", key, "连续斜杠应被折叠（对齐 Go path.Join）");
    }

    @Test
    @DisplayName("② 取不到 channel 身份时**退化但不崩**（仍是纯方法名）")
    void missingAuthorityFallsBackToMethodName() {
        assertEquals(METHOD_DESCRIPTOR.getFullMethodName(),
                GoStyleGrpcClientBreakerInterceptor.goBreakerName("", METHOD_DESCRIPTOR.getFullMethodName()),
                "无身份 ⇒ 退化为方法名（至少不崩）");
        assertEquals("", GoStyleGrpcClientBreakerInterceptor.targetOf(null),
                "null channel ⇒ 空身份");
    }

    // ==================== ③ 行为层：跨服务污染真的消失 ====================

    @Test
    @DisplayName("③ 服务 A 连续失败把 **A 的**熔断打开后，服务 B **仍可放行**")
    void serviceAFailureDoesNotTripServiceB() throws Exception {
        GoStyleGrpcClientBreakerInterceptor interceptor =
                new GoStyleGrpcClientBreakerInterceptor(true, () -> 0.0D);

        // 两个 authority 不同的轻量 channel（可控身份，避免真实网络等待）
        Channel channelA = new AuthorityChannel("downstream-a");
        Channel channelB = new AuthorityChannel("downstream-b");

        // ⚠️ 必须**跨桶**累积失败：googlebreaker 的 history() 按 Go 语义跳过当前桶
        //   （collection/rollingwindow.go 的 IgnoreCurrentBucket）⇒ 同一桶里几百次失败等于 0
        for (int round = 0; round < 3; round++) {
            for (int i = 0; i < 20; i++) {
                try {
                    drive(interceptor, channelA);
                } catch (GoStyleCircuitOpenException ignored) {
                    // 已打开，继续驱动即可
                }
            }
            sleepOneBucket();
        }

        // 关键断言：B 的调用**不被熔断拒掉**
        boolean bRejected = false;
        try {
            drive(interceptor, channelB);
        } catch (GoStyleCircuitOpenException e) {
            bRejected = true;
        }
        assertTrue(!bRejected,
                "🔴 服务 A 的失败不得拖开服务 B 的熔断器（Go: 键含 cc.Target() ⇒ 各自独立）");
    }

    // ==================== 辅助 ====================

    /** 真调一次（走真实的 {@code interceptCall} + {@code start} ⇒ 触发 onClose ⇒ 记一次失败）。 */
    private static void drive(GoStyleGrpcClientBreakerInterceptor interceptor, Channel channel) {
        ClientCall<byte[], byte[]> call =
                interceptor.interceptCall(METHOD_DESCRIPTOR, CallOptions.DEFAULT, channel);
        call.start(new ClientCall.Listener<>() {
        }, new Metadata());
    }

    /**
     * 造一个<b>真实</b>的 grpc-java channel（不是 mock）⇒ {@code authority()} 是真的。
     *
     * <p>⚠️ 用<b>直连地址</b>而非 in-process：in-process 的 authority 恒为 {@code "localhost"}
     * （实测）⇒ 多个 in-process channel 身份会塌缩 ⇒ 无法用于身份断言。
     */
    private static Channel realChannel(int port) {
        // ⚠️ **必须显式写 dns:/// scheme**：Nacos discovery 在全局 NameResolverRegistry 里
        //    注册了一个高优先级 provider，会把任何无 scheme 的 target 当作 serviceId 去 Nacos 拉实例。
        //    本测试要的是**直连**的裸 channel，显式 scheme 即可绕开服务发现。
        Channel c = ManagedChannelBuilder.forTarget("dns:///127.0.0.1:" + port)
                .usePlaintext()
                .build();
        OPENED_CHANNELS.add(c);
        return c;
    }

    @org.junit.jupiter.api.AfterAll
    static void shutdownChannels() {
        for (Channel c : OPENED_CHANNELS) {
            if (c instanceof io.grpc.ManagedChannel mc) {
                mc.shutdownNow();
            }
        }
    }

    /** 桶长 250ms（window 10s ÷ 40 桶）⇒ 睡 300ms 保证跨过一个桶边界。 */
    private static void sleepOneBucket() throws InterruptedException {
        Thread.sleep(300L);
    }

    /**
     * 轻量 channel：只提供<b>可控的 authority</b> 与一个会失败的调用。
     *
     * <p>⚠️ 它替代的是「传输层」，<b>不是</b> grpc-java 的 channel 机制 ——
     * 身份机制本身由 ① 那组用<b>真 channel</b> + <b>真实 interceptCall</b> 验证。
     */
    private static final class AuthorityChannel extends Channel {
        private final String authority;

        AuthorityChannel(String authority) {
            this.authority = authority;
        }

        @Override
        public String authority() {
            return authority;
        }

        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> newCall(
                MethodDescriptor<ReqT, RespT> methodDescriptor, CallOptions callOptions) {
            return new ClientCall<ReqT, RespT>() {
                @Override
                public void start(Listener<RespT> responseListener, Metadata headers) {
                    responseListener.onClose(Status.INTERNAL.withDescription("boom"),
                            new Metadata());
                }

                @Override
                public void request(int numMessages) {}

                @Override
                public void cancel(String message, Throwable cause) {}

                @Override
                public void halfClose() {}

                @Override
                public void sendMessage(ReqT message) {}
            };
        }
    }

    private static final class BytesMarshaller implements MethodDescriptor.Marshaller<byte[]> {
        @Override
        public InputStream stream(byte[] value) {
            return new ByteArrayInputStream(value);
        }

        @Override
        public byte[] parse(InputStream stream) {
            try {
                return stream.readAllBytes();
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
