package com.rag.comment.comment.rpc.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;

/**
 * 🔴 Redis **连接池参数口径**守门（comment-rpc）—— 池参数必须逐项对着 Go 的真值，且**池耗尽不得无限等**。
 *
 * <h2>守的是什么漂移</h2>
 * <p>本组件<b>形态上已经是对的</b>（yml 配了 {@code lettuce.pool.enabled: true} ＋ pom 显式声明
 * {@code commons-pool2} ⇒ 池是开的）⇒ 但<b>参数</b>上有两处对 Go 的偏离：
 * <ol>
 *   <li>🔴 {@code min-idle: 4} —— 本域 Go 侧<b>直连 go-redis</b>（不是 go-zero）
 *       ⇒ {@code MinIdleConns} 未设 ⇒ {@code options.go:218-222} **无 default 赋值** ⇒ **0**
 *       ⇒ ⇒ 写成 4 是**对 Go 的偏离**（go-zero 的 {@code idleConns = 8} 只适用于<b>走 go-zero</b> 的域）。</li>
 *   <li>🔴 <b>缺 {@code max-wait}</b> —— 池耗尽时 Go **4s 失败**，Lettuce 默认**无限等**。</li>
 * </ol>
 *
 * <h2>Go 出处（消费点，不是声明点）</h2>
 * <pre>
 *   service/comment/rpc/internal/cache/redis.go:17-19
 *       rdb := redis.NewClient(&amp;redis.Options{Addr: Host})   ← **只设了 Addr**
 *     ⇒ PoolSize 未设 ⇒ go-redis v9.20.0 options.go:350-351 ⇒ PoolSize = 10 × GOMAXPROCS
 *     ⇒ MinIdleConns 未设 ⇒ options.go:218-222 **无 default** ⇒ **0**
 *     ⇒ MaxIdleConns 未设 ⇒ options.go:224-228 **无 default** ⇒ **0**（消费点 internal/pool/pool.go:1257 ⇒ 无上限）
 *     ⇒ PoolTimeout 未设 ⇒ options.go:380-386 ⇒ PoolTimeout = ReadTimeout + 1s = **4s**
 *       （消费点 internal/pool/pool.go:1113 p.semaphore.Acquire(ctx, p.cfg.PoolTimeout, ErrPoolTimeout)）
 * </pre>
 *
 * <h2>🔴 为什么 {@code max-wait} 不可省（这是全仓级共性缺陷）</h2>
 * <p>不配 ⇒ commons-pool2 2.12.1 {@code BaseObjectPoolConfig:59,68}
 * {@code DEFAULT_MAX_WAIT = Duration.ofMillis(-1L)} ⇒ 消费点 {@code GenericObjectPool.java:306}
 * {@code p = negativeDuration ? idleObjects.takeFirst() : idleObjects.pollFirst(remainingWaitDuration)}
 * ⇒ <b>负值 ⇒ 阻塞到有人还连接为止（无上限）</b>。
 * <p>🔴 <b>Lettuce 的 3s 命令超时救不了场</b>：{@code CommandExpiryWriter.write(command)} 才起算计时器
 * （实测 lettuce-core 6.8.2 字节码），而<b>借连接发生在写命令之前</b>
 * （{@code RedisTemplate.execute} → {@code getConnection()} → {@code borrowObject()} → 之后才 {@code write}）
 * ⇒ ⇒ 池耗尽时 3s 命令超时<b>根本没开始</b> ⇒ ⇒ <b>无限挂起</b>（Go 是 4s 失败）。
 *
 * <h2>为什么断言「真装配出来的池参数」而不是「yml 里有这几行」</h2>
 * <p>AGENTS.md §3.3：<b>配置存在 ≠ 机制生效</b>。本守门起<b>真</b> {@link ApplicationContextRunner}
 * ＋ Boot <b>真</b>的 {@link DataRedisAutoConfiguration} ⇒ 拿<b>真的</b> {@link LettuceConnectionFactory}
 * ⇒ 断言<b>消费点</b>：Boot 4 真正的装配点是
 * {@code LettuceConnectionConfiguration$PoolBuilderFactory.getPoolConfig}（实测字节码）
 * ⇒ {@code setMaxTotal} / {@code setMaxIdle} / {@code setMinIdle} / {@code setMaxWait}。
 * <p>⚠️ Boot 4.0.8 的 {@code LettuceConnectionConfiguration} <b>没有</b> {@code getPoolConfig} 方法
 * （Boot 2/3 才有）⇒ ⇒ 引用时以<b>类名 + 方法名</b>为准，不要照抄别处的行号。
 *
 * <h2>证据强度</h2>
 * <p>L3 静态实测（Boot 4.0.8 / commons-pool2 2.12.1 / lettuce-core 6.8.2 / go-redis v9.20.0 的
 * 字节码与源码逐条对照）＋ 本守门的<b>真装配</b>断言。
 * <p>⚠️ <b>不是双侧活体对拍</b>：Go 侧是<b>源码级</b>取证，<b>没有</b>同时跑的 Go 侧探针 ⇒ 按本仓口径只写 L3。
 */
