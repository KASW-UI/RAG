package com.rag.users.admin.api.service;

import com.rag.common.exception.BusinessException;
import com.rag.users.admin.api.client.AdminRpcClient;
import com.rag.users.admin.api.dto.UnBanUserReq;
import com.rag.users.admin.api.dto.UnBanUserResp;
import com.rag.users.admin.api.metrics.AdminApiMetrics;
import com.rag.users.common.errmsg.ErrorCode;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * admin unBanUser 用例 —— 3 状态机。
 *
 * 对应 Go: service/user/admin/api/internal/logic/admin/unbanuserlogic.go 的 UnBanUserLogic。
 */
@Service
public class AdminUnBanUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUnBanUserService.class);

    private final AdminRpcClient adminRpcClient;
    private final AdminApiMetrics metrics;

    public AdminUnBanUserService(AdminRpcClient adminRpcClient, AdminApiMetrics metrics) {
        this.adminRpcClient = adminRpcClient;
        this.metrics = metrics;
    }

    public UnBanUserResp unBanUser(UnBanUserReq req, HttpServletRequest httpRequest) {
        long operatorId;
        try {
            operatorId = IdentityResolver.claimedUID(httpRequest);
        } catch (IdentityResolver.ErrInvalidClaimException e) {
            log.error("unBanUser ErrInvalidClaim", e);
            throw new BusinessException(ErrorCode.ERROR_TOKEN_RUNTIME.getCode(), ErrorCode.ERROR_TOKEN_RUNTIME.getMsg());
        }

        com.rag.users.admin.proto.UnBanUserReq rpcReq = com.rag.users.admin.proto.UnBanUserReq.newBuilder()
                .setUid(parseUid(req.getUid()))
                .setOperatorId(operatorId)
                .build();

        com.rag.users.admin.proto.UnBanUserResp rpcResp;
        try {
            rpcResp = adminRpcClient.adminRpc().unBanUser(rpcReq);
        } catch (StatusRuntimeException e) {
            Status.Code code = e.getStatus().getCode();
            metrics.getUnbanRpcError().increment();
            log.error("unBanUser RPC error, target_uid={}", req.getUid(), e);
            if (code == Status.Code.NOT_FOUND) {
                throw new BusinessException(ErrorCode.ERROR_USER_NOT_EXIST.getCode(), ErrorCode.ERROR_USER_NOT_EXIST.getMsg());
            }
            // DRIFT-004（已修）：Go 出处 unbanuserlogic.go:50-57，
            // Internal → ErrorDbSelect(5003)，与 banuser 端点的 5001 不同（Go 自身的不一致）。
            // 真源冻结 ⇒ 逐字跟随。注意 banuser 侧仍应是 5001，不要连带改。
            if (code == Status.Code.INTERNAL) {
                throw new BusinessException(ErrorCode.ERROR_DB_SELECT.getCode(), ErrorCode.ERROR_DB_SELECT.getMsg());
            }
            throw new BusinessException(ErrorCode.CODE_SERVER_BUSY.getCode(), ErrorCode.CODE_SERVER_BUSY.getMsg());
        }

        metrics.getUnbanSuccess().increment();
        log.info("unBanUser success, operator_id={} target_uid={}", operatorId, req.getUid());

        UnBanUserResp resp = new UnBanUserResp();
        // 与参考侧 1:1：透传下游 admin-rpc 的 success，不硬编码 true。
        // （参考侧 UnBanUserLogic.java:109）硬编码会丢掉下游的部分失败信号。
        resp.setSuccess(rpcResp.getSuccess());
        return resp;
    }

    private static long parseUid(String uidStr) {
        return AdminApiSupport.parseUid(uidStr);
    }
}
