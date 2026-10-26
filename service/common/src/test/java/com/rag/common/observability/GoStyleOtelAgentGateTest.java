package com.rag.common.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🔴🔴 **T-11 守门**：全局 OpenTelemetry 装配（对应 Go {@code core/trace/agent.go} 的 {@code startAgent}）。
 *
 * <h2>本轮要证的<b>三件事</b>（每件都对应 Go 的一行）</h2>
 * <table border="1">
 *   <caption>三条守门 ↔ Go 出处</caption>
 *   <tr><th>守门</th><th>断言</th><th>Go 出处</th></tr>
 *   <tr>
 *     <td>① <b>全局 provider 是真的</b></td>
 *     <td>真 Spring Boot 上下文启动后，{@code GlobalOpenTelemetry.get()} 建出的 span
 *         <b>SpanContext 有效</b>；全局 propagator <b>能 inject 出 traceparent</b></td>
 *     <td>{@code core/trace/agent.go:127}（{@code otel.SetTracerProvider(tp)}）＋
 *         {@code core/trace/propagation.go:8-11}（{@code SetTextMapPropagator}）</td>
 *   </tr>
 *   <tr>
 *     <td>② <b>span 真的被导出到网络上</b></td>
 *     <td>假 OTLP 接收端（<b>真 gRPC 服务端</b>）收到 ≥1 个非空 Export 请求</td>
 *     <td>{@code core/trace/agent.go:114-121}（{@code if len(c.Endpoint) > 0 { WithBatcher(exp) }}）</td>
 *   </tr>
 *   <tr>
 *     <td>③ <b>关停会 flush</b></td>
 *     <td>关停（{@code shutdown()}）后，pending 的 span 立刻被导出
 *         （<b>早于</b> BatchSpanProcessor 的 5s 批量周期 ⇒ 证明是 flush 带来的）</td>
 *     <td>{@code core/service/serviceconf.go:71-73}（{@code proc.AddShutdownListener(trace.StopAgent)}）
 *         ＋ {@code core/trace/agent.go:55-62}（{@code tp.Shutdown}）</td>
 *   </tr>
 * </table>
 *
 * <h2>🔴 为什么 ① 必须用「真 Spring Boot 上下文」</h2>
 * <p>因为 RAG 侧走的是 Spring Boot 的<b>自动配置清单</b>机制
 * （{@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}）
 * ⇒ 只有真上下文才证明「清单被读到 ⇒ 自动配置被应用 ⇒ BFPP 跑了 ⇒ 全局装上了」
 * ⇒ <b>mock 上下文证明不了任何一环</b>。
 * <p>⚠️ 而这条链<b>正是 rpc 模块（刻意不扫 {@code com.rag.common}）能拿到 trace 的唯一路径</b>
 * ⇒ 它必须被真跑一次。
 *
 * <h2>🔴 为什么 ② 必须是「真 gRPC 服务端」</h2>
 * <p>要证的是「<b>字节真的出了机器</b>」，不是「exporter 被配上了」⇒ mock 只能证后半句。
 */
class GoStyleOtelAgentGateTest {

    @BeforeEach
    void resetGlobal() {
        // 同 JVM 内其它测试可能装过全局（GlobalOpenTelemetry 全局只能设一次）
        // ⇒ 每个用例都从「干净的全局」开始。
        GlobalOpenTelemetry.resetForTest();
        GoStyleOtelAgent.shutdown();
    }

    @AfterEach
    void cleanupGlobal() {
        GoStyleOtelAgent.shutdown();
        GlobalOpenTelemetry.resetForTest();
    }

    // ====================================================================================
    // ① 全局 provider 是真的（真 Spring Boot 上下文）
    // ====================================================================================

