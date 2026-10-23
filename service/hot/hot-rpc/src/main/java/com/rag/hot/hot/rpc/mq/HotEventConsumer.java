package com.rag.hot.hot.rpc.mq;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.common.observability.MqConsumer;
import com.rag.hot.hot.rpc.config.HotRpcProperties;
import com.rag.hot.hot.rpc.domain.HeavyKeeper;
import com.rag.hot.hot.rpc.metrics.HotRpcMetrics;
import io.opentelemetry.context.Context;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 消息入口：校验 → 计数 → registry.add → 触发快照 —— 对应 Go {@code ConsumeClaim}
 * （{@code hot_handler.go:101-111}）+ {@code consumeMessage}（{@code :142-183}）。
 *
 * <h2>🚨 本类的第一语义：<b>永不外抛</b></h2>
 * Go 的消费循环无条件执行 {@code sess.MarkMessage(msg, "")}（{@code :108}），
 * <b>即使 {@code consumeMessage} 返回错误</b>。⇒ 反序列化失败 / 缺 {@code article_id} /
 * 未知或零权重 type 这三类坏消息<b>被静默丢弃、offset 照常推进、永不重试</b>。
 *
 * <p>Java 侧默认行为完全相反：Spring Kafka 的 {@code DefaultErrorHandler} 会 seek + 重试，
 * 一条坏消息能<b>卡死整个分区</b>。因此本类：
 * <ol>
 *   <li>listener 方法<b>绝不向容器抛异常</b>（所有失败在内部吞掉并记 ERROR）⇒ 位点照常提交；</li>
 *   <li>payload 以 {@code String} 接收、手动反序列化（而不是交给 Kafka 反序列化器）
 *       ⇒ 让「解析失败」也落进上面那个吞异常的边界内，与 Go 的
 *       {@code json.Unmarshal} 在 {@code consumeMessage} 内部发生的位置一致；</li>
 *   <li>容器层的 {@code DefaultErrorHandler} 配成零重试（见 {@code HotKafkaConfig}）作为兜底。</li>
 * </ol>
 *
 * <h2>触发快照的条件</h2>
 * Go 是<b>全局计数器</b> {@code h.counter.Add(1) % h.syncEvery == 0}（{@code :179}），
 * <b>不是每分区计数</b> ⇒ 分区越多，单分区的快照越稀疏。照抄。
 */
