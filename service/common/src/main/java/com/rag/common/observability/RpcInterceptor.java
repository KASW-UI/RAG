package com.rag.common.observability;

import io.grpc.ForwardingServerCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Grpc;
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
     * 共享层的日志出口（go-zero 的 {@code logx.WithContext(ctx)} 的等价物）。
     *
     * <p>⚠️ RAG 侧**不**把 ctx 注入日志（共享层的 {@code com.rag.common.logger.Logger}
     * 才是那一层）⇒ 本类只打「结构化的一行」⇒ ⇒ 详见
     * {@link #logSlowCall} 的「刻意只落 slowcall 分支」说明。
     */
    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(RpcInterceptor.class);

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
                long serverBudgetNanos = (timeout != null && timeout.compareTo(Duration.ZERO) > 0)
                        ? toNanosSaturated(timeout) : Long.MAX_VALUE;
                long effectiveTimeoutNanos = Math.min(serverBudgetNanos, clientDeadlineRemainingNanos());
                // 🔴 E65：**绝对时刻只在这里算一次**（Go 的 ctx deadline 是在**拦截器入口**定下的）。
                //   ① 两个载体（{@link DeadlineState} / {@link GoStyleRequestDeadline}）共用它
                //      ⇒ 二者指向**同一时刻** ⇒ 不会出现「SQL 已过期但出向还活着」；
                //   ② 若等到 onHalfClose 才加 ⇒ 会比 Go 宽「入口→onHalfClose」那一小段 ⇒ 漂移。
                //   ⚠️ 时基统一取 {@link DeadlineState#monotonicNanos}（生产 = {@code System::nanoTime}）
                //   ⇒ DeadlineState 内部的到期判定与本时刻**同源**（不会一个用假时钟一个用真时钟）。
                long absoluteDeadlineNanos = saturatingAddNanos(
                        DeadlineState.monotonicNanos.getAsLong(), effectiveTimeoutNanos);
                // 「**无预算**」= 服务端没配 timeout（timeout<=0）**且**客户端没带 deadline
                // ⇒ Go 侧 ctx 此时**没有 deadline**（服务端不加 WithTimeout ⇒ 父 ctx 也没有）
                // ⇒ ⇒ 出向载体**不能绑**（绑了就会凭空给出一个 292 年的 deadline）
                // ⇒ ⇒ 详见 {@link GoStyleRequestDeadline.Scope#NOOP}。
                boolean hasDeadlineBudget = effectiveTimeoutNanos != Long.MAX_VALUE;
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
                    long timeoutNanos = effectiveTimeoutNanos;
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
                    }, timeoutNanos, TimeUnit.NANOSECONDS);
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
                        // 🔴 G1 反馈单 3/3 的共享层半边：go-zero 框架级 Stat 层的「慢调用日志」
                        //   ⇒ 只落**慢**的那一支（Go 的另一支 = 每个请求都打全量 body 到 Info，本层**刻意不落**）
                        logSlowCall(method, duration, finalSlow, remoteAddrOf(call));
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
                     *
                     * <p>⚠️ <b>计数口径（2026-10-27 订正 · G2 反馈单 FB-04）</b>：
                     * <b>合并前</b>全仓主源码里 {@code Span.current()} 的<b>调用点</b>只有 1 处
                     * （{@code comment-rpc} 的 {@code CommentFactDeliveryService.java:148}），
                     * 而它跑在 <b>调度线程</b>上（{@code CommentFactDispatchScheduler} ⇒ 不走 gRPC handler）
                     * ⇒ <b>本改动碰不到它</b> ⇒ 该处零回归。
                     *
                     * <p>但<b>合并后</b>全仓是 <b>2 处</b>：<b>like 域</b>的
                     * {@code LikeSpans.java:242/:246}（{@code Span.current().setAttribute(...)}，
                     * G2 树 {@code 2b3c49a}）在 like 域落码后并入本树
                     * ⇒ 它<b>落在本改动的受益面上</b>（调用点 {@code LikeDomainService}
                     * ⇒ gRPC handler 线程）⇒ 合并后该处由 <b>no-op 变真生效</b>
                     * ⇒ <b>预期行为变更，非回归</b>。
                     * <p>⇒ 因此 like 域的 <b>A9 端到端 E2E 必须在合并后补</b>
                     * （真 server + 真链 ⇒ 断言导出的 server span 真带 {@code query.*} /
                     * {@code cache.*} / {@code like.*} 属性）；
                     * 域侧<b>契约级</b>守门（如 {@code LikeSpansAttributeGateTest} 的手工
                     * {@code makeCurrent}）<b>不等于</b>端到端生效。
                     * <p>⚠️ MQ 路径不受影响：{@code LikeUpdateKafkaListener.onMessage} 走
                     * {@code MqConsumer.traceConsumer(Context.current(), …)} ⇒ <b>自建</b> consumer span
                     * ⇒ 不依赖本改动 ⇒ {@code messaging.*} 本来就生效（与 Go 侧 {@code TraceConsumer} 同形）。
                     */
                    @Override
                    public void onHalfClose() {
                        // 🔴 A9（FB-04）：scope 必须**包住整个 handler 执行期**
                        //    ⇒ 业务代码 Span.current() 拿到的就是 server span
                        try (Scope ignored = otelCtx.makeCurrent()) {
                            if (effectiveTimeoutNanos == Long.MAX_VALUE) {
                                super.onHalfClose();
                                return;
                            }
                            // 🔴 E65（出向 gRPC 不继承请求 deadline）——
                            //   把**同一个绝对时刻**（入口处算好的）同时绑给**两个载体**：
                            //   ① {@code DeadlineState}（本类）= 服务端 SQL 超时用
                            //   ② {@link GoStyleRequestDeadline} = 出向 gRPC 载体（Go 的 ctx 传播）
                            //   ⇒ 二者必须指向**同一时刻**（算一次、两个共用 ⇒ 不会各算各的 now）
                            //   ⇒ 绑定点必须在**跑 handler 的线程**上（本方法即 handler 入口）
                            //   ⚠️ 「无预算」时**不绑出向载体**（Go 侧此时 ctx 无 deadline ⇒ 出向也不该有）
                            //   ⚠️⚠️ **诚实边界**：上方「{@code effectiveTimeoutNanos == Long.MAX_VALUE}」的**早退
                            //      已经覆盖了「无预算」这条路径** ⇒ ⇒ 本行的三元分支在当前代码路径上
                            //      **不可达** ⇒ 它只是**冗余防护**（那道早退若被删，本分支仍保证「不绑」）。
                            //      ⇒ ⇒ 守门 {@code noBudgetMeansNoOutboundCarrierIsBound} 的**变异对象
                            //      是「两道守卫同时失效」**：实测**单点变异不转红**（删早退 / 三元改无条件绑
                            //      各自都仍被另一道守卫挡住），**双点变异才转红** ⇒ 判据活着，但别写成
                            //      「删掉早退就会红」。
                            try (DeadlineState.Scope ignoredScope =
                                         DeadlineState.bindAbsoluteNanos(absoluteDeadlineNanos);
                                 GoStyleRequestDeadline.Scope ignoredCarrier = hasDeadlineBudget
                                         ? GoStyleRequestDeadline.bindAbsoluteNanos(absoluteDeadlineNanos)
                                         : GoStyleRequestDeadline.Scope.NOOP) {
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
        long deadlineNanos = (timeout == null || timeout.compareTo(Duration.ZERO) <= 0)
                ? Long.MAX_VALUE
                : toNanosSaturated(timeout);
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
                try (DeadlineState.Scope scope = DeadlineState.bindRemainingNanos(deadlineNanos)) {
                    respHolder[0] = handler.handle(req);
                } catch (Throwable e) {
                    errHolder[0] = e;
                }
            });

            long timeoutNanos = deadlineNanos;
            try {
                // Future.get 直接阻塞到 worker 完成或超时——比原来 10ms 轮询更精确，CPU 占用更低。
                // 🔴 DRIFT-COMMON-005：用**纳秒** ⇒ 亚毫秒的 timeout 不会被截成 0ms
                //    （旧实现 timeout.toMillis() == 0 ⇒ get(0) 立即返回 ⇒ 请求被秒判超时）。
                workerFuture.get(timeoutNanos, TimeUnit.NANOSECONDS);
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
    private static long clientDeadlineRemainingNanos() {
        try {
            // ⚠️ 必须用全限定名：本类 import 的是 OTel 的 io.opentelemetry.context.Context
            io.grpc.Deadline deadline = io.grpc.Context.current().getDeadline();
            if (deadline == null) {
                return Long.MAX_VALUE;
            }
            // 🔴 DRIFT-COMMON-005：**必须取纳秒**。
            //    旧实现取 MILLISECONDS ⇒ grpc-java 内部的纳秒精度被**截到毫秒**
            //    ⇒ 客户端 deadline 的亚毫秒部分被丢弃（Go 侧 ctx 是纳秒精度）。
            long remaining = deadline.timeRemaining(TimeUnit.NANOSECONDS);
            return remaining > 0 ? remaining : 0L;
        } catch (RuntimeException e) {
            return Long.MAX_VALUE;
        }
    }

    /**
     * 饱和加法：{@code base + delta} 溢出 ⇒ 返回 {@link Long#MAX_VALUE}（「几乎无限」）。
     *
     * <p>⚠️ <b>为什么不用 {@code base + delta}</b>：溢出后会变成<b>负数</b>
     * ⇒ 会被读成「已过期」⇒ 请求会被立刻判死（与 {@code DeadlineState} 里
     * {@code bindRemainingNanos} 的同款饱和处理一致）。
     */
    private static long saturatingAddNanos(long base, long delta) {
        try {
            return Math.addExact(base, delta);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    /**
     * <b>go-zero 框架级 Stat 层的「慢调用日志」（G1 反馈单 3/3 的共享层半边 · 2026-10-30 · G4 lane）。</b>
     *
     * <h2>Go 出处（逐行读过）</h2>
     * <pre>
     * // go-zero@v1.10.2/zrpc/internal/serverinterceptors/statinterceptor.go:74-97
     * func logDuration(ctx, method, req, duration, ignoreMethods, durationThreshold) {
     *     addr := peer.FromContext(ctx).Addr.String()            // :77-80  客户端地址
     *     logger := logx.WithContext(ctx).WithDuration(duration) // :82
     *     if !shouldLogContent(method, ignoreMethods) {         // :83  方法在 ignore 名单里
     *         if isSlow(duration, durationThreshold) {
     *             logger.Slowf("[RPC] slowcall - %s - %s", addr, method)          // :85
     *         }
     *     } else {                                              // :87  **默认走这一支**
     *         content, err := json.Marshal(req)                 // :88
     *         if err != nil { … }                               // :89-90
     *         else if isSlow(duration, durationThreshold) {
     *             logger.Slowf("[RPC] slowcall - %s - %s - %s", addr, method, content) // :92
     *         } else {
     *             logger.Infof("%s - %s - %s", addr, method, content)                // :94
     *         }
     *     }
     * }
     *
     * // 阈值：:28 StatConf.SlowThreshold `json:",default=500ms"`；:19 defaultSlowThreshold = 500ms
     * // :65-72 isSlow：durationThreshold>0 用它，否则用全局兜底 slowThreshold（真源 yaml 无 Middlewares 块 ⇒ 走兜底 500ms）
     * </pre>
     *
     * <h2>本方法**只落 slowcall 那一支**（有意的不完整对齐）</h2>
     * <ul>
     *   <li>Go 的 <b>Info 分支</b>（`:94`）会把<b>每个请求的完整 JSON body</b>打进 Info 日志
     *       ⇒ <b>本层刻意不落</b>：① 体量（每请求一行全量 body）；② 内容面（body 含业务数据）
     *       ⇒ ⇒ 这是一处**有意的部分对齐**，已在台账登记（`docs/DRIFT/global/vs-common.md` §4.19.5）。</li>
     *   <li>Go 的 <b>慢调用分支</b>（`:85`/`:92`）⇒ <b>本方法落它</b>
     *       ⇒ 文案对齐 Go 的 {@code "[RPC] slowcall - <addr> - <method>"}，
     *       并<b>额外</b>带一个 duration（Go 侧 duration 在日志<b>元数据</b>里，不在文本里）。</li>
     * </ul>
     *
     * <h2>阈值从哪来（两侧同数）</h2>
     * <p>本类的 {@code slowThreshold} 参数最终来自
     * {@code RpcServerInterceptorFactory} → {@code ObservabilityProperties#getSlowThreshold()}
     * → 兜底 {@link ObservabilityConstants#DEFAULT_SLOW_THRESHOLD}（= <b>500ms</b>，与 Go 的
     * {@code defaultSlowThreshold} 同数）⇒ ⇒ <b>不需要新增任何配置</b>。
     *
     * <h2>⚠️ 归属链差异（诚实边界，不可外推）</h2>
     * <p>Go 的 Stat 层挂在 <b>Recover 之后 / Breaker 之前</b>（{@code zrpc/server.go:151-153}）
     * ⇒ 它量到的 duration <b>包含</b> breaker / shedding 引入的耗时；
     * 而本类（{@code RpcInterceptor}）是<b>最内层</b>的业务拦截器
     * ⇒ 它量到的 duration <b>不含</b> breaker / shedding 的开销
     * ⇒ ⇒ <b>同一笔慢调用，RAG 可能判成「不慢」而 Go 判成「慢」</b>
     * ⇒ ⇒ 这是<b>链层位置</b>带来的差异（不是阈值差异）⇒ 已登记。
     *
     * @param method        gRPC 全方法名（Go 侧的 {@code info.FullMethod}）
     * @param duration      本次调用的耗时
     * @param slowThreshold 慢调用阈值（≤0 ⇒ 不判慢）
     * @param remoteAddr    客户端地址（Go 侧的 {@code addr}；取不到时为 {@code "-"}）
     */
    private static void logSlowCall(String method, Duration duration, Duration slowThreshold, String remoteAddr) {
        if (slowThreshold == null || slowThreshold.compareTo(Duration.ZERO) <= 0) {
            return;
        }
        if (duration == null || duration.compareTo(slowThreshold) <= 0) {
            // Go 的 isSlow（:65-72）是严格大于 ⇒ 等于阈值不算慢 ⇒ 本层同款
            return;
        }
        log.warn("[RPC] slowcall - {} - {} - duration {}ms", remoteAddr, method, duration.toMillis());
    }

    /**
     * 取客户端地址（Go 侧 {@code peer.FromContext(ctx).Addr.String()} 的等价物）。
     *
     * <p>⚠️ <b>取不到时返回 {@code "-"} 而不是抛异常</b> —— 日志路径**绝不能**影响主流程
     * （Go 侧 {@code peer.FromContext} 取不到时 {@code addr} 就是空串 ⇒ 同样不报错）。
     */
    private static String remoteAddrOf(ServerCall<?, ?> call) {
        try {
            Object attr = call.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
            if (attr instanceof java.net.InetSocketAddress inet) {
                return inet.getAddress() != null
                        ? inet.getAddress().getHostAddress()
                        : inet.getHostString();
            }
            return "-";
        } catch (RuntimeException e) {
            return "-";
        }
    }

    /**
     * {@link Duration} → 纳秒的<b>饱和</b>换算（{@link Duration#toNanos()} 在溢出时抛
     * {@link ArithmeticException} ⇒ 那种配置值应当视为「几乎无限」而不是让请求启动失败）。
     */
    private static long toNanosSaturated(Duration duration) {
        try {
            return duration.toNanos();
        } catch (ArithmeticException overflow) {
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

        /**
         * 当前线程的 deadline 过期时刻（<b>单调钟</b>纳秒，{@link System#nanoTime()} 域）；
         * 未设置时 {@link #isExpired()} 返回 false。
         *
         * <p>🔴 <b>为什么存「单调钟纳秒」而不是「墙钟 epoch 毫秒」</b>（DRIFT-COMMON-005 修复）：
         * <ul>
         *   <li><b>Go 出处（逐条可核）</b>：标准库 {@code context.WithDeadline}
         *       （{@code $GOROOT/src/context/context.go:625} · {@code :632}）把 deadline 存成
         *       {@code time.Time}（字段见 {@code $GOROOT/src/context/context.go:666}）⇒ <b>纳秒精度</b>；
         *       而 {@code time.Time} <b>自带单调时钟读数</b>（{@code $GOROOT/src/time/time.go:152}
         *       的 {@code ext int64} + {@code :164} 的 {@code hasMonotonic} 标志位）；
         *       {@code time.Until(t)}（{@code $GOROOT/src/time/time.go:1233}）在有单调读数时走
         *       {@code subMono(t.ext, runtimeNano()-startNano)} ⇒ <b>纯单调</b>
         *       ⇒ <b>NTP 校正 / {@code setSystemTime} / 虚拟机快照恢复都影响不到它</b>。</li>
         *   <li><b>旧实现（已修）的两个问题</b>：
         *       <ul>
         *         <li><b>时基</b>：存的是 {@code System.currentTimeMillis()} 的 epoch 毫秒 ⇒ 墙钟
         *             可被 NTP 校正 / 改系统时间 / 快照恢复<b>向前或向后跳</b>
         *             ⇒ <b>倒跳</b>时 {@link #isExpired()} 长时间为 false（<b>超时不触发</b>）、
         *             <b>前跳</b>时提前判过期；</li>
         *         <li><b>精度</b>：毫秒粒度，且是在<b>创建时</b>就截断（{@code timeout.toMillis()}）
         *             ⇒ 有效 deadline 时刻本身最多差 &lt; 1ms。</li>
         *       </ul></li>
         *   <li><b>本实现</b>：把「剩余预算」在<b>绑定瞬间</b>换算成单调钟纳秒存下
         *       ⇒ ① 绑定之后<b>不受任何墙钟跳变影响</b>；② <b>纳秒精度</b>（不再截到毫秒）。</li>
         * </ul>
         *
         * <p>⚠️ <b>与 {@code GoStyleRequestDeadline} 的关系</b>：那是<b>另一个载体</b>
         * （出向 stub / api 线程的 HTTP 预算），它本来就用 {@code System.nanoTime()} + 纳秒
         * ⇒ ⇒ <b>「出向已对齐」推不出「入向也对齐」</b> —— 本条就是<b>入向</b>那一侧。
         */
        private static final ThreadLocal<Long> DEADLINE_NANOS = new ThreadLocal<>();

        /**
         * 墙钟读数（<b>仅用于兼容入口</b> {@link #bindToCurrent(long)} 把 epoch 毫秒换算成预算）。
         *
         * <p>⚠️ <b>为什么是包级可见的可变字段</b>：本条修复的<b>唯一可观察行为</b>是
         * 「墙钟跳变后 deadline 判定不受影响」⇒ ⇒ 而墙钟<b>改不了</b>（改系统时间要 root、
         * 且会污染整台机器）⇒ ⇒ 只能<b>注入</b> ⇒ ⇒ 守门用它模拟一次 NTP 前跳/后跳。
         *
         * <p>🔴 <b>只允许测试改</b>：<b>包级私有</b> ⇒ 外部模块（16 个消费方）碰不到；
         * 守门用 {@code try/finally} 复原 ⇒ ⇒ 不给生产留后门。
         */
        static java.util.function.LongSupplier wallClockMillis = System::currentTimeMillis;

        /**
         * 单调钟读数 —— 与 {@link #wallClockMillis} 同款（<b>包级私有 · 只允许测试改</b>），
         * 用途是让守门<b>确定性地推进时间</b>（不必 {@code sleep}，也不受 CI 负载影响）。
         */
        static java.util.function.LongSupplier monotonicNanos = System::nanoTime;

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
            Long d = DEADLINE_NANOS.get();
            // ⚠️ 用**减法**而不是 `now >= d`：单调钟会回绕（292 年）⇒ 差值法仍正确
            return d != null && monotonicNanos.getAsLong() - d >= 0;
        }

        /** 还剩多少毫秒到 deadline；未设置时返回 Long.MAX_VALUE。 */
        public static long remainingMillis() {
            Long d = DEADLINE_NANOS.get();
            if (d == null) return Long.MAX_VALUE;
            long remainNanos = d - monotonicNanos.getAsLong();
            if (remainNanos <= 0) return 0L;
            // 🔴 **下取整**（对齐 Go：time.Until 返回 Duration，转毫秒即截断）
            return remainNanos / 1_000_000L;
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
         *
         * <p>🔴 <b>兼容入口（DRIFT-COMMON-005 修复后的形态）</b>：入参仍是<b>墙钟 epoch 毫秒</b>
         * （既有跨域调用方都这么传：{@code favorite-rpc/.../FavoriteAuthorityEndpointConfig.java:276}
         * ＋ comment-rpc / favorite-rpc 的测试）⇒ ⇒ <b>签名与语义都不变</b>。
         * <p>本方法在<b>绑定瞬间</b>把「墙钟 epoch 毫秒」换算成「剩余预算」，
         * 再以<b>单调钟纳秒</b>存下 ⇒ ⇒
         * <ul>
         *   <li><b>到期时刻</b>仍是调用方给的那个墙钟时刻（语义不变）；</li>
         *   <li>但<b>到期判定</b>不再受绑定之后的墙钟跳变影响（这才是修复点）。</li>
         * </ul>
         * <p>⚠️ <b>新代码请用 {@link #bindRemainingNanos(long)}</b> —— 它是<b>纳秒精度</b>、
         * 且完全不依赖墙钟（生产路径已全部改用它）。
         */
        public static Scope bindToCurrent(long epochMillis) {
            long remainingMillis = epochMillis - wallClockMillis.getAsLong();
            return bindRemainingNanos(saturatingToNanos(remainingMillis));
        }

        /**
         * 绑定「从现在起还剩 N 纳秒」的预算（<b>单调钟 + 纳秒精度</b>）—— <b>生产路径用这个</b>。
         *
         * <p>⚠️ 与 {@link #bindToCurrent(long)} 的差别：① <b>纳秒</b>（不再被截到毫秒）；
         * ② <b>完全不读墙钟</b> ⇒ ⇒ 预算来源由调用方给（客户端 deadline / 服务端 timeout），
         * 载体内部不做任何墙钟换算。
         *
         * @param remainingNanos 剩余预算（纳秒）；{@link Long#MAX_VALUE} 表示「无期限」
         *                       （与既有 {@code Long.MAX_VALUE} 哨兵同形）
         */
        public static Scope bindRemainingNanos(long remainingNanos) {
            long now = monotonicNanos.getAsLong();
            // 🔴 饱和加法：now + remainingNanos 溢出 ⇒ 会变成「负数」⇒ 误判为已过期
            DEADLINE_NANOS.set(
                    (remainingNanos == Long.MAX_VALUE || remainingNanos > Long.MAX_VALUE - now)
                            ? Long.MAX_VALUE
                            : now + remainingNanos);
            return () -> DEADLINE_NANOS.remove();
        }

        /**
         * 绑定一个<b>绝对</b>到期时刻（单调钟纳秒）—— <b>与出向载体绑同一个时刻时用这个</b>。
         *
         * <p>🔴 <b>为什么需要它</b>：服务端有<b>两个</b> deadline 载体
         * （本类 = SQL 超时用；{@link GoStyleRequestDeadline} = 出向 gRPC 用）
         * ⇒ 它们必须指向<b>同一个绝对时刻</b>，否则两者会差出「各算各的 now」的那点纳秒
         * ⇒ ⇒ 调用方<b>算一次绝对时刻</b>、两个载体共用。
         *
         * <p>⚠️ 时基必须与本类的 {@link #monotonicNanos} 一致（= {@code System::nanoTime}）
         * ⇒ 调用方用 {@code System.nanoTime()} 计算即可。
         */
        public static Scope bindAbsoluteNanos(long absoluteNanos) {
            DEADLINE_NANOS.set(absoluteNanos);
            return () -> DEADLINE_NANOS.remove();
        }

        /**
         * 毫秒 → 纳秒的<b>饱和</b>换算（负数原样保留 ⇒ 仍表示「已过期」）。
         */
        private static long saturatingToNanos(long millis) {
            if (millis <= 0) {
                return millis;
            }
            if (millis > Long.MAX_VALUE / 1_000_000L) {
                return Long.MAX_VALUE;
            }
            return millis * 1_000_000L;
        }

        /** try-with-resources 析构接口；业务方通常不直接调用。 */
        @FunctionalInterface
        public interface Scope extends AutoCloseable {
            @Override
            void close();
        }
    }
}
