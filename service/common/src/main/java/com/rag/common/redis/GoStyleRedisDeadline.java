package com.rag.common.redis;

import com.rag.common.observability.GoStyleRequestDeadline;
import org.springframework.dao.QueryTimeoutException;

/**
 * <b>共享层的「Redis 期限感知」入口（G1 反馈单 2/3 · 2026-10-30 · G4 lane）。</b>
 *
 * <h2>⚠️ 本能力面<b>刻意比 Go 更严</b>（这是 Jason 2026-10-30 明确批准的偏离）</h2>
 * <p><b>Go 侧的事实（逐行读过）</b>：
 * <ul>
 *   <li>{@code go-zero@v1.10.2/core/stores/redis/redisclientmanager.go:24-64} 构造 go-redis 客户端时
 *       <b>没有设</b> {@code ContextTimeoutEnabled}
 *       ⇒ {@code go-redis/v9@v9.20.0/options.go:168-174}（该字段默认 {@code false}）
 *       ⇒ {@code go-redis/v9@v9.20.0/redis.go:1024-1032} 的 {@code baseClient.context(ctx)}
 *       <b>把 ctx 直接换成 {@code context.Background()}</b>
 *       ⇒ ⇒ 传到 socket 层的 ctx <b>已无 deadline</b>
 *       ⇒ {@code go-redis/v9@v9.20.0/internal/pool/conn.go:961-991} 的 {@code cn.deadline(ctx, timeout)}
 *       只能用<b>固定的 {@code ReadTimeout} = 3s</b>（{@code options.go:364-379}）。</li>
 *   <li>⇒ ⇒ <b>Go 的 Redis 命令 I/O 本来就不受请求 deadline 约束</b>
 *       ⇒ ⇒ RAG 侧的 {@code spring.data.redis.timeout: 3s} 与之<b>同形</b>
 *       ⇒ ⇒ <b>E66「Redis 不感知 deadline」按 Go 的事实<b>不是漂移</b></b>。</li>
 * </ul>
 *
 * <h2>那本类为什么存在（Jason 拍板：按「理想语义」追，接受比 Go 更严）</h2>
 * <p>让 <b>RAG 的 Redis 调用感知请求预算</b> ⇒ 具体做到哪一步，必须写清楚：
 * <ul>
 *   <li><b>本类交付的</b>：请求预算<b>已耗尽</b> ⇒ <b>不再下发新的 Redis 命令</b>
 *       （抛 {@link QueryTimeoutException}）⇒ ⇒ <b>不把一个已经死掉的请求
 *       继续挂在 Redis 上</b>。</li>
 *   <li><b>本类<b>不</b>交付的</b>：<b>「命令级 socket 超时随剩余预算收紧」</b>
 *       ⇒ ⇒ <b>技术上不可达</b>（不是没做）：① Spring Data Redis 4.0.7 的
 *       {@code RedisConnection} <b>没有</b> {@code setTimeout}（逐条 javap 过：它自己只有
 *       {@code close / isClosed / isQueueing / isPipelined / openPipeline / closePipeline /
 *       getNativeConnection / getSentinelConnection} 八个方法）；
 *       ② {@code getNativeConnection()} 返回的是 <b>commands 对象</b>而不是连接
 *       （{@code LettuceConnection.getNativeConnection()} 的字节码：{@code getAsyncConnection()}）
 *       ⇒ Lettuce 的 {@code StatefulConnection.setTimeout(Duration)} <b>够不着</b>。</li>
 *   <li><b>「等待不超过剩余预算」的严格模式</b> ⇒ 需要把命令丢到<b>另一个线程</b>再
 *       {@code Future.get(remaining)} ⇒ ⚠️ 与 <b>事务绑定</b>（{@code RedisConnectionUtils}
 *       的 ThreadLocal 事务连接在调用线程上）和<b>线程亲和</b>冲突
 *       ⇒ ⇒ <b>本轮不落</b>，登记为「待立项」（见台账 §4.19.5）。</li>
 * </ul>
 *
 * <h2>与 Go 的差异方向</h2>
 * <p>本能力面让 RAG <b>比 Go 更严</b> ⇒ 按 AGENTS.md §3「差异只允许出现在表达层」，
 * 它<b>不是</b>表达层差异 ⇒ ⇒ 已作为<b>有意偏离</b>登记（待签字，落点在
 * {@code docs/Intentional_Deviations/} ⇒ <b>不归 G4 写权</b> ⇒ 转合并收口执行者）。
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
