package com.rag.favorite.favorite.api.interceptor;

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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.method.HandlerMethod;

import javax.crypto.SecretKey;

/**
 * favorite-api 的 JWT 鉴权拦截器 —— 合并 Go 端 {@code rest.WithJwt} +
 * {@code CheckBlacklistMiddleware} 两段中间件的职责。
 *
 * <p><b>Go 出处</b>：
 * <ul>
 *   <li>{@code service/favorite/api/internal/handler/routes.go:16-59}
 *       —— 7 个路由<b>全量</b> {@code rest.WithMiddlewares([CheckBlacklistMiddleware], ...)}
 *       + {@code rest.WithJwt(serverCtx.Config.UserAuth.AccessSecret)}；</li>
 *   <li>{@code service/favorite/api/internal/middleware/checkblacklist_middleware.go:22-50}；</li>
 *   <li>{@code service/favorite/api/internal/logic/favorite/helper.go:14-34}
 *       —— {@code extractUserID}（消费 {@code ctx.Value("userId")}）。</li>
 * </ul>
 *
 * <p><b>与 Go 逐条对齐的行为（L1，不可改）</b>：
 * <ol>
 *   <li>Authorization 头缺失 / 为空 → <b>HTTP 401</b></li>
 *   <li>剥掉 {@code Bearer } 后 token 为空 → 401
 *       （Go: {@code golang-jwt/jwt/v4@v4.5.2/request/oauth2.go} 的
 *       {@code stripBearerPrefixFromTokenString}，三条规则见 {@link BearerTokenExtractor}）</li>
 *   <li>签名错 / 过期 / 格式错 → 401。<b>Go 不区分原因</b>，三种失败都只走到同一个
 *       {@code unauthorized()}</li>
 *   <li>验签成功后：把 claim {@code userId} 的<b>原始对象</b>写进 request attribute</li>
 *   <li>token 在 Redis 黑名单（{@code user:jwt_blacklist:<token>}）→ <b>HTTP 401</b></li>
 *   <li>Redis 故障 → <b>不阻塞业务（fail-open）</b></li>
 * </ol>
 *
 * <h3>⚠️ 执行顺序：<b>先验签、后黑名单</b>（与 Go 的 chain 内外关系一致）</h3>
 * <p>Go 出处三段，缺一不可：
 * <ol>
 *   <li>{@code rest/engine.go:101-113} {@code bindRoute}：先 {@code appendAuthHandler(JWT)}，
 *       再 {@code ng.middlewares}，最后 {@code chn.ThenFunc(route.Handler)}；</li>
 *   <li>{@code rest/chain/chain.go} 的 {@code Then} 是 {@code m0(m1(...mN(h)))}，
 *       <b>索引 0 最外层、先执行</b>；{@code Append} 追加到尾部 = 最内层；</li>
 *   <li>{@code rest/server.go:214-233} {@code WithMiddlewares}/{@code WithMiddleware}
 *       在<b>构造期</b>就把 {@code route.Handler} 包成 {@code middleware(route.Handler)}
 *       —— 黑名单是 {@code ThenFunc} 的<b>参数</b>，比 chain 任何元素都内层。</li>
 * </ol>
 * ⇒ 真实顺序：… Timeout → Recover → … → <b>Authorize(JWT)</b> → observability
 * → <b>CheckBlacklist</b> → handler。
 *
 * <p>可观察差异（顺序反了会怎样）：一个<b>签名无效但已在黑名单中</b>的 token，
 * Go → {@code Authorize} 先失败 → 401，<b>黑名单从不查询</b>；
 * 反过来则会对畸形 token 产生一次多余的 Redis 读。
 *
 * <h3>⚠️ 黑名单 token 的取法与 Go 的 users 域<b>不同</b>（本域特有，勿"顺手统一"）</h3>
 * <p>Go 侧本域中间件（{@code checkblacklist_middleware.go:30}）是：
 * <pre>
 *   token := strings.TrimSpace(strings.TrimPrefix(authHeader, "Bearer "))
 * </pre>
 * 即「大小写<b>敏感</b>的精确前缀 TrimPrefix」<b>再</b> TrimSpace。
 * 而 users/admin 域（{@code service/user/user/api/internal/middleware/checkblacklistmiddleware.go:28}）
 * 是 {@code strings.TrimPrefix(authHeader, "Bearer ")}，<b>没有</b> TrimSpace。
 * <p>⇒ 本实现刻意<b>不</b>直接复用 {@link BearerTokenExtractor#trimBearerPrefix}，
 * 而是在其结果上再补一次 Go 语义的 TrimSpace。
 *
 * <p>而<b>验签</b>用的取法与 users 域<b>一致</b>（大小写不敏感、缺失前缀时原样返回整串），
 * 因为两边都走 go-zero 的 {@code handler.Authorize}。
 * ⇒ 于是本域也存在 Go 自身的不一致：{@code "bearer <token>"} 验签用 {@code "<token>"}、
 * 黑名单查 {@code "bearer <token>"}。这是 Go 的行为，<b>原样保留</b>。
 */
