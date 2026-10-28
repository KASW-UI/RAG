package com.rag.follow.follow.api.auth;

import com.rag.common.exception.AuthenticationException;
import com.rag.common.exception.BusinessException;
import com.rag.follow.follow.api.config.FollowApiProperties;
import com.rag.follow.follow.common.errmsg.FollowErrorCode;
import com.rag.follow.follow.common.errmsg.FollowErrorMessages;
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
 * follow JWT <b>验签</b> —— 只做「解析 + 验签名 + 取 claim」，<b>不写响应、不查 Redis</b>。
 *
 * <h2>为什么从 {@code FollowJwtAuthInterceptor} 里搬出来</h2>
 *
 * <p>DRIFT-USERAPI-009②：Go 的 3s 预算<b>不覆盖</b> JWT 验签，RAG 的<b>覆盖</b>。
 * <p><b>Go 侧链序（真源，逐行核实 go-zero@v1.10.2 + follow 真源）</b>：
 * <pre>
 *   service/follow/api/followcenter.go:40-42
 *     httpTimeout := observability.DisableNativeRestTimeout(&c.RestConf)
 *     server := rest.MustNewServer(c.RestConf)
 *     server.Use(observability.NewHTTPMiddleware(c.Name, httpTimeout, observability.SlowThreshold()))
 *     ⇒ observability 是**唯一**的 user middleware
 *
 *   rest/engine.go:101-113  bindRoute：
 *     chn = ng.buildChainWithNativeMiddlewares(...)   // 原生中间件（索引 0 = 最外层）
 *     chn = ng.appendAuthHandler(fr, chn, verifier)   // ← JWT 验签
 *     for _, mw := range ng.middlewares { ... }        // ← observability(3s) 在 JWT **之内**
 *     handle = chn.ThenFunc(route.Handler)
 *
 *   rest/chain/chain.go:38-43  Append = 「加在请求流的最后」⇒ 越 Append 越靠内
 *
 *   ⇒ 执行序：原生中间件 → **JWT 验签** → **observability(3s)** → 黑名单 → handler
 *   ⇒ 3s 计时器在 Authorize **之后**起算 ⇒ **验签耗时不计入 3s 预算**
 *   ⇒ 黑名单（route 级 WithMiddlewares ⇒ 最内层）在 3s 预算**之内** ⇒ RAG 现状已一致，不动
 * </pre>
 *
 * <p>⚠️ <b>另一个前提不能漏</b>：Go 的 native {@code TimeoutHandler} 被
 * {@code observability.DisableNativeRestTimeout} 关掉了（{@code followcenter.go:40}）
 * ⇒ 3s <b>只</b>由 observability 提供 ⇒ 「验签在预算之外」才有意义。
 */
@Component
public class FollowJwtSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(FollowJwtSignatureVerifier.class);

    private final SecretKey key;

    public FollowJwtSignatureVerifier(FollowApiProperties properties) {
        // L4 idiomatization（强制留痕 + Go ↔ RAG 对照）：
        //   Go 出处：go-zero rest/token/tokenparser.go:83-85 的 keyfunc 无条件返回
        //     `[]byte(secret)` —— golang-jwt 不校验 key 长度，36 字节 UUID 可直接签。
        //   Java 表达差异：jjwt 0.12 强制 ≥32 字节（HS256 / RFC 7518），故构造器
        //     调 Keys.hmacShaKeyFor 时必须先补齐；本构造器与 JwtUtil.getToken 共用
        //     JwtUtil.padToHmacKeySize，强制签发与验签落在同一份 32 字节 key 上。
        //   语义等价论证：Go 的 secret 本身就是 36 字节（UUID），≥32 ⇒ padding 对它是
        //     no-op；即便换成 <32 字节的 secret，zero-fill 后 HS256 字节一致，
        //     仅在「换密钥」这个非本组件动作上生效，不影响任何运行期行为。
        this.key = Keys.hmacShaKeyFor(
                JwtUtil.padToHmacKeySize(properties.getUserAuth().getAccessSecret()));
    }

    /**
     * 验签 —— <b>不写响应</b>；失败时把异常装进 {@link FollowJwtVerificationResult} 交给调用方重抛。
     *
     * @param authHeader 请求的 {@code Authorization} 头（可为 null）
     * @param uri        请求路径（只用于日志）
     */
    public FollowJwtVerificationResult verify(String authHeader, String uri) {
        // Go: rest/token/tokenparser.go:81-86 doParseToken → request.ParseFromRequest(r,
        //     request.AuthorizationHeaderExtractor, ...)。头缺失时 extractor 返回 errNoAuthParam
        //     → ParseToken 返回 err → authhandler.go:56 unauthorized()。
        if (authHeader == null || authHeader.isEmpty()) {
            log.warn("path {} with missing Authorization header", uri);
            return FollowJwtVerificationResult.failed(unauthorized());
        }

        // Go: golang-jwt/v4 request/oauth2.go stripBearerPrefixFromTokenString
        //     —— 大小写不敏感、不匹配时原样返回整串。三条规则都在 BearerTokenExtractor 里。
        String token = BearerTokenExtractor.tokenForSignatureVerification(authHeader);
        if (token.isEmpty()) {
            log.warn("path {} with empty Bearer token", uri);
            return FollowJwtVerificationResult.failed(unauthorized());
        }

        Claims claims;
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            claims = jws.getPayload();
        } catch (BusinessException e) {
            // 保持原样上抛（与修复前 catch (BusinessException e) { throw e; } 语义一致）
            return FollowJwtVerificationResult.failed(e);
        } catch (Throwable t) {
            // 签名错 / 过期 / 格式错 —— Go 侧同样只走到 unauthorized()，不区分原因
            log.warn("JWT verify failed on {} : {}", uri, t.getMessage());
            return FollowJwtVerificationResult.failed(unauthorized());
        }

        // Go: authhandler.go:71-79 遍历 claims，把非标准 claim（aud/exp/jti/iat/iss/nbf/sub 除外）
        //     逐个塞进 ctx。follow api 侧只有 logic/follow/helper.go 的 extractUserID 读
        //     "userId"，其余 claim 无消费者，故这里只落这一个。
        // ⚠️ 存的是**原始对象**（不是 long）：取值判定属于业务层（helper.go:13-33），
        //   失败返回 5004 ⇒ HTTP 200 + {code:5004} ⇒ 鉴权层**不能**把它变成 401。
        return FollowJwtVerificationResult.ok(claims.get("userId"));
    }

    /**
     * 统一构造鉴权失败。
     *
     * <p><b>body 形状：401 + 空 body。</b>Go 的 {@code unauthorized()}
     * （{@code authhandler.go:106-122}）只调 {@code writer.WriteHeader(http.StatusUnauthorized)}，
     * <b>不写任何字节</b>，也不设 Content-Type。
     * 由 {@code GlobalExceptionHandler#handleAuthentication} 负责还原该形状。
     *
     * <p>业务码用 {@code ERROR_UNAUTHORIZED}（5004）：follow 域错误码表
     * （{@code service/follow/common/errmsg.go:41}）里鉴权码就是 5004。
     * 注意该 code <b>不会出现在 body 里</b>（body 为空）——它只用于日志与
     * {@code FollowBlacklistInterceptor} 那条<b>有 body</b>的 401。
     */
    public static AuthenticationException unauthorized() {
        int code = FollowErrorCode.ERROR_UNAUTHORIZED.getCode();
        return new AuthenticationException(code, FollowErrorMessages.getMsg(code));
    }
}
