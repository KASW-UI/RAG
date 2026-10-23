package com.rag.common.protection;

import io.grpc.ForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * <b>gRPC 服务端 CPU 自适应降载</b> —— 对齐 go-zero 的 {@code UnarySheddingInterceptor}。
 *
 * <h3>Go 出处（消费点，不是声明点）</h3>
 * <pre>
 *   zrpc/server.go:160-162
 *       if c.CpuThreshold > 0 {
 *           shedder := load.NewAdaptiveShedder(load.WithCpuThreshold(c.CpuThreshold))
 *           svr.AddUnaryInterceptors(serverinterceptors.UnarySheddingInterceptor(shedder, metrics))
 *       }
 *       ⇒ **每个 server 一个全局 shedder**，不是 per-route
 *
 *   zrpc/internal/serverinterceptors/sheddinginterceptor.go:22-46
 *       sheddingStat.IncrementTotal()
 *       promise, err = shedder.Allow()
 *       if err != nil {
 *           metrics.AddDrop()
 *           sheddingStat.IncrementDrop()
 *           err = status.Error(codes.ResourceExhausted, err.Error())   // ← ① wire 契约
 *           return
 *       }
 *       defer func() {
 *           if errors.Is(err, context.DeadlineExceeded) {
 *               promise.Fail()                                          // ← ② fail 判据
 *           } else {
 *               sheddingStat.IncrementPass()
 *               promise.Pass()                                          // ← ③ pass（内部测 RT）
 *           }
 *       }()
 *       return handler(ctx, req)
 *
 *   core/load/adaptiveshedder.go:34
 *       var ErrServiceOverloaded = errors.New("service overloaded")     // ← ① 的文案
 * </pre>
 *
 * <h3>🔴 ① 拒绝<b>必须</b>走 {@code call.close(...)}，不能吞掉</h3>
 * <p>Go 侧 shedding 位于熔断器<b>内层</b>（链序 {@code Breaker → Shedding}），
 * 它的 {@code RESOURCE_EXHAUSTED} 是<b>作为返回值冒泡到熔断器</b>的
 * ⇒ 熔断器会把它记成失败（{@code RESOURCE_EXHAUSTED} 在 Go 的 6 码失败名单里）
 * ⇒ <b>持续降载最终会把熔断器打开</b>。
 * <p>若本类把拒绝吞掉（不经过 {@code call.close}），{@link GoStyleRpcBreakerInterceptor}
 * 就<b>观察不到</b> ⇒ 熔断永远不会因降载而打开 ⇒ <b>与 Go 行为不同</b>。
 * ⇒ 本类的拒绝一律走 {@code call.close(...)}，让错误自然冒泡到外层。
 *
 * <h3>🔴 ② {@code fail()} 的判据是「<b>ctx 超时</b>」，不是「任何 DEADLINE_EXCEEDED」</h3>
 * <p>Go 判的是 {@code errors.Is(err, context.DeadlineExceeded)} ——
 * 即<b>客户端 deadline 过期</b>；若是服务端<b>自己</b>返回 {@code status.Error(DEADLINE_EXCEEDED, …)}，
 * Go 判的是 {@code promise.Pass()}（<b>不是</b> Fail）。
 * <p>⇒ Java 侧用 {@link ServerCall#getDeadline()} 的 {@link Deadline#isExpired()} 来区分这两者：
 * <ul>
 *   <li>客户端 deadline 过期 ⇒ deadline 已到 ⇒ {@code fail()} ✔ 与 Go 一致</li>
 *   <li>服务端自己返回 DEADLINE_EXCEEDED 但客户端 deadline 未到 ⇒ {@code pass()} ✔ 与 Go 一致</li>
 * </ul>
 * ⚠️ <b>不能用 {@code Context.current().isCancelled()} 代替</b>：它对「客户端主动取消」也为 true，
 * 而 Go 的名单里<b>没有</b> {@code context.Canceled} ⇒ 客户端取消在 Go 侧是 {@code Pass()}。
 *
 * <h3>⚠️ 与 REST 侧（{@link GoStyleCpuSheddingFilter}）的两处刻意不同</h3>
 * <ol>
 *   <li><b>{@code promise.Fail()} 的判据</b>：REST 侧看「响应码 == 503」
 *       （{@code rest/handler/sheddinghandler.go:45}），zrpc 侧看「ctx 超时」。<b>Go 侧两者就是不同</b>。</li>
 *   <li><b>拒绝的 wire 形态</b>：REST 侧是 HTTP 503 无 body；zrpc 侧是
 *       {@code RESOURCE_EXHAUSTED} + {@code "service overloaded"}。</li>
 * </ol>
 */
public final class GoStyleRpcSheddingInterceptor implements ServerInterceptor {

    /** Go {@code core/load/adaptiveshedder.go:34} {@code ErrServiceOverloaded} 的文案（wire 可见）。 */
    public static final String SERVICE_OVERLOADED_DESCRIPTION = "service overloaded";

    /**
     * <b>全局一个</b> shedder（Go: {@code zrpc/server.go:161} 每 server 一个，
     * 与 REST 侧 {@code rest/engine.go:52-56} 同构）—— <b>不是</b> per-route。
     */
    private final GoStyleAdaptiveShedder shedder;

    private final boolean enabled;

    public GoStyleRpcSheddingInterceptor(GoStyleAdaptiveShedder shedder, boolean enabled) {
        this.shedder = shedder;
        this.enabled = enabled;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

        if (!enabled) {
            return next.startCall(call, headers);
        }

        final long startNanos = System.nanoTime();

        // Go: sheddinginterceptor.go:36-45 的 defer —— 调用结束后结算 promise
        final AtomicBoolean settled = new AtomicBoolean(false);
        final AtomicBoolean rejected = new AtomicBoolean(false);

        ServerCall<ReqT, RespT> watched = new ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(call) {
            @Override
            public void close(Status status, Metadata trailers) {
                if (settled.compareAndSet(false, true)) {
                    // ⚠️ 拒绝路径**不结算 promise**：Go 的 defer 是在 Allow() 成功**之后**才注册的
                    //    （sheddinginterceptor.go:24-31 先 Allow，:36 才 defer）⇒ 拒绝时既不 Pass 也不 Fail。
                    if (!rejected.get()) {
                        if (isCtxDeadlineExpired(status.getCode())) {
                            shedder.fail();                 // Go: promise.Fail()
                        } else {
                            long rtMs = (System.nanoTime() - startNanos) / 1_000_000L;
                            shedder.pass(rtMs);             // Go: promise.Pass()（内部测 RT）
                        }
                    }
                }
                super.close(status, trailers);
            }
        };

        // Go: sheddinginterceptor.go:24-31 —— Allow() 失败即拒
        if (!shedder.allow()) {
            rejected.set(true);
            // 🔴🔴 必须关 **watched**（本层包装过的那个），**不能关原始 call** ——
            //    关原始 call ⇒ 外层 breaker 的 close 观察点**不会被触发**
            //    ⇒ breaker 看不到这次降载 ⇒ 永远不会因降载而打开 ⇒ **与 Go 行为不同**。
            //    （本轮第一版写的是 call.close(...)，被 GoStyleRpcProtectionChainTest 当场抓到。）
            watched.close(Status.RESOURCE_EXHAUSTED.withDescription(SERVICE_OVERLOADED_DESCRIPTION),
                    new Metadata());
            // handler 不执行（Go: Allow() 失败时 handler(ctx, req) 从未被调用）
            return new ServerCall.Listener<ReqT>() {
            };
        }

        return next.startCall(watched, headers);
    }

    /**
     * 本次调用是否属于「ctx 超时」—— Go 侧判 {@code errors.Is(err, context.DeadlineExceeded)}。
     *
     * <p>⚠️ <b>刻意只看 close 的 status 码</b>：Go 侧若服务端<b>自己</b>返回
     * {@code status.Error(DEADLINE_EXCEEDED, …)}，判的是 {@code promise.Pass()}（不是 Fail）。
     * <p>⇒ 本仓能等价的前提是：<b>message-rpc 的业务代码一次都没有显式使用
     * {@code DEADLINE_EXCEEDED}</b>（实测；唯一来源是 {@code SqlTimeoutStatusTranslator}
     * 把 PG 的 57014 翻成它，那是<b>真超时</b>，两侧都判 Fail，一致）。
     * <p>⚠️ 若将来某个 handler 开始自己造 {@code DEADLINE_EXCEEDED} status，
     * 这里会与 Go 分岔（Go 判 Pass、RAG 判 Fail）⇒ 那时要改判据。
     */
    private static boolean isCtxDeadlineExpired(Status.Code closeCode) {
        return closeCode == Status.Code.DEADLINE_EXCEEDED;
    }

    /** 排障用：当前在途请求数。 */
    long flying() {
        return shedder.flying();
    }
}
