package com.rag.common.protection;

import com.rag.common.observability.GoStylePrometheusInterceptor;
import com.rag.common.observability.GoStyleRecoverInterceptor;
import com.rag.common.observability.GoStyleStatInterceptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Metadata;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>gRPC 服务端拦截器链的唯一顺序定义点</b>。
 *
 * <h3>Go 出处（消费点，不是声明点）</h3>
 * <pre>
 *   zrpc/server.go:144-163 setupUnaryInterceptors —— 按顺序 AddUnaryInterceptors：
 *       Trace → Recover → Stat → Prometheus → Breaker → Shedding → Timeout
 *   zrpc/internal/server.go:58 —— AddUnaryInterceptors 是 **append 到尾部**
 *       ⇒ grpc-go 的 ChainUnaryInterceptor 里「**先注册的最外层**」
 *       ⇒ 最终执行序（由外到内）：Trace → Recover → Stat → Prometheus
 *                                  → Breaker → Shedding → Timeout → handler
 * </pre>
 *
 * <h3>为什么必须显式手工串，而不是多次 {@code server.intercept(a, b)}</h3>
 * <p>{@code io.grpc.ServerBuilder#intercept} <b>每次只收一个</b>，而多次调用的执行顺序是
 * 「<b>added in reverse order</b>」—— 把嵌套关系押在一个容易记错的 API 细节上，
 * 等于把熔断器的正确性交给记忆。
 * <p>⇒ 本类用<b>代码结构</b>把顺序写死：<b>{@code build(business)} 返回的那条链，
 * recover 在最外层、breaker 次之、shedding 再次之、business 最内层。</b>
 *
 * <h3>⚠️ 各层的职责分工</h3>
 * <table border="1">
 *   <caption>本仓的链 vs Go 的链</caption>
 *   <tr><th>位置</th><th>本仓</th><th>Go</th><th>说明</th></tr>
 *   <tr><td>最外</td><td>{@code GoStyleRecoverInterceptor}</td><td>Recover</td>
 *       <td>panic 兜底。必须在最外层，否则内层的 panic 兜不住</td></tr>
 *   <tr><td>次外</td><td>{@link GoStyleRpcBreakerInterceptor}</td><td>Breaker</td>
 *       <td>必须<b>在 shedding 外层</b>：Go 侧 shedding 的 RESOURCE_EXHAUSTED 会冒泡上来被它记成失败</td></tr>
 *   <tr><td>再次</td><td>{@link GoStyleRpcSheddingInterceptor}</td><td>Shedding</td>
 *       <td>CPU 自适应降载</td></tr>
 *   <tr><td>最内</td><td>业务观测拦截器（超时 / trace）</td><td>Timeout / observability</td>
 *       <td>本仓把超时与观测合在一个拦截器里</td></tr>
 * </table>
 *
 * <h3>⚠️ 用法</h3>
 * <pre>
 *   // 各 rpc 组件的 GrpcServerConfigurer 里：
 *   return server -&gt; server.intercept(
 *           GoStyleRpcProtectionChain.build(rpcUnaryServerInterceptor, props));
 * </pre>
 *
 * <h3>🟢 链构成的<b>只读</b>查询入口（G2 反馈单 FB-03）</h3>
 * <p>链上有<b>两个条件分支</b>（降载要 {@code enabled && maxCpuPermille>0}、熔断要 {@code enabled}）
 * ⇒ ⇒ 链的<b>构成</b>是<b>配置相关</b>的，不是固定的。
 * <p>⇒ 域级守门若想分别断言「breaker 在链上」「shedding 在链上」，就需要一个<b>能看见构成</b>的入口；
 * 否则只能靠「关掉降载 ⇒ 链变得确定」来间接判断 ⇒ ⇒ <b>断言被迫退化</b>
 * （名义上写 breaker/shedding，实际只证明了 breaker）。
 * <p>⇒ 本类提供两个<b>只读</b>入口，<b>零行为改动</b>：
 * <ul>
 *   <li>{@link #compose(GoStyleProtectionProperties)} —— 由配置推出的<b>构成</b>
 *       （<b>唯一计算点</b>：{@link #build} 内部用的就是它 ⇒ 二者不可能不一致）；</li>
 *   <li>{@link #layerNames(ServerInterceptor)} —— 对<b>已构造出的链</b>的观测
 *       （靠 {@link Layered} 标记逐层读出层名）。</li>
 * </ul>
 */
public final class GoStyleRpcProtectionChain {

    private GoStyleRpcProtectionChain() {
    }

    /**
     * 链上的一层：<b>可被只读查询入口观测</b>。
     *
     * <p>⚠️ 之所以让标记接口<b>继承</b> {@link ServerInterceptor}（而不是另立一个）
     * ⇒ 是因为匿名类<b>不能</b>额外实现别的接口
     * （{@code new ServerInterceptor() implements Xxx} 是<b>非法语法</b>）
     * ⇒ ⇒ 只能「实例化这个接口」来同时拿到「是拦截器」+「带层名」。
     */
    public interface Layered extends ServerInterceptor {

        /** 本层的名字（{@code recover} / {@code breaker} / {@code shedding}）。 */
        String layerName();

        /** 本层包裹的<b>内层</b>拦截器（供 {@link #layerNames(ServerInterceptor)} 逐层下行）。 */
        ServerInterceptor inner();

        /**
         * 本层是否计入 {@link #layerNames(ServerInterceptor)} 的<b>名单</b>。
         *
         * <p>默认 {@code true}（保护层：recover / breaker / shedding）。
         *
         * <p>🔴 <b>为什么需要这个开关</b>（2026-10-30 · G4）：Go 的链是
         * {@code Trace → Recover → Stat → Prometheus → Breaker → Shedding → Timeout}
         * ⇒ 本仓补上 <b>Stat</b> / <b>Prometheus</b> 两个<b>观测层</b>后，
         * 链上多了两层 ⇒ 若它们也进名单，{@code layerNames} 的输出就会从
         * {@code [recover, breaker, shedding]} 变成 5 项
         * ⇒ ⇒ <b>8 个域级守门</b>（{@code *ProtectionChainWiringTest}）里
         * 「层名列表 == [recover, breaker, shedding]」与
         * 「层名列表 == compose(props).layerNames()」两条断言会<b>整体变红</b>
         * ⇒ ⇒ 而那些守门在<b>域侧写权面</b>（本层不能改）⇒ ⇒ 只能让观测层<b>不进名单</b>。
         *
         * <p>⇒ <b>名单的语义</b>因此收窄为「<b>保护层</b>的层名」；
         * 观测层确实在链上 ⇒ 用 {@link #allLayerNames(ServerInterceptor)} 看<b>全链</b>。
         */
        default boolean countedInLayerNames() {
            return true;
        }
    }

    /**
     * 链的<b>构成</b>（只读值对象）。
     *
     * @param recover  是否装 recover 层（本仓恒为 {@code true}：Go 侧 {@code Middlewares.Recover} 默认 true）
     * @param breaker  是否装 breaker 层
     * @param shedding 是否装 shedding 层
     */
    public record Composition(boolean recover, boolean breaker, boolean shedding) {

        /** 本构成对应的层名（由外到内）。 */
        public List<String> layerNames() {
            List<String> names = new ArrayList<>(3);
            if (recover) {
                names.add("recover");
            }
            if (breaker) {
                names.add("breaker");
            }
            if (shedding) {
                names.add("shedding");
            }
            return List.copyOf(names);
        }
    }

    /**
     * 由配置推出链的<b>构成</b>（<b>唯一计算点</b>）。
     *
     * <p>⚠️ {@link #build} 内部调用的就是本方法 ⇒ ⇒ 「配置推出的构成」与「实际构造出的链」
     * <b>不可能不一致</b>（这是把判据放在共享层、而不是让每个域各抄一遍条件的原因）。
     *
     * <p>Go 出处（两个条件分支各自有出处）：
     * <pre>
     *   zrpc/server.go:157-159   Breaker  —— Middlewares.Breaker 默认 true
     *   zrpc/server.go:160-163   Shedding —— CpuThreshold &gt; 0 才挂
     * </pre>
     */
    public static Composition compose(GoStyleProtectionProperties props) {
        boolean shedding = props.getCpuShedding().isEnabled() && props.maxCpuPermille() > 0;
        boolean breaker = props.getBreaker().isEnabled();
        return new Composition(true, breaker, shedding);
    }

    /**
     * 观测<b>已构造出的链</b>的层名（由外到内）。
     *
     * <p>⚠️ 只读：<b>不改链、不改配置</b> ⇒ 零行为改动。
     * <p>⇒ 域级守门可以据此<b>分别</b>断言 breaker 与 shedding 两层，
     * 而不必「关掉降载来换取确定性」。
     *
     * @param chain {@link #build} 产出的链
     * @return 层名列表（由外到内；最内的业务拦截器不是 {@link Layered} ⇒ 不在列表里）
     */
    public static List<String> layerNames(ServerInterceptor chain) {
        List<String> names = new ArrayList<>(3);
        ServerInterceptor current = chain;
        while (current instanceof Layered layered) {
            if (layered.countedInLayerNames()) {
                names.add(layered.layerName());
            }
            current = layered.inner();
        }
        return List.copyOf(names);
    }

    /**
     * 观测<b>已构造出的链</b>的<b>全部</b>层名（由外到内，<b>含观测层</b>）。
     *
     * <p>⚠️ 与 {@link #layerNames(ServerInterceptor)} 的区别：那个方法只报<b>保护层</b>
     * （recover / breaker / shedding）⇒ 它的输出与 {@link Composition#layerNames()} 恒等
     * ⇒ 域级守门依赖这条恒等式；而本方法报<b>全链</b>（含 stat / prometheus 两个观测层）。
     *
     * <p>Go 对照：{@code zrpc/server.go:144-167} 的注册序
     * {@code Trace → Recover → Stat → Prometheus → Breaker → Shedding → Timeout}
     * ⇒ 本仓对应 {@code [recover, stat, prometheus, breaker, shedding]}（本仓无 Trace 层）。
     */
    public static List<String> allLayerNames(ServerInterceptor chain) {
        List<String> names = new ArrayList<>(5);
        ServerInterceptor current = chain;
        while (current instanceof Layered layered) {
            names.add(layered.layerName());
            current = layered.inner();
        }
        return List.copyOf(names);
    }

    /**
     * 组装完整的服务端拦截器链：<b>recover → breaker → shedding → business</b>。
     *
     * @param business 业务观测拦截器（超时 / trace），位于<b>最内层</b>
     * @param props    保护层配置（breaker / cpuShedding 的开关与阈值）
     */
    public static ServerInterceptor build(ServerInterceptor business, GoStyleProtectionProperties props) {
        // ⚠️ 构成由 compose() **唯一计算** ⇒ 本方法与 compose() 不可能给出两套条件
        Composition composition = compose(props);

        ServerInterceptor chain = business;

        // ③ Shedding（Go: zrpc/server.go:160-162，CpuThreshold > 0 才挂）
        if (composition.shedding()) {
            GoStyleAdaptiveShedder shedder = new GoStyleAdaptiveShedder(
                    props.maxCpuPermille(), props.getCpuShedding().getCpuSource());
            chain = sheddingThen(chain, shedder);
        }

        // ④ Breaker（Go: zrpc/server.go:157-159，Middlewares.Breaker 默认 true）
        if (composition.breaker()) {
            chain = breakerThen(chain);
        }

        // ③ Prometheus（Go: zrpc/server.go:154-156，Middlewares.Prometheus 默认 true）
        //    🔴 **位置**：在 breaker / shedding 的**外层** ⇒ ⇒ 被降载 / 熔断拒绝的请求
        //    **也会被 Prometheus 层记一笔**（Go 侧同理：shedding 层是「返回 error」，
        //    Prometheus 层的后置代码照样执行 ⇒ 记到 code=8 / code=14）。
        //    ⇒ 若把它装到 breaker / shedding **内侧**，这两类请求在指标里会**整个消失**。
        if (props.getPrometheus().isEnabled()) {
            chain = prometheusThen(chain);
        }

        // ② Stat（Go: zrpc/server.go:151-153，Middlewares.Stat 默认 true）
        //    🔴 **位置**：在 Prometheus 的外层、breaker / shedding 的外层
        //    ⇒ ⇒ 量到的耗时**含**降载 / 熔断开销（Go 的 Stat 层同样在外面）。
        if (props.getStat().isEnabled()) {
            chain = statThen(chain);
        }

        // ① Recover（Go: zrpc/server.go:148-150，Middlewares.Recover 默认 true）—— **最外层**
        return recoverThen(chain);
    }

    /**
     * 把 {@code stat} 串在 {@code inner} 的<b>外层</b>。
     *
     * <p>Go 对应：{@code zrpc/server.go:151-153} 的 {@code UnaryStatInterceptor}，
     * 位于 {@code Recover} 之内、{@code Prometheus} 之外。
     */
    public static ServerInterceptor statThen(ServerInterceptor inner) {
        return statThen(new GoStyleStatInterceptor(), inner);
    }

    /** 测试注入用（让断言能拿到具体的 stat 实例与聚合器）。 */
    static ServerInterceptor statThen(GoStyleStatInterceptor stat, ServerInterceptor inner) {
        return new Layered() {
            @Override
            public String layerName() {
                return "stat";
            }

            @Override
            public ServerInterceptor inner() {
                return inner;
            }

            @Override
            public boolean countedInLayerNames() {
                // ⚠️ 观测层**不计入**保护层的名单（见 {@link Layered#countedInLayerNames()} 的说明）。
                return false;
            }

            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                    ServerCall<ReqT, RespT> call,
                    Metadata headers,
                    ServerCallHandler<ReqT, RespT> next) {
                ServerCallHandler<ReqT, RespT> delegateToInner =
                        new ServerCallHandler<ReqT, RespT>() {
                            @Override
                            public ServerCall.Listener<ReqT> startCall(
                                    ServerCall<ReqT, RespT> c, Metadata h) {
                                return inner.interceptCall(c, h, next);
                            }
                        };
                return stat.interceptCall(call, headers, delegateToInner);
            }
        };
    }

    /**
     * 把 {@code prometheus} 串在 {@code inner} 的<b>外层</b>。
     *
     * <p>Go 对应：{@code zrpc/server.go:154-156} 的 {@code UnaryPrometheusInterceptor}，
     * 位于 {@code Stat} 之内、{@code Breaker} 之外。
     */
    public static ServerInterceptor prometheusThen(ServerInterceptor inner) {
        final GoStylePrometheusInterceptor prometheus = new GoStylePrometheusInterceptor();
        return new Layered() {
            @Override
            public String layerName() {
                return "prometheus";
            }

            @Override
            public ServerInterceptor inner() {
                return inner;
            }

            @Override
            public boolean countedInLayerNames() {
                // ⚠️ 观测层**不计入**保护层的名单（见 {@link Layered#countedInLayerNames()} 的说明）。
                return false;
            }

            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                    ServerCall<ReqT, RespT> call,
                    Metadata headers,
                    ServerCallHandler<ReqT, RespT> next) {
                ServerCallHandler<ReqT, RespT> delegateToInner =
                        new ServerCallHandler<ReqT, RespT>() {
                            @Override
                            public ServerCall.Listener<ReqT> startCall(
                                    ServerCall<ReqT, RespT> c, Metadata h) {
                                return inner.interceptCall(c, h, next);
                            }
                        };
                return prometheus.interceptCall(call, headers, delegateToInner);
            }
        };
    }

    /**
     * 把 {@code recover} 串在 {@code inner} 的<b>外层</b>。
     *
     * <p>⚠️ 用匿名类而不是 lambda：{@code ServerInterceptor#interceptCall} 与
     * {@code ServerCallHandler#startCall} <b>各自是泛型方法</b>，
     * 三层 lambda 嵌套时 javac 推断不出函数描述符（实测报「函数描述符无效」）。
     */
    public static ServerInterceptor recoverThen(ServerInterceptor inner) {
        final ServerInterceptor outer = new GoStyleRecoverInterceptor();
        return new Layered() {
            @Override
            public String layerName() {
                return "recover";
            }

            @Override
            public ServerInterceptor inner() {
                return inner;
            }

            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                    ServerCall<ReqT, RespT> call,
                    Metadata headers,
                    ServerCallHandler<ReqT, RespT> next) {
                // ⚠️ ServerCallHandler 的类型参数在**接口声明处**（ServerCallHandler<ReqT, RespT>），
                //    不是方法级泛型 —— 这里若再写一次 <Q, S> 会与父类型「同名但互不覆盖」而编译失败。
                ServerCallHandler<ReqT, RespT> delegateToInner =
                        new ServerCallHandler<ReqT, RespT>() {
                            @Override
                            public ServerCall.Listener<ReqT> startCall(
                                    ServerCall<ReqT, RespT> c, Metadata h) {
                                return inner.interceptCall(c, h, next);
                            }
                        };
                return outer.interceptCall(call, headers, delegateToInner);
            }
        };
    }

    /** 把 {@code breaker} 串在 {@code inner} 的<b>外层</b>。 */
    public static ServerInterceptor breakerThen(ServerInterceptor inner) {
        return breakerThen(new GoStyleRpcBreakerInterceptor(true), inner);
    }

    /**
     * 把指定的 {@code breaker} 串在 {@code inner} 的<b>外层</b>（测试注入用）。
     *
     * <p>⚠️ 测试必须能拿到<b>具体的 breaker 实例</b>才能断言「失败被记进去了」，
     * 而 {@link GoStyleRpcBreakerInterceptor#breakerFor(String)} 是包私有的 ⇒ 需要这条重载把实例暴露出来。
     */
    static ServerInterceptor breakerThen(GoStyleRpcBreakerInterceptor breaker, ServerInterceptor inner) {
        return new Layered() {
            @Override
            public String layerName() {
                return "breaker";
            }

            @Override
            public ServerInterceptor inner() {
                return inner;
            }

            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                    ServerCall<ReqT, RespT> call,
                    Metadata headers,
                    ServerCallHandler<ReqT, RespT> next) {
                // ⚠️⚠️ **这里必须调 inner.interceptCall(...)，不能把 next 直接透传**。
                //    透传 next ⇒ inner（业务拦截器）**永远不会被调用** ⇒
                //    超时 / deadline 传播 / gRPC span 全部静默消失，
                //    而链上其它环节一切正常、**不报任何错**（§3.3 的经典形态）。
                //    （本轮第一版就是这么写的，被 GoStyleRpcProtectionChainTest 当场抓到。）
                ServerCallHandler<ReqT, RespT> delegateToInner =
                        new ServerCallHandler<ReqT, RespT>() {
                            @Override
                            public ServerCall.Listener<ReqT> startCall(
                                    ServerCall<ReqT, RespT> c, Metadata h) {
                                return inner.interceptCall(c, h, next);
                            }
                        };
                return breaker.interceptCall(call, headers, delegateToInner);
            }
        };
    }

    /** 把 {@code shedding} 串在 {@code inner} 的<b>外层</b>。 */
    public static ServerInterceptor sheddingThen(ServerInterceptor inner, GoStyleAdaptiveShedder shedder) {
        return sheddingThen(new GoStyleRpcSheddingInterceptor(shedder, true), inner);
    }

    /** 把指定的 {@code shedding} 串在 {@code inner} 的<b>外层</b>（测试注入用）。 */
    static ServerInterceptor sheddingThen(GoStyleRpcSheddingInterceptor shedding, ServerInterceptor inner) {
        return new Layered() {
            @Override
            public String layerName() {
                return "shedding";
            }

            @Override
            public ServerInterceptor inner() {
                return inner;
            }

            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                    ServerCall<ReqT, RespT> call,
                    Metadata headers,
                    ServerCallHandler<ReqT, RespT> next) {
                // ⚠️ 同 breakerThen：必须调 inner.interceptCall(...)，不能透传 next
                ServerCallHandler<ReqT, RespT> delegateToInner =
                        new ServerCallHandler<ReqT, RespT>() {
                            @Override
                            public ServerCall.Listener<ReqT> startCall(
                                    ServerCall<ReqT, RespT> c, Metadata h) {
                                return inner.interceptCall(c, h, next);
                            }
                        };
                return shedding.interceptCall(call, headers, delegateToInner);
            }
        };
    }
}
