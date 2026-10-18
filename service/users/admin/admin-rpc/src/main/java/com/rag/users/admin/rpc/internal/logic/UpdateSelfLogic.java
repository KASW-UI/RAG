package com.rag.users.admin.rpc.internal.logic;

import com.rag.users.admin.proto.AdminInfo;
import com.rag.users.admin.proto.UpdateSelfReq;
import com.rag.users.admin.proto.UpdateSelfResp;
import com.rag.users.admin.rpc.internal.model.Admin;
import com.rag.users.admin.rpc.internal.model.AdminExceptions.NotFoundException;
import com.rag.users.admin.rpc.internal.svc.ServiceContext;
import com.rag.users.common.cryptx.PasswordUtil;
import io.grpc.Status;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 对应 Go 端 internal/logic/updateselflogic.go：
 *   func (l *UpdateSelfLogic) UpdateSelf(in *pb.UpdateSelfReq) (*pb.UpdateSelfResp, error)
 *
 * <p>admin 自己改自己的账号信息。改的是 admin 表，不是 user 表。
 *
 * <p>Works（W1~W5）：
 *   W1 FindOneAdminByUid(uid) → NotFound / Internal
 *   W2 if username 非空 → 查 username 冲突（exist.Uid != self.Uid → AlreadyExists）
 *   W3 构造 toUpdate（跳零值：username/password/email/extra_info）
 *   W4 UpdateOneAdminByUid
 *   W5 重读 admin，构造 resp{Success=true, Admin=AdminInfo}
 *
 * <p>email 特殊处理：Admin.Email 是 *string nullable。
 *   Go 端 `toUpdate.Email = optionalString(in.Email)`：trim 后空 → nil → GORM 写 SQL NULL。
 *   Java 端：用 AdminHelpers.optionalString 把 req.Email trim，空 → null；DataModel.updateAdminByUid 已改造，
 *   `hasEmail = newAdmin.getEmail() != null`（不判断 isEmpty），null 视为"显式设置"，写 SQL NULL。
 *
 * <p>没有 OTel span / metrics（Go 端 updateselflogic.go 也没埋点，D-016 1:1 保留）。
 */
@Service
public class UpdateSelfLogic {

    private final ServiceContext svcCtx;

    public UpdateSelfLogic(ServiceContext svcCtx) {
        this.svcCtx = svcCtx;
    }

    public UpdateSelfResp updateSelf(UpdateSelfReq req) {
        // W1 查 admin
        Admin self;
        try {
            self = svcCtx.getDataModel().findOneAdminByUid(req.getUid());
        } catch (NotFoundException e) {
            throw Status.NOT_FOUND
                    .withDescription("用户不存在")
                    .asRuntimeException();
        } catch (Exception e) {
            throw Status.INTERNAL
                    .withDescription("DB查询失败")
                    .asRuntimeException();
        }

        // W2 username 冲突检查（仅在客户端显式传 username 时）
        if (!req.getUsername().isEmpty()) {
            Admin existing;
            try {
                existing = svcCtx.getDataModel().findOneAdminByUsername(req.getUsername());
            } catch (NotFoundException e) {
                existing = null; // 未被占用，名字可用
            } catch (Exception e) {
                throw Status.INTERNAL
                        .withDescription("DB查询失败")
                        .asRuntimeException();
            }
            // D-004 1:1 保留：不排除自己。如果 admin 把 username 改成自己现在的值，也会 AlreadyExists。
            if (existing != null && existing.getUid() != req.getUid()) {
                throw Status.ALREADY_EXISTS
                        .withDescription("该用户名已被使用")
                        .asRuntimeException();
            }
        }

        // W3 构造 toUpdate（跳零值）
        Admin toUpdate = new Admin();
        if (!req.getUsername().isEmpty()) {
            toUpdate.setUsername(req.getUsername());
        }
        if (!req.getPassword().isEmpty()) {
            try {
                toUpdate.setPassword(PasswordUtil.encrypt(req.getPassword()));
            } catch (Exception e) {
                throw Status.INTERNAL
                        .withDescription("密码加密失败")
                        .asRuntimeException();
            }
        }
        // email nullable：admin 表的 email 可空。Go 端 optionalString 把 trim 后的空 → nil（写 SQL NULL），
        // 非空 → &trimmed。Java 侧用 AdminHelpers.optionalString 实现同样语义。
        if (!req.getEmail().isEmpty()) {
            toUpdate.setEmail(AdminHelpers.optionalString(req.getEmail()));
        }
        if (req.getExtraInfoCount() > 0) {
            Map<String, String> map = new HashMap<>();
            req.getExtraInfoMap().forEach(map::put);
            toUpdate.setExtraInfo(map);
        }

        // W4 更新
        try {
            svcCtx.getDataModel().updateAdminByUid(req.getUid(), toUpdate);
        } catch (Exception e) {
            throw Status.INTERNAL
                    .withDescription("DB更新失败")
                    .asRuntimeException();
        }

        // W5 重读 + 构造 resp
        Admin newAdmin;
        try {
            newAdmin = svcCtx.getDataModel().findOneAdminByUid(req.getUid());
        } catch (NotFoundException e) {
            // 并发删除场景
            throw Status.NOT_FOUND
                    .withDescription("用户不存在")
                    .asRuntimeException();
        } catch (Exception e) {
            throw Status.INTERNAL
                    .withDescription("DB查询失败")
                    .asRuntimeException();
        }

        AdminInfo.Builder b = AdminInfo.newBuilder()
                .setUid(newAdmin.getUid())
                .setUsername(newAdmin.getUsername() == null ? "" : newAdmin.getUsername())
                .setEmail(AdminHelpers.stringValue(newAdmin.getEmail()));
        Map<String, String> extra = newAdmin.getExtraInfo();
        if (extra != null && !extra.isEmpty()) {
            b.putAllExtraInfo(extra);
        } else {
            b.putAllExtraInfo(new HashMap<>());
        }

        return UpdateSelfResp.newBuilder()
                .setSuccess(true)
                .setAdmin(b.build())
                .build();
    }
}