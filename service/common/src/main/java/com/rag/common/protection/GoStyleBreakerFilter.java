package com.rag.common.protection;

import com.rag.common.web.ConditionalOnApiSide;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
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
 * <p><b>Go 出处</b>：{@code rest/handler/breakerhandler.go:18-42}
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
 *       {@code breakerSeparator = "://"} 逐字一致（便于两边日志对齐）。
 *       <br>⚠️ <b>2026-10-27 订正</b>：Go 的 {@code path} 是<b>注册期路由模板</b>
 *       （{@code rest/engine.go:149} 传的是 {@code route.Path}）⇒ 同模板下的不同具体 URI
 *       （{@code /article/1} 与 {@code /article/2}）<b>共用</b>一个 breaker。
 *       本类原先用 raw {@code getRequestURI()} ⇒ 每个具体 URI 各一个 ⇒
 *       <b>熔断爆炸半径与 Go 不同</b>（Go 会把同模板的兄弟 URI 一起挡住，RAG 不会）。
 *       ⇒ 现改为：优先用 {@link GoStyleRouteTemplateResolver} 解析出的模板；
 *       <b>解析不出来时回落 raw URI</b>（= 旧行为，不是新语义）。
 *       <br>⚠️ 另：Go 侧<b>未命中路由（404 / 405）根本不经过 breaker</b>
 *       （{@code rest/engine.go:244-268} 的 404 链只有 Trace + Log）⇒ 本类同样跳过。</li>
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
 * <p>⚠️ <b>2026-10-30 · G4 lane：本类属 api 侧 rest 中间件 ⇒ 已挂 {@link com.rag.common.web.ConditionalOnApiSide}</b>
 * ⇒ ⇒ 此前「rpc 域不注册本类」的依据是<b>域侧不扫 {@code com.rag.common} 这道约定</b>（不是本类的条件）
 * ⇒ ⇒ 现由共享层自己兜住 ⇒ ⇒ Go 出处与语义等价论证见该注解。
 */
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
@ConditionalOnApiSide
public class GoStyleBreakerFilter implements Filter {

    private final boolean enabled;
    private final Map<String, GoStyleBreaker> breakers = new ConcurrentHashMap<>();

    /**
     * 「注册期路由模板」解析器；<b>可为 null</b>（本进程没有 Spring MVC ⇒ 没有自动配置注册它）。
     *
     * <p>⚠️ <b>null 不是「没有模板」，而是「判不出来」</b> ⇒ 此时<b>回落旧行为</b>
     * （按 raw URI 分桶、照常进 breaker）⇒ 绝不因为「解析不了」而静默关掉熔断保护。
     */
    private final GoStyleRouteTemplateSource routeTemplateSource;

    public GoStyleBreakerFilter(GoStyleProtectionProperties props,
                                 ObjectProvider<GoStyleRouteTemplateSource> routeTemplateSourceProvider) {
        this.enabled = props.getBreaker().isEnabled();
        this.routeTemplateSource = routeTemplateSourceProvider == null ? null : routeTemplateSourceProvider.getIfAvailable();
    }

    /** 取（或懒建）某路由的 breaker。测试直接调它来观测状态。 */
    GoStyleBreaker breakerFor(HttpServletRequest request) {
        return breakerFor(request, resolveRoute(request));
    }

    /**
     * 取（或懒建）某路由的 breaker；<b>复用已解析出的结果</b>。
     *
     * <p>⚠️ <b>为什么要带 resolution 参数</b>：解析路由模板要走一遍 Spring 的路由匹配
     * ⇒ 每请求解析<b>两次</b>就是白做一遍匹配 ⇒ ⇒ {@link #doFilter} 解析一次、这里复用。
     */
    private GoStyleBreaker breakerFor(HttpServletRequest request, GoStyleRouteResolution resolution) {
        return breakers.computeIfAbsent(routeKey(request, resolution), GoStyleBreaker::new);
    }

    /**
     * 与 Go {@code breakerhandler.go:21} 的 name 拼接一致。
     *
     * <p><b>⚠️ 键的语义（2026-10-27 改）</b>：Go 用的是<b>注册期路由模板</b>
     * （{@code rest/engine.go:149} 传的是 {@code route.Path}）⇒ 同一模板下的不同具体 URI
     * （如 {@code /article/1} 与 {@code /article/2}）<b>共用</b>一个 breaker。
     * <p>⇒ 本方法优先用模板；<b>解析不出来时回落 raw URI</b>（= 旧行为，不是新语义）。
     */
    String routeKey(HttpServletRequest request) {
        return routeKey(request, resolveRoute(request));
    }

    private String routeKey(HttpServletRequest request, GoStyleRouteResolution resolution) {
        if (resolution.kind() == GoStyleRouteResolution.Kind.TEMPLATE) {
            return request.getMethod() + "://" + resolution.template();
        }
        return request.getMethod() + "://" + request.getRequestURI();
    }

    /**
     * 解析路由模板；三态里只有「确定未命中」会让调用方跳过 breaker。
     */
    private GoStyleRouteResolution resolveRoute(HttpServletRequest request) {
        if (routeTemplateSource == null) {
            return GoStyleRouteResolution.UNKNOWN;
        }
        try {
            GoStyleRouteResolution resolution = routeTemplateSource.resolve(request);
            return resolution == null ? GoStyleRouteResolution.UNKNOWN : resolution;
        } catch (RuntimeException e) {
            // 解析本身抛异常 ⇒ 判不出来 ⇒ 回落旧行为
            return GoStyleRouteResolution.UNKNOWN;
        }
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

        // Go 出处：rest/engine.go:244-268 —— 404 的链只有 Trace + Log，**没有 breaker**
        //          （patrouter.go:73 没匹配上 ⇒ handleNotFound ⇒ notFoundHandler）
        // ⇒ 判定「确定未命中」时，本层**完全不参与**（既不建 breaker，也不计数）
        GoStyleRouteResolution resolution = resolveRoute(request);
        if (resolution.kind() == GoStyleRouteResolution.Kind.NOT_MATCHED) {
            chain.doFilter(req, resp);
            return;
        }

        GoStyleBreaker breaker = breakerFor(request, resolution);
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