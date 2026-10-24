package com.rag.users.user.api.service;

import com.rag.common.exception.BusinessException;
import com.rag.common.protection.GoStyleCircuitOpenException;
import com.rag.users.common.errmsg.ErrorCode;
import com.rag.users.common.jwt.JwtUtil;
import com.rag.users.user.api.client.UserRpcClient;
import com.rag.users.user.api.config.UserApiProperties;
import com.rag.users.user.api.dto.LoginReq;
import com.rag.users.user.api.dto.LoginResp;
import com.rag.users.user.api.metrics.UserApiMetrics;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * login 用例 — HTTP DTO ↔ gRPC proto + JWT 签发 + 5 状态机业务码映射。
 *
 * 对应 Go: service/user/user/api/internal/logic/user/loginlogic.go 的 LoginLogic。
 *
 * <p>L1 外部契约 + L2 业务语义（6 状态机，1:1 复刻）：
 * <pre>
 *   1. 调 userRpc.login(LoginReq{Username, Password})
 *      → gRPC codes.Internal → ErrorServerCommon (5001) + loginRpcError
 *      → 其它（默认）        → CodeServerBusy    (1015) + loginRpcError
 *
 *   2. rpcResp.Status == 1（密码错或用户不存在）
 *      → ErrorLoginWrong (1002) + loginWrongPassword
 *
 *   3. rpcResp.Status == 2（用户封禁）
 *      → ErrorUserBanned (1011) + loginUserBanned
 *
 *   4. JwtUtil.getToken 抛异常
 *      → ErrorTokenRuntime (1006) + loginTokenError
 *
 *   5. 成功
 *      → loginSuccess + 返回 {token: "..."}
 * </pre>
 *
 * <p>L4 实现机制差异（与 Go 端）：
 * <ul>
 *   <li>Go 用 LogicResult 双返回 + handler 包 envelope；Java 用 BusinessException 单异常路径，
 *       GlobalExceptionHandler 统一包 envelope</li>
 *   <li>Go 用 LoggerFactoryBean；Java 用 SLF4J Logger（避免引入 LoggerFactoryBean 适配层）</li>
 *   <li>Go 在 config.UserAuth 拿 accessSecret/accessExpire；Java 在 UserApiProperties.UserAuth 拿（Spring 风格）</li>
 * </ul>
 */
@Service
public class UserLoginService {

    private static final Logger log = LoggerFactory.getLogger(UserLoginService.class);

    private final UserRpcClient userRpcClient;
    private final UserApiProperties properties;
    private final UserApiMetrics metrics;

    public UserLoginService(UserRpcClient userRpcClient,
                            UserApiProperties properties,
                            UserApiMetrics metrics) {
        this.userRpcClient = userRpcClient;
        this.properties = properties;
        this.metrics = metrics;
    }

    /**
     * 对应 Go {@code func (l *LoginLogic) Login(req *types.LoginReq) (resp *types.LoginResp, code int)}。
     *
     * @param req   HTTP 层 LoginReq（username + password）
     * @param httpRequest  当前 HTTP 请求（保留参数对齐 Go 端 ctx 参数，但本方法不依赖 request attribute）
     * @return LoginResp（带 token）
     * @throws BusinessException 业务码异常（1002 / 1011 / 1006 / 5001 / 1015）
     */
    public LoginResp login(LoginReq req, HttpServletRequest httpRequest) {
        com.rag.users.user.proto.LoginReq rpcReq = com.rag.users.user.proto.LoginReq.newBuilder()
                .setUsername(req.getUsername() == null ? "" : req.getUsername())
                .setPassword(req.getPassword() == null ? "" : req.getPassword())
                .build();

        com.rag.users.user.proto.LoginResp rpcResp;
        try {
            rpcResp = userRpcClient.userRpc().login(rpcReq);
        } catch (GoStyleCircuitOpenException e) {
            // Go 出处：loginlogic.go:46-48 的 err != nil 分支 —— 它的 err != nil
            // **捕获一切**，所以熔断抛出的 breaker.ErrServiceUnavailable（普通 error，
            // 不是 gRPC status）也会命中 ⇒ Go 侧的 rpc_error 计数**会**递增。
            // 本 catch 只为对齐这个计数；业务码映射交给 advice 的 handleCircuitOpen
            // （Go 侧 status.FromError ⇒ codes.Unknown ⇒ default ⇒ 1015）。
            metrics.getLoginRpcError().increment();
            log.error("login RPC error (circuit breaker open), username={}", req.getUsername(), e);
            throw e;
        } catch (StatusRuntimeException e) {
            metrics.getLoginRpcError().increment();
            log.error("login RPC error, username={}", req.getUsername(), e);
            // 1:1 对齐 Go 端 LoginLogic 的 switch (e.getStatus().getCode())：
            //   codes.Internal → ErrorServerCommon (5001)
            //   其它（default）→ CodeServerBusy    (1015)
            io.grpc.Status.Code code = e.getStatus().getCode();
            if (code == io.grpc.Status.Code.INTERNAL) {
                throw new BusinessException(
                        ErrorCode.ERROR_SERVER_COMMON.getCode(),
                        ErrorCode.ERROR_SERVER_COMMON.getMsg());
            }
            throw new BusinessException(
                    ErrorCode.CODE_SERVER_BUSY.getCode(),
                    ErrorCode.CODE_SERVER_BUSY.getMsg());
        }

        // rpcResp.Status == 1 → 密码错或用户不存在（rpc 不区分）
        if (rpcResp.getStatus() == 1L) {
            metrics.getLoginWrongPassword().increment();
            log.info("login failed : wrong password,username : {}", req.getUsername());
            throw new BusinessException(ErrorCode.ERROR_LOGIN_WRONG.getCode(), ErrorCode.ERROR_LOGIN_WRONG.getMsg());
        }

        // rpcResp.Status == 2 → 用户封禁（DB status=1）
        if (rpcResp.getStatus() == 2L) {
            metrics.getLoginUserBanned().increment();
            log.info("login failed : user banned,username : {}", req.getUsername());
            throw new BusinessException(ErrorCode.ERROR_USER_BANNED.getCode(), ErrorCode.ERROR_USER_BANNED.getMsg());
        }

        // 签发 JWT —— 与 Go jwt.GetToken(secret, now, expire, uid) 1:1
        long nowSec = System.currentTimeMillis() / 1000L;
        long expireSec = properties.getUserAuth().getAccessExpire();
        String accessSecret = properties.getUserAuth().getAccessSecret();
        String token;
        try {
            token = JwtUtil.getToken(accessSecret, nowSec, expireSec, rpcResp.getUid());
        } catch (Throwable t) {
            metrics.getLoginTokenError().increment();
            log.error("generate token failed : {}", t.getMessage(), t);
            throw new BusinessException(ErrorCode.ERROR_TOKEN_RUNTIME.getCode(), ErrorCode.ERROR_TOKEN_RUNTIME.getMsg());
        }

        metrics.getLoginSuccess().increment();
        log.info("login success,username : {}", req.getUsername());

        LoginResp resp = new LoginResp();
        resp.setToken(token);
        return resp;
    }
}
