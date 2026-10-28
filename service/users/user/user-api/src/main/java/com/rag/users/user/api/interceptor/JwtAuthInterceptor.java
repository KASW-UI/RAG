package com.rag.users.user.api.interceptor;

import com.rag.common.exception.AuthenticationException;
import com.rag.users.common.errmsg.ErrorCode;
import com.rag.users.user.api.auth.GoStyleJwtAuthFilter;
import com.rag.users.user.api.auth.JwtAuthPaths;
import com.rag.users.user.api.auth.JwtSignatureVerifier;
import com.rag.users.user.api.auth.JwtVerificationResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 鉴权拦截器 —— 承担 Go 端 {@code rest.WithJwt}（<b>验签结果消费</b>）+
 * {@code CheckBlacklistMiddleware}（<b>黑名单</b>）两段中间件的后半程。
 *
 * 对应 Go: service/user/user/api/internal/middleware/jwtauth.go + checkblacklistmiddleware.go。
 *
 * <h2>🔴 DRIFT-USERAPI-009②：验签已搬进 {@link GoStyleJwtAuthFilter}</h2>
 *
 * <p>Go 侧链序：<b>原生中间件 → JWT 验签 → observability(3s 计时) → 黑名单 → handler</b>
 * （{@code rest/engine.go:101-113} + {@code rest/chain/chain.go:38-43} 的 Append 语义）
 * ⇒ <b>3s 预算不覆盖 JWT 验签</b>。
 * <p>Servlet 侧所有 {@code Filter} 都在 {@code DispatcherServlet} 外面、
 * {@code HandlerInterceptor} 在里面 ⇒ 验签要出预算，必须搬进 Filter
 * ⇒ 验签搬到 {@link GoStyleJwtAuthFilter}（{@code @Order(+6)}，在 3s 预算的 {@code +7} 之外）。
 *
 * <p><b>本类现在只做三件事</b>：
 * <ol>
 *   <li>消费 Filter 放进 request attribute 的<b>验签结果</b>；</li>
 *   <li>把验签失败<b>原样重抛</b>（异常对象由 Filter 产出 ⇒
 *       {@code GlobalExceptionHandler} 的 {@code @ExceptionHandler} 仍管得到
 *       ⇒ <b>401 + 完整信封</b>的形状逐字不变，N-1 签字项不受影响）；</li>
 *   <li><b>黑名单检查</b>（仍在预算<b>之内</b> ⇒ 与 Go 一致）。</li>
 * </ol>
 *
 * <p><b>L1 行为冻结（与 Go 端 1:1）</b>：
 * <ol>
 *   <li>public 路径（{@code /user/v1/login}、{@code /user/v1/register}）→ <b>直接放行</b>，不校验 token</li>
 *   <li>protected 路径（其余 7 个）—— 验签由 {@link GoStyleJwtAuthFilter} 完成，
 *     本类消费结果，失败一律 <b>HTTP 401</b>：
 *     <ul>
 *       <li>Authorization 缺失 / 不以 "Bearer " 开头 → {@code AuthenticationException} → 401</li>
 *       <li>"Bearer " 后 token 为空 → {@code AuthenticationException} → 401</li>
 *       <li>JWT 签名错 / 过期 → {@code AuthenticationException} → 401</li>
 *       <li>Redis blacklist（{@code user:jwt_blacklist:<token>}）存在 → {@code AuthenticationException} → 401</li>
 *       <li>Redis 故障 → 不阻塞业务（与 Go 端 {@code err == nil && exists} 行为一致）</li>
 *     </ul>
 *     以上任一失败都<b>不</b>写 userId / jwt_token attribute。
 *   </li>
 *   <li><b>🔴 claim 的「存在性」与「取值」都不在鉴权层拒绝</b>（2026-10-23 修 DRIFT-USERAPI-001）：
 *       Go 出处 {@code go-zero@v1.10.2 rest/handler/authhandler.go:54-81} ——
 *       只有 {@code ParseToken} 失败与 {@code !tok.Valid} 两条会 401；
 *       之后 {@code authhandler.go:72-79} 把**非标准 claim 逐个塞进 ctx**，
 *       {@code authhandler.go:81} 直接 {@code next.ServeHTTP} ⇒ 请求**到达 handler**。
 *       ⇒ 「签名合法但 userId claim 缺失 / 是字符串」在 Go 侧<b>不是 401</b>，
 *       而是由业务层 {@code identity/resolve.go:70-73} 的 type assertion 失败
 *       ⇒ {@code ErrInvalidClaim} ⇒ handler {@code httpx.OkJson} ⇒
 *       <b>HTTP 200 + 业务码 1006</b>。
 *       验签器现在<b>只记 warn、不写 userId attribute、不抛异常</b>，把判定交给业务层。
 *       ⚠️ 黑名单检查仍在 claim 判定<b>之前/之后都照常执行</b>（Go 的链序是
 *       JWT → 黑名单 → handler），所以「畸形 claim + 已拉黑」两侧都是 401。
 *   </li>
 *   <li><b>uid 取值（{@code uid <= 0}）也不在鉴权层拒绝</b>：Go 的 {@code rest.WithJwt} 同样不校验 claim 取值，
 *       该规则属于 identity 层（{@code identity/resolve.go} {@code ClaimedUID}），
 *       由 {@code IdentityResolver.claimedUID} 承担，失败表现为 <b>HTTP 200 + 业务码 1006</b>。
 *       ⚠️ 但 Go 侧的 {@code uid <= 0} 规则<b>分端点</b>：{@code get} / {@code avatar/*} 拒绝，
 *       {@code delete} / {@code update} <b>不</b>拒绝（见 {@code IdentityResolver.claimedUIDAllowNonPositive}）。
 *   </li>
 * </ol>
 *
 * <p><b>L4 实现差异</b>：
 * <ul>
 *   <li>Go 端用两个 {@code rest.Middleware}（go-zero 风格）；Java 端拆成
 *       {@code Filter}（验签）+ {@code HandlerInterceptor}（黑名单 + 抛异常），
 *       以便把验签移出 3s 预算（DRIFT-USERAPI-009②）；</li>
 *   <li>JWT 算法 HS256 + HMAC-SHA256（与 users-common/JwtUtil 1:1 对齐）</li>
 *   <li>Redis key 格式 {@code user:jwt_blacklist:<token>} 1:1 对齐 Go 端
 *       {@code fmt.Sprintf("user:jwt_blacklist:%s", token)}</li>
 * </ul>
 */
@Component
public class JwtAuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthInterceptor.class);

    /** Redis blacklist key 前缀（与 Go 端 1:1）。 */
    private static final String BLACKLIST_KEY_PREFIX = "user:jwt_blacklist:";

    private final JwtSignatureVerifier verifier;

    private final StringRedisTemplate redisTemplate;

    public JwtAuthInterceptor(JwtSignatureVerifier verifier, StringRedisTemplate redisTemplate) {
        this.verifier = verifier;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String uri = request.getRequestURI();

        // public 路径直接放行（与 Go 端 rest.AddRoutes(login, register) 不带 WithJwt 一致）
        if (JwtAuthPaths.isPublic(uri)) {
            return true;
        }

        // ---------- 阶段 1：JWT 验签（结果由 GoStyleJwtAuthFilter 在 3s 预算之外产出）----------
        // Go 出处：golang-jwt/jwt/v4@v4.5.2/request/oauth2.go:7-12
        //   stripBearerPrefixFromTokenString：前缀【大小写不敏感】（strings.ToUpper 比较），
        //   且【不匹配时原样返回整串】而不是报错。
        // 与 admin 侧共用 BearerTokenExtractor，避免两份实现各自演化出 DRIFT-010 那类缺陷。
        JwtVerificationResult result = (JwtVerificationResult) request.getAttribute(
                GoStyleJwtAuthFilter.VERIFICATION_ATTRIBUTE);
        if (result == null) {
            // 🔴 兜底：Filter 未命中（路径判据不一致 / Filter 未被注册）⇒ 就地验签。
            //   行为与 DRIFT-USERAPI-009② 修复前完全一致（401 照常）⇒ **不会造成鉴权绕过**；
            //   但验签落回 3s 预算**之内** ⇒ 该路径上 ② 未闭环 ⇒ 必须大声记日志。
            log.warn("JWT 验签未命中 filter 缓存 ⇒ 就地验签（DRIFT-USERAPI-009② 在此路径未闭环）: {}", uri);
            request.setAttribute(GoStyleJwtAuthFilter.VERIFICATION_SOURCE_ATTRIBUTE,
                    GoStyleJwtAuthFilter.SOURCE_INLINE);
            result = verifier.verify(request.getHeader("Authorization"), uri);
        } else {
            request.setAttribute(GoStyleJwtAuthFilter.VERIFICATION_SOURCE_ATTRIBUTE,
                    GoStyleJwtAuthFilter.SOURCE_FILTER);
        }

        // 验签失败 ⇒ 原样重抛（异常对象由 Filter 产出 ⇒ @ExceptionHandler 仍管得到 ⇒ 401 信封形状不变）
        if (result.failed()) {
            throw result.failure();
        }

        // claim 可用才写 userId attribute（与 Go 的「逐个 claim 塞进 ctx」等价）
        if (result.claimUsable()) {
            request.setAttribute("userId", Long.valueOf(result.uid()));
        }

        // ---------- 阶段 2：黑名单检查（对应 Go CheckBlacklistMiddleware.Handle）----------
        // Go 出处 checkblacklistmiddleware.go:22-44：header 为空 → 直接放行；
        // TrimPrefix 后为空 → 直接放行且**不写 jwt_token**；否则查 Redis，
        // 命中即 401；未命中则把【TrimPrefix 结果】写进 ctx["jwt_token"]。
        //
        // 顺序说明：Go 的 Authorize 排在黑名单之前
        // （rest/engine.go:101-113 bindRoute 先 appendAuthHandler 再 ThenFunc(route.Handler)，
        //  而 chain.Then = m0(m1(...mN(h)) 使索引 0 最外层先执行；黑名单被
        //  rest/server.go:222-233 WithMiddleware 包进 route.Handler，是 ThenFunc 的参数）。
        // ⇒ 验签失败时黑名单**从不被查**，Go 也不为畸形 token 产生任何 Redis 读。
        String jwtToken = result.trimmedToken();
        if (!jwtToken.isEmpty() && isBlacklisted(jwtToken)) {
            log.warn("token in blacklist, uid={}", result.claimUsable() ? result.uid() : "n/a");
            throw new AuthenticationException(
                    ErrorCode.ERROR_TOKEN_RUNTIME.getCode(),
                    ErrorCode.ERROR_TOKEN_RUNTIME.getMsg()
            );
        }

        request.setAttribute("jwt_token", jwtToken);
        if (!jwtToken.equals(result.signatureToken())) {
            // 与 Go 一致的不一致：非 "Bearer " 规范写法下两段取法不同（Go 自身如此）。保留 warn 便于排查。
            log.warn("user blacklist token != jwt token (auth header is not 'Bearer <token>')");
        }
        return true;
    }

    /**
     * Redis 黑名单查询 —— fail-open 与 Go 逐条对齐。
     *
     * <p>Go: {@code exists, err := m.Redis.ExistsCtx(...); if err == nil && exists { 401 }}。
     * Redis 故障时 {@code err != nil} ⇒ 条件为 false ⇒ <b>放行</b>，不阻塞业务。
     * 本实现把异常吞成 {@code null}，随后 {@code exists != null && exists} 同样为 false ⇒ 放行。
     */
    private boolean isBlacklisted(String token) {
        String blacklistKey = BLACKLIST_KEY_PREFIX + token;
        Boolean exists;
        try {
            exists = redisTemplate.hasKey(blacklistKey);
        } catch (Throwable t) {
            log.warn("Redis blacklist check failed : {}", t.getMessage());
            exists = null;
        }
        return exists != null && exists;
    }
}
