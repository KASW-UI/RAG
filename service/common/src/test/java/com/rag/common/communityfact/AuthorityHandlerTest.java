package com.rag.common.communityfact;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AuthorityHandler 关键契约：401/404/503/200/中断响应。
 *
 * <p>需要 spring-test 提供的 MockHttpServletRequest / MockHttpServletResponse。
 */
class AuthorityHandlerTest {

    private static final String TOKEN = "test-token-at-least-32-bytes-long";

    static {
        // 避免 logger 报 stdout 噪音
        ch.qos.logback.classic.Logger NOOP = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger(AuthorityHandler.class);
        NOOP.setLevel(ch.qos.logback.classic.Level.OFF);
    }

    @Test
    void rejectsRequestWithoutAuth() throws Exception {
        AuthorityHandler.AuthorityLookup lookup = (ctx, p, e) -> {
            throw new AssertionError("should not be called");
        };
        var servlet = AuthorityHandler.create("rtw.comment-rpc", TOKEN, lookup);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/internal/v1/community/facts");
        req.setQueryString("producer=rtw.comment-rpc&event_id=known");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        servlet.service(req, resp);
        assertEquals(401, resp.getStatus());
    }

    @Test
    void rejectsRequestWithWrongProducer() throws Exception {
        AuthorityHandler.AuthorityLookup lookup = (ctx, p, e) -> {
            throw new AssertionError("should not be called");
        };
        var servlet = AuthorityHandler.create("rtw.comment-rpc", TOKEN, lookup);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/internal/v1/community/facts");
        req.setQueryString("producer=other&event_id=known");
        req.addParameter("producer", "other");
        req.addParameter("event_id", "known");
        req.addHeader("Authorization", "Bearer " + TOKEN);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        servlet.service(req, resp);
        assertEquals(404, resp.getStatus());
    }

    @Test
    void rejectsRequestWithMissingEventId() throws Exception {
        AuthorityHandler.AuthorityLookup lookup = (ctx, p, e) -> {
            throw new AssertionError("should not be called");
        };
        var servlet = AuthorityHandler.create("rtw.comment-rpc", TOKEN, lookup);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/internal/v1/community/facts");
        req.setQueryString("producer=rtw.comment-rpc");
        req.addParameter("producer", "rtw.comment-rpc");
        req.addHeader("Authorization", "Bearer " + TOKEN);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        servlet.service(req, resp);
        assertEquals(404, resp.getStatus());
    }

    @Test
    void mapsLookupUnavailableTo404() throws Exception {
        AuthorityHandler.AuthorityLookup lookup = (ctx, p, e) -> {
            throw new AuthorityHandler.AuthorityUnavailableException("source gone");
        };
        var servlet = AuthorityHandler.create("rtw.comment-rpc", TOKEN, lookup);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/internal/v1/community/facts");
        req.setQueryString("producer=rtw.comment-rpc&event_id=known");
        req.addParameter("producer", "rtw.comment-rpc");
        req.addParameter("event_id", "known");
        req.addHeader("Authorization", "Bearer " + TOKEN);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        servlet.service(req, resp);
        assertEquals(404, resp.getStatus());
    }

    @Test
    void mapsOtherLookupErrorTo503() throws Exception {
        AuthorityHandler.AuthorityLookup lookup = (ctx, p, e) -> {
            throw new IOException("storage unavailable");
        };
        var servlet = AuthorityHandler.create("rtw.comment-rpc", TOKEN, lookup);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/internal/v1/community/facts");
        req.setQueryString("producer=rtw.comment-rpc&event_id=known");
        req.addParameter("producer", "rtw.comment-rpc");
        req.addParameter("event_id", "known");
        req.addHeader("Authorization", "Bearer " + TOKEN);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        servlet.service(req, resp);
        assertEquals(503, resp.getStatus());
    }

