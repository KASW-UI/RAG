package com.rag.like.like.api.config;

import com.rag.like.like.api.interceptor.LikeJwtAuthInterceptor;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 配置 —— 挂 JWT 鉴权 + 注册 go-zero 对齐的 Jackson 3 规则。
 *
 * <p><b>路径注册与 Go 端逐条对应</b>：
 * <pre>
 *   Go  : server.AddRoutes([...6 个路由...],
 *                          rest.WithJwt(serverCtx.Config.UserAuth.AccessSecret),
 *                          rest.WithPrefix("/like/v1"))
 * </pre>
 *
 * <p><b>为什么只有一条拦截器、且没有 excludePathPatterns</b>：
 * Go 侧 like api <b>只注册过一段路由表</b>，6/6 全量 {@code rest.WithJwt}，
 * <b>不存在 public 路径</b>。因此这里不需要 exclude —— 加了反而凭空制造分歧。
 *
 * <p><b>⚠️ 一个必须显式说明的表达层差异</b>：Go 的 {@code rest.WithPrefix("/like/v1")}
 * 与 {@code /getlikecount} 拼接出的是精确路径 {@code /like/v1/getlikecount}；
 * 而 Spring 的 {@code /like/v1/**} 是<b>前缀通配</b>，会覆盖
 * {@code /like/v1/ 下任何路径}。因为本服务的 6 个 Controller 端点都挂在
 * {@code @RequestMapping("/like/v1")} 下、且全部需要鉴权，两者覆盖集合等价。
 * 真正多出来的只有「未注册的子路径」，那些会由 DispatcherServlet 返回
 * {@code NoResourceFoundException} → 404（见 {@code GlobalExceptionHandler}），
 * 而 Go 的 router 同样不匹配 → 404，两侧一致。
 *
 * <p><b>关于 {@link LikeGoStyleCoercion}</b>：把 go-zero 的解析规则施加到
 * <b>本模块 MVC</b> 的 Jackson 3 mapper 上。对 like 域的实际影响：
 * {@code target_type} / {@code target_id} 这类 String 字段收到 {@code {"target_type":123}}
 * 两侧都拒；{@code action_type} / {@code cursor} / {@code page_size} 收到
 * {@code {"action_type":"1"}} / {@code {"cursor":"10"}} 两侧都拒（Go 是 type mismatch）；
 * {@code weight} 收到 {@code {"weight":"1.5"}} 两侧都拒。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /** Go 侧路由前缀（{@code rest.WithPrefix("/like/v1")}）。 */
    private static final String LIKE_PREFIX = "/like/v1/**";

    private final LikeJwtAuthInterceptor likeJwtAuthInterceptor;

    public WebMvcConfig(LikeJwtAuthInterceptor likeJwtAuthInterceptor) {
        this.likeJwtAuthInterceptor = likeJwtAuthInterceptor;
    }

    @Bean
    public JsonMapperBuilderCustomizer goStyleCoercionCustomizer() {
        return LikeGoStyleCoercion::applyTo;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(likeJwtAuthInterceptor)
                .addPathPatterns(LIKE_PREFIX);
    }
}