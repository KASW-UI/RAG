import com.rag.common.observability.GoStyleRequestDeadline;
import com.rag.common.observability.RpcInterceptor;
import com.rag.common.protection.GoStyleProtectionConfiguration;
import com.rag.common.protection.GoStyleProtectionProperties;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ServerCalls;
import io.opentelemetry.api.GlobalOpenTelemetry;
import net.devh.boot.grpc.client.autoconfigure.GrpcClientAutoConfiguration;
import net.devh.boot.grpc.client.channelfactory.GrpcChannelConfigurer;
import net.devh.boot.grpc.client.channelfactory.GrpcChannelFactory;
import net.devh.boot.grpc.client.channelfactory.InProcessChannelFactory;
import net.devh.boot.grpc.client.config.GrpcChannelsProperties;
import net.devh.boot.grpc.client.interceptor.GlobalClientInterceptorRegistry;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 【global 域 · 第四十六轮】E38② 重估的 **RAG 半边活体探针**。
 *
 * <p>测的行为（与 Go 半边逐字对齐）：
 * <b>上游请求的 deadline 已经过期 ⇒ 业务代码再发一次下游 gRPC 调用 ⇒ 会怎样？</b>
 *
 * <p>🔴 跑的是**生产类** {@link RpcInterceptor}（不是自己 new 一个等价物）
 * ⇒ 与 Go 半边（真源 {@code observability.NewUnaryServerInterceptor}）逐键对拍。
 *
 * <p>🔴 真传输层：上游 hop = **真 Netty server（随机端口）+ 真 Netty 客户端**（`dns:///` 直连，
 * 绕开 Nacos 服务发现）⇒ 观测的是**网络上真正传过来的字节**；
 * 下游 hop = **in-process**（理由见下面的改造说明）。
 *
 * <p>⚠️ <b>为什么 RAG 侧要观测两个载体</b>：
 * RAG 有<b>两个</b>互不相通的 deadline 载体，而 Go 只有<b>一个</b>（ctx）：
 * <ul>
 *   <li>{@link GoStyleRequestDeadline} —— <b>出向 gRPC 调用</b>实际使用的那个
 *       （favorite-rpc / follow-rpc 的 {@code applyTo(stub)} 读它）</li>
 *   <li>{@code RpcInterceptor.DeadlineState} —— <b>SQL 路径</b>使用的那个
 *       （{@code DeadlineQueryTimeoutInjector} 读它）</li>
 * </ul>
 * ⇒ <b>出向调用与 SQL 走的是两套载体</b>，这正是本探针要暴露的东西。
 *
 * <p>输出格式与 Go 半边一致：{@code PROBE|<case>|<key>=<value>|...}
 *
 * <h2>🔴 2026-10-30 改造：RAG 半边的下游 channel 改走 **devh 的生产装配路径**</h2>
 * <p><b>改造前</b>：下游 channel 是探针自己 {@code NettyChannelBuilder} 搓出来的
 * ⇒ ⇒ <b>绕开了 devh 的装配</b> ⇒ ⇒ 全局客户端拦截器
 * （{@code GoStyleOutboundDeadlineInterceptor}）<b>接不到这条 channel 上</b>
 * ⇒ ⇒ 出向调用<b>拿不到请求 deadline</b> ⇒ ⇒ 必然挂满
 * ⇒ ⇒ 测到的 {@code failed_fast=false} / {@code code=0} 是<b>探针自己的构造产物</b>，
 * <b>不是域侧 stub 的行为</b>。
 * <p><b>改造后</b>：下游 channel 由 <b>devh 自己的 {@link GrpcChannelFactory}</b> 造
 * （{@code createChannel("probe")} ⇒ 走 {@code AbstractChannelFactory.createChannel}
 * ⇒ {@code ClientInterceptors.interceptForward(channel, interceptors)}）
 * ⇒ ⇒ 全局拦截器<b>按生产的方式</b>接在 channel 上
 * ⇒ ⇒ 出向调用<b>继承请求 deadline</b> ⇒ ⇒ 测到的就是<b>生产行为</b>。
 * <p><b>装配面与 G4 的运行期守门一致</b>：真 Spring 上下文 ＋
 * {@code @ImportAutoConfiguration(GrpcClientAutoConfiguration.class)}
 * ＋ {@code @Import(GoStyleProtectionConfiguration.class)}
 * ⇒ ⇒ 拦截器由 devh 的 {@code GlobalClientInterceptorRegistry} 按
 * {@code @GrpcGlobalClientInterceptor} 注解收集 ⇒ ⇒ <b>不是</b>探针手工塞进去的。
 * <p><b>裸 channel 降级为负控</b>：仍然测一遍，但只打到 <b>stderr</b>
 * ⇒ ⇒ 它证明「这次测到的快速失败<b>确实是 devh 装配带来的</b>，不是 in-process 传播」
 * ⇒ ⇒ 它<b>不进入</b> PROBE 键位（键位必须与 Go 半边逐字一一对应）。
 *
 * <p><b>诚实边界（本轮实测得出，必须写明）</b>：
 * <ul>
 *   <li>本仓 classpath 上<b>没有</b> {@code io.grpc.netty}（未 shade 的那套）
 *       ⇒ ⇒ devh 的 {@code ConditionalOnClass} <b>回退</b>到 {@code InProcessChannelFactory}
 *       ⇒ ⇒ devh 的 channel <b>只能</b>连 in-process 服务端 ⇒ ⇒ 下游 hop 必须是进程内
 *       ⇒ ⇒ （踩过的坑：下游若开在 TCP 上 ⇒ devh channel 拿到
 *       {@code UNAVAILABLE("io exception")} ⇒ ⇒ 那是<b>连不上</b>，**不是**行为差异）。
 *       ⇒ ⇒ 若日后 {@code service/common} 的 classpath 上有了 {@code grpc-netty}
 *       ⇒ ⇒ devh 会改用 {@code NettyChannelFactory} ⇒ ⇒ 本探针的下游必须同步改回 TCP。</li>
 *   <li>被测的**下游 hop 是进程内**，Go 半边是 TCP ⇒ ⇒ 但被测的量是
 *       「<b>出向调用有没有拿到请求 deadline</b>」⇒ ⇒ 与传输层无关
 *       ⇒ ⇒ 两侧可比（grpc-go 用过期 ctx ⇒ DEADLINE_EXCEEDED(4)；
 *       grpc-java 用已过期 deadline ⇒ DEADLINE_EXCEEDED(4) ⇒ ⇒ 两侧同码）。</li>
 *   <li>本探针仍<b>未</b>覆盖「客户端硬上界 2000ms 未启用」那一轴（= 域侧 yml · P2-T04）。</li>
 * </ul>
 */
