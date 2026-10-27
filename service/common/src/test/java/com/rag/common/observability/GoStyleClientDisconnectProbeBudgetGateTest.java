package com.rag.common.observability;

import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>守门：每个请求的 1xx 探针行数必须有上界（否则真实客户端读不到最终响应）。</b>
 *
 * <h3>这条守门钉的是哪个缺陷（2026-10-27 实测）</h3>
 * <p>共享层的「客户端断连主动探测」每片等待醒来就往 socket 写一行
 * {@code HTTP/1.1 103 Early Hints} ⇒ <b>1xx 行数 ≈ 耗时 ÷ 探测间隔</b>
 * ⇒ <b>原本没有上界</b>。
 * <p>⇒ 而客户端对 interim(1xx) 响应有硬上限：**JDK {@code java.net.http} 默认 8 个**
 * （{@code java.net.http/jdk/internal/net/http/Exchange.java:71-72}
 * ⇒ {@code MAX_NON_FINAL_RESPONSES = Utils.getIntegerNetProperty(
 * "jdk.httpclient.maxNonFinalResponses", 8)}；超限见同文件 {@code :544-550}
 * ⇒ 抛 {@code ProtocolException("Too many interim responses received: 9 > 8")}）。
 * <p>⇒ 实测复现（本仓 {@code api-envelope-probe/favorite}）：一个耗时 6.7s 的请求
 * 写出 <b>12 行</b> 1xx ⇒ JDK 客户端直接抛异常 ⇒ <b>客户端根本读不到最终响应</b>
 * ⇒ 但那**不是业务错**（RAG 的最终响应与 Go 冻结期望<b>逐字节一致</b>）。
 *
 * <h3>为什么必须跑在<b>真 Tomcat + 真 JDK 客户端</b>上（§3.3）</h3>
 * <ul>
 *   <li><b>真 Tomcat</b>：1xx 是<b>裸写 socket</b>（绕过 Tomcat 的 OutputBuffer）
 *       ⇒ mock response 的 {@code write()} <b>永远不会失败也不会产生 1xx</b>
 *       ⇒ 那种测试是「错误答案的守卫」。</li>
 *   <li><b>真 JDK 客户端</b>：断言 ② 要证的正是「<b>真实客户端</b>能否解析」，
 *       ⇒ 用一个自己写的宽松解析器就<b>证不出</b>这个约束
 *       ⇒ 必须用 {@code java.net.http.HttpClient}（= 真正会摔的那一个）。</li>
 * </ul>
 *
 * <h3>三条断言 ＋ 每条一个<b>专属变异</b></h3>
 * <table border="1">
 *   <caption>断言与变异</caption>
 *   <tr><th>断言</th><th>钉的是</th><th>专属变异</th><th>变异后果</th></tr>
 *   <tr><td>① 线上 1xx 行数 ≤ 预算</td><td>预算闸门真的生效</td>
 *       <td>M1：闸门写成 {@code used > max}（差一）</td><td>① 红（行数 = cap+1）</td></tr>
 *   <tr><td>② JDK HttpClient 能读到最后响应</td><td>预算**低于客户端上限**</td>
 *       <td>M3：默认上界 4 → 1000</td><td>② 红（客户端抛 ProtocolException）</td></tr>
 *   <tr><td>③ 第二个请求也有自己的预算</td><td>计数**按请求**（不是全局）</td>
 *       <td>M2：计数器改成静态字段</td><td>③ 红（第二个请求 0 行）</td></tr>
 * </table>
 * <p>⚠️ **用例会互相影响**（预算/端口/静态状态）⇒ ⇒ 用
 * {@link TestMethodOrder} 固定次序 ⇒ ⇒ 否则「变异只红哪一条」不可复算。
 *
 * <h3>为什么把探测间隔压到 50ms</h3>
 * <p>默认间隔 500ms ⇒ 要写出 9 行 1xx 需要 4.5s ⇒ 守门会跑很久。
 * ⇒ 这里在 {@code @BeforeAll} 把间隔压到 {@code MIN_PROBE_INTERVAL_MILLIS}（50ms）
 * ⇒ 700ms 的业务 ⇒ <b>约 13 次探测</b> ⇒ 足够越过客户端上限（8）⇒ 守门 1 秒内跑完。
 * <p>⚠️ 该属性是<b>进程级</b> ⇒ 只在本类的 {@code @BeforeAll}/{@code @AfterAll} 之间生效
 * ⇒ ⚠️ 本仓 surefire <b>未开并行</b>（实测无 {@code junit-platform.properties}）
 * ⇒ ⇒ 不会影响同 JVM 的其它测试类。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GoStyleClientDisconnectProbeBudgetGateTest {

    /** 慢路径：耗时必须 ≫ 探测间隔 ⇒ 探针必然反复写。 */
    private static final String PATH_SLOW = "/slow";

    /** 业务处理耗时（毫秒）—— 配合 50ms 的探测间隔 ⇒ 约 13 次探测（> 客户端上限 8）。 */
    private static final long SLOW_BUSINESS_MS = 700L;

    /** HTTP 超时（秒）—— 必须远大于业务耗时 ⇒ 请求是**正常完成**的（不是被超时打断）。 */
    private static final int HTTP_TIMEOUT_SECONDS = 10;

    private static Tomcat tomcat;
    private static int port;

    @BeforeAll
    static void startAll() throws Exception {
        // ⚠️ 必须在 Tomcat 起来**之前**设好（探针间隔是**每次调用现读**的 ⇒ 其实先后都行，
        //    但放前面更直观）。@AfterAll 会清掉 ⇒ 不污染同 JVM 的其它测试类。
        System.setProperty(GoStyleClientDisconnectProbe.PROBE_INTERVAL_PROPERTY, "50");

        File base = new File(System.getProperty("java.io.tmpdir"), "rag-common-disconnect-probe-budget-gate");
        base.mkdirs();
        tomcat = new Tomcat();
        tomcat.setBaseDir(base.getAbsolutePath());
        tomcat.setPort(0);
        tomcat.getConnector();
        Context ctx = tomcat.addContext("", base.getAbsolutePath());

        Tomcat.addServlet(ctx, "slow", new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                try {
                    Thread.sleep(SLOW_BUSINESS_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                resp.setContentType("application/json");
                resp.getWriter().write("{\"code\":200,\"msg\":\"ok\"}");
            }
        });
        ctx.addServletMappingDecoded(PATH_SLOW, "slow");

        // ★ **真** HttpMiddleware —— 1xx 探针与预算闸门都住在它里面
        addFilter(ctx, "httpMiddleware", HttpMiddleware.newHttpMiddleware(
                "probe-budget-gate-svc", Duration.ofSeconds(HTTP_TIMEOUT_SECONDS), Duration.ofSeconds(1)));

        tomcat.start();
        port = tomcat.getConnector().getLocalPort();
    }

    @AfterAll
    static void stopAll() {
        System.clearProperty(GoStyleClientDisconnectProbe.PROBE_INTERVAL_PROPERTY);
        if (tomcat != null) {
            try {
                tomcat.stop();
                tomcat.destroy();
            } catch (Exception ignored) {
                // 收尾失败不影响判定
            }
        }
    }

    private static void addFilter(Context ctx, String name, Filter filter) {
        FilterDef def = new FilterDef();
        def.setFilterName(name);
        def.setFilter(filter);
        def.setAsyncSupported("false");
        ctx.addFilterDef(def);
        FilterMap map = new FilterMap();
        map.setFilterName(name);
        map.addURLPattern("/*");
        ctx.addFilterMap(map);
    }

    // ================================================================== 断言 ①

    @Test
    @Order(1)
    @DisplayName("🔴 真 Tomcat：线上 1xx 行数必须 ≤ 预算（每请求一个预算 · 不能无上界）")
    void probeLinesNeverExceedTheBudget() throws Exception {
        String raw = httpGet(PATH_SLOW);

        int lines = countInformationalResponses(raw);
        int cap = GoStyleClientDisconnectProbe.maxProbeLines();

        // 正向对照：慢请求必然触发探测 ⇒ 线上**必须**看得到 1xx
        // ⇒ 否则下面的「≤ cap」会变成「0 ≤ cap」的空转断言（绿得没有意义）。
        assertTrue(lines > 0,
                "正向对照失败：慢请求（>" + SLOW_BUSINESS_MS + "ms）必然触发探测 ⇒ 线上必须看到 1xx 探针。"
                        + "实际 1xx 行数=" + lines + "，原始响应=" + raw);

        assertTrue(lines <= cap,
                "1xx 行数必须 ≤ 每请求预算（上界=" + cap + "）—— "
                        + "客户端对 interim(1xx) 响应有硬上限（JDK java.net.http 默认 8，"
                        + "超限抛 ProtocolException「Too many interim responses received」）"
                        + "⇒ 无上界 ⇒ **任何耗时 > ~4s 的请求都会让真实客户端读不到最终响应**。"
                        + "实测 1xx 行数=" + lines);
    }

    // ================================================================== 断言 ②

    @Test
    @Order(2)
    @DisplayName("🔴 真 Tomcat + **真 JDK HttpClient**：慢请求必须能被真实客户端读完")
    void jdkHttpClientCanStillParseTheResponse() throws Exception {
        // ★ 用**真的** java.net.http.HttpClient —— 断言的正是「真实客户端会不会摔」。
        //   自己写宽松解析器就证不出这个约束（那才是「错误答案的守卫」）。
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + PATH_SLOW))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            // ⚠️ 把客户端异常**收敛成断言失败**（§3.3：「红测试必须红在目标断言上」）
            //   —— 否则它会以「测试方法抛异常」的形式飘红，看不出是哪条判据挂了。
            throw new AssertionError("真实客户端必须能读到最终响应，但 JDK HttpClient 直接失败了：" + e
                    + "（典型成因：1xx 行数超过客户端的 interim 上限 ⇒ "
                    + "ProtocolException「Too many interim responses received: N > 8」）", e);
        }

        assertEquals(200, response.statusCode(),
                "真实客户端必须能读到最终响应（200）—— 上界必须**低于**客户端的 interim 上限。"
                        + "若抛 ProtocolException「Too many interim responses received: N > 8」"
                        + "⇒ 就是 1xx 行数超了客户端的上限。");
        assertTrue(response.body().contains("\"code\":200"),
                "最终响应 body 必须完整送达。实际=" + response.body());
    }

    // ================================================================== 断言 ③

    @Test
    @Order(3)
    @DisplayName("🔒 真 Tomcat：预算是**按请求**的（第二个请求不能被前一个吃光预算）")
    void budgetIsPerRequestNotGlobal() throws Exception {
        String first = httpGet(PATH_SLOW);
        String second = httpGet(PATH_SLOW);

        int firstLines = countInformationalResponses(first);
        int secondLines = countInformationalResponses(second);

        assertTrue(firstLines > 0 && secondLines > 0,
                "两个请求都必须各自拿到预算（正向对照）—— 实际 first=" + firstLines + " second=" + secondLines);
        assertEquals(firstLines, secondLines,
                "第二个请求必须拿到**自己的**预算 ⇒ 两个请求的 1xx 行数必须相同。"
                        + "若第二个是 0 行 ⇒ 计数器是**全局**的 ⇒ 前一个请求把后一个的断连探测能力吃光了。"
                        + "实际 first=" + firstLines + " second=" + secondLines);
    }

    // ================================================================== 工具

    /**
     * 数原始响应里的 <b>1xx 状态行</b>个数。
     *
     * <p>按行扫：每个 1xx 块恰好贡献一个形如 {@code HTTP/1.1 103 Early Hints} 的状态行
     * ⇒ 最终响应（{@code HTTP/1.1 200 ...}）不计入。
     */
    private static int countInformationalResponses(String raw) {
        int count = 0;
        for (String line : raw.split("\r\n")) {
            if (line.startsWith("HTTP/1.1 1")) {
                count++;
            }
        }
        return count;
    }

    /** 裸 socket 发请求，读到 EOF 为止（原始字节，含可能存在的 1xx 探针）。 */
    private static String httpGet(String path) throws Exception {
        try (Socket s = new Socket("127.0.0.1", port)) {
            s.setSoTimeout(30_000);
            String request = "GET " + path + " HTTP/1.1\r\nHost: 127.0.0.1:" + port
                    + "\r\nConnection: close\r\n\r\n";
            s.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            s.getOutputStream().flush();
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int n;
            while ((n = s.getInputStream().read(chunk)) > 0) {
                buf.write(chunk, 0, n);
            }
            return buf.toString(StandardCharsets.UTF_8);
        }
    }
}
