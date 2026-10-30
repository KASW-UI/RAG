package com.rag.common.protection;

import com.rag.common.observability.GoStylePrometheusInterceptor;
import com.rag.common.observability.GoStylePrometheusMetrics;
import com.rag.common.observability.GoStylePrometheusSink;
import com.rag.common.observability.GoStyleStatInterceptor;
import com.rag.common.observability.GoStyleStatMetrics;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🔴 <b>Stat ＋ Prometheus 两层的链级守门</b>（Go 的 {@code zrpc/server.go:144-167} 注册序）。
 *
 * <h3>本守门锁住三件事</h3>
 * <ol>
 *   <li><b>位置</b>：两层都在 {@code breaker} / {@code shedding} 的<b>外层</b>
 *       ⇒ ⇒ 被降载 / 熔断拒绝的请求<b>也会被记一笔</b>（Go 侧同理）；
 *       ⇒ 若装到内侧 ⇒ 这两类请求在指标里<b>整个消失</b>（且不报错）。</li>
 *   <li><b>跨域名单恒等式</b>：{@code layerNames(chain)} 的输出必须<b>仍然是</b>
 *       {@code [recover, breaker, shedding]} ⇒ ⇒ 8 个<b>域级</b>守门
 *       （{@code *ProtectionChainWiringTest}）的断言<b>一条都不破</b>
 *       （那两个文件在域侧写权面 ⇒ 本层不能改 ⇒ 只能让观测层不进名单）。</li>
 *   <li><b>Go 的两条语义</b>：panic ⇒ Prometheus <b>不记</b>（Go 的直线代码被跳过），
 *       但 Stat <b>照记</b>（Go 用 defer）。</li>
 * </ol>
 */
@DisplayName("🔴 Stat/Prometheus 两层：链上位置 + 跨域名单恒等式 + Go 的 panic 语义")
class GoStyleRpcMetricsChainGateTest {

    private static final String METHOD =
            FakeServerCall.methodNameOf("message.MessageService", "MarkNotificationRead");

    @AfterEach
    void clearSink() {
        GoStylePrometheusMetrics.clearSink();
    }

    /** 记录型写入面（把 Go 的两次写入抓下来）。 */
    private static final class RecordingSink implements GoStylePrometheusSink {

        final List<String> durations = new ArrayList<>();
        final List<String> codes = new ArrayList<>();

        @Override
        public void observeDurationMillis(String method, long millis) {
            durations.add(method + "=" + millis);
        }

        @Override
        public void incrementCode(String method, int code) {
            codes.add(method + "=" + code);
        }
    }

