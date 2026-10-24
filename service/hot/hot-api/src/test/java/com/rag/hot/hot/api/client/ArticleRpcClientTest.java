package com.rag.hot.hot.api.client;

import com.rag.article.article.proto.Article;
import com.rag.article.article.proto.ArticleServiceGrpc;
import com.rag.article.article.proto.ArticleStatus;
import com.rag.article.article.proto.GetArticleRequest;
import com.rag.article.article.proto.GetArticleResponse;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ArticleRpcClient} 的真实 stub 级测试 —— 锁住 Go 侧用 fake 客户端主动断言的两条不变量。
 *
 * <p><b>对照基准（Go 侧）</b>：{@code service/hot/api/internal/logic/hot/getHotArticlesLogic_test.go:34-45}
 * <pre>
 *   func (f fakeArticleRPC) GetArticle(_ context.Context, in *articleservice.GetArticleRequest) (...) {
 *       if !in.PublicOnly || in.IncrView {
 *           return nil, errors.New("hot hydration must use public article scope without a view")
 *       }
 *       ...
 *   }
 * </pre>
 * ⇒ Go 把「公开只读、不计浏览量」写成了一条<b>会主动失败</b>的断言。
 * 本测试用捕获型拦截器读出<b>真正下发的请求</b>，等价地把它变成 Java 侧的断言。
 */
class ArticleRpcClientTest {

    private ManagedChannel channel;
    private CapturingClientInterceptor capture;
    private ArticleRpcClient client;

    @BeforeEach
    void setUp() {
        channel = CapturingClientInterceptor.unusedChannel();
        capture = new CapturingClientInterceptor(GetArticleResponse.newBuilder()
                .setArticle(Article.newBuilder()
                        .setId("a1")
                        .setTitle("T")
                        .setStatus(ArticleStatus.PUBLISHED)
                        .build())
                .build());
        client = new ArticleRpcClient();
        // 绕开 @GrpcClient 注入，构造一条「真的走拦截器」的真实 stub
        ArticleServiceGrpc.ArticleServiceBlockingStub stub = ArticleServiceGrpc
                .newBlockingStub(ClientInterceptors.intercept(channel, capture));
        client.rawStubField(stub);
    }

    @AfterEach
    void tearDown() {
        channel.shutdownNow();
    }

    @Test
    @DisplayName("Go 不变量：公开只读、不计浏览量、且不携带 requester_id")
    void mustRequestPublicScopeWithoutViewing() {
        client.getPublicArticle("a1", -1L);

        GetArticleRequest sent = (GetArticleRequest) capture.capturedRequest();
        assertNotNull(sent, "请求必须真的下发到 gRPC 层");
        assertEquals("a1", sent.getArticleId());
        assertTrue(sent.getPublicOnly(), "PublicOnly 必须为 true（否则读到可变源行而非已发布冻结版本）");
        assertFalse(sent.getIncrView(), "IncrView 必须为 false（否则每次拉热榜都给文章 +1 阅读量）");
        assertEquals("", sent.getRequesterId(), "Go 未设置 RequesterId，Java 侧同样不携带");
    }

    @Test
    @DisplayName("Go zrpc RpcClientConf.Timeout 的 tag 默认值 2000ms 会被显式施加")
    void mustApplyTwoSecondCallDeadline() {
        client.getPublicArticle("a1", -1L);

        Long deadlineMillis = capture.capturedDeadlineMillis();
        assertNotNull(deadlineMillis, "devh starter 不提供默认 deadline，必须由本模块显式补");
        // 留出执行耗时余量：断言「贴着 2000ms」，不是「小于 2000ms 就算过」
        assertTrue(deadlineMillis > 1500L && deadlineMillis <= 2000L,
                "deadline 应为 2000ms 量级，实际=" + deadlineMillis);
    }

    @Test
    @DisplayName("两层 deadline 取小：请求剩余预算短于 2s 时以请求预算为准")
    void mustUseRequestBudgetWhenShorterThanTwoSeconds() {
        client.getPublicArticle("a1", 300L);

        Long deadlineMillis = capture.capturedDeadlineMillis();
        assertNotNull(deadlineMillis);
        assertTrue(deadlineMillis > 0L && deadlineMillis <= 300L,
                "Go 是 context.WithTimeout(ctx, 2s) 嵌在请求 ctx 之上 ⇒ 取小，实际=" + deadlineMillis);
    }

    @Test
    @DisplayName("请求预算已耗尽时给最小 deadline，而不是发起无 deadline 的慢调用")
    void mustNotIssueUnboundedCallWhenBudgetExhausted() {
        client.getPublicArticle("a1", 0L);

        Long deadlineMillis = capture.capturedDeadlineMillis();
        assertNotNull(deadlineMillis);
        assertTrue(deadlineMillis >= 0L && deadlineMillis <= 50L,
                "预算耗尽应立即失败（1ms 上限，测试执行期间可能已归零），实际=" + deadlineMillis);
    }

    @Test
    @DisplayName("hot-rpc 侧同样套用 2s / 取小 规则（两个 client 共享同一份默认超时）")
    void hotRpcClientSharesSameDeadlineRule() {
        var capture2 = new CapturingClientInterceptor(
                com.rag.hot.hot.proto.GetHotArticlesResponse.getDefaultInstance());
        var hotClient = new HotRpcClient();
        hotClient.rawStubField(com.rag.hot.hot.proto.HotServiceGrpc
                .newBlockingStub(ClientInterceptors.intercept(channel, capture2)));

        hotClient.getHotArticles(100, -1L);

        Long deadlineMillis = capture2.capturedDeadlineMillis();
        assertNotNull(deadlineMillis);
        assertTrue(deadlineMillis > 1500L && deadlineMillis <= 2000L,
                "两个 client 必须共享 zrpc RpcClientConf.Timeout 的同一个默认值，实际=" + deadlineMillis);
        assertEquals(100,
                ((com.rag.hot.hot.proto.GetHotArticlesRequest) capture2.capturedRequest()).getTopK(),
                "Go getHotArticlesLogic.go:63 的 TopK 是硬编码的 100");
    }
}