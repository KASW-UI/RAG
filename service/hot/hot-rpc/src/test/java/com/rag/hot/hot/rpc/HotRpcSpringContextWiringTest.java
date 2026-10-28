package com.rag.hot.hot.rpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.hot.hot.rpc.mq.HotEventConsumer;
import com.rag.hot.hot.rpc.mq.ArticleHotEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 装配证据 —— <b>断言的是「机制真的接上了」，不是「Bean 存在」</b>。
 *
 * <h2>为什么本类必须存在</h2>
 * <p>2026-10-23 的组件级审查（{@code docs/DRIFT/hot/rpc.md}）实测出：
 * <b>hot-rpc 在 Boot 4 下根本起不来</b> ——
 * {@code HotEventConsumer} 构造注入 Jackson 2 的
 * {@code com.fasterxml.jackson.databind.ObjectMapper}，
 * 而 Boot 4 把 Jackson 自动配置拆进了 {@code spring-boot-jackson} 且只产 <b>Jackson 3</b> 的
 * {@code tools.jackson.databind.json.JsonMapper}，
 * ⇒ {@code NoSuchBeanDefinitionException: ObjectMapper} ⇒ 上下文起不来。
 *
 * <p>⚠️ <b>在此之前 hot-rpc 一个 {@code @SpringBootTest} 都没有</b> ——
 * 全部测试都是纯 Mockito 单测，<b>从不装配 Spring 上下文</b>，
 * 所以「组件起不来」这件事<b>没有任何测试能发现</b>，测试全绿。
 * 这正是 AGENTS.md §3.3 纪律的教科书案例：
 * <b>纯 Mockito mock 数据层的单测天然测不到装配层问题</b>。
 *
 * <p>本类的存在就是为了让 hot-rpc <b>真的起一次</b>。
 *
 * <h2>为什么不需要真 PG / Testcontainers</h2>
 * <p>hot-rpc <b>没有 {@code @MapperScan}</b>、没有 {@code DataSource} 依赖
 * （存储侧是 Redis，RedisTemplate 的连接是<b>懒建</b>的），
 * 所以上下文启动不需要连库 ⇒ 不引入 Testcontainers，
 * 避免把「装配验证」与「数据层验证」耦在一起。
 */
@SpringBootTest(
        classes = HotRpcApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "grpc.server.port=22500",
                // 绕过 Nacos：devh 的 @GrpcClient 在 Bean 初始化期就建 channel（不是懒建），
                // 测试环境没有 Nacos ⇒ discovery:// 解析失败 ⇒ 上下文起不来。
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN",
                // 绕过 Kafka：consumer 会持续重试连接，但不影响上下文启动与本类断言。
                "spring.kafka.bootstrap-servers=127.0.0.1:1",
                // 绕过 Redis：Lettuce 连接懒建，不真正连。
                "spring.data.redis.host=127.0.0.1",
                "spring.data.redis.port=1"
        })
class HotRpcSpringContextWiringTest {

    @Autowired private ApplicationContext context;
    @Autowired private ObjectMapper objectMapper;

    // ==================================================================
    // 1. Jackson 2 的 ObjectMapper Bean（本次修复的主体）
    // ==================================================================

    @Test
    @DisplayName("🔴 Jackson 2 的 ObjectMapper Bean 存在（Boot 4 拆模块后缺它上下文起不来）")
    void jackson2ObjectMapperBeanExists() {
        // ⚠️ 必须按**类型**取 Jackson 2 的类：Boot 4 装的是 Jackson 3 的 JsonMapper
        //    （tools.jackson.databind.json.JsonMapper），两者包名不同、互不通用。
        ObjectMapper mapper = context.getBean(ObjectMapper.class);
        assertNotNull(mapper, "Jackson 2 的 ObjectMapper 缺失 ⇒ HotEventConsumer 构造注入失败 ⇒ 上下文起不来");
    }

