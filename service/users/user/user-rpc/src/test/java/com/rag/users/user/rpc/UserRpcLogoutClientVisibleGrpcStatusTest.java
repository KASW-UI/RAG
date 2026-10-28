package com.rag.users.user.rpc;

import com.rag.users.user.proto.LogoutReq;
import com.rag.users.user.proto.LogoutResp;
import com.rag.users.user.proto.UserServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.TimeUnit;

import com.rag.users.user.rpc.support.GoStyleRedisDisconnectProxy;
import com.rag.users.user.rpc.support.RealGrpcPort;
import static com.rag.users.user.rpc.support.GoStyleHostPortPinning.pinContainerPort;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 **L4 缺口闭合点：RAG 腿的「客户端可见 gRPC 响应」**（2026-10-28 · G1 lane）
 *
 * <h2>为什么要有这个类</h2>
 * <p>§48.1 的四条腿里，<b>Go 腿的「客户端可见 gRPC status」是 L4（真源进程内直观测）</b>，
 * 而 <b>RAG 腿那一格当时只有 L1</b>（源码推断：{@code UserDomainService.logout} 的 catch 块映射）⇒ ⇒
 * 两侧强度不齐 ⇒ 按弱侧记 ⇒ <b>V-014 / ARPC-013 当时只能写「机制面双侧 L4 ＋ 响应面 Go 腿 L4 / RAG 腿 L1」</b>。
 * <p><b>本类补的就是 RAG 腿那一格</b> ⇒ 用<b>真 gRPC server ＋ 真 gRPC 客户端</b>，
 * 把响应从 wire 上抓下来 ⇒ ⇒ 那一格才能升 L4。
 *
 * <h2>为什么必须是「真 server ＋ 真 client」，不能直调 service</h2>
 * <p>因为这一格要回答的问题<b>不是</b>「service 抛了什么异常」，而是
 * <b>「客户端最终看到什么 status」</b> ⇒ ⇒ 而 status 的最终形态由<b>整条拦截器链</b>决定：
 * <pre>
 *   GoStyleRpcProtectionChain.build(...) = recover → breaker → shedding → 业务观测拦截器(含 2s 超时)
 * </pre>
 * 直调 {@code UserDomainService.logout} <b>绕过了整条链</b> ⇒ ⇒ 恰恰漏掉最关键的那一格：
 * <b>2s 超时预算会不会先于业务异常触发</b>（触发了 ⇒ 客户端看到的是 {@code DEADLINE_EXCEEDED} / 1015，
 * 而<b>不是</b> {@code INTERNAL}）⇒ ⇒ 这正是 V-014 / ARPC-013 的题眼。
 *
 * <h2>本类断言的判据</h2>
 * <ol>
 *   <li><b>对照组（Redis 可达）</b>：{@code logout} <b>成功</b> ⇒ 证明「server / 客户端 / 整条链 / proto」都通
 *       （否则后面「失败」可能是「根本没连上」这种假失败 ⇒ §3.3：不能用错误答案当守卫）；</li>
 *   <li><b>实验组（Redis 被 FIN 断开）</b>：客户端拿到的 status <b>必须是</b>
 *       {@code INTERNAL} ＋ 描述「注销失败,Redis写入异常」
 *       ⇒ ⇒ <b>与 Go 腿逐字同形</b>（Go 腿实测：{@code PROBE-RESULT ... grpc_code=Internal
 *       grpc_message="注销失败,Redis写入异常"}）⇒ ⇒ 这才是「两侧一致」的正面证据；</li>
 *   <li><b>耗时</b>：失败调用必须 <b>≪ 2s rpc 预算</b>（预算来自 {@code observability.rpc-timeout: 2s}）
 *       ⇒ ⇒ 证明「是业务异常先到、不是超时先到」⇒ ⇒ 与 §48.1 的 RAG 腿 0ms 口径互相印证。</li>
 * </ol>
 *
 * <h2>⚠️ 隔离（照 G3 交来的隔离配方逐条对照 ⇒ 逐条结论见 docs/DRIFT/users/vs-common.md §48.1-bis）</h2>
 * <ul>
 *   <li><b>Redis</b>：本类用<b>自己的 Testcontainers 容器</b>（与 Go 腿那次用的 {@code g1-users-probe-redis}
 *       <b>不是同一个实例</b>）⇒ ⇒ <b>db 级串味不可能发生</b>；</li>
 *   <li><b>PG</b>：本类起 PG 只是<b>让完整应用上下文能起来</b>（{@code logout} 路径本身不碰 DB）
 *       ⇒ ⇒ 用的是<b>自建库</b>（{@code user_rpc_it}）⇒ ⇒ 不碰任何共享库；</li>
 *   <li><b>端口</b>：gRPC 端口用<b>运行时挑的空闲口</b>（不是 yaml 里的 49210 ⇒ 不与本机容器撞口）
 *       ⇒ ⇒ 挑不到 ⇒ <b>上下文起不来 ⇒ 大声失败</b>（不静默）；</li>
 *   <li><b>服务发现</b>：Nacos 指向必然拒连的 {@code 127.0.0.1:1}（启动自检只记日志不中断）⇒ ⇒ 不注册任何服务。</li>
 * </ul>
 *
 * <h2>⚠️ 为什么本类要 {@code @DirtiesContext(AFTER_CLASS)}</h2>
 * <p>本上下文里 {@code spring.data.redis.port} 指向一个<b>会随本类结束而死的代理端口</b>
 * ⇒ ⇒ 若上下文被缓存下来给别的测试复用 ⇒ 那个测试会连到一个已死的端口 ⇒ <b>假红</b>
 * ⇒ ⇒ 必须标 {@code AFTER_CLASS} 丢弃。
 *
 * <p><b>Ryuk 已在本模块 pom 的 surefire 配置里关掉</b>（与同模块其它容器测试同款理由）。
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
        classes = UserRpcApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                // Nacos：指向必然拒连的地址 ⇒ 启动自检只记日志、不中断启动，也不注册任何服务
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                // OTLP：不可达端点只会在后台重试刷日志 ⇒ 关掉
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
@Testcontainers
class UserRpcLogoutClientVisibleGrpcStatusTest {

