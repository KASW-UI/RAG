package com.rag.users.admin.api.service;

import com.rag.common.exception.BusinessException;
import com.rag.users.admin.api.client.AdminRpcClient;
import com.rag.users.admin.api.dto.BanUserReq;
import com.rag.users.admin.api.dto.BanUserResp;
import com.rag.users.admin.api.metrics.AdminApiMetrics;
import com.rag.users.common.errmsg.ErrorCode;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * admin banUser 用例 —— 3 状态机（NotFound / Internal / Success）。
 *
 * 对应 Go: service/user/admin/api/internal/logic/admin/banuserlogic.go 的 BanUserLogic。
 */
@Service
public class AdminBanUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminBanUserService.class);

    private final AdminRpcClient adminRpcClient;
    private final AdminApiMetrics metrics;

    public AdminBanUserService(AdminRpcClient adminRpcClient, AdminApiMetrics metrics) {
        this.adminRpcClient = adminRpcClient;
        this.metrics = metrics;
    }

    public BanUserResp banUser(BanUserReq req, HttpServletRequest httpRequest) {
        long operatorId;
        try {
            operatorId = IdentityResolver.claimedUID(httpRequest);
        } catch (IdentityResolver.ErrInvalidClaimException e) {
            log.error("banUser ErrInvalidClaim", e);
            throw new BusinessException(ErrorCode.ERROR_TOKEN_RUNTIME.getCode(), ErrorCode.ERROR_TOKEN_RUNTIME.getMsg());
        }

        com.rag.users.admin.proto.BanUserReq rpcReq = com.rag.users.admin.proto.BanUserReq.newBuilder()
                .setUid(parseUid(req.getUid()))
                .setOperatorId(operatorId)
                .build();

        try {
            adminRpcClient.adminRpc().banUser(rpcReq);
        } catch (StatusRuntimeException e) {
            Status.Code code = e.getStatus().getCode();
            metrics.getBanRpcError().increment();
            log.error("banUser RPC error, target_uid={}", req.getUid(), e);
            if (code == Status.Code.NOT_FOUND) {
                throw new BusinessException(ErrorCode.ERROR_USER_NOT_EXIST.getCode(), ErrorCode.ERROR_USER_NOT_EXIST.getMsg());
            }
            if (code == Status.Code.INTERNAL) {
                throw new BusinessException(ErrorCode.ERROR_SERVER_COMMON.getCode(), ErrorCode.ERROR_SERVER_COMMON.getMsg());
            }
            throw new BusinessException(ErrorCode.CODE_SERVER_BUSY.getCode(), ErrorCode.CODE_SERVER_BUSY.getMsg());
        }

        metrics.getBanSuccess().increment();
        log.info("banUser success, operator_id={} target_uid={}", operatorId, req.getUid());

        BanUserResp resp = new BanUserResp();
        resp.setSuccess(true);
        return resp;
    }

    /** uid 字段从 String 解析（JSON 序列化为字符串避免精度丢失） */
    private static long parseUid(String uidStr) {
        if (uidStr == null || uidStr.isEmpty()) return 0L;
        try {
            return Long.parseLong(uidStr);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
