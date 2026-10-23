package com.rag.article.article.api;

import com.rag.article.article.api.client.ArticleRpcClient;
import com.rag.article.article.api.client.UserRpcClient;
import com.rag.common.http.GoStyleJsonBodyConverter;
import com.rag.common.http.GoStyleJsonBodyConverterConfig;
import com.rag.common.protection.GoStyleBreakerFilter;
import com.rag.common.protection.GoStyleCpuSheddingFilter;
import com.rag.common.protection.GoStyleGrpcClientBreakerInterceptor;
import com.rag.common.protection.GoStyleMaxBytesFilter;
import com.rag.common.protection.GoStyleMaxConnsFilter;
import com.rag.common.spring.HttpMiddlewareFilter;
import com.rag.users.common.jwt.JwtUtil;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * article-api 的<b>装配层</b>守门网 —— 关闭上一轮台账里的
 * {@code UNRESOLVED-001 / 003 / 004 / 005 / 006}。
 *
 * <h2>⚠️ 为什么这一类测试之前不存在</h2>
 * <p>本模块原有的 <b>60 条</b>测试全部是
 * {@code @SpringBootTest + @AutoConfigureMockMvc + @MockitoBean}，
 * 而 {@code @MockitoBean} 恰好把<b>要验证的那一段替换掉了</b>：
 * <ul>
 *   <li>{@code @GrpcClient} 的两个 stub 被 mock ⇒ <b>从没验证过它是否真被注入</b>；</li>
 *   <li>MockMvc <b>不经过真实 Tomcat</b> ⇒ 400/401/404 的响应头<b>从未被客户端看到过</b>。</li>
 * </ul>
 * ⇒ 这正是 AGENTS.md §3.3 点名的形态：「<b>注解存在 ≠ 机制生效</b>」。
 *
 * <h2>本类的四条断言与它们关闭的台账条目</h2>
 * <table border="1">
 *   <caption>断言 → 台账条目</caption>
 *   <tr><th>断言</th><th>关闭</th></tr>
 *   <tr><td>{@code @GrpcClient} 的两个 stub 真注入</td><td>{@code UNRESOLVED-004}</td></tr>
 *   <tr><td>共享层 {@code com.rag.common} 的 Bean 真在上下文里</td><td>{@code UNRESOLVED-006}</td></tr>
 *   <tr><td>Filter 的<b>实际生效顺序</b> == Go 的链序</td><td>{@code UNRESOLVED-003}</td></tr>
 *   <tr><td>真实 Tomcat 下 400/401/404 的响应头逐字节</td><td>{@code UNRESOLVED-001} / {@code -005}</td></tr>
 * </table>
 *
 * <h2>⚠️ 本类<b>刻意不</b> mock 任何东西</h2>
 * <p>gRPC channel 指向一个<b>必然连不上</b>的地址（{@code static://127.0.0.1:1}）——
 * 本类只验证<b>装配</b>，不发起任何调用。
 * <p>⚠️ 这也是 devh starter 的一个坑：{@code @GrpcClient} 在 <b>Bean 初始化期</b>就建 channel
 * （不是懒建）⇒ 测试环境没有 Nacos 时 {@code discovery://} 解析失败 ⇒ <b>上下文起不来</b>
 * ⇒ 必须覆盖成 {@code static://}。
 */
@SpringBootTest(
        classes = ArticleApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                // devh 的 @GrpcClient 在 Bean 初始化期建 channel ⇒ 必须指向可解析但不连通的地址
                "grpc.client.article-rpc.address=static://127.0.0.1:1",
                "grpc.client.article-rpc.negotiation-type=plaintext",
                "grpc.client.user-rpc.address=static://127.0.0.1:1",
                "grpc.client.user-rpc.negotiation-type=plaintext",
                // ⚠️ 必须整个关掉服务发现：Nacos 客户端连不上时 webServerStartStop 会直接失败
                // （"Client not connected, current status:STARTING"）⇒ 上下文起不来。
                // 本类只验证**装配**，两个 client 都指向 static:// 不走注册中心。
                "spring.cloud.discovery.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.discovery.register-enabled=false",
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "logging.level.com.alibaba.nacos=WARN",
                "management.otlp.tracing.export.enabled=false"
        })
class ArticleApiAssemblyWiringTest {

    private static final String SECRET = "ae0536f9-6450-4606-8e13-5a19ed505da0";

    @Autowired private ApplicationContext ctx;
    @Autowired private ArticleRpcClient articleRpcClient;
    @Autowired private UserRpcClient userRpcClient;

    /** Spring Boot 起的真实 Tomcat 端口（用 @Value 而不是 @LocalServerPort —— 后者的包在 Boot 4 里移位了）。 */
    @Value("${local.server.port}")
    private int port;

    /** ⚠️ 刻意用 **JDK 的 HttpClient**，不用任何 Spring 测试客户端 ——
     *  断言的必须是「客户端真正收到的那份字节」，绕开框架的任何再加工。 */
    private final HttpClient http = HttpClient.newHttpClient();

    private String base() {
        return "http://localhost:" + port;
    }

    // =====================================================================
    // 1. UNRESOLVED-004：@GrpcClient 是否真在容器装配
    // =====================================================================

    @Test
    @DisplayName("装配层：@GrpcClient(\"article-rpc\") / (\"user-rpc\") 的 stub **真被注入**（不是 null）")
    void grpcClientStubsAreReallyInjected() {
        // ⚠️ 修之前的 60 条测试用 @MockitoBean 把这两个 client 整个换掉了
        //    ⇒ 这一段「恰好把要验证的那一段替换掉了」（AGENTS.md §3.3）。
        assertNotNull(articleRpcClient.articleRpc(),
                "ArticleRpcClient 的 stub 必须是 devh 注入的真实 stub，不能是 null");
        assertNotNull(userRpcClient.userRpc(),
                "UserRpcClient 的 stub 必须是 devh 注入的真实 stub，不能是 null");
    }

    // =====================================================================
    // 2. UNRESOLVED-006：共享层 com.rag.common 是否真被扫到
    // =====================================================================

    @Test
    @DisplayName("装配层：共享层 com.rag.common 的 6 个保护件 Bean **真在上下文里**")
    void sharedCommonBeansAreReallyRegistered() {
        // ⚠️ ArticleApiApplication 的 scanBasePackages 含 "com.rag.common" ——
        //    漏掉它 ⇒ 保护层**静默消失**且**不报错**（AGENTS.md §3.3）。
        assertNotNull(ctx.getBean(GoStyleGrpcClientBreakerInterceptor.class),
                "GoStyleGrpcClientBreakerInterceptor 必须被扫到（熔断层）");
        assertNotNull(ctx.getBean(GoStyleMaxConnsFilter.class), "GoStyleMaxConnsFilter 必须被扫到");
        assertNotNull(ctx.getBean(GoStyleBreakerFilter.class), "GoStyleBreakerFilter 必须被扫到");
        assertNotNull(ctx.getBean(GoStyleCpuSheddingFilter.class), "GoStyleCpuSheddingFilter 必须被扫到");
        assertNotNull(ctx.getBean(HttpMiddlewareFilter.class), "HttpMiddlewareFilter 必须被扫到");
        assertNotNull(ctx.getBean(GoStyleMaxBytesFilter.class), "GoStyleMaxBytesFilter 必须被扫到");
        assertNotNull(ctx.getBean(GoStyleJsonBodyConverterConfig.class),
                "GoStyleJsonBodyConverterConfig 必须被扫到（WebMvcConfigurer）");
        // ⚠️ 转换器本身**不是 Bean**（它在 extendMessageConverters 里 new 出来）⇒
        //    断言「Bean 存在」测不到「它真被插进了 MVC 的转换器链」⇒ 必须查链。
        assertTrue(ctx.getBean(RequestMappingHandlerAdapter.class).getMessageConverters().stream()
                        .anyMatch(c -> c instanceof GoStyleJsonBodyConverter),
                "GoStyleJsonBodyConverter 必须真在 MVC 的 message converter 链里"
                        + "（漏了 ⇒ 非 JSON Content-Type 的 body 从 200 变 415）");
    }

    // =====================================================================
    // 3. UNRESOLVED-003：Filter 链的**实际生效顺序**
    // =====================================================================

    /**
     * Go 侧链序（go-zero v1.10.2 · {@code rest/engine.go:130-166} {@code buildChainWithNativeMiddlewares}）：
     * <pre>
     *   chn = chain.New()
     *   Trace → Log → Prometheus → MaxConns → Breaker → Shedding → Timeout → Recover → Metrics → MaxBytes → Gunzip
     *   （chain.Append 的顺序 = 由外到内）
     * </pre>
     * ⇒ 与本组件相关的子序列是
     * <b>MaxConns → Breaker → Shedding(CPU) → Timeout → MaxBytes</b>。
     * <p>而 CORS 与 {@code path.Clean} 在 Go 侧属于<b>路由层</b>
     * （{@code server.go:148-153} {@code WithCors} 包装的是 router；
     * {@code patrouter.go:48,60} 的 {@code path.Clean} 在 router 内部）
     * ⇒ 它们在<b>整条 middleware 链之外</b>。
     * <p>JSON body 8MiB 上限在 Go 侧是 {@code httpx/requests.go:21} 的
     * {@code maxBodyLen}，位于 {@code httpx.Parse} <b>内部</b>
     * ⇒ 而 {@code MaxBytes} 在链上<b>更外层</b> ⇒ RAG 侧
     * {@code MaxBytes} 必须<b>先于</b> {@code JsonBodyLimit}。
     */
    @Test
    @DisplayName("装配层：Filter 的实际生效顺序 == Go 的链序（MaxConns→Breaker→CPU→Timeout→MaxBytes→JsonLimit）")
    void effectiveFilterOrderMatchesGoChain() {
        List<String> order = effectiveFilterOrder();

        // ① Go 的链式保护层子序列（严格）
        int maxConns = order.indexOf("GoStyleMaxConnsFilter");
        int breaker = order.indexOf("GoStyleBreakerFilter");
        int shedding = order.indexOf("GoStyleCpuSheddingFilter");
        int timeout = order.indexOf("HttpMiddlewareFilter");
        int maxBytes = order.indexOf("GoStyleMaxBytesFilter");
        int jsonLimit = order.indexOf("GoStyleJsonBodyLimitFilter");

        assertTrue(maxConns >= 0 && breaker >= 0 && shedding >= 0
                        && timeout >= 0 && maxBytes >= 0 && jsonLimit >= 0,
                "6 个保护件都必须真在 Filter 链里，实际链序=" + order);
        assertTrue(maxConns < breaker && breaker < shedding && shedding < timeout
                        && timeout < maxBytes,
                "Go 的链序是 MaxConns→Breaker→Shedding→Timeout→MaxBytes（engine.go:130-166），实际=" + order);
        assertTrue(maxBytes < jsonLimit,
                "Go 的 MaxBytes 在 httpx.Parse **之外** ⇒ 必须先于 JSON body 上限生效，实际=" + order);

        // ② 路由层（CORS / path.Clean）在 Go 侧在整条 middleware 链**之外** ⇒ 必须在最前面
        int pathNormalizing = order.indexOf("GoStylePathNormalizingFilter");
        int cors = order.indexOf("ArticleGoStyleCorsFilter");
        assertTrue(pathNormalizing >= 0 && cors >= 0,
                "CORS 与 path.Clean 必须在链里，实际=" + order);
        assertTrue(pathNormalizing < maxConns && cors < maxConns,
                "Go 侧 CORS/path.Clean 属路由层，在整条 middleware 链之外 ⇒ 必须最先，实际=" + order);

        // ③ 🔴 **完整链序**钉死（2026-10-24 真容器实测得出）
        // ⚠️ 前两位同为 @Order(HIGHEST_PRECEDENCE) ⇒ 它们的**相对**顺序由 Bean 注册序决定，
        //    不是 @Order 表达的 ⇒ 这一条是「**当前实际**链序」的回归网，不是 @Order 的推论。
        //    而 Go 侧这个相对顺序是明确的：**CORS 在外、path.Clean 在内**
        //    （server.go:148-153 的 WithCors 包装的是 router，patrouter.go:48,60 的 path.Clean 在 router 内部）
        //    ⇒ 实测结果与 Go **一致**，不是巧合。
        assertEquals(List.of(
                        "ArticleGoStyleCorsFilter",
                        "GoStylePathNormalizingFilter",
                        "GoStyleMaxConnsFilter",
                        "GoStyleBreakerFilter",
                        "GoStyleCpuSheddingFilter",
                        "HttpMiddlewareFilter",
                        "GoStyleMaxBytesFilter",
                        "GoStyleJsonBodyLimitFilter",
                        // 🔴 2026-10-24 新增：Go 的 query 参数总数上限（httpx/util.go 的 2048）
                        //    在 Go 侧位于**路由 handler 内**（httpx.Parse）⇒ 即所有中间件之后
                        //    ⇒ Java 侧放在**最后一个** filter 位（紧贴 controller）最贴近 Go 的位置。
                        "GoStyleFormParamCountFilter"),
                order,
                "真容器实测的 Filter 链序；Go 对应链序见方法 javadoc");
    }

    /** 按 Spring Boot 自己用的排序规则排出 Filter 链的实际顺序。 */
    private List<String> effectiveFilterOrder() {
        Map<String, Filter> beans = ctx.getBeansOfType(Filter.class);
        List<Filter> filters = new ArrayList<>(beans.values());
        // ⚠️ 必须是 **AnnotationAwareOrderComparator**（认 @Order），
        //    不能用 org.springframework.core.OrderComparator（只认 Ordered 接口）
        //    —— 用错会让排序完全失效（第一版就踩了：链序看起来是乱的）。
        // Spring Boot 的 ServletContextInitializerBeans 用的就是它；
        // 它是**稳定**排序 ⇒ 同 @Order 的按 Bean 注册顺序 ⇒ 与容器实际链序一致。
        filters.sort(AnnotationAwareOrderComparator.INSTANCE);
        List<String> names = new ArrayList<>();
        for (Filter f : filters) {
            String simple = f.getClass().getSimpleName();
            if (simple.startsWith("GoStyle") || simple.startsWith("ArticleGoStyle")
                    || simple.startsWith("HttpMiddleware")) {
                names.add(simple);
            }
        }
        return names;
    }

    // =====================================================================
    // 4. UNRESOLVED-001 / -005：真实 Tomcat 下 400/401/404 的响应头
    // =====================================================================

    @Test
    @DisplayName("真 Tomcat：400 的 Content-Type **实测** = \"text/plain;charset=utf-8\"（**无空格**）—— Go 是带空格的 ⇒ 已确认 LOW 漂移")
    void realTomcatBadRequestHeadersAreGoShaped() throws Exception {
        String token = JwtUtil.getToken(SECRET, System.currentTimeMillis() / 1000L, 3600L, 1001L);
        HttpRequest req = HttpRequest.newBuilder(URI.create(base() + "/v1/article"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString("{\"title\":"))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(400, resp.statusCode(), "畸形 JSON 必须是 400");
        // 🔴 **已确认的 LOW 漂移**（本轮由真 Tomcat 首次实测确认，详见台账 UNRESOLVED-001）：
        //   Go 的 net/http.Error 写的是字面量 "text/plain; charset=utf-8"（**分号后有空格**）；
        //   RAG 实测是 "text/plain;charset=utf-8"（**无空格**）。
        //   成因：Spring 的 ServletServerHttpResponse.writeHeaders() 在
        //   servletResponse.getContentType()==null 时会调 setContentType(mediaType.toString())，
        //   而 **Java 的 MediaType.toString() 不在分号后加空格** ⇒ 原始 header 被覆盖。
        //   ⚠️ 上游只在一处绕过了它（GoStyleJsonBodyLimitFilter 用 setHeader），
        //   **异常处理这条路（ResponseEntity → Spring 写响应）没绕过**。
        //   ⇒ 本断言锁的是**实测现状**（characterization test），
        //     它的 @DisplayName 已明说「与 Go 差一个空格」，不是把 RAG 现状写成 Go 的事实。
        assertEquals("text/plain;charset=utf-8", resp.headers().firstValue("Content-Type").orElse(null),
                "⚠️ 实测现状是**无空格**版（与 Go 的 \"text/plain; charset=utf-8\" 差一个空格）"
                        + "；这是已登记的 LOW 漂移，不是 Go 的形状 —— 若这里变了说明行为又动过");
        assertEquals("nosniff", resp.headers().firstValue("X-Content-Type-Options").orElse(null),
                "Go http.Error 必须带 X-Content-Type-Options: nosniff");
    }

    @Test
    @DisplayName("真 Tomcat：401 的 body 是**空的**且**没有** Content-Type（Go 只 WriteHeader(401)）")
    void realTomcatUnauthorizedHasEmptyBodyAndNoContentType() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(base() + "/v1/me/article/1"))
                .GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(401, resp.statusCode(), "缺 Authorization ⇒ 401");
        assertTrue(resp.body() == null || resp.body().isEmpty(),
                "Go authhandler.go:106-122 只写状态码，body 为空；实际=" + resp.body());
        assertNull(resp.headers().firstValue("Content-Type").orElse(null),
                "空 body 的响应不应带 Content-Type（多一项就与 Go 不一致）");
    }

    @Test
    @DisplayName("真 Tomcat：未注册路径 404 的 body = \"404 page not found\\n\" + text/plain + nosniff")
    void realTomcatNotFoundUsesGoRouterShape() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(base() + "/v1/nope"))
                .GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(404, resp.statusCode());
        assertEquals("404 page not found\n", resp.body(),
                "Go rest/router/patrouter.go:73 的 404 body 是字面量 + 换行");
        // ⚠️ 同上：实测是**无空格**版（Go 是 "text/plain; charset=utf-8"）⇒ 已登记的 LOW 漂移
        assertEquals("text/plain;charset=utf-8", resp.headers().firstValue("Content-Type").orElse(null),
                "⚠️ 实测现状是**无空格**版（Go 的 http.NotFound 走 http.Error ⇒ 带空格）");
        assertEquals("nosniff", resp.headers().firstValue("X-Content-Type-Options").orElse(null));
    }

    @Test
    @DisplayName("真 Tomcat：方法不匹配 ⇒ **404 空 body**（Go: WithCors 接管 NotAllowedHandler，不是 405）")
    void realTomcatMethodMismatchIsEmptyBodyNotFound() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(base() + "/v1/article/1"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(404, resp.statusCode(),
                "Go 侧全部是 404（不是 405）：patrouter.go:77-78 + cors/handlers.go:44-48");
        assertTrue(resp.body() == null || resp.body().isEmpty(),
                "Go 的 cors.NotAllowedHandler 不写 body；实际=" + resp.body());
    }

    // =====================================================================
    // 9. DRIFT-005：query 参数总数上限（Go: httpx/util.go 的 maxFormParamCount = 2048）
    // =====================================================================

    /**
     * 🔴 真 Tomcat（<b>默认 8 KB 请求行上限</b>）：query 参数过多 ⇒ <b>400</b>。
     *
     * <h2>🔴 本用例推翻台账的一条前提（2026-10-24 真容器实测）</h2>
     * 台账原写「RAG <b>无上限</b>」⇒ <b>不成立</b>：
     * Tomcat 自己有请求行/头部长度上限（默认 8 KB），
     * 而 2049 个<b>最短</b>的非空 query 值（{@code a=1&} × 2049 ≈ 8196 字节）
     * <b>本身就超了 8 KB</b> ⇒ Tomcat 在<b>连接器层</b>就拒掉，请求<b>根本到不了</b> servlet filter。
     * <br>⇒ 实测：状态码 <b>400</b>（<b>与 Go 一致</b>），但 body 是 <b>Tomcat 的 HTML 错误页</b>，
     * 而 Go 是 {@code http.Error} 的 text/plain + {@code "too many form values, error: …"}。
     * <br>⇒ 真正的差异是<b>响应体形状</b>（以及阈值：RAG 的 8 KB 更严 ≈ 2040 个值 < Go 的 2049），
     * <b>不是</b>「无上限」⇒ 台账的定性已更正。
     *
     * <h2>本用例断言的是「客户端真正收到的那份」</h2>
     * ⇒ 状态码必须与 Go 一致（400）；body 的差异<b>如实断言为现状</b>（characterization），
     * 并在 {@code @DisplayName} 里写明与 Go 差在哪。
     */
    @Test
    @DisplayName("🔴 真 Tomcat（默认 8 KB 请求行上限）：query 参数过多 ⇒ **400**；"
            + "⚠️ body 是 Tomcat 的 HTML 页（Go 是 text/plain + too many form values）")
    void realTomcatRejectsTooManyQueryParamsWith400LikeGo() throws Exception {
        HttpResponse<String> resp = http.send(
                HttpRequest.newBuilder(URI.create(base() + "/v1/articles?" + goQuery(2049)))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(400, resp.statusCode(),
                "Go 侧 httpx/util.go:48-53 超限 ⇒ http.Error(400)；"
                        + "RAG 侧由 Tomcat 的请求行上限先拒 ⇒ 状态码同样应是 400");
        // ⚠️ characterization：默认配置下拒绝发生在**连接器层**，body 是 Tomcat 的 HTML 错误页。
        //    Go 的 body 是 text/plain 的 "too many form values, error: <r.Form.Encode()>"。
        //    ⇒ 这一条差异已登记（DRIFT-ARTICLE-API-005），**不修**：
        //    要把它改掉就得动 Tomcat 的错误页产出（ErrorReportValve），风险远大于收益。
        assertTrue(resp.body() != null && resp.body().contains("Bad Request"),
                "默认配置下 body 是 Tomcat 的 HTML 错误页（已登记的 body 形状差异）；实际="
                        + (resp.body() == null ? "<null>" : resp.body().substring(0,
                                Math.min(200, resp.body().length()))));
    }

    /**
     * 🔴 恰好 2048 个非空值：在<b>默认</b>配置下<b>仍然</b>是 400 ——
     * 因为 Tomcat 的 8 KB 请求行上限比 Go 的 2048 个值**更早**触发
     * （2048 × 4 字节 + 路径 ≈ 8204 > 8192）。
     *
     * <p>⇒ 这条用例的作用是<b>钉住"默认配置下这条闸门不可达"</b>这个事实，
     * 防止下一个人误以为"应用层闸门已经在拦"。
     */
    @Test
    @DisplayName("真 Tomcat（默认 8 KB 上限）：**恰好 2048** 个非空 query 值 ⇒ 也是 400"
            + "（Tomcat 的请求行上限比 Go 的 2048 更早触发 ⇒ 应用层闸门在默认配置下不可达）")
    void realTomcatDefaultHeaderLimitFiresBeforeTheFormParamGate() throws Exception {
        HttpResponse<String> resp = http.send(
                HttpRequest.newBuilder(URI.create(base() + "/v1/articles?" + goQuery(2048)))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(400, resp.statusCode(),
                "默认配置下 Tomcat 的 8 KB 请求行上限先触发 ⇒ 400（与 Go 的状态码一致，body 不同）");
    }

    /** 🔴 Go 只数<b>非空</b>值（{@code if len(v) == 0 { continue }}）⇒ 空值再多也不触发。 */
    @Test
    @DisplayName("真 Tomcat：3000 个**空值** query 参数 ⇒ **不**被这条上限拒掉（Go 不计空值）")
    void realTomcatIgnoresEmptyQueryValues() throws Exception {
        HttpResponse<String> resp = http.send(
                HttpRequest.newBuilder(URI.create(base() + "/v1/articles?" + emptyGoQuery(3000)))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertNotEquals(400, resp.statusCode(),
                "Go 的 GetFormValues 对空值 `continue`（不计数）⇒ 空值再多也不该被拒；"
                        + "实测=" + resp.statusCode());
    }

    /** 造 N 个非空 query 值（{@code a=1&a=1&…}，最短字节 ⇒ 尽量不撞 Tomcat 的请求行上限）。 */
    private static String goQuery(int n) {
        StringBuilder sb = new StringBuilder(n * 4);
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append('&');
            }
            sb.append("a=1");
        }
        return sb.toString();
    }

    /** 造 N 个<b>空值</b> query 参数（{@code a&a&…} ⇒ Go 的 value 为空 ⇒ 不计数）。 */
    private static String emptyGoQuery(int n) {
        StringBuilder sb = new StringBuilder(n * 2);
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append('&');
            }
            sb.append('a');
        }
        return sb.toString();
    }
}
