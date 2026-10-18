package com.rag.common.observability;

import io.grpc.Status;
import io.grpc.Status.Code;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Map;

import static com.rag.common.observability.ObservabilityConstants.ATTR_BIZ_CODE;
import static com.rag.common.observability.ObservabilityConstants.ATTR_BIZ_MSG;
import static com.rag.common.observability.ObservabilityConstants.ATTR_DURATION_DOUBLE;
import static com.rag.common.observability.ObservabilityConstants.ATTR_ERROR;
import static com.rag.common.observability.ObservabilityConstants.ATTR_ERROR_KIND;
import static com.rag.common.observability.ObservabilityConstants.ATTR_TIMEOUT;
import static com.rag.common.observability.ObservabilityConstants.ATTR_TIMEOUT_BOOL;
import static com.rag.common.observability.ObservabilityConstants.DEFAULT_SLOW_THRESHOLD;
import static com.rag.common.observability.ObservabilityConstants.ERROR_KIND_BUSINESS;
import static com.rag.common.observability.ObservabilityConstants.ERROR_KIND_SYSTEM;
import static com.rag.common.observability.ObservabilityConstants.ERROR_KIND_TIMEOUT;
import static com.rag.common.observability.ObservabilityConstants.ATTR_SLOW_BOOL;
import static com.rag.common.observability.ObservabilityConstants.SUCCESS_BIZ_CODE;

/**
 * 对应 Go 版 service/common/observability/observability.go 的整个文件。
 *
 * <p>关键契约：
 * <ul>
 *   <li>{@code MarkBusinessError/MarkSystemError/MarkTimeout/MarkDurationAndSlow} 形参 {@code Map<String, Object>}，
 *       与 MqConsumer 同款类型推断（Java OTEL 没有 {@code attribute.KeyValue} varargs）</li>
 *   <li>Duration 用 double（毫秒，保留 Go 版 float64(microsec/1000) 的亚毫秒精度）</li>
 *   <li>{@code IsTimeoutError} 沿 cause 链走（对齐 Go 版 {@code errors.Is} 语义）</li>
 * </ul>
 */
public final class Observability {

    private Observability() {
    }

    public static Duration slowThreshold() {
        for (String key : new String[]{"SEA_OBSERVABILITY_SLOW_THRESHOLD", "SEA_OBSERVABILITY_SLOW_THRESHOLD_MS"}) {
            String raw = System.getenv(key);
            raw = raw == null ? "" : raw.trim();
            if (raw.isEmpty()) continue;
            Duration d = parseGoDuration(raw);
            if (d != null && d.compareTo(Duration.ZERO) > 0) return d;
            try {
                long ms = Long.parseLong(raw);
                if (ms > 0) return Duration.ofMillis(ms);
            } catch (NumberFormatException ignore) { /* not pure ms */ }
        }
        return DEFAULT_SLOW_THRESHOLD;
    }

    public static Duration timeoutFromMillis(long ms) {
        if (ms <= 0) return Duration.ZERO;
        return Duration.ofMillis(ms);
    }

    /**
     * 对应 Go 版 {@code MarkBusinessError}。
     */
    public static void markBusinessError(Context ctx, int code, String msg, Duration duration, Map<String, Object> extraAttrs) {
        if (code == SUCCESS_BIZ_CODE) {
            markDurationAndSlow(ctx, duration, slowThreshold(), extraAttrs);
            return;
        }
        Span span = Span.fromContext(ctx);
        if (!span.isRecording()) return;
        AttributesBuilder b = io.opentelemetry.api.common.Attributes.builder();
        b.put(ATTR_ERROR, true);
        b.put(ATTR_ERROR_KIND, ERROR_KIND_BUSINESS);
        b.put(ATTR_BIZ_CODE, (long) code);
        b.put(ATTR_BIZ_MSG, msg);
        b.put(ATTR_DURATION_DOUBLE, durationMillis(duration));
        addExtraAttrs(b, extraAttrs);
        span.setAllAttributes(b.build());
        span.setStatus(StatusCode.ERROR, msg);
    }

    /**
     * 对应 Go 版 {@code MarkSystemError}。
     */
    public static void markSystemError(Context ctx, Throwable err, Duration duration, Map<String, Object> extraAttrs) {
        String msg = "system error";
        if (err != null) {
            msg = err.getMessage() != null ? err.getMessage() : err.getClass().getSimpleName();
        }
        Span span = Span.fromContext(ctx);
        if (!span.isRecording()) return;
        AttributesBuilder b = io.opentelemetry.api.common.Attributes.builder();
        b.put(ATTR_ERROR, true);
        b.put(ATTR_ERROR_KIND, ERROR_KIND_SYSTEM);
        b.put(ATTR_BIZ_MSG, msg);
        b.put(ATTR_DURATION_DOUBLE, durationMillis(duration));
        addExtraAttrs(b, extraAttrs);
        span.setAllAttributes(b.build());
        if (err != null) span.recordException(err);
        span.setStatus(StatusCode.ERROR, msg);
    }

