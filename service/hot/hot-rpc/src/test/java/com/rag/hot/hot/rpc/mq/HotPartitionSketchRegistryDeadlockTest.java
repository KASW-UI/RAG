package com.rag.hot.hot.rpc.mq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.rag.hot.hot.rpc.config.HotRpcProperties;
import com.rag.hot.hot.rpc.domain.HeavyKeeper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * 锁死分区生命周期，并作为 <b>Go 侧死锁的回归守门</b>。
 *
 * <h2>为什么要有这条测试</h2>
 * Go {@code hot_handler.go:132} 持 {@code hkMu.Lock()} 后在 {@code :136} 调
 * {@code syncToRedis}，后者在 {@code :196} 又取 {@code hkMu.RLock()} ——
 * {@code sync.RWMutex} 不可重入 ⇒ <b>Go 侧必然死锁</b>（已用最小 Go 程序复现）。
 *
 * <p>Java 的 {@code ReentrantReadWriteLock} <b>允许</b>写锁持有者再取读锁，
 * 所以 1:1 直译<b>不会</b>死锁 —— 也就是说「照抄结构」这个动作本身就会把 Go 的死锁悄悄抹掉。
 * 本测试锁住「撤销路径必须在有限时间内完成」这个契约：
 * 若有人日后把 Redis 落盘重新挪回锁内（模仿 Go 结构），本用例会超时失败。
 */
class HotPartitionSketchRegistryDeadlockTest {

    private HotRpcProperties props;
    private HotPartitionSketchRegistry registry;

    @BeforeEach
    void setUp() {
        props = new HotRpcProperties();
        registry = new HotPartitionSketchRegistry(props);
    }

    @Test
    @Timeout(10)
    @DisplayName("assign → detach 全程不死锁，且 detach 原子摘除分区")
    void assignThenDetachDoesNotDeadlock() {
        registry.assign(List.of(0, 1, 2));
        assertThat(registry.size()).isEqualTo(3);

        Map<Integer, HeavyKeeper> detached = registry.detach(List.of(1));

        assertThat(detached).containsOnlyKeys(1);
        assertThat(registry.size()).isEqualTo(2);
        assertThat(registry.heavyKeeperFor(1)).isNull();
    }

    @Test
    @Timeout(10)
    @DisplayName("⚠️ 在持写锁的同时做「取 sketch 引用」不会像 Go 那样永久阻塞")
    void heavyKeeperLookupInsideDetachFlowDoesNotBlock() {
        registry.assign(List.of(0));

        AtomicBoolean acquired = new AtomicBoolean(false);
        CountDownLatch done = new CountDownLatch(1);

        // 模拟 Go 的 OnPartitionsRevoked 形态：撤销过程中同时要拿到 sketch 去落盘
        Thread worker = new Thread(() -> {
            Map<Integer, HeavyKeeper> detached = registry.detach(List.of(0));
            acquired.set(detached.containsKey(0));
            done.countDown();
        });
        worker.start();

        assertThatCode(() -> {
            if (!done.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("detach 未在 5s 内完成 —— 疑似引入 Go 侧的锁升级死锁");
            }
        }).doesNotThrowAnyException();
        assertThat(acquired).isTrue();

        // 撤销之后所有读者仍能立即拿到锁（Go 侧此刻会被永久卡死）
        assertThat(registry.heavyKeeperFor(0)).isNull();
        assertThat(registry.partitionIds()).isEmpty();
    }

    @Test
    @Timeout(10)
    @DisplayName("detach 不持有写锁：并发读者不会被阻塞（落盘发生在锁外）")
    void readersAreNotBlockedAfterDetach() {
        registry.assign(List.of(0, 1));

        registry.detach(List.of(0));

        // 反复读锁不应超时 —— 若 detach 期间仍持写锁，这里会卡住
        long start = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            registry.partitionIds();
        }
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
        assertThat(elapsed).isLessThan(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("assign 幂等：已存在的分区不会被重建（防止重复回灌）")
    void assignIsIdempotentPerPartition() {
        Map<Integer, HeavyKeeper> first = registry.assign(List.of(0, 1));
        assertThat(first).containsOnlyKeys(0, 1);

        Map<Integer, HeavyKeeper> second = registry.assign(List.of(0, 1));
        assertThat(second).as("已存在的分区不应重新建 sketch").isEmpty();
        assertThat(registry.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("detach 不存在的分区是安全的空操作（对应 Go 的 delete 语义）")
    void detachUnknownPartitionIsNoop() {
        registry.assign(List.of(0));

        assertThat(registry.detach(List.of(99))).isEmpty();
        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("sketch 按 yaml 的 HeavyKeeper 四参数创建（width/depth/decay/topK）")
    void newSketchUsesConfiguredParameters() {
        props.getHeavyKeeper().setWidth(64);
        props.getHeavyKeeper().setDepth(3);
        props.getHeavyKeeper().setDecay(0.5);
        props.getHeavyKeeper().setTopK(7);

        Map<Integer, HeavyKeeper> created = registry.assign(List.of(0));

        HeavyKeeper hk = created.get(0);
        assertThat(hk).isNotNull();
        // topK=7 ⇒ 最多保留 7 条
        for (int i = 0; i < 20; i++) {
            hk.add("k" + i, 1);
        }
        assertThat(hk.topKSize()).isLessThanOrEqualTo(7);
    }
}