package com.rag.task.task.rpc.mq;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 两个 sink 的 OTEL span 属性 —— 对应 Go 端
 * {@code internal/sink/like_sink.go:147-163} 的 {@code taskMessageAttrs} 与 {@code taskFlushAttrs}
 * （Go 里它们是<b>同一个包级</b>的函数，所以 article sink 直接复用了 like sink 的定义）。
 *
 * <p>⚠️ attribute 的<b>名字与取值</b>属于可观察行为（Jaeger 里的 span 结构）：
 * <ul>
 *   <li>{@code messaging.system} = {@code kafka}；</li>
 *   <li>{@code messaging.destination} —— ⚠️ <b>传的不是 topic 名</b>，
 *       而是 Go 源码里写死的一对逻辑名：用户路 {@code user_like_sink}、
 *       文章路 {@code article_like_sink}；</li>
 *   <li>{@code messaging.operation} = {@code consume} 或 {@code flush}；</li>
 *   <li>consume 带 {@code messaging.message.key}，flush 带 {@code batch.size}（int）。</li>
 * </ul>
 */
final class TaskTraceAttributes {

    private TaskTraceAttributes() {
        // 纯静态工具，禁止实例化
    }

    /** Go {@code taskMessageAttrs}：消费一条消息时的 span 属性。 */
    static Map<String, Object> message(String destination, String key) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("messaging.system", "kafka");
        attrs.put("messaging.destination", destination);
        attrs.put("messaging.operation", "consume");
        attrs.put("messaging.message.key", key);
        return attrs;
    }

    /** Go {@code taskFlushAttrs}：刷盘一批时的 span 属性。 */
    static Map<String, Object> flush(String destination, int batchSize) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("messaging.system", "kafka");
        attrs.put("messaging.destination", destination);
        attrs.put("messaging.operation", "flush");
        attrs.put("batch.size", batchSize);
        return attrs;
    }
}