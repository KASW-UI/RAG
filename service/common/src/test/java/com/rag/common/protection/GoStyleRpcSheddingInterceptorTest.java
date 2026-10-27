package com.rag.common.protection;

import io.grpc.Metadata;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
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

        // ⚠️⚠️ **配方（2026-10-24 改）**：本用例原先用「开 50 → 关 50 → 再开」的涌现式配方，
        //    而 {@code avgFlying} 只在**在途数下降**时更新、且带 0.9 的滞后系数
        //    （adaptiveshedder.go:139-152）⇒ 全部开完再全部关完，结束时 avgFlying 只剩 ~0.26，
        //    而 cap 的下界是 {@code maxFlight() * 0.1 >= 0.1} ⇒ **margin 只有 2.6 倍**
        //    ⇒ 一旦机器负载让这批调用分散到不同时间桶（窗口滚动），cap 变大 ⇒ 降载不发生
        //    ⇒ **测试随机红**（实测在全量回归里真的红过一次）。
        // ⇒ 现在改成：开 100 → **只关 90**（关闭时在途数仍高 ⇒ avgFlying 被抬到 ~10）
        //    ⇒ margin 放大到 100 倍，不再时序敏感。
        java.util.List<io.grpc.ServerCall<Object, Object>> opened = new java.util.ArrayList<>();
        for (int i = 0; i < 100; i++) {
            opened.add(openAndCapture(interceptor));
        }
        for (int i = 0; i < 90; i++) {
            opened.get(i).close(Status.OK, new Metadata());
        }
        // 再来一个 ⇒ highThru 成立 ⇒ 应被降载
        FakeServerCall dropped = new FakeServerCall(METHOD);
        ServerCallHandler<Object, Object> next = new ServerCallHandler<>() {
            @Override
            public ServerCall.Listener<Object> startCall(ServerCall<Object, Object> c, Metadata h) {
                return new ServerCall.Listener<>() {
                };
            }
        };
        interceptor.interceptCall(dropped, new Metadata(), next);

        assertThat(dropped.closedStatus).as("CPU 恒过载 + 在途堆积后必须出现降载").isNotNull();
        assertThat(dropped.closedStatus.getCode())
                .as("Go: sheddinginterceptor.go:30 —— codes.ResourceExhausted")
                .isEqualTo(Status.Code.RESOURCE_EXHAUSTED);
        assertThat(dropped.closedStatus.getDescription())
                .as("Go: core/load/adaptiveshedder.go:34 ErrServiceOverloaded 的文案")
                .isEqualTo("service overloaded");
        assertThat(dropped.handlerRan)
                .as("Go: Allow() 失败时 handler 从未被调用").isFalse();
    }

    /**
     * 驱动一次**悬着**的调用，并返回**被拦截器包装过的**那个 call。
     *
     * <p>⚠️ 必须返回<b>包装过的</b> call：拦截器的 promise 结算挂在
     * {@code ForwardingServerCall#close} 上 ⇒ 关<b>原始</b> call 不会触发结算
     * ⇒ 在途数不会下降 ⇒ 造不出「交错关闭把 avgFlying 抬高」的效果。
     */
    private static io.grpc.ServerCall<Object, Object> openAndCapture(
            GoStyleRpcSheddingInterceptor interceptor) {
        FakeServerCall call = new FakeServerCall(METHOD);
        final io.grpc.ServerCall<Object, Object>[] wrapped = new io.grpc.ServerCall[1];
        ServerCallHandler<Object, Object> next = new ServerCallHandler<>() {
            @Override
            public ServerCall.Listener<Object> startCall(io.grpc.ServerCall<Object, Object> c, Metadata h) {
                wrapped[0] = c;
                return new ServerCall.Listener<>() {
                };
            }
        };
        interceptor.interceptCall(call, new Metadata(), next);
        return wrapped[0];
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

    @Test
    @DisplayName("🔴 RT 测量必须保留小数（Go: float64 毫秒 + ceil）⇒ helper 层不得整型截断")
    void rtMeasurementKeepsFractionalMilliseconds() {
        GoStyleRpcSheddingInterceptor interceptor =
                new GoStyleRpcSheddingInterceptor(shedder(1000), true, GoStyleSheddingStat.getInstance());

        double rt = interceptor.measuredRtMs(System.nanoTime() - 1_500_000L);

        // ⚠️ **只断言下界，不设上界**：机器有负载时，两次 nanoTime() 之间可能隔几百微秒
        //    ⇒ 实测过 2.47ms ⇒ ⇒ **任何上界都会在慢机上假红** ⇒ ⇒ 而**这正是本条要消灭的那类脆弱性**
        //    ⇒ ⇒ 判据只抓「有没有被截断」：截断 ⇒ 恰好 1.0（整型毫秒）⇒ 不截断 ⇒ ≥ 1.5。
        assertThat(rt).as("1.5ms 前开始的调用 ⇒ RT 必须 > 1.0（整型截断会得到恰好 1.0）")
                .isGreaterThan(1.0D);
    }

    @Test
    @DisplayName("🔴 调用点必须把【浮点毫秒】交给被调方（Go: ceil(1.5ms)=2）⇒ 整型截断 ⇒ 记 1 ⇒ 必红")
    void callSiteMustPassFractionalMillisecondsToShedder() {
        GoStyleAdaptiveShedder s = shedder(1000);
        // 假时钟：第 1 次取（startNanos）= 0，第 2 次取（测 RT）= 1_500_000ns ⇒ **RT 恰好 1.5ms**
        AtomicLong clock = new AtomicLong(0L);
        LongSupplier fakeClock = () -> clock.getAndIncrement() == 0 ? 0L : 1_500_000L;
        GoStyleRpcSheddingInterceptor interceptor =
                new GoStyleRpcSheddingInterceptor(s, true, GoStyleSheddingStat.getInstance(), fakeClock);

        drive(interceptor, Status.Code.OK);

        // Go: adaptiveshedder.go:285-287 ⇒ rt = 1.5 ⇒ math.Ceil(1.5) = 2 ⇒ rtCounter 记 **2**
        // ⚠️ **这一条才是守「调用点」的网**：上一条只守 helper ⇒ helper 绿、调用点照样能截断
        //    ⇒ ⇒ **两处都要有网**（与 tools/check-ledger-selfcheck.py 那条「两处契约不一致」同族）。
        //    ⇒ ⇒ 整型截断 ⇒ 被调方收到 1.0 ⇒ ceil(1.0)=1 ⇒ 记 **1** ⇒ ⇒ 本断言必红。
        assertThat(s.recordedRtSum()).as("1.5ms 的调用 ⇒ 被调方必须记 2（整型截断会记 1）")
                .isEqualTo(2L);
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
