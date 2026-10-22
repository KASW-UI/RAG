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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link GoStyleMaxConnsFilter} 的并发语义锁定。
 *
 * <p><b>为什么用「直接驱动 filter」而不是 MockMvc</b>：
 * MaxConns 的本质是<b>并发闸门</b>，需要两个请求<b>真正同时</b>在途才能观察到 503。
 * MockMvc 的单次 {@code perform} 是同步的，拿不到「一个占着、一个被拒」这个状态。
 * ⇒ 这里直接构造 filter + 两个线程驱动 {@code doFilter}，是<b>确定性</b>的，
 * 且不依赖任何容器调度运气。
 *
 * <p>⚠️ <b>本类证明的是闸门逻辑，不是响应头</b>：裸 503 在真实 Tomcat 下的
 * 响应头（无 Content-Type / 空 body）由 {@code tools/difftest/check-protection-layer.sh}
 * 在真容器守。两层不互相冒充（AGENTS.md §3.4.2）。
 */
class GoStyleMaxConnsFilterTest {

    private static GoStyleProtectionProperties props(int maxConns, long maxBytes) {
        GoStyleProtectionProperties p = new GoStyleProtectionProperties();
        p.setMaxConns(maxConns);
        p.setMaxBytes(maxBytes);
        return p;
    }

    @Test
    @DisplayName("maxConns<=0 ⇒ no-op（Go maxconnshandler.go:13-17 的短路）")
    void zeroMaxConnsIsNoOp() throws Exception {
        GoStyleMaxConnsFilter filter = new GoStyleMaxConnsFilter(props(0, 1048576L));
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicInteger hits = new AtomicInteger();
        FilterChain chain = (req, res) -> hits.incrementAndGet();

        filter.doFilter(new MockHttpServletRequest("POST", "/x"), resp, chain);
        assertEquals(1, hits.get(), "maxConns=0 时不得限流");
        assertEquals(200, resp.getStatus());
    }

    @Test
    @DisplayName("达到并发上限 ⇒ 裸 503（Go w.WriteHeader(503)，无 body）")
    void rejectsWithBare503WhenSaturated() throws Exception {
        GoStyleMaxConnsFilter filter = new GoStyleMaxConnsFilter(props(1, 1048576L));

        CountDownLatch inChain = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        FilterChain blocking = (req, res) -> {
            inChain.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        // 第一个请求占住闸门
        Thread holder = new Thread(() -> {
            try {
                filter.doFilter(new MockHttpServletRequest("POST", "/x"),
                        new MockHttpServletResponse(), blocking);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
        holder.start();
        assertTrue(inChain.await(5, TimeUnit.SECONDS), "前置条件：第一个请求必须已占住闸门");

        // 第二个请求应当立刻被拒
        MockHttpServletResponse second = new MockHttpServletResponse();
        AtomicInteger downstream = new AtomicInteger();
        filter.doFilter(new MockHttpServletRequest("POST", "/x"), second,
                (req, res) -> downstream.incrementAndGet());

        assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, second.getStatus());
        assertEquals(0, downstream.get(), "被拒的请求不得触达下游");
        assertEquals(0, second.getContentAsByteArray().length, "Go 侧是裸 WriteHeader，body 必须为空");
        assertEquals(1, filter.inFlight(), "第一个请求仍在途");

        release.countDown();
        holder.join(5_000);
        assertEquals(0, filter.inFlight(), "请求结束后必须归还许可（Go 的 defer latch.Return()）");
    }

    @Test
    @DisplayName("许可归还后可再次通过（信号量不泄漏）")
    void permitsAreReturnedAfterCompletion() throws Exception {
        GoStyleMaxConnsFilter filter = new GoStyleMaxConnsFilter(props(1, 1048576L));
        AtomicInteger hits = new AtomicInteger();
        FilterChain ok = (req, res) -> hits.incrementAndGet();

        for (int i = 0; i < 5; i++) {
            MockHttpServletResponse resp = new MockHttpServletResponse();
            filter.doFilter(new MockHttpServletRequest("POST", "/x"), resp, ok);
            assertEquals(200, resp.getStatus(), "第 " + i + " 次必须通过（许可已归还）");
        }
        assertEquals(5, hits.get());
        assertEquals(0, filter.inFlight());
    }

    @Test
    @DisplayName("下游抛异常时许可也必须归还（Go 的 defer 语义）")
    void permitsReturnedEvenWhenDownstreamThrows() throws Exception {
        GoStyleMaxConnsFilter filter = new GoStyleMaxConnsFilter(props(1, 1048576L));
        FilterChain boom = (req, res) -> {
            throw new IllegalStateException("downstream blew up");
        };
        try {
            filter.doFilter(new MockHttpServletRequest("POST", "/x"),
                    new MockHttpServletResponse(), boom);
        } catch (IllegalStateException expected) {
            // 异常向上传播是正确行为（不吞异常）
        }
        assertEquals(0, filter.inFlight(),
                "异常路径下许可泄漏会让服务逐步锁死 —— 这是 MaxConns 最危险的失效模式");
    }

    @Test
    @DisplayName("生效值来自配置（默认 10000，对齐 Go rest/config.go:52）")
    void defaultMatchesGo() {
        GoStyleMaxConnsFilter filter =
                new GoStyleMaxConnsFilter(new GoStyleProtectionProperties());
        assertEquals(10000, filter.maxConns());
        assertNotNull(filter);
    }

    @Test
    @DisplayName("MaxBytes 生效值默认 1048576（Go rest/config.go:53）")
    void maxBytesDefaultMatchesGo() {
        assertEquals(1048576L, new GoStyleMaxBytesFilter(
                new GoStyleProtectionProperties()).maxBytes());
    }
}
