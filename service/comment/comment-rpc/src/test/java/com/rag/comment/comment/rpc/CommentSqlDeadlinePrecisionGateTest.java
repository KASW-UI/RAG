package com.rag.comment.comment.rpc;

import com.rag.comment.comment.rpc.domain.CommentIndexEntity;
import com.rag.comment.comment.rpc.mapper.CommentIndexMapper;
import com.rag.comment.comment.rpc.support.RealContainerHostPort;
import com.rag.common.observability.RpcInterceptor.DeadlineState;
import com.rag.common.persistence.DeadlineQueryTimeoutInjector;
import com.rag.common.persistence.SqlTimeoutStatusTranslator;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.grpc.Status;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.postgresql.jdbc.PgStatement;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Wrapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🔴 E46 守门：<b>真 PostgreSQL + 真 MyBatis 插件链</b>上的「SQL 取消时刻 == deadline 时刻」与
 * 「过期 ⇒ 事务回滚」。
 *
 * <h2>为什么必须是真 PG（§3.3）</h2>
 * <p>本条要证明的是<b>驱动与服务端之间的时序</b>：CancelRequest 什么时候发出去、服务端什么时候掐掉查询、
 * 事务什么时候回滚。mock 出来的 Statement 只能证明「代码写成了这样」，证明不了任何时序。
 * ⇒ 本类用 Testcontainers 起<b>真 PostgreSQL</b>、用<b>真 pgjdbc</b>、走<b>真 MyBatis 插件链</b>
 * （生产链路上的那两个拦截器原样注册）。
 *
 * <h2>🔴 为什么还必须是<b>真连接池</b>（E62 · 第四十五轮）</h2>
 * <p><b>「真 PG」不等于「真生产路径」。</b>本类原先用 {@code DriverManagerDataSource}（<b>没有池</b>）
 * ⇒ 拿到的 {@code Statement} 就是 pgjdbc 的实现类 ⇒ 注入器的毫秒级反射探测<b>成功</b>
 * ⇒ ⇒ <b>守门绿、生产坏</b>（生产走 HikariCP ⇒ 拿到的是代理类 ⇒ 探测必失败 ⇒ 退回秒级）。
 * ⇒ 现在本类用<b>真 {@code HikariDataSource}</b> ⇒ <b>第一次跑在生产形态上</b>
 * ⇒ 并有一条用例断言「拿到的确实是池代理」⇒ <b>守门自己不再能被自己改坏</b>。
 * ⇒ 同族：<code>docs/Pitfalls/global/cross-domain.md</code> 的 **PIT-GLOBAL-056 / 057**。
 *
 * <h2>Go 出处（本类断言的语义基准）</h2>
 * <ul>
 *   <li><b>取消在途查询</b>：Go 侧 {@code service/comment/rpc/internal/model/comment.go:36}
 *       {@code m.conn.WithContext(ctx).Transaction(...)} ⇒ ctx 到点 ⇒ pgx 发 CancelRequest
 *       ⇒ 查询在<b>服务端立刻停止</b>。</li>
 *   <li><b>已过期 ⇒ 查询不发到服务端</b>：Go 标准库 {@code database/sql} 在取连接前查
 *       {@code ctx.Err()} ⇒ ctx 过期 ⇒ 查询根本不会发出。</li>
 *   <li><b>提交不看 deadline</b>：{@code gorm.io/gorm@v1.31.1} {@code finisher_api.go:622}
 *       ⇒ {@code tx.Commit()} 不检查 ctx ⇒ <b>Go 侧「查询失败 ⇒ 回滚；查询全成功 ⇒ 提交」</b>，
 *       即使提交那一刻 ctx 已过期也一样提交。</li>
 * </ul>
 *
 * <h2>修前的行为（= 本守门的红态）</h2>
 * <p>旧实现把剩余毫秒<b>向上取整成秒</b>后交给 {@code setQueryTimeout(int)}
 * ⇒ 取消时刻 = {@code deadline + (0~999ms]} ⇒ 且「已过期」时仍然放行这条 SQL 再跑最多 1 秒
 * ⇒ 事务可能提交 ⇒ <b>客户端已放弃、服务端却把写入提交了</b>。
 */
