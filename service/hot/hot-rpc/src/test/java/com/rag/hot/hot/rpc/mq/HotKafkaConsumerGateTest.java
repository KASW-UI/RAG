package com.rag.hot.hot.rpc.mq;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import com.rag.hot.hot.rpc.HotRpcApplication;
import com.rag.hot.hot.rpc.domain.HeavyKeeper;
import com.rag.hot.hot.rpc.support.RealContainerHostPort;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.RangeAssignor;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.backoff.FixedBackOff;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 🔴 <b>真 Kafka</b> 上的消费侧守门测试：drop 契约 / 位点推进 / 零重试 handler / RoundRobin 实际分配。
 *
 * <h2>为什么必须是「真 Kafka 且默认跑」</h2>
 * <p>在此之前，本组件的 MQ 语义全部只有<b>静态源码证据</b>
 * （{@code docs/DRIFT/hot/rpc.md} 的 UNR-4 / UNR-5）。而这一层恰恰是
 * 「读着像对、跑着不对」的重灾区：
 * <ul>
 *   <li><b>分配策略</b>：{@code spring.kafka.consumer.properties.partition.assignment.strategy}
 *       写在 yaml 里 ≠ 真的落到了 {@code ConsumerConfig}
 *       —— Boot 4 的 {@code KafkaProperties.buildConsumerProperties()} 是否仍消费
 *       {@code spring.kafka.consumer.properties.*} 必须实测（AGENTS.md §3.3.1
 *       「配置里没写 ≠ 没开，也 ≠ 没做」）。</li>
 *   <li><b>零重试</b>：{@code DefaultErrorHandler(FixedBackOff(0,0))} 写在代码里
 *       ≠ 容器真的用了它；更不等于「坏消息不会卡住分区」。</li>
 *   <li><b>drop 契约</b>：Go 侧无条件 {@code sess.MarkMessage(msg, "")}（{@code hot_handler.go:108}）
 *       ⇒ 坏消息静默丢弃、位点照常推进。这一层<b>只有真 broker 的位点推进能证明</b>。</li>
 * </ul>
 * ⇒ mock / 手写假 broker / 纯 Mockito 单测都替代不了
 * （与本仓 comment-rpc 的真 PG 守门测试、hot-rpc 的真 Redis 守门测试同口径）。
 *
 * <h2>被测对象是<b>生产装配本身</b>（不是复制品）</h2>
 * <ul>
 *   <li>容器：<b>生产</b>的 {@code hotKafkaListenerContainerFactory}
 *       （含零重试 error handler 与分区回调）；</li>
 *   <li>listener：<b>生产</b>的 {@link HotEventConsumer}（不是另写一个等价物）；</li>
 *   <li>consumer 配置：<b>生产</b> {@code application.yml} 绑出来的 {@link ConsumerFactory}
 *       （不是测试里另写一份 properties）；</li>
 *   <li>topic 与分区数：<b>生产</b> {@code hot-rpc.kafka.topic} 与
 *       {@code hot-rpc.interaction.partition-count}（不是测试里写死 3）。</li>
 * </ul>
 *
 * <h2>Go 出处</h2>
 * <ul>
 *   <li>{@code service/hot/rpc/hot.go:57} —— {@code saramaConf.Consumer.Group.Rebalance.Strategy
 *       = sarama.NewBalanceStrategyRoundRobin()}</li>
 *   <li>{@code service/hot/rpc/hot.go:58} —— {@code saramaConf.Consumer.Offsets.Initial
 *       = sarama.OffsetNewest}</li>
 *   <li>{@code service/hot/rpc/internal/mqs/hot_handler.go:108} —— 无条件
 *       {@code sess.MarkMessage(msg, "")}（drop 契约）</li>
 *   <li>{@code service/hot/rpc/internal/mqs/hot_handler.go:152-166} —— 三类坏消息的判定</li>
 *   <li>{@code service/hot/rpc/internal/mqs/hot_handler.go:168} —— 校验通过后才
 *       {@code eventConsumeTotal.Inc(event.Type)}</li>
 *   <li>{@code service/hot/rpc/internal/mqs/hot_handler.go:83-99} —— Setup/Cleanup 分区回调</li>
 * </ul>
 */
