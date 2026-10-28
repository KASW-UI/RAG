package com.rag.like.like.api.interceptor;

import com.rag.common.exception.AuthenticationException;
import com.rag.like.like.api.auth.LikeJwtAuthFilter;
import com.rag.like.like.api.auth.LikeJwtSignatureVerifier;
import com.rag.like.like.common.errmsg.LikeErrorCode;
import com.rag.like.like.common.errmsg.LikeErrorMessages;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

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
 *       {@code stripBearerPrefixFromTokenString}，三条规则见 {@code BearerTokenExtractor}）</li>
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
 * <h3>🔴 DRIFT-LIKEAPI-009②：验签跑在<b>哪一层</b></h3>
 * <p>Go 侧链序（真源）：原生中间件 → <b>Authorize(验签)</b> → <b>observability(3s 计时)</b> → handler
 * ⇒ <b>验签耗时不计入 3s 预算</b>（详见 {@link LikeJwtAuthFilter} 的类注释，含 Go 行号）。
 * <p>而 Servlet 的 {@code HandlerInterceptor} 在 {@code DispatcherServlet} <b>里面</b>，
 * 而 3s 预算在 {@code HttpMiddlewareFilter}(+7) 起算、整条下游都在预算内
 * ⇒ 若验签留在本类，就等于<b>验签耗时会吃掉 3s 预算</b> ⇒ 与 Go 不一致。
 * <p>⇒ 验签已搬到 {@link LikeJwtAuthFilter}（{@code @Order(+6)}，在预算 Filter **之外**）；
 * 本类只负责：
 * <ul>
 *   <li><b>消费</b> Filter 的验签结果（成功 ⇒ 落 claim；失败 ⇒ 抛 401）；</li>
 *   <li>Filter <b>未生效</b>时（未被组件扫描 / 未注册）<b>兜底就地验签</b>
 *       ⇒ 仍然 401（安全不倒退），但此时验签落在预算内 ⇒ 守门会红。</li>
 * </ul>
 */
@Component
public class LikeJwtAuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LikeJwtAuthInterceptor.class);

    /**
     * 与 Go {@code handler.Authorize} 里 {@code ctx.Value("userId")} 的 key 同名。
     * 存的是 claim 的<b>原始对象</b>（不是 long），原因见类注释。
     */
    public static final String CLAIM_ATTRIBUTE = "userId";

    private final LikeJwtSignatureVerifier verifier;

    public LikeJwtAuthInterceptor(LikeJwtSignatureVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
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

        // ── 路径 1（正常）：消费 Filter 的验签结果（验签在 3s 预算**之外**）──
        Object attr = request.getAttribute(LikeJwtAuthFilter.VERIFICATION_ATTRIBUTE);
        if (attr instanceof LikeJwtSignatureVerifier.VerificationResult result) {
            if (result.failed()) {
                throw unauthorized();
            }
            request.setAttribute(CLAIM_ATTRIBUTE, result.claim());
            return true;
        }

        // ── 路径 2（兜底）：Filter 未生效 ⇒ 就地验签（验签在 3s 预算**之内**）──
        // ⚠️ 这条分支**不是**正常路径：它意味着 DRIFT-LIKEAPI-009② 未闭环
        //    （验签重新落回预算内）⇒ 守门 {@code LikeApiJwtVerificationScopeTest} 会红。
        //    保留它是为了「Filter 万一没装配上也不至于变成放行所有请求」——
        //    安全不倒退，只是漂移没修好。
        LikeJwtSignatureVerifier.VerificationResult inline = verifier.verify(
                request.getHeader("Authorization"), request.getRequestURI());
        if (inline.failed()) {
            throw unauthorized();
        }
        request.setAttribute(CLAIM_ATTRIBUTE, inline.claim());
        request.setAttribute(LikeJwtAuthFilter.VERIFICATION_SOURCE_ATTRIBUTE,
                LikeJwtAuthFilter.SOURCE_INLINE);
        log.warn("like JWT verification fell back to the interceptor (filter result absent)");
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
