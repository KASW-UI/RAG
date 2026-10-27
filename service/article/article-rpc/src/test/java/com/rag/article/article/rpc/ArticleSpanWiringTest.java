package com.rag.article.article.rpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.article.article.proto.ArticleStatus;
import com.rag.article.article.proto.CreateArticleRequest;
import com.rag.article.article.proto.DeleteArticleRequest;
import com.rag.article.article.proto.GetArticleRequest;
import com.rag.article.article.proto.ListArticlesRequest;
import com.rag.article.article.proto.UpdateArticleRequest;
import com.rag.article.article.proto.UploadFileRequest;
import com.rag.article.article.rpc.client.ArticleObjectStorageClient;
import com.rag.article.article.rpc.client.ContentSecurityPort;
import com.rag.article.article.rpc.client.MessageRpcClient;
import com.rag.article.article.rpc.config.ArticleRpcProperties;
import com.rag.article.article.rpc.domain.ArticleEntity;
import com.rag.article.article.rpc.mapper.ArticleMapper;
import com.rag.article.article.rpc.mapper.ArticleSyncOutboxMapper;
import com.rag.article.article.rpc.metrics.ArticleRpcMetrics;
import com.rag.article.article.rpc.mq.ArticleReviewConsumer;
import com.rag.article.article.rpc.mq.ArticleSyncResultConsumer;
import com.rag.article.article.rpc.mq.ArticleSyncEventTypes;
import com.rag.article.article.rpc.service.ArticleAtomicService;
import com.rag.article.article.rpc.service.ArticleDomainService;
import com.rag.article.article.rpc.service.ArticlePublicDomainService;
import com.rag.article.article.rpc.service.ArticleSpans;
import com.rag.common.spring.SnowflakeBean;
import com.rag.common.spring.SnowflakeProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 🔴 L-10 守门 —— article-rpc 的 <b>8 个手写 span 必须真的被导出</b>，
 * 且带齐 Go 侧的 span 名 / tracer 名 / 属性名 / 属性值 / 事件名 / 「记不记错误」的判据。
 *
 * <h2>为什么是「装配层」而不是「结构断言」</h2>
 * <p>AGENTS.md §3.3：<b>不得凭注解 / 方法名称断定机制已启用</b>。
 * 断言「{@code ArticleSpans.traced} 被调用了」只能证明<b>代码写成了这样</b>，
 * 证明不了<b>运行时真的产生了 span</b>。
 * ⇒ 本类自装一个 SDK ＋ 收集型 {@link SpanExporter}，让 {@code GlobalOpenTelemetry} 指向它，
 * 然后断言<b>导出的 span</b>。
 * <br>⚠️ 并且第一条断言就是<b>装置活性</b>（{@link #collectorIsAlive}）：
 * 没有它，「什么 span 都没导出」会让下面所有断言<b>平凡通过</b>。
 *
 * <h2>为什么不用 Spring 上下文 / Testcontainers</h2>
 * <p>span 的可观察事实（名字 / 属性 / 事件 / 记不记错误）**全部可以在纯 Mockito 下驱动**：
 * ① 成功路径靠 stub mapper / storage / kafkaTemplate；② 失败路径靠让它们抛。
 * ⇒ ⇒ <b>不碰 PG</b>（⇒ 不受库表状态影响、也不占 Testcontainers 端口段）、
 * <b>不占 gRPC 端口</b>（⇒ 不与别的域抢端口段）⇒ 本类可<b>毫秒级</b>跑完。
 *
 * <h2>Go 出处（决定「有几个 span」「记不记」，不是「有几个方法」）</h2>
 * <p>Go 侧 article-rpc <b>6 个 logic 全部</b>手写了 span
 * （{@code create_article_logic.go:43} · {@code get_article_logic.go:47} ·
 * {@code update_article_logic.go:47} · {@code delete_article_logic.go:41} ·
 * {@code list_articles_logic.go:31} · {@code upload_file_logic.go:41}）
 * ＋ <b>2 个 consumer</b> 走共享层的 {@code TraceConsumer}
 * （{@code article_consumer.go:45} · {@code article_sync_result_consumer.go:40}）
 * ⇒ 共 <b>8 个</b>。
 *
 * <h2>🔴 三处「Go 侧的不一致」必须逐字保留（本类逐条断言）</h2>
 * <ol>
 *   <li><b>span 名<b>无域前缀</b></b>：6 个 logic 的 span 名是 {@code CreateArticle} / {@code GetArticle} / …，
 *       <b>不是</b> {@code Article.CreateArticle} ⇒ 断言名字逐字。</li>
 *   <li><b>属性名<b>裸名</b></b>：{@code author_id} / {@code article_id} / {@code page} …，
 *       <b>没有</b> {@code audit.} 前缀（那是 comment 域的形状）。</li>
 *   <li><b>事件名<b>带空格</b></b>：{@code "start find article"} / {@code "db list success"} … ⇒ 逐字断言。</li>
 * </ol>
 *
 * <h2>🔴 「Go 记不记这个错误」的判据在 article 域<b>不是</b>业务码段</h2>
 * <p>comment 域用「业务码 ≥ 5000 ⇒ 系统失败 ⇒ Go 会 RecordError」的码段判据；
 * <b>article 域不适用</b>（Go 连 NotFound / PermissionDenied 都记），且有「<b>记了但不返回</b>」。
 * ⇒ 本类按 Go 的<b>逐点</b>事实断言：
 * <table border="1">
 *   <caption>article 域的「记 / 不记」真值表（每条都有 Go 行号）</caption>
 *   <tr><th>位置</th><th>Go</th><th>本类断言</th></tr>
 *   <tr><td>{@code get_article_logic.go:56-58}（NotFound）</td><td>只设 {@code article_found=false}，<b>不记</b></td>
 *       <td>无异常事件</td></tr>
 *   <tr><td>{@code get_article_logic.go:66-68}（PermissionDenied）</td><td><b>不记</b></td><td>无异常事件</td></tr>
 *   <tr><td>{@code get_article_logic.go:73-75}（IncrViewCount 失败）</td><td><b>记了但不返回</b></td>
 *       <td>有异常事件，<b>且方法仍成功返回</b></td></tr>
 *   <tr><td>{@code update_article_logic.go:59-63}（NotFound）</td><td><b>记</b></td><td>有异常事件</td></tr>
 *   <tr><td>{@code delete_article_logic.go:53-57 / :58-62}</td><td><b>都记</b></td><td>有异常事件</td></tr>
 * </table>
 */
class ArticleSpanWiringTest {

    /**
     * 自建收集型 exporter —— <b>不引 {@code opentelemetry-sdk-testing}</b>
     * （引入它只为测试反而扩大依赖面）。范式照抄同域 comment 的 {@code CommentSpanWiringTest}。
     */
    static final class CollectingExporter implements SpanExporter {
        final List<SpanData> spans = Collections.synchronizedList(new ArrayList<>());

        @Override
        public CompletableResultCode export(Collection<SpanData> batch) {
            spans.addAll(batch);
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }

        void clear() {
            spans.clear();
        }
    }

    private static final CollectingExporter EXPORTER = new CollectingExporter();

    @BeforeAll
    static void installSdk() {
        // 🔴 关键：把 GlobalOpenTelemetry 指向本测试的收集器。
        // ArticleSpans 用的是 GlobalOpenTelemetry.getTracer(...)，不装 SDK 就是 no-op（永远导不出 span）。
        GlobalOpenTelemetry.resetForTest();
        GlobalOpenTelemetry.set(OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(EXPORTER))
                        .build())
                .build());
    }

    @AfterAll
    static void uninstallSdk() {
        GlobalOpenTelemetry.resetForTest();
    }

    // ---------- 被测对象 ----------

    private ArticleMapper articleMapper;
    private ArticleObjectStorageClient storage;
    private ArticleAtomicService atomicService;
    private ArticlePublicDomainService publicService;
    private KafkaTemplate<String, String> kafkaTemplate;
    private ArticleDomainService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        EXPORTER.clear();
        articleMapper = mock(ArticleMapper.class);
        storage = mock(ArticleObjectStorageClient.class);
        atomicService = mock(ArticleAtomicService.class);
        publicService = mock(ArticlePublicDomainService.class);
        kafkaTemplate = mock(KafkaTemplate.class);

        when(storage.getObjectNamePrefixForArticle()).thenReturn("articles/content/");
        when(storage.getObjectNamePrefixForImage()).thenReturn("articles/images/");
        when(storage.getBucketName()).thenReturn("articles");
        // ⚠️ 必须 stub：mock 默认返 null ⇒ protobuf 的 setFileUrl(null) 会 NPE（与 span 无关）
        when(storage.buildPublicFileURL(anyString())).thenReturn("https://cdn.example/articles/x.png");

        // ⚠️ 必须 stub send 的返回值：实现用 .get() 等 broker 确认 ⇒ mock 默认返回 null 会 NPE。
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        SnowflakeBean snowflake = new SnowflakeBean(new SnowflakeProperties());
        ArticleRpcProperties properties = new ArticleRpcProperties();
        properties.getKqPusherConf().setTopic("article-creation-topic");

        service = new ArticleDomainService(articleMapper, storage, atomicService, publicService,
                new ArticleRpcMetrics(new SimpleMeterRegistry()), snowflake, properties, kafkaTemplate);
    }

    // ---------- 断言助手 ----------

    private static Optional<SpanData> findSpan(String name) {
        synchronized (EXPORTER.spans) {
            return EXPORTER.spans.stream().filter(s -> name.equals(s.getName())).findFirst();
        }
    }

    private static List<String> exportedSpanNames() {
        synchronized (EXPORTER.spans) {
            return EXPORTER.spans.stream().map(SpanData::getName).toList();
        }
    }

    private static String stringAttr(SpanData span, String key) {
        return span.getAttributes().get(AttributeKey.stringKey(key));
    }

    private static Long longAttr(SpanData span, String key) {
        return span.getAttributes().get(AttributeKey.longKey(key));
    }

    private static Boolean boolAttr(SpanData span, String key) {
        return span.getAttributes().get(AttributeKey.booleanKey(key));
    }

    private static List<String> eventNames(SpanData span) {
        return span.getEvents().stream().map(EventData::getName).toList();
    }

    /** Go 的 {@code span.RecordError(err)} ⇒ 一个名为 {@code exception} 的事件。 */
    private static boolean hasExceptionEvent(SpanData span) {
        return span.getEvents().stream().anyMatch(e -> "exception".equals(e.getName()));
    }

    private static ArticleEntity article(String id, String authorId) {
        ArticleEntity a = new ArticleEntity();
        a.setId(id);
        a.setAuthorId(authorId);
        a.setTitle("t-" + id);
        a.setBrief("b-" + id);
        a.setContent("articles/" + id + ".md");
        a.setCoverImageUrl("");
        a.setManualTypeTag("");
        a.setSecondaryTags(new ArrayList<>());
        a.setStatus(ArticleStatus.REVIEWING_VALUE);
        a.setExtInfo(new HashMap<>());
        a.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        a.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return a;
    }

    // ====================================================================================
    // ⓪ 装置活性（没有它，下面「没有 span」的断言会平凡通过）
    // ====================================================================================

    @Test
    @DisplayName("⓪ 装置活性：装上 SDK 后，同线程挂的真 span **能**收到属性（否则下面全部平凡通过）")
    void collectorIsAlive() {
        Span probe = GlobalOpenTelemetry.getTracer("gate").spanBuilder("gate.probe").startSpan();
        try (io.opentelemetry.context.Scope ignored = probe.makeCurrent()) {
            probe.setAttribute("gate.probe.attr", "v");
        } finally {
            probe.end();
        }
        assertTrue(exportedSpanNames().contains("gate.probe"),
                "收集器没收到探针 span ⇒ 下面的断言全部平凡通过。实际导出 = " + exportedSpanNames());
    }

    // ====================================================================================
    // ① 6 个 logic span：名字 / tracer 名 / 属性 / 事件
    // ====================================================================================

    @Test
    @DisplayName("✅ createArticle 导出 CreateArticle（tracer=article-rpc）＋ author_id/title/article_id")
    void createArticleExportsGoSpan() {
        service.createArticle(CreateArticleRequest.newBuilder()
                .setAuthorId("1001").setTitle("hello").setMarkdownContent("# md").build());

        SpanData span = findSpan(ArticleSpans.CREATE_ARTICLE).orElseThrow(
                () -> new AssertionError("没有导出 CreateArticle span。实际导出 = " + exportedSpanNames()));
        assertEquals("article-rpc", span.getInstrumentationScopeInfo().getName(),
                "Go: create_article_logic.go:43 ⇒ otel.Tracer(\"article-rpc\")");
        assertEquals(SpanKind.INTERNAL, span.getKind(),
                "Go 的 tracer.Start 没带 WithSpanKind ⇒ 默认 INTERNAL");
        assertEquals("1001", stringAttr(span, ArticleSpans.ATTR_AUTHOR_ID));
        assertEquals("hello", stringAttr(span, ArticleSpans.ATTR_TITLE));
        assertNotNull(stringAttr(span, ArticleSpans.ATTR_ARTICLE_ID),
                "Go: create_article_logic.go:57 ⇒ span.SetAttributes(article_id)");
    }

    @Test
    @DisplayName("✅ getArticle 成功路径导出 GetArticle ＋ article_found=true ＋ 8 个事件（名逐字，带空格）")
    void getArticleExportsGoSpanWithEightEvents() throws Exception {
        when(articleMapper.selectById("a-1")).thenReturn(article("a-1", "1001"));
        when(storage.readString(anyString())).thenReturn("# body");

        // ⚠️ incrView 必须**打开** ⇒ Go 的 "start incr view count" / "incr view count success"
        //    这两个事件只在 in.IncrView 为真时才发（get_article_logic.go:70-78）。
        service.getArticle(GetArticleRequest.newBuilder()
                .setArticleId("a-1").setIncrView(true).build());

        SpanData span = findSpan(ArticleSpans.GET_ARTICLE).orElseThrow(
                () -> new AssertionError("没有导出 GetArticle span。实际导出 = " + exportedSpanNames()));
        assertEquals("article-rpc", span.getInstrumentationScopeInfo().getName());
        assertEquals("a-1", stringAttr(span, ArticleSpans.ATTR_ARTICLE_ID));
        assertEquals(Boolean.TRUE, boolAttr(span, ArticleSpans.ATTR_ARTICLE_FOUND),
                "Go: get_article_logic.go:65 ⇒ span.SetAttributes(article_found=true)");
        // 🔴 8 个事件，**名字带空格**，顺序与 Go 一致
        assertEquals(List.of(
                        ArticleSpans.EV_START_FIND_ARTICLE,
                        ArticleSpans.EV_FIND_ARTICLE_SUCCESS,
                        ArticleSpans.EV_START_INCR_VIEW_COUNT,
                        ArticleSpans.EV_INCR_VIEW_COUNT_SUCCESS,
                        ArticleSpans.EV_START_GET_MINIO_OBJECT,
                        ArticleSpans.EV_GET_MINIO_OBJECT_SUCCESS,
                        ArticleSpans.EV_START_READ_MINIO_OBJECT,
                        ArticleSpans.EV_READ_MINIO_OBJECT_SUCCESS),
                eventNames(span),
                "Go: get_article_logic.go:53/64/71/77/82/93/95/102 ⇒ 8 个事件，顺序不可变");
    }

    @Test
    @DisplayName("✅ uploadFile 导出 UploadFile ＋ file_name/file_id/content_type/object_name ＋ 2 个事件")
    void uploadFileExportsGoSpan() {
        service.uploadFile(UploadFileRequest.newBuilder()
                .setFileName("pic.png")
                .setContent(com.google.protobuf.ByteString.copyFromUtf8("bytes"))
                .build());

        SpanData span = findSpan(ArticleSpans.UPLOAD_FILE).orElseThrow(
                () -> new AssertionError("没有导出 UploadFile span。实际导出 = " + exportedSpanNames()));
        assertEquals("article-rpc", span.getInstrumentationScopeInfo().getName());
        assertEquals("pic.png", stringAttr(span, ArticleSpans.ATTR_FILE_NAME));
        assertTrue(longAttr(span, ArticleSpans.ATTR_FILE_ID) > 0L,
                "Go: upload_file_logic.go:53 ⇒ attribute.Int64(\"file_id\", id)");
        assertEquals("image/png", stringAttr(span, ArticleSpans.ATTR_CONTENT_TYPE));
        assertTrue(stringAttr(span, ArticleSpans.ATTR_OBJECT_NAME).startsWith("articles/images/"));
        assertEquals(List.of(ArticleSpans.EV_START_UPLOAD_TO_MINIO, ArticleSpans.EV_UPLOAD_TO_MINIO_SUCCESS),
                eventNames(span),
                "Go: upload_file_logic.go:66 / :82");
    }

    @Test
    @DisplayName("✅ listArticles 作者视角导出 ListArticles ＋ 4 个起始属性 ＋ total_articles ＋ 2 个事件")
    void listArticlesExportsGoSpan() {
        when(articleMapper.countAuthorView(any(), any(), any())).thenReturn(7L);
        when(articleMapper.selectAuthorView(any(), any(), any(), anyString(), anyBoolean(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(new ArrayList<>());

        service.listArticles(ListArticlesRequest.newBuilder()
                .setPage(3).setPageSize(25).setSortBy("created_at").setDesc(true)
                .setAuthorId("1001").build());

        SpanData span = findSpan(ArticleSpans.LIST_ARTICLES).orElseThrow(
                () -> new AssertionError("没有导出 ListArticles span。实际导出 = " + exportedSpanNames()));
        assertEquals(3L, longAttr(span, ArticleSpans.ATTR_PAGE),
                "Go: list_articles_logic.go:33 ⇒ attribute.Int64(\"page\", …)");
        assertEquals(25L, longAttr(span, ArticleSpans.ATTR_PAGE_SIZE));
        assertEquals("created_at", stringAttr(span, ArticleSpans.ATTR_SORT_BY));
        assertEquals(Boolean.TRUE, boolAttr(span, ArticleSpans.ATTR_DESC),
                "Go: list_articles_logic.go:36 ⇒ attribute.Bool(\"desc\", …)");
        assertEquals("1001", stringAttr(span, ArticleSpans.ATTR_AUTHOR_ID),
                "Go: list_articles_logic.go:59 ⇒ 条件属性");
        assertEquals(7L, longAttr(span, ArticleSpans.ATTR_TOTAL_ARTICLES),
                "Go: list_articles_logic.go:71 ⇒ attribute.Int64(\"total_articles\", total)");
        assertEquals(List.of(ArticleSpans.EV_START_DB_LIST, ArticleSpans.EV_DB_LIST_SUCCESS),
                eventNames(span), "Go: list_articles_logic.go:62 / :70");
    }

    // ====================================================================================
    // ② 「有没有 span」的**双向**断言（Go 在 span 之前 return ⇒ 那条路径不得有 span）
    // ====================================================================================

    @Test
    @DisplayName("🔴 getArticle 的公开分支 **不得**产生 GetArticle span（Go :41-43 在 tracer.Start 之前 return）")
    void getArticlePublicBranchHasNoSpan() {
        try {
            service.getArticle(GetArticleRequest.newBuilder()
                    .setArticleId("a-1").setPublicOnly(true).build());
        } catch (RuntimeException ignored) {
            // 公开分支的返回值组装细节与本守门无关；本守门只关心「有没有产生 span」
        }
        assertFalse(exportedSpanNames().contains(ArticleSpans.GET_ARTICLE),
                "Go: get_article_logic.go:41-43 的公开分支在 span 之前 return ⇒ 不该有 span。实际导出 = "
                        + exportedSpanNames());
    }

    @Test
    @DisplayName("🔴 getArticle 的「作者身份 + 计阅读量」非法组合 **不得**产生 span（Go :44-46 在 span 之前）")
    void getArticleIllegalComboHasNoSpan() {
        assertThrows(RuntimeException.class, () -> service.getArticle(GetArticleRequest.newBuilder()
                .setArticleId("a-1").setRequesterId("1001").setIncrView(true).build()));
        assertFalse(exportedSpanNames().contains(ArticleSpans.GET_ARTICLE),
                "Go: get_article_logic.go:44-46 在 span 之前 ⇒ 不该有 span。实际导出 = " + exportedSpanNames());
    }

    @Test
    @DisplayName("🔴 listArticles 的公开分支**有** span（Go :32 在 :46 之前）且**不设**那三个条件属性")
    void listArticlesPublicBranchHasSpanButNoConditionalAttributes() {
        service.listArticles(ListArticlesRequest.newBuilder()
                .setPublicOnly(true)
                .setManualTypeTag("tag").setSecondaryTag("sec").setAuthorId("1001").build());

        SpanData span = findSpan(ArticleSpans.LIST_ARTICLES).orElseThrow(
                () -> new AssertionError("公开分支也该有 ListArticles span。实际导出 = " + exportedSpanNames()));
        assertEquals("article-rpc", span.getInstrumentationScopeInfo().getName());
        assertNull(stringAttr(span, ArticleSpans.ATTR_MANUAL_TYPE_TAG),
                "Go: list_articles_logic.go:46-48 公开分支直接 return ⇒ :51 的 manual_type_tag 不设");
        assertNull(stringAttr(span, ArticleSpans.ATTR_SECONDARY_TAG));
        assertNull(stringAttr(span, ArticleSpans.ATTR_AUTHOR_ID));
        assertTrue(eventNames(span).isEmpty(), "公开分支没有 db list 事件");
    }

    // ====================================================================================
    // ③ 「Go 记不记这个错误」的逐条断言（article 域**不是**业务码段判据）
    // ====================================================================================

    @Test
    @DisplayName("🔴 getArticle 的 NotFound：**不记**异常事件，但 article_found=false（Go :56-58）")
    void getArticleNotFoundIsNotRecorded() {
        when(articleMapper.selectById("a-1")).thenReturn(null);

        assertThrows(RuntimeException.class, () -> service.getArticle(
                GetArticleRequest.newBuilder().setArticleId("a-1").build()));

        SpanData span = findSpan(ArticleSpans.GET_ARTICLE).orElseThrow(
                () -> new AssertionError("没有导出 GetArticle span。实际导出 = " + exportedSpanNames()));
        assertEquals(Boolean.FALSE, boolAttr(span, ArticleSpans.ATTR_ARTICLE_FOUND),
                "Go: get_article_logic.go:57 ⇒ article_found=false");
        assertFalse(hasExceptionEvent(span),
                "Go 在 NotFound 分支**不 RecordError** ⇒ 不该有 exception 事件。实际事件 = " + eventNames(span));
        assertEquals(List.of(ArticleSpans.EV_START_FIND_ARTICLE), eventNames(span),
                "Go: get_article_logic.go:53 ⇒ NotFound 之前只有 1 个事件");
        assertEquals(io.opentelemetry.api.trace.StatusCode.UNSET, span.getStatus().getStatusCode(),
                "Go 侧无 SetStatus ⇒ 状态保持 UNSET");
    }

    @Test
    @DisplayName("🔴 getArticle 的 PermissionDenied：**不记**异常事件（Go :66-68）")
    void getArticlePermissionDeniedIsNotRecorded() {
        when(articleMapper.selectById("a-1")).thenReturn(article("a-1", "1001"));

        assertThrows(RuntimeException.class, () -> service.getArticle(GetArticleRequest.newBuilder()
                .setArticleId("a-1").setRequesterId("2002").build()));

        SpanData span = findSpan(ArticleSpans.GET_ARTICLE).orElseThrow(
                () -> new AssertionError("没有导出 GetArticle span。实际导出 = " + exportedSpanNames()));
        assertFalse(hasExceptionEvent(span),
                "Go 在 PermissionDenied 分支**不 RecordError**。实际事件 = " + eventNames(span));
        assertEquals(List.of(ArticleSpans.EV_START_FIND_ARTICLE, ArticleSpans.EV_FIND_ARTICLE_SUCCESS),
                eventNames(span), "Go: get_article_logic.go:53 / :64 ⇒ 权限判定之前已发这 2 个事件");
    }

    @Test
    @DisplayName("🔴 getArticle 的 IncrViewCount 失败：**记了但不返回**（Go :73-75）⇒ 有异常事件且方法仍成功")
    void getArticleIncrViewFailureIsRecordedButNotReturned() throws Exception {
        when(articleMapper.selectById("a-1")).thenReturn(article("a-1", "1001"));
        when(storage.readString(anyString())).thenReturn("# body");
        when(articleMapper.incrViewCount("a-1")).thenThrow(new RuntimeException("db write failed"));

        // ⚠️ Go 在这里只记不抛 ⇒ 方法**成功返回**
        assertNotNull(service.getArticle(GetArticleRequest.newBuilder()
                .setArticleId("a-1").setIncrView(true).build()),
                "Go: get_article_logic.go:73-75 记了错误但**继续往下走** ⇒ 方法必须成功返回");

        SpanData span = findSpan(ArticleSpans.GET_ARTICLE).orElseThrow(
                () -> new AssertionError("没有导出 GetArticle span。实际导出 = " + exportedSpanNames()));
        assertTrue(hasExceptionEvent(span),
                "Go: get_article_logic.go:73 ⇒ span.RecordError(err) ⇒ 必须有 exception 事件。实际事件 = "
                        + eventNames(span));
    }

    @Test
    @DisplayName("🔴 updateArticle 的 NotFound：**要记**异常事件（Go :59-63，与 get 的 NotFound 不同）")
    void updateArticleNotFoundIsRecorded() {
        when(articleMapper.selectById("a-1")).thenReturn(null);

        assertThrows(RuntimeException.class, () -> service.updateArticle(
                UpdateArticleRequest.newBuilder().setArticleId("a-1").build()));

        SpanData span = findSpan(ArticleSpans.UPDATE_ARTICLE).orElseThrow(
                () -> new AssertionError("没有导出 UpdateArticle span。实际导出 = " + exportedSpanNames()));
        assertTrue(hasExceptionEvent(span),
                "Go: update_article_logic.go:61 ⇒ span.RecordError(NotFound) ⇒ 必须有 exception 事件。实际事件 = "
                        + eventNames(span));
    }

    @Test
    @DisplayName("🔴 deleteArticle 的 PermissionDenied：**要记**异常事件（Go :58-62）")
    void deleteArticlePermissionDeniedIsRecorded() {
        when(articleMapper.selectById("a-1")).thenReturn(article("a-1", "1001"));

        assertThrows(RuntimeException.class, () -> service.deleteArticle(DeleteArticleRequest.newBuilder()
                .setArticleId("a-1").setOperatorId("2002").build()));

        SpanData span = findSpan(ArticleSpans.DELETE_ARTICLE).orElseThrow(
                () -> new AssertionError("没有导出 DeleteArticle span。实际导出 = " + exportedSpanNames()));
        assertTrue(hasExceptionEvent(span),
                "Go: delete_article_logic.go:60 ⇒ span.RecordError(PermissionDenied) ⇒ 必须有 exception 事件。实际事件 = "
                        + eventNames(span));
    }

    // ====================================================================================
    // ④ 2 个 consumer span：名字 / SpanKind=CONSUMER / 4 个属性（destination 是**逻辑名**）
    // ====================================================================================

    @Test
    @DisplayName("✅ ArticleReviewConsumer 导出 ArticleConsumer.Consume（CONSUMER）＋ destination=article_review（逻辑名，非 topic）")
    void reviewConsumerExportsGoSpan() {
        ArticleReviewConsumer consumer = new ArticleReviewConsumer(new ObjectMapper(), articleMapper,
                atomicService, storage, mock(ContentSecurityPort.class),
                new ArticleRpcMetrics(new SimpleMeterRegistry()), mock(SnowflakeBean.class));

        // 非法 JSON ⇒ Go :49-52 的丢弃分支（return nil）⇒ span 存在、**无**异常事件
        consumer.onMessage(new ConsumerRecord<>("article-creation-topic", 0, 1L, "key-1", "not-json"), "not-json");

        SpanData span = findSpan(ArticleSpans.CONSUMER_REVIEW).orElseThrow(
                () -> new AssertionError("没有导出 ArticleConsumer.Consume span。实际导出 = " + exportedSpanNames()));
        assertEquals("article-rpc", span.getInstrumentationScopeInfo().getName());
        assertEquals(SpanKind.CONSUMER, span.getKind(),
                "Go: service/common/observability/mq.go ⇒ trace.WithSpanKind(trace.SpanKindConsumer)");
        assertEquals("kafka", stringAttr(span, ArticleSpans.ATTR_MESSAGING_SYSTEM));
        assertEquals("article_review", stringAttr(span, ArticleSpans.ATTR_MESSAGING_DESTINATION),
                "🔴 Go: article_consumer.go:45 传的是**逻辑名** \"article_review\"，"
                        + "**不是** kafka topic \"article-creation-topic\"");
        assertEquals("consume", stringAttr(span, ArticleSpans.ATTR_MESSAGING_OPERATION));
        assertEquals("key-1", stringAttr(span, ArticleSpans.ATTR_MESSAGING_MESSAGE_KEY));
        assertFalse(hasExceptionEvent(span), "丢弃分支不应有 exception 事件。实际事件 = " + eventNames(span));
    }

    @Test
    @DisplayName("🔴 审核消费者失败 ⇒ span 记错 **且** 异常必须逃出（Go 的 kq：Consume 返回 error ⇒ 重投）")
    void reviewConsumerFailureMarksSpanAndRethrows() throws Exception {
        ArticleReviewConsumer consumer = new ArticleReviewConsumer(new ObjectMapper(), articleMapper,
                atomicService, storage, mock(ContentSecurityPort.class),
                new ArticleRpcMetrics(new SimpleMeterRegistry()), mock(SnowflakeBean.class));

        ArticleEntity a = article("a-1", "1001");
        a.setExtInfo(new HashMap<>(Map.of(ArticleSyncEventTypes.EXT_REVIEW_NONCE, "nonce-1")));
        when(articleMapper.selectById("a-1")).thenReturn(a);
        when(storage.readString(anyString())).thenThrow(new RuntimeException("minio down"));

        String json = "{\"article_id\":\"a-1\",\"author_id\":\"1001\","
                + "\"content_path\":\"articles/a-1.md\",\"review_nonce\":\"nonce-1\"}";
        assertThrows(RuntimeException.class, () -> consumer.onMessage(
                        new ConsumerRecord<>("article-creation-topic", 0, 1L, "key-1", json), json),
                "🔴 Go 的 kq：Consume 返回 error ⇒ 不确认位点 ⇒ **必须抛出**而不是吞掉");

        SpanData span = findSpan(ArticleSpans.CONSUMER_REVIEW).orElseThrow(
                () -> new AssertionError("没有导出 ArticleConsumer.Consume span。实际导出 = " + exportedSpanNames()));
        assertTrue(hasExceptionEvent(span), "失败必须进 span。实际事件 = " + eventNames(span));
        assertEquals(io.opentelemetry.api.trace.StatusCode.ERROR, span.getStatus().getStatusCode(),
                "Go 的 MarkSystemError 会置 ERROR ⇒ 共享层 markSystemError 同款");
    }

    @Test
    @DisplayName("✅ ArticleSyncResultConsumer 导出 ArticleSyncResultConsumer.Consume ＋ destination=article_sync_result")
    void syncResultConsumerExportsGoSpan() {
        ArticleSyncResultConsumer consumer = new ArticleSyncResultConsumer(new ObjectMapper(), articleMapper,
                mock(ArticleSyncOutboxMapper.class), atomicService, mock(MessageRpcClient.class));

        consumer.onMessage(new ConsumerRecord<>("article-sync-results", 0, 1L, "key-2", "not-json"), "not-json");

        SpanData span = findSpan(ArticleSpans.CONSUMER_SYNC_RESULT).orElseThrow(
                () -> new AssertionError("没有导出 ArticleSyncResultConsumer.Consume span。实际导出 = "
                        + exportedSpanNames()));
        assertEquals(SpanKind.CONSUMER, span.getKind());
        assertEquals("article_sync_result", stringAttr(span, ArticleSpans.ATTR_MESSAGING_DESTINATION),
                "🔴 Go: article_sync_result_consumer.go:40 传的是**逻辑名**，不是 topic \"article-sync-results\"");
        assertEquals("key-2", stringAttr(span, ArticleSpans.ATTR_MESSAGING_MESSAGE_KEY));
    }
}
