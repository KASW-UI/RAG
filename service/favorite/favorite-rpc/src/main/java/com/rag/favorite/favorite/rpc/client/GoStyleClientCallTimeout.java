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
 * <br>⇒ 本域的出向调用<b>可能跑在没有请求载体的线程上</b>
 * （{@code GoStyleRequestDeadline} 的 ThreadLocal 只在<b>绑它的那个线程</b>可见：
 * 共享层的 HTTP worker、以及共享层 {@code 53bc05ec} 起在
 * {@code RpcInterceptor.onHalfClose} 绑的 gRPC handler 线程；
 * ⚠️ <b>2026-10-31 订正</b>：原文写「唯一 set 点在共享层的 HTTP worker 里」<b>已过期</b>）
 * ⇒ 只用 {@code applyTo} 会让这些线程上的出向调用<b>完全没有上限</b>
 * ⇒ 本类把「无预算 ⇒ 用 2000ms」显式写出来。
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
 *
 * <h2>🔴 2026-10-31 归因订正 · 「继承」那一半由<b>devh 拦截器链</b>给，「2s 上界」那一半<b>只在域侧</b></h2>
 * <p><b>原文这句「本类只解决 2s 封顶那一半」在结论上仍成立，但归因需要补清楚</b>：
 * <ul>
 *   <li><b>「继承请求 deadline」那一半 ⇒ 由 devh 全局客户端拦截器链提供</b>：
 *       {@code GoStyleOutboundDeadlineInterceptor}（{@code @GrpcGlobalClientInterceptor}，
 *       {@code enabled} <b>默认 true</b>）的 {@code effectiveCallOptions} 第 ① 项读
 *       {@code GoStyleRequestDeadline.remainingNanos()} ⇒ 域内 stub 只要是 {@code @GrpcClient} 注入的
 *       （本域 main 代码零命中自建 channel ⇒ <b>无一例外</b>）就<b>已经</b>继承请求 deadline
 *       ⇒ ⇒ <b>本类的 {@code applyTo} 对「继承」这一半是冗余的</b>；</li>
 *   <li><b>「2s 客户端上界」那一半 ⇒ 只在域侧</b>：拦截器层的客户端硬上界
 *       （{@code GoStyleProtectionProperties.OutboundDeadline#capMillis}）<b>默认 0 = 不启用</b>，
 *       全仓 {@code service/<域>/<组件>/src/main/resources/*.yml} 的 {@code outbound-deadline} <b>零配置</b>
 *       ⇒ ⇒ Go 的 2000ms 客户端上界<b>只有本类提供</b>
 *       ⇒ ⇒ 且本类的 {@code applyTo} <b>不可去</b>：要算「{@code min(请求剩余, 2s)}」就得先有「请求剩余」
 *       ⇒ ⇒ <b>本类是「2s 上界」那一半的唯一落点</b>，不是闭合 E65 的条件。</li>
 * </ul>
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
