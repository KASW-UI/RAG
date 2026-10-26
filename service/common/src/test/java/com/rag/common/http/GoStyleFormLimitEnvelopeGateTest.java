package com.rag.common.http;

import com.rag.common.response.Response;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>守门（envelope 模式）：闸门抛出的异常必须真的落到「该域自己的兜底 handler」手里。</b>
 *
 * <h2>🔴 这条守门在守什么机制</h2>
 * <p>Go 侧注册了 {@code httpx.SetErrorHandler} 的域（hot / follow / like / message / comment），
 * 解析失败时走的是<b>业务信封</b>：{@code 200 + {code:5001, msg:<该域自己的文案>, data:null}}。
 * <br>⇒ 那条文案<b>在域自己的错误码表里</b>（like / comment =「系统内部错误」，
 * hot / follow / message = {@code "internal server error"}）⇒
 * <b>共享层写不出来</b> ⇒ 共享层只能<b>抛异常</b>，由域自己的
 * {@code @ExceptionHandler(Exception.class)} 兜底去查表渲染。
 *
 * <h3>⚠️ 为什么这条必须用<b>真容器 + 真 Spring MVC</b></h3>
 * <p>servlet {@code Filter} 抛出的异常<b>到不了</b> {@code @RestControllerAdvice}
 * （Filter 在 DispatcherServlet <b>之外</b>）⇒ 整条链路只在
 * <b>Interceptor</b> 上成立 ⇒ 「Bean 存在 / 类写对了」都不算证据（§3.3）。
 *
 * <h3>⚠️ 本测试的 advice 是「某个域的替身」</h3>
 * <p>它刻意<b>不是</b>共享层的实现 ⇒ 这样才能证明「文案由域提供」：
 * 若共享层哪天改成硬编码文案，本测试会红。
 */
@SpringBootTest(
        classes = GoStyleFormLimitEnvelopeGateTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "rag.http.go-form-limit.mode=envelope",
                "server.max-http-request-header-size=1028KB"
        })
@DisplayName("🔴 Go 风格 form 值上限闸门（envelope 模式：抛异常 → 域自己的兜底 handler）")
class GoStyleFormLimitEnvelopeGateTest {

    @LocalServerPort
    private int port;

    @Autowired
    private org.springframework.boot.web.server.context.WebServerApplicationContext webServerContext;

    private static final String PATH_PROBE = "/probe";

    @Test
    @DisplayName("🔴 2049 个非空值 ⇒ **200 + 业务信封**（不是 400 + text/plain）")
    void tooManyValuesBecomeBusinessEnvelope() throws Exception {
        Raw resp = rawGet(PATH_PROBE + "?" + nonEmptyQuery(2049));

        assertEquals(200, resp.status,
                "Go 侧注册了 SetErrorHandler ⇒ 走业务信封（200），不是 http.Error(400)；实际=\n" + resp);
        assertTrue(resp.header("Content-Type").contains("application/json"),
                "信封响应的 Content-Type 必须是 application/json；实测=" + resp.header("Content-Type"));
        assertTrue(resp.body.contains("\"code\":5001"),
                "信封的 code 必须是该域的 ErrorServerCommon(5001)；实际=" + resp.body);
        assertTrue(resp.body.contains("\"msg\":\"" + TestAdvice.MSG + "\""),
                "信封的 msg 必须来自**域自己**的错误码表（这里是测试替身的文案），"
                        + "共享层不得硬编码；实际=" + resp.body);
        assertTrue(resp.body.contains("\"data\":null"),
                "Go 的 SetErrorHandler 分支里 Data 恒为 nil ⇒ 信封必须有 \"data\":null；实际=" + resp.body);
    }

    @Test
    @DisplayName("Go: 恰好 2048 个非空值 ⇒ 放行（仍进业务逻辑 ⇒ code=200）")
    void exactlyMaxNonEmptyValuesIsAccepted() throws Exception {
        Raw resp = rawGet(PATH_PROBE + "?" + nonEmptyQuery(2048));
        assertEquals(200, resp.status, "实际=\n" + resp);
        assertTrue(resp.body.contains("\"code\":200"), "应当进到业务逻辑；实际=" + resp.body);
    }

    @Test
    @DisplayName("🔴 3000 个空值参数 ⇒ 放行（Go 数非空值 ⇒ 0 个）—— X-6 的窗口")
    void manyEmptyValuesAreAcceptedLikeGo() throws Exception {
        Raw resp = rawGet(PATH_PROBE + "?" + emptyQuery(3000));
        assertEquals(200, resp.status, "实际=\n" + resp);
        assertTrue(!resp.body.contains("<html"), "绝不能是 Tomcat 的 HTML 错误页；实际=\n" + resp);
    }

    @Test
    @DisplayName("🔴 运行期 connector 的参数上限 = 10001（envelope 模式也必须抬，否则 Tomcat 先拒）")
    void runtimeParameterCountIsGoAligned() {
        Connector c = ((org.springframework.boot.tomcat.TomcatWebServer) webServerContext.getWebServer())
                .getTomcat().getService().findConnectors()[0];
        assertEquals(10001, c.getMaxParameterCount(),
                "envelope 模式同样需要把容器上限抬到 10001 ⇒ 否则 Tomcat 先于应用层拒绝；实测="
                        + c.getMaxParameterCount());
    }

    // ==================================================================
    // helpers
    // ==================================================================

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
            if (head.toLowerCase().contains("transfer-encoding: chunked")) {
                body = decodeChunked(body);
            }
            return new Raw(status, body, head);
        }
    }

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
            pos = end + 2;
        }
        return out.toString();
    }

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
            ProbeController.class,
            TestAdvice.class
    })
    static class TestApp {
    }

    @RestController
    static class ProbeController {
        @GetMapping(PATH_PROBE)
        String probe(HttpServletRequest request) {
            String page = request.getParameter("page");
            return "{\"code\":200,\"page\":" + (page == null ? "null" : page) + "}";
        }
    }

    /**
     * 「某个域的兜底 handler」的替身 —— 形状照抄各域的
     * {@code @ExceptionHandler(Exception.class)}：{@code 200 + {code:5001, 查表文案, data:null}}。
     */
    @RestControllerAdvice
    static class TestAdvice {

        /** 刻意用一句共享层<b>不可能</b>硬编码的文案 ⇒ 证明文案由域提供。 */
        static final String MSG = "internal server error";

        @ExceptionHandler(Exception.class)
        ResponseEntity<Response> handleAny(Exception e) {
            return ResponseEntity.ok(new Response(5001, MSG, null));
        }
    }
}
