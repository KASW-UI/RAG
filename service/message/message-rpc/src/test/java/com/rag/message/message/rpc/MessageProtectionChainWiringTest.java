package com.rag.message.message.rpc;

import com.rag.common.protection.GoStyleProtectionProperties;
import com.rag.common.protection.GoStyleRpcProtectionChain;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerBuilder;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import net.devh.boot.grpc.server.serverfactory.GrpcServerConfigurer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * <b>DRIFT-MSG-V-026 的逐组件守门（message-rpc 组件）</b>：熔断 / 降载链必须<b>真的</b>挂到本组件的 gRPC server 上。
 *
 * <p><b>为什么必须有这条（2026-10-27 · G2 lane 补齐）</b>：
 * 熔断 / 降载在 Go 侧是 <b>zrpc 框架级</b>能力，每个 rpc server 都自带；
 * 本仓把链的定义点放在共享模块 {@code GoStyleRpcProtectionChain}，
 * 但<b>每个组件仍然要自己把链交给 ServerBuilder</b> ——
 * 漏掉任何个组件，那个组件就<b>既没有 recover、也没有熔断、也没有降载</b>，而<b>不报任何错</b>（AGENTS.md §3.3 的经典形态）。
 * ⇒ ⇒ 兄弟组件（task-rpc / like-rpc）都有这条守门，而<b>本组件此前没有</b>
 * ⇒ ⇒ 本组件的 A2 轴此前只有 <b>L1（源码级）</b>证据，<b>没有运行期证据</b>
 * ⇒ ⇒ 本类补上：<b>组件级、运行期、确定性</b>。
 *
 * <p><b>本测试断言五件事</b>：
 * <ol>
 *   <li>{@code @Import} 列表含 {@code GoStyleProtectionConfiguration}
 *       ⇒ {@code GoStyleProtectionProperties} Bean 真的存在
 *       （它是 {@code @ConfigurationProperties} 类，<b>不会</b>被组件扫描自动注册，
 *       缺了它 {@code rpcInterceptorConfigurer} 根本注入不到参数 ⇒ 上下文起不来）；</li>
 *   <li>本组件交给 {@code ServerBuilder} 的那条链的<b>层名列表</b> == {@code [recover, breaker, shedding]}
 *       ⇒ 一次同时钉住「有 breaker」「有 shedding」「层序 = recover→breaker→shedding」；</li>
 *   <li><b>实际装上的链</b> == <b>配置推出的构成</b>（{@code layerNames(chain)} == {@code compose(props).layerNames()}）
 *       ⇒ 抓住「配置说三层、实际只装两层」这类装配与配置脱节的漂移；</li>
 *   <li>关掉降载 ⇒ 层名列表变成 {@code [recover, breaker]} ⇒ 证明降载层是<b>条件装的</b>；</li>
 *   <li><b>熔断层</b>与<b>降载层</b>各自真的包住了业务 call（包装层归属）
 *       ⇒ 证明它们不只是「名单上有」，而是<b>真的在调用路径上</b>。</li>
 * </ol>
 *
 * <p><b>Go 出处</b>：{@code zrpc/server.go:144-167} 的注册序 = Trace → Recover → Stat → Prometheus
 * → Breaker → Shedding ⇒ 本仓的链 = <b>recover → breaker → shedding → 业务观测拦截器</b>；
 * 其中 Breaker 恒挂（{@code Middlewares.Breaker} 默认 true）· Shedding 仅在 {@code CpuThreshold > 0} 时挂
 * （{@code zrpc/server.go:157-163}）⇒ ⇒ 本类的层名断言与「降载条件装」断言逐项对应。
 *
 * <p><b>语义等价论证</b>：本类只做<b>只读观测</b>（{@code layerNames} 是纯读，不改链、不改配置）
 * ⇒ ⇒ 零行为影响；判据取自共享层的<b>唯一计算点</b> {@code compose(props)}
 * ⇒ ⇒ 「配置推出的构成」与「实际构造出的链」不可能不一致 ⇒ ⇒ 域侧不必各抄一遍条件。
 *
 * <p>⚠️ <b>为什么第 2/3/4 条与第 5 条互补而非替代</b>：层名列表证明「有哪些层、什么顺序」，
 * 但<b>证不出「这些层真的包住了调用」</b>；包装层归属证明「真的包住了」，
 * 但<b>证不出层序</b> ⇒ ⇒ <b>五条合起来才是「A2 链序对齐」的完整判据</b>（任何单条都不充分）。
 */
