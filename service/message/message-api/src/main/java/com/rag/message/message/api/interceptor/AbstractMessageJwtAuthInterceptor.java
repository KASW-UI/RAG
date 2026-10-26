package com.rag.message.message.api.interceptor;

import com.rag.common.exception.AuthenticationException;
import com.rag.message.message.api.auth.MessageJwtAuthFilter;
import com.rag.message.message.api.auth.MessageJwtSignatureVerifier;
import com.rag.message.message.common.errmsg.MessageErrorCode;
import com.rag.message.message.common.errmsg.MessageErrorMessages;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

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
 *       （Go: {@code golang-jwt/jwt/v4@v4.5.2/request/oauth2.go} 的
 *       {@code stripBearerPrefixFromTokenString}，规则见 {@code BearerTokenExtractor}）</li>
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
 *
 * <h3>🔴 DRIFT-MESSAGEAPI-009②：验签跑在<b>哪一层</b></h3>
 * <p>Go 侧链序（真源）：原生中间件 → <b>Authorize(验签)</b> → <b>observability(3s 计时)</b> → handler
 * ⇒ <b>验签耗时不计入 3s 预算</b>（详见 {@link MessageJwtAuthFilter} 的类注释，含 Go 行号）。
 * <p>而 Servlet 的 {@code HandlerInterceptor} 在 {@code DispatcherServlet} <b>里面</b>，
 * 而 3s 预算在 {@code HttpMiddlewareFilter}(+7) 起算、整条下游都在预算内
 * ⇒ 若验签留在本类，就等于<b>验签耗时会吃掉 3s 预算</b> ⇒ 与 Go 不一致。
 * <p>⇒ 验签已搬到 {@link MessageJwtAuthFilter}（{@code @Order(+6)}，在预算 Filter **之外**）；
 * 本类只负责：
 * <ul>
 *   <li><b>消费</b> Filter 的验签结果（成功 ⇒ 落 claim；失败 ⇒ 抛 401）；</li>
 *   <li>Filter <b>未生效</b>时（未被组件扫描 / 未注册）<b>兜底就地验签</b>
 *       ⇒ 仍然 401（安全不倒退），但此时验签落在预算内 ⇒ 守门会红。</li>
 * </ul>
 */
public abstract class AbstractMessageJwtAuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AbstractMessageJwtAuthInterceptor.class);

    /**
     * 与 Go {@code handler.Authorize} 里 {@code ctx.Value("userId")} 的 key 同名。
     * 存的是 claim 的<b>原始对象</b>（不是 long），原因见类注释 —— 取值判定属于业务层。
     */
    public static final String CLAIM_ATTRIBUTE = "userId";

    /**
     * 本拦截器守的路由组 —— 决定用<b>哪把 key</b>验签。
     *
     * <p>⚠️ 用<b>显式</b>组别而不是「按路径推断」：拦截器的组别由 Spring 的注册路径决定
     * （{@code WebMvcConfig}），比字符串前缀推断更可靠；而 Filter 那一侧才需要按路径分派。
     */
    private final MessageJwtSignatureVerifier.RouteGroup routeGroup;

    private final MessageJwtSignatureVerifier verifier;

    protected AbstractMessageJwtAuthInterceptor(MessageJwtSignatureVerifier.RouteGroup routeGroup,
                                                MessageJwtSignatureVerifier verifier) {
        this.routeGroup = routeGroup;
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
        Object attr = request.getAttribute(MessageJwtAuthFilter.VERIFICATION_ATTRIBUTE);
        if (attr instanceof MessageJwtSignatureVerifier.VerificationResult result) {
            if (result.failed()) {
                throw unauthorized();
            }
            request.setAttribute(CLAIM_ATTRIBUTE, result.claim());
            return true;
        }

        // ── 路径 2（兜底）：Filter 未生效 ⇒ 就地验签（验签在 3s 预算**之内**）──
        // ⚠️ 这条分支**不是**正常路径：它意味着 DRIFT-MESSAGEAPI-009② 未闭环
        //    （验签重新落回预算内）⇒ 守门 {@code MessageApiJwtVerificationScopeTest} 会红。
        //    保留它是为了「Filter 万一没装配上也不至于变成放行所有请求」——
        //    安全不倒退，只是漂移没修好。
        MessageJwtSignatureVerifier.VerificationResult inline = verifier.verify(
                routeGroup, request.getHeader("Authorization"), request.getRequestURI());
        if (inline.failed()) {
            throw unauthorized();
        }
        request.setAttribute(CLAIM_ATTRIBUTE, inline.claim());
        request.setAttribute(MessageJwtAuthFilter.VERIFICATION_SOURCE_ATTRIBUTE,
                MessageJwtAuthFilter.SOURCE_INLINE);
        log.warn("{} JWT verification fell back to the interceptor (filter result absent)", routeGroup);
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
