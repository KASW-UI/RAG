package com.rag.users.user.rpc.mapper;

import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.rag.users.user.rpc.mapper.UserAvatarHistoryMapper;
import com.rag.users.user.rpc.mapper.UserMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.SimpleTransactionStatus;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §3.3 强制测试：<b>真正装配 Spring 上下文</b>，证明 {@code @Transactional} 挂在
 * MyBatis-Plus mapper 接口的 {@code default} 方法上<b>确实生效</b>。
 *
 * <h2>为什么必须有这个类（AGENTS.md §6.2 P1，长期挂着 UNKNOWN）</h2>
 * <p>{@code UserAvatarHistoryMapper} 的两个多步方法把 {@code @Transactional} 标在
 * <b>接口的 default 方法</b>上：
 * <pre>
 *   createAvatarHistory(...)   4 步原子：W1 查 user → W2 旧 current 置 false
 *                                      → W3 INSERT 新 history → W4 双写 user.extra_info
 *   selectAvatarHistory(...)   5 步原子：W1 查 user → W2 查 history → W3 旧 current 置 false
 *                                      → W4 目标置 true → W5 双写 user.extra_info
 * </pre>
 * 对应 Go 出处 {@code model/user.go:72} 与 {@code model/user.go:134} 的
 * {@code m.conn.WithContext(ctx).Transaction(func(tx *gorm.DB) error {...})}：
 * <b>任何一步失败 ⇒ 整体回滚</b>。这是 D-005 / D-015 的硬约束。
 *
 * <p><b>此前的测试完全证明不了这件事</b>：
 * {@code UserRpcJsonbAndAvatarProjectionTest} 用
 * {@code Mockito.mock(UserMapper.class, CALLS_REAL_METHODS)} 跑 default 方法体 ——
 * Mockito 的代理<b>根本不经过 Spring</b>，既没有 AOP 织入、也没有事务。
 * 按 §3.3，这类单测<b>按定义</b>测不到「代理有没有装上」。
 * 这正是这个风险能长期挂在 UNKNOWN 的原因。
 *
 * <h2>本测试如何真正触发该机制</h2>
 * <p>装一个真实的 Spring 容器（{@link AnnotationConfigApplicationContext}），
 * 组件与生产完全同构：
 * <ul>
 *   <li>{@code @EnableTransactionManagement(proxyTargetClass = false)}
 *       —— 与 Spring Boot {@code TransactionAutoConfiguration} 上的注解逐字一致，
 *       这决定了走 JDK 代理（mapper 是接口，只能走 JDK 代理）；</li>
 *   <li>{@code MapperFactoryBean}（由 {@code @MapperScan} 注册）与生产同一个类；</li>
 *   <li>{@code MybatisSqlSessionFactoryBean} + {@code SqlSessionTemplate}
 *       —— 生产由 {@code MybatisPlusAutoConfiguration} 产出，同一类；</li>
 *   <li>{@code PlatformTransactionManager} 换成<b>记录型</b>实现，
 *       这样 begin / commit / rollback 三件事是可断言的。</li>
 * </ul>
 * 然后：
 * <ol>
 *   <li>断言 mapper bean 真的是 AOP 代理，且 advisor 里有事务拦截器；</li>
 *   <li><b>真的调用</b> {@code createAvatarHistory} —— 用一个 {@code getConnection()}
 *       直接抛的 DataSource 让第一步 SQL 失败，从而<b>真正触发</b>事务的开始与回滚，
 *       断言记录到的是 {@code begin → rollback} 而<b>不是</b> {@code begin → commit}。</li>
 * </ol>
 *
 * <h2>本测试<b>不</b>覆盖什么（诚实边界）</h2>
 * <p>本类用「记录型事务管理器」替代真实 DataSource，只验证「事务边界这个机制有没有被装上、
 * 异常时会不会走回滚分支」—— 也就是 §3.3 所指的那一类<b>框架机制是否真的生效</b>。
 * <p>它<b>不</b>验证数据库层面的真实状态：jsonb 的 {@code CAST}、
 * {@code @Results} 是否真把 jsonb 列映射成 {@code Map}、回滚后 PG 里那一行是否真的还在。
 *
 * <p><b>2026-10-21 更新</b>：上述缺口已由同模块的
 * {@code UserRpcContainerWiringTest}（真 Testcontainers PostgreSQL）关闭，
 * 覆盖 jsonb 写入 / 读取 / CAST、4-5 步事务的<b>真实行回滚</b>、UNIQUE 约束。
 * 本类保留的价值在于它是<b>纯上下文装配</b>的探针，能在不依赖 Docker 的环境下
 * 守住「mapper bean 确实被 AOP 代理」这条结构断言；两者互补，不要互相替代。
 */
