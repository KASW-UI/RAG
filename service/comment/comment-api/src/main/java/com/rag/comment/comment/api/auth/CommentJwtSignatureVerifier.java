package com.rag.comment.comment.api.auth;

import com.rag.comment.comment.api.config.CommentApiProperties;
import com.rag.comment.comment.common.errmsg.CommentErrorCode;
import com.rag.comment.comment.common.errmsg.CommentErrorMessages;
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
import java.time.Instant;
import java.util.Date;

/**
 * comment JWT <b>验签</b> —— 只做「解析 + 验签名 + 取 claim + 校验 iat」，
 * <b>不写响应、不查 Redis</b>。
 *
 * <h2>为什么从 {@code CommentJwtAuthInterceptor} 里搬出来</h2>
 *
 * <p>🔴 <b>DRIFT-USERAPI-009②</b>：Go 的 3s 预算<b>不覆盖</b> JWT 验签，RAG 的<b>覆盖</b>。
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
 *   ⇒ 3s 计时器在 Authorize **之后**起算 ⇒ **验签耗时不计入预算**
 * </pre>
 *
 * <p>⚠️ <b>本域 3s 预算确实来自 observability（不是 go-zero native）</b>：
 * {@code service/comment/api/commentservice.go:34-36}
 * <pre>
 *   httpTimeout := observability.DisableNativeRestTimeout(&amp;c.RestConf)
 *   server := rest.MustNewServer(c.RestConf)
 *   server.Use(observability.NewHTTPMiddleware(c.Name, httpTimeout, observability.SlowThreshold()))
 * </pre>
 * ⇒ native 那条 Timeout 被关掉了 ⇒ 3s <b>只</b>由 observability 提供
 * ⇒ 「验签在预算之外」才有意义（否则 native 那条 3s 还在，照样覆盖验签）。
 *
 * <p><b>Java 侧落点</b>：Servlet 的所有 {@code Filter} 都在 {@code DispatcherServlet} <b>外面</b>，
 * {@code HandlerInterceptor} 在<b>里面</b> ⇒ 要把验签移出 3s 预算，就必须把它搬进 Filter
 * ⇒ 见 {@link GoStyleJwtAuthFilter}。
 *
 * <h2>⚠️ 本域<b>没有黑名单</b>（与 favorite / users 域不同）</h2>
 * <p>Go 侧 comment-api 的 {@code routes.go:16-56} <b>没有</b> {@code rest.WithMiddlewares}
 * ⇒ 没有 route 级黑名单中间件 ⇒ ⇒ 本类<b>不查 Redis</b>，RAG 侧
 * {@code CommentJwtAuthInterceptor} 也<b>没有</b>黑名单分支 ⇒ <b>两侧一致，无需新增</b>。
 *
 * <h2>⚠️ 本层刻意<b>不</b>校验 claim 取值</h2>
 * <p>Go 侧 {@code handler.Authorize} 只验签名与 exp，之后原样透传。
 * claim 是不是数字、能不能转成 int64，由 logic 层的
 * {@code api/internal/logic/comment/helper.go:10-29 extractUserID} 判定，
 * 失败返回 {@code ErrorTokenRuntime}（<b>1006</b>）⇒ <b>HTTP 200 + {code:1006}</b>。
 * 若在本层把它变成 401，Go 返回 200 + 1006 的场景在 RAG 上就会变成 401 —— 状态码就错了。
 */
