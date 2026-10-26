package com.rag.message.message.api.auth;

import com.rag.message.message.api.config.MessageApiProperties;
import com.rag.users.common.jwt.BearerTokenExtractor;
import com.rag.users.common.jwt.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;

/**
 * message-api 的 JWT <b>验签</b>组件 —— Go 端两段 {@code rest.WithJwt(secret)} 的等价物。
 *
 * <h3>Go 出处（两把 key，两段路由表）</h3>
 * <pre>
 *   service/message/api/internal/handler/routes.go:60-61
 *       server.AddRoutes([...8 个 user 路由...],
 *                        rest.WithJwt(serverCtx.Config.UserAuth.AccessSecret),
 *                        rest.WithPrefix("/message/v1"))
 *   service/message/api/internal/handler/routes.go:92-93
 *       server.AddRoutes([...5 个 admin 路由...],
 *                        rest.WithJwt(serverCtx.Config.AdminAuth.AccessSecret),
 *                        rest.WithPrefix("/message/v1/admin"))
 *     → rest/server.go:185  featuredRoutes.jwt.enabled = true
 *     → rest/engine.go:70-83  appendAuthHandler ⇒ handler.Authorize(secret)
 *     → rest/handler/authhandler.go:45-84
 * </pre>
 *
 * <h3>🔴 为什么必须按<b>路径</b>分派 key</h3>
 * <p>Go 侧两段路由表<b>各自</b>带一把 key ⇒ 拿 admin token 打 user 端点会验签失败 → 401，
 * 反之亦然。<b>这是 Go 的既有语义，不要为了方便合成一把 key。</b>
 * <p>⇒ 本类持有<b>两把</b> key，并提供两种入口：
 * <ul>
 *   <li>{@link #verifyForPath} —— 按路径分派（给 {@link MessageJwtAuthFilter} 用，
 *       因为 Filter 在路由匹配<b>之前</b>，只能靠路径判组）；</li>
 *   <li>{@link #verify} —— 按<b>显式</b>的 {@link RouteGroup}（给拦截器兜底路径用，
 *       它的组别由 Spring 的注册路径决定，比路径推断更可靠）。</li>
 * </ul>
 *
 * <h3>语义等价论证（逐条对齐 Go 的 4 条行为）</h3>
 * <ol>
 *   <li>Authorization 头缺失 / 为空 → 失败（Go: {@code tokenparser.go} 的
 *       {@code errNoAuthParam} → {@code authhandler.go:56 unauthorized()}）</li>
 *   <li>Bearer 前缀剥离失败 / token 为空 → 失败
 *       （Go: {@code golang-jwt/jwt/v4@v4.5.2/request/oauth2.go} 的
 *       {@code stripBearerPrefixFromTokenString}，规则见 {@link BearerTokenExtractor}）</li>
 *   <li>签名错 / 过期 / 格式错 → 失败。<b>Go 不区分原因</b></li>
 *   <li>成功 → 返回 claim {@code userId} 的<b>原始对象</b>（不是 long）。
 *       Go 侧 {@code authhandler.go:71-79} 把非标准 claim 原样塞进 ctx，
 *       业务层 {@code api/internal/logic/shared/helper.go} 的 {@code UserIDFromContext} 判定，
 *       失败返回 5004 ⇒ <b>取值判定属于业务层</b>。</li>
 * </ol>
 *
 * <h3>⚠️ 本类<b>不写响应</b>、<b>不抛异常</b></h3>
 * <p>验签失败时只返回 {@link VerificationResult#failure()} ⇒ 由调用方决定怎么处理。
 * 这样同一个组件才能同时被 {@link MessageJwtAuthFilter}（验签在 3s 预算之外）
 * 与 {@code AbstractMessageJwtAuthInterceptor} 的兜底分支（就地验签）复用，
 * 而<b>两条路径的判定结果逐字一致</b>。
 */
