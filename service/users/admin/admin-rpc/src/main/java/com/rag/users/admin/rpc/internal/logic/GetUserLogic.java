package com.rag.users.admin.rpc.internal.logic;

import com.rag.users.admin.proto.GetUserReq;
import com.rag.users.admin.proto.GetUserResp;
import com.rag.users.admin.proto.UserInfo;
import com.rag.users.admin.rpc.internal.model.AdminExceptions.NotFoundException;
import com.rag.users.admin.rpc.internal.model.User;
import com.rag.users.admin.rpc.internal.svc.ServiceContext;
import io.grpc.Status;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 对应 Go 端 internal/logic/getuserlogic.go：
 *   func (l *GetUserLogic) GetUser(in *pb.GetUserReq) (*pb.GetUserResp, error)
 *
 * <p>与 user-rpc GetUserLogic 关键差异：admin-rpc UserInfo 比 user-rpc UserInfo 多一个 score 字段。
 * Go 端 getuserlogic.go 的 pb.UserInfo 构造没有设置 score 字段（proto3 默认 0），1:1 保留：
 * Java 端不显式 setScore（默认 0）。
 *
 * <p>Works（W1~W3）：
 *   W1 FindOneUserByUid(uid)
 *   W2 NotFoundException → StatusRuntimeException(NOT_FOUND, "用户不存在")
 *   W3 构造 GetUserResp{User: UserInfo{uid, username, email, status, extra_info}}
 *
 * <p>Found 字段：Go 端没设置，proto3 默认 false。1:1 跟随 — Java 端不 setFound。
 *     （user-rpc Java 端 chose self-init Found=true，是 user-rpc 自己的 D-003 决策，
 *      与 admin-rpc 的 Go 端行为不一致；本类遵循 admin-rpc 的 Go 行为）
 *
 * <p>没有 OTel span / metrics（D-016：Go 端 getuserlogic.go 也没埋点）。
 */
@Service
public class GetUserLogic {

    private final ServiceContext svcCtx;

    public GetUserLogic(ServiceContext svcCtx) {
        this.svcCtx = svcCtx;
    }

    public GetUserResp getUser(GetUserReq req) {
        User user;
        try {
            user = svcCtx.getDataModel().findOneUserByUid(req.getUid());
        } catch (NotFoundException e) {
            throw Status.NOT_FOUND
                    .withDescription("用户不存在")
                    .asRuntimeException();
        } catch (Exception e) {
            throw Status.INTERNAL
                    .withDescription("DB查询失败")
                    .asRuntimeException();
        }

        UserInfo.Builder b = UserInfo.newBuilder()
                .setUid(user.getUid())
                .setUsername(user.getUsername() == null ? "" : user.getUsername())
                .setEmail(user.getEmail() == null ? "" : user.getEmail())
                .setStatus(user.getStatus() == null ? 0L : user.getStatus()); // 1:1 跟随 Go：不 setScore（proto3 默认 0）
        Map<String, String> extra = user.getExtraInfo();
        if (extra != null && !extra.isEmpty()) {
            b.putAllExtraInfo(extra);
        } else {
            b.putAllExtraInfo(new HashMap<>());
        }

        return GetUserResp.newBuilder()
                .setUser(b.build())
                // Found 字段不设：Go 端默认 false，1:1 跟随
                .build();
    }
}