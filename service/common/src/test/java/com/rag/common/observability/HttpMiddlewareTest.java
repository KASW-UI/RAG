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
        assertEquals("Request Timeout", resp.getContentAsString(), "client must only receive \"Request Timeout\", no leaked business body");
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
        assertEquals("Request Timeout", body, "no partial business body must leak to client on timeout");
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