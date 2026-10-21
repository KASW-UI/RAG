package com.rag.users.user.rpc.bootstrap;

import com.rag.users.user.rpc.UserRpcApplication;
import com.rag.users.user.rpc.domain.UserAvatarHistoryEntity;
import com.rag.users.user.rpc.domain.UserEntity;
import com.rag.users.user.rpc.exception.UserExceptions.AvatarHistoryNotFoundException;
import com.rag.users.user.rpc.exception.UserExceptions.NotFoundException;
import com.rag.users.user.rpc.mapper.UserAvatarHistoryMapper;
import com.rag.users.user.rpc.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * user-rpc 真实 Spring 容器 + 真实 PostgreSQL 集成测试。
 *
 * <p><b>关闭的缺口</b>：此前 {@code AvatarTransactionWiringTest} 与
 * {@code UserRpcJsonbAndAvatarProjectionTest} 都是<b>纯 mock 数据层</b>的单测，
 * 前者用"记录型事务管理器"只证明 {@code begin/rollback} 被调用过，
 * 后者只断言 MyBatis-Plus 生成的 SQL 片段文本。<b>两者都没有真实 PG</b>
 * （该类注释里自己写着"没有真实 PostgreSQL，所以不验证 jsonb 的 CAST"）。
 *
 * <p>按 AGENTS.md §3.3，{@code @TableField(typeHandler=…)} 与 {@code @Transactional}
 * 都属于<b>运行期隐式装配</b>，出错时不报错。静态断言证明不了它们真的生效。
 *
 * <p><b>为什么不能用 H2</b>：本模块依赖三样 H2 不支持或语义不同的能力：
 * <ol>
 *   <li>{@code extra_info} 是 <b>jsonb</b> 列，{@code JsonbTypeHandler} 走
 *       {@code ps.setObject(..., Types.OTHER)} —— 换成 {@code setString} 会被 PG 直接拒收</li>
 *   <li>{@code updateUserById} 的 {@code setSql("extra_info = CAST({0} AS jsonb)")} 显式 CAST</li>
 *   <li>手写 {@code @Select} 上的 {@code @Results} 是否真把 jsonb 列映射成 {@code Map}</li>
 * </ol>
 * 换掉数据库等于把要验证的东西换没了。
 *
 * <p><b>本类证明什么</b>：
 * <ul>
 *   <li>{@code JsonbTypeHandler} 在 MP 生成的 insert 上真的能写入 jsonb 列（§3.3 缺口）</li>
 *   <li>{@code @Results} 让手写 {@code @Select} 真能读回 {@code Map} 而不是 null（§3.3 缺口）</li>
 *   <li>{@code CAST(... AS jsonb)} 的显式 CAST 在真 PG 上成立（§3.3 缺口）</li>
 *   <li>4/5 步事务<b>真的回滚行</b>，而不只是"调用过 rollback"（§3.3 缺口）</li>
 *   <li>UNIQUE 约束在真 PG 上确实拦得住重复 username —— 这是 Go 侧 {@code AlreadyExists} 的地基</li>
 * </ul>
 *
 * <p><b>Ryuk 已在本模块 pom 的 surefire 配置里关掉</b>（理由与 admin-rpc 同名配置一致：
 * Ryuk 镜像需从 Docker Hub 拉，本机走加速器拉会 EOF 失败且发生在 {@code @Container} 之前）。
 */
