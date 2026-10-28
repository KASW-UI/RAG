package com.rag.follow.follow.api.interceptor;

import com.rag.follow.follow.api.auth.FollowJwtAuthPaths;
import com.rag.follow.follow.api.auth.FollowJwtSignatureVerifier;
import com.rag.follow.follow.api.auth.FollowJwtVerificationResult;
import com.rag.follow.follow.api.auth.GoStyleJwtAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * follow-api 的 JWT 验签拦截器 —— Go 端 {@code rest.WithJwt(secret)} 的等价物
 * （<b>验签结果消费</b>；验签本体已搬进 {@link GoStyleJwtAuthFilter}）。
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
 *
 * <h2>🔴 DRIFT-USERAPI-009②：验签已搬进 {@link GoStyleJwtAuthFilter}</h2>
 *
 * <p>Go 侧链序：<b>原生中间件 → JWT 验签 → observability(3s 计时) → 黑名单 → handler</b>
 * （{@code rest/engine.go:101-113} + {@code rest/chain/chain.go:38-43} 的 Append 语义
 * + {@code followcenter.go:42} 只有一个 {@code server.Use}）
 * ⇒ <b>3s 预算不覆盖 JWT 验签</b>。
 * <p>Servlet 侧所有 {@code Filter} 都在 {@code DispatcherServlet} 外面、
 * {@code HandlerInterceptor} 在里面 ⇒ 验签要出预算，必须搬进 Filter
 * ⇒ 验签搬到 {@link GoStyleJwtAuthFilter}（{@code @Order(+6)}，在 3s 预算的 {@code +7} 之外）。
 * <p>⇒ 本类现在只做两件事：<b>消费</b>验签结果 + 把失败<b>原样重抛</b>
 * （异常对象由 Filter 产出 ⇒ {@code GlobalExceptionHandler} 的 {@code @ExceptionHandler} 仍管得到
 * ⇒ <b>401 + 空 body</b>的形状逐字不变）。
 * <p>⇒ 黑名单<b>留在 {@link FollowBlacklistInterceptor}</b>（不动）——
 * Go 的黑名单是 route 级中间件（{@code rest.WithMiddlewares} ⇒ {@code rest/server.go:222-233}
 * 把 {@code middleware(route.Handler)} 包在 handler 外 ⇒ 比 chain 任何元素都内层）
 * ⇒ 在 observability(3s) <b>之内</b> ⇒ RAG 现状已一致。
 */
@Component
public class FollowJwtAuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(FollowJwtAuthInterceptor.class);

    /**
     * 与 Go {@code authhandler.go:71-79} 里 {@code ctx.Value("userId")} 的 key 同名。
     * 存的是 claim 的<b>原始对象</b>（不是 long），原因见类注释 —— 取值判定属于业务层。
     */
    public static final String CLAIM_ATTRIBUTE = "userId";

    private final FollowJwtSignatureVerifier verifier;

    public FollowJwtAuthInterceptor(FollowJwtSignatureVerifier verifier) {
        // ⚠️ 密钥构造已移入 {@link FollowJwtSignatureVerifier}（验签搬进 Filter 后的归属）；
        //   L4 idiomatization 的 Go ↔ RAG 等价性论证见该类的构造器注释（内容未变）。
        this.verifier = verifier;
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
        //
        // ⚠️ **验签搬到 Filter 后，本判据只能留在这里**（Filter 拿不到 handler）
        // ⇒ Filter 会对未匹配路径也验签一次，但**不写响应** ⇒ 最终仍是 404 ⇒ 响应与 Go 一致。
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        // ---------- 阶段 1：JWT 验签（结果由 GoStyleJwtAuthFilter 在 3s 预算之外产出）----------
        FollowJwtVerificationResult result = (FollowJwtVerificationResult) request.getAttribute(
                GoStyleJwtAuthFilter.VERIFICATION_ATTRIBUTE);
        if (result == null) {
            // 🔴 兜底：Filter 未命中（路径判据不一致 / Filter 未被注册）⇒ 就地验签。
            //   行为与 DRIFT-USERAPI-009② 修复前完全一致（401 照常）⇒ **不会造成鉴权绕过**；
            //   但验签落回 3s 预算**之内** ⇒ 该路径上 ② 未闭环 ⇒ 必须大声记日志。
            log.warn("follow JWT 验签未命中 filter 缓存 ⇒ 就地验签（DRIFT-USERAPI-009② 在此路径未闭环）: {}",
                    request.getRequestURI());
            request.setAttribute(GoStyleJwtAuthFilter.VERIFICATION_SOURCE_ATTRIBUTE,
                    GoStyleJwtAuthFilter.SOURCE_INLINE);
            result = verifier.verify(request.getHeader("Authorization"), request.getRequestURI());
        } else {
            request.setAttribute(GoStyleJwtAuthFilter.VERIFICATION_SOURCE_ATTRIBUTE,
                    GoStyleJwtAuthFilter.SOURCE_FILTER);
        }

        // 验签失败 ⇒ 原样重抛（异常对象由 Filter 产出 ⇒ @ExceptionHandler 仍管得到 ⇒ 401 空 body 不变）
        if (result.failed()) {
            throw result.failure();
        }

        // Go: authhandler.go:71-79 遍历 claims，把非标准 claim（aud/exp/jti/iat/iss/nbf/sub 除外）
        //     逐个塞进 ctx。follow api 侧只有 logic/follow/helper.go 的 extractUserID 读
        //     "userId"，其余 claim 无消费者，故这里只落这一个。
        // ⚠️ 存的是**原始对象**（不是 long）：取值判定属于业务层（helper.go:13-33），
        //   失败返回 5004 ⇒ HTTP 200 + {code:5004} ⇒ 鉴权层**不能**把它变成 401。
        request.setAttribute(CLAIM_ATTRIBUTE, result.claim());
        return true;
    }
}
