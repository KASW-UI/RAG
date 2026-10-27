package com.rag.task.task.rpc.config;

import io.lettuce.core.ClientOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🔴 守门：task-rpc 的 <b>Redis 命令超时必须落在 3s</b>（= Go 侧 go-redis 的库默认 ReadTimeout）。
 *
 * <h2>🔴 为什么必须有这个守门（2026-10-27 G2 取证）</h2>
 * <p>本组件原先<b>没有</b>配 {@code spring.data.redis.timeout} ⇒ Boot 4 不调
 * {@code commandTimeout(...)}（{@code LettuceConnectionConfiguration#applyProperties:173-175}
 * 只在 {@code getTimeout() != null} 时才设）⇒ 落到 Lettuce 自己的默认 ⇒
 * <b>实测 = {@code PT1M}（60 秒）</b>。
 *
 * <p>而 Go 侧<b>不是</b> 60s：
 * <ul>
 *   <li>{@code service/task/rpc/internal/svc/service_context.go:25-30}
 *       ⇒ {@code redis.NewClient(&redis.Options{Addr, Password, DB})}
 *       ⇒ <b>不设任何 timeout</b> ⇒ 走 go-redis 库默认；</li>
 *   <li>go-redis v9.20.0 {@code options.go:370} ⇒ {@code ReadTimeout = 3 * time.Second}；</li>
 *   <li>⚠️ 真源 yaml 里写的 {@code ReadTimeoutMs: 1000} <b>不生效</b> ——
 *       {@code internal/config/config.go:32-37} 的 LikeRedis struct <b>没有</b>该字段，
 *       且 go-zero 的 {@code conf.Load} 非严格模式 ⇒ 未知字段被静默忽略
 *       （Go 侧活体探针实测：{@code LOAD OK}，{@code ReadTimeoutMs: 1000} 未进 struct）。</li>
 * </ul>
 *
 * <h2>🔴 为什么这条不是「配置洁癖」，而是可观察的行为漂移</h2>
 * <p>「先连上、再断连」场景下（Lettuce 默认 {@code DisconnectedBehavior.DEFAULT}
 * ⇒ 命令<b>排队等重连</b>，挂到 commandTimeout 才失败），<b>双侧活体实测</b>：
 * <table border="1">
 *   <caption>2026-10-27 G2 · 真 Redis 容器 + 探针（同一操作：GET）</caption>
 *   <tr><th>侧</th><th>断连后行为</th><th>实测耗时</th></tr>
 *   <tr><td>Go（go-redis，与真源同款 Options）</td><td>拒连快速失败</td><td><b>1694 ms</b></td></tr>
 *   <tr><td>RAG 未配 timeout（本守门修之前）</td><td>排队等重连 → RedisCommandTimeoutException</td>
 *       <td><b>60021 ms</b></td></tr>
 *   <tr><td>RAG 配 3s（本守门修之后）</td><td>快速失败</td><td><b>3 ms</b></td></tr>
 * </table>
 * ⇒ 60s ≫ 本组件的 rpc-timeout（2s）⇒ 一次 Redis 断连会把<b>整个 RPC</b>拖到超时，
 * 而 Go 是「快速失败 ⇒ 域侧 lenient 包装 catch ⇒ 回源 DB ⇒ 请求按时返回」。
 *
 * <h2>为什么断言「真装配出来的 ClientOptions」而不是「yml 里有这一行」</h2>
 * <p>AGENTS.md §3.3：<b>注解/配置存在 ≠ 机制生效</b>。只断言 yml 有 {@code timeout: 3s}
 * 挡不住「属性名写错 ⇒ Spring 静默用默认值」—— 而 Boot 4 的属性名确实是
 * {@code spring.data.redis.timeout}（{@code DataRedisProperties#getTimeout}）。
 * ⇒ 本测试起<b>真 Spring 上下文</b>、拿<b>真的</b> {@link LettuceConnectionFactory}、
 * 读<b>真的</b> {@link ClientOptions}，用框架自己的 Binder 走一遍 relaxed binding。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        // ⚠️ 必须用**真应用类** ⇒ 只有它才会加载 task-rpc 真实的 application.yml
        //    （断言 3s 的对象就是那份 yml 里的 spring.data.redis.timeout）。
        //    用一个空的 @SpringBootApplication 只会拿到 Boot 默认值（60s）⇒ 断言恒红。
        classes = com.rag.task.task.rpc.TaskRpcApplication.class,
        properties = {
                // 🔴 专属上下文缓存键：Spring 的 context cache key **包含 properties**，
                //    但**不含 @DynamicPropertySource 的值** ⇒ 若与同模块另一个 @SpringBootTest
                //    的 properties 恰好相同 ⇒ 两者**共享同一个 Spring 上下文**
                //    ⇒ 本类的断言可能读到别的测试装配出来的 factory。
                "rag.test.gate.context-key=task-redis-command-timeout",

                // 绕过 Nacos / Kafka / Redis 的真实连接（都是懒建或连不上也不影响本断言）
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "logging.level.com.alibaba.nacos=WARN",
                "spring.kafka.bootstrap-servers=127.0.0.1:1",
                "spring.data.redis.host=127.0.0.1",
                "spring.data.redis.port=1",

                // ⚠️ 本测试与 DB 无关 ⇒ 排除 Flyway（否则上下文启动时会去建表 ⇒ 需要真 PG）。
                //    ⚠️ 排除 Flyway 不会影响 Redis 自动配置（LettuceConnectionFactory 由
                //    Boot 的 RedisAutoConfiguration 装配，与 Flyway 无关）。
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
        })
@DisplayName("task-rpc：Redis 命令超时必须是 3s（对齐 Go 的 go-redis 默认 ReadTimeout）")
class TaskRedisCommandTimeoutGateTest {

    @Autowired
    LettuceConnectionFactory connectionFactory;

    @Autowired
    ApplicationContext context;

    @Test
    @DisplayName("① 命令超时机制必须**启用**（Boot 打开的，不是 Lettuce 自己的默认）")
    void commandTimeoutMechanismIsEnabled() {
        ClientOptions options = clientOptions();
        assertThat(options)
                .as("Lettuce 客户端必须有 ClientOptions（否则说明连 Lettuce 都没用上）")
                .isNotNull();
        assertThat(options.getTimeoutOptions())
                .as("Boot 4 在 LettuceConnectionConfiguration:229 显式 timeoutOptions(TimeoutOptions.enabled())"
                        + " ⇒ 命令超时是**启用**的 ⇒ timeoutCommands 必须为 true")
                .isNotNull();
        assertThat(options.getTimeoutOptions().isTimeoutCommands())
                .as("命令超时必须启用；Lettuce 自己的 DEFAULT_TIMEOUT_COMMANDS=false，"
                        + "是 Boot 把它打开的 ⇒ 若此处为 false，说明 Boot 版本/装配路径变了")
                .isTrue();
    }

    @Test
    @DisplayName("② 连接超时（connect-timeout）必须是 5s（对齐 Go 的 go-redis 默认 DialTimeout）")
    void connectTimeoutIsFiveSeconds() {
        // ⚠️ **判据必须查「消费点」，不能查「声明点」**（AGENTS.md §3.3.1）：
        //   Boot 4：`LettuceConnectionConfiguration:212-215`
        //     `if (connectTimeout != null) builder.socketOptions(SocketOptions.builder().connectTimeout(ct))`
        //   ⇒ 落在 **`ClientOptions.socketOptions.connectTimeout`**（不是 RedisURI）。
        //   Go 侧真值：go-redis v9.20.0 `options.go:338-340`
        //     `if opt.DialTimeout == 0 { opt.DialTimeout = 5 * time.Second }`
        //     （真源 go.mod:15 锁的就是 v9.20.0；真源自己的 redis.Options 只设 Addr/Password/DB）。
        //   ⚠️ 不配会落 Lettuce 的默认 **10s**（`SocketOptions.DEFAULT_CONNECT_TIMEOUT = 10` 秒）。
        ClientOptions options = clientOptions();
        assertThat(options.getSocketOptions())
                .as("ClientOptions 必须有 SocketOptions（Boot 靠它设置 connectTimeout）")
                .isNotNull();
        assertThat(options.getSocketOptions().getConnectTimeout())
                .as("🔴 Redis 连接超时必须 = 5s（Go 的 go-redis 默认 DialTimeout）。"
                        + "未配 spring.data.redis.connect-timeout 时会落 Lettuce 默认的 10s"
                        + " ⇒ 与 Go 差 2 倍（Go 5s 拒连 ⇒ 快速失败）。")
                .isEqualTo(java.time.Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("③ 命令超时（消费点 = RedisURI.timeout）必须是 3s —— 那才是命令过期时长")
    void redisUriTimeoutIsThreeSeconds() {
        // ⚠️ **判据必须查「消费点」，不能查「声明点」**（AGENTS.md §3.3.1）：
        //   Boot 4 的 `spring.data.redis.timeout` 走的是
        //     `LettuceConnectionConfiguration#applyProperties:173-175` ⇒ `builder.commandTimeout(d)`
        //     ⇒ `LettuceClientConfiguration.getCommandTimeout()`
        //     ⇒ `LettuceConnectionFactory:1483` `builder.withTimeout(...)` ⇒ **RedisURI.timeout**
        //     ⇒ `CommandExpiryWriter.setTimeout(Duration)`（`CommandExpiryWriter:160`）
        //     ⇒ 命令过期时长。
        //   ⚠️ **不是** `ClientOptions.getTimeoutOptions()`：Boot 在
        //     `LettuceConnectionConfiguration:229` 只写了 `TimeoutOptions.enabled()`
        //     （机制**启用**，但 source 仍是 `DefaultTimeoutSource` ⇒ 返回 -1）
        //     ⇒ 真正的时长由 `CommandExpiryWriter:174` 的 `applyConnectionTimeout` 分支
        //     从**连接级 timeout** 取（即 RedisURI.timeout）。
        //   ⇒ 断言 native client 的 TimeoutSource 类型是**错的判据**（它恒为 DefaultTimeoutSource）。
        java.time.Duration redisUriTimeout = connectionFactory.getClientConfiguration()
                .getCommandTimeout();
        assertThat(redisUriTimeout)
                .as("🔴 RedisURI 级命令超时必须 = 3s（Go 的 go-redis 默认 ReadTimeout）。"
                        + "未配时落 RedisURI 的默认 60s（RedisURI.java:221 'Default timeout: 60 sec'）"
                        + " ⇒ 实测 Redis 断连时命令挂 60021ms，"
                        + "而 Go 侧快速失败（实测 1694ms）⇒ 一次 Redis 断连会拖垮整个 RPC。")
                .isEqualTo(java.time.Duration.ofSeconds(3));
    }

    @Test
    @DisplayName("④ 阳性对照：Lettuce 的**默认**命令超时是 60s（证明断言 ③ 有识别能力）")
    void lettuceDefaultCommandTimeoutIsSixtySeconds() {
        // RedisURI 的默认 timeout = 60s（RedisURI.java:221 / :257）
        io.lettuce.core.RedisURI defaultUri = io.lettuce.core.RedisURI.create("127.0.0.1", 6379);
        assertThat(defaultUri.getTimeout())
                .as("★ 阳性对照：Lettuce 默认命令超时是 60s ⇒ 若断言 ③ 恒真，"
                        + "说明它没在区分「配了 3s」与「没配」")
                .isEqualTo(java.time.Duration.ofSeconds(60));
    }

    /** Lettuce 原生客户端上的 ClientOptions（Boot 把它组装进去的地方）。 */
    private ClientOptions clientOptions() {
        io.lettuce.core.AbstractRedisClient nativeClient = connectionFactory.getNativeClient();
        assertThat(nativeClient)
                .as("Lettuce 原生客户端必须已创建（连接是懒建的，但客户端本身在启动时就建好）")
                .isNotNull();
        return nativeClient.getOptions();
    }
}
