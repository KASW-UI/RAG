package com.rag.task.task.rpc.config;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🔴 **第二十轮 · Redis 连接池维度的「活体对拍」探针（RAG 腿）** —— 测「池耗尽时等连接的最长等待」。
 *
 * <h2>🔴 这条探针在测什么（不要与已有的「配置断言」混淆）</h2>
 * <p>已有的 {@code TaskRedisCommandTimeoutGateTest} 断言 <b>⑥（池形态）⑦（max-wait = 4s）</b>
 * —— 那两条读的是 <b>Boot 装配出来的那份 pool 配置对象</b> ⇒ ⇒ 它们证明「<b>配置层</b>对」，
 * <b>不证明</b>「<b>运行期</b>真按这个上限在跑」。
 * <p>本类补的就是后半段：<b>真的把池占满，然后量「第 65 条命令/连接等了多久」</b>。
 *
 * <h2>🔴 三条断言（每条都对应一个独立问题）</h2>
 * <ol>
 *   <li><b>普通命令走不走池</b> —— Spring Data Redis 的 {@code LettuceConnectionFactory}
 *       默认 {@code shareNativeConnection = true}（{@code LettuceConnectionFactory.java:132}）
 *       ⇒ ⇒ 普通命令走 <b>一条共享的 native 连接</b>（Lettuce 在单 TCP 连接上多路复用命令）
 *       ⇒ ⇒ <b>池根本没参与</b> ⇒ ⇒ 「池耗尽 ⇒ 无限等」这个说法<b>只对「池真正被用的路径」成立</b>
 *       ⇒ ⇒ 判据 = <b>Redis 服务端的 {@code connected_clients}</b>（实测连接数，不是配置值）。</li>
 *   <li><b>pipeline 路径走不走池</b> —— {@code LettuceConnection.openPipeline()}
 *       ⇒ {@code getOrCreateDedicatedConnection()} ⇒ <b>从 pool 里借一条专属连接</b>
 *       ⇒ ⇒ 判据同上：{@code connected_clients} 必须随占位条数增长。</li>
 *   <li><b>池耗尽 ⇒ 等连接的上限</b> —— 占满 64 条（= 生产的 {@code max-active}）后，
 *       第 65 条 {@code openPipeline()} 必须<b>在 ~4s 内失败</b>（= 生产的 {@code max-wait: 4s}）
 *       ⇒ ⇒ <b>而不是无限等</b>（不配 {@code max-wait} 时 commons-pool2 落
 *       {@code DEFAULT_MAX_WAIT_MILLIS = -1} ⇒ 负值分支走 {@code takeFirst()} = 永久阻塞）。</li>
 * </ol>
 *
 * <h2>⚠️ 探针专用的一处配置放宽（必须写明，否则读数会被误读）</h2>
 * <p>{@code spring.data.redis.timeout} 在本探针里放宽到 <b>30s</b>。
 * <p><b>原因</b>：要占住一条连接，必须让命令活得比 <b>4s</b>（池等待上限）更久；
 * 而生产配置的命令超时是 <b>3s</b> ⇒ ⇒ 占用者会在 3s 被掐掉 ⇒ ⇒ <b>量到的将是「命令超时」而不是「池等待」</b>
 * ⇒ ⇒ 必须放宽<b>命令超时</b>才能把<b>池等待</b>单独量出来。
 * <p><b>不影响被测量</b>：被测的 {@code max-wait}（4s）与命令超时是<b>两个独立旋钮</b>
 * （前者 = 借连接的上限，后者 = 命令的执行上限）⇒ ⇒ <b>命令超时不参与「借连接等多久」</b>
 * ⇒ ⇒ 读数仍然就是池等待上限。
 *
 * <h2>🔴 为什么「失败」而不是「等成功」才是判据</h2>
 * <p>池满 ⇒ 第 65 条<b>必须失败</b>（有上限）⇒ ⇒ 若不配 {@code max-wait} ⇒ 它会<b>永久阻塞</b>
 * ⇒ ⇒ 本类把第 65 条放进独立线程并用 {@code future.get(8, SECONDS)} 兜底
 * ⇒ ⇒ 「配了 max-wait」⇒ 线程在 ~4s 内以异常结束 ⇒ ⇒ 「没配」⇒ 8s 还没结束 ⇒ ⇒
 * <b>断言会红在目标断言上、且测试不会挂死</b>（这是变异验证的前提）。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        classes = com.rag.task.task.rpc.TaskRpcApplication.class,
        properties = {
                // 🔴 专属上下文缓存键（properties 进 key、@DynamicPropertySource 不进 ⇒ 必须给唯一值）
                "rag.test.gate.context-key=task-redis-pool-exhaustion-p99",

                // 🔴 **必须**（2026-10-27 第二十轮实测踩到，by like 域那条探针暴露）：
                //    gRPC server 走**固定端口 9005**（task-rpc 的 application.yml:203）
                //    ⇒ ⇒ 若该端口被占 ⇒ 上下文**起不来** ⇒ 整类 3 条全红、且**不是**被测行为红。
                //    ⇒ 故必须置 0（= 随机空闲端口），与本仓其余 @SpringBootTest 同款。
                //    （本类首跑时**漏了**这一行、只是 9005 恰好空闲才侥幸通过 ⇒ 属**自身缺陷**。）
                "grpc.server.port=0",
                "management.otlp.tracing.export.enabled=false",
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "logging.level.com.alibaba.nacos=WARN",
                "spring.kafka.bootstrap-servers=127.0.0.1:1",

                // ⚠️ 与 DB 无关 ⇒ 排除 Flyway（否则要真 PG）
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",

                // ⚠️ 探针专用：命令超时放宽到 30s（见类 javadoc「探针专用的一处配置放宽」）
                "spring.data.redis.timeout=30s"
        })
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("task-rpc：Redis 连接池维度活体对拍（普通命令绕池 / pipeline 走池 / 池耗尽等连接的上限）")
class TaskRedisPoolExhaustionP99GateTest {

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Autowired
    LettuceConnectionFactory connectionFactory;

