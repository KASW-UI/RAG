package com.rag.hot.hot.rpc.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>对拍式守门测试</b>：把 Go 真源算出的值<b>固化成 fixture</b>，逐条断言生产实现与之相同。
 *
 * <h2>为什么必须是「对拍 fixture」而不是「行为测试」</h2>
 * <p>DRIFT-HOTRPC-001：{@code HeavyKeeper.hash()} 曾把 Go 的 <b>{@code uint64} 取模</b>
 * 写成 Java 的<b>有符号</b>取模 + 补正（{@code (int)(h % width)} 再补 {@code +width}）。
 * 后果：FNV-64 最高位为 1 的 key（约 <b>49.5%</b>）被算到<b>另一个桶</b>，
 * 且<b>不崩、不越界</b> ⇒ 既有 46 条绿测试与 10/10 变异验证<b>一个都抓不到</b>
 * （{@code HeavyKeeperTest} 只断言相对次序，从不断言桶位）。
 * ⇒ 只有「拿 Go 算出的<b>具体列号</b>来钉」才守得住。
 *
 * <h2>fixture 的来源（可复现）</h2>
 * <p>Go 侧：把真源 {@code service/hot/heavykeeper/heavykeeper.go}
 * <b>逐字节复制</b>到临时目录（sha256 = {@code 2fef39e7a8e35a19e6774fbfb1ed360e138dd73b5baef3090e58a2bbebd4309e}，
 * 与真源一致），仅追加一个<b>最小导出</b>（{@code ProbeHash} 暴露私有 {@code hash}），
 * 再用 {@code go run} 跑探针。<b>Go 仓零改动。</b>
 * <p>Java 侧：<b>直接调用生产类</b> {@link HeavyKeeper}（不是另写一份算法），
 * 私有 {@code hash} 用反射取。
 *
 * <h2>三组列号 fixture + 两组榜单 fixture 各覆盖什么</h2>
 * <ol>
 *   <li><b>列号表</b>：3 组 (width, depth) 组合 × 17 个 key × 每个 seed 一列
 *       ⇒ 直接钉住「桶位」这个语义位。</li>
 *   <li><b>重碰撞榜单</b>：{@code width=7 / depth=3}、60 个 key ⇒ 几乎每个桶都被抢占，
 *       榜单<b>完全</b>由桶位决定 ⇒ 桶位错则榜单错。</li>
 *   <li><b>生产口径变权榜单</b>：{@code width=1000 / depth=5}、400 个 key、权重 {@code (i%13)+1}
 *       ⇒ 13 个分数档、第 100 名恰好落在档位边界 ⇒ 榜单对桶位敏感。</li>
 * </ol>
 *
 * <p>⚠️ 两组榜单 fixture 都用 {@code decay=0}：此时 Go 的冲突分支
 * {@code rand.Float64() < math.Pow(0, count)} <b>恒为 false</b>
 * ⇒ 冲突时桶<b>不被抢占</b> ⇒ 结果<b>完全确定</b>，可以固化成 fixture。
 * （{@code decay>0} 时 Go 与 Java 的随机数不同源 ⇒ 不可对拍，只能断言相对次序。）
 *
 * <p>⚠️ 本测试<b>不</b>断言「Go 与 Java 用了同一个随机数」——那不可能；
 * 它断言的是<b>桶位</b>与<b>由桶位决定的那部分榜单</b>。
 */
class HeavyKeeperGoParityTest {

    // ==================================================================
    // 1. 列号 fixture（width=1000 depth=5，生产口径）
    // ==================================================================

    private static final Object[][] GO_HASH_WIDTH_1000_DEPTH_5 = {
            {"", new int[]{455, 244, 877, 666, 611, }},
            {"0", new int[]{869, 658, 447, 236, 25, }},
            {"123456789012345678901234567890", new int[]{836, 47, 258, 469, 992, }},
            {"a", new int[]{84, 295, 506, 717, 240, }},
            {"article-1", new int[]{405, 194, 983, 772, 561, }},
            {"article-2", new int[]{750, 961, 328, 539, 594, }},
            {"article-9", new int[]{645, 434, 223, 12, 801, }},
            {"article-A", new int[]{45, 834, 623, 412, 201, }},
            {"article-hot", new int[]{533, 322, 111, 900, 689, }},
            {"emoji😀", new int[]{984, 195, 406, 617, 828, }},
            {"hot-article", new int[]{369, 158, 947, 736, 213, }},
            {"key-with-dash_and_underscore", new int[]{783, 572, 205, 994, 939, }},
            {"noise-0", new int[]{772, 983, 194, 405, 928, }},
            {"noise-999", new int[]{795, 584, 217, 6, 639, }},
            {"zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz", new int[]{79, 868, 501, 290, 235, }},
            {"中文键", new int[]{798, 9, 376, 587, 642, }},
            {"文章-中文键", new int[]{776, 987, 198, 409, 620, }},
    };

