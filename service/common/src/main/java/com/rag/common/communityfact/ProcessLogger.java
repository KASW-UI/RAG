package com.rag.common.communityfact;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Pattern;

/**
 * 对应 Go 版 service/common/communityfact/logging.go 的进程日志 + OTEL 本地追踪启动。
 *
 * <p>职责：
 * <ul>
 *   <li>{@link ProcessMetadata}：进程元数据 + 校验</li>
 *   <li>{@link #newProcessLogger(OutputStream, ProcessMetadata)}：按 Go 版 OBS-r3 envelope 输出 JSON，
 *       metadata 通过自定义 logback appender 在 logger 实例上绑定（不依赖 MDC，避免跨线程插值）</li>
 *   <li>{@link #installLocalTracing()}：本地 OpenTelemetry（AlwaysSample + W3C trace context + baggage）</li>
 * </ul>
 *
 * <p><b>与 Go 版的差异（已评估）</b>：
 * <ul>
 *   <li>Go 版用 {@code log/slog}；Java 版用 logback + Jackson 自定义 appender。</li>
 *   <li>Go 版通过 {@code base.WithAttrs([]slog.Attr)} 把 metadata 绑到 logger 实例；
 *       Java 版用 {@link ProcessLogAppender} 实例字段持有 metadata（每个 logger 一份 appender），
 *       <b>不依赖 SLF4J MDC</b>（MDC 是 thread-local，会跨线程插值，破坏跨线程一致性）。</li>
 *   <li>Go 版 {@code slog.ReplaceAttr} 重命名 time/level/msg；Java 版在 encoder 阶段做同样重命名。</li>
 *   <li>Java 端没有 {@code go-zero logx} 等价物；{@code InstallFrameworkLogger} 在 Java 版不需要，
 *       删除对应方法，避免为了模仿而再造一层。</li>
 * </ul>
 */
public final class ProcessLogger {

    /** process version 必须 40 位小写 hex。 */
    private static final Pattern PROCESS_VERSION = Pattern.compile("^[0-9a-f]{40}$");

    /** 严格保证字段顺序，便于测试断言。 */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSS'Z'").withZone(TimeZone.getTimeZone("UTC").toZoneId());

    /**
     * 对应 Go 版 {@code ProcessMetadata struct}。
     */
    public static final class ProcessMetadata {
        public String service;
        public String environment;
        public String version;
        public String instanceId;
        public String component;

        public ProcessMetadata() {}

        public ProcessMetadata(String service, String environment, String version,
                               String instanceId, String component) {
            this.service = service;
            this.environment = environment;
            this.version = version;
            this.instanceId = instanceId;
            this.component = component;
        }

        /**
         * 对应 Go 版 {@code func (m ProcessMetadata) Validate() error}。
         */
        public void validate() {
            if (service == null || service.isEmpty()
                    || instanceId == null || instanceId.isEmpty()
                    || component == null || component.isEmpty()
                    || version == null || !PROCESS_VERSION.matcher(version).matches()) {
                throw new IllegalArgumentException("community fact process identity incomplete");
            }
            if (!("local".equals(environment) || "test".equals(environment)
                    || "staging".equals(environment) || "production".equals(environment))) {
                throw new IllegalArgumentException("community fact process environment invalid");
            }
        }

        String resolved(String value) {
            return (value == null || value.isEmpty()) ? "unresolved" : value;
        }
    }

