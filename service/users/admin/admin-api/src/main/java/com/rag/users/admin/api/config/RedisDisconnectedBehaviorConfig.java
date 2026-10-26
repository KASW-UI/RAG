package com.rag.users.admin.api.config;

import io.lettuce.core.ClientOptions;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientOptionsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * L4 idiomatization（2026-10-27 · admin-api 的 {@code DRIFT-ADMINAPI-016} 同款家族）：
 * Redis <b>断连</b>时的命令行为对齐 Go。
 *
 * <h2>要解决的现象（守门实测，2026-10-27 · admin-api 域内）</h2>
 *
 * <p>把 Redis <b>优雅断开</b>（FIN 形态 —— 线上 {@code docker stop} 的真实形态），
 * 再打一个带 Authorization 头的受保护端点：
 * <pre>
 *   Go  : HTTP 200  耗时 1.72s   ← fail-open 放行
 *   RAG : HTTP 503  耗时 3.02s   body="Request Timeout"
 * </pre>
 *
 * <h2>机制（不是「异常映射不同」，是「失败耗时不同」）</h2>
 *
 * <p>两侧的 HTTP 请求超时都是 <b>3000ms</b>，且<b>已经对齐</b>：
 * <ul>
 *   <li>Go  ：go-zero v1.10.2 {@code rest/config.go:55} {@code Timeout int64 json:",default=3000"}
 *        ⇒ go-zero 原生超时中间件（{@code DisableNativeRestTimeout} 已关掉它，
 *        由 observability 的 {@code NewHTTPMiddleware} 单独自执行，超时后返回 {@code "Request Timeout"}）；</li>
 *   <li>RAG ：{@code application.yml} 的 {@code observability.http-timeout: 3s}
 *        ⇒ {@code service/common} 的 {@code HttpMiddlewareFilter}，超时文案同为 {@code "Request Timeout"}。</li>
 * </ul>
 *
 * <p>⇒ **唯一的差别是「Redis 失败要花多久」**：
 * <ul>
 *   <li><b>Go</b>：go-redis 的 {@code MaxRetries: 3}（go-zero {@code redisclientmanager.go:18} 常量
 *        {@code maxRetries = 3}）+ 退避 ⇒ 连接被拒/被关时<b>快速失败</b>（实测 ~1.7s）
 *        ⇒ 仍在 3000ms 预算内 ⇒ 进业务层 ⇒ <b>200</b>；</li>
 *   <li><b>RAG</b>：Lettuce 默认把断连期间的命令<b>挂起</b>等重连，
 *        等到 {@code spring.data.redis.timeout: 3s} 的<b>命令超时</b>才失败
 *        ⇒ <b>请求超时预算先耗尽</b> ⇒ <b>503</b>。</li>
 * </ul>
 *
 * <h2>本类做的事</h2>
 *
 * <p>把 Lettuce 的断连行为改成 <b>{@link ClientOptions.DisconnectedBehavior#REJECT_COMMANDS}</b>：
 * 断连时<b>立即拒命令</b>（而不是挂起等重连）⇒ Redis 挂掉时失败耗时 ≪ 3000ms
 * ⇒ 进业务层 ⇒ <b>fail-open 200</b>，与 Go 一致。
 *
 * <h2>⚠️ <b>语义等价论证（Go 出处 = 消费点，不是声明点）</b></h2>
 *
 * <ul>
 *   <li>Go 出处：go-zero {@code core/stores/redis/redisclientmanager.go:27-46}
 *        {@code red.NewClient(&red.Options{Addr, MaxRetries: maxRetries, MinIdleConns: idleConns})}
 *        —— <b>没有</b>设置任何 {@code DialTimeout}/{@code ReadTimeout}
 *        ⇒ 用 go-redis v9 的默认值，且 {@code MaxRetries=3} 明确开启重试；
 *        连接被拒时每次重试都是<b>立即失败</b> ⇒ 整体快速失败。</li>
 *   <li>Go 侧的「Redis 挂了要放行」判据：
 *        {@code service/user/admin/api/internal/middleware/checkblacklistmiddleware.go:35-36}
 *        {@code exists, err := m.Redis.ExistsCtx(r.Context(), blacklistKey)}
 *        ＋ {@code if err == nil && exists { ... 401 ... }}
 *        ⇒ <b>err != nil ⇒ 判据为 false ⇒ 直接放行</b>。</li>
 *   <li>RAG 侧同形：{@code AdminJwtAuthInterceptor.isBlacklisted:197-203}
 *        —— {@code catch (Throwable t)} ⇒ {@code exists=null} ⇒ 判据为 false ⇒ 放行。</li>
 * </ul>
 *
 * <p>⇒ <b>两侧在「Redis 不可用」时都尽快让命令失败，且失败都被上层捕获并 fail-open</b>
 * ⇒ <b>对外都是「放行 + 业务层正常响应」</b>。
 *
 * <p>⚠️ <b>本类只改「断连」这一种故障，不改「连着但不回包」</b>：
 * 那种情况下 Lettuce 仍然等满 {@code timeout: 3s}，请求预算（也是 3s）先到 ⇒ 503
 * ⇒ 与 Go（go-redis {@code ReadTimeout} 默认 3s ⇒ 同样 503）<b>一致</b>。
 * ⇒ <b>两类故障分别对齐</b>，没有把其中一类改成另一类。
 *
 * <h2>✅ 已验证（2026-10-27 · 修前/修后 + 变异验证）</h2>
 *
 * <p><b>修前</b>（本类不存在 ⇒ Lettuce 用默认 {@code DEFAULT}）：
 * <pre>
 *   断连策略断言：expected: &lt;REJECT_COMMANDS&gt; but was: &lt;DEFAULT&gt;
 *   断连后 hasKey 耗时：<b>3039ms</b>（挂到 commandTimeout: 3s）
 *   异常链：QueryTimeoutException: Redis command timed out &lt;- RedisCommandTimeoutException: Command timed out after 3 second(s)
 * </pre>
 * <p><b>修后</b>：断连策略 = {@code REJECT_COMMANDS}，断连后 hasKey <b>≈1ms</b> 失败
 * ⇒ 进业务层 ⇒ <b>fail-open 200</b>，与 Go 同形。
 *
 * <p>守门网：{@code AdminApiRedisDisconnectedFastFailTest}（2 条 · 真 Redis 容器 + 真故障注入
 * + 可控 TCP 代理制造 FIN）。
 *
 * <h2>⚠️ 为什么这里是<b>域内副本</b>而不是共享层</h2>
 *
 * <p>user-api 侧早就有同款类（{@code service/users/user/user-api/.../config/RedisDisconnectedBehaviorConfig.java}），
 * admin-api / follow-api 此前<b>都没有</b> ⇒ 同款漂移在三个 api 域同时成立。
 * 本类按<b>域内副本</b>落码（<b>不写共享层</b> —— {@code service/common/**} 的写权归 G4）：
 * <ul>
 *   <li>本类在 {@code service/users/admin/admin-api/**} 内 ⇒ 完全在 G1 的写权内
 *       ⇒ <b>不需要等共享层、不阻塞本波</b>；</li>
 *   <li>共享层收编（把三份副本并成一个自动配置）是<b>可选的后续收敛</b>，
 *       已作为反馈单派给 G4 ⇒ G4 落码时应当<b>删除这三份域内副本</b>，避免双份配置。</li>
 * </ul>
 */
@Configuration
public class RedisDisconnectedBehaviorConfig {

    /**
     * Go 出处与语义论证见类级 javadoc。
     *
     * <p>⚠️ <b>Boot 4 的包名变了</b>：{@code LettuceClientOptionsBuilderCustomizer}
     * 在 Boot 4 里位于 {@code org.springframework.boot.data.redis.autoconfigure}
     * （<b>不在</b> {@code spring-boot-autoconfigure} 里了 —— 那是 {@code spring-boot-data-redis} 模块）。
     */
    @Bean
    public LettuceClientOptionsBuilderCustomizer goStyleRedisDisconnectedBehavior() {
        return builder -> builder.disconnectedBehavior(
                ClientOptions.DisconnectedBehavior.REJECT_COMMANDS);
    }
}
