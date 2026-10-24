package com.rag.hot.hot.api.service;

import com.rag.article.article.proto.Article;
import com.rag.article.article.proto.ArticleStatus;
import com.rag.article.article.proto.GetArticleResponse;
import com.rag.common.spring.LoggerFactoryBean;
import com.rag.hot.hot.api.client.ArticleRpcClient;
import com.rag.hot.hot.api.config.HotHydrationExecutorConfiguration;
import com.rag.hot.hot.api.dto.HotArticleCard;
import com.rag.hot.hot.api.metrics.HotApiMetrics;
import com.rag.hot.hot.proto.HotArticleItem;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link HotArticleHydrator} 的并发窗口 / 4 条 skip 分支 / 保序 / best-effort 测试。
 *
 * <p><b>对照基准</b>：Go {@code getHotArticlesLogic.go:91-170} +
 * {@code getHotArticlesLogic_test.go:97-148}（过滤保序、跨页 rank）与
 * {@code :150-183}（article-rpc 失败不拖垮整包）。
 */
class HotArticleHydratorTest {

    private ArticleRpcClient articleRpcClient;
    private MeterRegistry registry;
    private HotApiMetrics metrics;
    private Executor executor;
    private HotArticleHydrator hydrator;

    @BeforeEach
    void setUp() {
        articleRpcClient = mock(ArticleRpcClient.class);
        registry = new SimpleMeterRegistry();
        metrics = new HotApiMetrics(registry);
        executor = new HotHydrationExecutorConfiguration().hotHydrationExecutor();
        hydrator = new HotArticleHydrator(articleRpcClient, metrics,
                mock(LoggerFactoryBean.class), executor);
    }

    @Test
    @DisplayName("Go :127-130 —— nil item / 空 article_id 走 empty_article_id，且不打 RPC")
    void skipsNilAndEmptyArticleId() {
        when(articleRpcClient.getPublicArticle(any(), anyLong()))
                .thenReturn(published("x"));

        List<HotArticleCard> raw = hydrator.hydrate(
                java.util.Arrays.asList(null, hotItem(""), hotItem("ok")), -1L);

        // Go 的 hydrate 用 nil 占位，过滤与重排名发生在紧随其后的 assignRanks（:114-123）
        assertEquals(3, raw.size(), "hydrate 与输入等长，nil 即 skip");
        assertNull(raw.get(0));
        assertNull(raw.get(1));
        List<HotArticleCard> out = HotRankPaginator.assignRanks(raw);
        assertEquals(1, out.size());
        // fixture 对任何 article_id 都返回固定文章 "x"（见 setUp 的 when(...).thenReturn）
        assertEquals("x", out.get(0).getArticleId());
        assertEquals(2.0, skipCount(HotApiMetrics.SKIP_EMPTY_ARTICLE_ID),
                "nil item 与空 article_id 两条都落在同一个 reason 上");
        assertEquals(0.0, skipCount(HotApiMetrics.SKIP_ARTICLE_RPC_ERROR),
                "这两条根本没发出 RPC，不应产生 article_rpc_error");
    }

