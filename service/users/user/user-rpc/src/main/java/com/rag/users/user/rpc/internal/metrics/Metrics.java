package com.rag.users.user.rpc.internal.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Prometheus 计数器。
 *
 * 对应 Go 端 internal/metrics/metrics.go：
 *   UserRegisterCount = prometheus.NewCounterVec(... "user_rpc_register_total", []string{"result"})
 *   UserLoginCount    = prometheus.NewCounterVec(... "user_rpc_login_total",    []string{"result"})
 *
 * Go 端只在这两个 RPC 上埋点（其他 7 个 RPC 没 metrics）。D-016 决策：1:1 保留最小覆盖，
 * Java 侧用 Micrometer Counter builder 拆成 8 个具名 Counter，对应 Go 的 8 个 result label 取值。
 *
 * Go → Java label 映射：
 *   user_rpc_register_total{result=success}      → registerSuccess
 *   user_rpc_register_total{result=fail_exists}  → registerFailExists
 *   user_rpc_register_total{result=db_error}     → registerDbError
 *   user_rpc_register_total{result=internal_error} → registerInternalError
 *   user_rpc_login_total{result=success}         → loginSuccess
 *   user_rpc_login_total{result=user_not_found}  → loginUserNotFound
 *   user_rpc_login_total{result=db_error}        → loginDbError
 *   user_rpc_login_total{result=internal_error}  → loginInternalError
 *
 * MeterRegistry 由 spring-boot-starter-actuator 自动装配，注入即可。
 * 暴露端点：GET /actuator/prometheus（要 application.yaml 显式 include prometheus 端点）。
 */
@Component
public class Metrics {

    private final Counter registerSuccess;
    private final Counter registerFailExists;
    private final Counter registerDbError;
    private final Counter registerInternalError;

    private final Counter loginSuccess;
    private final Counter loginUserNotFound;
    private final Counter loginDbError;
    private final Counter loginInternalError;

    public Metrics(MeterRegistry registry) {
        this.registerSuccess = Counter.builder("user_rpc_register_total")
                .description("Total number of user registerations")
                .tag("result", "success")
                .register(registry);
        this.registerFailExists = Counter.builder("user_rpc_register_total")
                .description("Total number of user registerations")
                .tag("result", "fail_exists")
                .register(registry);
        this.registerDbError = Counter.builder("user_rpc_register_total")
                .description("Total number of user registerations")
                .tag("result", "db_error")
                .register(registry);
        this.registerInternalError = Counter.builder("user_rpc_register_total")
                .description("Total number of user registerations")
                .tag("result", "internal_error")
                .register(registry);

        this.loginSuccess = Counter.builder("user_rpc_login_total")
                .description("Total number of user logins")
                .tag("result", "success")
                .register(registry);
        this.loginUserNotFound = Counter.builder("user_rpc_login_total")
                .description("Total number of user logins")
                .tag("result", "user_not_found")
                .register(registry);
        this.loginDbError = Counter.builder("user_rpc_login_total")
                .description("Total number of user logins")
                .tag("result", "db_error")
                .register(registry);
        this.loginInternalError = Counter.builder("user_rpc_login_total")
                .description("Total number of user logins")
                .tag("result", "internal_error")
                .register(registry);
    }

    public Counter getRegisterSuccess() { return registerSuccess; }
    public Counter getRegisterFailExists() { return registerFailExists; }
    public Counter getRegisterDbError() { return registerDbError; }
    public Counter getRegisterInternalError() { return registerInternalError; }

    public Counter getLoginSuccess() { return loginSuccess; }
    public Counter getLoginUserNotFound() { return loginUserNotFound; }
    public Counter getLoginDbError() { return loginDbError; }
    public Counter getLoginInternalError() { return loginInternalError; }
}