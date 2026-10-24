package com.rag.common.protection;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link GoStyleRpcProtectionChain} 的<b>链序</b>守门。
 *
 * <p>链序错了不会编译失败、不会启动失败，只会让行为悄悄变掉 ⇒ 必须有可断言的用例。
 */
@DisplayName("GoStyleRpcProtectionChain：recover → breaker → shedding → business 的顺序必须成立")
class GoStyleRpcProtectionChainTest {

    private static final String METHOD =
            FakeServerCall.methodNameOf("message.MessageService", "MarkNotificationRead");

    private static GoStyleAdaptiveShedder overloadedShedder() {
        GoStyleAdaptiveShedder s = new GoStyleAdaptiveShedder(
                900L, GoStyleProtectionProperties.CpuSource.SYSTEM);
        s.setCpuPermilleReader(() -> 1000);     // 恒过载
        return s;
    }

    /** 业务拦截器：记调用次数，并让 handler 正常结束。 */
    private static ServerInterceptor business(AtomicInteger calls) {
        return new ServerInterceptor() {
            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                    ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
                return next.startCall(call, headers);
            }
        };
    }

    private static FakeServerCall drive(ServerInterceptor chain) {
        FakeServerCall call = new FakeServerCall(METHOD);
        ServerCallHandler<Object, Object> next = new ServerCallHandler<>() {
            @Override
            public ServerCall.Listener<Object> startCall(ServerCall<Object, Object> c, Metadata h) {
                call.handlerRan = true;
                c.close(Status.OK, new Metadata());
                return new ServerCall.Listener<>() {
                };
            }
        };
        chain.interceptCall(call, new Metadata(), next);
        return call;
    }

    /** 驱动一次**不结束**的调用（该请求一直算「在途」）⇒ 在途数堆积。 */
    private static FakeServerCall driveOpen(ServerInterceptor chain) {
        FakeServerCall call = new FakeServerCall(METHOD);
        ServerCallHandler<Object, Object> next = new ServerCallHandler<>() {
            @Override
            public ServerCall.Listener<Object> startCall(ServerCall<Object, Object> c, Metadata h) {
                call.handlerRan = true;
                return new ServerCall.Listener<>() {
                };
            }
        };
        chain.interceptCall(call, new Metadata(), next);
        return call;
    }

    // ==================================================================
    // 1. 顺序：shedding 必须在内、breaker 在外
    // ==================================================================

    /**
     * 🔴 <b>本用例是「链序」唯一能被抓到的形态</b>。
     *
     * <p>Go 侧的链序是 {@code Breaker → Shedding}（breaker 在外），
     * 于是 shedding 的 {@code RESOURCE_EXHAUSTED} 会<b>冒泡到 breaker</b>，
     * 而 {@code RESOURCE_EXHAUSTED} 在 Go 的 6 码失败名单里
     * ⇒ <b>持续降载最终会把熔断器打开</b>。
     *
     * <p>若链序写反（shedding 在外），shedding 的拒绝会被 breaker 挡在中间看不见
     * ⇒ breaker 永远不会因降载而打开 ⇒ <b>与 Go 行为不同，且不会有任何报错</b>。
     */
    @Test
    @DisplayName("🔴 链序：shedding 的降载拒绝必须能被外层 breaker 看到（否则熔断永远开不了）")
    void sheddingRejectionIsVisibleToTheOuterBreaker() {
        GoStyleRpcBreakerInterceptor breaker =
                new GoStyleRpcBreakerInterceptor(true, n -> new GoStyleBreaker(n, () -> 0.0D));
        GoStyleRpcSheddingInterceptor shedding =
                new GoStyleRpcSheddingInterceptor(overloadedShedder(), true);

        ServerInterceptor chain = GoStyleRpcProtectionChain.breakerThen(
                breaker, GoStyleRpcProtectionChain.sheddingThen(shedding, business(new AtomicInteger())));

        // 反复驱动：CPU 恒过载 ⇒ 在途堆积 ⇒ shedding 拒绝 ⇒ breaker 记失败
        // （配方同 GoStyleAdaptiveShedderTest#overloadedAndHighInFlightSheds）
        for (int i = 0; i < 50; i++) {
            driveOpen(chain);                       // ① 悬着的调用把在途数堆上去
        }
        for (int i = 0; i < 50; i++) {
            drive(chain);                           // ② 正常完成 ⇒ avgFlying 抬升
        }
        boolean sawOverload = false;
        for (int i = 0; i < 50 && !sawOverload; i++) {
            FakeServerCall c = driveOpen(chain);
            if (c.closedStatus != null && c.closedStatus.getCode() == Status.Code.RESOURCE_EXHAUSTED) {
                sawOverload = true;
            }
        }

        assertThat(sawOverload).as("前置条件：CPU 恒过载时必须出现降载").isTrue();

        // ⚠️ 必须跨过一个桶边界（250ms）—— Go 的 history() **忽略当前未完成桶**
        //    （collection/rollingwindow.go:70-72「ignore the current bucket, because of partial data」）
        //    ⇒ 不跨桶的话，刚发生的失败根本不在窗口里。
        sleepOneBucket();

        assertThat(breaker.breakerFor(METHOD).historyTotal())
                .as("shedding 的 RESOURCE_EXHAUSTED 必须被外层 breaker 记成失败（链序 Breaker → Shedding）")
                .isGreaterThan(0L);
        assertThat(breaker.breakerFor(METHOD).accept())
                .as("失败已被记入 ⇒ 熔断应已打开")
                .isFalse();
    }

    private static void sleepOneBucket() {
        try {
            Thread.sleep(260L);     // Go 的桶宽 250ms（googlebreaker.go:12-21）
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted", e);
        }
    }

    // ==================================================================
    // 2. recover 在最外层
    // ==================================================================

    /**
     * 🔴 recover 必须在<b>最外层</b>：否则业务链上的 panic 兜不住。
     *
     * <p>Go 侧的注册序（{@code zrpc/internal/server.go:58} 的 append ⇒ 先注册的最外层）
     * 把 Recover 排在 Breaker 之前 ⇒ recover 在最外层。
     */
    @Test
    @DisplayName("🔴 链序：recover 在最外层 ⇒ 业务 interceptor 自己抛的 panic 也被兜住")
    void recoverIsOutermostSoItCatchesInnerPanics() {
        ServerInterceptor panicking = new ServerInterceptor() {
            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                    ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
                throw new IllegalStateException("业务链上的 panic");
            }
        };

        ServerInterceptor chain = GoStyleRpcProtectionChain.recoverThen(panicking);

        FakeServerCall call = new FakeServerCall(METHOD);
        ServerCallHandler<Object, Object> next = new ServerCallHandler<>() {
            @Override
            public ServerCall.Listener<Object> startCall(ServerCall<Object, Object> c, Metadata h) {
                return new ServerCall.Listener<>() {
                };
            }
        };
        chain.interceptCall(call, new Metadata(), next);

        assertThat(call.closedStatus).as("panic 必须被 recover 兜住并关闭调用").isNotNull();
        assertThat(call.closedStatus.getCode()).isEqualTo(Status.Code.INTERNAL);
        assertThat(call.closedStatus.getDescription()).startsWith("panic: ");
    }

    // ==================================================================
    // 3. build() 的默认链确实包含业务 interceptor
    // ==================================================================

    @Test
    @DisplayName("build() 产出的链会真的调用到业务 interceptor（否则超时/trace 静默消失）")
    void buildChainReachesBusinessInterceptor() {
        GoStyleProtectionProperties props = new GoStyleProtectionProperties();
        props.getCpuShedding().setEnabled(false);   // 降载会短路，先关掉保证确定性

        AtomicInteger businessCalls = new AtomicInteger();
        ServerInterceptor chain = GoStyleRpcProtectionChain.build(
                new ServerInterceptor() {
                    @Override
                    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                            ServerCall<ReqT, RespT> call, Metadata headers,
                            ServerCallHandler<ReqT, RespT> next) {
                        businessCalls.incrementAndGet();
                        return next.startCall(call, headers);
                    }
                },
                props);

        FakeServerCall call = drive(chain);

        assertThat(businessCalls.get()).as("业务 interceptor 必须被调用到").isEqualTo(1);
        assertThat(call.closedStatus.getCode()).isEqualTo(Status.Code.OK);
    }
}
