package com.rag.hot.hot.api.service;

import com.rag.article.article.proto.Article;
import com.rag.article.article.proto.ArticleStatus;
import com.rag.article.article.proto.GetArticleResponse;
import com.rag.common.exception.BusinessException;
import com.rag.common.spring.LoggerFactoryBean;
import com.rag.hot.hot.api.client.ArticleRpcClient;
import com.rag.hot.hot.api.client.HotRpcClient;
import com.rag.hot.hot.api.config.HotHydrationExecutorConfiguration;
import com.rag.hot.hot.api.dto.HotArticlesReq;
import com.rag.hot.hot.api.dto.HotArticlesResp;
import com.rag.hot.hot.api.metrics.HotApiMetrics;
import com.rag.hot.hot.common.errmsg.HotErrorCode;
import com.rag.hot.hot.proto.GetHotArticlesResponse;
import com.rag.hot.hot.proto.HotArticleItem;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link HotArticleQueryService} 的编排测试 —— 重点是<b>致命分支</b>与
 * <b>best-effort 的不对称</b>（Go {@code getHotArticlesLogic.go:54-89}）。
 *
 * <p>Hydrator 用<b>真实实例</b>（只 mock 掉 article-rpc 边界），
 * 这样「hot-rpc 失败 ⇒ 整包 5001」与「article-rpc 失败 ⇒ 少一条」能在同一套夹具里对照。
 */
class HotArticleQueryServiceTest {

    private HotRpcClient hotRpcClient;
    private ArticleRpcClient articleRpcClient;
    private final Map<String, GetArticleResponse> articleOverrides = new HashMap<>();
    private final Set<String> failingArticleIds = new HashSet<>();

    private MeterRegistry registry;
    private HotApiMetrics metrics;
    private HotArticleQueryService service;

    @BeforeEach
    void setUp() {
        hotRpcClient = mock(HotRpcClient.class);
        articleRpcClient = mock(ArticleRpcClient.class);
        articleOverrides.clear();
        failingArticleIds.clear();
        // ⚠️ 只注册**一次** stubbing：Mockito 在 `when(...)` 求值时会真的调用一次 mock，
        //    若已有 thenAnswer 在册，matcher 的占位值（eq("a") → null）会被喂进那个 answer。
        //    叠加第二条 when() 的写法在这里会直接 NPE。
        when(articleRpcClient.getPublicArticle(any(), anyLong())).thenAnswer(inv -> {
            String id = inv.getArgument(0);
            if (failingArticleIds.contains(id)) {
                throw new StatusRuntimeException(Status.UNAVAILABLE);
            }
            GetArticleResponse override = articleOverrides.get(id);
            return override != null ? override : published(id, id);
        });

        registry = new SimpleMeterRegistry();
        metrics = new HotApiMetrics(registry);
        HotArticleHydrator hydrator = new HotArticleHydrator(
                articleRpcClient,
                metrics,
                mock(LoggerFactoryBean.class),
                new HotHydrationExecutorConfiguration().hotHydrationExecutor());
        service = new HotArticleQueryService(hotRpcClient, hydrator, metrics, mock(LoggerFactoryBean.class));
    }

    @Test
    @DisplayName("Go :62-69 —— hot-rpc 失败是 FATAL：整包 5001，不是空列表")
    void hotRpcFailureIsFatal() {
        when(hotRpcClient.getHotArticles(anyInt(), anyLong()))
                .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.getHotArticles(new HotArticlesReq(1, 20)));

