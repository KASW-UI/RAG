package com.rag.like.like.api;

import com.rag.like.like.api.auth.LikeJwtAuthFilter;
import com.rag.like.like.api.client.LikeRpcClient;
import com.rag.users.common.jwt.JwtUtil;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 🔴 <b>DRIFT-LIKEAPI-009②</b> 的守门：JWT <b>验签必须发生在 3s 预算之外</b>。
 *
 * <h2>Go 侧依据（真源，逐行）</h2>
 * <pre>
 *   service/like/api/likecenter.go:36-38
 *     httpTimeout := observability.DisableNativeRestTimeout(&c.RestConf)   // ← 关掉 go-zero 原生超时
 *     server := rest.MustNewServer(c.RestConf)
 *     server.Use(observability.NewHTTPMiddleware(c.Name, httpTimeout, observability.SlowThreshold()))
 *
 *   ⚠️ service/common/observability/http.go:30-34 ⇒ conf.Middlewares.Timeout = false
 *     ⇒ go-zero 原生 TimeoutHandler **不在链上**；3s 预算由**真源自己**的中间件提供
 *     ⇒ 而它注册在 server.Use ⇒ ng.middlewares
 *
 *   rest/engine.go:101-116  bindRoute：
 *     chn = ng.buildChainWithNativeMiddlewares(...)   // 原生中间件（索引 0 = 最外层）
 *     chn = ng.appendAuthHandler(fr, chn, verifier)   // ← JWT 验签
 *     for _, mw := range ng.middlewares { ... }        // ← observability(3s 计时) 在 JWT **之内**
 *     handle = chn.ThenFunc(route.Handler)
 *   rest/chain/chain.go:38-42  Append = 「加在请求流的最后」⇒ 越 Append 越靠内
 *   ⇒ 执行序：原生中间件 → **JWT 验签** → **observability(3s)** → handler
 *   ⇒ 3s 计时器在 Authorize **之后**起算 ⇒ **验签耗时不计入 3s 预算**
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
 *       ⇒ 证明验签真的发生在 interceptor 之前（即在 3s 预算之外）；
 *       且 401 的<b>形状</b>逐字不变（401 + <b>空 body</b>，Go {@code unauthorized()} 的逐字还原）。</li>
 *   <li><b>路径判据自检</b>：Go 侧 like api 6/6 全量 {@code WithJwt}、<b>无 public 路径</b>
 *       ⇒ 判据就是前缀本身。</li>
 * </ol>
 *
 * <h2>🔴 变异验证（本类不是假绿）</h2>
 * <pre>
 *   变异 1：把 {@code LikeJwtAuthFilter} 的 {@code @Order} 改成 +8（跑到 3s 预算之后）
 *         ⇒ 「JWT 在预算之外」那条断言红
 *   变异 2：让 {@code LikeJwtAuthFilter} 在验签失败时<b>自己写 401</b>
 *         ⇒ {@code LikeApiProtectionMvcTest.oversizedBodyIsRejectedBeforeAuth} 红
 *            （413 压过 401 的语义被打破）
 *   变异 3：摘掉 {@code LikeJwtAuthFilter} 的 {@code @Component}
 *         ⇒ 「interceptor 消费 filter 结果」那条断言红（源码会落进 "inline" 兜底）
 * </pre>
 */
