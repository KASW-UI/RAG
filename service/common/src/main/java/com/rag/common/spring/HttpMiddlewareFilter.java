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
 * <p><b>serviceName</b>：从 {@code spring.application.name} 注入（与
 * {@link LoggerFactoryBean} 同款来源）；为空时回退到 {@code "unknown-service"}。
 *
 * <p><b>slowThreshold</b>：从 {@link ObservabilityProperties#getSlowThreshold()} 读取；
 * 为 null 时回退到 {@link Observability#slowThreshold()}（env var fallback）。
 *
 * <p><b>timeout（H1 修复）</b>：从 {@link ObservabilityProperties#getHttpTimeout()} 读取，
 * 为 null 时回退 {@link Duration#ZERO}（不限时，对齐 Go 的 {@code TimeoutFromMillis(0) → 0}）。
 * 此前本类把 timeout <b>硬编码</b>成 {@code Duration.ZERO} 且 javadoc 声称「业务可通过同名 Bean
 * 覆盖或加 setter 扩展」—— 但 {@link ObservabilityProperties} 当时根本没有 timeout 字段，
 * 那个扩展点不存在。Go 侧 {@code DisableNativeRestTimeout} 的产物是 {@code conf.Timeout}
 * （user 系 yaml 未配置 → go-zero 默认 3000ms），现在由各模块 yaml 的
 * {@code observability.http-timeout: 3s} 承载。
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
        Duration httpTimeout = props != null ? props.getHttpTimeout() : null;
        this.delegate = HttpMiddleware.newHttpMiddleware(
                effectiveServiceName,
                httpTimeout,
                slowThreshold
        );
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        delegate.doFilter(req, resp, chain);
    }
}