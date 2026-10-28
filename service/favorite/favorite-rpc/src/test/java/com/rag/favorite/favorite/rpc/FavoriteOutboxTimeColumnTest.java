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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.rag.favorite.favorite.rpc.support.RealGrpcPort;
import com.rag.favorite.favorite.rpc.support.RealContainerHostPort;

/**
 * 🔴🔴 真 PG 上的 **outbox 时间列**校验 —— 守住 2026-10-25 第七轮修的时钟源漂移。
 *
 * <h2>这条漂移是怎么被查出来的</h2>
 * <p>由 {@code tools/ddl-parity-probe/favorite/}（DDL 双侧活体对拍）实测查出：
 * Go 侧 {@code favorite_fact_outbox.created_at} / {@code updated_at} <b>没有 DEFAULT</b>，
 * RAG 侧<b>有 {@code DEFAULT now()}</b> ⇒ 两列的值来自<b>不同的时钟源</b>：
 * <ul>
 *   <li><b>Go</b>：struct tag 是 {@code gorm:"autoCreateTime;not null"} /
 *       {@code gorm:"autoUpdateTime;not null"}，而 struct 字面量
 *       （{@code internal/model/favorite_fact.go:158-159}）<b>不设</b>这两个字段
 *       ⇒ 由 <b>GORM 在应用层赋值</b>
 *       （{@code gorm.io/gorm@v1.31.1/callbacks/create.go:302-306}）
 *       ⇒ 值来自 <b>Go 进程时钟</b>。</li>
 *   <li><b>RAG（修前）</b>：INSERT <b>不绑</b>这两列 ⇒ 落到 {@code DEFAULT now()}
 *       ⇒ 值来自 <b>PG 服务器时钟</b>。</li>
 * </ul>
 *
 * <h2>为什么这是行为差异，而不只是 DDL 差异</h2>
 * <p>{@code created_at} 是取件查询的排序键
 * （{@code ORDER BY created_at, event_id}，favorite_delivery.go:141-147）
 * ⇒ 应用进程与数据库之间的时钟偏移会让<b>取件顺序</b>产生分歧。
 * <p>而 {@code updated_at}：Go 侧<b>每次 UPDATE 都会刷新</b>（GORM 的 {@code autoUpdateTime}），
 * 修前的 RAG 侧 {@code markBlocked} / {@code markFailed} <b>根本不碰这一列</b>
 * ⇒ 行的 {@code updated_at} 永远停在插入时刻。
 *
 * <h2>本类断言的四件事</h2>
 * <ol>
 *   <li>INSERT 写进去的 {@code created_at} / {@code updated_at} <b>精确等于</b>传入的时刻
 *       ⇒ 证明「应用层绑定」真的生效（若落回 {@code DEFAULT now()}，值会≠传入值）。</li>
 *   <li>{@code markBlocked} / {@code markFailed} / {@code markAccepted} 之后
 *       {@code updated_at} <b>被推进</b>到传入值。</li>
 *   <li>🔴 两列在 {@code information_schema} 里<b>没有 DEFAULT</b>
 *       ⇒ 证明 V7 迁移生效，且<b>漏绑会硬失败</b>（与 Go 同款）而不是被静默兜底。</li>
 * </ol>
 *
 * <p>⚠️ 与 {@code FavoriteSchemaParityTest} 同款纪律：<b>真 PG</b>、<b>真 Flyway</b>、
 * <b>真 mapper</b>；不用 H2（timestamptz 的 DEFAULT 与精度行为都测不到）。
 */
