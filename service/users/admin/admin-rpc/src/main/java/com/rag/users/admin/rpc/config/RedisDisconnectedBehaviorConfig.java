package com.rag.users.admin.rpc.config;

import io.lettuce.core.ClientOptions;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientOptionsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * L4 idiomatization（DRIFT-ARPC-013 · 2026-10-27 · G1 lane）：Redis <b>断连</b>时的命令行为对齐 Go。
 *
 * <h2>要解决的现象（rpc 侧特有形态：<b>不是</b> HTTP 503，是 <b>RPC 被拖到超时</b>）</h2>
 *
 * <p>本组件（admin-rpc）的 Redis 只有一个用途：<b>logout 把 JWT 写进黑名单</b>
 * （{@code AdminDomainService.logout:258-277} ⇒ {@code stringRedisTemplate.opsForValue().set(BLACKLIST_KEY_PREFIX + token, "banned", ...)}）。
 * ⇒ 与 api 侧（读黑名单、fail-open 放行）<b>机制同源、后果不同</b>：
 * api 侧的后果是 503，rpc 侧的后果是 <b>整个 RPC 撞超时</b>。
 *
 * <h2>Go 侧的形状（先读真源，不是推测）</h2>
 * <pre>
 *   Go service/user/admin/rpc/internal/logic/logoutlogic.go:35-40
 *     err := l.svcCtx.BizRedis.SetexCtx(l.ctx, blackListKey, "banned", int(expireTime))
 *     if err != nil {
 *         logger.LogBusinessErr(l.ctx, errmsg.ErrorRedisUpdate, err)
 *         return nil, status.Error(codes.Internal, "注销失败,Redis写入异常")   ← **快速失败 ⇒ INTERNAL**
 *     }
 *
 *   为什么快：go-zero v1.10.2 core/stores/redis/redisclientmanager.go
 *     · :14  maxRetries = 3
 *     · :32-39 red.NewClient(&red.Options{Addr, MaxRetries: maxRetries, MinIdleConns, ...})
 *       —— **不设** DialTimeout / ReadTimeout
 *     ⇒ 连接被拒/被关时每次重试都**立即失败** ⇒ 整体快速失败（实测 ~1.7s）
 *       ⇒ 仍在客户端 2s 预算内 ⇒ 进业务层 ⇒ **INTERNAL「注销失败,Redis写入异常」**
 * </pre>
 *
 * <h2>RAG 侧：<b>错误面本来就一致</b>，差的只是<b>到达时间</b></h2>
 * <pre>
 *   AdminDomainService.logout:266-275
 *     try { stringRedisTemplate.opsForValue().set(key, "banned", Duration.ofSeconds(ttlSeconds)); }
 *     catch (StatusRuntimeException e) { throw e; }                                  ← B-1 守卫（DEADLINE_EXCEEDED 放行）
 *     catch (Exception e) { log.error("admin logout Redis setex failed", e);        ← 原始异常进日志（与 Go 同款）
 *                           throw Status.INTERNAL.withDescription("注销失败,Redis写入异常").asRuntimeException(); }
 * </pre>
 * ⇒ <b>wire 面（status + description）逐字与 Go 一致</b> ⇒ 漂移<b>不在错误面</b>，
 * 而在「命令多久才失败」：
 * <pre>
 *   Lettuce 默认（DisconnectedBehavior.DEFAULT + autoReconnect=true）
 *     ⇒ 断连期间的命令被**排队**等重连（DefaultEndpoint:149 / :837-852）
 *     ⇒ 一直挂到 spring.data.redis.timeout: 3s 的 commandTimeout 才失败
 *     ⇒ 而本组件 rpc-timeout: 2s ⇒ **RPC 超时先到** ⇒ 客户端看到 DEADLINE_EXCEEDED（1015「服务繁忙」）
 *     ⇒ 与 Go 的 INTERNAL「注销失败,Redis写入异常」**不是同一个形状** ⇒ 可观察漂移。
 * </pre>
 *
 * <h2>本类做的事</h2>
 *
 * <p>把断连行为设成 {@link ClientOptions.DisconnectedBehavior#REJECT_COMMANDS}
 * ⇒ 断连时<b>立即拒命令</b>（{@code DefaultEndpoint:314} 直接返回
 * {@code RedisException("Currently not connected. Commands are rejected.")}）
 * ⇒ 失败耗时 ≪ 2s ⇒ 走进上面的 {@code catch (Exception e)}
 * ⇒ <b>INTERNAL「注销失败,Redis写入异常」</b> ⇒ 与 Go <b>同形</b>。
 *
 * <h2>⚠️ 本类只改「断连」这一种故障，<b>不改「连着但不回包」</b></h2>
 *
 * <p>后者（Redis 收下命令但不回包）两侧都会等满各自的读超时：
 * Go = go-redis ReadTimeout 默认 <b>3s</b> · RAG = {@code timeout: 3s}
 * ⇒ 都 ≫ 本组件的 2s rpc 预算 ⇒ <b>两侧都是 DEADLINE_EXCEEDED</b> ⇒ 已一致。
 * ⇒ <b>两类故障分别对齐</b>，没有把其中一类改成另一类。
 *
 * <h2>⚠️ 与 api 侧三份副本的关系（避免双份配置）</h2>
 *
 * <p>本类与 {@code user-api} / {@code admin-api} / {@code follow-api} 的三份同名类是<b>同款</b>。
 * <br>⇒ 共享层收编（并成一个自动配置）已作为反馈单派给 G4
 * ⇒ G4 落码时应当<b>删除域内这些副本</b>（共 5 份），避免双份配置。
 * <br>⇒ ⚠️ <b>删除前置 = 共享层那份先合进各域的树</b>，别提前删。
 *
 * <h2>Go 出处 = 消费点，不是声明点（AGENTS.md §3.3.1）</h2>
 *
 * <ul>
 *   <li>Go：{@code service/user/admin/rpc/internal/logic/logoutlogic.go:35-40}（错误出口）
 *        ＋ {@code core/stores/redis/redisclientmanager.go:14,32-39}（快速失败的来源）</li>
 *   <li>RAG：{@code AdminDomainService.logout:258-277}（catch 分支）
 *        ＋ {@code io.lettuce.core.protocol.DefaultEndpoint:149,314,837-852}（排队 vs 立即拒）</li>
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
     * <br>⇒ 依赖没引对时这个类型<b>整个不存在</b>，而应用照常启动 ⇒ 静默失效（AGENTS.md §3.3）。
     */
    @Bean
    public LettuceClientOptionsBuilderCustomizer goStyleRedisDisconnectedBehavior() {
        return builder -> builder.disconnectedBehavior(
                ClientOptions.DisconnectedBehavior.REJECT_COMMANDS);
    }
}
