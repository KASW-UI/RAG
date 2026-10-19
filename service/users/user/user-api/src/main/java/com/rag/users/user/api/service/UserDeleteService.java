package com.rag.users.user.api.service;

import com.rag.common.exception.BusinessException;
import com.rag.users.common.errmsg.ErrorCode;
import com.rag.users.user.api.client.UserRpcClient;
import com.rag.users.user.api.dto.DeleteUserReq;
import com.rag.users.user.api.dto.DeleteUserResp;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * deleteUser 用例 — claim uid + 4 状态机业务码映射。
 *
 * 对应 Go: service/user/user/api/internal/logic/user/deleteuserlogic.go 的 DeleteUserLogic。
 *
 * <p>L1 外部契约 + L2 业务语义（4 状态机，1:1 复刻）：
 * <pre>
 *   1. IdentityResolver.claimedUID(request) → uid
 *      → 失败 → ErrorTokenRuntime (1006)
 *
 *   2. 调 userRpc.deleteUser(DeleteUserReq{Uid})
 *      → 失败 或 rpcResp.Success == false：
 *         gRPC codes.NotFound  → ErrorUserNotExist (1003)
 *         gRPC codes.Internal  → ErrorServerCommon (5001)
 *         default              → CodeServerBusy    (1015)
 *
 *   3. 成功
 *      → Success + 返回 {success: true}
 * </pre>
 *
 * <p>Go 端 DeleteUserLogic 不写 metrics —— 1:1 不补。
 */
@Service
public class UserDeleteService {

    private static final Logger log = LoggerFactory.getLogger(UserDeleteService.class);

    private final UserRpcClient userRpcClient;

    public UserDeleteService(UserRpcClient userRpcClient) {
        this.userRpcClient = userRpcClient;
    }

    public DeleteUserResp deleteUser(DeleteUserReq req, HttpServletRequest httpRequest) {
        long uid;
        try {
            uid = IdentityResolver.claimedUID(httpRequest);
        } catch (IdentityResolver.ErrInvalidClaimException e) {
            log.error("deleteUser ErrInvalidClaim", e);
            throw new BusinessException(ErrorCode.ERROR_TOKEN_RUNTIME.getCode(), ErrorCode.ERROR_TOKEN_RUNTIME.getMsg());
        }

        com.rag.users.user.proto.DeleteUserReq rpcReq = com.rag.users.user.proto.DeleteUserReq.newBuilder()
                .setUid(uid).build();
        com.rag.users.user.proto.DeleteUserResp rpcResp;
        try {
            rpcResp = userRpcClient.userRpc().deleteUser(rpcReq);
        } catch (StatusRuntimeException e) {
            log.error("deleteUser RPC error", e);
            throw e;
        }

        // Go 端同时检查 err != nil && rpcResp.Success == false
        if (!rpcResp.getSuccess()) {
            // rpcResp.Success == false 但没有 gRPC error —— Go 端走 default 分支
            throw new BusinessException(ErrorCode.CODE_SERVER_BUSY.getCode(), ErrorCode.CODE_SERVER_BUSY.getMsg());
        }

        DeleteUserResp resp = new DeleteUserResp();
        resp.setSuccess(true);
        return resp;
    }
}
