package com.rag.comment.comment.rpc;

import com.rag.comment.comment.rpc.support.RealContainerHostPort;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 🔴 真 PostgreSQL 上的 DDL / 事实流守门测试 —— 对应 Go 侧那三个
 * <b>默认 t.Skip 的</b> {@code _test.go}（domain_fact_test.go / fact_process_fixture_test.go /
 * fact_delivery_logging_test.go）。
 *
 * <h2>为什么必须是「真 PG 且默认跑」</h2>
 * <p>Go 侧那三个测试在没有 DSN 时 {@code t.Skip} ⇒ <b>默认从不执行</b>，
 * 于是以下每一条在本仓库里都长期处于「没人验证过」的状态：
 * <ul>
 *   <li>Flyway 能不能真的跑完 V1..V8（尤其 V6 的 {@code NOT VALID CHECK} 与
 *       V8 的 {@code DO $$ … $$} + plpgsql 触发器 —— 这两处一旦语法不被接受，
 *       <b>整批迁移失败</b>，而单元测试全绿）；</li>
 *   <li>{@code INSERT … ON CONFLICT … RETURNING} 能不能被正确表达
 *       （MyBatis 的 {@code @Insert} 走 executeUpdate 拿不到 RETURNING，本域用
 *       {@code @Select} 承载 —— 见 {@code CommentFactStreamMapper#bumpVersion}）；</li>
 *   <li>jsonb 列与位运算的实际行为；</li>
 *   <li>CHECK 约束是否真的拦得住「信封三件套不全」的事实行。</li>
 * </ul>
 * H2 替代不了：ON CONFLICT 的冲突目标解析、部分索引、jsonb、plpgsql 在 H2 上都不是同一套语义
 * ⇒ 「把要验证的东西换没了」。
 *
 * <h2>为什么不用 {@code @SpringBootTest}</h2>
 * <p>本测试只验证「迁移脚本 + SQL 语义」，不需要 Redis / Kafka / Nacos。
 * 若走 Spring 上下文，就得为了让 {@code CommentCacheService}(要 StringRedisTemplate) 与
 * {@code CommentAuditPublisher}(要 KafkaTemplate) 能注入而保留那些自动配置，
 * 于是整个上下文会尝试连 Redis/Kafka/Nacos —— 把一个「DDL 守门测试」变成「全栈启动测试」。
 * ⇒ 这里直接用与生产同版本的 Flyway 引擎 + 裸 JDBC 驱动迁移，聚焦且无外部依赖。
 */
@Testcontainers
class CommentSchemaPgGateTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("first_db")
            .withUsername("admin")
            .withPassword("password")
            .withCreateContainerCmdModifier(RealContainerHostPort.bindPg(RealContainerHostPort.COMMENT_SCHEMA_PG_GATE));

    /** 与生产同一条 DSN 形态（含 TimeZone=Asia/Shanghai，对齐 Go 的 comment.yaml:9）。 */
    private static String jdbcUrl() {
        return PG.getJdbcUrl() + "?TimeZone=Asia/Shanghai";
    }

    private static JdbcTemplate jdbc;

    /** 本域独立账本：验证「改了表名就不与其它模块撞号」这条策略本身也成立。 */
    private static final String LEDGER = "flyway_schema_history_comment_it";

    @BeforeAll
    static void migrate() {
        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl(), PG.getUsername(), PG.getPassword())
                .locations("classpath:db/migration")
                .schemas("public")
                .table(LEDGER)
                .baselineOnMigrate(true)
                .load();
        flyway.migrate();

        jdbc = new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
                jdbcUrl(), PG.getUsername(), PG.getPassword()));
    }

    // ====================================================================================
    // ① 迁移：7 张表 + 约束 + 索引 + 触发器 + 函数
    // ====================================================================================

    @Test
    @DisplayName("Flyway 把 V1..V8 全部应用成功，7 张表都在")
    void allSevenTablesExist() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema='public'", String.class);
        assertThat(tables).contains(
                "subject", "comment_index", "comment_content", "comment_like", "report_record",
                "comment_domain_fact_outbox", "comment_fact_stream");

        List<String> applied = jdbc.queryForList(
                "SELECT version FROM " + LEDGER + " WHERE success ORDER BY installed_rank",
                String.class);
        assertThat(applied).containsExactly("1", "2", "3", "4", "5", "6", "7", "8");
    }

    @Test
    @DisplayName("🔴 V8 的 DO $$ 块与 plpgsql 触发器真的被创建（不是被静默跳过）")
    void plpgsqlTriggerWasCreatedByFlyway() {
        Integer triggerCount = jdbc.queryForObject(
                "SELECT count(*) FROM pg_trigger WHERE tgname = 'comment_fact_identity_immutable'", Integer.class);
        assertThat(triggerCount).isEqualTo(1);

        Integer functionCount = jdbc.queryForObject(
                "SELECT count(*) FROM pg_proc WHERE proname = 'reject_comment_fact_identity_change'", Integer.class);
        assertThat(functionCount).isEqualTo(1);
    }

    @Test
    @DisplayName("🔴 NOT VALID CHECK 约束存在且拦得住「信封三件套不全」")
    void dcWireCheckConstraintBlocksIncompleteFact() {
        // 先放一条完整事实（后面要靠它验证触发器）
        jdbc.update("""
                INSERT INTO comment_domain_fact_outbox
                    (event_id, event_type, payload, aggregate_id, fact_version, delivery_envelope)
                VALUES ('rtw.comment.1.created', 'community.comment.created', '{}'::jsonb,
                        '1', 1, '{"event_id":"x"}'::jsonb)
                """);

        // envelope 缺 delivery_envelope ⇒ CHECK 必须拒绝
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO comment_domain_fact_outbox (event_id, event_type, payload, aggregate_id, fact_version)
                VALUES ('rtw.comment.2.created', 'community.comment.created', '{}'::jsonb, '2', 1)
                """))
                .hasMessageContaining("comment_new_fact_has_dc_wire");

        jdbc.update("DELETE FROM comment_domain_fact_outbox WHERE event_id = 'rtw.comment.1.created'");
    }

    @Test
    @DisplayName("🔴 信封不可变触发器：改 delivery_status 放行，改 payload 抛异常")
    void identityTriggerAllowsMetadataUpdateButRejectsPayloadRewrite() {
        jdbc.update("""
                INSERT INTO comment_domain_fact_outbox
                    (event_id, event_type, payload, aggregate_id, fact_version, delivery_envelope)
                VALUES ('rtw.comment.7.created', 'community.comment.created',
                        '{"a":1}'::jsonb, '7', 1, '{"event_id":"x"}'::jsonb)
                """);
        try {
            // 投递元数据更新 ⇒ 正常（真实投递路径走的就是这条）
            jdbc.update("""
                    UPDATE comment_domain_fact_outbox
                    SET delivery_status = 2, retry_count = retry_count + 1
                    WHERE event_id = 'rtw.comment.7.created'
                    """);
            assertThat(jdbc.queryForObject(
                    "SELECT retry_count FROM comment_domain_fact_outbox WHERE event_id='rtw.comment.7.created'",
                    Integer.class)).isEqualTo(1);

            // 改 payload ⇒ 必须被拒
            assertThatThrownBy(() -> jdbc.update(
                    "UPDATE comment_domain_fact_outbox SET payload = '{\"a\":2}'::jsonb "
                            + "WHERE event_id = 'rtw.comment.7.created'"))
                    .hasMessageContaining("comment fact envelope is immutable");
        } finally {
            jdbc.update("DELETE FROM comment_domain_fact_outbox WHERE event_id = 'rtw.comment.7.created'");
        }
    }

    @Test
    @DisplayName("部分索引 idx_comment_legacy_fact_aggregate 只覆盖 delivery_envelope IS NULL 的行")
    void legacyPartialIndexPredicate() {
        String predicate = jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes "
                        + "WHERE indexname = 'idx_comment_legacy_fact_aggregate'", String.class);
        assertThat(predicate).contains("payload ->> 'aggregate_id'");
        assertThat(predicate.toLowerCase()).contains("where");
    }

    // ====================================================================================
    // ② 列类型：与 Go 的 GORM AutoMigrate 实测结果一致
    // ====================================================================================

    @Test
    @DisplayName("🔴 delivery_status / retry_count 必须是 integer（GORM int32 的产物，不是 003 里的 smallint）")
    void deliveryColumnsAreIntegerNotSmallint() {
        Map<String, Object> types = columnTypes(
                "SELECT column_name, data_type FROM information_schema.columns "
                        + "WHERE table_name='comment_domain_fact_outbox' AND column_name IN "
                        + "('delivery_status','retry_count','technical_offset')", "column_name");
        assertThat(types).containsEntry("delivery_status", "integer");
        assertThat(types).containsEntry("retry_count", "integer");
        assertThat(types).containsEntry("technical_offset", "bigint");
    }

    @Test
    @DisplayName("🔴 业务表时间列是 timestamptz（GORM postgres 驱动的产物，不是 timestamp）")
    void businessTimeColumnsAreTimestamptz() {
        Map<String, Object> types = columnTypes(
                "SELECT table_name || '.' || column_name AS col, data_type FROM information_schema.columns "
                        + "WHERE table_schema='public' AND data_type IN ('timestamp with time zone','timestamp without time zone') "
                        + "AND table_name IN ('subject','comment_index','comment_content','comment_like')",
                "col");
        assertThat(types).isNotEmpty();
        assertThat(types.values()).allMatch(v -> "timestamp with time zone".equals(v));
    }

    @Test
    @DisplayName("🔴 jsonb 列类型正确：comment_content.meta + outbox 的 payload/delivery_envelope")
    void jsonbColumnsAreJsonb() {
        Map<String, Object> types = columnTypes(
                "SELECT table_name || '.' || column_name AS col, data_type FROM information_schema.columns "
                        + "WHERE table_schema='public' AND data_type='jsonb'", "col");
        assertThat(types).containsKeys("comment_content.meta",
                "comment_domain_fact_outbox.payload", "comment_domain_fact_outbox.delivery_envelope");
    }

    // ====================================================================================
    // ③ 唯一约束 = 并发正确性载体
    // ====================================================================================

    @Test
    @DisplayName("comment_like 唯一键 (user_id, comment_id) 冲突时 ON CONFLICT DO NOTHING 静默跳过")
    void commentLikeUniqueKeyEnablesLockAnchorInsert() {
        jdbc.update("""
                INSERT INTO comment_like (user_id, comment_id, target_type, target_id, state)
                VALUES (7, 99, 'article', 'a1', 0)
                """);
        // 锁锚的第二次插入必须静默成功（而不是报错）—— 这是并发首次点赞的正确性载体
        int affected = jdbc.update("""
                INSERT INTO comment_like (user_id, comment_id, target_type, target_id, state)
                VALUES (7, 99, 'article', 'a1', 0)
                ON CONFLICT DO NOTHING
                """);
        assertThat(affected).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM comment_like WHERE user_id=7 AND comment_id=99", Integer.class))
                .isEqualTo(1);
        jdbc.update("DELETE FROM comment_like WHERE user_id=7 AND comment_id=99");
    }

    @Test
    @DisplayName("report_record 唯一键 + ON CONFLICT DO NOTHING（不带 Columns）静默吞掉重复举报")
    void duplicateReportIsSilentlyIgnored() {
        jdbc.update("INSERT INTO report_record (user_id, comment_id, target_type, target_id, reason) "
                + "VALUES (7, 99, 'article', 'a1', 1)");
        int affected = jdbc.update("INSERT INTO report_record (user_id, comment_id, target_type, target_id, reason) "
                + "VALUES (7, 99, 'article', 'a1', 1) ON CONFLICT DO NOTHING");
        assertThat(affected).isZero();
        // 接口仍返回 success=true（Go report_comment_logic.go:97-99）
        assertThat(jdbc.queryForObject("SELECT count(*) FROM report_record WHERE user_id=7", Integer.class))
                .isEqualTo(1);
        jdbc.update("DELETE FROM report_record WHERE user_id=7");
    }

    @Test
    @DisplayName("🔴 subject 冲突目标固定为 (target_type, target_id)，计数自增逐字等价 Go")
    void subjectUpsertIncrementsCounts() {
        jdbc.update("""
                INSERT INTO subject (target_type, target_id, total_count, root_count, state, attribute, owner_id)
                VALUES ('article', 'upsert-test', 1, 1, 0, 0, 5)
                """);
        // 根评论：total +1 且 root +1
        jdbc.update("""
                INSERT INTO subject (target_type, target_id, total_count, root_count, state, attribute, owner_id)
                VALUES ('article', 'upsert-test', 1, 1, 0, 0, 5)
                ON CONFLICT (target_type, target_id) DO UPDATE SET
                    total_count = subject.total_count + 1, root_count = subject.root_count + 1
                """);
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT total_count, root_count FROM subject WHERE target_type='article' AND target_id='upsert-test'");
        assertThat(((Number) row.get("total_count")).longValue()).isEqualTo(2L);
        assertThat(((Number) row.get("root_count")).longValue()).isEqualTo(2L);

        // 回复（rootCount=0）：只加 total
        jdbc.update("""
                INSERT INTO subject (target_type, target_id, total_count, root_count, state, attribute, owner_id)
                VALUES ('article', 'upsert-test', 1, 0, 0, 0, 5)
                ON CONFLICT (target_type, target_id) DO UPDATE SET total_count = subject.total_count + 1
                """);
        row = jdbc.queryForMap(
                "SELECT total_count, root_count FROM subject WHERE target_type='article' AND target_id='upsert-test'");
        assertThat(((Number) row.get("total_count")).longValue()).isEqualTo(3L);
        assertThat(((Number) row.get("root_count")).longValue()).isEqualTo(2L);

        jdbc.update("DELETE FROM subject WHERE target_id='upsert-test'");
    }

    @Test
    @DisplayName("🔴 comment_fact_stream 的原子取号：ON CONFLICT … RETURNING 逐条递增")
    void factStreamVersionAllocationIsMonotonic() {
        for (int i = 1; i <= 3; i++) {
            Long version = jdbc.queryForObject("""
                    INSERT INTO comment_fact_stream (aggregate_id, version) VALUES ('agg-x', 1)
                    ON CONFLICT (aggregate_id) DO UPDATE SET version = comment_fact_stream.version + 1
                    RETURNING version
                    """, Long.class);
            assertThat(version).isEqualTo((long) i);
        }
        jdbc.update("DELETE FROM comment_fact_stream WHERE aggregate_id='agg-x'");
    }

    @Test
    @DisplayName("🔴 fact_stream 版本上界 CHECK 生效（2^53-1）")
    void factStreamVersionRangeCheck() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO comment_fact_stream (aggregate_id, version) VALUES ('agg-y', 0)"))
                .hasMessageContaining("comment_fact_stream_version_range");
    }

    // ====================================================================================
    // ④ jsonb 往返 + 位运算
    // ====================================================================================

    @Test
    @DisplayName("jsonb 列往返：写入字符串原样读回（PG 会规范化空白与键序）")
    void jsonbRoundTrips() {
        jdbc.update("INSERT INTO comment_content (comment_id, content, meta) "
                + "VALUES (1001, 'hello', '{\"b\":1,\"a\":2}'::jsonb)");
        String meta = jdbc.queryForObject(
                "SELECT meta::text FROM comment_content WHERE comment_id=1001", String.class);
        // jsonb 会规范化键序 ⇒ 这里断言「语义相等」而不是「字节相等」
        assertThat(meta).contains("\"a\"").contains("\"b\"");
        assertThat(meta.indexOf("\"a\"")).isLessThan(meta.indexOf("\"b\""));
        jdbc.update("DELETE FROM comment_content WHERE comment_id=1001");
    }

    @Test
    @DisplayName("🔴 attribute 位运算：置位用 |，清位用 & ~（用 - 会借位翻低位）")
    void attributeBitOperations() {
        jdbc.update("INSERT INTO comment_index (id, target_type, target_id, user_id) "
                + "VALUES (2001, 'article', 'a1', 7)");
        // 精选：置第 3 位
        jdbc.update("UPDATE comment_index SET attribute = attribute | 8 WHERE id=2001");
        assertThat(attr(2001)).isEqualTo(8L);
        // 作者回复：置第 1 位
        jdbc.update("UPDATE comment_index SET attribute = attribute | 2 WHERE id=2001");
        assertThat(attr(2001)).isEqualTo(10L);
        // 取消精选：清第 3 位
        jdbc.update("UPDATE comment_index SET attribute = attribute & ~8 WHERE id=2001");
        assertThat(attr(2001)).isEqualTo(2L);
        // 再取消置顶：清第 2 位
        jdbc.update("UPDATE comment_index SET attribute = attribute & ~2 WHERE id=2001");
        assertThat(attr(2001)).isZero();
        jdbc.update("DELETE FROM comment_index WHERE id=2001");
    }

    @Test
    @DisplayName("🔴 点赞差量：0 差值不写列，非 0 才写")
    void likeCountDelta() {
        jdbc.update("INSERT INTO comment_index (id, target_type, target_id, user_id) "
                + "VALUES (2002, 'article', 'a1', 7)");
        jdbc.update("UPDATE comment_index SET like_count = like_count + 1 WHERE id=2002");
        assertThat(jdbc.queryForObject("SELECT like_count FROM comment_index WHERE id=2002", Long.class))
                .isEqualTo(1L);
        // unlike
        jdbc.update("UPDATE comment_index SET like_count = like_count + -1 WHERE id=2002");
        assertThat(jdbc.queryForObject("SELECT like_count FROM comment_index WHERE id=2002", Long.class))
                .isZero();
        jdbc.update("DELETE FROM comment_index WHERE id=2002");
    }

    @Test
    @DisplayName("🔴 删除只扣一次计数：state != 2 才动 total_count（幂等删除）")
    void softDeleteIsIdempotentForCounters() {
        jdbc.update("INSERT INTO comment_index (id, target_type, target_id, user_id, state) "
                + "VALUES (2003, 'article', 'a1', 7, 0)");
        jdbc.update("INSERT INTO subject (target_type, target_id, total_count, root_count, state, attribute, owner_id) "
                + "VALUES ('article', 'del-test', 1, 1, 0, 0, 7)");

        // 首次删除
        jdbc.update("UPDATE comment_index SET state = 2 WHERE id=2003");
        jdbc.update("UPDATE subject SET total_count = total_count - 1, root_count = root_count - 1 "
                + "WHERE target_type='article' AND target_id='del-test'");
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT total_count FROM subject WHERE target_id='del-test'");
        assertThat(((Number) row.get("total_count")).longValue()).isZero();

        // 第二次删除：Go 的 if comment.State != 2 会跳过 ⇒ 这里断言计数不再变
        int state = jdbc.queryForObject("SELECT state FROM comment_index WHERE id=2003", Integer.class);
        assertThat(state).isEqualTo(2);
        assertThat(((Number) jdbc.queryForMap(
                "SELECT total_count FROM subject WHERE target_id='del-test'")
                .get("total_count")).longValue()).isZero();

        jdbc.update("DELETE FROM subject WHERE target_id='del-test'");
        jdbc.update("DELETE FROM comment_index WHERE id=2003");
    }

    @Test
    @DisplayName("行级悲观锁 SELECT … FOR UPDATE 在真 PG 上可执行")
    void forUpdateLockWorks() {
        jdbc.update("INSERT INTO comment_index (id, target_type, target_id, user_id) "
                + "VALUES (2004, 'article', 'a1', 7)");
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM comment_index WHERE id = 2004 FOR UPDATE");
        assertThat(rows).hasSize(1);
        jdbc.update("DELETE FROM comment_index WHERE id=2004");
    }

    /** 两列 (key, value) 查询的结果集 → Map（{@code queryForMap(sql, Map.class)} 不是合法用法）。 */
    private static Map<String, Object> columnTypes(String sql, String keyColumn) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        for (Map<String, Object> row : jdbc.queryForList(sql)) {
            out.put(String.valueOf(row.get(keyColumn)), row.get("data_type"));
        }
        return out;
    }

    private long attr(long id) {
        return jdbc.queryForObject("SELECT attribute FROM comment_index WHERE id=?", Long.class, id);
    }
}