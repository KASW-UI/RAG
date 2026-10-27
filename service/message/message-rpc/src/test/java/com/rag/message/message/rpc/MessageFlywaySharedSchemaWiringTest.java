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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.rag.message.message.rpc.support.RealContainerPort;

/**
 * <b>DRIFT-MSG-V-027（2026-10-24）守门：共享 schema 下 Flyway 的 baseline 会把 V1 整条跳过。</b>
 *
 * <p><b>缺陷形态</b>：message-rpc 用 Flyway，账本表名独立（{@code flyway_schema_history_message}），
 * 且 {@code baseline-on-migrate: true}。而 message-rpc 与 user-rpc / admin-rpc
 * <b>连同一个库</b>（生产 first_db / 对拍 rag_db），后两者用 {@code SchemaInitializer}
 * 幂等重放（无账本）⇒ <b>它们先启动 ⇒ public schema 已经非空</b>。
 *
 * <p>⇒ message-rpc 启动时 Flyway 看到「非空 schema + 没有自己的账本」
 * ⇒ 因 {@code baseline-on-migrate=true} ⇒ <b>在 V1 处打 baseline 标记</b>
 * ⇒ <b>V1 的 SQL 从未执行</b> ⇒ {@code message_notification} 表永远建不出来
 * ⇒ V5（ALTER 那 8 个时间列）报 {@code relation "message_notification" does not exist}
 * ⇒ <b>服务起不来</b>。
 *
 * <p><b>为什么本模块此前的测试全绿却测不到</b>：
 * {@code MessageTransactionWiringTest} 每个用例都是<b>全新空库</b> + 只有 message-rpc 一个进程
 * ⇒ schema 是空的 ⇒ baseline 根本不触发 ⇒ V1 正常执行。
 * ⇒ 「测试全绿」与「生产拓扑下根本起不来」同时成立 —— 这就是 AGENTS.md §3.3 的经典形态，
 * 也是本类存在的理由：<b>必须显式把 schema 弄成非空</b>。
 *
 * <p><b>修法</b>：{@code spring.flyway.baseline-version: 0}
 * ⇒ baseline 标记打在 version 0 ⇒ <b>V1 之后全部迁移照常执行</b>
 * ⇒ 共享库里也能安全启动，且一条迁移都不丢。
 *
 * <p><b>证据</b>（真 PG 16 + 真 jar，2026-10-24 difftest 并行实例实测）：
 * <pre>
 *   非空 schema + baseline-on-migrate=true（baseline-version 默认 1）
 *     ⇒ 账本首行 = `&lt;&lt; Flyway Baseline &gt;&gt;`（version 1）
 *     ⇒ message_notification 不存在 ⇒ V5 失败 ⇒ 启动失败
 *   非空 schema + baseline-version=0
 *     ⇒ 账本 = V1..V5 五条，type 全为 SQL
 *     ⇒ message_notification 建出 ⇒ Started MessageRpcApplication in 4.09 seconds
 * </pre>
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
@org.testcontainers.junit.jupiter.EnabledIfDockerAvailable
class MessageFlywaySharedSchemaWiringTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("message_rpc_shared_schema_it")
                    .withUsername("rag")
                    .withPassword("rag");
/**
     * 本类的真容器**固定宿主口**（message 域段 22760–22789 内 · 全局段表 §4.10.19.3 / §4.10.39.2 分配）。
     *
     * <p>🔴 为什么不用 Testcontainers 的默认随机口：随机宿主口落在 **OS 临时段 49152–65535**
     * （= Docker published 段）⇒ ⇒ 与 {@code tools/difftest/ddl-parity.sh} 的固定口 {@code 55560–55569}
     * 同属一个池子 ⇒ ⇒ 全局段表把该段标为**禁占**。
     *
     * <p>🔴 为什么端口写死在本类里、而不是轮询分配：Testcontainers 的 surefire **每个 fork 一个独立 JVM**
     * ⇒ ⇒ 静态计数器在每个 fork 里都从头开始 ⇒ ⇒ 两个并发 fork 会拿到同一个端口 ⇒ ⇒ 撞车。
     */
    private static final int FIXED_HOST_PORT = 22767;
    static {
        RealContainerPort.withFixedHostPort(POSTGRES, FIXED_HOST_PORT,
                RealContainerPort.PG_CONTAINER_PORT);
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) throws Exception {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);

        // ⚠️⚠️ **这一步是本测试的全部意义**：
        //   在 Spring 上下文启动（= Flyway 开始迁移）**之前**把 public schema 弄成非空，
        //   模拟「别的模块（user-rpc / admin-rpc）先在同一库里建过表」的生产拓扑。
        //   ⇒ 只有在这种前提下，baseline-on-migrate 才会触发、V1 才会被跳过。
        //
        //   为什么放在 @DynamicPropertySource 而不是 @BeforeEach：
        //   @BeforeEach 跑在上下文**之后** ⇒ 那时 Flyway 早就迁移完了 ⇒ 测不到任何东西。
        try (Connection c = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS unrelated_other_module_table (id BIGINT PRIMARY KEY)");
        }
    }

    @Autowired private JdbcTemplate jdbcTemplate;

    private boolean tableExists(String name) {
        Integer n = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables "
                        + "where table_schema='public' and table_name=?",
                Integer.class, name);
        return n != null && n == 1;
    }

    @Test
    @DisplayName("🔴 非空 schema 下 V1 必须真执行 ⇒ message_notification 必须存在")
    void messageNotificationTableMustExistEvenOnNonEmptySchema() {
        assertTrue(tableExists("unrelated_other_module_table"),
                "前置条件：schema 必须非空，否则本用例测不到 baseline 路径");
        assertTrue(tableExists("message_notification"),
                "V1（create message notification）必须真的执行过 —— "
                        + "若被 baseline 跳过，这张表就永远不存在，V5 会因它不存在而启动失败");
    }

    @Test
    @DisplayName("🔴 账本的 BASELINE 行版本必须是 0（否则 V1 被整条跳过）")
    void ledgerBaselineMustSitBelowV1() throws Exception {
        List<String> versions = new ArrayList<>();
        List<String> types = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "select version, type from flyway_schema_history_message order by installed_rank")) {
            while (rs.next()) {
                versions.add(rs.getString(1));
                types.add(rs.getString(2));
            }
        }

        // ⚠️ **断言口径（2026-10-24 实测纠正过一次）**：
        //   baseline-on-migrate=true 时 Flyway **一定会**写一行 BASELINE ——
        //   关键不是「有没有 BASELINE 行」，而是「**这行的版本是不是 0**」。
        //   · version=1 的 BASELINE ⇒ **V1 被跳过** ⇒ message_notification 建不出来（缺陷形态）
        //   · version=0 的 BASELINE ⇒ 基线打在 V1 **之下** ⇒ V1..V5 照常全部执行（修好后的形态）
        //   ⇒ 曾经把断言写成「账本里不允许出现 BASELINE 行」是**过严且错的口径**，
        //     那样连修好后的状态也会判红（变异验证把它当场抓出来了）。
        for (int i = 0; i < types.size(); i++) {
            if ("BASELINE".equals(types.get(i))) {
                assertEquals("0", versions.get(i),
                        "BASELINE 行必须落在 version 0（V1 之下）；"
                                + "落在 version 1 ⇒ V1 被整条跳过 ⇒ message_notification 建不出来。"
                                + "当前账本=" + versions + "/" + types);
            }
        }

        // 核心：V1 必须**真的执行过**（type=SQL），而不是被 baseline 顶掉。
        assertTrue(types.contains("SQL"), "账本里一条 SQL 行都没有 ⇒ 迁移全被跳过；账本="
                + versions + "/" + types);
        for (String v : List.of("1", "2", "3", "4", "5")) {
            assertTrue(versions.contains(v),
                    "账本里缺 version " + v + " ⇒ 该迁移没执行；账本=" + versions + "/" + types);
        }
        // version "1" 必须是 SQL 行（不是 BASELINE）
        assertEquals("SQL", types.get(versions.indexOf("1")),
                "version 1 必须是 SQL 行（V1 真执行了），不能是 BASELINE；账本="
                        + versions + "/" + types);
    }

    @Test
    @DisplayName("✅ 三张 message 表在非空 schema 下也必须齐活")
    void allThreeMessageTablesMustExist() {
        assertTrue(tableExists("message_notification"), "message_notification 缺失");
        assertTrue(tableExists("message_conversation"), "message_conversation 缺失");
        assertTrue(tableExists("message_conversation_message"), "message_conversation_message 缺失");
    }
}
