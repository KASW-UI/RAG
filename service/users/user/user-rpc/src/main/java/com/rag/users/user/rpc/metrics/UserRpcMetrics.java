package com.rag.users.user.rpc.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * user-rpc 业务指标收集。
 *
 * 对应 Go: service/user/user/rpc/internal/metrics/metrics.go 的 8 个 Counter：
 *   UserRegisterCount = prometheus.NewCounterVec(... "user_rpc_register_total", []string{"result"})
 *   UserLoginCount    = prometheus.NewCounterVec(... "user_rpc_login_total",    []string{"result"})
 *
 * Go → Java label 映射（D-016：1:1 保留最小覆盖）：
 *   user_rpc_register_total{result=success}      → registerSuccess
 *   user_rpc_register_total{result=fail_exists}  → registerFailExists
 *   user_rpc_register_total{result=db_error}     → registerDbError
 *   user_rpc_register_total{result=internal_error} → registerInternalError
 *   user_rpc_login_total{result=success}         → loginSuccess
 *   user_rpc_login_total{result=user_not_found}  → loginUserNotFound
 *   user_rpc_login_total{result=db_error}        → loginDbError
 *   user_rpc_login_total{result=internal_error}  → loginInternalError
 *
 * 框架职责:
 *   - Micrometer Counter + MeterRegistry —— 取代直接 prometheus client
 *   - 8 个具名 Counter，每个 Counter 自带 tag("result", ...)（与 Go 端 label 一致）
 *   - 暴露端点：Spring Boot Actuator 默认不暴露（user-rpc 纯 gRPC 不引 web）；
 *     如需 scrape metrics，未来可加 spring-boot-starter-web + 配置 prometheus 端点
 *   - 从原 internal.metrics.Metrics 改名 + 加 UserRpc 前缀以体现归属
 */
@Component
public class UserRpcMetrics {

    private final Counter registerSuccess;
    private final Counter registerFailExists;
    private final Counter registerDbError;
    private final Counter registerInternalError;

    private final Counter loginSuccess;
    private final Counter loginUserNotFound;
    private final Counter loginDbError;
    private final Counter loginInternalError;

    @Autowired
    public UserRpcMetrics(MeterRegistry registry) {
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