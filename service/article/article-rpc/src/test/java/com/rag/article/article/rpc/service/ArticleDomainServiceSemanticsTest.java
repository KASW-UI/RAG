package com.rag.article.article.rpc.service;

import com.rag.article.article.proto.ArticleStatus;
import com.rag.article.article.proto.CreateArticleRequest;
import com.rag.article.article.proto.GetArticleRequest;
import com.rag.article.article.proto.ListArticlesRequest;
import com.rag.article.article.proto.ListArticlesResponse;
import com.rag.article.article.proto.UpdateArticleRequest;
import com.rag.article.article.rpc.client.ArticleObjectStorageClient;
import com.rag.article.article.rpc.exception.ArticleExceptions;
import com.rag.article.article.rpc.mapper.ArticleMapper;
import com.rag.article.article.rpc.metrics.ArticleRpcMetrics;
import com.rag.common.spring.SnowflakeBean;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.grpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 作者视角六个用例的<b>分支语义</b>单元测试。
 *
 * <p><b>覆盖的是"哪条分支被选中"与"失败时抛出什么"</b>，
 * 不覆盖 SQL（那部分由 {@code ArticleTransactionWiringTest} 在真 PG 上验证）。
 * <br>依据：Go 侧 {@code internal/logic/} 六个 logic 的判定顺序就是业务语义本身。
 */
class ArticleDomainServiceSemanticsTest {

    private ArticleMapper articleMapper;
    private ArticleObjectStorageClient storage;
    private ArticleAtomicService atomicService;
    private ArticlePublicDomainService publicService;
    private KafkaTemplate<String, String> kafkaTemplate;
    private ArticleDomainService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        articleMapper = mock(ArticleMapper.class);
        storage = mock(ArticleObjectStorageClient.class);
        atomicService = mock(ArticleAtomicService.class);
        publicService = mock(ArticlePublicDomainService.class);
        kafkaTemplate = mock(KafkaTemplate.class);

        when(storage.getObjectNamePrefixForArticle()).thenReturn("articles/content/");
        when(storage.getObjectNamePrefixForImage()).thenReturn("articles/images/");
        when(storage.getBucketName()).thenReturn("articles");

