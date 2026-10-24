package com.rag.hot.hot.api.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * wire 契约测试 —— 用<b>裸 {@link ObjectMapper}</b>（不加载 Spring 的任何定制）
 * 锁住 15 个 snake_case 字段名与「缺席 vs null」的字面行为。
 *
 * <p><b>为什么用裸 mapper 而不是 MockMvc</b>：本类要验的是「Jackson 按注解怎么写」，
 * 混入 Spring 的 mapper 定制会让失败原因不可分辨（这正是 like-api 的
 * {@code LikeApiMvcPipelineTest} 类注释里点名的「standaloneSetup 式假绿」）。
 */
class HotApiWireContractTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("Go types.go:6-20 —— HotArticleCard 的 13 个字段名逐字一致")
    void itemUsesGoSnakeCaseFieldNames() throws Exception {
        HotArticleCard card = new HotArticleCard();
        card.setArticleId("a1");
        card.setRank(1);
        card.setHotScore(100L);
        card.setTitle("t");
        card.setBrief("b");
        card.setCoverImageUrl("c");
        card.setAuthorId("u1");
        card.setCreateTime(1700000000000L);
        card.setViewCount(1);
        card.setLikeCount(2);
        card.setCommentCount(3);
        card.setManualTypeTag("评测");
        card.setSecondaryTags(List.of("x"));

        String json = mapper.writeValueAsString(card);
        for (String field : List.of("\"article_id\"", "\"rank\"", "\"hot_score\"", "\"title\"",
                "\"brief\"", "\"cover_image_url\"", "\"author_id\"", "\"create_time\"",
                "\"view_count\"", "\"like_count\"", "\"comment_count\"", "\"manual_type_tag\"",
                "\"secondary_tags\"")) {
            assertTrue(json.contains(field), "缺少字段 " + field + "，实际=" + json);
        }
        assertEquals(13, mapper.readTree(json).size(), "13 个字段，实际=" + json);
    }

    @Test
    @DisplayName("⚠️ secondary_tags 为 null 时输出 null 而非 []（Go 的 nil slice）")
    void emptySecondaryTagsIsNullOnTheWire() throws Exception {
        HotArticleCard card = new HotArticleCard();
        card.setSecondaryTags(null);

        assertTrue(mapper.writeValueAsString(card).contains("\"secondary_tags\":null"),
                "Go 无 omitempty + nil slice ⇒ 必须是 null");
    }

    @Test
    @DisplayName("Go types.go:27-33 —— HotArticlesResp 的 5 个字段名逐字一致")
    void respUsesGoSnakeCaseFieldNames() throws Exception {
        HotArticlesResp resp = new HotArticlesResp(new ArrayList<>(), 0L, 1, 20, "rolling");

        String json = mapper.writeValueAsString(resp);
        for (String field : List.of("\"items\"", "\"total\"", "\"page\"", "\"page_size\"", "\"scope\"")) {
            assertTrue(json.contains(field), "缺少字段 " + field + "，实际=" + json);
        }
        assertTrue(json.contains("\"items\":[]"), "空页必须是 [] 而不是 null，实际=" + json);
    }

    @Test
    @DisplayName("⚠️ hot_score 是 Go 的 uint32：Java 侧用无符号 long 输出，避免 ≥2^31 变负数")
    void hotScoreIsUnsignedOnTheWire() throws Exception {
        HotArticleCard card = new HotArticleCard();
        // protobuf-java 把 uint32 映射成有符号 int：0xFFFFFFFF → -1
        int rawFromProto = -1;
        card.setHotScore(Integer.toUnsignedLong(rawFromProto));

        assertEquals(4294967295L, card.getHotScore());
        assertTrue(mapper.writeValueAsString(card).contains("\"hot_score\":4294967295"),
                "Go 的 encoding/json 对 uint32 写无符号十进制");
    }
}