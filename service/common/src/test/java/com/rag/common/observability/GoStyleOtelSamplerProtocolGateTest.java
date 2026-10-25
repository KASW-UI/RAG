package com.rag.common.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 🔴🔴 **T-11 残留守门**：采样比例（{@code Telemetry.Sampler}）与 batcher（{@code Telemetry.Batcher}）。
 *
 * <h2>为什么单独一个类</h2>
 * <p>{@link GoStyleOtelAgentGateTest} 证的��「全局 provider 装上了 / span 真导出 / 关停会 flush」，
 * <b>都是 {@code Sampler = 1.0} 这一个取值</b> ⇒ 采样器与 batcher 的其它分支<b>一条都没被验证过</b>。
 * ⇒ 本类补上这两条分支。
 *
 * <h2>Go 出处（逐条）</h2>
 * <pre>
 *   go-zero@v1.10.2/core/trace/config.go:9-12
 *       Sampler float64 `json:",default=1.0"`
 *       Batcher string  `json:",default=otlpgrpc,options=zipkin|otlpgrpc|otlphttp|file"`
 *
 *   go-zero@v1.10.2/core/trace/agent.go:111
 *       sdktrace.WithSampler(sdktrace.ParentBased(sdktrace.TraceIDRatioBased(c.Sampler)))
 *
 *   go-zero@v1.10.2/core/trace/agent.go:100-101（createExporter 的 default 分支）
 *       default:
 *           return nil, fmt.Errorf("unknown exporter: %s", c.Batcher)
 *
 *   go-zero@v1.10.2/core/trace/agent.go:114-121
 *       if len(c.Endpoint) > 0 { exp, err := createExporter(c); if err != nil { logx.Error(err); return err } ... }
 *
 *   go-zero@v1.10.2/core/trace/agent.go:43-52（StartAgent 的 once.Do）
 *       tp, err := startAgent(c); if err != nil { logx.Error(err); return }   ← 继续启动
 * </pre>
 *
 * <h2>🔴 一条被自己抓出来的错账</h2>
 * <p>第二十一轮台账写的是「非 grpc 的 {@code protocol} ⇒ <b>抛错</b>（不静默降级）」——
 * <b>代码里当时并没有这层校验</b>（{@code buildProvider} 压根没读 {@code config.protocol()}）
 * ⇒ 非法值被<b>静默当成 grpc</b> ⇒ 那是<b>真缺陷</b>，不是文档问题。
 * ⇒ 本类先用「④」把它钉成守门，再由第二十三轮的生产改动真正补上校验。
 */
class GoStyleOtelSamplerProtocolGateTest {

    @BeforeEach
    void resetGlobal() {
        GlobalOpenTelemetry.resetForTest();
        GoStyleOtelAgent.shutdown();
    }

    @AfterEach
    void cleanupGlobal() {
        GoStyleOtelAgent.shutdown();
        GlobalOpenTelemetry.resetForTest();
    }

    // ====================================================================================
    // 采样比例（Go: Telemetry.Sampler）
    // ====================================================================================

    @Test
    @DisplayName("🔴 Sampler=0.0 ⇒ **根 span 不采样 ⇒ 一个 span 都不导出**（Go: TraceIDRatioBased(0.0)）")
    void ratioZeroMeansNothingIsExported() throws Exception {
        try (FakeOtlpCollector collector = new FakeOtlpCollector()) {
            var provider = GoStyleOtelAgent.buildProvider(
                    new GoStyleOtelConfig("common-otel-sampler-gate", collector.endpoint(), "grpc", 0.0d));
            try {
                Tracer tracer = provider.get("gate");
                Span span = tracer.spanBuilder("root-span").startSpan();

                assertThat(span.isRecording())
                        .as("Sampler=0.0 ⇒ 根 span 必须**不 recording**"
                                + "（Go: sdktrace.TraceIDRatioBased(0.0) ⇒ DROP）")
                        .isFalse();
                assertThat(span.getSpanContext().isSampled())
                        .as("被 DROP 的 span：**SpanContext 仍有效**（有 trace-id/span-id），"
                                + "但 sampled 标志为 false（trace flags = 00）"
                                + " —— Go 的 OTel SDK 同款（Go: Drop 决策仍给有效 SpanContext + NotSampled）"
                                + " ⇒ 所以响应头里**仍会有** traceparent，只是 flags=00")
                        .isFalse();
                span.end();
            } finally {
                // 关停 ⇒ flush ⇒ 若有 pending span 必被送出（这是「确实没导出」的强证据）
                provider.close();
            }
            assertThat(collector.requestCount())
                    .as("Sampler=0.0 ⇒ **一个 span 都不该出机器**（Go: 采样器把根 span 丢了）")
                    .isEqualTo(0);
        }
    }

