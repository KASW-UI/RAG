package com.rag.like.like.rpc;

import io.lettuce.core.ClientOptions;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🔴 守门：like-rpc 的 <b>Redis 命令超时 = 3s ＋ 连接超时 = 5s</b>（对齐 Go 的 go-redis 库默认）。
 *
 * <h2>Go 侧真值（为什么是 3s / 5s，而不是 yaml 里写的数）</h2>
 * <ul>
 *   <li>真源 like-rpc 用 {@code redis.MustNewRedis(c.Storage.Redis.RedisConf)}（{@code service/like/rpc/internal/svc/servicecontext.go:27}），
 *       而 go-zero 的 {@code RedisConf}（{@code core/stores/redis/conf.go:19-28}）<b>没有 timeout 字段</b>
 *       ⇒ 不设 DialTimeout / ReadTimeout；</li>
 *   <li>⇒ 真正生效的是 go-redis <b>v9.20.0</b>（真源 {@code go.mod:15} 锁的就是这个版本）的库默认：
 *       {@code options.go:370} {@code ReadTimeout = 3s} · {@code options.go:338-340} {@code DialTimeout = 5s}。</li>
 * </ul>
 *
 * <h2>🔴 为什么命令超时必须查「消费点」（AGENTS.md §3.3.1）</h2>
 * <p>Boot 4 的 {@code spring.data.redis.timeout} 走的是
 * {@code LettuceConnectionConfiguration#applyProperties:173-175} ⇒ {@code builder.commandTimeout(d)}
 * ⇒ {@code LettuceConnectionFactory:1483} {@code builder.withTimeout(...)} ⇒ <b>{@code RedisURI.timeout}</b>
 * ⇒ {@code CommandExpiryWriter.setTimeout(Duration)}（{@code CommandExpiryWriter:160}）⇒ 命令过期时长。
 * <p>⚠️ <b>不是</b> {@code ClientOptions.getTimeoutOptions()}：Boot 在
 * {@code LettuceConnectionConfiguration:229} 只写了 {@code TimeoutOptions.enabled()}
 * （机制<b>启用</b>，但 source 仍是 {@code DefaultTimeoutSource} ⇒ 返回 -1）
 * ⇒ 真正的时长由 {@code CommandExpiryWriter:174} 的 {@code applyConnectionTimeout} 分支从**连接级** timeout 取。
 *
 * <h2>🔴 为什么这不是「配置洁癖」，而是可观察的行为漂移</h2>
 * <p>「先连上、再断连」场景（Lettuce 默认 {@code DisconnectedBehavior.DEFAULT} ⇒ 命令<b>排队等重连</b>），
 * <b>双侧活体实测</b>（2026-10-27 G2 · 真 Redis 容器 + 探针，同一操作 GET）：
 * <table border="1">
 *   <caption>同一次实测的三个口径</caption>
 *   <tr><th>口径</th><th>断连后行为</th><th>实测耗时</th></tr>
 *   <tr><td>Go（go-redis，与真源同款 Options）</td><td>拒连快速失败</td><td><b>1694 ms</b></td></tr>
 *   <tr><td>RAG 未配 timeout</td><td>排队等重连 → RedisCommandTimeoutException</td><td><b>60021 ms</b></td></tr>
 *   <tr><td>RAG 配 3s（本域现状）</td><td>快速失败</td><td><b>3 ms</b></td></tr>
 * </table>
 * ⇒ 60s ≫ 本组件的 rpc-timeout（2s）⇒ 一次 Redis 断连会把<b>整个 RPC</b>拖到超时，
 * 而 Go 是「快速失败 ⇒ 域侧 lenient 包装 catch ⇒ 回源 DB ⇒ 请求按时返回」。
 */
@SpringBootTest(
        classes = LikeRpcApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                // 🔴 专属上下文缓存键：Spring 的 context cache key **包含 properties**，
                //    但**不含 @DynamicPropertySource 的值** ⇒ 若与同模块另一个 @SpringBootTest
                //    的 properties 恰好相同 ⇒ 两者**共享同一个 Spring 上下文**
                //    ⇒ 本类的断言可能读到别的测试装配出来的 factory。
                "rag.test.gate.context-key=like-redis-timeouts",

                "grpc.server.port=0",
                // 绕过 Nacos：devh 的 @GrpcClient 在 Bean 初始化期就建 channel（不是懒建）
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                // like-rpc 只有一个 client：message-rpc
                "grpc.client.message-rpc.address=static://127.0.0.1:1",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN",
                // 绕过 Kafka / Redis 的真实连接
                "spring.kafka.bootstrap-servers=127.0.0.1:1",
                "spring.data.redis.host=127.0.0.1",
                "spring.data.redis.port=1",

                // ⚠️ 本测试与 DB 无关 ⇒ 排除 Flyway（否则上下文启动时会去建表 ⇒ 需要真 PG）。
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
        })
@DisplayName("like-rpc：Redis 命令超时 3s ＋ 连接超时 5s ＋ **连接池 64/16/8**（对齐 Go 的 go-redis 库默认 ＋ go-zero 的 `idleConns = 8`）")
class LikeRpcRedisTimeoutsGateTest {

