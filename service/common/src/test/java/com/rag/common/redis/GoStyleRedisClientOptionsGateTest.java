package com.rag.common.redis;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.TimeoutOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientOptionsBuilderCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;

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
 *
 * <h2>🔴 2026-10-28：断言③ 从「非 null」升级为「<b>有判别力</b>的语义位」</h2>
 * <p><b>旧版断言</b>（本轮之前）是 {@code assertThat(options.getTimeoutOptions()).isNotNull()}
 * ⇒ ⇒ <b>恒真、零判别力</b>，实证如下（字节码级，{@code javap -c}）：
 * <ul>
 *   <li>{@code io.lettuce.core.ClientOptions#getTimeoutOptions()} =
 *       {@code aload_0 / getfield timeoutOptions / areturn}
 *       ⇒ ⇒ 该字段是 {@code final} 且<b>结构上不可能为 null</b>
 *       ⇒ ⇒ 断言期望值「非 null」= <b>恒真</b>；</li>
 *   <li>Boot 4.0.8 的 {@code LettuceConnectionConfiguration#createClientOptions} 字节码：
 *       <pre>
 *         143: invokestatic  TimeoutOptions.enabled:()Lio/lettuce/core/TimeoutOptions;
 *         146: invokevirtual ClientOptions$Builder.timeoutOptions:(...)Lio/lettuce/core/ClientOptions$Builder;
 *         151: invokeinterface ObjectProvider.orderedStream:()...
 *         162: invokeinterface Stream.forEach:(...)      ← customizer 在**之后**才跑
 *       </pre>
 *       ⇒ ⇒ <b>customizer 再调一次 {@code timeoutOptions(...)} 会整体替换 Boot 装的那份</b>
 *       （{@code ClientOptions$Builder#timeoutOptions} 的字节码 = {@code putfield timeoutOptions}）；
 *       ⇒ ⇒ 而旧断言对「整份被替换」<b>毫无反应</b>。</li>
 * </ul>
 * ⇒ ⇒ <b>旧断言能被什么破坏而不红</b>：
 * customizer 改成 {@code builder.disconnectedBehavior(REJECT_COMMANDS).timeoutOptions(TimeoutOptions.create())}
 * ⇒ ⇒ ① 绿（断连行为未动）· ② 绿（仍恰好一份）· ③ 绿（仍然非 null）
 * ⇒ ⇒ <b>运维命令超时已被整份替换，而三条守门一条不红</b>。
 *
 * <h3>⚠️ 关于「disabled」这个写法</h3>
 * <p>本仓解析到的 lettuce 是 <b>6.8.2.RELEASE</b>（{@code io.lettuce:lettuce-core:6.8.2.RELEASE:provided}），
 * 它<b>没有</b> {@code TimeoutOptions.disabled()}（{@code javap} 全量方法列表只有
 * {@code builder() / create() / enabled() / enabled(Duration)}；6.1.8 同样没有）
 * ⇒ ⇒ 「关掉命令超时」在本版本里唯一对应的是 {@link TimeoutOptions#create()}
 * ⇒ ⇒ 它 = {@code builder().build()} ⇒ {@code timeoutCommands=false} ·
 * {@code applyConnectionTimeout=false} · {@code source=null}
 * ⇒ ⇒ <b>比任何一种「换一份超时」都更能代表「整份被顶掉」</b>。
 *
 * <h3>本轮断言的判别力（逐条对照上面的破坏手法）</h3>
 * <ul>
 *   <li>{@code isTimeoutCommands() == true} ⇒ {@code create()} 给 {@code false} ⇒ <b>红</b>；</li>
 *   <li>{@code isApplyConnectionTimeout() == true} ⇒ {@code create()} 给 {@code false} ⇒ <b>红</b>；</li>
 *   <li>{@code getSource() != null} ⇒ {@code create()} 给 {@code null} ⇒ <b>红</b>；</li>
 *   <li>{@code getSource().getTimeout(null) == -1}
 *       ⇒ 「仍是 Boot 装的那份<b>默认</b> source」（{@code DefaultTimeoutSource} 的字节码恒返回 -1，
 *       含义 = 不额外固定超时、走后者的命令/连接超时）
 *       ⇒ 换成 {@code enabled(Duration)} 装出来的 {@code FixedTimeoutSource}（恒返回固定纳秒）⇒ <b>红</b>；</li>
 *   <li>④（{@code getClientConfiguration().getCommandTimeout() == 3s}）⇒ 证明
 *       <b>运维配的那个值本身</b>没丢（它走 {@code RedisURI.timeout} 这条独立出口，
 *       <b>不受</b> customizer 顶掉 {@code TimeoutOptions} 影响）
 *       ⇒ ⇒ 与上面 4 条合起来才完整表达「<b>超时没被顶掉</b>」：
 *       ③ 证「超时<b>机制</b>还在」，④ 证「超时<b>值</b>还在」。</li>
 * </ul>
 *
 * <p><b>⚠️ 本类断言的是「共享层内部契约」，不是 Go 对拍结论</b>：
 * 「共享层不得顶掉运维配的 Redis 超时」是本层自己的契约
 * （Go 侧 {@code core/stores/redis/redisclientmanager.go:27-46} 未设命令超时，
 * 但那是<b>另一条轴</b>：此处守的是「RAG 侧运维配的超时不被共享层悄悄作废」）。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        classes = GoStyleRedisClientOptionsGateTest.TestApp.class,
        properties = {
                "spring.data.redis.host=127.0.0.1",
                "spring.data.redis.port=1",
                // 🔴 2026-10-28：显式配命令超时 ⇒ 断言④ 才有东西可证
                //    （否则「运维配的值没丢」是恒真的 —— 和旧版断言③ 同一个毛病）。
                "spring.data.redis.timeout=3s"
        })
@DisplayName("共享层 Redis 客户端：断连即拒单（Go 的 fail-open 前置条件）")
class GoStyleRedisClientOptionsGateTest {

    /** 测试里显式配的命令超时（对应 {@code spring.data.redis.timeout=3s}）。 */
    private static final Duration CONFIGURED_COMMAND_TIMEOUT = Duration.ofSeconds(3);

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
    @DisplayName("③ 命令超时的<b>语义位</b>必须仍在（运维配的 spring.data.redis.timeout 要保住）")
    void commandTimeoutIsNotClobbered() {
        ClientOptions options = clientOptions();
        TimeoutOptions timeouts = options.getTimeoutOptions();

        assertThat(timeouts).as("Boot 会把 spring.data.redis.timeout 写进 timeoutOptions；"
                + "共享层若再调 timeoutOptions 就会覆盖它 ⇒ 那是新漂移").isNotNull();

        assertThat(timeouts.isTimeoutCommands())
                .as("🔴 判别力锚点：TimeoutOptions.create() 的 timeoutCommands=false"
                        + " ⇒ 共享层若把整份 TimeoutOptions 顶掉，这一条立刻红"
                        + "（旧版断言只查「非 null」= 恒真 ⇒ 顶掉了也不红）")
                .isTrue();

        assertThat(timeouts.isApplyConnectionTimeout())
                .as("🔴 判别力锚点：TimeoutOptions.create() 的 applyConnectionTimeout=false"
                        + " ⇒ 同上，顶掉整份就红")
                .isTrue();

        assertThat(timeouts.getSource())
                .as("🔴 判别力锚点：TimeoutOptions.create() 的 source=null"
                        + " ⇒ 顶掉整份就红（Boot 装的是 enabled() ⇒ 必有 source）")
                .isNotNull();

        assertThat(timeouts.getSource().getTimeout(null))
                .as("🔴 判别力锚点：Boot 装的是 TimeoutOptions.enabled() ⇒ source = DefaultTimeoutSource"
                        + " ⇒ getTimeout(...) 恒为 -1（含义 = 不额外固定超时，走命令/连接自身的超时）；"
                        + "若被换成 enabled(Duration) 装出来的 FixedTimeoutSource ⇒ 返回固定纳秒 ⇒ 这里红")
                .isEqualTo(-1L);
    }

    @Test
    @DisplayName("④ 运维配的命令超时<b>值</b>（3s）必须仍是 3s（超时机制没被顶掉 + 值也没丢）")
    void configuredCommandTimeoutValueSurvives() {
        assertThat(connectionFactory.getClientConfiguration().getCommandTimeout())
                .as("🔴 spring.data.redis.timeout=3s 的唯一真出口 = LettuceClientConfiguration#commandTimeout"
                        + " ⇒ 它独立于 ClientOptions.timeoutOptions（后者走 RedisURI/TimeoutOptions 两条路）"
                        + " ⇒ 与断言③ 合起来才完整表达「超时没被顶掉」：③ 证机制在，④ 证值在")
                .isEqualTo(CONFIGURED_COMMAND_TIMEOUT);
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
