package com.rag.users.user.api.service;

import com.rag.common.exception.BusinessException;
import com.rag.users.common.errmsg.ErrorCode;
import com.rag.users.user.api.client.UserRpcClient;
import com.rag.users.user.api.dto.GetUserReq;
import com.rag.users.user.api.dto.GetUserResp;
import com.rag.users.user.api.dto.UserInfo;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * getUser 用例 — IdentityResolver 验证已认证 UID 仍然活跃 + 8 状态机业务码映射。
 *
 * 对应 Go: service/user/user/api/internal/logic/user/getuserlogic.go 的 GetUserLogic。
 *
 * <p>L1 外部契约 + L2 业务语义（8 状态机，1:1 复刻）：
 * <pre>
 *   1. IdentityResolver.resolveUser(request, userRpc) — 5 个 sentinel exception → 5 个业务码：
 *      ErrInvalidClaim        → ErrorTokenRuntime    (1006)
 *      ErrUserNotFound        → ErrorUserNotExist    (1003)
 *      ErrUserInactive        → ErrorUserBanned      (1011)
 *      ErrStatusUnavailable   → CodeServerBusy        (1015)
 *      ErrIdentityMismatch    → ErrorServerCommon    (5001)
 *
 *   2. gRPC 异常：
 *      codes.NotFound  → ErrorUserNotExist    (1003)
 *      codes.Internal  → ErrorServerCommon    (5001)
 *      default         → CodeServerBusy       (1015)
 *
 *   3. 成功
 *      → Success + 返回 {user, found: true}（user 只填 uid/username/email/extraInfo 4 字段，score 不填）
 * </pre>
 *
 * <p>Go 端 GetUserLogic 不写 metrics —— 1:1 不补。
 */
@Service
public class UserGetService {

    private static final Logger log = LoggerFactory.getLogger(UserGetService.class);

    private final UserRpcClient userRpcClient;

    public UserGetService(UserRpcClient userRpcClient) {
        this.userRpcClient = userRpcClient;
    }

    public GetUserResp getUser(GetUserReq req, HttpServletRequest httpRequest) {
        com.rag.users.user.proto.UserInfo protoUser;
        try {
            protoUser = IdentityResolver.resolveUser(httpRequest, userRpcClient.userRpc());
        } catch (IdentityResolver.ErrInvalidClaimException e) {
            log.error("getUser ErrInvalidClaim", e);
            throw new BusinessException(ErrorCode.ERROR_TOKEN_RUNTIME.getCode(), ErrorCode.ERROR_TOKEN_RUNTIME.getMsg());
        } catch (IdentityResolver.ErrUserNotFoundException e) {
            log.error("getUser ErrUserNotFound", e);
            throw new BusinessException(ErrorCode.ERROR_USER_NOT_EXIST.getCode(), ErrorCode.ERROR_USER_NOT_EXIST.getMsg());
        } catch (IdentityResolver.ErrUserInactiveException e) {
            log.error("getUser ErrUserInactive", e);
            throw new BusinessException(ErrorCode.ERROR_USER_BANNED.getCode(), ErrorCode.ERROR_USER_BANNED.getMsg());
        } catch (IdentityResolver.ErrStatusUnavailableException e) {
            log.error("getUser ErrStatusUnavailable", e);
            throw new BusinessException(ErrorCode.CODE_SERVER_BUSY.getCode(), ErrorCode.CODE_SERVER_BUSY.getMsg());
        } catch (IdentityResolver.ErrIdentityMismatchException e) {
            log.error("getUser ErrIdentityMismatch", e);
            throw new BusinessException(ErrorCode.ERROR_SERVER_COMMON.getCode(), ErrorCode.ERROR_SERVER_COMMON.getMsg());
        } catch (StatusRuntimeException e) {
            log.error("getUser RPC error", e);
            throw e;
        }

        // 成功：只复制 Uid/Username/Email/ExtraInfo 4 字段（与 Go 端 userinfo 选择一致，score 不透传）
        UserInfo apiUser = new UserInfo();
        apiUser.setUid(protoUser.getUid());
        apiUser.setUsername(protoUser.getUsername());
        if (protoUser.getEmail() != null && !protoUser.getEmail().isEmpty()) {
            apiUser.setEmail(protoUser.getEmail());
        }
        if (protoUser.getExtraInfoCount() > 0) {
            apiUser.setExtraInfo(protoUser.getExtraInfoMap());
        }

        GetUserResp resp = new GetUserResp();
        resp.setUser(apiUser);
        resp.setFound(true);
        return resp;
    }
}