    // ==================================================================
    // 2. 列号 fixture（width=7 depth=3，必冲突口径）
    // ==================================================================

    private static final Object[][] GO_HASH_WIDTH_7_DEPTH_3 = {
            {"", new int[]{3, 0, 2, }},
            {"0", new int[]{2, 6, 3, }},
            {"123456789012345678901234567890", new int[]{3, 6, 2, }},
            {"a", new int[]{6, 2, 5, }},
            {"article-1", new int[]{6, 3, 0, }},
            {"article-2", new int[]{4, 0, 5, }},
            {"article-9", new int[]{1, 5, 2, }},
            {"article-A", new int[]{6, 3, 0, }},
            {"article-hot", new int[]{0, 4, 1, }},
            {"emoji😀", new int[]{0, 3, 6, }},
            {"hot-article", new int[]{2, 6, 3, }},
            {"key-with-dash_and_underscore", new int[]{3, 0, 2, }},
            {"noise-0", new int[]{4, 0, 3, }},
            {"noise-999", new int[]{6, 3, 5, }},
            {"zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz", new int[]{4, 1, 3, }},
            {"中文键", new int[]{5, 1, 6, }},
            {"文章-中文键", new int[]{5, 1, 4, }},
    };

    // ==================================================================
    // 3. 列号 fixture（width=100000 depth=5）
    // ==================================================================

    private static final Object[][] GO_HASH_WIDTH_100000_DEPTH_5 = {
            {"", new int[]{67455, 39244, 23877, 95666, 54611, }},
            {"0", new int[]{63869, 35658, 7447, 79236, 51025, }},
            {"123456789012345678901234567890", new int[]{46836, 75047, 3258, 31469, 33992, }},
            {"a", new int[]{18084, 46295, 74506, 2717, 5240, }},
            {"article-1", new int[]{16405, 88194, 59983, 31772, 3561, }},
            {"article-2", new int[]{70750, 98961, 14328, 42539, 83594, }},
            {"article-9", new int[]{81645, 53434, 25223, 97012, 68801, }},
            {"article-A", new int[]{3045, 74834, 46623, 18412, 90201, }},
            {"article-hot", new int[]{58533, 30322, 2111, 73900, 45689, }},
            {"emoji😀", new int[]{81984, 10195, 38406, 66617, 94828, }},
            {"hot-article", new int[]{54369, 26158, 97947, 69736, 67213, }},
            {"key-with-dash_and_underscore", new int[]{30783, 2572, 87205, 58994, 17939, }},
            {"noise-0", new int[]{12772, 40983, 69194, 97405, 99928, }},
            {"noise-999", new int[]{6795, 78584, 63217, 35006, 19639, }},
            {"zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz", new int[]{62079, 33868, 18501, 90290, 49235, }},
            {"中文键", new int[]{62798, 91009, 6376, 34587, 75642, }},
            {"文章-中文键", new int[]{29776, 57987, 86198, 14409, 42620, }},
    };

    @Test
    @DisplayName("🔴 桶位列号逐条等于 Go 真源（width=1000 depth=5，生产口径）")
    void hashColumnsMatchGoAtProductionWidth() throws Exception {
        assertHashColumnsMatchGo(GO_HASH_WIDTH_1000_DEPTH_5, 1000, 5);
    }

    @Test
    @DisplayName("🔴 桶位列号逐条等于 Go 真源（width=7 depth=3，必冲突口径）")
    void hashColumnsMatchGoUnderHeavyCollision() throws Exception {
        assertHashColumnsMatchGo(GO_HASH_WIDTH_7_DEPTH_3, 7, 3);
    }

    @Test
    @DisplayName("🔴 桶位列号逐条等于 Go 真源（width=100000 depth=5）")
    void hashColumnsMatchGoAtWideWidth() throws Exception {
        assertHashColumnsMatchGo(GO_HASH_WIDTH_100000_DEPTH_5, 100000, 5);
    }

    @Test
    @DisplayName("🔴 重碰撞下的 Top-K 榜单（key 集合 + 分数）逐条等于 Go 真源")
    void topKMatchesGoUnderHeavyCollision() {
        // Go 出处：service/hot/heavykeeper/heavykeeper.go:77-115（Add）/ :118-130（TopK）
        // fixture 由 Go 真源在同一输入下算出（decay=0 ⇒ 冲突不抢占 ⇒ 结果确定）。
        HeavyKeeper hk = new HeavyKeeper(7, 3, 0.0, 100);
        for (int i = 0; i < 60; i++) {
            hk.add("article-" + i, 1);
        }

        assertThat(toCountMap(hk.topK())).isEqualTo(GO_TOPK_WIDTH_7);
    }

