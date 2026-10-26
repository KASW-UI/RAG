package com.rag.task.task.rpc;

import com.rag.common.protection.GoStyleProtectionProperties;
import com.rag.common.protection.GoStyleRpcBreakerInterceptor;
import com.rag.common.protection.GoStyleRpcSheddingInterceptor;
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
 * <b>DRIFT-MSG-V-026 的逐组件守门</b>：熔断 / 降载链必须<b>真的</b>挂到本组件的 gRPC server 上。
 *
 * <p><b>为什么必须是「组件级」而不是「一条共用测试」</b>（AGENTS.md §3.5）：
 * 熔断 / 降载在 Go 侧是 <b>zrpc 框架级</b>能力，每个 rpc server 都自带；
 * 本仓把链的定义点放在共享模块 {@code GoStyleRpcProtectionChain}，
 * 但<b>每个组件仍然要自己把链交给 ServerBuilder</b> ——
 * 漏掉任何个组件，那个组件就<b>既没有 recover、也没有熔断、也没有降载</b>，
 * 而<b>不报任何错</b>（这正是 §3.3 的经典形态）。
 *
 * <p><b>本测试断言三件事</b>：
 * <ol>
 *   <li>{@code @Import} 列表含 {@code GoStyleProtectionConfiguration}
 *       ⇒ {@code GoStyleProtectionProperties} Bean 真的存在
 *       （它是 {@code @ConfigurationProperties} 类，<b>不会</b>被组件扫描自动注册，
 *       缺了它 {@code rpcInterceptorConfigurer} 根本注入不到参数 ⇒ 上下文起不来）；</li>
 *   <li><b>熔断层</b>在链上：<b>关掉降载</b>后，业务 interceptor 拿到的 {@code call}
 *       <b>不是</b>原始 call ⇒ 链上<b>至少</b>有一层包装；又因为此刻降载已关，
 *       唯一可能包住业务 call 的就是<b>熔断层</b> ⇒ 断到「熔断在链上」；</li>
 *   <li><b>降载层</b>在链上：<b>不关降载</b>时，业务 interceptor 拿到的 {@code call}
 *       <b>就是降载层包出来的那一个</b>（包装层的归属可直接判定）⇒ 断到「降载在链上」。</li>
 * </ol>
 *
 * <p>⚠️ <b>为什么断言不能是「同一个对象」</b>：
 * 链上有包装层时，业务 interceptor 收到的是<b>包装后的 call</b> ⇒ {@code same(call)} 必然不成立。
 * ⇒ 断言改成「业务 interceptor <b>确实被调到</b>」+「拿到的 call <b>不是</b>原始 call」——
 * 这比「同一个对象」<b>更强</b>：它能区分「业务 interceptor 在链里」与「链被拼反 / 没挂上」。
 *
 * <h2>⚠️ 2026-10-27 订正记录（本轮 D-2）</h2>
 * <p><b>原 DisplayName 写「链上真的有 breaker/shedding 包装层」，但原用例把降载关掉了</b>
 * （{@code props.getCpuShedding().setEnabled(false)}）⇒ <b>shedding 从未在链上</b>，
 * 那条用例<b>只</b>证明了 breaker ⇒ <b>DisplayName 与注释是假断言</b>（AGENTS.md §3.3.2）。
 * <ul>
 *   <li>⇒ 原用例的 DisplayName / 注释已订正为「断言<b>熔断</b>在链上」（并把判据从
 *       「拿到的是包装过的 call」加强为「拿到的 call <b>归熔断层所有</b>」）；</li>
 *   <li>⇒ 另立一条用例证明<b>降载</b>层也在链上（本类的第二条测试）。</li>
 * </ul>
 *
 * <h3>⚠️ 判据为什么是「包装层归属」而不是「降载统计的增量」</h3>
 * <p>共享层的降载统计 {@code GoStyleSheddingStat} 是<b>进程级单例</b>，
 * 且每分钟上报一次会把三个计数<b>清零</b>（Go: {@code core/load/sheddingstat.go:52-70} 的 {@code reset()}）
 * ⇒ 用「增量 +1」当判据，会在「上报清零恰好落在测量窗口内」时**假红**（概率 ~1e-5/次）
 * ⇒ <b>守门不能带随机性</b>。
 * <p>⇒ 改用<b>确定性</b>判据：链上每层都用 {@code ForwardingServerCall} 包住内层的 call，
 * 而<b>包装层由哪一层创建，是可以从包装对象的归属类直接判定的</b>
 * （降载层的包装由 {@link GoStyleRpcSheddingInterceptor} 创建、熔断层的由
 * {@link GoStyleRpcBreakerInterceptor} 创建）⇒ 无时间窗口、无共享状态、无假红可能。
 *
 * <h3>⚠️ 合并后可以升级为更强判据（本轮<b>不能</b>用）</h3>
 * <p>共享层已落码「链层构成」只读入口（FB-03 · G4 提交 {@code 182fc32}）：
 * {@code GoStyleRpcProtectionChain.compose(props)} 与
 * {@code GoStyleRpcProtectionChain.layerNames(chain)}
 * ⇒ 合并后本类可改用它们，**直接**断言层名列表（{@code [recover, breaker, shedding]}）。
 * <p><b>本轮不用它的原因</b>：该入口在 G4 的分支上，<b>尚未合入本波的工作树</b>
 * ⇒ 现在引用它 ⇒ 本模块<b>编译不过</b> ⇒ 本波提交会直接红。
 * ⇒ 本轮先用「包装层归属」这个等价且确定性的判据顶上，合并后按 §5.3e 重跑收口回归时再升级。
 */
