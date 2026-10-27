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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>守门（off 模式）：容器上限的抬升<b>与闸门的有无解耦</b>。</b>
 *
 * <h2>🔴 这条守门钉住的是「两轴解耦」这个契约</h2>
 * <p>共享层把「form 值上限」拆成<b>两条独立的轴</b>，此前被<b>错误地</b>耦合在同一个 {@code mode} 上：
 * <table border="1">
 *   <caption>两条轴</caption>
 *   <tr><th>轴</th><th>由谁决定</th><th>OFF 时的答案</th></tr>
 *   <tr><td><b>① 装不装 form 值闸门</b>（决定<b>拒绝形状</b>）</td>
 *       <td>{@link GoStyleFormLimitMode}（{@code mode} 属性）</td>
 *       <td><b>不装</b> ⇒ 域自捕获（favorite）/ 上限不可达（task）</td></tr>
 *   <tr><td><b>② 抬不抬容器参数上限</b>（决定<b>判定权归容器还是归应用层</b>）</td>
 *       <td>{@link GoStyleTomcatParameterLimitCustomizer}</td>
 *       <td><b>照抬</b> ⇒ 一律 10001（= Go 的段数上限 10000 + 1）</td></tr>
 * </table>
 *
 * <h3>Go 出处（真源，逐行）</h3>
 * <pre>
 * Go 1.25 net/url/url.go:1001            defaultMaxParams = 10000（query 段数上限）
 * Go 1.25 net/url/url.go:1020-1022        "number of URL query parameters exceeded limit"
 * go-zero@v1.10.2/rest/httpx/util.go:15   maxFormParamCount = 2048（数【非空值】）
 *
 * ⇒ Go 侧**没有**「容器层参数上限」这个概念 ⇒ Tomcat 的 2048（yaml 层）是一个
 *   **比 Go 更早**的闸门 ⇒ ⇒ 它必须被抬到 10001，否则 Tomcat 会先于 Go 拒。
 * </pre>
 *
 * <h3>🔴 为什么「off 域也必须抬」（这正是本类存在的理由）</h3>
 * <p>旧实现按「{@code mode == OFF ⇒ 不动容器上限}」分叉 ⇒ ⇒ <b>OFF 域的运行期上限停在 yaml 的 2048</b>
 * ⇒ ⇒ 与其余域（10001）形成<b>运行期分叉</b> ⇒ ⇒ 而 OFF 的<b>两种</b>情形都会被这条误伤：
 * <ul>
 *   <li><b>上限不可达</b>（task：Go 侧 {@code r.Form} 从不求值）
 *       ⇒ ⇒ Tomcat 不该比 Go <b>更早</b>拒 ⇒ 容器上限也该抬到 10001；</li>
 *   <li><b>域自捕获</b>（favorite：域内已有 Go 口径的应用层判据，数<b>非空值</b>）
 *       ⇒ ⇒ 判定权<b>应该</b>交还应用层 ⇒ 容器上限也该抬到 10001
 *       ⇒ ⇒ 否则「Tomcat 数总参数值数（含空值）vs Go 数非空值数」的<b>差 1</b>永远闭不上
 *       ⇒ ⇒ 归 <b>DRIFT-HOTAPI-030</b>（跨域，收口在 global）+ <b>DRIFT-FAV-API-001</b>（域内）。</li>
 * </ul>
 *
 * <h3>⚠️ 为什么这三条断言都不是 Mock 能测的（§3.3）</h3>
 * <ol>
 *   <li><b>①</b> 断言的是<b>运行期 connector</b> 上的值 ⇒ MockMvc 根本不经过 Connector
 *       ⇒ 必须<b>真 Tomcat</b>（{@code webEnvironment = RANDOM_PORT}）。</li>
 *   <li><b>②</b> 走的是<b>真 Servlet 容器</b>的 query 解析 ⇒ 必须真发 HTTP 请求（裸 socket）。</li>
 *   <li><b>③</b> 断言「闸门<b>确实没装</b>」⇒ 必须读<b>容器里那个 bean</b>的 {@code mode()}
 *       ⇒ 而不是「new 一个出来自己断言」（那样测的是副本，不是装配面）。</li>
 * </ol>
 *
 * <h3>🔴 变异验证（本类的每条断言都有专属变异）</h3>
 * <ul>
 *   <li><b>变异 ①（产品侧）</b>：把 {@link GoStyleTomcatParameterLimitCustomizer} 里的
 *       「无条件抬上限」改回「{@code mode == OFF ⇒ return}」⇒
 *       ⇒ 断言①<b>红</b>（运行期 2048 ≠ 10001）⇒ 断言②<b>连带红</b>
 *       （容器在第 2049 个就拒 ⇒ 400 + HTML）⇒ 如实标注：②不是「专属」。</li>
 *   <li><b>变异 ②（产品侧）</b>：把 {@link GoStyleFormValueLimitInterceptor} 里的
 *       「{@code mode == OFF ⇒ 直接放行}」删掉⇒ ⇒ 断言③<b>红</b>
 *       （{@code preHandle} 不再返回 true）⇒ 断言②<b>连带红</b>（400 + Go 原文）
 *       ⇒ 如实标注：③对②是「专属」，②不是。</li>
 * </ul>
 *
 * <h3>⚠️ 前提：controller <b>必须</b>读 query 参数</h3>
 * <p>Tomcat 只在应用真的调用 {@code getParameter*} 时才解析 query 并计数
 * ⇒ ⇒ 本类的 controller <b>必须</b>调用 {@code getParameter("page")}，
 * 否则 ② 会<b>假绿</b>（容器根本没解析 query ⇒ 什么都不会拒）。
 */
