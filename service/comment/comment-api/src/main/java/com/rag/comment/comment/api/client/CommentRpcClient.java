package com.rag.comment.comment.api.client;

import com.rag.comment.comment.proto.CommentServiceGrpc;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

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
 * <p><b>⚠️ deadline</b>：Go 侧靠超时中间件把 deadline 传进下游调用，
 * RAG 侧在唯一能一处覆盖所有调用点的位置 —— 这个 getter —— 套上请求级 deadline。
 * <b>不要</b>把返回值缓存到字段：它每次返回新实例，缓存会把第一次调用时的预算固化下来。
 */
@Component
public class CommentRpcClient {

    @GrpcClient("comment-rpc")
    private CommentServiceGrpc.CommentServiceBlockingStub commentServiceBlockingStub;

    /** 返回<b>已套上请求级 deadline</b> 的 stub。 */
    public CommentServiceGrpc.CommentServiceBlockingStub commentRpc() {
        return com.rag.common.observability.GoStyleRequestDeadline.applyTo(commentServiceBlockingStub);
    }
}