    @Autowired
    StringRedisTemplate redisTemplate;

    /**
     * 🔴 把容器的随机宿主端口注入 Redis 连接配置。
     *
     * <p>⚠️ Spring 的 context cache key <b>不含 {@code @DynamicPropertySource} 的值</b>
     * ⇒ ⇒ 上面 {@code properties} 里的 {@code rag.test.gate.context-key} 是<b>必须的</b>
     * ⇒ ⇒ 否则本类可能与同模块另一个 {@code @SpringBootTest} 共享上下文 ⇒ ⇒ 读到别人的 factory。
     */
    @org.springframework.test.context.DynamicPropertySource
    static void redisProperties(
            org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    /** 占住池的连接（pipeline 路径）。 */
    private final List<RedisConnection> held = new ArrayList<>();

    /**
     * 🔴 **每条断言后必须释放占位**（2026-10-27 第二十轮 like 腿实测踩到）：
     * 断言②若失败而不释放 ⇒ 池被占满 ⇒ 断言③的<b>准备阶段</b>（借连接）就会 4s 超时
     * ⇒ ⇒ 报错落在③的<b>准备代码</b>上、不是被测行为 ⇒ ⇒ 「红在别处」⇒ ⇒ 变异验证失效。
     * ⇒ 故释放动作必须放在 {@code @AfterEach}（**断言失败也会跑**）。
     */
    @org.junit.jupiter.api.AfterEach
    void releaseHeldConnections() {
        for (RedisConnection c : held) {
            try {
                c.closePipeline();
            } catch (RuntimeException ignored) {
                // 关掉即可
            }
            try {
                c.close();
            } catch (RuntimeException ignored) {
                // 关掉即可
            }
        }
        held.clear();
    }

    @AfterAll
    static void stopContainer() {
        if (REDIS != null) {
            REDIS.stop();
        }
    }

    /** 池的 maxTotal（读**真装配**，不读 yml）。 */
    private int poolMaxTotal() {
        return ((org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration)
                connectionFactory.getClientConfiguration()).getPoolConfig().getMaxTotal();
    }

    /**
     * 要占住多少条才能让【下一条】必然等池。
     *
     * <p>🔴 **为什么是 {@code maxTotal - 1} 而不是 {@code maxTotal}**：
     * Spring Data Redis 的<b>共享 native 连接本身也是从池里借的</b>
     * （{@code LettuceConnectionFactory.getOrCreateSharedConnection()} ⇒
     * {@code new SharedConnection<>(this.connectionProvider)}）
     * ⇒ ⇒ 它<b>已经占掉 1 个槽</b> ⇒ ⇒ 实测占满 {@code maxTotal} 条时，
     * <b>最后一条就已经在等池并 4s 超时了</b> ⇒ ⇒ 故占 {@code maxTotal - 1} 条，
     * 留 1 个空槽给「下一条」去撞那个上限。
     */
    private int holdCount() {
        return poolMaxTotal() - 1;
    }

    /** Redis 服务端当前的连接数（= 池是否真被用上的判据）。 */
    private int connectedClients() {
        Properties info = redisTemplate.getConnectionFactory().getConnection().serverCommands().info("clients");
        return Integer.parseInt(String.valueOf(info.get("connected_clients")));
    }

    private static String chain(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable c = t; c != null; c = c.getCause()) {
            sb.append(c.getClass().getName()).append(": ").append(c.getMessage()).append(" | ");
        }
        return sb.toString();
    }

