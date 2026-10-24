package com.rag.users.admin.rpc.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Snowflake 初始化触发器 —— 对应 user-rpc 端 SnowflakeBootstrap 同款位置。
 *
 * <p>实际 Snowflake 初始化由 service-common 模块的 {@code SnowflakeBean}（@Configuration）
 * 自动完成（workerId 计算、fail-fast），admin-rpc 启动时只需要确保 SnowflakeBean 被实例化。
 * 本类用 {@code ContextRefreshedEvent} 触发一次"使用"动作（{@code Snowflake.nextId()} probe），
 * 保证 Snowflake 单例 bean 在 admin-rpc 启动期已就绪。
 *
 * <p>{@code AdminSeederBootstrap} 依赖 {@code Snowflake.nextId()} 生成默认 admin uid，
 * 所以 Snowflake 必须先初始化（Order.HIGHEST_PRECEDENCE）。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SnowflakeBootstrap {

    private static final Logger log = LoggerFactory.getLogger(SnowflakeBootstrap.class);

    private volatile boolean initialized = false;

    @EventListener(ContextRefreshedEvent.class)
    public void init() {
        if (initialized) {
            return;
        }
        synchronized (this) {
            if (initialized) {
                return;
            }
            try {
                long probe = com.rag.common.snowflake.Snowflake.nextId();
                log.info("admin-rpc Snowflake ready, probe uid={}", probe);
                initialized = true;
            } catch (Throwable t) {
                log.error("admin-rpc Snowflake init failed", t);
                throw new IllegalStateException("snowflake init failed", t);
            }
        }
    }
}
