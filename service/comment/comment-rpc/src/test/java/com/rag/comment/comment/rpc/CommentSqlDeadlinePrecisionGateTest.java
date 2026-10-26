package com.rag.comment.comment.rpc;

import com.rag.comment.comment.rpc.domain.CommentIndexEntity;
import com.rag.comment.comment.rpc.mapper.CommentIndexMapper;
import com.rag.common.observability.RpcInterceptor.DeadlineState;
import com.rag.common.persistence.DeadlineQueryTimeoutInjector;
import com.rag.common.persistence.SqlTimeoutStatusTranslator;
import io.grpc.Status;
import org.apache.ibatis.mapping.Environment;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
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
            .withPassword("password");

    /** 独立账本表 ⇒ 不与同模块其它测试类的 Flyway 账本撞号。 */
    private static final String LEDGER = "flyway_schema_history_comment_deadline_gate";

    /** 两条用例用的主键（固定值 ⇒ 失败时能直接手工查库）。 */
    private static final long ID_A = 9_000_001L;
    private static final long ID_B = 9_000_002L;

    private static JdbcTemplate jdbc;
    private static CommentIndexMapper indexMapper;
    private static DataSource dataSource;

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

        dataSource = new DriverManagerDataSource(url, PG.getUsername(), PG.getPassword());
        jdbc = new JdbcTemplate(dataSource);

        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("deadline-gate",
                new SpringManagedTransactionFactory(), dataSource));
        // 🔴 这两个就是生产链路上的那两个拦截器（E46 的落点在 service/common）⇒ 不是「另写一个假的」。
        configuration.addInterceptor(new DeadlineQueryTimeoutInjector());
        configuration.addInterceptor(new SqlTimeoutStatusTranslator());
        configuration.addMapper(CommentIndexMapper.class);
        SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(configuration);
        SqlSessionTemplate template = new SqlSessionTemplate(factory);
        indexMapper = template.getMapper(CommentIndexMapper.class);
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM comment_index WHERE id IN (?, ?)", ID_A, ID_B);
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
}
