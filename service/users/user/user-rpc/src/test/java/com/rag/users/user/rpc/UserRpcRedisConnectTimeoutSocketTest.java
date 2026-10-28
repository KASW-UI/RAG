package com.rag.users.user.rpc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * user-rpc 的 Lettuce <b>socket 层</b>连接超时守门 + <b>命令超时消费层</b>守门
 * （2026-10-27 · G1 lane · DRIFT-USERRPC-V-014）。
 *
 * <h2>这条补的是哪一层</h2>
 * <pre>
 *   ① 文本层：yml 里写了 timeout: 3s / connect-timeout: 5s   ✅ {@link UserRpcRedisTimeoutConfigTest}
 *   ② 绑定层：Spring Binder 真把它们绑进 DataRedisProperties ✅ 同上
 *   ③ 消费层：Lettuce 客户端里真的拿到 3s / 5s              ← **本类**
 * </pre>
 *
 * <h2>③ 为什么不能靠读代码就算「已证」</h2>
 * <p>AGENTS.md §3.3.1：判断「Java 框架能力是否生效」必须有<b>运行证据</b>，「配置里写了」不算。
 * 本类因此做两件事，缺一不可：
 * <ol>
 *   <li><b>配置消费点</b>：断言 Lettuce 客户端里 {@code ClientOptions.socketOptions.connectTimeout}
 *       真的等于 5s、{@code LettuceClientConfiguration.commandTimeout} 真的等于命令超时。</li>
 *   <li><b>socket 层实测</b>：把连接指向一个<b>黑洞 IP</b>（TEST-NET-1 {@code 192.0.2.1}，
 *       RFC 5737 保留段，TCP SYN 发出去<b>永不回包</b>），然后真去 ping 一次，量它多久失败。</li>
 * </ol>
 *
 * <h2>🔴 命令超时的消费层判据（2026-10-27 按 G2 的建议核过一遍）</h2>
 * <pre>
 *   ✅ 正确：`cf.getClientConfiguration().getCommandTimeout()`
 *      —— LettuceClientConfiguration 上的**独立**字段（spring-data-redis 4.0.7 javap 实测）。
 *      Boot 4.0.8 LettuceConnectionConfiguration.applyProperties 的字节码：
 *        Duration timeout = getProperties().getTimeout();
 *        if (timeout != null) { builder.commandTimeout(timeout); }        ← applyProperties
 *      ⇒ spring.data.redis.timeout 真的流到了客户端的 commandTimeout。
 *
 *   ❌ 错误：断言 `ClientOptions.getTimeoutOptions()` 的 source 类型
 *      —— Boot 只写 `TimeoutOptions.enabled()`（启用机制，source 仍是 DefaultTimeoutSource ⇒ 返回 -1）
 *      ⇒ 断言它必然恒定 ⇒ 是假断言。
 *
 *   ❌ 也不存在：`io.lettuce.core.ClientOptions.getCommandTimeout()`
 *      —— lettuce 6.8.2 的 ClientOptions **没有**这个方法（javap 实测：只有 getTimeoutOptions），
 *         命令超时不挂在它上面 ⇒ 照抄会编译不过。
 * </pre>
 *
 * <h2>为什么这个时长区间是有判别力的</h2>
 * <pre>
 *   ≈5s     ← 本配置的 connect-timeout: 5s          ✅ 期望值
 *   ≈10s    ← Lettuce 自己的默认值（SocketOptions.DEFAULT_CONNECT_TIMEOUT_DURATION = 10s）
 *             ⇒ 出现这个值就说明 connect-timeout **没生效**（Boot 4 不回落到 timeout！）
 *   ≈30s    ← 本测试把命令超时覆盖成了 30s ⇒ 若测出这个值，说明测的是命令超时不是连接超时
 *   ≈75s    ← macOS 的 TCP connect 系统默认（内核 SYN 重传耗尽）
 * </pre>
 * ⇒ 断言区间取 {@code [4000ms, 9500ms)}：**只** 5s 落得进去。
 *
 * <h2>⚠️ 黑洞的可用性是环境相关的 ⇒ 用 assumption 而不是硬失败</h2>
 * <p>某些网络环境里 {@code 192.0.2.1} 会直接返回「no route to host」（几毫秒就失败），
 * 那时测到的不是超时而是<b>路由错误</b> ⇒ 时长断言没有意义。
 * ⇒ <b>配置消费点的断言永远生效</b>，时长断言只在黑洞确实黑洞时生效。
 *
 * <h2>Go 出处（决定「5s / 3s」这两个数字，不是拍脑袋）</h2>
 * <pre>
 *   Go service/user/user/rpc/etc/user.yaml:17-20：BizRedis 段**没有**任何 Timeout 字段
 *     → go-zero v1.10.2 core/stores/redis/redisclientmanager.go:32-39
 *       建 red.Options 时只传 Addr/User/Pass/DB/MaxRetries/MinIdleConns/TLSConfig
 *       —— **三个 timeout 一个都没传**
 *     → github.com/redis/go-redis/v9 options.go 的 init()：
 *         DialTimeout = 5s        （:329-330）
 *         ReadTimeout = 3 * time.Second
 * </pre>
 *
 * <p>⚠️ 红鲱鱼：go-zero {@code core/stores/redis/redis.go:27} 写着
 * {@code readWriteTimeout = 2 * time.Second} —— <b>它是死常量，全文件仅 BLPop 用</b>。
 * 只看这一行会得出「Go 的 Redis 超时是 2s」的错误结论。
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
        classes = UserRpcRedisConnectTimeoutSocketTest.RedisOnlyApp.class,
        properties = {
                // 黑洞 IP：RFC 5737 的 TEST-NET-1，TCP SYN 发出去永不回包
                "spring.data.redis.host=192.0.2.1",
                "spring.data.redis.port=6379",
                // ⚠️ **把命令超时拉到 30s**，为的是**隔离出 connect 超时**。
                //   Lettuce 的连接是**异步**的：`getConnection()` 立刻返回，
                //   真正的 TCP 握手在后台进行 ⇒ 命令是排在「还没连上的通道」上的。
                //   于是**先到期的往往是命令超时（timeout: 3s），而不是 connect 超时（5s）**。
                //   把命令超时拉长到远大于 connect 超时之后，
                //   **唯一**可能先到的就是 connect 超时 ⇒ 测出来的时长才有判别力。
                //   ⚠️ 副作用（有意为之）：本测试里 commandTimeout 的期望值随之变成 30s，
                //      而**真实配置值 3s** 由 {@link UserRpcRedisTimeoutConfigTest}（文本层 + 绑定层）钉死
                //      ⇒ 两条合起来 = 「属性是 3s」＋「属性确实流到了客户端的 commandTimeout」。
                "spring.data.redis.timeout=30s"
        })
