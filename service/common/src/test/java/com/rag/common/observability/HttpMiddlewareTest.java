package com.rag.common.observability;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HttpMiddleware 关键契约验证：覆盖 DRIFT-004（499 vs 503 区分）、DRIFT-005
 * （超时后 client 收到严格 "Request Timeout" ）、正常路径 envelope 解析。
 */
class HttpMiddlewareTest {

    private InMemorySpanExporter exporter;
    private SdkTracerProvider tracerProvider;

    @BeforeEach
    void installSdk() {
        exporter = InMemorySpanExporter.create();
        tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        io.opentelemetry.api.GlobalOpenTelemetry.resetForTest();
        io.opentelemetry.sdk.OpenTelemetrySdk sdk = io.opentelemetry.sdk.OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
        io.opentelemetry.api.GlobalOpenTelemetry.set(sdk);
    }

    @AfterEach
    void cleanup() {
        io.opentelemetry.api.GlobalOpenTelemetry.resetForTest();
        tracerProvider.shutdown();
    }

    /**
     * DRIFT-004 修复验证：worker 超时（deadline exceed）→ status=503（不是 499）。
     * <p>对齐 Go 版 {@code ctx.Err() == context.DeadlineExceeded → 503}。
     */
    @Test
    void deadlineExceedReturns503() throws Exception {
        Filter filter = HttpMiddleware.newHttpMiddleware("svc", Duration.ofMillis(50), Duration.ofMillis(10));
        SleepHandler sleepHandler = new SleepHandler(500);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/slow");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        // 用全局 span 拿当前 ctx
        Span span = tracerProvider.get("test").spanBuilder("req").startSpan();
        Context ctx = Context.current().with(span);
        FilterChain chain = (ServletRequest r, ServletResponse w) -> {
            try (var s = ctx.makeCurrent()) {
                try {
                    sleepHandler.handle(r, w);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new ServletException(ie);
                }
            }
        };
        try {
            filter.doFilter(req, resp, chain);
        } catch (ServletException expected) {
            // 业务线程被 interrupt 后抛 InterruptedException，被 ServletException 包；不影响结果
        }
        assertEquals(503, resp.getStatus(), "deadline exceed must be 503, not 499");
        // D-11：断言值随之变化。Go 的 http.Error 会写 `body + "\n"`
        // （net/http 源码 errors.go：fmt.Fprintln(w, error)），Java 侧旧实现是裸 write()、
        // 不带换行也不带 Content-Type。这里改为与 Go 一致的 "Request Timeout\n"。
        // 关键是**不能**把断言放宽成 contains：必须锁住完整 body，防止将来又漏字段。
        assertEquals("Request Timeout\n", resp.getContentAsString(),
                "client must receive exactly Go's http.Error body, no leaked business body");
        assertTrue(resp.getContentType() != null && resp.getContentType().startsWith("text/plain"),
                "Go 的 http.Error 会设置 Content-Type: text/plain; charset=utf-8");
    }

    /**
     * DRIFT-005 修复验证：buffer 模式下，超时后 client 不会收到任何业务 body（即使 handler 已经 write 过）。
     * <p>模拟 handler 写一部分业务 body 再超时，验证 client 收到的是纯 "Request Timeout"。
     */
    @Test
    void timeoutDoesNotLeakPartialBody() throws Exception {
        Filter filter = HttpMiddleware.newHttpMiddleware("svc", Duration.ofMillis(50), Duration.ofMillis(10));
        // handler 先写部分业务 JSON，再 sleep 超时
        PartialWriteHandler handler = new PartialWriteHandler(500);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/leaky");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        Span span = tracerProvider.get("test").spanBuilder("req").startSpan();
        Context ctx = Context.current().with(span);
        FilterChain chain = (ServletRequest r, ServletResponse w) -> {
            try (var s = ctx.makeCurrent()) {
                try {
                    handler.handle(r, w);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new ServletException(ie);
                }
            }
        };
        try {
            filter.doFilter(req, resp, chain);
        } catch (ServletException ignored) {
        }
        assertEquals(503, resp.getStatus());
        String body = resp.getContentAsString();
        // D-11：同 deadlineExceedReturns503，body 现在与 Go 的 http.Error 一致（带尾部换行）
        assertEquals("Request Timeout\n", body, "no partial business body must leak to client on timeout");
        assertNotNull(body);
        assertTrue(body.length() < 100, "timeout body must be small, no leaked prefix");
    }

    /**
     * 正常路径：handler 立即返回 envelope{code:200}，flushTo 把 captured body 写到 client。
     */
    @Test
    void normalResponseFlushesCapturedBody() throws Exception {
        Filter filter = HttpMiddleware.newHttpMiddleware("svc", Duration.ZERO, Duration.ofMillis(10));
        StringHandler handler = new StringHandler("{\"code\":200,\"msg\":\"ok\",\"data\":null}");

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/ok");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        Span span = tracerProvider.get("test").spanBuilder("req").startSpan();
        Context ctx = Context.current().with(span);
        FilterChain chain = (ServletRequest r, ServletResponse w) -> {
            try (var s = ctx.makeCurrent()) {
                handler.handle(r, w);
            }
        };
        filter.doFilter(req, resp, chain);
        assertEquals(200, resp.getStatus());
        assertTrue(resp.getContentAsString().contains("\"code\":200"), resp.getContentAsString());
    }

