package com.rag.common.precedence;

import io.lettuce.core.AbstractRedisClient;
import io.lettuce.core.ClientOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientOptionsBuilderCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 守门：共享层 Redis customizer 的「<b>遮蔽契约</b>」——
 * 域内副本在场时，共享层<b>退让</b>（不叠加第二份），而不是两份一起生效。
 *
 * <p><b>为什么要钉这个契约</b>（2026-10-27 · G1 新建 3 份域内副本时暴露）：
 * 共享层的 {@code @Bean} 带 {@code @ConditionalOnMissingBean} ⇒
 * <b>域内一旦已定义 {@code LettuceClientOptionsBuilderCustomizer}，共享层那份 Bean 根本不会被创建</b>
 * ⇒ 容器里恒为<b>恰好一份</b>。
 * ⚠️ <b>代价 = 遮蔽</b>：域内副本存在时共享层在该域<b>完全不生效</b>
 * ⇒ 共享层的后续改动（例如 E66 的 {@code timeoutOptions}）在该域<b>静默不生效</b>
 * ⇒ ⇒ 这就是「<b>域内副本必须删除</b>」的技术依据。
 *
 * <p><b>另一半（无域内副本时共享层自己顶得上）</b>由
 * {@code com.rag.common.redis.GoStyleRedisClientOptionsGateTest} 守住
 * （它断言：恰好一份、且名字是 {@code goStyleRedisClientOptionsCustomizer}、生效值 = {@code REJECT_COMMANDS}）。
 * ⇒ 两条合起来 = 「<b>要么域内那份生效，要么共享层那份生效，但永远只有一份</b>」。
 *
 * <p>⚠️ <b>包位置刻意选 {@code com.rag.common.precedence}</b>：
 * {@code com.rag.common.redis} 与 {@code com.rag.common.protection} 里各有一个
 * {@code @SpringBootApplication} 的测试上下文 ⇒ 组件扫描会<b>递归扫子包</b>
 * ⇒ 本类若放在 {@code com.rag.common.redis} 之下，会被那些上下文扫到
 * ⇒ 把共享层守门（断言「恰好一份且是共享层那份」）打红。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        classes = GoStyleRedisClientOptionsPrecedenceGateTest.TestApp.class,
        properties = {
                "spring.data.redis.host=127.0.0.1",
                "spring.data.redis.port=1"
        })
@DisplayName("共享层 Redis customizer 的遮蔽契约：域内副本在场 ⇒ 共享层退让（不叠加第二份）")
class GoStyleRedisClientOptionsPrecedenceGateTest {

    @Autowired
    ApplicationContext context;

    @Autowired
    LettuceConnectionFactory connectionFactory;

    @Test
    @DisplayName("① 域内副本在场时，容器里<b>恰好一份</b> customizer，且是<b>域内那份</b>")
    void domainCopyIsTheOnlyCustomizer() {
        assertThat(context.getBeanNamesForType(LettuceClientOptionsBuilderCustomizer.class))
                .as("两份 customizer 同时在场 ⇒ 同一个客户端选项会被改两次 ⇒ 行为取决于注册顺序，不可预期")
                .containsExactly("goStyleDomainLikeDisconnectedBehavior");
    }

    @Test
    @DisplayName("② 共享层那份 Bean <b>根本没被创建</b>（不是「被覆盖」，是「没注册」）")
    void sharedLayerBacksOff() {
        assertThat(context.containsBean("goStyleRedisClientOptionsCustomizer"))
                .as("共享层必须退让 ⇒ 这条正是「共享层后续改动在该域静默不生效」的根因")
                .isFalse();
    }

    @Test
    @DisplayName("③ 生效值 = <b>域内那份</b>设的值 ⇒ 证明共享层那份<b>没有被叠加</b>上去")
    void effectiveValueComesFromDomainCopyOnly() {
        assertThat(clientOptions().getDisconnectedBehavior())
                .as("域内副本刻意设成 DEFAULT（与共享层的 REJECT_COMMANDS 相反）⇒ "
                        + "生效值是 DEFAULT ⇒ 共享层那份没被应用；"
                        + "若是 REJECT_COMMANDS ⇒ 说明两份叠加了")
                .isEqualTo(ClientOptions.DisconnectedBehavior.DEFAULT);
    }

    /** Lettuce 原生客户端上的 ClientOptions（Boot 把它组装进去的地方）。 */
    private ClientOptions clientOptions() {
        AbstractRedisClient nativeClient = connectionFactory.getNativeClient();
        assertThat(nativeClient).as("Lettuce 原生客户端必须已创建").isNotNull();
        return nativeClient.getOptions();
    }

    /**
     * 测试应用：组件扫描本包 ⇒ 扫到 {@link DomainLikeRedisDisconnectedBehaviorConfig}（域内副本替身），
     * 自动配置清单 ⇒ 带上共享层的 {@code GoStyleRedisClientAutoConfiguration}。
     */
    @SpringBootApplication
    static class TestApp {
    }
}
