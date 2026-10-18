package com.rag.users.admin.api.internal.filter;

import com.rag.users.admin.api.internal.config.Config;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 对应 Go 端 routes.go:93 的 {@code rest.WithJwt(serverCtx.Config.AdminAuth.AccessSecret)}（go-zero JWT 中间件）。
 *
 * <p>L4 实现机制差异：Go 端用 go-zero 自带 {@code rest.WithJwt}（基于 golang-jwt/jwt/v5），
 * Java 侧用本 {@code Filter} + {@code io.jsonwebtoken}（jjwt 0.12.6）手工装配，
 * 严格对齐 go-zero 的六个可观察面行为：
 *
 * <p><b>六个可观察面逐项对齐</b>：
 * <ul>
 *   <li><b>Return</b>：成功 → 进入下游 {@link CheckBlacklistFilter}；
 *       失败 → 401 + 空 body + 不进下游</li>
 *   <li><b>Error</b>：401 状态码（与 Go 端 go-zero 一致 —— go-zero 默认 401 不带 body；
 *       <b>不</b>使用 Spring Security 默认的 401 body 格式）</li>
 *   <li><b>Log</b>：本 filter 不打日志；下游 Logic 失败时由 logger 统一记录</li>
 *   <li><b>Trace</b>：从 OTel {@code Context.current()} 取 span，jjwt 解析失败不影响 trace 链路</li>
 *   <li><b>Metric</b>：Go 端 JWT 验证失败无 metrics（401 走 silent parse），1:1 不补</li>
 *   <li><b>Lifecycle</b>：每个 HTTP request 走一次；用 {@link OncePerRequestFilter} 保证不重复</li>
 * </ul>
 *
 * <p><b>路径规则</b>（与 Go 端 routes.go 1:1）：
 * <ul>
 *   <li>public 路由（{@code /admin/login}、{@code /admin/create}）：<b>不</b>挂 JWT 校验，
 *       filter 直接放行；与 Go 端 {@code rest.AddRoutes([]Route{create, login})} 不带 WithJwt 一致</li>
 *   <li>protected 路由（其余 11 个）：必须带 {@code Authorization: Bearer <token>}；
 *       token 缺失/非法签名 → 401；合法 → 把 {@code claims.userId} 写入 request attribute "userId"
 *       （Go 端写 ctx key "userId"，值类型 json.Number；Java 端写 attribute "userId"，值类型 {@link Long}）</li>
 * </ul>
 *
 * <p><b>算法对齐</b>：
 * <ul>
 *   <li>HS256 + HMAC-SHA256（与 {@link com.rag.users.common.jwt.JwtUtil#getToken} 1:1）</li>
 *   <li>secret bytes = {@code secretKey.getBytes(UTF_8)}（与 {@code JwtUtil.getToken} 同款）</li>
 *   <li>claim 名 "userId" 必须保持字符串一致 —— JwtUtil.getToken 也用 "userId" 作为 claim key</li>
 * </ul>
 *
 * <p><b>由 {@code AdminApiApplication} 装配 URL pattern</b>：通过 {@code FilterRegistrationBean}
 * 显式注册到 11 条 protected 路径；不注册到 {@code /admin/login}、{@code /admin/create}。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)   // 比 CheckBlacklistFilter 早跑（CheckBlacklistFilter 是 +20）
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** 与 Go 端 routes.go 第二组 AddRoutes 的 11 个 protected path 1:1。 */
    private static final Set<String> PROTECTED_PATH_SUFFIXES = Set.of(
            "/admin/banuser",
            "/admin/delete",
            "/admin/invite/create",
            "/admin/getself",
            "/admin/getuser",
            "/admin/getuserlist",
            "/admin/logout",
            "/admin/resetuserpassword",
            "/admin/unbanuser",
            "/admin/update",
            "/admin/updateuser"
    );

    private final SecretKey key;

    public JwtAuthenticationFilter(Config config) {
        byte[] secretBytes = config.getAdminAuth().getAccessSecret().getBytes(StandardCharsets.UTF_8);
        this.key = Keys.hmacShaKeyFor(secretBytes);
    }

    /**
     * 是否本 request 路径需要 JWT 校验。
     * 对应 Go 端 routes.go 的两条 {@code rest.AddRoutes} 块：login/create 不在 WithJwt 块中。
     */
    public static boolean requiresAuth(String requestURI) {
        if (requestURI == null) return false;
        for (String suffix : PROTECTED_PATH_SUFFIXES) {
            if (requestURI.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // login / create 直接放行 —— 与 Go 端 routes.go 一致
        return !requiresAuth(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            // Go 端 rest.WithJwt：缺少 Bearer token → 401 + 空 body
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        String token = authHeader.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        long uid;
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            Object claim = jws.getPayload().get("userId");
            if (claim instanceof Number) {
                // claim 是数字（含 ≤0 情况） → 写 attr；下游 Logic（如 GetSelf）会校验 uid>0
                uid = ((Number) claim).longValue();
                request.setAttribute("userId", Long.valueOf(uid));
            } else {
                // claim 缺失 / 类型错（非 Number） → <b>不</b>写 attr；
                // 下游 Logic 找不到 attr → 业务码 1006（token 已过期/无效）
                // （与 Go 端 go-zero rest.WithJwt 静默解析失败等价：request 进入下游但 ctx 缺 userId）
            }
        } catch (Throwable t) {
            // 签名错 / 过期 —— 401 + 空 body，与 Go 端 silent 行为一致（go-zero rest.WithJwt）
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        filterChain.doFilter(request, response);
    }
}