    @Autowired
    LettuceConnectionFactory connectionFactory;

    @Test
    @DisplayName("① 命令超时机制必须**启用**（Boot 打开的，不是 Lettuce 自己的默认）")
    void commandTimeoutMechanismIsEnabled() {
        ClientOptions options = clientOptions();
        assertThat(options)
                .as("Lettuce 客户端必须有 ClientOptions（否则说明连 Lettuce 都没用上）")
                .isNotNull();
        assertThat(options.getTimeoutOptions().isTimeoutCommands())
                .as("命令超时必须启用；Lettuce 自己的 DEFAULT_TIMEOUT_COMMANDS=false，"
                        + "是 Boot 4 在 LettuceConnectionConfiguration:229 把它打开的"
                        + " ⇒ 若此处为 false，说明 Boot 版本/装配路径变了")
                .isTrue();
    }

    @Test
    @DisplayName("② 命令超时（消费点 = RedisURI.timeout）必须是 3s")
    void commandTimeoutIsThreeSeconds() {
        java.time.Duration commandTimeout = connectionFactory.getClientConfiguration()
                .getCommandTimeout();
        assertThat(commandTimeout)
                .as("🔴 Redis 命令超时必须 = 3s（Go 的 go-redis v9.20.0 默认 ReadTimeout）。"
                        + "未配时落 RedisURI 的默认 60s（RedisURI.java:221 'Default timeout: 60 sec'）"
                        + " ⇒ 实测 Redis 断连时命令挂 60021ms，"
                        + "而 Go 侧快速失败（实测 1694ms）⇒ 一次 Redis 断连会拖垮整个 RPC。")
                .isEqualTo(java.time.Duration.ofSeconds(3));
    }

