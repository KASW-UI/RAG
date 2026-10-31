package com.rag.hot.hot.api.client;

import io.grpc.stub.AbstractStub;

import java.util.concurrent.TimeUnit;

/**
 * 「把<b>请求剩余预算</b>传播给出向调用」的<b>唯一实现点</b>（<b>只传播，不封顶</b>）。
 *
 * <h2>🆕 2026-10-31 · G3 lane · 第十三轮：本类<b>不再</b>自带 2s 上界</h2>
 * <p>本类原先做的是 {@code min(2s, 请求剩余预算)} ⇒ ⇒ 那 <b>2s</b> 是<b>第二个上界来源</b>
 * ⇒ ⇒ 与共享层「唯一实现」的形态冲突（共享层棘轮
 * {@code GoStyleClientCallTimeoutSingleSourceGateTest} 判据 ④：
 * 「cap 的来源必须是共享层参数，域侧私有常量 = 违规」）。
 * ⇒ ⇒ 现在：<b>本类只负责「传播请求预算」</b>，<b>上界交给共享层</b>
 * （本域 {@code application.yml} 的 {@code rag.protection.outbound-deadline.cap-millis}
 * ⇒ 共享层 {@code GoStyleOutboundDeadlineInterceptor} 以 <b>min</b> 语义并入）。
 *
 * <h2>为什么本组件<b>必须</b>显式传播（不能靠共享层读载体）</h2>
 * <p>本组件的补全跑在<b>线程池 worker</b>上，而
 * {@link com.rag.common.observability.GoStyleRequestDeadline} 是 <b>ThreadLocal</b>
 * （由 {@code HttpMiddleware} 设在跑业务的 worker 线程上）
 * ⇒ ⇒ worker 上读不到载体 ⇒ ⇒ 共享层拦截器在那里<b>看不到</b>请求预算
 * ⇒ ⇒ 预算必须在<b>请求线程</b>上取一次（{@code HotArticleHydrator.remainingBudgetMs}
 * 把请求的<b>绝对</b> deadline 折算成剩余毫秒）⇒ ⇒ <b>显式</b>传进来。
 *
 * <h2>Go 出处（三段，缺一段就得不出「2s」这个数 —— 但 2s <b>不在本类</b>）</h2>
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
 * <p>⇒ 现在这条 min 由<b>两段接力</b>完成：<b>本类</b>传播「请求剩余预算」，
 * <b>共享层</b>再并入「客户端上界 2000ms」⇒ ⇒ 合起来仍是 min(2s, 请求预算)。
 *
 * <h2>为什么需要这个类，而不是在每个 client 里各写一遍</h2>
 * 本组件有<b>两个</b> gRPC client（hot-rpc / article-rpc），两处各写一遍的失效形态是
 * 「改了一处忘了另一处」，且没有任何测试会失败。
 */
final class GoStyleGrpcCallDeadline {

    private GoStyleGrpcCallDeadline() {
    }

    /**
     * 把<b>请求剩余预算</b>套到 stub 上（<b>不封顶</b>）。
     *
     * @param stub             任意 grpc blocking stub
     * @param remainingBudgetMs 请求剩余预算（毫秒）；{@code -1} 表示本次请求<b>没有</b> HTTP 超时预算
     *                          （对应 Go「没装 TimeoutHandler ⇒ ctx 上没有 deadline」）
     * @return 带 deadline 的<b>新</b> stub —— 调用方不要缓存（见下）；
     *         「无预算」时<b>原样返回</b>（⇒ 交由共享层施加客户端上界）
     *
     * <p>⚠️ <b>返回值不可缓存到字段</b>：{@code withDeadlineAfter} 每次返回新实例，
     * 缓存会把「第一次调用时的预算」固化下来，之后每个请求都带着过期 deadline。
     *
     * <p>⚠️ <b>预算已耗尽时给 1ns</b>（而不是原样返回 stub）：
     * Go 侧 ctx 一旦到期，下游调用<b>立即失败</b>；
     * 原样返回等于发起一个「没有 deadline 的慢调用」，那正是本类要修的漂移。
     */
    static <T extends AbstractStub<T>> T apply(T stub, long remainingBudgetMs) {
        if (remainingBudgetMs < 0L) {
            // ⚠️ 无请求预算 ⇒ **不挂任何 deadline**（对应 Go「ctx 上没有 deadline」）
            //   ⇒ ⇒ 此时出向调用的上界**只剩**共享层的客户端上界（cap-millis = 2000）
            //   ⇒ ⇒ **这里绝不能再补一个 2s**（那会重新造出第二个上界来源 ⇒ 共享层棘轮判红）
            return stub;
        }
        if (remainingBudgetMs <= 0L) {
            // 预算已耗尽 ⇒ Go 侧 ctx 已到期，下游调用**立即失败**
            return stub.withDeadlineAfter(1L, TimeUnit.NANOSECONDS);
        }
        return stub.withDeadlineAfter(remainingBudgetMs, TimeUnit.MILLISECONDS);
    }
}
