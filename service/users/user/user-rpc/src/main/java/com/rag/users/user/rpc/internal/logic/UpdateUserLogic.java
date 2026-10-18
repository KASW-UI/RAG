package com.rag.users.user.rpc.internal.logic;

import com.rag.users.common.cryptx.PasswordUtil;
import com.rag.users.user.proto.UpdateUserReq;
import com.rag.users.user.proto.UpdateUserResp;
import com.rag.users.user.proto.UserInfo;
import com.rag.users.user.rpc.internal.model.UserExceptions.NotFoundException;
import com.rag.users.user.rpc.internal.model.User;
import com.rag.users.user.rpc.internal.svc.ServiceContext;
import io.grpc.Status;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 对应 Go 端 internal/logic/updateuserlogic.go：
 *   func (l *UpdateUserLogic) UpdateUser(in *pb.UpdateUserReq) (*pb.UpdateUserResp, error)
 *
 * 部分更新——只更新"显式传入且非空"的字段。
 *
 * Works（W1~W5）：
 *   W1 按需 Username 重名检查（仅 in.Username 非空时）
 *   W2 按需 bcrypt（仅 in.Password 非空时）
 *   W3 按需 Email 覆盖（仅非空时）
 *   W4 按需 ExtraInfo 覆盖（仅非 nil 时）
 *   W5 DataModel.updateUserById(uid, toUpdate) → 仅更新非空字段（D-017）
 *   W6 重新读回 user 构造 resp
 *
 * 关键 Decision：
 *   D-004：Username 重名检查不排除自己——若用户把自己的 Username 改成现值也会 AlreadyExists（Go 端 quirk，1:1 保留）
 *   D-017：DB UPDATE 只写 4 列（DataModel.updateUserById 用动态 JPQL 拼字段）
 *   注：resp 不含 Status 字段（Go 端 UserInfo 有但 UpdateUserResp 不带；HTTP 层也只透传 4 个字段）
 *
 * 没有 metrics / OTel span（Go 端 updateuserlogic.go 也没埋点）。
 */
@Service
public class UpdateUserLogic {

    private final ServiceContext svcCtx;

    public UpdateUserLogic(ServiceContext svcCtx) {
        this.svcCtx = svcCtx;
    }

    public UpdateUserResp updateUser(UpdateUserReq req) {
        // DRIFT-018: timeout -> DEADLINE_EXCEEDED
        svcCtx.checkDeadline();
        User toUpdate = new User();

        // W1 Username 重名检查（仅当非空时）
        if (!req.getUsername().isEmpty()) {
            // 关键：不要在 try/catch 里 throw StatusRuntimeException 后又被 catch(Exception) 吞掉
            // 拆开 lookup 与 throw：先拿到 User（可能 null），再判断
            com.rag.users.user.rpc.internal.model.User existing;
            try {
                existing = svcCtx.getUserModel().findOneByUserName(req.getUsername());
            } catch (NotFoundException e) {
                existing = null;
            }
            if (existing != null) {
                // 命中 = 已被占用 → AlreadyExists（D-004：不排除自己）
                throw Status.ALREADY_EXISTS
                        .withDescription("用户名已存在")
                        .asRuntimeException();
            }
            toUpdate.setUsername(req.getUsername());
        }

        // W2 bcrypt
        if (!req.getPassword().isEmpty()) {
            try {
                toUpdate.setPassword(PasswordUtil.encrypt(req.getPassword()));
            } catch (Exception e) {
                throw Status.INTERNAL.withDescription("密码生成失败").asRuntimeException();
            }
        }

        // W3 Email
        if (!req.getEmail().isEmpty()) {
            toUpdate.setEmail(req.getEmail());
        }

        // W4 ExtraInfo
        if (req.getExtraInfoCount() > 0) {
            Map<String, String> map = new HashMap<>();
            req.getExtraInfoMap().forEach(map::put);
            toUpdate.setExtraInfo(map);
        }

        // W5 Update（DataModel.updateUserById 用动态 JPQL，只写非空字段 —— D-017）
        try {
            svcCtx.getUserModel().updateUserById(req.getUid(), toUpdate);
        } catch (Exception e) {
            throw Status.INTERNAL.withDescription("DB更新失败").asRuntimeException();
        }

        // W6 重读
        User fresh;
        try {
            fresh = svcCtx.getUserModel().findOneByUid(req.getUid());
        } catch (NotFoundException e) {
            // 并发删除场景
            throw Status.NOT_FOUND.withDescription("用户不存在").asRuntimeException();
        } catch (Exception e) {
            throw Status.INTERNAL.withDescription("DB查询失败").asRuntimeException();
        }

        UserInfo.Builder b = UserInfo.newBuilder()
                .setUid(fresh.getUid())
                .setUsername(fresh.getUsername() == null ? "" : fresh.getUsername())
                .setEmail(fresh.getEmail() == null ? "" : fresh.getEmail());
        Map<String, String> extra = fresh.getExtraInfo();
        if (extra != null && !extra.isEmpty()) {
            b.putAllExtraInfo(extra);
        } else {
            b.putAllExtraInfo(new HashMap<>());
        }
        // 注：resp 不含 Status 字段，对齐 Go 端 UpdateUserResp.UserInfo 只填 Uid/Username/Email/ExtraInfo

        return UpdateUserResp.newBuilder()
                .setUser(b.build())
                .build();
    }
}