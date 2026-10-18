package com.rag.users.admin.rpc.internal.logic;

import com.rag.users.admin.proto.DeleteUserReq;
import com.rag.users.admin.proto.DeleteUserResp;
import com.rag.users.admin.rpc.internal.metrics.Metrics;
import com.rag.users.admin.rpc.internal.model.AdminExceptions.NotFoundException;
import com.rag.users.admin.rpc.internal.svc.ServiceContext;
import io.grpc.Status;
import io.opentelemetry.api.trace.Span;
import org.springframework.stereotype.Service;

/**
 * 对应 Go 端 internal/logic/deleteuserlogic.go：
 *   func (l *DeleteUserLogic) DeleteUser(in *pb.DeleteUserReq) (*pb.DeleteUserResp, error)
 *
 * <p>Works（W1~W3）：
 *   W1 OTel span "Action-Admin-DeleteUser"，属性 audit.operator_id + audit.target_uid
 *   W2 DeleteOneUserByUid(uid) → NotFound（metrics delete+user_not_found）/ Internal（metrics delete+db_error）
 *   W3 metrics delete+success → DeleteUserResp{Success=true}
 *
 * <p>DataModel.deleteUserByUid 已改造：RowsAffected=0 抛 NotFoundException，
 *     1:1 跟随 Go 端 `result.RowsAffected == 0 → ErrorNotFound`。
 *
 * <p>Go 端没有 internal_error label（DB 失败就 db_error），所以 DeleteUser 只埋 3 个 result。
 */
@Service
public class DeleteUserLogic {

    private final ServiceContext svcCtx;
    private final Metrics metrics;

    public DeleteUserLogic(ServiceContext svcCtx, Metrics metrics) {
        this.svcCtx = svcCtx;
        this.metrics = metrics;
    }

    public DeleteUserResp deleteUser(DeleteUserReq req) {
        Span span = svcCtx.getTracer().spanBuilder("Action-Admin-DeleteUser")
                .setAttribute("audit.operator_id", req.getOperatorId())
                .setAttribute("audit.target_uid", req.getUid())
                .startSpan();
        try {
            // W2 delete
            try {
                svcCtx.getDataModel().deleteUserByUid(req.getUid());
            } catch (NotFoundException e) {
                metrics.getDeleteUserUserNotFound().increment();
                throw Status.NOT_FOUND
                        .withDescription(String.format("用户%d不存在", req.getUid()))
                        .asRuntimeException();
            } catch (Exception e) {
                metrics.getDeleteUserDbError().increment();
                span.recordException(e);
                throw Status.INTERNAL
                        .withDescription("DB更新失败")
                        .asRuntimeException();
            }

            // W3 success
            metrics.getDeleteUserSuccess().increment();
            return DeleteUserResp.newBuilder()
                    .setSuccess(true)
                    .build();
        } finally {
            span.end();
        }
    }
}