@Testcontainers
@org.testcontainers.junit.jupiter.EnabledIfDockerAvailable
@SpringBootTest(
        classes = FavoriteRpcApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "grpc.client.user-rpc.address=static://127.0.0.1:1",
                "grpc.client.article-rpc.address=static://127.0.0.1:1",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
class FavoriteOutboxTimeColumnTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("first_db")
                    .withUsername("admin")
                    .withPassword("password")
                    .withCreateContainerCmdModifier(RealContainerHostPort.bindPg(RealContainerHostPort.FAVORITE_OUTBOX_TIME_COLUMN));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
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

    @Autowired
    private com.rag.favorite.favorite.rpc.mapper.FavoriteFactOutboxMapper outboxMapper;

    /** 固定时刻（微秒精度 —— PostgreSQL 的 timestamptz 只到微秒）。 */
    private static final OffsetDateTime T0 =
            OffsetDateTime.of(2026, 1, 2, 3, 4, 5, 123456000, ZoneOffset.UTC);
    private static final OffsetDateTime T1 =
            OffsetDateTime.of(2026, 1, 2, 3, 4, 6, 654321000, ZoneOffset.UTC);

    @BeforeEach
    void clean() {
        jdbcTemplate.execute("TRUNCATE TABLE favorite_fact_outbox CASCADE");
    }

    private void insert(String eventId, OffsetDateTime at) {
        int n = outboxMapper.insertFact(eventId, 1001L, 1L, "{\"schema_version\":1}", at, at);
        assertEquals(1, n);
    }

    private OffsetDateTime col(String eventId, String column) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM favorite_fact_outbox WHERE event_id = ?",
                (rs, i) -> rs.getObject(column, OffsetDateTime.class), eventId);
    }

    @Test
    @DisplayName("🔴 INSERT 的 created_at/updated_at 必须是【应用层传入的时刻】，不是 DB 的 now()")
    void insertBindsApplicationLayerClock() {
        insert("favorite.1001.v1", T0);

        assertEquals(T0, col("favorite.1001.v1", "created_at"),
                "created_at 必须精确等于应用层传入的时刻 —— 若等于 DB 的 now()，"
                        + "说明 INSERT 又退回了 DEFAULT 兜底（时钟源漂移回归）");
        assertEquals(T0, col("favorite.1001.v1", "updated_at"),
                "updated_at 必须精确等于应用层传入的时刻");

        // 🔴 反证：DB 的 now() 与 T0 差着「真实的当前时间」，绝不可能相等
        OffsetDateTime dbNow = jdbcTemplate.queryForObject(
                "SELECT now()", (rs, i) -> rs.getObject(1, OffsetDateTime.class));
        assertNotEquals(dbNow, col("favorite.1001.v1", "created_at"),
                "created_at 竟然等于 DB 的 now() ⇒ 说明它来自 DB 时钟而不是应用层时钟");
    }

    @Test
    @DisplayName("🔴 markBlocked 必须推进 updated_at（Go 侧 GORM 的 autoUpdateTime 每次 UPDATE 都刷）")
    void markBlockedAdvancesUpdatedAt() {
        insert("favorite.1002.v1", T0);
        assertEquals(T0, col("favorite.1002.v1", "updated_at"));

        outboxMapper.markBlocked("favorite.1002.v1", T1);

        assertEquals(T1, col("favorite.1002.v1", "updated_at"),
                "markBlocked 之后 updated_at 必须推进到传入值 —— "
                        + "Go 侧 GORM 的 autoUpdateTime 会刷新它；不刷新就是漂移");
        assertEquals(3, status("favorite.1002.v1"), "status 必须落 3（blocked）");
    }

    @Test
    @DisplayName("🔴 markFailed 必须推进 updated_at")
    void markFailedAdvancesUpdatedAt() {
        insert("favorite.1003.v1", T0);

        outboxMapper.markFailed("favorite.1003.v1", T1);

        assertEquals(T1, col("favorite.1003.v1", "updated_at"),
                "markFailed 之后 updated_at 必须推进到传入值");
        assertEquals(2, status("favorite.1003.v1"), "status 必须落 2（failed）");
    }

    @Test
    @DisplayName("🔴 markAccepted 必须推进 updated_at，且用的是应用层时钟而不是 DB 的 now()")
    void markAcceptedAdvancesUpdatedAt() {
        insert("favorite.1004.v1", T0);

        outboxMapper.markAccepted("favorite.1004.v1", "receipt-1", "a".repeat(64),
                42L, T0, T1, T1);

        assertEquals(T1, col("favorite.1004.v1", "updated_at"),
                "markAccepted 的 updated_at 必须是应用层传入值 —— 修前写的是 DB 的 now()");
        assertEquals(1, status("favorite.1004.v1"), "status 必须落 1（accepted）");
    }

    @Test
    @DisplayName("🔴 两列在 schema 上【没有 DEFAULT】⇒ 漏绑会硬失败（与 Go 同款），不会被静默兜底")
    void timeColumnsHaveNoDefault() {
        List<String> defaults = jdbcTemplate.queryForList(
                "SELECT column_name || '=' || COALESCE(column_default, '<NULL>') "
                        + "FROM information_schema.columns "
                        + "WHERE table_schema = current_schema() AND table_name = 'favorite_fact_outbox' "
                        + "AND column_name IN ('created_at', 'updated_at') ORDER BY column_name",
                String.class);

        assertEquals(2, defaults.size(), "应当查到两列");
        for (String d : defaults) {
            assertTrue(d.endsWith("=<NULL>"),
                    "「" + d + "」仍带 DEFAULT ⇒ 漏绑时间列会被 DB 静默兜底成 DB 时钟，"
                            + "与 Go（无 DEFAULT ⇒ 硬失败）不同源");
        }
    }

    private int status(String eventId) {
        Integer s = jdbcTemplate.queryForObject(
                "SELECT status FROM favorite_fact_outbox WHERE event_id = ?",
                Integer.class, eventId);
        return s == null ? -1 : s;
    }
}
