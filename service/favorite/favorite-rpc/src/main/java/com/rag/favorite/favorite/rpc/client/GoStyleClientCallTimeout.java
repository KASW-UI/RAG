package com.rag.favorite.favorite.rpc.client;

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
 * {@code service/favorite/rpc/etc/favorite.yaml} 的 {@code UserRpc} / {@code ArticleRpc} 段
 * <b>一个字都没写 Timeout</b>（只有 {@code Etcd.{Hosts,Key}}）⇒ 2s 只能从
 * <b>struct tag 默认</b>推出来（AGENTS.md §3.3.1 的典型形态：「配置里没写」≠「没开」）。
 *
 * <h2>🔴 为什么「没有请求预算时也要套 2s」</h2>
 * <p>Go 的客户端超时是<b>拦截器级别</b>的，与有没有 HTTP 超时中间件<b>无关</b>
 * ⇒ 「有时有客户端超时、有时没有」在 Go 的 ctx 语义里<b>不存在</b>。
 * <br>⇒ 本域是<b>纯 gRPC 进程</b>（{@code web-application-type: none}），
 * {@code GoStyleRequestDeadline} 的 ThreadLocal <b>永远不会被 set</b>
 * （唯一 set 点在共享层的 HTTP worker 里）⇒ 只用 {@code applyTo} 会让本域的出向调用
 * <b>完全没有上限</b> ⇒ 本类把「无预算 ⇒ 用 2000ms」显式写出来。
 *
 * <h2>⚠️ 范式来源</h2>
 * <p>本仓 <b>同域</b> {@code favorite-api/.../client/FavoriteRpcClient.java:111,156-168}
 * 的 {@code CLIENT_TIMEOUT_MILLIS} ＋ {@code capByClientTimeout} 是<b>同款漂移的现成正确实现</b>
 * ⇒ 直接照抄，<b>不发明第三种写法</b>（AGENTS.md §3.2）。本类只是把它<b>提到 rpc 侧的
 * package-private 工具类</b>，让 {@code UserRpcClient} / {@code ArticleRpcClient}
 * 两个出向出口<b>共用同一处</b>，避免同一个封顶逻辑在两个文件里各写一遍。
 *
 * <h2>⚠️ 本类只解决「2s 封顶」这一半</h2>
 * <p>Go 的 {@code depCtx} 还会把<b>服务端</b>的 deadline 传播给下游调用（先到者生效）
 * ⇒ RAG 侧「继承服务端 deadline」那一半的落点在<b>共享层</b> ⇒ <b>已登记为 global 的 E65</b>
 * ⇒ 本类不重复实现，也不越权去改共享层。
 */
final class GoStyleClientCallTimeout {

    /**
     * Go {@code zrpc/config.go:31} 的 {@code json:",default=2000"} → 2000ms。
     *
     * <p>⚠️ <b>不是配置项</b> —— Go 侧没在 yaml 里写，靠 struct tag 默认。
     */
    static final long CLIENT_TIMEOUT_MILLIS = 2000L;

    private GoStyleClientCallTimeout() {
        // 纯静态工具，禁止实例化
    }

    /**
     * 把 deadline 压到「ctx 预算」与「客户端超时」里的<b>较小者</b>。
     *
     * <p><b>语义</b>：Go 侧客户端拦截器
     * {@code go-zero@v1.10.2/zrpc/internal/clientinterceptors/timeoutinterceptor.go:17-27} 做的是
     * {@code ctx, cancel := context.WithTimeout(ctx, c.Timeout)} ——
     * 即「在已有 ctx 预算之上再套一层 2s」，<b>取更小的生效</b>。
     *
     * <p><b>⚠️ 没有 ctx 预算时也要套 2s</b>：Go 的客户端超时是
     * <b>拦截器级别</b>的，与有没有 HTTP 超时中间件无关；
     * 若此时原样返回，就等于「有时有客户端超时、有时没有」——
     * 那正是 Go 的 ctx 语义里不存在的不一致。
     *
     * @param stub 任意 grpc blocking stub（<b>调用方须先套上请求级 deadline</b>）
     */
    static <T extends AbstractStub<T>> T capByClientTimeout(T stub) {
        io.grpc.Deadline deadline = stub.getCallOptions().getDeadline();
        long remainingNanos = deadline == null
                ? Long.MAX_VALUE
                : deadline.timeRemaining(TimeUnit.NANOSECONDS);
        long capNanos = TimeUnit.MILLISECONDS.toNanos(CLIENT_TIMEOUT_MILLIS);
        if (remainingNanos <= 0L) {
            // 预算已耗尽 ⇒ 立刻失败（Go 侧 ctx 到期同理）
            return stub.withDeadlineAfter(1L, TimeUnit.NANOSECONDS);
        }
        return stub.withDeadlineAfter(Math.min(remainingNanos, capNanos), TimeUnit.NANOSECONDS);
    }
}
