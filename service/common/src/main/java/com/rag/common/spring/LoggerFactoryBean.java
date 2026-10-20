package com.rag.common.spring;

import com.rag.common.logger.FatalError;
import com.rag.common.logger.LogOption;
import com.rag.common.logger.LogOptions;
import com.rag.common.snowflake.Snowflake;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Spring 入口：将服务名从 {@code spring.application.name} 注入到 Bean 实例，提供实例方法
 * {@link #logInfo} / {@link #logBusinessErr} / {@link #logFatal}。
 *
 * <p><b>与 {@link com.rag.common.logger.Logger}（static GLOBAL）完全独立</b>（决策 3）：
 * <ul>
 *   <li>Bean 实例的 {@code serviceName} 由 Spring 容器在构造时注入，运行时不变；</li>
 *   <li>{@code Logger.GLOBAL.serviceName} 由业务通过 {@code Logger.init(...)} 设置；</li>
 *   <li>两条路径用各自的 {@code serviceName}，互不干扰。</li>
 * </ul>
 *
 * <p><b>双轨并存</b>（决策 2）：业务可以选择
 * <pre>{@code
 *   // 旧 static 入口（继续可用）
 *   Logger.logBusinessErr(ctx, 40101, err, WithUserID.of("u1"));
 *
 *   // 新 Spring Bean 入口（业务模块未来平滑迁移路径）
 *   @Autowired LoggerFactoryBean logger;
 *   logger.logBusinessErr(ctx, 40101, err, WithUserID.of("u1"));
 * }</pre>
 *
 * <p><b>字段语义</b>与 Go 版 {@code logger.LogBusinessErr} 1:1 对齐：
 * service / event_id / trace_id / error_code / file_line / call_path / call_chain / error_reason；
 * 可选 user_id / article_id（空字符串字段消失而非空串）。
 */
@Component
public class LoggerFactoryBean {

    /**
     * Spring 注入的 serviceName；与 {@code Logger.GLOBAL.serviceName} 完全独立。
     */
    private final String serviceName;

    /**
     * 调用栈过滤前缀，独立维护（避免 logger 包自身栈帧噪声）。
     *
     * <p><b>M10 修复</b>：与 {@link com.rag.common.logger.Logger#FILTER_PREFIXES} 同款修法 ——
     * 旧版这些全是包前缀，却拿 {@link #getFuncName} 产出的<b>短类名</b>去比，
     * 永远匹配不上，{@code call_chain} 退化成未过滤的完整栈。
     * 现改为拿 {@code declaringClass.getName()} 全名比对（见 {@link #isFiltered}）。
     * 两份实现的清单必须保持一致：{@code Logger} 的清单带 {@code com.rag.common.logger.}，
     * 本类额外带 {@code com.rag.common.spring.}（本类自身所在包）。
     */
    private static final List<String> FILTER_PREFIXES = List.of(
            // —— Go 版 logger.go:53-58 原文三条 ——
            "runtime.",
            "testing.",
            "github.com/zeromicro/go-zero/",
            // —— Java 侧「框架内部帧」，对应 Go 想过滤掉的 go-zero 内部帧 ——
            "java.lang.Thread",
            "java.util.concurrent.",
            "jdk.internal.misc.",
            "io.opentelemetry.",
            "org.slf4j.",
            "ch.qos.logback.",
            "com.rag.common.logger.",
            "com.rag.common.spring."
    );

    private static final Pattern IMAGE_BASE64_TRUNCATE = Pattern.compile("(image_base64:\")[^\"]*(\"?)");

    /**
     * Spring 构造注入 serviceName；不允许为空（与 {@code Logger.init} 行为对齐）。
     *
     * @param serviceName 从 {@code spring.application.name} 注入的服务名；为空时抛 IllegalStateException
     */
    public LoggerFactoryBean(
            @Value("${spring.application.name:unknown-service}") String serviceName
    ) {
        if (serviceName == null || serviceName.isEmpty()) {
            throw new IllegalStateException("spring.application.name must not be empty");
        }
        this.serviceName = serviceName;
    }

    public String getServiceName() {
        return serviceName;
    }

    /**
     * 业务错误日志，字段集合与 {@code Logger.logBusinessErr} 一致。
     */
    public void logBusinessErr(io.opentelemetry.context.Context otelCtx,
                               int code,
                               Throwable err,
                               LogOption... opts) {
        LogOptions logOpts = new LogOptions();
        if (opts != null) {
            for (LogOption opt : opts) {
                opt.apply(logOpts);
            }
        }

        String eventIdStr = "unknown";
        try {
            long eventId = Snowflake.nextId();
            eventIdStr = String.valueOf(eventId);
        } catch (Throwable ignore) {
            // 与 Logger.doLogBusinessErr 同款兜底
        }

        String errorReason = "unknown error";
        if (err != null) {
            errorReason = err.getMessage() != null ? err.getMessage() : err.toString();
            if (errorReason != null && errorReason.contains("image_base64")) {
                errorReason = IMAGE_BASE64_TRUNCATE.matcher(errorReason).replaceAll("$1...(truncated)$2");
            }
        }

        StackWalker.StackFrame callFrame = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(s -> s.skip(2).findFirst())
                .orElse(null);
        String fileLine = "";
        String callPath = "";
        if (callFrame != null) {
            String fileName = fileBaseName(callFrame.getFileName());
            int line = callFrame.getLineNumber();
            fileLine = fileName + ":" + line;
            String funcName = getFuncName(callFrame);
            callPath = fileName + ":" + funcName;
        }

        String callChain = getCallChain(3);

        String traceId = extractTraceId(otelCtx);
        if (traceId == null || traceId.isEmpty()) {
            traceId = "unknown";
        }

        org.slf4j.Logger slf4j = LoggerFactory.getLogger(serviceName);
        try (var ignored = MDC.putCloseable("trace_id", traceId)) {
            var at = slf4j.atError()
                    .setMessage("business_error")
                    .addKeyValue("service", serviceName)
                    .addKeyValue("event_id", eventIdStr)
                    .addKeyValue("trace_id", traceId)
                    .addKeyValue("error_code", code)
                    .addKeyValue("file_line", fileLine)
                    .addKeyValue("call_path", callPath)
                    .addKeyValue("call_chain", callChain)
                    .addKeyValue("error_reason", errorReason);
            if (logOpts.getUserId() != null && !logOpts.getUserId().isEmpty()) {
                at.addKeyValue("user_id", logOpts.getUserId());
            }
            if (logOpts.getArticleId() != null && !logOpts.getArticleId().isEmpty()) {
                at.addKeyValue("article_id", logOpts.getArticleId());
            }
            at.log();
        }
    }

    /**
     * 业务信息日志，字段集合与 {@code Logger.logInfo} 一致。
     */
    public void logInfo(io.opentelemetry.context.Context otelCtx,
                        String msg,
                        LogOption... opts) {
        LogOptions logOpts = new LogOptions();
        if (opts != null) {
            for (LogOption opt : opts) {
                opt.apply(logOpts);
            }
        }

        String eventIdStr = "unknown";
        try {
            long eventId = Snowflake.nextId();
            eventIdStr = String.valueOf(eventId);
        } catch (Throwable ignore) {
            // 与 Logger.doLogInfo 同款兜底
        }

        StackWalker.StackFrame callFrame = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(s -> s.skip(2).findFirst()).orElse(null);
        String position = "unknown";
        if (callFrame != null) {
            String fileName = fileBaseName(callFrame.getFileName());
            String funcName = getFuncName(callFrame);
            position = fileName + ":" + funcName;
        }

        String traceId = extractTraceId(otelCtx);
        if (traceId == null || traceId.isEmpty()) {
            traceId = "unknown";
        }

        org.slf4j.Logger slf4j = LoggerFactory.getLogger(serviceName);
        try (var ignored = MDC.putCloseable("trace_id", traceId)) {
            var at = slf4j.atInfo()
                    .setMessage("business_info")
                    .addKeyValue("service", serviceName)
                    .addKeyValue("event_id", eventIdStr)
                    .addKeyValue("trace_id", traceId)
                    .addKeyValue("position", position)
                    .addKeyValue("msg", msg);
            if (logOpts.getUserId() != null && !logOpts.getUserId().isEmpty()) {
                at.addKeyValue("user_id", logOpts.getUserId());
            }
            if (logOpts.getArticleId() != null && !logOpts.getArticleId().isEmpty()) {
                at.addKeyValue("article_id", logOpts.getArticleId());
            }
            at.log();
        }
    }

    /**
     * 致命错误日志，行为与 {@code Logger.logFatal} 对齐：抛 IllegalStateException 而非
     * {@code System.exit(1)}（Spring 化：避免绕过 SpringApplication graceful shutdown 与
     * {@code @PreDestroy}）。
     */
    public void logFatal(io.opentelemetry.context.Context otelCtx,
                         Throwable err,
                         LogOption... opts) {
        LogOptions logOpts = new LogOptions();
        if (opts != null) {
            for (LogOption opt : opts) {
                opt.apply(logOpts);
            }
        }

        String eventIdStr = "unknown";
        try {
            long eventId = Snowflake.nextId();
            eventIdStr = String.valueOf(eventId);
        } catch (Throwable ignore) {
            // 与 Logger.doLogFatal 同款兜底
        }

        String errorReason = "unknown error";
        if (err != null) {
            errorReason = err.getMessage() != null ? err.getMessage() : err.toString();
        }

        StackWalker.StackFrame callFrame = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(s -> s.skip(2).findFirst()).orElse(null);
        String fileLine = "unknown/file.go:0";
        String callPath = "unknown";
        if (callFrame != null) {
            String fileName = fileBaseName(callFrame.getFileName());
            int line = callFrame.getLineNumber();
            fileLine = fileName + ":" + line;
            String funcName = getFuncName(callFrame);
            callPath = fileName + ":" + funcName;
        }

        String callChain = getCallChain(3);

        String traceId = extractTraceId(otelCtx);
        if (traceId == null || traceId.isEmpty()) {
            traceId = "unknown";
        }

        org.slf4j.Logger slf4j = LoggerFactory.getLogger(serviceName);
        try (var ignored = MDC.putCloseable("trace_id", traceId)) {
            var at = slf4j.atError()
                    .setMessage("fatal_error")
                    .addKeyValue("service", serviceName)
                    .addKeyValue("event_id", eventIdStr)
                    .addKeyValue("trace_id", traceId)
                    .addKeyValue("file_line", fileLine)
                    .addKeyValue("call_path", callPath)
                    .addKeyValue("call_chain", callChain)
                    .addKeyValue("error_reason", errorReason);
            if (logOpts.getUserId() != null && !logOpts.getUserId().isEmpty()) {
                at.addKeyValue("user_id", logOpts.getUserId());
            }
            if (logOpts.getArticleId() != null && !logOpts.getArticleId().isEmpty()) {
                at.addKeyValue("article_id", logOpts.getArticleId());
            }
            at.log();
        }

        // H3 修复：与 Logger.doLogFatal 统一抛 FatalError（见 com.rag.common.logger.FatalError）
        throw new FatalError(
                "fatal_error logged: " + errorReason
                        + " (event_id=" + eventIdStr + ", service=" + serviceName + ")",
                err);
    }

    // ===== helper methods（独立实现，避免与 Logger 共享导致耦合）=====

    private static String getFuncName(StackWalker.StackFrame frame) {
        if (frame == null) {
            return "unknown";
        }
        String declaringClass = frame.getDeclaringClass().getName();
        String methodName = frame.getMethodName();
        if (declaringClass == null || methodName == null) {
            return "unknown";
        }
        String[] parts = declaringClass.split("\\.");
        String shortClass = parts[parts.length - 1];
        // M10 修复：旧版两个分支返回完全相同的表达式（闭包分支是死代码）。
        // Go 版闭包分支只取最后一段，Java lambda 的对应物 = 只返回 lambda 方法名。
        if (methodName.contains("$")) {
            return methodName;
        }
        return shortClass + "." + methodName;
    }

    private static String getCallChain(int skip) {
        final int maxDepth = 32;
        List<String> frames = new ArrayList<>();
        StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(s -> {
                    int count = 0;
                    int taken = 0;
                    java.util.Iterator<StackWalker.StackFrame> it = s.iterator();
                    while (it.hasNext()) {
                        StackWalker.StackFrame f = it.next();
                        if (count++ < skip) {
                            continue;
                        }
                        if (taken >= maxDepth) {
                            break;
                        }
                        taken++;
                        String fn = getFuncName(f);
                        if (isFiltered(f, fn)) {
                            continue;
                        }
                        frames.add(fn);
                    }
                    return null;
                });
        Collections.reverse(frames);
        if (frames.isEmpty()) {
            return "unknown";
        }
        return String.join(" -> ", frames);
    }

    /**
     * M10 修复：拿 declaringClass 全名比对包前缀（详见 {@link com.rag.common.logger.Logger} 同名方法）。
     */
    private static boolean isFiltered(StackWalker.StackFrame frame, String funcName) {
        String declaringClass = frame != null ? frame.getDeclaringClass().getName() : null;
        for (String prefix : FILTER_PREFIXES) {
            if (declaringClass != null && declaringClass.startsWith(prefix)) {
                return true;
            }
            if (funcName != null && funcName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static String extractTraceId(io.opentelemetry.context.Context otelCtx) {
        if (otelCtx == null) {
            return "";
        }
        Span span = Span.fromContext(otelCtx);
        SpanContext sc = span.getSpanContext();
        if (!sc.isValid()) {
            return "";
        }
        return sc.getTraceId();
    }

    private static String fileBaseName(String path) {
        if (path == null || path.isEmpty()) {
            return "unknown";
        }
        int idx = path.lastIndexOf('/');
        if (idx >= 0) {
            return path.substring(idx + 1);
        }
        int winIdx = path.lastIndexOf('\\');
        if (winIdx >= 0) {
            return path.substring(winIdx + 1);
        }
        return path;
    }
}