package com.rag.comment.comment.rpc.client;

import io.grpc.stub.AbstractStub;

import java.util.concurrent.TimeUnit;

/**
 * 🔴 <b>出向 gRPC 调用的 2s 客户端封顶</b> —— 对齐 go-zero 的
 * {@code zrpc/internal/client.go} 无条件装的 {@code TimeoutInterceptor}。
 *
 * <h2>Go 出处（消费点，不是声明点）</h2>
 * <pre>
 * go-zero@v1.10.2/zrpc/internal/client.go:113-117
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
 * <br>⇒ 而 {@code GoStyleRequestDeadline#applyTo} 在<b>没有</b>请求预算时**原样返回 stub**
 * （不挂 deadline）⇒ 光用它会让本域的出向调用**完全没有上限**
 * ⇒ 本类把「无预算 ⇒ 用 2000ms」显式写出来。
 *
 * <h2>⚠️ 为什么是「min(2s, 剩余预算)」而不是固定 2s</h2>
 * <p>Go 的 {@code context.WithTimeout(ctx, 2s)} 是<b>嵌在已有 ctx 之上</b>的
 * ⇒ 父 ctx 的 deadline 更早时，<b>先到者生效</b> ⇒ Java 侧必须取 min。
 *
 * <h2>🔴 2026-10-30 订正 · 「继承」那一半<b>已由共享层落码</b>（本类原文此处已过期）</h2>
 * <p><b>原文写的是</b>「Go 的 depCtx 还会把服务端的 deadline 传播给下游调用 ⇒ RAG 侧
 * 『继承服务端 deadline』那一半的落点在<b>共享层</b> ⇒ <b>已登记为 global 的 E65</b>
 * ⇒ 本类不重复实现，也不越权去改共享层」。
 * <br>⇒ <b>现状</b>：共享层 {@code 53bc05ec} 已把 {@code GoStyleRequestDeadline}
 * 绑进 {@code RpcInterceptor.onHalfClose}
 * （{@code service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java:519}）
 * ⇒ ⇒ 载体在位 ⇒ <b>「继承」那一半已经闭合</b>（本类不再需要自己处理继承）
 * ⇒ ⇒ 本类的职责收敛为「<b>在已继承的预算之上</b>再压 2s 客户端封顶」。
 *
 * <h2>🔴 2026-10-30 订正 · 精度走<b>纳秒</b>（不是毫秒）</h2>
 * <p><b>旧实现读的是毫秒</b>（{@code GoStyleRequestDeadline.remainingMillis()}）
 * ⇒ 它把<b>亚毫秒部分截掉</b>（{@code GoStyleRequestDeadline.java:90-96}）
 * ⇒ 与 Go 的纳秒预算（{@code go-zero@v1.10.2/zrpc/internal/clientinterceptors/timeoutinterceptor.go:25}
 * 的 {@code context.WithTimeout(ctx, t)} ⇒ t 是纳秒）**差 ≤1ms**。
 * <br>⇒ 现改为：出向上界 = {@code min(载体剩余<b>纳秒</b>, 2s)}
 * ⇒ 读法与共享层 {@code GoStyleRequestDeadline.remainingNanos()}（{@code :109-115}）一致。
 *
 * <h2>⚠️ 范式来源</h2>
 * <p>本仓<b>同波次同形态</b>的 {@code service/favorite/favorite-rpc/.../client/GoStyleClientCallTimeout.java:89-100}
 * （{@code capByClientTimeout}）是<b>同款漂移的现成正确实现</b> ⇒ 直接照抄，<b>不发明第三种写法</b>
 * （AGENTS.md §3.2）。本类只是把它<b>提到域内的 package-private 工具类</b>，
 * 让 {@code ArticleRpcClient} / {@code MessageRpcClient} 两个出向出口<b>共用同一处</b>，
 * 避免同一个封顶逻辑在两个文件里各写一遍、以后只改一处。
 *
 * <h2>⚠️ 两个出口的<b>载体可见性不同</b>（2026-10-30 域侧逐出口核对）</h2>
 * <p>本类的输入（stub 上已有的 deadline）来自<b>调用线程</b>上的载体：
 * <ul>
 *   <li>{@code ArticleRpcClient}（调用链 {@code CommentServiceGrpcImpl.java:73} ⇒
 *       {@code CommentDomainService.java:427/:431/:274/:254}）⇒ <b>gRPC handler 线程</b>
 *       ⇒ 共享层 {@code RpcInterceptor.onHalfClose} 绑的载体<b>可见</b> ⇒ <b>继承入向剩余预算</b>；</li>
 *   <li>{@code MessageRpcClient}（调用点 {@code CommentAuditListener.java:249}，该类 {@code :99}
 *       是 {@code @KafkaListener}）⇒ <b>Kafka consumer 线程</b> ⇒ 载体<b>不可见</b>
 *       ⇒ 只有本类的 2s 封顶。</li>
 * </ul>
 * ⇒ ⇒ 而 Go 侧那条 consumer 出口的 ctx 是 <b>Kafka 消费 ctx（无 deadline）</b>
 * ⇒ ⇒ <b>两侧同形</b> ⇒ consumer 那条出口<b>不是漂移</b>。
 * （逐出口证据 ⇒ {@code docs/DRIFT/comment/rpc.md} 第五轮新增段）
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
     * <p><b>🔴 纳秒读法（2026-10-30 订正）</b>：预算来自
     * {@code stub.getCallOptions().getDeadline()}（由
     * {@code GoStyleRequestDeadline.applyTo} 按<b>纳秒</b>挂上）
     * ⇒ 这里也按<b>纳秒</b>读（{@code timeRemaining(NANOSECONDS)}）
     * ⇒ <b>不再</b>经过 {@code remainingMillis()} 的毫秒截断 ⇒ 与 Go 的纳秒预算一致。
     *
     * <p>⚠️ 预算已耗尽时给 <b>1 纳秒</b>：Go 侧 ctx 一旦到期，下游调用<b>立即失败</b>；
     * 原样返回等于发起一个「没有 deadline 的慢调用」，那正是要避免的漂移。
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
