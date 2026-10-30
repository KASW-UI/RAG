package com.rag.common.redis;

import com.rag.common.observability.GoStyleRequestDeadline;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * <b>守门（活体层）：预算耗尽 ⇒ <b>Redis 里真的没写进去</b>。</b>
 *
 * <h2>这一层补的是哪一格</h2>
 * <p>{@link GoStyleRedisDeadlineRealLettuceGateTest}（L1）证明的是
 * 「<b>生产装配路径上代理接上了，且预算耗尽时在<b>拨号之前</b>就失败</b>」
 * ⇒ ⇒ 但它<b>证不了「命令真的没落到 Redis 里」</b>（它压根没连）⇒ ⇒ <b>这一层才是「写面」的硬证据</b>：
 * <ul>
 *   <li>先写一个基线值；</li>
 *   <li>在<b>预算耗尽</b>的状态下再写一次 ⇒ ⇒ 必须抛
 *       {@link QueryTimeoutException}（没下发）；</li>
 *   <li>用<b>另一条不带预算的独立通道</b>读回 ⇒ ⇒ <b>必须还是基线值</b>
 *       ⇒ ⇒ <b>「没写进去」不是靠异常类型推断的，是读回来证明的</b>。</li>
 * </ul>
 *
 * <h2>为什么这一层重要（Go 对照）</h2>
 * <p>Go 侧：{@code logoutlogic.go:38} 的 {@code SetexCtx(l.ctx, …)} ⇒ ⇒
 * {@code pool.Get(ctx)} ⇒ {@code waitTurn(ctx)}（{@code pool.go:1098-1103}）先查
 * {@code ctx.Done()} ⇒ <b>ctx 已到期 ⇒ 命令根本不下发</b>
 * ⇒ ⇒ <b>RAG 在这一层必须与 Go 同形 ⇒ 即「预算耗尽 ⇒ 不写」</b>
 * ⇒ ⇒ 这一层就是那件事的<b>双侧对照面</b>（Go 侧靠读源码 + 真源探针，RAG 侧靠本守门）。</p>
 *
 * <h2>⚠️ 诚实边界</h2>
 * <ul>
 *   <li><b>容器起不来 ⇒ 整类跳过</b>（{@code assumeTrue}）⇒ ⇒ surefire 记 <b>Skipped</b>
 *       （<b>不是绿</b>）⇒ ⇒ 「这一层在本机跑没跑」要读 surefire 报告，<b>不能靠「构建绿」推断</b>。</li>
 *   <li>只用 {@code RedisTemplate} 这条<b>上层</b>写法 ⇒ ⇒
 *       {@code connection.stringCommands().xxx(...)} 直调底层的形态<b>不在本守门覆盖内</b>
 *       （见 {@link GoStyleRedisDeadlineConnection} 的「已知不覆盖的面」）。</li>
 * </ul>
 */
class GoStyleRedisDeadlineLiveRedisWriteFaceGateTest {

    private static GenericContainer<?> redis;

