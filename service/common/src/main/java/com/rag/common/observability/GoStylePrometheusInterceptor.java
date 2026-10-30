package com.rag.common.observability;

import io.grpc.ForwardingServerCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * <b>Prometheus 层</b> —— 对齐 go-zero 的 {@code UnaryPrometheusInterceptor}：
 * 每个 unary 请求记一条<b>耗时直方图</b> ＋ 一条<b>状态码计数</b>。
 *
 * <h3>Go 出处（消费点，不是声明点）</h3>
 * <pre>
 *   go-zero@v1.10.2/zrpc/server.go:154-156
 *       if c.Middlewares.Prometheus {
 *           svr.AddUnaryInterceptors(serverinterceptors.UnaryPrometheusInterceptor)
 *       }
 *       └─ 默认值：zrpc/internal/config.go:24  Prometheus bool `json:",default=true"`
 *
 *   go-zero@v1.10.2/zrpc/internal/serverinterceptors/prometheusinterceptor.go:35-42
 *       func UnaryPrometheusInterceptor(ctx context.Context, req any,
 *           info *grpc.UnaryServerInfo, handler grpc.UnaryHandler) (any, error) {
 *           startTime := timex.Now()
 *           resp, err := handler(ctx, req)
 *           metricServerReqDur.Observe(timex.Since(startTime).Milliseconds(), info.FullMethod)
 *           metricServerReqCodeTotal.Inc(info.FullMethod, strconv.Itoa(int(status.Code(err))))
 *           return resp, err
 *       }
 *
 *   同文件 :13-31 —— 两个指标的**定义**（名字 / label / buckets）：
 *       Histogram: rpc_server_requests_duration_ms   label: method
 *                  buckets = 1,2,5,10,25,50,100,250,500,1000,2000,5000
 *       Counter  : rpc_server_requests_code_total    label: method + code
 * </pre>
 *
 * <h3>🔴 本层在链上的<b>位置</b>＝ Stat 之内、Breaker / Shedding 之外</h3>
 * <p>Go 的注册序是 {@code Trace → Recover → Stat → Prometheus → Breaker → Shedding → Timeout}
 * ⇒ ⇒ <b>被降载 / 熔断拒绝的请求也会被 Prometheus 层记一笔</b>
 * （Go 的 shedding 层是「返回 error」⇒ Prometheus 层的后置代码照样执行 ⇒
 * 记到 {@code code=8(RESOURCE_EXHAUSTED)} / {@code code=14(UNAVAILABLE)}）。
 * ⇒ ⇒ 本层必须装在 breaker / shedding 的<b>外层</b>，否则这两类请求在指标里<b>整个消失</b>。
 *
 * <h3>🔴 panic 时<b>不</b>记（这是 Go 的行为，本类照抄）</h3>
 * <p>Go 的 Prometheus 层是「<b>先调 handler、再记</b>」的直线代码 ⇒ handler panic 时，
 * 后面的两行<b>根本不会执行</b> ⇒ ⇒ <b>panic 的请求在 Prometheus 里没有任何样本</b>
 * （但 {@link GoStyleStatInterceptor} 仍然会记一条耗时样本 —— Go 的 Stat 层用 {@code defer}）。
 * ⇒ ⇒ 本类用「{@code onHalfClose} 是否抛出」来区分这两条路径 ⇒ 与 Go 同形。
 *
 * <h3>⚠️ 耗时口径</h3>
 * <p>Go 观测的是 {@code timex.Since(startTime).Milliseconds()} —— <b>整型毫秒（截断）</b>。
 * 本类同样<b>先截断成毫秒</b>再观测 ⇒ 桶归属与 Go 逐桶一致。
 */
public final class GoStylePrometheusInterceptor implements ServerInterceptor {

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

        // ⚠️ Go: core/metric/metric.go:14-20 —— prometheus 未启用 ⇒ **整次写入被丢弃**。
        //    本仓的同形语义 = 「没有 registry ⇒ 没有 sink ⇒ 本层整段跳过」。
        final GoStylePrometheusSink sink = GoStylePrometheusMetrics.sink();
        if (sink == null) {
            return next.startCall(call, headers);
        }

        final long startNanos = System.nanoTime();
        final String method = call.getMethodDescriptor().getFullMethodName();
        final long[] handlerDoneNanos = new long[1];
        final AtomicBoolean recorded = new AtomicBoolean(false);
        final AtomicBoolean panicked = new AtomicBoolean(false);

        ServerCall<ReqT, RespT> watched = new ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(call) {
            @Override
            public void close(Status status, Metadata trailers) {
                if (recorded.compareAndSet(false, true) && !panicked.get()) {
                    long end = handlerDoneNanos[0] > 0L ? handlerDoneNanos[0] : System.nanoTime();
                    long millis = Math.max(0L, end - startNanos) / 1_000_000L;
                    sink.observeDurationMillis(method, millis);
                    // ⚠️ Go: status.Code(err) —— nil ⇒ codes.OK(0)；非 status 错误 ⇒ codes.Unknown(2)。
                    //    本仓取「close 时的状态码」⇒ 与 Go 的「handler 返回的 error」在正常路径上同值。
                    sink.incrementCode(method, status.getCode().value());
                }
                super.close(status, trailers);
            }
        };

        ServerCall.Listener<ReqT> listener;
        try {
            listener = next.startCall(watched, headers);
        } catch (RuntimeException | Error e) {
            // ⚠️ Go 的 Prometheus 层：handler **在 startCall 这一步**就 panic 时，
            //    后置的两行 Observe/Inc 同样不会执行 ⇒ 也不记。
            panicked.set(true);
            throw e;
        }
        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT>(listener) {
            @Override
            public void onHalfClose() {
                try {
                    super.onHalfClose();
                } catch (RuntimeException | Error e) {
                    // ⚠️ Go 的 Prometheus 层在 handler panic 时**不记**（后置代码被跳过）⇒ 标记后原样抛出。
                    panicked.set(true);
                    throw e;
                }
                handlerDoneNanos[0] = System.nanoTime();
            }
        };
    }
}
