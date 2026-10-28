package com.rag.common.protection;

import io.grpc.ForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * <b>gRPC 服务端熔断</b> —— 对齐 go-zero 的 {@code UnaryBreakerInterceptor}。
 *
 * <h3>Go 出处（消费点，不是声明点）</h3>
 * <pre>
 *   zrpc/internal/serverinterceptors/breakerinterceptor.go:28-40
 *       func UnaryBreakerInterceptor(ctx, req, info, handler) (resp any, err error) {
 *           breakerName := info.FullMethod                       // ← ① 隔离 key
 *           err = breaker.DoWithAcceptableCtx(ctx, breakerName, func() error {
 *               var err error
 *               resp, err = handler(ctx, req)                    // ← ② handler 的 error 即判据
 *               return err
 *           }, serverSideAcceptable)                             // ← ③ 失败判据
 *           return resp, convertError(err)
 *       }
 *
 *   breakerinterceptor.go:42-50
 *       func convertError(err error) error {
 *           if errors.Is(err, breaker.ErrServiceUnavailable) {
 *               return status.Error(gcodes.Unavailable, err.Error())   // ← ④ wire 契约
 *           }
 *           return err
 *       }
 *
 *   core/breaker/breaker.go:19
 *       var ErrServiceUnavailable = errors.New("circuit breaker is open")   // ← ④ 的文案
 * </pre>
 *
 * <h3>🔴 为什么观察点是 {@code ServerCall.close(status, trailers)}，不是 listener 的 onError</h3>
 * <p>Go 侧的熔断器判据来自 <b>handler 的返回值</b>（一个 {@code error}）。
 * grpc-java 没有「handler 返回值」这回事 —— 服务端实现用 {@code observer.onError(t)} 报错，
 * 而 grpc 内部最终把它变成 {@code call.close(status, trailers)}。
 * <p><b>关键：Go 侧所有会冒泡到熔断器的错误，在 grpc-java 里最终都体现为一次 {@code call.close}</b>：
 * <table border="1">
 *   <caption>四条错误路径 → 同一个观察点</caption>
 *   <tr><th>路径</th><th>Go</th><th>grpc-java</th></tr>
 *   <tr><td>handler 抛业务错误</td><td>返回 status error</td><td>{@code onError(sre)} → {@code call.close}</td></tr>
 *   <tr><td>handler 抛非 status 异常</td><td>返回普通 error</td><td>{@code onError(INTERNAL)} → {@code call.close}</td></tr>
 *   <tr><td>超时</td><td>返回 DEADLINE_EXCEEDED</td><td>{@code call.close(DEADLINE_EXCEEDED)}</td></tr>
 *   <tr><td><b>内层 shedding 拒绝</b></td><td>返回 RESOURCE_EXHAUSTED</td><td>{@code call.close(RESOURCE_EXHAUSTED)}</td></tr>
 * </table>
 * ⇒ <b>只盯 {@code close} 一个点，四条路径全覆盖，且不重复计数</b>（{@link #judged} 守卫）。
 *
 * <p>⚠️ <b>若内层（shedding）把拒绝吞掉、不走 {@code call.close}，本类就观察不到</b>
 * ⇒ 熔断永远不会因为 shedding 的拒绝而打开 ⇒ <b>与 Go 不同</b>。
 * 这是「链序 Breaker → Shedding」的直接后果，<b>必须</b>由 {@link GoStyleRpcSheddingInterceptor}
 * 用 {@code call.close(...)} 上报拒绝来保证。
 *
 * <h3>🔴 熔断器<b>自己</b>的拒绝不算失败</h3>
 * <p>Go 侧 {@code doReq} 在 {@code accept()} 失败时<b>直接返回</b>，<b>不走判据</b>
 * （{@code core/breaker/breaker.go:96-104}）⇒ 自己拒自己不算失败。
 * ⇒ 本类的拒绝走<b>未被包装的原始 call</b> ⇒ 不会触发自己的 {@code close} 覆写 ⇒ 不自计数。
 *
 * <h3>⚠️ 与 REST 侧（{@link GoStyleBreakerFilter}）的三处刻意不同</h3>
 * <ol>
 *   <li><b>隔离 key</b>：zrpc 用 {@code info.FullMethod}（gRPC 全方法名），
 *       REST 用 {@code METHOD://path}（{@code rest/handler/breakerhandler.go:19}）。</li>
 *   <li><b>失败判据</b>：zrpc 用 {@link GoStyleRpcFailurePolicy}（6 码白名单），
 *       REST 用「响应码 ≥ 500」（{@code breakerhandler.go:33}）。</li>
 *   <li><b>wire 契约</b>：zrpc 返回 gRPC status {@code UNAVAILABLE} + {@code "circuit breaker is open"}；
 *       REST 返回普通 error ⇒ 上层映射成 {@code 1015 / "service busy"}（见 {@link GoStyleCircuitOpenException}）。
 *       ⇒ <b>zrpc 侧刻意不抛 {@link GoStyleCircuitOpenException}</b>。</li>
 * </ol>
 */
public final class GoStyleRpcBreakerInterceptor implements ServerInterceptor {

    /** Go {@code core/breaker/breaker.go:19} {@code ErrServiceUnavailable} 的文案（wire 可见）。 */
    public static final String CIRCUIT_OPEN_DESCRIPTION = "circuit breaker is open";

    /** 每个 gRPC 全方法名一个 breaker（Go: {@code breakerName := info.FullMethod}）。 */
    private final Map<String, GoStyleBreaker> breakers = new ConcurrentHashMap<>();

    /** 关闭时（等价 Go 侧 {@code c.Middlewares.Breaker=false}）：透传，不做任何熔断。 */
    private final boolean enabled;

    /** breaker 实例的工厂（测试可注入固定随机源 ⇒ 跳闸点确定）。 */
    private final java.util.function.Function<String, GoStyleBreaker> breakerFactory;

    public GoStyleRpcBreakerInterceptor(boolean enabled) {
        this(enabled, GoStyleBreaker::new);
    }

    /**
     * 测试注入口：允许指定 breaker 工厂。
     *
     * <p>⚠️ 为什么要它：go-zero 的概率源不可播种（{@code mathx.NewProba} 用墙钟播种），
     * ⇒ 「跳闸点」在生产里是概率的；测试要确定性地断言「失败被记进去了」，
     * 就得把随机源固定住（{@code new GoStyleBreaker(name, () -> 0.0)} ⇒ 必拒绝）。
     */
    GoStyleRpcBreakerInterceptor(boolean enabled,
                                 java.util.function.Function<String, GoStyleBreaker> breakerFactory) {
        this.enabled = enabled;
        this.breakerFactory = breakerFactory;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

        if (!enabled) {
            return next.startCall(call, headers);
        }

        // ① Go: breakerName := info.FullMethod
        final String name = call.getMethodDescriptor().getFullMethodName();
        final GoStyleBreaker breaker = breakers.computeIfAbsent(name, breakerFactory);

        // Go: core/breaker/breaker.go:96-104 —— accept() 失败 ⇒ markDrop() + 直接返回
        if (!breaker.accept()) {
            breaker.markDrop();
            // ⚠️ 用**原始 call**（不是下面那个 wrappedCall）⇒ 自己拒自己不会触发自己的 close 覆写
            //    ⇒ 不会自计数（与 Go 的 doReq 一致：拒绝路径不走判据）
            call.close(Status.UNAVAILABLE.withDescription(CIRCUIT_OPEN_DESCRIPTION), new Metadata());
            // handler 不执行（Go: accept() 失败时 req() 从未被调用）
            return new ServerCall.Listener<ReqT>() {
            };
        }

        // ② 观察点：包住 ServerCall，等它 close 时判一次成败
        // ⚠️ 判据的输入只能是 **Status**，不能是 Throwable ——
        //    grpc-java 的 ServerCall.Listener **没有** onError(Throwable)（那是 StreamObserver 的方法），
        //    拦截器能观察到的唯一错误信号就是 ServerCall#close(Status, Metadata)。
        //    （Go 的判据输入是 handler 返回的 error；本仓已论证两层判据可压成一层 ⇒ 等价，见
        //     GoStyleRpcFailurePolicy 的类注释。）
        final AtomicBoolean judged = new AtomicBoolean(false);

        ServerCall<ReqT, RespT> watched = new ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(call) {
            @Override
            public void close(Status status, Metadata trailers) {
                if (judged.compareAndSet(false, true)) {
                    if (GoStyleRpcFailurePolicy.isFailure(status.getCode())) {
                        breaker.markFailure();
                    } else {
                        breaker.markSuccess();
                    }
                }
                super.close(status, trailers);
            }
        };

        return next.startCall(watched, headers);
    }

    /** 暴露 breaker 实例，便于测试与指标读取（按 gRPC 全方法名）。 */
    GoStyleBreaker breakerFor(String fullMethodName) {
        return breakers.get(fullMethodName);
    }
}
