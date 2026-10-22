package com.rag.hot.hot.rpc.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 对应 Go {@code service/hot/heavykeeper/heavykeeper_test.go} 的 4 条测试。
 *
 * <p>⚠️ <b>断言强度刻意与 Go 保持一致</b>：概率算法只能断言<b>相对顺序</b>与<b>容量上限</b>，
 * 不能断言精确计数（冲突分支含 {@code rand}）。Go 侧 4 条测试同样如此。
 */
class HeavyKeeperTest {

    @Test
    @DisplayName("BasicAdd：加得最多的 key 排 Top1（对齐 Go TestHeavyKeeper_BasicAdd）")
    void basicAdd() {
        HeavyKeeper hk = new HeavyKeeper(100, 3, 0.9, 10);
        for (int i = 0; i < 10; i++) {
            hk.add("hot", 1);
        }
        for (int i = 0; i < 5; i++) {
            hk.add("cold", 1);
        }

        List<HeavyKeeper.Item> topK = hk.topK();
        assertThat(topK).isNotEmpty();
        assertThat(topK.get(0).key()).isEqualTo("hot");
        assertThat(topK.get(1).key()).isEqualTo("cold");
    }

    @Test
    @DisplayName("WeightedAdd：加权后重的 key 排前（对齐 Go TestHeavyKeeper_WeightedAdd）")
    void weightedAdd() {
        HeavyKeeper hk = new HeavyKeeper(100, 3, 0.9, 10);
        for (int i = 0; i < 10; i++) {
            hk.add("light", 1);
            hk.add("heavy", 10);
        }

        List<HeavyKeeper.Item> topK = hk.topK();
        assertThat(topK.get(0).key()).isEqualTo("heavy");
    }

    @Test
    @DisplayName("TopKCapacity：TopK=10 时长度不超过 10（对齐 Go TestHeavyKeeper_TopKCapacity）")
    void topKCapacity() {
        HeavyKeeper hk = new HeavyKeeper(100, 3, 0.9, 10);
        for (int i = 0; i < 100; i++) {
            hk.add("article-" + i, i + 1);
        }

        assertThat(hk.topK()).hasSizeLessThanOrEqualTo(10);
        assertThat(hk.topKSize()).isLessThanOrEqualTo(10);
    }

    @Test
    @DisplayName("ConflictDecay：高频 key 能扛住大量低频噪声（对齐 Go TestHeavyKeeper_ConflictDecay）")
    void conflictDecay() {
        HeavyKeeper hk = new HeavyKeeper(100, 3, 0.9, 5);
        for (int i = 0; i < 500; i++) {
            hk.add("hot-article", 10);
        }
        for (int i = 0; i < 1000; i++) {
            hk.add("noise-" + i, 1);
        }

        assertThat(hk.topK()).extracting(HeavyKeeper.Item::key).contains("hot-article");
    }

    @Test
    @DisplayName("topK 是降序：相邻条目 count 无符号比较为非递增")
    void topKIsDescending() {
        HeavyKeeper hk = new HeavyKeeper(200, 4, 0.9, 50);
        for (int i = 1; i <= 30; i++) {
            for (int n = 0; n < i; n++) {
                hk.add("a" + i, 1);
            }
        }

        List<HeavyKeeper.Item> topK = hk.topK();
        for (int i = 1; i < topK.size(); i++) {
            assertThat(Integer.compareUnsigned(topK.get(i - 1).count(), topK.get(i).count()))
                    .as("item[%d]=%s 必须 ≥ item[%d]=%s（无符号比较）",
                            i - 1, topK.get(i - 1), i, topK.get(i))
                    .isGreaterThanOrEqualTo(0);
        }
    }
}