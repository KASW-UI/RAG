package com.rag.article.article.rpc.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.article.article.proto.ArticleStatus;
import com.rag.article.article.rpc.client.ArticleObjectStorageClient;
import com.rag.article.article.rpc.client.ContentSecurityPort;
import com.rag.article.article.rpc.domain.ArticleEntity;
import com.rag.article.article.rpc.mapper.ArticleMapper;
import com.rag.article.article.rpc.metrics.ArticleRpcMetrics;
import com.rag.article.article.rpc.service.ArticleAtomicService;
import com.rag.common.spring.SnowflakeBean;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 审核消费者的分支语义 —— <b>方向测试：哪一条必须丢、哪一条必须重投</b>。
 *
 * <h2>为什么这类测试不能省</h2>
 * <p>Go 的 ack 契约是「{@code Consume} 返回 error ⇒ 不确认」，
 * Spring Kafka 恰好相反：「listener 正常返回 ⇒ 提交位点；抛异常 ⇒ 不提交」。
 * ⇒ Java 侧必须把 Go 的<b>五条丢弃分支</b>翻译成正常 return，
 * 把<b>三条重投分支</b>翻译成抛异常。
 * 只要有一条方向写反，就是一条静默的丢消息或无限重投路径，
 * 而且<b>不会让任何单测变红</b>——它只在真实 Kafka 上才显形。
 *
 * <p>因此这里的判据是 <b>「有没有抛异常」</b>，而不是断言内部调用。
 */
class ArticleReviewConsumerTest {

    private final ArticleMapper articleMapper = mock(ArticleMapper.class);
    private final ArticleAtomicService atomicService = mock(ArticleAtomicService.class);
    private final ArticleObjectStorageClient storage = mock(ArticleObjectStorageClient.class);
    private final ContentSecurityPort security = mock(ContentSecurityPort.class);
    private final ArticleRpcMetrics metrics = mock(ArticleRpcMetrics.class);
    private final SnowflakeBean snowflake = mock(SnowflakeBean.class);
    private final ObjectMapper mapper = new ObjectMapper();

    private ArticleReviewConsumer consumer() {
        return new ArticleReviewConsumer(mapper, articleMapper, atomicService,
                storage, security, metrics, snowflake);
    }

    private static ConsumerRecord<String, String> record(String json) {
        return new ConsumerRecord<>("article-creation-topic", 0, 1L, "k", json);
    }

    /** 一篇状态正确、四元组齐全的 REVIEWING 文章。 */
    private ArticleEntity reviewingArticle() {
        ArticleEntity a = new ArticleEntity();
        a.setId("a-1");
        a.setAuthorId("1001");
        a.setContent("articles/a-1.md");
        a.setCoverImageUrl("");
        a.setStatus(ArticleStatus.REVIEWING_VALUE);
        Map<String, String> ext = new HashMap<>();
        ext.put(ArticleSyncEventTypes.EXT_REVIEW_NONCE, "nonce-1");
        a.setExtInfo(ext);
        return a;
    }

