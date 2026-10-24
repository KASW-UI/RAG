package com.rag.users.admin.api.service;

import com.rag.common.exception.BusinessException;
import com.rag.users.admin.api.client.AdminRpcClient;
import com.rag.users.admin.api.dto.LogoutReq;
import com.rag.users.admin.api.dto.LogoutResp;
import com.rag.users.common.errmsg.ErrorCode;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * admin logout 用例 —— 4 状态机。
 *
 * 对应 Go: service/user/admin/api/internal/logic/admin/logoutlogic.go 的 LogoutLogic。
 *
 * <p>L2 业务语义：
 * <ol>
 *   <li>从 request attribute "jwt_token" 取 token → 取不到 → ErrorTokenNotExist (1004)</li>
 *   <li>调 adminRpc.logout(LogoutReq{Token}) → gRPC 错 → 抛业务码</li>
 *   <li>成功 → LogoutResp{Success=true}</li>
 * </ol>
 */
@Service
public class AdminLogoutService {

    private static final Logger log = LoggerFactory.getLogger(AdminLogoutService.class);

    private final AdminRpcClient adminRpcClient;

    public AdminLogoutService(AdminRpcClient adminRpcClient) {
        this.adminRpcClient = adminRpcClient;
    }

    public LogoutResp logout(LogoutReq req, HttpServletRequest httpRequest) {
        Object tokenAttr = httpRequest.getAttribute("jwt_token");
        if (tokenAttr == null) {
            log.error("Token不存在");
            throw new BusinessException(ErrorCode.ERROR_TOKEN_NOT_EXIST.getCode(), ErrorCode.ERROR_TOKEN_NOT_EXIST.getMsg());
        }
        String token = (String) tokenAttr;

        com.rag.users.admin.proto.LogoutReq rpcReq = com.rag.users.admin.proto.LogoutReq.newBuilder()
                .setToken(token).build();
        try {
            adminRpcClient.adminRpc().logout(rpcReq);
        } catch (StatusRuntimeException e) {
            Status.Code code = e.getStatus().getCode();
            log.error("admin logout RPC error", e);
            if (code == Status.Code.INTERNAL) {
                throw new BusinessException(ErrorCode.ERROR_SERVER_COMMON.getCode(), ErrorCode.ERROR_SERVER_COMMON.getMsg());
            }
            throw new BusinessException(ErrorCode.CODE_SERVER_BUSY.getCode(), ErrorCode.CODE_SERVER_BUSY.getMsg());
        }

        LogoutResp resp = new LogoutResp();
        resp.setSuccess(true);
        return resp;
    }
}
