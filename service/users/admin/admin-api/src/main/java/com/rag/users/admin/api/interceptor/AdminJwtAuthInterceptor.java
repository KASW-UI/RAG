package com.rag.users.admin.api.interceptor;

import com.rag.common.exception.AuthenticationException;
import com.rag.users.admin.api.auth.AdminJwtAuthPaths;
import com.rag.users.admin.api.auth.AdminJwtSignatureVerifier;
import com.rag.users.admin.api.auth.AdminJwtVerificationResult;
import com.rag.users.admin.api.auth.GoStyleJwtAuthFilter;
import com.rag.users.common.errmsg.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Admin JWT 鉴权拦截器 —— 承担 Go 端 {@code rest.WithJwt}（<b>验签结果消费</b>）+
 * {@code CheckBlacklistMiddleware}（<b>黑名单</b>）两段中间件的后半程。
 *
 * 对应 Go: service/user/admin/api/internal/handler/routes.go 的第二段 AddRoutes
 * （{@code rest.WithMiddlewares([CheckBlacklistMiddleware], ...) + rest.WithJwt(secret)}）。
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
 *       ⇒ <b>401 + 完整信封</b>的形状逐字不变）；</li>
 *   <li><b>黑名单检查</b>（仍在预算<b>之内</b> ⇒ 与 Go 一致
 *       —— Go 的黑名单是 route 级中间件 {@code rest.WithMiddlewares} ⇒
 *       {@code rest/server.go:222-233 WithMiddleware} 把 {@code middleware(route.Handler)}
 *       包在 handler 外 ⇒ 比 chain 任何元素都内层 ⇒ 在 observability(3s) <b>之内</b>）。</li>
 * </ol>
 *
 * <p><b>L1 行为冻结（与 Go 端 1:1）</b>：
 * <ol>
 *   <li>public 路径（{@code /admin/v1/login}、{@code /admin/v1/create}）：<b>直接放行</b>，不校验 token</li>
 *   <li>protected 路径（其余 13 个）—— 验签由 {@link GoStyleJwtAuthFilter} 完成，
 *     本类消费结果，失败一律 <b>HTTP 401</b>
 *     （抛 {@code AuthenticationException}，与 user-api 同款，见 DRIFT-004）：
 *     <ul>
 *       <li>Authorization 头缺失 / 为空 → 401（验签阶段）</li>
 *       <li>剥掉 {@code Bearer } 后 token 为空 → 401（验签阶段）</li>
 *       <li>JWT 签名错 / 过期 → 401（验签阶段）</li>
 *       <li>claim.userId 缺失或类型不对 → 401（验签阶段）</li>
 *       <li>Redis blacklist（{@code admin:jwt_blacklist:<token>}）存在 → 401 + <b>不</b>进下游（黑名单阶段）</li>
 *       <li>Redis 故障 → 不阻塞业务（fail-open）</li>
 *     </ul>
 *     验签阶段任一失败都<b>不</b>写 userId / jwt_token attribute，也<b>不查 Redis</b>（与 Go 一致）。
 *   </li>
 *   <li><b>{@code uid <= 0} 不在本层拒绝</b>：Go 的 {@code Authorize} 只验签名 + exp，
 *       {@code uid > 0} 是 identity 层的业务规则（{@code service/user/user/identity/resolve.go:75-77}），
 *       由 {@code IdentityResolver.claimedUID} 承担 → HTTP 200 + {@code {code:1006}}。
 *       <b>本类此前在此处抛 {@code BusinessException(1006)}</b>（DRIFT-010），已按 Go 出处移出，
 *       与 user-api 的层次划分统一。</li>
 * </ol>
 *
 * <p><b>已回退的两个 DRIFT（2026-10-21，issue #71186893）</b>：
 * <ul>
 *   <li><b>DRIFT-009</b>：执行顺序此前被改成「黑名单先、验签后」，其 javadoc 给出的 Go 出处
 *       （{@code CheckBlacklist(Jwt(handler))}）读反了 chain 的内外关系。已改回验签优先。</li>
 *   <li><b>DRIFT-010</b>：token 提取此前用 {@code split(" ", 2).parts[1]}，
 *       对 {@code "XXBearer eyJ..."} 会取出 {@code "eyJ..."} 去验签，
 *       而 Go 的 {@code stripBearerPrefixFromTokenString} 在前缀不匹配时<b>原样返回整串</b>
 *       （必然验签失败）。叠加黑名单用整串 {@code TrimPrefix} 查不到，
 *       构成<b>注销绕过</b>。已按 Go 逐条重写为 {@code BearerTokenExtractor} 的两个方法。</li>
 * </ul>
 */
