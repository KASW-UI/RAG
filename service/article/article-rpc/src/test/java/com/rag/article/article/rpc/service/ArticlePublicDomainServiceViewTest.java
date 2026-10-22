package com.rag.article.article.rpc.service;

import com.rag.article.article.proto.ListArticlesRequest;
import com.rag.article.article.proto.ListArticlesResponse;
import com.rag.article.article.rpc.client.ArticleObjectStorageClient;
import com.rag.article.article.rpc.domain.ArticleEntity;
import com.rag.article.article.rpc.domain.ArticleRevisionEntity;
import com.rag.article.article.rpc.exception.ArticleExceptions;
import com.rag.article.article.rpc.mapper.ArticlePublicMapper;
import com.rag.article.article.rpc.mapper.ArticleRevisionMapper;
import io.grpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 公开读路径的<b>字段来源</b>单元测试 —— 对应 Go {@code get_article_logic.go:170-189}
 * 的 {@code publishedArticleResponse}。
 *
 * <h2>为什么这组断言最关键</h2>
 * 「读者能看到什么」由一条规则决定：<b>有 revision 时字段全部取 revision，
 * 没有时取 article 行</b>。用错来源就会让读者看到<b>作者未提交的草稿</b> ——
 * 这是本组件最严重的一类潜在漂移，且<b>两侧测试都可能绿</b>：
 * 它只在"作者改过草稿、而读者还没看"的那段时间窗口里显现。
 * <br>对应的 Go 断言在 {@code article_public_test.go:168-172}。
 */
class ArticlePublicDomainServiceViewTest {

    private ArticlePublicMapper publicMapper;
    private ArticleRevisionMapper revisionMapper;
    private ArticleObjectStorageClient storage;
    private ArticlePublicDomainService service;

    @BeforeEach
    void setUp() {
        publicMapper = mock(ArticlePublicMapper.class);
        revisionMapper = mock(ArticleRevisionMapper.class);
        storage = mock(ArticleObjectStorageClient.class);
        service = new ArticlePublicDomainService(publicMapper, revisionMapper, storage);
    }

    // ==================================================================
    // 1. 有 revision：字段必须全部取 revision
    // ==================================================================

    @Test
    @DisplayName("🔴 Go: 有 revision 时 title/brief/tag/author 全部取已批准版本，不泄漏草稿")
    void revisionFieldsWinOverDraftFields() {
        ArticleEntity draft = article("a-1", "draft title", "draft brief", "draft-tag",
                "2002", 3, 5, 6, 7);
        ArticleRevisionEntity revision = revision("a-1:r1", "approved title", "approved brief",
                "approved-tag", "1001", 1234567890123L);

        when(publicMapper.selectPublicById("a-1")).thenReturn(draft);
        when(publicMapper.selectPublication("a-1")).thenReturn(publishedPointer("a-1:r1"));
        when(revisionMapper.selectByRevisionId("a-1", "a-1:r1")).thenReturn(revision);
        when(publicMapper.incrPublicViewCount("a-1")).thenReturn(1);

        var view = service.getPublicArticle("a-1", false);

        assertEquals("approved title", view.title, "读者必须看到已批准标题，而非作者草稿标题");
        assertEquals("approved brief", view.brief);
        assertEquals("approved-tag", view.manualTypeTag);
        assertEquals("1001", view.authorId, "author_id 也取 revision（Go get_article_logic.go:185）");
        assertEquals("a-1:r1", view.extInfo.get("published_revision_id"));
        // ⚠️ update_time 取**发布时间**，不是源行 updated_at
        assertEquals(1234567890123L, view.updateTimeMs, "update_time 必须是 published_at（发布时间）");
        // create_time 仍取源行
        assertEquals(draft.getCreatedAt().toInstant().toEpochMilli(), view.createTimeMs);
        // 互动计数取源行
        assertEquals(5, view.viewCount);
        assertEquals(6, view.likeCount);
        assertEquals(7, view.commentCount);
    }

    @Test
    @DisplayName("🔴 Go: 有 revision 时**完全不读对象存储**（MinIO 挂掉也必须能返回）")
    void approvedRevisionDoesNotDependOnObjectStorage() throws Exception {
        when(publicMapper.selectPublicById("a-1")).thenReturn(article("a-1", "t", "b", "tag", "1001", 3, 1, 0, 0));
        when(publicMapper.selectPublication("a-1")).thenReturn(publishedPointer("a-1:r1"));
        when(revisionMapper.selectByRevisionId("a-1", "a-1:r1")).thenReturn(
                revision("a-1:r1", "t", "b", "tag", "1001", 1000L));

        var view = service.getPublicArticle("a-1", false);

        assertEquals("# Approved r1", view.markdown, "正文取冻结的 revision.markdown");
        // ⚠️ 关键断言：一次对象读取都不能发生
        //    （Go 测试 article_public_test.go:173-178 把 MinioClient 置 nil 后仍要求成功）
        verify(storage, never()).readString(anyString());
        verify(storage, never()).get(anyString());
    }

