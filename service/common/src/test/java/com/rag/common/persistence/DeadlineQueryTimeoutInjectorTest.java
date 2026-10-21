package com.rag.common.persistence;

import com.rag.common.observability.RpcInterceptor.DeadlineState;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Invocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DeadlineQueryTimeoutInjector} 的单元测试 —— B-1 缓解的第一半。
 *
 * <p><b>本测试的边界（§3.3 诚实声明）</b>：
 * 它验证的是「拿到 Statement 之后设了多少秒」这段<b>纯逻辑</b>，
 * 用的是 mock Statement，<b>不会</b>证明三件事：
 * <ol>
 *   <li>拦截器真的被 MyBatis 注册并对 {@code StatementHandler.prepare} 生效；</li>
 *   <li>{@code setQueryTimeout} 真的让 pgjdbc 向 PG 发出了 CancelRequest；</li>
 *   <li>业务层没有把它再吞成 INTERNAL。</li>
 * </ol>
 * 这三项由端到端实验 {@code tools/difftest/exp-b1-timeout-cancel.sh} 覆盖
 * （判据：RAG 的「PG 阻塞查询又存活了」必须 ≪ 12s，且响应体必须是 Go 的 1015）。
 */
class DeadlineQueryTimeoutInjectorTest {

    private final DeadlineQueryTimeoutInjector injector = new DeadlineQueryTimeoutInjector();

    /**
     * 造一个 proceed() 后会返回给定 Statement 的 Invocation。
     *
     * <p><b>为什么必须用 mock(StatementHandler) 而不是自己写个同名方法</b>：
     * MyBatis 3.5.19 的 {@code Invocation} 构造器会校验
     * {@code method.getDeclaringClass() ∈ targetClasses}（Configuration / StatementHandler /
     * Executor / … 的白名单），否则抛
     * {@code "Method 'xxx' is not supported as a plugin target"}。
     * 自己声明一个同名方法会因 declaring class 不在白名单而被拒 —— 这是第一版测试的真实报错。
     * mock 出来的 {@code method} 来自接口本身，declaring class 天然合法，
     * 且 {@code proceed()} 仍是真实的反射调用。
     */
    private static Invocation prepareInvocationReturning(Statement statement) throws Exception {
        StatementHandler handler = mock(StatementHandler.class);
        when(handler.prepare(any(), any())).thenReturn(statement);
        Method method = StatementHandler.class.getMethod("prepare", Connection.class, Integer.class);
        return new Invocation(handler, method, new Object[]{null, null});
    }

    @Test
    @DisplayName("没有 deadline 时完全不动 Statement —— 非 rpc 线程（定时任务 / MQ / 启动建表）行为不变")
    void noDeadlineLeavesStatementUntouched() throws Throwable {
        Statement statement = mock(Statement.class);

        // 不 bind 任何 deadline ⇒ remainingMillis() 返回 Long.MAX_VALUE
        injector.intercept(prepareInvocationReturning(statement));

        verify(statement, never()).setQueryTimeout(anyInt());
    }

    @Test
    @DisplayName("剩余 2s ⇒ setQueryTimeout(2)")
    void remainingTwoSecondsIsInjected() throws Throwable {
        Statement statement = mock(Statement.class);
        long deadline = System.currentTimeMillis() + 2_000;

        try (DeadlineState.Scope ignored = DeadlineState.bindToCurrent(deadline)) {
            injector.intercept(prepareInvocationReturning(statement));
        }

        // 向上取整：剩余可能是 2000~1999ms，都应给 2 秒
        verify(statement).setQueryTimeout(2);
    }

    @Test
    @DisplayName("剩余 1.5s ⇒ setQueryTimeout(2)，不因向下取整白砍半秒")
    void fractionalRemainingRoundsUp() throws Throwable {
        Statement statement = mock(Statement.class);
        long deadline = System.currentTimeMillis() + 1_500;

        try (DeadlineState.Scope ignored = DeadlineState.bindToCurrent(deadline)) {
            injector.intercept(prepareInvocationReturning(statement));
        }

        verify(statement).setQueryTimeout(2);
    }

    @Test
    @DisplayName("剩余不足 1s ⇒ 保底 1 秒（JDBC 不接受 0）")
    void subSecondRemainingFloorsToOne() throws Throwable {
        Statement statement = mock(Statement.class);
        long deadline = System.currentTimeMillis() + 100;

        try (DeadlineState.Scope ignored = DeadlineState.bindToCurrent(deadline)) {
            injector.intercept(prepareInvocationReturning(statement));
        }

        verify(statement).setQueryTimeout(1);
    }

    @Test
    @DisplayName("deadline 已过 ⇒ 仍设 1 秒，让在途 SQL 有机会被 CancelRequest 掐掉")
    void expiredDeadlineStillInjectsOneSecond() throws Throwable {
        Statement statement = mock(Statement.class);
        long deadline = System.currentTimeMillis() - 5_000;

        try (DeadlineState.Scope ignored = DeadlineState.bindToCurrent(deadline)) {
            injector.intercept(prepareInvocationReturning(statement));
        }

        verify(statement).setQueryTimeout(1);
    }

    @Test
    @DisplayName("驱动拒绝 setQueryTimeout 时降级为无超时，且不上抛")
    void driverRefusingTimeoutDoesNotBreakTheQuery() throws Throwable {
        Statement statement = mock(Statement.class);
        doThrow(new SQLFeatureNotSupportedStub())
                .when(statement).setQueryTimeout(anyInt());
        long deadline = System.currentTimeMillis() + 2_000;

        try (DeadlineState.Scope ignored = DeadlineState.bindToCurrent(deadline)) {
            // 关键断言：整个调用正常返回，说明 SQLException 被吞掉、SQL 仍可执行
            assertDoesNotThrow(() -> injector.intercept(prepareInvocationReturning(statement)));
        }
    }

    /** 用受检异常构造更麻烦，这里直接复用 SQLException 子类。 */
    private static final class SQLFeatureNotSupportedStub extends SQLException {
        SQLFeatureNotSupportedStub() {
            super("driver does not support setQueryTimeout");
        }
    }

    private static void assertDoesNotThrow(ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Throwable t) {
            throw new AssertionError("不应抛出异常，但抛了: " + t, t);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Throwable;
    }

    @Test
    @DisplayName("prepare 返回的若不是 Statement（自定义 handler）则原样放行，不做类型强转")
    void nonStatementResultPassesThrough() throws Throwable {
        StatementHandler handler = mock(StatementHandler.class);
        when(handler.prepare(any(), any())).thenReturn(null);
        Method method = StatementHandler.class.getMethod("prepare", Connection.class, Integer.class);

        Object result = injector.intercept(new Invocation(handler, method, new Object[]{null, null}));

        org.junit.jupiter.api.Assertions.assertNull(result);
    }
}