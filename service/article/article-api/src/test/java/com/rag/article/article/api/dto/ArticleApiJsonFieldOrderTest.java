package com.rag.article.article.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 🔴🔴 article 域响应 JSON 的<b>字段顺序</b>必须与 Go 的 struct 声明序一致。
 *
 * <h2>它挡的是什么</h2>
 * <p>Go 的 {@code encoding/json} 对 struct <b>按字段声明序</b>输出；
 * Jackson 默认按<b>字母序</b> ⇒ ⇒ 两侧<b>字段与值完全相同</b>，但<b>字节不同</b>。
 * ⇒ 这不是「JSON 语义等价」（JSON 对象键序本就无意义），但本仓的口径是
 * <b>双侧活体逐字节相同</b>（hot 域 <b>D15</b> 的先例：
 * {@code docs/DRIFT/hot/api.md} ⇒ 修法 = {@code @JsonPropertyOrder} 固定声明序
 * ⇒ ⇒ <b>不需要</b>登记为有意偏离）。
 *
 * <h2>实测怎么发现的</h2>
 * <p>2026-10-29 · G3 阶段 1 · <b>A-1 双侧活体对拍</b>（两侧同时起服务、同一行数据）：
 * <pre>
 * Go : {"code":200,"msg":"OK","data":{"articles":[{"id":…,"title":…,"brief":…,…}],"total":1,"page":1,"page_size":20}}
 * RAG: {"code":200,"msg":"OK","data":{"articles":[{"author_id":…,"author_name":…,"brief":…,…}],"page":1,"page_size":20,"total":1}}
 * </pre>
 * ⇒ **字段与值逐条相同，只有键序不同** ⇒ ⇒ 就是本类要钉的那件事。
 *
 * <h2>为什么不能只断言「字段都在」</h2>
 * <p>AGENTS.md §3.3.3：<b>注解存在 ≠ 机制生效</b> ⇒ ⇒
 * {@code @JsonPropertyOrder} 写对了也可能没生效（Jackson 配置覆盖 / DTO 被换掉）⇒
 * ⇒ 本类**真的序列化一次**并把<b>键序</b>取出来断言 ⇒ ⇒ <b>去掉注解即变红</b>。
 */
class ArticleApiJsonFieldOrderTest {

    /** Go {@code service/article/api/internal/types/types.go:6-24} 的 {@code Article} 声明序。 */
    private static final List<String> GO_ARTICLE_ORDER = List.of(
            "id", "title", "brief", "content", "cover_image_url", "manual_type_tag",
            "secondary_tags", "author_id", "author_name", "create_time", "update_time",
            "status", "view_count", "like_count", "comment_count", "share_count", "ext_info");

    /** Go {@code types.go:67-72} 的 {@code ListArticlesResp} 声明序。 */
    private static final List<String> GO_LIST_RESP_ORDER = List.of("articles", "total", "page", "page_size");

    private static List<String> keyOrder(JsonNode node) {
        List<String> keys = new ArrayList<>();
        node.fieldNames().forEachRemaining(keys::add);
        return keys;
    }

    private static Article sampleArticle() {
        Article a = new Article();
        a.setId("9007199254740993");
        a.setTitle("标题");
        a.setBrief("摘要");
        a.setContent("正文");
        a.setCoverImageUrl("https://example.invalid/c.png");
        a.setManualTypeTag("tech");
        a.setSecondaryTags(null);
        a.setAuthorId("1001");
        a.setAuthorName("用户 1001");
        a.setCreateTime(1767323045000L);
        a.setUpdateTime(1767323046000L);
        a.setStatus(2);
        a.setViewCount(1);
        a.setLikeCount(2);
        a.setCommentCount(3);
        a.setShareCount(4);
        a.setExtInfo(null);
        return a;
    }

    @Test
    @DisplayName("🔴 Article 的 JSON 键序 == Go types.Article 的声明序（去掉 @JsonPropertyOrder 即变红）")
    void articleKeyOrderMatchesGoDeclarationOrder() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode node = (ObjectNode) mapper.readTree(mapper.writeValueAsString(sampleArticle()));
        assertEquals(GO_ARTICLE_ORDER, keyOrder(node),
                "Article 的 JSON 键序必须与 Go struct 声明序一致（Jackson 默认字母序 ⇒ 必须靠 @JsonPropertyOrder 固定）");
    }

    @Test
    @DisplayName("🔴 ListArticlesResp 的 JSON 键序 == Go types.ListArticlesResp 的声明序")
    void listRespKeyOrderMatchesGoDeclarationOrder() throws Exception {
        ListArticlesResp resp = new ListArticlesResp();
        resp.setArticles(java.util.List.of(sampleArticle()));
        resp.setTotal(1);
        resp.setPage(1);
        resp.setPageSize(20);

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode node = (ObjectNode) mapper.readTree(mapper.writeValueAsString(resp));
        assertEquals(GO_LIST_RESP_ORDER, keyOrder(node),
                "ListArticlesResp 的 JSON 键序必须与 Go struct 声明序一致");
    }

    @Test
    @DisplayName("📌 Go 侧出处的声明序与本测试写死的期望一致（防「改了 Go 就忘了改这里」）")
    void expectedOrderMatchesGoSourceDeclaration() {
        // ⚠️ 这条是**字符串层面的护栏**：期望值必须逐条等于 Go 侧的 json tag 顺序。
        //    Go 出处：Sea-RideTheWind_golang/service/article/api/internal/types/types.go:6-24 / :67-72
        assertEquals(
                "id,title,brief,content,cover_image_url,manual_type_tag,secondary_tags,"
                        + "author_id,author_name,create_time,update_time,status,view_count,"
                        + "like_count,comment_count,share_count,ext_info",
                String.join(",", GO_ARTICLE_ORDER));
        assertEquals("articles,total,page,page_size", String.join(",", GO_LIST_RESP_ORDER));
    }
}
