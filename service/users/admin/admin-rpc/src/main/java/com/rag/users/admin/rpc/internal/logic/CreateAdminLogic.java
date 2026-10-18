package com.rag.users.admin.rpc.internal.logic;

import com.rag.common.snowflake.Snowflake;
import com.rag.users.admin.proto.CreateAdminReq;
import com.rag.users.admin.proto.CreateAdminResp;
import com.rag.users.admin.rpc.internal.model.AdminExceptions;
import com.rag.users.admin.rpc.internal.model.DataModel;
import com.rag.users.admin.rpc.internal.svc.ServiceContext;
import com.rag.users.common.cryptx.PasswordUtil;
import io.grpc.Status;
import io.opentelemetry.api.trace.Span;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 对应 Go 端 internal/logic/createadminlogic.go：
 *   func (l *CreateAdminLogic) CreateAdmin(in *pb.CreateAdminReq) (*pb.CreateAdminResp, error)
 *
 * <p>用 invite code 创建一个新 admin。整段含 4 步原子事务：
 *   W1 查 invite（悲观锁 SELECT ... FOR UPDATE by code）
 *   W2 校验 invite 未用未过期
 *   W3 查 username 冲突
 *   W4 bcrypt password + Snowflake uid
 *   W5 insert admin（unique violation 兜底 username 冲突）
 *   W6 标记 invite 已用
 *
 * <p>Works（Java 端顺序与 Go 略有不同）：
 *   J1 OTel span "Action-Admin-CreateAdmin"，属性 audit.admin_username
 *   J2 校验 inviteCode 非空 → PERMISSION_DENIED "管理员邀请码错误"
 *   J3 trim username + inviteCode（Go 端 strings.TrimSpace）
 *   J4 bcrypt password（在事务外，避免延长事务持有时间；user-rpc RegisterLogic 也是这个模式）
 *   J5 Snowflake.nextId()（事务外）
 *   J6 DataModel.createAdminAtomic(...) 4 步事务
 *   J7 错误映射：
 *      - InviteUnavailableException → PERMISSION_DENIED "管理员邀请码错误"
 *      - UsernameTakenException → ALREADY_EXISTS "用户名已存在"
 *      - 唯一冲突（race condition 兜底） → ALREADY_EXISTS "用户名已存在"
 *      - 其他 Exception → INTERNAL "DB添加失败"
 *   J8 成功 → CreateAdminResp{Uid=uid}
 *
 * <p>关键 Decision：
 *   - invite code 必须正确才能创建 admin → PermissionDenied
 *   - invite 过期或已用 → PermissionDenied（不区分，给同一个错误码）
 *   - username 唯一（admin 表）→ BALANCE + AlreadyExists
 *   - 4 步必须原子（W1+W2+W3+W5+W6 任一失败整体回滚）
 *
 * <p>与 Go 端 L1-L3 1:1 复刻，错误描述用正常 UTF-8 中文（Go 端源文件 mojibake 不继承）。
 */
@Service
public class CreateAdminLogic {

    private final ServiceContext svcCtx;

    public CreateAdminLogic(ServiceContext svcCtx) {
        this.svcCtx = svcCtx;
    }

    public CreateAdminResp createAdmin(CreateAdminReq req) {
        Span span = svcCtx.getTracer().spanBuilder("Action-Admin-CreateAdmin")
                .setAttribute("audit.admin_username", req.getUsername())
                .startSpan();
        try {
            // J2 inviteCode 非空
            String inviteCode = req.getInviteCode() == null ? "" : req.getInviteCode().trim();
            if (inviteCode.isEmpty()) {
                throw Status.PERMISSION_DENIED
                        .withDescription("管理员邀请码错误")
                        .asRuntimeException();
            }

            // J3 trim username（Go 端 strings.TrimSpace）
            String username = req.getUsername() == null ? "" : req.getUsername().trim();
            if (username.isEmpty()) {
                throw Status.INVALID_ARGUMENT
                        .withDescription("用户名不能为空")
                        .asRuntimeException();
            }

            // J4 bcrypt password（事务外，避免延长事务持有时间）
            String passwordHash;
            try {
                passwordHash = PasswordUtil.encrypt(req.getPassword());
            } catch (Exception e) {
                span.recordException(e);
                throw Status.INTERNAL
                        .withDescription("DB添加失败")
                        .asRuntimeException();
            }

            // J5 Snowflake uid（事务外）
            long uid;
            try {
                uid = Snowflake.nextId();
            } catch (Exception e) {
                span.recordException(e);
                throw Status.INTERNAL
                        .withDescription("DB添加失败")
                        .asRuntimeException();
            }

            // J3.5 extra_info
            Map<String, String> extraInfo = null;
            if (req.getExtraInfoCount() > 0) {
                extraInfo = new HashMap<>();
                req.getExtraInfoMap().forEach(extraInfo::put);
            }

            // J6 4 步事务（DataModel.createAdminAtomic 内部 @Transactional）
            try {
                svcCtx.getDataModel().createAdminAtomic(
                        username,
                        inviteCode,
                        passwordHash,
                        AdminHelpers.optionalString(req.getEmail()),
                        extraInfo,
                        uid);
            } catch (DataModel.InviteUnavailableException e) {
                // W1 找不到 invite / invite 已用 / invite 过期 → 统一 PermissionDenied
                throw Status.PERMISSION_DENIED
                        .withDescription("管理员邀请码错误")
                        .asRuntimeException();
            } catch (DataModel.UsernameTakenException e) {
                // W3 事务内 username 已被其他 admin 占用 → AlreadyExists
                throw Status.ALREADY_EXISTS
                        .withDescription("用户名已存在")
                        .asRuntimeException();
            } catch (Exception e) {
                // 兜底：W5 insert admin 时 unique violation（race condition）
                // W3 的 username 检查和 W5 的 insert 之间可能并发
                if (AdminExceptions.isUniqueViolation(e)) {
                    throw Status.ALREADY_EXISTS
                            .withDescription("用户名已存在")
                            .asRuntimeException();
                }
                span.recordException(e);
                throw Status.INTERNAL
                        .withDescription("DB添加失败")
                        .asRuntimeException();
            }

            // J8 成功
            return CreateAdminResp.newBuilder()
                    .setUid(uid)
                    .build();
        } finally {
            span.end();
        }
    }
}