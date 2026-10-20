package com.rag.users.admin.api.interceptor;

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
 *   <li>protected 路径（其余 11 个）：
 *     <ul>
 *       <li>Authorization 缺失 或 "Bearer " 后 token 为空 → 401 + <b>不</b>设 userId / jwt_token attribute</li>
 *       <li>JWT 签名错 / 过期 → 401 + <b>不</b>设 attribute</li>
 *       <li>合法签名 + claim.userId 存在 → 写 attribute "userId" + attribute "jwt_token"</li>
 *       <li>Redis blacklist（{@code admin:jwt_blacklist:<token>}）存在 → 401 + <b>不</b>进下游</li>
 *       <li>Redis 故障 → 不阻塞业务</li>
 *     </ul>
 *   </li>
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

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("protected admin path {} without Bearer token", uri);
            throw new BusinessException(
                    ErrorCode.ERROR_TOKEN_RUNTIME.getCode(),
                    ErrorCode.ERROR_TOKEN_RUNTIME.getMsg());
        }
        String token = authHeader.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            log.warn("protected admin path {} with empty Bearer token", uri);
            throw new BusinessException(
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
                throw new BusinessException(
                        ErrorCode.ERROR_TOKEN_RUNTIME.getCode(),
                        ErrorCode.ERROR_TOKEN_RUNTIME.getMsg());
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Throwable t) {
            log.warn("admin JWT verify failed : {}", t.getMessage());
            throw new BusinessException(
                    ErrorCode.ERROR_TOKEN_RUNTIME.getCode(),
                    ErrorCode.ERROR_TOKEN_RUNTIME.getMsg());
        }

        // Redis blacklist 检查
        String blacklistKey = BLACKLIST_KEY_PREFIX + token;
        Boolean exists;
        try {
            exists = redisTemplate.hasKey(blacklistKey);
        } catch (Throwable t) {
            log.warn("admin Redis blacklist check failed : {}", t.getMessage());
            exists = null;
        }
        if (exists != null && exists) {
            log.warn("admin token in blacklist, uid={}", uid);
            throw new BusinessException(
                    ErrorCode.ERROR_TOKEN_RUNTIME.getCode(),
                    ErrorCode.ERROR_TOKEN_RUNTIME.getMsg());
        }

        request.setAttribute("jwt_token", token);
        return true;
    }
}
