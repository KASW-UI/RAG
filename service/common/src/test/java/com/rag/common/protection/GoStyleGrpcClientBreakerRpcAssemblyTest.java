package com.rag.common.protection;

import com.rag.test.rpc.RpcLikeApplication;
import net.devh.boot.grpc.client.interceptor.GlobalClientInterceptorRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>守门：gRPC client 侧熔断拦截器在「纯 gRPC 进程」的装配面里真进了 devh 的收集器。</b>
 *
 * <h2>这条守门钉住的是什么（问题来源）</h2>
 * <p><b>G3 派单（2026-10-27 · 反馈单 7 项齐全）</b>：
 * {@link GoStyleGrpcClientBreakerInterceptor} 原先带 {@code @Component} ⇒ 只能靠组件扫描拿到；
 * 而纯 gRPC 进程<b>刻意不扫</b> {@code com.rag.common} ⇒ ⇒ <b>rpc 侧永远拿不到 client breaker</b>
 * ⇒ <b>出向调用无熔断</b>，而 Go 侧 zrpc <b>默认开</b> ⇒ 两侧行为分叉。
 * ⇒ 且该缺陷<b>没有任何报错</b>：编译通过、上下文能启动、Bean 断言照样是绿的。
 *
 * <h2>Go 出处（逐行核对过）</h2>
 * <pre>
 * // ① go-zero@v1.10.2 zrpc/internal/config.go:14   —— 默认开启
 * Breaker    bool `json:",default=true"`
 * // ② go-zero@v1.10.2 zrpc/internal/client.go:112-113 —— client 侧装配
 * if c.middlewares.Breaker {
 *     interceptors = append(interceptors, clientinterceptors.BreakerInterceptor)
 * }
 * // ③ go-zero@v1.10.2 zrpc/internal/clientinterceptors/breakerinterceptor.go:13-18
 * </pre>
 * <p>（真源仓库 {@code Sea-RideTheWind_golang} 不含 {@code zrpc}；它由真源 {@code go.mod:17}
 * 锁定的 {@code github.com/zeromicro/go-zero v1.10.2} 提供。）
 *
 * <h2>为什么断言的是「starter 的 registry」而不是「Bean 存在」</h2>
 * <p>AGENTS.md §3.3：<b>断言 Bean 存在 ≠ 断言机制生效</b>。
 * <p>devh 的真实链路（{@code javap} 实测 3.1.0.RELEASE）：
 * <ol>
 *   <li>{@code GrpcClientAutoConfiguration} 产出
 *       {@code @Bean AnnotationGlobalClientInterceptorConfigurer(ApplicationContext)}；</li>
 *   <li>该 configurer 的 {@code configureClientInterceptors(List)} 用
 *       {@code getBeansWithAnnotation(GrpcGlobalClientInterceptor.class)} 收集
 *       ⇒ <b>只认类上的 {@code @GrpcGlobalClientInterceptor} 注解</b>；</li>
 *   <li>{@code GlobalClientInterceptorRegistry.initClientInterceptors()}
 *       用 {@code getBeansOfType(GlobalClientInterceptorConfigurer.class)} 收集 configurer 并调用它们；</li>
 *   <li>{@code nettyGrpcChannelFactory(..., GlobalClientInterceptorRegistry, ...)}
 *       ⇒ <b>channel factory 消费这个 registry</b> ⇒ registry 里有它，channel 里才会有它。</li>
 * </ol>
 * ⇒ ⇒ 本类断言的是<b>第 ③ 步的产物</b>（registry 的列表里确实有它）⇒ 由此可推出第 ④ 步成立。
 *
 * <h2>为什么用「模拟 rpc 装配面」而不是某个域的 Application</h2>
 * <p>共享层的测试<b>不能依赖域侧 jar</b>；而 rpc 的装配口径是
 * 「<b>不扫 {@code com.rag.common}、改用 {@code @Import} 精确引入</b>」
 * ⇒ {@link RpcLikeApplication} 就按这个口径搭了一个最小上下文。
 */
@SpringBootTest(
        classes = RpcLikeApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "management.otlp.tracing.export.enabled=false"
        })
class GoStyleGrpcClientBreakerRpcAssemblyTest {

    @Autowired
    private ApplicationContext ctx;

    /**
     * ⚠️ <b>刻意用「按需取」而不是 {@code @Autowired} 注入</b>：
     * 若用 {@code @Autowired}，那么「Bean 缺失」会让<b>测试上下文起不来</b>
     * ⇒ 变异验证时红的是「上下文创建失败」，<b>不是目标断言</b>
     * ⇒ 按 AGENTS.md §3.3「红测试必须红在目标断言上」的纪律 ⇒ 这里改成按需取，
     * 让变异后上下文<b>照常启动</b>、由断言负责报错。
     */
    private GoStyleGrpcClientBreakerInterceptor breaker() {
        return ctx.getBeanProvider(GoStyleGrpcClientBreakerInterceptor.class).getIfAvailable();
    }

    @Test
    @DisplayName("纯 gRPC 装配面（不扫 com.rag.common，只 @Import）里，client breaker 仍然成为 Bean")
    void clientBreakerIsABeanInRpcLikeContext() {
        assertThat(breaker())
                .as("纯 gRPC 进程刻意不扫 com.rag.common ⇒ client breaker 只能靠 @Import 链拿到；"
                        + "拿不到 ⇒ rpc 侧出向调用无熔断（Go 侧 zrpc 默认开）")
                .isNotNull();
    }

    @Test
    @DisplayName("devh 的 GlobalClientInterceptorRegistry 里确实有它 ⇒ channel factory 会装配它")
    void devhRegistryActuallyCollectsIt() {
        GlobalClientInterceptorRegistry registry = ctx.getBean(GlobalClientInterceptorRegistry.class);
        GoStyleGrpcClientBreakerInterceptor interceptor = breaker();

        assertThat(interceptor)
                .as("前置：Bean 必须在（否则本条断言无意义）")
                .isNotNull();
        assertThat(registry.getClientInterceptors())
                .as("拦截器必须在 starter 收集到的 ClientInterceptor 列表里；"
                        + "否则它只是个 Bean，永远不会进入 rpc 的出向调用链 ⇒ 出向无熔断")
                .contains(interceptor);
    }

    @Test
    @DisplayName("默认配置下熔断是**开启**的（Go zrpc/internal/config.go:14 default=true）")
    void breakerIsEnabledByDefault() {
        GoStyleProtectionProperties props = ctx.getBean(GoStyleProtectionProperties.class);

        assertThat(props.getBreaker().isEnabled())
                .as("默认必须开启，否则退回「Go 有熔断、RAG 没有」的漂移状态")
                .isTrue();
    }
}
