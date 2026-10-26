package com.rag.like.like.rpc;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 **共享 schema 下 Flyway 的 baseline 会把 V1 整条跳过** —— 对齐 message-rpc 的同款守门
 * （{@code MessageFlywaySharedSchemaWiringTest}，2026-10-24 抓到并修掉的真缺陷）。
 *
 * <h2>缺陷形态（逐条）</h2>
 * <ul>
 *   <li>本模块与其它 rpc 模块**连同一个库**（{@code first_db} / {@code rag_db}），
 *       各自用**独立的账本表**（{@code flyway_schema_history_like}）⇒
 *       <b>先启动的模块已经把 public schema 弄非空</b>；</li>
 *   <li>本模块启动时 Flyway 看到「非空 schema + 没有自己的账本」
 *       ⇒ 因 {@code baseline-on-migrate=true} ⇒ <b>在 V1 处打 baseline 标记</b>
 *       （默认 {@code baseline-version=1}）⇒ <b>V1 的 SQL 从未执行</b>
 *       ⇒ {@code like_record} 表永远建不出来 ⇒ 后续迁移报
 *       {@code relation "like_record" does not exist} ⇒ <b>服务起不来</b>。</li>
 * </ul>
 *
 * <h2>为什么「全新空库」的测试测不出来</h2>
 * <p>本模块大多数单测都是<b>全新空库</b> + 只有本模块一个进程 ⇒ schema 是空的
 * ⇒ baseline 根本不触发 ⇒ V1 正常执行
 * ⇒ 「测试全绿」与「生产拓扑下根本起不来」同时成立（AGENTS.md §3.3 的经典形态）。
 * ⇒ ⇒ 本类**显式把 schema 弄成非空**，让 baseline 路径真的被走到。
 *
 * <h2>为什么不用 {@code @SpringBootTest}（沿用本仓既有先例）</h2>
 * <p>{@code CommentSchemaPgGateTest} 已写明理由：起 Spring 上下文就得把 Redis / Kafka / Nacos
 * 都拉进来 ⇒ 把「DDL 守门」变成「全栈启动测试」。
 * ⇒ 这里直接用<b>与生产同版本的 Flyway 引擎 + 裸 JDBC</b>。
 * ⚠️ 但**配置取自本模块自己的 {@code application.yml}**（不是硬编码）⇒
 * 测的就是生产真正用的那几个值 ⇒ 配置一改，测试立刻跟着变。
 *
 * <h2>修法</h2>
 * <p>{@code spring.flyway.baseline-version: 0} ⇒ baseline 标记打在 version 0
 * ⇒ <b>V1 之后全部迁移照常执行</b> ⇒ 共享库里也能安全启动，且一条迁移都不丢。
 */
@Testcontainers
@org.testcontainers.junit.jupiter.EnabledIfDockerAvailable
class LikeFlywaySharedSchemaGateTest {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("like_rpc_shared_schema_it")
            .withUsername("rag")
            .withPassword("rag");

    @Test
    @DisplayName("🔴 非空 schema 下 V1 必须真执行 ⇒ like_record 表必须存在")
    void v1MustRunEvenOnNonEmptySchema() throws Exception {
        makeSchemaNonEmpty();

        Flyway flyway = flywayConfiguredByModuleYaml();
        flyway.migrate();

        assertTrue(tableExists("like_record"),
                "V1（create like_record）必须真的执行过 —— 若被 baseline 跳过，"
                        + "这张表就永远不存在，后续迁移会因它不存在而失败");
        assertTrue(tableExists("like_consume_inbox"),
                "like_consume_inbox（V2）必须存在 —— 它同样会被 baseline 跳过");

        assertTrue(tableExists("like_outbox_event"),
                "like_outbox_event（V3）必须存在 —— 它同样会被 baseline 跳过");

        assertTrue(tableExists("like_domain_fact_outbox"),
                "like_domain_fact_outbox（V4）必须存在 —— 它同样会被 baseline 跳过");

        assertTrue(tableExists("like_fact_stream"),
                "like_fact_stream（V5）必须存在 —— 它同样会被 baseline 跳过");

    }

