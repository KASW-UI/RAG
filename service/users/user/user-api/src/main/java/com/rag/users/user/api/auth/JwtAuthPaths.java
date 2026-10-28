package com.rag.users.user.api.auth;

import java.util.Set;

/**
 * user-api 的 JWT 路径判据 —— <b>Filter 与 Interceptor 共用同一份</b>。
 *
 * <h2>为什么必须只有一份</h2>
 *
 * <p>DRIFT-USERAPI-009② 的修法把 JWT <b>验签</b>拆成两段：
 * 验签在 {@link GoStyleJwtAuthFilter}（Servlet Filter，3s 预算之外），
 * 响应与黑名单在 {@code JwtAuthInterceptor}（HandlerInterceptor，3s 预算之内）。
 * <p>两段若各写一份路径判据，就可能出现「Filter 判为受保护、Interceptor 判为放行」
 * 或反之的缝隙：
 * <ul>
 *   <li>后者（Interceptor 放行）⇒ <b>鉴权被绕过</b> ⇒ 安全漏洞；</li>
 *   <li>前者（Interceptor 没拿到 Filter 的结果）⇒ 落进 interceptor 的兜底分支
 *       ⇒ 验签退回 3s 预算之内 ⇒ <b>DRIFT-USERAPI-009② 静默不闭环</b>。</li>
 * </ul>
 * ⇒ 判据收敛到这一个类，两段都调它 ⇒ 两个缝隙同时消失。
 *
 * <h2>Go 侧依据（真源，逐行）</h2>
 * <pre>
 *   service/user/user/api/internal/handler/routes.go
 *     rest.AddRoutes([]rest.Route{{Method: http.MethodPost, Path: "/user/v1/login"}},)
 *     rest.AddRoutes([]rest.Route{{Method: http.MethodPost, Path: "/user/v1/register"}},)
 *     rest.AddRoutes([]rest.Route{ ...其余 7 个... },
 *         rest.WithJwt(c.UserAuth.AccessSecret),
 *         rest.WithMiddlewares([]rest.Middleware{security.CheckBlacklistMiddleware}, routes...))
 * </pre>
 * ⇒ <b>public = login / register</b>（这两段 <b>不</b>带 {@code WithJwt}），
 * 其余 7 个路径带 JWT ⇒ 即本类的 {@link #isProtected}。
 *
 * <p><b>L4 idiomatization</b>：Go 用「注册时挂不挂中间件」表达 public/protected，
 * Java 用「路径前缀 + 白名单」表达 ⇒ 表达层差异，语义等价（集合逐条一致）。
 */
public final class JwtAuthPaths {

    /** public 路径（HUMAN_ONLY.MD 决策：register/login 不挂 JWT）。 */
    public static final Set<String> PUBLIC_PATHS = Set.of(
            "/user/v1/register", "/user/v1/login"
    );

    /**
     * 受保护路径的共同前缀 —— 与 {@code WebMvcConfig} 的
     * {@code addPathPatterns("/user/v1/**")} 对齐。
     */
    private static final String PROTECTED_PREFIX = "/user/v1";

    private JwtAuthPaths() {
    }

    /** 是否 public（不挂 JWT，直接放行）。 */
    public static boolean isPublic(String uri) {
        return uri != null && PUBLIC_PATHS.contains(uri);
    }

    /**
     * 是否受保护（要验签）。
     *
     * <p>⚠️ <b>这里必须与 {@code WebMvcConfig} 注册的 {@code /user/v1/**} 覆盖同一集合</b>：
     * 少覆盖 ⇒ interceptor 拿不到 Filter 的验签结果（落兜底 ⇒ ② 不闭环）；
     * 多覆盖 ⇒ 只会多做一次无用验签，<b>不构成鉴权绕过</b>（拦截器仍按自己的判据放行）。
     * ⇒ 判据取 {@code /user/v1} 本身 + {@code /user/v1/} 前缀 ⇒ 是 {@code /user/v1/**} 的
     * <b>超集</b>（Ant 的 {@code /**} 可匹配零段 ⇒ {@code /user/v1} 本身也在内）⇒ 安全侧对齐。
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
