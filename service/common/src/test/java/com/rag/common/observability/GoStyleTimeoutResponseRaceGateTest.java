package com.rag.common.observability;

import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.Context;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🔴🔴 **超时路径的「响应撕裂」守门**：HTTP 超时后，客户端必须拿到<b>一个完整、干净</b>的
 * {@code 503 + "Request Timeout\n"}。
 *
 * <h2>它挡的是什么（2026-10-25 全仓回归实测抓到）</h2>
 * <p>全仓回归里唯一一条红：{@code MessageApiTimeoutRealTomcatTest.slowDownstreamYieldsGoTimeoutContract}
 * ⇒ 客户端拿到 {@code java.io.IOException: HTTP/1.1 header parser received no bytes}
 * ⇒ 服务端日志显示 Tomcat 抛
 * {@code NullPointerException: Cannot invoke "MimeHeaderField.getName()" because "this.headers[i]" is null}。
 *
 * <h2>🔴 本门在「修前」的实测复现率：<b>200/200（100%）</b></h2>
 * <p>生产里的真实窗口<b>窄得多</b>（Spring 的异常解析链比本门的裸 servlet 慢 ⇒ 容器线程通常先把 503 写完）
 * ⇒ 生产表现为<b>偶发</b>（全仓 2217 条里碰到 1 次）。
 * <p>本门<b>刻意</b>把 worker 的晚写提到<b>最早</b>（最坏时序）⇒ 缺陷形态 100% 复现
 * ⇒ 好处是「守门红 / 绿」是<b>确定性</b>的，不靠运气。
 *
 * <h2>🔴 归因（已做 A/B 对拍）</h2>
 * <p>本门在 <b>{@code aac691c}（第二十轮收口 = T-11 全局 OpenTelemetry 之前）</b>上跑，
 * <b>同样 200/200 全坏</b> ⇒ ⇒ 该缺陷<b>早于</b> T-11 ⇒ <b>不是 T-11 引入的</b>。
 *
 * <h3>根因（已逐行取证）</h3>
 * <ol>
 *   <li>{@link HttpMiddleware} 把业务链丢进 worker 线程跑，容器线程 {@code await(timeout)}；</li>
 *   <li>超时 ⇒ 容器线程 <b>interrupt worker</b> ⇒ worker 的业务抛 {@code InterruptedException}
 *       ⇒ Spring 的 {@code @ExceptionHandler} 接手 ⇒ 它把错误响应写回 Servlet 响应 ⇒
 *       <b>最终会调 {@code flushBuffer()}</b>；</li>
 *   <li>与此同时，容器线程<b>同时</b>在写 503（{@code setContentType} + {@code setHeader}
 *       + {@code getWriter().flush()} ⇒ 触发 Tomcat commit）。</li>
 * </ol>
 * ⇒ <b>两个线程同时改同一个响应的头</b> ⇒ Tomcat 11 的
 * {@code MimeHeaders.createHeader()}（{@code tomcat-embed-core-11.0.24}）：
 * <pre>
 *   if ((mh = headers[count]) == null) { headers[count] = mh = new MimeHeaderField(); }
 *   count++;
 * </pre>
 * ⇒ 两个线程各自 {@code count++} 却只填了一个槽位 ⇒ <b>数组里留下 null 洞</b>
 * ⇒ 随后任何线程读头（{@code Http11Processor.prepareResponse} 读 {@code "Date"}）
 * ⇒ {@code headers[i].getName()} ⇒ <b>NPE</b> ⇒ 响应根本没写出去 ⇒ 客户端只看到 EOF。
 *
 * <h3>为什么 Go 侧不会有这个问题（这才是判定依据）</h3>
 * <p><b>真源 = 本项目自己的中间件</b>（<b>不是</b> go-zero 的原生超时中间件 ——
 * Go 侧 {@code observability.DisableNativeRestTimeout} 把它关掉了）：
 * <pre>
 *   Sea-RideTheWind_golang/service/common/observability/http.go
 *     case &lt;-ctx.Done():
 *         rec.markTimedOut()                       // ← 先把业务写封掉
 *         statusCode := http.StatusServiceUnavailable
 *         if errors.Is(ctx.Err(), context.Canceled) { statusCode = httpClientClosedRequest }
 *         MarkTimeout(ctx, ctx.Err(), duration, httpAttrs(serviceName, req, statusCode)...)
 *         http.Error(w, httpTimeoutBody, statusCode)   // ← 写**原始** w
 *
 *     type observabilityResponseWriter struct {
 *         header http.Header
 *         body   bytes.Buffer
 *         status int
 *         mu       sync.Mutex
 *         timedOut bool
 *     }
 *     func (w *observabilityResponseWriter) Write(data []byte) (int, error) {
 *         w.mu.Lock(); defer w.mu.Unlock()
 *         if w.timedOut { return 0, http.ErrHandlerTimeout }   // ← 超时后**丢弃**业务写
 *         return w.body.Write(data)
 *     }
 * </pre>
 * ⇒ Go 的 writer 是<b>纯 buffer</b>：{@code Write}/{@code WriteHeader} <b>从不碰</b>底层
 * {@code http.ResponseWriter}；唯一落 socket 的地方是 {@code flushTo(dst)}，
 * 而它<b>只在 done 分支</b>被调用 ⇒ ⇒ <b>socket 的写者永远只有一个</b> ⇒ 超时响应必然完整。
 * ⇒ RAG 侧的 {@code ObservabilityResponseWriter} <b>没有</b>覆写 {@code flushBuffer()}
 * ⇒ worker 能提交响应 ⇒ 两个写者 ⇒ 既是<b>撕裂 / 状态码被抢</b>（行为漂移），
 * 也是 <b>NPE 的直接成因</b>。
 *
 * <h3>本门怎么复现（不是靠运气）</h3>
 * <ul>
 *   <li><b>真 Tomcat</b>（{@code tomcat-embed-core}）+ <b>生产用的那个 {@link HttpMiddleware}</b>
 *       （{@code newHttpMiddleware(...)}，不是 mock）；</li>
 *   <li>超时阈值取 <b>30ms</b>（生产是 3s）⇒ 一轮只要几十毫秒 ⇒ 可以<b>多轮</b>；</li>
 *   <li>Servlet 被 interrupt 后<b>刻意复刻生产里 {@code @ExceptionHandler} 的那次晚写</b>
 *       （{@code setHeader} + 写 body + {@code flushBuffer()}）⇒ 把「两个线程同时改头」这个
 *       窗口顶到最大；</li>
 *   <li>客户端用<b>裸 socket</b>（不是 HttpClient）⇒ 才能看见「一个字节都没收到」这种撕裂。</li>
 * </ul>
 *
 * <h3>断言</h3>
 * <ul>
 *   <li>状态行必须是 {@code HTTP/1.1 503}（撕裂时会是 500 / EOF / 半截响应）；</li>
 *   <li>body 必须<b>恰好</b>是 {@code "Request Timeout\n"}（Go: {@code http.Error} 写 body + "\n"）；</li>
 *   <li><b>不能出现</b> worker 晚写留下的 {@code X-Late-Write} 头
 *       （Go 的 {@code timeoutWriter} 在超时后丢弃业务写 ⇒ 客户端看不到）；</li>
 *   <li>必须带 {@code X-Content-Type-Options: nosniff}（Go {@code http.Error} 自带）。</li>
 * </ul>
 */
