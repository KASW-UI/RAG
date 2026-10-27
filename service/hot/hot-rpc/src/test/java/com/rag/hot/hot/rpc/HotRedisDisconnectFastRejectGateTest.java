package com.rag.hot.hot.rpc;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientOptionsBuilderCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 🔴 <b>hot 域 §5.3e 项②：共享层那份 {@code REJECT_COMMANDS} 在本域【真的生效】的活体对拍</b>。
 *
 * <h2>🔴 这条补的是哪一个缺口</h2>
 * <p>本组件**用过一次真 Redis 容器**的断连实测，但那次测的是「<b>配了</b> {@code REJECT_COMMANDS} 有没有用」
 * ⇒ ⇒ 证明的是「<b>该配置项有效</b>」，**不是**「<b>共享层那份已经在本域生效</b>」
 * ⇒ ⇒ 共享层那份落地之后，必须在本域<b>不配任何域内配置</b>、让共享层那份生效，再跑一次断连活体。
 * <p>⇒ ⇒ 本类做这件事，而且用<b>对照组</b>证明「快失败」这件事是被那条机制决定的。
 *
 * <h2>三段判据</h2>
 * <ol>
 *   <li><b>共享层那份 customizer 在场</b>：{@code getBeansOfType(LettuceClientOptionsBuilderCustomizer.class)}
 *       的 size <b>必须 == 1</b>，且 bean 名含 {@code goStyleRedisClientOptionsCustomizer}
 *       ⇒ ⇒ <b>同时钉住「遮蔽契约」</b>：共享层那份带 {@code @ConditionalOnMissingBean}
 *       ⇒ ⇒ <b>域内若自己声明一个，本层就退让</b> ⇒ ⇒ 那时 size 可能是 1 但不是共享层那个
 *       ⇒ ⇒ <b>size == 1 ＋ 名字对得上</b> 两条合起来才证明「用的就是共享层那份、没有域内副本遮蔽」</li>
 *   <li><b>断连行为真的被设成 {@code REJECT_COMMANDS}</b>：<b>消费点</b> =
 *       {@code connectionFactory.getNativeClient().getOptions().getDisconnectedBehavior()}
 *       ⇒ ⇒ <b>不是</b>去读某个配置项（那只是「声明」）</li>
 *   <li><b>断连活体 A/B</b>：真 Redis 容器 ⇒ 连上 ⇒ PING ⇒ <b>静默等待（期间不发命令）</b>
 *       ⇒ {@code docker stop}（优雅 FIN，不是 kill 的 RST）⇒ ⇒ 量<b>第一条命令</b>的耗时
 *       ⇒ ⇒ <b>A 组</b> = 共享层产出的那套 ClientOptions（REJECT_COMMANDS）
 *       ⇒ <b>B 组</b> = 同样只把断连行为换成 Lettuce 默认的 {@code DEFAULT}
 *       ⇒ ⇒ **A 必须快速失败、B 必须排队到命令超时** ⇒ ⇒ <b>这才是「机制是承重的」的证据</b></li>
 * </ol>
 *
 * <h2>Go 侧对照（真源 · go-redis v9.20.0）</h2>
 * <p>Go 侧走 go-redis 的 {@code Options} ⇒ <b>断连即拒连、快速失败</b>
 * ⇒ ⇒ RAG 侧必须落在同一个量级（**毫秒级**），而不是「排队等到超时」。
 *
 * <h2>⚠️ 与「池耗尽」是两件事（本域现在有池 ⇒ 必须分开判）</h2>
 * <p>本域在 2026-10-27 已把 Redis <b>连接池打开</b>（对齐 Go 的「有池」）⇒ ⇒ 于是断连有<b>两种形态</b>：
 * <table border="1">
 *   <caption>必须分开判的两条</caption>
 *   <tr><th>形态</th><th>触发条件</th><th>等待语义</th><th>本类覆盖</th></tr>
 *   <tr><td><b>池里有空闲连接</b></td><td>断连时池里还有可用连接</td>
 *       <td>取到的那条连接已死 ⇒ 取决于<b>断连行为</b>（REJECT_COMMANDS ⇒ 立即拒单）</td>
 *       <td>✅ <b>本类测的就是这一条</b></td></tr>
 *   <tr><td><b>池已耗尽</b></td><td>所有连接都在用 / 池空</td>
 *       <td>借连接时的等待上限（{@code max-wait}）⇒ Go 侧 4s ⇒ RAG 侧靠 {@code max-wait: 4s} 对齐</td>
 *       <td>❌ 不在本类（已由池守门覆盖）</td></tr>
 * </table>
 * ⇒ ⚠️ **别把这两条混为一谈** —— 混了就会得出「断连会挂 4s」这种**只对池耗尽成立**的结论。
 *
 * <h2>⚠️ 口径边界</h2>
 * <ul>
 *   <li>本类需要 <b>docker</b> ⇒ ⇒ 无 docker 时用 {@code assumeTrue} <b>整类跳过</b>（记「skipped」，不记「通过」）</li>
 *   <li>本类测的是 <b>RAG 侧单边</b>的断连行为 ⇒ ⇒ <b>不</b>重新做 Go 侧 ⇒ ⇒ 因此本类<b>不</b>主张
 *       「双侧活体对拍」，只主张「<b>RAG 侧在共享层那份生效的前提下</b>的断连行为」</li>
 *   <li><b>不外推</b>：like / task / users / admin 各域的域上下文不同 ⇒ customizer 数量可能不同
 *       ⇒ 行为可能不同 ⇒ ⇒ **每个域自己跑一遍**才是证据（同一机制、不同域不互相代替）</li>
 * </ul>
 */
