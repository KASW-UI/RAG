package com.rag.hot.hot.rpc.mq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.hot.hot.rpc.config.HotRpcProperties;
import com.rag.hot.hot.rpc.domain.HeavyKeeper;
import com.rag.hot.hot.rpc.metrics.HotRpcMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * 锁死 <b>「撤销时的 Redis 落盘发生在写锁之外」</b>这一约束。
 *
 * <h2>为什么这是独立的一条测试</h2>
 * Go {@code hot_handler.go:132-137} 的形态是「持写锁 → 调 {@code syncToRedis}（内含 {@code RLock}）」
 * ⇒ <b>Go 侧必然自死锁</b>（{@code sync.RWMutex} 不可重入，已用最小 Go 程序复现）。
 *
 * <p>而 Java 的 {@code ReentrantReadWriteLock} <b>允许</b>写锁持有者再取读锁，
 * 所以「把 Redis 落盘挪回锁内」这个 Go 形态<b>不会</b>产生死锁 ——
 * 也就是说，<b>无法</b>用「会不会卡住」来判定它是否发生了。</p>
 *
 * <p>真正可观察的差别是 <b>锁的持有时长</b>：若落盘在写锁内，
 * Redis 一慢就会把<b>所有读者</b>（消费路径 {@code consumeMessage} 的
 * {@code hkMu.RLock()}、{@code periodicSync} 的取分区循环）一起阻塞。
 * 本测试用一个被 latch 卡住的 {@code flush} 制造「慢 Redis」，
 * 断言读者<b>仍能立即返回</b>。落盘一旦挪回锁内，本用例会超时失败。
 */
class HotRevokeFlushOutsideLockTest {

    private static final String TOPIC = "article-hot-events";

    @Test
    @Timeout(20)
    @DisplayName("flush 阻塞期间读锁仍然可用 ⇒ 落盘确实在写锁之外")
    void readersAreNotBlockedWhileRevokeFlushes() throws Exception {
        HotRpcProperties props = new HotRpcProperties();
        props.getKafka().setTopic(TOPIC);
        HotRpcMetrics metrics = new HotRpcMetrics(new SimpleMeterRegistry());
        HotPartitionSketchRegistry registry = new HotPartitionSketchRegistry(props);

        CountDownLatch flushStarted = new CountDownLatch(1);
        CountDownLatch releaseFlush = new CountDownLatch(1);

        // 模拟「慢 Redis」：flush 阻塞在 latch 上
        HotRedisSnapshotStore slowStore =
                org.mockito.Mockito.mock(HotRedisSnapshotStore.class);
        doAnswer(inv -> {
            flushStarted.countDown();
            releaseFlush.await(10, TimeUnit.SECONDS);
            return null;
        }).when(slowStore).flush(anyInt(), any(HeavyKeeper.class));

        HotEventConsumer consumer = new HotEventConsumer(
                new ObjectMapper(), props, metrics, registry, slowStore, Runnable::run);
        consumer.onPartitionsAssigned(List.of(0));
        assertThat(registry.size()).isEqualTo(1);

        Thread revoke = new Thread(() -> consumer.onPartitionsRevoked(List.of(0)));
        revoke.start();

        assertThat(flushStarted.await(10, TimeUnit.SECONDS))
                .as("撤销线程应已进入 flush").isTrue();

        // flush 仍被卡住 —— 此时若写锁未释放，所有读锁调用都会阻塞
        long startNanos = System.nanoTime();
        List<Integer> ids = registry.partitionIds();
        HeavyKeeper hk = registry.heavyKeeperFor(0);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);

        assertThat(elapsed)
                .as("落盘在写锁内 ⇒ 读者被 Redis 延迟阻塞")
                .isLessThan(Duration.ofSeconds(2));
        assertThat(ids).isEmpty();
        assertThat(hk).isNull();

        releaseFlush.countDown();
        revoke.join(TimeUnit.SECONDS.toMillis(10));
        assertThat(revoke.isAlive()).isFalse();
    }
}