package com.rag.test.redis;

import com.rag.common.redis.GoStyleRedisDeadlineAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/**
 * <b>「真生产路径」的装配面</b>：Boot 自己的 {@code DataRedisAutoConfiguration}
 * ＋ <b>真 {@code LettuceConnectionFactory}</b> ＋ 本层的期限感知自动配置。
 *
 * <h2>为什么与生产<b>完全</b>同形</h2>
 * <p>生产上，各域注入的 {@code RedisTemplate} / {@code StringRedisTemplate}
 * 是 Boot 的 {@code DataRedisAutoConfiguration} 造出来的 ⇒ ⇒ 本类用
 * {@link ImportAutoConfiguration} 引的<b>就是那一个自动配置</b>
 * ⇒ ⇒ 那两个 template Bean 与真 Lettuce factory <b>都由 Boot 自己造</b>
 * ⇒ ⇒ <b>与生产逐字同形</b>（区别只在 Redis 服务端没起）。
 *
 * <h2>为什么不用 stub（与 {@link RedisLikeApplication} 的分工）</h2>
 * <p>{@link RedisLikeApplication} 用 stub ⇒ 它证明的是「<b>本层自己的机制</b>生效」
 * （装配 ＋ 拦截 ＋ 不下发 ＋ 异常类型），但它<b>证不了「Boot 造的那两个 template
 * 真的被换了」</b> ⇒ ⇒ 本类补的就是这一格：<b>真 Lettuce 路径上的接线证据</b>。
 *
 * <h2>Redis 服务端没起，为什么还能判定</h2>
 * <p>因为<b>预算检查在「取连接 / 建连 / 下发」之前</b> ⇒ ⇒ 预算耗尽时
 * 命令<b>根本不会走到 socket</b> ⇒ ⇒ 断言「抛的是 {@code QueryTimeoutException}
 * 而不是连接异常」本身就证明了「没碰网络」⇒ ⇒ <b>不需要 Redis 在跑</b>。
 */
@Configuration(proxyBeanMethods = false)
@ImportAutoConfiguration({
        DataRedisAutoConfiguration.class,      // Boot 自己造 redisTemplate / stringRedisTemplate / LettuceConnectionFactory
        GoStyleRedisDeadlineAutoConfiguration.class  // 本层：BPP 把那两个 template 的 factory 换掉
})
public class RedisRealLettuceApplication {
}
