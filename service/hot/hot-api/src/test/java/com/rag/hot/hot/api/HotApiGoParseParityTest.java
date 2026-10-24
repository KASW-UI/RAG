package com.rag.hot.hot.api;

import com.rag.article.article.proto.Article;
import com.rag.article.article.proto.ArticleStatus;
import com.rag.article.article.proto.GetArticleResponse;
import com.rag.hot.hot.api.client.ArticleRpcClient;
import com.rag.hot.hot.api.client.HotRpcClient;
import com.rag.hot.hot.proto.GetHotArticlesResponse;
import com.rag.hot.hot.proto.HotArticleItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <b>Go 解析语义对拍</b>：把 go-zero {@code httpx.Parse} 在本端点上的行为逐条钉住。
 *
 * <h2>为什么必须用真实管线</h2>
 * 本类要验的每一条都发生在<b>「Spring 怎么把 query/body 交给业务代码」</b>这一层：
 * 同名多值怎么取、form 值个数怎么数、body 什么时候被读。
 * ⇒ 用 {@code @RequestParam} 单元测试根本触不到，必须走真实 MVC 管线。
 *
 * <h2>Go 侧的证据来源</h2>
 * <p>Go 出处：go-zero <b>v1.10.2</b>（{@code github.com/zeromicro/go-zero@v1.10.2}）
 * <ul>
 *   <li>{@code rest/httpx/requests.go:47-72} —— {@code Parse} 的四步顺序</li>
 *   <li>{@code rest/httpx/requests.go:153-155} —— {@code withJsonBody}</li>
 *   <li>{@code rest/httpx/util.go:15,44-61} —— 2048 上限 / 空值丢弃 / {@code []} 后缀</li>
 *   <li>{@code core/mapping/unmarshaler.go:835-846} —— 同名多值取 {@code Index(0)}</li>
 *   <li>{@code core/mapping/jsonunmarshaler.go:45-53} + {@code core/jsonx/json.go:63-77}
 *       —— JSON body 的语法校验与「只读一个值」语义</li>
 * </ul>
 * 期望值由 <b>Go 活体探针</b>（{@code httpx.Parse} + 逐字复制的 {@code types.HotArticlesReq}）跑出来，
 * 不是照着源码推的。
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
class HotApiGoParseParityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HotRpcClient hotRpcClient;

    @MockitoBean
    private ArticleRpcClient articleRpcClient;

    // ==================================================================
    // 规则 4：同名多值取首值（Go unmarshaler.go:835-846 的 Index(0)）
    // ==================================================================

    @Test
    @DisplayName("🔴 ?page=5&page=3 ⇒ page=5（Go 取首值，不是 \"5,3\" 整包 5001）")
    void repeatedQueryParamTakesFirstValueLikeGo() throws Exception {
        stubHappyPath();

        mockMvc.perform(get("/hot/v1/articles").param("page", "5").param("page", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.page").value(5));
    }

    @Test
    @DisplayName("🔴 ?page[]=5&page[]=9 ⇒ page=5（Go 剥掉 [] 后缀后归到同名 key）")
    void arrayNotationTakesFirstValueLikeGo() throws Exception {
        stubHappyPath();

        mockMvc.perform(get("/hot/v1/articles").param("page[]", "5").param("page[]", "9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.page").value(5));
    }

    @Test
    @DisplayName("🔴 ?page=5&page= ⇒ page=5（Go 先丢弃空值再取首值）")
    void emptyValueAmongRepeatedParamsIsDroppedLikeGo() throws Exception {
        stubHappyPath();

        mockMvc.perform(get("/hot/v1/articles").param("page", "5").param("page", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(5));
    }

    @Test
    @DisplayName("🔴 ?page=1,2 ⇒ 5001（Go 的 strconv.ParseInt(\"1,2\") 失败，不做逗号拆分）")
    void commaSeparatedValueIsParseFailureLikeGo() throws Exception {
        stubHappyPath();

        mockMvc.perform(get("/hot/v1/articles").param("page", "1,2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001))
                .andExpect(jsonPath("$.msg").value("internal server error"));
    }

    // ==================================================================
    // 规则 5：form 值个数上限 2048（Go httpx/util.go:15,48-53）
    // ==================================================================

    @Test
    @DisplayName("🔴 2048 个 form 值 ⇒ 正常 200（Go 的上限之内）")
    void formValueCountAtLimitIsAccepted() throws Exception {
        stubHappyPath();

        mockMvc.perform(get("/hot/v1/articles").queryParams(junkParams(2048)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("🔴 2049 个 form 值 ⇒ 5001（Go 的 too many form values 拒绝路径）")
    void formValueCountOverLimitIsRejected() throws Exception {
        stubHappyPath();

        mockMvc.perform(get("/hot/v1/articles").queryParams(junkParams(2049)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001))
                .andExpect(jsonPath("$.msg").value("internal server error"));
    }

    // ==================================================================
    // 规则 6：JSON body 的拒绝路径（Go httpx/requests.go:153-155 + jsonunmarshaler.go:45-53）
    // ==================================================================

    @Test
    @DisplayName("🔴 畸形 JSON body + application/json ⇒ 5001（Go 的 unexpected EOF 拒绝路径）")
    void malformedJsonBodyIsRejectedLikeGo() throws Exception {
        stubHappyPath();

        mockMvc.perform(get("/hot/v1/articles").param("page", "5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5001));
    }

    @Test
    @DisplayName("🔴 顶层不是对象的 JSON body（数组/null/数字/字符串）⇒ 5001")
    void nonObjectJsonBodyIsRejectedLikeGo() throws Exception {
        stubHappyPath();

        for (String body : new String[] {"[1,2]", "null", "123", "\"x\""}) {
            mockMvc.perform(get("/hot/v1/articles").param("page", "5")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(5001));
        }
    }

    @Test
    @DisplayName("🔴 对象形态的 JSON body ⇒ 200，且字段被整体忽略（Go 无 json tag ⇒ 不覆盖 form 值）")
    void objectJsonBodyIsAcceptedAndIgnoredLikeGo() throws Exception {
        stubHappyPath();

        mockMvc.perform(get("/hot/v1/articles").param("page", "5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"page\":9,\"page_size\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.page").value(5))
                .andExpect(jsonPath("$.data.page_size").value(20));
    }

    @Test
    @DisplayName("🔴 非 application/json 的 body ⇒ 完全不读（Go withJsonBody 为 false）")
    void nonJsonContentTypeBodyIsIgnoredLikeGo() throws Exception {
        stubHappyPath();

        mockMvc.perform(get("/hot/v1/articles").param("page", "5")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.page").value(5));
    }

    private org.springframework.util.MultiValueMap<String, String> junkParams(int count) {
        org.springframework.util.LinkedMultiValueMap<String, String> map =
                new org.springframework.util.LinkedMultiValueMap<>();
        for (int i = 0; i < count; i++) {
            map.add("k" + i, "v" + i);
        }
        return map;
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
