package com.rag.favorite.favorite.api.auth;

/**
 * favorite-api 的 JWT <b>路径判据</b> —— Filter 与守门测试共用同一份。
 *
 * <h2>Go 侧依据（真源，逐行）</h2>
 * <pre>
 *   service/favorite/api/internal/handler/routes.go:15-60
 *     server.AddRoutes(
 *         rest.WithMiddlewares([]rest.Middleware{serverCtx.CheckBlacklistMiddleware},
 *                             []rest.Route{ 7 个路由 }),
 *         rest.WithJwt(serverCtx.Config.UserAuth.AccessSecret),
 *         rest.WithPrefix("/favorite/v1"))
 * </pre>
 * ⇒ <b>整个 favorite-api 只有这一段 {@code AddRoutes}</b>，且<b>带 {@code rest.WithJwt}</b>
 * ⇒ ⇒ <b>本域没有 public 路径</b>（7/7 全量受保护）。
 * <p>⇒ 与 users / admin 域不同：那边有 login / create 两段 public 段（本类没有这个概念）。
 *
 * <h2>为什么判据必须只有一份</h2>
 * <p>DRIFT-USERAPI-009② 的修法把 JWT <b>验签</b>拆成两段：验签在
 * {@link GoStyleJwtAuthFilter}（Servlet Filter，3s 预算之外），响应在
 * {@code FavoriteJwtAuthInterceptor}（HandlerInterceptor，3s 预算之内）。
 * <p>两段若各写一份路径判据，就会出现「Filter 判为受保护、Interceptor 判为放行」的缝隙：
 * <ul>
 *   <li>后者 ⇒ <b>鉴权被绕过</b> ⇒ 安全漏洞；</li>
 *   <li>前者 ⇒ interceptor 拿不到验签结果 ⇒ 落进兜底分支
 *       ⇒ 验签退回 3s 预算之内 ⇒ <b>DRIFT-USERAPI-009② 静默不闭环</b>。</li>
 * </ul>
 * ⇒ 判据收敛到这一个类，两个缝隙同时消失。
 *
 * <h2>⚠️ 本类的判据只管「要不要验签」，不管「要不要拦」</h2>
 * <p><b>「拦截」判据在 interceptor 里，是另一条</b>：{@code handler instanceof HandlerMethod}
 * （Go：{@code rest/engine.go:104-118 bindRoute} 只把 chain 挂到<b>匹配上</b>的路由上
 * ⇒ 未注册路径 404，<b>与有没有 token 无关</b>）。
 * <p>⇒ 所以「未注册子路径」在两侧都是 <b>404</b>（不是 401）：
 * <ul>
 *   <li>Filter 会对 {@code /favorite/v1/**} 下的一切（含未注册子路径）验签
 *       ⇒ 但它<b>只记录、不写响应</b> ⇒ 对外不可见；</li>
 *   <li>interceptor 见到不是 {@code HandlerMethod} ⇒ 直接放行 ⇒ 由 DispatcherServlet 回 404。</li>
 * </ul>
 * ⇒ 本类的判据<b>少覆盖</b>只会让该路径落进 interceptor 的兜底分支（就地验签 ⇒ ②不闭环，
 * 但<b>不会造成鉴权绕过</b>）；<b>多覆盖</b>只是多做一次无用验签。
 */
public final class FavoriteJwtAuthPaths {

    /**
     * 受保护路径前缀 —— 与 {@code WebMvcConfig} 的 {@code /favorite/v1/**} 对齐。
     *
     * <p>⚠️ Go 侧前缀是 {@code /favorite/v1}（{@code rest.WithPrefix("/favorite/v1")}），
     * RAG 侧保持同名 ⇒ <b>无路径重命名差异</b>（与 users/admin 域不同，那两边把
     * {@code /usercenter/v1} 改成了 {@code /user/v1}）。
     */
    public static final String PROTECTED_PREFIX = "/favorite/v1";

    private FavoriteJwtAuthPaths() {
    }

    /**
     * 是否受保护（要验签）。
     *
     * <p>⚠️ 必须与 {@code WebMvcConfig} 注册的 {@code /favorite/v1/**} 覆盖同一集合：
     * 少覆盖 ⇒ interceptor 拿不到 Filter 的验签结果（落兜底 ⇒ ② 不闭环）；
     * 多覆盖 ⇒ 只会多做一次无用验签，<b>不构成鉴权绕过</b>。
     */
    public static boolean isProtected(String uri) {
        if (uri == null) {
            return false;
        }
        return uri.equals(PROTECTED_PREFIX) || uri.startsWith(PROTECTED_PREFIX + "/");
    }
}
