package com.rag.common.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 {@link RpcInterceptor.DeadlineState} 的**时基 + 精度**守门（DRIFT-COMMON-005 修复的回归网）。
 *
 * <h2>🔴 为什么这条必须有（AGENTS.md §3.3.3：「断言『Bean 存在』≠ 断言机制生效」）</h2>
 * <p>本条修的是<b>时基</b>（墙钟 → 单调钟）与<b>精度</b>（毫秒 → 纳秒）⇒ ⇒ 这两件事
 * <b>都不产生异常、不产生日志</b> ⇒ ⇒ 只有<b>专门构造的断言</b>才能把它们钉住。
 *
 * <h2>Go 出处（逐条可核 · 本仓 pin 住的 Go 1.25.11 标准库）</h2>
 * <ul>
 *   <li>{@code $GOROOT/src/context/context.go:625} —— {@code func WithDeadline(parent Context, d time.Time)}
 *       · {@code :632} —— {@code WithDeadlineCause}；deadline 存成 {@code time.Time}
 *       （字段 {@code context.go:666}）⇒ <b>纳秒精度</b>；</li>
 *   <li>{@code $GOROOT/src/time/time.go:152} —— {@code time.Time} 的 {@code ext int64}（单调读数）
 *       · {@code :164} —— {@code hasMonotonic} 标志位；</li>
 *   <li>{@code $GOROOT/src/time/time.go:1233} —— {@code func Until(t Time) Duration}：
 *       有单调读数时走 {@code subMono(t.ext, runtimeNano()-startNano)}
 *       ⇒ <b>纯单调</b> ⇒ NTP 校正 / {@code setSystemTime} / 虚拟机快照恢复<b>都影响不到它</b>。</li>
 * </ul>
 *
 * <h2>🔴 本守门的判别力是怎么来的（**旧实现会红**）</h2>
 * <p>旧实现把 deadline 存成<b>墙钟 epoch 毫秒</b>，每次读都用 {@code System.currentTimeMillis()} 再比一次
 * ⇒ ⇒ <b>墙钟跳变会直接改变判定</b>。
 * <p>⇒ 本类用<b>两个包级私有注入点</b>（{@code wallClockMillis} / {@code monotonicNanos}）模拟时钟跳变：
 * <ul>
 *   <li><b>墙钟前跳 1 小时</b> ⇒ 旧实现：{@code remainingMillis()} 变成 0 且 {@code isExpired()} 变 true
 *       ⇒ <b>红</b>；新实现：<b>完全不受影响</b>；</li>
 *   <li><b>墙钟后跳 1 小时</b> ⇒ 旧实现：{@code remainingMillis()} 变成 ≈ 3 602 000 ms
 *       ⇒ <b>超时永不触发</b> ⇒ <b>红</b>；新实现：<b>完全不受影响</b>。</li>
 * </ul>
 * <p>⇒ 这两条断言<b>不是「跑绿就放心」</b>，而是<b>旧实现跑上去必然红</b> ⇒ ⇒ 有判别力。
 *
 * <h2>⚠️ 注入点是<b>包级私有</b>的（不给生产留后门）</h2>
 * <p>{@code wallClockMillis} / {@code monotonicNanos} 是 {@code static} 可变字段，
 * 但<b>包级私有</b> ⇒ 外部 16 个消费模块<b>碰不到</b>；本类用 {@code @AfterEach} 复原。
 * ⇒ 另有一条守恒断言（§4）确认<b>生产代码里没有第二处赋值点</b>。
 */
@DisplayName("🔴 DeadlineState 的时基 = 单调钟（Go 的 time.Until 语义）+ 精度 = 纳秒（DRIFT-COMMON-005）")
class DeadlineStateMonotonicClockGateTest {

    /** 模拟用的墙钟基准（任意 epoch 毫秒）。 */
    private static final long FAKE_WALL_NOW = 1_700_000_000_000L;

    /** 模拟用的单调钟基准（任意纳秒）。 */
    private static final long FAKE_MONO_NOW = 123_456_789_000L;

