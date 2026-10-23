package com.rag.like.like.rpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.like.like.rpc.mapper.LikeConsumeInboxMapper;
import com.rag.like.like.rpc.mapper.LikeDomainFactOutboxMapper;
import com.rag.like.like.rpc.mapper.LikeFactStreamMapper;
import com.rag.like.like.rpc.mapper.LikeOutboxEventMapper;
import com.rag.like.like.rpc.mapper.LikeRecordMapper;
import com.rag.like.like.rpc.mq.LikeEventContracts;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 装配证据 —— <b>断言的是「机制真的接上了」，不是「Bean 存在」</b>。
 *
 * <h2>为什么本类必须存在</h2>
 * <p>2026-10-23 的组件级审查（{@code docs/DRIFT/like/rpc.md}）实测出：
 * <b>like-rpc 在 Boot 4 下根本起不来</b>。本轮逐层剥出<b>四条</b>独立的启动阻塞：
 * <ol>
 *   <li>缺 {@code spring-boot-kafka} ⇒ {@code NoSuchBeanDefinitionException: KafkaTemplate}；</li>
 *   <li>缺 Jackson 2 的 {@code ObjectMapper} Bean（Boot 4 的 Jackson 自动配置只产 Jackson 3）；</li>
 *   <li>缺 {@code MybatisPlusOrderingBridgeAutoConfiguration}
 *       ⇒ MP 的 {@code @AutoConfigureAfter} 引用 Boot 3 的旧包名而静默悬空
 *       ⇒ 不产出 {@code SqlSessionFactory}；</li>
 *   <li>（hot-rpc 同款）{@code TaskExecutor} 注入歧义 / factory↔consumer 循环依赖。</li>
 * </ol>
 *
 * <p>⚠️ <b>在此之前 like-rpc 一个 {@code @SpringBootTest} 都没有</b> ——
 * 全部测试都是纯 Mockito 单测，<b>从不装配 Spring 上下文</b>，
 * 所以「组件起不来」这件事<b>没有任何测试能发现</b>，测试全绿。
 * 这正是 AGENTS.md §3.3 纪律的教科书案例：
 * <b>纯 Mockito mock 数据层的单测天然测不到装配层问题</b>。
 *
 * <h2>为什么必须接真 PG</h2>
 * <p>like-rpc 有 {@code @MapperScan}，MapperFactoryBean <b>要求</b> {@code sqlSessionFactory}，
 * 没有 DataSource 就没有它 ⇒ 「不接库」这个看似保守的选择，
 * <b>恰恰让本类要验证的东西无法验证</b>（同 article-rpc 第一版的教训）。
 * 且本模块用 Flyway，接真 PG 才能顺带验迁移是否真的建了表。
 */
@SpringBootTest(
        classes = LikeRpcApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "grpc.server.port=0",
                // 绕过 Nacos：devh 的 @GrpcClient 在 Bean 初始化期就建 channel（不是懒建），
                // 测试环境没有 Nacos ⇒ discovery:// 解析失败 ⇒ 上下文起不来。
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                // like-rpc 只有一个 client：message-rpc（@GrpcClient("message-rpc")）。
                "grpc.client.message-rpc.address=static://127.0.0.1:1",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN",
                // 绕过 Kafka：consumer 会持续重试连接，但不影响上下文启动与本类断言。
                "spring.kafka.bootstrap-servers=127.0.0.1:1",
                // 绕过 Redis：Lettuce 连接懒建，不真正连。
                "spring.data.redis.host=127.0.0.1",
                "spring.data.redis.port=1"
        })
