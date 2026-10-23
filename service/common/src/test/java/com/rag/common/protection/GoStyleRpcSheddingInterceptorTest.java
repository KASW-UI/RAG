package com.rag.common.protection;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link GoStyleRpcSheddingInterceptor} 对齐 Go
 * {@code zrpc/internal/serverinterceptors/sheddinginterceptor.go}。
 */
@DisplayName("GoStyleRpcSheddingInterceptor：降载判据与 wire 契约必须与 Go 一致")
class GoStyleRpcSheddingInterceptorTest {

    private static final String METHOD =
            FakeServerCall.methodNameOf("message.MessageService", "MarkNotificationRead");

    private static GoStyleAdaptiveShedder shedder(int cpuPermille) {
        GoStyleAdaptiveShedder s = new GoStyleAdaptiveShedder(
                900L, GoStyleProtectionProperties.CpuSource.SYSTEM);
        s.setCpuPermilleReader(() -> cpuPermille);
        return s;
    }

    /** 驱动一次调用：handler「跑」了，并以 {@code closeCode} 结束。 */
    private static FakeServerCall drive(GoStyleRpcSheddingInterceptor interceptor, Status.Code closeCode) {
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

    /**
     * 驱动一次调用：handler「跑」了，但<b>不结束这次调用</b> ⇒ 该请求一直算「在途」。
     *
     * <p>⚠️ Go 的 {@code shouldDrop()} 是<b>两个条件同时成立</b>才丢：
     * （CPU 过载 <b>或</b> 仍在冷却期）<b>且</b> 在途数超过动态上限
     * （{@code highThru()}，上限由 QPS × RT 算出）。
     * ⇒ 只把 CPU 打满而请求都立刻完成 ⇒ 在途数归零 ⇒ <b>不会</b>降载
     * （这正是 Go 相对「简化移植」的严谨之处）。
     */
    private static FakeServerCall driveOpen(GoStyleRpcSheddingInterceptor interceptor) {
        FakeServerCall call = new FakeServerCall(METHOD);
        ServerCallHandler<Object, Object> next = new ServerCallHandler<>() {
            @Override
            public ServerCall.Listener<Object> startCall(ServerCall<Object, Object> c, Metadata h) {
                call.handlerRan = true;
                return new ServerCall.Listener<>() {
                    // 不 close ⇒ 调用悬着 ⇒ 在途数持续堆积
                };
            }
        };
        interceptor.interceptCall(call, new Metadata(), next);
        return call;
    }

    @Test
    @DisplayName("降载时返回 RESOURCE_EXHAUSTED + \"service overloaded\"，且 handler 不执行")
    void overloadUsesGoWireContract() {
        GoStyleAdaptiveShedder s = shedder(1000);       // 恒过载
        GoStyleRpcSheddingInterceptor interceptor = new GoStyleRpcSheddingInterceptor(s, true);

        // 配方同 GoStyleAdaptiveShedderTest#overloadedAndHighInFlightSheds：
        //   ① 一批悬着的调用把在途数堆上去
        for (int i = 0; i < 50; i++) {
            driveOpen(interceptor);
        }
        //   ② 一批正常完成的调用把 avgFlying 抬上去
        for (int i = 0; i < 50; i++) {
            drive(interceptor, Status.Code.OK);
        }
        //   ③ 继续驱动，直到出现降载
        FakeServerCall dropped = null;
        for (int i = 0; i < 50 && dropped == null; i++) {
            FakeServerCall c = driveOpen(interceptor);
            if (c.closedStatus != null && c.closedStatus.getCode() == Status.Code.RESOURCE_EXHAUSTED) {
                dropped = c;
            }
        }

        assertThat(dropped).as("CPU 恒过载 + 在途堆积后必须出现降载").isNotNull();
        assertThat(dropped.closedStatus.getDescription())
                .as("Go: core/load/adaptiveshedder.go:34 ErrServiceOverloaded 的文案")
                .isEqualTo("service overloaded");
        assertThat(dropped.handlerRan)
                .as("Go: Allow() 失败时 handler 从未被调用").isFalse();
    }

    // ==================================================================
    // 2. promise 结算：Fail vs Pass
    // ==================================================================

    @Test
    @DisplayName("DEADLINE_EXCEEDED ⇒ promise.Fail()（不写 RT 窗口）")
    void deadlineExceededFailsThePromise() {
        GoStyleAdaptiveShedder s = shedder(0);          // 恒不过载 ⇒ 不会降载
        GoStyleRpcSheddingInterceptor interceptor = new GoStyleRpcSheddingInterceptor(s, true);

        drive(interceptor, Status.Code.DEADLINE_EXCEEDED);
        sleepOneBucket();

        assertThat(s.minRt())
                .as("Go: promise.Fail() 不记 RT（sheddinginterceptor.go:38-42）⇒ minRt 保持缺省 1000")
                .isEqualTo(1000.0D);
    }

    @Test
    @DisplayName("正常结束 ⇒ promise.Pass()（写 RT 窗口）")
    void normalCompletionPassesThePromise() {
        GoStyleAdaptiveShedder s = shedder(0);
        GoStyleRpcSheddingInterceptor interceptor = new GoStyleRpcSheddingInterceptor(s, true);

        drive(interceptor, Status.Code.OK);
        sleepOneBucket();

        assertThat(s.minRt())
                .as("Go: promise.Pass() 会记 RT（promise.Pass 内部 timex.Since(start)）")
                .isLessThan(1000.0D);
    }

    @Test
    @DisplayName("🔴 enabled=false 时完全透传")
    void disabledSheddingIsTransparent() {
        GoStyleAdaptiveShedder s = shedder(1000);
        GoStyleRpcSheddingInterceptor interceptor = new GoStyleRpcSheddingInterceptor(s, false);

        FakeServerCall call = drive(interceptor, Status.Code.OK);

        assertThat(call.handlerRan).isTrue();
        assertThat(call.closedStatus.getCode()).isEqualTo(Status.Code.OK);
    }

    private static void sleepOneBucket() {
        try {
            Thread.sleep(150L);     // shedder 的桶宽 100ms（adaptiveshedder.go:18-30）
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted", e);
        }
    }
}
