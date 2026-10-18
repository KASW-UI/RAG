package com.rag.users.admin.api.internal.logic;

import com.rag.common.spring.LoggerFactoryBean;
import com.rag.users.admin.api.internal.identity.IdentityResolver;
import com.rag.users.admin.api.internal.svc.ServiceContext;
import com.rag.users.admin.api.internal.types.ApiTypes;
import com.rag.users.admin.proto.AdminInfo;
import com.rag.users.common.errmsg.ErrorCode;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.opentelemetry.context.Context;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * 对应 Go 端 internal/logic/admin/getselflogic.go。
 *
 * <p>L1 外部契约 + L2 业务语义（七状态机，逐项冻结，对应 Go getselflogic.go 第 35-69 行）：
 * <pre>
 *   1. request attribute "userId" 取 UID（由 JwtAuthenticationFilter 写入，对应 Go ctx.Value("userId")）
 *      → 类型不是 Long 或 <= 0 → ErrorTokenRuntime (1006) + log "ctx userId is not json.Number" / "parse uid failed"
 *
 *   2. 调 adminRpc.GetSelf(GetSelfReq{Uid})
 *      → 失败：
 *         gRPC codes.NotFound   → ErrorUserNotExist (1003)   // 对应 Go 第 54 行
 *         gRPC codes.Internal   → ErrorServerCommon (5001)   // 对应 Go 第 56 行
 *         其它（默认）           → CodeServerBusy   (1015)    // 对应 Go 第 58 行
 *
 *   3. IdentityResolver 抛 ErrAdminNotFound（admin-rpc 没返 admin）→ ErrorUserNotExist (1003)
 *      IdentityResolver 抛 ErrIdentityMismatch（jwt uid 与 admin-rpc 返回 uid 不一致）→ ErrorServerCommon (5001)
 *
 *   4. 成功
 *      → Success (200) + 返回 {admin: {uid, username, email, extra_info}}
 *      → email="" 时跳过（对应 Go json:"email,omitempty"）；extra_info 空时不输出（NON_NULL）
 * </pre>
 *
 * <p>六个可观察面保留：
 * <ul>
 *   <li><b>Return</b>：成功时 {@code data.admin} 是 {@link ApiTypes.AdminInfo}（4 字段，与 Go 端第 63-68 行的
 *       AdminInfo{Uid, Username, Email, Extrainfo} 1:1）；失败时 5 个业务码之一</li>
 *   <li><b>Error</b>：5 个固定业务码（1006/1003/5001/1015/200）</li>
 *   <li><b>Log</b>：所有 5 种失败分支都 logger.LogBusinessErr；success 时不打 log（与 Go 一致）</li>
 *   <li><b>Trace</b>：从 {@link Context#current()} 取 OTel ctx</li>
 *   <li><b>Metric</b>：Go 端 getselflogic.go <b>无 metrics 埋点</b> —— 1:1 不补（DRIFT-016 决策）</li>
 *   <li><b>Lifecycle</b>：仅依赖 Spring 注入的 {@link ServiceContext}，无可变状态；可重入</li>
 * </ul>
 */
@Service
public class GetSelfLogic {

    private final ServiceContext svcCtx;
    private final LoggerFactoryBean logger;

    public GetSelfLogic(ServiceContext svcCtx, LoggerFactoryBean logger) {
        this.svcCtx = svcCtx;
        this.logger = logger;
    }

    /**
     * 对应 Go {@code func (l *GetselfLogic) Getself(req *types.GetSelfReq) (resp *types.GetSelfResp, code int)}。
     */
    public LogicResult<ApiTypes.GetSelfResp> getSelf(ApiTypes.GetSelfReq req, HttpServletRequest httpRequest) {
        Context otelCtx = Context.current();

        AdminInfo admin;
        try {
            admin = IdentityResolver.resolveAdmin(httpRequest, svcCtx.getAdminRpc());
        } catch (IdentityResolver.ErrInvalidClaimException e) {
            // 对应 Go: logger.LogBusinessErr(errmsg.ErrorTokenRuntime, "ctx userId is not json.Number" / "parse uid failed")
            logger.logBusinessErr(otelCtx, ErrorCode.ERROR_TOKEN_RUNTIME.getCode(), e);
            return LogicResult.fail(ErrorCode.ERROR_TOKEN_RUNTIME);   // 1006
        } catch (IdentityResolver.ErrAdminNotFoundException e) {
            // 对应 Go: codes.NotFound → ErrorUserNotExist
            logger.logBusinessErr(otelCtx, ErrorCode.ERROR.getCode(), e);
            return LogicResult.fail(ErrorCode.ERROR_USER_NOT_EXIST);   // 1003
        } catch (IdentityResolver.ErrIdentityMismatchException e) {
            // 对应 user-api IdentityResolver 映射 (5001)；Go 端 admin 没此分支（无 IdentityResolver 概念）
            logger.logBusinessErr(otelCtx, ErrorCode.ERROR.getCode(), e);
            return LogicResult.fail(ErrorCode.ERROR_SERVER_COMMON);   // 5001
        } catch (StatusRuntimeException e) {
            // 对应 Go: switch st.Code() 第 53-60 行
            logger.logBusinessErr(otelCtx, ErrorCode.ERROR.getCode(), e);
            Status.Code code = e.getStatus().getCode();
            switch (code) {
                case NOT_FOUND:
                    return LogicResult.fail(ErrorCode.ERROR_USER_NOT_EXIST);   // 1003
                case INTERNAL:
                    return LogicResult.fail(ErrorCode.ERROR_SERVER_COMMON);   // 5001
                default:
                    return LogicResult.fail(ErrorCode.CODE_SERVER_BUSY);       // 1015
            }
        }

        // 成功：组装 envelope（对应 Go 第 62-69 行）
        // 复制 4 个字段：uid / username / email / extra_info
        ApiTypes.AdminInfo apiAdmin = new ApiTypes.AdminInfo();
        apiAdmin.setUid(admin.getUid());
        apiAdmin.setUsername(admin.getUsername());
        // admin-rpc GetSelfLogic 用 AdminHelpers.stringValue(*string) 把 null email 转 ""，
        // 所以 admin.getEmail() 永远是非 null String。Java 端把 "" 转 null 让 @JsonInclude(NON_NULL) 跳过，
        // 效果对齐 Go 端 json:"email,omitempty"。
        String email = admin.getEmail();
        if (email != null && !email.isEmpty()) {
            apiAdmin.setEmail(email);
        }
        // proto3 map：getExtraInfoCount() > 0 代表有 entry
        if (admin.getExtraInfoCount() > 0) {
            apiAdmin.setExtraInfo(admin.getExtraInfoMap());
        }

        ApiTypes.GetSelfResp resp = new ApiTypes.GetSelfResp();
        resp.setAdmin(apiAdmin);
        return LogicResult.ok(resp);
    }
}