    @Test
    @DisplayName("Go: 读者看到的 status 恒为 PUBLISHED（哪怕源行正在 REVIEWING）")
    void publicStatusIsAlwaysPublished() {
        when(publicMapper.selectPublicById("a-1")).thenReturn(article("a-1", "t", "b", "tag", "1001", 3, 0, 0, 0));
        when(publicMapper.selectPublication("a-1")).thenReturn(publishedPointer("a-1:r1"));
        when(revisionMapper.selectByRevisionId("a-1", "a-1:r1")).thenReturn(
                revision("a-1:r1", "t", "b", "tag", "1001", 1000L));

        var view = service.getPublicArticle("a-1", false);
        assertEquals(com.rag.article.article.proto.ArticleStatus.PUBLISHED_VALUE, view.status);
    }

    // ==================================================================
    // 2. legacy 路径：无 revision 指针
    // ==================================================================

    @Test
    @DisplayName("Go: legacy PUBLISHED 且无指针 ⇒ 回落读原对象，并打 publication_gap 标记")
    void legacyRowFallsBackToOriginalObject() throws Exception {
        when(publicMapper.selectPublicById("legacy")).thenReturn(
                article("legacy", "Legacy title", "b", "tag", "1001", 2, 0, 0, 0));
        when(publicMapper.selectPublication("legacy")).thenReturn(null);   // 无指针行
        when(storage.readString("legacy.md")).thenReturn("# Legacy");

        var view = service.getPublicArticle("legacy", false);

        assertEquals("# Legacy", view.markdown);
        assertEquals("legacy_revision_missing", view.extInfo.get("publication_gap"));
        // ⚠️ legacy 不伪造 revision_id
        assertFalse(view.extInfo.containsKey("published_revision_id"));
        verify(storage).readString("legacy.md");
    }

    @Test
    @DisplayName("Go: 无指针且不是 PUBLISHED ⇒ NotFound（article not published）")
    void noPointerAndNotPublishedIsNotFound() {
        when(publicMapper.selectPublicById("draft")).thenReturn(
                article("draft", "t", "b", "tag", "1001", 1, 0, 0, 0));
        when(publicMapper.selectPublication("draft")).thenReturn(null);

        ArticleExceptions.Rpc e = assertThrows(ArticleExceptions.Rpc.class,
                () -> service.getPublicArticle("draft", false));
        assertEquals(Status.Code.NOT_FOUND, e.getCode());
        assertEquals("article not published", e.getMessage());
    }

    @Test
    @DisplayName("Go: 指针 state=retracted ⇒ NotFound（撤回后不可见，即便指针还指向某 revision）")
    void retractedPointerIsNotVisible() {
        when(publicMapper.selectPublicById("a-1")).thenReturn(
                article("a-1", "t", "b", "tag", "1001", 2, 0, 0, 0));
        var pointer = new ArticlePublicMapper.ArticlePublicationRow();
        pointer.setArticleId("a-1");
        pointer.setCurrentRevision("a-1:r1");
        pointer.setState("retracted");
        when(publicMapper.selectPublication("a-1")).thenReturn(pointer);

        ArticleExceptions.Rpc e = assertThrows(ArticleExceptions.Rpc.class,
                () -> service.getPublicArticle("a-1", false));
        assertEquals(Status.Code.NOT_FOUND, e.getCode());
    }

    // ==================================================================
    // 3. 浏览量：公开路径的不对称（致命 vs best-effort）
    // ==================================================================

