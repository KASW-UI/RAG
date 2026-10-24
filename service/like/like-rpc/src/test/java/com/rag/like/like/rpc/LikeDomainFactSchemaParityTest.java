package com.rag.like.like.rpc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 DRIFT-LIKE-017 守门测试 —— {@code like_domain_fact_outbox} 必须与 Go 的实际 schema 对齐。
 *
 * <h2>为什么必须查真 PG 的 information_schema / pg_catalog</h2>
 * 本类验证的全部内容就是<b>schema 本身</b>（列类型、CHECK、索引、触发器），
 * 这些东西<b>只存在于数据库里</b>：读 DDL 文件只能证明「脚本写了什么」，
 * 读实体注解只能证明「Java 声明了什么」——
 * <b>两者都不能证明「迁移真的执行了、真的建出来了」</b>。
 *
 * <p>⇒ 直接查 {@code information_schema.columns} / {@code pg_constraint} /
 * {@code pg_indexes} / {@code pg_trigger}，并<b>真的触发一次</b>看约束是否发作。
 *
 * <h2>逐项 Go 依据（全部给到消费点）</h2>
 * <ol>
 *   <li><b>列类型 integer 而非 bigint</b>：
 *       {@code gorm@v1.31.2/schema/field.go:330-341} 给 int32 算 {@code Size = 32}
 *       → {@code gorm.io/driver/postgres@v1.6.0/postgres.go:231-234}
 *       {@code case size <= 32: return "integer"}；</li>
 *   <li>{@code like_new_fact_has_dc_wire} CHECK：Go {@code migrations/002_like_dc_wire.sql}（NOT VALID）；</li>
 *   <li>{@code idx_like_legacy_fact_subject_target} 部分索引：Go {@code 002}；</li>
 *   <li>{@code idx_like_fact_delivery} 复合索引：Go {@code 003}；</li>
 *   <li>{@code like_domain_fact_identity_immutable} 触发器：Go {@code 003}（BEFORE UPDATE，非 constraint trigger）；</li>
 *   <li>{@code like_fact_stream_version_range} CHECK：Go {@code 002}。</li>
 * </ol>
 *
 * <p><b>可证伪性</b>：删掉 {@code V6__align_like_domain_fact_outbox_with_go.sql}
 * 再跑本类，①②③④⑤⑥ <b>应当逐条红</b>。
 */
@SpringBootTest(
        classes = LikeRpcApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "grpc.server.port=0",
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "grpc.client.message-rpc.address=static://127.0.0.1:1",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN",
                "spring.kafka.bootstrap-servers=127.0.0.1:1",
                "spring.data.redis.host=127.0.0.1",
                "spring.data.redis.port=1"
        })
