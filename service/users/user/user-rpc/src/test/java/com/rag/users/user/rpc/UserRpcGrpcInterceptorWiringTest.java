package com.rag.users.user.rpc;

import com.rag.users.user.proto.GetUserReq;
import com.rag.users.user.proto.GetUserResp;
import com.rag.users.user.proto.UserServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerCallHandler;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ServerCalls;
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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import com.rag.users.user.rpc.support.RealGrpcPort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.rag.users.user.rpc.support.GoStyleHostPortPinning.pinContainerPort;

/**
 * 🔴 <b>运行期守门</b>：user-rpc 的 gRPC 业务观测拦截器<b>真的</b>挂到了 server 上，
 * 且<b>服务端超时真的打断了「正在执行的处理」</b>。
 *
 * <h2>为什么 user-rpc 必须有这一条（此前是缺口）</h2>
 * <p>本域此前的 {@link GrpcInterceptorWiringTest} 只做<b>纯 Mockito</b> 的链拼装断言：
 * {@code mock(ServerBuilder.class)} → 捕获 {@code intercept(...)} 的入参 → 再把链
 * <b>手工</b>跑一遍 {@code interceptCall}。
 * <p>⚠️ 按 AGENTS.md §3.3，<b>纯 mock 单测不算「机制生效」的证据</b>：
 * 它证明的是「链的拼装代码写成了这样」，<b>证明不了</b>
 * 「{@code server.intercept(...)} 真的被 grpc-java 的 server 采纳并作用在<b>真实调用</b>上」。
 * <p>⇒ 本类起一个<b>真实 gRPC server</b>，用容器里那个 {@link GrpcServerConfigurer} 装配它，
 * 注册一个「处理函数一直不返回」的服务（用 latch 阻塞，不是 {@code sleep}），
 * 然后用 5 秒的客户端 deadline 去调：
 * <ul>
 *   <li>若服务端超时生效 ⇒ <b>约 300ms</b> 就返回 {@code DEADLINE_EXCEEDED}（远早于客户端的 5 秒）；</li>
 *   <li>若 interceptor 没挂上 ⇒ 要等满 5 秒才由<b>客户端</b> deadline 超时返回。</li>
 * </ul>
 * 两者返回的 status 码<b>都是</b> {@code DEADLINE_EXCEEDED}，因此必须再断言<b>耗时</b>才能区分。
 *
 * <h2>Go 出处（行为层，不是表达层）</h2>
 * <ul>
 *   <li>{@code service/user/user/rpc/user.go:32} → {@code rpcTimeout := observability.DisableNativeRpcTimeout(&c.RpcServerConf)}
 *       （user.yaml <b>未配</b> Timeout ⇒ 生效值来自 go-zero {@code zrpc/config.go} 的
 *       {@code Timeout int64 json:",default=2000"} = <b>2000ms</b>）</li>
 *   <li>{@code service/user/user/rpc/user.go:43} → {@code s.AddUnaryInterceptors(observability.NewUnaryServerInterceptor(rpcTimeout, observability.SlowThreshold()))}</li>
 * </ul>
 *
 * <h2>🔴 2026-10-25（第二十三轮 · global L2 · X-5 残留）：三条判别式断言缺一不可</h2>
 * <p>grpc-java <b>不会</b>向<b>裸</b> {@link ServerCallHandler} 派发 {@code onHalfClose}
 * ⇒ 用裸 handler 注册慢服务时，「处理函数一直不返回」这件事<b>从未发生</b>
 * ⇒ 客户端照样拿到（自己的）{@code DEADLINE_EXCEEDED} ⇒ 只断言 status 与耗时的用例<b>照样是绿的</b>
 * ⇒ 那是「服务端能取消一个<b>空调用</b>」，不是「服务端超时打断一个<b>正在执行</b>的处理」。
 * <p>⇒ 因此本类必须同时满足三条：
 * <ol>
 *   <li>用 {@link ServerCalls#asyncUnaryCall}（标准派发入口）注册慢服务 ⇒ handler 真的会被调用；</li>
 *   <li>断言「<b>handler 确实被调用过</b>」；</li>
 *   <li>断言「<b>取消发生时它仍在跑</b>」（不是处理早就结束了才收到取消）。</li>
 * </ol>
 * （探针证据与完整经过 ⇒ {@code docs/DRIFT/global/cross-domain.md} §二·八）
 *
 * <h2>为什么用 latch 而不是 {@code sleep}</h2>
 * <p>{@code Thread.sleep(10_000)} 方案下，「取消发生时它还在跑」这条断言<b>根本写不出来</b>
 *（等到断言时它早跑完了）⇒ 必须用 latch：{@code probe.release.await(30, SECONDS)}
 * ⇒ 测试在 {@code finally} 里<b>立刻</b>放行，且能<b>精确</b>观测时序。
 */