@DisplayName("DRIFT-MSG-V-026 MessageRpcApplication：熔断/降载链必须真的挂到 gRPC server 上")
class MessageProtectionChainWiringTest {

    @Test
    @DisplayName("Application 必须 @Import GoStyleProtectionConfiguration（否则 properties Bean 不存在）")
    void applicationImportsProtectionConfiguration() {
        var importAnnotation = MessageRpcApplication.class
                .getAnnotation(org.springframework.context.annotation.Import.class);
        assertThat(importAnnotation)
                .as("MessageRpcApplication 必须有 @Import；没有它 = common 的 Bean 一个都不装配")
                .isNotNull();

        assertThat(java.util.Arrays.asList(importAnnotation.value()))
                .as("@Import 列表必须含 GoStyleProtectionConfiguration —— "
                        + "它是 GoStyleProtectionProperties（breaker / cpuShedding 开关与阈值）的唯一注册点，"
                        + "漏掉它 = 上下文起不来（缺 Bean），不是「保护层静默关闭」")
                .contains(com.rag.common.protection.GoStyleProtectionConfiguration.class);
    }

    @Test
    @DisplayName("🔴 链的层名列表必须是 [recover, breaker, shedding]（共享层原生判据 · 一次钉住两层 + 层序）")
    void chainLayerNamesMatchComposedChain() {
        ServerInterceptor interceptor = mock(ServerInterceptor.class);

        GoStyleProtectionProperties props = new GoStyleProtectionProperties();
        props.getCpuShedding().setEnabled(true);
        // ⚠️ 阈值抬到 999（Go 的 tag 声明域 [0:1000) 的上界）⇒ 即使整机 CPU 打满也不触发降载
        //    ⇒ 本用例只依赖「层在不在链上 + 顺序」，**不依赖跑测试那一刻的机器负载**（确定性）。
        props.getCpuShedding().setCpuThreshold(999L);

        ServerInterceptor chained = captureChainedInterceptor(interceptor, props);

        assertThat(GoStyleRpcProtectionChain.layerNames(chained))
                .as("本组件交给 gRPC server 的链必须含 recover → breaker → shedding 三层"
                        + "（Go: zrpc/server.go:144-167 的注册序 ⇒ 本仓的链 = recover → breaker → shedding"
                        + " → 业务观测拦截器）。若少了 breaker 或 shedding ⇒ 该组件的熔断 / 降载形同虚设，"
                        + "且**不报任何错**。")
                .containsExactly("recover", "breaker", "shedding");

        assertThat(GoStyleRpcProtectionChain.layerNames(chained))
                .as("**实际装到 server 上的链**必须与**配置推出的构成**逐项一致"
                        + "（compose() 是构成的唯一计算点 ⇒ 这条断言能抓住"
                        + "「配置说三层、实际只装两层」这类装配与配置脱节的漂移）。")
                .isEqualTo(GoStyleRpcProtectionChain.compose(props).layerNames());
    }

    @Test
    @DisplayName("🔴 关掉降载 ⇒ 层名列表必须变成 [recover, breaker]（证明降载层是**条件装的**，不是恒在）")
    void sheddingLayerIsConditional() {
        ServerInterceptor interceptor = mock(ServerInterceptor.class);

        GoStyleProtectionProperties props = new GoStyleProtectionProperties();
        props.getCpuShedding().setEnabled(false);

        ServerInterceptor chained = captureChainedInterceptor(interceptor, props);

        assertThat(GoStyleRpcProtectionChain.layerNames(chained))
                .as("降载关掉后，链上必须**只剩** recover → breaker 两层"
                        + "（Go: zrpc/server.go:160-163 CpuThreshold > 0 才挂 Shedding）。"
                        + "若降载层仍在 ⇒ 说明它被写成了恒在 ⇒ 与 Go 的条件挂载不一致。")
                .containsExactly("recover", "breaker");
    }

