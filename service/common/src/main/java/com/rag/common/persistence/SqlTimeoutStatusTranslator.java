package com.rag.common.persistence;

import io.grpc.Status;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.sql.SQLException;
import java.util.Properties;

/**
 * <p>B-1 缓解（第二半）：把「SQL 被 deadline 取消」翻译成 gRPC 的 {@code DEADLINE_EXCEEDED}。
 *
 * <p><b>为什么必须有这一半</b>：{@link DeadlineQueryTimeoutInjector} 让 pgjdbc 发 CancelRequest 之后，
 * 驱动抛的是 {@code PSQLException: ERROR: canceling statement due to user request}，
 * SQLState = <b>57014</b>。而业务层的 DB 调用几乎都写成
 * {@code catch (Exception e) { throw Status.INTERNAL.withDescription("DB查询失败")... }} ——
 * 于是超时会被当成「数据库故障」上报。
 * 2026-10-21 的端到端实测证实了这个后果：只注入 {@code default-statement-timeout=2}
 * 而不做翻译时，RAG 的超时响应体是 {@code {"code":5001,"msg":"系统内部错误"}}，
 * 而 Go 是 {@code {"code":1015,"msg":"服务繁忙"}} —— <b>离 Go 更远了</b>。
 *
 * <p><b>Go 出处</b>：Go 侧 ctx 到点后，查询返回 {@code context.DeadlineExceeded}，
 * 经 go-zero 的错误映射成为「服务繁忙」1015。RAG 侧对应的 1015 由
 * {@code user-api} / {@code admin-api} 的 {@code mapRPCStatusToCode} 的 default 分支产出
 * （{@code DEADLINE_EXCEEDED} 未被单列，落 default → {@code CODE_SERVER_BUSY}=1015）。
 * 因此<b>本类只要产出正确的 DEADLINE_EXCEEDED，api 层无需任何改动即可与 Go 对齐</b>。
 *
 * <p><b>为什么不能翻译成 INTERNAL 再在 api 层改</b>：那是把「超时」和「数据库真的坏了」混成
 * 一个码，而 Go 侧这两者本来就不同（超时 1015 / 数据库错 5001）。翻译点必须在
 * 异常离开数据访问层之前，也就是这里。
 *
 * <p><b>拦截位置选 {@link Executor} 而非 {@code StatementHandler}</b>：CancelRequest 引发的
 * {@code SQLException} 是在<b>执行</b>阶段抛出的，{@code prepare} 阶段根本看不到它。
 * {@code Executor} 是最靠近业务调用、又能兜住整次 query/update 的位置。
 */
@Intercepts({
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class,
                        ResultHandler.class, CacheKey.class, BoundSql.class}),
        @Signature(type = Executor.class, method = "update",
                args = {MappedStatement.class, Object.class})
})
public class SqlTimeoutStatusTranslator implements Interceptor {

    /**
     * PostgreSQL 的 {@code query_canceled} SQLState。
     * 由 CancelRequest（{@code setQueryTimeout} 到点、或 pg 的 {@code pg_cancel_backend}）产生。
     */
    static final String PG_QUERY_CANCELED = "57014";

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        try {
            return invocation.proceed();
        } catch (Throwable t) {
            if (isStatementTimeout(t)) {
                throw Status.DEADLINE_EXCEEDED
                        .withDescription("sql statement canceled at rpc deadline")
                        .asRuntimeException();
            }
            throw t;
        }
    }

    /**
     * 判断异常链里是否含 SQLState 57014。
     *
     * <p><b>为什么必须遍历整条 cause 链、而不是直接 catch (SQLException)</b>：
     * MyBatis 的 {@link Invocation#proceed()} 声明的是
     * {@code throws InvocationTargetException, IllegalAccessException}，
     * 它是<b>反射调用</b>目标方法，真正的 {@code SQLException}（{@code Executor.query} 声明抛它）
     * 被包在 {@code InvocationTargetException.getTargetException()} 里。
     * 写成 {@code catch (SQLException e)} 会<b>永远匹配不上</b>，而且编译器不会报错
     * （catch 一个 Throwable 声明里可能出现的子类是合法的）——
     * 典型的 §3.3「不报错的失效」，翻译器会静默地什么都不做。
     *
     * <p>逐层遍历同时覆盖了 {@code BatchExecutor}、Spring {@code SqlSessionTemplate}
     * 等可能再裹一层的场景；防御 {@code cause == self} 的自引用环，避免异常链畸形时死循环。
     */
    static boolean isStatementTimeout(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof SQLException sql && PG_QUERY_CANCELED.equals(sql.getSQLState())) {
                return true;
            }
            if (c.getCause() == c) {
                break;
            }
        }
        return false;
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // 本拦截器无配置项：识别目标固定为 PostgreSQL 的 57014。
    }
}