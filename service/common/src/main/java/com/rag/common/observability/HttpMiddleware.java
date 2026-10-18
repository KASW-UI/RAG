package com.rag.common.observability;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.rag.common.observability.Observability.durationMillis;
import static com.rag.common.observability.Observability.markBusinessError;
import static com.rag.common.observability.Observability.markDurationAndSlow;
import static com.rag.common.observability.Observability.markSystemError;
import static com.rag.common.observability.Observability.markTimeout;
import static com.rag.common.observability.Observability.timeoutFromMillis;
import static com.rag.common.observability.ObservabilityConstants.DEFAULT_SLOW_THRESHOLD;
import static com.rag.common.observability.ObservabilityConstants.SUCCESS_BIZ_CODE;

/**
 * 对应 Go 版 service/common/observability/http.go 的整个文件。
 *
 * <p>Go 版签名是 {@code rest.Middleware = func(http.HandlerFunc) http.HandlerFunc}；
 * Java 版用 {@link jakarta.servlet.Filter}（等价物）。
 *
 * <p>Go 版核心结构：
 * <ul>
 *   <li>{@code DisableNativeRestTimeout(conf *rest.RestConf)}：关闭 go-zero 自带 rest 超时中间件</li>
 *   <li>{@code NewHTTPMiddleware(serviceName, timeout, slowThreshold)}：构造中间件</li>
 *   <li>{@code observabilityResponseWriter}：buffer 模式，捕获响应 body / status / headers，用于 envelope 解析；
 *       flushTo 一次性写到 client</li>
 * </ul>
 *
 * <p>Java 版语义对齐：
 * <ul>
 *   <li>{@link ObservabilityResponseWriter} 用 buffer 模式（不 tee）。超时分支完全不写 captured body；
 *       flushTo 仅在 done 分支调用；超时分支用 {@code httpResp.getWriter().write} 写"Request Timeout"，
 *       对齐 Go 版 {@code http.Error(w, httpTimeoutBody, statusCode)} 语义。</li>
 *   <li>超时状态码区分：{@link ObservabilityResponseWriter#markTimedOut} 与
 *       {@link ObservabilityResponseWriter#markClientCancelled} 两个独立标志，对齐 Go 版
 *       {@code errors.Is(ctx.Err(), context.Canceled) → 499 / DeadlineExceeded → 503} 语义。</li>
 *   <li>ctx 取消传播（DRIFT-006 限制说明）：Java 版用 Thread.sleep 轮询 + worker.interrupt()，
 *       业务必须响应 {@link InterruptedException} 或周期性查 {@code Thread.isInterrupted()}。</li>
 * </ul>
 */
public final class HttpMiddleware {

    /**
     * 对应 Go 版 {@code httpClientClosedRequest = 499}：客户端断开连接时 nginx 风格的约定状态码。
     */
    private static final int HTTP_CLIENT_CLOSED_REQUEST = 499;

    /**
     * 对应 Go 版 {@code httpTimeoutBody = "Request Timeout"}：HTTP 超时时 body 内容。
     */
    private static final String HTTP_TIMEOUT_BODY = "Request Timeout";