    @Test
    @DisplayName("③ 连接超时（消费点 = ClientOptions.socketOptions.connectTimeout）必须是 5s")
    void connectTimeoutIsFiveSeconds() {
        // Boot 4：`LettuceConnectionConfiguration:212-215`
        //   `if (connectTimeout != null) builder.socketOptions(SocketOptions.builder().connectTimeout(ct))`
        // Go 侧：go-redis v9.20.0 `options.go:338-340` `DialTimeout = 5s`
        // ⚠️ 不配会落 Lettuce 的默认 **10s**（`SocketOptions.DEFAULT_CONNECT_TIMEOUT = 10` 秒）。
        ClientOptions options = clientOptions();
        assertThat(options.getSocketOptions())
                .as("ClientOptions 必须有 SocketOptions（Boot 靠它设置 connectTimeout）")
                .isNotNull();
        assertThat(options.getSocketOptions().getConnectTimeout())
                .as("🔴 Redis 连接超时必须 = 5s（Go 的 go-redis 默认 DialTimeout）。"
                        + "未配 spring.data.redis.connect-timeout 时会落 Lettuce 默认的 10s"
                        + " ⇒ 与 Go 差 2 倍。")
                .isEqualTo(java.time.Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("④ 阳性对照：Lettuce 的**默认**命令超时是 60s（证明断言 ② 有识别能力）")
    void lettuceDefaultCommandTimeoutIsSixtySeconds() {
        io.lettuce.core.RedisURI defaultUri = io.lettuce.core.RedisURI.create("127.0.0.1", 6379);
        assertThat(defaultUri.getTimeout())
                .as("★ 阳性对照：Lettuce 默认命令超时是 60s ⇒ 若断言 ② 恒真，"
                        + "说明它没在区分「配了 3s」与「没配」")
                .isEqualTo(java.time.Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("⑤ Redis 连接池必须**启用**（Go 侧有池；Boot 4 的判据 = enabled ?? COMMONS_POOL2_AVAILABLE）")
    void redisPoolIsEnabled() {
        // ⚠️ **判据必须查「消费点」**（AGENTS.md §3.3.1）：
        //   Boot 4 `DataRedisConnectionConfiguration#isPoolEnabled`（`:195-198`）：
        //     `Boolean enabled = pool.getEnabled(); return (enabled != null) ? enabled : COMMONS_POOL2_AVAILABLE;`
        //   ⇒ 显式配了 `enabled: true` ⇒ 走 `LettuceConnectionConfiguration#createBuilder:162-165`
        //     的 `PoolBuilderFactory` ⇒ 产出 `LettucePoolingClientConfiguration`（**池化**）
        //   ⇒ 未启用时产出的是普通 `LettuceClientConfiguration`（**共享单连接**）。
        //   Go 侧：真源 `service/like/rpc/internal/svc/servicecontext.go:27` 走 **go-zero 的 redis**
        //     ⇒ go-zero 构造 `red.Options`（`go-zero@v1.10.2/core/stores/redis/redisclientmanager.go:32-40`）
        //     ⇒ **未设 `PoolSize`** ⇒ 落 go-redis v9.20.0 库默认
        //       （`go-redis@v9.20.0/options.go:350-351` `PoolSize = 10 * runtime.GOMAXPROCS(0)`）⇒ **Go 侧有池**。
        //   ⇒ RAG 侧若不池化 ⇒ 并发下命令互相串行排队 ⇒ 可能撞 3s 命令超时 ⇒ **形态漂移**。
        //   ⚠️ 本条**不能只靠 yml 断言**：`enabled` 未配时判据由 **classpath 有无 commons-pool2** 决定
        //     ⇒ 必须像本条这样**读真装配出来的 ClientConfiguration**。
        assertThat(connectionFactory.getClientConfiguration())
                .as("🔴 Lettuce 连接池必须**启用**（Go 侧 go-redis 默认有池：PoolSize = 10 × 核数）。"
                        + "Boot 4 的判据是 `enabled != null ? enabled : COMMONS_POOL2_AVAILABLE`"
                        + " ⇒ 若 yml 的 `spring.data.redis.lettuce.pool.enabled` 缺失，"
                        + "且 classpath 里没有 commons-pool2 ⇒ 池不启用 ⇒ Lettuce 走**共享单连接**"
                        + " ⇒ 并发下命令串行排队 ⇒ 可能撞 3s 命令超时。")
                .isInstanceOf(LettucePoolingClientConfiguration.class);
    }

    @Test
    @DisplayName("⑥ 连接池参数必须 = 64 / 16 / 8（min-idle = Go 的 MinIdleConns 8；max-active 按数量级）")
    void redisPoolParametersMatchGoBaseline() {
        // ⚠️ **逐项对着 Go 的真值**，不拿「现有 RAG 代码」当基准（AGENTS.md §3.1）：
        //   Go 侧（本域走 **go-zero 的 redis** ⇒ 底层仍是 go-redis v9.20.0）：
        //     · `MinIdleConns` = go-zero 的常量 `idleConns = 8`
        //       （`go-zero@v1.10.2/core/stores/redis/redisclientmanager.go:15` ⇒ `:38` 写进 `red.Options`）
        //       ⇒ **Go 侧有确切值** ⇒ 本项 **8 = 精确对齐**
        //       （🆕 2026-10-27 原为 4 ⇒ 那是**偏离 Go**；已修）
        //     · `PoolSize` 未设 ⇒ `go-redis@v9.20.0/options.go:350-351` `= 10 * GOMAXPROCS`
        //       ⇒ **机器相关**（本机 10 核 ⇒ 100）⇒ **无法逐值对齐** ⇒ 只按**数量级**对齐 64。
        //     · 「最大空闲连接数」在 go-redis **无对应项**（不限空闲连接数，靠 `IdleTimeout` 回收）
        //       ⇒ `max-idle` 是**纯表达层**取值。
        //   消费点（Boot 4 `LettuceConnectionConfiguration`）：
        //     · `:162-165` `createBuilder` ⇒ 池启用时走 `PoolBuilderFactory`
        //     · `:264` `LettucePoolingClientConfiguration.builder().poolConfig(getPoolConfig(properties))`
        //     · `:267-271` `getPoolConfig` ⇒ `:269 setMaxTotal(maxActive)` · `:270 setMaxIdle(maxIdle)` · `:271 setMinIdle(minIdle)`
        GenericObjectPoolConfig<?> poolConfig = poolConfig();
        assertThat(poolConfig.getMaxTotal())
                .as("🔴 连接池最大连接数必须 = 64（Boot 4 `LettuceConnectionConfiguration:269` "
                        + "`config.setMaxTotal(properties.getMaxActive())`）"
                        + " ⇒ Go 侧是 `10 × 核数`（本机 100，机器相关 ⇒ **数量级**对齐，非逐值对齐）"
                        + " ⇒ 数值差异属**性能面**，已登记在 `DRIFT-LIKE-RPC-REDIS-POOL`")
                .isEqualTo(64);
        assertThat(poolConfig.getMaxIdle())
                .as("连接池最大空闲连接数必须 = 16（Boot 4 `:270` `setMaxIdle`）"
                        + " ⇒ Go 侧**无对应项**（go-redis 不限空闲连接数）⇒ 纯表达层取值")
                .isEqualTo(16);
        assertThat(poolConfig.getMinIdle())
                .as("🔴 连接池最小空闲连接数必须 = 8（Boot 4 `:271` `setMinIdle`）"
                        + " ⇒ Go 侧 `MinIdleConns = 8`（go-zero 的 `idleConns = 8`）⇒ **精确对齐**"
                        + " ⇒ 写成 4 会变成**对 Go 的偏离**")
                .isEqualTo(8);
    }

    /** 池配置（**读消费点**：Boot 装配出来的 `LettucePoolingClientConfiguration`）。 */
    private GenericObjectPoolConfig<?> poolConfig() {
        assertThat(connectionFactory.getClientConfiguration())
                .as("Lettuce 连接池必须已启用（否则读不到池参数 ⇒ 断言 ⑥ 无法成立）")
                .isInstanceOf(LettucePoolingClientConfiguration.class);
        return ((LettucePoolingClientConfiguration) connectionFactory.getClientConfiguration()).getPoolConfig();
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
