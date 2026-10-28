package com.rag.favorite.favorite.rpc;

import com.rag.common.observability.RpcInterceptor.DeadlineState;
import com.rag.favorite.favorite.rpc.mapper.FavoriteFolderMapper;
import com.rag.favorite.favorite.rpc.support.RealGrpcPort;
import com.rag.favorite.favorite.rpc.support.RealContainerHostPort;
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
import static org.junit.jupiter.api.Assertions.assertTrue;


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
 * <h2>本类断言的五件事</h2>
 * <ol>
 *   <li>🔴 <b>生产路径的 statement 是 HikariCP 代理类</b>
 *       ⇒ 毫秒 API 在<b>它自己的类上</b>不可达（不是「驱动没这个方法」）
 *       ⇒ ⇒ 共享层<b>必须先 unwrap 到驱动实现类</b>才能用上毫秒精度</li>
 *   <li>🔴 <b>驱动内部原始毫秒 = 原样 R</b>（<b>不是</b> {@code ceil(R/1000)*1000}）
 *       ⇒ ⇒ <b>毫秒路径在本域生产路径上真的生效了</b></li>
 *   <li>🔴 <b>代理层看到的秒数 = floor(驱动原始毫秒 / 1000)</b>（<b>下取整</b>；
 *       秒级路径则是上取整）⇒ 与第 ② 条互为印证</li>
 *   <li>🔴 <b>阴性对照</b>：同一个 PG、<b>不走连接池</b>时，statement 是 pgjdbc 实现类
 *       ⇒ 毫秒 API <b>可达</b> ⇒ ⇒ 证明「差别来自<b>连接池代理</b>，不是驱动缺方法」</li>
 *   <li>🟢 <b>没有 deadline 时注入器完全不动</b>（定时任务 / MQ 消费 / 启动期建表不受影响）</li>
 * </ol>
 *
 * <h2>判别器：为什么能分辨「秒级」与「毫秒级」</h2>
 * <p>pgjdbc 的 {@code setQueryTimeout(int)} <b>内部委托给</b> {@code setQueryTimeoutMs(seconds * 1000L)}
 * （{@code javap -c org.postgresql.jdbc.PgStatement} 实测）⇒ 驱动内部只有一个毫秒字段 {@code timeout}
 * ⇒ {@code getQueryTimeoutMs()} 读的就是它 ⇒
 * <b>秒级路径</b> ⇒ 该字段 = {@code ceil(R/1000)*1000}（<b>比 R 大</b>）；
 * <b>毫秒路径</b> ⇒ 该字段 = <b>原样 R</b>。
 * <p>⇒ 本类把 R 故意取成 <b>约 1700ms</b>（绑 2000ms 后先睡 300ms）⇒ 两种路径给出<b>不同</b>的值
 * ⇒ ⇒ 断言能真正区分，而不是「碰巧都绿」。
 * <p>🔴 <b>断言不硬编码 1700 / 2000</b> —— 那些数会随时序漂移 ⇒ 改成
 * <b>用「R 的取值窗口」判定</b>（见 {@link DeadlineWindow}）⇒ <b>确定性</b>，且秒级路径必然落不进那个窗口。
 *
 * <h2>⚠️ 与 Go 侧的语义关系（不要误读）</h2>
 * <p>本类<b>不</b>断言「RAG 与 Go 的取消时刻相同」—— 那是<b>共享层</b>的事。
 * <p>本类只钉住<b>本域生产路径的当前事实</b>：<b>毫秒路径生效 ⇒ 驱动收到原样 R</b>
 * ⇒ ⇒ 配合恒等式 {@code t_exec + R ≡ deadline} ⇒ <b>Go ↔ RAG 的取消时刻差 = 0</b>
 * （Go 到 deadline 精确取消；RAG 现在也是把「剩余预算原样」交给驱动 ⇒ 同一个 deadline）。
 * <p>🔴 <b>历史</b>：本类最初断言的是「<b>秒级回退</b>」（驱动收到 2000 整秒）——
 * 那是 {@code global} 第四十一轮 E62 的结论在本域的落地形态（毫秒探测被 Hikari 代理类挡住）。
 * <b>{@code global} 第四十五轮把探测改成「先 unwrap 到驱动实现类」</b> ⇒ 毫秒路径真的生效
 * ⇒ ⇒ 本类在合并当天<b>按设计转红</b>（red = 台账该更新，不是回归）⇒ 本版已改成钉「毫秒路径生效」的新事实。
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
                    .withPassword("password")
                    .withCreateContainerCmdModifier(RealContainerHostPort.bindPg(RealContainerHostPort.FAVORITE_SQL_TIMEOUT_PRODUCTION_PATH));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("favorite.fact.dispatch.enabled", () -> "false");
        registry.add("favorite.fact.authority.enabled", () -> "false");
        registry.add("favorite.fact.legacy-preflight.enabled", () -> "false");
        // 🔴 gRPC server 端口走**本域专用段**（RealGrpcPort），**不用 `port=0`**：
        //   `port=0` 让 OS 从临时端口段（macOS 49152–65535）里挑 ⇒ 那是 Docker 随机发布
        //   容器端口用的同一段 ⇒ 别的会话的容器先占住 0.0.0.0:P ⇒ 我们再绑同一个端口
        //   ⇒ EADDRINUSE ⇒ **整个测试类启动失败**（表现为「偶尔红、换个时间又绿」的抖动）。
        registry.add("grpc.server.port", RealGrpcPort::pick);
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
    @DisplayName("🔴 生产路径：注入器看到的 statement 是 HikariCP 代理类 ⇒ 毫秒 API 在它自己类上不可达 ⇒ 共享层必须先 unwrap 到驱动实现类")
    void productionPathStatementIsPoolProxyWithoutMillisecondApi() {
        assertEquals("com.zaxxer.hikari.pool.HikariProxyPreparedStatement", probePath().getName(),
                "favorite-rpc 的生产数据源必须是 HikariCP（Spring Boot 默认 + 本域 application.yml 显式配了 spring.datasource.hikari）");

        assertThrows(NoSuchMethodException.class,
                () -> probePath().getMethod("setQueryTimeoutMs", long.class),
                "🔴 Hikari 代理类上不该有毫秒 API ⇒ 任何「按 statement.getClass() 找毫秒方法」的探测都必然失败"
                        + " ⇒ 这正是共享层必须**先 unwrap 到驱动实现类**的原因（global 第四十五轮已修）");
    }

    @Test
    @DisplayName("🔴 生产路径的有效超时是【毫秒路径的下取整】（代理层看到的秒数）")
    void productionPathEffectiveTimeoutIsMillisecondFloor() throws Exception {
        DeadlineWindow win = runWithDeadlineAndQuery();

        assertNotNull(PROBE.statementClass.get(), "探针必须抓到 statement");

        // 🔴 两条断言必须**分属两个测试方法**：同方法内第一条失败会**遮住**第二条
        //    ⇒ 届时只知道「红了」，不知道「红在哪一条、实际值多少」。
        long raw = PROBE.queryTimeoutMs.get();
        long seconds = PROBE.queryTimeoutSeconds.get();
        assertEquals(raw / 1000L, seconds,
                "毫秒路径 ⇒ 代理层秒数 = floor(驱动原始毫秒 / 1000)（**下取整**）；"
                        + "秒级路径则是 ceil(R/1000)（**上取整**）⇒ 实测 驱动原始毫秒=" + raw + " 秒=" + seconds);
    }

    @Test
    @DisplayName("🔴 生产路径的【驱动内部原始毫秒】= 原样 R（毫秒路径的指纹，非上取整）")
    void productionPathDriverRawMillisIsOriginalRemaining() throws Exception {
        DeadlineWindow win = runWithDeadlineAndQuery();

        assertNotNull(PROBE.statementClass.get(), "探针必须抓到 statement");

        // 这是**判别两条路径的量**：
        //   秒级路径写进去的是 ceil(R/1000)*1000（**上取整**，比 R 大）；
        //   毫秒路径写进去的是**原样 R**。
        // 🔴 **不硬编码 2000 / 1700** —— 改成**用「R 的取值窗口」判定**：
        //    R = deadline − 注入时刻，而注入时刻落在 [查询前, 查询后] ⇒
        //    ⇒ R ∈ [deadline−查询后, deadline−查询前] ⇒ 断言 raw 落在该窗口内
        //    ⇒ ⇒ **确定性**（不依赖时序抖动），且**秒级路径必然落不进这个窗口**
        //    （上取整值比 R 大 ⇒ 除非 R 恰好是 1000 的整数倍）。
        long raw = PROBE.queryTimeoutMs.get();
        long lo = win.rMinMillis();
        long hi = win.rMaxMillis();
        assertTrue(raw >= lo - 1L && raw <= hi + 1L,
                "🔴 毫秒路径 ⇒ 驱动收到的是【原样 R】（deadline − 注入时刻）⇒ 实测 " + raw
                        + " 必须落在 R 的窗口 [" + lo + ", " + hi + "] 内；"
                        + "秒级路径写进去的是 ceil(R/1000)*1000（**上取整**）⇒ 落不进这个窗口");
        assertTrue(raw < 2000L || raw % 1000L != 0L,
                "🔴 判别器：毫秒路径写进去的是【原样 R】⇒ R 不是 1000 的整数倍；"
                        + "秒级路径写进去的**必然是** 1000 的整数倍 ⇒ 实测 " + raw);
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
            // ⇒ ⇒ 生产路径上读到的那个值**不可能**是「读不出来 / 读错了」
            // ⇒ ⇒ 它要么是「秒级回退的上取整值」、要么是「毫秒路径的原样 R」⇒ 由另一条测试判定
            ms.invoke(ps, 1700L);
            Object raw = driverClass.getMethod("getQueryTimeoutMs").invoke(ps);
            assertNotNull(raw);
            assertEquals(1700L, ((Long) raw).longValue(),
                    "🔴 毫秒路径会把原值原样写进去 ⇒ 证明「驱动内部毫秒」这个读数本身是**准的** "
                            + "⇒ 生产路径上读到的是上取整值还是原样 R，由生产路径那条测试判定");
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

    /**
     * 绑一个 2000ms 的 deadline，先睡 300ms 让 R 落到 ≈1700ms，再跑一次真实读。
     *
     * @return {@link DeadlineWindow} —— 「R 的取值窗口」，用来把断言做成**确定性**的
     */
    private DeadlineWindow runWithDeadlineAndQuery() throws InterruptedException {
        PROBE.reset();
        long deadline = System.currentTimeMillis() + 2000L;
        long beforeQuery = System.currentTimeMillis();
        try (DeadlineState.Scope ignored = DeadlineState.bindToCurrent(deadline)) {
            Thread.sleep(300L);
            beforeQuery = System.currentTimeMillis();
            folderMapper.findByUserId(4242L);
        }
        long afterQuery = System.currentTimeMillis();
        return new DeadlineWindow(deadline, beforeQuery, afterQuery);
    }

    /**
     * 「R 的取值窗口」。
     *
     * <p>注入器算的是 {@code R = deadline − 注入时刻}；注入发生在 {@code prepare} 期间
     * ⇒ 注入时刻 ∈ [查询前, 查询后] ⇒ ⇒ {@code R ∈ [deadline−查询后, deadline−查询前]}。
     *
     * <p>⇒ 用它断言「驱动收到的是**原样 R**」时，<b>不依赖任何时序抖动</b>
     * （不用硬编码 1700 / 2000 这类会漂的数）。
     */
    record DeadlineWindow(long deadline, long beforeQueryMillis, long afterQueryMillis) {
        long rMinMillis() {
            return deadline - afterQueryMillis;
        }

        long rMaxMillis() {
            return deadline - beforeQueryMillis;
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
