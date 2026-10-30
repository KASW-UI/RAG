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
 * <b>守门：出向 deadline 传播拦截器在「纯 gRPC 进程」的装配面里真进了 devh 的收集器。</b>
 *
 * <h2>这条守门钉住的是什么</h2>
 * <p>devh 的 {@code AnnotationGlobalClientInterceptorConfigurer} 是用
 * {@code applicationContext.getBeansWithAnnotation(GrpcGlobalClientInterceptor.class)}
 * 收集的 ⇒ 它认的是<b>类上的注解</b>，不是「是不是 ClientInterceptor」
 * ⇒ ⇒ <b>Bean 在、但 registry 里没有</b> ⇒ <b>出向调用永远拿不到 deadline</b>
 * ⇒ 而这一切<b>不报错</b>：编译通过、上下文能启动、Bean 断言照样是绿的
 * ⇒ ⇒ 必须有一条<b>直接读 registry</b> 的网（AGENTS.md §3.3）。
 *
 * <h2>为什么用「模拟 rpc 装配面」而不是某个域的 Application</h2>
 * <p>共享层的测试<b>不能依赖域侧 jar</b>；而 rpc 的装配口径是
 * 「<b>不扫 {@code com.rag.common}、改用 {@code @Import} 精确引入</b>」
 * ⇒ {@link RpcLikeApplication} 就按这个口径搭了一个最小上下文。
 *
 * <h2>Go 出处（装配面为什么是「客户端拦截器级」）</h2>
 * <pre>
 * // go-zero@v1.10.2 zrpc/internal/client.go:115-116
 * if c.middlewares.Timeout {
 *     interceptors = append(interceptors, clientinterceptors.TimeoutInterceptor(timeout))
 * }
 * // go-zero@v1.10.2 zrpc/internal/config.go:15  Timeout bool `json:",default=true"`
 * </pre>
 */
@SpringBootTest(
        classes = RpcLikeApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "management.otlp.tracing.export.enabled=false"
        })
class GoStyleOutboundDeadlineRpcAssemblyTest {

    @Autowired
    private ApplicationContext ctx;

    /**
     * ⚠️ <b>刻意用「按需取」而不是 {@code @Autowired} 注入</b>：
     * 若用 {@code @Autowired}，那么「Bean 缺失」会让<b>测试上下文起不来</b>
     * ⇒ 变异验证时红的是「上下文创建失败」，<b>不是目标断言</b>
     * ⇒ 按 AGENTS.md §3.3「红测试必须红在目标断言上」的纪律 ⇒ 这里改成按需取。
     */
    private GoStyleOutboundDeadlineInterceptor interceptor() {
        return ctx.getBeanProvider(GoStyleOutboundDeadlineInterceptor.class).getIfAvailable();
    }

    @Test
    @DisplayName("纯 gRPC 装配面（不扫 com.rag.common，只 @Import）里，出向 deadline 拦截器仍然是 Bean")
    void interceptorIsABeanInRpcLikeContext() {
        assertThat(interceptor())
                .as("纯 gRPC 进程刻意不扫 com.rag.common ⇒ 出向 deadline 拦截器只能靠 @Import 链拿到；"
                        + "拿不到 ⇒ rpc 侧出向调用不继承请求 deadline（E65）")
                .isNotNull();
    }

    @Test
    @DisplayName("devh 的 GlobalClientInterceptorRegistry 里确实有它 ⇒ channel factory 会装配它")
    void devhRegistryActuallyCollectsIt() {
        GlobalClientInterceptorRegistry registry = ctx.getBean(GlobalClientInterceptorRegistry.class);
        GoStyleOutboundDeadlineInterceptor interceptor = interceptor();

        assertThat(interceptor)
                .as("前置：Bean 必须在（否则本条断言无意义）")
                .isNotNull();
        assertThat(registry.getClientInterceptors())
                .as("拦截器必须在 starter 收集到的 ClientInterceptor 列表里；"
                        + "否则它只是个 Bean，永远不会进入出向调用链 ⇒ E65 不闭合")
                .contains(interceptor);
    }

    @Test
    @DisplayName("默认配置下「出向 deadline 传播」是**开启**的（Go 侧客户端超时中间件默认开）")
    void propagationIsEnabledByDefault() {
        GoStyleProtectionProperties props = ctx.getBean(GoStyleProtectionProperties.class);

        assertThat(props.getOutboundDeadline().isEnabled())
                .as("默认必须开启，否则退回「Go 有 ctx 传播、RAG 没有」的漂移状态")
                .isTrue();
    }

    @Test
    @DisplayName("客户端硬上界本轮默认 **0（不启用）** ⇒ 不给调用凭空加 2000ms 约束")
    void clientCapIsOffByDefault() {
        GoStyleProtectionProperties props = ctx.getBean(GoStyleProtectionProperties.class);

        assertThat(props.getOutboundDeadline().getCapMillis())
                .as("Go 的 2000ms 是**默认值**而各域可覆盖（真源 article.yaml = 70000/10000）"
                        + "⇒ 写成全局默认会比 Go 严 35 倍 ⇒ 本轮默认不启用，逐域启用登记在 P2-T04")
                .isZero();
    }
}
