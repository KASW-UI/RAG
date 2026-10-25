package com.rag.article.article.api.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * CORS —— Go {@code rest.MustNewServer(c.RestConf, rest.WithCors())} 的等价物。
 *
 * <h2>为什么<b>只有</b> article-api 需要它</h2>
 * <pre>
 *   service/article/api/article.go:33     rest.MustNewServer(c.RestConf, rest.WithCors())   ← 有
 *   service/message/api/messagecenter.go:41   rest.MustNewServer(c.RestConf)             ← 无
 * </pre>
 * ⇒ 这是 article 域的 L1 契约（浏览器端跨域调用），不是全局约定。
 * 刻意放进 article-api 而不是 {@code com.rag.common}：放共享层就得给所有 api 模块
 * 加开关，而 message 域明确<b>不要</b>这个响应头（多一项就多一项差异）。
 *
 * <h2>为什么必须是 Servlet {@code Filter}，不能用 {@code addCorsMappings}</h2>
 * <pre>
 *   rest/server.go:147-152   func WithCors(origin ...string) RunOption {
 *                               server.router = newCorsRouter(server.router, nil, origin...)
 *                           }
 *   rest/server.go:355-357   func (c *corsRouter) ServeHTTP(w, r) {
 *                               c.middleware(c.Router.ServeHTTP)(w, r)      ← 包在 router **外面**
 *                           }
 * </pre>
 * ⇒ 连<b>未注册路径</b>（404）与<b>方法不匹配</b>（405）都先经过这层。
 * 而 {@code WebMvcConfigurer#addCorsMappings} 只对<b>匹配到 handler</b> 的请求生效
 * —— 未注册路径拿不到 handler，CORS 头就丢了。
 *
 * <h2>⚠️ {@code @Order} 的真实作用是「在 Filter 之间排序」</h2>
 * Go 的 {@code corsRouter} 在整条链<b>最外层</b>（Trace / Log / MaxConns / … 之前）。
 * Java 侧 Filter 顺序用 {@code @Order} 表达，取 {@link Ordered#HIGHEST_PRECEDENCE} + 1。
 * <p>⚠️ <b>2026-10-25（第十九轮 · global T-8）：由 {@code HIGHEST_PRECEDENCE} 改为 {@code +1}</b> ——
 * <b>本类必须排在共享层的 {@code GoStyleTraceparentFilter}(+3) 之前</b>，理由是 Go 侧
 * {@code rest/internal/cors/handlers.go:53-67}：{@code cors.Middleware} 对 {@code OPTIONS}
 * <b>短路</b>（{@code :61-63} 直接写 204，<b>不调 next</b>）⇒ 预检响应<b>根本不经过 router</b>
 * ⇒ <b>不经过 TraceHandler</b> ⇒ <b>Go 的 204 预检响应没有 traceparent</b>
 * ⇒ 本类若排在 traceparent filter <b>之后</b> ⇒ 预检响应会<b>多带</b>一个 traceparent ⇒ 与 Go 不一致。
 * ⇒ <b>CORS 仍在整条链最外层</b>（Go 等价性不变），
 * 排在 {@code GoStylePathNormalizingFilter}(+2) / {@code GoStyleTraceparentFilter}(+3) /
 * {@code GoStyleMaxConnsFilter}(+4) / {@code GoStyleCpuSheddingFilter}(+6) /
 * {@code HttpMiddlewareFilter}(+7) / {@code GoStyleMaxBytesFilter}(+8) 之前。
 *
 * <p><b>可观察后果</b>：一个超限（413）或 OPTIONS 预检请求在 Go 与 RAG 上<b>都</b>带 CORS 头。
 * 若把本 Filter 放到 MaxBytes 之后，一个 {@code OPTIONS} 预检会被 MaxBytes 拦下
 * （它也带 Content-Length）而丢掉 CORS 头 —— 前端会报一个很难定位的 CORS 错误。
 *
 * <h2>七个响应头的取值逐条抄自 {@code rest/internal/cors/handlers.go:10-27}</h2>
 * <p>⚠️ {@code Access-Control-Allow-Credentials} 在 {@code setHeader} 里是
 * <b>条件写入</b>的：{@code if origin != allOrigins}。
 * 本模块走的是 {@code WithCors()} 无参分支 ⇒ {@code checkAndSetHeaders} 的
 * {@code len(origins)==0} 分支 ⇒ {@code setHeader(w, "*")}
 * ⇒ <b>origin 就是 {@code "*"}</b> ⇒ <b>不写</b> Allow-Credentials。
 * 照抄「浏览器需要 credentials 就加上」会凭空多出一个响应头，
 * 且带 {@code "*"} 时浏览器本来就会拒绝该组合。
 *
 * <h2>⚠️ {@code OPTIONS} 一律 204，且<b>不进业务链</b></h2>
 * <pre>
 *   rest/internal/cors/handlers.go:61-66
 *       if r.Method == http.MethodOptions { w.WriteHeader(http.StatusNoContent) } else { next(w, r) }
 * </pre>
 * ⇒ {@code OPTIONS /v1/任意路径}（含未注册路径）都是 <b>204 空 body</b>，
 * <b>不是</b> 405 / 404。这条对「前端发 PUT / DELETE 前先预检」是必需的。
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class ArticleGoStyleCorsFilter implements Filter {

    private static final String ALLOW_ORIGIN = "Access-Control-Allow-Origin";
    private static final String ALLOW_METHODS = "Access-Control-Allow-Methods";
    private static final String ALLOW_HEADERS = "Access-Control-Allow-Headers";
    private static final String EXPOSE_HEADERS = "Access-Control-Expose-Headers";
    private static final String MAX_AGE = "Access-Control-Max-Age";
    private static final String VARY = "Vary";

    private static final String ORIGIN_HEADER = "Origin";
    private static final String REQUEST_METHOD_HEADER = "Access-Control-Request-Method";
    private static final String REQUEST_HEADERS_HEADER = "Access-Control-Request-Headers";

    /** go-zero 的常量原值，⚠️ 逐字抄（含空格），不要"整理"成紧凑写法。 */
    private static final String ALL_ORIGINS = "*";
    private static final String METHODS_VALUE = "GET, HEAD, POST, PATCH, PUT, DELETE";
    private static final String ALLOW_HEADERS_VALUE =
            "Content-Type, Origin, X-CSRF-Token, Authorization, AccessToken, Token, Range";
    private static final String EXPOSE_HEADERS_VALUE =
            "Content-Length, Access-Control-Allow-Origin, Access-Control-Allow-Headers";
    private static final String MAX_AGE_VALUE = "86400";

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) resp;

        setVaryHeaders(request, response);
        setCorsHeaders(response);

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            // Go: w.WriteHeader(http.StatusNoContent) —— 只写状态码，不写 body
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * Go {@code rest/internal/cors/handlers.go:117-123}。
     *
     * <p>⚠️ Go 用的是 {@code header.Add}（追加）而非 {@code Set}（覆盖）。
     * 本实现用 {@code addHeader} 对齐，避免与下游已设置的 {@code Vary} 互相覆盖。
     */
    private static void setVaryHeaders(HttpServletRequest request, HttpServletResponse response) {
        response.addHeader(VARY, ORIGIN_HEADER);
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.addHeader(VARY, REQUEST_METHOD_HEADER);
            response.addHeader(VARY, REQUEST_HEADERS_HEADER);
        }
    }

    /**
     * Go {@code checkAndSetHeaders} 的 {@code len(origins)==0} 分支 + {@code setHeader(w, "*")}。
     *
     * <p>⚠️ <b>刻意不写 {@code Access-Control-Allow-Credentials}</b>：见类注释。
     * Go 的 {@code setHeader} 里它是条件写入，{@code "*"} 分支不写。
     */
    private static void setCorsHeaders(HttpServletResponse response) {
        response.setHeader(ALLOW_ORIGIN, ALL_ORIGINS);
        response.setHeader(ALLOW_METHODS, METHODS_VALUE);
        response.setHeader(ALLOW_HEADERS, ALLOW_HEADERS_VALUE);
        response.setHeader(EXPOSE_HEADERS, EXPOSE_HEADERS_VALUE);
        response.setHeader(MAX_AGE, MAX_AGE_VALUE);
    }
}