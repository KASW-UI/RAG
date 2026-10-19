package com.rag.users.user.api.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * user-api HTTP 层业务指标收集。
 *
 * 对应 Go: service/user/user/api/internal/metrics/metrics.go 的 UserApiInterceptCount。
 *
 * Go → Java label 映射（DRIFT-016：1:1 保留最小覆盖 —— 仅 login/register 埋点）：
 *   user_api_intercept_total{path="/user/login",    result="rpc_error"}       → loginRpcError
 *   user_api_intercept_total{path="/user/login",    result="wrong_password"}  → loginWrongPassword
 *   user_api_intercept_total{path="/user/login",    result="user_banned"}     → loginUserBanned
 *   user_api_intercept_total{path="/user/login",    result="token_error"}     → loginTokenError
 *   user_api_intercept_total{path="/user/login",    result="success"}         → loginSuccess
 *   user_api_intercept_total{path="/user/register", result="rpc_error"}       → registerRpcError
 *   user_api_intercept_total{path="/user/register", result="success"}         → registerSuccess
 *
 * 其余 7 个 RPC（get / delete / update / logout / upload / avatar/history / avatar/select）
 * 在 Go 端没有埋点，1:1 不补。
 *
 * 框架职责:
 *   - Micrometer Counter + MeterRegistry —— 取代直接 prometheus client
 *   - 从原 internal.metrics.Metrics 改名 + 加 UserApi 前缀
 *   - 暴露端点：Spring Boot Actuator /actuator/prometheus（user-api 有 HTTP server，可暴露）
 */
@Component
public class UserApiMetrics {

    private final Counter loginRpcError;
    private final Counter loginWrongPassword;
    private final Counter loginUserBanned;
    private final Counter loginTokenError;
    private final Counter loginSuccess;

    private final Counter registerRpcError;
    private final Counter registerSuccess;

    @Autowired
    public UserApiMetrics(MeterRegistry registry) {
        this.loginRpcError = Counter.builder("user_api_intercept_total")
                .description("Total number of API frontline interceptions")
                .tag("path", "/user/login")
                .tag("result", "rpc_error")
                .register(registry);
        this.loginWrongPassword = Counter.builder("user_api_intercept_total")
                .description("Total number of API frontline interceptions")
                .tag("path", "/user/login")
                .tag("result", "wrong_password")
                .register(registry);
        this.loginUserBanned = Counter.builder("user_api_intercept_total")
                .description("Total number of API frontline interceptions")
                .tag("path", "/user/login")
                .tag("result", "user_banned")
                .register(registry);
        this.loginTokenError = Counter.builder("user_api_intercept_total")
                .description("Total number of API frontline interceptions")
                .tag("path", "/user/login")
                .tag("result", "token_error")
                .register(registry);
        this.loginSuccess = Counter.builder("user_api_intercept_total")
                .description("Total number of API frontline interceptions")
                .tag("path", "/user/login")
                .tag("result", "success")
                .register(registry);

        this.registerRpcError = Counter.builder("user_api_intercept_total")
                .description("Total number of API frontline interceptions")
                .tag("path", "/user/register")
                .tag("result", "rpc_error")
                .register(registry);
        this.registerSuccess = Counter.builder("user_api_intercept_total")
                .description("Total number of API frontline interceptions")
                .tag("path", "/user/register")
                .tag("result", "success")
                .register(registry);
    }

    public Counter getLoginRpcError() { return loginRpcError; }
    public Counter getLoginWrongPassword() { return loginWrongPassword; }
    public Counter getLoginUserBanned() { return loginUserBanned; }
    public Counter getLoginTokenError() { return loginTokenError; }
    public Counter getLoginSuccess() { return loginSuccess; }
    public Counter getRegisterRpcError() { return registerRpcError; }
    public Counter getRegisterSuccess() { return registerSuccess; }
}