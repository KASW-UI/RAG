package com.rag.like.like.api;

import com.rag.common.observability.GoStyleRequestDeadline;
import com.rag.common.protection.GoStyleOutboundDeadlineInterceptor;
import com.rag.common.protection.GoStyleProtectionProperties;
import com.rag.like.like.api.client.LikeRpcClient;
import com.rag.like.like.proto.GetLikeCountReq;
import com.rag.like.like.proto.GetLikeCountResp;
import com.rag.like.like.proto.LikeCountItem;
import com.rag.like.like.proto.LikeServiceGrpc;
import io.grpc.Channel;
import io.grpc.ClientInterceptors;
import io.grpc.Context;
import io.grpc.Deadline;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DRIFT-LIKEAPI-017 的守门测试 ——
 * <b>单次 RPC 调用必须有「固定 2000ms」这个独立上界</b>。
 *
 * <h2>Go 侧的两个独立旋钮（缺一就是漂移）</h2>
 * <ol>
 *   <li><b>HTTP 总预算 3000ms</b> ——
 *       {@code service/common/observability/http.go:53}
 *       {@code ctx, cancel := context.WithTimeout(r.Context(), timeout)}
 *       （timeout 来自 {@code go-zero rest/config.go:55} 的
 *       {@code Timeout int64 `json:",default=3000"`}）。
 *       RAG 侧等价物 = {@link GoStyleRequestDeadline}。</li>
 *   <li><b>单次 RPC 上界 2000ms</b> ——
 *       {@code zrpc/config.go:31} {@code Timeout int64 `json:",default=2000"`}
 *       → {@code zrpc/client.go:70-71} {@code if c.Timeout > 0 { … WithTimeout(2000ms) }}
 *       → {@code zrpc/internal/clientinterceptors/timeoutinterceptor.go:25}
 *       {@code ctx, cancel := context.WithTimeout(ctx, t)}。</li>
 * </ol>
 *
 * <p>Go 的 {@code context.WithTimeout(parent, 2s)} 在「父 deadline 更早」时<b>取更早者</b>
 * ⇒ 实际有效值 = {@code min(2000ms, 3000ms − 已耗时)}。
 *
 * <h2>⚠️ 为什么必须起一个<b>真</b> gRPC server（而不是断言 stub 上的字段）</h2>
 * <p>「stub 上挂了 deadline」只证明<b>配置</b>；本类证明<b>行为</b>：
 * 服务端被<b>真的</b>掐断在 2 秒，而<b>服务端自己观测到的 deadline</b> 也确实是 2 秒。
 * <p>判据设计成<b>成功 / 失败二分</b>，不存在「两者都绿」的可能：
 * 服务端睡 <b>2500ms</b>（正落在 Go 的 2s~3s 窗口内）⇒
 * <ul>
 *   <li>有 2s 上界 ⇒ 客户端 <b>DEADLINE_EXCEEDED</b>；</li>
 *   <li>没有 2s 上界（只有 HTTP 的 3s） ⇒ 客户端 <b>成功</b>拿到响应。</li>
 * </ul>
 * ⇒ 这正是 Go 与修前 RAG 的可观察差异（台账 §9 U-04 记的「下游耗时 2.5s 时 RAG 真的放行」）。
 *
 * <h2>⚠️ 反向对照（防止「测试永远红」那种假绿）</h2>
 * <p>{@code capLargerThanServerSleepSucceeds} 把上界调到 5s ⇒ 上界不再是瓶颈 ⇒ 调用<b>成功</b>。
 * ⇒ 证明这套判据能区分「成功」与「失败」，不是「一律抛异常」。
 *
 * <h2>🔴 2026-10-31（P2-T04 · G2 lane）：本类改为验「共享层上界经域 client 生效」</h2>
 * <p>原先本类的上界来自<b>域侧内联</b>（{@code LikeRpcClient.likeRpc()} 自己算
 * {@code min(2000ms, 剩余预算)} 并套到 stub 上）⇒ ⇒ 那份内联已被删除
 * （域侧只配参数 ⇒ 上界唯一由共享层 {@code GoStyleOutboundDeadlineInterceptor} 承担）。
 * ⇒ ⇒ <b>本类的 6 条断言一条不改、语义一条不改</b>，只把<b>上界的来源</b>从「域侧内联」
 * 换成<b>共享层的生产拦截器</b>（真 {@code GoStyleOutboundDeadlineInterceptor} 装在真 channel 上）
 * ⇒ ⇒ 这 6 条现在验的是「<b>本域配的上界真被共享层施加、且经域 client 的调用路径生效</b>」
 * ⇒ ⇒ <b>覆盖面不减反增</b>：原先验的是域侧实现，现在验的是生产路径。
 * ⇒ ⇒ <b>反向对照的覆盖入口也跟着换</b>：原先调域侧 {@code LikeApiProperties}，
 * 现在调<b>共享层 cap</b>（{@code GoStyleProtectionProperties.OutboundDeadline#capMillis}）。
 */
class LikeRpcTimeoutCapTest {

    /**
     * 服务端故意睡 <b>2500ms</b> —— 落在 Go 的「单次上界 2000ms」与「HTTP 总预算 3000ms」之间。
     * <p>⇒ 有 2s 上界 ⇒ 超时；只有 3s 的 HTTP 预算 ⇒ 放行。
     */
    private static final long SERVER_SLEEP_MILLIS = 2500L;

    private static Server server;
    /** in-process server 名（channel 每个用例现建 ⇒ 因为不同用例的 cap 不同）。 */
    private static String serverName;
    /** 每个用例一条 channel（cap 不同 ⇒ 拦截器不同 ⇒ 不能共用）。 */
    private ManagedChannel channel;

    /** 服务端观测到的 deadline（{@code null} = 客户端<b>没有</b>传 deadline 过来）。 */
    private static final AtomicReference<Deadline> serverObservedDeadline = new AtomicReference<>();
    /**
     * ⚠️ <b>必须在 handler 里当场取值</b>：{@link Deadline} 是<b>不可变</b>对象，
     * {@code timeRemaining(...)} 是<b>调用那一刻</b>才算出来的 ——
     * 若把 Deadline 对象存起来、事后才读 remaining，读到的会是<b>已过期</b>的负数。
     */
    private static final AtomicLong serverObservedRemainingMillis = new AtomicLong(-1L);
    private static final AtomicInteger serverCallCount = new AtomicInteger();

    @BeforeAll
    static void startServer() throws Exception {
        serverName = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(serverName)
                .addService(new LikeServiceGrpc.LikeServiceImplBase() {
                    @Override
                    public void getLikeCount(GetLikeCountReq request,
                                            StreamObserver<GetLikeCountResp> responseObserver) {
                        serverCallCount.incrementAndGet();
                        // ⭐ 服务端自己看到的 deadline —— 这是「deadline 真的传过去了」的证据
                        Deadline observed = Context.current().getDeadline();
                        serverObservedDeadline.set(observed);
                        // ⚠️ 当场取值：Deadline 不可变，timeRemaining 是「此刻」才算的
                        serverObservedRemainingMillis.set(observed == null
                                ? -1L
                                : observed.timeRemaining(TimeUnit.MILLISECONDS));
                        try {
                            Thread.sleep(SERVER_SLEEP_MILLIS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            responseObserver.onError(new StatusRuntimeException(
                                    Status.CANCELLED.withDescription("server interrupted")));
                            return;
                        }
                        try {
                            responseObserver.onNext(GetLikeCountResp.newBuilder()
                                    .putCounts("1", LikeCountItem.newBuilder().setLikeCount(1L).build())
                                    .build());
                            responseObserver.onCompleted();
                        } catch (RuntimeException ignored) {
                            // 客户端已超时取消 ⇒ 往已关闭的流上写会抛，忽略即可
                        }
                    }
                })
                .build()
                .start();
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (server != null) {
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private LikeRpcClient client;

    @BeforeEach
    void setUp() {
        // ⚠️ 上界来源 = **共享层的生产拦截器**（域侧内联已删 ⇒ 见类 javadoc 的「上界已下沉共享层」）
        GoStyleProtectionProperties props = new GoStyleProtectionProperties();
        props.getOutboundDeadline().setCapMillis(2000L);
        channel = InProcessChannelBuilder.forName(serverName).build();
        Channel capped = ClientInterceptors.intercept(channel,
                new GoStyleOutboundDeadlineInterceptor(props));
        client = new LikeRpcClient();
        ReflectionTestUtils.setField(client, "likeServiceBlockingStub",
                LikeServiceGrpc.newBlockingStub(capped));
        GoStyleRequestDeadline.clear();
        serverObservedDeadline.set(null);
        serverObservedRemainingMillis.set(-1L);
        serverCallCount.set(0);
    }

    @AfterEach
    void tearDown() {
        // ThreadLocal 残留会把下一个用例变成假绿/假红
        GoStyleRequestDeadline.clear();
        if (channel != null) {
            channel.shutdownNow();
        }
    }

    private static GetLikeCountReq req() {
        return GetLikeCountReq.newBuilder()
                .setTargetType("article")
                .addAllTargetIds(java.util.List.of("1"))
                .build();
    }

    private void setHttpBudgetMillis(long millis) {
        GoStyleRequestDeadline.begin(System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis));
    }

    // ==================================================================
    // 1. 单次上界本身（无 HTTP 预算）
    // ==================================================================

    @Test
    @DisplayName("🔴 无 HTTP 预算时，单次 RPC 仍必须封顶 2000ms（Go zrpc/config.go:31 的 struct tag 默认）")
    void withoutHttpBudgetTheCallIsStillCappedAt2000ms() {
        assertTrue(!GoStyleRequestDeadline.isPresent(), "本用例的前提就是「没有请求级 deadline」");

        long start = System.nanoTime();
        StatusRuntimeException e = assertThrows(StatusRuntimeException.class,
                () -> client.likeRpc().getLikeCount(req()),
                "服务端睡 2500ms；没有 2000ms 上界时这次调用应当<b>成功</b>返回 —— "
                        + "能抛异常恰恰说明上界生效了（但若上界缺失，这里就会红）");
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertEquals(Status.Code.DEADLINE_EXCEEDED, e.getStatus().getCode(),
                "必须是 DEADLINE_EXCEEDED（Go ctx 到期的等价形态），不是别的错误码");
        assertTrue(elapsedMillis < SERVER_SLEEP_MILLIS,
                "必须在服务端应答（" + SERVER_SLEEP_MILLIS + "ms）之前就收场，实际耗时 " + elapsedMillis
                        + "ms —— 等满说明 2000ms 上界没生效");
    }

    // ==================================================================
    // 2. HTTP 预算 3s ⇒ 仍然是 2s（Go 的 min 语义）
    // ==================================================================

    @Test
    @DisplayName("🔴 HTTP 预算 3000ms 时，单次调用仍封顶 2000ms（Go 取 min(2s, 3s−已耗时)）")
    void httpBudgetOf3sDoesNotRaiseTheSingleCallCap() {
        setHttpBudgetMillis(3000L);

        long start = System.nanoTime();
        StatusRuntimeException e = assertThrows(StatusRuntimeException.class,
                () -> client.likeRpc().getLikeCount(req()),
                "HTTP 预算 3s > 服务端耗时 2.5s ⇒ 没有 2000ms 上界时这次调用应当<b>成功</b>；"
                        + "抛异常说明 2s 上界真的生效了");
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertEquals(Status.Code.DEADLINE_EXCEEDED, e.getStatus().getCode());
        assertTrue(elapsedMillis < SERVER_SLEEP_MILLIS,
                "实际耗时 " + elapsedMillis + "ms —— 必须早于服务端应答的 " + SERVER_SLEEP_MILLIS
                        + "ms；等满说明单次上界（2s）没生效，RAG 把 Go 会判超时的调用放了过去");
    }

    @Test
    @DisplayName("🔴 服务端观测到的 deadline 是 2000ms（**不是** HTTP 预算的 3000ms）")
    void serverObservesThe2000msCapNotTheHttpBudget() {
        setHttpBudgetMillis(3000L);

        assertThrows(StatusRuntimeException.class, () -> client.likeRpc().getLikeCount(req()));

        Deadline observed = serverObservedDeadline.get();
        assertNotNull(observed,
                "服务端<b>没有</b>观测到 deadline ⇒ 客户端根本没把 deadline 传过来 ⇒ "
                        + "「单次调用上界」与「HTTP 预算」都没生效");
        long remainingMillis = serverObservedRemainingMillis.get();
        assertTrue(remainingMillis <= 2000L,
                "服务端观测到的剩余预算 " + remainingMillis
                        + "ms 必须 ≤ 2000ms（Go 的单次上界）；> 2000 说明只传了 HTTP 的 3000ms 预算");
        assertTrue(remainingMillis > 1500L,
                "服务端观测到的剩余预算 " + remainingMillis
                        + "ms 明显小于 2000ms ⇒ deadline 传过去时已经损耗太多，判据需重估");
    }

    // ==================================================================
    // 3. HTTP 预算比上界更小 ⇒ 取更小者
    // ==================================================================

    @Test
    @DisplayName("🔴 HTTP 预算 500ms 时取更小者（Go 的 min 语义），而不是被 2s 上界顶回去")
    void httpBudgetSmallerThanTheCapWins() {
        setHttpBudgetMillis(500L);

        long start = System.nanoTime();
        StatusRuntimeException e = assertThrows(StatusRuntimeException.class,
                () -> client.likeRpc().getLikeCount(req()));
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertEquals(Status.Code.DEADLINE_EXCEEDED, e.getStatus().getCode());
        assertTrue(elapsedMillis < 1500L,
                "HTTP 预算只有 500ms ⇒ 必须很快收场，实际耗时 " + elapsedMillis
                        + "ms —— 若接近 2000ms 说明取的是「上界」而不是 min(上界, 剩余预算)");
    }

    // ==================================================================
    // 4. 预算耗尽 ⇒ 立刻到期（不是「没有 deadline」）
    // ==================================================================

    @Test
    @DisplayName("🔴 HTTP 预算已耗尽时立刻失败（不是退化成「没有 deadline」）")
    void exhaustedHttpBudgetFailsImmediately() {
        GoStyleRequestDeadline.begin(System.nanoTime() - 1L);   // 已经过去

        long start = System.nanoTime();
        StatusRuntimeException e = assertThrows(StatusRuntimeException.class,
                () -> client.likeRpc().getLikeCount(req()));
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertEquals(Status.Code.DEADLINE_EXCEEDED, e.getStatus().getCode());
        assertTrue(elapsedMillis < 1000L,
                "预算已耗尽 ⇒ 必须立刻失败，实际耗时 " + elapsedMillis
                        + "ms —— 若接近服务端睡眠时间，说明退化成了「没有 deadline」");
    }

    // ==================================================================
    // 5. 反向对照：上界不是瓶颈时应当成功
    // ==================================================================

    @Test
    @DisplayName("✅ 反向对照：上界调到 5s（大于服务端耗时）时调用必须**成功**")
    void capLargerThanServerSleepSucceeds() {
        // ⚠️ 上界来源已改为共享层 ⇒ 这里调的是**共享层的 cap**（不再是域侧 properties）
        GoStyleProtectionProperties wideProps = new GoStyleProtectionProperties();
        wideProps.getOutboundDeadline().setCapMillis(5000L);
        ManagedChannel wideChannel = InProcessChannelBuilder.forName(serverName).build();
        try {
            Channel cappedWide = ClientInterceptors.intercept(wideChannel,
                    new GoStyleOutboundDeadlineInterceptor(wideProps));
            LikeRpcClient wideCapClient = new LikeRpcClient();
            ReflectionTestUtils.setField(wideCapClient, "likeServiceBlockingStub",
                    LikeServiceGrpc.newBlockingStub(cappedWide));

            setHttpBudgetMillis(3000L);

            GetLikeCountResp resp = assertDoesNotThrow(
                    () -> wideCapClient.likeRpc().getLikeCount(req()),
                    "🔴 上界调到 5s（> 服务端耗时 " + SERVER_SLEEP_MILLIS + "ms）⇒ 调用**应当成功**"
                            + "⇒ ⇒ 若这里抛了异常 ⇒ ⇒ 说明**有别的东西把上界压住了**"
                            + "（例如域侧那份写死 2000ms 的副本）⇒ ⇒ 「上界可放宽」这件事在域侧根本没生效。"
                            + "⇒ ⇒ 这条是**反向对照**：它保证本类能区分「成功」与「失败」，不是一律抛异常。");

            assertNotNull(resp, "上界 5s > 服务端耗时 2.5s ⇒ 调用应当成功返回");
            assertEquals(1L, resp.getCountsMap().get("1").getLikeCount(),
                    "拿到的应是服务端真正算出来的响应 —— 这条证明前面的「超时」不是因为调用本身有问题");
            assertEquals(1, serverCallCount.get(), "服务端应当被真的调用到 1 次");
        } finally {
            wideChannel.shutdownNow();
        }
    }
}
