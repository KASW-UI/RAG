package com.rag.common.observability;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
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
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>守门：客户端断连 ⇒ <b>在飞的下游 gRPC 被取消</b>（而不是等自己的 deadline）。</b>
 *
 * <h3>Go 侧的判据（真源，consumption point）</h3>
 * <pre>
 *   Sea-RideTheWind_golang/service/common/observability/http.go:80-88
 *       case &lt;-ctx.Done():
 *           statusCode := http.StatusServiceUnavailable
 *           if errors.Is(ctx.Err(), context.Canceled) { statusCode = httpClientClosedRequest }  // 499
 *           MarkTimeout(ctx, ctx.Err(), duration, httpAttrs(serviceName, req, statusCode)...)
 *           http.Error(w, httpTimeoutBody, statusCode)
 *   ⇒ Go 的 ctx 一取消，所有 ctx-aware 的下游调用（grpc.Dial）**立刻**返回
 *   ⇒ 而 Go 的 net/http 在客户端断开时取消 r.Context()
 *   ⇒ 断连 ⇒ 在飞 gRPC 立刻中止
 * </pre>
 *
 * <h3>为什么必须是「真 Tomcat + 真 gRPC」（§3.3）</h3>
 * <p>本类要守的机制有两条，都<b>不是 mock 能测的</b>：
 * <ol>
 *   <li><b>「往 socket 写失败」才能发现断连</b>：{@code MockHttpServletResponse} /
 *       MockMvc 的 write() <b>永远不失败</b> ⇒ 断连分支根本触发不了。</li>
 *   <li><b>「取消 gRPC Context 才能中止在飞 RPC」</b>：纯 mock 的 {@code ClientCall}
 *       不读 {@code Context.current()} ⇒ 取消链路根本不存在。</li>
 * </ol>
 * ⇒ 这里用<b>嵌入式真 Tomcat</b>（{@code tomcat-embed-core}，test 作用域）
 * + <b>真</b> {@link HttpMiddleware} + <b>真 in-process gRPC server</b>。
 *
 * <h3>⚠️ 为什么下游调用必须是「异步」的</h3>
 * <p>若下游用<b>阻塞 stub</b>，那么「worker 线程被 interrupt」就足以让调用返回
 * ⇒ 测试会<b>假绿</b>（分不清是 Context 取消生效还是线程中断生效）。
 * ⇒ 这里用 {@code ClientCalls.asyncUnaryCall}（<b>不受线程中断影响</b>）
 * ⇒ <b>唯一</b>能取消它的路径就是取消 gRPC {@code Context}
 * ⇒ 断言因此才有判别力（§3.3「绿测试可能是错误答案的守卫」的反面）。
 *
 * <h3>断言三件事</h3>
 * <ol>
 *   <li><b>断连 ⇒ 在飞 gRPC 被取消</b>，且<b>远早于 HTTP 超时</b>。</li>
 *   <li><b>正常客户端不受影响</b>：body 逐字节相同、{@code Content-Length} 保住（不是 chunked）、
 *       {@code Content-Type} 保住。</li>
 *   <li><b>探针字节不破坏 HTTP 流</b>：客户端能按 RFC 9110 跳过 1xx 后正常解析出最终响应；
 *       <b>并且必须真的看到 1xx</b>（正向对照，证明探针确实跑过 ⇒ 断言不是空转）。</li>
 * </ol>
 */
class GoStyleClientDisconnectProbeGateTest {

    /** 与 Go 侧 {@code httpClientClosedRequest} 同值。 */
    private static final int HTTP_CLIENT_CLOSED_REQUEST = 499;

    /** HTTP 超时（秒）。刻意远大于「探测间隔」⇒ 断连必须靠**探测**发现，而不是靠超时兜底。 */
    private static final int HTTP_TIMEOUT_SECONDS = 8;

    /** 下游 gRPC 的服务名（in-process 通道名）。 */
    private static final String GRPC_SERVICE_NAME = "rag-disconnect-probe-gate";

    /** 下游方法名。 */
    private static final String PATH_DOWNSTREAM = "/downstream";

    /** 「慢但正常」的路径：耗时 > 探测间隔 ⇒ 探针**一定会**跑 ⇒ 用来验「客户端不受影响」。 */
    private static final String PATH_SLOW_OK = "/slow-ok";

    private static Tomcat tomcat;
    private static int port;
    private static Server grpcServer;
    private static ManagedChannel grpcChannel;
    private static ListAppender<ILoggingEvent> logAppender;

