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
            msg = errorMessageOf(err);
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
        // M2 修复：Go 版 observability.go:103 无条件 `span.RecordError(errors.New(msg))`。
        //   旧 Java 版写成 `if (err != null)`，导致 err==nil 的调用点（msg="system error"）
        //   在 Go 会产生一条 span event、Java 不产生 —— 可观测的遥测差异。
        //   语义等价论证：Go 记录的是 errors.New(msg)（一个只有 message 的新 error），
        //   Java 没有 errors.New；用等价构造补齐，保证「一定有一条 exception event」。
        span.recordException(err != null ? err : new RuntimeException(msg));
        span.setStatus(StatusCode.ERROR, msg);
    }

    /**
     * 对应 Go 版 {@code MarkTimeout}。
     */
    public static void markTimeout(Context ctx, Throwable err, Duration duration, Map<String, Object> extraAttrs) {
        String msg = "timeout";
        if (err != null) {
            msg = errorMessageOf(err);
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
        // M2 同上：Go observability.go:126 无条件 RecordError。
        span.recordException(err != null ? err : new RuntimeException(msg));
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
     * <p>沿 cause 链走，对齐 Go 版 {@code errors.Is} / {@code errors.As}。
     *
     * <p><b>M1 修复</b>：Go 版三分支是
     * {@code errors.Is(err, context.DeadlineExceeded)} /
     * {@code status.Code == DEADLINE_EXCEEDED} /
     * {@code errors.As(err, &netErr) && netErr.Timeout()}。
     * 第三支的 {@code net.Error} 是 Go 标准库接口，涵盖 {@code *net.OpError}、
     * {@code *os.SyscallError}、HTTP transport 的各类超时 —— 旧 Java 版只认
     * {@link java.util.concurrent.TimeoutException} 和 {@link java.net.SocketTimeoutException}，
     * 漏掉了 {@link java.net.http.HttpTimeoutException}（它继承 {@link java.io.IOException}，
     * 不属于前两者），导致 <b>下游 HTTP 调用超时被归类成 system error 而非 timeout</b>，
     * {@code sea.error.kind} 与重试决策都会走错分支。
     *
     * <p>{@code net.Error.Timeout()} 的 Java 侧映射，无对应物的语言构造不做臆测：
     * <ul>
     *   <li>{@code http.Client 请求超时} → {@link java.net.http.HttpTimeoutException}</li>
     *   <li>{@code HttpClient 建连超时} → {@link java.net.http.HttpConnectTimeoutException}</li>
     *   <li>{@code net.Conn SetReadDeadline 超时} → {@link java.net.SocketTimeoutException}</li>
     *   <li>{@code syscall ETIMEDOUT（URLConnection 读超时）} → {@link java.io.InterruptedIOException}</li>
     * </ul>
     */
    public static boolean isTimeoutError(Throwable err) {
        if (err == null) return false;
        Throwable t = err;
        while (t != null) {
            if (t instanceof java.util.concurrent.TimeoutException) return true;
            if (t instanceof SocketTimeoutException) return true;
            if (t instanceof java.net.http.HttpTimeoutException) return true;
            if (t instanceof java.net.http.HttpConnectTimeoutException) return true;
            if (t instanceof java.io.InterruptedIOException) return true;
            Status st = Status.fromThrowable(t);
            if (st != null && st.getCode() == Code.DEADLINE_EXCEEDED) return true;
            Throwable next = t.getCause();
            if (next == t) break;
            t = next;
        }
        return false;
    }

    /**
     * M3 修复：对应 Go 版 {@code msg = err.Error()} 的取值语义。
     *
     * <p>Go 的 {@code error} 必有 message（{@code errors.New("")} 返回 {@code ""}），
     * {@code err.Error()} 结果恒为字符串，不存在 null 分支。旧 Java 版写成
     * {@code getMessage() != null ? getMessage() : getClass().getSimpleName()}，
     * 对无 message 的异常（如 {@code new RuntimeException()}）会填入类名 ——
     * 这是 Java 独有的信息，Go 侧对应位置会是空串，属于凭空多出来的 {@code sea.biz_msg} 值。
     *
     * <p>这里按 Go 语义取 {@code getMessage()} 原样，null 归一为 {@code ""}；
     * 异常类型本身仍完整保留在 {@link io.opentelemetry.api.trace.Span#recordException} 的栈里。
     */
    private static String errorMessageOf(Throwable err) {
        String m = err.getMessage();
        return m != null ? m : "";
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