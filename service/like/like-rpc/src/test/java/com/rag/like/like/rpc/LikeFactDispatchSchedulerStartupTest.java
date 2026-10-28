package com.rag.like.like.rpc;

import com.rag.common.communityfact.Wire;
import com.rag.like.like.rpc.config.LikeRpcProperties;
import com.rag.like.like.rpc.mq.LikeFactDispatchScheduler;
import com.rag.like.like.rpc.service.LikeFactDispatchService;
import io.opentelemetry.context.Context;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.time.Duration;
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
 * 🔴🔴 事实投递调度器的<b>真启动</b>守门 —— 对应 Go 端
 * {@code service/like/rpc/cmd/fact-dispatch/main.go:51-77} 的进程启动条件。
 *
 * <h2>为什么必须「真启动」而不是 mock 掉</h2>
 * <p>本类要抓的缺陷是<b>启动阻塞</b>：
 * 共享层 {@code Dispatcher.run} 是<b>阻塞式</b>的（投递循环跑在<b>调用线程</b>上，
 * 直到 {@code stop()} 才返回），而调度器的启动点挂在 {@code @PostConstruct} 上
 * —— 那是 <b>Spring 上下文刷新线程</b> ⇒ 一旦用 {@code run} 而不是 {@code runAsync}，
 * <b>上下文永远刷新不完</b> ⇒ <b>整个 like-rpc 起不来</b>。
 *
 * <p>⚠️ <b>这类缺陷用 mock 一定测不出来</b>：
 * <ul>
 *   <li>mock 掉 {@code Dispatcher.run} ⇒ 根本不进循环 ⇒ 测不到阻塞；</li>
 *   <li>只测「开关关闭时不起线程」⇒ 默认关闭时缺陷根本不显现 ⇒ 测不到；</li>
 *   <li>纯 Mockito 单测不装配 Spring 上下文 ⇒ {@code @PostConstruct} 压根不会跑。</li>
 * </ul>
 * ⇒ 唯一有效的形态是：<b>真的起一个 Spring 上下文</b>（{@code @PostConstruct} 真的会执行），
 * 并把 {@code refresh()} 放到<b>独立线程</b>里跑 + <b>超时</b> ——
 * 阻塞就会被这条超时抓住。
 *
 * <h2>🔴 本守门自己也踩过坑：<b>「挂住」不算「红」</b></h2>
 * <p>首版把 {@code ctx.refresh()} 直接放在测试线程里调 ⇒ 变异 M10（改回阻塞式 {@code run}）时
 * <b>整个测试挂住</b>，而不是变红 —— CI 会被拖死。
 * <p>更隐蔽的一层：{@code refresh()} 挂住时**不能再调 {@code ctx.close()}** ——
 * Spring 的 {@code refresh()} 与 {@code close()} 都 {@code synchronized} 在
 * {@code startupShutdownMonitor} 上 ⇒ {@code close()} 与 {@code refresh()} <b>互等</b> ⇒ <b>死锁</b>。
 * ⇒ 本类的做法：<b>refresh 放在独立守护线程 + 20s 上限</b>；
 * <b>refresh 没成功就不 close</b>（守护线程随 JVM 退出而死）。
 *
 * <h2>五条用例分别钉住五个语义</h2>
 * <ol>
 *   <li>默认关闭 ⇒ <b>完全不启动任何线程</b>（Go：缺 DC 配置的独立进程不启动）；</li>
 *   <li>默认配置（不显式设置）⇒ 事实投递默认关闭；</li>
 *   <li>配置不全（token &lt; 32）⇒ 不启动（Go {@code main.go:55}）；</li>
 *   <li>开启 ⇒ <b>上下文刷新不被阻塞</b>（本类的核心断言）；</li>
 *   <li>开启 ⇒ 后台线程<b>真的在投递</b>，且容器关闭后<b>真的停</b>。</li>
 * </ol>
 */
class LikeFactDispatchSchedulerStartupTest {

    /** Go {@code cmd/fact-dispatch/main.go:55} 要求 {@code len(token) >= 32}。 */
    private static final String VALID_TOKEN = "t".repeat(32);

    /** 刷新超时：够慢也够快（阻塞的实现会在这个时限内被抓住）。 */
    private static final Duration REFRESH_TIMEOUT = Duration.ofSeconds(20);

    /** 共享层后台线程名（{@code Dispatcher.runAsync} 里起的守护线程）。 */
    private static final String RUNNER_THREAD_NAME = "communityfact-dispatcher";

    // ====================================================================================
    // ① 默认关闭 ⇒ 完全不启动任何线程
    // ====================================================================================

