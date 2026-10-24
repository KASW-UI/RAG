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
 * <h2>池子为什么是 64 而不是 8</h2>
 * <b>并发窗口 8 是「每个请求」的</b>，由 {@code HotArticleHydrator} 里的
 * {@code Semaphore(8)} 施加；池的容量必须容纳<b>多个并发请求</b>各自的 8 个在途任务，
 * 否则一个请求会把池占满、其它请求排队（而 Go 侧它们是各自独立的 8）。
 *
 * <p><b>⚠️ 64 这个数是实测定的，不是拍的</b>（2026-10-24 第十轮 U10，DRIFT-HOTAPI-028）：
 * 旧值 <b>32</b> = 「4 个并发请求 × 8」⇒ 在 **8 并发**下，合计在途被池压到 32
 * （= 每请求窗口从 8 掉到 4）⇒ 实测 <b>p99 1.76×</b>、p50 1.95×（Go 405.9ms vs RAG 715.3ms）。
 * 改成 <b>64 = 8 并发 × 8 窗口</b> ⇒ 8 并发档位下<b>每请求都拿回完整的 8 在途</b>
 * ⇒ 与 Go 一致（Go 侧 8 并发时合计在途 64，实测下游峰值 = 64）。
 *
 * <p><b>⚠️ 与 Go 的残余差异（本类的已知偏离，已签字）</b>：
 * Go 侧<b>没有跨请求的总上限</b> —— {@code getHotArticlesLogic.go:93} 的
 * {@code sem := make(chan struct{}, 8)} 是<b>每个请求各建一个</b>，
 * N 个并发请求 ⇒ N×8 个同时在途的下游调用，**无全局封顶**。
 * 本类是一个<b>全局</b>池 ⇒ N 超过 8 时，合计在途仍被 64（+ 队列 64）封顶。
 * ⇒ 这是「<b>RAG 比 Go 更保守 / 更保护下游</b>」的一侧 ⇒ 已按
 * {@code SD-HOT-API-010} 签字留痕（<b>不是缺陷，是决策</b>）。
 * ⇒ 影响：并发 ≤ 8 时**完全等价**；并发 &gt; 8 时 RAG 排队、Go 不排 ⇒ 延迟分布不同。
 *
 * <h2>为什么队列要<b>有界</b></h2>
 * {@code queueCapacity} 无界时，一个慢下游 + 大窗口会把请求堆成任务队列，
 * 队列里的任务还在<b>持有</b>已经过期的超时预算上下文，且没有任何背压。
 * 有界 + {@code CallerRunsPolicy}：池与队列都满时，<b>提交线程自己跑这个任务</b>
 * （即请求线程），把背压直接传回发起方 —— 效果是「请求变慢」，而不是「静默堆积」。
 *
 * <p><b>⇒ 64 线程 + 64 队列 = 128 个「已提交未完成」的任务位</b>
 * = 16 个并发请求 × 每请求 8 ⇒ **并发 ≤ 16 时 {@code CallerRunsPolicy} 不会触发**
 * ⇒ 背压形态 = <b>纯排队</b>（与 Go 同类；Go 侧永远不会有「请求线程自己跑补全」）。
 * 实测（U10）8 并发时下游在途峰值 = 64、<b>无 CallerRuns</b> ⇒ 结论成立。
 */
@Configuration(proxyBeanMethods = false)
public class HotHydrationExecutorConfiguration {

    /**
     * <b>8 个并发请求 × 每个请求 8 个在途补全</b>。
     *
     * <p>⚠️ 旧值 <b>32</b>（= 4 请求 × 8）会把 <b>8 并发</b>时的每请求窗口压到 4
     * ⇒ 实测 p99 1.76×（DRIFT-HOTAPI-028，第十轮 U10 活体对拍）。
     *
     * <p>⚠️ <b>不要再往下调</b>：池容量一旦小于「并发请求数 × 8」，
     * 就会把<b>每个请求自己的</b>窗口缩小 —— 那不是「限流」，是**偷偷改了单请求语义**。
     */
    private static final int POOL_SIZE = 64;

    /**
     * 允许的排队上限。{@code Semaphore(8)} 保证单请求在途 ≤ 8，
     * 所以队列里堆积的只会是<b>其它并发请求</b>的在途任务；
     * 取 64 ⇒ 池 + 队列共 128 个任务位 = 16 个并发请求 ⇒ **并发 ≤ 16 时不触发 CallerRuns**。
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