@Component
public class AdminJwtAuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AdminJwtAuthInterceptor.class);

    /** Redis blacklist key 前缀（与 Go 端 1:1：admin:jwt_blacklist:）。 */
    private static final String BLACKLIST_KEY_PREFIX = "admin:jwt_blacklist:";

    private final AdminJwtSignatureVerifier verifier;
    private final StringRedisTemplate redisTemplate;

    public AdminJwtAuthInterceptor(AdminJwtSignatureVerifier verifier, StringRedisTemplate redisTemplate) {
        // ⚠️ 密钥构造已移入 {@link AdminJwtSignatureVerifier}（验签搬进 Filter 后的归属）；
        //   L4 idiomatization 的 Go ↔ RAG 等价性论证见该类的构造器注释（内容未变）。
        this.verifier = verifier;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String uri = request.getRequestURI();

        if (AdminJwtAuthPaths.isPublic(uri)) {
            return true;
        }

        // ==================================================================
        // 阶段 1：JWT 验签（结果由 GoStyleJwtAuthFilter 在 3s 预算之外产出）
        //
        // Go 出处（三段，缺一不可，顺序是结论的关键）：
        //   1. rest/engine.go:101-113 bindRoute：先 appendAuthHandler(JWT)，再 ng.middlewares，
        //      最后 chn.ThenFunc(route.Handler)
        //   2. rest/chain/chain.go 的 Then 是 m0(m1(...mN(h)))，**索引 0 最外层、先执行**；
        //      Append 追加到尾部 = 最内层
        //   3. rest/server.go:222-233 WithMiddleware 在**构造期**就把 route.Handler 包成
        //      middleware(route.Handler) —— 黑名单是 ThenFunc 的**参数**，比 chain 任何元素都内层
        //
        // ⇒ 真实顺序：… Timeout → Recover → … → **Authorize(JWT)** → signature → observability
        //            → **CheckBlacklist** → handler
        //
        // 【DRIFT-009 已回退】本类此前实现的是「黑名单先、验签后」，原注释写
        // 「withMiddlewares 反向包裹 ⇒ CheckBlacklist(Jwt(handler))，黑名单是外层」——
        // 前半句（Append 到尾部 = 最内层）成立，后半句错：**黑名单根本不在 chain 里**，
        // 它是 ThenFunc 的参数，比 chain 任何元素都靠内。与 user-api 的 JwtAuthInterceptor
        // （验签→黑名单，与 Go 一致）自相矛盾。现已改回验签优先。
        //
        // 可观察差异（修复前）：一个**签名无效但已在黑名单中**的 token，
        //   Go   → Authorize 先失败 → 401 + 空 body，黑名单**从不查询**
        //   RAG  → 先打一次 Redis hasKey → 401 + envelope{code:1006}
        // 即 Go 不为畸形 token 产生任何 Redis 读，RAG 会；且「已注销」与「令牌异常」两种语义被搅在一起。
        //
        // 【DRIFT-010 已回退】本类此前还在此处做 `uid <= 0 → BusinessException(1006)`。
        // Go 的 Authorize **不校验 claim 取值**，`uid > 0` 是 identity 层的业务规则
        // （service/user/user/identity/resolve.go:75-77），user-api 已按该出处把判断交给
        // IdentityResolver.claimedUID。此处一并移出，消除两模块的层次不对称。
        // ==================================================================

        AdminJwtVerificationResult result = (AdminJwtVerificationResult) request.getAttribute(
                GoStyleJwtAuthFilter.VERIFICATION_ATTRIBUTE);
        if (result == null) {
            // 🔴 兜底：Filter 未命中（路径判据不一致 / Filter 未被注册）⇒ 就地验签。
            //   行为与 DRIFT-USERAPI-009② 修复前完全一致（401 照常）⇒ **不会造成鉴权绕过**；
            //   但验签落回 3s 预算**之内** ⇒ 该路径上 ② 未闭环 ⇒ 必须大声记日志。
            log.warn("admin JWT 验签未命中 filter 缓存 ⇒ 就地验签（DRIFT-USERAPI-009② 在此路径未闭环）: {}", uri);
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

        // claim 可用才写 userId attribute
        // ⚠️ admin 侧与 user 侧**刻意不同**：畸形 claim 在 admin 侧是 **401**（由 verifier 产出 failure），
        //   而 user 侧按 DRIFT-USERAPI-001 改成「不拒绝、交给 identity 层」⇒ **不在此统一两域**。
        request.setAttribute("userId", Long.valueOf(result.uid()));

        // ---------- 阶段 2：黑名单检查（对应 Go CheckBlacklistMiddleware.Handle）----------
        // Go checkblacklistmiddleware.go:22-44：
        //   authHeader := r.Header.Get("Authorization"); if authHeader == "" { next; return }
        //   token := strings.TrimPrefix(authHeader, "Bearer ")   ← 大小写【敏感】的精确前缀
        //   if token == "" { next; return }
        //   exists, err := Redis.ExistsCtx(key); if err == nil && exists { 401 + body }
        //   ctx := context.WithValue(ctx, "jwt_token", token)   ← 写的是 TrimPrefix 结果
        //
        // 注意 Go 内部【两段取法本就不同】：Authorize 用大小写不敏感的 strip，
        // 这里用大小写敏感的 TrimPrefix。故 "bearer <token>" 在 Go 里
        // 验签用 "<token>"、黑名单查 "bearer <token>"。这是 Go 自身的不一致，本实现原样保留。
        String jwtToken = result.trimmedToken();
        if (!jwtToken.isEmpty() && isBlacklisted(jwtToken)) {
            log.warn("admin token in blacklist, uri={}", uri);
            throw new AuthenticationException(
                    ErrorCode.ERROR_TOKEN_RUNTIME.getCode(),
                    ErrorCode.ERROR_TOKEN_RUNTIME.getMsg());
        }

        // Go 只有在 header 非空且 TrimPrefix 结果非空时才写 jwt_token；
        // 上面已排除两种为空的分支，此处必然写入。
        request.setAttribute("jwt_token", jwtToken);
        if (!jwtToken.equals(result.signatureToken())) {
            // 与 Go 一致的不一致：非 "Bearer " 规范写法下两段取法不同。保留 warn 便于排查。
            log.warn("admin blacklist token != jwt token (auth header is not 'Bearer <token>')");
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
            log.warn("admin Redis blacklist check failed : {}", t.getMessage());
            exists = null;
        }
        return exists != null && exists;
    }
}
