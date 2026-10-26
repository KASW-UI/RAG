package com.rag.favorite.favorite.rpc;

import com.rag.common.observability.RpcInterceptor.DeadlineState;
import com.rag.favorite.favorite.rpc.mapper.FavoriteFolderMapper;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;


/**
 * 🔴🔴 真 PG 上的 <b>「SQL 超时精度」生产路径</b>校验 —— 钉住本域台账里那组「毫秒精度」表述的<b>真实前提</b>。
 *
 * <h2>为什么需要这个类</h2>
 * <p>本域台账 §28 记着「共享层 {@code DeadlineQueryTimeoutInjector} 改成毫秒精度 ⇒ 取消时刻 == deadline 时刻」。
 * <b>那是共享层那版实现的描述</b>；<b>但它在本域的生产路径上能不能真的走到，是另一件事</b>。
 * <p>共享层 {@code global} 域第四十一轮查明（编号 E62）：毫秒精度的探测用的是
 * {@code statement.getClass().getMethod("setQueryTimeoutMs", long.class)}，
 * 而<b>生产路径上 statement 是 HikariCP 的代理类</b> ⇒ 该方法<b>不可达</b> ⇒
 * 探测必然失败 ⇒ <b>退回秒级向上取整</b>。
 * <p>⇒ 本类<b>不替共享层下结论</b>（那是 global 的单写权），只回答<b>本域自己的问题</b>：
 * <b>favorite-rpc 的生产路径上，注入器看到的 statement 到底是什么、有效超时值到底是什么。</b>
 *
 * <h2>Go 出处（取消语义 = 本类断言的上游语义）</h2>
 * <ul>
 *   <li>{@code service/favorite/rpc/internal/model/favorite.go:34}
 *       （{@code m.conn.WithContext(ctx).Create(folder)}）—— 同款写法另见
 *       {@code :39} {@code :51} {@code :63} {@code :76} {@code :84} {@code :96} {@code :108}
 *       ⇒ <b>每次 DB 访问都带 ctx</b> ⇒ pgx 在 ctx 到点时向 PG 发 <b>CancelRequest</b>
 *       ⇒ <b>Go 侧取消时刻 == deadline 时刻</b>（毫秒级误差来自网络传播，不来自取整）</li>
 * </ul>
 *
 * <h2>本类断言的四件事</h2>
 * <ol>
 *   <li>🔴 <b>生产路径的 statement 是 HikariCP 代理类</b>
 *       ⇒ 毫秒 API 在<b>它自己的类上</b>不可达（不是「驱动没这个方法」）</li>
 *   <li>🔴 <b>有效超时值是秒级向上取整</b>：给驱动的是 {@code ceil(R/1000)} 秒
 *       ⇒ 驱动内部的<b>原始毫秒</b>是整秒，不是 R</li>
 *   <li>🔴 <b>阴性对照</b>：同一个 PG、<b>不走连接池</b>时，statement 是 pgjdbc 实现类
 *       ⇒ 毫秒 API <b>可达</b> ⇒ ⇒ 证明「差别来自<b>连接池代理</b>，不是驱动缺方法」</li>
 *   <li>🟢 <b>没有 deadline 时注入器完全不动</b>（定时任务 / MQ 消费 / 启动期建表不受影响）</li>
 * </ol>
 *
 * <h2>判别器：为什么能分辨「秒级」与「毫秒级」</h2>
 * <p>pgjdbc 的 {@code setQueryTimeout(int)} <b>内部委托给</b> {@code setQueryTimeoutMs(seconds * 1000L)}
 * （{@code javap -c org.postgresql.jdbc.PgStatement} 实测）⇒ 驱动内部只有一个毫秒字段 {@code timeout}
 * ⇒ {@code getQueryTimeoutMs()} 读的就是它 ⇒
 * <b>秒级路径</b> ⇒ 该字段 = {@code ceil(R/1000)*1000}（<b>整秒</b>）；
 * <b>毫秒路径</b> ⇒ 该字段 = <b>原样 R</b>（非整秒）。
 * <p>⇒ 本类把 R 故意取成 <b>约 1700ms</b>（绑 2000ms 后先睡 300ms）⇒ 两种路径给出<b>不同</b>的值
 * ⇒ ⇒ 断言能真正区分，而不是「碰巧都绿」。
 *
 * <h2>⚠️ 与 Go 侧的语义关系（不要误读）</h2>
 * <p>本类<b>不</b>断言「RAG 与 Go 的取消时刻相同」—— 那是<b>共享层</b>的事，且当前<b>不成立</b>
 * （Go 到 deadline 精确取消，RAG 走秒级回退 ⇒ 最坏晚 ≤999ms）。
 * <p>本类只钉住<b>本域生产路径的当前事实</b>：<b>秒级回退</b>。
 * <p>⇒ <b>一旦共享层修好「探测目标」（改成先 unwrap 到驱动实现类）</b>，本类会<b>转红</b>
 * ⇒ 那正是要更新本域台账的信号（<b>不是回归</b>）。
 *
 * <p>⚠️ 与 {@code FavoriteFolderItemTimeColumnTest} 同款纪律：<b>真 PG</b>、<b>真 HikariCP</b>、
 * <b>真 MyBatis 插件链</b>（生产注册的那个注入器原样生效）、<b>真 mapper</b>；不用 H2。
 */
