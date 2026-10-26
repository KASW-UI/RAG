package com.rag.common.http;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.catalina.connector.Connector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>守门（raw 模式）：query 参数的判定权必须回到应用层，并按 Go 的语义判。</b>
 *
 * <h3>要守的三条机制（都不是 mock 能测的，§3.3）</h3>
 * <ol>
 *   <li><b>Tomcat 的容器上限必须被抬到 10001</b>：{@code Connector.setMaxParameterCount} 只有
 *       在<b>真容器</b>里才有意义；MockMvc 根本不经过 Connector。</li>
 *   <li><b>应用层必须按 Go 的语义判</b>：Go 数<b>非空值</b>（{@code httpx/util.go:44-45}），
 *       Tomcat 数<b>参数总数</b>（{@code Parameters.addParameter} 每个都计数，含空值）
 *       ⇒ 两者在「有空值参数」时判据不同。</li>
 *   <li><b>闸门必须真的被注册</b>：本类走的是<b>真 Spring MVC 拦截器链</b>
 *       （{@code GoStyleFormLimitWebMvcConfig} ⇒ {@code InterceptorRegistry}）
 *       ⇒ 「Bean 存在」不等于「进了拦截器链」（§3.3）。</li>
 * </ol>
 *
 * <h3>Go 出处（真源）</h3>
 * <pre>
 * go-zero@v1.10.2/rest/httpx/util.go:15      maxFormParamCount = 2048
 * go-zero@v1.10.2/rest/httpx/util.go:44-45   if len(v) == 0 { continue }      // ← 空值不计数
 * go-zero@v1.10.2/rest/httpx/util.go:48      if n &lt; maxFormParamCount { … } else { return nil, fmt.Errorf("too many form values, error: %s", r.Form.Encode()) }
 * Go 1.25 net/url/url.go:1001                defaultMaxParams = 10000（query 段数上限）
 *
 * ⇒ Go 的拒绝形状（**没注册 SetErrorHandler 的域**：article / user / task / favorite）：
 *   HTTP 400 + Content-Type「text/plain; charset=utf-8」+ nosniff
 *   + body = "too many form values, error: " + r.Form.Encode() + "\n"
 * </pre>
 *
 * <h3>🔴 这条守门对应的漂移（{@code DRIFT-HOTAPI-030} / global X-6）</h3>
 * <p><b>现状</b>（Tomcat 上限 = 模块 yaml 里的 2048）：请求带<b>空值</b>参数时
 * （例：{@code ?a&a&a…} 共 3000 个）⇒ <b>总数 3000 ≥ 2048</b> ⇒ Tomcat 先拒 ⇒ <b>400 + HTML</b>；
 * 而 Go 数非空值 ⇒ <b>0 个非空值</b> ⇒ Go <b>放行</b> ⇒ <b>两侧行为不一致</b>。
 * <br>⇒ <b>守门点</b>：{@code 3000 个空值参数 ⇒ 必须 200}（不是 400）。
 * <br>⚠️ <b>前提</b>：应用真的读了 query 参数（{@code getParameter*}）⇒ Tomcat 才会解析并计数
 * ⇒ 所以本测试的 controller <b>必须</b>调用 {@code getParameter("page")}，否则这条用例会<b>假绿</b>。
 */
@SpringBootTest(
        classes = GoStyleParamLimitGateTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "rag.http.go-form-limit.mode=raw",
                // ⚠️ 与各 api 模块的 yaml 保持一致：Go 的真实上限是 1 MiB + 4 KiB。
                //    不配的话（Boot 默认 8KB）10001 段的请求行（≈40KB）会在连接器层就被拒 ⇒ 测不到应用层。
                "server.max-http-request-header-size=1028KB"
        })
@DisplayName("🔴 Go 风格 form 值上限闸门（raw 模式）")
class GoStyleParamLimitGateTest {

    @LocalServerPort
    private int port;

    @Autowired
    private org.springframework.boot.web.server.context.WebServerApplicationContext webServerContext;

    private static final String PATH_PROBE = "/probe";

    // ==================================================================
    // ① 容器层：运行期的真实上限
    // ==================================================================

