package com.rag.common.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>守门：请求 deadline 载体的作用域（Scope）不会「串味」，也不会「漏 clear」。</b>
 *
 * <h2>这条守门钉住的是什么（为什么必须有）</h2>
 * <p>{@link GoStyleRequestDeadline} 原先只有 {@code begin}/{@code clear}：
 * <ul>
 *   <li><b>漏 {@code clear}</b> ⇒ 线程池复用时<b>上一个请求的 deadline 漏到下一个</b>
 *       ⇒ 新请求会被判成「已过期」⇒ 立刻失败；</li>
 *   <li><b>嵌套</b> ⇒ 内层 {@code begin} 覆盖外层、外层 {@code clear} 把内层的也清了
 *       ⇒ 外层的预算丢失。</li>
 * </ul>
 * ⇒ 本轮引入的 {@link GoStyleRequestDeadline.Scope} 把这两条都消掉：
 * <b>保存原值 + 原样还原</b>，且编译器保证配对。
 *
 * <h2>⚠️ 为什么这条不能省（「跑绿 ≠ 对」）</h2>
 * <p>{@code GoStyleRequestDeadlineTest} 与 {@code DeadlineCancellationSemanticsTest}
 * 的 {@code @AfterEach clear()} 都跑在<b>测试线程</b>上 ⇒ 它们<b>抓不到</b>
 * 「生产路径上的 grpc executor 线程复用」这一形态 ⇒ ⇒ 本条专门补这个缺口。
 */
class GoStyleRequestDeadlineScopeGateTest {

    @Test
    @DisplayName("作用域关闭后载体消失 ⇒ 下一个请求不会捡到上一个的 deadline")
    void scopeRemovesCarrierOnClose() {
        assertThat(GoStyleRequestDeadline.isPresent())
                .as("前置：本测试线程上不应残留载体")
                .isFalse();

        try (GoStyleRequestDeadline.Scope ignored =
                     GoStyleRequestDeadline.bindAbsoluteNanos(System.nanoTime() + TimeUnit.SECONDS.toNanos(60))) {
            assertThat(GoStyleRequestDeadline.isPresent())
                    .as("作用域内必须可见")
                    .isTrue();
            assertThat(GoStyleRequestDeadline.remainingMillis())
                    .as("作用域内剩余应为正")
                    .isGreaterThan(0L);
        }

        assertThat(GoStyleRequestDeadline.isPresent())
                .as("作用域关闭后必须清掉 ⇒ 否则线程池复用会串味")
                .isFalse();
        assertThat(GoStyleRequestDeadline.remainingMillis())
                .as("无载体时 remainingMillis 必须返回 -1（=「无预算」）")
                .isEqualTo(-1L);
    }

    @Test
    @DisplayName("嵌套作用域：内层关闭后**外层的值被原样还原**（不是被清掉）")
    void nestedScopeRestoresOuterValue() {
        long outer = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        long inner = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);

        try (GoStyleRequestDeadline.Scope outerScope = GoStyleRequestDeadline.bindAbsoluteNanos(outer)) {
            long outerRemaining = GoStyleRequestDeadline.remainingMillis();
            try (GoStyleRequestDeadline.Scope innerScope = GoStyleRequestDeadline.bindAbsoluteNanos(inner)) {
                assertThat(GoStyleRequestDeadline.remainingMillis())
                        .as("内层应是更紧的那个预算")
                        .isLessThan(outerRemaining);
            }
            assertThat(GoStyleRequestDeadline.isPresent())
                    .as("内层关闭后外层必须还在（begin/clear 形态下这里会被清掉）")
                    .isTrue();
            assertThat(GoStyleRequestDeadline.remainingMillis())
                    .as("还原的应是外层那个预算")
                    .isGreaterThan(0L);
        }

        assertThat(GoStyleRequestDeadline.isPresent())
                .as("最外层关闭后必须清掉")
                .isFalse();
    }

    @Test
    @DisplayName("线程隔离：另一个线程上看不到本线程的载体（ThreadLocal 的基本语义）")
    void carrierIsThreadLocal() throws Exception {
        AtomicReference<Boolean> otherThreadSees = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        try (GoStyleRequestDeadline.Scope ignored =
                     GoStyleRequestDeadline.bindAbsoluteNanos(System.nanoTime() + TimeUnit.SECONDS.toNanos(60))) {
            Thread worker = new Thread(() -> {
                otherThreadSees.set(GoStyleRequestDeadline.isPresent());
                done.countDown();
            });
            worker.start();
            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
            worker.join(5_000L);
        }

        assertThat(otherThreadSees.get())
                .as("载体是 ThreadLocal ⇒ 别的线程看不到")
                .isFalse();
    }
}
