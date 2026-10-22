package com.rag.hot.hot.rpc.mq;

import java.util.List;
import java.util.Set;

import com.rag.hot.hot.rpc.domain.HeavyKeeper;
import com.rag.hot.hot.rpc.metrics.HotRpcMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 全部 Redis keyspace + 4 段 Lua + 30s 周期快照 —— 对应 Go
 * {@code service/hot/rpc/internal/mqs/hot_handler.go} 的 Redis 快照段
 * （{@code syncToRedis:195-242}、{@code loadFromRedis:265-283}、{@code periodicSync:244-263}）。
 *
 * <p><b>为什么 Redis 细节必须原样复刻</b>：本类是最容易在「等价重构」中被改坏的一面。
 * 三处不可统一的地方：
 * <ol>
 *   <li><b>两个不同的过期时间</b>：分片 key {@code hot:articles:p{i}} 的 TTL 是
 *       <b>300s 硬编码</b>（写死在 Lua 里，{@code hot_handler.go:232}），与配置
 *       {@code Interaction.TTL}（3600）<b>无关</b>；而合并后的大 key {@code hot:articles}
 *       才用 {@code Interaction.TTL}。⇒ 空闲超过 5 分钟的分片 key 会消失，
 *       之后 rebalance 回灌读到空。</li>
 *   <li><b>{@code EXISTS} 守卫</b>（Lua 第一行）：目标分片 key 不存在时直接返回 0、
 *       <b>整个快照被丢弃</b>。⇒ <b>某个分片的首次快照必然丢失</b>。这是 Go 既有行为，原样保留。</li>
 *   <li><b>shadow → RENAME 的原子性</b>：先写 shadow 再 Lua 原子改名，
 *       避免合并期读到半成品。</li>
 * </ol>
 *
 * <p><b>错误契约（L3）</b>：Go 的同步失败路径是「{@code redisSyncTotal.Inc("fail")} 后 return，
 * <b>不抛出</b>」（{@code hot_handler.go:220-224,236-241}）。
 * Spring Data Redis 一律抛 {@code DataAccessException} ⇒ 本类<b>必须显式吞掉</b>，
 * 否则会把「快照失败不影响主流程」变成「消费失败触发重试」，直接改变 drop 语义。
 */
@Component
public class HotRedisSnapshotStore {

    private static final Logger LOG = LoggerFactory.getLogger(HotRedisSnapshotStore.class);

    /** 合并后的大 key，{@code GetHotArticles} 的唯一读取源。Go {@code get_hot_articles_logic.go:16}。 */
    public static final String HOT_ARTICLES_KEY = "hot:articles";

    /** 合并期的影子榜 key。Go {@code hot_handler.go:390}。 */
    public static final String MERGE_SHADOW_KEY = "hot:articles:shadow";

    /** 分区快照 key 模板。Go {@code hot_handler.go:208}。 */
    private static final String PARTITION_KEY_FMT = "hot:articles:p%d";

    /** 分区影子榜 key 模板。Go {@code hot_handler.go:209}。 */
    private static final String PARTITION_SHADOW_KEY_FMT = "hot:articles:p%d:shadow";

    /**
     * ⚠️ 分片 key 的 EXPIRE 秒数：<b>300 硬编码</b>，Go {@code hot_handler.go:232} 写死在 Lua 里。
     * <b>不要</b>改成读 {@code Interaction.TTL}（那是给大 key 的）——那是行为变更。
     */
    public static final int PARTITION_TTL_SECONDS = 300;

    /** 周期快照间隔，Go {@code hot_handler.go:245} 硬编码 30s。 */
    private static final int PERIODIC_SYNC_MILLIS = 30_000;

    /**
     * 分片快照改名脚本（Go {@code hot_handler.go:227-234}）。
     *
     * <p>⚠️ {@code EXISTS} 守卫：目标 key 不存在 ⇒ 返回 0 ⇒ <b>首次快照被丢弃</b>。
     * KEYS[1]=shadow，KEYS[2]=分区 key。
     */
    static final RedisScript<Long> PARTITION_RENAME_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then
                return 0
            end
            redis.call('RENAME', KEYS[1], KEYS[2])
            redis.call('EXPIRE', KEYS[2], 300)
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final HotPartitionSketchRegistry registry;
    private final HotRpcMetrics metrics;
    private final TaskExecutor taskExecutor;

    public HotRedisSnapshotStore(StringRedisTemplate redis,
                                 HotPartitionSketchRegistry registry,
                                 HotRpcMetrics metrics,
                                 TaskExecutor taskExecutor) {
        this.redis = redis;
        this.registry = registry;
        this.metrics = metrics;
        this.taskExecutor = taskExecutor;
    }

    public static String partitionKey(int partitionId) {
        return String.format(PARTITION_KEY_FMT, partitionId);
    }

    public static String partitionShadowKey(int partitionId) {
        return String.format(PARTITION_SHADOW_KEY_FMT, partitionId);
    }

