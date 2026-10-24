package com.rag.task.task.rpc.mq;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TaskCountStore} 的<b>保留策略契约</b>守门测试 —— 用<b>真 Redis</b>。
 *
 * <h2>为什么需要这个类（背景）</h2>
 * <p>本仓 {@code docs/DRIFT/task/rpc.md} 的 §六 曾把「{@code task:goka:count:*} 无 TTL、只增不删」
 * 登记为一条<b>待办风险</b>，理由写着「<b>goka 的表会随 Kafka retention 滚动</b>」，
 * 并建议「长期需加保留策略（按 {@code like:total} 的 90 天口径定期清理）」。
 *
 * <h2>🔴 那个前提是错的，本类用源码 + 运行时证据把它钉死</h2>
 * <p><b>Go 侧的等价物不是 Redis key，而是一张 goka 表</b>，它由两部分组成：
 * <ol>
 *   <li><b>本地 LevelDB</b>（表存储本体）：
 *       <pre>
 *       // goka@v1.1.16 storage/builders.go:18-26
 *       func DefaultBuilder(path string) Builder {
 *           return func(topic string, partition int32) (Storage, error) {
 *               fp := filepath.Join(path, fmt.Sprintf("%s.%d", topic, partition))
 *               db, err := leveldb.OpenFile(fp, nil)      // ← 默认选项，**无 TTL**
 *       </pre>
 *       ⇒ LevelDB 默认<b>不按时间过期</b>；而 goka 的 {@code PartitionTable.Delete}
 *       （{@code partition_table.go:622}）在 task 域<b>从未被调用</b>
 *       （Go 的两个计数 processor 只有 {@code ctx.Value()} / {@code ctx.SetValue()}，
 *       见 {@code initGoKa.go:75-81} 与 {@code :92-97}）⇒ <b>key 永不删除</b>。</li>
 *   <li><b>Kafka compacted topic</b>（{@code <group>-table}，表状态的 Kafka 副本）：
 *       <pre>
 *       // goka@v1.1.16 topic_manager.go:317-326
 *       func (m *topicManager) EnsureTableExists(topic string, npar int) error {
 *           return m.ensureExists(topic, npar, m.topicManagerConfig.Table.Replication,
 *               map[string]string{
 *                   "cleanup.policy": m.topicManagerConfig.tableCleanupPolicy(),  // ⇒ "compact"
 *               })                                                                   // ← **只有这一个键**
 *       </pre>
 *       ⇒ <b>没有 {@code retention.ms}</b> ⇒ 落 broker 默认（{@code log.retention.hours}=168h）。
 *       且 Kafka 的按时间删除（{@code LogManager.deleteOldSegments}）<b>只在
 *       {@code cleanup.policy} 含 {@code delete} 时才启用</b>；{@code compact} 单独使用
 *       ⇒ <b>只做同 key 旧版本的压缩，不按时间删 key</b>。</li>
 * </ol>
 * ⇒ <b>结论：Go 的计数表「每个 key 保留最新值、且永不按时间过期」。</b>
 * ⇒ RAG 的 {@code task:goka:count:<group>:<key>}（Redis {@code INCR}，一个 key 一个值、
 * <b>不设 TTL</b>）<b>正是这个语义</b> ⇒ <b>「无 TTL」是等价实现，不是缺陷。</b>
 *
 * <h2>⚠️ 所以本类守的是「<b>不许</b>加 TTL」这条契约</h2>
 * <p>如果哪天有人觉得「Redis 内存无上限」而给这些 key 加 TTL，
 * 那就<b>制造了一条与 Go 的新漂移</b>（Go 永不过期、RAG 会过期）
 * ⇒ 计数会在 TTL 到期后归零，而 {@code like:total:<uid>} 是<b>普通 SET</b>不是 GREATEST
 * ⇒ 重启/过期后会被更小的值覆盖（与 {@code DRIFT-TASK-RPC-001} 同型的后果）。
 * ⇒ <b>本类的第 3 条断言就是拦这个的。</b>
 *
 * <h2>⚠️ 必须带阳性对照</h2>
 * <p>{@link #ttlProbeCanTellExpiringFromNonExpiring()} 先证明「读 TTL」这个探针
 * <b>确实能区分「有 TTL」与「无 TTL」</b>。
 * 没有这一条，「读到 -1」就可能只是「探针压根没生效」，
 * 那样第 3 条断言就是<b>假绿</b>（与本仓 {@code docs/Pitfalls/} 里记的那类事故同型）。
 *
 * <h2>为什么必须是<b>真 Redis</b></h2>
 * <p>{@link TaskSinkConsumeTest} 里 {@link TaskCountStore} 用的是<b>内存 Map</b> 假 Redis
 * ⇒ 它<b>根本没有 TTL 这个概念</b> ⇒ 任何关于「保留策略」的断言在那个替身上都必然通过。
 */
@Testcontainers
// ⚠️ 刻意**不加** @EnabledIfDockerAvailable（与 TaskProgressUpsertParityTest /
//    RedisPipelinesRealRedisGateTest 同款理由：该注解在类级求值时会去拿 Docker client，
//    拉不到 ryuk 镜像时它抛的异常会被压成「Docker is not available」⇒ 整类静默 skip
//    而 reactor 报 BUILD SUCCESS —— 那正是本类要防的事故本身。
//    Ryuk 已在 task-rpc/pom.xml 的 surefire environmentVariables 里禁用。
class TaskCountStoreRetentionContractTest {

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private LettuceConnectionFactory factory;
    private StringRedisTemplate redis;
    private TaskCountStore store;

    @BeforeEach
    void setUp() {
        factory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        factory.afterPropertiesSet();
        factory.start();
        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        store = new TaskCountStore(redis);
    }

    // ==================================================================
    // 阳性对照：探针本身必须能区分「有 TTL」与「无 TTL」
    // ==================================================================

    @Test
    @DisplayName("🔴 阳性对照：TTL 探针能区分「有 TTL」与「无 TTL」")
    void ttlProbeCanTellExpiringFromNonExpiring() {
        String withTtl = "gate:with-ttl";
        redis.opsForValue().set(withTtl, "v", Duration.ofSeconds(120));
        Long ttlWithExpire = redis.getExpire(withTtl, TimeUnit.SECONDS);
        assertNotNull(ttlWithExpire, "★ 探针失效：getExpire 返回 null");
        assertTrue(ttlWithExpire > 0,
                "★ 阳性对照失效：一个 120 秒 TTL 的 key 读出来不是正数（实际=" + ttlWithExpire
                        + "）⇒ 说明本测试用的「读 TTL」探针根本区分不出「有没有过期时间」"
                        + " ⇒ 后面那条「无 TTL」的断言就可能是假绿。");

        String withoutTtl = "gate:without-ttl";
        redis.opsForValue().set(withoutTtl, "v");
        Long ttlNoExpire = redis.getExpire(withoutTtl, TimeUnit.SECONDS);
        assertNotNull(ttlNoExpire, "★ 探针失效：getExpire 返回 null");
        assertEquals(-1L, ttlNoExpire,
                "★ 探针失效：一个没有 TTL 的 key 读出来不是 -1（实际=" + ttlNoExpire + "）");
    }

    // ==================================================================
    // 契约：无 TTL（= Go 的 compacted table 语义）
    // ==================================================================

    @Test
    @DisplayName("🔴 契约：task:goka:count:* 的 key 永不过期（TTL == -1）—— 对齐 Go 的 compacted table")
    void counterKeyNeverExpires() {
        store.increment("task.rpc.consumer61", "5001");

        String key = TaskCountStore.keyOf("task.rpc.consumer61", "5001");
        assertTrue(redis.hasKey(key), "计数 key 不存在：" + key);

        Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
        assertNotNull(ttl, "★ 探针失效：getExpire 返回 null");
        assertEquals(-1L, ttl,
                "🔴 契约被破坏：`" + key + "` 的 TTL = " + ttl + "（期望 -1 = 永不过期）。\n"
                        + "Go 侧的等价物是 goka 表（LevelDB + compacted topic），两者都是\n"
                        + "「每个 key 保留最新值、且**不按时间过期**」：\n"
                        + "  · LevelDB 默认无 TTL（goka storage/builders.go:18-26）\n"
                        + "  · 表 topic 的 config 只有 cleanup.policy=compact、**没有 retention.ms**\n"
                        + "    （goka topic_manager.go:317-326）⇒ Kafka 只压缩同 key 的旧版本，不按时间删 key\n"
                        + "⇒ 给这个 key 加 TTL 会**制造一条与 Go 的新漂移**"
                        + "（计数到期归零，而 like:total 是普通 SET 不是 GREATEST ⇒ 会被更小的值覆盖）。");
    }

    @Test
    @DisplayName("契约：计数语义与 Go 一致（首次为 1，之后累加）")
    void counterIncrementsLikeGo() {
        assertEquals(1L, store.increment("task.rpc.consumer61", "5001"));
        assertEquals(2L, store.increment("task.rpc.consumer61", "5001"));
        assertEquals(3L, store.increment("task.rpc.consumer61", "5001"));
        assertEquals(3L, store.current("task.rpc.consumer61", "5001"));
    }

    @Test
    @DisplayName("契约：两个 goka group 的计数互相隔离（DRIFT-TASK-RPC-001 的修复不回归）")
    void groupsAreIsolated() {
        assertEquals(1L, store.increment("task.rpc.consumer61", "190"));
        assertEquals(1L, store.increment("task.rpc.consumer71", "190"));
        assertEquals(1L, store.current("task.rpc.consumer61", "190"));
        assertEquals(1L, store.current("task.rpc.consumer71", "190"));
    }
}
