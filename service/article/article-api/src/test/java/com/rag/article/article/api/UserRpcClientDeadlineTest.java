package com.rag.article.article.api;

import com.rag.article.article.api.client.UserRpcClient;
import com.rag.common.observability.GoStyleRequestDeadline;
import com.rag.users.user.proto.UserServiceGrpc;
import io.grpc.Channel;
import io.grpc.Deadline;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code user-rpc} 的 <b>per-client 单次调用预算</b> —— DRIFT-ARTICLE-API-002 的守门网。
 *
 * <h2>Go 出处（三段，缺一不可）</h2>
 * <pre>
 * // ① 声明点：go-zero v1.10.2 · zrpc/config.go:31
 *   Timeout int64 `json:",default=2000"`
 *
 * // ② yaml：service/article/api/etc/article-api.yaml:20-24 —— UserRpcConf **没有** Timeout
 *   ⇒ 走 ① 的 tag 默认 = 2000ms
 *   （⚠️ 同一份 yaml 里 ArticleRpcConf.Timeout 是**显式** 70000 ⇒ 两个 client 差 35 倍，
 *     而逐字段对 yaml 时看不出这个差别 —— AGENTS.md §3.3.1 点名的形态）
 *
 * // ③ 消费点：zrpc/client.go:70-71 → zrpc/internal/client.go:83（buildUnaryInterceptors）
 * //            → zrpc/internal/clientinterceptors/timeoutinterceptor.go:25
 *   ctx, cancel := context.WithTimeout(ctx, t)
 * </pre>
 *
 * <h2>⚠️ 语义是「取父子 deadline 中较早的」，不是「覆盖」</h2>
 * <p>Go 的 {@code context.WithTimeout(ctx, 2s)}：父 ctx（HTTP 70s 预算）剩余不足 2s 时，
 * <b>父 ctx 赢</b>。⇒ Java 侧必须显式算 {@code min}。
 *
 * <p>⚠️ <b>grpc-java 不能替我们做这个合并</b>：
 * {@code io.grpc.CallOptions.withDeadline} 的字节码是 {@code builder.deadline = deadline}
 * —— <b>直接覆盖</b>（grpc-api 1.66.0 实测）。
 * ⇒ 若写成 {@code applyTo(stub).withDeadlineAfter(2, SECONDS)}，HTTP 剩余预算会被顶掉
 * （剩 0.5s 时反而给 2s）⇒ 请求可能超出 HTTP 总预算。
 */
class UserRpcClientDeadlineTest {

    /** Go 侧的 per-client 上限：{@code zrpc/config.go:31} 的 tag 默认 2000ms。 */
    private static final long GO_CLIENT_TIMEOUT_MILLIS = 2000L;

    @AfterEach
    void clearBudget() {
        GoStyleRequestDeadline.clear();
    }

    @Test
    @DisplayName("无 HTTP 预算 ⇒ user-rpc 的 deadline 是 **2000ms**（Go 的 zrpc tag 默认）")
    void withoutHttpBudgetUsesGoClientDefault() {
        UserRpcClient client = newClient();

        long remaining = remainingMillisOf(client);

        assertTrue(remaining > 0 && remaining <= GO_CLIENT_TIMEOUT_MILLIS,
                "无 HTTP 预算时 deadline 应为 2000ms 之内，实际=" + remaining);
    }

    @Test
    @DisplayName("HTTP 预算 10s ⇒ deadline 仍是 **2000ms**（取 min，不是 10s）")
    void httpBudgetLargerThanClientCapStillUsesClientCap() {
        UserRpcClient client = newClient();
        beginBudget(10_000L);

        long remaining = remainingMillisOf(client);

        assertTrue(remaining > 0 && remaining <= GO_CLIENT_TIMEOUT_MILLIS,
                "客户端 2s 上限应压过 10s 的 HTTP 预算，实际=" + remaining);
    }

    @Test
    @DisplayName("HTTP 预算 500ms ⇒ deadline 是 **500ms**（取 min：grpc-java 的 withDeadline 是覆盖，必须自己算）")
    void httpBudgetSmallerThanClientCapWins() {
        UserRpcClient client = newClient();
        beginBudget(500L);

        long remaining = remainingMillisOf(client);

        assertTrue(remaining > 0 && remaining <= 500L,
                "HTTP 剩余预算更短时应以它为准（Go 的 context.WithTimeout 取父子较早者），实际=" + remaining);
    }

    @Test
    @DisplayName("HTTP 预算已耗尽 ⇒ deadline **立即到期**（不能因为 2s 上限而重新获得时间）")
    void exhaustedHttpBudgetStaysExpired() {
        UserRpcClient client = newClient();
        // 已经过去的时刻 ⇒ 预算耗尽
        GoStyleRequestDeadline.begin(System.nanoTime() - TimeUnit.SECONDS.toNanos(1L));

        long remaining = remainingMillisOf(client);

        assertTrue(remaining <= 0L,
                "预算耗尽时不应因为 2s 上限而重新拿到 2s，实际=" + remaining);
    }

    // ==================== helpers ====================

    private void beginBudget(long millis) {
        GoStyleRequestDeadline.begin(System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis));
    }

    private long remainingMillisOf(UserRpcClient client) {
        Deadline deadline = client.userRpc().getCallOptions().getDeadline();
        if (deadline == null) {
            return Long.MAX_VALUE;
        }
        return deadline.timeRemaining(TimeUnit.MILLISECONDS);
    }

    /**
     * 造一个只带 stub、不带 channel 行为的 client。
     * <p>本测试只关心 <b>deadline 怎么算</b>，不关心调用是否真的发出 ——
     * channel 是 mock，不会产生任何网络行为。
     */
    private UserRpcClient newClient() {
        UserRpcClient client = new UserRpcClient();
        Channel channel = Mockito.mock(Channel.class);
        ReflectionTestUtils.setField(client, "userServiceBlockingStub",
                UserServiceGrpc.newBlockingStub(channel));
        return client;
    }

    @Test
    @DisplayName("Go 的上限常量就是 2000ms（防「有人顺手改成 30s」—— yaml 里看不到这个数）")
    void goClientTimeoutConstantIsTwoSeconds() {
        assertEquals(2000L, GO_CLIENT_TIMEOUT_MILLIS,
                "zrpc/config.go:31 的 tag 默认是 2000；改了它就是一条未登记的语义变更");
    }
}
