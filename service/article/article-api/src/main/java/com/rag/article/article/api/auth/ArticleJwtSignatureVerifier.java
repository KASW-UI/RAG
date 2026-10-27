package com.rag.article.article.api.auth;

import com.rag.article.article.api.config.ArticleApiProperties;
import com.rag.common.exception.AuthenticationException;
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
 * article JWT <b>验签</b> —— 只做「解析 + 验签名 + 取 claim」，<b>不写响应、不查 Redis</b>。
 *
 * <h2>为什么从 {@code JwtAuthInterceptor} 里搬出来</h2>
 *
 * <p>🔴 <b>DRIFT-USERAPI-009②</b>：Go 的 70s 预算<b>不覆盖</b> JWT 验签，RAG 的<b>覆盖</b>。
 *
 * <p><b>Go 侧链序（真源，逐行核实 go-zero@v1.10.2）</h2>
 * <pre>
 *   rest/engine.go:101-116  bindRoute：
 *     chn = ng.buildChainWithNativeMiddlewares(...)   // 原生中间件（索引 0 = 最外层）
 *     chn = ng.appendAuthHandler(fr, chn, verifier)   // ← JWT 验签在这里 append（:108）
 *     for _, mw := range ng.middlewares { ... }        // ← observability(3s) 在这之后 append（:110-112）
 *     handle = chn.ThenFunc(route.Handler)
 *   rest/chain/chain.go  Append = 「加在请求流的最后」⇒ 越 Append 越靠内
 *
 *   ⇒ 执行序：原生中间件 → **JWT 验签** → **observability(3s)** → handler
 *   ⇒ 70s 计时器在 Authorize **之后**起算 ⇒ **验签耗时不计入预算**
 * </pre>
 *
 * <p>⚠️ <b>本域 70s 预算确实来自 observability（不是 go-zero native）</b>：
 * {@code service/article/api/articlecenter.go} 的
 * {@code observability.DisableNativeRestTimeout(&amp;c.RestConf)}
 * ＋ {@code server.Use(observability.NewHTTPMiddleware(...))}
 * ⇒ native 那条 Timeout 被关掉了 ⇒ 3s <b>只</b>由 observability 提供
 * ⇒ 「验签在预算之外」才有意义（否则 native 那条 3s 还在，照样覆盖验签）。
 *
 * <p><b>Java 侧落点</b>：Servlet 的所有 {@code Filter} 都在 {@code DispatcherServlet} <b>外面</b>，
 * {@code HandlerInterceptor} 在<b>里面</b> ⇒ 要把验签移出 70s 预算，就必须把它搬进 Filter
 * ⇒ 见 {@link GoStyleJwtAuthFilter}。
 *
 * <h2>⚠️ 本层刻意<b>不</b>校验 claim 的取值</h2>
 * <ul>
 *   <li>Go 侧 {@code handler.Authorize}（{@code rest/handler/authhandler.go:45-84}）只验签名与 exp，
 *       随后把所有<b>非标准</b> claim（除 aud/exp/jti/iat/iss/nbf/sub）原样塞进 ctx；</li>
 *   <li>claim 是不是数字、是不是正数，由 logic 层
 *       {@code api/internal/logic/article/helper.go:17-39} 的 {@code extractCurrentUserID} 判定，
 *       失败返回 {@code ErrorUnauthorized}（<b>1005</b>），
 *       最终输出是 <b>HTTP 200 + {code:1005,msg:"请先登录"}</b>。</li>
 * </ul>
 * 若在本层把它变成 401，Go 返回 200 + 1005 的场景在 RAG 上会变成 401 —— 状态码就错了。
 * 取值判定见 {@code ArticleApiSupport#userId}。
 */
@Component
public class ArticleJwtSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(ArticleJwtSignatureVerifier.class);

    private final SecretKey key;

    public ArticleJwtSignatureVerifier(ArticleApiProperties properties) {
        // L4 idiomatization（强制留痕 + Go ↔ RAG 对照）：
        //   Go 出处：go-zero rest/handler/authhandler.go:84 的
        //     `func(token *jwt.Token) (any, error) { return []byte(secret), nil }`
        //     —— Go 端 golang-jwt 不校验 key 长度，本域的 secret 能直接签。
        //   Java 表达差异：jjwt 0.12 强制 ≥32 字节（HS256 / RFC 7518），故构造器
        //     调 Keys.hmacShaKeyFor 时必须先补齐；本构造器与 JwtUtil.getToken 共用
        //     JwtUtil.padToHmacKeySize，强制签发与验签落在同一份 32 字节 key 上，
        //     否则本模块在默认密钥下 Bean 创建即抛 WeakKeyException。
        //   语义等价论证：padding 是 zero-fill，落到 HS256 上字节一致 → token 形状、
        //     claim、exp 全一致；Go ↔ RAG 仅在 key 长度这一非行为字段上收敛。
        this.key = Keys.hmacShaKeyFor(
                JwtUtil.padToHmacKeySize(properties.getAuth().getAccessSecret()));
    }

    /**
     * 验签 —— <b>不写响应</b>；失败时把异常装进 {@link ArticleJwtVerificationResult} 交给调用方重抛。
     *
     * @param authHeader 请求的 {@code Authorization} 头（可为 null）
     * @param uri        请求路径（只用于日志）
     */
    public ArticleJwtVerificationResult verify(String authHeader, String uri) {
        // Go: rest/token/tokenparser.go doParseToken → request.ParseFromRequest(r,
        //     request.AuthorizationHeaderExtractor, ...)。头缺失时 extractor 返回 errNoAuthParam
        //     → parser.ParseToken 返回 err → authhandler.go:100 unauthorized()。
        if (authHeader == null || authHeader.isEmpty()) {
            log.warn("article-api path {} with missing Authorization header", uri);
            return ArticleJwtVerificationResult.failed(unauthorized());
        }

        // Go: golang-jwt/jwt/v4 request/oauth2.go stripBearerPrefixFromTokenString
        //     —— 大小写不敏感、不匹配时原样返回整串。三条规则都在 BearerTokenExtractor 里。
        String token = BearerTokenExtractor.tokenForSignatureVerification(authHeader);
        if (token.isEmpty()) {
            log.warn("article-api path {} with empty Bearer token", uri);
            return ArticleJwtVerificationResult.failed(unauthorized());
        }

        Claims claims;
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            claims = jws.getPayload();
        } catch (Throwable t) {
            // 签名错 / 过期 / 格式错 —— Go 侧同样只走到 unauthorized()，不区分原因
            log.warn("article-api JWT verify failed on {} : {}", uri, t.getMessage());
            return ArticleJwtVerificationResult.failed(unauthorized());
        }

        return ArticleJwtVerificationResult.ok(claims);
    }

    /**
     * <b>本域 401 的唯一构造点</b> —— 验签失败的各种形态共用同一形态（Go 不区分原因）。
     *
     * <p><b>body 不会被写出去</b> —— 见 {@code GlobalExceptionHandler#handleAuthentication}
     * 对 Go {@code authhandler.go:106 unauthorized()} 的逐字还原：
     * Go 只 {@code WriteHeader(401)}，<b>不写任何字节</b> ⇒ RAG 侧也落<b>空 body</b>。
     *
     * <p>⚠️ 业务码填 {@code 1005}（article 域的 {@code ErrorUnauthorized}）
     * <b>只是为了让日志可读</b>：401 的 body 是空的 ⇒ 这个码<b>不会</b>出现在响应里。
     */
    public static AuthenticationException unauthorized() {
        return new AuthenticationException(
                com.rag.article.article.common.errmsg.ErrorCode.ERROR_UNAUTHORIZED.getCode(),
                com.rag.article.article.common.errmsg.ErrorCode.getMsg(
                        com.rag.article.article.common.errmsg.ErrorCode.ERROR_UNAUTHORIZED.getCode()));
    }
}