    /**
     * 对应 Go {@code syncToRedis}：把分区 sketch 的 TopK 落到 {@code hot:articles:p{i}}。
     *
     * <p>行为逐条对齐：
     * <ol>
     *   <li>分区不存在 ⇒ 直接 return（Go {@code :199-201}）</li>
     *   <li>{@code TopK()} 为空 ⇒ <b>不写</b>，旧 key 保持陈旧（Go {@code :204-206}）</li>
     *   <li>pipeline：{@code DEL shadow} + {@code ZADD shadow}（Go {@code :217-218}）</li>
     *   <li>Lua：{@code EXISTS} 守卫 + {@code RENAME} + {@code EXPIRE 300}</li>
     *   <li>失败 → {@code redisSyncTotal.Inc("fail")} 后 return，<b>不抛</b>（Go {@code :220-224,236-238}）</li>
     * </ol>
     *
     * <p>⚠️ 与 Go 的<b>唯一有意差异</b>：Go 的 {@code syncToRedis} 自己按 partitionId 去
     * {@code hkMap} 里取 sketch；本方法直接接收 sketch 引用。
     * 原因见 {@link HotPartitionSketchRegistry} 的类注释（Go 侧持写锁再取读锁会死锁）。
     */
    public void flush(int partitionId, HeavyKeeper hk) {
        if (hk == null) {
            return;
        }
        List<HeavyKeeper.Item> items = hk.topK();
        if (items.isEmpty()) {
            // Go：TopK 为空则不写，旧 key 保持陈旧（hot_handler.go:204-206）
            return;
        }

        String partitionKey = partitionKey(partitionId);
        String shadowKey = partitionShadowKey(partitionId);

        Set<ZSetOperations.TypedTuple<String>> tuples =
                items.stream()
                        .map(it -> (ZSetOperations.TypedTuple<String>) ZSetOperations.TypedTuple
                                .of(it.key(), HeavyKeeper.countToScore(it.count())))
                        .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));

        try {
            redis.executePipelined(new org.springframework.data.redis.core.SessionCallback<Object>() {
                @Override
                @SuppressWarnings("unchecked")
                public Object execute(org.springframework.data.redis.core.RedisOperations operations) {
                    operations.delete(shadowKey);
                    operations.opsForZSet().add(shadowKey, tuples);
                    return null;
                }
            });
        } catch (RuntimeException e) {
            LOG.error("[Sync] partition {} pipeline failed: {}", partitionId, e.getMessage(), e);
            metrics.redisSyncFail();
            return;
        }
        LOG.info("[Sync] partition {} pipeline success: items={}", partitionId, items.size());

        try {
            Long result = redis.execute(PARTITION_RENAME_SCRIPT,
                    List.of(shadowKey, partitionKey));
            if (result == null || result == 0L) {
                // result==0 = EXISTS 守卫命中（首次快照必丢）或真失败 —— Go 同样走 fail 分支
                LOG.error("[Sync] partition {} rename failed: result={}", partitionId, result);
                metrics.redisSyncFail();
            } else {
                metrics.redisSyncSuccess();
            }
        } catch (RuntimeException e) {
            LOG.error("[Sync] partition {} rename failed: {}", partitionId, e.getMessage(), e);
            metrics.redisSyncFail();
        }
    }

    /**
     * 对应 Go {@code syncToRedis} 的「按 partitionId 自行取 sketch」形态，
     * 供 {@link #periodicSync()} 与消费侧的触发快照使用。
     */
    public void flushByPartition(int partitionId) {
        flush(partitionId, registry.heavyKeeperFor(partitionId));
    }

    /**
     * 对应 Go {@code loadFromRedis}（{@code hot_handler.go:265-283}）：
     * {@code ZREVRANGE hot:articles:p{i} 0 -1}，把<b>合并后的累计分数当权重</b>灌进 sketch。
     *
     * <p>⚠️ <b>这是有损语义，必须保留</b>（Go 既有行为，非 bug）：
     * sketch 的桶计数与真实发生次数之间<b>没有可逆关系</b>，
     * 回灌得到的是一个<b>新估计</b>而不是原值。
     * 换成「精确恢复」会改变 rebalance 后热度的连续性手感。
     */
    public void loadInto(int partitionId, HeavyKeeper hk) {
        String key = partitionKey(partitionId);
        Set<ZSetOperations.TypedTuple<String>> items;
        try {
            items = redis.opsForZSet().reverseRangeWithScores(key, 0, -1);
        } catch (RuntimeException e) {
            LOG.error("[Hot] partition {} load failed: {}", partitionId, e.getMessage(), e);
            return;
        }
        if (items == null || items.isEmpty()) {
            LOG.info("[Hot] loaded 0 articles for partition {}", partitionId);
            return;
        }

        int loaded = 0;
        for (ZSetOperations.TypedTuple<String> item : items) {
            Object member = item.getValue();
            if (!(member instanceof String articleId)) {
                // Go :275-278 的 `, ok` 保护：非字符串 member 跳过
                continue;
            }
            Double score = item.getScore();
            if (score == null) {
                continue;
            }
            hk.add(articleId, HeavyKeeper.scoreToCount(score));
            loaded++;
        }
        LOG.info("[Hot] loaded {} articles for partition {}", loaded, partitionId);
    }

    /**
     * 对应 Go {@code periodicSync}（{@code hot_handler.go:244-263}）：30s 硬编码 ticker，
     * 对每个已持有分区起一个 goroutine 同步。
     *
     * <p>Go 的「每分区一个 goroutine」在 Java 侧映射为 Spring 的 {@link TaskExecutor} 提交
     * （默认 {@code applicationTaskExecutor}）⇒ 单个分区的 Redis 慢不会拖住其他分区，
     * 也不会推迟下一轮 30s tick。
     */
    @Scheduled(fixedRate = PERIODIC_SYNC_MILLIS)
    public void periodicSync() {
        for (int pid : registry.partitionIds()) {
            // 先在锁外取 sketch 引用（Go :250-255 同款），再异步落盘
            HeavyKeeper hk = registry.heavyKeeperFor(pid);
            taskExecutor.execute(() -> flush(pid, hk));
        }
    }

    /** 供测试与合并侧复用的 Redis 模板访问。 */
    StringRedisTemplate redis() {
        return redis;
    }
}