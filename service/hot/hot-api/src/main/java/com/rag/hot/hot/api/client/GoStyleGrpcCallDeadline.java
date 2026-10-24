package com.rag.hot.hot.api.client;

import io.grpc.stub.AbstractStub;

import java.util.concurrent.TimeUnit;

/**
 * 「每次下游调用各自 2 秒，且与请求预算取小」的<b>唯一实现点</b>。
 *
 * <h2>Go 出处（三段，缺一段就得不出 2000ms 这个数）</h2>
 * <pre>
 *   ① zrpc/config.go:31            RpcClientConf.Timeout int64 `json:",default=2000"`
 *                                  // etc/hotcenter.yaml 的 HotRpcConf / ArticleRpcConf 都**没写** Timeout
 *                                  // ⇒ 生效值来自 struct tag，不是 yaml（AGENTS.md §3.3.1 的典型形态）
 *   ② zrpc/internal/config.go:15   ClientMiddlewaresConf.Timeout bool `json:",default=true"`
 *   ③ zrpc/internal/clientinterceptors/timeoutinterceptor.go:20-25
 *          ctx, cancel := context.WithTimeout(ctx, t)
 * </pre>
 *
 * <h2>为什么是 min(2s, 请求剩余预算) 而不是二者之一</h2>
 * {@code context.WithTimeout(ctx, 2s)} 是<b>嵌在已有 ctx 之上</b>的：
 * HTTP 层已经用 {@code context.WithTimeout(r.Context(), 3s)} 建了一个 deadline
 * （{@code service/common/observability/http.go:53}），
 * gRPC 这层再嵌 2s ⇒ <b>先到者生效</b>。
 * <p>典型分歧场景：hot-rpc 已花掉 1.5s，剩 1.5s 预算；此时 article-rpc 的单次上限就是 1.5s
 * 而非 2s。只实现「固定 2s」会让慢 article 在 Go 侧被 skip、在 Java 侧被采纳。
 *
 * <h2>为什么需要这个类，而不是在每个 client 里各写一遍</h2>
 * 本组件有<b>两个</b> gRPC client（hot-rpc / article-rpc），Go 侧它们共享同一份
 * {@code RpcClientConf.Timeout} 默认值。两处各写一遍的失效形态是「改了一处忘了另一处」，
 * 且没有任何测试会失败。
 */
final class GoStyleGrpcCallDeadline {

    /**
     * Go {@code zrpc/config.go:31} 的 {@code json:",default=2000"} → 2000ms。
     */
    static final long DEFAULT_CALL_TIMEOUT_MILLIS = 2000L;

    private GoStyleGrpcCallDeadline() {
    }

    /**
     * 给 stub 套上「min(2s, 请求剩余预算)」的 deadline。
     *
     * @param stub             任意 grpc blocking stub
     * @param remainingBudgetMs {@link com.rag.common.observability.GoStyleRequestDeadline#remainingMillis()}
     *                          的返回值；{@code -1} 表示本次请求<b>没有</b> HTTP 超时预算
     *                          （对应 Go「没装 TimeoutHandler ⇒ ctx 上没有 deadline」）
     * @return 带 deadline 的<b>新</b> stub —— 调用方不要缓存（见下）
     *
     * <p>⚠️ <b>返回值不可缓存到字段</b>：{@code withDeadlineAfter} 每次返回新实例，
     * 缓存会把「第一次调用时的预算」固化下来，之后每个请求都带着过期 deadline。
     *
     * <p>⚠️ 预算已耗尽时给 <b>1ms</b>（而不是原样返回 stub）：
     * Go 侧 ctx 一旦到期，下游调用<b>立即失败</b>；
     * 原样返回等于发起一个「没有 deadline 的慢调用」，那正是本类要修的漂移。
     */
    static <T extends AbstractStub<T>> T apply(T stub, long remainingBudgetMs) {
        long budget = remainingBudgetMs < 0
                ? DEFAULT_CALL_TIMEOUT_MILLIS
                : Math.min(DEFAULT_CALL_TIMEOUT_MILLIS, remainingBudgetMs);
        return stub.withDeadlineAfter(Math.max(1L, budget), TimeUnit.MILLISECONDS);
    }
}