    @BeforeAll
    static void startRedis() {
        try {
            redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);
            redis.start();
        } catch (Throwable t) {
            // ⚠️ 容器起不来（无 docker / 无镜像）⇒ **诚实跳过** ⇒ 不把 CI 弄红、也不假装通过
            redis = null;
            System.out.println("### live redis unavailable ⇒ 本类将跳过：" + t);
        }
    }

    @Test
    @DisplayName("预算耗尽 ⇒ Redis 里<b>真的没写进去</b>（读回仍是基线值 ⇒ 不是靠异常类型推断的）")
    void exhaustedBudgetDoesNotWrite() {
        assumeTrue(redis != null, "live redis 不可用 ⇒ 跳过（诚实跳过，不是通过）");

        LettuceConnectionFactory real = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(redis.getHost(), redis.getMappedPort(6379)));
        real.afterPropertiesSet();
        try {
            RedisTemplate<String, String> budgetAware = template(
                    new GoStyleRedisDeadlineConnectionFactory(real, true));

            // ① 基线：无预算 ⇒ 正常写入
            budgetAware.opsForValue().set("g4-write-face", "baseline");

            // ② 预算耗尽 ⇒ 再写一次 ⇒ 必须被拦下
            GoStyleRequestDeadline.clear();
            try (GoStyleRequestDeadline.Scope ignored = GoStyleRequestDeadline.bindAbsoluteNanos(
                    System.nanoTime() - TimeUnit.SECONDS.toNanos(1))) {
                assertThatThrownBy(() -> budgetAware.opsForValue().set("g4-write-face", "overwritten"))
                        .as("预算耗尽 ⇒ 必须在<b>下发之前</b>失败（否则就是数据面回归：Go 不写、RAG 写）")
                        .isInstanceOf(QueryTimeoutException.class);
            } finally {
                GoStyleRequestDeadline.clear();
            }

            // ③ 独立通道（**不带预算**）读回 ⇒ 必须还是基线值
            RedisTemplate<String, String> plain = template(real);
            assertThat(plain.opsForValue().get("g4-write-face"))
                    .as("🔴 这一格是本守门的<b>核心</b>：读回来还是基线值 ⇒ "
                            + "<b>命令真的没落到 Redis 里</b> ⇒ ⇒ RAG 与 Go 在「预算耗尽 ⇒ 不写」上同形")
                    .isEqualTo("baseline");
        } finally {
            real.destroy();
        }
    }

    @Test
    @DisplayName("预算充足 ⇒ 正常写入并读回（证明没误伤正常路径）")
    void aliveBudgetWritesNormally() {
        assumeTrue(redis != null, "live redis 不可用 ⇒ 跳过（诚实跳过，不是通过）");

        LettuceConnectionFactory real = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(redis.getHost(), redis.getMappedPort(6379)));
        real.afterPropertiesSet();
        try {
            RedisTemplate<String, String> budgetAware = template(
                    new GoStyleRedisDeadlineConnectionFactory(real, true));

            GoStyleRequestDeadline.clear();
            try (GoStyleRequestDeadline.Scope ignored = GoStyleRequestDeadline.bindAbsoluteNanos(
                    System.nanoTime() + TimeUnit.SECONDS.toNanos(30))) {
                budgetAware.opsForValue().set("g4-write-face-alive", "written");
                assertThat(budgetAware.opsForValue().get("g4-write-face-alive"))
                        .as("预算充足 ⇒ 必须真的写进去并读得回来 ⇒ ⇒ 「预算感知」没有误伤正常路径")
                        .isEqualTo("written");
            } finally {
                GoStyleRequestDeadline.clear();
            }
        } finally {
            real.destroy();
        }
    }

    /**
     * 🔴 <b>这一格是「命令下发前」那道检查的<b>唯一</b>守门</b>（变异掉它 ⇒ 本格必须红）。
     *
     * <p><b>为什么单独一格</b>：正常路径下每次命令都会重新取连接 ⇒ ⇒
     * 「取连接前」那道检查<b>先</b>触发 ⇒ ⇒ 命令处那道<b>永远轮不到</b> ⇒ ⇒
     * 变异掉命令处那道，两层守门<b>仍然全绿</b>（实测）⇒ ⇒ 只有「<b>连接已建立</b>」这个序列
     * 能把它逼出来：连接已建立 ⇒ ⇒ 后续命令<b>不重新取连接</b> ⇒ ⇒
     * 「取连接前」那道<b>不再触发</b> ⇒ ⇒ 此刻能拦住的<b>只有</b>命令处那道。
     *
     * <p>⚠️ <b>这正是 Go 侧「每条命令都过一次 waitTurn」的等价物</b>：Go 的
     * {@code pool.Get(ctx)} 每条命令都调 ⇒ 每条命令都查 ctx；RAG 若只有「取连接前」那道，
     * 则<b>复用同一条连接</b>的场景（事务绑定 / 手动持有连接）会漏检 ⇒ ⇒ 补上这一道 = 同形。
     */
    @Test
    @DisplayName("连接<b>已建立</b>后再耗尽预算 ⇒ 命令仍被拦下（只有「命令下发前」那道能拦 ⇒ 两道分工被钉住）")
    void exhaustedBudgetBlocksCommandOnAlreadyEstablishedConnection() {
        assumeTrue(redis != null, "live redis 不可用 ⇒ 跳过（诚实跳过，不是通过）");

        LettuceConnectionFactory real = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(redis.getHost(), redis.getMappedPort(6379)));
        real.afterPropertiesSet();
        try {
            GoStyleRedisDeadlineConnectionFactory budgetAwareFactory =
                    new GoStyleRedisDeadlineConnectionFactory(real, true);
            RedisTemplate<String, String> t = template(budgetAwareFactory);
            t.opsForValue().set("g4-established", "baseline");

            // 预算充足 ⇒ 取一条**已建立**的连接
            org.springframework.data.redis.connection.RedisConnection conn =
                    budgetAwareFactory.getConnection();
            try {
                // 现在把预算耗尽 ⇒ 再用**这条已建立的连接**发命令
                GoStyleRequestDeadline.clear();
                try (GoStyleRequestDeadline.Scope ignored = GoStyleRequestDeadline.bindAbsoluteNanos(
                        System.nanoTime() - TimeUnit.SECONDS.toNanos(1))) {
                    assertThatThrownBy(() -> conn.set(
                            "g4-established".getBytes(), "overwritten".getBytes()))
                            .as("⚠️ 连接已建立 ⇒ 「取连接前」那道<b>不会</b>再触发 ⇒ ⇒ "
                                    + "此刻能拦住的<b>只有</b>「命令下发前」那道 ⇒ ⇒ "
                                    + "变异掉它 ⇒ 本格必须红")
                            .isInstanceOf(QueryTimeoutException.class);
                } finally {
                    GoStyleRequestDeadline.clear();
                }
            } finally {
                conn.close();
            }

            assertThat(template(real).opsForValue().get("g4-established"))
                    .as("读回仍是基线值 ⇒ 命令真的没下发")
                    .isEqualTo("baseline");
        } finally {
            real.destroy();
        }
    }

    /** 一个最简的 {@link RedisTemplate}（字符串序列化）。 */
    private static RedisTemplate<String, String> template(
            org.springframework.data.redis.connection.RedisConnectionFactory factory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}
