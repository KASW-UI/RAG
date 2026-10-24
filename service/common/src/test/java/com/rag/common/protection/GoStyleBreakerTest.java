package com.rag.common.protection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link GoStyleBreaker} 对 Go {@code core/breaker/googlebreaker.go:52-79} {@code accept()}
 * 的行为锁定。
 *
 * <p><b>为什么必须用注入的确定性随机源</b>：熔断的最终一步是概率判定
 * （{@code proba.TrueOnProba(dropRatio)}）。若用真随机，要么测不出「会拒绝」，
 * 要么偶发红。<b>概率性逻辑不注入随机源就没法写有意义的测试</b> ——
 * 这一点本身就是本类必须有测试构造器（{@code GoStyleBreaker(String, DoubleSupplier)}）的理由。
 *
 * <p><b>断言策略</b>：所有用例都<b>不依赖真实时间推进</b>（窗口是 10s，真等会让测试变慢 100 倍），
 * 而是通过「把当前桶写满失败 → 切到下一个桶」来构造 history。做不到的地方会明确注释。
 */
class GoStyleBreakerTest {

    /** 构造一个「random 恒返回 x」的源。{@code x >= dropRatio} ⇒ 拒绝。 */
    private static java.util.function.DoubleSupplier fixed(double x) {
        return () -> x;
    }

    @Test
    @DisplayName("冷启动保护：前 5 个请求一律放行（Go protection=5）")
    void coldStartProtectionAllowsFirstFive() {
        // protection=5 ⇒ total<=5 时 dropRatio<0 ⇒ 放行，与随机值无关
        GoStyleBreaker breaker = new GoStyleBreaker("POST:///m", fixed(0.0));
        for (int i = 0; i < 5; i++) {
            assertTrue(breaker.accept(), "第 " + i + " 个请求必须放行（protection 窗口内）");
            breaker.markSuccess();
        }
    }

    @Test
    @DisplayName("全成功历史 ⇒ 永远放行")
    void allSuccessNeverTrips() {
        GoStyleBreaker breaker = new GoStyleBreaker("POST:///m", fixed(0.0));
        for (int i = 0; i < 500; i++) {
            assertTrue(breaker.accept(), "全部成功时不得熔断（第 " + i + " 次）");
            breaker.markSuccess();
        }
    }

    @Test
    @DisplayName("大量失败 + random 恒为 0 ⇒ 必然拒绝（dropRatio 远大于 0）")
    void heavyFailureTripsWhenRandomIsZero() {
        GoStyleBreaker breaker = new GoStyleBreaker("POST:///m", fixed(0.0));
        // 先用成功把窗口灌成「有底」的状态，再用失败压垮
        for (int i = 0; i < 50; i++) {
            breaker.accept();
            breaker.markSuccess();
        }
        sleepOneBucket();
        for (int i = 0; i < 50; i++) {
            breaker.accept();
            breaker.markFailure();
        }
        sleepOneBucket();

        boolean tripped = false;
        for (int i = 0; i < 20 && !tripped; i++) {
            if (!breaker.accept()) {
                tripped = true;
            }
            sleepOneBucket();
        }
        assertTrue(tripped, "连续 50 次失败后，窗口内必须出现拒绝");
    }

    @Test
    @DisplayName("force-pass 保底：lastPass 有值且距今 >1s 时无条件放行探测请求")
    void forcePassAfterOneSecond() throws InterruptedException {
        AtomicReference<Double> rnd = new AtomicReference<>(0.0D);   // 0.0 ⇒ 必拒
        GoStyleBreaker breaker = new GoStyleBreaker("POST:///m", rnd::get);

        for (int i = 0; i < 50; i++) {
            breaker.accept();
            breaker.markSuccess();
        }
        sleepOneBucket();
        for (int i = 0; i < 50; i++) {
            breaker.accept();
            breaker.markFailure();
        }
        sleepOneBucket();

        // 先逼出一次拒绝
        boolean sawReject = false;
        for (int i = 0; i < 100 && !sawReject; i++) {
            sawReject = !breaker.accept();
            sleepOneBucket();
        }
        assertTrue(sawReject, "前置条件：必须先能拒绝，否则本用例无意义");

        // 放行一次以刷新 lastPass（Go 侧 lastPass 只在这两处被写，见下方用例）
        rnd.set(0.999D);
        assertTrue(breaker.accept(), "前置条件：需要一次成功放行来写入 lastPass");

        // 等过 forcePassDuration（1s）后，即使 random 恒命中拒绝也必须放行
        Thread.sleep(1_050L);
        rnd.set(0.0D);
        assertTrue(breaker.accept(),
                "距上次放行超过 1s 后必须强制放行一个探测请求，否则熔断永不恢复");
    }

