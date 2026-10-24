package com.rag.users.admin.api.service;

import com.rag.common.exception.BusinessException;
import com.rag.users.admin.api.client.AdminRpcClient;
import com.rag.users.admin.api.dto.CreateInviteCodeReq;
import com.rag.users.admin.api.dto.CreateInviteCodeResp;
import com.rag.users.admin.api.metrics.AdminApiMetrics;
import com.rag.users.common.errmsg.ErrorCode;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * admin createInviteCode 用例 —— 3 状态机 + 3 metrics label（含 token_invalid）。
 *
 * 对应 Go: service/user/admin/api/internal/logic/admin/createinvitecodelogic.go 的 CreateInviteCodeLogic。
 *
 * <p>L2 业务语义：
 * <ol>
 *   <li>claim uid → InvalidClaim（DRIFT-NEW-007：invite/create 唯一有 token_invalid label）→ inviteTokenInvalid</li>
 *   <li>调 adminRpc.createInviteCode(CreateInviteCodeReq{OperatorId}) → 失败 → inviteRpcError</li>
 *   <li>成功 → inviteSuccess + 返回 CreateInviteCodeResp{InviteCode, ExpiresAt}</li>
 * </ol>
 */
@Service
public class AdminCreateInviteCodeService {

    private static final Logger log = LoggerFactory.getLogger(AdminCreateInviteCodeService.class);

    private final AdminRpcClient adminRpcClient;
    private final AdminApiMetrics metrics;

    public AdminCreateInviteCodeService(AdminRpcClient adminRpcClient, AdminApiMetrics metrics) {
        this.adminRpcClient = adminRpcClient;
        this.metrics = metrics;
    }

    public CreateInviteCodeResp createInviteCode(CreateInviteCodeReq req, HttpServletRequest httpRequest) {
        long operatorId;
        try {
            operatorId = IdentityResolver.claimedUID(httpRequest);
        } catch (IdentityResolver.ErrInvalidClaimException e) {
            metrics.getInviteTokenInvalid().increment();
            log.error("createInviteCode ErrInvalidClaim", e);
            throw new BusinessException(ErrorCode.ERROR_TOKEN_RUNTIME.getCode(), ErrorCode.ERROR_TOKEN_RUNTIME.getMsg());
        }

        com.rag.users.admin.proto.CreateInviteCodeReq rpcReq = com.rag.users.admin.proto.CreateInviteCodeReq.newBuilder()
                .setOperatorId(operatorId)
                .build();

        com.rag.users.admin.proto.CreateInviteCodeResp rpcResp;
        try {
            rpcResp = adminRpcClient.adminRpc().createInviteCode(rpcReq);
        } catch (StatusRuntimeException e) {
            Status.Code code = e.getStatus().getCode();
            metrics.getInviteRpcError().increment();
            log.error("createInviteCode RPC error, operator_id={}", operatorId, e);
            if (code == Status.Code.INTERNAL) {
                throw new BusinessException(ErrorCode.ERROR_SERVER_COMMON.getCode(), ErrorCode.ERROR_SERVER_COMMON.getMsg());
            }
            if (code == Status.Code.NOT_FOUND) {
                throw new BusinessException(ErrorCode.ERROR_USER_NOT_EXIST.getCode(), ErrorCode.ERROR_USER_NOT_EXIST.getMsg());
            }
            throw new BusinessException(ErrorCode.CODE_SERVER_BUSY.getCode(), ErrorCode.CODE_SERVER_BUSY.getMsg());
        }

        metrics.getInviteSuccess().increment();
        log.info("createInviteCode success, operator_id={}", operatorId);

        CreateInviteCodeResp resp = new CreateInviteCodeResp();
        resp.setInviteCode(rpcResp.getInviteCode());
        resp.setExpiresAt(rpcResp.getExpiresAt());
        return resp;
    }
}