    @Test
    @DisplayName("链上真的有 **breaker** 包装层（降载已关 ⇒ 唯一可能包住业务 call 的就是 breaker）")
    void breakerLayerWrapsTheBusinessInterceptor() {
        ServerInterceptor interceptor = mock(ServerInterceptor.class);

        // ⚠️ 关掉 CPU 降载：**本用例只断言熔断层**。
        //    关掉降载之后，链上唯一可能包住业务 call 的就是 breaker ⇒
        //    「拿到的不是原始 call」这一条**恰好**等价于「breaker 在链上」。
        //    （降载层由另一条用例单独断言 —— 两条合起来才覆盖 Go 的两层。）
        GoStyleProtectionProperties props = new GoStyleProtectionProperties();
        props.getCpuShedding().setEnabled(false);

        ChainRun run = runChainAndCaptureBusinessCall(interceptor, props);

        assertThat(run.businessCall())
                .as("链上有 breaker ⇒ 业务 interceptor 拿到的必须是包装过的 call。"
                        + "若拿到的是原始 call，说明熔断层没挂上 —— 即 breaker 形同虚设。")
                .isNotSameAs(run.rawCall());

        assertThat(run.businessCall().getClass().getEnclosingClass())
                .as("降载已关 ⇒ 此刻唯一可能包住业务 call 的就是 **breaker 层**；"
                        + "若这一层归属不是 com.rag.common.protection.GoStyleRpcBreakerInterceptor，说明链的层次不对"
                        + "（例如 breaker 被排到了降载外层/内层之外），或包装层被别的层顶替。")
                .isEqualTo(com.rag.common.protection.GoStyleRpcBreakerInterceptor.class);
    }

    @Test
    @DisplayName("链上真的有 **降载（shedding）** 包装层（不关降载 ⇒ 业务 interceptor 拿到的是降载层包出来的 call）")
    void sheddingLayerWrapsTheBusinessInterceptor() {
        ServerInterceptor interceptor = mock(ServerInterceptor.class);

        // ⚠️ 本用例**不关降载** —— 它要证明的就是降载层在链上。
        GoStyleProtectionProperties props = new GoStyleProtectionProperties();
        props.getCpuShedding().setEnabled(true);
        // ⚠️ 阈值抬到 999（Go 的 tag 声明域 [0:1000) 的上界）⇒ 即使整机 CPU 打满也不触发降载
        //    ⇒ 断言只依赖「降载层在不在链上」，**不依赖跑测试那一刻的机器负载**（确定性）。
        props.getCpuShedding().setCpuThreshold(999L);

        ChainRun run = runChainAndCaptureBusinessCall(interceptor, props);

        assertThat(run.businessCall())
                .as("链上有降载 ⇒ 业务 interceptor 拿到的 call 不是原始 call。")
                .isNotSameAs(run.rawCall());

        assertThat(run.businessCall().getClass().getEnclosingClass())
                .as("降载开着时，**最内层**包住业务 call 的应当是降载层"
                        + "（Go 的链序 zrpc/server.go:144-167：Breaker → Shedding → handler ⇒ "
                        + "降载在熔断**内层**）；若这一层归属不是 com.rag.common.protection.GoStyleRpcSheddingInterceptor，"
                        + "说明降载层没挂上，或链的层次被排错了。")
                .isEqualTo(com.rag.common.protection.GoStyleRpcSheddingInterceptor.class);
    }

    /**
     * 跑一次链的结果：<b>原始 call</b>（交给链的那个）与<b>业务 interceptor 拿到的 call</b>（链包过的那个）。
     *
     * @param rawCall      交给链的原始 call
     * @param businessCall 业务 interceptor 实际拿到的 call
     */
    private record ChainRun(ServerCall<Object, Object> rawCall,
                            ServerCall<Object, Object> businessCall) {
    }

