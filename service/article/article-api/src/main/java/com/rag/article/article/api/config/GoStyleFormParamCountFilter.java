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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 🔴 query 参数总数上限 —— 对应 go-zero {@code rest/httpx/util.go} 的 {@code maxFormParamCount}。
 *
 * <h2>Go 出处（逐行，消费点）</h2>
 * <pre>
 * go-zero@v1.10.2/rest/httpx/util.go:11-17
 *   const (
 *       // most servers and clients have a limit of 8192 bytes (8 KB)
 *       // one parameter at least take 4 chars, for example `?a=b&amp;c=d`
 *       maxFormParamCount = 2048
 *   )
 *
 * go-zero@v1.10.2/rest/httpx/util.go:22-64  GetFormValues
 *   for name, values := range r.Form {
 *       for _, v := range values {
 *           if len(v) == 0 { continue }              // ← 空值不计数
 *           if n &lt; maxFormParamCount { filtered = append(filtered, v); n++ }
 *           else { return nil, fmt.Errorf("too many form values, error: %s", r.Form.Encode()) }
 *       }
 *   }
 * </pre>
 *
 * <p><b>调用链</b>：Go handler 里的 {@code httpx.Parse(r, &amp;req)}
 * （{@code service/article/api/internal/handler/article/*.go:21}）
 * → {@code httpx.ParseForm} → {@code httpx.GetFormValues} ⇒ 上面那个上限生效。
 * <br>⇒ 超限时 {@code GetFormValues} 返回 error → handler 调 {@code httpx.ErrorCtx}
 * → {@code httpx/responses.go:142-159} 的 {@code doHandleError}：
 * 本模块未注册 {@code SetErrorHandler} ⇒ {@code handler == nil} ⇒
 * 该 error <b>不是</b> gRPC error ⇒ 走 {@code else} 分支 ⇒
 * {@code http.Error(w, err.Error(), http.StatusBadRequest)}
 * ⇒ <b>HTTP 400</b>，body = {@code "too many form values, error: " + r.Form.Encode()} + {@code "\n"}。
 *
 * <h2>⚠️ 为什么这条不是「无害的防御性增强」</h2>
 * 它在 Go 侧是一条 <b>DoS 闸门</b>（注释明说是为了 8 KB query 上限）。
 * 没有它 ⇒ 客户端可以发任意多个 query 参数，服务端<b>全部解析</b>并绑定。
 * <br>⚠️ Tomcat 自己也有 {@code maxParameterCount}（默认 10000），
 * 但那是<b>另一个阈值</b> ⇒ 在 <b>2049 ~ 10000</b> 这段区间里两侧行为不同。
 *
 * <h2>🔴 真容器实测：默认配置下这条闸门<b>不可达</b>（2026-10-24）</h2>
 * Tomcat 自己有请求行/请求头上限（Boot 4 的 {@code server.max-http-request-header-size}，默认 <b>8 KB</b>），
 * 而 2049 个<b>最短</b>的非空 query 值（{@code a=1&} × 2049 ≈ 8196 字节）<b>本身就超了</b>
 * ⇒ Tomcat 在<b>连接器层</b>就以 <b>400</b> 拒掉，请求<b>根本到不了</b>本 filter
 * （实测 body 是 Tomcat 的 <b>HTML 错误页</b>，不是 Go 的 text/plain）。
 * <br>⇒ 所以本 filter 的价值是：<b>让上限不依赖容器配置</b> ——
 * 部署把 {@code server.max-http-request-header-size} 调高（长 header 场景常见）时，
 * RAG 仍然按 Go 的 2048 判据拒绝，<b>且给出 Go 逐字相同的 body</b>（实测已验证）。
 * <br>⚠️ Boot 4 里属性名是 {@code server.max-http-request-header-size}；
 * 旧的 {@code server.max-http-header-size} <b>已不存在</b> ⇒ 写它会被<b>静默忽略</b>
 * （实测踩过：写了它，请求仍被 Tomcat 以 400 拒掉，闸门根本没执行）。
 *
 * <h2>⚠️ 为什么只统计 <b>query string</b>，不碰 body</h2>
 * Go 侧 {@code r.Form} 是「query + body（仅 urlencoded / multipart 字段）」的合并结果。
 * 本模块的端点逐个核对下来：
 * <ul>
 *   <li>GET 端点（{@code /v1/article/{id}}、{@code /v1/articles}）⇒ Go 的 {@code r.Form} 就是 query；</li>
 *   <li>POST/PUT 端点用 {@code application/json} ⇒ Go 的 {@code r.PostForm} 为空 ⇒ 只有 query；</li>
 *   <li>唯一 multipart 端点（{@code /v1/upload}）只有一个文件 part ⇒ 没有表单字段 ⇒ 只有 query。</li>
 * </ul>
 * ⇒ <b>本模块内「统计 query string」与 Go 等价</b>。
 * <br>⚠️ 而且<b>不能</b>用 {@code request.getParameterMap()}：它会让容器提前解析
 * multipart body（{@code parseParts}），改变 body 的消费时机 ⇒ 走 query string 更安全。
 *
 * <h2>⚠️ 相邻但<b>未</b>覆盖的 Go 行为（已登记在 DRIFT 台账）</h2>
 * <ul>
 *   <li>{@code net/url} 自己的 {@code defaultMaxParams = 10000}（{@code url.go:1001}）——
 *       超限时 Go 返 400「number of URL query parameters exceeded limit」；
 *       Tomcat 的 {@code maxParameterCount} 默认也是 10000，但判据不同（数"参数"vs 数"分段"）。</li>
 *   <li>query 里出现 {@code ;} ⇒ Go 报「invalid semicolon separator in query」并返 400
 *       （{@code url.go:1026-1029}）；本 filter 按 Go 的分段规则<b>跳过</b>该段，不返 400。</li>
 *   <li>query 里出现非法 {@code %} 转义 ⇒ Go 返 400（{@code url.go:1034-1043}）；
 *       本 filter 按 Go 的分段规则<b>跳过</b>该段，不返 400。</li>
 * </ul>
 * ⇒ 这三条是<b>相邻</b>差异，不在本条的范围内；本条只对齐<b>2048</b> 这一条。
 *
 * <h2>{@code @Order} 为什么取 {@code HIGHEST_PRECEDENCE + 8}</h2>
 * Go 侧这个上限在<b>路由 handler 内</b>（{@code httpx.Parse}），
 * 即<b>所有中间件之后</b> ⇒ Java 侧要放在等价于中间件的 filter <b>之后</b>。
 * <br>现有 filter 序（已实测钉住）：CORS(0) → path.Clean(0) → MaxConns(+1) → Breaker(+2)
 * → CpuShedding(+3) → observability(+5) → MaxBytes(+6) → JsonBody(+7)。
 * ⇒ 本 filter 取 <b>+8</b>，紧贴 controller 之前，是最接近 Go 位置的一档。
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Order(Ordered.HIGHEST_PRECEDENCE + 8)
public class GoStyleFormParamCountFilter implements Filter {

    /**
     * Go {@code rest/httpx/util.go:15} 的 {@code maxFormParamCount}，<b>不可配置</b>
     * （Go 侧它是常量 ⇒ 做成 property 等于凭空多出 Go 没有的开关）。
     */
    private static final int MAX_FORM_PARAM_COUNT = 2048;

    /** Go {@code url.go} 的 {@code upperhex}（大写十六进制）。 */
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) resp;

        String query = request.getQueryString();
        if (query != null && !query.isEmpty()) {
            Map<String, List<String>> form = parseGoQuery(query);
            int nonEmpty = countNonEmptyValues(form);
            if (nonEmpty > MAX_FORM_PARAM_COUNT) {
                writeGoStyleBadRequest(response,
                        "too many form values, error: " + encodeGoValues(form));
                return;
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * Go {@code net/url.parseQuery}（{@code url.go:1019-1054}）的分段规则。
     *
     * <p>逐条对齐：
     * <ul>
     *   <li>按 {@code &} 切分（Go 1.17+ <b>不</b>把 {@code ;} 当分隔符）；</li>
     *   <li>段内含 {@code ;} ⇒ Go 记错误并<b>跳过该段</b>（{@code url.go:1026-1029}）；</li>
     *   <li>key 为空 ⇒ 跳过（{@code url.go:1030-1032}）；</li>
     *   <li>按<b>第一个</b> {@code =} 切 key/value（{@code url.go:1033}）；</li>
     *   <li>key 与 value 各自 {@code QueryUnescape}（{@code +} ⇒ 空格，{@code %XX} ⇒ 字节）；</li>
     *   <li>解码失败 ⇒ Go 跳过该段（{@code url.go:1034-1045}）。</li>
     * </ul>
     */
    private static Map<String, List<String>> parseGoQuery(String query) {
        Map<String, List<String>> form = new LinkedHashMap<>();
        int start = 0;
        while (start <= query.length()) {
            int amp = query.indexOf('&', start);
            String part = (amp < 0) ? query.substring(start) : query.substring(start, amp);
            start = (amp < 0) ? query.length() + 1 : amp + 1;

            if (part.indexOf(';') >= 0) {
                continue;                       // Go: invalid semicolon separator ⇒ 跳过该段
            }
            if (part.isEmpty()) {
                continue;                       // Go: key == "" ⇒ 跳过
            }
            int eq = part.indexOf('=');
            String rawKey = (eq < 0) ? part : part.substring(0, eq);
            String rawValue = (eq < 0) ? "" : part.substring(eq + 1);

            String key = queryUnescape(rawKey);
            String value = queryUnescape(rawValue);
            if (key == null || value == null) {
                continue;                       // Go: 解码失败 ⇒ 跳过该段
            }
            form.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }
        return form;
    }

    /** Go {@code GetFormValues} 的 {@code if len(v) == 0 { continue }} —— <b>空值不计数</b>。 */
    private static int countNonEmptyValues(Map<String, List<String>> form) {
        int n = 0;
        for (List<String> values : form.values()) {
            for (String v : values) {
                if (!v.isEmpty()) {
                    n++;
                }
            }
        }
        return n;
    }

    /**
     * Go {@code net/url.Values.Encode}（{@code url.go:1055-1073}）：<b>按 key 排序</b>后逐个转义。
     *
     * <p>⚠️ 排序用<b>码点序</b>（= UTF-8 字节序），不是 {@link String#compareTo}
     * （UTF-16 码元序）—— 与 {@code DRIFT-ARTICLE-API-007} 同一个坑。
     */
    private static String encodeGoValues(Map<String, List<String>> form) {
        List<String> keys = new ArrayList<>(form.keySet());
        keys.sort(GoStyleFormParamCountFilter::compareByCodePoint);
        StringBuilder buf = new StringBuilder();
        for (String k : keys) {
            String keyEscaped = queryEscape(k);
            for (String v : form.get(k)) {
                if (buf.length() > 0) {
                    buf.append('&');
                }
                buf.append(keyEscaped).append('=').append(queryEscape(v));
            }
        }
        return buf.toString();
    }

    /**
     * Go {@code net/url.QueryEscape}（{@code url.go:282-284} → {@code shouldEscape} 的
     * {@code encodeQueryComponent} 分支，{@code url.go:158-161}）。
     *
     * <p>规则：{@code A-Z a-z 0-9 - _ . ~} <b>不转义</b>；空格 ⇒ {@code +}；其余 ⇒ {@code %XX}（<b>大写</b>）。
     * <br>⚠️ 不能用 {@code java.net.URLEncoder}：它保留 {@code *}、把 {@code ~} 转成 {@code %7E} ⇒ 与 Go 不同。
     */
    private static String queryEscape(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '~') {
                out.append((char) c);
            } else if (c == ' ') {
                out.append('+');
            } else {
                out.append('%').append(HEX[(c >> 4) & 0xF]).append(HEX[c & 0xF]);
            }
        }
        return out.toString();
    }

    /**
     * Go {@code net/url.QueryUnescape}（{@code url.go:282-284} → {@code unescape}）。
     *
     * <p>⚠️ 按<b>字节</b>解码（不是 char）：{@code %E4%B8%AD} 是 3 个字节的「中」，
     * 若按 char 解会得到 3 个 U+00E4/U+00B8/U+00AD ⇒ 再编成 UTF-8 就是 6 个字节 ⇒ 与 Go 不同。
     * <br>返回 {@code null} 表示解码失败（Go 侧是 error ⇒ 跳过该段）。
     */
    private static String queryUnescape(String s) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '%') {
                if (i + 2 >= s.length()) {
                    return null;
                }
                int hi = hexValue(s.charAt(i + 1));
                int lo = hexValue(s.charAt(i + 2));
                if (hi < 0 || lo < 0) {
                    return null;
                }
                out.write((hi << 4) | lo);
                i += 3;
            } else if (c == '+') {
                out.write(' ');
                i++;
            } else {
                // Go 按字节处理 ⇒ 非 ASCII 原样写成 UTF-8 字节
                byte[] encoded = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
                out.write(encoded, 0, encoded.length);
                i++;
            }
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static int hexValue(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        }
        return -1;
    }

    /** 码点序（= UTF-8 字节序），与 Go 的字符串比较一致。 */
    private static int compareByCodePoint(String a, String b) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            int ca = a.codePointAt(i);
            int cb = b.codePointAt(j);
            if (ca != cb) {
                return Integer.compare(ca, cb);
            }
            i += Character.charCount(ca);
            j += Character.charCount(cb);
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }

    /**
     * 直接写 Go {@code http.Error} 的形状（{@code net/http/server.go}）。
     *
     * <p>⚠️ 与 {@code GoStyleJsonBodyLimitFilter} 里那份是<b>同一个形状</b>，
     * 但<b>刻意不抽到共享层</b>：{@code service/common} 被 16 个组件依赖，
     * 与其它域的工作树并发修改会冲突（AGENTS.md §5.3）。
     */
    private static void writeGoStyleBadRequest(HttpServletResponse response, String message)
            throws IOException {
        response.setHeader("Content-Type", "text/plain; charset=utf-8");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.getOutputStream().write((message + "\n").getBytes(StandardCharsets.UTF_8));
    }
}