    /**
     * PG 只为<b>让完整应用上下文起得来</b>（{@code logout} 路径不碰 DB）
     * ⇒ ⇒ 自建库 ⇒ 不碰任何共享库 ⇒ 与 Go 腿那次「跳过 model.InitDB」不冲突。
     */
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("user_rpc_it")
                    .withUsername("rag")
                    .withPassword("rag")
                    .withCreateContainerCmdModifier(cmd -> pinContainerPort(cmd, 5432));

    /** 本类自己的 Redis（与 Go 腿那次用的容器不是同一个实例 ⇒ db 级串味不可能）。 */
    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine")
                    .withExposedPorts(6379)
                    .withCreateContainerCmdModifier(cmd -> pinContainerPort(cmd, 6379));

    /**
     * app → Redis 的透传代理（<b>同款</b> FIN 故障形态：先关监听口，再对已建立连接半关闭）
     * ⇒ ⇒ 与 Go 腿那次用的代理<b>同形态</b>（否则两侧数字不可比 —— §48.1 的口径表第一行）。
     */
    static final GoStyleRedisDisconnectProxy PROXY = GoStyleRedisDisconnectProxy.create(() -> {
        try {
            return REDIS.getHost() + ":" + REDIS.getMappedPort(6379);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Redis 容器还没起来", e);
        }
    });

