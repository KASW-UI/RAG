package com.rag.common.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 <b>真 Tomcat</b> 守门：共享层的<b>路径归一</b>与 Go 的 {@code path.Clean} 同形。
 *
 * <h2>这条守门守的是什么</h2>
 * <p>G2 反馈单 FB-01（A3 轴）：Go 的 router 对请求路径做 {@code path.Clean}
 * ⇒ 路径变体<b>命中同一条路由</b>；RAG 侧原先<b>只有 article-api 有</b>这一层（域内副本）
 * ⇒ ⇒ 其余 9 个 api 缺这一层 ⇒ 变体路径两侧行为分叉。
 *
 * <h2>🟢 双侧活体探针（AGENTS.md §3.3：必须两侧同时跑）</h2>
 * <pre>
 *   [Go 侧]  真 go-zero v1.10.2 rest server（/tmp 临时工程，CGO_ENABLED=0）+ curl：
 *             PROBE /v1/x        =&gt; 200
 *             PROBE /v1/x/       =&gt; 200
 *             PROBE /v1//x       =&gt; 200
 *             PROBE /v1/x/../x   =&gt; 200
 *             PROBE /v1/./x      =&gt; 200
 *
 *   [RAG 侧] 本类：真 Tomcat + **裸 socket** 逐字发同样的 5 个 target
 *             ⇒ 必须得到**同样的 5 个 200**
 * </pre>
 * ⇒ ⇒ 本类的断言值**不是**从 Go 源码「推」出来的，是**从 Go 的活体服务量出来的**。
 *
 * <h2>⚠️ 为什么必须用<b>裸 socket</b>（而不是 HttpClient / HttpURLConnection）</h2>
 * <ul>
 *   <li><b>MockMvc 测不到</b>：它不经过 Connector，拿不到「客户端到底发了什么」。</li>
 *   <li><b>JDK HttpClient 会自己归一 URI</b> ⇒ {@code /v1//x}、{@code /v1/x/../x}
 *       在到达服务器之前就被改写 ⇒ ⇒ 测的<b>不是</b>我们想测的东西。</li>
 *   <li>裸 socket 才能保证<b>发出去的 target 与 Go 探针发的逐字相同</b>
 *       ⇒ ⇒ 两边比的是<b>同一个请求</b>。</li>
 * </ul>
 *
 * <h2>🔴 为什么这条守门是<b>行为级</b>而不是结构级</h2>
 * <p>「Bean 存在」≠「它在链上」——若归一器没进链，{@code /v1/x/} 就是 404
 * ⇒ ⇒ 本类的第 ②~⑤ 条断言<b>直接</b>证明它在链上且生效
 * ⇒ ⇒ 不需要额外的「装配层」断言来间接推断。
 */
@SpringBootTest(
        classes = GoStylePathNormalizingTomcatGateTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("🔴 真 Tomcat：路径变体命中同一条路由（对齐 Go 的 path.Clean）")
class GoStylePathNormalizingTomcatGateTest {

    @LocalServerPort
    private int port;

    private static final String ROUTE = "/v1/x";

    // ==================================================================
    // ① 基线：正常路径必须仍然命中（归一器不许把正常路径也打掉）
    // ==================================================================

    @Test
    @DisplayName("① 基线：/v1/x ⇒ 200（归一器不许误伤正常路径）")
    void normalPathStillMatches() throws Exception {
        Raw resp = rawGet(ROUTE);
        assertEquals(200, resp.status, "正常路径必须命中；实际=\n" + resp);
        assertTrue(resp.body.contains("\"hit\":\"x\""),
                "必须真的进到 handler（而不是被某个 404 兜底页糊过去）；实际=\n" + resp);
    }

    // ==================================================================
    // ②~⑤ Go 侧实测为 200 的 4 个路径变体
    // ==================================================================

    @Test
    @DisplayName("② /v1/x/ ⇒ 200（Go 活体实测 200：尾斜杠被 path.Clean 去掉）")
    void trailingSlashMatches() throws Exception {
        Raw resp = rawGet("/v1/x/");
        assertEquals(200, resp.status,
                "Go 侧实测 /v1/x/ ⇒ 200（go-zero patrouter.go:60 的 path.Clean 去掉尾斜杠）；"
                        + "RAG 侧没有这一层时是 404 ⇒ 两侧分叉；实际=\n" + resp);
        assertTrue(resp.body.contains("\"hit\":\"x\""), "必须真的进到 handler；实际=\n" + resp);
    }

    @Test
    @DisplayName("③ /v1//x ⇒ 200（Go 活体实测 200：重复斜杠被合并）")
    void duplicateSlashMatches() throws Exception {
        Raw resp = rawGet("/v1//x");
        assertEquals(200, resp.status,
                "Go 侧实测 /v1//x ⇒ 200；实际=\n" + resp);
        assertTrue(resp.body.contains("\"hit\":\"x\""), "必须真的进到 handler；实际=\n" + resp);
    }

    @Test
    @DisplayName("④ /v1/x/../x ⇒ 200（Go 活体实测 200：.. 被消解）")
    void dotDotSegmentMatches() throws Exception {
        Raw resp = rawGet("/v1/x/../x");
        assertEquals(200, resp.status,
                "Go 侧实测 /v1/x/../x ⇒ 200；实际=\n" + resp);
        assertTrue(resp.body.contains("\"hit\":\"x\""), "必须真的进到 handler；实际=\n" + resp);
    }

    @Test
    @DisplayName("⑤ /v1/./x ⇒ 200（Go 活体实测 200：. 段被丢弃）")
    void dotSegmentMatches() throws Exception {
        Raw resp = rawGet("/v1/./x");
        assertEquals(200, resp.status,
                "Go 侧实测 /v1/./x ⇒ 200；实际=\n" + resp);
        assertTrue(resp.body.contains("\"hit\":\"x\""), "必须真的进到 handler；实际=\n" + resp);
    }

    // ==================================================================
    // ⑥ 反向：归一不许把「本来不存在的路径」也变成命中
    // ==================================================================

    @Test
    @DisplayName("⑥ 反向：/v1/nope ⇒ 仍 404（归一不许放宽路由匹配）")
    void unknownPathStillNotFound() throws Exception {
        Raw resp = rawGet("/v1/nope");
        assertEquals(404, resp.status,
                "Go 侧 /v1/nope 也是 404（路由表里没有它 ⇒ Clean 之后还是没有）；"
                        + "若 RAG 变成 200 ⇒ 归一逻辑放宽了匹配，是更严重的漂移；实际=\n" + resp);
    }

    // ==================================================================
    // ⑦ 语义级：goPathClean 逐条对 Go 的 path/clean.go
    // ==================================================================

    @Test
    @DisplayName("⑦ goPathClean 逐条对齐 Go path.Clean（去尾斜杠 / 合并斜杠 / 消解 .. 与 .）")
    void goPathCleanMatchesGoSemantics() {
        assertEquals("/v1/x", GoStylePathNormalizingFilter.goPathClean("/v1/x/"), "去尾斜杠");
        assertEquals("/v1/x", GoStylePathNormalizingFilter.goPathClean("/v1//x"), "合并重复斜杠");
        assertEquals("/v1/x", GoStylePathNormalizingFilter.goPathClean("/v1/x/../x"), "消解 ..");
        assertEquals("/v1/x", GoStylePathNormalizingFilter.goPathClean("/v1/./x"), "丢弃 . 段");
        assertEquals("/v1/x", GoStylePathNormalizingFilter.goPathClean("/v1/x"), "本来就是干净的 ⇒ 不变");
        assertEquals("/", GoStylePathNormalizingFilter.goPathClean("/"), "根路径 ⇒ Go 是 \"/\"（不是空串）");
        assertEquals(".", GoStylePathNormalizingFilter.goPathClean(""), "Go：空串 ⇒ \".\"");
        assertEquals(".", GoStylePathNormalizingFilter.goPathClean(null), "Go：nil ⇒ \".\"");
        assertEquals("/a/b", GoStylePathNormalizingFilter.goPathClean("/a/b/../../a/b"), "多级回退");
        // ⚠️ 完整的 32 条 Go 实测基准在 GoStylePathCleanParityTest 里逐条钉死。
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
     * 用<b>裸 socket</b> 发一个 GET —— ⚠️ 刻意不用 HttpClient / HttpURLConnection：
     * 它们会<b>自己归一 URI</b>（{@code //} 与 {@code /../} 在到达服务器前就被改写）
     * ⇒ ⇒ 测出来的就不是「客户端到底发了什么」⇒ 与 Go 探针比的就不是同一个请求。
     */
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
     * 探测端点：回显「我确实被路由命中了，且看到的是清洗后的路径」。
     *
     * <p>⚠️ 它刻意<b>不</b>覆写路径相关方法 ⇒ 回显的 {@code getRequestURI()}
     * 就是<b>归一器交给 Spring 的那个值</b> ⇒ 一并证明「归一器改的正是路由匹配用的路径」。
     */
    @RestController
    static class ProbeController {

        @GetMapping(ROUTE)
        String probe(jakarta.servlet.http.HttpServletRequest request) {
            return "{\"hit\":\"x\",\"uri\":\"" + request.getRequestURI() + "\"}";
        }
    }
}