@Testcontainers
@EnabledIfDockerAvailable
@SpringBootTest(
        classes = FavoriteRpcApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "grpc.server.port=0",
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "grpc.client.user-rpc.address=static://127.0.0.1:1",
                "grpc.client.article-rpc.address=static://127.0.0.1:1",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
class FavoriteSqlTimeoutProductionPathTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("first_db")
                    .withUsername("admin")
                    .withPassword("password");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("favorite.fact.dispatch.enabled", () -> "false");
        registry.add("favorite.fact.authority.enabled", () -> "false");
        registry.add("favorite.fact.legacy-preflight.enabled", () -> "false");
    }

    /** 生产数据源（Spring Boot 默认 HikariCP；本域 {@code application.yml} 也显式配了 {@code spring.datasource.hikari.*}）。 */
    @Autowired
    private DataSource dataSource;

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Autowired
    private FavoriteFolderMapper folderMapper;

    /**
     * 探针（<b>静态</b>）。
     *
     * <p>🔴 <b>为什么是静态、且装上之后不摘</b>：MyBatis 的 {@code Configuration.getInterceptors()}
     * 返回的是<b>不可变</b>列表（实测 {@code UnsupportedOperationException}）⇒ 公开 API <b>摘不掉</b>。
     * ⇒ 改用「<b>静态单例 + 幂等安装一次</b>」：探针是<b>纯只读观测</b>（只读类名与超时值，不改任何行为）
     * ⇒ 留在插件链里对其它测试类**无害**。
     */
    private static final StatementProbe PROBE = new StatementProbe();

    private static boolean probeInstalled;

    @BeforeEach
    void installProbe() {
        if (probeInstalled) {
            return;
        }
        // 追加到插件链末尾 ⇒ MyBatis 的 pluginAll 是「后加的在最外层」
        // ⇒ 本探针在外层 ⇒ invocation.proceed() 返回时，内层的 DeadlineQueryTimeoutInjector 已经设过超时。
        sqlSessionFactory.getConfiguration().addInterceptor(PROBE);
        probeInstalled = true;
    }

    @Test
    @DisplayName("🔴 生产路径：注入器看到的 statement 是 HikariCP 代理类 ⇒ 毫秒 API 在它自己类上不可达")
    void productionPathStatementIsPoolProxyWithoutMillisecondApi() {
        assertEquals("com.zaxxer.hikari.pool.HikariProxyPreparedStatement", probePath().getName(),
                "favorite-rpc 的生产数据源必须是 HikariCP（Spring Boot 默认 + 本域 application.yml 显式配了 spring.datasource.hikari）");

        assertThrows(NoSuchMethodException.class,
                () -> probePath().getMethod("setQueryTimeoutMs", long.class),
                "🔴 Hikari 代理类上不该有毫秒 API ⇒ 任何「按 statement.getClass() 找毫秒方法」的探测都必然失败");
    }

    @Test
    @DisplayName("🔴 生产路径的有效超时是【秒级向上取整】（代理层看到的秒数）")
    void productionPathEffectiveTimeoutIsSecondLevelCeil() throws Exception {
        runWithDeadlineAndQuery();

        assertNotNull(PROBE.statementClass.get(), "探针必须抓到 statement");

        // 代理对外只暴露秒级接口；驱动内部的原始毫秒在另一个测试里断言。
        // 🔴 两条断言必须**分属两个测试方法**：同方法内第一条失败会**遮住**第二条
        //    ⇒ 届时只知道「红了」，不知道「红在哪一条、实际值是多少」。
        assertEquals(2, PROBE.queryTimeoutSeconds.get(),
                "R ≈ 1700ms ⇒ 秒级路径 = ceil(1700/1000) = 2 秒；"
                        + "若共享层改成走毫秒路径，这里会变成 floor(1700/1000) = 1 ⇒ "
                        + "本条会转红 —— 那是【台账该更新】的信号（见 docs/DRIFT/favorite/rpc.md §36），不是回归");
    }

    @Test
    @DisplayName("🔴 生产路径的【驱动内部原始毫秒】是整秒（秒级路径的指纹）")
    void productionPathDriverRawMillisIsWholeSecond() throws Exception {
        runWithDeadlineAndQuery();

        assertNotNull(PROBE.statementClass.get(), "探针必须抓到 statement");

        // 这是**判别两条路径的量**：秒级路径写进去的是 ceil(R/1000)*1000（整秒）；
        // 毫秒路径写进去的是**原样 R**（≈1700，非整秒）⇒ ⇒ 整秒 ⇔ 走的是秒级路径。
        assertEquals(2000L, PROBE.queryTimeoutMs.get(),
                "🔴 秒级路径 ⇒ 驱动内部毫秒 = ceil(R/1000)*1000 = 2000（整秒）；"
                        + "若共享层改成走毫秒路径，这里会是 ≈R（≈1700，非整秒）⇒ "
                        + "本条会转红 —— 那是【台账该更新】的信号（见 docs/DRIFT/favorite/rpc.md §36），不是回归");
    }

    @Test
    @DisplayName("🔴 阴性对照：同一个 PG 不走连接池 ⇒ statement 是 pgjdbc 实现类 ⇒ 毫秒 API 可达")
    void withoutPoolTheDriverStatementDoesExposeMillisecondApi() throws Exception {
        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             PreparedStatement ps = conn.prepareStatement("SELECT 1")) {

            Class<?> driverClass = ps.getClass();
            assertEquals("org.postgresql.jdbc.PgPreparedStatement", driverClass.getName(),
                    "不走池 ⇒ 拿到的是 pgjdbc 实现类");

            // 能找到 ⇒ 驱动**有**毫秒 API ⇒ 差别只在「连接池代理」，不在驱动。
            Method ms = driverClass.getMethod("setQueryTimeoutMs", long.class);
            assertNotNull(ms);

            // 🔴 阳性对照（变异自证）：毫秒路径**真的能用**时，驱动内部毫秒就是**原值**（1700）
            // ⇒ ⇒ 生产路径上读到的 2000（整秒）**不可能**是「读不出来 / 读错了」
            // ⇒ ⇒ 只能解释成「确实走的是秒级回退」⇒ ⇒ 这条断言**真的在区分两条路径**，不是恒真。
            ms.invoke(ps, 1700L);
            Object raw = driverClass.getMethod("getQueryTimeoutMs").invoke(ps);
            assertNotNull(raw);
            assertEquals(1700L, ((Long) raw).longValue(),
                    "🔴 毫秒路径会把原值原样写进去 ⇒ 与生产路径上的 2000（整秒）**明确不同** "
                            + "⇒ 证明生产路径确实走的是秒级回退");
        }
    }

    @Test
    @DisplayName("🟢 没有 deadline 时注入器完全不动（定时任务 / MQ / 启动期建表不受影响）")
    void withoutDeadlineNothingIsInjected() throws Exception {
        PROBE.reset();
        // 不 bindToCurrent ⇒ DeadlineState.remainingMillis() == Long.MAX_VALUE ⇒ 注入器直接 return
        folderMapper.findByUserId(4242L);

        assertNotNull(PROBE.statementClass.get(), "探针必须抓到 statement");
        assertEquals(0, PROBE.queryTimeoutSeconds.get(), "没有 deadline ⇒ 不设任何超时");
        assertEquals(0L, PROBE.queryTimeoutMs.get(), "没有 deadline ⇒ 驱动内部毫秒保持 0");
    }

    /** 绑一个 2000ms 的 deadline，先睡 300ms 让 R 落到 ≈1700ms，再跑一次真实读。 */
    private void runWithDeadlineAndQuery() throws InterruptedException {
        PROBE.reset();
        long deadline = System.currentTimeMillis() + 2000L;
        try (DeadlineState.Scope ignored = DeadlineState.bindToCurrent(deadline)) {
            Thread.sleep(300L);
            folderMapper.findByUserId(4242L);
        }
    }

    /** 跑一次带 deadline 的读并返回抓到的 statement 类。 */
    private Class<?> probePath() {
        try {
            runWithDeadlineAndQuery();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        assertNotNull(PROBE.statementClass.get(), "探针必须抓到 statement");
        return PROBE.statementClass.get();
    }

    /**
     * 探针：拦截 MyBatis 的 {@code StatementHandler.prepare}，记录 statement 的真实类与有效超时值。
     *
     * <p>🔴 <b>为什么必须拦 MyBatis 而不是直接 {@code dataSource.getConnection()}</b>：
     * 只有在<b>真实的 MyBatis 插件链里</b>才能证明「注入器看到的就是这个对象」——
     * 直接拿连接只能证明「池子会代理 statement」，证明不了「MyBatis 把它交给了注入器」。
     */
    @Intercepts(@Signature(type = StatementHandler.class, method = "prepare",
            args = {Connection.class, Integer.class}))
    static final class StatementProbe implements Interceptor {

        final AtomicReference<Class<?>> statementClass = new AtomicReference<>();
        final AtomicReference<Integer> queryTimeoutSeconds = new AtomicReference<>(-1);
        final AtomicReference<Long> queryTimeoutMs = new AtomicReference<>(-1L);

        void reset() {
            statementClass.set(null);
            queryTimeoutSeconds.set(-1);
            queryTimeoutMs.set(-1L);
        }

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            Object prepared = invocation.proceed();
            if (prepared instanceof Statement statement && statementClass.get() == null) {
                statementClass.set(statement.getClass());
                try {
                    queryTimeoutSeconds.set(statement.getQueryTimeout());
                } catch (SQLException e) {
                    queryTimeoutSeconds.set(-1);
                }
                queryTimeoutMs.set(readRawTimeoutMillis(statement));
            }
            return prepared;
        }

        /**
         * 读驱动内部的<b>原始毫秒</b>（{@code PgStatement.timeout} 字段）。
         *
         * <p>代理类只暴露秒级接口 ⇒ 必须先 unwrap 到驱动实现类。
         * <p>用反射而不是直接引用驱动实现类：驱动版本升级时这个方法名可能变，
         * 变了就<b>读不到</b>（返回 {@code null}）⇒ 断言会明确失败，而不是悄悄退化成秒级读数。
         */
        private static Long readRawTimeoutMillis(Statement statement) {
            try {
                Class<?> pgStatement = Class.forName("org.postgresql.jdbc.PgStatement");
                Object driver = statement.unwrap(pgStatement);
                Method getter = driver.getClass().getMethod("getQueryTimeoutMs");
                Object value = getter.invoke(driver);
                return value instanceof Long l ? l : null;
            } catch (ReflectiveOperationException | SQLException e) {
                return null;
            }
        }

        @Override
        public Object plugin(Object target) {
            return Plugin.wrap(target, this);
        }

        @Override
        public void setProperties(Properties properties) {
            // 本探针无配置项。
        }
    }
}
