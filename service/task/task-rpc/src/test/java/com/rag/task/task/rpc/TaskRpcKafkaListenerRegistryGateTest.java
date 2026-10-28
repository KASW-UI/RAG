package com.rag.task.task.rpc;

import com.rag.task.task.rpc.support.GrpcTestPort;
import com.rag.task.task.rpc.support.RealContainerPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🔴 <b>阶段 1 补的守门：task-rpc 的 6 个 {@code @KafkaListener} 必须<b>真的进了容器的 registry</b>。</b>
 *
 * <h2>🔴 为什么这是「阶段 1 准入阻断项」而不是「补个测试 nice-to-have」</h2>
 *
 * <p>AGENTS.md §3.3.3 的硬要求：<b>凡用 {@code @KafkaListener} 必须有装配层测试断言 listener
 * 真进了容器的 registry（非「方法存在」）</b>。
 * ⇒ 本仓的 like-rpc（{@code LikeRpcSpringContextWiringTest:180-181}）与
 * hot-rpc（{@code HotRpcSpringContextWiringTest:134-137}）<b>都有</b>这条守门，
 * <b>唯独 task-rpc 没有</b> ⇒ 本域在这条上是<b>全仓唯一的缺口</b>。
 *
 * <p>而本域是<b>纯消息消费域</b>：task-rpc 的全部业务价值 = 「消费 like 域投递的 Kafka 事实
 * ⇒ 折算点赞累计数 ⇒ 推进任务进度」。
 * ⇒ <b>listener 没真装配 ⇒ 消息一条都不会被消费 ⇒ 整域业务不成立</b>
 * ⇒ 这是 P0 第 1 条「核心业务链路能够真实运行」的直接抓手。
 *
 * <h2>🔴 为什么已有守门抓不到</h2>
 *
 * <p>本域唯一的 listener 契约测试 {@code TaskKafkaOffsetResetContractTest:185-195}
 * 是<b>反射读注解</b>（遍历 6 个类的 {@code @KafkaListener} 注解取 {@code properties}）
 * ⇒ 它证明的是「<b>声明了什么</b>」，不是「<b>装配了什么</b>」。
 * ⇒ 若某次重构摘掉 {@code spring-boot-kafka}、或改坏 {@code containerFactory}，
 * 这条守门<b>照样是绿的</b>，而消息<b>一条都不会被消费</b>。
 * （AGENTS.md §3.3 的教科书案例：like-rpc / hot-rpc 都曾「测试全绿、组件根本起不来」。）
 *
 * <h2>本类断言什么（不是「注解存在」，而是「容器里真的按 Go 的契约挂着」）</h2>
 *
 * <p>逐条核 6 个容器的 <b>topic</b> / <b>groupId</b> / <b>auto.offset.reset</b>，
 * 取值全部来自 <b>Go 真源</b>：
 * <ul>
 *   <li>topic / group 字面量 ⇒ {@code service/task/rpc/etc/task.yaml:17-32}；</li>
 *   <li>「哪个消费者绑哪个 topic/group」⇒ {@code service/task/rpc/internal/Init/}：</li>
 *   <li>offset ⇒ goka 三组 {@code initGoKa.go:131/173/207} 写死 {@code sarama.OffsetOldest}；
 *       kq 三组吃 yaml 的 {@code Offset: latest}（task.yaml:34）。</li>
 * </ul>
 *
 * <h2>环境前置</h2>
 *
 * <p>本类接<b>真 PostgreSQL</b>（本模块用 Flyway + MyBatis-Plus ⇒ 没有 DataSource 就起不来上下文），
 * 并把 Kafka 指向<b>不可达地址</b>：consumer 只会持续重试连接，<b>不影响上下文启动</b>，
 * 而 {@code KafkaTemplate} / {@code ConsumerFactory} / <b>listener 容器照样被装配</b>
 * —— 这正是本类要验的东西（⚠️ <b>不能</b>用 {@code exclude} 掉 Kafka 自动配置：
 * 那会把 listener 容器一起排除掉 ⇒ 断言必然假红）。
 */
@SpringBootTest(
        classes = com.rag.task.task.rpc.TaskRpcApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "grpc.server.port=0",
                // 绕过 Nacos：注册中心不可达，注册本身已 try/catch。
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "logging.level.com.alibaba.nacos=WARN",
                // ⚠️ 绕过 Kafka 的方式是**指向不可达地址**，**不是** exclude 自动配置。
                "spring.kafka.bootstrap-servers=127.0.0.1:1",
                // 绕过 Redis：Lettuce 连接懒建，不真正连。
                "spring.data.redis.host=127.0.0.1",
                "spring.data.redis.port=1",
                "management.otlp.tracing.export.enabled=false"
        })
