package com.rag.article.article.rpc;

import com.rag.article.article.rpc.support.RealContainerHostPort;
import com.rag.article.article.rpc.support.RealGrpcPort;
import net.devh.boot.grpc.server.event.GrpcServerStartedEvent;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 启动期门禁的**时机** —— 对应 Go {@code article.go:41} 与 {@code :44} 的先后关系。
 *
 * <h2>Go 出处（逐行，消费点）</h2>
 * <pre>
 * article.go:41   u := model.NewArticleRepo(c)        ← InitDB 里校验 article_revision_immutable
 *                                                    ← 失败 ⇒ panic（db.go:61-71）
 * article.go:44   s := zrpc.MustNewServer(...)         ← gRPC server 在这之后才被创建
 * article.go:57   serviceGroup.Add(kq.MustNewQueue(...))  ← Kafka consumer 同理
 * article.go:78   serviceGroup.Start()                ← 真正开始收流量
 * </pre>
 * ⇒ Go：**门禁在 server 被创建之前** ⇒ 缺触发器时进程**从不收流量**。
 *
 * <h2>为什么这道闸必须 fail-fast</h2>
 * {@code article_revision_immutable} 触发器是「已发布正文不可变」这条保证的<b>唯一实现</b>。
 * 缺它 ⇒ 进程照常启动、审核照常发布，但任何一次误 UPDATE 都能改写已发布正文，
 * <b>且不会有任何报错</b>（Go 侧会 panic）。
 * <p>⇒ 关键不只是"启动要失败"，而是"<b>失败之前不许对外服务</b>"。
 * 这正是 {@code DRIFT-ARTICLE-RPC-005} 记的那条 OBSERVABLE DIFFERENCE：
 * RAG 原先在 {@code ApplicationReadyEvent} 才校验，而那时 gRPC server 早已绑定端口。
 *
 * <h2>本类怎么"看见"服务器有没有启动过</h2>
 * devh 的 {@link net.devh.boot.grpc.server.serverfactory.GrpcServerLifecycle}
 * 是 {@code SmartLifecycle}，{@code start()} 里 <b>{@code localServer.start()} 绑定端口之后</b>
 * 才 {@code publishEvent(new GrpcServerStartedEvent(...))}。
 * <br>⇒ <b>收到该事件 == 端口已经绑过、已经对外服务过</b>。
 * <br>⚠️ 这比"事后看端口通不通"可靠：失败启动后端口最终也会关闭，
 * 事后检查<b>分不出</b>「从没启动」与「启动过又关了」。
 *
 * <h2>为什么要有一条<b>对照</b>用例</h2>
 * 负向用例（不该收到事件 ⇒ 断言事件没收到）如果监听器本身坏了，
 * 就会<b>假绿</b>。⇒ 正向用例（schema 完好 ⇒ 必须收到事件）证明监听器是活的。
 */
