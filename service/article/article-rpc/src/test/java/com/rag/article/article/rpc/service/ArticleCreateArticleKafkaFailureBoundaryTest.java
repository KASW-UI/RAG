package com.rag.article.article.rpc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import com.rag.article.article.rpc.ArticleRpcApplication;
import com.rag.article.article.rpc.client.ArticleObjectStorageClient;
import com.rag.article.article.rpc.exception.ArticleExceptions;
import com.rag.article.article.rpc.mq.ArticleSyncEventTypes;
import com.rag.article.article.proto.ArticleStatus;
import com.rag.article.article.proto.CreateArticleRequest;
import com.rag.article.article.rpc.support.RealContainerHostPort;
import com.rag.article.article.rpc.support.RealGrpcPort;
import io.grpc.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 🔴 <b>真 PG + 死 broker</b> 上的「创建文章写路径」守门 ——
 * 补 {@code docs/DRIFT/article/rpc.md} §4 等价矩阵里那两行<b>只有「静态」证据</b>的格子：
 * <ul>
 *   <li>「CreateArticle · Kafka 失败<b>不回滚</b>已插入行」；</li>
 *   <li>「CreateArticle · MinIO 先 / DB 后顺序」里的<b>失败面</b>（Kafka 推送失败的错误码）。</li>
 * </ul>
 *
 * <h2>Go 出处（<b>逐条指到消费点</b>，不是声明点）</h2>
 * <pre>
 * 插入点  service/article/rpc/internal/logic/create_article_logic.go:109-113
 *        ⇒ 插入 article 行（status = REVIEWING，ext_info 写 8 个初始键）
 * 推送点  service/article/rpc/internal/logic/create_article_logic.go:125-130
 *        ⇒ 推 Kafka 审核消息；失败 ⇒ span.RecordError(err) + status.Error(codes.Internal, err.Error())
 *        ⇒ 🔴 **行不回滚**（Go 没有事务）
 * </pre>
 *
 * <h2>为什么必须「真 PG」而不是 Mockito</h2>
 * <p>本用例要断言的是「<b>行还在库里</b>」—— 这是一条<b>持久化事实</b>，
 * 不是「某个方法被调用过」（AGENTS.md §3.3.3：纯 Mockito 锁的是调用形态，不是行为）。
 * ⇒ 必须让真正的 INSERT 落到真 PG，然后回读。
 *
 * <h2>为什么 Kafka 指向<b>死 broker</b>（而不是 mock KafkaTemplate）</h2>
 * <p>因为要断言的是「<b>推送真的失败</b>」这条路径（{@code kafkaTemplate.send(...).get()} 抛错
 * ⇒ {@code ArticleExceptions.internal(...)}），而不是「mock 被人配置成抛错」。
 * ⇒ 用一个<b>真的连不上</b>的 broker（{@code 127.0.0.1:1}）⇒ 失败是环境事实，不是测试装置。
 * <p>⚠️ {@code ArticleSpringContextWiringTest} 早就用同一个死地址起上下文 ⇒ 上下文<b>照常启动</b>
 * （listener 容器连不上只会后台重试，不会让启动失败）。
 */
@Testcontainers
@SpringBootTest(
        classes = ArticleRpcApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN",
                "grpc.client.security-rpc.address=static://127.0.0.1:1",
                "grpc.client.message-rpc.address=static://127.0.0.1:1",
                // 🔴 死 broker ⇒ 推送**必然**失败（这是本用例的被测前提，不是缺陷）
                "spring.kafka.bootstrap-servers=127.0.0.1:1",
                // ⚠️ 四个 Kafka 通道的 brokers 必须与全局 bootstrap-servers **同值**
                //    （ArticleKafkaChannelValidator 发现分叉会**让启动失败**）。
                "article-rpc.kq-pusher-conf.brokers=127.0.0.1:1",
                "article-rpc.kq-consumer-conf.brokers=127.0.0.1:1",
                "article-rpc.article-sync-pusher-conf.brokers=127.0.0.1:1",
                "article-rpc.article-sync-result-consumer-conf.brokers=127.0.0.1:1",
                // 让「取不到 metadata」快速失败，而不是干等默认的 60s
                "spring.kafka.producer.properties.max.block.ms=2000",
                "spring.kafka.producer.properties.request.timeout.ms=2000",
                "spring.kafka.producer.properties.delivery.timeout.ms=3000",
                "spring.kafka.producer.properties.retries=0"
        })
