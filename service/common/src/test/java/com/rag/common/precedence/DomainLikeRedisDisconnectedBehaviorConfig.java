package com.rag.common.precedence;

import io.lettuce.core.ClientOptions;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientOptionsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 守门用的「<b>域内副本</b>」替身：形态与真实域内副本
 * （{@code service/users/user/user-api/.../config/RedisDisconnectedBehaviorConfig.java}
 * 以及 G1 在 admin-api / follow-api 新建的那两份）<b>完全同构</b> ——
 * 都是一个 {@code @Configuration} ＋ 一个 {@code LettuceClientOptionsBuilderCustomizer} Bean。
 *
 * <p>⚠️ <b>刻意把断连行为设成 {@link ClientOptions.DisconnectedBehavior#DEFAULT}</b>
 * （与共享层的 {@code REJECT_COMMANDS} <b>相反</b>）⇒ 这样才能<b>判别</b>出
 * 「共享层那份到底有没有被叠加上去」：
 * <ul>
 *   <li>若共享层<b>退让</b>（{@code @ConditionalOnMissingBean} 生效）⇒ 生效值 = {@code DEFAULT}；</li>
 *   <li>若共享层<b>被叠加</b> ⇒ 生效值 = {@code REJECT_COMMANDS}（自动配置后注册 ⇒ 后应用）</li>
 *       ⇒ 断言立即变红。</li>
 * </ul>
 *
 * <p>⚠️ <b>这不是「域内副本应该设 DEFAULT」的建议</b> ——
 * 真实的三份域内副本都设 {@code REJECT_COMMANDS}（与共享层同值）
 * ⇒ 本类只是<b>守门用的判别器</b>，用来证明「遮蔽」而不是「叠加」这个契约。
 */
@Configuration
public class DomainLikeRedisDisconnectedBehaviorConfig {

    /**
     * 域内副本那份 customizer（Bean 名与真实域内副本同款前缀，便于对照）。
     */
    @Bean
    public LettuceClientOptionsBuilderCustomizer goStyleDomainLikeDisconnectedBehavior() {
        return builder -> builder.disconnectedBehavior(ClientOptions.DisconnectedBehavior.DEFAULT);
    }
}
