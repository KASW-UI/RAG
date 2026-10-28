package com.rag.message.message.api;

import com.rag.message.message.api.auth.MessageJwtAuthFilter;
import com.rag.message.message.api.client.MessageRpcClient;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 🔴 <b>DRIFT-MESSAGEAPI-009②</b> 的守门：JWT <b>验签必须发生在 3s 预算之外</b>。
 *
 * <h2>Go 侧依据（真源，逐行）</h2>
 * <pre>
 *   service/message/api/messagecenter.go:40-42
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
 * <h2>本域比 user/follow 域多一条：<b>两把 key</b></h2>
 * <p>Go 侧两段 {@code AddRoutes} 各自带一把 key（{@code routes.go:60-61} 用 UserAuth、
 * {@code routes.go:92-93} 用 AdminAuth）⇒ 验签 Filter 必须<b>按路径分派</b>。
 * 本类用「拿 user token 打 admin 端点 ⇒ 401」把这条分派判据钉住
 * （若 Filter 错用 user key，user token 会验签成功 ⇒ **不会** 401 ⇒ 这条会红）。
 *
 * <h2>本类断言的四件事</h2>
 * <ol>
 *   <li><b>装配层</b>：JWT Filter 真的注册进了本模块上下文；</li>
 *   <li><b>顺序层（本条是核心）</b>：<b>容器实际装配出的</b> Filter 链里，
 *       JWT Filter 排在 {@code HttpMiddlewareFilter}（3s 预算的起算点）<b>之前</b>
 *       ⇒ 验签在预算之外。
 *       ⚠️ 断言的是<b>装配结果</b>（{@code ServletContextInitializerBeans} 的排序输出），
 *       <b>不是</b>两个 {@code @Order} 注解的数字。</li>
 *   <li><b>行为层</b>：interceptor 确实<b>消费</b>了 Filter 的验签结果
 *       （{@code request().attribute(...)} = {@code "filter"}）；
 *       且 401 的<b>形状</b>逐字不变（401 + <b>空 body</b>）。</li>
 *   <li><b>路径判据自检</b>：Go 侧 message api 13 个端点 100% 带 {@code WithJwt}、
 *       <b>无 public 路径</b>；admin 前缀必须与 user 前缀<b>区分</b>。</li>
 * </ol>
 *
 * <h2>🔴 变异验证（本类不是假绿）</h2>
 * <pre>
 *   变异 1：把 {@code MessageJwtAuthFilter} 的 {@code @Order} 改成 +8（跑到 3s 预算之后）
 *         ⇒ 「JWT 在预算之外」那条断言红
 *   变异 2：让 {@code MessageJwtAuthFilter} 在验签失败时<b>自己写 401</b>
 *         ⇒ {@code MessageApiProtectionMvcTest.oversizedBodyIsRejectedBeforeAuth} 红
 *            （413 压过 401 的语义被打破）
 *   变异 3：摘掉 {@code MessageJwtAuthFilter} 的 {@code @Component}
 *         ⇒ 「interceptor 消费 filter 结果」那条断言红（源码会落进 "inline" 兜底）
 *   变异 4：把 {@code MessageJwtSignatureVerifier.verifyForPath} 的分派写成「一律用 user key」
 *         ⇒ 「admin 端点用 user token ⇒ 401」那条断言红
 * </pre>
 */
