package com.rag.like.like.api.interceptor;

import com.rag.common.exception.AuthenticationException;
import com.rag.like.like.api.config.LikeApiProperties;
import com.rag.like.like.common.errmsg.LikeErrorCode;
import com.rag.like.like.common.errmsg.LikeErrorMessages;
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
import org.springframework.web.servlet.HandlerInterceptor;

import javax.crypto.SecretKey;

/**
 * like-api 的 JWT 鉴权拦截器 —— Go 端 {@code rest.WithJwt(secret)} 的等价物。
 *
 * <p><b>Go 出处</b>：{@code service/like/api/internal/handler/routes.go:49} 的
 * {@code rest.WithJwt(serverCtx.Config.UserAuth.AccessSecret)}
 * → {@code rest/server.go:185} 设置 {@code featuredRoutes.jwt.enabled}
 * → {@code rest/engine.go:70-79 appendAuthHandler} → {@code handler.Authorize(secret)}
 * （{@code rest/handler/authhandler.go}）。
 *
 * <p><b>与 Go 逐条对齐的 4 条行为（L1，不可改）</b>：
 * <ol>
 *   <li>Authorization 头缺失 / 为空 → <b>HTTP 401</b></li>
 *   <li>Bearer 前缀剥离失败 / token 为空 → 401
 *       （Go: {@code golang-jwt/jwt/v4@v4.5.2/request/oauth2.go} 的
 *       {@code stripBearerPrefixFromTokenString}，三条规则见 {@link BearerTokenExtractor}）</li>
 *   <li>签名错 / 过期 / 格式错 → 401。<b>Go 不区分原因</b>，
 *       三种失败都只走到同一个 {@code unauthorized()}</li>
 *   <li>验签成功后：把 claim {@code userId} 的<b>原始对象</b>写进 request attribute</li>
 * </ol>
 *
 * <h3>⚠️ 本层刻意<b>不</b>校验 claim 的取值</h3>
 * <p>这是与 Go 一致的<b>关键分层</b>，改了就构成行为回归：
 * <ul>
 *   <li>Go 侧 {@code handler.Authorize} 只验签名与 exp，
 *       随后把<b>所有非标准 claim</b>（{@code aud/exp/jti/iat/iss/nbf/sub} 除外）
 *       <b>原样</b>塞进 ctx；</li>
 *   <li>claim 是不是数字、能不能转成 int64，由<b>业务层</b>判定 ——
 *       {@code getlikestatelogic.go:37-49} 等 4 处 {@code ctx.Value("userId").(json.Number)}
 *       断言，失败返回 {@code NewErrCode(ErrorTokenRuntime)}（<b>1006</b>），
 *       最终输出是 <b>HTTP 200 + {code:1006,msg:"TOKEN已过期"}</b>。</li>
 *   <li>若在本层把它变成 401，Go 返回「200 + 1006」的场景会变成 401 ——
 *       <b>HTTP 状态码就错了</b>，而 code 1006 与 401 在客户端是完全不同的处理路径。</li>
 * </ul>
 * 身份解析的落点见 {@code LikeApiSupport#userId(HttpServletRequest)}。
 *
 * <h3>⚠️ 为什么 claim 存<b>原始对象</b>而不是 long</h3>
 * <p>Go 侧 {@code userId} 是 {@code json.Number}（{@code type Number string}），
 * 业务层做的是<b>类型断言</b>：不是 {@code json.Number} 就直接落 {@code !ok} 分支。
 * 若这里提前转成 {@code long}，{@code {"userId":"abc"}} 会在鉴权层就抛异常变成 401，
 * 而 Go 是走到业务层才判 1006。存原始对象是为了让「非法 claim」这条判定留在业务层。
 */
@Component
public class LikeJwtAuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LikeJwtAuthInterceptor.class);

    /**
     * 与 Go {@code handler.Authorize} 里 {@code ctx.Value("userId")} 的 key 同名。
     * 存的是 claim 的<b>原始对象</b>（不是 long），原因见类注释。
     */
    public static final String CLAIM_ATTRIBUTE = "userId";

    private final SecretKey key;

    public LikeJwtAuthInterceptor(LikeApiProperties properties) {
        this.key = Keys.hmacShaKeyFor(
                JwtUtil.padToHmacKeySize(properties.getUserAuth().getAccessSecret()));
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String authHeader = request.getHeader("Authorization");
        // ── Go 的「路由匹配先于鉴权」：未注册路径**不经 auth** ──
        // 证据：rest/engine.go:104-118 的 bindRoute 只把 chain（native + auth + 用户中间件）
        //       挂到 router.Handle(method, path, handle) 上，即**只对匹配上的路由生效**；
        //       router/patrouter.go:73,79 的 404 / 405 分支根本进不了这条链。
        //       ⇒ Go 侧 /message/v1/nope 与 /like/v1/nope 恒为 404，与有没有 token 无关。
        //
        // ⚠️ Spring 侧默认**做不到**：Boot 的静态资源处理器匹配 `/**`，
        //    于是未注册路径**也拿到了 handler**，mappedHandler != null ⇒ 拦截器照跑
        //    ⇒ 404 被鉴权遮蔽成 401（2026-10-22 真 Tomcat 实测）。
        //
        //    判据用 `handler instanceof HandlerMethod`：它精确对应 Go 那句
        //    「有没有匹配上 @RequestMapping 方法」——ResourceHttpRequestHandler 不是 HandlerMethod。
        //    ⚠️ 不要改成「看路径是否以 /v1/ 开头」之类的字符串判断：那是重新发明一遍路由表。
        //    完整证据（含真 Tomcat 探针输出）见 com.rag.common.http.GoStyleRouterErrorResponses。
        if (!(handler instanceof org.springframework.web.method.HandlerMethod)) {
            return true;
        }

        // Go: rest/token/tokenparser.go:80-84 doParseToken → request.ParseFromRequest(r,
        //     request.AuthorizationHeaderExtractor, ...)。头缺失时 extractor 返回 errNoAuthParam
        //     → ParseToken 返回 err → authhandler.go:56 unauthorized()。
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
        //     逐个塞进 ctx。like api 侧只有 4 个 logic 的 ctx.Value("userId") 读它，
        //     其余 claim 无消费者，故这里只落这一个。
        request.setAttribute(CLAIM_ATTRIBUTE, claims.get(CLAIM_ATTRIBUTE));
        return true;
    }

    /**
     * 统一构造鉴权失败。
     *
     * <p><b>body 为空</b>：Go 的 {@code unauthorized()}（{@code authhandler.go:106-122}）
     * 只调 {@code writer.WriteHeader(http.StatusUnauthorized)}，<b>不写任何字节</b>。
     * 渲染成 {@code ResponseEntity.status(401).build()}（见
     * {@code GlobalExceptionHandler#handleAuthentication}）—— 返回带 envelope 的 body
     * 会把 Go 从未输出过的字节写给客户端。
     *
     * <p>这里的 code/msg 只是异常载体上的信息（用于日志），<b>不会进入响应体</b>。
     */
    private static AuthenticationException unauthorized() {
        return new AuthenticationException(
                LikeErrorCode.ERROR_TOKEN_RUNTIME.getCode(),
                LikeErrorMessages.getMsg(LikeErrorCode.ERROR_TOKEN_RUNTIME.getCode()));
    }
}