    @Test
    @DisplayName("🔴 默认关闭 ⇒ 完全不启动任何线程（不创建 sender、不进投递循环）")
    void disabledSchedulerStartsNothing() {
        AtomicInteger calls = new AtomicInteger();
        AnnotationConfigApplicationContext ctx = context(props(false, VALID_TOKEN), calls);
        boolean refreshed = false;
        try {
            refreshWithTimeout(ctx);
            refreshed = true;

            LikeFactDispatchScheduler scheduler = ctx.getBean(LikeFactDispatchScheduler.class);
            assertThat(scheduler.isRunning())
                    .as("开关关闭时不得创建调度句柄（Go：缺 DC 配置的独立进程根本不启动）")
                    .isFalse();
            assertThat(calls.get())
                    .as("开关关闭时一次投递都不许发生")
                    .isZero();
            assertThat(hasRunnerThread())
                    .as("开关关闭时不得留下后台投递线程")
                    .isFalse();
        } finally {
            closeIfRefreshed(ctx, refreshed);
        }
    }

    // ====================================================================================
    // ② 默认配置 ⇒ 默认关闭
    // ====================================================================================

    @Test
    @DisplayName("🔴 默认配置（不显式设置任何开关）⇒ 事实投递默认关闭")
    void defaultConfigurationKeepsDispatchDisabled() {
        AtomicInteger calls = new AtomicInteger();
        // 刻意**不**调用 setDispatchEnabled ⇒ 用类里的默认值
        // ⇒ 守的是「默认值必须是 false」（Go：缺 DC 配置的独立进程根本不启动）
        LikeRpcProperties defaults = new LikeRpcProperties();
        defaults.getFact().setDcEndpoint("http://127.0.0.1:1/v1/events");
        defaults.getFact().setDcToken(VALID_TOKEN);

        AnnotationConfigApplicationContext ctx = context(defaults, calls);
        boolean refreshed = false;
        try {
            refreshWithTimeout(ctx);
            refreshed = true;

            LikeFactDispatchScheduler scheduler = ctx.getBean(LikeFactDispatchScheduler.class);
            assertThat(scheduler.isRunning())
                    .as("like.fact.dispatch-enabled 的默认值必须是 false（Go 侧是独立进程，默认不启动）")
                    .isFalse();
            assertThat(hasRunnerThread()).isFalse();
        } finally {
            closeIfRefreshed(ctx, refreshed);
        }
    }

    // ====================================================================================
    // ③ 配置不全 ⇒ 不启动（Go main.go:55-56 的进程退出条件）
    // ====================================================================================

    @Test
    @DisplayName("🔴 配置不全（dc-token < 32 字节）⇒ 不启动（Go main.go:55）")
    void incompleteConfigurationDoesNotStart() {
        AtomicInteger calls = new AtomicInteger();
        // 单 JVM 里不能像 Go 那样「进程退出」（会带走 gRPC 与 Kafka 消费）⇒ 告警后保持停用。
        AnnotationConfigApplicationContext ctx = context(props(true, "short-token"), calls);
        boolean refreshed = false;
        try {
            refreshWithTimeout(ctx);
            refreshed = true;

            LikeFactDispatchScheduler scheduler = ctx.getBean(LikeFactDispatchScheduler.class);
            assertThat(scheduler.isRunning())
                    .as("配置不完整时不得启动投递循环")
                    .isFalse();
            assertThat(calls.get()).isZero();
        } finally {
            closeIfRefreshed(ctx, refreshed);
        }
    }

    // ====================================================================================
    // ④ 开启 ⇒ 上下文刷新不被阻塞（本类的核心断言）
    // ====================================================================================

    @Test
    @DisplayName("🔴 开启 ⇒ 上下文刷新**不被 @PostConstruct 阻塞**（Dispatcher.run 是阻塞式的 ⇒ 必须 runAsync）")
    void enabledSchedulerDoesNotBlockContextRefresh() {
        AtomicInteger calls = new AtomicInteger();
        AnnotationConfigApplicationContext ctx = context(props(true, VALID_TOKEN), calls);
        boolean refreshed = false;
        try {
            // 🔴 refreshWithTimeout 在被阻塞时**抛 AssertionError**（不是挂住）
            refreshWithTimeout(ctx);
            refreshed = true;

            LikeFactDispatchScheduler scheduler = ctx.getBean(LikeFactDispatchScheduler.class);
            assertThat(scheduler.isRunning())
                    .as("开启后应拿到调度句柄（runAsync 立即返回句柄）")
                    .isTrue();
        } finally {
            closeIfRefreshed(ctx, refreshed);
        }
    }

    // ====================================================================================
    // ⑤ 开启 ⇒ 后台线程真的在投递，且容器关闭后真的停
    // ====================================================================================

