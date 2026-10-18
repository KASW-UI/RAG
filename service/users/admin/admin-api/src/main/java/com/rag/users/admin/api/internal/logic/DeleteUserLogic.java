package com.rag.users.admin.api.internal.logic;

import com.rag.common.spring.LoggerFactoryBean;
import com.rag.users.admin.api.internal.identity.IdentityResolver;
import com.rag.users.admin.api.internal.metrics.Metrics;
import com.rag.users.admin.api.internal.svc.ServiceContext;
import com.rag.users.admin.api.internal.types.ApiTypes;
import com.rag.users.admin.proto.DeleteUserReq;
import com.rag.users.admin.proto.DeleteUserResp;
import com.rag.users.common.errmsg.ErrorCode;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.opentelemetry.context.Context;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * 对应 Go 端 internal/logic/admin/deletelogic.go。
 *
 * <p>L1 外部契约 + L2 业务语义（六状态机，逐项冻结，对应 Go deletelogic.go 第 35-61 行）：
 * <pre>
 *   1. operator_id（DRIFT-NEW-003 沿用 banuser 模式）
 *   2. 调 adminRpc.DeleteUser(DeleteUserReq{Uid, OperatorId})
 *      → 失败：
 *         gRPC codes.NotFound → ErrorUserNotExist  (1003)
 *         gRPC codes.Internal → ErrorServerCommon  (5001)   // 与 banuser 一致
 *         其它（默认）         → CodeServerBusy     (1015)
 *      → metrics "rpc_error"
 *   3. 成功 → Success (200) + {success: rpcResp.Success} + metrics "success"
 * </pre>
 */
@Service
public class DeleteUserLogic {

    private final ServiceContext svcCtx;
    private final Metrics metrics;
    private final LoggerFactoryBean logger;

    public DeleteUserLogic(ServiceContext svcCtx, Metrics metrics, LoggerFactoryBean logger) {
        this.svcCtx = svcCtx;
        this.metrics = metrics;
        this.logger = logger;
    }

    public LogicResult<ApiTypes.DeleteUserResp> deleteUser(ApiTypes.DeleteUserReq req, HttpServletRequest httpRequest) {
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
        DeleteUserReq rpcReq = DeleteUserReq.newBuilder()
                .setUid(targetUid)
                .setOperatorId(operatorId)
                .build();

        DeleteUserResp rpcResp;
        try {
            rpcResp = svcCtx.getAdminRpc().deleteUser(rpcReq);
        } catch (StatusRuntimeException e) {
            metrics.getDeleteRpcError().increment();
            logger.logBusinessErr(otelCtx, ErrorCode.ERROR.getCode(), e);
            Status.Code code = e.getStatus().getCode();
            switch (code) {
                case NOT_FOUND:
                    return LogicResult.fail(ErrorCode.ERROR_USER_NOT_EXIST);
                case INTERNAL:
                    return LogicResult.fail(ErrorCode.ERROR_SERVER_COMMON);
                default:
                    return LogicResult.fail(ErrorCode.CODE_SERVER_BUSY);
            }
        }

        metrics.getDeleteSuccess().increment();
        ApiTypes.DeleteUserResp resp = new ApiTypes.DeleteUserResp();
        resp.setSuccess(rpcResp.getSuccess());
        return LogicResult.ok(resp);
    }

    private static Long parseLongOrNull(String s) {
        if (s == null) return null;
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
    }
}