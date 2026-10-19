package com.rag.users.user.api.interceptor;

import com.rag.common.exception.BusinessException;
import com.rag.users.common.errmsg.ErrorCode;
import com.rag.users.user.api.config.UserApiProperties;
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
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * JWT 鉴权拦截器 —— 合并 Go 端 {@code JwtAuthenticationFilter} + {@code CheckBlacklistFilter} 两个 filter 的职责。
 *
 * 对应 Go: service/user/user/api/internal/middleware/jwtauth.go + checkblacklistmiddleware.go。
 *
 * <p><b>L1 行为冻结（与 Go 端 1:1）</b>：
 * <ol>
 *   <li>public 路径（{@code /user/v1/login}、{@code /user/v1/register}）→ <b>直接放行</b>，不校验 token</li>
 *   <li>protected 路径（其余 7 个）：
 *     <ul>
 *       <li>Authorization 缺失 或 "Bearer " 后 token 为空 → 401 + <b>不</b>设 userId / jwt_token attribute</li>
 *       <li>JWT 签名错 / 过期 / claim 缺失 → 401 + <b>不</b>设 attribute</li>
 *       <li>合法签名 + claim.userId 存在 → 写 attribute "userId" + attribute "jwt_token"</li>
 *       <li>Redis blacklist（{@code user:jwt_blacklist:<token>}）存在 → 401 + <b>不</b>进下游</li>
 *       <li>Redis 故障 → 不阻塞业务（与 Go 端 {@code err == nil && exists} 行为一致）</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <p><b>L4 实现差异</b>：
 * <ul>
 *   <li>Go 端用两个 {@code rest.Middleware}（go-zero 风格）；Java 端合一个 {@link HandlerInterceptor}
 *       风格（Spring MVC 风格），在 preHandle() 里按顺序做：JWT 解析 → 写 userId → blacklist 检查 → 写 jwt_token</li>
 *   <li>JWT 算法 HS256 + HMAC-SHA256（与 users-common/JwtUtil 1:1 对齐）</li>
 *   <li>Redis key 格式 {@code user:jwt_blacklist:<token>} 1:1 对齐 Go 端
 *       {@code fmt.Sprintf("user:jwt_blacklist:%s", token)}</li>
 * </ul>
 */
@Component
public class JwtAuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthInterceptor.class);

    /** public 路径前缀（HUMAN_ONLY.MD 决策：register/login 不挂 JWT）。 */
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/user/v1/register", "/user/v1/login"
    );

    /** Redis blacklist key 前缀（与 Go 端 1:1）。 */
    private static final String BLACKLIST_KEY_PREFIX = "user:jwt_blacklist:";

    private final SecretKey key;
    private final StringRedisTemplate redisTemplate;

    public JwtAuthInterceptor(UserApiProperties properties, StringRedisTemplate redisTemplate) {
        byte[] secretBytes = properties.getUserAuth().getAccessSecret().getBytes(StandardCharsets.UTF_8);
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.redisTemplate = redisTemplate;
    }

    /**
     * 判断路径是否 public（不挂 JWT）。
     * 注意：protected 路径匹配走 prefix（因为 {@code /user/v1/avatar/history} 等子路径要带前缀匹配）。
     */
    private static boolean isPublic(String path) {
        if (path == null) return false;
        return PUBLIC_PATHS.contains(path);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String uri = request.getRequestURI();

        // public 路径直接放行（与 Go 端 rest.AddRoutes(login, register) 不带 WithJwt 一致）
        if (isPublic(uri)) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            // Go 端 rest.WithJwt：缺少 Bearer token → 401
            log.warn("protected path {} without Bearer token, uid cannot be resolved", uri);
            throw new BusinessException(
                    ErrorCode.ERROR_TOKEN_RUNTIME.getCode(),
                    ErrorCode.ERROR_TOKEN_RUNTIME.getMsg()
            );
        }
        String token = authHeader.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            log.warn("protected path {} with empty Bearer token", uri);
            throw new BusinessException(
                    ErrorCode.ERROR_TOKEN_RUNTIME.getCode(),
                    ErrorCode.ERROR_TOKEN_RUNTIME.getMsg()
            );
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
                    log.warn("JWT claim userId <= 0 : {}", uid);
                    throw new BusinessException(
                            ErrorCode.ERROR_TOKEN_RUNTIME.getCode(),
                            ErrorCode.ERROR_TOKEN_RUNTIME.getMsg()
                    );
                }
                request.setAttribute("userId", Long.valueOf(uid));
            } else {
                log.warn("JWT claim userId missing or wrong type : {}", claim == null ? "null" : claim.getClass());
                throw new BusinessException(
                        ErrorCode.ERROR_TOKEN_RUNTIME.getCode(),
                        ErrorCode.ERROR_TOKEN_RUNTIME.getMsg()
                );
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Throwable t) {
            // 签名错 / 过期 —— 与 Go 端 silent 行为一致
            log.warn("JWT verify failed : {}", t.getMessage());
            throw new BusinessException(
                    ErrorCode.ERROR_TOKEN_RUNTIME.getCode(),
                    ErrorCode.ERROR_TOKEN_RUNTIME.getMsg()
            );
        }

        // Redis blacklist 检查（与 CheckBlacklistFilter 一致）
        String blacklistKey = BLACKLIST_KEY_PREFIX + token;
        Boolean exists;
        try {
            exists = redisTemplate.hasKey(blacklistKey);
        } catch (Throwable t) {
            // Redis 故障：不阻塞业务（与 Go 端 err == nil && exists 行为一致）
            log.warn("Redis blacklist check failed : {}", t.getMessage());
            exists = null;
        }
        if (exists != null && exists) {
            log.warn("token in blacklist, uid={}", uid);
            throw new BusinessException(
                    ErrorCode.ERROR_TOKEN_RUNTIME.getCode(),
                    ErrorCode.ERROR_TOKEN_RUNTIME.getMsg()
            );
        }

        // 通过：把 token 字符串写入 request attribute "jwt_token"（与 Go 端 context.WithValue 等价）
        request.setAttribute("jwt_token", token);
        return true;
    }
}
