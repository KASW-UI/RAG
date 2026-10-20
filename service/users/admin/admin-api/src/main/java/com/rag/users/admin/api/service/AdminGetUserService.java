package com.rag.users.admin.api.service;

import com.rag.common.exception.BusinessException;
import com.rag.users.admin.api.client.AdminRpcClient;
import com.rag.users.admin.api.dto.GetUserReq;
import com.rag.users.admin.api.dto.GetUserResp;
import com.rag.users.admin.api.dto.UserInfo;
import com.rag.users.common.errmsg.ErrorCode;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * admin getUser 用例 —— 3 状态机（Found=false 时 user=null；admin-rpc 端不设 Found 默认 false，1:1 跟随 Go）。
 *
 * 对应 Go: service/user/admin/api/internal/logic/admin/getuserlogic.go 的 GetUserLogic。
 */
@Service
public class AdminGetUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminGetUserService.class);

    private final AdminRpcClient adminRpcClient;
    private final AdminApiSupport support;

    public AdminGetUserService(AdminRpcClient adminRpcClient, AdminApiSupport support) {
        this.adminRpcClient = adminRpcClient;
        this.support = support;
    }

    public GetUserResp getUser(GetUserReq req, HttpServletRequest httpRequest) {
        long uid = parseUid(req.getUid());

        com.rag.users.admin.proto.GetUserReq rpcReq = com.rag.users.admin.proto.GetUserReq.newBuilder()
                .setUid(uid)
                .build();

        com.rag.users.admin.proto.GetUserResp rpcResp;
        try {
            rpcResp = adminRpcClient.adminRpc().getUser(rpcReq);
        } catch (StatusRuntimeException e) {
            Status.Code code = e.getStatus().getCode();
            log.error("getUser RPC error, uid={}", uid, e);
            if (code == Status.Code.INTERNAL) {
                throw new BusinessException(ErrorCode.ERROR_SERVER_COMMON.getCode(), ErrorCode.ERROR_SERVER_COMMON.getMsg());
            }
            if (code == Status.Code.NOT_FOUND) {
                throw new BusinessException(ErrorCode.ERROR_USER_NOT_EXIST.getCode(), ErrorCode.ERROR_USER_NOT_EXIST.getMsg());
            }
            throw new BusinessException(ErrorCode.CODE_SERVER_BUSY.getCode(), ErrorCode.CODE_SERVER_BUSY.getMsg());
        }

        // DRIFT-002（已修）：found 显式置 true。
        //
        // Go 出处：api/internal/logic/admin/getuserlogic.go:51-60 —— api 层自己构造
        //   types.GetUserResp{ User: ..., Found: true }
        // 注意 Go 的「true」写在 **api 层**，而不是 rpc 层：Go 的 rpc 层
        // getuserlogic.go 只 return &pb.GetUserResp{User: ...}，确实没设 pb 的 found，
        // proto3 默认 false。本实现此前据此把 rpcResp.getFound() 透传下来 ⇒ 恒 false，
        // 注释还写着「1:1 跟随 Go 端」——**参照的是错误的层**。
        //
        // 可观察影响：客户端若按 found 判定"是否找到"，成功查询时拿到 false 会走反分支。
        GetUserResp resp = new GetUserResp();
        resp.setFound(true);
        if (rpcResp.hasUser()) {
            resp.setUser(support.fromProto(rpcResp.getUser()));
        }
        return resp;
    }

    private static long parseUid(String uidStr) {
        return AdminApiSupport.parseUid(uidStr);
    }
}
