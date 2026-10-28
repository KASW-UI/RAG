package com.rag.users.admin.api;

import com.rag.users.admin.api.client.AdminRpcClient;
import io.lettuce.core.ClientOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientOptionsBuilderCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.rag.users.admin.api.support.GoStyleHostPortPinning.pinContainerPort;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 **B 组反例：把断连策略换成 {@code DEFAULT} ⇒ 命令被排队 ⇒ 挂到 commandTimeout(3s)，而不是快速失败**
 * （2026-10-28 · G1 lane）
 *
 * <h2>这个类存在的理由（AGENTS.md §3.3「跑绿 ≠ 判据活着」）</h2>
 * <p>{@code AdminApiRedisDisconnectedFastFailTest}（A 组）证明了「RAG 侧断连时命令**快速失败**（&lt;1500ms）
 * ⇒ 进业务层 ⇒ fail-open 放行 ⇒ 200」，与 Go 真源同形。
 * <p><b>但 A 组单独存在时，证明不了「是那条机制决定的」</b> —— 它只证明「当前配置下是快速失败」，
 * 同样兼容「无论 Redis 怎么断连都会是快速失败」这个假设 ⇒ ⇒ **归因强度只能停在 L3**。
 * <p>本类提供<b>同场 B 组</b>：<b>唯一变量 = 断连策略</b> ⇒ ⇒ 与 A 组并排 ⇒ ⇒
 * <b>「是 REJECT_COMMANDS 这条决定的」才成立</b> ⇒ ⇒ 归因强度升 L4。
 *
 * <h2>怎么做到「唯一变量 = 那一条」</h2>
 * <p>共享层 {@code GoStyleRedisClientAutoConfiguration} 的 bean 带
 * {@code @ConditionalOnMissingBean} ⇒ ⇒ <b>域侧一旦已定义 {@code LettuceClientOptionsBuilderCustomizer}，
 * 共享层那份根本不会创建</b>（是「遮蔽」不是「叠加」）⇒ ⇒ 本类用一个 <b>test 侧</b>的
 * {@code @TestConfiguration} 提供一份值设成 {@code DEFAULT} 的 customizer ⇒ ⇒
 * 断连策略从 {@code REJECT_COMMANDS} 变成 {@code DEFAULT}，<b>其余全部不变</b>
 * （同款代理实现 / 同款故障形态 / 同款测点 / 同款 Redis 容器）。
 *
 * <h2>⚠️ 与 A 组共用【同一个】故障注入实现</h2>
 * <p>本类的代理实例直接复用 A 组那个 <b>package-private 嵌套类</b>
 * {@code AdminApiRedisDisconnectedFastFailTest.GracefulDisconnectProxy}
 * ⇒ ⇒ <b>是同一份字节码</b>（不是「照抄一份」）⇒ ⇒ 故障形态必然同款 ⇒ ⇒ 「同口径」成立。
 * <p>⚠️ <b>但必须是【两个独立实例】</b>：A 组的 {@code PROXY} 是 {@code static final}
 * ⇒ 两个类共用它 ⇒ A 组先断连后，B 组拿到的代理已经断了 ⇒ ⇒ 本类<b>自己建一份</b>
 * （同一个类、不同的实例、上游指向<b>自己的</b> Redis 容器）⇒ ⇒ 两组互不污染。
 *
 * <h2>预期结果（这就是这条轴的题眼）</h2>
 * <pre>
 *   A 组（REJECT_COMMANDS）：断连 ⇒ 命令【立即被拒】⇒ ~1ms ⇒ 失败耗时 ≪ 1500ms
 *   B 组（DEFAULT）      ：断连 ⇒ 命令【被排队】等重连 ⇒ 撞上 commandTimeout 3s
 *                            ⇒ 实测失败耗时 ≈3000ms（&lt;1500ms 的快速失败**不再成立**）
 * </pre>
 * ⇒ ⇒ **B 组挂到 3s 这件事，正是「A 组的快速失败不是白捡的」的证据** ⇒ ⇒
 * ⇒ ⇒ 也解释了为什么这条修复<b>必须做</b>：没有它，HTTP 预算（3s）会先于命令超时耗尽 ⇒ ⇒ 变成 503，而 Go 是 200。
 *
 * <h2>⚠️ 2026-10-28 第一版 B 组漏了一步 ⇒ 测出来的东西是错的</h2>
 * <p>漏掉的是「<b>先把连接建立起来</b>」：
 * <p>漏掉 ⇒ Lettuce <b>从未建立过连接</b> ⇒ ⇒ 断连后发的第一条命令是「<b>连接被拒</b>」
 * （proxy 监听口已关 ⇒ connection refused ⇒ <b>~15ms 快失败</b>）
 * ⇒ ⇒ 那样测的是「连不上」，<b>不是</b>「连上之后被断开」⇒ ⇒ {@code disconnectedBehavior} 根本不起作用
 * ⇒ ⇒ 实测会得到 {@code elapsedMs≈15} ⇒ ⇒ **假象**（与 A 组的结论长得一样 ⇒ 等于没测）。
 * <p>本类第一步就是<b>先写一次 key 并断言读得到</b> ⇒ ⇒ 连接【已建立】⇒ ⇒
 * 断连后走的是「已连接 → 被 FIN」那条路 ⇒ ⇒ 这才是 {@code disconnectedBehavior} 起作用的场景（与 A 组同口径）。
 *
 * <h2>⚠️ 为什么这个 B 组不会踩 G4 的源扫守门</h2>
 * <p>{@code GoStyleRedisClientShadowingGateTest} 扫的是 {@code src/main/java} 那一层
 * ⇒ ⇒ <b>{@code src/test/java} 不在扫描面</b> ⇒ ⇒ 守门在我域仍绿。
 *
 * <h2>⚠️ 本类的「变红能力」是内建的，且【拆成两条腿】</h2>
 * <p>把下面那个 {@code defaultDisconnectedBehaviorCustomizer} bean 删掉
 * ⇒ ⇒ 共享层那份 {@code REJECT_COMMANDS} 就会接管 ⇒ ⇒ 命令被立即拒绝 ⇒ ⇒ 失败耗时回到 ~1ms
 * ⇒ ⇒ <b>本类的耗时断言必红</b> ⇒ ⇒ **不需要额外的变异注入就能证明它不是恒真**。
 *
 * <p><b>为什么拆成两个 {@code @Test}（前提腿 / 目标腿）</b> —— 本轮实测踩出来的：
 * <p>第一版把「前提断言」和「目标断言」写在<b>同一个方法</b>里 ⇒ ⇒ 变异（删掉遮蔽 bean）时
 * <b>前提断言先红</b>（"断连行为必须是 DEFAULT ⇒ 实测 REJECT_COMMANDS"）⇒ ⇒ <b>目标断言根本没跑到</b>
 * ⇒ ⇒ ⇒ **拿这个结果只能说「前提断言是活的」，不能说「目标断言是活的」** ⇒ ⇒ **同一个方法里堆两条断言 ⇒ 变异只能证明第一条**。
 * <p>⇒ ⇒ 拆开之后：
 * <ul>
 *   <li><b>前提腿</b>（{@code disconnectedBehaviorIsDefaultAndIsTheOnlyVariable}）：
 *       只看配置层（customizer 数量 / 名字 / 断连行为 / 命令超时锚点）⇒ ⇒ <b>不制造断连</b>。</li>
 *   <li><b>目标腿</b>（{@code disconnectedRedisQueuesCommandsUntilCommandTimeout}）：
 *       <b>第一条断言就是耗时断言</b> ⇒ ⇒ 变异时<b>红点必定落在耗时断言上</b>。</li>
 * </ul>
 * ⇒ ⇒ 两条腿互不依赖 ⇒ ⇒ 变异一次 ⇒ ⇒ **两条腿各自被证伪一次** ⇒ ⇒ 「目标断言有判别力」这句话才站得住。
 * ⇒ ⇒ **同族纪律**：与「前提断言与目标断言要分开做变异」同源，但这里补的是
 * ⇒ ⇒ **「分开」必须落在【不同的用例】上，不能只写在同一条用例的不同断言行上」**。
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
        classes = {
                AdminApiApplication.class,
                AdminApiRedisDisconnectedDefaultBehaviorTest.DefaultDisconnectedBehaviorConfig.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                // 拒连最快；Nacos 连不上只会打日志，不影响本测试
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                // AdminRpcClient 用不到，给个不会真连、也不走 Nacos name resolver 的地址
                "grpc.client.admin-rpc.address=static://127.0.0.1:65535",
                "grpc.client.admin-rpc.negotiation-type=plaintext",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
@Testcontainers
class AdminApiRedisDisconnectedDefaultBehaviorTest {

    /**
     * 🔴 必须 mock 掉 {@code AdminRpcClient}（与 A 组同款理由）：
     * {@code AdminRpcClient.afterPropertiesSet()} 是「启动期拨号检查已武装」的自检
     * ⇒ 不 mock 的话<b>上下文启动就会失败</b>。
     */
    @MockitoBean
    private AdminRpcClient adminRpcClient;

    /**
     * 排队下限 —— 命令必须<b>一直挂到 commandTimeout(3s)</b>，而不是快速失败。
     *
     * <p>为什么取 2500ms：A 组判「快速失败」用的是 {@code < 1500ms}
     * ⇒ ⇒ 本组必须<b>明显越过</b> 1500ms 才算「排队」⇒ ⇒ 2500ms 落在中间，两侧都判得开
     * ⇒ ⇒ 而实测（DEFAULT 断连）是 ≈3000ms（挂到 {@code spring.data.redis.timeout: 3s}）。
     */
    private static final long QUEUED_BUDGET_MIN_MS = 2500L;

    /**
     * 本组<b>自己的</b> Redis 容器（与 A 组同款镜像、同款钉口）⇒ ⇒ 上游地址不同、故障形态相同。
     */
    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379).withCreateContainerCmdModifier(cmd -> pinContainerPort(cmd, 6379));

    /**
     * <b>与 A 组用【同一个】代理实现</b>（{@code AdminApiRedisDisconnectedFastFailTest.GracefulDisconnectProxy}）
     * ⇒ ⇒ 两侧故障形态必然同款 ⇒ ⇒ 「同口径」成立。
     *
     * <p>⚠️ <b>是独立实例</b>（不是 A 组那个 {@code static final PROXY}）⇒ ⇒ 两组互不污染。
     */
    static final AdminApiRedisDisconnectedFastFailTest.GracefulDisconnectProxy PROXY =
            AdminApiRedisDisconnectedFastFailTest.GracefulDisconnectProxy.create(() -> {
                try {
                    return REDIS.getHost() + ":" + REDIS.getMappedPort(6379);
                } catch (RuntimeException e) {
                    throw new IllegalStateException("Redis 容器还没起来", e);
                }
            });

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry r) {
        r.add("spring.data.redis.host", () -> "127.0.0.1");
        r.add("spring.data.redis.port", PROXY::port);
        r.add("spring.data.redis.password", () -> "");
    }

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * <b>唯一变量</b>：断连策略 = {@code DEFAULT}（把共享层那份 {@code REJECT_COMMANDS} 遮蔽掉）。
     *
     * <p>⚠️ <b>为什么放 test 侧</b>：共享层在 G4 写权面 ⇒ 域侧不能改它的源码；
     * 而 {@code @ConditionalOnMissingBean} 让我们可以<b>在 test 侧提供一份同类型的 bean</b> 来遮蔽它
     * ⇒ ⇒ <b>域内零落码</b>（不碰共享层、不碰域内生产代码）⇒ ⇒ 这就是「A/B 受控对照」能做到「唯一变量」的原因。
     */
    @TestConfiguration
    static class DefaultDisconnectedBehaviorConfig {

        @Bean
        LettuceClientOptionsBuilderCustomizer defaultDisconnectedBehaviorCustomizer() {
            return builder -> builder.disconnectedBehavior(ClientOptions.DisconnectedBehavior.DEFAULT);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  两条腿**故意拆成两个 @Test**（方法学要求，见类注释「前提腿 / 目标腿」）
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 🔴 <b>前提腿</b>：确认「遮蔽真的发生了，且它就是唯一变量」。
     *
     * <p>本方法**不制造断连** ⇒ ⇒ 与目标腿**互不依赖** ⇒ ⇒ 两条腿各自可被独立变异证伪。
     */
    @Test
    @DisplayName("🔴 B 组前提腿：容器里恰好一份 customizer，且断连行为确实是 DEFAULT（唯一变量已就位）")
    void disconnectedBehaviorIsDefaultAndIsTheOnlyVariable() {
        Map<String, LettuceClientOptionsBuilderCustomizer> customizers =
                applicationContext.getBeansOfType(LettuceClientOptionsBuilderCustomizer.class);
        assertEquals(1, customizers.size(),
                "容器里必须恰好一份 LettuceClientOptionsBuilderCustomizer ⇒ 实测 " + customizers.keySet()
                        + " ⇒ 若为 2 份 ⇒ 共享层那份没被遮蔽 ⇒ ⇒ 本类测的就不是 DEFAULT 场景");
        // 读**运行时真正在用的**那份 ClientOptions（不是 factory 上的配置副本）
        org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory factory =
                applicationContext.getBean(org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory.class);
        ClientOptions actual = factory.getClientConfiguration().getClientOptions().orElse(null);
        assertEquals(ClientOptions.DisconnectedBehavior.DEFAULT,
                actual == null ? null : actual.getDisconnectedBehavior(),
                "断连行为必须是 DEFAULT（这才是本组的唯一变量）⇒ 实测 "
                        + (actual == null ? "null" : actual.getDisconnectedBehavior()));
        assertTrue(customizers.containsKey("defaultDisconnectedBehaviorCustomizer"),
                "那一份必须是本类提供的遮蔽 bean ⇒ 实测 " + customizers.keySet());

        // 命令超时必须仍是 3s（= go-redis ReadTimeout 默认）⇒ ⇒ 目标腿的「排队到 3s」有确定的时间锚
        assertEquals(java.time.Duration.ofSeconds(3),
                factory.getClientConfiguration().getCommandTimeout(),
                "命令超时必须仍是 3s ⇒ 否则目标腿的「挂到 commandTimeout」没有确定锚点");
    }

    /**
     * 🔴 <b>目标腿</b>：断连后命令必须<b>被排队</b>（挂到 commandTimeout），而不是被立即拒绝。
     *
     * <p>⚠️ <b>本方法的第一条断言就是耗时断言</b> ⇒ ⇒ 变异（把遮蔽 bean 删掉 ⇒ 共享层的
     * {@code REJECT_COMMANDS} 接管）时，<b>红点必须落在耗时断言上</b>
     * ⇒ ⇒ 而不是被前提断言挡在前面 ⇒ ⇒ 这样才证明「目标断言本身有判别力」。
     */
    @Test
    @DisplayName("🔴 B 组目标腿：断连策略=DEFAULT ⇒ 命令被排队 ⇒ 挂到 commandTimeout(3s)，不是 <1500ms 的快速失败")
    void disconnectedRedisQueuesCommandsUntilCommandTimeout() {
        // ── ① 对照组：Redis 可达 ⇒ 先把连接【建立起来】并成功写一次 ──
        //    ⚠️ **这一步是必需的，不是多余的**：漏掉 ⇒ Lettuce 从未建立过连接
        //    ⇒ ⇒ 断连后第一条命令是「连接被拒」（~15ms 快失败）⇒ ⇒ 测的是「连不上」
        //    ⇒ ⇒ `disconnectedBehavior` 根本不起作用 ⇒ ⇒ 实测会长得跟 A 组一样 ⇒ ⇒ 假象。
        redisTemplate.opsForValue().set("admin:redis:probe", "1");
        assertEquals(Boolean.TRUE, redisTemplate.hasKey("admin:redis:probe"),
                "Redis 可达时 hasKey 必须为 true ⇒ 否则连接【从未建立】⇒ ⇒ 后面测的是「连不上」而不是「被断开」");

        // ── ② 制造断连（FIN 形态，与 A 组同款代理、同一份实现）──
        PROXY.disconnectGracefully();

        // ── ③ 实验组：失败耗时 ──
        long t0 = System.nanoTime();
        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> redisTemplate.hasKey("admin:redis:probe"),
                "Redis 断连后 hasKey 必须抛异常（DEFAULT 下是【排队到超时】抛，不是立即被拒）");
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);

        System.out.printf("[B 组 · DEFAULT 断连行为] admin-api hasKey 失败耗时=%dms（下限 %dms）%n",
                elapsedMs, QUEUED_BUDGET_MIN_MS);

        // ── ④ 判据 1（**本方法的第一条断言**）：必须【挂到 commandTimeout】而不是快速失败 ──
        //    ⇒ 这是「A 组的 <1500ms 快速失败是 REJECT_COMMANDS 换来的」这一归因的【反事实证据】
        assertTrue(elapsedMs >= QUEUED_BUDGET_MIN_MS,
                "DEFAULT 断连行为下命令被【排队】⇒ 必然挂到 commandTimeout(3s) ⇒ 实测 " + elapsedMs
                        + "ms ⇒ ⇒ 若与 A 组一样是 <1500ms ⇒ 说明命令根本没被排队 ⇒ ⇒ 判据失效"
                        + "（最可能的成因：连接从未建立 ⇒ 测的是「连接被拒」而不是「排队」）");

        // ── ⑤ 判据 2：不能是「立即被拒」那条分支 ──
        //    REJECT_COMMANDS 的异常原文是 "Currently not connected. Commands are rejected."
        //    ⇒ ⇒ 出现它 ⇒ 说明遮蔽没生效（本组测的还是共享层那份）⇒ ⇒ 本组无效
        String chain = causeChain(thrown);
        assertFalse(chain.toLowerCase().contains("commands are rejected"),
                "异常链里出现 REJECT_COMMANDS 的原文 ⇒ 说明共享层那份没被遮蔽 ⇒ ⇒ 本组无效。异常链：" + chain);

        // ── ⑥ 判据 3：不能无限等（排除「卡死」这种假象）────
        assertTrue(elapsedMs < 10000L,
                "失败耗时 " + elapsedMs + "ms ⇒ 若远超 commandTimeout ⇒ 说明命令既没被拒也没超时 ⇒ ⇒ 判据失效");
    }

    /** 把异常链压成一行（断言消息里要能看出 Lettuce 到底抛的是哪一条分支）。 */
    private static String causeChain(Throwable t) {
        StringBuilder sb = new StringBuilder();
        int depth = 0;
        while (t != null && depth < 8) {
            if (depth > 0) {
                sb.append(" <- ");
            }
            sb.append(t.getClass().getSimpleName()).append(": ").append(t.getMessage());
            t = t.getCause();
            depth++;
        }
        return sb.toString();
    }
}
