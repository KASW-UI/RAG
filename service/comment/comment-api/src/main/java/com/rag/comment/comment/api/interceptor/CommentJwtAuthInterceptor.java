package com.rag.comment.comment.api.interceptor;

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
import java.time.Instant;
import java.util.Date;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.crypto.SecretKey;

/**
 * comment-api 的 JWT 鉴权拦截器 —— Go 端 {@code rest.WithJwt(secret)} 的等价物。
 *
 * <p><b>Go 出处</b>：{@code service/comment/api/internal/handler/routes.go:54} 的
 * {@code rest.WithJwt(serverCtx.Config.UserAuth.AccessSecret)}
 * → {@code rest/server.go:185} → {@code handler.Authorize(secret)}
 * （{@code rest/handler/authhandler.go}）。
 *
 * <p><b>⚠️ 覆盖范围 = 全部 7 个端点，无例外、无 public 路径</b>。
 * Go 只有一段 {@code AddRoutes}，7 条路由共用同一把 key，
 * 因此这里是一个具体拦截器即可（message-api 需要抽象基类是因为它有两把 key）。
 *
 * <p><b>L1 行为冻结（逐条对照 Go 源码，非推断）</b>：
 * <ol>
 *   <li>Authorization 头缺失 → 401（{@code authhandler.go:55} → {@code unauthorized()}）</li>
 *   <li>Bearer 前缀剥离后为空 → 401
 *       （Go: {@code golang-jwt/jwt/v4/request/oauth2.go} 的
 *       {@code stripBearerPrefixFromTokenString}，规则见 {@link BearerTokenExtractor}）</li>
 *   <li>签名错 / 过期 / 格式错 → 401（{@code authhandler.go:60-68}）</li>
 *   <li>验签成功后：把 claim {@code userId} 的<b>原始对象</b>写进 request attribute
 *       （Go {@code authhandler.go:71-79} 把所有非标准 claim 塞进 ctx）</li>
 * </ol>
 *
 * <p><b>⚠️ 本层刻意<b>不</b>校验 claim 取值</b>：Go 的 {@code handler.Authorize} 只验签名与 exp，
 * 之后原样透传。claim 是不是数字、能不能转成 int64，由 logic 层的
 * {@code api/internal/logic/comment/helper.go:10-29 extractUserID} 判定，
 * 失败返回 {@code ErrorTokenRuntime}（<b>1006</b>）⇒ <b>HTTP 200 + {code:1006}</b>。
 * 若在本层把它变成 401，Go 返回 200 + 1006 的场景在 RAG 上就会变成 401 —— 状态码就错了。
 * 身份解析落点见 {@code CommentApiSupport#userId(HttpServletRequest)}。
 *
 * <p><b>⚠️ 与 users 域「401 + 完整 envelope」不同</b>：Go 的 {@code unauthorized()}
 * 只 {@code WriteHeader(401)}，<b>不写任何 body 字节</b>，
 * 所以本模块的 401 是<b>空 body</b>。写成 envelope 会给客户端多出 Go 从未输出过的字节。
 */
@Component
public class CommentJwtAuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(CommentJwtAuthInterceptor.class);

    /**
     * 与 Go {@code handler.Authorize} 里 {@code ctx.Value("userId")} 的 key 同名。
     * 存的是 claim 的<b>原始对象</b>（不是 long）—— 取值判定属于业务层，见类注释。
     */
    public static final String CLAIM_ATTRIBUTE = "userId";

    private final SecretKey key;

    public CommentJwtAuthInterceptor(CommentApiProperties properties) {
        this.key = Keys.hmacShaKeyFor(
                JwtUtil.padToHmacKeySize(properties.getUserAuth().getAccessSecret()));
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // ── Go 的「路由匹配先于鉴权」：未注册路径**不经 auth** ──
        // 证据：rest/engine.go 的 bindRoute 只把 chain 挂到 router.Handle(method, path, handle)
        //       上，即**只对匹配上的路由生效**；router/patrouter.go 的 404 / 405 分支进不了这条链。
        //       ⇒ Go 侧 /comment/v1/nope 恒为 404，与有没有 token 无关。
        //
        // ⚠️ Spring 侧默认**做不到**：静态资源处理器匹配 /**，未注册路径也拿到了 handler，
        //    mappedHandler != null ⇒ 拦截器照跑 ⇒ 404 被鉴权遮蔽成 401。
        //    判据用 `handler instanceof HandlerMethod`：精确对应 Go 那句「有没有匹配上处理方法」。
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

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
            log.warn("JWT issued-at in the future on {}", request.getRequestURI());
            throw unauthorized();
        }

        // Go: authhandler.go:63-72 遍历 claims，把非标准 claim（aud/exp/jti/iat/iss/nbf/sub 除外）
        //     逐个塞进 ctx。comment 域只有 helper.go 的 extractUserID 读 "userId"，
        //     其余 claim 无消费者，故这里只落这一个。
        request.setAttribute(CLAIM_ATTRIBUTE, claims.get(CLAIM_ATTRIBUTE));
        return true;
    }

    /**
     * 统一构造鉴权失败。
     *
     * <p>业务码用 {@code ERROR_TOKEN_RUNTIME}（<b>1006</b>）——
     * comment 域与 message 域在这里<b>不一样</b>：message 域用 {@code ERROR_UNAUTHORIZED}（5004）。
     * 1006 对应 Go {@code errmsg.go:56 ErrorTokenRuntime}，是 comment-api 6 个 logic
     * 的 {@code extractUserID} 失败分支所引用的码。
     *
     * <p><b>body 不会被写出去</b> —— 见 {@code CommentGlobalExceptionHandler#handleAuthentication}
     * 对 Go {@code authhandler.go:106 unauthorized()} 的逐字还原。
     */
    private static AuthenticationException unauthorized() {
        return new AuthenticationException(
                CommentErrorCode.ERROR_TOKEN_RUNTIME.getCode(),
                CommentErrorMessages.getMsg(CommentErrorCode.ERROR_TOKEN_RUNTIME.getCode()));
    }
}