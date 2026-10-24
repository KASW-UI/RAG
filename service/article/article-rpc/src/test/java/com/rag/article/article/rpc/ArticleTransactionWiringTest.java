package com.rag.article.article.rpc;

import com.rag.article.article.rpc.domain.ArticleEntity;
import com.rag.article.article.rpc.domain.ArticleSyncOutboxEntity;
import com.rag.article.article.rpc.exception.ArticleExceptions;
import com.rag.article.article.rpc.mapper.ArticleMapper;
import com.rag.article.article.rpc.mapper.ArticlePublicationMapper;
import com.rag.article.article.rpc.mapper.ArticleSyncOutboxMapper;
import com.rag.article.article.rpc.mq.ArticleSyncEventTypes;
import com.rag.article.article.rpc.service.ArticleAtomicService;
import com.rag.article.article.rpc.service.ArticlePublicDomainService;
import com.rag.article.article.rpc.service.ArticlePublicDomainService.PublicArticleView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 事务与 schema 证据 —— 真 PostgreSQL 上验证三件<b>只有真容器才能证明</b>的事。
 *
 * <h2>为什么必须是真 PG（不能用 H2 / 不能用 Mockito）</h2>
 * 本类验证的不变量本身就是 PG 语义：
 * <ul>
 *   <li>{@code article_revision_immutable} 触发器（<b>BEFORE UPDATE OR DELETE 抛异常</b>）
 *       —— H2 没有这个能力；</li>
 *   <li>{@code article_publication.state} 与 {@code pointer_version} 的 CHECK 约束；</li>
 *   <li>{@code article_domain_outbox} 的 {@code UNIQUE(aggregate_id, aggregate_version)}；</li>
 *   <li>{@code FOR UPDATE} 行锁下的并发串行化；</li>
 *   <li>jsonb 列（{@code secondary_tags} / {@code ext_info}）与 {@code @>} 算子；</li>
 *   <li>「触发器注入失败 ⇒ 整事务回滚」——需要真实的事务与约束行为。</li>
 * </ul>
 * 换成 H2 等于<b>把要验证的东西换没了</b>（同 message-rpc / admin-rpc 的理由）。
 *
 * <p><b>为什么不用纯 Mockito 单测</b>：mock 数据层不执行 SQL、不装配 Spring 上下文，
 * 于是「@Transactional 是否真的生效」「触发器是否存在」这两类问题<b>按定义测不到</b>。
 * AGENTS.md §6.6 记录的 message-rpc 教训正是如此：21 条测试全绿，
 * 但模块<b>一次都没真正启动过</b>。
 */
@SpringBootTest(
        classes = ArticleRpcApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "grpc.server.port=0",
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN",
                // ⚠️ devh 的 @GrpcClient 在 **Bean 初始化期就建 channel**（不是懒建），
                //    测试环境没有 Nacos ⇒ discovery:// 解析失败 ⇒ 上下文起不来。
                //    覆盖成 static:// 指向必然连不上的端口即可；本类验证的路径**不调这两个 client**。
                "grpc.client.security-rpc.address=static://127.0.0.1:1",
                "grpc.client.message-rpc.address=static://127.0.0.1:1",
                // Kafka：listener 容器会在上下文启动时尝试连接 broker。
                // 本类不验证消费路径，用一个不可达地址让 listener 惰性重试即可（不阻塞上下文启动）。
                "spring.kafka.bootstrap-servers=127.0.0.1:1"
        })