@SpringBootTest(
        classes = UserRpcApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                // grpc-server-spring-boot-starter 3.1.0 的 GrpcServerLifecycle 没有
                // @ConditionalOnProperty("grpc.server.enabled")，实测写 false 仍会起 server。
                "grpc.server.port=0",
                // 指到必然拒连的地址：NacosRegistration 捕获 NacosException 只记日志不中断启动。
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                // OTLP 指向不可达端点，只会在后台重试刷日志，关掉。
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN",
                // 绕过 Redis：Lettuce 连接懒建，不真正连。
                "spring.data.redis.host=127.0.0.1",
                "spring.data.redis.port=1",
                // ⚠️ 压到 300ms 让「服务端超时」与「客户端 5s deadline」的差距足够大、好区分。
                //    生产值 2s 由 productionTimeoutMatchesGoZeroDefault 单独断言。
                "observability.rpc-timeout=300ms",
                // ⚠️ 关掉 CPU 自适应降载，让本类的判据**只**取决于「业务观测拦截器有没有挂上」。
                //    否则链上的 shedding 层会先返回 RESOURCE_EXHAUSTED，把判据盖掉 ⇒ 变成 flaky。
                "rag.protection.cpu-shedding.enabled=false"
        })
@Testcontainers
class UserRpcGrpcInterceptorWiringTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("user_rpc_interceptor_it")
                    .withUsername("rag")
                    .withPassword("rag").withCreateContainerCmdModifier(cmd -> pinContainerPort(cmd, 5432));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private ApplicationContext ctx;

    @Test
    @DisplayName("运行期：服务端超时由容器里的 GrpcServerConfigurer 真正施加到 server 上"
            + "（Go: user.go:43 AddUnaryInterceptors）")
    void serverSideTimeoutIsActuallyEnforced() throws Exception {
        // ⚠️ 必须按 Bean 名取：devh starter 自己还注册了 compressionServerConfigurer /
        //    decompressionServerConfigurer 两个同类型 Bean，ctx.getBean(GrpcServerConfigurer.class)
        //    会抛 NoUniqueBeanDefinitionException。我们要验的只有本模块自己那个。
        GrpcServerConfigurer configurer = ctx.getBean("rpcInterceptorConfigurer", GrpcServerConfigurer.class);
        assertNotNull(configurer, "rpcInterceptorConfigurer 必须产出 GrpcServerConfigurer Bean");

        // ⚠️ 不能用 forPort(0)：那是 **OS 盲选口**（macOS 临时段 49152–65535）
        //    ⇒ 与 Docker 随机发布口**同段** ⇒ 可能落到别的容器已发布的口号上
        //    ⇒ 邻居占 127.0.0.1:P 时我们通配绑定照样成功、lsof 两个监听者并存
        //    ⇒ 客户端被更具体的那个抢走 ⇒ **静默假红**（机制与实测见 support/RealGrpcPort）。
        ServerBuilder<?> builder = ServerBuilder.forPort(RealGrpcPort.pick());
        configurer.accept(builder);                  // ← 被测的那一行（接口其实是 Consumer<ServerBuilder<?>>）
        SlowHandlerProbe probe = new SlowHandlerProbe();
        Server server = builder.addService(slowUserService(probe)).build().start();

        // ⚠️ **必须显式写 dns:/// scheme**：Nacos discovery 在全局 NameResolverRegistry 里
        //    注册了一个高优先级 provider，会把任何无 scheme 的 target 当作 serviceId 去 Nacos 拉实例。
        //    本测试要的是**直连**的裸 channel，显式 scheme 即可绕开服务发现。
        ManagedChannel channel = io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
                .forTarget("dns:///127.0.0.1:" + server.getPort()).usePlaintext().build();
        try {
            var stub = UserServiceGrpc.newBlockingStub(channel);
            long startNanos = System.nanoTime();
            StatusRuntimeException e = assertThrows(StatusRuntimeException.class,
                    () -> stub.withDeadlineAfter(5, TimeUnit.SECONDS)
                            .getUser(GetUserReq.newBuilder().setUid(1L).build()),
                    "「处理函数一直不返回」的服务必须被服务端超时打断");
            long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;

            assertEquals(Status.Code.DEADLINE_EXCEEDED, e.getStatus().getCode(),
                    "实际 status=" + e.getStatus().getCode()
                            + " description=" + e.getStatus().getDescription()
                            + " cause=" + e.getStatus().getCause());
            assertTrue(elapsedMillis < 3_000,
                    "必须在服务端超时（300ms）附近返回，实际耗时 " + elapsedMillis
                            + "ms。若接近 5000ms，说明只有客户端 deadline 生效 —— "
                            + "即 server.intercept(...) 没挂上，超时/deadline 传播/gRPC span 已静默消失。");

            // 🔴 2026-10-25（第二十三轮 · global L2 · X-5 残留）：下面这两条断言是**原来缺的**那一维。
            //    原先本域只有纯 mock 的链拼装断言 ⇒ 完全没有运行期证据。
            assertTrue(probe.entered.await(2, TimeUnit.SECONDS),
                    "handler 必须**真的被调用过**。若为 false，说明 grpc-java 根本没把 onHalfClose "
                            + "派发下去（裸 ServerCallHandler 就是这种情形）⇒ 本测试验的不是超时，而是空调用。");
            assertFalse(probe.completed.get(),
                    "服务端超时取消发生时，handler 必须**仍在跑**（未完成）。"
                            + "若为 true，说明取消发生在处理完成之后 ⇒ 超时闸根本没作用在处理过程上。");
        } finally {
            probe.release.countDown();               // 放行 handler，让它别挂在 30s 上
            channel.shutdownNow();
            server.shutdownNow();
        }
    }

    @Test
    @DisplayName("配置：生产 application.yaml 的 observability.rpc-timeout 必须是 2s"
            + "（Go: user.go:32 未配 Timeout ⇒ go-zero zrpc/config.go 默认 2000ms）")
    void productionTimeoutMatchesGoZeroDefault() throws Exception {
        try (var in = getClass().getResourceAsStream("/application.yaml")) {
            assertNotNull(in, "application.yaml 必须随 jar 打包");
            String yml = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(yml.contains("rpc-timeout: 2s"),
                    "Go 侧 user.yaml 未配 Timeout ⇒ 走 go-zero zrpc/config.go 的 default=2000ms；"
                            + "Java 侧必须同样是 2s，否则超时语义与 Go 不一致");
        }
    }

    /**
     * 注册一个「处理函数一直不返回」的服务 —— 用来观察超时是否真的生效。
     *
     * <p>🔴 2026-10-25（第二十三轮 · global L2 · X-5 残留）：这里**必须**用 grpc-java 的
     * <b>标准派发入口</b> {@link ServerCalls#asyncUnaryCall}，**不能**用裸
     * {@link ServerCallHandler} —— 后者不会被派发 {@code onHalfClose}
     * ⇒ 「处理函数一直不返回」从未发生 ⇒ 守门空转。
     */
    private static ServerServiceDefinition slowUserService(SlowHandlerProbe probe) {
        ServerCallHandler<GetUserReq, GetUserResp> slowHandler =
                ServerCalls.asyncUnaryCall((req, observer) -> {
                    probe.entered.countDown();
                    try {
                        // 「业务处理很慢」：用 latch 而不是 sleep 10s ⇒
                        // ① 测试可以在 finally 里**立刻**放行，不必等满 10 秒；
                        // ② 可以**精确**观测「取消发生时它还在跑」。
                        probe.release.await(30, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    probe.completed.set(true);
                    try {
                        observer.onNext(GetUserResp.getDefaultInstance());
                        observer.onCompleted();
                    } catch (RuntimeException ignored) {
                        // 调用已被服务端超时关闭 ⇒ 这里再写就会抛。
                        // ⚠️ **不是**掩盖问题：本测试要断言的正是「关闭发生时它还在跑」这个时序。
                    }
                });
        // ⚠️ 自建一个只含 GetUser 这一个方法的 ServiceDescriptor：
        //    ① 必须用生成代码里的**同一个** getGetUserMethod() 实例，否则报
        //       "Bound method ... not same instance as method in service descriptor"；
        //    ② 描述符里的每个方法都必须有绑定。
        io.grpc.ServiceDescriptor onlyGetUser = io.grpc.ServiceDescriptor
                .newBuilder(UserServiceGrpc.getServiceDescriptor().getName())
                .addMethod(UserServiceGrpc.getGetUserMethod())
                .build();
        return ServerServiceDefinition.builder(onlyGetUser)
                .addMethod(UserServiceGrpc.getGetUserMethod(), slowHandler)
                .build();
    }

    /**
     * 「处理函数确实被调用过」+「取消发生时它仍在跑」这两个证据。
     *
     * <p>⚠️ 这两个信号是**原来缺的那一维**：只看「客户端拿到什么 status、耗时多久」，
     * 而那两件事在「handler 压根没执行」时<b>同样成立</b> ⇒ 守门是空的。
     */
    private static final class SlowHandlerProbe {
        /** handler 真的被调用 ⇒ countDown。 */
        final CountDownLatch entered = new CountDownLatch(1);
        /** 测试收尾时放行 ⇒ 让 handler 立刻退出，不必等满 30s。 */
        final CountDownLatch release = new CountDownLatch(1);
        /** handler 跑完了（= 取消发生时它已经不在跑了）。 */
        final AtomicBoolean completed = new AtomicBoolean(false);
    }
}
