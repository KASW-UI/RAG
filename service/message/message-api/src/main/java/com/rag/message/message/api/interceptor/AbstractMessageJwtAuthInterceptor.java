package com.rag.message.message.api.interceptor;

import com.rag.common.exception.AuthenticationException;
import com.rag.message.message.common.errmsg.MessageErrorCode;
import com.rag.message.message.common.errmsg.MessageErrorMessages;
import com.rag.users.common.jwt.BearerTokenExtractor;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.crypto.SecretKey;

/**
 * message-api 的 JWT 鉴权逻辑基类 —— Go 端 {@code rest.WithJwt(secret)} 的等价物。
 *
 * <p><b>Go 出处</b>：{@code service/message/api/internal/handler/routes.go} 的两段
 * {@code server.AddRoutes(routes, rest.WithJwt(secret), rest.WithPrefix(...))}，
 * 其中 {@code rest.WithJwt} 定义在 {@code rest/server.go:185} → {@code handler.Authorize(secret)}
 * （{@code rest/handler/authhandler.go}）。
 *
 * <p><b>为什么抽成基类而不是两个各自实现的拦截器</b>：Go 侧两段路由表的<b>唯一差别</b>是
 * secret 与前缀，鉴权逻辑本身是同一份 {@code handler.Authorize}。写成两个独立类等于
 * 复制一份安全边界 —— AGENTS.md 记录的 DRIFT-010（admin 侧一份、user 侧另一份，
 * 各自演化出注销绕过）就是这个形态。本类持有全部逻辑，两个子类只提供 key。
 *
 * <p><b>L1 行为冻结（逐条对照 Go 源码，非推断）</b>：
 * <ol>
 *   <li>Authorization 头缺失 → {@link AuthenticationException} → <b>HTTP 401</b></li>
 *   <li>Bearer 前缀剥离失败 / token 为空 → 401
 *       （Go: {@code golang-jwt/jwt/v4/request/oauth2.go} 的
 *       {@code stripBearerPrefixFromTokenString}，规则见 {@link BearerTokenExtractor}）</li>
 *   <li>签名错 / 过期 / 格式错 → 401</li>
 *   <li>验签成功后：把 claim {@code userId} 的<b>原始对象</b>写进 request attribute</li>
 * </ol>
 *
 * <p><b>⚠️ 本层刻意<b>不</b>校验 claim 的取值</b>，这是与 user-api 最大的实现差异：
 * <ul>
 *   <li>Go 侧 {@code handler.Authorize} 只验签名与 exp，随后把所有非标准 claim 原样塞进 ctx；</li>
 *   <li>claim 是不是数字、是不是正数，由 logic 层
 *       {@code api/internal/logic/shared/helper.go} 的 {@code UserIDFromContext} 判定，
 *       失败返回 {@code ErrorUnauthorized}（<b>5004</b>），
 *       最终输出是 <b>HTTP 200 + {code:5004,msg:"unauthorized"}</b>。</li>
 *   <li>若在本层把它变成 401，Go 返回 200 + 5004 的场景在 RAG 上会变成 401 —— 状态码就错了。</li>
 * </ul>
 * 身份解析的落点见 {@code MessageApiSupport#userId(HttpServletRequest)}。
 */
public abstract class AbstractMessageJwtAuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AbstractMessageJwtAuthInterceptor.class);

    /**
     * 与 Go {@code handler.Authorize} 里 {@code ctx.Value("userId")} 的 key 同名。
     * 存的是 claim 的<b>原始对象</b>（不是 long），原因见类注释 —— 取值判定属于业务层。
     */
    public static final String CLAIM_ATTRIBUTE = "userId";

    private final String routeGroup;
    private final SecretKey key;

    protected AbstractMessageJwtAuthInterceptor(String routeGroup, SecretKey key) {
        this.routeGroup = routeGroup;
        this.key = key;
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

        // Go: rest/token/tokenparser.go doParseToken → request.ParseFromRequest(r,
        //     request.AuthorizationHeaderExtractor, ...)。头缺失时 extractor 返回 errNoAuthParam
        //     → parser.ParseToken 返回 err → authhandler.go:100 unauthorized()。
        if (authHeader == null || authHeader.isEmpty()) {
            log.warn("{} path {} with missing Authorization header", routeGroup, request.getRequestURI());
            throw unauthorized();
        }

        // Go: golang-jwt/v4 request/oauth2.go stripBearerPrefixFromTokenString
        //     —— 大小写不敏感、不匹配时原样返回整串。三条规则都在 BearerTokenExtractor 里。
        String token = BearerTokenExtractor.tokenForSignatureVerification(authHeader);
        if (token.isEmpty()) {
            log.warn("{} path {} with empty Bearer token", routeGroup, request.getRequestURI());
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
            log.warn("{} JWT verify failed on {} : {}", routeGroup, request.getRequestURI(), t.getMessage());
            throw unauthorized();
        }

        // Go: authhandler.go:63-72 遍历 claims，把非标准 claim（aud/exp/jti/iat/iss/nbf/sub 除外）
        //     逐个塞进 ctx。message api 侧只有 logic/shared/helper.go 的 UserIDFromContext 读
        //     "userId"，其余 claim 无消费者，故这里只落这一个。
        request.setAttribute(CLAIM_ATTRIBUTE, claims.get(CLAIM_ATTRIBUTE));
        return true;
    }

    /**
     * 统一构造鉴权失败。
     *
     * <p>业务码用 {@code ERROR_UNAUTHORIZED}（5004）而不是 user-api 的 1006：
     * message 域的错误码表（{@code service/message/common/errmsg.go}）里没有 1006，
     * 鉴权码就是 5004。<b>body 不会被写出去</b> —— 见
     * {@code GlobalExceptionHandler#handleAuthentication} 对 Go
     * {@code authhandler.go:106 unauthorized()} 的逐字还原。
     */
    private static AuthenticationException unauthorized() {
        return new AuthenticationException(
                MessageErrorCode.ERROR_UNAUTHORIZED.getCode(),
                MessageErrorMessages.getMsg(MessageErrorCode.ERROR_UNAUTHORIZED.getCode()));
    }
}