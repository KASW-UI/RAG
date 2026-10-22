package com.rag.article.article.rpc.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.article.article.rpc.domain.ArticleEntity;
import com.rag.article.article.rpc.domain.ArticleRevisionEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 消息 / 事件契约的单元测试 —— 全部是 <b>L1 跨服务契约</b>的锁定。
 *
 * <p>这些断言不碰数据库：它们锁的是<b>序列化后的字节与字段名</b>，
 * 而下游（推荐 / 检索 / Sea-BreakTheWaves）按这些字段名解析，
 * 发布回调要读回冻结的 payload 做六项比对。
 * <br>⇒ 任何"顺手改个字段名 / 改个 omitempty"都会让下游静默解不出内容，
 * 而<b>两侧的测试都可能是绿的</b>。
 */
class ArticleSyncEventTypesContractTest {

    private final ObjectMapper mapper = new ObjectMapper();

    // ==================================================================
    // 1. ArticleReviewMessage：审核四元组
    // ==================================================================

    @Test
    @DisplayName("Go: ArticleReviewMessage 的四个 JSON 字段名逐字不变（审核四元组载体）")
    void reviewMessageFieldNamesAreFrozen() throws Exception {
        ArticleSyncEventTypes.ArticleReviewMessage msg = new ArticleSyncEventTypes.ArticleReviewMessage(
                "a-1", "1001", "articles/content/a-1.md", "nonce-1");

        String json = mapper.writeValueAsString(msg);
        assertTrue(json.contains("\"article_id\":\"a-1\""), json);
        assertTrue(json.contains("\"author_id\":\"1001\""), json);
        assertTrue(json.contains("\"content_path\":\"articles/content/a-1.md\""), json);
        assertTrue(json.contains("\"review_nonce\":\"nonce-1\""), json);
    }

    @Test
    @DisplayName("Go: review_nonce 是 omitempty —— 空值必须整个字段消失（不是 \"\"）")
    void reviewNonceIsOmittedWhenEmpty() throws Exception {
        ArticleSyncEventTypes.ArticleReviewMessage msg = new ArticleSyncEventTypes.ArticleReviewMessage(
                "a-1", "1001", "p.md", "");
        String json = mapper.writeValueAsString(msg);
        assertFalse(json.contains("review_nonce"),
                "空 review_nonce 被序列化成空串而不是省略 ⇒ 与 Go 的 omitempty 不一致：" + json);
    }

    // ==================================================================
    // 2. ArticleSyncEvent：omitempty 组与必出组
    // ==================================================================

    @Test
    @DisplayName("Go: ArticleSyncEvent 的必出字段全部出现（含 status 与 version_ms）")
    void syncEventAlwaysCarriesRequiredFields() throws Exception {
        ArticleEntity article = new ArticleEntity();
        article.setId("a-1");
        article.setAuthorId("1001");
        article.setTitle("");
        article.setBrief("");
        article.setCoverImageUrl("");
        article.setManualTypeTag("");
        article.setSecondaryTags(List.of());
        article.setStatus(com.rag.article.article.proto.ArticleStatus.REVIEWING_VALUE);

        ArticleSyncEventTypes.ArticleSyncEvent event = ArticleSyncEventTypes.newArticleSyncEvent(
                article, "", ArticleSyncEventTypes.OP_DELETE, ArticleSyncEventTypes.REASON_DELETE,
                "e-1", 1700000000000L);

        String json = mapper.writeValueAsString(event);
        // 这八个是 Go 里**没有** omitempty 的字段
        assertTrue(json.contains("\"event_scope\":\"article_sync\""), json);
        assertTrue(json.contains("\"event_id\":\"e-1\""), json);
        assertTrue(json.contains("\"article_id\":\"a-1\""), json);
        assertTrue(json.contains("\"op\":\"delete\""), json);
        assertTrue(json.contains("\"reason\":\"delete\""), json);
        assertTrue(json.contains("\"author_id\":\"1001\""), json);
        assertTrue(json.contains("\"version_ms\":1700000000000"), json);
        // ⚠️ delete 事件带的是**当前状态的名字**，不是数值
        assertTrue(json.contains("\"status\":\"REVIEWING\""),
                "delete 事件应报当前状态的枚举名：" + json);
    }