    @Test
    @DisplayName("🔴 ParentBased：入向 parent 已采样 ⇒ **即使 Sampler=0.0，子 span 仍被采样并导出**"
            + "（Go: ParentBased(TraceIDRatioBased(0.0))）")
    void parentBasedKeepsSampledParentEvenWhenRatioIsZero() throws Exception {
        try (FakeOtlpCollector collector = new FakeOtlpCollector()) {
            var provider = GoStyleOtelAgent.buildProvider(
                    new GoStyleOtelConfig("common-otel-sampler-gate", collector.endpoint(), "grpc", 0.0d));
            try {
                // 入向 traceparent：flags = 01 ⇒ **已采样**
                Map<String, String> incoming = new HashMap<>();
                incoming.put("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");

                Context parent = io.opentelemetry.context.propagation.TextMapPropagator
                        .composite(io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator.getInstance())
                        .extract(Context.root(), incoming, MapGetter.INSTANCE);

                Tracer tracer = provider.get("gate");
                Span span = tracer.spanBuilder("child-span").setParent(parent).startSpan();

                assertThat(span.isRecording())
                        .as("ParentBased ⇒ 入向 parent 已采样 ⇒ 子 span **必须**被采样"
                                + "（Go: sdktrace.ParentBased(...)）")
                        .isTrue();
                span.end();
            } finally {
                provider.close();
            }
            assertThat(collector.requestCount())
                    .as("被采样的子 span 必须真的导出（⇒ ParentBased 这层包装真的生效）")
                    .isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    @DisplayName("🔴 sampler 名字 → 比例 的映射：always_on⇒1.0 / always_off⇒0.0 / traceidratio⇒arg / 非法名⇒炸")
    void samplerNameMapsToGoRatio() {
        assertThat(GoStyleOtelConfig.toRatio("always_on", null)).isEqualTo(1.0d);
        assertThat(GoStyleOtelConfig.toRatio("parentbased_always_on", null)).isEqualTo(1.0d);
        assertThat(GoStyleOtelConfig.toRatio("always_off", null)).isEqualTo(0.0d);
        assertThat(GoStyleOtelConfig.toRatio("parentbased_always_off", null)).isEqualTo(0.0d);
        assertThat(GoStyleOtelConfig.toRatio("traceidratio", 0.25d)).isEqualTo(0.25d);
        assertThat(GoStyleOtelConfig.toRatio("parentbased_traceidratio", 0.5d)).isEqualTo(0.5d);
        assertThatThrownBy(() -> GoStyleOtelConfig.toRatio("never_heard_of_it", null))
                .as("非法采样器名必须炸（不能静默降级成 always_on）")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("never_heard_of_it");
    }

    // ====================================================================================
    // batcher（Go: Telemetry.Batcher）
    // ====================================================================================

    @Test
    @DisplayName("🔴 非法 batcher + 端点非空 ⇒ 装不上（Go: unknown exporter ⇒ 整个 agent 装不上）")
    void unknownBatcherMustFailInsteadOfSilentlyUsingGrpc() throws Exception {
        try (FakeOtlpCollector collector = new FakeOtlpCollector()) {
            assertThatThrownBy(() -> GoStyleOtelAgent.buildProvider(
                    new GoStyleOtelConfig("common-otel-protocol-gate", collector.endpoint(), "not-a-batcher", 1.0d)))
                    .as("非法 batcher 必须抛错（Go: core/trace/agent.go:100-101 unknown exporter）"
                            + "—— 静默按 grpc 处理就是**静默降级**")
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("unknown exporter")
                    .hasMessageContaining("not-a-batcher");
        }
    }

    @Test
    @DisplayName("🔴 Go 支持但本仓未实现的 batcher（写成 Go 的名字 otlphttp）⇒ 同样装不上（不许静默降级成 grpc）")
    void goSupportedButUnimplementedBatcherMustFail() throws Exception {
        try (FakeOtlpCollector collector = new FakeOtlpCollector()) {
            assertThatThrownBy(() -> GoStyleOtelAgent.buildProvider(
                    new GoStyleOtelConfig("common-otel-protocol-gate", collector.endpoint(), "otlphttp", 1.0d)))
                    .as("Go 的 Batcher 合法集合含 otlphttp，但本仓**只实现了 grpc**（= Go 的 otlpgrpc）"
                            + " ⇒ 不能静默按 grpc 处理")
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("unknown exporter")
                    .hasMessageContaining("otlphttp")
                    .hasMessageContaining("未实现");
        }
    }

    @Test
    @DisplayName("🔴 端点为空 ⇒ **根本不校验** batcher（Go: createExporter 只在 len(Endpoint)>0 时被调用）")
    void emptyEndpointSkipsBatcherValidation() {
        // Go 出处：core/trace/agent.go:114-121 —— 端点为空 ⇒ 不建 exporter ⇒ 不走 createExporter
        //          ⇒ Batcher 写什么都不报错。
        var provider = GoStyleOtelAgent.buildProvider(
                new GoStyleOtelConfig("common-otel-protocol-gate", null, "not-a-batcher", 1.0d));
        assertThat(provider)
                .as("端点为空时必须能装出 provider（Go 同款：只少了 exporter）")
                .isNotNull();
        provider.close();
    }

    @Test
    @DisplayName("🔴 装不上**不阻断启动**：install 返回 null + 全局仍是 no-op（Go: logx.Error 后继续启动）")
    void installFailureDoesNotBreakStartup() throws Exception {
        try (FakeOtlpCollector collector = new FakeOtlpCollector()) {
            io.opentelemetry.api.OpenTelemetry installed;
            try {
                installed = GoStyleOtelAgent.install(
                        new GoStyleOtelConfig("common-otel-protocol-gate", collector.endpoint(), "not-a-batcher", 1.0d));
            } catch (RuntimeException e) {
                // Go: StartAgent 的 once.Do 里只 logx.Error ⇒ 异常**不许**逃出来
                throw new AssertionError("install 不许抛异常阻断启动（Go: core/trace/agent.go:43-52）", e);
            }
            assertThat(installed)
                    .as("装不上时 install 必须返回 null（照 Go：全局仍是 no-op）")
                    .isNull();
            assertThat(GoStyleOtelAgent.isInstalled())
                    .as("装不上时「已装配」标志必须保持 false ⇒ 不会误报成已装")
                    .isFalse();

            // 全局仍是 no-op ⇒ 建出的 span 无效
            Span span = GlobalOpenTelemetry.get().getTracer("gate").spanBuilder("x").startSpan();
            assertThat(span.getSpanContext().isValid())
                    .as("装不上时全局仍是 no-op ⇒ span 无效（与 Go 失败路径同构）")
                    .isFalse();
            span.end();
        }
    }

    /** 极简 getter：只取 {@code traceparent}。 */
    private enum MapGetter implements TextMapGetter<Map<String, String>> {
        INSTANCE;

        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
            return carrier.get(key);
        }
    }
}
