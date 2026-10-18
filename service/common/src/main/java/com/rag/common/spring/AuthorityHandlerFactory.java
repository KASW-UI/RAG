package com.rag.common.spring;

import com.rag.common.communityfact.AuthorityHandler;
import com.rag.common.communityfact.AuthorityHandler.AuthorityLookup;
import jakarta.servlet.http.HttpServlet;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.stereotype.Component;

/**
 * Spring 入口：把 {@link AuthorityHandler} 内部 HTTP 查询端点（{@code /internal/v1/community/facts}）
 * 注册为 Spring Bean。
 *
 * <p><b>业务用法</b>（业务模块在自己 {@code @SpringBootApplication} 类里写）：
 * <pre>{@code
 * @Bean
 * public ServletRegistrationBean<HttpServlet> authorityHandler(Config config, AuthorityLookup lookup) {
 *     return AuthorityHandlerFactory.register(config.getProducer(), config.getToken(), lookup);
 * }
 * }</pre>
 *
 * <p><b>注意</b>：{@link AuthorityLookup} 是业务特定逻辑（每个 domain 不同），所以 Factory 不
 * 能完全无参化，业务仍需在自己 {@code @Bean} 方法里提供 lookup 函数。
 */
@Component
public class AuthorityHandlerFactory {

    /**
     * 端点路径（与 Go 版 {@code /internal/v1/community/facts} 1:1）。
     */
    public static final String AUTHORITY_PATH = "/internal/v1/community/facts";

    /**
     * 业务模块在 {@code @Bean} 方法里调此工厂方法注册 AuthorityHandler。
     *
     * <p>路径硬编码为 {@value #AUTHORITY_PATH}（与 Go 版对齐）；业务如有特殊路径需求请
     * 直接调用 {@link AuthorityHandler#create(String, String, AuthorityLookup)} + 自己包装
     * {@code ServletRegistrationBean}。
     */
    public static ServletRegistrationBean<HttpServlet> register(
            String producer,
            String token,
            AuthorityLookup lookup
    ) {
        HttpServlet servlet = AuthorityHandler.create(producer, token, lookup);
        ServletRegistrationBean<HttpServlet> bean = new ServletRegistrationBean<>(servlet);
        // Spring Boot 4 API：用 addUrlMappings(...)（复数）替代旧版 addUrlPatterns(...)
        bean.addUrlMappings(AUTHORITY_PATH);
        return bean;
    }
}