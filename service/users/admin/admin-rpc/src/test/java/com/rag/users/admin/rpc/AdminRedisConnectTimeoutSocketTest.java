package com.rag.users.admin.rpc;

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

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 🔴 UNRESOLVED-ARPC-003 的**最后一层**守门 —— Lettuce 在 socket 层真按 5s 连接超时。
 *
 * <h2>这条补的是哪一层</h2>
 * <p>此前已有两层证据（见 {@code AdminRedisTimeoutConfigTest}）：
 * <pre>
 *   ① 文本层：yml 里写了 timeout: 3s / connect-timeout: 5s   ✅ 已证
 *   ② 绑定层：Spring Binder 真把它们绑进 DataRedisProperties    ✅ 已证
 *   ③ 消费层：Lettuce 在 socket 层真按 5s 超时                   ← **本类**
 * </pre>
 *
 * <h2>③ 为什么不能靠读代码就算「已证」</h2>
 * <p>AGENTS.md §3.3.1：判断「Java 框架能力是否生效」必须有<b>运行证据</b>，
 * 「配置里写了」不算。本类因此做两件事，缺一不可：
 * <ol>
 *   <li><b>配置消费点</b>：断言 Lettuce 客户端里 {@code ClientOptions.socketOptions.connectTimeout}
 *       真的等于 5s。Boot 4.0.8 的接线在
 *       {@code org.springframework.boot.data.redis.autoconfigure.LettuceConnectionConfiguration:212-215}：
 *       <pre>
 *         Duration connectTimeout = getProperties().getConnectTimeout();
 *         if (connectTimeout != null) {
 *             builder.socketOptions(SocketOptions.builder().connectTimeout(connectTimeout).build());
 *         }
 *       </pre>
 *       ⇒ 属性名写错 / 前缀不对 / 类型不匹配时，这里都拿不到 5s。
 *       （注意 Boot 4 里 {@code RedisProperties} 已从 {@code spring-boot-autoconfigure}
 *       拆到 {@code spring-boot-data-redis} 模块，依赖没引对时<b>整个属性类都不存在</b>，
 *       而应用照常启动 —— 这正是 §3.3 说的静默失效。）</li>
 *   <li><b>socket 层实测</b>：把连接指向一个<b>黑洞 IP</b>（TEST-NET-1 {@code 192.0.2.1}，
 *       RFC 5737 保留段，TCP SYN 发出去<b>永不回包</b>），
 *       然后真去 ping 一次，量它多久失败。</li>
 * </ol>
 *
 * <h2>为什么这个时长区间是有判别力的</h2>
 * <p>同一个「失败耗时」有好几个可能的来源，必须能把它们区分开：
 * <pre>
 *   ≈5s     ← 本配置的 connect-timeout: 5s          ✅ 期望值
 *   ≈10s    ← Lettuce 自己的默认值（SocketOptions.DEFAULT_CONNECT_TIMEOUT_DURATION = 10s）
 *             ⇒ 出现这个值就说明 connect-timeout **没生效**
 *   ≈3s     ← 🔴 **不应出现**：Boot 4 在 connect-timeout 为 null 时**整段跳过**（**不**回落 timeout）
 *             ⇒ 真实默认是上一行的 10s ⇒ 出现 3s 说明测到的是别的超时
 *   ≈75s    ← macOS 的 TCP connect 系统默认（内核 SYN 重传耗尽）
 * </pre>
 * ⇒ 断言区间取 {@code [4000ms, 9500ms)}：**只** 5s 落得进去。
 *
 * <h2>⚠️ 黑洞的可用性是环境相关的 ⇒ 用 assumption 而不是硬失败</h2>
 * <p>某些网络环境里 {@code 192.0.2.1} 会直接返回「no route to host」（几毫秒就失败），
 * 那时测到的不是超时而是<b>路由错误</b> ⇒ 时长断言没有意义。
 * ⇒ 本类把「黑洞是否真的黑洞」用 {@link org.junit.jupiter.api.Assumptions} 判掉：
 * <b>配置消费点的断言永远生效</b>，时长断言只在黑洞确实黑洞时生效。
 *
 * <h2>为什么用最小上下文而不是整个 admin-rpc</h2>
 * <p>本测试只关心 Redis 客户端，<b>不需要</b> PG / gRPC / Nacos。
 * 但它<b>必须</b>加载真实的 {@code application.yml}（否则测的是我自己写死的值）。
 * ⇒ 用 {@code @SpringBootTest(classes = RedisOnlyApp.class)}：
 * {@code @SpringBootTest} 仍会走完整的 config-data 流程 ⇒ <b>真实 yml 会被加载</b>，
 * 而 {@code @ImportAutoConfiguration(DataRedisAutoConfiguration.class)} 只导入 Redis 那一个自动配置
 * ⇒ 不会顺手把 datasource / nacos / kafka 全拖起来。
 *
 * <h2>Go 出处（决定「5s」这个数字，不是拍脑袋）</h2>
 * <pre>
 *   Go service/user/admin/rpc/etc/admin.yaml：BizRedis 段<b>没有</b>任何 Timeout 字段
 *     → go-zero v1.10.2 core/stores/redis/redisclientmanager.go:32-39
 *       建 red.Options 时只传 Addr/User/Pass/DB/MaxRetries/MinIdleConns/TLSConfig
 *       —— <b>三个 timeout 一个都没传</b>
 *     → github.com/redis/go-redis/v9 options.go 的 init()：
 *         DialTimeout = 5s        （:329-330）
 *         ReadTimeout = 3 * time.Second
 * </pre>
 * ⇒ Go 的<b>连接</b>超时是 <b>5s</b>、<b>读命令</b>超时是 <b>3s</b>。
 *
 * <p>⚠️ 红鲱鱼：go-zero {@code core/stores/redis/redis.go:27} 写着
 * {@code readWriteTimeout = 2 * time.Second} —— <b>它是死常量，全文件仅此一处、从未被使用</b>。
 * 只看这一行会得出「Go 的 Redis 超时是 2s」的错误结论。
 */
