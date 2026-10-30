package com.rag.test.redis;

import com.rag.common.redis.GoStyleRedisDeadlineAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * <b>模拟一个「用 Redis 的进程」的装配面</b>（api / rpc 侧同形）。
 *
 * <h2>为什么与生产同形</h2>
 * <p>生产上，各域的 {@code RedisTemplate} / {@code StringRedisTemplate} 是
 * Boot 的 {@code RedisAutoConfiguration} 造出来的 ⇒ ⇒ 本类用
 * {@link ImportAutoConfiguration} 引 {@link GoStyleRedisDeadlineAutoConfiguration}
 * （它就在 {@code AutoConfiguration.imports} 清单里）⇒ ⇒ <b>装配路径与生产一致</b>。
 *
 * <h2>为什么用 stub 连接而不是真 Redis</h2>
 * <p>本守门要证明的是「<b>本层的机制</b>生效」（装配 ＋ 拦截 ＋ 不下发 ＋ 异常类型），
 * <b>不是</b>「与真实 Redis 的行为一致」⇒ ⇒ 用一个「会记录命令」的 stub 连接
 * ⇒ ⇒ 断言可以精确到「<b>那条命令到底有没有被下发</b>」这一层
 * ⇒ ⇒ 真 Redis 的对拍属另一条（已登记为待办）。
 */
@Configuration(proxyBeanMethods = false)
@ImportAutoConfiguration(GoStyleRedisDeadlineAutoConfiguration.class)
public class RedisLikeApplication {

    /** stub connection factory（记录命令，不连真 Redis）。 */
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        return RedisStubFactory.stubFactory();
    }

    /** 模拟各域注入的那个 {@code RedisTemplate}。 */
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        return template;
    }
}
