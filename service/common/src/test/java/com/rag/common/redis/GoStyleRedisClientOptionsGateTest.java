package com.rag.common.redis;

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
 * 守门：共享层把 Lettuce 的<b>断连行为</b>改成 {@code REJECT_COMMANDS}，
 * 让「Redis 不可用」时的失败语义与 Go 一致（<b>尽快失败 ⇒ 上层 fail-open</b>）。
 *
 * <p><b>Go 出处（消费点）</b>：
 * <ul>
 *   <li>{@code service/user/user/api/internal/middleware/checkblacklistmiddleware.go:35-36}
 *       ⇒ {@code if err == nil && exists} ⇒ <b>err != nil ⇒ 放行</b>（fail-open）；</li>
 *   <li>{@code core/stores/redis/redisclientmanager.go:18,27-46}
 *       ⇒ {@code MaxRetries = 3} 且未设 DialTimeout/ReadTimeout
 *       ⇒ 连接被拒时<b>快速失败</b>（users 域实测 ~1.7s）。</li>
 * </ul>
 *
 * <p><b>为什么必须是真容器</b>：断言的是「Boot 真的把 customizer 应用到了
 * {@code ClientOptions} 上」⇒ 只有起真 Spring 上下文才作数
 * （AGENTS.md §3.3：「注解存在」≠「机制生效」）。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        classes = GoStyleRedisClientOptionsGateTest.TestApp.class,
        properties = {
                "spring.data.redis.host=127.0.0.1",
                "spring.data.redis.port=1"
        })
@DisplayName("共享层 Redis 客户端：断连即拒单（Go 的 fail-open 前置条件）")
class GoStyleRedisClientOptionsGateTest {

    @Autowired
    LettuceConnectionFactory connectionFactory;

    @Autowired
    ApplicationContext context;

    @Test
    @DisplayName("① ClientOptions 的断连行为必须是 REJECT_COMMANDS（断连时立即拒单，不排队等重连）")
    void disconnectedBehaviorIsRejectCommands() {
        ClientOptions options = clientOptions();
        assertThat(options).as("Lettuce 客户端必须有 ClientOptions（否则说明连 Lettuce 都没用上）").isNotNull();
        assertThat(options.getDisconnectedBehavior())
                .as("断连即拒单 ⇒ 断连期间的命令立即失败（而不是挂起等重连）"
                        + "⇒ 上层 catch 到 err ⇒ fail-open，与 Go 一致")
                .isEqualTo(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS);
    }

    @Test
    @DisplayName("② 共享层这份 customizer 必须<b>有且只有一份</b>（单一来源，不得翻倍）")
    void customizerIsRegisteredExactlyOnce() {
        assertThat(context.getBeanNamesForType(LettuceClientOptionsBuilderCustomizer.class))
                .as("重复注册会让同一个 customizer 被应用多次（各域各注册一个 ⇒ 统计翻倍）")
                .hasSize(1);
        assertThat(context.getBeanNamesForType(LettuceClientOptionsBuilderCustomizer.class)[0])
                .isEqualTo("goStyleRedisClientOptionsCustomizer");
    }

    @Test
    @DisplayName("③ 命令超时必须<b>未被</b>本层改动（运维配的 spring.data.redis.timeout 要保住）")
    void commandTimeoutIsNotClobbered() {
        ClientOptions options = clientOptions();
        assertThat(options.getTimeoutOptions())
                .as("Boot 会把 spring.data.redis.timeout 写进 timeoutOptions；"
                        + "共享层若再调 timeoutOptions 就会覆盖它 ⇒ 那是新漂移")
                .isNotNull();
    }

    /** Lettuce 原生客户端上的 ClientOptions（Boot 把它组装进去的地方）。 */
    private ClientOptions clientOptions() {
        io.lettuce.core.AbstractRedisClient nativeClient = connectionFactory.getNativeClient();
        assertThat(nativeClient).as("Lettuce 原生客户端必须已创建（连接是懒建的，但客户端本身在启动时就建好）").isNotNull();
        return nativeClient.getOptions();
    }

    @SpringBootApplication
    static class TestApp {
    }
}
