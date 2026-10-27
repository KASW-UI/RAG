package com.rag.article.article.api.auth;

import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.List;

/**
 * article-api 的 <b>受保护路由表</b> —— <b>Filter 与 interceptor 共用同一份</b>。
 *
 * <h2>Go 侧依据（真源，逐行）</h2>
 * <pre>
 *   service/article/api/internal/handler/routes.go:19-47
 *     server.AddRoutes([]rest.Route{
 *         {POST,   "/article",        CreateArticleHandler},      ← 第一段：带 WithJwt
 *         {PUT,    "/article/:id",    UpdateArticleHandler},
 *         {DELETE, "/article/:id",    DeleteArticleHandler},
 *         {GET,    "/me/article/:id", GetAuthorArticleHandler},
 *     }, rest.WithJwt(serverCtx.Config.Auth.AccessSecret),
 *        rest.WithPrefix("/v1"), rest.WithTimeout(70*time.Second))
 *
 *     server.AddRoutes([]rest.Route{
 *         {GET, "/article/:id", GetArticleHandler},               ← 第二段：**无** WithJwt
 *         {GET, "/articles",   ListArticlesHandler},
 *     }, rest.WithPrefix("/v1"))
 *
 *     server.AddRoutes([]rest.Route{
 *         {POST, "/upload", UploadImageHandler},                  ← 第三段：**无** WithJwt
 *     }, rest.WithPrefix("/v1"), rest.WithMaxBytes(10MB))
 * </pre>
 * ⇒ 7 个端点里<b>恰好 4 个</b>要 JWT；<b>特别是 {@code POST /v1/upload} 是公开的</b> ——
 * 「上传图片要登录」是一个很容易凭直觉加上去、但 Go 侧<b>没有</b>的规则。
 *
 * <h2>⚠️ 为什么必须带 method 一起匹配（而不是只用路径）</h2>
 * <p>Spring 的 {@code addPathPatterns} 只按<b>路径</b>匹配，不看 method。
 * 若把 {@code /v1/article/{id}} 整条交给鉴权：
 * <pre>
 *   POST /v1/article/123        Spring: 鉴权 → 401（无 token）
 *                               Go:     该路由未注册 → 405，且**不进鉴权链**
 * </pre>
 * ⇒ 凭空多出一条「401 压过 405」的路径。
 *
 * <h2>⚠️ 为什么用 {@link PathPattern} 而不是字符串前缀比较</h2>
 * <p>{@code /v1/article} 与 {@code /v1/articles} 在前缀语义下会互相命中，
 * 而 Go 的 router 是<b>逐段精确匹配</b>（{@code /v1/articles} <b>不是</b> {@code /v1/article}
 * 的匹配）⇒ 前缀式判断会把两个不同的路由合并成一个安全边界 —— 而它们一个要鉴权一个不要。
 *
 * <h2>为什么判据必须只有一份</h2>
 * <p>DRIFT-USERAPI-009② 的修法把 JWT <b>验签</b>拆成两段：验签在
 * {@link GoStyleJwtAuthFilter}（Servlet Filter，70s 预算之外），响应在
 * {@code JwtAuthInterceptor}（HandlerInterceptor，70s 预算之内）。
 * <p>两段若各写一份判据，就会出现「Filter 判为受保护、Interceptor 判为放行」的缝隙：
 * <ul>
 *   <li>后者 ⇒ <b>鉴权被绕过</b> ⇒ 安全漏洞；</li>
 *   <li>前者 ⇒ interceptor 拿不到验签结果 ⇒ 落进兜底分支
 *       ⇒ 验签退回 70s 预算之内 ⇒ <b>DRIFT-USERAPI-009② 静默不闭环</b>。</li>
 * </ul>
 * ⇒ 判据收敛到这一个类，两个缝隙同时消失。
 *
 * <p>⚠️ **本域比 favorite / comment 更「干净」**：两域是「全保护、无 public 路径」，
 * 本域有 3 个<b>公开</b>端点 ⇒ 判据<b>必须</b>共用一份，否则 public 端点会被误伤。
 */
public final class ArticleJwtProtectedRoutes {

    /**
     * article-api 的<b>全部</b>受保护路由，逐条对应 Go {@code routes.go} 第一段。
     *
     * <p>⚠️ **这张表是「受保护集合」的<b>唯一</b>定义</b> —— 改这里等于改安全边界，
     * 两侧（Filter / interceptor）会同时变 ⇒ **不会出现单侧漂移**。
     */
    private static final List<ProtectedRoute> PROTECTED_ROUTES = List.of(
            new ProtectedRoute(HttpMethod.POST, "/v1/article"),
            new ProtectedRoute(HttpMethod.PUT, "/v1/article/{id}"),
            new ProtectedRoute(HttpMethod.DELETE, "/v1/article/{id}"),
            new ProtectedRoute(HttpMethod.GET, "/v1/me/article/{id}"));

    private ArticleJwtProtectedRoutes() {
    }

    /**
     * 请求是否命中受保护路由（<b>method + path</b> 双双命中才算）。
     *
     * @param method 请求方法
     * @param path   <b>已剥掉 context-path</b> 的请求路径
     */
    public static boolean isProtected(String method, String path) {
        if (method == null || path == null) {
            return false;
        }
        // ⚠️ PathPattern.matches 只接受 PathContainer（Spring 7 的签名），
        //    不是 String —— 传 String 编译不过。
        PathContainer pathContainer = PathContainer.parsePath(path);
        for (ProtectedRoute route : PROTECTED_ROUTES) {
            if (route.method().matches(method) && route.pattern().matches(pathContainer)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 取<b>剥掉 context-path</b> 之后的请求路径。
     *
     * <p>⚠️ Go 的 router 看到的是<b>剥掉前缀之后</b>的路径（{@code rest.WithPrefix("/v1")}
     * 已在注册时拼好）。本模块的 context-path 是 {@code /}，减掉它等于原样；
     * 但仍写成减法，是为了在有人改 context-path 时不会静默错位
     * —— 那会让 {@code /v1/article} 永远匹配不上，<b>鉴权整体失效</b>
     * （比报错更危险：所有写接口变公开）。
     */
    public static String pathWithinContext(jakarta.servlet.http.HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return path;
    }

    /** 一条受保护路由 = {@code method} + {@code path} 模板。缺一不可（见类注释）。 */
    private record ProtectedRoute(HttpMethod method, PathPattern pattern) {
        ProtectedRoute(HttpMethod method, String pattern) {
            this(method, PathPatternParser.defaultInstance.parse(pattern));
        }
    }
}
