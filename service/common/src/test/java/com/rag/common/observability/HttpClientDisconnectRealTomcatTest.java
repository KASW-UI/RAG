package com.rag.common.observability;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
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
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>T-4 / DRIFT-HOTAPI-025 守门：客户端断连必须被记成 <b>499</b>，不能是 200。</b>
 *
 * <h3>Go 侧的判据（真源，consumption point）</h3>
 * <pre>
 *   Sea-RideTheWind_golang/service/common/observability/http.go:80-88
 *       case &lt;-ctx.Done():
 *           statusCode := http.StatusServiceUnavailable
 *           if errors.Is(ctx.Err(), context.Canceled) { statusCode = httpClientClosedRequest }
 *           MarkTimeout(ctx, ctx.Err(), duration, httpAttrs(serviceName, req, statusCode)...)
 *           http.Error(w, httpTimeoutBody, statusCode)
 *   ⇒ Go 的 499 由 **ctx 被取消** 驱动；net/http 在**客户端断开**时取消 r.Context()
 *   ⇒ 断连 ⇒ 必定 499（记录里 http.status_code=499）
 * </pre>
 *
 * <h3>为什么必须跑在<b>真 Tomcat</b>上（§3.3）</h3>
 * <p>本模块要守的机制是「<b>往 socket 写响应失败</b>」。
 * {@code MockHttpServletResponse} / MockMvc 的 {@code write()} <b>永远不失败</b>
 * ⇒ 断连分支<b>根本触发不了</b> ⇒ 那种测试是「错误答案的守卫」，比没有测试更危险。
 * <p>⇒ 这里用<b>嵌入式真 Tomcat</b>（{@code tomcat-embed-core}，test 作用域）
 * + <b>真</b> {@link HttpMiddleware}（不是自己 new 一个假 filter）。
 *
 * <h3>断言三件事</h3>
 * <ol>
 *   <li><b>span 的 http.status_code == 499</b>（Go 的等价物：{@code MarkTimeout} + {@code httpAttrs(..., 499)}）
 *       —— 断连事件必须<b>可观测</b>；旧实现留的是 200。</li>
 *   <li><b>日志里有 {@code [HTTP] 499}</b> —— Go 侧 499 之所以可见是因为 go-zero 的 access log；
 *       RAG 没有 access log ⇒ 若只写 span，没接 trace 后端时事件依然不可见。</li>
 *   <li><b>响应 framing 不许变</b> —— 499 的实现要提前 flush 响应（否则写不到 socket、断连观测不到），
 *       而提前提交会把 {@code Content-Length} 退化成 {@code Transfer-Encoding: chunked}
 *       ⇒ 那就是<b>新增的 wire 差异</b>（Go 是 Content-Length）。这条把 framing 钉死。</li>
 * </ol>
 */
class HttpClientDisconnectRealTomcatTest {

    /** 与 Go 侧 {@code httpClientClosedRequest} 同值。 */
    private static final int HTTP_CLIENT_CLOSED_REQUEST = 499;

    private static final String PATH = "/slow";
    private static final String PATH_EMPTY = "/empty";
    private static final String PATH_NO_CONTENT = "/nocontent";

    private static Tomcat tomcat;
    private static int port;
    private static InMemorySpanExporter spanExporter;
    private static Tracer tracer;
    private static ListAppender<ILoggingEvent> logAppender;

    /** 业务线程睡多久 —— 必须 > 客户端 RST 的时刻，RAG 才可能在写响应时才发现断连。 */
    private static final long BUSINESS_SLEEP_MS = 700L;

    /** 客户端在请求发出后多久 RST。 */
    private static final int RST_AFTER_MS = 150;

