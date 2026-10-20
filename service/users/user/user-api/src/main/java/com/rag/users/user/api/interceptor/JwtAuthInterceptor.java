package com.rag.users.user.api.interceptor;

import com.rag.common.exception.AuthenticationException;
import com.rag.common.exception.BusinessException;
import com.rag.users.common.errmsg.ErrorCode;
import com.rag.users.common.jwt.JwtUtil;
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
import java.util.Set;

/**
 * JWT 鉴权拦截器 —— 合并 Go 端 {@code JwtAuthenticationFilter} + {@code CheckBlacklistFilter} 两个 filter 的职责。
 *
 * 对应 Go: service/user/user/api/internal/middleware/jwtauth.go + checkblacklistmiddleware.go。
 *
 * <p><b>L1 行为冻结（与 Go 端 1:1）</b>：
 * <ol>
 *   <li>public 路径（{@code /user/v1/login}、{@code /user/v1/register}）→ <b>直接放行</b>，不校验 token</li>
 *   <li>protected 路径（其余 7 个）—— 本层只承担 Go {@code rest.WithJwt} 的职责
 *     （验签名 + exp + claim 存在且为数字），失败一律 <b>HTTP 401</b>：
 *     <ul>
 *       <li>Authorization 缺失 / 不以 "Bearer " 开头 → {@code AuthenticationException} → 401</li>
 *       <li>"Bearer " 后 token 为空 → {@code AuthenticationException} → 401</li>
 *       <li>JWT 签名错 / 过期 → {@code AuthenticationException} → 401</li>
 *       <li>claim.userId 缺失或类型不对 → {@code AuthenticationException} → 401</li>
 *       <li>Redis blacklist（{@code user:jwt_blacklist:<token>}）存在 → {@code AuthenticationException} → 401</li>
 *       <li>Redis 故障 → 不阻塞业务（与 Go 端 {@code err == nil && exists} 行为一致）</li>
 *     </ul>
 *     以上任一失败都<b>不</b>写 userId / jwt_token attribute。
 *   </li>
 *   <li><b>uid 取值（{@code uid <= 0}）不在本层拒绝</b>：Go 的 {@code rest.WithJwt} 同样不校验 claim 取值，
 *       该规则属于 identity 层（{@code identity/resolve.go} {@code ClaimedUID}），
 *       由 {@code IdentityResolver.claimedUID} 承担，失败表现为 <b>HTTP 200 + 业务码 1006</b>。
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
        // L4 idiomatization（强制留痕 + Go ↔ RAG 对照）：
        //   Go 出处：service/user/common/jwt/jwt.go `token.SignedString([]byte(secretKey))`
        //     —— Go 侧 golang-jwt 不校验 key 长度，user secret 是 36 字节 UUID 已 ≥ 32 字节，
        //     走"返回 bytes 原长"分支，padding 是 no-op。
        //   Java 表达差异：jjwt 0.12 强制 ≥32 字节，所以即便 user secret 已 ≥32 仍统一调
        //     padToHmacKeySize —— 这样 admin 与 user 的 padding 逻辑收口在 JwtUtil 一份，
        //     将来若 user secret 也换成短密钥无需再改拦截器。
        //   语义等价论证：bytes.length ≥ 32 时返回原 bytes，行为与直接 getBytes 一致；
        //     token 形状、claim、exp 全一致。Go ↔ RAG 无行为差异。
        this.key = Keys.hmacShaKeyFor(JwtUtil.padToHmacKeySize(properties.getUserAuth().getAccessSecret()));
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
            throw new AuthenticationException(
                    ErrorCode.ERROR_TOKEN_RUNTIME.getCode(),
                    ErrorCode.ERROR_TOKEN_RUNTIME.getMsg()
            );
        }
        // DRIFT-008：Go 出处 checkblacklistmiddleware.go:29 是 `strings.TrimPrefix(authHeader, "Bearer ")`，
        // <b>不</b> trim 余下部分。此前的 `.trim()` 会造成两处可观察差异：
        //   ① RAG 接受 Go 会因签名不符而 401 的 token（"Bearer  abc " → Go " abc " / RAG "abc"）；
        //   ② 两边算出的 blacklist key 不同（带/不带尾空格），注销后的黑名单查询会错配。
        String token = authHeader.substring("Bearer ".length());
        if (token.isEmpty()) {
            log.warn("protected path {} with empty Bearer token", uri);
            throw new AuthenticationException(
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
                // ⚠️ 这里<b>不</b>做 uid <= 0 的拒绝判断。
                // Go 出处：go-zero 的 rest.WithJwt 只验签名与 exp，不校验 claim 取值；
                // `uid > 0` 是业务规则，落在 identity 层
                // （identity/resolve.go:75-77 `if err != nil || uid <= 0 { return 0, ErrInvalidClaim }`）。
                // 之前的实现放在拦截器里直接 401，导致 Go 返回 200 + 1006 的场景
                // （get / avatar.history / avatar / avatar.select 四个端点）在 RAG 上变成了 401。
                // 现在把取值判断交还给 IdentityResolver.claimedUID，与 Go 的层次划分一致。
                request.setAttribute("userId", Long.valueOf(uid));
            } else {
                log.warn("JWT claim userId missing or wrong type : {}", claim == null ? "null" : claim.getClass());
                throw new AuthenticationException(
                        ErrorCode.ERROR_TOKEN_RUNTIME.getCode(),
                        ErrorCode.ERROR_TOKEN_RUNTIME.getMsg()
                );
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Throwable t) {
            // 签名错 / 过期 —— 与 Go 端 silent 行为一致
            log.warn("JWT verify failed : {}", t.getMessage());
            throw new AuthenticationException(
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
            throw new AuthenticationException(
                    ErrorCode.ERROR_TOKEN_RUNTIME.getCode(),
                    ErrorCode.ERROR_TOKEN_RUNTIME.getMsg()
            );
        }

        // 通过：把 token 字符串写入 request attribute "jwt_token"（与 Go 端 context.WithValue 等价）
        request.setAttribute("jwt_token", token);
        return true;
    }
}
