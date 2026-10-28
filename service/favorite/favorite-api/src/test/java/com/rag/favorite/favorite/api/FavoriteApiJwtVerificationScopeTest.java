package com.rag.favorite.favorite.api;

import com.rag.favorite.favorite.api.auth.FavoriteJwtAuthPaths;
import com.rag.favorite.favorite.api.auth.GoStyleJwtAuthFilter;
import com.rag.favorite.favorite.api.client.FavoriteRpcClient;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 🔴 <b>DRIFT-USERAPI-009②</b>（favorite-api 推广）的守门：JWT <b>验签必须发生在 3s 预算之外</b>。
 *
 * <h2>Go 侧依据（真源，逐行）</h2>
 * <pre>
 *   service/favorite/api/favorite.go:28-30
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
 *   ⇒ 执行序：原生中间件 → **JWT 验签** → **observability(3s 计时)** → 黑名单 → handler
 *   ⇒ 3s 计时器在 Authorize **之后**起算 ⇒ **验签耗时不计入 3s 预算**
 *   ⇒ 黑名单（route 级 WithMiddlewares ⇒ 最内层）在 3s 预算**之内** ⇒ RAG 现状已一致
 * </pre>
 *
 * <h2>本类断言的四件事</h2>
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
 *       ⇒ 证明验签真的发生在 interceptor 之前（即在 3s 预算之外）；</li>
 *   <li><b>不变式层</b>：401 的<b>信封形状</b>逐字不变；「413 压过 401」不变；
 *       「未注册路径仍是 404」不变（⇒ Filter <b>绝不写响应</b>这条不变式的行为级证据）。</li>
 * </ol>
 *
 * <h2>🔴 变异验证（本类不是假绿）</h2>
 * <pre>
 *   变异 1：把 {@code GoStyleJwtAuthFilter} 的 {@code @Order} 改成 +8（跑到 3s 预算之后）
 *         ⇒ 「JWT 在预算之外」那条断言红
 *   变异 2：让 Filter 在验签失败时<b>自己写 401</b>
 *         ⇒ 「未注册路径仍 404」「413 压过 401」两条红
 *   变异 3：摘掉 Filter 的 {@code @Component}
 *         ⇒ 「interceptor 消费 filter 结果」那条断言红（源码会落进 "inline" 兜底）
 * </pre>
 */
@SpringBootTest(
        classes = FavoriteApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "spring.cloud.nacos.discovery.enabled=false",
                // 给个不会真连的地址，避免 devh 的 channel 解析去碰 Nacos
                "grpc.client.favorite-rpc.address=static://127.0.0.1:65535",
                "grpc.client.favorite-rpc.negotiation-type=plaintext",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN",
                // 便于触发「413 压过 401」那条（Go 侧 favorite-api 没有 MaxBytes，
                // 走 rest/config.go:53 的 default=1048576 ⇒ 本类只证明 RAG 侧的不变式成立）
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
@DisplayName("DRIFT-USERAPI-009②（favorite-api）：JWT 验签在 3s 预算之外")
class FavoriteApiJwtVerificationScopeTest {

    private static final String FOLDER_CREATE = "/favorite/v1/folder/create";
    private static final String UNREGISTERED = "/favorite/v1/nope";

    /**
     * 3s 预算 Filter 的 <b>bean 名</b>（Spring 默认 bean 名 = 类名首字母小写）。
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

    /** favorite-rpc 的 client —— 整体 mock 掉，本类不碰 RPC。 */
    @MockitoBean
    private FavoriteRpcClient favoriteRpcClient;

    /** 黑名单查询打桩：{@code hasKey → null}（= 未拉黑）⇒ 毫秒级返回、不连 Redis。 */
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
        mockMvc.perform(post(FOLDER_CREATE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"n\"}"))
                .andExpect(request().attribute(
                        GoStyleJwtAuthFilter.VERIFICATION_SOURCE_ATTRIBUTE,
                        GoStyleJwtAuthFilter.SOURCE_FILTER));
    }

    // ==================================================================
    // 4. 不变式层
    // ==================================================================

    @Test
    @DisplayName("401 的信封形状不变（401 + code 5004 + data null）")
    void unauthorizedEnvelopeIsUnchanged() throws Exception {
        mockMvc.perform(post(FOLDER_CREATE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"n\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(5004))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    /**
     * 🔴 造一个超过 {@code rag.protection.max-bytes=1024}、但仍是合法 JSON 的体。
     */
    private static String oversizedJson() {
        StringBuilder sb = new StringBuilder("{\"name\":\"");
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
        mockMvc.perform(post(FOLDER_CREATE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oversizedJson()))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("🔴 未注册路径【无 token】→ 仍是 404（不是 401）⇒ Filter 绝不写响应")
    void unregisteredPathWithoutTokenIsStill404() throws Exception {
        // 这条是「Filter **绝不写响应**」这条不变式的**行为级**证据：
        // Filter 对 /favorite/v1/** 下的一切都会验签（/favorite/v1/nope 也在内），
        // 验签失败 ⇒ 若 Filter 自己写 401，这里就会变成 401。
        // ⇒ 仍是 404 ⇒ 证明 401 只由 interceptor 抛 ⇒ 与 Go 的 router 一致
        //    （patrouter.go:73,79 的 404 分支进不了鉴权 chain）。
        mockMvc.perform(get(UNREGISTERED))
                .andExpect(status().isNotFound());
    }

    // ==================================================================
    // 5. 路径判据自检
    // ==================================================================

    @Test
    @DisplayName("路径判据：favorite 只有一段 AddRoutes（带 WithJwt）⇒ 无 public 路径，/favorite/v1/** 全受保护")
    void pathPredicateMatchesGoRouteSplit() {
        // Go: routes.go:16-59 只有一段 AddRoutes，带 rest.WithJwt ⇒ 7/7 受保护、无 public
        assertThat(FavoriteJwtAuthPaths.isProtected("/favorite/v1/folder/create")).isTrue();
        assertThat(FavoriteJwtAuthPaths.isProtected("/favorite/v1/item/list")).isTrue();
        assertThat(FavoriteJwtAuthPaths.isProtected("/favorite/v1")).isTrue();

        // 域外路径不碰
        assertThat(FavoriteJwtAuthPaths.isProtected("/actuator/health")).isFalse();
        assertThat(FavoriteJwtAuthPaths.isProtected(null)).isFalse();
        // ⚠️ 前缀相同但不是路径分隔的（如 /favorite/v10/...）不算受保护
        assertThat(FavoriteJwtAuthPaths.isProtected("/favorite/v10/x")).isFalse();
    }
}
