package com.rag.users.user.rpc.bootstrap;

import com.rag.common.snowflake.Snowflake;
import com.rag.common.spring.SnowflakeBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Snowflake workerId 启动期校验 —— @EventListener(ApplicationReadyEvent)。
 *
 * 对应 Go: service/user/user/rpc/user.go 的 snowflake.NewNode(...) 调用 + NodeId 范围校验。
 *
 * 框架职责:
 *   - Spring @EventListener —— 应用启动完成后触发
 *   - SnowflakeBean 已由 service/common 自动装配（@Component + workerId 解析），本类只做 fail-fast 校验
 *   - 检查 workerId 落在 [0, 1023] 范围内（10 位 Snowflake workerId 位）
 *   - 失败抛 IllegalStateException 让 SpringApplication 启动失败（fail-fast）
 *   - registerLogic 调 Snowflake.nextId() 生成 uid（users_user.uid 主键策略 = ASSIGN_ID）
 *
 * 注：SnowflakeBean 已主动调 Snowflake.bindBean(this) + Snowflake.init() 等价操作，
 *     这里再做一次 init() 校验是 fail-fast（在所有业务线程启动前先确认 workerId 合法）。
 */
@Component
public class SnowflakeBootstrap {

    private static final Logger log = LoggerFactory.getLogger(SnowflakeBootstrap.class);

    private final SnowflakeBean snowflakeBean;

    public SnowflakeBootstrap(SnowflakeBean snowflakeBean) {
        this.snowflakeBean = snowflakeBean;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initSnowflake() {
        long workerId = snowflakeBean.getWorkerId();
        if (workerId < 0 || workerId > Snowflake.MAX_WORKER_ID) {
            throw new IllegalStateException(
                    "Snowflake workerId out of range [0, " + Snowflake.MAX_WORKER_ID + "]: " + workerId);
        }
        // 触发 Snowflake.init() 校验（throws if generateNodeId failed）
        Snowflake.init();
        log.info("SnowflakeBootstrap verified workerId={}, Snowflake ready", workerId);
    }
}