package com.rag.favorite.favorite.rpc.client;

import com.rag.common.observability.GoStyleRequestDeadline;
import com.rag.common.observability.RpcInterceptor;
import com.rag.favorite.favorite.proto.FavoriteServiceGrpc;
import com.rag.favorite.favorite.proto.GetFavoriteFoldersReq;
import com.rag.favorite.favorite.proto.GetFavoriteFoldersResp;
import com.rag.favorite.favorite.rpc.support.RealGrpcPort;
import com.rag.users.user.proto.UserServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.stub.ServerCalls;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🔴 <b>E65 域侧守门</b>：favorite-rpc 的出向出口
 * <b>真的继承</b>入向 deadline，且继承精度是<b>纳秒</b>（不是毫秒）。
 *
 * <h2>🔴 为什么本域需要这一条（2026-10-31）</h2>
 * <p>本域此前的证据只到 <b>L2 源码级</b>：
 * <ul>
 *   <li>{@code docs/DRIFT/favorite/rpc.md} 第三轮新增段逐条核了「调用链 ⇒ 调用线程」
 *       （两条出向出口都在 {@code FavoriteDomainService} 链上 ⇒ gRPC handler 线程）；</li>
 *   <li>共享层的探针（{@code tools/semantic-probe/deadline-downstream/}）<b>测不到域内出口</b>
 *       —— 它的 RAG 半边<b>手搓 Netty channel</b>，绕开 devh 的全局拦截器装配
 *       ⇒ 探针的 {@code outgoing_carrier} 键<b>不能</b>当「域内出口没继承」的证据
 *       （那是<b>探针自己的构造</b>，不是域侧缺陷）；</li>
 * </ul>
 * ⇒ ⇒ 「域内出口确实继承载体」这件事<b>一直缺运行证据</b> ⇒ 本类补上。
 *
 * <h2>🔴 五条用例的分工（每条各有<b>专属</b>判别维度 · 缺一条都不够）</h2>
 * <ol>
 *   <li><b>真 wire + 真共享层拦截器</b> ⇒ 证明<b>载体在 handler 线程真的绑上了</b>；</li>
 *   <li><b>入向预算 &gt; 2s</b> ⇒ 证明 <b>2s 客户端封顶真的生效</b>（专治「不压任何 deadline」）；</li>
 *   <li><b>入向预算 &lt; 2s</b> ⇒ 证明出向上界<b>真的继承了入向剩余</b>（专治「无脑套 2s」）；</li>
 *   <li><b>已知绝对纳秒</b> ⇒ 证明<b>域内出向计算点不截断亚毫秒</b>（可确定性分辨毫秒版）；</li>
 *   <li><b>无载体</b> ⇒ 证明 2s 客户端封顶<b>无条件</b>在（对齐 Go 的拦截器级 {@code TimeoutInterceptor}）。</li>
 * </ol>
 *
 * <h2>🔴 为什么「继承」这一半<b>不是</b>本类的判据</h2>
 * <p>「继承请求 deadline」在生产链路上是 <b>devh 全局客户端拦截器链</b>给的
 * （{@code GoStyleOutboundDeadlineInterceptor}，{@code @GrpcGlobalClientInterceptor}，
 * {@code enabled} <b>默认 true</b>；devh {@code AbstractChannelFactory:61/:88/:95}
 * 把全局拦截器 {@code interceptForward} 到 channel 上 ⇒ 本域 main 代码零命中自建 channel
 * ⇒ 出向 stub <b>全部</b>是 {@code @GrpcClient} 注入 ⇒ <b>无一例外</b>）。
 * <br>⇒ ⇒ 本类**不重复**验那一层（那是<b>装配面</b>，归共享层 / global）；
 * ⇒ ⇒ 本类验的是<b>域内这一半</b>：<b>2s 客户端上界的计算点</b>（拦截器层的
 * {@code capMillis} <b>默认 0 = 不启用</b> ⇒ Go 的 2000ms 上界<b>只在域侧</b>）
 * ⇒ ⇒ 且域内那份 {@code applyTo} 是「{@code min(请求剩余, 2s)}」的<b>输入</b>（要先有请求剩余）。
 *
 * <h2>Go 出处（行为层，逐条对应）</h2>
 * <ul>
 *   <li><b>出向取 min</b>：{@code go-zero@v1.10.2/zrpc/internal/clientinterceptors/timeoutinterceptor.go:25}
 *       {@code ctx, cancel := context.WithTimeout(ctx, t)} ⇒ 父 ctx 更早则<b>先到者生效</b>；</li>
 *   <li><b>纳秒精度</b>：同上一行 ⇒ {@code t} 是<b>纳秒</b> ⇒ Go 侧<b>不</b>做毫秒截断；</li>
 *   <li><b>无条件 2s</b>：{@code go-zero@v1.10.2/zrpc/internal/client.go:113-117}
 *       ＋ {@code go-zero@v1.10.2/zrpc/internal/config.go:15}
 *       ＋ {@code go-zero@v1.10.2/zrpc/config.go:31}；</li>
 *   <li><b>父 ctx 的 deadline 来自入向</b>：
 *       {@code go-zero@v1.10.2/zrpc/internal/serverinterceptors/timeoutinterceptor.go:34}。</li>
 * </ul>
 *
 * <h2>🔴 为什么第一条必须用<b>真 socket</b>而不是 in-process</h2>
 * <p>in-process transport <b>不走 wire</b> ⇒ 没有 {@code grpc-timeout} 头
 * ⇒ 服务端 {@code io.grpc.Context.current().getDeadline()} 读到 {@code null}
 * ⇒ 载体<b>根本不会绑</b> ⇒ 用 in-process 写这条 ⇒ <b>必然假红</b>。
 * <br>⇒ 本类用 {@link RealGrpcPort#pick()}（本域段 22100–22199）起<b>真 socket</b>。
 *
 * <h2>🔴 为什么载体在 handler 线程可见</h2>
 * <p>载体是 <b>ThreadLocal</b>（{@code GoStyleRequestDeadline.java:64}）
 * ⇒ 只在<b>绑它的那个线程</b>可见 ⇒ 共享层在 {@code RpcInterceptor.onHalfClose}
 * （{@code RpcInterceptor.java:494}）里绑定 ⇒ ⇒ handler 必须<b>跑在同一个线程</b>上。
 * <br>⇒ 链路：grpc-java 的 {@code ServerBuilder} <b>默认 executor = directExecutor</b>
 * ⇒ {@code ServerCalls.asyncUnaryCall} 最终用 {@code call.getExecutor()}
 * ⇒ ⇒ 本类<b>不</b>覆写 executor ⇒ 与生产形态<b>同款</b>（同线程）。
 *
 * <h2>🔴 判据设计的两处坑（已被实测踩过 · 防下一个人重踩）</h2>
 * <ol>
 *   <li><b>绝对剩余值不能当判据</b>：本机「两次 {@code System.nanoTime()} 调用之间」的机器开销
 *       约 <b>0.3–0.5ms</b> ⇒ 用绝对值判会<b>时红时绿</b>（假信号）
 *       ⇒ 必须拿「载体当下的剩余」当基线，只测<b>相对损失</b>；</li>
 *   <li><b>第一次跑要付类加载 + JIT 的钱</b>（实测 ≈ <b>0.15ms</b>）⇒ 那笔钱会混进「相对损失」
 *       ⇒ 必须<b>先热身</b>（跑一遍同款路径）再测，否则会<b>误判成精度损失</b>。</li>
 * </ol>
 */
class FavoriteOutboundDeadlineCarrierGateTest {

    /** 客户端给的 deadline（毫秒）—— 必须<b>穿过 wire</b>，才能证明服务端真的收到了。 */
    private static final long CLIENT_DEADLINE_MILLIS = 1_500L;

    /**
     * 另一个场景：客户端给的 deadline <b>长于</b> 2s 客户端封顶（5s）。
     *
     * <p>⚠️ <b>为什么必须有这个场景</b>：入向预算<b>短于</b> 2s 时，「继承入向预算」与
     * 「原样返回（不压任何 deadline）」算出来的上界<b>完全相同</b>
     * ⇒ ⇒ 单一「短预算」场景<b>分辨不了</b>这两种实现
     * ⇒ ⇒ 必须再加一个「长于 2s」的场景（正确实现 = 2s，不压的实现 = 入向预算）。
     */
    private static final long LONG_CLIENT_DEADLINE_MILLIS = 5_000L;

    /**
     * 第二条用例里手动绑定的剩余预算：<b>10.9ms</b>（刻意取非整毫秒 ⇒ 亚毫秒部分 = 0.9ms）。
     *
     * <p>⚠️ 亚毫秒部分要<b>足够大</b>才能与测量开销拉开距离
     * ⇒ 毫秒版（读 {@code remainingMillis()}）会把 10.9ms 砍成 <b>10ms</b> ⇒ 精度损失 ≈ 0.9ms
     * ⇒ 而纳秒版损失 ≈ 几 μs ⇒ ⇒ 两者相差两个数量级 ⇒ 判据稳。
     */
    private static final long KNOWN_BUDGET_NANOS = 10_900_000L;

    /** 精度损失的判据上界（纳秒）：<b>0.4ms</b>。 */
    private static final long PRECISION_LOSS_TOLERANCE_NANOS = 400_000L;

    private static Server server;
    private static ManagedChannel channel;

    /** handler 内观测到的载体状态（跨线程传递 ⇒ 必须用引用类型）。 */
    private static final AtomicReference<Boolean> carrierPresent = new AtomicReference<>();
    private static final AtomicReference<Long> carrierRemainingNanos = new AtomicReference<>();
    private static final AtomicReference<Long> outboundRemainingNanos = new AtomicReference<>();

    @BeforeAll
    static void startServer() throws Exception {
        int port = RealGrpcPort.pick();

        // ⚠️ 这里**只给 service name**（不给 `getServiceDescriptor()`）：
        //    带 descriptor 构造 ⇒ grpc-java 会要求「descriptor 里的每个方法都必须被绑定」
        //    ⇒ 而本类只关心 deadline 载体 ⇒ 只绑一个方法。
        ServerServiceDefinition service = ServerServiceDefinition.builder(
                        FavoriteServiceGrpc.getServiceDescriptor().getName())
                .addMethod(FavoriteServiceGrpc.getGetFavoriteFoldersMethod(),
                        ServerCalls.asyncUnaryCall((GetFavoriteFoldersReq request,
                                                   io.grpc.stub.StreamObserver<GetFavoriteFoldersResp> observer) -> {
                            // 🔴 下面几行跑在 **gRPC handler 线程** ⇒ 共享层 `RpcInterceptor.onHalfClose`
                            //    在**同一线程**上绑的 ThreadLocal 载体**在这里可见**。
                            carrierPresent.set(GoStyleRequestDeadline.isPresent());
                            carrierRemainingNanos.set(GoStyleRequestDeadline.remainingNanos());

                            // 域内真实消费点：与 `UserRpcClient.userRpc()` **同一个调用形态**
                            // （第一段 applyTo 挂入向预算，第二段压 2s 客户端封顶）。
                            UserServiceGrpc.UserServiceBlockingStub stub =
                                    UserServiceGrpc.newBlockingStub(channel);
                            UserServiceGrpc.UserServiceBlockingStub capped =
                                    GoStyleClientCallTimeout.capByClientTimeout(
                                            GoStyleRequestDeadline.applyTo(stub));
                            io.grpc.Deadline deadline = capped.getCallOptions().getDeadline();
                            outboundRemainingNanos.set(deadline == null
                                    ? -1L
                                    : deadline.timeRemaining(TimeUnit.NANOSECONDS));

                            observer.onNext(GetFavoriteFoldersResp.getDefaultInstance());
                            observer.onCompleted();
                        }))
                .build();

        // 🔴 真装共享层的拦截器：`Duration.ZERO` = 服务端**不配**自己的预算
        //    ⇒ 生效预算 = min(无, 客户端 deadline) = **客户端 deadline**
        //    ⇒ 与本仓生产的 rpc 组件形态一致（本域未配 `grpc.server.timeout`）。
        ServerServiceDefinition intercepted = ServerInterceptors.intercept(service,
                RpcInterceptor.newUnaryServerInterceptor(Duration.ZERO, Duration.ofSeconds(1)));

        server = ServerBuilder.forPort(port).addService(intercepted).build().start();
                // 🔴 **必须用显式 `dns:///` scheme，不能用 `forAddress(...)`**（2026-10-31 实测踩坑）：
                //    devh 的 `GrpcClientAutoConfiguration:124` 会在**起 Spring 上下文**时
                //    把自己的 `discovery` NameResolverProvider 注册进 **grpc-java 的进程级全局**
                //    `NameResolverRegistry.getDefaultRegistry()` ⇒ ⇒ **一旦本 JVM 里起过一次 devh 上下文，
                //    该注册就是进程级的** ⇒ ⇒ 之后**任何**无显式 scheme 的手搓 channel
                //    （`ManagedChannelBuilder.forAddress("127.0.0.1", P)`）都会被 devh 的 discovery resolver
                //    **接管** ⇒ ⇒ 把 `127.0.0.1:P` 当**服务名**去 Nacos 查 ⇒ ⇒ `UNAVAILABLE`
                //    ⇒ ⇒ 实测表现：同一个模块里，某个 `@SpringBootTest` 先跑 ⇒ 本条就红；
                //    单独跑本类 ⇒ 5/5 全绿 ⇒ ⇒ **「单跑绿、同跑红」＝ 上下文污染，不是判据问题**。
                //    ⇒ 显式 `dns:///` 直接选定 grpc-java 自带的 DNS resolver ⇒ 不受 devh 注册影响。
        channel = ManagedChannelBuilder.forTarget("dns:///127.0.0.1:" + port).usePlaintext().build();
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (channel != null) {
            channel.shutdownNow();
            channel.awaitTermination(5, TimeUnit.SECONDS);
        }
        if (server != null) {
            server.shutdownNow();
            server.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    /** 一次「客户端带 deadline 调 → 读 handler 侧观测」的结果。 */
    private static final class Observation {
        final boolean carrierPresent;
        final long carrierRemainingNanos;
        final long outboundRemainingNanos;
        final Status.Code statusCode;

        Observation(boolean carrierPresent, long carrierRemainingNanos,
                    long outboundRemainingNanos, Status.Code statusCode) {
            this.carrierPresent = carrierPresent;
            this.carrierRemainingNanos = carrierRemainingNanos;
            this.outboundRemainingNanos = outboundRemainingNanos;
            this.statusCode = statusCode;
        }
    }

    /** 客户端带 deadline 调一次 ⇒ 返回 handler 线程上的观测。 */
    private static Observation callAndObserve(long clientDeadlineMillis) {
        carrierPresent.set(null);
        carrierRemainingNanos.set(null);
        outboundRemainingNanos.set(null);

        FavoriteServiceGrpc.FavoriteServiceBlockingStub stub =
                FavoriteServiceGrpc.newBlockingStub(channel)
                        .withDeadlineAfter(clientDeadlineMillis, TimeUnit.MILLISECONDS);

        Status.Code code = Status.Code.OK;
        try {
            stub.getFavoriteFolders(GetFavoriteFoldersReq.getDefaultInstance());
        } catch (StatusRuntimeException e) {
            code = e.getStatus().getCode();
        }

        return new Observation(
                Boolean.TRUE.equals(carrierPresent.get()),
                carrierRemainingNanos.get() == null ? -1L : carrierRemainingNanos.get(),
                outboundRemainingNanos.get() == null ? -1L : outboundRemainingNanos.get(),
                code);
    }

    @Test
    @DisplayName("🔴 真 wire + 真共享层拦截器 ⇒ 出向载体在 gRPC handler 线程真的绑上，且客户端的 deadline 真的到了服务端")
    void carrierIsReallyBoundOnHandlerThreadOverRealWire() {
        Observation o = callAndObserve(CLIENT_DEADLINE_MILLIS);

        assertThat(o.statusCode)
                .as("客户端调用必须成功返回（服务端没有超时调度器，超时只可能来自客户端自己的 deadline）")
                .isEqualTo(Status.Code.OK);

        assertThat(o.carrierPresent)
                .as("🔴 共享层 `RpcInterceptor.onHalfClose` 绑的载体必须在 **gRPC handler 线程**上可见"
                        + "⇒ 否则本域出向出口<b>根本没有继承入向 deadline</b>（⇒ E65 在本域不成立）")
                .isTrue();

        assertThat(o.carrierRemainingNanos)
                .as("载体剩余预算必须是**纳秒**且 > 0（-1 = 载体在位但没有预算 ⇒ 异常）")
                .isPositive();

        assertThat(o.carrierRemainingNanos)
                .as("🔴 客户端给的 %dms 必须**穿过 wire** 到了服务端", CLIENT_DEADLINE_MILLIS)
                .isGreaterThan(TimeUnit.MILLISECONDS.toNanos(CLIENT_DEADLINE_MILLIS) - 300_000_000L);
    }

    @Test
    @DisplayName("🔴 入向预算**长于** 2s ⇒ 出向上界被压到 2s（客户端封顶真的生效，不是原样返回）")
    void outboundIsCappedAtTwoSecondsWhenInboundBudgetIsLonger() {
        Observation o = callAndObserve(LONG_CLIENT_DEADLINE_MILLIS);

        assertThat(o.statusCode).as("客户端调用必须成功返回").isEqualTo(Status.Code.OK);
        assertThat(o.carrierPresent).as("载体必须在 handler 线程可见").isTrue();
        assertThat(o.carrierRemainingNanos)
                .as("载体剩余应 ≈ %dms（客户端给了 > 2s 的预算 ⇒ 载体上界是它、不是 2s）",
                        LONG_CLIENT_DEADLINE_MILLIS)
                .isGreaterThan(TimeUnit.MILLISECONDS.toNanos(LONG_CLIENT_DEADLINE_MILLIS) - 300_000_000L);

        long twoSeconds = TimeUnit.MILLISECONDS.toNanos(GoStyleClientCallTimeout.CLIENT_TIMEOUT_MILLIS);
        assertThat(o.outboundRemainingNanos)
                .as("🔴 入向预算（%dms）> 2s ⇒ 出向上界必须是 **2s 客户端封顶**"
                        + "（若等于入向预算 ⇒ 说明「根本没压 2s」）", LONG_CLIENT_DEADLINE_MILLIS)
                .isLessThanOrEqualTo(twoSeconds);
        assertThat(o.outboundRemainingNanos)
                .as("出向上界必须贴近 2s（不能是别的数）")
                .isGreaterThan(twoSeconds - 100_000_000L);
    }

    @Test
    @DisplayName("🔴 入向预算**短于** 2s ⇒ 出向上界继承入向剩余（不是无脑套 2s）")
    void outboundInheritsInboundBudgetWhenItIsShorter() {
        Observation o = callAndObserve(CLIENT_DEADLINE_MILLIS);

        assertThat(o.outboundRemainingNanos)
                .as("🔴 域内出向出口的上界必须**继承**了载体预算（不是无脑套 2s）")
                .isPositive();
        assertThat(o.outboundRemainingNanos)
                .as("出向上界 = min(载体剩余, 2s) ⇒ 必须**不宽于**载体剩余")
                .isLessThanOrEqualTo(o.carrierRemainingNanos);
        assertThat(o.outboundRemainingNanos)
                .as("🔴 入向预算只有 %dms ⇒ 出向上界必须**明显小于 2s**（否则就是「无脑套 2s」）",
                        CLIENT_DEADLINE_MILLIS)
                .isLessThan(TimeUnit.MILLISECONDS.toNanos(2_000L));
    }

    @Test
    @DisplayName("🔴 域内出向计算点按**纳秒**取预算（毫秒版会把 10.9ms 砍成 10ms ⇒ 本条转红）")
    void outboundBudgetKeepsNanosecondPrecision() {
        ManagedChannel local = InProcessChannelBuilder.forName("favorite-precision-only")
                .directExecutor().build();
        try {
            UserServiceGrpc.UserServiceBlockingStub stub =
                    UserServiceGrpc.newBlockingStub(local);

            long carrierNow;
            long outbound;
            long budgetNanos = KNOWN_BUDGET_NANOS;

            // 🔴🔴 **必须先热身**：整条路径第一次跑要付**类加载 + JIT** 的钱
            //    （本机实测 ≈ 0.15ms）⇒ ⇒ 那笔钱会**混进 loss** ⇒ ⇒ 不热身就会
            //    读到「loss ≈ 0.15ms」而误判成精度损失。
            try (GoStyleRequestDeadline.Scope ignored = GoStyleRequestDeadline.bindAbsoluteNanos(
                    System.nanoTime() + budgetNanos)) {
                GoStyleClientCallTimeout.capByClientTimeout(
                                GoStyleRequestDeadline.applyTo(stub))
                        .getCallOptions().getDeadline();
            }

            try (GoStyleRequestDeadline.Scope ignored = GoStyleRequestDeadline.bindAbsoluteNanos(
                    System.nanoTime() + budgetNanos)) {
                // 🔴 **基线**：此刻载体自己还剩多少（同一线程、同一载体上直接读）。
                carrierNow = GoStyleRequestDeadline.remainingNanos();

                UserServiceGrpc.UserServiceBlockingStub capped =
                        GoStyleClientCallTimeout.capByClientTimeout(
                                GoStyleRequestDeadline.applyTo(stub));
                io.grpc.Deadline deadline = capped.getCallOptions().getDeadline();
                outbound = deadline == null ? -1L : deadline.timeRemaining(TimeUnit.NANOSECONDS);
            }

            assertThat(outbound)
                    .as("出向必须拿到 deadline（无 deadline = 出向完全没上限 ⇒ 与 Go 相反）")
                    .isPositive();
            assertThat(outbound)
                    .as("出向上界**不得比载体预算更宽**（Go 侧是 min(父 ctx 剩余, 2s)）")
                    .isLessThanOrEqualTo(carrierNow);

            long loss = carrierNow - outbound;
            assertThat(loss)
                    .as("🔴 出向上界相对载体预算的**精度损失**必须 < %d ns（0.4ms）"
                            + "；预算 %d ns（10.9ms）的亚毫秒部分若被毫秒截断 ⇒ 损失 ≈ %d ns"
                            + " ⇒ 毫秒版（读 remainingMillis）会转红",
                            PRECISION_LOSS_TOLERANCE_NANOS, budgetNanos, 900_000L)
                    .isLessThan(PRECISION_LOSS_TOLERANCE_NANOS);
        } finally {
            local.shutdownNow();
        }
    }

    @Test
    @DisplayName("🔴 线程上没有载体 ⇒ 出向仍套 2s 客户端封顶（Go 侧 TimeoutInterceptor 是拦截器级别的）")
    void noCarrierStillGetsTwoSecondClientCap() {
        ManagedChannel local = InProcessChannelBuilder.forName("favorite-no-carrier")
                .directExecutor().build();
        try {
            UserServiceGrpc.UserServiceBlockingStub stub =
                    UserServiceGrpc.newBlockingStub(local);

            assertThat(GoStyleRequestDeadline.isPresent())
                    .as("本用例的前提：当前线程**没有**载体（否则测的就不是「无预算」这条路径）")
                    .isFalse();

            UserServiceGrpc.UserServiceBlockingStub capped =
                    GoStyleClientCallTimeout.capByClientTimeout(
                            GoStyleRequestDeadline.applyTo(stub));
            io.grpc.Deadline deadline = capped.getCallOptions().getDeadline();

            assertThat(deadline)
                    .as("🔴 没有请求预算时**也必须**有 deadline（否则出向调用完全没有上限 ⇒ 与 Go 相反）")
                    .isNotNull();

            long remaining = deadline.timeRemaining(TimeUnit.NANOSECONDS);
            long twoSeconds = TimeUnit.MILLISECONDS.toNanos(GoStyleClientCallTimeout.CLIENT_TIMEOUT_MILLIS);
            assertThat(remaining)
                    .as("出向上界必须是 2s 封顶（%d ns）", twoSeconds)
                    .isLessThanOrEqualTo(twoSeconds);
            assertThat(remaining)
                    .as("出向上界必须贴近 2s（不能是别的数）")
                    .isGreaterThan(twoSeconds - 100_000_000L);
        } finally {
            local.shutdownNow();
        }
    }
}