    private static ServerInterceptor business() {
        return new ServerInterceptor() {
            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                    ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
                return next.startCall(call, headers);
            }
        };
    }

    /** 驱动一次正常完成的调用。 */
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

    /** 驱动一次**不结束**的调用（在途堆积用）。 */
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

    private static GoStyleAdaptiveShedder overloadedShedder() {
        GoStyleAdaptiveShedder s = new GoStyleAdaptiveShedder(
                900L, GoStyleProtectionProperties.CpuSource.SYSTEM);
        s.setCpuPermilleReader(() -> 1000);     // 恒过载
        return s;
    }

    // ==================================================================
    // ① 跨域名单恒等式（域侧 8 个守门靠它）
    // ==================================================================

    @Test
    @DisplayName("🔴 layerNames(chain) 必须仍是 [recover, breaker, shedding]（域级守门的恒等式不能破）")
    void protectionLayerNamesStayUnchangedForDomainGates() {
        GoStyleProtectionProperties props = new GoStyleProtectionProperties();

        ServerInterceptor chain = GoStyleRpcProtectionChain.build(business(), props);

        assertThat(GoStyleRpcProtectionChain.layerNames(chain))
                .as("🔴 **8 个域级守门**（task/like/message/article/comment/favorite/follow/admin 的 "
                        + "*ProtectionChainWiringTest）断言的正是这一条 ⇒ 若这里变成 5 项，"
                        + "那些守门会**整体变红**，而它们在**域侧写权面**（本层不能改）"
                        + "⇒ ⇒ 观测层必须不进名单。")
                .containsExactly("recover", "breaker", "shedding");
    }

    @Test
    @DisplayName("①' 关掉降载 ⇒ 名单仍是 [recover, breaker]（与域侧守门的第二条断言同款）")
    void protectionLayerNamesFollowTheSheddingSwitch() {
        GoStyleProtectionProperties props = new GoStyleProtectionProperties();
        props.getCpuShedding().setEnabled(false);

        ServerInterceptor chain = GoStyleRpcProtectionChain.build(business(), props);

        assertThat(GoStyleRpcProtectionChain.layerNames(chain))
                .containsExactly("recover", "breaker");
    }

    // ==================================================================
    // ② 全链层名（Go 的注册序）
    // ==================================================================

    @Test
    @DisplayName("② 全链层名必须是 [recover, stat, prometheus, breaker, shedding]（Go: zrpc/server.go:144-167）")
    void fullChainMatchesGoRegistrationOrder() {
        GoStyleProtectionProperties props = new GoStyleProtectionProperties();

        ServerInterceptor chain = GoStyleRpcProtectionChain.build(business(), props);

        assertThat(GoStyleRpcProtectionChain.allLayerNames(chain))
                .as("Go 的注册序（先注册的最外层）：Trace → Recover → **Stat** → **Prometheus** "
                        + "→ Breaker → Shedding → Timeout ⇒ 本仓对应 [recover, stat, prometheus, breaker, shedding]")
                .containsExactly("recover", "stat", "prometheus", "breaker", "shedding");
    }

    @Test
    @DisplayName("②' 关掉 stat / prometheus ⇒ 对应两层从链上消失（Go: Middlewares.Stat / Prometheus 可关）")
    void observationLayersAreSwitchable() {
        GoStyleProtectionProperties props = new GoStyleProtectionProperties();
        props.getStat().setEnabled(false);
        ServerInterceptor withoutStat = GoStyleRpcProtectionChain.build(business(), props);
        assertThat(GoStyleRpcProtectionChain.allLayerNames(withoutStat))
                .containsExactly("recover", "prometheus", "breaker", "shedding");

        GoStyleProtectionProperties props2 = new GoStyleProtectionProperties();
        props2.getPrometheus().setEnabled(false);
        ServerInterceptor withoutPrometheus = GoStyleRpcProtectionChain.build(business(), props2);
        assertThat(GoStyleRpcProtectionChain.allLayerNames(withoutPrometheus))
                .containsExactly("recover", "stat", "breaker", "shedding");

        // 保护层的名单**不受**观测层开关影响
        assertThat(GoStyleRpcProtectionChain.layerNames(withoutStat))
                .containsExactly("recover", "breaker", "shedding");
        assertThat(GoStyleRpcProtectionChain.layerNames(withoutPrometheus))
                .containsExactly("recover", "breaker", "shedding");
    }

    // ==================================================================
    // ③ Stat 层真的喂了聚合器
    // ==================================================================

    @Test
    @DisplayName("③ Stat 层必须真的把耗时喂给聚合器（否则每分钟那行报告恒为 qps 0）")
    void statLayerFeedsTheAggregator() {
        GoStyleStatMetrics metrics = new GoStyleStatMetrics("case-chain");
        ServerInterceptor chain = GoStyleRpcProtectionChain.statThen(
                new GoStyleStatInterceptor(metrics), business());

        FakeServerCall call = drive(chain);

        assertThat(call.closedStatus.getCode()).isEqualTo(Status.Code.OK);
        assertThat(metrics.pendingSampleCount())
                .as("一次成功调用 ⇒ 聚合器里必须有一条样本")
                .isEqualTo(1L);
    }

    // ==================================================================
    // ④ Prometheus 层真的写了（时长 + 状态码）
    // ==================================================================

    @Test
    @DisplayName("④ Prometheus 层必须写两条：耗时直方图 + 状态码计数（Go: prometheusinterceptor.go:39-40）")
    void prometheusLayerWritesDurationAndCode() {
        RecordingSink sink = new RecordingSink();
        GoStylePrometheusMetrics.setSink(sink);

        ServerInterceptor chain = GoStyleRpcProtectionChain.prometheusThen(business());

        drive(chain);

        assertThat(sink.durations).as("耗时直方图必须被观测一次").hasSize(1);
        assertThat(sink.durations.get(0)).startsWith(METHOD + "=");
        assertThat(sink.codes)
                .as("状态码计数必须是 Go 的数字码（OK = 0）")
                .containsExactly(METHOD + "=" + Status.Code.OK.value());
    }

    @Test
    @DisplayName("④' 没有 registry（无 sink）⇒ 整段跳过，既不崩也不写（Go: prometheus.Enabled()==false）")
    void withoutSinkTheLayerIsSkipped() {
        GoStylePrometheusMetrics.clearSink();

        ServerInterceptor chain = GoStyleRpcProtectionChain.prometheusThen(business());
        FakeServerCall call = drive(chain);

        assertThat(call.closedStatus.getCode())
                .as("没有写入面 ⇒ 拦截器必须**放行**，不能影响业务")
                .isEqualTo(Status.Code.OK);
        assertThat(call.handlerRan).isTrue();
    }

    // ==================================================================
    // ⑤ 位置：降载拒绝也要被记（code = 8）
    // ==================================================================

    @Test
    @DisplayName("🔴 位置：被降载拒绝的请求【也必须被 Prometheus 记一笔】（code=8 ⇒ 证明它在 shedding 外层）")
    void sheddingRejectionIsAlsoRecorded() {
        RecordingSink sink = new RecordingSink();
        GoStylePrometheusMetrics.setSink(sink);

        ServerInterceptor chain = GoStyleRpcProtectionChain.prometheusThen(
                GoStyleRpcProtectionChain.sheddingThen(business(), overloadedShedder()));

        // 配方同 GoStyleRpcProtectionChainTest#sheddingRejectionIsVisibleToTheOuterBreaker：
        // 先用悬着的调用把在途数堆上去，再用正常调用把 avgFlying 抬起来 ⇒ 才触发降载。
        int dropsBefore = GoStyleStatMetrics.getInstance().pendingDrops();
        for (int i = 0; i < 50; i++) {
            driveOpen(chain);
        }
        for (int i = 0; i < 50; i++) {
            drive(chain);
        }
        boolean sawOverload = false;
        for (int i = 0; i < 50 && !sawOverload; i++) {
            FakeServerCall c = driveOpen(chain);
            if (c.closedStatus != null && c.closedStatus.getCode() == Status.Code.RESOURCE_EXHAUSTED) {
                sawOverload = true;
            }
        }

        assertThat(sawOverload).as("前置条件：CPU 恒过载时必须出现降载").isTrue();
        assertThat(GoStyleStatMetrics.getInstance().pendingDrops())
                .as("Go: sheddinginterceptor.go:28 —— `metrics.AddDrop()` ⇒ 每分钟那行报告里的 "
                        + "**drops** 必须能被降载计数撑起来（否则 drops 恒为 0）")
                .isGreaterThan(dropsBefore);
        assertThat(sink.codes)
                .as("🔴 **降载拒绝必须出现在 Prometheus 的状态码计数里**"
                        + "（Go: shedding 层是「返回 error」⇒ Prometheus 层的后置代码照样执行）"
                        + "⇒ ⇒ 这条断言**同时**证明本层装在 shedding 的**外层**。"
                        + "若装到内侧 ⇒ 被降载的请求在指标里整个消失。")
                .contains(METHOD + "=" + Status.Code.RESOURCE_EXHAUSTED.value());
    }

    // ==================================================================
    // ⑥ Go 的 panic 语义：Prometheus 不记、Stat 照记
    // ==================================================================

    @Test
    @DisplayName("🔴 panic 时：Prometheus【不记】（Go 的直线代码被跳过），但 Stat【照记】（Go 用 defer）")
    void panicSemanticsMatchGo() {
        RecordingSink sink = new RecordingSink();
        GoStylePrometheusMetrics.setSink(sink);

        GoStyleStatMetrics metrics = new GoStyleStatMetrics("case-panic");

        ServerInterceptor panicking = new ServerInterceptor() {
            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                    ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
                throw new IllegalStateException("业务链上的 panic");
            }
        };

        ServerInterceptor chain = GoStyleRpcProtectionChain.recoverThen(
                GoStyleRpcProtectionChain.statThen(
                        new GoStyleStatInterceptor(metrics),
                        GoStyleRpcProtectionChain.prometheusThen(panicking)));

        FakeServerCall call = new FakeServerCall(METHOD);
        ServerCallHandler<Object, Object> next = new ServerCallHandler<>() {
            @Override
            public ServerCall.Listener<Object> startCall(ServerCall<Object, Object> c, Metadata h) {
                return new ServerCall.Listener<>() {
                };
            }
        };
        chain.interceptCall(call, new Metadata(), next);

        assertThat(call.closedStatus.getCode()).isEqualTo(Status.Code.INTERNAL);
        assertThat(sink.durations)
                .as("Go: prometheusinterceptor.go:38-40 —— handler panic ⇒ 后置两行不执行 ⇒ **不记**")
                .isEmpty();
        assertThat(sink.codes).isEmpty();
        assertThat(metrics.pendingSampleCount())
                .as("Go: statinterceptor.go:52-56 —— defer ⇒ panic 也**照记**一条样本")
                .isEqualTo(1L);
    }

    // ==================================================================
    // ⑦ panic 之后调用**仍被 close**（判据的活性用例）
    // ==================================================================

    @Test
    @DisplayName("🔴 panic 之后调用若仍被 close（例如超时线程随后关掉它）⇒ Prometheus【仍不得】补记")
    void panicThenLaterCloseStillRecordsNothing() {
        RecordingSink sink = new RecordingSink();
        GoStylePrometheusMetrics.setSink(sink);

        // 业务 interceptor：先抓住「Prometheus 层传下来的那个包装 call」，再 panic
        final ServerCall<Object, Object>[] captured = new ServerCall[1];
        ServerInterceptor panicking = new ServerInterceptor() {
            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                    ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
                captured[0] = (ServerCall<Object, Object>) call;
                throw new IllegalStateException("业务链上的 panic");
            }
        };

        ServerInterceptor chain = GoStyleRpcProtectionChain.prometheusThen(panicking);

        FakeServerCall call = new FakeServerCall(METHOD);
        ServerCallHandler<Object, Object> next = new ServerCallHandler<>() {
            @Override
            public ServerCall.Listener<Object> startCall(ServerCall<Object, Object> c, Metadata h) {
                return new ServerCall.Listener<>() {
                };
            }
        };
        try {
            chain.interceptCall(call, new Metadata(), next);
        } catch (IllegalStateException expected) {
            // Go: handler panic ⇒ Prometheus 层的后置两行**从未执行**
        }

        assertThat(captured[0]).as("必须抓到 Prometheus 层传下去的包装 call").isNotNull();
        captured[0].close(Status.INTERNAL, new Metadata());

        assertThat(sink.durations)
                .as("🔴 Go: prometheusinterceptor.go:38-40 —— handler panic ⇒ Observe/Inc **从未执行**"
                        + "⇒ ⇒ 即使事后这个调用又被 close（例如超时调度线程把它关掉），"
                        + "Prometheus 也**不得**补记一笔。")
                .isEmpty();
        assertThat(sink.codes).isEmpty();
    }

    // ==================================================================
    // ⑧ 业务 interceptor 仍被调到（插层不能把业务链掐断）
    // ==================================================================

    @Test
    @DisplayName("⑦ 插层后业务 interceptor 仍必须被调到（否则超时/trace 静默消失）")
    void businessInterceptorIsStillReached() {
        AtomicInteger businessCalls = new AtomicInteger();
        GoStyleProtectionProperties props = new GoStyleProtectionProperties();
        props.getCpuShedding().setEnabled(false);

        ServerInterceptor chain = GoStyleRpcProtectionChain.build(new ServerInterceptor() {
            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                    ServerCall<ReqT, RespT> call, Metadata headers,
                    ServerCallHandler<ReqT, RespT> next) {
                businessCalls.incrementAndGet();
                return next.startCall(call, headers);
            }
        }, props);

        FakeServerCall call = drive(chain);

        assertThat(businessCalls.get()).isEqualTo(1);
        assertThat(call.closedStatus.getCode()).isEqualTo(Status.Code.OK);
    }

    /** 让编译器看见 Prometheus 拦截器确实被链引用（防「类写了但没进链」）。 */
    @Test
    @DisplayName("⑧ Prometheus 拦截器类确实被链构造出来（防「类写了但没进链」）")
    void prometheusInterceptorIsActuallyWired() {
        GoStyleProtectionProperties props = new GoStyleProtectionProperties();
        ServerInterceptor chain = GoStyleRpcProtectionChain.build(business(), props);

        boolean found = false;
        io.grpc.ServerInterceptor current = chain;
        while (current instanceof GoStyleRpcProtectionChain.Layered layered) {
            if (layered.layerName().equals("prometheus")) {
                found = true;
                break;
            }
            current = layered.inner();
        }
        assertThat(found).isTrue();
        assertThat(new GoStylePrometheusInterceptor()).isNotNull();
    }
}