    @Test
    @DisplayName("Go: op=upsert 时 status 恒为 PUBLISHED（哪怕源行还在 REVIEWING）")
    void upsertForcesPublishedStatus() {
        ArticleEntity article = new ArticleEntity();
        article.setId("a-1");
        article.setAuthorId("1001");
        article.setStatus(com.rag.article.article.proto.ArticleStatus.REVIEWING_VALUE);

        ArticleSyncEventTypes.ArticleSyncEvent upsert = ArticleSyncEventTypes.newArticleSyncEvent(
                article, "# body", ArticleSyncEventTypes.OP_UPSERT, ArticleSyncEventTypes.REASON_UPDATE,
                "e-1", 1L);
        assertEquals("PUBLISHED", upsert.status,
                "upsert 事件必须报 PUBLISHED（Go NewArticleSyncEvent:97），下游据此认为内容已批准");

        ArticleSyncEventTypes.ArticleSyncEvent delete = ArticleSyncEventTypes.newArticleSyncEvent(
                article, "", ArticleSyncEventTypes.OP_DELETE, ArticleSyncEventTypes.REASON_DELETE, "e-1", 1L);
        assertEquals("REVIEWING", delete.status, "delete 事件应报当前状态名");
    }

    @Test
    @DisplayName("Go: 六字段空值被 omitempty 省略（下游按字段缺省判断「没有」）")
    void syncEventOmitsEmptyOptionalFields() throws Exception {
        ArticleEntity article = new ArticleEntity();
        article.setId("a-1");
        article.setAuthorId("1001");
        article.setTitle("");
        article.setBrief("");
        article.setCoverImageUrl("");
        article.setManualTypeTag("");
        article.setSecondaryTags(List.of());   // 空切片，Go 也会省略
        article.setStatus(com.rag.article.article.proto.ArticleStatus.PUBLISHED_VALUE);

        ArticleSyncEventTypes.ArticleSyncEvent event = ArticleSyncEventTypes.newArticleSyncEvent(
                article, "", ArticleSyncEventTypes.OP_DELETE, ArticleSyncEventTypes.REASON_DELETE, "e-1", 1L);
        String json = mapper.writeValueAsString(event);
        for (String field : List.of("title", "brief", "cover_url", "manual_type_tag",
                "secondary_tags", "markdown")) {
            assertFalse(json.contains("\"" + field + "\""),
                    "空字段 " + field + " 未被省略（Go 是 omitempty）：" + json);
        }
    }

    @Test
    @DisplayName("ArticleSyncEvent 可反序列化回同一结构（发布回调的冻结输入比对依赖它）")
    void syncEventRoundTrips() throws Exception {
        ArticleEntity article = new ArticleEntity();
        article.setId("a-2");
        article.setAuthorId("2002");
        article.setTitle("t");
        article.setBrief("b");
        article.setCoverImageUrl("c");
        article.setManualTypeTag("m");
        article.setSecondaryTags(List.of("x", "y"));
        article.setStatus(com.rag.article.article.proto.ArticleStatus.PUBLISHED_VALUE);

        ArticleSyncEventTypes.ArticleSyncEvent original = ArticleSyncEventTypes.newArticleSyncEvent(
                article, "# body", ArticleSyncEventTypes.OP_UPSERT, ArticleSyncEventTypes.REASON_CREATE,
                "e-2", 42L);

        ArticleSyncEventTypes.ArticleSyncEvent parsed = mapper.readValue(
                mapper.writeValueAsString(original), ArticleSyncEventTypes.ArticleSyncEvent.class);

        assertEquals(original.eventId, parsed.eventId);
        assertEquals(original.articleId, parsed.articleId);
        assertEquals(original.op, parsed.op);
        assertEquals(original.versionMs, parsed.versionMs);
        assertEquals(original.authorId, parsed.authorId);
        assertEquals("# body", parsed.markdown);
        assertEquals(List.of("x", "y"), parsed.secondaryTags);
    }

    // ==================================================================
    // 3. event key（三元组唯一键）
    // ==================================================================

    @Test
    @DisplayName("Go: ArticleSyncEventKey 三段各自 TrimSpace，段间不额外处理")
    void eventKeyTrimsEachSegment() {
        assertEquals("a-1:upsert:e-1",
                ArticleSyncEventTypes.articleSyncEventKey(" a-1 ", " upsert ", " e-1 "));
        assertEquals("::", ArticleSyncEventTypes.articleSyncEventKey(null, null, null),
                "null 段应变成空串而不是 \"null\"（Go 的 TrimSpace(nil) 是空串）");
    }

