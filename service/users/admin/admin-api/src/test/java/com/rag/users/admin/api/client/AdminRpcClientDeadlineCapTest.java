package com.rag.users.admin.api.client;

import com.rag.common.observability.GoStyleRequestDeadline;
import com.rag.users.admin.proto.AdminServiceGrpc;
import io.grpc.Channel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * {@code admin-api} 出向 admin-rpc 调用的 <b>客户端超时（2s）与 min 语义</b>守门。
 *
 * <h2>它守的是什么</h2>
 * <p>Go 侧的出向调用<b>恒带 2s 客户端超时</b>（{@code go-zero@v1.10.2/zrpc/config.go:31}
 * 的 {@code Timeout int64 json:",default=2000"}，且 Go 侧
 * {@code service/user/admin/api/etc/admincenter.yaml} 的 {@code AdminRpc:} 段<b>没有</b>显式 Timeout）
 * ⇒ 而 RAG 侧 {@code devh} 注入的<b>裸 stub 没有 call deadline</b>
 * ⇒ ⇒ <b>缺「客户端侧」收口</b>。
 *
 * <h2>⚠️ 差异的准确边界（不要读宽）</h2>
 * <p>RAG 侧下游 {@code admin-rpc} <b>自己</b>带服务端 2s 超时
 * （{@code service/users/admin/admin-rpc/src/main/resources/application.yml:191} = {@code rpc-timeout: 2s}，
 * 接线在位由 {@code check-grpc-interceptor-wiring.py} = EXIT=0 实测确认）
 * ⇒ ⇒ <b>形态①「请求到达下游、业务挂死」</b>：RAG 由<b>服务端</b> 2s 收口 ⇒ {@code DEADLINE_EXCEEDED}
 * ⇒ 本域映射（Service 层 catch 的 default ＋ {@code GlobalExceptionHandler.mapRPCStatusToCode} 的 default）
 * ⇒ <b>1015</b> ＋ {@code HttpStatus.OK}
 * ⇒ ⇒ <b>与 Go 同形</b>（Go 侧 {@code admin/api/internal/logic/admin/loginlogic.go:47-57} 的 default
 * ⇒ {@code errmsg.go:6} 的 1015 ＋ {@code httpx.OkJson} = HTTP 200）。
 * ⇒ ⇒ <b>形态②「请求没到达下游」</b>（进程不可达 / 网络黑洞 / 连接建好但无响应）：
 * Go 的出向 deadline = <b>min(剩余 3s ctx, 2s 客户端) = 2s</b>
 * （3s 预算来自 {@code service/common/observability/http.go:30-34} ＋ {@code :53-56} ⇒ 传进业务 ctx）
 * ⇒ <b>200 + 1015</b>；RAG 修前<b>没有任何客户端收口</b>
 * ⇒ 只能等本进程 HTTP 预算（{@code admin-api/application.yml:205} = {@code http-timeout: 3s}）
 * ⇒ ⇒ <b>503 + {@code Request Timeout}</b>
 * ⇒ ⇒ <b>形态②才是本条要修的可观察差异</b>。
 *
 * <h2>本测试如何真正触发该机制（AGENTS.md §3.3）</h2>
 * <p>真的构造 stub（{@code newBlockingStub} + 真 {@code CallOptions}）并真的读回 deadline；
 * 且真的走 {@link GoStyleRequestDeadline#begin(long)} 设预算（api 进程的 HTTP 中间件就是这么做的），
 * 验证 min 语义的两个分支。
 *
 * <p>⚠️ <b>不覆盖什么</b>：不起真实 admin-rpc、不发真实请求
 * ⇒ 证的是「<b>出向调用的 deadline 口径</b>」，<b>不</b>证「下游挂死时客户端真的收到 1015」
 * （那需要双侧活体对拍）。
 *
 * <h2>⚠️ 为什么下界要扣掉「本用例自身耗时」</h2>
 * <p>读 deadline 是<b>墙钟</b>操作，而入站预算在推进中<b>不断变短</b>
 * ⇒ 若环境慢（首次 mock 生成 / 类加载，实测可吃掉 400ms+），
 * 固定下界（如「必须 ≥ 500ms」）会<b>假红</b>：本类首版实测 500ms 的预算只读到 72ms，
 * 而<b>被测物完全正确</b> ⇒ ⇒ 那是<b>判据的缺陷</b>，不是被测物的缺陷。
 * ⇒ 改成「期望值 − 实测耗时 − slack」⇒ 既不放过「比 Go 更严」，也不被环境抖动误伤。
 */
class AdminRpcClientDeadlineCapTest {

    private static final long SLACK_MILLIS = 200L;

    @AfterEach
    void clearBudget() {
        GoStyleRequestDeadline.clear();
    }

    @Test
    @DisplayName("无入站预算 ⇒ 出向 deadline 必须是 Go 侧的 2s 客户端超时")
    void withoutInboundBudget_capIsTwoSeconds() {
        AdminServiceGrpc.AdminServiceBlockingStub raw = rawStub();

        long beganAt = System.nanoTime();
        AdminServiceGrpc.AdminServiceBlockingStub capped = AdminRpcClient.capByClientTimeout(raw);

        assertMatchesGoMinSemantics(2000L, beganAt, capped, "无入站预算");
    }

    @Test
    @DisplayName("入站预算 < 2s ⇒ 取更早者（剩余）⇒ 与 Go 的 context.WithTimeout(ctx, 2s) 同形")
    void inboundBudgetShorterThanCap_takesTheEarlierOne() {
        // ⚠️ 先造 mock 再设预算：mock 生成耗时不能算进预算窗口（见类注释末段）。
        AdminServiceGrpc.AdminServiceBlockingStub raw = rawStub();

        long budgetMillis = 500L;
        long beganAt = System.nanoTime();
        GoStyleRequestDeadline.begin(beganAt + TimeUnit.MILLISECONDS.toNanos(budgetMillis));

        AdminServiceGrpc.AdminServiceBlockingStub capped = AdminRpcClient.capByClientTimeout(
                GoStyleRequestDeadline.applyTo(raw));

        assertMatchesGoMinSemantics(budgetMillis, beganAt, capped, "入站预算 500ms < 2s");
    }

    @Test
    @DisplayName("入站预算 > 2s ⇒ 取 2s 客户端超时（Go 的 min 语义 ⇒ RAG 不能比 Go 宽松）")
    void inboundBudgetLongerThanCap_stillCapsAtTwoSeconds() {
        AdminServiceGrpc.AdminServiceBlockingStub raw = rawStub();

        long beganAt = System.nanoTime();
        GoStyleRequestDeadline.begin(beganAt + TimeUnit.SECONDS.toNanos(5L));

        AdminServiceGrpc.AdminServiceBlockingStub capped = AdminRpcClient.capByClientTimeout(
                GoStyleRequestDeadline.applyTo(raw));

        // min(5000ms, 2000ms) = 2000ms
        assertMatchesGoMinSemantics(AdminRpcClient.CLIENT_TIMEOUT_MILLIS, beganAt, capped,
                "入站预算 5s > 2s");
    }

    @Test
    @DisplayName("入站预算已耗尽 ⇒ 出向必须立刻失败（Go 侧 ctx 到期同理）")
    void exhaustedInboundBudget_failsImmediately() {
        AdminServiceGrpc.AdminServiceBlockingStub raw = rawStub();

        GoStyleRequestDeadline.begin(System.nanoTime() - TimeUnit.SECONDS.toNanos(1L));

        AdminServiceGrpc.AdminServiceBlockingStub capped = AdminRpcClient.capByClientTimeout(
                GoStyleRequestDeadline.applyTo(raw));

        long remainingMillis = capped.getCallOptions().getDeadline().timeRemaining(TimeUnit.MILLISECONDS);
        assertTrue(remainingMillis <= 1L,
                "预算已耗尽 ⇒ 出向必须立刻失败（deadline ≤ 1ns），实际 = " + remainingMillis + "ms");
    }

    @Test
    @DisplayName("对外 accessor 必须真的套上 cap ⇒ 业务侧拿到的 stub 带 deadline")
    void accessorReturnsCappedStub() {
        AdminRpcClient client = new AdminRpcClient(mock(
                net.devh.boot.grpc.client.config.GrpcChannelsProperties.class));
        AdminServiceGrpc.AdminServiceBlockingStub raw = rawStub();
        injectRawStub(client, raw);

        long beganAt = System.nanoTime();
        AdminServiceGrpc.AdminServiceBlockingStub exposed = client.adminRpc();

        assertMatchesGoMinSemantics(AdminRpcClient.CLIENT_TIMEOUT_MILLIS, beganAt, exposed,
                "adminRpc() accessor");
    }

    // ================== 辅助 ==================

    /**
     * 断言「出向 deadline = min(入站剩余, 2s 客户端超时)」这一 Go 语义。
     *
     * @param expectedMillis Go 语义的期望值（已取好 min）
     * @param beganAt        本用例开始计时的墙钟点（{@code System.nanoTime()}）
     * @param capped         被测 stub
     * @param scene          场景描述（进断言消息）
     */
    private static void assertMatchesGoMinSemantics(long expectedMillis, long beganAt,
            AdminServiceGrpc.AdminServiceBlockingStub capped, String scene) {
        io.grpc.Deadline d = capped.getCallOptions().getDeadline();
        assertNotNull(d, scene + " ⇒ 出向 stub 必须带 call deadline（Go 侧恒有 2s 客户端超时）");
        long actualMillis = d.timeRemaining(TimeUnit.MILLISECONDS);
        long elapsedMillis = (System.nanoTime() - beganAt) / 1_000_000L;
        assertTrue(actualMillis <= expectedMillis + SLACK_MILLIS,
                scene + " ⇒ 出向 deadline 不得超过 " + expectedMillis + "ms（Go 的 min 语义），实际 = "
                        + actualMillis + "ms");
        assertTrue(actualMillis >= expectedMillis - elapsedMillis - SLACK_MILLIS,
                scene + " ⇒ 出向 deadline 应≈" + expectedMillis + "ms（已扣除本用例自身耗时 "
                        + elapsedMillis + "ms），实际 = " + actualMillis + "ms");
    }

    private static AdminServiceGrpc.AdminServiceBlockingStub rawStub() {
        return AdminServiceGrpc.newBlockingStub(mock(Channel.class));
    }

    private static void injectRawStub(AdminRpcClient client, AdminServiceGrpc.AdminServiceBlockingStub raw) {
        try {
            var f = AdminRpcClient.class.getDeclaredField("adminServiceBlockingStub");
            f.setAccessible(true);
            f.set(client, raw);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("注入裸 stub 失败（测试自身的问题，不是被测物的问题）", e);
        }
    }
}
