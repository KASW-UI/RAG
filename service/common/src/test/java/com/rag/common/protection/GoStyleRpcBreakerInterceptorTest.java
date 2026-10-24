package com.rag.common.protection;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link GoStyleRpcBreakerInterceptor} 对齐 Go
 * {@code zrpc/internal/serverinterceptors/breakerinterceptor.go}。
 *
 * <p>⚠️ <b>本类验的是「判据被喂对了」，不是「熔断器跳闸了」</b>：
 * 跳闸点的随机性由 {@link GoStyleBreakerTest} 覆盖（那边直接操作内核）；
 * 本类要证明的是<b>拦截器把正确的信号喂给了内核</b> ——
 * 业务错误不喂失败、5xx 喂失败、拒绝时返回 Go 的那套 wire 契约。
 */
@DisplayName("GoStyleRpcBreakerInterceptor：熔断判据与 wire 契约必须与 Go 一致")
class GoStyleRpcBreakerInterceptorTest {

    private static final String METHOD =
            FakeServerCall.methodNameOf("message.MessageService", "MarkNotificationRead");

    /** 固定随机源为 0.0 ⇒ dropRatio>0 时**必拒绝**（与 GoStyleBreakerTest 同款配方）。 */
    private static GoStyleRpcBreakerInterceptor interceptorWithFixedRandom() {
        return new GoStyleRpcBreakerInterceptor(true, name -> new GoStyleBreaker(name, () -> 0.0D));
    }

    /**
     * 驱动一次调用：handler「跑」了，并以 {@code closeCode} 结束这次调用。
     *
     * <p>⚠️ 这模拟的是 grpc-java 的真实路径：服务实现 → {@code observer.onError/onCompleted}
     * → grpc 内部 {@code call.close(status, trailers)}。
     */
    private static FakeServerCall drive(GoStyleRpcBreakerInterceptor interceptor, Status.Code closeCode) {
        FakeServerCall call = new FakeServerCall(METHOD);
        ServerCallHandler<Object, Object> next = new ServerCallHandler<>() {
            @Override
            public ServerCall.Listener<Object> startCall(ServerCall<Object, Object> c, Metadata h) {
                call.handlerRan = true;
                c.close(Status.fromCode(closeCode), new Metadata());
                return new ServerCall.Listener<>() {
                };
            }
        };
        interceptor.interceptCall(call, new Metadata(), next);
        return call;
    }

    // ==================================================================
    // 1. wire 契约
    // ==================================================================

    @Test
    @DisplayName("熔断打开时返回 UNAVAILABLE + \"circuit breaker is open\"，且 handler 不执行")
    void rejectionUsesGoWireContract() {
        GoStyleRpcBreakerInterceptor interceptor = interceptorWithFixedRandom();

        // 先灌一批成功 + 失败，把窗口压到会拒绝的状态（配方同 GoStyleBreakerTest）
        for (int i = 0; i < 50; i++) {
            drive(interceptor, Status.Code.OK);
        }
        sleepOneBucket();
        for (int i = 0; i < 50; i++) {
            drive(interceptor, Status.Code.INTERNAL);
        }
        sleepOneBucket();

        // 现在应当开始拒绝
        FakeServerCall rejected = null;
        for (int i = 0; i < 20 && rejected == null; i++) {
            FakeServerCall c = drive(interceptor, Status.Code.OK);
            if (c.closedStatus != null && c.closedStatus.getCode() == Status.Code.UNAVAILABLE) {
                rejected = c;
            }
            sleepOneBucket();
        }

        assertThat(rejected).as("连续失败后必须出现拒绝").isNotNull();
        assertThat(rejected.closedStatus.getDescription())
                .as("Go: core/breaker/breaker.go:19 ErrServiceUnavailable 的文案")
                .isEqualTo("circuit breaker is open");
        assertThat(rejected.handlerRan)
                .as("Go: accept() 失败时 handler 从未被调用").isFalse();
    }

    @Test
    @DisplayName("隔离 key 是 gRPC 全方法名（Go: info.FullMethod），不是 METHOD://path")
    void isolationKeyIsFullMethodName() {
        GoStyleRpcBreakerInterceptor interceptor = interceptorWithFixedRandom();
        drive(interceptor, Status.Code.OK);

        assertThat(interceptor.breakerFor(METHOD)).as("必须按 FullMethod 建 breaker").isNotNull();
        assertThat(interceptor.breakerFor("/message.MessageService/Other")).isNull();
    }

    // ==================================================================
    // 2. 判据：什么算失败
    // ==================================================================

    @Test
    @DisplayName("🔴 业务错误（NOT_FOUND / INVALID_ARGUMENT / PERMISSION_DENIED）不计入熔断失败")
    void businessErrorsDoNotCountAsFailures() {
        GoStyleRpcBreakerInterceptor interceptor = interceptorWithFixedRandom();

        for (int i = 0; i < 50; i++) {
            drive(interceptor, Status.Code.OK);
        }
        sleepOneBucket();
        // 大量业务错误
        for (int i = 0; i < 50; i++) {
            drive(interceptor, Status.Code.NOT_FOUND);
        }
        sleepOneBucket();

        assertThat(interceptor.breakerFor(METHOD).accept())
                .as("业务错误全都不在 Go 的 6 码失败名单里 ⇒ 熔断绝不能开")
                .isTrue();
    }

    @Test
    @DisplayName("🔴 5xx 类（INTERNAL / UNAVAILABLE / RESOURCE_EXHAUSTED）计入熔断失败 ⇒ 熔断会开")
    void serverErrorsDoCountAsFailures() {
        GoStyleRpcBreakerInterceptor interceptor = interceptorWithFixedRandom();

        for (int i = 0; i < 50; i++) {
            drive(interceptor, Status.Code.OK);
        }
        sleepOneBucket();
        for (int i = 0; i < 50; i++) {
            drive(interceptor, Status.Code.INTERNAL);
        }
        sleepOneBucket();

        assertThat(interceptor.breakerFor(METHOD).accept())
                .as("INTERNAL 在 Go 的 6 码失败名单里 ⇒ 必须能触发熔断")
                .isFalse();
    }

    @Test
    @DisplayName("enabled=false 时完全透传（等价 Go 的 Middlewares.Breaker=false）")
    void disabledBreakerIsTransparent() {
        GoStyleRpcBreakerInterceptor interceptor = new GoStyleRpcBreakerInterceptor(false);

        FakeServerCall call = drive(interceptor, Status.Code.INTERNAL);

        assertThat(call.handlerRan).isTrue();
        assertThat(call.closedStatus.getCode()).isEqualTo(Status.Code.INTERNAL);
        assertThat(interceptor.breakerFor(METHOD)).as("关闭时不该建任何 breaker").isNull();
    }

    private static void sleepOneBucket() {
        try {
            Thread.sleep(260L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted", e);
        }
    }
}
