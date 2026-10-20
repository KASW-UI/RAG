package com.rag.users.admin.api.interceptor;

import com.rag.common.exception.AuthenticationException;
import com.rag.common.exception.BusinessException;
import com.rag.users.admin.api.config.AdminApiProperties;
import com.rag.users.common.errmsg.ErrorCode;
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
 * Admin JWT 鉴权拦截器 —— 合并 Go 端 {@code JwtAuthenticationFilter} + {@code CheckBlacklistFilter} 两个 filter 的职责。
 *
 * 对应 Go: service/user/admin/api/internal/middleware/jwtauth.go + checkblacklistmiddleware.go。
 *
 * <p><b>L1 行为冻结（与 Go 端 1:1）</b>：
 * <ol>
 *   <li>public 路径（{@code /admin/v1/login}、{@code /admin/v1/create}）：<b>直接放行</b>，不校验 token</li>
 *   <li>protected 路径（其余 11 个）—— 本层只承担 Go {@code rest.WithJwt} 的职责
 *     （验签名 + exp + claim 存在且为数字），失败一律 <b>HTTP 401</b>
 *     （抛 {@code AuthenticationException}，与 user-api 同款，见 DRIFT-004）：
 *     <ul>
 *       <li>Authorization 缺失 / 不以 "Bearer " 开头 → 401</li>
 *       <li>"Bearer " 后 token 为空 → 401</li>
 *       <li>JWT 签名错 / 过期 → 401</li>
 *       <li>claim.userId 缺失或类型不对 → 401</li>
 *       <li>Redis blacklist（{@code admin:jwt_blacklist:<token>}）存在 → 401 + <b>不</b>进下游</li>
 *       <li>Redis 故障 → 不阻塞业务</li>
 *     </ul>
 *     以上任一失败都<b>不</b>写 userId / jwt_token attribute。
 *   </li>
 *   <li><b>uid 取值（{@code uid <= 0}）在本层拒绝</b>：抛 {@code BusinessException(1006)}，
 *       经 {@code GlobalExceptionHandler.handleBusiness} → HTTP 200 + {@code {code:1006}}。
 *       Go 端 {@code rest.WithJwt} 不做这个校验（uid=0 会原样进 ctx），但 admin-api 侧
 *       {@code IdentityResolver.claimedUID} 同样拒 {@code uid <= 0} ⇒ 两端最终都在业务层被拒，
 *       且都是 1006，行为等价。</li>
 * </ol>
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

    /** Authorization 头里的 Bearer 前缀（含尾随空格）。 */
    private static final String BEARER_PREFIX = "Bearer ";

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
        // DRIFT-009（已修）：执行顺序改为「黑名单先、验签后」，与 Go 端一致。
        //
        // Go 出处：handler/routes.go:32-95 的两段 AddRoutes：
        //   server.AddRoutes(rest.WithMiddlewares([CheckBlacklistMiddleware], ...),
        //                     rest.WithJwt(secret), rest.WithPrefix("/admincenter/v1"))
        // go-zero 的 WithJwt 把 Jwt middleware **追加到切片尾部**，而 withMiddlewares 反向包裹
        //   → 最终链路是 CheckBlacklist(Jwt(handler))，黑名单是外层、先执行。
        //
        // 本实现此前是「验签先、黑名单后」，方向相反。可观察差异：
        // 一个**签名无效但已在黑名单中**的 token，
        //   Go   → 黑名单先命中 → 401 + {"code":401,"msg":"登录已失效,请重新登陆"}
        //   RAG  → 验签先失败     → 401 + envelope{code:1006}
        // 两者都是 401，但业务码语义完全不同（"已注销" vs "令牌异常"）。
        //
        // 两段各自的 fail-open 行为也与 Go 逐条对齐，见下方注释。
        // ==================================================================

        // ---------- 阶段 1：黑名单检查（对应 Go CheckBlacklistMiddleware.Handle）----------
        String authHeader = request.getHeader("Authorization");
        String blacklistCheckedToken = null;
        if (authHeader != null && !authHeader.isEmpty()) {
            // Go: strings.TrimPrefix(authHeader, "Bearer ") —— 不校验前缀是否存在，也不 trim 余下部分
            String token = authHeader.startsWith(BEARER_PREFIX)
                    ? authHeader.substring(BEARER_PREFIX.length())
                    : authHeader;
            if (!token.isEmpty()) {
                blacklistCheckedToken = token;
                if (isBlacklisted(token)) {
                    log.warn("admin token in blacklist, uri={}", uri);
                    throw new AuthenticationException(
                            ErrorCode.ERROR_TOKEN_RUNTIME.getCode(),
                            ErrorCode.ERROR_TOKEN_RUNTIME.getMsg());
                }
            }
            // token 为空 → Go 直接 next(w, r) 放行，交给 Jwt middleware 拒（见阶段 2）
        }
        // header 为空 → Go 直接 next(w, r) 放行，黑名单**完全不查**，交给 Jwt middleware 拒

        // ---------- 阶段 2：JWT 验签（对应 go-zero rest/handler/authhandler.go Handle）----------
        if (authHeader == null || authHeader.isEmpty()) {
            log.warn("protected admin path {} without Authorization header", uri);
            throw new AuthenticationException(
                    ErrorCode.ERROR_TOKEN_RUNTIME.getCode(),
                    ErrorCode.ERROR_TOKEN_RUNTIME.getMsg());
        }
        // Go: strings.SplitN(auth, " ", 2) 且要求 len(parts)==2
        String[] parts = authHeader.split(" ", 2);
        if (parts.length != 2) {
            log.warn("protected admin path {} with malformed Authorization header", uri);
            throw new AuthenticationException(
                    ErrorCode.ERROR_TOKEN_RUNTIME.getCode(),
                    ErrorCode.ERROR_TOKEN_RUNTIME.getMsg());
        }
        String token = parts[1];
        if (token.isEmpty()) {
            log.warn("protected admin path {} with empty Bearer token", uri);
            throw new AuthenticationException(
                    ErrorCode.ERROR_TOKEN_RUNTIME.getCode(),
                    ErrorCode.ERROR_TOKEN_RUNTIME.getMsg());
        }

        long uid;
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            Object claim = jws.getPayload().get("userId");
            if (claim instanceof Number) {
                uid = ((Number) claim).longValue();
                if (uid <= 0) {
                    log.warn("admin JWT claim userId <= 0 : {}", uid);
                    throw new BusinessException(
                            ErrorCode.ERROR_TOKEN_RUNTIME.getCode(),
                            ErrorCode.ERROR_TOKEN_RUNTIME.getMsg());
                }
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

        request.setAttribute("jwt_token", token);
        // 与黑名单阶段检查过的 token 做一致性提示：两者取法不同（TrimPrefix vs SplitN），
        // 只有在 authHeader 规范写成 "Bearer <token>" 时才相等。与 Go 保持同样的不一致。
        if (blacklistCheckedToken != null && !blacklistCheckedToken.equals(token)) {
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
