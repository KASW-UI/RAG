package com.rag.users.admin.api.auth;

import java.util.Set;

/**
 * admin-api 的 JWT 路径判据 —— <b>Filter 与 Interceptor 共用同一份</b>。
 *
 * <h2>Go 侧依据（真源，逐行）</h2>
 * <pre>
 *   service/user/admin/api/internal/handler/routes.go
 *     server.AddRoutes(
 *         rest.WithMiddlewares([]rest.Middleware{serverCtx.CheckBlacklistMiddleware}, ...),
 *         rest.WithJwt(serverCtx.Config.AdminAuth.AccessSecret),
 *         rest.WithPrefix("/admincenter/v1"))
 *     server.AddRoutes(
 *         []rest.Route{{Method: http.MethodPost, Path: "/login"}},
 *         rest.WithPrefix("/admincenter/v1"))
 *     server.AddRoutes(
 *         []rest.Route{{Method: http.MethodPost, Path: "/create"}},
 *         rest.WithPrefix("/admincenter/v1"))
 * </pre>
 * ⇒ <b>public = login / create</b>（这两段<b>不</b>带 {@code WithJwt}），其余 13 个端点带 JWT
 * ⇒ 即本类的 {@link #isProtected}。
 *
 * <p>⚠️ Go 的 prefix 是 {@code /admincenter/v1}，RAG 侧是 {@code /admin/v1}
 * ⇒ 属**已登记的表达层差异**（路径重命名），本类按 **RAG 侧实际路径**判定
 * ⇒ 与 {@code AdminController} 的 {@code @RequestMapping("/admin/v1")} 一致。
 *
 * <h2>为什么必须只有一份</h2>
 * <p>DRIFT-USERAPI-009② 的修法把 JWT <b>验签</b>拆成两段：验签在
 * {@link GoStyleJwtAuthFilter}（Servlet Filter，3s 预算之外），响应在
 * {@code AdminJwtAuthInterceptor}（HandlerInterceptor，3s 预算之内）。
 * <p>两段若各写一份路径判据，就会出现「Filter 判为受保护、Interceptor 判为放行」的缝隙：
 * <ul>
 *   <li>后者 ⇒ <b>鉴权被绕过</b> ⇒ 安全漏洞；</li>
 *   <li>前者 ⇒ interceptor 拿不到验签结果 ⇒ 落进兜底分支
 *       ⇒ 验签退回 3s 预算之内 ⇒ <b>DRIFT-USERAPI-009② 静默不闭环</b>。</li>
 * </ul>
 * ⇒ 判据收敛到这一个类，两个缝隙同时消失。
 */
public final class AdminJwtAuthPaths {

    /** public 路径（HUMAN_ONLY.MD 决策：login / create 不挂 JWT）。 */
    public static final Set<String> PUBLIC_PATHS = Set.of(
            "/admin/v1/login", "/admin/v1/create"
    );

    /** 受保护路径的共同前缀 —— 与 {@code WebMvcConfig} 的 {@code /admin/v1/**} 对齐。 */
    private static final String PROTECTED_PREFIX = "/admin/v1";

    private AdminJwtAuthPaths() {
    }

    /** 是否 public（不挂 JWT，直接放行）。 */
    public static boolean isPublic(String uri) {
        return uri != null && PUBLIC_PATHS.contains(uri);
    }

    /**
     * 是否受保护（要验签）。
     *
     * <p>⚠️ 必须与 {@code WebMvcConfig} 注册的 {@code /admin/v1/**} 覆盖同一集合：
     * 少覆盖 ⇒ interceptor 拿不到 Filter 的验签结果（落兜底 ⇒ ② 不闭环）；
     * 多覆盖 ⇒ 只会多做一次无用验签，<b>不构成鉴权绕过</b>。
     */
    public static boolean isProtected(String uri) {
        if (uri == null) {
            return false;
        }
        if (isPublic(uri)) {
            return false;
        }
        return uri.equals(PROTECTED_PREFIX) || uri.startsWith(PROTECTED_PREFIX + "/");
    }
}
