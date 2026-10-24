package com.rag.hot.hot.rpc.mq;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.rag.hot.hot.rpc.config.HotRpcProperties;
import com.rag.hot.hot.rpc.domain.HeavyKeeper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@code partitionId → HeavyKeeper} 的分区注册表 —— 对应 Go {@code hkMap} 及其分区生命周期
 * （{@code hot_handler.go:51-52} 字段；{@code OnPartitionsAssigned:113-126}、
 * {@code OnPartitionsRevoked:128-140}）。
 *
 * <p>不变量：<b>每个当前持有的分区恰好一个 sketch</b>；重复 assign 不回灌、revoke 后不再持有。
 *
 * <h2>⚠️ 与 Go 的一处有意偏离：撤销路径不做「持写锁再取读锁」</h2>
 * Go 的撤销路径是：
 * <pre>{@code
 * func (h *HotHandler) OnPartitionsRevoked(partitions []int32) {
 *     h.hkMu.Lock()            // hot_handler.go:132  持写锁
 *     defer h.hkMu.Unlock()
 *     h.syncToRedis(ctx, pid)  // hot_handler.go:136  → :196 h.hkMu.RLock()  ← 死锁
 *     delete(h.hkMap, pid)
 * }
 * }</pre>
 * Go 的 {@code sync.RWMutex} <b>不可重入</b>：持写锁时 {@code readerCount} 为负，
 * 此时同 goroutine 调 {@code RLock} 会永久阻塞；{@code defer Unlock} 因此永不执行。
 * ⇒ <b>Go 侧第一次 rebalance 撤销分区就必然死锁</b>，{@code delete} 永不执行，
 * 分区快照<b>从未真正落盘过</b>（已用最小 Go 程序复现验证）。
 * 对照：{@code OnPartitionsAssigned} 持写锁调用的 {@code loadFromRedis} <b>不取任何锁</b>，
 * 所以只有撤销这一侧有问题。
 *
 * <p><b>为什么不能「照抄」这个行为</b>：Java 的 {@link ReentrantReadWriteLock} <b>允许</b>
 * 写锁持有者再取读锁（官方支持的 write→read 降级）。若按 §12.5 的规划直接 1:1 直译，
 * Go 的死锁会<b>悄悄变成能正常跑</b>，两套实现行为不一致且没有任何告警。
 * 而刻意在 Java 里写死锁，既不可能也让 L2 语义（rebalance 落盘）彻底落空。
 * ⇒ 本实现采用 Go 另外两个调用点（{@code periodicSync:250-255}、{@code consumeMessage:170-172}）
 * 已经用过的正确形态：<b>锁内取引用 → 锁外做 Redis I/O</b>。
 *
 * <p>具体做法：{@link #detach(Collection)} 在写锁内把 sketch <b>原子移除并返回引用</b>，
 * 由调用方在<b>锁外</b>落盘。这样既消除了自死锁，也顺带保证「撤销后不再有新事件进入该 sketch」。
 *
 * <h2>另一处收敛（Go 既有形态）</h2>
 * Go 的 {@code OnPartitionsAssigned} 在<b>持写锁期间</b>做 {@code loadFromRedis} 的 Redis I/O，
 * 会阻塞所有 {@code RLock} 读者。本实现的 {@link #assign(Collection)} 只在锁内建 sketch，
 * 回灌由调用方在锁外完成 —— 这是 Go 另外两个调用点已有的形态，<b>不改变业务可观察结果</b>。
 */
@Component
public class HotPartitionSketchRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(HotPartitionSketchRegistry.class);

    private final Map<Integer, HeavyKeeper> hkMap = new LinkedHashMap<>();

    /** 对应 Go 的 {@code hkMu sync.RWMutex}。 */
    private final ReentrantReadWriteLock hkMu = new ReentrantReadWriteLock();

    private final HotRpcProperties props;

    public HotPartitionSketchRegistry(HotRpcProperties props) {
        this.props = props;
    }

    /** 按 Go {@code etc/hot*.yaml} 的 {@code HeavyKeeper} 四参数新建 sketch。 */
    private HeavyKeeper newHeavyKeeper() {
        HotRpcProperties.HeavyKeeper cfg = props.getHeavyKeeper();
        return new HeavyKeeper(cfg.getWidth(), cfg.getDepth(), cfg.getDecay(), cfg.getTopK());
    }

    /**
     * 对应 Go {@code OnPartitionsAssigned} 的「建 sketch」部分（{@code hot_handler.go:113-126}）。
     *
     * <p>返回<b>本次新建</b>的 {@code partitionId → sketch}，供调用方在锁外做
     * {@link HotRedisSnapshotStore#loadInto} 回灌。已存在的分区被跳过（Go {@code :118-120} 的
     * {@code continue}，防止重复回灌）。
     *
     * <p>注意顺序与 Go 一致：<b>先注册进 map，再回灌</b> ⇒ 回灌期间到达的事件仍会进同一个 sketch。
     */
    public Map<Integer, HeavyKeeper> assign(Collection<Integer> partitions) {
        Map<Integer, HeavyKeeper> created = new LinkedHashMap<>();
        hkMu.writeLock().lock();
        try {
            for (Integer pid : partitions) {
                if (hkMap.containsKey(pid)) {
                    continue;
                }
                HeavyKeeper hk = newHeavyKeeper();
                hkMap.put(pid, hk);
                created.put(pid, hk);
                LOG.info("[Hot] assigned partition {}", pid);
            }
        } finally {
            hkMu.writeLock().unlock();
        }
        return created;
    }

    /**
     * 对应 Go {@code OnPartitionsRevoked} 的「落盘 + 丢弃」部分（{@code hot_handler.go:128-140}）。
     *
     * <p><b>写锁内只做「取出引用 + 从 map 移除」，落盘由调用方在锁外完成</b>
     * —— 理由与偏差论证见类注释。
     *
     * @return 被摘下的 {@code partitionId → sketch}，调用方须在锁外依次落盘
     */
    public Map<Integer, HeavyKeeper> detach(Collection<Integer> partitions) {
        Map<Integer, HeavyKeeper> detached = new LinkedHashMap<>();
        hkMu.writeLock().lock();
        try {
            for (Integer pid : partitions) {
                HeavyKeeper hk = hkMap.remove(pid);
                if (hk != null) {
                    detached.put(pid, hk);
                }
                LOG.info("[Hot] revoked partition {}", pid);
            }
        } finally {
            hkMu.writeLock().unlock();
        }
        return detached;
    }

    /**
     * 取分区 sketch，对应 Go 消费路径 {@code hkMu.RLock(); hk := hkMap[pid]; hkMu.RUnlock()}
     * （{@code hot_handler.go:170-172}）。
     *
     * @return sketch 引用；分区不在 map 时返回 {@code null}（Go 侧即「静默 return nil」）
     */
    public Map<Integer, HeavyKeeper> detachWithinWriteLock(Collection<Integer> partitions,
                                                          HotRedisSnapshotStore store) {
        Map<Integer, HeavyKeeper> detached = new LinkedHashMap<>();
        hkMu.writeLock().lock();
        try {
            for (Integer pid : partitions) {
                HeavyKeeper hk = hkMap.remove(pid);
                if (hk != null) {
                    detached.put(pid, hk);
                    store.flush(pid, hk);
                }
            }
        } finally {
            hkMu.writeLock().unlock();
        }
        return detached;
    }

    public HeavyKeeper heavyKeeperFor(int partitionId) {
        hkMu.readLock().lock();
        try {
            return hkMap.get(partitionId);
        } finally {
            hkMu.readLock().unlock();
        }
    }

    /** 当前持有的分区快照，对应 Go {@code periodicSync:250-255} 的取 key 循环。 */
    public List<Integer> partitionIds() {
        hkMu.readLock().lock();
        try {
            return new ArrayList<>(hkMap.keySet());
        } finally {
            hkMu.readLock().unlock();
        }
    }

    /** 当前持有的分区数（测试观测用）。 */
    public int size() {
        hkMu.readLock().lock();
        try {
            return hkMap.size();
        } finally {
            hkMu.readLock().unlock();
        }
    }
}