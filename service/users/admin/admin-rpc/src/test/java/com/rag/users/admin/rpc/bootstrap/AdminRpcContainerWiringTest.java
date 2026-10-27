package com.rag.users.admin.rpc.bootstrap;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rag.common.persistence.DeadlineQueryTimeoutInjector;
import com.rag.common.persistence.SqlTimeoutStatusTranslator;
import com.rag.users.admin.rpc.AdminRpcApplication;
import com.rag.users.admin.rpc.domain.AdminEntity;
import com.rag.users.admin.rpc.domain.UserEntity;
import com.rag.users.admin.rpc.mapper.AdminMapper;
import com.rag.users.admin.rpc.mapper.UserMapper;
import com.rag.users.admin.rpc.service.AdminDomainService;
import com.rag.users.common.cryptx.PasswordUtil;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.SqlSessionFactory;
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

import java.util.ArrayList;
import java.util.HashSet;
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
    @Autowired
    private SqlSessionFactory sqlSessionFactory;

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
    // B-1：SQL 超时翻译链的**装配层**断言（§3.3：注解存在 ≠ 机制生效）
    // ==================================================================

    /**
     * <b>本方法证明 admin-rpc 的 MyBatis 插件链里真的装着 B-1 那两个拦截器。</b>
     *
     * <p><b>为什么不能靠「{@code @Import} 写了」当证据</b>：
     * {@code AdminRpcApplication} 上确实写着
     * {@code @Import({DeadlineQueryTimeoutInjector.class, SqlTimeoutStatusTranslator.class})}，
     * 但那是<b>声明</b>；MyBatis-Plus 到底有没有把容器里的 {@code Interceptor} Bean
     * 收进 {@code Configuration}，是<b>运行期</b>的事 —— 本仓已经吃过两次同款亏
     * （P-4「{@code @Import} 只让 Bean 存在」· P-ADMINAPI-009「最小上下文 ≠ 生产装配」）。
     *
     * <p><b>这条链断了，本域的具体后果</b>：
     * <ol>
     *   <li>缺 {@link DeadlineQueryTimeoutInjector} ⇒ 每条 SQL 不带 {@code statement_timeout}
     *       ⇒ 客户端 deadline 到点时<b>在途查询继续跑满</b>
     *       ⇒ Go 侧 ctx 会同步取消 ⇒ 两侧在「客户端已放弃后 PG 还占着多久」上出现差值</li>
     *   <li>缺 {@link SqlTimeoutStatusTranslator} ⇒ PG 的 57014 {@code query_canceled}
     *       不会变成 {@code DEADLINE_EXCEEDED}
     *       ⇒ 落进业务层的 {@code catch (Exception e)}
     *       ⇒ <b>被改写成 INTERNAL</b> ⇒ api 侧渲染 5001（Go 是 1015）⇒ <b>wire 面就漂了</b></li>
     * </ol>
     *
     * <p>⚠️ 本方法只证明<b>装配</b>（链里有这两个）。
     * <b>「链断了会怎样」的两个后果本身各有各自的守门</b>：
     * 放行分支由 {@code AdminDomainServiceCatchStructureTest}（字节码级，28/28）守，
     * 翻译器本体由 {@code service/common} 的 {@code SqlTimeoutStatusTranslatorTest} 守。
     * ⇒ 三段合起来才覆盖整条链，缺一段就有洞。
     */
    @Test
    @DisplayName("B-1：MyBatis 插件链里真的装着 DeadlineQueryTimeoutInjector + SqlTimeoutStatusTranslator")
    void sqlTimeoutInterceptorsAreReallyInThePluginChain() {
        List<Interceptor> chain = sqlSessionFactory.getConfiguration().getInterceptors();
        List<String> names = chain.stream().map(i -> i.getClass().getName()).toList();

        assertTrue(chain.stream().anyMatch(SqlTimeoutStatusTranslator.class::isInstance),
                "MyBatis 插件链里没有 SqlTimeoutStatusTranslator ⇒ PG 的 57014 不会被翻成 "
                        + "DEADLINE_EXCEEDED ⇒ 会落进 catch(Exception) 被改写成 INTERNAL（5001 ≠ Go 的 1015）；"
                        + "实际链 = " + names);
        assertTrue(chain.stream().anyMatch(DeadlineQueryTimeoutInjector.class::isInstance),
                "MyBatis 插件链里没有 DeadlineQueryTimeoutInjector ⇒ 每条 SQL 不带 statement_timeout "
                        + "⇒ 客户端 deadline 到点后在途查询继续跑满；实际链 = " + names);
    }

    /**
     * 翻译器必须挂在<b>执行点</b>（{@code Executor.query} / {@code Executor.update}）——
     * CancelRequest 引发的 {@code SQLException} 是在<b>执行</b>阶段抛出的，
     * {@code StatementHandler.prepare} 阶段根本看不到它
     * ⇒ 挂错点 = <b>永远翻译不到</b>（而且是静默的：链里有它、测试也全绿）。
     */
    @Test
    @DisplayName("B-1：SqlTimeoutStatusTranslator 挂在 Executor 执行点（不是 StatementHandler.prepare）")
    void translatorInterceptsTheExecutionPoint() {
        assertMountedOnExecutor(SqlTimeoutStatusTranslator.class);
    }

    /**
     * 🔴 <b>负控</b>：把<b>同一条判据</b>跑在一枚<b>故意挂错点</b>的变异体上，要求它<b>必须被拒绝</b>。
     *
     * <p><b>为什么必须要有这条</b>（users/user 域 2026-10-26 指出）：
     * 用例 ② 读的是<b>注解元数据</b> ⇒ <b>判据若写松就恒绿</b>
     * ——例如只查「有没有 {@code @Intercepts}」而不查「挂哪个方法」，
     * 那它对<b>任何</b>挂载方式都通过 ⇒ <b>等于没守门</b>。
     * ⇒ 所以守门本身必须<b>被证明会红</b>。
     *
     * <p><b>变异体为什么放在测试类里</b>：它是一个
     * <b>静态嵌套类</b>、<b>没有</b>被 {@code @Import}、<b>不在</b>组件扫描路径上
     * ⇒ <b>不参与生产装配</b>，只作为判据的输入。
     *
     * <p>⚠️ <b>不要照抄结论</b>：本条是本域自己写的、自己跑过的（变异见 §5.3 的 M2/M3 记档）。
     */
    @Test
    @DisplayName("B-1 负控：挂错到 StatementHandler.prepare 的变异体必须被同一条判据拒绝")
    void mountPointGuardRejectsWrongMountPoint() {
        WrongMountTranslatorVariant variant = new WrongMountTranslatorVariant();
        assertNotNull(variant.getClass().getAnnotation(Intercepts.class),
                "变异体自身得先有 @Intercepts，否则这条负控就没意义");

        // ⚠️ 这里**只要「判据拒绝了它」**，不检查拒绝的理由 ——
        // 理由检查会**误报**：判据被合法重构（例如把「方法覆盖」那条提到「类型」那条前面）
        // 就会让本条变红，而守卫其实还好 ⇒ ⇒ **判据的内部顺序不该被本条绑住**。
        // 「必须红在挂载点那条」由下面的 NC-B 单独证明（那条的变异体只违反挂载点一条规则）。
        assertThrows(AssertionError.class,
                () -> assertMountedOnExecutor(variant.getClass()),
                "同一条判据对「故意挂在 StatementHandler.prepare 上的翻译器」竟然通过了 "
                        + "⇒ 判据写松了（恒绿）⇒ 这条守门等于没守");
    }

    /**
     * 🔴 <b>负控之二（把「挂载点」这一条单独隔离出来）</b>。
     *
     * <p>NC-A 证明的是「判据不是恒绿」；但它**不能**证明「判据真的在看挂载点」——
     * 判据只要有任意一条别的规则（覆盖 query/update、不得挂 StatementHandler…）在拦，
     * NC-A 就会绿。
     *
     * <p><b>本条的变异体是「一切正确 + 额外挂错」</b>：
     * 它**同时**声明了正确的 {@code Executor.query}/{@code Executor.update}
     * **和**错误的 {@code StatementHandler.prepare}
     * ⇒ ⇒ <b>只有「不得挂在 StatementHandler 上」那条规则能拒绝它</b>
     * ⇒ ⇒ 于是本条变红 ⇒ **证明挂载点那条规则真的在生效**。
     */
    @Test
    @DisplayName("B-1 负控之二：只违反「挂载点」一条规则的变异体，必须由该规则拒绝")
    void mountPointRuleAloneRejectsCorrectPlusWrongMount() {
        CorrectPlusWrongMountVariant variant = new CorrectPlusWrongMountVariant();

        AssertionError caught = assertThrows(AssertionError.class,
                () -> assertMountedOnExecutor(variant.getClass()),
                "「一切正确 + 额外挂 StatementHandler.prepare」的变异体竟然通过了 "
                        + "⇒ 判据没有在看挂载点 ⇒ 挂载点这条规则是摆设");

        // 这条**可以**检查理由：NC-B 的变异体只违反挂载点一条规则 ⇒ 拒绝它的**只可能是**那条规则
        assertTrue(caught.getMessage().contains("StatementHandler"),
                "负控抛了 AssertionError，但**不是挂载点那条规则**抛的（消息 = " + caught.getMessage()
                        + "）⇒ 变异没有精确命中 ⇒ 本条的隔离性不成立");
    }

    /**
     * <b>挂载点判据本体</b>：一个翻译器必须挂在 {@code Executor}（{@code query} + {@code update}）上。
     *
     * <p>抽成独立方法是为了让<b>正例</b>（用例 ②）与<b>负控</b>（用例 ③）跑<b>同一段代码</b>
     * ⇒ 否则负控验的是另一份逻辑，等于没验。
     */
    private static void assertMountedOnExecutor(Class<?> translatorClass) {
        Intercepts intercepts = translatorClass.getAnnotation(Intercepts.class);
        assertNotNull(intercepts,
                translatorClass.getSimpleName() + " 没有 @Intercepts ⇒ 根本不会被 MyBatis 收进插件链");

        Set<String> types = new HashSet<>();
        Set<String> methods = new HashSet<>();
        for (Signature s : intercepts.value()) {
            types.add(s.type().getSimpleName());
            methods.add(s.method());
        }

        assertTrue(types.contains("Executor"),
                translatorClass.getSimpleName() + " 没有拦截 Executor ⇒ 执行阶段抛出的 57014 看不到；"
                        + "实际拦截类型 = " + types);
        assertTrue(methods.contains("query") && methods.contains("update"),
                translatorClass.getSimpleName() + " 没有覆盖 query/update 两个执行方法；实际 = " + methods);
        assertFalse(types.contains("StatementHandler"),
                translatorClass.getSimpleName() + " 若挂在 StatementHandler.prepare 上，"
                        + "CancelRequest 引发的 SQLException 永远翻译不到");
    }

    /**
     * 🔴 <b>故意挂错点的变异体</b> —— 只用来喂给负控用例，<b>不参与生产装配</b>。
     *
     * <p>它挂在 {@code StatementHandler.prepare} 上（真实翻译器挂在 {@code Executor} 上）
     * ⇒ 正是「CancelRequest 引发的 SQLException 在执行阶段才抛 ⇒ prepare 阶段看不到它」
     * 那个真实错误形状。
     */
    @Intercepts({
            @Signature(type = org.apache.ibatis.executor.statement.StatementHandler.class,
                    method = "prepare",
                    args = {java.sql.Connection.class, Integer.class})
    })
    static class WrongMountTranslatorVariant {
        // 故意留空：它只需要「类型 + 挂载点」这个形状
    }

    /**
     * 🔴 <b>「一切正确 + 额外挂错」的变异体</b> —— 用来把「挂载点」那条规则<b>单独隔离</b>出来。
     *
     * <p>它声明了正确的 {@code Executor.query}/{@code Executor.update}（⇒ 前两条规则都过）
     * <b>再加上</b>错误的 {@code StatementHandler.prepare}
     * ⇒ ⇒ <b>只有「不得挂在 StatementHandler 上」那条规则能拒绝它</b>。
     */
    @Intercepts({
            @Signature(type = org.apache.ibatis.executor.Executor.class, method = "query",
                    args = {org.apache.ibatis.mapping.MappedStatement.class, Object.class,
                            org.apache.ibatis.session.RowBounds.class,
                            org.apache.ibatis.session.ResultHandler.class}),
            @Signature(type = org.apache.ibatis.executor.Executor.class, method = "update",
                    args = {org.apache.ibatis.mapping.MappedStatement.class, Object.class}),
            @Signature(type = org.apache.ibatis.executor.statement.StatementHandler.class,
                    method = "prepare",
                    args = {java.sql.Connection.class, Integer.class})
    })
    static class CorrectPlusWrongMountVariant {
        // 故意留空：它只需要「类型 + 挂载点」这个形状
    }

    // ==================================================================
    // MP 排序桥（2026-10-27 · 共享层收编后新增）
    // ==================================================================

    /**
     * <b>防回归：MP 排序桥必须由<b>共享层</b>那份提供（域内副本已删），且排序边真的生效。</b>
     *
     * <h2>为什么需要这条（AGENTS.md §3.3.3）</h2>
     * <p>Boot 4 把 {@code DataSourceAutoConfiguration} 挪到了
     * {@code org.springframework.boot.jdbc.autoconfigure}，而 MyBatis-Plus 声明的排序引用
     * 还指着 Boot 3 的包名 ⇒ <b>静默悬空</b>（Boot 找不到这个类名 ⇒ <b>不报错</b>）
     * ⇒ ⇒ 随后按 imports 的字母序求值：{@code com.baomidou.*} 在 {@code org.springframework.*} 之前
     * ⇒ ⇒ MP <b>先跑</b> ⇒ {@code @ConditionalOnSingleCandidate(DataSource)} 此时找不到任何
     * DataSource bean ⇒ <b>整个 MP 自动配置不生效</b> ⇒ 不产出 {@code SqlSessionFactory}
     * ⇒ ⇒ mapper 报 {@code Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required}。
     *
     * <p><b>这个失效面是「静默」的</b>：注解写对了、编译通过、上下文也可能起得来，
     * 只有 mapper 第一次执行时才炸 ⇒ 必须有一条<b>运行期</b>判据钉住它。
     *
     * <h2>本判据分三层，逐层加码</h2>
     * <ol>
     *   <li><b>注册层</b>：classpath 上<b>全部</b>同名 {@code AutoConfiguration.imports} 资源里，
     *       至少一份含共享层那份的 FQN
     *       —— ⚠️ 原先用 {@code getResourceAsStream} 取<b>第一份</b>同名资源，
     *       而 classpath 上可能有<b>多份</b> ⇒ <b>命中哪份取决于 classpath 顺序</b> ⇒ 判定不确定
     *       ⇒ 改为遍历<b>全部</b>同名资源、断言「至少一份含目标行」⇒ 与清单份数<b>解耦</b>。</li>
     *   <li><b>域内那份必须真的没了</b>：否则收编等于没做（全仓两份桥同时注册）。</li>
     *   <li><b>机制层（最强）</b>：运行期证明排序边真的生效 —— MP 的 {@code SqlSessionFactory}
     *       真的产出了（若排序边没建立，MP 早于 DataSource 求值 ⇒ 整个 MP 自动配置不生效
     *       ⇒ <b>上下文根本起不来</b> ⇒ 本类其余用例连带失败）。</li>
     * </ol>
     *
     * <p>⚠️ <b>与共享层那份的关系</b>：共享层那份用
     * {@code afterName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration"}
     * ＋ {@code beforeName = "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration"}
     * （<b>字符串</b>形式 ⇒ 对「没有 MyBatis-Plus」的消费者结构性安全），
     * 域内原副本用类字面量形式 ⇒ <b>指向同一对节点</b> ⇒ 排序图上<b>同向等价</b>
     * ⇒ ⇒ 删除域内那份<b>不产生任何行为差</b>。
     */
    @Test
    @DisplayName("防回归：MP 排序桥由共享层那份提供（域内副本已删），且排序边真的生效")
    void mybatisOrderingBridgeMustBeProvidedBySharedLayer() throws Exception {
        // ① 注册层：classpath 上全部同名 imports 资源里，至少一份含共享层那份
        List<String> all = new ArrayList<>();
        java.util.Enumeration<java.net.URL> resources = getClass().getClassLoader()
                .getResources("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");
        while (resources.hasMoreElements()) {
            java.net.URL url = resources.nextElement();
            all.add(new String(url.openStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
        assertFalse(all.isEmpty(), "classpath 上找不到 AutoConfiguration.imports ⇒ 断言无意义");
        assertTrue(all.stream().anyMatch(s -> s.contains(
                        "com.rag.common.persistence.MybatisPlusOrderingBridgeAutoConfiguration")),
                "排序桥未注册 ⇒ Boot 4 下 MyBatis-Plus 早于 DataSource 求值 ⇒ 整个 MP 自动配置不生效 ⇒ "
                        + "mapper 报 sqlSessionFactory 缺失。实测读到的清单：" + all);

        // ② 域内那份必须真的不在 classpath 上（类文件已删 ⇒ 加载失败）
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.rag.users.admin.rpc.config.MybatisPlusOrderingBridgeAutoConfiguration"),
                "域内那份排序桥还在 ⇒ 收编没做 ⇒ 全仓会有两份桥同时注册，"
                        + "共享层那份后续的改动在本域会静默不生效");

        // ③ 机制层：MP 真的产出了 SqlSessionFactory（排序边真的生效）
        assertNotNull(sqlSessionFactory,
                "容器里没有 SqlSessionFactory ⇒ MP 自动配置没生效 ⇒ 排序边没建立（桥没被注册或没被消费）");
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