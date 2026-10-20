package com.rag.users.admin.api.service;

import com.rag.common.exception.BusinessException;
import com.rag.users.admin.api.client.AdminRpcClient;
import com.rag.users.admin.api.dto.UpdateUserReq;
import com.rag.users.admin.api.dto.UpdateUserResp;
import com.rag.users.admin.api.dto.UserInfo;
import com.rag.users.admin.api.metrics.AdminApiMetrics;
import com.rag.users.common.errmsg.ErrorCode;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;

/**
 * admin updateUser 用例 —— 5 状态机。
 *
 * 对应 Go: service/user/admin/api/internal/logic/admin/updateuserlogic.go 的 UpdateUserLogic。
 *
 * <p>L2 业务语义：
 * <ol>
 *   <li>claim uid → InvalidClaim → 1006</li>
 *   <li>调 adminRpc.updateUser(UpdateUserReq{Uid, Username?, Password?, Email?, ExtraInfo?, OperatorId})
 *      → gRPC INTERNAL → 5001 + updateDbError (DRIFT-NEW-005：Go 端 updateuser 用 db_error 而非 rpc_error)
 *      → ALREADY_EXISTS → 1001
 *      → NOT_FOUND → 1003
 *      → default → 1015</li>
 *   <li>成功 → updateSuccess + UpdateUserResp{Success=true, User=UserInfo}</li>
 * </ol>
 */
@Service
public class AdminUpdateUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUpdateUserService.class);

    private final AdminRpcClient adminRpcClient;
    private final AdminApiMetrics metrics;
    private final AdminApiSupport support;

    public AdminUpdateUserService(AdminRpcClient adminRpcClient, AdminApiMetrics metrics, AdminApiSupport support) {
        this.adminRpcClient = adminRpcClient;
        this.metrics = metrics;
        this.support = support;
    }

    public UpdateUserResp updateUser(UpdateUserReq req, HttpServletRequest httpRequest) {
        long operatorId;
        try {
            operatorId = IdentityResolver.claimedUID(httpRequest);
        } catch (IdentityResolver.ErrInvalidClaimException e) {
            log.error("updateUser ErrInvalidClaim", e);
            throw new BusinessException(ErrorCode.ERROR_TOKEN_RUNTIME.getCode(), ErrorCode.ERROR_TOKEN_RUNTIME.getMsg());
        }

        com.rag.users.admin.proto.UpdateUserReq.Builder builder = com.rag.users.admin.proto.UpdateUserReq.newBuilder()
                .setUid(parseUid(req.getUid()))
                .setOperatorId(operatorId);
        if (req.getUsername() != null) builder.setUsername(req.getUsername());
        if (req.getPassword() != null) builder.setPassword(req.getPassword());
        if (req.getEmail() != null) builder.setEmail(req.getEmail());
        if (req.getExtraInfo() != null) builder.putAllExtraInfo(new HashMap<>(req.getExtraInfo()));

        com.rag.users.admin.proto.UpdateUserResp rpcResp;
        try {
            rpcResp = adminRpcClient.adminRpc().updateUser(builder.build());
        } catch (StatusRuntimeException e) {
            Status.Code code = e.getStatus().getCode();
            metrics.getUpdateDbError().increment();
            log.error("updateUser RPC error, target_uid={}", req.getUid(), e);
            if (code == Status.Code.INTERNAL) {
                throw new BusinessException(ErrorCode.ERROR_SERVER_COMMON.getCode(), ErrorCode.ERROR_SERVER_COMMON.getMsg());
            }
            if (code == Status.Code.ALREADY_EXISTS) {
                throw new BusinessException(ErrorCode.ERROR_USER_EXIST.getCode(), ErrorCode.ERROR_USER_EXIST.getMsg());
            }
            if (code == Status.Code.NOT_FOUND) {
                throw new BusinessException(ErrorCode.ERROR_USER_NOT_EXIST.getCode(), ErrorCode.ERROR_USER_NOT_EXIST.getMsg());
            }
            throw new BusinessException(ErrorCode.CODE_SERVER_BUSY.getCode(), ErrorCode.CODE_SERVER_BUSY.getMsg());
        }

        metrics.getUpdateSuccess().increment();
        log.info("updateUser success, operator_id={} target_uid={}", operatorId, req.getUid());

        UpdateUserResp resp = new UpdateUserResp();
        // 与参考侧 UpdateUserLogic.java:129 1:1：显式 true。
        // 不能透传 rpcResp.getSuccess()——admin.proto 的 UpdateSelfResp/UpdateUserResp
        // 在 admin-rpc 未显式 set 时 proto3 默认值是 false，透传会把"成功"报成失败。
        resp.setSuccess(true);
        if (rpcResp.hasUser()) {
            UserInfo ui = support.fromProto(rpcResp.getUser());
            resp.setUser(ui);
        }
        return resp;
    }

    private static long parseUid(String uidStr) {
        return AdminApiSupport.parseUid(uidStr);
    }
}
