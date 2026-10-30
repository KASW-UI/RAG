package com.rag.follow.follow.rpc.client;

import com.rag.users.user.proto.GetUserReq;
import com.rag.users.user.proto.GetUserResp;
import com.rag.users.user.proto.UserServiceGrpc;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

/**
 * user-rpc gRPC client facade —— 对应 Go 端
 * {@code service/follow/rpc/internal/svc/servicecontext.go:30} 的
 * {@code UserRpc: userservice.NewUserService(zrpc.MustNewClient(c.UserRpc))}。
 *
 * <p><b>本模块与 user 域的全部交互只有这一个方法</b>（{@code helper.go:81-94} 的
 * {@code ensureUserExists}）：Follow / Block / GetFollowList / GetBlockList /
 * GetFollowerList / GetRecommendations 六个 logic 都会调它，
 * <b>Unfollow 与 Unblock 不调</b>（见 {@code unfollowlogic.go:31-51} / {@code unblocklogic.go:31-51}，
 * 两者只有 validate + delete）。
 *
 * <p><b>⚠️ 本 facade 不吞异常、不做降级</b> —— 这是与 message-rpc / like-rpc 的
 * {@code UserRpcClient} 最重要的差别。follow 域对「用户不存在」有<b>明确的业务分支</b>
 * （返回 5101），对「下游挂了」也有<b>明确的业务分支</b>（返回 5001），
 * 两者必须在调用点区分开；一旦在这里 catch 掉，调用点就再也分不出来。
 * 所以：{@link StatusRuntimeException} 原样抛出，由
 * {@code FollowUserExistenceService} 按 Go 的三分支映射成业务码。
 */
@Component
public class UserRpcClient {

    private final UserServiceGrpc.UserServiceBlockingStub userStub;

    public UserRpcClient(@GrpcClient("user-rpc") UserServiceGrpc.UserServiceBlockingStub userStub) {
        this.userStub = userStub;
    }

