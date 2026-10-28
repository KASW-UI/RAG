package com.rag.like.like.api.auth;

import com.rag.like.like.api.config.LikeApiProperties;
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
 * like-api 的 JWT <b>验签</b>组件 —— Go 端 {@code rest.WithJwt(secret)} 里
 * {@code handler.Authorize(secret)} 的等价物，被抽成独立组件的<b>唯一</b>理由是
 * 「验签要发生在 3s 预算<b>之外</b>」（见 {@link LikeJwtAuthFilter}）。
 *
 * <h3>Go 出处</h3>
 * <pre>
 *   service/like/api/internal/handler/routes.go:49
 *       rest.WithJwt(serverCtx.Config.UserAuth.AccessSecret)
 *     → rest/server.go:185  featuredRoutes.jwt.enabled = true
 *     → rest/engine.go:70-83  appendAuthHandler ⇒ handler.Authorize(secret)
 *     → rest/handler/authhandler.go:45-84
 * </pre>
 *
 * <h3>语义等价论证（逐条对齐 Go 的 4 条行为）</h3>
 * <ol>
 *   <li>Authorization 头缺失 / 为空 → 失败（Go: {@code tokenparser.go} 的
 *       {@code errNoAuthParam} → {@code authhandler.go:56 unauthorized()}）</li>
 *   <li>Bearer 前缀剥离失败 / token 为空 → 失败
 *       （Go: {@code golang-jwt/jwt/v4@v4.5.2/request/oauth2.go} 的
 *       {@code stripBearerPrefixFromTokenString}，三条规则见 {@link BearerTokenExtractor}）</li>
 *   <li>签名错 / 过期 / 格式错 → 失败。<b>Go 不区分原因</b>，
 *       三种失败都只走到同一个 {@code unauthorized()}</li>
 *   <li>成功 → 返回 claim {@code userId} 的<b>原始对象</b>（不是 long）。
 *       Go 侧 {@code authhandler.go:71-79} 把所有非标准 claim 原样塞进 ctx，
 *       业务层 {@code getlikestatelogic.go:37-49} 做 {@code json.Number} 断言，
 *       失败返回 1006 ⇒ <b>取值判定属于业务层，不属于鉴权层</b>。</li>
 * </ol>
 *
 * <h3>⚠️ 本类<b>不写响应</b>、<b>不抛异常</b></h3>
 * <p>验签失败时只返回 {@link VerificationResult#failure()} ⇒ 由调用方决定怎么处理。
 * 这样同一个组件才能同时被 {@link LikeJwtAuthFilter}（Filter 层，验签在 3s 预算之外）
 * 与 {@code LikeJwtAuthInterceptor} 的兜底分支（就地验签）复用，
 * 而<b>两条路径的判定结果逐字一致</b>（否则「Filter 路径成功 / 兜底路径失败」会造出行为分叉）。
 */
@Component
public class LikeJwtSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(LikeJwtSignatureVerifier.class);

    /**
     * 验签结果。
     *
     * @param failed 是否失败（{@code true} ⇒ 401）
     * @param claim  成功时 claim {@code userId} 的<b>原始对象</b>；失败时为 {@code null}；
     *               成功但 token 里<b>没有</b> userId claim 时也为 {@code null}
     *               （<b>这不是失败</b> —— Go 侧同样放行，由业务层判 1006）
     */
    public record VerificationResult(boolean failed, Object claim) {

        public static VerificationResult success(Object claim) {
            return new VerificationResult(false, claim);
        }

        public static VerificationResult failure() {
            return new VerificationResult(true, null);
        }
    }

    private final SecretKey key;

    public LikeJwtSignatureVerifier(LikeApiProperties properties) {
        this.key = Keys.hmacShaKeyFor(
                JwtUtil.padToHmacKeySize(properties.getUserAuth().getAccessSecret()));
    }

    /**
     * 验签。<b>不抛异常、不写响应</b>。
     *
     * @param authHeader 请求的 {@code Authorization} 头（可为 {@code null}）
     * @param uri        请求路径（只用于日志）
     */
    public VerificationResult verify(String authHeader, String uri) {
        // Go: rest/token/tokenparser.go:81-86 doParseToken → request.ParseFromRequest(r,
        //     request.AuthorizationHeaderExtractor, ...)。头缺失时 extractor 返回 errNoAuthParam
        //     → ParseToken 返回 err → authhandler.go:56 unauthorized()。
        if (authHeader == null || authHeader.isEmpty()) {
            log.warn("path {} with missing Authorization header", uri);
            return VerificationResult.failure();
        }

        // Go: golang-jwt/jwt/v4 request/oauth2.go stripBearerPrefixFromTokenString
        //     —— 大小写不敏感、不匹配时原样返回整串。三条规则都在 BearerTokenExtractor 里。
        String token = BearerTokenExtractor.tokenForSignatureVerification(authHeader);
        if (token.isEmpty()) {
            log.warn("path {} with empty Bearer token", uri);
            return VerificationResult.failure();
        }

        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            // Go: authhandler.go:65-79 遍历 claims，把非标准 claim（aud/exp/jti/iat/iss/nbf/sub 除外）
            //     逐个塞进 ctx。like api 侧只有 4 个 logic 的 ctx.Value("userId") 读它，
            //     其余 claim 无消费者，故这里只取这一个。
            return VerificationResult.success(jws.getPayload().get("userId"));
        } catch (Throwable t) {
            // 签名错 / 过期 / 格式错 —— Go 侧同样只走到 unauthorized()，不区分原因
            log.warn("JWT verify failed on {} : {}", uri, t.getMessage());
            return VerificationResult.failure();
        }
    }
}
