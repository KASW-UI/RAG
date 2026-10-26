package com.rag.common.persistence;

import com.rag.common.observability.RpcInterceptor.DeadlineState;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Invocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
 * 它验证的是「拿到 Statement 之后给它设了什么超时」这段<b>纯逻辑</b>，
 * 用的是代理 / mock 的 Statement，<b>不会</b>证明三件事：
 * <ol>
 *   <li>拦截器真的被 MyBatis 注册并对 {@code StatementHandler.prepare} 生效；</li>
 *   <li>驱动真的按毫秒发出了 CancelRequest；</li>
 *   <li>业务层没有把它再吞成 INTERNAL。</li>
 * </ol>
 * 前两项由 <b>真 PostgreSQL</b> 的守门
 * {@code service/comment/comment-rpc/src/test/.../CommentSqlDeadlinePrecisionGateTest}
 * 覆盖（判据：真 PG 上「在途查询在 deadline 时刻被取消」且「过期后的事务不提交」）。
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
    @DisplayName("剩余 2s ⇒ setQueryTimeout(2)（驱动不支持毫秒级时的秒级回退路径）")
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

    // ====================================================================================
    // 以下是 E46（SQL 取消时刻的精度差）新增的用例
    // ====================================================================================

    /**
     * <p>pgjdbc ≥ 42.7 提供 {@code setQueryTimeoutMs(long)}（实现类 {@code org.postgresql.jdbc.PgStatement} 上，
     * {@code org.postgresql.PGStatement} 接口没有它 —— 实测 {@code javap}）。
     *
     * <p>本接口只用来<b>造一个「支持毫秒级」的 Statement</b>：拦截器是<b>按方法名反射探测</b>的
     * ⇒ 代理类实现了本接口 ⇒ 探测必然命中 ⇒ 可以断言「毫秒数原样传下去、秒级路径没被走」。
     */
    public interface MsTimeoutCapable {
        void setQueryTimeoutMs(long millis);
    }

    /** 记录注入器对 Statement 做了什么。 */
    private static final class StatementSpy implements java.lang.reflect.InvocationHandler {
        long millis = -1L;
        int seconds = -1;
        boolean closed = false;

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "setQueryTimeoutMs" -> millis = (Long) args[0];
                case "setQueryTimeout" -> seconds = (Integer) args[0];
                case "close" -> closed = true;
                case "toString" -> {
                    return "StatementSpy";
                }
                case "hashCode" -> {
                    return System.identityHashCode(proxy);
                }
                case "equals" -> {
                    return proxy == args[0];
                }
                default -> {
                    // 其余 Statement 方法本测试用不到
                }
            }
            return null;
        }
    }

    private static Statement spyWithMsSupport(StatementSpy spy) {
        return (Statement) Proxy.newProxyInstance(
                StatementSpy.class.getClassLoader(),
                new Class<?>[]{Statement.class, MsTimeoutCapable.class},
                spy);
    }

    /** 预热代理类生成（见 {@link #millisecondRemainingIsPassedThroughVerbatim()} 里的说明）。 */
    private static void warmUpProxy() {
        spyWithMsSupport(new StatementSpy());
    }

    @Test
    @DisplayName("🔴 驱动支持毫秒级 ⇒ 剩余毫秒数原样传下去，且不走秒级路径（取消时刻 == deadline 时刻）")
    void millisecondRemainingIsPassedThroughVerbatim() throws Throwable {
        StatementSpy spy = new StatementSpy();
        Statement statement = spyWithMsSupport(spy);
        // ⚠️ **两处一次性开销必须先跑完，否则会把「剩余毫秒数」吃掉一大截**：
        //   ① Mockito 首次建 mock（ByteBuddy 生成类）≈ 400ms；
        //   ② 首次建动态代理类 ≈ 数百 ms。
        // 它们都发生在「算 deadline → 调注入器」之间 ⇒ 剩余毫秒数会被凭空吃掉 ⇒ 断言必然失败。
        // （实测：不做预热时断言值是 822 而不是 ~1234，差值正好是这两笔开销。）
        Invocation invocation = prepareInvocationReturning(statement);
        long deadline = System.currentTimeMillis() + 1_234;

        try (DeadlineState.Scope ignored = DeadlineState.bindToCurrent(deadline)) {
            injector.intercept(invocation);
        }

        assertTrue(spy.millis > 1_000 && spy.millis <= 1_234,
                "毫秒数应原样落在 (1000, 1234] 区间，实际=" + spy.millis);
        assertEquals(-1, spy.seconds, "秒级路径不该被走（否则取消时刻最晚比 deadline 晚 999ms）");
    }

    @Test
    @DisplayName("🔴 deadline 已过 ⇒ 立即失败（SQLState 57014）且关掉语句，不发到服务端")
    void expiredDeadlineFailsImmediately() {
        StatementSpy spy = new StatementSpy();
        Statement statement = spyWithMsSupport(spy);
        long deadline = System.currentTimeMillis() - 1_000;

        SQLTimeoutException thrown;
        try (DeadlineState.Scope ignored = DeadlineState.bindToCurrent(deadline)) {
            thrown = assertThrows(SQLTimeoutException.class,
                    () -> injector.intercept(prepareInvocationReturning(statement)));
        }

        assertEquals("57014", thrown.getSQLState(),
                "必须是 57014 ⇒ 才能被 SqlTimeoutStatusTranslator 翻成 DEADLINE_EXCEEDED");
        assertTrue(spy.closed, "语句已建好但未执行 ⇒ 必须关掉，否则 MyBatis 的 finally 关不到它");
        assertEquals(-1L, spy.millis, "已过期就不该再设任何超时");
    }

    @Test
    @DisplayName("驱动不支持毫秒级 ⇒ 退回秒级向上取整（降级路径仍可用）")
    void driverWithoutMsSupportFallsBackToSeconds() throws Throwable {
        Statement statement = mock(Statement.class);
        long deadline = System.currentTimeMillis() + 1_500;

        try (DeadlineState.Scope ignored = DeadlineState.bindToCurrent(deadline)) {
            injector.intercept(prepareInvocationReturning(statement));
        }

        verify(statement).setQueryTimeout(2);
    }

    @Test
    @DisplayName("剩余大到秒级换算会溢出 ⇒ 当作「没有 deadline」处理，一个超时都不设")
    void hugeRemainingIsTreatedAsNoDeadline() throws Throwable {
        Statement statement = mock(Statement.class);
        // 远大于 int 秒上限（≈24.8 天）⇒ 秒级换算会溢出 ⇒ 必须是「不设超时」
        long deadline = System.currentTimeMillis() + (long) Integer.MAX_VALUE * 1000L + 60_000L;

        try (DeadlineState.Scope ignored = DeadlineState.bindToCurrent(deadline)) {
            injector.intercept(prepareInvocationReturning(statement));
        }

        verify(statement, never()).setQueryTimeout(anyInt());
        assertInstanceOf(Statement.class, statement);
    }
}