    @Test
    void returns200WithBodyOnSuccess() throws Exception {
        Wire.AuthorityFact fact = new Wire.AuthorityFact();
        Wire.Event ev = new Wire.Event();
        ev.eventId = "known";
        ev.producer = "rtw.comment-rpc";
        fact.event = ev;
        AuthorityHandler.AuthorityLookup lookup = (ctx, p, e) -> fact;
        var servlet = AuthorityHandler.create("rtw.comment-rpc", TOKEN, lookup);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/internal/v1/community/facts");
        req.setQueryString("producer=rtw.comment-rpc&event_id=known");
        req.addParameter("producer", "rtw.comment-rpc");
        req.addParameter("event_id", "known");
        req.addHeader("Authorization", "Bearer " + TOKEN);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        servlet.service(req, resp);
        assertEquals(200, resp.getStatus());
        assertEquals("application/json", resp.getContentType());
        // 校验 body 含 event_id（默认 Jackson 序列化包含所有字段包括 null）
        assertTrue(resp.getContentAsString().contains("\"event_id\":\"known\""), resp.getContentAsString());
        assertEquals("no-store", resp.getHeader("Cache-Control"));
    }

    @Test
    void handlesInterruptedResponseGracefully() throws Exception {
        Wire.AuthorityFact fact = new Wire.AuthorityFact();
        Wire.Event ev = new Wire.Event();
        ev.eventId = "known";
        ev.producer = "rtw.comment-rpc";
        fact.event = ev;
        AuthorityHandler.AuthorityLookup lookup = (ctx, p, e) -> fact;
        var servlet = AuthorityHandler.create("rtw.comment-rpc", TOKEN, lookup);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/internal/v1/community/facts");
        req.setQueryString("producer=rtw.comment-rpc&event_id=known");
        req.addHeader("Authorization", "Bearer " + TOKEN);

        // 构造一个 write 抛 IOException 的 response
        HttpServletResponse failing = new FailingResponse();
        servlet.service(req, failing);
        // 不抛 = 通过；DRIFT-033 修复路径
    }

    @Test
    void createValidatesArguments() {
        AuthorityHandler.AuthorityLookup lookup = (ctx, p, e) -> null;
        assertThrowsIAE("null producer", () -> AuthorityHandler.create(null, TOKEN, lookup));
        assertThrowsIAE("null token", () -> AuthorityHandler.create("p", null, lookup));
        assertThrowsIAE("short token", () -> AuthorityHandler.create("p", "short", lookup));
        assertThrowsIAE("null lookup", () -> AuthorityHandler.create("p", TOKEN, null));
    }

    /**
     * DRIFT-013 修复验证：多个 Authorization header 必须返回 401（对齐 Go 版
     * {@code r.Header.Values("Authorization")} len 必须 == 1）。
     */
    @Test
    void rejectsRequestWithMultipleAuthorizationHeaders() throws Exception {
        AuthorityHandler.AuthorityLookup lookup = (ctx, p, e) -> {
            throw new AssertionError("should not be called");
        };
        var servlet = AuthorityHandler.create("rtw.comment-rpc", TOKEN, lookup);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/internal/v1/community/facts");
        req.setQueryString("producer=rtw.comment-rpc&event_id=known");
        req.addParameter("producer", "rtw.comment-rpc");
        req.addParameter("event_id", "known");
        // 故意发两个 Authorization header（模拟异常客户端或攻击尝试）
        req.addHeader("Authorization", "Bearer " + TOKEN);
        req.addHeader("Authorization", "Bearer " + TOKEN + "tampered");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        servlet.service(req, resp);
        assertEquals(401, resp.getStatus());
    }

    private static void assertThrowsIAE(String label, Runnable r) {
        try { r.run(); throw new AssertionError(label + " should throw"); }
        catch (IllegalArgumentException ignored) { /* ok */ }
    }

    /** response 写时报"connection closed"，验证 DRIFT-033 修复。 */
    private static class FailingResponse extends MockHttpServletResponse {
        @Override
        public PrintWriter getWriter() {
            return new PrintWriter(new java.io.OutputStream() {
                @Override public void write(int b) throws IOException { throw new IOException("connection closed"); }
            });
        }
    }
}