package com.rag.comment.comment.api.client;

import com.rag.common.observability.GoStyleRequestDeadline;
import com.rag.comment.comment.proto.CommentServiceGrpc;
import io.grpc.stub.AbstractStub;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * comment-rpc gRPC client —— 持有 {@code CommentServiceBlockingStub}。
 *
 * <p><b>Go 出处</b>：{@code service/comment/api/internal/svc/servicecontext.go:18-23}
 * <pre>
 * func NewServiceContext(c config.Config) *ServiceContext {
 *     conn := zrpc.MustNewClient(c.CommentRpc)
 *     return &ServiceContext{
 *         Config:     c,
 *         CommentCli: commentpb.NewCommentServiceClient(conn.Conn()),
 *     }
 * }
 * </pre>
 *
 * <p><b>框架职责</b>：
 * <ul>
 *   <li>{@code @GrpcClient("comment-rpc")} 由 devh grpc-client-spring-boot-starter
 *       自动注入阻塞式 stub，取代了 Go 的 {@code zrpc.MustNewClient} +
 *       {@code NewCommentServiceClient} 两层手工装配。</li>
 *   <li>Go 侧 api → rpc 的调用是<b>同步阻塞</b>的（无 goroutine / 无 errgroup），
 *       所以用 {@code BlockingStub} 而不是 future stub ——
 *       这属于 L3（并发与失败传播语义），不是可随手换的风格差异。</li>
 * </ul>
 *
 * <p><b>⚠️ retry：Go 侧没有，禁止加</b>。{@code zrpc.RpcClientConf}
 * <b>没有</b> {@code Retries} 字段（别与有该字段的 {@code zrpc.ClientConf} 混淆）。
 * 加 retry 会改变可观察的<b>调用次数与延迟</b>。
 *
 * <h2>⚠️ deadline：2 秒上限是 Go 的<b> struct tag 默认</b>，不是配置项</h2>
 *
 * <p><b>Go 出处</b>：{@code go-zero@v1.10.2/zrpc/config.go:31}
 * <pre>
 *   RpcClientConf.Timeout int64 `json:",default=2000"`     // ← 2000ms
 *   zrpc/internal/client.go:115-116                        // 装 TimeoutInterceptor(timeout)
 *   zrpc/internal/clientinterceptors/timeoutinterceptor.go:25   // context.WithTimeout(ctx, t)
 * </pre>
 * ⚠️ {@code etc/commentservice.yaml} <b>一个字都没写 Timeout</b>
 * ⇒ 2s 只能从 <b>struct tag 默认</b>推出来，<b>照 yaml 是推不出来的</b>（AGENTS.md §3.3.1 的典型形态）。
 *
 * <p><b>为什么是 min(2s, 请求剩余预算) 而不是固定 2s</b>：
 * Go 的 {@code context.WithTimeout(ctx, 2s)} 是<b>嵌在已有 ctx 之上</b>的 ——
 * HTTP 层已由 {@code observability.NewHTTPMiddleware} 建了 3s deadline
 * （{@code service/common/observability/http.go:53}），gRPC 这层再嵌 2s ⇒ <b>先到者生效</b>。
 *
 * <p>⚠️ <b>为什么不能只套 {@code GoStyleRequestDeadline.applyTo}</b>：
 * {@code applyTo} 在<b>没有</b> HTTP 预算时**原样返回 stub**（不挂 deadline），
 * 而 Go 侧的 timeout 中间件是<b>永远装着</b>的（{@code zrpc/internal/config.go:15}
 * {@code ClientMiddlewaresConf.Timeout bool `json:",default=true"`}）
 * ⇒ Go 的下游调用<b>永远</b>有 2s 上限，RAG 侧也必须如此。
 *
 * <p>⚠️ <b>范式来源</b>：本仓 {@code task-api} 的 {@code TaskRpcClient.withDeadline} 是<b>同款漂移的现成正确实现</b>
 * ⇒ 直接照抄，<b>不发明第三种写法</b>（AGENTS.md §3.2）。
 *
 * <p><b>⚠️ deadline 不可缓存到字段</b>：{@code withDeadlineAfter} 每次返回新实例，
 * 缓存会把「第一次调用时的预算」固化下来，之后每个请求都带着过期 deadline。
 */
@Component
public class CommentRpcClient {

    /**
     * Go {@code zrpc/config.go:31} 的 {@code json:",default=2000"} → 2000ms。
     *
     * <p>⚠️ <b>不是配置项</b> —— Go 侧没在 yaml 里写，靠 struct tag 默认。
     */
    static final long DEFAULT_CALL_TIMEOUT_MILLIS = 2000L;

    @GrpcClient("comment-rpc")
    private CommentServiceGrpc.CommentServiceBlockingStub commentServiceBlockingStub;

    /** 返回<b>已套上「min(2s, 请求剩余预算)」deadline</b> 的 stub。 */
    public CommentServiceGrpc.CommentServiceBlockingStub commentRpc() {
        return withDeadline(commentServiceBlockingStub);
    }

    /**
     * 套上「min(2s, 请求剩余预算)」的 deadline。
     *
     * <p>⚠️ <b>返回值不可缓存到字段</b>：{@code withDeadlineAfter} 每次返回新实例，
     * 缓存会把「第一次调用时的预算」固化下来。
     *
     * <p>⚠️ 预算已耗尽时给 <b>1ms</b>：Go 侧 ctx 一旦到期，下游调用<b>立即失败</b>；
     * 原样返回等于发起一个「没有 deadline 的慢调用」，那正是要避免的漂移。
     *
     * @param stub 任意 grpc blocking stub
     */
    static <T extends AbstractStub<T>> T withDeadline(T stub) {
        long remaining = GoStyleRequestDeadline.remainingMillis();
        long budget = remaining < 0
                ? DEFAULT_CALL_TIMEOUT_MILLIS
                : Math.min(DEFAULT_CALL_TIMEOUT_MILLIS, remaining);
        return stub.withDeadlineAfter(Math.max(1L, budget), TimeUnit.MILLISECONDS);
    }

    /** 仅供测试注入捕获型 stub（package-private，非生产 API）。 */
    CommentServiceGrpc.CommentServiceBlockingStub rawStub() {
        return commentServiceBlockingStub;
    }

    /** 仅供测试注入捕获型 stub（package-private，非生产 API）。 */
    void rawStubField(CommentServiceGrpc.CommentServiceBlockingStub stub) {
        this.commentServiceBlockingStub = stub;
    }
}
