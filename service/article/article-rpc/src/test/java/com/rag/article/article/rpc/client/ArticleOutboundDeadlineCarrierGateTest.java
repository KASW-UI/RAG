package com.rag.article.article.rpc.client;

import com.rag.article.article.proto.ArticleServiceGrpc;
import com.rag.article.article.proto.GetArticleRequest;
import com.rag.article.article.proto.GetArticleResponse;
import com.rag.article.article.rpc.support.RealGrpcPort;
import com.rag.common.observability.GoStyleRequestDeadline;
import com.rag.common.observability.RpcInterceptor;
import com.rag.message.message.proto.MessageServiceGrpc;
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
 * 🔴 <b>域侧守门</b>：本域出向出口（{@code MessageRpcClient} → {@code message-rpc}）的
 * <b>2s 客户端封顶真的生效</b>，且计算精度是<b>纳秒</b>（不是毫秒）。
 *
 * <h2>🔴 为什么必须新写这一条（2026-10-31 · G3 域侧自修）</h2>
 * <p>本域此前的出向出口是<b>裸 stub 调用</b>（{@code stub.sendNotification(req.build())}）
 * ⇒ ⇒ <b>零期限</b>：既无 2s 客户端封顶，又因跑在 Kafka consumer 线程上、
 * 共享层 {@code RpcInterceptor.onHalfClose} 绑的 ThreadLocal 载体<b>不可见</b>
 * ⇒ ⇒ 连「继承入向 deadline」也不成立
 * ⇒ ⇒ 而 Go 侧同款出口<b>有</b> 2000ms 客户端封顶 ⇒ ⇒ <b>可观察行为分叉</b>
 * （下游 {@code message-rpc} 挂死时 Go 2s 返回、RAG 无限期阻塞 ⇒ 消费线程被占死）。
 * <br>⇒ 本类补上这条缺失的运行证据（修前只有 <b>L2 源码级</b>）。
 *
 * <h2>🔴 本类证的是「<b>机制</b>」，不是「生产链路的最终数值」</h2>
 * <p>⚠️ <b>必须说清楚，否则会误读</b>：本域唯一的出向出口跑在 <b>Kafka consumer 线程</b>上
 * ⇒ ⇒ 生产上<b>载体不可见</b> ⇒ ⇒ 净效果 = <b>恰好 2s 封顶</b>
 * ⇒ ⇒ <b>对应本类第 ⑤ 条</b>（无载体 ⇒ 仍套 2s）。
 * <br>⇒ 而第 ①②③④ 条证的是<b>同一个计算点的机制</b>：
 * 「载体可见时 ⇒ 出向上界 = min(载体剩余, 2s)」⇒ ⇒ 这是<b>同一段代码</b>的另一半，
 * 用来证明「这个计算点本身是对的」（否则 ⑤ 条单独存在时，一个「写死 2s、完全不看载体」
 * 的实现也能过 ⑤ ⇒ ⇒ 那样 ⑤ 就成了<b>假绿</b>）。
 *
 * <h2>🔴 五条用例的分工（每条各有<b>专属</b>判别维度 · 缺一条都不够）</h2>
 * <ol>
 *   <li><b>真 wire + 真共享层拦截器</b> ⇒ 证明<b>载体在 handler 线程真的绑上了</b>
 *       （把证据从 L2 源码级升到 <b>L4 活体</b>）⇒ 且证明客户端的 deadline
 *       <b>真的穿过 wire 到了服务端</b>；</li>
 *   <li><b>入向预算 &gt; 2s</b> ⇒ 证明 <b>2s 客户端封顶真的生效</b>
 *       （这一条专治「不压任何 deadline」的实现）；</li>
 *   <li><b>入向预算 &lt; 2s</b> ⇒ 证明出向上界<b>真的继承了入向剩余</b>
 *       （这一条专治「无脑套 2s、根本不继承」的实现）；</li>
 *   <li><b>已知绝对纳秒</b> ⇒ 证明<b>域内出向计算点不截断亚毫秒</b>
 *       （这一条<b>可确定性分辨</b>「毫秒版」与「纳秒版」）；</li>
 *   <li><b>无载体</b> ⇒ 证明 2s 客户端封顶<b>无条件</b>在
 *       （对齐 Go 侧拦截器级别的 {@code TimeoutInterceptor}）
 *       ⇒ ⇒ <b>这一条才是本域生产出口真正落的那一档</b>。</li>
 * </ol>
 *
 * <h2>🔴 为什么必须拆成「长预算」与「短预算」两个场景</h2>
 * <p>入向预算<b>短于</b> 2s 时，「继承入向预算」与「原样返回（不压任何 deadline）」
 * 算出来的上界<b>完全相同</b> ⇒ ⇒ 单一「短预算」场景<b>分辨不了</b>这两种实现
 * ⇒ ⇒ 必须再加一个「长于 2s」的场景（正确实现 = 2s，不压的实现 = 入向预算）才可分辨。
 *
 * <h2>🔴 为什么精度那条能<b>确定性</b>分辨毫秒版与纳秒版</h2>
 * <p><b>wire 的 deadline 精度是毫秒</b>（grpc 协议的 {@code grpc-timeout} 单位就是毫秒）
 * ⇒ ⇒ 服务端读到的入向 deadline <b>天然毫秒对齐</b>
 * ⇒ ⇒ 用它来测「亚毫秒有没有被截断」⇒ 截断量<b>随机落在 [0,1)ms</b>
 * ⇒ ⇒ 阈值判据<b>必然</b>时红时绿 ⇒ <b>不能</b>当守门。
 * <br>⇒ 因此精度那条用<b>手动绑定一个已知的绝对纳秒时刻</b>（{@code now + 10.9ms}）：
 * <ul>
 *   <li><b>纳秒版</b>（本类要求）：上界 = {@code min(10.9ms, 2s)} = 10.9ms ⇒ 精度损失 ≈ μs 级；</li>
 *   <li><b>毫秒版</b>（旧实现）：{@code remainingMillis()} = {@code toMillis(10.9ms)} = <b>10ms</b>
 *       ⇒ 上界 = 10ms ⇒ 精度损失 ≈ <b>0.9ms</b> ⇒ 必然超出 0.4ms 判据 ⇒ <b>必然红</b>。</li>
 * </ul>
 *
 * <h2>🔴 为什么必须<b>热身</b>（第二处「看起来像精度损失」的坑）</h2>
 * <p>整条路径第一次跑要付<b>类加载 + JIT</b> 的钱（本机实测 ≈ <b>0.15ms</b>）
 * ⇒ ⇒ 那笔钱会<b>混进</b>「出向相对载体窄了多少」⇒ ⇒ 不热身就会读到
 * 「loss ≈ 0.15ms」而<b>误判成精度损失</b> ⇒ ⇒ 本类先跑一遍<b>同款路径</b>热身再测。
 *
 * <h2>Go 出处（行为层，逐条对应）</h2>
 * <ul>
 *   <li><b>出向取 min</b>：{@code go-zero@v1.10.2/zrpc/internal/clientinterceptors/timeoutinterceptor.go:25}
 *       {@code ctx, cancel := context.WithTimeout(ctx, t)} ⇒ 父 ctx 更早则<b>先到者生效</b>；</li>
 *   <li><b>纳秒精度</b>：同上一行 ⇒ {@code t} 是 <b>纳秒</b> ⇒ Go 侧<b>不</b>做毫秒截断；</li>
 *   <li><b>无条件 2s</b>：{@code go-zero@v1.10.2/zrpc/internal/client.go:113-117}（装拦截器）
 *       ＋ {@code go-zero@v1.10.2/zrpc/internal/config.go:15}（{@code Timeout bool json:",default=true"}）
 *       ＋ {@code go-zero@v1.10.2/zrpc/config.go:31}（{@code Timeout int64 json:",default=2000"}）
 *       ⇒ ⚠️ 本域 {@code service/article/rpc/etc/article.yaml} 的 {@code MessageRpc} 段
 *       <b>没写 Timeout</b> ⇒ 取的就是这个 default ⇒ <b>2000</b>；</li>
 *   <li><b>调用点</b>：{@code service/article/rpc/internal/mqs/article_sync_result_consumer.go:197}
 *       {@code l.svcCtx.MessageRpc.SendNotification(ctx, …)} ⇒ ctx 是 <b>Kafka 消费 ctx（无 deadline）</b>
 *       ⇒ ⇒ Go 侧这条出口<b>也只有 2s 封顶</b> ⇒ ⇒ 与本域生产形态<b>同形</b>。</li>
 * </ul>
 *
 * <h2>🔴 为什么第一条用<b>真 socket</b> 而不是 in-process</h2>
 * <p>in-process transport <b>不走 wire</b> ⇒ 没有 {@code grpc-timeout} 头
 * ⇒ ⇒ 服务端读到的 deadline 是 <b>null</b> ⇒ 载体<b>根本不会绑</b>
 * ⇒ ⇒ 用 in-process 写这条守门 ⇒ <b>必然红</b>（假红）。
 * <br>⇒ 本类用 {@link RealGrpcPort#pick()}（本域专用段 22400–22479）起<b>真 socket</b>。
 *
 * <h2>🔴 为什么 handler 与 {@code onHalfClose} <b>同线程</b>（载体可见的前提）</h2>
 * <p>载体是 <b>ThreadLocal</b>（{@code GoStyleRequestDeadline.java:64}）
 * ⇒ 只在<b>绑它的那个线程</b>可见 ⇒ 共享层在 {@code RpcInterceptor.onHalfClose}
 * （{@code RpcInterceptor.java:494}）里绑定 ⇒ ⇒ handler 必须<b>跑在同一个线程</b>上。
 * <br>⇒ 链路：grpc-java 的 {@code ServerBuilder} <b>默认 executor = directExecutor</b>
 * ⇒ {@code ServerCalls.asyncUnaryCall} 最终用 {@code call.getExecutor()}
 * ⇒ ⇒ 本类<b>不</b>覆写 executor ⇒ 与生产形态<b>同款</b>（同线程）。
 *
 * <h2>🔴 范式来源</h2>
 * <p>本类<b>照抄</b>本仓 comment 域的同款守门
 * （{@code service/comment/comment-rpc/.../client/CommentOutboundDeadlineCarrierGateTest.java}，
 * 5 例 + 变异矩阵已验证判别力）⇒ ⇒ <b>不发明第三种写法</b>（AGENTS.md §3.2）。
 */
class ArticleOutboundDeadlineCarrierGateTest {

    /** 客户端给的 deadline（毫秒）—— 必须<b>穿过 wire</b>，才能证明服务端真的收到了。 */
    private static final long CLIENT_DEADLINE_MILLIS = 1_500L;

    /**
     * 另一个场景：客户端给的 deadline <b>长于</b> 2s 客户端封顶（5s）。
     *
     * <p>⚠️ <b>为什么必须有这个场景</b>：入向预算<b>短于</b> 2s 时，「继承入向预算」与
     * 「原样返回（不压任何 deadline）」算出来的上界<b>完全相同</b>
     * ⇒ ⇒ 单一「短预算」场景<b>分辨不了</b>这两种实现 ⇒ 必须再加一个「长于 2s」的场景：
     * 此时正确实现的上界是 <b>2s</b>，而「不压」的实现是 <b>5s</b> ⇒ ⇒ 两者可分辨。
     */
    private static final long LONG_CLIENT_DEADLINE_MILLIS = 5_000L;

    /**
     * 精度那条用例里手动绑定的剩余预算：<b>10.9ms</b>（刻意取非整毫秒 ⇒ 亚毫秒部分 = 0.9ms）。
     *
     * <p>⚠️ 亚毫秒部分要<b>足够大</b>才能与测量开销拉开距离
     * ⇒ 毫秒版（读 {@code remainingMillis()}）会把 10.9ms 砍成 <b>10ms</b> ⇒ 精度损失 ≈ 0.9ms
     * ⇒ 而纳秒版损失 ≈ 几 μs ⇒ ⇒ 两者相差两个数量级 ⇒ 判据稳。
     */
    private static final long KNOWN_BUDGET_NANOS = 10_900_000L;

    /**
     * 精度损失的判据上界（纳秒）：<b>0.4ms</b>。
     *
     * <p>⚠️ <b>为什么测「相对损失」而不是「绝对剩余值」</b>：本机实测「两次 {@code nanoTime()}
     * 调用之间」的机器开销约 <b>0.3–0.5ms</b>（类加载 + JIT）⇒ ⇒ 绝对剩余值里混着这笔开销
     * ⇒ ⇒ 用绝对值当判据会**时红时绿**（假信号）⇒ 必须拿「载体当下的剩余」当基线，
     * 只测「出向比它窄了多少」。
     */
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
        //    ⇒ 而本类只关心 deadline 载体 ⇒ 只绑 `GetArticle` 一个方法。
        ServerServiceDefinition service = ServerServiceDefinition.builder(
                        ArticleServiceGrpc.getServiceDescriptor().getName())
                .addMethod(ArticleServiceGrpc.getGetArticleMethod(),
                        ServerCalls.asyncUnaryCall((GetArticleRequest request,
                                                   io.grpc.stub.StreamObserver<GetArticleResponse> observer) -> {
                            // 🔴 下面三行跑在 **gRPC handler 线程** ⇒ 共享层 `RpcInterceptor.onHalfClose`
                            //    在**同一线程**上绑的 ThreadLocal 载体**在这里可见**。
                            carrierPresent.set(GoStyleRequestDeadline.isPresent());
                            carrierRemainingNanos.set(GoStyleRequestDeadline.remainingNanos());

                            // 域内真实消费点：与 `MessageRpcClient.messageRpc()` **同一个调用形态**
                            // （第一段 applyTo 挂入向预算，第二段压 2s 客户端封顶），
                            // 且出向下游类型也一致（`message-rpc`）。
                            MessageServiceGrpc.MessageServiceBlockingStub stub =
                                    MessageServiceGrpc.newBlockingStub(channel);
                            MessageServiceGrpc.MessageServiceBlockingStub capped =
                                    GoStyleClientCallTimeout.capByClientTimeout(
                                            GoStyleRequestDeadline.applyTo(stub));
                            io.grpc.Deadline deadline = capped.getCallOptions().getDeadline();
                            outboundRemainingNanos.set(deadline == null
                                    ? -1L
                                    : deadline.timeRemaining(TimeUnit.NANOSECONDS));

                            observer.onNext(GetArticleResponse.getDefaultInstance());
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

    // ====================================================================================
    // ① 真 wire + 真共享层拦截器 ⇒ 载体在 handler 线程真的绑上了（L4 活体）
    //    ⚠️ 拆成**三个场景**，因为单场景判别力不足（详见 LONG_CLIENT_DEADLINE_MILLIS 的注释）：
    //       ①a 载体可见       ⇒ 证「共享层在 handler 线程真的绑了」
    //       ①b 长预算 → 2s   ⇒ 证「2s 客户端封顶真的生效」
    //       ①c 短预算 → 继承 ⇒ 证「出向上界真的继承了入向剩余，而不是无脑套 2s」
    // ====================================================================================

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

        ArticleServiceGrpc.ArticleServiceBlockingStub stub =
                ArticleServiceGrpc.newBlockingStub(channel)
                        .withDeadlineAfter(clientDeadlineMillis, TimeUnit.MILLISECONDS);

        Status.Code code = Status.Code.OK;
        try {
            stub.getArticle(GetArticleRequest.getDefaultInstance());
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
                        + "⇒ 否则「继承入向 deadline」这一半在机制上就不成立")
                .isTrue();

        assertThat(o.carrierRemainingNanos)
                .as("载体剩余预算必须是**纳秒**且 > 0（-1 = 载体在位但没有预算 ⇒ 异常）")
                .isPositive();

        assertThat(o.carrierRemainingNanos)
                .as("🔴 客户端给的 %dms 必须**穿过 wire** 到了服务端"
                        + "（若服务端读到的是毫秒级 1500 或更少，说明 grpc-timeout 没传过来 ⇒ 载体绑了个空预算）",
                        CLIENT_DEADLINE_MILLIS)
                .isGreaterThan(TimeUnit.MILLISECONDS.toNanos(CLIENT_DEADLINE_MILLIS) - 300_000_000L);
    }

    @Test
    @DisplayName("🔴 入向预算**长于** 2s ⇒ 出向上界被压到 2s（客户端封顶真的生效，不是原样返回）")
    void outboundIsCappedAtTwoSecondsWhenInboundBudgetIsLonger() {
        Observation o = callAndObserve(LONG_CLIENT_DEADLINE_MILLIS);

        assertThat(o.statusCode)
                .as("客户端调用必须成功返回")
                .isEqualTo(Status.Code.OK);
        assertThat(o.carrierPresent)
                .as("载体必须在 handler 线程可见")
                .isTrue();
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

    // ====================================================================================
    // ② 纳秒精度：域内出向计算点**不截断**亚毫秒（可确定性分辨毫秒版）
    // ====================================================================================

    @Test
    @DisplayName("🔴 域内出向计算点按**纳秒**取预算（毫秒版会把 10.9ms 砍成 10ms ⇒ 本条转红）")
    void outboundBudgetKeepsNanosecondPrecision() {
        ManagedChannel local = InProcessChannelBuilder.forName("article-precision-only")
                .directExecutor().build();
        try {
            MessageServiceGrpc.MessageServiceBlockingStub stub =
                    MessageServiceGrpc.newBlockingStub(local);

            long carrierNow;
            long outbound;
            long budgetNanos = KNOWN_BUDGET_NANOS;

            // 🔴🔴 **必须先热身**：整条路径（`bindAbsoluteNanos` → `applyTo` →
            //    `capByClientTimeout` → `Deadline.after` → `timeRemaining`）第一次跑要付
            //    **类加载 + JIT** 的钱（本机实测 ≈ 0.15ms）⇒ ⇒ 那笔钱会**混进 loss**
            //    ⇒ ⇒ 不热身就会读到「loss ≈ 0.15ms」而误判成精度损失。
            //    ⇒ 热身跑的是**同一条路径**（同样在 scope 内、同样带载体）。
            try (GoStyleRequestDeadline.Scope ignored = GoStyleRequestDeadline.bindAbsoluteNanos(
                    System.nanoTime() + budgetNanos)) {
                GoStyleClientCallTimeout.capByClientTimeout(
                                GoStyleRequestDeadline.applyTo(stub))
                        .getCallOptions().getDeadline();
            }

            try (GoStyleRequestDeadline.Scope ignored = GoStyleRequestDeadline.bindAbsoluteNanos(
                    System.nanoTime() + budgetNanos)) {
                // 🔴 **基线**：此刻载体自己还剩多少（在同一线程、同一载体上直接读）。
                //    之所以要拿它当基线，是因为「两次 nanoTime 调用之间的机器开销」
                //    （本机实测约 0.3–0.5ms：类加载 + JIT）会**混进**绝对值里
                //    ⇒ ⇒ 判据必须测**相对损失**（出向比载体窄了多少），
                //    而不是测绝对剩余值（那会把测量开销误读成精度损失）。
                carrierNow = GoStyleRequestDeadline.remainingNanos();

                MessageServiceGrpc.MessageServiceBlockingStub capped =
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
                            + " ⇒ 旧实现（读 remainingMillis）会转红",
                            PRECISION_LOSS_TOLERANCE_NANOS, budgetNanos, 900_000L)
                    .isLessThan(PRECISION_LOSS_TOLERANCE_NANOS);
        } finally {
            local.shutdownNow();
        }
    }

    // ====================================================================================
    // ③ 无载体 ⇒ 2s 客户端封顶仍然无条件在（对齐 Go 的拦截器级别 TimeoutInterceptor）
    //    ⚠️ 这一条才是**本域生产出口真正落的那一档**（Kafka consumer 线程 ⇒ 载体不可见）
    // ====================================================================================

    @Test
    @DisplayName("🔴 线程上没有载体 ⇒ 出向仍套 2s 客户端封顶（Go 侧 TimeoutInterceptor 是拦截器级别的）")
    void noCarrierStillGetsTwoSecondClientCap() {
        ManagedChannel local = InProcessChannelBuilder.forName("article-no-carrier")
                .directExecutor().build();
        try {
            MessageServiceGrpc.MessageServiceBlockingStub stub =
                    MessageServiceGrpc.newBlockingStub(local);

            assertThat(GoStyleRequestDeadline.isPresent())
                    .as("本用例的前提：当前线程**没有**载体（否则测的就不是「无预算」这条路径）"
                            + "；本域生产出口正是在 Kafka consumer 线程上 ⇒ 落的就是这一档")
                    .isFalse();

            MessageServiceGrpc.MessageServiceBlockingStub capped =
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