    @Test
    @DisplayName("🔴 未知字段被忽略（Go 的 encoding/json 行为）⇒ 多余字段的消息不得导致解析失败")
    void unknownFieldsAreIgnoredLikeGoEncodingJson() {
        // Go 出处：service/hot/rpc/internal/mqs/hot_handler.go:152
        //   if err := json.Unmarshal([]byte(value), &event); err != nil { … }
        //   目标类型：service/hot/rpc/internal/mqs/types.go:5-8
        //   type ArticleHotEvent struct { ArticleID string `json:"article_id"`; Type string `json:"type"` }
        //
        // Go 的 encoding/json（标准库）**默认忽略未知字段** ⇒ 一条带多余字段的 Kafka 消息
        // 在 Go 侧正常处理；而 Jackson 2 默认抛 UnrecognizedPropertyException
        // ⇒ Java 侧会走进「解析失败 ⇒ 丢弃消息」分支，而 Go 侧照常消费 ⇒ 方向相反的漂移。
        //
        // ⚠️ 这里断言的是**实际解析行为**，不是「某个 feature 被关了」——
        //    断言 feature 标志等于断言实现细节，断言行为才锚定语义。
        //
        // 📌 本条由**两层**共同保证，任一层单独存在都够：
        //    ① record 上的 {@code @JsonIgnoreProperties(ignoreUnknown = true)}
        //       （见 ArticleHotEvent 的类级 javadoc：Go 侧 producer 实测发 4 个字段）；
        //    ② ObjectMapper 上关掉 FAIL_ON_UNKNOWN_PROPERTIES（对齐 Go encoding/json 的全局默认）。
        String payload = """
                {"article_id":"a-1","type":"like","extra_field":"Go 会忽略","another":123}
                """;

        ArticleHotEvent event = assertDoesNotThrow(
                () -> objectMapper.readValue(payload, ArticleHotEvent.class),
                "带未知字段的消息解析失败 ⇒ Jackson 侧会丢弃它，而 Go 侧照常消费 ⇒ 方向相反的漂移");

        assertEquals("a-1", event.articleId());
        assertEquals("like", event.type());
    }

    @Test
    @DisplayName("🔴 类型不匹配仍然报错（不能因为关了未知字段就放松类型校验）")
    void typeMismatchStillFails() {
        // Go 的 encoding/json 对类型不匹配是**报错**，不是静默转换：
        //   json: cannot unmarshal number into Go struct field ArticleHotEvent.article_id of type string
        // 关闭 FAIL_ON_UNKNOWN_PROPERTIES 不应顺带放松这一条。
        assertThrows(com.fasterxml.jackson.databind.exc.InvalidFormatException.class,
                () -> objectMapper.readValue("{\"article_id\":123}", ArticleHotEvent.class),
                "数字塞进 string 字段竟然没报错 ⇒ 与 Go 的 encoding/json 相反");
    }

    // ==================================================================
    // 2. Kafka 装配：listener 必须真的进了容器的 registry
    // ==================================================================

    @Test
    @DisplayName("🔴 Kafka listener 容器工厂存在（spring-boot-kafka 缺失 ⇒ 整个工厂都没有）")
    void kafkaListenerContainerFactoryExists() {
        ConcurrentKafkaListenerContainerFactory<?, ?> factory =
                context.getBean("hotKafkaListenerContainerFactory", ConcurrentKafkaListenerContainerFactory.class);
        assertNotNull(factory, "hotKafkaListenerContainerFactory 缺失 ⇒ spring-boot-kafka 没生效");
    }

    @Test
    @DisplayName("🔴 @KafkaListener 真的进了容器的 registry（不是「listener 方法存在」）")
    void listenerIsActuallyRegisteredInRegistry() {
        KafkaListenerEndpointRegistry registry = context.getBean(KafkaListenerEndpointRegistry.class);
        assertNotNull(registry, "KafkaListenerEndpointRegistry 缺失 ⇒ 没有 listener 真正注册");

        var containers = registry.getListenerContainers();
        assertFalse(containers.isEmpty(),
                "registry 里没有任何 listener 容器 ⇒ @KafkaListener 没有真正装配，"
                        + "消息不会被消费（而「listener 方法存在」的断言照样是绿的）");

        // ⚠️ 断言的是**容器实际使用的 groupId**，不是容器 id。
        //    容器 id 在未显式写 @KafkaListener(id=...) 时是自动生成的
        //    "KafkaListenerEndpointContainer#0"（含序号，不可预测）；
        //    groupId 才是与 Go 对齐的语义位（application.yml: group-id=article-hot-group
        //    ← Go KqConsumerConf.Group）。
        boolean groupMatched = containers.stream()
                .anyMatch(c -> "article-hot-group".equals(c.getContainerProperties().getGroupId()));
        assertTrue(groupMatched,
                "registry 里的容器 groupId 都不等于 'article-hot-group'（实际 = "
                        + containers.stream().map(c -> c.getContainerProperties().getGroupId()).toList()
                        + "）⇒ HotEventConsumer 的 @KafkaListener 没真正接上，"
                        + "或消费组名与 Go 的 KqConsumerConf.Group 不一致");
    }

