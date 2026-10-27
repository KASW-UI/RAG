package com.rag.hot.hot.rpc.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.ClientOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;

/**
 * 🔴 Redis **超时口径**守门（hot-rpc）—— TCP 连接超时必须 = Go 的 DialTimeout **5s**。
 *
 * <h2>守的是什么漂移</h2>
 * <p>hot-rpc 读 Redis 的 ZSET 取 Top-K（Go 侧 {@code get_hot_articles_logic.go:43} 的
 * {@code l.svcCtx.RedisClient.ZRevRangeWithScores(...)}）⇒ 连接建立阶段的超时<b>可观察</b>；
 * 本组件是<b>纯 gRPC 进程</b>（无 HTTP 预算兜底）⇒ 连接超时的差异会<b>直接</b>体现在
 * gRPC 调用耗时上。
 *
 * <p><b>Go 出处（消费点，不是声明点）</b>：
 * <pre>
 *   service/hot/rpc/internal/svc/service_context.go:17-19
 *       rds := redis.NewClient(&amp;redis.Options{Addr: c.RedisConf.Host, Password: c.RedisConf.Pass})
 *     ← **只设了 Addr + Password** ⇒ DialTimeout 未设
 *     → go-redis v9.20.0 options.go:338-340
 *       if opt.DialTimeout == 0 { opt.DialTimeout = 5 * time.Second }
 *       ⇒ **DialTimeout = 5s**
 *       （ReadTimeout/WriteTimeout 同理走 options.go:364-379 ⇒ 3s）
 * </pre>
 *
 * <h2>为什么 Spring 侧「不写就错」</h2>
 * <ul>
 *   <li>Boot 4.0.8 的 {@code LettuceConnectionConfiguration.createClientOptions}：
 *       <b>只有</b> {@code spring.data.redis.connect-timeout} 非空时才调
 *       {@code SocketOptions.Builder.connectTimeout(...)}；<b>不写 ⇒ 整段不设</b>
 *       ⇒ 落回 Lettuce 的 {@code SocketOptions.DEFAULT_CONNECT_TIMEOUT} = <b>10s</b>。</li>
 *   <li>⚠️ {@code spring.data.redis.timeout}（命令超时）<b>不再</b>兼管连接超时 ——
 *       那是 Boot 2/3 的行为（Boot 4 起拆成两个属性）。⇒ 两个超时必须分别配。</li>
 *   <li>⇒ 不写 {@code connect-timeout} ⇒ 连接超时 <b>10s vs Go 5s</b> ⇒ <b>漂移 2 倍</b>。</li>
 * </ul>
 *
 * <h2>为什么本守门不是「断言 yml 里有这个键」</h2>
 * <p>本守门走的是<b>整条真实链路</b>：
 * <ol>
 *   <li>{@link ConfigDataApplicationContextInitializer} ⇒ 加载本模块**真实的** {@code application.yml}；</li>
 *   <li>Boot <b>真实的</b> {@link DataRedisAutoConfiguration} ⇒ 装配出<b>真的</b>
 *       {@link LettuceConnectionFactory}；</li>
 *   <li>断言读的是<b>消费点</b>：{@code LettuceClientConfiguration.getClientOptions()}
 *       → {@code ClientOptions.getSocketOptions().getConnectTimeout()}。</li>
 * </ol>
 * <p>⚠️ <b>共享层不参与</b>：{@code service/common} 的 {@code GoStyleRedisClientAutoConfiguration}
 * 只设 {@code disconnectedBehavior(REJECT_COMMANDS)}、<b>不碰</b> {@code socketOptions}
 * ⇒ 连接超时完全由本域 yml 决定 ⇒ 本守门不引入共享层那一半。
 *
 * <p><b>证据强度</b>：L3 静态实测（Boot 4.0.8 字节码 + Lettuce 6.8.2 常量 + go-redis v9.20.0 源码）
 * + 本守门的<b>真容器</b>装配断言。
 */
class HotRpcRedisTimeoutContractTest {

    /** Go 的 DialTimeout（go-redis v9.20.0 options.go:338-340）。 */
    private static final Duration GO_DIAL_TIMEOUT = Duration.ofSeconds(5);

    /** Go 的 ReadTimeout / WriteTimeout（go-redis v9.20.0 options.go:364-379）。 */
    private static final Duration GO_COMMAND_TIMEOUT = Duration.ofSeconds(3);

    @Test
    @DisplayName("🔴 Redis 连接超时 = Go 的 DialTimeout(5s)，命令超时 = Go 的 Read/WriteTimeout(3s)")
    void redisConnectAndCommandTimeoutsMatchGoSide() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withConfiguration(AutoConfigurations.of(DataRedisAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    LettuceConnectionFactory factory = context.getBean(LettuceConnectionFactory.class);
                    ClientOptions clientOptions = factory.getClientConfiguration().getClientOptions()
                            .orElseThrow(() -> new AssertionError("LettuceClientConfiguration 没有 ClientOptions"));

                    assertThat(clientOptions.getSocketOptions().getConnectTimeout())
                            .as("Redis 的 **TCP 连接**超时必须 = Go 的 DialTimeout(5s)；"
                                    + "Boot 4 的 spring.data.redis.timeout **不再**兼管它，"
                                    + "不写 connect-timeout 就会落回 Lettuce 默认的 10s")
                            .isEqualTo(GO_DIAL_TIMEOUT);

                    assertThat(factory.getClientConfiguration().getCommandTimeout())
                            .as("Redis 的**命令**超时必须 = Go 的 Read/WriteTimeout(3s)")
                            .isEqualTo(GO_COMMAND_TIMEOUT);
                });
    }
}
