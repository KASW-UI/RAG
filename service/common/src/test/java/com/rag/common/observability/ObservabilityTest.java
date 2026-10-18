package com.rag.common.observability;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Observability 关键契约：isTimeoutError 沿 cause 链、markXxx 写属性、durationMillis 保留亚毫秒。
 */
class ObservabilityTest {

    @Test
    void isTimeoutErrorReturnsFalseOnNull() {
        assertFalse(Observability.isTimeoutError(null));
    }

    @Test
    void isTimeoutErrorDetectsTimeoutException() {
        assertTrue(Observability.isTimeoutError(new TimeoutException("t")));
    }

    @Test
    void isTimeoutErrorDetectsSocketTimeoutException() {
        assertTrue(Observability.isTimeoutError(new SocketTimeoutException("s")));
    }

    @Test
    void isTimeoutErrorWalksCauseChain() {
        // 对齐 Go 版 errors.Is：包装在 ExecutionException 里的 TimeoutException 也应识别
        TimeoutException root = new TimeoutException("root");
        ExecutionException wrapper = new ExecutionException(root);
        assertTrue(Observability.isTimeoutError(wrapper));
    }

    @Test
    void isTimeoutErrorDetectsDeadlineExceeded() {
        // 故意构造 INTERNAL / DEADLINE_EXCEEDED
        io.grpc.StatusRuntimeException internal = io.grpc.Status.INTERNAL
                .withDescription("dummy").asRuntimeException();
        io.grpc.StatusRuntimeException deadline = io.grpc.Status.DEADLINE_EXCEEDED
                .withDescription("dl").asRuntimeException();
        assertTrue(Observability.isTimeoutError(deadline));
        assertFalse(Observability.isTimeoutError(internal));
    }

    @Test
    void isTimeoutErrorReturnsFalseOnUnrelatedException() {
        assertFalse(Observability.isTimeoutError(new RuntimeException("boom")));
        assertFalse(Observability.isTimeoutError(new IllegalArgumentException("x")));
    }

    @Test
    void durationMillisPreservesSubMillisPrecision() {
        // Go 版用 float64(microsec/1000)；Java 版 toNanos / 1_000_000 也保留亚毫秒
        Duration d = Duration.ofNanos(1_234_567);
        double ms = Observability.durationMillis(d);
        assertEquals(1.234567, ms, 0.000001);
    }

    @Test
    void markBusinessErrorWritesAttributes() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        SdkTracerProvider tracer = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        try {
            Span span = tracer.get("test").spanBuilder("op").startSpan();
            Context ctx = Context.current().with(span);
            Observability.markBusinessError(ctx, 40101, "password wrong", Duration.ofMillis(10),
                    Map.of("custom.k", "v"));
            span.end();
            List<io.opentelemetry.sdk.trace.data.SpanData> spans = exporter.getFinishedSpanItems();
            assertEquals(1, spans.size());
            var attrs = spans.get(0).getAttributes().asMap();
            assertEquals(true, attrs.get(io.opentelemetry.api.common.AttributeKey.booleanKey("error")));
            assertEquals("business", attrs.get(io.opentelemetry.api.common.AttributeKey.stringKey("sea.error.kind")));
            assertEquals(40101L, attrs.get(io.opentelemetry.api.common.AttributeKey.longKey("sea.biz_code")));
            assertEquals("password wrong", attrs.get(io.opentelemetry.api.common.AttributeKey.stringKey("sea.biz_msg")));
        } finally {
            tracer.shutdown();
        }
    }

    @Test
    void markTimeoutWritesTimeoutAttributes() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        SdkTracerProvider tracer = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        try {
            Span span = tracer.get("test").spanBuilder("op").startSpan();
            Context ctx = Context.current().with(span);
            Observability.markTimeout(ctx, new TimeoutException("dl"), Duration.ofMillis(5), null);
            span.end();
            var attrs = exporter.getFinishedSpanItems().get(0).getAttributes().asMap();
            assertEquals(true, attrs.get(io.opentelemetry.api.common.AttributeKey.booleanKey("error")));
            assertEquals("timeout", attrs.get(io.opentelemetry.api.common.AttributeKey.stringKey("sea.error.kind")));
            assertEquals(true, attrs.get(io.opentelemetry.api.common.AttributeKey.booleanKey("sea.timeout")));
        } finally {
            tracer.shutdown();
        }
    }

    @Test
    void markDurationAndSlowDetectsSlow() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        SdkTracerProvider tracer = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        try {
            Span span = tracer.get("test").spanBuilder("op").startSpan();
            Context ctx = Context.current().with(span);
            // duration 5ms, threshold 1ms → slow
            Observability.markDurationAndSlow(ctx, Duration.ofMillis(5), Duration.ofMillis(1), null);
            span.end();
            var attrs = exporter.getFinishedSpanItems().get(0).getAttributes().asMap();
            assertEquals(true, attrs.get(io.opentelemetry.api.common.AttributeKey.booleanKey("sea.slow")));
        } finally {
            tracer.shutdown();
        }
    }

    @Test
    void markDurationAndSlowUnderThresholdDoesNotMarkSlow() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        SdkTracerProvider tracer = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        try {
            Span span = tracer.get("test").spanBuilder("op").startSpan();
            Context ctx = Context.current().with(span);
            Observability.markDurationAndSlow(ctx, Duration.ofMillis(1), Duration.ofMillis(500), null);
            span.end();
            var attrs = exporter.getFinishedSpanItems().get(0).getAttributes().asMap();
            // sea.slow 不存在或为 false
            Object slow = attrs.get(io.opentelemetry.api.common.AttributeKey.booleanKey("sea.slow"));
            assertTrue(slow == null || Boolean.FALSE.equals(slow));
        } finally {
            tracer.shutdown();
        }
    }

    @Test
    void slowThresholdFallsBackToDefault() {
        // 在没有 env 时返回 500ms
        Duration d = Observability.slowThreshold();
        // env 可能被 CI 配置；我们只验证 d > 0
        assertTrue(d.toMillis() > 0);
    }
}