package com.rag.common.protection;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link GoStyleSheddingStat} 对齐 Go {@code core/load/sheddingstat.go} +
 * {@code zrpc/internal/serverinterceptors/sheddinginterceptor.go} 的三个计数。
 *
 * <p><b>为什么这条守门必须存在</b>：DRIFT-MSG-V-025 落地前，
 * RAG 侧的降载<b>完全不可观测</b> —— 判据照抄了 Go，但 Go 侧
 * {@code IncrementTotal/IncrementPass/IncrementDrop} 三个计数<b>一个都没有</b>。
 * ⇒ 降载在不在工作、丢了多少，**从任何地方都看不出来</b>。
 *
 * <p><b>⚠️ 一条必须先纠正的认知</b>（本类的 javadoc 里也写了）：
 * Go 侧这三个计数<b>不在 Prometheus 里</b>，只在<b>每分钟一行的日志</b>里；
 * 而 Go 的熔断计数（{@code markDrop/markFailure}）写进 {@code b.stat} 滚动窗口，
 * 是<b>算法内部状态、根本没有对外暴露</b>。
 * ⇒ 本类只复刻 Go <b>确实有</b>的那部分，不发明 Go 没有的 Prometheus 指标。
 *
 * <p><b>⚠️ 为什么这里直接 stub 掉 {@code allow()}</b>：
 * 本类要验的是「三个计数<b>被记在了哪一步</b>」，<b>不是</b>「降载判据本身」——
 * 后者由 {@code GoStyleAdaptiveShedderTest}（10 例）与
 * {@code GoStyleRpcSheddingInterceptorTest}（4 例）单独覆盖。
 * ⇒ 直接 stub {@code allow()} 的返回值 ⇒ 本类<b>完全不依赖时序</b>、不会随机红。
 * （本类第一版用「堆在途 → 交错关闭 → 制造降载」的涌现式配方，
 *  而 {@code avgFlying} 只在在途数下降时更新且带 0.9 滞后系数 ⇒ margin 极薄
 *  ⇒ 实测在全量回归里真的随机红过一次。）
 */
@DisplayName("GoStyleSheddingStat：降载的三个计数与每分钟统计日志必须与 Go 一致")
class GoStyleSheddingStatTest {

    private static final String METHOD =
            FakeServerCall.methodNameOf("message.MessageService", "MarkNotificationRead");

    /**
     * 造一个 {@code allow()} 可控的 shedder。
     *
     * <p>⚠️ {@code GoStyleAdaptiveShedder} 是 {@code final class} ⇒ 靠 Mockito 5 的
     * <b>inline mock maker</b>（5.x 默认）才能 mock（实测 5.20.0 可用）。
     */
    private static GoStyleAdaptiveShedder shedderAllowing(boolean allow) {
        GoStyleAdaptiveShedder s = mock(GoStyleAdaptiveShedder.class);
        when(s.allow()).thenReturn(allow);
        return s;
    }

    /** 驱动一次调用并让它以 {@code closeCode} 结束（被允许的路径）。 */
    private static void drive(GoStyleRpcSheddingInterceptor interceptor, Status.Code closeCode) {
        FakeServerCall call = new FakeServerCall(METHOD);
        ServerCallHandler<Object, Object> next = new ServerCallHandler<>() {
            @Override
            public ServerCall.Listener<Object> startCall(ServerCall<Object, Object> c, Metadata h) {
                c.close(Status.fromCode(closeCode), new Metadata());
                return new ServerCall.Listener<>() {
                };
            }
        };
        interceptor.interceptCall(call, new Metadata(), next);
    }

    // ==================================================================
    // 1. 三个计数的位置
    // ==================================================================

    @Test
    @DisplayName("正常请求 ⇒ total+1 / pass+1 / drop=0")
    void normalRequestCountsTotalAndPass() {
        GoStyleSheddingStat stat = new GoStyleSheddingStat("test");
        GoStyleRpcSheddingInterceptor interceptor =
                new GoStyleRpcSheddingInterceptor(shedderAllowing(true), true, stat);

        drive(interceptor, Status.Code.OK);

        assertThat(stat.snapshot().total())
                .as("Go: sheddinginterceptor.go:25 IncrementTotal() 在 Allow 之前 ⇒ 每个请求都 +1")
                .isEqualTo(1L);
        assertThat(stat.snapshot().pass())
                .as("Go: sheddinginterceptor.go:37 IncrementPass() 与 promise.Pass() 同一支")
                .isEqualTo(1L);
        assertThat(stat.snapshot().drop()).isEqualTo(0L);
    }

