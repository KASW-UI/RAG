package com.rag.users.user.api;

import com.rag.users.user.api.auth.GoStyleJwtAuthFilter;
import com.rag.users.user.api.auth.JwtAuthPaths;
import com.rag.users.user.api.client.UserRpcClient;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.boot.web.servlet.ServletContextInitializerBeans;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 🔴 <b>DRIFT-USERAPI-009②</b> 的守门：JWT <b>验签必须发生在 3s 预算之外</b>。
 *
 * <h2>Go 侧依据（真源，逐行）</h2>
 * <pre>
 *   go-zero@v1.10.2 rest/engine.go:101-113  bindRoute：
 *     chn = ng.buildChainWithNativeMiddlewares(...)   // 原生中间件（索引 0 = 最外层）
 *     chn = ng.appendAuthHandler(fr, chn, verifier)   // ← JWT 验签
 *     for _, mw := range ng.middlewares { ... }        // ← observability(3s 计时) 在 JWT **之内**
 *     handle = chn.ThenFunc(route.Handler)
 *   rest/chain/chain.go:38-43  Append = 「加在请求流的最后」⇒ 越 Append 越靠内
 *   ⇒ 执行序：原生中间件 → **JWT 验签** → **observability(3s)** → 黑名单 → handler
 *   ⇒ 3s 计时器在 Authorize **之后**起算 ⇒ **验签耗时不计入 3s 预算**
 * </pre>
 *
 * <h2>本类断言的三件事</h2>
 * <ol>
 *   <li><b>装配层</b>：JWT Filter 真的注册进了本模块上下文；</li>
 *   <li><b>顺序层（本条是核心）</b>：<b>容器实际装配出的</b> Filter 链里，
 *       JWT Filter 排在 {@code HttpMiddlewareFilter}（3s 预算的起算点）<b>之前</b>
 *       ⇒ 验签在预算之外。
 *       ⚠️ 这里断言的是<b>装配结果</b>（{@code ServletContextInitializerBeans} 的排序输出），
 *       <b>不是</b>两个 {@code @Order} 注解的数字 —— 注解只是排序的<b>依据</b>，
 *       装配结果才是运行时事实。</li>
 *   <li><b>行为层</b>：interceptor 确实<b>消费</b>了 Filter 的验签结果
 *       （{@code request().attribute(...)} = {@code "filter"}）
 *       ⇒ 证明验签真的发生在 interceptor 之前（即在 3s 预算之外）；
 *       且 401 的<b>信封形状</b>逐字不变（N-1 签字项）。</li>
 * </ol>
 *
 * <h2>🔴 变异验证（本类不是假绿）</h2>
 * <pre>
 *   变异 1：把 {@code GoStyleJwtAuthFilter} 的 {@code @Order} 改成 +8（跑到 3s 预算之后）
 *         ⇒ 「JWT 在预算之外」那条断言红
 *   变异 2：让 {@code GoStyleJwtAuthFilter} 在验签失败时<b>自己写 401</b>
 *         ⇒ {@code UserApiProtectionMvcTest.oversizedBodyIsRejectedBeforeAuth} 红
 *            （413 压过 401 的语义被打破）
 *   变异 3：摘掉 {@code GoStyleJwtAuthFilter} 的 {@code @Component}
 *         ⇒ 「interceptor 消费 filter 结果」那条断言红（源码会落进 "inline" 兜底）
 * </pre>
 *
 * <h2>为什么用 mock 的 Redis 而不是真 Redis</h2>
 * <p>{@code UserApiProtectionMvcTest} 起真 Redis 是因为它要测「保护层」，
 * 而黑名单查询一旦连不上会卡满 3s 命令超时 ⇒ 撞上 3s HTTP 预算 ⇒ 503。
 * <p>本类只关心<b>验签发生在哪一层</b> ⇒ {@code StringRedisTemplate} 打桩成
 * {@code hasKey → null}（= 未拉黑，与 Go 的 {@code err == nil && exists} 同义）
 * ⇒ 毫秒级返回 ⇒ 被测变量收敛到验签位置本身。
 */