@Testcontainers
@SpringBootTest(
        classes = {HotRpcApplication.class, HotKafkaConsumerGateTest.ProbeConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "grpc.server.port=0",
                // 绕过 Nacos：devh 的 @GrpcClient 在 Bean 初始化期就建 channel（不是懒建）
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN",
                // 探针 topic / 消费组：只服务于本测试的「零重试」用例
                "hot.kafka.probe.topic=hot-zero-retry-probe"
        })
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HotKafkaConsumerGateTest {

    /** 探针消费组：与生产组 {@code article-hot-group} 隔离，互不抢分区。 */
    static final String PROBE_GROUP = "hot-zero-retry-probe-group";

    /** 生产消费组（Go {@code KqConsumerConf.Group}）。 */
    @Value("${spring.kafka.consumer.group-id}")
    String consumerGroup;

    /** 生产 topic（Go {@code KqConsumerConf.Topic}）。 */
    @Value("${hot-rpc.kafka.topic}")
    String hotTopic;

    /** 生产分区数（Go {@code Interaction.PartitionCount}）。 */
    @Value("${hot-rpc.interaction.partition-count}")
    int partitionCount;

    @Value("${hot.kafka.probe.topic}")
    String probeTopic;

    /**
     * 真 Kafka。
     *
     * <p>⚠️ 固定用 {@code apache/kafka:3.7.2}：本机 Docker Hub 直连不通，
     * 而该镜像<b>本地已有</b>（Testcontainers 默认拉取策略是「本地有就不拉」）。
     */
    @Container
    @SuppressWarnings("resource")
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka:3.7.2"));

    /** 真 Redis：生产 listener 的分区回调会回灌 Redis（{@code store.loadInto}）。 */
    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379)
            .withCreateContainerCmdModifier(RealContainerHostPort.bindRedis(RealContainerHostPort.HOT_KAFKA_CONSUMER_GATE));

    @DynamicPropertySource
    static void infra(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired private ApplicationContext context;
    @Autowired private ConsumerFactory<Object, Object> consumerFactory;
    @Autowired private HotPartitionSketchRegistry registry;
    @Autowired private MeterRegistry meterRegistry;

    private static Admin admin;
    private static Producer<String, String> producer;

    @BeforeAll
    static void setUpClients() {
        String bootstrap = KAFKA.getBootstrapServers();
        admin = Admin.create(Map.of("bootstrap.servers", bootstrap));
        producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class));
    }

    @AfterAll
    static void tearDownClients() {
        if (producer != null) {
            producer.close(Duration.ofSeconds(5));
        }
        if (admin != null) {
            admin.close(Duration.ofSeconds(5));
        }
    }

    /**
     * 每个用例前先确认「生产容器真的加入消费组并拿到了全部分区」。
     *
     * <p>⚠️ 必须等：生产配置是 {@code auto-offset-reset: latest}
     * （Go 侧 {@code sarama.OffsetNewest}，{@code hot.go:58}）
     * ⇒ 容器加入之前的消息<b>不会被回放</b>。不等就会把「消息没被消费」
     * 误判成「drop 语义不对」。
     *
     * <p>顺带证明了 {@code ConsumerRebalanceListener} → {@code HotEventConsumer.onPartitionsAssigned}
     * → {@code registry.assign} 这条链<b>真的跑通了</b>
     * （registry 的分区数就是「回调收到几个分区」的直接观测）。
     */
    @BeforeEach
    void awaitContainerJoinedGroup() {
        awaitCondition(() -> registry.size() == partitionCount,
                "生产 listener 容器没拿到全部分区（实际 " + registry.size() + "/" + partitionCount + "）");
    }

    // ==================================================================
    // 1. drop 契约：坏消息静默丢弃、位点照常推进、后续好消息仍被消费
    // ==================================================================

    @Test
    @Order(1)
    @DisplayName("🔴 三类坏消息被静默丢弃、位点照常推进、后面的好消息仍被消费（Go 的无条件 MarkMessage）")
    void badMessagesAreDroppedAndOffsetsStillAdvance() throws Exception {
        final int partition = 0;
        final long offsetBefore = committedOffset(hotTopic, partition);
        final double likeBefore = counterCount("like");
        final String goodId = "good-" + UUID.randomUUID();

        // ① 反序列化失败（Go hot_handler.go:152 json.Unmarshal 失败）
        publish(hotTopic, partition, "not-json-at-all");
        // ② 缺 article_id（Go hot_handler.go:157 ArticleID == ""）
        publish(hotTopic, partition, "{\"type\":\"like\"}");
        // ③ 未知 / 零权重 type（Go hot_handler.go:162-166）
        publish(hotTopic, partition, "{\"article_id\":\"x\",\"type\":\"no-such-type\"}");
        // ④ 正常消息：必须被消费（说明前三条没有卡住分区）
        publish(hotTopic, partition, "{\"article_id\":\"" + goodId + "\",\"type\":\"like\"}");

        // ⚠️ 顺序很重要：**先等位点到位，再断言指标终值**。
        //    4 条消息是逐条被消费的，若在「位点还没走完」时就去断言指标，
        //    会撞上「第 2 条已计、第 4 条还没计」的**中间态**而误判为通过
        //    （这个坑是变异验证 M3 实测抓出来的，不是推演出来的）。
        //    位点到达 offsetBefore+4 ⇒ 4 条都已处理完 ⇒ 指标此刻才是终值。
        //
        // Go 出处：hot_handler.go:108 —— 无条件 MarkMessage ⇒ 位点照常推进
        awaitCondition(() -> committedOffset(hotTopic, partition) >= offsetBefore + 4,
                "位点没推进到 4 条之后（坏消息被重试/卡住了？）");

        // Go 出处：hot_handler.go:168 —— 校验通过之后才 Inc ⇒ 三条坏消息都不进指标
        assertEquals(likeBefore + 1, counterCount("like"),
                "消费指标不对 ⇒ 有坏消息被计数了"
                        + "（Go 侧 hot_handler.go:168 是**先校验后 Inc**，三类坏消息都不该进指标）");

        // ⚠️ 未知 type 必须**永不出现在指标里**（HotRpcMetrics 类注释：
        //    「未知或零权重的 type 会被 drop，永不出现在指标里」）
        assertEquals(0d, counterCount("no-such-type"),
                "未知 type 竟然进了消费指标 ⇒ 校验顺序反了（Go 侧 hot_handler.go:168 是先校验后 Inc）");

        // Go 出处：hot_handler.go:177 hk.Add(articleID, weight) ⇒ 好消息进了 sketch
        HeavyKeeper hk = registry.heavyKeeperFor(partition);
        assertNotNull(hk, "分区 " + partition + " 没有 sketch ⇒ 分区回调没跑");
        assertTrue(hk.topK().stream().anyMatch(item -> goodId.equals(item.key())),
                "正常消息没进 sketch（topK = " + hk.topK() + "）");
    }

    // ==================================================================
    // 2. 零重试 handler 的实际效果
    // ==================================================================

    @Test
    @Order(2)
    @DisplayName("🔴 零重试 handler：抛异常的记录只投递 1 次、且不卡住后续消息")
    void zeroRetryHandlerDoesNotRedeliver() throws Exception {
        ZeroRetryProbeListener probe = context.getBean(ZeroRetryProbeListener.class);
        final int before = probe.deliveries.get();

        publish(probeTopic, 0, "{\"article_id\":\"boom-1\",\"type\":\"like\"}");
        awaitCondition(() -> probe.deliveries.get() == before + 1, "第一条失败消息根本没被投递");
        // 给 error handler 充分时间做「重试 / seek / 再投递」
        Thread.sleep(3_000L);
        assertEquals(before + 1, probe.deliveries.get(),
                "失败消息被重投了 ⇒ 零重试没生效（Go 侧是无条件 MarkMessage，不存在重试）");

        // 分区不被卡住：第二条消息同样能被投递，且同样只投 1 次
        publish(probeTopic, 0, "{\"article_id\":\"boom-2\",\"type\":\"like\"}");
        awaitCondition(() -> probe.deliveries.get() == before + 2,
                "第二条消息没被投递 ⇒ 分区被坏消息卡住了");
        Thread.sleep(2_000L);
        assertEquals(before + 2, probe.deliveries.get(), "第二条消息被重投 ⇒ 零重试没生效");
    }

    // ==================================================================
    // 3. RoundRobin 实际分配（UNR-4）
    // ==================================================================

    @Test
    @Order(3)
    @DisplayName("🔴 RoundRobinAssignor 真的落到了 consumer（3 分区 2 消费者 ⇒ [0,2]/[1]；Range 会是 [0,1]/[2]）")
    void productionConsumerFactoryReallyUsesRoundRobinAssignor() {
        // 用**生产 ConsumerFactory** 建两个消费者（独立消费组，不抢生产组的分区）。
        // 生产 ConsumerFactory 里的 partition.assignment.strategy 来自
        // application.yml:49 —— 本用例断言的正是「这个配置真的进了 ConsumerConfig」。
        Map<String, Set<Integer>> roundRobin = assignmentOf("rr-probe-group", null);

        // Go 出处：hot.go:57 NewBalanceStrategyRoundRobin()
        //   3 分区 2 消费者 ⇒ RoundRobin 分成 [0,2] / [1]（按分区号轮流派发）
        assertEquals(Set.of(0, 2), roundRobin.get("c0"),
                "第一个消费者拿到的分区不是 [0,2]（实际 " + roundRobin.get("c0") + "）"
                        + " ⇒ RoundRobinAssignor 没落到 consumer，或被改成了别的分配策略");
        assertEquals(Set.of(1), roundRobin.get("c1"),
                "第二个消费者拿到的分区不是 [1]（实际 " + roundRobin.get("c1") + "）");

        // ── 对照组：把策略换成 RangeAssignor，分配必须变成 [0,1]/[2] ──
        // 这一步证明本用例**对分配策略敏感**：若两种策略结果相同，断言就是假绿。
        Map<String, Set<Integer>> range = assignmentOf("rr-range-probe-group",
                RangeAssignor.class.getName());
        assertEquals(Set.of(0, 1), range.get("c0"),
                "RangeAssignor 对照组应得 [0,1]（实际 " + range.get("c0") + "）");
        assertEquals(Set.of(2), range.get("c1"),
                "RangeAssignor 对照组应得 [2]（实际 " + range.get("c1") + "）");
    }

    // ==================================================================
    // 4. 装配层断言：容器上的 error handler 就是零重试的那个
    // ==================================================================

    @Test
    @Order(4)
    @DisplayName("🔴 容器上的 error handler 就是零重试的 DefaultErrorHandler（noRetries=true / maxAttempts=0 / ackAfterHandle=true）")
    void containerErrorHandlerIsZeroRetry() {
        ConcurrentKafkaListenerContainerFactory<?, ?> factory = context.getBean(
                "hotKafkaListenerContainerFactory", ConcurrentKafkaListenerContainerFactory.class);
        // ⚠️ Spring Kafka 4.0.7 的 AbstractKafkaListenerContainerFactory 只暴露 setter，
        //    没有 getter ⇒ 这里读的就是**容器真正会用的那个实例**（工厂私有字段），
        //    比「某个新建的 handler」更接近消费点。
        CommonErrorHandler handler =
                (CommonErrorHandler) ReflectionTestUtils.getField(factory, "commonErrorHandler");

        assertNotNull(handler, "工厂上没有 commonErrorHandler ⇒ 容器会用默认的 FixedBackOff(0,9) 重试");
        assertTrue(handler instanceof DefaultErrorHandler,
                "容器上的 error handler 不是 DefaultErrorHandler（实际 " + handler + "）");

        DefaultErrorHandler defaultErrorHandler = (DefaultErrorHandler) handler;
        assertTrue(defaultErrorHandler.isAckAfterHandle(),
                "ackAfterHandle=false ⇒ 处理完（含放弃）不提交位点，"
                        + "与 Go 的无条件 MarkMessage（hot_handler.go:108）相反");

        // ⚠️ 这里读的是**消费点**上的字段，不是「yaml/代码里写了什么」：
        //   FailedRecordTracker.noRetries 由 backOff.start().nextBackOff() == STOP 决定
        //   （spring-kafka 4.0.7 FailedRecordTracker 构造器字节码：
        //      93: backOff.start() → 100: nextBackOff() → 105: ldc2_w -1L → 112: iconst_1
        //      → 117: putfield noRetries）
        //   也就是说 noRetries=true 才等价于「第一次失败就放弃」。
        Object tracker = ReflectionTestUtils.getField(defaultErrorHandler, "failureTracker");
        assertNotNull(tracker, "读不到 failureTracker ⇒ Spring Kafka 内部结构变了，断言需重做");

        Object noRetries = ReflectionTestUtils.getField(tracker, "noRetries");
        assertEquals(Boolean.TRUE, noRetries,
                "noRetries != true ⇒ 该 handler 会重试（Spring Kafka 默认 FixedBackOff(0,9)）"
                        + "，坏消息会把分区卡住 9 轮，而 Go 侧不存在重试");

        Object backOff = ReflectionTestUtils.getField(tracker, "backOff");
        assertTrue(backOff instanceof FixedBackOff,
                "backOff 不是 FixedBackOff（实际 " + backOff + "）");
        assertEquals(0L, ((FixedBackOff) backOff).getMaxAttempts(),
                "maxAttempts != 0 ⇒ 不是零重试");
    }

    // ==================================================================
    // helpers
    // ==================================================================

    /**
     * 用生产 {@link ConsumerFactory} 建两个消费者，让它们加入同一个消费组，
     * 返回各自的分区集合。
     *
     * @param assignorOverride 非空时覆盖 {@code partition.assignment.strategy}（对照组用）
     */
    private Map<String, Set<Integer>> assignmentOf(String group, String assignorOverride) {
        List<Consumer<Object, Object>> consumers = new ArrayList<>();
        Map<String, Set<Integer>> result = new LinkedHashMap<>();
        try {
            for (int i = 0; i < 2; i++) {
                Properties override = null;
                if (assignorOverride != null) {
                    override = new Properties();
                    override.put(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG, assignorOverride);
                }
                Consumer<Object, Object> consumer = consumerFactory.createConsumer(
                        group, "probe-" + group + "-" + i, null, override);
                consumer.subscribe(List.of(hotTopic));
                consumers.add(consumer);
            }

            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            while (System.nanoTime() < deadline) {
                int total = consumers.stream().mapToInt(c -> c.assignment().size()).sum();
                boolean allAssigned = consumers.stream().allMatch(c -> !c.assignment().isEmpty());
                if (allAssigned && total == partitionCount) {
                    break;
                }
                for (Consumer<Object, Object> consumer : consumers) {
                    consumer.poll(Duration.ofMillis(200));
                }
            }

            for (int i = 0; i < consumers.size(); i++) {
                Set<Integer> partitions = new TreeSet<>();
                for (TopicPartition tp : consumers.get(i).assignment()) {
                    partitions.add(tp.partition());
                }
                result.put("c" + i, partitions);
            }
            return result;
        } finally {
            for (Consumer<Object, Object> consumer : consumers) {
                try {
                    consumer.close(Duration.ofSeconds(5));
                } catch (RuntimeException ignored) {
                    // 关闭失败不影响断言
                }
            }
        }
    }

    private void publish(String topic, int partition, String value) throws Exception {
        producer.send(new ProducerRecord<>(topic, partition, null, value)).get(20, TimeUnit.SECONDS);
    }

    /**
     * 生产消费组在指定分区上的已提交位点（= 下一条要读的位置）。
     *
     * <p>不声明受检异常：调用点在 {@link BooleanSupplier} 里（轮询等待），
     * 受检异常在 lambda 里无法直接抛。
     */
    private long committedOffset(String topic, int partition) {
        try {
            Map<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata> offsets =
                    admin.listConsumerGroupOffsets(consumerGroup)
                            .partitionsToOffsetAndMetadata().get(20, TimeUnit.SECONDS);
            org.apache.kafka.clients.consumer.OffsetAndMetadata offset =
                    offsets.get(new TopicPartition(topic, partition));
            return offset == null ? 0L : offset.offset();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("读已提交位点被中断", e);
        } catch (Exception e) {
            throw new IllegalStateException("读已提交位点失败: " + e.getMessage(), e);
        }
    }

    private double counterCount(String type) {
        io.micrometer.core.instrument.Counter counter = meterRegistry
                .find("article_hot_kafka_consume_total")
                .tag("type", type)
                .counter();
        return counter == null ? 0d : counter.count();
    }

    private static void awaitCondition(BooleanSupplier condition, String failureMessage) {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail(failureMessage + "（等待被中断）");
            }
        }
        fail(failureMessage);
    }

    /**
     * 探针 listener：<b>故意抛异常</b>，用来观察零重试 error handler 的实际行为。
     *
     * <p>⚠️ 它复用<b>生产</b>的 {@code hotKafkaListenerContainerFactory}
     * ⇒ 用的就是生产那份零重试 handler（而不是另造一个）。
     */
    static class ZeroRetryProbeListener {

        final AtomicInteger deliveries = new AtomicInteger();

        @KafkaListener(topics = "${hot.kafka.probe.topic}",
                groupId = PROBE_GROUP,
                containerFactory = "hotKafkaListenerContainerFactory")
        public void onMessage(ConsumerRecord<String, String> record) {
            deliveries.incrementAndGet();
            throw new IllegalStateException("probe: 故意失败，验证零重试 handler 不会重投");
        }
    }

    /**
     * 测试专用配置：
     * <ul>
     *   <li>{@link NewTopic} —— <b>必须在 listener 容器启动前建好</b>。
     *       否则 broker 会按默认分区数（1）自动建 topic，
     *       后面再改分区数就不生效了（且 RoundRobin 的形状会整个变掉）。
     *       <br>时序保证：{@code KafkaAdmin} 是 {@code SmartInitializingSingleton}
     *       ⇒ {@code afterSingletonsInstantiated()} 里建 topic，
     *       而 listener 容器是在 {@code finishRefresh()} 的 lifecycle 阶段才启动
     *       ⇒ <b>topic 一定先于容器存在</b>。</li>
     *   <li>分区数直接取生产配置 {@code hot-rpc.interaction.partition-count}
     *       ⇒ 配置漂移时本测试跟着漂移，不会写出第二份「3」。</li>
     * </ul>
     */
    @TestConfiguration
    static class ProbeConfig {

        @Bean
        NewTopic hotEventsTopic(@Value("${hot-rpc.kafka.topic}") String topic,
                                @Value("${hot-rpc.interaction.partition-count}") int partitions) {
            return new NewTopic(topic, partitions, (short) 1);
        }

        @Bean
        NewTopic probeTopic(@Value("${hot.kafka.probe.topic}") String topic) {
            return new NewTopic(topic, 1, (short) 1);
        }

        @Bean
        ZeroRetryProbeListener zeroRetryProbeListener() {
            return new ZeroRetryProbeListener();
        }
    }
}