    @Test
    @DisplayName("Go 真实性质：lastPass 从未被写入（初值 0）时，force-pass 不生效")
    void noForcePassWhenLastPassNeverSet() throws InterruptedException {
        // googlebreaker.go:63 的判据是 `lastPass > 0 && since(lastPass) > forcePassDuration`
        // 而 lastPass 只在两处被写：(a) force-pass 分支自身、(b) 概率判定通过之后。
        // ⇒ 若熔断在「概率判定」这一步直接拒绝、从未走过 (b)，lastPass 恒为 0，
        //    force-pass 永远不触发。本用例把这个**不直观但真实**的性质锁住，
        //    防止有人「顺手加个保护」而改变熔断恢复时机。
        //
        // ⚠️ 本用例必须**在 10s 窗口内**完成：一旦失败历史滚出窗口，dropRatio 变负，
        //    accept() 会走 `dropRatio <= 0` 早退而放行，断言语义就失效了。
        //    ⇒ 迭代次数与 sleep 都刻意压到最小（实测 ~3.5s）。
        AtomicReference<Double> rnd = new AtomicReference<>(0.0D);
        GoStyleBreaker breaker = new GoStyleBreaker("POST:///m", rnd::get);

        for (int i = 0; i < 50; i++) {
            breaker.accept();          // protection 窗口内，dropRatio<=0 早退，不写 lastPass
            breaker.markFailure();
        }
        sleepOneBucket();
        for (int i = 0; i < 50; i++) {
            breaker.accept();
            breaker.markFailure();
        }
        sleepOneBucket();

        boolean sawReject = false;
        for (int i = 0; i < 10 && !sawReject; i++) {
            sawReject = !breaker.accept();   // 恒命中拒绝 ⇒ lastPass 始终未被写入
            sleepOneBucket();
        }
        assertTrue(sawReject, "前置条件：必须先能拒绝");

        Thread.sleep(1_050L);               // 超过 forcePassDuration
        assertFalse(breaker.accept(),
                "lastPass 恒为 0 时，即使过了 1s 也不 force-pass —— 与 Go 一致");
    }

    @Test
    @DisplayName("drop 也计入 Sum：失败+drop 混合后 dropRatio 足以触发拒绝")
    void dropCountsTowardsSum() {
        // dropRatio = (total - protection - weightedAccepts)/(total+1)，accepts=0 时
        // total=60（30 failure + 30 drop）⇒ dropRatio = 55/61 ≈ 0.90。
        // 用 random=0.5 断言「会拒」—— 注意 random 必须**小于** dropRatio 才会命中，
        // 用 0.999 会误判（0.999 > 0.90 ⇒ 放行）。
        GoStyleBreaker breaker = new GoStyleBreaker("POST:///m", fixed(0.5));
        for (int i = 0; i < 30; i++) {
            breaker.accept();
            breaker.markFailure();
            breaker.markDrop();
        }
        sleepOneBucket();
        assertFalse(breaker.accept(),
                "random=0.5 < dropRatio≈0.90 时应拒绝（否则 drop 未被计入 Sum）");
    }

    @Test
    @DisplayName("breaker 是 per-route 的：不同路由互不影响")
    void breakersAreIsolatedPerRoute() {
        GoStyleBreaker a = new GoStyleBreaker("POST:///a", fixed(0.0));
        GoStyleBreaker b = new GoStyleBreaker("POST:///b", fixed(0.0));
        for (int i = 0; i < 50; i++) {
            a.accept();
            a.markFailure();
        }
        sleepOneBucket();
        assertTrue(b.accept(), "路由 b 未经历过失败，不得受路由 a 的影响");
    }

    @Test
    @DisplayName("name 与 Go 的 METHOD://path 拼接一致")
    void breakerNameUsesGoSeparator() {
        assertEquals("POST:///message/v1/chat",
                new GoStyleBreaker("POST:///message/v1/chat", fixed(0)).name());
    }

    /** 跨过 250ms 桶边界，让写入落到「下一个」桶，使上一个桶成为已完成桶。 */
    private static void sleepOneBucket() {
        try {
            Thread.sleep(260L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted", e);
        }
    }
}