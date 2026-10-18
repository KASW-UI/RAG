package com.rag.common.spring;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Observability 配置绑定（{@code application.yml}）：
 * <pre>{@code
 * observability:
 *   slow-threshold: 750ms    # Go duration 格式或纯毫秒
 * }</pre>
 *
 * <p>优先级：
 * <ol>
 *   <li>{@link #slowThreshold}（application.yml / Spring properties）</li>
 *   <li>环境变量 {@code SEA_OBSERVABILITY_SLOW_THRESHOLD} / {@code SEA_OBSERVABILITY_SLOW_THRESHOLD_MS}</li>
 *   <li>默认 500ms（{@code ObservabilityConstants.DEFAULT_SLOW_THRESHOLD}）</li>
 * </ol>
 *
 * <p>由 {@link HttpMiddlewareFilter} / {@link RpcServerInterceptorFactory} 注入消费；
 * 不存在时上层 fallback 到 {@link Observability#slowThreshold()}（env var 读取）。
 *
 * <p>注：{@code @Component} 让本类随 {@code com.rag.common} 包扫描注册成 Bean；
 * 如果没有这注解，单靠 {@code @ConfigurationProperties} 不会被 Spring 容器发现。
 */
@Component
@ConfigurationProperties("observability")
public class ObservabilityProperties {

    /**
     * 慢调用阈值；{@code null} 时 {@code Observability.slowThreshold()} 回退到 env var 读取。
     */
    private Duration slowThreshold;

    public Duration getSlowThreshold() {
        return slowThreshold;
    }

    public void setSlowThreshold(Duration slowThreshold) {
        this.slowThreshold = slowThreshold;
    }
}