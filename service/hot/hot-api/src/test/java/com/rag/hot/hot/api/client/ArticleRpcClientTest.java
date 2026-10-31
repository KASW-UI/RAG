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
import static org.junit.jupiter.api.Assertions.assertNull;
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
    @DisplayName("⚠️ 2026-10-31 第十三轮：域 client **不再自带** 2s 上界 ⇒ 无请求预算时**不挂 deadline**（上界归共享层）")
    void mustNotCarryItsOwnTwoSecondCap() {
        client.getPublicArticle("a1", -1L);

        Long deadlineMillis = capture.capturedDeadlineMillis();
        assertNull(deadlineMillis,
                "Go zrpc RpcClientConf.Timeout 的 tag 默认值 2000ms 现在**由共享层**承担"
                        + "（`GoStyleOutboundDeadlineInterceptor` ＋ 本域 yml 的"
                        + " `rag.protection.outbound-deadline.cap-millis`）"
                        + "⇒ ⇒ 域 client **不得**再自带一份上界"
                        + "（那会是**第二个来源** = 漂移面；共享层棘轮判据 ④ 也判它违规）"
                        + "⇒ ⇒ 本测试用的 channel **没有**装共享层拦截器"
                        + "⇒ ⇒ 这里读到的必须是【无 deadline】"
                        + "（共享层那半的活体证据在 `HotApiOutboundDeadlineCapGateTest`：真发调用在 ≤2000ms 处失败）。");
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
    @DisplayName("两个 client 的**请求预算传播**规则一致（无预算 ⇒ 都不挂；有预算 ⇒ 都按预算挂）＋ TopK 硬编码 100")
    void hotRpcClientSharesSameDeadlineRule() {
        var capture2 = new CapturingClientInterceptor(
                com.rag.hot.hot.proto.GetHotArticlesResponse.getDefaultInstance());
        var hotClient = new HotRpcClient();
        hotClient.rawStubField(com.rag.hot.hot.proto.HotServiceGrpc
                .newBlockingStub(ClientInterceptors.intercept(channel, capture2)));

        // ① 无请求预算 ⇒ 两个 client 都**不挂** deadline（上界统一在共享层 ⇒ 域侧两个 client 同源）
        hotClient.getHotArticles(100, -1L);
        assertNull(capture2.capturedDeadlineMillis(),
                "⚠️ 2026-10-31 第十三轮：2s 上界改由共享层承担 ⇒ 域侧两个 client 都**不得**自带上界"
                        + "⇒ ⇒ 这里读到的必须是【无 deadline】（两个 client 的上界来源同源 = 共享层那一个）");

        // ② 有请求预算 ⇒ 两个 client 都**按预算**挂（Go 是 context.WithTimeout(ctx, 2s) 嵌在请求 ctx 之上 ⇒ 取小）
        hotClient.getHotArticles(100, 300L);
        Long hotDeadline = capture2.capturedDeadlineMillis();
        assertNotNull(hotDeadline);
        assertTrue(hotDeadline > 0L && hotDeadline <= 300L,
                "hot-rpc 方向的请求预算必须照样传播（实际=" + hotDeadline + "ms）");

        client.getPublicArticle("a1", 300L);
        Long articleDeadline = capture.capturedDeadlineMillis();
        assertNotNull(articleDeadline);
        assertTrue(articleDeadline > 0L && articleDeadline <= 300L,
                "article-rpc 方向的请求预算必须照样传播（实际=" + articleDeadline + "ms）"
                        + "⇒ ⇒ 两个 client 的**传播**规则一致；**上界**则统一由共享层承担");

        assertEquals(100,
                ((com.rag.hot.hot.proto.GetHotArticlesRequest) capture2.capturedRequest()).getTopK(),
                "Go getHotArticlesLogic.go:63 的 TopK 是硬编码的 100");
    }
}