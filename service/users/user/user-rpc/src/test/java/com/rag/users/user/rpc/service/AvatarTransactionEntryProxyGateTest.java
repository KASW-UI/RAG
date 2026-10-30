package com.rag.users.user.rpc.service;

import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.rag.common.spring.LoggerFactoryBean;
import com.rag.users.user.rpc.config.UserRpcProperties;
import com.rag.users.user.rpc.mapper.UserAvatarHistoryMapper;
import com.rag.users.user.rpc.mapper.UserMapper;
import com.rag.users.user.rpc.metrics.UserRpcMetrics;
import com.rag.users.user.proto.GetAvatarHistoryReq;
import com.rag.users.user.proto.SelectAvatarReq;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttributeSource;
import org.springframework.transaction.support.SimpleTransactionStatus;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * {@code S1-USERS-004} 的<b>防退化守门</b>（G1 lane · 2026-10-30）。
 *
 * <h2>它守的是什么</h2>
 * <p>{@code S1-USERS-004}（`docs/DRIFT/users/stage1-admission.md` §3 / §4 任务表）登记的形状是：
 * <b>头像链路的事务正确性依赖「调用方永远从别的 Bean 调进来」</b>。
 * <ul>
 *   <li>事务声明在 {@link UserAvatarHistoryMapper} 的两个 <b>{@code default} 方法</b>上
 *       （{@code createAvatarHistory} = 4 步原子 · {@code selectAvatarHistory} = 5 步原子）；
 *   <li>编排入口在 {@link UserDomainService}（<b>另一个 Bean</b>）⇒ 调用<b>经 Spring 代理</b>
 *       ⇒ 事务生效。</li>
 * </ul>
 * <p>同文件 §4 任务表明写「<b>把事务边界上移 ⇒ 阶段 1 不动</b>」（已验证的边界不许动），
 * 而 §518 写「<b>当前实现正确，缺防退化</b>」⇒ ⇒
 * <b>本类只加护栏，一行生产代码都不改</b>。
 *
 * <h2>为什么「机制生效」那条已有测试还不够</h2>
 * <p>同包的 {@code AvatarTransactionWiringTest} 已经证明
 * 「<b>mapper bean 确实被 AOP 代理</b>」+「异常时确实 begin → rollback」⇒
 * 那条守的是 <b>mapper 侧</b>。
 * <p>本类守的是另一头：<b>编排层</b>。
 * <p>退化形态有三种，本类逐条堵：
 * <ol>
 *   <li><b>事务声明被搬到编排层</b> ⇒ 编排层方法 <code>this.xxx()</code> 自调用
 *       ⇒ <b>绕过代理</b> ⇒ 事务静默失效（<b>不会报错</b>）；</li>
 *   <li><b>编排层绕过注入自己 new 一个 mapper</b>（或用 static 字段持有）
 *       ⇒ 拿到的不是代理 ⇒ 同上；</li>
 *   <li><b>mapper 上的 {@code @Transactional} 被删</b> ⇒ 4/5 步不再原子。</li>
 * </ol>
 *
 * <h2>本测试如何真正触发该机制（AGENTS.md §3.3）</h2>
 * <p>装一个<b>真实</b>的 Spring 容器，组件与生产同构：
 * <ul>
 *   <li>{@code @EnableTransactionManagement(proxyTargetClass = false)}
 *       —— 与 Spring Boot {@code TransactionAutoConfiguration} 上的注解逐字一致
 *       （mapper 是接口 ⇒ 只能走 JDK 代理）；</li>
 *   <li>{@code @MapperScan} ⇒ {@code MapperFactoryBean} 产出<b>真代理</b>
 *       （生产由 {@code MybatisPlusAutoConfiguration} 产出，同一类）；</li>
 *   <li>{@code MybatisSqlSessionFactoryBean} + {@code SqlSessionTemplate}（生产同源类）；</li>
 *   <li>{@link PlatformTransactionManager} 换成<b>记录型</b>实现 ⇒ begin / commit / rollback 可断言；</li>
 *   <li><b>真的装上 {@link UserDomainService} 与 {@link AvatarDomainService}</b>
 *       —— 只把<b>与事务无关</b>的依赖（redis / logger / 配置）换成替身。</li>
 * </ul>
 * 然后<b>真的调用真实入口</b> {@code UserDomainService.selectAvatar(...)}
 * ⇒ DataSource 刻意不可用 ⇒ mapper 的 W1 SQL 失败
 * ⇒ 断言记录到的是 {@code begin → rollback} 而<b>不是</b> {@code begin → commit}
 * ⇒ ⇒ <b>证明「真实编排入口确实经代理、事务确实开启并回滚」</b>。
 *
 * <h2>本测试<b>不</b>覆盖什么（诚实边界）</h2>
 * <p>与 {@code AvatarTransactionWiringTest} 同款：DataSource 是<b>刻意不可用</b>的替身
 * ⇒ 本类只验「事务边界这个机制在<b>真实编排入口</b>上有没有被装上」，
 * <b>不</b>验数据库层面的真实行回滚（那一层由同模块 {@code UserRpcContainerWiringTest}
 * 用真 Testcontainers PostgreSQL 覆盖）⇒ <b>两者互补，不要互相替代</b>。
 *
 * <p>⚠️ <b>本类不测「事务边界该放在哪」</b>（那是 Go 侧 {@code model/user.go:72} / {@code :134}
 * 的 {@code conn.Transaction(...)} 的形状对齐问题，属阶段 2）⇒
 * 本类只把「不许悄悄退化」钉死。
 */