    @Test
    @Order(1)
    @DisplayName("① 普通命令走【共享 native 连接】⇒ 池不参与 ⇒ 「池耗尽无限等」不适用于普通命令路径")
    void regularCommandsDoNotBorrowFromThePool() {
        int before = connectedClients();
        for (int i = 0; i < 50; i++) {
            redisTemplate.opsForValue().set("g2-pool-probe:regular:" + i, "v");
        }
        int after = connectedClients();

        System.out.printf("PROBE regular connected_clients before=%d after=%d delta=%d%n", before, after, after - before);

        assertThat(after - before)
                .as("🔴 普通命令必须复用【同一条共享连接】⇒ 服务端连接数几乎不增长"
                        + "（实测 Spring Data Redis `LettuceConnectionFactory.shareNativeConnection` 默认 = true"
                        + " ⇒ `SharedConnection.getConnection()` 只持一条）"
                        + " ⇒ ⇒ 池不参与普通命令 ⇒ 「池耗尽 ⇒ 无限等」只对【池真正被用的路径】成立")
                .isLessThanOrEqualTo(2);
    }

    @Test
    @Order(2)
    @DisplayName("② pipeline 路径【从池借专属连接】⇒ 占满 64 条 ⇒ 服务端连接数必须随之增长")
    void pipelinePathBorrowsFromThePool() {
        int before = connectedClients();
        int hold = holdCount();
        for (int i = 0; i < hold; i++) {
            RedisConnection conn = connectionFactory.getConnection();
            conn.openPipeline(); // 🔴 ⇒ getOrCreateDedicatedConnection() ⇒ 从 pool 借一条
            held.add(conn);
        }
        int after = connectedClients();
        System.out.printf("PROBE pipeline hold=%d (maxTotal=%d)%n", hold, poolMaxTotal());

        System.out.printf("PROBE pipeline connected_clients before=%d after=%d delta=%d%n", before, after, after - before);

        assertThat(before)
                .as("🔴 前置：借之前的服务端连接数必须 < 占位条数（否则本断言可能**空过**）"
                        + " ⇒ 实测 before=%d / hold=%d", before, hold)
                .isLessThan(hold);

        assertThat(after)
                .as("🔴 %d 条 pipeline 必须各借一条【池里的】连接 ⇒ 服务端连接数必须长到池的上限"
                        + " ⇒ ⇒ 这是「池真的在 pipeline 路径上被使用」的活体证据"
                        + "（⚠️ **不能**断言「净增量 >= 占位数」：池里**已存在**的空闲连接会被复用"
                        + " ⇒ 净增量会小于占位数）", hold)
                .isGreaterThanOrEqualTo(hold);
    }

    @Test
    @Order(3)
    @DisplayName("③ 池耗尽 ⇒ 第 65 条 pipeline 的等连接时间必须 ≈4s（= max-wait）⇒ 不是无限等")
    void poolExhaustionWaitIsBoundedByMaxWait() {
        // 占到只剩 1 个空位（共享连接占 1 ⇒ 再借 1 条就满）
        int hold = holdCount();
        for (int i = 0; i < hold; i++) {
            RedisConnection conn = connectionFactory.getConnection();
            conn.openPipeline();
            held.add(conn);
        }
        System.out.printf("PROBE exhausted hold=%d (maxTotal=%d)%n", hold, poolMaxTotal());

        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "g2-pool-probe-waiter");
            t.setDaemon(true);
            return t;
        });

        long t0 = System.nanoTime();
        Future<Throwable> future = executor.submit(() -> {
            try {
                RedisConnection conn = connectionFactory.getConnection();
                conn.openPipeline();
                return null; // 😱 不该拿到连接
            } catch (Throwable t) {
                return t;
            }
        });

        Throwable thrown = null;
        boolean timedOut = false;
        try {
            thrown = future.get(8, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            timedOut = true;
        } catch (Exception e) {
            thrown = e;
        }
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;

        System.out.printf("PROBE exhausted wait_elapsed_ms=%d timedOut=%s thrown=%s%n",
                elapsedMs, timedOut, thrown == null ? "null" : chain(thrown));

        // 释放占位
        for (RedisConnection c : held) {
            try {
                c.closePipeline();
            } catch (RuntimeException ignored) {
                // 关掉即可
            }
            c.close();
        }
        held.clear();
        executor.shutdownNow();

        assertThat(timedOut)
                .as("🔴 池耗尽时第 65 条必须【在 8s 内失败】（生产的 max-wait = 4s）"
                        + " ⇒ 若这里超时 ⇒ 说明【没配 max-wait ⇒ commons-pool2 走负值分支 takeFirst() = 永久阻塞】"
                        + " ⇒ ⇒ 这就是「无限等」那个漂移的活体形态")
                .isFalse();

        assertThat(thrown)
                .as("🔴 第 65 条必须以异常结束（拿不到连接），不能悄悄成功")
                .isNotNull();

        assertThat(elapsedMs)
                .as("🔴 等连接的最长等待必须 ≈4s（生产的 max-wait: 4s）"
                        + " ⇒ 实测 %dms ⇒ ⇒ 这是「池等待上限」的活体读数"
                        + "（不是配置值 ⇒ 读的是真行为）", elapsedMs)
                .isBetween(3_000L, 8_000L);
    }
}
