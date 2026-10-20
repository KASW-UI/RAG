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
     * 对应 Go 版 {@code var filterPrefixes = []string{...}}：
     * <pre>
     * var filterPrefixes = []string{
     *     "runtime.",
     *     "testing.",
     *     "github.com/zeromicro/go-zero/",
     * }
     * </pre>
     *
     * <p><b>M10 修复</b>：这三条在 Go 侧<b>本来就是死条目</b> ——
     * {@code getFuncName} 先用 {@code filepath.Base} / 取最后两段把包路径剥掉了，
     * 产出的字符串永远不可能以 {@code "github.com/zeromicro/go-zero/"} 开头。
     * 真正生效的只有 {@code "runtime."} 和 {@code "testing."}，因为那两条恰好是
     * <b>短名</b>（Go runtime 的函数名就叫 {@code runtime.gopanic}）。
     *
     * <p>旧 Java 版照抄了三条，又额外加了 4 条包前缀
     * （{@code io.opentelemetry.} / {@code org.slf4j.} / {@code ch.qos.logback.} /
     * {@code com.rag.common.logger.}），但 {@link #getFuncName} 返回的是<b>短类名</b>
     * （{@code "UserController.getUser"}），于是 7 条前缀<b>一条都匹配不上</b>，
     * {@link #isFiltered} 恒 false、{@code call_chain} 字段退化成未过滤的完整栈。
     *
     * <p>本表改为<b>可命中</b>的形态：每条都以 Java 的包/类全名开头，
     * 由 {@link #isFiltered} 拿 {@code StackFrame.getDeclaringClass().getName()} 去比对
     * （见该方法注释）。前三条保留 Go 原文以留存对应关系。
     */
private static final List<String> FILTER_PREFIXES = List.of(
            // —— Go 版 logger.go:53-58 原文三条 ——
            "runtime.",
            "testing.",
            "github.com/zeromicro/go-zero/",
            // —— Java 侧真正的「框架内部帧」，对应 Go 想过滤掉的 go-zero 内部帧 ——
            "java.lang.Thread",       // 线程启动帧，对应 Go 的 runtime.*
            "java.util.concurrent.",  // 线程池帧，对应 Go 的 runtime.*
            "jdk.internal.misc.",
            "io.opentelemetry.",
            "org.slf4j.",
            "ch.qos.logback.",
            "com.rag.common.logger.", // logger 自身帧
            "com.rag.common.spring."
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
     * 本方法改为：先输出 fatal_error 日志（字段与 Go 逐条一致），再抛 {@link FatalError}
     * 由调用方决定如何终止：
     * <ul>
     *   <li>Spring Boot 业务 main：让异常冒泡到 {@code SpringApplication.run(...)} 之前的调用点，
     *       JVM 退出码非 0；Spring 容器未就绪时无副作用。</li>
     *   <li>独立进程（standalone / communityfact worker）：业务显式在 catch 块里调
     *       {@code System.exit(1)} 或走 {@code @PreDestroy}。</li>
     * </ul>
     *
     * <p><b>H3 修复</b>：此前抛的是普通 {@link IllegalStateException}，会被 Spring MVC 的
     * {@code @ExceptionHandler(RuntimeException.class)} 兜底吞掉并转成 200 业务 envelope，
     * 进程带着致命状态继续接流量 —— Go 版 {@code os.Exit(1)} 绝不会发生这种事。
     * 改用 {@link FatalError} 让「不可被静默吞掉」成为类型级约束：
     * 各模块 {@code GlobalExceptionHandler} 必须显式 rethrow {@link FatalError}。
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

        // H3 修复：抛 FatalError 而不是普通 IllegalStateException。
        // 见 FatalError 类注释 —— 普通异常会被 @ExceptionHandler(RuntimeException) 吞掉，
        // 让「fatal 后必须停止接流量」退化成可选项。
        throw new FatalError(
                "fatal_error logged: " + errorReason
                        + " (event_id=" + eventIdStr + ", service=" + serviceName + ")",
                err);
    }

    /**
     * 对应 Go 版 {@code func getFuncName(pc uintptr) string}。
     *
     * <p>Go 版用 {@code runtime.FuncForPC(pc).Name()} 拿到 "package.Type.Method" 全名，再按 "." 切分：
     * <ul>
     *   <li>如果倒数第二段含 "func"（闭包/匿名）：<b>只取最后一段</b></li>
     *   <li>否则：<b>取倒数两段拼接</b>（类名.方法名）</li>
     * </ul>
     *
     * <p><b>M10 修复</b>：旧 Java 版两个分支 {@code return shortClass + "." + methodName;}
     * 完全相同，闭包分支是死代码。Java 的 lambda 不像 Go 那样有独立函数名 ——
     * lambda 被编译成<b>宿主类</b>上的 {@code lambda$xxx$0} 方法，
     * 所以「对应 Go 匿名函数只取最后一段」= 只返回 lambda 方法名，丢掉宿主类前缀。
     */
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
        // 对齐 Go 版 "如果倒数第二段含 func（闭包/匿名）则只取最后一段"：
        // Java lambda 的方法名形如 lambda$logBusinessErr$0，只返回它，不带宿主类。
        if (methodName.contains("$")) {
            return methodName;
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
                        if (isFiltered(f, fn)) {
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
     *
     * <p><b>M10 修复</b>：Go 版拿 {@code getFuncName} 的产出（短名）去比前缀。
     * Java 版若照抄就必然失配 —— {@code getFuncName} 产出 {@code "UserController.getUser"}，
     * 而 {@link #FILTER_PREFIXES} 全是 {@code "org.slf4j."} 这种<b>包前缀</b>。
     *
     * <p>因此 Java 版改为拿 {@link StackWalker.StackFrame#getDeclaringClass()} 的<b>全名</b>比对，
     * 这才是「包前缀」语义真正作用的对象；短名比对只作为补充（覆盖 Go 那种
     * {@code runtime.gopanic} 式的短名命中）。
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