class AvatarTransactionEntryProxyGateTest {

    private AnnotationConfigApplicationContext ctx;
    private RecordingTxManager txManager;

    // ================== 门 1：结构 —— 编排层不得自带事务 ==================

    @Test
    @DisplayName("门 1：UserDomainService 自身不得有任何 @Transactional 方法（自调用会绕过代理）")
    void domainServiceMustNotCarryTransactionalMethods() {
        List<String> offenders = new ArrayList<>();
        for (Method m : UserDomainService.class.getDeclaredMethods()) {
            if (AnnotationUtils.findAnnotation(m, Transactional.class) != null) {
                offenders.add(m.getName());
            }
        }
        assertTrue(offenders.isEmpty(),
                "UserDomainService 上出现 @Transactional ⇒ 同 Bean 自调用会绕过代理 ⇒ 4/5 步原子性静默失效；"
                        + "事务声明必须留在 UserAvatarHistoryMapper 上。实际命中 = " + offenders);
    }

    // ================== 门 2：结构 —— mapper 必须是「注入来的」 ==================

    @Test
    @DisplayName("门 2：编排层的 mapper 字段必须是 final + 构造器注入，且不得被 static 字段持有")
    void mapperMustBeConstructorInjectedAndFinal() throws Exception {
        Field f = UserDomainService.class.getDeclaredField("avatarHistoryMapper");
        assertEquals(UserAvatarHistoryMapper.class, f.getType(),
                "avatarHistoryMapper 字段类型必须是接口（⇒ Spring 注入的是代理），实际 = " + f.getType());
        assertTrue(Modifier.isFinal(f.getModifiers()),
                "avatarHistoryMapper 字段必须 final（防止运行期被换成一个非代理实例）");

        boolean ctorInjects = Arrays.stream(UserDomainService.class.getDeclaredConstructors())
                .anyMatch(c -> Arrays.asList(c.getParameterTypes()).contains(UserAvatarHistoryMapper.class));
        assertTrue(ctorInjects,
                "UserDomainService 必须由构造器注入 UserAvatarHistoryMapper（⇒ 容器交出来的就是代理）");

        for (Field sf : UserDomainService.class.getDeclaredFields()) {
            assertFalse(Modifier.isStatic(sf.getModifiers())
                            && UserAvatarHistoryMapper.class.isAssignableFrom(sf.getType()),
                    "static 字段 " + sf.getName() + " 持有 mapper ⇒ 绕过容器 ⇒ 不是代理 ⇒ 事务失效");
        }
    }

    // ================== 门 3：运行时 —— 编排层拿到的就是事务代理 ==================

    @Test
    @DisplayName("门 3：编排层实际持有的 mapper 必须是 AOP 代理，且两个多步方法仍带 @Transactional")
    void injectedMapperIsTheTransactionalProxy() throws Exception {
        UserDomainService svc = unwrap(ctx.getBean(UserDomainService.class));
        Object mapper = readField(svc, "avatarHistoryMapper");

        assertNotNull(mapper, "编排层持有的 mapper 为空");
        assertTrue(AopUtils.isAopProxy(mapper),
                "编排层拿到的 mapper 不是 AOP 代理 ⇒ 调用不经过事务拦截器 ⇒ 4/5 步原子性等于没有");

        TransactionAttributeSource tas = new AnnotationTransactionAttributeSource();
        List<String> advised = new ArrayList<>();
        for (Method m : UserAvatarHistoryMapper.class.getDeclaredMethods()) {
            if (tas.getTransactionAttribute(m, UserAvatarHistoryMapper.class) != null) {
                advised.add(m.getName());
            }
        }
        assertTrue(advised.contains("createAvatarHistory") && advised.contains("selectAvatarHistory"),
                "两个多步方法的 @Transactional 必须仍在 mapper 上，实际命中 = " + advised);
    }

    // ================== 门 4：真触发 —— 走真实编排入口，事务必须真的开、真的回滚 ==================

