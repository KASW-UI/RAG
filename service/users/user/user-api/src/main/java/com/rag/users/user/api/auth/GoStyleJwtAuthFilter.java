package com.rag.users.user.api.auth;

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
 * 🔴 <b>DRIFT-USERAPI-009②</b>：把 JWT <b>验签</b>搬出 3s 预算 —— 对齐 Go 的链序。
 *
 * <h2>Go 侧链序（真源，逐行核实 go-zero@v1.10.2）</h2>
 * <pre>
 *   rest/engine.go:101-113  bindRoute：
 *     chn = ng.buildChainWithNativeMiddlewares(...)   // 原生中间件（索引 0 = 最外层）
 *     chn = ng.appendAuthHandler(fr, chn, verifier)   // ← JWT 验签
 *     for _, mw := range ng.middlewares { ... }        // ← observability(3s) 在 JWT **之内**
 *     handle = chn.ThenFunc(route.Handler)
 *   rest/chain/chain.go:38-43  Append = 「加在请求流的最后」⇒ 越 Append 越靠内
 *
 *   ⇒ 执行序：原生中间件 → **JWT 验签** → **observability(3s 计时)** → 黑名单 → handler
 *   ⇒ 3s 计时器在 Authorize **之后**起算 ⇒ **JWT 验签耗时不计入 3s 预算**
 * </pre>
 *
 * <h2>为什么 Java 侧必须用 Filter</h2>
 *
 * <p>Servlet 的<b>所有</b> {@code Filter} 都在 {@code DispatcherServlet} <b>外面</b>，
 * {@code HandlerInterceptor} 在<b>里面</b> ⇒ 验签要出 3s 预算，就必须是 Filter
 * （{@code HttpMiddlewareFilter} 也是 Filter ⇒ 数值更小的 {@code @Order} 先执行 ⇒ 包在它外面）。
 *
 * <h2>🔴 本 Filter 的顺序为什么是 {@code HIGHEST_PRECEDENCE + 6}</h2>
 *
 * <p>共享层（{@code service/common}，G4 写权）已占用的槽位（<b>实测值</b>，非台账旧值）：
 * <pre>
 *   +3  GoStyleTraceparentFilter    （起 SERVER span）
 *   +4  GoStyleMaxConnsFilter
 *   +5  GoStyleBreakerFilter
 *   +6  GoStyleCpuSheddingFilter
 *   +7  HttpMiddlewareFilter         （3s 预算在这里起算）
 *   +8  GoStyleMaxBytesFilter
 * </pre>
 *
 * <p>本 Filter 的合法窗口是 <b>[+3, +6]</b>，两端各有一条硬约束：
 * <ul>
 *   <li><b>下界 +3</b>：{@code GoStyleTraceparentFilter}(+3) 会起一个 <b>SERVER span</b>
 *       ⇒ Go 侧 TraceHandler 在链首、JWT 验签在 span <b>之内</b>
 *       ⇒ 本 Filter 必须落在 traceparent <b>之后</b>，否则验签跑到 span 外
 *       ⇒ <b>观测面差异</b>（span 不覆盖验签）。</li>
 *   <li><b>上界 +6</b>：{@code HttpMiddlewareFilter}(+7) 在 <b>submit worker 线程之前</b>起算 deadline
 *       ⇒ 落在 +7 之后 = 验签仍在预算内 ⇒ <b>DRIFT-USERAPI-009② 不闭环</b>。</li>
 * </ul>
 *
 * <p>⇒ [+3, +6] 四个槽位<b>全被共享层占满</b> ⇒ 本 Filter 与
 * {@code GoStyleCpuSheddingFilter}(+6) <b>并列</b>。
 * <p>⚠️ <b>并列是否安全？</b> —— <b>安全</b>，因为<b>本 Filter 绝不写响应</b>（见下）⇒
 * 无论谁先谁后，<b>响应结果完全相同</b>。
 * <p>⚠️ 与 Go 的<b>残余差异</b>（如实登记，不掩饰）：Go 的 JWT 验签排在
 * MaxConns / Breaker / Shedding <b>之后</b> ⇒ 过载被拒的请求<b>不会</b>做验签；
 * 本 Filter 与 Shedding 并列 ⇒ 并列落在 Shedding 之前时，多做一次<b>无用验签</b>
 * （<b>不改变任何响应</b>，只是多一次 HMAC 计算）。
 * ⇒ 这条差异<b>只在过载时可见</b>，且<b>只影响 CPU，不影响 wire</b>。
 *
 * <h2>🔴🔴 本 Filter <b>绝不写响应</b>（本类最吃重的不变式）</h2>
 *
 * <p>若本 Filter 在验签失败时<b>自己</b>写 401，则：
 * <ul>
 *   <li>本 Filter(+6) 排在 {@code GoStyleMaxBytesFilter}(+8) <b>之前</b>
 *       ⇒ <b>401 会压过 413</b>；</li>
 *   <li>而 Go 侧 MaxBytes 是 native middleware，排在 Authorize <b>之外</b>（更外层）
 *       ⇒ Go 是 <b>413 压过 401</b>。</li>
 * </ul>
 * ⇒ 那会把「413 压过 401」这条<b>已被变异测试锁死</b>的语义（见
 * {@code GoStyleMaxBytesFilter} 的类注释 + {@code UserApiProtectionMvcTest}）变成新漂移。
 * <p>⇒ <b>所以本 Filter 只验签、只把结果放进 request attribute、然后放行</b>；
 * 401 仍由 {@code JwtAuthInterceptor} 抛 ⇒
 * <ul>
 *   <li>{@code @ExceptionHandler}（{@code GlobalExceptionHandler}）仍然管得到
 *       ⇒ <b>401 + 完整信封</b>的形状逐字不变（N-1 签字项不受影响）；</li>
 *   <li>401 发生在所有 Filter <b>之后</b> ⇒ MaxBytes 的 413 仍然压过 401（与 Go 一致）。</li>
 * </ul>
 * ⇒ ⚠️ <b>改本类时若要写响应，必须同时重做「413 压过 401」的语义论证。</b>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 6)