class CommentRpcRedisPoolContractTest {

    /**
     * Go 的 {@code MinIdleConns}（go-redis v9.20.0 {@code options.go:218-222}：<b>无 default 赋值</b> ⇒ 0）。
     */
    private static final int GO_MIN_IDLE_CONNS = 0;

    /**
     * Go 的 {@code PoolTimeout}（go-redis v9.20.0 {@code options.go:380-386}：
     * {@code PoolTimeout = ReadTimeout + 1s}；本域 ReadTimeout = 3s）。
     */
    private static final Duration GO_POOL_TIMEOUT = Duration.ofSeconds(4);

    /** 池最大连接数（本仓取值；Go 侧是 {@code 10 × 核数}，机器相关 ⇒ 数量级对齐）。 */
    private static final int RAG_MAX_ACTIVE = 64;

    /** 池最大空闲连接数（本仓取值；Go 侧 {@code MaxIdleConns} 未设 ⇒ 0 = 无上限）。 */
    private static final int RAG_MAX_IDLE = 16;

    @Test
    @DisplayName("① Redis 连接池必须**启用**（Boot 4 的判据 = enabled ?? COMMONS_POOL2_AVAILABLE）")
    void redisPoolMustBeEnabled() {
        assertThat(poolConfiguration())
                .as("🔴 Lettuce 连接池必须**启用**：Go 侧 go-redis 默认有池（PoolSize = 10 × 核数）"
                        + " ⇒ 未启用时 Lettuce 走**共享单连接** ⇒ 并发下命令串行排队"
                        + " ⇒ 与 Go 差一整个池形态。"
                        + "⚠️ 启用但**参数错**是另一类漂移 ⇒ 由 ②③ 覆盖。")
                .isNotNull();
    }

    @Test
    @DisplayName("② 池参数必须是 64 / 16 / 0（min-idle 与 Go **精确**对齐，不是 4 也不是 8）")
    void redisPoolParametersMatchGoBaseline() {
        GenericObjectPoolConfig<?> poolConfig = poolConfig();

        assertThat(poolConfig.getMaxTotal())
                .as("🔴 连接池最大连接数必须 = 64（Boot 4 `PoolBuilderFactory.getPoolConfig` ⇒ `setMaxTotal(getMaxActive())`）"
                        + " ⇒ Go 侧是 `PoolSize = 10 × 核数`（本机 100，机器相关 ⇒ **数量级**对齐）"
                        + " ⇒ 数值差异属**性能面**。"
                        + "⚠️ Go 的 `MaxActiveConns` 未设 ⇒ `options.go:230-235` ⇒ Go 侧**无总量硬上限**")
                .isEqualTo(RAG_MAX_ACTIVE);

        assertThat(poolConfig.getMaxIdle())
                .as("连接池最大空闲连接数必须 = 16（Boot 4 ⇒ `setMaxIdle(getMaxIdle())`）"
                        + " ⇒ Go 侧 `MaxIdleConns` 未设 ⇒ `options.go:224-228` 无 default ⇒ 0"
                        + " ⇒ 消费点 `internal/pool/pool.go:1257` ⇒ **0 = 无上限**"
                        + " ⇒ 本项 16 是**表达层**取值（**比 Go 更严**，只影响空闲连接回收，不影响命令结果）")
                .isEqualTo(RAG_MAX_IDLE);

        assertThat(poolConfig.getMinIdle())
                .as("🔴 连接池最小空闲连接数必须 = 0（Boot 4 ⇒ `setMinIdle(getMinIdle())`）"
                        + " ⇒ Go 侧 `MinIdleConns` **未设** ⇒ `go-redis@v9.20.0/options.go:218-222` **无 default**"
                        + " ⇒ 保持 **0** ⇒ 本项是**精确对齐**。"
                        + "⚠️ **本域 Go 走 go-redis 直连**（`cache/redis.go:17-19` 只设 `Addr`）"
                        + " ⇒ **不是** go-zero 的 `idleConns = 8`（那是 like / favorite 域的口径）"
                        + " ⇒ **写成 4 或 8 都是对 Go 的偏离**")
                .isEqualTo(GO_MIN_IDLE_CONNS);
    }

