package com.rag.favorite.favorite.rpc.fact;

import com.rag.favorite.favorite.rpc.service.FavoriteAuthorityLookupService;
import com.rag.favorite.favorite.rpc.service.FavoriteFactEnvelopeFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 U4 / DEV-FAV-RPC-005 的守门 —— Go 的<b>单次 lookup 3s 查询预算</b>是否真的接到了 DB 查询。
 *
 * <h2>Go 出处</h2>
 * <pre>
 * // service/favorite/rpc/internal/authority/http.go:44 / :80
 * ctx, cancel := context.WithTimeout(r.Context(), 3*time.Second)
 * defer cancel()
 * fact, err := reader.AuthoritativeFavoriteFact(ctx, r.PathValue("producer"), r.PathValue("event_id"))
 * if errors.Is(err, model.ErrFavoriteFactUnavailable) { http.NotFound(w, r); return }
 * if err != nil { http.Error(w, "source unavailable", http.StatusServiceUnavailable); return }
 * </pre>
 * <p>⇒ Go 的 3s 约束的是 <b>DB 查询</b>（{@code m.conn.WithContext(ctx)} 把 ctx 带进 GORM）：
 * ctx 到点 ⇒ pg 驱动发 CancelRequest ⇒ 查询在服务端立刻停止 ⇒
 * {@code query.Error != nil} ⇒ <b>原样上抛</b>（favorite_authority.go:215-217
 * <b>不</b>包成 {@code ErrFavoriteFactUnavailable}）⇒ ⇒ <b>503 "source unavailable"</b>。
 *
 * <h2>为什么修复前测不出来</h2>
 * <p>修复前 Java 侧<b>没有</b>把 deadline 接到查询上 ⇒ 慢查询一路顶到
 * Hikari 的 {@code connection-timeout}（<b>连接</b>超时，量纲不同）。
 * ⚠️ 而「查不到」与「查询超时」<b>功能分支都是「出错 ⇒ 503」</b> ——
 * 只断言状态码<b>区分不出来</b> ⇒ 必须同时断言<b>耗时</b>。
 *
 * <h2>本类怎么造出「慢查询」</h2>
 * <p>用另一个连接对 {@code favorite_fact_outbox} 加 <b>ACCESS EXCLUSIVE</b> 锁
 * ⇒ mapper 的 {@code SELECT … WHERE event_id = ?}（与 ACCESS SHARE 冲突）<b>被阻塞</b>
 * ⇒ 到点被 CancelRequest 掐断。
 * <p>⚠️ 这比「{@code pg_sleep(30)}」更真实：它复刻的是「行被锁住」这种生产上真会发生的等待，
 * 而且**不依赖任何测试专用 SQL**（走的是<b>生产 mapper 的真实语句</b>）。
 *
 * <h2>为什么不用真 Tomcat</h2>
 * <p>被测的是「deadline 有没有传到 SQL」，与 HTTP 传输无关 ⇒ 直接调 servlet 的
 * {@code doGet}（同一包下的包私有静态类）即可，省掉一个 web 容器依赖。
 * ⚠️ wire 形态（path 参数 / 401 / 404 / 503 的头部与 body）另有真 Tomcat 的覆盖。
 */