    @Test
    @DisplayName("门 4：真实入口 selectAvatar 走代理 ⇒ 事务必须 begin → rollback，且不得 commit")
    void realEntrySelectAvatarMustGoThroughTheProxy() {
        UserDomainService svc = ctx.getBean(UserDomainService.class);
        txManager.calls.clear();

        // W1 的 SQL 必然失败（DataSource 刻意不可用）⇒ mapper 的事务方法抛异常
        // ⇒ 事务拦截器必须 rollback ⇒ 编排层把它翻成 INTERNAL 上抛。
        assertThrows(RuntimeException.class,
                () -> svc.selectAvatar(SelectAvatarReq.newBuilder().setUid(1001L).setHistoryId("H-1").build()),
                "selectAvatar 在 DataSource 不可用时必须抛异常");

        assertEquals(List.of("begin", "rollback"), txManager.calls,
                "真实编排入口必须经代理 ⇒ 事务开启并回滚；实际事件流 = " + txManager.calls);
        assertFalse(txManager.calls.contains("commit"),
                "失败路径绝不能出现 commit");
    }

    @Test
    @DisplayName("门 4b：对照 —— 无事务的普通查询入口不得开启事务（说明门 4 的「begin」不是噪声）")
    void plainLookupMustNotStartTransaction() {
        UserDomainService svc = ctx.getBean(UserDomainService.class);
        txManager.calls.clear();

        assertThrows(RuntimeException.class,
                () -> svc.getAvatarHistory(GetAvatarHistoryReq.newBuilder().setUid(1001L).setLimit(10).build()),
                "getAvatarHistory 在 DataSource 不可用时必须抛异常");

        assertEquals(List.of(), txManager.calls,
                "getAvatarHistory 路径上不应有任何事务事件，实际 = " + txManager.calls);
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

    private static Object readField(Object target, String name) throws Exception {
        Field f = UserDomainService.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    /**
     * 解开 Spring AOP 代理，取回<b>目标对象</b>。
     *
     * <p>为什么必须解：一旦有人给 {@code UserDomainService} 本体加上 {@code @Transactional}，
     * 容器交出来的就是 <b>CGLIB 子类代理</b> ⇒ 代理实例自己的字段是<b>空的</b>（值在目标对象上）
     * ⇒ 直接读字段会读到 {@code null} ⇒ 门 3 会以「mapper 为空」这种<b>误导性</b>信息变红
     * ⇒ ⇒ 这里先解代理，门 3 的断言信息才始终指向真正要查的东西
     * （那种退化由<b>门 1</b> 精确拦下，门 3 只负责说清「代理是不是事务代理」）。
     */
    private static UserDomainService unwrap(UserDomainService bean) throws Exception {
        if (bean instanceof org.springframework.aop.framework.Advised advised) {
            Object target = advised.getTargetSource().getTarget();
            if (target instanceof UserDomainService svc) {
                return svc;
            }
        }
        return bean;
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
                    throw new SQLException("AvatarTransactionEntryProxyGateTest: 刻意不连真实库");
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

        // ---- 编排层的真实 Bean（被测主体）----

        @Bean
        AvatarDomainService avatarDomainService() {
            return new AvatarDomainService();
        }

        @Bean
        UserDomainService userDomainService(UserMapper userMapper,
                                            UserAvatarHistoryMapper avatarHistoryMapper,
                                            UserRpcMetrics metrics,
                                            StringRedisTemplate bizRedis,
                                            LoggerFactoryBean logger,
                                            UserRpcProperties properties,
                                            AvatarDomainService avatarDomainService) {
            return new UserDomainService(userMapper, avatarHistoryMapper, metrics, bizRedis,
                    logger, properties, avatarDomainService);
        }

        // ---- 与事务无关的依赖：换成替身（不进事务路径）----

        @Bean
        UserRpcMetrics userRpcMetrics() {
            return new UserRpcMetrics(new SimpleMeterRegistry());
        }

        @Bean
        StringRedisTemplate bizRedis() {
            return mock(StringRedisTemplate.class);
        }

        @Bean
        LoggerFactoryBean logger() {
            return mock(LoggerFactoryBean.class);
        }

        /**
         * ⚠️ 用替身而不是真 bean：真 {@code UserRpcProperties} 带 {@code @PostConstruct}
         * 的 JWT 密钥非空校验（域内 P0#8 守卫）⇒ 装配它会让本测试去关心一个与事务无关的
         * 启动期约束 ⇒ 反而把「事务边界这条断言」淹没。
         */
        @Bean
        UserRpcProperties properties() {
            return mock(UserRpcProperties.class);
        }
    }

    /**
     * 记录型事务管理器 —— 把「事务边界有没有生效」变成可断言的事实。
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