@Testcontainers
@org.testcontainers.junit.jupiter.EnabledIfDockerAvailable
class LikeRpcSpringContextWiringTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("like_rpc_wiring_it")
                    .withUsername("rag")
                    .withPassword("rag");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private ApplicationContext context;
    @Autowired private ObjectMapper objectMapper;

    // ==================================================================
    // 1. Jackson 2 的 ObjectMapper Bean
    // ==================================================================

    @Test
    @DisplayName("🔴 Jackson 2 的 ObjectMapper Bean 存在（Boot 4 拆模块后缺它上下文起不来）")
    void jackson2ObjectMapperBeanExists() {
        ObjectMapper mapper = context.getBean(ObjectMapper.class);
        assertNotNull(mapper, "Jackson 2 的 ObjectMapper 缺失 ⇒ 6 处构造注入失败 ⇒ 上下文起不来");
    }

    @Test
    @DisplayName("🔴 未知字段被忽略（Go 的 encoding/json 行为）")
    void unknownFieldsAreIgnoredLikeGoEncodingJson() {
        // Go 出处：service/like/rpc/internal/mq/internal/mqs/likemq.go:86
        //   if err := json.Unmarshal([]byte(val), &msg); err != nil { … }
        // Go 的 encoding/json 默认忽略未知字段 ⇒ 多余字段不影响消费；
        // Jackson 2 默认抛 UnrecognizedPropertyException ⇒ Java 侧会 drop 消息 ⇒ 方向相反的漂移。
        String payload = """
                {"msg_id":"m-1","user_id":7,"target_type":"article","target_id":"t-1",
                 "author_id":9,"state":1,"extra_field":"Go 会忽略"}
                """;

        LikeEventContracts.KafkaLikeMsg msg = assertDoesNotThrow(
                () -> objectMapper.readValue(payload, LikeEventContracts.KafkaLikeMsg.class),
                "带未知字段的消息解析失败 ⇒ Java 侧会丢弃它，而 Go 侧照常消费 ⇒ 方向相反的漂移");

        assertEquals("m-1", msg.msgId);
        assertEquals(7L, msg.userId);
        assertEquals("t-1", msg.targetId);
    }

    @Test
    @DisplayName("🔴 number → string 字段报错（Go 的 encoding/json 行为，不是静默强转）")
    void numberIntoStringFieldFailsLikeGo() {
        // Go 出处：service/like/rpc/internal/types/types.go:4
        //   MsgID string `json:"msg_id"`   ← string 字段
        // Go 的 encoding/json 对 number → string 是报错：
        //   json: cannot unmarshal number into Go struct field ... of type string
        // ⚠️ Jackson 2 默认会把 123 静默转成 "123" ⇒ 必须靠 CoercionConfig 关掉。
        assertThrows(com.fasterxml.jackson.databind.exc.InvalidFormatException.class,
                () -> objectMapper.readValue("{\"msg_id\":123}", LikeEventContracts.KafkaLikeMsg.class),
                "数字塞进 string 字段竟然没报错 ⇒ 与 Go 的 encoding/json 相反");
    }

    // ==================================================================
    // 2. Kafka 装配
    // ==================================================================

    @Test
    @DisplayName("🔴 KafkaTemplate 存在（缺 spring-boot-kafka ⇒ 整个模板都没有 ⇒ 组件起不来）")
    void kafkaTemplateExists() {
        assertNotNull(context.getBean(KafkaTemplate.class),
                "KafkaTemplate 缺失 ⇒ spring-boot-kafka 没生效 ⇒ 上下文起不来");
    }

    @Test
    @DisplayName("🔴 @KafkaListener 真的进了容器的 registry（不是「listener 方法存在」）")
    void listenerIsActuallyRegisteredInRegistry() {
        KafkaListenerEndpointRegistry registry = context.getBean(KafkaListenerEndpointRegistry.class);
        assertNotNull(registry, "KafkaListenerEndpointRegistry 缺失 ⇒ 没有 listener 真正注册");

        var containerIds = registry.getListenerContainerIds();
        assertFalse(containerIds.isEmpty(),
                "registry 里没有任何 listener 容器 ⇒ @KafkaListener 没有真正装配，"
                        + "消息不会被消费（而「listener 方法存在」的断言照样是绿的）");

        // like-rpc 的 @KafkaListener 写了显式 id="like-update-consumer" ⇒ 容器 id 应当就是它。
        assertTrue(containerIds.contains("like-update-consumer"),
                "registry 的容器 id = " + containerIds
                        + "，不含显式 id 'like-update-consumer' ⇒ LikeUpdateKafkaListener 没真正接上");
    }

    // ==================================================================
    // 3. MyBatis-Plus 装配（排序桥接）
    // ==================================================================

    @Test
    @DisplayName("🔴 SqlSessionFactory 存在（缺 MybatisPlusOrderingBridgeAutoConfiguration ⇒ MP 自动配置不生效）")
    void sqlSessionFactoryExists() {
        assertNotNull(context.getBean(SqlSessionFactory.class),
                "SqlSessionFactory 缺失 ⇒ MybatisPlusOrderingBridgeAutoConfiguration 没生效，"
                        + "Spring Boot 4 下 MP 会先于 DataSource 求值导致自动配置整体不生效");
    }

    @Test
    @DisplayName("@MapperScan 生效：5 个 Mapper Bean 全部可注入")
    void allMappersAreRegistered() {
        assertNotNull(context.getBean(LikeRecordMapper.class), "LikeRecordMapper 未注册 ⇒ @MapperScan 缺失");
        assertNotNull(context.getBean(LikeConsumeInboxMapper.class));
        assertNotNull(context.getBean(LikeDomainFactOutboxMapper.class));
        assertNotNull(context.getBean(LikeFactStreamMapper.class));
        assertNotNull(context.getBean(LikeOutboxEventMapper.class));
    }
}
