package com.rag.users.user.rpc.bootstrap;

import com.rag.common.persistence.DeadlineQueryTimeoutInjector;
import com.rag.common.persistence.SqlTimeoutStatusTranslator;
import com.rag.users.user.rpc.UserRpcApplication;
import com.rag.users.user.rpc.domain.UserAvatarHistoryEntity;
import com.rag.users.user.rpc.domain.UserEntity;
import com.rag.users.user.rpc.exception.UserExceptions.AvatarHistoryNotFoundException;
import com.rag.users.user.rpc.exception.UserExceptions.NotFoundException;
import com.rag.users.user.rpc.mapper.UserAvatarHistoryMapper;
import com.rag.users.user.rpc.mapper.UserMapper;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSessionFactory;
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

import java.sql.Connection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * user-rpc 真实 Spring 容器 + 真实 PostgreSQL 集成测试。
 *
 * <p><b>关闭的缺口</b>：此前 {@code AvatarTransactionWiringTest} 与
 * {@code UserRpcJsonTextAndAvatarProjectionTest} 都是<b>纯 mock 数据层</b>的单测，
 * 前者用"记录型事务管理器"只证明 {@code begin/rollback} 被调用过，
 * 后者只断言 MyBatis-Plus 生成的 SQL 片段文本。<b>两者都没有真实 PG</b>
 * （该类注释里自己写着"没有真实 PostgreSQL，所以不验证 extra_info 的 CAST"）。
 *
 * <p>按 AGENTS.md §3.3，{@code @TableField(typeHandler=…)} 与 {@code @Transactional}
 * 都属于<b>运行期隐式装配</b>，出错时不报错。静态断言证明不了它们真的生效。
 *
 * <p><b>为什么不能用 H2</b>：本模块依赖三样 H2 不支持或语义不同的能力：
 * <ol>
 *   <li>{@code extra_info} 的 {@link com.rag.users.user.rpc.mapper.JsonTextTypeHandler}
 *       与真 PG 的绑定/反序列化行为（text ↔ {@code Map<String,String>}）</li>
 *   <li>{@code updateUserById} 的 {@code setSql("extra_info = {0}")} 片段拼装与执行</li>
 *   <li>手写 {@code @Select} 上的 {@code @Results} 是否真把该列映射成 {@code Map}</li>
 * </ol>
 * 换掉数据库等于把要验证的东西换没了。
 *
 * <p><b>本类证明什么</b>：
 * <ul>
 *   <li>{@code JsonTextTypeHandler} 在 MP 生成的 insert 上真的能写入 extra_info 列（§3.3 缺口）</li>
 *   <li>{@code @Results} 让手写 {@code @Select} 真能读回 {@code Map} 而不是 null（§3.3 缺口）</li>
 *   <li>extra_info 的<b>字节按原样落库</b>，没被 jsonb 规范化改写（§3.3 缺口，
 *       DRIFT-USERRPC-V-003 修复后新增）</li>
 *   <li>4/5 步事务<b>真的回滚行</b>，而不只是"调用过 rollback"（§3.3 缺口）</li>
 *   <li>UNIQUE 约束在真 PG 上确实拦得住重复 username —— 这是 Go 侧 {@code AlreadyExists} 的地基</li>
 *   <li>B-1 的两个 MyBatis 拦截器<b>真的在插件链里</b>、翻译器<b>挂在 Executor 执行点</b>
 *       ——§3.3 缺口：{@code @Import} 写了 ≠ MyBatis-Plus 收进了插件链（2026-10-26 补）</li>
 * </ul>
 *
 * ⚠️ <b>2026-10-23（DRIFT-USERRPC-V-003）本类整体换了一次前提</b>：
 *   extra_info 列已由 jsonb 改回 <b>text</b>（与 Go 一致）。原注释里
 *   「换成 setString 会被 PG 拒收」「显式 CAST 不能省」这些<b>随列类型一起失效</b>，
 *   已全部重写而非保留 —— 留着就是在给下一个人讲一个已经不成立的故事。
 *   换来的<b>新缺口</b>是反向的：text 列让
 *   {@code CAST(x AS jsonb)} 这类残留<b>能跑通但不报错</b>，
 *   只有断言「落库字节与 Go 序列化结果逐字节相同」才能抓住（见
 *   {@code extraInfoBytesAreStoredVerbatim}）。
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
    }

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private UserAvatarHistoryMapper avatarHistoryMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    /**
     * B-1 装配层断言用：MyBatis-Plus 自动配置产出的 {@code SqlSessionFactory}
     * ——它的 {@code Configuration} 里带的就是容器里收进来的全部 {@link Interceptor}。
     *
     * <p>⚠️ 断言链的内容必须读<b>这个 bean</b>，不能 new 一个
     * {@code Configuration} 自己看 —— 那样验的是「注解写对了」，
     * 而不是「本进程真的把它装上了」（§3.3）。
     */
    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    // ==================================================================
    // §3.3 缺口 1：extra_info 的列类型与建表脚本
    // ==================================================================

    /**
     * 本类其余用例的前提：users 域 DDL 真跑了，且 {@code extra_info} 真的是 {@code text}。
     *
     * <p>Go 出处：真源 {@code model.InitDB}（内含 {@code AutoMigrate}）在 PG 上实测建出的
     * 建表语句原文是 {@code "extra_info" text}，已抓到日志留存（DRIFT-USERRPC-V-003）。
     *
     * <p>⚠️ <b>2026-10-23 本断言方向反转过</b>，原因是它<b>服务的目的没变，但服务的对象变了</b>：
     * 原断言「必须是 jsonb」的理由是「若退化成 text，下面的 jsonb 用例会假绿」。
     * 现在列就是 text，那套 jsonb 用例已被替换 ⇒ <b>新的风险是反方向的</b>：
     * 若有人把脚本改回 jsonb，下面所有 {@code extra_info} 用例<b>照样全绿</b>
     * （jsonb 能接受任何合法 JSON 文本，{@code @Results} 也照样解得出 Map）。
     * ⇒ 拦住它的是 {@link #extraInfoBytesAreStoredVerbatim}，不是这条类型断言。
     * <b>两条缺一不可：这条管「列类型与 Go 一致」，那条管「字节不被规范化」。</b>
     */
    @Test
    @DisplayName("§3.3：建表真跑了，且 extra_info 的列类型确实是 text（与 Go 一致）")
    void schemaInitCreatedTextExtraInfoColumn() {
        // 建表方式已在 2026-10-21 从 Flyway 换成幂等重放（DRIFT-NEW-015）：
        // flyway_schema_history 那张表已经不存在，所以这里只能验「表在不在」。
        // 顺带变成一个更强的断言：user-rpc **单独**对着空库启动，
        // 也能把 admin 域的两张表建出来（Go 侧 AutoMigrate 就是这个行为）。
        Set<String> tables = Set.copyOf(jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_name IN "
                        + "('users_user','users_user_avatar_history',"
                        + "'admin_admin','admin_invite')", String.class));
        assertEquals(Set.of("users_user", "users_user_avatar_history",
                        "admin_admin", "admin_invite"),
                tables,
                "user-rpc 单独对着空库启动时，users 域四张表都应被幂等建出来");

        String extraInfoType = jdbcTemplate.queryForObject(
                "SELECT data_type FROM information_schema.columns "
                        + "WHERE table_name = 'users_user' AND column_name = 'extra_info'",
                String.class);
        assertEquals("text", extraInfoType,
                "extra_info 必须是 text（Go 的 AutoMigrate 建出来就是 text）；"
                        + "若退回 jsonb，PG 会重排/去重/删空格，落库字节将与 Go 不同");
    }

    /**
     * 🔴 <b>DRIFT-USERRPC-V-003 修复后新增的守门</b>：extra_info 必须<b>逐字节</b>落库。
     *
     * <p><b>要证伪的是</b>：写入路径上是否还残留着 {@code CAST(... AS jsonb)}。
     * 残留了<b>不会报错</b> —— text 列接受 assignment cast，测试照样全绿，
     * 但 PG 会顺手把 JSON 规范化一遍（键按「长度→字节序」重排、去重键、删多余空格、
     * 冒号后补空格），于是<b>库里存的字节不再是应用写进去的那个</b>。
     *
     * <p><b>为什么下面这组键能分辨两种列类型</b>：
     * <ul>
     *   <li>Jackson 按 <b>插入序</b> 输出：{@code {"c":"3","a":"1","bb":"2"}}</li>
     *   <li>PG jsonb 按 <b>键长→字节序</b> 输出：{@code {"a": "1", "c": "3", "bb": "2"}}</li>
     * </ul>
     * 两者<b>键序不同</b> ⇒ 用 {@code assertEquals} 比对完整字符串即可分辨，
     * 且失败信息会直接把两种序列化结果都打出来，一眼看出是哪边。
     *
     * <p>⚠️ 用 {@code SELECT extra_info::text} 取值时，text 列返回原样、jsonb 列返回规范化文本
     * —— 这个差异正是本用例的判据，<b>不要</b>为了「两边都能跑」把它改写成不敏感的断言。
     */
    @Test
    @DisplayName("§3.3：extra_info 逐字节落库，没被 jsonb 规范化重排（CAST 残留守门）")
    void extraInfoBytesAreStoredVerbatim() {
        // ⚠️ uid 必须与本类其他用例错开（共享同一个 Testcontainer 库，uid 上有 UNIQUE）
        long uid = 700012L;
        UserEntity user = new UserEntity();
        user.setUid(uid);
        user.setUsername("verbatim");
        user.setPassword("x");
        user.setEmail("verbatim@example.com");
        user.setStatus(0L);
        // LinkedHashMap ⇒ Jackson 按插入序输出：c, a, bb
        // 而 jsonb 会重排成 a, c, bb ⇒ 两者可分辨
        Map<String, String> extra = new LinkedHashMap<>();
        extra.put("c", "3");
        extra.put("a", "1");
        extra.put("bb", "2");
        user.setExtraInfo(extra);

        userMapper.insert(user);

        String raw = jdbcTemplate.queryForObject(
                "SELECT extra_info::text FROM users_user WHERE uid = ?", String.class, uid);
        assertEquals("{\"c\":\"3\",\"a\":\"1\",\"bb\":\"2\"}", raw,
                "extra_info 必须原样落库。实际 = " + raw
                        + "　若键被重排成 a,c,bb ⇒ 列又变回 jsonb 或写入路径残留了 CAST(... AS jsonb)");
    }

    // ==================================================================
    // §3.3 缺口 2：JsonTextTypeHandler 在 MP 生成的 insert 上真的生效
    // ==================================================================

    /**
     * <b>本类是 {@code AvatarTransactionWiringTest} 明确不覆盖的那一半。</b>
     * 那个类写着"没有真实 PostgreSQL，所以不验证 extra_info 的实际写入"。
     *
     * <p><b>要证伪的是</b>：{@code @TableField(typeHandler = JsonTextTypeHandler.class)}
     * 真的被 MyBatis-Plus 的 insert SQL 采用。若失效（字段级 TypeHandler 没被装配），
     * MyBatis 会按 {@code javaType=Object} 猜 handler，序列化结果不受控 ——
     * <b>这是运行期隐式装配，不报错、测试也可能照样绿</b>（§3.3）。
     *
     * <p>⚠️ <b>2026-10-23 前提变了</b>：列已是 text，本条的判据从
     * 「setString 会被 PG 拒收」改成「落库字节是否与 Jackson 序列化结果一致」
     * —— 后者同样只在真 PG 上可验，且比「没报错」更严。
     * 具体逐字节断言见 {@link #extraInfoBytesAreStoredVerbatim}；本条负责覆盖面
     * （MP 生成的 insert 这一条路径）。
     *
     * <p>Go 对应：{@code CreateUser} 走 GORM，{@code serializer:json} 把 map 写成 JSON 文本入库。
     */
    @Test
    @DisplayName("§3.3：JsonTextTypeHandler 在 MP insert 上真能写入 extra_info 列")
    void jsonTextTypeHandlerWorksOnGeneratedInsert() {
        long uid = 700001L;
        UserEntity user = new UserEntity();
        user.setUid(uid);
        user.setUsername("jsontext-insert");
        user.setPassword("x");
        user.setEmail("jsontext-insert@example.com");
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
    // §3.3 缺口 3：手写 @Select 上的 @Results 真的把 extra_info 读成 Map
    // ==================================================================

    /**
     * {@code UserMapper.selectByUidRaw / selectByUsernameRaw} 是手写 {@code @Select}，
     * <b>不走</b> MP 生成的 ResultMap，因此必须靠注解上的 {@code @Results} 才拿得到该列。
     * 少了它，字段静默变 null —— 又是一种"不报错"的失效。
     */
    @Test
    @DisplayName("§3.3：手写 @Select 上的 @Results 真把 extra_info 读成 Map（不是 null）")
    void resultsAnnotationMapsExtraInfoToMap() {
        long uid = 700002L;
        UserEntity user = new UserEntity();
        user.setUid(uid);
        user.setUsername("jsontext-select");
        user.setPassword("x");
        user.setEmail("jsontext-select@example.com");
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

        UserEntity byName = userMapper.findOneByUserName("jsontext-select");
        assertNotNull(byName, "按 username 应能查到");
        assertEquals("hello", byName.getExtraInfo().get("bio"));
    }

    // ==================================================================
    // §3.3 缺口 4：updateUserById 的 SQL 片段在真 PG 上成立且不残留 CAST
    // ==================================================================

    /**
     * {@code updateUserById} 用的是 {@code wrapper.setSql("extra_info = {0}", json)}
     * 而不是 {@code wrapper.set(...)} —— 因为后者会把参数塞进
     * {@code ew.paramNameValuePairs}、丢掉字段类型信息，序列化结果不受控。
     *
     * <p>⚠️ <b>2026-10-23（DRIFT-USERRPC-V-003）本条的理由整个换掉了</b>：
     * 原实现是 {@code setSql("extra_info = CAST({0} AS jsonb)", json)}，理由是
     * 「PG 拒收 varchar → jsonb」。列改回 text 后那个理由消失，
     * <b>但 CAST 不能留着</b> —— 它在 text 列上<b>能跑通</b>，只是会顺手规范化一次，
     * 把刚落库的字节改写掉。⇒ 这类「能跑但语义错」的残留，靠「不报错」永远发现不了，
     * 必须由 {@link #extraInfoBytesAreStoredVerbatim} 的逐字节断言来拦。
     *
     * <p>本条负责覆盖面（{@code setSql} 片段这条路），那条负责语义（字节没被动过）。
     */
    @Test
    @DisplayName("§3.3：updateUserById 的 extra_info 片段在真 PG 上成立")
    void updateUserByIdWritesExtraInfoOnRealPg() {
        long uid = 700003L;
        seedUser(uid, "jsontext-update", "jsontext-update@example.com");

        UserEntity patch = new UserEntity();
        Map<String, String> extra = new HashMap<>();
        extra.put("theme", "dark");
        patch.setExtraInfo(extra);

        int affected = userMapper.updateUserById(uid, patch);
        assertEquals(1, affected, "应更新 1 行");

        String raw = jdbcTemplate.queryForObject(
                "SELECT extra_info::text FROM users_user WHERE uid = ?", String.class, uid);
        assertTrue(raw != null && raw.contains("\"theme\""),
                "extra_info 应是 JSON 对象文本，实际 = " + raw);

        // @Results 读回路径也要跟着对
        assertEquals("dark", userMapper.findOneByUid(uid).getExtraInfo().get("theme"));
    }

    /**
     * 🔴 2026-10-23（DRIFT-USERRPC-V-010 修复）：<b>旧断言是错的，本用例被反转</b>。
     *
     * <p>旧用例名 {@code zeroValuePatchDoesNotUpdate} / 旧 DisplayName「全零值 patch 不发 UPDATE
     * （对齐 GORM 的 no-op 语义）」断言「无字段变更 ⇒ 返回 0、不发 UPDATE、update_time 不推进」。
     * <p>⚠️ 那条「GORM 的 no-op 语义」<b>没有 Go 出处</b>，且被 2026-10-23 的
     * <b>双侧活体对拍</b>证伪（真进程 + 真 PG，2026-10-23 21:25）：
     * <pre>
     *   请求体 {}                 Go: update_time 推进   RAG(修复前): 不推进   ← 漂移
     *   请求体 {"extra_info":{}} Go: update_time 推进   RAG(修复前): 不推进   ← 漂移
     * </pre>
     * <p><b>Go 出处（消费点）</b>：{@code gorm.io/gorm@v1.31.1/callbacks/update.go:235-252}
     * <pre>
     * if field.AutoUpdateTime &gt; 0 &amp;&amp; value[field.Name] == nil &amp;&amp; value[field.DBName] == nil {
     *     now := stmt.DB.NowFunc()
     *     assignValue(field, now)
     *     set = append(set, clause.Assignment{Column: …, Value: now})   // ← 无条件进 SET
     * }
     * </pre>
     * ⇒ {@code autoUpdateTime} <b>无条件</b>进 SET ⇒ {@code ConvertToAssignments} 的 set 非空
     * ⇒ {@code callbacks/update.go:74-79} 的 {@code else { return }}（"空 set 就不发 UPDATE"）<b>不触发</b>
     * ⇒ <b>Go 总会发出一条只推进 update_time 的 UPDATE</b>。
     * <p>⇒ 本用例现在锁住的是「<b>必须</b>发 UPDATE、<b>必须</b>推进 update_time、
     * 但<b>不得</b>改任何业务列」这三条。
     */
    @Test
    @DisplayName("DRIFT-USERRPC-V-010：全零值 patch 仍发 UPDATE（只推进 update_time，不碰业务列）")
    void zeroValuePatchStillAdvancesUpdateTimeOnly() throws Exception {
        long uid = 700004L;
        seedUser(uid, "jsontext-nopatch", "jsontext-nopatch@example.com");
        Object before = jdbcTemplate.queryForObject(
                "SELECT update_time FROM users_user WHERE uid = ?", Object.class, uid);
        String emailBefore = jdbcTemplate.queryForObject(
                "SELECT email FROM users_user WHERE uid = ?", String.class, uid);

        // PG 的 CURRENT_TIMESTAMP 是**事务**时间戳；两次读之间必须跨事务才能看出推进。
        Thread.sleep(30L);

        int affected = userMapper.updateUserById(uid, new UserEntity());
        assertEquals(1, affected,
                "全零值 patch 仍必须发出 UPDATE —— Go 的 autoUpdateTime 无条件进 SET"
                        + "（gorm callbacks/update.go:238-252）");

        Object after = jdbcTemplate.queryForObject(
                "SELECT update_time FROM users_user WHERE uid = ?", Object.class, uid);
        assertNotEquals(before, after,
                "update_time 必须推进 —— 这是 Go 侧 autoUpdateTime 的无条件行为");

        String emailAfter = jdbcTemplate.queryForObject(
                "SELECT email FROM users_user WHERE uid = ?", String.class, uid);
        assertEquals(emailBefore, emailAfter, "业务列不得被这次 UPDATE 改动");
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
    // B-1：SQL 超时翻译链的**装配层**断言（§3.3：注解存在 ≠ 机制生效）
    // ==================================================================

    /**
     * <b>本方法证明 user-rpc 的 MyBatis 插件链里真的装着 B-1 那两个拦截器。</b>
     *
     * <p><b>为什么不能靠「{@code @Import} 写了」当证据</b>：
     * {@code UserRpcApplication} 上确实写着
     * {@code @Import({DeadlineQueryTimeoutInjector.class, SqlTimeoutStatusTranslator.class})}，
     * 但那是<b>声明</b>；MyBatis-Plus 到底有没有把容器里的 {@code Interceptor} Bean
     * 收进 {@code Configuration}，是<b>运行期</b>的事（§3.3）。
     *
     * <p><b>消费点在哪（§3.3.1：行号指向消费点而非声明点）</b>：
     * MyBatis-Plus 3.5.17 的 {@code MybatisPlusAutoConfiguration}
     * 构造器收 {@code ObjectProvider<Interceptor[]>}，
     * 再在 {@code sqlSessionFactory(DataSource)} 里交给 {@code SqlSessionFactoryBean}；
     * 链的最终形态就是 {@code sqlSessionFactory.getConfiguration().getInterceptors()}。
     * <b>不读这一段就等于没验</b> —— 本仓已经吃过两次同款亏
     * （P-4「{@code @Import} 只让 Bean 存在」· P-ADMINAPI-009「最小上下文 ≠ 生产装配」）。
     *
     * <p><b>这条链断了，本域的具体后果</b>（与 admin 域同型）：
     * <ol>
     *   <li>缺 {@link DeadlineQueryTimeoutInjector} ⇒ 每条 SQL 不带 {@code statement_timeout}
     *       ⇒ 客户端 deadline 到点时<b>在途查询继续跑满</b>
     *       ⇒ Go 侧 ctx 会同步取消（{@code service/common/observability/rpc.go:38,55}
     *           把带 2000ms deadline 的 {@code timeoutCtx} 传进 handler）⇒ 两侧在
     *           「客户端已放弃后 PG 还占着多久」上出现差值</li>
     *   <li>缺 {@link SqlTimeoutStatusTranslator} ⇒ PG 的 57014 {@code query_canceled}
     *       不会变成 {@code DEADLINE_EXCEEDED}
     *       ⇒ 落进业务层的 {@code catch (Exception e)}
     *       ⇒ <b>被改写成 INTERNAL</b> ⇒ api 侧 {@code mapRPCStatusToCode}
     *           （{@code user-api/…/advice/GlobalExceptionHandler.java:154-155}）渲染 5001，
     *           而 Go 是 1015（{@code service/user/common/errmsg/errmsg.go:6}）
     *           ⇒ <b>wire 面就漂了</b></li>
     * </ol>
     *
     * <p>⚠️ 本方法只证明<b>装配</b>（链里有这两个）。
     * 「链断了会怎样」的两个后果本身各有各自的守门：
     * 放行分支由 {@code DeadlinePassthroughTest} 守（纯控制流，mock mapper 直接抛
     * {@code StatusRuntimeException}），
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
                        + "DEADLINE_EXCEEDED ⇒ 会落进 catch(Exception) 被改写成 INTERNAL"
                        + "（5001 ≠ Go 的 1015）；实际链 = " + names);
        assertTrue(chain.stream().anyMatch(DeadlineQueryTimeoutInjector.class::isInstance),
                "MyBatis 插件链里没有 DeadlineQueryTimeoutInjector ⇒ 每条 SQL 不带 statement_timeout "
                        + "⇒ 客户端 deadline 到点后在途查询继续跑满；实际链 = " + names);
    }

    /**
     * 翻译器必须挂在<b>执行点</b>（{@code Executor.query} / {@code Executor.update}）——
     * CancelRequest 引发的 {@code SQLException} 是在<b>执行</b>阶段抛出的，
     * {@code StatementHandler.prepare} 阶段根本看不到它
     * ⇒ 挂错点 = <b>永远翻译不到</b>（而且是静默的：链里有它、测试也全绿）。
     *
     * <p>本方法断言的是<b>注解声明的挂载目标</b>（{@code @Intercepts} 里的
     * {@code @Signature}），与上面那条「链里有没有它」是<b>两个独立的失效面</b>：
     * 前者管「在不在」，后者管「挂在哪儿」。两者都要绿，翻译链才成立。
     */
    @Test
    @DisplayName("B-1：SqlTimeoutStatusTranslator 挂在 Executor 执行点（不是 StatementHandler.prepare）")
    void translatorInterceptsTheExecutionPoint() {
        assertTranslatorMountedOnExecutor(SqlTimeoutStatusTranslator.class);
    }

    /**
     * <b>负控 NC-A（恒绿检查）：同一条断言必须能识破「挂错点」的翻译器。</b>
     *
     * <p><b>为什么需要它</b>：上面那条断言读的是注解元数据。若断言本身写错了
     * （比如只查「有没有 {@code @Intercepts}」而不查挂载点），
     * 它会<b>恒绿</b>，等于没有守门。本方法用一枚<b>故意挂错点的变异体</b>
     * 跑<b>同一条断言路径</b>，要求它<b>必须拒绝</b> ——
     * 这才证明该断言有分辨力，而不是「恰好为真」。
     *
     * <p>⚠️ 变异体是本测试类里的一个空壳类型，<b>不参与任何生产装配</b>
     * （它没有被 {@code @Import}，也不在 {@code com.rag.users.user.rpc} 的组件扫描路径上
     * ——它是本测试类的<b>静态嵌套类</b>，Spring 不会把它当 Bean）。
     * 它唯一的作用是给断言喂一个「形状错误」的输入。
     *
     * <p>⚠️ <b>本条只证明「判据不是恒绿」，不证明「判据真在看挂载点」</b> ——
     * 因为本变异体<b>同时违反三条规则</b>（无 Executor · 没覆盖 query+update · 挂了 StatementHandler）
     * ⇒ 判据只要有<b>任意一条</b>别的规则在拦，它就会绿
     * ⇒ ⇒ 隔离性由 {@link #translatorMountPointGuardRejectsExtraStatementHandlerSignature}（NC-B）负责。
     */
    @Test
    @DisplayName("B-1 负控 NC-A：同一断言必须识破「挂在 StatementHandler.prepare」的变异体")
    void translatorMountPointGuardRejectsWrongMountPoint() {
        assertThrows(AssertionError.class,
                () -> assertTranslatorMountedOnExecutor(MutantTranslatorMountedOnStatementHandler.class),
                "断言对「挂错点」的翻译器必须报错；它没报错 ⇒ 说明这条断言没有分辨力，"
                        + "上面那条用例是恒绿的假守门");
    }

    /**
     * <b>负控 NC-B（隔离性检查）：「一切正确 + 多挂一个 {@code StatementHandler.prepare}」也必须被拒绝。</b>
     *
     * <p><b>为什么 NC-A 不够</b> —— 这条是 <code>users/admin</code> 域回信指出的元教训
     * （他们踩过：NC-A 的变异体同时违反三条规则 ⇒ 判据只要有任意一条别的规则在拦，它就会绿
     * ⇒ <b>NC-A 只证明「判据不是恒绿」，证明不了「判据真的在看挂载点」</b>）。
     *
     * <p><b>NC-B 的变异体只违反一条规则</b>：{@code Executor.query}×2 + {@code Executor.update}
     * <b>全都正确</b>，<b>只多挂一条</b> {@code StatementHandler.prepare}
     * ⇒ 拒绝它的<b>只可能是</b>「不得挂 StatementHandler」那一条 ⇒ ⇒ 隔离性成立。
     *
     * <p>⚠️ <b>元教训：负控的「必须红在目标断言」要按「变异体违反了几条规则」定强度</b> ——
     * 违反多条 ⇒ <b>不能查理由</b>（会把一次合法的部分弱化误报成失败，
     * <code>users/admin</code> 域第一版就因此误报过）⇒ NC-A <b>只要求「被拒绝」（不查理由）</b>；
     * 只违反一条 ⇒ <b>可以查</b>（且这才有隔离力）⇒ <b>NC-B 才查理由</b>。
     */
    @Test
    @DisplayName("B-1 负控 NC-B：只违反「不得挂 StatementHandler」一条规则的变异体也必须被拒绝")
    void translatorMountPointGuardRejectsExtraStatementHandlerSignature() {
        AssertionError thrown = assertThrows(AssertionError.class,
                () -> assertTranslatorMountedOnExecutor(
                        MutantTranslatorWithExtraStatementHandlerSignature.class),
                "「一切正确 + 多挂一条 StatementHandler.prepare」的变异体必须被拒绝；"
                        + "它没报错 ⇒ 说明「不得挂 StatementHandler」那条规则根本没在看挂载点");
        assertTrue(thrown.getMessage() != null && thrown.getMessage().contains("StatementHandler"),
                "NC-B 变异体只违反一条规则 ⇒ 拒绝它的应当就是那条；实际拒绝理由 = " + thrown.getMessage());
    }

    /**
     * 「翻译器挂在 {@code Executor} 执行点」这条断言的<b>唯一实现</b>，
     * 正例与负控共用它 —— 共用才能保证负控验的就是正例在用的那条判据。
     *
     * @param translatorClass 待检查的翻译器类型
     */
    private static void assertTranslatorMountedOnExecutor(Class<?> translatorClass) {
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
                "翻译器没有拦截 Executor ⇒ 执行阶段抛出的 57014 看不到；实际拦截类型 = " + types);
        assertTrue(methods.contains("query") && methods.contains("update"),
                "翻译器没有覆盖 query/update 两个执行方法；实际 = " + methods);
        assertFalse(types.contains("StatementHandler"),
                "翻译器若挂在 StatementHandler.prepare 上，CancelRequest 引发的 SQLException 永远翻译不到");
    }

    /**
     * <b>变异体（只给负控用）</b>：把挂载点故意挂成
     * {@code StatementHandler.prepare} —— 正是本仓历史上踩过的那个错位形状
     * （{@code DeadlineQueryTimeoutInjector} 就挂在这里：它要在 prepare 之后
     * 给 Statement 设超时，所以挂 prepare 是<b>对的</b>；
     * 而翻译器要在<b>执行</b>阶段抓 SQLException，挂 prepare 就<b>永远抓不到</b>）。
     */
    @Intercepts(@Signature(
            type = StatementHandler.class,
            method = "prepare",
            args = {Connection.class, Integer.class}))
    static class MutantTranslatorMountedOnStatementHandler {
        // 刻意为空：只需要它携带一枚「形状错误」的 @Intercepts 注解。
        // ⚠️ 本变异体**同时违反三条规则**（无 Executor · 没覆盖 query+update · 挂了 StatementHandler）
        // ⇒ 只配 NC-A 用；隔离性由 NC-B 负责。
    }

    /**
     * <b>变异体 NC-B（只给隔离性负控用）</b>：
     * {@code Executor.query}×2 + {@code Executor.update} <b>全部正确</b>
     *（与生产 {@code SqlTimeoutStatusTranslator} 的三个 signature 逐条同形），
     * <b>只多挂一条</b> {@code StatementHandler.prepare}
     * ⇒ <b>只违反「不得挂 StatementHandler」这一条规则</b>
     * ⇒ 拒绝它的只可能是那一条 ⇒ 隔离性成立。
     *
     * <p>⚠️ 与 NC-A 的变异体**形状相反**：NC-A 是「只剩错的那条」，NC-B 是「全对 + 多一条错」。
     * 两者缺一不可 —— 只有 NC-A 时，判据可能被别的规则「代劳」而误绿。
     */
    @Intercepts({
            @Signature(type = Executor.class, method = "query",
                    args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
            @Signature(type = Executor.class, method = "query",
                    args = {MappedStatement.class, Object.class, RowBounds.class,
                            ResultHandler.class, CacheKey.class, BoundSql.class}),
            @Signature(type = Executor.class, method = "update",
                    args = {MappedStatement.class, Object.class}),
            @Signature(type = StatementHandler.class, method = "prepare",
                    args = {Connection.class, Integer.class})
    })
    static class MutantTranslatorWithExtraStatementHandlerSignature {
        // 刻意为空：只需要它携带一枚「只违反一条规则」的 @Intercepts 注解。
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
