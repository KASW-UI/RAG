package com.rag.common.http;

import org.apache.catalina.Context;
import com.rag.common.observability.GoStyleOtelAgent;
import com.rag.common.observability.GoStyleOtelConfig;
import io.opentelemetry.api.GlobalOpenTelemetry;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 <b>T-8 守门</b>：HTTP 响应必须带 <b>W3C {@code Traceparent}</b>，且带的是
 * <b>「当前 server span」</b> 而不是入向 parent 的回显。
 *
 * <h2>Go 侧事实（真源）</h2>
 * <pre>
 * go-zero@v1.10.2/rest/handler/tracehandler.go:47-63
 *   ctx := propagator.Extract(r.Context(), propagation.HeaderCarrier(r.Header))
 *   spanCtx, span := tracer.Start(ctx, spanName, …, SpanKindServer, …)
 *   propagator.Inject(spanCtx, propagation.HeaderCarrier(w.Header()))   // ← 响应头
 *   next.ServeHTTP(trw, r.WithContext(spanCtx))
 *
 * go-zero@v1.10.2/rest/engine.go:134-138 / :246-251
 *   Middlewares.Trace 默认 true ⇒ 每个路由都挂；notFoundHandler 也挂 ⇒ 404 也带
 * </pre>
 *
 * <h2>为什么必须用<b>真 Tomcat + 裸 socket</b>（§3.3）</h2>
 * <ul>
 *   <li><b>裸 socket</b>：要断言的是<b>响应头在 wire 上的字节</b>
 *       —— 头名大小写（{@code Traceparent} vs {@code traceparent}）只有看原始字节才知道；</li>
 *   <li><b>真 Tomcat</b>：{@code MockMvc} / {@code MockHttpServletResponse} 的头处理
 *       与容器不同（且 MockMvc 不会真的走 Filter 链的顺序）。</li>
 * </ul>
 *
 * <h2>本类断言的<b>四件事</b>（每件都对着 Go 的一行）</h2>
 * <ol>
 *   <li>响应<b>带</b> {@code Traceparent}，值形如 {@code 00-<32hex>-<16hex>-01}；</li>
 *   <li><b>trace-id 沿用入向</b>（server span 是入向 context 的子 span）;</li>
 *   <li><b>span-id 与入向不同</b>（写出去的是 server span 自己，<b>不是回显</b>）;</li>
 *   <li><b>采样位随入向</b>（Go = {@code ParentBased(TraceIDRatioBased(1.0))}
 *       ⇒ 入向 sampled=0 ⇒ 子 span sampled=0 ⇒ flags {@code 00}）。</li>
 * </ol>
 */
class GoStyleTraceparentFilterTest {

    private static Tomcat tomcat;
    private static int port;

    /** 一个合法的入向 traceparent（trace-id 全 1，span-id 全 2，采样位 1）。 */
    private static final String INBOUND_TRACE_ID = "11111111111111111111111111111111";
    private static final String INBOUND_SPAN_ID = "2222222222222222";

    @BeforeAll
    static void startTomcat() throws Exception {
        // 🔴 2026-10-25（第二十一轮 · global T-11）：本类现在**依赖全局 provider**
        //    （filter 改用 GlobalOpenTelemetry.get().getTracer(...) 起 server span）。
        //    ⇒ 必须在这里**显式装配**，否则本类会变成「顺序依赖的假绿」——
        //    实测踩过：整套 service/common 跑下来是绿的，但只是因为**别的测试类**
        //    恰好先装过全局 provider ⇒ 单跑本类就会红。
        //    ⇒ 装的是**生产装配代码**（GoStyleOtelAgent），不是仿写 ⇒ 顺带覆盖了
        //    「全局 sampler = ParentBased(TraceIDRatioBased(1.0))」这条 Go 语义。
        GlobalOpenTelemetry.resetForTest();
        GoStyleOtelAgent.shutdown();
        GoStyleOtelAgent.install(new GoStyleOtelConfig("common-traceparent-gate", null, "grpc", 1.0d));

        File base = new File(System.getProperty("java.io.tmpdir"), "rag-common-traceparent-test");
        base.mkdirs();
        tomcat = new Tomcat();
        tomcat.setBaseDir(base.getAbsolutePath());
        tomcat.setPort(0);
        tomcat.getConnector();
        Context ctx = tomcat.addContext("", base.getAbsolutePath());

        Tomcat.addServlet(ctx, "ok", new jakarta.servlet.http.HttpServlet() {
            @Override
            protected void doGet(jakarta.servlet.http.HttpServletRequest req,
                                 jakarta.servlet.http.HttpServletResponse resp) throws java.io.IOException {
                resp.setContentType("application/json");
                resp.getWriter().write("{\"code\":200,\"msg\":\"ok\"}");
            }
        });
        ctx.addServletMappingDecoded("/ok", "ok");

        // 应用级 404：**在 filter 链内**由业务 servlet 自己返回 404
        //（Go 侧对应 go-zero 的 notFoundHandler —— 它也是链内的一环）。
        Tomcat.addServlet(ctx, "notfound", new jakarta.servlet.http.HttpServlet() {
            @Override
            protected void doGet(jakarta.servlet.http.HttpServletRequest req,
                                 jakarta.servlet.http.HttpServletResponse resp) throws java.io.IOException {
                resp.setStatus(404);
                resp.setContentType("application/json");
                resp.getWriter().write("{\"code\":404,\"msg\":\"not found\"}");
            }
        });
        ctx.addServletMappingDecoded("/notfound", "notfound");

        // 被测的 filter —— **生产的那个类**，不是仿写。
        addFilter(ctx, "goStyleTraceparent", new GoStyleTraceparentFilter());

        tomcat.start();
        port = tomcat.getConnector().getLocalPort();
    }

