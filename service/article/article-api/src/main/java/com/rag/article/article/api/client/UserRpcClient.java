package com.rag.article.article.api.client;

import com.rag.common.observability.GoStyleRequestDeadline;
import com.rag.users.user.proto.UserServiceGrpc;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

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

    /**
     * Go 的 <b>per-client 单次调用上限</b> —— 2000ms。
     *
     * <p>⚠️ <b>这是 struct tag 里的默认值，yaml 里看不到</b>（AGENTS.md §3.3.1 点名的形态）：
     * <pre>
     * // go-zero v1.10.2 · zrpc/config.go:31（声明点）
     *   Timeout int64 `json:",default=2000"`
     *
     * // service/article/api/etc/article-api.yaml:20-24 —— UserRpcConf **没有** Timeout 字段
     *   UserRpcConf:
     *     Etcd: { Hosts: [etcd:2379], Key: user.rpc }
     *   ⇒ 走 tag 默认 = 2000ms
     *
     * // zrpc/client.go:70-71（消费点）
     *   if c.Timeout > 0 { opts = append(opts, WithTimeout(time.Duration(c.Timeout)*time.Millisecond)) }
     *
     * // zrpc/internal/client.go:83 → buildUnaryInterceptors(cliOpts.Timeout)
     * // zrpc/internal/clientinterceptors/timeoutinterceptor.go:25（真正的执行点）
     *   ctx, cancel := context.WithTimeout(ctx, t)
     * </pre>
     * ⚠️ 同一份 yaml 里 {@code ArticleRpcConf.Timeout} 是<b>显式</b> 70000 —— 两个 client 的预算
     * <b>差 35 倍</b>，而逐字段对 yaml 时<b>看不出</b>这个差别。
     */
    private static final long CLIENT_TIMEOUT_MILLIS = 2000L;

    @GrpcClient("user-rpc")
    private UserServiceGrpc.UserServiceBlockingStub userServiceBlockingStub;

    /**
     * 返回<b>已套上双重预算</b>的 stub：<b>min(HTTP 剩余预算, 2s)</b>。
     *
     * <p>理由与 {@code ArticleRpcClient#articleRpc()} 的「请求级 deadline」是同一套，
     * 但这里<b>多一层 2s 的硬上限</b>（Go 侧由 {@code TimeoutInterceptor} 提供）。
     *
     * <h2>⚠️ 为什么必须自己算 min，不能链式 {@code withDeadlineAfter}</h2>
     * <pre>
     * // grpc-java io.grpc.CallOptions.withDeadline（1.66.0，字节码实测）：
     *   builder.deadline = deadline;        // ← **直接覆盖**，不是取最小
     * </pre>
     * ⇒ {@code applyTo(stub).withDeadlineAfter(2, SECONDS)} 会把 HTTP 剩余预算
     * <b>顶掉</b>（剩 0.5s 时反而给 2s）⇒ 请求可能超出 HTTP 总预算。
     * <p>Go 侧 {@code context.WithTimeout(ctx, 2s)} 的语义是<b>取父子 deadline 中较早的</b>
     * ⇒ Java 侧必须显式算出 {@code min}，不能依赖框架替你合并。
     *
     * <p>⚠️ <b>不要把返回值缓存到字段上</b>：{@code withDeadlineAfter} 每次返回新实例，
     * 缓存会把「第一次调用时的预算」固化下来，之后每个请求都带着过期 deadline。
     */
    public UserServiceGrpc.UserServiceBlockingStub userRpc() {
        long remaining = GoStyleRequestDeadline.remainingMillis();
        long effective = remaining < 0L
                ? CLIENT_TIMEOUT_MILLIS
                : Math.min(remaining, CLIENT_TIMEOUT_MILLIS);
        return userServiceBlockingStub.withDeadlineAfter(effective, TimeUnit.MILLISECONDS);
    }
}
