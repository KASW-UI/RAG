package com.rag.users.admin.rpc.internal.logic;

import com.rag.users.admin.proto.BanUserReq;
import com.rag.users.admin.proto.BanUserResp;
import com.rag.users.admin.rpc.internal.metrics.Metrics;
import com.rag.users.admin.rpc.internal.model.AdminExceptions.NotFoundException;
import com.rag.users.admin.rpc.internal.svc.ServiceContext;
import io.grpc.Status;
import io.opentelemetry.api.trace.Span;
import org.springframework.stereotype.Service;

/**
 * 对应 Go 端 internal/logic/banuserlogic.go：
 *   func (l *BanUserLogic) BanUser(in *pb.BanUserReq) (*pb.BanUserResp, error)
 *
 * <p>把 user.status 改成 1（封禁）。
 *
 * <p>Works（W1~W3）：
 *   W1 OTel span "Action-Admin-BanUser"，属性 audit.operator_id + audit.target_uid
 *   W2 UpdateUserStatusByUid(uid, 1) → NotFound（metrics ban+user_not_found）/ Internal（metrics ban+db_error）
 *   W3 metrics ban+success → BanUserResp{Success=true}
 *
 * <p>DataModel.updateUserStatusByUid 已改造：RowsAffected=0 抛 NotFoundException，
 *     1:1 跟随 Go 端 `result.RowsAffected == 0 → ErrorNotFound`。
 */
@Service
public class BanUserLogic {

    private final ServiceContext svcCtx;
    private final Metrics metrics;

    public BanUserLogic(ServiceContext svcCtx, Metrics metrics) {
        this.svcCtx = svcCtx;
        this.metrics = metrics;
    }

    public BanUserResp banUser(BanUserReq req) {
        Span span = svcCtx.getTracer().spanBuilder("Action-Admin-BanUser")
                .setAttribute("audit.operator_id", req.getOperatorId())
                .setAttribute("audit.target_uid", req.getUid())
                .startSpan();
        try {
            // W2 update status=1
            try {
                svcCtx.getDataModel().updateUserStatusByUid(req.getUid(), 1L);
            } catch (NotFoundException e) {
                metrics.getBanUserNotFound().increment();
                throw Status.NOT_FOUND
                        .withDescription("用户不存在")
                        .asRuntimeException();
            } catch (Exception e) {
                metrics.getBanDbError().increment();
                span.recordException(e);
                throw Status.INTERNAL
                        .withDescription("DB更新失败")
                        .asRuntimeException();
            }

            // W3 success
            metrics.getBanSuccess().increment();
            return BanUserResp.newBuilder()
                    .setSuccess(true)
                    .build();
        } finally {
            span.end();
        }
    }
}