    @AfterAll
    static void stopTomcat() {
        if (tomcat != null) {
            try {
                tomcat.stop();
                tomcat.destroy();
            } catch (Exception ignored) {
                // 收尾失败不影响判定
            }
        }
        // 🔴 还原全局：GlobalOpenTelemetry 全局只能设一次 ⇒ 不还原会污染同 JVM 的其它测试类。
        GoStyleOtelAgent.shutdown();
        GlobalOpenTelemetry.resetForTest();
    }

    private static void addFilter(Context ctx, String name, jakarta.servlet.Filter filter) {
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

    // ==================================================================
    // ① 入向带 traceparent ⇒ 响应带的是「server span」，不是回显
    // ==================================================================

    @Test
    @DisplayName("🔴 真 Tomcat：响应带 Traceparent，且是 server span（trace-id 沿用入向、span-id 换新）")
    void responseCarriesServerSpanTraceparent() throws Exception {
        String inbound = "00-" + INBOUND_TRACE_ID + "-" + INBOUND_SPAN_ID + "-01";
        String raw = rawGet("/ok", "Traceparent: " + inbound);

        String header = traceparentOf(raw);
        assertNotNull(header, "响应里必须有 Traceparent（Go: tracehandler.go:61 propagator.Inject）。原始响应=\n" + raw);

        Matcher m = Pattern.compile("^00-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})$").matcher(header);
        assertTrue(m.matches(),
                "Traceparent 的形状必须是 00-<32hex traceid>-<16hex spanid>-<flags>。实际=" + header);

        assertEquals(INBOUND_TRACE_ID, m.group(1),
                "trace-id 必须**沿用入向**（Go: Extract → Start ⇒ server span 是入向的子 span）。实际=" + header);
        assertNotEquals(INBOUND_SPAN_ID, m.group(2),
                "span-id 必须**不同于**入向 ⇒ 证明写出去的是 server span 自己，"
                        + "**不是**把入向头回显一遍（回显是最容易写错、且看起来完全正常的一种实现）。实际=" + header);
        assertEquals("01", m.group(3),
                "采样位必须是 01（Go: Sampler=1.0 ⇒ ParentBased(TraceIDRatioBased(1.0)) ⇒ 入向已采样 ⇒ 子 span 采样）。实际=" + header);
    }

    // ==================================================================
    // ② 无入向 ⇒ 也要有（Go 每个响应都带）
    // ==================================================================

    @Test
    @DisplayName("✅ 真 Tomcat：没有入向 traceparent 时，响应照样带一个合法的 Traceparent")
    void responseCarriesFreshTraceparentWhenNoInbound() throws Exception {
        String raw = rawGet("/ok");

        String header = traceparentOf(raw);
        assertNotNull(header, "Go 的 TraceHandler 对**每个**响应都注入 ⇒ 没有入向也必须有。原始响应=\n" + raw);
        assertTrue(Pattern.matches("^00-[0-9a-f]{32}-[0-9a-f]{16}-01$", header),
                "无入向时必须自己生成一个合法的 traceparent。实际=" + header);
        assertNotEquals("00000000000000000000000000000000", header.substring(3, 35),
                "trace-id 不能是全 0（那是 INVALID 的 SpanContext）。实际=" + header);
    }

    // ==================================================================
    // ③ 404 也要带（Go: notFoundHandler 也挂 TraceHandler）
    // ==================================================================

    @Test
    @DisplayName("✅ 真 Tomcat：404 响应也带 Traceparent（Go: TraceHandler 在业务之前注入 ⇒ 与状态码无关）")
    void errorStatusResponseAlsoCarriesTraceparent() throws Exception {
        String raw = rawGet("/notfound");
        assertTrue(raw.startsWith("HTTP/1.1 404"), "本用例要的是一个真的 404。实际=\n" + raw);
        assertNotNull(traceparentOf(raw),
                "Go: propagator.Inject 在 next.ServeHTTP **之前** ⇒ 任何状态码都带。"
                        + "原始响应=\n" + raw);
    }

    /**
     * ⚠️ <b>边界（不是缺陷）</b>：<b>容器级</b> 404（Tomcat 的 error valve 生成的错误页）
     * <b>不经过 filter 链</b> ⇒ 任何 filter 都加不上头。
     *
     * <p>这<b>不构成</b>与 Go 的漂移：Go 侧的 404 由 go-zero 的 {@code notFoundHandler}
     * 生成，而它<b>是链内的一环</b>（{@code engine.go:246-251}）⇒ Go 的 404 <b>带</b> traceparent。
     * ⇒ RAG 侧对应的形态是「Spring DispatcherServlet → {@code BasicErrorController}」，
     * 那<b>在</b> filter 链内 ⇒ 同样带。
     * ⇒ 本类只把**应用级** 404（业务 servlet 自己返回）纳入断言，因为那是链内的形态。
     */
    @Test
    @DisplayName("🔒 边界：容器级 404（error valve）不经过 filter 链 —— 与 Go 不构成漂移，但必须写清")
    void containerLevelNotFoundIsOutOfFilterChain() throws Exception {
        String raw = rawGet("/no-such-path");
        assertTrue(raw.startsWith("HTTP/1.1 404"), "本用例要的是一个真的 404。实际=\n" + raw);
        // ⚠️ 这里**断言没有** Traceparent —— 因为 Tomcat 的 error valve 在 filter 链之外。
        //    若哪天这条红了，说明 Tomcat 改了错误页生成位置（那才需要重新判定）。
        assertNull(traceparentOf(raw),
                "容器级 404 由 Tomcat 的 error valve 生成 ⇒ 不经过 filter 链 ⇒ 不该有 Traceparent。"
                        + "若这条红了，说明容器行为变了，需要重新判定（但**不是**与 Go 的漂移："
                        + "Go 的 404 是链内 notFoundHandler 生成的）。原始响应=\n" + raw);
    }

    // ==================================================================
    // ④ 采样位随入向（ParentBased 语义）
    // ==================================================================

    @Test
    @DisplayName("🔒 真 Tomcat：入向 sampled=0 ⇒ 响应 flags 必须是 00（Go 的 ParentBased 语义）")
    void sampledFlagFollowsInboundParent() throws Exception {
        String inbound = "00-" + INBOUND_TRACE_ID + "-" + INBOUND_SPAN_ID + "-00";
        String raw = rawGet("/ok", "Traceparent: " + inbound);

        String header = traceparentOf(raw);
        assertNotNull(header, "原始响应=\n" + raw);
        assertTrue(header.endsWith("-00"),
                "Go: ParentBased(TraceIDRatioBased(1.0)) ⇒ 入向不采样 ⇒ 子 span 也不采样 ⇒ flags 必须是 00。"
                        + "实际=" + header);
        assertEquals(INBOUND_TRACE_ID, header.substring(3, 35),
                "即便不采样，trace-id 仍沿用入向。实际=" + header);
    }

    // ==================================================================
    // ⑤ 头名大小写：必须与 Go 的 canonical 形式一致
    // ==================================================================

    @Test
    @DisplayName("🔒 真 Tomcat：头名必须是 Traceparent（Go 的 textproto canonical 形式），不是 traceparent")
    void headerNameIsCanonicalizedLikeGo() throws Exception {
        String raw = rawGet("/ok");
        assertTrue(raw.contains("\r\nTraceparent: "),
                "Go 的 propagation.HeaderCarrier.Set 走 textproto.MIMEHeader.Set ⇒ 键被 canonical 成 "
                        + "『Traceparent』；Tomcat 原样发出 ⇒ 本类必须主动 canonical 化，否则 wire 上是 "
                        + "小写 traceparent ⇒ 与 Go 字节级不一致。原始响应=\n" + raw);
        assertTrue(!raw.contains("\r\ntraceparent: "),
                "不该出现小写的 traceparent（与 Go 字节级不一致）。原始响应=\n" + raw);
    }

    // ==================================================================
    // 辅助：裸 socket 发请求，读原始响应字节
    // ==================================================================

    /** 从原始响应里取出 {@code Traceparent} 的值（大小写不敏感地找，取到的值保持原样）。 */
    private static String traceparentOf(String raw) {
        Matcher m = Pattern.compile("(?im)^traceparent:\\s*(\\S+)\\s*$").matcher(raw);
        return m.find() ? m.group(1) : null;
    }

    /** 裸 socket 发一个 GET，返回**原始响应文本**（含状态行与全部响应头）。 */
    private static String rawGet(String path, String... extraHeaders) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(10_000);
            StringBuilder req = new StringBuilder();
            req.append("GET ").append(path).append(" HTTP/1.1\r\n");
            req.append("Host: 127.0.0.1:").append(port).append("\r\n");
            for (String h : extraHeaders) {
                req.append(h).append("\r\n");
            }
            req.append("Connection: close\r\n\r\n");
            OutputStream os = socket.getOutputStream();
            os.write(req.toString().getBytes(StandardCharsets.UTF_8));
            os.flush();

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\r\n");
                }
            }
            return sb.toString();
        }
    }
}
