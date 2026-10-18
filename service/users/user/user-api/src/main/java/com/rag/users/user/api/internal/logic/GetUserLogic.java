package com.rag.users.user.api.internal.logic;

import com.rag.common.spring.LoggerFactoryBean;
import com.rag.users.common.errmsg.ErrorCode;
import com.rag.users.user.api.internal.identity.IdentityResolver;
import com.rag.users.user.api.internal.svc.ServiceContext;
import com.rag.users.user.api.internal.types.ApiTypes;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.opentelemetry.context.Context;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * 对应 Go 端 internal/logic/user/getuserlogic.go。
 *
 * <p>L1 外部契约 + L2 业务语义（八状态机，逐项冻结）：
 * <pre>
 *   1. IdentityResolver.ResolveUser(request, userRpc)
 *      → 失败时抛 5 个 sentinel exception：
 *         ErrInvalidClaim        → ErrorTokenRuntime    (1006)
 *         ErrUserNotFound        → ErrorUserNotExist    (1003)
 *         ErrUserInactive        → ErrorUserBanned      (1011)
 *         ErrStatusUnavailable   → CodeServerBusy        (1015)
 *         ErrIdentityMismatch    → ErrorServerCommon    (5001)
 *
 *   2. gRPC 异常（RPC 内部错误）：
 *         codes.NotFound          → ErrorUserNotExist    (1003)
 *         codes.Internal          → ErrorServerCommon    (5001)
 *         default                 → CodeServerBusy       (1015)
 *
 *   3. 成功
 *      → Success (200) + 返回 {user, found: true}
 *      → user 只填充 Uid/Username/Email/ExtraInfo（4 字段，与 Go 端 userinfo 字段选择一致）
 *      → Score 不填（GetUser RPC 回执 UserInfo.score 字段不在 api 这一层用）
 * </pre>
 *
 * <p>六个可观察面保留：
 * <ul>
 *   <li><b>Return</b>：成功时 {@code data.user.uid} 是字符串形式 UID + 4 个字段；失败时 8 种业务码</li>
 *   <li><b>Error</b>：8 个固定业务码（1006/1003/1011/1015/5001）</li>
 *   <li><b>Log</b>：所有 8 种失败分支都 logger.LogBusinessErr（不带用户名 / uid，与 Go 端一致）</li>
 *   <li><b>Metric</b>：Go 端 GetUserLogic <b>无 metrics 埋点</b> —— 1:1 不补</li>
 * </ul>
 */
@Service
public class GetUserLogic {

    private final ServiceContext svcCtx;
    private final LoggerFactoryBean logger;

    public GetUserLogic(ServiceContext svcCtx, LoggerFactoryBean logger) {
        this.svcCtx = svcCtx;
        this.logger = logger;
    }

    /**
     * 对应 Go {@code func (l *GetuserLogic) Getuser(req *types.GetUserReq) (resp *types.GetUserResp, code int)}。
     */
    public LogicResult<ApiTypes.GetUserResp> getUser(ApiTypes.GetUserReq req, HttpServletRequest httpRequest) {
        Context otelCtx = Context.current();

        com.rag.users.user.proto.UserInfo user;
        try {
            user = IdentityResolver.resolveUser(httpRequest, svcCtx.getUserRpc());
        } catch (IdentityResolver.ErrInvalidClaimException e) {
            logger.logBusinessErr(otelCtx, ErrorCode.ERROR.getCode(), e);
            return LogicResult.fail(ErrorCode.ERROR_TOKEN_RUNTIME);
        } catch (IdentityResolver.ErrUserNotFoundException e) {
            logger.logBusinessErr(otelCtx, ErrorCode.ERROR.getCode(), e);
            return LogicResult.fail(ErrorCode.ERROR_USER_NOT_EXIST);
        } catch (IdentityResolver.ErrUserInactiveException e) {
            logger.logBusinessErr(otelCtx, ErrorCode.ERROR.getCode(), e);
            return LogicResult.fail(ErrorCode.ERROR_USER_BANNED);
        } catch (IdentityResolver.ErrStatusUnavailableException e) {
            logger.logBusinessErr(otelCtx, ErrorCode.ERROR.getCode(), e);
            return LogicResult.fail(ErrorCode.CODE_SERVER_BUSY);
        } catch (IdentityResolver.ErrIdentityMismatchException e) {
            logger.logBusinessErr(otelCtx, ErrorCode.ERROR.getCode(), e);
            return LogicResult.fail(ErrorCode.ERROR_SERVER_COMMON);
        } catch (StatusRuntimeException e) {
            logger.logBusinessErr(otelCtx, ErrorCode.ERROR.getCode(), e);
            Status.Code code = e.getStatus().getCode();
            if (code == Status.Code.NOT_FOUND) {
                return LogicResult.fail(ErrorCode.ERROR_USER_NOT_EXIST);
            }
            if (code == Status.Code.INTERNAL) {
                return LogicResult.fail(ErrorCode.ERROR_SERVER_COMMON);
            }
            return LogicResult.fail(ErrorCode.CODE_SERVER_BUSY);
        }

        // 成功：只复制 Uid/Username/Email/ExtraInfo 4 个字段（与 Go 端 userinfo 选择一致）
        ApiTypes.UserInfo apiUser = new ApiTypes.UserInfo();
        apiUser.setUid(user.getUid());
        apiUser.setUsername(user.getUsername());
        if (user.getEmail() != null && !user.getEmail().isEmpty()) {
            apiUser.setEmail(user.getEmail());
        }
        if (user.getExtraInfoCount() > 0) {
            // proto3 map：count > 0 即代表有 entry
            apiUser.setExtraInfo(user.getExtraInfoMap());
        }

        ApiTypes.GetUserResp resp = new ApiTypes.GetUserResp();
        resp.setUser(apiUser);
        resp.setFound(true);
        return LogicResult.ok(resp);
    }
}