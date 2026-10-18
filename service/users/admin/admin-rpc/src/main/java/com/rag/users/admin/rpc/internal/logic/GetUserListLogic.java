package com.rag.users.admin.rpc.internal.logic;

import com.rag.users.admin.proto.GetUserListReq;
import com.rag.users.admin.proto.GetUserListResp;
import com.rag.users.admin.proto.UserInfo;
import com.rag.users.admin.rpc.internal.model.DataModel;
import com.rag.users.admin.rpc.internal.model.User;
import com.rag.users.admin.rpc.internal.svc.ServiceContext;
import io.grpc.Status;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 对应 Go 端 internal/logic/getuserlistlogic.go：
 *   func (l *GetUserListLogic) GetUserList(in *pb.GetUserListReq) (*pb.GetUserListResp, error)
 *
 * <p>Works（W1~W3）：
 *   W1 DataModel.findUserListByKeyword(page, pageSize, keyword) → (users, total)
 *   W2 Exception → StatusRuntimeException(INTERNAL, "DB查询失败")
 *   W3 逐行 User → pb.UserInfo 转换；构造 GetUserListResp{List, Total}
 *
 * <p>关键字模糊查询：Go 端 keyword 非空时按 username/email LIKE 查询，否则全量。
 *     Java 侧由 DataModel.findUserListByKeyword 处理（含分页 offset + limit）。
 *
 * <p>UserInfo 字段：与 GetUserLogic 同样的"1:1 跟随 Go 端不设 score"原则。
 *
 * <p>没有 OTel span / metrics（D-016：Go 端 getuserlistlogic.go 也没埋点）。
 */
@Service
public class GetUserListLogic {

    private final ServiceContext svcCtx;

    public GetUserListLogic(ServiceContext svcCtx) {
        this.svcCtx = svcCtx;
    }

    public GetUserListResp getUserList(GetUserListReq req) {
        DataModel.UserPage page;
        try {
            page = svcCtx.getDataModel().findUserListByKeyword(
                    (int) req.getPage(),
                    (int) req.getPageSize(),
                    req.getKeyword());
        } catch (Exception e) {
            throw Status.INTERNAL
                    .withDescription("DB查询失败")
                    .asRuntimeException();
        }

        List<UserInfo> list = new ArrayList<>();
        for (User user : page.users()) {
            UserInfo.Builder b = UserInfo.newBuilder()
                    .setUid(user.getUid())
                    .setUsername(user.getUsername() == null ? "" : user.getUsername())
                    .setEmail(user.getEmail() == null ? "" : user.getEmail())
                    .setStatus(user.getStatus() == null ? 0L : user.getStatus());
            Map<String, String> extra = user.getExtraInfo();
            if (extra != null && !extra.isEmpty()) {
                b.putAllExtraInfo(extra);
            } else {
                b.putAllExtraInfo(new HashMap<>());
            }
            list.add(b.build());
        }

        return GetUserListResp.newBuilder()
                .addAllList(list)
                .setTotal(page.total())
                .build();
    }
}