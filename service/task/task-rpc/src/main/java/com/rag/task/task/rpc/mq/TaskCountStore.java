package com.rag.task.task.rpc.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * goka {@code Persist} 的等价物 —— 对应 Go 端两个 processor 里的
 * {@code goka.Persist(new(codec.Int64))} 与 {@code ctx.Value()} / {@code ctx.SetValue()}。
 *
 * <h2>Go 侧的三行就是全部语义</h2>
 * <pre>
 * // initGoKa.go:75-81（用户计数；文章计数 :92-97 同型）
 * var cur int64
 * if v := ctx.Value(); v != nil { cur = v.(int64) }
 * cur++
 * ctx.SetValue(cur)
 * ctx.Emit(goka.Stream(outTopicTaskUser), ctx.Key(), cur)
 * </pre>
 *
 * <p>⇒ 它是一个<b>按 Kafka key 累加的计数器</b>：key 首次出现 ⇒ 存量为 nil ⇒ 从 0 起算，
 * {@code cur++} 后为 <b>1</b>；key 已存在 ⇒ 从上次 emit 出去的那个值继续 +1。
 *
 * <h2>🔴🔴 2026-10-23 修正：本类此前是「内存单例」，有两处与 Go 不等价</h2>
 *
 * <h3>① 每个 group 必须各持一份存储（此前两个 group 共用了一张表）</h3>
 * <p>Go 侧 <b>两个 goka processor 是两次独立的 {@code goka.NewProcessor}</b>
 * （{@code initGoKa.go:178-181} 用户路、{@code :212-215} 文章路），
 * 各自拥有独立的 store。goka 的 store 路径就<b>按 group 名分目录</b>：
 * <pre>
 * // goka@v1.1.16 processor.go:82
 * WithStorageBuilder(storage.DefaultBuilder(DefaultProcessorStoragePath(gg.Group())))
 * // goka@v1.1.16 storage/builders.go:8-17 ⇒ LevelDB，文件名 = &lt;path&gt;/&lt;topic&gt;.&lt;partition&gt;
 * </pre>
 * <p>此前本类是<b>单个 {@code @Component} + 单张 {@code ConcurrentHashMap}</b>，
 * 被 {@link TaskUserCountConsumer} 与 {@link TaskArticleCountConsumer} 共用。
 * ⇒ <b>用户路的 key 是 {@code user_id}、文章路的 key 是 {@code article_id}，
 * 两条链路的 id 空间会相交</b>：一旦出现 {@code user_id == "190"} 且
 * {@code article_id == "190"} 的两条消息，两路的计数就<b>互相污染</b>，
 * 后续 emit 出去的 {@code cur} 全错。
 * <p>现在按 <b>group id</b> 命名空间隔离（{@link #increment(String, String)} 的第一个参数），
 * 与 goka 的「按 group 分目录」一一对应。
 *
 * <h3>② 存储必须跨重启存活（此前是纯进程内存）</h3>
 * <p>Go 用的是 <b>LevelDB 落盘</b>，且该 table 本身还是一个 Kafka compacted topic
 * （{@code <group>-table}），新实例可以从表里重建状态。
 * ⇒ <b>进程重启后计数从上次 emit 的值继续</b>，不会归零。
 * <p>纯内存 Map 归零 ⇒ 可观察后果<b>不是</b>「重复计数无害」，而是：
 * <ul>
 *   <li>点赞路的 {@code like:total:<uid>} 是<b>普通 SET</b>
 *       （{@code UserLikeProgressSink} 的 {@code pipelineSetLikeTotal}），
 *       不是 GREATEST ⇒ 重启后被<b>更小的值覆盖</b>；</li>
 *   <li>达标判定 {@code total >= target(5)} 读的是<b>计数器</b>而不是 DB
 *       ⇒ 重启后用户必须重新攒够 5 次，任务才会完成；</li>
 *   <li>好消息是 {@code task:done:<uid>:<taskId>} 的 SETNX（90 天 TTL）不受影响，
 *       已完成的任务不会被重复发奖。</li>
 * </ul>
 * ⇒ 改用 Redis {@code INCR}：原子、跨重启、跨实例共享，语义比「进程内 + 落盘缓存」更贴近
 * goka 的 <b>Kafka 表</b>（那才是权威副本，本地 LevelDB 只是缓存）。
 *
 * <h2>⚠️ Redis 失败时的行为：抛异常，让位点不提交</h2>
 * <p>Go 侧 store 写失败时 goka 会退避并<b>重投该条消息</b>（计数不会前进）。
 * 这里让异常直接冒出 listener：{@code AckMode.RECORD} 下未提交位点 ⇒ 该条会被重投。
 * <b>不要</b>吞掉异常返回 1 —— 那等于把「计数没加上」伪装成「加了一次」，
 * 比丢数据更难排查。
 *
 * <h2>⚠️ key 命名空间是 RAG 侧的实现产物</h2>
 * <p>{@code task:goka:count:<groupId>:<messageKey>}。
 * Go 侧不存在这个 Redis key（它用 {@code <group>-table} compacted topic + 本地 LevelDB），
 * 本类与 {@link TaskRedisKeys} 里的业务 key（{@code task:progress:*} / {@code task:done:*} …）
 * <b>刻意不共用前缀</b>，避免被误读成 Go 也有的业务键。
 *
 * <h2>⚠️ 为什么用 {@code compute} 语义已经不重要，但 INCR 仍是必需的</h2>
 * <p>Go 靠「同一 group 内同一 key 串行处理」保证无并发写；Redis {@code INCR} 本身原子，
 * 即使将来给 listener 调了 {@code concurrency > 1} 也不会丢计数。
 *
 * <h2>🔴 2026-10-24：为什么这些 key <b>不设 TTL</b>（保留策略契约）</h2>
 * <p>本类的 Redis key <b>刻意不设过期时间</b>。这不是「忘了设」，而是<b>对齐 Go 侧的语义</b>，
 * 且已由真 Redis 守门测试锁住
 * （{@code TaskCountStoreRetentionContractTest}）。三条依据：
 *
 * <h3>① Go 侧计数表的<b>权威存储</b>是本地 LevelDB，默认无 TTL</h3>
 * <pre>
 * // goka@v1.1.16 storage/builders.go:18-26
 * func DefaultBuilder(path string) Builder {
 *     return func(topic string, partition int32) (Storage, error) {
 *         fp := filepath.Join(path, fmt.Sprintf("%s.%d", topic, partition))
 *         db, err := leveldb.OpenFile(fp, nil)     // ← 默认选项，**无 TTL**
 * </pre>
 * <p>而 goka 唯一的删除入口 {@code PartitionTable.Delete}
 * （{@code partition_table.go:622}）在 task 域<b>从未被调用</b> ——
 * 两个计数 processor 只有 {@code ctx.Value()} / {@code ctx.SetValue()}
 * （{@code initGoKa.go:75-81} 与 {@code :92-97}）⇒ <b>key 永不删除</b>。
 *
 * <h3>② Go 侧的 Kafka compacted topic 是<b>副本</b>，不是权威存储，且无人消费</h3>
 * <pre>
 * // goka@v1.1.16 topic_manager.go:317-326
 * func (m *topicManager) EnsureTableExists(topic string, npar int) error {
 *     return m.ensureExists(topic, npar, ..., map[string]string{
 *         "cleanup.policy": m.topicManagerConfig.tableCleanupPolicy(),   // ⇒ "compact"
 *     })                                          // ← **只有这一个键**，没有 retention.ms
 * </pre>
 * <p>⇒ 该副本的 config 里<b>没有 {@code retention.ms}</b>，落 broker 默认。
 * ⚠️ 但**无论它怎么滚动都不影响计数正确性**：这个 {@code <group>-table} topic
 * <b>全仓没有任何读取点</b>（唯一写它的是 goka 自己）⇒ 它只是表状态的一个备份，
 * <b>权威副本是本地 LevelDB</b>。
 * ⇒ <b>RAG 侧没有这个副本</b>（计数直接落 Redis），但这不构成可观察差异。
 *
 * <h3>③ 加 TTL 会<b>制造一条与 Go 的新漂移</b></h3>
 * <p>Go 的计数<b>永不丢失</b>；若给这些 key 加 TTL，计数会在到期后归零，
 * 而 {@code like:total:<uid>} 是<b>普通 SET</b>（不是 GREATEST）
 * ⇒ 重启/过期后会被<b>更小的值覆盖</b>，达标判定（{@code total >= target}）随之出错 ——
 * 与 {@code DRIFT-TASK-RPC-001} 同型的后果。
 *
 * <p>⚠️ <b>顺带一个实测事实</b>：Kafka 的 compacted topic <b>强制要求消息带 key</b>
 * （无 key 直接被 broker 拒：{@code InvalidRecordException: Compacted topic cannot accept
 * message without key}）⇒ Go 侧 goka 一直带 {@code ctx.Key()} emit 才写得进去
 * （{@code initGoKa.go:81} / {@code :106}）。这也从侧面印证了「compacted topic 存的是
 * <b>key → 最新值</b>」这一语义，与本类「一个 key 一个值」的模型同构。
 */
@Component
public class TaskCountStore {

    private static final Logger LOG = LoggerFactory.getLogger(TaskCountStore.class);

    /**
     * 计数器 key 前缀 —— 见类注释「key 命名空间是 RAG 侧的实现产物」。
     * 刻意区别于 {@link TaskRedisKeys} 的 {@code task:progress / task:init / task:done / task:inits}。
     */
    static final String COUNTER_KEY_PREFIX = "task:goka:count:";

    private final StringRedisTemplate redis;

    public TaskCountStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 对应 Go {@code ctx.Value()} + {@code cur++} + {@code ctx.SetValue(cur)}。
     *
     * @param group goka group id（{@code task.rpc.consumer61} / {@code task.rpc.consumer71}）——
     *               <b>命名空间</b>，Go 侧靠「按 group 分目录」达到同一效果
     * @param key   Kafka 消息的 key（Go 的 {@code ctx.Key()}）
     * @return 累加后的值；该 (group,key) 首次出现时返回 1
     * @throws org.springframework.data.redis.RedisConnectionFailureException
     *         Redis 不可用时<b>直接抛出</b> ⇒ 位点不提交 ⇒ 该条被重投（见类注释）
     */
    public long increment(String group, String key) {
        Long incremented = redis.opsForValue().increment(keyOf(group, key));
        if (incremented == null) {
            // 只有在 pipeline / 事务里没有立即返回值时才会是 null；
            // 正常路径不会走到。真走到就明确报错，而不是假装加了 1。
            throw new IllegalStateException(
                    "INCR returned null for group=" + group + " key=" + key);
        }
        return incremented;
    }

    /** 当前存量，仅用于诊断与测试。 */
    public long current(String group, String key) {
        String raw = redis.opsForValue().get(keyOf(group, key));
        if (raw == null) {
            return 0L;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            LOG.warn("counter value is not a number, treat as 0: group={} key={} raw={}", group, key, raw);
            return 0L;
        }
    }

    static String keyOf(String group, String key) {
        return COUNTER_KEY_PREFIX + group + ":" + key;
    }
}