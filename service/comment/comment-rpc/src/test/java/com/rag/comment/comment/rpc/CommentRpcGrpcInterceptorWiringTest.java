package com.rag.comment.comment.rpc;

import com.rag.comment.comment.proto.CommentServiceGrpc;
import com.rag.comment.comment.proto.GetCommentReq;
import com.rag.comment.comment.proto.GetCommentResp;
import com.rag.common.observability.RpcInterceptor.DeadlineState;
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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import com.rag.comment.comment.rpc.support.RealGrpcPort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 <b>运行期守门</b>：comment-rpc 的 gRPC 业务观测拦截器<b>真的</b>挂到了 server 上，
 * 且<b>服务端超时真的打断了「正在执行的处理」</b>。
 *
 * <h2>为什么 comment-rpc 必须有这一条</h2>
 * <p>本域此前的装配层守门只有两条<b>不启动 Spring 上下文</b>的测试：
 * {@link CommentProtectionChainWiringTest}（纯 Mockito 拼装断言）与
 * {@link CommentSchemaPgGateTest} / {@link CommentRpcGoSemanticsGateTest}（裸 JDBC + 裸 MyBatis）。
 * ⇒ <b>没有任何一条测试验证过「拦截器真的挂到了 gRPC server 上」</b>。
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
 *   <li>{@code service/comment/rpc/comment.go:33} →
 *       {@code rpcTimeout := observability.DisableNativeRpcTimeout(&c.RpcServerConf)}
 *       （Go {@code etc/comment.yaml} <b>未配</b> Timeout ⇒ 生效值来自 go-zero
 *       {@code zrpc/config.go} 的 {@code Timeout int64 json:",default=2000"} = <b>2000ms</b>）</li>
 *   <li>{@code service/comment/rpc/comment.go:54} →
 *       {@code s.AddUnaryInterceptors(observability.NewUnaryServerInterceptor(rpcTimeout, observability.SlowThreshold()))}</li>
 * </ul>
 *
 * <h2>🔴 三条判别式断言缺一不可（本 lane 的硬性要求）</h2>
 * <p>grpc-java <b>不会</b>向<b>裸</b> {@link ServerCallHandler} 派发 {@code onHalfClose}
 * ⇒ 用裸 handler 注册慢服务时，「处理函数一直不返回」这件事<b>从未发生</b>
 * ⇒ 客户端照样拿到（自己的）{@code DEADLINE_EXCEEDED} ⇒ 只断言 status 与耗时的用例<b>照样是绿的</b>
 * ⇒ 那是「服务端能取消一个<b>空调用</b>」，不是「服务端超时打断一个<b>正在执行</b>的处理」。
 * <p>⇒ 因此本类必须同时满足：
 * <ol>
 *   <li>用 {@link ServerCalls#asyncUnaryCall}（标准派发入口）注册慢服务 ⇒ handler 真的会被调用；</li>
 *   <li>断言「<b>handler 确实被调用过</b>」；</li>
 *   <li>断言「<b>取消发生时它仍在跑</b>」（不是处理早就结束了才收到取消）。</li>
 * </ol>
 * （探针证据与完整经过 ⇒ {@code docs/DRIFT/global/cross-domain.md} §二·八）
 *
 * <h2>为什么用 latch 而不是 {@code sleep}</h2>
 * <p>{@code Thread.sleep(10_000)} 方案下，「取消发生时它还在跑」这条断言<b>根本写不出来</b>
 * （等到断言时它早跑完了）⇒ 必须用 latch：{@code probe.release.await(30, SECONDS)}
 * ⇒ 测试在 {@code finally} 里<b>立刻</b>放行，且能<b>精确</b>观测时序。
 *
 * <h2>为什么必须接真 PG</h2>
 * <p>comment-rpc 有 {@code @MapperScan}，MapperFactoryBean <b>要求</b> {@code sqlSessionFactory}，
 * 没有 DataSource 就没有它 ⇒ 「不接库」会让本类要验证的东西无法验证。
 * 且本模块用 Flyway，接真 PG 才能顺带验迁移是否真的建了表。
 */
@SpringBootTest(
        classes = CommentRpcApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                // 🔴 gRPC server 端口走**本域专用段**（RealGrpcPort），**不用 `port=0`**：
                //    port=0 会让 OS 从临时端口段（macOS 49152–65535）里挑，而那是
                //    **Docker 随机发布容器端口用的同一段** ⇒ 别的会话的容器先占住
                //    ⇒ EADDRINUSE ⇒ 整个测试类启动失败（表现为「偶尔红、换个时间又绿」的抖动）。
                //    ⇒ 本域专用段 = **22600–22699**（global 段表预分配给 comment 域），
                //    原先的裸硬编码 21500 **落在 favorite 域的探针段 21400–21599 内**
                //    ⇒ ⇒ 两边都以为对方不在自己段里 ⇒ ⇒ 2026-10-27 一并迁出。
                //    ⇒ 端口在 @DynamicPropertySource 里注册（**不进 context cache key**）。
                // 指到必然拒连的地址：NacosRegistration 捕获 NacosException 只记日志不中断启动。
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                // devh 的 @GrpcClient 在 Bean 初始化期就建 channel（不是懒建）⇒ 必须给静态地址，
                // 否则 discovery:// 解析要去找 Nacos。
                "grpc.client.message-rpc.address=static://127.0.0.1:1",
                "grpc.client.article-rpc.address=static://127.0.0.1:1",
                // OTLP 指向不可达端点，只会在后台重试刷日志，关掉。
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN",
                // 绕过 Kafka：consumer 会持续重试连接，但不影响上下文启动与本类断言。
                "spring.kafka.bootstrap-servers=127.0.0.1:1",
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
class CommentRpcGrpcInterceptorWiringTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("comment_rpc_interceptor_it")
                    .withUsername("rag")
                    .withPassword("rag");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        // 🔴 gRPC server 端口走**本域专用段**（22600–22699），不用 `port=0`
        //    （port=0 落 OS 临时段 ⇒ 与 Docker 发布口同段 ⇒ 环境性抖动）。
        //    ⚠️ **@DynamicPropertySource 的值不进 Spring 的 context cache key**
        //    ⇒ ⇒ 各测试类的**专属 context key**（rag.test.gate.context-key=*）仍是必需的
        //    ⇒ ⇒ 两处都要留：一个是「端口不撞段」，一个是「上下文不共享」。
        r.add("grpc.server.port", RealGrpcPort::pick);
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private ApplicationContext ctx;

    @Test
    @DisplayName("运行期：服务端超时由容器里的 GrpcServerConfigurer 真正施加到 server 上"
            + "（Go: comment.go:54 AddUnaryInterceptors）")
    void serverSideTimeoutIsActuallyEnforced() throws Exception {
        // ⚠️ 必须按 Bean 名取：devh starter 自己还注册了 compressionServerConfigurer /
        //    decompressionServerConfigurer 两个同类型 Bean，ctx.getBean(GrpcServerConfigurer.class)
        //    会抛 NoUniqueBeanDefinitionException。我们要验的只有本模块自己那个。
        GrpcServerConfigurer configurer = ctx.getBean("rpcInterceptorConfigurer", GrpcServerConfigurer.class);
        assertNotNull(configurer, "rpcInterceptorConfigurer 必须产出 GrpcServerConfigurer Bean");

        ServerBuilder<?> builder = ServerBuilder.forPort(0);
        configurer.accept(builder);                  // ← 被测的那一行（接口其实是 Consumer<ServerBuilder<?>>）
        SlowHandlerProbe probe = new SlowHandlerProbe();
        Server server = builder.addService(slowCommentService(probe)).build().start();

        // ⚠️ **必须显式写 dns:/// scheme**：Nacos discovery 在全局 NameResolverRegistry 里
        //    注册了一个高优先级 provider，会把任何无 scheme 的 target 当作 serviceId 去 Nacos 拉实例。
        //    本测试要的是**直连**的裸 channel，显式 scheme 即可绕开服务发现。
        ManagedChannel channel = io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
                .forTarget("dns:///127.0.0.1:" + server.getPort()).usePlaintext().build();
        try {
            var stub = CommentServiceGrpc.newBlockingStub(channel);
            long startNanos = System.nanoTime();
            StatusRuntimeException e = assertThrows(StatusRuntimeException.class,
                    () -> stub.withDeadlineAfter(5, TimeUnit.SECONDS)
                            .getComment(GetCommentReq.newBuilder()
                                    .setTargetType("article")
                                    .setTargetId("t-1")
                                    .setPage(1)
                                    .setPageSize(10)
                                    .build()),
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

            // 🔴 下面这两条断言是「判别式」的那一维：
            //    只看「客户端拿到什么 status、耗时多久」—— 而这两件事在
            //    「handler 压根没被调用过」时**同样成立**（客户端照样等到自己的 5s deadline）
            //    ⇒ 守门是空的，只能证明「服务端能取消一个空调用」。
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
    @DisplayName("运行期：deadline 真的传播到了 handler 线程（onHalfClose 那一半）")
    void deadlineIsPropagatedToHandlerThread() throws Exception {
        GrpcServerConfigurer configurer = ctx.getBean("rpcInterceptorConfigurer", GrpcServerConfigurer.class);
        assertNotNull(configurer);

        // handler 内部观测到的「剩余预算」；Long.MAX_VALUE = 未设置（= 没接上）
        AtomicLong observedRemaining = new AtomicLong(Long.MAX_VALUE);
        AtomicReference<String> handlerThread = new AtomicReference<>("<未进入>");
        SlowHandlerProbe probe = new SlowHandlerProbe();

        ServerBuilder<?> builder = ServerBuilder.forPort(0);
        configurer.accept(builder);
        Server server = builder.addService(
                slowCommentService(probe, observedRemaining, handlerThread)).build().start();

        ManagedChannel channel = io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
                .forTarget("dns:///127.0.0.1:" + server.getPort()).usePlaintext().build();
        try {
            var stub = CommentServiceGrpc.newBlockingStub(channel);
            assertThrows(StatusRuntimeException.class,
                    () -> stub.withDeadlineAfter(5, TimeUnit.SECONDS)
                            .getComment(GetCommentReq.newBuilder()
                                    .setTargetType("article")
                                    .setTargetId("t-1")
                                    .build()),
                    "「处理函数一直不返回」的服务必须被服务端超时打断");

            assertTrue(probe.entered.await(2, TimeUnit.SECONDS),
                    "handler 根本没被调用（线程 = " + handlerThread.get() + "）"
                            + "⇒ 本用例无法证明 deadline 传播；先确认 handler 入口本身可达");

            long remaining = observedRemaining.get();
            assertTrue(remaining != Long.MAX_VALUE,
                    "handler 线程（" + handlerThread.get() + "）上 DeadlineState 恒未设置 "
                            + "⇒ onHalfClose 从未执行 ⇒ deadline 没有传播到业务层 "
                            + "⇒ checkDeadline() 全是死代码，且 SQL 不会被注入 queryTimeout");
            assertTrue(remaining > 0 && remaining <= 300,
                    "handler 观测到的剩余预算应是服务端超时（300ms）之内，实际 = " + remaining
                            + "ms。若远超 300ms，说明绑的不是服务端超时而是别的时刻");

            // 与上一条同源 —— 「deadline 传到了 handler 线程」只说明 onHalfClose 执行过，
            // 还要证明「取消发生时它**仍在跑**」⇒ 超时闸作用在**处理过程**上，
            // 而不是「处理早就结束了、事后才收到取消」。
            assertFalse(probe.completed.get(),
                    "取消发生时 handler 必须**仍在跑**（未完成）。"
                            + "若为 true，说明取消发生在处理完成之后 ⇒ 超时闸根本没作用在处理过程上。");
        } finally {
            probe.release.countDown();
            channel.shutdownNow();
            server.shutdownNow();
        }
    }

    @Test
    @DisplayName("配置：生产 application.yml 的 observability.rpc-timeout 必须是 2s"
            + "（Go: comment.go:33 未配 Timeout ⇒ go-zero zrpc/config.go 默认 2000ms）")
    void productionTimeoutMatchesGoZeroDefault() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/application.yml")) {
            assertNotNull(in, "application.yml 必须随 jar 打包");
            String yml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(yml.contains("rpc-timeout: 2s"),
                    "Go 侧 etc/comment.yaml 未配 Timeout ⇒ 走 go-zero zrpc/config.go 的 default=2000ms；"
                            + "Java 侧必须同样是 2s，否则超时语义与 Go 不一致");
        }
    }

    /**
     * 注册一个「处理函数一直不返回」的服务 —— 用来观察超时是否真的生效。
     *
     * <p>🔴 这里**必须**用 grpc-java 的标准派发入口 {@link ServerCalls#asyncUnaryCall}，
     * <b>不能</b>用裸 {@link ServerCallHandler} —— 后者不会被派发 {@code onHalfClose}
     * ⇒ 方法体一次都不执行 ⇒ 「处理函数一直不返回」从未发生 ⇒ 守门空转。
     */
    private static ServerServiceDefinition slowCommentService(SlowHandlerProbe probe) {
        return slowCommentService(probe, new AtomicLong(Long.MAX_VALUE), new AtomicReference<>("<未进入>"));
    }

    private static ServerServiceDefinition slowCommentService(SlowHandlerProbe probe,
            AtomicLong observedRemaining, AtomicReference<String> handlerThread) {
        ServerCallHandler<GetCommentReq, GetCommentResp> slowHandler =
                ServerCalls.asyncUnaryCall((req, observer) -> {
                    probe.entered.countDown();
                    handlerThread.set(Thread.currentThread().getName());
                    // ⚠️ 此刻正是 RpcInterceptor 的 onHalfClose 绑定 deadline 的作用域内，
                    //    所以这里读到的就是「服务端看到的剩余预算」。
                    observedRemaining.set(DeadlineState.remainingMillis());
                    try {
                        // 🔴 用 latch 而不是 sleep 10s ⇒
                        // ① 测试可以在 finally 里**立刻**放行，不必等满 10 秒；
                        // ② 可以**精确**观测「取消发生时它还在跑」。
                        probe.release.await(30, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    probe.completed.set(true);
                    try {
                        observer.onNext(GetCommentResp.getDefaultInstance());
                        observer.onCompleted();
                    } catch (RuntimeException ignored) {
                        // 调用已被服务端超时关闭 ⇒ 这里再写就会抛。
                        // ⚠️ **不是**掩盖问题：本测试要断言的正是「关闭发生时它还在跑」这个时序。
                    }
                });
        // ⚠️ 自建一个只含 GetComment 这一个方法的 ServiceDescriptor：
        //    ① 必须用生成代码里的**同一个** getGetCommentMethod() 实例，否则报
        //       "Bound method ... not same instance as method in service descriptor"；
        //    ② 描述符里的每个方法都必须有绑定。
        io.grpc.ServiceDescriptor onlyGetComment = io.grpc.ServiceDescriptor
                .newBuilder(CommentServiceGrpc.getServiceDescriptor().getName())
                .addMethod(CommentServiceGrpc.getGetCommentMethod())
                .build();
        return ServerServiceDefinition.builder(onlyGetComment)
                .addMethod(CommentServiceGrpc.getGetCommentMethod(), slowHandler)
                .build();
    }

    /**
     * 「处理函数确实被调用过」+「取消发生时它仍在跑」这两个证据。
     *
     * <p>⚠️ 这两个信号是「判别式」那一维：只看「客户端拿到什么 status、耗时多久」，
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