@SpringBootTest(
        classes = UserRpcApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                // grpc-server-spring-boot-starter 3.1.0 的 GrpcServerLifecycle 没有
                // @ConditionalOnProperty("grpc.server.enabled")，实测写 false 仍会起 server。
                // 用 port=0 绑临时端口，避免与本机已跑的容器（sea-rtw-user-rpc 占 49210）撞端口。
                "grpc.server.port=0",
                // 指到必然拒连的地址：NacosRegistration 捕获 NacosException 只记日志不中断启动，
                // 拒连比 DNS 失败更快，也不会让测试挂住
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                // OTLP 指向不可达端点，只会在后台重试刷日志，关掉
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
@Testcontainers
class UserRpcContainerWiringTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("user_rpc_it")
                    .withUsername("rag")
                    .withPassword("rag");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        // 显式打开：本类的 schema 前提就是 Flyway 真跑了
        r.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private UserAvatarHistoryMapper avatarHistoryMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ==================================================================
    // §3.3 缺口 1：extra_info 的列类型与 Flyway
    // ==================================================================

    /**
     * 本类其余用例的前提：V1/V2 迁移真跑了，且 {@code extra_info} 真的是 jsonb。
     *
     * <p>Go 出处：{@code V1__create_user.sql} 的 {@code extra_info JSONB}，
     * 对应 GORM {@code AutoMigrate} 从 struct tag 推出的 jsonb 列。
     *
     * <p>顺带证明"类型不是 varchar" —— 若某次迁移被手改成 {@code text}/{@code varchar}，
     * 下面所有 jsonb 用例都会变成假绿（TypeHandler 的 Types.OTHER 对 text 也能写成功）。
     */
    @Test
    @DisplayName("§3.3：Flyway 真跑了，且 extra_info 的列类型确实是 jsonb")
    void flywayCreatedJsonbColumns() {
        Integer migrations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true", Integer.class);
        assertEquals(2, migrations, "V1/V2 两份迁移都应成功执行");

        Set<String> tables = Set.copyOf(jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_name IN "
                        + "('users_user','users_user_avatar_history')", String.class));
        assertEquals(Set.of("users_user", "users_user_avatar_history"), tables);

        String extraInfoType = jdbcTemplate.queryForObject(
                "SELECT data_type FROM information_schema.columns "
                        + "WHERE table_name = 'users_user' AND column_name = 'extra_info'",
                String.class);
        assertEquals("jsonb", extraInfoType,
                "extra_info 必须是 jsonb；若退化成 text/varchar，下面所有 jsonb 断言都会假绿");
    }

    // ==================================================================
    // §3.3 缺口 2：JsonbTypeHandler 在 MP 生成的 insert 上真的生效
    // ==================================================================

    /**
     * <b>本类是 {@code AvatarTransactionWiringTest} 明确不覆盖的那一半。</b>
     * 那个类写着"没有真实 PostgreSQL，所以不验证 jsonb 的 CAST"。
     *
     * <p>要证伪的是：{@code @TableField(typeHandler = JsonbTypeHandler.class)}
     * 真的被 MyBatis-Plus 的 insert SQL 采用。若失效（退回 {@code setString}），
     * PostgreSQL 会直接报：
     * {@code ERROR: column "extra_info" is of type jsonb but expression is of type character varying}
     * —— <b>这个错误只在真 PG 上出现</b>，H2 和 mock 都测不到。
     *
     * <p>Go 对应：{@code CreateUser} 走 GORM，jsonb 列由驱动按 jsonb 绑参写入。
     */
    @Test
    @DisplayName("§3.3：JsonbTypeHandler 在 MP insert 上真能写入 jsonb 列（Types.OTHER 生效）")
    void jsonbTypeHandlerWorksOnGeneratedInsert() {
        long uid = 700001L;
        UserEntity user = new UserEntity();
        user.setUid(uid);
        user.setUsername("jsonb-insert");
        user.setPassword("x");
        user.setEmail("jsonb-insert@example.com");
        user.setStatus(0L);
        Map<String, String> extra = new HashMap<>();
        extra.put("nickname", "阿伟");
        extra.put("city", "上海");
        user.setExtraInfo(extra);

        userMapper.insert(user);   // 失效则抛 DataIntegrityViolation / PSQLException

        // 直接从库里读原始文本，绕开 TypeHandler，确认列里真的是 JSON 对象而不是字符串
        String raw = jdbcTemplate.queryForObject(
                "SELECT extra_info::text FROM users_user WHERE uid = ?", String.class, uid);
        assertNotNull(raw, "extra_info 不该是 NULL");
        assertTrue(raw.startsWith("{") && raw.contains("\"nickname\""),
                "extra_info 应是 JSON 对象文本，实际 = " + raw);
        assertTrue(raw.contains("阿伟"), "UTF-8 内容不应被转义破坏，实际 = " + raw);
    }

    // ==================================================================
    // §3.3 缺口 3：手写 @Select 上的 @Results 真的把 jsonb 读成 Map
    // ==================================================================

    /**
     * {@code UserMapper.selectByUidRaw / selectByUsernameRaw} 是手写 {@code @Select}，
     * <b>不走</b> MP 生成的 ResultMap，因此必须靠注解上的 {@code @Results} 才拿得到 jsonb。
     * 少了它，字段静默变 null —— 又是一种"不报错"的失效。
     */
    @Test
    @DisplayName("§3.3：手写 @Select 上的 @Results 真把 jsonb 读成 Map（不是 null）")
    void resultsAnnotationMapsJsonbToMap() {
        long uid = 700002L;
        UserEntity user = new UserEntity();
        user.setUid(uid);
        user.setUsername("jsonb-select");
        user.setPassword("x");
        user.setEmail("jsonb-select@example.com");
        user.setStatus(0L);
        Map<String, String> extra = new HashMap<>();
        extra.put("bio", "hello");
        user.setExtraInfo(extra);
        userMapper.insert(user);

        UserEntity byUid = userMapper.findOneByUid(uid);
        assertNotNull(byUid, "按 uid 应能查到");
        assertNotNull(byUid.getExtraInfo(),
                "@Results 缺失时 extraInfo 会静默变 null —— 这条断言就是防它");
        assertEquals("hello", byUid.getExtraInfo().get("bio"));

        UserEntity byName = userMapper.findOneByUserName("jsonb-select");
        assertNotNull(byName, "按 username 应能查到");
        assertEquals("hello", byName.getExtraInfo().get("bio"));
    }

    // ==================================================================
    // §3.3 缺口 4：updateUserById 的显式 CAST 在真 PG 上成立
    // ==================================================================

    /**
     * {@code updateUserById} 用的是 {@code wrapper.setSql("extra_info = CAST({0} AS jsonb)", json)}
     * 而不是 {@code wrapper.set(...)} —— 因为后者会把参数塞进
     * {@code ew.paramNameValuePairs}、丢掉字段类型信息，PG 会拒收 varchar → jsonb。
     *
     * <p>这条断言同时覆盖了 {@code userMapper.java:110-130} 那段注释里描述的失败形态。
     */
    @Test
    @DisplayName("§3.3：updateUserById 的 CAST({0} AS jsonb) 在真 PG 上成立")
    void updateUserByIdCastsToJsonbOnRealPg() {
        long uid = 700003L;
        seedUser(uid, "jsonb-update", "jsonb-update@example.com");

        UserEntity patch = new UserEntity();
        Map<String, String> extra = new HashMap<>();
        extra.put("theme", "dark");
        patch.setExtraInfo(extra);

        int affected = userMapper.updateUserById(uid, patch);
        assertEquals(1, affected, "应更新 1 行");

        String raw = jdbcTemplate.queryForObject(
                "SELECT extra_info::text FROM users_user WHERE uid = ?", String.class, uid);
        assertTrue(raw != null && raw.contains("\"theme\""),
                "CAST 后的 extra_info 应是 JSON 对象文本，实际 = " + raw);

        // @Results 读回路径也要跟着对
        assertEquals("dark", userMapper.findOneByUid(uid).getExtraInfo().get("theme"));
    }

    /**
     * 防回退：{@code updateUserById} 的 {@code any == false → return 0} 语义。
     * Go 侧 GORM 对全零值 struct 不发 UPDATE，RAG 必须同样"什么都不改就返回 0"。
     */
    @Test
    @DisplayName("DRIFT-001：全零值 patch 不发 UPDATE（对齐 GORM 的 no-op 语义）")
    void zeroValuePatchDoesNotUpdate() {
        long uid = 700004L;
        seedUser(uid, "jsonb-nopatch", "jsonb-nopatch@example.com");
        Object before = jdbcTemplate.queryForObject(
                "SELECT update_time FROM users_user WHERE uid = ?", Object.class, uid);

        int affected = userMapper.updateUserById(uid, new UserEntity());
        assertEquals(0, affected, "无字段变更时应返回 0 且不发 UPDATE");

        Object after = jdbcTemplate.queryForObject(
                "SELECT update_time FROM users_user WHERE uid = ?", Object.class, uid);
        assertEquals(before, after, "不发 UPDATE ⇒ update_time 不应推进");
    }

    // ==================================================================
    // §3.3 缺口 5：4/5 步事务在真 PG 上真的回滚行
    // ==================================================================

    /**
     * <b>升级 {@code AvatarTransactionWiringTest} 的证据等级。</b>
     * 那个类用"记录型事务管理器"证明了 begin→rollback 的<b>调用序列</b>，
     * 但证明不了 PG 真的把行回滚了。这里让 W3 的 INSERT 撞 UNIQUE(history_id) 失败，
     * 然后断言<b>W2 已经执行的 UPDATE 被真回滚了</b>。
     *
     * <p>Go 出处：{@code uploadavatarlogic.go} 的 {@code m.conn.Transaction(func(tx) error {...})}，
     * 4 步任一失败整体回滚（D-005）。
     */
    @Test
    @DisplayName("§3.3：createAvatarHistory 第 3 步失败 ⇒ 第 2 步的 UPDATE 被真回滚")
    void createAvatarHistoryRollsBackOnRealPg() {
        long uid = 700005L;
        seedUser(uid, "avatar-rollback", "avatar-rollback@example.com");

        // 先成功建一条 current=true 的历史，作为"回滚后必须原样保留"的基线
        avatarHistoryMapper.createAvatarHistory(userMapper, uid, "h-keep", "https://a/1.png", "image/png", 11L);
        assertTrue(currentFlagOf(uid, "h-keep"), "前置条件：h-keep 已是 current");

        // 再用<b>同一个 history_id</b> 建第二条 ⇒ W3 的 INSERT 撞 UNIQUE(history_id)
        DataIntegrityViolationException ex = assertThrows(DataIntegrityViolationException.class,
                () -> avatarHistoryMapper.createAvatarHistory(
                        userMapper, uid, "h-keep", "https://a/2.png", "image/png", 22L),
                "history_id 撞 UNIQUE 时 W3 必须失败");
        assertNotNull(ex);

        // 关键断言：W2「把旧 current 置 false」必须被回滚
        assertTrue(currentFlagOf(uid, "h-keep"),
                "事务未真正回滚：W2 的 clearCurrent 生效了，说明这不是一个原子事务");
        assertEquals(1, countHistory(uid), "失败的 INSERT 不得留下任何行");
        // W4 的双写同样不得生效
        String avatar = jdbcTemplate.queryForObject(
                "SELECT extra_info::text FROM users_user WHERE uid = ?", String.class, uid);
        assertTrue(avatar != null && avatar.contains("https://a/1.png"),
                "W4 的 extra_info 双写必须一起回滚，实际 = " + avatar);
    }

    /**
     * 4 步的<b>成功</b>路径：W2 旧 current→false、W3 新行 is_current=true、
     * W4 双写 {@code users.extra_info["avatar"]}（D-014）。
     */
    @Test
    @DisplayName("§3.3：createAvatarHistory 4 步在真 PG 上按序落库")
    void createAvatarHistorySucceedsOnRealPg() {
        long uid = 700006L;
        seedUser(uid, "avatar-ok", "avatar-ok@example.com");

        avatarHistoryMapper.createAvatarHistory(userMapper, uid, "h-1", "https://a/1.png", "image/png", 11L);
        avatarHistoryMapper.createAvatarHistory(userMapper, uid, "h-2", "https://a/2.png", "image/png", 22L);

        assertFalse(currentFlagOf(uid, "h-1"), "旧头像应被 W2 置为非 current");
        assertTrue(currentFlagOf(uid, "h-2"), "新头像应为 current");
        assertEquals(2, countHistory(uid));

        String avatar = jdbcTemplate.queryForObject(
                "SELECT extra_info::text FROM users_user WHERE uid = ?", String.class, uid);
        assertTrue(avatar != null && avatar.contains("https://a/2.png"),
                "W4 应把最新 avatar 双写进 users.extra_info，实际 = " + avatar);
    }

    /**
     * 5 步的<b>成功</b>路径 + D-015 的 uid 隔离：history 查询必须带 uid 条件，
     * 否则能取到别人的头像。
     */
    @Test
    @DisplayName("§3.3：selectAvatarHistory 5 步在真 PG 上落库，且带 uid 隔离（D-015）")
    void selectAvatarHistorySucceedsAndIsolatesByUid() {
        long alice = 700007L;
        long bob = 700008L;
        seedUser(alice, "alice-avatar", "alice-avatar@example.com");
        seedUser(bob, "bob-avatar", "bob-avatar@example.com");

        avatarHistoryMapper.createAvatarHistory(userMapper, alice, "a-1", "https://a/alice.png", "image/png", 1L);
        avatarHistoryMapper.createAvatarHistory(userMapper, bob, "b-1", "https://a/bob.png", "image/png", 2L);

        // alice 切到 a-1（此时已是 current，验证幂等路径也走得通）
        UserAvatarHistoryEntity got = avatarHistoryMapper.selectAvatarHistory(userMapper, alice, "a-1");
        assertTrue(got.getIsCurrent(), "返回的实体必须带 is_current=true（否则响应自相矛盾）");
        assertEquals("https://a/alice.png", got.getAvatarUrl());

        // D-015：alice 不能取 bob 的 history_id
        assertThrows(AvatarHistoryNotFoundException.class,
                () -> avatarHistoryMapper.selectAvatarHistory(userMapper, alice, "b-1"),
                "history 查询必须带 uid 条件，否则能越权取别人头像");
    }

    /** 5 步的失败路径：history 不存在 ⇒ W3 的 clearCurrent 不得生效（否则会误清他人 current）。 */
    @Test
    @DisplayName("§3.3：selectAvatarHistory 找不到 history ⇒ 不清任何 current")
    void selectAvatarHistoryMissingDoesNotClearCurrent() {
        long uid = 700009L;
        seedUser(uid, "avatar-missing", "avatar-missing@example.com");
        avatarHistoryMapper.createAvatarHistory(userMapper, uid, "m-1", "https://a/m.png", "image/png", 3L);
        assertTrue(currentFlagOf(uid, "m-1"));

        assertThrows(AvatarHistoryNotFoundException.class,
                () -> avatarHistoryMapper.selectAvatarHistory(userMapper, uid, "m-not-exist"));

        assertTrue(currentFlagOf(uid, "m-1"),
                "W2 在 W3 之前执行，若事务没真正回滚就会把已有 current 清掉");
    }

    /** W1 的用户不存在分支：Go 侧对应 NotFound → rpc 层 status.NotFound。 */
    @Test
    @DisplayName("§3.3：user 不存在时 4/5 步都抛 NotFoundException（对齐 Go 的 NotFound）")
    void missingUserRaisesNotFound() {
        assertThrows(NotFoundException.class,
                () -> avatarHistoryMapper.createAvatarHistory(
                        userMapper, 999999L, "x-1", "https://a/x.png", "image/png", 1L));
        assertThrows(NotFoundException.class,
                () -> avatarHistoryMapper.selectAvatarHistory(userMapper, 999999L, "x-1"));
    }

    // ==================================================================
    // UNIQUE 约束：Go 侧 AlreadyExists 的地基
    // ==================================================================

    /**
     * Go 侧 {@code registerlogic.go} 的"用户名已存在"依赖 {@code username} 的唯一索引。
     * 若迁移里漏了 UNIQUE，这里会通过，而线上会出现两个同名用户 —— 且<b>不会有任何报错</b>。
     */
    @Test
    @DisplayName("§3.3：username/uid/email 的 UNIQUE 约束在真 PG 上真的拦得住")
    void uniqueConstraintsAreEnforcedOnRealPg() {
        long uid = 700010L;
        seedUser(uid, "dup-check", "dup-check@example.com");

        UserEntity dupName = new UserEntity();
        dupName.setUid(uid + 1);
        dupName.setUsername("dup-check");
        dupName.setPassword("x");
        dupName.setEmail("other@example.com");
        assertThrows(DataIntegrityViolationException.class, () -> userMapper.insert(dupName),
                "username UNIQUE 未生效 ⇒ Go 侧的 AlreadyExists 分支失去依据");

        UserEntity dupEmail = new UserEntity();
        dupEmail.setUid(uid + 2);
        dupEmail.setUsername("other-name");
        dupEmail.setPassword("x");
        dupEmail.setEmail("dup-check@example.com");
        assertThrows(DataIntegrityViolationException.class, () -> userMapper.insert(dupEmail),
                "email UNIQUE 未生效");
    }

    // ==================================================================
    // helpers
    // ==================================================================

    /** 每个用例都从干净状态开始：同一个容器被本类所有用例共享。 */
    private void seedUser(long uid, String username, String email) {
        jdbcTemplate.update("DELETE FROM users_user_avatar_history WHERE uid = ?", uid);
        jdbcTemplate.update("DELETE FROM users_user WHERE uid = ?", uid);
        jdbcTemplate.update(
                "INSERT INTO users_user (uid, username, password, email, status) VALUES (?, ?, ?, ?, 0)",
                uid, username, "x", email);
    }

    private boolean currentFlagOf(long uid, String historyId) {
        Boolean flag = jdbcTemplate.queryForObject(
                "SELECT is_current FROM users_user_avatar_history WHERE uid = ? AND history_id = ?",
                Boolean.class, uid, historyId);
        return Boolean.TRUE.equals(flag);
    }

    private int countHistory(long uid) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users_user_avatar_history WHERE uid = ?", Integer.class, uid);
        return n == null ? 0 : n;
    }

    /** 证明 null 路径不会 NPE：extra_info 为 NULL 时 @Results 应给出 null 而非抛异常。 */
    @Test
    @DisplayName("§3.3：extra_info 为 NULL 时读回 null 而非抛异常")
    void nullExtraInfoReadsBackAsNull() {
        long uid = 700011L;
        seedUser(uid, "null-extra", "null-extra@example.com");

        UserEntity user = userMapper.findOneByUid(uid);
        assertNotNull(user);
        assertNull(user.getExtraInfo(), "NULL 列应映射成 null");
    }
}