@Component
public class CommentJwtSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(CommentJwtSignatureVerifier.class);

    private final SecretKey key;

    public CommentJwtSignatureVerifier(CommentApiProperties properties) {
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
                JwtUtil.padToHmacKeySize(properties.getUserAuth().getAccessSecret()));
    }

    /**
     * 验签 —— <b>不写响应</b>；失败时把异常装进 {@link CommentJwtVerificationResult} 交给调用方重抛。
     *
     * @param authHeader 请求的 {@code Authorization} 头（可为 null）
     * @param uri        请求路径（只用于日志）
     */
    public CommentJwtVerificationResult verify(String authHeader, String uri) {
        // ---------- 阶段 1a：Authorization 头缺失 ----------
        // Go 出处：rest/token/tokenparser.go:80-84 doParseToken → request.ParseFromRequest(r,
        //     request.AuthorizationHeaderExtractor, ...)。头缺失时 extractor 返回 errNoAuthParam
        //     → ParseToken 返回 err → authhandler.go:55 unauthorized()。
        if (authHeader == null || authHeader.isEmpty()) {
            log.warn("path {} with missing Authorization header", uri);
            return CommentJwtVerificationResult.failed(unauthorized());
        }

        // Go: golang-jwt/v4 request/oauth2.go stripBearerPrefixFromTokenString
        //     —— 大小写不敏感、不匹配时原样返回整串。三条规则都在 BearerTokenExtractor 里。
        String token = BearerTokenExtractor.tokenForSignatureVerification(authHeader);
        if (token.isEmpty()) {
            log.warn("path {} with empty Bearer token", uri);
            return CommentJwtVerificationResult.failed(unauthorized());
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
            log.warn("JWT verify failed on {} : {}", uri, t.getMessage());
            return CommentJwtVerificationResult.failed(unauthorized());
        }

        // ⚠️⚠️ **iat 校验**：jjwt **不校验** iat，Go 的 jwt 库**校验** ⇒ 这里必须自己补。
        //
        // 【Go 出处】golang-jwt/jwt/v4@v4.5.2：
        //   map_claims.go:124-141  MapClaims.Valid() → VerifyIssuedAt(now, false)
        //   map_claims.go:63-85    VerifyIssuedAt：iat 缺失 ⇒ !req ⇒ 有效；
        //                            iat 是数字 ⇒ verifyIat(iat, now)；
        //                            iat 是**其它类型** ⇒ 直接 return false（**拒绝**）
        //   claims.go:250-255      verifyIat：now >= iat 才有效（**无时钟偏移容忍**）
        //
        // 【为什么这一条很要紧】jjwt 只校验 exp / nbf，**不校验 iat**
        // ⇒ 修复前 RAG 会接受「iat 在未来」的 token，而 Go 侧会拒绝
        // ⇒ **RAG 比 Go 松**（安全面更宽）⇒ 与 AGENTS.md §3 的「差异默认判 RAG 错」一致 ⇒ 必须修。
        //
        // 【精度对齐】Go 比较的是**秒**（time.Unix(cmp,0) vs newNumericDateFromSeconds(iat)）；
        //   jjwt 的 getIssuedAt() 返回毫秒精度的 Date，但 token 里的 iat 是整秒
        //   ⇒ issuedAt.toInstant() 的毫秒部分恒为 0 ⇒ 与 Go 的秒级比较**等价**
        //   （iat == 当前秒 ⇒ 两侧都判有效；iat == 当前秒+1 ⇒ 两侧都判无效）。
        Date issuedAt = claims.getIssuedAt();
        if (issuedAt != null && issuedAt.toInstant().isAfter(Instant.now())) {
            // Go: MapClaims.Valid() → "Token used before issued" ⇒ authhandler 走 unauthorized()
            log.warn("JWT issued-at in the future on {}", uri);
            return CommentJwtVerificationResult.failed(unauthorized());
        }

        return CommentJwtVerificationResult.ok(claims);
    }

    /**
     * <b>本域 401 的唯一构造点</b> —— 验签失败的三种形态共用同一形态（Go 不区分原因）。
     *
     * <p><b>body 不会被写出去</b> —— 见 {@code CommentGlobalExceptionHandler#handleAuthentication}
     * 对 Go {@code authhandler.go:106 unauthorized()} 的逐字还原：
     * Go 只 {@code WriteHeader(401)}，<b>不写任何字节</b> ⇒ RAG 侧也落<b>空 body</b>。
     *
     * <p>⚠️ <b>业务码用 {@code ERROR_TOKEN_RUNTIME}（1006）而不是 {@code ERROR_UNAUTHORIZED}（5004）</b>
     * —— comment 域与 message 域在这里<b>不一样</b>（message 域用 5004）⇒ <b>不「顺手统一」</b>。
     */
    public static AuthenticationException unauthorized() {
        return new AuthenticationException(
                CommentErrorCode.ERROR_TOKEN_RUNTIME.getCode(),
                CommentErrorMessages.getMsg(CommentErrorCode.ERROR_TOKEN_RUNTIME.getCode()));
    }
}
