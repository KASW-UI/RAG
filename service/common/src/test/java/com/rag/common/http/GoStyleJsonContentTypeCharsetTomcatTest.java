package com.rag.common.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🔴🔴 **T-6 守门**：JSON 响应的 {@code Content-Type} 必须<b>带 {@code charset=utf-8}</b>
 * （Go 写的是 {@code "application/json; charset=utf-8"}）。
 *
 * <h2>⚠️ 为什么断言的是「归一化后」的形态（与仓库既有先例一致）</h2>
 * <p>真 Tomcat 会把 Content-Type <b>拆成 (type/subtype, charset) 两份存</b>、写出去时<b>重新拼</b>
 * ⇒ 线上恒为 {@code type/subtype;charset=xxx}（<b>分号后无空格</b>）
 * ⇒ 而 Go 的 net/http 是<b>逐字</b>写出 {@code "application/json; charset=utf-8"}（<b>有空格</b>）。
 * <p>⇒ 按 RFC 9110，参数分隔符两旁的 OWS <b>不显著</b> ⇒ 两者<b>语义相同</b>
 * ⇒ ⇒ 本仓库<b>已有先例</b>：{@code LikeApiRouterErrorRealTomcatTest} 对 404 的
 * {@code text/plain; charset=utf-8} 就是<b>刻意解析后比对</b>（去空格），
 * 注释明写「比原始字符串会得到一个『必然红、且红了也不代表有错』的断言（已实测）」。
 * ⇒ ⇒ 本守门<b>沿用同一口径</b>：锁「charset 必须在 + type 必须是 application/json」，
 * <b>不</b>去锁那个空格（锁了就是一条永远红的断言）。
 *
 * <h2>Go 出处（真源，逐行）</h2>
 * <pre>
 * go-zero@v1.10.2/rest/httpx/responses.go:175-193（doWriteJson）
 *     func doWriteJson(w http.ResponseWriter, code int, v any) error {
 *         bs, err := jsonx.Marshal(v)
 *         ...
 *         w.Header().Set(ContentType, header.ContentTypeJson)   // ← 无条件覆盖
 *         w.WriteHeader(code)
 *         ...
 *     }
 *
 * go-zero@v1.10.2/rest/internal/header/headers.go:16-17
 *     ContentType = "Content-Type"
 *     ContentTypeJson = "application/json; charset=utf-8"        // ← 分号后**有空格**
 * </pre>
 *
 * <h2>⚠️ 为什么「分号后有空格」是承重细节</h2>
 * <p>Spring 的 {@code MediaType.toString()} 产出的是 {@code application/json;charset=utf-8}
 * ⇒ <b>无空格</b> ⇒ 与 Go <b>字节级不一致</b>。
 * ⇒ 这正是 hot 域 {@code SD-HOT-API-006} 当初签字的技术理由
 * （「Spring 的 {@code MediaType.toString()} 产出 {@code ;} 后无空格 ⇒ 不可逐字节复刻」）。
 * <p>⇒ <b>2026-10-25 用户拍板撤销该签字</b>（理由已被本仓自己证伪）⇒ 本类守的是<b>撤销后</b>的新形态。
 *
 * <h2>⚠️ 为什么必须用<b>裸 socket</b>（§3.3）</h2>
 * <ul>
 *   <li><b>MockMvc 测不到</b>：它不经过 Connector ⇒ 拿不到容器最终发出的字节。</li>
 *   <li>{@code response.getContentType()} 会把头<b>解析</b>成 {@code MediaType} ⇒
 *       空格差异会被抹平 ⇒ <b>证不出</b>「分号后有没有空格」。</li>
 *   <li>裸 socket 才能拿到<b>原样</b>的响应头行。</li>
 * </ul>
 *
 * <h2>为什么这条落在<b>共享层</b>而不是某个 api</h2>
 * <p>Go 侧 {@code doWriteJson} 对<b>所有</b> JSON 响应生效 ⇒ RAG 侧也必须对<b>所有</b> api 生效
 * ⇒ 落点只能是 {@code service/common} ⇒ 改 1 处 ⇒ 10 个 api 全部生效 ⇒ <b>零 api 代码改动</b>。
 */
