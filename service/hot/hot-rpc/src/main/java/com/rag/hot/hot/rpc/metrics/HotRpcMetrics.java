package com.rag.hot.hot.rpc.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * hot-rpc 业务指标 —— 对应 Go 端 {@code service/hot/rpc/internal/mqs/hot_handler.go:30-45} 的两个
 * {@code metric.CounterVec}：
 *
 * <pre>
 * Namespace: article_hot / Subsystem: kafka / Name: consume_total  Labels: [type]
 * Namespace: article_hot / Subsystem: redis / Name: sync_total    Labels: [status]
 * </pre>
 *
 * <p>渲染后的 Prometheus 名与 Go 侧逐字一致：{@code article_hot_kafka_consume_total{type}} /
 * {@code article_hot_redis_sync_total{status}} ⇒ <b>Prometheus 抓取端不需要改配置</b>。
 * （命名方式沿用本仓库 message-rpc {@code MessageMetrics} 的既有约定。）
 *
 * <p><b>标签取值域</b>：
 * <ul>
 *   <li>{@code type} = {@code event.Type}，且只在「权重校验通过之后」才 Inc
 *       ⇒ 实际出现的取值域 ⊆ 配置里 {@code Interaction.Weights} 的 {@code Name} 集合。
 *       未知或零权重的 type 会被 drop，永不出现在指标里。</li>
 *   <li>{@code status} = {@code "success"} / {@code "fail"}，对应 Go {@code syncToRedis} 的两条分支。</li>
 * </ul>
 *
 * <p>⚠️ Go 侧还有 {@code internal/metrics/metrics.go} 的 {@code InitMetrics}（:9091 promhttp），
 * <b>全仓零调用点</b>（实测 grep 确认：comment/follow/message/favorite/points/user 都调了它，
 * 只有 hot/rpc 与 hot/api 没调）⇒ 死代码，<b>不迁移</b>。真正生效的暴露端点是
 * {@code prometheus.StartAgent} 的 {@code Prometheus.Port}（19091/19092/19093）。
 */
@Component
public class HotRpcMetrics {

    /** 对应 Go {@code article_hot_kafka_consume_total labels: [type]}。 */
    private static final String KAFKA_CONSUME_TOTAL = "article_hot_kafka_consume_total";

    /** 对应 Go {@code article_hot_redis_sync_total labels: [status]}。 */
    private static final String REDIS_SYNC_TOTAL = "article_hot_redis_sync_total";

    private static final String STATUS_SUCCESS = "success";
    private static final String STATUS_FAIL = "fail";

    private final MeterRegistry registry;

    public HotRpcMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * 对应 Go {@code eventConsumeTotal.Inc(event.Type)}。
     *
     * <p>⚠️ 调用位置语义敏感：Go 在<b>三类校验全部通过之后</b>才 Inc
     * （{@code hot_handler.go:168}），且<b>早于</b> {@code hkMap} 查找（{@code :171}）——
     * 即分区不在 map 时<b>仍然计数</b>，只是不入 sketch。调用方必须保持该次序。
     */
    public void eventConsumed(String type) {
        Counter.builder(KAFKA_CONSUME_TOTAL)
                .description("Total number of consumed hot events")
                .tag("type", type)
                .register(registry)
                .increment();
    }

    /** 对应 Go {@code redisSyncTotal.Inc("success")}（{@code hot_handler.go:240}）。 */
    public void redisSyncSuccess() {
        redisSync(STATUS_SUCCESS);
    }

    /** 对应 Go {@code redisSyncTotal.Inc("fail")}（{@code hot_handler.go:222,238}）。 */
    public void redisSyncFail() {
        redisSync(STATUS_FAIL);
    }

    private void redisSync(String status) {
        Counter.builder(REDIS_SYNC_TOTAL)
                .description("Total number of redis sync operations")
                .tag("status", status)
                .register(registry)
                .increment();
    }
}