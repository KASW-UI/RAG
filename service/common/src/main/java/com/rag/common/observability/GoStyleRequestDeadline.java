package com.rag.common.observability;

import io.grpc.stub.AbstractStub;

import java.util.concurrent.TimeUnit;

/**
 * 请求级 deadline 的<b>载体</b>：把 {@link HttpMiddleware} 的 HTTP 超时预算，
 * 显式带到 gRPC 调用点上 —— 即 Go 侧 <b>ctx 传播</b>的 Java 等价物。
 *
 * <h2>为什么需要它（Go 侧的机制，逐句对齐）</h2>
 *
 * <pre>
 *   rest/handler/timeouthandler.go:67-68
 *       ctx, cancelCtx := context.WithTimeout(r.Context(), h.dt)
 *       r = r.WithContext(ctx)                 // ← 关键：deadline 被**传进业务**
 *   rest/handler/timeouthandler.go:79-89
 *       go func() { h.handler.ServeHTTP(tw, r); close(done) }()
 * </pre>
 *
 * <p>⇒ Go 的「超时后不 kill goroutine」是**假象**：handler 函数还在跑，
 * 但它手上的 {@code ctx} 已经到期，于是所有 <b>ctx-aware</b> 的下游 I/O
 * （gRPC / DB）**立即中止**，goroutine 随之迅速结束。
 * <p>Java 没有隐式 ctx。超时分支只能 {@code Future.cancel(true)}。
 * ⚠️ <b>本段曾写「grpc-java 的阻塞 stub 不可中断，调用会一直挂到下游自己结束」——
 * 该说法已被实测推翻</b>（{@code DeadlineCancellationSemanticsTest} 三条对照）：
 * 阻塞调用<b>是可以被线程中断打断的</b>，而且中断会沿传输传导到服务端 handler。
 * <p><b>真实差异只在 status code 上</b>，而且它比「挂死」严重得多：
 * <table border="1">
 *   <caption>同一个「下游超时」事件的两条路径</caption>
 *   <tr><th>路径</th><th>客户端看到</th><th>在 codes.Acceptable 失败名单里？</th><th>breaker 会</th></tr>
 *   <tr><td>Go ctx 到期</td><td>{@code DEADLINE_EXCEEDED}</td><td><b>是</b></td><td>计为失败</td></tr>
 *   <tr><td>Java {@code Future.cancel(true)}</td><td>{@code CANCELLED}</td><td><b>否</b></td><td>计为<b>成功</b></td></tr>
 * </table>
 * ⇒ 不补 deadline，下游变慢对 client breaker <b>完全不可见</b>（被当成成功），
 * 熔断器永远不会因超时而打开 —— 这才是要补的实质。
 *
 * <h2>⚠️ 用 ThreadLocal 而不是显式传参，是一次<b>有意识的取舍</b></h2>
 *
 * <p>显式传参（每个 {@code rpcClient.method(req, deadline)}）在类型上更安全，
 * 但它要改 Go 侧<b>每个</b> logic 的函数签名，而 Go 那边恰恰是靠 ctx「隐形」传递的 ——
 * 强行显式化会让两边的调用形状对不上，也让「6 份逐字重复的 logic 模板」这一观察失效。
 * <p>ThreadLocal 的代价是<b>它对「谁在跑这段代码」有假设</b>，这个假设必须写死并被测住：
 * <ul>
 *   <li><b>必须在 worker 线程上 set</b>（不是容器线程）—— 业务跑在 worker 上，
 *       容器线程的 ThreadLocal 对它<b>不可见</b>。这是最容易写错的一处。</li>
 *   <li><b>必须在 worker 的 finally 里 clear</b> —— worker 来自线程池，
 *       残留会把下一个请求的 deadline 污染成上一个的（§3.3：静默串味）。</li>
 *   <li><b>无超时路径不 set</b> —— 对应 Go「没装 TimeoutHandler 就没有 ctx deadline」。</li>
 * </ul>
 *
 * <h2>⚠️ 与 client breaker 的关系（用户 2026-10-22 拍板）</h2>
 *
 * <p>deadline 生效后，gRPC 调用超时会真的返回 {@code DEADLINE_EXCEEDED}。
 * 而 {@code com.rag.common.protection.GoStyleGrpcClientBreakerInterceptor#isAcceptable}
 * 把 {@code DEADLINE_EXCEEDED} 列为<b>失败</b>（对齐 Go {@code zrpc/internal/codes/accept.go}）
 * ⇒ <b>超时会计入 breaker、并可能触发熔断</b>。
 * <p>这是<b>有意接受的</b>：在补 deadline 之前，该码<b>永远不可能出现</b>
 * （调用不会被 deadline 打断），所以这条判据是「按 Go 写好但从未被触发」。
 * 现在它开始真实生效，语义与 Go 一致 —— <b>不是新增偏离</b>。
 */
public final class GoStyleRequestDeadline {

    private static final ThreadLocal<Long> DEADLINE_NANOS = new ThreadLocal<>();

    private GoStyleRequestDeadline() {
    }

    /**
     * 在<b>当前线程</b>（必须是跑业务的那个 worker）设定绝对 deadline。
     *
     * @param deadlineNanos {@link System#nanoTime()} 时基下的绝对到期时刻
     *                      （由调用方按 {@code start + timeout} 算好传入）
     */
    public static void begin(long deadlineNanos) {
        DEADLINE_NANOS.set(deadlineNanos);
    }

    /** 清空。必须与 {@link #begin(long)} 成对，否则线程池复用会串味。 */
    public static void clear() {
        DEADLINE_NANOS.remove();
    }

    /** 当前线程是否处在有 deadline 的请求里。 */
    public static boolean isPresent() {
        return DEADLINE_NANOS.get() != null;
    }

    /** 剩余毫秒数；无 deadline 时返回 -1。 */
    public static long remainingMillis() {
        Long d = DEADLINE_NANOS.get();
        if (d == null) {
            return -1L;
        }
        return Math.max(0L, TimeUnit.NANOSECONDS.toMillis(d - System.nanoTime()));
    }

    /**
     * 给 stub 套上当前线程的剩余预算；<b>没有预算时原样返回</b>。
     *
     * <p>⚠️ <b>预算已耗尽时给 1 纳秒</b>（而不是原样返回）：
     * Go 侧 ctx 已到期时下游调用会<b>立刻失败</b>；若这里原样返回，
     * Java 就会发起一个「没有 deadline 的慢调用」—— 那正是要修的那个漂移。
     *
     * @param stub 任意 grpc blocking stub
     * @return 带 deadline 的新 stub（grpc 的 {@code withDeadlineAfter} 每次返回新实例，
     *         调用方<b>不需要</b>缓存；但也<b>不要</b>把它缓存在字段上，
     *         那会把「第一次调用时的预算」固化下来）
     */
    public static <T extends AbstractStub<T>> T applyTo(T stub) {
        Long d = DEADLINE_NANOS.get();
        if (d == null) {
            return stub;
        }
        long remainingNanos = d - System.nanoTime();
        if (remainingNanos <= 0L) {
            return stub.withDeadlineAfter(1L, TimeUnit.NANOSECONDS);
        }
        return stub.withDeadlineAfter(remainingNanos, TimeUnit.NANOSECONDS);
    }
}