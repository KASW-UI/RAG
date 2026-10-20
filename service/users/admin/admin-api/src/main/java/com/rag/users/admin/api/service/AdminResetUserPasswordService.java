package com.rag.users.admin.api.service;

import com.rag.common.exception.BusinessException;
import com.rag.users.admin.api.client.AdminRpcClient;
import com.rag.users.admin.api.dto.ResetUserPasswordReq;
import com.rag.users.admin.api.dto.ResetUserPasswordResp;
import com.rag.users.admin.api.metrics.AdminApiMetrics;
import com.rag.users.common.errmsg.ErrorCode;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * admin resetUserPassword 用例 —— 4 状态机。
 *
 * 对应 Go: service/user/admin/api/internal/logic/admin/resetuserpasswordlogic.go 的 ResetUserPasswordLogic。
 */
@Service
public class AdminResetUserPasswordService {

    private static final Logger log = LoggerFactory.getLogger(AdminResetUserPasswordService.class);

    private final AdminRpcClient adminRpcClient;
    private final AdminApiMetrics metrics;

    public AdminResetUserPasswordService(AdminRpcClient adminRpcClient, AdminApiMetrics metrics) {
        this.adminRpcClient = adminRpcClient;
        this.metrics = metrics;
    }

    public ResetUserPasswordResp resetUserPassword(ResetUserPasswordReq req, HttpServletRequest httpRequest) {
        long operatorId;
        try {
            operatorId = IdentityResolver.claimedUID(httpRequest);
        } catch (IdentityResolver.ErrInvalidClaimException e) {
            log.error("resetUserPassword ErrInvalidClaim", e);
            throw new BusinessException(ErrorCode.ERROR_TOKEN_RUNTIME.getCode(), ErrorCode.ERROR_TOKEN_RUNTIME.getMsg());
        }

        com.rag.users.admin.proto.ResetUserPasswordReq rpcReq = com.rag.users.admin.proto.ResetUserPasswordReq.newBuilder()
                .setUid(parseUid(req.getUid()))
                .setOperatorId(operatorId)
                .build();

        com.rag.users.admin.proto.ResetUserPasswordResp rpcResp;
        try {
            rpcResp = adminRpcClient.adminRpc().resetUserPassword(rpcReq);
        } catch (StatusRuntimeException e) {
            Status.Code code = e.getStatus().getCode();
            metrics.getResetRpcError().increment();
            log.error("resetUserPassword RPC error, target_uid={}", req.getUid(), e);
            if (code == Status.Code.NOT_FOUND) {
                throw new BusinessException(ErrorCode.ERROR_USER_NOT_EXIST.getCode(), ErrorCode.ERROR_USER_NOT_EXIST.getMsg());
            }
            if (code == Status.Code.INTERNAL) {
                throw new BusinessException(ErrorCode.ERROR_SERVER_COMMON.getCode(), ErrorCode.ERROR_SERVER_COMMON.getMsg());
            }
            throw new BusinessException(ErrorCode.CODE_SERVER_BUSY.getCode(), ErrorCode.CODE_SERVER_BUSY.getMsg());
        }

        metrics.getResetSuccess().increment();
        log.info("resetUserPassword success, operator_id={} target_uid={}", operatorId, req.getUid());

        ResetUserPasswordResp resp = new ResetUserPasswordResp();
        // 与参考侧 1:1：透传下游 admin-rpc 的 success，不硬编码 true。
        // （参考侧 ResetUserPasswordLogic.java:84）硬编码会丢掉下游的部分失败信号。
        resp.setSuccess(rpcResp.getSuccess());
        return resp;
    }

    private static long parseUid(String uidStr) {
        return AdminApiSupport.parseUid(uidStr);
    }
}
