package com.rag.common.observability;

import io.grpc.ForwardingServerCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.Status.Code;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.rag.common.observability.Observability.isTimeoutError;
import static com.rag.common.observability.Observability.markBusinessError;
import static com.rag.common.observability.Observability.markDurationAndSlow;
import static com.rag.common.observability.Observability.markSystemError;
import static com.rag.common.observability.Observability.markTimeout;
import static com.rag.common.observability.Observability.timeoutFromMillis;
import static com.rag.common.observability.ObservabilityConstants.DEFAULT_SLOW_THRESHOLD;

/**
 * 对应 Go 版 service/common/observability/rpc.go 的整个文件（122 行）。
 *
 * <p>Go 版核心：
 * <ul>
 *   <li>{@code DisableNativeRpcTimeout(conf *zrpc.RpcServerConf)}：关闭 go-zero 自带的 zrpc 服务端超时</li>
 *   <li>{@code NewUnaryServerInterceptor(timeout, slowThreshold) grpc.UnaryServerInterceptor}：构造服务端拦截器</li>
 *   <li>拦截器内部 goroutine 跑业务、主线程 select，处理 panic / 超时 / done</li>
 * </ul>
 *
 * <p>Java 版用 {@link ServerInterceptor}：
 * <ul>
 *   <li>起 otel server span（与 Go 版 trace.WithSpanKind(SERVER) 对齐）</li>
 *   <li>{@link ScheduledExecutorService} 在 timeout 后调 {@code wrappedCall.close(DEADLINE_EXCEEDED)} 触发超时</li>
 *   <li>包装 {@link ServerCall} 拦截 {@code close}，按 status 走 business / system / timeout 三分支打 span 属性</li>
 *   <li>包装 {@link ServerCall.Listener} 拦截 {@code onCancel}，处理客户端提前断连</li>
 * </ul>
 *
 * <p><b>DRIFT-011 修复</b>：原 Java 版 {@code newUnaryServerInterceptor} 只返回简单 listener，超时/span 全部失效；
 * 现在 {@link #newUnaryServerInterceptor} 真正实现 Go 版语义（超时 + span + 错误分类），
 * 业务方注册到 grpc Server 即可生效。同时保留 {@link #handle} 静态方法供非 grpc Server 场景使用。
 */
public final class RpcInterceptor {

    /**
     * 拦截器专用的超时调度器，单例后台线程。
     * <p>对应 Go 版拦截器 goroutine select 的"主线程等结果"语义。
     *
     * <p><b>Spring 化</b>：保留静态单例（grpc server 拦截器在启动期就注册，每请求都依赖）；
     * 业务模块在 {@code @PreDestroy} 里显式调 {@link #shutdown()} 关闭线程池，
     * 避免依赖 daemon 兜底导致 Spring 容器已关闭而 dispatcher 资源未清理。
     *
     * <p>🔴 <b>为什么是「可重建」的单例，而不是 {@code static final}</b>（2026-10-27 · 修 G1 #8 的连带效应）：
     * 本池是<b>进程级静态</b>的，而 {@link #shutdown()} 会<b>永久终止</b>它
     * ⇒ ⇒ <b>同一个 JVM 里只要存在第二个 Spring 上下文</b>（surefire 一个模块一个 JVM ⇒ 模块内多个上下文），
     * <b>前一个上下文关闭后</b>本池就变成「已终止」
     * ⇒ 后续请求的 {@code schedule(...)} 被拒（{@code RejectedExecutionException}）
     * ⇒ 拦截器返回 {@code INTERNAL} 而不是 {@code DEADLINE_EXCEEDED}
     * （实测症状：{@code ScheduledThreadPoolExecutor[Terminated]}；
     * {@code ArticleGrpcInterceptorWiringTest} 在全量回归里红、<b>隔离跑却绿</b> ⇒ 典型的跨测试污染）。
     * ⇒ ⇒ 改为：<b>池被终止后，下一次使用按需重建</b>（见 {@link #timeoutScheduler()}）。
     * <ul>
     *   <li><b>生产</b>：单上下文 ⇒ shutdown 之后没有下一次使用 ⇒ <b>池保持关闭</b> ⇒ 关闭钩子的意图不变；</li>
     *   <li>线程是 <b>daemon</b> ⇒ 即便重建也不阻止 JVM 退出。</li>
     * </ul>
     */
    private static final AtomicReference<ScheduledExecutorService> TIMEOUT_SCHEDULER =
            new AtomicReference<>(newTimeoutScheduler());

    /**
     * {@link #handle} 方法专用 worker 线程池，替换裸 {@code new Thread(...)}。
     * <p>Spring 化：业务模块在 {@code @PreDestroy} 里显式调 {@link #shutdown()} 关闭。
     *
     * <p>🔴 与 {@link #TIMEOUT_SCHEDULER} <b>同款</b>：进程级静态 + 终止即永久
     * ⇒ 必须同样支持<b>按需重建</b>（见 {@link #handleExecutor()}）。
     */
    private static final AtomicReference<ExecutorService> HANDLE_EXECUTOR =
            new AtomicReference<>(newHandleExecutor());