@Testcontainers
class TaskRpcKafkaListenerRegistryGateTest {

    @Container
    static PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("first_db")
                    .withUsername("admin")
                    .withPassword("password");

    /**
     * 本类的真容器<b>固定宿主口</b>（task 域段 22820–22849 内 · 全局段表 §4.10.19.3 分配）。
     *
     * <p>🔴 为什么不用 Testcontainers 的默认随机口：随机宿主口落在 <b>OS 临时段 49152–65535</b>
     * ⇒ 与 Docker published 段同池 ⇒ 全局段表把该段标为<b>禁占</b>。
     * 🔴 为什么端口写死：surefire 每个 fork 一个独立 JVM ⇒ 静态计数器在每个 fork 里从头开始
     * ⇒ 两个并发 fork 会拿到同一个端口 ⇒ 撞车。
     * ⇒ 22826 是本域段内**当前未被占用**的一个（域内其余容器用 22820~22825 与 22840）。
     */
    private static final int FIXED_HOST_PORT = 22826;
    static {
        RealContainerPort.withFixedHostPort(POSTGRES, FIXED_HOST_PORT,
                RealContainerPort.PG_CONTAINER_PORT);
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("grpc.server.port", GrpcTestPort::freePort);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private KafkaListenerEndpointRegistry registry;

    /**
     * 解析「<b>真正跑消费者</b>的那个容器」，供本类读 topic / groupId / offset 契约。
     *
     * <p>🔴 <b>2026-10-29 G2 修：本守门曾把容器类型写死成 {@code KafkaMessageListenerContainer}。</b>
     * <p>事实：Spring Boot 的默认工厂是 {@code ConcurrentKafkaListenerContainerFactory}
     * ⇒ 它<b>永远</b>产出 {@code ConcurrentMessageListenerContainer}
     * （{@code concurrency=1} 时它只是<b>只有一个子容器</b>，类型仍然是 concurrent）
     * ⇒ 而 {@code ConcurrentMessageListenerContainer} 与 {@code KafkaMessageListenerContainer}
     * 在 Spring Kafka 里是<b>兄弟</b>（都 extends {@code AbstractMessageListenerContainer}，
     * <b>不是</b>父子）⇒ 原断言对<b>每一个</b> listener 都必然失败
     * ⇒ <b>判据把合规实现判成了红</b>（产品没坏，是守门的【宽严】写错了）。
     *
     * <p>⇒ 现在改为<b>类型无关</b>地解析：
     * <ul>
     *   <li>concurrent ⇒ 取它的<b>第 1 个子容器</b>（子容器才是真正持有 consumer 的那个）；</li>
     *   <li>单线程 ⇒ 就是它自己。</li>
     * </ul>
     * ⇒ 契约（topic / groupId / auto.offset.reset）仍从<b>消费者所在的那一层</b>读
     * ⇒ 守门强度不降（读的是真正生效的配置），且不再有假红。
     */
    private static MessageListenerContainer effectiveConsumer(MessageListenerContainer container) {
        if (container instanceof ConcurrentMessageListenerContainer<?, ?> concurrent) {
            List<? extends KafkaMessageListenerContainer<?, ?>> children = concurrent.getContainers();
            assertThat(children)
                    .as("🔴 concurrent 容器 " + concurrent.getBeanName()
                            + " 没有子容器 ⇒ 没有任何消费者在跑（消息一条都不会被消费）")
                    .isNotEmpty();
            return children.get(0);
        }
        return container;
    }

    /**
     * 6 个 listener 的 Go 契约。
     *
     * <p>key = RAG 侧的显式 listener id（{@code @KafkaListener(id = ...)}）
     * ⇒ value = [topic, groupId, auto.offset.reset]
     *
     * <p>⚠️ 这张表的<b>每一格</b>都有 Go 出处（AGENTS.md §3.1：逐字对照，不采信转述）：
     * <ul>
     *   <li>字面量 ⇒ {@code service/task/rpc/etc/task.yaml:17-32}；</li>
     *   <li>绑定关系 ⇒ {@code internal/Init/initGoKa.go:123-127/164-168/198-202}（goka 三组）、
     *       {@code initKafkaRaw.go:24-30}（raw 诊断）、{@code initUserFilter.go:29-30}（用户 sink）、
     *       {@code initArticleFilter.go:29-30}（文章 sink）；</li>
     *   <li>offset ⇒ goka 三组 {@code initGoKa.go:131/173/207} = {@code sarama.OffsetOldest}；
     *       kq 三组吃 yaml {@code Offset: latest}（task.yaml:34）。</li>
     * </ul>
     */
    private static final Map<String, String[]> GO_CONTRACT = new LinkedHashMap<>();

    static {
        // goka 三组：initGoKa.go
        GO_CONTRACT.put("task-goka-router", new String[]{"task.logs.raw", "task.rpc.consumer41", "earliest"});
        GO_CONTRACT.put("task-goka-user-count", new String[]{"task.logs.raw.User", "task.rpc.consumer61", "earliest"});
        GO_CONTRACT.put("task-goka-article-count", new String[]{"task.logs.raw.Article", "task.rpc.consumer71", "earliest"});
        // kq 三组：initKafkaRaw.go / initUserFilter.go / initArticleFilter.go
        GO_CONTRACT.put("task-raw-diagnostic", new String[]{"task.logs.raw", "task.rpc.consumer31", "latest"});
        GO_CONTRACT.put("task-user-progress-sink", new String[]{"task.logs.filtered.User", "task.rpc.consumer81", "latest"});
        GO_CONTRACT.put("task-article-progress-sink", new String[]{"task.logs.filtered.Article", "task.rpc.consumer101", "latest"});
    }

    @Test
    @DisplayName("🔴 KafkaListenerEndpointRegistry 存在且 6 个 listener 的容器 id 都在里面")
    void everyListenerIsActuallyRegisteredInTheContainerRegistry() {
        assertThat(registry)
                .as("🔴 KafkaListenerEndpointRegistry 缺失 ⇒ 没有 listener 真正注册")
                .isNotNull();

        var containerIds = registry.getListenerContainerIds();
        assertThat(containerIds)
                .as("🔴 registry 里没有任何 listener 容器 ⇒ @KafkaListener 没真正装配，"
                        + "消息一条都不会被消费（而「listener 方法存在」的断言照样是绿的）")
                .isNotEmpty();

        for (String id : GO_CONTRACT.keySet()) {
            assertThat(containerIds)
                    .as("🔴 registry 的容器 id = " + containerIds + "，不含 " + id
                            + " ⇒ 该 listener 没真正接上 ⇒ 它负责的那段链路是死的")
                    .contains(id);
        }
    }

    @Test
    @DisplayName("🔴 每个 listener 的 topic / groupId 必须与 Go 真源逐字一致")
    void everyListenerBindsGoTopicsAndGroups() {
        for (Map.Entry<String, String[]> e : GO_CONTRACT.entrySet()) {
            String id = e.getKey();
            String[] expected = e.getValue();

            MessageListenerContainer container = registry.getListenerContainer(id);
            assertThat(container)
                    .as("🔴 listener " + id + " 不在 registry 里")
                    .isNotNull();
            assertThat(container)
                    .as("🔴 listener " + id + " 的容器类型 = " + container.getClass()
                            + " ⇒ 不是 Spring Kafka 的两种合法容器类型"
                            + "（KafkaMessageListenerContainer / ConcurrentMessageListenerContainer）")
                    .isInstanceOfAny(KafkaMessageListenerContainer.class, ConcurrentMessageListenerContainer.class);

            ContainerProperties props = effectiveConsumer(container).getContainerProperties();

            assertThat(props.getTopics())
                    .as("🔴 listener " + id + " 的 topic 与 Go 不一致。"
                            + "Go 出处：service/task/rpc/etc/task.yaml + internal/Init/*.go")
                    .containsExactly(expected[0]);

            assertThat(props.getGroupId())
                    .as("🔴 listener " + id + " 的 groupId 与 Go 不一致 ⇒ 位点与并发模型都变了")
                    .isEqualTo(expected[1]);
        }
    }

    @Test
    @DisplayName("🔴 auto.offset.reset 必须与 Go 一致（goka 三组 earliest / kq 三组 latest）")
    void everyListenerBindsGoOffsetReset() {
        for (Map.Entry<String, String[]> e : GO_CONTRACT.entrySet()) {
            String id = e.getKey();
            String expected = e.getValue()[2];

            MessageListenerContainer container = registry.getListenerContainer(id);
            assertThat(container).isNotNull();

            ContainerProperties props = effectiveConsumer(container).getContainerProperties();
            Object actual = props.getKafkaConsumerProperties().get("auto.offset.reset");

            assertThat(actual)
                    .as("🔴 listener " + id + " 的 auto.offset.reset 与 Go 不一致 ⇒ "
                            + "首次消费（无位点）时从哪开始消费就变了。"
                            + "Go 出处：goka 三组 initGoKa.go:131/173/207 = sarama.OffsetOldest；"
                            + "kq 三组吃 task.yaml:34 的 Offset: latest")
                    .isEqualTo(expected);
        }
    }
}
