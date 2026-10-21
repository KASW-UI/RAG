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
 *   <li>委托重构后 {@code call_chain} 的帧数不因多一层委托而错位</li>
 * </ul>
 *
 * <p>不验证 SLF4J 输出内容（与 LoggerTest 同款限制）。
 */
class LoggerFactoryBeanTest {

    /**
     * 锁住 2026-10-21 委托重构的关键行为：<b>{@code call_chain} 的链首必须是业务调用方</b>。
     *
     * <p>委托前 Bean 路径的栈是 {@code getCallChain → Bean.logInfo → 业务调用方}，
     * 而 {@code getCallChain(3)} 跳过 3 帧 ⇒ 链首落在业务调用方的<b>上一层</b>（少一帧）。
     * 委托后多了一层 {@code Logger.doLogInfo}，跳 3 帧正好落在业务调用方。
     *
     * <p>本方法名 {@code businessCallerLayer0} 必须出现在 {@code call_chain} 里，
     * 而它的调用者（JUnit 反射层）不应出现。
     */
    @Test
    void callChainStartsAtBusinessCaller() {
        LoggerFactoryBean bean = new LoggerFactoryBean("chain-test");
        ListAppender<ILoggingEvent> appender = captureLogs();
        try {
            // 调用点必须在未被 FILTER_PREFIXES 过滤的包里，
            // 否则探针帧本身会被滤掉，「链首落点」无从断言。
            com.rag.common.verify.CallChainProbe.invokeBean(bean);
            String chain = findCallChain(appender);
            assertTrue(chain.contains("invokeBean"),
                    "call_chain 链首必须落在业务调用方；错位说明 getCallChain 的 skip "
                            + "帧数与委托层数不匹配：" + chain);
            assertFalse(chain.contains("LoggerFactoryBean"),
                    "LoggerFactoryBean 自身帧应被 FILTER_PREFIXES 过滤掉：" + chain);
            assertFalse(chain.contains("doLogBusinessErr"),
                    "委托层 Logger.doLogBusinessErr 也应被过滤掉：" + chain);
        } finally {
            detach(appender);
        }
    }

    private static ListAppender<ILoggingEvent> captureLogs() {
        ch.qos.logback.classic.Logger root =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(
                        org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        return appender;
    }

    private static void detach(ListAppender<ILoggingEvent> appender) {
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(
                org.slf4j.Logger.ROOT_LOGGER_NAME)).detachAppender(appender);
        appender.stop();
    }

    private static String findCallChain(ListAppender<ILoggingEvent> appender) {
        for (ILoggingEvent e : appender.list) {
            var kv = e.getKeyValuePairs();
            if (kv != null) {
                for (var pair : kv) {
                    if ("call_chain".equals(pair.key)) {
                        return String.valueOf(pair.value);
                    }
                }
            }
        }
        return "<no call_chain found>";
    }

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
     * H3 契约：logFatal 必须抛 {@link com.rag.common.logger.FatalError}
     * —— 既不是 {@code System.exit(1)}，也不是普通 {@link IllegalStateException}。
     * 详见 {@link com.rag.common.logger.FatalError} 类注释与 {@code LoggerTest} 的同名用例。
     */
    @Test
    void logFatalThrowsFatalErrorInsteadOfOnExit() {
        LoggerFactoryBean bean = new LoggerFactoryBean("bean-test-fatal");
        com.rag.common.logger.FatalError ex = assertThrows(
                com.rag.common.logger.FatalError.class,
                () -> bean.logFatal(Context.root(), new RuntimeException("bootstrap fail")));
        assertEquals(com.rag.common.logger.FatalError.class, ex.getClass(),
                "FatalError 必须是独立类型，不能挂在 IllegalStateException 下");
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