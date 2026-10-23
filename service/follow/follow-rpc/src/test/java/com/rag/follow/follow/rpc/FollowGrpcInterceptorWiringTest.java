package com.rag.follow.follow.rpc;

import com.rag.follow.follow.proto.BaseResp;
import com.rag.follow.follow.proto.FollowServiceGrpc;
import com.rag.follow.follow.proto.RelationReq;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.server.serverfactory.GrpcServerConfigurer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 「gRPC interceptor 真的挂到 server 上了吗」的<b>运行期</b>证据 —— AGENTS.md §3.3 的核心命题。
 *
 * <h2>为什么必须起一个真 server 来测</h2>
 * <p>devh 的 {@code grpc-server-spring-boot-starter} 3.1.0 <b>不会自动</b>把用户提供的
 * {@code ServerInterceptor} Bean 挂到 server 上（message-rpc 在 DRIFT-MSG-003 上踩过，
 * like-rpc 在 {@code LikeGrpcInterceptorWiringTest} 上补了同类用例）。
 * 若只断言「容器里有 {@code ServerInterceptor} Bean」，删掉
 * {@code FollowRpcApplication#rpcInterceptorConfigurer} 之后断言<b>照样是绿的</b>，
 * 而超时 / deadline 传播 / gRPC span 已全部静默消失 ——
 * 这正是 §3.3「断言 Bean 存在 ≠ 断言机制生效」的反面教材。
 *
 * <p>所以本类起一个<b>真实 gRPC server</b>，用容器里那个 {@link GrpcServerConfigurer} 装配它，
 * 注册一个「故意睡 10 秒」的服务，然后用 5 秒的客户端 deadline 去调：
 * <ul>
 *   <li>若服务端超时生效 ⇒ <b>约 300ms</b> 就返回 {@code DEADLINE_EXCEEDED}
 *       （远早于客户端的 5 秒）；</li>
 *   <li>若 interceptor 没挂上 ⇒ 要等满 5 秒才由<b>客户端</b> deadline 超时返回。</li>
 * </ul>
 * 两者返回的 status 码<b>都是</b> {@code DEADLINE_EXCEEDED}，因此必须再断言<b>耗时</b>
 * 才能区分 —— 这正是「只断言结果不看机制」会漏掉的地方。
 */
@SpringBootTest(
        classes = FollowRpcApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "grpc.server.port=0",
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN",
                "grpc.client.user-rpc.address=static://127.0.0.1:1",
                // 压到 300ms 让「服务端超时」与「客户端 5s deadline」的差距足够大、好区分。
                // 生产值 2s 由下面 productionTimeoutMustMatchGoZeroDefault 单独断言。
                "observability.rpc-timeout=300ms"
        })
