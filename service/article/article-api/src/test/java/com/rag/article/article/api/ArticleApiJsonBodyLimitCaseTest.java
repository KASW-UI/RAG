package com.rag.article.article.api;

import com.rag.article.article.api.client.ArticleRpcClient;
import com.rag.article.article.api.client.UserRpcClient;
import com.rag.article.article.proto.ArticleServiceGrpc.ArticleServiceBlockingStub;
import com.rag.users.common.jwt.JwtUtil;
import com.rag.users.user.proto.UserServiceGrpc.UserServiceBlockingStub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 🔴 守门：article 域的 8 MiB JSON body 闸门必须走<b>共享层唯一真源</b>的判据。
 *
 * <h2>Go 出处（真源 = L2 框架层 go-zero@v1.10.2）</h2>
 * <pre>
 * rest/httpx/requests.go:21
 *     maxBodyLen = 8 &lt;&lt; 20 // 8MB
 * rest/httpx/requests.go:117-124
 *     func ParseJsonBody(r *http.Request, v any) error {
 *         if withJsonBody(r) {
 *             reader := io.LimitReader(r.Body, maxBodyLen)   // ← 只有命中判据才读、才受 8 MiB 限制
 *             return mapping.UnmarshalJsonReader(reader, v)
 *         }
 *         return mapping.UnmarshalJsonMap(nil, v)           // ← 不命中 ⇒ body 被整个丢弃
 *     }
 * rest/httpx/requests.go:153-155
 *     func withJsonBody(r *http.Request) bool {
 *         return r.ContentLength &gt; 0 &amp;&amp; strings.Contains(r.Header.Get(header.ContentType), header.ApplicationJson)
 *     }
 * </pre>
 *
 * <h2>🔴 本类守的是 2026-10-25 第十六轮修掉的漂移</h2>
 * <p>修之前 {@code GoStyleJsonBodyLimitFilter} 自己抄了一份判据 ⇒ 与共享转换器的判据
 * 是<b>两份实现</b> ⇒ 哪天一边改成「用 MediaType 解析」，RAG 就内部自相矛盾
 * （闸门说不读、解析器说要读）⇒ 见 {@code docs/DRIFT/global/cross-domain.md} X-9。
 * ⇒ 本轮改为<b>调用</b>唯一真源 ⇒ 结构上不可能再分叉。
 *
 * <h3>为什么这里验的是「大小写」而不是「阈值」</h3>
 * <p>阈值（8 MiB）那条已由 {@code ArticleApiMvcPipelineTest#oversizedJsonBodyIsRejectedAtEightMiB} 守住。
 * ⇒ 本类专门守<b>判据的来源</b>：闸门必须与 Go 一样<b>大小写敏感</b> ⇒
 * {@code Application/JSON} 在 Go 侧<b>不读 body</b> ⇒ 那 8 MiB 上限<b>根本不适用</b>
 * ⇒ 闸门<b>不许</b>触发。
 *
 * <h2>⚠️ 判别式断言为什么是「body 含 is not set」而不是「400」</h2>
 * <p>两种情况的 HTTP 状态码都是 400（article 域没注册 SetErrorHandler ⇒ 一律 400 text/plain），
 * <b>状态码分不出来</b> ⇒ 必须看 body：
 * <ul>
 *   <li>闸门触发 ⇒ body = {@code "request entity too large\n"}；</li>
 *   <li>闸门未触发 ⇒ body 被丢弃 ⇒ DTO 零值 ⇒ 必填校验失败 ⇒ body 含 {@code "is not set"}。</li>
 * </ul>
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
@DisplayName("🔴 article 域 8 MiB 闸门：判据必须来自共享层唯一真源（大小写敏感）")
class ArticleApiJsonBodyLimitCaseTest {

    /** 与 application.yml 的 {@code article-api.auth.access-secret} 同一份。 */
    private static final String SECRET = "ae0536f9-6450-4606-8e13-5a19ed505da0";
    private static final long CLAIM_UID = 1001L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ArticleRpcClient articleRpcClient;

    @MockitoBean
    private UserRpcClient userRpcClient;

    @BeforeEach
    void setUp() {
        Mockito.when(articleRpcClient.articleRpc())
                .thenReturn(Mockito.mock(ArticleServiceBlockingStub.class));
        Mockito.when(userRpcClient.userRpc())
                .thenReturn(Mockito.mock(UserServiceBlockingStub.class));
    }

    // ==================================================================
    // ① 大小写：Go 不读 ⇒ 8 MiB 上限不适用 ⇒ 闸门不许触发
    // ==================================================================

    @Test
    @DisplayName("🔴 Content-Type: Application/JSON + 9 MiB body ⇒ 8 MiB 闸门**不触发**（Go 不读 body）")
    void mixedCaseContentTypeDoesNotTriggerTheEightMiBGate() throws Exception {
        var response = mockMvc.perform(post("/v1/article")
                        .header("Authorization", bearer(tokenFor(CLAIM_UID)))
                        .header(HttpHeaders.CONTENT_TYPE, "Application/JSON")
                        .content(oversizedBody()))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse();

        String body = response.getContentAsString();
        assertFalse(body.contains("request entity too large"),
                "Go 的 strings.Contains 大小写敏感 ⇒ Application/JSON ⇒ Go **不读 body** ⇒ "
                        + "8 MiB 上限（maxBodyLen）根本不适用 ⇒ 闸门不许触发；"
                        + "实际 body=" + body);
        assertTrue(body.contains("is not set"),
                "闸门未触发 ⇒ body 被丢弃（Go 的 UnmarshalJsonMap(nil, v)）⇒ DTO 零值 ⇒ "
                        + "必填字段校验失败 ⇒ body 应含 \"is not set\"；实际 body=" + body);
    }

    @Test
    @DisplayName("🔴 Content-Type: application/vnd.api+json + 9 MiB body ⇒ 8 MiB 闸门**不触发**（Go 是字面子串）")
    void vendorJsonSuffixDoesNotTriggerTheEightMiBGate() throws Exception {
        var response = mockMvc.perform(post("/v1/article")
                        .header("Authorization", bearer(tokenFor(CLAIM_UID)))
                        .header(HttpHeaders.CONTENT_TYPE, "application/vnd.api+json")
                        .content(oversizedBody()))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse();

        String body = response.getContentAsString();
        assertFalse(body.contains("request entity too large"),
                "Go: strings.Contains(\"application/vnd.api+json\", \"application/json\") == false ⇒ 不读 body ⇒ "
                        + "8 MiB 上限不适用；实际 body=" + body);
        assertTrue(body.contains("is not set"), "实际 body=" + body);
    }

    // ==================================================================
    // ② 对照：标准小写 ⇒ 闸门**必须**触发（证明上一条不是「闸门根本没接线」的假绿）
    // ==================================================================

    @Test
    @DisplayName("对照：Content-Type: application/json + 9 MiB body ⇒ 8 MiB 闸门**触发** ⇒ body = request entity too large")
    void standardJsonContentTypeTriggersTheEightMiBGate() throws Exception {
        var response = mockMvc.perform(post("/v1/article")
                        .header("Authorization", bearer(tokenFor(CLAIM_UID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oversizedBody()))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse();

        assertTrue(response.getContentAsString().contains("request entity too large"),
                "标准 JSON 头必须命中 ⇒ 8 MiB 上限适用 ⇒ 闸门触发；"
                        + "若这条不成立，说明闸门根本没接上 ⇒ 上面两条就是假绿；"
                        + "实际 body=" + response.getContentAsString());
    }

    // ==================================================================
    // helpers
    // ==================================================================

    /**
     * 一个<b>整体超过 8 MiB</b> 的合法 JSON 文档。
     *
     * <p>⚠️ 必须是**一个** JSON 值整体超过 8 MiB —— 若由多个小 JSON 拼成，
     * Go 的 {@code json.Decoder} 只读**第一个**值 ⇒ 不会触发截断。
     */
    private static String oversizedBody() {
        return "{\"title\":\"t\",\"content\":\"c\",\"brief\":\"" + "x".repeat(9 * 1024 * 1024) + "\"}";
    }

    private static String tokenFor(long uid) {
        return JwtUtil.getToken(SECRET, System.currentTimeMillis() / 1000L, 3600L, uid);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