    private static ScheduledExecutorService newTimeoutScheduler() {
        return Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "obs-rpc-timeout");
            t.setDaemon(true);
            return t;
        });
    }

    private static ExecutorService newHandleExecutor() {
        return Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "obs-rpc-worker");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 取当前的超时调度器；<b>若上一轮 {@link #shutdown()} 已把它终止 ⇒ 按需重建一份新的</b>。
     *
     * <p>CAS + 自旋：并发重建时只会有一个胜出，其余线程直接用胜出的那份 ⇒ 不会漏建、也不会建多份。
     */
    static ScheduledExecutorService timeoutScheduler() {
        while (true) {
            ScheduledExecutorService current = TIMEOUT_SCHEDULER.get();
            if (!current.isShutdown()) {
                return current;
            }
            if (TIMEOUT_SCHEDULER.compareAndSet(current, newTimeoutScheduler())) {
                return TIMEOUT_SCHEDULER.get();
            }
        }
    }

    /**
     * 取当前的 worker 池；<b>若上一轮 {@link #shutdown()} 已把它终止 ⇒ 按需重建一份新的</b>。
     */
    static ExecutorService handleExecutor() {
        while (true) {
            ExecutorService current = HANDLE_EXECUTOR.get();
            if (!current.isShutdown()) {
                return current;
            }
            if (HANDLE_EXECUTOR.compareAndSet(current, newHandleExecutor())) {
                return HANDLE_EXECUTOR.get();
            }
        }
    }

    /**
     * Go 真源 {@code service/common/observability/rpc.go:69} 在超时时给客户端的
     * <b>status description</b>：
     *
     * <pre>
     * Go: err := status.Error(codes.DeadlineExceeded, timeoutCtx.Err().Error())
     *     // timeoutCtx.Err() == context.DeadlineExceeded ⇒ .Error() == "context deadline exceeded"
     * </pre>
     *
     * <p>同一个 {@code err} 还被 {@code MarkTimeout(ctx, err, ...)} 拿去算遥测文案
     * （{@code observability.go:108-112}：{@code msg = err.Error()}）
     * ⇒ Go 侧的 {@code sea.biz_msg} =
     * {@code "rpc error: code = DeadlineExceeded desc = context deadline exceeded"}。
     *
     * <p>⚠️ 旧实现写的是 {@code "rpc timeout after " + timeoutMs + "ms"} ⇒
     * <b>description 与遥测 biz_msg 两侧都不同</b> ⇒ 客户端拿到的错误文案与 Go 不一致
     * ⇒ 记为 global 台账 <b>E30</b>（另见 {@code comment/rpc.md} U10 ·
     * {@code users/user/rpc.md} V-007 —— 三个域各自独立记过同一条）。
     */
    private static final String GO_DEADLINE_EXCEEDED_DESCRIPTION = "context deadline exceeded";

    private RpcInterceptor() {
    }

    /**
     * Spring 化生命周期钩子：关闭 {@link #TIMEOUT_SCHEDULER} 与 {@link #HANDLE_EXECUTOR}。
     * <p>推荐在业务模块的 {@code @PreDestroy} / {@code DisposableBean.destroy()} 里调用，
     * 让线程池关闭时序与 SpringApplication graceful shutdown 一致。
     *
     * <p><b>幂等</b>：重复调用安全（{@code shutdownNow} 重复 OK）。
     */
    public static void shutdown() {
        TIMEOUT_SCHEDULER.get().shutdownNow();
        HANDLE_EXECUTOR.get().shutdownNow();
    }

    /**
     * 对应 Go 版 {@code func DisableNativeRpcTimeout(conf *zrpc.RpcServerConf) time.Duration}。
     *
     * <p>Java 版没有 go-zero 等价物，方法签名简化为直接传 timeoutMillis，返回 Duration。
     */
    public static Duration disableNativeRpcTimeout(long timeoutMillis) {
        return timeoutFromMillis(timeoutMillis);
    }

    /**
     * 对应 Go 版 {@code func NewUnaryServerInterceptor(timeout, slowThreshold time.Duration) grpc.UnaryServerInterceptor}。
     *
     * <p>本拦截器做三件事：
     * <ol>
     *   <li>起一个 SERVER 类型的 otel span（rpc.system / rpc.method / rpc.grpc.status_code）</li>
     *   <li>如果 {@code timeout > 0}，到时调用 {@code wrappedCall.close(DEADLINE_EXCEEDED)} 强制结束</li>
     *   <li>在 wrappedCall.close 时根据 gRPC status 分类：OK → markDurationAndSlow；
     *       DEADLINE_EXCEEDED → markTimeout；业务类 code → markBusinessError；其他 → markSystemError</li>
     * </ol>
     *
     * <p><b>DRIFT-018 已知差异（Java 化方案）</b>：Go 版通过 {@code context.WithTimeout(ctx, timeout)} + select
     * 实现"真取消"——timeout 触发时，handler 拿到的 ctx 立即 Done，所有 ctx-aware 操作（http.Client、sql.DB、grpc.Dial）
     * 会立即中断。
     *
     * <p>Java grpc-java 的 {@link ServerCall#close} 只能从外部"关闭 call"让客户端看到超时，
     * <b>不会</b>主动中断业务 handler 线程。本拦截器的补救手段分<b>两条互不覆盖的路径</b>，
     * <b>不是并列的两层</b>（旧注释写成并列 ⇒ 读者会误以为「gRPC 调用链上也有第 ② 层」）：
     * <ol>
     *   <li><b>【gRPC 路径 · 本方法返回的拦截器】</b>：只有<b>第 ① 层</b> ——
     *       把 deadline 过期时刻<b>绑定到真正跑 handler 的线程</b>
     *       （{@code DeadlineState.bindToCurrent}，作用域自动清理），业务线程可随时读
     *       （{@code onHalfClose} 里绑定 ⇒ 见下方 {@code M7 修复}）。
     *       <br>🔴 <b>这一层是「感知」不是「打断」</b>：超时调度器只做
     *       {@code close(DEADLINE_EXCEEDED)}，<b>不</b>给业务线程发 interrupt
     *       ⇒ handler 若不主动读 {@link DeadlineState#isExpired()}，就会一直跑下去。</li>
     *   <li><b>【非 gRPC 路径 · 仅 {@link #handle} 静态方法】</b>：才有<b>第 ② 层</b> ——
     *       {@code future.get(timeout)} + {@code future.cancel(true)}（→ worker.interrupt()）
     *       让业务线程被真正打断。
     *       <br>⚠️ <b>本拦截器（gRPC 路径）没有第 ② 层</b> —— 这是 grpc-java 的框架限制
     *       （{@code ServerCall#close} 无法取消已在跑的 handler），
     *       <b>Go 也不杀 goroutine</b>（见 global 台账 cross-domain §二·十五·④ 的定性）。</li>
     * </ol>
     * <p>⚠️ 逐行取证：{@code workerFuture.cancel(true)}（真正触发 interrupt）在
     * {@link #handle} 内**仅两处**（超时分支与被 interrupt 分支）；
     * gRPC 路径的 {@code onCancel} 里那个 {@code cancel(false)} 取消的是<b>超时调度任务</b>，
     * <b>不是</b>业务线程。
     *
     * <p>业务 handler 必须实现以下任一机制（无 ctx 传播是 grpc-java 框架限制，不是本组件 bug）：
     * <ul>
     *   <li>在长循环里周期性调用 {@link DeadlineState#isExpired()}，到点抛 {@link java.util.concurrent.TimeoutException}
     *       （<b>gRPC 与 handle 两条路径都适用</b>）</li>
     *   <li>在阻塞 IO / 下游 RPC 前用 {@link DeadlineState#remainingMillis()} 限流
     *       （<b>两条路径都适用</b>）</li>
     *   <li>(<b>仅 {@link #handle} 路径</b>) 捕获 {@link InterruptedException}，
     *       配合 {@code CompletableFuture.cancel(true)} 主动响应
     *       —— gRPC 路径<b>没有</b> interrupt，捕不到</li>
     *   <li>检查线程 interrupt flag（{@link Thread#isInterrupted()}）——
     *       同样<b>仅 {@link #handle} 路径</b>才有意义</li>
     * </ul>
     *
     * <p>推荐模式（<b>仅 {@link #handle} 路径</b>）：业务用
     * {@link java.util.concurrent.CompletableFuture} 包装 handler 调用，
     * 超时触发 cancel(true) → worker.interrupt()，业务线程 catch InterruptedException 立刻退出。
     * <br>⚠️ gRPC 路径<b>不能</b>照抄这个模式 —— 那里没有 interrupt，
     * 只能靠第 ① 层（{@link DeadlineState} 感知）主动自查。
     */
    public static ServerInterceptor newUnaryServerInterceptor(Duration timeout, Duration slowThreshold) {
        if (slowThreshold == null || slowThreshold.compareTo(Duration.ZERO) <= 0) {
            slowThreshold = DEFAULT_SLOW_THRESHOLD;
        }
        Duration finalSlow = slowThreshold;
        Tracer tracer = GlobalOpenTelemetry.get().getTracer("rpc-server");

        return new ServerInterceptor() {
            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                    ServerCall<ReqT, RespT> call,
                    Metadata headers,
                    ServerCallHandler<ReqT, RespT> next) {
                String method = call.getMethodDescriptor().getFullMethodName();
                Instant start = Instant.now();
                Span span = tracer.spanBuilder(method)
                        .setSpanKind(SpanKind.SERVER)
                        .setParent(Context.current())
                        .startSpan();
                Context otelCtx = Context.current().with(span);
                // DRIFT-018 修复（Java 化）：用 ThreadLocal DeadlineState 暴露 deadline 时刻，
                // 对应 Go 版 context.WithTimeout(ctx, timeout) 后 ctx.Done() 语义。
                //
                // 🔴 E38：Go 的 `ctx` **携带客户端 deadline** ⇒ `context.WithTimeout(parent, d)` 的
                //   有效期限 = **min(客户端 deadline, now+d)**（Go 标准库语义：WithDeadline 里
                //   `if cur, ok := parent.Deadline(); ok && cur.Before(d) { return WithCancel(parent) }`
                //   —— 父 deadline 更早时直接继承父的）
                //   ⇒ 旧实现只算 `now + timeout` ⇒ **客户端 deadline 完全不参与** ⇒ 漂移。
                //   运行期实证（users/admin 实测 · 客户端 deadline 1000ms 档）：
                //   Go `Δ_start=+1005ms / Δ_end=+0ms`（随客户端一起死）
                //   vs RAG `Δ_start=+2117ms / Δ_end=+1111ms`（客户端已放弃，PG 查询仍占着 1.1s）
                //   ⇒ 可观察（抓取侧）⇒ global 台账 E38（落点在本类 ⇒ global 单写权）。
                //
                // ⚠️ 客户端 deadline 从 grpc-java 的 `io.grpc.Context.current().getDeadline()` 取
                //   （2026-10-26 实测：在 `interceptCall` 里可用 ⇒ 服务端 ctx 由入向 grpc-timeout 派生；
                //   实测客户端 1s ⇒ 服务端读到 932ms 剩余）
                // ⚠️ `timeout<=0`（不启用服务端超时）时**保持旧行为**（不引入客户端 deadline）：
                //   Go 侧 timeout<=0 直接 `handler(ctx, req)`，业务靠 ctx.Done() 感知；
                //   RAG 侧此刻**不注册超时调度器** ⇒ 若把 DeadlineState 设成客户端 deadline，
                //   会让业务在「服务端根本没超时」时误判为超时 ⇒ 超出本条修复范围。
                long serverBudgetMs = (timeout != null && timeout.compareTo(Duration.ZERO) > 0)
                        ? timeout.toMillis() : Long.MAX_VALUE;
                long effectiveTimeoutMs = Math.min(serverBudgetMs, clientDeadlineRemainingMs());
                long deadlineEpochMs = effectiveTimeoutMs == Long.MAX_VALUE
                        ? Long.MAX_VALUE
                        : System.currentTimeMillis() + effectiveTimeoutMs;
                AtomicReference<Throwable> timeoutErrRef = new AtomicReference<>();
                AtomicReference<Boolean> timedOutRef = new AtomicReference<>(Boolean.FALSE);
                // M6 修复：close() 可能被调用两次 —— 超时调度线程先 close(DEADLINE_EXCEEDED)，
                // 业务 handler 跑完后再 close(OK/自己的状态)。旧实现每次都 span.end() + 打一遍标，
                // 结果是同一 span 被 end 两次、属性被后写覆盖、遥测里出现重复事件。
                // Go 版 rpc.go 的 select 只有一条出口，不存在这个问题。
                AtomicBoolean spanFinalized = new AtomicBoolean(false);

                // 超时调度：到时把 timedOutRef 设 true 并主动 close call（DEADLINE_EXCEEDED）
                // 用 holder 数组让 timeoutFuture 可以被匿名类引用（Java 局部变量必须 final/effectively-final）
                final ScheduledFuture<?>[] timeoutFutureHolder = new ScheduledFuture<?>[1];
                // 🔴 DRIFT-COMMON-001 修复：超时必须走 wrappedCall，而不是原始 call。
                // 原始 call 是 wrappedCall 的委托目标，close 它会<b>绕过</b>下面的 span 打标与
                // span.end()（:238-240）⇒ 超时调用的 span 永不结束、永不导出 ⇒ 超时在遥测里不可见。
                // Go 出处：rpc.go:68-71 的 select 超时分支同样走 markRPCResult/MarkTimeout，
                // 与 done 分支共用同一条打标出口（Go 由 otel 插桩管 span 生命周期，
                // 但「超时要打 timeout 属性」这件事两侧一致）。
                // wrappedCall 在下方才创建，故用 AtomicReference 中转；万一调度器先于赋值开火
                // （timeout 极短时），退回 call 至少保证客户端仍能收到 DEADLINE_EXCEEDED。
                final AtomicReference<ServerCall<ReqT, RespT>> wrappedCallRef = new AtomicReference<>();
                if (timeout != null && timeout.compareTo(Duration.ZERO) > 0) {
                    // 🔴 E38：延时用 **effectiveTimeoutMs**（= min(客户端 deadline, 服务端预算)）
                    //   而不再是「服务端自己的 timeout」⇒ 与 Go 的 context.WithTimeout(ctx, timeout) 同语义。
                    //   ⚠️ 若客户端 deadline 已过期 ⇒ effective=0 ⇒ 调度器**立即** fire
                    //      （Go 侧：timeoutCtx.Done() 已就绪 ⇒ select 立即走超时分支 ⇒ 同款）
                    long timeoutMs = effectiveTimeoutMs;
                    timeoutFutureHolder[0] = timeoutScheduler().schedule(() -> {
                        // Go 真源 rpc.go:69-71 —— 超时这一路有**两个**可观察面，本实现必须同时对齐：
                        //   ① wire：client 收到的 status description = timeoutCtx.Err().Error()
                        //        = "context deadline exceeded"（见 GO_DEADLINE_EXCEEDED_DESCRIPTION）
                        //   ② 遥测：MarkTimeout(ctx, err, ...) 取 err.Error()
                        //        = "rpc error: code = DeadlineExceeded desc = context deadline exceeded"
                        //        ⇒ 本地这个异常**只喂遥测**，所以它的 message 要按 Go 的 status error 格式拼
                        // ⚠️ 旧实现两条都写成 "rpc timeout after " + timeoutMs + "ms" ⇒ 两侧都漂
                        //    （global 台账 E30；comment/rpc.md U10 · users/user/rpc.md V-007）
                        TimeoutException te = new TimeoutException(
                                goStyleStatusMessage(Code.DEADLINE_EXCEEDED, GO_DEADLINE_EXCEEDED_DESCRIPTION));
                        timeoutErrRef.set(te);
                        timedOutRef.set(Boolean.TRUE);
                        // 触发 call 关闭；close 是线程安全的
                        try {
                            ServerCall<ReqT, RespT> target =
                                    wrappedCallRef.get() != null ? wrappedCallRef.get() : call;
                            target.close(Status.DEADLINE_EXCEEDED
                                            .withDescription(GO_DEADLINE_EXCEEDED_DESCRIPTION),
                                    new Metadata());
                        } catch (Throwable ignored) {
                            // call 可能已经被业务关闭
                        }
                    }, timeoutMs, TimeUnit.MILLISECONDS);
                }

                // 包装 ServerCall：拦截 close 做 span 属性标注
                ServerCall<ReqT, RespT> wrappedCall = new ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(call) {
                    @Override
                    public void close(Status status, Metadata trailers) {
                        if (timeoutFutureHolder[0] != null) {
                            timeoutFutureHolder[0].cancel(false);
                        }
                        // M6：只有第一次 close 负责打标 + end span
                        if (!spanFinalized.compareAndSet(false, true)) {
                            super.close(status, trailers);
                            return;
                        }
                        Duration duration = Duration.between(start, Instant.now());
                        Map<String, Object> attrs = rpcAttrs(method, status.getCode());
                        if (span.isRecording()) {
                            span.setAttribute(AttributeKey.stringKey("rpc.system"), "grpc");
                            span.setAttribute(AttributeKey.stringKey("rpc.method"), method);
                            span.setAttribute(AttributeKey.longKey("rpc.grpc.status_code"), (long) status.getCode().value());
                        }
                        Throwable err = timeoutErrRef.get();
                        if (status.getCode() == Code.OK) {
                            markDurationAndSlow(otelCtx, duration, finalSlow, attrs);
                        } else if (status.getCode() == Code.DEADLINE_EXCEEDED || timedOutRef.get()) {
                            // 兜底：业务自己以 DEADLINE_EXCEEDED 关了 call（**不是**本调度器超时）
                            // ⇒ Go 侧对应 markRPCResult → IsTimeoutError → MarkTimeout(ctx, err, ...)
                            //    其中 msg = err.Error() = "rpc error: code = <code> desc = <desc>"
                            // ⇒ 必须用**实际 status**拼，不能用硬编码串（旧实现写死 "rpc deadline exceeded"）
                            Throwable tErr = err != null ? err : new TimeoutException(
                                    goStyleStatusMessage(status.getCode(), status.getDescription()));
                            markTimeout(otelCtx, tErr, duration, attrs);
                        } else if (isBusinessGrpcCode(status.getCode())) {
                            // M4 修复：Go rpc.go:95 传的是 err.Error()，即 status error 的完整串
                            //   "rpc error: code = NotFound desc = xxx"（含 code）。
                            // 旧 Java 版只取 getDescription()，把 code 从 biz_msg 里丢了，
                            // 光看 biz_msg 无法判断是哪个业务码失败。
                            markBusinessError(otelCtx, status.getCode().value(),
                                    goStyleStatusMessage(status.getCode(), status.getDescription()),
                                    duration, attrs);
                        } else {
                            Throwable sysErr = new RuntimeException(goStyleStatusMessage(status.getCode(), status.getDescription()));
                            markSystemError(otelCtx, sysErr, duration, attrs);
                        }
                        try (Scope ignored = otelCtx.makeCurrent()) {
                            span.end();
                        }
                        super.close(status, trailers);
                    }
                };

                // 包装 Listener：拦截 onCancel 处理客户端提前断开
                // DRIFT-COMMON-001：把 wrappedCall 交给超时调度器，使超时与业务 close 共用同一条
                // span 打标 + span.end() 出口（M6 的 spanFinalized CAS 保证只会执行一次）。
                wrappedCallRef.set(wrappedCall);
                ServerCall.Listener<ReqT> listener = next.startCall(wrappedCall, headers);
                return new ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT>(listener) {
                    /**
                     * M7 修复：把 deadline 绑定到<b>真正跑 handler 的线程</b>。
                     *
                     * <p>旧实现把 {@code DeadlineState.markExpired(...)} 放在超时调度线程里
                     * （{@code TIMEOUT_SCHEDULER.schedule(...)} 的 lambda）。{@code DeadlineState} 用的是
                     * {@code ThreadLocal}，设在调度线程上对 handler 线程<b>完全不可见</b> ——
                     * 于是 {@code UserDomainService} 里那些「配合 DeadlineState 感知超时」的注释与
                     * {@code isExpired()} 调用恒为 false，是一个写好了却永远不生效的机制。
                     *
                     * <p>grpc-java 的执行模型：{@code interceptCall} 与 {@code onHalfClose}（真正调 handler）
                     * 通常是同一线程，但框架允许换线程。{@code onHalfClose} 才是 handler 的入口，
                     * 所以 deadline 必须在它里面绑定。
                     *
                     * <h2>🔴 A9 修复（2026-10-27 · G2 反馈单 FB-04）：让 <b>业务 handler 执行期</b>能拿到当前 server span</h2>
                     *
                     * <p><b>Go 侧的机制来源（逐行）</b>：
                     * <pre>
                     *   go-zero v1.10.2 zrpc/internal/serverinterceptors/tracinginterceptor.go:18-24
                     *       ctx, span := startSpan(ctx, info.FullMethod)
                     *       …
                     *       resp, err := handler(ctx, req)        ← handler 拿到的 ctx **携带 span**
                     *   同文件 :95-107（startSpan）
                     *       return tr.Start(trace.ContextWithRemoteSpanContext(ctx, spanCtx), name, …)
                     *                 ⇒ span 是**从 ctx 派生**并**放回 ctx**的
                     *   ⇒ 业务代码 trace.SpanFromContext(l.ctx) 取得到 ⇒ 属性真落
                     * </pre>
                     *
                     * <p><b>RAG 侧为什么取不到</b>：Java 的 gRPC handler<b>不接收 ctx</b>
                     * ⇒ 业务代码唯一的等价物是 {@code Span.current()}（= {@code Context.current()} 里的 span）
                     * ⇒ 而本拦截器此前<b>只在</b> {@code close()}（{@code :391}）与 {@code onCancel()}（{@code :451}）
                     * 里 {@code makeCurrent()} ⇒ <b>handler 执行期 {@code Context.current()} 是根 context</b>
                     * ⇒ {@code Span.current()} 拿到 <b>invalid（no-op）span</b> ⇒ 属性<b>静默丢弃</b>
                     * ⇒ ⇒ 域侧若按 {@code Span.current().setAttribute(...)} 落码 ⇒ <b>跑绿但零效果</b>。
                     *
                     * <p><b>修法</b>：在 {@code onHalfClose} 里把 {@code otelCtx.makeCurrent()} 的 scope
                     * <b>包住 {@code super.onHalfClose()}</b>（即 handler 执行期）
                     * ⇒ 业务代码 {@code Span.current()} 就是那个 server span。
                     *
                     * <p><b>为什么不是「让业务自建子 span」</b>：Go 侧 like 域是<b>给已有的 server span 加属性</b>、
                     * <b>不新建</b> ⇒ 自建子 span 会让 trace 里多一个 Go 没有的 span ⇒ <b>结构漂移</b>。
                     *
                     * <p><b>影响面</b>：范围内 10 个 rpc 组件的 gRPC handler 线程；
                     * 但<b>只有 like 域</b>依赖「当前 span」语义（Go 侧 {@code SpanFromContext} 6 处）。
                     * ⚠️ 全仓主源码里 {@code Span.current()} 原本只有 1 处
                     * （{@code comment-rpc} 的 {@code CommentFactDeliveryService.java:148}），
                     * 而它跑在 <b>调度线程</b>上（{@code CommentFactDispatchScheduler} ⇒ 不走 gRPC handler）
                     * ⇒ <b>本改动碰不到它</b> ⇒ 零回归。
                     */
                    @Override
                    public void onHalfClose() {
                        // 🔴 A9（FB-04）：scope 必须**包住整个 handler 执行期**
                        //    ⇒ 业务代码 Span.current() 拿到的就是 server span
                        try (Scope ignored = otelCtx.makeCurrent()) {
                            if (deadlineEpochMs == Long.MAX_VALUE) {
                                super.onHalfClose();
                                return;
                            }
                            try (DeadlineState.Scope ignoredScope = DeadlineState.bindToCurrent(deadlineEpochMs)) {
                                super.onHalfClose();
                            }
                        }
                    }

                    @Override
                    public void onCancel() {
                        if (timeoutFutureHolder[0] != null) {
                            timeoutFutureHolder[0].cancel(false);
                        }
                        // M6：与 close() 共用同一个 finalized 守卫，避免 close + onCancel 双写
                        if (!spanFinalized.compareAndSet(false, true)) {
                            super.onCancel();
                            return;
                        }
                        Duration duration = Duration.between(start, Instant.now());
                        Map<String, Object> attrs = rpcAttrs(method, Code.CANCELLED);
                        if (span.isRecording()) {
                            span.setAttribute(AttributeKey.stringKey("rpc.system"), "grpc");
                            span.setAttribute(AttributeKey.stringKey("rpc.method"), method);
                            span.setAttribute(AttributeKey.longKey("rpc.grpc.status_code"), (long) Code.CANCELLED.value());
                        }
                        Throwable cancelErr = new RuntimeException("client cancelled");
                        if (timedOutRef.get()) {
                            markTimeout(otelCtx, timeoutErrRef.get(), duration, attrs);
                        } else {
                            markSystemError(otelCtx, cancelErr, duration, attrs);
                        }
                        try (Scope ignored = otelCtx.makeCurrent()) {
                            span.end();
                        }
                        super.onCancel();
                    }
                };
            }
        };
    }

    /**
     * 非 grpc Server 场景下的入口（业务直接调用 handler 时用）。保留 Go 版 {@code handle} 风格的包装：
     *
     * <p>行为：
     * <ul>
     *   <li>起 otel span</li>
     *   <li>独立线程跑 handler；主线程等结果或超时</li>
     *   <li>超时返回 RuntimeException（包装 DEADLINE_EXCEEDED）；handler 异常按 gRPC code 分类</li>
     * </ul>
     */
    public static <ReqT, RespT> RpcResult<RespT> handle(
            String method,
            ReqT req,
            Duration timeout,
            Duration slowThreshold,
            RpcCallHandler<ReqT, RespT> handler) {
        if (slowThreshold == null || slowThreshold.compareTo(Duration.ZERO) <= 0) {
            slowThreshold = DEFAULT_SLOW_THRESHOLD;
        }
        Instant start = Instant.now();
        Span span = GlobalOpenTelemetry.get().getTracer("rpc-handler")
                .spanBuilder(method).setSpanKind(SpanKind.INTERNAL).startSpan();
        // DRIFT-018 修复（Java 化）：用 ThreadLocal DeadlineState 让 handle() 路径下的 worker
        // 线程感知超时，对应 Go 版 context.WithTimeout(ctx, timeout) 的 ctx.Done() 语义。
        long deadlineEpochMs = (timeout == null || timeout.compareTo(Duration.ZERO) <= 0)
                ? Long.MAX_VALUE
                : System.currentTimeMillis() + timeout.toMillis();
        try (Scope ignored = span.makeCurrent()) {
            Context otelCtx = Context.current();
            if (timeout == null || timeout.compareTo(Duration.ZERO) <= 0) {
                RespT resp;
                Throwable err;
                try {
                    resp = handler.handle(req);
                    err = null;
                } catch (Throwable t) {
                    resp = null;
                    err = t;
                }
                Duration duration = Duration.between(start, Instant.now());
                markRpcResult(otelCtx, method, err, duration, slowThreshold);
                span.end();
                return new RpcResult<>(resp, err);
            }

            // 超时控制：用独立线程跑业务，主线程等结果或超时
            Object[] respHolder = new Object[1];
            Throwable[] errHolder = new Throwable[1];
            // Spring 化：用 HANDLE_EXECUTOR 替换裸 Thread；用 Future.get(timeout) 替换轮询 Thread.sleep(10)
            // 业务线程被 cancel(true) 后会收到 interrupt，可通过 InterruptedException 感知（对齐 Go 版 cancel 语义）。
            Future<?> workerFuture = handleExecutor().submit(() -> {
                // DRIFT-018 修复：把 deadline bind 到当前 worker 线程，
                // 业务 catch InterruptedException 后通过 DeadlineState.isExpired() 感知
                try (DeadlineState.Scope scope = DeadlineState.bindToCurrent(deadlineEpochMs)) {
                    respHolder[0] = handler.handle(req);
                } catch (Throwable e) {
                    errHolder[0] = e;
                }
            });

            long timeoutMs = timeout.toMillis();
            try {
                // Future.get 直接阻塞到 worker 完成或超时——比原来 10ms 轮询更精确，CPU 占用更低。
                workerFuture.get(timeoutMs, TimeUnit.MILLISECONDS);
                Duration duration = Duration.between(start, Instant.now());
                if (errHolder[0] != null) {
                    markRpcResult(otelCtx, method, errHolder[0], duration, slowThreshold);
                    span.end();
                    return new RpcResult<>(null, errHolder[0]);
                }
                markRpcResult(otelCtx, method, null, duration, slowThreshold);
                span.end();
                return new RpcResult<>((RespT) respHolder[0], null);
            } catch (TimeoutException te) {
                // 超时未完成：cancel(true) 触发业务线程 interrupt
                workerFuture.cancel(true);
                Duration duration = Duration.between(start, Instant.now());
                Status st = Status.DEADLINE_EXCEEDED.withDescription(GO_DEADLINE_EXCEEDED_DESCRIPTION);
                // DRIFT-010 修复：用 StatusRuntimeException 而不是裸 RuntimeException
                io.grpc.StatusRuntimeException dee = new io.grpc.StatusRuntimeException(st);
                // 遥测文案按 Go 的 status error 格式拼（MarkTimeout 取 err.Error()）
                // ⚠️ 旧实现传的是 dee 本身 ⇒ 其 getMessage() = "DEADLINE_EXCEEDED: 2000ms"
                //    ⇒ 与 Go 的 "rpc error: code = DeadlineExceeded desc = ..." 不同
                markTimeout(otelCtx,
                        new TimeoutException(goStyleStatusMessage(Code.DEADLINE_EXCEEDED, GO_DEADLINE_EXCEEDED_DESCRIPTION)),
                        duration, rpcAttrs(method, Code.DEADLINE_EXCEEDED));
                span.end();
                return new RpcResult<>(null, dee);
            } catch (InterruptedException ie) {
                // 调用线程被 interrupt（业务侧 cancel / Spring 容器关闭）
                Thread.currentThread().interrupt();
                workerFuture.cancel(true);
                Duration duration = Duration.between(start, Instant.now());
                markRpcResult(otelCtx, method, ie, duration, slowThreshold);
                span.end();
                return new RpcResult<>(null, ie);
            } catch (ExecutionException ee) {
                // worker 内部抛未捕获异常（不应发生——handler 应自行 catch）；兜底标记为 systemError
                Duration duration = Duration.between(start, Instant.now());
                Throwable cause = ee.getCause() != null ? ee.getCause() : ee;
                markRpcResult(otelCtx, method, cause, duration, slowThreshold);
                span.end();
                return new RpcResult<>(null,
                        cause instanceof Exception ? (Exception) cause : new RuntimeException(cause));
            }
        }
    }

    /**
     * 对应 Go 版 {@code func markRPCResult(ctx, method, err, duration, slowThreshold)}。
     */
    private static void markRpcResult(Context ctx, String method, Throwable err, Duration duration, Duration slowThreshold) {
        Code code = Code.OK;
        if (err != null) {
            Status st = Status.fromThrowable(err);
            if (st != null && st.getCode() != Code.OK) {
                code = st.getCode();
            } else {
                code = Code.UNKNOWN;
            }
        }
        Map<String, Object> attrs = rpcAttrs(method, code);
        if (err == null) {
            markDurationAndSlow(ctx, duration, slowThreshold, attrs);
            return;
        }
        if (isTimeoutError(err)) {
            markTimeout(ctx, err, duration, attrs);
            return;
        }
        if (isBusinessGrpcCode(code)) {
            markBusinessError(ctx, code.value(),
                    goStyleStatusMessage(code, Status.fromThrowable(err).getDescription()),
                    duration, attrs);
            return;
        }
        markSystemError(ctx, err, duration, attrs);
    }

    /**
     * M4 修复：构造对齐 Go 的 status error 文本。
     *
     * <p>Go 的 {@code status.Error(codes.NotFound, "xxx").Error()} 产出
     * {@code "rpc error: code = NotFound desc = xxx"} —— <b>code 与 desc 都在</b>，
     * {@code rpc.go:95} 直接把它作为 {@code sea.biz_msg}。
     *
     * <p>Java 侧两种写法都会丢 code：拦截器路径用 {@code getDescription()}（只剩 desc），
     * {@code markRpcResult} 路径用 {@code getMessage()}（grpc-java 给的是
     * {@code "NOT_FOUND: desc"}，格式与 Go 不同但 code 在）。统一收敛到本方法。
     *
     * <p>已知残留差异：gRPC Java 的 {@link Code#name()} 返回 {@code "NOT_FOUND"}（全大写），
     * Go 的 {@code codes.Code.String()} 返回 {@code "NotFound"}（驼峰）。语言常量表示不同，
     * 不做臆造映射；语义（code + desc 都在）已对齐。
     */
    private static String goStyleStatusMessage(Code code, String description) {
        String desc = description != null ? description : "";
        return "rpc error: code = " + code.name() + " desc = " + desc;
    }

    /**
     * 🔴 **E38** —— 取**客户端**的 deadline 剩余毫秒（grpc-java 服务端 ctx）。
     *
     * <p><b>Go 出处</b>：{@code service/common/observability/rpc.go:38}
     * <pre>
     * Go: timeoutCtx, cancel := context.WithTimeout(ctx, timeout)
     * </pre>
     * ⇒ {@code ctx} 是 gRPC 传进来的服务端 ctx，**携带客户端 deadline**
     * ⇒ Go 标准库 {@code WithDeadline} 的语义：父 deadline 更早时**直接继承父的**
     * ⇒ 有效期限 = {@code min(客户端 deadline, now+timeout)}。
     *
     * <p><b>grpc-java 侧的取值点</b>：{@code io.grpc.Context.current().getDeadline()}
     * ⇒ 服务端 ctx 由**入向 {@code grpc-timeout} 头**派生
     * ⇒ 2026-10-26 实测：在 {@code interceptCall} 里可用（客户端 deadline 1s
     * ⇒ 服务端读到 <b>932ms</b> 剩余；headers 里也有 {@code grpc-timeout: 936027u}）。
     *
     * <p><b>返回值的三个约定</b>：
     * <ul>
     *   <li>客户端**没带** deadline ⇒ {@link Long#MAX_VALUE}（不参与 min）⇒ 退化成「只算服务端自己的」
     *       —— 与 Go 侧「父 ctx 无 deadline」同款；</li>
     *   <li>客户端 deadline **已过期** ⇒ 返回 {@code 0}（不是负数）
     *       ⇒ min 后 effective=0 ⇒ 调度器立即 fire
     *       —— 与 Go 侧「{@code timeoutCtx.Done()} 已就绪 ⇒ select 立即走超时分支」同款；</li>
     *   <li>取值本身抛异常 ⇒ 返回 {@link Long#MAX_VALUE}（**不因取 deadline 失败而误杀请求**）。</li>
     * </ul>
     *
     * <p>⚠️ 本方法只解决**期限的计算**；「客户端放弃后，服务端在途的 PG 查询随之取消」
     * 是<b>另一层</b>（Go 靠 ctx cancel ⇒ GORM 取消查询；grpc-java 的
     * {@code ServerCall#close()} <b>不取消业务线程</b> ⇒ 框架限制，与 T-14 同根）
     * ⇒ **该层属 E38 的第 ② 层，Jason 2026-10-26 已拍板「另立项」** ⇒ 本轮不碰。
     */
    private static long clientDeadlineRemainingMs() {
        try {
            // ⚠️ 必须用全限定名：本类 import 的是 OTel 的 io.opentelemetry.context.Context
            io.grpc.Deadline deadline = io.grpc.Context.current().getDeadline();
            if (deadline == null) {
                return Long.MAX_VALUE;
            }
            long remaining = deadline.timeRemaining(TimeUnit.MILLISECONDS);
            return remaining > 0 ? remaining : 0L;
        } catch (RuntimeException e) {
            return Long.MAX_VALUE;
        }
    }

    /**
     * 对应 Go 版 {@code func isBusinessGRPCCode(code codes.Code) bool}。
     *
     * <pre>
     *   InvalidArgument, NotFound, AlreadyExists, PermissionDenied,
     *   Unauthenticated, FailedPrecondition, OutOfRange  → true
     * </pre>
     */
    private static boolean isBusinessGrpcCode(Code code) {
        switch (code) {
            case INVALID_ARGUMENT:
            case NOT_FOUND:
            case ALREADY_EXISTS:
            case PERMISSION_DENIED:
            case UNAUTHENTICATED:
            case FAILED_PRECONDITION:
            case OUT_OF_RANGE:
                return true;
            default:
                return false;
        }
    }

    /**
     * 对应 Go 版 {@code func rpcAttrs(method string, code codes.Code) []attribute.KeyValue}。
     */
    private static Map<String, Object> rpcAttrs(String method, Code code) {
        Map<String, Object> m = new LinkedHashMap<>(3);
        m.put("rpc.system", "grpc");
        m.put("rpc.method", method);
        m.put("rpc.grpc.status_code", (long) code.value());
        return m;
    }

    /**
     * gRPC 服务端业务调用处理器，Java 版与 Go 版 grpc.UnaryHandler 等价。
     */
    @FunctionalInterface
    public interface RpcCallHandler<ReqT, RespT> {
        RespT handle(ReqT req) throws Exception;
    }

    /**
     * gRPC 调用结果（resp + 错误），对应 Go 版 {@code (any, error)}。
     */
    public static final class RpcResult<RespT> {
        private final RespT response;
        private final Throwable error;

        public RpcResult(RespT response, Throwable error) {
            this.response = response;
            this.error = error;
        }

        public RespT response() {
            return response;
        }

        public Throwable error() {
            return error;
        }
    }

    /**
     * 对应 Go 版 {@code context.WithTimeout(ctx, timeout)} 暴露的 ctx.Done() 语义——
     * Java 化：用 {@link ThreadLocal} 让任意业务线程查 deadline 过期状态。
     *
     * <p><b>业务 handler 接入规范（必读）</b>：
     * <p>Go 版 handler 在 {@code select { case <-ctx.Done(): ... }} 里感知超时；Java grpc-java
     * 不通过 ctx 传播 cancel（DRIFT-006），所以业务 handler 必须<b>显式</b>选用以下任一机制感知超时：
     * <ul>
     *   <li><b>机制 A（推荐）</b>：在长循环里周期性查 {@link #isExpired()}，到点抛
     *       {@link java.util.concurrent.TimeoutException}：
     *       <pre>{@code
     *       while (moreWork) {
     *           if (DeadlineState.isExpired()) {
     *               throw new TimeoutException("deadline reached");
     *           }
     *           doWork();
     *       }
     *       }</pre>
     *   </li>
     *   <li><b>机制 B</b>：在阻塞 IO / 下游 RPC 前用 {@link #remainingMillis()} 限流：
     *       <pre>{@code
     *       long left = DeadlineState.remainingMillis();
     *       if (left <= 0) throw new TimeoutException("deadline reached");
     *       downstream.call(Duration.ofMillis(left));
     *       }</pre>
     *   </li>
     *   <li><b>机制 C</b>（仅 {@link #handle} 路径）：业务抛在 {@code CompletableFuture} 里，
     *       {@link #handle} 调 {@code future.cancel(true)} 给业务线程发 interrupt，
     *       业务 catch {@link InterruptedException} 立即退出。
     *   </li>
     *   <li><b>机制 D</b>：检查 {@link Thread#isInterrupted()}（兜底）</li>
     * </ul>
     *
     * <p><b>ctx-aware Java 标准 API（{@link java.net.http.HttpClient} / JDBC / grpc stub）
     * 不会自动感知 deadline</b>，因为它们的 timeout 参数是单独传入的。建议：
     * <ul>
     *   <li>在调下游前用 {@link #remainingMillis()} 计算剩余时间，传给下游 timeout</li>
     *   <li>或者改用 {@link #handle} 路径（业务线程会被 interrupt）</li>
     * </ul>
     *
     * <p>框架职责说明：
     * <ul>
     *   <li>Go 版：ctx 在 goroutine 间传递，靠 select ctx.Done() 实现取消传播</li>
     *   <li>Java 版：ThreadLocal 是 Java 多线程下最自然的"per-thread 状态"机制，
     *       不需要业务方在切线程时 rebind</li>
     * </ul>
     *
     * <p>配合 {@link #newUnaryServerInterceptor} / {@link #handle} 使用；详见 DRIFT-018 注释。
     */
    public static final class DeadlineState {
        private DeadlineState() {}

        /** 当前线程的 deadline 过期时刻（epoch 毫秒）；未设置时 {@link #isExpired()} 返回 false。 */
        private static final ThreadLocal<Long> DEADLINE = new ThreadLocal<>();

        /*
         * ⚠️ 2026-10-25（{@code align/global} 收口提交）：原 {@code markExpired(long)}
         * 已<b>删除</b>，此处保留说明以留痕。
         *
         * <p>删除理由（三条，均为实测）：
         * <ol>
         *   <li><b>无任何生产调用方</b> —— 全仓 grep 实证：仅 5 处 javadoc 引用，无一处真实调用；</li>
         *   <li><b>永远不生效</b> —— 它只做 {@code DEADLINE.set(...)}，
         *       而 {@code DEADLINE} 是 {@code ThreadLocal}；唯一 conceivable 的用法
         *       （设在超时调度线程上）对 handler 线程<b>不可见</b>；</li>
         *   <li><b>不做作用域清理</b> —— {@link #bindToCurrent(long)} 返回的 {@link Scope}
         *       会在 try-with-resources 结束时 {@code remove()}，避免线程池复用串味。</li>
         * </ol>
         *
         * <p><b>为什么它一直留到今天</b>：{@code service/users/user/user-rpc} 的
         * {@code UserDomainService} javadoc 误以它描述 deadline 的来源，
         * 删除会让那个跨域文件的 javadoc 链接悬空，而<b>跨域文件的改动不由 common 域决定</b>
         * ⇒ 这是跨域单点 ⇒ 归 {@code global} 收口域（明细见
         * {@code docs/DRIFT/global/cycle-deps.md}）。
         * ⇒ <b>两步走完（同一提交内）</b>：① 改正 users 侧假断言 → ② 删除本方法。
         *
         * <p>实际生效路径：{@link #newUnaryServerInterceptor} 的 {@code onHalfClose}
         * 在<b>真正跑 handler 的线程</b>上 {@link #bindToCurrent(long)}（M7 修复）。
         *
         * <p>对应 Go 版 ctx 触发 cancel 后，handler ctx 立即 Done() 的语义。
         */

        /** 当前线程是否已过 deadline；false 包括"未设置"和"还有时间"。 */
        public static boolean isExpired() {
            Long d = DEADLINE.get();
            return d != null && System.currentTimeMillis() >= d;
        }

        /** 还剩多少毫秒到 deadline；未设置时返回 Long.MAX_VALUE。 */
        public static long remainingMillis() {
            Long d = DEADLINE.get();
            if (d == null) return Long.MAX_VALUE;
            return Math.max(0L, d - System.currentTimeMillis());
        }

        /**
         * 业务代码用 try-with-resources 绑定 deadline 到当前线程；结束后自动清除（避免泄漏到下个任务）。
         *
         * <pre>{@code
         * try (DeadlineState.Scope ignored = DeadlineState.bindToCurrent(deadlineMs)) {
         *     // 业务循环
         *     while (moreWork) {
         *         if (DeadlineState.isExpired()) {
         *             throw new TimeoutException("rpc deadline reached");
         *         }
         *     }
         * }
         * }</pre>
         *
         * <p>对应 Go 版 handler 在循环里 {@code select { case <-ctx.Done(): ... }}。
         */
        public static Scope bindToCurrent(long epochMillis) {
            DEADLINE.set(epochMillis);
            return () -> DEADLINE.remove();
        }

        /** try-with-resources 析构接口；业务方通常不直接调用。 */
        @FunctionalInterface
        public interface Scope extends AutoCloseable {
            @Override
            void close();
        }
    }
}
