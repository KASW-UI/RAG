package com.rag.users.admin.api.service;

import com.rag.common.exception.BusinessException;
import com.rag.users.admin.api.client.AdminRpcClient;
import com.rag.users.admin.api.dto.UpdateSelfReq;
import com.rag.users.admin.api.dto.UpdateSelfResp;
import com.rag.users.common.errmsg.ErrorCode;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;

/**
 * admin updateSelf 用例 —— 5 状态机。
 *
 * 对应 Go: service/user/admin/api/internal/logic/admin/updateselflogic.go 的 UpdateSelfLogic。
 *
 * <p>L2 业务语义：
 * <ol>
 *   <li>claim uid → InvalidClaim → 1006</li>
 *   <li>调 adminRpc.updateSelf(UpdateSelfReq{Uid, Username?, Password?, Email?, ExtraInfo?})
 *      → gRPC INTERNAL → 5001 / ALREADY_EXISTS → 1001 / default → 1015</li>
 *   <li>成功 → UpdateSelfResp{Success=true, Admin=AdminInfo}</li>
 * </ol>
 *
 * <p>email nullable：admin 表的 email 可空，Go 端 optionalString 把 trim 后的空 → null（写 SQL NULL）。
 * Java 端保持同款（DTO 传 "" 或 trim 后的非空值；admin-rpc 端 AdminDomainService.updateSelf 用 optionalString 处理）。
 */
@Service
public class AdminUpdateSelfService {

    private static final Logger log = LoggerFactory.getLogger(AdminUpdateSelfService.class);

    private final AdminRpcClient adminRpcClient;
    private final AdminApiSupport support;

    public AdminUpdateSelfService(AdminRpcClient adminRpcClient, AdminApiSupport support) {
        this.adminRpcClient = adminRpcClient;
        this.support = support;
    }

    public UpdateSelfResp updateSelf(UpdateSelfReq req, HttpServletRequest httpRequest) {
        long uid;
        try {
            uid = IdentityResolver.claimedUID(httpRequest);
        } catch (IdentityResolver.ErrInvalidClaimException e) {
            log.error("updateSelf ErrInvalidClaim", e);
            throw new BusinessException(ErrorCode.ERROR_TOKEN_RUNTIME.getCode(), ErrorCode.ERROR_TOKEN_RUNTIME.getMsg());
        }

        com.rag.users.admin.proto.UpdateSelfReq.Builder builder = com.rag.users.admin.proto.UpdateSelfReq.newBuilder()
                .setUid(uid);
        if (req.getUsername() != null) builder.setUsername(req.getUsername());
        if (req.getPassword() != null) builder.setPassword(req.getPassword());
        if (req.getEmail() != null) builder.setEmail(req.getEmail());
        if (req.getExtraInfo() != null) builder.putAllExtraInfo(new HashMap<>(req.getExtraInfo()));

        com.rag.users.admin.proto.UpdateSelfResp rpcResp;
        try {
            rpcResp = adminRpcClient.adminRpc().updateSelf(builder.build());
        } catch (StatusRuntimeException e) {
            Status.Code code = e.getStatus().getCode();
            log.error("updateSelf RPC error", e);
            if (code == Status.Code.INTERNAL) {
                throw new BusinessException(ErrorCode.ERROR_SERVER_COMMON.getCode(), ErrorCode.ERROR_SERVER_COMMON.getMsg());
            }
            if (code == Status.Code.ALREADY_EXISTS) {
                throw new BusinessException(ErrorCode.ERROR_USER_EXIST.getCode(), ErrorCode.ERROR_USER_EXIST.getMsg());
            }
            throw new BusinessException(ErrorCode.CODE_SERVER_BUSY.getCode(), ErrorCode.CODE_SERVER_BUSY.getMsg());
        }

        UpdateSelfResp resp = new UpdateSelfResp();
        // 与参考侧 UpdateSelfLogic.java:134 1:1：必须显式 true，
        // 不透传 rpcResp.getSuccess()（proto3 默认 false 会把成功报成失败）。
        resp.setSuccess(true);
        resp.setAdmin(support.fromProto(rpcResp.getAdmin()));
        return resp;
    }
}
