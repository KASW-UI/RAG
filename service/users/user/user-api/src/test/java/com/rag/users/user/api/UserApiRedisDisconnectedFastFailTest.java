package com.rag.users.user.api;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisAsyncCommandsImpl;
import io.lettuce.core.api.StatefulConnection;
import com.rag.users.user.api.client.UserRpcClient;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 user-api 的 **Redis 断连行为**必须与 Go 真源同形：<b>快速失败 ⇒ fail-open 放行</b>。
 *
 * <h2>这条守的是哪条漂移</h2>
 *
 * <p>{@code docs/DRIFT/users/user/api.md} 的 <b>UNRESOLVED-004</b>：
 * 「Redis 不可用 / 很慢时，两侧分别返回什么？」
 * 2026-10-24 用真 Redis 故障注入跑出<b>可观察差异</b>（<b>DRIFT-USERAPI-014</b>）：
 *
 * <pre>
 *   把两侧的 Redis 容器**停掉**（⇒ connection refused），再打同一个受保护端点：
 *     Go  : HTTP 200  耗时 1.72s   ← fail-open 放行
 *     RAG : HTTP 503  耗时 3.02s   body="Request Timeout"
 * </pre>
 *
 * <h2>根因（行号指向<b>消费点</b>，不是声明点 —— AGENTS.md §3.3.1）</h2>
 *
 * <ul>
 *   <li>Lettuce {@code io.lettuce.core.protocol.DefaultEndpoint:149}
 *       {@code this.rejectCommandsWhileDisconnected = isRejectCommand(clientOptions)}；
 *       {@code :837-852} 的判据是
 *       {@code DEFAULT + autoReconnect=true ⇒ 不拒 ⇒ 命令<b>排队</b>等重连}；</li>
 *   <li>Spring Boot 4.0.8 {@code org.springframework.boot.data.redis.autoconfigure.
 *       LettuceConnectionConfiguration:209-230 createClientOptions(...)}
 *       <b>从不设</b> {@code disconnectedBehavior} ⇒ 用 Lettuce 默认 {@code DEFAULT}；</li>
 *   <li>⇒ 断连期间 {@code hasKey} 被<b>挂起</b>，一直挂到
 *       {@code spring.data.redis.timeout: 3s} 的 commandTimeout（实测 3.02s），
 *       而 HTTP 预算（{@code observability.http-timeout: 3s}）<b>先耗尽</b> ⇒ <b>503</b>。</li>
 * </ul>
 *
 * <p>Go 侧为什么是 200：go-zero {@code core/stores/redis/redisclientmanager.go:14}
 * {@code maxRetries = 3}、{@code :32-40} {@code red.NewClient(&red.Options{... MaxRetries: maxRetries ...})}
 * <b>没有</b>设 {@code DialTimeout}/{@code ReadTimeout}
 * ⇒ 连接被拒时<b>每次重试都立即失败</b> ⇒ 整体快速失败（实测 ~1.7s，仍在 3s 预算内）
 * ⇒ 进业务层 ⇒ {@code checkblacklistmiddleware.go:36 if err == nil && exists} 判据为 false
 * ⇒ <b>fail-open 放行 ⇒ 200</b>。
 *
 * <h2>修法</h2>
 *
 * <p>{@code RedisDisconnectedBehaviorConfig} 把 Lettuce 的断连行为设成
 * {@link ClientOptions.DisconnectedBehavior#REJECT_COMMANDS}
 * ⇒ {@code DefaultEndpoint:314} 直接返回
 * {@code RedisException("Currently not connected. Commands are rejected.")}
 * ⇒ 失败耗时 ≪ 3s ⇒ 进业务层 ⇒ <b>fail-open 200</b>，与 Go 同形。
 *
 * <h2>⚠️⚠️ 故障注入方式是这个测试的**全部难点**（2026-10-24 实测踩出来的）</h2>
 *
 * <p>第一版用 {@code container.stop()} 做故障注入，**测不出这条漂移** ——
 * 变异验证（把 {@code REJECT_COMMANDS} 改回 {@code DEFAULT}）时测试**照样绿**。
 * 原因（用时间曲线探针实测到 Lettuce 层的异常原文）：
 *
 * <pre>
 *   REJECT_COMMANDS : RedisException: Currently not connected. Commands are rejected.
 *   DEFAULT         : RedisException: java.net.SocketException: Connection reset
 * </pre>
 *
 * <p>⇒ {@code container.stop()} 让容器被**强杀** ⇒ 连接收到的是 <b>RST</b>
 * ⇒ Lettuce 往 socket 写命令时**立刻失败** ⇒ <b>根本走不到</b>
 * {@code disconnectedBehavior} 那个分支 ⇒ 两种配置的耗时都是 1~13ms ⇒ <b>分不出</b>。
 * <br>⇒ 而线上真实场景是 {@code docker stop} ⇒ Redis <b>优雅退出</b> ⇒ 连接收到的是 <b>FIN</b>
 * ⇒ Lettuce 进入 <b>DISCONNECTED（正在重连）</b> 状态
 * ⇒ 命令被<b>排队</b> ⇒ 这才是 {@code DEFAULT} 会挂 3s 的那条路径。
 *
 * <p>⇒ 本类用一个<b>可控 TCP 透传代理</b>把 app 挡在中间：
 * 故障时<b>先关监听口</b>（⇒ 重连被拒）+ <b>再对已建立的连接半关闭</b>（⇒ 发 FIN）
 * ⇒ 精确复刻 live 的断连形态。
 *
 * <h2>⚠️ 本类只守「断连」，<b>不</b>守「连着但不回包」</h2>
 *
 * <p>后者（{@code CLIENT PAUSE}）两侧都应 503 "Request Timeout"
 * （活体实测：Go 3.003s / RAG 3.018s，<b>完全一致</b>）
 * ⇒ 那是「命令真的发出去了、真的在等」⇒ 拒绝没有意义，且改了会把 503 变成 200 ⇒ <b>是反向漂移</b>。
 *
 * <h2>为什么必须真 Redis（不能 mock）</h2>
 *
 * <p>要触发的是 Lettuce 的<b>断连状态机</b>：mock 数据层只会证明「代码调用了 hasKey」，
 * 证明不了「断连时命令被排队还是被拒绝」——
 * 那正是 §3.3 说的「绿测试测不到真机制」。
 *
 * <h2>本类的可证伪性</h2>
 *
 * <p>把 {@code RedisDisconnectedBehaviorConfig} 里的 {@code REJECT_COMMANDS} 改回 {@code DEFAULT}，
 * 用例 2 应当<b>红</b>，且失败消息里的实测耗时应是 <b>≈3000ms</b>（挂到 commandTimeout）
 * 而不是 &lt;1500ms ⇒ 说明这条守卫真的在盯「快速失败」这件事，
 * 而不是「Bean 存在」这种假断言。
 * （2026-10-24 实测：变异后用例 1、2 **都红**，用例 2 的耗时是 ≈3000ms。）
 */
@SpringBootTest(
        classes = UserApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                // 拒连最快；Nacos 连不上只会打日志，不影响本测试
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                // UserRpcClient 用不到，给个不会真连、也不走 Nacos name resolver 的地址
                "grpc.client.user-rpc.address=static://127.0.0.1:65535",
                "grpc.client.user-rpc.negotiation-type=plaintext",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserApiRedisDisconnectedFastFailTest {

    /**
     * 🔴 DRIFT-G1-002（2026-10-27）本测试**必须** mock 掉 {@code UserRpcClient}。
     *
     * <p>理由（与 admin-api 的 {@code AdminApiPlainText400ContentTypeTest} 同款）：
     * {@code UserRpcClient.afterPropertiesSet()} 是「启动期拨号检查已武装」的自检，
     * 而 devh 的 {@code immediate-connect-timeout} 现在是 <b>3s</b>
     * ⇒ 本测试的 {@code grpc.client.user-rpc.address} 指向 {@code 127.0.0.1:65535}
     * （**本来就没人监听**）⇒ 不 mock 的话<b>上下文启动就会失败</b>
     * （{@code Can't connect to channel user-rpc}）。
     *
     * <p>被 mock 的 bean <b>不会</b>执行 {@code afterPropertiesSet()}
     * ⇒ 启动期检查被跳过 ⇒ 本测试只关心 Redis 的快速失败语义，不受 gRPC 影响。
     */
    @MockitoBean
    private UserRpcClient userRpcClient;

    /**
     * 断连后命令必须<b>在 1500ms 内</b>失败。
     *
     * <p>阈值怎么定的：修复前的实测是 <b>≈3020ms</b>（挂到 {@code commandTimeout: 3s}），
     * 修复后是 <b>≈1ms</b>（活体对拍里是 28ms）。
     * ⇒ 1500ms 落在两者中间，两侧都判得开。
     */
    private static final long FAST_FAIL_BUDGET_MS = 1500L;

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    /**
     * app 与 Redis 之间的透传代理。
     *
     * <p>上游地址<b>延迟解析</b>（accept 时才取）⇒ 代理可以在容器启动<b>之前</b>就建好，
     * 从而 {@code @DynamicPropertySource} 能在 Spring 上下文创建前就拿到端口。
     */
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
     * 用例 1：<b>断连策略确实被装配进去了</b>，且 Redis 此刻是通的。
     *
     * <p>⚠️ 这一条只是「配置层」的断言，<b>不能单独</b>当作「机制生效」的证据
     * （AGENTS.md §3.3：注解/配置存在 ≠ 机制生效）——
     * 真正证明机制生效的是用例 2 的<b>故障注入 + 耗时断言</b>。
     */
    @Test
    @Order(1)
    @DisplayName("🔴 断连策略 = REJECT_COMMANDS（配置层），且 Redis 可达时 hasKey 真的通")
    void clientOptionsRejectCommandsWhileDisconnectedAndRedisReachable() {
        // ⚠️ 读**运行时真正在用的**那份 ClientOptions（不是 factory 上的配置副本）：
        //    Lettuce 端点实际拿到的对象才有意义。
        //    `conn.getNativeConnection()` 返回的是 `RedisAsyncCommandsImpl` 门面，
        //    它的 `getStatefulConnection()` 才是连接本体 ⇒ `StatefulConnection.getOptions()`。
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
                        + "（DefaultEndpoint:149 / :837-852），挂到 commandTimeout(3s) ⇒ HTTP 预算先耗尽 ⇒ 503，"
                        + "而 Go 是快速失败 ⇒ fail-open 200。");

        // ── 命令超时 / 连接超时的**消费层**锚点 ─────────────────────────
        // ⚠️ 判据必须是 `getClientConfiguration().getCommandTimeout()`：
        //   · `io.lettuce.core.ClientOptions` 在 lettuce 6.8.2 **没有** getCommandTimeout()
        //   · `ClientOptions.getTimeoutOptions()` 的 source 恒为 DefaultTimeoutSource ⇒ 假断言
        // ⇒ 本测试**不覆盖** spring.data.redis.timeout ⇒ 读到的就是**真实配置值**。
        assertEquals(Duration.ofSeconds(3),
                connectionFactory.getClientConfiguration().getCommandTimeout(),
                "命令超时必须等于 3s（= go-redis ReadTimeout 默认）⇒ 这是命令超时的**消费层**锚点。"
                        + "拿不到 3s 说明 spring.data.redis.timeout 压根没流到 Lettuce 客户端。");
        assertEquals(Duration.ofSeconds(5),
                connectionFactory.getClientConfiguration().getClientOptions()
                        .map(io.lettuce.core.ClientOptions::getSocketOptions)
                        .map(io.lettuce.core.SocketOptions::getConnectTimeout)
                        .orElse(null),
                "连接超时必须等于 5s（= go-redis DialTimeout 默认）⇒ 连接超时的**消费层**锚点。"
                        + "若是 10s ⇒ 那是 Lettuce 自己的默认（Boot 4 在 null 时整段跳过，不回落 timeout）。");

        redisTemplate.opsForValue().set("uu:redis:probe", "1");
        assertEquals(Boolean.TRUE, redisTemplate.hasKey("uu:redis:probe"),
                "Redis 可达时 hasKey 必须为 true（否则后面的故障注入测不到断连状态机）");
    }

    /**
     * 用例 2：<b>制造优雅断开（FIN）</b> ⇒ 命令必须<b>快速失败</b>（而不是挂到 commandTimeout）。
     *
     * <p>这条是本类的核心断言，也是修复前会红的那一条。
     */
    @Test
    @Order(2)
    @DisplayName("🔴 Redis 断连（FIN 形态）时命令**快速失败**（<1500ms），不是挂到 commandTimeout(3s)")
    void disconnectedRedisFailsFastInsteadOfHangingUntilCommandTimeout() {
        // 真故障注入：**先关监听口**（⇒ 之后的重连被拒）
        // ⇒ **再对已建立的连接半关闭**（⇒ 客户端收到 FIN，而不是 RST）
        // 这两步的顺序不能反：先关监听口才能保证「断连期间重连一定失败」，
        // 否则 Lettuce 会在 FIN 到达前就重连成功 ⇒ 又测不到排队分支。
        PROXY.disconnectGracefully();

        long t0 = System.nanoTime();
        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> redisTemplate.hasKey("uu:redis:probe"),
                "Redis 断连时 hasKey 必须抛异常（上层 JwtAuthInterceptor.isBlacklisted "
                        + "catch (Throwable) ⇒ exists=null ⇒ fail-open 放行）");
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);

        // 把实测值打出来：台账要引用**具体数字**，不能只写「断言通过」
        System.out.printf("[Redis 断连实测] user-api hasKey 失败耗时=%dms（预算 %dms）%n",
                elapsedMs, FAST_FAIL_BUDGET_MS);

        assertTrue(elapsedMs < FAST_FAIL_BUDGET_MS,
                "Redis 断连后命令必须**快速失败**，实测耗时 " + elapsedMs + "ms，"
                        + "预算 " + FAST_FAIL_BUDGET_MS + "ms。"
                        + "若接近 3000ms ⇒ Lettuce 又在断连期间**排队**等重连 ⇒ "
                        + "请求会先撞上 HTTP 3s 预算 ⇒ 变成 503，而 Go 是 200（fail-open）。"
                        + "检查 RedisDisconnectedBehaviorConfig 是否被装配。"
                        + " 连接共享模式(shareNativeConnection)=" + connectionFactory.getShareNativeConnection() + "；"
                        + " 异常链：" + causeChain(thrown));
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
     *
     * <p>为什么不用 {@code container.stop()}：那发的是 <b>RST</b>
     * ⇒ Lettuce 往 socket 写命令时立刻失败 ⇒ 压根不经过 {@code disconnectedBehavior} 分支
     * ⇒ 测不出本条漂移（2026-10-24 实测，见类注释）。
     */
    static final class GracefulDisconnectProxy implements AutoCloseable {

        private final ServerSocket listener;
        private final Supplier<String> upstream;
        private final Set<Socket> clients = ConcurrentHashMap.newKeySet();
        private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "uu-redis-proxy");
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
                            // 上游还没起来等一小会儿再来
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
