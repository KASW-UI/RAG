package com.rag.hot.hot.rpc.config;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import com.rag.hot.hot.rpc.mq.HotEventConsumer;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.kafka.autoconfigure.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka 监听容器的<b>两项容器级语义</b>：分区分配回调 与 错误处理策略。
 *
 * <p>⚠️ <b>本类承载的是 L3「MQ 失败处理」语义，不是配置糖</b>。
 * 缺任何一项都会让 Go 的行为发生实质改变。
 *
 * <h2>① 分区分配回调：Go 的 Setup / Cleanup</h2>
 * Go 的 {@code HotHandler} 实现了 sarama 的 {@code ConsumerGroupHandler}：
 * {@code Setup → OnPartitionsAssigned}、{@code Cleanup → OnPartitionsRevoked}
 * （{@code hot_handler.go:83-99}），且<b>只处理与自身 topic 相同的 claim</b>。
 * Spring Kafka 的 {@code @KafkaListener} 没有直接的 assign/revoke 钩子，
 * 须通过容器的 {@link ConsumerRebalanceListener} 提供 —— 这是 Spring 官方的对应机制。
 *
 * <h2>② 零重试错误处理：drop 语义的最后一道防线</h2>
 * Go 的消费循环<b>无条件</b> {@code sess.MarkMessage(msg, "")}（{@code :108}），
 * 即使 {@code consumeMessage} 返回错误 ⇒ 坏消息静默丢弃、位点照常推进、<b>永不重试</b>。
 * 而 Spring Kafka 的默认 {@code DefaultErrorHandler} 是 {@code FixedBackOff(0L, 9)}
 * ——<b>重试 9 次后</b>才跳过。这条链路上任何一条坏消息都会<b>卡住分区 9 轮</b>。
 *
 * <p>这里显式改成 {@code FixedBackOff(0L, 0L)}（零间隔零重试，失败即跳过并提交位点）
 * ⇒ 与 Go 的无条件 MarkMessage 对齐。
 * 注意这只是<b>兜底</b>：{@code HotEventConsumer} 本身已保证 listener 永不外抛，
 * 所以正常情况下根本走不到这里。
 */
@Configuration
public class HotKafkaConfig {

    private static final Logger LOG = LoggerFactory.getLogger(HotKafkaConfig.class);

    /**
     * 专用容器工厂：注入零重试错误处理 + 分区重平衡回调。
     *
     * <p>用具名 bean 而非覆盖 Boot 的默认 {@code kafkaListenerContainerFactory}，
     * 避免影响上下文里其它可能的 listener；由 {@code @KafkaListener(containerFactory=...)} 显式引用。
     *
     * <h2>⚠️ 为什么 {@code consumer} 参数上必须有 {@link Lazy}</h2>
     * <p>本方法第 4 个参数注入 {@link HotEventConsumer}，是为了给 rebalance 回调一个接收方
     * （{@code consumer.onPartitionsAssigned / onPartitionsRevoked}）。
     * 而 {@code HotEventConsumer} 上的
     * {@code @KafkaListener(containerFactory = "hotKafkaListenerContainerFactory")}
     * 又让 Spring 在创建该 Bean 时去解析<b>本工厂</b> ⇒
     * <b>工厂 → consumer → 工厂</b> 的循环依赖。
     *
     * <p>不加 {@code @Lazy} 时的实测报错：
     * <pre>
     * BeanCreationException: hotKafkaListenerContainerFactory
     *   └─ parameter 3 → hotEventConsumer
     *        └─ BeanCurrentlyInCreationException: Requested bean is currently in creation
     * </pre>
     *
     * <p><b>{@code @Lazy} 在这里安全，且不改变任何可观察行为</b>，依据是<b>时序</b>：
     * {@link ConsumerRebalanceListener} 的两个回调<b>只在 consumer 容器已经启动、
     * 并加入消费组拿到分区之后</b>才可能被触发；
     * 那时 {@code HotEventConsumer} 必然已经创建完毕 ⇒ 延迟解析拿到的是真实 Bean，
     * 既不会 NPE，也不会改变回调触发时机与次数。
     * 反之，若在 Bean 创建期就解析 consumer，则必然循环、上下文根本起不来。
     *
     * <p>📌 <b>不要改成「让 factory 不依赖 consumer」</b>（例如把分区回调整体搬走）：
     * 那会改变 {@code HotEventConsumer} 的职责边界，而它正是 Go 侧
     * {@code HotHandler}（{@code hot_handler.go:83-99}）的对位物 ——
     * 分区回调留在 consumer 上，与 Go 的归属一致。
     *
     * <p>⚠️ 修这一条时同时发现：本模块此前<b>没有任何 {@code @SpringBootTest}</b>，
     * 所以这条循环依赖<b>连同 Jackson 缺口、TaskExecutor 歧义</b>一起，
     * 在全绿的测试套件下<b>完全无人察觉</b>。
     * 锁死用例：{@code HotRpcSpringContextWiringTest}。
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> hotKafkaListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object> consumerFactory,
            HotRpcProperties props,
            @Lazy HotEventConsumer consumer) {
        // Boot 的 ConcurrentKafkaListenerContainerFactoryConfigurer.configure 签名就是
        // <Object,Object>，这也是 Spring Boot 默认工厂的形状；listener 方法签名里的
        // String 由 MessagingMessageListenerAdapter 按方法参数类型处理。
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure(factory, consumerFactory);

        // ② 零重试：对齐 Go 的无条件 MarkMessage（drop 而非 retry）
        factory.setCommonErrorHandler(zeroRetryErrorHandler());

        // ① 分区回调：对齐 Go 的 Setup / Cleanup
        factory.getContainerProperties().setConsumerRebalanceListener(rebalanceListener(props, consumer));

        return factory;
    }

    /**
     * {@link DefaultErrorHandler} + {@code FixedBackOff(0, 0)}：一旦进入错误处理，立即放弃该记录。
     *
     * <p>{@code setAckAfterHandle(true)} 保证「处理完（含放弃）」后提交位点，
     * 使坏消息的位点<b>照常推进</b>——这正是 Go 的无条件 MarkMessage。
     */
    static DefaultErrorHandler zeroRetryErrorHandler() {
        DefaultErrorHandler handler = new DefaultErrorHandler(new FixedBackOff(0L, 0L));
        handler.setAckAfterHandle(true);
        return handler;
    }

    /**
     * 对应 Go {@code Setup} / {@code Cleanup}：只对本服务的 topic 生效
     * （Go {@code hot_handler.go:84-88,93-97} 的 {@code if topic == h.topic}）。
     */
    static ConsumerRebalanceListener rebalanceListener(HotRpcProperties props, HotEventConsumer consumer) {
        String topic = props.getKafka().getTopic();
        return new ConsumerRebalanceListener() {

            @Override
            public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
                List<Integer> pids = ownedPartitions(partitions, topic);
                if (!pids.isEmpty()) {
                    LOG.info("[Hot] partitions revoked: {}", pids);
                    consumer.onPartitionsRevoked(pids);
                }
            }

            @Override
            public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                List<Integer> pids = ownedPartitions(partitions, topic);
                if (!pids.isEmpty()) {
                    LOG.info("[Hot] partitions assigned: {}", pids);
                    consumer.onPartitionsAssigned(pids);
                }
            }
        };
    }

    private static List<Integer> ownedPartitions(Collection<TopicPartition> partitions, String topic) {
        return partitions.stream()
                .filter(tp -> tp.topic().equals(topic))
                .map(TopicPartition::partition)
                .collect(Collectors.toCollection(java.util.ArrayList::new));
    }
}