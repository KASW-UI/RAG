package com.rag.users.user.api;

import com.rag.users.user.api.client.UserRpcClient;
import com.rag.users.user.proto.GetUserReq;
import com.rag.users.user.proto.GetUserResp;
import com.rag.users.user.proto.UserInfo;
import com.rag.users.user.proto.UserServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.client.config.GrpcChannelsProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.TimeUnit;
import com.rag.users.user.api.support.RealGrpcPort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DRIFT-NEW-014 回归网：user-api 的 gRPC 客户端必须与 plaintext 的 user-rpc 握手。
 *
 * <p><b>这个 bug 是怎么被抓住的</b>：不是静态审查，是 {@code tools/difftest} 的双端对拍。
 * user-api 发的是 TLS ClientHello，user-rpc 的 netty 打出
 * {@code Http2Exception: HTTP/2 client preface string missing or corrupt.
 * Hex dump: 16030301...}（{@code 0x16} = TLS handshake record），
 * 所有经 user-rpc 的业务码塌成 {@code 1015 服务繁忙}。
 *
 * <p><b>为什么「配置文件里有没有那行字」不算测试</b>（§3.3）：
 * devh 的 {@code GrpcChannelProperties.DEFAULT_NEGOTIATION_TYPE} 静态初始化成
 * {@code TLS}（字节码 offset 33-36），且 {@code getNegotiationType()} 在字段为 null 时
 * <b>回落到该默认值</b>（同文件 offset 1-10）。也就是说这行配置
 * <b>不写不会报错、只会静默变成 TLS</b> —— 正是 §3.3 点名的
 * 「凭注解/配置名称断定机制已启用」的那一类静默失效。
 * 所以断言必须是：<b>用真实配置把真实 channel 建起来，打到真实 server 上，真调通一次。</b>
 *
 * <p><b>走的是生产代码路径，不是手工复刻映射</b>：
 * 把 {@code grpc.client.user-rpc.address} 覆盖成 {@code static://127.0.0.1:<端口>}，
 * 然后用容器里那个<b>真实的</b> {@link UserRpcClient} 发请求 ——
 * channel 由 devh 的 {@code ShadedNettyChannelFactory} 按真实配置建出，
 * 不存在「测试里写死 plaintext 所以测不出问题」的可能。
 *
 * <p>配套的负向对照 {@link #tlsChannelAgainstSamePlaintextServerFails()} 用 TLS
 * 直连同一个 server 并断言失败，证明这个 server 确实只收明文、
 * 正向用例不是「怎么都绿」的假绿。
 *
 * <p><b>Go 出处</b>：Go 侧 user-rpc 是纯 gRPC 明文 ——
 * {@code service/user/user/rpc/user.go:36} 的 {@code zrpc.MustNewServer} 全仓无 TLS 配置，
 * 故 Java 客户端必须同样走明文。
 * 对照 admin-api/application.yml：它<b>已经有</b> {@code negotiation-type: plaintext}
 * 并带 DRIFT-NEW-008 注释；当年 admin 侧踩过并修了，user 侧单纯被漏掉。
 */
@SpringBootTest(
        classes = UserApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                // 单测环境没有 Nacos，user-api 也没有自定义 NacosRegistration
                // （只有 rpc 模块有，它构造注入 NacosServiceManager bean，
                //  所以这里的 enabled=false 不会让上下文起不来）。
                // 关掉之后 composite discovery 只剩 SimpleDiscoveryClient，
                //  由 @DynamicPropertySource 注册的静态实例兜底 ——
                // 否则 devh 会先拿 NacosDiscoveryClient 试一次、打一串
                // 「Can not get hosts from nacos server」的 WARN 再回退，
                // 虽然最终能通，但依赖的是兜底顺序，不是确定性行为。
                "spring.cloud.nacos.discovery.enabled=false",
                // 占位值：真实端口在 @DynamicPropertySource 里给。
                // PORT 是 static final 但由方法初始化，不是编译期常量，
                // 写不进注解；而 @DynamicPropertySource 的优先级高于
                // @SpringBootTest(properties)，所以这个占位值会被覆盖掉。
                "grpc.client.user-rpc.address=static://127.0.0.1:1",
                "spring.cloud.discovery.client.simple.instances.user-rpc[0].uri="
                        + "static://127.0.0.1:1"
        })
class UserApiGrpcNegotiationTest {

    /** 真实 plaintext gRPC server 的端口。静态初始化，早于任何 Spring 装配。 */
    private static final int PORT = freePort();

    private static final Server SERVER = startPlaintextServer();

    /** 观察请求是否真的到达 server，而不是被客户端本地短路。 */
    private static volatile boolean called;

    private static int freePort() {
        // ⚠️ 原来用 `new ServerSocket(0)`：那是 **OS 盲选口**（macOS 临时段 49152–65535）
        //    ⇒ 与 Docker 随机发布口**同段** ⇒ 「挑到没人监听的口」这个前提**直接失效**
        //    ⇒ 邻居占 127.0.0.1:P 时我们的通配绑定照样成功、lsof 两个监听者并存
        //    ⇒ 客户端被更具体的那个抢走 ⇒ **静默假红**（机制与实测见 support/RealGrpcPort）。
        return RealGrpcPort.pick();
    }

    /**
     * 真起一个 <b>plaintext</b> gRPC server。
     *
     * <p>{@link NettyServerBuilder} 不带任何 {@code sslContext} ⇒ 就是明文，
     * 与 {@code zrpc.MustNewServer} 的 Go 侧形态一致。
     */
    private static Server startPlaintextServer() {
        try {
            return NettyServerBuilder.forPort(PORT)
                    .addService(new UserServiceGrpc.UserServiceImplBase() {
                        @Override
                        public void getUser(GetUserReq request, StreamObserver<GetUserResp> obs) {
                            called = true;
                            obs.onNext(GetUserResp.newBuilder()
                                    .setFound(true)
                                    .setUser(UserInfo.newBuilder()
                                            .setUid(request.getUid())
                                            .setUsername("negotiation-ok")
                                            .build())
                                    .build());
                            obs.onCompleted();
                        }
                    })
                    .build()
                    .start();
        } catch (IOException e) {
            throw new IllegalStateException("起 plaintext gRPC server 失败", e);
        }
    }

    /**
     * 把 devh 的下游地址指向本测试起的真 server。
     *
     * <p>用 {@code static://} 而不是 {@code discovery:///}：静态地址不依赖任何注册中心，
     * 断言的就只剩「协商方式对不对」这一个变量。
     * 两处都要写（application 层的 grpc.client.*.address 与 SimpleDiscoveryClient 实例），
     * 因为 devh 的 {@code NamedChannelFactory} 会按 scheme 选 name resolver，
     * 解析时又会走 composite discovery，两条路径都得指向同一个静态地址。
     */
    @DynamicPropertySource
    static void downstream(DynamicPropertyRegistry registry) {
        registry.add("grpc.client.user-rpc.address",
                () -> "static://127.0.0.1:" + PORT);
    }

    @AfterAll
    static void stopServer() {
        SERVER.shutdownNow();
    }

    @BeforeEach
    void resetCalledFlag() {
        called = false;
    }

    @Autowired
    private UserRpcClient userRpcClient;

    @Autowired
    private GrpcChannelsProperties channels;

    @Test
    @DisplayName("绑定的协商方式必须显式是 plaintext，而不是 devh 的 TLS 默认值")
    void boundNegotiationTypeIsPlaintextNotTheTlsDefault() {
        // getNegotiationType() 在字段为 null 时回落到 DEFAULT_NEGOTIATION_TYPE = TLS，
        // 所以这里读到的就是 channel 真正会用的值，而不是「配置里写了什么」。
        assertEquals(
                net.devh.boot.grpc.client.config.NegotiationType.PLAINTEXT,
                channels.getChannel("user-rpc").getNegotiationType(),
                "grpc.client.user-rpc.negotiation-type 必须显式配成 plaintext；"
                        + "devh 的默认值是 TLS，漏配会静默变成 TLS 而不报错");
    }

    @Test
    @DisplayName("用真实配置把真实 channel 建起来，能与 plaintext user-rpc 打通一次真实调用")
    void realClientTalksToRealPlaintextServer() {
        GetUserResp resp = userRpcClient.userRpc()
                .getUser(GetUserReq.newBuilder().setUid(4242L).build());

        assertTrue(resp.getFound(), "服务端应正常受理");
        assertEquals(4242L, resp.getUser().getUid(), "请求应原样到达服务端");
        assertEquals("negotiation-ok", resp.getUser().getUsername());
        assertTrue(called, "调用必须真的到达 server，而不是被客户端本地短路");
    }

    @Test
    @DisplayName("负向对照：同一个 server 用 TLS 连必须失败（证明正向用例不是假绿）")
    void tlsChannelAgainstSamePlaintextServerFails() {
        // forAddress(String,int) 用默认 TLS 凭据；显式再钉一次 TLS，与漏配时的形态一致。
        ManagedChannel tlsChannel = NettyChannelBuilder
                .forAddress("127.0.0.1", PORT)
                .negotiationType(io.grpc.netty.shaded.io.grpc.netty.NegotiationType.TLS)
                .build();
        try {
            // TLS ClientHello 打到只收明文的 server ⇒ 失败。
            // 这正是生产上漏配 negotiation-type 时的形态
            // （服务端报 HTTP/2 client preface string missing）。
            assertThrows(RuntimeException.class, () ->
                            UserServiceGrpc.newBlockingStub(tlsChannel)
                                    .withDeadlineAfter(5, TimeUnit.SECONDS)
                                    .getUser(GetUserReq.newBuilder().setUid(1L).build()),
                    "plaintext server 不该接受 TLS 握手；"
                            + "若这条失败，说明 server 侧被误配成了 TLS，正向用例也就失去意义");
        } finally {
            tlsChannel.shutdownNow();
        }
    }
}