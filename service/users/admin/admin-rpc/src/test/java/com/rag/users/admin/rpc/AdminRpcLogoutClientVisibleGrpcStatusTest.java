package com.rag.users.admin.rpc;

import com.rag.users.admin.proto.AdminServiceGrpc;
import com.rag.users.admin.proto.LogoutReq;
import com.rag.users.admin.proto.LogoutResp;
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
import com.rag.users.admin.rpc.support.RealGrpcPort;

import static com.rag.users.admin.rpc.AdminRpcRedisDisconnectedFastFailTest.GracefulDisconnectProxy;
import static com.rag.users.admin.rpc.support.GoStyleHostPortPinning.pinContainerPort;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 **admin-rpc 的 A 组：Redis 断连 ⇒ 客户端可见的 gRPC status 必须是 INTERNAL「注销失败,Redis写入异常」**
 * （2026-10-28 · G1 lane）
 *
 * <h2>为什么 admin 域要单独做一遍</h2>
 * <p>{@code DRIFT-ARPC-013} 与 {@code DRIFT-USERRPC-V-014} 是**同机制、不同域** ⇒ ⇒
 * 而 <b>admin 与 user 的真源 logout 是【两份独立实现】</b>（黑名单前缀不同 · 各自的 `AccessExpire`）
 * ⇒ ⇒ **user 域的结论不能外推到 admin 域** ⇒ ⇒ admin 侧必须自己跑一遍（这也是 §48.1 给 admin 单独做
 * {@code probe-admin} 的同一条理由）。
 *
 * <h2>本类与 user 域那份的关系</h2>
 * <p>形状同款（真 gRPC server ＋ 真客户端 ＋ FIN 代理），但**落在不同模块** ⇒ ⇒
 * <b>各用自己的模块内那份代理实现</b>（admin 模块用 {@code AdminRpcRedisDisconnectedFastFailTest} 里的那份）
 * ⇒ ⇒ **同一次 A/B 对照内部必须是同一份实现**（这条纪律见 users/vs-common.md §50.2 ④）。
 *
 * <h2>⚠️ admin-rpc 的 gRPC 端口在 yaml 里是 8081</h2>
 * <p>本机 8081/8889/8082 <b>有别人的进程</b> ⇒ ⇒ 必须覆盖 ⇒ ⇒ 本类用<b>运行时挑的空闲口</b>
 * ⇒ ⇒ 挑不到 ⇒ <b>上下文起不来 ⇒ 大声失败</b>。
 *
 * <h2>判据</h2>
 * <ol>
 *   <li>对照组（Redis 可达）：logout 成功，且黑名单**真的写进 Redis**（key = {@code admin:jwt_blacklist:<token>}）
 *       ⇒ ⇒ 否则后面的「失败」可能只是「根本没连上」这种假失败；</li>
 *   <li>实验组（FIN 断开）：客户端 status 必须 = {@code INTERNAL} ＋ 「注销失败,Redis写入异常」
 *       ⇒ ⇒ <b>与 Go 腿逐字同形</b>（Go 腿实测：{@code grpc_code=Internal grpc_message="注销失败,Redis写入异常"}）；</li>
 *   <li>耗时必须 ≪ 2s rpc 预算 ⇒ ⇒ 证明「业务异常先到、不是超时先到」。</li>
 * </ol>
 *
 * <p>⚠️ <b>不要断言 {@code success == true}</b>：Go 真源
 * {@code service/user/admin/rpc/internal/logic/logoutlogic.go} 同样是 {@code return &pb.LogoutResp{}, nil}
 * ⇒ ⇒ {@code success} 恒为 proto3 默认 <b>false</b> ⇒ ⇒ RAG 侧不 setSuccess <b>与 Go 逐字同形</b>
 * ⇒ ⇒ <b>改成 true 反而制造漂移</b>。
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
        classes = AdminRpcApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
@Testcontainers
class AdminRpcLogoutClientVisibleGrpcStatusTest {

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

    /** 与本模块的 B 组**共用同一份**代理实现（admin 模块内那份嵌套类）⇒ ⇒ 故障形态必然同款。 */
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
    private StringRedisTemplate redisTemplate;

    @Test
    @DisplayName("🔴 admin-rpc：Redis 断连 ⇒ 客户端可见的 gRPC status 必须是 INTERNAL「注销失败,Redis写入异常」（与 Go 腿逐字同形）")
    void logoutClientSeesInternalWhenRedisDisconnected() throws Exception {
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress("127.0.0.1", GRPC_PORT)
                .usePlaintext()
                .build();
        try {
            AdminServiceGrpc.AdminServiceBlockingStub stub = AdminServiceGrpc.newBlockingStub(channel);

            // ── ① 对照组：先建立连接并成功写一次（否则后面测的是「连不上」而不是「连上后被断开」）──
            LogoutResp ok = stub.logout(LogoutReq.newBuilder().setToken("g1-probe-reachable").build());
            assertFalse(ok.getSuccess(),
                    "Go 真源 admin logoutlogic 返回 `&pb.LogoutResp{}` ⇒ success 恒为 proto3 默认 false"
                            + " ⇒ RAG 侧必须同样不设 ⇒ ⚠️ 谁改成 true 谁就制造了一条新漂移");
            String written = redisTemplate.opsForValue().get("admin:jwt_blacklist:g1-probe-reachable");
            assertEquals("banned", written,
                    "对照组必须证明黑名单真的写进 Redis 了（admin 域前缀 = admin:jwt_blacklist:）");

            // ── ② 制造断连（FIN 形态，与本模块 B 组同款代理）──
            PROXY.disconnectGracefully();

            // ── ③ 实验组：客户端可见的 status ──
            long t0 = System.nanoTime();
            StatusRuntimeException err = assertThrows(StatusRuntimeException.class,
                    () -> stub.logout(LogoutReq.newBuilder().setToken("g1-probe-disconnected").build()));
            long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;

            System.out.printf("[A 组 · admin-rpc · REJECT_COMMANDS] code=%s description=%s elapsedMs=%d%n",
                    err.getStatus().getCode(), err.getStatus().getDescription(), elapsedMs);

            assertEquals(Status.Code.INTERNAL, err.getStatus().getCode(),
                    "Redis 断连时客户端必须看到 INTERNAL（与 Go 腿逐字同形）"
                            + " ⇒ 实测 code=" + err.getStatus().getCode()
                            + " description=" + err.getStatus().getDescription()
                            + " elapsedMs=" + elapsedMs
                            + " ⇒ ⚠️ 若实测是 DEADLINE_EXCEEDED/1015 ⇒ 2s 超时预算先到 ⇒ ⇒ 断连策略没生效");
            assertEquals("注销失败,Redis写入异常", err.getStatus().getDescription(),
                    "描述必须与 Go 腿逐字相同（Go 实测 grpc_message=\"注销失败,Redis写入异常\"）");
            assertTrue(elapsedMs < 2000L,
                    "失败调用必须在 rpc 预算（2000ms）内返回 ⇒ 实测 " + elapsedMs + "ms");
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
