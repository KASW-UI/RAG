package com.rag.users.user.rpc;

import com.rag.users.user.proto.GetUserReq;
import com.rag.users.user.proto.LoginReq;
import com.rag.users.user.rpc.domain.UserEntity;
import com.rag.users.user.rpc.mapper.UserMapper;
import com.rag.users.user.rpc.service.UserDomainService;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 🔴 守门测试 —— {@code users_user.status IS NULL} 的读取语义
 * （<b>DRIFT-USERRPC-V-012</b>；本类取代 {@code UserRpcLoginNullStatusFailClosedTest}）。
 *
 * <h2>⚠️ 本类是被<b>推翻</b>的上一版守门测试的替代品，不是新增的第三种说法</h2>
 * 上一版类名 {@code UserRpcLoginNullStatusFailClosedTest} 断言「status IS NULL ⇒ Login 抛
 * {@code codes.Internal "DB查询失败"}」，理由写的是
 * 「{@code gorm@v1.31.2/scan.go:153 rows.Scan} 对 NULL → int64 直接返回错误」。
 * <b>该理由已被证伪</b>，上一版连同它锁的期望值一并作废（AGENTS.md §3.3.3：
 * 先改/删错误断言，再改实现）。保留本类的意义在于**把正确的那一侧钉死**，
 * 免得下一个 agent 再按同一句错的 Go 出处把它改回去。
 *
 * <h2>Go 侧行为：NULL <b>读成 0，不报错</b>（消费点三段，缺一段就会得出相反结论）</h2>
 * <ol>
 *   <li>{@code service/user/user/rpc/internal/model/data_model.go:11}（声明点）
 *       {@code Status int64 `gorm:"column:status;default:0"`} —— <b>无 not null</b>、值类型非指针；</li>
 *   <li>{@code gorm.io/gorm@v1.31.2/scan.go:21} {@code prepareValues} 与
 *       {@code schema/field.go:1009} {@code NewValuePool}
 *       ���是 {@code poolInitializer(reflect.PointerTo(field.IndirectFieldType))}
 *       ⇒ 传给 {@code rows.Scan} 的 dest 是 <b>{@code **int64}</b>；</li>
 *   <li>{@code database/sql/convert.go:431-435}
 *       {@code case reflect.Pointer: if src == nil { dv.SetZero(); return nil }}
 *       ⇒ <b>不报错</b>；随后
 *       {@code gorm@v1.31.2/schema/field.go:616-622} 的
 *       {@code case **int64: if data != nil && *data != nil { …SetInt(**data) }}
 *       在 {@code *data == nil} 时<b>不动作</b> ⇒ 字段留在<b>零值 0</b>。</li>
 * </ol>
 * ⚠️ {@code database/sql/convert.go:438-440} 那条
 * 「{@code converting NULL to int64 is unsupported}」<b>永远走不到</b> ——
 * dest 的 kind 是 {@code Pointer} 而非 {@code Int64}。<b>上一版就是踩在这里。</b>
 *
 * <h2>双侧活体对拍（2026-10-23 21:40–21:41，真进程 + 真 PG，两侧各自真栈）</h2>
 * <pre>
 *   status IS NULL 打 Login  ：Go 200 + token          RAG（误修期间）5001「系统内部错误」
 *   status IS NULL 打 GetUser ：Go 200，user 里 status 字段整个不出现
 *                              （Go 侧 omitempty，读到的值就是 0）
 *                             RAG（误修期间）5001「系统内部错误」
 * </pre>
 * ⇒ 「status=0 意为活跃，把 NULL 默认成活跃是 fail-open」<b>不成立</b>：
 * Go 走的正是「NULL ⇒ 0」这条路，且它<b>照样放行</b>。
 *
 * <h2>为什么必须真 PG（不能 mock）</h2>
 * 本类整条链路就是「NULL 真的进到 Java 侧时会被怎么处理」。
 * mock 数据层不执行 SQL，构造不出 MyBatis 跳过 setter 的那条路径。
 * 另外 {@code status} 列在 RAG 侧是 {@code NOT NULL}（{@code 01_create_user.sql:39}），
 * 与 Go 不同（<b>DRIFT-USERRPC-V-002</b>）⇒ 本类<b>先 DROP NOT NULL</b> 才能复现 Go 的可达性。
 *
 * <h2>本类的可证伪性</h2>
 * <ul>
 *   <li>把 {@code UserEntity#status} 的 {@code = 0L} 初始化值删掉 ⇒ 用例 1、2、3、6 立刻红；</li>
 *   <li>把 {@code UserDomainService#login} / {@code #getUser} 的 status-null 守卫加回来
 *       ⇒ 用例 2、3、6 立刻红（变 INTERNAL）。</li>
 * </ul>
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
class UserRpcLoginNullStatusReadsAsZeroTest {

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
    private static final long UID = 9001L;
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
                """, UID, USERNAME,
                com.rag.users.common.cryptx.PasswordUtil.encrypt(RAW_PASSWORD),
                "null-status@mail.test");

        assertNull(statusRaw(),
                "前置校验：种子行的 status 必须是 NULL（否则本用例测的是别的东西）");
    }

    // ==================================================================
    // 核心：NULL 必须被读成 0（与 Go 的 field.Set 留零值同形）
    // ==================================================================

    @Test
    @DisplayName("🔴 Go: status IS NULL 被读成 0 而不是 null（gorm field.Set 留零值）")
    void mapperMapsNullStatusToZeroLikeGo() {
        UserEntity u = userMapper.findOneByUserName(USERNAME);
        assertNotNull(u, "前置校验：用户应能查到");
        assertEquals(0L, u.getStatus(),
                "Go 把 NULL 读成零值 0（field.go:616-622 + convert.go:431-435），不是 null。"
                        + "RAG 侧靠 UserEntity 的 `= 0L` 初始化值 + MyBatis callSettersOnNulls=false 达成同形。"
                        + "若这里是 null，说明初始化值被删了或 setter 被调用了");
    }

    @Test
    @DisplayName("🔴 Go: status IS NULL 打 Login 是 status=0 成功，不是 INTERNAL")
    void loginWithNullStatusSucceedsLikeGo() {
        assertEquals(0L, userService.login(loginReq(RAW_PASSWORD)).getStatus(),
                "Go: status 读成 0 ⇒ status==1 判 false ⇒ 放行 ⇒ LoginResp{status:0}。"
                        + "双侧活体对拍实测 Go 返回 200 + token");
    }

    /**
     * 🔴 <b>反向对照</b>：NULL <b>不会</b>在 mapper 层就中断，
     * 因此<b>必然</b>走到密码校验分支。
     *
     * <p>上一版守门测试断言「用错误密码 + status=NULL 时必须抛 INTERNAL（判据在密码校验之前）」——
     * 那是<b>错的</b>：Go 的 mapper 层根本不报错，密码校验照跑。
     * ⇒ 用错误密码时返回的是 {@code LoginResp{status=1}}（D-002：密码错与用户不存在共用 1），
     * <b>不是</b> INTERNAL。这条用例把「判据放前面」这个错误实现钉死。
     */
    @Test
    @DisplayName("🔴 Go（反向对照）: status IS NULL + 错密码 ⇒ status=1，不是 INTERNAL")
    void nullStatusReachesPasswordCheck() {
        assertEquals(1L, userService.login(loginReq("definitely-wrong-password")).getStatus(),
                "Go 的 mapper 层不报错 ⇒ 必然走到密码校验 ⇒ 返回密码错分支的 LoginResp{status=1}，"
                        + "而不是 INTERNAL「DB查询失败」。若这里抛 INTERNAL，说明又加回了 status-null 守卫");
    }

    @Test
    @DisplayName("🔴 Go: status IS NULL 打 GetUser 返回 status=0，不抛 INTERNAL")
    void getUserWithNullStatusReturnsZeroLikeGo() {
        var resp = userService.getUser(GetUserReq.newBuilder().setUid(UID).build());
        assertEquals(0L, resp.getUser().getStatus(),
                "Go 侧同样把 0 交给 protobuf，再由 JSON 的 omitempty 省掉该字段"
                        + "（双侧活体实测 Go 的响应里 user.status 整个不出现）");
    }

    // ==================================================================
    // 对照组：确保没有误伤正常路径
    // ==================================================================

    @Test
    @DisplayName("对照组：status 有值时 Login 行为不变（0 成功 / 1 封禁）")
    void nonNullStatusBehaviourUnchanged() {
        jdbcTemplate.update("UPDATE users_user SET status = 0 WHERE uid = " + UID);
        assertEquals(0L, userService.login(loginReq(RAW_PASSWORD)).getStatus(),
                "status=0 ⇒ 正常登录成功（Go: LoginResp{uid, status=0}）");

        jdbcTemplate.update("UPDATE users_user SET status = 1 WHERE uid = " + UID);
        assertEquals(2L, userService.login(loginReq(RAW_PASSWORD)).getStatus(),
                "status=1 ⇒ 封禁（Go: LoginResp{status=2}，本实现 status=2）");
    }

    @Test
    @DisplayName("对照组：用户不存在仍是 Status=1（NOT_FOUND 未被吞掉）")
    void missingUserStillReturnsStatusOne() {
        var req = LoginReq.newBuilder()
                .setUsername("no-such-user-at-all")
                .setPassword(RAW_PASSWORD)
                .build();
        assertEquals(1L, userService.login(req).getStatus(),
                "Go: loginlogic.go:47-52 user not found ⇒ LoginResp{status:1}");
    }

    @Test
    @DisplayName("对照组：status=1（真封禁）时 GetUser 仍返回 1，不被 NULL 语义误伤")
    void getUserWithBannedStatusStillReturnsOne() {
        jdbcTemplate.update("UPDATE users_user SET status = 1 WHERE uid = " + UID);
        assertEquals(1L,
                userService.getUser(GetUserReq.newBuilder().setUid(UID).build()).getUser().getStatus());
    }

    /**
     * 对照组：uid 不存在仍抛 NOT_FOUND —— 确认上面的守卫删除没有把
     * {@code findOneByUid} 的 NotFound 分支一起吞掉。
     */
    @Test
    @DisplayName("对照组：uid 不存在仍抛 NOT_FOUND（不是 INTERNAL）")
    void getUserMissingStillThrowsNotFound() {
        StatusRuntimeException ex = assertThrows(StatusRuntimeException.class,
                () -> userService.getUser(GetUserReq.newBuilder().setUid(999999L).build()));
        assertEquals(io.grpc.Status.Code.NOT_FOUND, ex.getStatus().getCode());
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
                "SELECT status FROM users_user WHERE uid = " + UID, Long.class);
    }
}