package com.rag.hot.hot.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 补全线程池 —— 为 {@code HotArticleHydrator} 提供「有界的平台线程」，
 * 以承接 Go 侧 100 个 goroutine + 容量 8 信号量的并发结构。
 *
 * <h2>为什么需要一个池（Go 侧是裸 goroutine）</h2>
 * Go 的 {@code go func(){...}()}（{@code getHotArticlesLogic.go:101-109}）零成本，
 * Java 没有等价物：要么每个 item 一个平台线程（100 个线程/请求，不可接受），
 * 要么落到一个有界池上并用信号量卡住「在途数」。
 * <p>Spring 的 {@link ThreadPoolTaskExecutor} 正是这个角色：
 * <b>自带生命周期</b>（{@code @Bean} 的 {@code destroyMethod} 会在容器关闭时
 * {@code shutdown()}，不用手写 {@code Runtime.addShutdownHook}）。
 *
 * <h2>池子为什么是 32 而不是 8</h2>
 * <b>并发窗口 8 是「每个请求」的</b>，由 {@code HotArticleHydrator} 里的
 * {@code Semaphore(8)} 施加；池的容量必须容纳<b>多个并发请求</b>各自的 8 个在途任务，
 * 否则一个请求会把池占满、其它请求排队（而 Go 侧它们是各自独立的 8）。
 * 取 32 = 4 个并发请求 × 8，是「每请求 8 窗口」的合理放大；
 * ⚠️ 它<b>不是</b>并发窗口本身 —— 真正的上限由信号量决定，
 * 调大池子不会让单个请求跑得更快。
 *
 * <h2>为什么队列要<b>有界</b></h2>
 * {@code queueCapacity} 无界时，一个慢下游 + 大窗口会把请求堆成任务队列，
 * 队列里的任务还在<b>持有</b>已经过期的超时预算上下文，且没有任何背压。
 * 有界 + {@code CallerRunsPolicy}：池与队列都满时，<b>提交线程自己跑这个任务</b>
 * （即请求线程），把背压直接传回发起方 —— 效果是「请求变慢」，而不是「静默堆积」。
 */
@Configuration(proxyBeanMethods = false)
public class HotHydrationExecutorConfiguration {

    /** 4 个并发请求 × 每个请求 8 个在途补全。 */
    private static final int POOL_SIZE = 32;

    /**
     * 允许的排队上限。{@code Semaphore(8)} 保证单请求在途 ≤ 8，
     * 所以队列里堆积的只会是<b>其它并发请求</b>的在途任务；
     * 取 64 足以让 4 个并发请求都不排队，再多就交给 {@code CallerRunsPolicy} 背压。
     */
    private static final int QUEUE_CAPACITY = 64;

    /**
     * 线程名前缀：Go 侧这些 goroutine 无名，Java 侧给个前缀才能在线程 dump 里认出来。
     */
    private static final String THREAD_NAME_PREFIX = "hot-hydrate-";

    @Bean("hotHydrationExecutor")
    public Executor hotHydrationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(POOL_SIZE);
        executor.setMaxPoolSize(POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix(THREAD_NAME_PREFIX);
        // 池与队列都满 ⇒ 由提交线程（= 发起请求的 worker）自己执行该任务，把背压传回调用方。
        // 不用 AbortPolicy（会抛 RejectedExecutionException，把 best-effort 的补全变成 500）。
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}