public final class DeadlineDownstreamProbe {

    /** 下游服务的全方法名（两边一致；Go 半边用 pb.LikeService/GetLikeCount）。 */
    private static final String DOWNSTREAM_METHOD_NAME = "probe.Downstream/Hang";

    /**
     * 下游 **in-process 服务端名** = devh 的 channel 名。
     *
     * <p>⚠️ devh 的 {@code InProcessChannelFactory.newChannelBuilder(name)} 走的是
     * {@code InProcessChannelBuilder.forName(name)} ⇒ ⇒ **channel 名必须与服务端名一致**。
     */
    private static final String DOWNSTREAM_SERVER_NAME = "probe-downstream-deadline";

    /** 上游服务的全方法名（⚠️ 必须与上游服务名同前缀 ⇒ grpc-java 的硬约束）。 */
    private static final String UPSTREAM_METHOD_NAME = "probe.Upstream/Hang";

    /** 与 Go 半边同一组参数。 */
    private static final Duration TIMEOUT = Duration.ofMillis(300);
    private static final Duration SLOW_THRESHOLD = Duration.ofMillis(750);

    /**
     * 「出向调用是否立刻失败」的判定窗口（与 Go 半边同值 400ms）。
     *
     * <p>⚠️ 必须**远大于**一次本地回环 RPC 的正常耗时（~1-5ms），
     * 又必须**小于**下游 hang server 的响应时间（30s）⇒ 400ms 是安全档。
     */
    private static final long DOWNSTREAM_FAIL_FAST_WINDOW_MS = 400L;

    /** 等到 deadline 过期之后再多等这么久，确保真的到期。 */
    private static final long EXPIRY_GRACE_MS = 50L;

