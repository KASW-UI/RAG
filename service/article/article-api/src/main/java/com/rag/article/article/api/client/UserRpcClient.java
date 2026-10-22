package com.rag.article.article.api.client;

import com.rag.common.observability.GoStyleRequestDeadline;
import com.rag.users.user.proto.UserServiceGrpc;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

/**
 * user-rpc gRPC client —— 持有 {@code UserServiceBlockingStub}。
 *
 * <p><b>Go 出处</b>：{@code service/article/api/internal/svc/service_context.go:26}
 * <pre>
 *   UserRpc: userservice.NewUserService(zrpc.MustNewClient(c.UserRpcConf))
 * </pre>
 *
 * <p><b>唯一的消费者是「作者昵称补全」</b>：
 * {@code logic/article/helper.go:82} 的 {@code svcCtx.UserRpc.GetUser(...)}。
 * 它<b>不参与任何判定</b> —— 失败、找不到用户、username 为空，
 * 三种情况都只是退回 {@code fallbackAuthorName}（{@code "用户 <id>"} / {@code "未知作者"}）。
 * 这一点决定了它的失败绝不能冒泡到 HTTP 响应，见 {@code ArticleAuthorResolver}。
 *
 * <p>⚠️ 因为它只用于「锦上添花」，<b>批量列表里必须做请求内去重</b>：
 * Go {@code enrichArticleAuthors}（{@code helper.go:48-69}）用一个
 * {@code map[string]string} 保证<b>同一个 authorId 在一次请求里只调一次</b>。
 * N 篇文章 N 个作者就是 N 次 RPC；去掉去重会把一次列表请求放大成 N 次串行 RPC。
 *
 * <p><b>⚠️ retry / breaker 与 {@code ArticleRpcClient} 同款</b>：Go 侧无 retry；
 * breaker 由 {@code com.rag.common.protection} 统一提供，不在此重复实现。
 */
@Component
public class UserRpcClient {

    @GrpcClient("user-rpc")
    private UserServiceGrpc.UserServiceBlockingStub userServiceBlockingStub;

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

    public UserServiceGrpc.UserServiceBlockingStub userRpc() {
        return GoStyleRequestDeadline.applyTo(userServiceBlockingStub);
    }
}
