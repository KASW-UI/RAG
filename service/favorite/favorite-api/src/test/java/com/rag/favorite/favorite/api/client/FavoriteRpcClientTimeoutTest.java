package com.rag.favorite.favorite.api.client;

import com.rag.favorite.favorite.proto.FavoriteServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 证明 favorite-api 的 **gRPC client 超时被压在 Go 的 2s**，而不是一路顶到 HTTP 超时。
 *
 * <p><b>为什么这条不能省</b>：Go 侧 api → rpc 有<b>两层</b>预算，
 * <b>取更小的生效</b>：
 * <ul>
 *   <li><b>ctx 预算 3s</b>：Go {@code rest/config.go:55} 的 {@code Timeout default=3000}
 *       （+ {@code timeouthandler.go:68} 的 {@code r.WithContext(ctx)}）；</li>
 *   <li><b>zrpc client 超时 2s</b>：Go {@code zrpc/config.go:29} 的
 *       {@code RpcClientConf.Timeout int64 `json:",default=2000"`}，
 *       经 {@code zrpc/internal/client.go:115-116} 的
 *       {@code clientinterceptors.TimeoutInterceptor(timeout)} 生效。</li>
 * </ul>
 * ⇒ Go 侧下游挂死时是 <b>2s 先到</b> ⇒ api 拿到 rpc error ⇒ 返回 <b>200 + 业务码 1015</b>；
 * 若只带 3s 的 ctx 预算，就会一路顶到 HTTP 超时分支 ⇒ 返回 <b>503 "Request Timeout"</b>，
 * 且状态码与 body 不自洽。
 *
 * <p><b>⚠️ 为什么用真的 in-process channel</b>：直接断言常量「== 2000」是
 * 「拿 RAG 的值验 RAG 的值」（AGENTS.md §3.3：绿测试可能是错误答案的守卫）。
 * ⇒ 这里用<b>真实</b>的 {@link io.grpc.inprocess.InProcessServerBuilder} +
 * {@link io.grpc.inprocess.InProcessChannelBuilder} 造出真实的 stub，
 * 再断言<b>压完之后读回来的 deadline</b> 确实 ≤ 2s —— 验证的是行为，不是数字。
 *
 * <p><b>⚠️ 证据边界</b>：本类验的是「deadline 被正确压到 2s」，
 * <b>不是</b>「下游真的挂死时客户端在 2s 准时返回」这条端到端计时。
 * 后者需要让服务端 sleep 超过 2s 并断言调用耗时（message 域的
 * {@code GoStyleGrpcClientBreakerE2ETest} 是那类形态的参考）。
 */
class FavoriteRpcClientTimeoutTest {

    private static Server server;
    private static ManagedChannel channel;

    @BeforeAll
    static void startServer() throws Exception {
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name).directExecutor().build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
    }

    @AfterAll
    static void stopServer() {
        if (channel != null) {
            channel.shutdownNow();
        }
        if (server != null) {
            server.shutdownNow();
        }
    }

    @Test
    @DisplayName("客户端超时常量 = Go 的 default=2000（zrpc/config.go:29）")
    void clientTimeoutConstantMatchesGoDefault() {
        assertEquals(2000L, FavoriteRpcClient.CLIENT_TIMEOUT_MILLIS,
                "Go zrpc/config.go:29 的 RpcClientConf.Timeout default=2000；"
                        + "改这个值等于改了 Go 的行为");
    }

    @Test
    @DisplayName("没有 ctx 预算时也会被压到 2s（Go 的客户端超时是拦截器级别的，与 HTTP 超时中间件无关）")
    void capsDeadlineEvenWithoutCtxBudget() {
        FavoriteServiceGrpc.FavoriteServiceBlockingStub stub =
                FavoriteServiceGrpc.newBlockingStub(channel);
        assertEquals(null, stub.getCallOptions().getDeadline(),
                "前置条件：原始 stub 没有 deadline");

        FavoriteServiceGrpc.FavoriteServiceBlockingStub capped =
                FavoriteRpcClient.capByClientTimeout(stub);

        assertNotNull(capped.getCallOptions().getDeadline(),
                "没有 ctx 预算时也必须套上 2s —— 否则就成了「有时有客户端超时、有时没有」");
        long remaining = capped.getCallOptions().getDeadline().timeRemaining(TimeUnit.NANOSECONDS);
        assertTrue(remaining > 0L && remaining <= TimeUnit.MILLISECONDS.toNanos(2000L),
                "deadline 必须 ≤ 2s，实际剩余 = " + remaining + " ns");
    }

    @Test
    @DisplayName("ctx 预算比 2s 短时，取更小的那个（Go 的 WithTimeout 语义）")
    void keepsTheShorterCtxBudget() {
        FavoriteServiceGrpc.FavoriteServiceBlockingStub stub =
                FavoriteServiceGrpc.newBlockingStub(channel)
                        .withDeadlineAfter(500L, TimeUnit.MILLISECONDS);

        FavoriteServiceGrpc.FavoriteServiceBlockingStub capped =
                FavoriteRpcClient.capByClientTimeout(stub);

        long remaining = capped.getCallOptions().getDeadline().timeRemaining(TimeUnit.NANOSECONDS);
        assertTrue(remaining > 0L && remaining <= TimeUnit.MILLISECONDS.toNanos(500L),
                "ctx 预算 500ms 更短 ⇒ 必须保留 500ms，实际剩余 = " + remaining + " ns");
    }

    @Test
    @DisplayName("ctx 预算比 2s 长时，压到 2s（Go 侧下游挂死是 2s 先到）")
    void capsLongerCtxBudgetToTwoSeconds() {
        FavoriteServiceGrpc.FavoriteServiceBlockingStub stub =
                FavoriteServiceGrpc.newBlockingStub(channel)
                        .withDeadlineAfter(10L, TimeUnit.SECONDS);

        FavoriteServiceGrpc.FavoriteServiceBlockingStub capped =
                FavoriteRpcClient.capByClientTimeout(stub);

        long remaining = capped.getCallOptions().getDeadline().timeRemaining(TimeUnit.NANOSECONDS);
        assertTrue(remaining > 0L && remaining <= TimeUnit.MILLISECONDS.toNanos(2000L),
                "ctx 预算 10s 更长 ⇒ 必须压到 2s，实际剩余 = " + remaining + " ns");
    }
}
