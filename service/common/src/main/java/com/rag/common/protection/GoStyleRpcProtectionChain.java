package com.rag.common.protection;

import com.rag.common.observability.GoStyleRecoverInterceptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Metadata;

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
 */
public final class GoStyleRpcProtectionChain {

    private GoStyleRpcProtectionChain() {
    }

    /**
     * 组装完整的服务端拦截器链：<b>recover → breaker → shedding → business</b>。
     *
     * @param business 业务观测拦截器（超时 / trace），位于<b>最内层</b>
     * @param props    保护层配置（breaker / cpuShedding 的开关与阈值）
     */
    public static ServerInterceptor build(ServerInterceptor business, GoStyleProtectionProperties props) {
        ServerInterceptor chain = business;

        // ③ Shedding（Go: zrpc/server.go:160-162，CpuThreshold > 0 才挂）
        if (props.getCpuShedding().isEnabled() && props.maxCpuPermille() > 0) {
            GoStyleAdaptiveShedder shedder = new GoStyleAdaptiveShedder(
                    props.maxCpuPermille(), props.getCpuShedding().getCpuSource());
            chain = sheddingThen(chain, shedder);
        }

        // ② Breaker（Go: zrpc/server.go:157-159，Middlewares.Breaker 默认 true）
        if (props.getBreaker().isEnabled()) {
            chain = breakerThen(chain);
        }

        // ① Recover（Go: zrpc/server.go:131-133，Middlewares.Recover 默认 true）—— **最外层**
        return recoverThen(chain);
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
        return new ServerInterceptor() {
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
        return new ServerInterceptor() {
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
        return new ServerInterceptor() {
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