@org.testcontainers.junit.jupiter.EnabledIfDockerAvailable
class ArticleCreateArticleKafkaFailureBoundaryTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("article_create_kafka_fail_it")
                    .withUsername("rag")
                    .withPassword("rag")
                    .withCreateContainerCmdModifier(
                            RealContainerHostPort.bindPg(
                                    RealContainerHostPort.CREATE_ARTICLE_KAFKA_FAILURE));

    @DynamicPropertySource
    static void infra(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("grpc.server.port", RealGrpcPort::pick);
    }

    /**
     * 🔴 MinIO 在本用例里<b>不参与被测语义</b>（被测的是「Kafka 推送失败 ⇒ 行不回滚」），
     * ⇒ 把对象存储这一层<b>换成桩</b>，让本用例不必依赖一个真的 MinIO。
     * <p>⚠️ 桩只挡 MinIO，<b>不挡</b>被测的那条失败路径。
     */
    @MockitoBean
    ArticleObjectStorageClient storage;

    @Autowired
    ArticleDomainService domainService;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("🔴 Go create_article_logic.go:125-130：Kafka 推送失败 ⇒ INTERNAL，"
            + "**且已插入的行仍在库里**（Go 无事务 ⇒ 不回滚）")
    void kafkaPushFailureReturnsInternalAndKeepsTheInsertedRow() {
        // ── 准备：MinIO 那一层用桩（getObjectNamePrefixForArticle 要真值；put 不抛） ──
        org.mockito.Mockito.when(storage.getObjectNamePrefixForArticle())
                .thenReturn("articles/content/");

        // ── 造一个**必然**推送失败的请求 ──
        CreateArticleRequest request =
                CreateArticleRequest.newBuilder()
                        .setAuthorId("author-kafka-fail")
                        .setTitle("标题")
                        .setMarkdownContent("# 正文")
                        .build();

        // ── 调：Go 的 status.Error(codes.Internal, ...) ⇒ 领域异常带 INTERNAL ──
        ArticleExceptions.Rpc thrown = assertThrows(ArticleExceptions.Rpc.class,
                () -> domainService.createArticle(request),
                "Kafka 推送失败时必须抛领域异常（Go: status.Error(codes.Internal, err.Error())）");
        assertEquals(Status.Code.INTERNAL, thrown.getCode(),
                "错误码必须是 INTERNAL（Go create_article_logic.go:125-130）"
                        + " ⇒ 实际=" + thrown.getCode());

        // ── 关键断言：**行还在**，且是 REVIEWING + 8 个初始 ext_info 键 ──
        //    ⚠️ status 是 SMALLINT（V8__create_article.sql）· ext_info 是 JSONB ⇒ 必须 ::text 才拿得到内容
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select id, status, ext_info::text as ext_info from article order by created_at desc limit 1");
        assertEquals(1, rows.size(), "article 表里没有行 ⇒ 插入根本没发生（本用例前提失效）");

        Map<String, Object> row = rows.get(0);
        String id = String.valueOf(row.get("id"));
        assertEquals(ArticleStatus.REVIEWING_VALUE,
                ((Number) row.get("status")).intValue(),
                "新建的文章状态必须是 REVIEWING（Go create_article_logic.go:109-113）");

        String extInfo = String.valueOf(row.get("ext_info"));
        assertNotNull(extInfo, "ext_info 为空");
        // Go create_article_logic.go:97-106 的 8 个初始键（消费者按它们判幂等）
        for (String key : new String[] {
                ArticleSyncEventTypes.EXT_PUBLISH_STAGE,
                ArticleSyncEventTypes.EXT_RECO_SYNC_STATE,
                ArticleSyncEventTypes.EXT_LAST_SYNC_ERROR,
                ArticleSyncEventTypes.EXT_LAST_SYNC_EVENT_ID,
                ArticleSyncEventTypes.EXT_LAST_SYNC_VERSION,
                ArticleSyncEventTypes.EXT_PENDING_SYNC_REASON,
                ArticleSyncEventTypes.EXT_LAST_SYNC_REASON,
                ArticleSyncEventTypes.EXT_REVIEW_NONCE }) {
            assertTrue(extInfo.contains(key),
                    "ext_info 缺少初始键 " + key + "（Go create_article_logic.go:97-106）"
                            + " ⇒ ext_info=" + extInfo);
        }
        // review_nonce 初始值 = articleID 本身（Go: mqs.ExtReviewNonce: articleID）
        assertTrue(extInfo.contains("\"value\":\"" + id + "\"")
                        || extInfo.contains(":" + id + "\"")
                        || extInfo.contains(id),
                "review_nonce 的初始值必须就是 articleID 本身（Go mqs.ExtReviewNonce: articleID）"
                        + " ⇒ articleID=" + id + " ext_info=" + extInfo);

        // ── 核心：**没有回滚** ──
        //    若 RAG 侧「顺手」加了事务，失败会把刚插入的行回滚掉 ⇒ 这里就会查不到 ⇒ 用例转红。
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from article where id = ?", Integer.class, id),
                "Go 侧 create_article_logic.go:125-130 的语义是**行不回滚**"
                        + "（Go 没有事务）⇒ RAG 侧若回滚就是**行为漂移**（比 Go 更严 ⇒ 也不对齐）");
    }
}
