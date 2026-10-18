package com.rag.common.spring;

import com.rag.common.snowflake.Snowflake;
import com.rag.common.snowflake.Snowflake.State;
import org.springframework.stereotype.Component;

/**
 * Snowflake Bean 化入口（决策 2：Snowflake 同意 Bean 化）。
 *
 * <p>workerId 来源优先级（决策 5）：
 * <ol>
 *   <li>环境变量 {@code SNOWFLAKE_NODE_ID}（运维契约）</li>
 *   <li>{@link SnowflakeProperties#getWorkerId()}（application.yml / Spring properties）</li>
 *   <li>兜底 hash：{@code SERVICE_NAME + PID} sha256 前 2 字节 % 1024</li>
 * </ol>
 *
 * <p><b>主路径语义（决策 6）</b>：本 Bean 是 Snowflake 唯一 workerId owner。static {@code Snowflake.nextId()}
 * 通过 {@link Snowflake#bindBean(SnowflakeBean)} 委托本 Bean，避免同一 JVM 内两个不同
 * workerId 产生 ID 冲突。
 *
 * <p><b>业务模块当前用法</b>：业务 main 调 {@code Snowflake.init()} 触发 legacy HOLDER 初始化
 * （fail-fast 校验），但 ID 实际走 Bean 路径。HOLDER 的 workerId 在 Bean 存在时不被使用。
 */
@Component
public class SnowflakeBean {

    private final long workerId;
    private final State state = new State();

    public SnowflakeBean(SnowflakeProperties props) {
        this.workerId = resolveWorkerId(props);
        // 注册到 Snowflake 静态委托
        Snowflake.bindBean(this);
        // 与 Go 版 logx.Infof("Snowflake initialized with nodeId: %d", nodeId) 对齐
        Snowflake.LOG.info("Snowflake initialized with nodeId: {}", workerId);
    }

    /**
     * 生成下一个 64 位 ID；算法与 Go 版 bwmarrin/snowflake 同位宽。
     */
    public long nextId() {
        return Snowflake.nextIdInternal(workerId, state);
    }

    public long getWorkerId() {
        return workerId;
    }

    /**
     * 决策 5：env > Properties > 兜底。
     */
    private static long resolveWorkerId(SnowflakeProperties props) {
        // 1. env var（运维契约，最高优先级）
        String explicit = System.getenv("SNOWFLAKE_NODE_ID");
        if (explicit != null && !explicit.isEmpty()) {
            try {
                long parsed = Long.parseLong(explicit.trim());
                if (parsed >= 0) {
                    return parsed % (Snowflake.MAX_WORKER_ID + 1);
                }
            } catch (NumberFormatException ignore) {
                // parse 失败被忽略，按优先级继续
            }
        }
        // 2. Properties（application.yml）
        if (props != null && props.getWorkerId() != null && props.getWorkerId() >= 0) {
            return props.getWorkerId() % (Snowflake.MAX_WORKER_ID + 1);
        }
        // 3. 兜底 hash（复用 Snowflake.generateNodeId 算法）
        return Snowflake.generateNodeId();
    }
}