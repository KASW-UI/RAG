package com.rag.users.user.rpc;

import com.rag.users.user.rpc.domain.UserEntity;
import com.rag.users.user.proto.LoginReq;
import com.rag.users.user.rpc.mapper.UserMapper;
import com.rag.users.user.rpc.service.UserDomainService;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 DRIFT-USERRPC-V-001 守门测试 —— {@code users_user.status IS NULL} 时 {@code Login}
 * <b>必须失败</b>（fail-closed），<b>绝不能</b>被当作「未封禁」放行（fail-open）。
 *
 * <h2>Go 侧行为（声明点 + 消费点）</h2>
 * <ul>
 *   <li><b>声明点</b> {@code service/user/user/rpc/internal/model/data_model.go:11}：
 *       {@code Status int64 `gorm:"column:status;default:0"}` —— <b>没有 not null</b>，
 *       值类型 {@code int64}；</li>
 *   <li><b>消费点链</b>（§3.3.1 要求指到真正执行它的那行）：
 *       {@code gorm@v1.31.2/scan.go:153} {@code db.AddError(rows.Scan(values...))}
 *       → {@code database/sql} 对「NULL → int64」返回 unsupported 错误
 *       → {@code internal/model/user.go:28} {@code return nil, err}（<b>非</b> {@code ErrRecordNotFound}）
 *       → {@code internal/logic/loginlogic.go:53-57} 的 {@code db_error} 支
 *       ⇒ {@code codes.Internal "DB查询失败"}，<b>登录被拒</b>。</li>
 * </ul>
 *
 * <h2>修复前的 RAG 行为</h2>
 * {@code if (user.getStatus() != null && user.getStatus() == 1L)} ——
 * {@code status} 为 NULL 时<b>跳过封禁检查</b>，直接落到 W4 返回 {@code LoginResp{uid, status=0}}，
 * <b>登录成功</b>。方向是 fail-open：<b>status 列若为 NULL，被封禁的用户可以登录</b>。
 *
 * <h2>为什么必须真 PG（不能 mock）</h2>
 * 本用例的核心是「<b>NULL 真的能进到 Java 侧</b>」。mock 数据层不执行 SQL，
 * 构造不出 MP 把 NULL 映射成 {@code null} 的那条路径。
 * 另外 {@code status} 列在 RAG 侧是 {@code NOT NULL}（{@code 01_create_user.sql:39}），
 * 与 Go 不同（DRIFT-USERRPC-V-002）⇒ 本类<b>先 DROP NOT NULL</b> 才能复现 Go 的可达性。
 *
 * <h2>本类的可证伪性</h2>
 * 把 {@code UserDomainService#login} 里那段 {@code if (user.getStatus() == null) throw …}
 * 删掉再跑本类，<b>应当立刻红</b>。
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
class UserRpcLoginNullStatusFailClosedTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("users_login_null_status_it")
                    .withUsername("rag")
                    .withPassword("rag");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private UserDomainService userService;
    @Autowired private UserMapper userMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final String USERNAME = "null-status-user";
    /**
     * 用项目<b>自己</b>的 {@code PasswordUtil} 生成，而不是硬编码一个 bcrypt 字面量。
     *
     * <p>⚠️ 硬编码 hash 是本类第一版踩的坑：写死的 hash 对不上明文，
     * 登录走到密码校验分支返回 {@code status=1}，而 D-002 里
     * 「用户不存在」与「密码错」<b>共用同一个 1</b> ⇒ 对照组红得莫名其妙。
     * 顺带一提：共用状态码本身让「到底是哪种失败」不可判别，
     * 这是 {@code UserDomainService:186} 注释里已登记的既有行为（与 Go 一致），不改。
     */
    private static final String RAW_PASSWORD = "password";

    @BeforeEach
    void dropNotNullAndSeed() {
        jdbcTemplate.execute("TRUNCATE users_user RESTART IDENTITY CASCADE");

        // ⚠️ RAG 的 DDL 声明 status NOT NULL（01_create_user.sql:39），Go 侧没有 not null。
        // 不去掉这条约束就**根本插不进 NULL 行**，也就复现不了 Go 的可达性
        // —— 这本身正是 DRIFT-USERRPC-V-002 记的那个 DDL 差异。
        jdbcTemplate.execute("ALTER TABLE users_user ALTER COLUMN status DROP NOT NULL");

        jdbcTemplate.update("""
                INSERT INTO users_user (uid, username, password, email, status, score,
                                       extra_info, create_time, update_time)
                VALUES (?, ?, ?, ?, NULL, 0, '{}'::jsonb,
                        TIMESTAMP '2020-01-01 00:00:00', TIMESTAMP '2020-01-01 00:00:00')
                """, 9001L, USERNAME,
                com.rag.users.common.cryptx.PasswordUtil.encrypt(RAW_PASSWORD),
                "null-status@mail.test");

        assertNull(statusRaw(),
                "前置校验：种子行的 status 必须是 NULL（否则本用例测的是别的东西）");
    }

    // ==================================================================

    @Test
    @DisplayName("🔴 Go: status IS NULL 时 Login 抛 INTERNAL，而不是放行（DRIFT-USERRPC-V-001）")
    void loginWithNullStatusFailsClosed() {
        var req = LoginReq.newBuilder()
                .setUsername(USERNAME)
                .setPassword(RAW_PASSWORD)
                .build();

        StatusRuntimeException ex =
                assertThrows(StatusRuntimeException.class, () -> userService.login(req),
                        "status 为 NULL 时必须失败（Go: 扫描失败 ⇒ codes.Internal \"DB查询失败\"）。"
                                + "若这里没抛，说明又退回 fail-open 了 —— 被封禁用户可登录");

        assertEquals(Status.Code.INTERNAL, ex.getStatus().getCode(),
                "错误码必须与 Go 的 codes.Internal 一致");
        assertEquals("DB查询失败", ex.getStatus().getDescription(),
                "description 文案必须与 Go 的 \"DB查询失败\" 逐字一致");
    }

    /**
     * 🔴 <b>反向对照：status 为 NULL 时<b>不应</b>走到密码校验</b>。
     *
     * <p>Go 的失败发生在 <b>mapper 层</b>（{@code FindOneByUserName} 返回时），
     * <b>根本走不到</b> {@code cryptx.CheckPassword}。若 RAG 把判据放到密码校验<b>之后</b>，
     * 错误码虽然可能对，但会多走一步、且泄漏「该用户名是否存在」——
     * 这是一条可观察的语义差异。
     *
     * <p>用<b>错误的密码</b> + {@code status = NULL} 组合：Go 两种情况下都返回 INTERNAL
     * （因为 NULL 先触发扫描失败）；而「fail-open 且判据在后」的写法会返回 {@code Status=1}
     * （密码错分支）⇒ 二者可区分。
     */
    @Test
    @DisplayName("🔴 Go: 判据必须在密码校验之前（mapper 层就失败，不泄漏用户名是否存在）")
    void nullStatusIsDetectedBeforePasswordCheck() {
        var req = LoginReq.newBuilder()
                .setUsername(USERNAME)
                .setPassword("definitely-wrong-password")
                .build();

        StatusRuntimeException ex =
                assertThrows(StatusRuntimeException.class, () -> userService.login(req));

        assertEquals(Status.Code.INTERNAL, ex.getStatus().getCode(),
                "Go 的 NULL 扫描失败先于密码错误发生 ⇒ 这里必须是 INTERNAL，"
                        + "而不是密码错分支的 LoginResp{status=1}");
    }

    /**
     * 对照组：<b>status 有值时行为不变</b>，确保修复没有误伤正常路径。
     *
     * <p>三态各断一次：0=正常、1=封禁、2 之类的其它值 ⇒ 都不是 INTERNAL。
     */
    @Test
    @DisplayName("对照组：status 有值时 Login 行为不变（0 成功 / 1 封禁）")
    void nonNullStatusBehaviourUnchanged() {
        jdbcTemplate.update("UPDATE users_user SET status = 0 WHERE uid = 9001");
        assertEquals(0L, userService.login(loginReq(RAW_PASSWORD)).getStatus(),
                "status=0 ⇒ 正常登录成功（Go: LoginResp{uid, status=0}）");

        jdbcTemplate.update("UPDATE users_user SET status = 1 WHERE uid = 9001");
        assertEquals(2L, userService.login(loginReq(RAW_PASSWORD)).getStatus(),
                "status=1 ⇒ 封禁（Go: LoginResp{status=2}，本实现 status=2）");
    }

    /**
     * 对照组：用户不存在仍走 {@code Status=1}，<b>不</b>被 NULL 判据误伤。
     */
    @Test
    @DisplayName("对照组：用户不存在仍是 Status=1（NOT_FOUND 未被 db_error 判据吞掉）")
    void missingUserStillReturnsStatusOne() {
        var req = LoginReq.newBuilder()
                .setUsername("no-such-user-at-all")
                .setPassword(RAW_PASSWORD)
                .build();

        assertEquals(1L, userService.login(req).getStatus(),
                "Go: loginlogic.go:47-52 user not found ⇒ LoginResp{status:1}，不是 INTERNAL");
    }

    // ==================================================================

    private LoginReq loginReq(String rawPassword) {
        return LoginReq.newBuilder()
                .setUsername(USERNAME)
                .setPassword(rawPassword)
                .build();
    }

    private Long statusRaw() {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM users_user WHERE uid = 9001", Long.class);
    }

    /** 实体侧也要能看到 null —— 这是整条链路的前提（MP 把 NULL 映射成 null 而不是 0）。 */
    @Test
    @DisplayName("前提：mapper 把 NULL status 映射成 null（而不是 0）")
    void mapperMapsNullStatusToNull() {
        UserEntity u = userMapper.findOneByUserName(USERNAME);
        assertTrue(u != null, "前置校验：用户应能查到");
        assertNull(u.getStatus(),
                "MP 把 NULL 映射成 null 正是缺陷的前提。若这里是 0，则本类其余用例失去意义");
    }
}