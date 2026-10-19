package com.rag.users.user.api.service;

import com.rag.common.exception.BusinessException;
import com.rag.users.common.errmsg.ErrorCode;
import com.rag.users.user.api.client.UserRpcClient;
import com.rag.users.user.api.dto.LogoutReq;
import com.rag.users.user.api.dto.LogoutResp;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * logout 用例 — HTTP DTO ↔ gRPC proto + 4 状态机业务码映射。
 *
 * 对应 Go: service/user/user/api/internal/logic/user/logoutlogic.go 的 LogoutLogic。
 *
 * <p>L1 外部契约 + L2 业务语义（4 状态机，1:1 复刻）：
 * <pre>
 *   1. 从 request attribute "jwt_token" 取 token（JwtAuthInterceptor 在 blacklist 校验后写入）
 *      → 取不到 → ErrorTokenNotExist (1004) + 日志 "Token不存在"
 *      （对应 Go 端 errmsg.ErrorTokenNotExist=1004；
 *        注意：errmsg.ErrorTokenTypeWrong=1005 是 user-rpc LogoutLogic 用的，不是 user-api）
 *
 *   2. 调 userRpc.logout(LogoutReq{Token})
 *      → gRPC codes.Internal → ErrorServerCommon (5001)
 *      → 其它（默认）         → CodeServerBusy   (1015)
 *
 *   3. 成功
 *      → Success + 返回 {success: true}
 * </pre>
 *
 * <p>Go 端 LogoutLogic 不写 metrics —— 1:1 不补。
 */
@Service
public class UserLogoutService {

    private static final Logger log = LoggerFactory.getLogger(UserLogoutService.class);

    private final UserRpcClient userRpcClient;

    public UserLogoutService(UserRpcClient userRpcClient) {
        this.userRpcClient = userRpcClient;
    }

    public LogoutResp logout(LogoutReq req, HttpServletRequest httpRequest) {
        // 对应 Go: l.ctx.Value("jwt_token")
        Object tokenAttr = httpRequest.getAttribute("jwt_token");
        if (tokenAttr == null) {
            log.error("Token不存在");
            throw new BusinessException(ErrorCode.ERROR_TOKEN_NOT_EXIST.getCode(), ErrorCode.ERROR_TOKEN_NOT_EXIST.getMsg());
        }
        String token = (String) tokenAttr;

        com.rag.users.user.proto.LogoutReq rpcReq = com.rag.users.user.proto.LogoutReq.newBuilder()
                .setToken(token).build();
        try {
            userRpcClient.userRpc().logout(rpcReq);
        } catch (StatusRuntimeException e) {
            log.error("logout RPC error", e);
            throw e;
        }

        LogoutResp resp = new LogoutResp();
        resp.setSuccess(true);
        return resp;
    }
}