    /** 注入前的原值（复原用）。 */
    private final java.util.function.LongSupplier originalWall =
            RpcInterceptor.DeadlineState.wallClockMillis;
    private final java.util.function.LongSupplier originalMono =
            RpcInterceptor.DeadlineState.monotonicNanos;

    @AfterEach
    void restoreClocks() {
        RpcInterceptor.DeadlineState.wallClockMillis = originalWall;
        RpcInterceptor.DeadlineState.monotonicNanos = originalMono;
    }

    // ==================================================================
    // ① 机制断言：存储域必须是「单调钟纳秒」，不是「墙钟 epoch 毫秒」
    // ==================================================================

    @Test
    @DisplayName("① 存储域 = 单调钟纳秒（反射读真实字段 ⇒ 旧实现存 epoch 毫秒 ⇒ 本条必红）")
    void storedDeadlineLivesInMonotonicNanosDomain() throws Exception {
        // 用可控的假时钟 ⇒ 存进去的值必须可预测
        RpcInterceptor.DeadlineState.wallClockMillis = () -> FAKE_WALL_NOW;
        RpcInterceptor.DeadlineState.monotonicNanos = () -> FAKE_MONO_NOW;

        long budgetMillis = 2000L;
        try (RpcInterceptor.DeadlineState.Scope ignored =
                     RpcInterceptor.DeadlineState.bindToCurrent(FAKE_WALL_NOW + budgetMillis)) {

            Long stored = readStoredDeadline();
            assertNotNull(stored, "DeadlineState 的 ThreadLocal 里没有值 ⇒ bindToCurrent 没生效");

            // 旧实现：stored = FAKE_WALL_NOW + 2000（epoch 毫秒域）
            //         ⇒ stored - FAKE_MONO_NOW ≈ 1.7e12 ⇒ 远大于预算纳秒 ⇒ 本断言红
            // 新实现：stored = FAKE_MONO_NOW + 2000e6 ⇒ 差值 = 预算纳秒
            long deltaNanos = stored - FAKE_MONO_NOW;
            long budgetNanos = budgetMillis * 1_000_000L;

            assertEquals(budgetNanos, deltaNanos,
                    "存进去的 deadline 必须落在**单调钟纳秒域**（= 注入的单调钟 + 预算纳秒）"
                            + " ⇒ 旧实现存的是墙钟 epoch 毫秒 ⇒ 本断言会红"
                            + "\n  stored      = " + stored
                            + "\n  注入单调钟  = " + FAKE_MONO_NOW
                            + "\n  预算纳秒    = " + budgetNanos
                            + "\n  实测差值    = " + deltaNanos);
        }
    }

    // ==================================================================
    // ② 行为断言：墙钟**前跳**不得让 deadline 提前过期
    // ==================================================================

    @Test
    @DisplayName("② 墙钟前跳 1 小时 ⇒ deadline 判定**不受影响**（旧实现会立刻判过期 ⇒ 本条必红）")
    void wallClockJumpForwardDoesNotExpireDeadline() {
        RpcInterceptor.DeadlineState.wallClockMillis = () -> FAKE_WALL_NOW;
        RpcInterceptor.DeadlineState.monotonicNanos = () -> FAKE_MONO_NOW;

        long budgetMillis = 2000L;
        try (RpcInterceptor.DeadlineState.Scope ignored =
                     RpcInterceptor.DeadlineState.bindToCurrent(FAKE_WALL_NOW + budgetMillis)) {

            // 模拟 NTP 前跳 / setSystemTime 前调 / 虚拟机快照恢复 ⇒ 墙钟 +1 小时
            RpcInterceptor.DeadlineState.wallClockMillis = () -> FAKE_WALL_NOW + 3_600_000L;

            long remaining = RpcInterceptor.DeadlineState.remainingMillis();
            assertEquals(budgetMillis, remaining,
                    "墙钟前跳 1 小时后，剩余预算必须**仍是 " + budgetMillis + "ms**"
                            + "（Go：time.Until 走 subMono ⇒ 墙钟跳变影响不到它）"
                            + " ⇒ 旧实现（每次读都拿墙钟再比一次）会算出 0 并判过期 ⇒ 本条必红");

            assertFalse(RpcInterceptor.DeadlineState.isExpired(),
                    "墙钟前跳 1 小时后 deadline **不得**被判为已过期"
                            + " ⇒ Go 侧此时 ctx 还没 Done()（Go 的 ctx.Deadline() 也不受墙钟跳变影响）");
        }
    }

