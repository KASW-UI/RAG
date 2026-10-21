package com.rag.users.admin.api.interceptor;

import com.rag.common.exception.AuthenticationException;
import com.rag.common.exception.BusinessException;
import com.rag.users.admin.api.config.AdminApiProperties;
import com.rag.users.common.errmsg.ErrorCode;
import com.rag.users.common.jwt.BearerTokenExtractor;
import com.rag.users.common.jwt.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.crypto.SecretKey;
import java.util.Set;

/**
 * Admin JWT 鉴权拦截器 —— 合并 Go 端 {@code rest.WithJwt} + {@code CheckBlacklistMiddleware} 两段中间件的职责。
 *
 * 对应 Go: {@code service/user/admin/api/internal/handler/routes.go} 的第二段 AddRoutes
 * （{@code rest.WithMiddlewares([CheckBlacklistMiddleware], ...) + rest.WithJwt(secret)}）。
 *
 * <p><b>L1 行为冻结（与 Go 端 1:1）</b>：
 * <ol>
 *   <li>public 路径（{@code /admin/v1/login}、{@code /admin/v1/create}）：<b>直接放行</b>，不校验 token</li>
 *   <li>protected 路径（其余 11 个）—— <b>严格按 Go 的两段顺序：先验签、后黑名单</b>
 *     （三段取证见 {@link #preHandle} 内的注释），失败一律 <b>HTTP 401</b>
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
 *       构成<b>注销绕过</b>。已按 Go 逐条重写为 {@link #tokenForSignatureVerification}。</li>
 * </ul>
 */
@Component
public class AdminJwtAuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AdminJwtAuthInterceptor.class);

    /** public 路径前缀（HUMAN_ONLY.MD 决策：login / create 不挂 JWT）。 */
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/admin/v1/login", "/admin/v1/create"
    );

    /** Redis blacklist key 前缀（与 Go 端 1:1：admin:jwt_blacklist:）。 */
    private static final String BLACKLIST_KEY_PREFIX = "admin:jwt_blacklist:";

    private final SecretKey key;
    private final StringRedisTemplate redisTemplate;

    public AdminJwtAuthInterceptor(AdminApiProperties properties, StringRedisTemplate redisTemplate) {
        // L4 idiomatization（强制留痕 + Go ↔ RAG 对照）：
        //   Go 出处：service/user/common/jwt/jwt.go 的 `token.SignedString([]byte(secretKey))`
        //     —— Go 端 golang-jwt 不校验 key 长度，15 字节 admin secret 能直接签。
        //   Java 表达差异：jjwt 0.12 强制 ≥32 字节（HS256 / RFC 7518），故拦截器构造器
        //     调 Keys.hmacShaKeyFor 时必须先补齐；本构造器与 JwtUtil.getToken 共用
        //     JwtUtil.padToHmacKeySize，强制签发与验签落在同一份 32 字节 key 上，
        //     否则 admin-api 在默认 15 字节密钥下 Bean 创建即抛 WeakKeyException。
        //   语义等价论证：padding 是 zero-fill，落到 HS256 上字节一致 → token 形状、
        //     claim、exp 全一致；Go ↔ RAG 仅在 key 长度这一非行为字段上收敛。
        this.key = Keys.hmacShaKeyFor(JwtUtil.padToHmacKeySize(properties.getAdminAuth().getAccessSecret()));
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String uri = request.getRequestURI();

        if (PUBLIC_PATHS.contains(uri)) {
            return true;
        }

        // ==================================================================
        // 阶段 1：JWT 验签（对应 go-zero rest.WithJwt → rest/handler/authhandler.go Authorize）
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

        String authHeader = request.getHeader("Authorization");
        // Go: rest/token/tokenparser.go doParseToken → request.ParseFromRequest(r,
        //        request.AuthorizationHeaderExtractor, ...)
        // AuthorizationHeaderExtractor 定义在 golang-jwt/jwt/v4@v4.5.2/request/oauth2.go:7-12：
        //   if len(tok) > 6 && strings.ToUpper(tok[0:7]) == "BEARER " { return tok[7:], nil }
        //   return tok, nil        ← 【无 "Bearer " 前缀时原样返回整串，不报错】
        // 即前缀【大小写不敏感】，且缺失前缀【不是直接 401，而是把整串拿去验签】。
        //
        // 本类此前用 split(" ", 2) 取 parts[1]，对 "XXBearer eyJ..." 会取出 "eyJ..." 去验签——
        // 与 Go **相反**：Go 验的是整串（必然验签失败 → 401），RAG 却可能验签通过；
        // 再叠加阶段 2 用整串 TrimPrefix 查黑名单必然查不到，构成**注销绕过**：
        // 已注销的管理员 token 只要前缀加 "XXBearer " 即可继续调用 admin-api。
        // 现按 Go 的 stripBearerPrefixFromTokenString 逐条对齐。
        if (authHeader == null || authHeader.isEmpty()) {
            log.warn("protected admin path {} without Authorization header", uri);
            throw new AuthenticationException(
                    ErrorCode.ERROR_TOKEN_RUNTIME.getCode(),
                    ErrorCode.ERROR_TOKEN_RUNTIME.getMsg());
        }
        String token = BearerTokenExtractor.tokenForSignatureVerification(authHeader);
        if (token.isEmpty()) {
            log.warn("protected admin path {} with empty Bearer token", uri);
            throw new AuthenticationException(
                    ErrorCode.ERROR_TOKEN_RUNTIME.getCode(),
                    ErrorCode.ERROR_TOKEN_RUNTIME.getMsg());
        }

        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            Object claim = jws.getPayload().get("userId");
            if (claim instanceof Number) {
                long uid = ((Number) claim).longValue();
                request.setAttribute("userId", Long.valueOf(uid));
            } else {
                log.warn("admin JWT claim userId missing or wrong type : {}",
                        claim == null ? "null" : claim.getClass());
                throw new AuthenticationException(
                        ErrorCode.ERROR_TOKEN_RUNTIME.getCode(),
                        ErrorCode.ERROR_TOKEN_RUNTIME.getMsg());
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Throwable t) {
            log.warn("admin JWT verify failed : {}", t.getMessage());
            throw new AuthenticationException(
                    ErrorCode.ERROR_TOKEN_RUNTIME.getCode(),
                    ErrorCode.ERROR_TOKEN_RUNTIME.getMsg());
        }

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
        String jwtToken = BearerTokenExtractor.trimBearerPrefix(authHeader);
        if (!jwtToken.isEmpty() && isBlacklisted(jwtToken)) {
            log.warn("admin token in blacklist, uri={}", uri);
            throw new AuthenticationException(
                    ErrorCode.ERROR_TOKEN_RUNTIME.getCode(),
                    ErrorCode.ERROR_TOKEN_RUNTIME.getMsg());
        }

        // Go 只有在 header 非空且 TrimPrefix 结果非空时才写 jwt_token；
        // 上面已排除两种为空的分支，此处必然写入。
        request.setAttribute("jwt_token", jwtToken);
        if (!jwtToken.equals(token)) {
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
