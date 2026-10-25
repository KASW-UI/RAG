package com.rag.common.http;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 <b>真 Tomcat</b> 守门：<b>原始 Content-Type 头值不会被容器归一</b>。
 *
 * <h2>这条守门守的是「承重事实」，不是行为</h2>
 *
 * <p>共享层唯一真源 {@code GoStyleJsonBodyConverter#goReadsBody(HttpServletRequest)}
 * 内部取 {@code request.getContentType()}，并据此判定「Go 是否读 body」。
 * ⇒ 这个取值必须是<b>原始头值</b>（大小写敏感），否则整条规则会与 Go 相反。
 *
 * <h3>Go 出处（真源 = L2 框架层 go-zero@v1.10.2）</h3>
 * <pre>
 * rest/httpx/requests.go:153-155
 *     func withJsonBody(r *http.Request) bool {
 *         return r.ContentLength &gt; 0 &amp;&amp; strings.Contains(r.Header.Get(header.ContentType), header.ApplicationJson)
 *     }
 * </pre>
 * ⇒ {@code r.Header.Get} 返回客户端发来的**原始字符串** ⇒ {@code strings.Contains} **大小写敏感**
 * ⇒ {@code Content-Type: Application/JSON} ⇒ Go **不读 body**。
 *
 * <h3>Tomcat 侧的逐行依据（不是推测）</h3>
 * <pre>
 * org/apache/catalina/connector/Request.java:1081-1083  getContentType() → coyoteRequest.getContentType()
 * org/apache/coyote/Request.java:694-700                 → contentTypeMB.toStringType()
 * org/apache/tomcat/util/buf/MessageBytes.java:214-229     → byteC.toString()
 * org/apache/tomcat/util/buf/ByteChunk.java:602-609        → 只做字符集解码（REPLACE）
 * </pre>
 * ⇒ 全链<b>没有</b>小写化 / MIME 归一 ⇒ 本类用<b>裸 socket</b>逐字发头，把这件事从「读源码」升级为「实测」。
 *
 * <h2>⚠️ 为什么必须用真容器 + 裸 socket（§3.3）</h2>
 * <ul>
 *   <li><b>MockMvc 测不到</b>：它根本不经过 Connector，{@code getContentType()} 来自 mock 自身。</li>
 *   <li><b>MockHttpServletRequest 也不够</b>：它的 {@code getContentType()} 返回自己存的值 ⇒
 *       证明的是「mock 没归一」，不是「Tomcat 没归一」。</li>
 *   <li>裸 socket 才能保证<b>发出去的头就是那个大小写</b>：JDK 的 HttpClient 会规范化部分头，
 *       而 {@code HttpURLConnection} 连 {@code Content-Type} 都会被改写。</li>
 * </ul>
 */
@SpringBootTest(
        classes = GoStyleJsonBodyRawHeaderTomcatTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("🔴 真 Tomcat：原始 Content-Type 头值不被归一 ⇒ 共享判据保持 Go 的大小写敏感")
class GoStyleJsonBodyRawHeaderTomcatTest {

    @LocalServerPort
    private int port;

    private static final String PATH_PROBE = "/probe";

    // ==================================================================
    // ① 承重事实：容器不改头值
    // ==================================================================

    @Test
    @DisplayName("🔴 Content-Type: Application/JSON ⇒ 到达应用层仍是 Application/JSON（未被小写化）")
    void rawContentTypeSurvivesTheContainer() throws Exception {
        Raw resp = rawPost(PATH_PROBE, "Application/JSON", "{");

        assertEquals(200, resp.status, "探测端点必须可达；实际响应=\n" + resp);
        assertTrue(resp.body.contains("\"raw\":\"Application/JSON\""),
                "Tomcat 必须把原始头值原样交给应用层（大小写敏感 ⇒ 与 Go 的 strings.Contains 同形）；"
                        + "若这里变成 application/json ⇒ 共享判据会与 Go 判成相反；实际=\n" + resp);
    }

    // ==================================================================
    // ② 判据行为：大小写敏感
    // ==================================================================

    @Test
    @DisplayName("🔴 Application/JSON ⇒ 判据为**假**（Go 不读 body）")
    void mixedCaseContentTypeIsNotRead() throws Exception {
        Raw resp = rawPost(PATH_PROBE, "Application/JSON", "{");
        assertTrue(resp.body.contains("\"reads\":false"),
                "Go 的 strings.Contains 大小写敏感 ⇒ Application/JSON 不含 application/json ⇒ 不读 body；实际=\n" + resp);
    }

    @Test
    @DisplayName("🔴 全大写 APPLICATION/JSON ⇒ 判据为**假**")
    void upperCaseContentTypeIsNotRead() throws Exception {
        Raw resp = rawPost(PATH_PROBE, "APPLICATION/JSON", "{");
        assertTrue(resp.body.contains("\"reads\":false"), "实际=\n" + resp);
    }

    @Test
    @DisplayName("对照：标准小写 application/json ⇒ 判据为**真**（Go 读 body）")
    void lowerCaseContentTypeIsRead() throws Exception {
        Raw resp = rawPost(PATH_PROBE, "application/json", "{");
        assertTrue(resp.body.contains("\"reads\":true"),
                "标准 JSON 头必须照常命中（否则是把正常路径也打掉了）；实际=\n" + resp);
    }

    @Test
    @DisplayName("对照：带 charset ⇒ 判据为**真**（Go 是 Contains，不是精确相等）")
    void contentTypeWithCharsetIsRead() throws Exception {
        Raw resp = rawPost(PATH_PROBE, "application/json; charset=utf-8", "{");
        assertTrue(resp.body.contains("\"reads\":true"), "实际=\n" + resp);
    }

    @Test
    @DisplayName("🔴 +json 后缀（application/vnd.api+json）⇒ 判据为**假**（Go 是字面子串匹配）")
    void vendorJsonSuffixIsNotRead() throws Exception {
        Raw resp = rawPost(PATH_PROBE, "application/vnd.api+json", "{");
        assertTrue(resp.body.contains("\"reads\":false"),
                "Go 的 strings.Contains(\"application/vnd.api+json\", \"application/json\") == false ⇒ 不读；实际=\n" + resp);
    }

    @Test
    @DisplayName("🔴 无 Content-Type ⇒ 判据为**假**")
    void missingContentTypeIsNotRead() throws Exception {
        Raw resp = rawPost(PATH_PROBE, null, "{");
        assertTrue(resp.body.contains("\"reads\":false"),
                "Go 的 r.Header.Get 缺头 ⇒ 空串 ⇒ contains 为假 ⇒ 不读；实际=\n" + resp);
    }

    // ==================================================================
    // helpers
    // ==================================================================

    /** 裸 socket 收到的响应。 */
    private record Raw(int status, String body, String head) {
        @Override
        public String toString() {
            return "status=" + status + "\nhead=" + head + "\nbody=" + body;
        }
    }

    /**
     * 用<b>裸 socket</b> 发一个 POST —— ⚠️ 刻意不用 HttpClient / HttpURLConnection：
     * 它们会规范化部分请求头（{@code HttpURLConnection} 连 {@code Content-Type} 都会被改写）
     * ⇒ 就测不出「原始头值」这件事了。
     *
     * @param contentType 头值；{@code null} ⇒ **不发** Content-Type 头
     */
    private Raw rawPost(String target, String contentType, String body) throws Exception {
        try (Socket s = new Socket("localhost", port)) {
            s.setSoTimeout(30_000);
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            StringBuilder head = new StringBuilder();
            head.append("POST ").append(target).append(" HTTP/1.1\r\n");
            head.append("Host: localhost:").append(port).append("\r\n");
            if (contentType != null) {
                head.append("Content-Type: ").append(contentType).append("\r\n");
            }
            head.append("Content-Length: ").append(payload.length).append("\r\n");
            head.append("Connection: close\r\n\r\n");

            OutputStream out = s.getOutputStream();
            out.write(head.toString().getBytes(StandardCharsets.ISO_8859_1));
            out.write(payload);
            out.flush();

            InputStream in = s.getInputStream();
            String raw = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
            int sep = raw.indexOf("\r\n\r\n");
            String rawHead = sep < 0 ? raw : raw.substring(0, sep);
            String rawBody = sep < 0 ? "" : raw.substring(sep + 4);
            int sp1 = rawHead.indexOf(' ');
            int sp2 = rawHead.indexOf(' ', sp1 + 1);
            int status = (sp1 < 0 || sp2 < 0) ? -1 : Integer.parseInt(rawHead.substring(sp1 + 1, sp2).trim());
            if (rawHead.toLowerCase().contains("transfer-encoding: chunked")) {
                rawBody = decodeChunked(rawBody);
            }
            return new Raw(status, rawBody, rawHead);
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
            pos = end + 2;
        }
        return out.toString();
    }

    // ==================================================================
    // 测试应用
    // ==================================================================

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(ProbeController.class)
    static class TestApp {
    }

    /**
     * 把「容器交给应用层的原始头值」与「共享判据的结论」一起报出来。
     *
     * <p>⚠️ 本 controller <b>不读 body</b>：它只报告判据结论 ⇒ 不干扰 body 流的任何行为。
     */
    @RestController
    static class ProbeController {
        @PostMapping(PATH_PROBE)
        String probe(HttpServletRequest request) {
            String raw = request.getContentType();
            boolean reads = GoStyleJsonBodyConverter.goReadsBody(request);
            return "{\"raw\":\"" + (raw == null ? "" : raw) + "\",\"reads\":" + reads + "}";
        }
    }
}
