package com.rag.article.article.api;

import com.rag.article.article.api.auth.ArticleJwtProtectedRoutes;
import com.rag.article.article.api.auth.GoStyleJwtAuthFilter;
import com.rag.article.article.api.client.ArticleRpcClient;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 🔴 <b>DRIFT-USERAPI-009②</b>（article-api 推广）的守门：JWT <b>验签必须发生在 70s 预算之外</b>。
 *
 * <h2>⚠️ 本域预算是 <b>70s</b>，不是 3s（全仓唯一例外）</h2>
 * <p>RAG `article-api/.../application.yml:234` {@code http-timeout: 70s}
 * ≡ Go `service/article/api/etc/article.yaml:4` {@code Timeout: 70000}
 * ⇒ 而其余 api 域（favorite / comment / hot / users / follow / message / like / task）
 * 全部是 {@code 3s} ⇒ ⇒ **本域是唯一例外**。
 * ⇒ ⚠️ **教训**：推广配方时**预算数值必须逐域核**，不能照抄上游域的「3s」
 * （抄错了也不影响**代码行为**——本条修的是**作用域**不是阈值——但**注释/台账会变成假事实**）。
 *
 * <h2>Go 侧依据（真源，逐行）</h2>
 * <pre>
 *   service/article/api/articlecenter.go
 *     httpTimeout := observability.DisableNativeRestTimeout(&amp;c.RestConf)
 *     server := rest.MustNewServer(c.RestConf)
 *     server.Use(observability.NewHTTPMiddleware(c.Name, httpTimeout, observability.SlowThreshold()))
 *     ⇒ observability 是**唯一**的 user middleware；native 的 Timeout 被关掉
 *
 *   go-zero@v1.10.2 rest/engine.go:101-116  bindRoute：
 *     chn = ng.buildChainWithNativeMiddlewares(...)   // 原生中间件（索引 0 = 最外层）
 *     chn = ng.appendAuthHandler(fr, chn, verifier)   // ← JWT 验签（:108）
 *     for _, mw := range ng.middlewares { ... }        // ← observability(3s) 在 JWT **之内**（:110-112）
 *     handle = chn.ThenFunc(route.Handler)
 *   rest/chain/chain.go  Append = 「加在请求流的最后」⇒ 越 Append 越靠内
 *   ⇒ 执行序：原生中间件 → **JWT 验签** → **observability(70s 计时)** → handler
 *   ⇒ 70s 计时器在 Authorize **之后**起算 ⇒ **验签耗时不计入 70s 预算**
 * </pre>
 *
 * <h2>⚠️ 本域与 comment / favorite 域的<b>关键差异</b></h2>
 * <p>本域有 <b>3 个公开端点</b>（Go {@code routes.go} 第二/三段 {@code AddRoutes} **不带
 * {@code WithJwt}）：{@code GET /v1/article/:id} · {@code GET /v1/articles} ·
 * {@code POST /v1/upload} ⇒ ⇒ <b>Filter 的「要不要验签」判据不能是「路径在 /v1/** 下」</b>
 * ⇒ 必须与 interceptor 共用<b>同一份</b>「method + path ∈ PROTECTED_ROUTES」判据
 * （{@link ArticleJwtProtectedRoutes}）⇒ 否则要么<b>公开端点被误伤</b>（安全面收窄但行为漂移），
 * 要么<b>受保护端点落进兜底分支</b>（②静默不闭环）。
 * <p>⇒ 本类的第 7 条（路径判据自检）就是这条不变式的<b>结构面</b>证据；
 * **行为面**由既有守门 {@code ArticleApiRouteAuthBoundaryTest} 的
 * 「7 个端点的鉴权边界快照：4 个要 JWT、3 个公开」覆盖。
 *
 * <h2>本类断言的四件事</h2>
 * <ol>
 *   <li><b>装配层</b>：JWT Filter 真的注册进了本模块上下文；</li>
 *   <li><b>顺序层（本条是核心）</b>：<b>容器实际装配出的</b> Filter 链里，
 *       JWT Filter 排在 {@code HttpMiddlewareFilter}（70s 预算的起算点）<b>之前</b>
 *       ⇒ 验签在预算之外。
 *       ⚠️ 这里断言的是<b>装配结果</b>（{@code ServletContextInitializerBeans} 的排序输出），
 *       <b>不是</b>两个 {@code @Order} 注解的数字 —— 注解只是排序的<b>依据</b>，
 *       装配结果才是运行时事实。</li>
 *   <li><b>行为层</b>：interceptor 确实<b>消费</b>了 Filter 的验签结果
 *       （{@code request().attribute(...)} = {@code "filter"}）；</li>
 *   <li><b>不变式层</b>：401 的<b>空 body</b>形状逐字不变；「413 压过 401」不变；
 *       「未注册路径仍是 404」不变（⇒ Filter <b>绝不写响应</b>这条不变式的行为级证据）。</li>
 * </ol>
 *
 * <h2>🔴 变异验证（本类不是假绿）</h2>
 * <pre>
 *   变异 1：把 {@code GoStyleJwtAuthFilter} 的 {@code @Order} 改成 +8（跑到 70s 预算之后）
 *         ⇒ 「JWT 在预算之外」那条断言红
 *   变异 2：让 Filter 在验签失败时<b>自己写 401</b>
 *         ⇒ 「未注册路径仍 404」「413 压过 401」两条红
 *   变异 3：摘掉 Filter 的 {@code @Component}
 *         ⇒ 「interceptor 消费 filter 结果」那条断言红（来源标记变 "inline"）
 * </pre>
 */
