package com.rag.users.admin.rpc;

import com.rag.users.admin.rpc.service.AdminDomainService;
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
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.rag.users.admin.rpc.support.GoStyleHostPortPinning.pinContainerPort;
import com.rag.users.admin.rpc.support.RealGrpcPort;

/**
 * 🔴 DRIFT-ADMIN-RPC-003 守门测试 —— {@code users_user.update_time} 必须真的推进。
 *
 * <h2>修掉的是什么（静默失效，不是报错）</h2>
 *
 * <p>{@code UserEntity.updateTime} 标的是
 * {@code updateStrategy = FieldStrategy.NEVER}（{@code domain/UserEntity.java:71}），
 * 本意是「不让实体自动写这一列」。但 MyBatis-Plus <b>没有 GORM 的 autoUpdateTime</b> ——
 * {@code NEVER} 的语义是「<b>永远不更新</b>」，不是「<b>我没赋值时才不更新</b>」。
 * 于是本类 5 个 UPDATE 入口<b>全部</b>不推进 update_time：不报错、不告警、SQL 全部成功。
 *
 * <h2>Go 出处（声明点 + 消费点）</h2>
 * <ul>
 *   <li><b>声明点</b>：{@code service/user/admin/rpc/internal/model/data_model.go:12-13}
 *       （Admin）、{@code :27-28}、{@code :44-45}（User）：
 *       {@code UpdateTime time.Time `gorm:"column:update_time;autoUpdateTime"`}；</li>
 *   <li><b>消费点</b>：{@code gorm@v1.31.2/callbacks/update.go:243-259} 与 {@code :279-281}。</li>
 * </ul>
 *
 * <h2>为什么用 {@code setSql} 而不是 {@code MetaObjectHandler}</h2>
 *
 * <p>本类 5 处写路径<b>全是 {@code update(null, wrapper)}</b> —— 第一个参数是 {@code null}。
 * MP 的 {@code MetaObjectHandler.updateFill} 需要<b>实体</b>去填字段，实体为 null 时
 * <b>根本不会被调用</b>（{@code MybatisParameterHandler:107-115} 只对非 null 的
 * {@code et} 建 MetaObject）。在 article-rpc 加 handler 才对（那边全是 {@code updateById(entity)}），
 * <b>这里加是无功</b>。
 *
 * <h2>为什么必须真 PG（不能 mock）</h2>
 *
 * <p>要验证的是「真的发出了 {@code UPDATE} 且真的写进了 {@code update_time} 列」。
 * mock 数据层不执行 SQL；本类还要先跑 {@code AdminSchemaBootstrap} 的幂等建表。
 *
 * <p><b>本类的可证伪性</b>：删掉任意一处
 * {@code wrapper.setSql("update_time = CURRENT_TIMESTAMP")} 再跑本类，
 * 对应用例<b>应当立刻红</b>。若仍全绿，说明断言没落在该列上，本类即刻失去价值。
 */