    /**
     * 只把本组件<b>真的交给 {@code ServerBuilder}</b> 的那条拦截器<b>抓出来</b>（不跑调用）。
     *
     * <p>⇒ 这是「层名观测」的入口：共享层的 {@code layerNames(chain)} 需要一个<b>已构造出的链</b>，
     * 而本组件的链是在 {@code rpcInterceptorConfigurer} 里造的 ⇒ ⇒ 必须经由
     * {@code GrpcServerConfigurer} 这条真实路径取 ⇒ ⇒ **不能自己 new 一条链**（否则验的不是本组件的装配）。
     */
    private static ServerInterceptor captureChainedInterceptor(
            ServerInterceptor business, GoStyleProtectionProperties props) {

        GrpcServerConfigurer configurer =
                new MessageRpcApplication().rpcInterceptorConfigurer(business, props);

        ServerBuilder<?> builder = mock(ServerBuilder.class);
        ArgumentCaptor<ServerInterceptor> captor = ArgumentCaptor.forClass(ServerInterceptor.class);

        configurer.accept(builder);

        verify(builder).intercept(captor.capture());
        return captor.getValue();
    }

    /**
     * 把本组件交给 {@code ServerBuilder} 的那条链<b>真跑一遍</b>，返回<b>业务 interceptor 拿到的那个 call</b>。
     *
     * <p>⚠️⚠️ 三处打桩缺一不可，任何一处漏掉都会让断言变成「假绿」：
     * <ol>
     *   <li><b>{@code getMethodDescriptor()}</b>：链上的 breaker 用它取隔离 key
     *       （Go: {@code breakerName := info.FullMethod}）⇒ Mockito 默认返回 null ⇒ 链里抛 NPE，
     *       而那个 NPE 会被<b>最外层的 recover 静默兜住</b>（关掉调用、返回 INTERNAL），
     *       现象是「业务 interceptor 零交互」—— <b>看起来像「链没生效」，真因却在链里面</b>；</li>
     *   <li><b>{@code startCall(...)} 的返回值</b>：Mockito 对返回类型 {@code ServerCall.Listener} 的
     *       默认答案是 null，而 {@code SimpleForwardingServerCallListener} 的构造器
     *       {@code checkNotNull(delegate)} ⇒ 不打桩会 NPE；</li>
     *   <li><b>业务 interceptor 的 {@code interceptCall(...)} 返回值</b>：同上，不打桩会 NPE。</li>
     * </ol>
     */
    private static ChainRun runChainAndCaptureBusinessCall(
            ServerInterceptor business, GoStyleProtectionProperties props) {

        ServerInterceptor chained = captureChainedInterceptor(business, props);

        // 真跑一遍被交给 builder 的那个拦截器。
        ServerCall<Object, Object> call = mock(ServerCall.class);
        ServerCallHandler<Object, Object> next = mock(ServerCallHandler.class);
        ServerCall.Listener<Object> listener = new ServerCall.Listener<Object>() {
        };
        doReturn(listener).when(next).startCall(any(), any());

        when(call.getMethodDescriptor()).thenReturn(MethodDescriptor.<Object, Object>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("/message.MessageService/SendNotification")
                .setRequestMarshaller(new NoopMarshaller())
                .setResponseMarshaller(new NoopMarshaller())
                .build());

        chained.interceptCall(call, new Metadata(), next);

        ArgumentCaptor<ServerCall<Object, Object>> callCaptor =
                ArgumentCaptor.forClass(ServerCall.class);
        verify(business).interceptCall(callCaptor.capture(), any(), any());

        return new ChainRun(call, callCaptor.getValue());
    }

    /** 最小可用的 marshaller（测试不真的序列化）。 */
    private static final class NoopMarshaller implements MethodDescriptor.Marshaller<Object> {
        @Override
        public java.io.InputStream stream(Object value) {
            return new java.io.ByteArrayInputStream(new byte[0]);
        }

        @Override
        public Object parse(java.io.InputStream stream) {
            return new Object();
        }
    }
}
