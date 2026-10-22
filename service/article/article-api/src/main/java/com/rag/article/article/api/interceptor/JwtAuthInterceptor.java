package com.rag.article.article.api.interceptor;

import com.rag.article.article.api.config.ArticleApiProperties;
import com.rag.common.exception.AuthenticationException;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import javax.crypto.SecretKey;
import java.util.List;

/**
 * article-api 的 JWT 鉴权 —— Go 端第一段 {@code server.AddRoutes(..., rest.WithJwt(secret), ...)}
 * 的等价物。
 *
 * <h2>Go 出处（{@code internal/handler/routes.go:19-38}）</h2>
 * <pre>
 *   server.AddRoutes([]rest.Route{
 *       {POST,   "/article",        CreateArticleHandler},        ← 第一段：带 WithJwt
 *       {PUT,    "/article/:id",    UpdateArticleHandler},
 *       {DELETE, "/article/:id",    DeleteArticleHandler},
 *       {GET,    "/me/article/:id", GetAuthorArticleHandler},
 *   }, rest.WithJwt(serverCtx.Config.Auth.AccessSecret),
 *      rest.WithPrefix("/v1"), rest.WithTimeout(70*time.Second))
 *
 *   server.AddRoutes([]rest.Route{
 *       {GET, "/article/:id", GetArticleHandler},                 ← 第二段：**无** WithJwt
 *       {GET, "/articles",   ListArticlesHandler},
 *   }, rest.WithPrefix("/v1"))
 *
 *   server.AddRoutes([]rest.Route{
 *       {POST, "/upload", UploadImageHandler},                    ← 第三段：**无** WithJwt
 *   }, rest.WithPrefix("/v1"), rest.WithMaxBytes(10MB))
 * </pre>
 * ⇒ 7 个端点里<b>恰好 4 个</b>要 JWT。<b>特别是 {@code POST /v1/upload} 是公开的</b> ——
 * 「上传图片要登录」是一个很容易凭直觉加上去、但 Go 侧<b>没有</b>的规则。
 *
 * <h2>为什么必须带 method 一起匹配（而不是只用路径）</h2>
 *
 * <p>Spring 的 {@code addPathPatterns} 只按<b>路径</b>匹配，不看 method。
 * 若把 {@code /v1/article/{id}} 整条交给拦截器：
 * <pre>
 *   POST /v1/article/123        Spring: 拦截器 → 401（无 token）
 *                               Go:     该路由未注册 → 405，且**不进鉴权链**
 * </pre>
 * ⇒ 凭空多出一条「401 压过 405」的路径。
 * 所以本类持有 {@link ProtectedRoute}（method + PathPattern）表，在
 * {@code preHandle} 里自行判定；{@code WebMvcConfig} 只需把本拦截器
 * 挂在 {@code /v1/**} 上。
 *
 * <h2>「路由匹配先于鉴权」这条守卫也要保留</h2>
 * <pre>
 *   rest/engine.go:104-118   bindRoute 只把 chain（native + auth + 用户中间件）
 *                            挂到 router.Handle(method, path, handle) 上
 *   rest/router/patrouter.go:73,79   404 / 405 分支根本进不了这条链
 * </pre>
 * ⇒ Go 侧 {@code /v1/nope} 恒 404，与有没有 token 无关。
 * Spring 侧默认做不到（Boot 静态资源处理器匹配 {@code /**} ⇒ 未注册路径也拿到 handler
 * ⇒ 鉴权把 404 遮蔽成 401）。守卫是 {@code handler instanceof HandlerMethod}。
 * 本条在本模块<b>必须</b>存在：article-api 有 3 个<b>公开</b>端点，
 * 一旦这条守卫丢失，404 的形状会随路径落在受保护区还是公开区而变化。
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
 *
 * <h2>401 的 body 是<b>空的</b></h2>
 * <pre>
 *   rest/handler/authhandler.go:106-122  unauthorized()
 *       writer.WriteHeader(http.StatusUnauthorized)     ← 只写状态码，不写 body、不设 Content-Type
 * </pre>
 * ⇒ {@code GlobalExceptionHandler#handleAuthentication} 返回
 * {@code ResponseEntity.status(401).build()}（<b>不能</b>是 {@code .body(...)}，
 * 否则容器会补一个 {@code Content-Type}，多出一项响应头）。
 */
