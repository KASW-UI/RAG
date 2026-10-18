package com.rag.users.admin.api.internal.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Prometheus 计数器。
 *
 * <p>对应 Go 端 internal/metrics/metrics.go：
 * <pre>
 * AdminApiInterceptCount = prometheus.NewCounterVec(prometheus.CounterOpts{
 *     Namespace: "",
 *     Subsystem: "admin_api",
 *     Name:      "intercept_total",
 *     Help:      "Total number of API frontline interceptions",
 * }, []string{"path", "result"})
 * </pre>
 *
 * <p>Java 版用 Micrometer Counter builder，对应 Go 端 login / banuser / unbanuser / delete /
 * resetuserpassword / updateuser / invite/create 这 7 个 RPC 的具名 result 取值
 * （按 Go 代码 admin/*.logic.go 逐个 grep 得出）：
 * <ul>
 *   <li>login: rpc_error / internal_error / success（3 个）</li>
 *   <li>banuser: rpc_error / success（2 个）</li>
 *   <li>unbanuser: rpc_error / success（2 个）</li>
 *   <li>delete: rpc_error / success（2 个）</li>
 *   <li>resetuserpassword: rpc_error / success（2 个）</li>
 *   <li>updateuser: <b>db_error</b> / success（2 个 —— DRIFT-NEW-005：Go 端 updateuser 用 db_error 而非 rpc_error）</li>
 *   <li>invite/create: token_invalid / rpc_error / success（3 个 —— <b>DRIFT-NEW-007</b>：Go 端 createinvite 是唯一有 token_invalid label 的 RPC）</li>
 * </ul>
 *
 * <p>对照映射（counter 名 + label 完全一致）：
 * <pre>
 *   admin_api_intercept_total{path="/admin/login",              result="rpc_error"}       → loginRpcError
 *   admin_api_intercept_total{path="/admin/login",              result="internal_error"}  → loginInternalError  // JWT 签发失败
 *   admin_api_intercept_total{path="/admin/login",              result="success"}         → loginSuccess
 *   admin_api_intercept_total{path="/admin/banuser",            result="rpc_error"}       → banRpcError
 *   admin_api_intercept_total{path="/admin/banuser",            result="success"}         → banSuccess
 *   admin_api_intercept_total{path="/admin/unbanuser",          result="rpc_error"}       → unbanRpcError
 *   admin_api_intercept_total{path="/admin/unbanuser",          result="success"}         → unbanSuccess
 *   admin_api_intercept_total{path="/admin/delete",             result="rpc_error"}       → deleteRpcError
 *   admin_api_intercept_total{path="/admin/delete",             result="success"}         → deleteSuccess
 *   admin_api_intercept_total{path="/admin/resetuserpassword",  result="rpc_error"}       → resetRpcError
 *   admin_api_intercept_total{path="/admin/resetuserpassword",  result="success"}         → resetSuccess
 *   admin_api_intercept_total{path="/admin/updateuser",         result="db_error"}        → updateDbError
 *   admin_api_intercept_total{path="/admin/updateuser",         result="success"}         → updateSuccess
 *   admin_api_intercept_total{path="/admin/invite/create",      result="token_invalid"}   → inviteTokenInvalid  // 合并 claim 错 + parse 错 2 处
 *   admin_api_intercept_total{path="/admin/invite/create",      result="rpc_error"}       → inviteRpcError
 *   admin_api_intercept_total{path="/admin/invite/create",      result="success"}         → inviteSuccess
 * </pre>
 *
 * <p>其它 6 个 RPC（create / getself / getuser / getuserlist / logout / update）在 Go 端没有埋点
 * （DRIFT-016 同款决策：1:1 保留最小覆盖）—— 后续每个 RPC 实装时按 Go 端实现再补。
 *
 * <p>MeterRegistry 由 spring-boot-starter-actuator 自动装配，注入即可；
 * 暴露端点：{@code GET /actuator/prometheus}。
 */
@Component
public class Metrics {

    private final Counter loginRpcError;
    private final Counter loginInternalError;
    private final Counter loginSuccess;

    private final Counter banRpcError;
    private final Counter banSuccess;

    private final Counter unbanRpcError;
    private final Counter unbanSuccess;

    private final Counter deleteRpcError;
    private final Counter deleteSuccess;

    private final Counter resetRpcError;
    private final Counter resetSuccess;

    private final Counter updateDbError;
    private final Counter updateSuccess;

    private final Counter inviteTokenInvalid;
    private final Counter inviteRpcError;
    private final Counter inviteSuccess;