    /**
     * 对应 Go 版 {@code MarkTimeout}。
     */
    public static void markTimeout(Context ctx, Throwable err, Duration duration, Map<String, Object> extraAttrs) {
        String msg = "timeout";
        if (err != null) {
            msg = err.getMessage() != null ? err.getMessage() : err.getClass().getSimpleName();
        }
        Span span = Span.fromContext(ctx);
        if (!span.isRecording()) return;
        AttributesBuilder b = io.opentelemetry.api.common.Attributes.builder();
        b.put(ATTR_ERROR, true);
        b.put(ATTR_ERROR_KIND, ERROR_KIND_TIMEOUT);
        b.put(ATTR_TIMEOUT_BOOL, true);
        b.put(ATTR_BIZ_MSG, msg);
        b.put(ATTR_DURATION_DOUBLE, durationMillis(duration));
        addExtraAttrs(b, extraAttrs);
        span.setAllAttributes(b.build());
        if (err != null) span.recordException(err);
        span.setStatus(StatusCode.ERROR, msg);
    }

    /**
     * 对应 Go 版 {@code MarkDurationAndSlow}。
     */
    public static void markDurationAndSlow(Context ctx, Duration duration, Duration slowThreshold, Map<String, Object> extraAttrs) {
        Span span = Span.fromContext(ctx);
        if (!span.isRecording()) return;
        AttributesBuilder b = io.opentelemetry.api.common.Attributes.builder();
        b.put(ATTR_DURATION_DOUBLE, durationMillis(duration));
        if (slowThreshold != null && slowThreshold.compareTo(Duration.ZERO) > 0
                && duration != null && duration.compareTo(slowThreshold) > 0) {
            b.put(ATTR_SLOW_BOOL, true);
        }
        addExtraAttrs(b, extraAttrs);
        span.setAllAttributes(b.build());
    }

    /**
     * 对应 Go 版 {@code func IsTimeoutError(err error) bool}。
     * <p>沿 cause 链走，对齐 Go 版 {@code errors.Is}。
     */
    public static boolean isTimeoutError(Throwable err) {
        if (err == null) return false;
        Throwable t = err;
        while (t != null) {
            if (t instanceof java.util.concurrent.TimeoutException) return true;
            if (t instanceof SocketTimeoutException) return true;
            Status st = Status.fromThrowable(t);
            if (st != null && st.getCode() == Code.DEADLINE_EXCEEDED) return true;
            Throwable next = t.getCause();
            if (next == t) break;
            t = next;
        }
        return false;
    }

    /**
     * 对应 Go 版 {@code durationMilliseconds(duration time.Duration) float64}：
     * {@code float64(duration.Microseconds()) / 1000}（保留亚毫秒精度）。
     */
    public static double durationMillis(Duration duration) {
        if (duration == null) return 0.0;
        return (double) duration.toNanos() / 1_000_000.0;
    }

    /**
     * 把任意 Java 类型映射到 OTEL attribute（与 MqConsumer.addAttribute 同款推断）。
     */
    private static void addExtraAttrs(AttributesBuilder b, Map<String, Object> extra) {
        if (extra == null || extra.isEmpty()) return;
        for (Map.Entry<String, Object> e : extra.entrySet()) {
            Object v = e.getValue();
            if (v == null) continue;
            String key = e.getKey();
            if (v instanceof String s) b.put(key, s);
            else if (v instanceof Long l) b.put(key, l);
            else if (v instanceof Integer i) b.put(key, i.longValue());
            else if (v instanceof Boolean bo) b.put(key, bo);
            else if (v instanceof Double d) b.put(key, d);
            else if (v instanceof Float f) b.put(key, f.doubleValue());
            else b.put(key, v.toString());
        }
    }

    /** 解析 Go 版 time.ParseDuration 子集。 */
    private static Duration parseGoDuration(String raw) {
        try {
            long totalNanos = 0;
            int i = 0;
            int n = raw.length();
            while (i < n) {
                int j = i;
                while (j < n && (Character.isDigit(raw.charAt(j)) || raw.charAt(j) == '.')) j++;
                if (j == i) return null;
                double value = Double.parseDouble(raw.substring(i, j));
                int k = j;
                while (k < n && Character.isLetter(raw.charAt(k))) k++;
                if (k == j) return null;
                long nanos;
                switch (raw.substring(j, k)) {
                    case "ns": nanos = (long) value; break;
                    case "us": nanos = (long) (value * 1_000L); break;
                    case "ms": nanos = (long) (value * 1_000_000L); break;
                    case "s":  nanos = (long) (value * 1_000_000_000L); break;
                    case "m":  nanos = (long) (value * 60L * 1_000_000_000L); break;
                    case "h":  nanos = (long) (value * 3600L * 1_000_000_000L); break;
                    default: return null;
                }
                totalNanos += nanos;
                i = k;
            }
            return Duration.ofNanos(totalNanos);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}