package com.rag.common.protection;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link GoStyleCpuSheddingFilter} 的行为锁定 —— 对齐 Go
 * {@code rest/handler/sheddinghandler.go:22-56} + {@code core/load/adaptiveshedder.go}。
 *
 * <p><b>为什么此前没有这一类</b>：降载的三个判据
 * （{@code systemOverloaded} / {@code stillHot} / {@code highThru}）全是<b>纯函数</b>，
 * {@link GoStyleAdaptiveShedderTest} 用注入 CPU 读数的方式把<b>判据本身</b>测透了。
 * 但<b>「filter 真的在链上、真的按 Go 的形状拒绝」</b>这一层此前<b>没有任何运行时证据</b>
 * ⇒ 对应 {@code docs/DRIFT/users/user/api.md} 的 {@code DRIFT-USERAPI-008} 表格里
 * Shedding 一行标注的「⚠️ 未单独实测」。
 *
 * <p><b>⚠️ 为什么「把 CPU 打满」不可行、必须注入读数</b>：
 * 真把 CPU 压到 900‰ 既慢又不确定（而且本机同时跑着别的会话的容器），
 * 而 {@code systemOverloaded()} 的输入只有 CPU 读数一个 ⇒ 注入即可精确构造。
 * ⇒ 这是 §3.3「验证机制实际生效」在**不能真造负载**时的正确做法：
 * <b>注入机制的真实输入，断言机制的真实输出形状</b>。
 */
class GoStyleCpuSheddingFilterTest {

    private static GoStyleProtectionProperties props(long cpuThreshold) {
        GoStyleProtectionProperties p = new GoStyleProtectionProperties();
        p.getCpuShedding().setCpuThreshold(cpuThreshold);
        return p;
    }

    @Test
    @DisplayName("RT 的口径是**浮点**毫秒（Go adaptiveshedder.go:285），不是整型截断")
    void rtUsesFloatMilliseconds() {
        assertEquals(1.2D, GoStyleCpuSheddingFilter.elapsedRtMs(0L, 1_200_000L), 1e-9,
                "1.2ms 必须原样保留小数 —— 整型截断会让 RT 系统性少记最多 1ms，"
                        + "⇒ minRt 偏小 ⇒ 在途上限偏低 ⇒ 比 Go 更早降载");
        assertTrue(GoStyleCpuSheddingFilter.elapsedRtMs(0L, 999_999L) > 0D,
                "0.999999ms 不得被截断成 0");
    }

    @Test
    @DisplayName("CPU 过载且在途超上限 ⇒ 裸 503 空 body（Go sheddinghandler.go:40 的 w.WriteHeader(503)）")
    void overloadRejectsWithBare503() throws Exception {
        GoStyleCpuSheddingFilter filter = new GoStyleCpuSheddingFilter(props(900));
        // 注入恒定的高 CPU 读数 ⇒ systemOverloaded() 恒真（Go: stat.CpuUsage() >= 900）
        filter.setCpuPermilleReader(() -> 1000);

        // ── 预热：造出「已完成桶里的低 RT」⇒ minRt 变小 ⇒ maxFlight 落到下界 1
        //
        // Go maxFlight = max(maxPass * minRt * windowScale, 1)：
        //   冷启动时 minRt 取默认 1000ms ⇒ maxFlight = 1 * 1000 * 0.01 = 10 ⇒ 上限太松，测不出来。
        //   窗口里有过一个**已完成桶**的低 RT 后 ⇒ minRt 变小 ⇒ maxFlight 落到下界 1
        //   ⇒ overloadFactor(0.1) ⇒ 上限 cap = 0.1 ⇒ 「在途 ≥ 2」即可触发。
        AtomicInteger warm = new AtomicInteger();
        FilterChain fast = (req, res) -> warm.incrementAndGet();
        filter.doFilter(new MockHttpServletRequest("POST", "/x"), new MockHttpServletResponse(), fast);
        assertEquals(1, warm.get());
        // 桶宽 100ms ⇒ 睡过桶边界，那个桶才变成「已完成桶」（Go: IgnoreCurrentBucket）
        Thread.sleep(150L);

        // ── 构造 3 个并发在途，其中**只有一个**先结束
        //
        // Go highThru() = avgFlying > cap && flying > cap（cap = maxFlight * overloadFactor）
        // 而 avgFlying **只在 delta<0 时更新**：avgFlying = avgFlying*0.9 + flying_new*0.1
        // ⇒ 要让 avgFlying > 0.1，必须在 **flying ≥ 3** 时发生一次归零（此时 flying_new = 2）。
        CountDownLatch inChain = new CountDownLatch(3);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch releaseRest = new CountDownLatch(1);

        Thread[] holders = new Thread[3];
        for (int i = 0; i < 3; i++) {
            final CountDownLatch mine = (i == 0) ? releaseFirst : releaseRest;
            holders[i] = new Thread(() -> {
                try {
                    filter.doFilter(new MockHttpServletRequest("POST", "/x"),
                            new MockHttpServletResponse(), (req, res) -> {
                                inChain.countDown();
                                try {
                                    mine.await(5, TimeUnit.SECONDS);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            });
                } catch (Exception e) {
                    throw new AssertionError(e);
                }
            });
            holders[i].start();
        }
        assertTrue(inChain.await(5, TimeUnit.SECONDS), "前置条件：3 个请求必须都在途");

        // 只放行第 0 个 ⇒ flying 3→2，avgFlying = 0*0.9 + 2*0.1 = 0.2 > cap(0.1)
        releaseFirst.countDown();
        holders[0].join(5_000);

        // ── 此刻 CPU 恒过载、avgFlying 与 flying 双双越过上限 ⇒ 下一个请求必须被拒
        MockHttpServletResponse rejected = new MockHttpServletResponse();
        AtomicInteger downstream = new AtomicInteger();
        filter.doFilter(new MockHttpServletRequest("POST", "/x"), rejected,
                (req, res) -> downstream.incrementAndGet());

        assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, rejected.getStatus(),
                "CPU 过载 + 在途超上限 ⇒ Go sheddinghandler.go:40 写 503");
        assertEquals(0, downstream.get(), "被拒的请求不得触达下游");
        assertEquals(0, rejected.getContentAsByteArray().length,
                "Go 侧是裸 w.WriteHeader(503) ⇒ body 必须为空（不走 envelope）");

        releaseRest.countDown();
        for (Thread t : holders) {
            t.join(5_000);
        }
    }
}