    /**
     * gRPC 端口：<b>运行时挑一个空闲口</b> ⇒ 不用 yaml 里的 49210（那个口被本机容器占着）⇒
     * ⇒ 也避免与别的 lane 的 Testcontainers 随机口撞在同一段。
     * ⚠️ 挑完就释放 ⇒ 与应用真正 bind 之间有一个极小窗口 ⇒ 若被别人抢走 ⇒
     * <b>上下文起不来（BindException）⇒ 大声失败</b> ⇒ 不会静默测错东西。
     */
    static final int GRPC_PORT = pickFreePort();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("grpc.server.port", () -> String.valueOf(GRPC_PORT));
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.data.redis.host", () -> "127.0.0.1");
        r.add("spring.data.redis.port", PROXY::port);
        r.add("spring.data.redis.password", () -> "");
    }

    /** 只用于「对照组证明真的写进 Redis 了」（读的是 app 自己的那个 RedisTemplate ⇒ 同一个实例）。 */
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    @DisplayName("🔴 Redis 断连 ⇒ 客户端可见的 gRPC status 必须是 INTERNAL「注销失败,Redis写入异常」（与 Go 腿逐字同形）")
    void logoutClientSeesInternalWhenRedisDisconnected() throws Exception {
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress("127.0.0.1", GRPC_PORT)
                .usePlaintext()
                .build();
        try {
            UserServiceGrpc.UserServiceBlockingStub stub = UserServiceGrpc.newBlockingStub(channel);

            // ── ① 对照组：Redis 可达 ⇒ logout 必须成功 ──
            //    这一步不是「多余的前置」：它证明「server 起得来 / 客户端连得上 / proto 对得上 / 整条链通」
            //    ⇒ ⇒ 否则后面那个「失败」可能只是「根本没连上」这种**假失败**（§3.3：不能用错误答案当守卫）。
            LogoutResp ok = stub.logout(LogoutReq.newBuilder().setToken("g1-probe-reachable").build());

            // ⚠️ **不要断言 success == true**（2026-10-28 实测打脸过一次）：
            //   Go 真源 `service/user/user/rpc/internal/logic/logoutlogic.go:43`
            //   = `return &pb.LogoutResp{}, nil` ⇒ **success 永远是 proto3 的默认值 false**
            //   ⇒ ⇒ RAG 侧 `LogoutResp.newBuilder().build()`（**不 setSuccess**）**与 Go 逐字同形**
            //   ⇒ ⇒ **这不是漂移**，而且**改成 true 反而会制造漂移** ⇒ ⇒ 这里把「false」钉住当守卫。
            assertFalse(ok.getSuccess(),
                    "Go 真源 logoutlogic.go:43 返回 `&pb.LogoutResp{}` ⇒ success 恒为 proto3 默认 false"
                            + " ⇒ RAG 侧必须同样不设 ⇒ ⚠️ 谁把它改成 true 谁就制造了一条新漂移");

            // 并且**证明写真的发生了**（不能只看「没抛异常」—— 那样可能根本没写进 Redis）
            String written = redisTemplate.opsForValue().get("user:jwt_blacklist:g1-probe-reachable");
            assertEquals("banned", written,
                    "对照组必须证明黑名单**真的写进 Redis 了** ⇒ 否则后面的「失败」可能只是「压根没写」");

            // ── ② 制造断连（FIN 形态，与 Go 腿同款）──
            PROXY.disconnectGracefully();

            // ── ③ 实验组：客户端可见的 status ──
            long t0 = System.nanoTime();
            StatusRuntimeException err = assertThrows(StatusRuntimeException.class,
                    () -> stub.logout(LogoutReq.newBuilder().setToken("g1-probe-disconnected").build()));
            long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;

            System.out.printf("[A 组 · REJECT_COMMANDS 断连行为] code=%s description=%s elapsedMs=%d%n",
                    err.getStatus().getCode(), err.getStatus().getDescription(), elapsedMs);

            assertEquals(Status.Code.INTERNAL, err.getStatus().getCode(),
                    "Redis 断连时客户端必须看到 INTERNAL（与 Go 腿逐字同形）"
                            + " ⇒ 实测 code=" + err.getStatus().getCode()
                            + " description=" + err.getStatus().getDescription()
                            + " elapsedMs=" + elapsedMs
                            + " ⇒ ⚠️ 若实测是 DEADLINE_EXCEEDED/1015 ⇒ 说明 2s 超时预算先于业务异常触发"
                            + " ⇒ ⇒ 断连策略没生效（REJECT_COMMANDS 缺失）⇒ ⇒ **不是**「两侧一致」。");
            assertEquals("注销失败,Redis写入异常", err.getStatus().getDescription(),
                    "描述必须与 Go 腿逐字相同（Go 实测 grpc_message=\"注销失败,Redis写入异常\"）");

            // ── ④ 耗时：必须 ≪ 2s rpc 预算 ⇒ 证明「业务异常先到、不是超时先到」──
            long budgetMs = 2000L;
            assertTrue(elapsedMs < budgetMs,
                    "失败调用必须在 rpc 预算（" + budgetMs + "ms）内返回 ⇒ 实测 " + elapsedMs
                            + "ms ⇒ ⇒ 超过就说明是超时先到（那是 1015，不是 INTERNAL）");
        } finally {
            channel.shutdownNow();
            channel.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    /** 挑一个当前空闲的端口（挑完立即释放 ⇒ 应用 bind 时若被抢 ⇒ 大声失败，不静默）。 */
    private static int pickFreePort() {
        // ⚠️ 原来用 `new ServerSocket(0)`：那是 **OS 盲选口**（macOS 临时段 49152–65535）
        //    ⇒ 与 Docker 随机发布口**同段** ⇒ 「挑到没人监听的口」这个前提**直接失效**
        //    ⇒ 邻居占 127.0.0.1:P 时我们的通配绑定照样成功、lsof 两个监听者并存
        //    ⇒ 客户端被更具体的那个抢走 ⇒ **静默假红**（机制与实测见 support/RealGrpcPort）。
        return RealGrpcPort.pick();
    }
}