    /**
     * 出向调用的**安全上界**（毫秒）—— 只为「别把 handler 线程永久挂住」。
     *
     * <p>⚠️ <b>它不会污染被测量</b>：共享层的拦截器算的是 **min(载体剩余, 调用点自带)**，
     * 而本探针的测量时刻**载体已过期**（剩余 = 0）⇒ ⇒ min = **0** ⇒ ⇒ 仍然**立刻失败**。
     * ⇒ ⇒ 若哪天载体没绑上 ⇒ ⇒ 这一行变成**唯一**的上界 ⇒ ⇒ 调用会等到它 ⇒ ⇒
     * ⇒ ⇒ 于是 {@code failed_fast=false}（= 正确地报告「没有载体」）。
     */
    private static final long SAFETY_DEADLINE_MS = 1500L;

    public static void main(String[] args) throws Exception {
        try {
            GlobalOpenTelemetry.resetForTest();
        } catch (NoSuchMethodError ignored) {
            // 老版本没有 resetForTest
        }
        GlobalOpenTelemetry.set(io.opentelemetry.sdk.OpenTelemetrySdk.builder().build());

        // ── 下游 hang server：**in-process** ─────────────────────────────
        // ⚠️ 为什么是 in-process（不是 TCP）：本仓 classpath 上**没有** `io.grpc.netty`
        //    （未 shade 的那套）⇒ ⇒ devh 的 `ConditionalOnClass` 会**回退**到
        //    `InProcessChannelFactory` ⇒ ⇒ devh 的 channel **只能**连 in-process 服务端
        //    ⇒ ⇒ 下游 hop 必须是进程内，否则会拿到 UNAVAILABLE("io exception")
        //    （= 本轮实测踩过的坑：那不是行为差异，是**连不上**）。
        //    ⇒ ⇒ 上游 hop 仍然是**真 TCP**（真 Netty server ＋ 真 Netty 客户端）。
        // ⇒ ⇒ devh channel 与裸 channel **连的是同一个 in-process 服务端**
        //    ⇒ ⇒ 正负两次测量**只差「有没有 devh 装配」这一个变量**。
        Server downstream = InProcessServerBuilder.forName(DOWNSTREAM_SERVER_NAME)
                .addService(ServerServiceDefinition.builder("probe.Downstream")
                        .addMethod(HANG_METHOD, ServerCalls.asyncUnaryCall((req, obs) -> {
                            // 永不响应 ⇒ 出向调用若没有 deadline 就会挂住
                        }))
                        .build())
                .build().start();

        // ── 下游 channel ①：**生产装配路径**（devh 自己的 GrpcChannelFactory）────
        // ⚠️ 这里是本轮改造的核心：原先这条 channel 是探针自己搓的裸 Netty channel
        //    ⇒ ⇒ 绕开 devh 装配 ⇒ ⇒ 全局客户端拦截器接不上 ⇒ ⇒ 出向调用拿不到 deadline
        //    ⇒ ⇒ 测到的是「探针的构造」，不是「生产的行为」。
        // ⇒ ⇒ 现在改走 devh 自己的 channel factory ⇒ ⇒ 拦截器**按生产的方式**接上。
        ConfigurableApplicationContext devhCtx = new SpringApplicationBuilder(ProbeClientApp.class)
                .web(WebApplicationType.NONE)
                .run();
        Channel downstreamChannel = devhCtx.getBean(GrpcChannelFactory.class)
                .createChannel(DOWNSTREAM_SERVER_NAME);
        // 诊断（stderr，不进 PROBE 键位）：**实际生效的 channel factory 是哪一个**
        // ⇒ ⇒ 这一条决定了下游 hop 能不能真的连上（in-process vs netty）
        System.err.println("probe-devh-channel-factory|"
                + devhCtx.getBean(GrpcChannelFactory.class).getClass().getName());
        // 诊断（stderr，不进 PROBE 键位）：全局拦截器 registry 里到底有哪些
        System.err.println("probe-global-interceptors|"
                + devhCtx.getBean(GlobalClientInterceptorRegistry.class).getClientInterceptors());
        System.err.println("probe-interceptor-beans|"
                + devhCtx.getBeansWithAnnotation(GrpcGlobalClientInterceptor.class).keySet());
        // 诊断（stderr）：共享层的出向 deadline 开关/上界（**生产默认值**）
        System.err.println("probe-outbound-cfg|enabled="
                + devhCtx.getBean(GoStyleProtectionProperties.class)
                        .getOutboundDeadline().isEnabled()
                + "|capMillis="
                + devhCtx.getBean(GoStyleProtectionProperties.class)
                        .getOutboundDeadline().getCapMillis());

        // ── 下游 channel ②：**负控**（同一个 in-process 服务端，但不经 devh 装配）──
        // ⚠️ 保留它只有一个理由：**证明这次的「快速失败」确实是 devh 装配带来的**
        //    ⇒ ⇒ 它只打到 **stderr** ⇒ ⇒ **不进 PROBE 键位**（键位要与 Go 半边逐字对应）。
        ManagedChannel bareChannel = InProcessChannelBuilder
                .forName(DOWNSTREAM_SERVER_NAME)
                .build();

        // ── 上游 server（生产拦截器）────────────────────────────────────────
        CountDownLatch entered = new CountDownLatch(1);
        // ⚠️ handler 要先等 deadline 过期（~350ms）再跑那次**挂住**的下游调用（400ms）
        //    ⇒ 主线程必须等它**做完**，否则读到的是 null（不是「没观测到」）
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> outcome = new AtomicReference<>();
        ServerBuilder<?> builder = ServerBuilder.forPort(0);
        // ← 被测的那一行：生产拦截器
        builder.intercept(RpcInterceptor.newUnaryServerInterceptor(TIMEOUT, SLOW_THRESHOLD));
        ServerServiceDefinition svc = ServerServiceDefinition.builder("probe.Upstream")
                .addMethod(UPSTREAM_METHOD, ServerCalls.asyncUnaryCall((req, obs) -> {
                    entered.countDown();
                    // ⚠️ handler 跑在 grpc 线程上 ⇒ DeadlineState 绑定在**跑 handler 的线程**
                    outcome.set(measureDownstream(downstreamChannel));
                    // 负控：裸 channel ⇒ 不经 devh 装配 ⇒ ⇒ 出向调用拿不到 deadline ⇒ 挂满
                    // ⚠️ 只报 stderr ⇒ ⇒ 不进 PROBE 键位（键位必须与 Go 半边逐字一一对应）
                    System.err.println("probe-control|" + measureDownstream(bareChannel));
                    done.countDown();
                    // 永不 onNext / onCompleted ⇒ 让超时调度器去关 call
                }))
                .build();
        Server upstream = builder.addService(svc).build().start();

        ManagedChannel upstreamChannel = NettyChannelBuilder
                .forTarget("dns:///127.0.0.1:" + upstream.getPort())
                .usePlaintext()
                .build();
        try {
            // 客户端**不带** deadline ⇒ 让服务端自己的超时来触发
            //   ⚠️ 这样才能观测到「拦截器在 deadline 时刻返回，而业务线程继续跑」
            try {
                ClientCalls.blockingUnaryCall(upstreamChannel, UPSTREAM_METHOD, CallOptions.DEFAULT, new byte[0]);
                System.err.println("probe: upstream call unexpectedly succeeded");
                System.exit(2);
            } catch (StatusRuntimeException ignored) {
                // 预期：服务端超时关闭 ⇒ DEADLINE_EXCEEDED
            }
            if (!entered.await(2, TimeUnit.SECONDS)) {
                System.err.println("probe: handler was never entered — the probe measured nothing");
                System.exit(2);
            }
            if (!done.await(10, TimeUnit.SECONDS)) {
                System.err.println("probe: handler never finished the downstream measurement");
                System.exit(2);
            }
            String result = outcome.get();
            if (result == null) {
                System.err.println("probe: handler never produced the downstream outcome");
                System.exit(2);
            }
            System.out.printf("PROBE|downstream_deadline_failfast|%s%n", result);
        } finally {
            upstreamChannel.shutdownNow();
            upstream.shutdownNow();
            bareChannel.shutdownNow();
            devhCtx.close();
            downstream.shutdownNow();
        }
    }

