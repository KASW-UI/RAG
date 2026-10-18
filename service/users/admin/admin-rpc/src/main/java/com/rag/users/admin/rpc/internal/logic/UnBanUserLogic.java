package com.rag.users.admin.rpc.internal.logic;

import com.rag.users.admin.proto.UnBanUserReq;
import com.rag.users.admin.proto.UnBanUserResp;
import com.rag.users.admin.rpc.internal.metrics.Metrics;
import com.rag.users.admin.rpc.internal.model.AdminExceptions.NotFoundException;
import com.rag.users.admin.rpc.internal.svc.ServiceContext;
import io.grpc.Status;
import io.opentelemetry.api.trace.Span;
import org.springframework.stereotype.Service;

/**
 * 对应 Go 端 internal/logic/unbanuserlogic.go：
 *   func (l *UnBanUserLogic) UnBanUser(in *pb.UnBanUserReq) (*pb.UnBanUserResp, error)
 *
 * <p>把 user.status 改回 0（解封）。
 *
 * <p>Works（W1~W3）：
 *   W1 OTel span "Action-Admin-UnbanUser"（注意 Go 端是 UnbanUser 不是 UnBanUser），
 *      属性 audit.operator_id + audit.target_uid
 *   W2 UpdateUserStatusByUid(uid, 0) → NotFound（metrics unban+user_not_found）/ Internal（metrics unban+db_error）
 *   W3 metrics unban+success → UnBanUserResp{Success=true}
 */
@Service
public class UnBanUserLogic {

    private final ServiceContext svcCtx;
    private final Metrics metrics;

    public UnBanUserLogic(ServiceContext svcCtx, Metrics metrics) {
        this.svcCtx = svcCtx;
        this.metrics = metrics;
    }

    public UnBanUserResp unBanUser(UnBanUserReq req) {
        Span span = svcCtx.getTracer().spanBuilder("Action-Admin-UnbanUser")
                .setAttribute("audit.operator_id", req.getOperatorId())
                .setAttribute("audit.target_uid", req.getUid())
                .startSpan();
        try {
            // W2 update status=0
            try {
                svcCtx.getDataModel().updateUserStatusByUid(req.getUid(), 0L);
            } catch (NotFoundException e) {
                metrics.getUnbanUserNotFound().increment();
                throw Status.NOT_FOUND
                        .withDescription("用户不存在")
                        .asRuntimeException();
            } catch (Exception e) {
                metrics.getUnbanDbError().increment();
                span.recordException(e);
                throw Status.INTERNAL
                        .withDescription("DB更新失败")
                        .asRuntimeException();
            }

            // W3 success
            metrics.getUnbanSuccess().increment();
            return UnBanUserResp.newBuilder()
                    .setSuccess(true)
                    .build();
        } finally {
            span.end();
        }
    }
}