    // ==================================================================
    // ③ 行为断言：墙钟**后跳**不得让 deadline 变成「永远到不了」
    // ==================================================================

    @Test
    @DisplayName("③ 墙钟后跳 1 小时 ⇒ deadline 判定**不受影响**（旧实现会算出 ≈3602 秒 ⇒ 超时永不触发 ⇒ 本条必红）")
    void wallClockJumpBackwardDoesNotExtendDeadline() {
        RpcInterceptor.DeadlineState.wallClockMillis = () -> FAKE_WALL_NOW;
        RpcInterceptor.DeadlineState.monotonicNanos = () -> FAKE_MONO_NOW;

        long budgetMillis = 2000L;
        try (RpcInterceptor.DeadlineState.Scope ignored =
                     RpcInterceptor.DeadlineState.bindToCurrent(FAKE_WALL_NOW + budgetMillis)) {

            // 模拟 NTP 后跳 / 手动改系统时间回拨 ⇒ 墙钟 −1 小时
            RpcInterceptor.DeadlineState.wallClockMillis = () -> FAKE_WALL_NOW - 3_600_000L;

            long remaining = RpcInterceptor.DeadlineState.remainingMillis();
            assertEquals(budgetMillis, remaining,
                    "墙钟后跳 1 小时后，剩余预算必须**仍是 " + budgetMillis + "ms**"
                            + " ⇒ 旧实现会算出 ≈ 3 602 000ms ⇒ **超时永不触发** ⇒ 本条必红");

            assertFalse(RpcInterceptor.DeadlineState.isExpired(),
                    "墙钟后跳后 deadline 不得被判为已过期");
        }
    }

    // ==================================================================
    // ④ 兼容断言：既有跨域调用方的语义**不得变**
    // ==================================================================

    @Test
    @DisplayName("④ 兼容入口 bindToCurrent(epochMillis) 的**语义不变**（跨域调用方零改动）")
    void compatibilityEntryKeepsItsSemantics() {
        RpcInterceptor.DeadlineState.wallClockMillis = () -> FAKE_WALL_NOW;
        RpcInterceptor.DeadlineState.monotonicNanos = () -> FAKE_MONO_NOW;

        // ④-a 未来的 deadline ⇒ 未过期、剩余 ≈ 预算
        try (RpcInterceptor.DeadlineState.Scope ignored =
                     RpcInterceptor.DeadlineState.bindToCurrent(FAKE_WALL_NOW + 2000L)) {
            assertFalse(RpcInterceptor.DeadlineState.isExpired(), "未来的 deadline 不得判过期");
            assertEquals(2000L, RpcInterceptor.DeadlineState.remainingMillis(),
                    "剩余毫秒必须等于预算（兼容入口按毫秒计）");
        }

        // ④-b 过去的 deadline ⇒ 已过期、剩余 = 0
        //     （comment-rpc 的 CommentSqlDeadlinePrecisionGateTest 就用这个形态）
        try (RpcInterceptor.DeadlineState.Scope ignored =
                     RpcInterceptor.DeadlineState.bindToCurrent(FAKE_WALL_NOW - 1000L)) {
            assertTrue(RpcInterceptor.DeadlineState.isExpired(), "过去的 deadline 必须判过期");
            assertEquals(0L, RpcInterceptor.DeadlineState.remainingMillis(),
                    "已过期时剩余毫秒必须是 0（不是负数）");
        }

        // ④-c 作用域结束 ⇒ ThreadLocal 被清掉 ⇒ 未设置态
        assertEquals(Long.MAX_VALUE, RpcInterceptor.DeadlineState.remainingMillis(),
                "作用域结束后必须回到「未设置」（remainingMillis == Long.MAX_VALUE）"
                        + " ⇒ 否则会在线程池复用时串味");
        assertFalse(RpcInterceptor.DeadlineState.isExpired(), "未设置态不得判过期");
    }

