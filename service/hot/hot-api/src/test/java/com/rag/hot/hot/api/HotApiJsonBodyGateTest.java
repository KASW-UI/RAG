package com.rag.hot.hot.api;

import com.rag.article.article.proto.Article;
import com.rag.article.article.proto.ArticleStatus;
import com.rag.article.article.proto.GetArticleResponse;
import com.rag.common.exception.BusinessException;
import com.rag.hot.hot.api.client.ArticleRpcClient;
import com.rag.hot.hot.api.client.HotRpcClient;
import com.rag.hot.hot.api.form.GoFormValues;
import com.rag.hot.hot.proto.GetHotArticlesResponse;
import com.rag.hot.hot.proto.HotArticleItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 🔴 守门：hot 域的 JSON body 拒绝路径必须复刻 Go 的<b>两道闸</b>。
 *
 * <h2>Go 出处（真源 = L2 框架层 go-zero@v1.10.2）</h2>
 * <pre>
 * rest/httpx/requests.go:117-124
 *     func ParseJsonBody(r *http.Request, v any) error {
 *         if withJsonBody(r) {
 *             reader := io.LimitReader(r.Body, maxBodyLen)   // 闸 ②：只读前 8 MiB
 *             return mapping.UnmarshalJsonReader(reader, v)
 *         }
 *         return mapping.UnmarshalJsonMap(nil, v)
 *     }
 * rest/httpx/requests.go:153-155
 *     func withJsonBody(r *http.Request) bool {
 *         return r.ContentLength &gt; 0 &amp;&amp; strings.Contains(r.Header.Get(header.ContentType), header.ApplicationJson)
 *     }                                                       // 闸 ①：命中才读（大小写敏感）
 * rest/httpx/requests.go:21
 *     maxBodyLen = 8 &lt;&lt; 20 // 8MB
 * </pre>
 *
 * <h2>🔴 本类守的是 2026-10-25 第十六轮修掉的漂移</h2>
 * <p>修之前 hot 域本地抄了一份判据，且<b>没有第二道闸</b>：
 * <ul>
 *   <li>闸 ① 缺：本地用 {@code request.getContentLength()}（<b>int</b>）⇒ &gt; 2 GiB 时 Tomcat 返回 -1
 *       ⇒ 判据为假（不读）；Go 的 {@code r.ContentLength} 是 <b>int64</b> ⇒ 仍 &gt; 0 ⇒ 读。</li>
 *   <li>闸 ② 缺：本地用 {@code in.readAllBytes()} <b>整个读进堆里</b> ⇒ 一个 9 MiB 的合法 JSON
 *       在 Go 侧被<b>截断</b>（⇒ 解析失败 ⇒ 5001）、在 RAG 侧却<b>成功解析</b>（⇒ 200）⇒ 两侧相反。</li>
 * </ul>
 * ⇒ 期望值来自<b>双侧活体探针</b>（{@code tools/json-body-probe/}，Go 半边直接调
 * {@code httpx.Parse}），不是照源码推的。
 *
 * <h2>⚠️ 为什么 MockMvc 在这里够用</h2>
 * <p>本类验的是「<b>应用层</b>怎么读 body、怎么判」」，不验容器行为
 * ⇒ MockMvc 的 {@code MockHttpServletRequest} 提供了与 Tomcat 同形的两个输入
 * （原始 Content-Type 头值 / content 长度）⇒ 足够。
 * <br>⇒ 「Tomcat 真容器上头值不被归一」那条<b>承重事实</b>由
 * {@code service/common} 的 {@code GoStyleJsonBodyRawHeaderTomcatTest}（真 Tomcat + 裸 socket）单独守门。
 */