@Testcontainers
@org.testcontainers.junit.jupiter.EnabledIfDockerAvailable
@SpringBootTest(
        classes = com.rag.favorite.favorite.rpc.FavoriteRpcApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "grpc.server.port=0",
                // 绕过 Nacos：devh 的 @GrpcClient 在 Bean 初始化期就建 channel（不是懒建）。
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "grpc.client.user-rpc.address=static://127.0.0.1:1",
                "grpc.client.article-rpc.address=static://127.0.0.1:1",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
class FavoriteAuthorityLookupTimeoutTest {

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
        // ⚠️ 安全网：给每条连接设 10s 的 PG statement_timeout。
        //   它**不参与**本用例的判定（3s 的预算必须先到），只在「实现被改坏、
        //   deadline 没接上」时把查询从「永久阻塞」变成「10s 后 57014」，
        //   ⇒ 变异运行会在 ~10s 后拿到 503，但 elapsed 断言会红 ⇒ 不会把测试挂死。
        registry.add("spring.datasource.hikari.connection-init-sql",
                () -> "SET statement_timeout = '10s'");
    }

    /** Go {@code http.go:28-30} 要求 token ≥ 32 字节。 */
    private static final String TOKEN = "favorite-authority-test-token-0123456789";

    private static final long MIN_BUDGET_MILLIS = 2_500L;
    private static final long MAX_BUDGET_MILLIS = 8_000L;

    @Autowired
    private FavoriteAuthorityLookupService lookupService;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private FavoriteAuthorityEndpointConfig.FavoriteAuthorityServlet servlet() {
        return new FavoriteAuthorityEndpointConfig.FavoriteAuthorityServlet(
                TOKEN.getBytes(StandardCharsets.UTF_8), lookupService, false);
    }

    private MockHttpServletRequest request(String eventId) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI("/internal/v1/favorite/facts/"
                + FavoriteFactEnvelopeFactory.PRODUCER + "/" + eventId);
        req.addHeader("Authorization", "Bearer " + TOKEN);
        return req;
    }

    @Test
    @Timeout(120)
    @DisplayName("🔴 U4：查询被锁住 ⇒ 3s 预算到期 ⇒ 503 \"source unavailable\"（不是 404、也不是无限等）")
    void lockedQueryHitsTheGoLookupBudgetAndReturns503() throws Exception {
        // 🔴 关键：锁必须由**另一个连接**持有，且在事务里（不提交 ⇒ 锁不释放）。
        Connection lockConnection = dataSource.getConnection();
        lockConnection.setAutoCommit(false);
        try (Statement lockStatement = lockConnection.createStatement()) {
            lockStatement.execute("LOCK TABLE favorite_fact_outbox IN ACCESS EXCLUSIVE MODE");
        }

        try {
            MockHttpServletRequest req = request("favorite.9001.v1");
            MockHttpServletResponse resp = new MockHttpServletResponse();

            long startedAt = System.nanoTime();
            servlet().doGet(req, resp);
            long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

            // Go: http.go:51-56 —— 其它 error（含 ctx deadline）⇒ 503 + body "source unavailable\n"
            // ⚠️ 末尾的 "\n" 是 Go 的 http.Error 用 fmt.Fprintln 带来的（第四轮统一到 writeGoError）。
            assertEquals(503, resp.getStatus(),
                    "查询超时必须落 503（Go 的 DB 错误原样上抛 ⇒ 不在 ErrFavoriteFactUnavailable 分支 ⇒ 不是 404）；"
                            + "body=[" + resp.getContentAsString() + "] elapsed=" + elapsedMillis + "ms");
            assertEquals("source unavailable\n", resp.getContentAsString());

            // ⚠️ 这两条**一起**才是证据：单看状态码区分不出「3s 预算到期」与「其它错误」。
            assertTrue(elapsedMillis >= MIN_BUDGET_MILLIS,
                    "必须在 3s 预算附近才返回（说明是被 deadline 掐断的，不是立刻失败）；实际 "
                            + elapsedMillis + "ms");
            assertTrue(elapsedMillis <= MAX_BUDGET_MILLIS,
                    "必须在 3s 预算附近返回（说明不是落到了 PG 的 10s statement_timeout 或连接超时）；实际 "
                            + elapsedMillis + "ms");
        } finally {
            // 回滚 ⇒ 释放 ACCESS EXCLUSIVE 锁（否则后续用例全被阻塞）。
            lockConnection.rollback();
            lockConnection.close();
        }
    }

    @Test
    @Timeout(60)
    @DisplayName("🔴 对照组：无锁 + 查不到 ⇒ 404（查不到 ≠ 超时，两条分支不能混）")
    void missingEventIs404AndFast() throws Exception {
        MockHttpServletRequest req = request("favorite.999999.v1");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        long startedAt = System.nanoTime();
        servlet().doGet(req, resp);
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

        // Go: http.go:47-50 —— ErrFavoriteFactUnavailable ⇒ 404
        assertEquals(404, resp.getStatus(),
                "查不到必须落 404（Go 的 ErrFavoriteFactUnavailable 分支）；body=["
                        + resp.getContentAsString() + "]");
        assertTrue(elapsedMillis < 2_000L,
                "查不到必须**立刻**返回（不应等满 3s 预算）；实际 " + elapsedMillis + "ms");
    }

    @Test
    @Timeout(60)
    @DisplayName("🔴 对照组：查询能立刻返回时，elapsed 必须很小（证明 3s 不是「每次都等 3s」）")
    void fastPathDoesNotWaitForTheBudget() throws Exception {
        // 造一条**合法**的 v1 断言行：只让「取行」这一步成功（17 道校验随后会失败 ⇒ 404），
        // 但足以证明「SQL 不慢 ⇒ 不触发超时」。
        jdbcTemplate.update("""
                INSERT INTO favorite_fact_outbox
                    (event_id, favorite_id, aggregate_version, payload, status, retry_count,
                     created_at, updated_at)
                VALUES ('favorite.9002.v1', 9002, 1, '{}'::jsonb, 1, 0,
                        '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')
                """);
        try {
            MockHttpServletRequest req = request("favorite.9002.v1");
            MockHttpServletResponse resp = new MockHttpServletResponse();

            long startedAt = System.nanoTime();
            servlet().doGet(req, resp);
            long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

            // 行存在但 17 道校验不过 ⇒ 不可用 ⇒ 404（Go: favorite_authority.go:220-223）
            assertEquals(404, resp.getStatus(),
                    "行存在但校验不过 ⇒ 404；body=[" + resp.getContentAsString() + "]");
            assertTrue(elapsedMillis < 2_000L,
                    "快路径不应等满 3s 预算；实际 " + elapsedMillis + "ms");
        } finally {
            // TRUNCATE（不是 DELETE）：与 FavoriteSchemaParityTest 同款清库姿势。
            jdbcTemplate.execute("TRUNCATE TABLE favorite_fact_outbox CASCADE");
        }
    }
}
