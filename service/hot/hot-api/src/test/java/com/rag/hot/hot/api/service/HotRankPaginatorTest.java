package com.rag.hot.hot.api.service;

import com.rag.hot.hot.api.dto.HotArticleCard;
import com.rag.hot.hot.api.dto.HotArticlesReq;
import com.rag.hot.hot.api.dto.HotArticlesResp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@link HotRankPaginator} 的数值契约测试 —— 零 mock、零 IO。
 *
 * <p><b>对照基准</b>：Go {@code getHotArticlesLogic.go:172-208} +
 * {@code getHotArticlesLogic_test.go:62-95}（默认值与封顶）、
 * {@code :97-148}（过滤保序 / 跨页稠密 rank）。
 */
class HotRankPaginatorTest {

    @Nested
    @DisplayName("normalize —— 对齐 Go normalizePagination(:172-190)")
    class Normalize {

        @Test
        @DisplayName("缺省/非正值 → page=1, page_size=20")
        void appliesGoDefaults() {
            int[] r = HotRankPaginator.normalize(new HotArticlesReq(0, 0));
            assertEquals(1, r[0]);
            assertEquals(20, r[1]);
        }

        @Test
        @DisplayName("null 入参也走默认值（Go 函数体内有 if req != nil 分支）")
        void toleratesNull() {
            int[] r = HotRankPaginator.normalize(null);
            assertEquals(1, r[0]);
            assertEquals(20, r[1]);
        }

        @Test
        @DisplayName("page_size 超过 50 被封顶为 50（Go _test.go:85-94）")
        void capsPageSize() {
            int[] r = HotRankPaginator.normalize(new HotArticlesReq(2, 999));
            assertEquals(2, r[0]);
            assertEquals(50, r[1]);
        }

        @Test
        @DisplayName("负 page 归一化为 1，负 page_size 归一化为 20")
        void normalizesNegatives() {
            int[] r = HotRankPaginator.normalize(new HotArticlesReq(-7, -3));
            assertEquals(1, r[0]);
            assertEquals(20, r[1]);
        }
    }

    @Nested
    @DisplayName("paginate —— 对齐 Go paginate(:192-208)")
    class Paginate {

        @Test
        @DisplayName("total<=0 → 空页 [0,0]")
        void emptyTotalYieldsEmptyRange() {
            assertArrayEqualsRange(new int[]{0, 0}, HotRankPaginator.paginate(0, 1, 20));
            assertArrayEqualsRange(new int[]{0, 0}, HotRankPaginator.paginate(-1, 1, 20));
        }

        @Test
        @DisplayName("越界页 → 空页而不是报错")
        void outOfRangeYieldsEmptyRange() {
            assertArrayEqualsRange(new int[]{0, 0}, HotRankPaginator.paginate(3, 2, 20));
        }

        @Test
        @DisplayName("末页被 total 截断")
        void lastPageIsTruncated() {
            assertArrayEqualsRange(new int[]{20, 25}, HotRankPaginator.paginate(25, 2, 20));
        }

        @Test
        @DisplayName("⚠️ int32 乘法溢出 → 空页（把参数改成 long 会让这条失效）")
        void overflowYieldsEmptyRangeNotError() {
            // (page-1)*pageSize 在 int32 里溢出成负数 ⇒ 命中 Go 的 start < 0 分支
            assertArrayEqualsRange(new int[]{0, 0}, HotRankPaginator.paginate(100, 100_000_000, 50));
        }
    }

    @Nested
    @DisplayName("assignRanks —— 对齐 Go hydrateArticles 末段(:114-123)")
    class AssignRanks {

        @Test
        @DisplayName("rank 是过滤后的稠密序号")
        void assignsDenseRanks() {
            List<HotArticleCard> cards = cards("a", null, "c");
            List<HotArticleCard> ranked = HotRankPaginator.assignRanks(cards);

            assertEquals(2, ranked.size());
            assertEquals("a", ranked.get(0).getArticleId());
            assertEquals(1, ranked.get(0).getRank());
            assertEquals("c", ranked.get(1).getArticleId());
            assertEquals(2, ranked.get(1).getRank(), "被跳过的元素不占 rank");
        }

        @Test
        @DisplayName("全为 null 时返回空列表（不是 null）")
        void allSkippedYieldsEmptyList() {
            List<HotArticleCard> ranked =
                    HotRankPaginator.assignRanks(Arrays.asList(null, null, null));
            assertNotNull(ranked);
            assertEquals(0, ranked.size());
        }
    }

    @Nested
    @DisplayName("slice —— 对齐 Go :78-88")
    class Slice {

        @Test
        @DisplayName("total 是过滤后的总数，与分页参数无关")
        void totalIsFilteredWindowSize() {
            HotArticlesResp resp = HotRankPaginator.slice(cards("a", "b", "c"), 1, 1);
            assertEquals(3, resp.getTotal());
            assertEquals(1, resp.getItems().size());
            assertEquals("a", resp.getItems().get(0).getArticleId());
        }

        @Test
        @DisplayName("⚠️ 第 2 页首条的 rank 是全局稠密序号而不是页内序号（Go _test.go:135-147）")
        void secondPageKeepsCrossPageDenseRank() {
            // rank 在分页**之前**按过滤后下标重算（Go :119），所以第 2 页首条是 rank=2
            List<HotArticleCard> ranked = HotRankPaginator.assignRanks(cards("a", "b", "c"));
            HotArticlesResp resp = HotRankPaginator.slice(ranked, 2, 1);

            assertEquals(1, resp.getItems().size());
            assertEquals("b", resp.getItems().get(0).getArticleId());
            assertEquals(2, resp.getItems().get(0).getRank());
            assertEquals(3, resp.getTotal(), "total 是过滤后的窗口大小，与分页参数无关");
        }

        @Test
        @DisplayName("空页的 items 是空列表（JSON 为 [] 而不是 null）")
        void emptyPageYieldsEmptyListNotNull() {
            HotArticlesResp resp = HotRankPaginator.slice(Collections.emptyList(), 1, 20);
            assertNotNull(resp.getItems());
            assertEquals(0, resp.getItems().size());
            assertEquals(0, resp.getTotal());
            assertEquals("rolling", resp.getScope());
            assertEquals(1, resp.getPage());
            assertEquals(20, resp.getPageSize());
        }

        @Test
        @DisplayName("scope 恒为 rolling（Go 编译期常量 :30/:87）")
        void scopeIsAlwaysRolling() {
            assertEquals("rolling", HotRankPaginator.slice(cards("a"), 1, 20).getScope());
            assertEquals("rolling", HotRankPaginator.ROLLING_SCOPE);
        }
    }

    private static void assertArrayEqualsRange(int[] expected, int[] actual) {
        assertEquals(expected[0], actual[0], "start");
        assertEquals(expected[1], actual[1], "end");
    }

    private static List<HotArticleCard> cards(String... idsOrNull) {
        List<HotArticleCard> list = new ArrayList<>();
        for (String id : idsOrNull) {
            if (id == null) {
                list.add(null);
                continue;
            }
            HotArticleCard card = new HotArticleCard();
            card.setArticleId(id);
            list.add(card);
        }
        return list;
    }
}