class UserRpcRedisConnectTimeoutSocketTest {

    /** 黑洞 IP —— TEST-NET-1（RFC 5737 保留段，公网不可路由）。 */
    private static final String BLACKHOLE = "192.0.2.1";

    /** 本测试把命令超时覆盖成 30s（见 @SpringBootTest 的 properties）⇒ 消费层的期望值随之变化。 */
    private static final Duration EXPECTED_COMMAND_TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    RedisConnectionFactory connectionFactory;

    /**
     * 只导入 Redis 自动配置的最小上下文。
     *
     * <p>刻意<b>不</b>用 {@code @SpringBootApplication} / {@code @EnableAutoConfiguration}：
     * 那会把 datasource（要 PG）/ nacos 一起拖起来，而本测试跟它们毫无关系。
     */
    @SpringBootConfiguration
    @ImportAutoConfiguration(DataRedisAutoConfiguration.class)
    static class RedisOnlyApp {
    }

    @Test
    @DisplayName("✅ Lettuce 客户端真的拿到 connectTimeout=5s（不是只写在 yml 里、也不是 Lettuce 的 10s 默认）")
    void lettuceHonoursFiveSecondConnectTimeoutAtSocketLayer() {
        LettuceConnectionFactory cf = (LettuceConnectionFactory) connectionFactory;

        // ── ① 配置消费点：连接超时 ─────────────────────────────────────
        // ⚠️ 取值路径是 `getClientConfiguration().getClientOptions()` ——
        //   `LettuceConnectionFactory` **没有** `getClientOptions()` 这个方法
        //   （spring-data-redis 4.0.7 的 javap 实测），别照抄别处的写法。
        Duration configured = cf.getClientConfiguration()
                .getClientOptions()
                .map(io.lettuce.core.ClientOptions::getSocketOptions)
                .map(io.lettuce.core.SocketOptions::getConnectTimeout)
                .orElse(null);
        assertEquals(Duration.ofSeconds(5), configured,
                "Lettuce 客户端的 socket connectTimeout 必须等于 5s（= go-redis DialTimeout 默认）。"
                        + "拿不到 5s 说明 connect-timeout 没被绑进/没被 Boot 消费。"
                        + "⚠️ 若是 10s，那是 Lettuce 的默认值（SocketOptions.DEFAULT_CONNECT_TIMEOUT_DURATION）"
                        + "⇒ 说明这个属性压根没生效 —— "
                        + "Boot 4 在 connect-timeout 为 null 时**整段跳过**、**不会**回落到 timeout！");

        // ── ② 配置消费点：命令超时 ─────────────────────────────────────
        // ⚠️ 判据必须是 `getClientConfiguration().getCommandTimeout()`：
        //   · `io.lettuce.core.ClientOptions` 在 lettuce 6.8.2 **没有** getCommandTimeout()
        //   · `ClientOptions.getTimeoutOptions()` 的 source 恒为 DefaultTimeoutSource ⇒ 假断言
        Duration commandTimeout = cf.getClientConfiguration().getCommandTimeout();
        assertEquals(EXPECTED_COMMAND_TIMEOUT, commandTimeout,
                "Lettuce 客户端的 commandTimeout 必须等于本测试覆盖后的 30s —— "
                        + "这条断言证明「spring.data.redis.timeout 真的流到了客户端的 commandTimeout」"
                        + "（Boot 4.0.8 LettuceConnectionConfiguration.applyProperties → "
                        + "LettuceClientConfigurationBuilder.commandTimeout）。"
                        + "真实配置值 3s 由 UserRpcRedisTimeoutConfigTest 钉死。");
    }

