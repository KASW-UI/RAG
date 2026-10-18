package com.rag.users.user.api.internal.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Prometheus 计数器。
 *
 * <p>对应 Go 端 internal/metrics/metrics.go：
 * <pre>
 * UserApiInterceptCount = prometheus.NewCounterVec(prometheus.CounterOpts{
 *     Namespace: "",
 *     Subsystem: "user_api",
 *     Name:      "intercept_total",
 *     Help:      "Total number of API frontline interceptions",
 * }, []string{"path", "result"})
 * </pre>
 *
 * <p>Java 版用 Micrometer Counter builder，对应 Go 端 8 个具名 result 取值（按 Go 代码逐个 grep 得出）：
 * <ul>
 *   <li>login: rpc_error / wrong_password / user_banned / token_error / success（5 个）</li>
 *   <li>register: rpc_error / success（2 个）</li>
 * </ul>
 *
 * <p>对照映射（保证 L1 契约冻结 —— counter 名 + label 完全一致）：
 * <pre>
 *   user_api_intercept_total{path="/user/login",    result="rpc_error"}       → loginRpcError
 *   user_api_intercept_total{path="/user/login",    result="wrong_password"}  → loginWrongPassword
 *   user_api_intercept_total{path="/user/login",    result="user_banned"}     → loginUserBanned
 *   user_api_intercept_total{path="/user/login",    result="token_error"}     → loginTokenError
 *   user_api_intercept_total{path="/user/login",    result="success"}         → loginSuccess
 *   user_api_intercept_total{path="/user/register", result="rpc_error"}       → registerRpcError
 *   user_api_intercept_total{path="/user/register", result="success"}         → registerSuccess
 * </pre>
 *
 * <p>其余 7 个 RPC（get / delete / update / logout / upload / avatar/history / avatar/select）
 * 在 Go 端没有埋点（DRIFT-016 同款决策：1:1 保留最小覆盖）。
 *
 * <p>MeterRegistry 由 spring-boot-starter-actuator 自动装配，注入即可；
 * 暴露端点：{@code GET /actuator/prometheus}。
 */
@Component
public class Metrics {

    private final Counter loginRpcError;
    private final Counter loginWrongPassword;
    private final Counter loginUserBanned;
    private final Counter loginTokenError;
    private final Counter loginSuccess;

    private final Counter registerRpcError;
    private final Counter registerSuccess;

    public Metrics(MeterRegistry registry) {
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