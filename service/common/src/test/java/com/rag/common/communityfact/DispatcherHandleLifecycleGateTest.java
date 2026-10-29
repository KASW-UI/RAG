package com.rag.common.communityfact;

import io.opentelemetry.context.Context;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🔴🔴 <b>DRIFT-CF-018 守门</b>：<b>停止能力归调用方</b>（Go-faithful 形态）。
 *
 * <h2>它守的是什么缺陷</h2>
 * <p><b>DRIFT-CF-018</b>：{@code Dispatcher.run(once=false)} 是<b>阻塞式</b>的，
 * 而它返回的 {@link Dispatcher.DispatcherHandle} <b>只在循环退出后才交到调用方手上</b>
 * ⇒ ⇒ 阻塞形态的调用方<b>永远拿不到句柄</b> ⇒ ⇒ {@code @PreDestroy} 停不下来。
 *
 * <h2>Go 出处（为什么「句柄归调用方」才是 Go 的形状）</h2>
 * <ul>
 *   <li><b>被调方</b>：{@code service/common/communityfact/process.go:20}
 *       （{@code func RunDispatcher(ctx context.Context, …, once bool) error} —— <b>不返回句柄</b>）
 *       ＋ {@code process.go:56-60}（{@code select { case <-ctx.Done(): return nil; case <-ticker.C: }}）
 *       ⇒ 循环<b>唯一</b>的退出条件是 <b>ctx</b>；</li>
 *   <li><b>调用方</b>：{@code service/comment/rpc/cmd/fact-dispatch/main.go:68}
 *       （{@code ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)}）
 *       ＋ {@code :69}（{@code defer stop()}）
 *       ＋ {@code :77}（{@code return communityfact.RunDispatcher(ctx, …, once)}）
 *       ⇒ <b>停止能力在 dispatcher 被调用之前就已属于调用方</b>。</li>
 * </ul>
 * <p><b>语义等价论证</b>：Go 侧「停 dispatcher」= 让<b>调用方自己</b>的 ctx 变成 done
 * ⇒ 调用方可以在<b>任意线程、任意时刻</b>停它（signal handler 就是那个「任意时刻」）
 * ⇒ Java 侧对应物 = <b>调用方先 {@code Dispatcher.newHandle()} 造句柄</b>，再交给阻塞式 {@code run}。
 * ⇒ 🔴 <b>旧形态（run 返回句柄）在 Go 里<b>没有对应物</b></b> ⇒ 它是「无出处的 idiomatization」
 * （AGENTS.md §3.2）⇒ 在阻塞形态下<b>必然交不出句柄</b>。
 *
 * <h2>为什么这条测试<b>有牙齿</b>（不是假绿）</h2>
 * <p>核心用例 {@link #callerOwnedHandleStopsABlockingRunThatHasNotReturnedYet()} 断言的是
 * <b>「{@code run} 还在阻塞时，句柄就已经可用」</b> ⇒ ⇒ 若实现退回「句柄只在循环退出后交出」，
 * 本用例必然<b>超时失败</b>（阻塞的 run 永远不返回）⇒ ⇒ 判别力来自<b>真实线程时序</b>，不是断言措辞。
 * <p>另附 {@link #mutationGuardIsDiscriminating()}：它断言「stop 之前 run 线程<b>活着</b>、
 * stop 之后<b>已死</b>」⇒ ⇒ 排除「run 本来就没跑起来 ⇒ 绿得毫无意义」这种假绿。
 *
 * <h2>⚠️ 证据强度</h2>
 * <p>本门是<b>单测级（L1/E3）</b>：它断言的是 <b>Java 侧的并发时序契约</b>，
 * <b>不是</b> Go↔RAG 双侧活体对拍（AGENTS.md §3.3 要求「凡写『与 Go 一致』必须有两侧同时跑的探针」）
 * ⇒ ⇒ 本门<b>不</b>声称「与 Go 行为一致」，只声称「<b>Java 侧已具备 Go 那种『调用方随时可停』的能力</b>」
 * ⇒ ⇒ Go 侧那一半的依据是<b>源码逐行对照</b>（上面的出处），<b>不是</b>运行期证据。
 */
class DispatcherHandleLifecycleGateTest {

    private static final Logger LOG = LoggerFactory.getLogger(DispatcherHandleLifecycleGateTest.class);

    /** Go 侧 {@code process.go:56-60} 的 1s 栅格 ⇒ 留足余量但不允许「永远不返回」。 */
    private static final long STOP_TIMEOUT_MS = 8_000L;

    private static Wire.Sender noopSender() {
        return (ctx, event, raw) -> new Wire.TechnicalReceipt();
    }

    /**
     * 🔴 核心用例：<b>阻塞中的 {@code run} 必须能被调用方的句柄停掉</b>。
     *
     * <p>这正是 DRIFT-CF-018 的<b>可观察判据</b>：旧形态下句柄在循环退出前<b>不可用</b>
     * ⇒ 调用方无从触发停止 ⇒ 阻塞的 run 永不返回。
     */
    @Test
    @DisplayName("DRIFT-CF-018：调用方持有的句柄能在阻塞的 run 返回之前把它停掉（Go：main.go:68 的 ctx/stop 先于 RunDispatcher）")
    void callerOwnedHandleStopsABlockingRunThatHasNotReturnedYet() throws Exception {
        Dispatcher.DispatcherHandle handle = Dispatcher.newHandle();
        CountDownLatch dispatched = new CountDownLatch(1);
        AtomicBoolean loopStillRunning = new AtomicBoolean(true);

        Dispatcher.DispatcherImpl impl = (ctx, sender, logger) -> {
            dispatched.countDown();
            // 返回 false ⇒ 队列空 ⇒ 内层 16 次批次循环在第一次就 break（Go 侧 sent=false 同样 break）
            return false;
        };

        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread runner = new Thread(() -> {
            try {
                Dispatcher.run(Context.current(), impl, noopSender(), LOG, false, handle, false);
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                loopStillRunning.set(false);
            }
        }, "cf-018-runner");
        runner.setDaemon(true);
        runner.start();

        try {
            // 先确认循环**真的跑起来了**（否则「停掉了」是假绿）
            assertThat(dispatched.await(STOP_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                    .as("投递循环必须真的跑起来（否则本用例的『停掉了』没有意义）")
                    .isTrue();

            // 🔴 判据：run 还没返回，但调用方手里的句柄**已经可用**
            assertThat(runner.isAlive()).as("run 必须仍在阻塞（once=false 的语义）").isTrue();
            assertThat(loopStillRunning.get()).as("run 尚未返回").isTrue();

            handle.stop();

            runner.join(STOP_TIMEOUT_MS);
            assertThat(runner.isAlive())
                    .as("调用方调 handle.stop() 之后，阻塞的 run 必须在 " + STOP_TIMEOUT_MS + "ms 内退出"
                            + "（旧形态下句柄不可用 ⇒ run 永不返回 ⇒ 这条会红）")
                    .isFalse();
            assertThat(failure.get()).as("run 不应抛异常").isNull();
        } finally {
            handle.stop();
            runner.join(STOP_TIMEOUT_MS);
        }
    }

    /**
     * Go 等价语义：ctx 在调用前就已取消 ⇒ {@code select} 立即命中 {@code ctx.Done()}
     * ⇒ {@code RunDispatcher} 立刻返回（{@code process.go:57-58}）。
     *
     * <p>Java 侧对应物：调用方在 {@code run} <b>启动前</b>就 {@code stop()} ⇒ 停止信号已置位
     * ⇒ 循环在第一个栅格处立即退出。
     */
    @Test
    @DisplayName("DRIFT-CF-018：run 启动前就 stop() ⇒ run 必须立刻返回（Go：ctx 预先取消 ⇒ select 立即命中 ctx.Done()）")
    void stopBeforeRunStartsMakesRunReturnPromptly() throws Exception {
        Dispatcher.DispatcherHandle handle = Dispatcher.newHandle();
        handle.stop();

        AtomicReference<Dispatcher.DispatcherHandle> returned = new AtomicReference<>();
        Thread runner = new Thread(() -> {
            returned.set(Dispatcher.run(Context.current(),
                    (ctx, sender, logger) -> false, noopSender(), LOG, false, handle, false));
        }, "cf-018-pre-cancelled");
        runner.setDaemon(true);
        runner.start();

        runner.join(STOP_TIMEOUT_MS);
        assertThat(runner.isAlive()).as("预先 stop() 的 run 必须立刻返回").isFalse();
        assertThat(returned.get()).as("run 必须返回句柄").isNotNull();
        assertThat(returned.get()).as("run 必须返回调用方持有的那一个句柄（不得另造）").isSameAs(handle);
    }

    /**
     * 🔴 <b>防回退的结构门</b>：旧的「阻塞式 run 返回句柄」重载必须一直带着 {@code @Deprecated}。
     *
     * <p>为什么需要它：那两个重载在 {@code once=false} 时<b>必然交不出句柄</b>
     * ⇒ ⇒ 它们必须被标记 ⇒ ⇒ 任何新写的调用点都会看到弃用告警 ⇒ ⇒ 不会「悄无声息地」再写一个。
     */
    @Test
    @DisplayName("DRIFT-CF-018 防回退：旧的阻塞式 run 重载必须带 @Deprecated（它们在 once=false 时交不出句柄）")
    void legacyBlockingOverloadsAreDeprecated() throws Exception {
        Method fiveArg = Dispatcher.class.getMethod("run", Context.class, Dispatcher.DispatcherImpl.class,
                Wire.Sender.class, Logger.class, boolean.class);
        Method sixArg = Dispatcher.class.getMethod("run", Context.class, Dispatcher.DispatcherImpl.class,
                Wire.Sender.class, Logger.class, boolean.class, boolean.class);

        assertThat(fiveArg.getAnnotation(Deprecated.class))
                .as("5 参阻塞重载必须标记 @Deprecated（DRIFT-CF-018）").isNotNull();
        assertThat(sixArg.getAnnotation(Deprecated.class))
                .as("6 参阻塞重载必须标记 @Deprecated（DRIFT-CF-018）").isNotNull();
    }

    /** 幂等：{@code stop()} 重复调用安全（Go 侧 cancel 也是幂等的）。 */
    @Test
    @DisplayName("DRIFT-CF-018：句柄的 stop() 必须幂等（Go：cancel() 可重复调用）")
    void handleStopIsIdempotent() {
        Dispatcher.DispatcherHandle handle = Dispatcher.newHandle();
        handle.stop();
        handle.stop();
        handle.stop();
        assertThat(handle).as("重复 stop 不得抛异常").isNotNull();
    }

    /**
     * 🔴 <b>变异守卫</b>：证明核心用例<b>不是假绿</b>。
     *
     * <p>它断言两件事：① 在 {@code stop()} 之前，{@code run} 线程<b>确实还活着</b>
     * （⇒ 循环真的在阻塞，而不是「压根没跑起来所以一停就结束」）；
     * ② {@code stop()} 之后，它<b>确实死了</b>（⇒ 停止动作真的生效了）。
     * ⇒ ⇒ 若把实现改回「句柄只在循环退出后交出」，核心用例会因超时而红，而本守卫会因 ① 而红。
     */
    @Test
    @DisplayName("DRIFT-CF-018 变异守卫：stop 之前 run 线程活着、stop 之后已死（排除假绿）")
    void mutationGuardIsDiscriminating() throws Exception {
        Dispatcher.DispatcherHandle handle = Dispatcher.newHandle();
        CountDownLatch dispatched = new CountDownLatch(1);

        Thread runner = new Thread(() -> Dispatcher.run(Context.current(),
                (ctx, sender, logger) -> {
                    dispatched.countDown();
                    return false;
                }, noopSender(), LOG, false, handle, false), "cf-018-mutation-guard");
        runner.setDaemon(true);
        runner.start();

        try {
            assertThat(dispatched.await(STOP_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                    .as("循环必须真的跑起来").isTrue();
            assertThat(runner.isAlive())
                    .as("① stop 之前 run 必须仍在阻塞（否则核心用例的『停掉了』是假绿）")
                    .isTrue();

            handle.stop();
            runner.join(STOP_TIMEOUT_MS);
            assertThat(runner.isAlive())
                    .as("② stop 之后 run 必须已退出（否则停止动作没生效）")
                    .isFalse();
        } finally {
            handle.stop();
            runner.join(STOP_TIMEOUT_MS);
        }
    }
}
