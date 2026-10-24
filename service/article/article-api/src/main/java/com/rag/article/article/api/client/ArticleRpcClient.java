package com.rag.article.article.api.client;

import com.rag.article.article.proto.ArticleServiceGrpc;
import com.rag.common.observability.GoStyleRequestDeadline;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

/**
 * article-rpc gRPC client —— 持有 {@code ArticleServiceBlockingStub}。
 *
 * <p><b>Go 出处</b>：{@code service/article/api/internal/svc/service_context.go:24}
 * <pre>
 *   ArticleRpc: articleservice.NewArticleService(zrpc.MustNewClient(c.ArticleRpcConf))
 * </pre>
 *
 * <p><b>框架职责</b>：
 * <ul>
 *   <li>{@code @GrpcClient("article-rpc")} 由 devh grpc-client-spring-boot-starter
 *       自动注入阻塞式 stub；寻址走
 *       {@code grpc.client.article-rpc.address=discovery:///article-rpc}，
 *       即 Nacos 自动发现（对应 Go 端的 etcd key {@code article.rpc}）。</li>
 *   <li>取代了 Go 的 {@code zrpc.MustNewClient} + {@code articleservice.NewArticleService} 两层。</li>
 *   <li>Go 侧 api → rpc 的调用是<b>同步阻塞</b>的（无 goroutine / 无 errgroup），
 *       所以用 {@code BlockingStub} 而不是 future stub —— 这一点属于 L3
 *       （并发与失败传播语义），不是可以随手换的风格差异。</li>
 * </ul>
 *
 * <p><b>⚠️ retry：Go 侧确实没有，禁止加</b>。
 * {@code zrpc.RpcClientConf}（{@code zrpc/config.go}）<b>没有 {@code Retries} 字段</b>
 * （注意别与 {@code zrpc.ClientConf} 混淆，后者有 Retries，但 api 用的是前者）。
 * 加 retry 会让「一次失败 → 业务码 1015」变成「重试 N 次后仍失败」，
 * 可观察的<b>调用次数与延迟</b>都变了。1015 = {@code CodeServerBusy}（{@code errmsg.go:31}）。
 *
 * <p><b>⚠️ circuit breaker：Go 侧有，且默认开启</b>（{@code zrpc/internal/config.go:14}
 * {@code ClientMiddlewaresConf.Breaker bool json:",default=true"}，
 * {@code etc/article-api.yaml} 未配 Middlewares ⇒ 走 default）。
 * 实现由 {@code com.rag.common.protection.GoStyleGrpcClientBreakerInterceptor} 提供，
 * 本模块<b>只要扫描到 {@code com.rag.common} 就自动生效</b>（见 {@code ArticleApiApplication}）
 * —— <b>不要</b>在这里再写一个 client 拦截器。
 *
 * <p><b>⚠️ 与「CreateArticle 的 70s 独立超时」的关系</b>：
 * Go {@code create_article_logic.go:53} 对这一个调用显式加了
 * {@code zrpc.WithCallTimeout(70*time.Second)}，其余调用靠 ctx deadline。
 * 本模块的 HTTP 预算也是 70s（{@code observability.http-timeout: 70s}），
 * 因此<b>两条路径的预算在本部署下同值</b>；
 * 若日后调小 HTTP 超时，CreateArticle 这一路的差异会显现，
 * 届时按 {@code ArticleCreateService} 的注释处理。
 */
@Component
public class ArticleRpcClient {

    @GrpcClient("article-rpc")
    private ArticleServiceGrpc.ArticleServiceBlockingStub articleServiceBlockingStub;

    /**
     * 返回<b>已套上请求级 deadline</b> 的 stub。
     *
     * <p>理由与 {@code MessageRpcClient#messageRpc()} 完全相同：Go 侧靠
     * {@code observability/http.go:53-56} 的 {@code context.WithTimeout(r.Context(), timeout)}
     * + {@code r.WithContext(ctx)} 把 deadline 传进<b>全部</b>下游调用；
     * RAG 侧唯一能一处覆盖所有调用点的位置就是这个 getter。
     *
     * <p>⚠️ <b>不要把返回值缓存到字段上</b>：{@code withDeadlineAfter} 每次返回新实例，
     * 缓存会把「第一次调用时的预算」固化下来，之后每个请求都带着过期 deadline。
     */

    public ArticleServiceGrpc.ArticleServiceBlockingStub articleRpc() {
        return GoStyleRequestDeadline.applyTo(articleServiceBlockingStub);
    }
}
