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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * <p>B-1 缓解（第一半）：把当前 rpc 的 deadline 落到每条 SQL 上，让 SQL 的<b>取消时刻 == deadline 时刻</b>。
 *
 * <p><b>Go 出处</b>：
 * <ul>
 *   <li><b>① 取消在途查询</b>：Go 侧 ctx-aware 查询（例：
 *       {@code service/comment/rpc/internal/model/comment.go:36}
 *       {@code m.conn.WithContext(ctx).Transaction(...)}、{@code :166} {@code m.conn.WithContext(ctx).Model(...)}）
 *       ⇒ {@code database/sql} 把 ctx 交给 driver ⇒ pgx 在 ctx 到点时向 PG 发 <b>CancelRequest</b>
 *       ⇒ 查询在<b>服务端立刻停止</b>、<b>连接照常回到池里可用</b>。</li>
 *   <li><b>② 已过期则查询直接失败</b>：Go 标准库 {@code database/sql} 的 {@code DB.conn(ctx, …)}
 *       在<b>取连接之前</b>就查 {@code ctx.Err()} ⇒ ctx 已过期 ⇒ <b>查询根本不会发到服务端</b>
 *       ⇒ 返回 {@code context.DeadlineExceeded}。</li>
 *   <li><b>③ 提交不看 deadline</b>：{@code gorm.io/gorm@v1.31.1} {@code finisher_api.go:622} 的
 *       {@code Transaction} ⇒ {@code db.Begin()} ⇒ {@code BeginTx(db.Statement.Context, opt)}（事务带 ctx），
 *       但 {@code tx.Commit()} ⇒ {@code committer.Commit()} ⇒ <b>不检查 ctx</b>
 *       ⇒ <b>Go 侧「查询全部成功 ⇒ 提交成功」，哪怕提交时 ctx 已过期</b>。</li>
 * </ul>
 *
 * <p><b>语义等价论证</b>（本类做的事与上面三条逐条对位）：
 * <ol>
 *   <li><b>取消时刻对齐（对位 ①）</b>：本类把剩余毫秒数<b>原样</b>交给驱动
 *       （pgjdbc ≥ 42.7 的 {@code setQueryTimeoutMs(long)}，见下方「为什么用反射」），
 *       ⇒ 驱动在 deadline 时刻发 CancelRequest ⇒ <b>与 Go 的 ctx 到点取消同一时刻、同一机制</b>。</li>
 *   <li><b>已过期立即失败（对位 ②）</b>：deadline 已过时本类<b>不执行 SQL</b>，直接抛
 *       {@link SQLTimeoutException}（SQLState {@code 57014}）
 *       ⇒ 与 Go「ctx.Err() != nil ⇒ 查询不发到服务端」同款
 *       ⇒ 且该 SQLState 会被 {@link SqlTimeoutStatusTranslator} 翻成 {@code DEADLINE_EXCEEDED}。</li>
 *   <li><b>不加提交前守卫（对位 ③）</b>：本类<b>刻意不做</b>「提交前查一次是否已过期、过期就回滚」。
 *       理由：Go 的 {@code tx.Commit()} <b>不查 ctx</b> ⇒ 加了这种守卫就是 <b>RAG 比 Go 更严</b>
 *       ⇒ 会把「查询全部在 deadline 前完成、只是提交时刚好过期」这一种情况从 Go 的「提交成功」改成「回滚」
 *       ⇒ <b>那是新造一条漂移，不是修漂移</b>。<br>
 *       ⇒ 正确做法是<b>让「过期」在查询层就体现</b>（对位 ①②）⇒ 事务自然回滚 ⇒ 与 Go 一致。</li>
 * </ol>
 *
 * <p><b>为什么用反射而不是直接依赖 pgjdbc 的类</b>：
 * {@code setQueryTimeoutMs(long)} 只挂在 <b>实现类</b> {@code org.postgresql.jdbc.PgStatement}
 * 上，{@code org.postgresql.PGStatement} <b>接口没有它</b>（实测 {@code javap}）；
 * 而 {@code service/common} 是被 10 个 api + 10 个 rpc 共享的库，<b>不该把驱动实现类编进它的 API 面</b>
 * ⇒ 用反射按方法名探测，<b>探测不到就退回秒精度</b>（并记 WARN，不静默）。
 *
 * <p><b>Java 侧为什么缺这一环</b>：Java 只有 {@code Future.cancel(true)} → {@code Thread.interrupt()}，
 * 而 pgjdbc <b>不响应线程中断</b>。2026-10-21 的行为级实测（{@code tools/difftest/probe-jdbc-cancel.java}）：
 * 对一条等锁查询在第 1000ms 调 {@code interrupt()}，查询<b>照常正常返回</b>，耗时 11014ms
 * —— 比不打断的 10022ms 还长 1s，说明 interrupt 连「让查询稍早结束」都没做到。
 * ⇒ 只能走驱动自己的取消通道（CancelRequest），也就是 Go 用的同一条路。
 *
 * <p><b>无 deadline 时不注入</b>：{@link DeadlineState#remainingMillis()} 返回 {@link Long#MAX_VALUE}
 * 表示本线程没有 deadline（定时任务、MQ 消费、启动期建表等），此时<b>完全不动</b>，
 * SQL 行为与引入本类之前逐字一致。
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

    /**
     * PostgreSQL 的 {@code query_canceled} SQLState。
     * 与 {@link SqlTimeoutStatusTranslator} 用<b>同一个口径</b>：本类抛的异常要能被它翻译成
     * {@code DEADLINE_EXCEEDED}，否则「已过期 ⇒ 查询立即失败」会退化成「数据库故障」。
     */
    private static final String PG_QUERY_CANCELED = "57014";

    /** pgjdbc ≥ 42.7 的毫秒级 queryTimeout 方法名（{@code PgStatement.setQueryTimeoutMs(long)}）。 */
    private static final String SET_QUERY_TIMEOUT_MS = "setQueryTimeoutMs";

    /**
     * 秒级回退路径能表达的最大剩余毫秒数。
     *
     * <p>秒级路径要把毫秒换算成 {@code int} 秒 ⇒ 剩余超过 {@code int} 秒时换算会<b>溢出</b>。
     * 正常路径上剩余最多是 rpc 超时预算（2s），不会触发；这里只是把「异常大的剩余」挡在门外，
     * 免得哪天有人把 deadline 绑成 {@link Long#MAX_VALUE} 时算出乱数秒。
     */
    private static final long MAX_TIMEOUT_MS = Integer.MAX_VALUE * 1000L;

    /** 每个 Statement 实现类探测一次「有没有毫秒级方法」；探测结果缓存（含否定结果）。 */
    private static final ConcurrentHashMap<Class<?>, Optional<Method>> MS_TIMEOUT_METHOD = new ConcurrentHashMap<>();

    /** 「驱动不支持毫秒级」这条 WARN 只打一次（否则每条 SQL 都打一行，日志会被刷爆）。 */
    private static final AtomicBoolean MS_UNSUPPORTED_WARNED = new AtomicBoolean(false);

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object prepared = invocation.proceed();
        if (prepared instanceof Statement statement) {
            applyQueryTimeout(statement);
        }
        return prepared;
    }

    /**
     * 按当前线程的剩余 deadline 设置 SQL 超时。
     *
     * <p>三条路径：
     * <ol>
     *   <li><b>没有 deadline</b> ⇒ 完全不动（SQL 行为与本类引入前逐字一致）。</li>
     *   <li><b>deadline 已过</b> ⇒ <b>不执行这条 SQL</b>，直接抛 {@code 57014}
     *       （Go 等价：{@code database/sql} 在取连接前查 {@code ctx.Err()} ⇒ 查询不会发到服务端）。</li>
     *   <li><b>还有剩余</b> ⇒ 把<b>剩余毫秒数原样</b>交给驱动 ⇒ 取消时刻 == deadline 时刻。</li>
     * </ol>
     *
     * <p>⚠️ <b>为什么「已过期」不再设 1 秒兜底</b>（旧行为）：旧实现把「剩余 ≤ 0」当成「设 1 秒」，
     * 理由是「让在途 SQL 有机会尽快被掐掉」。但那条路径下<b>语句本身还没执行</b>
     * ⇒ 设 1 秒等于<b>放行这条 SQL 再跑最多 1 秒</b>
     * ⇒ 在自动提交（无事务）场景下，那条 SQL <b>会在服务端真的执行并提交</b>
     * ⇒ 而 Go 侧此时<b>根本不会把这条 SQL 发出去</b> ⇒ 两侧对「这次操作算不算成功」的答案相反。
     */
    private void applyQueryTimeout(Statement statement) throws SQLException {
        long remaining = DeadlineState.remainingMillis();
        if (remaining == NO_DEADLINE || remaining > MAX_TIMEOUT_MS) {
            return;
        }
        if (remaining <= 0) {
            throwAlreadyExpired(statement);
        }
        if (applyMillisecondTimeout(statement, remaining)) {
            return;
        }
        applySecondTimeout(statement, remaining);
    }

    /**
     * deadline 已过：关掉语句并抛「被 deadline 取消」。
     *
     * <p><b>为什么先 {@code close()}</b>：本方法是 MyBatis {@code prepare} 的后半段
     * ⇒ 语句对象已经建好，但 {@code Executor.doQuery} 里的 {@code stmt} 变量此刻还是 {@code null}
     * ⇒ 抛异常后 MyBatis 的 {@code finally} 关不到它 ⇒ 不关就是句柄泄漏。
     */
    private void throwAlreadyExpired(Statement statement) throws SQLException {
        try {
            statement.close();
        } catch (SQLException e) {
            // 关不掉也不影响主结论：要抛的异常在下面。
            LOG.debug("关闭过期语句失败（不影响主流程）: {}", e.toString());
        }
        throw new SQLTimeoutException(
                "rpc deadline already reached before statement execution",
                PG_QUERY_CANCELED, 0);
    }

    /**
     * 毫秒精度超时（pgjdbc ≥ 42.7 的 {@code setQueryTimeoutMs(long)}）。
     *
     * @return {@code true} 表示已按毫秒精度设好；{@code false} 表示驱动不支持，调用方应退回秒级。
     */
    private boolean applyMillisecondTimeout(Statement statement, long millis) {
        Optional<Method> method = millisecondTimeoutMethod(statement.getClass());
        if (method.isEmpty()) {
            if (MS_UNSUPPORTED_WARNED.compareAndSet(false, true)) {
                LOG.warn("JDBC 驱动 {} 不支持毫秒级 queryTimeout ⇒ 退回秒级向上取整 ⇒ "
                                + "SQL 取消时刻最晚比 deadline 晚 999ms",
                        statement.getClass().getName());
            }
            return false;
        }
        try {
            method.get().invoke(statement, millis);
            return true;
        } catch (InvocationTargetException e) {
            LOG.warn("驱动拒绝毫秒级 queryTimeout({}ms)，退回秒级: {}", millis, e.getTargetException().toString());
            return false;
        } catch (ReflectiveOperationException e) {
            LOG.warn("毫秒级 queryTimeout 反射调用失败，退回秒级: {}", e.toString());
            return false;
        }
    }

    /**
     * 秒级回退：向上取整并保底 1 秒。
     *
     * <p>⚠️ 这是<b>降级路径</b>：只有驱动不支持毫秒级时才会走到
     * ⇒ 取消时刻最晚比 deadline 晚 999ms（= 秒级取整的固有误差）。
     */
    private void applySecondTimeout(Statement statement, long remaining) {
        int seconds = (int) Math.max(1L, Math.ceil(remaining / 1000.0));
        try {
            statement.setQueryTimeout(seconds);
        } catch (SQLException e) {
            LOG.warn("JDBC 驱动拒绝 setQueryTimeout({}s)，该语句退回无超时模式（B-1 缓解对这条语句失效）: {}",
                    seconds, e.toString());
        }
    }

    /**
     * 探测 Statement 实现类上有没有 {@code setQueryTimeoutMs(long)}。
     *
     * <p>结果按类缓存（含「没有」这个否定结果）⇒ 反射查找只发生一次。
     */
    private static Optional<Method> millisecondTimeoutMethod(Class<?> statementClass) {
        return MS_TIMEOUT_METHOD.computeIfAbsent(statementClass, cls -> {
            try {
                return Optional.of(cls.getMethod(SET_QUERY_TIMEOUT_MS, long.class));
            } catch (NoSuchMethodException e) {
                return Optional.empty();
            }
        });
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
