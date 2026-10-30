package com.rag.users.user.rpc;

import com.rag.users.user.proto.LogoutReq;
import com.rag.users.user.proto.UserServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.lettuce.core.ClientOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientOptionsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import com.rag.users.user.rpc.support.RealGrpcPort;

import static com.rag.users.user.rpc.support.GoStyleHostPortPinning.pinContainerPort;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 **B 组反例：把断连策略换成 {@code DEFAULT} ⇒ 客户端就会看到 1015（DEADLINE_EXCEEDED）而不是 INTERNAL**
 * （2026-10-28 · G1 lane）
 *
 * <h2>这个类存在的理由（AGENTS.md §3.3「跑绿 ≠ 判据活着」）</h2>
 * <p>{@code UserRpcLogoutClientVisibleGrpcStatusTest}（A 组）证明了「RAG 侧客户端看到的是
 * {@code INTERNAL}「注销失败,Redis写入异常」，与 Go 腿逐字同形」。
 * <p><b>但 A 组单独存在时，证明不了「是那条机制决定的」</b> —— 它只证明「当前配置下是 INTERNAL」，
 * 同样兼容「无论 Redis 怎么断连都会是 INTERNAL」这个假设 ⇒ ⇒ **归因强度只能停在 L3**。
 * <p>本类提供<b>同场 B 组</b>：<b>唯一变量 = 断连策略</b> ⇒ ⇒ 与 A 组并排 ⇒ ⇒
 * <b>「是 REJECT_COMMANDS 这条决定的」才成立</b> ⇒ ⇒ 归因强度升 L4。
 *
 * <h2>怎么做到「唯一变量 = 那一条」</h2>
 * <p>共享层 {@code GoStyleRedisClientAutoConfiguration} 的 bean 带
 * {@code @ConditionalOnMissingBean} ⇒ ⇒ <b>域侧一旦已定义 {@code LettuceClientOptionsBuilderCustomizer}，
 * 共享层那份根本不会创建</b>（是「遮蔽」不是「叠加」）⇒ ⇒ 本类用一个 <b>test 侧</b>的
 * {@code @TestConfiguration} 提供一份值设成 {@code DEFAULT} 的 customizer ⇒ ⇒
 * 断连策略从 {@code REJECT_COMMANDS} 变成 {@code DEFAULT}，<b>其余全部不变</b>
 * （同款代理 / 同款故障形态 / 同款测点 / 同款 gRPC server 与客户端）。
 *
 * <h2>预期结果（这就是 V-014 / ARPC-013 的题眼）</h2>
 * <pre>
 *   A 组（REJECT_COMMANDS）：断连 ⇒ 命令【立即被拒】⇒ ~1ms ⇒ 业务异常先到
 *                            ⇒ 客户端看到 INTERNAL「注销失败,Redis写入异常」  ← 与 Go 同形
 *   B 组（DEFAULT）      ：断连 ⇒ 命令【被排队】等重连 ⇒ 撞上 commandTimeout 3s
 *                            ⇒ 但 2s 的 rpc 预算【先到】⇒ 客户端看到 DEADLINE_EXCEEDED（1015）
 * </pre>
 * ⇒ ⇒ **B 组看到 1015 这件事，正是「A 组的 INTERNAL 不是白捡的」的证据** ⇒ ⇒
 * ⇒ ⇒ 也解释了为什么这条修复<b>必须做</b>：没有它，客户端拿到的就是 1015 而不是 Go 的 INTERNAL。
 *
 * <h2>⚠️ 为什么这个 B 组不会踩 G4 的源扫守门</h2>
 * <p>{@code GoStyleRedisClientShadowingGateTest} 扫的是 {@code src/main/java} 那一层
 * ⇒ ⇒ <b>{@code src/test/java} 不在扫描面</b> ⇒ ⇒ 守门在我域仍绿（已实测，见台账 §49.1）。
 * ⚠️（本行**刻意不写** {@code service/…/src/main/java} 的完整写法 ⇒ ⇒ 因为那个通配写法里含
 * {@code **}{@code /} ⇒ ⇒ 会被 javadoc 词法器当成**注释结束符** ⇒ ⇒ 整份注释提前闭合 ⇒ ⇒ 编译报错
 * ⇒ ⇒ 教训：**javadoc 里不能出现字面的星号-斜杠组合**。）
 *
 * <h2>⚠️ 本类的「变红能力」是内建的</h2>
 * <p>把下面那个 {@code defaultDisconnectedBehaviorCustomizer} bean 删掉
 * ⇒ ⇒ 共享层那份 {@code REJECT_COMMANDS} 就会接管 ⇒ ⇒ 客户端会看到 INTERNAL ⇒ ⇒
 * <b>本类的断言必红</b> ⇒ ⇒ **不需要额外的变异注入就能证明它不是恒真**。
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
        classes = {
                UserRpcApplication.class,
                UserRpcLogoutClientVisibleGrpcStatusDefaultBehaviorTest.DefaultDisconnectedBehaviorConfig.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
@Testcontainers
class UserRpcLogoutClientVisibleGrpcStatusDefaultBehaviorTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("user_rpc_it")
                    .withUsername("rag")
                    .withPassword("rag")
                    .withCreateContainerCmdModifier(cmd -> pinContainerPort(cmd, 5432));

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine")
                    .withExposedPorts(6379)
                    .withCreateContainerCmdModifier(cmd -> pinContainerPort(cmd, 6379));

    /**
     * <b>与 A 组用【同一个】代理实现</b>（{@code support/GoStyleRedisDisconnectProxy}）
     * ⇒ ⇒ 两侧故障形态必然同款 ⇒ ⇒ 「同口径」成立。
     */
    static final com.rag.users.user.rpc.support.GoStyleRedisDisconnectProxy PROXY =
            com.rag.users.user.rpc.support.GoStyleRedisDisconnectProxy.create(() -> {
                try {
                    return REDIS.getHost() + ":" + REDIS.getMappedPort(6379);
                } catch (RuntimeException e) {
                    throw new IllegalStateException("Redis 容器还没起来", e);
                }
            });

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

    @Autowired
    private LettuceConnectionFactory connectionFactory;

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    /**
     * <b>唯一变量</b>：断连策略 = {@code DEFAULT}（把共享层那份 {@code REJECT_COMMANDS} 遮蔽掉）。
     *
     * <p>⚠️ <b>为什么放 test 侧</b>：共享层在 G4 写权面 ⇒ 域侧不能改它的源码；
     * 而 {@code @ConditionalOnMissingBean} 让我们可以**在 test 侧提供一份同类型的 bean** 来遮蔽它
     * ⇒ ⇒ **域内零落码**（不碰共享层、不碰域内生产代码）⇒ ⇒ 这就是「A/B 受控对照」能做到「唯一变量」的原因。
     */
    @TestConfiguration
    static class DefaultDisconnectedBehaviorConfig {

        @Bean
        LettuceClientOptionsBuilderCustomizer defaultDisconnectedBehaviorCustomizer() {
            return builder -> builder.disconnectedBehavior(ClientOptions.DisconnectedBehavior.DEFAULT);
        }
    }

    @Test
    @DisplayName("🔴 B 组反例：断连策略=DEFAULT ⇒ 命令被排队 ⇒ 2s rpc 预算先到 ⇒ 客户端看到 DEADLINE_EXCEEDED(1015)，不是 INTERNAL")
    void logoutClientSeesDeadlineExceededWhenCommandsAreQueued() throws Exception {
        // ── ① 前置：确认「遮蔽真的发生了」──
        //    容器里必须【恰好一份】customizer，且它的断连行为确实是 DEFAULT
        //    ⇒ 否则本类测的就不是「DEFAULT 下会怎样」，而是「压根没装策略」
        Map<String, LettuceClientOptionsBuilderCustomizer> customizers =
                applicationContext.getBeansOfType(LettuceClientOptionsBuilderCustomizer.class);
        assertEquals(1, customizers.size(),
                "容器里必须恰好一份 LettuceClientOptionsBuilderCustomizer ⇒ 实测 " + customizers.keySet()
                        + " ⇒ 若为 2 份 ⇒ 共享层那份没被遮蔽 ⇒ ⇒ 本类测的就不是 DEFAULT 场景");
        ClientOptions options = connectionFactory.getClientConfiguration().getClientOptions().orElse(null);
        assertEquals(ClientOptions.DisconnectedBehavior.DEFAULT,
                options == null ? null : options.getDisconnectedBehavior(),
                "断连行为必须是 DEFAULT（这才是本组的唯一变量）");

        ManagedChannel channel = ManagedChannelBuilder
                .forTarget("dns:///127.0.0.1:" + GRPC_PORT)
                .usePlaintext()
                .build();
        try {
            UserServiceGrpc.UserServiceBlockingStub stub = UserServiceGrpc.newBlockingStub(channel);

            // ── ② 对照组：Redis 可达 ⇒ 先把连接【建立起来】并成功写一次 ──
            //    ⚠️ **这一步是必需的，不是多余的**（2026-10-28 第一版 B 组漏了它 ⇒ ⇒ 测出来的东西是错的）：
            //    漏掉 ⇒ Lettuce **从未建立过连接** ⇒ ⇒ 断连后发的第一条命令是「**连接被拒**」
            //    （proxy 监听口已关 ⇒ connection refused ⇒ **~15ms 快失败**）
            //    ⇒ ⇒ 那样测的是「连不上」，**不是**「连上之后被断开」⇒ ⇒ `disconnectedBehavior` 根本不起作用
            //    ⇒ ⇒ 第一版 B 组实测就是 `elapsedMs=15 · INTERNAL`（= 快失败）⇒ ⇒ 假象。
            //    有了这一步 ⇒ ⇒ 连接【已建立】⇒ ⇒ 断连后走的是「已连接 → 被 FIN」那条路
            //    ⇒ ⇒ 这才是 `disconnectedBehavior` 起作用的场景（与 A 组同口径）。
            stub.logout(LogoutReq.newBuilder().setToken("g1-probe-b-reachable").build());

            // ── ③ 制造断连（FIN 形态，与 A 组同款代理、同一份实现）──
            PROXY.disconnectGracefully();

            // ── ③ 实验组：客户端可见的 status ──
            long t0 = System.nanoTime();
            StatusRuntimeException err = assertThrows(StatusRuntimeException.class,
                    () -> stub.logout(LogoutReq.newBuilder().setToken("g1-probe-default-behavior").build()));
            long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;

            System.out.printf("[B 组 · DEFAULT 断连行为] code=%s description=%s elapsedMs=%d%n",
                    err.getStatus().getCode(), err.getStatus().getDescription(), elapsedMs);

            // ── ④ 判据 1：必须是 DEADLINE_EXCEEDED（1015），而不是 INTERNAL ──
            //    ⇒ 这是「A 组的 INTERNAL 是 REJECT_COMMANDS 换来的」这一归因的【反事实证据】
            assertEquals(Status.Code.DEADLINE_EXCEEDED, err.getStatus().getCode(),
                    "DEFAULT 断连行为下命令被排队 ⇒ 撞上 2s rpc 预算 ⇒ 客户端必须是 DEADLINE_EXCEEDED(1015)"
                            + " ⇒ 实测 code=" + err.getStatus().getCode()
                            + " description=" + err.getStatus().getDescription()
                            + " elapsedMs=" + elapsedMs
                            + " ⇒ ⚠️ 若实测是 INTERNAL ⇒ 说明本组的「唯一变量」没生效 ⇒ ⇒ 本组无效");

            // ── ⑤ 判据 2：耗时必须【接近预算】⇒ 证明「排队」而不是「快速失败」真的发生了 ──
            assertTrue(elapsedMs >= 1500L,
                    "DEFAULT 下命令被排队 ⇒ 必然等到 rpc 预算（2s）才返回 ⇒ 实测 " + elapsedMs
                            + "ms ⇒ ⇒ 若与 A 组一样是 ~0ms ⇒ 说明命令根本没被排队 ⇒ ⇒ 判据失效");
        } finally {
            channel.shutdownNow();
            channel.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static int pickFreePort() {
        // ⚠️ 原来用 `new ServerSocket(0)`：那是 **OS 盲选口**（macOS 临时段 49152–65535）
        //    ⇒ 与 Docker 随机发布口**同段** ⇒ 「挑到没人监听的口」这个前提**直接失效**
        //    ⇒ 邻居占 127.0.0.1:P 时我们的通配绑定照样成功、lsof 两个监听者并存
        //    ⇒ 客户端被更具体的那个抢走 ⇒ **静默假红**（机制与实测见 support/RealGrpcPort）。
        return RealGrpcPort.pick();
    }
}

// ⚠️ **必须显式写 dns:/// scheme**：Nacos discovery 在全局 NameResolverRegistry 里
//    注册了一个高优先级 provider，会把任何无 scheme 的 target 当作 serviceId 去 Nacos 拉实例。
//    本测试要的是**直连**的裸 channel，显式 scheme 即可绕开服务发现。
//    （2026-10-31 · G1 lane · P2-T04 转派单：原为 `.forAddress("127.0.0.1", GRPC_PORT)`
//      ⇒ 无 scheme ⇒ 会被 devh 的 discovery resolver 接管 ⇒ 把 127.0.0.1:P 当服务名去 Nacos 查
//      ⇒ UNAVAILABLE ⇒ 表现为「单跑绿、同跑红」。本段追加在**文件末尾** ⇒ 零行位移。）