@Component
public class HotEventConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(HotEventConsumer.class);

    /** Go {@code hot_handler.go:105} 的 tracer 名。 */
    private static final String TRACER_NAME = "hot-rpc";

    /** Go {@code hot_handler.go:105} 的 span 名。 */
    private static final String SPAN_NAME = "HotHandler.Consume";

    /** Go {@code hot_handler.go:146}：单条消费超过 100ms 打慢日志（与全局 SlowThreshold 无关）。 */
    private static final long SLOW_LOG_THRESHOLD_MILLIS = 100L;

    private final ObjectMapper objectMapper;
    private final HotRpcProperties props;
    private final HotRpcMetrics metrics;
    private final HotPartitionSketchRegistry registry;
    private final HotRedisSnapshotStore store;
    private final TaskExecutor taskExecutor;

    /** 对应 Go {@code h.counter atomic.Int64}。 */
    private final AtomicLong counter = new AtomicLong();

    /** 对应 Go {@code hot.go:47-50}：按 {@code Name} 建 map，{@code Code} 从不读取。 */
    private final Map<String, Integer> weights = new ConcurrentHashMap<>();

    public HotEventConsumer(ObjectMapper objectMapper,
                            HotRpcProperties props,
                            HotRpcMetrics metrics,
                            HotPartitionSketchRegistry registry,
                            HotRedisSnapshotStore store,
                            @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor) {
        this.objectMapper = objectMapper;
        this.props = props;
        this.metrics = metrics;
        this.registry = registry;
        this.store = store;
        this.taskExecutor = taskExecutor;
        for (HotRpcProperties.Weight w : props.getInteraction().getWeights()) {
            if (w.getName() != null) {
                weights.put(w.getName(), w.getWeight());
            }
        }
    }

    /**
     * Kafka 入口。<b>本方法不向外抛任何异常。</b>
     *
     * <p>payload 用 {@code String} 接收：让 JSON 解析失败发生在方法内部（被吞），
     * 而不是发生在 Kafka 反序列化器里（会触发容器错误处理与重试）。
     */
    @KafkaListener(topics = "${hot-rpc.kafka.topic}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "hotKafkaListenerContainerFactory")
    public void onMessage(ConsumerRecord<String, String> record) {
        final int partitionId = record.partition();
        final String key = record.key();
        final String value = record.value();

        Map<String, Object> attrs = Map.of(
                "messaging.system", "kafka",
                "messaging.destination", record.topic(),
                "messaging.operation", "consume",
                "messaging.kafka.partition", partitionId,
                "messaging.message.key", key == null ? "" : key);

        // Go：`_ = observability.TraceConsumer(...)` —— 返回的 error 被丢弃，位点照常 MarkMessage。
        // ⚠️ 关键：error 必须**交给** traceConsumer 才能被写进 span（markSystemError）。
        // 若在 lambda 内就吞掉，traceConsumer 收到的是 nil ⇒ span 会被标成成功，与 Go 不一致。
        Throwable err = MqConsumer.traceConsumer(Context.current(), TRACER_NAME, SPAN_NAME, attrs,
                ctx -> {
                    try {
                        return consumeMessage(partitionId, value);
                    } catch (RuntimeException e) {
                        // 非预期异常也降级成返回值，绝不让它逃出 listener（否则触发容器重试）
                        return e;
                    }
                });

        if (err != null) {
            LOG.debug("[HotHandler] consume dropped (offset still committed): {}", err.getMessage());
        }
    }

    /**
     * 对应 Go {@code consumeMessage}（{@code hot_handler.go:142-183}）。
     *
     * <p>校验顺序<b>逐条对照 Go</b>：
     * <ol>
     *   <li>反序列化失败 ⇒ 返回 error（仍被 mark ⇒ drop）</li>
     *   <li>{@code ArticleID == ""} ⇒ 返回 error（drop）</li>
     *   <li>{@code weights[type]} 缺失或 {@code <= 0} ⇒ 返回 error（drop）</li>
     *   <li><b>校验全过之后</b>才 {@code eventConsumeTotal.Inc(type)}（Go {@code :168}）</li>
     *   <li>分区不在 {@code hkMap} ⇒ <b>静默返回</b>：事件被接受、offset 推进、
     *       <b>仍然计数</b>（指标在第 4 步已加），只是不入 sketch</li>
     *   <li>{@code hk.Add(id, weight)}</li>
     *   <li>全局计数器 {@code % syncEvery == 0} ⇒ 异步触发一次快照</li>
     * </ol>
     *
     * @return 等价 Go 的 {@code error} 返回值：三类坏消息返回非 {@code null}，
     *         分区未持有与正常路径返回 {@code null}。
     *         ⚠️ 该返回值<b>必须</b>交给 {@code traceConsumer} 才能让 span 记录到 error，
     *         之后才在 {@link #onMessage} 层丢弃（Go 的 {@code _ =}）。
     */
    Throwable consumeMessage(int partitionId, String value) {
        long startNanos = System.nanoTime();

        ArticleHotEvent event;
        try {
            event = objectMapper.readValue(value, ArticleHotEvent.class);
        } catch (Exception e) {
            LOG.error("[HotHandler] unmarshal event failed: {}, value: {}", e.getMessage(), value);
            return e;
        }

        if (event.articleId() == null || event.articleId().isEmpty()) {
            LOG.error("[HotHandler] invalid event: {}", value);
            return new IllegalArgumentException("invalid hot event: missing article id");
        }

        Integer weight = event.type() == null ? null : weights.get(event.type());
        if (weight == null || weight <= 0) {
            LOG.error("[HotHandler] unknown or zero-weight type: {}, event: {}",
                    event.type(), value);
            return new IllegalArgumentException(
                    "unknown or zero-weight hot event type: " + event.type());
        }

        // ★ 必须在 hkMap 查找之前：Go 的顺序是 Inc(:168) → 查 hkMap(:171)
        metrics.eventConsumed(event.type());

        HeavyKeeper hk = registry.heavyKeeperFor(partitionId);
        if (hk == null) {
            // Go :173-175 静默 return nil：已计数、已 mark、只是不入 sketch
            return null;
        }

        hk.add(event.articleId(), weight);

        long syncEvery = props.getInteraction().getSyncEvery();
        if (syncEvery > 0 && counter.incrementAndGet() % syncEvery == 0) {
            // Go :180 `go h.syncToRedis(context.Background(), partitionID)`
            taskExecutor.execute(() -> store.flush(partitionId, hk));
        }

        long costMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        if (costMillis > SLOW_LOG_THRESHOLD_MILLIS) {
            LOG.warn("[HotHandler] slow consume, cost: {}ms, value: {}", costMillis, value);
        }
        return null;
    }

    /**
     * 分区获得：建 sketch + 从 Redis 回灌。对应 Go {@code OnPartitionsAssigned}。
     *
     * <p>回灌在锁外进行（见 {@link HotPartitionSketchRegistry} 的偏差论证）。
     */
    public void onPartitionsAssigned(Collection<Integer> partitions) {
        Map<Integer, HeavyKeeper> created = registry.assign(partitions);
        created.forEach(store::loadInto);
    }

    /**
     * 分区撤销：先落盘、再从注册表摘除。对应 Go {@code OnPartitionsRevoked}。
     *
     * <p>顺序与 Go 的意图一致（{@code syncToRedis} 后 {@code delete}），但落盘在锁外完成，
     * 以避开 Go 侧「持写锁再取读锁」的必然死锁。
     */
    public void onPartitionsRevoked(Collection<Integer> partitions) {
        Map<Integer, HeavyKeeper> detached = registry.detach(partitions);
        detached.forEach(store::flush);
    }

    /** 当前已处理的全局事件计数（测试观测用；Go 侧 {@code h.counter} 无对外接口）。 */
    long processedCount() {
        return counter.get();
    }

    /** 事件类型 → 权重（测试观测用）。 */
    Map<String, Integer> weights() {
        return Map.copyOf(weights);
    }
}