    /** 客户端 RST 之后多久才允许「在飞 gRPC 被取消」（毫秒）。 */
    private static final long CANCEL_LATENCY_BUDGET_MS = 3_000L;

    /** 业务处理耗时（毫秒）—— 必须 > 探测间隔，探针才有机会在业务跑完前触发。 */
    private static final long SLOW_BUSINESS_MS = 900L;

    /** 客户端在请求发出后多久 RST（毫秒）。 */
    private static final int RST_AFTER_MS = 150;

    /** 每次下游调用一条记录（服务端侧观察到的取消）。 */
    private static final BlockingQueue<CallRecord> CALLS = new LinkedBlockingQueue<>();

    /**
     * 一次下游调用在**服务端**的观察记录。
     */
    private static final class CallRecord {
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch cancelled = new CountDownLatch(1);
        volatile long cancelledAtNanos;

        boolean awaitCancelled(long timeoutMs) throws InterruptedException {
            return cancelled.await(timeoutMs, TimeUnit.MILLISECONDS);
        }
    }

    private static final MethodDescriptor<String, String> METHOD =
            MethodDescriptor.<String, String>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName("rag.test.Downstream/Ping")
                    .setRequestMarshaller(new StringMarshaller())
                    .setResponseMarshaller(new StringMarshaller())
                    .build();

