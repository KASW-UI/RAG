package com.rag.users.admin.api.internal.logic;

import com.rag.common.spring.LoggerFactoryBean;
import com.rag.users.admin.api.internal.identity.IdentityResolver;
import com.rag.users.admin.api.internal.metrics.Metrics;
import com.rag.users.admin.api.internal.svc.ServiceContext;
import com.rag.users.admin.api.internal.types.ApiTypes;
import com.rag.users.admin.proto.ResetUserPasswordReq;
import com.rag.users.admin.proto.ResetUserPasswordResp;
import com.rag.users.common.errmsg.ErrorCode;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.opentelemetry.context.Context;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * 对应 Go 端 internal/logic/admin/resetuserpasswordlogic.go。
 *
 * <p>L1 外部契约 + L2 业务语义（六状态机，逐项冻结，对应 Go resetuserpasswordlogic.go 第 36-62 行）：
 * <pre>
 *   1. operator_id（DRIFT-NEW-003 沿用）
 *   2. 调 adminRpc.ResetUserPassword(ResetUserPasswordReq{Uid, OperatorId})
 *      → 失败：
 *         gRPC codes.NotFound → ErrorUserNotExist  (1003)
 *         gRPC codes.Internal → <b>ErrorDbSelect</b>  (5003)   // 与 unbanuser 一致
 *         其它（默认）         → CodeServerBusy     (1015)
 *      → metrics "rpc_error"
 *   3. 成功 → Success (200) + {success: rpcResp.Success} + metrics "success"
 * </pre>
 */
@Service
public class ResetUserPasswordLogic {

    private final ServiceContext svcCtx;
    private final Metrics metrics;
    private final LoggerFactoryBean logger;

    public ResetUserPasswordLogic(ServiceContext svcCtx, Metrics metrics, LoggerFactoryBean logger) {
        this.svcCtx = svcCtx;
        this.metrics = metrics;
        this.logger = logger;
    }

    public LogicResult<ApiTypes.ResetUserPasswordResp> resetUserPassword(ApiTypes.ResetUserPasswordReq req, HttpServletRequest httpRequest) {
        Context otelCtx = Context.current();

        long operatorId;
        try {
            operatorId = IdentityResolver.claimedUID(httpRequest);
        } catch (IdentityResolver.ErrInvalidClaimException e) {
            logger.logBusinessErr(otelCtx, ErrorCode.ERROR_TOKEN_RUNTIME.getCode(), e);
            return LogicResult.fail(ErrorCode.ERROR_TOKEN_RUNTIME);
        }

        Long targetUid = req.getUid() == null ? null : parseLongOrNull(req.getUid());
        if (targetUid == null) {
            return LogicResult.fail(ErrorCode.ERROR_REQUEST_PARAM);
        }
        ResetUserPasswordReq rpcReq = ResetUserPasswordReq.newBuilder()
                .setUid(targetUid)
                .setOperatorId(operatorId)
                .build();

        ResetUserPasswordResp rpcResp;
        try {
            rpcResp = svcCtx.getAdminRpc().resetUserPassword(rpcReq);
        } catch (StatusRuntimeException e) {
            metrics.getResetRpcError().increment();
            logger.logBusinessErr(otelCtx, ErrorCode.ERROR.getCode(), e);
            Status.Code code = e.getStatus().getCode();
            switch (code) {
                case NOT_FOUND:
                    return LogicResult.fail(ErrorCode.ERROR_USER_NOT_EXIST);
                case INTERNAL:
                    return LogicResult.fail(ErrorCode.ERROR_DB_SELECT);   // 5003
                default:
                    return LogicResult.fail(ErrorCode.CODE_SERVER_BUSY);
            }
        }

        metrics.getResetSuccess().increment();
        ApiTypes.ResetUserPasswordResp resp = new ApiTypes.ResetUserPasswordResp();
        resp.setSuccess(rpcResp.getSuccess());
        return LogicResult.ok(resp);
    }

    private static Long parseLongOrNull(String s) {
        if (s == null) return null;
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
    }
}