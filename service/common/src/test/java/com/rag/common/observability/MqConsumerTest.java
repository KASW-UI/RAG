package com.rag.common.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * MqConsumer 关键契约：默认 tracerName/spanName、attrs 写入 span、err → markTimeout / markSystemError。
 *
 * <p>通过 GlobalOpenTelemetry 装上带 InMemorySpanExporter 的 SDK，验证 span 真的被创建并写入属性。
 */
class MqConsumerTest {

    private InMemorySpanExporter exporter;
    private SdkTracerProvider tracerProvider;

    @BeforeEach
    void installSdk() {
        GlobalOpenTelemetry.resetForTest();
        exporter = InMemorySpanExporter.create();
        tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
        GlobalOpenTelemetry.set(sdk);
    }

    @AfterEach
    void cleanup() {
        GlobalOpenTelemetry.resetForTest();
        tracerProvider.shutdown();
    }

    @Test
    void traceConsumerAppliesDefaultsAndAttributes() {
        Throwable result = MqConsumer.traceConsumer(Context.root(), "", "",
                Map.of("kafka.topic", "order", "kafka.partition", 3),
                ctx -> null);
        assertEquals(null, result);
        List<io.opentelemetry.sdk.trace.data.SpanData> spans = exporter.getFinishedSpanItems();
        assertEquals(1, spans.size());
        var sd = spans.get(0);
        assertEquals("message.consume", sd.getName());
        assertEquals("mq-consumer", sd.getInstrumentationScopeInfo().getName());
        var attrs = sd.getAttributes().asMap();
        assertEquals("order", attrs.get(io.opentelemetry.api.common.AttributeKey.stringKey("kafka.topic")));
        assertEquals(3L, attrs.get(io.opentelemetry.api.common.AttributeKey.longKey("kafka.partition")));
    }

    @Test
    void traceConsumerOnErrorMarksSystemError() {
        RuntimeException boom = new RuntimeException("boom");
        Throwable returned = MqConsumer.traceConsumer(Context.root(), "tracer", "span",
                Map.of(), ctx -> boom);
        assertEquals(boom, returned);
        var spans = exporter.getFinishedSpanItems();
        assertEquals(1, spans.size());
        var attrs = spans.get(0).getAttributes().asMap();
        assertEquals(true, attrs.get(io.opentelemetry.api.common.AttributeKey.booleanKey("error")));
        assertEquals("system", attrs.get(io.opentelemetry.api.common.AttributeKey.stringKey("sea.error.kind")));
    }

    @Test
    void traceConsumerOnTimeoutMarksTimeout() {
        java.util.concurrent.TimeoutException te = new java.util.concurrent.TimeoutException("dl");
        Throwable returned = MqConsumer.traceConsumer(Context.root(), null, null,
                Map.of(), ctx -> te);
        assertEquals(te, returned);
        var spans = exporter.getFinishedSpanItems();
        assertEquals(1, spans.size());
        var attrs = spans.get(0).getAttributes().asMap();
        assertEquals("timeout", attrs.get(io.opentelemetry.api.common.AttributeKey.stringKey("sea.error.kind")));
        assertEquals(true, attrs.get(io.opentelemetry.api.common.AttributeKey.booleanKey("sea.timeout")));
    }
}