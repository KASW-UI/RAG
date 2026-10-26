package com.rag.common.protection;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * L4 idiomatization：按路由的熔断中间件，对齐 Go {@code handler.BreakerHandler}。
 *
 * <p><b>Go 出处</b>：{@code rest/handler/breakerhandler.go:19-44}
 * <pre>
 * func BreakerHandler(method, path string, metrics *stat.Metrics) func(http.Handler) http.Handler {
 *     brk := breaker.NewBreaker(breaker.WithName(method + "://" + path))   // ← 每路由一个
 *     return func(next http.Handler) http.Handler {
 *         return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
 *             promise, err := brk.Allow()
 *             if err != nil {
 *                 metrics.AddDrop()
 *                 w.WriteHeader(http.StatusServiceUnavailable)          // ← 503，无 body
 *                 return
 *             }
 *             cw := response.NewWithCodeResponseWriter(w)
 *             defer func() {
 *                 if cw.Code &lt; http.StatusInternalServerError { promise.Accept() }
 *                 else { promise.Reject(fmt.Sprintf("%d %s", cw.Code, http.StatusText(cw.Code))) }
 *             }()
 *             next.ServeHTTP(cw, r)
 *         })
 *     }
 * }
 * </pre>
 *
 * <p><b>语义等价论证</b>：
 * <ol>
 *   <li><b>隔离单位 = {@code METHOD://path}</b>：Go 在 {@code engine.go:149} 对每个路由
 *       各建一个 breaker。13 个端点 ⇒ 最多 13 个 breaker，互不影响。
 *       本类用 {@link ConcurrentHashMap} 按同一 key 懒建，key 拼接方式与 Go 的
 *       {@code breakerSeparator = "://"} 逐字一致（便于两边日志对齐）。</li>
 *   <li><b>成败判据只看 HTTP 状态码 ≥500</b>（{@code breakerhandler.go:39}）。
 *       ⚠️ 这在本模块有个<b>必须说清的后果</b>：message-api 的所有业务错误
 *       （含下游 gRPC 失败）都是 <b>HTTP 200 + envelope</b>
 *       （Go 侧 {@code messagecenter.go} 注册了 {@code SetErrorHandler}），
 *       ⇒ <b>下游 RPC 挂掉不会触发本熔断</b>，Go 与 RAG 在这一点上必须同样「不熔断」。
 *       能触发它的只有两条路径：超时（Go {@code timeouthandler.go:115} 写 503）
 *       与未捕获 panic（{@code recoverhandler.go:17} 写 500）。
 *       ⇒ <b>本层的真实职责是「别让一个正在超时或崩溃的端点被继续打」，
 *       而不是「保护下游」</b>。若误以为它保护下游、按业务码去熔断，就是新增行为。</li>
 *   <li><b>拒绝时裸 503 空 body</b>，理由同 {@link GoStyleMaxBytesFilter}
 *       （不用 {@code sendError}，避免容器补 Content-Type）。</li>
 *   <li><b>拒绝也要 {@code markDrop}</b>（{@code breakerhandler.go:29} →
 *       {@code allow()} 内 {@code b.markDrop()}）：drop 计入 {@code Sum}，
 *       会推高后续的 {@code dropRatio}。</li>
 * </ol>
 *
 * <p><b>链上位置</b>：Go {@code engine.go:148-150} 的 Breaker 在 MaxConns 之后、
 * Shedding 之前 ⇒ 本类注册在 {@link Ordered#HIGHEST_PRECEDENCE} + 5。
 *
 * <p>⚠️ <b>2026-10-25（第十九轮 · global T-8）：由 +2 改为 +5</b>（给 traceparent filter 让出位置，
 * 相对顺序不变 —— 见 {@code GoStyleMaxConnsFilter} 的同款说明）。
 *
 * <p><b>为什么 breaker 实例不做无界缓存</b>：key 由 method+path 构成，基数等于路由数
 * （本模块 13 条，含未匹配路径不会建 key），无界增长的现实风险为零。
 */
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class GoStyleBreakerFilter implements Filter {

    private final boolean enabled;
    private final Map<String, GoStyleBreaker> breakers = new ConcurrentHashMap<>();

    public GoStyleBreakerFilter(GoStyleProtectionProperties props) {
        this.enabled = props.getBreaker().isEnabled();
    }

    /** 取（或懒建）某路由的 breaker。测试直接调它来观测状态。 */
    GoStyleBreaker breakerFor(HttpServletRequest request) {
        return breakers.computeIfAbsent(routeKey(request), GoStyleBreaker::new);
    }

    /** 与 Go {@code breakerhandler.go:21} 的 name 拼接一致。 */
    static String routeKey(HttpServletRequest request) {
        return request.getMethod() + "://" + request.getRequestURI();
    }

    /** 当前已建的 breaker 数（= 有流量过的路由数）。 */
    int trackedRoutes() {
        return breakers.size();
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        if (!enabled) {
            chain.doFilter(req, resp);
            return;
        }
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) resp;

        GoStyleBreaker breaker = breakerFor(request);
        if (!breaker.accept()) {
            // breakerhandler.go:25-32
            breaker.markDrop();
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }

        GoStyleStatusCapturingResponse capturing = new GoStyleStatusCapturingResponse(response);
        try {
            chain.doFilter(request, capturing);
        } finally {
            // breakerhandler.go:37-43 的 defer：按最终状态码记 success / failure。
            // ⚠️ 必须放在 finally：响应已写出但 handler 抛异常时，Go 侧的 defer 同样会跑，
            //    此时 capturedStatus 是 500（容器错误页已定码），故仍判 failure —— 与 Go 一致。
            if (capturing.isAcceptable()) {
                breaker.markSuccess();
            } else {
                breaker.markFailure();
            }
        }
    }
}