@SpringBootTest(
        classes = AdminRpcApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
@Testcontainers
@org.testcontainers.junit.jupiter.EnabledIfDockerAvailable
class AdminRpcUpdateTimeAdvanceWiringTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("admin_rpc_updatetime_it")
                    .withUsername("rag")
                    .withPassword("rag").withCreateContainerCmdModifier(cmd -> pinContainerPort(cmd, 5432));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("grpc.server.port", () -> String.valueOf(RealGrpcPort.pick()));
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private AdminDomainService adminService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final long UID = 8801L;

    @BeforeEach
    void resetTables() {
        // 表名来自 users-common 的幂等 DDL：01_create_user.sql:33 → users_user、
        // 03_create_admin.sql:48 → admin_admin（**不是** users_admin，别按 users_ 前缀猜）
        jdbcTemplate.execute("TRUNCATE users_user, admin_admin RESTART IDENTITY CASCADE");
    }

    // ==================================================================
    // 1. banUser（Go: UpdateUserStatusByUid，internal/model/admin.go:104）
    // ==================================================================

    @Test
    @DisplayName("🔴 Go: banUser 推进 update_time")
    void banUserAdvancesUpdateTime() {
        seedUser();
        OffsetDateTime before = updateTimeOf();

        assertTrue(adminService.banUser(UID, 1L));

        assertEquals(1L, statusOf(), "前置校验：状态确实被改成封禁");
        assertAdvances(before, "banUser");
    }

    // ==================================================================
    // 2. unBanUser（Go: UpdateUserStatusByUid status=0，同一行）
    // ==================================================================

    @Test
    @DisplayName("🔴 Go: unBanUser 推进 update_time")
    void unBanUserAdvancesUpdateTime() {
        seedUser();
        jdbcTemplate.update("UPDATE users_user SET status = 1 WHERE uid = ?", UID);
        OffsetDateTime before = updateTimeOf();

        assertTrue(adminService.unBanUser(UID, 1L));

        assertEquals(0L, statusOf(), "前置校验：状态确实被解封");
        assertAdvances(before, "unBanUser");
    }

    // ==================================================================
    // 3. updateUser（Go: UpdateOneUserByUid，Updates(struct)，admin.go:58）
    // ==================================================================

    @Test
    @DisplayName("🔴 Go: updateUser 推进 update_time")
    void updateUserAdvancesUpdateTime() {
        seedUser();
        OffsetDateTime before = updateTimeOf();

        Map<String, String> extra = new HashMap<>();
        extra.put("nickname", "new-nick");
        adminService.updateUser(UID, 1L, "renamed", "newpass", "new@mail.test", extra);

        assertEquals("renamed", jdbcTemplate.queryForObject(
                "SELECT username FROM users_user WHERE uid = ?", String.class, UID),
                "前置校验：业务列真的写进去了（否则本用例可能只在测一条空转的 UPDATE）");
        assertAdvances(before, "updateUser");
    }

    // ==================================================================
    // 4. resetUserPassword（Go: UpdateUserPasswordByUid，admin.go:112）
    // ==================================================================

    @Test
    @DisplayName("🔴 Go: resetUserPassword 推进 update_time")
    void resetUserPasswordAdvancesUpdateTime() {
        seedUser();
        OffsetDateTime before = updateTimeOf();
        String oldHash = jdbcTemplate.queryForObject(
                "SELECT password FROM users_user WHERE uid = ?", String.class, UID);

        // 注意签名只有 (uid, operatorId) —— 新密码由配置的 default-password 生成，
        // 不由调用方传入（Go 侧同样是 DefaultPassword，见 AdminDomainService:980-989）
        assertTrue(adminService.resetUserPassword(UID, 1L));

        assertNotEquals(oldHash, jdbcTemplate.queryForObject(
                        "SELECT password FROM users_user WHERE uid = ?", String.class, UID),
                "前置校验：密码确实被换成新的 bcrypt hash");
        assertAdvances(before, "resetUserPassword");
    }

    // ==================================================================
    // 5. 未命中时不推进（Go 的 UpdateUserStatusByUid 有 RowsAffected==0 早返回）
    // ==================================================================

    @Test
    @DisplayName("🔴 Go: 用户不存在时 banUser 抛 NOT_FOUND，且不动任何行")
    void missingUserDoesNotAdvanceUpdateTime() {
        seedUser();
        OffsetDateTime before = updateTimeOf();

        try {
            adminService.banUser(999_999L, 1L);
            org.junit.jupiter.api.Assertions.fail("应当抛 NOT_FOUND（Go: admin.go:105-107 RowsAffected==0）");
        } catch (io.grpc.StatusRuntimeException expected) {
            assertEquals(io.grpc.Status.Code.NOT_FOUND, expected.getStatus().getCode());
        }

        assertEquals(before, updateTimeOf(), "没有命中行 ⇒ 没有任何 UPDATE 发出 ⇒ 时间戳不动");
    }

    // ==================================================================
    // 辅助
    // ==================================================================

    /**
     * 把 update_time 推到 2020，让「有没有前进」无歧义。
     *
     * <p>⚠️ 字面量用 {@code TIMESTAMPTZ '…+00'} 而不是原来的 {@code TIMESTAMP '…'}：
     * {@code users_user.update_time} 现在是 {@code timestamptz}
     * （见 {@code users-common/src/main/resources/db/01_create_user.sql:82-83}），
     * 无时区字面量塞进去会被 PG 按<b>会话 TimeZone</b> 隐式换算
     * ⇒ 时区不是 UTC 时种子数据的绝对时刻会漂，而这个测试只关心「有没有前进」。
     */
    private void seedUser() {
        jdbcTemplate.update("""
                INSERT INTO users_user (uid, username, password, email, status, score,
                                       extra_info, create_time, update_time)
                VALUES (?, 'ts-user', 'old-hash', 'ts@mail.test', 0, 0, '{}'::jsonb,
                        TIMESTAMPTZ '2020-01-01 00:00:00+00', TIMESTAMPTZ '2020-01-01 00:00:00+00')
                """, UID);
    }

    /**
     * 🔴 读列类型随 DDL 一起从 {@code LocalDateTime} 改成 {@link OffsetDateTime}
     * （DRIFT-USERRPC-V-004，2026-10-23）。
     *
     * <p>不是顺手改：{@code users_user.update_time} 现在是 {@code timestamptz}
     * （见 {@code users-common/.../01_create_user.sql:82-83}），
     * 用 {@code LocalDateTime.class} 去 {@code getObject} 一个 timestamptz，
     * pgjdbc 会拿 JVM 默认时区把它压成无偏移的本地时间 ——
     * <b>时区不是 UTC 时，这个读法会静默差一个偏移量</b>。
     * 断言的是「时间是否前进」，读法本身不能引入时区噪声。
     */
    private OffsetDateTime updateTimeOf() {
        return jdbcTemplate.queryForObject(
                "SELECT update_time FROM users_user WHERE uid = ?", OffsetDateTime.class, UID);
    }

    private long statusOf() {
        Long s = jdbcTemplate.queryForObject(
                "SELECT status FROM users_user WHERE uid = ?", Long.class, UID);
        return s == null ? -1L : s;
    }

    private void assertAdvances(OffsetDateTime before, String op) {
        OffsetDateTime after = updateTimeOf();
        assertTrue(after.isAfter(before),
                op + " 之后 update_time 必须前进（GORM autoUpdateTime 的语义）；"
                        + "仍等于 " + before + " 说明该入口漏了 "
                        + "wrapper.setSql(\"update_time = CURRENT_TIMESTAMP\") —— "
                        + "注意 UserEntity.updateTime 的 updateStrategy=NEVER 让它静默失效");
    }
}