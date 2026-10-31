package com.rag.article.article.api;

import com.rag.article.article.api.client.ArticleRpcClient;
import com.rag.article.article.api.client.UserRpcClient;
import com.rag.common.http.GoStyleJsonBodyConverter;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertNotNull(ctx.getBean(com.rag.common.protection.GoStyleGrpcClientBreakerInterceptor.class),
                "GoStyleGrpcClientBreakerInterceptor 必须被扫到（熔断层）");
        assertNotNull(ctx.getBean(com.rag.common.protection.GoStyleMaxConnsFilter.class), "GoStyleMaxConnsFilter 必须被扫到");
        assertNotNull(ctx.getBean(com.rag.common.protection.GoStyleBreakerFilter.class), "GoStyleBreakerFilter 必须被扫到");
        assertNotNull(ctx.getBean(com.rag.common.protection.GoStyleCpuSheddingFilter.class), "GoStyleCpuSheddingFilter 必须被扫到");
        assertNotNull(ctx.getBean(com.rag.common.spring.HttpMiddlewareFilter.class), "HttpMiddlewareFilter 必须被扫到");
        assertNotNull(ctx.getBean(com.rag.common.protection.GoStyleMaxBytesFilter.class), "GoStyleMaxBytesFilter 必须被扫到");
        assertNotNull(ctx.getBean(com.rag.common.http.GoStyleJsonBodyConverterConfig.class),
                "GoStyleJsonBodyConverterConfig 必须被扫到（WebMvcConfigurer）");
        // ⚠️ 转换器本身**不是 Bean**（它在 extendMessageConverters 里 new 出来）⇒
        //    断言「Bean 存在」测不到「它真被插进了 MVC 的转换器链」⇒ 必须查链。
        assertTrue(ctx.getBean(RequestMappingHandlerAdapter.class).getMessageConverters().stream()
                        .anyMatch(c -> c instanceof GoStyleJsonBodyConverter),
                "GoStyleJsonBodyConverter 必须真在 MVC 的 message converter 链里"
                        + "（漏了 ⇒ 非 JSON Content-Type 的 body 从 200 变 415）");
    }

    // =====================================================================
    // 2-bis. 出向 deadline 拦截器：**真进** devh 的 GlobalClientInterceptorRegistry
    //       （2026-11-02 · G3 lane 第十四轮 · 补 G4 lane 指出的本域缺口：
    //        其他四域都有这条装配面守门，article-api 独缺）
    // =====================================================================

    /**
     * 🔴 <b>装配面</b>：出向 deadline 拦截器（共享层）真被 devh 的
     * {@code GlobalClientInterceptorRegistry} 收进<b>收集名单</b>。
     *
     * <h2>⚠️ 为什么「Bean 存在」<b>不够</b>（AGENTS.md §3.3）</h2>
     * <p>devh 认的是<b>类上的注解</b>（{@code @GrpcGlobalClientInterceptor}），
     * 而真正的收集发生在 starter 的 {@code AnnotationGlobalClientInterceptorConfigurer}
     * （= {@code applicationContext.getBeansWithAnnotation(...)}）
     * ⇒ ⇒ 「上下文里有这个 Bean」**证不了**「它真被收集」
     * ⇒ ⇒ devh 的 {@code AbstractChannelFactory#createChannel} 最后一步是
     * {@code ClientInterceptors.interceptForward(channel, interceptors)}
     * ⇒ ⇒ <b>只有进了 registry 的那些</b>才会被装到 channel 上
     * ⇒ ⇒ 漏了 ⇒ <b>出向调用没有任何客户端硬上界</b>，而<b>不报错</b>。
     *
     * <h2>为什么这条对 article-api 特别要紧</h2>
     * <p>本域两个出向方向（{@code article-rpc} / {@code user-rpc}）的上界<b>全部</b>来自
     * 这个拦截器 ＋ 域 yml 的
     * {@code rag.protection.outbound-deadline.cap-millis-by-service} 分档表
     * ⇒ ⇒ 拦截器不在 registry ⇒ ⇒ <b>两个方向同时失去上界</b>。
     *
     * <h2>⚠️ 本类<b>刻意不</b> mock 任何东西</h2>
     * <p>所以这里的 registry 是<b>生产路径</b>上那一个（由 devh starter 的自动配置注册）
     * ⇒ ⇒ <b>不是</b>手工 new 的。
     */
    @Test
    @DisplayName("装配层：出向 deadline 拦截器真在 devh 的 GlobalClientInterceptorRegistry 名单里 ⇒ channel factory 会装配它")
    void outboundDeadlineInterceptorIsRegisteredWithTheStarter() {
        // ⚠️ **只保留这一条运行期判据**，不再另加「类上带 @GrpcGlobalClientInterceptor」的静态断言 ——
        //    原因：静态断言与本条是**同一个前提**（devh 的收集器按**类上的注解**收集）⇒ ⇒ 加了就是冗余；
        //    而**放在同一个测试方法里还会互相遮蔽**（前一条失败 ⇒ 后一条根本不执行 ⇒ ⇒ 变异命中不精确 ⇒
        //    读起来像「后一条不灵」，实则是**短路**）。
        //    ⇒ ⇒ 判据打在**真名单**上 ⇒ ⇒ 变异（把类上的收集注解摘掉）⇒ ⇒ 本条**必红**。
        net.devh.boot.grpc.client.interceptor.GlobalClientInterceptorRegistry registry =
                ctx.getBeanProvider(net.devh.boot.grpc.client.interceptor.GlobalClientInterceptorRegistry.class)
                        .getIfAvailable();
        assertNotNull(registry,
                "🔴 本域上下文里必须有 devh 的 GlobalClientInterceptorRegistry（由 devh starter 的自动配置注册）"
                        + "⇒ ⇒ 拿不到 ⇒ 说明 devh 客户端 starter 的装配面没起来 ⇒ ⇒ 本条判据失效");
        List<io.grpc.ClientInterceptor> collected = registry.getClientInterceptors();
        assertTrue(collected.stream()
                        .anyMatch(i -> i instanceof com.rag.common.protection.GoStyleOutboundDeadlineInterceptor),
                "🔴 devh 的 GlobalClientInterceptorRegistry 必须真收它（实测名单 = " + collected + "）"
                        + "⇒ ⇒ 否则它只是个 Bean，channel factory 不会装它 ⇒ ⇒ "
                        + "article-api 的两个出向方向（article-rpc / user-rpc）同时失去客户端硬上界。");
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

        // ②bis 🔴 2026-10-25 第十九轮（T-8）：**traceparent filter 的位置是「承重」的**，
        //        两条约束各有 Go 出处，缺一条就产生字节级差异：
        //   ① **CORS 必须在它之前** —— Go `rest/internal/cors/handlers.go:61-63` 的
        //      `cors.Middleware` 对 `OPTIONS` **短路**（写 204，**不调 next**）
        //      ⇒ 预检响应**不经过 router** ⇒ **不经过 TraceHandler**
        //      ⇒ **Go 的 204 预检响应没有 traceparent**。
        //      ⇒ 本类若排在 traceparent filter **之后** ⇒ 预检响应会**多带**一个 traceparent。
        //   ② **MaxConns 必须在它之后** —— Go `rest/engine.go:135` 的 `TraceHandler` 是
        //      native middleware 链**里的第一个**（MaxConns/Breaker/Shedding 在 `:146/:149/:152`）
        //      ⇒ 被它们拒成 503 的响应**也带** traceparent。
        //      ⇒ 本类若排在 MaxConns **之后** ⇒ 503 响应**丢头**。
        int traceparent = order.indexOf("GoStyleTraceparentFilter");
        assertTrue(traceparent >= 0,
                "共享层的 GoStyleTraceparentFilter 必须真在 Filter 链里"
                        + "（§3.3：Bean 存在 ≠ 进了链），实际=" + order);
        assertTrue(cors < traceparent,
                "CORS 必须排在 traceparent filter **之前**（Go 的 cors.Middleware 对 OPTIONS 短路 ⇒ "
                        + "Go 的 204 预检**没有** traceparent）；实际=" + order);
        assertTrue(traceparent < maxConns,
                "traceparent filter 必须排在 MaxConns **之前**（Go 的 TraceHandler 是 native 链第一个 ⇒ "
                        + "被拒成 503 的响应**也带** traceparent）；实际=" + order);

        // ②ter 🔴 2026-10-27（DRIFT-USERAPI-009②）：**JWT 验签 Filter 的两条承重约束**
        //        （这两条才是行为语义；它与 CpuShedding 的相对位置**不承重**，见下）
        //   ① **必须在 traceparent 之后** —— Go 侧 `TraceHandler`（`engine.go:135`）是 native
        //      链**第一个**，`Authorize`（`:108`）在其**内** ⇒ 验签发生在 SERVER span **之内**
        //      ⇒ RAG 侧验签 Filter 必须排在 `GoStyleTraceparentFilter` **之后**
        //      ⇒ 否则验签会跑在 span **之外**（span 里看不到这段耗时）。
        //   ② **必须在 70s 预算之前** —— Go 侧 observability(3s)（`:110-112`）在 `Authorize`
        //      **内层** ⇒ 计时器在验签**之后**起算 ⇒ RAG 侧验签 Filter 必须排在
        //      `HttpMiddlewareFilter` **之前** ⇒ 否则验签耗时会**被计入** 70s 预算。
        int jwtAuth = order.indexOf("GoStyleJwtAuthFilter");
        assertTrue(jwtAuth >= 0,
                "本域的 GoStyleJwtAuthFilter 必须真在 Filter 链里"
                        + "（§3.3：Bean 存在 ≠ 进了链），实际=" + order);
        assertTrue(traceparent < jwtAuth,
                "JWT 验签 Filter 必须排在 traceparent **之后**（Go 的 Authorize 在 TraceHandler **之内**"
                        + " ⇒ 验签耗时必须落进 SERVER span）；实际=" + order);
        assertTrue(jwtAuth < timeout,
                "JWT 验签 Filter 必须排在 70s 预算 Filter **之前**（Go 的 observability(3s) 在 Authorize "
                        + "**内层** ⇒ 计时器在验签之后起算 ⇒ 验签耗时不计入预算）；实际=" + order);

        // ③ 🔴 **完整链序**钉死（2026-10-24 真容器实测得出，2026-10-25 第十九轮插入 traceparent）
        // ⚠️ 2026-10-25 起**前两位不再同级**：CORS(+1) / path.Clean(+2) **各自显式取值**
        //    ⇒ 它们的相对顺序现在**由 @Order 表达**，不再依赖 Bean 注册序（此前是隐式依赖）。
        //    而 Go 侧这个相对顺序是明确的：**CORS 在外、path.Clean 在内**
        //    （server.go:148-153 的 WithCors 包装的是 router，patrouter.go:48,60 的 path.Clean 在 router 内部）
        //    ⇒ 实测结果与 Go **一致**，不是巧合。
        //
        // ⚠️ **2026-10-27（DRIFT-USERAPI-009②）新增 `GoStyleJwtAuthFilter`**：
        //    Go 侧 `rest/engine.go:108` 的 `Authorize(JWT)` 排在 `:110-112` 的 observability(3s)
        //    **之外**（更外层）⇒ 验签在 70s 预算**之外** ⇒ RAG 侧必须把验签 Filter 排在
        //    `HttpMiddlewareFilter` **之前** ⇒ 本列表里它在 Breaker 与 CpuShedding 之间。
        //    🔴 **它与 `GoStyleCpuSheddingFilter` 并列 +6**（共享层已占满 [+3,+6] 四槽
        //    ⇒ 合法窗口里没有别的空位）⇒ 两者的**相对位置由组件扫描顺序决定**
        //    （本域 `com.rag.article.article.api` 先于 `com.rag.common`）⇒
        //    ⚠️ **这条相对顺序【不承重】**：本 Filter **绝不写响应** ⇒ 与 CpuShedding 互换
        //    **不改变任何可观察行为** ⇒ 真正的承重约束只有「traceparent 之内、70s 预算之外」两条。
        assertEquals(List.of(
                        "ArticleGoStyleCorsFilter",
                        "GoStylePathNormalizingFilter",
                        "GoStyleTraceparentFilter",
                        "GoStyleMaxConnsFilter",
                        "GoStyleBreakerFilter",
                        "GoStyleJwtAuthFilter",
                        "GoStyleCpuSheddingFilter",
                        "HttpMiddlewareFilter",
                        "GoStyleMaxBytesFilter",
                        "GoStyleJsonBodyLimitFilter"),
                order,
                "真容器实测的 Filter 链序；Go 对应链序见方法 javadoc"
                        + "（⚠️ GoStyleJwtAuthFilter 与 GoStyleCpuSheddingFilter 并列 +6 ⇒ "
                        + "两者相对位置由扫描顺序决定、**不承重** ⇒ 若本条**只因这两个互换**而红，"
                        + "那是注册序变化、**不是行为回归**）");

        // ④ 🔴 2026-10-25 第十四轮：Go 的 query 解析与 form 值上限闸门**不再是 Filter**，
        //    而是 Spring MVC 的 **HandlerInterceptor**（共享层 `GoStyleFormValueLimitInterceptor`）。
        //    ⇒ 原因（Go 出处）：Go 的这个闸门在 **handler 内**（`httpx.Parse`）⇒ 其拒绝形状
        //    **按域而异**（注册了 `httpx.SetErrorHandler` 的域 ⇒ 200 + 业务信封；
        //    没注册的域 ⇒ 400 + Go 原文）⇒ 信封的文案在**域自己的错误码表**里
        //    ⇒ 共享层写不出来 ⇒ 必须把异常抛给域的 `@RestControllerAdvice`
        //    ⇒ 而 servlet Filter 抛的异常**到不了** advice（Filter 在 DispatcherServlet 之外）。
        //    ⇒ 故改用 Interceptor；它的顺序单独断言（见下）。
        List<String> interceptors = effectiveInterceptorOrder();
        assertTrue(interceptors.contains("GoStyleFormValueLimitInterceptor"),
                "form 值上限闸门必须真的进了 MVC 拦截器链（§3.3：Bean 存在 ≠ 进了链）；实际="
                        + interceptors);
        assertEquals("GoStyleFormValueLimitInterceptor", interceptors.get(0),
                "共享层闸门取 order=HIGHEST_PRECEDENCE ⇒ 必须排在各域自己的鉴权拦截器**之前**；实际="
                        + interceptors);
    }

    /** MVC 拦截器链的实际顺序（取自容器真正用的那个 {@code RequestMappingHandlerMapping}）。 */
    private List<String> effectiveInterceptorOrder() {
        List<String> names = new ArrayList<>();
        // ⚠️ Spring 7 里 `AbstractHandlerMapping.getInterceptors()` 已改名 `getAdaptedInterceptors()`
        //    （返回 `HandlerInterceptor[]`）⇒ 用旧名会编译不过。
        for (org.springframework.web.servlet.HandlerInterceptor i
                : ctx.getBean("requestMappingHandlerMapping",
                        org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping.class)
                        .getAdaptedInterceptors()) {
            names.add(i.getClass().getSimpleName());
        }
        return names;
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
     * ✅ 真 Tomcat：query 参数过多 ⇒ <b>400</b>，且 body 是 <b>Go 的 text/plain 形态</b>。
     *
     * <h2>🟥 本用例的断言在 2026-10-25（第十三轮「Tomcat 三旋钮」轮）被翻转</h2>
     * <p>改前（默认 8 KB 请求行上限）：2049 个最短非空 query 值（{@code a=1&} × 2049 ≈ 8196 字节）
     * <b>本身就超了 8 KB</b> ⇒ Tomcat 在<b>连接器层</b>就拒 ⇒ 请求<b>根本到不了</b> servlet filter
     * ⇒ 状态码 400（与 Go 一致）但 body 是 <b>Tomcat 的 HTML 错误页</b>。
     * <p>改后（{@code server.max-http-request-header-size: 1028KB} ＋
     * {@code server.tomcat.max-parameter-count: 2048}）：请求<b>能到达</b>
     * {@code GoStyleFormValueLimitInterceptor} ⇒ 该 filter 按 Go 的语义
     * （{@code httpx/util.go:44-53}：数<b>非空值</b>，第 2049 个即报错）
     * 产出 <b>Go 逐字相同的 body</b>：{@code "too many form values, error: " + r.Form.Encode()}。
     * <br>⇒ <b>状态码与 body 都与 Go 一致</b> ⇒ 原先登记的 body 形状差异
     * （{@code DRIFT-ARTICLE-API-005}）<b>随容器旋钮对齐而关闭</b>。
     * <p>⚠️ 本用例此前断言「body 含 {@code Bad Request}」（即<b>断言漂移存在</b>）——
     * 那是一条 characterization 断言，漂移被修掉后它**必然转红** ⇒ 本次按 §3.3
     * 「先改/删错误断言，再改实现」的顺序改写为<b>断言 Go 的形态</b>。
     */
    @Test
    @DisplayName("✅ 真 Tomcat：2049 个非空 query 参数 ⇒ **400 + Go 的 text/plain body**"
            + "（第十三轮容器旋钮对齐后，与 Go 逐字节一致）")
    void realTomcatRejectsTooManyQueryParamsWith400LikeGo() throws Exception {
        HttpResponse<String> resp = http.send(
                HttpRequest.newBuilder(URI.create(base() + "/v1/articles?" + goQuery(2049)))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(400, resp.statusCode(),
                "Go 侧 httpx/util.go:48-53 超限 ⇒ http.Error(400)");
        // ✅ 2026-10-25 第十三轮：请求能到达 GoStyleFormValueLimitInterceptor ⇒ body 是 Go 的形态。
        assertTrue(resp.body() != null && resp.body().startsWith("too many form values, error: a=1&a=1&"),
                "body 必须是 Go 的 `too many form values, error: <r.Form.Encode()>` 形态；实际="
                        + (resp.body() == null ? "<null>" : resp.body().substring(0,
                                Math.min(200, resp.body().length()))));
        // ⚠️ 反向断言：Tomcat 的 HTML 错误页**不得**再出现（那是这条漂移的现场特征）
        assertFalse(resp.body() != null && resp.body().contains("Bad Request"),
                "不得再出现 Tomcat 的 HTML 错误页（该差异已于第十三轮关闭）");
        assertTrue(resp.headers().firstValue("Content-Type").orElse("").startsWith("text/plain"),
                "Go 的 http.Error ⇒ text/plain；实际="
                        + resp.headers().firstValue("Content-Type").orElse("<none>"));
    }

    /**
     * ✅ 恰好 <b>2048</b> 个非空值 ⇒ <b>200</b>（与 Go 一致）。
     *
     * <h2>🟥 断言同样在 2026-10-25 第十三轮被翻转</h2>
     * <p>改前（默认 8 KB 请求行上限）：2048 × 4 字节 + 路径 ≈ 8204 &gt; 8192
     * ⇒ Tomcat 的连接器层上限<b>比 Go 的 2048 更早</b>触发 ⇒ 400
     * ⇒ 本用例当时的作用是「钉住『默认配置下应用层闸门不可达』这个事实」。
     * <p>改后：请求行上限 1028KB ⇒ 8204 字节<b>远在限内</b>
     * ⇒ 请求到达 {@code GoStyleFormValueLimitInterceptor}，其判据是
     * {@code nonEmpty > 2048} ⇒ <b>2048 不触发</b> ⇒ 放行 ⇒ <b>200</b>。
     * <br>⇒ 与 Go 的 {@code httpx.GetFormValues}（{@code if n < maxFormParamCount} ⇒
     * 第 2048 个仍被接受）<b>一致</b> ⇒ 应用层闸门<b>首次真正可达</b>，
     * 且阈值与 Go 相同。
     */
    @Test
    @DisplayName("✅ 真 Tomcat：**恰好 2048** 个非空 query 值 ⇒ **200**（与 Go 一致）"
            + "（第十三轮前是 400 —— Tomcat 的 8 KB 请求行上限先触发）")
    void realTomcatFormParamGateMatchesGoAtExactly2048() throws Exception {
        HttpResponse<String> resp = http.send(
                HttpRequest.newBuilder(URI.create(base() + "/v1/articles?" + goQuery(2048)))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode(),
                "Go 的 GetFormValues：第 2048 个非空值仍被接受（`n < maxFormParamCount` 才计数）⇒ 200；"
                        + "RAG 侧 GoStyleFormValueLimitInterceptor 的 `nonEmpty > 2048` 判据同样放行 ⇒ 两侧必须一致");
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

    // =====================================================================
    // 5. 第十九轮（T-8）：真 Tomcat 下「谁带 Traceparent、谁不带」
    // =====================================================================

    /**
     * 🔴 Go 侧事实：{@code rest/internal/cors/handlers.go:53-67} 的 {@code cors.Middleware}
     * 对 {@code OPTIONS} <b>短路</b>（{@code :61-63} 直接写 204，<b>不调 next</b>）
     * ⇒ 预检响应<b>根本不经过 router</b> ⇒ <b>不经过 TraceHandler</b>
     * ⇒ <b>Go 的 204 预检响应没有 traceparent</b>。
     *
     * <p>⇒ 这条断言钉的是「<b>CORS 必须在 traceparent filter 之前</b>」这条<b>承重</b>约束：
     * 顺序一旦反过来，预检响应就会<b>多带</b>一个 traceparent ⇒ 与 Go 不一致。
     */
    @Test
    @DisplayName("🔴 真 Tomcat：OPTIONS 预检 204 **不带** Traceparent（Go 的 cors.Middleware 短路 ⇒ 不经过 TraceHandler）")
    void optionsPreflightHasNoTraceparent() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(base() + "/v1/article"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(204, resp.statusCode(),
                "OPTIONS 预检必须是 204（CORS filter 短路，不下传）");
        assertTrue(resp.headers().firstValue("Traceparent").isEmpty(),
                "🔴 Go 的预检响应**没有** traceparent（cors.Middleware 短路 ⇒ 不经过 TraceHandler）；"
                        + "RAG 若带上 ⇒ 说明 traceparent filter 排在了 CORS **之前**（顺序被改坏了）。实际头="
                        + resp.headers().map());
    }

    /**
     * ✅ 共享层的 {@code GoStyleTraceparentFilter} 必须<b>真进了 api 的 Filter 链</b> ——
     * 「Bean 存在」≠「机制生效」（AGENTS.md §3.3）⇒ 这里看<b>客户端真正收到的那份字节</b>。
     */
    @Test
    @DisplayName("✅ 真 Tomcat：正常请求的响应**带** Traceparent（共享层 filter 真进了 api 的链，§3.3）")
    void normalResponseCarriesTraceparent() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(base() + "/v1/me/article/1"))
                .GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(401, resp.statusCode(),
                "本用例只关心响应头；缺 Authorization ⇒ 401（Go 侧同样是 401 且带 traceparent）");
        String traceparent = resp.headers().firstValue("Traceparent").orElse(null);
        assertNotNull(traceparent,
                "共享层的 GoStyleTraceparentFilter 必须真在 api 的 Filter 链里（§3.3）。实际头="
                        + resp.headers().map());
        assertTrue(traceparent.matches("^00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}$"),
                "Traceparent 必须是 W3C 的 00-<32hex traceid>-<16hex spanid>-<flags>。实际=" + traceparent);
    }

    /**
     * 🔴 <b>characterization test</b> —— 钉的是<b>实测现状</b>，不是 Go 的事实。
     *
     * <p><b>Go 侧事实</b>：{@code rest/router/patrouter.go:56-72} 的路由分发有<b>三个</b>出口：
     * <pre>
     *   ① 路由命中          → 路由链（TraceHandler 在 engine.go:135 ⇒ **带** traceparent）
     *   ② 路径都没注册      → handleNotFound → engine.notFoundHandler（engine.go:245-251，**也挂** TraceHandler）⇒ **带**
     *   ③ 路径命中、方法不对 → pr.notAllowed（= cors.NotAllowedHandler，**router 层**）
     *                          或 w.WriteHeader(405)（**router 层**）
     *                          ⇒ **两条都在 router 层，都不经过 TraceHandler** ⇒ **不带**
     * </pre>
     *
     * <p><b>RAG 侧</b>：③ 落在 {@code DispatcherServlet} <b>内</b>（{@code GlobalExceptionHandler}
     * 的 {@code handleMethodNotSupported}）⇒ filter 链<b>早已跑完</b>
     * ⇒ 共享层的 traceparent filter <b>已经注入过头</b> ⇒ <b>多带一个 {@code Traceparent}</b>。
     *
     * <p>⇒ 这是一条<b>已确认的残留字节级差异</b>（Go 侧无、RAG 侧有），
     * 根因是<b>出口所在层级不同</b>（router 层 vs DispatcherServlet 内）⇒ 共享层改不动。
     * ⇒ 本断言的作用是「<b>若哪天这里变了，说明行为又动过</b>」，<b>不是</b>把现状写成 Go 的形状。
     */
    @Test
    @DisplayName("🔴 真 Tomcat：方法不匹配（Go = 404 空 body）的响应**多带**一个 Traceparent —— 残留差异，characterization")
    void methodNotAllowedCarriesTraceparentUnlikeGo() throws Exception {
        // /v1/article 只注册了 POST（ArticleController:123）⇒ GET 它 = 「路径命中、方法不对」
        HttpRequest req = HttpRequest.newBuilder(URI.create(base() + "/v1/article")).GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(404, resp.statusCode(),
                "Go 侧 cors.NotAllowedHandler 对非 OPTIONS 写 404（article 的 DRIFT-001）");
        assertTrue(resp.body() == null || resp.body().isEmpty(),
                "Go 的 cors.NotAllowedHandler 只写状态码、body 为空；实际=" + resp.body());
        assertTrue(resp.headers().firstValue("Traceparent").isPresent(),
                "🔴 **实测现状**：RAG 的这个出口在 DispatcherServlet 内 ⇒ filter 链已跑完 ⇒ **多带** traceparent；"
                        + "Go 的同一出口在 router 层 ⇒ **不带**。这是已登记的残留差异（global 台账 T-12），"
                        + "本断言锁的是**现状**；若哪天这里变了，说明行为又动过。实际头=" + resp.headers().map());
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