    @Test
    @DisplayName("✅ 连接黑洞的失败耗时落在 [4000ms, 9500ms) —— 只容得下 5s")
    void blackholeConnectFailsAtFiveSeconds() {
        LettuceConnectionFactory cf = (LettuceConnectionFactory) connectionFactory;

        // 分两段量，好看清「时间花在哪」：
        //   getConnection() —— 拿到连接对象（Lettuce 的握手是**异步**的，这一步通常很快）
        //   ping()         —— 真正要等「连不上」的那一步
        long t0 = System.nanoTime();
        long tConn = t0;
        try {
            RedisConnection conn = cf.getConnection();
            tConn = System.nanoTime();
            try {
                conn.ping();
            } finally {
                conn.close();
            }
        } catch (Exception expected) {
            // 连黑洞必须失败 —— 这是预期结果，不是错误
        }
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
        long connectMs = (tConn - t0) / 1_000_000L;

        // 把实测值打出来：台账要引用**具体数字**，不能只写「断言通过」
        System.out.printf(
                "[Lettuce socket 实测] 配置的 connectTimeout=%s  实测失败耗时=%dms（其中 getConnection %dms）%n",
                Duration.ofSeconds(5), elapsedMs, connectMs);

        // ⚠️ 黑洞的可用性是环境相关的：某些网络里 192.0.2.1 会直接「no route to host」，
        //    那样测到的是路由错误、不是超时 ⇒ 时长断言无意义，跳过（但消费点断言不受影响）。
        assumeTrue(elapsedMs >= 1000,
                "192.0.2.1 在本环境不是黑洞（" + elapsedMs + "ms 就失败了，可能是 no route to host）"
                        + " ⇒ 跳过 socket 层时长断言；配置消费点的断言不受影响");

        assertTrue(elapsedMs >= 4000 && elapsedMs < 9500,
                "连接黑洞的失败耗时应落在 [4000ms, 9500ms) —— 该区间**只**容得下 5s。"
                        + "实测 " + elapsedMs + "ms（其中 getConnection 用了 " + connectMs + "ms）。"
                        + "≈10s ⇒ Lettuce 默认值（connect-timeout 没生效）；"
                        + "≈30s ⇒ 测到的是命令超时（本测试已把 timeout 覆盖成 30s，出现 30s 说明覆盖没生效）；"
                        + "≈75s ⇒ TCP 系统默认（本配置没生效）");
    }
}
