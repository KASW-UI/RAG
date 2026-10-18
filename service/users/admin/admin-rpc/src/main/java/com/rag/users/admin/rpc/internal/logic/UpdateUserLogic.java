package com.rag.users.admin.rpc.internal.logic;

import com.rag.users.admin.proto.UpdateUserReq;
import com.rag.users.admin.proto.UpdateUserResp;
import com.rag.users.admin.proto.UserInfo;
import com.rag.users.admin.rpc.internal.metrics.Metrics;
import com.rag.users.admin.rpc.internal.model.AdminExceptions.NotFoundException;
import com.rag.users.admin.rpc.internal.model.User;
import com.rag.users.admin.rpc.internal.svc.ServiceContext;
import com.rag.users.common.cryptx.PasswordUtil;
import io.grpc.Status;
import io.opentelemetry.api.trace.Span;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 对应 Go 端 internal/logic/updateuserlogic.go：
 *   func (l *UpdateUserLogic) UpdateUser(in *pb.UpdateUserReq) (*pb.UpdateUserResp, error)
 *
 * <p>admin 改 user 表里的用户（不是 admin 自己）。
 *
 * <p>Works（W1~W6）：
 *   W1 OTel span "Action-Admin-UpdateUser"，属性 audit.operator_id + audit.target_uid
 *   W2 FindOneUserByUid(uid) → NotFound（metrics update+user_not_found）/ Internal（metrics update+db_error）
 *   W3 if username 非空 → 查 username 冲突（existUser.Uid != in.Uid → AlreadyExists）
 *   W4 构造 toUpdate（跳零值；User.Email 非 null，用普通赋值）
 *   W5 UpdateOneUserByUid（DB 失败 → Internal metrics update+db_error）
 *   W6 重读 user，构造 resp{User=UserInfo}，metrics update+success
 *
 * <p>Found/Score 字段 1:1 跟随 Go 端：不 setFound / 不 setScore。
 *
 * <p>UserInfo.Status 用 Long 包装类型，0L 兜底。
 */
@Service
public class UpdateUserLogic {

    private final ServiceContext svcCtx;
    private final Metrics metrics;

    public UpdateUserLogic(ServiceContext svcCtx, Metrics metrics) {
        this.svcCtx = svcCtx;
        this.metrics = metrics;
    }

    public UpdateUserResp updateUser(UpdateUserReq req) {
        Span span = svcCtx.getTracer().spanBuilder("Action-Admin-UpdateUser")
                .setAttribute("audit.operator_id", req.getOperatorId())
                .setAttribute("audit.target_uid", req.getUid())
                .startSpan();
        try {
            // W2 查 user
            User target;
            try {
                target = svcCtx.getDataModel().findOneUserByUid(req.getUid());
            } catch (NotFoundException e) {
                metrics.getUpdateUserUserNotFound().increment();
                throw Status.NOT_FOUND
                        .withDescription("用户不存在")
                        .asRuntimeException();
            } catch (Exception e) {
                metrics.getUpdateUserDbError().increment();
                span.recordException(e);
                throw Status.INTERNAL
                        .withDescription("DB查询失败")
                        .asRuntimeException();
            }

            // W3 username 冲突检查（仅在客户端显式传 username 时）
            User toUpdate = new User();
            if (!req.getUsername().isEmpty()) {
                User existing;
                try {
                    existing = svcCtx.getDataModel().findOneUserByUsername(req.getUsername());
                } catch (NotFoundException e) {
                    existing = null;
                } catch (Exception e) {
                    metrics.getUpdateUserDbError().increment();
                    span.recordException(e);
                    throw Status.INTERNAL
                            .withDescription("DB查询失败")
                            .asRuntimeException();
                }
                // D-004 1:1 保留：不排除自己。
                if (existing != null && existing.getUid() != req.getUid()) {
                    throw Status.ALREADY_EXISTS
                            .withDescription("用户名已存在")
                            .asRuntimeException();
                }
                toUpdate.setUsername(req.getUsername());
            }

            // W4 bcrypt password
            if (!req.getPassword().isEmpty()) {
                try {
                    toUpdate.setPassword(PasswordUtil.encrypt(req.getPassword()));
                } catch (Exception e) {
                    metrics.getUpdateUserInternalError().increment();
                    span.recordException(e);
                    throw Status.INTERNAL
                            .withDescription("密码加密失败")
                            .asRuntimeException();
                }
            }
            // email 非 null（User 表定义）：不进 null，仅 trim。Go 端 `toUpdate.Email = in.Email`，赋值整个字符串。
            if (!req.getEmail().isEmpty()) {
                toUpdate.setEmail(req.getEmail());
            }
            if (req.getExtraInfoCount() > 0) {
                Map<String, String> map = new HashMap<>();
                req.getExtraInfoMap().forEach(map::put);
                toUpdate.setExtraInfo(map);
            }

            // W5 update
            try {
                svcCtx.getDataModel().updateUserByUid(req.getUid(), toUpdate);
            } catch (Exception e) {
                metrics.getUpdateUserDbError().increment();
                span.recordException(e);
                throw Status.INTERNAL
                        .withDescription("DB更新失败")
                        .asRuntimeException();
            }

            // W6 重读
            User newUser;
            try {
                newUser = svcCtx.getDataModel().findOneUserByUid(req.getUid());
            } catch (Exception e) {
                metrics.getUpdateUserDbError().increment();
                span.recordException(e);
                throw Status.INTERNAL
                        .withDescription("DB查询失败")
                        .asRuntimeException();
            }

            UserInfo.Builder b = UserInfo.newBuilder()
                    .setUid(newUser.getUid())
                    .setUsername(newUser.getUsername() == null ? "" : newUser.getUsername())
                    .setEmail(newUser.getEmail() == null ? "" : newUser.getEmail())
                    .setStatus(newUser.getStatus() == null ? 0L : newUser.getStatus());
            Map<String, String> extra = newUser.getExtraInfo();
            if (extra != null && !extra.isEmpty()) {
                b.putAllExtraInfo(extra);
            } else {
                b.putAllExtraInfo(new HashMap<>());
            }

            metrics.getUpdateUserSuccess().increment();
            return UpdateUserResp.newBuilder()
                    .setSuccess(true)
                    .setUser(b.build())
                    .build();
        } finally {
            span.end();
        }
    }
}