    @Test
    @DisplayName("🔴 账本的 BASELINE 行版本必须是 0（否则 V1 被整条跳过）")
    void ledgerBaselineMustSitBelowV1() throws Exception {
        makeSchemaNonEmpty();

        Flyway flyway = flywayConfiguredByModuleYaml();
        flyway.migrate();

        List<String> versions = new ArrayList<>();
        List<String> types = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "select version, type from flyway_schema_history_like order by installed_rank")) {
            while (rs.next()) {
                versions.add(rs.getString(1));
                types.add(rs.getString(2));
            }
        }

        // ⚠️ 口径（同 message-rpc 的实测纠正）：baseline-on-migrate=true 时 Flyway **一定会**
        //   写一行 BASELINE —— 关键不是「有没有 BASELINE 行」，而是「**这行的版本是不是 0**」。
        for (int i = 0; i < types.size(); i++) {
            if ("BASELINE".equals(types.get(i))) {
                assertEquals("0", versions.get(i),
                        "BASELINE 行必须落在 version 0（V1 之下）；落在 version 1 ⇒ V1 被整条跳过。"
                                + "当前账本=" + versions + "/" + types);
            }
        }
        assertTrue(types.contains("SQL"), "账本里一条 SQL 行都没有 ⇒ 迁移全被跳过；账本="
                + versions + "/" + types);
        for (String v : List.of("1", "2", "3", "4", "5", "6")) {
            assertTrue(versions.contains(v),
                    "账本里缺 version " + v + " ⇒ 该迁移没执行；账本=" + versions + "/" + types);
        }
        assertEquals("SQL", types.get(versions.indexOf("1")),
                "version 1 必须是 SQL 行（V1 真执行了），不能是 BASELINE；账本="
                        + versions + "/" + types);
    }

    // ==================================================================
    // helpers
    // ==================================================================

    /** 把 public schema 弄成「非空」—— 模拟「别的模块先在同一库里建过表」的生产拓扑。 */
    private static void makeSchemaNonEmpty() throws Exception {
        try (Connection c = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
             Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS unrelated_other_module_table (id BIGINT PRIMARY KEY)");
        }
    }

    /**
     * 用**本模块 {@code application.yml} 里真实的 flyway 配置**跑一遍真 Flyway。
     *
     * <p>⚠️ 刻意<b>不硬编码</b> locations / schemas / table / baseline-on-migrate / baseline-version
     * ⇒ 测的就是生产真正用的那几个值 ⇒ 配置一改，测试立刻跟着变。
     */
    private static Flyway flywayConfiguredByModuleYaml() throws IOException {
        Properties p = moduleYaml();

        // 🔴 先确认读到的是**本模块自己的** yaml（classpath 上可能还有别的模块的 application.yml）
        assertEquals("flyway_schema_history_like", p.getProperty("spring.flyway.table"),
                "读到的必须是本模块自己的 application.yml（账本表名对不上 ⇒ 可能读串了）");
        assertEquals("0", p.getProperty("spring.flyway.baseline-version"),
                "🔴 本模块的 application.yml 必须显式配 spring.flyway.baseline-version: 0 —— "
                        + "否则共享库里非空 schema 会让 Flyway 在 V1 处打 baseline ⇒ V1 被整条跳过"
                        + " ⇒ 首张表永远建不出来 ⇒ 服务起不来。实际="
                        + p.getProperty("spring.flyway.baseline-version"));

        FluentConfiguration cfg = Flyway.configure()
                .dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword())
                .locations(p.getProperty("spring.flyway.locations", "classpath:db/migration"))
                .schemas(p.getProperty("spring.flyway.schemas", "public"))
                .table(p.getProperty("spring.flyway.table", "flyway_schema_history"))
                .baselineOnMigrate(Boolean.parseBoolean(
                        p.getProperty("spring.flyway.baseline-on-migrate", "false")));
        String baselineVersion = p.getProperty("spring.flyway.baseline-version");
        assertNotNull(baselineVersion, "spring.flyway.baseline-version 必须显式配置");
        // ⚠️ Flyway 10 的 FluentConfiguration 只有 baselineVersion(String) / (MigrationVersion) 两个重载
        cfg.baselineVersion(baselineVersion.trim());
        return cfg.load();
    }

    /** 读本模块 classpath 上的 {@code application.yml}（扁平化后的属性）。 */
    private static Properties moduleYaml() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("module-app", new ClassPathResource("application.yml"));
        Properties p = new Properties();
        for (PropertySource<?> s : sources) {
            // ⚠️ getPropertyNames() 只在 EnumerablePropertySource 上（PropertySource 基类没有）
            if (s instanceof org.springframework.core.env.EnumerablePropertySource<?> enumerable) {
                for (String name : enumerable.getPropertyNames()) {
                    Object v = s.getProperty(name);
                    if (v != null) {
                        p.setProperty(name, String.valueOf(v));
                    }
                }
            }
        }
        return p;
    }

    private static boolean tableExists(String name) throws Exception {
        try (Connection c = DriverManager.getConnection(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "select count(*) from information_schema.tables "
                             + "where table_schema='public' and table_name='" + name + "'")) {
            return rs.next() && rs.getInt(1) == 1;
        }
    }
}
