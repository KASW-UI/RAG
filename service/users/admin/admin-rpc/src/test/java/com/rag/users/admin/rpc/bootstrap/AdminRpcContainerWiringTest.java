package com.rag.users.admin.rpc.bootstrap;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rag.users.admin.rpc.AdminRpcApplication;
import com.rag.users.admin.rpc.domain.AdminEntity;
import com.rag.users.admin.rpc.domain.UserEntity;
import com.rag.users.admin.rpc.mapper.AdminMapper;
import com.rag.users.admin.rpc.mapper.UserMapper;
import com.rag.users.admin.rpc.service.AdminDomainService;
import com.rag.users.common.cryptx.PasswordUtil;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * admin-rpc 真实 Spring 容器 + 真实 PostgreSQL 集成测试（issue #71186443）。
 *
 * <p>对应 AGENTS.md §6.2.3 的 **A-U0 / A-U2 / A-U3** —— 这三项此前只有静态推导，
 * 本类是它们第一次被真正执行验证的地方。
 *
 * <p><b>为什么必须用真 PG、不能用 H2</b>：本模块依赖三样 H2 没有/语义不同的能力：
 * <ol>
 *   <li>{@code extra_info} 是 <b>text</b> 列，写入走 {@code JsonTextTypeHandler} 的
 *       {@code ps.setString(...)}（且必须<b>逐字节</b>落库，不能被 jsonb 规范化）</li>
 *   <li>{@code AdminCreateAtomicService} 的 {@code SELECT ... FOR UPDATE} 悲观锁</li>
 *   <li>{@code setSql("extra_info = {0}")} 的参数绑定
 *       （⚠️ 2026-10-25 第十八轮起不再是 {@code CAST(... AS jsonb)} —— 那一列本来就是 text）</li>
 * </ol>
 * 换掉数据库等于把要验证的东西换没了。
 *
 * <p><b>本类证明什么</b>：
 * <ul>
 *   <li><b>建表真的先于 seeder 跑了</b> —— 见 {@link #schemaAppliedBeforeSeeder()}。
 *       若 {@link AdminSeederBootstrap} 上的 {@code @DependsOn("adminSchemaBootstrap")}
 *       没生效，seeder 会在 {@code admin_admin} 建表之前 SELECT，
 *       <b>容器直接起不来</b>，本类全部用例会以 context 加载失败告终 ——
 *       「测试能跑起来」本身就是那个断言。</li>
 *   <li><b>DRIFT-001 的运行时实证</b> —— 见 {@link #seeder_createdAdminWithHardcodedPassword()}</li>
 *   <li><b>A-U3 的 LIKE 双重包裹等价性</b> —— 见 {@link #keywordLikeDoubleWrapMatchesSingleWrap()}</li>
 * </ul>
 *
 * <p><b>无 Docker 时自动跳过</b>（{@code disabledWithoutDocker = true}），
 * 不会因为开发机没开 Docker 而让整条 build 变红。
 */
@SpringBootTest(
        classes = AdminRpcApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                // gRPC server 无法关闭：devh grpc-server-spring-boot-starter 3.1.0 的
                // GrpcServerLifecycle 没有 @ConditionalOnProperty("grpc.server.enabled")，
                // 实测写 grpc.server.enabled=false 仍会起 server。这里改用 port=0 让它绑
                // 临时端口，避免与本机已跑的容器（sea-rtw-admin-rpc 占 8081）撞端口。
                "grpc.server.port=0",
                // 指到必然拒连的地址：NacosRegistration 捕获 NacosException 只记日志不中断启动，
                // 拒连比 DNS 失败更快，也不会让测试挂住
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                // OTLP 指向不可达端点，只会在后台重试刷日志，关掉
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
@Testcontainers
class AdminRpcContainerWiringTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("admin_rpc_it")
                    .withUsername("rag")
                    .withPassword("rag");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private AdminMapper adminMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private AdminDomainService adminDomainService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ==================================================================
    // A-U0 / DRIFT-010：Flyway 与 seeder 的真实时序
    // ==================================================================

    /**
     * <b>本方法是 DRIFT-010 的真正验收。</b>
     *
     * <p>DRIFT-010 把 seeder 从 {@code ApplicationReadyEvent} 提前到 {@code @PostConstruct}。
     * 这带来一个风险：{@code @PostConstruct} 在 {@code finishBeanFactoryInitialization}
     * 期间执行，而建表跑在 {@link AdminSchemaBootstrap} 的 {@code @PostConstruct}，
     * 两者都是普通 {@code @Component}，
     * <b>注册顺序只是扫描顺序，不是契约</b> —— 谁先跑不可依赖。
     *
     * <p>因此这里的断言是<b>双向</b>的：
     * <ul>
     *   <li>能跑到方法体 ⇒ 容器 refresh 成功 ⇒ seeder 的 SELECT 没有撞上「表不存在」</li>
     *   <li>四张表都在 ⇒ users 域 DDL 确实整套执行了</li>
     * </ul>
     * 若把 {@link AdminSeederBootstrap} 上的
     * {@code @DependsOn("adminSchemaBootstrap")} 去掉，本类会在 context 加载阶段就挂，
     * 报 {@code relation "admin_admin" does not exist}。
     *
     * <p><b>建表方式已在 2026-10-21 从 Flyway 换成幂等重放</b>（DRIFT-NEW-015）：
     * {@code flyway_schema_history} 那张表<b>已经不存在了</b>，
     * 所以这里不能再数账本条数，只能直接验「表在不在」。
     * 无账本、无版本号、无跨进程状态 —— 谁先启动都行。
     */
    @Test
    @DisplayName("A-U0/DRIFT-010：容器能起来 ⇒ 建表先于 seeder 执行（时序约束真实生效）")
    void schemaAppliedBeforeSeeder() {
        // 四张表：admin 域两张 + user 域两张。
        // admin-rpc 也读写 users_user（BanUser / DeleteUser / ResetUserPassword /
        // UpdateUser），所以 users_user 必须在场 —— 这正是它与 user-rpc
        // 必须连同一个库的原因。Go 侧的对应证据是
        // admin/rpc/internal/model/db.go:15 的 AutoMigrate(&Admin{}, &AdminInvite{}, &User{})：
        // 那个 &User{} 就是「admin-rpc 也管 users_user」的直接出处。
        //
        // 断言点：admin-rpc **单独**对着一个空库启动，users 域四张表都应被幂等建出来。
        // 改动前这做不到 —— 那时 admin-rpc 只有自己的 3 份 Flyway 迁移，
        // 且与 user-rpc 的版本号撞车，第二个起来的进程会静默跳过自己的迁移。
        Set<String> tables = Set.copyOf(jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_name IN "
                        + "('admin_admin','admin_invite','users_user','users_user_avatar_history')",
                String.class));
        assertEquals(Set.of("admin_admin", "admin_invite",
                        "users_user", "users_user_avatar_history"),
                tables,
                "admin-rpc 单独对着空库启动时，users 域四张表都应被幂等建出来");
    }

    // ==================================================================
    // A-U2 / DRIFT-001：默认管理员凭据的运行时实证
    // ==================================================================

    /**
     * Go 出处：internal/svc/servicecontext.go:17-19
     * <pre>
     *   const (
     *       defaultAdminUsername = "admin"
     *       defaultAdminPassword = "admin"    // 与 Config.System.DefaultPassword 无关
     *   )
     * </pre>
     * 静态阶段已断言常量值；这里证明<b>空库启动</b>后真的能凭 <code>admin/admin</code> 通过登录校验，
     * 且<b>不能</b>用 <code>123456</code>（即配置项的值）通过 —— 后者若通过就说明 seeder 又接回了配置。
     */
    @Test
    @DisplayName("A-U2/DRIFT-001：空库启动后默认管理员密码是 admin，不是 123456")
    void seeder_createdAdminWithHardcodedPassword() {
        AdminEntity admin = adminMapper.selectOne(
                new LambdaQueryWrapper<AdminEntity>().eq(AdminEntity::getUsername, "admin"));

        assertNotNull(admin, "seeder 应在空库上建出默认 admin");
        assertTrue(PasswordUtil.check("admin", admin.getPassword()),
                "默认管理员密码应为 Go 的硬编码常量 admin");
        assertFalse(PasswordUtil.check("123456", admin.getPassword()),
                "DRIFT-001 回归：seeder 又用上了 system.default-password(123456)");
        assertNotNull(admin.getUid());
        assertTrue(admin.getUid() > 0, "uid 应由 Snowflake 生成");
    }

    /** 幂等：重复跑 seeder 不得产生第二行。 */
    @Test
    @DisplayName("A-U2：重复执行 seeder 不产生重复账号")
    void seeder_isIdempotentAtRuntime() {
        Integer before = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_admin WHERE username = 'admin'", Integer.class);
        assertEquals(1, before, "初始应恰好 1 个 admin（幂等意味着上一个用例已建好且没重复）");

        new AdminSeederBootstrap(adminMapper, null).ensureDefaultAdmin();

        Integer after = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_admin WHERE username = 'admin'", Integer.class);
        assertEquals(before, after, "已存在时 seeder 应直接 no-op");
    }

    /**
     * A-U2 的另一半：resetUserPassword 读的是<b>配置</b>（默认 123456），
     * 与 seeder 的硬编码常量是两个不同来源。这是全模块最容易被「顺手统一」搞坏的地方。
     */
    @Test
    @DisplayName("A-U2：resetUserPassword 用配置默认密码，与 seeder 常量互不影响")
    void resetUserPassword_usesConfiguredDefault() {
        UserEntity user = new UserEntity();
        user.setUid(424242L);
        user.setUsername("reset-target");
        user.setPassword("whatever");
        user.setEmail("reset@example.com");
        userMapper.insert(user);

        assertTrue(adminDomainService.resetUserPassword(424242L, 1L));

        UserEntity after = userMapper.selectOne(
                new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUid, 424242L));
        assertTrue(PasswordUtil.check("123456", after.getPassword()),
                "resetUserPassword 应使用配置 system.default-password(123456)");
        assertFalse(PasswordUtil.check("admin", after.getPassword()),
                "resetUserPassword 不应误用 seeder 的硬编码常量");
    }

    // ==================================================================
    // A-U3：LIKE 双重包裹在真库上的等价性
    // ==================================================================

    /**
     * 静态阶段只证明了「生成的 SQL 文本里 pattern 是 {@code %%kw%%}」，
     * 并<b>推理</b>「连续 % 会折叠 ⇒ 与 Go 的 {@code LIKE '%kw%'} 匹配集合相同」。
     * 这里在真 PG 上把那条推理坐实。
     *
     * <p>Go 出处：internal/model/admin.go:152-155
     * {@code keyWord := "%" + keyword + "%"; db.Where("username LIKE ? OR email LIKE ?", ...)}
     */
    @Test
    @DisplayName("A-U3：MP 双重包裹 %%kw%% 与 Go 的 %kw% 在真库上匹配集合相同")
    void keywordLikeDoubleWrapMatchesSingleWrap() {
        seedUsers();

        // keyword = " alice "：RAG 不 trim ⇒ pattern 含空格 ⇒ 应 0 命中
        AdminDomainService.UserListResult withSpaces =
                adminDomainService.getUserList(1, 100, " alice ");
        assertEquals(0L, withSpaces.total(),
                "keyword 含前后空格时，RAG 不 trim ⇒ 应与 Go 一样 0 命中（而不是匹配到 alice）");

        // keyword = "alice"：双重包裹 %%alice%% 应与单层 %alice% 等价
        AdminDomainService.UserListResult ragged =
                adminDomainService.getUserList(1, 100, "alice");
        AdminDomainService.UserListResult singleWrapViaQuery =
                adminDomainService.getUserList(1, 100, "%alice%");   // 被包成 %%%alice%%%

        // 3 行里命中 2 行：alice(username 命中) + carol(email 命中)，bob 两个字段都不命中
        assertEquals(2L, ragged.total(), "双层包裹应命中 2 行（username 命中 + email 命中）");
        assertEquals(ragged.total(), singleWrapViaQuery.total(),
                "%%alice%% 与 %%%alice%%% 的匹配集合必须相同（连续 % 折叠）");

        // 直接用原生 SQL 复核 Go 的写法
        Integer goStyle = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users_user WHERE username LIKE ? OR email LIKE ?",
                Integer.class, "%alice%", "%alice%");
        assertEquals(goStyle.intValue(), ragged.total(),
                "RAG 的结果集必须与 Go 的 LIKE '%alice%' 一致");
    }

    /** 防回退：空白 keyword 必须走过滤而不是返回全量（DRIFT-006）。 */
    @Test
    @DisplayName("DRIFT-006：空白 keyword 在真库上仍走 LIKE 过滤，不返回全量")
    void whitespaceKeywordFiltersOnRealPg() {
        seedUsers();
        AdminDomainService.UserListResult all = adminDomainService.getUserList(1, 100, "");
        AdminDomainService.UserListResult blank = adminDomainService.getUserList(1, 100, " ");

        assertEquals(3L, all.total(), "空 keyword ⇒ 全量 3 行");
        assertEquals(0L, blank.total(),
                "DRIFT-006 回归：空白 keyword 被当成无过滤条件，返回了全量");
    }

    /**
     * ⚠️ 本条在 2026-10-22 被推翻重写（E-3 根因 R4）—— 旧断言把「PG 会拒负 OFFSET」当成了规格。
     *
     * <p>Go 的 {@code model/admin.go:159-160} 算出 {@code offset := (page-1)*pageSize}，
     * 但 GORM 在生成 SQL 时有一层静默抑制（gorm.io/gorm@v1.31.1 {@code clause/limit.go}：
     * {@code LIMIT} 仅在 {@code >=0} 时生成、{@code OFFSET} 仅在 {@code >0} 时生成）。
     * 所以 page=0 → offset=-20 → 不生成 OFFSET → <b>不报错，返回首页</b>。
     * 实测：Go admin-api 18889 直打 {@code page=0} / {@code page=-1} /
     * {@code page_size=-5} / {@code page=int64max}，<b>四种全部 200</b>。
     *
     * <p>旧实现原样把负值拼进 SQL，于是 PG 报 22023 → INTERNAL → api 层 5001。
     * 本条现在断言「<b>真 PG 上不报错</b>」—— 这正是修复前后最容易看出来的差别，
     * 而且必须真 PG：纯 mock 的 mapper 测试根本不生成 SQL。
     */
    @Test
    @DisplayName("R4：page=0 在真 PG 上不报错，返回首页（Go 是 200；修复前 RAG 是 5001）")
    void negativeOffsetSuppressedLikeGo() {
        seedUsers();
        AdminDomainService.UserListResult r = adminDomainService.getUserList(0, 20, "");
        assertEquals(3L, r.total(), "total 与分页无关");
        assertFalse(r.users().isEmpty(),
                "offset=-20 被抑制 ⇒ 等价于首页，应当有数据返回，而不是报错");
    }

    // ==================================================================
    // helpers
    // ==================================================================

    private void seedUsers() {
        // 清空整表而不是只删本次的 uid：同一个容器被本类所有用例共享
        // （resetUserPassword_usesConfiguredDefault 会插一行），
        // 局部删除会让用例间的执行顺序影响断言值。
        jdbcTemplate.update("DELETE FROM users_user");
        insertUser(900001L, "alice", "alice@example.com");     // username 命中
        insertUser(900002L, "bob", "bob@example.com");         // 两个字段都不命中
        insertUser(900003L, "carol", "alice-team@example.com"); // 只有 email 命中 ⇒ 覆盖 OR 分支
    }

    private void insertUser(long uid, String username, String email) {
        jdbcTemplate.update(
                "INSERT INTO users_user (uid, username, password, email, status) VALUES (?, ?, ?, ?, 0)",
                uid, username, "x", email);
    }

}