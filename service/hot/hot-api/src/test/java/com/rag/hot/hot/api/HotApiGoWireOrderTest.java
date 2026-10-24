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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * <b>wire 字节级</b>对拍：响应体的<b>字段顺序</b>必须等于 Go 的结构体声明序。
 *
 * <h2>为什么必须是真实上下文 + 原始字节</h2>
 * <p>字段顺序由<b>运行时那个 mapper</b> 决定，而 Spring Boot 装配出来的 mapper 与
 * {@code new ObjectMapper()}、{@code Jackson2ObjectMapperBuilder.json().build()}
 * 都可能不同 ⇒ <b>用裸 mapper 断言字段顺序是无效的</b>
 * （这正是 {@code HotApiWireContractTest} 曾经踩的坑：它用裸 mapper 断言，
 * 于是「运行时是字母序」这件事<b>永远测不出来</b>）。
 * <p>⇒ 本类必须：① 真的起 Spring 上下文；② 断言<b>原始响应体</b>，不是解析后的 JSON 树
 * （JSON 树会把字段顺序信息丢掉）。
 *
 * <h2>Go 出处</h2>
 * <ul>
 *   <li>{@code service/common/response/response.go} — {@code code, msg, data}</li>
 *   <li>{@code service/hot/api/internal/types/types.go:27-33} — {@code items, total, page, page_size, scope}</li>
 *   <li>{@code service/hot/api/internal/types/types.go:6-20} — 13 个 item 字段的声明序</li>
 *   <li>Go 的 {@code encoding/json} 按<b>结构体声明序</b>输出（{@code core/jsonx/json.go:16-33}
 *       用 {@code json.Encoder}，默认不排序）</li>
 * </ul>
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
class HotApiGoWireOrderTest {

    /** Go {@code types.go:6-20} 的声明序。 */
    private static final String[] GO_ITEM_FIELD_ORDER = {
            "article_id", "rank", "hot_score", "title", "brief", "cover_image_url", "author_id",
            "create_time", "view_count", "like_count", "comment_count", "manual_type_tag", "secondary_tags"
    };

    /** Go {@code types.go:27-33} 的声明序。 */
    private static final String[] GO_RESP_FIELD_ORDER = {"items", "total", "page", "page_size", "scope"};

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HotRpcClient hotRpcClient;

    @MockitoBean
    private ArticleRpcClient articleRpcClient;

    @Test
    @DisplayName("🔴 信封 + data + item 的字段顺序逐字等于 Go 的声明序")
    void fieldOrderMatchesGoDeclarationOrder() throws Exception {
        stubHappyPath();

        String body = mockMvc.perform(get("/hot/v1/articles"))
                .andReturn().getResponse().getContentAsString();

        // Go：response.Response{Code, Msg, Data} ⇒ {"code":…,"msg":…,"data":…}
        assertThat(body).startsWith("{\"code\":200,\"msg\":\"OK\",\"data\":{");

        // Go：types.HotArticlesResp{Items, Total, Page, PageSize, Scope}
        assertThat(body).containsPattern("\"data\":\\{\"items\":\\[\\{");

        // item 内部：13 个字段的相对顺序
        int cursor = body.indexOf("\"items\":[");
        for (String field : GO_ITEM_FIELD_ORDER) {
            int at = body.indexOf("\"" + field + "\":", cursor);
            assertThat(at).as("字段 %s 必须出现在 wire 上（body=%s）", field, body).isGreaterThanOrEqualTo(0);
            cursor = at;
        }

        // data 层 5 个字段的相对顺序
        cursor = body.indexOf("\"data\":{");
        for (String field : GO_RESP_FIELD_ORDER) {
            int at = body.indexOf("\"" + field + "\":", cursor);
            assertThat(at).as("字段 %s 必须出现在 wire 上（body=%s）", field, body).isGreaterThanOrEqualTo(0);
            cursor = at;
        }
    }

    private void stubHappyPath() {
        when(hotRpcClient.getHotArticles(anyInt(), anyLong())).thenReturn(
                GetHotArticlesResponse.newBuilder()
                        .addItems(HotArticleItem.newBuilder().setArticleId("a1").setHotScore(4242))
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
