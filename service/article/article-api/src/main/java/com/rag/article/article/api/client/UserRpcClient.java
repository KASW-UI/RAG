package com.rag.article.article.api.client;

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
 *
 * <h2>🆕 2026-11-02 · G3 lane：域侧内联副本<b>已剪除</b>（上界改由共享层承担）</h2>
 * <p>本类此前<b>自带</b>一份「客户端硬上界」副本：{@code min(请求剩余预算, 2s)}
 * + 一个域侧私有常量 ⇒ ⇒ 那是<b>第二个上界来源</b> ⇒ ⇒ 与共享层「唯一实现」
 * 的形态冲突（共享层棘轮 {@code GoStyleClientCallTimeoutSingleSourceGateTest}
 * 判据 ④：cap 的来源必须是共享层参数，域侧私有常量 = 违规）
 * ⇒ ⇒ <b>副本数 = 漂移面</b> ⇒ ⇒ 本轮剪掉。
 *
 * <h3>剪掉之后，2s 上界去哪了 —— <b>零行为变化</b>的逐项论证</h3>
 * <p><b>Go 侧的真相</b>（逐条回 Go 真源核对，非转述）：
 * <ul>
 *   <li>{@code go-zero@v1.10.2/zrpc/config.go:31} ——
 *       {@code Timeout int64 `json:",default=2000"`} ⇒ <b>默认 2000ms</b>；</li>
 *   <li>{@code service/article/api/etc/article-api.yaml:20-24} 的
 *       {@code UserRpcConf:} 段<b>没有</b>显式 {@code Timeout}
 *       ⇒ ⇒ user-rpc 方向 = <b>2000ms</b>；</li>
 *   <li>{@code go-zero@v1.10.2/zrpc/client.go:70-71} ——
 *       {@code if c.Timeout > 0 { opts = append(opts, WithTimeout(...)) }}；</li>
 *   <li>{@code go-zero@v1.10.2/zrpc/internal/clientinterceptors/timeoutinterceptor.go:25} ——
 *       {@code ctx, cancel := context.WithTimeout(ctx, t)} ⇒ <b>取父子 deadline 中较早者</b>。</li>
 * </ul>
 *
 * <h3>现在由谁承担 —— <b>共享层唯一实现</b>（域侧只配参数）</h3>
 * <ul>
 *   <li><b>实现</b>：{@code com.rag.common.protection.GoStyleOutboundDeadlineInterceptor}
 *       （带 devh 的 {@code @GrpcGlobalClientInterceptor}
 *       ⇒ ⇒ 进全局客户端拦截器 registry
 *       ⇒ ⇒ devh 的 {@code AbstractChannelFactory#createChannel} 最后一步是
 *       {@code ClientInterceptors.interceptForward(channel, interceptors)}
 *       ⇒ ⇒ 拦截器装在 <b>channel</b> 上 ⇒ ⇒ <b>裸 stub 同样经过</b>）；</li>
 *   <li><b>参数</b>：本域 {@code application.yml} 的
 *       {@code rag.protection.outbound-deadline.cap-millis-by-service.user.UserService: 2000}
 *       ⇒ ⇒ <b>域侧零代码改动</b>。</li>
 * </ul>
 *
 * <h3>为什么<b>算术</b>完全等价（不是「差不多」）</h3>
 * <table border="1">
 *   <caption>剪除前后的算术对照</caption>
 *   <tr><th>场景</th><th>剪除前（域侧内联）</th><th>剪除后（共享层）</th><th>结论</th></tr>
 *   <tr><td>请求预算不可读</td><td>2s</td><td>min(∞, 2s) = 2s</td><td>同值</td></tr>
 *   <tr><td>请求预算 &gt; 2s</td><td>min(预算, 2s) = 2s</td><td>min(预算, 2s) = 2s</td><td>同值</td></tr>
 *   <tr><td>请求预算 &lt; 2s</td><td>min(预算, 2s) = 预算</td><td>min(预算, 2s) = 预算</td><td>同值</td></tr>
 *   <tr><td>请求预算已耗尽</td><td>立即到期</td><td>立即到期（1ns）</td><td>同值</td></tr>
 * </table>
 * ⇒ ⇒ <b>请求预算的载体是同一个</b>（{@code GoStyleRequestDeadline} 的 ThreadLocal）
 * ⇒ ⇒ 传播<b>没有丢</b>，只是从域侧搬到了共享层。
 *
 * <p>⚠️ <b>唯一的精度差</b>：剪除前用毫秒（{@code remainingMillis()}），
 * 剪除后用纳秒（{@code remainingNanos()}）⇒ ⇒ 差 <b>&lt; 1ms</b>，且两者都取
 * <b>更早者</b> ⇒ ⇒ 只可能<b>更严</b>、不可能更长 ⇒ ⇒ 不构成行为差异。
 *
 * <p>⚠️ <b>返回值现在是字段本身</b>（不再每次 new 一个带 deadline 的副本）：
 * grpc-java 的 {@code AbstractStub} 是<b>不可变</b>的 ⇒ ⇒ 共享同一个实例是安全的
 * ⇒ ⇒ 原 javadoc 那句「不要把返回值缓存到字段上」的告诫<b>已不再适用</b>
 * —— 它的前提是「返回值带每次都不同的 deadline」，现在没有了。
 */
@Component
public class UserRpcClient {

    @GrpcClient("user-rpc")
    private UserServiceGrpc.UserServiceBlockingStub userServiceBlockingStub;

    /**
     * 返回<b>裸 stub</b>（不带任何 deadline）。
     *
     * <p>⚠️ <b>不要在这里加 deadline</b>：出向的「客户端硬上界」与「请求预算传播」
     * <b>全部</b>由共享层的 {@code GoStyleOutboundDeadlineInterceptor} 承担
     * （channel 级 ⇒ 裸 stub 同样经过）⇒ ⇒ 域侧再加一份就是<b>第二个上界来源</b>
     * ⇒ ⇒ 共享层棘轮判据 ④ 会判红。
     *
     * <p>本域的上界参数在 {@code application.yml}：
     * {@code rag.protection.outbound-deadline.cap-millis-by-service.user.UserService: 2000}。
     */
    public UserServiceGrpc.UserServiceBlockingStub userRpc() {
        return userServiceBlockingStub;
    }
}