    /**
     * 探针的**客户端装配面**：只拉起 devh 的自动配置 ＋ 共享层的保护链配置。
     *
     * <p>⚠️ **与 G4 的运行期守门 {@code GoStyleDevhOutboundDeadlineAssemblyGateTest}
     * 用的是同一套装配面** ⇒ ⇒ 两者的结论说的是同一条链。
     *
     * <p>⚠️ **为什么不用 {@code @GrpcClient} 注入**：本探针的下游是**探针自己起的
     * hang server**（随机端口、进程内）⇒ ⇒ 它的地址只有起完 server 才知道
     * ⇒ ⇒ 没法用编译期固定的 {@code @GrpcClient("probe")} 值 ⇒ ⇒ 改用
     * {@code GrpcChannelFactory.createChannel(name)}（devh 的**同一条**建 channel 路径）。
     */
    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration(GrpcClientAutoConfiguration.class)
    @Import(GoStyleProtectionConfiguration.class)
    static class ProbeClientApp {

        /**
         * 显式声明 devh 的 channel factory = {@link InProcessChannelFactory}。
         *
         * <p>⚠️ <b>为什么必须显式声明</b>：devh 自动配置选出来的是
         * {@code InProcessOrAlternativeChannelFactory} ⇒ ⇒ 它在**没有真实传输**
         * （本仓 classpath 上没有 {@code io.grpc.netty}）时**回退到 DNS**
         * ⇒ ⇒ 于是把 channel 名当**主机名**去解析 ⇒ ⇒ 报
         * {@code UNAVAILABLE("Unable to resolve host ...")}。
         * ⇒ ⇒ 显式换成 {@link InProcessChannelFactory} ⇒ ⇒ 它走
         * {@code InProcessChannelBuilder.forName(name)} ⇒ ⇒ 连进程内服务端。
         *
         * <p>⚠️ 这与 G4 的运行期守门 {@code GoStyleDevhOutboundDeadlineAssemblyGateTest}
         * <b>用的是同一个声明</b> ⇒ ⇒ 两者说的是同一条装配链。
         */
        @Bean
        GrpcChannelFactory grpcChannelFactory(GrpcChannelsProperties props,
                                              GlobalClientInterceptorRegistry registry,
                                              List<GrpcChannelConfigurer> configurers) {
            return new InProcessChannelFactory(props, registry, configurers);
        }
    }

