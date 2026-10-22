package com.rag.common.protection;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;

/**
 * 只<b>观测</b>状态码的响应包装器 —— 给 {@link GoStyleBreakerFilter} 提供
 * 「这次调用最终返回了哪个 HTTP 状态」的判据。
 *
 * <p><b>为什么必须自己写、且必须「即时透传」而不是缓冲</b>：
 * 本项目 {@code service/common} 的 {@code HttpMiddleware} 里有一个
 * {@code ObservabilityResponseWriter}，它早期把 {@code setHeader} 收进
 * {@code capturedHeaders} 再在 {@code flushTo} 回写 —— 结果在真实 Tomcat 下
 * 因为回写发生在 {@code chain.doFilter()} <b>之后</b>（此时 response 已 committed）
 * 而被静默丢弃（见 AGENTS.md §3.3 的 DRIFT-024 记录）。
 * <b>本类刻意不重演那个形态</b>：只记录一个 int 状态码，
 * {@code setStatus} 立即透传给底层，<b>没有任何 header/body 缓冲</b>，
 * 因此不存在「回写时已 committed」这个失效面。
 *
 * <p><b>默认值 200 的依据</b>：servlet 规范下，应用若<b>从未</b>调用
 * {@code setStatus} 而直接写 body，最终状态码就是 200。
 * Go 侧的 {@code response.NewWithCodeResponseWriter} 同样以
 * {@code http.StatusOK} 为初值（{@code cw.Code < http.StatusInternalServerError} 这一句
 * 依赖该初值才能把「没显式设过码」判成成功）。
 * ⇒ 两边的「未显式设码」都等价于 <b>成功</b>，不是 unknown。
 */
final class GoStyleStatusCapturingResponse extends HttpServletResponseWrapper {

    /**
     * servlet 未显式设码时的等价状态码。
     *
     * <p>用常量而不是「null 表示未设」：Go 侧也不区分「显式设成 200」与「没设」，
     * 两者在 {@code cw.Code < 500} 的判据下走向相同。
     */
    private static final int IMPLICIT_OK = HttpServletResponse.SC_OK;

    private int capturedStatus = IMPLICIT_OK;

    GoStyleStatusCapturingResponse(HttpServletResponse response) {
        super(response);
    }

    @Override
    public void setStatus(int sc) {
        // 先记后传：即使底层抛异常（如 response 已 committed），判据也不会丢
        this.capturedStatus = sc;
        super.setStatus(sc);
    }

    @Override
    public void sendError(int sc) throws IOException {
        this.capturedStatus = sc;
        super.sendError(sc);
    }

    @Override
    public void sendError(int sc, String msg) throws IOException {
        this.capturedStatus = sc;
        super.sendError(sc, msg);
    }

    @Override
    public void sendRedirect(String location) throws IOException {
        // Go 的 chain 里没有 redirect 分支；3xx < 500，breaker 判成功 —— 与不记等价
        super.sendRedirect(location);
    }

    /** 本次调用最终对外呈现的 HTTP 状态码。 */
    int capturedStatus() {
        return capturedStatus;
    }

    /**
     * 与 Go {@code breakerhandler.go:39} 的判据逐字对齐：
     * {@code if cw.Code < http.StatusInternalServerError { promise.Accept() } else { promise.Reject(...) }}。
     *
     * <p>⚠️ <b>这条判据只看 HTTP 状态码，不看业务码</b>。本模块的业务错误
     * （含下游 gRPC 失败）恒为 <b>HTTP 200 + envelope</b>，因此在 Go 侧它们
     * 会被 breaker 记为 <b>Accept（成功）</b>。若 Java 侧改成「按 envelope 里的
     * {@code code} 判成败」，RAG 就会在 Go 不会熔断的地方熔断 —— 那是新增行为，不是对齐。
     */
    boolean isAcceptable() {
        return capturedStatus < HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
    }
}