    // ==================================================================
    // ⑤ 精度断言：纳秒预算不被截成毫秒；饱和不溢出
    // ==================================================================

    @Test
    @DisplayName("⑤ 纳秒预算 + 饱和不溢出（Long.MAX_VALUE 哨兵不得翻成「已过期」）")
    void nanosecondBudgetAndSaturation() {
        RpcInterceptor.DeadlineState.wallClockMillis = () -> FAKE_WALL_NOW;
        RpcInterceptor.DeadlineState.monotonicNanos = () -> FAKE_MONO_NOW;

        // ⑤-a 亚毫秒预算：50 万纳秒（0.5ms）⇒ 推进 0.6ms 后必须过期
        try (RpcInterceptor.DeadlineState.Scope ignored =
                     RpcInterceptor.DeadlineState.bindRemainingNanos(500_000L)) {
            assertFalse(RpcInterceptor.DeadlineState.isExpired(), "刚绑定时不得已过期");
            // 推进单调钟 0.6ms（墙钟不动 ⇒ 证明判定只认单调钟）
            RpcInterceptor.DeadlineState.monotonicNanos = () -> FAKE_MONO_NOW + 600_000L;
            assertTrue(RpcInterceptor.DeadlineState.isExpired(),
                    "推进 0.6ms 后 0.5ms 的预算必须已过期"
                            + " ⇒ 旧实现把预算截到毫秒 ⇒ 亚毫秒预算根本表达不出来");
        }

        // ⑤-b 饱和：Long.MAX_VALUE 不得溢出成「负数 ⇒ 已过期」
        try (RpcInterceptor.DeadlineState.Scope ignored =
                     RpcInterceptor.DeadlineState.bindRemainingNanos(Long.MAX_VALUE)) {
            assertFalse(RpcInterceptor.DeadlineState.isExpired(),
                    "Long.MAX_VALUE 表示「无期限」⇒ 不得因溢出被判过期");
            assertTrue(RpcInterceptor.DeadlineState.remainingMillis() > 0L,
                    "Long.MAX_VALUE 下的剩余毫秒必须是极大正数（不是 0）");
        }

        // ⑤-c 兼容入口的 Long.MAX_VALUE 也不得溢出
        try (RpcInterceptor.DeadlineState.Scope ignored =
                     RpcInterceptor.DeadlineState.bindToCurrent(Long.MAX_VALUE)) {
            assertFalse(RpcInterceptor.DeadlineState.isExpired(),
                    "bindToCurrent(Long.MAX_VALUE) 不得因溢出被判过期");
            assertTrue(RpcInterceptor.DeadlineState.remainingMillis() > 0L,
                    "bindToCurrent(Long.MAX_VALUE) 的剩余毫秒必须是极大正数");
        }
    }

    // ==================================================================
    // ⑥ 守恒断言：生产路径不得再回到「墙钟 epoch 毫秒」
    // ==================================================================

    @Test
    @DisplayName("⑥ 守恒：生产代码里不再有「拿墙钟和 deadline 比」的写法")
    void productionCodeDoesNotCompareWallClockWithDeadline() throws Exception {
        Path file = Path.of("src/main/java/com/rag/common/observability/RpcInterceptor.java");
        if (!Files.isRegularFile(file)) {
            // surefire 的工作目录是模块目录；从 code source 向上找
            file = repoRoot().resolve("service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java");
        }
        assertTrue(Files.isRegularFile(file), "找不到 RpcInterceptor.java：" + file);

        String code = stripCommentsAndStrings(Files.readString(file, StandardCharsets.UTF_8));

        List<String> offenders = new ArrayList<>();
        if (code.contains("System.currentTimeMillis() >= d")
                || code.contains("d - System.currentTimeMillis()")
                || code.contains("System.currentTimeMillis() + timeout.toMillis()")) {
            offenders.add("仍用墙钟 epoch 毫秒比较 deadline");
        }
        assertTrue(offenders.isEmpty(),
                "以下写法说明修复被回退（⇒ 墙钟跳变会再次影响判定）：" + offenders);
    }