@Testcontainers
@org.testcontainers.junit.jupiter.EnabledIfDockerAvailable
class LikeDomainFactSchemaParityTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("like_rpc_ddl_it")
                    .withUsername("rag")
                    .withPassword("rag");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private JdbcTemplate jdbcTemplate;

    private static final String T = "like_domain_fact_outbox";

    // ==================================================================
    // ① 列类型：Go 的 int32 ⇒ integer（不是 bigint）
    // ==================================================================

    @Test
    @DisplayName("🔴 Go: delivery_status / retry_count 是 integer（V4 曾错写成 bigint）")
    void deliveryColumnsAreIntegerNotBigint() {
        assertEquals("integer", columnType("delivery_status"),
                "Go 的 DeliveryStatus 是 int32 且无 type tag ⇒ AutoMigrate 建出 integer。"
                        + "消费点：gorm schema/field.go:330-341（int32 ⇒ Size=32）"
                        + "→ driver/postgres@v1.6.0 postgres.go:231-234（size<=32 ⇒ integer）");
        assertEquals("integer", columnType("retry_count"),
                "同上，RetryCount 也是 int32");
    }

    @Test
    @DisplayName("对照：其余列类型与 Go 一致（technical_offset 仍是 bigint）")
    void otherColumnTypesUnchanged() {
        // int64 ⇒ Size=64 ⇒ bigint，不能被误改成 integer
        assertEquals("bigint", columnType("technical_offset"), "Go 的 TechnicalOffset 是 int64");
        assertEquals("bigint", columnType("fact_version"), "Go 的 FactVersion 是 *int64");
        assertEquals("jsonb", columnType("payload"), "Go tag 是 type:jsonb");
        assertEquals("jsonb", columnType("delivery_envelope"), "Go tag 是 type:jsonb");
        assertEquals("timestamp with time zone", columnType("delivered_at"),
                "Go 的 *time.Time ⇒ timestamptz");
        assertEquals("character varying", columnType("event_id"), "Go tag 是 type:varchar(128)");
        assertEquals("character", columnType("technical_input_hash"), "Go tag 是 type:char(64)");
    }

    // ==================================================================
    // ② like_new_fact_has_dc_wire CHECK（Go 002）
    // ==================================================================

    @Test
    @DisplayName("🔴 Go: like_new_fact_has_dc_wire CHECK 存在，且新写入被它拦得住")
    void newFactMustCarryDcWire() {
        assertNotNull(constraintDef("like_new_fact_has_dc_wire"), "Go 002 里有这个 CHECK，RAG 侧此前没有");

        jdbcTemplate.update("DELETE FROM " + T);
        // 三件套齐全 ⇒ 通过
        insertFact("ok-1", "agg-1", 1L, "{\"v\":1}");
        // 缺 delivery_envelope ⇒ 被拒
        assertThrows(Exception.class, () -> insertFact("bad-1", "agg-2", 1L, null),
                "Go 002 的语义：新事实必须带完整 DC 线（NOT VALID 只放行存量行，不放行新 INSERT）");
        // 缺 fact_version ⇒ 被拒
        assertThrows(Exception.class, () -> {
            jdbcTemplate.update("""
                    INSERT INTO like_domain_fact_outbox
                        (event_id, event_type, payload, aggregate_id, delivery_envelope, delivery_status,
                         retry_count, technical_receipt_id, technical_input_hash, technical_offset, created_at)
                    VALUES ('bad-2','t','{}'::jsonb,'agg-3','{"v":1}',0,0,'','',0, now())
                    """);
        }, "缺 fact_version 同样应被 CHECK 拒");
    }

    // ==================================================================
    // ③ legacy 部分索引（Go 002）
    // ==================================================================

    @Test
    @DisplayName("🔴 Go: idx_like_legacy_fact_subject_target 部分索引存在")
    void legacyPartialIndexExists() {
        String def = indexDef("idx_like_legacy_fact_subject_target");
        assertNotNull(def, "Go 002 的 legacy 部分索引 RAG 侧此前完全没有");
        assertTrue(def.contains("WHERE") || def.contains("delivery_envelope IS NULL"),
                "它必须是**部分索引**（WHERE delivery_envelope IS NULL），"
                        + "否则会索引全表并放大写入成本；实际定义：" + def);
    }

    // ==================================================================
    // ④ 投递复合索引（Go 003）
    // ==================================================================

    @Test
    @DisplayName("🔴 Go: idx_like_fact_delivery 复合索引存在（补单列索引缺 created_at/event_id）")
    void deliveryCompositeIndexExists() {
        String def = indexDef("idx_like_fact_delivery");
        assertNotNull(def, "Go 003 的复合索引 RAG 侧此前没有；"
                + "fact_dispatch 的 FOR UPDATE SKIP LOCKED + ORDER BY created_at 依赖它");
        assertTrue(def.contains("created_at") && def.contains("event_id"),
                "复合列必须是 (delivery_status, created_at, event_id)；实际：" + def);
    }

    @Test
    @DisplayName("对照：Go tag 带来的单列索引保留（RAG 侧原有那条不删）")
    void singleColumnDeliveryIndexStillPresent() {
        assertNotNull(indexDef("idx_like_domain_fact_outbox_delivery_status"),
                "这条对应 Go 的 `index` tag，是 V4 建的单列索引 —— 加复合索引**不意味着**删它");
    }

    // ==================================================================
    // ⑤ 信封不可变触发器（Go 003）—— 必须真的触发，不能只看它在 pg_catalog 里
    // ==================================================================

    @Test
    @DisplayName("🔴 Go: 改 envelope 被触发器拒，改 delivery 元数据放行（与 Go 同一条分界）")
    void envelopeIsImmutableButDeliveryMetadataIsNot() {
        String trig = triggerDef("like_domain_fact_identity_immutable");
        assertNotNull(trig, "Go 003 的不可变触发器 RAG 侧此前没有");
        assertTrue(trig.toUpperCase().contains("BEFORE"),
                "必须是 BEFORE UPDATE（Go 就是 BEFORE）—— 改成 AFTER/constraint trigger 会改变报错时机；实际：" + trig);

        jdbcTemplate.update("DELETE FROM " + T);
        insertFact("imm-1", "agg-imm", 1L, "{\"v\":1}");

        // ① 不可变清单内的一列 ⇒ 拒
        assertThrows(Exception.class,
                () -> jdbcTemplate.update("UPDATE " + T + " SET payload = '{\"v\":2}'::jsonb WHERE event_id='imm-1'"),
                "payload 属不可变清单（Go 003 的 IS DISTINCT FROM 链里含 payload）");
        assertThrows(Exception.class,
                () -> jdbcTemplate.update("UPDATE " + T + " SET fact_version = 99 WHERE event_id='imm-1'"),
                "fact_version 同属不可变清单");

        // ② 投递元数据 ⇒ **必须放行**（生产路径就靠这几列更新，误伤会打断投递）
        jdbcTemplate.update("UPDATE " + T + " SET delivery_status = 2, retry_count = retry_count + 1 "
                + "WHERE event_id='imm-1'");
        jdbcTemplate.update("UPDATE " + T + " SET delivery_status = 1, delivered_at = now() "
                + "WHERE event_id='imm-1'");
        assertEquals(1, statusOf("imm-1"), "投递元数据更新必须放行 —— 否则 LikeDomainFactOutboxMapper 的三条 UPDATE 全被打死");
    }

    // ==================================================================
    // ⑥ like_fact_stream.version 范围 CHECK（Go 002）
    // ==================================================================

    @Test
    @DisplayName("🔴 Go: like_fact_stream.version 必须在 [1, 2^53-1]（JSON 安全整数）")
    void factStreamVersionRangeEnforced() {
        assertNotNull(constraintDef("like_fact_stream_version_range"), "Go 002 里这个 CHECK RAG 侧没有");

        jdbcTemplate.update("DELETE FROM like_fact_stream");
        jdbcTemplate.update("INSERT INTO like_fact_stream (aggregate_id, version) VALUES ('agg-ok', 1)");
        assertThrows(Exception.class,
                () -> jdbcTemplate.update(
                        "INSERT INTO like_fact_stream (aggregate_id, version) VALUES ('agg-0', 0)"),
                "version=0 应被拒（下界）");
        assertThrows(Exception.class,
                () -> jdbcTemplate.update(
                        "INSERT INTO like_fact_stream (aggregate_id, version) VALUES ('agg-big', 9007199254740992)"),
                "version > 2^53-1 应被拒 —— 它要进 JSON 信封，超出即静默丢精度");
    }

    // ==================================================================
    // 辅助
    // ==================================================================

    /**
     * ⚠️ {@code CAST(? AS jsonb)} 不能省：pgjdbc 把 Java {@code String} 默认绑成
     * {@code character varying}，jsonb 列直接拒收
     * （{@code column "delivery_envelope" is of type jsonb but expression is of type character varying}）。
     * 这与 admin-rpc 的 {@code extra_info} 是<b>同一个坑</b>（那里已有注释记录）。
     */
    private void insertFact(String eventId, String aggregateId, Long factVersion, String envelope) {
        jdbcTemplate.update("""
                INSERT INTO like_domain_fact_outbox
                    (event_id, event_type, payload, aggregate_id, fact_version, delivery_envelope,
                     delivery_status, retry_count, technical_receipt_id, technical_input_hash,
                     technical_offset, created_at)
                VALUES (?, 'community.target.interaction', '{"subject_ref":"s1","target_type":"article","target_id":"9"}'::jsonb,
                        ?, ?, CAST(? AS jsonb), 0, 0, '', '', 0, now())
                """, eventId, aggregateId, factVersion, envelope);
    }

    private int statusOf(String eventId) {
        Integer s = jdbcTemplate.queryForObject(
                "SELECT delivery_status FROM " + T + " WHERE event_id = ?", Integer.class, eventId);
        return s == null ? -1 : s;
    }

    private String columnType(String column) {
        return jdbcTemplate.queryForObject("""
                SELECT data_type FROM information_schema.columns
                WHERE table_name = ? AND column_name = ?
                """, String.class, T, column);
    }

    private String constraintDef(String name) {
        List<String> r = jdbcTemplate.queryForList("""
                SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = ?
                """, String.class, name);
        return r.isEmpty() ? null : r.get(0);
    }

    private String indexDef(String name) {
        List<String> r = jdbcTemplate.queryForList(
                "SELECT indexdef FROM pg_indexes WHERE indexname = ?", String.class, name);
        return r.isEmpty() ? null : r.get(0);
    }

    private String triggerDef(String name) {
        List<String> r = jdbcTemplate.queryForList("""
                SELECT action_timing || ' ' || event_manipulation
                FROM information_schema.triggers WHERE trigger_name = ?
                """, String.class, name);
        return r.isEmpty() ? null : r.get(0);
    }
}