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
 * 🔴 DRIFT-002（已修）：<b>JSON body 超过 8 MiB ⇒ 400</b>。
 *
 * <h2>Go 侧证据（这是一个<b>框架常量</b>，不在任何 yaml / tag 里）</h2>
 * <pre>
 *   rest/httpx/requests.go:20-21
 *       maxMemory  = 32 &lt;&lt; 20 // 32MB
 *       maxBodyLen = 8  &lt;&lt; 20 // 8MB        ← 行为定义，但**不可配置**
 *
 *   rest/httpx/requests.go:117-124
 *       func ParseJsonBody(r *http.Request, v any) error {
 *           if withJsonBody(r) {
 *               reader := io.LimitReader(r.Body, maxBodyLen)   // ← 静默截断，不是显式拒绝
 *               return mapping.UnmarshalJsonReader(reader, v)
 *           }
 *           return mapping.UnmarshalJsonMap(nil, v)
 *       }
 * </pre>
 *
 * ⇒ 任何 &gt; 8 MiB 的 JSON body 被 {@code LimitReader} <b>静默截断在 8 MiB</b>，
 * 随后 JSON 解析器拿到半个文档 ⇒ {@code unexpected EOF}
 * ⇒ handler 的 {@code httpx.ErrorCtx} ⇒ <b>400 + text/plain + nosniff</b>，
 * <b>永远不会调用 article-rpc</b>。
 *
 * <h3>为什么这条容易漏（AGENTS.md §3.1「否定式断言 / 表面配置对法」）</h3>
 * <p>{@code maxBodyLen} 既不是 {@code RestConf} 的字段，也不在任何 {@code json:",default=…"} tag 里，
 * <b>逐字段对 yaml 永远看不见它</b>。而 {@code MaxBytes} 是 <b>10 MiB</b>
 * （article-api.yaml 显式配置 + {@code routes.go} 的 {@code rest.WithMaxBytes}），
 * 两者<b>不是同一个东西</b>：
 * <pre>
 *   &lt; 8 MiB         两侧都解析成功（行为一致）
 *   8 – 10 MiB       Go 恒 400（截断）；Java 上游实现恒进业务   ← 这条 2 MiB 宽的带
 *   &gt; 10 MiB        两侧都被 MaxBytes 拦成裸 413
 * </pre>
 * ⇒ 上游实现只对齐了 {@code MaxBytes}，漏了这层，产出 DRIFT-002。
 *
 * <h3>为什么是「显式拒绝」而不是复刻「静默截断」</h3>
 * <p>两者的<b>可观察契约完全相同</b>：HTTP 400 + text/plain + nosniff + 业务逻辑不执行。
 * 差别只在 body 文案（Go 那句来自被截断后的解析器，不是任何自定义消息）。
 * 显式拒绝避免了「把 servlet 输入流截断到与 Content-Length 不符」这类容器层面的怪异行为，
 * 且文案差异已按 OBSERVABLE DIFFERENCE（低危）登记。
 *
 * <h3>触发条件与 Go 的 {@code withJsonBody} 逐条一致</h3>
 * <pre>
 *   httpx/requests.go:153-155
 *       func withJsonBody(r *http.Request) bool {
 *           return r.ContentLength &gt; 0 &amp;&amp; strings.Contains(r.Header.Get(ContentType), ApplicationJson)
 *       }
 * </pre>
 * ⇒ 只对「有 Content-Length 且为正」+「Content-Type 含 application/json」的请求生效。
 * <b>chunked（无 Content-Length）在 Go 侧根本不会读 body</b>，
 * Java 侧由 {@code GoStyleJsonBodyConverter.goReadsBody} 已经同样处理（返回 null → 零值 DTO）。
 *
 * <h3>{@code @Order} 的真实作用是「在 Filter 之间排序」</h3>
 * 必须<b>在 {@code GoStyleMaxBytesFilter}(+6) 之后</b>：先让 10 MiB 的闸门按 Go 的链序
 * （MaxBytes 在本层<b>之外</b>）拒绝，超出 MaxBytes 的请求得到<b>裸 413</b>而不是 400。
 * 取 {@code HIGHEST_PRECEDENCE + 7}。
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Order(Ordered.HIGHEST_PRECEDENCE + 7)
public class GoStyleJsonBodyLimitFilter implements Filter {

    /**
     * Go {@code rest/httpx/requests.go:21} 的 {@code maxBodyLen = 8 << 20}。
     * <b>不可配置</b>（Go 侧它是常量），故这里也不做成 property ——
     * 做成可配置等于凭空多出 Go 没有的开关。
     */
    private static final long MAX_JSON_BODY_BYTES = 8L * 1024 * 1024;

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) resp;

        if (goReadsJsonBody(request) && request.getContentLengthLong() > MAX_JSON_BODY_BYTES) {
            // 与 Go 的 http.Error(400) 同形：text/plain + nosniff + 文案 + "\n"
            writeGoStyleBadRequest(response, "request entity too large");
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * 直接写 Go {@code http.Error} 的形状。
     *
     * <p>⚠️ Content-Type 用 {@link HttpServletResponse#setHeader} 而<b>不是</b>
     * {@link HttpServletResponse#setContentType}：后者会被 Tomcat 规范化并<b>重写</b>成
     * {@code text/plain;charset=utf-8}（分号后无空格），而 Go 写的是
     * {@code text/plain; charset=utf-8}（有空格）。这是 DRIFT-005。
     * <p>⚠️ 头必须在 {@code setStatus}（提交响应）<b>之前</b>写完，否则被静默忽略。
     */
    private static void writeGoStyleBadRequest(HttpServletResponse response, String message) throws IOException {
        response.setHeader("Content-Type", "text/plain; charset=utf-8");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.getOutputStream().write((message + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** Go {@code httpx.ParseJsonBody} 的 {@code withJsonBody} 判据，逐条对齐。 */
    private static boolean goReadsJsonBody(HttpServletRequest request) {
        long contentLength = request.getContentLengthLong();
        String contentType = request.getContentType();
        return contentLength > 0L
                && contentType != null
                && contentType.contains("application/json");
    }
}