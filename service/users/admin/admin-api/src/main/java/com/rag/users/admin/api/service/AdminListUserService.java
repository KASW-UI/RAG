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
        // DRIFT-007（已修）：分页默认值在这里套用，位置对应 Go 端的 struct tag
        //   form:"page,default=1" / form:"page_size,default=20"
        // tag 的 default 只在参数<b>缺失</b>时生效，因此用包装类型区分 null（缺失）
        // 与显式 0 —— 显式 0 必须原样透传给 rpc，让它按 Go 的语义算出负 OFFSET 报错，
        // 而不是在本层被悄悄兜底成第 1 页。
        com.rag.users.admin.proto.GetUserListReq rpcReq = com.rag.users.admin.proto.GetUserListReq.newBuilder()
                .setPage(req.pageOrDefault())
                .setPageSize(req.pageSizeOrDefault())
                // DRIFT-006（已修）：keyword 原样透传，<b>不</b> trim。
                // Go 端 model/admin.go:152 是 len(keyword) > 0，不去空白。
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
        // A-01（已修）：空结果时置 null，与 Go 的 nil slice 对齐。
        // Go: getuserlistlogic.go:51-62 用 `var list []types.UserInfo`，rpcResp.List 为空时
        // 循环不执行、list 保持 nil；types.GetUserListResp.List 的 tag 是 `json:"list"`
        // （<b>无</b> omitempty）⇒ 序列化成 "list": null。
        // 本实现此前无条件 setList(new ArrayList<>()) ⇒ 序列化成 "list": []。
        List<UserInfo> list = null;
        if (!rpcResp.getListList().isEmpty()) {
            list = new ArrayList<>();
            for (com.rag.users.admin.proto.UserInfo item : rpcResp.getListList()) {
                list.add(support.fromProto(item));
            }
        }
        resp.setList(list);
        return resp;
    }
}
