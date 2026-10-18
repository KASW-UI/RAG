package com.rag.common.communityfact;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 对应 Go 版 service/common/communityfact/process.go（69 行）
 *
 * <p>职责：dispatcher 循环把 frozen event 投递到 DC；批次 16，超时 10s，节奏 1s。
 *
 * <p>框架职责：
 * <ul>
 *   <li>Go 版 time.Ticker + for-loop → Java {@code CompletableFuture.get(1s)} + stopSignal 让 1s 节奏可中断</li>
 *   <li>Go 版 ctx.Done → Java {@link DispatcherHandle#stop()} 让业务方主动停止</li>
 *   <li>Go 版 context.WithTimeout 10s + defer cancel() → Java {@code CompletableFuture.get(10s)} + {@code future.cancel(true)}（cancel(true) 给业务线程发 interrupt）</li>
 * </ul>
 */
public final class Dispatcher {

    /**
     * 对应 Go 版 {@code type Dispatcher interface}：
     * 单次从 outbox 取一条 frozen event，调用 sender 投递；返回 (sent bool, err error)。
     */
    @FunctionalInterface
    public interface DispatcherImpl {
        /**
         * @param ctx otel context
         * @param sender Sender
         * @param logger 业务 Logger
         * @return {@code true} 表示已发送一条；{@code false} 表示 outbox 空（停止内层循环）
         */
        boolean dispatchFactOnce(Context ctx, Wire.Sender sender, Logger logger) throws Exception;
    }

    /**
     * 默认配置版：<b>不</b>注册 JVM shutdown hook（Spring 化推荐路径：业务用 {@code @PreDestroy} 显式
     * 调 {@link DispatcherHandle#stop()}）。
     * <p>对应 Go 版 {@code func RunDispatcher(ctx context.Context, dispatcher Dispatcher, sender Sender, logger *slog.Logger, once bool) error}。
     *
     * <p><b>Spring 化变更</b>：原默认版本注册 JVM shutdown hook 在进程退出时兜底；但 JVM hook 在 Spring
     * 容器场景下不可控（与 SpringApplication 的 graceful shutdown 时序冲突，且在测试 / 反复创建场景下
     * 累积超限）。新版默认不注册，业务模块应：
     * <pre>{@code
     * // Spring Boot 业务模块：把 DispatcherHandle 装到 Spring 容器，@PreDestroy 触发 stop
     * @Component
     * class DispatcherLifecycle {
     *     private final DispatcherHandle handle;
     *     DispatcherLifecycle(DispatcherHandle handle) { this.handle = handle; }
     *     @PreDestroy void shutdown() { handle.stop(); }
     * }
     * }</pre>
     */
    public static DispatcherHandle run(Context ctx, DispatcherImpl dispatcher, Wire.Sender sender,
                                       Logger logger, boolean once) {
        return run(ctx, dispatcher, sender, logger, once, false);
    }

    /**
     * 对应 Go 版 {@code func RunDispatcher(ctx context.Context, dispatcher Dispatcher, sender Sender, logger *slog.Logger, once bool) error}。
     *
     * <p><b>DRIFT-015 修复</b>：Go 版调用方可以 {@code cancel()} ctx 主动退出；Java 版返回 {@link DispatcherHandle}，
     * 调用方调 {@link DispatcherHandle#stop()} 立即中断循环（不等 JVM shutdown）。
     *
     * <p><b>DRIFT-008 修复 + Spring 化</b>：{@code registerShutdownHook} 参数控制是否注册 JVM shutdown hook：
     * <ul>
     *   <li>{@code false}（<b>默认</b>）：不注册 JVM hook；业务必须显式调 {@link DispatcherHandle#stop()}
     *       （推荐用 {@code @PreDestroy}）—— Spring 化首选路径，避免 hook 触发时序冲突与测试场景累积。</li>
     *   <li>{@code true}：JVM 退出时自动停止 dispatcher；适合不接 Spring 容器的独立进程。
     *       注意：JVM hook 触发时 Spring 容器已关闭，dispatcher 自身资源被清理即可。</li>
     * </ul>
     *
     * <p><b>DRIFT-008 二次修复</b>：单次 dispatch 10s 超时后不仅返回错误，还 {@code future.cancel(true)} 给业务线程发 interrupt，
     * 业务函数如果响应 {@link InterruptedException} 会立即停（对齐 Go 版 context.WithTimeout 的 cancel 语义）。
     */
    public static DispatcherHandle run(Context ctx, DispatcherImpl dispatcher, Wire.Sender sender,
                                       Logger logger, boolean once, boolean registerShutdownHook) {
        long started = System.nanoTime();
        if (dispatcher == null || sender == null || logger == null) {
            throw new IllegalArgumentException("community fact dispatcher process is not configured");
        }
        Tracer tracer = GlobalOpenTelemetry.get().getTracer("sea.rtw.communityfact");

        // 单独线程池跑 dispatch；用 CompletableFuture.get(10s) 实现 10s deadline
        // 对应 Go 版 context.WithTimeout(attempt, 10*time.Second) + defer cancel()
        ExecutorService dispatchExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "communityfact-dispatch");
            t.setDaemon(true);
            return t;
        });

        // 对应 Go 版内层 dispatch 函数：真正包 10s deadline
        java.util.function.Supplier<DispatchAttempt> dispatch = () -> {
            Span span = tracer.spanBuilder("community.fact_delivery.dispatch").startSpan();
            Context attemptCtx = ctx.with(span);
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return dispatcher.dispatchFactOnce(attemptCtx, sender, logger);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, dispatchExecutor);
            try {
                boolean sent = future.get(10, TimeUnit.SECONDS);
                span.end();
                return new DispatchAttempt(sent, null);
            } catch (TimeoutException te) {
                // DRIFT-008 修复：cancel(true) 触发线程 interrupt，业务函数如果响应 InterruptedException 会立刻停
                future.cancel(true);
                span.end();
                return new DispatchAttempt(false, te);
            } catch (ExecutionException ee) {
                span.end();
                Throwable cause = ee.getCause() != null ? ee.getCause() : ee;
                return new DispatchAttempt(false, cause instanceof Exception ? (Exception) cause : new RuntimeException(cause));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                future.cancel(true);
                span.end();
                return new DispatchAttempt(false, ie);
            }
        };

        AtomicBoolean running = new AtomicBoolean(true);
        Thread runnerThread = Thread.currentThread();
        // DRIFT-015 修复：用 stopSignal（CompletableFuture）替代 JVM shutdown hook，让调用方可以主动 stop
        CompletableFuture<Void> stopSignal = new CompletableFuture<>();
        DispatcherHandle handle = new DispatcherHandle(stopSignal, dispatchExecutor, runnerThread);

        Thread shutdownHook = new Thread(() -> {
            running.set(false);
            stopSignal.complete(null);
        }, "communityfact-dispatcher-shutdown");
        // DRIFT-008 修复：shutdown hook 注册可选，避免测试 / 反复 RunDispatcher 场景下 hook 累积
        if (registerShutdownHook) {
            Runtime.getRuntime().addShutdownHook(shutdownHook);
        }

        try {
            if (once) {
                DispatchAttempt result = dispatch.get();
                if (result.error != null) {
                    throw new RuntimeException(result.error);
                }
                logger.atInfo()
                        .addKeyValue("event", "community.fact_delivery.once")
                        .addKeyValue("outcome", "succeeded")
                        .addKeyValue("duration_ms",
                                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))
                        .addKeyValue("sent", result.sent)
                        .log("community fact delivery inspected");
                return handle;
            }

            // 对应 Go 版 ticker = time.NewTicker(time.Second) + 内层 16 次循环
            while (running.get() && !runnerThread.isInterrupted()) {
                for (int i = 0; i < 16; i++) {
                    if (!running.get() || runnerThread.isInterrupted()) {
                        break;
                    }
                    DispatchAttempt result = dispatch.get();
                    if (result.error != null) {
                        logger.atWarn()
                                .addKeyValue("event", "community.fact_delivery.retry")
                                .addKeyValue("outcome", "retryable")
                                .addKeyValue("error_code", deliveryErrorCode(result.error))
                                .addKeyValue("error_type", "delivery")
                                .log("community fact delivery retry scheduled");
                        break;
                    }
                    if (!result.sent) {
                        break;
                    }
                }
                // 对应 Go 版 select { case <-ctx.Done(): return nil; case <-ticker.C: }
                // 等待最多 1 秒，但 stopSignal.complete() 会立即唤醒（DRIFT-015 修复）
                try {
                    stopSignal.get(1, TimeUnit.SECONDS);
                    // stopSignal 被触发：退出循环
                    return handle;
                } catch (TimeoutException ie) {
                    // 正常 1 秒到，继续外层 while
                } catch (InterruptedException ie) {
                    runnerThread.interrupt();
                    return handle;
                } catch (ExecutionException ee) {
                    return handle;
                }
            }
            return handle;
        } finally {
            // DRIFT-008 修复：仅在注册了 hook 的情况下 remove
            if (registerShutdownHook) {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            }
            dispatchExecutor.shutdownNow();
        }
    }

    /**
     * DRIFT-015 修复：返回的 handle 让业务方可以主动 stop runner（替代依赖 JVM shutdown）。
     *
     * <p><b>Spring 化路径</b>：业务模块把 {@code DispatcherHandle} 注入到 Spring 容器（如 {@code @Component}
     * 持有字段），在 {@code @PreDestroy} / {@code DisposableBean.destroy()} 回调里调
     * {@link #stop()}，避免 JVM shutdown hook 在 SpringApplication 关闭链路里触发时序冲突。
     */
    public static final class DispatcherHandle {
        private final CompletableFuture<Void> stopSignal;
        private final ExecutorService dispatchExecutor;
        private final Thread runnerThread;

        DispatcherHandle(CompletableFuture<Void> stopSignal, ExecutorService dispatchExecutor, Thread runnerThread) {
            this.stopSignal = stopSignal;
            this.dispatchExecutor = dispatchExecutor;
            this.runnerThread = runnerThread;
        }

        /**
         * 立即停止 runner 循环（不等 JVM shutdown）：
         * <ol>
         *   <li>触发 {@link #stopSignal} 让 runner 的 1s 轮询立即唤醒退出</li>
         *   <li>{@code interrupt()} 主 runner 线程（若处于阻塞 sleep / IO）</li>
         *   <li>{@code shutdownNow()} dispatch 线程池——若业务线程正在跑，会被 interrupt</li>
         * </ol>
         *
         * <p>对应 Go 版 {@code ctx, cancel := context.WithCancel(...) + cancel()}。
         *
         * <p><b>幂等</b>：重复调用安全（{@code stopSignal.complete} 第二次 no-op；{@code interrupt} 幂等；
         * {@code shutdownNow} 重复执行 OK）。
         *
         * <p><b>Spring 化</b>：推荐在 {@code @PreDestroy} 里调用，让 Spring 容器关闭与 dispatcher
         * 关闭时序一致，避免 JVM hook 触发时 Spring 容器已关闭导致的资源清理不一致。
         */
        public void stop() {
            if (stopSignal != null) {
                stopSignal.complete(null);
            }
            if (runnerThread != null) {
                runnerThread.interrupt();
            }
            if (dispatchExecutor != null) {
                dispatchExecutor.shutdownNow();
            }
        }
    }

    /**
     * 对应 Go 版 {@code func deliveryErrorCode(err error) string}：
     * 区分 frozen envelope 阻塞 vs 普通投递失败。
     */
    public static String deliveryErrorCode(Throwable err) {
        if (err == null) {
            return "DELIVERY_ATTEMPT_FAILED";
        }
        Throwable t = err;
        while (t != null) {
            if (t == Wire.ERR_FROZEN_ENVELOPE_BLOCKED
                    || (t.getMessage() != null && t.getMessage().equals(Wire.ERR_FROZEN_ENVELOPE_BLOCKED.getMessage()))) {
                return "INVALID_FROZEN_ENVELOPE";
            }
            t = t.getCause();
        }
        return "DELIVERY_ATTEMPT_FAILED";
    }

    private static final class DispatchAttempt {
        final boolean sent;
        final Throwable error;

        DispatchAttempt(boolean sent, Throwable error) {
            this.sent = sent;
            this.error = error;
        }
    }

    private Dispatcher() {
    }
}