@Component
public class MessageJwtSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(MessageJwtSignatureVerifier.class);

    /**
     * 路由组 —— 对应 Go 侧两段 {@code AddRoutes} 的两把 key。
     */
    public enum RouteGroup {
        /** {@code /message/v1/**}（不含 admin）—— Go 第一段，UserAuth secret。 */
        USER,
        /** {@code /message/v1/admin/**} —— Go 第二段，AdminAuth secret。 */
        ADMIN
    }

    /**
     * 验签结果。
     *
     * @param failed 是否失败（{@code true} ⇒ 401）
     * @param claim  成功时 claim {@code userId} 的<b>原始对象</b>；失败时为 {@code null}；
     *               成功但 token 里<b>没有</b> userId claim 时也为 {@code null}
     *               （<b>这不是失败</b> —— Go 侧同样放行，由业务层判 5004）
     */
    public record VerificationResult(boolean failed, Object claim) {

        public static VerificationResult success(Object claim) {
            return new VerificationResult(false, claim);
        }

        public static VerificationResult failure() {
            return new VerificationResult(true, null);
        }
    }

    private final SecretKey userKey;
    private final SecretKey adminKey;

    public MessageJwtSignatureVerifier(MessageApiProperties properties) {
        this.userKey = Keys.hmacShaKeyFor(
                JwtUtil.padToHmacKeySize(properties.getUserAuth().getAccessSecret()));
        this.adminKey = Keys.hmacShaKeyFor(
                JwtUtil.padToHmacKeySize(properties.getAdminAuth().getAccessSecret()));
    }

    /**
     * 按路径分派验签（给 Filter 用）。
     *
     * <p>⚠️ 判据与 {@code WebMvcConfig} 的拦截器注册<b>逐条一致</b>：
     * user 拦截器 {@code /message/v1/**} 排除 {@code /message/v1/admin/**}；
     * admin 拦截器 {@code /message/v1/admin/**}。
     */
    public VerificationResult verifyForPath(String authHeader, String uri) {
        return MessageJwtAuthFilter.isAdminPath(uri)
                ? verify(RouteGroup.ADMIN, authHeader, uri)
                : verify(RouteGroup.USER, authHeader, uri);
    }

    /**
     * 按<b>显式</b>组别验签（给拦截器兜底路径用）。
     *
     * @param group      路由组（决定用哪把 key）
     * @param authHeader 请求的 {@code Authorization} 头（可为 {@code null}）
     * @param uri        请求路径（只用于日志）
     */
    public VerificationResult verify(RouteGroup group, String authHeader, String uri) {
        SecretKey key = group == RouteGroup.ADMIN ? adminKey : userKey;

        // Go: rest/token/tokenparser.go:80-84 doParseToken → request.ParseFromRequest(r,
        //     request.AuthorizationHeaderExtractor, ...)。头缺失时 extractor 返回 errNoAuthParam
        //     → ParseToken 返回 err → authhandler.go:56 unauthorized()。
        if (authHeader == null || authHeader.isEmpty()) {
            log.warn("{} path {} with missing Authorization header", group, uri);
            return VerificationResult.failure();
        }

        // Go: golang-jwt/jwt/v4 request/oauth2.go stripBearerPrefixFromTokenString
        //     —— 大小写不敏感、不匹配时原样返回整串。三条规则都在 BearerTokenExtractor 里。
        String token = BearerTokenExtractor.tokenForSignatureVerification(authHeader);
        if (token.isEmpty()) {
            log.warn("{} path {} with empty Bearer token", group, uri);
            return VerificationResult.failure();
        }

        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            // Go: authhandler.go:63-72 遍历 claims，把非标准 claim（aud/exp/jti/iat/iss/nbf/sub 除外）
            //     逐个塞进 ctx。message api 侧只有 logic/shared/helper.go 的 UserIDFromContext 读
            //     "userId"，其余 claim 无消费者，故这里只取这一个。
            return VerificationResult.success(jws.getPayload().get("userId"));
        } catch (Throwable t) {
            // 签名错 / 过期 / 格式错 —— Go 侧同样只走到 unauthorized()，不区分原因
            log.warn("{} JWT verify failed on {} : {}", group, uri, t.getMessage());
            return VerificationResult.failure();
        }
    }
}
