package com.rag.common.persistence;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link SqlTimeoutStatusTranslator} 的单元测试 —— B-1 缓解的第二半。
 *
 * <p><b>本测试真正锁住的东西</b>：{@code Invocation.proceed()} 抛的是
 * {@link InvocationTargetException} 而不是 {@link SQLException}（反射调用包装），
 * 而 MyBatis 的拦截器签名允许写 {@code catch (SQLException)} 且<b>编译器不会报错</b> ——
 * 那样的翻译器会静默失效、线上表现为「B-1 缓解上线了但错误码还是 5001」。
 */
class SqlTimeoutStatusTranslatorTest {

    private final SqlTimeoutStatusTranslator translator = new SqlTimeoutStatusTranslator();

    /**
     * 造一个 proceed() 后目标 {@code Executor.query} 会抛出给定异常的 Invocation。
     *
     * <p>与 {@code DeadlineQueryTimeoutInjectorTest} 同理，必须用 {@code mock(Executor)}：
     * MyBatis 3.5.19 的 {@code Invocation} 构造器只接受 declaring class 在其白名单里的方法
     * （{@code Executor} 是白名单成员，自定义同名类不是）。
     *
     * <p>关键点：{@code proceed()} 走的是<b>真实反射</b>，所以业务方法声明的
     * {@code SQLException} 必然被包成 {@link InvocationTargetException} ——
     * 这正是生产环境里翻译器必须处理的形态，测试不能绕过它。
     */
    private static Invocation invocationThrowing(Throwable toThrow) throws Exception {
        Executor executor = mock(Executor.class);
        if (toThrow instanceof RuntimeException re) {
            when(executor.query(any(), any(), any(), any())).thenThrow(re);
        } else {
            when(executor.query(any(), any(), any(), any())).thenThrow((SQLException) toThrow);
        }
        Method method = Executor.class.getMethod("query",
                MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class);
        return new Invocation(executor, method, new Object[]{null, null, null, null});
    }

    private static SQLException pgCanceled() {
        return new SQLException("ERROR: canceling statement due to user request", "57014");
    }

    @Test
    @DisplayName("57014 经 InvocationTargetException 包装后仍能翻译成 DEADLINE_EXCEEDED")
    void targetExceptionWrappingIsTraversed() throws Exception {
        Invocation invocation = invocationThrowing(pgCanceled());

        StatusRuntimeException thrown = assertThrows(StatusRuntimeException.class,
                () -> translator.intercept(invocation));

        assertEquals(Status.Code.DEADLINE_EXCEEDED, thrown.getStatus().getCode());
    }

    @Test
    @DisplayName("多层包装（反射 → 自定义 → 中间层）也能找到 57014")
    void deeplyNestedCauseChainIsTraversed() throws Exception {
        Invocation invocation = invocationThrowing(
                new RuntimeException("outer", new IllegalStateException("middle", pgCanceled())));

        StatusRuntimeException thrown = assertThrows(StatusRuntimeException.class,
                () -> translator.intercept(invocation));

        assertEquals(Status.Code.DEADLINE_EXCEEDED, thrown.getStatus().getCode());
    }

    @Test
    @DisplayName("其它 SQLState（如表不存在 42S02）原样上抛，不得被误判成超时")
    void otherSqlStatePassesThrough() throws Exception {
        SQLException other = new SQLException("relation does not exist", "42S02");
        Invocation invocation = invocationThrowing(other);

        Throwable thrown = assertThrows(Throwable.class, () -> translator.intercept(invocation));

        assertTrue(thrown instanceof InvocationTargetException,
                "非 57014 的异常绝不能被翻译成 DEADLINE_EXCEEDED —— 否则数据库故障会伪装成超时");
        assertEquals("42S02",
                ((SQLException) ((InvocationTargetException) thrown).getTargetException()).getSQLState());
    }

    @Test
    @DisplayName("正常路径原样返回结果")
    void happyPathReturnsResult() throws Throwable {
        Executor executor = mock(Executor.class);
        List<Object> rows = List.of("a", "b");
        when(executor.query(any(), any(), any(), any())).thenReturn(rows);
        Method method = Executor.class.getMethod("query",
                MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class);

        Object result = translator.intercept(new Invocation(executor, method, new Object[]{null, null, null, null}));

        assertSame(rows, result);
    }

    @Test
    @DisplayName("超长 cause 链能遍历完（不会栈溢出 / 死循环）")
    void veryLongCauseChainTerminates() {
        Throwable head = new SQLException("bottom", "08006");
        for (int i = 0; i < 2_000; i++) {
            head = new RuntimeException("layer-" + i, head);
        }

        assertFalse(SqlTimeoutStatusTranslator.isStatementTimeout(head));
        assertTrue(SqlTimeoutStatusTranslator.isStatementTimeout(
                new RuntimeException("deep", wrapRidiculouslyDeep(pgCanceled()))));
    }

    private static Throwable wrapRidiculouslyDeep(Throwable bottom) {
        Throwable head = bottom;
        for (int i = 0; i < 2_000; i++) {
            head = new RuntimeException("layer-" + i, head);
        }
        return head;
    }

    @Test
    @DisplayName("isStatementTimeout 对 null 与无 SQLState 的 SQLException 判定为 false")
    void recognizerEdgeCases() {
        assertFalse(SqlTimeoutStatusTranslator.isStatementTimeout(null));
        assertTrue(SqlTimeoutStatusTranslator.isStatementTimeout(pgCanceled()));
        assertFalse(SqlTimeoutStatusTranslator.isStatementTimeout(new SQLException("no state", (String) null)));
    }

    @Test
    @DisplayName("非 SQLException 的同状态异常不误判（必须靠 SQLState，不能靠 message 文本）")
    void nonSqlExceptionWith57014TextIsNotMisjudged() {
        RuntimeException looksCanceled = new RuntimeException("canceling statement due to user request");

        assertFalse(SqlTimeoutStatusTranslator.isStatementTimeout(looksCanceled),
                "不同驱动的文案不统一，按文本判定必然误伤");
    }

    @Test
    @DisplayName("未被翻译的异常保持原类型，不被吞掉也不被改写")
    void untouchedExceptionIdentityIsPreserved() throws Exception {
        Invocation invocation = invocationThrowing(new SQLException("x", "42S02"));

        Throwable thrown = assertThrows(Throwable.class, () -> translator.intercept(invocation));

        assertSame(InvocationTargetException.class, thrown.getClass());
    }
}