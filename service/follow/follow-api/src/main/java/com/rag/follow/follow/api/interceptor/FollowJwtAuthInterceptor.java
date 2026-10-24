package com.rag.follow.follow.api.interceptor;

import com.rag.common.exception.AuthenticationException;
import com.rag.follow.follow.api.config.FollowApiProperties;
import com.rag.follow.follow.common.errmsg.FollowErrorCode;
import com.rag.follow.follow.common.errmsg.FollowErrorMessages;
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
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.crypto.SecretKey;

/**
 * follow-api 的 JWT 验签拦截器 —— Go 端 {@code rest.WithJwt(secret)} 的等价物。
 *
 * <p><b>Go 出处（三段，缺一不可）</b>：
 * <ol>
 *   <li>{@code service/follow/api/internal/handler/routes.go:16-64}：
 *       8 个路由整体挂在 {@code server.AddRoutes(..., rest.WithJwt(serverCtx.Config.UserAuth.AccessSecret),
 *       rest.WithPrefix("/follow/v1"))} 下 —— <b>8 个端点全部要 JWT，没有 public 路径</b>；</li>
 *   <li>{@code rest/server.go:185} {@code WithJwt} → {@code handler.Authorize(secret)}
 *       （{@code rest/handler/authhandler.go:45-84}）；</li>
 *   <li>{@code rest/token/tokenparser.go:81-86}：{@code request.ParseFromRequest(r,
 *       request.AuthorizationHeaderExtractor, func(token *jwt.Token) (any, error) { return []byte(secret), nil },
 *       request.WithParser(newParser()))}，其中 {@code newParser() = jwt.NewParser(jwt.WithJSONNumber())}。</li>
 * </ol>
 *
 * <p><b>⚠️ {@code WithJSONNumber()} 是本类与「identity 解析」之间的关键契约</b>：
 * 它让 JWT 里的数字 claim 变成 Go 的 {@code json.Number} 而不是 {@code float64}，
 * 而 {@code api/internal/logic/follow/helper.go:13-33} 的 {@code extractUserID}
 * <b>正是按 {@code json.Number} 分支设计的</b>。取值判定见
 * {@code FollowApiSupport#userId}。
 *
 * <p><b>为什么黑名单另起一个拦截器、而不是像 favorite-api 那样并进来</b>：
 * Go 侧 follow/api 是<b>两段独立中间件</b>（{@code routes.go:17-19} 的
 * {@code rest.WithMiddlewares([CheckBlacklistMiddleware], routes...)} +
 * 外层 {@code rest.WithJwt}），且它们的<b>失败响应形状不同</b>（见
 * {@link FollowBlacklistInterceptor} 类注释）。合成一个类就必须用两个异常类型
 * 区分两种 body，反而把一个安全边界藏进方法内部。拆成两个拦截器后，
 * 「JWT 失败 = 401 空 body」与「黑名单命中 = 401 + JSON body」在类结构上就是分开的。
 *
 * <h3>⚠️ 本层刻意<b>不</b>校验 claim 的取值</h3>
 * <ul>
 *   <li>Go 侧 {@code handler.Authorize}（{@code authhandler.go:65-79}）只验签名与 exp，
 *       随后把所有非标准 claim（aud/exp/jti/iat/iss/nbf/sub 除外）原样塞进 ctx；</li>
 *   <li>claim 是不是数字、能不能转成 int64，由 logic 层
 *       {@code helper.go:13-33} 的 {@code extractUserID} 判定，失败返回
 *       {@code ErrorUnauthorized}（<b>5004</b>），最终输出是
 *       <b>HTTP 200 + {code:5004,msg:"unauthorized"}</b>；</li>
 *   <li>若在本层把它变成 401，Go 返回 200 + 5004 的场景在 RAG 上会变成 401 —— 状态码就错了。</li>
 * </ul>
 */
@Component
public class FollowJwtAuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(FollowJwtAuthInterceptor.class);

    /**
     * 与 Go {@code authhandler.go:71-79} 里 {@code ctx.Value("userId")} 的 key 同名。
     * 存的是 claim 的<b>原始对象</b>（不是 long），原因见类注释 —— 取值判定属于业务层。
     */
    public static final String CLAIM_ATTRIBUTE = "userId";

    private final SecretKey key;

    public FollowJwtAuthInterceptor(FollowApiProperties properties) {
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

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // ── Go 的「路由匹配先于鉴权」：未注册路径**不经 auth** ──
        // 证据：rest/engine.go:101-116 的 bindRoute 只把 chain（native + auth + 用户中间件）
        //       挂到 router.Handle(method, path, handle) 上，即**只对匹配上的路由生效**；
        //       router/patrouter.go:73,79 的 404 / 405 分支根本进不了这条链。
        //       ⇒ Go 侧 /follow/v1/nope 恒为 404，与有没有 token 无关。
        //
        // ⚠️ Spring 侧默认**做不到**：Boot 的静态资源处理器匹配 /**，
        //    于是未注册路径**也拿到了 handler**，mappedHandler != null ⇒ 拦截器照跑
        //    ⇒ 404 被鉴权遮蔽成 401。
        //
        // 判据用 `handler instanceof HandlerMethod`：它精确对应 Go 那句
        // 「有没有匹配上 @RequestMapping 方法」——ResourceHttpRequestHandler 不是 HandlerMethod。
        // ⚠️ 不要改成「看路径是否以 /follow/v1/ 开头」之类的字符串判断：那是重新发明一遍路由表。
        // 完整证据（含真 Tomcat 探针输出）见 com.rag.common.http.GoStyleRouterErrorResponses。
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        // Go: rest/token/tokenparser.go:81-86 doParseToken → request.ParseFromRequest(r,
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
        //     逐个塞进 ctx。follow api 侧只有 logic/follow/helper.go 的 extractUserID 读
        //     "userId"，其余 claim 无消费者，故这里只落这一个。
        request.setAttribute(CLAIM_ATTRIBUTE, claims.get(CLAIM_ATTRIBUTE));
        return true;
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
     * {@link FollowBlacklistInterceptor} 那条<b>有 body</b>的 401。
     */
    private static AuthenticationException unauthorized() {
        int code = FollowErrorCode.ERROR_UNAUTHORIZED.getCode();
        return new AuthenticationException(code, FollowErrorMessages.getMsg(code));
    }
}