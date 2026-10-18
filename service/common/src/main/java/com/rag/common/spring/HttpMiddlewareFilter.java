package com.rag.common.spring;

import com.rag.common.observability.HttpMiddleware;
import com.rag.common.observability.Observability;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;

/**
 * Spring 入口：把 HTTP observability filter 注册为 Spring Bean，让 Spring Boot 自动挂到 Servlet
 * 容器（无需业务在 {@code @SpringBootApplication} 写 {@code FilterRegistrationBean}）。
 *
 * <p>当前实现为薄包装——内部仍委托 {@link HttpMiddleware#newHttpMiddleware(String, Duration, Duration)}
 * 构造 Filter，确保行为与现有 {@code HttpMiddlewareTest} 1:1 一致。
 *
 * <p><b>serviceName</b>：从 {@code spring.application.name} 注入（与
 * {@link LoggerFactoryBean} 同款来源）；为空时回退到 {@code "unknown-service"}。
 *
 * <p><b>slowThreshold</b>：从 {@link ObservabilityProperties#getSlowThreshold()} 读取；
 * 为 null 时回退到 {@link Observability#slowThreshold()}（env var fallback）。
 *
 * <p><b>timeout</b>：本 Bean 默认 {@link Duration#ZERO}（不限时）；业务需要可通过同名 Bean
 * 覆盖或加 setter 暴露。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class HttpMiddlewareFilter implements Filter {

    private final Filter delegate;

    public HttpMiddlewareFilter(
            @Value("${spring.application.name:unknown-service}") String serviceName,
            ObservabilityProperties props
    ) {
        String effectiveServiceName = (serviceName == null || serviceName.isEmpty())
                ? "unknown-service"
                : serviceName;
        Duration slowThreshold = props != null && props.getSlowThreshold() != null
                ? props.getSlowThreshold()
                : Observability.slowThreshold();
        this.delegate = HttpMiddleware.newHttpMiddleware(
                effectiveServiceName,
                Duration.ZERO,
                slowThreshold
        );
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        delegate.doFilter(req, resp, chain);
    }
}