@SpringBootTest(
        classes = ArticleApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "grpc.client.article-rpc.address=static://127.0.0.1:65535",
                "grpc.client.article-rpc.negotiation-type=plaintext",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN",
                // 便于触发「413 压过 401」那条（Go 侧 article-api 的第三段 AddRoutes 带
                // WithMaxBytes(10MB)，第一段不带 ⇒ 本类只证明 RAG 侧的不变式成立）
                "rag.protection.max-bytes=1024",
                // 让判据**只**取决于「本类要验的那条路径」，不被 CPU 自适应降载抢先返回：
                // 降载命中时 GoStyleCpuSheddingFilter#doFilter 走 `response.setStatus(503)` 并**直接 return**
                // ⇒ **裸 503、无 body、无业务信封** ⇒ 本类要验的「HTTP 200 + 业务信封」判据被抢先盖掉
                // ⇒ ⇒ 机器一忙就**红在错的地方**（判据与被测路径无关）。
                // ⚠️ 这是**测试专用**覆盖，不改生产默认值：默认 true 对齐 Go 侧
                //    rest/config.go:22 `Middlewares.Shedding bool json:",default=true"`
                //    （默认值与 Go 出处见 GoStyleProtectionProperties.CpuShedding#enabled）。
                "rag.protection.cpu-shedding.enabled=false"
        })
@AutoConfigureMockMvc
@DisplayName("DRIFT-USERAPI-009②（article-api）：JWT 验签在 70s 预算之外")
class ArticleApiJwtVerificationScopeTest {

    /** 受保护端点（Go routes.go 第一段）：POST /v1/article。 */
    private static final String ARTICLE_CREATE = "/v1/article";
    private static final String UNREGISTERED = "/v1/nope";

    /**
     * 70s 预算 Filter 的 <b>bean 名</b>（Spring 默认 bean 名 = 类名首字母小写）。
     *
     * <p>⚠️ <b>为什么按 bean 名定位，而不是 import 共享层的 {@code HttpMiddlewareFilter}</b>：
     * <ul>
     *   <li>本域的<b>生产代码</b>对共享层的这个 Filter <b>零引用</b> —— 它是被组件扫描
     *       （{@code scanBasePackages} 含 {@code com.rag.common}）<b>自动</b>挂上去的；
     *       域侧只是<b>断言它的位置</b>，并不使用它。</li>
     *   <li>⇒ 按 bean 名定位 ⇒ <b>不新增域侧对共享层的编译期依赖</b>
     *       ⇒ 共享层辐射度的事实底座（{@code tools/difftest/check-wave-coupling.py}
     *       的 {@code common_radiation}，按 <b>import</b> 统计）<b>保持不变</b>
     *       ⇒ 不制造跨 lane 的快照漂移（那张快照归整合者，本 lane 无写权）。</li>
     * </ul>
     * ⇒ 若该 bean 被改名，本测试会<b>大声失败</b>（{@code NoSuchBeanDefinitionException}）。
     */
    private static final String BUDGET_FILTER_BEAN_NAME = "httpMiddlewareFilter";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext ctx;