    @Test
    @DisplayName("🔴 真 Spring 上下文：全局 provider 装上了（span 有效）＋ 全局 propagator 能 inject traceparent")
    void globalProviderIsRealInsideRealSpringContext() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(GateApplication.class)
                // 🔴 强制非 web：classpath 上有 spring-boot-starter-web（test scope）
                //    ⇒ 不指定的话 Spring 会起 Tomcat 去抢 8080 ⇒ 「端口已被占用」这种
                //    与被测语义毫无关系的失败会盖掉真正的断言。
                .web(org.springframework.boot.WebApplicationType.NONE)
                .properties(
                        "otel.service.name=common-otel-gate",
                        // 空端点 ⇒ 照 Go 的 `len(c.Endpoint) > 0` ⇒ 不装 exporter（免得连不上 jaeger 刷日志）
                        "otel.exporter.otlp.endpoint=")
                .run()) {

            // --- ①a：全局 tracer 建出的 span 必须**有效** ---
            //     修前：GlobalOpenTelemetry 是 no-op ⇒ SpanContext 恒 INVALID ⇒ 这里必红
            Span span = GlobalOpenTelemetry.get().getTracer("gate").spanBuilder("gate-span").startSpan();
            assertThat(span.getSpanContext().isValid())
                    .as("全局 provider 必须是真 SDK（Go: otel.SetTracerProvider(tp)）")
                    .isTrue();
            span.end();

            // --- ①b：全局 propagator 必须能 inject 出 traceparent ---
            //     Go: core/trace/propagation.go:8-11（init() 里装 composite propagator）
            Map<String, String> carrier = new HashMap<>();
            try (Scope scope = span.makeCurrent()) {
                GlobalOpenTelemetry.getPropagators().getTextMapPropagator()
                        .inject(Context.current(), carrier, Map::put);
            }
            assertThat(carrier)
                    .as("全局 propagator 必须能 inject traceparent（Go 的 init() 是**无条件**装的）")
                    .containsKey("traceparent");
            assertThat(carrier.get("traceparent"))
                    .as("traceparent 必须是 W3C 形态：00-<32 hex>-<16 hex>-<2 hex>")
                    .matches("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}");
        }
    }

    // ====================================================================================
    // ② span 真的被导出到网络上（真 gRPC 假 OTLP 接收端）
    // ====================================================================================

    @Test
    @DisplayName("🔴 真 gRPC 假 OTLP 接收端：span 真的被导出（收到 ≥1 个非空 Export 请求）")
    void spanIsActuallyExportedToTheWire() throws Exception {
        try (FakeOtlpCollector collector = new FakeOtlpCollector()) {
            SdkTracerProvider provider = GoStyleOtelAgent.buildProvider(
                    new GoStyleOtelConfig("common-otel-gate", collector.endpoint(), "grpc", 1.0d));
            try {
                Tracer tracer = provider.get("gate");
                tracer.spanBuilder("exported-span").startSpan().end();

                // BatchSpanProcessor 的默认批量周期是 5s ⇒ 这里等它自然导出（**不靠关停**）
                // ⇒ 证明的是「exporter 真的在导出」，而不是「关停时 flush 了一把」。
                awaitTrue(() -> collector.requestCount() > 0, 15_000L);
            } finally {
                provider.close();
            }
            assertThat(collector.requestCount())
                    .as("span 必须真的被送到 OTLP 接收端（不是「exporter 被配上了」）")
                    .isGreaterThanOrEqualTo(1);
            assertThat(collector.receivedBytes())
                    .as("收到的 Export 请求体不能是空的")
                    .isGreaterThan(0);
        }
    }

    // ====================================================================================
    // ③ 关停会 flush（早于批量周期）
    // ====================================================================================

    @Test
    @DisplayName("🔴 关停会 flush：pending 的 span 在关停后立刻被导出（早于 5s 批量周期）")
    void shutdownFlushesPendingSpans() throws Exception {
        try (FakeOtlpCollector collector = new FakeOtlpCollector()) {
            GoStyleOtelAgent.install(
                    new GoStyleOtelConfig("common-otel-gate", collector.endpoint(), "grpc", 1.0d));
            assertThat(GoStyleOtelAgent.isInstalled()).isTrue();

            Tracer tracer = GlobalOpenTelemetry.get().getTracer("gate");
            tracer.spanBuilder("pending-span").startSpan().end();

            // 关停 ⇒ Go: core/trace/agent.go:55-62（tp.Shutdown ⇒ flush 未导出的 span）
            GoStyleOtelAgent.shutdown();

            // ⚠️ 3s 的判定窗口 < BatchSpanProcessor 的 5s 批量周期
            //    ⇒ 「这么快就收到」只能解释成「关停时 flush 了」
            //    ⇒ 若实现退化成「不 flush」，这里会因超时而红。
            awaitTrue(() -> collector.requestCount() > 0, 3_000L);
            assertThat(collector.requestCount())
                    .as("关停必须 flush 未导出的 span（Go: trace.StopAgent → tp.Shutdown）")
                    .isGreaterThanOrEqualTo(1);
        }
    }

    // ====================================================================================
    // 夹具
    // ====================================================================================

    /**
     * 最小 Spring Boot 应用：<b>只开自动配置，不开组件扫描</b>。
     *
     * <p>⚠️ 不扫描是关键：否则会扫到 {@code com.rag.common} 里的 servlet 相关组件
     * （四个 Filter 依赖 servlet API）⇒ 非 web 上下文里会炸。
     * ⇒ 本门禁只验「自动配置清单 ⇒ BFPP ⇒ 全局装配」这一条链。
     */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class GateApplication {
    }

    private static void awaitTrue(BooleanSupplier condition, long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        // 🔴 超时必须**炸**（同 like 域契约守门的 awaitTrue：超时静默返回会让后续断言空转通过）
        throw new AssertionError("条件在 " + timeoutMillis + "ms 内未成立");
    }
}