    @BeforeAll
    static void startTomcat() throws Exception {
        File base = new File(System.getProperty("java.io.tmpdir"), "rag-common-disconnect-test");
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
                    Thread.sleep(BUSINESS_SLEEP_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                resp.getWriter().write("{\"code\":200,\"msg\":\"ok\"}");
            }
        });
        ctx.addServletMappingDecoded(PATH, "slow");

        Tomcat.addServlet(ctx, "empty", new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
                // 空 body（Go 对空响应体也是 Content-Length: 0）
            }
        });
        ctx.addServletMappingDecoded(PATH_EMPTY, "empty");

        Tomcat.addServlet(ctx, "big", new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                // ⚠️ 大于 Tomcat 响应缓冲（8192）⇒ 溢出部分会**直写 socket** ⇒ 基线本来就是 chunked
                resp.getWriter().write("x".repeat(20000));
            }
        });
        ctx.addServletMappingDecoded("/big", "big");

        Tomcat.addServlet(ctx, "nocontent", new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
                resp.setStatus(204);
            }
        });
        ctx.addServletMappingDecoded(PATH_NO_CONTENT, "nocontent");

        // ① 追踪 filter：把一个 **recording span** 挂到容器线程的 Context 上。
        //    HttpMiddleware 的打标（markHttpResult / markTimeout）跑在**容器线程**，
        //    用的是 Context.current() ⇒ 必须由本 filter 在容器线程上 makeCurrent。
        spanExporter = InMemorySpanExporter.create();
        SdkTracerProvider provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
        tracer = provider.get("rag-common-disconnect-test");

        addFilter(ctx, "tracing", new Filter() {
            @Override
            public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
                    throws IOException, ServletException {
                Span span = tracer.spanBuilder("test-http").startSpan();
                try (Scope scope = span.makeCurrent()) {
                    chain.doFilter(req, resp);
                } finally {
                    span.end();
                }
            }
        });

        // ② **真** HttpMiddleware（不是仿写）—— 断连语义就住在它里面。
        addFilter(ctx, "httpMiddleware",
                HttpMiddleware.newHttpMiddleware("test-svc", Duration.ofSeconds(5), Duration.ofSeconds(1)));

        tomcat.start();
        port = tomcat.getConnector().getLocalPort();

        logAppender = new ListAppender<>();
        logAppender.start();
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(HttpMiddleware.class)).addAppender(logAppender);
    }

    @AfterAll
    static void stopTomcat() {
        if (logAppender != null) {
            logAppender.stop();
        }
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

    @Test
    @DisplayName("🔴 真 Tomcat：客户端 RST 断连 ⇒ 记录 499（不是 200），且日志可见")
    void clientDisconnectIsRecordedAs499() throws Exception {
        spanExporter.reset();
        logAppender.list.clear();

        sendThenRst(PATH);

        SpanData span = awaitSpan();
        assertNotNull(span, "没拿到 span —— 说明请求根本没走完，测试失去意义");

        Long status = span.getAttributes().get(AttributeKey.longKey("http.status_code"));
        assertEquals((long) HTTP_CLIENT_CLOSED_REQUEST, status,
                "Go: ctx 被取消（客户端断开）⇒ MarkTimeout(..., httpAttrs(..., 499)) ⇒ http.status_code 必须是 499。"
                        + "实际=" + status + "（200 = 断连事件完全不可见，正是 DRIFT-HOTAPI-025 记的老问题）");

        assertTrue(logAppender.list.stream().anyMatch(e -> e.getFormattedMessage().contains("[HTTP] 499")),
                "499 必须有一条日志（Go 侧靠 go-zero 的 access log 可见；RAG 没有 access log ⇒"
                        + "只写 span 的话，没接 trace 后端时事件依然不可见）。日志=" + logAppender.list);
    }

    @Test
    @DisplayName("✅ 真 Tomcat：客户端不断连 ⇒ 记录 200（证明上一例确实是断连触发的）")
    void normalRequestIsRecordedAs200() throws Exception {
        spanExporter.reset();
        logAppender.list.clear();

        String body = httpGet(PATH);
        assertTrue(body.contains("200"), "正常请求必须拿到业务响应。实际=" + body);

        SpanData span = awaitSpan();
        assertNotNull(span, "没拿到 span");
        Long status = span.getAttributes().get(AttributeKey.longKey("http.status_code"));
        assertEquals(200L, status,
                "同一条路径、同一个 filter，只是不 RST ⇒ 必须是 200；否则上一例证明不了「是断连触发的」。实际=" + status);
        assertTrue(logAppender.list.stream().noneMatch(e -> e.getFormattedMessage().contains("[HTTP] 499")),
                "没有断连就不该有 499 日志。日志=" + logAppender.list);
    }

    @Test
    @DisplayName("🔒 真 Tomcat：framing 必须是 Content-Length（499 靠提前 flush 实现，别把它改成 chunked）")
    void responseFramingStaysContentLength() throws Exception {
        // ① 有 body
        String slow = httpGet(PATH);
        assertFalse(slow.contains("Transfer-Encoding"),
                "Go 的 net/http 对装得下的响应体算 Content-Length；RAG 提前 flush（为了观测断连）"
                        + "也不能退化成 chunked —— 那就是新增的 wire 差异。实际响应=" + slow);
        assertTrue(slow.contains("Content-Length:"), "必须带 Content-Length。实际响应=" + slow);
        int declared = Integer.parseInt(slow.replaceAll("(?s).*Content-Length:\\s*(\\d+).*", "$1").trim());
        String body = slow.substring(slow.indexOf("\r\n\r\n") + 4);
        assertEquals(body.getBytes(StandardCharsets.UTF_8).length, declared,
                "Content-Length 必须等于实际 body 字节数。响应=" + slow);

        // ② 空 body（Go：Content-Length: 0）
        String empty = httpGet(PATH_EMPTY);
        assertFalse(empty.contains("Transfer-Encoding"),
                "空 body 也必须是 Content-Length: 0，不是 chunked。实际响应=" + empty);
        assertTrue(empty.contains("Content-Length: 0"), "空 body 必须是 Content-Length: 0。实际响应=" + empty);

        // ③ 204：按 HTTP 语义不带 body ⇒ 也不带 Content-Length
        String nc = httpGet(PATH_NO_CONTENT);
        assertTrue(nc.startsWith("HTTP/1.1 204"), "必须是 204。实际响应=" + nc);
        assertFalse(nc.contains("Content-Length"),
                "204 按 HTTP 语义不带 body ⇒ 也不带 Content-Length（Go 同样不发）。实际响应=" + nc);
        assertFalse(nc.contains("Transfer-Encoding"), "204 不该有 Transfer-Encoding。实际响应=" + nc);

        // ④ 大 body（20000 字节 > Tomcat 响应缓冲 8192）
        //    ⇒ 溢出部分**直写 socket** ⇒ 响应在写完前就被提交 ⇒ **基线本来就是 chunked**
        //    ⇒ 这条钉住「我们的 8192 阈值没有把大 body 的 framing 改掉」
        String big = httpGet("/big");
        assertFalse(big.contains("Content-Length"),
                "大 body（> Tomcat 响应缓冲 8192）在基线里就是 chunked（写的时候已提交）⇒ "
                        + "我们不得改成 Content-Length，那才是新的 wire 差异。实际响应头=" + headOf(big));
        assertTrue(big.contains("Transfer-Encoding: chunked"),
                "大 body 必须是 chunked（与基线同形）。实际响应头=" + headOf(big));
        assertTrue(big.chars().filter(c -> c == 'x').count() == 20000L,
                "大 body 必须完整送达（20000 字节）。实际长度=" + big.chars().filter(c -> c == 'x').count());
    }

    // ------------------------------------------------------------------ 工具

    private SpanData awaitSpan() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000L;
        while (System.currentTimeMillis() < deadline) {
            List<SpanData> spans = spanExporter.getFinishedSpanItems();
            for (SpanData s : spans) {
                if ("test-http".equals(s.getName())) {
                    return s;
                }
            }
            Thread.sleep(50L);
        }
        return null;
    }

    /** 裸 socket 发请求，然后 {@code SO_LINGER{1,0}} 强制 RST（最粗暴的断连）。 */
    private void sendThenRst(String path) throws Exception {
        Socket s = new Socket("127.0.0.1", port);
        try {
            s.setSoTimeout(5_000);
            String req = "GET " + path + " HTTP/1.1\r\nHost: 127.0.0.1:" + port + "\r\nConnection: close\r\n\r\n";
            s.getOutputStream().write(req.getBytes(StandardCharsets.US_ASCII));
            s.getOutputStream().flush();
            Thread.sleep(RST_AFTER_MS);
            s.setSoLinger(true, 0);
            s.close();
        } finally {
            try {
                s.close();
            } catch (IOException ignored) {
                // 已经关了
            }
        }
    }

    /** 只取响应头部分（便于断言信息可读）。 */
    private static String headOf(String raw) {
        int i = raw.indexOf("\r\n\r\n");
        return i < 0 ? raw : raw.substring(0, i);
    }

    private String httpGet(String path) throws Exception {
        try (Socket s = new Socket("127.0.0.1", port)) {
            s.setSoTimeout(15_000);
            String req = "GET " + path + " HTTP/1.1\r\nHost: 127.0.0.1:" + port + "\r\nConnection: close\r\n\r\n";
            s.getOutputStream().write(req.getBytes(StandardCharsets.US_ASCII));
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
