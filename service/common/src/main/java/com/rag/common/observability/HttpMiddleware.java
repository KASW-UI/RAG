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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
     * 该状态码是否允许带 {@code Content-Length}。
     *
     * <p>1xx / 204 / 304 按 HTTP 语义不带 body ⇒ 也不带 {@code Content-Length}
     * （Go 的 {@code net/http} 同样不发；Tomcat 在未提交时收尾也不会发）。
     *
     * <p>⚠️ 这条是「<b>保形</b>」的一部分：{@link #flushCaptured} 提前提交响应，
     * 若不把 {@code Content-Length} 定下来，Tomcat 就会退化成 {@code Transfer-Encoding: chunked}
     * ⇒ 与 Go 的 {@code Content-Length} 不一致（真 Tomcat 实测：2026-10-25）。
     */
    private static boolean statusAllowsContentLength(int status) {
        return status >= 200 && status != 204 && status != 304;
    }

    /**
     * Tomcat 的响应输出缓冲默认大小（{@code org.apache.coyote.ExpandBuffer} 的 {@code defaultBufferSize}）。
     *
     * <p>用途：判断「响应体能不能整块装进缓冲」⇒ 决定要不要由我们先设 {@code Content-Length}
     * （详见 {@link #flushCaptured}）。<b>刻意是常量而非配置项</b>：本类在 Spring 装配前就初始化，
     * 挂配置只会得到一个「看起来能配、实际改了没反应」的面板（§3.3 同款形态）。
     */
    private static final int TOMCAT_RESPONSE_BUFFER_SIZE = 8192;

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
    /**
     * worker 线程数上界（2026-10-22 加；此前是 {@code newCachedThreadPool} ⇒ <b>无上限</b>）。
     *
     * <p><b>为什么要上界</b>：⚠️ 本条曾写成「grpc-java 的阻塞 stub <b>不可中断</b>」——
     * <b>该说法已被实测推翻</b>（见 {@code DeadlineCancellationSemanticsTest}）：
     * {@code Future.cancel(true)} <b>确实能中断</b>阻塞调用。
     * 真正的差异是<b>中断产生的 status 是 {@code CANCELLED}</b>，
     * 而 Go 的 ctx 到期产生的是 {@code DEADLINE_EXCEEDED}，
     * 后者才在 {@code codes.Acceptable} 的失败名单里（前者不在）——
     * 即「下游超时」在补 deadline 之前对 client breaker <b>不可见</b>。
     * <p>上界要解决的是另一个问题：<b>线程被钉住的时间</b>。
     * 中断信号发出后，调用要等到自己真正收场才释放线程；
     * 而 {@code newCachedThreadPool} 无上限 ⇒ 线程数按 到达率 × 下游耗时 <b>无界增长</b>。
     * <p>Go 侧不存在这个问题，原因是它的超时走 <b>ctx 传播</b>：
     * {@code timeouthandler.go:67} 的 {@code r.WithContext(ctx)} 把 deadline <b>传进业务</b>，
     * 下游 I/O 立即中止 ⇒ goroutine 迅速释放；而且 goroutine 本身只有 2 KB 初始栈。
     * ⇒ 上界是「拿不到 ctx 传播」这件事的**兜底**，不是 Go 语义的一部分，别把它当等价物。
     *
     * <p><b>为什么取 512</b>：worker 只在请求在途期间被占用，而并发上限由容器的
     * {@code max-threads}（Tomcat 默认 200）决定。512 ≈ 2.5× 留出余量，
     * 使<b>正常流量永远不触发拒绝</b>，拒绝只在「下游已卡死 + 高并发」时才出现。
     * <p>⚠️ 这是常量而非配置项：本类是 static 工具、在 Spring 装配前就初始化，
     * 挂配置只会得到一个「看起来能配、实际改了没反应」的面板（§3.3 同款形态）。
     * 真需要调，优先调容器的 {@code max-threads} 保持两者关系明确。
     */
    static final int MAX_HTTP_WORKERS = 512;

    /**
     * 供测试与排障观察当前 worker 池的活跃/上限，不参与任何判定。
     *
     * <p>🔴 <b>若上一轮 {@link #shutdown()} 已把池终止 ⇒ 这里按需重建一份新的</b>
     * （与 {@code RpcInterceptor} 的两个池同款理由：<b>进程级静态池 + 终止即永久</b>
     * ⇒ 与「同一个 JVM 里存在第二个 Spring 上下文」不兼容）。
     */
    static ThreadPoolExecutor httpExecutor() {
        while (true) {
            ExecutorService current = HTTP_EXECUTOR.get();
            if (!current.isShutdown()) {
                return (ThreadPoolExecutor) current;
            }
            if (HTTP_EXECUTOR.compareAndSet(current, newHttpExecutor())) {
                return (ThreadPoolExecutor) HTTP_EXECUTOR.get();
            }
        }
    }

    private static final AtomicReference<ExecutorService> HTTP_EXECUTOR =
            new AtomicReference<>(newHttpExecutor());

    private static ExecutorService newHttpExecutor() {
        return new ThreadPoolExecutor(
                0,
                MAX_HTTP_WORKERS,
                60L,
                TimeUnit.SECONDS,
                // ⚠️ 必须仍是 SynchronousQueue：与旧的 newCachedThreadPool 保持同一形状
                //    （提交即交接、不排队）。换成 LinkedBlockingQueue 会引入「排队」语义 ——
                //    请求在队列里等，超时计时就从「进入业务」推迟到了「开始处理」，那是新行为。
                new SynchronousQueue<>(),
                r -> {
                    Thread t = new Thread(r, "obs-http-worker");
                    t.setDaemon(true);
                    return t;
                },
                // 饱和 ⇒ 抛 RejectedExecutionException，由调用点转成**裸 503**（见 doFilter）。
                // 刻意不用 CallerRunsPolicy：那会把业务搬回容器线程，等于「超时保护整条失效」。
                new ThreadPoolExecutor.AbortPolicy());
    }

    private static final Logger log = LoggerFactory.getLogger(HttpMiddleware.class);

    private HttpMiddleware() {
    }

    /**
     * Spring 化生命周期钩子：关闭 {@link #HTTP_EXECUTOR}。
     * <p>推荐在业务模块的 {@code @PreDestroy} / {@code DisposableBean.destroy()} 里调用。
     * <p><b>幂等</b>：重复调用安全。
     */
    public static void shutdown() {
        HTTP_EXECUTOR.get().shutdownNow();
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
                    flushCaptured(httpResp, rec, serviceName, httpReq, duration, currentOtelContext());
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
                final long startNanos = System.nanoTime();

                // ===== 2026-10-25 新增：gRPC Context（客户端断连 ⇒ 在飞下游 RPC 立刻中止）=====
                //
                // Go 出处：Sea-RideTheWind_golang/service/common/observability/http.go:80-88
                //     case <-ctx.Done():
                //         if errors.Is(ctx.Err(), context.Canceled) { statusCode = httpClientClosedRequest }  // 499
                //   ⇒ Go 的 ctx 一取消，所有 ctx-aware 的下游调用（grpc.Dial）**立刻**返回。
                //   RAG 没有 ctx 传播 ⇒ 本 ref 让容器线程在「探针判定断连」时
                //   cancel 这个 io.grpc.Context ⇒ 在飞的 ClientCallImpl 立刻中止。
                //
                // ⚠️ 反射/版本细节：grpc-api 1.63 的 io.grpc.Context **没有 makeCurrent()**，
                //   只有 attach()/detach(Context) ⇒ 这里用官方推荐的 attach/detach 配对写法。
                // ⚠️ Context.current() 永远不返回 CancellableContext（返回「不可取消的替身」）
                //   ⇒ 但替身的 cancellableAncestor 指向我们的 CancellableContext
                //   ⇒ ClientCallImpl 在**发起调用的线程**上抓到的 Context 仍能把取消传回来
                //   （grpc-core 1.63 ClientCallImpl.java:119 抓 Context.current()，:363 挂 listener）。
                final java.util.concurrent.atomic.AtomicReference<io.grpc.Context.CancellableContext>
                        grpcContextRef = new java.util.concurrent.atomic.AtomicReference<>();

                // ⚠️ 预算必须在 submit **之前**起算：Go 侧 timeouthandler.go:67 就是在
                // 起 goroutine 之前 context.WithTimeout 的。若从 worker 真正开始才起算，
                // 「等线程」这段时间就不算进预算，超时窗口会比 Go 长。
                Instant start = Instant.now();
                final long deadlineNanos = startNanos + timeout.toNanos();

                Future<?> workerFuture;
                try {
                    workerFuture = httpExecutor().submit(() -> {
                    // 先进上下文，再跑业务；finally 必须还原，避免线程池复用时串味
                    RequestAttributes prevAttrs = RequestContextHolder.getRequestAttributes();
                    Map<String, String> prevMdc = MDC.getCopyOfContextMap();
                    // ⚠️ deadline 必须设在**worker 线程**上：业务跑在这里，
                    // 容器线程的 ThreadLocal 对它不可见（最容易写错的一处）。
                    GoStyleRequestDeadline.begin(deadlineNanos);
                    try {
                        if (requestAttributes != null) {
                            RequestContextHolder.setRequestAttributes(requestAttributes);
                        }
                        if (mdcSnapshot != null) {
                            MDC.setContextMap(mdcSnapshot);
                        }
                        try (io.opentelemetry.context.Scope ignoredScope = parentOtelCtx.makeCurrent()) {
                            // ★ gRPC Context 必须在 worker 线程上 attach：
                            //   下游 gRPC 调用是在 chain.doFilter 里发起的，
                            //   ClientCallImpl 在**调用线程**上抓 Context.current()
                            //   ⇒ 只有在 worker 上 current，才抓得到我们这个可取消的 Context。
                            io.grpc.Context.CancellableContext grpcCtx =
                                    io.grpc.Context.current().withCancellation();
                            grpcContextRef.set(grpcCtx);
                            io.grpc.Context previousGrpcCtx = grpcCtx.attach();
                            try {
                                chain.doFilter(httpReq, rec);
                            } finally {
                                grpcCtx.detach(previousGrpcCtx);
                            }
                        }
                    } catch (Throwable t) {
                        // 标记 timedOutByScheduler 已经在主线程里写好了；这里只记异常
                        // IOException 中含 "ClientAbort" / "EOFException" → 视作客户端断连（499）
                        // 其他 → 视作 panic（500）
                        panicHolder[0] = t;
                    } finally {
                        doneLatch.countDown();
                        // ⚠️ 必须清：worker 来自线程池，残留会把下一个请求的 deadline
                        // 污染成上一个的（静默串味，§3.3）。
                        GoStyleRequestDeadline.clear();
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
                } catch (RejectedExecutionException saturated) {
                    // ── worker 池饱和 ⇒ 容量拒绝（2026-10-22 新增路径）──
                    // 形状刻意对齐 Go rest/handler/maxconnshandler.go:12-36 的裸 503：
                    //   只有状态码，**无 body、无 Content-Type**、不走 envelope。
                    // 与「超时 503」刻意**不同**：超时那条走 writeTimeoutResponse（有 body）。
                    // 理由：两者的可观察含义不同 —— 容量不足是「现在别来」，超时是「来不及了」。
                    log.warn("http worker pool saturated (max={}), shedding request {}",
                            MAX_HTTP_WORKERS, httpReq.getRequestURI());
                    Context rejCtx = currentOtelContext();
                    Span rejSpan = Span.fromContext(rejCtx);
                    if (rejSpan.isRecording()) {
                        rejSpan.setAttribute(AttributeKey.stringKey("service.name"), serviceName);
                        rejSpan.setAttribute(AttributeKey.stringKey("http.route"), httpReq.getRequestURI());
                        rejSpan.setAttribute(AttributeKey.stringKey("http.method"), httpReq.getMethod());
                        rejSpan.setAttribute(AttributeKey.longKey("http.status_code"), 503L);
                    }
                    markTimeout(rejCtx, new java.util.concurrent.TimeoutException("http worker pool saturated"),
                            Duration.between(start, Instant.now()),
                            httpAttrs(serviceName, httpReq, 503));
                    httpResp.setStatus(503);
                    return;
                }

                long timeoutMs = timeout.toMillis();
                // 容器线程等 latch 到点即返回：精度对齐 Go 的 select{<-ctx.Done()}
                // （旧实现 Thread.sleep(10) 轮询最多晚 10ms 才判超时）
                //
                // ===== 2026-10-25：单次等待 ⇒ 「切片等待 + 周期主动探测」 =====
                // 动机：Tomcat 在 HTTP/1.1 下**不向应用层发断连信号** ⇒ 容器线程只有
                //   「往 socket 写」时才知道客户端已经走了 ⇒ 在飞的下游 gRPC 只能等自己的 deadline。
                //   改成切片等待后，每片醒来探一次 ⇒ 断连能被**提前**发现并立刻取消下游 RPC。
                //
                // ⚠️ **总预算不变**：切片只是把「一次性等 timeout」拆成「若干次等 min(间隔, 剩余)」，
                //   剩余量每轮按**真实经过时间**重算 ⇒ 总窗口既不延长也不缩短。
                // ⚠️ 探测本身是 fail-open：探不到（UNAVAILABLE）就什么都不做，继续等。
                boolean workerDone;
                boolean clientDisconnected = false;
                boolean probeBudgetLogged = false;
                try {
                    final long waitStartNanos = System.nanoTime();
                    final long budgetNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMs);
                    workerDone = false;
                    while (true) {
                        long remainingNanos = budgetNanos - (System.nanoTime() - waitStartNanos);
                        if (remainingNanos <= 0L) {
                            break;
                        }
                        long sliceMs = Math.max(1L, Math.min(
                                GoStyleClientDisconnectProbe.probeIntervalMillis(),
                                TimeUnit.NANOSECONDS.toMillis(remainingNanos)));
                        workerDone = doneLatch.await(sliceMs, TimeUnit.MILLISECONDS);
                        if (workerDone) {
                            break;
                        }
                        // 预算用尽 ⇒ 落到下面的超时分支（**不缩短也不延长**总窗口）
                        if (budgetNanos - (System.nanoTime() - waitStartNanos) <= 0L) {
                            break;
                        }
                        GoStyleClientDisconnectProbe.State probeState =
                                GoStyleClientDisconnectProbe.probe(httpReq, httpResp);
                        if (probeState == GoStyleClientDisconnectProbe.State.DISCONNECTED) {
                            clientDisconnected = true;
                            break;
                        }
                        if (probeState == GoStyleClientDisconnectProbe.State.BUDGET_EXHAUSTED) {
                            // 🔴 2026-10-27：本请求的 1xx 行数预算已用尽 ⇒ **不再写探针字节**。
                            //   ⚠️ **等待语义完全不变**（仍等到 worker 完成或超时 ⇒ 总窗口不变）
                            //   ⇒ 唯一代价：超长请求里客户端中途断连**不再被提前发现**
                            //   ⇒ 在飞的下游 gRPC 要等写最终响应时才被取消（而非提前取消）
                            //   ⇒ 这是「保住客户端能解析响应」换来的取舍（详见探针类 javadoc）。
                            //   ⇒ 只记一次（每请求最多一条日志，避免刷屏）。
                            if (!probeBudgetLogged) {
                                probeBudgetLogged = true;
                                log.info("[HTTP] 1xx 探针行数预算用尽（cap={}）⇒ 本请求不再写探针字节"
                                                + "（客户端断连将只能等写最终响应时才发现）",
                                        GoStyleClientDisconnectProbe.maxProbeLines());
                            }
                            continue;
                        }
                        // ALIVE / UNAVAILABLE ⇒ 什么都不做，继续等（fail-open）
                    }
                } catch (InterruptedException ie) {
                    // 容器关闭 / 请求被容器取消：Go 侧对应 ctx.Err() == context.Canceled
                    Thread.currentThread().interrupt();
                    // 🔴 2026-10-25（第二十二轮 · global）：**先标记、后中断**（同超时分支的理由）
                    rec.markClientCancelled();
                    workerFuture.cancel(true);
                    markTimeout(currentOtelContext(), new java.util.concurrent.TimeoutException("client cancelled: " + ie.getMessage()),
                            Duration.between(start, Instant.now()), httpAttrs(serviceName, httpReq, HTTP_CLIENT_CLOSED_REQUEST));
                    rec.writeTimeoutResponseQuietly(HTTP_CLIENT_CLOSED_REQUEST, httpReq);
                    return;
                }

                if (clientDisconnected) {
                    // ===== 探针判定「客户端已断连」⇒ 对齐 Go 的 ctx.Err()==context.Canceled 分支 =====
                    //
                    // ① 先取消 gRPC Context ⇒ **在飞的下游 RPC 立刻中止**（Go：ctx 取消同步传播）
                    //    ⚠️ 必须排在 workerFuture.cancel(true) 之前：
                    //    线程中断只能打断「阻塞在 interruptible 调用上」的代码，
                    //    而**异步**下游调用（future / observer）不受线程中断影响 ⇒ 只有 Context 能取消它。
                    io.grpc.Context.CancellableContext grpcCtx = grpcContextRef.get();
                    if (grpcCtx != null) {
                        grpcCtx.cancel(null);
                    }
                    // ② 中断 worker 线程（对齐 Go：ctx 取消后所有 ctx-aware 调用立即返回）
                    //    🔴 2026-10-25（第二十二轮 · global）：**先标记、后中断** ——
                    //    recordClientDisconnected() 里也会 markClientCancelled()，但它在中断**之后**，
                    //    ⇒ 业务可能在标记落地前就 commit 了响应 ⇒ 499 响应被抢走。
                    //    ⇒ 这里提前标记（同超时分支的理由）。
                    rec.markClientCancelled();
                    workerFuture.cancel(true);
                    // ③ 记录 499 + 写响应 —— **复用已有的 499 路径**（flushCaptured 里的那一套）
                    recordClientDisconnected(rec, currentOtelContext(), serviceName, httpReq,
                            Duration.between(start, Instant.now()));
                    // 🔴 走 rec ⇒ 与 worker 线程对底层的写互斥（见超时分支的注释）
                    rec.writeTimeoutResponseQuietly(HTTP_CLIENT_CLOSED_REQUEST, httpReq);
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
                        writeTimeoutResponseQuietly(httpResp, statusCode, httpReq);
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
                    //
                    // 🔴 2026-10-25（第二十二轮 · global）：**先标记、后中断**。
                    //   Go 出处：service/common/observability/http.go 的 `case <-ctx.Done():`
                    //     rec.markTimedOut()      // ← 先把业务写封掉
                    //     http.Error(w, ...)      // ← 再写超时响应
                    //   ⇒ Go 的业务 goroutine 被 ctx 取消的时刻与 markTimedOut 同一瞬间，
                    //     **但它写的是 rec（纯 buffer），碰不到 socket** ⇒ 无论怎么交错都安全。
                    //   ⇒ RAG 侧要拿到同样的保证，前提是「超时标记」必须**早于**「业务被唤醒」，
                    //     否则业务可能在标记落地前就把响应 commit 掉（见下面 markTimedOut 的注释）。
                    rec.markTimedOut();
                    // 取消业务线程（对应 Go 里 ctx 被 cancel，所有 ctx-aware 调用立即返回）
                    workerFuture.cancel(true);
                    int statusCode = 503;
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
                    //
                    // 🔴 2026-10-25（第二十二轮 · global）：走 rec ⇒ **与 worker 线程对底层的写互斥**
                    //   （否则两个线程同时改 Tomcat 的 MimeHeaders ⇒ createHeader() 的 count++ 交错
                    //     ⇒ 数组留 null 洞 ⇒ 读头 NPE ⇒ 响应一个字都发不出去）
                    rec.writeTimeoutResponseQuietly(statusCode, httpReq);
                } else {
                    markHttpResult(currentOtelContext(), serviceName, httpReq, rec.statusCode(), rec.bodyBytes(), duration, finalSlow);
                    flushCaptured(httpResp, rec, serviceName, httpReq, duration, currentOtelContext());
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
     * 把捕获的响应写给客户端；<b>写失败且判定为「客户端已断连」⇒ 记 499</b>（不再向上抛）。
     *
     * <p><b>为什么 499 的判据必须落在这里（DRIFT-HOTAPI-025 / global T-4）</b>：
     * <ul>
     *   <li><b>Go</b>（{@code service/common/observability/http.go:80-88}）：
     *       <pre>
     *       case &lt;-ctx.Done():
     *           statusCode := http.StatusServiceUnavailable
     *           if errors.Is(ctx.Err(), context.Canceled) { statusCode = httpClientClosedRequest }
     *           MarkTimeout(ctx, ctx.Err(), duration, httpAttrs(serviceName, req, statusCode)...)
     *           http.Error(w, httpTimeoutBody, statusCode)
     *       </pre>
     *       ⇒ Go 的 499 由 <b>ctx 被取消</b>驱动；而 Go 的 {@code net/http} 在<b>客户端断开</b>时
     *       会取消 {@code r.Context()} ⇒ 断连 ⇒ 必定 499，且 <b>在断连后 1~3ms 内</b>就定型。</li>
     *   <li><b>RAG</b>：Servlet 容器<b>不向应用暴露「客户端已断连」信号</b>
     *       （真 Tomcat 11.0.24 实测：async + {@code AsyncListener.onError} <b>不触发</b>；
     *       Tomcat 自己的 {@code AbstractProcessorLight} 对 {@code SocketEvent.DISCONNECT}
     *       明写 "Do nothing here, just wait for it to get recycled"
     *       ⇒ 详见台账 T-4「框架限制」一节）。
     *       ⇒ 唯一能观察到断连的时刻，就是 <b>往 socket 写响应的那一刻</b>。</li>
     * </ul>
     *
     * <p>⇒ RAG 的 499 判据 =「<b>filter 线程写 socket 失败</b>」。
     * <b>旧实现在写失败时让 IOException 直接逃出 doFilter</b> ⇒ ① 异常栈噪音直冲容器日志
     * ② 记录里留下的是 <b>200</b>（{@code markHttpResult} 已在写之前跑完）⇒ 断连事件<b>完全不可见</b>。
     *
     * <p><b>与 Go 的两处已知差异（台账留痕，不掩盖）</b>：
     * <ol>
     *   <li><b>定型时点</b>：Go 在断连瞬间定 499；RAG 要等业务跑完、写到 socket 才知道
     *       ⇒ RAG 的 499 必然<b>晚于</b> Go 的。</li>
     *   <li><b>在飞 gRPC</b>：Go 的 ctx 取消立刻中止下游调用；RAG 没有断连信号
     *       ⇒ 在飞调用仍要等到自己的 deadline 才结束（<b>框架限制</b>，非本类可修）。</li>
     * </ol>
     */
    private static void flushCaptured(HttpServletResponse httpResp,
                                      ObservabilityResponseWriter rec,
                                      String serviceName,
                                      HttpServletRequest httpReq,
                                      Duration duration,
                                      Context ctx) throws IOException {
        try {
            // ⚠️⚠️ **保形**：必须先把 Content-Length 定下来，否则下面那次 flush 会让响应
            //   退化成 `Transfer-Encoding: chunked`（真 Tomcat 实测：2026-10-25）。
            //   原因：flush ⇒ 响应**提前提交** ⇒ Tomcat 再也没机会在收尾时按缓冲内容算长度
            //   ⇒ 而 Go 的 net/http 对「装得下」的响应体是**算 Content-Length** 的
            //   ⇒ 不设它就是**新增的 wire 差异**（Go: Content-Length / RAG: chunked）。
            //   阈值取 Tomcat 自己的响应缓冲大小（{@code ExpandBuffer} 的 defaultBufferSize = 8192）：
            //   装得下 ⇒ 与 Tomcat 收尾时的算法一致（Content-Length）；
            //   装不下 ⇒ 本来就是 chunked（缓冲区溢出时 Tomcat 已提交），保持不变。
            byte[] captured = rec.bodyBytes();
            if (captured.length <= TOMCAT_RESPONSE_BUFFER_SIZE
                    && httpResp.getHeader("Content-Length") == null
                    && statusAllowsContentLength(httpResp.getStatus())) {
                httpResp.setContentLength(captured.length);
            }
            rec.flushTo(httpResp);
            // ⚠️⚠️ **必须显式 flush**，否则断连根本观测不到（2026-10-25 真 Tomcat 实测）：
            //   Tomcat 的 OutputBuffer 默认缓冲 8192 字节 ⇒ 小 body 写进去只进了缓冲区，
            //   **socket 一个字节都没发** ⇒ 客户端已 RST 也照样「写成功」⇒ 不抛异常
            //   ⇒ 499 分支永远进不去。
            //   `flush()` ⇒ `OutputBuffer.doFlush(true)` ⇒ 真网络 flush ⇒
            //   写失败被 Tomcat 包成 `ClientAbortException`（IOException 子类）抛出来。
            //   ⚠️ 语义中性：本次 flush 之后 filter 立即返回，Tomcat 本来也要在收尾时提交响应
            //   ⇒ 只是把提交提前了「几行代码」的距离，客户端可见行为不变。
            httpResp.getOutputStream().flush();
        } catch (IOException io) {
            if (!isClientCancelledException(io)) {
                // 非「客户端断连」的 IO 失败（例如后端存储坏了）⇒ 原样上抛，保持原有行为。
                throw io;
            }
            // ===== 客户端已断连 ⇒ 记 499（对齐 Go 的 ctx.Canceled 分支）=====
            recordClientDisconnected(rec, ctx, serviceName, httpReq, duration);
        }
    }

    /**
     * 记录「客户端已断连」（<b>499</b>）—— 对齐 Go 的 {@code ctx.Err() == context.Canceled} 分支。
     *
     * <p><b>唯一的 499 记录入口</b>：① {@link #flushCaptured} 写 socket 失败（老路径）；
     * ② {@code doFilter} 里「主动探测」判定断连（2026-10-25 新增）⇒ 两处走同一套代码，
     * 避免「同一个语义两套实现」在后续演进里漂移。
     *
     * <p>Go 出处：{@code Sea-RideTheWind_golang/service/common/observability/http.go:80-88}
     * <pre>
     *   case &lt;-ctx.Done():
     *       statusCode := http.StatusServiceUnavailable
     *       if errors.Is(ctx.Err(), context.Canceled) { statusCode = httpClientClosedRequest }  // 499
     *       MarkTimeout(ctx, ctx.Err(), duration, httpAttrs(serviceName, req, statusCode)...)
     *       http.Error(w, httpTimeoutBody, statusCode)
     * </pre>
     *
     * @param rec       响应记录器（置 clientCancelled 标志 ⇒ 后续写变成 no-op）
     * @param ctx       当前 OTel Context（打标用；**跑在容器线程**上）
     * @param serviceName 服务名
     * @param httpReq   当前请求
     * @param duration  请求已耗时
     */
    private static void recordClientDisconnected(ObservabilityResponseWriter rec,
                                                  Context ctx,
                                                  String serviceName,
                                                  HttpServletRequest httpReq,
                                                  Duration duration) {
        rec.markClientCancelled();
        markTimeout(ctx,
                new java.util.concurrent.TimeoutException("context canceled"),
                duration,
                httpAttrs(serviceName, httpReq, HTTP_CLIENT_CLOSED_REQUEST));
        // ⚠️ 为什么还要打一条日志：Go 侧 499 之所以「可见」是因为 go-zero 的 access log
        //   会打 `[HTTP] 499 - GET /x - ip - duration`；RAG **没有 access log**
        //   ⇒ 若只写 span，没接 trace 后端时这条事件依然不可见（实测：本仓库的 OTEL
        //   exporter 默认关闭）⇒ 必须留一条日志，否则「断连不可见」的老问题只修了一半。
        log.warn("[HTTP] {} - {} {} - {} - duration {}ms",
                HTTP_CLIENT_CLOSED_REQUEST,
                httpReq.getMethod(),
                httpReq.getRequestURI(),
                remoteAddrOf(httpReq),
                duration.toMillis());
    }

    /**
     * 客户端 IP（不含端口 —— Servlet API 只暴露 IP，Go 的 {@code r.RemoteAddr} 带端口）。
     * <p>属<b>表述差异</b>，不影响判定。
     */
    private static String remoteAddrOf(HttpServletRequest req) {
        if (req == null) {
            return "-";
        }
        String addr = req.getRemoteAddr();
        return addr == null || addr.isEmpty() ? "-" : addr;
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
        // ⚠️ 2026-10-24 补：Go 的 net/http `http.Error` 还会写
        //   `h.Set("X-Content-Type-Options", "nosniff")`（Go 1.11 起）
        //   ⇒ 少这一个头就是**字节级不一致**（真 Tomcat 实测：RAG 侧原本没有）。
        //   ⇒ 补上，与 Go 对齐。
        //   ⚠️ 已知**不可修**的残差：上面那个 Content-Type 里分号后的**空格** ——
        //   Tomcat 的 setContentType 会把 Content-Type 解析成 MediaType 再序列化，
        //   序列化时吃掉空格 ⇒ 发出 "text/plain;charset=utf-8"；
        //   实测 `setHeader("Content-Type", ...)` 也一样（Tomcat 内部转手调 setContentType）
        //   ⇒ 该空格在 Tomcat 上无任何写法能产出 ⇒ 归「框架限制」，不改。
        resp.setHeader("X-Content-Type-Options", "nosniff");
        // 🔴 2026-10-25（第二十二轮 · global）：**显式定 Content-Length** ——
        //   Go 的 `http.Error` → `fmt.Fprintln(w, error)` 写进 net/http 的缓冲 ⇒
        //   收尾时 net/http 按缓冲内容算长度 ⇒ 线上是 `Content-Length: 15`。
        //   而 RAG 这里用 `getWriter().write(...)` + `flush()` ⇒ flush 即提交
        //   ⇒ Tomcat 已经没机会算长度 ⇒ 退化成 `Transfer-Encoding: chunked` ⇒ **wire 不一致**。
        //   ⇒ 显式写死长度（body 固定是 "Request Timeout\n" = 15 字节）⇒ 与 Go 逐字节对齐。
        //   （与 `flushCaptured` 里那段「保形」注释是同一条理由。）
        resp.setContentLength(HTTP_TIMEOUT_BODY.length() + 1);
        resp.getWriter().write(HTTP_TIMEOUT_BODY);
        resp.getWriter().write('\n');
        resp.getWriter().flush();
    }

    /**
     * 写超时 / 断连响应；<b>写失败（客户端已走）不向上抛</b>。
     *
     * <p><b>为什么</b>：Go 的 {@code http.Error} 内部是 {@code fmt.Fprintln(w, error)}，
     * 而 {@code Fprintln} <b>忽略写错误</b> ⇒ Go 在「客户端已断连」时只是「写不进去」，
     * <b>不会</b>把异常抛给框架。RAG 旧实现让 IOException 直接逃出 {@code doFilter}
     * ⇒ 每次断连都在容器日志里留一坨异常栈。
     *
     * <p>⇒ 这里吞掉写异常（记一条日志），状态码与记录<b>不受影响</b>：
     * 503 / 499 的判定早已在写之前完成。
     */
    private static void writeTimeoutResponseQuietly(HttpServletResponse resp, int statusCode,
                                                     HttpServletRequest req) {
        try {
            writeTimeoutResponse(resp, statusCode);
        } catch (IOException io) {
            if (isClientCancelledException(io)) {
                log.warn("[HTTP] {} - {} {} - {} - client closed before response was written",
                        statusCode,
                        req == null ? "-" : req.getMethod(),
                        req == null ? "-" : req.getRequestURI(),
                        remoteAddrOf(req));
            } else {
                log.warn("[HTTP] {} - failed to write response: {}", statusCode, io.toString());
            }
        }
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

        /**
         * 🔴 **底层响应的写锁**（2026-10-25 · 第二十二轮 · global）。
         *
         * <p><b>为什么必须有</b>：worker 线程（业务）与容器线程（超时 / 断连分支）<b>都可能碰底层响应</b>。
         * Tomcat 11 的 {@code MimeHeaders.createHeader()}（{@code tomcat-embed-core-11.0.24}）：
         * <pre>
         *   if ((mh = headers[count]) == null) { headers[count] = mh = new MimeHeaderField(); }
         *   count++;
         * </pre>
         * ⇒ 两个线程同时加头时，各自 {@code count++} 却只填了一个槽位 ⇒ <b>数组里留 null 洞</b>
         * ⇒ 随后任何线程读头（{@code Http11Processor.prepareResponse} 读 {@code "Date"}）
         * ⇒ {@code headers[i].getName()} ⇒ <b>NPE</b> ⇒ 响应一个字都发不出去（客户端只看到 EOF）。
         *
         * <p>⇒ 这把锁把「对底层响应的所有修改」串行化 ⇒ 洞不可能出现。
         *
         * <p>⚠️ <b>锁是止血，不是根治</b>：根治是「worker 根本不碰底层」⇒ 见 {@link #flushBuffer()}。
         * 两者都要：锁挡掉「标记落地前已在飞行中的那次写」，no-op flushBuffer 挡掉「worker 抢先提交」。
         */
        private final java.util.concurrent.locks.ReentrantLock underlyingLock =
                new java.util.concurrent.locks.ReentrantLock();

        /** 对底层响应做一次写：<b>加锁</b> + <b>标志检查</b>（超时 / 断连后一律 no-op）。 */
        private void writeToOriginal(OriginalAction action) {
            underlyingLock.lock();
            try {
                if (timedOut || clientCancelled) {
                    return;
                }
                action.run((HttpServletResponse) this.original);
            } finally {
                underlyingLock.unlock();
            }
        }

        /** 写动作（不抛 IOException 的那批）。 */
        @FunctionalInterface
        private interface OriginalAction {
            void run(HttpServletResponse resp);
        }

        ObservabilityResponseWriter(HttpServletResponse original) {
            super(original);
            this.original = original;
        }

        @Override
        public void setStatus(int sc) {
            // 🔴 2026-10-25（第二十二轮 · global）：加锁 + 标志检查（见 writeToOriginal 的注释）
            writeToOriginal(resp -> {
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
                resp.setStatus(sc);
            });
        }

        @Override
        public void sendError(int sc) throws IOException {
            underlyingLock.lock();
            try {
                if (timedOut || clientCancelled) {
                    return;
                }
                this.status = sc;
                // 与 setStatus 同步 propagate（见 setStatus 的注释）
                ((HttpServletResponse) this.original).sendError(sc);
            } finally {
                underlyingLock.unlock();
            }
        }

        @Override
        public void sendError(int sc, String msg) throws IOException {
            underlyingLock.lock();
            try {
                if (timedOut || clientCancelled) {
                    return;
                }
                this.status = sc;
                // 与 setStatus 同步 propagate（见 setStatus 的注释）
                ((HttpServletResponse) this.original).sendError(sc, msg);
            } finally {
                underlyingLock.unlock();
            }
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
            writeToOriginal(resp -> {
                capturedHeaders.put(name, value);
                // 关键修复（2026-10-22 E-1 实测取证）：与 setStatus 同构的问题，头也要**实时** propagate。
                //
                // 旧实现只写 capturedHeaders，等 flushTo 再统一写回 underlying。实测（真实 Tomcat）：
                //   200 JSON 响应   → Content-Type: application/json 正常
                //   400 text/plain   → **Content-Type 完全丢失**，且 Transfer-Encoding: chunked
                //                       + Connection: close
                // 原因是 flushTo 在 chain.doFilter() **之后**才执行，此时下游
                // outputMessage.flush() → wrapper.flushBuffer() → 底层 Tomcat 已 committed，
                // 此时再 dst.setHeader(...) 会被 Tomcat **静默忽略**（不报错、不断言）。
                //
                // ⚠️ 这正是 §3.3「框架机制必须验证实际生效」第四类的又一次实例：
                // 「代码里 headers.set(...) 明明写了」不等于「客户端收到的响应里有」。
                // 它此前 4 轮对拍全绿，只因为 difftest **只比 body 不比响应头**，
                // 而 body 恰好是对的（DRIFT-006 对齐的正是正文）。
                resp.setHeader(name, value);
            });
        }

        @Override
        public void addHeader(String name, String value) {
            writeToOriginal(resp -> {
                capturedHeaders.merge(name, value, (a, b) -> a + "," + b);
                // 与 setHeader 同理：实时 propagate（见上面注释）
                resp.addHeader(name, value);
            });
        }

        /**
         * Content-Type 单独 override。
         *
         * <p>{@link jakarta.servlet.http.HttpServletResponseWrapper} 的默认实现
         * {@code setContentType(type) → response.setContentType(type)} 是<b>直接透传</b>的，
         * 本类未 override 它。为避免「setContentType 直通、setHeader 进 buffer」这种
         * 两套语义并存（本轮漂移的间接成因），这里显式接管，走与 setHeader 相同的实时路径。
         */
        @Override
        public void setContentType(String type) {
            writeToOriginal(resp -> {
                if (type != null) {
                    capturedHeaders.put("Content-Type", type);
                }
                resp.setContentType(type);
            });
        }

        /**
         * 🔴🔴 **超时 / 断连之后，不再把底层响应提交出去**（2026-10-25 · 第二十二轮 · global）。
         *
         * <p><b>Go 出处</b>：{@code Sea-RideTheWind_golang/service/common/observability/http.go}
         * <pre>
         *   type observabilityResponseWriter struct {
         *       header http.Header
         *       body   bytes.Buffer
         *       status int
         *       mu       sync.Mutex
         *       timedOut bool
         *   }
         *
         *   func (w *observabilityResponseWriter) Write(p []byte) (int, error) {
         *       w.mu.Lock(); defer w.mu.Unlock()
         *       if w.timedOut { return 0, http.ErrHandlerTimeout }
         *       return w.body.Write(p)
         *   }
         *   func (w *observabilityResponseWriter) flushTo(dst http.ResponseWriter) { ... }
         * </pre>
         * ⇒ Go 的 writer 只实现 {@code Header/WriteHeader/Write} ⇒ 它<b>根本没有</b>「提交响应」的能力；
         * 唯一把 buffer 落到 socket 的地方是 {@code flushTo(dst)}，而 {@code flushTo} 只在
         * <b>done 分支</b>（{@code case <-done:}）被调用；超时分支是
         * {@code http.Error(w, ...)}（写<b>原始</b> w）⇒ <b>socket 的写者永远只有一个</b>。
         *
         * <p><b>RAG 侧此前</b>：本类<b>没有</b>覆写 {@code flushBuffer()}
         * ⇒ {@code ServletResponseWrapper.flushBuffer()} 直接透传给底层
         * ⇒ <b>worker 线程能提交响应</b> ⇒ 两个写者 ⇒ ① 状态码被抢（客户端收到 200 而不是 503）
         * ② Tomcat 的 {@code MimeHeaders} 被写坏 ⇒ NPE ⇒ 客户端只看到 EOF。
         * ⇒ 生产实证：2026-10-25 全仓回归唯一一条红
         * （{@code MessageApiTimeoutRealTomcatTest.slowDownstreamYieldsGoTimeoutContract}）
         * ⇒ 客户端 {@code IOException: HTTP/1.1 header parser received no bytes}。
         *
         * <p><b>为什么改成 no-op 不会影响正常路径</b>：正常路径下<b>没有人</b>调
         * {@code flushBuffer()}（Spring 写 body 走的是本类的缓冲流，不会 flush）⇒
         * 真正提交响应的是 {@code flushCaptured}（容器线程）⇒ 行为不变。
         * ⇒ 唯一会调它的是<b>异常处理路径</b>：{@code @ExceptionHandler} 返回
         * {@code ResponseEntity} ⇒ Spring 的 {@code HttpEntityMethodProcessor.handleReturnValue}
         * ⇒ {@code ServletServerHttpResponse.flush()} ⇒ {@code flushBuffer()}
         * ⇒ 那正是要掐掉的那次提交（超时被 interrupt 时，异常处理器正好在写响应）。
         *
         * <p>⚠️ SSE（{@code Accept: text/event-stream}）<b>不走本中间件</b>
         * （{@code shouldSkipHTTPTimeout} 会短路）⇒ 不受影响。
         *
         * <p>⚠️ <b>为什么是「超时后 no-op」而不是「永远 no-op」</b>：Servlet 规范里
         * 「commit 之后设 status 无效」是<b>容器语义</b>（{@code HttpMiddlewareTest#statusSetAfterCommitIsIgnoredByServletSemantics}
         * 把它固化成显式契约）⇒ 正常路径下 {@code flushBuffer()} 必须<b>照常透传</b>、
         * 该提交还得提交 ⇒ 否则那条契约就失去了它要守的语义。
         * ⇒ 只在<b>超时 / 断连之后</b>把它掐掉 —— 那正是 worker 不该再碰底层响应的时刻。
         */
        @Override
        public void flushBuffer() throws IOException {
            underlyingLock.lock();
            try {
                if (timedOut || clientCancelled) {
                    // Go: 超时后业务写被丢弃（ErrHandlerTimeout）⇒ worker 不许再提交响应
                    return;
                }
                ((HttpServletResponse) this.original).flushBuffer();
            } finally {
                underlyingLock.unlock();
            }
        }

        int statusCode() {
            return status;
        }

        byte[] bodyBytes() {
            return body.toByteArray();
        }

        void markTimedOut() {
            // 🔴 2026-10-25（第二十二轮 · global）：**与对底层的写同一把锁** ⇒
            //   「置标志」与「写底层」不会交错 ⇒ 不存在「标志还没落地，worker 就把响应提交了」的窗口。
            underlyingLock.lock();
            try {
                this.timedOut = true;
            } finally {
                underlyingLock.unlock();
            }
        }

        void markClientCancelled() {
            underlyingLock.lock();
            try {
                this.clientCancelled = true;
            } finally {
                underlyingLock.unlock();
            }
        }

        /**
         * 写超时 / 断连响应（<b>容器线程</b>用）—— 与 worker 线程对底层的写<b>互斥</b>。
         *
         * <p>🔴 2026-10-25（第二十二轮 · global）：这是「双写 MimeHeaders ⇒ NPE」的最后一块拼图 ——
         * 容器线程写 503（{@code setStatus} + {@code setContentType} + {@code setHeader} + body）
         * 与 worker 线程的晚写<b>同时</b>进 Tomcat 的头数组 ⇒ 必须串行化。
         *
         * <p>⚠️ 这里<b>不能</b>走 {@code writeToOriginal}：那个方法在 timedOut / clientCancelled
         * 时是 no-op，而超时响应恰恰要在这两个标志已置位时写出去 ⇒ 这里直接持锁写。
         */
        void writeTimeoutResponseQuietly(int statusCode, HttpServletRequest req) {
            underlyingLock.lock();
            try {
                HttpMiddleware.writeTimeoutResponseQuietly(
                        (HttpServletResponse) this.original, statusCode, req);
            } finally {
                underlyingLock.unlock();
            }
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
            // 🔴 2026-10-25（第二十二轮 · global）：持锁 ⇒ 与 worker 线程对底层的写互斥
            //   （done 分支时 worker 已结束 ⇒ 实际上不会争用；持锁是为了**结构上**保证只有一个写者）
            underlyingLock.lock();
            try {
                // 2026-10-22（E-1）更新：setHeader / addHeader / setContentType 均已改为**实时 propagate**，
                // 与 setStatus 同一策略。这里保留二次写入作为兜底：Tomcat 的 setHeader 是
                // **覆盖**语义（不是 add），所以重复写入不会产生重复 header；
                // 它覆盖的是「下游完全没碰过 underlying」的少数路径。
                for (Map.Entry<String, String> e : capturedHeaders.entrySet()) {
                    dst.setHeader(e.getKey(), e.getValue());
                }
                if (body.size() > 0) {
                    dst.getOutputStream().write(body.toByteArray());
                }
            } finally {
                underlyingLock.unlock();
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