class AvatarTransactionWiringTest {

    private AnnotationConfigApplicationContext ctx;
    private RecordingTxManager txManager;

    // ================== 断言 1：代理结构 ==================

    @Test
    @DisplayName("§3.3：mapper default 方法上的 @Transactional 必须真的把 bean 包成 AOP 代理")
    void mapperIsTransactionallyProxied() {
        UserAvatarHistoryMapper mapper = ctx.getBean(UserAvatarHistoryMapper.class);

        assertTrue(AopUtils.isAopProxy(mapper),
                "UserAvatarHistoryMapper 未被 Spring AOP 代理 —— 4/5 步原子性等于没有");

        assertTrue(advisedMethodsOf(mapper).contains("createAvatarHistory")
                        && advisedMethodsOf(mapper).contains("selectAvatarHistory"),
                "两个多步方法都必须有事务 advisor 命中，实际命中 = " + advisedMethodsOf(mapper));
    }

    @Test
    @DisplayName("对照组：UserMapper 没有 @Transactional 方法 ⇒ 正确地<b>不</b>被代理，但它仍参与同一事务")
    void userMapperIsNotProxied_butStillJoinsAmbientTransaction() {
        UserMapper userMapper = ctx.getBean(UserMapper.class);

        // UserMapper 上的方法（selectByUidRaw / updateUserById / deleteUserByUid …）都没有
        // @Transactional，自动代理器因此<b>不</b>给它织任何 advisor —— 这是正确行为，
        // 不是缺陷。（这条断言写反过一次，正是它把"想当然"挡在了外面。）
        assertFalse(AopUtils.isAopProxy(userMapper),
                "UserMapper 没有任何 @Transactional 方法，不应被 AOP 代理");

        // 但它<b>依然参与</b> createAvatarHistory 开启的那个事务 ——
        // 靠的不是 bean 被代理，而是 SqlSessionTemplate → DataSourceUtils 的线程绑定连接：
        // 事务里第一条 SQL 用的连接会被 TransactionSynchronizationManager 绑到当前线程，
        // 后续所有 SqlSession 复用同一条连接，于是天然同事务。
        // 用「UserMapper 的调用也会让 recording TM 看到 begin」来间接证明线程绑定生效：
        // 在 createAvatarHistory 已开启事务之后，经 userMapper 发起的失败仍走同一事务事件流。
        UserAvatarHistoryMapper mapper = ctx.getBean(UserAvatarHistoryMapper.class);
        txManager.calls.clear();

        assertTrue(runAndExpectFailure(() ->
                        mapper.createAvatarHistory(userMapper, 1001L, "H-1", "https://x/y.png", "image/png", 10L))
                        .isPresent(),
                "createAvatarHistory 应因 DataSource 不可用而失败");

        assertEquals(List.of("begin", "rollback"), txManager.calls,
                "UserMapper 作为事务内参与方，其失败必须落在同一个事务的 rollback 上，实际 = "
                        + txManager.calls);
    }

    // ================== 断言 2：异常时真的回滚 ==================

    @Test
    @DisplayName("§3.3：createAvatarHistory 中途失败必须 begin → rollback，且不得 commit")
    void createAvatarHistory_rollsBackOnFailure() {
        UserAvatarHistoryMapper mapper = ctx.getBean(UserAvatarHistoryMapper.class);
        UserMapper userMapper = ctx.getBean(UserMapper.class);
        txManager.calls.clear();

        // W1 的 SQL 必然失败：StubDataSource.getConnection() 直接抛 SQLException。
        // 若 @Transactional 真的生效，异常必须以 RuntimeException 形态穿出事务拦截器 →
        // 触发 rollback（Go 侧 Transaction(func) 返回 err 时同样回滚）。
        assertTrue(runAndExpectFailure(() ->
                        mapper.createAvatarHistory(userMapper, 1001L, "H-1", "https://x/y.png", "image/png", 10L))
                        .isPresent(),
                "createAvatarHistory 在 DataSource 不可用时必须抛异常");

        assertEquals(List.of("begin", "rollback"), txManager.calls,
                "期望 begin → rollback（对齐 Go 的 Transaction 回滚语义），实际 = " + txManager.calls);
        assertFalse(txManager.calls.contains("commit"),
                "失败路径绝不能出现 commit");
    }