    @Test
    @DisplayName("③ 池耗尽的等待上限必须是 4s（对齐 Go 的 PoolTimeout；不配 = 无限等）")
    void poolExhaustionWaitMustBeBounded() {
        assertThat(poolConfig().getMaxWaitDuration())
                .as("🔴 池耗尽的等待上限必须 = 4s（Go 的 `PoolTimeout = ReadTimeout + 1s`，ReadTimeout = 3s ⇒ 4s；"
                        + "`go-redis@v9.20.0/internal/pool/pool.go:1113` "
                        + "`p.semaphore.Acquire(ctx, p.cfg.PoolTimeout, ErrPoolTimeout)`）。"
                        + "不配 `max-wait` ⇒ 落 commons-pool2 的 `DEFAULT_MAX_WAIT = -1`"
                        + " ⇒ `GenericObjectPool:306` ⇒ **无限等**，且 3s 命令超时尚未起算"
                        + " ⇒ ⇒ **比 Go 更糟**（Go 4s 失败）")
                .isEqualTo(GO_POOL_TIMEOUT);
    }

    @Test
    @DisplayName("④ 阳性对照：commons-pool2 自己的默认 maxWait 是 -1（证明断言 ③ 有识别能力）")
    void commonsPoolDefaultMaxWaitIsNegativeOne() {
        assertThat(new GenericObjectPoolConfig().getMaxWaitDuration())
                .as("★ 阳性对照：commons-pool2 的**默认** maxWait 是 -1（= 无限等）"
                        + " ⇒ 若断言 ③ 恒真，说明它没在区分「配了 4s」与「没配」")
                .isEqualTo(Duration.ofMillis(-1L));
    }

    /** 池配置对象（**读消费点**：Boot 装配出来的 {@link LettucePoolingClientConfiguration}）。 */
    private LettucePoolingClientConfiguration poolConfiguration() {
        AtomicReference<LettucePoolingClientConfiguration> holder = new AtomicReference<>();
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withConfiguration(AutoConfigurations.of(DataRedisAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).as("Redis 自动配置必须装配成功").hasNotFailed();
                    LettuceConnectionFactory factory = context.getBean(LettuceConnectionFactory.class);
                    assertThat(factory.getClientConfiguration())
                            .as("🔴 Lettuce 连接池必须已启用（否则读不到池参数 ⇒ 断言 ②③ 无法成立）"
                                    + " ⇒ 未启用时装配出的是普通 `LettuceClientConfiguration`（共享单连接）")
                            .isInstanceOf(LettucePoolingClientConfiguration.class);
                    holder.set((LettucePoolingClientConfiguration) factory.getClientConfiguration());
                });
        LettucePoolingClientConfiguration configuration = holder.get();
        assertThat(configuration)
                .as("池配置必须取到（ApplicationContextRunner 未执行断言 ⇒ 上下文没起来）")
                .isNotNull();
        return configuration;
    }

    /** 池参数（**读消费点**：Boot 装配出来的 {@link GenericObjectPoolConfig}）。 */
    private GenericObjectPoolConfig<?> poolConfig() {
        return poolConfiguration().getPoolConfig();
    }
}