@Component
public class FavoriteJwtAuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(FavoriteJwtAuthInterceptor.class);

    /**
     * 与 Go {@code authhandler.go:71-79} 里 {@code ctx.Value("userId")} 的 key 同名。
     * 存的是 claim 的<b>原始对象</b>（不是 long），原因见类注释。
     */
    public static final String CLAIM_ATTRIBUTE = "userId";

    /**
     * Go {@code checkblacklist_middleware.go:48} 写进 ctx 的 key
     * （{@code context.WithValue(r.Context(), "jwt_token", token)}）。
     */
    public static final String JWT_TOKEN_ATTRIBUTE = "jwt_token";

    /**
     * Redis 黑名单 key 前缀 —— Go {@code checkblacklist_middleware.go:36}
     * 的 {@code fmt.Sprintf("user:jwt_blacklist:%s", token)}。
     *
     * <p>⚠️ <b>必须是 {@code user:} 而不是 {@code admin:}</b>：Go 侧 favorite/api
     * 用的就是 {@code user:jwt_blacklist:}，与 user-api 的登出写入端同 key
     * （user-rpc 登出写的就是这个 key）⇒ favorite-api 才能拦住已登出的 token。
     */
    private static final String BLACKLIST_KEY_PREFIX = "user:jwt_blacklist:";

    private final SecretKey key;
    private final StringRedisTemplate redisTemplate;

    public FavoriteJwtAuthInterceptor(FavoriteApiProperties properties, StringRedisTemplate redisTemplate) {
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
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // ── Go 的「路由匹配先于鉴权」：未注册路径**不经 auth** ──
        // 证据：rest/engine.go:104-118 的 bindRoute 只把 chain（native + auth + 用户中间件）
        //       挂到 router.Handle(method, path, handle) 上，即**只对匹配上的路由生效**；
        //       router/patrouter.go:73,79 的 404 / 405 分支根本进不了这条链。
        //       ⇒ Go 侧 /favorite/v1/nope 恒为 404，与有没有 token 无关。
        //
        // ⚠️ Spring 侧默认**做不到**：Boot 的静态资源处理器匹配 `/**`，
        //    于是未注册路径**也拿到了 handler**，mappedHandler != null ⇒ 拦截器照跑
        //    ⇒ 404 被鉴权遮蔽成 401。
        //
        // 判据用 `handler instanceof HandlerMethod`：它精确对应 Go 那句
        // 「有没有匹配上 @RequestMapping 方法」——ResourceHttpRequestHandler 不是 HandlerMethod。
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        // ================= 阶段 1：JWT 验签 =================
        // Go: rest/token/tokenparser.go:80-84 doParseToken → request.ParseFromRequest(r,
        //     request.AuthorizationHeaderExtractor, ...)。头缺失时 extractor 返回 errNoAuthParam
        //     → ParseToken 返回 err → authhandler.go:56 unauthorized()。
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || authHeader.isEmpty()) {
            log.warn("path {} with missing Authorization header", request.getRequestURI());
            throw unauthorized();
        }

        // Go: golang-jwt/v4 request/oauth2.go stripBearerPrefixFromTokenString
        //     —— 大小写不敏感、不匹配时原样返回整串。三条规则都在 BearerTokenExtractor 里。
        String token = BearerTokenExtractor.tokenForSignatureVerification(authHeader);
        if (token.isEmpty()) {
            log.warn("path {} with empty Bearer token", request.getRequestURI());
            throw unauthorized();
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
            log.warn("JWT verify failed on {} : {}", request.getRequestURI(), t.getMessage());
            throw unauthorized();
        }

        // Go: authhandler.go:71-79 遍历 claims，把非标准 claim（aud/exp/jti/iat/iss/nbf/sub 除外）
        //     逐个塞进 ctx。favorite api 侧只有 7 个 logic 的 ctx.Value("userId") 读它，
        //     其余 claim 无消费者，故这里只落这一个。
        request.setAttribute(CLAIM_ATTRIBUTE, claims.get(CLAIM_ATTRIBUTE));

        // ================= 阶段 2：黑名单检查 =================
        // Go checkblacklist_middleware.go:22-50：
        //   authHeader := r.Header.Get("Authorization"); if authHeader == "" { next; return }
        //   token := strings.TrimSpace(strings.TrimPrefix(authHeader, "Bearer "))
        //   if token == "" { next; return }
        //   exists, err := m.Redis.ExistsCtx(r.Context(), blackListKey)
        //   if err == nil && exists { 401 + body }
        //   ctx := context.WithValue(r.Context(), "jwt_token", token)
        //
        // 注意：Go 内部【两段取法本就不同】——Authorize 用大小写不敏感的 strip，
        // 这里用大小写敏感的 TrimPrefix + TrimSpace。故 "bearer <token>" 在 Go 里
        // 验签用 "<token>"、黑名单查 "bearer <token>"。这是 Go 自身的不一致，本实现原样保留。
        String jwtToken = goTrimSpace(BearerTokenExtractor.trimBearerPrefix(authHeader));
        if (!jwtToken.isEmpty() && isBlacklisted(jwtToken)) {
            log.warn("favorite token in blacklist, uri={}", request.getRequestURI());
            throw unauthorized();
        }

        // Go 只有在 header 非空且 TrimPrefix 结果非空时才写 jwt_token；
        // 上面已排除两种为空的分支，此处必然写入。
        request.setAttribute(JWT_TOKEN_ATTRIBUTE, jwtToken);
        if (!jwtToken.equals(token)) {
            // 与 Go 一致的不一致：非 "Bearer <token>" 规范写法下两段取法不同。保留 warn 便于排查。
            log.warn("favorite blacklist token != jwt token (auth header is not 'Bearer <token>')");
        }
        return true;
    }

    /**
     * Redis 黑名单查询 —— fail-open 与 Go 逐条对齐。
     *
     * <p>Go: {@code exists, err := m.Redis.ExistsCtx(...); if err == nil && exists { 401 }}
     * （{@code service/favorite/api/internal/middleware/checkblacklist_middleware.go:37}）。
     * Redis 故障时 {@code err != nil} ⇒ 条件为 false ⇒ <b>放行</b>，不阻塞业务。
     * 本实现把异常吞成 {@code null}，随后 {@code exists != null && exists} 同样为 false ⇒ 放行。
     */
    private boolean isBlacklisted(String token) {
        String blacklistKey = BLACKLIST_KEY_PREFIX + token;
        Boolean exists;
        try {
            exists = redisTemplate.hasKey(blacklistKey);
        } catch (Throwable t) {
            log.warn("favorite Redis blacklist check failed : {}", t.getMessage());
            exists = null;
        }
        return exists != null && exists;
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

    /**
     * 统一构造鉴权失败。
     *
     * <p><b>body 形状</b>：Go 的 {@code unauthorized()}（{@code authhandler.go:106-122}）
     * 只调 {@code writer.WriteHeader(http.StatusUnauthorized)}，<b>不写任何字节</b>。
     * Go 的黑名单分支（{@code checkblacklist_middleware.go:39-45}）则写
     * {@code {"code":5004,"msg":"未登录或登录已失效"}}（<b>两字段，没有 {@code data}</b>）。
     * <p>本端统一成完整 envelope（{@code {code,msg,data}}）—— 与 users/admin/comment 域同款。
     * <p>⚠️ <b>两侧 body 的字段集合不同</b>（Go 空 body / 两字段；RAG 三字段），
     * 但 <b>HTTP 状态码 401 与 code 5004 是对齐的</b> ⇒ 属表达层差异，<b>待签字</b>，
     * 登记在 {@code docs/Intentional_Deviations/favorite/api.md}（DEV-FAV-API-001）。
     * <p>⚠️ <b>不要</b>把「§3.4 已登记」当成依据：§3.4 是<b>proto 复制</b>那一节，
     * 与本条无关（此前本类的注释误引了它）。
     */
    private static AuthenticationException unauthorized() {
        int code = FavoriteErrorCode.ERROR_UNAUTHORIZED.getCode();
        return new AuthenticationException(code, FavoriteErrorMessages.getMsg(code));
    }
}
