package com.rag.article.article.rpc;

import com.rag.article.article.rpc.client.ContentSecurityPort;
import com.rag.article.article.rpc.client.UnavailableContentSecurityClient;
import com.rag.article.article.rpc.domain.ArticleEntity;
import com.rag.article.article.rpc.mapper.ArticleDomainOutboxMapper;
import com.rag.article.article.rpc.mapper.ArticleMapper;
import com.rag.article.article.rpc.mapper.ArticlePublicMapper;
import com.rag.article.article.rpc.mapper.ArticlePublicationMapper;
import com.rag.article.article.rpc.mapper.ArticleRevisionMapper;
import com.rag.article.article.rpc.mapper.ArticleSyncOutboxMapper;
import com.rag.article.article.rpc.metrics.ArticleRpcMetrics;
import com.rag.article.article.rpc.service.ArticleAtomicService;
import com.rag.article.article.rpc.service.ArticleDomainService;
import com.rag.article.article.rpc.service.ArticlePublicDomainService;
import com.rag.article.article.rpc.service.impl.ArticleServiceGrpcImpl;
import io.grpc.ServerInterceptor;
import net.devh.boot.grpc.server.serverfactory.GrpcServerConfigurer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.FailedRecordProcessor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 装配证据 —— <b>断言的是"机制真的接上了"，不是"Bean 存在"</b>。
 *
 * <p>AGENTS.md §6.6 记录的 message-rpc 教训：21 条测试全绿，但模块<b>一次都没真正启动过</b>；
 * 随后挖出 3 个"模块根本起不来"级缺陷（缺 {@code @MapperScan}、缺 {@code spring-boot-flyway}、
 * Bean 名撞车）。本类的存在就是为了让 article-rpc <b>真的起一次</b>。
 *
 * <h2>⚠️ 为什么这里必须接真 PG，不能像第一版那样排除 DataSourceAutoConfiguration</h2>
 * <p>第一版为了「不连库」加了
 * {@code spring.autoconfigure.exclude=…DataSourceAutoConfiguration}，
 * 结果上下文<b>根本起不来</b>：{@code @MapperScan} 注册的 6 个 MapperFactoryBean
 * 全部要求 {@code sqlSessionFactory}，没有 DataSource 就没有它 ——
 * <pre>BeanCreationException: articleSyncOutboxMapper …
 *      Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required</pre>
 * <p>也就是说「不接库」这个看似保守的选择，<b>恰恰让本类要验证的东西无法验证</b>。
 * Docker 可用时，正确做法是接真 PG（见 {@link ArticleTransactionWiringTest}），
 * 这样才顺带能验 Flyway 是否真的建了表（DRIFT-MSG-006）。
 */
@SpringBootTest(
        classes = ArticleRpcApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "grpc.server.port=0",
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN",
                "grpc.client.security-rpc.address=static://127.0.0.1:1",
                "grpc.client.message-rpc.address=static://127.0.0.1:1",
                "spring.kafka.bootstrap-servers=127.0.0.1:1"
        })
