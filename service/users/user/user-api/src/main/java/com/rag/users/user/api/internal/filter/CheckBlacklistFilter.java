package com.rag.users.user.api.internal.filter;

import com.rag.users.user.api.internal.filter.JwtAuthenticationFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 对应 Go 端 internal/middleware/checkblacklistmiddleware.go。
 *
 * <p>L4 实现机制差异：Go 端用 {@code rest.Middleware = func(http.HandlerFunc) http.HandlerFunc}，
 * Java 侧用 {@link OncePerRequestFilter}（等价的拦截机制）。
 *
 * <p><b>六个可观察面逐项对齐</b>：
 * <ul>
 *   <li><b>Return</b>：成功（不在黑名单）→ 进入下游 handler；失败（在黑名单）→ 401 + JSON body + 不进 handler；
 *       <b>注意：JWT 校验失败时本 filter 不会触发</b>（路径规则一致 + JwtAuthenticationFilter 先短路）</li>
 *   <li><b>Error</b>：401 + body = {@code {"code":401,"msg":"登录已失效,请重新登陆"}}
 *       （与 Go 端 {@code w.Write([]byte('{"code":401, "msg":"登录已失效,请重新登陆"}'))} 1:1）</li>
 *   <li><b>Log</b>：本 filter 不打日志（Go 端同样不打）</li>
 *   <li><b>Trace</b>：从 OTel Context.current() 取 span，Redis Exists 失败不影响 trace</li>
 *   <li><b>Metric</b>：Go 端 checkblacklist <b>无 metrics 埋点</b> —— 1:1 不补</li>
 *   <li><b>Lifecycle</b>：每个 HTTP request 走一次；用 {@link OncePerRequestFilter} 保证不重复</li>
 * </ul>
 *
 * <p><b>路径规则</b>（与 Go 端 routes.go 一致）：login / register 路径 <b>不挂</b>本 filter。
 *
 * <p><b>执行序列</b>（与 Go 端 middleware chain 顺序一致）：
 * <ol>
 *   <li>{@link com.rag.users.user.api.internal.security.JwtAuthenticationFilter} 先跑（验签 + 设 userId）</li>
 *   <li>本 filter 再跑：
 *     <ul>
 *       <li>Authorization header 缺失 或 "Bearer " 后 token 为空 → 直接放行，<b>不</b>设 jwt_token
 *           （与 Go 端 middleware:Handle 第 26-33 行的 early-return 一致）</li>
 *       <li>Redis Exists {@code user:jwt_blacklist:<token>} 返回 true → 401 + JSON body + 不进下游</li>
 *       <li>Redis Exists 返回 false → 放行 + 把 token 字符串写入 request attribute "jwt_token"
 *           （与 Go 端 {@code context.WithValue(r.Context(), "jwt_token", token)} 等价）</li>
 *       <li>Redis Exists 抛异常（连接失败等） → <b>不</b>返回 401，直接放行
 *           （与 Go 端 {@code if err == nil && exists} 行为一致：Redis 故障不阻塞业务）</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <p><b>Redis key 格式冻结</b>（与 Go 端 {@code fmt.Sprintf("user:jwt_blacklist:%s", token)} 1:1）：
 * <pre>
 *   key = "user:jwt_blacklist:" + rawTokenString
 * </pre>
 *
 * <p><b>Response body 冻结</b>（与 Go 端 1:1，含 UTF-8 编码）：
 * <pre>
 *   Content-Type: application/json;charset=utf-8
 *   Body: {"code":401, "msg":"登录已失效,请重新登陆"}
 * </pre>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)   // 在 JwtAuthenticationFilter (+10) 之后跑
public class CheckBlacklistFilter extends OncePerRequestFilter {

    /** 与 JwtAuthenticationFilter 同款 protected 路径集合（保证两个 filter 路由规则一致）。 */
    private static final Set<String> PROTECTED_PATH_SUFFIXES = Set.of(
            "/user/delete", "/user/get", "/user/avatar/history",
            "/user/logout", "/user/avatar", "/user/avatar/select", "/user/update"
    );

    /** 与 Go 端 fmt.Sprintf("user:jwt_blacklist:%s", token) 1:1。 */
    private static final String BLACKLIST_KEY_PREFIX = "user:jwt_blacklist:";

    /** 与 Go 端 w.Header().Set("Content-Type", "appliaction/json;charset=utf-8") 1:1（保留 go-zero 拼写错误"appliaction"）。 */
    @SuppressWarnings("SpellCheckingInspection")
    private static final String RESPONSE_CONTENT_TYPE = "appliaction/json;charset=utf-8";

    /** 与 Go 端 w.Write([]byte(...)) 1:1。注意 msg 中"登录已失效,请重新登陆"原文使用中文逗号 + "登陆"（不是"登录"）。 */
    private static final String RESPONSE_BODY = "{\"code\":401, \"msg\":\"登录已失效,请重新登陆\"}";

    private final StringRedisTemplate redisTemplate;

    public CheckBlacklistFilter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !JwtAuthenticationFilter.requiresAuth(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null) {
            // 与 Go 端第 26-28 行 early-return 一致：不进 blacklist 检查，不设 jwt_token
            filterChain.doFilter(request, response);
            return;
        }
        String token = authHeader.startsWith("Bearer ")
                ? authHeader.substring("Bearer ".length()).trim()
                : "";
        if (token.isEmpty()) {
            // 与 Go 端第 30-33 行 early-return 一致
            filterChain.doFilter(request, response);
            return;
        }

        String blacklistKey = BLACKLIST_KEY_PREFIX + token;
        Boolean exists;
        try {
            // Redis Exists —— 与 Go 端 redis.ExistsCtx(r.Context(), blackListKey) 1:1
            exists = redisTemplate.hasKey(blacklistKey);
        } catch (Throwable t) {
            // Redis 故障：不阻塞业务（与 Go 端 err == nil && exists 行为一致）
            exists = null;
        }
        if (exists != null && exists) {
            response.setHeader("Content-Type", RESPONSE_CONTENT_TYPE);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getOutputStream().write(RESPONSE_BODY.getBytes(StandardCharsets.UTF_8));
            return;
        }

        // 与 Go 端 context.WithValue(r.Context(), "jwt_token", token) 等价
        request.setAttribute("jwt_token", token);
        filterChain.doFilter(request, response);
    }
}