@Testcontainers
@org.testcontainers.junit.jupiter.EnabledIfDockerAvailable
class ArticleTransactionWiringTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("article_rpc_tx_it")
                    .withUsername("rag")
                    .withPassword("rag");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        // ⚠️ ApplicationName 是**锁序探针**（lockOrderIsArticleThenPublication）的抓手：
        //    它要从 pg_stat_activity 里挑出「本测试卡住的后端」。
        //    同容器实例上只有本测试会卡在 FOR UPDATE 上 ⇒ 加上标记就不会误抓别的会话。
        r.add("spring.datasource.url", () -> {
            String url = POSTGRES.getJdbcUrl();
            return url + (url.contains("?") ? "&" : "?") + "ApplicationName=article-lockorder";
        });
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private ArticleAtomicService atomicService;
    @Autowired private ArticleMapper articleMapper;
    @Autowired private ArticlePublicationMapper publicationMapper;
    @Autowired private ArticleSyncOutboxMapper syncOutboxMapper;
    @Autowired private com.rag.article.article.rpc.mapper.ArticlePublicMapper publicMapper;
    @Autowired private com.rag.article.article.rpc.service.ArticlePublicDomainService publicService;
    @Autowired private JdbcTemplate jdbcTemplate;

    /**
     * 🔴 每个用例前清空 article 域五张表。
     *
     * <p>静态容器在整个测试类生命周期内复用同一份 PG，<b>不清就是跨用例串数据</b>：
     * 同一主键被前一个用例插入 ⇒ 后面报 {@code DuplicateKey}，
     * 而报错看起来像是被测代码的问题，实则是数据泄漏。
     * 用 TRUNCATE 而非 DELETE：{@code article_revision} 上有 BEFORE DELETE 触发器
     * （{@code article_revision_immutable}），DELETE 会被它挡下来。
     */
    @org.junit.jupiter.api.BeforeEach
    void resetDomainTables() {
        jdbcTemplate.execute("TRUNCATE article, article_sync_outbox, article_revision, "
                + "article_publication, article_domain_outbox RESTART IDENTITY CASCADE");
    }

    // ==================================================================
    // 1. Flyway 迁移确实执行了（含不可变触发器）
    // ==================================================================

    @Test
    @DisplayName("Flyway 迁移真的执行了：5 张表 + article_revision_immutable 触发器都在")
    void flywayMigrationsActuallyRan() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class);
        assertTrue(tables.contains("article"), "article 表缺失 ⇒ Flyway 未执行");
        assertTrue(tables.contains("article_sync_outbox"), "article_sync_outbox 表缺失");
        assertTrue(tables.contains("article_revision"), "article_revision 表缺失");
        assertTrue(tables.contains("article_publication"), "article_publication 表缺失");
        assertTrue(tables.contains("article_domain_outbox"), "article_domain_outbox 表缺失");

        // ⚠️ 这条断言是 ArticleSchemaGate 存在的理由：
        // 缺 spring-boot-flyway 时，启动日志里连一行 Flyway 都没有，表与触发器都不创建，
        // 而进程"看起来完全正常"（DRIFT-MSG-006 同款）。
        Boolean trigger = jdbcTemplate.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM pg_trigger
                WHERE tgname = 'article_revision_immutable'
                  AND tgrelid = to_regclass('article_revision'))
                """, Boolean.class);
        assertTrue(trigger, "不可变触发器缺失 ⇒ 已发布正文可被改写（Go 侧会 panic）");
    }

    @Test
    @DisplayName("Go: article revision is immutable —— PG 触发器真的拒绝 UPDATE")
    void pgTriggerRejectsRevisionMutation() {
        String articleId = insertArticle("imm-1");
        insertRevision(articleId, 1L, "imm-1:r1", "sync-imm-1", "# first", 1000L);

        // 对应 Go 测试 community_article_test.go:224-227
        assertThrows(Exception.class, () -> jdbcTemplate.update(
                "UPDATE article_revision SET markdown = 'tampered' WHERE revision_id = ?", "imm-1:r1"),
                "PG 允许修改不可变 revision ⇒ 触发器没生效");
        assertThrows(Exception.class, () -> jdbcTemplate.update(
                "DELETE FROM article_revision WHERE revision_id = ?", "imm-1:r1"),
                "PG 允许删除不可变 revision ⇒ 触发器没生效");

        // 正文必须仍是原值
        String markdown = jdbcTemplate.queryForObject(
                "SELECT markdown FROM article_revision WHERE revision_id = ?", String.class, "imm-1:r1");
        assertEquals("# first", markdown);
    }

    @Test
    @DisplayName("article_publication 的 CHECK 约束生效（state 与 pointer_version）")
    void publicationCheckConstraintsAreEnforced() {
        assertThrows(Exception.class, () -> jdbcTemplate.update(
                "INSERT INTO article_publication (article_id, current_revision, pointer_version, state, last_event_id)"
                        + " VALUES ('chk-1', 'r1', 1, 'bogus-state', 'e1')"),
                "state CHECK 约束未生效");
        assertThrows(Exception.class, () -> jdbcTemplate.update(
                "INSERT INTO article_publication (article_id, current_revision, pointer_version, state, last_event_id)"
                        + " VALUES ('chk-2', 'r1', 0, 'published', 'e1')"),
                "pointer_version > 0 CHECK 约束未生效");
    }

    // ==================================================================
    // 3. 🔴 成功路径（U-5 / CASE-R11）：发布流水线的四步联动
    // ==================================================================
    //
    // Go 出处：service/article/rpc/internal/mqs/article_sync_result_consumer.go:74-153
    //   tx.Create(&revision)      → ① 冻结已批准正文为一条 revision
    //   tx.Save(&pointer)          → ② 指针指向该 revision，state=published，pointerVersion+1
    //   tx.Create(&domainEvent)    → ③ 域事件入 article_domain_outbox
    //   article.Status = PUBLISHED; SetSyncState(...); ExtPublishedOnce = "true"  → ④
    //
    // ⚠️ 之前**只有失败回滚路径**有真 PG 用例（domainOutboxFailureRollsBackEverything）
    //    ⇒ 「发布成功时四者是否真的同时落库」从未被验证过（U-5）。

    @Test
    @DisplayName("🔴 Go: acceptSyncResult **成功**路径 ⇒ revision + 指针 + 域事件 + 文章状态 四者同时落库")
    void acceptSyncResultSuccessPathPublishesEverything() {
        String articleId = "pub-ok-1";
        seedReviewingArticle(articleId, "pub-ok.md", "sync-pub-ok", 2000L);
        insertRevisionReadyOutbox(articleId, "sync-pub-ok", 2000L);

        ArticleSyncEventTypes.ArticleSyncResult result = new ArticleSyncEventTypes.ArticleSyncResult();
        result.articleId = articleId;
        result.eventId = "sync-pub-ok";
        result.op = ArticleSyncEventTypes.OP_UPSERT;
        result.versionMs = 2000L;
        result.success = true;

        ArticleAtomicService.PublishOutcome outcome =
                atomicService.acceptSyncResult(result, new ObjectMapperHolder().mapper());

        // ⚠️ 种子里 last_sync_reason = 'update' ⇒ Go 的通知条件
        //    （syncReason == create && ExtPublishedOnce == ""，:146-148）**不成立** ⇒ 不通知
        assertNull(outcome, "reason=update ⇒ 不该要求发通知（Go :146-148）");

        // ① revision：内容取自**冻结 payload**，不是当前 article 行
        Integer revisions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM article_revision WHERE article_id = ?", Integer.class, articleId);
        assertEquals(1, revisions, "成功路径必须落一条 revision");
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT revision FROM article_revision WHERE article_id = ?", Integer.class, articleId),
                "revision 号 = MAX(revision)+1（Go :109-111）");
        assertEquals(articleId + ":r1", jdbcTemplate.queryForObject(
                "SELECT revision_id FROM article_revision WHERE article_id = ?", String.class, articleId),
                "revision_id 形如 \"<articleId>:r<n>\"（Go :112）");
        assertEquals("# Approved", jdbcTemplate.queryForObject(
                "SELECT markdown FROM article_revision WHERE article_id = ?", String.class, articleId),
                "正文必须取**冻结 payload** 的 markdown（Go :118）");
        assertEquals("approved title", jdbcTemplate.queryForObject(
                "SELECT title FROM article_revision WHERE article_id = ?", String.class, articleId),
                "标题必须取**冻结 payload** 的 title（Go :115）");
        assertEquals("sync-pub-ok", jdbcTemplate.queryForObject(
                "SELECT sync_event_id FROM article_revision WHERE article_id = ?", String.class, articleId),
                "revision 记下这次同步的 event_id（Go :120）");
        assertNotNull(jdbcTemplate.queryForObject(
                "SELECT published_at FROM article_revision WHERE article_id = ?",
                java.time.OffsetDateTime.class, articleId), "published_at 必须落库（Go :120）");

        // ② 指针：current_revision 指向刚建的 revision，state=published，pointerVersion=1
        assertEquals(articleId + ":r1", jdbcTemplate.queryForObject(
                "SELECT current_revision FROM article_publication WHERE article_id = ?",
                String.class, articleId), "指针必须指向刚冻结的 revision（Go :123）");
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT pointer_version FROM article_publication WHERE article_id = ?",
                Long.class, articleId), "指针不存在时 pointerVersion 从 0+1=1 起（Go :121）");
        assertEquals("published", jdbcTemplate.queryForObject(
                "SELECT state FROM article_publication WHERE article_id = ?", String.class, articleId),
                "state = published（Go :124）");

        // ③ 域事件：aggregate_version 与指针版本一致，event_type = published
        Integer domainEvents = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM article_domain_outbox WHERE aggregate_id = ?", Integer.class, articleId);
        assertEquals(1, domainEvents, "成功路径必须落一条域事件（Go :127）");
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT aggregate_version FROM article_domain_outbox WHERE aggregate_id = ?",
                Long.class, articleId), "域事件的 aggregate_version = pointerVersion（Go :125）");
        // ⚠️ event_type **不是**裸的 "published"：Go 把 operation 拼进信封类型
        //    （community_article_event.go:91 `EventType: "community.article." + operation`），
        //    裸 "published" 只是它的一个入参（:131）。
        assertEquals("community.article.published", jdbcTemplate.queryForObject(
                "SELECT event_type FROM article_domain_outbox WHERE aggregate_id = ?",
                String.class, articleId), "域事件的 event_type = community.article.published（Go :91）");
        assertEquals("community.article.sync-pub-ok.published", jdbcTemplate.queryForObject(
                "SELECT event_id FROM article_domain_outbox WHERE aggregate_id = ?",
                String.class, articleId), "域事件 id = community.article.<eventId>.<operation>（Go :65）");
        assertEquals(jdbcTemplate.queryForObject(
                "SELECT last_event_id FROM article_publication WHERE article_id = ?",
                String.class, articleId), jdbcTemplate.queryForObject(
                "SELECT event_id FROM article_domain_outbox WHERE aggregate_id = ?",
                String.class, articleId),
                "指针的 last_event_id 必须指向这条域事件（Go :124）");

        // ④ 文章本身：PUBLISHED + ext_info 状态机
        ArticleEntity after = articleMapper.selectById(articleId);
        assertNotNull(after, "文章行必须还在");
        assertEquals(com.rag.article.article.proto.ArticleStatus.PUBLISHED_VALUE, after.getStatus(),
                "状态必须变成 PUBLISHED（Go :150）");
        assertNotNull(after.getExtInfo(), "ext_info 回读为 null ⇒ jsonb 映射没生效");
        assertEquals("published", after.getExtInfo().get(ArticleSyncEventTypes.EXT_PUBLISH_STAGE),
                "publish_stage = published（Go :151 的 SetSyncState 第 1 参）");
        assertEquals("succeeded", after.getExtInfo().get(ArticleSyncEventTypes.EXT_RECO_SYNC_STATE),
                "reco_sync_state = succeeded（Go :151 的 SetSyncState 第 2 参）");
        assertEquals("true", after.getExtInfo().get(ArticleSyncEventTypes.EXT_PUBLISHED_ONCE),
                "published_once = \"true\"（Go :152）");
        assertEquals("sync-pub-ok", after.getExtInfo().get(ArticleSyncEventTypes.EXT_LAST_SYNC_EVENT_ID),
                "last_sync_event_id 推进到本次事件（Go :151）");
        assertEquals("2000", after.getExtInfo().get(ArticleSyncEventTypes.EXT_LAST_SYNC_VERSION),
                "last_sync_version_ms 推进（Go :151）");
    }

    @Test
    @DisplayName("Go: 首次发布（reason=create）⇒ 返回 PublishOutcome 要求发通知；**已发布过** ⇒ 不再通知")
    void acceptSyncResultNotifiesOnlyOnFirstPublish() {
        // ① 首次发布：last_sync_reason = create 且 published_once 为空 ⇒ 要通知
        String firstId = "pub-notify-1";
        seedReviewingArticleWithReason(firstId, "n1.md", "sync-n1", 3000L,
                ArticleSyncEventTypes.REASON_CREATE);
        insertRevisionReadyOutbox(firstId, "sync-n1", 3000L);

        ArticleSyncEventTypes.ArticleSyncResult first = new ArticleSyncEventTypes.ArticleSyncResult();
        first.articleId = firstId;
        first.eventId = "sync-n1";
        first.op = ArticleSyncEventTypes.OP_UPSERT;
        first.versionMs = 3000L;
        first.success = true;

        ArticleAtomicService.PublishOutcome outcome =
                atomicService.acceptSyncResult(first, new ObjectMapperHolder().mapper());

        assertNotNull(outcome, "reason=create 且从未发布过 ⇒ 必须要求发通知（Go :146-148）");
        assertTrue(outcome.notifyRequired(), "notifyRequired 必须为 true");
        assertEquals("1001", outcome.authorId(), "通知对象是文章作者（Go :147 notifyAuthor = article.AuthorID）");

        // ② 再次发布（同一篇文章换事件）：published_once 已是 "true" ⇒ **不再**通知
        String secondId = "pub-notify-2";
        seedReviewingArticleWithReason(secondId, "n2.md", "sync-n2", 4000L,
                ArticleSyncEventTypes.REASON_CREATE);
        // 预置 published_once = "true"（模拟「这篇已经发布过一次」）
        jdbcTemplate.update("UPDATE article SET ext_info = jsonb_set(ext_info, '{published_once}', '\"true\"') "
                + "WHERE id = ?", secondId);
        insertRevisionReadyOutbox(secondId, "sync-n2", 4000L);

        ArticleSyncEventTypes.ArticleSyncResult second = new ArticleSyncEventTypes.ArticleSyncResult();
        second.articleId = secondId;
        second.eventId = "sync-n2";
        second.op = ArticleSyncEventTypes.OP_UPSERT;
        second.versionMs = 4000L;
        second.success = true;

        ArticleAtomicService.PublishOutcome outcome2 =
                atomicService.acceptSyncResult(second, new ObjectMapperHolder().mapper());

        assertNull(outcome2, "published_once 已非空 ⇒ 不该再通知（Go :146 的第二个条件）");
        // ⚠️ 但**发布本身**照样发生（published_once 只挡通知，不挡发布）
        assertEquals(com.rag.article.article.proto.ArticleStatus.PUBLISHED_VALUE,
                articleMapper.selectById(secondId).getStatus(),
                "published_once 只挡通知，**不挡发布**（Go :147 只影响 notifyAuthor 的赋值）");
    }

    /**
     * 🔴 Go 侧通知标题与 revision 标题<b>来自两个不同的源</b>，不可混用。
     *
     * <h2>Go 出处（两处，消费点）</h2>
     * <ul>
     *   <li><b>revision 的标题 = 冻结 payload</b>：
     *       {@code article_sync_result_consumer.go:115} {@code Title: approved.Title}
     *       —— {@code approved} 是从 outbox payload 反序列化出来的<b>冻结输入</b>。</li>
     *   <li><b>通知的标题 = 当前行</b>：
     *       {@code article_sync_result_consumer.go:147}
     *       {@code notifyAuthor, notifyTitle = article.AuthorID, article.Title}
     *       —— {@code article} 是<b>本次事务里锁住的那一行</b>，不是冻结输入。</li>
     * </ul>
     *
     * <h2>为什么两者可以不同（可达性，不是理论）</h2>
     * 作者在 REVIEWING 期间改标题：{@code update_article_logic.go:142-144} <b>立即把新标题写进行</b>，
     * 而已冻结的 payload <b>不跟着变</b>。
     * <br>⚠️ 关键：改标题<b>不会</b>立刻推进 {@code last_sync_event_id} ——
     * {@code enqueueReview}（{@code update_article_logic.go:232-245}）只推 Kafka，
     * 新 event_id 要等审核回调（{@code article_consumer.go:107}）才写。
     * <br>⇒ 在「标题已改」到「新 event_id 写入」这个窗口里，旧同步结果到达：
     * 双重判据（{@code last_sync_event_id} + {@code last_sync_version_ms}）<b>依然成立</b>
     * ⇒ 发布照常发生，但 Go 通知里用的是<b>新标题</b>。
     *
     * <h2>为什么这条断言有分量</h2>
     * 同时断言 revision 标题 = 冻结值、通知标题 = 当前行值 ⇒
     * 两个源被<b>分别</b>钉死，任何一边取错源都会红。
     */
    @Test
    @DisplayName("🔴 Go: revision 标题取**冻结 payload**，通知标题取**当前行**（两个源不可混用）")
    void acceptSyncResultUsesFrozenTitleForRevisionButCurrentRowTitleForNotification() {
        String articleId = "pub-title-1";
        // 当前行标题 = 作者在审核期间改过的新标题
        seedReviewingArticleWithReason(articleId, "t.md", "sync-title-1", 5000L,
                ArticleSyncEventTypes.REASON_CREATE, "作者改过的标题");
        // 冻结 payload 的标题 = 入队那一刻的旧标题
        insertRevisionReadyOutbox(articleId, "sync-title-1", 5000L, "冻结时的标题");

        ArticleSyncEventTypes.ArticleSyncResult result = new ArticleSyncEventTypes.ArticleSyncResult();
        result.articleId = articleId;
        result.eventId = "sync-title-1";
        result.op = ArticleSyncEventTypes.OP_UPSERT;
        result.versionMs = 5000L;
        result.success = true;

        ArticleAtomicService.PublishOutcome outcome =
                atomicService.acceptSyncResult(result, new ObjectMapperHolder().mapper());

        // ① revision：冻结 payload 的标题（Go :115）
        assertEquals("冻结时的标题", jdbcTemplate.queryForObject(
                "SELECT title FROM article_revision WHERE article_id = ?", String.class, articleId),
                "revision 的标题必须取**冻结 payload**（Go :115 Title: approved.Title）");

        // ② 通知：当前行的标题（Go :147）
        assertNotNull(outcome, "reason=create 且从未发布过 ⇒ 必须返回 PublishOutcome（Go :146-148）");
        assertEquals("作者改过的标题", outcome.title(),
                "通知标题必须取**当前行**（Go :147 article.Title），不是冻结 payload 的标题");
    }

    /** 与 {@link #seedReviewingArticle} 同款，但可指定 {@code last_sync_reason}（通知条件的判据）。 */
    private void seedReviewingArticleWithReason(String articleId, String contentPath,
                                                String eventId, long versionMs, String reason) {
        seedReviewingArticleWithReason(articleId, contentPath, eventId, versionMs, reason, "title");
    }

    /**
     * 同上，并可指定<b>当前行的标题</b>。
     *
     * <p>Go 出处：{@code update_article_logic.go:142-144}（改标题只改行，不改已冻结的 payload）
     * ⇒ 行标题与冻结标题可以不一致。
     */
    private void seedReviewingArticleWithReason(String articleId, String contentPath,
                                                String eventId, long versionMs, String reason,
                                                String rowTitle) {
        jdbcTemplate.update("""
                INSERT INTO article (id, title, brief, content, cover_image_url, manual_type_tag,
                                     secondary_tags, author_id, status, view_count, like_count,
                                     comment_count, share_count, ext_info, created_at, updated_at)
                VALUES (?, ?, 'brief', ?, '', 'tag', '[]'::jsonb, '1001', 3, 0, 0, 0, 0,
                        jsonb_build_object(
                          'reco_sync_state', 'pending',
                          'last_sync_event_id', ?,
                          'last_sync_version_ms', ?::text,
                          'last_sync_reason', ?,
                          'pending_sync_reason', ?,
                          'review_nonce', ?),
                        now(), now())
                """, articleId, rowTitle, contentPath, eventId, String.valueOf(versionMs), reason, reason, eventId);
    }

    // ==================================================================
    // 4. 事务回滚（Go: TestArticleDomainOutboxFailureRollsBackPublication）
    // ==================================================================

    @Test
    @DisplayName("Go: 域事件写失败 ⇒ revision/指针/状态三者全部回滚（真实回滚，非静默）")
    void domainOutboxFailureRollsBackEverything() throws Exception {
        String articleId = "rollback-article";
        seedReviewingArticle(articleId, "rollback.md", "sync-rollback", 1000L);
        insertRevisionReadyOutbox(articleId, "sync-rollback", 1000L);

        // ⚠️ 前置断言：acceptSyncResult 里有**五处静默 no-op**（Go :75-94），
        //    任一命中就 return nil，表现为"什么都没发生"而不是报错。
        //    不先把前置条件钉死，失败时无法区分"回滚没生效"和"压根没进发布分支"。
        ArticleEntity before = articleMapper.selectById(articleId);
        assertNotNull(before, "seed 失败");
        assertEquals(com.rag.article.article.proto.ArticleStatus.REVIEWING_VALUE, before.getStatus(),
                "种子文章不是 REVIEWING ⇒ 会走 ④ no-op");
        assertNotNull(before.getExtInfo(), "ext_info 回读为 null ⇒ @TableName(autoResultMap) 或 typeHandler 没生效");
        assertEquals("sync-rollback", before.getExtInfo().get(ArticleSyncEventTypes.EXT_LAST_SYNC_EVENT_ID),
                "last_sync_event_id 回读不到 ⇒ 会走 ② no-op（" + before.getExtInfo() + "）");
        assertEquals("1000", before.getExtInfo().get(ArticleSyncEventTypes.EXT_LAST_SYNC_VERSION),
                "last_sync_version_ms 回读不到 ⇒ 会走 ② no-op");
        assertNotNull(syncOutboxMapper.selectByEventId("sync-rollback"), "冻结 payload 行不存在 ⇒ 会走 ⑤ 抛错分支而非回滚分支");

        // 用触发器注入域事件写入失败（Go community_article_test.go:254-259 同款手法）
        jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION deny_domain_event() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'injected domain outbox failure'; END $$;
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER deny_domain_event BEFORE INSERT ON article_domain_outbox
                FOR EACH ROW EXECUTE FUNCTION deny_domain_event()
                """);
        try {
            ArticleSyncEventTypes.ArticleSyncResult result = new ArticleSyncEventTypes.ArticleSyncResult();
            result.articleId = articleId;
            result.eventId = "sync-rollback";
            result.op = ArticleSyncEventTypes.OP_UPSERT;
            result.versionMs = 1000L;
            result.success = true;

            assertThrows(Exception.class, () -> atomicService.acceptSyncResult(result, new ObjectMapperHolder().mapper()),
                    "域事件写失败却被当成发布成功");

            // 三者都必须为空/未变
            Integer revisions = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM article_revision WHERE article_id = ?", Integer.class, articleId);
            assertEquals(0, revisions, "revision 在失败事务后残留 ⇒ 没有真的回滚");

            Integer pointers = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM article_publication WHERE article_id = ?", Integer.class, articleId);
            assertEquals(0, pointers, "指针在失败事务后残留 ⇒ 没有真的回滚");

            ArticleEntity article = articleMapper.selectById(articleId);
            assertNotNull(article);
            assertEquals(com.rag.article.article.proto.ArticleStatus.REVIEWING_VALUE, article.getStatus(),
                    "业务状态在没有域事件的情况下被改成 PUBLISHED");
        } finally {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS deny_domain_event ON article_domain_outbox");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS deny_domain_event()");
        }
    }

    @Test
    @DisplayName("@Transactional 真的生效：ArticleAtomicService 必须是 AOP 代理")
    void atomicServiceIsAopProxied() {
        assertTrue(org.springframework.aop.support.AopUtils.isAopProxy(atomicService),
                "ArticleAtomicService 不是 AOP 代理 ⇒ @Transactional 静默失效，"
                        + "retract/soft-delete/outbox 三步会各自 auto-commit");
    }

    // ==================================================================
    // 3. 域事件顺序保证（UNIQUE(aggregate_id, aggregate_version)）
    // ==================================================================

    @Test
    @DisplayName("Go: 同一 (aggregate_id, aggregate_version) 不能有两条域事件")
    void domainOutboxUniqueConstraintOrdersEvents() {
        jdbcTemplate.update("INSERT INTO article_domain_outbox "
                + "(event_id, aggregate_id, aggregate_version, event_type, payload, status, retry_count, created_at, updated_at)"
                + " VALUES ('e1', 'agg-1', 1, 'community.article.published', '{}'::jsonb, 0, 0, now(), now())");

        assertThrows(Exception.class, () -> jdbcTemplate.update("INSERT INTO article_domain_outbox "
                        + "(event_id, aggregate_id, aggregate_version, event_type, payload, status, retry_count, created_at, updated_at)"
                        + " VALUES ('e2', 'agg-1', 1, 'community.article.retracted', '{}'::jsonb, 0, 0, now(), now())"),
                "同一版本号能插入两条域事件 ⇒ 事件顺序保证失效");

        // 不同版本号可以共存
        jdbcTemplate.update("INSERT INTO article_domain_outbox "
                + "(event_id, aggregate_id, aggregate_version, event_type, payload, status, retry_count, created_at, updated_at)"
                + " VALUES ('e3', 'agg-1', 2, 'community.article.retracted', '{}'::jsonb, 0, 0, now(), now())");
        assertEquals(2, countDomainEvents("agg-1"));
    }

    // ==================================================================
    // 4. jsonb 往返（验证 typeHandler + autoResultMap 真的生效）
    // ==================================================================

    @Test
    @DisplayName("jsonb 往返：secondary_tags(数组) 与 ext_info(对象, 值恒为字符串) 都保真")
    void jsonbRoundTripsFaithfully() {
        String articleId = "jsonb-1";
        // ⚠️ 这里**不能**先用 insertArticle(...) 占位：本用例要验的是 jsonb 列经
        //    typeHandler 写入后的回读，占位行会让第二次 insert 撞 DuplicateKey，
        //    报错看起来像被测代码的问题（第一版就踩了）。
        ArticleEntity article = new ArticleEntity();
        article.setId(articleId);
        article.setTitle("jsonb title");
        article.setAuthorId("1001");
        article.setStatus(com.rag.article.article.proto.ArticleStatus.REVIEWING_VALUE);
        article.setSecondaryTags(List.of("alpha", "中文标签", "tag/with\"quote"));
        article.setExtInfo(Map.of(
                ArticleSyncEventTypes.EXT_LAST_SYNC_VERSION, "1792670400000",
                ArticleSyncEventTypes.EXT_PUBLISHED_ONCE, "true",   // ⚠️ 字符串 "true"，不是布尔
                ArticleSyncEventTypes.EXT_RECO_SYNC_STATE, "pending"));
        article.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        article.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        articleMapper.insert(article);

        ArticleEntity loaded = articleMapper.selectById(articleId);
        assertNotNull(loaded);
        // ⚠️ 这条断言证明 @TableName(autoResultMap = true) 生效：
        //    少了它，读 jsonb 得到的是 PGobject，映射成 List/Map 会直接失败。
        assertEquals(List.of("alpha", "中文标签", "tag/with\"quote"), loaded.getSecondaryTags());
        assertEquals("1792670400000", loaded.getExtInfo().get(ArticleSyncEventTypes.EXT_LAST_SYNC_VERSION));
        assertEquals("true", loaded.getExtInfo().get(ArticleSyncEventTypes.EXT_PUBLISHED_ONCE));
        assertEquals("pending", loaded.getExtInfo().get(ArticleSyncEventTypes.EXT_RECO_SYNC_STATE));

        // 列类型必须是 jsonb（不是 text / json）—— Go 侧 struct tag 写死 type:jsonb
        String dataType = jdbcTemplate.queryForObject(
                "SELECT data_type FROM information_schema.columns "
                        + "WHERE table_name = 'article' AND column_name = 'secondary_tags'",
                String.class);
        assertEquals("jsonb", dataType);
    }

    // ==================================================================
    // 5. 软删除可见性（FindOne vs FindOneUnscoped）
    // ==================================================================

    @Test
    @DisplayName("软删除：selectById 看不到已删行，selectByIdUnscoped 能看到（Go 的两种方法）")
    void softDeleteVisibilityMatchesGoMethodSplit() {
        String articleId = insertArticle("soft-del-1");
        assertNotNull(articleMapper.selectById(articleId));
        assertNotNull(articleMapper.selectByIdUnscoped(articleId));

        assertEquals(1, articleMapper.softDelete(articleId));

        // Go: FindOne 带软删除过滤 ⇒ 查不到 ⇒ GetArticle/DeleteArticle 返回 NotFound
        assertNull(articleMapper.selectById(articleId),
                "已删文章仍能被 selectById 查到 ⇒ Go 的 NotFound 语义漂移");
        // Go: FindOneUnscoped 是同步结果消费者的唯一入口 ⇒ 必须仍能看到
        assertNotNull(articleMapper.selectByIdUnscoped(articleId),
                "已删文章无法被 Unscoped 查询 ⇒ 迟到同步结果无法落库");

        // 二次删除是 no-op（Go: 影响 0 行，不是错误）
        assertEquals(0, articleMapper.softDelete(articleId));
    }

    @Test
    @DisplayName("作者视角 IncrViewCount 与公开视角 IncrPublicViewCount 是**两套不同**的谓词")
    void incrViewCountMatchesGoAuthorPathSemantics() {
        // 未发布（status=REVIEWING）且无指针
        String draftId = insertArticle("vp-draft");

        // ⚠️ 作者路径 incrViewCount 在 Go 侧**根本没有可见性谓词**
        //    （article.go:121-124：`Where("id = ?")` + UpdateColumn，无 status / 无 publication 判定），
        //    所以草稿也能加 —— 这正是"作者视角"与"读者视角"刻意分家的原因。
        //    第一版这里断言 0，是把公开路径的规则错套到了作者路径上。
        assertEquals(1, articleMapper.incrViewCount(draftId),
                "作者路径的浏览量自增在 Go 侧不带任何可见性过滤，草稿也应当被计数");

        // 公开路径才走带谓词的 incrPublicViewCount：不可见 ⇒ 影响 0 行
        assertEquals(0, incrPublicViaMapper(draftId), "不可见的草稿竟能计公开阅读量");

        // soft-deleted 行两条路径都不该被计入（作者路径靠 deleted_at IS NULL 兜住）
        assertEquals(1, articleMapper.softDelete(draftId));
        assertEquals(0, articleMapper.incrViewCount(draftId), "已软删的行仍被计入浏览量");

        // PUBLISHED + 无指针 ⇒ legacy 可见，公开路径应放行
        String legacyId = insertArticle("vp-legacy");
        jdbcTemplate.update("UPDATE article SET status = 2 WHERE id = ?", legacyId);
        assertEquals(1, incrPublicViaMapper(legacyId), "legacy PUBLISHED 文章应可计公开阅读量");
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT view_count FROM article WHERE id = ?", Integer.class, legacyId));
    }

    /**
     * 公开阅读量自增必须走<b>真正下线的</b> {@link ArticlePublicMapper}，
     * 而不是测试里另写一份 SQL —— 后者只能证明 SQL 本身对，
     * 证明不了"Mapper 上的谓词与 select 侧一致"。
     */
    private int incrPublicViaMapper(String articleId) {
        return publicMapper.incrPublicViewCount(articleId);
    }

    // ==================================================================
    // 6. 本次语义验证新增：两个需要**真代理**才能发现的回归。
    //    两者在修复前都是 79 条全绿下存活的——那正是它们需要被补上的原因。
    // ==================================================================

    /**
     * 🔴 S-1：公开阅读计数必须能真的提交。
     *
     * <p>乊一版实现给 {@code getPublicArticle} 打了 {@code @Transactional(readOnly = true)}，
     * 而方法内含一条 {@code UPDATE} → pgjdbc 发 {@code BEGIN READ ONLY}
     * → PG 拒：{@code 25006 cannot execute UPDATE in a read-only transaction} ⇒ 每次调用均失败。
     *
     * <p>⚠️ <b>为何以前的测试拿不到</b>：
     * {@code ArticlePublicDomainServiceViewTest} 全 Mockito（不经过代理 ⇒ 事务<b>根本不生效</b>）；
     * 本类之前也没有经代理调用过 {@code getPublicArticle(.., true)}。
     * 本用例直接自注入真实 Bean（AOP 代理），所以它能真的触发事务。
     */
    @Test
    @DisplayName("🔴 Go: 公开详情计阅读量是能提交的（不得落在 read-only 事务里）")
    void publicViewCountIncrementActuallyCommits() {
        String articleId = "pubview-1";
        insertArticle(articleId);
        // PUBLISHED + 指针指向一个真实 revision → 走「不读对象存储」的分支
        jdbcTemplate.update("UPDATE article SET status = 2 WHERE id = ?", articleId);
        insertRevision(articleId, 1L, "pubview-1:r1", "sync-pubview-1", "# Approved", 1000L);
        jdbcTemplate.update("""
                INSERT INTO article_publication (article_id, current_revision, pointer_version, state, last_event_id)
                VALUES (?, 'pubview-1:r1', 1, 'published', 'evt-1')
                """, articleId);

        assertEquals(0, viewCountOf(articleId), "前置校验：初始阅读量应为 0");

        PublicArticleView view = publicService.getPublicArticle(articleId, true);

        assertNotNull(view, "公开详情应正常返回；若 readOnly 事务未移除，"
                + "这里会是 PSQLException: 25006 cannot execute UPDATE in a read-only transaction");
        assertEquals(1L, viewCountOf(articleId),
                "PG 里的 view_count 必须真的 +1（说明 UPDATE 已提交，而不是被只放在内存里）");
        assertEquals(1, view.viewCount, "响应里的 view_count 是内存 +1 后的值");
        assertEquals("# Approved", view.markdown, "正文取冻结的 revision.markdown");
    }

    /** 同一个公开详情，不计阅读量时不应发生任何写入。 */
    @Test
    @DisplayName("Go: 公开详情 incrView=false 时不计数")
    void publicViewCountNotIncrementedWhenNotRequested() {
        String articleId = "pubview-2";
        insertArticle(articleId);
        jdbcTemplate.update("UPDATE article SET status = 2 WHERE id = ?", articleId);
        insertRevision(articleId, 1L, "pubview-2:r1", "sync-pubview-2", "# Approved", 2000L);
        jdbcTemplate.update("""
                INSERT INTO article_publication (article_id, current_revision, pointer_version, state, last_event_id)
                VALUES (?, 'pubview-2:r1', 1, 'published', 'evt-2')
                """, articleId);

        PublicArticleView view = publicService.getPublicArticle(articleId, false);

        assertNotNull(view);
        assertEquals(0, viewCountOf(articleId), "不请求计数时不应写入");
    }

    // =====================================================================
    // 🔴 DRIFT-ARTICLE-RPC-003：公开读的 DB 失败 ⇒ 必须是 INTERNAL（5001），不是 UNKNOWN（1015）
    // =====================================================================
    //
    // Go 出处（逐行读过消费点）：
    //   model/article_public.go:34-57        FindPublicOne —— 三处 DB 失败返回**同一种** error
    //   internal/logic/get_article_logic.go:129-135
    //       if err == gorm.ErrRecordNotFound { return nil, status.Error(codes.NotFound, "article not published") }
    //       if err != nil { …; return nil, status.Error(codes.Internal, "read publication failed") }
    //   internal/logic/get_article_logic.go:157-163
    //       increased, err := …IncrPublicViewCount(l.ctx, in.ArticleId)
    //       if err != nil { …; return nil, status.Error(codes.Internal, "increment public view failed") }
    //       if !increased { return nil, status.Error(codes.NotFound, "article no longer published") }
    //
    // ⚠️ 修之前的形状：DataAccessException 一路冒到 ArticleServiceGrpcImpl 的 catch(RuntimeException)
    //    ⇒ 落 UNKNOWN ⇒ api 侧 default 分支 ⇒ 业务码 **1015**（Go 是 5001）。
    //    ⇒ DB 抖动被计成「服务繁忙/限流」，两套告警口径混用。
    //
    // ⚠️ 为什么**不能用 Mockito 把 mapper 打桩**：那样测不到「真 SQL 失败会不会被翻译成 INTERNAL」。
    //    本组用例让**真 PG 真的报错**（表改名 / CHECK 约束），走真 MyBatis + 真异常翻译。

    @Test
    @DisplayName("🔴 Go: 公开读的 DB 失败 ⇒ INTERNAL \"read publication failed\"（不是 UNKNOWN ⇒ 业务码 1015）")
    void publicReadDbFailureIsInternalNotUnknown() {
        String articleId = "pubfail-1";
        insertArticle(articleId);
        jdbcTemplate.update("UPDATE article SET status = 2 WHERE id = ?", articleId);
        insertRevision(articleId, 1L, "pubfail-1:r1", "sync-pubfail-1", "# Approved", 1000L);
        jdbcTemplate.update("""
                INSERT INTO article_publication (article_id, current_revision, pointer_version, state, last_event_id)
                VALUES (?, 'pubfail-1:r1', 1, 'published', 'evt-1')
                """, articleId);

        // 让公开读用到的表**真的不存在** ⇒ 真 SQL 错误（42P01）⇒ 真 DataAccessException
        jdbcTemplate.execute("ALTER TABLE article_publication RENAME TO article_publication_hidden");
        try {
            ArticleExceptions.Rpc ex = assertThrows(ArticleExceptions.Rpc.class,
                    () -> publicService.getPublicArticle(articleId, false),
                    "公开读的 DB 失败必须被翻译成 gRPC INTERNAL，而不是裸 UNKNOWN");
            assertEquals(io.grpc.Status.Code.INTERNAL, ex.getCode(),
                    "Go: status.Error(codes.Internal, \"read publication failed\")");
            assertEquals("read publication failed", ex.getMessage(),
                    "Go 的文案逐字：get_article_logic.go:133");
        } finally {
            jdbcTemplate.execute("ALTER TABLE article_publication_hidden RENAME TO article_publication");
        }
    }

    @Test
    @DisplayName("🔴 Go: 公开读计阅读量的 DB 失败 ⇒ INTERNAL \"increment public view failed\"（不是 UNKNOWN）")
    void publicViewIncrementDbFailureIsInternalNotUnknown() {
        String articleId = "pubfail-2";
        insertArticle(articleId);
        jdbcTemplate.update("UPDATE article SET status = 2 WHERE id = ?", articleId);
        insertRevision(articleId, 1L, "pubfail-2:r1", "sync-pubfail-2", "# Approved", 2000L);
        jdbcTemplate.update("""
                INSERT INTO article_publication (article_id, current_revision, pointer_version, state, last_event_id)
                VALUES (?, 'pubfail-2:r1', 1, 'published', 'evt-2')
                """, articleId);

        // 只让 **UPDATE** 失败、SELECT 仍成功 ⇒ 精确命中 incrPublicViewCount 这一条路径
        // （若改名 article 表，前面的 SELECT 会先失败 ⇒ 测不到计数那条路径）
        // ⚠️ NOT VALID 是必需的：既有行的 view_count=0 不满足 view_count<0，
        //    不加 NOT VALID 的话 ALTER 自己就会失败。
        jdbcTemplate.execute(
                "ALTER TABLE article ADD CONSTRAINT chk_force_view_fail CHECK (view_count < 0) NOT VALID");
        try {
            ArticleExceptions.Rpc ex = assertThrows(ArticleExceptions.Rpc.class,
                    () -> publicService.getPublicArticle(articleId, true),
                    "计阅读量的 DB 失败必须被翻译成 gRPC INTERNAL");
            assertEquals(io.grpc.Status.Code.INTERNAL, ex.getCode(),
                    "Go: status.Error(codes.Internal, \"increment public view failed\")");
            assertEquals("increment public view failed", ex.getMessage(),
                    "Go 的文案逐字：get_article_logic.go:160");
        } finally {
            jdbcTemplate.execute("ALTER TABLE article DROP CONSTRAINT IF EXISTS chk_force_view_fail");
        }
    }

    /**
     * 🔴 S-2：作者视角列表在 {@code sort_by} 为空时必须是 <b>DESC</b>。
     *
     * <p>Go 出处：{@code internal/model/article.go:161-176}（消费点）——
     * <pre>
     * order := "created_at desc"
     * if opt.SortBy != "" {
     *     switch opt.SortBy { ... }
     *     if opt.Desc { order += " desc" } else { order += " asc" }
     * }
     * </pre>
     * 方向拼接整体位于 {@code if opt.SortBy != ""} 内部 ⇒ {@code SortBy} 为空时 {@code Desc}
     * 被完全忽略，恒定 {@code created_at desc}。
     * 而 proto3 的 {@code sort_by} / {@code desc} 默认值正好是 {@code ""} / {@code false}
     * ⇒ <b>任何不显式设排序的调用（最常见形态）都走这条路径</b>。
     *
     * <p>乊一版是无条件方向（{@code <when test="desc">DESC</when><otherwise>ASC</otherwise>}）
     * → 默认调用退化成 ASC，<b>整页顺序颠倒</b>。
     */
    @Test
    @DisplayName("🔴 Go: author-view 默认排序（sort_by 不设、desc=false）= created_at DESC")
    void authorViewDefaultsToNewestFirst() {
        String a = "sort-old", b = "sort-mid", c = "sort-new";
        insertArticleWithCreatedAt(a, "2026-01-01T00:00:00Z");
        insertArticleWithCreatedAt(b, "2026-02-01T00:00:00Z");
        insertArticleWithCreatedAt(c, "2026-03-01T00:00:00Z");

        // sortBy = "" 与 desc = false，即 proto3 的默认调用形态
        List<ArticleEntity> page =
                articleMapper.selectAuthorView(null, null, null, "", false, 10, 0L);

        assertEquals(List.of(c, b, a), page.stream().map(ArticleEntity::getId).toList(),
                "Go 在 SortBy==\"\" 时恒定 created_at DESC（新在前）；"
                        + "若得到 ASC，说明方向被提前到 SortBy 为空之外了");
    }

    /** 对照：{@code sort_by} 非空时，desc 标志生效（Go 的另一个分支）。 */
    @Test
    @DisplayName("Go: author-view sort_by=view_count 时 desc 标志生效")
    void authorViewHonoursDescWhenSortByPresent() {
        insertArticleWithViewCount("vv-low", 1);
        insertArticleWithViewCount("vv-high", 9);

        List<ArticleEntity> asc = articleMapper.selectAuthorView(
                null, null, null, "view_count", false, 10, 0L);
        List<ArticleEntity> desc = articleMapper.selectAuthorView(
                null, null, null, "view_count", true, 10, 0L);

        assertEquals(List.of("vv-low", "vv-high"), asc.stream().map(ArticleEntity::getId).toList(),
                "sort_by 非空 + desc=false → ASC");
        assertEquals(List.of("vv-high", "vv-low"), desc.stream().map(ArticleEntity::getId).toList(),
                "sort_by 非空 + desc=true → DESC");
    }

    // ==================================================================
    // 7. article.updated_at 自动推进（Go gorm:"autoUpdateTime" 的 Java 对应物）
    // ==================================================================

    /**
     * 🔴 S-3：{@code updateById} 必须真的推进 {@code updated_at}。
     *
     * <p><b>Go 出处</b>：声明点 {@code service/article/rpc/internal/model/article.go:31}
     * {@code UpdatedAt time.Time `gorm:"autoUpdateTime"`}；
     * <b>消费点</b> {@code gorm@v1.31.2/callbacks/update.go:243-259}：
     * <pre>{@code
     * if field.AutoUpdateTime > 0 && value[field.Name] == nil && value[field.DBName] == nil {
     *     if v, ok := selectColumns[field.DBName]; (ok && v) || !ok {
     *         now := stmt.DB.NowFunc(); assignValue(field, now)
     *         set = append(set, clause.Assignment{Column: …, Value: now})
     * }}}</pre>
     *
     * <p><b>修复前为什么静默不推进</b>：{@code ArticleEntity.updatedAt} 没有
     * {@code @TableField(fill = INSERT_UPDATE)}，而 {@code updateById} 的默认
     * {@code updateStrategy = NOT_NULL} 会把实体里那个<b>从库里读回来的陈旧值原样写回</b>
     * ⇒ {@code updated_at} 永远停在建行那一刻。实测手段：把 {@code updated_at}
     * 手工推到过去，再走一次真实更新，看它有没有往前走。
     *
     * <p>⚠️ <b>本用例的形状是被缺陷决定的</b>：必须<b>先读出实体</b>（携带陈旧值）
     * 再 {@code updateById} 回去 —— 直接 new 一个 {@code updatedAt = null} 的实体
     * 测不出问题，因为那样 {@code NOT_NULL} 会跳过该列，恰好「看起来是好的」。
     */
    @Test
    @DisplayName("🔴 Go: updateById 推进 updated_at（autoUpdateTime 的 Java 对应）")
    void updateByIdAdvancesUpdatedAt() {
        String articleId = "ts-advance";
        insertArticle(articleId);
        // 把 updated_at 推到 2020，让「有没有前进」无歧义
        jdbcTemplate.update("UPDATE article SET updated_at = TIMESTAMPTZ '2020-01-01 00:00:00+00' "
                + "WHERE id = ?", articleId);
        OffsetDateTime before = updatedAtOf(articleId);

        // 关键：走真实读-改-写路径（域服务 8 处写路径全是这个形状）
        ArticleEntity loaded = articleMapper.selectById(articleId);
        assertEquals(before, loaded.getUpdatedAt(), "前置校验：实体确实携带了陈旧值");

        loaded.setBrief("brief-changed");
        articleMapper.updateById(loaded);

        OffsetDateTime after = updatedAtOf(articleId);
        assertTrue(after.isAfter(before),
                "updated_at 必须前进（GORM autoUpdateTime 的语义）；"
                        + "若仍等于 " + before + "，说明 MetaObjectHandler 没触发"
                        + "或 @TableField(fill=INSERT_UPDATE) 没生效 —— 注意这两者都是静默的");
        assertEquals("brief-changed", jdbcTemplate.queryForObject(
                "SELECT brief FROM article WHERE id = ?", String.class, articleId),
                "同一句 UPDATE 里的业务列必须真的写进去（否则本用例可能只是在测一个空转的语句）");
    }

    /**
     * 🔴 S-4：{@code UpdateColumn} 式的「只改一列」<b>不许</b>顺带推进 {@code updated_at}。
     *
     * <p><b>这是 S-3 的镜像约束</b>，也是 Go 侧 {@code !stmt.SkipHooks} 那一半：
     * {@code UpdateColumn} 会置 {@code SkipHooks=true}，{@code autoUpdateTime} 因此不生效
     * ——Go 侧只有两处这样的路径：
     * {@code internal/model/article.go:123} 与 {@code internal/model/article_public.go:159}，
     * 两处都是 {@code UpdateColumn("view_count", gorm.Expr("view_count + 1", 1))}。
     *
     * <p>Java 侧的对应实现是<b>原生 {@code @Update} 注解</b>（{@link ArticleMapper#incrViewCount}），
     * 完全绕开 MyBatis-Plus ⇒ handler 天然不会在它上面触发。
     *
     * <p>⚠️ <b>为什么必须单独锁</b>：若有人日后把 {@code incrViewCount} 改写成
     * {@code LambdaUpdateWrapper} + {@code update(null, wrapper)}，或把 MetaObjectHandler
     * 改成无条件改写，两条用例会<b>只有这一条变红</b>。只测 S-3 抓不到那个改动。
     */
    @Test
    @DisplayName("🔴 Go: view_count 自增不推进 updated_at（等价于 UpdateColumn 的 SkipHooks）")
    void viewCountIncrementDoesNotTouchUpdatedAt() {
        String articleId = "ts-skiphooks";
        insertArticle(articleId);
        jdbcTemplate.update("UPDATE article SET updated_at = TIMESTAMPTZ '2020-01-01 00:00:00+00' "
                + "WHERE id = ?", articleId);
        OffsetDateTime before = updatedAtOf(articleId);

        articleMapper.incrViewCount(articleId);

        assertEquals(1, viewCountOf(articleId), "前置校验：阅读量确实自增了");
        assertEquals(before, updatedAtOf(articleId),
                "Go 的 UpdateColumn 置 SkipHooks=true ⇒ autoUpdateTime 不生效；"
                        + "若这里也前进了，说明 handler 的作用面比 Go 更宽");
    }

    // ==================================================================
    // 11. CASE-R12 —— 并发：同一篇文章同时「降级」与「删除」
    // ==================================================================

    /**
     * <h2>为什么这条必须是真 PG + 真并发</h2>
     * <ul>
     *   <li>要验证的是<b>行锁的串行化语义</b>（{@code SELECT ... FOR UPDATE} 在两个事务之间的行为）
     *       —— H2 与 mock 数据层都测不到；</li>
     *   <li>要验证的是<b>死锁是否发生</b> —— 单线程根本造不出来；</li>
     *   <li>要验证的是<b>指针版本单调</b>（{@code article_publication.pointer_version}）
     *       —— 它靠 {@code FOR UPDATE} + {@code UNIQUE(aggregate_id, aggregate_version)} 一起保证。</li>
     * </ul>
     *
     * <h2>Go 出处（逐行对照）</h2>
     * <pre>
     * Go 降级：update_article_logic.go:216-221
     *     RunInTx { RetractPublicationTx(…); ArticleRepo.UpdateTx(…) }
     * Go 删除：delete_article_logic.go:84-96
     *     RunInTx { RetractPublicationTx(…); ArticleSyncOutbox.CreateTx(…); ArticleRepo.DeleteTx(…) }
     * Go RetractPublicationTx：community_article_event.go:112-114
     *     tx.Clauses(clause.Locking{Strength:"UPDATE"}).Where("article_id=?").First(&amp;pointer)
     * Go 冲突判据：community_article_event.go:123-125
     *     if pointer.State != "published" || pointer.CurrentRevision == "" {
     *         return errors.New("community publication pointer conflicts with published article")
     *     }
     * </pre>
     *
     * <h2>预期（两侧一致）</h2>
     * 两个事务里<b>先拿到 publication 行锁的那个</b>完成撤回（{@code pointer_version} 1→2、
     * {@code state='retracted'}、写一条 {@code aggregate_version=2} 的 {@code retracted} 域事件）；
     * <b>后到的那个</b>在 READ COMMITTED 下重新读到已提交的 {@code state='retracted'}
     * ⇒ 撞 Go 的 {@code community publication pointer conflicts with published article}。
     * ⇒ <b>恰好一个成功</b>；指针版本<b>单调到 2</b>（不是 3）；域事件<b>只有 1 条</b>。
     *
     * <p>⚠️ 这是 <b>U-2</b> 要求的并发用例（此前只有静态代码对照，没有活体证据）。
     */
    @Test
    @DisplayName("CASE-R12: 同一篇文章并发「降级」与「删除」⇒ 无死锁；指针版本单调到 2；先到者赢、后到者报 pointer conflicts（与 Go 同）")
    void concurrentRetractAndDeleteOnSameArticleSerializesWithoutDeadlock() throws Exception {
        String articleId = "conc-retract-1";
        seedPublishedArticleWithPointer(articleId);

        // ⚠️ 实体在**主线程**里读（与生产一致：调用点先读、后调 atomicService），
        //    两个线程各拿一份实例 —— 不要在被测线程里用带 deleted_at IS NULL 过滤的读，
        //    并发窗口里读到 null 那是测试代码的竞态，不是被测行为。
        ArticleEntity forDowngrade = articleMapper.selectById(articleId);
        forDowngrade.setStatus(com.rag.article.article.proto.ArticleStatus.DRAFT_VALUE);
        Map<String, String> ext = ArticleSyncEventTypes.ensureExtInfo(forDowngrade.getExtInfo());
        ArticleSyncEventTypes.setSyncState(ext, "source_only", "pending",
                ArticleSyncEventTypes.REASON_STATUS_CHANGE, "evt-down", 2000L, "");
        forDowngrade.setExtInfo(ext);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> down = pool.submit(() -> {
                start.await();
                try {
                    atomicService.retractAndUpdate(forDowngrade, "evt-down", 2000L,
                            ArticleSyncEventTypes.REASON_STATUS_CHANGE);
                    return null;
                } catch (Throwable t) {
                    return t;
                }
            });
            Future<Throwable> del = pool.submit(() -> {
                start.await();
                try {
                    atomicService.retractAndDelete(articleId, "evt-del", 2000L);
                    return null;
                } catch (Throwable t) {
                    return t;
                }
            });

            start.countDown();
            // ⚠️ 60s 超时是**死锁的哨兵**：真死锁时两个事务互相等待，future 永不完成
            //    ⇒ get() 抛 TimeoutException ⇒ 用例失败（而不是永远挂着）。
            Throwable tDown = down.get(60, TimeUnit.SECONDS);
            Throwable tDel = del.get(60, TimeUnit.SECONDS);

            // ① 无死锁：两个 future 都在超时内完成，且失败也不是 PG 的死锁（SQLSTATE 40P01）
            assertFalse(isDeadlock(tDown), "降级路径出现死锁：" + rootMessage(tDown));
            assertFalse(isDeadlock(tDel), "删除路径出现死锁：" + rootMessage(tDel));

            // ② 恰好一个成功：后到者撞 Go 的「pointer conflicts」
            boolean downOk = (tDown == null);
            boolean delOk = (tDel == null);
            assertTrue(downOk ^ delOk,
                    "两个事务必须串行化 ⇒ 恰好一个成功（down=" + rootMessage(tDown)
                            + " / del=" + rootMessage(tDel) + "）");
            Throwable loser = downOk ? tDel : tDown;
            assertTrue(rootMessage(loser).contains(
                            "community publication pointer conflicts with published article"),
                    "后到者必须撞 Go 的判据（community_article_event.go:123-125），实际："
                            + rootMessage(loser));

            // ③ 指针版本单调到 2（不是 3）—— 后到者不得再推一次
            assertEquals(2L, jdbcTemplate.queryForObject(
                    "SELECT pointer_version FROM article_publication WHERE article_id = ?",
                    Long.class, articleId), "指针版本必须单调到 2（后到者不得再推一次）");
            assertEquals("retracted", jdbcTemplate.queryForObject(
                    "SELECT state FROM article_publication WHERE article_id = ?",
                    String.class, articleId), "指针状态 = retracted（Go community_article_event.go:134）");

            // ④ 域事件只有 1 条，aggregate_version = 指针版本（Go :125）
            assertEquals(1, countDomainEvents(articleId),
                    "只有成功的那次能写域事件（后到者在写之前就抛了）");
            assertEquals(2L, jdbcTemplate.queryForObject(
                    "SELECT aggregate_version FROM article_domain_outbox WHERE aggregate_id = ?",
                    Long.class, articleId), "域事件的 aggregate_version = 指针版本（Go :125）");
            assertEquals("community.article.retracted", jdbcTemplate.queryForObject(
                    "SELECT event_type FROM article_domain_outbox WHERE aggregate_id = ?",
                    String.class, articleId), "域事件的 event_type = community.article.retracted（Go :91）");
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * <h2>🔴 这条测的是<b>锁序</b>，不是结果</h2>
     *
     * <p>CASE-R12 测的是「两个事务的<b>最终结果</b>」，而<b>锁序</b>是另一维：
     * 只要两条路径的锁序<b>彼此一致</b>，结果就一样 —— 所以 CASE-R12 <b>证伪不了锁序</b>
     * （实测：把 {@code retractAndUpdate} 的 article 行锁拿掉，用例<b>仍全绿</b>）。
     * ⇒ 换一条<b>能唯一判定</b>的探针。
     *
     * <h2>探针原理（确定性，不靠碰运气的交错）</h2>
     * <ol>
     *   <li>用<b>另一条连接</b>开一个事务，<b>同时</b>扣住两把行锁：
     *       {@code article} 行 与 {@code article_publication} 行；</li>
     *   <li>让被测代码在<b>另一个线程</b>里去跑 {@code retractAndUpdate} ⇒ 它必然卡在
     *       <b>它最先要的那把锁</b>上；</li>
     *   <li>用 {@code pg_stat_activity} 读出<b>被阻塞后端正在执行的 SQL</b>
     *       （{@code wait_event_type = 'Lock'}）⇒ 那条 SQL 就是它的<b>第一把锁</b>。</li>
     * </ol>
     *
     * <h2>为什么这样能唯一判定</h2>
     * 两把锁都被扣着 ⇒ 无论它先要哪把，都会卡住 ⇒ <b>被阻塞的 SQL 唯一确定</b>它的第一把锁：
     * <ul>
     *   <li>卡在 {@code FROM article WHERE … FOR UPDATE} ⇒ 第一把锁是 <b>article</b>
     *       ⇒ 锁序 = <b>article → publication</b>；</li>
     *   <li>卡在 {@code FROM article_publication WHERE … FOR UPDATE} ⇒ 第一把锁是 publication
     *       ⇒ 锁序 = <b>publication → article</b>。</li>
     * </ul>
     * ⚠️ 判别子串用 {@code "FROM article WHERE"}（<b>带 WHERE</b>）——
     * {@code "FROM article_publication"} 里<b>不含</b>它（{@code article_publication} 是下划线连接），
     * 否则两条 SQL 会互相误判。
     *
     * <h2>Go 侧的对应事实（逐行对照，见 docs/DRIFT/article/rpc.md 的 U-2）</h2>
     * Go 的四条写路径分成<b>两种</b>锁序：
     * <pre>
     * article → publication ：WithArticleTx（article_sync_result_consumer.go:74 / article_consumer.go:104）
     *                         community_revision.go:81-89 先 SELECT … FOR UPDATE article
     * publication → article ：RetractPublicationTx 先 FOR UPDATE publication
     *                         （community_article_event.go:112-114），随后才 UPDATE/DELETE article
     * </pre>
     * ⇒ <b>Go 自己</b>在「发布」与「撤回/删除」之间存在锁序反转；
     * ⇒ 本实现把四条路径<b>统一</b>成 article → publication ⇒ <b>不存在反转</b>。
     */
    @Test
    @DisplayName("U-2: 锁序实测 —— 同时扣住 article 与 publication 两把锁 ⇒ 被阻塞的必须是 article 那把（锁序 = article → publication）")
    void lockOrderIsArticleThenPublication() throws Exception {
        String articleId = "lock-order-1";
        seedPublishedArticleWithPointer(articleId);

        // ⚠️ 必须是**另一条连接**：同一个连接上的事务锁不住自己。
        Connection holder = jdbcTemplate.getDataSource().getConnection();
        holder.setAutoCommit(false);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            holdRowLock(holder, "SELECT * FROM article WHERE id = ? FOR UPDATE", articleId);
            holdRowLock(holder, "SELECT * FROM article_publication WHERE article_id = ? FOR UPDATE", articleId);

            ArticleEntity forDowngrade = articleMapper.selectById(articleId);
            forDowngrade.setStatus(com.rag.article.article.proto.ArticleStatus.DRAFT_VALUE);
            Map<String, String> ext = ArticleSyncEventTypes.ensureExtInfo(forDowngrade.getExtInfo());
            ArticleSyncEventTypes.setSyncState(ext, "source_only", "pending",
                    ArticleSyncEventTypes.REASON_STATUS_CHANGE, "evt-lo", 2000L, "");
            forDowngrade.setExtInfo(ext);

            Future<Throwable> f = pool.submit(() -> {
                try {
                    atomicService.retractAndUpdate(forDowngrade, "evt-lo", 2000L,
                            ArticleSyncEventTypes.REASON_STATUS_CHANGE);
                    return null;
                } catch (Throwable t) {
                    return t;
                }
            });

            String blocked = awaitBlockedForUpdate(30_000L);
            assertNotNull(blocked,
                    "没观测到被阻塞的 FOR UPDATE ⇒ 探针本身失效（不是被测行为的问题）");
            assertTrue(blocked.contains("FROM article WHERE"),
                    "锁序必须是 article → publication ⇒ 它应该先卡在 **article** 的 FOR UPDATE 上；"
                            + "实际被阻塞的语句：" + blocked);
            assertFalse(blocked.contains("FROM article_publication"),
                    "若卡在 publication 上 ⇒ 第一把锁是 publication ⇒ 与实现的锁序不符：" + blocked);

            // 释放扣着的锁，让被测线程收尾（它会撞上 pointer conflicts，符合预期）
            holder.rollback();
            Throwable outcome = f.get(60, TimeUnit.SECONDS);
            assertNull(outcome, "释放锁后它本该成功（此时两把锁都没被扣着了），实际：" + rootMessage(outcome));
        } finally {
            try {
                holder.rollback();
            } catch (Exception ignored) {
                // holder 可能已被关闭
            }
            holder.close();
            pool.shutdownNow();
        }
    }

    /** 在给定连接上扣住一行（{@code FOR UPDATE}），事务保持打开。 */
    private static void holdRowLock(Connection conn, String sql, String id) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                // 必须把结果读完，否则 PG 会等客户端消费 ⇒ 拿不到锁
                while (rs.next()) {
                    // no-op
                }
            }
        }
    }

    /**
     * 等到某个后端卡在 {@code FOR UPDATE} 上，返回它<b>正在执行的 SQL</b>。
     *
     * <p>用 {@code application_name} 过滤：本类所有连接都带同一个标记
     * （{@code @DynamicPropertySource} 里加进 JDBC URL），
     * 而本容器实例上只有本测试会卡住 ⇒ 不会误抓别的会话。
     */
    private String awaitBlockedForUpdate(long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            List<String> blocked = jdbcTemplate.queryForList(
                    "SELECT query FROM pg_stat_activity "
                            + "WHERE wait_event_type = 'Lock' AND query ILIKE '%FOR UPDATE%' "
                            + "AND application_name = 'article-lockorder' "
                            + "AND pid <> pg_backend_pid()",
                    String.class);
            for (String q : blocked) {
                if (q != null && !q.isBlank()) {
                    return q;
                }
            }
            Thread.sleep(100);
        }
        return null;
    }

    /** 取异常链里每一层的消息拼串（Spring 会把底层异常包成好几层）。 */
    private static String rootMessage(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            if (cur.getMessage() != null) {
                sb.append(cur.getMessage()).append(" | ");
            }
            if (cur.getCause() == cur) {
                break;
            }
        }
        return sb.toString();
    }

    /** 是否是 PG 的死锁（{@code 40P01 deadlock detected}）。 */
    private static boolean isDeadlock(Throwable t) {
        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            if (cur instanceof java.sql.SQLException sql && "40P01".equals(sql.getSQLState())) {
                return true;
            }
            String m = cur.getMessage();
            if (m != null && m.toLowerCase().contains("deadlock")) {
                return true;
            }
            if (cur.getCause() == cur) {
                break;
            }
        }
        return false;
    }

    private OffsetDateTime updatedAtOf(String articleId) {
        return jdbcTemplate.queryForObject(
                "SELECT updated_at FROM article WHERE id = ?", OffsetDateTime.class, articleId);
    }

    // ==================================================================
    // 辅助
    // ==================================================================

    private int viewCountOf(String articleId) {
        Integer v = jdbcTemplate.queryForObject(
                "SELECT view_count FROM article WHERE id = ?", Integer.class, articleId);
        return v == null ? 0 : v;
    }

    private void insertArticleWithCreatedAt(String id, String isoInstant) {
        jdbcTemplate.update("""
                INSERT INTO article (id, title, brief, content, cover_image_url, manual_type_tag,
                                     secondary_tags, author_id, status, view_count, like_count,
                                     comment_count, share_count, ext_info, created_at, updated_at)
                VALUES (?, 'title', 'brief', 'content.md', '', 'tag', '[]'::jsonb, '1001', 3,
                        0, 0, 0, 0, '{}'::jsonb, ?::timestamptz, now())
                """, id, isoInstant);
    }

    private void insertArticleWithViewCount(String id, int viewCount) {
        jdbcTemplate.update("""
                INSERT INTO article (id, title, brief, content, cover_image_url, manual_type_tag,
                                     secondary_tags, author_id, status, view_count, like_count,
                                     comment_count, share_count, ext_info, created_at, updated_at)
                VALUES (?, 'title', 'brief', 'content.md', '', 'tag', '[]'::jsonb, '1001', 3,
                        ?, 0, 0, 0, '{}'::jsonb, now(), now())
                """, id, viewCount);
    }

    private int countDomainEvents(String aggregateId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM article_domain_outbox WHERE aggregate_id = ?",
                Integer.class, aggregateId);
        return count == null ? 0 : count;
    }

    private String insertArticle(String id) {
        jdbcTemplate.update("""
                INSERT INTO article (id, title, brief, content, cover_image_url, manual_type_tag,
                                     secondary_tags, author_id, status, view_count, like_count,
                                     comment_count, share_count, ext_info, created_at, updated_at)
                VALUES (?, 'title-' || ?, 'brief', 'content.md', '', 'tag', '[]'::jsonb, '1001', 3,
                        0, 0, 0, 0, '{}'::jsonb, now(), now())
                """, id, id);
        return id;
    }

    private void insertRevision(String articleId, long revision, String revisionId,
                                String syncEventId, String markdown, long publishedAtMs) {
        jdbcTemplate.update("""
                INSERT INTO article_revision (article_id, revision, revision_id, author_id, source_object,
                                              content_sha256, title, brief, cover_image_url, manual_type_tag,
                                              secondary_tags, markdown, published_at, sync_event_id)
                VALUES (?, ?, ?, '1001', ?, repeat('a', 64), 'rev title', 'rev brief', '', 'tag',
                        '[]'::jsonb, ?, to_timestamp(? / 1000.0), ?)
                """, articleId, revision, revisionId, articleId + "-r1.md", markdown,
                (double) publishedAtMs, syncEventId);
    }

    private void seedReviewingArticle(String articleId, String contentPath,
                                      String eventId, long versionMs) {
        jdbcTemplate.update("""
                INSERT INTO article (id, title, brief, content, cover_image_url, manual_type_tag,
                                     secondary_tags, author_id, status, view_count, like_count,
                                     comment_count, share_count, ext_info, created_at, updated_at)
                VALUES (?, 'title', 'brief', ?, '', 'tag', '[]'::jsonb, '1001', 3, 0, 0, 0, 0,
                        jsonb_build_object(
                          'reco_sync_state', 'pending',
                          'last_sync_event_id', ?,
                          'last_sync_version_ms', ?::text,
                          'last_sync_reason', 'update',
                          'pending_sync_reason', 'update',
                          'review_nonce', ?),
                        now(), now())
                """, articleId, contentPath, eventId, String.valueOf(versionMs), eventId);
    }

    /**
     * 建一条<b>已发布</b>的文章 + 社区指针（{@code state='published'} / {@code pointer_version=1}）。
     *
     * <p>这是 retract 路径能真正推进指针的<b>前置条件</b>：
     * Go 的 {@code RetractPublicationTx} 在指针<b>缺失</b>时走 legacy 缺口分支（标记后正常返回，
     * {@code community_article_event.go:115-119}），只有在 {@code state != "published"}
     * 时才抛冲突（{@code :123-125}）⇒ 不铺这条指针，测的就不是同一条路径。
     *
     * <p>⚠️ {@code status = 2} = {@code ArticleStatus.PUBLISHED_VALUE}
     * （proto 枚举：UNSPECIFIED=0 / DRAFT=1 / PUBLISHED=2 / REVIEWING=3 / REJECTED=4 / DELETED=5）。
     */
    private void seedPublishedArticleWithPointer(String articleId) {
        jdbcTemplate.update("""
                INSERT INTO article (id, title, brief, content, cover_image_url, manual_type_tag,
                                     secondary_tags, author_id, status, view_count, like_count,
                                     comment_count, share_count, ext_info, created_at, updated_at)
                VALUES (?, 'published title', 'brief', 'pub.md', '', 'tag', '[]'::jsonb, '1001', 2, 0, 0, 0, 0,
                        jsonb_build_object(
                          'publish_stage', 'published',
                          'reco_sync_state', 'succeeded',
                          'published_once', 'true',
                          'last_sync_event_id', 'evt-seed',
                          'last_sync_version_ms', '1000',
                          'last_sync_reason', 'create'),
                        now(), now())
                """, articleId);
        jdbcTemplate.update("""
                INSERT INTO article_publication (article_id, current_revision, pointer_version, state, last_event_id)
                VALUES (?, ?, 1, 'published', 'community.article.evt-seed.published')
                """, articleId, articleId + ":r1");
    }

    /** 建一条 pending 的同步 outbox 行（发布回调要读回它的冻结 payload）。 */
    private void insertRevisionReadyOutbox(String articleId, String eventId, long versionMs) {
        insertRevisionReadyOutbox(articleId, eventId, versionMs, "approved title");
    }

    /**
     * 同上，但可指定<b>冻结 payload 的标题</b>。
     *
     * <p>Go 出处：{@code article_sync_types.go:109}（{@code Title: article.Title}）——
     * 冻结的是<b>入队那一刻</b>的行标题；发布回调通知时读的是<b>当前行</b>的标题
     * （{@code article_sync_result_consumer.go:147}）。两者可以不同 ⇒ 需要分别可控。
     */
    private void insertRevisionReadyOutbox(String articleId, String eventId, long versionMs,
                                           String frozenTitle) {
        ArticleSyncEventTypes.ArticleSyncEvent event = new ArticleSyncEventTypes.ArticleSyncEvent();
        event.eventScope = ArticleSyncEventTypes.ARTICLE_SYNC_SCOPE;
        event.eventId = eventId;
        event.articleId = articleId;
        event.op = ArticleSyncEventTypes.OP_UPSERT;
        event.reason = ArticleSyncEventTypes.REASON_UPDATE;
        event.authorId = "1001";
        event.status = "PUBLISHED";
        event.versionMs = versionMs;
        event.title = frozenTitle;
        event.markdown = "# Approved";
        String payload;
        try {
            payload = new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(event);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        syncOutboxMapper.insert(eventId,
                ArticleSyncEventTypes.articleSyncEventKey(articleId, event.op, eventId),
                ArticleSyncEventTypes.ARTICLE_SYNC_SCOPE, articleId, payload);
    }

    /** 让测试代码能拿到一个 ObjectMapper 而不必在每个断言处 new。 */
    private static final class ObjectMapperHolder {
        private final com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();

        com.fasterxml.jackson.databind.ObjectMapper mapper() {
            return mapper;
        }
    }
}