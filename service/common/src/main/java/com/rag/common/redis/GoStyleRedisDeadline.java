package com.rag.common.redis;

import com.rag.common.observability.GoStyleRequestDeadline;
import org.springframework.dao.QueryTimeoutException;

/**
 * <b>共享层的「Redis 期限感知」入口（G1 反馈单 2/3 · 2026-10-30 · G4 lane）。</b>
 *
 * <h2>⚠️ 结论订正（2026-10-30 15:2x · G4 本层自查）：<b>E66 是真漂移</b> ⇒ 本类的作用是「把 RAG 拉回与 Go 同形」，<b>不是</b>「比 Go 更严」</h2>
 * <p><b>Go 侧的事实（逐行读过）—— 两条<b>独立</b>的机制，结论相反，必须分开写</b>：
 * <ul>
 *   <li><b>①「派发前」Go <b>是</b>查 deadline 的</b> ⇒ ⇒ <b>Go 的 Redis 写受请求 deadline 约束</b>
 *       ⇒ ⇒ <b>E66 是真漂移</b>（RAG 侧不查 ⇒ 漂移）：
 *       <ul>
 *         <li>{@code go-zero@v1.10.2/core/stores/redis/redisclientmanager.go:32-40} 构造客户端时
 *             <b>没有设</b> {@code ContextTimeoutEnabled}（默认 {@code false}）
 *             ⇒ 但那<b>只影响 socket 层</b>（见 ②），<b>不影响</b>派发前的检查。</li>
 *         <li>{@code go-redis/v9@v9.20.0/internal/pool/pool.go:1098-1104}
 *             {@code ConnPool.waitTurn} 的 <b>fast path 无条件先查 {@code ctx.Done()}</b>
 *             ⇒ {@code ctx.Err()} ⇒ <b>命令根本不下发</b>。</li>
 *         <li>调用链（逐段读过）：{@code logoutlogic.go:38}
 *             {@code BizRedis.SetexCtx(l.ctx, …)} → {@code core/stores/redis/redis.go:1517-1523}
 *             {@code conn.Set(ctx, …)} → {@code string_commands.go:451-469} {@code cmdable.Set}
 *             → {@code redis.go:1434-1438} {@code Client.Process} → {@code redis.go:212-214}
 *             {@code processHook} → {@code redis.go:830} {@code baseClient.process}
 *             → {@code redis.go:961} {@code withConn} → {@code redis.go:383-400} {@code getConn}
 *             → {@code redis.go:402-403} {@code _getConn} → {@code pool.go:802-804} {@code ConnPool.Get}
 *             → {@code pool.go:807} {@code getConn} → {@code pool.go:837} {@code waitTurn}
 *             ⇒ ⇒ <b>命中 ①</b>。</li>
 *       </ul></li>
 *   <li><b>②「socket 层」Go <b>不</b>查请求 deadline</b> ⇒ ⇒ <b>那一层 RAG 与 Go 同形</b>：
 *       <ul>
 *         <li>{@code go-redis/v9@v9.20.0/redis.go:1024-1032} 的 {@code baseClient.context(ctx)}
 *             在 {@code ContextTimeoutEnabled=false} 时<b>把 ctx 换成 {@code context.Background()}</b>
 *             ⇒ {@code redis.go:968}/{@code :985} 的
 *             {@code cn.WithWriter/WithReader(c.context(ctx), …)}
 *             <b>拿到的 ctx 已无 deadline</b>
 *             ⇒ {@code internal/pool/conn.go:961-991} 的 {@code cn.deadline(ctx, timeout)}
 *             只能用<b>固定的 {@code ReadTimeout} = 3s</b>（{@code options.go:364-379}）。</li>
 *         <li>⇒ ⇒ RAG 侧的 {@code spring.data.redis.timeout: 3s} 与之<b>同形</b>
 *             ⇒ ⇒ <b>「固定 3s socket 超时」这一轴不是漂移</b>。</li>
 *       </ul></li>
 * </ul>
 * <p>⚠️ <b>本类此前的 javadoc 写「Go 的 Redis 命令 I/O 本来就不受请求 deadline 约束 ⇒ E66 不是漂移」
 * —— 那句话<b>只看了 ②、漏了 ① ⇒ 是错的</b>（2026-10-30 15:2x 由 G4 本层自查订正，
 * 见 {@code docs/DRIFT/global/vs-common.md} §4.19.5）。⇒ ⇒ <b>正确结论：E66 是真漂移</b>，
 * 本能力面正是<b>补上 Go 已有的 ①</b>（⇒ ⇒ 这<b>不是</b>「比 Go 更严」，
 * 而是<b>把 RAG 拉回与 Go 同形</b>）。</p>
 *
 * <h2>那本类为什么存在</h2>
 * <p>让 <b>RAG 的 Redis 调用在「下发命令之前」就感知请求预算</b> ⇒ 这正是 Go 已有的 ①
 * ⇒ ⇒ 本类是<b>补齐漂移</b>（同形化），<b>不是</b>凭空新增约束
 * ⇒ 具体做到哪一步，必须写清楚：
 * <ul>
 *   <li><b>本类交付的</b>：请求预算<b>已耗尽</b> ⇒ <b>不再下发新的 Redis 命令</b>
 *       （抛 {@link QueryTimeoutException}）⇒ ⇒ <b>不把一个已经死掉的请求
 *       继续挂在 Redis 上</b>。</li>
 *   <li><b>本类<b>不</b>交付的</b>：<b>「命令级 socket 超时随剩余预算收紧」</b>
 *       ⇒ ⇒ <b>本轮刻意不落</b>（<b>不是「技术上不可达」</b> —— 那句断言<b>是错的</b>，
 *       2026-10-30 订正）：
 *       <ul>
 *         <li><b>路线<b>是可达的</b></b>（本层实测，不是推测）：lettuce 的
 *             {@code io.lettuce.core.TimeoutOptions$TimeoutSource}（<b>public</b> abstract；
 *             抽象方法 {@code long getTimeout(RedisCommand<?, ?, ?>)} ＋ {@code getTimeUnit()}）
 *             ＋ {@code TimeoutOptions$Builder.timeoutSource(TimeoutSource)}
 *             ＋ {@code ClientOptions$Builder.timeoutOptions(TimeoutOptions)}
 *             ⇒ 再用 Boot 4 的 {@code LettuceClientOptionsBuilderCustomizer} 注入即可
 *             ⇒ ⇒ <b>「Lettuce 逐命令动态返回剩余 deadline」这条路走得通</b>。
 *             ⚠️ <b>customizer 的位置是关键</b>：实测 Boot 4.0.8
 *             {@code LettuceConnectionConfiguration.createClientOptions} 的字节码 ——
 *             offset 143 {@code TimeoutOptions.enabled()}（<b>硬编码无参</b>）→ offset 146
 *             {@code timeoutOptions(...)} → offset 150+ 才 apply customizer
 *             ⇒ ⇒ <b>customizer 在硬编码之后 ⇒ 可覆盖</b>。</li>
 *         <li><b>但落它会把 RAG 推到「比 Go 更严」的一侧</b>：Go 的 socket 层<b>不</b>收紧
 *             （固定 3s，见上面 ②）⇒ ⇒ 收紧 = <b>新造一条有意偏离</b>（需签字）
 *             ⇒ ⇒ 本轮<b>不落</b>（登记为「待立项」，见台账 §4.19.5）。</li>
 *       </ul></li>
 *   <li><b>「等待不超过剩余预算」的严格模式</b> ⇒ 需要把命令丢到<b>另一个线程</b>再
 *       {@code Future.get(remaining)} ⇒ ⚠️ 与 <b>事务绑定</b>（{@code RedisConnectionUtils}
 *       的 ThreadLocal 事务连接在调用线程上）和<b>线程亲和</b>冲突
 *       ⇒ ⇒ <b>本轮不落</b>，登记为「待立项」（见台账 §4.19.4 ⑥ 一）。</li>
 * </ul>
 *
 * <h2>与 Go 的差异方向</h2>
 * <p>①（派发前）：<b>本类落码后与 Go 同形</b>（Go 查、RAG 也查）⇒ <b>不产生新的有意偏离</b>。
 * <p>②（socket 层固定超时）：<b>本类不动</b> ⇒ 仍与 Go 同形（两侧都是固定 3s）⇒ <b>不产生新的有意偏离</b>。
 * <p>⚠️ <b>由此带来的登记变化</b>：本层此前按「Redis 比 Go 更严」登记的那条有意偏离候选
 * ⇒ ⇒ <b>前提已被订正（E66 是真漂移）⇒ 该登记作废</b> ⇒ 改按「补齐漂移」处理。
 * ⇒ ⇒ 订正全过程（证据链 ＋ 对转派单口径的影响）见
 * {@code docs/DRIFT/global/vs-common.md} §4.19.5。
 */
