package com.rag.users.admin.api.service;

import com.rag.common.exception.BusinessException;
import com.rag.users.admin.api.client.AdminRpcClient;
import com.rag.users.admin.api.config.AdminApiProperties;
import com.rag.users.admin.api.dto.LoginReq;
import com.rag.users.admin.api.dto.LoginResp;
import com.rag.users.admin.api.metrics.AdminApiMetrics;
import com.rag.users.common.errmsg.ErrorCode;
import com.rag.users.common.jwt.JwtUtil;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * admin login 用例 —— 4 状态机 + JWT 签发。
 *
 * 对应 Go: service/user/admin/api/internal/logic/admin/loginlogic.go 的 LoginLogic。
 *
 * <p>L2 业务语义（4 状态机，1:1 复刻）：
 * <ol>
 *   <li>调 adminRpc.login(LoginReq{Username, Password})
 *      → gRPC codes.Internal → ErrorServerCommon (5001) + loginRpcError
 *      → 其它（默认）        → CodeServerBusy    (1015) + loginRpcError
 *   <li>gRPC codes.NotFound → 抛业务码 ErrorUserNotExist (1003)（admin 域 NotFound → 用户名不存在）
 *   <li>JwtUtil.getToken 抛异常 → ErrorTokenRuntime (1006) + loginInternalError
 *   <li>成功 → loginSuccess + 返回 LoginResp{token}
 * </ol>
 */
@Service
public class AdminLoginService {

    private static final Logger log = LoggerFactory.getLogger(AdminLoginService.class);

    private final AdminRpcClient adminRpcClient;
    private final AdminApiProperties properties;
    private final AdminApiMetrics metrics;

    public AdminLoginService(AdminRpcClient adminRpcClient,
                            AdminApiProperties properties,
                            AdminApiMetrics metrics) {
        this.adminRpcClient = adminRpcClient;
        this.properties = properties;
        this.metrics = metrics;
    }

    public LoginResp login(LoginReq req, HttpServletRequest httpRequest) {
        com.rag.users.admin.proto.LoginReq rpcReq = com.rag.users.admin.proto.LoginReq.newBuilder()
                .setUsername(req.getUsername() == null ? "" : req.getUsername())
                .setPassword(req.getPassword() == null ? "" : req.getPassword())
                .build();

        long uid;
        try {
            com.rag.users.admin.proto.LoginResp rpcResp = adminRpcClient.adminRpc().login(rpcReq);
            uid = rpcResp.getUid();
        } catch (StatusRuntimeException e) {
            Status.Code code = e.getStatus().getCode();
            metrics.getLoginRpcError().increment();
            log.error("admin login RPC error, username={}", req.getUsername(), e);
            // Go 端 loginlogic.go switch：Internal → 5001；NotFound → 1003；default → 1015
            if (code == Status.Code.INTERNAL) {
                throw new BusinessException(ErrorCode.ERROR_SERVER_COMMON.getCode(), ErrorCode.ERROR_SERVER_COMMON.getMsg());
            }
            if (code == Status.Code.NOT_FOUND) {
                throw new BusinessException(ErrorCode.ERROR_USER_NOT_EXIST.getCode(), ErrorCode.ERROR_USER_NOT_EXIST.getMsg());
            }
            if (code == Status.Code.UNAUTHENTICATED) {
                // 密码错误：admin-rpc 的 LoginLogic 对 wrong password 抛 Status.UNAUTHENTICATED。
                // 参考侧 LoginLogic.java:97-101 把它映射成 1002「用户名或密码错误」，
                // 缺了这个 case 会落到 default 1015「服务繁忙」——把最常见的用户可见失败
                // 报成服务端故障。
                throw new BusinessException(ErrorCode.ERROR_LOGIN_WRONG.getCode(), ErrorCode.ERROR_LOGIN_WRONG.getMsg());
            }
            throw new BusinessException(ErrorCode.CODE_SERVER_BUSY.getCode(), ErrorCode.CODE_SERVER_BUSY.getMsg());
        }

        // 签发 JWT —— 与 Go jwt.GetToken(secret, now, expire, uid) 1:1
        long nowSec = System.currentTimeMillis() / 1000L;
        long expireSec = properties.getAdminAuth().getAccessExpire();
        String accessSecret = properties.getAdminAuth().getAccessSecret();
        String token;
        try {
            token = JwtUtil.getToken(accessSecret, nowSec, expireSec, uid);
        } catch (Throwable t) {
            // 与参考侧 LoginLogic.java:104-115 1:1：JWT 签发失败属于服务端内部错误 → 5001，
            // 不是 1006「TOKEN已过期」（那是 token 校验失败，语义完全不同）。
            metrics.getLoginInternalError().increment();
            log.error("admin login generate token failed : {}", t.getMessage(), t);
            throw new BusinessException(ErrorCode.ERROR_SERVER_COMMON.getCode(), ErrorCode.ERROR_SERVER_COMMON.getMsg());
        }

        metrics.getLoginSuccess().increment();
        log.info("admin login success, uid={} username={}", uid, req.getUsername());

        LoginResp resp = new LoginResp();
        resp.setToken(token);
        return resp;
    }
}
