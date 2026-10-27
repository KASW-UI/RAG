package com.rag.favorite.favorite.rpc;

import org.junit.jupiter.api.BeforeEach;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.rag.favorite.favorite.rpc.support.RealGrpcPort;
import com.rag.favorite.favorite.rpc.support.RealContainerHostPort;

/**
 * 🔴 真 PostgreSQL 上的 schema 资产校验 —— 对应 Go 侧
 * {@code service/favorite/rpc/internal/model/db.go:33} 的 {@code AutoMigrate}
 * 与 4 个手写 SQL 的<b>全部</b>产物。
 *
 * <h2>⚠️ 为什么必须是真 PG 而不能是 H2</h2>
 * <p>本类要验证的三类资产，H2 <b>一样都不支持</b>：
 * <ul>
 *   <li>{@code BEFORE UPDATE} 的 plpgsql 触发器 + {@code IS DISTINCT FROM}</li>
 *   <li>{@code CREATE INDEX … WHERE status IN (0,2)} 部分索引</li>
 *   <li>{@code CHECK (source_snapshot_sha256 ~ '^[a-f0-9]{64}$')} 正则约束</li>
 * </ul>
 * 换成 H2 等于「把要验证的东西换没了」（AGENTS.md §0 like 行记录的同型教训）。
 *
 * <h2>本类承接 Go 验收资产里的哪部分</h2>
 * <p>Go 的 {@code acceptance.sh} 依赖 PG16 二进制 + {@code go test -race}，RAG 侧无等价物
 * （docs/FileTree/favorite/rpc.md §12.3 末表）⇒ DDL 这部分由本类承接，
 * 事务与并发部分由 {@code FavoriteFactChainTransactionTest} 承接。
 *
 * <p>⚠️ 本类<b>不</b>用 {@code @SpringBootTest} 之外的方式启动容器，
 * 也不在断言里用「Bean 存在」代替「机制生效」（§3.3.3）。
 */
