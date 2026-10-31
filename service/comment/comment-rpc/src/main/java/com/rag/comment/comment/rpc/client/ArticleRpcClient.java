package com.rag.comment.comment.rpc.client;

import com.rag.article.article.proto.ArticleServiceGrpc;
import com.rag.article.article.proto.GetArticleRequest;
import com.rag.article.article.proto.GetArticleResponse;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * article-rpc 客户端 —— 对应 Go 端 {@code service/comment/rpc/internal/svc/service_context.go:37}
 * 的 {@code ArticleRpc: articleservice.NewArticleService(zrpc.MustNewClient(c.ArticleRpc))}。
 *
 * <p><b>全模块只有一个调用点</b>：{@code internal/logic/subject_fallback.go:17} 的
 * {@code GetArticle(ArticleId: targetID, IncrView: false)} ——
 * 「本地没有这个 target 的评论区时，去 article 侧问一下作者是谁」。
 *
 * <h2>⚠️ RAG 的 proto 比 Go 侧多两个字段，必须显式置默认值</h2>
 * <p>Go 调用只填两个字段（subject_fallback.go:18-19）：
 * <pre>
 * &amp;articleservice.GetArticleRequest{ArticleId: targetID, IncrView: false}
 * </pre>
 * 而 RAG 的 {@code article.proto:55-60} 多了：
 * <pre>
 * bool   public_only  = 3;   // 公开读取仅返回当前可读的已发布版本
 * string requester_id = 4;   // 作者读取时由认证 HTTP 层传入 UID；RPC 先核作者再取正文
 * </pre>
 * proto3 的默认值是 {@code false} / {@code ""}，与「不传」在 wire 上<b>完全等价</b>。
 * 这里<b>显式写出</b>而不是依赖默认值，是为了让「Go 没传」这件事在代码里看得见 ——
 * 若将来某个默认值被改动（例如把 {@code public_only} 默认改成 true），
 * 这里会立刻与 Go 分叉，而显式赋值不会。
 *
 * <p>⚠️ <b>{@code incr_view} 必须是 {@code false}</b>：Go 明确传 false，
 * 评论区回落是「查作者」而不是「打开文章详情页」⇒ 不能给文章加阅读量。
 *
 * <h2>失败语义</h2>
 * <p>Go {@code subject_fallback.go:21-28} 把<b>所有</b>失败（RPC 错、resp 为空、article 为空、
 * authorId 解析失败、authorId ≤ 0）一律折叠成同一个 {@code ErrorSubjectNotFound}，
 * <b>不区分原因</b>。调用方（{@code resolveOwnerID} / {@code loadSubject}）据此走 1012。
 * 本类保持同样的折叠。
 */
@Component
public class ArticleRpcClient {

    private static final Logger LOG = LoggerFactory.getLogger(ArticleRpcClient.class);

    /**
     * 对应 Go {@code zrpc.MustNewClient(c.ArticleRpc)} 的寻址方式：
     * {@code ArticleRpc.Etcd.Key: article.rpc} ⇒ 服务发现名 {@code article-rpc}。
     */
    @GrpcClient("article-rpc")
    private ArticleServiceGrpc.ArticleServiceBlockingStub stub;

    /**
     * 出向 stub —— <b>deadline 由共享层的全局客户端拦截器统一施加</b>（2026-10-31 · G3 第十一轮）。
     *
     * <p>🔴 <b>Go 出处（行为层）</b>：Go 侧 {@code zrpc.MustNewClient(c.ArticleRpc)} 的客户端
     * <b>无条件</b>装 {@code TimeoutInterceptor}
     * （{@code zrpc/internal/client.go:113-117} ＋ {@code zrpc/internal/config.go:15} 的
     * {@code default=true}）⇒ 每次调用都有 <b>2000ms</b> 上限
     * （{@code zrpc/config.go:31} 的 {@code json:",default=2000"}；本域 Go 侧
     * {@code etc/comment.yaml} 的 {@code ArticleRpc:} 段<b>没写</b> {@code Timeout} ⇒ 走这个默认值）。
     *
     * <p>🔴 <b>为什么本类不再自己套 deadline</b>：Go 的客户端超时是<b>拦截器级别</b>的
     * ⇒ ⇒ RAG 侧同形也应是拦截器级 ⇒ ⇒ 改由共享层的
     * {@code GoStyleOutboundDeadlineInterceptor}（devh 的 {@code @GrpcGlobalClientInterceptor}
     * ⇒ 装在<b>每个</b>出向 devh 调用上 ⇒ 连<b>裸 stub</b> 都过链）统一施加，
     * 本域<b>只配参数</b>：{@code rag.protection.outbound-deadline.cap-millis=2000}
     * （见本组件 {@code application.yml}；共享层默认 {@code capMillis=0} ⇒ <b>不配 = 不启用</b>）。
     * <br>⇒ ⇒ 语义不变：出向上界 = <b>min(入向剩余预算, 2s 客户端封顶, 调用点自带)</b>
     * = Go 的 {@code context.WithTimeout(ctx, 2s)}（父 ctx 更早则先到者生效）。
     *
     * <p>⚠️ <b>域内私有副本 {@code GoStyleClientCallTimeout} 已删除</b>（本轮）：
     * <b>副本数 = 漂移面</b> —— 每份副本各自实现一遍「取 min」语义 ⇒ 极易有的取 min、
     * 有的写成覆盖 ⇒ ⇒ 共享层守门 {@code GoStyleClientCallTimeoutSingleSourceGateTest}
     * 的终态口径 = 「域侧 0 份，只配参数」。
     */
    private ArticleServiceGrpc.ArticleServiceBlockingStub articleRpc() {
        return stub;
    }

    /**
     * 对应 Go {@code resolveSubjectFallback} 里的 {@code GetArticle} 调用。
     *
     * @return {@code null} = 任何形式的失败（RPC 异常 / resp 空 / article 空 /
     *         authorId 非法 / authorId ≤ 0）—— 与 Go 的折叠行为一致
     */
    public Long fetchAuthorId(String articleId) {
        GetArticleResponse response;
        try {
            response = articleRpc().getArticle(GetArticleRequest.newBuilder()
                    .setArticleId(articleId)
                    // Go: IncrView: false
                    .setIncrView(false)
                    // ↓ Go 侧没有这两个字段，显式置 proto3 默认值以锁定「与 Go 等价」
                    .setPublicOnly(false)
                    .setRequesterId("")
                    .build());
        } catch (StatusRuntimeException e) {
            LOG.warn("article-rpc GetArticle failed for target {}: {}", articleId, e.getStatus());
            return null;
        }
        if (response == null || !response.hasArticle()) {
            return null;
        }
        String rawAuthorId = response.getArticle().getAuthorId();
        if (rawAuthorId == null) {
            return null;
        }
        // Go: strconv.ParseInt(strings.TrimSpace(resp.Article.AuthorId), 10, 64)
        long authorId;
        try {
            authorId = Long.parseLong(rawAuthorId.strip());
        } catch (NumberFormatException e) {
            return null;
        }
        if (authorId <= 0) {
            return null;
        }
        return authorId;
    }
}