    // ==================================================================
    // 4. community 信封
    // ==================================================================

    @Test
    @DisplayName("Go: published 事件的信封字段与三条强约束（wiki_module_id 恒 null 等）")
    void publishedEnvelopeHonoursHardConstraints() {
        ArticleEntity article = new ArticleEntity();
        article.setId("a-3");
        article.setAuthorId("1001");

        ArticleRevisionEntity revision = new ArticleRevisionEntity();
        revision.setArticleId("a-3");
        revision.setRevision(1L);
        revision.setRevisionId("a-3:r1");
        revision.setAuthorId("1001");
        revision.setSourceObject("a-3-r1.md");
        revision.setContentSha256("a".repeat(64));
        revision.setTitle("approved");
        revision.setMarkdown("# approved");

        ArticleSyncEventTypes.CommunityArticleEnvelope envelope = ArticleSyncEventTypes.articleDomainEvent(
                article, revision, "", ArticleSyncEventTypes.OP_PUBLISHED, "sync-1", 1L,
                "2026-10-22T00:00:00.000000000Z");

        assertEquals("community.article.sync-1.published", envelope.eventId);
        assertEquals("community.article.published", envelope.eventType);
        assertEquals("rtw.community.article", envelope.producer);
        assertEquals(1, envelope.schemaVersion, "信封 schema_version 是数字 1（不是字符串）");
        assertEquals("a-3", envelope.aggregateId);
        assertEquals(1L, envelope.aggregateVersion);
        assertEquals("sync-1", envelope.operationId);

        // 三条强约束
        assertNull(envelope.payload.wikiModuleId, "社区文章永不进知识库 ⇒ wiki_module_id 必须恒 null");
        assertTrue(envelope.payload.searchEvidence, "published 的 search_evidence 必须为 true");
        assertEquals("rtw.community.article/a-3:r1", envelope.payload.sourceRef);
        assertEquals("rtw.identity", envelope.payload.author.authorityId);
        assertEquals("platform", envelope.payload.author.tenantId);
        assertEquals("1001", envelope.payload.author.subjectId);
    }

    @Test
    @DisplayName("Go: retracted 事件不带 markdown 且 search_evidence=false")
    void retractedEnvelopeOmitsMarkdown() throws Exception {
        ArticleEntity article = new ArticleEntity();
        article.setId("a-4");
        article.setAuthorId("1001");

        ArticleSyncEventTypes.CommunityArticleEnvelope envelope = ArticleSyncEventTypes.articleDomainEvent(
                article, null, "a-4:r1", ArticleSyncEventTypes.OP_RETRACTED, "sync-2", 2L,
                "2026-10-22T00:00:00.000000000Z");

        assertFalse(envelope.payload.searchEvidence, "retracted 不是可检索证据");
        assertNull(envelope.payload.markdown, "retracted 事件不带正文");
        assertEquals("a-4:r1", envelope.payload.revisionId);

        String json = mapper.writeValueAsString(envelope);
        assertFalse(json.contains("\"markdown\""), "retracted 事件的 markdown 必须因 omitempty 消失：" + json);
    }

    @Test
    @DisplayName("Go: published 缺 revision / retracted 缺 previousRevision ⇒ 抛异常让事务回滚")
    void incompleteEventsAreRejected() {
        ArticleEntity article = new ArticleEntity();
        article.setId("a-5");
        article.setAuthorId("1001");

        // published 但 revision 为 null
        assertThrows(IllegalArgumentException.class, () ->
                ArticleSyncEventTypes.articleDomainEvent(article, null, "",
                        ArticleSyncEventTypes.OP_PUBLISHED, "sync-3", 1L, "now"));
        // retracted 但没有 previousRevision
        assertThrows(IllegalArgumentException.class, () ->
                ArticleSyncEventTypes.articleDomainEvent(article, null, "",
                        ArticleSyncEventTypes.OP_RETRACTED, "sync-3", 1L, "now"));
        // 作者 UID 不可解析
        article.setAuthorId("not-a-number");
        assertThrows(IllegalArgumentException.class, () ->
                ArticleSyncEventTypes.articleDomainEvent(article, null, "a-5:r1",
                        ArticleSyncEventTypes.OP_RETRACTED, "sync-3", 1L, "now"));
        article.setAuthorId("0");
        assertThrows(IllegalArgumentException.class, () ->
                ArticleSyncEventTypes.articleDomainEvent(article, null, "a-5:r1",
                        ArticleSyncEventTypes.OP_RETRACTED, "sync-3", 1L, "now"));
    }

