package com.rag.common.protection;

import com.rag.common.protection.GoStyleProtectionProperties.CpuSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link GoStyleAdaptiveShedder} 对 Go {@code core/load/adaptiveshedder.go} 的行为锁定。
 *
 * <p><b>本测试类的存在理由</b>：降载器的三个判据全是<b>纯函数</b>
 * （{@code overloadFactor} / {@code maxPass} / {@code minRt} / {@code maxFlight}），
 * 只要注入确定性的 CPU 读数就能<b>精确断言</b>，不必真去压满 CPU。
 * 这是「框架机制必须验证实际生效」（AGENTS.md §3.3）在本层的落地方式：
 * 真去烧 CPU 既慢又不确定，而判据本身是可以被完整验证的。
 */
class GoStyleAdaptiveShedderTest {

    private static GoStyleAdaptiveShedder shedder(long thresholdPermille) {
        return new GoStyleAdaptiveShedder(thresholdPermille, CpuSource.SYSTEM);
    }

    @Test
    @DisplayName("overloadFactor：CPU 恰在阈值 ⇒ 1.0（不削减上限）")
    void factorIsOneAtThreshold() {
        GoStyleAdaptiveShedder s = shedder(900);
        s.setCpuPermilleReader(() -> 900);
        assertEquals(1.0D, s.overloadFactor(), 1e-9);
    }

    @Test
    @DisplayName("overloadFactor：CPU 打满 1000 ⇒ 降到下界 0.1（永不 0）")
    void factorNeverDropsBelowPointOne() {
        GoStyleAdaptiveShedder s = shedder(900);
        s.setCpuPermilleReader(() -> 1000);
        // Go overloadFactorLowerBound=0.1，注释：at least accept 10% even if cpu is highly overloaded
        assertEquals(0.1D, s.overloadFactor(), 1e-9);
    }

    @Test
    @DisplayName("overloadFactor：中间值线性插值（900→1000 区间）")
    void factorInterpolatesLinearly() {
        GoStyleAdaptiveShedder s = shedder(900);
        s.setCpuPermilleReader(() -> 950);
        // (1000-950)/(1000-900) = 0.5
        assertEquals(0.5D, s.overloadFactor(), 1e-9);
    }

    @Test
    @DisplayName("CPU 读不到（-1）⇒ fail-open：不降载、factor=1")
    void unreadableCpuFailsOpen() {
        GoStyleAdaptiveShedder s = shedder(900);
        s.setCpuPermilleReader(() -> -1);
        assertFalse(s.systemOverloaded(),
                "读不到 CPU 必须视作未过载 —— 若当成过载会把全部流量杀光");
        assertEquals(1.0D, s.overloadFactor(), 1e-9);
    }

    @Test
    @DisplayName("冷启动：minRt 取默认 1000ms ⇒ maxFlight 很大，不会误丢")
    void coldStartDoesNotOverDrop() {
        GoStyleAdaptiveShedder s = shedder(900);
        s.setCpuPermilleReader(() -> 0);
        assertEquals(1000.0D, s.minRt(), 1e-9, "无数据时用 defaultMinRt=1000ms");
        // maxPass 初值 1 ⇒ maxFlight = 1 * 1000 * 0.01 = 10
        assertEquals(10.0D, s.maxFlight(), 1e-9);
        assertEquals(1L, s.maxPass(), "无数据时 maxPass 初值为 1（Go: result := int64(1)）");
    }

    @Test
    @DisplayName("maxFlight 有下界 1（Go: mathx.AtLeast(maxFlight, 1)）")
    void maxFlightHasLowerBoundOfOne() {
        GoStyleAdaptiveShedder s = shedder(900);
        s.setCpuPermilleReader(() -> 0);
        assertTrue(s.maxFlight() >= 1.0D, "maxFlight 不得小于 1");
    }

    @Test
    @DisplayName("CPU 未过阈值时，即使在途很高也不丢请求（shouldDrop 需两条件同时成立）")
    void lowCpuNeverShedsEvenWithHighInFlight() {
        GoStyleAdaptiveShedder s = shedder(900);
        s.setCpuPermilleReader(() -> 100);          // 远低于阈值

        // 先造出高 avgFlying：一批 allow 后集中 pass
        for (int i = 0; i < 50; i++) {
            assertTrue(s.allow(), "CPU 未过载时 allow 必须恒成功");
        }
        for (int i = 0; i < 50; i++) {
            s.pass(5);
        }

        boolean anyDrop = false;
        for (int i = 0; i < 20; i++) {
            if (!s.allow()) {
                anyDrop = true;
                break;
            }
        }
        assertFalse(anyDrop,
                "CPU=100‰ 远低于阈值 900‰ 时，Go 的 systemOverloaded() 为 false ⇒ 一律放行");
    }