    @Test
    @DisplayName("🔴 运行期 connector 的参数上限 = 10001（= Go 的段数上限 +1），不是模块 yaml 里的 2048")
    void runtimeParameterCountIsGoAligned() {
        assertEquals(10001, connector().getMaxParameterCount(),
                "容器上限必须是 10001（Go 的 net/url 段数上限 10000 + 1）"
                        + " ⇒ 它必须比 Go 的段数上限大 1，闸门才有机会先按 Go 的语义拒；"
                        + "实测=" + connector().getMaxParameterCount());
    }

    // ==================================================================
    // ② 应用层：Go 的 2048 个非空值判据
    // ==================================================================

    @Test
    @DisplayName("🔴 Go: 2049 个**非空**值 ⇒ 400 + body 逐字 = \"too many form values, error: \" + r.Form.Encode()")
    void tooManyNonEmptyValuesIsGoShapedBadRequest() throws Exception {
        Raw resp = rawGet(PATH_PROBE + "?" + nonEmptyQuery(2049));

        assertEquals(400, resp.status,
                "Go 的 httpx/util.go:48-53 超限 ⇒ http.Error(400)；实际响应=\n" + resp);
        assertEquals("too many form values, error: " + nonEmptyQuery(2049) + "\n", resp.body,
                "body 必须逐字等于 Go 的 fmt.Errorf(\"too many form values, error: %s\", r.Form.Encode()) + \"\\n\"");
        assertEquals("nosniff", resp.header("X-Content-Type-Options"),
                "Go 的 http.Error 会写 X-Content-Type-Options: nosniff");
        assertTrue(resp.header("Content-Type").startsWith("text/plain"),
                "Go 的 http.Error 写 text/plain; charset=utf-8；实测=" + resp.header("Content-Type"));
    }

    @Test
    @DisplayName("Go: 恰好 2048 个**非空**值 ⇒ 放行（判据是 n < 2048 才计数）")
    void exactlyMaxNonEmptyValuesIsAccepted() throws Exception {
        Raw resp = rawGet(PATH_PROBE + "?" + nonEmptyQuery(2048));

        assertEquals(200, resp.status, "实际响应=\n" + resp);
    }

    // ==================================================================
    // ③ 🔴 这条就是 DRIFT-HOTAPI-030 / global X-6 的窗口
    // ==================================================================

    @Test
    @DisplayName("🔴 Go: 3000 个**空值**参数 ⇒ **放行**（Go 数非空值 ⇒ 0 个）"
            + " —— 现状会被 Tomcat 的容器上限先拒成 400 + HTML")
    void manyEmptyValuesAreAcceptedLikeGo() throws Exception {
        Raw resp = rawGet(PATH_PROBE + "?" + emptyQuery(3000));

        assertEquals(200, resp.status,
                "Go 的 httpx/util.go:44-45「空值不计数」⇒ 3000 个空值参数对 Go 而言是 0 个非空值"
                        + " ⇒ Go 放行（200）⇒ RAG 必须一致；"
                        + "⚠️ 若这里变成 400，说明容器上限（总数口径）仍在生效 ⇒ 本条就是那个漂移；"
                        + "实际=\n" + resp);
        assertTrue(!resp.body.contains("<html"),
                "绝不能是 Tomcat 的 HTML 错误页；实际=\n" + resp);
    }

    // ==================================================================
    // ④ Go 的 net/url 段数上限（10000）
    // ==================================================================

    @Test
    @DisplayName("🔴 Go: query 超过 10000 段 ⇒ 400 + body 逐字 = \"number of URL query parameters exceeded limit\\n\"")
    void tooManyQuerySegmentsIsRejectedLikeGo() throws Exception {
        Raw resp = rawGet(PATH_PROBE + "?" + nonEmptyQuery(10001));

        assertEquals(400, resp.status, "Go 的 net/url/url.go:1020-1022 超限；实际=\n" + resp);
        assertEquals("number of URL query parameters exceeded limit\n", resp.body,
                "body 必须逐字等于 Go 的 errors.New(\"number of URL query parameters exceeded limit\")");
    }

    // ==================================================================
    // helpers
    // ==================================================================

    private Connector connector() {
        org.springframework.boot.tomcat.TomcatWebServer ws =
                (org.springframework.boot.tomcat.TomcatWebServer) webServerContext.getWebServer();
        // ⚠️ Tomcat 11 的 TomcatWebServer **没有** getConnectors() ⇒ 必须从 service 上取
        return ws.getTomcat().getService().findConnectors()[0];
    }

