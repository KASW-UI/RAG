package com.rag.users.admin.rpc.internal.logic;

import com.rag.users.admin.proto.AdminInfo;
import com.rag.users.admin.proto.GetSelfReq;
import com.rag.users.admin.proto.GetSelfResp;
import com.rag.users.admin.rpc.internal.model.Admin;
import com.rag.users.admin.rpc.internal.model.AdminExceptions.NotFoundException;
import com.rag.users.admin.rpc.internal.svc.ServiceContext;
import io.grpc.Status;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 对应 Go 端 internal/logic/getselflogic.go：
 *   func (l *GetSelfLogic) GetSelf(in *pb.GetSelfReq) (*pb.GetSelfResp, error)
 *
 * <p>Works（W1~W3）：
 *   W1 FindOneAdminByUid(in.Uid) —— 查 admin 表
 *   W2 NotFoundException → StatusRuntimeException(NOT_FOUND, "用户不存在")
 *   W3 构造 GetSelfResp{Admin: AdminInfo{uid, username, email: stringValue(admin.Email), extra_info}}
 *
 * <p>关键决策：admin.Email 是 *string（nullable），Go 端用 stringValue(*string) 转 ""，Java 端 AdminHelpers.stringValue 实现相同语义。
 *
 * <p>中文错误描述：Go 端 mojibake（GBK→UTF-8 误转，源文件里是乱码字面值），Java 侧用正常 UTF-8 中文"用户不存在"，
 *     不影响 wire 协议（错误码仍是 codes.NotFound / codes.Internal）。
 *
 * <p>没有 OTel span / metrics（D-016：Go 端 getselflogic.go 也没埋点）。
 */
@Service
public class GetSelfLogic {

    private final ServiceContext svcCtx;

    public GetSelfLogic(ServiceContext svcCtx) {
        this.svcCtx = svcCtx;
    }

    public GetSelfResp getSelf(GetSelfReq req) {
        Admin admin;
        try {
            admin = svcCtx.getDataModel().findOneAdminByUid(req.getUid());
        } catch (NotFoundException e) {
            throw Status.NOT_FOUND
                    .withDescription("用户不存在")
                    .asRuntimeException();
        } catch (Exception e) {
            throw Status.INTERNAL
                    .withDescription("DB查询失败")
                    .asRuntimeException();
        }

        AdminInfo.Builder b = AdminInfo.newBuilder()
                .setUid(admin.getUid())
                .setUsername(admin.getUsername() == null ? "" : admin.getUsername())
                .setEmail(AdminHelpers.stringValue(admin.getEmail())); // *string → string 语义对齐
        Map<String, String> extra = admin.getExtraInfo();
        if (extra != null && !extra.isEmpty()) {
            b.putAllExtraInfo(extra);
        } else {
            b.putAllExtraInfo(new HashMap<>());
        }

        return GetSelfResp.newBuilder()
                .setAdmin(b.build())
                .build();
    }
}