@SpringBootTest(
        classes = HotRpcApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                // 🔴 专属上下文缓存键：Spring 的 context cache key **包含 properties**，
                //    但**不含 @DynamicPropertySource 的值** ⇒ 若与同模块另一个 @SpringBootTest
                //    的 properties 恰好相同 ⇒ 两者**共享同一个 Spring 上下文**
                //    ⇒ 本类的断言可能读到别的测试装配出来的 factory。
                "rag.test.gate.context-key=hot-redis-disconnect-reject",

                "grpc.server.port=0",
                // 绕过 Nacos：注册在 ApplicationReadyEvent 且已 try/catch，但 devh 的发现配置仍会连
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "logging.level.com.alibaba.nacos=WARN",
                "management.otlp.tracing.export.enabled=false",
                // 绕过 Kafka / Redis 的真实连接（本类自己起容器）
                "spring.kafka.bootstrap-servers=127.0.0.1:1",
                "spring.data.redis.host=127.0.0.1",
                "spring.data.redis.port=1",
                // ⚠️ 本模块**没有** Flyway 依赖（无 PG / 无迁移脚本）⇒
                //    **不能**用 spring.autoconfigure.exclude 排它 ——
                //    排一个 classpath 上不存在的自动配置类 ⇒ Boot 直接启动失败。
                //    ⇒ 这里什么都不排。
                "spring.flyway.enabled=false"
        })
@DisplayName("hot-rpc：共享层那份 REJECT_COMMANDS 在本域真的生效（活体断连 A/B）")
class HotRedisDisconnectFastRejectGateTest {

    @Autowired
    LettuceConnectionFactory connectionFactory;

    @Autowired
    ApplicationContext context;

    // ------------------------------------------------------------------ 判据 ①②