    @Test
    @DisplayName("🔴 生产口径变权 Top-K 榜单（key 集合 + 分数）逐条等于 Go 真源")
    void topKMatchesGoAtProductionWidthWithVaryingWeights() {
        HeavyKeeper hk = new HeavyKeeper(1000, 5, 0.0, 100);
        for (int i = 0; i < 400; i++) {
            hk.add("article-" + i, (i % 13) + 1);
        }

        assertThat(toCountMap(hk.topK())).isEqualTo(GO_TOPK_WIDTH_1000_VAR_WEIGHT);
    }

    // ==================================================================
    // fixture 与断言工具
    // ==================================================================

    private static final Map<String, Integer> GO_TOPK_WIDTH_7 = new LinkedHashMap<>();
    static {
        GO_TOPK_WIDTH_7.put("article-0", 1);
        GO_TOPK_WIDTH_7.put("article-1", 1);
        GO_TOPK_WIDTH_7.put("article-19", 1);
        GO_TOPK_WIDTH_7.put("article-2", 1);
        GO_TOPK_WIDTH_7.put("article-20", 1);
        GO_TOPK_WIDTH_7.put("article-3", 1);
        GO_TOPK_WIDTH_7.put("article-32", 1);
        GO_TOPK_WIDTH_7.put("article-4", 1);
        GO_TOPK_WIDTH_7.put("article-5", 1);
        GO_TOPK_WIDTH_7.put("article-6", 1);
        GO_TOPK_WIDTH_7.put("article-7", 1);
    }

    private static final Map<String, Integer> GO_TOPK_WIDTH_1000_VAR_WEIGHT = new LinkedHashMap<>();
    static {
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-10", 11);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-100", 10);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-101", 11);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-102", 12);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-103", 13);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-11", 12);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-113", 10);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-114", 11);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-115", 12);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-116", 13);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-12", 13);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-126", 10);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-127", 11);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-139", 10);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-140", 11);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-141", 12);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-142", 13);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-152", 10);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-153", 11);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-154", 12);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-155", 13);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-165", 10);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-166", 11);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-167", 12);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-168", 13);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-180", 12);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-181", 13);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-204", 10);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-205", 11);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-206", 12);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-207", 13);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-217", 10);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-218", 11);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-219", 12);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-22", 10);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-220", 13);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-23", 11);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-230", 10);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-231", 11);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-232", 12);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-233", 13);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-24", 12);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-243", 10);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-244", 11);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-245", 12);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-246", 13);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-25", 13);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-256", 10);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-257", 11);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-258", 12);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-259", 13);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-269", 10);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-270", 11);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-271", 12);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-272", 13);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-282", 10);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-283", 11);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-284", 12);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-285", 13);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-296", 11);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-297", 12);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-298", 13);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-308", 10);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-309", 11);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-310", 12);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-311", 13);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-321", 10);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-322", 11);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-323", 12);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-324", 13);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-334", 10);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-335", 11);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-35", 10);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-350", 13);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-36", 11);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-360", 10);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-361", 11);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-362", 12);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-363", 13);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-37", 12);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-373", 10);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-374", 11);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-375", 12);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-376", 13);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-38", 13);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-387", 11);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-48", 10);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-49", 11);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-50", 12);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-51", 13);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-61", 10);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-62", 11);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-63", 12);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-64", 13);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-74", 10);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-75", 11);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-76", 12);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-77", 13);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-9", 10);
        GO_TOPK_WIDTH_1000_VAR_WEIGHT.put("article-90", 13);
    }

    /** 把生产实现的 Top-K 结果归一成 {@code key → count} 的 Map 再比。 */
    private static Map<String, Integer> toCountMap(List<HeavyKeeper.Item> items) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (HeavyKeeper.Item it : items) {
            m.put(it.key(), it.count());
        }
        return m;
    }

    /**
     * 断言生产实现 {@code hash(key, seed)} 逐条等于 Go fixture。
     *
     * <p>用反射取私有 {@code hash}：本测试要钉的正是<b>私有桶位计算</b>，
     * 而那个语义位在公开 API 上<b>不可见</b>（只有 Top-K 结果间接体现）。
     */
    private static void assertHashColumnsMatchGo(Object[][] fixture, int width, int depth)
            throws Exception {
        HeavyKeeper hk = new HeavyKeeper(width, depth, 0.9, 100);
        Method hash = HeavyKeeper.class.getDeclaredMethod("hash", String.class, int.class);
        hash.setAccessible(true);

        List<String> mismatches = new ArrayList<>();
        for (Object[] row : fixture) {
            String key = (String) row[0];
            int[] expected = (int[]) row[1];
            for (int seed = 0; seed < depth; seed++) {
                int actual = (Integer) hash.invoke(hk, key, seed);
                if (actual != expected[seed]) {
                    mismatches.add("key=%s seed=%d 期望 %d 实际 %d".formatted(key, seed, expected[seed], actual));
                }
            }
        }

        assertThat(mismatches)
                .as("桶位列号与 Go 真源不一致（%d 处）⇒ 桶位错 ⇒ 冲突分布错 ⇒ 热榜内容错", mismatches.size())
                .isEmpty();
    }
}