    @Test
    @DisplayName("🔴 开启 ⇒ 后台线程真的在投递；容器关闭后线程真的消失")
    void enabledSchedulerRunsInBackgroundAndStopsOnClose() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AnnotationConfigApplicationContext ctx = context(props(true, VALID_TOKEN), calls);
        boolean refreshed = false;
        try {
            refreshWithTimeout(ctx);
            refreshed = true;

            // 后台线程真的跑了（每秒一轮 ⇒ 10s 内必然被调用过）
            awaitTrue(() -> calls.get() > 0, Duration.ofSeconds(10));
            assertThat(calls.get())
                    .as("开启后后台线程必须真的在调用投递逻辑")
                    .isGreaterThan(0);
            assertThat(hasRunnerThread())
                    .as("开启后应存在后台投递线程")
                    .isTrue();
        } finally {
            closeIfRefreshed(ctx, refreshed);
        }
        // 上下文已关闭 ⇒ @PreDestroy ⇒ stop()
        awaitTrue(() -> !hasRunnerThread(), Duration.ofSeconds(10));
        assertThat(hasRunnerThread())
                .as("容器关闭后投递线程必须真的退出（@PreDestroy → stop()）")
                .isFalse();
    }

    // ====================================================================================
    // 夹具
    // ====================================================================================

    /**
     * 🔴 在<b>独立守护线程</b>里 refresh，并给一个上限。
     *
     * <p>⚠️ <b>为什么不能直接在测试线程里调 {@code ctx.refresh()}</b>：
     * {@code @PostConstruct} 里若调了阻塞式 {@code Dispatcher.run} ⇒ refresh 永不返回
     * ⇒ 整个测试<b>挂住</b>（而不是变红）⇒ CI 被拖死。
     *
     * <p>⚠️ <b>为什么这里不调 {@code ctx.close()}</b>：refresh 与 close 都
     * {@code synchronized} 在 {@code startupShutdownMonitor} 上
     * ⇒ refresh 还挂着时 close 会与它<b>互等</b> ⇒ <b>死锁</b>。
     * ⇒ 刷新没成功就不 close（守护线程随 JVM 退出而死）。
     */
    private static void refreshWithTimeout(AnnotationConfigApplicationContext ctx) {
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "like-refresh-probe");
            t.setDaemon(true);
            return t;
        });
        try {
            Future<?> refresh = executor.submit(ctx::refresh);
            refresh.get(REFRESH_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new AssertionError("🔴 上下文刷新被 @PostConstruct 阻塞（超过 " + REFRESH_TIMEOUT.toSeconds()
                    + "s 未返回）⇒ 调度器用了阻塞式 Dispatcher.run，必须改成非阻塞的 runAsync");
        } catch (ExecutionException e) {
            throw new AssertionError("🔴 上下文刷新失败", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("🔴 上下文刷新被中断", e);
        } finally {
            executor.shutdownNow();
        }
    }

    /** 只有刷新成功才关上下文（否则会与仍挂着的 refresh 死锁）。 */
    private static void closeIfRefreshed(AnnotationConfigApplicationContext ctx, boolean refreshed) {
        if (refreshed) {
            ctx.close();
        }
    }

    /** 构造配置：{@code dispatchEnabled} / {@code dcToken} 可控，其余取默认。 */
    private static LikeRpcProperties props(boolean dispatchEnabled, String dcToken) {
        LikeRpcProperties props = new LikeRpcProperties();
        props.getFact().setDispatchEnabled(dispatchEnabled);
        // ⚠️ endpoint 的 path 必须是 /v1/events —— 共享层 Wire.DCEventSender.create(:201) 硬校验
        props.getFact().setDcEndpoint("http://127.0.0.1:1/v1/events");
        props.getFact().setDcToken(dcToken);
        props.getFact().setDcTimeout(Duration.ofSeconds(1));
        props.getFact().setOnce(false);
        return props;
    }

    /**
     * 最小 Spring 上下文：<b>真的装配</b>调度器 ⇒ {@code @PostConstruct} / {@code @PreDestroy} 会真的跑。
     *
     * <p>⚠️ 用 {@code AnnotationConfigApplicationContext} 而不是 {@code @SpringBootTest}：
     * 本类只验「调度器的生命周期」，不需要 Redis / Kafka / Nacos / gRPC。
     */
    private static AnnotationConfigApplicationContext context(LikeRpcProperties props, AtomicInteger calls) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.registerBean(LikeRpcProperties.class, () -> props);
        ctx.registerBean(LikeFactDispatchService.class, () -> new StubDeliveryService(calls));
        ctx.registerBean(LikeFactDispatchScheduler.class);
        return ctx;
    }

    /** 是否还有共享层的后台投递线程活着。 */
    private static boolean hasRunnerThread() {
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (RUNNER_THREAD_NAME.equals(thread.getName()) && thread.isAlive()) {
                return true;
            }
        }
        return false;
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
     * （投递语义由 {@code LikeFactDeliveryGoSemanticsGateTest} 在真 PG 上守）。
     *
     * <p>返回 {@code false} = 「outbox 空」⇒ 共享层内层批次 break ⇒ 外层按 1s 节奏继续。
     */
    private static final class StubDeliveryService extends LikeFactDispatchService {

        private final AtomicInteger calls;

        StubDeliveryService(AtomicInteger calls) {
            super(null, null, null);
            this.calls = calls;
        }

        @Override
        public boolean dispatchFactOnce(Context ctx, Wire.Sender sender, Logger logger) {
            calls.incrementAndGet();
            return false;
        }
    }
}