@Testcontainers
class CommentSqlDeadlinePrecisionGateTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("first_db")
            .withUsername("admin")
            .withPassword("password")
            .withCreateContainerCmdModifier(RealContainerHostPort.bindPg(RealContainerHostPort.COMMENT_SQL_DEADLINE_PRECISION_GATE));

    /** 独立账本表 ⇒ 不与同模块其它测试类的 Flyway 账本撞号。 */
    private static final String LEDGER = "flyway_schema_history_comment_deadline_gate";

    /** 两条用例用的主键（固定值 ⇒ 失败时能直接手工查库）。 */
    private static final long ID_A = 9_000_001L;
    private static final long ID_B = 9_000_002L;

    private static JdbcTemplate jdbc;
    private static CommentIndexMapper indexMapper;
    private static DataSource dataSource;
    private static StatementTimeoutRecorder recorder;

    @BeforeAll
    static void setUp() {
        String url = PG.getJdbcUrl() + "?TimeZone=Asia/Shanghai";
        Flyway flyway = Flyway.configure()
                .dataSource(url, PG.getUsername(), PG.getPassword())
                .locations("classpath:db/migration")
                .schemas("public")
                .table(LEDGER)
                .baselineOnMigrate(true)
                .load();
        flyway.migrate();

        // 🔴🔴 **必须是真 HikariCP 池**（E62 · 2026-10-26 第四十五轮修）：
        // 生产链路的 DataSource 就是 HikariCP ⇒ Connection.prepareStatement 返回的是
        // `com.zaxxer.hikari.pool.HikariProxyPreparedStatement`（Javassist 运行时生成的 final 类，
        // **只实现 java.sql.Statement**）⇒ **驱动私有的毫秒级方法不在代理的接口面上**。
        // ⇒ 旧版守门用 `DriverManagerDataSource`（**没有池**）⇒ 拿到的就是 pgjdbc 实现类
        // ⇒ 反射探测成功 ⇒ **守门绿、生产坏**（E46 的守门正是这么骗过自己的）。
        // ⇒ 这里换成真池 ⇒ 守门跑的就是生产形态。
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(url);
        hikari.setUsername(PG.getUsername());
        hikari.setPassword(PG.getPassword());
        hikari.setMaximumPoolSize(4);   // 锁连接 + 被阻塞的查询连接 ⇒ 至少要 2
        hikari.setPoolName("comment-deadline-gate");
        dataSource = new HikariDataSource(hikari);
        jdbc = new JdbcTemplate(dataSource);

        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("deadline-gate",
                new SpringManagedTransactionFactory(), dataSource));
        // 🔴 这两个就是生产链路上的那两个拦截器（E46 的落点在 service/common）⇒ 不是「另写一个假的」。
        configuration.addInterceptor(new DeadlineQueryTimeoutInjector());
        configuration.addInterceptor(new SqlTimeoutStatusTranslator());
        // 🔴 记录器**必须排在注入器之后** ⇒ 它在插件链里更外层 ⇒ proceed() 返回时
        //    注入器**已经**把超时设好了 ⇒ 它读到的就是「消费点上的真实值」。
        recorder = new StatementTimeoutRecorder();
        configuration.addInterceptor(recorder);
        configuration.addMapper(CommentIndexMapper.class);
        SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(configuration);
        SqlSessionTemplate template = new SqlSessionTemplate(factory);
        indexMapper = template.getMapper(CommentIndexMapper.class);
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM comment_index WHERE id IN (?, ?)", ID_A, ID_B);
        recorder.reset();
    }

    private static CommentIndexEntity entity(long id) {
        CommentIndexEntity e = new CommentIndexEntity();
        e.setId(id);
        e.setTargetType("article");
        e.setTargetId("t-1");
        e.setUserId(42L);
        // ⚠️ created_at / updated_at 是 NOT NULL 且**由调用方显式赋值**
        //    （mapper 的 INSERT 把这两列绑成参数，不是 CURRENT_TIMESTAMP）⇒ 不给值会被 PG 拒。
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        return e;
    }

    private static Throwable catchThrowable(Runnable r) {
        try {
            r.run();
            return null;
        } catch (Throwable t) {
            return t;
        }
    }

    /** 把异常链摊平成人能读的串（断言失败时直接进报告）。 */
    private static String describeChain(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (sb.length() > 0) {
                sb.append("  <-  ");
            }
            sb.append(c.getClass().getName()).append(": ").append(c.getMessage());
            if (c instanceof java.sql.SQLException sql) {
                sb.append(" [SQLState=").append(sql.getSQLState()).append(']');
            }
            if (c.getCause() == c) {
                break;
            }
        }
        return sb.toString();
    }

    // ====================================================================================
    // ① 已过期 ⇒ 查询立即失败 ⇒ 事务回滚（Go 侧 ctx cancel 同款）
    // ====================================================================================

    @Test
    @DisplayName("🔴 deadline 已过 ⇒ 第二条语句立即失败（DEADLINE_EXCEEDED）⇒ 第一条随事务回滚")
    void expiredDeadlineFailsStatementAndRollsBackTransaction() {
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        Throwable thrown = null;
        try {
            tx.execute(status -> {
                // 第一条：此刻线程上没有绑定 deadline ⇒ 不设任何超时 ⇒ 正常写入
                indexMapper.insert(entity(ID_A));
                // 第二条：deadline 已过 ⇒ Go 侧这条 SQL 根本不会发到服务端 ⇒ 必然失败
                try (DeadlineState.Scope ignored =
                             DeadlineState.bindToCurrent(System.currentTimeMillis() - 1_000)) {
                    indexMapper.insert(entity(ID_B));
                }
                return null;
            });
        } catch (Throwable t) {
            thrown = t;
        }

        assertThat(thrown)
                .as("过期后仍执行 SQL ⇒ 事务会带着写入提交 ⇒ 与 Go 的 ctx cancel 语义相反")
                .isNotNull();
        assertThat(Status.fromThrowable(thrown).getCode())
                .as("SQL 被 deadline 取消必须翻译成 DEADLINE_EXCEEDED（不能是 INTERNAL）\n实际异常链：%s",
                        describeChain(thrown))
                .isEqualTo(Status.Code.DEADLINE_EXCEEDED);

        // 🔴 关键断言：异常上抛 ⇒ 事务回滚 ⇒ 第一条写入**不在**表里。
        //    这正是 E46 的核心后果「客户端已放弃、服务端却提交了写入」的反面。
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM comment_index WHERE id IN (?, ?)", Integer.class, ID_A, ID_B))
                .as("过期后失败 ⇒ 整个事务回滚 ⇒ 两条写入都不该留在表里")
                .isZero();
    }

    // ====================================================================================
    // ② 在途查询在 deadline 时刻被取消（毫秒级）
    // ====================================================================================

    @Test
    @DisplayName("🔴 在途查询在 deadline 时刻被取消 ⇒ 存活时长 ≈ 剩余毫秒（不是「最晚晚 999ms」）")
    void inFlightQueryIsCanceledAtDeadline() throws Exception {
        // 另开一个连接持有 ACCESS EXCLUSIVE 锁 ⇒ 目标连接的 SELECT 会阻塞在锁上。
        // ⚠️ 必须用表级 ACCESS EXCLUSIVE：普通 SELECT 走 MVCC 快照读，**不会被行锁挡住**。
        try (Connection lockConn = dataSource.getConnection()) {
            lockConn.setAutoCommit(false);
            try (Statement st = lockConn.createStatement()) {
                st.execute("LOCK TABLE comment_index IN ACCESS EXCLUSIVE MODE");
            }

            long remainingMs = 300;
            long deadline = System.currentTimeMillis() + remainingMs;
            long startNanos = System.nanoTime();

            Throwable thrown;
            try (DeadlineState.Scope ignored = DeadlineState.bindToCurrent(deadline)) {
                thrown = catchThrowable(() -> indexMapper.findOneByIdPlain(ID_A));
            }
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

            assertThat(thrown).as("被锁阻塞的查询必须在 deadline 处被取消").isNotNull();
            assertThat(Status.fromThrowable(thrown).getCode())
                    .as("取消异常必须翻译成 DEADLINE_EXCEEDED")
                    .isEqualTo(Status.Code.DEADLINE_EXCEEDED);

            // 🔴 精度断言：毫秒精度 ⇒ 存活 ≈ 剩余毫秒 + 取消传播延迟（几十 ms）。
            //    旧的「秒级向上取整」⇒ 剩余 300ms 会被算成 1 秒 ⇒ 存活 ≈ 1000ms ⇒ 这条会红。
            assertThat(elapsedMs)
                    .as("在途查询的存活时长应贴近剩余 %dms（旧实现秒级取整会到 ~1000ms）", remainingMs)
                    .isLessThan(remainingMs + 400);
        }
    }

    // ====================================================================================
    // ③ E62 守门：池化代理路径下，毫秒级超时**真的落到驱动语句上**
    // ====================================================================================

    /**
     * <b>记录器</b>：在 {@code StatementHandler.prepare} 的<b>消费点</b>上读驱动语句的超时值。
     *
     * <p><b>为什么必须断言「消费点」而不是只看 Bean 存在 / 只看最终行为（§3.3）</b>：
     * 「Bean 存在」只能证明「代码写成了这样」；「最终行为对」只能间接推断。
     * ⇒ 本记录器在<b>注入器刚刚设完超时的那一刻</b>，把驱动语句上的 {@code timeout} 读出来
     * ⇒ ⇒ **直接证明「毫秒值真的送到了驱动」**，而不是「看起来结果对」。
     *
     * <p><b>为什么它必须排在注入器之后注册</b>：MyBatis 的插件链是
     * {@code interceptorChain.pluginAll} 逐个包裹 ⇒ <b>最后注册的在外层</b>
     * ⇒ 外层的 {@code proceed()} 返回时，内层的注入器<b>已经</b>设好超时了。
     */
    @Intercepts(@Signature(
            type = StatementHandler.class,
            method = "prepare",
            args = {Connection.class, Integer.class}))
    static final class StatementTimeoutRecorder implements Interceptor {

        /** 插件链里真正拿到的那个 Statement（生产形态下 = Hikari 代理）。 */
        volatile Statement captured;

        /** 驱动语句上的超时（毫秒）。读不到时为 -1。 */
        volatile long driverTimeoutMs = -1L;

        void reset() {
            captured = null;
            driverTimeoutMs = -1L;
        }

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            Object prepared = invocation.proceed();
            if (prepared instanceof Statement statement) {
                captured = statement;
                driverTimeoutMs = readDriverTimeoutMs(statement);
            }
            return prepared;
        }

        /**
         * 把池代理剥到驱动实现，再读它内部的 {@code timeout}。
         *
         * <p>实测 pgjdbc：{@code setQueryTimeout(int s)} 内部就是
         * {@code setQueryTimeoutMs(s * 1000L)}（源码 616-618 行）⇒
         * ⇒ {@code getQueryTimeoutMs()} <b>能精确区分两条路径</b>：
         * 毫秒路径 = 剩余毫秒（≤ 剩余）· 秒级路径 = 向上取整后的整秒（&gt; 剩余）。
         */
        private static long readDriverTimeoutMs(Statement statement) {
            try {
                Statement driver = statement;
                if (statement instanceof Wrapper wrapper) {
                    Statement unwrapped = wrapper.unwrap(Statement.class);
                    if (unwrapped != null) {
                        driver = unwrapped;
                    }
                }
                if (driver instanceof PgStatement pgStatement) {
                    return pgStatement.getQueryTimeoutMs();
                }
            } catch (SQLException e) {
                return -1L;
            }
            return -1L;
        }

        @Override
        public Object plugin(Object target) {
            return org.apache.ibatis.plugin.Plugin.wrap(target, this);
        }

        @Override
        public void setProperties(java.util.Properties properties) {
            // 无配置项
        }
    }

    @Test
    @DisplayName("🔴 E62：真 HikariCP 池路径 ⇒ 毫秒级超时真的落到驱动语句上（不是退回秒级）")
    void pooledPathAppliesMillisecondTimeoutOnDriverStatement() {
        long remainingMs = 1_500L;
        long deadline = System.currentTimeMillis() + remainingMs;

        try (DeadlineState.Scope ignored = DeadlineState.bindToCurrent(deadline)) {
            indexMapper.findOneByIdPlain(ID_A);
        }

        Statement captured = recorder.captured;
        assertThat(captured)
                .as("记录器必须真的在插件链里跑过（否则下面的断言全是空转）")
                .isNotNull();

        // 🔴 自证断言：这条守门**必须跑在池化代理路径上** ⇒ 否则它又变成「守门绿、生产坏」
        //    （旧版用 DriverManagerDataSource ⇒ 无池 ⇒ 拿到的就是驱动实现类 ⇒ 探测必然成功）。
        assertThat(captured.getClass().getName())
                .as("🔴 必须真的走池化代理路径（生产形态）⇒ 否则本守门证明不了生产行为")
                .contains("Hikari");

        long driverTimeoutMs = recorder.driverTimeoutMs;
        assertThat(driverTimeoutMs)
                .as("必须真的读到驱动语句上的超时（读不到 = -1）")
                .isGreaterThan(0L);

        // 🔴 关键断言：毫秒路径 ⇒ 驱动上的超时 **≤ 剩余毫秒**；
        //    秒级向上取整 ⇒ 1.5s 会变成 2000ms ⇒ **必然越界** ⇒ 这条守门在 E62 未修时是红的。
        assertThat(driverTimeoutMs)
                .as("驱动语句上的超时必须是毫秒级原值（秒级向上取整会给 2000ms ⇒ 取消时刻最晚比 deadline 晚 999ms）")
                .isLessThanOrEqualTo(remainingMs);
        assertThat(driverTimeoutMs)
                .as("驱动语句上的超时应当贴近剩余毫秒（说明它确实来自 deadline，而不是别的数）")
                .isGreaterThan(remainingMs - 500L);
    }
}
