package com.rag.users.user.rpc;

import com.rag.users.common.ddl.SchemaInitializer;
import com.rag.users.user.rpc.domain.UserEntity;
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

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static com.rag.users.user.rpc.support.GoStyleHostPortPinning.pinContainerPort;
import com.rag.users.user.rpc.support.RealGrpcPort;

/**
 * 🔴 DRIFT-USERRPC-V-004 守门测试 —— {@code users_user} 的时间列必须是 {@code timestamptz}。
 *
 * <h2>Go 出处（声明点 + 消费点）</h2>
 * <ul>
 *   <li><b>声明点</b>：{@code service/user/user/rpc/internal/model/data_model.go:14-15}
 *       与 {@code service/user/admin/rpc/internal/model/data_model.go:12-13}
 *       —— {@code CreateTime/UpdateTime time.Time `gorm:"column:…;auto…Time"}，
 *       <b>没有 type tag</b>（Go 出处必须指到「消费该 tag 的控制流」，此处即驱动侧的列类型推导）；</li>
 *   <li><b>消费点</b>：{@code gorm.io/driver/postgres@v1.6.0/postgres.go:251-256}
 *       <pre>
 *       case schema.Time:
 *           if field.Precision &gt; 0 { return fmt.Sprintf("timestamptz(%d)", field.Precision) }
 *           return "timestamptz"
 *       </pre>
 *       ⇒ 无 precision 一律 {@code timestamptz}。</li>
 * </ul>
 *
 * <h2>为什么必须真 PG（不能 mock / 不能只读 SQL 文件）</h2>
 * <p>本类要证两件「读 SQL 文件证不出来」的事：
 * <ol>
 *   <li><b>DDL 真被执行了</b> —— {@code SchemaInitializer} 是无版本账本的幂等重放器
 *       （{@code users-common/.../SchemaInitializer.java:118-122}），
 *       文件写了不等于 PG 里就是那个类型；</li>
 *   <li><b>重放不会损坏既有数据</b> —— 见下「本类抓到的那个 bug」。</li>
 * </ol>
 *
 * <h2>本类抓到的 bug：裸 ALTER 在重放时会累积平移整列</h2>
 * <p>迁移最初写成
 * {@code ALTER TABLE users_user ALTER COLUMN create_time TYPE TIMESTAMPTZ USING create_time AT TIME ZONE 'UTC';}
 * （注释还写着「PG 会做恒等转换」—— <b>这句是错的</b>）。
 * 对已经是 {@code timestamptz} 的列：
 * <pre>
 *   timestamptz --AT TIME ZONE 'UTC'--&gt; timestamp(UTC 墙钟)
 *                --按 PG 会话 TimeZone 铸回--&gt; timestamptz(可能已被平移)
 * </pre>
 * 而 {@code SchemaInitializer} <b>每次启动都原样重放</b> ⇒ 会话时区非 UTC 时，
 * <b>每启动一次整列时刻就偏移一次且永不收敛</b>。
 * 修法：把会话时区显式钉成 UTC 再转、转完 {@code RESET}，让首迁与重放两条路径都恒等。
 * （原想用 {@code DO $$…$$} + {@code information_schema} 条件守卫，
 *   <b>实测不可行</b>：spring-jdbc 7.0.9 的 {@code ScriptUtils} 不支持 PostgreSQL 美元引号，
 *   块内分号被切断 ⇒ 两个 rpc 组件直接起不来。详见 DDL 文件里的记录。）
 * 用例 {@link #replayUnderNonUtcSessionTimeZone_doesNotShiftStoredInstants()} 是这条的守门。
 *
 * <h2>可证伪性</h2>
 * <ul>
 *   <li>把 DDL 的 {@code TIMESTAMPTZ} 改回 {@code TIMESTAMP} → 用例 1 / 3 立刻红；</li>
 *   <li>删掉 DDL 里的 {@code SET TIME ZONE 'UTC'} → 用例 4 在非 UTC 会话时区下立刻红。</li>
 * </ul>
 */