@SpringBootTest(
        classes = GoStyleJsonContentTypeCharsetTomcatTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("🔴 真 Tomcat：JSON 响应的 Content-Type 必须带 charset=utf-8（Go: application/json; charset=utf-8）")
class GoStyleJsonContentTypeCharsetTomcatTest {

    @LocalServerPort
    private int port;

    private static final String PATH_JSON = "/json";
    private static final String PATH_ERROR = "/error";
    private static final String PATH_RAW_TEXT = "/rawtext";

    /**
     * Go 的常量 —— **分号后有空格**。
     *
     * <p>⚠️ <b>这条线路上拿不到这个形态</b>：Tomcat 11 的
     * {@code Response.setContentType(String)} 把 Content-Type <b>拆成 (type/subtype, charset)</b>
     * 两份存，写出去时<b>重新拼</b>（{@code Http11OutputBuffer.writeHeaders} ⇒ {@code ";"} 后面<b>没有空格</b>）
     * ⇒ ⇒ 线上恒为 {@code application/json;charset=utf-8}。
     * ⇒ ⇒ 与 Go 的<b>逐字节</b>一致属<b>框架限制</b>（已登记），
     * 本守门锁的是<b>可修的那一半</b>：<b>charset 必须出现</b>。
     */
    private static final String GO_JSON_CONTENT_TYPE = "application/json; charset=utf-8";

    /** Tomcat 归一化之后的实际形态（= RAG 侧能达到的最好结果）。 */
    private static final String TOMCAT_NORMALIZED_JSON_CONTENT_TYPE = "application/json;charset=utf-8";

    // ==================================================================
    // ① 成功路径：JSON 响应
    // ==================================================================

    @Test
    @DisplayName("🔴 JSON 成功响应 ⇒ Content-Type 逐字节 = application/json; charset=utf-8")
    void jsonResponseContentTypeMatchesGoByteForByte() throws Exception {
        Raw resp = rawGet(PATH_JSON);

        assertThat(resp.status).isEqualTo(200);
        assertThat(resp.contentType())
                .as("Go: rest/httpx/responses.go:182 ⇒ w.Header().Set(ContentType, header.ContentTypeJson)"
                        + "；header.ContentTypeJson = \"application/json; charset=utf-8\""
                        + " ⇒ **charset 必须出现**（修前是裸 application/json，连 charset 都没有）；"
                        + "⚠️ 但 Tomcat 11 会把 Content-Type 拆成 (type/subtype, charset) 重新拼"
                        + " ⇒ 线上是**无空格**形态 ⇒ 与 Go 的逐字节差一个空格（框架限制，已登记）；"
                        + "实际响应头=\n" + resp)
                .isEqualTo(TOMCAT_NORMALIZED_JSON_CONTENT_TYPE)
                .as("JSON 响应必须带 charset=utf-8（Go 有）⇒ 且必须是 application/json 而非别的类型");
    }

    // ==================================================================
    // ② 失败路径：@ExceptionHandler 的 JSON 响应
    // ==================================================================

    @Test
    @DisplayName("🔴 JSON 失败响应（@ExceptionHandler）⇒ 同一个 Content-Type")
    void jsonErrorResponseContentTypeMatchesGoByteForByte() throws Exception {
        Raw resp = rawGet(PATH_ERROR);

        assertThat(resp.status).isEqualTo(400);
        assertThat(resp.contentType())
                .as("Go: 错误响应走 httpx.WriteJson ⇒ 同样 doWriteJson ⇒ 同一个 ContentTypeJson；实际=\n" + resp)
                .isEqualTo(TOMCAT_NORMALIZED_JSON_CONTENT_TYPE);
    }

    // ==================================================================
    // ④ 🔴🔴 MockMvc 路径：共享层写的值必须 == 各域自己的 advice 会写的值
    // ==================================================================

    /**
     * 🔴🔴🔴 **这条守门锁的是「共享层改动不许打红别的域的 MockMvc 守门」**。
     *
     * <p><b>事故经过（2026-10-25 第二十五轮实测）**：本 Advice 初版写的是 Go 的<b>带空格</b>字面量
     * {@code "application/json; charset=utf-8"} ⇒ <b>真 Tomcat 路径没事</b>（容器归一化成无空格），
     * 但 <b>MockMvc 路径没有容器归一化</b> ⇒ 写什么就原样出现 ⇒ 把
     * {@code FavoriteApiMvcPipelineTest.responseContentTypeMatchesGoConstant} 打红了
     * ⇒ 而 favorite 域对 global <b>零写权</b> ⇒ 修法只能落在本类。
     *
     * <p><b>不变量</b>：各域自己的 advice（如 favorite 的 {@code GlobalResponseAdvice}）
     * 用 {@code MediaType.parseMediaType("application/json; charset=utf-8")}
     * ⇒ Spring <b>保留原样的大小写</b> ⇒ 产出 {@code application/json;charset=utf-8}（小写 utf-8）。
     * ⇒ 本 Advice 必须写<b>同一个值</b> ⇒ MockMvc 路径<b>零变化</b> ⇒ 别的域的守门不会被共享层改动打到。
     *
     * <p>⚠️ <b>不能用 {@code new MediaType(APPLICATION_JSON, UTF_8).toString()}</b> 写死期望值：
     * 那条路产出的是 {@code application/json;charset=UTF-8}（<b>大写</b> UTF-8）
     * ⇒ 与各域实际产出的<b>小写</b>不一致 ⇒ 用它当期望值会把正确的实现判成红。
     */
    @Test
    @DisplayName("🔴 MockMvc 路径：Advice 写的值 == 各域自己 advice 会写的值 ⇒ 共享层改动不误伤别的域的 MockMvc 守门")
    void adviceWritesExactlyWhatDomainsOwnAdviceWritesInMockMvc() {
        assertThat(GoStyleJsonContentTypeAdvice.GO_JSON_CONTENT_TYPE)
                .as("各域自己的 advice（favorite GlobalResponseAdvice 等）用 "
                        + "MediaType.parseMediaType(\"application/json; charset=utf-8\") ⇒ 产出 "
                        + "application/json;charset=utf-8（Spring 保留原样大小写）"
                        + " ⇒ 共享层必须写同一个值，否则 MockMvc 路径的值会变 ⇒ 别的域的守门会被打红")
                .isEqualTo(MediaType.parseMediaType("application/json; charset=utf-8").toString());
    }

    // ==================================================================
    // ③ 🔴 不许误伤：显式指定 Content-Type 的响应必须原样保留
    // ==================================================================

    @Test
    @DisplayName("🔴 显式 Content-Type（text/plain; charset=utf-8）⇒ **原样保留**，不许被改成 JSON")
    void explicitContentTypeIsNotClobbered() throws Exception {
        Raw resp = rawGet(PATH_RAW_TEXT);

        assertThat(resp.status).isEqualTo(200);
        assertThat(resp.contentType())
                .as("Go: http.Error 写的是 text/plain; charset=utf-8（rest 的错误响应走这条）"
                        + " ⇒ 本次修复只针对 JSON ⇒ 显式声明的 Content-Type **不许**被改写成 JSON；"
                        + "⚠️ Tomcat 同样会把它归一化成**无空格**形态 ⇒ 断言 type 与 charset，不断言空格；实际=\n" + resp)
                .startsWith("text/plain")
                .contains("charset=utf-8")
                .doesNotContain("application/json");
        assertThat(resp.body).contains("plain-text-body");
    }

    // ==================================================================
    // helpers
    // ==================================================================

    /** 裸 socket 收到的响应。 */
    private record Raw(int status, String body, String head) {
        String contentType() {
            for (String line : head.split("\r\n")) {
                if (line.toLowerCase(java.util.Locale.ROOT).startsWith("content-type:")) {
                    return line.substring("content-type:".length()).trim();
                }
            }
            return null;
        }

        @Override
        public String toString() {
            return "status=" + status + "\nhead=" + head + "\nbody=" + body;
        }
    }

    private Raw rawGet(String target) throws Exception {
        try (Socket s = new Socket("localhost", port)) {
            s.setSoTimeout(30_000);
            StringBuilder head = new StringBuilder();
            head.append("GET ").append(target).append(" HTTP/1.1\r\n");
            head.append("Host: localhost:").append(port).append("\r\n");
            head.append("Connection: close\r\n\r\n");

            OutputStream out = s.getOutputStream();
            out.write(head.toString().getBytes(StandardCharsets.ISO_8859_1));
            out.flush();

            InputStream in = s.getInputStream();
            String raw = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
            if (raw.isEmpty()) {
                // 🔴 **2026-10-27 第十三次全量时实测到的一次抖动**：本类三条断言同时红成
                //    「expected: 200 but was: -1」⇒ ⇒ **-1 = 响应头解析不出来** ⇒
                //    而 Tomcat 那次**正常起在随机端口**（日志可查）⇒ ⇒ 真实现象是
                //    **连接建立后、服务器一个字节都没回就关掉了**（连接层，不是断言语义）。
                //    ⇒ ⇒ 旧写法把这种**连接层**故障报成「status = -1」⇒ ⇒ 读者看不出
                //    「到底是没有响应，还是响应格式不对」⇒ ⇒ **不可诊断**。
                //    ⇒ ⇒ 这里改成**可读的失败**：直接把「空响应」这个事实说清楚。
                //    ⇒ ⇒ 注意：**不加重试**（重试会把「服务器真的拒绝连接」这类真缺陷洗成绿的）。
                throw new IllegalStateException(
                        "服务器返回**空响应**（连接已建立，但**一个字节都没回**就被关掉了）"
                                + " ⇒ ⇒ 这不是断言语义不符，而是**连接层**现象"
                                + "（Tomcat accept 队列被打满 / 连接被立即关闭 / 机器负载抖动）"
                                + " ⇒ ⇒ 目标=" + target + " 端口=" + port);
            }
            int sep = raw.indexOf("\r\n\r\n");
            String rawHead = sep < 0 ? raw : raw.substring(0, sep);
            String rawBody = sep < 0 ? "" : raw.substring(sep + 4);
            int sp1 = rawHead.indexOf(' ');
            int sp2 = rawHead.indexOf(' ', sp1 + 1);
            int status = (sp1 < 0 || sp2 < 0) ? -1 : Integer.parseInt(rawHead.substring(sp1 + 1, sp2).trim());
            if (rawHead.toLowerCase(java.util.Locale.ROOT).contains("transfer-encoding: chunked")) {
                rawBody = decodeChunked(rawBody);
            }
            return new Raw(status, rawBody, rawHead);
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

    // ==================================================================
    // 测试应用
    // ==================================================================

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({ProbeController.class, JsonErrorAdvice.class})
    static class TestApp {
    }

    @RestController
    static class ProbeController {

        /** JSON 成功响应。 */
        @GetMapping(PATH_JSON)
        Map<String, Object> json() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", 0);
            m.put("msg", "ok");
            m.put("data", "payload");
            return m;
        }

        /** 抛异常 ⇒ 走 @ExceptionHandler ⇒ JSON 错误响应。 */
        @GetMapping(PATH_ERROR)
        Map<String, Object> error() {
            throw new IllegalStateException("boom");
        }

        /**
         * 显式声明 Content-Type 的响应 —— 用来证明「本次修复不许误伤显式声明」。
         *
         * <p>⚠️ 这里用 {@code byte[]} + 显式头 ⇒ 写入方是 {@code ByteArrayHttpMessageConverter}
         * （排在 Jackson 之前）⇒ 与 Go 的 {@code http.Error} 形态对应。
         */
        @GetMapping(PATH_RAW_TEXT)
        ResponseEntity<byte[]> rawText() {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "text/plain; charset=utf-8")
                    .body("plain-text-body".getBytes(StandardCharsets.UTF_8));
        }
    }

    /** JSON 失败响应的处理器（照 Go 的 {@code httpx.WriteJson} 形态：JSON body + 状态码）。 */
    @org.springframework.web.bind.annotation.RestControllerAdvice
    static class JsonErrorAdvice {
        @org.springframework.web.bind.annotation.ExceptionHandler(IllegalStateException.class)
        ResponseEntity<Map<String, Object>> onIllegalState(IllegalStateException e) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", 5000);
            m.put("msg", e.getMessage());
            return ResponseEntity.status(400).body(m);
        }
    }
}
