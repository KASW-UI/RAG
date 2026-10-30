package com.rag.common.protection;

import com.rag.test.api.ApiLikeApplication;
import net.devh.boot.grpc.client.interceptor.GlobalClientInterceptorRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>守门：api 侧的「<b>包扫描</b>」装配面里，出向 deadline 拦截器也真进了 devh 的收集器。</b>
 *
 * <h2>为什么这条守门必须存在（它补的是哪条缺口）</h2>
 * <p>共享层目前有<b>两条不同的装配路径</b>：
 * <ul>
 *   <li><b>rpc 侧</b>：<b>不扫</b> {@code com.rag.common}，靠 {@code @Import} 精确引入
 *       ⇒ 由 {@code GoStyleOutboundDeadlineRpcAssemblyTest} 钉住；</li>
 *   <li><b>api 侧</b>：<b>扫</b> {@code com.rag.common}（10 个 api 组件：9 个
 *       {@code scanBasePackages} ＋ {@code task-api} 的 {@code @ComponentScan}）
 *       ⇒ ⇒ <b>在补这条守门之前，api 侧只有「源码级推断」</b>
 *       （读过 10 个 {@code *Application.java} 的扫描声明），
 *       <b>没有任何运行证据</b>证明 api 进程真的拿到了这个拦截器
 *       ⇒ ⇒ 而 AGENTS.md §3.3 明确：「<b>配置里没写 ≠ 没开</b>」、
 *       「<b>注解存在 ≠ 机制生效</b>」。</li>
 * </ul>
 * ⇒ ⇒ 本守门用<b>与 api 同款的包扫描机制</b>起真上下文，直接读 devh 的
 * {@code GlobalClientInterceptorRegistry}。
 *
 * <h2>效力边界（不许外推）</h2>
 * <p>本守门证明的是「<b>包扫描 ⇒ 发现 {@code GoStyleProtectionConfiguration}
 * ⇒ 它的 {@code @Import} 链 ⇒ 拦截器 Bean ⇒ 进 devh 的 registry</b>」这一条链；
 * <b>不等于</b>「某个具体 api 进程（带它自己的 yml 与全部共享层 Bean）也起得来」
 * ⇒ ⇒ 那一层需要各域自己的守门（本 lane 无域侧写权 ⇒ 已登记为转派项）。
 *
 * <h2>判据为什么有鉴别力</h2>
 * <p>若把 {@link ApiLikeApplication} 的 {@code @ComponentScan} 去掉
 * （⇒ 退化成「只有 @Import」的 rpc 口径）⇒ 上下文里就<b>没有</b>拦截器
 * ⇒ ⇒ 断言<b>精确转红</b>（红在目标断言上，不是「上下文起不来」）。
 *
 * <h2>Go 出处（为什么 api 侧也该有这条能力面）</h2>
 * <pre>
 * // go-zero@v1.10.2 zrpc/internal/client.go:115-116
 * if c.middlewares.Timeout {
 *     interceptors = append(interceptors, clientinterceptors.TimeoutInterceptor(timeout))
 * }
 * // go-zero@v1.10.2 zrpc/internal/config.go:15  Timeout bool `json:",default=true"`
 * // ⇒ api 侧同样经由 zrpc client 装上 ⇒ 与 rpc 侧**同一条能力面**
 * </pre>
 */
@SpringBootTest(
        classes = ApiLikeApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "management.otlp.tracing.export.enabled=false"
        })
class GoStyleOutboundDeadlineApiAssemblyTest {

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
    @DisplayName("api 侧的**包扫描**装配面里，出向 deadline 拦截器仍然是 Bean")
    void interceptorIsABeanInApiLikeContext() {
        assertThat(interceptor())
                .as("api 侧靠包扫描拿到共享层配置 ⇒ 拿不到 ⇒ api 的出向 gRPC 不继承请求 deadline（E65）")
                .isNotNull();
    }

    @Test
    @DisplayName("api 侧：devh 的 GlobalClientInterceptorRegistry 里确实有它 ⇒ channel factory 会装配它")
    void devhRegistryActuallyCollectsItInApiLikeContext() {
        GlobalClientInterceptorRegistry registry = ctx.getBean(GlobalClientInterceptorRegistry.class);
        GoStyleOutboundDeadlineInterceptor interceptor = interceptor();

        assertThat(interceptor)
                .as("前置：Bean 必须在（否则本条断言无意义）")
                .isNotNull();
        assertThat(registry.getClientInterceptors())
                .as("拦截器必须在 starter 收集到的 ClientInterceptor 列表里；"
                        + "否则它只是个 Bean，永远不会进入出向调用链 ⇒ api 侧 E65 不闭合")
                .contains(interceptor);
    }
}
