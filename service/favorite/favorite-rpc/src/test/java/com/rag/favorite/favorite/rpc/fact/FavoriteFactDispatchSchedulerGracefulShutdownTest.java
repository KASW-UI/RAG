package com.rag.favorite.favorite.rpc.fact;

import com.rag.common.communityfact.Wire;
import com.rag.favorite.favorite.rpc.bootstrap.FavoriteFactProcessLogging;
import com.rag.favorite.favorite.rpc.config.FavoriteRpcProperties;
import com.rag.favorite.favorite.rpc.service.FavoriteFactDispatchService;
import io.opentelemetry.context.Context;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.StandardEnvironment;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🔴 <b>DRIFT-CF-018 的 favorite 域半边</b>：进程能否<b>优雅关闭</b>。
 *
 * <h2>这条缺陷的机制（共享层半边已修 · 域侧半边本类守）</h2>
 * <pre>
 * 共享层 Dispatcher.run(ctx, dispatcher, sender, logger, once, registerShutdownHook)
 *   = 已 @Deprecated(forRemoval = true) 的 6 参重载
 *   ⇒ 它内部**自己**新建句柄 ⇒ 句柄只有在**循环结束**时才交得出来
 *   ⇒ once = false 时循环永不结束 ⇒ **调用方永远拿不到句柄**
 *   ⇒ @PreDestroy stop() 拿到 null ⇒ **循环停不下来**
 *   ⇒ 且循环跑在**调用线程**上
 *   ⇒ 本类的触发点是 @EventListener(ApplicationReadyEvent)
 *   ⇒ 阻塞的是 **SpringApplication.run() 的主线程**
 *   ⇒ ⇒ **进程无法优雅关闭**（主线程永久占用 + 投递循环停不掉）
 * </pre>
 *
 * <h2>正确形态（与 like / comment 域逐字同形）</h2>
 * <p>{@link com.rag.common.communityfact.Dispatcher#runAsync} ⇒ 循环跑在守护线程上
 * ＋ 句柄<b>进入循环之前</b>就发布 ⇒ {@code @PreDestroy} 拿得到句柄 ⇒ 能停 ⇒ 可优雅关闭。
 *
 * <h2>⚠️ 为什么「超时 ⇒ 抛 AssertionError」而不是直接调</h2>
 * <p>若代码退回阻塞式 {@code Dispatcher.run}，在测试线程里直接调 {@code start()} ⇒
 * <b>永远不返回</b> ⇒ 整个测试<b>挂住</b>（而不是变红）⇒ CI 被拖死。
 * ⇒ 故 {@code start()} 必须跑在<b>守护线程</b>里并带上限 ⇒ 超时即<b>精确变红</b>。
 * （手法与 {@code service/like/.../LikeFactDispatchSchedulerStartupTest} 同款。）
 *
 * <h2>「进程在 N 秒内退出」在进程内的可判定代理</h2>
 * <p>单测不可能真的退出 JVM ⇒ 等价判据 = <b>投递线程在 N 秒内真的消失</b>
 * （它由 {@code @PreDestroy → stop()} 停掉 ⇒ 停得掉 ⇔ 句柄非 null ⇔ 可优雅关闭）。
 */
class FavoriteFactDispatchSchedulerGracefulShutdownTest {

    /** 共享层 {@code Dispatcher.runAsync} 起的守护线程名（Dispatcher.java:236）。 */
    private static final String RUNNER_THREAD_NAME = "communityfact-dispatcher";

    /** {@code start()} 的上限：阻塞式形态下超过它就判红。 */
    private static final Duration START_TIMEOUT = Duration.ofSeconds(15);

    /** 投递线程消失的等待上限。 */
    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(15);

    /** {@code Wire.DCEventSender.create} 要求 token ≥ 32 字节（Wire.java:191）。 */
    private static final String VALID_TOKEN = "t".repeat(32);

    // ====================================================================================
    // ① 默认关闭 ⇒ 完全不启动（守卫「开关默认关闭时这类缺陷测不出来」）
    // ====================================================================================

    @Test
    @DisplayName("🔴 默认关闭 ⇒ 不启动投递循环（不建线程 · 不拿句柄）")
    void disabledSchedulerStartsNothing() {
        AnnotationConfigApplicationContext ctx = context(props(false), new AtomicInteger());
        try {
            FavoriteFactDispatchScheduler scheduler = ctx.getBean(FavoriteFactDispatchScheduler.class);
            scheduler.start();
            assertThat(scheduler.isRunning())
                    .as("未启用 ⇒ 不得有句柄（也就没有线程可停）")
                    .isFalse();
        } finally {
            ctx.close();
        }
    }

    // ====================================================================================
    // ② 开启 ⇒ start() 不阻塞 + 句柄立即交出（DRIFT-CF-018 的核心判据）
    // ====================================================================================

    @Test
    @DisplayName("🔴 开启 ⇒ start() **不阻塞**且拿到**非 null** 句柄（阻塞式 run ⇒ 句柄永远交不出）")
    void enabledSchedulerStartsWithoutBlockingAndPublishesHandle() {
        AnnotationConfigApplicationContext ctx = context(props(true), new AtomicInteger());
        try {
            FavoriteFactDispatchScheduler scheduler = ctx.getBean(FavoriteFactDispatchScheduler.class);

            // 🔴 在守护线程里调：退回阻塞式 run 时这里是**超时变红**而不是挂死
            startWithTimeout(scheduler);

            assertThat(scheduler.isRunning())
                    .as("runAsync 在进入循环之前就发布句柄 ⇒ @PreDestroy 才停得下来（进程可优雅关闭）")
                    .isTrue();
        } finally {
            ctx.close();
        }
    }

    // ====================================================================================
    // ③ 容器关闭 ⇒ @PreDestroy 真的停掉投递线程（优雅关闭成立）
    // ====================================================================================

    @Test
    @DisplayName("🔴 容器关闭 ⇒ @PreDestroy 拿到句柄并让投递线程在 N 秒内消失（进程可优雅关闭）")
    void containerCloseStopsDispatcherThread() throws Exception {
        AnnotationConfigApplicationContext ctx = context(props(true), new AtomicInteger());
        Set<Long> before = runnerThreadIds();
        try {
            FavoriteFactDispatchScheduler scheduler = ctx.getBean(FavoriteFactDispatchScheduler.class);
            startWithTimeout(scheduler);

            // 本轮起的投递线程（按 threadId 精确定位 ⇒ 不受同模块其它测试的残留影响）
            long runnerId = awaitNewRunnerThread(before);
            assertThat(Thread.currentThread().getId())
                    .as("投递线程应当是**另一条**线程（runAsync ⇒ 守护线程，不阻塞调用方）")
                    .isNotEqualTo(runnerId);
        } finally {
            ctx.close();
        }
        // 上下文已关闭 ⇒ @PreDestroy ⇒ stop() ⇒ 投递线程必须真的消失
        awaitTrue(() -> !isAlive(runnerThreadIds()), STOP_TIMEOUT);
        assertThat(isAlive(runnerThreadIds()))
                .as("容器关闭后投递线程必须真的退出（@PreDestroy → stop() ⇒ 句柄非 null 的直接后果）")
                .isFalse();
    }

    // ====================================================================================
    // 夹具
    // ====================================================================================

    /**
     * 🔴 在<b>独立守护线程</b>里调 {@code start()}，并给一个上限。
     *
     * <p>⚠️ 阻塞式 {@code Dispatcher.run} ⇒ {@code start()} 永不返回 ⇒ 测试<b>挂住</b>（不是变红）
     * ⇒ CI 被拖死 ⇒ 故必须隔离到守护线程 + 超时。
     */
    private static void startWithTimeout(FavoriteFactDispatchScheduler scheduler) {
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "favorite-dispatch-start-probe");
            t.setDaemon(true);
            return t;
        });
        try {
            Future<?> started = executor.submit(scheduler::start);
            started.get(START_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new AssertionError("🔴 start() 被阻塞（超过 " + START_TIMEOUT.toSeconds()
                    + "s 未返回）⇒ 调度器用了阻塞式 Dispatcher.run（once=false ⇒ 句柄永远交不出来）"
                    + "，必须改成非阻塞的 Dispatcher.runAsync");
        } catch (ExecutionException e) {
            throw new AssertionError("🔴 start() 失败", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("🔴 start() 被中断", e);
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 最小 Spring 上下文：<b>真的装配</b>调度器 ⇒ {@code @PreDestroy} 会真的跑。
     *
     * <p>⚠️ 用 {@code AnnotationConfigApplicationContext} 而不是 {@code @SpringBootTest}：
     * 本类只验「调度器的生命周期」，不需要 Redis / Kafka / Nacos / gRPC。
     */
    private static AnnotationConfigApplicationContext context(FavoriteRpcProperties props, AtomicInteger calls) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.registerBean(FavoriteRpcProperties.class, () -> props);
        ctx.registerBean(FavoriteFactDispatchService.class, () -> new StubDispatchService(calls));
        ctx.registerBean(FavoriteFactProcessLogging.class,
                () -> new FavoriteFactProcessLogging("favorite-rpc", "test-host", new StandardEnvironment()));
        ctx.registerBean(FavoriteFactDispatchScheduler.class);
        ctx.refresh();
        return ctx;
    }

    /** 构造配置：{@code enabled} / {@code dcToken} 可控。 */
    private static FavoriteRpcProperties props(boolean dispatchEnabled) {
        FavoriteRpcProperties props = new FavoriteRpcProperties();
        props.getFact().getDispatch().setEnabled(dispatchEnabled);
        // ⚠️ endpoint 的 path 必须是 /v1/events —— 共享层 Wire.DCEventSender.create(:201) 硬校验
        props.getFact().getDispatch().setDcEndpoint("http://127.0.0.1:1/v1/events");
        props.getFact().getDispatch().setDcToken(VALID_TOKEN);
        props.getFact().getDispatch().setDcTimeout(Duration.ofSeconds(1));
        props.getFact().getDispatch().setOnce(false);
        return props;
    }

    /** 所有存活的共享层投递线程 id。 */
    private static Set<Long> runnerThreadIds() {
        Set<Long> ids = new HashSet<>();
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (RUNNER_THREAD_NAME.equals(thread.getName()) && thread.isAlive()) {
                ids.add(thread.getId());
            }
        }
        return ids;
    }

    private static boolean isAlive(Set<Long> ids) {
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (RUNNER_THREAD_NAME.equals(thread.getName()) && thread.isAlive() && ids.contains(thread.getId())) {
                return true;
            }
        }
        return false;
    }

    /** 等到出现一条<b>本轮新增</b>的投递线程，返回它的 id。 */
    private static long awaitNewRunnerThread(Set<Long> before) throws InterruptedException {
        long deadline = System.nanoTime() + STOP_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            for (Long id : runnerThreadIds()) {
                if (!before.contains(id)) {
                    return id;
                }
            }
            Thread.sleep(50);
        }
        throw new AssertionError("🔴 开启后 15s 内没出现共享层投递线程 ⇒ 调度器没真正启动循环");
    }

    /** 轮询等待条件成立（不引第三方库）。 */
    private static void awaitTrue(BooleanSupplier condition, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
    }

    /**
     * 只计数、不真的投递 —— 本类要验的是<b>调度生命周期</b>，不是投递语义
     * （投递语义由真 PG 上的对拍门禁守）。
     *
     * <p>返回 {@code false} = 「outbox 空」⇒ 共享层内层批次 break ⇒ 外层按 1s 节奏继续。
     */
    private static final class StubDispatchService extends FavoriteFactDispatchService {

        private final AtomicInteger calls;

        StubDispatchService(AtomicInteger calls) {
            super(null, null, null);
            this.calls = calls;
        }

        @Override
        public boolean dispatchFactOnce(Context ctx, Wire.Sender sender) {
            calls.incrementAndGet();
            return false;
        }
    }
}
