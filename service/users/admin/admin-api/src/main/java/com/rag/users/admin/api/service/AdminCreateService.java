package com.rag.users.admin.api.service;

import com.rag.common.exception.BusinessException;
import com.rag.users.admin.api.client.AdminRpcClient;
import com.rag.users.admin.api.dto.CreateAdminReq;
import com.rag.users.admin.api.dto.CreateAdminResp;
import com.rag.users.common.errmsg.ErrorCode;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;

/**
 * admin createAdmin 用例 —— 3 状态机。
 *
 * 对应 Go: service/user/admin/api/internal/logic/admin/createlogic.go:34 的 CreateLogic.Create。
 *
 * <p>L2 业务语义：
 * <ol>
 *   <li>调 adminRpc.createAdmin(CreateAdminReq{Username, Password, Email?, ExtraInfo?, InviteCode})
 *      → gRPC PERMISSION_DENIED → ErrorAdminInviteCodeWrong (1016)（invite code 错）
 *      → gRPC ALREADY_EXISTS → ErrorUserExist (1001)（username 已存在）
 *      → gRPC INTERNAL → ErrorServerCommon (5001)
 *      → default → CodeServerBusy (1015)</li>
 *   <li>成功 → CreateAdminResp{Uid}</li>
 * </ol>
 */
@Service
public class AdminCreateService {

    private static final Logger log = LoggerFactory.getLogger(AdminCreateService.class);

    private final AdminRpcClient adminRpcClient;

    public AdminCreateService(AdminRpcClient adminRpcClient) {
        this.adminRpcClient = adminRpcClient;
    }

    public CreateAdminResp createAdmin(CreateAdminReq req, HttpServletRequest httpRequest) {
        com.rag.users.admin.proto.CreateAdminReq rpcReq = com.rag.users.admin.proto.CreateAdminReq.newBuilder()
                .setUsername(req.getUsername() == null ? "" : req.getUsername())
                .setPassword(req.getPassword() == null ? "" : req.getPassword())
                .setEmail(req.getEmail() == null ? "" : req.getEmail())
                .setInviteCode(req.getInviteCode() == null ? "" : req.getInviteCode())
                .putAllExtraInfo(req.getExtraInfo() == null ? new HashMap<>() : new HashMap<>(req.getExtraInfo()))
                .build();

        long uid;
        try {
            com.rag.users.admin.proto.CreateAdminResp rpcResp = adminRpcClient.adminRpc().createAdmin(rpcReq);
            uid = rpcResp.getUid();
        } catch (StatusRuntimeException e) {
            Status.Code code = e.getStatus().getCode();
            log.error("admin createAdmin RPC error, username={}", req.getUsername(), e);
            if (code == Status.Code.PERMISSION_DENIED) {
                throw new BusinessException(ErrorCode.ERROR_ADMIN_INVITE_CODE_WRONG.getCode(), ErrorCode.ERROR_ADMIN_INVITE_CODE_WRONG.getMsg());
            }
            if (code == Status.Code.ALREADY_EXISTS) {
                throw new BusinessException(ErrorCode.ERROR_USER_EXIST.getCode(), ErrorCode.ERROR_USER_EXIST.getMsg());
            }
            if (code == Status.Code.INTERNAL) {
                throw new BusinessException(ErrorCode.ERROR_SERVER_COMMON.getCode(), ErrorCode.ERROR_SERVER_COMMON.getMsg());
            }
            throw new BusinessException(ErrorCode.CODE_SERVER_BUSY.getCode(), ErrorCode.CODE_SERVER_BUSY.getMsg());
        }

        CreateAdminResp resp = new CreateAdminResp();
        resp.setUid(uid);
        return resp;
    }
}