    private static String reviewJson(String articleId, String authorId, String contentPath, String nonce) {
        ArticleSyncEventTypes.ArticleReviewMessage m = new ArticleSyncEventTypes.ArticleReviewMessage();
        m.articleId = articleId;
        m.authorId = authorId;
        m.contentPath = contentPath;
        m.reviewNonce = nonce;
        try {
            return new ObjectMapper().writeValueAsString(m);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 同时桩掉两条读路径：正文走 readString，图片走 get + readAll。 */
    private void givenStorage(String content) throws Exception {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        when(storage.readString(anyString())).thenReturn(content);
        when(storage.get(anyString())).thenReturn(new ByteArrayInputStream(bytes));
        when(storage.readAll(any())).thenReturn(bytes);
    }

    /** 该桩内容对应的 base64，用于断言 data URI 的字节部分。 */
    private static String b64(String content) {
        return java.util.Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
    }

    private void givenCleanAudit() {
        when(security.sanitizeText(anyString()))
                .thenReturn(new ContentSecurityPort.TextAuditResult(true, false, 0f, "", ""));
    }

    // ==================================================================
    // 1. 五条「必须丢弃」分支 —— 正常返回，绝不抛异常
    // ==================================================================

    @Test
    @DisplayName("丢弃①：JSON 解析失败 ⇒ 正常返回（位点提交），Go :49-52")
    void malformedJsonIsDroppedNotRetried() {
        ArticleReviewConsumer c = consumer();
        assertDoesNotThrow(() -> c.onMessage(record("{not json"), "{not json"),
                "Go 在此返回 nil 提交位点；Java 若抛异常就变成无限重投");
        verifyNoInteractions(atomicService);
    }

    @Test
    @DisplayName("丢弃②：文章不存在 ⇒ 正常返回，Go :55-57")
    void missingArticleIsDropped() {
        when(articleMapper.selectById("a-1")).thenReturn(null);
        ArticleReviewConsumer c = consumer();
        assertDoesNotThrow(() -> c.onMessage(record(reviewJson("a-1", "1001", "articles/a-1.md", "nonce-1")), reviewJson("a-1", "1001", "articles/a-1.md", "nonce-1")));
        verifyNoInteractions(storage, atomicService);
    }

    @Test
    @DisplayName("丢弃③：四元组任一不匹配 ⇒ 正常返回（过期审核任务），Go :62-68")
    void staleReviewTupleIsDropped() {
        ArticleEntity a = reviewingArticle();
        // nonce 不匹配：作者改过标题但对象路径没变，旧任务带着旧 nonce 进来
        when(articleMapper.selectById("a-1")).thenReturn(a);

        ArticleReviewConsumer c = consumer();
        String stale = reviewJson("a-1", "1001", "articles/a-1.md", "STALE-NONCE");
        assertDoesNotThrow(() -> c.onMessage(record(stale), stale),
                "过期任务必须被丢弃，不能拿旧 nonce 去审核新内容");
        verifyNoInteractions(storage, atomicService);
    }

    @Test
    @DisplayName("丢弃④：幂等命中（pending + 已有 last_sync_event_id）⇒ 不入队，Go :85-87")
    void alreadyQueuedReviewIsDropped() throws Exception {
        ArticleEntity a = reviewingArticle();
        Map<String, String> ext = new HashMap<>(a.getExtInfo());
        ext.put(ArticleSyncEventTypes.EXT_RECO_SYNC_STATE, "pending");
        ext.put(ArticleSyncEventTypes.EXT_LAST_SYNC_EVENT_ID, "evt-already");
        a.setExtInfo(ext);
        when(articleMapper.selectById("a-1")).thenReturn(a);
        givenStorage("# hello");
        givenCleanAudit();

        String json = reviewJson("a-1", "1001", "articles/a-1.md", "nonce-1");
        ArticleReviewConsumer c = consumer();
        assertDoesNotThrow(() -> c.onMessage(record(json), json));
        verify(atomicService, never()).queueSyncOutboxWithLock(any(), any(), any(), any(), any(), any(), anyLong());
    }

    @Test
    @DisplayName("丢弃⑤：审核判定为广告 ⇒ 拒稿并返回，不入队，Go :92-95")
    void rejectedArticleIsDroppedAfterMarkingRejected() throws Exception {
        ArticleEntity a = reviewingArticle();
        when(articleMapper.selectById("a-1")).thenReturn(a);
        givenStorage("# buy now");
        when(security.sanitizeText(anyString()))
                .thenReturn(new ContentSecurityPort.TextAuditResult(true, true, 0.99f, "", "ad"));

        String json = reviewJson("a-1", "1001", "articles/a-1.md", "nonce-1");
        ArticleReviewConsumer c = consumer();
        assertDoesNotThrow(() -> c.onMessage(record(json), json));

        assertEquals(ArticleStatus.REJECTED_VALUE, a.getStatus(), "命中广告后未置为 REJECTED");
        verify(articleMapper).updateById(a);
        verify(atomicService, never()).queueSyncOutboxWithLock(any(), any(), any(), any(), any(), any(), anyLong());
    }

    // ==================================================================
    // 2. 三条「必须重投」分支 —— 抛异常，位点不提交
    // ==================================================================

    @Test
    @DisplayName("重投①：MinIO 读正文失败 ⇒ 抛异常，Go :70-81")
    void minioReadFailureIsRetried() throws Exception {
        ArticleEntity a = reviewingArticle();
        when(articleMapper.selectById("a-1")).thenReturn(a);
        when(storage.readString(anyString())).thenThrow(new RuntimeException("minio down"));

        String json = reviewJson("a-1", "1001", "articles/a-1.md", "nonce-1");
        ArticleReviewConsumer c = consumer();
        assertThrows(RuntimeException.class, () -> c.onMessage(record(json), json),
                "正文读不到必须重投；静默 return 会把审核任务丢掉并永久卡住文章");
        verify(metrics).minioError("get");
    }

    @Test
    @DisplayName("重投②：审核 RPC 失败 ⇒ 抛异常（绝不是「当成没问题」），Go :89-91")
    void securityAuditFailureIsRetried() throws Exception {
        ArticleEntity a = reviewingArticle();
        when(articleMapper.selectById("a-1")).thenReturn(a);
        givenStorage("# hello");
        when(security.sanitizeText(anyString()))
                .thenThrow(new ContentSecurityPort.SecurityAuditException("rpc down"));

        String json = reviewJson("a-1", "1001", "articles/a-1.md", "nonce-1");
        ArticleReviewConsumer c = consumer();
        assertThrows(ContentSecurityPort.SecurityAuditException.class,
                () -> c.onMessage(record(json), json),
                "审核不可用时必须 fail-loud；返回「不是广告」等于放行未审核内容");
        verify(atomicService, never()).queueSyncOutboxWithLock(any(), any(), any(), any(), any(), any(), anyLong());
    }

    @Test
    @DisplayName("重投③：图片审核失败 ⇒ 抛异常（Go :190-192，不是判定为通过）")
    void imageAuditFailureIsRetried() throws Exception {
        ArticleEntity a = reviewingArticle();
        when(articleMapper.selectById("a-1")).thenReturn(a);
        givenStorage("![x](https://host/articles/images/1.png)");
        givenCleanAudit();
        when(security.detectImageAd(anyString()))
                .thenThrow(new ContentSecurityPort.SecurityAuditException("image rpc down"));

        String json = reviewJson("a-1", "1001", "articles/a-1.md", "nonce-1");
        ArticleReviewConsumer c = consumer();
        assertThrows(RuntimeException.class, () -> c.onMessage(record(json), json));
    }

    // ==================================================================
    // 3. 正常路径：入队参数必须与冻结输入一致
    // ==================================================================

    @Test
    @DisplayName("正常路径：入队的 markdown 来自 MinIO，且四个入参逐字对应消息")
    void happyPathQueuesWithFrozenInput() throws Exception {
        ArticleEntity a = reviewingArticle();
        when(articleMapper.selectById("a-1")).thenReturn(a);
        givenStorage("# approved body");
        givenCleanAudit();
        when(snowflake.nextId()).thenReturn(987654321L);
        when(atomicService.queueSyncOutboxWithLock(any(), any(), any(), any(), any(), any(), anyLong()))
                .thenReturn(true);

        String json = reviewJson("a-1", "1001", "articles/a-1.md", "nonce-1");
        ArticleReviewConsumer c = consumer();
        c.onMessage(record(json), json);

        ArgumentCaptor<String> md = ArgumentCaptor.forClass(String.class);
        verify(atomicService).queueSyncOutboxWithLock(eq("a-1"), eq("1001"), eq("articles/a-1.md"),
                eq("nonce-1"), md.capture(), eq("987654321"), anyLong());
        assertEquals("# approved body", md.getValue(),
                "入队的 markdown 必须是对象存储里冻结的那份，而不是消息里的路径");
    }

    // ==================================================================
    // 4. 四元组：四项都必须校验
    // ==================================================================

    @Test
    @DisplayName("四元组必须四项全查：status / content / author / nonce 少一项都不通过")
    void reviewTupleChecksAllFourFields() {
        ArticleSyncEventTypes.ArticleReviewMessage msg = new ArticleSyncEventTypes.ArticleReviewMessage();
        msg.articleId = "a-1";
        msg.authorId = "1001";
        msg.contentPath = "articles/a-1.md";
        msg.reviewNonce = "nonce-1";

        ArticleEntity ok = reviewingArticle();
        assertTrue(ArticleReviewConsumer.matchesReviewTuple(ok, msg), "基线：四项全等应当通过");

        ArticleEntity wrongStatus = reviewingArticle();
        wrongStatus.setStatus(ArticleStatus.PUBLISHED_VALUE);
        assertFalse(ArticleReviewConsumer.matchesReviewTuple(wrongStatus, msg), "status 不符必须拒绝");

        ArticleEntity wrongContent = reviewingArticle();
        wrongContent.setContent("articles/OTHER.md");
        assertFalse(ArticleReviewConsumer.matchesReviewTuple(wrongContent, msg), "content 不符必须拒绝");

        ArticleEntity wrongAuthor = reviewingArticle();
        wrongAuthor.setAuthorId("2002");
        assertFalse(ArticleReviewConsumer.matchesReviewTuple(wrongAuthor, msg), "author 不符必须拒绝");

        ArticleEntity wrongNonce = reviewingArticle();
        wrongNonce.setExtInfo(Map.of(ArticleSyncEventTypes.EXT_REVIEW_NONCE, "other-nonce"));
        assertFalse(ArticleReviewConsumer.matchesReviewTuple(wrongNonce, msg), "nonce 不符必须拒绝");
    }

    // ==================================================================
    // 5. 拒稿写回的三个字段
    // ==================================================================

    @Test
    @DisplayName("拒稿：sync_state=rejected / sync_result=failed / 原因可区分 / eventId 清空")
    void rejectionWritesDistinguishableReasonAndClearsEventId() throws Exception {
        ArticleEntity a = reviewingArticle();
        Map<String, String> ext = new LinkedHashMap<>(a.getExtInfo());
        ext.put(ArticleSyncEventTypes.EXT_PENDING_SYNC_REASON, "update");
        ext.put(ArticleSyncEventTypes.EXT_LAST_SYNC_EVENT_ID, "old-event");
        a.setExtInfo(ext);
        when(articleMapper.selectById("a-1")).thenReturn(a);
        givenStorage("![x](https://host/articles/images/1.png)");
        givenCleanAudit();
        when(storage.get(anyString())).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));
        when(storage.readAll(any())).thenReturn(new byte[]{1, 2, 3});
        when(security.detectImageAd(anyString()))
                .thenReturn(new ContentSecurityPort.ImageAuditResult(true, true, 0.98f));

        String json = reviewJson("a-1", "1001", "articles/a-1.md", "nonce-1");
        ArticleReviewConsumer c = consumer();
        assertDoesNotThrow(() -> c.onMessage(record(json), json));

        assertEquals(ArticleStatus.REJECTED_VALUE, a.getStatus());
        assertEquals("failed", a.getExtInfo().get(ArticleSyncEventTypes.EXT_RECO_SYNC_STATE),
                "reco_sync_state 必须是 failed（publish_stage 才是 rejected）");
        assertEquals("rejected", a.getExtInfo().get(ArticleSyncEventTypes.EXT_PUBLISH_STAGE),
                "publish_stage 必须是 rejected");
        assertEquals("image_rejected", a.getExtInfo().get(ArticleSyncEventTypes.EXT_LAST_SYNC_ERROR),
                "图片广告的原因必须是 image_rejected，运维据此区分查文本还是查图");
        assertEquals("update", a.getExtInfo().get(ArticleSyncEventTypes.EXT_LAST_SYNC_REASON),
                "拒稿时记的必须是 pending_sync_reason（本来要同步什么），不是 last_sync_reason");
        assertEquals("", a.getExtInfo().get(ArticleSyncEventTypes.EXT_LAST_SYNC_EVENT_ID),
                "eventId 必须被清空，否则重新提交时幂等判据仍会命中而永远发不出去");
    }

