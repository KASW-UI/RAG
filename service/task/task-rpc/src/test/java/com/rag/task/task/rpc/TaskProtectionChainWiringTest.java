package com.rag.task.task.rpc;

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
 * （降载层的包装由 {@link com.rag.common.protection.GoStyleRpcSheddingInterceptor} 创建、熔断层的由
 * {@link com.rag.common.protection.GoStyleRpcBreakerInterceptor} 创建）⇒ 无时间窗口、无共享状态、无假红可能。
 *
 * <h3>✅ 合并后已升级为更强判据（2026-10-27 · 合并 G4 `align/g4-service-common` @ {@code 37816ca}）</h3>
 * <p>共享层的「链层构成」只读入口（FB-03 · G4 提交 {@code 182fc32}）已随合并到位：
 * {@code GoStyleRpcProtectionChain.compose(props)} 与
 * {@code GoStyleRpcProtectionChain.layerNames(chain)}
 * ⇒ ⇒ 本类新增两条用例，<b>直接</b>断言层名列表（{@code [recover, breaker, shedding]}）。
 * <p><b>为什么这两条比旧判据更强</b>（旧判据保留，不删）：
 * <ul>
 *   <li>旧判据「包装层归属」只能证明<b>某一层确实包住了业务 call</b> ⇒ 它<b>证不出层序</b>
 *       （recover 在 breaker 外还是内，旧判据完全无感）；</li>
 *   <li>新判据 {@code layerNames(chain)} <b>由外到内</b>列出真实装上的层 ⇒ ⇒
 *       <b>一次同时钉住「有 breaker」「有 shedding」「顺序是 recover→breaker→shedding」</b>
 *       ⇒ 与 Go 侧 {@code zrpc/server.go:144-167} 的注册序逐项对齐；</li>
 *   <li>再加一条「关掉降载 ⇒ 层名列表变成 {@code [recover, breaker]}」⇒ ⇒
 *       证明<b>降载层是条件装的</b>（不是恒在）⇒ ⇒ 旧判据要靠「关掉降载来换取确定性」，
 *       新判据<b>不必关</b> ⇒ ⇒ 旧判据降级为「包装语义」证据，新判据承担「构成 + 顺序」证据，
 *       两者<b>互补而非替代</b>。</li>
 * </ul>
 * <p><b>第三条断言（一致性）</b>：{@code layerNames(chain)} 必须 <b>等于</b> {@code compose(props).layerNames()}
 * ⇒ ⇒ 证明「<b>实际装到 server 上的那条链</b>」与「<b>配置推出的构成</b>」<b>逐项一致</b>
 * ⇒ ⇒ 这一条是旧判据完全覆盖不到的：它能抓住「配置说三层、实际只装两层」这类
 * <b>装配与配置脱节</b>的漂移。
 * <p>⚠️ <b>三层断言合起来才是「A2 链序对齐」的完整判据</b>；任何单条都不充分
 * （这与本域 Redis 命令超时守门「机制位①/② 单独都恒真」是同一条纪律）。
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
                        + "（Go: zrpc/server.go:144-167 的注册序 = Trace → Recover → Stat → Prometheus"
                        + " → Breaker → Shedding ⇒ 本仓的链 = recover → breaker → shedding → 业务观测拦截器）。"
                        + "若少了 breaker 或 shedding ⇒ 该组件的熔断 / 降载形同虚设，且**不报任何错**。")
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
                new TaskRpcApplication().rpcInterceptorConfigurer(business, props);

        ServerBuilder<?> builder = mock(ServerBuilder.class);
        ArgumentCaptor<ServerInterceptor> captor = ArgumentCaptor.forClass(ServerInterceptor.class);

        configurer.accept(builder);

        verify(builder).intercept(captor.capture());
        return captor.getValue();
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

        ServerInterceptor chained = captureChainedInterceptor(business, props);

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
