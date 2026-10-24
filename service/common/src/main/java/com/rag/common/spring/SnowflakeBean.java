package com.rag.common.spring;

import com.rag.common.snowflake.Snowflake;
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
 * <p><b>M11 修复</b>：本 Bean 此前<b>自己持有</b>一份 {@code State}，而
 * {@code Snowflake} 里还有另一份静态 {@code HOLDER.state}，靠 {@code bindBean} 决定走哪边。
 * 后果是同一 JVM 可能有两个不同 workerId 的生成器交替产 ID。
 *
 * <p>现在本 Bean <b>不再持有任何生成状态</b>，只负责在容器 refresh 期把 workerId
 * 解析结果交给 {@link Snowflake#setWorkerId(Long)}，之后全进程共用 {@code Snowflake} 里
 * 那唯一一份状态 —— 与 Go 版「进程内一个 {@code snowflake.Node}」一一对应。
 *
 * <p><b>调用时序要求</b>：本 Bean 必须在任何 {@code Snowflake.nextId()} 调用之前构造完成。
 * Spring 容器 refresh 期所有单例 Bean 先于应用接受流量初始化，因此这是成立的；
 * {@code Snowflake.setWorkerId} 本身也做了 first-wins 保护，重复调用不会让 workerId 漂移。
 */
@Component
public class SnowflakeBean {

    public SnowflakeBean(SnowflakeProperties props) {
        long workerId = resolveWorkerId(props);
        // 把解析结果交给 Snowflake 的唯一状态；本 Bean 不再持有 State
        Snowflake.setWorkerId(workerId);
    }

    /**
     * 本进程生效的 workerId；委托 {@link Snowflake#currentWorkerId()}，与全局唯一状态同源。
     */
    public long getWorkerId() {
        Long id = Snowflake.currentWorkerId();
        if (id == null) {
            Snowflake.init();
            id = Snowflake.currentWorkerId();
        }
        return id == null ? 0L : id;
    }

    /**
     * 生成下一个 64 位 ID；算法与 Go 版 bwmarrin/snowflake v0.3.0 同位宽。
     */
    public long nextId() {
        return Snowflake.nextId();
    }

    /**
     * 决策 5：env &gt; Properties &gt; 兜底。
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