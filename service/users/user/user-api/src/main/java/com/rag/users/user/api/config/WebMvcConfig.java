package com.rag.users.user.api.config;

import com.rag.users.user.api.interceptor.JwtAuthInterceptor;
import com.rag.users.common.http.GoStyleCoercion;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 配置 —— 注册 JwtAuthInterceptor 拦截 /user/v1/** 所有路径。
 *
 * <p>路径规则：
 * <ul>
 *   <li>JwtAuthInterceptor 拦截 {@code /user/v1/**}</li>
 *   <li>JWT 鉴权细节（public/protected 区分、blacklist 检查）在 {@link JwtAuthInterceptor#preHandle}
 *       内部按 path 匹配完成（与 Go 端 rest.AddRoutes 块区分对齐）</li>
 * </ul>
 */
/**
 * <p>另注册 {@link GoStyleCoercion} 的 Jackson customizer（E-3 根因 R1）：
 * 关掉标量强转与浮点截断，对齐 go-zero 的解析层。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtAuthInterceptor jwtAuthInterceptor;

    /**
     * E-3 根因 R1：把 users-common 的对齐规则施加到 <b>本模块 MVC</b> 的 Jackson 3 mapper 上。
     *
     * <p><b>为什么是 {@code JsonMapperBuilderCustomizer} 而不是改 converter 上的 ObjectMapper</b>：
     * <ul>
     *   <li>Boot 4 的 {@code Jackson2ObjectMapperBuilderCustomizer} 已随自动配置模块化消失；</li>
     *   <li>{@code MappingJackson2HttpMessageConverter}（Jackson 2）已标记待删除；</li>
     *   <li><b>更关键的是 Jackson 3 的 coercion 只能在 builder 上配</b> ——
     *       {@code ObjectMapper} 上根本没有 {@code coercionConfigFor}，
     *       事后改 mapper 这条路在 Jackson 3 里不存在。</li>
     * </ul>
     *
     * <p><b>只动 MVC 这一个 mapper</b>：gRPC 用 protobuf marshaller、
     * MyBatis 与 {@code JsonbTypeHandler} 各持自己的 Jackson 2 mapper，
     * 都与 go-zero 的 http 解析层无关，不该被这两条规则波及。
     */
    @Bean
    public JsonMapperBuilderCustomizer goStyleCoercionCustomizer() {
        return GoStyleCoercion::applyTo;
    }

    public WebMvcConfig(JwtAuthInterceptor jwtAuthInterceptor) {
        this.jwtAuthInterceptor = jwtAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns("/user/v1/**");
    }
}
