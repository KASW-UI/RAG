package com.rag.comment.comment.rpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.comment.comment.rpc.mapper.CommentDomainFactOutboxMapper;
import com.rag.comment.comment.rpc.mapper.CommentFactStreamMapper;
import com.rag.comment.comment.rpc.service.CommentDomainFactService;
import com.rag.comment.comment.rpc.support.RealContainerHostPort;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 <b>D3 的守门网</b> —— 锁住「事实载荷的 {@code event_time} 必须归一化到 UTC」。
 *
 * <h2>本轮修掉的漂移</h2>
 * <p><b>Go 事实</b>（{@code service/comment/rpc/internal/model/domain_fact.go:95-97}）：
 * <pre>
 *   if fact.EventTime.IsZero() { fact.EventTime = time.Now().UTC() }
 *   fact.EventTime = fact.EventTime.UTC()        // ← ← ← 无条件归一化
 *   fact.OccurredAt = fact.EventTime
 * </pre>
 * <p><b>修复前 RAG 现状</b>（{@code CommentDomainFactService.append()}）：
 * 只有第一步（{@code null ⇒ now}），<b>漏了第二步</b>
 * ⇒ 调用方传一个带 {@code +08:00} 偏移的 {@code event_time} 时，
 * Go 的 payload / envelope 是 {@code "2026-10-24T10:00:00Z"}，
 * RAG 是原样的 {@code "2026-10-24T18:00:00+08:00"} ⇒ <b>信封字节漂移</b>。
 * <p>⚠️ 当前 3 个调用方（{@code CommentTransactionalService:234/389/542}）都传 UTC
 * ⇒ <b>当前不可观察</b>；但 {@code append(CommentFact)} 是 <b>public API</b>，
 * 第 4 个调用方传本地偏移就会漂 ⇒ 属**预防性**对齐。
 *
 * <h2>为什么必须真 PG + 真 MyBatis</h2>
 * <p>本类要验的是<b>落库的那份字节</b>（payload jsonb + delivery_envelope jsonb）
 * ⇒ 必须真的走 {@code INSERT … ON CONFLICT … RETURNING} 与 jsonb 列，
 * mock 数据层测不到任何东西（AGENTS.md §3.3）。
 *
 * <h2>🧬 变异实测</h2>
 * <p>把 {@code append()} 里的 {@code normalizeEventTimeToUtc(fact.eventTime)} 改回
 * {@code (fact.eventTime == null) ? now() : fact.eventTime}
 * ⇒ {@link #eventTimeWithOffsetIsNormalizedToUtcInPayloadAndEnvelope()} 变红
 * ⇒ 守门网不是假绿。
 */
@Testcontainers
class CommentFactEventTimeGateTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("first_db")
            .withUsername("admin")
            .withPassword("password")
            .withCreateContainerCmdModifier(RealContainerHostPort.bindPg(RealContainerHostPort.COMMENT_FACT_EVENT_TIME_GATE));

    /** 本类独立账本（不与其它 gate test 的账本撞号）。 */
    private static final String LEDGER = "flyway_schema_history_comment_eventtime_gate";

    private static JdbcTemplate jdbc;
    private static CommentDomainFactService factService;

    @BeforeAll
    static void setUp() {
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
        configuration.setEnvironment(new Environment("eventtime-gate",
                new SpringManagedTransactionFactory(), dataSource));
        configuration.addMapper(CommentDomainFactOutboxMapper.class);
        configuration.addMapper(CommentFactStreamMapper.class);
        SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(configuration);
        SqlSessionTemplate template = new SqlSessionTemplate(factory);

        factService = new CommentDomainFactService(
                template.getMapper(CommentDomainFactOutboxMapper.class),
                template.getMapper(CommentFactStreamMapper.class),
                new ObjectMapper());
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM comment_domain_fact_outbox");
        jdbc.update("DELETE FROM comment_fact_stream");
    }

    /** 造一个最小可用的 fact（Go 的必填四项 + 业务字段）。 */
    private static CommentDomainFactService.CommentFact fact(String aggregateId, String eventTime) {
        CommentDomainFactService.CommentFact f = new CommentDomainFactService.CommentFact();
        f.eventId = "evt-" + aggregateId;
        f.eventType = "community.comment.created";
        f.aggregateId = aggregateId;
        f.operationId = "op-" + aggregateId;
        f.subjectRef = "rtw.identity/platform/2002";
        f.operatorRef = "rtw.identity/platform/1001";
        f.targetType = "article";
        f.targetId = "a-diff";
        f.operation = "created";
        f.sourceRef = "src-" + aggregateId;
        f.commentId = "2113939338147643392";
        f.eventTime = eventTime;
        return f;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readJson(String column, String aggregateId) {
        String raw = jdbc.queryForObject(
                "SELECT " + column + "::text FROM comment_domain_fact_outbox WHERE aggregate_id = ?",
                String.class, aggregateId);
        try {
            return new ObjectMapper().readValue(raw, Map.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("🔴 D3：event_time 带 +08:00 偏移 ⇒ 载荷与信封**全部**归一化到 UTC")
    void eventTimeWithOffsetIsNormalizedToUtcInPayloadAndEnvelope() {
        factService.append(fact("agg-d3-1", "2026-10-24T18:00:00+08:00"));

        Map<String, Object> payload = readJson("payload", "agg-d3-1");
        Map<String, Object> envelope = readJson("delivery_envelope", "agg-d3-1");

        assertEquals("2026-10-24T10:00:00Z", payload.get("event_time"),
                "Go domain_fact.go:96 fact.EventTime = fact.EventTime.UTC() ⇒ 载荷必须是 UTC");
        assertEquals("2026-10-24T10:00:00Z", payload.get("occurred_at"),
                "Go domain_fact.go:97 fact.OccurredAt = fact.EventTime（归一化之后）");
        assertEquals("2026-10-24T10:00:00Z", envelope.get("occurred_at"),
                "Go domain_fact.go:132 OccurredAt: fact.OccurredAt.Format(time.RFC3339Nano)");
        assertNotEquals("2026-10-24T18:00:00+08:00", payload.get("event_time"),
                "不能保留调用方给的本地偏移");
    }

    @Test
    @DisplayName("🔴 D3：event_time 本来就是 UTC ⇒ **原样不动**（不许被二次改写）")
    void eventTimeAlreadyUtcIsUnchanged() {
        factService.append(fact("agg-d3-2", "2026-10-24T10:00:00Z"));

        Map<String, Object> payload = readJson("payload", "agg-d3-2");
        assertEquals("2026-10-24T10:00:00Z", payload.get("event_time"));
        assertEquals("2026-10-24T10:00:00Z", payload.get("occurred_at"));
    }

    @Test
    @DisplayName("🔴 D3：纳秒精度保留、末尾 0 被去掉（Go RFC3339Nano 语义）")
    void nanoPrecisionMatchesGoRfc3339Nano() {
        factService.append(fact("agg-d3-3", "2026-10-24T18:00:00.123456789+08:00"));

        Map<String, Object> payload = readJson("payload", "agg-d3-3");
        assertEquals("2026-10-24T10:00:00.123456789Z", payload.get("event_time"));
    }

    @Test
    @DisplayName("🔴 D3：event_time 为 null ⇒ 取当前时刻（Go 的 IsZero 分支）")
    void nullEventTimeBecomesNow() {
        factService.append(fact("agg-d3-4", null));

        Map<String, Object> payload = readJson("payload", "agg-d3-4");
        String eventTime = String.valueOf(payload.get("event_time"));
        assertTrue(eventTime.endsWith("Z"),
                "Go 用 time.Now().UTC() ⇒ 必须以 Z 结尾（不能落到 JVM 默认时区）⇒ 实际 " + eventTime);
        assertEquals(eventTime, payload.get("occurred_at"), "Go: OccurredAt = EventTime");
        Instant parsed = Instant.parse(eventTime);
        assertTrue(Math.abs(ChronoUnit.SECONDS.between(parsed, Instant.now())) < 120,
                "应当接近当前时刻 ⇒ 实际 " + eventTime);
    }

    @Test
    @DisplayName("🔴 D3：无法解析的 event_time ⇒ **原样保留**（不新增 Go 没有的失败模式）")
    void malformedEventTimeIsKeptAsIs() {
        factService.append(fact("agg-d3-5", "not-a-time"));

        Map<String, Object> payload = readJson("payload", "agg-d3-5");
        assertEquals("not-a-time", payload.get("event_time"),
                "Go 的 EventTime 是 time.Time（不可能非法）⇒ 抛异常等于新增一个 Go 没有的失败模式"
                        + "（会回滚事务）。⇒ 原样保留，交给投递侧 RFC3339 校验判 BLOCKED。");
    }
}
