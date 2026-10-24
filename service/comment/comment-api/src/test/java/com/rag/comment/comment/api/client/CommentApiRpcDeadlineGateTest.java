package com.rag.comment.comment.api.client;

import com.rag.common.observability.GoStyleRequestDeadline;
import com.rag.comment.comment.proto.CommentServiceGrpc;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 <b>MISMATCH-API-03 的守门网</b> —— 锁住「comment-api → comment-rpc 的 2 秒 deadline 上限」。
 *
 * <h2>本轮修掉的漂移</h2>
 * <p><b>Go 事实</b>：{@code go-zero@v1.10.2/zrpc/config.go:31}
 * {@code RpcClientConf.Timeout int64 `json:",default=2000"}` ⇒ <b>2000ms</b>；
 * {@code zrpc/internal/client.go:115-116} 装 {@code TimeoutInterceptor(timeout)}；
 * {@code etc/commentservice.yaml:21-23} <b>没覆盖</b> {@code Timeout} ⇒ 生效值 = 2s。
 * <p><b>修复前 RAG 现状</b>：{@code CommentRpcClient.commentRpc()} 只套
 * {@code GoStyleRequestDeadline.applyTo(...)} ⇒ 取自 HTTP 预算 <b>3s</b>
 * （{@code application.yml:140} {@code http-timeout: 3s}）⇒ <b>有效 deadline = 3s，无 2s 上限</b>。
 * <p><b>可观察后果</b>：下游耗时落在 <b>2.0–3.0s</b> 时 Go 返 {@code DEADLINE_EXCEEDED}、<b>RAG 成功</b>。
 *
 * <h2>为什么断言「行为」而不是「CallOptions 上挂了 deadline」</h2>
 * <p>本仓自己的纪律（{@code service/common/.../GoStyleRequestDeadlineTest.java:56}）：
 * 「断言 stub 上挂了 CallOptions」是断言<b>配置</b>，不是断言<b>行为</b>。
 * ⇒ 本类起一个<b>真 in-process gRPC server</b>，服务端方法<b>故意睡到 deadline 之后</b>，
 * 观察客户端调用**以什么结局收场**。
 *
 * <h2>三个用例各自能区分什么</h2>
 * <table border="1">
 *   <caption>「有 2s 上限」与「没有 2s 上限」的判别设计</caption>
 *   <tr><th>用例</th><th>HTTP 预算</th><th>服务端睡眠</th><th>有 2s 上限</th><th>回退成 applyTo（变异）</th></tr>
 *   <tr><td>①</td><td>无</td><td>2.5s</td><td><b>DEADLINE_EXCEEDED</b></td><td><b>OK</b>（无 deadline ⇒ 调用成功）</td></tr>
 *   <tr><td>②</td><td>30s</td><td>2.5s</td><td><b>DEADLINE_EXCEEDED</b></td><td><b>OK</b>（30s 预算够用）</td></tr>
 *   <tr><td>③</td><td>3s</td><td>5s</td><td><b>DEADLINE_EXCEEDED</b> 且 <b>≈2s</b></td>
 *       <td>DEADLINE_EXCEEDED 但 <b>≈3s</b> ⇒ 靠<b>耗时</b>区分</td></tr>
 * </table>
 *
 * <p>⚠️ 用例 ①② 的判别靠<b>结局码不同</b>（DEADLINE_EXCEEDED vs OK）⇒ 不依赖计时，稳定。
 * <p>⚠️ 用例 ③ 与生产形态完全一致（HTTP 预算 3s），但两边的结局码<b>相同</b>
 * ⇒ 只能靠耗时区分（2s 上限 vs 3s 预算）⇒ 阈值取 2.6s，两侧各留 0.4s 以上余量。
 *
 * <p>🧬 <b>变异实测</b>：把 {@code commentRpc()} 改回
 * {@code GoStyleRequestDeadline.applyTo(commentServiceBlockingStub)}（= 修复前）
 * ⇒ 用例 ①② 变红（拿到 OK 而不是 DEADLINE_EXCEEDED）⇒ 守门网不是假绿。
 */
class CommentApiRpcDeadlineGateTest {

    private static Server server;
    private static ManagedChannel channel;
    /** 服务端是否真的把调用跑完了（用来证明「没被 deadline 打断」）。 */
    private static final AtomicBoolean serverCompleted = new AtomicBoolean(false);

    /** 服务端的睡眠时长 —— 可按用例调整。 */
    private static volatile long serverSleepMillis = 2_500L;

    @BeforeAll
    static void startServer() throws Exception {
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name)
                // ⚠️ **不能** directExecutor()：那会让服务端 handler **跑在客户端线程上**
                // ⇒ 服务端的 Thread.sleep 会把客户端线程一起堵住 ⇒
                //    deadline 虽然准时触发了（结局码仍是 DEADLINE_EXCEEDED），
                //    但客户端**要等服务端睡完才返回** ⇒ 测不出「2s 就掐断」。
                // ⇒ 服务端跑在自己的线程池上，客户端才能在 2s 处**立即**返回。
                .addService(io.grpc.ServerServiceDefinition.builder("comment.CommentService")
                        .addMethod(
                                CommentServiceGrpc.getGetCommentMethod(),
                                io.grpc.stub.ServerCalls.asyncUnaryCall((req, obs) -> {
                                    try {
                                        Thread.sleep(serverSleepMillis);
                                        serverCompleted.set(true);
                                        obs.onNext(com.rag.comment.comment.proto.GetCommentResp
                                                .getDefaultInstance());
                                        obs.onCompleted();
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                        obs.onError(new StatusRuntimeException(Status.CANCELLED
                                                .withDescription("server interrupted")));
                                    } catch (RuntimeException e) {
                                        obs.onError(e);
                                    }
                                }))
                        .build())
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (channel != null) {
            channel.shutdownNow();
        }
        if (server != null) {
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @AfterEach
    void clearDeadline() {
        // 测试之间绝不能残留 deadline —— 残留会把下一个用例变成假绿/假红
        GoStyleRequestDeadline.clear();
        serverCompleted.set(false);
    }

    private static CommentRpcClient client() {
        CommentRpcClient c = new CommentRpcClient();
        c.rawStubField(CommentServiceGrpc.newBlockingStub(channel));
        return c;
    }

    /** 真发一次调用，返回观察到的状态码；成功则返回 {@link Status.Code#OK}。 */
    private static Status.Code call(CommentRpcClient client) {
        try {
            client.commentRpc().getComment(
                    com.rag.comment.comment.proto.GetCommentReq.newBuilder()
                            .setTargetType("article").setTargetId("a-diff").build());
            return Status.Code.OK;
        } catch (StatusRuntimeException e) {
            return e.getStatus().getCode();
        }
    }

    // ==================== 三个用例 ====================

    @Test
    @DisplayName("Go zrpc/config.go:31 default=2000 —— **无 HTTP 预算**时下游调用也必须有 2s 上限")
    void noHttpBudgetStillGetsTwoSecondCap() {
        serverSleepMillis = 2_500L;
        assertEquals(-1L, GoStyleRequestDeadline.remainingMillis(), "本用例的前提：当前线程没有 HTTP 预算");

        long start = System.nanoTime();
        Status.Code code = call(client());
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertEquals(Status.Code.DEADLINE_EXCEEDED, code,
                "Go 的 timeout 中间件是**永远装着**的（zrpc/internal/config.go:15 default=true）"
                        + "⇒ 即使没有 HTTP 预算，下游调用也必须在 2s 处被掐断。"
                        + "若观察到 OK，说明这次调用根本没有 deadline。");
        assertTrue(elapsedMillis < 2_400L,
                "必须由 **2s 上限**掐断（不是服务端 2.5s 后自己返回）⇒ 实测 " + elapsedMillis + "ms");
        assertTrue(!serverCompleted.get(), "服务端不应把调用跑完 —— 跑完说明客户端没掐断");
    }

    @Test
    @DisplayName("Go http.go:53 的 3s 预算 vs zrpc 的 2s 上限 ⇒ **2s 生效**（上限小于预算）")
    void twoSecondCapWinsOverLongerHttpBudget() {
        serverSleepMillis = 2_500L;
        GoStyleRequestDeadline.begin(System.nanoTime() + TimeUnit.SECONDS.toNanos(30));

        long start = System.nanoTime();
        Status.Code code = call(client());
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertEquals(Status.Code.DEADLINE_EXCEEDED, code,
                "HTTP 预算 30s，但 Go 的下游上限是 2s ⇒ 必须在 2s 处 DEADLINE_EXCEEDED。"
                        + "若观察到 OK，说明 2s 上限没生效。");
        assertTrue(elapsedMillis < 2_400L,
                "必须由 **2s 上限**掐断 ⇒ 实测 " + elapsedMillis + "ms");
    }

    @Test
    @DisplayName("生产形态（HTTP 预算 3s + 上限 2s）⇒ **2s 处**掐断，不是 3s")
    void productionShapeCapsAtTwoSecondsNotThree() {
        serverSleepMillis = 5_000L;
        GoStyleRequestDeadline.begin(System.nanoTime() + TimeUnit.SECONDS.toNanos(3));

        long start = System.nanoTime();
        Status.Code code = call(client());
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertEquals(Status.Code.DEADLINE_EXCEEDED, code, "两边的结局码相同，只能靠耗时区分");
        assertTrue(elapsedMillis < 2_600L,
                "必须在 **2s** 处掐断；若实测接近 3000ms，说明用的是 HTTP 的 3s 预算、"
                        + "Go 的 2s 上限没生效 ⇒ 实测 " + elapsedMillis + "ms");
    }

    @Test
    @DisplayName("预算已耗尽 ⇒ **立刻**失败（1ms），不是「没有 deadline」")
    void exhaustedBudgetMeansImmediateNotAbsent() {
        serverSleepMillis = 5_000L;
        GoStyleRequestDeadline.begin(System.nanoTime() - 1L);   // 已经过去
        assertEquals(0L, GoStyleRequestDeadline.remainingMillis());

        long start = System.nanoTime();
        Status.Code code = call(client());
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertEquals(Status.Code.DEADLINE_EXCEEDED, code);
        assertTrue(elapsedMillis < 500L,
                "预算耗尽后必须立刻失败；等满服务端睡眠时间说明根本没有 deadline ⇒ 实测 "
                        + elapsedMillis + "ms");
    }
}
