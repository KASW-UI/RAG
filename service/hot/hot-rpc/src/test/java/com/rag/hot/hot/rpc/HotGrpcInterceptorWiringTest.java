package com.rag.hot.hot.rpc;

import com.rag.common.observability.RpcInterceptor.DeadlineState;
import com.rag.hot.hot.proto.GetHotArticlesRequest;
import com.rag.hot.hot.proto.GetHotArticlesResponse;
import com.rag.hot.hot.proto.HotServiceGrpc;
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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「gRPC interceptor 真的挂到 server 上了吗」的<b>运行期</b>证据 —— AGENTS.md §3.3 的核心命题。
 *
 * <h2>为什么 hot 域必须补这一条</h2>
 * <p>Go 侧<b>显式</b>注册了拦截器（这是行为层的事，不是「表达层」）：
 * <ul>
 *   <li>{@code service/hot/rpc/hot.go:31} → {@code s := zrpc.MustNewServer(c.RpcServerConf, …)}
 *       ⇒ Go 侧由 zrpc <b>框架级</b>补上 Trace → Recover → Stat → Prometheus → Breaker → Shedding
 *       （消费点 {@code go-zero/zrpc/server.go:144-163}）</li>
 *   <li>{@code service/hot/rpc/hot.go:42} →
 *       {@code s.AddUnaryInterceptors(observability.NewUnaryServerInterceptor(rpcTimeout, observability.SlowThreshold()))}
 *       ⇒ 带 <b>timeout</b> 与 <b>slow threshold</b> 两个参数</li>
 * </ul>
 *
 * <p>⇒ RAG 侧必须做到<b>两件</b>，缺任一件都<b>静默失效</b>（不报错、测试照样全绿）：
 * <ol>
 *   <li>{@code @Import(RpcServerInterceptorFactory.class)} —— 否则本 Bean 根本不存在
 *       （{@code com.rag.common} 已不再被 rpc 进程全包扫描，见 AGENTS.md §6.3 问题 3）</li>
 *   <li>{@code @Bean GrpcServerConfigurer} → {@code server.intercept(...)}
 *       —— ⚠️ <b>devh 的 grpc-server-spring-boot-starter 3.1.0 从不自动注册 ServerInterceptor</b>
 *       （common 域已用 {@code javap} 对 jar 证伪，H2）⇒ 只产出 Bean 而没有 Configurer ⇒
 *       链路上<b>什么都不会发生</b></li>
 * </ol>
 *
 * <h2>🔴 本类存在的原因：hot-rpc 此前<b>两件都只有第 ① 件</b></h2>
 * <p>修复前 {@code HotRpcApplication} 有 {@code @Import(RpcServerInterceptorFactory.class)}、
 * <b>没有</b> {@code GrpcServerConfigurer} ⇒ 属 §3.3 的 <b>H2 形态在 hot 域复发</b>
 * （common 域的 DRIFT-COMMON-004 登记了证据、判归属 {@code align/hot}，
 * 但 hot 域自己的三份台账此前<b>零提及</b>这条缺口）。
 * <br>可观察后果（全部是静默的）：
 * <ol>
 *   <li>{@code interceptCall} / {@code onHalfClose} <b>从未执行</b> ⇒ gRPC 无 span；</li>
 *   <li><b>无 deadline 传播</b> ⇒ handler 线程上 {@code DeadlineState} 恒未设置
 *       ⇒ 业务层 {@code checkDeadline()} 全是死代码；</li>
 *   <li>{@code DeadlineQueryTimeoutInjector} 因拿不到 {@code DeadlineState}
 *       而<b>不给 SQL 设 {@code queryTimeout}</b>；</li>
 *   <li>gRPC 本身无超时（客户端只能等 api 层 {@code http-timeout}）。</li>
 * </ol>
 *
 * <h2>为什么必须起一个真 server 来测</h2>
 * <p>只断言「容器里有 {@code ServerInterceptor} Bean」<b>是假绿</b>：删掉
 * {@code HotRpcApplication#rpcInterceptorConfigurer} 之后该断言<b>照样是绿的</b>，
 * 而超时 / deadline 传播 / gRPC span 已全部静默消失 ——
 * 这正是 §3.3「断言 Bean 存在 ≠ 断言机制生效」的反面教材。
 * <p>所以本类起一个<b>真实 gRPC server</b>，用容器里那个 {@link GrpcServerConfigurer} 装配它，
 * 注册一个「处理函数一直不返回」的服务（用 latch 阻塞，不是 {@code sleep}），
 * 然后用 5 秒的客户端 deadline 去调：
 * <ul>
 *   <li>若服务端超时生效 ⇒ <b>约 300ms</b> 就返回 {@code DEADLINE_EXCEEDED}
 *       （远早于客户端的 5 秒）；</li>
 *   <li>若 interceptor 没挂上 ⇒ 要等满 5 秒才由<b>客户端</b> deadline 超时返回。</li>
 * </ul>
 * 两者返回的 status 码<b>都是</b> {@code DEADLINE_EXCEEDED}，因此必须再断言<b>耗时</b>
 * 才能区分 —— 这正是「只断言结果不看机制」会漏掉的地方。
 *
 * <h2>第二条用例断言的是<b>另一半</b>：deadline 传播</h2>
 * <p>超时被掐断只能证明 {@code interceptCall} 跑过；<b>deadline 有没有传到 handler 线程</b>
 * 是 {@code onHalfClose} 的职责（{@code RpcInterceptor} 的 M7 修复把它绑在 handler 入口）。
 * <p>⇒ 本类在 handler 内部读 {@link DeadlineState#remainingMillis()}：
 * 未接上时恒为 {@code Long.MAX_VALUE}（未设置），接上时 ≤ 服务端超时。
 * <b>两条用例分别覆盖 {@code interceptCall} 与 {@code onHalfClose} 两半。</b>
 *
 * <h2>🔴 2026-10-25（第二十三轮 · global L2 · X-5 残留）：本类原先<b>守门不严</b></h2>
 * <p>本类的慢服务<b>已经</b>用 {@link io.grpc.stub.ServerCalls#asyncUnaryCall}（标准派发入口）注册
 * ⇒ grpc-java 会把 {@code onHalfClose} 派发下去 ⇒ <b>handler 真的跑起来了</b>
 * ⇒ 所以它<b>不是</b> task/follow 那种「绿但空转」的形态。
 * <p><b>但它守不住退化</b>：原用例只断言「客户端拿到什么 status、耗时多久」，
 * 而这两件事在「handler 压根没被调用过」时<b>同样成立</b>
 * ⇒ 把 {@code asyncUnaryCall} 换回<b>裸</b> {@code ServerCallHandler}，本类<b>照样全绿</b>
 * ⇒ 等于「一次没被验证过的修复」。
 * <p>⇒ 本轮补上 task/follow 已有的两条断言：<b>handler 确实被调用过</b> +
 * <b>取消发生时它仍在跑</b>；并把 {@code Thread.sleep(10_000)} 换成 latch
 * （sleep 方案下这两条断言根本写不出来）。
 * （探针证据与完整经过 ⇒ {@code docs/DRIFT/global/cross-domain.md} §二·八）
 *
 * <h2>为什么不需要真 PG / Testcontainers</h2>
 * <p>hot-rpc 没有 {@code @MapperScan}、没有 {@code DataSource} 依赖（存储侧是 Redis，
 * 且 Lettuce 连接是<b>懒建</b>的）⇒ 上下文启动不需要连库
 * ⇒ 不引入 Testcontainers，避免把「装配验证」与「数据层验证」耦在一起。
 */
@SpringBootTest(
        classes = HotRpcApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "grpc.server.port=0",
                // 绕过 Nacos：devh 的 @GrpcClient 在 Bean 初始化期就建 channel（不是懒建），
                // 测试环境没有 Nacos ⇒ discovery:// 解析失败 ⇒ 上下文起不来。
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN",
                // 绕过 Kafka：consumer 会持续重试连接，但不影响上下文启动与本类断言。
                "spring.kafka.bootstrap-servers=127.0.0.1:1",
                // 绕过 Redis：Lettuce 连接懒建，不真正连。
                "spring.data.redis.host=127.0.0.1",
                "spring.data.redis.port=1",
                // ⚠️ 压到 300ms 让「服务端超时」与「客户端 5s deadline」的差距足够大、好区分。
                //    生产值 2s 由下面 productionTimeoutMatchesGoZeroDefault 单独断言。
                "observability.rpc-timeout=300ms",
                // ⚠️ 关掉 CPU 自适应降载，让本类的判据**只**取决于「业务观测拦截器有没有挂上」。
                //    否则在一台 CPU 占用率超阈值的机器上，链上的 shedding 层会先返回
                //    RESOURCE_EXHAUSTED，把「超时是否生效」这个判据盖掉 ⇒ 变成 flaky。
                //    ⚠️ 这是**测试专用**覆盖，不改生产默认值（rag.protection.cpu-shedding.enabled
                //    的默认 true 是对齐 Go zrpc 的 Middlewares.Shedding default=true）。
                "rag.protection.cpu-shedding.enabled=false"
        })
class HotGrpcInterceptorWiringTest {

    @Autowired private ApplicationContext ctx;

    @Test
    @DisplayName("运行期：服务端超时由容器里的 GrpcServerConfigurer 真正施加到 server 上"
            + "（Go: hot.go:42 AddUnaryInterceptors）")
    void serverSideTimeoutIsActuallyEnforced() throws Exception {
        GrpcServerConfigurer configurer = ctx.getBean("rpcInterceptorConfigurer", GrpcServerConfigurer.class);
        assertNotNull(configurer, "rpcInterceptorConfigurer 必须产出 GrpcServerConfigurer Bean");

        ServerBuilder<?> builder = ServerBuilder.forPort(0);
        configurer.accept(builder);                  // ← 被测的那一行（接口其实是 Consumer<ServerBuilder<?>>）
        SlowHandlerProbe probe = new SlowHandlerProbe();
        Server server = builder.addService(slowHotService(probe)).build().start();

        // ⚠️ **必须显式写 dns:/// scheme**：Nacos discovery 在全局 NameResolverRegistry 里
        //    注册了一个高优先级 provider，会把任何无 scheme 的 target 当作 serviceId 去 Nacos 拉实例。
        //    本测试要的是**直连**的裸 channel，显式 scheme 即可绕开服务发现。
        ManagedChannel channel = io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
                .forTarget("dns:///127.0.0.1:" + server.getPort()).usePlaintext().build();
        try {
            var stub = HotServiceGrpc.newBlockingStub(channel);
            long startNanos = System.nanoTime();
            StatusRuntimeException e = assertThrows(StatusRuntimeException.class,
                    () -> stub.withDeadlineAfter(5, TimeUnit.SECONDS)
                            .getHotArticles(GetHotArticlesRequest.newBuilder().setTopK(20).build()),
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
            //    原用例只看「客户端拿到什么 status、耗时多久」—— 而这两件事在
            //    「handler 压根没被调用过」时**同样成立**（客户端照样等到自己的 5s deadline）
            //    ⇒ 守门是空的，只能证明「服务端能取消一个空调用」。
            //    ⇒ 本类原先用 asyncUnaryCall ⇒ 当前实现下 handler 确实跑起来了，
            //      但**守不住退化**（换回裸 ServerCallHandler 仍全绿）⇒ 必须把这条维度钉死。
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
        Server server = builder.addService(slowHotService(probe, observedRemaining, handlerThread)).build().start();

        ManagedChannel channel = io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
                .forTarget("dns:///127.0.0.1:" + server.getPort()).usePlaintext().build();
        try {
            var stub = HotServiceGrpc.newBlockingStub(channel);
            assertThrows(StatusRuntimeException.class,
                    () -> stub.withDeadlineAfter(5, TimeUnit.SECONDS)
                            .getHotArticles(GetHotArticlesRequest.newBuilder().setTopK(20).build()),
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

            // 🔴 2026-10-25（第二十三轮 · global L2）：本条与上一条同源 ——
            //    「deadline 传到了 handler 线程」只说明 onHalfClose 执行过，
            //    还要证明「取消发生时它**仍在跑**」⇒ 超时闸作用在**处理过程**上，
            //    而不是「处理早就结束了、事后才收到取消」。
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
            + "（Go: hot.go:31 未配 Timeout ⇒ go-zero zrpc/config.go 默认 2000ms）")
    void productionTimeoutMatchesGoZeroDefault() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/application.yml")) {
            assertNotNull(in, "application.yml 必须随 jar 打包");
            String yml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(yml.contains("rpc-timeout: 2s"),
                    "Go 侧 hot.yaml 未配 Timeout ⇒ 走 go-zero zrpc/config.go 的 default=2000ms；"
                            + "Java 侧必须同样是 2s，否则超时语义与 Go 不一致");
        }
    }

    /**
     * 注册一个「处理函数一直不返回」的服务 —— 用来观察超时是否真的生效。
     *
     * @param observedRemaining 供 handler 写入它观测到的剩余预算（{@code Long.MAX_VALUE} = 未设置）
     */
    private static ServerServiceDefinition slowHotService(SlowHandlerProbe probe) {
        return slowHotService(probe, new AtomicLong(Long.MAX_VALUE), new AtomicReference<>("<未进入>"));
    }

    private static ServerServiceDefinition slowHotService(SlowHandlerProbe probe,
            AtomicLong observedRemaining, AtomicReference<String> handlerThread) {
        // ⚠️⚠️ **必须用 grpc-java 的标准 unary handler（ServerCalls.asyncUnaryCall）**，
        //    不能自己写一个裸 ServerCallHandler。
        //
        //    实测（2026-10-25）：用裸 ServerCallHandler 注册到 **unary** 方法上时，
        //    grpc-java **根本不会**把 onHalfClose 派发给它 ⇒ 方法体一次都不执行
        //    （handlerEntered 恒为 false）。
        //    ⇒ 后果：服务端超时照样会由拦截器的调度器把 call 关掉 ⇒
        //    「超时生效」这类断言<b>照样是绿的</b>，但 handler 从未跑过
        //    ⇒ **这类断言证明不了「handler 真的被调用过」**。
        //
        //    标准 handler 才会走 ServerCalls.UnaryServerCallListener.onHalfClose()
        //    → 调用业务方法 ⇒ 方法体在 RpcInterceptor 的 deadline 绑定作用域内执行。
        ServerCallHandler<GetHotArticlesRequest, GetHotArticlesResponse> slowHandler =
                io.grpc.stub.ServerCalls.asyncUnaryCall(
                        (GetHotArticlesRequest req,
                         io.grpc.stub.StreamObserver<GetHotArticlesResponse> obs) -> {
                            probe.entered.countDown();
                            handlerThread.set(Thread.currentThread().getName());
                            // ⚠️ 此刻正是 RpcInterceptor 的 onHalfClose 绑定 deadline 的作用域内，
                            //    所以这里读到的就是「服务端看到的剩余预算」。
                            observedRemaining.set(DeadlineState.remainingMillis());
                            try {
                                // 🔴 2026-10-25（第二十三轮 · global L2）：用 latch 而不是 sleep 10s ⇒
                                // ① 测试可以在 finally 里**立刻**放行，不必等满 10 秒；
                                // ② 可以**精确**观测「取消发生时它还在跑」。
                                probe.release.await(30, TimeUnit.SECONDS);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            probe.completed.set(true);
                            try {
                                obs.onNext(GetHotArticlesResponse.getDefaultInstance());
                                obs.onCompleted();
                            } catch (RuntimeException ignored) {
                                // 调用已被服务端超时关闭 ⇒ 这里再写就会抛。
                                // ⚠️ **不是**掩盖问题：本测试要断言的正是「关闭发生时它还在跑」这个时序。
                            }
                        });
        // ⚠️ 自建一个只含 GetHotArticles 这一个方法的 ServiceDescriptor：
        //    ① 必须用生成代码里的**同一个** getGetHotArticlesMethod() 实例，否则报
        //       "Bound method ... not same instance as method in service descriptor"；
        //    ② 描述符里的每个方法都必须有绑定。
        io.grpc.ServiceDescriptor onlyGetHotArticles = io.grpc.ServiceDescriptor
                .newBuilder(HotServiceGrpc.getServiceDescriptor().getName())
                .addMethod(HotServiceGrpc.getGetHotArticlesMethod())
                .build();
        return ServerServiceDefinition.builder(onlyGetHotArticles)
                .addMethod(HotServiceGrpc.getGetHotArticlesMethod(), slowHandler)
                .build();
    }

    /**
     * 「处理函数确实被调用过」+「取消发生时它仍在跑」这两个证据。
     *
     * <p>⚠️ 这两个信号是<b>原来缺的那一维</b>：原用例只看「客户端拿到什么 status、耗时多久」，
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