    /**
     * 对应 Go 版 {@code func NewProcessLogger(output io.Writer, meta ProcessMetadata) *slog.Logger}。
     *
     * <p>返回一个 SLF4J {@link Logger}，底层 logback 配置把 JSON 写到 {@code output}，
     * metadata 通过 {@link ProcessLogAppender} 实例字段注入（不依赖 MDC）。
     *
     * <p>logger 名唯一化（按 service + environment + component 拼），避免多实例互窜。
     */
    public static Logger newProcessLogger(OutputStream output, ProcessMetadata meta) {
        meta.validate();

        ch.qos.logback.classic.LoggerContext lc =
                (ch.qos.logback.classic.LoggerContext) LoggerFactory.getILoggerFactory();
        String loggerName = "communityfact.process."
                + meta.resolved(meta.service) + "."
                + meta.resolved(meta.environment) + "."
                + meta.resolved(meta.component);
        ch.qos.logback.classic.Logger logger = lc.getLogger(loggerName);
        logger.setLevel(ch.qos.logback.classic.Level.INFO);
        logger.setAdditive(false);
        logger.detachAndStopAllAppenders();

        ProcessLogAppender appender = new ProcessLogAppender(output, meta);
        appender.setContext(lc);
        appender.start();
        logger.addAppender(appender);
        return logger;
    }

    /**
     * 对应 Go 版 {@code func InstallLocalTracing() *sdktrace.TracerProvider}。
     */
    public static SdkTracerProvider installLocalTracing() {
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setSampler(Sampler.alwaysOn())
                .build();
        OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(TextMapPropagator.composite(
                        W3CTraceContextPropagator.getInstance(),
                        W3CBaggagePropagator.getInstance())))
                .buildAndRegisterGlobal();
        return tracerProvider;
    }

    /**
     * 把当前 OpenTelemetry 上下文的 trace_id/span_id 注入业务 envelope。
     * 对应 Go 版 {@code traceLogHandler.Handle}：record.AddAttrs(slog.String("trace_id", ...), ...)。
     *
     * <p>本方法由 {@link ProcessLogAppender} 内部调用，业务层不需要直接调。
     */
    private static Map<String, Object> traceContextAttrs() {
        Span span = Span.fromContext(io.opentelemetry.context.Context.current());
        if (!span.getSpanContext().isValid()) {
            return Map.of();
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("trace_id", span.getSpanContext().getTraceId());
        m.put("span_id", span.getSpanContext().getSpanId());
        m.put("trace_sampled", span.getSpanContext().isSampled());
        return m;
    }

    /**
     * 自定义 logback appender：把 metadata 注入输出 JSON，不依赖 MDC。
     *
     * <p>对应 Go 版 {@code NewProcessLogger} 的 base.WithAttrs 注入字段 + traceLogHandler 注入 trace 字段。
     */
    private static final class ProcessLogAppender extends ch.qos.logback.core.AppenderBase<ch.qos.logback.classic.spi.ILoggingEvent> {
        private final OutputStream output;
        private final ProcessMetadata meta;

        ProcessLogAppender(OutputStream output, ProcessMetadata meta) {
            this.output = output;
            this.meta = meta;
        }

        @Override
        protected void append(ch.qos.logback.classic.spi.ILoggingEvent event) {
            Map<String, Object> json = new LinkedHashMap<>();
            // base attrs（slog base.WithAttrs 等价）
            json.put("service", meta.resolved(meta.service));
            json.put("environment", meta.resolved(meta.environment));
            json.put("service_version", meta.resolved(meta.version));
            json.put("instance_id", meta.resolved(meta.instanceId));
            json.put("component", meta.resolved(meta.component));
            json.put("log_source", "application");
            // 字段重命名（slog ReplaceAttr 等价）
            json.put("timestamp", TIMESTAMP_FMT.format(Instant.ofEpochMilli(event.getTimeStamp())));
            json.put("level", event.getLevel().toString().toLowerCase());
            json.put("message", event.getFormattedMessage());
            // trace 字段（traceLogHandler 等价）
            json.putAll(traceContextAttrs());
            // 业务 SLF4J fluent API 携带的 KV（addKeyValue 等价）
            event.getKeyValuePairs().forEach(kv -> {
                if (kv.value != null) json.put(kv.key, kv.value.toString());
            });
            try (JsonGenerator gen = MAPPER.getFactory().createGenerator(output)) {
                MAPPER.writeValue(gen, json);
                gen.flush();
                output.write('\n');
                output.flush();
            } catch (IOException e) {
                throw new IllegalStateException("community process logger write failed", e);
            }
        }
    }

    private ProcessLogger() {
    }
}