    /**
     * H4 回归：客户端断连必须<b>真的</b>把 499 写进响应。
     *
     * <p>Go 出处 {@code observability/http.go:84-88}：
     * <pre>
     * case &lt;-ctx.Done():
     *     statusCode := http.StatusServiceUnavailable
     *     if errors.Is(ctx.Err(), context.Canceled) { statusCode = httpClientClosedRequest }  // 499
     *     MarkTimeout(...)
     *     http.Error(w, httpTimeoutBody, statusCode)
     * </pre>
     * 客户端收到的是 499。
     *
     * <p>修复前的 Java 实现在这条分支上只打了 span 属性就 {@code throw new ServletException(panic)}，
     * 响应从未写出 —— 分支<b>不可达</b>，且原有三个测试全部只断言 503，没有任何一条覆盖 499，
     * 所以这个缺陷一直没被发现。本用例补上那条缺失的断言。
     */
    @Test
    void clientDisconnectReturns499() throws Exception {
        Filter filter = HttpMiddleware.newHttpMiddleware("svc", Duration.ofMillis(500), Duration.ofMillis(10));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/aborted");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        // EOFException 是 isClientCancelledException 认得的客户端断连信号
        FilterChain chain = (r, w) -> {
            throw new java.io.EOFException("client closed connection");
        };

        filter.doFilter(req, resp, chain); // 修复前这里会抛 ServletException

        assertEquals(499, resp.getStatus(),
                "客户端断连必须返回 499（对齐 Go httpClientClosedRequest），且不得抛异常");
        assertTrue(resp.getContentAsString().startsWith("Request Timeout"),
                "499 响应体同样走 http.Error 的 Request Timeout");
    }

    /**
     * H5 回归：超时分支把业务换到 worker 线程后，Spring 的请求上下文必须跟着过去。
     *
     * <p>Go 侧靠显式 ctx 传参跨 goroutine；Java 的 {@code RequestContextHolder} 是线程局部，
     * 不搬运就等于清空 —— 业务里的 {@code @RequestScope} bean、{@code LocaleContextHolder}、
     * 以及将来引入的 {@code SecurityContextHolder} 全部失效，且<b>不报错</b>。
     *
     * <p>修复前 HttpMiddleware 直接 {@code HTTP_EXECUTOR.submit(() -> chain.doFilter(...))}，
     * worker 上 {@code getRequestAttributes()} 恒为 null。
     */
    @Test
    void workerThreadInheritsRequestContext() throws Exception {
        Filter filter = HttpMiddleware.newHttpMiddleware("svc", Duration.ofMillis(200), Duration.ofMillis(10));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/ctx");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        AtomicBoolean sawRequestAttrs = new AtomicBoolean(false);
        AtomicBoolean sawMdc = new AtomicBoolean(false);
        FilterChain chain = (r, w) -> {
            sawRequestAttrs.set(org.springframework.web.context.request.RequestContextHolder
                    .getRequestAttributes() != null);
            sawMdc.set(org.slf4j.MDC.get("h5-marker") != null);
            ((HttpServletResponse) w).setStatus(200);
        };

        org.springframework.web.context.request.RequestContextHolder
                .setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(req));
        org.slf4j.MDC.put("h5-marker", "carried");
        try {
            filter.doFilter(req, resp, chain);
        } finally {
            org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
            org.slf4j.MDC.remove("h5-marker");
        }

        assertTrue(sawRequestAttrs.get(),
                "worker 线程上的 RequestContextHolder 必须非空（@RequestScope / LocaleContextHolder 依赖它）");
        assertTrue(sawMdc.get(), "worker 线程上的 MDC 必须继承容器线程的键值");
    }

    // ===== handler helpers =====

    private static class SleepHandler {
        private final long ms;
        SleepHandler(long ms) { this.ms = ms; }
        void handle(ServletRequest r, ServletResponse w) throws IOException, InterruptedException {
            Thread.sleep(ms);
            w.getWriter().write("ok");
        }
    }

    private static class PartialWriteHandler {
        private final long ms;
        PartialWriteHandler(long ms) { this.ms = ms; }
        void handle(ServletRequest r, ServletResponse w) throws IOException, InterruptedException {
            w.getWriter().write("{\"code\":200,\"msg\":\"partial");
            w.getWriter().flush();
            Thread.sleep(ms);
            w.getWriter().write("\",\"data\":null}");
        }
    }

    private static class StringHandler {
        private final String body;
        StringHandler(String body) { this.body = body; }
        void handle(ServletRequest r, ServletResponse w) throws IOException {
            ((HttpServletResponse) w).setStatus(200);
            ((HttpServletResponse) w).setContentType("application/json");
            w.getWriter().write(body);
        }
    }
}