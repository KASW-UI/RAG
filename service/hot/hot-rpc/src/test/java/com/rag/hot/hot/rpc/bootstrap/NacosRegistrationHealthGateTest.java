package com.rag.hot.hot.rpc.bootstrap;

import com.alibaba.cloud.nacos.NacosServiceManager;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.rag.hot.hot.rpc.HotRpcApplication;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse.ServingStatus;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.protobuf.services.HealthStatusManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * 🔴 <b>「注册失败 ⇒ 健康检查 NOT_SERVING」</b>的守门测试（DRIFT-HOTRPC-010 的配套）。
 *
 * <h2>为什么必须有这条</h2>
 * <p>Go 侧注册 etcd 失败会 <b>{@code panic} 退出进程</b>（{@code zrpc/server.go:88-93}）⇒ 探针连不上。
 * RAG 侧用户 2026-10-24 拍板<b>保持进程存活</b>（崩进程不是想要的语义），
 * 但那就必须补上「<b>注册失败 ⇒ 探针失败</b>」，否则就成了
 * 「注册不上却一切正常」的<b>假活</b>：进程在、端口在、服务发现里没有它。
 *
 * <h2>断言的是<b>真的 gRPC health 协议</b>，不是内部字段</h2>
 * <p>本测试用 {@code HealthGrpc} 客户端通过 <b>in-process gRPC 通道</b>真的发一次
 * {@code Check} ⇒ 断言的是「探针实际会看到什么」，
 * 而不是「某个 Java 字段被改成了什么」。
 * ⚠️ 若只断言内部标志位，{@code setStatus} 根本没生效也会绿。
 *
 * <h2>Go 出处</h2>
 * <ul>
 *   <li>{@code zrpc/internal/server.go:36-38} —— {@code h = health.NewServer()}</li>
 *   <li>{@code zrpc/internal/rpcserver.go:61-63} —— {@code RegisterHealthServer} + {@code Resume()}</li>
 *   <li>{@code zrpc/server.go:88-93} —— 注册失败 ⇒ {@code panic} ⇒ 进程退出</li>
 * </ul>
 */
@SpringBootTest(
        classes = HotRpcApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "grpc.server.port=22500",
                // 绕过 Nacos：@GrpcClient 在 Bean 初始化期就建 channel（不是懒建）
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN",
                // 绕过 Kafka：listener 容器在启动时就要造 consumer，bootstrap 解析不了会直接起不来
                "spring.kafka.bootstrap-servers=127.0.0.1:1",
                // 绕过 Redis：Lettuce 连接懒建，不真正连
                "spring.data.redis.host=127.0.0.1",
                "spring.data.redis.port=1"
        })
class NacosRegistrationHealthGateTest {

    @MockitoBean
    NacosServiceManager nacosServiceManager;

    @MockitoBean
    NamingService namingService;

    @Autowired
    NacosRegistration registration;

    @Autowired
    HealthStatusManager healthStatusManager;

    /**
     * 让注册**确定失败**，再显式跑一次注册。
     *
     * <p>⚠️ 为什么要在 {@code @BeforeEach} 里显式再调一次：
     * {@code register()} 监听的是 {@code ApplicationReadyEvent}，它在<b>上下文启动时</b>就触发过
     * （那时 mock 还没打桩 ⇒ 走的是 NPE 分支）。
     * 显式重调一次 ⇒ 断言的对象是「<b>Nacos 明确拒绝注册</b>」这条路径，与启动顺序无关。
     */
    @BeforeEach
    void makeRegistrationFail() throws Exception {
        when(nacosServiceManager.getNamingService()).thenReturn(namingService);
        doThrow(new NacosException(500, "simulated nacos unavailable"))
                .when(namingService).registerInstance(anyString(), anyString(), any(Instance.class));
        registration.register();
    }

    @Test
    @DisplayName("🔴 注册失败 ⇒ gRPC 整体健康检查报 NOT_SERVING（探针必须看得到失败）")
    void registrationFailureIsVisibleToGrpcHealthProbe() throws java.io.IOException {
        assertFalse(registration.registrationHealthy(),
                "注册失败了却认为自己是健康的 ⇒ 假活");

        assertEquals(ServingStatus.NOT_SERVING, checkOverall(),
                "gRPC 整体健康检查没报 NOT_SERVING ⇒ 探针看不到注册失败"
                        + "（Go 侧此时进程已退出、探针连不上；RAG 侧必须用状态表达同一结果）");
    }

    @Test
    @DisplayName("🔴 只登记整体状态（与 Go 同款）⇒ 查具体服务名仍是 NOT_FOUND，不造 Go 没有的行为")
    void onlyOverallStatusIsRegisteredLikeGo() throws java.io.IOException {
        // Go 侧 health.NewServer() 只在 statusMap 里放 "" 这一条（构造时 SERVING、Resume() 也只改它）
        // ⇒ 查任何**具体**服务名一律 NOT_FOUND。
        // RAG 侧若额外登记 "hot-rpc"，就造出了 Go 没有的「查具体服务名能拿到状态」这一行为 ⇒ 不可做。
        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () -> check("hot-rpc"),
                "查具体服务名竟然返回了状态 ⇒ 多登记了 Go 侧没有的条目");
        assertEquals(Status.Code.NOT_FOUND, e.getStatus().getCode(),
                "查未登记的服务名应当是 NOT_FOUND（与 Go 侧一致）");
    }

    // ==================================================================
    // helpers
    // ==================================================================

    /** 真的发一次 grpc.health.v1.Health/Check（service 名 = 整体）。 */
    private ServingStatus checkOverall() throws java.io.IOException {
        return check(HealthStatusManager.SERVICE_NAME_ALL_SERVICES);
    }

    /**
     * 用 in-process gRPC 通道真的查一次健康状态。
     *
     * <p>⚠️ 必须走真实协议而不是读 {@code HealthStatusManager} 的内部 map ——
     * {@code HealthStatusManager} **没有**公开的状态 getter，
     * 而「探针实际会看到什么」只有走一遍协议才知道。
     */
    private ServingStatus check(String serviceName) throws java.io.IOException {
        String target = InProcessServerBuilder.generateName();
        Server server = InProcessServerBuilder.forName(target)
                .directExecutor()
                .addService(healthStatusManager.getHealthService())
                .build()
                .start();
        ManagedChannel channel = InProcessChannelBuilder.forName(target).directExecutor().build();
        try {
            return HealthGrpc.newBlockingStub(channel)
                    .check(HealthCheckRequest.newBuilder().setService(serviceName).build())
                    .getStatus();
        } finally {
            channel.shutdownNow();
            server.shutdownNow();
        }
    }
}