    @Test
    @DisplayName("⚠️ ctx 超时 ⇒ total+1 / pass=0（Go 的 Fail 分支不 IncrementPass）")
    void deadlineExceededDoesNotCountPass() {
        GoStyleSheddingStat stat = new GoStyleSheddingStat("test");
        GoStyleRpcSheddingInterceptor interceptor =
                new GoStyleRpcSheddingInterceptor(shedderAllowing(true), true, stat);

        drive(interceptor, Status.Code.DEADLINE_EXCEEDED);

        assertThat(stat.snapshot().total()).isEqualTo(1L);
        assertThat(stat.snapshot().pass())
                .as("Go: sheddinginterceptor.go:35-37 —— ctx 超时走 promise.Fail() 那一支，**不** IncrementPass")
                .isEqualTo(0L);
        assertThat(stat.snapshot().drop()).isEqualTo(0L);
    }

    @Test
    @DisplayName("被降载丢弃 ⇒ total+1 / drop+1 / pass=0（且 handler 不执行）")
    void droppedRequestCountsDrop() {
        GoStyleSheddingStat stat = new GoStyleSheddingStat("test");
        GoStyleRpcSheddingInterceptor interceptor =
                new GoStyleRpcSheddingInterceptor(shedderAllowing(false), true, stat);

        assertThat(interceptor.stat())
                .as("拦截器持有的必须就是注入的那个 stat 实例（否则计数会记到别处 —— "
                        + "生产代码里 stat 是进程级单例，测试里必须能换成独立实例）")
                .isSameAs(stat);

        FakeServerCall dropped = new FakeServerCall(METHOD);
        ServerCallHandler<Object, Object> next = new ServerCallHandler<>() {
            @Override
            public ServerCall.Listener<Object> startCall(ServerCall<Object, Object> c, Metadata h) {
                return new ServerCall.Listener<>() {
                };
            }
        };
        interceptor.interceptCall(dropped, new Metadata(), next);

        assertThat(dropped.closedStatus)
                .as("Go: sheddinginterceptor.go:30 —— Allow 失败 ⇒ codes.ResourceExhausted")
                .isNotNull();
        assertThat(dropped.closedStatus.getCode())
                .isEqualTo(Status.Code.RESOURCE_EXHAUSTED);
        assertThat(dropped.handlerRan)
                .as("Go: Allow() 失败时 handler 从未被调用")
                .isFalse();

        assertThat(stat.snapshot().drop())
                .as("Go: sheddinginterceptor.go:28-29 —— Allow 失败 ⇒ metrics.AddDrop() + IncrementDrop()")
                .isEqualTo(1L);
        assertThat(stat.snapshot().total())
                .as("被丢的请求**也**先计了 total（Go: IncrementTotal 在 Allow 之前）")
                .isEqualTo(1L);
        assertThat(stat.snapshot().pass())
                .as("被丢的请求既不 Pass 也不 Fail（Go 的 defer 在 Allow 成功之后才注册）")
                .isEqualTo(0L);
    }

    @Test
    @DisplayName("降载关闭时**完全透传** ⇒ 三个计数一个都不增")
    void disabledSheddingIsTransparentAndSilent() {
        GoStyleSheddingStat stat = new GoStyleSheddingStat("test");
        GoStyleRpcSheddingInterceptor interceptor =
                new GoStyleRpcSheddingInterceptor(shedderAllowing(false), false, stat);

        drive(interceptor, Status.Code.OK);

        assertThat(stat.snapshot())
                .as("enabled=false ⇒ 连统计都不做（Go 侧对应「根本没装这个拦截器」）")
                .isEqualTo(new GoStyleSheddingStat.Snapshot(0L, 0L, 0L));
    }

    // ==================================================================
    // 2. 每分钟统计日志（Go: core/load/sheddingstat.go:52-70）
    // ==================================================================

    @Test
    @DisplayName("drop=0 时日志用 shedding_stat，drop>0 时用 shedding_stat_drop（Go 逐字一致）")
    void logTagDependsOnDropCount() {
        GoStyleSheddingStat stat = new GoStyleSheddingStat("rpc");

        stat.incrementTotal();
        stat.incrementPass();
        assertThat(stat.formatLine(123))
                .as("Go: sheddingstat.go:60-61 —— drop==0 ⇒ shedding_stat")
                .isEqualTo("(rpc) shedding_stat [1m], cpu: 123, total: 1, pass: 1, drop: 0");

        stat.incrementDrop();
        assertThat(stat.formatLine(123))
                .as("Go: sheddingstat.go:62-64 —— drop>0 ⇒ shedding_stat_drop")
                .isEqualTo("(rpc) shedding_stat_drop [1m], cpu: 123, total: 1, pass: 1, drop: 1");
    }

    @Test
    @DisplayName("上报后计数清零（Go: loop() 里 st := s.reset()）")
    void reportResetsCounters() {
        GoStyleSheddingStat stat = new GoStyleSheddingStat("rpc");
        stat.incrementTotal();
        stat.incrementPass();
        stat.incrementDrop();

        stat.reportOnce();

        assertThat(stat.snapshot())
                .as("Go: sheddingstat.go:53 —— st := s.reset() ⇒ 打日志的那份快照来自 reset，"
                        + "且计数同时被清零")
                .isEqualTo(new GoStyleSheddingStat.Snapshot(0L, 0L, 0L));
    }
}
