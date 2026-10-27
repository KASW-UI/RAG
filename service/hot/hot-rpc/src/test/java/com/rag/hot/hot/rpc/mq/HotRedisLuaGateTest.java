package com.rag.hot.hot.rpc.mq;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.rag.hot.hot.rpc.config.HotRpcProperties;
import com.rag.hot.hot.rpc.domain.HeavyKeeper;
import com.rag.hot.hot.rpc.metrics.HotRpcMetrics;
import com.rag.hot.hot.rpc.support.RealContainerHostPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🔴 <b>真 Redis</b> 上的 4 段 Lua / 两个 TTL / RENAME 原子性守门测试。
 *
 * <h2>为什么必须是「真 Redis 且默认跑」</h2>
 * <p>在此之前，本组件的 Lua 与 TTL 全部只有<b>静态源码证据</b>
 * （{@code docs/DRIFT/hot/rpc.md} 的 UNR-1 / UNR-2）。
 * 而这一层恰恰是最容易「读着像对、跑着不对」的地方：
 * <ul>
 *   <li>Lua 脚本里的 {@code KEYS[1] / KEYS[2]} 绑反 ⇒ 结果 key 写错，<b>不报错</b>；</li>
 *   <li>{@code EXPIRE} 写死 300 vs 配置 3600 ⇒ 两个 TTL 被「统一」成一个，
 *       静态读代码<b>看不出来</b>（两处都得改才不冲突）；</li>
 *   <li>{@code RENAME} 的原子性是 Redis 单命令保证的，<b>Mock 根本测不到</b>；</li>
 *   <li>CAS 型脚本（compare-and-{@code EXPIRE} / compare-and-{@code DEL}）的
 *       「值不匹配就不动手」语义，必须有真 Redis 才能验。</li>
 * </ul>
 * ⇒ H2 / mock / 手写假 Redis 都替代不了（与本仓 comment-rpc 的真 PG 守门测试同理由）。
 *
 * <h2>被测对象是<b>生产代码本身</b>（不是复制品）</h2>
 * <p>4 段 Lua 都是生产类里的 {@code static final RedisScript} 常量，
 * 断言直接跑它们；TTL 用<b>生产 {@code application.yml}</b> 绑出来的
 * {@link HotRpcProperties}（不是测试里另写一份数字）。
 *
 * <h2>Go 出处</h2>
 * <ul>
 *   <li>{@code service/hot/rpc/internal/mqs/hot_handler.go:227-234} —— 分片改名脚本（EXISTS 守卫 + RENAME + EXPIRE 300）</li>
 *   <li>{@code service/hot/rpc/internal/mqs/hot_handler.go:301-306} —— 看门狗续期（compare-and-EXPIRE）</li>
 *   <li>{@code service/hot/rpc/internal/mqs/hot_handler.go:329-334} —— 锁释放（compare-and-DEL）</li>
 *   <li>{@code service/hot/rpc/internal/mqs/hot_handler.go:400-407} —— 合并改名（字面量锁名 + RENAME + EXPIRE TTL）</li>
 *   <li>{@code service/hot/rpc/internal/mqs/hot_handler.go:208-209,390} —— 5 个 keyspace 的名字</li>
 * </ul>
 */
