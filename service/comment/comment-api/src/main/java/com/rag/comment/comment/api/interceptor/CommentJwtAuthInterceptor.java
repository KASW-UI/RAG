package com.rag.comment.comment.api.interceptor;

import com.rag.comment.comment.api.auth.CommentJwtSignatureVerifier;
import com.rag.comment.comment.api.auth.CommentJwtVerificationResult;
import com.rag.comment.comment.api.auth.GoStyleJwtAuthFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * comment-api 的 JWT 鉴权拦截器 —— Go 端 {@code rest.WithJwt(secret)} 的<b>响应面</b>。
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
 *       {@code stripBearerPrefixFromTokenString}，规则见 {@code BearerTokenExtractor}）</li>
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
 *
 * <h3>🔴 验签与响应被拆成两段（DRIFT-USERAPI-009②）</h3>
 * <p>Go 侧 3s 预算<b>不覆盖</b>验签（计时器在 {@code Authorize} 之后起算），
 * 而 RAG 侧验签原本内联在本类里（{@code HandlerInterceptor} ⇒ DispatcherServlet <b>内</b>）
 * ⇒ 验签<b>被计入</b> 3s 预算 ⇒ 与 Go 相反。
 * <p>⇒ 修法（与 users/admin/favorite 域同款配方）：<b>验签</b>搬进
 * {@code GoStyleJwtAuthFilter}（{@code @Order(+6)} 的 Servlet Filter ⇒ 在 3s 预算的
 * {@code +7} <b>之外</b>），本类只<b>消费</b>它的结果、失败<b>原样重抛</b>。
 * <p>⇒ 401 的<b>空 body 形状逐字不变</b>（异常对象由 Filter 产出 ⇒
 * {@code @ExceptionHandler} 仍然管得到）；「413 压过 401」也不变
 * （本 Filter <b>绝不写响应</b> ⇒ 401 仍发生在所有 Filter 之后）。
 */
@Component
public class CommentJwtAuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(CommentJwtAuthInterceptor.class);

    /**
     * 与 Go {@code handler.Authorize} 里 {@code ctx.Value("userId")} 的 key 同名。
     * 存的是 claim 的<b>原始对象</b>（不是 long）—— 取值判定属于业务层，见类注释。
     */
    public static final String CLAIM_ATTRIBUTE = "userId";

    private final CommentJwtSignatureVerifier verifier;

    public CommentJwtAuthInterceptor(CommentJwtSignatureVerifier verifier) {
        this.verifier = verifier;
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
        //
        // ⚠️ 本条判据**只管「要不要拦」**；「要不要验签」是另一条判据
        //    （{@code CommentJwtAuthPaths#isProtected}，由 Filter 使用）⇒ 两者刻意不同源。
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        String uri = request.getRequestURI();

        // ================= 阶段 1：JWT 验签 =================
        // 结果由 GoStyleJwtAuthFilter 在 3s 预算**之外**产出（本类只消费，不自己验签）。
        CommentJwtVerificationResult result = (CommentJwtVerificationResult) request.getAttribute(
                GoStyleJwtAuthFilter.VERIFICATION_ATTRIBUTE);
        if (result == null) {
            // 🔴 兜底：Filter 未命中（路径判据不一致 / Filter 未被注册）⇒ 就地验签。
            //   行为与 DRIFT-USERAPI-009② 修复前完全一致（401 照常）⇒ **不会造成鉴权绕过**；
            //   但验签落回 3s 预算**之内** ⇒ 该路径上 ② 未闭环 ⇒ 必须大声记日志。
            log.warn("comment JWT 验签未命中 filter 缓存 ⇒ 就地验签（DRIFT-USERAPI-009② 在此路径未闭环）: {}", uri);
            request.setAttribute(GoStyleJwtAuthFilter.VERIFICATION_SOURCE_ATTRIBUTE,
                    GoStyleJwtAuthFilter.SOURCE_INLINE);
            result = verifier.verify(request.getHeader("Authorization"), uri);
        } else {
            request.setAttribute(GoStyleJwtAuthFilter.VERIFICATION_SOURCE_ATTRIBUTE,
                    GoStyleJwtAuthFilter.SOURCE_FILTER);
        }

        // 验签失败 ⇒ **原样重抛**（异常对象由 Filter 产出 ⇒ @ExceptionHandler 仍管得到
        // ⇒ 401 + **空 body** 的形状逐字不变）。
        if (result.failed()) {
            throw result.failure();
        }

        // Go: authhandler.go:63-72 遍历 claims，把非标准 claim（aud/exp/jti/iat/iss/nbf/sub 除外）
        //     逐个塞进 ctx。comment 域只有 helper.go 的 extractUserID 读 "userId"，
        //     其余 claim 无消费者，故这里只落这一个。
        // ⚠️ 落的是 claim 的**原始对象**（不是 long）⇒ 解析成 int64 是业务层的事
        //    （CommentApiSupport#userId，失败落 HTTP 200 + {code:1006}，**不是 401**）。
        request.setAttribute(CLAIM_ATTRIBUTE, result.claims().get(CLAIM_ATTRIBUTE));
        return true;
    }
}
