package com.rag.users.admin.api.service;

import com.rag.common.exception.BusinessException;
import com.rag.users.admin.api.client.AdminRpcClient;
import com.rag.users.admin.api.dto.GetSelfReq;
import com.rag.users.admin.api.dto.GetSelfResp;
import com.rag.users.common.errmsg.ErrorCode;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * admin getSelf 用例 —— 2 状态机（GetSelf admin proto 无 status 字段，所以没有 inactive 状态）。
 *
 * 对应 Go: service/user/admin/api/internal/logic/admin/getselflogic.go 的 GetSelfLogic。
 *
 * <p>L2 业务语义：
 * <ol>
 *   <li>IdentityResolver.resolveAdmin(request, adminRpc) → 5 个 sentinel exception → 5 个业务码</li>
 *   <li>成功 → GetSelfResp{Admin=AdminInfo}</li>
 * </ol>
 */
@Service
public class AdminGetSelfService {

    private static final Logger log = LoggerFactory.getLogger(AdminGetSelfService.class);

    private final AdminRpcClient adminRpcClient;
    private final AdminApiSupport support;

    public AdminGetSelfService(AdminRpcClient adminRpcClient, AdminApiSupport support) {
        this.adminRpcClient = adminRpcClient;
        this.support = support;
    }

    public GetSelfResp getSelf(GetSelfReq req, HttpServletRequest httpRequest) {
        com.rag.users.admin.proto.AdminInfo protoAdmin;
        try {
            protoAdmin = IdentityResolver.resolveAdmin(httpRequest, adminRpcClient.adminRpc());
        } catch (IdentityResolver.ErrInvalidClaimException e) {
            log.error("getSelf ErrInvalidClaim", e);
            throw new BusinessException(ErrorCode.ERROR_TOKEN_RUNTIME.getCode(), ErrorCode.ERROR_TOKEN_RUNTIME.getMsg());
        } catch (IdentityResolver.ErrAdminNotFoundException e) {
            log.error("getSelf ErrAdminNotFound", e);
            throw new BusinessException(ErrorCode.ERROR_USER_NOT_EXIST.getCode(), ErrorCode.ERROR_USER_NOT_EXIST.getMsg());
        } catch (IdentityResolver.ErrIdentityMismatchException e) {
            log.error("getSelf ErrIdentityMismatch", e);
            throw new BusinessException(ErrorCode.ERROR_SERVER_COMMON.getCode(), ErrorCode.ERROR_SERVER_COMMON.getMsg());
        } catch (StatusRuntimeException e) {
            log.error("getSelf RPC error", e);
            throw e;
        }

        GetSelfResp resp = new GetSelfResp();
        resp.setAdmin(support.fromProto(protoAdmin));
        return resp;
    }
}
