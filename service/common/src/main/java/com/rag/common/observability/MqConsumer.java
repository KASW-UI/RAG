package com.rag.common.observability;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.function.Function;

import static com.rag.common.observability.Observability.isTimeoutError;
import static com.rag.common.observability.Observability.markDurationAndSlow;
import static com.rag.common.observability.Observability.markSystemError;
import static com.rag.common.observability.Observability.markTimeout;
import static com.rag.common.observability.Observability.slowThreshold;

/**
 * 对应 Go 版 service/common/observability/mq.go 的整个文件（43 行）。
 *
 * <p>Go 版核心：
 * <pre>
 * func TraceConsumer(ctx, tracerName, spanName, attrs, fn func(ctx) error) error {
 *     ctx, span := otel.Tracer(tracerName).Start(ctx, spanName,
 *         trace.WithSpanKind(trace.SpanKindConsumer),
 *         trace.WithAttributes(attrs...),
 *     )
 *     defer span.End()
 *     err := fn(consumeCtx)
 *     duration := time.Since(start)
 *     if err != nil {
 *         if IsTimeoutError(err) { MarkTimeout(...) } else { MarkSystemError(...) }
 *         return err
 *     }
 *     MarkDurationAndSlow(...)
 *     return nil
 * }
 * </pre>
 *
 * <p>Java 版用 {@link Tracer} + {@link Span} + Scope 还原 ctx 切换语义。
 *
 * <p>框架职责：
 * <ul>
 *   <li>Go 版的 {@code fn func(context.Context) error} 是带 ctx 的函数；Java 版用 {@link Function}{@code <Context, Throwable>}（只关心错误，返回值不是 MQ consumer 的语义重点）</li>
 *   <li>OpenTelemetry Java 的 tracer 通过 {@code GlobalOpenTelemetry.getTracer(name)} 拿</li>
 * </ul>
 */
public final class MqConsumer {

    private MqConsumer() {
    }

    /**
     * 对应 Go 版 {@code func TraceConsumer(ctx, tracerName, spanName string, attrs []attribute.KeyValue, fn func(context.Context) error) error}。
     *
     * <p><b>DRIFT-032 修复</b>：Go 版 attrs 是 {@code attribute.KeyValue}（key + value）；
     * Java 版历史签名是 {@code AttributeKey<?>[]}（只 key 没 value），导致 caller 传入的属性从未写入 span。
     * 现签名改为 {@code Map<String, Object>}（key-value 对），内部在 span builder 阶段用
     * {@code setAllAttributes} 写入 span（对齐 Go 版 {@code trace.WithAttributes(attrs...)} 行为）。
     *
     * <p>caller 用法：
     * <pre>{@code
     * MqConsumer.traceConsumer(parent, "mq", "process.order",
     *     Map.of("order.id", "12345", "kafka.partition", 3),
     *     ctx -> { ... });
     * }</pre>
     */
    public static Throwable traceConsumer(
            Context parent,
            String tracerName,
            String spanName,
            Map<String, Object> attrs,
            Function<Context, Throwable> fn) {
        Context ctx0 = parent == null ? Context.root() : parent;
        String finalTracerName = (tracerName == null || tracerName.isEmpty()) ? "mq-consumer" : tracerName;
        String finalSpanName = (spanName == null || spanName.isEmpty()) ? "message.consume" : spanName;

        Tracer tracer = io.opentelemetry.api.GlobalOpenTelemetry.getTracer(finalTracerName);
        var spanBuilder = tracer.spanBuilder(finalSpanName)
                .setSpanKind(SpanKind.CONSUMER)
                .setParent(ctx0);
        // DRIFT-032 修复：把 caller attrs 写到 span 上（对齐 Go 版 trace.WithAttributes(attrs...)）
        if (attrs != null && !attrs.isEmpty()) {
            io.opentelemetry.api.common.AttributesBuilder attrBuilder = io.opentelemetry.api.common.Attributes.builder();
            for (Map.Entry<String, Object> e : attrs.entrySet()) {
                if (e.getValue() == null) continue;
                addAttribute(attrBuilder, e.getKey(), e.getValue());
            }
            spanBuilder.setAllAttributes(attrBuilder.build());
        }
        Span span = spanBuilder.startSpan();
        try (Scope ignored = span.makeCurrent()) {
            Context consumeCtx = Context.current();
            Instant start = Instant.now();
            Throwable err;
            try {
                err = fn.apply(consumeCtx);
            } catch (Throwable t) {
                err = t;
            }
            Duration duration = Duration.between(start, Instant.now());
            // markTimeout / markSystemError / markDurationAndSlow 不再传 attrs（caller attrs 已在 span builder 写入）
            if (err != null) {
                if (isTimeoutError(err)) {
                    markTimeout(consumeCtx, err, duration, null);
                } else {
                    markSystemError(consumeCtx, err, duration, null);
                }
                return err;
            }
            markDurationAndSlow(consumeCtx, duration, slowThreshold(), null);
            return null;
        } finally {
            span.end();
        }
    }

    /**
     * 把任意 Java 类型映射到 OTEL attribute（对齐 Go attribute.AnyValue 自动类型推断）。
     */
    private static void addAttribute(io.opentelemetry.api.common.AttributesBuilder b, String key, Object value) {
        if (value instanceof String s) {
            b.put(key, s);
        } else if (value instanceof Long l) {
            b.put(key, l);
        } else if (value instanceof Integer i) {
            b.put(key, i.longValue());
        } else if (value instanceof Boolean bo) {
            b.put(key, bo);
        } else if (value instanceof Double d) {
            b.put(key, d);
        } else if (value instanceof Float f) {
            b.put(key, f.doubleValue());
        } else {
            // 兜底：toString()
            b.put(key, value.toString());
        }
    }
}