    @Test
    @DisplayName("⑥-b 守恒：两个注入点**必须是包级私有**（外部模块改不了 ⇒ 不给生产留后门）")
    void clockInjectionPointsAreNotPubliclyWritable() throws Exception {
        for (String name : new String[]{"wallClockMillis", "monotonicNanos"}) {
            Field field = RpcInterceptor.DeadlineState.class.getDeclaredField(name);
            int modifiers = field.getModifiers();

            assertFalse(java.lang.reflect.Modifier.isPublic(modifiers),
                    name + " 是 public ⇒ 外部模块能改时钟 ⇒ 生产环境可被篡改 deadline 判定");
            assertFalse(java.lang.reflect.Modifier.isProtected(modifiers),
                    name + " 是 protected ⇒ 同上");
            assertFalse(java.lang.reflect.Modifier.isPrivate(modifiers),
                    name + " 是 private ⇒ 同包的守门改不了它 ⇒ 本类就测不到「时钟跳变」这个行为");
            assertTrue(java.lang.reflect.Modifier.isStatic(modifiers),
                    name + " 必须是 static（否则每个实例各有一份时钟）");
        }

        // DeadlineState 上不得有 public 的「设时钟」入口
        for (Method m : RpcInterceptor.DeadlineState.class.getDeclaredMethods()) {
            assertFalse(java.lang.reflect.Modifier.isPublic(m.getModifiers())
                            && m.getName().toLowerCase(java.util.Locale.ROOT).contains("clock"),
                    "DeadlineState 上出现了 public 的改时钟入口：" + m.getName());
        }
    }

    // ==================================================================
    // helpers
    // ==================================================================

    /** 反射读 {@code DeadlineState} 私有静态 {@code ThreadLocal} 里当前线程存的值。 */
    private static Long readStoredDeadline() throws Exception {
        Field field = RpcInterceptor.DeadlineState.class.getDeclaredField("DEADLINE_NANOS");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        ThreadLocal<Long> holder = (ThreadLocal<Long>) field.get(null);
        return holder.get();
    }

    /** 仓库根：向上找同时含 {@code service/} 与 {@code pom.xml} 的目录。 */
    private static Path repoRoot() {
        Path p = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (p != null) {
            if (Files.isDirectory(p.resolve("service")) && Files.isRegularFile(p.resolve("pom.xml"))) {
                return p;
            }
            p = p.getParent();
        }
        throw new IllegalStateException("找不到仓库根（user.dir=" + System.getProperty("user.dir") + "）");
    }

    /** 去掉注释与字符串字面量 ⇒ 只留「代码」⇒ 避免 javadoc 里的字样造成假红/假绿。 */
    private static String stripCommentsAndStrings(String src) {
        StringBuilder out = new StringBuilder(src.length());
        boolean inLine = false;
        boolean inBlock = false;
        boolean inStr = false;
        boolean inChar = false;
        boolean escaped = false;
        for (int i = 0; i < src.length(); i++) {
            char c = src.charAt(i);
            char n = i + 1 < src.length() ? src.charAt(i + 1) : '\0';
            if (inLine) {
                if (c == '\n') {
                    inLine = false;
                    out.append(c);
                }
                continue;
            }
            if (inBlock) {
                if (c == '*' && n == '/') {
                    inBlock = false;
                    i++;
                }
                continue;
            }
            if (inStr) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inStr = false;
                }
                out.append(' ');
                continue;
            }
            if (inChar) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '\'') {
                    inChar = false;
                }
                out.append(' ');
                continue;
            }
            if (c == '/' && n == '/') {
                inLine = true;
                i++;
                continue;
            }
            if (c == '/' && n == '*') {
                inBlock = true;
                i++;
                continue;
            }
            if (c == '"') {
                inStr = true;
                out.append(' ');
                continue;
            }
            if (c == '\'') {
                inChar = true;
                out.append(' ');
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }
}
