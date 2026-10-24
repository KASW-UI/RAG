package com.rag.like.like.rpc.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * like 消费侧指标 —— 对应 Go 端
 * {@code service/like/rpc/internal/mq/internal/metrics/metrics.go} 的 {@code ConsumeLikeMsgCount}。
 *
 * <p>Go：{@code CounterVec{Subsystem: "like_mq", Name: "consume_total"}, []string{"action", "result"}}。
 *
 * <p><b>⚠️ Go 的第一个 label 名叫 {@code action}，但所有调用点传的都是 "receive" 或 "flush"</b>，
 * 与点赞动作无关。这是 Go 的命名遗留（大概是照抄别的域的 counter）。
 * 照搬会让这个 label 永远只有两个值，看上去像 bug 但确实是现状；
 * <b>不</b>改成 {@code stage} 之类更准确的命名，因为已有告警规则可能已按 {@code action} 建好查询。
 *
 * <p><b>result 取值全集</b>（照抄 Go 的调用点，缺一个就少一条可观测路径）：
 * <ul>
 *   <li>{@code receive} × {@code json_error / empty_msg_id / outbox_error / tx_error / committed}</li>
 *   <li>{@code flush} × {@code snowflake_error}</li>
 * </ul>
 * 注意 {@code empty_msg_id} 这个名字其实对应的是「字段非法」，与 msg_id 无关 —— Go 的命名问题，照搬。
 */
@Component
public class LikeMqMetrics {

    private final MeterRegistry registry;

    public LikeMqMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** {@code ConsumeLikeMsgCount.WithLabelValues(stage, result).Inc()}。 */
    public void consume(String stage, String result) {
        Counter.builder("like_mq_consume_total")
                .description("Total number of like message consumed from Kafka")
                .tags("action", stage, "result", result)
                .register(registry)
                .increment();
    }
}