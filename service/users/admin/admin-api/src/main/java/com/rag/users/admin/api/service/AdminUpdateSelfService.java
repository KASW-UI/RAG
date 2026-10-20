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
            // Go 出处：api/internal/logic/admin/updatelogic.go:56-67 的完整 switch
            //   AlreadyExists → 1001 / NotFound → 1003 / Internal → 5003 / default → 1015
            //
            // DRIFT-003（已修）：本 switch 曾缺 NOT_FOUND case，落到 default 被报成
            // 1015「服务繁忙」——把"token 主体对应的 admin 已不存在"报成服务端故障。
            // 这与 user-api DRIFT-003 是同一类：无出处的 idiomatization，写法上完全自然。
            if (code == Status.Code.ALREADY_EXISTS) {
                throw new BusinessException(ErrorCode.ERROR_USER_EXIST.getCode(), ErrorCode.ERROR_USER_EXIST.getMsg());
            }
            if (code == Status.Code.NOT_FOUND) {
                throw new BusinessException(ErrorCode.ERROR_USER_NOT_EXIST.getCode(), ErrorCode.ERROR_USER_NOT_EXIST.getMsg());
            }
            // DRIFT-004（已修）：Go 的 update 端点 Internal → ErrorDbSelect(5003)「查询数据库失败」，
            // 与其余 9 个端点的 5001「系统内部错误」不同。这处不一致是 Go 侧的历史遗留，
            // 但真源已冻结，RAG 必须逐字跟随，不能"顺手统一成 5001"。
            if (code == Status.Code.INTERNAL) {
                throw new BusinessException(ErrorCode.ERROR_DB_SELECT.getCode(), ErrorCode.ERROR_DB_SELECT.getMsg());
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
