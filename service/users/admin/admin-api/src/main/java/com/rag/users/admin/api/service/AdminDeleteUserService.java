package com.rag.users.admin.api.service;

import com.rag.common.exception.BusinessException;
import com.rag.users.admin.api.client.AdminRpcClient;
import com.rag.users.admin.api.dto.DeleteUserReq;
import com.rag.users.admin.api.dto.DeleteUserResp;
import com.rag.users.admin.api.metrics.AdminApiMetrics;
import com.rag.users.common.errmsg.ErrorCode;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * admin deleteUser 用例 —— 4 状态机（NotFound / Internal / Success + claim uid）。
 *
 * 对应 Go: service/user/admin/api/internal/logic/admin/deleteuserlogic.go 的 DeleteUserLogic。
 */
@Service
public class AdminDeleteUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminDeleteUserService.class);

    private final AdminRpcClient adminRpcClient;
    private final AdminApiMetrics metrics;

    public AdminDeleteUserService(AdminRpcClient adminRpcClient, AdminApiMetrics metrics) {
        this.adminRpcClient = adminRpcClient;
        this.metrics = metrics;
    }

    public DeleteUserResp deleteUser(DeleteUserReq req, HttpServletRequest httpRequest) {
        long operatorId;
        try {
            operatorId = IdentityResolver.claimedUID(httpRequest);
        } catch (IdentityResolver.ErrInvalidClaimException e) {
            log.error("deleteUser ErrInvalidClaim", e);
            throw new BusinessException(ErrorCode.ERROR_TOKEN_RUNTIME.getCode(), ErrorCode.ERROR_TOKEN_RUNTIME.getMsg());
        }

        com.rag.users.admin.proto.DeleteUserReq rpcReq = com.rag.users.admin.proto.DeleteUserReq.newBuilder()
                .setUid(parseUid(req.getUid()))
                .setOperatorId(operatorId)
                .build();

        try {
            adminRpcClient.adminRpc().deleteUser(rpcReq);
        } catch (StatusRuntimeException e) {
            Status.Code code = e.getStatus().getCode();
            metrics.getDeleteRpcError().increment();
            log.error("deleteUser RPC error, target_uid={}", req.getUid(), e);
            if (code == Status.Code.NOT_FOUND) {
                throw new BusinessException(ErrorCode.ERROR_USER_NOT_EXIST.getCode(), ErrorCode.ERROR_USER_NOT_EXIST.getMsg());
            }
            if (code == Status.Code.INTERNAL) {
                throw new BusinessException(ErrorCode.ERROR_SERVER_COMMON.getCode(), ErrorCode.ERROR_SERVER_COMMON.getMsg());
            }
            throw new BusinessException(ErrorCode.CODE_SERVER_BUSY.getCode(), ErrorCode.CODE_SERVER_BUSY.getMsg());
        }

        metrics.getDeleteSuccess().increment();
        log.info("deleteUser success, operator_id={} target_uid={}", operatorId, req.getUid());

        DeleteUserResp resp = new DeleteUserResp();
        resp.setSuccess(true);
        return resp;
    }

    private static long parseUid(String uidStr) {
        if (uidStr == null || uidStr.isEmpty()) return 0L;
        try {
            return Long.parseLong(uidStr);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
