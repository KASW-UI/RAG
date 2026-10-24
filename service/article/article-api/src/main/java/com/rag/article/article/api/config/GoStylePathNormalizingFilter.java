package com.rag.article.article.api.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 🔴 DRIFT-004（已修）：<b>路径规范化对齐 Go 的 {@code path.Clean}</b>。
 *
 * <h2>Go 侧证据（注册与匹配<b>两侧</b>都做）</h2>
 * <pre>
 *   rest/router/patrouter.go:48   cleanPath := path.Clean(reqPath)      // 注册时
 *   rest/router/patrouter.go:60   reqPath  := path.Clean(r.URL.Path)    // 匹配时
 * </pre>
 *
 * ⇒ Go 的路由表里存的是<b>已 Clean 的模板</b>，匹配时也把请求路径 Clean 一次。
 * 可观察后果（实测）：
 * <pre>
 *   GET  /v1/articles/     → 200（尾斜杠被去掉）
 *   GET  /v1//articles    → 200（重复斜杠被合并）
 *   GET  /v1/articles/../articles → 200（.. 被解析）
 * </pre>
 *
 * <h3>为什么 Spring 侧做不到「配一下就行」</h3>
 * <p>Spring Framework 7 的 {@code PathMatchConfigurer} <b>已经没有</b>
 * {@code setUseTrailingSlashMatch}（实测该方法不存在），
 * {@code PathPatternParser.setPathOptions} 的 {@code PathContainer.Options}
 * 只有 {@code urlDecode} 与 {@code removeSemicolonContent}，<b>没有尾斜杠开关</b>。
 * ⇒ 只能自己把请求路径规范化后再交给 DispatcherServlet。
 *
 * <h3>为什么在 Filter 层做（而不是 HandlerInterceptor）</h3>
 * <p>路由匹配发生在 DispatcherServlet <b>内部</b>，Interceptor 跑在匹配<b>之后</b>，
 * 那里再改路径已经来不及（会变成 404）。Filter 在 DispatcherServlet 之外，
 * 是唯一能影响匹配结果的位置。
 * 这与 {@link GoStyleJsonBodyLimitFilter} / CORS 是同一类原因。
 *
 * <h3>⚠️ 与 {@code JwtAuthInterceptor} 的一致性</h3>
 * <p>拦截器用 {@code getRequestURI()} 匹配受保护路由表。本 Filter 覆写的正是
 * {@code getRequestURI}，所以鉴权看到的是<b>同一个规范化后</b>的路径 ——
 * 与 Go 侧「router 先 Clean、再决定是否进鉴权链」的顺序一致。
 * 若两者不一致，会出现「路由匹配到了但不鉴权」或「鉴权了但路由没匹配」这类错位。
 *
 * <h3>⚠️ {@code @Order}：必须在<b>所有</b>会读路径的 Filter 之前</h3>
 * 取 {@link Ordered#HIGHEST_PRECEDENCE}（与 CORS 同级，靠 bean 名稳定顺序）。
 * 本 Filter <b>不改</b>任何其它行为，只改「路径长什么样」。
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GoStylePathNormalizingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        String uri = request.getRequestURI();
        String cleaned = goPathClean(uri);
        if (cleaned.equals(uri)) {
            //绝大多数请求不需要包装，省一次对象分配
            chain.doFilter(request, resp);
            return;
        }
        chain.doFilter(new CleanedPathRequest(request, cleaned), resp);
    }

    /**
     * Go {@code path.Clean} 的逐字复刻（{@code path/clean.go}）。
     *
     * <pre>
     *   func Clean(path string) string {
     *       if path == "" { return "." }
     *       rooted := path[0] == '/'
     *       n := len(path)
     *       out := lazybuf{s: path}
     *       r, dotdot := 0, 0
     *       if rooted { out.append('/'); r, dotdot = 1, 1 }
     *       for r &lt; n {
     *           switch {
     *           case path[r] == '/':            r++                    // 重复斜杠
     *           case path[r] == '.' &amp;&amp; (r+1 == n || path[r+1] == '/'):  r++        // "." 段
     *           case path[r] == '.' &amp;&amp; path[r+1] == '.' &amp;&amp; (r+2 == n || path[r+2] == '/'):
     *               r += 2
     *               switch {
     *               case out.w &gt; dotdot:  out.w--; out.pop()
     *               case !rooted:           out.append('.')
     *               }
     *               dotdot = out.w
     *           default:
     *               if rooted &amp;&amp; out.w != 1 || !rooted &amp;&amp; out.w != 0 { out.append('/') }
     *               for ; r &lt; n &amp;&amp; path[r] != '/'; r++ { out.append(path[r]) }
     *           }
     *       }
     *       if out.w == 0 { return "." }
     *       return out.string()
     *   }
     * </pre>
     *
     * <p>用 {@link Deque} 而不是可变 buffer，便于对照上面 Go 源码逐句读。
     */
    static String goPathClean(String path) {
        if (path == null || path.isEmpty()) {
            return ".";
        }
        boolean rooted = path.charAt(0) == '/';
        int n = path.length();
        Deque<String> out = new ArrayDeque<>();
        int dotdot = 0;
        if (rooted) {
            out.addLast("");
            dotdot = 1;
        }
        for (int r = 0; r < n; ) {
            char c = path.charAt(r);
            if (c == '/') {
                r++;
            } else if (c == '.' && (r + 1 == n || path.charAt(r + 1) == '/')) {
                // "." 段，直接丢弃
                r++;
            } else if (c == '.' && r + 1 < n && path.charAt(r + 1) == '.'
                    && (r + 2 == n || path.charAt(r + 2) == '/')) {
                // ".." 段：回退一段
                r += 2;
                if (out.size() > dotdot) {
                    out.removeLast();
                } else if (!rooted) {
                    out.addLast(".");
                }
                dotdot = out.size();
            } else {
                int start = r;
                while (r < n && path.charAt(r) != '/') {
                    r++;
                }
                out.addLast(path.substring(start, r));
            }
        }
        if (out.isEmpty()) {
            return ".";
        }
        return String.join("/", out);
    }

    /**
     * 只覆写「路径」相关的三个 getter。
     *
     * <p>⚠️ <b>不覆写</b> {@code getRequestURL()}：Go 的 {@code r.URL.Path} 只影响路由匹配，
     * 访问日志 / 审计若要看到<b>原始</b> URL 应当读原始值。
     * 本 Filter 的目的是「让路由匹配与 Go 一致」，不是「篡改请求」。
     */
    private static final class CleanedPathRequest extends HttpServletRequestWrapper {

        private final String cleanedUri;

        CleanedPathRequest(HttpServletRequest request, String cleanedUri) {
            super(request);
            this.cleanedUri = cleanedUri;
        }

        @Override
        public String getRequestURI() {
            return cleanedUri;
        }

        @Override
        public String getServletPath() {
            return cleanedUri;
        }

        @Override
        public String getPathInfo() {
            return null;
        }
    }
}