    // ==================================================================
    // 6. Markdown 图片抽取与 mimeType
    // ==================================================================

    @Test
    @DisplayName("图片抽取用非贪婪正则 ![.*?]\\((.*?)\\)（Go :209）")
    void imageExtractionIsNonGreedy() {
        List<String> urls = ArticleReviewConsumer.extractImageUrls(
                "a ![one](https://h/1.png) b ![two](https://h/2.png) c");
        assertEquals(List.of("https://h/1.png", "https://h/2.png"), urls);

        assertTrue(ArticleReviewConsumer.extractImageUrls("no images here").isEmpty());
        assertTrue(ArticleReviewConsumer.extractImageUrls(null).isEmpty(),
                "Go 对 nil 内容返回空切片");
    }

    @Test
    @DisplayName("mimeType 只认 png/webp，**其余一律 image/jpeg**（Go :244-250，默认不是 octet-stream）")
    void mimeTypeDefaultsToJpegNotOctetStream() throws Exception {
        givenStorage("body");
        ArticleReviewConsumer c = consumer();

        String e = b64("body");
        assertEquals("data:image/png;base64," + e,
                c.buildImageDataUri("https://host/articles/images/1.png"));
        assertEquals("data:image/webp;base64," + e,
                c.buildImageDataUri("https://host/articles/images/1.webp"));
        assertEquals("data:image/jpeg;base64," + e,
                c.buildImageDataUri("https://host/articles/images/1.jpg"));
        assertEquals("data:image/jpeg;base64," + e,
                c.buildImageDataUri("https://host/articles/images/1.gif"),
                "未知后缀回落 jpeg；改成 octet-stream 会让下游解码器选错");
        assertEquals("data:image/jpeg;base64," + e,
                c.buildImageDataUri("https://host/articles/images/noext"),
                "无后缀同样回落 jpeg");
    }