    @Test
    @DisplayName("🔴 Go: 公开路径浏览量自增失败 ⇒ 致命 Internal（与作者路径的 best-effort 相反）")
    void publicViewIncrementFailureIsFatal() {
        when(publicMapper.selectPublicById("a-1")).thenReturn(
                article("a-1", "t", "b", "tag", "1001", 2, 0, 0, 0));
        when(publicMapper.selectPublication("a-1")).thenReturn(null);
        try {
            when(storage.readString(anyString())).thenReturn("# legacy");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        // RowsAffected == 0 ⇒ 撤回赢了竞态 ⇒ NotFound（Go get_article_logic.go:162-164）
        when(publicMapper.incrPublicViewCount("a-1")).thenReturn(0);
        ArticleExceptions.Rpc e = assertThrows(ArticleExceptions.Rpc.class,
                () -> service.getPublicArticle("a-1", true));
        assertEquals(Status.Code.NOT_FOUND, e.getCode());
        assertEquals("article no longer published", e.getMessage(),
                "竞态丢失的文案与「本来没发布」不同，这是不同的运维信号");
    }

    @Test
    @DisplayName("Go: 公开路径自增成功后响应里的 viewCount 是内存 +1")
    void publicViewCountIsIncrementedInMemory() {
        when(publicMapper.selectPublicById("a-1")).thenReturn(
                article("a-1", "t", "b", "tag", "1001", 2, 4, 0, 0));
        when(publicMapper.selectPublication("a-1")).thenReturn(publishedPointer("a-1:r1"));
        when(revisionMapper.selectByRevisionId("a-1", "a-1:r1")).thenReturn(
                revision("a-1:r1", "t", "b", "tag", "1001", 1000L));
        when(publicMapper.incrPublicViewCount("a-1")).thenReturn(1);

        var view = service.getPublicArticle("a-1", true);
        assertEquals(5, view.viewCount);
    }

    // ==================================================================
    // 4. 列表：page/size 归一 + 永不返回正文
    // ==================================================================

    @Test
    @DisplayName("Go: 公开列表把 page<1⇒1、size<1⇒20、size>100⇒100 后回显归一值")
    void publicListNormalisesPagination() {
        when(publicMapper.countPublic(any(), any(), any())).thenReturn(0L);
        when(publicMapper.selectPublicPage(any(), any(), any(), any(), anyBoolean(), anyInt(), anyLong()))
                .thenReturn(List.of());

        ListArticlesResponse small = service.listPublicArticles(
                ListArticlesRequest.newBuilder().setPage(0).setPageSize(0).build());
        assertEquals(1, small.getPage());
        assertEquals(20, small.getPageSize());

        ListArticlesResponse capped = service.listPublicArticles(
                ListArticlesRequest.newBuilder().setPage(2).setPageSize(500).build());
        assertEquals(100, capped.getPageSize());
        // page=2, size=100 ⇒ offset=100
        verify(publicMapper).selectPublicPage(any(), any(), any(), any(), anyBoolean(), eq(100), eq(100L));
    }

    @Test
    @DisplayName("🔴 Go: 公开列表每条的 markdown 恒为空（不返回正文，也不返回对象 key）")
    void publicListNeverReturnsBody() {
        ArticleEntity article = article("a-1", "title", "brief", "tag", "1001", 3, 0, 0, 0);
        article.setContent("articles/content/a-1.md");   // 对象 key 也不该出现在响应里
        when(publicMapper.countPublic(any(), any(), any())).thenReturn(1L);
        when(publicMapper.selectPublicPage(any(), any(), any(), any(), anyBoolean(), anyInt(), anyLong()))
                .thenReturn(List.of(article));
        when(publicMapper.selectRevisionsForPublished(any())).thenReturn(List.of(
                revision("a-1:r1", "approved", "b", "tag", "1001", 1000L)));

        ListArticlesResponse response = service.listPublicArticles(
                ListArticlesRequest.newBuilder().setPublicOnly(true).setPage(1).setPageSize(20).build());

        assertEquals(1, response.getArticlesCount());
        assertEquals("", response.getArticles(0).getMarkdownContent(),
                "公开列表不得返回正文");
        assertEquals("approved", response.getArticles(0).getTitle(), "标题取 revision");
    }

    @Test
    @DisplayName("Go: 公开列表的二级标签过滤按 JSON 数组字面量绑定（不是字符串拼接）")
    void secondaryTagIsBoundAsJsonArray() {
        when(publicMapper.countPublic(any(), any(), any())).thenReturn(0L);
        when(publicMapper.selectPublicPage(any(), any(), any(), any(), anyBoolean(), anyInt(), anyLong()))
                .thenReturn(List.of());

        service.listPublicArticles(ListArticlesRequest.newBuilder()
                .setPublicOnly(true).setSecondaryTag("approved-secondary").build());

        verify(publicMapper).countPublic(isNull(), eq("[\"approved-secondary\"]"), isNull());
    }

    // ==================================================================
    // 辅助
    // ==================================================================

    private static ArticleEntity article(String id, String title, String brief, String tag,
                                          String authorId, int status,
                                          int viewCount, int likeCount, int commentCount) {
        ArticleEntity a = new ArticleEntity();
        a.setId(id);
        a.setTitle(title);
        a.setBrief(brief);
        a.setManualTypeTag(tag);
        a.setAuthorId(authorId);
        a.setStatus(status);
        a.setViewCount(viewCount);
        a.setLikeCount(likeCount);
        a.setCommentCount(commentCount);
        a.setShareCount(0);
        a.setContent(id + ".md");
        a.setCreatedAt(OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        a.setUpdatedAt(OffsetDateTime.of(2026, 1, 2, 0, 0, 0, 0, ZoneOffset.UTC));
        return a;
    }

    private static ArticleRevisionEntity revision(String revisionId, String title, String brief,
                                                  String tag, String authorId, long publishedAtMs) {
        ArticleRevisionEntity r = new ArticleRevisionEntity();
        r.setArticleId(revisionId.substring(0, revisionId.indexOf(":")));
        r.setRevisionId(revisionId);
        r.setRevision(1L);
        r.setTitle(title);
        r.setBrief(brief);
        r.setManualTypeTag(tag);
        r.setAuthorId(authorId);
        r.setMarkdown("# Approved r1");
        r.setPublishedAt(OffsetDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(publishedAtMs), ZoneOffset.UTC));
        return r;
    }

    private static ArticlePublicMapper.ArticlePublicationRow publishedPointer(String currentRevision) {
        var row = new ArticlePublicMapper.ArticlePublicationRow();
        row.setArticleId(currentRevision.substring(0, currentRevision.indexOf(":")));
        row.setCurrentRevision(currentRevision);
        row.setState("published");
        row.setPointerVersion(1L);
        return row;
    }
}