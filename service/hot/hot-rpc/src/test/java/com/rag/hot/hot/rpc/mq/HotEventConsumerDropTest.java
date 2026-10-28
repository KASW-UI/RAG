package com.rag.hot.hot.rpc.mq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.hot.hot.rpc.config.HotRpcProperties;
import com.rag.hot.hot.rpc.domain.HeavyKeeper;
import com.rag.hot.hot.rpc.metrics.HotRpcMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;

/**
 * 锁死消费入口的 <b>drop 契约</b>与 <b>指标计数次序</b> —— 对应 Go
 * {@code hot_handler.go:101-111}（无条件 MarkMessage）与 {@code :142-183}（consumeMessage）。
 *
 * <p>测试入口用 {@link #onMessage(ConsumerRecord)}（对外的 listener 方法）而不是直接调
 * {@code consumeMessage}，因为「不抛异常」这个契约是<b>对容器</b>的承诺。
 */
class HotEventConsumerDropTest {

    private static final String TOPIC = "article-hot-events";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MeterRegistry registry;
    private HotRpcProperties props;
    private HotRpcMetrics metrics;
    private HotPartitionSketchRegistry sketches;
    private HotRedisSnapshotStore store;
    private AtomicInteger flushCount;
    private HotEventConsumer consumer;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        props = new HotRpcProperties();
        props.getInteraction().setSyncEvery(100L);
        props.getKafka().setTopic(TOPIC);
        props.getInteraction().setWeights(List.of(
                newWeight(1, "like", 1),
                newWeight(2, "comment", 2),
                newWeight(3, "coin", 3),
                newWeight(4, "share", 4)));
        metrics = new HotRpcMetrics(registry);
        sketches = new HotPartitionSketchRegistry(props);
        // store 只作为「快照触发」的观测点存在，这里用计数器替代，避免为了测 drop 而连真 Redis
        flushCount = new AtomicInteger();
        store = org.mockito.Mockito.mock(HotRedisSnapshotStore.class);
        TaskExecutor sameThread = Runnable::run;
        consumer = new HotEventConsumer(MAPPER, props, metrics, sketches, store, sameThread);
    }

    private static HotRpcProperties.Weight newWeight(int code, String name, int weight) {
        HotRpcProperties.Weight w = new HotRpcProperties.Weight();
        w.setCode(code);
        w.setName(name);
        w.setWeight(weight);
        return w;
    }

    private void send(int partition, String payload) {
        consumer.onMessage(new ConsumerRecord<>(TOPIC, partition, 0L, "key", payload));
    }

    private double consumeTotal(String type) {
        var counter = registry.find("article_hot_kafka_consume_total").tag("type", type).counter();
        return counter == null ? 0.0 : counter.count();
    }

    // ---------------------------------------------------------------- drop 契约

    @Test
    @DisplayName("⚠️ 三类坏消息必须返回非 null error：Go 的 consumeMessage 是 return err")
    void badMessagesReturnErrorForSpanMarking() {
        // Go：unmarshal 失败 / ArticleID=="" / 权重缺失 三者都 `return err`
        // 这些 err 会先进 TraceConsumer 把 span 标成 system error，再被 `_ =` 丢弃。
        // 若 Java 侧在进 span 之前就吞掉，span 会被误标成成功 ⇒ 与 Go 不一致。
        assertThat(consumer.consumeMessage(0, "{not-json")).isNotNull();
        assertThat(consumer.consumeMessage(0, "{\"type\":\"like\"}")).isNotNull();
        assertThat(consumer.consumeMessage(0, "{\"article_id\":\"a1\",\"type\":\"nope\"}"))
                .isNotNull();
    }

    @Test
    @DisplayName("正常路径与「分区未持有」都返回 null error（不标成 system error）")
    void healthyPathsReturnNullError() {
        consumer.onPartitionsAssigned(List.of(0));

        assertThat(consumer.consumeMessage(0, "{\"article_id\":\"a1\",\"type\":\"like\"}")).isNull();
        // 分区未持有：Go :173-175 静默 return nil —— 不是错误
        assertThat(consumer.consumeMessage(9, "{\"article_id\":\"a2\",\"type\":\"like\"}")).isNull();
    }

    @Test
    @DisplayName("坏 JSON：不抛异常、位点照常推进、不计数")
    void malformedJsonIsDroppedSilently() {
        assertThatCode(() -> send(0, "{not-json")).doesNotThrowAnyException();
        assertThat(consumeTotal("like")).isZero();
    }

    @Test
    @DisplayName("article_id 缺失/为空：不抛异常、不计数")
    void emptyArticleIdIsDroppedSilently() {
        assertThatCode(() -> send(0, "{\"type\":\"like\"}")).doesNotThrowAnyException();
        assertThatCode(() -> send(0, "{\"article_id\":\"\",\"type\":\"like\"}"))
                .doesNotThrowAnyException();
        assertThat(consumeTotal("like")).isZero();
    }

    @Test
    @DisplayName("未知 type：不抛异常、不计数（标签取值域 = 配置里 Name 的集合）")
    void unknownTypeIsDroppedSilently() {
        assertThatCode(() -> send(0, "{\"article_id\":\"a1\",\"type\":\"unknown\"}"))
                .doesNotThrowAnyException();
        assertThatCode(() -> send(0, "{\"article_id\":\"a1\",\"type\":\"coin\"}"))
                .doesNotThrowAnyException(); // coin 是已知 type，只是分区未 assign
        assertThat(consumeTotal("unknown")).isZero();
    }

    @Test
    @DisplayName("零权重 type：不抛异常、不计数")
    void zeroWeightTypeIsDroppedSilently() {
        HotRpcProperties.Weight zero = newWeight(9, "zero", 0);
        props.getInteraction().setWeights(List.of(zero));
        HotEventConsumer c = new HotEventConsumer(MAPPER, props, metrics, sketches, store, Runnable::run);

        assertThatCode(() -> c.onMessage(
                new ConsumerRecord<>(TOPIC, 0, 0L, "k", "{\"article_id\":\"a1\",\"type\":\"zero\"}")))
                .doesNotThrowAnyException();
        assertThat(consumeTotal("zero")).isZero();
    }

    // ---------------------------------------------------------------- 指标次序

    @Test
    @DisplayName("⚠️ 分区未 assign 时仍然计数：指标 Inc 早于 hkMap 查找（Go :168 → :171）")
    void metricIsIncrementedEvenWhenPartitionIsNotAssigned() {
        // 没有调用 onPartitionsAssigned ⇒ hkMap 为空
        assertThat(sketches.size()).isZero();

        send(7, "{\"article_id\":\"a1\",\"type\":\"like\"}");

        // Go 的顺序是 eventConsumeTotal.Inc(:168) 在前、hkMap 查找(:171) 在后
        assertThat(consumeTotal("like")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("分区已 assign 且 type 合法：计数 + 入 sketch")
    void validEventCountsAndEntersSketch() {
        consumer.onPartitionsAssigned(List.of(0));
        assertThat(sketches.size()).isEqualTo(1);

        send(0, "{\"article_id\":\"a1\",\"type\":\"coin\"}");

        assertThat(consumeTotal("coin")).isEqualTo(1.0);
        HeavyKeeper hk = sketches.heavyKeeperFor(0);
        assertThat(hk).isNotNull();
        assertThat(hk.topK()).extracting(HeavyKeeper.Item::key).contains("a1");
    }

    @Test
    @DisplayName("生产端多发字段（user_id/timestamp）不影响解析：ignoreUnknown 是硬要求")
    void unknownFieldsAreIgnored() {
        consumer.onPartitionsAssigned(List.of(0));

        // service/hot/test/cmd/producer/producer.go:14-19 实测发送的就是 4 字段
        assertThatCode(() -> send(0,
                "{\"article_id\":\"a1\",\"type\":\"like\",\"user_id\":\"u9\",\"timestamp\":123456}"))
                .doesNotThrowAnyException();

        assertThat(consumeTotal("like")).isEqualTo(1.0);
        assertThat(sketches.heavyKeeperFor(0).topK()).extracting(HeavyKeeper.Item::key).contains("a1");
    }

    // ---------------------------------------------------------------- 权重表

    @Test
    @DisplayName("权重表按 name 建（Code 被丢弃，对齐 Go hot.go:47-50）")
    void weightsAreKeyedByName() {
        assertThat(consumer.weights())
                .containsEntry("like", 1)
                .containsEntry("comment", 2)
                .containsEntry("coin", 3)
                .containsEntry("share", 4);
    }

    // ---------------------------------------------------------------- 分区生命周期

    @Test
    @DisplayName("assign 幂等：重复 assign 不会重复回灌（Go :118-120 的 continue）")
    void assignIsIdempotent() {
        consumer.onPartitionsAssigned(List.of(0));
        consumer.onPartitionsAssigned(List.of(0));
        assertThat(sketches.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("revoke 后分区不再持有")
    void revokeRemovesPartition() {
        consumer.onPartitionsAssigned(List.of(0, 1));
        assertThat(sketches.size()).isEqualTo(2);

        consumer.onPartitionsRevoked(List.of(0));

        assertThat(sketches.size()).isEqualTo(1);
        assertThat(sketches.heavyKeeperFor(0)).isNull();
        assertThat(sketches.heavyKeeperFor(1)).isNotNull();
    }
}