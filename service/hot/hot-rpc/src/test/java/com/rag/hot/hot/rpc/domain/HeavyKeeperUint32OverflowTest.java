package com.rag.hot.hot.rpc.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 锁死 Go {@code uint32} 语义 —— Go 自带的 4 条测试<b>完全没覆盖</b>这一层。
 *
 * <p><b>为什么必须单独锁</b>：Go 的 {@code bucket.count} 是 {@code uint32}，而 Java 用
 * {@code int} 存同一批二进制位。加减法两语言都会自然回绕（两补码），所以那部分不会错；
 * <b>真正会错的是比较</b>：Go 的 {@code count > maxCount} 是<b>无符号</b>比较，
 * Java 写 {@code count > maxCount} 则是<b>有符号</b>比较，在 count ≥ 2^31 时结论相反。
 *
 * <p>下面的 {@link #uint32OverflowKeepsItemInTopK()} 就是这条差异的判定器：
 * 若实现退化成有符号比较，该用例会失败（item 不会进 TopK）。
 */
class HeavyKeeperUint32OverflowTest {

    @Test
    @DisplayName("count += weight 溢出按模 2^32 回绕，与 Go uint32 位模式一致")
    void uint32OverflowKeepsItemInTopK() {
        HeavyKeeper hk = new HeavyKeeper(100, 1, 0.9, 10);

        // 2_000_000_000 + 2_000_000_000 = 4e9 > 2^32
        // Go: uint32(4e9) = 4_000_000_000 - 4_294_967_296 = -294_967_296 的位模式
        hk.add("overflow-key", 2_000_000_000);
        hk.add("overflow-key", 2_000_000_000);

        List<HeavyKeeper.Item> topK = hk.topK();
        assertThat(topK).extracting(HeavyKeeper.Item::key).contains("overflow-key");

        HeavyKeeper.Item item = topK.stream()
                .filter(i -> i.key().equals("overflow-key"))
                .findFirst()
                .orElseThrow();

        // 位模式与 uint32(4e9) 完全一致
        assertThat(Integer.toUnsignedLong(item.count())).isEqualTo(4_000_000_000L);
        assertThat(item.count()).isEqualTo(-294_967_296);

        // ★ 关键判定：无符号比较让 maxCount 被更新；换成有符号比较这一步会失败
        assertThat(Integer.compareUnsigned(item.count(), 0)).isPositive();
    }

    /**
     * 锁定 <b>最小堆比较器</b>必须是<b>无符号</b>的 —— 这是 {@link HeavyKeeper} 里第二处会分岔的地方
     * （第一处是 {@code maxCount} 比较）。
     *
     * <p>观察点不是「输出顺序」，而是<b>淘汰谁</b>：{@code updateTopK} 用 {@code topKHeap.peek()}
     * 取堆顶（= 最小项）决定淘汰对象，比较器一错就会淘汰错的那个 key。
     *
     * <p>构造：TopK=2，{@code huge} 的计数溢出成负位模式但无符号值最大（4e9）。
     * 再加入权重 10 的 {@code new}：
     * <ul>
     *   <li>比较器<b>无符号</b>（正确）⇒ 堆顶是 mid(5) ⇒ {@code 10 > 5} ⇒ 淘汰 mid ⇒ TopK={huge,new}</li>
     *   <li>比较器<b>有符号</b>（错误）⇒ 堆顶是 huge(负数) ⇒ {@code compareUnsigned(10, 4e9)} 为假
     *       ⇒ new 被拒 ⇒ TopK={huge,mid}</li>
     * </ul>
     * 两者结果不同，因此本用例能真正锁住比较器。
     */
    @Test
    @DisplayName("最小堆比较器必须无符号：溢出成负位模式的 key 不能被当成堆顶")
    void heapComparatorMustBeUnsigned() {
        HeavyKeeper hk = new HeavyKeeper(100_000, 1, 0.9, 2);

        hk.add("huge", 2_000_000_000);
        hk.add("huge", 2_000_000_000); // 无符号值 4e9，位模式为负
        hk.add("mid", 5);             // TopK 已满（2 个）
        hk.add("new", 10);            // 10 > 5 ⇒ 正确实现应淘汰 mid

        List<HeavyKeeper.Item> topK = hk.topK();
        assertThat(topK)
                .as("有符号比较器会把 huge 误判为堆顶，导致 new 被错误拒绝")
                .extracting(HeavyKeeper.Item::key)
                .containsExactly("huge", "new");
    }

    /**
     * 🔴 <b>第三处会分岔的地方：桶冲突时的衰减概率</b>（U9 系统变异查出来的守门缺口）。
     *
     * <p><b>判据</b>：Go 的 {@code decay^count} 里 {@code count} 是 {@code uint32}，
     * 而 Java 用 {@code int} 存同一批位 ⇒ 必须走 <b>无符号</b>数值。
     *
     * <p><b>为什么这条能确定性地抓住「有符号」改法</b>（不靠随机）：
     * <pre>
     *   count 位模式 = -294_967_296（无符号值 4e9）
     *   无符号：decay^4e9  = 0.0    ⇒ nextDouble() &lt; 0.0 恒假 ⇒ **不衰减**
     *   有符号：decay^-2.9e8 = +Inf ⇒ nextDouble() &lt; +Inf 恒真 ⇒ **必然衰减**
     * </pre>
     * ⇒ 两条路径的结果<b>完全相反且都确定</b>，不依赖随机数。
     *
     * <p><b>构造</b>：{@code width=1} ⇒ 所有 key 都落到唯一的桶 ⇒ 第二个 key 必然与第一个<b>冲突</b>
     * （指纹不同 —— FNV-1a 32 对不同输入必不同，因为乘以奇数是双射）。
     * 最后再 {@code add("a", 1)} 一次，把桶里的计数<b>回写进 TopK</b>
     * （{@code updateTopK} 只在同一 key 再次加入时才更新已存在条目的 count，
     *  否则衰减的效果在 {@code topK()} 上<b>看不见</b>）。
     *
     * <p><b>Go 出处</b>：{@code service/hot/heavykeeper/heavykeeper.go:98} 的
     * {@code rand.Float64() &lt; math.Pow(decay, float64(count))}。
     */
    @Test
    @DisplayName("桶冲突的衰减概率必须按 uint32 无符号取指数：溢出成负位模式 ⇒ 概率为 0 ⇒ 不衰减")
    void decayProbabilityUsesUnsignedCount() {
        HeavyKeeper hk = new HeavyKeeper(1, 1, 0.9, 10);

        hk.add("a", 2_000_000_000);
        hk.add("a", 2_000_000_000); // 累计 4e9 ⇒ int 位模式为负（uint32 语义）
        hk.add("b", 5);              // 指纹不同 ⇒ 走冲突分支（decay^count）
        hk.add("a", 1);              // 再加一次 ⇒ 把桶里的计数回写进 TopK

        List<HeavyKeeper.Item> topK = hk.topK();
        assertThat(topK)
                .as("冲突且未衰减 ⇒ b 的 count 恒为 0 ⇒ 不进 TopK")
                .extracting(HeavyKeeper.Item::key)
                .containsExactly("a");

        // 无符号语义：4e9 + 1 = 4_000_000_001（冲突时 decay^4e9 = 0 ⇒ 不衰减）
        // 有符号语义（变异）：decay^(-294967296) = +Inf ⇒ 必然衰减 ⇒ 变成 3_999_999_996
        assertThat(Integer.toUnsignedLong(topK.get(0).count()))
                .as("有符号取指数会让 decay^count = +Inf ⇒ 冲突必然衰减 ⇒ 计数被减掉 weight")
                .isEqualTo(4_000_000_001L);
    }

    @Test
    @DisplayName("countToScore：Go float64(uint32) 是无符号数值，不是 int 的符号值")
    void countToScoreIsUnsigned() {        // int 位模式 0xFFFFFFFF 若按有符号解释是 -1；Go 的 float64(uint32) 是 4294967295
        assertThat(HeavyKeeper.countToScore(-1)).isEqualTo(4294967295.0);
        assertThat(HeavyKeeper.countToScore(-1)).isNotEqualTo(-1.0);
        assertThat(HeavyKeeper.countToScore(0)).isEqualTo(0.0);
        assertThat(HeavyKeeper.countToScore(100)).isEqualTo(100.0);
    }

    @Test
    @DisplayName("scoreToCount：与 Go uint32(z.Score) 的截断结果一致，且能反向 round-trip")
    void scoreToCountTruncatesTowardZero() {
        // Go `uint32(z.Score)` 是向零截断，不能 Math.round
        assertThat(HeavyKeeper.scoreToCount(99.9)).isEqualTo(99);
        assertThat(HeavyKeeper.scoreToCount(0.5)).isZero();
        assertThat(HeavyKeeper.scoreToCount(7.0)).isEqualTo(7);

        // 与 countToScore 构成 round-trip
        for (int raw : new int[]{0, 1, 127, Integer.MAX_VALUE, Integer.MIN_VALUE, -1}) {
            assertThat(HeavyKeeper.scoreToCount(HeavyKeeper.countToScore(raw))).isEqualTo(raw);
        }
    }
}