    @Test
    @DisplayName("图片路径段数不足 ⇒ 抛异常（Go :224-230），整篇重投而不是跳过这张图")
    void malformedImagePathIsAnErrorNotASkip() {
        ArticleReviewConsumer c = consumer();
        assertThrows(IllegalArgumentException.class,
                () -> c.buildImageDataUri("https://host/only-one-segment.png"),
                "段数不足必须报错——静默跳过等于让未审核的图片通过");
    }

    @Test
    @DisplayName("封面图与正文图片一起审，且封面排在正文之后（Go :185-190）")
    void coverImageIsAuditedAfterBodyImages() throws Exception {
        // 正文里放一张，封面再放一张 ⇒ 期望恰好 2 次审核调用
        givenStorage("# body ![inlined](https://host/articles/images/inlined.png)");
        ArticleEntity a = reviewingArticle();
        a.setCoverImageUrl("https://host/articles/images/cover.png");
        when(articleMapper.selectById("a-1")).thenReturn(a);
        givenCleanAudit();
        List<String> seen = new ArrayList<>();
        when(security.detectImageAd(anyString()))
                .thenAnswer(inv -> {
                    seen.add(inv.getArgument(0));
                    return new ContentSecurityPort.ImageAuditResult(true, false, 0f);
                });
        when(snowflake.nextId()).thenReturn(1L);
        when(atomicService.queueSyncOutboxWithLock(any(), any(), any(), any(), any(), any(), anyLong()))
                .thenReturn(true);

        String json = reviewJson("a-1", "1001", "articles/a-1.md", "nonce-1");
        ArticleReviewConsumer c = consumer();
        c.onMessage(record(json), json);

        assertEquals(2, seen.size(), "正文图 + 封面图应各审一次");
        for (String uri : seen) {
            assertTrue(uri.startsWith("data:image/") && uri.contains(";base64,"),
                    uri + " 必须是被读成字节再拼出的 data URI，不能把 URL 直接送下游");
        }
    }
}