@SpringBootTest(
        classes = UserApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                // 拒连最快；Nacos 连不上只会打日志，不影响本测试
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                // UserRpcClient 整体被 @MockitoBean 替换；这个 channel bean 不会被用到
                // ⚠️ DRIFT-G1-002 之后 user-rpc 开了 immediate-connect-timeout ⇒
                //    不 mock 掉 UserRpcClient 的话上下文**起不来**（连不上 ⇒ 启动期硬失败）
                "grpc.client.user-rpc.address=static://127.0.0.1:65535",
                "grpc.client.user-rpc.negotiation-type=plaintext",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
@AutoConfigureMockMvc
@DisplayName("DRIFT-USERAPI-009②：JWT 验签在 3s 预算之外")
class UserApiJwtVerificationScopeTest {

    private static final String PROTECTED_PATH = "/user/v1/get";

    /**
     * 3s 预算 Filter 的 <b>bean 名</b>（Spring 默认 bean 名 = 类名首字母小写）。
     *
     * <p>⚠️ <b>为什么这里按 bean 名定位，而不是 {@code import com.rag.common.spring.HttpMiddlewareFilter}</b>：
     * <ul>
     *   <li><b>本域的生产代码对共享层的这个 Filter <b>零引用</b></b> —— 它是被组件扫描
     *       （{@code scanBasePackages} 含 {@code com.rag.common}）<b>自动</b>挂上去的；
     *       域侧只是<b>断言它的位置</b>，并不使用它。</li>
     *   <li>⇒ 按 bean 名定位 ⇒ <b>不新增域侧对共享层的编译期依赖</b>；</li>
     *   <li>⇒ 连带好处：共享层辐射度的事实底座（{@code tools/difftest/check-wave-coupling.py}
     *       的 {@code common_radiation}，按 <b>import</b> 统计）<b>保持不变</b>
     *       ⇒ 不制造跨 lane 的快照漂移（那张快照归整合者，本 lane 无写权）。</li>
     * </ul>
     * ⇒ 若该 bean 被改名，本测试会<b>大声失败</b>（{@code NoSuchBeanDefinitionException}）⇒ 不会静默放过。
     */
    private static final String BUDGET_FILTER_BEAN_NAME = "httpMiddlewareFilter";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext ctx;

    /** DRIFT-G1-002 之后必须 mock，否则上下文起不来（见类注释）。 */
    @MockitoBean
    private UserRpcClient userRpcClient;

    /** 黑名单查询打桩：{@code hasKey → null}（= 未拉黑）⇒ 毫秒级返回。 */
    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    // ==================================================================
    // 1. 装配层
    // ==================================================================

    @Test
    @DisplayName("JWT 验签 Filter 真的注册进了本模块上下文")
    void jwtFilterIsRegistered() {
        assertThat(ctx.getBean(GoStyleJwtAuthFilter.class)).isNotNull();
    }

    // ==================================================================
    // 2. 顺序层（本条是核心）
    // ==================================================================

    @Test
    @DisplayName("🔴 容器装配出的链里，JWT Filter 排在 3s 预算（HttpMiddlewareFilter）之前 ⇒ 验签在预算之外")
    void jwtFilterRunsBeforeTheThreeSecondBudgetFilter() {
        List<ServletContextInitializer> filters = assembledFilters();

        int jwtIdx = indexOfFilter(filters, ctx.getBean(GoStyleJwtAuthFilter.class));
        int budgetIdx = indexOfFilter(filters, ctx.getBean(BUDGET_FILTER_BEAN_NAME, Filter.class));

        assertThat(jwtIdx)
                .as("JWT 验签 Filter 不在容器装配出的 Filter 链里")
                .isGreaterThanOrEqualTo(0);
        assertThat(budgetIdx)
                .as("3s 预算 Filter（HttpMiddlewareFilter）不在容器装配出的 Filter 链里")
                .isGreaterThanOrEqualTo(0);
        assertThat(jwtIdx)
                .as("JWT 验签 Filter 必须排在 3s 预算 Filter **之前**（数值小的先执行 ⇒ 包在外面）"
                        + "，否则验签仍被 3s 预算覆盖 ⇒ DRIFT-USERAPI-009② 不闭环。"
                        + "⚠️ Go 侧：Authorize 在 observability(3s) **之外**（更外层）。")
                .isLessThan(budgetIdx);
    }

    /**
     * 取<b>容器实际装配出</b>的 Filter 顺序。
     *
     * <p>⚠️ 为什么不用「读两个 {@code @Order} 注解比大小」：
     * 注解只是 Spring 排序的<b>依据</b>，装配结果才是运行时事实。
     * {@code ServletContextInitializerBeans} 就是 Boot 在启动时用来装配 Filter 的那个类
     * ⇒ 它的排序输出 = 容器里的真实顺序。
     */
    private List<ServletContextInitializer> assembledFilters() {
        return new ServletContextInitializerBeans(ctx).stream()
                .filter(i -> i instanceof FilterRegistrationBean<?>)
                .toList();
    }

    private static int indexOfFilter(List<ServletContextInitializer> filters, Filter target) {
        for (int i = 0; i < filters.size(); i++) {
            ServletContextInitializer init = filters.get(i);
            if (init instanceof FilterRegistrationBean<?> frb && frb.getFilter() == target) {
                return i;
            }
        }
        return -1;
    }

    // ==================================================================
    // 3. 行为层
    // ==================================================================

    @Test
    @DisplayName("🔴 interceptor 消费的是 Filter 的验签结果（=filter），不是自己就地验签（=inline）")
    void interceptorConsumesTheFilterResult() throws Exception {
        mockMvc.perform(post(PROTECTED_PATH)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"uid\":\"1\"}"))
                .andExpect(request().attribute(
                        GoStyleJwtAuthFilter.VERIFICATION_SOURCE_ATTRIBUTE,
                        GoStyleJwtAuthFilter.SOURCE_FILTER));
    }

    @Test
    @DisplayName("401 的信封形状不变（401 + code 1006 + data null）—— N-1 签字项不受影响")
    void unauthorizedEnvelopeIsUnchanged() throws Exception {
        mockMvc.perform(post(PROTECTED_PATH)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"uid\":\"1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1006))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    // ==================================================================
    // 4. 路径判据自检
    // ==================================================================

    @Test
    @DisplayName("路径判据：public 放行、protected 拦截、域外不碰")
    void pathPredicateMatchesGoRouteSplit() {
        // Go: rest.AddRoutes(login, register) 不带 WithJwt ⇒ public
        assertThat(JwtAuthPaths.isPublic("/user/v1/login")).isTrue();
        assertThat(JwtAuthPaths.isPublic("/user/v1/register")).isTrue();
        assertThat(JwtAuthPaths.isProtected("/user/v1/login")).isFalse();
        assertThat(JwtAuthPaths.isProtected("/user/v1/register")).isFalse();

        // Go: 其余 7 个路径带 WithJwt ⇒ protected
        assertThat(JwtAuthPaths.isProtected("/user/v1/get")).isTrue();
        assertThat(JwtAuthPaths.isProtected("/user/v1/avatar/history")).isTrue();
        assertThat(JwtAuthPaths.isProtected("/user/v1")).isTrue();

        // 域外路径两段都不碰
        assertThat(JwtAuthPaths.isProtected("/actuator/health")).isFalse();
        assertThat(JwtAuthPaths.isPublic("/actuator/health")).isFalse();
    }
}
