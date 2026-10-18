package com.rag.common.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Snowflake Bean 化配置绑定，注入 workerId。
 *
 * <p>优先级（决策 5）：
 * <ol>
 *   <li>环境变量 {@code SNOWFLAKE_NODE_ID}（运维契约）</li>
 *   <li>{@link #workerId}（application.yml / spring properties）</li>
 *   <li>兜底 hash：{@code SERVICE_NAME + PID} sha256 前 2 字节 % 1024</li>
 * </ol>
 *
 * <p>注意：本类仅在 {@link SnowflakeBean} 中使用；环境变量与兜底逻辑始终由
 * {@link SnowflakeBean} 解析，本类只持有可选的 Properties 字段。
 *
 * <p>注：{@code @Component} 让本类随 {@code com.rag.common} 包扫描注册成 Bean；
 * 如果没有这注解，单靠 {@code @ConfigurationProperties} 不会被 Spring 容器发现。
 */
@Component
@ConfigurationProperties("snowflake")
public class SnowflakeProperties {

    /**
     * 显式指定的 workerId；{@code null} 时按优先级回退到环境变量 / 兜底 hash。
     */
    private Long workerId;

    public Long getWorkerId() {
        return workerId;
    }

    public void setWorkerId(Long workerId) {
        this.workerId = workerId;
    }
}