package com.rag.follow.follow.rpc.client;

import com.rag.users.user.proto.GetUserReq;
import com.rag.users.user.proto.GetUserResp;
import com.rag.users.user.proto.UserServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code follow-rpc} 出向 user-rpc 调用的 <b>2s 客户端超时</b>守门。
 *
 * <h2>它守的是什么</h2>
 * <p>Go 侧的 {@code follow-rpc → user-rpc} 出向调用<b>恒带 2s 客户端超时</b>：
 * <ul>
 *   <li>{@code go-zero@v1.10.2/zrpc/config.go:31} ——
 *       {@code RpcClientConf.Timeout int64 `json:",default=2000"`} ⇒ <b>默认 2000ms</b>；</li>
 *   <li>Go 侧 {@code service/follow/rpc/etc/follow.yaml} 的 {@code UserRpc:} 段
 *       <b>没有</b>显式 {@code Timeout}（只有 {@code Etcd}）⇒ ⇒ 走默认值；</li>
 *   <li><b>消费点</b>：{@code go-zero@v1.10.2/zrpc/internal/clientinterceptors/timeoutinterceptor.go:25}
 *       —— {@code ctx, cancel := context.WithTimeout(ctx, t)} ⇒ <b>取更早者</b>。</li>
 * </ul>
 * <p>⇒ 而 RAG 侧 {@code devh} 注入的<b>裸 stub 没有 call deadline</b>
 * ⇒ ⇒ <b>缺「客户端侧」收口</b>。
 *
 * <h2>⚠️ 差异的准确边界（不要读宽）</h2>
 * <p>RAG 侧下游 {@code user-rpc} <b>自己</b>带服务端 2s 超时
 * （{@code service/users/user/user-rpc/src/main/resources/application.yaml:209} = {@code rpc-timeout: 2s}，
 * 接线在位由 {@code check-grpc-interceptor-wiring.py} = EXIT=0 实测确认）
 * ⇒ ⇒ <b>形态①「请求到达下游、业务挂死」</b>：RAG 由<b>服务端</b> 2s 收口 ⇒ {@code DEADLINE_EXCEEDED}
 * ⇒ 本域 {@code FollowUserExistenceService.java:86-91} 的分支② ⇒ {@code rpcError(INTERNAL, 5001)}
 * ⇒ ⇒ <b>与 Go 同形</b>（Go 侧 {@code helper.go:84-89} ⇒ {@code GRPCError(codes.Internal, ErrorServerCommon)}
 * ⇒ {@code followcommon/errmsg.go:38} = 5001）。
 * ⇒ ⇒ <b>形态②「请求没到达下游」</b>（进程不可达 / 网络黑洞 / 连接建好但无响应）：
 * Go 由<b>客户端</b> 2s 收口 ⇒ 失败在<b>客户端</b>被观测 ⇒ 下游拿到 <b>INTERNAL + 5001</b>；
 * RAG 修前<b>没有任何客户端收口</b> ⇒ 只能由<b>服务端</b>拦截器兜底
 * （{@code follow-rpc/application.yml:247} = {@code rpc-timeout: 2s}，管的是<b>入站</b>调用）
 * ⇒ ⇒ <b>形态②才是差异</b>（下游看到的 status 与业务码都不同）。
 *
 * <h2>本测试如何真正触发该机制（AGENTS.md §3.3）</h2>
 * <p>本类<b>不是</b>「断言代码里写了 {@code withDeadlineAfter}」这种文本级存在性，而是<b>两层真触发</b>：
 * <ol>
 *   <li><b>真 CallOptions</b>：{@code newBlockingStub} 造出<b>真的</b> {@code CallOptions}，
 *       再<b>真的</b>读回 {@code getCallOptions().getDeadline()}（grpc-java 自己算出来的 deadline）；</li>
 *   <li><b>真发一次调用</b>：起一个 <b>in-process 真实 gRPC server</b>，它的 {@code GetUser}
 *       <b>故意不响应</b> ⇒ 客户端<b>真的阻塞</b> ⇒ 只能靠<b>自己套的 2s deadline</b> 收口
 *       ⇒ 断言拿到的是 {@code DEADLINE_EXCEEDED}（Go 侧 ctx 到期同样是 {@code DeadlineExceeded}）。</li>
 * </ol>
 *
 * <h2>与 api 侧守门的差异（诚实边界）</h2>
 * <p>rpc 进程<b>没有入站 ctx 预算载体</b>：{@code GoStyleRequestDeadline.begin} 的
 * <b>生产</b>调用点全仓<b>只有 1 处</b> = {@code service/common/.../observability/HttpMiddleware.java:324}
 * ⇒ 只在 HTTP 中间件里被调 ⇒ <b>rpc 进程恒 no-op</b>
 * ⇒ ⇒ 这里<b>只有「2s 上界」这一维</b> ⇒ ⇒ 与 Go 的 min(服务端 2s, 客户端 2s) <b>同值</b>。
 * <p>⚠️ 本类<b>不</b>起真实 user-rpc、<b>不</b>连 Nacos、<b>不</b>带生产链路上的
 * {@code GoStyleGrpcClientBreakerInterceptor} ⇒ 它证的是
 * 「<b>follow-rpc 自己套的客户端 deadline 真在生效</b>」，
 * <b>不</b>证「整条生产链路的端到端超时表现」（那需要双侧活体对拍）。
 */
