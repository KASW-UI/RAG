package com.rag.task.task.rpc.mq;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * goka {@code Persist} 的等价物 —— 对应 Go 端两个 processor 里的
 * {@code goka.Persist(new(codec.Int64))} 与 {@code ctx.Value()} / {@code ctx.SetValue()}。
 *
 * <h2>Go 侧的三行就是全部语义</h2>
 * <pre>
 * // initGoKa.go:75-81（用户计数）
 * var cur int64
 * if v := ctx.Value(); v != nil { cur = v.(int64) }
 * cur++
 * ctx.SetValue(cur)
 * ctx.Emit(goka.Stream(outTopicTaskUser), ctx.Key(), cur)
 * </pre>
 *
 * <p>⇒ 它是一个<b>按 Kafka key 累加的本地计数器</b>：
 * <ul>
 *   <li>key 首次出现 ⇒ 存量为 nil ⇒ 从 0 起算，{@code cur++} 后为 <b>1</b>；</li>
 *   <li>key 已存在 ⇒ 从上次 emit 出去的<b>那个值</b>继续 +1；</li>
 *   <li>值不是全局共享的 —— 它是<b>本进程本地</b>状态，不在 Redis 里、也不在 PG 里。</li>
 * </ul>
 *
 * <h2>⚠️ 为什么用内存 Map 而不是 Redis（这是 L4，实现机制）</h2>
 * <p>goka 的默认 store 会把 KV 表<b>持久化到本地文件</b>，目的是进程重启后不重复累加。
 * Java 侧改用进程内 {@link ConcurrentHashMap}，理由：
 * <ul>
 *   <li>goka 的落盘是<b>单机恢复优化</b>，不影响消息本身的处理语义；</li>
 *   <li>放进 Redis 会让「每条消息一次网络往返」，而这条链路的 QPS 是点赞量级 —— 成本不对等；</li>
 *   <li>更重要的是：<b>重启后从 0 重新累加，与 goka 从文件恢复相比只是重复计数</b>，
 *       而下游（两个 sink）用的是 {@code max(旧值, 新值)} 与 SETNX 完成幂等，
 *       重复计数<b>不会</b>造成业务错误。</li>
 * </ul>
 * ⇒ 这是可接受的行为差异，但它<b>不是</b>严格等价：重启后 emit 出去的计数会从 1 重新开始。
 * 若将来要求"跨重启精确计数"，需要改成本地文件 store（RocksDB / MapDB）而非 Redis。
 *
 * <h2>⚠️ 线程安全：为什么用 compute 而非 get/put</h2>
 * <p>Go 侧 goka 保证<b>同一个 group 内同一 key 的处理是串行的</b>（单分区单消费者）。
 * Spring Kafka 默认 {@code concurrency=1}，同样是单线程消费 ⇒ 语义一致。
 * 但 {@link #increment} 仍用 {@code compute}，因为
 * {@code @Scheduled} 之外将来可能引入并发路径，且「读-改-写」非原子是典型的竞态写法。
 */
@Component
public class TaskCountStore {

    /** 每个消费组各持有一份：Go 侧三个 group 是三个独立的 goka processor，存储互不共享。 */
    private final Map<String, Long> counters = new ConcurrentHashMap<>();

    /**
     * 对应 Go {@code ctx.Value()} + {@code cur++} + {@code ctx.SetValue(cur)}。
     *
     * @param key Kafka 消息的 key（Go 的 {@code ctx.Key()}）
     * @return 累加后的值；key 首次出现时返回 1
     */
    public long increment(String key) {
        return counters.compute(key, (k, current) -> current == null ? 1L : current + 1L);
    }

    /** 当前存量，仅用于诊断与测试。 */
    public long current(String key) {
        return counters.getOrDefault(key, 0L);
    }

    /** 条目数，仅用于诊断与测试。 */
    public int size() {
        return counters.size();
    }
}