    /** 裸 socket 收到的那份响应。 */
    private record Raw(int status, String body, String head) {
        String header(String name) {
            for (String line : head.split("\r\n")) {
                int colon = line.indexOf(':');
                if (colon > 0 && line.substring(0, colon).trim().equalsIgnoreCase(name)) {
                    return line.substring(colon + 1).trim();
                }
            }
            return null;
        }
    }

    private Raw rawGet(String target) throws Exception {
        try (Socket s = new Socket("localhost", port)) {
            s.setSoTimeout(30_000);
            String request = "GET " + target + " HTTP/1.1\r\n"
                    + "Host: localhost:" + port + "\r\n"
                    + "Connection: close\r\n\r\n";
            OutputStream out = s.getOutputStream();
            out.write(request.getBytes(StandardCharsets.UTF_8));
            out.flush();
            InputStream in = s.getInputStream();
            byte[] buf = in.readAllBytes();
            String raw = new String(buf, StandardCharsets.ISO_8859_1);
            int sep = raw.indexOf("\r\n\r\n");
            String head = sep < 0 ? raw : raw.substring(0, sep);
            String body = sep < 0 ? "" : raw.substring(sep + 4);
            int sp1 = head.indexOf(' ');
            int sp2 = head.indexOf(' ', sp1 + 1);
            int status = (sp1 < 0 || sp2 < 0) ? -1 : Integer.parseInt(head.substring(sp1 + 1, sp2).trim());
            // ⚠️ Go 的 http.Error 会 **Del("Content-Length")** ⇒ Go 侧这个错误响应也是 chunked
            //    （net/http/server.go 的 Error()）⇒ RAG 侧同样 chunked ⇒ **两侧一致**。
            //    而 body 一旦超过 Tomcat 的 8KB 响应缓冲（Tomcat 默认 responseBufferSize=8192），
            //    Tomcat 就会放弃 Content-Length 改用 chunked ⇒ 读裸 socket 时**必须解 chunk**。
            if (head.toLowerCase().contains("transfer-encoding: chunked")) {
                body = decodeChunked(body);
            }
            return new Raw(status, body, head);
        }
    }

    /** 解 HTTP/1.1 chunked 传输编码。 */
    private static String decodeChunked(String raw) {
        StringBuilder out = new StringBuilder();
        int pos = 0;
        while (pos < raw.length()) {
            int eol = raw.indexOf("\r\n", pos);
            if (eol < 0) {
                break;
            }
            String sizeLine = raw.substring(pos, eol).trim();
            int semi = sizeLine.indexOf(';');
            if (semi >= 0) {
                sizeLine = sizeLine.substring(0, semi);
            }
            int size;
            try {
                size = Integer.parseInt(sizeLine, 16);
            } catch (NumberFormatException e) {
                break;
            }
            pos = eol + 2;
            if (size == 0) {
                break;
            }
            int end = Math.min(raw.length(), pos + size);
            out.append(raw, pos, end);
            pos = end + 2; // 跳过 chunk 后的 CRLF
        }
        return out.toString();
    }

    /** N 个**非空** query 值（{@code a=1&a=1&…}）。 */
    private static String nonEmptyQuery(int n) {
        StringBuilder sb = new StringBuilder(n * 4);
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append('&');
            }
            sb.append("a=1");
        }
        return sb.toString();
    }

    /** N 个**空值** query 参数（{@code a&a&…} ⇒ Go 的 value 为空 ⇒ 不计数）。 */
    private static String emptyQuery(int n) {
        StringBuilder sb = new StringBuilder(n * 2);
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append('&');
            }
            sb.append("a");
        }
        return sb.toString();
    }

    // ==================================================================
    // 测试应用
    // ==================================================================

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({
            GoStyleFormValueLimitInterceptor.class,
            GoStyleFormLimitWebMvcConfig.class,
            GoStyleTomcatParameterLimitCustomizer.class,
            ProbeController.class
    })
    static class TestApp {
    }

    /**
     * 🔴 这个 controller <b>必须读 query 参数</b>（{@code getParameter("page")}）
     * ⇒ 否则 Tomcat 不会解析 query ⇒ 容器上限不触发 ⇒ 「3000 个空值」那条用例会假绿。
     */
    @RestController
    static class ProbeController {
        @GetMapping(PATH_PROBE)
        String probe(HttpServletRequest request) {
            String page = request.getParameter("page");
            return "{\"code\":200,\"page\":" + (page == null ? "null" : page) + "}";
        }
    }
}
