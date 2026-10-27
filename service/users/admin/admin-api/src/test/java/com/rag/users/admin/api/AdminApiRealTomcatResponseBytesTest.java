package com.rag.users.admin.api;

import com.rag.users.admin.api.client.AdminRpcClient;
import com.rag.users.admin.proto.AdminServiceGrpc;
import com.rag.users.admin.proto.LoginResp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 🔴🔴 <b>admin-api 真 Tomcat（RANDOM_PORT）响应字节级探针</b> ——
 * 闭合台账 {@code docs/DRIFT/users/admin/api.md} 的 <b>CASE-24</b>（真 Tomcat 下 400 的
 * Content-Type / nosniff）与 <b>CASE-26</b>（3s 超时路径的 503 + nosniff），
 * 并据此验证 <b>DRIFT-ADMINAPI-007</b>（503 超时响应缺 {@code X-Content-Type-Options: nosniff}）
 * <b>已因共享层修复而闭合</b>。
 *
 * <h2>为什么不能用 MockMvc（§3.3 的硬要求）</h2>
 * <p>CASE-24 在台账里被标成 <b>UNRESOLVED</b>，原因不是「没写测试」，而是
 * <b>已有的测试全是 MockMvc</b> ⇒ ⇒ MockMvc 的 {@code MockHttpServletResponse} 在
 * {@code setHeader} / {@code commit} 的时序上与 Tomcat 不同 ⇒ <b>MockMvc 里的断言对
 * 真实字节没有判别力</b>（台账 CASE-23 那一行的备注就是这个意思）。
 * <p>⇒ 本类用 <b>{@code webEnvironment = RANDOM_PORT}</b> 起<b>真 Tomcat</b>，
 * 再用 <b>真 {@link java.net.http.HttpClient}</b> 打过去 ⇒ 断言的是
 * <b>wire 上真正落地的字节</b>（不是容器内的模拟对象）。
 *
 * <h2>Go 出处（逐条到消费点，不是声明点）</h2>
 * <pre>
 *   【CASE-24 · 400 text/plain】
 *   Sea-RideTheWind_golang/service/user/admin/api/internal/handler/admin/createhandler.go:20-21
 *       if err := httpx.Parse(r, &amp;req); err != nil {
 *           httpx.ErrorCtx(r.Context(), w, err)
 *   go-zero@v1.10.2/rest/httpx/responses.go:36      → doHandleError(w, err, buildErrorHandler(ctx), ...)
 *   go-zero@v1.10.2/rest/httpx/responses.go:127-140  → buildErrorHandler 返回 nil
 *       （前提：service/user 全树**从未**调用 httpx.SetErrorHandler ⇒ 实测零命中）
 *   go-zero@v1.10.2/rest/httpx/responses.go:145-156  → handler == nil 分支
 *       http.Error(w, err.Error(), http.StatusBadRequest)
 *   ⇒ Go 标准库 net/http.Error 做四件事：
 *       ① Content-Type: text/plain; charset=utf-8
 *       ② X-Content-Type-Options: nosniff
 *       ③ WriteHeader(400)
 *       ④ fmt.Fprintln(w, err) ⇒ body 带一个尾随换行
 *
 *   【CASE-26 · 503 超时】
 *   Sea-RideTheWind_golang/service/common/observability/http.go:22  → httpTimeoutBody = "Request Timeout"
 *   Sea-RideTheWind_golang/service/common/observability/http.go:80  → case &lt;-ctx.Done():
 *   Sea-RideTheWind_golang/service/common/observability/http.go:83  → statusCode := http.StatusServiceUnavailable
 *   Sea-RideTheWind_golang/service/common/observability/http.go:88  → http.Error(w, httpTimeoutBody, statusCode)
 *   ⇒ 同一个 net/http.Error ⇒ 同款四件事 + body = "Request Timeout\n"（16 字节）
 * </pre>
 *
 * <h2>⚠️ 已知且<b>不可修</b>的残差（不是漂移，不按漂移修）</h2>
 * <p>Go 写的是 <b>原始字节</b> {@code "text/plain; charset=utf-8"}（分号后<b>有空格</b>）；
 * Tomcat 的 {@code setContentType} / {@code setHeader("Content-Type", …)} 会把 Content-Type
 * <b>解析成 MediaType 再序列化</b> ⇒ 序列化时<b>吃掉分号后的空格</b>
 * ⇒ 真实栈上是 {@code "text/plain;charset=utf-8"}。
 * <p>⇒ <b>归一化后（去空格 + 小写）逐字相同，原始字节不同</b> ⇒ 这是<b>容器渲染差异</b>，
 * 不是「RAG 少写了一个空格」⇒ Tomcat 上<b>没有任何写法</b>能产出那个空格。
 * ⇒ 故本类断言「<b>type 是 text/plain 且 charset 存在且等于 utf-8</b>」，
 * <b>不为那一个空格把测试判红</b>（§3.3：绿测试若去守一个不可达的目标，
 * 就是「错误答案的守卫」）。message-api 的同款守门（{@code MessageApiTimeoutRealTomcatTest}）
 * 已是同款处置。
 *
 * <h2>为什么只起 1 个 Spring 上下文 / 不起容器</h2>
 * <ul>
 *   <li>本类的 3 条用例共用<b>同一份</b> {@code @SpringBootTest} 配置 ⇒ Spring 的
 *       TestContext 缓存<b>复用同一个上下文</b> ⇒ 只起一次真 Tomcat。</li>
 *   <li><b>不起 Testcontainers</b>：本类只打<b>public</b> 路径（{@code /admin/v1/create}、
 *       {@code /admin/v1/login}）⇒ 黑名单查 Redis 只在「有 token」时才发生
 *       ⇒ 这里 {@link StringRedisTemplate} 直接打桩 ⇒ <b>零 Docker 依赖</b>
 *       ⇒ 既省一条容器启动（~2~5s），也躲开「宿主→容器端口转发」那类环境 flake。</li>
 * </ul>
 *
 * <h2>⚠️ 为什么超时用例打的是 <b>public</b> 路径</h2>
 * <p>{@code /admin/v1/login} 是 public 路径（台账 {@code AdminController} 的
 * 「============== public（无 JWT） ==============” 段）⇒ 不需要签 token。
 * ⇒ 而 <b>受保护</b> 路径会在鉴权层短路成 401 ⇒ <b>根本走不到慢调用</b> ⇒ 测不到超时。
 *
 * <h2>🔴 变异验证（本类不是假绿）</h2>
 * <pre>
 *   变异 A（目标断言 · CASE-26）：把共享层 {@code HttpMiddleware.writeTimeoutResponse} 的
 *         {@code resp.setHeader("X-Content-Type-Options", "nosniff")} 摘掉
 *         ⇒ 红在「nosniff 必须存在」那条断言上（不是红在前提上）
 *   变异 B（目标断言 · CASE-24）：把 admin-api 的 {@code GlobalExceptionHandler}
 *         里 {@code goStylePlainTextBadRequest} 的 Content-Type 改成不含 charset
 *         ⇒ 红在「charset 必须存在」那条断言上
 *   变异 C（前提断言）：把本类的慢 stub 改成不睡（= 提前失去超时条件）
 *         ⇒ 红在「状态码必须是 503」那条断言上 ⇒ 证明 503 是<b>超时触发</b>的，
 *           不是这条路径本来就会返回 503
 * </pre>
 * ⚠️ 变异 A / B 落在<b>共享层 / 域侧生产代码</b>上 ⇒ 由 G4 / 本域自行实施并回滚；
 * 本类只负责保证「摘掉之后一定红」。
 *
 * @see com.rag.users.admin.api.AdminApiPlainText400ContentTypeTest
 *      —— 同一条 400 契约的 <b>MockMvc 版</b>（本类补的是<b>真 Tomcat 版</b>）
 */
@SpringBootTest(
        classes = AdminApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "grpc.client.admin-rpc.address=static://127.0.0.1:65535",
                "grpc.client.admin-rpc.negotiation-type=plaintext",
                // ⚠️ 必须关掉 Nacos 自动注册 —— 否则 RANDOM_PORT（真起 web server）时
                //    Spring Cloud Alibaba 会在 web server 启动阶段注册 Nacos，
                //    而 127.0.0.1:1 连不上 ⇒ 注册抛异常 ⇒ **整个上下文起不来**
                //    （实测：Caused by ErrCode:-401, ErrMsg:Client not connected, current status:STARTING，
                //      at NacosServiceRegistry.register）。
                //    ⚠️ MOCK 环境不启动 web server ⇒ admin-api 现有的 MockMvc 测试**没踩到这个坑**
                //    ⇒ 这正是 admin-api 此前**没有 RANDOM_PORT 先例**的原因之一。
                //    （同款处置见 hot-api 的 HotApiRealTomcatGateTest。）
                //    ⇒ 这条只是**测试配置**；生产配置下的真实行为单独立档（DRIFT-HOTAPI-018）。
                "spring.cloud.service-registry.auto-registration.enabled=false",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
class AdminApiRealTomcatResponseBytesTest {

    /**
     * Go: httpTimeoutBody = "Request Timeout"（http.go:22）。
     *
     * <p>⚠️ 字节数别数错：{@code "Request Timeout"} 是 <b>15 个字符</b>（R-e-q-u-e-s-t + 空格 + T-i-m-e-o-u-t）
     * ⇒ 加上尾随换行 = <b>16 字节</b> ⇒ Go 的 net/http 算出的 Content-Length 是 <b>16</b>。
     */
    private static final String GO_TIMEOUT_BODY = "Request Timeout";

    /** {@code "Request Timeout".length() + 1} = 16 —— Go 的 net/http 按缓冲内容算出的 Content-Length。 */
    private static final int GO_TIMEOUT_CONTENT_LENGTH = GO_TIMEOUT_BODY.length() + 1;

    /**
     * Go 的「缺必填字段」400 body（<b>逐字节</b>）。
     *
     * <p>Go 出处：{@code go-zero@v1.10.2/core/mapping/unmarshaler.go:1199}
     * {@code fmt.Errorf("field %q is not set", name)}
     * ⇒ {@code field "username" is not set}，再由 {@code http.Error} 的 {@code fmt.Fprintln}
     * 补一个尾随换行 ⇒ 共 28 字节。
     */
    private static final String GO_MISSING_FIELD_BODY = "field \"username\" is not set\n";

    @LocalServerPort
    private int port;

    /**
     * 🔴 必须 mock 掉整个 {@link AdminRpcClient}。
     *
     * <p>admin-api 自 DRIFT-ADMINAPI-009 起有「3s 内连不上 admin-rpc ⇒ 启动失败」的检查
     * ⇒ 不 mock 掉它的话上下文<b>根本起不来</b>（被 mock 的 bean 不会执行
     * {@code afterPropertiesSet()} ⇒ 该检查被跳过；检查本身由
     * {@code AdminApiStartupFailFastTest} 单独守门）。
     */
    @MockitoBean
    private AdminRpcClient adminRpcClient;

    /**
     * 黑名单查询打桩（{@code hasKey → null} = 未拉黑）⇒ 毫秒级返回。
     *
     * <p>本类只打 public 路径 ⇒ 理论上根本不会查 Redis；打桩是为了让上下文
     * <b>确定不依赖</b>一个真 Redis（否则「起不来」会被误读成「被测行为坏了」）。
     */
    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    // ==================================================================
    // CASE-24：真 Tomcat 下 400 的 Content-Type / nosniff / 尾换行
    // ==================================================================

    @Test
    @DisplayName("🔴 CASE-24 真 Tomcat：400 text/plain 必须带 charset=utf-8 + nosniff + 尾随换行")
    void missingRequiredFieldYieldsGoPlainText400OnRealTomcat() throws Exception {
        // public 路径 + 空 body ⇒ 缺必填字段 ⇒ GoRequiredFieldException ⇒ 400 text/plain。
        // ⚠️ 不能用受保护路径（如 /admin/v1/getuser）：鉴权在请求体解析之前短路 ⇒ 拿到 401，
        //    根本走不到 text/plain 分支。
        HttpResponse<String> resp = post("/admin/v1/create", "{}");

        dump("CASE-24 /admin/v1/create {}", resp);

        assertEquals(400, resp.statusCode(),
                "Go: http.Error(w, err.Error(), http.StatusBadRequest) ⇒ 状态码必须是 400。"
                        + "实际=" + resp.statusCode() + " body=[" + resp.body() + "]");

        assertGoPlainText(resp,
                "Go 出处：go-zero@v1.10.2/rest/httpx/responses.go:155 的 http.Error(w, err.Error(), 400)"
                        + " ⇒ Content-Type 必须是 text/plain 且带 charset=utf-8。");

        assertEquals("nosniff", resp.headers().firstValue("x-content-type-options").orElse(null),
                "Go: net/http 的 http.Error 同时设 X-Content-Type-Options: nosniff（Go 1.11 起）。"
                        + "⚠️ 这一条正是 DRIFT-ADMINAPI-007 争的那一项 ⇒ 摘掉就红。实际头=" + resp.headers().map());

        assertTrue(resp.body() != null && resp.body().endsWith("\n"),
                "Go: http.Error 走 fmt.Fprintln ⇒ body 带一个尾随换行。实际 body=["
                        + (resp.body() == null ? "null" : resp.body().replace("\n", "\\n")) + "]");

        // ⚠️ 这一条**逐字节**断言 body —— 因为两侧的原文**确实逐字相同**（实测 wire 上 28 字节）：
        //   Go  : go-zero@v1.10.2/core/mapping/unmarshaler.go:1199 → fmt.Errorf("field %q is not set", name)
        //         ⇒ `field "username" is not set`，再由 http.Error 的 fmt.Fprintln 补一个 "\n" = 28 字节
        //   RAG : GoStyleRequired.field("username", …) 同款文案 ⇒ wire 上实测同这 28 字节
        // ⇒ 台账原先写「文本内容不对齐、也不试图对齐」——那是 **DRIFT-ADMINAPI-011 修复前**的状态；
        //    本条实测证明**两侧逐字节相同** ⇒ 把原文钉成守卫（比「以 \n 结尾」强得多）。
        assertEquals(GO_MISSING_FIELD_BODY, resp.body(),
                "Go 出处：go-zero@v1.10.2/core/mapping/unmarshaler.go:1199 的 "
                        + "fmt.Errorf(\"field %q is not set\", name) ⇒ body 必须逐字节等于 "
                        + "`field \"username\" is not set\\n`（28 字节）。实际 body=["
                        + (resp.body() == null ? "null" : resp.body().replace("\n", "\\n")) + "]");
    }

    // ==================================================================
    // CASE-26：真 Tomcat 下 3s 超时路径的 503 + nosniff
    // ==================================================================

    @Test
    @DisplayName("🔴 CASE-26 真 Tomcat：下游睡过 3s ⇒ 必须 503 + text/plain;charset=utf-8 + nosniff + \"Request Timeout\\n\"")
    void slowDownstreamYieldsGoTimeout503OnRealTomcat() throws Exception {
        // 让下游 gRPC 睡 5s（> observability.http-timeout: 3s）⇒ 把超时分支真正逼出来。
        makeDownstreamSlow(5000L);

        long t0 = System.nanoTime();
        HttpResponse<String> resp = post("/admin/v1/login", LOGIN_BODY);
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;

        dump("CASE-26 /admin/v1/login (slow " + elapsedMs + "ms)", resp);

        assertEquals(503, resp.statusCode(),
                "Go: service/common/observability/http.go:83 的 statusCode := http.StatusServiceUnavailable"
                        + " ⇒ 状态码必须是 503。实际=" + resp.statusCode() + " body=[" + resp.body() + "]"
                        + " 耗时=" + elapsedMs + "ms");

        assertGoPlainText(resp,
                "Go 出处：service/common/observability/http.go:88 的 http.Error(w, httpTimeoutBody, 503)"
                        + " ⇒ Content-Type 必须是 text/plain 且带 charset=utf-8。");

        assertEquals("nosniff", resp.headers().firstValue("x-content-type-options").orElse(null),
                "Go: net/http 的 http.Error 同时设 X-Content-Type-Options: nosniff。"
                        + "⚠️ 这一条正是 DRIFT-ADMINAPI-007 争的那一项 ⇒ 摘掉就红。实际头=" + resp.headers().map());

        assertEquals(GO_TIMEOUT_BODY + "\n", resp.body(),
                "Go: http.Error 写 body + 尾随换行 ⇒ 必须是 \"Request Timeout\\n\"。实际=["
                        + (resp.body() == null ? "null" : resp.body().replace("\n", "\\n")) + "]");

        // Go 的 net/http 收尾时按缓冲内容算长度 ⇒ 线上是 Content-Length: 15。
        // RAG 侧靠共享层 writeTimeoutResponse 里那句显式 setContentLength 对齐。
        String cl = resp.headers().firstValue("content-length").orElse(null);
        assertEquals(String.valueOf(GO_TIMEOUT_CONTENT_LENGTH), cl,
                "Go: net/http 按缓冲内容算长度 ⇒ Content-Length 应为 "
                        + GO_TIMEOUT_CONTENT_LENGTH + "（\"Request Timeout\" 15 字节 + 尾随换行 1 字节）。实际=" + cl);
    }

    // ==================================================================
    // 对照组：同一条路径 + 快下游 ⇒ 200 + application/json
    // ==================================================================

    @Test
    @DisplayName("✅ 对照组：同一路由 + 快下游 ⇒ 200 + application/json（证明 503 是超时触发的，不是别的短路）")
    void fastDownstreamStillReturns200JsonEnvelope() throws Exception {
        makeDownstreamSlow(0L);

        HttpResponse<String> resp = post("/admin/v1/login", LOGIN_BODY);

        dump("对照组 /admin/v1/login (fast)", resp);

        assertEquals(200, resp.statusCode(),
                "同一条路径、同一个 stub，只是不睡 ⇒ 必须正常返回；否则上一例证明不了「是超时触发的」。"
                        + "实际=" + resp.statusCode() + " body=[" + resp.body() + "]");

        String ct = resp.headers().firstValue("content-type").orElse(null);
        assertNotNull(ct, "正常路径必须带 Content-Type（envelope 是 JSON）");
        assertTrue(ct.startsWith("application/json"),
                "对照组证明「text/plain」是<b>分支特有</b>的，不是所有响应都那样。实际=" + ct);
    }

    // ==================================================================
    // 辅助
    // ==================================================================

    /** login 的合法 body（username / password 都是必填 ⇒ 不能缺）。 */
    private static final String LOGIN_BODY = "{\"username\":\"probe-admin\",\"password\":\"pw123456\"}";

    /**
     * 把下游 admin-rpc 的 {@code Login} 打桩成「睡 {@code millis} 毫秒」。
     *
     * <p>⚠️ 为什么必须是<b>先建立连接语义之外</b>的「慢服务端」而不是断连：
     * 本类要逼出的是 <b>服务端自己的 3s 预算</b>（{@code observability.http-timeout: 3s}），
     * 与客户端连不连得上无关 ⇒ ⇒ 用「慢」而不是「断」。
     */
    private void makeDownstreamSlow(long millis) {
        AdminServiceGrpc.AdminServiceBlockingStub stub =
                mock(AdminServiceGrpc.AdminServiceBlockingStub.class);
        when(stub.login(any())).thenAnswer(inv -> {
            if (millis > 0L) {
                Thread.sleep(millis);
            }
            return LoginResp.getDefaultInstance();
        });
        when(adminRpcClient.adminRpc()).thenReturn(stub);
    }

    /**
     * 断言「Go 的 {@code net/http.Error} 形状」——**归一化后**比，不逐字节比。
     *
     * <p>归一化 = 去空格 + 小写 ⇒ 只吃掉「Tomcat 重渲染会吃掉分号后空格」这一个已知差异。
     * ⚠️ charset 的<b>有无</b>仍然必须判出来（那才是 DRIFT-ADMINAPI-011 争的那一项）。
     */
    private static void assertGoPlainText(HttpResponse<String> resp, String why) {
        String ct = resp.headers().firstValue("content-type").orElse(null);
        assertNotNull(ct, why + " 实际：Content-Type 缺失。头=" + resp.headers().map());
        String normalized = ct.replace(" ", "").toLowerCase(java.util.Locale.ROOT);
        assertTrue(normalized.startsWith("text/plain"),
                why + " 实际 Content-Type=[" + ct + "]");
        assertTrue(normalized.contains("charset=utf-8"),
                why + " 实际 Content-Type=[" + ct + "]"
                        + " ⚠️ charset 必须存在（Go 的 net/http.Error 逐字写死 text/plain; charset=utf-8）。");
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    /** 把真实字节打到 stdout —— 台账要引的就是这份输出（AGENTS.md §3.3：结论要有运行证据）。 */
    private static void dump(String label, HttpResponse<String> resp) {
        System.out.println("======== admin-api 真 Tomcat 字节级取证 ========");
        System.out.println("case            = " + label);
        System.out.println("status          = " + resp.statusCode());
        System.out.println("content-type    = " + resp.headers().firstValue("content-type").orElse("<缺失>"));
        System.out.println("nosniff         = " + resp.headers().firstValue("x-content-type-options").orElse("<缺失>"));
        System.out.println("content-length  = " + resp.headers().firstValue("content-length").orElse("<缺失>"));
        System.out.println("body            = [" + (resp.body() == null ? "null" : resp.body().replace("\n", "\\n")) + "]");
        System.out.println("=============================================");
    }
}
