package com.rag.users.user.rpc.internal.logic;

import com.rag.users.user.proto.DeleteUserReq;
import com.rag.users.user.proto.DeleteUserResp;
import com.rag.users.user.rpc.internal.model.UserExceptions.NotFoundException;
import com.rag.users.user.rpc.internal.svc.ServiceContext;
import io.grpc.Status;
import io.opentelemetry.api.trace.Span;
import org.springframework.stereotype.Service;

/**
 * 对应 Go 端 internal/logic/deleteuserlogic.go。
 *
 * Works：
 *   W1 OTel span "Action-User-Delete"，属性 biz-uid=in.Uid
 *   W2 FindOneByUid(uid) → 必须存在
 *   W3 DataModel.deleteUserByUid(uid)
 *
 * 关键 Decision：
 *   D-003：Go 端同时返 Success=false + NotFound error，但 gRPC wire 只发 error
 *         → Java 端只抛 StatusRuntimeException
 *
 * 没有 metrics（D-016 1:1 保留）。
 * UN-1：Go 端 DeleteUser 不级联删 user_avatar_history 表（DeleteUserByUid 只 Delete(&User{})）；
 *       是否级联由 Postgres FK ON DELETE CASCADE 决定——没看 schema，等用户确认。
 */
@Service
public class DeleteUserLogic {

    private final ServiceContext svcCtx;

    public DeleteUserLogic(ServiceContext svcCtx) {
        this.svcCtx = svcCtx;
    }

    public DeleteUserResp deleteUser(DeleteUserReq req) {
        Span span = svcCtx.getTracer().spanBuilder("Action-User-Delete")
                .setAttribute("biz-uid", req.getUid())
                .startSpan();
        // DRIFT-018: timeout -> DEADLINE_EXCEEDED
        svcCtx.checkDeadline();

                try {
            // W2
            try {
                svcCtx.getUserModel().findOneByUid(req.getUid());
            } catch (NotFoundException e) {
                throw Status.NOT_FOUND
                        .withDescription("用户不存在")
                        .asRuntimeException();
            } catch (Exception e) {
                throw Status.INTERNAL
                        .withDescription("DB查询失败")
                        .asRuntimeException();
            }

            // W3
            try {
                svcCtx.getUserModel().deleteUserByUid(req.getUid());
            } catch (Exception e) {
                throw Status.INTERNAL
                        .withDescription("DB更新失败")
                        .asRuntimeException();
            }

            return DeleteUserResp.newBuilder().setSuccess(true).build();
        } finally {
            span.end();
        }
    }
}