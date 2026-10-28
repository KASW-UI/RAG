package com.rag.common.snowflake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 🔴 <b>DRIFT-SF-001 守门：Snowflake 的时钟源必须是「锚点 + 单调流逝」，不是墙钟。</b>
 *
 * <p><b>Go 出处（逐条可核）</b>：
 * <ul>
 *   <li>{@code bwmarrin/snowflake@v0.3.0/snowflake.go:124} {@code var curTime = time.Now()}
 *       —— 带单调读数的当前时刻；</li>
 *   <li>{@code :126} {@code n.epoch = curTime.Add(time.Unix(Epoch/1000, (Epoch%1000)*1000000).Sub(curTime))}
 *       —— 库注释原文：<i>"to make sure we use the monotonic clock if available"</i>；</li>
 *   <li>{@code :139} {@code now := time.Since(n.epoch).Nanoseconds() / 1000000}
 *       —— 两个时间点都带单调读数 ⇒ {@code Time.Sub}（{@code $GOROOT/src/time/time.go:1194}）
 *       走 {@code subMono}（{@code :1210}）⇒ <b>纯单调</b>。</li>
 * </ul>
 *
 * <p><b>为什么需要守门</b>（AGENTS.md §3.3.3）：旧实现用 {@code System.currentTimeMillis() - EPOCH}
 * （纯墙钟）⇒ 墙钟倒退 ⇒ {@code now < lastTimestamp} ⇒ {@code sequence} 归零
 * ⇒ <b>同一毫秒产出完全相同的 ID</b> ⇒ 命中 {@code docs/PhasedGoal.md} P0 #3「重复处理」。
 * 而墙钟<b>改不了</b>（改系统时间要 root 且污染整台机器）⇒ ⇒ 只能<b>注入</b>墙钟来复现。
 *
 * <p><b>断言设计（避免假绿）</b>：本守门用<b>注入的墙钟 + 注入的单调钟</b>分别驱动，
 * ⇒ 「墙钟倒退」与「单调钟前进」<b>可以同时发生</b> ⇒ 只有真正读单调钟的实现才能通过。
 * 变异验证（把 {@code nowMillisSinceEpoch()} 改回墙钟）⇒ 本守门<b>必须变红</b>。
 */
class SnowflakeMonotonicClockGateTest {

    /** 注入的墙钟（epoch 毫秒）。 */
    private final AtomicLong fakeWallClock = new AtomicLong();

    /** 注入的单调钟（纳秒）。 */
    private final AtomicLong fakeMonotonic = new AtomicLong();

    @AfterEach
    void restoreRealClocks() {
        Snowflake.wallClockMillis = System::currentTimeMillis;
        Snowflake.monotonicNanos = System::nanoTime;
    }

    /**
     * 装上假时钟并返回一个新的 {@link Snowflake.State}（⇒ 锚点在此刻取定）。
     */
    private Snowflake.State newStateAt(long wallMillis, long monotonicNanos) {
        fakeWallClock.set(wallMillis);
        fakeMonotonic.set(monotonicNanos);
        Snowflake.wallClockMillis = fakeWallClock::get;
        Snowflake.monotonicNanos = fakeMonotonic::get;
        return new Snowflake.State();
    }

    @Test
    @DisplayName("① 时钟源 = 单调钟：墙钟倒退 1 小时，产出的 ID 仍严格递增（不倒退）")
    void wallClockRollbackDoesNotMakeIdGoBackwards() {
        long t0 = 1_700_000_000_000L; // 墙钟起点
        Snowflake.State state = newStateAt(t0, 1_000_000_000L);

        long first = Snowflake.nextIdInternal(7L, state);

        // 🔴 墙钟倒退 1 小时（= NTP 后跳 / setSystemTime / 快照恢复）
        fakeWallClock.set(t0 - 3_600_000L);
        // 单调钟继续正常前进（真实世界里的单调钟不受墙钟影响）
        fakeMonotonic.addAndGet(1_000_000L); // +1ms

        long second = Snowflake.nextIdInternal(7L, state);

        assertTrue(second > first,
                "墙钟倒退后 ID 必须仍然递增（旧实现会倒退 ⇒ 同一毫秒可能产出重复 ID）"
                        + "；first=" + first + " second=" + second);
    }