class GoStyleTimeoutResponseRaceGateTest {

    /** 超时阈值：生产是 3s，这里压到 30ms —— 只影响「多久触发」，不影响「触发后发生什么」。 */
    private static final Duration TIMEOUT = Duration.ofMillis(30);

    /** Servlet 的睡眠时长：必须远大于 TIMEOUT，保证每次都走超时分支。 */
    private static final long SLOW_MILLIS = 5_000L;

    /**
     * 轮数：默认 200。
     *
     * <p>可用 {@code -Drag.timeoutRace.rounds=N} 调大（压测时用）。
     * 之所以要多轮：这个缺陷是<b>竞态</b>，单轮命中与否是概率事件
     * ⇒ 守门必须「多轮都干净」，否则就是拿运气当证据。
     */
    private static final int ROUNDS = Integer.getInteger("rag.timeoutRace.rounds", 200);

    private static Tomcat tomcat;
    private static int port;

    @BeforeAll
    static void startEverything() throws Exception {
        File base = new File(System.getProperty("java.io.tmpdir"), "rag-common-timeout-race-gate");
        base.mkdirs();
        tomcat = new Tomcat();
        tomcat.setBaseDir(base.getAbsolutePath());
        tomcat.setPort(0);
        tomcat.getConnector();
        Context ctx = tomcat.addContext("", base.getAbsolutePath());

        Tomcat.addServlet(ctx, "slow", new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
                try {
                    Thread.sleep(SLOW_MILLIS);
                    // 没被 interrupt ⇒ 说明这一轮没走超时分支（不该发生）
                    resp.setStatus(200);
                    resp.getWriter().write("{\"code\":200}");
                } catch (InterruptedException ie) {
                    // 🔴 复刻生产里 @ExceptionHandler 的那次「晚写」：
                    //    Spring 的 GlobalExceptionHandler 把错误响应写回 Servlet 响应，
                    //    最终走到 ServletServerHttpResponse.flush() → flushBuffer() → Tomcat commit。
                    //    这里用最少的动作把同样的效果做出来：改一个头 + 写 body + flush。
                    try {
                        resp.setHeader("X-Late-Write", "1");
                        resp.getWriter().write("{\"code\":5001,\"msg\":\"late\"}");
                        resp.flushBuffer();
                    } catch (Exception ignored) {
                        // 容器可能已经提交/关闭了响应 —— 这正是竞态的一部分，不影响判定
                    }
                } catch (Exception e) {
                    // 忽略
                }
            }
        });
        ctx.addServletMappingDecoded("/slow", "slow");

        // 被测对象：生产用的那个 HttpMiddleware（不是 mock、不是简化版）
        Filter middleware = HttpMiddleware.newHttpMiddleware("timeout-race-gate", TIMEOUT, null);
        FilterDef def = new FilterDef();
        def.setFilterName("httpMiddleware");
        def.setFilter(middleware);
        def.setAsyncSupported("false");
        ctx.addFilterDef(def);
        FilterMap map = new FilterMap();
        map.setFilterName("httpMiddleware");
        map.addURLPattern("/*");
        ctx.addFilterMap(map);

        tomcat.start();
        port = tomcat.getConnector().getLocalPort();
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
    }

    @Test
    @DisplayName("🔴 真 Tomcat + 真 HttpMiddleware：超时后客户端必须拿到**完整干净**的 503（不许撕裂）")
    void timeoutResponseIsNeverTorn() {
        List<String> broken = new ArrayList<>();

        for (int i = 0; i < ROUNDS; i++) {
            String raw;
            try {
                raw = rawGet("/slow");
            } catch (Exception e) {
                broken.add("第 " + i + " 轮：连接层异常 ⇒ " + e);
                continue;
            }
            String problem = inspect(raw);
            if (problem != null) {
                broken.add("第 " + i + " 轮：" + problem + " ⇒ 原始响应=[" + abbreviate(raw) + "]");
            }
        }

        assertThat(broken)
                .as("超时路径的响应必须**每一次**都是完整干净的 503。"
                        + "失败说明 worker 线程与容器线程在并发写同一个响应 ⇒ Tomcat 的 MimeHeaders "
                        + "被写坏（count 多加一次 ⇒ 留 null 洞）⇒ NPE ⇒ 响应没写出去 ⇒ 客户端只看到 EOF。"
                        + "Go 侧不存在这个问题：go-zero 的 timeouthandler.go 用 timeoutWriter（带锁的 buffer）"
                        + "承接业务写 ⇒ 永远只有一个写者碰 socket。"
                        + "共 " + ROUNDS + " 轮，坏 " + broken.size() + " 轮。")
                .isEmpty();
    }

    /**
     * 判一次响应是否「撕裂」。返回 {@code null} 表示这一次是好的。
     */
    private static String inspect(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "一个字节都没收到（连接被掐断）";
        }
        // ⚠️ RFC 9110 §15.2：1xx 是 **informational（临时）响应**，客户端**必须忽略**它、
        //    继续等最终响应。
        // 🔴 **订正（2026-10-27）**：本注释原写「Tomcat 11 会自发地发一条 103
        //    （RAG 侧**无人**设 103 ⇒ 是容器自己的行为）」—— **这个归因是错的**：
        //    那行 1xx 是**共享层自己**写的 ⇒ 出处是
        //    `com.rag.common.observability.GoStyleClientDisconnectProbe`
        //    （裸写 socket 的 `HTTP/1.1 103 Early Hints`，已签字偏离 SD-2026-10-25-04）。
        //    ⇒ 证据：同一条「无 Authorization 的 401 路径」实测**零 1xx**
        //    （401 走得快 ⇒ 来不及触发探测）⇒ 若 103 真是容器自发的，401 也该有。
        //    ⇒ 影响：**归因订正不影响本用例的判定**（两种来源下都要跳过 1xx），
        //    但它会影响「谁负责 1xx 的行数」——那是共享层的行数上界问题。
        //    ⇒ 这里按规范跳过所有 1xx，再看最终响应 —— 这也是任何真实客户端（如 java.net.http）做的事。
        int pos = 0;
        String statusLine;
        String head;
        String rawBody;
        while (true) {
            int sep = raw.indexOf("\r\n\r\n", pos);
            if (sep < 0) {
                return "响应没有结束头（原始=[" + abbreviate(raw) + "]）";
            }
            String block = raw.substring(pos, sep);
            statusLine = block.split("\r\n", 2)[0];
            if (statusLine.startsWith("HTTP/1.1 1")) {
                pos = sep + 4;
                continue;
            }
            head = block;
            rawBody = raw.substring(sep + 4);
            break;
        }
        if (!statusLine.startsWith("HTTP/1.1 503")) {
            return "状态行不是 503（实际=" + statusLine + "）";
        }
        // body 必须是 "Request Timeout\n"（chunked 要先解出来）
        String body = decodeBody(head, rawBody);
        if (!"Request Timeout\n".equals(body)) {
            return "body 不等于 \"Request Timeout\\n\"（实际=[" + abbreviate(body) + "]）";
        }
        // worker 的晚写不许漏到客户端
        if (head.toLowerCase().contains("x-late-write")) {
            return "worker 线程的晚写漏进了响应（X-Late-Write）";
        }
        if (!head.toLowerCase().contains("x-content-type-options: nosniff")) {
            return "缺少 X-Content-Type-Options: nosniff（Go 的 http.Error 自带）";
        }
        // Content-Type：Tomcat 会把分号后的空格吃掉（已知框架限制）⇒ 只断言 type 与 charset
        String ct = headerOf(head, "content-type");
        if (ct == null || !ct.toLowerCase().startsWith("text/plain") || !ct.toLowerCase().contains("charset=utf-8")) {
            return "Content-Type 不对（实际=" + ct + "）";
        }
        return null;
    }

    private static String headerOf(String head, String name) {
        for (String line : head.split("\r\n")) {
            int c = line.indexOf(':');
            if (c > 0 && line.substring(0, c).trim().equalsIgnoreCase(name)) {
                return line.substring(c + 1).trim();
            }
        }
        return null;
    }

    /**
     * 取 body：{@code Transfer-Encoding: chunked} ⇒ 解 chunk；否则按 {@code Content-Length} 截。
     *
     * <p>⚠️ 为什么门要自己解 chunk：门用<b>裸 socket</b>（否则看不见「一个字节都没收到」这种撕裂）⇒
     * 拿到的就是 wire 上的原始字节 ⇒ chunked 的分块头还在里面。
     * 而「body 是不是 {@code Request Timeout\n}」是<b>语义</b>层的断言 ⇒ 必须先解出来。
     */
    private static String decodeBody(String head, String rawBody) {
        String te = headerOf(head, "transfer-encoding");
        if (te != null && te.toLowerCase().contains("chunked")) {
            StringBuilder sb = new StringBuilder();
            int i = 0;
            while (i < rawBody.length()) {
                int eol = rawBody.indexOf("\r\n", i);
                if (eol < 0) {
                    break;
                }
                String sizeLine = rawBody.substring(i, eol).trim();
                int semi = sizeLine.indexOf(';');
                if (semi >= 0) {
                    sizeLine = sizeLine.substring(0, semi);
                }
                int size;
                try {
                    size = Integer.parseInt(sizeLine.trim(), 16);
                } catch (NumberFormatException nfe) {
                    return rawBody;
                }
                if (size == 0) {
                    break;
                }
                int start = eol + 2;
                int end = Math.min(start + size, rawBody.length());
                sb.append(rawBody, start, end);
                i = end + 2;
            }
            return sb.toString();
        }
        String cl = headerOf(head, "content-length");
        if (cl != null) {
            try {
                int len = Integer.parseInt(cl.trim());
                return rawBody.length() >= len ? rawBody.substring(0, len) : rawBody;
            } catch (NumberFormatException ignored) {
                return rawBody;
            }
        }
        return rawBody;
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "null";
        }
        String flat = s.replace("\r", "\\r").replace("\n", "\\n");
        return flat.length() <= 400 ? flat : flat.substring(0, 400) + "…(truncated)";
    }

    private static String rawGet(String path) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(15_000);
            String req = "GET " + path + " HTTP/1.1\r\n"
                    + "Host: 127.0.0.1:" + port + "\r\n"
                    + "Connection: close\r\n\r\n";
            OutputStream os = socket.getOutputStream();
            os.write(req.getBytes(StandardCharsets.UTF_8));
            os.flush();

            // ⚠️ 必须读**原始字节**：BufferedReader.readLine() 会把 "\r\n" 归一化成 "\n"
            //   ⇒ body 的字节级断言（"Request Timeout\n"）会被改写 ⇒ 断言就失去意义。
            //   ⇒ 用 ISO-8859-1 解码（1 字节 ↔ 1 字符，字节不丢）。
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            try (java.io.InputStream in = socket.getInputStream()) {
                int n;
                while ((n = in.read(chunk)) > 0) {
                    buf.write(chunk, 0, n);
                }
            }
            return buf.toString(StandardCharsets.ISO_8859_1);
        }
    }
}