@SpringBootTest(
        classes = GoStyleParamLimitOffModeGateTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                // 🔴 本类专门守「off 模式」⇒ 这里**故意**用 off（也是 favorite 域的缺省值）。
                "rag.http.go-form-limit.mode=off",
                // ⚠️ 与各 api 模块的 yaml 保持一致：Go 的真实上限是 1 MiB + 4 KiB。
                //    不配的话（Boot 默认 8KB）大请求行会在连接器层就被拒 ⇒ 测不到应用层。
                "server.max-http-request-header-size=1028KB"
        })
@DisplayName("🔴 容器上限的抬升与闸门的有无**解耦**（off 模式）")
class GoStyleParamLimitOffModeGateTest {

    @LocalServerPort
    private int port;

    @Autowired
    private org.springframework.boot.web.server.context.WebServerApplicationContext webServerContext;

    /** 容器里那个**真的**闸门 bean（不是 new 出来的副本）⇒ 用来断言「它确实在、且确实是 OFF」。 */
    @Autowired
    private GoStyleFormValueLimitInterceptor gate;

    private static final String PATH_PROBE = "/probe";

    // ==================================================================
    // ① 容器层：off 域的运行期上限**照样**是 10001
    // ==================================================================

    @Test
    @DisplayName("🔴 off 域的运行期 connector 参数上限**仍是 10001**（抬上限与 mode 解耦）")
    void runtimeParameterCountIsRaisedEvenWhenGateIsOff() {
        assertEquals(10001, connector().getMaxParameterCount(),
                "容器上限必须**无条件**抬到 10001（Go 的 net/url 段数上限 10000 + 1）"
                        + " ⇒ 它与「装不装应用层闸门」是两条独立的轴："
                        + "off 只该关掉闸门（拒绝形状），不该把判定权还给容器；"
                        + "⚠️ 若这里不是 10001，说明又回到了「off ⇒ 不动容器上限」的老实现"
                        + " ⇒ 实测值会掉到 **Boot 默认 1000**（本测试应用没配 yaml）或 yaml 的 2048；"
                        + "实测=" + connector().getMaxParameterCount());
    }

    // ==================================================================
    // ② 活体：off 域的请求**不被容器抢先拒**
    // ==================================================================

    @Test
    @DisplayName("🔴 off 域：2049 个非空值 ⇒ **放行 200**（容器不拒 + 闸门没装 ⇒ 两轴都对）")
    void manyNonEmptyValuesPassThroughWhenGateIsOff() throws Exception {
        Raw resp = rawGet(PATH_PROBE + "?" + nonEmptyQuery(2049));

        assertEquals(200, resp.status,
                "off 域：容器上限 10001 ⇒ 2049 个参数**不该**被容器拒；"
                        + "闸门是 off ⇒ 也不该被共享层拒 ⇒ ⇒ 必须 200；"
                        + "⚠️ 若这里变成 400：要么容器上限没抬（老实现），要么闸门被误装了；"
                        + "实际=\n" + resp);
        assertTrue(!resp.body.contains("<html"),
                "绝不能是 Tomcat 的 HTML 错误页；实际=\n" + resp);
    }

    // ==================================================================
    // ③ 闸门侧：off ⇒ 共享层**确实没有**装闸门
    // ==================================================================

    @Test
    @DisplayName("🔴 off ⇒ 容器里那个闸门确实是 OFF，且 2049 个非空值**原样放行**")
    void sharedLayerGateIsAbsentWhenOff() throws Exception {
        assertEquals(GoStyleFormLimitMode.Mode.OFF, gate.mode(),
                "容器里那个闸门 bean 的模式必须是 OFF（证明属性真的被读到，"
                        + "而不是「测试自己 new 了一个 off 的副本」）；实际=" + gate.mode());

        MockHttpServletRequest req = new MockHttpServletRequest("GET", PATH_PROBE);
        req.setQueryString(nonEmptyQuery(2049));
        MockHttpServletResponse resp = new MockHttpServletResponse();

        assertTrue(gate.preHandle(req, resp, null),
                "off 模式下闸门必须直接放行（返回 true）⇒ 它**不能**写任何响应；"
                        + "⚠️ 若这里变成 false，说明「off ⇒ 不装闸门」这条被破坏了"
                        + "⇒ 而 favorite 域的拒绝形状会被共享层抢先接管（200 + 5003 ⇒ 400/5001）");
        assertEquals(0, resp.getContentAsByteArray().length, "off 模式不得写 body");
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
     * ⇒ 否则 Tomcat 不会解析 query ⇒ 容器上限不触发 ⇒ 断言②会<b>假绿</b>。
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
