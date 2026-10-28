package com.rag.hot.hot.rpc.service;

import java.util.List;

import com.rag.hot.hot.rpc.mq.HotRedisSnapshotStore;
import com.rag.hot.hot.rpc.support.RealContainerHostPort;
import com.rag.hot.hot.proto.HotArticleItem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🔴 <b>真 Redis</b> 上的 <b>查询面</b>守门测试
 * （补 {@code docs/DRIFT/hot/rpc.md} 未闭合表里最后一条：「{@code HotRankingQueryService}
 * 的 Redis 读取分支仍只有 Mockito 单测」）。
 *
 * <h2>为什么这条必须上真 Redis</h2>
 * <p>第四轮的真 Redis IT 覆盖的是<b>写入面</b>（4 段 Lua / 两个 TTL / {@code EXISTS} 守卫），
 * <b>查询面</b>一直只有 Mockito 单测 ⇒ 下面这几条<b>全都</b>是 mock 测不了的：
 * <ul>
 *   <li><b>{@code Set} 是否保序</b> —— Spring Data Redis 的
 *       {@code deserializeTupleValues} 返回哪种 {@code Set}？若是 {@code HashSet}，
 *       <b>并列项的顺序会丢</b> ⇒ 热榜顺序与 Go 不同。mock 返回什么就是什么，<b>永远测不出</b>。</li>
 *   <li><b>闭区间</b> —— Go 的 {@code ZRevRangeWithScores(ctx, key, 0, k-1)} 是
 *       <b>rank 闭区间</b> ⇒ 取 <b>k</b> 条（不是 k+1）。</li>
 *   <li><b>并列 tie-break</b> —— ZSET 同分时按 member <b>字典序升序</b>排 rank，
 *       {@code ZREVRANGE} 取逆序 ⇒ 同分项的 member 呈<b>字典序降序</b>。</li>
 *   <li><b>float64 → uint32 向零截断</b> —— Redis score 是 float64，
 *       {@code uint32(3.7)} 是 <b>3</b>（截断）而不是 4（round）。</li>
 * </ul>
 *
 * <h2>命令层已取证为<b>同构</b>（但仍要实测）</h2>
 * <p>Go 侧 go-redis 的 {@code ZRevRangeWithScores} 与 RAG 侧 Lettuce 的
 * {@code zRevRangeWithScores} <b>发的是同一个命令</b>：
 * <ul>
 *   <li>go-redis v9.20.0 {@code zsetCommands.go} ⇒ {@code ZREVRANGE key start stop WITHSCORES}；</li>
 *   <li>Lettuce 6.8.2 {@code RedisCommandBuilder.zrevrangeWithScores} ⇒
 *       {@code createCommand(ZREVRANGE, …, args.add(start).add(stop).add(WITHSCORES))}
 *       （<b>消费点</b>，非声明点）；</li>
 *   <li>Spring Data Redis 4.0.7 {@code DefaultZSetOperations.reverseRangeWithScores}
 *       ⇒ {@code connection.zRevRangeWithScores(rawKey, start, end)}。</li>
 * </ul>
 * ⇒ 服务端返回的字节应当相同；本测试验的是<b>框架层有没有在中间动手脚</b>（保序 / 截断 / 闭区间）。
 *
 * <h2>Go 出处</h2>
 * <ul>
 *   <li>{@code service/hot/rpc/internal/logic/get_hot_articles_logic.go:34-40} —— TopK 归一；</li>
 *   <li>{@code .../get_hot_articles_logic.go:43} —— {@code ZRevRangeWithScores(ctx, "hot:articles", 0, k-1)}；</li>
 *   <li>{@code .../get_hot_articles_logic.go:44-51} —— 错误分支；</li>
 *   <li>{@code .../get_hot_articles_logic.go:55-59} —— member 非字符串 → 跳过；</li>
 *   <li>{@code .../get_hot_articles_logic.go:62} —— {@code HotScore: uint32(z.Score)}。</li>
 * </ul>
 */