    @Test
    @DisplayName("CPU 打满且在途超上限 ⇒ 丢请求（返回 false）")
    void overloadedAndHighInFlightSheds() {
        GoStyleAdaptiveShedder s = shedder(900);
        s.setCpuPermilleReader(() -> 1000);         // 恒过载

        // 先用一轮 allow+pass 抬高 avgFlying（avgFlying 只在 delta<0 时更新）
        for (int i = 0; i < 50; i++) {
            s.allow();
        }
        for (int i = 0; i < 50; i++) {
            s.pass(5);
        }

        // 随后连续 allow：在途会越过 maxFlight*overloadFactor ⇒ 必须出现丢弃
        boolean anyDrop = false;
        for (int i = 0; i < 20; i++) {
            if (!s.allow()) {
                anyDrop = true;
                break;
            }
        }
        assertTrue(anyDrop,
                "CPU 打满 + avgFlying 与 flying 双双超过上限时，Go 的 shouldDrop() 必须为真");
    }

    @Test
    @DisplayName("stillHot：刚丢弃过则冷却期内仍算过载（Go coolOffDuration=1s）")
    void stillHotWithinCoolOffWindow() {
        GoStyleAdaptiveShedder s = shedder(900);
        s.setCpuPermilleReader(() -> 1000);

        for (int i = 0; i < 50; i++) {
            s.allow();
        }
        for (int i = 0; i < 50; i++) {
            s.pass(5);
        }
        boolean dropped = false;
        for (int i = 0; i < 20; i++) {
            if (!s.allow()) {
                dropped = true;
                break;
            }
        }
        assertTrue(dropped, "前置条件：先要丢过一次，droppedRecently 才会被置位");

        // 把 CPU 读数降到阈值以下 —— 但仍可能因 stillHot 而判定过载
        s.setCpuPermilleReader(() -> 0);
        assertFalse(s.systemOverloaded(), "CPU 已回落，systemOverloaded 应为 false");
        assertTrue(s.stillHot(), "冷却期内 stillHot() 必须为 true —— 这是 Go 的防抖设计");
    }

    @Test
    @DisplayName("fail() 只减在途，不记 RT/QPS（Go promise.Fail 只调 addFlying(-1)）")
    void failDoesNotRecordRtOrQps() {
        GoStyleAdaptiveShedder s = shedder(900);
        s.setCpuPermilleReader(() -> 0);

        for (int i = 0; i < 10; i++) {
            s.allow();
        }
        for (int i = 0; i < 10; i++) {
            s.fail();
        }
        assertEquals(0L, s.flying(), "fail 后在途归零");
        assertEquals(1000.0D, s.minRt(), 1e-9,
                "Fail 不得写入 RT 窗口 —— 否则降载期的 QPS 会被被丢的请求抬高，越降越不降");
    }

    // ==================== RT 统计口径（Go adaptiveshedder.go:284-289）====================

    @Test
    @DisplayName("RT 必须是「浮点毫秒 + 向上取整」：1.2ms 记 2，不是整型截断的 1")
    void rtIsRecordedWithCeilOfFloatMilliseconds() {
        GoStyleAdaptiveShedder s = shedder(900);
        s.setCpuPermilleReader(() -> 0);

        s.pass(1.2D);

        assertEquals(2L, s.recordedRtSum(),
                "Go: rtCounter.Add(int64(math.Ceil(rt)))，rt 是 float64 毫秒 ⇒ ceil(1.2)=2。"
                        + "若记成 1，说明这里用了整型毫秒（截断）⇒ 系统性少记约 1ms "
                        + "⇒ minRt 偏小 ⇒ 在途上限偏低 ⇒ 比 Go 更早降载。");
        assertEquals(1L, s.recordedRtCount(), "Go 的 Bucket.Add 无论 v 多大都 Count++");
    }

    @Test
    @DisplayName("RT=0 时记 0（Go 的 Add(0)），不是「至少记 1」")
    void zeroRtIsRecordedAsZeroNotOne() {
        GoStyleAdaptiveShedder s = shedder(900);
        s.setCpuPermilleReader(() -> 0);

        s.pass(0.0D);

        assertEquals(0L, s.recordedRtSum(),
                "Go: math.Ceil(0.0)=0 ⇒ rtCounter 的 Sum 不加（Count 仍 +1）。"
                        + "若记成 1，说明加了「至少记 1」的下限 ⇒ minRt 被抬高 ⇒ "
                        + "在途上限被抬高 ⇒ 比 Go 更晚降载。");
        assertEquals(1L, s.recordedRtCount(), "Count 照样 +1（Go: Bucket.Add 先 Sum+=v 再 Count++）");
    }

    @Test
    @DisplayName("RT 恰为整毫秒时记原值（Go: ceil(2.0)=2）")
    void exactIntegerRtIsRecordedVerbatim() {
        GoStyleAdaptiveShedder s = shedder(900);
        s.setCpuPermilleReader(() -> 0);

        s.pass(2.0D);

        assertEquals(2L, s.recordedRtSum(), "整毫秒不得被偏移（Go: ceil(2.0)=2）");
        assertEquals(1L, s.recordedRtCount());
    }
}