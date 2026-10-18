package com.rag.common.logger;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Logger 关键契约：Init 幂等、空 svcName 抛异常、logBusinessErr / logInfo 不抛、image_base64 truncate 行为。
 *
 * <p>不验证 logback 输出内容（logback 1.6 默认 pattern layout 因依赖而异），
 * 只验证不抛异常 + Init 行为。
 */
class LoggerTest {

    @AfterEach
    void reset() {
        // 重新初始化一个测试名，方便后续 test 用
        Logger.init("logger-test");
    }

    @Test
    void initThrowsOnNullServiceName() throws Exception {
        // 重置 GLOBAL 状态，模拟"首次 init"
        resetGlobalState();
        assertThrows(IllegalStateException.class, () -> Logger.init(null));
        resetGlobalState();
        assertThrows(IllegalStateException.class, () -> Logger.init(""));
    }

    @Test
    void initIsIdempotent() throws Exception {
        // 显式 resetGlobalState，避免依赖其他测试在 JVM 单例上留下的状态
        resetGlobalState();
        Logger.init("first");
        assertEquals("first", getServiceNameViaReflection());
        Logger.init("second");
        // 幂等：sync.Once 等价
        assertEquals("first", getServiceNameViaReflection());
    }

    @Test
    void logInfoDoesNotThrowWithoutCtx() {
        Logger.init("logger-test");
        assertDoesNotThrow(() -> Logger.logInfo(Context.root(), "test info"));
    }

    @Test
    void logBusinessErrDoesNotThrowWithoutCtx() {
        Logger.init("logger-test");
        assertDoesNotThrow(() -> Logger.logBusinessErr(Context.root(), 500, new RuntimeException("boom")));
    }

    @Test
    void logBusinessErrHandlesNullError() {
        Logger.init("logger-test");
        assertDoesNotThrow(() -> Logger.logBusinessErr(Context.root(), 400, null));
    }

    @Test
    void logInfoAcceptsOptions() {
        Logger.init("logger-test");
        assertDoesNotThrow(() -> Logger.logInfo(Context.root(), "msg",
                WithUserID.of("u1"), WithArticleID.of("a1")));
    }

    /**
     * DRIFT-002 修复验证：logInfo 不传 WithUserID/WithArticleID 时，日志 kv 列表里
     * 不应包含 user_id / article_id 键（对齐 Go 版 "if != '' 才追加字段" 守卫）。
     */
    @Test
    void logInfoOmitsEmptyUserIdAndArticleId() throws Exception {
        resetGlobalState();
        Logger.init("logger-test-omits");
        ListAppender<ILoggingEvent> appender = attachAppender("logger-test-omits");
        try {
            Logger.logInfo(Context.root(), "no-options-msg");
            ILoggingEvent event = lastEvent(appender);
            assertFalse(containsKey(event, "user_id"), "user_id key should not be present when unset");
            assertFalse(containsKey(event, "article_id"), "article_id key should not be present when unset");
        } finally {
            detachAppender("logger-test-omits", appender);
        }
    }

    /**
     * DRIFT-002 修复验证：logBusinessErr 不传 WithUserID/WithArticleID 时同样不出空字段。
     */
    @Test
    void logBusinessErrOmitsEmptyUserIdAndArticleId() throws Exception {
        resetGlobalState();
        Logger.init("logger-test-omits-err");
        ListAppender<ILoggingEvent> appender = attachAppender("logger-test-omits-err");
        try {
            Logger.logBusinessErr(Context.root(), 400, new RuntimeException("boom"));
            ILoggingEvent event = lastEvent(appender);
            assertFalse(containsKey(event, "user_id"));
            assertFalse(containsKey(event, "article_id"));
        } finally {
            detachAppender("logger-test-omits-err", appender);
        }
    }

    /**
     * DRIFT-002 修复验证：显式传 WithUserID 时 user_id 必须出现在日志中。
     */
    @Test
    void logInfoIncludesNonEmptyUserId() throws Exception {
        resetGlobalState();
        Logger.init("logger-test-includes");
        ListAppender<ILoggingEvent> appender = attachAppender("logger-test-includes");
        try {
            Logger.logInfo(Context.root(), "msg", WithUserID.of("u-42"));
            ILoggingEvent event = lastEvent(appender);
            assertTrue(containsKey(event, "user_id"), "user_id key should be present when explicitly set");
        } finally {
            detachAppender("logger-test-includes", appender);
        }
    }

    /**
     * Spring 化契约：logFatal 必须抛 IllegalStateException 而非 System.exit(1)，
     * 以避免绕过 SpringApplication 的 graceful shutdown 与 @PreDestroy。
     * 调用方负责进程终止（JVM 默认行为 / 业务显式 exit）。
     */
    @Test
    void logFatalThrowsInsteadOfOnExit() throws Exception {
        resetGlobalState();
        Logger.init("logger-test-fatal");
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> Logger.logFatal(Context.root(), new RuntimeException("bootstrap fail")));
        // 抛出时 cause 必须保留（业务 catch 后能拿 root cause）
        assertTrue(ex.getCause() instanceof RuntimeException);
        assertEquals("bootstrap fail", ex.getCause().getMessage());
        // message 携带 fatal 上下文供业务侧调试
        assertTrue(ex.getMessage().contains("fatal_error logged"), ex.getMessage());
        assertTrue(ex.getMessage().contains("service=logger-test-fatal"), ex.getMessage());
    }

    private static ListAppender<ILoggingEvent> attachAppender(String loggerName) {
        ch.qos.logback.classic.Logger lbLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(loggerName);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        lbLogger.addAppender(appender);
        return appender;
    }

    private static void detachAppender(String loggerName, ListAppender<ILoggingEvent> appender) {
        ch.qos.logback.classic.Logger lbLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(loggerName);
        lbLogger.detachAppender(appender);
        appender.stop();
    }

    private static ILoggingEvent lastEvent(ListAppender<ILoggingEvent> appender) {
        assertTrue(appender.list.size() > 0, "expected at least one log event");
        return appender.list.get(appender.list.size() - 1);
    }

    private static boolean containsKey(ILoggingEvent event, String key) {
        return event.getKeyValuePairs().stream().anyMatch(kv -> key.equals(kv.key));
    }

    private static String getServiceNameViaReflection() {
        try {
            java.lang.reflect.Field f = Logger.class.getDeclaredField("GLOBAL");
            f.setAccessible(true);
            Object g = f.get(null);
            java.lang.reflect.Field sn = g.getClass().getDeclaredField("serviceName");
            sn.setAccessible(true);
            return (String) sn.get(g);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void resetGlobalState() throws Exception {
        java.lang.reflect.Field f = Logger.class.getDeclaredField("GLOBAL");
        f.setAccessible(true);
        Object g = f.get(null);
        java.lang.reflect.Field sn = g.getClass().getDeclaredField("serviceName");
        sn.setAccessible(true);
        sn.set(g, null);
        java.lang.reflect.Field in = g.getClass().getDeclaredField("initialized");
        in.setAccessible(true);
        in.set(g, false);
    }
}