public final class GoStyleRedisDeadline {

    /**
     * 「没有预算」的哨兵（= {@link GoStyleRequestDeadline#remainingNanos()} 在载体缺席时的返回值）。
     */
    public static final long NO_BUDGET = -1L;

    private GoStyleRedisDeadline() {
    }

    /**
     * 当前线程的<b>请求剩余预算</b>（纳秒）；<b>没有载体时返回 {@link #NO_BUDGET}</b>。
     */
    public static long remainingNanos() {
        return GoStyleRequestDeadline.remainingNanos();
    }

    /**
     * 请求预算是否<b>已耗尽</b>。
     *
     * <p>⚠️ <b>「没有载体」不算耗尽</b>（返回 {@code false}）——
     * 那对应 Go 侧「ctx 没有 deadline」⇒ ⇒ 行为**原样放行**（不凭空加约束）。
     */
    public static boolean isExhausted() {
        long remaining = remainingNanos();
        return remaining >= 0L && remaining <= 0L;
    }

    /**
     * 预算耗尽 ⇒ <b>抛异常，让调用方在「下发命令之前」就失败</b>。
     *
     * <p>⚠️ <b>为什么抛 {@link QueryTimeoutException} 而不是自定义异常</b>：
     * 它是 Spring 的<b>标准</b>超时异常（{@code org.springframework.dao}）⇒
     * 域侧现有的 {@code catch (Exception e)} 逻辑<b>不用改一行</b>就能接住
     * ⇒ ⇒ 对外错误面（错误码 / 错误文案）与 Go 侧<b>保持一致</b>。
     *
     * @param operation 被拦下的操作名（进消息，便于排查）
     */
    public static void assertNotExhausted(String operation) {
        long remaining = remainingNanos();
        if (remaining >= 0L && remaining <= 0L) {
            throw new QueryTimeoutException(
                    "redis command skipped: request deadline exhausted (op=" + operation + ")");
        }
    }
}
