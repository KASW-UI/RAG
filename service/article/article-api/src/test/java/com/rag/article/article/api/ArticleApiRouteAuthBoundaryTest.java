package com.rag.article.article.api;

import com.rag.article.article.api.client.ArticleRpcClient;
import com.rag.article.article.api.client.UserRpcClient;
import com.rag.users.common.jwt.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 「<b>路由匹配先于鉴权</b>」与「<b>method 也参与鉴权判定</b>」这两条规则的守门。
 *
 * <h2>为什么这两条必须各守一次</h2>
 * <p>它们守的是<b>本模块自己写的代码</b>（{@code JwtAuthInterceptor} 的两条守卫），
 * 而不是共享实现。同一个 bug 在 message-api 被修好，不代表 article-api 不会犯 ——
 * article-api 还多了一个 message-api <b>没有</b>的东西：3 个公开端点。
 *
 * <h2>Go 侧依据</h2>
 * <pre>
 *   rest/engine.go:104-118   bindRoute 只把 chain（native + auth + 用户中间件）
 *                            挂到 router.Handle(method, path, handle) 上
 *                            ⇒ chain 只对**匹配上的路由**生效
 *   rest/router/patrouter.go:73   路径无匹配 → 404，**不进 chain**
 *   rest/router/patrouter.go:79   方法不匹配 → Allow + 405，**不进 chain**
 * </pre>
 * ⇒ Go 侧未注册路径 / 方法不匹配<b>恒</b> 404 / 405，<b>与有没有 token 无关</b>。
 *
 * <h2>⚠️ Spring 侧默认做不到</h2>
 * <p>Boot 的静态资源处理器匹配 {@code /**}，于是未注册路径<b>也</b>拿到了 handler，
 * {@code mappedHandler != null} ⇒ 拦截器照跑 ⇒ <b>404 被鉴权遮蔽成 401</b>。
 * 守卫是 {@code preHandle} 里的 {@code handler instanceof HandlerMethod}。
 *
 * <p>第二条守卫（method 也参与判定）同样必要：
 * {@code addPathPatterns} 只按路径匹配。若把 {@code /v1/article/{id}} 整条交给拦截器，
 * 则 {@code POST /v1/article/123}（Go 侧未注册 ⇒ 405）会变成 <b>401</b>。
 */
@SpringBootTest(
        classes = ArticleApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "grpc.client.article-rpc.address=static://127.0.0.1:65535",
                "grpc.client.article-rpc.negotiation-type=plaintext",
                "grpc.client.user-rpc.address=static://127.0.0.1:65535",
                "grpc.client.user-rpc.negotiation-type=plaintext",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN",
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
class ArticleApiRouteAuthBoundaryTest {

    private static final String SECRET = "ae0536f9-6450-4606-8e13-5a19ed505da0";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ArticleRpcClient articleRpcClient;

    @MockitoBean
    private UserRpcClient userRpcClient;

    // ============== 守卫 1：路由匹配先于鉴权 ==============

    @Test
    @DisplayName("未知路径 + **无 token** → 404（不是 401）：路由匹配先于鉴权")
    void unknownPathIs404WithoutToken() throws Exception {
        // ⚠️ 刻意不带 Authorization。缺了 handler instanceof HandlerMethod 守卫时，
        //    这里会返回 401 —— 未注册路径被鉴权遮蔽。
        mockMvc.perform(post("/v1/nope"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/v1/article/1/extra"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/nope"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("未知路径 404 的 body 是 Go 的 \"404 page not found\\n\" + text/plain + nosniff")
    void unknownPathUsesGoRouterShape() throws Exception {
        var response = mockMvc.perform(get("/v1/nope"))
                .andExpect(status().isNotFound())
                .andReturn().getResponse();

        assertEqualsTextPlainUtf8(response.getContentType());
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        org.junit.jupiter.api.Assertions.assertEquals("404 page not found\n",
                response.getContentAsString(),
                "Go rest/router/patrouter.go 的 404 body 是字面量 + 换行");
    }

    // ============== 守卫 2：method 也参与鉴权判定 ==============

    @Test
    @DisplayName("POST /v1/article/1（路径像受保护的，但 method 未注册）→ **404**，不是 401 也不是 405")
    void wrongMethodOnProtectedShapeIsGoNotFound() throws Exception {
        // 🔴 DRIFT-001（已修）：上一版这里断言 **405**，是事实错误。
        //
        // Go 侧证据链（注册方 → 分派方）：
        //   rest/server.go:150      WithCors() → SetNotAllowedHandler(cors.NotAllowedHandler(...))
        //   rest/router/patrouter.go:71-82  methodsAllowed 命中 → 走 pr.notAllowed，
        //                                     而 pr.notAllowed 已被上面换掉 ⇒ **不会**执行
        //                                     `Set("Allow")` + `WriteHeader(405)` 那两句
        //   rest/internal/cors/handlers.go:44-48  非 OPTIONS → gw.WriteHeader(404)
        //
        // article-api 是**四个 api 域里唯一**用 rest.WithCors() 的（message/like/hot 都是
        // 单参 MustNewServer），所以它们是 405、我们是 404 —— 不是本模块抄错了，
        // 是共享实现 GoStyleRouterErrorResponses.methodNotAllowed（405）**不适用**于本域。
        //
        // ⚠️ 刻意不带 Authorization：拦截器若只看路径不认 method，这里会变成 401。
        mockMvc.perform(post("/v1/article/1"))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist("Allow"));
    }

    @Test
    @DisplayName("PATCH /v1/article/1 与 PATCH /v1/articles → 404（Go 未注册 PATCH；WithCors 接管 notAllowed）")
    void patchIsNotRegisteredAnywhere() throws Exception {
        // 同 DRIFT-001：Go 侧全部是 404，不是 405。
        mockMvc.perform(patch("/v1/article/1")).andExpect(status().isNotFound());
        mockMvc.perform(patch("/v1/articles")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("方法不匹配的 404 是**空 body + 无 Content-Type**（Go cors.NotAllowedHandler 只 WriteHeader）")
    void methodNotFoundIsEmptyBody() throws Exception {
        // ⚠️ 与「路径未注册」的 404 是**两支不同形状**，Go 侧亦然：
        //   路径未注册     → http.NotFound      ⇒ 404 + "404 page not found\n" + text/plain + nosniff
        //   路径命中方法不对 → cors.NotAllowed  ⇒ 404 + **空 body** + 无 Content-Type
        var response = mockMvc.perform(post("/v1/article/1"))
                .andExpect(status().isNotFound())
                .andReturn().getResponse();

        org.junit.jupiter.api.Assertions.assertEquals("", response.getContentAsString(),
                "Go 的 cors.NotAllowedHandler 只 WriteHeader(404)，不写 body");
        org.junit.jupiter.api.Assertions.assertNull(response.getContentType(),
                "body 为空 ⇒ 容器不补 Content-Type（Go 侧也没有）");
        // 但 CORS 头仍然有：cors.NotAllowedHandler 先调 checkAndSetHeaders 再 WriteHeader
        assertEquals("*", response.getHeader("Access-Control-Allow-Origin"));
    }

    // ============== /v1/articles 不是 /v1/article 的前缀（否则公开端点会被误保护）==============

    @Test
    @DisplayName("GET /v1/articles 无 token ⇒ 放行到业务（不能被 /v1/article 的鉴权误伤）")
    void pluralPathIsNotProtectedBySingularRule() throws Exception {
        Mockito.when(articleRpcClient.articleRpc())
                .thenReturn(Mockito.mock(
                        com.rag.article.article.proto.ArticleServiceGrpc.ArticleServiceBlockingStub.class));
        mockMvc.perform(get("/v1/articles"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.code").value(5001));
    }

    @Test
    @DisplayName("GET /v1/article/1 无 token ⇒ 放行到业务（public 读详情）")
    void publicArticleDetailIsNotProtected() throws Exception {
        Mockito.when(articleRpcClient.articleRpc())
                .thenReturn(Mockito.mock(
                        com.rag.article.article.proto.ArticleServiceGrpc.ArticleServiceBlockingStub.class));
        // stub 未打桩 ⇒ getArticle 返回 null ⇒ Go 的 `res == nil` 分支 ⇒ 1002。
        // ⚠️ 期望值是 1002 而不是 5001：两个端点的「rpc 返回 nil」映射不同 ——
        //    详情读 → ErrorArticleNone(1002)，列表读 → ErrorServerCommon(5001)。
        //    （Go: get_article_logic.go 的 if res == nil 与 list_articles_logic.go 的
        //      if res == nil 各自写了不同的码。）
        mockMvc.perform(get("/v1/article/1"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.code").value(1002));
    }

    // ============== 路由表完整快照 ==============

    @Test
    @DisplayName("7 个端点的鉴权边界快照：4 个要 JWT、3 个公开")
    void authBoundarySnapshot() throws Exception {
        // 受保护：无 token ⇒ 401
        mockMvc.perform(post("/v1/article")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/v1/article/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/v1/article/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/me/article/1")).andExpect(status().isUnauthorized());

        // 公开：无 token 也能进业务（RPC stub 未打桩 ⇒ 落到 5001 而不是 401）
        var stub = Mockito.mock(
                com.rag.article.article.proto.ArticleServiceGrpc.ArticleServiceBlockingStub.class);
        // ⚠️ uploadFile 必须打桩：Mockito 默认返回 null，而 Go 侧
        //    `return &types.UploadImageResp{ImageUrl: rpcResp.FileUrl}` 在 nil 上会 panic
        //    ⇒ RAG 侧同样 NPE → handleAny → 裸 500（两侧一致，但测的不是我们想测的东西）。
        Mockito.when(stub.uploadFile(Mockito.any())).thenReturn(
                com.rag.article.article.proto.UploadFileResponse.newBuilder()
                        .setFileUrl("http://minio/a.png").build());
        Mockito.when(articleRpcClient.articleRpc()).thenReturn(stub);
        mockMvc.perform(get("/v1/article/1")).andExpect(status().isOk());
        mockMvc.perform(get("/v1/articles")).andExpect(status().isOk());
        mockMvc.perform(multipart("/v1/upload")
                        .file(new MockMultipartFile("image", "a.png", "image/png", new byte[]{1})))
                .andExpect(status().isOk());
    }

    // ============== DRIFT-004：path.Clean 归一化 ==============

    @Test
    @DisplayName("尾斜杠 / 重复斜杠按 Go path.Clean 归一（/v1/articles/ ⇒ 200，不是 404）")
    void trailingAndDuplicateSlashesAreNormalized() throws Exception {
        Mockito.when(articleRpcClient.articleRpc())
                .thenReturn(Mockito.mock(
                        com.rag.article.article.proto.ArticleServiceGrpc.ArticleServiceBlockingStub.class));

        // Go: patrouter.go:60 `reqPath := path.Clean(r.URL.Path)` ⇒ 尾斜杠/重复斜杠被吃掉
        mockMvc.perform(get("/v1/articles/")).andExpect(status().isOk());
        mockMvc.perform(get("/v1//articles")).andExpect(status().isOk());
        mockMvc.perform(get("/v1/articles/../articles")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("受保护路径的尾斜杠也归一，且**仍需鉴权**（归一后落在受保护表内）")
    void normalizedProtectedPathStillRequiresJwt() throws Exception {
        // 防回归：若归一化 Filter 与 JwtAuthInterceptor 看到的路径不一致，
        // 会出现「路由匹配到了却不鉴权」的越权。此处无 token ⇒ 必须 401。
        mockMvc.perform(post("/v1/article/")).andExpect(status().isUnauthorized());
    }

    // ============== CORS：Go rest.WithCors() 无参 ⇒ 通配 + OPTIONS 恒 204 ==============

    @Test
    @DisplayName("OPTIONS 任意路径（含未注册）⇒ 204 空 body + CORS 头（Go cors.Middleware 短路）")
    void optionsIsAlwaysNoContent() throws Exception {
        for (String path : new String[]{"/v1/article", "/v1/upload", "/v1/nonexistent"}) {
            var response = mockMvc.perform(options(path))
                    .andExpect(status().isNoContent())
                    .andReturn().getResponse();
            org.junit.jupiter.api.Assertions.assertEquals("", response.getContentAsString());
            assertEquals("*", response.getHeader("Access-Control-Allow-Origin"));
        }
    }

    @Test
    @DisplayName("业务响应带 Go cors.setHeader 的五个头，且**不**写 Allow-Credentials（origin 是 \"*\"）")
    void corsHeadersMatchGo() throws Exception {
        Mockito.when(articleRpcClient.articleRpc())
                .thenReturn(Mockito.mock(
                        com.rag.article.article.proto.ArticleServiceGrpc.ArticleServiceBlockingStub.class));
        var response = mockMvc.perform(get("/v1/articles"))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        assertEquals("*", response.getHeader("Access-Control-Allow-Origin"));
        assertEquals("GET, HEAD, POST, PATCH, PUT, DELETE", response.getHeader("Access-Control-Allow-Methods"));
        assertEquals("Content-Type, Origin, X-CSRF-Token, Authorization, AccessToken, Token, Range",
                response.getHeader("Access-Control-Allow-Headers"));
        assertEquals("Content-Length, Access-Control-Allow-Origin, Access-Control-Allow-Headers",
                response.getHeader("Access-Control-Expose-Headers"));
        assertEquals("86400", response.getHeader("Access-Control-Max-Age"));
        // Go setHeader 对 origin == "*" 的分支**不**写 Allow-Credentials
        org.junit.jupiter.api.Assertions.assertNull(response.getHeader("Access-Control-Allow-Credentials"));
    }

    @Test
    @DisplayName("未注册路径的 404 也带 CORS 头（Go corsRouter 包在 router **外面**）")
    void corsAppliesToNotFoundToo() throws Exception {
        var response = mockMvc.perform(get("/v1/nope"))
                .andExpect(status().isNotFound())
                .andReturn().getResponse();
        assertEquals("*", response.getHeader("Access-Control-Allow-Origin"),
                "CORS 必须在 Filter 层而非 addCorsMappings，否则未注册路径拿不到 CORS 头");
    }

    // ============== MaxBytes：10 MiB（不是共享默认的 1 MiB）==============

    @Test
    @DisplayName("MaxBytes 生效值是 **10485760**（Go article-api.yaml 覆盖了 tag 默认的 1 MiB）")
    void maxBytesIsTenMiB() {
        // 这条断言专门锁 application.yml 的 rag.protection.max-bytes。
        // 若漏配成共享默认值 1048576，写接口会突然开始返回裸 413 ——
        // 一个只在传大正文（>1 MiB）时才显形的漂移，压测和小请求都测不出来。
        var filter = context.getBean(com.rag.common.protection.GoStyleMaxBytesFilter.class);
        long effective = (long) org.springframework.test.util.ReflectionTestUtils.getField(filter, "maxBytes");
        assertEquals(10L * 1024 * 1024, effective,
                "Go: article-api.yaml MaxBytes=10485760；engine.checkedMaxBytes 对**全部**路由生效"
                        + "（route 级 WithMaxBytes 与 conf 级同值），不只是 /upload");
    }

    @Autowired
    private org.springframework.context.ApplicationContext context;

    private static void assertEqualsTextPlainUtf8(String contentType) {
        org.junit.jupiter.api.Assertions.assertTrue(
                contentType != null && contentType.startsWith("text/plain"),
                "Go http.Error 的 Content-Type 必须是 text/plain; charset=utf-8，实际=" + contentType);
    }

    private static void assertEquals(String expected, String actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }

    private static void assertEquals(String expected, String actual, String message) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual, message);
    }

    private static void assertEquals(long expected, long actual, String message) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual, message);
    }

    @Test
    @DisplayName("超限请求 ⇒ **裸** 413：无 body、**无 Content-Type**、无 nosniff（Go maxbyteshandler.go:22）")
    void oversizedBodyIsBare413() throws Exception {
        // 10 MiB + 1 字节。Content-Length 头存在 ⇒ Go 的 MaxBytesHandler 立刻 413。
        byte[] tooBig = new byte[10 * 1024 * 1024 + 1];
        var response = mockMvc.perform(post("/v1/article")
                        .header("Authorization", "Bearer " + JwtUtil.getToken(
                                SECRET, System.currentTimeMillis() / 1000L, 3600L, 1L))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(tooBig))
                .andExpect(status().isPayloadTooLarge())
                .andReturn().getResponse();

        // ⚠️ 413 与 400 的形状**不同**，别混：
        //   400（http.Error）      → text/plain + nosniff + 文案 + 换行
        //   413（MaxBytesHandler）  → **只有 WriteHeader(413)**，什么都没有
        //   依据：maxbyteshandler.go:19-22 只调 w.WriteHeader，没有 http.Error。
        org.junit.jupiter.api.Assertions.assertEquals("", response.getContentAsString(),
                "Go 的 413 不写 body");
        org.junit.jupiter.api.Assertions.assertNull(response.getContentType(),
                "Go 的 413 不设 Content-Type；Java 侧也必须刻意不 sendError（否则容器补 text/html）");
        org.junit.jupiter.api.Assertions.assertNull(response.getHeader("X-Content-Type-Options"),
                "Go 的 413 没有 nosniff（那是 http.Error 才加的）");
    }

    @Test
    @DisplayName("413 **压过** 401：MaxBytes 是 Filter，在 DispatcherServlet 之外（Go 链序同构）")
    void maxBytesBeatsJwt() throws Exception {
        byte[] tooBig = new byte[10 * 1024 * 1024 + 1];
        // ⚠️ 刻意**不带** token。Go 侧 MaxBytesHandler 在 chain 里位于 Authorize **之前**
        //    （engine.buildChainWithNativeMiddlewares 先 Append MaxBytes，
        //     appendAuthHandler 后 Append Authorize），⇒ 先 413。
        //    Java 侧同样是结构性的：Filter 在 DispatcherServlet 外，Interceptor 在内。
        mockMvc.perform(post("/v1/article")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(tooBig))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    @DisplayName("未超限时 MaxBytes 不干预（10 MiB 边界内放行到业务）")
    void withinLimitPassesThrough() throws Exception {
        Mockito.when(articleRpcClient.articleRpc())
                .thenReturn(Mockito.mock(
                        com.rag.article.article.proto.ArticleServiceGrpc.ArticleServiceBlockingStub.class));
        // 9 MiB：Go 侧 MaxBytes 放行，随后必填校验失败 ⇒ 400
        byte[] big = new byte[9 * 1024 * 1024];
        java.util.Arrays.fill(big, (byte) ' ');
        var response = mockMvc.perform(post("/v1/article")
                        .header("Authorization", "Bearer " + JwtUtil.getToken(
                                SECRET, System.currentTimeMillis() / 1000L, 3600L, 1L))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(big))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse();
        org.junit.jupiter.api.Assertions.assertNotNull(response.getContentAsString());
    }
}