    @Test
    @DisplayName("① 共享层那份 customizer 在场且【没被域内副本遮蔽】＋ ② 断连行为 = REJECT_COMMANDS")
    void sharedLayerCustomizerIsInEffectAndNothingShadowsIt() {
        Map<String, LettuceClientOptionsBuilderCustomizer> customizers =
                context.getBeansOfType(LettuceClientOptionsBuilderCustomizer.class);

        assertThat(customizers)
                .as("① 共享层那份 customizer 必须在场（共享层用 @AutoConfiguration + 自动配置清单装上）")
                .hasSize(1);
        assertThat(customizers.keySet())
                .as("① 且必须就是共享层那一个（bean 名固定 goStyleRedisClientOptionsCustomizer）"
                        + " ⇒ ⇒ 域内若自己声明一个，共享层那份会退让 ⇒ ⇒ 名字对得上才说明没被遮蔽")
                .anyMatch(name -> name.contains("goStyleRedisClientOptionsCustomizer"));

        // ⚠️ 消费点 = **真在跑的客户端**的选项 ⇒ ⇒ 不是读配置项、也不是读 ClientConfiguration
        ClientOptions options = connectionFactory.getNativeClient().getOptions();
        assertThat(options)
                .as("② 消费点 = LettuceConnectionFactory.getNativeClient().getOptions()（不是读配置项）")
                .isNotNull();
        assertThat(options.getDisconnectedBehavior())
                .as("② 共享层那份把断连行为设成 REJECT_COMMANDS ⇒ 断连即拒单，不排队等重连")
                .isEqualTo(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS);
    }

    // ------------------------------------------------------------------ 判据 ③（活体 A/B）

    @Test
    @DisplayName("③ 断连活体 A/B：REJECT_COMMANDS 必须【毫秒级失败】，Lettuce 默认必须【排队到命令超时】")
    void disconnectIsFastRejectedWithSharedLayerOptions() {
        assumeTrue(dockerAvailable(), "本机没有可用的 docker ⇒ 本条整类跳过（记 skipped，不记通过）");

        // A 组 = 共享层产出的那套 ClientOptions（REJECT_COMMANDS）
        ClientOptions sharedOptions = connectionFactory.getNativeClient().getOptions();
        // B 组 = 同一套，只把断连行为换成 Lettuce 默认的 DEFAULT
        ClientOptions defaultOptions = ClientOptions.builder()
                .timeoutOptions(sharedOptions.getTimeoutOptions())
                .socketOptions(sharedOptions.getSocketOptions())
                .build();

        long rejectMs = measureFirstCommandAfterDisconnect(sharedOptions, "g3-hot-redis-reject");
        long defaultMs = measureFirstCommandAfterDisconnect(defaultOptions, "g3-hot-redis-default");

        // 📊 实测值打印出来（台账要引**实测**，不引「预期」）
        System.out.println("[G3-REJECT-PROBE] hot A(REJECT_COMMANDS)=" + rejectMs
                + "ms  B(DEFAULT)=" + defaultMs + "ms  差=" + (defaultMs - rejectMs) + "ms");

        assertThat(rejectMs)
                .as("③ A 组（REJECT_COMMANDS）：断连后第一条命令必须**快速失败**"
                        + " ⇒ ⇒ Go 侧同款是毫秒级 ⇒ ⇒ 绝不能撞 3s 命令超时")
                .isLessThan(1000L);
        assertThat(defaultMs)
                .as("③ B 组（Lettuce 默认 DEFAULT）：命令会**排队等重连** ⇒ ⇒ 必须等到命令超时"
                        + " ⇒ ⇒ 这条是**对照组** ⇒ ⇒ 它证明上一条测到的「快」是被机制决定的，"
                        + "不是「反正都快」")
                .isGreaterThanOrEqualTo(2000L);
        assertThat(rejectMs)
                .as("③ A 与 B 的差距 = 机制的实际收益（毫秒级 vs 秒级）")
                .isLessThan(defaultMs);
    }

