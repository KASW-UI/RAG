package com.rag.favorite.favorite.api.interceptor;

import com.rag.favorite.favorite.api.auth.FavoriteJwtSignatureVerifier;
import com.rag.favorite.favorite.api.auth.FavoriteJwtVerificationResult;
import com.rag.favorite.favorite.api.auth.GoStyleJwtAuthFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.method.HandlerMethod;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * favorite-api 的 JWT 鉴权拦截器 —— 承担 Go 端 {@code rest.WithJwt} 的<b>响应面</b>
 * ＋ {@code CheckBlacklistMiddleware} 的<b>黑名单面</b>。
 *
 * <p><b>Go 出处</b>：
 * <ul>
 *   <li>{@code service/favorite/api/internal/handler/routes.go:16-59}
 *       —— 7 个路由<b>全量</b> {@code rest.WithMiddlewares([CheckBlacklistMiddleware], ...)}
 *       + {@code rest.WithJwt(serverCtx.Config.UserAuth.AccessSecret)}；</li>
 *   <li>{@code service/favorite/api/internal/middleware/checkblacklist_middleware.go:22-50}；</li>
 *   <li>{@code service/favorite/api/internal/logic/favorite/helper.go:14-34}
 *       —— {@code extractUserID}（消费 {@code ctx.Value("userId")}）。</li>
 * </ul>
 *
 * <p><b>与 Go 逐条对齐的行为（L1，不可改）</b>：
 * <ol>
 *   <li>Authorization 头缺失 / 为空 → <b>HTTP 401</b></li>
 *   <li>剥掉 {@code Bearer } 后 token 为空 → 401
 *       （Go: {@code golang-jwt/jwt/v4@v4.5.2/request/oauth2.go} 的
 *       {@code stripBearerPrefixFromTokenString}，三条规则见 {@code BearerTokenExtractor}）</li>
 *   <li>签名错 / 过期 / 格式错 → 401。<b>Go 不区分原因</b>，三种失败都只走到同一个
 *       {@code unauthorized()}</li>
 *   <li>验签成功后：把 claim {@code userId} 的<b>原始对象</b>写进 request attribute</li>
 *   <li>token 在 Redis 黑名单（{@code user:jwt_blacklist:<token>}）→ <b>HTTP 401</b></li>
 *   <li>Redis 故障 → <b>不阻塞业务（fail-open）</b></li>
 * </ol>
 *
 * <h3>⚠️ 执行顺序：<b>先验签、后黑名单</b>（与 Go 的 chain 内外关系一致）</h3>
 * <p>Go 出处三段，缺一不可：
 * <ol>
 *   <li>{@code rest/engine.go:101-116} {@code bindRoute}：先 {@code appendAuthHandler(JWT)}，
 *       再 {@code ng.middlewares}，最后 {@code chn.ThenFunc(route.Handler)}；</li>
 *   <li>{@code rest/chain/chain.go} 的 {@code Then} 是 {@code m0(m1(...mN(h)))}，
 *       <b>索引 0 最外层、先执行</b>；{@code Append} 追加到尾部 = 最内层；</li>
 *   <li>{@code rest/server.go:214-233} {@code WithMiddlewares}/{@code WithMiddleware}
 *       在<b>构造期</b>就把 {@code route.Handler} 包成 {@code middleware(route.Handler)}
 *       —— 黑名单是 {@code ThenFunc} 的<b>参数</b>，比 chain 任何元素都内层。</li>
 * </ol>
 * ⇒ 真实顺序：… Timeout → Recover → … → <b>Authorize(JWT)</b> → observability
 * → <b>CheckBlacklist</b> → handler。
 *
 * <p>可观察差异（顺序反了会怎样）：一个<b>签名无效但已在黑名单中</b>的 token，
 * Go → {@code Authorize} 先失败 → 401，<b>黑名单从不查询</b>；
 * 反过来则会对畸形 token 产生一次多余的 Redis 读。
 *
 * <h3>🔴 验签与响应被拆成两段（DRIFT-USERAPI-009②）</h3>
 * <p>Go 侧 3s 预算<b>不覆盖</b>验签（计时器在 {@code Authorize} 之后起算），
 * 而 RAG 侧验签原本内联在本类里（{@code HandlerInterceptor} ⇒ DispatcherServlet <b>内</b>）
 * ⇒ 验签<b>被计入</b> 3s 预算 ⇒ 与 Go 相反。
 * <p>⇒ 修法（与 users/admin 域同款配方）：<b>验签</b>搬进
 * {@code GoStyleJwtAuthFilter}（{@code @Order(+6)} 的 Servlet Filter ⇒ 在 3s 预算的
 * {@code +7} <b>之外</b>），本类只<b>消费</b>它的结果、失败<b>原样重抛</b>；
 * <b>黑名单留在本类</b>（Go 侧黑名单是 route 级中间件 ⇒ 在 3s 预算<b>之内</b> ⇒ 现状本就一致）。
 * <p>⇒ 401 的<b>信封形状逐字不变</b>（异常对象由 Filter 产出 ⇒
 * {@code @ExceptionHandler} 仍然管得到）；「413 压过 401」也不变
 * （本 Filter <b>绝不写响应</b> ⇒ 401 仍发生在所有 Filter 之后）。
 *
 * <h3>⚠️ 黑名单 token 的取法与 Go 的 users 域<b>不同</b>（本域特有，勿"顺手统一"）</h3>
 * <p>Go 侧本域中间件（{@code checkblacklist_middleware.go:30}）是：
 * <pre>
 *   token := strings.TrimSpace(strings.TrimPrefix(authHeader, "Bearer "))
 * </pre>
 * 即「大小写<b>敏感</b>的精确前缀 TrimPrefix」<b>再</b> TrimSpace。
 * 而 users/admin 域（{@code service/user/user/api/internal/middleware/checkblacklistmiddleware.go:28}）
 * 是 {@code strings.TrimPrefix(authHeader, "Bearer ")}，<b>没有</b> TrimSpace。
 * <p>⇒ 本实现刻意<b>不</b>直接复用 {@code BearerTokenExtractor#trimBearerPrefix}，
 * 而是在其结果上再补一次 Go 语义的 TrimSpace（见 {@link FavoriteJwtSignatureVerifier}）。
 *
 * <p>而<b>验签</b>用的取法与 users 域<b>一致</b>（大小写不敏感、缺失前缀时原样返回整串），
 * 因为两边都走 go-zero 的 {@code handler.Authorize}。
 * ⇒ 于是本域也存在 Go 自身的不一致：{@code "bearer <token>"} 验签用 {@code "<token>"}、
 * 黑名单查 {@code "bearer <token>"}。这是 Go 的行为，<b>原样保留</b>。
 */
@Component
public class FavoriteJwtAuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(FavoriteJwtAuthInterceptor.class);

    /**
     * 与 Go {@code authhandler.go:71-79} 里 {@code ctx.Value("userId")} 的 key 同名。
     * 存的是 claim 的<b>原始对象</b>（不是 long），原因见类注释。
     */
    public static final String CLAIM_ATTRIBUTE = "userId";

    /**
     * Go {@code checkblacklist_middleware.go:48} 写进 ctx 的 key
     * （{@code context.WithValue(r.Context(), "jwt_token", token)}）。
     */
    public static final String JWT_TOKEN_ATTRIBUTE = "jwt_token";

    /**
     * Redis 黑名单 key 前缀 —— Go {@code checkblacklist_middleware.go:36}
     * 的 {@code fmt.Sprintf("user:jwt_blacklist:%s", token)}。
     *
     * <p>⚠️ <b>必须是 {@code user:} 而不是 {@code admin:}</b>：Go 侧 favorite/api
     * 用的就是 {@code user:jwt_blacklist:}，与 user-api 的登出写入端同 key
     * （user-rpc 登出写的就是这个 key）⇒ favorite-api 才能拦住已登出的 token。
     */
    private static final String BLACKLIST_KEY_PREFIX = "user:jwt_blacklist:";

    private final FavoriteJwtSignatureVerifier verifier;
    private final StringRedisTemplate redisTemplate;

    public FavoriteJwtAuthInterceptor(FavoriteJwtSignatureVerifier verifier, StringRedisTemplate redisTemplate) {
        this.verifier = verifier;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // ── Go 的「路由匹配先于鉴权」：未注册路径**不经 auth** ──
        // 证据：rest/engine.go:104-118 的 bindRoute 只把 chain（native + auth + 用户中间件）
        //       挂到 router.Handle(method, path, handle) 上，即**只对匹配上的路由生效**；
        //       router/patrouter.go:73,79 的 404 / 405 分支根本进不了这条链。
        //       ⇒ Go 侧 /favorite/v1/nope 恒为 404，与有没有 token 无关。
        //
        // ⚠️ Spring 侧默认**做不到**：Boot 的静态资源处理器匹配 `/**`，
        //    于是未注册路径**也拿到了 handler**，mappedHandler != null ⇒ 拦截器照跑
        //    ⇒ 404 被鉴权遮蔽成 401。
        //
        // 判据用 `handler instanceof HandlerMethod`：它精确对应 Go 那句
        // 「有没有匹配上 @RequestMapping 方法」——ResourceHttpRequestHandler 不是 HandlerMethod。
        //
        // ⚠️ 本条判据**只管「要不要拦」**；「要不要验签」是另一条判据
        //    （{@code FavoriteJwtAuthPaths#isProtected}，由 Filter 使用）⇒ 两者刻意不同源。
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        String uri = request.getRequestURI();

        // ================= 阶段 1：JWT 验签 =================
        // 结果由 GoStyleJwtAuthFilter 在 3s 预算**之外**产出（本类只消费，不自己验签）。
        FavoriteJwtVerificationResult result = (FavoriteJwtVerificationResult) request.getAttribute(
                GoStyleJwtAuthFilter.VERIFICATION_ATTRIBUTE);
        if (result == null) {
            // 🔴 兜底：Filter 未命中（路径判据不一致 / Filter 未被注册）⇒ 就地验签。
            //   行为与 DRIFT-USERAPI-009② 修复前完全一致（401 照常）⇒ **不会造成鉴权绕过**；
            //   但验签落回 3s 预算**之内** ⇒ 该路径上 ② 未闭环 ⇒ 必须大声记日志。
            log.warn("favorite JWT 验签未命中 filter 缓存 ⇒ 就地验签（DRIFT-USERAPI-009② 在此路径未闭环）: {}", uri);
            request.setAttribute(GoStyleJwtAuthFilter.VERIFICATION_SOURCE_ATTRIBUTE,
                    GoStyleJwtAuthFilter.SOURCE_INLINE);
            result = verifier.verify(request.getHeader("Authorization"), uri);
        } else {
            request.setAttribute(GoStyleJwtAuthFilter.VERIFICATION_SOURCE_ATTRIBUTE,
                    GoStyleJwtAuthFilter.SOURCE_FILTER);
        }

        // 验签失败 ⇒ **原样重抛**（异常对象由 Filter 产出 ⇒ @ExceptionHandler 仍管得到
        // ⇒ 401 + 三字段信封的形状逐字不变；本域已签字的 DEV-FAV-API-001 不受影响）。
        if (result.failed()) {
            throw result.failure();
        }

        // Go: authhandler.go:71-79 遍历 claims，把非标准 claim（aud/exp/jti/iat/iss/nbf/sub 除外）
        //     逐个塞进 ctx。favorite api 侧只有 7 个 logic 的 ctx.Value("userId") 读它，
        //     其余 claim 无消费者，故这里只落这一个。
        // ⚠️ 落的是 claim 的**原始对象**（不是 long）⇒ 解析成 int64 是业务层的事
        //    （FavoriteApiSupport#userId，失败落 HTTP 200 + {code:5004}，**不是 401**）。
        request.setAttribute(CLAIM_ATTRIBUTE, result.claims().get(CLAIM_ATTRIBUTE));

        // ================= 阶段 2：黑名单检查 =================
        // Go checkblacklist_middleware.go:22-50：
        //   authHeader := r.Header.Get("Authorization"); if authHeader == "" { next; return }
        //   token := strings.TrimSpace(strings.TrimPrefix(authHeader, "Bearer "))
        //   if token == "" { next; return }
        //   exists, err := m.Redis.ExistsCtx(r.Context(), blackListKey)
        //   if err == nil && exists { 401 + body }
        //   ctx := context.WithValue(r.Context(), "jwt_token", token)
        //
        // 注意：Go 内部【两段取法本就不同】——Authorize 用大小写不敏感的 strip，
        // 这里用大小写敏感的 TrimPrefix + TrimSpace。故 "bearer <token>" 在 Go 里
        // 验签用 "<token>"、黑名单查 "bearer <token>"。这是 Go 自身的不一致，本实现原样保留。
        //
        // ⚠️ 黑名单**留在本类**（不在 Filter 里）：Go 侧它是 route 级中间件
        //    ⇒ 在 observability(3s) **之内** ⇒ RAG 现状本就一致 ⇒ 别动。
        String jwtToken = result.trimmedToken();
        if (!jwtToken.isEmpty() && isBlacklisted(jwtToken)) {
            log.warn("favorite token in blacklist, uri={}", uri);
            throw FavoriteJwtSignatureVerifier.unauthorized();
        }

        // Go 只有在 header 非空且 TrimPrefix 结果非空时才写 jwt_token；
        // 上面已排除两种为空的分支，此处必然写入。
        request.setAttribute(JWT_TOKEN_ATTRIBUTE, jwtToken);
        if (!jwtToken.equals(result.signatureToken())) {
            // 与 Go 一致的不一致：非 "Bearer <token>" 规范写法下两段取法不同。保留 warn 便于排查。
            log.warn("favorite blacklist token != jwt token (auth header is not 'Bearer <token>')");
        }
        return true;
    }

    /**
     * Redis 黑名单查询 —— fail-open 与 Go 逐条对齐。
     *
     * <p>Go: {@code exists, err := m.Redis.ExistsCtx(...); if err == nil && exists { 401 }}
     * （{@code service/favorite/api/internal/middleware/checkblacklist_middleware.go:37}）。
     * Redis 故障时 {@code err != nil} ⇒ 条件为 false ⇒ <b>放行</b>，不阻塞业务。
     * 本实现把异常吞成 {@code null}，随后 {@code exists != null && exists} 同样为 false ⇒ 放行。
     */
    private boolean isBlacklisted(String token) {
        String blacklistKey = BLACKLIST_KEY_PREFIX + token;
        Boolean exists;
        try {
            exists = redisTemplate.hasKey(blacklistKey);
        } catch (Throwable t) {
            log.warn("favorite Redis blacklist check failed : {}", t.getMessage());
            exists = null;
        }
        return exists != null && exists;
    }
}
