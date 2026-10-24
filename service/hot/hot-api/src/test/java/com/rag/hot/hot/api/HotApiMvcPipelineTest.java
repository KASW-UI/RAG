package com.rag.hot.hot.api;

import com.rag.article.article.proto.Article;
import com.rag.article.article.proto.ArticleStatus;
import com.rag.article.article.proto.GetArticleResponse;
import com.rag.hot.hot.api.client.ArticleRpcClient;
import com.rag.hot.hot.api.client.HotRpcClient;
import com.rag.hot.hot.proto.GetHotArticlesResponse;
import com.rag.hot.hot.proto.HotArticleItem;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * hot-api <b>真实 MVC 管线</b>测试 —— 锁住 Go {@code service/hot/api} 的 L1 契约。
 *
 * <h2>为什么必须是真实管线</h2>
 * 本服务最容易漂移的三件事<b>只在真实管线里测得到</b>：
 * <table border="1">
 *   <caption>真实管线 vs standaloneSetup</caption>
 *   <tr><th>要验的东西</th><th>依赖</th></tr>
 *   <tr>
 *     <td>成功信封只包一层</td>
 *     <td>{@code GlobalResponseAdvice} 对 {@code body instanceof Response} 的放行分支</td>
 *   </tr>
 *   <tr>
 *     <td>错误信封的 code <b>不是</b> 200</td>
 *     <td>同一个 advice + {@code @ExceptionHandler} 返回 {@code ResponseEntity} 时的相互作用</td>
 *   </tr>
 *   <tr>
 *     <td>404/405 是裸响应</td>
 *     <td>{@code GoStyleRouterErrorResponses} 的字节形状 + advice 对非 JSON 的放行</td>
 *   </tr>
 * </table>
 *
 * <p><b>唯一被替换的是 gRPC 边界</b>（两个 client）—— 被测语义全在 api 进程内。
 * 另注：<b>匿名可访问</b>本身就是被测语义（Go 的 {@code routes.go} 没有任何鉴权中间件），
 * 因此这里刻意不带任何 token。
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
class HotApiMvcPipelineTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HotRpcClient hotRpcClient;

    @MockitoBean
    private ArticleRpcClient articleRpcClient;

    @Test
    @DisplayName("Go :45-51 —— 成功信封恒为 200 + {code:200,msg:\"OK\",data}，匿名可访问")
    void successEnvelopeIsGoShaped() throws Exception {
        stubHappyPath();

        mockMvc.perform(get("/hot/v1/articles"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("OK"))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.page_size").value(20))
                .andExpect(jsonPath("$.data.scope").value("rolling"))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].article_id").value("a1"))
                .andExpect(jsonPath("$.data.items[0].rank").value(1))
                .andExpect(jsonPath("$.data.items[0].hot_score").value(4242));
    }

    @Test
    @DisplayName("⚠️ 顶层 code 不得恒为 200（AGENTS.md §3.3 的二次包裹回归）")
    void errorEnvelopeIsNotDoubleWrapped() throws Exception {
        when(hotRpcClient.getHotArticles(anyInt(), anyLong()))
                .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

        mockMvc.perform(get("/hot/v1/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001))
                .andExpect(jsonPath("$.msg").value("internal server error"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("⚠️ query 解析失败 → 200 + 5001，不是 400（Go SetErrorHandler 恒 200）")
    void queryParseFailureMapsToTwoHundredWithFiveThousandAndOne() throws Exception {
        mockMvc.perform(get("/hot/v1/articles").param("page", "abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001))
                .andExpect(jsonPath("$.msg").value("internal server error"));
    }

    @Test
    @DisplayName("⚠️ ?page=（空值）在 Go 取默认值，不是解析失败")
    void emptyQueryValueFallsBackToGoDefault() throws Exception {
        stubHappyPath();

        mockMvc.perform(get("/hot/v1/articles").param("page", "").param("page_size", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.page_size").value(20));
    }

    @Test
    @DisplayName("⚠️ page_size 上限 50 生效（Go :185-187）")
    void pageSizeCapIsApplied() throws Exception {
        stubHappyPath();

        mockMvc.perform(get("/hot/v1/articles").param("page_size", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page_size").value(50));
    }

    /**
     * ⚠️ **U9/D2 系统变异补出来的边界用例**（2026-10-24 第九轮）。
     *
     * <p><b>为什么必须有这条</b>：上面的 {@code pageSizeCapIsApplied} 请求的是
     * {@code page_size=999} —— <b>远超上限</b> ⇒ 把上限从「{@code > 50}」放宽到
     * 「{@code > 51}」它**照样**会被截到 50 ⇒ <b>抓不到那个变异</b>。
     * ⇒ 真正能区分「{@code > 50}」与「{@code > 51}」的输入是
     * <b>恰好越过上限一格的 {@code 51}</b>，此前<b>没有任何测试</b>钉它。
     *
     * <p><b>Go 出处</b>：{@code getHotArticlesLogic.go:185-187}
     * <pre>
     * if pageSize > maxPageSize {      // maxPageSize = 50
     *     pageSize = maxPageSize
     * }
     * </pre>
     * ⇒ {@code 50} 不截断（还是 50）、{@code 51} 截断成 50、{@code 999} 截断成 50。
     * 本测试把三个值都钉住，其中 {@code 51} 是**唯一**能发现「上限被放宽」的输入。
     */
    @Test
    @DisplayName("⚠️ page_size 恰好越过上限一格（51）时也必须截到 50 —— 边界值")
    void pageSizeCapBoundaryIsApplied() throws Exception {
        stubHappyPath();

        // 恰好等于上限：Go 不截断（pageSize > 50 才截）⇒ 仍是 50
        mockMvc.perform(get("/hot/v1/articles").param("page_size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page_size").value(50));

        // 恰好越过上限一格：Go 截断成 50（**唯一**能发现「上限被放宽」的输入）
        mockMvc.perform(get("/hot/v1/articles").param("page_size", "51"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page_size").value(50));
    }

    @Test
    @DisplayName("⚠️ 404 是 Go 的裸响应：text/plain + nosniff + 尾随换行，无信封")
    void notFoundIsBareGoStyleResponse() throws Exception {
        mockMvc.perform(get("/hot/v1/nope"))
                .andExpect(status().isNotFound())
                .andExpect(content().string("404 page not found\n"))
                .andExpect(content().contentType("text/plain;charset=utf-8"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    @DisplayName("⚠️ 405 是裸状态码 + Allow 头，无 body、无信封")
    void methodNotAllowedIsBareStatusCode() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .request(HttpMethod.POST, "/hot/v1/articles"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().string(""));
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