    /**
     * 连上 → PING → <b>静默等待</b>（期间不发命令，否则 watchdog 重连会掩盖排队窗口）
     * → {@code docker stop}（优雅 FIN）→ 量第一条命令的耗时。
     */
    private long measureFirstCommandAfterDisconnect(ClientOptions options, String containerName) {
        // 🔴 每组**各自**取一个空闲端口（不能复用同一个：容器还在占着）
        int port;
        try {
            port = freePort();
        } catch (IOException e) {
            throw new IllegalStateException("取空闲端口失败", e);
        }
        String name = containerName + "-" + port;
        // 起容器给本组用
        String out = docker("run", "-d", "--rm", "--name", name,
                "-p", "127.0.0.1:" + port + ":6379", "redis:7-alpine");
        assertThat(out).as("起 %s 容器失败", name).startsWith("0|");
        boolean ready = false;
        for (int i = 0; i < 60; i++) {
            if (redisPing(port)) {
                ready = true;
                break;
            }
            sleep(200);
        }
        assertThat(ready).as("容器 %s 里的 Redis 未就绪", name).isTrue();

        RedisURI uri = RedisURI.create("redis://127.0.0.1:" + port);
        uri.setTimeout(Duration.ofSeconds(3));   // 与本域 application.yml 的 timeout: 3s 一致
        RedisClient client = RedisClient.create(uri);
        // ⚠️ Lettuce 6.x 没有 create(RedisURI, ClientOptions) 重载 ⇒ ⇒ 只能建好再 setOptions
        //    （**必须在 connect 之前** ⇒ 否则已建立的连接不会换选项）
        client.setOptions(options);
        StatefulRedisConnection<String, String> connection = client.connect();
        try {
            assertThat(connection.sync().ping())
                    .as("连上后 PING 必须通（否则测的不是「断连」而是「连不上」）")
                    .isEqualTo("PONG");
            // 🔴 静默等待：期间**不发任何命令**（发了会被 watchdog 重连掩盖排队窗口）
            sleep(1000);
            // 优雅 FIN（不是 kill 的 RST）
            docker("stop", name);
            // 断连后第一条命令 ⇒ 量它多久失败
            long t0 = System.nanoTime();
            try {
                connection.sync().get("g3-probe-key");
            } catch (Exception expected) {
                // 断连后必然抛异常（拒单 / 命令超时）⇒ 这里只关心耗时
            }
            long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
            return elapsedMs;
        } finally {
            try {
                connection.close();
            } catch (Exception ignored) {
                // 关连接失败不影响结论
            }
            client.shutdown();
            docker("rm", "-f", name);
        }
    }

    // ------------------------------------------------------------------ 工具

    private static boolean dockerAvailable() {
        return docker("info", "--format", "{{.ServerVersion}}").startsWith("0|");
    }

    /**
     * 返回 "&lt;exitCode&gt;|&lt;combined output&gt;"。
     * ⚠️ **本方法不抛异常** ⇒ ⇒ 失败时返回 {@code "-1|<原因>"} ⇒ ⇒
     * 调用方一律用「是否以 {@code 0|} 开头」判断成败（而不是靠 try/catch）⇒ ⇒
     * 避免「docker 不存在」这种环境问题把整个测试类变成编译/运行错误。
     */
    private static String docker(String... args) {
        try {
            // ⚠️ ProcessBuilder **没有** add(String...) ⇒ ⇒ 只能用 command(List<String>)
            List<String> cmd = new ArrayList<>();
            cmd.add("docker");
            cmd.addAll(Arrays.asList(args));
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int code = p.waitFor();
            return code + "|" + out;
        } catch (Exception e) {
            return "-1|" + e.getClass().getName() + ": " + e.getMessage();
        }
    }

    private static boolean redisPing(int port) {
        // ⚠️ RedisClient **不是** AutoCloseable ⇒ ⇒ 不能用 try-with-resources
        RedisClient client = null;
        StatefulRedisConnection<String, String> c = null;
        try {
            client = RedisClient.create("redis://127.0.0.1:" + port);
            c = client.connect();
            return "PONG".equals(c.sync().ping());
        } catch (Exception e) {
            return false;
        } finally {
            if (c != null) {
                try {
                    c.close();
                } catch (Exception ignored) {
                    // 忽略
                }
            }
            if (client != null) {
                client.shutdown();
            }
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private static void sleep(long ms) {
        try {
            TimeUnit.MILLISECONDS.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
