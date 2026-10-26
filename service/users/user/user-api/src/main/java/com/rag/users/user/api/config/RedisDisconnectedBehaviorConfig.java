package com.rag.users.user.api.config;

import io.lettuce.core.ClientOptions;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientOptionsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * L4 idiomatization（DRIFT-USERAPI-014 候选修法 · 2026-10-24）：Redis **断连**时的命令行为对齐 Go。
 *
 * <h2>要解决的现象（活体实测，2026-10-24）</h2>
 *
 * <p>把两侧的 Redis 容器**停掉**（⇒ connection refused），再打同一个受保护端点：
 * <pre>
 *   Go  : HTTP 200  耗时 1.72s   ← fail-open 放行
 *   RAG : HTTP 503  耗时 3.02s   body="Request Timeout"
 * </pre>
 * ⇒ **对外形状不同** ⇒ 这就是 §9 `UNRESOLVED-004`（`DRIFT-USERAPI-009②`）实测坐实的那条差异。
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
 *        {@code maxRetries = 3}）+ 退避 ⇒ 连接被拒/被关时**快速失败**（实测 ~1.7s）
 *        ⇒ 仍在 3000ms 预算内 ⇒ 进业务层 ⇒ <b>200</b>；</li>
 *   <li><b>RAG</b>：Lettuce 默认把断连期间的命令<b>挂起</b>等重连，
 *        等到 {@code spring.data.redis.timeout: 3s} 的<b>命令超时</b>才失败
 *        （实测 3.02s）⇒ <b>请求超时预算先耗尽</b> ⇒ <b>503</b>。</li>
 * </ul>
 *
 * <h2>本类做的事</h2>
 *
 * <p>把 Lettuce 的断连行为改成 <b>{@link ClientOptions.DisconnectedBehavior#REJECT_COMMANDS}</b>：
 * 断连时<b>立即拒命令</b>（而不是挂起等重连）⇒ Redis 挂掉时失败耗时 ≪ 3000ms
 * ⇒ 进业务层 ⇒ <b>fail-open 200</b>，与 Go 一致。
 *
 * <p>⚠️ <b>语义等价论证（Go 出处 = 消费点，不是声明点）</b>：
 * <ul>
 *   <li>Go 出处：go-zero {@code core/stores/redis/redisclientmanager.go:27-46}
 *        {@code red.NewClient(&red.Options{Addr, MaxRetries: maxRetries, MinIdleConns: idleConns})}
 *        —— <b>没有</b>设置任何 {@code DialTimeout}/{@code ReadTimeout}
 *        ⇒ 用 go-redis v9 的默认值，且 {@code MaxRetries=3} 明确开启重试；
 *        连接被拒时每次重试都是<b>立即失败</b> ⇒ 整体快速失败。</li>
 *   <li>语义等价论证：两侧在「Redis 不可用」时都<b>尽快让命令失败</b>，
 *        且失败都<b>被上层捕获并 fail-open</b>
 *        （Go {@code checkblacklistmiddleware.go:36} 的 {@code if err == nil && exists}；
 *        RAG {@code JwtAuthInterceptor.isBlacklisted:247} 的 {@code catch (Throwable)}）
 *        ⇒ <b>对外都是「放行 + 业务层正常响应」</b>。</li>
 * </ul>
 *
 * <p>⚠️ <b>本类只改「断连」这一种故障，不改「连着但不回包」</b>：
 * 那种情况下 Lettuce 仍然等满 {@code timeout: 3s}，请求预算（也是 3s）先到 ⇒ 503
 * ⇒ 与 Go（go-redis {@code ReadTimeout} 默认 3s ⇒ 同样 503）<b>一致</b>。
 * ⇒ <b>两类故障分别对齐</b>，没有把其中一类改成另一类。
 *
 * <h2>✅ 已验证（2026-10-24 · 双侧活体对拍 + 变异验证）</h2>
 *
 * <p><b>修前</b>（把 {@code REJECT_COMMANDS} 改回 {@code DEFAULT}）：
 * <pre>
 *   Go  : HTTP 200  耗时 1.72s   ← fail-open 放行
 *   RAG : HTTP 503  耗时 3.02s   body="Request Timeout"
 * </pre>
 * <p><b>修后</b>：
 * <pre>
 *   Go  : HTTP 200  耗时 1.72s
 *   RAG : HTTP 200  耗时 0.03s   ← 快速失败 ⇒ fail-open 放行 ⇒ 与 Go 同形
 * </pre>
 *
 * <p>守门网：{@code UserApiRedisDisconnectedFastFailTest}（2 条 · 真 Redis 容器 + 真故障注入）。
 * <b>变异验证</b>（改回 {@code DEFAULT}）⇒ 两条都红，其中耗时那条实测 <b>3017ms</b>，
 * 异常链 {@code RedisCommandTimeoutException: Command timed out after 3 second(s)}
 * ⇒ 与 live 实测的 3020ms 吻合 ⇒ 这条守卫真的在盯「快速失败」，不是假绿。
 *
 * <p>⚠️ <b>「首次建连期间会不会被误拒」这一条已实测排除</b>：
 * 活体对拍里 RAG 侧在 Redis 恢复后立刻拿到了 <b>401</b>（黑名单命中）
 * ⇒ 说明重连后命令照常下发，{@code REJECT_COMMANDS} <b>只</b>作用于「已断连」态。
 *
 * <p>⚠️ <b>跨域提示（2026-10-27 更新）</b>：本类原先<b>只</b>装在 user-api，
 * {@code admin-api} / {@code follow-api} 有<b>同款</b>黑名单中间件 ⇒ <b>同款漂移</b>。
 * <br>⇒ <b>已核实</b>（两侧逐行对照）：Go 侧
 * {@code service/user/admin/api/internal/middleware/checkblacklistmiddleware.go:35-36}
 * 与 {@code service/follow/api/internal/middleware/checkblacklistmiddleware.go:40-41}
 * 都是 {@code if err == nil && exists} ⇒ <b>err != nil ⇒ 放行</b>（fail-open）；
 * RAG 侧 {@code AdminJwtAuthInterceptor.isBlacklisted:197-203} 与
 * {@code FollowBlacklistInterceptor.isBlacklisted:150-156} 都是 {@code catch (Throwable)}
 * ⇒ 判据为 false ⇒ 放行 ⇒ <b>两侧目标形状一致（fail-open 200）</b>，
 * 差别只在 RAG 会先被 3s HTTP 预算掐成 503。
 * <br>⇒ <b>admin-api / follow-api 已各自落域内副本</b>（2026-10-27）：
 * 修前实测断连后 {@code hasKey} 挂 <b>3013ms / 3012ms</b>，
 * 修后 <b>1ms / 0ms</b> ⇒ 三域同形。
 * <br>⇒ ⚠️ 三份<b>域内副本</b>是<b>过渡形态</b>：共享层收编（并成一个自动配置）已作为反馈单派给 G4
 * ⇒ G4 落码时应当<b>删除这三份副本</b>，避免双份配置。
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
