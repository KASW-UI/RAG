package com.rag.follow.follow.rpc.service;

import com.rag.follow.follow.common.errmsg.FollowErrorCode;
import com.rag.follow.follow.common.errmsg.FollowGrpcError;
import com.rag.follow.follow.rpc.client.UserRpcClient;
import com.rag.users.user.proto.GetUserResp;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.stereotype.Service;

/**
 * 用户存在性校验 —— 对应 Go 端 {@code logic/helper.go:81-94} 的
 * {@code ensureUserExists(ctx, svcCtx, userID)}。
 *
 * <p><b>为什么独立成 Bean 而不是塞进 {@link FollowDomainService}</b>：
 * Go 侧它是 helper.go 里的一个包级函数，被 6 个 logic 调用。
 * 它<b>唯一的区别</b>是依赖 {@code svcCtx.UserRpc}；其余部分（校验 / 响应工厂 / 推荐算法纯函数）
 * 都是无依赖纯逻辑，放在 {@link FollowRpcSupport}。
 * 拆成两个类是为了让「依赖 gRPC 下游」与「纯函数」在类型层面分开：
 * 这样 {@link FollowRpcSupport} 可以被纯单测覆盖，不需要 mock stub。
 *
 * <h2>⚠️ 本类的三分支映射是 follow 域最容易被「顺手改好」的地方</h2>
 *
 * <p>Go 原文（{@code helper.go:82-94}）：
 * <pre>
 * resp, err := svcCtx.UserRpc.GetUser(ctx, &amp;userservice.GetUserReq{Uid: userID})
 * if err != nil {
 *     st, ok := status.FromError(err)
 *     if ok &amp;&amp; st.Code() == codes.NotFound {
 *         return GRPCError(codes.NotFound, ErrorUserNotFound)     // ① 5101
 *     }
 *     return GRPCError(codes.Internal, ErrorServerCommon)         // ② 5001
 * }
 * if resp == nil || !resp.Found || resp.User == nil {
 *     return GRPCError(codes.NotFound, ErrorUserNotFound)         // ③ 5101
 * }
 * return nil
 * </pre>
 *
 * <table border="1">
 *   <caption>三条分支的判定条件与产出</caption>
 *   <tr><th>分支</th><th>条件</th><th>gRPC 码</th><th>业务码</th></tr>
 *   <tr><td>①</td><td>下游明确返回 {@code NOT_FOUND}</td><td>NOT_FOUND</td><td>5101 user not found</td></tr>
 *   <tr><td>②</td><td>下游其它错误（含超时 / 熔断 / UNAVAILABLE）</td><td>INTERNAL</td><td>5001 internal server error</td></tr>
 *   <tr><td>③</td><td>下游成功但 {@code found=false} 或 {@code user} 为空</td><td>NOT_FOUND</td><td>5101 user not found</td></tr>
 * </table>
 *
 * <p><b>分支②是「所有非 NOT_FOUND 的下游故障」的唯一出口</b> ——
 * 包括 {@code UNAVAILABLE}（user-rpc 没起来）、{@code DEADLINE_EXCEEDED}（超时）、
 * {@code INTERNAL}、{@code UNKNOWN}。它们<b>全部</b>被折叠成
 * {@code INTERNAL + "internal server error"}，客户端看不到区别。
 * <b>不要</b>「改进」成把下游码透传出去：那会让 follow-api 的业务码从稳定的 5001
 * 变成随下游故障形态漂移的值。
 */
@Service
public class FollowUserExistenceService {

    private final UserRpcClient userRpcClient;

    public FollowUserExistenceService(UserRpcClient userRpcClient) {
        this.userRpcClient = userRpcClient;
    }

    /**
     * 对应 Go {@code ensureUserExists}：确认 {@code userID} 在 user 域存在。
     *
     * @throws io.grpc.StatusRuntimeException 三条失败分支之一（5101 / 5001），
     *         由 {@code FollowServiceGrpcImpl} 转成 {@code onError}
     */
    public void ensureUserExists(long userId) {
        GetUserResp resp;
        try {
            resp = userRpcClient.getUser(userId);
        } catch (StatusRuntimeException sre) {
            // 分支①：下游**明确**回 NOT_FOUND ⇒ 与「用户不存在」同义 ⇒ 5101。
            //   ⚠️ 这条分支是活的：user-rpc 对不存在的 uid 就是
            //      `status.Error(codes.NotFound, "用户不存在")`
            //      （Go service/user/user/rpc/.../getuserlogic.go 与
            //        RAG service/users/user/user-rpc 的 UserDomainService 两侧同款）。
            //      少了它，「用户不存在」会被报成 5001 —— follow-api 反查后拿到的是
            //      "internal server error"，而 Go 拿到 "user not found"。
            if (UserRpcClient.isNotFound(sre)) {
                throw FollowRpcSupport.rpcError(Status.Code.NOT_FOUND,
                        FollowErrorCode.ERROR_USER_NOT_FOUND.getCode());
            }
            // 分支②：其余一切下游故障（UNAVAILABLE / DEADLINE_EXCEEDED / INTERNAL /
            //         UNKNOWN / CANCELLED）统一折叠成 5001，客户端不区分。
            //   ⚠️ 这条路径**不**附带 userId 日志选项 —— Go 侧同样只是返回 error，
            //      由调用方（logic）统一打日志。保持一致。
            throw FollowRpcSupport.rpcError(Status.Code.INTERNAL,
                    FollowErrorCode.ERROR_SERVER_COMMON.getCode());
        }

        // 分支③：Go 的 `resp == nil` 在 Java 侧由 protobuf 的 has 语义覆盖 ——
        // getFound() 为 false 或 hasUser() 为 false 都归到这里，产出与 Go 完全相同的 5101。
        // ⚠️ hasUser() 而不是 getUser() != null：proto3 的 message 字段「未设置」时
        //    getUser() 返回默认实例（非 null），用它判空会把「空用户」当成「用户存在」。
        if (!resp.getFound() || !resp.hasUser()) {
            throw FollowRpcSupport.rpcError(Status.Code.NOT_FOUND,
                    FollowErrorCode.ERROR_USER_NOT_FOUND.getCode());
        }
    }
}