    /**
     * deadline 过期后发一次下游调用，返回观测串。
     *
     * <p>观测四个键：
     * <ul>
     *   <li>{@code failed_fast} —— 下游调用是否**立刻**返回（= 拿到 error）</li>
     *   <li>{@code code} —— 那次调用的 gRPC 状态码（未失败则为 0）</li>
     *   <li>{@code outgoing_carrier} —— <b>出向调用实际使用</b>的载体
     *       （{@link GoStyleRequestDeadline}）的状态三档</li>
     *   <li>{@code sql_carrier} —— <b>SQL 路径</b>使用的载体
     *       （{@code RpcInterceptor.DeadlineState}）的状态三档</li>
     * </ul>
     *
     * <p>🔴 <b>出向调用必须用生产写法</b>：RAG 的 rpc 组件里出向 gRPC 的生产写法是
     * <b>devh 的 channel</b>（{@code @GrpcClient} 注入 ⇒ devh 的
     * {@code GrpcChannelFactory} 建 channel ⇒ 全局客户端拦截器接在 channel 上）
     * ⇒ ⇒ <b>域侧「裸 stub」不需要改任何一行</b>（它照样走 devh channel）
     * ⇒ ⇒ 本探针用 {@code downstreamChannel}（= devh channel）做**正式观测**，
     * 用 {@code bareChannel}（= 裸 channel）做**负控**。
     *
     * @param downstreamChannel 出向调用用的 channel（正式观测 = devh channel；负控 = 裸 channel）
     */
    private static String measureDownstream(Channel downstreamChannel) {
        // ① 等到有效期限过去
        //    ⚠️ 读的是**生产拦截器算出来的**有效期限（DeadlineState）⇒ 不用猜
        long left = RpcInterceptor.DeadlineState.remainingMillis();
        if (left > 0) {
            try {
                Thread.sleep(left + EXPIRY_GRACE_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } else {
            try {
                Thread.sleep(EXPIRY_GRACE_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // ② 两个载体的状态（三档，与 Go 半边逐字一致）
        String outgoingCarrier = carrierState(
                GoStyleRequestDeadline.isPresent(),
                GoStyleRequestDeadline.isPresent() ? GoStyleRequestDeadline.remainingMillis() : -1L);
        String sqlCarrier = carrierState(
                RpcInterceptor.DeadlineState.remainingMillis() >= 0,
                RpcInterceptor.DeadlineState.remainingMillis());

        // ③ 出向调用：**在当前线程**（= handler 线程 = **生产形状**）
        //    ⚠️ **必须在当前线程**：`GoStyleRequestDeadline` 是普通 `ThreadLocal`
        //    ⇒ ⇒ **不可继承** ⇒ ⇒ 换线程就看不到载体 ⇒ ⇒ 拦截器自然套不上 deadline
        //    ⇒ ⇒ 而生产里出向调用**就在 handler 线程上**（同步发起）⇒ ⇒ 这才是同形。
        //    ⇒ ⇒ 只给一个**安全上界**（SAFETY_DEADLINE_MS）防挂死；载体在时 min(0, 上界)=0
        //    ⇒ ⇒ ⇒ 仍然立刻失败 ⇒ ⇒ 不污染被测量。
        long t0 = System.nanoTime();
        StatusRuntimeException thrown = null;
        try {
            ClientCalls.blockingUnaryCall(downstreamChannel, HANG_METHOD,
                    CallOptions.DEFAULT.withDeadlineAfter(SAFETY_DEADLINE_MS, TimeUnit.MILLISECONDS),
                    new byte[0]);
        } catch (StatusRuntimeException e) {
            thrown = e;
        }
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;

        boolean failedFast = thrown != null && elapsedMs < DOWNSTREAM_FAIL_FAST_WINDOW_MS;
        int code = 0;
        if (thrown != null && thrown.getStatus() != null) {
            code = thrown.getStatus().getCode().value();
            // 诊断（stderr，不进 PROBE 键位）：码 + 描述 + cause 链
            // ⇒ ⇒ 「desc=io exception」这种壳必须能看到底下的真因
            StringBuilder causeChain = new StringBuilder();
            for (Throwable t = thrown.getCause(); t != null && causeChain.length() < 400; t = t.getCause()) {
                causeChain.append(" <- ").append(t.getClass().getSimpleName())
                        .append(':').append(String.valueOf(t.getMessage()));
            }
            System.err.println("probe-outcome|code=" + code + "|desc=" + thrown.getStatus().getDescription()
                    + "|cause=" + causeChain);
        }
        // 诊断（stderr）：本次出向调用耗时 ⇒ ⇒ 与负控对比即可看出「有没有被载体掐断」
        System.err.println("probe-elapsed|ms=" + elapsedMs + "|failedFast=" + failedFast);

        return String.format("failed_fast=%b|code=%d|outgoing_carrier=%s|sql_carrier=%s",
                failedFast, code, outgoingCarrier, sqlCarrier);
    }

    /**
     * 载体状态三档。
     *
     * @param present   载体是否存在于当前线程
     * @param remaining 剩余毫秒（-1 表示「没有载体」）
     */
    private static String carrierState(boolean present, long remaining) {
        if (!present) {
            return "absent";
        }
        return remaining <= 0 ? "present_expired" : "present_live";
    }

    private static final MethodDescriptor<byte[], byte[]> HANG_METHOD =
            MethodDescriptor.<byte[], byte[]>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(DOWNSTREAM_METHOD_NAME)
                    .setRequestMarshaller(ByteMarshaller.INSTANCE)
                    .setResponseMarshaller(ByteMarshaller.INSTANCE)
                    .build();

    private static final MethodDescriptor<byte[], byte[]> UPSTREAM_METHOD =
            MethodDescriptor.<byte[], byte[]>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(UPSTREAM_METHOD_NAME)
                    .setRequestMarshaller(ByteMarshaller.INSTANCE)
                    .setResponseMarshaller(ByteMarshaller.INSTANCE)
                    .build();

    private static final class ByteMarshaller implements MethodDescriptor.Marshaller<byte[]> {
        static final ByteMarshaller INSTANCE = new ByteMarshaller();

        @Override
        public InputStream stream(byte[] value) {
            return new ByteArrayInputStream(value == null ? new byte[0] : value);
        }

        @Override
        public byte[] parse(InputStream stream) {
            return new byte[0];
        }
    }

    private DeadlineDownstreamProbe() {
    }
}