    public Metrics(MeterRegistry registry) {
        this.loginRpcError = Counter.builder("admin_api_intercept_total")
                .description("Total number of API frontline interceptions")
                .tag("path", "/admin/login")
                .tag("result", "rpc_error")
                .register(registry);
        this.loginInternalError = Counter.builder("admin_api_intercept_total")
                .description("Total number of API frontline interceptions")
                .tag("path", "/admin/login")
                .tag("result", "internal_error")
                .register(registry);
        this.loginSuccess = Counter.builder("admin_api_intercept_total")
                .description("Total number of API frontline interceptions")
                .tag("path", "/admin/login")
                .tag("result", "success")
                .register(registry);

        this.banRpcError = Counter.builder("admin_api_intercept_total")
                .description("Total number of API frontline interceptions")
                .tag("path", "/admin/banuser")
                .tag("result", "rpc_error")
                .register(registry);
        this.banSuccess = Counter.builder("admin_api_intercept_total")
                .description("Total number of API frontline interceptions")
                .tag("path", "/admin/banuser")
                .tag("result", "success")
                .register(registry);

        this.unbanRpcError = Counter.builder("admin_api_intercept_total")
                .description("Total number of API frontline interceptions")
                .tag("path", "/admin/unbanuser")
                .tag("result", "rpc_error")
                .register(registry);
        this.unbanSuccess = Counter.builder("admin_api_intercept_total")
                .description("Total number of API frontline interceptions")
                .tag("path", "/admin/unbanuser")
                .tag("result", "success")
                .register(registry);

        this.deleteRpcError = Counter.builder("admin_api_intercept_total")
                .description("Total number of API frontline interceptions")
                .tag("path", "/admin/delete")
                .tag("result", "rpc_error")
                .register(registry);
        this.deleteSuccess = Counter.builder("admin_api_intercept_total")
                .description("Total number of API frontline interceptions")
                .tag("path", "/admin/delete")
                .tag("result", "success")
                .register(registry);

        this.resetRpcError = Counter.builder("admin_api_intercept_total")
                .description("Total number of API frontline interceptions")
                .tag("path", "/admin/resetuserpassword")
                .tag("result", "rpc_error")
                .register(registry);
        this.resetSuccess = Counter.builder("admin_api_intercept_total")
                .description("Total number of API frontline interceptions")
                .tag("path", "/admin/resetuserpassword")
                .tag("result", "success")
                .register(registry);

        this.updateDbError = Counter.builder("admin_api_intercept_total")
                .description("Total number of API frontline interceptions")
                .tag("path", "/admin/updateuser")
                .tag("result", "db_error")
                .register(registry);
        this.updateSuccess = Counter.builder("admin_api_intercept_total")
                .description("Total number of API frontline interceptions")
                .tag("path", "/admin/updateuser")
                .tag("result", "success")
                .register(registry);

        this.inviteTokenInvalid = Counter.builder("admin_api_intercept_total")
                .description("Total number of API frontline interceptions")
                .tag("path", "/admin/invite/create")
                .tag("result", "token_invalid")
                .register(registry);
        this.inviteRpcError = Counter.builder("admin_api_intercept_total")
                .description("Total number of API frontline interceptions")
                .tag("path", "/admin/invite/create")
                .tag("result", "rpc_error")
                .register(registry);
        this.inviteSuccess = Counter.builder("admin_api_intercept_total")
                .description("Total number of API frontline interceptions")
                .tag("path", "/admin/invite/create")
                .tag("result", "success")
                .register(registry);
    }

    public Counter getLoginRpcError() { return loginRpcError; }
    public Counter getLoginInternalError() { return loginInternalError; }
    public Counter getLoginSuccess() { return loginSuccess; }

    public Counter getBanRpcError() { return banRpcError; }
    public Counter getBanSuccess() { return banSuccess; }

    public Counter getUnbanRpcError() { return unbanRpcError; }
    public Counter getUnbanSuccess() { return unbanSuccess; }

    public Counter getDeleteRpcError() { return deleteRpcError; }
    public Counter getDeleteSuccess() { return deleteSuccess; }

    public Counter getResetRpcError() { return resetRpcError; }
    public Counter getResetSuccess() { return resetSuccess; }

    public Counter getUpdateDbError() { return updateDbError; }
    public Counter getUpdateSuccess() { return updateSuccess; }

    public Counter getInviteTokenInvalid() { return inviteTokenInvalid; }
    public Counter getInviteRpcError() { return inviteRpcError; }
    public Counter getInviteSuccess() { return inviteSuccess; }
}