@SpringBootTest(
        classes = HotApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "grpc.client.hot-rpc.address=static://127.0.0.1:65535",
                "grpc.client.hot-rpc.negotiation-type=plaintext",
                "grpc.client.article-rpc.address=static://127.0.0.1:65535",
                "grpc.client.article-rpc.negotiation-type=plaintext",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
@AutoConfigureMockMvc
@DisplayName("🔴 hot 域 JSON body：Go 的两道闸（命中才读 + 只读前 8 MiB）")
class HotApiJsonBodyGateTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HotRpcClient hotRpcClient;

    @MockitoBean
    private ArticleRpcClient articleRpcClient;

    // ==================================================================
    // ① 闸 ②：Go 的 maxBodyLen = 8 MiB（本轮修掉的那条）
    //
    // ⚠️ **端到端与解析层要分开看**（2026-10-25 实测出来的层次）：
    //   · 端到端：Go 的 MaxBytes **默认 1 MiB 且全局生效**
    //     （go-zero rest/config.go:53 `MaxBytes int64 json:",default=1048576"` +
    //      rest/config.go:21 `Middlewares.MaxBytes bool json:",default=true"` +
    //      rest/engine.go:163-164 `chn.Append(handler.MaxBytesHandler(...))`）
    //     ⇒ 一个 9 MiB 的 body 在 Go 侧**先被 MaxBytes 拦成裸 413**（net/http 的
    //      `requestTooLarge()`），**根本走不到** ParseJsonBody ⇒ RAG 也必须 413。
    //   · 解析层：`httpx.ParseJsonBody` 自己还有一道 `io.LimitReader(r.Body, 8MiB)`
    //     ⇒ 它只在「body 过了 MaxBytes」时才有机会生效。
    //   ⇒ 本端点的 MaxBytes = 1 MiB < 8 MiB ⇒ **端到端这一段不可达**
    //     ⇒ 那道 8 MiB 闸的价值在**解析层**（探针 + 直接调用），不在端到端。
    // ==================================================================

    @Test
    @DisplayName("🔴 9 MiB body 端到端 ⇒ **413**（Go 的 MaxBytes 默认 1 MiB 全局生效 ⇒ 先于 JSON 闸）")
    void nineMiBBodyIsRejectedByMaxBytesLikeGo() throws Exception {
        stubHappyPath();

        mockMvc.perform(get("/hot/v1/articles").param("page", "5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oversizedBody()))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    @DisplayName("🔴 解析层：9 MiB **单个** JSON 对象 ⇒ 被 8 MiB 截断 ⇒ 解析失败（Go 的 maxBodyLen）")
    void nineMiBJsonBodyIsTruncatedAtEightMiBInTheParseLayer() {
        // ⚠️ 刻意**直接调用**生产方法，而不是走端到端 ——
        //    端到端那一段被 MaxBytes（1 MiB）先拒了 ⇒ 测不到 ParseJsonBody 这一层。
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent(oversizedBody().getBytes(java.nio.charset.StandardCharsets.UTF_8));

        BusinessException e = assertThrows(BusinessException.class,
                () -> GoFormValues.requireParsableJsonBody(request),
                "Go 的 ParseJsonBody 用 io.LimitReader(r.Body, 8<<20) ⇒ 9 MiB 的文档被截断 ⇒ "
                        + "解析失败 ⇒ 5001；RAG 若成功 ⇒ 说明没复刻 maxBodyLen");

        assertEquals(5001, e.getCode(), "Go 侧这个错误不是 *CodeError ⇒ 恒 5001");
        assertEquals("internal server error", e.getMessage());
    }

    @Test
    @DisplayName("对照：512 KiB **合法** JSON body ⇒ 200（闸 ② 不能误伤正常路径）")
    void halfMiBJsonBodyStillReachesBusiness() throws Exception {
        stubHappyPath();

        mockMvc.perform(get("/hot/v1/articles").param("page", "5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBodyOf(512 * 1024)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ==================================================================
    // ② 闸 ①：命中才读，且大小写敏感（= Go 的 strings.Contains）
    // ==================================================================

    @Test
    @DisplayName("🔴 Content-Type: Application/JSON + 畸形 body ⇒ **不读** ⇒ 200（Go 大小写敏感）")
    void mixedCaseContentTypeIsNotRead() throws Exception {
        stubHappyPath();

        mockMvc.perform(get("/hot/v1/articles").param("page", "5")
                        .header(HttpHeaders.CONTENT_TYPE, "Application/JSON")
                        .content("{"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.page").value(5));
    }

    @Test
    @DisplayName("🔴 Content-Type: APPLICATION/JSON + 畸形 body ⇒ **不读** ⇒ 200")
    void upperCaseContentTypeIsNotRead() throws Exception {
        stubHappyPath();

        mockMvc.perform(get("/hot/v1/articles").param("page", "5")
                        .header(HttpHeaders.CONTENT_TYPE, "APPLICATION/JSON")
                        .content("{"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("🔴 Content-Type: application/vnd.api+json + 畸形 body ⇒ **不读** ⇒ 200（Go 是字面子串）")
    void vendorJsonSuffixIsNotRead() throws Exception {
        stubHappyPath();

        mockMvc.perform(get("/hot/v1/articles").param("page", "5")
                        .header(HttpHeaders.CONTENT_TYPE, "application/vnd.api+json")
                        .content("{"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("对照：标准 application/json + 畸形 body ⇒ **读** ⇒ 5001（闸 ① 仍生效）")
    void standardJsonContentTypeIsStillRejected() throws Exception {
        stubHappyPath();

        mockMvc.perform(get("/hot/v1/articles").param("page", "5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));
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
        return jsonBodyOf(9 * 1024 * 1024);
    }

    /** 造一个 padding 长度为 {@code padding} 的合法 JSON 对象。 */
    private static String jsonBodyOf(int padding) {
        return "{\"brief\":\"" + "x".repeat(padding) + "\"}";
    }

    private void stubHappyPath() {
        when(hotRpcClient.getHotArticles(anyInt(), anyLong())).thenReturn(
                GetHotArticlesResponse.newBuilder()
                        .addItems(HotArticleItem.newBuilder().setArticleId("a1").setHotScore(4242))
                        .addItems(HotArticleItem.newBuilder().setArticleId("a2").setHotScore(99))
                        .build());
        when(articleRpcClient.getPublicArticle(any(), anyLong())).thenAnswer(inv -> {
            String id = inv.getArgument(0);
            return GetArticleResponse.newBuilder()
                    .setArticle(Article.newBuilder()
                            .setId(id)
                            .setTitle("title-" + id)
                            .setStatus(ArticleStatus.PUBLISHED)
                            .build())
                    .build();
        });
    }
}