    @BeforeAll
    static void startAll() throws Exception {
        // ===== 真 in-process gRPC server =====
        grpcServer = InProcessServerBuilder.forName(GRPC_SERVICE_NAME)
                .addService(ServerServiceDefinition.builder("rag.test.Downstream")
                        .addMethod(METHOD, ServerCalls.asyncUnaryCall(
                                new ServerCalls.UnaryMethod<String, String>() {
                                    @Override
                                    public void invoke(String request, StreamObserver<String> responseObserver) {
                                        CallRecord record = new CallRecord();
                                        CALLS.add(record);
                                        record.started.countDown();
                                        // ★ 服务端观察取消的唯一通道：server 侧 Context 被 cancel 时回调
                                        io.grpc.Context.current().addListener(ctx -> {
                                            record.cancelledAtNanos = System.nanoTime();
                                            record.cancelled.countDown();
                                        }, Runnable::run);
                                        try {
                                            if (record.awaitCancelled(60_000L)) {
                                                responseObserver.onError(Status.CANCELLED
                                                        .withDescription("client cancelled the rpc")
                                                        .asRuntimeException());
                                            } else {
                                                responseObserver.onNext("pong");
                                                responseObserver.onCompleted();
                                            }
                                        } catch (InterruptedException e) {
                                            Thread.currentThread().interrupt();
                                        }
                                    }
                                }))
                        .build())
                .build()
                .start();

        grpcChannel = InProcessChannelBuilder.forName(GRPC_SERVICE_NAME).build();

        // ===== 真 Tomcat =====
        File base = new File(System.getProperty("java.io.tmpdir"), "rag-common-disconnect-probe-gate");
        base.mkdirs();
        tomcat = new Tomcat();
        tomcat.setBaseDir(base.getAbsolutePath());
        tomcat.setPort(0);
        tomcat.getConnector();
        Context ctx = tomcat.addContext("", base.getAbsolutePath());

        // ① 会发起下游 gRPC 的路径（用**异步** stub ⇒ 线程中断取消不了它）
        Tomcat.addServlet(ctx, "downstream", new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                CountDownLatch finished = new CountDownLatch(1);
                StreamObserver<String> observer = new StreamObserver<>() {
                    @Override
                    public void onNext(String value) {
                        finished.countDown();
                    }

                    @Override
                    public void onError(Throwable t) {
                        finished.countDown();
                    }

                    @Override
                    public void onCompleted() {
                        finished.countDown();
                    }
                };
                ClientCalls.asyncUnaryCall(grpcChannel.newCall(METHOD, CallOptions.DEFAULT),
                        "ping", observer);
                try {
                    // ★ 阻塞在下游调用上：只有「取消 gRPC Context」能让它返回
                    finished.await(60, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                resp.setContentType("application/json");
                resp.getWriter().write("{\"code\":200,\"msg\":\"ok\"}");
            }
        });
        ctx.addServletMappingDecoded(PATH_DOWNSTREAM, "downstream");

        // ② 「慢但正常」的路径：耗时 > 探测间隔 ⇒ 探针会跑 ⇒ 用来验客户端不受影响
        Tomcat.addServlet(ctx, "slowOk", new HttpServlet() {
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
        ctx.addServletMappingDecoded(PATH_SLOW_OK, "slowOk");

        // ③ **真** HttpMiddleware
        addFilter(ctx, "httpMiddleware", HttpMiddleware.newHttpMiddleware(
                "probe-gate-svc", Duration.ofSeconds(HTTP_TIMEOUT_SECONDS), Duration.ofSeconds(1)));

        tomcat.start();
        port = tomcat.getConnector().getLocalPort();

        logAppender = new ListAppender<>();
        logAppender.start();
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(HttpMiddleware.class)).addAppender(logAppender);
    }

    @AfterAll
    static void stopAll() {
        if (logAppender != null) {
            logAppender.stop();
        }
        if (grpcChannel != null) {
            grpcChannel.shutdownNow();
        }
        if (grpcServer != null) {
            try {
                grpcServer.shutdownNow();
            } catch (Exception ignored) {
                // 收尾失败不影响判定
            }
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

    // ================================================================== 断言 1

    @Test
    @DisplayName("🔴 真 Tomcat + 真 gRPC：客户端 RST ⇒ 在飞的下游 gRPC 被取消（远早于 HTTP 超时）")
    void clientDisconnectCancelsInFlightGrpc() throws Exception {
        logAppender.list.clear();
        CALLS.clear();

        // ① 先把客户端请求打出去：业务会发起下游 gRPC 并一直等
        Socket socket = new Socket("127.0.0.1", port);
        socket.setSoTimeout(5_000);
        String request = "GET " + PATH_DOWNSTREAM + " HTTP/1.1\r\nHost: 127.0.0.1:" + port
                + "\r\nConnection: close\r\n\r\n";
        socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().flush();

        // ② 等下游调用真的到了服务端
        CallRecord record = CALLS.poll(10, TimeUnit.SECONDS);
        assertNotNull(record, "下游 gRPC 根本没被调用 —— 测试失去意义");
        assertTrue(record.started.await(5, TimeUnit.SECONDS), "下游 handler 没启动");

        // ③ 客户端 RST（最粗暴的断连）
        long rstAtNanos = System.nanoTime();
        socket.setSoLinger(true, 0);
        socket.close();

        // ④ ★ 核心断言：下游 gRPC **被取消了**
        boolean cancelled = record.awaitCancelled(CANCEL_LATENCY_BUDGET_MS);
        long latencyMs = record.cancelledAtNanos == 0L
                ? -1L
                : (record.cancelledAtNanos - rstAtNanos) / 1_000_000L;

        assertTrue(cancelled,
                "客户端断开后，在飞的下游 gRPC 必须在 " + CANCEL_LATENCY_BUDGET_MS + "ms 内被取消 —— "
                        + "Go 的 net/http 会在客户端断开时取消 r.Context()，ctx 一取消所有下游 grpc.Dial 调用立刻返回；"
                        + "RAG 以前没有这个通道（只能等自己的 deadline），现在靠「主动探测写」补上。"
                        + "实测取消延迟=" + latencyMs + "ms，HTTP 超时=" + (HTTP_TIMEOUT_SECONDS * 1000) + "ms");
        assertTrue(latencyMs >= 0 && latencyMs < CANCEL_LATENCY_BUDGET_MS,
                "取消延迟必须在 HTTP 超时之前。实测=" + latencyMs + "ms");

        // ⑤ HTTP 层也必须很快定型为 499（而不是拖到超时 503）
        assertTrue(awaitLog499(CANCEL_LATENCY_BUDGET_MS),
                "HTTP 层必须很快记 499（对齐 Go 的 ctx.Canceled 分支）。日志=" + logAppender.list);
    }

    // ================================================================== 断言 2 & 3

    @Test
    @DisplayName("✅ 真 Tomcat：客户端不断连 ⇒ body 逐字节相同、Content-Length / Content-Type 全保住")
    void normalClientIsNotAffectedByProbeBytes() throws Exception {
        String raw = httpGet(PATH_SLOW_OK);

        // 正向对照：探针**确实跑过**（否则下面的断言全是空转）
        assertTrue(raw.contains("HTTP/1.1 103 Early Hints"),
                "慢请求（>" + SLOW_BUSINESS_MS + "ms）必然触发探测 ⇒ 客户端必须先收到 1xx 探针。"
                        + "实际响应=" + raw);

        HttpResponse parsed = parseHttpResponse(raw);
        assertTrue(parsed.statusLine.startsWith("HTTP/1.1 200"),
                "跳过 1xx 之后必须是 200。实际响应=" + raw);
        assertEquals("{\"code\":200,\"msg\":\"ok\"}", parsed.body,
                "body 必须逐字节相同。实际响应=" + raw);
        assertEquals("application/json", parsed.headers.get("Content-Type"),
                "Content-Type 必须保住（探针**不提交响应**⇒ 不会被 Tomcat 静默忽略）。实际响应=" + raw);
        assertNotNull(parsed.headers.get("Content-Length"),
                "Content-Length 必须保住（不能退化成 chunked）。实际响应=" + raw);
        assertEquals(parsed.body.getBytes(StandardCharsets.UTF_8).length,
                Integer.parseInt(parsed.headers.get("Content-Length")),
                "Content-Length 必须等于实际 body 字节数。实际响应=" + raw);
        assertFalse(parsed.headers.containsKey("Transfer-Encoding"),
                "不能退化成 chunked（Go 的 net/http 对装得下的响应体算 Content-Length）。实际响应=" + raw);
    }

    @Test
    @DisplayName("🔒 真 Tomcat：探针字节不破坏 HTTP 流（RFC 9110：客户端忽略 1xx）")
    void probeBytesDoNotBreakHttpStream() throws Exception {
        String raw = httpGet(PATH_SLOW_OK);

        // ① 1xx 块必须自身完整（状态行 + 空行），否则客户端解析器会被带偏
        int firstBlank = raw.indexOf("\r\n\r\n");
        assertTrue(firstBlank > 0, "探针块必须有完整的空行结尾。实际响应=" + raw);
        assertTrue(raw.substring(0, firstBlank).startsWith("HTTP/1.1 103"),
                "第一块必须是 1xx。实际响应=" + raw);

        // ② 跳过 1xx 之后，最终响应必须完整可解析
        HttpResponse parsed = parseHttpResponse(raw);
        assertTrue(parsed.statusLine.startsWith("HTTP/1.1 200"),
                "最终响应状态行必须是 200。实际响应=" + raw);
        assertTrue(parsed.headers.containsKey("Content-Type"), "最终响应必须有 Content-Type。实际响应=" + raw);
        assertTrue(parsed.headers.containsKey("Content-Length"), "最终响应必须有 Content-Length。实际响应=" + raw);
        assertEquals("{\"code\":200,\"msg\":\"ok\"}", parsed.body, "最终响应 body 必须完整。实际响应=" + raw);
    }

    // ================================================================== 工具

    private boolean awaitLog499(long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (logAppender.list.stream().anyMatch(e -> e.getFormattedMessage().contains("[HTTP] 499"))) {
                return true;
            }
            Thread.sleep(20L);
        }
        return logAppender.list.stream().anyMatch(e -> e.getFormattedMessage().contains("[HTTP] 499"));
    }

    /** 裸 socket 发请求，读到 EOF 为止（原始字节，含可能存在的 1xx 探针）。 */
    private String httpGet(String path) throws Exception {
        try (Socket s = new Socket("127.0.0.1", port)) {
            s.setSoTimeout(20_000);
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

    /**
     * 极简 HTTP/1.1 响应解析器：<b>容忍前面的 1xx</b>（RFC 9110 允许任意多个 1xx 先行）。
     */
    private static HttpResponse parseHttpResponse(String raw) {
        int pos = 0;
        while (true) {
            int eol = raw.indexOf("\r\n", pos);
            if (eol < 0) {
                throw new IllegalStateException("没有完整的状态行：响应=" + raw);
            }
            String statusLine = raw.substring(pos, eol);
            pos = eol + 2;
            Map<String, String> headers = new LinkedHashMap<>();
            while (true) {
                eol = raw.indexOf("\r\n", pos);
                if (eol < 0) {
                    throw new IllegalStateException("头部没有结束：响应=" + raw);
                }
                String line = raw.substring(pos, eol);
                pos = eol + 2;
                if (line.isEmpty()) {
                    break;
                }
                int colon = line.indexOf(':');
                if (colon > 0) {
                    headers.put(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
                }
            }
            if (statusLine.startsWith("HTTP/1.1 1")) {
                // 1xx ⇒ 丢弃整块，继续读最终响应
                continue;
            }
            return new HttpResponse(statusLine, headers, raw.substring(pos));
        }
    }

    private static final class HttpResponse {
        final String statusLine;
        final Map<String, String> headers;
        final String body;

        HttpResponse(String statusLine, Map<String, String> headers, String body) {
            this.statusLine = statusLine;
            this.headers = headers;
            this.body = body;
        }
    }

    /** String 的 gRPC marshaller（测试用）。 */
    private static final class StringMarshaller implements MethodDescriptor.Marshaller<String> {
        @Override
        public InputStream stream(String value) {
            return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public String parse(InputStream stream) {
            try {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
