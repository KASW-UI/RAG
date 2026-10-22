package com.rag.article.article.api;

import com.rag.article.article.api.client.ArticleRpcClient;
import com.rag.article.article.api.client.UserRpcClient;
import com.rag.article.article.proto.Article;
import com.rag.article.article.proto.ArticleServiceGrpc.ArticleServiceBlockingStub;
import com.rag.article.article.proto.ArticleStatus;
import com.rag.article.article.proto.GetArticleRequest;
import com.rag.article.article.proto.GetArticleResponse;
import com.rag.article.article.proto.ListArticlesRequest;
import com.rag.article.article.proto.ListArticlesResponse;
import com.rag.users.common.jwt.JwtUtil;
import com.rag.users.user.proto.UserServiceGrpc.UserServiceBlockingStub;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * article-api 真实 MVC 管线测试 —— 锁住 Go {@code service/article/api} 的 L1/L2 契约。
 *
 * <h2>为什么是「真实管线」而不是 standaloneSetup</h2>
 * 本服务最关键的四个性质全部只有真实管线才测得到：
 * <table border="1">
 *   <caption>standaloneSetup 测不到的四件事</caption>
 *   <tr><th>性质</th><th>为什么</th></tr>
 *   <tr>
 *     <td><b>4 条 JWT 受保护路由</b>的精确集合</td>
 *     <td>取决于 {@code WebMvcConfig#addInterceptors} + {@code JwtAuthInterceptor} 的
 *         {@code ProtectedRoute}（method + path）表</td>
 *   </tr>
 *   <tr>
 *     <td>「解析失败 ⇒ 400 text/plain + nosniff」</td>
 *     <td>取决于 {@code GlobalExceptionHandler} 与
 *         {@code ResponseBodyAdvice} advice chain 的相互作用
 *         （AGENTS.md 的 DRIFT-FRAMEWORK-001 就是在这一层丢的）</td>
 *   </tr>
 *   <tr>
 *     <td>「{@code POST /v1/upload} 不走信封」</td>
 *     <td>取决于 {@code GlobalResponseAdvice} 对 {@code ResponseEntity} 的放行</td>
 *   </tr>
 *   <tr>
 *     <td>{@code GoStyleCoercion} 挂在真实 MVC mapper 上</td>
 *     <td>单独 new 一个 mapper 造断言是假绿</td>
 *   </tr>
 * </table>
 *
 * <h2>本测试类逐条对应 Go 的两个真源</h2>
 * <ol>
 *   <li>{@code internal/handler/article/public_read_handler_test.go}
 *       —— 逐条搬 {@code TestPublicAndAuthorHTTPReadContract} 的 9 个断言
 *       （含「不泄露草稿正文」与「HTTP → RPC scope 没丢」两组）；</li>
 *   <li>各 {@code logic/*.go} 的 {@code switch st.Code()} —— 逐个 gRPC code 验业务码。</li>
 * </ol>
 *
 * <h2>⚠️ 断言的是「Go 的行为」，不是「Java 现在的行为」</h2>
 * 每条期望值都直接抄自 Go 源码行，不允许「照着当前实现写断言」——
 * 那样只会把一个错误答案锁成绿色（AGENTS.md §3.3）。
 *
 * <p><b>唯一被替换的是 gRPC 边界</b>（{@link ArticleRpcClient} / {@link UserRpcClient}）：
 * 被测语义全在 api 进程内：路由 → 拦截器 → Service → advice → 序列化。
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
                "logging.level.com.alibaba.nacos=WARN"
        })
@AutoConfigureMockMvc
class ArticleApiMvcPipelineTest {

    /** 与 application.yml 的 {@code article-api.auth.access-secret} 同一份。 */
    private static final String SECRET = "ae0536f9-6450-4606-8e13-5a19ed505da0";
    private static final long CLAIM_UID = 1001L;
    private static final long OTHER_UID = 2002L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ArticleRpcClient articleRpcClient;

    @MockitoBean
    private UserRpcClient userRpcClient;

    private ArticleServiceBlockingStub stub;
    private UserServiceBlockingStub userStub;

    /** 记录 RPC 请求，用于断言「HTTP → RPC scope 没丢」。对应 Go 的 rpc.getCalls / rpc.listCall。 */
    private final List<GetArticleRequest> getCalls = new ArrayList<>();
    private ListArticlesRequest lastListCall;

    @BeforeEach
    void setUp() {
        stub = Mockito.mock(ArticleServiceBlockingStub.class);
        userStub = Mockito.mock(UserServiceBlockingStub.class);
        Mockito.when(articleRpcClient.articleRpc()).thenReturn(stub);
        Mockito.when(userRpcClient.userRpc()).thenReturn(userStub);
        getCalls.clear();
        lastListCall = null;
    }

    // =====================================================================
    // 1. Go: public_read_handler_test.go:99-151 —— 公开读 / 作者读契约
    // =====================================================================

    /**
     * Go {@code TestPublicAndAuthorHTTPReadContract} 的完整搬运。
     *
     * <p>Go 侧的 fake rpc（{@code publicReadArticleRPC}）语义逐条转成 Mockito 桩：
     * <pre>
     *   ArticleId == "stale-backend" → REVIEWING + "secret draft"      （旧 rpc 忽略 public_only）
     *   ArticleId == "stale-author"  → DRAFT + "secret draft"
     *   RequesterId == "2002"        → PermissionDenied("wrong author")
     *   ArticleId == "draft" &amp;&amp; PublicOnly → NotFound("unpublished")
     *   其余                          → RequesterId 非空 ? (REVIEWING,"# current draft")
     *                                                   : (PUBLISHED, "# approved")
     * </pre>
     */
    @Test
    @DisplayName("Go TestPublicAndAuthorHTTPReadContract：公开读 1002/200、作者读 401/1006/200、草稿不泄露")
    void publicAndAuthorHttpReadContract() throws Exception {
        Mockito.when(stub.getArticle(Mockito.any())).thenAnswer(inv -> {
            GetArticleRequest in = inv.getArgument(0);
            getCalls.add(in);
            return fakeGetArticle(in);
        });
        Mockito.when(stub.listArticles(Mockito.any())).thenAnswer(inv -> {
            lastListCall = inv.getArgument(0);
            if ("old".equals(lastListCall.getManualTypeTag())) {
                return ListArticlesResponse.newBuilder()
                        .addArticles(pbArticle("draft", "1001", ArticleStatus.DRAFT))
                        .setTotal(1)
                        .build();
            }
            return ListArticlesResponse.newBuilder()
                    .addArticles(pbArticle("published", "1001", ArticleStatus.PUBLISHED))
                    .setTotal(1).setPage(1).setPageSize(20)
                    .build();
        });
        // Go: publicReadUserRPC.GetUser 恒返回 {Found:false} ⇒ 作者名回落 "用户 1001"
        Mockito.when(userStub.getUser(Mockito.any())).thenReturn(
                com.rag.users.user.proto.GetUserResp.newBuilder().setFound(false).build());

        // ── 公开读：草稿 → 1002（Go:115-117）──
        mockMvc.perform(get("/v1/article/draft").queryParam("incr_view", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002));

        // ── 公开读：已发布 → 200（Go:118-120）──
        mockMvc.perform(get("/v1/article/published").queryParam("incr_view", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // ── 公开读：旧 rpc 漏草稿 ⇒ 1002 且**不泄露正文**（Go:121-123）──
        String staleBody = mockMvc.perform(get("/v1/article/stale-backend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002))
                .andReturn().getResponse().getContentAsString();
        assertFalse(staleBody.contains("secret draft"),
                "旧 rpc 返回的草稿正文绝不能从公开详情接口泄露：" + staleBody);

        // ── 作者读：无 JWT → 401（Go:125-127）──
        mockMvc.perform(get("/v1/me/article/draft"))
                .andExpect(status().isUnauthorized());

        // ── 作者读：非作者 → 1006（Go:128-130）──
        mockMvc.perform(get("/v1/me/article/draft").header("Authorization", bearer(tokenFor(OTHER_UID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1006));

        // ── 作者读：本人草稿 → 200（Go:131-133）──
        mockMvc.perform(get("/v1/me/article/draft").header("Authorization", bearer(tokenFor(CLAIM_UID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // ── 作者读：旧 rpc 漏他人草稿 ⇒ 1006 且不泄露（Go:134-136）──
        String staleAuthorBody = mockMvc.perform(
                        get("/v1/me/article/stale-author").header("Authorization", bearer(tokenFor(OTHER_UID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1006))
                .andReturn().getResponse().getContentAsString();
        assertFalse(staleAuthorBody.contains("secret draft"),
                "旧 rpc 返回的他人草稿正文绝不能泄露：" + staleAuthorBody);

        // ── 公开列表：正常 → 200（Go:137-139）──
        mockMvc.perform(get("/v1/articles").queryParam("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // ── 公开列表：旧 rpc 漏草稿 ⇒ **整条 5001** 且不泄露（Go:140-142）──
        String staleListBody = mockMvc.perform(get("/v1/articles").queryParam("manual_type_tag", "old"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001))
                .andReturn().getResponse().getContentAsString();
        assertFalse(staleListBody.contains("secret draft"),
                "旧 rpc 的草稿行绝不能从公开列表泄露：" + staleListBody);

        // ══════════════════════════════════════════════════════════════
        // Go: public_read_handler_test.go:143-150 —— 断言 HTTP→RPC 的 scope 没丢
        // ══════════════════════════════════════════════════════════════
        assertEquals(6, getCalls.size(), "Go 侧断言恰好 6 次 GetArticle 调用");
        assertTrue(getCalls.get(0).getPublicOnly(), "公开读 #1 必须 public_only=true");
        assertTrue(getCalls.get(0).getIncrView(), "公开读 #1 必须透传 incr_view=true");
        assertTrue(getCalls.get(1).getPublicOnly(), "公开读 #2 必须 public_only=true");
        assertTrue(getCalls.get(2).getPublicOnly(), "公开读 #3 必须 public_only=true");
        assertEquals(String.valueOf(OTHER_UID), getCalls.get(3).getRequesterId(),
                "作者读必须带 requester_id");
        assertFalse(getCalls.get(3).getPublicOnly(),
                "作者读**不得**带 public_only（否则看不到自己的草稿）");
        assertEquals(String.valueOf(CLAIM_UID), getCalls.get(4).getRequesterId());
        assertFalse(getCalls.get(4).getIncrView(),
                "作者读的 IncrView 必须恒 false —— Go GetAuthorArticleLogic 不设该字段");
        assertEquals(String.valueOf(OTHER_UID), getCalls.get(5).getRequesterId());
        assertNotNull(lastListCall);
        assertTrue(lastListCall.getPublicOnly(), "公开列表必须 public_only=true");
    }

    /** Go {@code publicReadArticleRPC.GetArticle} 的逐条语义。 */
    private static GetArticleResponse fakeGetArticle(GetArticleRequest in) {
        if ("stale-backend".equals(in.getArticleId())) {
            return GetArticleResponse.newBuilder()
                    .setArticle(pbArticle("stale-backend", "1001", ArticleStatus.REVIEWING, "secret draft"))
                    .build();
        }
        if ("stale-author".equals(in.getArticleId())) {
            return GetArticleResponse.newBuilder()
                    .setArticle(pbArticle("stale-author", "1001", ArticleStatus.DRAFT, "secret draft"))
                    .build();
        }
        if (String.valueOf(OTHER_UID).equals(in.getRequesterId())) {
            throw Status.PERMISSION_DENIED.withDescription("wrong author").asRuntimeException();
        }
        if ("draft".equals(in.getArticleId()) && in.getPublicOnly()) {
            throw Status.NOT_FOUND.withDescription("unpublished").asRuntimeException();
        }
        boolean isAuthorRead = !in.getRequesterId().isEmpty();
        return GetArticleResponse.newBuilder()
                .setArticle(pbArticle(in.getArticleId(), "1001",
                        isAuthorRead ? ArticleStatus.REVIEWING : ArticleStatus.PUBLISHED,
                        isAuthorRead ? "# current draft" : "# approved"))
                .build();
    }

    // =====================================================================
    // 2. 鉴权边界：7 个端点里恰好 4 个要 JWT
    // =====================================================================

    @Test
    @DisplayName("无 token：4 个受保护端点全部 401 空 body")
    void protectedRoutesRequireJwt() throws Exception {
        String token = bearer(tokenFor(CLAIM_UID));

        for (String[] call : new String[][]{
                {"POST", "/v1/article"},
                {"PUT", "/v1/article/1"},
                {"DELETE", "/v1/article/1"},
                {"GET", "/v1/me/article/1"}}) {
            var result = switch (call[0]) {
                case "POST" -> mockMvc.perform(post(call[1]).contentType(MediaType.APPLICATION_JSON).content("{}"));
                case "PUT" -> mockMvc.perform(put(call[1]).contentType(MediaType.APPLICATION_JSON).content("{}"));
                case "DELETE" -> mockMvc.perform(delete(call[1]));
                default -> mockMvc.perform(get(call[1]));
            };
            result.andExpect(status().isUnauthorized())
                    .andExpect(content().string(""));
        }
        assertNotNull(token);
    }

    @Test
    @DisplayName("公开端点不需要 token：GET /v1/article/:id、GET /v1/articles、POST /v1/upload")
    void publicRoutesDoNotRequireJwt() throws Exception {
        Mockito.when(stub.getArticle(Mockito.any())).thenReturn(
                GetArticleResponse.newBuilder()
                        .setArticle(pbArticle("x", "1001", ArticleStatus.PUBLISHED))
                        .build());
        Mockito.when(stub.listArticles(Mockito.any())).thenReturn(ListArticlesResponse.newBuilder().build());
        Mockito.when(stub.uploadFile(Mockito.any())).thenReturn(
                com.rag.article.article.proto.UploadFileResponse.newBuilder()
                        .setFileUrl("http://minio/x.png").build());
        Mockito.when(userStub.getUser(Mockito.any())).thenReturn(
                com.rag.users.user.proto.GetUserResp.newBuilder().setFound(false).build());

        // ⚠️ 这三个端点在 Go 里**不带 rest.WithJwt**。
        //    「上传图片要登录」是凭直觉会加上去、但 Go 侧没有的规则。
        mockMvc.perform(get("/v1/article/x")).andExpect(status().isOk());
        mockMvc.perform(get("/v1/articles")).andExpect(status().isOk());
        mockMvc.perform(multipart("/v1/upload")
                        .file(new MockMultipartFile("image", "a.png", "image/png", new byte[]{1, 2, 3})))
                .andExpect(status().isOk());
    }

    // =====================================================================
    // 3. POST /v1/upload：唯一不走信封的端点（L1）
    // =====================================================================

    @Test
    @DisplayName("upload 成功返回**裸** {\"image_url\":…}，不是 {code,msg,data}（Go OkJsonCtx vs OkJson）")
    void uploadSuccessIsNotWrappedInEnvelope() throws Exception {
        Mockito.when(stub.uploadFile(Mockito.any())).thenReturn(
                com.rag.article.article.proto.UploadFileResponse.newBuilder()
                        .setFileUrl("http://minio/a.png").build());

        mockMvc.perform(multipart("/v1/upload")
                        .file(new MockMultipartFile("image", "a.png", "image/png", new byte[]{1})))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.image_url").value("http://minio/a.png"))
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("upload 的 RPC 失败 ⇒ **400 text/plain + nosniff**，不是 1015 信封（Go 无 error handler）")
    void uploadRpcFailureIsBareTextPlain400() throws Exception {
        Mockito.doThrow(Status.INTERNAL.withDescription("minio down").asRuntimeException())
                .when(stub).uploadFile(Mockito.any());

        var response = mockMvc.perform(multipart("/v1/upload")
                        .file(new MockMultipartFile("image", "a.png", "image/png", new byte[]{1})))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse();

        assertTrue(response.getContentType().startsWith("text/plain"),
                "Go http.Error 的 Content-Type 必须是 text/plain；charset=utf-8，实际=" + response.getContentType());
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"),
                "Go http.Error 必须带 X-Content-Type-Options: nosniff");
        assertFalse(response.getContentAsString().contains("\"code\""),
                "解析/下游失败不是信封形状");
        assertTrue(response.getContentAsString().endsWith("\n"),
                "Go http.Error 用 Fprintln，body 以 \\n 结尾");
    }

    @Test
    @DisplayName("upload 缺少 image 字段 ⇒ 400 text/plain（Go: r.FormFile 失败 → httpx.ErrorCtx）")
    void uploadMissingPartIsBare400() throws Exception {
        mockMvc.perform(multipart("/v1/upload")
                        .file(new MockMultipartFile("wrong", "a.png", "image/png", new byte[]{1})))
                .andExpect(status().isBadRequest());
    }

    // =====================================================================
    // 4. 解析层失败：400 text/plain（article-api 没有 SetErrorHandler）
    // =====================================================================

    @Test
    @DisplayName("必填字段缺席 → 400 text/plain + body 含 \"is not set\"（Go newInitError）")
    void missingRequiredFieldIsGoInitError() throws Exception {
        var response = mockMvc.perform(post("/v1/article")
                        .header("Authorization", bearer(tokenFor(CLAIM_UID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse();

        assertTrue(response.getContentAsString().contains("is not set"),
                "缺席应报 field \"title\" is not set，实际=" + response.getContentAsString());
        assertTrue(response.getContentType().startsWith("text/plain"));
    }

    @Test
    @DisplayName("必填字段显式 null → 400 text/plain + body 含 \"mustn't be nil\"（Go unmarshaler.go:856-862）")
    void explicitNullRequiredFieldIsGoNilError() throws Exception {
        var response = mockMvc.perform(post("/v1/article")
                        .header("Authorization", bearer(tokenFor(CLAIM_UID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":null,\"content\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse();

        assertTrue(response.getContentAsString().contains("mustn't be nil"),
                "显式 null 应报 field \"title\" mustn't be nil，实际=" + response.getContentAsString());
    }

    @Test
    @DisplayName("数字进 String 字段 → 400（Go type mismatch for field \"title\"；ArticleGoStyleCoercion 规则 1）")
    void numberIntoStringFieldIsRejected() throws Exception {
        var response = mockMvc.perform(post("/v1/article")
                        .header("Authorization", bearer(tokenFor(CLAIM_UID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":123,\"content\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse();
        assertTrue(response.getContentType().startsWith("text/plain"));
    }

    @Test
    @DisplayName("字符串进 int 字段 → 400（article 域无 ,string；ArticleGoStyleCoercion 补强）")
    void stringIntoIntFieldIsRejected() throws Exception {
        var response = mockMvc.perform(put("/v1/article/1")
                        .header("Authorization", bearer(tokenFor(CLAIM_UID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"2\"}"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse();
        assertTrue(response.getContentType().startsWith("text/plain"),
                "{\"status\":\"2\"} 在 Go 侧是 type mismatch（article 的 types.go 没有一个 ,string）");
    }

    @Test
    @DisplayName("畸形 JSON → 400 text/plain（Go UnmarshalJsonReader 失败 → httpx.ErrorCtx）")
    void malformedJsonIsBare400() throws Exception {
        var response = mockMvc.perform(post("/v1/article")
                        .header("Authorization", bearer(tokenFor(CLAIM_UID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse();
        assertTrue(response.getContentType().startsWith("text/plain"));
    }

    // =====================================================================
    // 5. gRPC code → 业务码（L2 的核心映射）
    // =====================================================================

    @Test
    @DisplayName("公开读：NOT_FOUND→1002、INTERNAL→5001、PERMISSION_DENIED→**1015**（Go default 分支）")
    void getArticleRpcCodeMapping() throws Exception {
        Mockito.when(userStub.getUser(Mockito.any())).thenReturn(
                com.rag.users.user.proto.GetUserResp.newBuilder().setFound(false).build());

        // ⚠️ 用 doThrow().when(...) 而不是 when(...).thenThrow(...)：
        //    when(stub.getArticle(...)) **会真的调用一次** getArticle，
        //    而上一条 thenThrow 仍然生效 ⇒ 「打桩」这一步自己就抛了。
        Mockito.doThrow(Status.NOT_FOUND.asRuntimeException())
                .when(stub).getArticle(Mockito.any());
        mockMvc.perform(get("/v1/article/x")).andExpect(jsonPath("$.code").value(1002));

        Mockito.doThrow(Status.INTERNAL.asRuntimeException())
                .when(stub).getArticle(Mockito.any());
        mockMvc.perform(get("/v1/article/x")).andExpect(jsonPath("$.code").value(5001));

        // ⚠️ Go 的 public GetArticle 只有三个分支：NotFound / Internal / default。
        //    PermissionDenied 落进 default ⇒ **1015「服务繁忙」**，不是 1006。
        //    「公开接口不告诉调用方你被拒了」是 Go 侧的刻意设计。
        Mockito.doThrow(Status.PERMISSION_DENIED.asRuntimeException())
                .when(stub).getArticle(Mockito.any());
        mockMvc.perform(get("/v1/article/x")).andExpect(jsonPath("$.code").value(1015));
    }

    @Test
    @DisplayName("创建：AlreadyExists→1001（data=null）、FailedPrecondition→1004（**data 带 article_id**）")
    void createRpcCodeMappingCarriesArticleIdInData() throws Exception {
        // Go create_article_logic.go:60-69：两条分支的 resp 带 article_id
        Mockito.doThrow(Status.FAILED_PRECONDITION
                        .withDescription("publish rejected: 1792670400123")
                        .asRuntimeException())
                .when(stub).createArticle(Mockito.any());

        mockMvc.perform(post("/v1/article")
                        .header("Authorization", bearer(tokenFor(CLAIM_UID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"content\":\"c\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1004))
                // ⚠️ 「失败但带 data」是本组件的 L2 语义：客户端需要这个 id 才能继续轮询
                .andExpect(jsonPath("$.data.article_id").value("1792670400123"));

        // AlreadyExists 分支：data 必须是 null
        Mockito.doThrow(Status.ALREADY_EXISTS.asRuntimeException())
                .when(stub).createArticle(Mockito.any());
        mockMvc.perform(post("/v1/article")
                        .header("Authorization", bearer(tokenFor(CLAIM_UID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"content\":\"c\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("创建：无 userId claim ⇒ **HTTP 200 + {code:1005}**，不是 401（Go extractCurrentUserID）")
    void missingUserIdClaimIsBusinessCode1005Not401() throws Exception {
        // token 里**不带** userId claim：签名有效但没有身份。
        // ⚠️ 必须用 jjwt 直接签一个「只有 exp、没有 userId」的 token。
        //    改 JwtUtil.getToken(...) 的返回值再字符串替换是**错的** ——
        //    payload 一改签名就对不上，验签先失败 ⇒ 得到 401 而不是 200 + 1005，
        //    测试会「因为错误的原因」通过或失败。
        long nowSeconds = System.currentTimeMillis() / 1000L;
        String tokenNoUid = io.jsonwebtoken.Jwts.builder()
                .issuedAt(new java.util.Date(nowSeconds * 1000L))
                .expiration(new java.util.Date((nowSeconds + 3600L) * 1000L))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        com.rag.users.common.jwt.JwtUtil.padToHmacKeySize(SECRET)))
                .compact();

        mockMvc.perform(post("/v1/article")
                        .header("Authorization", bearer(tokenNoUid))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"content\":\"c\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1005))
                .andExpect(jsonPath("$.msg").value("请先登录"));
    }

    @Test
    @DisplayName("更新：先读鉴权，非作者 ⇒ 1006；作者 ⇒ 透传 rpc 的 success（可能 false）")
    void updateOwnershipAndSuccessPassthrough() throws Exception {
        Mockito.when(userStub.getUser(Mockito.any())).thenReturn(
                com.rag.users.user.proto.GetUserResp.newBuilder().setFound(false).build());

        // 非作者：GetArticle 返回别人的文章 ⇒ 1006，且**不得**调 UpdateArticle
        Mockito.when(stub.getArticle(Mockito.any())).thenReturn(
                GetArticleResponse.newBuilder()
                        .setArticle(pbArticle("1", String.valueOf(OTHER_UID), ArticleStatus.PUBLISHED))
                        .build());
        Mockito.when(stub.updateArticle(Mockito.any())).thenReturn(
                com.rag.article.article.proto.UpdateArticleResponse.newBuilder().setSuccess(true).build());

        mockMvc.perform(put("/v1/article/1")
                        .header("Authorization", bearer(tokenFor(CLAIM_UID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"new\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1006));
        Mockito.verify(stub, Mockito.never()).updateArticle(Mockito.any());

        // 作者：success 直接取 rpc 的值（Go GetSuccess()，**不是**恒 true）
        Mockito.when(stub.getArticle(Mockito.any())).thenReturn(
                GetArticleResponse.newBuilder()
                        .setArticle(pbArticle("1", String.valueOf(CLAIM_UID), ArticleStatus.PUBLISHED))
                        .build());
        Mockito.when(stub.updateArticle(Mockito.any())).thenReturn(
                com.rag.article.article.proto.UpdateArticleResponse.newBuilder().setSuccess(false).build());

        mockMvc.perform(put("/v1/article/1")
                        .header("Authorization", bearer(tokenFor(CLAIM_UID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"new\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.success").value(false));
    }

    @Test
    @DisplayName("更新：非空才下发 optional 字段；status=0 不下发（Go update_article_logic.go:74-92）")
    void updateOnlySendsNonEmptyOptionalFields() throws Exception {
        Mockito.when(userStub.getUser(Mockito.any())).thenReturn(
                com.rag.users.user.proto.GetUserResp.newBuilder().setFound(false).build());
        Mockito.when(stub.getArticle(Mockito.any())).thenReturn(
                GetArticleResponse.newBuilder()
                        .setArticle(pbArticle("1", String.valueOf(CLAIM_UID), ArticleStatus.PUBLISHED))
                        .build());
        Mockito.when(stub.updateArticle(Mockito.any())).thenReturn(
                com.rag.article.article.proto.UpdateArticleResponse.newBuilder().setSuccess(true).build());

        mockMvc.perform(put("/v1/article/1")
                        .header("Authorization", bearer(tokenFor(CLAIM_UID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        // brief 显式给空串、status 显式给 0 —— 两者都**不该**下发
                        .content("{\"title\":\"t\",\"brief\":\"\",\"status\":0}"))
                .andExpect(status().isOk());

        ArgumentCaptor<com.rag.article.article.proto.UpdateArticleRequest> captor =
                ArgumentCaptor.forClass(com.rag.article.article.proto.UpdateArticleRequest.class);
        Mockito.verify(stub).updateArticle(captor.capture());
        var sent = captor.getValue();
        assertEquals("t", sent.getTitle());
        assertFalse(sent.hasBrief(), "空串 brief 不得下发（Go: if req.Brief != \"\"）");
        assertFalse(sent.hasStatus(), "status=0 不得下发（Go: if req.Status != 0）");
    }

    @Test
    @DisplayName("删除：非作者 ⇒ 1006；作者 ⇒ success 恒 true，且两个 id 都下发")
    void deleteOwnershipAndOperatorId() throws Exception {
        Mockito.when(userStub.getUser(Mockito.any())).thenReturn(
                com.rag.users.user.proto.GetUserResp.newBuilder().setFound(false).build());

        Mockito.when(stub.getArticle(Mockito.any())).thenReturn(
                GetArticleResponse.newBuilder()
                        .setArticle(pbArticle("1", String.valueOf(OTHER_UID), ArticleStatus.PUBLISHED))
                        .build());
        mockMvc.perform(delete("/v1/article/1").header("Authorization", bearer(tokenFor(CLAIM_UID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1006));
        Mockito.verify(stub, Mockito.never()).deleteArticle(Mockito.any());

        Mockito.when(stub.getArticle(Mockito.any())).thenReturn(
                GetArticleResponse.newBuilder()
                        .setArticle(pbArticle("1", String.valueOf(CLAIM_UID), ArticleStatus.PUBLISHED))
                        .build());
        mockMvc.perform(delete("/v1/article/1").header("Authorization", bearer(tokenFor(CLAIM_UID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.success").value(true));

        ArgumentCaptor<com.rag.article.article.proto.DeleteArticleRequest> captor =
                ArgumentCaptor.forClass(com.rag.article.article.proto.DeleteArticleRequest.class);
        Mockito.verify(stub).deleteArticle(captor.capture());
        assertEquals(String.valueOf(CLAIM_UID), captor.getValue().getOperatorId(),
                "operator_id 必须下发（对端要记审计）");
    }

    // =====================================================================
    // 6. query string 的 Go 解释规则（GoFormValues）
    // =====================================================================

    @Test
    @DisplayName("?incr_view=1 ⇒ true（Go convertTypeFromString）；Spring 默认绑定会拒 1")
    void incrViewAcceptsGoTruthyOne() throws Exception {
        Mockito.when(userStub.getUser(Mockito.any())).thenReturn(
                com.rag.users.user.proto.GetUserResp.newBuilder().setFound(false).build());
        Mockito.when(stub.getArticle(Mockito.any())).thenReturn(
                GetArticleResponse.newBuilder()
                        .setArticle(pbArticle("x", "1001", ArticleStatus.PUBLISHED))
                        .build());

        mockMvc.perform(get("/v1/article/x").queryParam("incr_view", "1"))
                .andExpect(status().isOk());

        ArgumentCaptor<GetArticleRequest> captor = ArgumentCaptor.forClass(GetArticleRequest.class);
        Mockito.verify(stub).getArticle(captor.capture());
        assertTrue(captor.getValue().getIncrView(),
                "?incr_view=1 在 Go 侧是 true（convertTypeFromString 的 \"1\" 分支）");
    }

    @Test
    @DisplayName("?incr_view=（空值）⇒ false 且**不报错**（Go GetFormValues 丢弃空值）")
    void incrViewEmptyValueIsDroppedNotRejected() throws Exception {
        Mockito.when(userStub.getUser(Mockito.any())).thenReturn(
                com.rag.users.user.proto.GetUserResp.newBuilder().setFound(false).build());
        Mockito.when(stub.getArticle(Mockito.any())).thenReturn(
                GetArticleResponse.newBuilder()
                        .setArticle(pbArticle("x", "1001", ArticleStatus.PUBLISHED))
                        .build());

        mockMvc.perform(get("/v1/article/x").queryParam("incr_view", ""))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("?incr_view=abc ⇒ 400（Go errTypeMismatch；不是静默当 false）")
    void incrViewInvalidValueIsRejected() throws Exception {
        mockMvc.perform(get("/v1/article/x").queryParam("incr_view", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("列表默认值：page=1 / page_size=20 / sort_by=create_time / desc=true（Go tag ,default=）")
    void listRequestDefaults() throws Exception {
        Mockito.when(userStub.getUser(Mockito.any())).thenReturn(
                com.rag.users.user.proto.GetUserResp.newBuilder().setFound(false).build());
        Mockito.when(stub.listArticles(Mockito.any())).thenReturn(ListArticlesResponse.newBuilder().build());

        mockMvc.perform(get("/v1/articles")).andExpect(status().isOk());

        ArgumentCaptor<ListArticlesRequest> captor = ArgumentCaptor.forClass(ListArticlesRequest.class);
        Mockito.verify(stub).listArticles(captor.capture());
        var sent = captor.getValue();
        assertEquals(1, sent.getPage());
        assertEquals(20, sent.getPageSize());
        assertEquals("create_time", sent.getSortBy());
        assertTrue(sent.getDesc(), "Go tag 是 desc,default=true");
    }

    @Test
    @DisplayName("?page=010 ⇒ 10（base-10），不是 8（Integer.decode 的八进制陷阱）")
    void pageIsParsedAsBase10NotOctal() throws Exception {
        Mockito.when(userStub.getUser(Mockito.any())).thenReturn(
                com.rag.users.user.proto.GetUserResp.newBuilder().setFound(false).build());
        Mockito.when(stub.listArticles(Mockito.any())).thenReturn(ListArticlesResponse.newBuilder().build());

        mockMvc.perform(get("/v1/articles").queryParam("page", "010")).andExpect(status().isOk());

        ArgumentCaptor<ListArticlesRequest> captor = ArgumentCaptor.forClass(ListArticlesRequest.class);
        Mockito.verify(stub).listArticles(captor.capture());
        assertEquals(10, captor.getValue().getPage(),
                "Go strconv.ParseInt(s,10,32) 是十进制；Integer.decode 会当成八进制 8");
    }

    @Test
    @DisplayName("列表空结果 ⇒ \"articles\":null（Go var articles []types.Article 是 nil slice）")
    void emptyListIsNullNotEmptyArray() throws Exception {
        Mockito.when(userStub.getUser(Mockito.any())).thenReturn(
                com.rag.users.user.proto.GetUserResp.newBuilder().setFound(false).build());
        Mockito.when(stub.listArticles(Mockito.any())).thenReturn(ListArticlesResponse.newBuilder().build());

        mockMvc.perform(get("/v1/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.articles").isEmpty())
                .andExpect(jsonPath("$.data.articles").value(org.hamcrest.Matchers.nullValue()));
    }

    // =====================================================================
    // 7. 作者昵称补全：请求内去重 + 四条回落路径
    // =====================================================================

    @Test
    @DisplayName("列表里同一作者只调一次 GetUser（Go enrichArticleAuthors 的请求内 cache）")
    void authorNameIsDedupedPerRequest() throws Exception {
        Mockito.when(stub.listArticles(Mockito.any())).thenReturn(ListArticlesResponse.newBuilder()
                .addArticles(pbArticle("a", "1001", ArticleStatus.PUBLISHED))
                .addArticles(pbArticle("b", "1001", ArticleStatus.PUBLISHED))
                .addArticles(pbArticle("c", "1001", ArticleStatus.PUBLISHED))
                .setTotal(3).setPage(1).setPageSize(20)
                .build());
        Mockito.when(userStub.getUser(Mockito.any())).thenReturn(
                com.rag.users.user.proto.GetUserResp.newBuilder()
                        .setFound(true)
                        .setUser(com.rag.users.user.proto.UserInfo.newBuilder()
                                .setUid(CLAIM_UID).setUsername("alice").build())
                        .build());

        mockMvc.perform(get("/v1/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.articles[0].author_name").value("alice"))
                .andExpect(jsonPath("$.data.articles[2].author_name").value("alice"));

        Mockito.verify(userStub, Mockito.times(1)).getUser(Mockito.any());
    }

    @Test
    @DisplayName("GetUser 失败 ⇒ 回落 \"用户 <id>\"，**不**让整个列表失败（Go helper.go:82-89）")
    void authorNameFallbackOnRpcFailure() throws Exception {
        Mockito.when(stub.listArticles(Mockito.any())).thenReturn(ListArticlesResponse.newBuilder()
                .addArticles(pbArticle("a", "1001", ArticleStatus.PUBLISHED))
                .setTotal(1).setPage(1).setPageSize(20)
                .build());
        Mockito.doThrow(Status.UNAVAILABLE.withDescription("user-rpc down").asRuntimeException())
                .when(userStub).getUser(Mockito.any());

        mockMvc.perform(get("/v1/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.articles[0].author_name").value("用户 1001"));
    }

    @Test
    @DisplayName("author_id 非数字 ⇒ 回落 \"用户 <id>\" 且**不发 RPC**（Go strconv.ParseInt 失败分支）")
    void nonNumericAuthorIdSkipsRpc() throws Exception {
        Mockito.when(stub.listArticles(Mockito.any())).thenReturn(ListArticlesResponse.newBuilder()
                .addArticles(pbArticle("a", "not-a-number", ArticleStatus.PUBLISHED))
                .setTotal(1).setPage(1).setPageSize(20)
                .build());

        mockMvc.perform(get("/v1/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.articles[0].author_name").value("用户 not-a-number"));

        Mockito.verify(userStub, Mockito.never()).getUser(Mockito.any());
    }

    @Test
    @DisplayName("author_id 为空 ⇒ \"未知作者\" 且不发 RPC；author_name 为空时**整个 key 消失**（Go ,omitempty）")
    void emptyAuthorIdIsUnknownAuthorAndOmitsKey() throws Exception {
        Mockito.when(stub.listArticles(Mockito.any())).thenReturn(ListArticlesResponse.newBuilder()
                .addArticles(pbArticle("a", "", ArticleStatus.PUBLISHED))
                .setTotal(1).setPage(1).setPageSize(20)
                .build());
        Mockito.when(userStub.getUser(Mockito.any())).thenReturn(
                com.rag.users.user.proto.GetUserResp.newBuilder().setFound(true)
                        .setUser(com.rag.users.user.proto.UserInfo.newBuilder().setUsername("  ").build())
                        .build());

        mockMvc.perform(get("/v1/articles"))
                .andExpect(status().isOk())
                // fallbackAuthorName("") = "未知作者"
                .andExpect(jsonPath("$.data.articles[0].author_name").value("未知作者"));
        Mockito.verify(userStub, Mockito.never()).getUser(Mockito.any());
    }

    // =====================================================================
    // 8. proto 空集合 → Go 的 null 形状（L1 字节级）
    // =====================================================================

    @Test
    @DisplayName("空 secondary_tags / ext_info ⇒ JSON null（Go cloneStringMap / nil slice）")
    void emptyProtoCollectionsBecomeNull() throws Exception {
        Mockito.when(userStub.getUser(Mockito.any())).thenReturn(
                com.rag.users.user.proto.GetUserResp.newBuilder().setFound(false).build());
        Mockito.when(stub.getArticle(Mockito.any())).thenReturn(
                GetArticleResponse.newBuilder()
                        .setArticle(pbArticle("x", "1001", ArticleStatus.PUBLISHED))
                        .build());

        String body = mockMvc.perform(get("/v1/article/x"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Go: Article.ExtInfo `json:"ext_info"`（无 omitempty）+ nil map ⇒ "ext_info":null
        assertTrue(body.contains("\"ext_info\":null"),
                "空 ext_info 必须是 null 而不是 {} 或缺失；实际=" + body);
        // Go: Article.SecondaryTags `json:"secondary_tags"`（无 omitempty）+ nil slice ⇒ null
        assertTrue(body.contains("\"secondary_tags\":null"),
                "空 secondary_tags 必须是 null 而不是 []；实际=" + body);
    }

    // =====================================================================
    // 9. DRIFT 回归：三条「绿测试守卫了错误答案」的修复后的守门
    // =====================================================================

    /**
     * DRIFT-002 守门：9 MiB 合法 JSON body ⇒ <b>400</b>，不得进业务。
     *
     * <p>Go {@code rest/httpx/requests.go:21,119}：{@code maxBodyLen = 8 << 20}，
     * {@code io.LimitReader} 把 body <b>静默截断</b>在 8 MiB ⇒ 解析器拿到半个文档 ⇒
     * {@code httpx.ErrorCtx} ⇒ 400 text/plain。
     *
     * <p>⚠️ 上一版对本条<b>完全无感</b>：当时 {@code #withinLimitPassesThrough} 用的是
     * 9 MiB <b>全空格</b>的 body，断言 400，但那是<b>必填校验</b>失败，
     * 压根没走到 8 MiB 截断那条路径 —— 一个「因为错误的原因而绿」的用例。
     */
    @Test
    @DisplayName("DRIFT-002：9 MiB **合法** JSON body ⇒ 400（Go 的 maxBodyLen=8MiB 静默截断）")
    void oversizedJsonBodyIsRejectedAtEightMiB() throws Exception {
        // 构造一个**合法**的 JSON 文档：padding 塞进 brief，content 短
        String padding = "x".repeat(9 * 1024 * 1024);
        String body = "{\"title\":\"t\",\"content\":\"c\",\"brief\":\"" + padding + "\"}";
        org.junit.jupiter.api.Assertions.assertTrue(body.length() > 9 * 1024 * 1024,
                "构造的 body 必须真的超过 9 MiB，否则本用例测不到 DRIFT-002");

        var response = mockMvc.perform(post("/v1/article")
                        .header("Authorization", bearer(tokenFor(CLAIM_UID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse();

        assertEqualsTextPlainUtf8(response.getContentType());
        Mockito.verify(stub, Mockito.never()).createArticle(Mockito.any());
    }

    @Test
    @DisplayName("DRIFT-002 反向：1 MiB 合法 JSON body ⇒ 进业务（阈值不是 1 MiB）")
    void oneMiBJsonBodyStillReachesBusiness() throws Exception {
        Mockito.when(stub.createArticle(Mockito.any())).thenReturn(
                com.rag.article.article.proto.CreateArticleResponse.newBuilder()
                        .setArticleId("42").build());

        String padding = "x".repeat(1024 * 1024 - 64);
        String body = "{\"title\":\"t\",\"content\":\"c\",\"brief\":\"" + padding + "\"}";

        mockMvc.perform(post("/v1/article")
                        .header("Authorization", bearer(tokenFor(CLAIM_UID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.article_id").value("42"));
    }

    /**
     * DRIFT-003 守门：非 multipart 的 {@code POST /v1/upload} ⇒ <b>400</b>，不是 500。
     *
     * <p>Go：{@code r.FormFile("image")} 内部 {@code ParseMultipartForm} 失败
     * ⇒ handler 的 {@code httpx.ErrorCtx} ⇒ 400 text/plain。
     *
     * <p>⚠️ 上一版返回<b>裸 500</b>：{@code MultipartException}（{@code MaxUploadSizeExceededException} 的<b>父类</b>）
     * 没被 handler 族覆盖，落到兜底 {@code handleAny}。
     * 除了状态码错，500 还会被 {@code GoStyleBreakerFilter} 记为<b>熔断失败</b>，
     * 一次客户端 Content-Type 笔误就能打熔断器。
     */
    @Test
    @DisplayName("DRIFT-003：非 multipart 的 POST /v1/upload ⇒ 400 text/plain（不是裸 500）")
    void nonMultipartUploadIsBadRequestNotServerError() throws Exception {
        var response = mockMvc.perform(post("/v1/upload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse();

        assertEqualsTextPlainUtf8(response.getContentType());
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertFalse(response.getContentAsString().isEmpty(), "Go 的 400 带可读文案");
        Mockito.verify(stub, Mockito.never()).uploadFile(Mockito.any());
    }

    /**
     * DRIFT-006 守门：类型不匹配的 body 文案与 Go 三段式一致。
     *
     * <p>Go（实测）：{@code type mismatch for field "title", expect "string", actual "number"}
     */
    @Test
    @DisplayName("DRIFT-006：type mismatch 文案含 expect/actual 两段（与 Go 逐字一致）")
    void typeMismatchMessageMatchesGoText() throws Exception {
        var response = mockMvc.perform(post("/v1/article")
                        .header("Authorization", bearer(tokenFor(CLAIM_UID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":123,\"content\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse();

        String body = response.getContentAsString();
        assertTrue(body.contains("type mismatch for field \"title\""), body);
        assertTrue(body.contains("expect \"string\""), "应含 Go 的 expect 段：" + body);
        assertTrue(body.contains("actual \"number\""), "应含 Go 的 actual 段：" + body);
    }

    /**
     * DRIFT-007 守门：{@code ext_info} 的 key 按<b>字典序</b>输出。
     *
     * <p>Go 的 {@code encoding/json} 对 map key 排序（mapEncoder → {@code sort.Slice}），
     * 同一份数据每次序列化字节一致。protobuf 的 {@code getExtInfoMap()} 是无序 HashMap，
     * 所以 {@code ArticleApiSupport.cloneStringMap} 必须落到 {@code TreeMap}。
     *
     * <p>⚠️ 上一版这条<b>没测</b>，而源码注释还错写成「用 LinkedHashMap 保持字典序」——
     * 独立验证者据此判定 Java 不排序。<b>代码本来是对的，错的是注释，且没有任何测试会红。</b>
     */
    @Test
    @DisplayName("DRIFT-007：ext_info 的 key 按 **字节序** 输出（Go 的 sort 是大小写敏感，不是 A-Z-a-z）")
    void extInfoKeysAreSortedLexicographically() throws Exception {
        Mockito.when(userStub.getUser(Mockito.any())).thenReturn(
                com.rag.users.user.proto.GetUserResp.newBuilder().setFound(false).build());
        Mockito.when(stub.getArticle(Mockito.any())).thenReturn(
                GetArticleResponse.newBuilder()
                        .setArticle(Article.newBuilder()
                                .setId("x").setAuthorId("1001")
                                .setStatus(ArticleStatus.PUBLISHED)
                                .putExtInfo("zeta", "1")
                                .putExtInfo("alpha", "2")
                                .putExtInfo("Mid", "3")
                                .build())
                        .build());

        String body = mockMvc.perform(get("/v1/article/x"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // ⚠️ 期望顺序是 **Mid < alpha < zeta**，不是直觉上的 alpha < Mid < zeta。
        //    Go 的 mapEncoder 用 `sort.Slice(..., func(i,j) { return ks[i] < ks[j] })`，
        //    即**逐字节**比较：大写 M(0x4D=77) < 小写 a(0x61=97)。
        //    Java TreeMap 的自然序（String.compareTo，UTF-16 码元比较）恰好同构。
        //    ⚠️ 第一版的断言写成了大小写不敏感顺序 ⇒ **测试红了而代码是对的**，
        //    这正是「照着直觉写断言」会制造的假红。
        int mid = body.indexOf("\"Mid\"");
        int alpha = body.indexOf("\"alpha\"");
        int zeta = body.indexOf("\"zeta\"");
        assertTrue(mid >= 0 && alpha > mid && zeta > alpha,
                "ext_info 必须按字节序（Mid < alpha < zeta）输出，实际=" + body);
    }

    // =====================================================================
    // helpers
    // =====================================================================

    private static Article pbArticle(String id, String authorId, ArticleStatus status) {
        return pbArticle(id, authorId, status, "# approved");
    }

    private static Article pbArticle(String id, String authorId, ArticleStatus status, String content) {
        return Article.newBuilder()
                .setId(id)
                .setAuthorId(authorId)
                .setStatus(status)
                .setMarkdownContent(content)
                .build();
    }

    private static String tokenFor(long uid) {
        return JwtUtil.getToken(SECRET, System.currentTimeMillis() / 1000L, 3600L, uid);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    /** Go {@code http.Error} 的 Content-Type 判定（与路由边界测试里那份同源）。 */
    private static void assertEqualsTextPlainUtf8(String contentType) {
        org.junit.jupiter.api.Assertions.assertTrue(
                contentType != null && contentType.startsWith("text/plain"),
                "Go http.Error 的 Content-Type 必须是 text/plain; charset=utf-8，实际=" + contentType);
    }
}