public class GoStyleJwtAuthFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(GoStyleJwtAuthFilter.class);

    /** 验签结果在 request attribute 里的键（由本 Filter 写、由 {@code JwtAuthInterceptor} 读）。 */
    public static final String VERIFICATION_ATTRIBUTE = "com.rag.users.user.api.jwt.verification";

    /**
     * 验签<b>来源</b>标记（{@code filter} / {@code inline}）—— 供守门测试断言
     * 「interceptor 确实消费了 Filter 的结果」（否则 DRIFT-USERAPI-009② 会静默不闭环）。
     */
    public static final String VERIFICATION_SOURCE_ATTRIBUTE = "com.rag.users.user.api.jwt.verificationSource";

    /** 来源 = 本 Filter（验签在 3s 预算之外）。 */
    public static final String SOURCE_FILTER = "filter";

    /** 来源 = interceptor 兜底就地验签（验签在 3s 预算之内 ⇒ ② 未闭环）。 */
    public static final String SOURCE_INLINE = "inline";

    private final JwtSignatureVerifier verifier;

    public GoStyleJwtAuthFilter(JwtSignatureVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        String uri = request.getRequestURI();
        if (JwtAuthPaths.isProtected(uri)) {
            JwtVerificationResult result = verifier.verify(request.getHeader("Authorization"), uri);
            request.setAttribute(VERIFICATION_ATTRIBUTE, result);
            request.setAttribute(VERIFICATION_SOURCE_ATTRIBUTE, SOURCE_FILTER);
            if (result.failed()) {
                // 🔴 只记录，**不写响应**（见类注释「本 Filter 绝不写响应」）。
                log.debug("JWT verification failed (deferred to interceptor) : {}", uri);
            }
        }
        // 🔴 无条件放行：验签失败也要继续走完 Filter 链 ⇒
        //    MaxBytes(+8) 仍有机会写 413（Go 侧 MaxBytes 在 Authorize 之外 ⇒ 413 压过 401），
        //    最终的 401 由 interceptor 抛（@ExceptionHandler 仍管得到 ⇒ 信封形状不变）。
        chain.doFilter(req, resp);
    }
}
