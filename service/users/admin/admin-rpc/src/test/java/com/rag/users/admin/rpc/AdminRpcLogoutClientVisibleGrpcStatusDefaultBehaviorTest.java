package com.rag.users.admin.rpc;

import com.rag.users.admin.proto.AdminServiceGrpc;
import com.rag.users.admin.proto.LogoutReq;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.lettuce.core.ClientOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientOptionsBuilderCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
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
import com.rag.users.admin.rpc.support.RealGrpcPort;

import static com.rag.users.admin.rpc.AdminRpcRedisDisconnectedFastFailTest.GracefulDisconnectProxy;
import static com.rag.users.admin.rpc.support.GoStyleHostPortPinning.pinContainerPort;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 **admin-rpc 的 B 组反例：断连策略 = {@code DEFAULT} ⇒ 客户端看到 1015（DEADLINE_EXCEEDED）而不是 INTERNAL**
 * （2026-10-28 · G1 lane）
 *
 * <h2>为什么要有 B 组</h2>
 * <p>{@code AdminRpcLogoutClientVisibleGrpcStatusTest}（A 组）证明了「admin-rpc 客户端看到的是
 * {@code INTERNAL}「注销失败,Redis写入异常」，与 Go 腿逐字同形」。
 * <p><b>但 A 组单独存在时证明不了「是那条机制决定的」</b> ⇒ ⇒ 本类提供<b>同场 B 组</b>：
 * <b>唯一变量 = 断连策略</b>（{@code REJECT_COMMANDS} → {@code DEFAULT}）⇒ ⇒ 归因强度才成立。
 *
 * <h2>怎么做到「唯一变量」</h2>
 * <p>共享层 {@code GoStyleRedisClientAutoConfiguration} 的 bean 带 {@code @ConditionalOnMissingBean}
 * ⇒ ⇒ <b>域侧一旦已定义同类型的 bean，共享层那份根本不会创建</b>（是「遮蔽」不是「叠加」）
 * ⇒ ⇒ 本类用一个 <b>test 侧</b>的 {@code @TestConfiguration} 提供一份值设成 {@code DEFAULT} 的 customizer
 * ⇒ ⇒ 其余全部不变（同款代理 / 同款故障形态 / 同款测点 / 同款 server 与客户端）。
 *
 * <h2>⚠️ 这个 B 组不会踩 G4 的源扫守门</h2>
 * <p>该守门扫的是各模块的 <b>{@code src/main/java}</b> 那一层 ⇒ ⇒ <b>{@code src/test/java} 不在扫描面</b>
 * ⇒ ⇒ 守门在我域仍绿。
 *
 * <h2>⚠️ 本类的「变红能力」是内建的</h2>
 * <p>把下面那个 bean 的 {@code @Bean} 摘掉 ⇒ ⇒ 共享层那份 {@code REJECT_COMMANDS} 接管
 * ⇒ ⇒ 客户端会看到 INTERNAL ⇒ ⇒ <b>本类必红</b> ⇒ ⇒ 不需要额外的变异注入就能证明它不是恒真。
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
        classes = {
                AdminRpcApplication.class,
                AdminRpcLogoutClientVisibleGrpcStatusDefaultBehaviorTest.DefaultDisconnectedBehaviorConfig.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
@Testcontainers
class AdminRpcLogoutClientVisibleGrpcStatusDefaultBehaviorTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("admin_rpc_it")
                    .withUsername("rag")
                    .withPassword("rag")
                    .withCreateContainerCmdModifier(cmd -> pinContainerPort(cmd, 5432));

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine")
                    .withExposedPorts(6379)
                    .withCreateContainerCmdModifier(cmd -> pinContainerPort(cmd, 6379));

    /** 与本模块的 A 组**共用同一份**代理实现 ⇒ ⇒ 故障形态必然同款。 */
    static final GracefulDisconnectProxy PROXY = GracefulDisconnectProxy.create(() -> {
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
    private ApplicationContext applicationContext;

    /** <b>唯一变量</b>：断连策略 = {@code DEFAULT}（遮蔽共享层那份 {@code REJECT_COMMANDS}）。 */
    @TestConfiguration
    static class DefaultDisconnectedBehaviorConfig {

        @Bean
        LettuceClientOptionsBuilderCustomizer defaultDisconnectedBehaviorCustomizer() {
            return builder -> builder.disconnectedBehavior(ClientOptions.DisconnectedBehavior.DEFAULT);
        }
    }

    @Test
    @DisplayName("🔴 admin-rpc B 组：断连策略=DEFAULT ⇒ 命令被排队 ⇒ 2s rpc 预算先到 ⇒ 客户端看到 DEADLINE_EXCEEDED(1015)")
    void logoutClientSeesDeadlineExceededWhenCommandsAreQueued() throws Exception {
        // ── ① 前置：确认「遮蔽真的发生了」──
        Map<String, LettuceClientOptionsBuilderCustomizer> customizers =
                applicationContext.getBeansOfType(LettuceClientOptionsBuilderCustomizer.class);
        assertEquals(1, customizers.size(),
                "容器里必须恰好一份 LettuceClientOptionsBuilderCustomizer ⇒ 实测 " + customizers.keySet());
        ClientOptions options = connectionFactory.getClientConfiguration().getClientOptions().orElse(null);
        assertEquals(ClientOptions.DisconnectedBehavior.DEFAULT,
                options == null ? null : options.getDisconnectedBehavior(),
                "断连行为必须是 DEFAULT（这才是本组的唯一变量）");

        ManagedChannel channel = ManagedChannelBuilder
                .forTarget("dns:///127.0.0.1:" + GRPC_PORT)
                .usePlaintext()
                .build();
        try {
            AdminServiceGrpc.AdminServiceBlockingStub stub = AdminServiceGrpc.newBlockingStub(channel);

            // ── ② 对照组：先建立连接并成功写一次 ──
            //    ⚠️ 必需：漏掉 ⇒ Lettuce 从未建连 ⇒ 断连后第一条命令是「连接被拒」（~15ms 快失败）
            //    ⇒ ⇒ 测的是「连不上」而不是「连上后被断开」⇒ ⇒ `disconnectedBehavior` 根本不起作用。
            stub.logout(LogoutReq.newBuilder().setToken("g1-probe-b-reachable").build());

            // ── ③ 制造断连（FIN 形态，与 A 组同款代理）──
            PROXY.disconnectGracefully();

            // ── ④ 实验组 ──
            long t0 = System.nanoTime();
            StatusRuntimeException err = assertThrows(StatusRuntimeException.class,
                    () -> stub.logout(LogoutReq.newBuilder().setToken("g1-probe-default-behavior").build()));
            long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;

            System.out.printf("[B 组 · admin-rpc · DEFAULT 断连行为] code=%s description=%s elapsedMs=%d%n",
                    err.getStatus().getCode(), err.getStatus().getDescription(), elapsedMs);

            assertEquals(Status.Code.DEADLINE_EXCEEDED, err.getStatus().getCode(),
                    "DEFAULT 断连行为下命令被排队 ⇒ 撞上 2s rpc 预算 ⇒ 客户端必须是 DEADLINE_EXCEEDED(1015)"
                            + " ⇒ 实测 code=" + err.getStatus().getCode()
                            + " description=" + err.getStatus().getDescription()
                            + " elapsedMs=" + elapsedMs);
            assertTrue(elapsedMs >= 1500L,
                    "DEFAULT 下命令被排队 ⇒ 必然等到 rpc 预算（2s）才返回 ⇒ 实测 " + elapsedMs + "ms");
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