@SpringBootTest(
        classes = MessageApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "grpc.client.message-rpc.address=static://127.0.0.1:65535",
                "grpc.client.message-rpc.negotiation-type=plaintext",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
@AutoConfigureMockMvc
@DisplayName("DRIFT-MESSAGEAPI-009②：JWT 验签在 3s 预算之外")
class MessageApiJwtVerificationScopeTest {

    /** user 组被测端点（Go: {@code rest.WithPrefix("/message/v1")} + {@code /notifications/read}）。 */
    private static final String USER_PATH = "/message/v1/notifications/read";

    /** admin 组被测端点（Go: {@code rest.WithPrefix("/message/v1/admin")} + {@code /notifications/send}）。 */
    private static final String ADMIN_PATH = "/message/v1/admin/notifications/send";

    /** 与 application.yml 的 {@code message-api.user-auth.access-secret} 同一份。 */
    private static final String USER_SECRET = "ae0536f9-6450-4606-8e13-5a19ed505da0";
    /** 与 application.yml 的 {@code message-api.admin-auth.access-secret} 同一份（15 字节，需补齐）。 */
    private static final String ADMIN_SECRET = "yU-hCm)2*?NQmWG";
    private static final long EXPIRE_SEC = 3600L;
    private static final long CLAIM_UID = 31337L;

    /**
     * 3s 预算 Filter 的 <b>bean 名</b>（Spring 默认 bean 名 = 类名首字母小写）。
     *
     * <p>⚠️ <b>为什么按 bean 名定位，而不是 import 共享层的 {@code HttpMiddlewareFilter}</b>：
     * 本域的生产代码对共享层的这个 Filter <b>零引用</b> —— 它是被组件扫描<b>自动</b>挂上去的；
     * 域侧只是<b>断言它的位置</b> ⇒ 按 bean 名定位 ⇒ <b>不新增域侧对共享层的编译期依赖</b>
     * ⇒ 不制造跨 lane 的辐射度快照漂移（那张快照归整合者，本 lane 无写权）。
     */
    private static final String BUDGET_FILTER_BEAN_NAME = "httpMiddlewareFilter";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext ctx;

    @MockitoBean
    private MessageRpcClient rpcClient;

    // ==================================================================
    // 1. 装配层
    // ==================================================================

    @Test
    @DisplayName("JWT 验签 Filter 真的注册进了本模块上下文")
    void jwtFilterIsRegistered() {
        assertThat(ctx.getBean(MessageJwtAuthFilter.class)).isNotNull();
    }

    // ==================================================================
    // 2. 顺序层（本条是核心）
    // ==================================================================

    @Test
    @DisplayName("🔴 容器装配出的链里，JWT Filter 排在 3s 预算（HttpMiddlewareFilter）之前 ⇒ 验签在预算之外")
    void jwtFilterRunsBeforeTheThreeSecondBudgetFilter() {
        List<ServletContextInitializer> filters = assembledFilters();

        int jwtIdx = indexOfFilter(filters, ctx.getBean(MessageJwtAuthFilter.class));
        int budgetIdx = indexOfFilter(filters, ctx.getBean(BUDGET_FILTER_BEAN_NAME, Filter.class));

        assertThat(jwtIdx)
                .as("JWT 验签 Filter 不在容器装配出的 Filter 链里")
                .isGreaterThanOrEqualTo(0);
        assertThat(budgetIdx)
                .as("3s 预算 Filter（HttpMiddlewareFilter）不在容器装配出的 Filter 链里")
                .isGreaterThanOrEqualTo(0);
        assertThat(jwtIdx)
                .as("JWT 验签 Filter 必须排在 3s 预算 Filter **之前**（数值小的先执行 ⇒ 包在外面）"
                        + "，否则验签仍被 3s 预算覆盖 ⇒ DRIFT-MESSAGEAPI-009② 不闭环。"
                        + "⚠️ Go 侧：Authorize 在 observability(3s) **之外**（更外层）。")
                .isLessThan(budgetIdx);
    }

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
        mockMvc.perform(post(USER_PATH)
                        .header("Authorization", "Bearer " + userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(request().attribute(
                        MessageJwtAuthFilter.VERIFICATION_SOURCE_ATTRIBUTE,
                        MessageJwtAuthFilter.SOURCE_FILTER));
    }

    @Test
    @DisplayName("🔴 admin 组同理：Filter 的结果也被 admin 拦截器消费")
    void adminInterceptorConsumesTheFilterResult() throws Exception {
        mockMvc.perform(post(ADMIN_PATH)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(request().attribute(
                        MessageJwtAuthFilter.VERIFICATION_SOURCE_ATTRIBUTE,
                        MessageJwtAuthFilter.SOURCE_FILTER));
    }

    @Test
    @DisplayName("401 的形状不变（401 + 空 body）—— Go unauthorized() 的逐字还原")
    void unauthorizedShapeIsUnchanged() throws Exception {
        mockMvc.perform(post(USER_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("🔴 两把 key 互不通用：admin 端点用 user token ⇒ 401（证明 Filter 按路径分派了 key）")
    void adminPathRejectsUserToken() throws Exception {
        mockMvc.perform(post(ADMIN_PATH)
                        .header("Authorization", "Bearer " + userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ==================================================================
    // 4. 路径判据自检
    // ==================================================================

    @Test
    @DisplayName("路径判据：13 个端点 100% WithJwt、无 public 路径；admin 前缀必须与 user 前缀区分")
    void pathPredicateMatchesGoRouteSplit() {
        // Go: 两段 AddRoutes 都带 rest.WithJwt ⇒ 没有 public 路径
        assertThat(MessageJwtAuthFilter.isProtected("/message/v1/conversations/list")).isTrue();
        assertThat(MessageJwtAuthFilter.isProtected("/message/v1/unread")).isTrue();
        assertThat(MessageJwtAuthFilter.isProtected("/message/v1/admin/notifications/send")).isTrue();

        // 域外路径不碰
        assertThat(MessageJwtAuthFilter.isProtected("/actuator/health")).isFalse();
        assertThat(MessageJwtAuthFilter.isProtected("/message/v2/unread")).isFalse();
        assertThat(MessageJwtAuthFilter.isProtected("/message/v1abc")).isFalse();

        // admin 前缀判定（决定用哪把 key）
        assertThat(MessageJwtAuthFilter.isAdminPath("/message/v1/admin/notifications/send")).isTrue();
        assertThat(MessageJwtAuthFilter.isAdminPath("/message/v1/admin")).isTrue();
        assertThat(MessageJwtAuthFilter.isAdminPath("/message/v1/unread")).isFalse();
        // ⚠️ 前缀相似但**不是** admin 组（Spring 的 /message/v1/admin/** 同样不匹配）
        assertThat(MessageJwtAuthFilter.isAdminPath("/message/v1/administrator")).isFalse();
    }

    private static String userToken() {
        return JwtUtil.getToken(USER_SECRET, System.currentTimeMillis() / 1000L, EXPIRE_SEC, CLAIM_UID);
    }

    private static String adminToken() {
        return JwtUtil.getToken(ADMIN_SECRET, System.currentTimeMillis() / 1000L, EXPIRE_SEC, CLAIM_UID);
    }
}