    /**
     * <b>zrpc 客户端超时 = 2s</b>。
     *
     * <p><b>Go 出处</b>：{@code go-zero@v1.10.2/zrpc/config.go:31}
     * —— {@code RpcClientConf.Timeout int64 `json:",default=2000"`}
     * ⇒ <b>默认 2000ms</b>；且 Go 侧 {@code service/follow/rpc/etc/follow.yaml} 的
     * {@code UserRpc:} 段<b>没有显式 Timeout</b>（只有 {@code Etcd}）⇒ ⇒
     * <b>Go 侧 follow-rpc 出向 user-rpc 的调用恒带 2s 客户端超时</b>。
     *
     * <p><b>消费点</b>：{@code go-zero@v1.10.2/zrpc/internal/clientinterceptors/timeoutinterceptor.go:25}
     * —— {@code context.WithTimeout(ctx, t)} ⇒ <b>取更早者</b>
     * ⇒ ⇒ <b>Go 的出向 deadline = min(入站 ctx 剩余, 2s 客户端超时)</b>。
     *
     * <p><b>语义等价论证</b>：Go 的 ctx 是 handler 透传的（{@code logic/helper.go:82}
     * 直接透传 ctx ⇒ 继承服务端 2s 的 deadline）⇒ 出向实际是 min(服务端 2s, 客户端 2s) = 2s；
     * RAG 侧<b>出向上界由共享层承担</b>（见下）⇒ ⇒ 同样是 2s 这一维 ⇒ <b>与 Go 同值</b>。
     * <p><b>🔴 2026-10-31（P2-T04 · G1 lane）：本类<b>不再</b>自己套客户端上界</b> ——
     * 上界由共享层的 {@code GoStyleOutboundDeadlineInterceptor} <b>唯一</b>承担
     * （{@code @GrpcGlobalClientInterceptor} ⇒ 装在<b>每个</b>出向 devh 调用上 ⇒ 与调用点无关），
     * 本域只配参数 {@code rag.protection.outbound-deadline.cap-millis=2000}
     * （{@code service/follow/follow-rpc/src/main/resources/application.yml} 的 {@code rag.protection} 段）
     * ⇒ ⇒ <b>为什么必须删掉域侧私有副本</b>：副本把上界<b>写死</b>在 {@code CallOptions} 里，
     * 共享层再对它取 min ⇒ ⇒ <b>min 只压小、不压大</b>
     * ⇒ ⇒ <b>「想放宽上界」会被副本压回 2000ms</b>（{@code FOLLOW_RPC_OUTBOUND_DEADLINE_CAP_MILLIS} 配得比 2000 大也不生效）
     * ⇒ ⇒ 动作②「上界可 env 覆盖」在<b>放宽方向</b>上被架空 ⇒ ⇒ 副本必删（棘轮
     * {@code GoStyleClientCallTimeoutSingleSourceGateTest}：域侧<b>只配参数</b>）。
     * ⇒ ⇒ <b>本常量仍在</b>：它现在是<b>断言用的期望值</b>（真实值由上面的 yml 持有），守门
     * {@code UserRpcClientDeadlineCapTest} 核对「yml 绑出的值 == 本常量 == 2000」。
     * <p><b>⚠️ 差异的准确边界（不要读宽）</b>：RAG 侧下游 {@code user-rpc} <b>自己</b>带服务端 2s 超时
     * （{@code service/users/user/user-rpc/src/main/resources/application.yaml:209} = {@code rpc-timeout: 2s}；
     * 接线在位由 {@code check-grpc-interceptor-wiring.py} = EXIT=0 实测确认）
     * ⇒ ⇒ <b>形态①「请求到达下游、业务挂死」</b>：由<b>服务端</b> 2s 收口 ⇒ {@code DEADLINE_EXCEEDED}
     * ⇒ 本域 {@code FollowUserExistenceService.java:86-91} 分支② ⇒ {@code rpcError(INTERNAL, 5001)}
     * ⇒ ⇒ <b>与 Go 同形</b>（Go 侧 {@code helper.go:84-89} ⇒ 同样 {@code INTERNAL} + 5001）
     * ⇒ ⇒ <b>形态②「请求没到达下游」</b>：Go 由<b>客户端</b> 2s 收口；RAG 修前只能由<b>服务端</b>兜底
     * （{@code service/follow/follow-rpc/src/main/resources/application.yml:308} = {@code rpc-timeout: 2s}，管的是<b>入站</b>调用）
     * ⇒ ⇒ <b>形态②才是本条要修的差异</b>。
     */
    public static final long CLIENT_TIMEOUT_MILLIS = 2000L;

    /**
     * 对应 Go {@code svcCtx.UserRpc.GetUser(ctx, &userservice.GetUserReq{Uid: userID})}
     * （{@code logic/helper.go:82}）。
     *
     * <p>⚠️ <b>BlockingStub 会阻塞调用线程</b> ⇒ 必须带 deadline（Go 侧由 client interceptor 兜底，
     * RAG 侧由共享层的出向 deadline 拦截器兜底 ⇒ 它装在 devh channel 上 ⇒ 与调用点无关）。
     * 服务端的整体超时由 {@code observability.rpc-timeout: 2s} 经 server interceptor 施加。
     *
     * @throws StatusRuntimeException 下游返回任意 gRPC 错误时原样抛出（不转换）
     */
    public GetUserResp getUser(long uid) {
        return userStub
                .getUser(GetUserReq.newBuilder().setUid(uid).build());
    }

    /**
     * Go 侧三分支判定（{@code helper.go:83-93}）里唯一的「看 status 码」动作，抽出来便于测试断言。
     *
     * <p>对应 Go：
     * <pre>
     * st, ok := status.FromError(err)
     * if ok &amp;&amp; st.Code() == codes.NotFound { return GRPCError(codes.NotFound, ErrorUserNotFound) }
     * return GRPCError(codes.Internal, ErrorServerCommon)
     * }</pre>
     *
     * @param err 调用 {@link #getUser} 抛出的异常
     * @return {@code true} 表示下游明确回的是 {@code NOT_FOUND}
     */
    public static boolean isNotFound(StatusRuntimeException err) {
        Status status = err.getStatus();
        return status != null && status.getCode() == Status.Code.NOT_FOUND;
    }
}