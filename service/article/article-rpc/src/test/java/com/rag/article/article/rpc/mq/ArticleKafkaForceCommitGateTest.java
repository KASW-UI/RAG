package com.rag.article.article.rpc.mq;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import com.rag.article.article.rpc.ArticleRpcApplication;
import com.rag.article.article.rpc.support.RealContainerHostPort;
import com.rag.article.article.rpc.support.RealGrpcPort;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.backoff.FixedBackOff;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 🔴🔴 <b>真 Kafka</b> 上的「失败位点处置」守门 —— 关闭 {@code docs/DRIFT/article/rpc.md}
 * 的 <b>U-3</b>（{@code seekAfterError=true} + {@code FixedBackOff(0,0)} 在真 Kafka 上到底是
 * 「丢弃」还是「进程内无限重投」）、<b>U-4</b>（S-3 的依据是否成立）与 <b>U-9</b>（8 handler 并发下的
 * offset 提交/跳过语义）的<b>唯一</b>手段。
 *
 * <h2>为什么必须是「真 Kafka」</h2>
 * <p>在此之前，本组件的 MQ 失败语义<b>全部只有源码级证据</b>：
 * <ul>
 *   <li>U-3 只引了 spring-kafka 4.0.7 的 {@code SeekUtils.java:107-135}（静态读，没跑过）；</li>
 *   <li>U-4 更是连<b>依据本身</b>都被台账标成「已证伪」（见下面的「U-4 的依据」一节）；</li>
 *   <li>U-9 的「顺序错乱窗口」两侧<b>都没有活体对拍</b>。</li>
 * </ul>
 * ⇒ 而这一层恰恰是「读着像对、跑着不对」的重灾区：<b>属性值对</b>（{@code isSeekAfterError()==true}）
 * <b>推不出行为对</b>（位点到底推没推进、消息到底会不会被重投）。
 *
 * <h2>被测对象是<b>生产装配本身</b>（不是复制品）</h2>
 * <ul>
 *   <li>错误处理器：<b>生产</b>的 {@code ArticleRpcApplication#articleNoCommitErrorHandler()}；</li>
 *   <li>容器工厂：<b>生产</b>的 {@code articleKafkaListenerContainerFactory}
 *       （含 {@code concurrency=8} 与 {@code AckMode.RECORD}）；</li>
 *   <li>ConsumerFactory：<b>生产</b> {@code application.yml} 绑出来的那一个；</li>
 *   <li>只有 <b>listener 本身</b>是探针（生产的 {@code ArticleReviewConsumer} 需要真 PG + 真 MinIO，
 *       而本用例要测的是<b>错误处理器</b>的位点语义，不是消费者的业务分支）。</li>
 * </ul>
 *
 * <h2>Go 出处（<b>逐条指到消费点</b>，不是声明点）</h2>
 * <pre>
 * 声明点  go-queue@v1.2.2/kq/config.go:24   ForceCommit bool `json:",default=true"`
 * 声明点  go-queue@v1.2.2/kq/config.go:19   Processors int `json:",default=8"`
 * 消费点  go-queue@v1.2.2/kq/queue.go:230   if err := q.consumeOne(ctx, string(msg.Key), string(msg.Value)); err != nil {
 * 消费点  go-queue@v1.2.2/kq/queue.go:231-233      if q.errorHandler != nil { q.errorHandler(ctx, msg, err) }
 * 消费点  go-queue@v1.2.2/kq/queue.go:235-237      if !q.c.ForceCommit { continue }      ← ForceCommit=true ⇒ 不 continue
 * 消费点  go-queue@v1.2.2/kq/queue.go:240      if err := q.consumer.CommitMessages(ctx, msg); err != nil {
 * ⇒ 失败消息**照样提交自己的位点** ⇒ 被丢弃；且 Go 侧**没有重试循环** ⇒ 不会进程内重投。
 * 消费点  go-queue@v1.2.2/kq/queue.go:219-246  8 个 processor goroutine 从**同一个 channel** 拉消息
 *                                          ⇒ 同一分区的消息可能落在**不同** goroutine ⇒ 提交**不保证连续**
 * 合并点  kafka-go@v0.4.47/reader.go:171-185  offsetStash.merge: 同一分区多次提交取 **max**
 *                                          ⇒ 提交位点**单调不回退**（即使乱序提交）⇒ 与 RAG 侧同构
 * 拉取点  go-queue@v1.2.2/kq/queue.go:248-259  startProducers: c.Consumers 个 goroutine 调 FetchMessage
 *                                          ⇒ article.yaml 配的是 **Consumers: 1** ⇒ 1 个拉取者
 * </pre>
 * ⚠️ {@code article-rpc/etc/article.yaml} <b>没有</b>配 {@code ForceCommit} / {@code Processors} /
 * {@code CommitInOrder} ⇒ 生效值 = 默认的 {@code true} / {@code 8} / {@code false}
 * （AGENTS.md §3.3.1：默认行为是否生效必须读到**消费该 tag 的那段控制流**）。
 *
 * <h2>U-4 的依据（2026-10-29 <b>真 Kafka 实测订正</b>：台账的「证伪」<b>结论对、理由错</b>）</h2>
 * <p>台账 §6-b / §9 U-4 写「{@code ArticleRpcApplication.java:143-154} 引用的
 * {@code commit(consumer, container, offset+1)} 那段代码<b>不在本模块的执行路径上</b>
 * ⇒ 只在 {@code commitRecovered=true} 时执行」。<b>实测复核：结论成立，但给出的理由只覆盖了其中一半</b>。
 * <p>spring-kafka 4.0.7 里有<b>两个不同</b>的 {@code commit(offset+1)} 落点：
 * <ul>
 *   <li><b>①</b> {@code SeekUtils.java:229-233} 的那个 —— 由 {@code commitRecovered} 把门，
 *       <b>且</b>要求 {@code AckMode.MANUAL_IMMEDIATE}；本模块是 {@code RECORD} 且从未
 *       {@code setCommitRecovered(true)} ⇒ <b>这条路径不走</b>（台账给出的理由，对这一条成立）；</li>
 *   <li><b>②</b> {@code FailedBatchProcessor.java:255-266} 的那个 —— 由 {@code isSeekAfterError()} 把门，
 *       <b>不需要</b> {@code commitRecovered}、<b>不需要</b> {@code MANUAL_IMMEDIATE}
 *       ⇒ <b>台账没提这一条</b>。但它<b>同样不走</b>，理由是<b>另一个</b>：</li>
 * </ul>
 * <p><b>② 为什么不走（这一条是本类的实测结论）</b>：
 * <pre>
 * FailedBatchProcessor.java:256   if (!remaining.isEmpty()) {   ← 守卫
 * FailedRecordTracker.java:102    this.noRetries = backOff.start().nextBackOff() == BackOffExecution.STOP;
 *                                 ⇒ FixedBackOff(0,0) ⇒ noRetries = true
 * FailedRecordTracker.java:174-177  if (this.noRetries) { attemptRecovery(...); return true; }
 *                                 ⇒ 失败记录**第一次就判为 recovered** ⇒ 被移出 remaining
 * ⇒ remaining 为空 ⇒ :256 的守卫为 false ⇒ ② 里的 commit(offset+1) **不执行**
 * </pre>
 * ⇒ <b>真正的提交路径是「容器侧的 RECORD ack」</b>（与 {@code isSeekAfterError()} <b>无关</b>）：
 * <pre>
 * KafkaMessageListenerContainer.java:2897  commitOffsetsIfNeededAfterHandlingError(cRecord);
 * KafkaMessageListenerContainer.java:2925  private void commitOffsetsIfNeededAfterHandlingError(...)
 * KafkaMessageListenerContainer.java:2936      ackCurrent(cRecord, this.isManualAck);
 * KafkaMessageListenerContainer.java:3196      if (this.isRecordAck &amp;&amp; this.producer == null) {
 * KafkaMessageListenerContainer.java:3197          Map&lt;TopicPartition, OffsetAndMetadata&gt; offsetsToCommit = buildSingleCommits(cRecord);
 * KafkaMessageListenerContainer.java:3199          commitOffsets(offsetsToCommit);
 * ⇒ 提交的正是**这条失败记录自己的 offset+1**（与 Go kq/queue.go:240 同构）
 * </pre>
 *
 * <h3>🔴 实测：{@code setSeekAfterError(true)} <b>不是承重项</b>（MUT-M2）</h3>
 * <p>把它改成 {@code false} ⇒ <b>行为完全不变</b>：失败消息仍只投递 1 次、位点仍提交为
 * 「它自己的 offset+1」（两条 U-3/U-4 的<b>行为</b>断言<b>全绿</b>）⇒ 只有那条
 * <b>装配面</b>断言（{@code isSeekAfterError()==true}）转红。
 * ⇒ ⇒ 与 {@code ArticleRpcApplication#articleNoCommitErrorHandler()} 的 javadoc 一致：
 * <b>承重的是 {@code FixedBackOff(0L, 0L)}（MUT-M1：改成 (0,9) ⇒ 失败消息被重投 <b>9</b> 次）</b>
 * <br>（{@code FixedBackOff(0L, 9L)} ⇒ {@code maxAttempts=9} ⇒ 该条消息共投递 <b>10</b> 次
 * ⇒ 断言 {@code assertEquals(before + 1, …)} 实际观测到 {@code before + 10} ⇒ <b>多出 9 次</b>；
 * ⚠️ 绝对基数随当轮 warmup 投递数浮动（实测见过 13 与 38 两个基数）⇒ <b>「多出 9 次」才是稳定量</b>）。
 */
@Testcontainers
@SpringBootTest(
        classes = {ArticleRpcApplication.class, ArticleKafkaForceCommitGateTest.ProbeConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN",
                // 绕过 devh @GrpcClient 的建 channel 期（两个下游都用死地址，探针不碰它们）
                "grpc.client.security-rpc.address=static://127.0.0.1:1",
                "grpc.client.message-rpc.address=static://127.0.0.1:1",
        })
@org.testcontainers.junit.jupiter.EnabledIfDockerAvailable
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ArticleKafkaForceCommitGateTest {

    /** 探针 topic：每轮唯一 ⇒ 不与别的会话/别的域抢分区。 */
    static final String PROBE_TOPIC = "article-force-commit-probe-" + UUID.randomUUID();

    /** 探针消费组：与生产组（{@code dataclean} / {@code article-sync-result}）隔离。 */
    static final String PROBE_GROUP = "article-force-commit-probe-group-" + UUID.randomUUID();

    /** 探针 topic 的分区数 = 生产 handler 并发度（8）⇒ 让并发模型可观测。 */
    static final int PROBE_PARTITIONS = 8;

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("article_force_commit_it")
                    .withUsername("rag")
                    .withPassword("rag")
                    .withCreateContainerCmdModifier(
                            RealContainerHostPort.bindPg(RealContainerHostPort.KAFKA_FORCE_COMMIT_GATE));

    /**
     * 🔴 真 Kafka。
     *
     * <p>⚠️ 固定用 {@code apache/kafka:3.7.2}：本机 Docker Hub 直连不通，
     * 而该镜像<b>本地已有</b>（Testcontainers 默认拉取策略是「本地有就不拉」）。
     *
     * <p>⚠️ <b>不钉宿主口</b>：{@code RealContainerHostPortTest} 的钉口守门只覆盖
     * {@code PostgreSQLContainer} / {@code GenericContainer}（{@code CONTAINER_DECL} 正则），
     * 而 Kafka 的宿主口只被本类自己用（{@code getBootstrapServers()} 永远回读真实映射口）
     * ⇒ ⇒ <b>不存在 PF-ARTICLE-RPC-016 那种「回话的不是我的容器」的风险</b>。
     */
    @Container
    @SuppressWarnings("resource")
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka:3.7.2"));

    @DynamicPropertySource
    static void infra(DynamicPropertyRegistry r) {
        r.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("grpc.server.port", RealGrpcPort::pick);
        // ⚠️ 四个 Kafka 通道的 brokers 必须与全局 bootstrap-servers **同值**
        //    （ArticleKafkaChannelValidator 发现分叉会**让启动失败**）。
        r.add("article-rpc.kq-pusher-conf.brokers", KAFKA::getBootstrapServers);
        r.add("article-rpc.kq-consumer-conf.brokers", KAFKA::getBootstrapServers);
        r.add("article-rpc.article-sync-pusher-conf.brokers", KAFKA::getBootstrapServers);
        r.add("article-rpc.article-sync-result-consumer-conf.brokers", KAFKA::getBootstrapServers);
        // 探针自己的 topic / group（动态值 ⇒ 只能走 Environment，不能进注解常量）
        r.add("article-rpc.kafka-probe.topic", () -> PROBE_TOPIC);
        r.add("article-rpc.kafka-probe.group", () -> PROBE_GROUP);
    }

    @Autowired private ProbeListener probe;

    /** 生产的容器工厂 Bean（探针 listener 也用它 ⇒ 探针与生产同一条错误契约）。 */
    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("articleKafkaListenerContainerFactory")
    private ConcurrentKafkaListenerContainerFactory<?, ?> productionFactory;

    private static Admin admin;
    private static Producer<String, String> producer;

    @BeforeAll
    static void setUpClients() throws Exception {
        String bootstrap = KAFKA.getBootstrapServers();
        admin = Admin.create(Map.of("bootstrap.servers", bootstrap));
        producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class));
        // ⚠️ 显式建 topic：不依赖 broker 的 auto-create（不同 broker 的开关不一样）
        try {
            admin.createTopics(List.of(new NewTopic(PROBE_TOPIC, PROBE_PARTITIONS, (short) 1)))
                    .all().get(30, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            // topic 已存在（重跑同一 JVM）⇒ 不致命
            if (!"TopicExistsException".equals(e.getClass().getSimpleName())
                    && !(e.getCause() != null
                            && "TopicExistsException".equals(e.getCause().getClass().getSimpleName()))) {
                throw e;
            }
        }
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

    // ==================================================================
    // 1. U-3 ①：失败消息**不被进程内重投**（Go 侧没有重试循环）
    // ==================================================================

    @Test
    @Order(2)
    @DisplayName("🔴 U-3：失败消息只被投递 1 次（Go kq/queue.go:230-242 无重试循环 ⇒ 不进程内重投）")
    void failedMessageIsNotRedeliveredInProcess() throws Exception {
        warmUp();

        int before = probe.deliveries.get();
        publish(0, "boom-no-redelivery");
        awaitCondition(() -> probe.deliveries.get() >= before + 1, "失败消息根本没被投递");

        // 给 error handler 充分时间做「重试 / seek / 再投递」
        Thread.sleep(5_000L);
        assertEquals(before + 1, probe.deliveries.get(),
                "失败消息被重投了 ⇒ 进程内无限重试（Go 侧 kq/queue.go:230-242 消费失败后立刻取下一条，"
                        + "既不重试也不 seek）");
    }

    // ==================================================================
    // 2. U-3 ② / U-4：失败消息的**位点**必须像 Go 的 ForceCommit=true 那样被提交
    // ==================================================================

    @Test
    @Order(1)
    @DisplayName("🔴 U-3/U-4：失败消息的位点被提交（Go kq/queue.go:240 CommitMessages ⇒ 消息被丢弃）")
    void failedMessageOffsetIsCommittedLikeGoForceCommit() throws Exception {
        warmUp();

        long offsetBefore = committedOffset(0);
        assertTrue(offsetBefore >= 0,
                "warmup 之后仍读不到已提交位点（offsetBefore=" + offsetBefore + "）⇒ 本用例无法判定");

        // ⚠️ 必须用**相对**计数：绝对值 `>= 1` 会被 warmup 那一次投递直接满足 ⇒ 假绿。
        int deliveredBefore = probe.deliveries.get();
        publish(0, "boom-offset-committed");
        awaitCondition(() -> probe.deliveries.get() > deliveredBefore, "失败消息根本没被投递");

        // ⚠️ 精确到「这条失败消息自己的 offset」：Go 侧 kq/queue.go:240 提交的是
        //    **失败消息自己的 offset+1**（CommitMessages(ctx, msg)），不是「下一条成功消息的位点」。
        long boomOffset = probe.deliveriesLog.stream()
                .filter(d -> "boom-offset-committed".equals(d.payload()))
                .findFirst().orElseThrow(() -> new IllegalStateException("boom 消息没被消费"))
                .offset();

        // ⚠️ 先等「位点真的推进」，再去断言 —— 否则会撞上中间态（hot-rpc 的同款教训）
        awaitCondition(() -> committedOffset(0) >= boomOffset + 1,
                "失败消息的位点没有推进（boomOffset=" + boomOffset
                        + " committed=" + committedOffset(0) + "）⇒ 消息没被丢弃"
                        + "（Go kq/queue.go:240 会提交失败消息自己的 offset+1）");

        long committed = committedOffset(0);
        assertEquals(boomOffset + 1, committed,
                "失败消息的位点不是「它自己的 offset+1」（boomOffset=" + boomOffset
                        + " committed=" + committed + "）"
                        + " ⇒ 与 Go 的 CommitMessages(ctx, msg)（kq/queue.go:240）不同构");
    }

    // ==================================================================
    // 3. U-9a：offset 提交 / 跳过语义（两侧同构的那一半）
    // ==================================================================

    /**
     * 🔴 <b>U-9a</b>：<b>每分区的提交位点 = 该分区「最大已投递 offset + 1」，且从不回退</b>。
     *
     * <h2>为什么断言的是「位点」而不是「线程」</h2>
     * <p>⚠️ <b>本用例的第一版断言过「同一分区的消息永远由同一个消费线程处理」，已实测证伪并改写</b>：
     * 真 Kafka 上消费组在<b>启动期会连做两次 rebalance</b>（第一次：某个 consumer 独占全部 8 个分区；
     * 第二次：按 {@code range} 分配器摊成一人一个）⇒ ⇒ <b>分区会在消费线程之间搬家</b>
     * ⇒ ⇒ 「线程身份」不是 Kafka 的不变量 ⇒ 拿它当断言 = 埋了一颗 flake。
     * <p>⇒ U-9 真正问的是「<b>offset 提交 / 跳过语义</b>」，那才是可断言的。
     *
     * <h2>Go 出处（两侧同构的那一半）</h2>
     * <pre>
     * 消费点  go-queue@v1.2.2/kq/queue.go:240   if err := q.consumer.CommitMessages(ctx, msg); err != nil {
     *        ⇒ Go 提交的是「这条消息自己的位点」（msg.Offset+1），不是「下一条成功消息的位点」。
     * 合并点  kafka-go@v0.4.47/reader.go:171-185  offsetStash.merge:
     *            if offset, ok := offsetsByPartition[c.partition]; !ok || c.offset > offset {
     *                offsetsByPartition[c.partition] = c.offset
     *            }
     *        ⇒ 同一分区多次提交取 **max** ⇒ 提交位点 **单调不回退**（即使 8 个 goroutine 乱序提交）。
     * </pre>
     * ⇒ 与 Go 同构 ⇒ 断言写的就是「每分区提交位点 = 最大已投递 offset + 1」＋「从不回退」。
     *
     * <h2>另一条断言（分区内首次投递按 offset 递增）</h2>
     * <p>这条锁的是 <b>RAG 侧的运行面事实</b>（单分区单线程 ⇒ 串行处理），
     * <b>不是</b>「与 Go 一致」—— Go 侧 8 个 processor 抢同一个 channel（{@code kq/queue.go:219-246}），
     * 同一分区的消息<b>可能</b>被并发处理 ⇒ 这是 <b>RAG 更严</b>的一侧 ⇒ 差异归 U-9b（见台账），不在本用例断言。
     */
    @Test
    @Order(3)
    @DisplayName("🔴 U-9a：每分区提交位点 = 最大已投递 offset + 1 且从不回退"
            + "（Go kq/queue.go:240 + kafka-go reader.go:171-185 取 max ⇒ 两侧同构）")
    void committedOffsetPerPartitionIsMaxDeliveredPlusOneAndNeverRegresses() throws Exception {
        warmUp();

        int deliveredBefore = probe.deliveries.get();
        // 每个分区投 3 条 ⇒ 24 条 ⇒ 8 个分区 × 3（并发可观测）
        for (int p = 0; p < PROBE_PARTITIONS; p++) {
            for (int i = 0; i < 3; i++) {
                publish(p, "u9a-p" + p + "-i" + i);
            }
        }

        // ⚠️ 边等边采样提交位点 ⇒ 后面断言「从不回退」有数据可查
        List<Long> committedSamples = new CopyOnWriteArrayList<>();
        awaitCondition(() -> {
            committedSamples.add(committedOffset(0));
            return probe.deliveries.get() >= deliveredBefore + PROBE_PARTITIONS * 3;
        }, "24 条探针消息没被全部消费（实际新增 "
                + (probe.deliveries.get() - deliveredBefore) + " 条）");

        // ① 分区内「首次投递」的 offset 必须递增
        //    （rebalance 会让已投递未提交的消息被**重投** ⇒ 按 offset 去重后再看顺序）
        Map<Integer, List<Long>> firstSeen = new LinkedHashMap<>();
        for (ProbeListener.Delivery d : probe.deliveriesLog) {
            List<Long> offs = firstSeen.computeIfAbsent(d.partition(), k -> new ArrayList<>());
            if (!offs.contains(d.offset())) {
                offs.add(d.offset());
            }
        }
        assertEquals(PROBE_PARTITIONS, firstSeen.size(),
                "不是每个分区都被消费到 ⇒ 并发模型观测不完整");
        for (Map.Entry<Integer, List<Long>> e : firstSeen.entrySet()) {
            List<Long> offs = e.getValue();
            for (int i = 1; i < offs.size(); i++) {
                assertTrue(offs.get(i) > offs.get(i - 1),
                        "分区 " + e.getKey() + " 的消息未按 offset 递增投递（" + offs + "）"
                                + " ⇒ 分区内不是串行处理（RAG 侧事实，见 U-9b 的模型差异说明）");
            }
        }

        // ② 每个分区的提交位点 == 该分区最大已投递 offset + 1
        for (Map.Entry<Integer, List<Long>> e : firstSeen.entrySet()) {
            int p = e.getKey();
            long maxDelivered = e.getValue().get(e.getValue().size() - 1);
            long expected = maxDelivered + 1;
            awaitCondition(() -> committedOffset(p) >= expected,
                    "分区 " + p + " 的提交位点没有推进到 " + expected
                            + "（当前 " + committedOffset(p) + "，最大已投递 " + maxDelivered + "）");
            assertEquals(expected, committedOffset(p),
                    "分区 " + p + " 的提交位点不是「最大已投递 offset + 1」"
                            + "（expected=" + expected + " actual=" + committedOffset(p) + "）"
                            + " ⇒ 与 Go 的「提交该消息自己的位点」（kq/queue.go:240）不同构");
        }

        // ③ 提交位点从不回退（Go 侧靠 kafka-go 取 max 保证 ⇒ 两侧同构）
        for (int i = 1; i < committedSamples.size(); i++) {
            assertTrue(committedSamples.get(i) >= committedSamples.get(i - 1),
                    "分区 0 的提交位点回退了（" + committedSamples + "）"
                            + " ⇒ 与 Go 侧 kafka-go 取 max（reader.go:171-185）不同构");
        }

        // ④ 并发确实生效：≥2 个不同的消费线程处理过消息
        //    （Go kq/config.go:19 Processors default=8 / 消费点 kq/queue.go:220）
        Set<String> threads = new HashSet<>();
        for (ProbeListener.Delivery d : probe.deliveriesLog) {
            threads.add(d.thread());
        }
        assertTrue(threads.size() >= 2,
                "只有 " + threads.size() + " 个消费线程处理过所有消息 ⇒ concurrency=8 没生效"
                        + "（Go kq/config.go:19 Processors default=8，消费点 kq/queue.go:220）");
    }

    // ==================================================================
    // 4. 装配层：容器上的错误处理器真的是生产的那个、且零重试
    // ==================================================================

    @Test
    @Order(4)
    @DisplayName("🔴 容器上的 error handler 就是生产的零重试 DefaultErrorHandler（noRetries=true / maxAttempts=0）")
    void containerErrorHandlerIsProductionZeroRetry() {
        // ⚠️ 生产工厂 Bean（名字来自 ArticleRpcApplication#articleKafkaListenerContainerFactory）
        //    —— 探针 listener 用的也是它（containerFactory = "articleKafkaListenerContainerFactory"）
        //    ⇒ 这里读到的就是**探针容器真正会用的那个实例**。
        ConcurrentKafkaListenerContainerFactory<?, ?> production = productionFactory;
        assertNotNull(production, "拿不到生产的容器工厂 ⇒ 探针用的不是生产装配");

        CommonErrorHandler handler =
                (CommonErrorHandler) ReflectionTestUtils.getField(production, "commonErrorHandler");
        assertNotNull(handler, "工厂上没有 commonErrorHandler ⇒ 容器会用默认的 FixedBackOff(0,9) 重试");
        assertTrue(handler instanceof DefaultErrorHandler,
                "容器上的 error handler 不是 DefaultErrorHandler（实际 " + handler + "）");

        DefaultErrorHandler defaultErrorHandler = (DefaultErrorHandler) handler;
        // ⚠️ 读的是**消费点**上的字段（FailedRecordTracker.noRetries），
        //    由 backOff.start().nextBackOff() == STOP 决定（spring-kafka 4.0.7 FailedRecordTracker 构造器）
        Object tracker = ReflectionTestUtils.getField(defaultErrorHandler, "failureTracker");
        assertNotNull(tracker, "读不到 failureTracker ⇒ Spring Kafka 内部结构变了，断言需重做");
        assertEquals(Boolean.TRUE, ReflectionTestUtils.getField(tracker, "noRetries"),
                "noRetries != true ⇒ 该 handler 会重试（Spring Kafka 默认 FixedBackOff(0,9)）"
                        + "，坏消息会把分区卡住 9 轮，而 Go 侧不存在重试（kq/queue.go:230-242）");

        Object backOff = ReflectionTestUtils.getField(tracker, "backOff");
        assertTrue(backOff instanceof FixedBackOff, "backOff 不是 FixedBackOff（实际 " + backOff + "）");
        assertEquals(0L, ((FixedBackOff) backOff).getMaxAttempts(), "maxAttempts != 0 ⇒ 不是零重试");

        assertTrue(defaultErrorHandler.isSeekAfterError(),
                "isSeekAfterError != true ⇒ 与 Go 的 ForceCommit 默认值 true 相反"
                        + "（kq/config.go:24 声明点 / kq/queue.go:235 消费点）");
    }

    // ==================================================================
    // helpers
    // ==================================================================

    /** 投一条「好」消息，把容器等进消费组并拿到分区 0（否则「没消费」会被误判成 drop 语义不对）。 */
    private void warmUp() throws Exception {
        int before = probe.deliveries.get();
        publish(0, "warmup");
        awaitCondition(() -> probe.deliveries.get() > before,
                "探针容器没加入消费组（探针 topic 上没有消息被消费）");
        // ⚠️⚠️ **必须等 warmup 的位点真的提交**，否则后面读到的 offsetBefore 可能是 -1
        //    （还没提交）⇒ 断言 `committed >= offsetBefore + 1` 会退化成 `committed >= 0`
        //    ⇒ **恒真 ⇒ 假绿**（这正是本仓 §3.3.3 反复踩的形态）。
        long warmupOffset = probe.deliveriesLog.stream()
                .filter(d -> "warmup".equals(d.payload()))
                .findFirst().orElseThrow(() -> new IllegalStateException("warmup 没被消费"))
                .offset();
        awaitCondition(() -> committedOffset(0) >= warmupOffset + 1,
                "warmup 的位点没有提交（committed=" + committedOffset(0) + "）");
    }

    private void publish(int partition, String payload) throws Exception {
        producer.send(new ProducerRecord<>(PROBE_TOPIC, partition, payload, payload))
                .get(20, java.util.concurrent.TimeUnit.SECONDS);
    }

    /** 该消费组在指定分区上**已提交**的位点（没有 ⇒ -1）。 */
    private long committedOffset(int partition) {
        try {
            Map<TopicPartition, OffsetAndMetadata> offsets =
                    admin.listConsumerGroupOffsets(PROBE_GROUP).partitionsToOffsetAndMetadata().get();
            OffsetAndMetadata om = offsets.get(new TopicPartition(PROBE_TOPIC, partition));
            return om == null ? -1L : om.offset();
        } catch (Exception e) {
            throw new IllegalStateException("读消费组已提交位点失败", e);
        }
    }

    private static void awaitCondition(BooleanSupplier cond, String message) throws Exception {
        long deadline = System.currentTimeMillis() + 60_000L;
        while (System.currentTimeMillis() < deadline) {
            if (cond.getAsBoolean()) {
                return;
            }
            Thread.sleep(200L);
        }
        fail(message);
    }

    // ==================================================================
    // 探针 listener（唯一的「非生产」部分：它只负责让错误处理器有事可做）
    // ==================================================================

    @TestConfiguration
    static class ProbeConfig {
        @Bean
        ProbeListener probeListener() {
            return new ProbeListener();
        }
    }

    static class ProbeListener {

        record Delivery(int partition, long offset, String payload, String thread) { }

        final AtomicInteger deliveries = new AtomicInteger();
        final List<Delivery> deliveriesLog = new CopyOnWriteArrayList<>();

        @KafkaListener(
                id = "article-force-commit-probe",
                topics = "${article-rpc.kafka-probe.topic}",
                groupId = "${article-rpc.kafka-probe.group}",
                containerFactory = "articleKafkaListenerContainerFactory")
        public void onMessage(ConsumerRecord<String, String> record, String value) {
            deliveries.incrementAndGet();
            deliveriesLog.add(new Delivery(record.partition(), record.offset(), value,
                    Thread.currentThread().getName()));
            if (value != null && value.startsWith("boom")) {
                throw new IllegalStateException("probe failure (故意): " + value);
            }
        }
    }
}
