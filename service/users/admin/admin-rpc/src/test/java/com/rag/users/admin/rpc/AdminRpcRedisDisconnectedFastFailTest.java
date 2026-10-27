package com.rag.users.admin.rpc;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisAsyncCommandsImpl;
import io.lettuce.core.api.StatefulConnection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 admin-rpc 的 <b>Redis 断连行为</b>必须与 Go 真源同形：<b>快速失败</b> ⇒ 客户端拿到
 * Go 的 {@code INTERNAL「注销失败,Redis写入异常」}，而不是被拖到 RPC 超时。
 *
 * <h2>这条守的是哪条漂移（DRIFT-ARPC-013）</h2>
 *
 * <p>rpc 侧与 api 侧<b>机制同源、后果不同</b>：
 * <pre>
 *   api 侧（读黑名单、fail-open 放行）：Redis 断连 ⇒ 命令排队到 commandTimeout(3s)
 *     ⇒ 撞 HTTP 3s 预算 ⇒ 503（Go 是 200 fail-open）
 *
 *   rpc 侧（写黑名单）：Redis 断连 ⇒ 命令排队到 commandTimeout(3s)
 *     ⇒ 而本组件 rpc-timeout: 2s ⇒ **RPC 超时先到** ⇒ 客户端看到 DEADLINE_EXCEEDED（1015「服务繁忙」）
 *     ⇒ 而 Go 是**快速失败** ⇒ 进业务层 ⇒ INTERNAL「注销失败,Redis写入异常」
 *     ⇒ **两端看到的不是同一个形状** ⇒ 可观察漂移。
 * </pre>
 *
 * <h2>Go 侧的形状（先读真源）</h2>
 * <pre>
 *   Go service/user/admin/rpc/internal/logic/logoutlogic.go:35-40
 *     err := l.svcCtx.BizRedis.SetexCtx(l.ctx, blackListKey, "banned", int(expireTime))
 *     if err != nil {
 *         logger.LogBusinessErr(l.ctx, errmsg.ErrorRedisUpdate, err)
 *         return nil, status.Error(codes.Internal, "注销失败,Redis写入异常")
 *     }
 *
 *   为什么快：go-zero v1.10.2 core/stores/redis/redisclientmanager.go
 *     · :14  maxRetries = 3
 *     · :32-39 red.NewClient(&red.Options{Addr, MaxRetries: maxRetries, MinIdleConns, ...})
 *       —— **不设** DialTimeout / ReadTimeout
 *     ⇒ 连接被拒/被关时每次重试都**立即失败** ⇒ 整体快速失败（实测 ~1.7s，仍在 2s 预算内）
 * </pre>
 *
 * <p>RAG 侧的错误面<b>本来就与 Go 一致</b>（{@code AdminDomainService.logout:266-275} 的
 * {@code catch (Exception e)} ⇒ {@code Status.INTERNAL.withDescription("注销失败,Redis写入异常")}）
 * ⇒ 漂移<b>只在失败耗时</b> ⇒ 本类守的就是「耗时」与「异常分支」。
 *
 * <h2>⚠️⚠️ 故障注入方式是这个测试的<b>全部难点</b></h2>
 *
 * <p>用 {@code container.stop()} 做故障注入<b>测不出</b>这条漂移 ——
 * {@code stop()} 让容器被<b>强杀</b> ⇒ 连接收到的是 <b>RST</b>
 * ⇒ Lettuce 往 socket 写命令时<b>立刻失败</b> ⇒ <b>根本走不到</b>
 * {@code disconnectedBehavior} 那个分支 ⇒ 两种配置的耗时都是 1~13ms ⇒ <b>分不出</b>。
 * <br>⇒ 而线上真实场景是 {@code docker stop} ⇒ Redis <b>优雅退出</b> ⇒ 连接收到的是 <b>FIN</b>
 * ⇒ Lettuce 进入 <b>DISCONNECTED（正在重连）</b> 状态 ⇒ 命令被<b>排队</b>
 * ⇒ 这才是 {@code DEFAULT} 会挂到 commandTimeout 的那条路径。
 * <br>⇒ 本类用一个<b>可控 TCP 透传代理</b>把 app 挡在中间：
 * 故障时<b>先关监听口</b>（⇒ 重连被拒）+ <b>再对已建立的连接半关闭</b>（⇒ 发 FIN）
 * ⇒ 精确复刻 live 的断连形态。
 *
 * <h2>断言为什么是「异常类型 + 耗时」两条</h2>
 * <pre>
 *   DEFAULT（未修）      ⇒ RedisCommandTimeoutException: Command timed out after 3 second(s)  耗时 ≈3000ms
 *   REJECT_COMMANDS（修后）⇒ RedisException: Currently not connected. Commands are rejected.  耗时 ≈1ms
 * </pre>
 * ⇒ <b>两条都断言</b>：耗时 &lt; 1500ms <b>且</b> 异常不是 {@code RedisCommandTimeoutException}。
 *
 * <h2>本类的可证伪性</h2>
 * <p>把 {@code RedisDisconnectedBehaviorConfig} 里的 {@code REJECT_COMMANDS} 改回 {@code DEFAULT}，
 * 用例 2 应当<b>红</b>，且失败消息里的实测耗时应是 <b>≈3000ms</b>（挂到 commandTimeout）。
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
        classes = AdminRpcRedisDisconnectedFastFailTest.RedisOnlyApp.class,
        properties = {
                // 拒连最快；Nacos 连不上只会打日志，不影响本测试
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1"
        })
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdminRpcRedisDisconnectedFastFailTest {

    /**
     * 断连后命令必须<b>在 1500ms 内</b>失败。
     *
     * <p>阈值怎么定的：未修时的实测是 <b>≈3000ms</b>（挂到 {@code commandTimeout: 3s}），
     * 修后是 <b>≈1ms</b>。
     */
    private static final long FAST_FAIL_BUDGET_MS = 1500L;

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    /** app 与 Redis 之间的透传代理（上游地址延迟解析 ⇒ 容器起来前就能建好）。 */
    static final GracefulDisconnectProxy PROXY = GracefulDisconnectProxy.create(() -> {
        try {
            return REDIS.getHost() + ":" + REDIS.getMappedPort(6379);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Redis 容器还没起来", e);
        }
    });

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry r) {
        r.add("spring.data.redis.host", () -> "127.0.0.1");
        r.add("spring.data.redis.port", PROXY::port);
        r.add("spring.data.redis.password", () -> "");
    }

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private LettuceConnectionFactory connectionFactory;

    /**
     * 只导入 Redis 自动配置的最小上下文（<b>不</b>用整个应用：admin-rpc 的完整上下文要 PG / Nacos，
     * 而本测试跟它们毫无关系）＋ 显式 {@code @Import} 本域的断连策略配置
     * ⇒ 证明的正是<b>域内那份配置</b>真的被装配进去了。
     */
    @SpringBootConfiguration
    @ImportAutoConfiguration(DataRedisAutoConfiguration.class)
    @Import(com.rag.users.admin.rpc.config.RedisDisconnectedBehaviorConfig.class)
    static class RedisOnlyApp {
    }

    /**
     * 用例 1：<b>断连策略确实被装配进去了</b>，且 Redis 此刻是通的。
     *
     * <p>⚠️ 这一条只是「配置层」的断言，<b>不能单独</b>当作「机制生效」的证据
     * （AGENTS.md §3.3：注解/配置存在 ≠ 机制生效）——
     * 真正证明机制生效的是用例 2 的<b>故障注入 + 耗时断言</b>。
     */
    @Test
    @Order(1)
    @DisplayName("🔴 断连策略 = REJECT_COMMANDS（配置层），且 Redis 可达时写黑名单真的通")
    void clientOptionsRejectCommandsWhileDisconnectedAndRedisReachable() {
        ClientOptions options = null;
        try (RedisConnection conn = connectionFactory.getConnection()) {
            Object nativeConn = conn.getNativeConnection();
            if (nativeConn instanceof RedisAsyncCommandsImpl impl) {
                options = impl.getStatefulConnection().getOptions();
            } else if (nativeConn instanceof StatefulConnection<?, ?> sc) {
                options = sc.getOptions();
            }
        }
        if (options == null) {
            options = connectionFactory.getClientConfiguration().getClientOptions().orElse(null);
        }
        assertNotNull(options, "拿不到 Lettuce 实际使用的 ClientOptions");
        assertEquals(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS,
                options.getDisconnectedBehavior(),
                "断连策略必须是 REJECT_COMMANDS："
                        + "Lettuce 的 DEFAULT + autoReconnect=true 会把断连期间的命令**排队**"
                        + "（DefaultEndpoint:149 / :837-852），挂到 commandTimeout(3s) "
                        + "⇒ 3s ≫ 本组件 rpc-timeout 2s ⇒ **RPC 先超时** ⇒ 客户端看到 1015，"
                        + "而 Go 是快速失败 ⇒ INTERNAL「注销失败,Redis写入异常」。");

        // ── 命令超时 / 连接超时的**消费层**锚点 ─────────────────────────
        // ⚠️ 判据必须是 `getClientConfiguration().getCommandTimeout()`：
        //   · `io.lettuce.core.ClientOptions` 在 lettuce 6.8.2 **没有** getCommandTimeout()
        //   · `ClientOptions.getTimeoutOptions()` 的 source 恒为 DefaultTimeoutSource ⇒ 假断言
        // ⇒ 本测试**不覆盖** spring.data.redis.timeout ⇒ 读到的就是**真实配置值**。
        assertEquals(Duration.ofSeconds(3),
                connectionFactory.getClientConfiguration().getCommandTimeout(),
                "命令超时必须等于 3s（= go-redis ReadTimeout 默认）⇒ 命令超时的**消费层**锚点。"
                        + "拿不到 3s 说明 spring.data.redis.timeout 压根没流到 Lettuce 客户端。");
        assertEquals(Duration.ofSeconds(5),
                connectionFactory.getClientConfiguration().getClientOptions()
                        .map(io.lettuce.core.ClientOptions::getSocketOptions)
                        .map(io.lettuce.core.SocketOptions::getConnectTimeout)
                        .orElse(null),
                "连接超时必须等于 5s（= go-redis DialTimeout 默认）⇒ 连接超时的**消费层**锚点。"
                        + "若是 10s ⇒ 那是 Lettuce 自己的默认（Boot 4 在 null 时整段跳过，不回落 timeout）。");

        // ── 命令超时的**机制腿**：3s 这个时长到底会不会被施加 ─────────
        // 🔴 2026-10-27 探针实测（Boot 4.0.8 + spring-data-redis 4.0.7 + lettuce 6.8.2）：
        //   TimeoutOptions.isTimeoutCommands        = true
        //   TimeoutOptions.isApplyConnectionTimeout = true
        //   TimeoutOptions.getSource()              = DefaultTimeoutSource ⇒ 恒返回 -1
        //   LettuceConnectionFactory.getTimeout()   = 3000ms（连接级 ⇒ RedisURI.timeout）
        // ⇒ **source 这一路给不出时长**（恒 -1）⇒ 3s 只能由 **applyConnectionTimeout 分支**
        //   从连接级 timeout 取 ⇒ ⇒ 「命令超时 3s 真的生效」是**两条腿**：
        //   ① 值（getCommandTimeout）② 机制（applyConnectionTimeout ＋ 连接级 timeout）
        // ⇒ ⚠️ **只断言值 ⇒ 假绿**：若 applyConnectionTimeout 被关掉，值照样读得到 3s，
        //   但命令**永不过期** ⇒ 断连时从「3s 快速失败」退化成「无限等待」
        // ⇒ ⚠️ `isTimeoutCommands()` **不是**有效判据（它恒为 true，与时长是否被施加无关）
        assertEquals(3000L, connectionFactory.getTimeout(),
                "连接级 timeout（⇒ RedisURI.timeout）必须是 3000ms —— "
                        + "Boot 4 的 TimeoutOptions.source 恒为 DefaultTimeoutSource（返回 -1）"
                        + " ⇒ 命令过期的时长只能从这一路取");
        assertTrue(options.getTimeoutOptions().isApplyConnectionTimeout(),
                "Lettuce 的 TimeoutOptions 必须开着 applyConnectionTimeout："
                        + "source 恒为 DefaultTimeoutSource（-1）⇒ 若这一位是 false，"
                        + "source 与回落两条路都给不出时长 ⇒ 命令永不过期 ⇒ "
                        + "「命令超时 3s」就只剩配置层事实、运行期不生效（假绿）");

        redisTemplate.opsForValue().set("admin:redis:probe", "1");
        assertEquals(Boolean.TRUE, redisTemplate.hasKey("admin:redis:probe"),
                "Redis 可达时写入/存在性检查必须成功（否则后面的故障注入测不到断连状态机）");
    }

    /**
     * 用例 2：<b>制造优雅断开（FIN）</b> ⇒ 命令必须<b>快速失败</b>（而不是挂到 commandTimeout）。
     */
    @Test
    @Order(2)
    @DisplayName("🔴 Redis 断连（FIN 形态）时命令**快速失败**（<1500ms），不是挂到 commandTimeout(3s)")
    void disconnectedRedisFailsFastInsteadOfHangingUntilCommandTimeout() {
        // 真故障注入：**先关监听口**（⇒ 之后的重连被拒）⇒ **再半关闭已建立的连接**（⇒ FIN）。
        // 顺序不能反：先关监听口才能保证「断连期间重连一定失败」。
        PROXY.disconnectGracefully();

        long t0 = System.nanoTime();
        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> redisTemplate.opsForValue().set("admin:redis:probe", "1"),
                "Redis 断连时写黑名单必须抛异常"
                        + "（上层 AdminDomainService.logout 的 catch (Exception e) ⇒ "
                        + "INTERNAL「注销失败,Redis写入异常」⇒ 与 Go 同形）");
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);

        // 把实测值打出来：台账要引用**具体数字**，不能只写「断言通过」
        System.out.printf("[Redis 断连实测] admin-rpc 写黑名单失败耗时=%dms（预算 %dms）%n",
                elapsedMs, FAST_FAIL_BUDGET_MS);

        assertTrue(elapsedMs < FAST_FAIL_BUDGET_MS,
                "Redis 断连后命令必须**快速失败**，实测耗时 " + elapsedMs + "ms，"
                        + "预算 " + FAST_FAIL_BUDGET_MS + "ms。"
                        + "若接近 3000ms ⇒ Lettuce 又在断连期间**排队**等重连 ⇒ "
                        + "3s ≫ rpc-timeout 2s ⇒ **RPC 先超时** ⇒ 客户端看到 1015，"
                        + "而 Go 是快速失败 ⇒ INTERNAL「注销失败,Redis写入异常」。"
                        + "检查 RedisDisconnectedBehaviorConfig 是否被装配。"
                        + " 连接共享模式(shareNativeConnection)=" + connectionFactory.getShareNativeConnection() + "；"
                        + " 异常链：" + causeChain(thrown));

        assertFalse(thrown instanceof io.lettuce.core.RedisCommandTimeoutException,
                "断连时的异常**不能**是 RedisCommandTimeoutException（那是「排队到 commandTimeout」的产物）；"
                        + "必须是「立即拒命令」的那条（Currently not connected. Commands are rejected.）。"
                        + "实测异常链：" + causeChain(thrown));
    }

    /** 把异常链压成一行（断言消息里要能看出 Lettuce 到底抛的是哪一条分支）。 */
    private static String causeChain(Throwable t) {
        StringBuilder sb = new StringBuilder();
        int depth = 0;
        while (t != null && depth < 8) {
            if (depth > 0) {
                sb.append(" <- ");
            }
            sb.append(t.getClass().getSimpleName()).append(": ").append(t.getMessage());
            t = t.getCause();
            depth++;
        }
        return sb.toString();
    }

    /**
     * 极简 TCP 透传代理 —— 只为制造一种故障形态：<b>服务端优雅断开（FIN）</b>。
     */
    static final class GracefulDisconnectProxy implements AutoCloseable {

        private final ServerSocket listener;
        private final Supplier<String> upstream;
        private final Set<Socket> clients = ConcurrentHashMap.newKeySet();
        private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "admin-redis-rpc-proxy");
            t.setDaemon(true);
            return t;
        });
        private volatile boolean closed;

        private GracefulDisconnectProxy(Supplier<String> upstream) throws IOException {
            this.upstream = upstream;
            this.listener = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        }

        static GracefulDisconnectProxy create(Supplier<String> upstream) {
            try {
                GracefulDisconnectProxy p = new GracefulDisconnectProxy(upstream);
                p.start();
                return p;
            } catch (IOException e) {
                throw new IllegalStateException("透传代理起不来", e);
            }
        }

        int port() {
            return listener.getLocalPort();
        }

        private void start() {
            pool.submit(() -> {
                while (!closed) {
                    try {
                        Socket client = listener.accept();
                        clients.add(client);
                        String[] hp = upstream.get().split(":");
                        Socket server = new Socket(hp[0], Integer.parseInt(hp[1]));
                        pool.submit(() -> pipe(client, server));
                        pool.submit(() -> pipe(server, client));
                    } catch (Exception e) {
                        if (!closed) {
                            try {
                                Thread.sleep(50);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                        }
                    }
                }
            });
        }

        private static void pipe(Socket from, Socket to) {
            byte[] buf = new byte[8192];
            try (Socket f = from) {
                int n;
                while ((n = f.getInputStream().read(buf)) > 0) {
                    to.getOutputStream().write(buf, 0, n);
                    to.getOutputStream().flush();
                }
            } catch (Exception ignored) {
                // 断连是预期内的
            } finally {
                try {
                    to.shutdownOutput();
                } catch (Exception ignored) {
                    // 已关闭
                }
            }
        }

        /**
         * 制造断连：<b>先关监听口</b>（⇒ 重连被 connection refused 挡掉）
         * ⇒ <b>再半关闭已建立的连接</b>（⇒ 客户端收到 FIN）。
         *
         * <p>⚠️ 用 {@code shutdownOutput()} 而不是 {@code close()}：
         * socket 上若还有未读数据，{@code close()} 会发 <b>RST</b> ⇒ 就退回成
         * 「Connection reset」那条不经过 {@code disconnectedBehavior} 的路径了。
         */
        void disconnectGracefully() {
            closed = true;
            try {
                listener.close();
            } catch (Exception ignored) {
                // 已关闭
            }
            for (Socket s : clients) {
                try {
                    s.shutdownOutput();
                } catch (Exception ignored) {
                    // 已关闭
                }
            }
            clients.clear();
        }

        @Override
        public void close() {
            disconnectGracefully();
            pool.shutdownNow();
        }
    }
}
