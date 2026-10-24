package com.rag.message.message.rpc;

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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>DRIFT-MSG-V-020 的真 PG 守门</b>：断言**运行中的库**里 9 个时间列确实是
 * {@code timestamp with time zone}，且可空性与真源一致。
 *
 * <h3>为什么不能只靠「读代码 + 反射断言字段类型」</h3>
 * <p>实体改成 {@code OffsetDateTime} 只能证明<b>Java 侧</b>准备好了；
 * 列类型若仍是 {@code TIMESTAMP}，pgjdbc 会<b>静默丢掉 offset</b>，
 * 读回来仍是无时区墙钟 —— 而这一切<b>编译通过、单测全绿</b>。
 * 这是 AGENTS.md §3.3.3「注解/类型存在 ≠ 机制生效」的 DDL 版本。
 * ⇒ 必须连真 PG 查 {@code information_schema.columns}。
 *
 * <h3>Go 侧基准</h3>
 * <pre>
 *   gorm.io/driver/postgres@v1.6.0/postgres.go:251-255
 *       case schema.Time: ... return "timestamptz"
 *   双侧活体 dump（同一个 postgres:16-alpine）实测：
 *       Go  的 created_at / updated_at  可空（tag 里没有 not null）
 *       只有 Conversation.LastMessageAt 带 not null
 * </pre>
 *
 * <p>⚠️ 本类只断言**列类型与可空性**；{@code DEFAULT CURRENT_TIMESTAMP} 与真源
 * 不同，属<b>刻意保留</b>的残差（两侧 INSERT 都显式绑定这些列 ⇒ 默认永不触发），
 * 原因与登记见 {@code V5} 迁移的头部注释与 DRIFT 台账 V-020 节。
 */
@SpringBootTest(
        classes = MessageRpcApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "grpc.server.port=0",
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN",
                "grpc.client.user-rpc.address=static://127.0.0.1:1",
                "grpc.client.admin-rpc.address=static://127.0.0.1:1"
        })
@Testcontainers
class MessageRpcTimeColumnParityTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("message_rpc_time_parity")
                    .withUsername("rag")
                    .withPassword("rag");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 列名 → Go 侧期望（isNullable 取自 GORM tag：没有 not null 就是可空）。 */
    private static Map<String, String[]> expectedColumns() {
        Map<String, String[]> m = new LinkedHashMap<>();
        m.put("message_notification.read_at", new String[]{"YES", "timestamp with time zone"});
        m.put("message_notification.created_at", new String[]{"YES", "timestamp with time zone"});
        m.put("message_notification.updated_at", new String[]{"YES", "timestamp with time zone"});
        // ⚠️ Go 的 LastMessageAt 带 `not null` ⇒ 两侧都必须是 NO
        m.put("message_conversation.last_message_at", new String[]{"NO", "timestamp with time zone"});
        m.put("message_conversation.created_at", new String[]{"YES", "timestamp with time zone"});
        m.put("message_conversation.updated_at", new String[]{"YES", "timestamp with time zone"});
        m.put("message_conversation_message.read_at", new String[]{"YES", "timestamp with time zone"});
        m.put("message_conversation_message.created_at", new String[]{"YES", "timestamp with time zone"});
        return m;
    }

    @Test
    @DisplayName("🔴 V-020：8 个时间列在真 PG 里必须是 timestamp with time zone，且可空性与真源一致")
    void allTimeColumnsAreTimestamptzWithGoMatchingNullability() {
        List<String> found = expectedColumns().keySet().stream().toList();
        String inClause = "'" + String.join("','", found) + "'";

        Map<String, Map<String, String>> actual = new LinkedHashMap<>();
        jdbcTemplate.query("select table_name||'.'||column_name as col, is_nullable, data_type "
                        + "from information_schema.columns "
                        + "where table_schema='public' and (table_name||'.'||column_name) in (" + inClause + ")",
                rs -> {
                    actual.put(rs.getString("col"), Map.of(
                            "is_nullable", rs.getString("is_nullable"),
                            "data_type", rs.getString("data_type")));
                });

        assertEquals(8, actual.size(), "8 个时间列应当全部存在；实际查到 " + actual.keySet()
                + "⇒ 是不是 V5 迁移没跑？（Flyway table=flyway_schema_history_message）");

        expectedColumns().forEach((col, want) -> {
            Map<String, String> got = actual.get(col);
            assertNotNull(got, col + " 未找到");
            assertEquals(want[1], got.get("data_type"),
                    col + " 列类型应与 GORM schema.Time→timestamptz 对齐；"
                            + "若仍是 timestamp without time zone，pgjdbc 会静默丢掉 offset");
            assertEquals(want[0], got.get("is_nullable"),
                    col + " 可空性应与 GORM tag 一致（tag 里没有 not null 就是可空）");
        });
    }

    /**
     * 端到端往返：写入一个**非 UTC offset** 的瞬时，读回来必须是**同一瞬时**。
     *
     * <p>这条比列类型断言更强：列类型对但映射器用错（例如把 offset 丢掉再按 JVM 时区补）
     * 时它照样会红。
     */
    @Test
    @DisplayName("往返：写入 +08:00 的瞬时 ⇒ 读回同一瞬时（offset 不丢、不按 JVM 时区重解释）")
    void instantRoundTripsWithoutLosingOffset() {
        OffsetDateTime written = OffsetDateTime.of(2026, 10, 23, 2, 0, 0, 0, ZoneOffset.ofHours(8));
        long id = System.currentTimeMillis();

        jdbcTemplate.update("""
                INSERT INTO message_notification
                    (id, recipient_id, sender_id, sender_role, kind, title, content,
                     is_read, created_at, updated_at)
                VALUES (?, 1, 1, 1, 1, 't', 'c', false, ?, ?)
                """, id, written, written);

        OffsetDateTime readBack = jdbcTemplate.queryForObject(
                "SELECT created_at FROM message_notification WHERE id = ?",
                OffsetDateTime.class, id);

        assertNotNull(readBack);
        assertEquals(written.toEpochSecond(), readBack.toEpochSecond(),
                "读回的 epoch 必须等于写入的瞬时；不等说明 offset 被丢掉后按 JVM 时区重解释了");
        // ⚠️ 用 isEqual() 而不是 equals()：OffsetDateTime.equals() 要求**瞬时与 offset 都相同**，
        //    而本用例刻意写 +08:00、连接会话时区是 UTC ⇒ 读回必然是 Z 表示（同一瞬时、offset 不同）。
        //    这正是要验的性质：**读回来的 offset 由会话决定，但瞬时必须不变**。
        assertTrue(written.isEqual(readBack),
                "读回值必须与写入值代表**同一瞬时**；offset 不同是正常的（会话时区不同）");

        jdbcTemplate.update("DELETE FROM message_notification WHERE id = ?", id);
    }
}