class UserRpcClientDeadlineCapTest {

    private static final long SLACK_MILLIS = 200L;

    /** 真发调用那条用例的下界 —— 防止「秒失败」被误判成「超时生效」。 */
    private static final long MIN_EXPECTED_WAIT_MILLIS = 1_500L;

    private Server server;
    private ManagedChannel channel;

    @AfterEach
    void tearDown() {
        if (channel != null) {
            channel.shutdownNow();
        }
        if (server != null) {
            server.shutdownNow();
        }
    }

    @Test
    @DisplayName("客户端超时常量必须是 2000ms（对齐 go-zero zrpc/config.go:31 的 default=2000）")
    void clientTimeoutConstantMatchesGoDefault() {
        assertEquals(2000L, UserRpcClient.CLIENT_TIMEOUT_MILLIS,
                "Go 侧 zrpc 客户端超时默认 2000ms（zrpc/config.go:31）⇒ RAG 侧常量必须同值");
    }

    @Test
    @DisplayName("capByClientTimeout ⇒ 产出真的 ≈2s call deadline（真 CallOptions，非文本断言）")
    void capProducesRealTwoSecondDeadline() {
        UserServiceGrpc.UserServiceBlockingStub raw = UserServiceGrpc.newBlockingStub(
                InProcessChannelBuilder.forName("no-such-server-for-cap-only").build());

        UserServiceGrpc.UserServiceBlockingStub capped = UserRpcClient.capByClientTimeout(raw);

        io.grpc.Deadline d = capped.getCallOptions().getDeadline();
        assertNotNull(d, "出向 stub 必须带 call deadline（Go 侧恒有 2s 客户端超时）");
        long remainingMillis = d.timeRemaining(TimeUnit.MILLISECONDS);
        assertTrue(remainingMillis <= UserRpcClient.CLIENT_TIMEOUT_MILLIS + SLACK_MILLIS,
                "出向 deadline 不得超过 2s 客户端超时，实际 = " + remainingMillis + "ms");
        assertTrue(remainingMillis >= UserRpcClient.CLIENT_TIMEOUT_MILLIS - SLACK_MILLIS,
                "出向 deadline 应当就是 2s 客户端超时，实际 = " + remainingMillis + "ms");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @DisplayName("下游不响应 ⇒ 客户端靠自己的 2s deadline 收口（真发调用 ⇒ DEADLINE_EXCEEDED）")
    void downstreamHangs_clientTimesOutOnItsOwnDeadline() throws Exception {
        String serverName = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(serverName)
                .addService(new UserServiceGrpc.UserServiceImplBase() {
                    @Override
                    public void getUser(GetUserReq request, StreamObserver<GetUserResp> responseObserver) {
                        // ⚠️ 故意**不**响应（既不 onNext 也不 onCompleted）
                        // ⇒ 客户端只能靠**自己套的** deadline 超时 ⇒ 这才是真正的触发点。
                    }
                })
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(serverName).build();

        UserRpcClient client = new UserRpcClient(UserServiceGrpc.newBlockingStub(channel));

        long startedAt = System.nanoTime();
        StatusRuntimeException thrown = assertThrows(StatusRuntimeException.class,
                () -> client.getUser(1L),
                "下游不响应时，follow-rpc 必须靠自己的客户端 deadline 收口，而不是一直挂着");
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

        assertEquals(Status.Code.DEADLINE_EXCEEDED, thrown.getStatus().getCode(),
                "下游挂死 ⇒ 客户端 deadline 到期 ⇒ status 必须是 DEADLINE_EXCEEDED"
                        + "（Go 侧 ctx 到期同样是 DeadlineExceeded）");
        assertTrue(elapsedMillis >= MIN_EXPECTED_WAIT_MILLIS,
                "必须是「等到 deadline 到期」而不是「立刻失败」，实际耗时 = " + elapsedMillis + "ms");
        assertTrue(elapsedMillis <= UserRpcClient.CLIENT_TIMEOUT_MILLIS + 3_000L,
                "不应比 2s 客户端超时晚太多（说明 deadline 没生效或被别的机制顶掉），实际耗时 = "
                        + elapsedMillis + "ms");
    }
}