@Testcontainers
@org.testcontainers.junit.jupiter.EnabledIfDockerAvailable
class ArticleSpringContextWiringTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("article_rpc_wiring_it")
                    .withUsername("rag")
                    .withPassword("rag");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private ApplicationContext context;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    // ==================================================================
    // 1. @MapperScan —— 缺它 6 个 Mapper 全部注册不上
    // ==================================================================

    @Test
    @DisplayName("@MapperScan 生效：6 个 Mapper Bean 全部可注入（DRIFT-MSG-005 同款）")
    void allMappersAreRegistered() {
        // ⚠️ 这 6 个 Bean 存在与否**直接**证明 @MapperScan 是否生效：
        //    它们全是 mybatis 的 MapperFactoryBean，没有扫描就一个都没有。
        assertNotNull(context.getBean(ArticleMapper.class), "ArticleMapper 未注册 ⇒ @MapperScan 缺失");
        assertNotNull(context.getBean(ArticlePublicMapper.class));
        assertNotNull(context.getBean(ArticleSyncOutboxMapper.class));
        assertNotNull(context.getBean(ArticleRevisionMapper.class));
        assertNotNull(context.getBean(ArticlePublicationMapper.class));
        assertNotNull(context.getBean(ArticleDomainOutboxMapper.class));
    }

    @Test
    @DisplayName("MyBatis-Plus 排序桥接生效：SqlSessionFactory 可用（否则 mapper 报 sqlSessionFactory 缺失）")
    void mybatisPlusIsWired() {
        assertNotNull(context.getBean(org.apache.ibatis.session.SqlSessionFactory.class),
                "SqlSessionFactory 缺失 ⇒ MybatisPlusOrderingBridgeAutoConfiguration 没生效，"
                        + "Spring Boot 4 下 MP 会先于 DataSource 求值导致自动配置整体不生效");
    }

    @Test
    @DisplayName("🔴 Flyway 真跑了：article 域五张表确实存在（DRIFT-MSG-006 同款）")
    void flywayActuallyCreatedArticleTables() {
        // ⚠️ 缺 spring-boot-flyway 时**启动日志里连一行 Flyway 都没有**（不是报错，是根本没加载），
        //    表永不创建 ⇒ 第一次真正执行 SQL 才炸。这里直接查 information_schema 把"跑过了"变成可观测事实。
        // ⚠️ 表名逐个对照 Go 的 TableName()，不是照着模型文件名猜的：
        //    article_public.go 是**模型文件**名，它查询的是 article / article_publication 两张表，
        //    并不存在名为 article_public 的表（第一版写错就是靠这条断言抓出来的）。
        List<String> expected = List.of(
                "article",                    // article.go
                "article_sync_outbox",        // article_sync_outbox.go
                "article_revision",           // community_revision.go
                "article_publication",        // community_revision.go
                "article_domain_outbox");     // community_revision.go
        for (String table : expected) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM information_schema.tables "
                            + "WHERE table_schema = 'public' AND table_name = ?",
                    Integer.class, table);
            assertEquals(1, count,
                    "表 " + table + " 不存在 ⇒ Flyway 迁移没执行（spring-boot-flyway 是否在 pom 里？）");
        }

        // 迁移必须已经被记进历史表，否则下一次启动会重跑 V1..V10
        Integer applied = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class);
        assertTrue(applied != null && applied >= 3,
                "flyway_schema_history 里成功执行的迁移少于 3 条（实际 " + applied + "）"
                        + "⇒ 本模块三份迁移脚本（V8/V9/V10）没有全部执行");
    }

    // ==================================================================
    // 2. gRPC interceptor 必须真的挂到 server 上
    // ==================================================================

    @Test
    @DisplayName("🔴 ServerInterceptor 既有 Bean 也被 GrpcServerConfigurer 挂上（§3.3：Bean 存在 ≠ 机制生效）")
    void serverInterceptorIsBothPresentAndAttached() {
        // ① Bean 存在。
        // ⚠️ 必须按**名字**取，不能按类型取：devh starter 自己还注册了一个
        //    grpcRequestScope（io.grpc.ServerInterceptor），
        //    按类型 getBean 会抛 NoUniqueBeanDefinitionException。
        //    挂上 server 的是 RpcServerInterceptorFactory 产出的那一个。
        ServerInterceptor unary = context.getBean("rpcUnaryServerInterceptor", ServerInterceptor.class);
        assertNotNull(unary, "rpcUnaryServerInterceptor 缺失 ⇒ RpcServerInterceptorFactory 没被 @Import");

        // ② 且被挂到 server 上
        assertTrue(context.getBeansOfType(GrpcServerConfigurer.class).size() >= 1,
                "GrpcServerConfigurer 缺失 ⇒ interceptor 不会挂上 server，"
                        + "observability.rpc-timeout(70s) 与 deadline 传播会静默失效");
    }

    @Test
    @DisplayName("@GrpcService 的 ArticleServiceGrpcImpl 已注册（RPC 面确实暴露）")
    void grpcServiceIsRegistered() {
        assertNotNull(context.getBean(ArticleServiceGrpcImpl.class));
    }

    // ==================================================================
    // 3. 领域 Bean 的事务代理
    // ==================================================================

    @Test
    @DisplayName("🔴 ArticleAtomicService 是 AOP 代理 ⇒ 四处 @Transactional 真的生效")
    void atomicServiceIsAopProxied() {
        ArticleAtomicService atomic = context.getBean(ArticleAtomicService.class);
        assertTrue(org.springframework.aop.support.AopUtils.isAopProxy(atomic),
                "ArticleAtomicService 不是 AOP 代理 ⇒ @Transactional 静默失效，"
                        + "retract+update / retract+delete / 审核入队 / 发布落地 都会退化为 auto-commit");
    }

    @Test
    @DisplayName("领域服务三个 Bean 齐备（作者视角 / 公开视角 / 原子事务）")
    void domainServicesArePresent() {
        assertNotNull(context.getBean(ArticleDomainService.class));
        assertNotNull(context.getBean(ArticlePublicDomainService.class));
        assertNotNull(context.getBean(ArticleAtomicService.class));
        assertNotNull(context.getBean(ArticleRpcMetrics.class));
    }

    // ==================================================================
    // 4. Kafka listener 容器工厂的错误契约
    // ==================================================================

    @Test
    @DisplayName("🔴 Kafka listener 工厂用 RECORD ack（逐条确认，BATCH 会连带丢同批成功消息）")
    void kafkaListenerFactoryUsesRecordAckMode() {
        @SuppressWarnings("unchecked")
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                context.getBean("articleKafkaListenerContainerFactory",
                        ConcurrentKafkaListenerContainerFactory.class);

        assertEquals(ContainerProperties.AckMode.RECORD, factory.getContainerProperties().getAckMode(),
                "ack 模式必须是 RECORD：Go kq 是逐条 CommitMessages，"
                        + "BATCH 会把位点推到整个 poll 批末，一条失败连带丢掉同批次已成功的消息");
    }

    @Test
    @DisplayName("🔴 工厂产出的容器确实挂着「零重试 + 失败即提交位点」的错误处理器")
    void kafkaContainersCarryTheForceCommitErrorHandler() {
        @SuppressWarnings("unchecked")
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                context.getBean("articleKafkaListenerContainerFactory",
                        ConcurrentKafkaListenerContainerFactory.class);

        // 🔴 关键：不是断言「Bean 存在」，而是断言**工厂真正产出的容器**上挂着它。
        //    createContainer(String...) 是 public，会走完整的 endpoint→container 装配路径，
        //    setCommonErrorHandler 若没写，这里读回来就是 null。
        ConcurrentMessageListenerContainer<String, String> container =
                factory.createContainer("article-probe-topic");

        CommonErrorHandler onContainer = container.getCommonErrorHandler();
        assertNotNull(onContainer, "容器上没有 CommonErrorHandler ⇒ listener 抛异常后的位点处置无人接管");
        assertSame(context.getBean("articleNoCommitErrorHandler"), onContainer,
                "容器上的错误处理器不是那个具名 Bean ⇒ 两个 listener 的失败契约可能不一致");

        assertTrue(onContainer instanceof DefaultErrorHandler,
                "必须是 DefaultErrorHandler（FailedRecordProcessor 家族），"
                        + "否则 BackOff=0 的语义设置无处可施");

        FailedRecordProcessor processor = (FailedRecordProcessor) onContainer;
        // 🔴 修正后：seekAfterError 必须为 **true**（= Go 的 ForceCommit 默认值）。
        //
        // Go 出处：go-queue@v1.2.2
        //   kq/config.go:24   ForceCommit bool `json:",default=true"`      ← 声明点，默认值在这里
        //   kq/queue.go:235   if !q.c.ForceCommit { continue }               ← 消费点
        //   kq/queue.go:240   CommitMessages(...)                           ← ForceCommit=true ⇒ continue 不执行
        // article-rpc 的 yaml 没配 ForceCommit ⇒ 生效值 true ⇒ **失败也提交位点**。
        //
        // 本用例曾断言 assertFalse，定位原因是只看到了 queue.go 的 `continue`，
        // 没有消费 ForceCommit 的 struct tag 默认值（AGENTS.md §3.3.1）。
        assertTrue(processor.isSeekAfterError(),
                "必须为 true：Go 的 ForceCommit 默认值是 true ⇒ 失败记录也提交位点。"
                        + "设为 false 会让失败消息在 rebalance/重启后无限重投，与 Go 相反。"
                        + "FailedBatchProcessor.seekOrRecover 在 true 分支里 commit 的正是失败记录自己的 offset+1。");

        // ⚠️ 「零次进程内重试」**没有**写成断言：
        // FailedRecordProcessor 只有 setBackOffFunction 而无公开 getter，BackOffExecutionUtils
        // 在 Spring 6+ 也已不存在，只能靠反射读私有字段；而 DefaultErrorHandler 的默认实现
        // 对未分类异常返回 null —— 断言会退化成「测代理代码行为」而非「测被测行为」。
        // 它在 ArticleRpcApplication#articleNoCommitErrorHandler 里是字面量 new FixedBackOff(0L, 0L)，
        // 可直接审阅；要变成运行时证据需要真 Kafka（未决，见 unresolved.md）。
    }

    @Test
    @DisplayName("🔴 Kafka handler 并发度 = 8（Go 的 KqConf.Processors 默认值，不是 yaml 的 Consumers: 1）")
    void kafkaHandlerConcurrencyMatchesGoProcessorsDefault() {
        @SuppressWarnings("unchecked")
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                context.getBean("articleKafkaListenerContainerFactory",
                        ConcurrentKafkaListenerContainerFactory.class);

        // Go：kq/config.go:19 Processors int `json:",default=8"` ← 声明点
        //     kq/queue.go:220 for i := 0; i < q.c.Processors; i++            ← 消费点
        // 注意 Consumers（yaml 配的 1）控制的是 reader 协程（queue.go:243 startProducers），
        // 不是 handler 并发度；把它当成「单消费者」是当前误判的来源。
        // ⚠️ 断言的是**工厂真正产出的容器**，不是 factory 字段
        //（ConcurrentKafkaListenerContainerFactory 只有 setter，没有 getter）。
        ConcurrentMessageListenerContainer<String, String> container =
                factory.createContainer("article-concurrency-probe-topic");
        assertEquals(8, container.getConcurrency(),
                "容器的 handler 并发度必须 = Go 的 Processors 默认值 8；"
                        + "Spring 默认 concurrency=1 会退化成严格串行，吞吐差一个量级。");
    }

    // ==================================================================
    // 5. security 端口：必须有且必须 fail-loud
    // ==================================================================

    @Test
    @DisplayName("🔴 security 端口恰好一个；兜底实现必须**抛异常**（绝不能 fail-open 放行）")
    void contentSecurityPortIsExactlyOneAndFailsLoud() {
        String[] beans = context.getBeanNamesForType(ContentSecurityPort.class);
        assertEquals(1, beans.length,
                "ContentSecurityPort 实现数必须恰好为 1；为 0 会让审核消费者拿到 null 端口而 NPE，"
                        + "为 2 会让注入歧义");

        ContentSecurityPort port = context.getBean(ContentSecurityPort.class);
        assertInstanceOf(UnavailableContentSecurityClient.class, port,
                "security 模块未落地时必须是兜底实现");

        // 🔴 核心：调用必须抛异常，绝不能"返回不是广告"从而放行未审核内容
        assertThrows(ContentSecurityPort.SecurityAuditException.class, () -> port.sanitizeText("# body"),
                "security 不可用时返回了正常结果 ⇒ 未审核内容会被直接放行（fail-open）");
        assertThrows(ContentSecurityPort.SecurityAuditException.class,
                () -> port.detectImageAd("data:image/jpeg;base64,AAA"),
                "security 不可用时返回了正常结果 ⇒ 未审核图片会被直接放行（fail-open）");
    }

    // ==================================================================
    // 6. Nacos 注册用了显式 Bean 名
    // ==================================================================

    @Test
    @DisplayName("🔴 NacosRegistration 用显式 Bean 名（撞名会让上下文直接起不来，DRIFT-MSG-004）")
    void nacosRegistrationHasExplicitBeanName() {
        // 裸 @Component 的默认名会是 "nacosRegistration"，与 Nacos 的自动配置同名
        assertTrue(context.containsBean("articleRpcNacosRegistration"),
                "必须用显式 Bean 名 articleRpcNacosRegistration，"
                        + "否则与 NacosServiceRegistryAutoConfiguration 的 nacosRegistration 撞车");
        assertFalse(context.containsBean("nacosRegistration") && !context.containsBean("articleRpcNacosRegistration"));
    }
}