    @Test
    @DisplayName("Go :137-142 —— article-rpc 报错只 skip 该条，不冒泡（best-effort）")
    void skipsArticleRpcFailureWithoutFailingRequest() {
        when(articleRpcClient.getPublicArticle(eq("bad"), anyLong()))
                .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));
        when(articleRpcClient.getPublicArticle(eq("good"), anyLong()))
                .thenReturn(published("good"));

        List<HotArticleCard> out = HotRankPaginator.assignRanks(hydrator.hydrate(
                java.util.Arrays.asList(hotItem("bad"), hotItem("good")), -1L));

        assertEquals(1, out.size(), "整包不能因单条 RPC 失败而失败");
        assertEquals("good", out.get(0).getArticleId());
        assertEquals(1L, skipCount(HotApiMetrics.SKIP_ARTICLE_RPC_ERROR));
        assertEquals(1L, rpcErrorCount(HotApiMetrics.CALLEE_ARTICLE_RPC));
    }

    @Test
    @DisplayName("Go :143-147 —— resp 无 article 走 article_missing")
    void skipsMissingArticle() {
        when(articleRpcClient.getPublicArticle(any(), anyLong()))
                .thenReturn(GetArticleResponse.getDefaultInstance());

        List<HotArticleCard> out = HotRankPaginator.assignRanks(hydrator.hydrate(
                Collections.singletonList(hotItem("ghost")), -1L));

        assertEquals(0, out.size());
        assertEquals(1L, skipCount(HotApiMetrics.SKIP_ARTICLE_MISSING));
    }

    @Test
    @DisplayName("Go :150-154 —— 非 PUBLISHED 走 article_not_published")
    void skipsNotPublishedArticle() {
        when(articleRpcClient.getPublicArticle(any(), anyLong()))
                .thenReturn(responseWithStatus("draft", ArticleStatus.DRAFT));

        List<HotArticleCard> out = HotRankPaginator.assignRanks(hydrator.hydrate(
                Collections.singletonList(hotItem("draft")), -1L));

        assertEquals(0, out.size());
        assertEquals(1L, skipCount(HotApiMetrics.SKIP_ARTICLE_NOT_PUBLISHED));
    }

    @Test
    @DisplayName("Go _test.go:47-60 —— 源行在改稿、公开投影仍是 PUBLISHED 时不得误过滤")
    void keepsApprovedRevisionWhileSourceIsReviewing() {
        // article-rpc 的 public_only 投影返回 status=PUBLISHED（源行可能是 REVIEWING）
        when(articleRpcClient.getPublicArticle(eq("editing"), anyLong()))
                .thenReturn(publishedWithTitle("editing", "Approved r1"));

        List<HotArticleCard> out = HotRankPaginator.assignRanks(hydrator.hydrate(
                Collections.singletonList(hotItem("editing")), -1L));

        assertEquals(1, out.size());
        assertEquals("Approved r1", out.get(0).getTitle());
    }

    @Test
    @DisplayName("Go :108/:114-123 —— 结果按下标保序，与完成顺序无关")
    void preservesInputOrderRegardlessOfCompletionOrder() {
        AtomicInteger seq = new AtomicInteger();
        when(articleRpcClient.getPublicArticle(any(), anyLong())).thenAnswer(inv -> {
            int n = seq.incrementAndGet();
            // 越靠后的元素越先返回：若实现改成「先完成先 add」，顺序断言会失败
            Thread.sleep(20 - (n % 20));
            return published(inv.getArgument(0));
        });

        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            ids.add("a" + i);
        }
        List<HotArticleCard> out = hydrator.hydrate(items(ids), -1L);

        assertEquals(30, out.size());
        for (int i = 0; i < 30; i++) {
            assertEquals("a" + i, out.get(i).getArticleId(), "第 " + i + " 条顺序错位");
        }
    }

    @Test
    @DisplayName("Go :93 的 sem 容量 8 —— 并发补全的在途数不得超过 8")
    void neverExceedsEightConcurrentHydrations() {
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        when(articleRpcClient.getPublicArticle(any(), anyLong())).thenAnswer(inv -> {
            int now = inFlight.incrementAndGet();
            peak.accumulateAndGet(now, Math::max);
            try {
                Thread.sleep(5);
                return published(inv.getArgument(0));
            } finally {
                inFlight.decrementAndGet();
            }
        });

        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            ids.add("a" + i);
        }
        List<HotArticleCard> out = hydrator.hydrate(items(ids), -1L);

        assertEquals(40, out.size());
        assertEquals(HotArticleHydrator.HYDRATION_CONCURRENCY, peak.get(),
                "并发峰值必须等于窗口 8：更小=退化成串行（延迟与下游负载都变），更大=突破了 Go 的信号量");
    }

    /**
     * ⚠️ **DRIFT-HOTAPI-028 的守门用例**（2026-10-24 第十轮 U10 活体对拍后新增）。
     *
     * <p><b>它守的是什么</b>：<b>跨请求</b>的合计在途数<b>不能被全局池压到单请求窗口以下</b>。
     *
     * <p><b>Go 侧语义</b>（{@code getHotArticlesLogic.go:93}）：
     * {@code sem := make(chan struct{}, hydrationConcurrency)} 是<b>每个请求各建一个</b>
     * ⇒ N 个并发请求 ⇒ <b>N × 8</b> 个同时在途的下游调用，<b>无全局封顶</b>。
     *
     * <p><b>为什么「每请求窗口 8」的旧测试抓不到这个</b>：
     * {@link #neverExceedsEightConcurrentHydrations()} 只跑<b>一个</b>请求
     * ⇒ 单请求的窗口是 8 ⇒ <b>无论池是 32 还是 64 都通过</b>
     * ⇒ 必须<b>多个请求同时跑</b>，池才会成为瓶颈。
     *
     * <p><b>旧值 32 时会怎样</b>：8 个请求 × 8 = 64 个任务 ⇒ 池只有 32 个线程
     * ⇒ 同时执行的最多 32 ⇒ 峰值 = 32 ⇒ <b>本断言变红</b>。
     * <b>新值 64 时</b>：64 个任务全部并行 ⇒ 峰值 = 64 ✅。
     *
     * <p>⚠️ 阈值本身是<b>部署决策</b>（已按 {@code SD-HOT-API-010} 签字：
     * RAG 保留跨请求总上限，比 Go 更保守）⇒ 本测试钉的是
     * 「<b>并发 ≤ 8 时必须与 Go 等价</b>」这条**已签字的等价区间**，
     * <b>不是</b>「池必须等于 64」这个具体数字
     * ⇒ 将来若为别的并发档位调池，本测试需同步调整期望值。
     */
    @Test
    @DisplayName("⚠️ 跨请求的合计在途不被全局池压扁：8 个并发请求 ⇒ 峰值 = 8×8 = 64")
    void concurrentRequestsEachKeepTheirOwnWindow() throws Exception {
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        when(articleRpcClient.getPublicArticle(any(), anyLong())).thenAnswer(inv -> {
            int now = inFlight.incrementAndGet();
            peak.accumulateAndGet(now, Math::max);
            try {
                // ⚠️ 必须让任务**真的重叠**：sleep 期间才计入在途
                Thread.sleep(30);
                return published(inv.getArgument(0));
            } finally {
                inFlight.decrementAndGet();
            }
        });

        final int requests = 8;
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < HotArticleHydrator.HYDRATION_CONCURRENCY; i++) {
            ids.add("a" + i);
        }

        ExecutorService callers = Executors.newFixedThreadPool(requests);
        try {
            // ⚠️ **起跑门**：8 个请求必须**同时**开始提交，否则各请求的 8 个窗口
            //    不会完全重叠 ⇒ 峰值会随机落在 56 之类的值上（实测过）⇒ 断言不稳。
            CountDownLatch startGate = new CountDownLatch(1);
            List<Future<List<HotArticleCard>>> futures = new ArrayList<>();
            for (int r = 0; r < requests; r++) {
                futures.add(callers.submit(() -> {
                    startGate.await();
                    return hydrator.hydrate(items(ids), -1L);
                }));
            }
            startGate.countDown();
            for (Future<List<HotArticleCard>> f : futures) {
                assertNotNull(f.get(60, TimeUnit.SECONDS));
            }
        } finally {
            callers.shutdownNow();
        }

        assertEquals(requests * HotArticleHydrator.HYDRATION_CONCURRENCY, peak.get(),
                "8 个并发请求的合计在途必须是 8×8=64：更小说明全局池把**每个请求自己的**窗口"
                        + "压小了（那不是限流，是偷偷改了单请求语义 —— DRIFT-HOTAPI-028）");
    }

    @Test
    @DisplayName("成功条目的 13 个字段取值来源正确：hot_score 取 hot-rpc，其余取 article 公开投影")
    void mapsFieldsFromTheCorrectUpstream() {
        when(articleRpcClient.getPublicArticle(any(), anyLong())).thenReturn(
                GetArticleResponse.newBuilder()
                        .setArticle(Article.newBuilder()
                                .setId("article-side-id")
                                .setTitle("T")
                                .setBrief("B")
                                .setCoverImageUrl("http://cover")
                                .setAuthorId("author-1")
                                .setCreateTime(1700000000123L)
                                .setViewCount(11)
                                .setLikeCount(22)
                                .setCommentCount(33)
                                .setManualTypeTag("评测")
                                .addSecondaryTags("A")
                                .setStatus(ArticleStatus.PUBLISHED)
                                .build())
                        .build());

        List<HotArticleCard> out = HotRankPaginator.assignRanks(hydrator.hydrate(
                Collections.singletonList(
                        HotArticleItem.newBuilder().setArticleId("hot-side-id").setHotScore(4242).build()),
                -1L));

        HotArticleCard card = out.get(0);
        assertNotNull(card);
        assertEquals(4242L, card.getHotScore(), "hot_score 来自 hot-rpc");
        assertEquals("article-side-id", card.getArticleId(),
                "Go :157 取的是 article.GetId()，不是 hot-rpc 的 article_id");
        assertEquals("T", card.getTitle());
        assertEquals("B", card.getBrief());
        assertEquals("http://cover", card.getCoverImageUrl());
        assertEquals("author-1", card.getAuthorId());
        assertEquals(1700000000123L, card.getCreateTime());
        assertEquals(11, card.getViewCount());
        assertEquals(22, card.getLikeCount());
        assertEquals(33, card.getCommentCount());
        assertEquals("评测", card.getManualTypeTag());
        assertEquals(List.of("A"), card.getSecondaryTags());
    }

    @Test
    @DisplayName("⚠️ secondary_tags 为空时是 null 而不是 []（Go 侧是 nil slice）")
    void emptySecondaryTagsBecomesNullNotEmptyList() {
        when(articleRpcClient.getPublicArticle(any(), anyLong())).thenReturn(published("x"));

        List<HotArticleCard> out = HotRankPaginator.assignRanks(hydrator.hydrate(
                Collections.singletonList(hotItem("x")), -1L));

        assertNull(out.get(0).getSecondaryTags(),
                "Go 的 proto3 repeated 字段在 wire 上无元素时是 nil ⇒ JSON 输出 null");
    }

    @Test
    @DisplayName("剩余预算被原样传到底层（Go 侧 ctx 被所有补全 goroutine 共享）")
    void forwardsRequestBudgetToEveryHydration() {
        when(articleRpcClient.getPublicArticle(any(), anyLong())).thenReturn(published("x"));

        hydrator.hydrate(java.util.Arrays.asList(hotItem("a"), hotItem("b"), hotItem("c")), 1500L);

        org.mockito.Mockito.verify(articleRpcClient, org.mockito.Mockito.times(3))
                .getPublicArticle(any(), eq(1500L));
    }

    @Test
    @DisplayName("空输入返回空列表")
    void emptyInputYieldsEmptyList() {
        assertEquals(0, hydrator.hydrate(Collections.emptyList(), -1L).size());
        assertEquals(0, hydrator.hydrate(null, -1L).size());
    }

    @Test
    @DisplayName("日志失败不得把 skip 变成 500（Go 的 logger 不会让请求失败）")
    void loggingFailureDoesNotBreakHydration() {
        LoggerFactoryBean failing = mock(LoggerFactoryBean.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("logger not initialized"))
                .when(failing).logInfo(any(), any(), any(com.rag.common.logger.LogOption.class));
        HotArticleHydrator strict = new HotArticleHydrator(articleRpcClient, metrics, failing, executor);
        when(articleRpcClient.getPublicArticle(any(), anyLong()))
                .thenReturn(GetArticleResponse.getDefaultInstance());

        List<HotArticleCard> out = HotRankPaginator.assignRanks(
                strict.hydrate(Collections.singletonList(hotItem("ghost")), -1L));

        assertEquals(0, out.size());
        assertEquals(1L, skipCount(HotApiMetrics.SKIP_ARTICLE_MISSING));
    }

    // ---------------------------------------------------------------- helpers

    private double skipCount(String reason) {
        return registry.counter("hot_api_article_skip_total", "reason", reason).count();
    }

    private double rpcErrorCount(String callee) {
        return registry.counter("hot_api_rpc_error_total", "callee", callee).count();
    }

    private static HotArticleItem hotItem(String articleId) {
        return HotArticleItem.newBuilder().setArticleId(articleId).setHotScore(1).build();
    }

    private static List<HotArticleItem> items(List<String> ids) {
        List<HotArticleItem> list = new ArrayList<>(ids.size());
        ids.forEach(id -> list.add(hotItem(id)));
        return list;
    }

    private static GetArticleResponse published(String articleId) {
        return publishedWithTitle(articleId, articleId);
    }

    private static GetArticleResponse publishedWithTitle(String articleId, String title) {
        return GetArticleResponse.newBuilder()
                .setArticle(Article.newBuilder()
                        .setId(articleId)
                        .setTitle(title)
                        .setStatus(ArticleStatus.PUBLISHED)
                        .build())
                .build();
    }

    private static GetArticleResponse responseWithStatus(String articleId, ArticleStatus status) {
        return GetArticleResponse.newBuilder()
                .setArticle(Article.newBuilder().setId(articleId).setStatus(status).build())
                .build();
    }
}