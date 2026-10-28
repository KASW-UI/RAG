package com.rag.favorite.favorite.rpc.client;

import com.rag.article.article.proto.ArticleServiceGrpc;
import com.rag.article.article.proto.GetArticleRequest;
import com.rag.common.observability.GoStyleRequestDeadline;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

/**
 * article-rpc gRPC client —— 持有 {@code ArticleServiceBlockingStub}。
 *
 * <p><b>Go 出处</b>：{@code service/favorite/rpc/internal/svc/service_context.go:38}
 * <pre>
 * ArticleRpc: articleservice.NewArticleService(zrpc.MustNewClient(c.ArticleRpc))
 * </pre>
 * 对应 {@code etc/favorite.yaml:43-47} 的 {@code Etcd.Key: article.rpc} ⇒ 服务发现名 {@code article-rpc}。
 *
 * <p><b>唯一消费者</b>是 {@code FavoriteArticleSnapshotResolver}
 * （Go 的 {@code logic/helper.go:126-178}）—— favorite 最核心业务规则
 * 「公开修订冻结」的取值来源。
 *
 * <h2>🔴 请求的三个字段全是 L1，不可改</h2>
 * <pre>
 * // logic/helper.go:133-137
 * resp, err := svcCtx.ArticleRpc.GetArticle(depCtx, &amp;articleservice.GetArticleRequest{
 *     ArticleId:  normalizedTargetID,
 *     IncrView:   false,
 *     PublicOnly: true,
 * })
 * </pre>
 * <ul>
 *   <li>{@code ArticleId} —— 已经过 {@code strings.TrimSpace}（helper.go:130）</li>
 *   <li>{@code IncrView = false} —— <b>收藏不得增加文章阅读量</b>。
 *       改成 true 会污染 article 侧的 view_count，且是<b>不可逆</b>的。</li>
 *   <li>{@code PublicOnly = true} —— 只读公开投影；为 false 会让收藏到未发布/草稿文章。</li>
 *   <li>{@code RequesterId} —— Go <b>不设</b>（零值空串）。favorite 不做作者读取，
 *       所以也不该设 ⇒ 保持未设置。</li>
 * </ul>
 */
@Component
public class ArticleRpcClient {

    @GrpcClient("article-rpc")
    private ArticleServiceGrpc.ArticleServiceBlockingStub articleServiceBlockingStub;

    /**
     * 返回<b>已套上「请求级 deadline ＋ 2s 客户端封顶」</b> 的 stub
     * （理由同 {@code UserRpcClient#userRpc}）。
     *
     * <p>🔴 <b>2s 客户端封顶（2026-10-27 G3 域侧自修 · A1）</b>：Go 侧
     * {@code zrpc.MustNewClient(c.ArticleRpc)} 的客户端<b>无条件</b>装
     * {@code TimeoutInterceptor}（{@code zrpc/internal/client.go:113-117}）⇒ 2000ms 上限；
     * 原先只有 {@code applyTo} ⇒ 本域出向调用<b>零期限</b> ⇒ 与 Go 分叉。
     */
    public ArticleServiceGrpc.ArticleServiceBlockingStub articleRpc() {
        return GoStyleClientCallTimeout.capByClientTimeout(
                GoStyleRequestDeadline.applyTo(articleServiceBlockingStub));
    }

    /**
     * 「按公开投影读取一篇文章」—— 对应 Go {@code helper.go:133-137} 的那次调用。
     *
     * <p>把三个 L1 字段固化在<b>这一处</b>，是为了让「收藏涨阅读量」这种事故
     * 变成一个只能改一行的地方，而不是散落在调用点的三个 boolean。
     *
     * @param trimmedArticleId <b>已经过 TrimSpace</b> 的文章 ID
     */
    public com.rag.article.article.proto.GetArticleResponse getPublicArticle(String trimmedArticleId) {
        GetArticleRequest request = GetArticleRequest.newBuilder()
                .setArticleId(trimmedArticleId)
                .setIncrView(false)     // L1：收藏不得增加阅读量
                .setPublicOnly(true)    // L1：只读公开（已发布）投影
                // requesterId 刻意不设：Go 侧零值空串，favorite 不做作者读取
                .build();
        return articleRpc().getArticle(request);
    }
}