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
import org.slf4j.MDC;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
 *   <li>ctx 取消传播：Go 版 {@code ctx} 显式贯穿全链，所有 ctx-aware 调用（http.Client / sql.DB /
 *       grpc.Dial）在 cancel 后立即返回；Java 无 ctx 传递，改用
 *       {@link java.util.concurrent.Future#cancel(boolean) cancel(true)} 中断 worker 线程，
 *       业务需响应 {@link InterruptedException} 或周期性查 {@link Thread#isInterrupted()}。</li>
 *   <li><b>H5</b>：worker 线程必须补齐 {@link RequestContextHolder} / {@link org.slf4j.MDC} /
 *       OTel {@link Context}，否则整条 Spring Web 链在 worker 上丢失请求上下文。</li>
 *   <li><b>H4</b>：499 / 503 / 容器取消三条分支都要<b>真的把状态码写进响应</b>，
 *       对齐 Go 版 {@code http.Error(w, httpTimeoutBody, statusCode)}。</li>
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
     *   <li>timeout <= 0 或 shouldSkipHTTPTimeout → 不开超时，直接在容器线程上链式处理</li>
     *   <li>否则：独立 worker 线程跑业务（含上下文传播），容器线程
     *       {@code CountDownLatch.await(timeout)} 精确等待</li>
     *   <li>超时分支（worker 未完成且容器线程等到点）：
     *     <ul>
     *       <li>statusCode = 503（HTTP ServiceUnavailable，对齐 Go 版 DeadlineExceeded）</li>
     *       <li>{@link #writeTimeoutResponse} 写"Request Timeout"到 client；
     *           调用方观察 http.status_code=503 + sea.error.kind=timeout</li>
     *     </ul>
     *   </li>
     *   <li>客户端断连分支（worker 抛 ClientAbortException / EOFException，或容器线程被 interrupt）：
     *     <ul>
     *       <li>statusCode = 499（HTTP ClientClosedRequest，对齐 Go 版 context.Canceled）</li>
     *       <li><b>同样把 499 写进响应</b>（H4 修复：旧实现只打 span 属性就抛异常，分支不可达）；
     *           span 标 timeout</li>
     *     </ul>
     *   </li>
     *   <li>panic 分支 → 原样重抛（对齐 Go {@code panic(p)}），由容器错误页渲染 500</li>
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
                // Java 版用独立线程跑业务 + 容器线程 CountDownLatch.await(timeout) 精确等待
                ObservabilityResponseWriter rec = new ObservabilityResponseWriter(httpResp);
                final Throwable[] panicHolder = new Throwable[1];
                final CountDownLatch doneLatch = new CountDownLatch(1);

                // ===== H5 修复：把「请求上下文」搬进 worker 线程 =====
                // Go 版把 r.Context()（含 deadline / trace）显式传给下游，靠的是参数传递，跨 goroutine 天然安全。
                // Java 的 Spring Web 栈把请求上下文放在**线程局部**里（RequestContextHolder / MDC / OTel Context），
                // 换线程就全丢。旧实现把 chain.doFilter 直接丢进裸 worker 线程，导致：
                //   · RequestContextHolder.getRequestAttributes() == null
                //   · @RequestScope / LocaleContextHolder 取不到值
                //   · 若引入 spring-security，SecurityContextHolder 同样为空
                // 今天还没爆，只是因为全仓尚未使用这些机制 —— 属于「一旦引入就整条 web 链失效」的雷。
                final RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
                final Map<String, String> mdcSnapshot = MDC.getCopyOfContextMap();
                final Context parentOtelCtx = Context.current();

                Future<?> workerFuture = HTTP_EXECUTOR.submit(() -> {
                    // 先进上下文，再跑业务；finally 必须还原，避免线程池复用时串味
                    RequestAttributes prevAttrs = RequestContextHolder.getRequestAttributes();
                    Map<String, String> prevMdc = MDC.getCopyOfContextMap();
                    try {
                        if (requestAttributes != null) {
                            RequestContextHolder.setRequestAttributes(requestAttributes);
                        }
                        if (mdcSnapshot != null) {
                            MDC.setContextMap(mdcSnapshot);
                        }
                        try (io.opentelemetry.context.Scope ignoredScope = parentOtelCtx.makeCurrent()) {
                            chain.doFilter(httpReq, rec);
                        }
                    } catch (Throwable t) {
                        // 标记 timedOutByScheduler 已经在主线程里写好了；这里只记异常
                        // IOException 中含 "ClientAbort" / "EOFException" → 视作客户端断连（499）
                        // 其他 → 视作 panic（500）
                        panicHolder[0] = t;
                    } finally {
                        doneLatch.countDown();
                        if (prevAttrs != null) {
                            RequestContextHolder.setRequestAttributes(prevAttrs);
                        } else {
                            RequestContextHolder.resetRequestAttributes();
                        }
                        if (prevMdc != null) {
                            MDC.setContextMap(prevMdc);
                        } else {
                            MDC.clear();
                        }
                    }
                });

                Instant start = Instant.now();
                long timeoutMs = timeout.toMillis();
                // 容器线程等 latch 到点即返回：精度对齐 Go 的 select{<-ctx.Done()}
                // （旧实现 Thread.sleep(10) 轮询最多晚 10ms 才判超时）
                boolean workerDone;
                try {
                    workerDone = doneLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
                } catch (InterruptedException ie) {
                    // 容器关闭 / 请求被容器取消：Go 侧对应 ctx.Err() == context.Canceled
                    Thread.currentThread().interrupt();
                    workerFuture.cancel(true);
                    rec.markClientCancelled();
                    markTimeout(currentOtelContext(), new java.util.concurrent.TimeoutException("client cancelled: " + ie.getMessage()),
                            Duration.between(start, Instant.now()), httpAttrs(serviceName, httpReq, HTTP_CLIENT_CLOSED_REQUEST));
                    writeTimeoutResponse(httpResp, HTTP_CLIENT_CLOSED_REQUEST);
                    return;
                }

                Duration duration = Duration.between(start, Instant.now());
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
                        // ===== H4 修复：499 之前是 throw ServletException，响应从未写出 =====
                        // Go observability/http.go:84-88 的 499 分支是 `http.Error(w, body, 499)`，
                        // 客户端真的收到 499；旧 Java 版打完 span 属性就抛异常，
                        // 499 只活在遥测里、永远到不了客户端（分支实际不可达）。
                        writeTimeoutResponse(httpResp, statusCode);
                        return;
                    }
                    markSystemError(pCtx, new RuntimeException(panic.getMessage(), panic), duration, httpAttrs(serviceName, httpReq, statusCode));
                    // M12 修复：Go 版 observability/http.go:75 是 `panic(p)` 原样重抛，
                    // 交给 go-zero 的 recovery 中间件渲染 500。旧 Java 版一律包成 ServletException，
                    // 改变了容器看到的异常类型（IOException/ServletException/RuntimeException 的
                    // 分派行为不同）。这里只对「容器不认识的」受检异常做包装，其余原样抛出。
                    rethrowAsServlet(panic);
                } else if (!workerDone) {
                    // 对齐 Go 版 ctx.Done() == DeadlineExceeded → 503
                    // 取消业务线程（对应 Go 里 ctx 被 cancel，所有 ctx-aware 调用立即返回）
                    workerFuture.cancel(true);
                    int statusCode = 503;
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
                    // D-11 修复：Go 的 http.Error 会写 text/plain + 尾部换行，
                    // 裸 getWriter().write() 两样都没有，客户端看到的是无 Content-Type 的裸串
                    writeTimeoutResponse(httpResp, statusCode);
                } else {
                    markHttpResult(currentOtelContext(), serviceName, httpReq, rec.statusCode(), rec.bodyBytes(), duration, finalSlow);
                    rec.flushTo(httpResp);
                }
            }
        };
    }

    /**
     * M12：把 worker 线程捕获的 Throwable 按类型分派重抛，语义对齐 Go 的 {@code panic(p)}。
     *
     * <p>Servlet Filter 的 {@code doFilter} 只能抛
     * {@link ServletException} / {@link IOException} / {@link RuntimeException} / {@link Error}；
     * 其它受检异常（业务 handler 里的自定义 checked exception）必须包装。
     * 已属于这四类的<b>原样抛出</b>，避免改变容器与 Spring MVC 的分派行为
     * （例如 {@code @ExceptionHandler(IOException.class)} 原本能命中，被包成 ServletException 就命不中）。
     */
    private static void rethrowAsServlet(Throwable t) throws ServletException, IOException {
        if (t instanceof ServletException se) throw se;
        if (t instanceof IOException io) throw io;
        if (t instanceof RuntimeException re) throw re;
        if (t instanceof Error err) throw err;
        throw new ServletException(t);
    }

    /**
     * D-11 修复：对齐 Go 版 {@code http.Error(w, httpTimeoutBody, statusCode)}。
     *
     * <p>Go 的 {@code http.Error} 做三件事：① {@code WriteHeader(status)}；
     * ② {@code Header().Set("Content-Type", "text/plain; charset=utf-8")}；
     * ③ 写 {@code body + "\n"}。旧 Java 版只做 ① 和裸 write，客户端少两个字段。
     */
    private static void writeTimeoutResponse(HttpServletResponse resp, int statusCode) throws IOException {
        resp.setStatus(statusCode);
        resp.setContentType("text/plain; charset=utf-8");
        resp.getWriter().write(HTTP_TIMEOUT_BODY);
        resp.getWriter().write('\n');
        resp.getWriter().flush();
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
        // M5 修复：删除此处直接 span.setAttribute 的重复打标。
        // Go 版 markHTTPResult（http.go:99-113）只把 attrs 传给 MarkBusinessError /
        // MarkSystemError / MarkDurationAndSlow，由那三个函数统一写入 span；
        // 旧 Java 版在 markHttpResult 里先 set 一遍、Mark* 里再 set 一遍 —— 同一批属性写两次。
        // 结果值一致（幂等），但多一次无效写，且让「谁负责打标」这件事在两处出现，review 时容易误判。
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
            // 关键修复（2026-10-21 实测）：status 必须立即 propagate 到 underlying。
            // 只缓存到 this.status 的旧实现里，下层 chain.doFilter() 跑完时已经
            // outputMessage.flush() → wrapper.flushBuffer() → 底层 MockHttpServletResponse
            // 标 committed，HttpMiddleware.flushTo 再写 status 直接被 !isCommitted() 吞掉。
            // 实测表象：HTTP 401 / 5003 一律降到 200，DRIFT-009 / DRIFT-004 全程不可见。
            // Go 对应的是 responseWriter.WriteHeader 同步语义，超时分支走未经 wrapper 的
            // writeTimeoutResponse(httpResp, 503) 兜底，与本改动无冲突。
            // 用 this.original 显式拿 HttpServletResponse，避免 inner class 字段遮蔽 super.getResponse()
            // return-type narrowing（javac 在某些版本会回退到 ServletResponseWrapper.getResponse()）。
            ((HttpServletResponse) this.original).setStatus(sc);
        }

        @Override
        public void sendError(int sc) throws IOException {
            if (timedOut || clientCancelled) {
                return;
            }
            this.status = sc;
            // 与 setStatus 同步 propagate（见 setStatus 的注释）
            ((HttpServletResponse) this.original).sendError(sc);
        }

        @Override
        public void sendError(int sc, String msg) throws IOException {
            if (timedOut || clientCancelled) {
                return;
            }
            this.status = sc;
            // 与 setStatus 同步 propagate（见 setStatus 的注释）
            ((HttpServletResponse) this.original).sendError(sc, msg);
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
            // 2026-10-21 修复后：setHeader 仍走 buffer（bufferedHeaders 与 buffered body 一同 flush）；
            // setStatus / sendError 已改为实时 propagate 到 underlying，见上面 setStatus 的注释。
            for (Map.Entry<String, String> e : capturedHeaders.entrySet()) {
                dst.setHeader(e.getKey(), e.getValue());
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