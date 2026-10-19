package com.rag.users.user.api.config;

import com.rag.users.user.api.interceptor.JwtAuthInterceptor;
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
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtAuthInterceptor jwtAuthInterceptor;

    public WebMvcConfig(JwtAuthInterceptor jwtAuthInterceptor) {
        this.jwtAuthInterceptor = jwtAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns("/user/v1/**");
    }
}
