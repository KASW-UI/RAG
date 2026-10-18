package com.rag.common.spring;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.rag.common.logger.WithArticleID;
import com.rag.common.logger.WithUserID;
import io.opentelemetry.context.Context;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LoggerFactoryBean 关键契约验证：
 * <ul>
 *   <li>Bean 与 Logger.GLOBAL 独立——构造时 serviceName 与 static GLOBAL 解耦</li>
 *   <li>空 serviceName 抛 IllegalStateException</li>
 *   <li>logInfo / logBusinessErr 不抛</li>
 *   <li>logFatal 抛 IllegalStateException 而非 System.exit(1)</li>
 *   <li>user_id / article_id 空字段守卫（与 LoggerTest DRIFT-002 同款）</li>
 * </ul>
 *
 * <p>不验证 SLF4J 输出内容（与 LoggerTest 同款限制）。
 */
class LoggerFactoryBeanTest {

    @Test
    void constructorRejectsEmptyServiceName() {
        assertThrows(IllegalStateException.class, () -> new LoggerFactoryBean(null));
        assertThrows(IllegalStateException.class, () -> new LoggerFactoryBean(""));
    }

    @Test
    void getServiceNameReturnsInjectedValue() {
        LoggerFactoryBean bean = new LoggerFactoryBean("admin-rpc");
        assertEquals("admin-rpc", bean.getServiceName());
    }

    @Test
    void logInfoDoesNotThrowWithoutCtx() {
        LoggerFactoryBean bean = new LoggerFactoryBean("bean-test");
        assertDoesNotThrow(() -> bean.logInfo(Context.root(), "test info"));
    }

    @Test
    void logBusinessErrDoesNotThrowWithoutCtx() {
        LoggerFactoryBean bean = new LoggerFactoryBean("bean-test");
        assertDoesNotThrow(() -> bean.logBusinessErr(Context.root(), 500, new RuntimeException("boom")));
    }

    @Test
    void logBusinessErrHandlesNullError() {
        LoggerFactoryBean bean = new LoggerFactoryBean("bean-test");
        assertDoesNotThrow(() -> bean.logBusinessErr(Context.root(), 400, null));
    }

    @Test
    void logInfoAcceptsOptions() {
        LoggerFactoryBean bean = new LoggerFactoryBean("bean-test");
        assertDoesNotThrow(() -> bean.logInfo(Context.root(), "msg",
                WithUserID.of("u1"), WithArticleID.of("a1")));
    }

    /**
     * Bean 路径与 LoggerTest DRIFT-002 同款：空 user_id / article_id 不输出字段。
     */
    @Test
    void logInfoOmitsEmptyUserIdAndArticleId() throws Exception {
        LoggerFactoryBean bean = new LoggerFactoryBean("bean-test-omits");
        ListAppender<ILoggingEvent> appender = attachAppender("bean-test-omits");
        try {
            bean.logInfo(Context.root(), "no-options-msg");
            ILoggingEvent event = lastEvent(appender);
            assertFalse(containsKey(event, "user_id"));
            assertFalse(containsKey(event, "article_id"));
        } finally {
            detachAppender("bean-test-omits", appender);
        }
    }

    @Test
    void logBusinessErrOmitsEmptyUserIdAndArticleId() throws Exception {
        LoggerFactoryBean bean = new LoggerFactoryBean("bean-test-omits-err");
        ListAppender<ILoggingEvent> appender = attachAppender("bean-test-omits-err");
        try {
            bean.logBusinessErr(Context.root(), 400, new RuntimeException("boom"));
            ILoggingEvent event = lastEvent(appender);
            assertFalse(containsKey(event, "user_id"));
            assertFalse(containsKey(event, "article_id"));
        } finally {
            detachAppender("bean-test-omits-err", appender);
        }
    }

    @Test
    void logInfoIncludesNonEmptyUserId() throws Exception {
        LoggerFactoryBean bean = new LoggerFactoryBean("bean-test-includes");
        ListAppender<ILoggingEvent> appender = attachAppender("bean-test-includes");
        try {
            bean.logInfo(Context.root(), "msg", WithUserID.of("u-42"));
            ILoggingEvent event = lastEvent(appender);
            assertTrue(containsKey(event, "user_id"));
        } finally {
            detachAppender("bean-test-includes", appender);
        }
    }

    /**
     * Spring 化契约：logFatal 必须抛 IllegalStateException 而非 System.exit(1)。
     */
    @Test
    void logFatalThrowsInsteadOfOnExit() {
        LoggerFactoryBean bean = new LoggerFactoryBean("bean-test-fatal");
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> bean.logFatal(Context.root(), new RuntimeException("bootstrap fail")));
        assertTrue(ex.getCause() instanceof RuntimeException);
        assertEquals("bootstrap fail", ex.getCause().getMessage());
        assertTrue(ex.getMessage().contains("fatal_error logged"));
        assertTrue(ex.getMessage().contains("service=bean-test-fatal"));
    }

    // ===== helper =====

    private static ListAppender<ILoggingEvent> attachAppender(String loggerName) {
        ch.qos.logback.classic.Logger lbLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(loggerName);
        // 让 logger 输出到 INFO/ERROR 级别才能被 ListAppender 捕获
        if (lbLogger.getLevel() == null || lbLogger.getLevel().toInt() > Level.INFO.toInt()) {
            lbLogger.setLevel(Level.INFO);
        }
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
}