@SpringBootTest(
        classes = UserRpcApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
@Testcontainers
@org.testcontainers.junit.jupiter.EnabledIfDockerAvailable
class UserRpcUsersUserTimestamptzParityTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("user_rpc_tstz_it")
                    .withUsername("rag")
                    .withPassword("rag").withCreateContainerCmdModifier(cmd -> pinContainerPort(cmd, 5432));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("grpc.server.port", () -> String.valueOf(RealGrpcPort.pick()));
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;

    /** 迁移后应当等于的绝对时刻：无时区墙钟 {@code 2020-01-01 08:00:00} 按 UTC 解释。 */
    private static final Instant SEED_INSTANT = Instant.parse("2020-01-01T08:00:00Z");
    private static final String NON_UTC_TZ = "Asia/Shanghai";

    @BeforeEach
    void resetTable() {
        jdbcTemplate.execute("TRUNCATE users_user RESTART IDENTITY CASCADE");
    }

    // ==================================================================
    // 1. 守恒断言：本表**恰好**只有 create_time / update_time 两个时间列，且都是 timestamptz
    // ==================================================================

    @Test
    @DisplayName("Go: users_user 的全部时间列都是 timestamptz（守恒：恰好 2 列，无漏网）")
    void allTimeColumnsAreTimestamptz() {
        Map<String, String> timeColumns = timeColumnTypes();
        assertEquals(
                Map.of("create_time", "timestamp with time zone",
                        "update_time", "timestamp with time zone"),
                timeColumns,
                "users_user 的时间列必须全部是 timestamptz（Go 消费点 postgres.go:251-256）；"
                        + "实际=" + timeColumns);
    }

    // ==================================================================
    // 2. 实体侧：字段类型必须带偏移（否则 Go 的 RFC3339 偏移量无从产出）
    // ==================================================================

    @Test
    @DisplayName("Go: UserEntity 的两个时间字段声明为 OffsetDateTime（带偏移）")
    void entityTimeFieldsAreOffsetDateTime() throws NoSuchFieldException {
        // 为什么断言「声明类型」而不是「往返值」：pgjdbc 把 timestamptz 取成 OffsetDateTime 时
        // 会归一到 JVM 默认时区，断言具体偏移量会随跑测机器的 TZ 变 ⇒ 假红。
        // 声明类型才是稳定且可证伪的那一半（LocalDateTime 根本无法表达偏移）。
        assertEquals(OffsetDateTime.class, fieldType("createTime"));
        assertEquals(OffsetDateTime.class, fieldType("updateTime"));
    }

    // ==================================================================
    // 3. 迁移路径：老库（TIMESTAMP 列）重放 DDL ⇒ 升成 timestamptz，墙钟按 UTC 解释
    // ==================================================================

    @Test
    @DisplayName("迁移：老库的无时区列重放 DDL 后升为 timestamptz，墙钟按 UTC 解释")
    void legacyTimestampColumn_isMigrated_asUtc() throws Exception {
        downgradeToLegacyTimestamp();
        seedLegacyRow();

        assertEquals("timestamp without time zone", columnType("create_time"),
                "前置条件：本用例从一个真的老库形态出发");

        applySchema("Asia/Shanghai");   // 故意用非 UTC 会话，排除「碰巧 UTC 才过」

        assertEquals("timestamp with time zone", columnType("create_time"));
        assertEquals(SEED_INSTANT, instantOf("create_time"),
                "无时区墙钟 2020-01-01 08:00:00 必须按 **UTC** 解释（USING ... AT TIME ZONE 'UTC'），"
                        + "而不是按会话 TimeZone");
    }

    // ==================================================================
    // 4. 🔴 重放守门：非 UTC 会话下重复重放，整列绝对时刻不得漂移
    // ==================================================================

    @Test
    @DisplayName("重放守门：已迁移的列在非 UTC 会话下重复重放 N 次，时刻不得累积平移")
    void replayUnderNonUtcSessionTimeZone_doesNotShiftStoredInstants() throws Exception {
        downgradeToLegacyTimestamp();
        seedLegacyRow();
        applySchema(NON_UTC_TZ);
        assertEquals(SEED_INSTANT, instantOf("create_time"), "首次迁移后的基准");

        // 模拟「同一个进程反复启动」：SchemaInitializer 每次启动都原样重放全量 DDL
        for (int i = 0; i < 3; i++) {
            applySchema(NON_UTC_TZ);
            assertEquals(SEED_INSTANT, instantOf("create_time"),
                    "第 " + (i + 1) + " 次重放后绝对时刻被平移了。"
                            + "根因：裸 ALTER 的 `timestamptz AT TIME ZONE 'UTC'` 不恒等，"
                            + "回铸时会按会话 TimeZone 再解释一次 —— "
                            + "DDL 必须先 `SET TIME ZONE 'UTC'` 再转。");
        }
    }

    // ==================================================================
    // 5. 重放必须成功返回 —— SchemaInitializer 对失败是直接抛（绝不能吞）
    // ==================================================================

    @Test
    @DisplayName("SchemaInitializer 重放必须成功返回 —— continueOnError=false 时任何失败都会炸启动")
    void replayOnAlreadyMigratedSchema_succeeds() throws Exception {
        applySchema("UTC");
        applySchema("UTC");
        assertEquals("timestamp with time zone", columnType("update_time"));
    }

    // ==================================================================
    // 6. SET/RESET 必须把会话时区还原（连接要归还连接池，不能带 UTC 出去）
    // ==================================================================

    @Test
    @DisplayName("DDL 不得把会话 TimeZone 带出脚本：applyAll 后应回到库默认值")
    void schemaReplay_restoresSessionTimeZone() throws Exception {
        String serverDefault = currentSessionTimeZone();
        try (Connection conn = dataSource.getConnection()) {
            try (Statement st = conn.createStatement()) {
                st.execute("SET TIME ZONE '" + NON_UTC_TZ + "'");
            }
            SchemaInitializer.applyAll(conn, "UserRpcUsersUserTimestamptzParityTest");
            try (Statement st = conn.createStatement();
                 var rs = st.executeQuery("SHOW TimeZone")) {
                rs.next();
                assertEquals(serverDefault, rs.getString(1),
                        "脚本里的 `SET TIME ZONE 'UTC'` 没有被 `RESET TIME ZONE` 还原 —— "
                                + "这条连接会带着 UTC 回到连接池，影响后续所有业务查询。");
            }
        }
    }

    // ==================================================================
    // helpers
    // ==================================================================

    private static Class<?> fieldType(String name) throws NoSuchFieldException {
        Field f = UserEntity.class.getDeclaredField(name);
        return f.getType();
    }

    /** 守恒用：把本表所有时间列的 data_type 收成一张表，漏一列就红。 */
    private Map<String, String> timeColumnTypes() {
        Map<String, String> out = new LinkedHashMap<>();
        jdbcTemplate.queryForList("""
                SELECT column_name, data_type
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = 'users_user'
                  AND data_type LIKE 'timestamp%'
                ORDER BY column_name
                """).forEach(r -> out.put((String) r.get("column_name"), (String) r.get("data_type")));
        return out;
    }

    private String columnType(String column) {
        return jdbcTemplate.queryForObject("""
                SELECT data_type
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = 'users_user'
                  AND column_name = ?
                """, String.class, column);
    }

    private Instant instantOf(String column) {
        // 比 OffsetDateTime 的**绝对时刻**而非对象相等：pgjdbc 会把 timestamptz
        // 归一到 JVM 默认时区返回，对象相等会随跑测机器 TZ 变 ⇒ 假红。
        OffsetDateTime v = jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM users_user WHERE uid = ?", OffsetDateTime.class, SEED_UID);
        return v.toInstant();
    }

    private static final long SEED_UID = 9901L;

    /** 把两列退回「迁移前」的 TIMESTAMP 形态，模拟老库。 */
    private void downgradeToLegacyTimestamp() {
        jdbcTemplate.execute("ALTER TABLE users_user ALTER COLUMN create_time TYPE TIMESTAMP");
        jdbcTemplate.execute("ALTER TABLE users_user ALTER COLUMN update_time TYPE TIMESTAMP");
    }

    private void seedLegacyRow() {
        jdbcTemplate.update("""
                INSERT INTO users_user (uid, username, password, email, status, score,
                                       extra_info, create_time, update_time)
                VALUES (?, 'tz-user', 'h', 'tz@mail.test', 0, 0, '{}',
                        TIMESTAMP '2020-01-01 08:00:00', TIMESTAMP '2020-01-01 08:00:00')
                """, SEED_UID);
    }

    /** 在指定会话时区下走**真实的** SchemaInitializer（不是把 SQL 抄一遍）。 */
    private void applySchema(String sessionTimeZone) throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            try (Statement st = conn.createStatement()) {
                st.execute("SET TIME ZONE '" + sessionTimeZone + "'");
            }
            SchemaInitializer.applyAll(conn, "UserRpcUsersUserTimestamptzParityTest");
        }
    }

    /** 走一条**全新**连接读库默认时区（不被本类任何 SET 影响）。 */
    private String currentSessionTimeZone() {
        return jdbcTemplate.queryForObject("SHOW TimeZone", String.class);
    }
}