    /** article-rpc 的 client —— 整体 mock 掉，本类不碰 RPC。 */
    @MockitoBean
    private ArticleRpcClient articleRpcClient;

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
    @DisplayName("🔴 容器装配出的链里，JWT Filter 排在 70s 预算（HttpMiddlewareFilter）之前 ⇒ 验签在预算之外")
    void jwtFilterRunsBeforeTheHttpBudgetFilter() {
        List<ServletContextInitializer> filters = assembledFilters();

        int jwtIdx = indexOfFilter(filters, ctx.getBean(GoStyleJwtAuthFilter.class));
        int budgetIdx = indexOfFilter(filters, ctx.getBean(BUDGET_FILTER_BEAN_NAME, Filter.class));

        assertThat(jwtIdx)
                .as("JWT 验签 Filter 不在容器装配出的 Filter 链里")
                .isGreaterThanOrEqualTo(0);
        assertThat(budgetIdx)
                .as("70s 预算 Filter（HttpMiddlewareFilter）不在容器装配出的 Filter 链里")
                .isGreaterThanOrEqualTo(0);
        assertThat(jwtIdx)
                .as("JWT 验签 Filter 必须排在 70s 预算 Filter **之前**（数值小的先执行 ⇒ 包在外面）"
                        + "，否则验签仍被 70s 预算覆盖 ⇒ DRIFT-USERAPI-009② 不闭环。"
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
        mockMvc.perform(post(ARTICLE_CREATE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\"}"))
                .andExpect(request().attribute(
                        GoStyleJwtAuthFilter.VERIFICATION_SOURCE_ATTRIBUTE,
                        GoStyleJwtAuthFilter.SOURCE_FILTER));
    }

    // ==================================================================
    // 4. 不变式层
    // ==================================================================

    @Test
    @DisplayName("401 的空 body 形状不变（Go 的 unauthorized() 只 WriteHeader(401)，不写任何字节）")
    void unauthorizedEmptyBodyIsUnchanged() throws Exception {
        mockMvc.perform(post(ARTICLE_CREATE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));
    }

    /**
     * 🔴 造一个超过 {@code rag.protection.max-bytes=1024}、但仍是合法 JSON 的体。
     */
    private static String oversizedJson() {
        StringBuilder sb = new StringBuilder("{\"title\":\"");
        while (sb.length() < 4096) {
            sb.append('a');
        }
        return sb.append("\"}").toString();
    }

    @Test
    @DisplayName("🔴 超限 body + 无 token → 413（不是 401）⇒「413 压过 401」没被搬家搬坏")
    void oversizedBodyStillBeatsUnauthorized() throws Exception {
        // ⚠️ 刻意**不带** Authorization ⇒ 拿到 413 而不是 401，
        //    就是「MaxBytes 在鉴权之外」的唯一证据（与 Go 的 chain 序一致：
        //    MaxBytes 是 native middleware，排在 Authorize 之外 ⇒ rest/engine.go:163-165）。
        mockMvc.perform(post(ARTICLE_CREATE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oversizedJson()))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("🔴 未注册路径【无 token】→ 仍是 404（不是 401）⇒ Filter 绝不写响应")
    void unregisteredPathWithoutTokenIsStill404() throws Exception {
        // 这条是「Filter **绝不写响应**」这条不变式的**行为级**证据：
        // 未注册路径压根不命中受保护表 ⇒ Filter 不会验签它；
        // 而 interceptor 的守卫 1（handler instanceof HandlerMethod）⇒ 放行 ⇒ 404。
        mockMvc.perform(get(UNREGISTERED))
                .andExpect(status().isNotFound());
    }

    // ==================================================================
    // 5. 路径判据自检（结构面）
    // ==================================================================

    @Test
    @DisplayName("路径判据：4 个受保护（method+path 双命中）· 3 个公开 · method 不匹配一律放行")
    void pathPredicateMatchesGoRouteSplit() {
        // ── 受保护（Go routes.go 第一段，带 WithJwt）──
        assertThat(ArticleJwtProtectedRoutes.isProtected("POST", "/v1/article")).isTrue();
        assertThat(ArticleJwtProtectedRoutes.isProtected("PUT", "/v1/article/1")).isTrue();
        assertThat(ArticleJwtProtectedRoutes.isProtected("DELETE", "/v1/article/1")).isTrue();
        assertThat(ArticleJwtProtectedRoutes.isProtected("GET", "/v1/me/article/1")).isTrue();

        // ── 公开（第二/三段，**不带** WithJwt）──
        assertThat(ArticleJwtProtectedRoutes.isProtected("GET", "/v1/article/1")).isFalse();
        assertThat(ArticleJwtProtectedRoutes.isProtected("GET", "/v1/articles")).isFalse();
        assertThat(ArticleJwtProtectedRoutes.isProtected("POST", "/v1/upload")).isFalse();

        // ── 「路径像但 method 不对」⇒ 放行（否则会凭空多出「401 压过 405」）──
        assertThat(ArticleJwtProtectedRoutes.isProtected("POST", "/v1/article/1")).isFalse();
        assertThat(ArticleJwtProtectedRoutes.isProtected("PATCH", "/v1/article/1")).isFalse();
        assertThat(ArticleJwtProtectedRoutes.isProtected("POST", "/v1/articles")).isFalse();

        // ── 域外路径 ──
        assertThat(ArticleJwtProtectedRoutes.isProtected("GET", "/actuator/health")).isFalse();
        assertThat(ArticleJwtProtectedRoutes.isProtected(null, "/v1/article")).isFalse();
        assertThat(ArticleJwtProtectedRoutes.isProtected("POST", null)).isFalse();
    }
}
