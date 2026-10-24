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
     * 对应 Go {@code svcCtx.UserRpc.GetUser(ctx, &userservice.GetUserReq{Uid: userID})}
     * （{@code logic/helper.go:82}）。
     *
     * <p>⚠️ <b>BlockingStub 会阻塞调用线程</b>，且<b>没有</b>在此处设 deadline ——
     * 与 Go 一致：Go 的 {@code zrpc.MustNewClient} 也只靠 client interceptor
     * （timeout / breaker）兜底，本模块不额外加超时。
     * 服务端的整体超时由 {@code observability.rpc-timeout: 2s} 经 server interceptor 施加。
     *
     * @throws StatusRuntimeException 下游返回任意 gRPC 错误时原样抛出（不转换）
     */
    public GetUserResp getUser(long uid) {
        return userStub.getUser(GetUserReq.newBuilder().setUid(uid).build());
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