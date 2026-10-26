package com.rag.favorite.favorite.rpc.fact;

import com.rag.favorite.favorite.rpc.service.FavoriteAuthorityLookupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 🔴🔴 权威读端点的 <b>path 形态与错误响应</b> 双侧对拍 —— 补上
 * {@code docs/DRIFT/favorite/rpc.md} §9.4 末尾那条「⚠️ 本轮未实测 ⇒ 记为 OBS」的未知项。
 *
 * <h2>期望值的来源</h2>
 * <p>🔴 <b>全部期望值来自 Go 真源的实测</b>，不是推断：
 * （Go 的 {@code http.Error} 见 {@code net/http/server.go:2337}，
 * {@code http.NotFound} 见 {@code net/http/server.go:2358}，
 * {@code http.Redirect} 见 {@code net/http/server.go:2403}），
 * {@code tools/authority-path-probe/favorite/} 用 Go 标准库复刻
 * {@code http.ServeMux} + {@code http.Error} / {@code http.NotFound} / {@code http.Redirect}
 * 的行为，把结果落在 {@code expected.txt}。
 * ⇒ <b>Go 侧行为变了 ⇒ 探针的 diff 会失败</b> ⇒ 提醒更新本测试（而不是让 RAG 静默漂移）。
 *
 * <h2>本轮挖出的 5 处漂移（修复前 RAG 侧的实测形态）</h2>
 * <ol>
 *   <li><b>少一段的 path ⇒ RAG 抛 {@code StringIndexOutOfBoundsException}（⇒ HTTP 500）</b>，
 *       Go 是 <b>404</b>。<br>
 *       根因：原判据 {@code rest.indexOf('/') != rest.lastIndexOf('/')} 在「没有 '/'」时
 *       <b>两者都是 -1 ⇒ 判据不成立</b> ⇒ 继续走 ⇒ {@code substring(0, -1)} 抛异常。</li>
 *   <li><b>需要清理的 path（{@code //}、{@code /./}、{@code /../}）⇒ Go 是 301 + Location</b>，
 *       RAG 是 404（没有重定向）。</li>
 *   <li><b>404 的 body 缺失</b>：Go 的 {@code http.NotFound} ⇒ {@code "404 page not found\n"}
 *       + {@code text/plain; charset=utf-8} + {@code nosniff}；RAG 原来<b>只写状态码</b>。</li>
 *   <li><b>401 的 body 少一个换行</b>：Go 的 {@code http.Error} 用 {@code fmt.Fprintln}
 *       ⇒ {@code "unauthorized\n"}；RAG 原来写的是 {@code "unauthorized"}。</li>
 *   <li><b>判定顺序</b>：Go 的 mux 层判定（301 / 404）<b>先于鉴权</b> ⇒
 *       「畸形 path + 无 token」在 Go 是 <b>404</b>，RAG 原来先撞鉴权 ⇒ <b>401</b>。</li>
 * </ol>
 *
 * <h2>为什么这些用例都到不了 DB</h2>
 * <p>12 条用例全部在<b>鉴权或 path 形态</b>处就返回 ⇒ <b>lookup service 一次都不会被调用</b>
 * ⇒ 本类<b>不需要 PG、不需要 Spring 上下文</b> ⇒ 跑得快、且不会因环境问题变绿/变红。
 * ⇒ 末尾用 {@code verifyNoInteractions} 把「没走到业务层」也钉住。
 */
class FavoriteAuthorityPathParityTest {

    private static final String TOKEN = "favorite-authority-test-token-0123456789";
    private static final String PREFIX = "/internal/v1/favorite/facts/";
    private static final String PRODUCER = "rtw.community.favorite";

    private static final String TEXT_PLAIN = "text/plain; charset=utf-8";
    private static final String TEXT_HTML = "text/html; charset=utf-8";

    /**
     * 一条用例：path + Authorization ⇒ 期望的响应形态。
     *
     * @param path     请求路径（不含 query）
     * @param query    query string（{@code null} = 无）
     * @param auth     Authorization 头（{@code null} = 不带）
     * @param status   期望状态码
     * @param location 期望的 Location 头（{@code null} = 无）
     * @param body     期望 body（逐字节）
     */
    private record Case(String path, String query, String auth, int status,
                        String location, String contentType, String nosniff, String body) {
    }

    private static Case muxCase(String path, int status, String location, String contentType,
                                String nosniff, String body) {
        return new Case(path, null, null, status, location, contentType, nosniff, body);
    }

    @Test
    @DisplayName("🔴 权威读端点的 path 形态与错误响应：逐条对齐 Go 真源（12 条）")
    void pathShapesAndErrorResponsesMatchGo() throws Exception {
        String notFoundBody = "404 page not found\n";
        String unauthorizedBody = "unauthorized\n";

        Case[] cases = new Case[]{
                // ── mux 层：path 需要清理 ⇒ 301（**先于鉴权**）──
                muxCase(PREFIX + PRODUCER + "//ev", 301,
                        PREFIX + PRODUCER + "/ev", TEXT_HTML, null,
                        "<a href=\"" + PREFIX + PRODUCER + "/ev\">Moved Permanently</a>.\n\n"),
                muxCase(PREFIX + PRODUCER + "/./ev", 301,
                        PREFIX + PRODUCER + "/ev", TEXT_HTML, null,
                        "<a href=\"" + PREFIX + PRODUCER + "/ev\">Moved Permanently</a>.\n\n"),
                muxCase(PREFIX + PRODUCER + "/x/../ev", 301,
                        PREFIX + PRODUCER + "/ev", TEXT_HTML, null,
                        "<a href=\"" + PREFIX + PRODUCER + "/ev\">Moved Permanently</a>.\n\n"),
                // ⚠️ Go 的 Location 会**二次转义**（% → %25）：这是 Go 的既有行为，照抄。
                muxCase(PREFIX + PRODUCER + "//a%2Fb", 301,
                        PREFIX + PRODUCER + "/a%252Fb", TEXT_HTML, null,
                        "<a href=\"" + PREFIX + PRODUCER + "/a%252Fb\">Moved Permanently</a>.\n\n"),

                // ── mux 层：模式不匹配 ⇒ 404（**先于鉴权**）──
                muxCase(PREFIX + PRODUCER + "/ev/extra", 404, null, TEXT_PLAIN, "nosniff", notFoundBody),
                muxCase(PREFIX + PRODUCER, 404, null, TEXT_PLAIN, "nosniff", notFoundBody),
                muxCase(PREFIX + PRODUCER + "/", 404, null, TEXT_PLAIN, "nosniff", notFoundBody),

                // ── 模式匹配 ⇒ 进 handler ⇒ 鉴权失败 ⇒ 401 ──
                muxCase(PREFIX + PRODUCER + "/ev", 401, null, TEXT_PLAIN, "nosniff", unauthorizedBody),
                muxCase(PREFIX + PRODUCER + "/a%2Fb", 401, null, TEXT_PLAIN, "nosniff", unauthorizedBody),
                muxCase(PREFIX + PRODUCER + "/%2E%2E", 401, null, TEXT_PLAIN, "nosniff", unauthorizedBody),

                // ── handler 层 ──
                new Case(PREFIX + PRODUCER + "/ev", null, "Bearer wrong-token", 401, null,
                        TEXT_PLAIN, "nosniff", unauthorizedBody),
                new Case(PREFIX + PRODUCER + "/ev", "x=1", "Bearer " + TOKEN, 404, null,
                        TEXT_PLAIN, "nosniff", notFoundBody),
        };

        FavoriteAuthorityLookupService lookupService = Mockito.mock(FavoriteAuthorityLookupService.class);
        FavoriteAuthorityEndpointConfig.FavoriteAuthorityServlet servlet =
                new FavoriteAuthorityEndpointConfig.FavoriteAuthorityServlet(
                        TOKEN.getBytes(StandardCharsets.UTF_8), lookupService, false);

        for (Case c : cases) {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.setRequestURI(c.path());
            if (c.query() != null) {
                req.setQueryString(c.query());
            }
            if (c.auth() != null) {
                req.addHeader("Authorization", c.auth());
            }
            MockHttpServletResponse resp = new MockHttpServletResponse();

            servlet.doGet(req, resp);

            assertEquals(c.status(), resp.getStatus(),
                    "状态码不符：path=" + c.path() + " query=" + c.query() + " auth=" + c.auth());
            assertEquals(c.location(), resp.getHeader("Location"),
                    "Location 不符：path=" + c.path());
            assertEquals(c.contentType(), resp.getContentType(),
                    "Content-Type 不符：path=" + c.path());
            assertEquals(c.nosniff(), resp.getHeader("X-Content-Type-Options"),
                    "X-Content-Type-Options 不符：path=" + c.path());
            assertEquals(c.body(), resp.getContentAsString(),
                    "body 不符（**逐字节**）：path=" + c.path() + " query=" + c.query());
        }

        // ⚠️ 12 条用例全部在鉴权 / path 形态处返回 ⇒ 业务层一次都不该被碰到。
        verifyNoInteractions(lookupService);
    }

    @Test
    @DisplayName("🔴 少一段的 path 不得抛异常（修复前是 StringIndexOutOfBoundsException ⇒ 500）")
    void shortPathDoesNotThrow() throws Exception {
        FavoriteAuthorityLookupService lookupService = Mockito.mock(FavoriteAuthorityLookupService.class);
        FavoriteAuthorityEndpointConfig.FavoriteAuthorityServlet servlet =
                new FavoriteAuthorityEndpointConfig.FavoriteAuthorityServlet(
                        TOKEN.getBytes(StandardCharsets.UTF_8), lookupService, false);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI(PREFIX + PRODUCER);     // 少一段
        MockHttpServletResponse resp = new MockHttpServletResponse();

        // ⚠️ 修复前这里会抛 StringIndexOutOfBoundsException（⇒ 容器转成 500）。
        servlet.doGet(req, resp);

        assertEquals(404, resp.getStatus());
        assertNull(resp.getHeader("Location"));
        verifyNoInteractions(lookupService);
    }
}
