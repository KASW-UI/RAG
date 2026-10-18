package com.rag.users.admin.rpc.internal.logic;

import com.rag.users.admin.proto.ResetUserPasswordReq;
import com.rag.users.admin.proto.ResetUserPasswordResp;
import com.rag.users.admin.rpc.internal.metrics.Metrics;
import com.rag.users.admin.rpc.internal.model.AdminExceptions.NotFoundException;
import com.rag.users.admin.rpc.internal.svc.ServiceContext;
import com.rag.users.common.cryptx.PasswordUtil;
import io.grpc.Status;
import io.opentelemetry.api.trace.Span;
import org.springframework.stereotype.Service;

/**
 * 对应 Go 端 internal/logic/resetuserpasswordlogic.go：
 *   func (l *ResetUserPasswordLogic) ResetUserPassword(in *pb.ResetUserPasswordReq) (*pb.ResetUserPasswordResp, error)
 *
 * <p>admin 把 user 的密码重置为 System.DefaultPassword（来自 etc/admin.yaml，由 Config.system.defaultPassword 提供）。
 *
 * <p>Works（W1~W5）：
 *   W1 OTel span "Action-Admin-ResetUserPassword"，属性 audit.operator_id + audit.target_uid
 *   W2 FindOneUserByUid(uid) → NotFound（metrics resetpassword+user_not_found）/ Internal（metrics resetpassword+db_error）
 *   W3 bcrypt DefaultPassword → Internal（metrics resetpassword+internal_error）
 *   W4 UpdateUserPasswordByUid(uid, hash) → Internal（metrics resetpassword+db_error）
 *   W5 metrics resetpassword+success → ResetUserPasswordResp{Success=true}
 *
 * <p>Go 端 UpdateUserPasswordByUid 不检查 RowsAffected（不会抛 NotFound），所以 ResetUserPassword 即使
 *     user 在 W2 和 W4 之间被并发删了，仍会返 success。这是 Go 端的语义，1:1 保留。
 */
@Service
public class ResetUserPasswordLogic {

    private final ServiceContext svcCtx;
    private final Metrics metrics;

    public ResetUserPasswordLogic(ServiceContext svcCtx, Metrics metrics) {
        this.svcCtx = svcCtx;
        this.metrics = metrics;
    }

    public ResetUserPasswordResp resetUserPassword(ResetUserPasswordReq req) {
        Span span = svcCtx.getTracer().spanBuilder("Action-Admin-ResetUserPassword")
                .setAttribute("audit.operator_id", req.getOperatorId())
                .setAttribute("audit.target_uid", req.getUid())
                .startSpan();
        try {
            // W2 查 user
            try {
                svcCtx.getDataModel().findOneUserByUid(req.getUid());
            } catch (NotFoundException e) {
                metrics.getResetPasswordUserNotFound().increment();
                throw Status.NOT_FOUND
                        .withDescription("用户不存在")
                        .asRuntimeException();
            } catch (Exception e) {
                metrics.getResetPasswordDbError().increment();
                span.recordException(e);
                throw Status.INTERNAL
                        .withDescription("DB查询失败")
                        .asRuntimeException();
            }

            // W3 bcrypt DefaultPassword
            String defaultPassword = svcCtx.getConfig().getSystem().getDefaultPassword();
            if (defaultPassword == null || defaultPassword.isEmpty()) {
                // 业务约束：admin-rpc 启动时 admin.yaml 必须配置 System.DefaultPassword。
                // 这里 fallback 不应该发生，但兜底避免 NPE。
                metrics.getResetPasswordInternalError().increment();
                span.recordException(new IllegalStateException("System.DefaultPassword not configured"));
                throw Status.INTERNAL
                        .withDescription("密码加密失败")
                        .asRuntimeException();
            }
            String hash;
            try {
                hash = PasswordUtil.encrypt(defaultPassword);
            } catch (Exception e) {
                metrics.getResetPasswordInternalError().increment();
                span.recordException(e);
                throw Status.INTERNAL
                        .withDescription("密码加密失败")
                        .asRuntimeException();
            }

            // W4 update
            try {
                svcCtx.getDataModel().updateUserPasswordByUid(req.getUid(), hash);
            } catch (Exception e) {
                metrics.getResetPasswordDbError().increment();
                span.recordException(e);
                throw Status.INTERNAL
                        .withDescription("DB更新失败")
                        .asRuntimeException();
            }

            // W5 success
            metrics.getResetPasswordSuccess().increment();
            return ResetUserPasswordResp.newBuilder()
                    .setSuccess(true)
                    .build();
        } finally {
            span.end();
        }
    }
}