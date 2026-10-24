package com.rag.like.like.rpc.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * like-rpc 的 gRPC 侧指标 —— 对应 Go 端
 * {@code service/like/rpc/internal/metrics/metrics.go} 的两个 CounterVec。
 *
 * <table border="1">
 *   <caption>Go ↔ Micrometer 对照</caption>
 *   <tr><th>Go</th><th>Micrometer 名</th><th>labels</th></tr>
 *   <tr><td>{@code LikeActionCount}（Subsystem {@code "like-rpc"}）</td>
 *       <td>{@code like_rpc_action_total}</td><td>{@code target_type / action / result}</td></tr>
 *   <tr><td>{@code LikeQueryCacheCount}（Subsystem {@code "like_rpc"}）</td>
 *       <td>{@code like_rpc_query_cache_total}</td><td>{@code target_type / result}</td></tr>
 * </table>
 *
 * <p><b>⚠️ Go 侧两个 Subsystem 拼写不一致</b>（一个连字符一个下划线），是源码事实。
 * Prometheus 的指标名合法字符集不含连字符，所以 Java 侧统一用下划线；
 * 若要严格对齐 Go 的字面量，Prometheus 侧的抓取配置本身就得先能接受那个名字。
 * <b>此处不构成行为漂移</b>：指标名不是 API 契约，只是运维可读性差异。
 *
 * <p><b>result 取值必须保持 Go 的原样</b>，因为告警规则依赖它们：
 * <ul>
 *   <li>{@code LikeActionCount}：{@code invalid_param / idempotent / redis_error /
 *       snowflake_error / json_error / kafka_error / redis_read_error / success}</li>
 *   <li>{@code LikeQueryCacheCount}：{@code hit / miss / redis_error}</li>
 * </ul>
 * 缺一个或拼错一个，对应的告警就静默失效。
 */
@Component
public class LikeRpcMetrics {

    private final MeterRegistry registry;

    public LikeRpcMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * {@code LikeActionCount.WithLabelValues(targetType, action, result).Inc()}
     *
     * <p><b>⚠️ label 取值不做白名单过滤</b>：Go 直接把 {@code target_type} 塞进 label，
     * 而 target_type 来自请求参数。Go 因此存在「高基数」风险，本实现照搬 ——
     * 加白名单会让 {@code unknown} 这类值消失，改变告警可见性。
     */
    public void action(String targetType, String action, String result) {
        Counter.builder("like_rpc_action_total")
                .description("Total number of like/dislike actions")
                .tags("target_type", targetType, "action", action, "result", result)
                .register(registry)
                .increment();
    }

    /**
     * {@code LikeQueryCacheCount.WithLabelValues(targetType, result).Add(n)}
     *
     * @param count Go 侧的 {@code Add(float64(...))}，允许批量值（如一次回源记多个 id）
     */
    public void cache(String targetType, String result, double count) {
        Counter.builder("like_rpc_query_cache_total")
                .description("Cache hit/miss counts for like queries")
                .tags("target_type", targetType, "result", result)
                .register(registry)
                .increment(count);
    }

    /** {@code LikeQueryCacheCount...Inc()} 的计数 1 版本。 */
    public void cache(String targetType, String result) {
        cache(targetType, result, 1.0d);
    }

    /**
     * 记录单个 RPC 的处理耗时。
     *
     * <p><b>Go 侧没有这个指标</b>，新增理由：Go 侧只能靠
     * {@code observability.NewUnaryServerInterceptor} 在 gRPC 层记录，
     * Java 侧已通过 {@code com.rag.common.observability.RpcInterceptor} 统一承担（挂在 server 上）。
     * 本方法<b>不</b>在用例里调用，留给需要按业务名细分耗时的地方；
     * 全局 RPC 耗时由那个公共拦截器负责，避免两套口径。
     */
    public void recordLatency(String operation, long durationNanos) {
        Timer.builder("like_rpc_operation_latency")
                .description("like-rpc operation latency")
                .tag("operation", operation)
                .register(registry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }
}