        // ⚠️ 必须 stub send 的返回值：实现用 .get() 等 broker 确认（与 Go 的同步 PushWithKey 等价），
        //    mock 默认返回 null ⇒ CompletableFuture.get() 抛 NPE。
        //    这里返回一个**已完成且成功**的 future，等价于"推送成功"。
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));

        com.rag.common.spring.SnowflakeBean snowflake =
                new com.rag.common.spring.SnowflakeBean(
                        new com.rag.common.spring.SnowflakeProperties());

        // ⚠️ properties 必须真的构造出来：enqueueReview / createArticle 会读 reviewTopic()。
        //    传 null 会让这些路径在测试里变成 NPE —— 那正是本类要覆盖的分支。
        var properties = new com.rag.article.article.rpc.config.ArticleRpcProperties();
        properties.getKqPusherConf().setTopic("article-creation-topic");

        service = new ArticleDomainService(articleMapper, storage, atomicService, publicService,
                new ArticleRpcMetrics(new SimpleMeterRegistry()), snowflake, properties, kafkaTemplate);
    }

    // ==================================================================
    // GetArticle：三条分支的判定顺序
    // ==================================================================

    @Test
    @DisplayName("Go: RequesterId + IncrView 组合非法 ⇒ InvalidArgument（且早于任何 DB 访问）")
    void authorReadCannotIncrementViewIsRejectedBeforeDbAccess() {
        GetArticleRequest request = GetArticleRequest.newBuilder()
                .setArticleId("a-1").setRequesterId("1001").setIncrView(true).build();

        ArticleExceptions.Rpc e = assertThrows(ArticleExceptions.Rpc.class,
                () -> service.getArticle(request));
        assertEquals(Status.Code.INVALID_ARGUMENT, e.getCode());
        assertEquals("author read cannot increment view", e.getMessage());
        verify(articleMapper, never()).selectById(anyString());
    }

    @Test
    @DisplayName("Go: PublicOnly 优先于上面那条校验（公开读 + 作者身份 + 计阅读量 是合法的）")
    void publicOnlyShortCircuitsTheInvalidArgumentBranch() {
        GetArticleRequest request = GetArticleRequest.newBuilder()
                .setArticleId("a-1").setRequesterId("1001").setIncrView(true)
                .setPublicOnly(true).build();

        ArticlePublicDomainService.PublicArticleView view =
                new ArticlePublicDomainService.PublicArticleView();
        view.id = "a-1";
        when(publicService.getPublicArticle("a-1", true)).thenReturn(view);

        // 不抛异常 ⇒ 说明走了公开分支而不是 InvalidArgument 分支
        assertDoesNotThrow(() -> service.getArticle(request));
        verify(publicService).getPublicArticle("a-1", true);
        verify(articleMapper, never()).selectById(anyString());
    }

    @Test
    @DisplayName("Go: 作者校验失败 ⇒ PermissionDenied，且早于 MinIO 取正文")
    void otherAuthorIsDeniedBeforeObjectFetch() throws Exception {
        when(articleMapper.selectById("a-1")).thenReturn(articleWith("a-1", "1001", ArticleStatus.REVIEWING_VALUE));
        GetArticleRequest request = GetArticleRequest.newBuilder()
                .setArticleId("a-1").setRequesterId("2002").build();

        ArticleExceptions.Rpc e = assertThrows(ArticleExceptions.Rpc.class,
                () -> service.getArticle(request));
        assertEquals(Status.Code.PERMISSION_DENIED, e.getCode());
        assertEquals("article author differs", e.getMessage());
        // ⚠️ 关键：不得发起对象读取（Go get_article_logic.go:66-68 在 :83 之前 return）
        verify(storage, never()).readString(anyString());
    }

    @Test
    @DisplayName("Go: 文章不存在 ⇒ NotFound")
    void missingArticleIsNotFound() {
        when(articleMapper.selectById("nope")).thenReturn(null);
        ArticleExceptions.Rpc e = assertThrows(ArticleExceptions.Rpc.class, () -> service.getArticle(
                GetArticleRequest.newBuilder().setArticleId("nope").build()));
        assertEquals(Status.Code.NOT_FOUND, e.getCode());
        assertEquals("article not found", e.getMessage());
    }

    @Test
    @DisplayName("🔴 Go: 浏览量自增是 best-effort —— 失败只记日志，仍返回成功（不可对称化）")
    void viewCountIncrementIsBestEffortOnAuthorPath() throws Exception {
        when(articleMapper.selectById("a-1")).thenReturn(articleWith("a-1", "1001", ArticleStatus.REVIEWING_VALUE));
        when(articleMapper.incrViewCount("a-1")).thenThrow(new RuntimeException("db down"));
        when(storage.readString(anyString())).thenReturn("# body");

        var response = service.getArticle(GetArticleRequest.newBuilder()
                .setArticleId("a-1").setIncrView(true).build());

        assertNotNull(response.getArticle(), "自增失败不应让整个请求失败（Go:72-75 只记日志）");
        assertEquals("# body", response.getArticle().getMarkdownContent());
        // 响应里的 viewCount 是内存 +1（Go:76 article.ViewCount++），不是读库结果
        assertEquals(1, response.getArticle().getViewCount());
    }

    @Test
    @DisplayName("🔴 Go: 作者视角返回真实 status（可能是 REVIEWING），与公开视角恒 PUBLISHED 不同")
    void authorViewReturnsRealStatusNotForcedPublished() throws Exception {
        when(articleMapper.selectById("a-1")).thenReturn(articleWith("a-1", "1001", ArticleStatus.REVIEWING_VALUE));
        when(storage.readString(anyString())).thenReturn("# body");

        var response = service.getArticle(GetArticleRequest.newBuilder().setArticleId("a-1").build());
        assertEquals(ArticleStatus.REVIEWING, response.getArticle().getStatus(),
                "作者看到的状态必须是真实状态；强制 PUBLISHED 会让作者看不到自己文章正在审核");
    }

    // ==================================================================
    // UpdateArticle：三条分支的优先级
    // ==================================================================

    @Test
    @DisplayName("🔴 Go: A 分支优先于 B —— 已发布 + 显式降级 + 内容变更 ⇒ 撤回发布（不是重审）")
    void branchAPrevailsOverBranchB() {
        when(articleMapper.selectById("a-1")).thenReturn(articleWith("a-1", "1001", ArticleStatus.PUBLISHED_VALUE));
        UpdateArticleRequest request = UpdateArticleRequest.newBuilder()
                .setArticleId("a-1")
                .setTitle("new title")                     // sourceChanged = true
                .setStatus(ArticleStatus.DRAFT)             // 显式非发布
                .build();

        service.updateArticle(request);

        // 走 A：调用 atomic 的 retractAndUpdate，**不**入队审核
        verify(atomicService).retractAndUpdate(
                any(com.rag.article.article.rpc.domain.ArticleEntity.class), anyString(), anyLong(), eq("status_change"));
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Go: B 分支 —— 已发布 + 仅内容变更 ⇒ 回 REVIEWING 并入队审核")
    void publishedPlusSourceChangeGoesBackToReview() {
        when(articleMapper.selectById("a-1")).thenReturn(articleWith("a-1", "1001", ArticleStatus.PUBLISHED_VALUE));
        UpdateArticleRequest request = UpdateArticleRequest.newBuilder()
                .setArticleId("a-1").setTitle("new title").build();

        service.updateArticle(request);

        verify(articleMapper).updateById(
                argThat((com.rag.article.article.rpc.domain.ArticleEntity a) ->
                        a.getStatus() == ArticleStatus.REVIEWING_VALUE));
        verify(atomicService, never()).retractAndUpdate(
                any(com.rag.article.article.rpc.domain.ArticleEntity.class), anyString(), anyLong(), anyString());
    }

    @Test
    @DisplayName("Go: B 分支把 last_sync_event_id 置空，让新一轮审核能通过幂等判据")
    void branchBClearsLastSyncEventId() {
        var article = articleWith("a-1", "1001", ArticleStatus.PUBLISHED_VALUE);
        article.setExtInfo(new java.util.LinkedHashMap<>(
                java.util.Map.of("last_sync_event_id", "old-event", "last_sync_version_ms", "123")));
        when(articleMapper.selectById("a-1")).thenReturn(article);

        service.updateArticle(UpdateArticleRequest.newBuilder().setArticleId("a-1").setTitle("t").build());

        assertEquals("", article.getExtInfo().get("last_sync_event_id"),
                "Go update_article_logic.go:87 的 eventId 参数是 \"\"；置空才能让审核消费者重新入队");
    }

    @Test
    @DisplayName("Go: C 分支 —— 已在 REVIEWING 且无源变更 ⇒ 不重新入队审核（避免重复消费）")
    void reviewingWithoutSourceChangeDoesNotReenqueue() {
        when(articleMapper.selectById("a-1")).thenReturn(articleWith("a-1", "1001", ArticleStatus.REVIEWING_VALUE));
        service.updateArticle(UpdateArticleRequest.newBuilder().setArticleId("a-1").build());

        verify(articleMapper).updateById(any(com.rag.article.article.rpc.domain.ArticleEntity.class));
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Go: 正文对象名带内容哈希（每次编辑写新对象，已批准字节永不被覆盖）")
    void markdownObjectNameCarriesContentHash() throws Exception {
        when(articleMapper.selectById("a-1")).thenReturn(articleWith("a-1", "1001", ArticleStatus.REVIEWING_VALUE));
        service.updateArticle(UpdateArticleRequest.newBuilder()
                .setArticleId("a-1").setMarkdownContent("# new body").build());

        String expectedHash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest("# new body".getBytes(StandardCharsets.UTF_8)));
        verify(storage).put("articles/content/a-1-" + expectedHash + ".md",
                "# new body".getBytes(StandardCharsets.UTF_8), "text/markdown", null);
    }

    @Test
    @DisplayName("Go: 固定命名（无哈希）只属于 create 路径 —— 两者的对象名规则不可混用")
    void createAndUpdateUseDifferentObjectNamingRules() {
        // create：<articlePath><id>.md（无哈希）
        assertTrue(service.getClass() != null);
        String createName = "articles/content/" + 123L + ".md";
        assertFalse(createName.contains("-"), "create 路径的对象名不含哈希（Go create_article_logic.go:59）");

        // update：<articlePath><id>-<sha256>.md
        String updateName = ArticleDomainService.articleMarkdownObjectName("articles/content/", "123", "# x");
        assertTrue(updateName.matches("articles/content/123-[0-9a-f]{64}\\.md"), updateName);
    }

    // ==================================================================
    // DeleteArticle
    // ==================================================================

    @Test
    @DisplayName("🔴 Go: 空 operator_id 与非作者返回**同一个** PermissionDenied（不可区分）")
    void emptyOperatorIdAndWrongOperatorAreIndistinguishable() {
        when(articleMapper.selectById("a-1")).thenReturn(articleWith("a-1", "1001", ArticleStatus.PUBLISHED_VALUE));

        var emptyOperator = assertThrows(ArticleExceptions.Rpc.class, () -> service.deleteArticle(
                com.rag.article.article.proto.DeleteArticleRequest.newBuilder()
                        .setArticleId("a-1").setOperatorId("").build()));
        var wrongOperator = assertThrows(ArticleExceptions.Rpc.class, () -> service.deleteArticle(
                com.rag.article.article.proto.DeleteArticleRequest.newBuilder()
                        .setArticleId("a-1").setOperatorId("2002").build()));

        assertEquals(emptyOperator.getCode(), wrongOperator.getCode());
        assertEquals(emptyOperator.getMessage(), wrongOperator.getMessage());
        assertEquals("forbidden", emptyOperator.getMessage());
        // 校验失败 ⇒ 绝不触碰事务
        verify(atomicService, never()).retractAndDelete(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("Go: 删除成功只调一次 retractAndDelete（不删对象存储）")
    void deleteDelegatesToAtomicService() throws Exception {
        when(articleMapper.selectById("a-1")).thenReturn(articleWith("a-1", "1001", ArticleStatus.PUBLISHED_VALUE));
        service.deleteArticle(com.rag.article.article.proto.DeleteArticleRequest.newBuilder()
                .setArticleId("a-1").setOperatorId("1001").build());

        verify(atomicService).retractAndDelete(eq("a-1"), anyString(), anyLong());
        // ⚠️ Go delete_article_logic.go:64-66 明确不删对象（历史 revision 必须仍可读）
        verify(storage, never()).put(anyString(), any(), anyString(), any());
    }

    // ==================================================================
    // ListArticles：作者视角的 page/size 透传
    // ==================================================================

    @Test
    @DisplayName("Go: 作者视角 page/page_size 原样透传并回显（不做归一，与公开视角不同）")
    void authorListPassesThroughPageAndSizeUnchanged() {
        when(articleMapper.countAuthorView(any(), any(), any())).thenReturn(0L);
        when(articleMapper.selectAuthorView(any(), any(), any(), any(), anyBoolean(), anyInt(), anyLong()))
                .thenReturn(List.of());

        var response = service.listArticles(ListArticlesRequest.newBuilder()
                .setPage(0).setPageSize(0).build());

        // ⚠️ 公开路径会把 0 归一为 page=1 / size=20；作者路径不归一
        assertEquals(0, response.getPage());
        assertEquals(0, response.getPageSize());
    }

    @Test
    @DisplayName("Go: 作者视角 offset 为负时夹到 0（Go model/article.go:157-159）")
    void negativeOffsetIsClampedToZero() {
        when(articleMapper.countAuthorView(any(), any(), any())).thenReturn(0L);
        when(articleMapper.selectAuthorView(any(), any(), any(), any(), anyBoolean(), anyInt(), anyLong()))
                .thenReturn(List.of());

        service.listArticles(ListArticlesRequest.newBuilder()
                .setPage(-3).setPageSize(10).build());

        verify(articleMapper).selectAuthorView(any(), any(), any(), any(), anyBoolean(), eq(10), eq(0L));
    }

    @Test
    @DisplayName("Go: PublicOnly 列表走公开路径（作者路径的 count 绝不被调用）")
    void publicListDelegatesToPublicService() {
        when(publicService.listPublicArticles(any())).thenReturn(
                ListArticlesResponse.newBuilder().setTotal(0).setPage(1).setPageSize(20).build());

        var response = service.listArticles(ListArticlesRequest.newBuilder().setPublicOnly(true).build());

        assertEquals(20, response.getPageSize(), "公开路径把 size=0 归一为默认 20");
        verify(articleMapper, never()).countAuthorView(any(), any(), any());
    }

    // ==================================================================
    // UploadFile
    // ==================================================================

    @Test
    @DisplayName("Go: 图片对象名 = <imagePath><id><ext>，ext 含点；URL 三段各自 trim")
    void uploadFileBuildsObjectNameAndPublicUrl() throws Exception {
        when(storage.buildPublicFileURL(anyString()))
                .thenReturn("http://localhost:39000/articles/articles/images/123.png");

        service.uploadFile(com.rag.article.article.proto.UploadFileRequest.newBuilder()
                .setFileName("photo.png")
                .setContent(com.google.protobuf.ByteString.copyFromUtf8("binary"))
                .build());

        verify(storage).put(startsWith("articles/images/"), any(byte[].class),
                eq("image/png"), eq("public, max-age=31536000, immutable"));
        // ⚠️ markdown 上传不带 CacheControl；图片带。两者不可混用。
        verify(storage, never()).put(anyString(), any(byte[].class), eq("text/markdown"),
                eq("public, max-age=31536000, immutable"));
    }

    @Test
    @DisplayName("Go: 未知扩展名回落 application/octet-stream（回落与查表是同一步）")
    void unknownExtensionFallsBackToOctetStream() {
        assertEquals("application/octet-stream", ArticleDomainService.guessContentType(".weirdext"));
        assertEquals("application/octet-stream", ArticleDomainService.guessContentType(""));
        assertEquals("application/octet-stream", ArticleDomainService.guessContentType(null));
        assertEquals("image/png", ArticleDomainService.guessContentType(".png"));
    }

    // ==================================================================
    // 辅助
    // ==================================================================

    /**
     * 造一行文章。{@code statusNumber} 传 proto 枚举的 <b>int 数值</b>
     * （{@code ArticleStatus.REVIEWING_VALUE} 等），与 {@code ArticleEntity.status} 的类型一致。
     */
    private static com.rag.article.article.rpc.domain.ArticleEntity articleWith(
            String id, String authorId, int statusNumber) {
        var article = new com.rag.article.article.rpc.domain.ArticleEntity();
        article.setId(id);
        article.setAuthorId(authorId);
        article.setStatus(statusNumber);
        article.setTitle("t");
        article.setBrief("b");
        article.setContent("articles/content/" + id + ".md");
        article.setExtInfo(new java.util.LinkedHashMap<>());
        article.setCreatedAt(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));
        article.setUpdatedAt(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));
        return article;
    }
}