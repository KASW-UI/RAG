package com.rag.follow.follow.api.auth;

/**
 * follow-api 的 JWT 路径判据 —— <b>Filter 与 Interceptor 共用同一份</b>。
 *
 * <h2>Go 侧依据（真源，逐行）</h2>
 * <pre>
 *   service/follow/api/internal/handler/routes.go:16-64
 *     server.AddRoutes(
 *         rest.WithMiddlewares(
 *             []rest.Middleware{serverCtx.CheckBlacklistMiddleware},
 *             []rest.Route{ /block, /block/list, /follow, /follow/list,
 *                           /follower/list, /recommendations, /unblock, /unfollow }...),
 *         rest.WithJwt(serverCtx.Config.UserAuth.AccessSecret),
 *         rest.WithPrefix("/follow/v1"),
 *     )
 * </pre>
 * ⇒ <b>8 个端点全带 {@code WithJwt}</b> ⇒ <b>没有 public 路径</b>
 * ⇒ 本类的 {@link #isProtected} 对 {@code /follow/v1/**} 一律为真。
 *
 * <p>⚠️ Go 的 prefix 是 {@code /follow/v1}，RAG 侧一致（{@code FollowController} 的
 * {@code @RequestMapping("/follow/v1")}）⇒ 本类按 <b>RAG 侧实际路径</b>判定。
 *
 * <h2>为什么必须只有一份</h2>
 * <p>DRIFT-USERAPI-009② 的修法把 JWT <b>验签</b>拆成两段：验签在
 * {@link GoStyleJwtAuthFilter}（Servlet Filter，3s 预算之外），
 * 响应在 {@code FollowJwtAuthInterceptor}（HandlerInterceptor，3s 预算之内）。
 * <p>两段若各写一份路径判据，就可能出现「Filter 判为受保护、Interceptor 判为放行」的缝隙：
 * <ul>
 *   <li>后者 ⇒ <b>鉴权被绕过</b> ⇒ 安全漏洞；</li>
 *   <li>前者 ⇒ interceptor 拿不到验签结果 ⇒ 落进兜底分支
 *       ⇒ 验签退回 3s 预算之内 ⇒ <b>DRIFT-USERAPI-009② 静默不闭环</b>。</li>
 * </ul>
 * ⇒ 判据收敛到这一个类，两个缝隙同时消失。
 */
public final class FollowJwtAuthPaths {

    /** 受保护路径的共同前缀 —— 与 {@code WebMvcConfig} 的 {@code /follow/v1/**} 对齐。 */
    private static final String PROTECTED_PREFIX = "/follow/v1";

    private FollowJwtAuthPaths() {
    }

    /**
     * 是否受保护（要验签）。
     *
     * <p>⚠️ follow 域<b>没有 public 路径</b>（Go 侧 8 个端点全挂 {@code WithJwt}）
     * ⇒ 故本类<b>不</b>提供 {@code isPublic}。
     */
    public static boolean isProtected(String uri) {
        if (uri == null) {
            return false;
        }
        return uri.equals(PROTECTED_PREFIX) || uri.startsWith(PROTECTED_PREFIX + "/");
    }
}
