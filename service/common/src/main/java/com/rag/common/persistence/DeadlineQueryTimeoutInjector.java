package com.rag.common.persistence;

import com.rag.common.observability.RpcInterceptor.DeadlineState;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * <p>B-1 缓解（第一半）：把当前 rpc 的 deadline 落到每条 SQL 的 {@code Statement.setQueryTimeout} 上。
 *
 * <p><b>Go 出处</b>：Go 侧靠 {@code context.WithTimeout} + {@code database/sql} 的 ctx-aware 查询。
 * {@code sql.DB.QueryContext} 在 ctx 到点时调 driver 的 {@code cancel()}，pgx / lib/pq 据此向 PG
 * 发 <b>CancelRequest</b>，查询在服务端立刻停止、<b>连接照常回到池里可用</b>。
 * 整条链路不需要任何额外配置，是语言级语义。
 *
 * <p><b>Java 侧为什么缺这一环</b>：Java 只有 {@code Future.cancel(true)} → {@code Thread.interrupt()}，
 * 而 pgjdbc <b>不响应线程中断</b>。2026-10-21 的行为级实测（{@code tools/difftest/probe-jdbc-cancel.java}）：
 * 对一条等锁查询在第 1000ms 调 {@code interrupt()}，查询<b>照常正常返回</b>，耗时 11014ms
 * —— 比不打断的 10022ms 还长 1s，说明 interrupt 连「让查询稍早结束」都没做到。
 * 字节码侧同样如此：MyBatis {@code BaseExecutor} / {@code SimpleExecutor} 与
 * pgjdbc {@code QueryExecutorImpl} 对 {@code isInterrupted} 的命中数均为 <b>0</b>。
 *
 * <p><b>本拦截器做什么</b>：拦 {@link StatementHandler#prepare}，在其返回 Statement 之后
 * 按 {@link DeadlineState#remainingMillis()} 设置超时。pgjdbc 收到 {@code setQueryTimeout(n)}
 * 会起 {@code StatementCancelTimerTask}，到点发 CancelRequest —— <b>与 Go 同一条服务端语义，
 * 且连接保可用</b>（对比 {@code socketTimeout}：那是 socket 读超时，连接会报废，语义相反）。
 *
 * <p><b>为什么不用全局 {@code mybatis-plus.configuration.default-statement-timeout}</b>：
 * 那是一个<b>静态</b>值，对不上每个请求各自的 deadline；实测用 2 注入时，
 * worker 的业务异常落在请求发起 +2.06s（确实生效），但它同时把所有 DB 错误压成同一个超时值，
 * 与链路上的多层超时无法对齐。逐请求注入才能保证「SQL 超时时刻 ≈ rpc deadline 时刻」。
 *
 * <p><b>无 deadline 时不注入</b>：{@link DeadlineState#remainingMillis()} 返回 {@link Long#MAX_VALUE}
 * 表示本线程没有 deadline（定时任务、MQ 消费、启动期建表等），此时<b>完全不动</b>，
 * SQL 行为与引入本类之前逐字一致。
 *
 * <p><b>秒数取整</b>：向上取整并保底 1 秒。向下取整会让剩余 1999ms 的查询只给 1s，
 * 白白砍掉近 1s；向上取整最坏是「SQL 比 deadline 晚 ≤1s 结束」，而 rpc 层自身的超时
 * 会先于该时刻把调用关闭，客户端可见行为不受影响。
 *
 * @see SqlTimeoutStatusTranslator 取消异常到 gRPC status 的翻译（本类的下游）
 */
@Intercepts(@Signature(
        type = StatementHandler.class,
        method = "prepare",
        args = {Connection.class, Integer.class}))
public class DeadlineQueryTimeoutInjector implements Interceptor {

    private static final Logger LOG = LoggerFactory.getLogger(DeadlineQueryTimeoutInjector.class);

    /** {@link DeadlineState} 未设置 deadline 时 {@code remainingMillis()} 的返回值。 */
    private static final long NO_DEADLINE = Long.MAX_VALUE;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object prepared = invocation.proceed();
        if (prepared instanceof Statement statement) {
            applyQueryTimeout(statement);
        }
        return prepared;
    }

    /**
     * 按当前线程的剩余 deadline 设置 SQL 超时；没有 deadline 或已过期时保底 1 秒。
     *
     * <p>已过期（remaining ≤ 0）时仍设 1 秒而不是不设：那种情况下
     * {@code RpcInterceptor} 的超时调度线程已经到点，会先把 call 关掉；
     * 这里设 1 秒是为了让<b>正在执行</b>的 SQL 也有机会尽快被 CancelRequest 掐掉，
     * 而不是任其挂到查询自行结束（那正是 B-1 要消除的行为）。
     *
     * <p>{@code setQueryTimeout} 是 checked 异常（驱动不支持时抛
     * {@code SQLFeatureNotSupportedException}）。这里<b>捕获并记 WARN</b>而不是向上抛：
     * 设超时是纯增强，失败时应当退回「SQL 不设超时」这一原有行为（B-1 依旧存在，但绝不
     * 因为增强失败而把原本能跑成功的查询搞挂）。
     * 但**不能静默** —— 静默吞掉正是 §3.3 反复出问题的地方，届时会表现为
     * 「B-1 缓解上线了却毫无效果」且无任何线索。
     */
    private void applyQueryTimeout(Statement statement) {
        long remaining = DeadlineState.remainingMillis();
        if (remaining == NO_DEADLINE) {
            return;
        }
        int seconds = remaining <= 0
                ? 1
                : (int) Math.max(1L, Math.ceil(remaining / 1000.0));
        try {
            statement.setQueryTimeout(seconds);
        } catch (SQLException e) {
            LOG.warn("JDBC 驱动拒绝 setQueryTimeout({}s)，该语句退回无超时模式（B-1 缓解对这条语句失效）: {}",
                    seconds, e.toString());
        }
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // 本拦截器无配置项：所有行为都由线程上的 DeadlineState 决定。
    }
}