@Testcontainers
class HotRedisLuaGateTest {

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379)
            .withCreateContainerCmdModifier(RealContainerHostPort.bindRedis(RealContainerHostPort.HOT_REDIS_LUA_GATE));

    private static StringRedisTemplate redis;
    private static HotRpcProperties props;
    private static HotPartitionSketchRegistry registry;
    private static HotRpcMetrics metrics;
    private static HotRedisSnapshotStore store;
    private static HotMergeScheduler merge;

    /**
     * 每个用例前清空整个库。
     *
     * <p>⚠️ 必须清：分片 key 是按<b>分区号</b>命名的（{@code hot:articles:p{i}}），
     * 而合并用例固定读 p0..p2（= {@code Interaction.PartitionCount}）⇒
     * 其它用例留在 p1/p2 上的数据会**混进合并结果**。
     * 本容器专供本测试类，直接 flushDb 最省事也最不容易漏。
     */
    @BeforeEach
    void flushRedis() {
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @BeforeAll
    static void setUp() {
        LettuceConnectionFactory cf =
                new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        cf.afterPropertiesSet();
        redis = new StringRedisTemplate(cf);
        redis.afterPropertiesSet();

        props = bindProductionProperties();
        registry = new HotPartitionSketchRegistry(props);
        metrics = new HotRpcMetrics(new SimpleMeterRegistry());
        SyncTaskExecutor executor = new SyncTaskExecutor();
        store = new HotRedisSnapshotStore(redis, registry, metrics, executor);
        merge = new HotMergeScheduler(redis, props, executor);
    }

    /**
     * 用<b>生产 application.yml</b> 绑定 {@link HotRpcProperties}。
     *
     * <p>为什么要绑 yml 而不是测试里写死数字：TTL / 分片数这些值在 Go 侧也是 yaml 里的，
     * 测试里另写一份就等于<b>把「配置漂移」藏进了测试</b>。
     */
    private static HotRpcProperties bindProductionProperties() {
        List<PropertySource<?>> sources = new ArrayList<>();
        try {
            for (PropertySource<?> ps : new YamlPropertySourceLoader()
                    .load("application", new ClassPathResource("application.yml"))) {
                sources.add(ps);
            }
        } catch (Exception e) {
            throw new IllegalStateException("读不到生产 application.yml", e);
        }
        return new Binder(ConfigurationPropertySources.from(sources),
                new PropertySourcesPlaceholdersResolver(sources))
                .bind("hot-rpc", HotRpcProperties.class)
                .orElseThrow(() -> new IllegalStateException("application.yml 里没有 hot-rpc 段"));
    }

    // ====================================================================================
    // ① 4 段 Lua 逐段行为
    // ====================================================================================

    @Test
    @DisplayName("🔴 Lua① 分片改名：shadow 不存在 ⇒ 返回 0 且**不创建** partition key")
    void partitionRenameScriptIsNoopWhenShadowMissing() {
        String shadow = HotRedisSnapshotStore.partitionShadowKey(0);
        String partition = HotRedisSnapshotStore.partitionKey(0);
        redis.delete(List.of(shadow, partition));

        Long result = redis.execute(HotRedisSnapshotStore.PARTITION_RENAME_SCRIPT,
                List.of(shadow, partition));

        assertThat(result).as("EXISTS(shadow)==0 ⇒ Lua 返回 0").isZero();
        assertThat(redis.hasKey(partition)).as("守卫命中时**不得**创建 partition key").isFalse();
        assertThat(redis.hasKey(shadow)).isFalse();
    }

    @Test
    @DisplayName("🔴 Lua① 分片改名：shadow 存在 ⇒ 改名成功、shadow 消失、partition TTL = 300 硬编码")
    void partitionRenameScriptRenamesAndSetsHardcodedTtl() {
        String shadow = HotRedisSnapshotStore.partitionShadowKey(1);
        String partition = HotRedisSnapshotStore.partitionKey(1);
        redis.delete(List.of(shadow, partition));
        redis.opsForZSet().add(shadow, "a1", 10d);

        Long result = redis.execute(HotRedisSnapshotStore.PARTITION_RENAME_SCRIPT,
                List.of(shadow, partition));

        assertThat(result).as("EXISTS(shadow)==1 ⇒ Lua 返回 1").isEqualTo(1L);
        assertThat(redis.hasKey(shadow)).as("RENAME 后 shadow 必须消失（原子改名）").isFalse();
        assertThat(redis.hasKey(partition)).isTrue();
        assertThat(redis.opsForZSet().score(partition, "a1")).isEqualTo(10d);

        Long ttl = redis.getExpire(partition, TimeUnit.SECONDS);
        assertThat(ttl).as("分片 TTL 必须是 **300 硬编码**，不是配置的 %d（Go hot_handler.go:232）",
                props.getInteraction().getTtlSeconds())
                .isBetween(290L, 300L);
    }

    @Test
    @DisplayName("🔴 Lua② 看门狗续期：锁值匹配 ⇒ 续期成功；不匹配 ⇒ 返回 0 且不动锁")
    void renewScriptOnlyRenewsWhenLockValueMatches() {
        String lock = HotMergeScheduler.MERGE_LOCK_KEY;
        redis.delete(lock);
        redis.opsForValue().set(lock, "instance-A", Duration.ofSeconds(10));

        Long mismatch = redis.execute(HotMergeScheduler.RENEW_SCRIPT, List.of(lock), "instance-B",
                String.valueOf(HotMergeScheduler.LOCK_TTL_SECONDS));
        assertThat(mismatch).as("compare-and-EXPIRE：值不匹配 ⇒ 返回 0").isZero();
        assertThat(redis.opsForValue().get(lock)).isEqualTo("instance-A");

        Long matched = redis.execute(HotMergeScheduler.RENEW_SCRIPT, List.of(lock), "instance-A",
                String.valueOf(HotMergeScheduler.LOCK_TTL_SECONDS));
        assertThat(matched).as("compare-and-EXPIRE：值匹配 ⇒ 返回 EXPIRE 的结果 1").isEqualTo(1L);
        assertThat(redis.opsForValue().get(lock)).isEqualTo("instance-A");
        assertThat(redis.getExpire(lock, TimeUnit.SECONDS)).isBetween(1L, 10L);
    }

    @Test
    @DisplayName("🔴 Lua③ 锁释放：值匹配 ⇒ 删锁；不匹配 ⇒ 保留锁（不得删别人的锁）")
    void releaseScriptOnlyDeletesOwnLock() {
        String lock = HotMergeScheduler.MERGE_LOCK_KEY;
        redis.delete(lock);
        redis.opsForValue().set(lock, "instance-A", Duration.ofSeconds(10));

        Long mismatch = redis.execute(HotMergeScheduler.RELEASE_SCRIPT, List.of(lock), "instance-B");
        assertThat(mismatch).as("compare-and-DEL：值不匹配 ⇒ 返回 0").isZero();
        assertThat(redis.opsForValue().get(lock)).as("别人的锁不得被删").isEqualTo("instance-A");

        Long matched = redis.execute(HotMergeScheduler.RELEASE_SCRIPT, List.of(lock), "instance-A");
        assertThat(matched).isEqualTo(1L);
        assertThat(redis.hasKey(lock)).as("自己的锁必须被删").isFalse();
    }

    @Test
    @DisplayName("🔴 Lua④ 合并改名：锁值不匹配 ⇒ **不改名**（hot:articles 不被创建）")
    void mergeRenameScriptAbortsWhenLockValueDoesNotMatch() {
        String lock = HotMergeScheduler.MERGE_LOCK_KEY;
        String shadow = HotRedisSnapshotStore.MERGE_SHADOW_KEY;
        redis.delete(List.of(lock, shadow, HotRedisSnapshotStore.HOT_ARTICLES_KEY));
        redis.opsForValue().set(lock, "instance-A", Duration.ofSeconds(10));
        redis.opsForZSet().add(shadow, "a1", 5d);

        Long result = redis.execute(HotMergeScheduler.MERGE_RENAME_SCRIPT,
                List.of(shadow, HotRedisSnapshotStore.HOT_ARTICLES_KEY),
                "instance-B", String.valueOf(props.getInteraction().getTtlSeconds()));

        assertThat(result).as("Lua 里比的是**字面量** \"hot:merge:lock\" 的 value ⇒ 不匹配即 0").isZero();
        assertThat(redis.hasKey(HotRedisSnapshotStore.HOT_ARTICLES_KEY))
                .as("CAS 失败 ⇒ hot:articles **不得**被创建").isFalse();
        assertThat(redis.hasKey(shadow)).as("CAS 失败 ⇒ shadow 保留").isTrue();
    }

    @Test
    @DisplayName("🔴 Lua④ 合并改名：锁值匹配 ⇒ 改名成功、大 key TTL = 配置值（3600）")
    void mergeRenameScriptRenamesAndSetsConfiguredTtl() {
        String lock = HotMergeScheduler.MERGE_LOCK_KEY;
        String shadow = HotRedisSnapshotStore.MERGE_SHADOW_KEY;
        redis.delete(List.of(lock, shadow, HotRedisSnapshotStore.HOT_ARTICLES_KEY));
        redis.opsForValue().set(lock, "instance-A", Duration.ofSeconds(10));
        redis.opsForZSet().add(shadow, "a1", 5d);
        redis.opsForZSet().add(shadow, "a2", 3d);

        Long result = redis.execute(HotMergeScheduler.MERGE_RENAME_SCRIPT,
                List.of(shadow, HotRedisSnapshotStore.HOT_ARTICLES_KEY),
                "instance-A", String.valueOf(props.getInteraction().getTtlSeconds()));

        assertThat(result).isEqualTo(1L);
        assertThat(redis.hasKey(shadow)).as("RENAME 后 shadow 消失").isFalse();
        assertThat(redis.hasKey(HotRedisSnapshotStore.HOT_ARTICLES_KEY)).isTrue();
        assertThat(redis.opsForZSet().score(HotRedisSnapshotStore.HOT_ARTICLES_KEY, "a1")).isEqualTo(5d);

        Long ttl = redis.getExpire(HotRedisSnapshotStore.HOT_ARTICLES_KEY, TimeUnit.SECONDS);
        assertThat(ttl).as("大 key TTL = Interaction.TTL = %d（与分片的 300 **不同**）",
                        props.getInteraction().getTtlSeconds())
                .isBetween(props.getInteraction().getTtlSeconds() - 10,
                        props.getInteraction().getTtlSeconds());
    }

    // ====================================================================================
    // ② 两个 TTL 不能被统一（分片 300 vs 大 key 配置值）
    // ====================================================================================

    @Test
    @DisplayName("🔴 两个 TTL 不同：分片 300（硬编码）vs 大 key = Interaction.TTL")
    void twoTtlsAreNotUnified() {
        String partition = HotRedisSnapshotStore.partitionKey(2);
        String big = HotRedisSnapshotStore.HOT_ARTICLES_KEY;
        redis.delete(List.of(partition, big));

        redis.opsForZSet().add(partition, "a1", 1d);
        redis.expire(partition, Duration.ofSeconds(HotRedisSnapshotStore.PARTITION_TTL_SECONDS));
        redis.opsForZSet().add(big, "a1", 1d);
        redis.expire(big, Duration.ofSeconds(props.getInteraction().getTtlSeconds()));

        assertThat(HotRedisSnapshotStore.PARTITION_TTL_SECONDS).isEqualTo(300);
        assertThat(props.getInteraction().getTtlSeconds()).isEqualTo(3600L);
        assertThat(redis.getExpire(partition, TimeUnit.SECONDS))
                .isBetween(290L, 300L);
        assertThat(redis.getExpire(big, TimeUnit.SECONDS))
                .isBetween(3590L, 3600L);
    }

    // ====================================================================================
    // ③ 生产路径：首次快照 / 空快照 / 回灌
    // ====================================================================================

    @Test
    @DisplayName("🔴 生产 flush() 的首次快照：**partition key 会被创建**（台账「首次快照必丢」是错的）")
    void firstSnapshotIsNotLost() {
        int pid = 7;
        String partition = HotRedisSnapshotStore.partitionKey(pid);
        String shadow = HotRedisSnapshotStore.partitionShadowKey(pid);
        redis.delete(List.of(partition, shadow));

        HeavyKeeper hk = new HeavyKeeper(props.getHeavyKeeper().getWidth(),
                props.getHeavyKeeper().getDepth(), props.getHeavyKeeper().getDecay(),
                props.getHeavyKeeper().getTopK());
        hk.add("a1", 7);
        hk.add("a2", 3);

        store.flush(pid, hk);

        assertThat(redis.hasKey(partition))
                .as("EXISTS 守卫检查的是 **shadow**（KEYS[1]），不是 partition（KEYS[2]）"
                        + "⇒ shadow 刚被 ZADD 写入 ⇒ 守卫恒为 1 ⇒ **首次快照不会被丢**")
                .isTrue();
        assertThat(redis.opsForZSet().score(partition, "a1")).isNotNull();
        assertThat(redis.hasKey(shadow)).isFalse();
        assertThat(redis.getExpire(partition, TimeUnit.SECONDS)).isBetween(290L, 300L);
    }

    @Test
    @DisplayName("🔴 生产 flush() 在 TopK 为空时**什么都不写**（旧 key 保持陈旧）")
    void emptyTopKLeavesStaleKeyUntouched() {
        int pid = 8;
        String partition = HotRedisSnapshotStore.partitionKey(pid);
        redis.delete(partition);
        redis.opsForZSet().add(partition, "stale", 1d);
        redis.expire(partition, Duration.ofSeconds(300));

        HeavyKeeper empty = new HeavyKeeper(props.getHeavyKeeper().getWidth(),
                props.getHeavyKeeper().getDepth(), props.getHeavyKeeper().getDecay(),
                props.getHeavyKeeper().getTopK());

        store.flush(pid, empty);

        assertThat(redis.opsForZSet().score(partition, "stale"))
                .as("TopK 为空 ⇒ 不写不删 ⇒ 旧 key 陈旧（Go hot_handler.go:204-206）").isEqualTo(1d);
    }

    @Test
    @DisplayName("🔴 回灌：分片 score 当**新权重**灌进 sketch（有损，不可逆）")
    void loadIntoTreatsScoreAsFreshWeight() {
        int pid = 9;
        String partition = HotRedisSnapshotStore.partitionKey(pid);
        redis.delete(partition);
        redis.opsForZSet().add(partition, "a1", 42d);

        HeavyKeeper hk = new HeavyKeeper(props.getHeavyKeeper().getWidth(),
                props.getHeavyKeeper().getDepth(), props.getHeavyKeeper().getDecay(),
                props.getHeavyKeeper().getTopK());

        store.loadInto(pid, hk);

        List<HeavyKeeper.Item> topK = hk.topK();
        assertThat(topK).hasSize(1);
        assertThat(topK.get(0).key()).isEqualTo("a1");
        // 42 灌进 depth=5 个桶 ⇒ 每个桶 count=42 ⇒ maxCount=42（不是 42*5）
        assertThat(Integer.toUnsignedLong(topK.get(0).count())).isEqualTo(42L);
    }

    // ====================================================================================
    // ④ 合并面：求和 / 降序 / 截断 / 空结果不写 shadow
    // ====================================================================================

    /**
     * 持锁：把 {@code hot:merge:lock} 的 value 设成本调度器的 instanceID。
     *
     * <p>为什么不走 {@code acquireLockWithWatchDog()}：它会顺带起一个看门狗线程，
     * 而本测试用的是**同步** TaskExecutor ⇒ 看门狗会 inline 跑死。
     * 直接写锁 value 与真实路径（抢锁后 value 就是 instanceID）等价。
     */
    private void holdMergeLock() {
        redis.opsForValue().set(HotMergeScheduler.MERGE_LOCK_KEY, merge.instanceId(),
                Duration.ofSeconds(HotMergeScheduler.LOCK_TTL_SECONDS));
    }

    @Test
    @DisplayName("🔴 合并：各分片分数**求和**后降序截断 100，写进 hot:articles")
    void mergeSumsAcrossPartitions() {
        redis.delete(List.of(HotRedisSnapshotStore.HOT_ARTICLES_KEY,
                HotRedisSnapshotStore.MERGE_SHADOW_KEY, HotMergeScheduler.MERGE_LOCK_KEY));
        for (int i = 0; i < props.getInteraction().getPartitionCount(); i++) {
            redis.opsForZSet().add(HotRedisSnapshotStore.partitionKey(i), "shared", 10d);
            redis.opsForZSet().add(HotRedisSnapshotStore.partitionKey(i), "only-p" + i, 5d);
        }
        // ⚠️ 必须先持锁：合并改名脚本比的是锁的 value（CAS），不持锁 ⇒ 改名被拒
        //    ⇒ 日志会出现 "[Merge] lock lost, abort"（那正是 CAS 生效的证据，不是失败）。
        holdMergeLock();

        merge.mergePartitions();

        Set<ZSetOperations.TypedTuple<String>> merged =
                redis.opsForZSet().reverseRangeWithScores(HotRedisSnapshotStore.HOT_ARTICLES_KEY, 0, -1);
        assertThat(merged).isNotNull();
        Map<String, Double> byKey = new java.util.HashMap<>();
        merged.forEach(t -> byKey.put(t.getValue(), t.getScore()));

        assertThat(byKey.get("shared"))
                .as("3 个分片各 10 分 ⇒ 求和 30").isEqualTo(30d);
        assertThat(byKey.get("only-p0")).isEqualTo(5d);
        assertThat(byKey).hasSize(4);
        assertThat(redis.hasKey(HotRedisSnapshotStore.MERGE_SHADOW_KEY))
                .as("改名后 shadow 消失").isFalse();
    }

    @Test
    @DisplayName("🔴 合并：分片为空 ⇒ **连 shadow 都不写**（Go :386-388）")
    void mergeWritesNothingWhenAllPartitionsEmpty() {
        redis.delete(List.of(HotRedisSnapshotStore.HOT_ARTICLES_KEY,
                HotRedisSnapshotStore.MERGE_SHADOW_KEY, HotMergeScheduler.MERGE_LOCK_KEY));
        for (int i = 0; i < props.getInteraction().getPartitionCount(); i++) {
            redis.delete(HotRedisSnapshotStore.partitionKey(i));
        }
        holdMergeLock();

        merge.mergePartitions();

        assertThat(redis.hasKey(HotRedisSnapshotStore.MERGE_SHADOW_KEY)).isFalse();
        assertThat(redis.hasKey(HotRedisSnapshotStore.HOT_ARTICLES_KEY)).isFalse();
    }

    /**
     * 🔴 <b>截断层守门</b>：合并结果 &gt;100 条时，<b>只留分数最高的 100 条</b>。
     *
     * <h2>为什么必须有这条（U9 系统变异查出来的真实守门缺口）</h2>
     * <p>此前 {@code MERGED_LIMIT = 100} 与「按分数降序排序」这两处<b>都没有任何测试覆盖</b>：
     * 既有用例的分片条目最多 4 条 ⇒ <b>永远走不到截断分支</b> ⇒
     * 变异验证时这两处<b>改错了也没人叫</b>（U9 第十一轮的 R08 / R09 两个「没变红」）。
     * <p>⚠️ 这两处<b>必须一起测</b>：它们在 &gt;100 条时<b>耦合</b> ——
     * 排序方向若被改反（升序），截断会留下<b>分数最低</b>的 100 条，
     * 而「条数」断言（100 vs 101）<b>抓不到</b>这种错误 ⇒ 必须断言<b>留存集合的边界</b>。
     *
     * <h2>Go 出处</h2>
     * <p>{@code service/hot/rpc/internal/mqs/hot_handler.go:383-385}：
     * <pre>
     * sort.Slice(merged, func(i, j int) bool { return merged[i].Score &gt; merged[j].Score })
     * if len(merged) &gt; 100 { merged = merged[:100] }
     * </pre>
     */
    @Test
    @DisplayName("🔴 合并：>100 条 ⇒ **只留分数最高的 100 条**（Go merged[:100] + 降序）")
    void mergeTruncatesToThe100HighestScores() {
        redis.delete(List.of(HotRedisSnapshotStore.HOT_ARTICLES_KEY,
                HotRedisSnapshotStore.MERGE_SHADOW_KEY, HotMergeScheduler.MERGE_LOCK_KEY));
        // 150 条，分数 1..150，全放 p0（避免跨分片求和干扰边界断言）
        for (int i = 1; i <= 150; i++) {
            redis.opsForZSet().add(HotRedisSnapshotStore.partitionKey(0),
                    String.format("a%03d", i), (double) i);
        }
        holdMergeLock();

        merge.mergePartitions();

        Set<ZSetOperations.TypedTuple<String>> merged =
                redis.opsForZSet().reverseRangeWithScores(HotRedisSnapshotStore.HOT_ARTICLES_KEY, 0, -1);
        assertThat(merged).isNotNull();

        assertThat(merged)
                .as("合并结果必须截断到 100（Go hot_handler.go:383-385 merged[:100]）")
                .hasSize(100);

        // ★ 关键判定：留存的必须是**分数最高**的那批（150 条里取 51..150）。
        //   只断言「条数 = 100」是不够的 —— 排序方向被改反时条数仍是 100，
        //   但留下的是**分数最低**的 1..100 ⇒ 必须断言边界分数。
        assertThat(merged.stream().mapToDouble(ZSetOperations.TypedTuple::getScore).min().orElseThrow())
                .as("留存的最低分必须是 51（= 150-100+1）⇒ 留的是最高的一批")
                .isEqualTo(51d);
        assertThat(merged.stream().mapToDouble(ZSetOperations.TypedTuple::getScore).max().orElseThrow())
                .as("留存的最高分必须是 150").isEqualTo(150d);

        // 且「a001」这种低分条目必须**已被丢掉**
        assertThat(merged.stream().map(ZSetOperations.TypedTuple::getValue).toList())
                .doesNotContain("a001", "a050")
                .contains("a051", "a150");
    }
}