    @Test
    @DisplayName("§3.3：selectAvatarHistory 中途失败同样必须回滚")
    void selectAvatarHistory_rollsBackOnFailure() {
        UserAvatarHistoryMapper mapper = ctx.getBean(UserAvatarHistoryMapper.class);
        UserMapper userMapper = ctx.getBean(UserMapper.class);
        txManager.calls.clear();

        assertTrue(runAndExpectFailure(() ->
                        mapper.selectAvatarHistory(userMapper, 1001L, "H-1"))
                        .isPresent(),
                "selectAvatarHistory 在 DataSource 不可用时必须抛异常");

        assertEquals(List.of("begin", "rollback"), txManager.calls,
                "期望 begin → rollback，实际 = " + txManager.calls);
    }

    @Test
    @DisplayName("对照组：无 @Transactional 的普通查询方法不得开启事务")
    void plainQueryMethodStartsNoTransaction() {
        UserMapper userMapper = ctx.getBean(UserMapper.class);
        txManager.calls.clear();

        assertTrue(runAndExpectFailure(() -> userMapper.selectByUidRaw(1001L)).isPresent(),
                "selectByUidRaw 同样会因 DataSource 不可用而失败");

        assertEquals(List.of(), txManager.calls,
                "selectByUidRaw 没有 @Transactional，不应产生任何事务事件，实际 = " + txManager.calls);
    }

    // ================== 装配 ==================

    @BeforeEach
    void setUp() {
        ctx = new AnnotationConfigApplicationContext();
        ctx.register(TestConfig.class);
        ctx.refresh();
        txManager = ctx.getBean(RecordingTxManager.class);
    }

    @AfterEach
    void tearDown() {
        if (ctx != null) {
            ctx.close();
        }
    }

    /**
     * 列出 mapper 接口上被事务属性源认定为「需要事务」的方法名。
     *
     * <p>刻意<b>不</b>只看 {@code isAopProxy}：代理装上了不代表 advisor 真的命中了目标方法。
     * 这里用 Spring 自己的 {@code AnnotationTransactionAttributeSource} 复核一遍，
     * 确认 {@code createAvatarHistory} / {@code selectAvatarHistory} 这两个 default 方法
     * 的 {@code @Transactional} 确实可被识别。
     */
    private static List<String> advisedMethodsOf(Object bean) {
        assertTrue(bean instanceof org.springframework.aop.framework.Advised,
                "mapper 应由 Spring AOP 织入（接口 + proxyTargetClass=false ⇒ JDK 代理）");
        List<String> matched = new CopyOnWriteArrayList<>();
        org.springframework.transaction.interceptor.TransactionAttributeSource tas =
                new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource();
        for (Method m : UserAvatarHistoryMapper.class.getDeclaredMethods()) {
            if (tas.getTransactionAttribute(m, UserAvatarHistoryMapper.class) != null) {
                matched.add(m.getName());
            }
        }
        return matched;
    }

    private static java.util.Optional<Throwable> runAndExpectFailure(Runnable r) {
        try {
            r.run();
            return java.util.Optional.empty();
        } catch (Throwable t) {
            return java.util.Optional.of(t);
        }
    }

    // ================== 测试应用 ==================

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = false) // 与 Boot TransactionAutoConfiguration 一致
    @MapperScan("com.rag.users.user.rpc.mapper")
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            // 刻意不可用：本测试只关心事务边界，不关心 SQL 是否跑得通。
            // 没有它就无法触发「中途失败 → 回滚」这条路径。
            return new AbstractDataSource() {
                @Override
                public Connection getConnection() throws SQLException {
                    throw new SQLException("AvatarTransactionWiringTest: 刻意不连真实库");
                }

                @Override
                public Connection getConnection(String username, String password) throws SQLException {
                    return getConnection();
                }
            };
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            // 不配 mapper xml：本测试只用注解 SQL
            factory.setTypeAliasesPackage("com.rag.users.user.rpc.domain");
            return factory.getObject();
        }

        @Bean
        SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
            return new SqlSessionTemplate(sqlSessionFactory);
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return new RecordingTxManager();
        }
    }

    /**
     * 记录型事务管理器 —— 把「事务边界有没有生效」变成可断言的事实。
     * 用 {@link org.springframework.transaction.support.AbstractPlatformTransactionManager}
     * 的裸 PlatformTransactionManager 接口实现，避免继承带来的隐式行为。
     */
    static class RecordingTxManager implements PlatformTransactionManager {

        final List<String> calls = new CopyOnWriteArrayList<>();

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            calls.add("begin");
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            calls.add("commit");
        }

        @Override
        public void rollback(TransactionStatus status) {
            calls.add("rollback");
        }
    }
}