@Testcontainers
@org.testcontainers.junit.jupiter.EnabledIfDockerAvailable
class ArticleSchemaGateStartupOrderTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("article_rpc_gate_it")
                    .withUsername("rag")
                    .withPassword("rag")
                    .withCreateContainerCmdModifier(RealContainerHostPort.bindPg(RealContainerHostPort.SCHEMA_GATE_STARTUP_ORDER));

    @BeforeAll
    static void migrateOnce() {
        // 用真 Flyway 跑一遍仓里的迁移脚本（V8/V9/V10）⇒ 表结构与生产完全一致。
        // ⚠️ 这里刻意**不**用 spring.flyway.enabled=true 走应用的迁移，
        //    因为本类要控制"迁移跑过、但触发器缺失"这个状态。
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas("public")
                .baselineOnMigrate(true)
                .load()
                .migrate();
    }

    @BeforeEach
    void ensureTriggerExists() {
        // 每条用例都从"触发器在"出发；负向用例自己再删掉它。
        // ⇒ 用例之间无顺序依赖（JUnit 不保证执行顺序）。
        recreateTrigger();
    }

    // ==================================================================
    // 负向：缺不可变触发器 ⇒ 进程必须失败，且**从未**对外服务
    // ==================================================================

    @Test
    @DisplayName("🔴 Go: 缺 article_revision_immutable ⇒ 启动失败，且 gRPC server **从未**绑定过端口")
    void missingImmutabilityTriggerFailsBeforeGrpcServerEverStarts() {
        dropTrigger();

        AtomicBoolean grpcServerStarted = new AtomicBoolean(false);
        Throwable failure = bootAndCaptureFailure(grpcServerStarted);

        assertNotNull(failure, "缺不可变触发器时进程必须启动失败（Go 是 panic）");
        assertTrue(messageChainContains(failure, "article_revision_immutable"),
                "失败原因必须指向不可变触发器缺失，实际为：" + rootMessage(failure));
        assertFalse(grpcServerStarted.get(),
                "❌ gRPC server 已经启动过（端口已绑定）⇒ 存在「已在收流量、但不可变保证缺失」的窗口，"
                        + "与 Go article.go:41（门禁在 :44 建 server 之前）不一致");
    }

    // ==================================================================
    // 正向对照：schema 完好 ⇒ 必须真的起来（证明上面的监听器是活的）
    // ==================================================================

    @Test
    @DisplayName("对照：schema 完好 ⇒ 启动成功且 gRPC server **确实**启动过（证明监听器不是死的）")
    void healthySchemaStartsTheGrpcServer() {
        AtomicBoolean grpcServerStarted = new AtomicBoolean(false);
        Throwable failure = bootAndCaptureFailure(grpcServerStarted);

        assertNull(failure, () -> "schema 完好时不该启动失败，实际失败：" + rootMessage(failure));
        assertTrue(grpcServerStarted.get(),
                "❌ 没收到 GrpcServerStartedEvent —— 说明本类的监听器本身失效，"
                        + "负向用例会因此**假绿**（断言一个永远不发生的事件）");
    }

    // ==================================================================
    // 工具
    // ==================================================================

    /**
     * 起一次真实的进程（真 Spring 上下文 + 真 gRPC server + 真 PG），把失败异常抓回来。
     *
     * <p>⚠️ 刻意<b>不用</b> {@code @SpringBootTest}：它要求上下文<b>起得来</b>，
     * 而本类要验证的正是"起不来"这条路径。
     */
    private Throwable bootAndCaptureFailure(AtomicBoolean grpcServerStarted) {
        SpringApplication app = new SpringApplication(ArticleRpcApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setBannerMode(Banner.Mode.OFF);
        app.setRegisterShutdownHook(false);
        // ⭐ 观测点：devh 在 localServer.start()（端口已绑定）之后才发这个事件。
        app.addListeners((ApplicationListener<GrpcServerStartedEvent>)
                event -> grpcServerStarted.set(true));
        ConfigurableApplicationContext ctx = null;
        try {
            ctx = app.run(
                    "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                    "--spring.datasource.username=" + POSTGRES.getUsername(),
                    "--spring.datasource.password=" + POSTGRES.getPassword(),
                    // ⚠️ 关掉 Flyway：schema 已由 @BeforeAll 用真 Flyway 建好，
                    //    这里要控制的是"迁移跑过、触发器被删"这个状态。
                    "--spring.flyway.enabled=false",
                    // 🔴 2026-10-28（G3）：走**本域专用段**（22400–22479）
                    //    ⇒ 不用 `0`（0 ⇒ OS 临时端口段 ⇒ 与 Docker 发布口同段 ⇒ 抖动）。
                    "--grpc.server.port=" + RealGrpcPort.pick(),
                    "--spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                    "--management.otlp.tracing.export.enabled=false",
                    "--logging.level.com.alibaba.nacos=WARN",
                    "--grpc.client.security-rpc.address=static://127.0.0.1:1",
                    "--grpc.client.message-rpc.address=static://127.0.0.1:1",
                    "--spring.kafka.bootstrap-servers=127.0.0.1:1",
                    // ⚠️ 四个通道的 brokers 键没有消费点（客户端是全局单例）⇒ 必须与全局同值，
                    //    否则 ArticleKafkaChannelValidator 会**先于** schema 门禁失败，
                    //    负向用例将拿不到「article_revision_immutable」这个真正的失败原因。
                    "--article-rpc.kq-pusher-conf.brokers=127.0.0.1:1",
                    "--article-rpc.kq-consumer-conf.brokers=127.0.0.1:1",
                    "--article-rpc.article-sync-pusher-conf.brokers=127.0.0.1:1",
                    "--article-rpc.article-sync-result-consumer-conf.brokers=127.0.0.1:1");
            return null;
        } catch (Throwable t) {
            return t;
        } finally {
            // ⚠️ **必须关掉上下文**：健康 schema 那条会真的把 gRPC server 与 Kafka listener 起起来，
            //    它们的线程都是非守护线程 ⇒ 不关的话 surefire 的 fork JVM 永远不退出（实测挂死）。
            //    关闭发生在观测点之后，不影响上面两个断言。
            if (ctx != null) {
                ctx.close();
            }
        }
    }

    private void dropTrigger() {
        execute("DROP TRIGGER IF EXISTS article_revision_immutable ON article_revision");
    }

    private void recreateTrigger() {
        execute("""
                CREATE OR REPLACE FUNCTION reject_article_revision_mutation() RETURNS trigger
                LANGUAGE plpgsql AS $$
                BEGIN
                    RAISE EXCEPTION 'article revision is immutable';
                END;
                $$;
                DROP TRIGGER IF EXISTS article_revision_immutable ON article_revision;
                CREATE TRIGGER article_revision_immutable
                    BEFORE UPDATE OR DELETE ON article_revision
                    FOR EACH ROW EXECUTE FUNCTION reject_article_revision_mutation();
                """);
    }

    private void execute(String sql) {
        try (var conn = POSTGRES.createConnection("");
             var stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (Exception e) {
            throw new IllegalStateException("failed to prepare the test schema: " + e.getMessage(), e);
        }
    }

    /** 沿异常链找关键字（Spring 会把异常包好几层）。 */
    private static boolean messageChainContains(Throwable t, String needle) {
        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            String msg = cur.getMessage();
            if (msg != null && msg.contains(needle)) {
                return true;
            }
            if (cur.getCause() == cur) {
                break;
            }
        }
        return false;
    }

    private static String rootMessage(Throwable t) {
        // ⚠️ t 可能是 null（"本来就不该失败"那条用例的失败原因就是"没有失败"）
        if (t == null) {
            return "<没有异常>";
        }
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getClass().getSimpleName() + ": " + cur.getMessage();
    }
}
