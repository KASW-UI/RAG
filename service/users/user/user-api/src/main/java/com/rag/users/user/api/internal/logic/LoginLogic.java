package com.rag.users.user.api.internal.logic;

import com.rag.common.spring.LoggerFactoryBean;
import com.rag.users.common.errmsg.ErrorCode;
import com.rag.users.common.jwt.JwtUtil;
import com.rag.users.user.api.internal.metrics.Metrics;
import com.rag.users.user.api.internal.svc.ServiceContext;
import com.rag.users.user.api.internal.types.ApiTypes;
import com.rag.users.user.proto.LoginReq;
import com.rag.users.user.proto.LoginResp;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.opentelemetry.context.Context;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * 对应 Go 端 internal/logic/user/loginlogic.go。
 *
 * <p>L4 实现机制差异：Go 用闭包构造函数（{@code NewLoginLogic(ctx, svcCtx) *LoginLogic}），
 * Java 侧用 {@code @Service} + 构造器注入 {@link ServiceContext} 和 {@link Metrics}。
 * 每个 request handler 调用 {@link #login(ApiTypes.LoginReq, HttpServletRequest)} 时，
 * {@code request} 参数代替 Go 端的 ctx 参数。
 *
 * <p>L1 外部契约 + L2 业务语义（六状态机，逐项冻结）：
 * <pre>
 *   1. 调 userRpc.Login(LoginReq{Username, Password})
 *      → 失败：
 *         gRPC codes.Internal   → ErrorServerCommon (5001) + metrics "rpc_error"
 *         其它（默认）           → CodeServerBusy    (1015) + metrics "rpc_error"
 *      → 成功：继续
 *
 *   2. rpcResp.Status == 1（密码错或用户不存在，Rpc 不区分）
 *      → ErrorLoginWrong (1002) + metrics "wrong_password"
 *
 *   3. rpcResp.Status == 2（用户封禁，DB status=1）
 *      → ErrorUserBanned (1011) + metrics "user_banned"
 *
 *   4. jwt.GetToken(...) 失败
 *      → ErrorTokenRuntime (1006) + metrics "token_error"
 *
 *   5. 成功
 *      → Success (200) + metrics "success"
 *      → 返回 {token: "..."}
 * </pre>
 *
 * <p>六个可观察面保留（identity_contract.md §5）：
 * <ul>
 *   <li><b>Return</b>：成功时 {@code data.token} 是 HS256 JWT（含 claims.userId = uid）；
 *       失败时 {@code code} 字段是 5 个 ErrorCode 之一</li>
 *   <li><b>Error</b>：5 个固定业务码（5001/1015/1002/1011/1006/200）</li>
 *   <li><b>Log</b>：rpc_error 时 logger.LogBusinessErr；wrong_password / user_banned / success 时
 *       logger.LogInfo 带用户名（不脱敏，与 Go 端一致）</li>
 *   <li><b>Trace</b>：从 {@link Context#current()} 取 OTel ctx，logger 自动填 trace_id 到 MDC</li>
 *   <li><b>Metric</b>：5 个 result label 完整保留（{@code rpc_error / wrong_password /
 *       user_banned / token_error / success}）</li>
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
            rpcResp = svcCtx.getUserRpc().login(rpcReq);
        } catch (StatusRuntimeException e) {
            metrics.getLoginRpcError().increment();
            logger.logBusinessErr(otelCtx, ErrorCode.ERROR.getCode(), e);
            Status.Code code = e.getStatus().getCode();
            if (code == Status.Code.INTERNAL) {
                return LogicResult.fail(ErrorCode.ERROR_SERVER_COMMON);
            }
            return LogicResult.fail(ErrorCode.CODE_SERVER_BUSY);
        }

        // rpcResp.Status == 1 → 密码错（与"用户不存在"共用，与 Go 端一致）
        if (rpcResp.getStatus() == 1L) {
            metrics.getLoginWrongPassword().increment();
            logger.logInfo(otelCtx, "login failed : wrong password,username : " + req.getUsername());
            return LogicResult.fail(ErrorCode.ERROR_LOGIN_WRONG);
        }

        // rpcResp.Status == 2 → 用户封禁
        if (rpcResp.getStatus() == 2L) {
            metrics.getLoginUserBanned().increment();
            logger.logInfo(otelCtx, "login failed : user banned,username : " + req.getUsername());
            return LogicResult.fail(ErrorCode.ERROR_USER_BANNED);
        }

        // 签发 JWT：与 Go 端 jwt.GetToken(secret, now, expire, uid) 1:1
        long nowSec = System.currentTimeMillis() / 1000L;
        long expireSec = svcCtx.getConfig().getUserAuth().getAccessExpire();
        String accessSecret = svcCtx.getConfig().getUserAuth().getAccessSecret();
        String token;
        try {
            token = JwtUtil.getToken(accessSecret, nowSec, expireSec, rpcResp.getUid());
        } catch (Throwable t) {
            metrics.getLoginTokenError().increment();
            logger.logBusinessErr(otelCtx, ErrorCode.ERROR_TOKEN_RUNTIME.getCode(),
                    new RuntimeException("generate token failed : " + t.getMessage(), t));
            return LogicResult.fail(ErrorCode.ERROR_TOKEN_RUNTIME);
        }

        metrics.getLoginSuccess().increment();
        logger.logInfo(otelCtx, "login success,username : " + req.getUsername());

        ApiTypes.LoginResp resp = new ApiTypes.LoginResp();
        resp.setToken(token);
        return LogicResult.ok(resp);
    }
}