@DisplayName("DRIFT-MSG-V-026 TaskRpcApplication：熔断/降载链必须真的挂到 gRPC server 上")
class TaskProtectionChainWiringTest {

    @Test
    @DisplayName("Application 必须 @Import GoStyleProtectionConfiguration（否则 properties Bean 不存在）")
    void applicationImportsProtectionConfiguration() {
        var importAnnotation = TaskRpcApplication.class
                .getAnnotation(org.springframework.context.annotation.Import.class);
        assertThat(importAnnotation)
                .as("TaskRpcApplication 必须有 @Import；没有它 = common 的 Bean 一个都不装配")
                .isNotNull();

        assertThat(java.util.Arrays.asList(importAnnotation.value()))
                .as("@Import 列表必须含 GoStyleProtectionConfiguration —— "
                        + "它是 GoStyleProtectionProperties（breaker / cpuShedding 开关与阈值）的唯一注册点，"
                        + "漏掉它 = 上下文起不来（缺 Bean），不是「保护层静默关闭」")
                .contains(com.rag.common.protection.GoStyleProtectionConfiguration.class);
    }

    @Test
    @DisplayName("链上真的有 **breaker** 包装层（降载已关 ⇒ 唯一可能包住业务 call 的就是 breaker）")
    void breakerLayerWrapsTheBusinessInterceptor() {
        ServerInterceptor interceptor = mock(ServerInterceptor.class);

        // ⚠️ 关掉 CPU 降载：**本用例只断言熔断层**。
        //    关掉降载之后，链上唯一可能包住业务 call 的就是 breaker ⇒
        //    「拿到的不是原始 call」这一条**恰好**等价于「breaker 在链上」。
        //    （降载层由另一条用例单独断言 —— 两条合起来才覆盖 Go 的两层。）
        //    降载本身由 service/common 的 GoStyleRpcSheddingInterceptorTest 单独覆盖。
        GoStyleProtectionProperties props = new GoStyleProtectionProperties();
        props.getCpuShedding().setEnabled(false);

        ChainRun run = runChainAndCaptureBusinessCall(interceptor, props);

        assertThat(run.businessCall())
                .as("链上有 breaker ⇒ 业务 interceptor 拿到的必须是包装过的 call。"
                        + "若拿到的是原始 call，说明熔断层没挂上 —— 即 breaker 形同虚设。")
                .isNotSameAs(run.rawCall());

        assertThat(run.businessCall().getClass().getEnclosingClass())
                .as("降载已关 ⇒ 此刻唯一可能包住业务 call 的就是 **breaker 层**；"
                        + "若这一层归属不是 GoStyleRpcBreakerInterceptor，说明链的层次不对"
                        + "（例如 breaker 被排到了降载外层/内层之外），或包装层被别的层顶替。")
                .isEqualTo(GoStyleRpcBreakerInterceptor.class);
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
                        + "降载在熔断**内层**）；若这一层归属不是 GoStyleRpcSheddingInterceptor，"
                        + "说明降载层没挂上，或链的层次被排错了。")
                .isEqualTo(GoStyleRpcSheddingInterceptor.class);
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

        GrpcServerConfigurer configurer =
                new TaskRpcApplication().rpcInterceptorConfigurer(business, props);

        ServerBuilder<?> builder = mock(ServerBuilder.class);
        ArgumentCaptor<ServerInterceptor> captor = ArgumentCaptor.forClass(ServerInterceptor.class);

        configurer.accept(builder);

        verify(builder).intercept(captor.capture());
        ServerInterceptor chained = captor.getValue();

        // 真跑一遍被交给 builder 的那个拦截器。
        ServerCall<Object, Object> call = mock(ServerCall.class);
        ServerCallHandler<Object, Object> next = mock(ServerCallHandler.class);
        ServerCall.Listener<Object> listener = new ServerCall.Listener<Object>() {
        };
        doReturn(listener).when(next).startCall(any(), any());

        when(call.getMethodDescriptor()).thenReturn(MethodDescriptor.<Object, Object>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("/task.TaskService/GetTask")
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