    // ==================================================================
    // 5. SetSyncState 的两条条件赋值
    // ==================================================================

    @Test
    @DisplayName("Go: SetSyncState 里 versionMs<=0 保留旧版本号、syncReason=\"\" 保留旧原因")
    void setSyncStateKeepsPreviousValuesWhenArgumentsAreEmpty() {
        Map<String, String> ext = new java.util.LinkedHashMap<>();
        ext.put(ArticleSyncEventTypes.EXT_LAST_SYNC_VERSION, "1792670400000");
        ext.put(ArticleSyncEventTypes.EXT_PENDING_SYNC_REASON, "update");

        ArticleSyncEventTypes.setSyncState(ext, "queued", "pending_review", "", "", 0L, "");

        assertEquals("1792670400000", ext.get(ArticleSyncEventTypes.EXT_LAST_SYNC_VERSION),
                "versionMs<=0 时必须保留旧版本号；无条件赋值会清成 0 并破坏发布回调的版本比对");
        assertEquals("update", ext.get(ArticleSyncEventTypes.EXT_PENDING_SYNC_REASON),
                "syncReason 为空时必须保留旧待同步原因");
        assertEquals("queued", ext.get(ArticleSyncEventTypes.EXT_PUBLISH_STAGE));
        assertEquals("pending_review", ext.get(ArticleSyncEventTypes.EXT_RECO_SYNC_STATE));
    }

    @Test
    @DisplayName("版本号存成字符串（与 Go 的 strconv.FormatInt 一致，不是数字）")
    void versionIsStoredAsString() {
        Map<String, String> ext = new java.util.LinkedHashMap<>();
        ArticleSyncEventTypes.setSyncState(ext, "s", "st", "create", "e1", 1792670400000L, "");
        String stored = ext.get(ArticleSyncEventTypes.EXT_LAST_SYNC_VERSION);
        assertEquals("1792670400000", stored);
        assertInstanceOf(String.class, stored,
                "版本号必须是 String；若读成数字，发布回调的 "
                        + "extInfo.get(LAST_SYNC_VERSION) != String.valueOf(result.versionMs) 会恒真");
    }

    // ==================================================================
    // 6. 枚举名映射
    // ==================================================================

    @Test
    @DisplayName("ArticleStatus 枚举名与 proto 生成枚举一致（未知值回落为数字字符串）")
    void articleStatusNamesMatchGeneratedEnum() {
        assertEquals("ARTICLE_STATUS_UNSPECIFIED",
                ArticleSyncEventTypes.articleStatusName(0));
        assertEquals("DRAFT", ArticleSyncEventTypes.articleStatusName(1));
        assertEquals("PUBLISHED", ArticleSyncEventTypes.articleStatusName(2));
        assertEquals("REVIEWING", ArticleSyncEventTypes.articleStatusName(3));
        assertEquals("REJECTED", ArticleSyncEventTypes.articleStatusName(4));
        assertEquals("DELETED", ArticleSyncEventTypes.articleStatusName(5));
        // Go 的 .String() 对未登记数值返回数字本身
        assertEquals("7", ArticleSyncEventTypes.articleStatusName(7));
    }

    @Test
    @DisplayName("parsePositiveUid：解析失败或非正数返回 -1（由调用点决定处置）")
    void parsePositiveUidReturnsSentinel() {
        assertEquals(1001L, ArticleSyncEventTypes.parsePositiveUid("1001"));
        assertEquals(-1L, ArticleSyncEventTypes.parsePositiveUid("abc"));
        assertEquals(-1L, ArticleSyncEventTypes.parsePositiveUid(""));
        assertEquals(-1L, ArticleSyncEventTypes.parsePositiveUid(null));
        assertEquals(0L, ArticleSyncEventTypes.parsePositiveUid("0"));
    }
}