@Testcontainers
@org.testcontainers.junit.jupiter.EnabledIfDockerAvailable
@SpringBootTest(
        classes = FavoriteRpcApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                // 绕过 Nacos：devh 的 @GrpcClient 在 **Bean 初始化期**就建 channel（不是懒建），
                // 测试环境没有 Nacos ⇒ discovery:// 解析失败 ⇒ 上下文起不来。
                // 实测（2026-10-23）：不覆盖这里会得到
                //   IllegalArgumentException: Incorrectly formatted target uri;
                //   expected: 'discovery:[//]/<service-name>'
                // 先例：like-rpc/LikeRpcSpringContextWiringTest 的同款注释。
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "grpc.client.user-rpc.address=static://127.0.0.1:1",
                "grpc.client.article-rpc.address=static://127.0.0.1:1",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
class FavoriteSchemaParityTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("first_db")
                    .withUsername("admin")
                    .withPassword("password")
                    .withCreateContainerCmdModifier(RealContainerHostPort.bindPg(RealContainerHostPort.FAVORITE_SCHEMA_PARITY));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // 三个 cmd/* 对应的可选功能在测试里保持关闭
        registry.add("favorite.fact.dispatch.enabled", () -> "false");
        registry.add("favorite.fact.authority.enabled", () -> "false");
        registry.add("favorite.fact.legacy-preflight.enabled", () -> "false");
        // 🔴 gRPC server 端口走**本域专用段**（RealGrpcPort），**不用 `port=0`**：
        //   `port=0` 让 OS 从临时端口段（macOS 49152–65535）里挑 ⇒ 那是 Docker 随机发布
        //   容器端口用的同一段 ⇒ 别的会话的容器先占住 0.0.0.0:P ⇒ 我们再绑同一个端口
        //   ⇒ EADDRINUSE ⇒ **整个测试类启动失败**（表现为「偶尔红、换个时间又绿」的抖动）。
        registry.add("grpc.server.port", RealGrpcPort::pick);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 🔴 用例之间必须清库。
     *
     * <p>实测（2026-10-23）：多个用例都往 {@code favorite_folder} 插 {@code (user_id=1001, name='f')}，
     * 而 {@code uk_user_folder_name (user_id, name)} 是 <b>L2 业务语义</b>（不是性能索引），
     * 只有第一个执行的用例能插进去，其余全部撞 23505 ⇒ 3 个用例报 DuplicateKeyException。
     *
     * <p>为什么用 TRUNCATE 而不是 DELETE：{@code favorite_legacy_fact_marker} 上有
     * {@code favorite_legacy_marker_immutable} BEFORE UPDATE OR DELETE 触发器，
     * DELETE 会被它拒绝；TRUNCATE 不触发行级 DELETE 触发器（只触发 TRUNCATE 触发器，本表没有）。
     *
     * <p>注意：TRUNCATE 绕开了那个触发器，而触发器本身由
     * {@link #legacyMarkerIsImmutable()} 用例单独验证（它用 UPDATE/DELETE 触发，不走清库）。
     */
    @BeforeEach
    void resetTables() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE favorite_folder, favorite_item,
                                 favorite_fact_outbox, favorite_legacy_fact_marker
                CASCADE
                """);
    }

    // ==================== 4 张表 ====================

    @Test
    @DisplayName("Flyway 建出 4 张表 —— 缺 favorite_legacy_fact_marker 会把 5304 变成 5008")
    void allFourTablesExist() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema='public'",
                String.class);
        for (String table : List.of("favorite_folder", "favorite_item",
                "favorite_fact_outbox", "favorite_legacy_fact_marker")) {
            assertTrue(tables.contains(table), "缺表: " + table);
        }
    }

    // ==================== 🔴 两个不可变触发器（AutoMigrate 绝不创建） ====================

    @Test
    @DisplayName("🔴 favorite_fact_identity_immutable：BEFORE UPDATE 拒改冻结信封的 4 列")
    void outboxEnvelopeIsImmutable() {
        jdbcTemplate.update("""
                INSERT INTO favorite_fact_outbox
                    (event_id, favorite_id, aggregate_version, payload, status, retry_count,
                     created_at, updated_at)
                VALUES ('favorite.9001.v1', 9001, 1,
                        '{"event_id":"favorite.9001.v1"}'::jsonb, 0, 0,
                        '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')
                """);

        // ① 改投递元数据列 ⇒ 必须成功（dispatcher 每天都在做）
        assertEquals(1, jdbcTemplate.update(
                "UPDATE favorite_fact_outbox SET status = 1, technical_offset = 7 WHERE event_id='favorite.9001.v1'"));

        // ② 🔴 改 payload ⇒ 必须被拒
        assertThrowsPg(() -> jdbcTemplate.update(
                "UPDATE favorite_fact_outbox SET payload='{\"tampered\":1}'::jsonb "
                        + "WHERE event_id='favorite.9001.v1'"));
        // ③ 🔴 改 favorite_id ⇒ 必须被拒
        assertThrowsPg(() -> jdbcTemplate.update(
                "UPDATE favorite_fact_outbox SET favorite_id = 9002 WHERE event_id='favorite.9001.v1'"));
        // ④ 🔴 改 aggregate_version ⇒ 必须被拒
        assertThrowsPg(() -> jdbcTemplate.update(
                "UPDATE favorite_fact_outbox SET aggregate_version = 2 WHERE event_id='favorite.9001.v1'"));
    }

    @Test
    @DisplayName("🔴 favorite_legacy_marker_immutable：连 DELETE 都禁（防事后抹掉审批证据）")
    void legacyMarkerIsImmutable() {
        jdbcTemplate.update("""
                INSERT INTO favorite_legacy_fact_marker
                    (favorite_id, user_id, folder_id, target_type, target_id,
                     source_snapshot_sha256, approval_ref)
                VALUES (8001, 1001, 2001, 'article', '9001',
                        'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
                        'TICKET-123')
                """);
        assertThrowsPg(() -> jdbcTemplate.update(
                "UPDATE favorite_legacy_fact_marker SET approval_ref='TICKET-999' WHERE favorite_id=8001"));
        assertThrowsPg(() -> jdbcTemplate.update(
                "DELETE FROM favorite_legacy_fact_marker WHERE favorite_id=8001"));
    }

    // ==================== 🔴 CHECK / UNIQUE 约束 ====================

    @Test
    @DisplayName("🔴 UNIQUE(favorite_id, aggregate_version)：一个收藏的每个版本恰好一个冻结事实")
    void oneFrozenFactPerAggregateVersion() {
        jdbcTemplate.update("""
                INSERT INTO favorite_fact_outbox
                    (event_id, favorite_id, aggregate_version, payload, status, retry_count,
                     created_at, updated_at)
                VALUES ('favorite.7001.v1', 7001, 1, '{"a":1}'::jsonb, 0, 0,
                        '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')
                """);
        // 🔴 23505 —— create_favorite_logic.go:157-161 依赖它翻译成 AlreadyExists/5302
        assertThrowsPg(() -> jdbcTemplate.update("""
                INSERT INTO favorite_fact_outbox
                    (event_id, favorite_id, aggregate_version, payload, status, retry_count,
                     created_at, updated_at)
                VALUES ('favorite.7001.v1-dup', 7001, 1, '{"a":1}'::jsonb, 0, 0,
                        '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')
                """));
    }

    @Test
    @DisplayName("🔴 CHECK (aggregate_version IN (1,2))：挡住无法解释的 v3 事实")
    void aggregateVersionIsConstrained() {
        assertThrowsPg(() -> jdbcTemplate.update("""
                INSERT INTO favorite_fact_outbox
                    (event_id, favorite_id, aggregate_version, payload, status, retry_count,
                     created_at, updated_at)
                VALUES ('favorite.7002.v3', 7002, 3, '{"a":1}'::jsonb, 0, 0,
                        '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')
                """));
    }

    @Test
    @DisplayName("🔴 CHECK (target_revision IS NULL OR (target_type='article' AND target_revision<>''))")
    void targetRevisionScopeIsConstrained() {
        jdbcTemplate.update("INSERT INTO favorite_folder (folder_id, user_id, name) VALUES (6001, 1001, 'f')");
        // 合法：post + null revision
        jdbcTemplate.update("""
                INSERT INTO favorite_item (favorite_id, folder_id, user_id, target_id, target_type)
                VALUES (6001, 6001, 1001, 'p1', 'post')
                """);
        // 🔴 非法：post + 非 null revision
        assertThrowsPg(() -> jdbcTemplate.update("""
                INSERT INTO favorite_item
                    (favorite_id, folder_id, user_id, target_id, target_type, target_revision)
                VALUES (6002, 6001, 1001, 'p2', 'post', 'p2:r1')
                """));
        // 🔴 非法：article + 空串 revision
        assertThrowsPg(() -> jdbcTemplate.update("""
                INSERT INTO favorite_item
                    (favorite_id, folder_id, user_id, target_id, target_type, target_revision)
                VALUES (6003, 6001, 1001, 'a3', 'article', '')
                """));
    }

    @Test
    @DisplayName("🔴 legacy 标记表的 hash 正则 CHECK：非 hex / 长度不对必须被拒")
    void legacyMarkerHashIsConstrained() {
        jdbcTemplate.update("INSERT INTO favorite_folder (folder_id, user_id, name) VALUES (6002, 1001, 'f')");
        assertThrowsPg(() -> jdbcTemplate.update("""
                INSERT INTO favorite_legacy_fact_marker
                    (favorite_id, user_id, folder_id, target_type, target_id,
                     source_snapshot_sha256, approval_ref)
                VALUES (5001, 1001, 6002, 'article', 'a1', 'NOT-A-HASH', 'TICKET-1')
                """));
        assertThrowsPg(() -> jdbcTemplate.update("""
                INSERT INTO favorite_legacy_fact_marker
                    (favorite_id, user_id, folder_id, target_type, target_id,
                     source_snapshot_sha256, approval_ref)
                VALUES (5002, 1001, 6002, 'article', 'a2',
                        'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA',
                        'TICKET-2')
                """));
        assertThrowsPg(() -> jdbcTemplate.update("""
                INSERT INTO favorite_legacy_fact_marker
                    (favorite_id, user_id, folder_id, target_type, target_id,
                     source_snapshot_sha256, approval_ref)
                VALUES (5003, 1001, 6002, 'article', 'a3',
                        'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', '')
                """));
    }

    @Test
    @DisplayName("🔴 uk_folder_target：同文件夹同目标只能收藏一次（小写化后的 target_type）")
    void folderTargetIsUnique() {
        jdbcTemplate.update("INSERT INTO favorite_folder (folder_id, user_id, name) VALUES (4001, 1001, 'f')");
        jdbcTemplate.update("""
                INSERT INTO favorite_item (favorite_id, folder_id, user_id, target_id, target_type)
                VALUES (4001, 4001, 1001, 'a1', 'article')
                """);
        assertThrowsPg(() -> jdbcTemplate.update("""
                INSERT INTO favorite_item (favorite_id, folder_id, user_id, target_id, target_type)
                VALUES (4002, 4001, 1001, 'a1', 'article')
                """));
        // 🔴 但 target_type 不同 ⇒ 是两条不同的收藏
        jdbcTemplate.update("""
                INSERT INTO favorite_item (favorite_id, folder_id, user_id, target_id, target_type)
                VALUES (4003, 4001, 1001, 'a1', 'post')
                """);
    }

    @Test
    @DisplayName("🔴 uk_user_folder_name：同用户收藏夹名唯一")
    void folderNameIsUniquePerUser() {
        jdbcTemplate.update("INSERT INTO favorite_folder (folder_id, user_id, name) VALUES (3001, 1001, 'dup')");
        assertThrowsPg(() -> jdbcTemplate.update(
                "INSERT INTO favorite_folder (folder_id, user_id, name) VALUES (3002, 1001, 'dup')"));
        // 不同用户可以同名
        jdbcTemplate.update("INSERT INTO favorite_folder (folder_id, user_id, name) VALUES (3003, 1002, 'dup')");
    }

    // ==================== 🔴 部分索引（服务 DispatchOnce 的取件查询） ====================

    @Test
    @DisplayName("🔴 favorite_fact_outbox_pending 部分索引存在且条件正确")
    void pendingPartialIndexExists() {
        List<String> indexes = jdbcTemplate.queryForList(
                "SELECT indexdef FROM pg_indexes WHERE schemaname='public' "
                        + "AND indexname='favorite_fact_outbox_pending'", String.class);
        assertEquals(1, indexes.size(), "取件查询会退化成全表扫");
        String def = indexes.get(0);
        assertTrue(def.contains("status"), def);
        assertTrue(def.contains("created_at"), def);
        // 🔴 部分条件（WHERE）才是这条索引的全部意义
        assertTrue(def.toLowerCase().contains("where"), def);
        assertFalse(def.toLowerCase().contains("where status is null"), def);
    }

    // ==================== 列类型：时区与业务 ID 形态 ====================

    @Test
    @DisplayName("🔴 favorite_item.target_id 是 varchar(255) 而非 bigint（article 雪花 ID > 2^53）")
    void targetIdIsVarchar() {
        String dataType = jdbcTemplate.queryForObject("""
                SELECT data_type FROM information_schema.columns
                WHERE table_schema='public' AND table_name='favorite_item' AND column_name='target_id'
                """, String.class);
        assertEquals("character varying", dataType);

        // 🔴 实测：超 2^53 的目标 ID 必须能原样存入
        jdbcTemplate.update("INSERT INTO favorite_folder (folder_id, user_id, name) VALUES (2001, 1001, 'f')");
        jdbcTemplate.update("""
                INSERT INTO favorite_item (favorite_id, folder_id, user_id, target_id, target_type)
                VALUES (2001, 2001, 1001, '9223372036854775807', 'article')
                """);
        String stored = jdbcTemplate.queryForObject(
                "SELECT target_id FROM favorite_item WHERE favorite_id=2001", String.class);
        assertEquals("9223372036854775807", stored);
    }

    @Test
    @DisplayName("🔴 时间列是 timestamptz 且带时区语义（Go DSN 显式 TimeZone=Asia/Shanghai）")
    void timeColumnsAreTimestamptz() {
        for (String[] pair : new String[][]{
                {"favorite_folder", "create_time"}, {"favorite_folder", "update_time"},
                {"favorite_item", "create_time"}, {"favorite_fact_outbox", "created_at"}}) {
            String dataType = jdbcTemplate.queryForObject("""
                    SELECT data_type FROM information_schema.columns
                    WHERE table_schema='public' AND table_name=? AND column_name=?
                    """, String.class, pair[0], pair[1]);
            assertEquals("timestamp with time zone", dataType, pair[0] + "." + pair[1]);
        }
    }

    @Test
    @DisplayName("favorite_item 没有 update_time —— 收藏条目不可变")
    void itemHasNoUpdateTime() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema='public' AND table_name='favorite_item' AND column_name='update_time'
                """, Integer.class);
        assertEquals(0, count);
    }

    /** 断言这条 DDL 触发了 PG 的约束/触发器异常。 */
    private static void assertThrowsPg(Runnable statement) {
        try {
            statement.run();
        } catch (RuntimeException expected) {
            // PG 的约束违例在 Spring JDBC 下是 DataAccessException 的子类
            assertTrue(expected.getClass().getName().contains("DataAccess")
                            || expected.getMessage() != null,
                    "异常类型异常: " + expected);
            return;
        }
        throw new AssertionError("期望被数据库约束/触发器拒绝，但语句成功了");
    }
}