@Component
public class JwtAuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthInterceptor.class);

    /**
     * 与 Go {@code extractCurrentUserID} 里 {@code ctx.Value("userId")} 的 key 同名。
     * 存的是 claim 的<b>原始对象</b>（不是 String / long），原因见类注释「不校验 claim 的取值」。
     */
    public static final String CLAIM_ATTRIBUTE = "userId";

    /**
     * article-api 的<b>全部</b>受保护路由，逐条对应 Go {@code routes.go} 第一段。
     *
     * <p>用 {@link PathPattern} 而不是字符串前缀比较：{@code /v1/article} 与
     * {@code /v1/articles} 在前缀语义下会互相命中，而 Go 的 router 是
     * <b>逐段精确匹配</b>（{@code /v1/articles} <b>不是</b> {@code /v1/article} 的匹配）。
     * 前缀式判断会把两个不同的路由合并成一个安全边界 —— 而它们一个要鉴权一个不要。
     */
    private static final List<ProtectedRoute> PROTECTED_ROUTES = List.of(
            new ProtectedRoute(HttpMethod.POST, "/v1/article"),
            new ProtectedRoute(HttpMethod.PUT, "/v1/article/{id}"),
            new ProtectedRoute(HttpMethod.DELETE, "/v1/article/{id}"),
            new ProtectedRoute(HttpMethod.GET, "/v1/me/article/{id}"));

    private final SecretKey key;

    public JwtAuthInterceptor(ArticleApiProperties properties) {
        this.key = Keys.hmacShaKeyFor(
                JwtUtil.padToHmacKeySize(properties.getAuth().getAccessSecret()));
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // ── 守卫 1：路由匹配先于鉴权（未注册路径不进鉴权链） ──
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        // ── 守卫 2：只有「method + path」双双命中受保护表才鉴权 ──
        //    public 端点（GET /v1/article/:id、GET /v1/articles、POST /v1/upload）
        //    以及「路径像但 method 不对」的组合都在这里放行。
        if (!isProtected(request)) {
            return true;
        }

        // Go: rest/token/tokenparser.go doParseToken → request.ParseFromRequest(r,
        //     request.AuthorizationHeaderExtractor, ...)。头缺失时 extractor 返回 errNoAuthParam
        //     → parser.ParseToken 返回 err → authhandler.go:100 unauthorized()。
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || authHeader.isEmpty()) {
            log.warn("article-api path {} with missing Authorization header", request.getRequestURI());
            throw unauthorized();
        }

        // Go: golang-jwt/v4 request/oauth2.go stripBearerPrefixFromTokenString
        //     —— 大小写不敏感、不匹配时原样返回整串。三条规则都在 BearerTokenExtractor 里。
        String token = BearerTokenExtractor.tokenForSignatureVerification(authHeader);
        if (token.isEmpty()) {
            log.warn("article-api path {} with empty Bearer token", request.getRequestURI());
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
            log.warn("article-api JWT verify failed on {} : {}", request.getRequestURI(), t.getMessage());
            throw unauthorized();
        }

        // Go: authhandler.go:63-78 遍历 claims，把非标准 claim（aud/exp/jti/iat/iss/nbf/sub 除外）
        //     逐个塞进 ctx。article-api 侧只有 helper.go:18 的 extractCurrentUserID 读
        //     "userId"，其余 claim 无消费者，故这里只落这一个。
        request.setAttribute(CLAIM_ATTRIBUTE, claims.get(CLAIM_ATTRIBUTE));
        return true;
    }

    /**
     * 请求是否命中受保护路由。
     *
     * <p>⚠️ 取的是 {@code requestURI - contextPath}：Go 的 router 看到的是<b>剥掉前缀之后</b>的路径
     * （{@code rest.WithPrefix("/v1")} 已在注册时拼好）。本模块的 context-path 是 {@code /}，
     * 减掉它等于原样；但仍写成减法，是为了在有人改 context-path 时不会静默错位
     * —— 那会让 {@code /v1/article} 永远匹配不上，<b>鉴权整体失效</b>
     * （比报错更危险：所有写接口变公开）。
     */
    private boolean isProtected(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        // ⚠️ PathPattern.matches 只接受 PathContainer（Spring 7 的签名），
        //    不是 String —— 传 String 编译不过。
        PathContainer pathContainer = PathContainer.parsePath(path);
        for (ProtectedRoute route : PROTECTED_ROUTES) {
            if (route.method().matches(request.getMethod())
                    && route.pattern().matches(pathContainer)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 统一构造鉴权失败。
     *
     * <p>业务码填 {@code 1005}（article 域的 {@code ErrorUnauthorized}）
     * <b>只是为了让日志可读</b>：{@code GlobalExceptionHandler#handleAuthentication}
     * 返回的是<b>空 body 的 401</b>，这个码<b>不会</b>出现在响应里。
     * ⚠️ 别把这个码当成「客户端能看到 1005」—— 能看到 1005 的路径是
     * {@code extractCurrentUserID} 取不到 userId 的<b>另一条</b>（见类注释）。
     */
    private static AuthenticationException unauthorized() {
        return new AuthenticationException(
                com.rag.article.article.common.errmsg.ErrorCode.ERROR_UNAUTHORIZED.getCode(),
                com.rag.article.article.common.errmsg.ErrorCode.getMsg(
                        com.rag.article.article.common.errmsg.ErrorCode.ERROR_UNAUTHORIZED.getCode()));
    }

    /** 一条受保护路由 = {@code method} + {@code path} 模板。缺一不可（见类注释）。 */
    private record ProtectedRoute(HttpMethod method, PathPattern pattern) {
        ProtectedRoute(HttpMethod method, String pattern) {
            this(method, PathPatternParser.defaultInstance.parse(pattern));
        }
    }
}