package com.rag.users.user.rpc;

import com.rag.users.common.ddl.SchemaInitializer;
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

import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static com.rag.users.user.rpc.support.GoStyleHostPortPinning.pinContainerPort;

/**
 * E-3 根因 **R3** 的回归网 —— users 域 DDL 的字符串列必须是 {@code text}，不是 {@code varchar(255)}。
 *
 * <p><b>为什么必须查真 PG 的 information_schema</b>：
 * R3 的全部内容就是<b>列类型</b>，而列类型只存在于数据库里 ——
 * 读 DDL 文件、读实体注解都证明不了「实际建出来的表是什么类型」，
 * 更证明不了「迁移脚本改过了但旧库没被改」。
 *
 * <p><b>Go 出处</b>：GORM {@code AutoMigrate} 给 Go 的 {@code string} 字段生成
 * {@code text}（无长度上限）。实测对照（{@code tools/difftest/check-ddl-static.py}）：
 * 两侧 35 列里 22 列类型不同，其中 10 列是 {@code text → varchar(255)}。
 *
 * <p><b>可观察后果</b>：5000 字符的 username 在 Go 写成功，
 * 在 RAG 被 PG 拒（{@code 22001 value too long}）⇒ 5001/5003。
 *
 * <p><b>本类同时锁住「幂等 ALTER 自愈」这条路</b>：
 * {@code SchemaInitializer} 无版本账本、每次启动原样重放 DDL，
 * 所以「改 DDL 文件」<b>不会自动改到已存在的表</b> ——
 * 必须靠那几条 {@code ALTER TABLE ... ALTER COLUMN ... TYPE text}。
 * {@link #alterSelfHealsAnExistingVarcharColumn()} 就是拿一个「人为建成 varchar(255)」
 * 的表来验证它真的会被改回 text。
 *
 * <p><b>无 Docker 时自动跳过</b>。
 */
@SpringBootTest(
        classes = UserRpcApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "grpc.server.port=0",
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
@Testcontainers
@org.testcontainers.junit.jupiter.EnabledIfDockerAvailable
class UserRpcSchemaTextColumnTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("users_schema_text_it")
                    .withUsername("rag")
                    .withPassword("rag").withCreateContainerCmdModifier(cmd -> pinContainerPort(cmd, 5432));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        // ⚠️ 刻意**不**设 spring.cloud.nacos.discovery.enabled=false：
        // 关掉它会让 NacosServiceManager Bean 不存在，而 NacosRegistration bootstrap
        // 仍然要注入它 ⇒ 上下文起不来。既有 AdminRpcTransactionWiringTest 同样只把
        // server-addr 指到死端口，让 Nacos 连不上但 Bean 仍在。
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private javax.sql.DataSource dataSource;

    /** 表 → 该表上必须是 text 的列。这份清单与 4 份 db/*.sql 里的 ALTER 一一对应。 */
    private static final Map<String, List<String>> TEXT_COLUMNS = new LinkedHashMap<>(Map.of(
            "users_user", List.of("username", "password", "email"),
            "users_user_avatar_history", List.of("history_id", "avatar_url", "content_type"),
            "admin_admin", List.of("username", "password", "email"),
            "admin_invite", List.of("code")
    ));

    @Test
    @DisplayName("R3：10 个字符串列在真 PG 上都是 text（Go 的 GORM 对 string 生成的就是 text）")
    void stringColumnsAreText() {
        for (Map.Entry<String, List<String>> e : TEXT_COLUMNS.entrySet()) {
            for (String col : e.getValue()) {
                String type = jdbcTemplate.queryForObject(
                        "select data_type from information_schema.columns"
                                + " where table_schema='public' and table_name=? and column_name=?",
                        String.class, e.getKey(), col);
                assertEquals("text", type,
                        e.getKey() + "." + col + " 不是 text —— Go 的 GORM 对 string 生成 text，"
                                + "varchar(255) 会让 >255 字符在 RAG 被 PG 拒（22001）而 Go 写成功");
            }
        }
    }

    @Test
    @DisplayName("R3：text 列真的能存下 >255 字符（否则列类型对了也没用）")
    void textColumnActuallyStoresLongValues() {
        String longName = "x".repeat(5000);
        jdbcTemplate.update("delete from users_user where email=?", "r3_long@x.com");
        jdbcTemplate.update(
                "insert into users_user (uid, username, email, status, create_time, update_time)"
                        + " values (?,?,?,0, now(), now())",
                777000001L, longName, "r3_long@x.com");
        String back = jdbcTemplate.queryForObject(
                "select username from users_user where email=?", String.class, "r3_long@x.com");
        assertEquals(5000, back.length(), "写进去读回来长度变了 —— 说明有截断");
        jdbcTemplate.update("delete from users_user where email=?", "r3_long@x.com");
    }

    @Test
    @DisplayName("R3：幂等 ALTER 能把「2026-10-22 之前的旧库」(varchar(255)) 自愈成 text")
    void alterSelfHealsAnExistingVarcharColumn() throws Exception {
        // 本测试的形状很关键：**先把真实列降级回 varchar(255)**，模拟修复之前建出来的库，
        // 再重放 DDL 看它有没有被改回 text。
        //
        // ⚠️ 早期版本造了一张无关的探针表 r3_legacy_probe 然后指望 DDL 去改它 ——
        //    那条 ALTER 只作用于 users_user / admin_admin 这些真实表，探针表根本不在名单里，
        //    于是「自愈」能力测了个寂寞（这条用例就是这么红的）。
        //    自愈的语义是「**已存在的这套表**被改掉」，不是「任意一张表被改掉」。
        jdbcTemplate.update("delete from users_user where length(username) > 255");
        jdbcTemplate.execute("ALTER TABLE users_user ALTER COLUMN username TYPE varchar(255)");
        assertEquals("character varying", jdbcTemplate.queryForObject(
                "select data_type from information_schema.columns"
                        + " where table_schema='public' and table_name='users_user' and column_name='username'",
                String.class),
                "降级没生效，测试前提不成立");

        // SchemaInitializer 是无账本的幂等重放，这正是它的用法
        try (Connection conn = dataSource.getConnection()) {
            SchemaInitializer.applyAll(conn, "r3-self-heal-probe");
        }

        assertEquals("text", jdbcTemplate.queryForObject(
                "select data_type from information_schema.columns"
                        + " where table_schema='public' and table_name='users_user' and column_name='username'",
                String.class),
                "重放 DDL 没有把 varchar(255) 改回 text —— "
                        + "只改 db/*.sql 的 CREATE 对已存在的库是无效的，必须靠那几条 ALTER");
    }

    @Test
    @DisplayName("R3：SchemaInitializer 连跑两次不报错（ALTER 必须是幂等的）")
    void schemaInitializerIsIdempotent() throws Exception {
        // 「建表」和「升级」共用同一套无账本脚本时，幂等性是硬要求 ——
        // ScriptUtils 用的是 continueOnError=false，不幂等就会在第二次启动时直接炸
        for (int i = 0; i < 2; i++) {
            try (Connection conn = dataSource.getConnection()) {
                SchemaInitializer.applyAll(conn, "r3-idempotency-probe-" + i);
            }
        }
    }
}