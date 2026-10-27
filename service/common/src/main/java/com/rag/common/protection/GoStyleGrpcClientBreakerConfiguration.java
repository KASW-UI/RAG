package com.rag.common.protection;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * <b>L4 idiomatization：把 gRPC client 侧熔断拦截器变成「可被 {@code @Import} 引入」的 Bean。</b>
 *
 * <p><b>为什么需要这个类（本类的存在理由）</b>：
 * {@link GoStyleGrpcClientBreakerInterceptor} 原先是 {@code @Component} ⇒ <b>只能靠组件扫描拿到</b>；
 * 而纯 gRPC 进程<b>刻意不扫</b> {@code com.rag.common}（{@link GoStyleProtectionConfiguration}
 * 的类注释写明原因：那一族里有 {@code jakarta.servlet.Filter}，全包扫描会把 web 类拖进 gRPC 进程）
 * ⇒ ⇒ <b>rpc 进程永远拿不到 client breaker</b> ⇒ <b>出向调用无熔断</b>。
 * 而 Go 侧 zrpc 的 client breaker 是<b>默认开</b>的 ⇒ 两侧行为分叉。
 *
 * <h2>Go 出处（三段，逐行核对过）</h2>
 * <pre>
 * // ① go-zero@v1.10.2 zrpc/internal/config.go:14   —— 默认开启
 * Breaker    bool `json:",default=true"`
 *
 * // ② go-zero@v1.10.2 zrpc/internal/client.go:112-113 —— client 侧装配
 * if c.middlewares.Breaker {
 *     interceptors = append(interceptors, clientinterceptors.BreakerInterceptor)
 * }
 *
 * // ③ go-zero@v1.10.2 zrpc/internal/clientinterceptors/breakerinterceptor.go:13-18
 * func BreakerInterceptor(ctx context.Context, method string, req, reply any,
 *     cc *grpc.ClientConn, invoker grpc.UnaryInvoker, opts ...grpc.CallOption) error {
 *     breakerName := path.Join(cc.Target(), method)
 *     return breaker.DoWithAcceptableCtx(ctx, breakerName, func() error {
 *         return invoker(ctx, method, req, reply, cc, opts...)
 *     }, codes.Acceptable)
 * }
 * </pre>
 * <p>⚠️ 真源仓库（{@code Sea-RideTheWind_golang}）<b>不包含</b> {@code zrpc} ——
 * 那是 go-zero 框架自身的包；真源通过 {@code go.mod:17} 锁定 {@code github.com/zeromicro/go-zero v1.10.2}
 * ⇒ 上面的行号对应的是<b>该版本</b>的框架源码。
 * 逐条核实：{@code config.go:14} = {@code Breaker bool `json:",default=true"`}；
 * {@code client.go:112-113} = 上面那两行；{@code breakerinterceptor.go:13-18} = 上面那个函数。
 *
 * <h2>语义等价论证</h2>
 * <ul>
 *   <li><b>装配面</b>：Go 侧每个 zrpc client 都装（②）⇒ RAG 侧每个 rpc 进程都应装
 *       ⇒ 本类让「rpc 进程」与「api 进程」<b>走同一条装配路径</b>
 *       ⇒ 两侧的「client breaker 是否在链上」这个问题<b>不再有域差异</b>。</li>
 *   <li><b>判据面</b>：acceptable 判据在 {@link GoStyleGrpcClientBreakerInterceptor#isAcceptable} 里
 *       逐条对齐 {@code zrpc/internal/codes/accept.go}，<b>本类不改动它</b>。</li>
 *   <li><b>开关面</b>：默认开启由 {@link GoStyleProtectionProperties#getBreaker()} 的
 *       {@code enabled = true} 承载（＝ Go 的 {@code default=true}）⇒ 本类不改默认值。</li>
 * </ul>
 *
 * <h2>为什么落在「共享层」而不是各 rpc 模块</h2>
 * <p>本仓库的 rpc 进程<b>一律走 {@code @Import} 精确引入</b> {@code com.rag.common} 里需要的 Bean
 * （这是 {@link GoStyleProtectionConfiguration} 类注释里写明的纪律：不能整包扫描）。
 * 而 <b>10 个 rpc 组件已经全部 {@code @Import} 了 {@link GoStyleProtectionConfiguration}</b>
 * ⇒ ⇒ 本类只要被它 {@code @Import}，<b>全部 rpc 进程零改动</b>就获得 client breaker。
 * <p>⚠️ 反过来，若改成「让每个 rpc 模块自己加一行 {@code @Import(本类.class)}」⇒ 那是
 * <b>10 个域文件各改一处</b> ⇒ 既落在域侧写权上、又制造 10 个可能漏写的地方 ⇒ 与本仓库
 * 「共享层义务收回包内、下游模块零装配代码」的既有纪律相悖 ⇒ <b>不取</b>。
 *
 * <h2>为什么不用 {@code @ConditionalOnMissingBean} 避重</h2>
 * <p>本类<b>没有</b>用 {@code @ConditionalOnMissingBean(GoStyleGrpcClientBreakerInterceptor.class)}：
 * 该条件只在<b>自动配置类</b>上可靠（Spring 官方 javadoc 明确说它在 {@code @Bean} 方法上
 * 只能匹配「已被处理过」的 bean 定义，而组件扫描产生的 {@code @Component} 此时通常还没注册）
 * ⇒ 用它会得到「api 侧与 rpc 侧各一个」的**重复注册**。
 * <p>⇒ 本仓采用的是<b>单一来源</b>：把 {@code @Component} 从拦截器类上摘掉，
 * <b>只由本类产出</b> ⇒ api 侧（扫到本类）与 rpc 侧（{@code @Import} 到本类）
 * <b>拿到的是同一个定义点</b> ⇒ 结构上不可能重复。
 *
 * <h2>{@code @ConditionalOnClass} 为什么用 {@code name = ...}</h2>
 * <p>本类的 {@code @Bean} 方法返回类型是 {@link GoStyleGrpcClientBreakerInterceptor}，
 * 而那个类<b>直接引用 {@code io.grpc.ClientInterceptor}</b> ⇒ 若 grpc 不在 classpath，
 * <b>加载本类就会失败</b>。
 * ⇒ 条件必须写成<b>字符串形式</b>（{@code name = ...}），让 Spring 用 ASM 读元数据、
 * <b>在加载本类之前</b>就完成判定 ⇒ 条件不成立时本类根本不会被加载。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = {
        "net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor",
        "io.grpc.ManagedChannel"
})
public class GoStyleGrpcClientBreakerConfiguration {

    /**
     * 产出 gRPC client 侧熔断拦截器 Bean。
     *
     * <p><b>为什么用 {@code @Bean} 而不是让拦截器自己带 {@code @Component}</b>：
     * {@code @Component} 只能靠组件扫描拿到 ⇒ 纯 gRPC 进程（不扫 {@code com.rag.common}）拿不到
     * ⇒ 详见类注释的「为什么落在共享层而不是各 rpc 模块」。
     *
     * <p>⚠️ <b>Bean 的存在还不够</b>：devh 的
     * {@code AnnotationGlobalClientInterceptorConfigurer} 用
     * {@code applicationContext.getBeansWithAnnotation(GrpcGlobalClientInterceptor.class)} 收集
     * ⇒ 它认的是<b>类上的注解</b>，不是「是不是 ClientInterceptor」
     * ⇒ {@link GoStyleGrpcClientBreakerInterceptor} 上的 {@code @GrpcGlobalClientInterceptor}
     * <b>必须保留</b>，否则 Bean 在、但 channel factory 不会消费它 ⇒ 熔断一次都不执行。
     * ⇒ 锁定用例：{@code GoStyleGrpcClientBreakerRpcAssemblyTest}（真 Spring 上下文，
     * 断言它在 starter 自己的 {@code GlobalClientInterceptorRegistry} 里）。
     */
    @Bean
    public GoStyleGrpcClientBreakerInterceptor goStyleGrpcClientBreakerInterceptor(
            GoStyleProtectionProperties props) {
        return new GoStyleGrpcClientBreakerInterceptor(props);
    }
}
