package com.rag.favorite.favorite.api.auth;

import com.rag.common.exception.AuthenticationException;
import com.rag.favorite.favorite.api.config.FavoriteApiProperties;
import com.rag.favorite.favorite.common.errmsg.FavoriteErrorCode;
import com.rag.favorite.favorite.common.errmsg.FavoriteErrorMessages;
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
 * favorite JWT <b>验签</b> —— 只做「解析 + 验签名 + 取 claim」，<b>不写响应、不查 Redis</b>。
 *
 * <h2>为什么从 {@code FavoriteJwtAuthInterceptor} 里搬出来</h2>
 *
 * <p>🔴 <b>DRIFT-USERAPI-009②</b>：Go 的 3s 预算<b>不覆盖</b> JWT 验签，RAG 的<b>覆盖</b>。
 *
 * <p><b>Go 侧链序（真源，逐行核实 go-zero@v1.10.2）</b>：
 * <pre>
 *   rest/engine.go:101-116  bindRoute：
 *     chn = ng.buildChainWithNativeMiddlewares(...)   // 原生中间件（索引 0 = 最外层）
 *     chn = ng.appendAuthHandler(fr, chn, verifier)   // ← JWT 验签在这里 append（:108）
 *     for _, mw := range ng.middlewares { ... }        // ← observability(3s) 在这之后 append（:110-112）
 *     handle = chn.ThenFunc(route.Handler)
 *   rest/chain/chain.go  Append = 「加在请求流的最后」⇒ 越 Append 越靠内
 *
 *   ⇒ 执行序：原生中间件 → **JWT 验签** → **observability(3s)** → 黑名单 → handler
 *   ⇒ 3s 计时器在 Authorize **之后**起算 ⇒ **验签耗时不计入预算**
 * </pre>
 *
 * <p>⚠️ <b>本域的 3s 预算确实来自 observability（不是 go-zero native）</b>：
 * {@code service/favorite/api/favorite.go:28-30}
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
 * <h2>⚠️ 与 users/admin 域<b>刻意不同</b>的地方（勿"顺手统一"）</h2>
 * <ol>
 *   <li><b>不校验 claim 取值</b>：Go 侧 {@code handler.Authorize}
 *       （{@code rest/handler/authhandler.go:71-79}）只验签名与 exp，随后把 claim
 *       <b>原样</b>塞进 ctx；claim 能不能变成 int64 是<b>业务层</b>的判定
 *       ⇒ 本类<b>不</b>因畸形 claim 拒绝（畸形 claim 的后果落在
 *       {@code FavoriteApiSupport#userId} ⇒ HTTP 200 + {code:5004}，<b>不是 401</b>）。</li>
 *   <li><b>黑名单 token 的取法</b>：本域 Go 侧中间件是
 *       {@code strings.TrimSpace(strings.TrimPrefix(authHeader, "Bearer "))}
 *       （<b>大小写敏感</b>的精确前缀 TrimPrefix，<b>再</b> TrimSpace），
 *       而验签用的是大小写<b>不敏感</b>的 strip ⇒ 两者<b>可以不同</b>
 *       ⇒ 这是 <b>Go 自身的不一致</b>，本实现<b>原样保留</b>。</li>
 * </ol>
 */
@Component
public class FavoriteJwtSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(FavoriteJwtSignatureVerifier.class);

    private final SecretKey key;

    public FavoriteJwtSignatureVerifier(FavoriteApiProperties properties) {
        // L4 idiomatization（强制留痕 + Go ↔ RAG 对照）：
        //   Go 出处：go-zero rest/handler/authhandler.go:84 的
        //     `func(token *jwt.Token) (any, error) { return []byte(secret), nil }`
        //     —— Go 端 golang-jwt 不校验 key 长度，36 字节的 favorite secret 能直接签。
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
     * 验签 —— <b>不写响应</b>；失败时把异常装进 {@link FavoriteJwtVerificationResult} 交给调用方重抛。
     *
     * @param authHeader 请求的 {@code Authorization} 头（可为 null）
     * @param uri        请求路径（只用于日志）
     */
    public FavoriteJwtVerificationResult verify(String authHeader, String uri) {
        // ---------- 阶段 1a：Authorization 头缺失 ----------
        // Go 出处：rest/token/tokenparser.go:80-84 doParseToken → request.ParseFromRequest(r,
        //     request.AuthorizationHeaderExtractor, ...)。头缺失时 extractor 返回 errNoAuthParam
        //     → ParseToken 返回 err → authhandler.go:56 unauthorized()。
        if (authHeader == null || authHeader.isEmpty()) {
            log.warn("path {} with missing Authorization header", uri);
            return FavoriteJwtVerificationResult.failed(unauthorized());
        }

        // Go: golang-jwt/v4 request/oauth2.go stripBearerPrefixFromTokenString（golang-jwt/jwt/v4@v4.5.2/request/oauth2.go:8）
        //     —— 大小写不敏感、不匹配时原样返回整串。三条规则都在 BearerTokenExtractor 里。
        String token = BearerTokenExtractor.tokenForSignatureVerification(authHeader);
        if (token.isEmpty()) {
            log.warn("path {} with empty Bearer token", uri);
            return FavoriteJwtVerificationResult.failed(unauthorized());
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
            return FavoriteJwtVerificationResult.failed(unauthorized());
        }

        // 黑名单 key 用的 token —— Go 的 CheckBlacklistMiddleware 用的是【TrimPrefix 结果】，
        // 与验签用的 token 是**两套取法**（Go 自身如此，畸形 header 下会不同）。
        String trimmedToken = goTrimSpace(BearerTokenExtractor.trimBearerPrefix(authHeader));
        return FavoriteJwtVerificationResult.ok(claims, token, trimmedToken);
    }

    /**
     * <b>本域 401 的唯一构造点</b> —— 验签失败与黑名单命中共用同一形态。
     *
     * <p><b>body 形状</b>：Go 的 {@code unauthorized()}（{@code authhandler.go:106-122}）
     * 只调 {@code writer.WriteHeader(http.StatusUnauthorized)}，<b>不写任何字节</b>。
     * Go 的黑名单分支（{@code checkblacklist_middleware.go:39-45}）则写
     * {@code {"code":5004,"msg":"未登录或登录已失效"}}（<b>两字段，没有 {@code data}</b>）。
     * <p>本端统一成完整 envelope（{@code {code,msg,data}}）—— 与 users/admin/comment 域同款。
     * <p>⚠️ <b>两侧 body 的字段集合不同</b>（Go 空 body / 两字段；RAG 三字段），
     * 但 <b>HTTP 状态码 401 与 code 5004 是对齐的</b> ⇒ 属表达层差异，<b>已签字</b>，
     * 登记在 {@code docs/Intentional_Deviations/favorite/api.md}（DEV-FAV-API-001）。
     */
    public static AuthenticationException unauthorized() {
        int code = FavoriteErrorCode.ERROR_UNAUTHORIZED.getCode();
        return new AuthenticationException(code, FavoriteErrorMessages.getMsg(code));
    }

    /**
     * Go {@code strings.TrimSpace} 的等价实现。
     *
     * <p>⚠️ <b>刻意不用 {@link String#trim()}、也不用 {@link String#strip()}</b>：
     * <ul>
     *   <li>{@code trim()} 只裁 ASCII ≤ ' '，不裁 U+00A0 / U+0085；</li>
     *   <li>{@code strip()} 用 {@link Character#isWhitespace}，它<b>不</b>包含
     *       U+00A0（NBSP）与 U+0085（NEL），而 Go 的 {@code unicode.IsSpace} 包含。</li>
     * </ul>
     * 本方法按 Go 的 {@code unicode.IsSpace} 判定表逐条列出，两侧行为完全一致。
     *
     * <p>（可观察性说明：只有「token 前后带 Unicode 空白」这一形态会走到这里，
     * 而那种 token 必然在验签阶段就失败 ⇒ 本方法实际只在标准写法下生效；
     * 仍然逐条对齐是为了不留下「换个 key 再改一次」的地雷。）
     */
    private static String goTrimSpace(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        int start = 0;
        int end = s.length();
        while (start < end && isGoSpace(s.charAt(start))) {
            start++;
        }
        while (end > start && isGoSpace(s.charAt(end - 1))) {
            end--;
        }
        return s.substring(start, end);
    }

    /** Go {@code unicode.IsSpace} 的判定表（{@code unicode.IsSpace} / {@code unicode.White_Space}）。 */
    private static boolean isGoSpace(char c) {
        switch (c) {
            case '\t':
            case '\n':
            case '\u000B':
            case '\f':
            case '\r':
            case ' ':
            case '\u0085':
            case '\u00A0':
                return true;
            default:
                // 其余 Unicode 空白：Java 的 Character.isWhitespace 与 Go 的 unicode.IsSpace
                // 在这一段上取值一致（Java 不含 U+00A0/U+0085，已在上面单列）。
                return Character.isWhitespace(c);
        }
    }
}
