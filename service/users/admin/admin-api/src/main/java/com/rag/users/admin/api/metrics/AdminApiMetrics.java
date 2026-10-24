package com.rag.users.admin.api.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * admin-api HTTP 层业务指标收集（Micrometer Counter）。
 *
 * 对应 Go: service/user/admin/api/internal/metrics/metrics.go 的 AdminApiInterceptCount。
 *
 * <p>Go → Java label 映射（16 个具名 Counter，覆盖 7 个 Logic）：
 * <p>〔<b>2026-10-24 复核订正</b>：原写「17 个」—— 实为 <b>16 个</b>
 * （login 3 + banuser 2 + unbanuser 2 + delete 2 + resetuserpassword 2 + updateuser 2 + invite/create 3）；
 * Go 侧 {@code metrics.go} 的 {@code CounterVec} 只有 <b>2 个 label 名</b>（path / result），
 * 16 是<b>组合数</b>、不是 label 名数量。逐条与 Go 的 {@code WithLabelValues} 调用点比对一致。〕
 *
 * <p>login (3 个)：
 * <pre>
 *   admin_api_intercept_total{path="/admin/login", result="rpc_error"}      → loginRpcError
 *   admin_api_intercept_total{path="/admin/login", result="internal_error"} → loginInternalError  // JWT 签发失败
 *   admin_api_intercept_total{path="/admin/login", result="success"}        → loginSuccess
 * </pre>
 *
 * <p>banuser (2)：
 * <pre>
 *   admin_api_intercept_total{path="/admin/banuser", result="rpc_error"}    → banRpcError
 *   admin_api_intercept_total{path="/admin/banuser", result="success"}       → banSuccess
 * </pre>
 *
 * <p>unbanuser (2) / delete (2) / resetuserpassword (2)：
 * 同 banuser 模式（rpc_error + success）。
 *
 * <p>updateuser (2)：
 * <pre>
 *   admin_api_intercept_total{path="/admin/updateuser", result="db_error"}  → updateDbError  (DRIFT-NEW-005)
 *   admin_api_intercept_total{path="/admin/updateuser", result="success"}    → updateSuccess
 * </pre>
 *
 * <p>invite/create (3，DRIFT-NEW-007：唯一有 token_invalid label）：
 * <pre>
 *   admin_api_intercept_total{path="/admin/invite/create", result="token_invalid"}  → inviteTokenInvalid
 *   admin_api_intercept_total{path="/admin/invite/create", result="rpc_error"}       → inviteRpcError
 *   admin_api_intercept_total{path="/admin/invite/create", result="success"}         → inviteSuccess
 * </pre>
 *
 * <p>其它 6 个 RPC（create / getself / getuser / getuserlist / logout / update）Go 端没埋点，1:1 不补。
 *
 * <p>⚠️ <b>exposition 上多一个 label（已知、未修）</b>：{@code application.yml} 配了
 * {@code management.metrics.tags.application: ${spring.application.name}}，
 * ⇒ Prometheus 输出行是 {@code admin_api_intercept_total{application="admin-api",path=...,result=...}}，
 * 而 Go 侧 {@code CounterVec} <b>只有 path / result 两个</b>。
 * ⇒ 按完整 label 集做匹配（scrape 规则 / 告警规则）的下游会失配。
 * 证据与处置见台账 {@code docs/DRIFT/users/admin/api.md} 的 M-43（2026-10-24 复核）。
 */
@Component
public class AdminApiMetrics {

    // ===== login =====
    private final Counter loginRpcError;
    private final Counter loginInternalError;
    private final Counter loginSuccess;

    // ===== banuser =====
    private final Counter banRpcError;
    private final Counter banSuccess;

    // ===== unbanuser =====
    private final Counter unbanRpcError;
    private final Counter unbanSuccess;

    // ===== delete =====
    private final Counter deleteRpcError;
    private final Counter deleteSuccess;

    // ===== resetuserpassword =====
    private final Counter resetRpcError;
    private final Counter resetSuccess;

    // ===== updateuser =====
    private final Counter updateDbError;
    private final Counter updateSuccess;

    // ===== invite/create =====
    private final Counter inviteTokenInvalid;
    private final Counter inviteRpcError;
    private final Counter inviteSuccess;

    public AdminApiMetrics(MeterRegistry registry) {
        // ---- login ----
        this.loginRpcError = counter(registry, "/admin/login", "rpc_error");
        this.loginInternalError = counter(registry, "/admin/login", "internal_error");
        this.loginSuccess = counter(registry, "/admin/login", "success");

        // ---- banuser ----
        this.banRpcError = counter(registry, "/admin/banuser", "rpc_error");
        this.banSuccess = counter(registry, "/admin/banuser", "success");

        // ---- unbanuser ----
        this.unbanRpcError = counter(registry, "/admin/unbanuser", "rpc_error");
        this.unbanSuccess = counter(registry, "/admin/unbanuser", "success");

        // ---- delete ----
        this.deleteRpcError = counter(registry, "/admin/delete", "rpc_error");
        this.deleteSuccess = counter(registry, "/admin/delete", "success");

        // ---- resetuserpassword ----
        this.resetRpcError = counter(registry, "/admin/resetuserpassword", "rpc_error");
        this.resetSuccess = counter(registry, "/admin/resetuserpassword", "success");

        // ---- updateuser ----
        this.updateDbError = counter(registry, "/admin/updateuser", "db_error");
        this.updateSuccess = counter(registry, "/admin/updateuser", "success");

        // ---- invite/create ----
        this.inviteTokenInvalid = counter(registry, "/admin/invite/create", "token_invalid");
        this.inviteRpcError = counter(registry, "/admin/invite/create", "rpc_error");
        this.inviteSuccess = counter(registry, "/admin/invite/create", "success");
    }

    private static Counter counter(MeterRegistry registry, String path, String result) {
        return Counter.builder("admin_api_intercept_total")
                .description("Total number of API frontline interceptions")
                .tag("path", path)
                .tag("result", result)
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