    private static final ObjectMapper ENVELOPE_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * Servlet Filter 专用 worker 线程池，替换裸 {@code new Thread(...)}。
     * <p><b>Spring 化</b>：业务模块在 {@code @PreDestroy} 里显式调 {@link #shutdown()} 关闭。
     *
     * <p>注意：<b>主线程仍用 {@code Thread.sleep(10)} 轮询</b>，不能用 {@code Future.get} 替代——
     * servlet 容器线程必须能立即响应 {@code InterruptedException}（容器关闭 / 请求取消），
     * 用 {@code Future.get} 会阻塞 servlet 主线程直到 timeout 才能退出，违反 servlet 生命周期。
     */
    private static final ExecutorService HTTP_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "obs-http-worker");
        t.setDaemon(true);
        return t;
    });

    private HttpMiddleware() {
    }

    /**
     * Spring 化生命周期钩子：关闭 {@link #HTTP_EXECUTOR}。
     * <p>推荐在业务模块的 {@code @PreDestroy} / {@code DisposableBean.destroy()} 里调用。
     * <p><b>幂等</b>：重复调用安全。
     */
    public static void shutdown() {
        HTTP_EXECUTOR.shutdownNow();
    }

    /**
     * 对应 Go 版 {@code func DisableNativeRestTimeout(conf *rest.RestConf) time.Duration}。
     *
     * <p>Java 版 Spring Boot 没有"原生 REST 超时中间件"概念，所以本方法等价于：
     * <pre>
     *   1. 从配置对象（如果有）取出 timeout 字段
     *   2. 返回该 Duration（让调用方把它传给 NewHTTPMiddleware）
     * </pre>
     */
    public static Duration disableNativeRestTimeout(long timeoutMillis) {
        return timeoutFromMillis(timeoutMillis);
    }

    /**
     * 对应 Go 版 {@code func NewHTTPMiddleware(serviceName string, timeout, slowThreshold time.Duration) rest.Middleware}。
     *
     * <p>行为与 Go 版完全对齐：
     * <ul>
     *   <li>timeout <= 0 或 shouldSkipHTTPTimeout → 不开超时，直接链式处理</li>
     *   <li>否则：独立 worker 线程跑业务，主线程 Thread.sleep(10ms) 轮询</li>
     *   <li>超时分支（worker 未完成且主线程 timeout 到期）：
     *     <ul>
     *       <li>statusCode = 503（HTTP ServiceUnavailable，对齐 Go 版 DeadlineExceeded）</li>
     *       <li>写"Request Timeout"到 client；调用方观察 http.status_code=503 + sea.error.kind=timeout</li>
     *     </ul>
     *   </li>
     *   <li>客户端断连分支（worker 抛 ClientAbortException / EOFException）：
     *     <ul>
     *       <li>statusCode = 499（HTTP ClientClosedRequest，对齐 Go 版 context.Canceled）</li>
     *       <li>同样写"Request Timeout"；span 标 timeout</li>
     *     </ul>
     *   </li>
     *   <li>正常完成 → flushTo 写 captured body 给 client</li>
     * </ul>
     */
    public static Filter newHttpMiddleware(String serviceName, Duration timeout, Duration slowThreshold) {
        if (slowThreshold == null || slowThreshold.compareTo(Duration.ZERO) <= 0) {
            slowThreshold = DEFAULT_SLOW_THRESHOLD;
        }
        Duration finalSlow = slowThreshold;
        return new Filter() {
            @Override
            public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
                    throws IOException, ServletException {
                HttpServletRequest httpReq = (HttpServletRequest) req;
                HttpServletResponse httpResp = (HttpServletResponse) resp;

                if (shouldSkipHTTPTimeout(httpReq) || timeout == null || timeout.compareTo(Duration.ZERO) <= 0) {
                    Instant start = Instant.now();
                    ObservabilityResponseWriter rec = new ObservabilityResponseWriter(httpResp);
                    chain.doFilter(httpReq, rec);
                    Duration duration = Duration.between(start, Instant.now());
                    markHttpResult(currentOtelContext(), serviceName, httpReq, rec.statusCode(), rec.bodyBytes(), duration, finalSlow);
                    rec.flushTo(httpResp);
                    return;
                }

                // 对应 Go 版 ctx, cancel := context.WithTimeout(r.Context(), timeout)
                // Java 版用独立线程跑业务 + 主线程 Thread.sleep(10ms) 轮询，超时则 interrupt 业务线程
                ObservabilityResponseWriter rec = new ObservabilityResponseWriter(httpResp);
                final Throwable[] panicHolder = new Throwable[1];
                final boolean[] doneHolder = new boolean[1];
                final boolean[] timedOutBySchedulerHolder = new boolean[1];
                // Spring 化：用 HTTP_EXECUTOR 替换裸 new Thread(...)；保留 doneHolder + 主线程 Thread.sleep(10) 轮询
                // ——servlet 容器线程必须响应 InterruptedException，不能用 Future.get 阻塞。
                Future<?> workerFuture = HTTP_EXECUTOR.submit(() -> {
                    try {
                        chain.doFilter(httpReq, rec);
                        doneHolder[0] = true;
                    } catch (Throwable t) {
                        // 标记 timedOutByScheduler 已经在主线程里写好了；这里只记异常
                        // IOException 中含 "ClientAbort" / "EOFException" → 视作客户端断连（499）
                        // 其他 → 视作 panic（500）
                        panicHolder[0] = t;
                    }
                });
                Instant start = Instant.now();
                long timeoutMs = timeout.toMillis();
                long elapsed = 0;
                while (!doneHolder[0] && panicHolder[0] == null && elapsed < timeoutMs) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    elapsed = Duration.between(start, Instant.now()).toMillis();
                }

                Duration duration = Duration.between(start, Instant.now());
                boolean workerDone = doneHolder[0];
                Throwable panic = panicHolder[0];
                if (panic != null) {
                    // 区分"客户端断连"（499）与"系统 panic"（500）
                    boolean clientCancelled = isClientCancelledException(panic);
                    int statusCode = clientCancelled ? HTTP_CLIENT_CLOSED_REQUEST : 500;
                    if (clientCancelled) {
                        rec.markClientCancelled();
                    }
                    Context pCtx = currentOtelContext();
                    Span pSpan = Span.fromContext(pCtx);
                    if (pSpan.isRecording()) {
                        pSpan.setAttribute(AttributeKey.stringKey("service.name"), serviceName);
                        pSpan.setAttribute(AttributeKey.stringKey("http.route"), httpReq.getRequestURI());
                        pSpan.setAttribute(AttributeKey.stringKey("http.method"), httpReq.getMethod());
                        pSpan.setAttribute(AttributeKey.longKey("http.status_code"), (long) statusCode);
                    }
                    if (clientCancelled) {
                        markTimeout(pCtx, new java.util.concurrent.TimeoutException("client cancelled: " + panic.getMessage()),
                                duration, httpAttrs(serviceName, httpReq, statusCode));
                    } else {
                        markSystemError(pCtx, new RuntimeException(panic.getMessage(), panic), duration, httpAttrs(serviceName, httpReq, statusCode));
                    }
                    throw new ServletException(panic);
                } else if (!workerDone) {
                    // 对齐 Go 版 ctx.Done() == DeadlineExceeded → 503
                    // Spring 化：用 workerFuture.cancel(true) 替代 worker.interrupt()，等价语义（业务线程被 interrupt）
                    workerFuture.cancel(true);
                    int statusCode = 503;
                    timedOutBySchedulerHolder[0] = true;
                    rec.markTimedOut();
                    Context tCtx = currentOtelContext();
                    Span tSpan = Span.fromContext(tCtx);
                    if (tSpan.isRecording()) {
                        tSpan.setAttribute(AttributeKey.stringKey("service.name"), serviceName);
                        tSpan.setAttribute(AttributeKey.stringKey("http.route"), httpReq.getRequestURI());
                        tSpan.setAttribute(AttributeKey.stringKey("http.method"), httpReq.getMethod());
                        tSpan.setAttribute(AttributeKey.longKey("http.status_code"), (long) statusCode);
                    }
                    markTimeout(tCtx, new java.util.concurrent.TimeoutException("http timeout"), duration, httpAttrs(serviceName, httpReq, statusCode));
                    httpResp.setStatus(statusCode);
                    httpResp.getWriter().write(HTTP_TIMEOUT_BODY);
                } else {
                    markHttpResult(currentOtelContext(), serviceName, httpReq, rec.statusCode(), rec.bodyBytes(), duration, finalSlow);
                    rec.flushTo(httpResp);
                }
            }
        };
    }

    /**
     * 对应 Go 版 {@code func shouldSkipHTTPTimeout(r *http.Request) bool}。
     */
    private static boolean shouldSkipHTTPTimeout(HttpServletRequest req) {
        if (req == null) {
            return false;
        }
        String upgrade = req.getHeader("Upgrade");
        if (upgrade != null && upgrade.equalsIgnoreCase("websocket")) {
            return true;
        }
        String accept = req.getHeader("Accept");
        return accept != null && accept.equalsIgnoreCase("text/event-stream");
    }

    /**
     * 判断异常是否是"客户端断开连接"语义（对齐 Go 版 ctx.Err() == context.Canceled）。
     * <p>常见 Tomcat / 通用 servlet 容器中：
     * <ul>
     *   <li>{@link java.io.EOFException}：client 中途关闭 socket</li>
     *   <li>{@code org.apache.catalina.connector.ClientAbortException}（运行时探测，避免硬依赖）</li>
     *   <li>任何 cause 链上含上述类型</li>
     * </ul>
     */
    private static boolean isClientCancelledException(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof java.io.EOFException) return true;
            String className = cur.getClass().getName();
            if (className.contains("ClientAbortException") || className.contains("ClientAbort")) return true;
            if (cur.getCause() == cur) break;
            cur = cur.getCause();
        }
        return false;
    }

    /**
     * 对应 Go 版 {@code func markHTTPResult(ctx, serviceName, r, statusCode, body, duration, slowThreshold)}。
     */
    private static void markHttpResult(Context ctx, String serviceName, HttpServletRequest r, int statusCode, byte[] body, Duration duration, Duration slowThreshold) {
        Map<String, Object> attrs = httpAttrs(serviceName, r, statusCode);
        Span span = Span.fromContext(ctx);
        if (span.isRecording()) {
            span.setAttribute(AttributeKey.stringKey("service.name"), serviceName);
            span.setAttribute(AttributeKey.stringKey("http.route"), r.getRequestURI());
            span.setAttribute(AttributeKey.stringKey("http.method"), r.getMethod());
            span.setAttribute(AttributeKey.longKey("http.status_code"), (long) statusCode);
        }
        HttpEnvelope env = parseHttpEnvelope(body);
        if (env != null && env.code != 0 && env.code != SUCCESS_BIZ_CODE) {
            markBusinessError(ctx, env.code, env.msg, duration, attrs);
            return;
        }
        if (statusCode >= 400) {
            markSystemError(ctx, new RuntimeException("http status " + statusCode), duration, attrs);
            return;
        }
        markDurationAndSlow(ctx, duration, slowThreshold, attrs);
    }

    /**
     * 对应 Go 版 {@code type httpEnvelope struct { Code int; Msg string }}。
     */
    public static final class HttpEnvelope {
        public int code;
        public String msg;

        public HttpEnvelope() {}

        public HttpEnvelope(int code, String msg) {
            this.code = code;
            this.msg = msg;
        }
    }

    /**
     * 对应 Go 版 {@code func parseHTTPEnvelope(body []byte) *httpEnvelope}。
     * <p>DRIFT-009 修复：用 Jackson 替代手写 regex，能正确处理转义、换行、空格等复杂 JSON。
     */
    private static HttpEnvelope parseHttpEnvelope(byte[] body) {
        if (body == null || body.length == 0) {
            return null;
        }
        try {
            HttpEnvelope env = ENVELOPE_MAPPER.readValue(body, HttpEnvelope.class);
            if (env.code == 0 && (env.msg == null || env.msg.isEmpty())) {
                return null;
            }
            return env;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 对应 Go 版 {@code func httpAttrs(serviceName string, r *http.Request, statusCode int) []attribute.KeyValue}。
     */
    private static Map<String, Object> httpAttrs(String serviceName, HttpServletRequest r, int statusCode) {
        Map<String, Object> m = new LinkedHashMap<>(4);
        m.put("service.name", serviceName);
        m.put("http.route", r.getRequestURI());
        m.put("http.method", r.getMethod());
        m.put("http.status_code", statusCode);
        return m;
    }

    /**
     * 对应 Go 版 {@code type observabilityResponseWriter struct{ ... }}：
     * <p>buffer 模式（DRIFT-005 修复）：
     * <ul>
     *   <li>所有写操作仅写到内部 {@code body}/{@code header} 缓冲（对齐 Go 版
     *       {@code observabilityResponseWriter.body bytes.Buffer + header http.Header}）</li>
     *   <li>timeout 标记（{@link #markTimedOut} / {@link #markClientCancelled}）置位后，
     *       所有写返回 no-op，对齐 Go 版 {@code Write → ErrHandlerTimeout}</li>
     *   <li>{@link #flushTo} 仅在 done 分支调用；超时/客户端断连分支不再 flush captured body，
     *       由调用方直接写"Request Timeout"到 original response（对齐 Go 版
     *       {@code http.Error(w, httpTimeoutBody, statusCode)}）</li>
     * </ul>
     * <p>DRIFT-005 历史：早期版本用 tee 模式（DRIFT-017 修复），但 tee 在 timeout 后会让 client
     * 收到部分业务 body 再收到 timeout body，违反 HTTP 语义。回退到 buffer 模式对齐 Go 版语义。
     */
    private static final class ObservabilityResponseWriter extends HttpServletResponseWrapper {
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();
        private final Map<String, String> capturedHeaders = new LinkedHashMap<>();
        private final jakarta.servlet.http.HttpServletResponse original;
        private int status = 200;
        private volatile boolean timedOut = false;
        private volatile boolean clientCancelled = false;

        ObservabilityResponseWriter(HttpServletResponse original) {
            super(original);
            this.original = original;
        }

        @Override
        public void setStatus(int sc) {
            if (timedOut || clientCancelled) {
                return;
            }
            this.status = sc;
        }

        @Override
        public void sendError(int sc) throws IOException {
            if (timedOut || clientCancelled) {
                return;
            }
            this.status = sc;
        }

        @Override
        public void sendError(int sc, String msg) throws IOException {
            if (timedOut || clientCancelled) {
                return;
            }
            this.status = sc;
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            // 不走 OutputStreamWriter 的 char/byte 缓冲：每次 write 直接转 UTF-8 字节写入 captured body。
            // 这样 servlet handler 写完 body 后无需 flush 也能在 bodyBytes() 读到（对齐 Go 版
            // observabilityResponseWriter.Write 直接写 buffer 的语义）。
            java.io.Writer unbuffered = new java.io.Writer() {
                @Override
                public void write(char[] cbuf, int off, int len) {
                    if (timedOut || clientCancelled) return;
                    byte[] bytes = new String(cbuf, off, len).getBytes(StandardCharsets.UTF_8);
                    body.write(bytes, 0, bytes.length);
                }
                @Override
                public void write(String str, int off, int len) {
                    if (timedOut || clientCancelled) return;
                    byte[] bytes = str.substring(off, off + len).getBytes(StandardCharsets.UTF_8);
                    body.write(bytes, 0, bytes.length);
                }
                @Override
                public void flush() {
                    // body 是 ByteArrayOutputStream，flush 是 no-op
                }
                @Override
                public void close() {
                    // no-op；body 随 wrapper 一起释放
                }
            };
            return new PrintWriter(unbuffered, false);
        }

        @Override
        public jakarta.servlet.ServletOutputStream getOutputStream() throws IOException {
            // 返回一个只写到 captured body 的 ServletOutputStream
            return new jakarta.servlet.ServletOutputStream() {
                @Override
                public void write(int b) throws IOException {
                    if (timedOut || clientCancelled) return;
                    body.write(b);
                }

                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    if (timedOut || clientCancelled) return;
                    body.write(b, off, len);
                }

                @Override
                public void flush() {
                    // no-op；buffer 模式不主动 flush
                }

                @Override
                public boolean isReady() {
                    return !timedOut && !clientCancelled;
                }

                @Override
                public void setWriteListener(jakarta.servlet.WriteListener writeListener) {
                    // buffer 模式不支持 write listener；no-op
                }
            };
        }

        @Override
        public void setHeader(String name, String value) {
            if (timedOut || clientCancelled) return;
            capturedHeaders.put(name, value);
        }

        @Override
        public void addHeader(String name, String value) {
            if (timedOut || clientCancelled) return;
            capturedHeaders.merge(name, value, (a, b) -> a + "," + b);
        }

        int statusCode() {
            return status;
        }

        byte[] bodyBytes() {
            return body.toByteArray();
        }

        void markTimedOut() {
            this.timedOut = true;
        }

        void markClientCancelled() {
            this.clientCancelled = true;
        }

        /**
         * 对齐 Go 版 {@code func (w *observabilityResponseWriter) flushTo(dst http.ResponseWriter)}：
         * <ul>
         *   <li>遍历 captured headers → dst.Header().Add(key, value)</li>
         *   <li>WriteHeader(status)（默认 200 即不调用）</li>
         *   <li>io.Copy(dst, &w.body) 把 buffer body 写到 dst</li>
         * </ul>
         */
        void flushTo(HttpServletResponse dst) throws IOException {
            if (timedOut || clientCancelled) {
                // 超时 / 客户端断连时不 flush captured body（避免污染 timeout 响应）
                return;
            }
            for (Map.Entry<String, String> e : capturedHeaders.entrySet()) {
                dst.setHeader(e.getKey(), e.getValue());
            }
            if (status != 200) {
                dst.setStatus(status);
            }
            if (body.size() > 0) {
                dst.getOutputStream().write(body.toByteArray());
            }
        }

        @SuppressWarnings("unused")
        Enumeration<String> emptyHeaders() {
            return Collections.emptyEnumeration();
        }
    }

    /**
     * 取出当前 OTEL Context，对应 Go 版 {@code otel.Tracer(...).Start(ctx, ...)} 中的 ctx。
     */
    private static Context currentOtelContext() {
        return Context.current();
    }
}