@SpringBootTest(
        classes = LikeApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "grpc.client.like-rpc.address=static://127.0.0.1:65535",
                "grpc.client.like-rpc.negotiation-type=plaintext",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
@AutoConfigureMockMvc
@DisplayName("DRIFT-LIKEAPI-009②：JWT 验签在 3s 预算之外")
class LikeApiJwtVerificationScopeTest {

    /** 被测端点（Go: {@code rest.WithPrefix("/like/v1")} + {@code /getlikecount}）。 */
    private static final String PROTECTED_PATH = "/like/v1/getlikecount";

    /** 与 application.yml 的 {@code like-api.user-auth.access-secret} 同一份。 */
    private static final String SECRET = "ae0536f9-6450-4606-8e13-5a19ed505da0";
    private static final long EXPIRE_SEC = 3600L;

    /**
     * 3s 预算 Filter 的 <b>bean 名</b>（Spring 默认 bean 名 = 类名首字母小写）。
     *
     * <p>⚠️ <b>为什么按 bean 名定位，而不是 import 共享层的 {@code HttpMiddlewareFilter}</b>：
     * <ul>
     *   <li>本域的生产代码对共享层的这个 Filter <b>零引用</b> —— 它是被组件扫描
     *       （{@code scanBasePackages} 含 {@code com.rag.common}）<b>自动</b>挂上去的；
     *       域侧只是<b>断言它的位置</b>，并不使用它。</li>
     *   <li>⇒ 按 bean 名定位 ⇒ <b>不新增域侧对共享层的编译期依赖</b>
     *       ⇒ 共享层辐射度的事实底座（{@code tools/difftest/check-wave-coupling.py}）
     *       保持不变 ⇒ 不制造跨 lane 的快照漂移（那张快照归整合者，本 lane 无写权）。</li>
     * </ul>
     * ⇒ 若该 bean 被改名，本测试会<b>大声失败</b>（{@code NoSuchBeanDefinitionException}）。
     */
    private static final String BUDGET_FILTER_BEAN_NAME = "httpMiddlewareFilter";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext ctx;

    @MockitoBean
    private LikeRpcClient likeRpcClient;

    // ==================================================================
    // 1. 装配层
    // ==================================================================

    @Test
    @DisplayName("JWT 验签 Filter 真的注册进了本模块上下文")
    void jwtFilterIsRegistered() {
        assertThat(ctx.getBean(LikeJwtAuthFilter.class)).isNotNull();
    }

    // ==================================================================
    // 2. 顺序层（本条是核心）
    // ==================================================================

    @Test
    @DisplayName("🔴 容器装配出的链里，JWT Filter 排在 3s 预算（HttpMiddlewareFilter）之前 ⇒ 验签在预算之外")
    void jwtFilterRunsBeforeTheThreeSecondBudgetFilter() {
        List<ServletContextInitializer> filters = assembledFilters();

        int jwtIdx = indexOfFilter(filters, ctx.getBean(LikeJwtAuthFilter.class));
        int budgetIdx = indexOfFilter(filters, ctx.getBean(BUDGET_FILTER_BEAN_NAME, Filter.class));

        assertThat(jwtIdx)
                .as("JWT 验签 Filter 不在容器装配出的 Filter 链里")
                .isGreaterThanOrEqualTo(0);
        assertThat(budgetIdx)
                .as("3s 预算 Filter（HttpMiddlewareFilter）不在容器装配出的 Filter 链里")
                .isGreaterThanOrEqualTo(0);
        assertThat(jwtIdx)
                .as("JWT 验签 Filter 必须排在 3s 预算 Filter **之前**（数值小的先执行 ⇒ 包在外面）"
                        + "，否则验签仍被 3s 预算覆盖 ⇒ DRIFT-LIKEAPI-009② 不闭环。"
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
        var stub = mock(com.rag.like.like.proto.LikeServiceGrpc.LikeServiceBlockingStub.class);
        when(likeRpcClient.likeRpc()).thenReturn(stub);
        when(stub.getLikeCount(any())).thenReturn(
                com.rag.like.like.proto.GetLikeCountResp.getDefaultInstance());

        mockMvc.perform(post(PROTECTED_PATH)
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_type\":\"article\",\"target_ids\":[\"1\"]}"))
                .andExpect(request().attribute(
                        LikeJwtAuthFilter.VERIFICATION_SOURCE_ATTRIBUTE,
                        LikeJwtAuthFilter.SOURCE_FILTER));
    }

    @Test
    @DisplayName("401 的形状不变（401 + 空 body）—— Go unauthorized() 的逐字还原")
    void unauthorizedShapeIsUnchanged() throws Exception {
        mockMvc.perform(post(PROTECTED_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target_type\":\"article\",\"target_ids\":[\"1\"]}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));
    }

    // ==================================================================
    // 4. 路径判据自检
    // ==================================================================

    @Test
    @DisplayName("路径判据：like 域 6/6 全量 WithJwt ⇒ 只有前缀，无 public 路径")
    void pathPredicateMatchesGoRouteSplit() {
        // Go: rest.AddRoutes([...6 个路由...], rest.WithJwt(...), rest.WithPrefix("/like/v1"))
        //      ⇒ 6/6 全部要验签，**不存在** public 路径
        assertThat(LikeJwtAuthFilter.isProtected("/like/v1/getlikecount")).isTrue();
        assertThat(LikeJwtAuthFilter.isProtected("/like/v1/getlikestate")).isTrue();
        assertThat(LikeJwtAuthFilter.isProtected("/like/v1/gettargetlikerlist")).isTrue();
        assertThat(LikeJwtAuthFilter.isProtected("/like/v1/getuserlikelist")).isTrue();
        assertThat(LikeJwtAuthFilter.isProtected("/like/v1/getusertotallike")).isTrue();
        assertThat(LikeJwtAuthFilter.isProtected("/like/v1/likeaction")).isTrue();

        // 域外路径不碰
        assertThat(LikeJwtAuthFilter.isProtected("/actuator/health")).isFalse();
        assertThat(LikeJwtAuthFilter.isProtected("/like/v2/getlikecount")).isFalse();
        // ⚠️ 前缀相似但**不是**本域路由的路径（Spring 的 /like/v1/** 同样不匹配）
        assertThat(LikeJwtAuthFilter.isProtected("/like/v1abc")).isFalse();
    }

    private static String token() {
        return JwtUtil.getToken(SECRET, System.currentTimeMillis() / 1000L, EXPIRE_SEC, 1001L);
    }
}
