package com.rag.common.observability;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * worker 池<b>有上界</b> + 饱和时回 Go 形状的裸 503。
 *
 * <h2>为什么这条必须有</h2>
 *
 * <p>2026-10-22 之前 {@code HTTP_EXECUTOR} 是 {@code Executors.newCachedThreadPool}，
 * 即 {@code maxPoolSize == Integer.MAX_VALUE}。由于超时分支只能
 * {@code Future.cancel(true)}（对 grpc-java 阻塞调用无效），
 * 下游卡住时表现是<b>线程数无界增长</b>。本类把上界钉死，防止它被改回去。
 *
 * <h2>⚠️ 本类会真的占满 512 条线程</h2>
 *
 * <p>这是刻意的：要触发 {@link java.util.concurrent.ThreadPoolExecutor.AbortPolicy}，
 * 就必须真的把池打满。所有阻塞任务都在 {@code finally} 里释放，
 * 保证即使断言失败也不会把池留给后续用例（否则会引发一片无关的红）。
 */
class HttpMiddlewareWorkerPoolTest {

    @Test
    @DisplayName("worker 池有明确上界，且**远小于**旧实现的 Integer.MAX_VALUE")
    void workerPoolIsBounded() {
        assertEquals(HttpMiddleware.MAX_HTTP_WORKERS,
                HttpMiddleware.httpExecutor().getMaximumPoolSize(),
                "上界必须来自 MAX_HTTP_WORKERS 这一个常量，避免出现「两处各写一个数字」");
        assertNotEquals(Integer.MAX_VALUE, HttpMiddleware.httpExecutor().getMaximumPoolSize(),
                "回到 newCachedThreadPool 就是这次要修的漂移本身");
    }

    @Test
    @DisplayName("上界 ≥ Tomcat 默认 max-threads(200) 的 2 倍 ⇒ 正常流量永不触发拒绝")
    void boundLeavesHeadroomOverContainerThreads() {
        // ⚠️ 这是**关系**断言，不是魔法数字断言：worker 只在请求在途时被占用，
        //    而并发上限由容器线程数决定。上界若低于 max-threads，正常流量就会开始被拒。
        assertTrue(HttpMiddleware.MAX_HTTP_WORKERS >= 200 * 2,
                "上界 " + HttpMiddleware.MAX_HTTP_WORKERS
                        + " 相对 Tomcat 默认 max-threads=200 缺乏余量，正常流量可能被误拒");
    }

    @Test
    @DisplayName("池饱和 ⇒ 请求被拒为**裸 503 空 body**，且不经过业务链")
    void saturationShedsWithBare503() throws Exception {
        ThreadPoolExecutorFacade facade = null;   // 占位以便 finally 引用
        CountDownLatch release = new CountDownLatch(1);
        List<Future<?>> fillers = new ArrayList<>();
        MockHttpServletResponse lastResponse = null;
        try {
            // 把池打满：每个任务都在 release 上阻塞
            java.util.concurrent.RejectedExecutionException saturatedAt = null;
            for (int i = 0; i < HttpMiddleware.MAX_HTTP_WORKERS + 32; i++) {
                try {
                    fillers.add(HttpMiddleware.httpExecutor().submit(() -> {
                        try {
                            release.await(30, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }));
                } catch (java.util.concurrent.RejectedExecutionException e) {
                    saturatedAt = e;
                    break;
                }
            }
            assertNotNull(saturatedAt, "打满 " + (HttpMiddleware.MAX_HTTP_WORKERS + 32)
                    + " 个任务后必须出现拒绝，否则说明池其实无界（本测试的前提就错了）");

            // 现在再走一次正常请求：业务链**不应**被执行
            boolean[] chainRan = {false};
            FilterChain chain = (req, resp) -> chainRan[0] = true;
            MockHttpServletResponse resp = new MockHttpServletResponse();
            HttpMiddleware.newHttpMiddleware("test", Duration.ofSeconds(5), Duration.ofSeconds(1))
                    .doFilter(new MockHttpServletRequest("GET", "/like/v1/x"), resp, chain);
            lastResponse = resp;

            assertTrue(!chainRan[0],
                    "被容量拒绝的请求**绝不能**执行业务 —— 那正是 CallerRunsPolicy 的错误之处"
                            + "（它把业务搬回容器线程，等于超时保护整条失效）");
            assertEquals(503, resp.getStatus());
            assertEquals("", resp.getContentAsString(),
                    "Go maxconnshandler.go:12-36 是裸 w.WriteHeader(503)：无 body、无 Content-Type。"
                            + "⚠️ 刻意与「超时 503」不同 —— 超时那条走 writeTimeoutResponse，有 body。");
        } finally {
            release.countDown();
            for (Future<?> f : fillers) {
                f.cancel(true);
            }
        }
    }

    /** 占位类型，避免误用被删的内部类；保留以明示本测试不依赖任何测试专用后门。 */
    private static final class ThreadPoolExecutorFacade {
    }
}