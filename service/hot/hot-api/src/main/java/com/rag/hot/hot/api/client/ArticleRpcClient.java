package com.rag.hot.hot.api.client;

import com.rag.article.article.proto.ArticleServiceGrpc;
import com.rag.article.article.proto.GetArticleRequest;
import com.rag.article.article.proto.GetArticleResponse;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

/**
 * article-rpc gRPC client —— 对应 Go {@code api/internal/svc/serviceContext.go:20-22} 的
 * 窄接口 {@code ArticleRPC}（只暴露 {@code GetArticle}）。
 *
 * <pre>
 *   Go: ArticleRpc: articleRPCAdapter{client: articleservice.NewArticleService(zrpc.MustNewClient(c.ArticleRpcConf))}
 * </pre>
 *
 * <h2>L4 idiomatization：本 facade <b>比 Go 多做了一件事</b> —— 把「调用点必须记得传对」变成「调用点无法传错」</h2>
 * Go 侧 {@code getHotArticlesLogic.go:132-136} 的调用点是这么写的：
 * <pre>
 *   articleResp, rpcErr := l.svcCtx.ArticleRpc.GetArticle(l.ctx, &amp;articleservice.GetArticleRequest{
 *       ArticleId:  hotItem.GetArticleId(),
 *       IncrView:   false,
 *       PublicOnly: true,
 *   })
 * </pre>
 * 即 {@code PublicOnly=true} / {@code IncrView=false} 是<b>每次调用都要记得重复一遍</b>的约定，
 * 而 {@code ArticleRPC} 暴露的是<b>完整请求结构</b>（Go 侧 {@code serviceContext.go:104} 已注意到这点）。
 * <p>本 facade 只暴露 {@code getPublicArticle(articleId)}，两个 flag 在<b>唯一出口</b>硬编码：
 * <ul>
 *   <li><b>语义等价论证</b>：Go 侧本组件<b>只有这一个调用点</b>（{@code grep GetArticle}
 *       在 {@code api/} 下只命中 {@code :132}），且传的就是 {@code (id, false, true)}。
 *       全量迁移这一个点后语义逐字一致。</li>
 *   <li><b>等价性不成立的场景不会发生</b>：如果将来 hot-api 要新增「作者视角」或「计浏览量」的
 *       取文路径，Go 那种写法能直接改，facade 则需要显式加方法 ——
 *       这正是希望<b>显式化</b>的那类差异（而不是让它悄悄发生）。</li>
 * </ul>
 *
 * <h2>⚠️ 这两个 flag 是有副作用的，不是「风格参数」</h2>
 * <ul>
 *   <li>{@code incr_view=true} 会让 article-rpc 给文章 {@code +1} 阅读量
 *       （{@code article.proto:57}）。热门列表每被拉一次就有 N 篇文章阅读量 +1 ——
 *       Go 侧明确传 false，Java 侧必须同样。</li>
 *   <li>{@code public_only=true} 决定取的是<b>「当前可读的已发布版本」（frozen revision）</b>
 *       （{@code article.proto:58}）而不是可变的源行。这是 L2 关键：
 *       源行处于 REVIEWING 准备改稿时，公开投影仍是 PUBLISHED，
 *       文章<b>不会</b>被 {@code status != PUBLISHED} 这条过滤误杀 ——
 *       Go 用 {@code getHotArticlesLogic_test.go:47-60} 专门锁住这条，
 *       本模块的 {@code ArticleRpcClientTest} 对齐该断言。</li>
 * </ul>
 *
 * <h2>超时 / 熔断 / retry</h2>
 * 与 {@link HotRpcClient} 同源：2s 调用 deadline 由 {@link GoStyleGrpcCallDeadline} 施加；
 * client 熔断由 {@code com.rag.common.protection} 的全局拦截器承担；<b>不</b>加 retry（Go 无 Retries）。
 */
@Component
public class ArticleRpcClient {

    /** Go {@code PublicOnly: true} —— 见类注释。 */
    public static final boolean PUBLIC_ONLY = true;

    /** Go {@code IncrView: false} —— 见类注释。 */
    public static final boolean INCR_VIEW = false;

    @GrpcClient("article-rpc")
    private ArticleServiceGrpc.ArticleServiceBlockingStub articleServiceBlockingStub;

    /**
     * 取文章<b>公开只读</b>投影 —— 对应 Go {@code buildHotArticleItem} 的那次
     * {@code ArticleRpc.GetArticle}（{@code getHotArticlesLogic.go:132-136}）。
     *
     * @param articleId        Go {@code hotItem.GetArticleId()}
     * @param remainingBudgetMs 请求剩余预算；{@code -1} 表示无 HTTP 超时预算
     * @return 下游响应；<b>不</b>吞异常 —— Go 侧这一层失败是 <b>skip 该条</b>（best-effort），
     *         由 {@code HotArticleHydrator} 捕获并转成 skip
     */
    public GetArticleResponse getPublicArticle(String articleId, long remainingBudgetMs) {
        return GoStyleGrpcCallDeadline.apply(articleServiceBlockingStub, remainingBudgetMs)
                .getArticle(GetArticleRequest.newBuilder()
                        .setArticleId(articleId)
                        .setIncrView(INCR_VIEW)
                        .setPublicOnly(PUBLIC_ONLY)
                        // Go 未设置 RequesterId ⇒ proto3 零值 ""（不携带）
                        .build());
    }

    /** 仅供测试注入捕获型 stub（package-private，非生产 API）。 */
    ArticleServiceGrpc.ArticleServiceBlockingStub rawStub() {
        return articleServiceBlockingStub;
    }

    /** 仅供测试注入捕获型 stub（package-private，非生产 API）。 */
    void rawStubField(ArticleServiceGrpc.ArticleServiceBlockingStub stub) {
        this.articleServiceBlockingStub = stub;
    }
}