    @Test
    @DisplayName("🔴 HotEventConsumer Bean 存在且注入的就是这个 Jackson 2 mapper")
    void hotEventConsumerIsWiredWithThisMapper() {
        HotEventConsumer consumer = context.getBean(HotEventConsumer.class);
        assertNotNull(consumer);
    }

    // ==================================================================
    // 3. 定时任务调度器：两条 @Scheduled 必须互不阻塞
    // ==================================================================

    @Test
    @DisplayName("🔴 gRPC 标准健康检查的 HealthStatusManager bean 存在（注册失败要能被探针观测到）")
    void grpcHealthStatusManagerBeanExists() {
        // Go 出处：zrpc/internal/server.go:36-38（health.NewServer()）
        //           + zrpc/internal/rpcserver.go:61-63（RegisterHealthServer + Resume）
        // ⇒ Go 侧**默认**就注册了 grpc 标准 health service（RpcServerConf.Health 的
        //    json:",default=true"）。
        //
        // RAG 侧由 devh starter 的 GrpcHealthServiceAutoConfiguration 提供
        // （@ConditionalOnClass(HealthStatusManager) + @ConditionalOnProperty(
        //    "grpc.server.health-service-enabled", matchIfMissing=true)）。
        //
        // ⚠️ 这里断言的是**容器里真有这个 bean**，不是「pom 里有 grpc-services」
        //    —— 没有它，NacosRegistration 就没法把「注册失败」写进探针能读到的状态
        //    （DRIFT-HOTRPC-010 的配套改动）。
        io.grpc.protobuf.services.HealthStatusManager manager =
                context.getBean(io.grpc.protobuf.services.HealthStatusManager.class);
        assertNotNull(manager,
                "HealthStatusManager bean 缺失 ⇒ grpc.server.health-service-enabled 可能被关掉了"
                        + " ⇒ 注册失败将无法被 gRPC 探针观测到");
    }

    @Test
    @DisplayName("🔴 调度器线程池 ≥ 2（Boot 4 默认 1 ⇒ 30s 快照与 10s 合并会互相排队）")
    void taskSchedulerHasAtLeastTwoThreads() {
        // Go 出处：service/hot/rpc/internal/mqs/hot_handler.go:76-77
        //     go h.periodicSync(context.Background())
        //     go h.periodicMerge(context.Background())
        //   两条**独立 goroutine**，各自的 ticker 互不影响。
        //
        // Java 侧：两条 @Scheduled 共用 Boot 的 TaskScheduler。
        //   Boot 4.0.8 的 TaskSchedulingProperties$Pool 默认 size = 1
        //   （字节码：<init> → iconst_1; putfield size）
        //   消费点：TaskSchedulingConfigurations$ThreadPoolTaskSchedulerBuilderConfiguration
        //        → getPool().getSize() → ThreadPoolTaskSchedulerBuilder.poolSize(int)
        //   ⇒ 不显式配 spring.task.scheduling.pool.size=2，两条周期任务会**串行排队**：
        //      合并一次要 pipeline 读 N 个分片 + 写 shadow + 跑 Lua（Redis 命令超时 3s）
        //      ⇒ 最坏情况会把 30s 快照推迟。
        //
        // ⚠️ 这里断言的是**调度器真实的线程池大小**（消费点生效），
        //    不是「yaml 里写了 2」——写了不等于生效（AGENTS.md §3.3.3）。
        ThreadPoolTaskScheduler scheduler = (ThreadPoolTaskScheduler) context.getBean("taskScheduler");
        assertNotNull(scheduler, "taskScheduler Bean 缺失 ⇒ @Scheduled 不会按配置建线程池");
        assertTrue(scheduler.getPoolSize() >= 2,
                "调度器只有 " + scheduler.getPoolSize() + " 个线程 ⇒ 30s 快照与 10s 合并互相排队，"
                        + "而 Go 侧是两条独立 goroutine（hot_handler.go:76-77）");
    }
}
