package com.rag.users.admin.api.internal.logic;

import com.rag.common.spring.LoggerFactoryBean;
import com.rag.users.admin.api.internal.svc.ServiceContext;
import com.rag.users.admin.api.internal.types.ApiTypes;
import com.rag.users.admin.proto.LogoutReq;
import com.rag.users.common.errmsg.ErrorCode;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.opentelemetry.context.Context;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * 对应 Go 端 internal/logic/admin/logoutlogic.go。
 *
 * <p>L1 外部契约 + L2 业务语义（四状态机，逐项冻结，对应 Go logoutlogic.go 第 35-58 行）：
 * <pre>
 *   1. 从 request attribute "jwt_token" 取 token 字符串
 *      （由 CheckBlacklistFilter 在 JWT 验签通过后写入，对应 Go 端 ctx.Value("jwt_token")）
 *      → 取不到 → ErrorTokenNotExist (1004) + 日志 "Token不存在"
 *      （对应 Go: admin/api/internal/logic/admin/logoutlogic.go:38-39 用 errmsg.ErrorTokenNotExist=1004）
 *
 *   2. 调 adminRpc.Logout(LogoutReq{Token})
 *      → 失败：
 *         gRPC codes.Internal → ErrorServerCommon (5001)
 *         其它（默认）         → CodeServerBusy   (1015)
 *
 *   3. 成功
 *      → Success (200) + 返回 {success: true}
 * </pre>
 *
 * <p><b>L1 wire 漂移说明</b>：admin-rpc 端 LogoutLogic 返回
 * {@code LogoutResp.newBuilder().build()}（proto3 default，Success = false）；
 * Go 端 admin-api logoutlogic.go 显式 {@code LogoutResp{Success: true}} —— 即 admin-api
 * 显式覆盖上游默认值。Java 侧必须显式 {@code resp.setSuccess(true)}，<b>不能</b>透传
 * {@code rpcResp.getSuccess()}（透传会拿到 false）。
 *
 * <p>六个可观察面保留：
 * <ul>
 *   <li><b>Return</b>：成功时 {@code data.success} = true；失败时 {@code code} 字段是 3 个 ErrorCode 之一</li>
 *   <li><b>Error</b>：3 个固定业务码（1004/5001/1015/200）</li>
 *   <li><b>Log</b>：token 缺失时 logger.LogBusinessErr(ErrorTokenNotExist=1004, "Token不存在")；
 *       rpc_error 时 logger.LogBusinessErr</li>
 *   <li><b>Trace</b>：从 {@link Context#current()} 取 OTel ctx</li>
 *   <li><b>Metric</b>：Go 端 logoutlogic.go <b>无 metrics 埋点</b>（与 login 不同 —— login 有 3 个 result label，
 *       logout 0 个；DRIFT-016 同款决策 1:1 保留）</li>
 *   <li><b>Lifecycle</b>：仅依赖 Spring 注入的 {@link ServiceContext}，无可变状态；可重入</li>
 * </ul>
 */
@Service
public class LogoutLogic {

    private final ServiceContext svcCtx;
    private final LoggerFactoryBean logger;

    public LogoutLogic(ServiceContext svcCtx, LoggerFactoryBean logger) {
        this.svcCtx = svcCtx;
        this.logger = logger;
    }

    /**
     * 对应 Go {@code func (l *LogoutLogic) Logout(req *types.LogoutReq) (resp *types.LogoutResp, code int)}。
     */
    public LogicResult<ApiTypes.LogoutResp> logout(ApiTypes.LogoutReq req, HttpServletRequest httpRequest) {
        Context otelCtx = Context.current();

        // 对应 Go: l.ctx.Value("jwt_token")
        Object tokenAttr = httpRequest.getAttribute("jwt_token");
        if (tokenAttr == null) {
            // 对应 Go: logger.LogBusinessErr(l.ctx, errmsg.ErrorTokenNotExist, fmt.Errorf("Token不存在"))
            logger.logBusinessErr(otelCtx, ErrorCode.ERROR_TOKEN_NOT_EXIST.getCode(),
                    new RuntimeException("Token不存在"));
            return LogicResult.fail(ErrorCode.ERROR_TOKEN_NOT_EXIST);
        }
        String token = (String) tokenAttr;

        LogoutReq rpcReq = LogoutReq.newBuilder().setToken(token).build();
        try {
            svcCtx.getAdminRpc().logout(rpcReq);
        } catch (StatusRuntimeException e) {
            // 对应 Go: logger.LogBusinessErr(l.ctx, errmsg.Error, err)
            logger.logBusinessErr(otelCtx, ErrorCode.ERROR.getCode(), e);
            // 对应 Go: switch st.Code() —— 只有 Internal 一种业务码映射，其它一律 CodeServerBusy
            Status.Code code = e.getStatus().getCode();
            if (code == Status.Code.INTERNAL) {
                return LogicResult.fail(ErrorCode.ERROR_SERVER_COMMON);          // 5001
            }
            return LogicResult.fail(ErrorCode.CODE_SERVER_BUSY);                  // 1015
        }

        // 对应 Go: return &types.LogoutResp{Success: true}, errmsg.Success
        // 注意：必须显式 setSuccess(true)，不能透传 rpcResp.getSuccess()（proto3 default false）
        ApiTypes.LogoutResp resp = new ApiTypes.LogoutResp();
        resp.setSuccess(true);
        return LogicResult.ok(resp);
    }
}