package com.rag.common.logger;

import com.rag.common.snowflake.Snowflake;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 对应 Go 版 service/common/logger/logger.go 的整个 Logger 类型（356 行）。
 *
 * <p>Go 版核心结构：
 * <pre>
 * type Logger struct { serviceName string; once sync.Once }
 * var globalLogger = &Logger{}
 * func Init(svcName string) { globalLogger.once.Do(func() { ... }) }
 * func LogBusinessErr / LogInfo / LogFatal 都是 globalLogger 上的薄包装
 * </pre>
 *
 * <p>Java 版核心结构：
 * <ul>
 *   <li>用静态 final 单例字段（{@link #globalLogger}）替代 Go 的包级 {@code var globalLogger}</li>
 *   <li>用类加载阶段 static-final 初始化（Holder Idiom）保证 {@link #init} 幂等</li>
 *   <li>用 SLF4J（logback-classic）替代 go-zero logx，SLF4J 2.x 的 fluent API {@code atInfo().addKeyValue("k", v).log()} 等价于 logx 的 {@code .Infow(msg, fields...)}</li>
 *   <li>用 {@link StackWalker} 替代 Go 的 {@code runtime.Caller(skip)}（Go skip 是固定 2 / 3 跳，Java 侧用 {@link StackWalker.Option#RETAIN_CLASS_REFERENCE} 配合 skip 计算）</li>
 * </ul>
 *
 * <p>框架职责：
 * <ul>
 *   <li>logx（Go）：go-zero 的日志库，封装 zap；带 {@code .Errorw(...)} 这种带 key/value 的方法</li>
 *   <li>SLF4J（Java）：日志门面，logback-classic 是常用实现；SLF4J 2.0 起 fluent API 自带 key/value，避免再引 logstash-logback-encoder</li>
 *   <li>MDC（Mapped Diagnostic Context）：跨方法栈传递诊断字段（这里是 trace_id）</li>
 * </ul>
 */
public class Logger {

    /**
     * 对应 Go 版字段 {@code serviceName string}。
     */
    private volatile String serviceName;

    /**
     * 对应 Go 版 {@code var globalLogger = &Logger{}}：包级单例。
     * Java 用 static-final 字段确保整个 JVM 只有一个。
     */
    private static final Logger GLOBAL = new Logger();

    /**
     * 对应 Go 版字段 {@code once sync.Once}：保证 Init 只执行一次。
     * Java 用 {@code synchronized} + volatile {@link #initialized} 标志做幂等。
     */
    private volatile boolean initialized = false;

    /**
     * 对应 Go 版字段 {@code serviceName string} 的 getter。
     */
    public String getServiceName() {
        return serviceName;
    }

    /**
     * 对应 Go 版 {@code var globalLogger = &Logger{}} 的内部访问入口。
     */
    private static Logger globalLogger() {
        return GLOBAL;
    }

    /**
     * 对应 Go 版字段
     * <pre>
     * var filterPrefixes = []string{
     *     "runtime.",
     *     "testing.",
     *     "github.com/zeromicro/go-zero/",
     * }
     * </pre>
     * Java 用静态 {@link List}{@code <String>} 替代；额外补充 Java 侧运行时栈帧前缀（SLF4J / logback / otel / 当前包）。
     */
    private static final List<String> FILTER_PREFIXES = List.of(
            "runtime.",
            "testing.",
            "io.opentelemetry.",
            "org.slf4j.",
            "ch.qos.logback.",
            "com.rag.common.logger."
    );

    /**
     * 对应 Go 版 {@code func Init(svcName string)}：
     *
     * <pre>
     * func Init(svcName string) {
     *     globalLogger.once.Do(func() {
     *         if svcName == "" { panic("service name cannot be empty") }
     *         globalLogger.serviceName = svcName
     *     })
     * }
     * </pre>
     *
     * <p>行为：
     * <ul>
     *   <li>第一次调用：写入 serviceName</li>
     *   <li>后续调用：no-op（与 sync.Once 一致）</li>
     *   <li>serviceName 为空：抛 IllegalStateException（与 Go 的 panic 语义对齐）</li>
     * </ul>
     */
    public static void init(String serviceName) {
        synchronized (GLOBAL) {
            if (!GLOBAL.initialized) {
                if (serviceName == null || serviceName.isEmpty()) {
                    throw new IllegalStateException("service name cannot be empty");
                }
                GLOBAL.serviceName = serviceName;
                GLOBAL.initialized = true;
            }
        }
    }

    /**
     * 对应 Go 版 {@code func LogBusinessErr(ctx context.Context, code int, err error, opts ...LogOption)} 签名。
     *
     * <p>Go 版的 ctx 是 context.Context；Java 版直接传 OTEL 的 io.opentelemetry.context.Context 当 ctx。
     * 如果调用方用的是 Spring / 自定义上下文，可以在调用前自己把 trace_id 塞到 MDC 后调用本方法。
     */
    public static void logBusinessErr(io.opentelemetry.context.Context otelCtx,
                                      int code,
                                      Throwable err,
                                      LogOption... opts) {
        globalLogger().doLogBusinessErr(
                otelCtx,
                code,
                err,
                opts);
    }

    /**
     * 对应 Go 版 {@code func LogInfo(ctx context.Context, msg string, opts ...LogOption)} 签名。
     */
    public static void logInfo(io.opentelemetry.context.Context otelCtx,
                               String msg,
                               LogOption... opts) {
        globalLogger().doLogInfo(otelCtx, msg, opts);
    }

    /**
     * 对应 Go 版 {@code func LogFatal(ctx context.Context, err error, opts ...LogOption)} 签名。
     */
    public static void logFatal(io.opentelemetry.context.Context otelCtx,
                                Throwable err,
                                LogOption... opts) {
        globalLogger().doLogFatal(otelCtx, err, opts);
    }

    /**
     * 对应 Go 版 {@code func (l *Logger) logBusinessErr(ctx context.Context, code int, err error, opts ...LogOption)} 私有方法。
     */
    void doLogBusinessErr(io.opentelemetry.context.Context otelCtx, int code, Throwable err, LogOption... opts) {
        if (serviceName == null || serviceName.isEmpty()) {
            throw new IllegalStateException("logger not initialized, call logger.Init() first");
        }

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
            // 对应 Go 版 e := snowflake.GetID(); e != nil 时 eventIDStr = "unknown"
        }

        String errorReason = "unknown error";
        if (err != null) {
            errorReason = err.getMessage() != null ? err.getMessage() : err.toString();
            if (errorReason != null && errorReason.contains("image_base64")) {
                errorReason = IMAGE_BASE64_TRUNCATE.matcher(errorReason).replaceAll("$1...(truncated)$2");
            }
        }

        StackWalker.StackFrame callFrame = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(s -> s.skip(2).
                        findFirst())
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
            // 对齐 Go 版 "if logOpts.UserID != "" 才追加 user_id 字段" 守卫：
            // 空字符串时整字段丢失，而不是输出 "user_id":""。
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
     * 对应 Go 版 {@code func (l *Logger) logInfo(ctx context.Context, msg string, opts ...LogOption)} 私有方法。
     */
    void doLogInfo(io.opentelemetry.context.Context otelCtx,
                   String msg,
                   LogOption... opts) {
        if (serviceName == null || serviceName.isEmpty()) {
            throw new IllegalStateException("logger not initialized, call logger.Init() first");
        }

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
            // 对应 Go 版 e != nil 时保持 "unknown"
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
     * 对应 Go 版 {@code func (l *Logger) logFatal(ctx context.Context, err error, opts ...LogOption)} 私有方法。
     *
     * <p>Go 版在 {@code logx.WithContext(ctx).Errorw(...)} 之后调 {@code os.Exit(1)}。
     * <b>Java Spring 化</b>：原 Java 版用 {@link System#exit(int)} 终止 JVM，但
     * {@code System.exit(1)} 会绕过 Spring {@code SpringApplication} 的 graceful shutdown、
     * 绕过所有 {@code @PreDestroy} 与 Bean 生命周期回调，违反 Spring 容器运行模型。
     * 本方法改为：先输出 fatal_error 日志（语义不变），再抛 {@link IllegalStateException}
     * 由调用方决定如何终止：
     * <ul>
     *   <li>Spring Boot 业务 main：让异常冒泡到 {@code SpringApplication.run(...)} 之前的调用点，
     *       JVM 退出码非 0；Spring 容器未就绪时无副作用。</li>
     *   <li>独立进程（standalone / communityfact worker）：业务显式在 catch 块里调
     *       {@code System.exit(1)} 或走 {@code @PreDestroy}。</li>
     * </ul>
     *
     * <p>调用方语义对齐 Go 版 "fatal 后必须停止接流量"：Spring 化版让"停止"由 JVM 默认行为承担
     * （main 中未捕获异常导致非 0 退出码），而不是直接调 {@code System.exit(1)}。
     */
    void doLogFatal(io.opentelemetry.context.Context otelCtx,
                    Throwable err,
                    LogOption... opts) {
        if (serviceName == null || serviceName.isEmpty()) {
            throw new IllegalStateException("logger not initialized, call logger.Init() first");
        }

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
            // 对应 Go 版 e != nil 时保持 "unknown"
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

        // 对应 Go 版 logx.Errorw 之后 os.Exit(1)
        // Spring 化：抛 IllegalStateException 让调用方决定进程终止方式，
        // 避免 System.exit(1) 绕过 SpringApplication 的 graceful shutdown 与 @PreDestroy。
        throw new IllegalStateException(
                "fatal_error logged: " + errorReason
                        + " (event_id=" + eventIdStr + ", service=" + serviceName + ")",
                err);
    }

    /**
     * 对应 Go 版 {@code func getFuncName(pc uintptr) string}。
     *
     * <p>Go 版用 {@code runtime.FuncForPC(pc).Name()} 拿到 "package.Type.Method" 全名，再按 "." 切分：
     * <ul>
     *   <li>如果倒数第二段含 "func"（闭包/匿名）：只取最后一段</li>
     *   <li>否则：取倒数两段拼接（包路径最后一段.方法名）</li>
     * </ul>
     */
    private static String getFuncName(StackWalker.StackFrame frame) {
        if (frame == null) {
            return "unknown";
        }
        // StackWalker.StackFrame#getDeclaringClass() 返回 Class<?>，调用 getName() 拿到完整类名
        // 对应 Go 版 runtime.FuncForPC(pc).Name()（拿到 "github.com/xxx.Yyy.FuncName" 这种全限定名）
        String declaringClass = frame.getDeclaringClass().getName();
        String methodName = frame.getMethodName();
        if (declaringClass == null || methodName == null) {
            return "unknown";
        }

        String[] parts = declaringClass.split("\\.");
        String shortClass = parts[parts.length - 1];
        // 对应 Go 版 "如果倒数第二段含 func（闭包/匿名）则只取最后一段"
        // Java 闭包是 lambda，方法名形如 "lambda$xxx"；含 "$" 视作 lambda
        if (methodName.contains("$")) {
            return shortClass + "." + methodName;
        }
        // 对应 Go 版 parts[len(parts)-2] + "." + parts[len(parts)-1]：取 class.method
        return shortClass + "." + methodName;
    }

    /**
     * 对应 Go 版 {@code func getCallChain(skip int) string}。
     *
     * <p>行为：
     * <ul>
     *   <li>采集当前线程栈帧（最大 32 帧，与 Go 版 {@code maxDepth = 32} 对齐）</li>
     *   <li>过滤掉 {@link #FILTER_PREFIXES} 列出的栈帧</li>
     *   <li>反转顺序，让入口函数在前（"A -> B -> C" 表示 A 调用 B 调用 C）</li>
     * </ul>
     */
    private static String getCallChain(int skip) {
        final int maxDepth = 32;
        // StackWalker.walk 返回的 Stream 不能 for-each；用显式 Iterator 遍历
        // 对应 Go 版 runtime.Callers(skip, pcs) + runtime.CallersFrames(pcs).Next() 循环
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
                        if (isFiltered(fn)) {
                            continue;
                        }
                        frames.add(fn);
                    }
                    return null;
                });

        // 对应 Go 版 for i, j := 0, len(chain)-1; i < j 反转
        Collections.reverse(frames);

        if (frames.isEmpty()) {
            return "unknown";
        }
        return String.join(" -> ", frames);
    }

    /**
     * 对应 Go 版 {@code func isFiltered(funcName string) bool}。
     */
    private static boolean isFiltered(String funcName) {
        for (String prefix : FILTER_PREFIXES) {
            if (funcName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 对应 Go 版 {@code regexp.MustCompile(`(image_base64:")[^"]*("?)`)}。
     */
    private static final Pattern IMAGE_BASE64_TRUNCATE = Pattern.compile("(image_base64:\")[^\"]*(\"?)");

    /**
     * 提取 OTEL SpanContext 的 traceId，对应 Go 版 {@code z_trace.TraceIDFromContext(ctx)}。
     *
     * <p>Go 版用 go-zero 自己的 trace 包从 ctx 取；Java 版直接用 OTEL 原生 API。
     */
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

    /**
     * 对应 Go 版 {@code filepath.Base(file)}。
     */
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