@SpringBootTest(
        classes = AdminRedisConnectTimeoutSocketTest.RedisOnlyApp.class,
        properties = {
                // 黑洞 IP：RFC 5737 的 TEST-NET-1，TCP SYN 发出去永不回包
                "spring.data.redis.host=192.0.2.1",
                "spring.data.redis.port=6379",
                // ⚠️ **把命令超时拉到 30s**，为的是**隔离出 connect 超时**。
                //   Lettuce 的连接是**异步**的：`getConnection()` 立刻返回，
                //   真正的 TCP 握手在后台进行 ⇒ 命令是排在「还没连上的通道」上的。
                //   于是**先到期的往往是命令超时（timeout: 3s），而不是 connect 超时（5s）**。
                //   第一版没覆盖这一项，实测 3266ms —— 那正是 timeout: 3s 到点，
                //   **不是** connect-timeout 到点 ⇒ 测的其实是命令超时，不是连接超时。
                //   把命令超时拉长到远大于 connect 超时之后，
                //   **唯一**可能先到的就是 connect 超时 ⇒ 测出来的时长才有判别力。
                "spring.data.redis.timeout=30s"
        })
class AdminRedisConnectTimeoutSocketTest {

    /** 黑洞 IP —— TEST-NET-1（RFC 5737 保留段，公网不可路由）。 */
    private static final String BLACKHOLE = "192.0.2.1";

    @Autowired
    RedisConnectionFactory connectionFactory;

    /**
     * 只导入 Redis 自动配置的最小上下文。
     *
     * <p>刻意<b>不</b>用 {@code @SpringBootApplication} / {@code @EnableAutoConfiguration}：
     * 那会把 datasource / nacos / kafka 一起拖起来（要 PG、要 Nacos），
     * 而本测试跟它们毫无关系。
     */
    @SpringBootConfiguration
    @ImportAutoConfiguration(DataRedisAutoConfiguration.class)
    static class RedisOnlyApp {
    }

    @Test
    @DisplayName("✅ Lettuce 在 socket 层真按 5s 连接超时（不是只写在 yml 里、也不是 Lettuce 的 10s 默认）")
    void lettuceHonoursFiveSecondConnectTimeoutAtSocketLayer() {
        LettuceConnectionFactory cf = (LettuceConnectionFactory) connectionFactory;

        // ── ① 配置消费点 ───────────────────────────────────────────────
        // ⚠️ 取值路径是 `getClientConfiguration().getClientOptions()` ——
        //   `LettuceConnectionFactory` **没有** `getClientOptions()` 这个方法
        //   （spring-data-redis 4.0.7 的 javap 实测），别照抄别处的写法。
        Duration configured = cf.getClientConfiguration()
                .getClientOptions()
                .map(io.lettuce.core.ClientOptions::getSocketOptions)
                .map(io.lettuce.core.SocketOptions::getConnectTimeout)
                .orElse(null);
        assertEquals(Duration.ofSeconds(5), configured,
                "Lettuce 客户端的 socket connectTimeout 必须等于 5s。"
                        + "拿不到 5s 说明 connect-timeout 没被绑进/没被 Boot 消费 —— "
                        + "Boot 4.0.8 的接线在 LettuceConnectionConfiguration:212-215。"
                        + "⚠️ 若是 10s，那是 Lettuce 的默认值（DEFAULT_CONNECT_TIMEOUT_DURATION），"
                        + "说明这个属性压根没生效");

        // ── ② socket 层实测 ───────────────────────────────────────────
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
                configured, elapsedMs, connectMs);

        // ⚠️ 黑洞的可用性是环境相关的：某些网络里 192.0.2.1 会直接「no route to host」，
        //    那样测到的是路由错误、不是超时 ⇒ 时长断言无意义，跳过（但①仍然有效）。
        assumeTrue(elapsedMs >= 1000,
                "192.0.2.1 在本环境不是黑洞（" + elapsedMs + "ms 就失败了，可能是 no route to host）"
                        + " ⇒ 跳过 socket 层时长断言；配置消费点的断言不受影响");

        assertTrue(elapsedMs >= 4000 && elapsedMs < 9500,
                "连接黑洞的失败耗时应落在 [4000ms, 9500ms) —— 该区间**只**容得下 5s。"
                        + "实测 " + elapsedMs + "ms（其中 getConnection 用了 " + connectMs + "ms）。"
                        + "≈10s ⇒ Lettuce 默认值（connect-timeout 没生效）；"
                        + "≈3s ⇒ 本测试已把 timeout 覆盖成 30s，出现 3s 说明覆盖没生效（**不是**「回落 timeout」）；"
                        + "≈75s ⇒ TCP 系统默认（本配置没生效）");
    }
}
