package com.rag.users.user.api.service;

import com.rag.common.exception.BusinessException;
import com.rag.users.common.errmsg.ErrorCode;
import com.rag.users.user.api.client.UserRpcClient;
import com.rag.users.user.api.dto.UpdateUserReq;
import com.rag.users.user.api.dto.UpdateUserResp;
import com.rag.users.user.api.dto.UserInfo;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * updateUser 用例 — claim uid + 4 状态机业务码映射。
 *
 * 对应 Go: service/user/user/api/internal/logic/user/updateuserlogic.go 的 UpdateUserLogic。
 *
 * <p>L1 外部契约 + L2 业务语义（4 状态机，1:1 复刻）：
 * <pre>
 *   1. IdentityResolver.claimedUID(request) → uid
 *      → 失败 → ErrorTokenRuntime (1006)
 *
 *   2. 调 userRpc.updateUser(UpdateUserReq{Uid, Username?, Password?, Email?, ExtraInfo?})
 *      → gRPC codes.Internal      → ErrorServerCommon (5001)
 *      → gRPC codes.AlreadyExists → ErrorUserExist    (1001)
 *      → gRPC codes.NotFound      → ErrorUserNotExist (1003)
 *      → default                  → CodeServerBusy    (1015)
 *
 *   3. 成功
 *      → Success + 返回 {user}（4 字段）
 * </pre>
 *
 * <p>Go 端 UpdateUserLogic 不写 metrics —— 1:1 不补。
 */
@Service
public class UserUpdateService {

    private static final Logger log = LoggerFactory.getLogger(UserUpdateService.class);

    private final UserRpcClient userRpcClient;

    public UserUpdateService(UserRpcClient userRpcClient) {
        this.userRpcClient = userRpcClient;
    }

    public UpdateUserResp updateUser(UpdateUserReq req, HttpServletRequest httpRequest) {
        long uid;
        try {
            uid = IdentityResolver.claimedUID(httpRequest);
        } catch (IdentityResolver.ErrInvalidClaimException e) {
            log.error("updateUser ErrInvalidClaim", e);
            throw new BusinessException(ErrorCode.ERROR_TOKEN_RUNTIME.getCode(), ErrorCode.ERROR_TOKEN_RUNTIME.getMsg());
        }

        com.rag.users.user.proto.UpdateUserReq.Builder builder = com.rag.users.user.proto.UpdateUserReq.newBuilder().setUid(uid);
        if (req.getUsername() != null) builder.setUsername(req.getUsername());
        if (req.getPassword() != null) builder.setPassword(req.getPassword());
        if (req.getEmail() != null) builder.setEmail(req.getEmail());
        if (req.getExtraInfo() != null) builder.putAllExtraInfo(req.getExtraInfo());

        com.rag.users.user.proto.UpdateUserResp rpcResp;
        try {
            rpcResp = userRpcClient.userRpc().updateUser(builder.build());
        } catch (StatusRuntimeException e) {
            log.error("updateUser RPC error", e);
            throw e;
        }

        com.rag.users.user.proto.UserInfo rpcUser = rpcResp.getUser();
        UserInfo apiUser = new UserInfo();
        apiUser.setUid(rpcUser.getUid());
        apiUser.setUsername(rpcUser.getUsername());
        if (rpcUser.getEmail() != null && !rpcUser.getEmail().isEmpty()) apiUser.setEmail(rpcUser.getEmail());
        if (rpcUser.getExtraInfoCount() > 0) apiUser.setExtraInfo(rpcUser.getExtraInfoMap());

        UpdateUserResp resp = new UpdateUserResp();
        resp.setUser(apiUser);
        return resp;
    }
}