    @Test
    @DisplayName("② 墙钟倒退期间，连续产出的 ID 全部唯一（无重复 ID）")
    void noDuplicateIdAcrossWallClockRollback() {
        long t0 = 1_700_000_000_000L;
        Snowflake.State state = newStateAt(t0, 1_000_000_000L);

        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            // 每 10 次就把墙钟往回拨 1 小时（模拟反复 NTP 校正）
            if (i % 10 == 5) {
                fakeWallClock.addAndGet(-3_600_000L);
            }
            // 单调钟每次 +1ms
            fakeMonotonic.addAndGet(1_000_000L);
            long id = Snowflake.nextIdInternal(7L, state);
            assertTrue(seen.add(id), "产出重复 ID：" + id + "（第 " + i + " 次）");
        }
    }

    @Test
    @DisplayName("②' 墙钟「回拨 → 再追回同一毫秒」不产出重复 ID（🔴 直接钉 P0 #3 的判别力用例）")
    void noDuplicateIdWhenWallClockReturnsToSameMillisecond() {
        long t0 = 1_700_000_000_000L;
        Snowflake.State state = newStateAt(t0, 1_000_000_000L);

        Set<Long> seen = new HashSet<>();

        // 第 1 次：墙钟停在 W0，单调钟 +1ms
        fakeMonotonic.addAndGet(1_000_000L);
        long id1 = Snowflake.nextIdInternal(7L, state);
        assertTrue(seen.add(id1), "首个 ID 重复");

        // 第 2 次：墙钟回拨 1 小时（= NTP 后跳），单调钟继续 +1ms
        fakeWallClock.set(t0 - 3_600_000L);
        fakeMonotonic.addAndGet(1_000_000L);
        long id2 = Snowflake.nextIdInternal(7L, state);
        assertTrue(seen.add(id2), "第二个 ID 重复：" + id2);

        // 第 3 次：墙钟【追回】到 W0（= NTP 前跳把它纠正回来），单调钟继续 +1ms
        //   ⇒ 🔴 墙钟实现此时读到的毫秒数与第 1 次完全相同 ⇒ sequence 被归零
        //      ⇒ 产出与 id1 **逐位相同**的 ID ⇒ 重复
        fakeWallClock.set(t0);
        fakeMonotonic.addAndGet(1_000_000L);
        long id3 = Snowflake.nextIdInternal(7L, state);

        assertTrue(seen.add(id3),
                "🔴 产出重复 ID：" + id3 + "（与 id1=" + id1 + " 相同）"
                        + " ⇒ 墙钟回拨后追回同一毫秒 ⇒ 同一毫秒产出两个相同 ID"
                        + " ⇒ 命中 docs/PhasedGoal.md P0 #3「重复处理」");
        assertTrue(id3 > id2, "ID 必须仍然递增（时间戳分量不得因墙钟追回而倒退）");
    }

    @Test
    @DisplayName("③ 墙钟前跳 1 小时，ID 仍递增（Go 侧同样不因前跳而跳号）")
    void wallClockJumpForwardDoesNotBreakMonotonicity() {
        long t0 = 1_700_000_000_000L;
        Snowflake.State state = newStateAt(t0, 1_000_000_000L);

        long first = Snowflake.nextIdInternal(7L, state);

        fakeWallClock.set(t0 + 3_600_000L); // 墙钟前跳
        fakeMonotonic.addAndGet(1_000_000L);

        long second = Snowflake.nextIdInternal(7L, state);
        assertTrue(second > first, "墙钟前跳后 ID 必须仍然递增");
    }

    @Test
    @DisplayName("④ 时间戳分量落在 epoch 之后（锚点含 epoch 偏移，不是「相对进程启动」）")
    void timestampComponentIsAfterEpoch() {
        // 2023-11-14 前后 ⇒ 距 twitter epoch（2010-11-04）约 4.8e10 ms
        long t0 = 1_700_000_000_000L;
        Snowflake.State state = newStateAt(t0, 1_000_000_000L);

        long id = Snowflake.nextIdInternal(7L, state);
        long timestampPart = id >>> 22; // 41 位时间戳分量

        long expected = t0 - Snowflake.EPOCH;
        assertTrue(timestampPart > 0,
                "时间戳分量必须落在 epoch 之后（> 0）；实际=" + timestampPart
                        + " ⇒ 若为 0 或负数，说明锚点做成了「相对进程启动」⇒ 与 Go 的位布局语义不符");
        assertTrue(Math.abs(timestampPart - expected) <= 2L,
                "时间戳分量必须≈(当前墙钟 - EPOCH)；期望≈" + expected + " 实际=" + timestampPart);
    }

    @Test
    @DisplayName("⑤ 位布局不变：41 位时间戳 + 10 位 workerId + 12 位 sequence")
    void bitLayoutUnchanged() {
        long t0 = 1_700_000_000_000L;
        Snowflake.State state = newStateAt(t0, 1_000_000_000L);

        long workerId = 513L; // 0..1023
        long id = Snowflake.nextIdInternal(workerId, state);

        long workerPart = (id >>> 12) & 0x3FFL;
        long sequencePart = id & 0xFFFL;

        assertEquals(workerId, workerPart, "workerId 位段必须原样落在 10 位上");
        assertTrue(sequencePart >= 0 && sequencePart <= 4095, "sequence 位段必须落在 12 位上");
        assertTrue((id >>> 22) > 0, "时间戳位段必须是 41 位的正数");
    }

    @Test
    @DisplayName("⑥ 同一毫秒内 sequence 递增；跨毫秒归零（对齐 v0.3.0 的分支语义）")
    void sequenceIncrementsWithinSameMillisecond() {
        long t0 = 1_700_000_000_000L;
        Snowflake.State state = newStateAt(t0, 1_000_000_000L);

        long id1 = Snowflake.nextIdInternal(7L, state);
        long id2 = Snowflake.nextIdInternal(7L, state);
        assertEquals(id1 + 1, id2, "同毫秒内 sequence 必须 +1（ID 逐个递增）");

        // 推进 1ms ⇒ 新毫秒 ⇒ sequence 归零
        fakeMonotonic.addAndGet(1_000_000L);
        long id3 = Snowflake.nextIdInternal(7L, state);
        assertEquals(0L, id3 & 0xFFFL, "新毫秒的 sequence 必须归零");
        assertNotEquals(id2, id3);
    }

    @Test
    @DisplayName("⑦ 时钟源确实是单调钟：把实现改回墙钟 ⇒ 本守门必红（变异判别力）")
    void mutationGuardIsDiscriminating() {
        // 本用例本身不改实现；它断言「注入的墙钟不影响 ID 的时间戳分量」——
        // 而「时间戳分量来自墙钟」的实现会让时间戳跟着假墙钟走 ⇒ 变红。
        long t0 = 1_700_000_000_000L;
        Snowflake.State state = newStateAt(t0, 1_000_000_000L);

        long before = Snowflake.nextIdInternal(7L, state) >>> 22;

        // 墙钟跳 10 年，但单调钟只走 1ms
        fakeWallClock.set(t0 + 365L * 24 * 3600 * 1000L);
        fakeMonotonic.addAndGet(1_000_000L);

        long after = Snowflake.nextIdInternal(7L, state) >>> 22;

        assertTrue(Math.abs(after - before) <= 2L,
                "时间戳分量只应随【单调钟】前进 ±1ms；实际从 " + before + " 跳到 " + after
                        + " ⇒ 说明实现读了墙钟 ⇒ 变异会被抓住");
    }
}
