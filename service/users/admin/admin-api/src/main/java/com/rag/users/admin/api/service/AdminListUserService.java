package com.rag.users.admin.api.service;

import com.rag.common.exception.BusinessException;
import com.rag.users.admin.api.client.AdminRpcClient;
import com.rag.users.admin.api.dto.GetUserListReq;
import com.rag.users.admin.api.dto.GetUserListResp;
import com.rag.users.admin.api.dto.UserInfo;
import com.rag.users.common.errmsg.ErrorCode;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * admin getUserList 用例 —— 2 状态机。
 *
 * 对应 Go: service/user/admin/api/internal/logic/admin/getuserlistlogic.go 的 GetUserListLogic。
 *
 * <p>keyword 非空时按 username/email LIKE 查询，否则全量；admin-rpc 端 AdminDomainService.getUserList 实现。
 */
@Service
public class AdminListUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminListUserService.class);

    private final AdminRpcClient adminRpcClient;
    private final AdminApiSupport support;

    public AdminListUserService(AdminRpcClient adminRpcClient, AdminApiSupport support) {
        this.adminRpcClient = adminRpcClient;
        this.support = support;
    }

    public GetUserListResp getUserList(GetUserListReq req, HttpServletRequest httpRequest) {
        com.rag.users.admin.proto.GetUserListReq rpcReq = com.rag.users.admin.proto.GetUserListReq.newBuilder()
                .setPage(req.getPage())
                .setPageSize(req.getPageSize())
                .setKeyword(req.getKeyword() == null ? "" : req.getKeyword())
                .build();

        com.rag.users.admin.proto.GetUserListResp rpcResp;
        try {
            rpcResp = adminRpcClient.adminRpc().getUserList(rpcReq);
        } catch (StatusRuntimeException e) {
            Status.Code code = e.getStatus().getCode();
            log.error("getUserList RPC error", e);
            if (code == Status.Code.INTERNAL) {
                throw new BusinessException(ErrorCode.ERROR_SERVER_COMMON.getCode(), ErrorCode.ERROR_SERVER_COMMON.getMsg());
            }
            throw new BusinessException(ErrorCode.CODE_SERVER_BUSY.getCode(), ErrorCode.CODE_SERVER_BUSY.getMsg());
        }

        GetUserListResp resp = new GetUserListResp();
        resp.setTotal(rpcResp.getTotal());
        List<UserInfo> list = new ArrayList<>();
        for (com.rag.users.admin.proto.UserInfo item : rpcResp.getListList()) {
            list.add(support.fromProto(item));
        }
        resp.setList(list);
        return resp;
    }
}
