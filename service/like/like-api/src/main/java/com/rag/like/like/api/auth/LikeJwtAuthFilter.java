package com.rag.like.like.api.auth;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 🔴 <b>DRIFT-LIKEAPI-009②</b>：把 JWT <b>验签</b>搬出 3s 预算 —— 对齐 Go 的链序。
 *
 * <h2>Go 侧链序（真源，逐行核实 go-zero@v1.10.2 + like 真源）</h2>
 * <pre>
 *   service/like/api/likecenter.go:36-38
 *     httpTimeout := observability.DisableNativeRestTimeout(&c.RestConf)
 *     server := rest.MustNewServer(c.RestConf)
 *     server.Use(observability.NewHTTPMiddleware(c.Name, httpTimeout, observability.SlowThreshold()))
 *
 *   ⚠️ 第一行是关键：**真源把 go-zero 原生 TimeoutHandler 关掉了**
 *     （service/common/observability/http.go:30-34 ⇒ conf.Middlewares.Timeout = false）
 *     ⇒ 原生 TimeoutHandler **不在链上**；3s 预算实际由**真源自己**的
 *       observability 中间件提供，而它注册在 server.Use ⇒ ng.middlewares。
 *
 *   rest/engine.go:101-116  bindRoute：
 *     chn = ng.buildChainWithNativeMiddlewares(...)   // 原生中间件（索引 0 = 最外层）
 *     chn = ng.appendAuthHandler(fr, chn, verifier)   // ← JWT 验签
 *     for _, mw := range ng.middlewares { ... }        // ← observability(3s 计时) 在 JWT **之内**
 *     handle = chn.ThenFunc(route.Handler)
 *   rest/chain/chain.go:38-42  Append = 「加在请求流的最后」⇒ 越 Append 越靠内
 *
 *   ⇒ 执行序：原生中间件 → **JWT 验签** → **observability(3s 计时)** → handler
 *   ⇒ 3s 计时器在 Authorize **之后**起算 ⇒ **验签耗时不计入 3s 预算**
 * </pre>
 *
 * <h2>为什么 Java 侧必须用 Filter</h2>
 * <p>Servlet 的<b>所有</b> {@code Filter} 都在 {@code DispatcherServlet} <b>外面</b>，
 * {@code HandlerInterceptor} 在<b>里面</b> ⇒ 验签要出 3s 预算，就必须是 Filter
 * （{@code HttpMiddlewareFilter} 也是 Filter ⇒ 数值更小的 {@code @Order} 先执行 ⇒ 包在它外面）。
 * <p>⚠️ <b>RAG 侧的漂移形态</b>（本类要修的就是它）：
 * {@code service/common/observability/HttpMiddleware.java:270/294/304/322}
 * —— 预算在 submit worker <b>之前</b>起算（{@code deadlineNanos = startNanos + timeout}），
 * 而 {@code chain.doFilter} 整条下游（含 DispatcherServlet 与其中的 HandlerInterceptor）
 * 都在 worker 里跑 ⇒ <b>验签耗时会吃掉 3s 预算</b>。
 *
 * <h2>🔴 本 Filter 的顺序为什么是 {@code HIGHEST_PRECEDENCE + 6}</h2>
 * <p>共享层（{@code service/common}，G4 写权）已占用的槽位（<b>实测值</b>，非台账旧值）：
 * <pre>
 *   +3  GoStyleTraceparentFilter    （起 SERVER span）
 *   +4  GoStyleMaxConnsFilter
 *   +5  GoStyleBreakerFilter
 *   +6  GoStyleCpuSheddingFilter
 *   +7  HttpMiddlewareFilter         （3s 预算在这里起算）
 *   +8  GoStyleMaxBytesFilter
 * </pre>
 * <p>本 Filter 的合法窗口是 <b>[+3, +6]</b>：
 * <ul>
 *   <li><b>下界 +3</b>：{@code GoStyleTraceparentFilter}(+3) 会起一个 <b>SERVER span</b>
 *       ⇒ Go 侧 TraceHandler 在链首、JWT 验签在 span <b>之内</b>
 *       ⇒ 本 Filter 必须落在 traceparent <b>之后</b>。</li>
 *   <li><b>上界 +6</b>：{@code HttpMiddlewareFilter}(+7) 在 <b>submit worker 线程之前</b>起算 deadline
 *       ⇒ 落在 +7 之后 = 验签仍在预算内 ⇒ <b>DRIFT-LIKEAPI-009② 不闭环</b>。</li>
 * </ul>
 * <p>⇒ [+3, +6] 四个槽位<b>全被共享层占满</b> ⇒ 本 Filter 与
 * {@code GoStyleCpuSheddingFilter}(+6) <b>并列</b>。
 * <p>⚠️ <b>并列是否安全？</b> —— <b>安全</b>，因为<b>本 Filter 绝不写响应</b>（见下）⇒
 * 无论谁先谁后，<b>响应结果完全相同</b>。
 *
 * <h2>🔴🔴 本 Filter <b>绝不写响应</b>（本类最吃重的不变式）</h2>
 * <p>若本 Filter 在验签失败时<b>自己</b>写 401，则：
 * <ul>
 *   <li>本 Filter(+6) 排在 {@code GoStyleMaxBytesFilter}(+8) <b>之前</b>
 *       ⇒ <b>401 会压过 413</b>；</li>
 *   <li>而 Go 侧 MaxBytes 是 native middleware，排在 Authorize <b>之外</b>（更外层）
 *       ⇒ Go 是 <b>413 压过 401</b>。</li>
 * </ul>
 * ⇒ 那会把「413 压过 401」这条语义变成新漂移。
 * <p>⇒ <b>所以本 Filter 只验签、只把结果放进 request attribute、然后放行</b>；
 * 401 仍由 {@code LikeJwtAuthInterceptor} 抛 ⇒
 * <ul>
 *   <li>{@code @ExceptionHandler}（{@code GlobalExceptionHandler}）仍然管得到
 *       ⇒ <b>401 + 空 body</b>的形状逐字不变；</li>
 *   <li>401 发生在所有 Filter <b>之后</b> ⇒ MaxBytes 的 413 仍然压过 401（与 Go 一致）。</li>
 * </ul>
 *
 * <h2>⚠️ 与 Go 的一处<b>残余差异</b>（如实登记）</h2>
 * <p>Go 的链只挂在<b>路由匹配上</b>（{@code rest/engine.go} 的 {@code router.Handle(method, path, handle)}）
 * ⇒ 未注册的路径（404）**根本进不了链** ⇒ 不验签。
 * <p>Servlet 的 Filter 在路由匹配<b>之前</b> ⇒ 本 Filter 会对 {@code /like/v1/**} 下的
 * <b>未匹配路径</b>也做一次验签。
 * ⇒ 但本 Filter <b>不写响应</b> ⇒ 未匹配路径仍由
 * {@code LikeJwtAuthInterceptor} 的 {@code handler instanceof HandlerMethod} 判据放行
 * ⇒ 最终仍是 <b>404</b>（与 Go 一致）⇒ <b>只是多一次 HMAC 计算</b>
 * ⇒ **只影响 CPU，不影响任何响应**。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 6)
public class LikeJwtAuthFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(LikeJwtAuthFilter.class);

    /**
     * 验签结果在 request attribute 里的键（由本 Filter 写、由 {@code LikeJwtAuthInterceptor} 读）。
     *
     * <p>⚠️ <b>为什么单独一个键</b>：claim 本身可能被合法地「不存在」（token 里没有 userId claim），
     * 而 Go 侧那种情况<b>不是鉴权失败</b>（放行，由业务层判 1006）⇒
     * 不能用「claim attribute 在不在」来表达「验签成功没有」⇒ 必须另用一个键放<b>结果对象</b>。
     */
    public static final String VERIFICATION_ATTRIBUTE = "com.rag.like.like.api.jwt.verification";

    /**
     * 验签<b>来源</b>标记（{@code filter} / {@code inline}）—— 供守门测试断言
     * 「interceptor 确实消费了 Filter 的结果」（否则 DRIFT-LIKEAPI-009② 会静默不闭环）。
     */
    public static final String VERIFICATION_SOURCE_ATTRIBUTE = "com.rag.like.like.api.jwt.verificationSource";

    /** 来源 = 本 Filter（验签在 3s 预算之外）。 */
    public static final String SOURCE_FILTER = "filter";

    /** 来源 = interceptor 兜底就地验签（验签在 3s 预算之内 ⇒ ② 未闭环）。 */
    public static final String SOURCE_INLINE = "inline";

    /** Go 侧路由前缀（{@code rest.WithPrefix("/like/v1")}）。 */
    private static final String LIKE_PREFIX = "/like/v1";

    private final LikeJwtSignatureVerifier verifier;

    public LikeJwtAuthFilter(LikeJwtSignatureVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        String uri = request.getRequestURI();
        if (isProtected(uri)) {
            LikeJwtSignatureVerifier.VerificationResult result =
                    verifier.verify(request.getHeader("Authorization"), uri);
            request.setAttribute(VERIFICATION_ATTRIBUTE, result);
            request.setAttribute(VERIFICATION_SOURCE_ATTRIBUTE, SOURCE_FILTER);
            if (result.failed()) {
                // 🔴 只记录，**不写响应**（见类注释「本 Filter 绝不写响应」）。
                log.debug("like JWT verification failed (deferred to interceptor) : {}", uri);
            }
        }
        // 🔴 无条件放行：验签失败也要继续走完 Filter 链 ⇒
        //    MaxBytes(+8) 仍有机会写 413，最终的 401 由 interceptor 抛。
        chain.doFilter(req, resp);
    }

    /**
     * 该路径是否需要验签。
     *
     * <p>Go 侧 like api <b>6/6 全量</b> {@code rest.WithJwt}，<b>不存在 public 路径</b>
     * ⇒ 判据就是前缀本身，不需要像 user 域那样拆 public/protected 两张表。
     * <p>等价于 Spring 的 {@code /like/v1/**}：匹配 {@code /like/v1} 与 {@code /like/v1/...}，
     * <b>不</b>匹配 {@code /like/v1abc}（那不是本域的路由）。
     */
    public static boolean isProtected(String uri) {
        return LIKE_PREFIX.equals(uri) || uri.startsWith(LIKE_PREFIX + "/");
    }
}
