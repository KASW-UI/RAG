package com.rag.common.http;

import com.rag.common.observability.FakeOtlpCollector;
import com.rag.common.observability.GoStyleOtelAgent;
import com.rag.common.observability.GoStyleOtelConfig;
import io.opentelemetry.api.GlobalOpenTelemetry;
import org.apache.catalina.Context;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.apache.catalina.startup.Tomcat;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🔴🔴 **「同源」守门**：响应头里的 {@code Traceparent} 与<b>真正被导出到网络上的那个 span</b>
 * 必须属于<b>同一条 trace</b>。
 *
 * <h2>为什么这条要单独一个类</h2>
 * <p>它要**关停** provider 来强制 flush（否则要等 BatchSpanProcessor 的 5s 批量周期）⇒
 * 关停后全局 provider 不可再用 ⇒ 不能与其它用例同处一个类。
 *
 * <h2>🔴 它挡的是什么</h2>
 * <p>2026-10-25 之前，本 filter 自带一个<b>私有</b> provider（不配 SpanProcessor）：
 * <ul>
 *   <li>响应头里有一个<b>合法</b>的 traceparent（看着完全正常）；</li>
 *   <li>但那个 span <b>永远不会被导出</b> ⇒ jaeger 里<b>查不到</b> ⇒
 *       客户端拿着 trace-id 去 jaeger 检索 ⇒ <b>查无此 trace</b>。</li>
 * </ul>
 * ⇒ 也就是说：**响应头看着对，trace 却是断的** ⇒ 而这**恰恰不是 Go 的行为**
 * （Go 只有一个 provider：{@code rest/handler/tracehandler.go:32-64} 起的 span
 * 既写进响应头、又被 {@code core/trace/agent.go} 导出 ⇒ <b>同一条</b>）。
 *
 * <p>⇒ 本类用「假 OTLP 接收端收到的那坨字节」当**客观证据**：
 * OTLP 的 protobuf 里 trace-id 是<b>原始 16 字节</b>（不是 hex 串）⇒
 * 只要响应头里的 trace-id 的 16 字节出现在导出字节里 ⇒ <b>它们是同一条 trace</b>。
 */
class GoStyleTraceparentFilterTraceSourceGateTest {

    private static Tomcat tomcat;
    private static int port;
    private static FakeOtlpCollector collector;

    @BeforeAll
    static void startEverything() throws Exception {
        GlobalOpenTelemetry.resetForTest();
        GoStyleOtelAgent.shutdown();

        collector = new FakeOtlpCollector();

        File base = new File(System.getProperty("java.io.tmpdir"), "rag-common-traceparent-source-gate");
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
        // 被测的 filter —— 生产的那个类
        FilterDef def = new FilterDef();
        def.setFilterName("goStyleTraceparent");
        def.setFilter(new GoStyleTraceparentFilter());
        def.setAsyncSupported("false");
        ctx.addFilterDef(def);
        FilterMap map = new FilterMap();
        map.setFilterName("goStyleTraceparent");
        map.addURLPattern("/*");
        ctx.addFilterMap(map);
        tomcat.start();
        port = tomcat.getConnector().getLocalPort();

        // 装配全局 provider，导出指向**假 OTLP 接收端**
        GoStyleOtelAgent.install(
                new GoStyleOtelConfig("common-traceparent-source-gate", collector.endpoint(), "grpc", 1.0d));
    }

    @AfterAll
    static void stopEverything() {
        if (tomcat != null) {
            try {
                tomcat.stop();
                tomcat.destroy();
            } catch (Exception ignored) {
                // 收尾失败不影响判定
            }
        }
        GoStyleOtelAgent.shutdown();
        GlobalOpenTelemetry.resetForTest();
        if (collector != null) {
            collector.close();
        }
    }

    @Test
    @DisplayName("🔴 真 Tomcat + 真 OTLP 接收端：响应头的 Traceparent 与**真正导出的 span** 同一条 trace")
    void responseTraceparentBelongsToTheExportedSpan() throws Exception {
        String raw = rawGet("/ok");
        String header = traceparentOf(raw);
        assertThat(header)
                .as("响应里必须有 Traceparent（Go: tracehandler.go:61 propagator.Inject）。原始响应=\n" + raw)
                .isNotNull();
        Matcher m = Pattern.compile("^00-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})$").matcher(header);
        assertThat(m.matches()).as("Traceparent 形状必须是 00-<32hex>-<16hex>-<flags>。实际=" + header).isTrue();
        String traceIdHex = m.group(1);

        // 关停 ⇒ flush（Go: core/trace/agent.go:55-62 tp.Shutdown ⇒ flush 未导出的 span）
        GoStyleOtelAgent.shutdown();

        // 等导出到达（关停是同步 flush ⇒ 通常立即到 ⇒ 给 3s 余量）
        long deadline = System.currentTimeMillis() + 3_000L;
        while (collector.requestCount() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertThat(collector.requestCount())
                .as("server span 必须真的被导出（Go: core/trace/agent.go:114-121 装 batcher）")
                .isGreaterThanOrEqualTo(1);

        // 🔴 关键断言：导出的字节里必须含**响应头那个 trace-id 的 16 个原始字节**
        //    （OTLP protobuf 里 trace-id 是原始字节，不是 hex 串）
        byte[] traceIdBytes = hexToBytes(traceIdHex);
        assertThat(containsBytes(collector.receivedBytesBlob(), traceIdBytes))
                .as("响应头的 trace-id=" + traceIdHex + " 必须出现在**真正导出**的 span 里 ⇒ 同一条 trace。"
                        + "若这条红了，说明响应头与导出物**不同源**（那正是私有 provider 形态的症状）。")
                .isTrue();
    }

    // ==================================================================
    // 辅助
    // ==================================================================

    private static String traceparentOf(String raw) {
        Matcher m = Pattern.compile("(?im)^traceparent:\\s*(\\S+)\\s*$").matcher(raw);
        return m.find() ? m.group(1) : null;
    }

    private static byte[] hexToBytes(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private static boolean containsBytes(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= haystack.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

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
