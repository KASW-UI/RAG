package com.rag.comment.comment.rpc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.rag.comment.comment.rpc.config.CommentRpcProperties;
import com.rag.comment.comment.rpc.domain.SubjectEntity;
import com.rag.comment.comment.rpc.mapper.CommentContentMapper;
import com.rag.comment.comment.rpc.mapper.CommentIndexMapper;
import com.rag.comment.comment.rpc.mapper.SubjectMapper;
import com.rag.comment.comment.rpc.metrics.CommentRpcMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.net.ServerSocket;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 <b>D6 / D7 的守门网</b> —— 锁住「comment 缓存层的 Redis 指标 label 与降级语义」。
 *
 * <h2>本轮修掉的两条</h2>
 * <p><b>D6 · 指标 label 与打点次数</b> —— Go 的 {@code GetSubjectCache} 有<b>三种</b>结局，
 * 而「Redis 真异常」与「JSON 解析失败」的<b>打点不同</b>：
 * <table border="1">
 *   <caption>Go comment_subject_cache.go 的三种结局 ⇒ 两侧应有的指标</caption>
 *   <tr><th>结局</th><th>Go 出处</th><th>{@code GetSubjectCache}</th>
 *       <th>{@code GetSubjectWithCache}</th><th>合计</th></tr>
 *   <tr><td>命中</td><td>:86-92</td><td>0</td><td>0</td><td><b>0</b></td></tr>
 *   <tr><td>未命中（redis.Nil）</td><td>:83-85</td><td>0</td><td>0</td><td><b>0</b></td></tr>
 *   <tr><td><b>Redis 真异常</b></td><td>:78-82</td>
 *       <td><b>2</b></td><td><b>2</b></td><td><b>4</b></td></tr>
 *   <tr><td><b>JSON 解析失败</b></td><td>:94-98</td>
 *       <td><b>0</b>（Go 注释明说「视作缓存未命中，不记 metric」）</td>
 *       <td><b>2</b></td><td><b>2</b></td></tr>
 * </table>
 * ⚠️ <b>为什么是 2 而不是 1</b>：Go 的读路径<b>读两次 Redis</b> ——
 * 外层一次（{@code :24-30}）+ singleflight 里的<b>双检</b>一次（{@code :37-43}）——
 * 两处的打点判定<b>逐条相同</b> ⇒ 异常路径每个 label 各打 <b>2</b> 次。
 * ⇒ 修复前 RAG 把两种异常<b>合并</b>成一个 catch ⇒ 两者都只打一个 {@code GetSubjectCache}
 * ⇒ label 集合与 Go 不同（多一个 {@code GetSubjectCache}、少一个 {@code GetSubjectWithCache}）。
 *
 * <p><b>D7 · 回填 Redis 失败</b> —— Go 的两处回填都是 {@code _ = c.SetSubjectCache(...)}
 * （{@code comment_subject_cache.go:59}、{@code get_comment_logic.go:176}）⇒ <b>显式忽略错误</b>。
 * 修复前 RAG 的 {@code getSubjectWithCache} <b>直接抛异常</b>
 * ⇒ Redis 挂掉时 Go 照常返回 DB 回源的结果、RAG 整个请求 500。
 *
 * <h2>为什么是真 Redis + 真 PG + 真 Micrometer</h2>
 * <ul>
 *   <li><b>真 Redis</b>（容器）：JSON 解析失败那条必须真的从 Redis 读到坏字节 ——
 *       用 mock 的 {@code opsForValue().get()} 造不出「Redis 返回了但内容坏了」这个形态。</li>
 *   <li><b>真 Redis 传输层失败</b>：把连接指向一个<b>没人监听的端口</b>
 *       ⇒ Lettuce 真去连、真被拒 ⇒ 走的是真异常分支（不是 mock 出来的异常）。</li>
 *   <li><b>真 PG + 真 MyBatis</b>：回源 DB 那条路必须是真 SQL
 *       （本仓纪律：mock 数据层的单测不算数）。</li>
 *   <li><b>真 Micrometer registry</b>：指标名与 tag 是<b>运维契约</b>
 *       ⇒ 断言的是真实注册进去的 meter，不是自己记的计数。</li>
 * </ul>
 *
 * <h2>🧬 变异实测</h2>
 * <ol>
 *   <li>把 {@code readSubjectCacheDetailed} 的两个 catch <b>合并</b>（回到修复前）
 *       ⇒ {@link #jsonParseFailureEmitsOnlyGetSubjectWithCache()} 变红
 *       （多出 {@code action=GetSubjectCache}）。</li>
 *   <li>把回填的 try/catch 去掉（回到修复前）
 *       ⇒ {@link #writeBackFailureDoesNotFailTheRead()} 变红（抛异常而不是返回 DB 结果）。</li>
 * </ol>
 */
@Testcontainers
class CommentCacheRedisMetricGateTest {

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("first_db")
            .withUsername("admin")
            .withPassword("password");

    /** 本类独立账本（不与其它 gate test 的账本撞号）。 */
    private static final String LEDGER = "flyway_schema_history_comment_cache_gate";

    private static final String TARGET_TYPE = "article";
    private static final String TARGET_ID = "a-cache";

    private static JdbcTemplate jdbc;
    private static SubjectMapper subjectMapper;
    private static CommentIndexMapper indexMapper;
    private static CommentContentMapper contentMapper;
    private static StringRedisTemplate redis;
    /** 指向「没人监听的端口」的模板 ⇒ 真连接被拒 ⇒ 真异常分支。 */
    private static StringRedisTemplate deadRedis;
    private static int deadPort;

    @BeforeAll
    static void setUp() throws Exception {
        String url = PG.getJdbcUrl() + "?TimeZone=Asia/Shanghai";
        Flyway flyway = Flyway.configure()
                .dataSource(url, PG.getUsername(), PG.getPassword())
                .locations("classpath:db/migration")
                .schemas("public")
                .table(LEDGER)
                .baselineOnMigrate(true)
                .load();
        flyway.migrate();

        DataSource dataSource = new SimpleDriverDataSource(
                new org.postgresql.Driver(), url, PG.getUsername(), PG.getPassword());
        jdbc = new JdbcTemplate(dataSource);

        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("cache-gate",
                new SpringManagedTransactionFactory(), dataSource));
        configuration.addMapper(SubjectMapper.class);
        configuration.addMapper(CommentIndexMapper.class);
        configuration.addMapper(CommentContentMapper.class);
        SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(configuration);
        SqlSessionTemplate template = new SqlSessionTemplate(factory);
        subjectMapper = template.getMapper(SubjectMapper.class);
        indexMapper = template.getMapper(CommentIndexMapper.class);
        contentMapper = template.getMapper(CommentContentMapper.class);

        redis = templateFor(REDIS.getHost(), REDIS.getMappedPort(6379));
        try (ServerSocket s = new ServerSocket(0)) {
            deadPort = s.getLocalPort();
        }
        deadRedis = templateFor("127.0.0.1", deadPort);

        jdbc.update("DELETE FROM subject WHERE target_type = ? AND target_id = ?", TARGET_TYPE, TARGET_ID);
        jdbc.update("INSERT INTO subject (target_type, target_id, total_count, root_count, state, "
                + "attribute, owner_id) VALUES (?, ?, 3, 1, 0, 0, 2002)",
                TARGET_TYPE, TARGET_ID);
    }

    private static StringRedisTemplate templateFor(String host, int port) {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(host, port));
        factory.afterPropertiesSet();
        StringRedisTemplate t = new StringRedisTemplate(factory);
        t.afterPropertiesSet();
        return t;
    }

    private static CommentCacheService service(StringRedisTemplate template, MeterRegistry registry) {
        return new CommentCacheService(template, subjectMapper, indexMapper, contentMapper,
                new CommentRpcProperties(), new CommentRpcMetrics(registry));
    }

    /** 读某个 action label 上的 redis 错误计数（0 ⇒ 该 label 从未被用过）。 */
    private static double count(MeterRegistry registry, String action, String type) {
        var counter = registry.find("comment_redis_error_total")
                .tag("action", action).tag("type", type).counter();
        return counter == null ? 0.0d : counter.count();
    }

    private static void assertNoRedisErrorMetric(MeterRegistry registry) {
        assertEquals(0.0d, count(registry, "GetSubjectCache", "query"), "GetSubjectCache 不该被打点");
        assertEquals(0.0d, count(registry, "GetSubjectWithCache", "query"), "GetSubjectWithCache 不该被打点");
        assertEquals(0.0d, count(registry, "GetSubjectWithCache", "db_fallback"), "db_fallback 不该被打点");
        assertEquals(0.0d, count(registry, "SetSubjectCache", "set"), "SetSubjectCache 不该被打点");
    }

    // ====================================================================================
    // ① 命中 ⇒ 0 个指标
    // ====================================================================================

    @Test
    @DisplayName("🔴 D6：缓存命中 ⇒ **0 个** redis 错误指标")
    void cacheHitEmitsNoRedisErrorMetric() throws Exception {
        MeterRegistry registry = new SimpleMeterRegistry();
        SubjectEntity subject = subjectMapper.findOneByTarget(TARGET_TYPE, TARGET_ID);
        assertNotNull(subject);

        ObjectMapper cacheMapper = JsonMapper.builder()
                .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        String json = cacheMapper.writeValueAsString(CommentCacheService.SubjectCacheDto.from(subject));
        redis.opsForValue().set(CommentRedisKeys.subjectKey(TARGET_ID), json, Duration.ofMinutes(5));

        SubjectEntity got = service(redis, registry).getSubjectWithCache(TARGET_TYPE, TARGET_ID);

        assertNotNull(got);
        assertEquals(3L, got.getTotalCount(), "应命中缓存里的值");
        assertNoRedisErrorMetric(registry);
    }

    // ====================================================================================
    // ② 未命中 ⇒ 0 个指标（Go: redis.Nil 不打点）
    // ====================================================================================

    @Test
    @DisplayName("🔴 D6：缓存未命中（redis.Nil）⇒ **0 个** redis 错误指标（Go 不打点）")
    void cacheMissEmitsNoRedisErrorMetric() {
        MeterRegistry registry = new SimpleMeterRegistry();
        redis.delete(CommentRedisKeys.subjectKey(TARGET_ID));

        SubjectEntity got = service(redis, registry).getSubjectWithCache(TARGET_TYPE, TARGET_ID);

        assertNotNull(got, "未命中应回源 DB");
        assertEquals(3L, got.getTotalCount());
        assertNoRedisErrorMetric(registry);
    }

    // ====================================================================================
    // ③ JSON 解析失败 ⇒ 只有 GetSubjectWithCache 一个（Go: GetSubjectCache 不打点）
    // ====================================================================================

    @Test
    @DisplayName("🔴 D6：缓存内容 JSON 坏了 ⇒ **只**打 GetSubjectWithCache（Go 侧 GetSubjectCache 不打点）")
    void jsonParseFailureEmitsOnlyGetSubjectWithCache() {
        MeterRegistry registry = new SimpleMeterRegistry();
        redis.opsForValue().set(CommentRedisKeys.subjectKey(TARGET_ID), "{not-json", Duration.ofMinutes(5));

        SubjectEntity got = service(redis, registry).getSubjectWithCache(TARGET_TYPE, TARGET_ID);

        assertNotNull(got, "解析失败按「未命中」处理 ⇒ 仍应回源 DB");
        assertEquals(3L, got.getTotalCount());
        assertEquals(0.0d, count(registry, "GetSubjectCache", "query"),
                "Go comment_subject_cache.go:94-98 明确写「JSON 解析失败不记 metric」"
                        + " ⇒ GetSubjectCache 这个 label **不该**出现");
        assertEquals(2.0d, count(registry, "GetSubjectWithCache", "query"),
                "Go :26-29（外层读）与 :39-42（singleflight 双检）各打一次 ⇒ 共 2 次");
    }

    // ====================================================================================
    // ④ Redis 真异常 ⇒ 两个都打
    // ====================================================================================

    @Test
    @DisplayName("🔴 D6：Redis **真异常** ⇒ 打 **两个**（GetSubjectCache + GetSubjectWithCache）")
    void redisTransportFailureEmitsBothLabels() {
        MeterRegistry registry = new SimpleMeterRegistry();

        SubjectEntity got = service(deadRedis, registry).getSubjectWithCache(TARGET_TYPE, TARGET_ID);

        assertNotNull(got, "Redis 挂掉时 Go 侧仍然回源 DB");
        assertEquals(2.0d, count(registry, "GetSubjectCache", "query"),
                "Go :78-82：rdb.Get 真异常 ⇒ GetSubjectCache（query）；外层读 + 双检 ⇒ 共 2 次");
        assertEquals(2.0d, count(registry, "GetSubjectWithCache", "query"),
                "Go :26-29（外层读）与 :39-42（singleflight 双检）各打一次 ⇒ 共 2 次");
    }

    // ====================================================================================
    // ⑤ D7：回填失败 ⇒ 读路径照常成功（Go 的 `_ =`）
    // ====================================================================================

    @Test
    @DisplayName("🔴 D7：回填 Redis 失败 ⇒ **读路径照常返回**（Go 的 `_ = c.SetSubjectCache(...)`）")
    void writeBackFailureDoesNotFailTheRead() {
        MeterRegistry registry = new SimpleMeterRegistry();

        // deadRedis：读失败 + 回填也失败
        SubjectEntity got = service(deadRedis, registry).getSubjectWithCache(TARGET_TYPE, TARGET_ID);

        assertNotNull(got,
                "Go comment_subject_cache.go:59 是 `_ = c.SetSubjectCache(...)` ⇒ 回填失败被忽略"
                        + " ⇒ 读路径必须照常返回 DB 回源的结果，而不是把异常抛出去");
        assertEquals(3L, got.getTotalCount());
        assertTrue(count(registry, "SetSubjectCache", "set") >= 1.0d,
                "Go :124-127：回填失败仍要打 SetSubjectCache（set）");
    }
}
