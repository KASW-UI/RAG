package com.rag.common.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 守门：共享层的三个<b>进程级静态池</b>在 {@code shutdown()} 之后必须<b>按需自愈</b>。
 *
 * <h2>为什么需要这条</h2>
 *
 * <p>这三个池是 {@code static final} 的<b>进程级单例</b>，而 {@code shutdown()} 会<b>永久终止</b>它们
 * ⇒ ⇒ <b>同一个 JVM 里只要存在第二个 Spring 上下文</b>（surefire「一个模块一个 JVM」⇒ 模块内多个上下文），
 * <b>前一个上下文关闭后</b>这三个池就变成「已终止」
 * ⇒ 后续任何依赖它们的请求都会被拒：
 * <ul>
 *   <li>{@code TIMEOUT_SCHEDULER.schedule(...)} 被拒 ⇒ 超时任务<b>永远不触发</b>
 *       ⇒ 拦截器返回 {@code INTERNAL} 而不是 {@code DEADLINE_EXCEEDED}；</li>
 *   <li>{@code HANDLE_EXECUTOR.submit(...)} 被拒 ⇒ <b>handler 根本没被调用</b>。</li>
 * </ul>
 *
 * <h2>实测症状与归因</h2>
 *
 * <p>🔴 这条缺陷是<b>修 G1 派单 #8</b>（关闭路径不得触发 web 类型的类加载）时<b>暴露</b>出来的：
 * 修之前，纯 gRPC 进程的 {@code @PreDestroy} <b>先抛 {@code NoClassDefFoundError}</b>
 * ⇒ {@code RpcInterceptor.shutdown()} <b>根本没机会执行</b> ⇒ 静态池<b>侥幸存活</b>
 * ⇒ 全量回归一直是绿的 ⇒ <b>那不是「本来就对」，是「缺陷把保护罩挡住了」</b>。
 * 修好 #8 之后，{@code shutdown()} 真的执行了 ⇒ 保护罩没了 ⇒ 这条潜伏缺陷显形：
 * <pre>
 *   ArticleGrpcInterceptorWiringTest（article 域写权）
 *     serverSideTimeoutIsActuallyEnforced
 *       ⇒ actual status=INTERNAL description=panic: Task ... rejected from
 *         ScheduledThreadPoolExecutor[Terminated, pool size = 0, ...]
 *     deadlineIsPropagatedToHandlerThread
 *       ⇒ handler 根本没被调用（线程 = &lt;未进入&gt;）
 *   ⇒ 隔离跑却绿（全绿 3/3）⇒ 典型「跨测试污染」
 * </pre>
 *
 * <h2>修法与它的边界</h2>
 *
 * <p><b>池被终止后，下一次使用按需重建</b>（{@code AtomicReference} + CAS 自旋）。
 * ⇒ <b>生产</b>：单上下文 ⇒ shutdown 之后没有下一次使用 ⇒ <b>池保持关闭</b> ⇒ 关闭钩子的意图不变；
 * <b>测试 / 多上下文</b>：下一个上下文用到时自动重建 ⇒ 不再被毒化。
 * ⚠️ 线程是 <b>daemon</b> ⇒ 即便重建也不阻止 JVM 退出。
 */
@DisplayName("共享层静态池：shutdown() 之后按需自愈（多 Spring 上下文不被毒化）")
class ObservationPoolSelfHealingGateTest {

    @Test
    @DisplayName("① shutdown() 必须<b>真的终止</b>当时的池（关闭钩子的意图不被稀释）")
    void shutdownTerminatesThePools() {
        ScheduledExecutorService scheduler = RpcInterceptor.timeoutScheduler();
        ExecutorService worker = RpcInterceptor.handleExecutor();

        RpcInterceptor.shutdown();

        assertThat(scheduler.isShutdown())
                .as("shutdown() 必须终止超时调度器（否则关闭钩子形同虚设）")
                .isTrue();
        assertThat(worker.isShutdown())
                .as("shutdown() 必须终止 worker 池")
                .isTrue();
    }

    @Test
    @DisplayName("② 终止后再次使用 ⇒ 池<b>按需重建</b>且真的可用（不再是 Terminated）")
    void poolsAreRecreatedOnDemandAfterShutdown() throws Exception {
        RpcInterceptor.shutdown();

        ScheduledExecutorService scheduler = RpcInterceptor.timeoutScheduler();
        assertThat(scheduler.isShutdown())
                .as("再次取用时必须拿到一份<b>新的</b>池（不是被终止的那份）")
                .isFalse();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean ran = new AtomicBoolean(false);
        scheduler.schedule(() -> {
            ran.set(true);
            latch.countDown();
        }, 10L, TimeUnit.MILLISECONDS);

        assertThat(latch.await(5, TimeUnit.SECONDS))
                .as("重建后的调度器必须真的能调度任务（否则等于没修）")
                .isTrue();
        assertThat(ran.get()).isTrue();

        ExecutorService worker = RpcInterceptor.handleExecutor();
        assertThat(worker.isShutdown())
                .as("worker 池同理：终止后按需重建")
                .isFalse();
        CountDownLatch workerLatch = new CountDownLatch(1);
        worker.execute(workerLatch::countDown);
        assertThat(workerLatch.await(5, TimeUnit.SECONDS))
                .as("重建后的 worker 池必须真的能提交任务")
                .isTrue();
    }

    @Test
    @DisplayName("③ HTTP 侧 worker 池<b>同理</b>（与上面两个池同款理由）")
    void httpPoolIsRecreatedOnDemandAfterShutdown() throws Exception {
        HttpMiddleware.shutdown();
        assertThat(HttpMiddleware.httpExecutor().isShutdown())
                .as("HTTP 池同样必须按需重建")
                .isFalse();

        CountDownLatch latch = new CountDownLatch(1);
        HttpMiddleware.httpExecutor().execute(latch::countDown);
        assertThat(latch.await(5, TimeUnit.SECONDS))
                .as("重建后的 HTTP 池必须真的能提交任务")
                .isTrue();
    }
}