        assertEquals(HotErrorCode.ERROR_SERVER_COMMON.getCode(), e.getCode());
        // ⚠️ 2026-10-24 订正：本行原断言 "internal server error" == e.getMessage()，
        //   锁的是**旧的不一致行为**（RAG 侧 getMessage() 是纯文案，而 Go 侧是 Error() 整串）。
        //   Go 的 NewErrCode 返回 *CodeError，其 Error() 逐字节是
        //   "ErrCode:5001, Errmsg:internal server error"（errmsg.go:15-17）⇒ 断言改成对齐后的形态。
        //   ⚠️ 注意：**信封**的 msg 仍是纯文案（走 HotCodeError.getBizMsg()），
        //   见 HotCodeErrorLogParityTest#envelopeKeepsPlainBusinessMessage。
        assertEquals("ErrCode:5001, Errmsg:internal server error", e.getMessage(),
                "异常对象的 getMessage() 必须与 Go 的 (*CodeError).Error() 逐字节一致"
                        + "（Go errmsg.go:15-17）");
        assertEquals(1.0, registry.counter("hot_api_rpc_error_total",
                "callee", HotApiMetrics.CALLEE_HOT_RPC).count());
        assertEquals(1.0, registry.counter("hot_api_request_total",
                "route", HotApiMetrics.ROUTE, "result", HotApiMetrics.RESULT_FAIL).count());
    }

    @Test
    @DisplayName("Go :70-75 —— hotResp==nil 也走 5001，并打在 article skip 指标上（Go 的既有归类）")
    void nilHotResponseIsFatalAndCountedAsArticleSkip() {
        when(hotRpcClient.getHotArticles(anyInt(), anyLong())).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.getHotArticles(new HotArticlesReq(1, 20)));

        assertEquals(HotErrorCode.ERROR_SERVER_COMMON.getCode(), e.getCode());
        assertEquals(1.0, registry.counter("hot_api_article_skip_total",
                "reason", HotApiMetrics.SKIP_HOT_RPC_NIL_RESPONSE).count());
    }

    @Test
    @DisplayName("Go :63 —— TopK 恒为 100，请求里的分页参数影响不到它")
    void alwaysRequestsTopK100() {
        when(hotRpcClient.getHotArticles(anyInt(), anyLong()))
                .thenReturn(GetHotArticlesResponse.getDefaultInstance());

        service.getHotArticles(new HotArticlesReq(3, 7));

        verify(hotRpcClient).getHotArticles(100, -1L);
        assertEquals(100, HotRankPaginator.MAX_HOT_ARTICLES);
    }

    @Test
    @DisplayName("Go _test.go:62-94 —— 空结果时仍返回默认值 page=1 / page_size=20 / scope=rolling")
    void appliesDefaultsAndScopeForEmptyWindow() {
        when(hotRpcClient.getHotArticles(anyInt(), anyLong()))
                .thenReturn(GetHotArticlesResponse.getDefaultInstance());

        HotArticlesResp resp = service.getHotArticles(new HotArticlesReq(0, 0));
        assertEquals(1, resp.getPage());
        assertEquals(20, resp.getPageSize());
        assertEquals("rolling", resp.getScope());
        assertEquals(0, resp.getTotal());
        assertNotNull(resp.getItems(), "空页的 items 必须是 [] 而不是 null");
        assertEquals(0, resp.getItems().size());

        HotArticlesResp capped = service.getHotArticles(new HotArticlesReq(2, 999));
        assertEquals(2, capped.getPage());
        assertEquals(50, capped.getPageSize());
    }

    @Test
    @DisplayName("Go _test.go:97-148 —— 过滤后 total、按下标保序、跨页稠密 rank")
    void filtersKeepsOrderAndPaginatesAfterFiltering() {
        when(hotRpcClient.getHotArticles(anyInt(), anyLong()))
                .thenReturn(hotResponse(item("a", 99), item("b", 88), item("c", 77), item("d", 66)));
        // b / d 非 PUBLISHED → 被过滤；a / c 保留
        articleOverrides.put("b", published("b", "B", ArticleStatus.DRAFT));
        articleOverrides.put("d", published("d", "D", ArticleStatus.REVIEWING));

        HotArticlesResp first = service.getHotArticles(new HotArticlesReq(1, 1));
        assertEquals(2, first.getTotal(), "total 是过滤后的窗口大小，不是 hot-rpc 返回的条数");
        assertEquals("a", first.getItems().get(0).getArticleId());
        assertEquals(1, first.getItems().get(0).getRank());

        HotArticlesResp second = service.getHotArticles(new HotArticlesReq(2, 1));
        assertEquals("c", second.getItems().get(0).getArticleId());
        assertEquals(2, second.getItems().get(0).getRank(), "第 2 页首条的 rank 是 2 而不是 1");
    }

    @Test
    @DisplayName("Go _test.go:150-183 —— article-rpc 失败只少一条，整包仍 200")
    void articleRpcFailureShrinksResultInsteadOfFailing() {
        when(hotRpcClient.getHotArticles(anyInt(), anyLong()))
                .thenReturn(hotResponse(item("a", 100), item("b", 90)));
        failingArticleIds.add("a");

        HotArticlesResp resp = service.getHotArticles(new HotArticlesReq(1, 20));

        assertEquals(1, resp.getTotal());
        assertEquals("b", resp.getItems().get(0).getArticleId());
        assertEquals(1, resp.getItems().get(0).getRank(), "rank 重算为 1");
    }

    @Test
    @DisplayName("成功路径记 result=success")
    void countsSuccess() {
        when(hotRpcClient.getHotArticles(anyInt(), anyLong()))
                .thenReturn(GetHotArticlesResponse.getDefaultInstance());

        service.getHotArticles(new HotArticlesReq(1, 20));

        assertEquals(1.0, registry.counter("hot_api_request_total",
                "route", HotApiMetrics.ROUTE, "result", HotApiMetrics.RESULT_SUCCESS).count());
    }

    // ---------------------------------------------------------------- helpers

    private static HotArticleItem item(String id, int score) {
        return HotArticleItem.newBuilder().setArticleId(id).setHotScore(score).build();
    }

    private static GetHotArticlesResponse hotResponse(HotArticleItem... items) {
        GetHotArticlesResponse.Builder builder = GetHotArticlesResponse.newBuilder();
        for (HotArticleItem item : items) {
            builder.addItems(item);
        }
        return builder.build();
    }

    private static GetArticleResponse published(String id, String title) {
        return published(id, title, ArticleStatus.PUBLISHED);
    }

    private static GetArticleResponse published(String id, String title, ArticleStatus status) {
        return GetArticleResponse.newBuilder()
                .setArticle(Article.newBuilder()
                        .setId(id)
                        .setTitle(title)
                        .setStatus(status)
                        .build())
                .build();
    }
}