@Testcontainers
class HotRankingQueryServiceRedisGateTest {

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379)
            .withCreateContainerCmdModifier(RealContainerHostPort.bindRedis(RealContainerHostPort.HOT_RANKING_QUERY_REDIS_GATE));

    private static StringRedisTemplate redis;
    private static HotRankingQueryService service;

    @BeforeAll
    static void setUp() {
        LettuceConnectionFactory cf =
                new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        cf.afterPropertiesSet();
        redis = new StringRedisTemplate(cf);
        redis.afterPropertiesSet();
        service = new HotRankingQueryService(redis);
    }

    @BeforeEach
    void flushRedis() {
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    // ====================================================================================
    // ① rank 闭区间（mock 测不了）
    // ====================================================================================

    @Test
    @DisplayName("🔴 Go :43 —— ZRevRangeWithScores(0, k-1) 是 **rank 闭区间** ⇒ k=3 恰好取 3 条")
    void rankRangeIsClosedInterval() {
        for (int i = 1; i <= 5; i++) {
            redis.opsForZSet().add(HotRedisSnapshotStore.HOT_ARTICLES_KEY, "article-" + i, i);
        }

        List<HotArticleItem> items = service.getHotArticles(3);

        assertThat(items).as("闭区间 [0, k-1] ⇒ 恰好 k 条，不是 k+1 条").hasSize(3);
        assertThat(items).extracting(HotArticleItem::getArticleId)
                .containsExactly("article-5", "article-4", "article-3");
    }

    @Test
    @DisplayName("🔴 ZREVRANGE 按 score 降序返回（不是插入序）")
    void resultsAreSortedByScoreDescending() {
        redis.opsForZSet().add(HotRedisSnapshotStore.HOT_ARTICLES_KEY, "low", 1);
        redis.opsForZSet().add(HotRedisSnapshotStore.HOT_ARTICLES_KEY, "high", 99);
        redis.opsForZSet().add(HotRedisSnapshotStore.HOT_ARTICLES_KEY, "mid", 50);

        List<HotArticleItem> items = service.getHotArticles(10);

        assertThat(items).extracting(HotArticleItem::getArticleId)
                .containsExactly("high", "mid", "low");
    }

    @Test
    @DisplayName("🔴 同分项的 tie-break：ZSET 同分按 member 字典序**升序**排 rank ⇒ ZREVRANGE 取**降序**")
    void tieBreakIsLexicographicDescending() {
        redis.opsForZSet().add(HotRedisSnapshotStore.HOT_ARTICLES_KEY, "b", 10);
        redis.opsForZSet().add(HotRedisSnapshotStore.HOT_ARTICLES_KEY, "a", 10);
        redis.opsForZSet().add(HotRedisSnapshotStore.HOT_ARTICLES_KEY, "c", 10);

        List<HotArticleItem> items = service.getHotArticles(10);

        // ⚠️ 这条同时在验「Spring 返回的 Set 保序」：
        //    若 deserializeTupleValues 返回 HashSet ⇒ 顺序会丢 ⇒ 本断言会变红。
        assertThat(items).extracting(HotArticleItem::getArticleId)
                .containsExactly("c", "b", "a");
    }

    // ====================================================================================
    // ② float64 → uint32 向零截断
    // ====================================================================================

    @Test
    @DisplayName("🔴 Go :62 —— uint32(score) 是**向零截断**：3.7 ⇒ 3（round 会给 4）")
    void floatScoreIsTruncatedTowardZero() {
        redis.opsForZSet().add(HotRedisSnapshotStore.HOT_ARTICLES_KEY, "frac", 3.7);
        redis.opsForZSet().add(HotRedisSnapshotStore.HOT_ARTICLES_KEY, "frac2", 2.2);

        List<HotArticleItem> items = service.getHotArticles(10);

        assertThat(items).extracting(HotArticleItem::getArticleId)
                .containsExactly("frac", "frac2");
        assertThat(items.get(0).getHotScore()).as("3.7 必须截断成 3，不能 round 成 4").isEqualTo(3);
        assertThat(items.get(1).getHotScore()).isEqualTo(2);
    }

    @Test
    @DisplayName("⚠️ 负 score：Go 的 uint32(负数) 是**未定义行为** ⇒ 本条只记录 RAG 的确定值，不做等价判定")
    void negativeScoreIsUndefinedOnGoSide() {
        redis.opsForZSet().add(HotRedisSnapshotStore.HOT_ARTICLES_KEY, "neg", -1.5);

        List<HotArticleItem> items = service.getHotArticles(10);

        assertThat(items).hasSize(1);
        // ⚠️ Go 侧 uint32(-1.5) 超出 int32 范围 ⇒ Go spec 明确「implementation-specific」
        //    ⇒ **无法用「以 Go 为准」判定哪个值对**。
        //    RAG 侧 (int)(long)(-1.5) = -1（保留符号位）。
        //    ⇒ 本条只钉住「RAG 取了一个确定值」，等价性由台账登记为 UNKNOWN。
        assertThat(items.get(0).getHotScore()).isEqualTo(-1);
    }

    // ====================================================================================
    // ③ 空 key 与 TopK 归一
    // ====================================================================================

    @Test
    @DisplayName("🔴 Go :44-51 —— key 不存在 ⇒ 返回空列表、不抛异常")
    void missingKeyReturnsEmptyListWithoutError() {
        assertThat(redis.hasKey(HotRedisSnapshotStore.HOT_ARTICLES_KEY)).isFalse();

        List<HotArticleItem> items = service.getHotArticles(20);

        assertThat(items).as("key 不存在不是错误").isEmpty();
    }

    @Test
    @DisplayName("🔴 Go :34-40 —— TopK 归一：k<=0 ⇒ 20；k>100 ⇒ 100")
    void topKIsNormalized() {
        for (int i = 1; i <= 120; i++) {
            redis.opsForZSet().add(HotRedisSnapshotStore.HOT_ARTICLES_KEY, "article-" + i, i);
        }

        assertThat(service.getHotArticles(0)).as("k<=0 ⇒ defaultTopK=20").hasSize(20);
        assertThat(service.getHotArticles(-5)).as("负数也是 k<=0 分支").hasSize(20);
        assertThat(service.getHotArticles(1000)).as("k>100 ⇒ maxTopK=100").hasSize(100);
        assertThat(service.getHotArticles(100)).hasSize(100);
    }
}