@Testcontainers
@org.testcontainers.junit.jupiter.EnabledIfDockerAvailable
class FollowGrpcInterceptorWiringTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("follow_rpc_interceptor_it")
                    .withUsername("rag")
                    .withPassword("rag");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private ApplicationContext ctx;

    @Test
    @DisplayName("运行期：服务端超时由容器里的 GrpcServerConfigurer 真正施加到 server 上")
    void serverSideTimeoutIsActuallyEnforced() throws Exception {
        // ⚠️ 必须按 Bean 名取：devh starter 自己还注册了 compressionServerConfigurer /
        //    decompressionServerConfigurer 两个同类型 Bean，ctx.getBean(GrpcServerConfigurer.class)
        //    会抛 NoUniqueBeanDefinitionException。我们要验的只有本模块自己那个。
        GrpcServerConfigurer configurer = ctx.getBean("rpcInterceptorConfigurer", GrpcServerConfigurer.class);
        assertNotNull(configurer, "rpcInterceptorConfigurer 必须产出 GrpcServerConfigurer Bean");

        ServerBuilder<?> builder = ServerBuilder.forPort(0);
        configurer.accept(builder);                  // ← 被测的那一行（接口其实是 Consumer<ServerBuilder<?>>）
        Server server = builder.addService(slowFollowService()).build().start();

        // ⚠️ **必须显式写 dns:/// scheme**：Nacos discovery 在全局 NameResolverRegistry 里
        //    注册了一个高优先级 provider，会把任何无 scheme 的 target（包括
        //    "127.0.0.1:61509"）当作 **serviceId** 去 Nacos 拉实例 ⇒
        //      UNAVAILABLE / "Can not get hosts from nacos server. serviceId: 127.0.0.1:61509"
        //    （实测连底层 shaded NettyChannelBuilder 也拦得住 —— 劫持发生在 NameResolver 层，
        //     不在 ManagedChannelBuilder 这一层。）
        //    本测试要的是**直连**的裸 channel，显式 scheme 即可绕开服务发现。
        ManagedChannel channel = io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
                .forTarget("dns:///127.0.0.1:" + server.getPort()).usePlaintext().build();
        try {
            var stub = FollowServiceGrpc.newBlockingStub(channel);
            long startNanos = System.nanoTime();
            StatusRuntimeException e = assertThrows(StatusRuntimeException.class,
                    () -> stub.withDeadlineAfter(5, TimeUnit.SECONDS).follow(
                            RelationReq.newBuilder().setUserId(1).setTargetId(2).build()),
                    "睡 10 秒的服务必须被超时打断");
            long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;

            assertEquals(Status.Code.DEADLINE_EXCEEDED, e.getStatus().getCode(),
                    "实际 status=" + e.getStatus().getCode()
                            + " description=" + e.getStatus().getDescription()
                            + " cause=" + e.getStatus().getCause());
            assertTrue(elapsedMillis < 3_000,
                    "必须在服务端超时（300ms）附近返回，实际耗时 " + elapsedMillis
                            + "ms。若接近 5000ms，说明只有客户端 deadline 生效 —— "
                            + "即 server.intercept(...) 没挂上，超时/deadline 传播/gRPC span 已静默消失。");
        } finally {
            channel.shutdownNow();
            server.shutdownNow();
        }
    }

    @Test
    @DisplayName("配置：生产 application.yml 的 observability.rpc-timeout 必须是 2s（go-zero 默认 2000ms）")
    void productionTimeoutMatchesGoZeroDefault() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/application.yml")) {
            assertNotNull(in, "application.yml 必须随 jar 打包");
            String yml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(yml.contains("rpc-timeout: 2s"),
                    "Go 侧 follow.yaml 未配 Timeout ⇒ 走 go-zero zrpc/config.go 的 default=2000ms；"
                            + "Java 侧必须同样是 2s，否则超时语义与 Go 不一致");
            assertTrue(yml.contains("table: flyway_schema_history_follow"),
                    "账本表名隔离是 §6.3 问题 4 的定案，不能被改回默认");
        }
    }

    /** 注册一个「处理函数睡 10 秒」的服务 —— 用来观察超时是否真的生效。 */
    private static ServerServiceDefinition slowFollowService() {
        ServerCallHandler<RelationReq, BaseResp> slowHandler = new ServerCallHandler<RelationReq, BaseResp>() {
            @Override
            public ServerCall.Listener<RelationReq> startCall(ServerCall<RelationReq, BaseResp> call,
                                                              Metadata headers) {
                return new ServerCall.Listener<RelationReq>() {
                    @Override
                    public void onHalfClose() {
                        try {
                            Thread.sleep(10_000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        // ⚠️ grpc-java 1.63 的 ServerCall.close 签名是 (Status, Metadata) ——
                        //    响应体必须先 sendMessage，再 close 传空 trailers。
                        call.sendHeaders(new Metadata());
                        call.sendMessage(BaseResp.getDefaultInstance());
                        call.close(Status.OK, new Metadata());
                    }
                };
            }
        };
        // ⚠️ 两条 grpc-java 的约束同时踩到过（各一轮红）：
        //    ① 必须用生成代码里的**同一个** getFollowMethod() 实例，否则报
        //       "Bound method ... not same instance as method in service descriptor"；
        //    ② 描述符里的每个方法都必须有绑定，否则报
        //       "No method bound for descriptor entry follow.FollowService/Unfollow"。
        //    ⇒ 自建一个只含 Follow 这一个方法的 ServiceDescriptor，方法仍取生成代码的实例。
        io.grpc.ServiceDescriptor onlyFollow = io.grpc.ServiceDescriptor
                .newBuilder(FollowServiceGrpc.getServiceDescriptor().getName())
                .addMethod(FollowServiceGrpc.getFollowMethod())
                .build();
        return ServerServiceDefinition.builder(onlyFollow)
                .addMethod(FollowServiceGrpc.getFollowMethod(), slowHandler)
                .build();
    }
}