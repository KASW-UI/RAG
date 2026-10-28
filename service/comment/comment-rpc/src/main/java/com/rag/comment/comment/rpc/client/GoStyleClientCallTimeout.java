package com.rag.comment.comment.rpc.client;

import com.rag.common.observability.GoStyleRequestDeadline;
import io.grpc.stub.AbstractStub;

import java.util.concurrent.TimeUnit;

/**
 * 🔴 <b>出向 gRPC 调用的 2s 客户端封顶</b> —— 对齐 go-zero 的
 * {@code zrpc/internal/client.go} 无条件装的 {@code TimeoutInterceptor}。
 *
 * <h2>Go 出处（消费点，不是声明点）</h2>
 * <pre>
 * go-zero@v1.10.2/zrpc/internal/client.go:113-117
 *   if c.middlewares.Breaker { interceptors = append(interceptors, clientinterceptors.BreakerInterceptor) }
 *   if c.middlewares.Timeout {
 *       interceptors = append(interceptors, clientinterceptors.TimeoutInterceptor(timeout))
 *   }
 *
 * go-zero@v1.10.2/zrpc/internal/clientinterceptors/timeoutinterceptor.go:17-29
 *   func TimeoutInterceptor(timeout time.Duration) grpc.UnaryClientInterceptor {
 *       return func(ctx context.Context, method string, req, reply any, cc *grpc.ClientConn, ...) error {
 *           t := getTimeoutFromCallOptions(opts, timeout)
 *           if t &lt;= 0 { return invoker(ctx, method, req, reply, cc, opts...) }
 *           ctx, cancel := context.WithTimeout(ctx, t)   // ← 在**已有 ctx 之上**再套一层
 *           defer cancel()
 *           return invoker(ctx, method, req, reply, cc, opts...)
 *       }
 *   }
 *
 * go-zero@v1.10.2/zrpc/internal/config.go:15
 *   Timeout bool `json:",default=true"`   // ← 中间件开关，默认**开**
 * go-zero@v1.10.2/zrpc/config.go:31
 *   Timeout int64 `json:",default=2000"`   // ← 2000ms
 * </pre>
 *
 * <p>⚠️ <b>为什么是 2000ms 而不是 yaml 里能读到的值</b>：
 * {@code service/comment/rpc/etc/comment.yaml} 的 {@code ArticleRpc} / {@code MessageRpc} 段
 * <b>一个字都没写 Timeout</b> ⇒ 2s 只能从 <b>struct tag 默认</b>推出来
 * （AGENTS.md §3.3.1 的典型形态：「配置里没写」≠「没开」）。
 *
 * <h2>🔴 为什么「没有请求预算时也要套 2s」</h2>
 * <p>Go 的客户端超时是<b>拦截器级别</b>的，与有没有 HTTP 超时中间件<b>无关</b>
 * ⇒ 「有时有客户端超时、有时没有」在 Go 的 ctx 语义里<b>不存在</b>。
 * <br>⇒ 而 {@link GoStyleRequestDeadline#applyTo} 在<b>没有</b>请求预算时**原样返回 stub**
 * （不挂 deadline）⇒ 光用它会让本域的出向调用**完全没有上限**
 * ⇒ 本类把「无预算 ⇒ 用 2000ms」显式写出来。
 *
 * <h2>⚠️ 为什么是「min(2s, 剩余预算)」而不是固定 2s</h2>
 * <p>Go 的 {@code context.WithTimeout(ctx, 2s)} 是<b>嵌在已有 ctx 之上</b>的
 * ⇒ 父 ctx 的 deadline 更早时，<b>先到者生效</b> ⇒ Java 侧必须取 min。
 *
 * <h2>⚠️ 范式来源</h2>
 * <p>本仓 <b>同域</b> {@code comment-api/.../client/CommentRpcClient.java:96-102}
 * 的 {@code withDeadline} 是<b>同款漂移的现成正确实现</b> ⇒ 直接照抄，<b>不发明第三种写法</b>
 * （AGENTS.md §3.2）。本类只是把它<b>提到域内的 package-private 工具类</b>，
 * 让 {@code ArticleRpcClient} / {@code MessageRpcClient} 两个出向出口<b>共用同一处</b>，
 * 避免同一个封顶逻辑在两个文件里各写一遍、以后只改一处。
 *
 * <h2>⚠️ 本类只解决「2s 封顶」这一半</h2>
 * <p>Go 的 {@code depCtx} 还会把<b>服务端</b>的 deadline 传播给下游调用（先到者生效）
 * ⇒ RAG 侧「继承服务端 deadline」那一半的落点在<b>共享层</b>
 * （{@code GoStyleRequestDeadline} 目前只有 HTTP worker 会 {@code begin}）
 * ⇒ <b>已登记为 global 的 E65</b> ⇒ 本类不重复实现，也不越权去改共享层。
 */
final class GoStyleClientCallTimeout {

    /**
     * Go {@code zrpc/config.go:31} 的 {@code json:",default=2000"} → 2000ms。
     *
     * <p>⚠️ <b>不是配置项</b> —— Go 侧没在 yaml 里写，靠 struct tag 默认。
     */
    static final long DEFAULT_CALL_TIMEOUT_MILLIS = 2000L;

    private GoStyleClientCallTimeout() {
        // 纯静态工具，禁止实例化
    }

    /**
     * 返回<b>已套上「min(2s, 请求剩余预算)」deadline</b> 的 stub。
     *
     * <p>⚠️ <b>返回值不可缓存到字段</b>：{@code withDeadlineAfter} 每次返回新实例，
     * 缓存会把「第一次调用时的预算」固化下来，之后每个请求都带着过期 deadline。
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
}
