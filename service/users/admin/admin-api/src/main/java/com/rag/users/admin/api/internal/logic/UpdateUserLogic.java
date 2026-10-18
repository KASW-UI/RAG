package com.rag.users.admin.api.internal.logic;

import com.rag.common.spring.LoggerFactoryBean;
import com.rag.users.admin.api.internal.identity.IdentityResolver;
import com.rag.users.admin.api.internal.metrics.Metrics;
import com.rag.users.admin.api.internal.svc.ServiceContext;
import com.rag.users.admin.api.internal.types.ApiTypes;
import com.rag.users.admin.proto.UpdateUserReq;
import com.rag.users.admin.proto.UpdateUserResp;
import com.rag.users.admin.proto.UserInfo;
import com.rag.users.common.errmsg.ErrorCode;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.opentelemetry.context.Context;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * 对应 Go 端 internal/logic/admin/updateuserlogic.go。
 *
 * <p>L1 外部契约 + L2 业务语义（八状态机，逐项冻结，对应 Go updateuserlogic.go 第 35-77 行）：
 * <pre>
 *   1. operator_id（DRIFT-NEW-003 沿用）
 *   2. 构造 rpcReq = UpdateUserReq{Uid, Username, Password, Email, ExtraInfo, OperatorId}
 *      （6 字段，1:1 透传；proto3 map 字段用反射 putExtraInfo —— DRIFT-NEW-002 沿用 CreateAdmin 模式）
 *   3. 调 adminRpc.UpdateUser(rpcReq)
 *      → 失败：
 *         gRPC codes.AlreadyExists → ErrorUserExist      (1001)   // 新增
 *         gRPC codes.NotFound      → ErrorUserNotExist   (1003)
 *         gRPC codes.Internal      → ErrorServerCommon   (5001)
 *         其它（默认）              → CodeServerBusy      (1015)
 *      → metrics "db_error"（DRIFT-NEW-005：Go 端 updateuser 用 db_error 而非 rpc_error）
 *   4. 成功
 *      → Success (200) + {success: true, user: {uid, username, email, status, extra_info}}
 *      → metrics "success"
 * </pre>
 */
@Service
public class UpdateUserLogic {

    private final ServiceContext svcCtx;
    private final Metrics metrics;
    private final LoggerFactoryBean logger;

    public UpdateUserLogic(ServiceContext svcCtx, Metrics metrics, LoggerFactoryBean logger) {
        this.svcCtx = svcCtx;
        this.metrics = metrics;
        this.logger = logger;
    }

    public LogicResult<ApiTypes.UpdateUserResp> updateUser(ApiTypes.UpdateUserReq req, HttpServletRequest httpRequest) {
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

        // 构造 rpcReq（对应 Go 第 40-47 行）
        // DRIFT-NEW-002 修复：直接调 Builder setter，不用反射（与 CreateAdmin / UpdateSelf 同款修复）。
        UpdateUserReq.Builder rpcBuilder = UpdateUserReq.newBuilder()
                .setUid(targetUid)
                .setOperatorId(operatorId);
        if (req.getUsername() != null) {
            rpcBuilder.setUsername(req.getUsername());
        }
        if (req.getPassword() != null) {
            rpcBuilder.setPassword(req.getPassword());
        }
        if (req.getEmail() != null) {
            rpcBuilder.setEmail(req.getEmail());
        }
        if (req.getExtraInfo() != null && !req.getExtraInfo().isEmpty()) {
            for (java.util.Map.Entry<String, String> entry : req.getExtraInfo().entrySet()) {
                rpcBuilder.putExtraInfo(entry.getKey(), entry.getValue());
            }
        }
        UpdateUserReq rpcReq = rpcBuilder.build();

        // 调 adminRpc.UpdateUser
        UpdateUserResp rpcResp;
        try {
            rpcResp = svcCtx.getAdminRpc().updateUser(rpcReq);
        } catch (StatusRuntimeException e) {
            // 对应 Go: metrics.AdminApiInterceptCount.WithLabelValues("/admin/updateuser", "db_error").Inc()
            metrics.getUpdateDbError().increment();
            logger.logBusinessErr(otelCtx, ErrorCode.ERROR.getCode(), e);
            // 对应 Go: switch st.Code() 第 54-63 行 —— 含 AlreadyExists
            Status.Code code = e.getStatus().getCode();
            switch (code) {
                case ALREADY_EXISTS:
                    return LogicResult.fail(ErrorCode.ERROR_USER_EXIST);        // 1001
                case NOT_FOUND:
                    return LogicResult.fail(ErrorCode.ERROR_USER_NOT_EXIST);    // 1003
                case INTERNAL:
                    return LogicResult.fail(ErrorCode.ERROR_SERVER_COMMON);    // 5001
                default:
                    return LogicResult.fail(ErrorCode.CODE_SERVER_BUSY);        // 1015
            }
        }

        // 成功：复制 5 字段（uid/username/email/status/extra_info），不 setScore
        // Success 字段：<b>必须显式 setSuccess(true)</b>（Go 第 68 行 Success: true）
        UserInfo protoUser = rpcResp.getUser();
        ApiTypes.UserInfo apiUser = new ApiTypes.UserInfo();
        if (protoUser != null) {
            apiUser.setUid(protoUser.getUid());
            apiUser.setUsername(protoUser.getUsername());
            String email = protoUser.getEmail();
            if (email != null && !email.isEmpty()) {
                apiUser.setEmail(email);
            }
            apiUser.setStatus(protoUser.getStatus());
            if (protoUser.getExtraInfoCount() > 0) {
                apiUser.setExtraInfo(protoUser.getExtraInfoMap());
            }
        }

        metrics.getUpdateSuccess().increment();
        ApiTypes.UpdateUserResp resp = new ApiTypes.UpdateUserResp();
        resp.setSuccess(true);
        resp.setUser(apiUser);
        return LogicResult.ok(resp);
    }

    private static Long parseLongOrNull(String s) {
        if (s == null) return null;
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
    }

    // invokeStringSetter / invokePutExtraInfoMap 已删除 —— DRIFT-NEW-002 修复：直接用 Builder setter。
}