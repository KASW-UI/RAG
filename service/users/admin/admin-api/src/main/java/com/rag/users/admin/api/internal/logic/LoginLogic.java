package com.rag.users.admin.api.internal.logic;

import com.rag.common.spring.LoggerFactoryBean;
import com.rag.users.admin.api.internal.metrics.Metrics;
import com.rag.users.admin.api.internal.svc.ServiceContext;
import com.rag.users.admin.api.internal.types.ApiTypes;
import com.rag.users.admin.proto.LoginReq;
import com.rag.users.admin.proto.LoginResp;
import com.rag.users.common.errmsg.ErrorCode;
import com.rag.users.common.jwt.JwtUtil;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.opentelemetry.context.Context;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * 对应 Go 端 internal/logic/admin/loginlogic.go。
 *
 * <p>L4 实现机制差异：Go 用闭包构造函数（{@code NewLoginLogic(ctx, svcCtx) *LoginLogic}），
 * Java 侧用 {@code @Service} + 构造器注入 {@link ServiceContext} 和 {@link Metrics}。
 * 每个 request handler 调用 {@link #login(ApiTypes.LoginReq, HttpServletRequest)} 时，
 * {@code request} 参数代替 Go 端的 ctx 参数。
 *
 * <p>L1 外部契约 + L2 业务语义（六状态机，逐项冻结，对应 Go loginlogic.go 第 37-72 行）：
 * <pre>
 *   1. 调 adminRpc.Login(LoginReq{Username, Password})
 *      → 失败：
 *         gRPC codes.NotFound         → ErrorUserNotExist  (1003) + metrics "rpc_error"
 *         gRPC codes.Internal         → ErrorServerCommon  (5001) + metrics "rpc_error"
 *         gRPC codes.Unauthenticated  → ErrorLoginWrong    (1002) + metrics "rpc_error"
 *         其它（默认）                 → CodeServerBusy     (1015) + metrics "rpc_error"
 *      → 成功：继续
 *
 *   2. jwt.GetToken(...) 失败
 *      → ErrorServerCommon (5001) + metrics "internal_error"
 *
 *   3. 成功
 *      → Success (200) + metrics "success"
 *      → 返回 {token: "..."}
 * </pre>
 *
 * <p><b>关键差异（与 user-api LoginLogic 不同）</b>：
 * user-api 用 {@code rpcResp.getStatus() == 1L/2L} 区分密码错 / 用户封禁；
 * admin-api proto 的 {@code LoginResp} 只有 uid 字段（admin.proto:66-68），<b>没有</b> status 字段，
 * 错误完全靠 gRPC codes 区分 —— 与 Go 端 admin/loginlogic.go 第 47-57 行的 switch 完全一致。
 *
 * <p>六个可观察面保留：
 * <ul>
 *   <li><b>Return</b>：成功时 {@code data.token} 是 HS256 JWT（含 claims.userId = uid）；
 *       失败时 {@code code} 字段是 5 个 ErrorCode 之一</li>
 *   <li><b>Error</b>：5 个固定业务码（1003/5001/1002/1015/5001/200）</li>
 *   <li><b>Log</b>：rpc_error 时 logger.LogBusinessErr；success 时 logger.LogInfo 带用户名（不脱敏，与 Go 端一致）</li>
 *   <li><b>Trace</b>：从 {@link Context#current()} 取 OTel ctx，logger 自动填 trace_id 到 MDC</li>
 *   <li><b>Metric</b>：3 个 result label 完整保留（{@code rpc_error / internal_error / success}）</li>
 *   <li><b>Lifecycle</b>：仅依赖 Spring 注入的 {@link ServiceContext} 和 {@link Metrics}，
 *       无可变状态；可重入</li>
 * </ul>
 */
@Service
public class LoginLogic {

    private final ServiceContext svcCtx;
    private final Metrics metrics;
    private final LoggerFactoryBean logger;

    public LoginLogic(ServiceContext svcCtx, Metrics metrics, LoggerFactoryBean logger) {
        this.svcCtx = svcCtx;
        this.metrics = metrics;
        this.logger = logger;
    }

    /**
     * 对应 Go {@code func (l *LoginLogic) Login(req *types.LoginReq) (resp *types.LoginResp, code int)}。
     */
    public LogicResult<ApiTypes.LoginResp> login(ApiTypes.LoginReq req, HttpServletRequest httpRequest) {
        Context otelCtx = Context.current();

        LoginReq rpcReq = LoginReq.newBuilder()
                .setUsername(req.getUsername())
                .setPassword(req.getPassword())
                .build();

        LoginResp rpcResp;
        try {
            rpcResp = svcCtx.getAdminRpc().login(rpcReq);
        } catch (StatusRuntimeException e) {
            metrics.getLoginRpcError().increment();
            logger.logBusinessErr(otelCtx, ErrorCode.ERROR.getCode(), e);
            // 对应 Go 端 loginlogic.go:47-57 的 switch st.Code()
            Status.Code code = e.getStatus().getCode();
            switch (code) {
                case NOT_FOUND:
                    return LogicResult.fail(ErrorCode.ERROR_USER_NOT_EXIST);          // 1003
                case INTERNAL:
                    return LogicResult.fail(ErrorCode.ERROR_SERVER_COMMON);          // 5001
                case UNAUTHENTICATED:
                    return LogicResult.fail(ErrorCode.ERROR_LOGIN_WRONG);            // 1002
                default:
                    return LogicResult.fail(ErrorCode.CODE_SERVER_BUSY);              // 1015
            }
        }

        // 签发 JWT：与 Go 端 jwt.GetToken(secret, now, expire, uid) 1:1
        long nowSec = System.currentTimeMillis() / 1000L;
        long expireSec = svcCtx.getConfig().getAdminAuth().getAccessExpire();
        String accessSecret = svcCtx.getConfig().getAdminAuth().getAccessSecret();
        String token;
        try {
            token = JwtUtil.getToken(accessSecret, nowSec, expireSec, rpcResp.getUid());
        } catch (Throwable t) {
            // 对应 Go 端 loginlogic.go:64-68 —— JWT 签发失败 → ErrorServerCommon (5001) + metrics "internal_error"
            metrics.getLoginInternalError().increment();
            logger.logBusinessErr(otelCtx, ErrorCode.ERROR_SERVER_COMMON.getCode(),
                    new RuntimeException("generate token failed : " + t.getMessage(), t));
            return LogicResult.fail(ErrorCode.ERROR_SERVER_COMMON);
        }

        metrics.getLoginSuccess().increment();
        logger.logInfo(otelCtx, "admin login success,username : " + req.getUsername());

        ApiTypes.LoginResp resp = new ApiTypes.LoginResp();
        resp.setToken(token);
        return LogicResult.ok(resp);
    }
}