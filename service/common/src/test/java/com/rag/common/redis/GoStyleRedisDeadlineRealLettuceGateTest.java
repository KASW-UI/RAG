package com.rag.common.redis;

import com.rag.common.observability.GoStyleRequestDeadline;
import com.rag.test.redis.RedisRealLettuceApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * <b>守门：Redis 的「请求期限感知」在<b>真生产路径</b>上真的接线了。</b>
 *
 * <h2>补的是哪一格</h2>
 * <ul>
 *   <li>{@code GoStyleRedisDeadlineGateTest}（stub 版）⇒ 证明「<b>本层自己的机制</b>生效」
 *       （装配 ＋ 拦截 ＋ 不下发 ＋ 异常类型）；</li>
 *   <li><b>本守门</b> ⇒ 补上「<b>Boot 自己造的那两个 template + 真 Lettuce factory</b>
 *       这条生产路径上，代理<b>确实</b>接上了」⇒ ⇒ 这是域侧「零改动即生效」的那一格
 *       ⇒ ⇒ <b>G1 反馈单 F-1 点名的证据缺口（「新测试用 Mockito 替换数据层 ⇒ 不证代理接线」）</b>
 *       就落在这里。</li>
 * </ul>
 *
 * <h2>🔴 判据为什么有牙（2026-10-30 第六次订正：上一版这一格是<b>零判别力</b>的）</h2>
 * <p><b>上一版的错</b>：拿「本地没有 Redis ⇒ 连接异常」当「没碰 socket」的证据
 * ⇒ ⇒ <b>但 {@code LettuceConnectionFactory.getConnection()} 会真去拨号</b>
 * （实测栈迹：{@code StandaloneConnectionProvider.getConnection} → {@code RedisClient.connect()}）
 * ⇒ ⇒ <b>预算耗尽时也是先炸连接异常</b> ⇒ ⇒ <b>两个方向形状一样 ⇒ 判别力 = 0</b>
 * ⇒ ⇒ 那版守门是<b>假绿</b>（与 §3.3「跑绿 ≠ 对」同族）。</p>
 * <p><b>这一版怎么救回来</b>：把预算检查<b>前移到建连之前</b>
 * （对齐 Go 的 {@code waitTurn} 位置，见
 * {@link GoStyleRedisDeadlineConnectionFactory#getConnection()}）
 * ⇒ ⇒ <b>预算耗尽 ⇒ 连都不拨</b> ⇒ ⇒ <b>异常类型本身</b>就成了「没碰网络」的证据
 * ⇒ ⇒ <b>不需要 Redis 在跑</b> ⇒ ⇒ <b>且两个方向的形状不同 ⇒ 判别力恢复</b>。</p>
 * <ul>
 *   <li><b>变异 ①</b>：删掉「建连前」的检查 ⇒ 本守门的第 ③ 格<b>必须转红</b>
 *       （异常会从 {@code QueryTimeoutException} 变成连接异常）；</li>
 *   <li><b>变异 ②</b>：把实现改成「永远抛」⇒ 第 ④ 格<b>必须转红</b>
 *       （预算充足时也会被拦 ⇒ 说明「没误伤」那一格活着）。</li>
 * </ul>
 *
 * <h2>⚠️ 证据强度（诚实声明）</h2>
 * <p><b>装配链与拦截点是真 Spring 上下文 + 真 Lettuce 客户端</b>；
 * <b>本守门不需要 Redis 服务端</b>（第 ③ 格在拨号之前就结束）⇒ ⇒ 它证明的是
 * 「<b>本层能力在生产装配路径上真的接上了</b>」，
 * <b>不是</b>「与真实 Redis 的行为一致」⇒ ⇒ 后者由
 * {@code GoStyleRedisDeadlineLiveRedisWriteFaceGateTest}（真 Redis 容器）覆盖，
 * 且<b>那一层在容器起不来时会诚实跳过</b>（见该类的说明）。</p>
 */
@SpringBootTest(
        classes = RedisRealLettuceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                // ⚠️ 故意指向一个<b>没有 Redis 在跑</b>的端口 ⇒ ⇒
                //   「预算充足 ⇒ 命令真的下发」这一格会撞上连接失败 ⇒ ⇒ 那正是「命令确实发出去了」的证据
                //   ⇒ ⇒ 而且<b>本守门不依赖本机有没有 Redis</b>（两个方向的形状与 Redis 在不在无关）
                "spring.data.redis.host=127.0.0.1",
                "spring.data.redis.port=16399",
                "spring.data.redis.timeout=1s",
                "spring.data.redis.connect-timeout=1s"
        })
class GoStyleRedisDeadlineRealLettuceGateTest {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedisTemplate<Object, Object> redisTemplate;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @Test
    @DisplayName("装配面：Boot 自己造的 redisTemplate / stringRedisTemplate 背后的 factory <b>确实</b>被换成了预算感知版")
    void bootTemplatesAreWrapped() {
        assertThat(stringRedisTemplate.getConnectionFactory())
                .as("各域注入的就是这个 stringRedisTemplate ⇒ 它没被换 ⇒ 域侧零改动就是假的")
                .isInstanceOf(GoStyleRedisDeadlineConnectionFactory.class);
        assertThat(redisTemplate.getConnectionFactory())
                .as("同理：Boot 造的那个 redisTemplate 也必须被换")
                .isInstanceOf(GoStyleRedisDeadlineConnectionFactory.class);
    }

    @Test
    @DisplayName("真连接<b>没被替换</b>：包装版的 delegate <b>就是</b>那个真 LettuceConnectionFactory")
    void delegateIsTheRealLettuceFactory() {
        assertThat(redisConnectionFactory)
                .as("本守门的前提：Boot 真的造了一个 Lettuce factory（否则这格没意义）")
                .isInstanceOf(LettuceConnectionFactory.class);
        GoStyleRedisDeadlineConnectionFactory wrapped =
                (GoStyleRedisDeadlineConnectionFactory) stringRedisTemplate.getConnectionFactory();
        assertThat(wrapped.delegate())
                .as("包装版必须<b>委托给</b>真 factory ⇒ 换了包装不等于把真连接顶掉"
                        + "（否则域侧会连到别的东西去）")
                .isSameAs(redisConnectionFactory);
    }

    @Test
    @DisplayName("预算耗尽 ⇒ 真 Lettuce 路径上<b>连都不拨</b>就失败（QueryTimeoutException，且没有连接异常）")
    void exhaustedBudgetBlocksBeforeAnyConnectionAttempt() {
        GoStyleRequestDeadline.clear();
        try (GoStyleRequestDeadline.Scope ignored = GoStyleRequestDeadline.bindAbsoluteNanos(
                System.nanoTime() - TimeUnit.SECONDS.toNanos(1))) {
            assertThatThrownBy(() -> stringRedisTemplate.opsForValue().get("k"))
                    .as("⚠️ 这一格是本守门<b>最有鉴别力</b>的一格：若「建连前」的检查被删掉 ⇒ "
                            + "会先去拨号 ⇒ ⇒ 异常会变成 RedisConnectionFailureException（连接失败）"
                            + " ⇒ ⇒ 而这里要求的是 QueryTimeoutException ⇒ ⇒ "
                            + "<b>异常类型本身就是「没碰网络」的证据</b>")
                    .satisfies(ex -> {
                        assertThat(hasCause(ex, QueryTimeoutException.class))
                                .as("异常链里必须有 QueryTimeoutException（⇒ 预算检查在派发前触发）；"
                                        + "实际异常链 = " + chain(ex))
                                .isTrue();
                        assertThat(hasCause(ex, RedisConnectionFailureException.class))
                                .as("🔴 且<b>不能</b>有连接异常 ⇒ 否则说明「已经去拨号了」"
                                        + "⇒ ⇒ 那就退化成「拨号失败」，判别力归零；"
                                        + "实际异常链 = " + chain(ex))
                                .isFalse();
                    });
        } finally {
            GoStyleRequestDeadline.clear();
        }
    }

    @Test
    @DisplayName("预算充足 ⇒ <b>不凭空加约束</b>（绝不抛 QueryTimeoutException ⇒ 命令确实往下走了）")
    void aliveBudgetDoesNotAddRestriction() {
        GoStyleRequestDeadline.clear();
        try (GoStyleRequestDeadline.Scope ignored = GoStyleRequestDeadline.bindAbsoluteNanos(
                System.nanoTime() + TimeUnit.SECONDS.toNanos(30))) {
            assertThatThrownBy(() -> stringRedisTemplate.opsForValue().get("k"))
                    .as("本机 16399 没有 Redis ⇒ 必然失败（这正是「它真的发出去了」的证据）")
                    .satisfies(ex -> assertThat(hasCause(ex, QueryTimeoutException.class))
                            .as("预算充足 ⇒ 绝不能是 QueryTimeoutException（否则就是凭空加约束 / 永远抛）；"
                                    + "实际异常链 = " + chain(ex))
                            .isFalse());
        } finally {
            GoStyleRequestDeadline.clear();
        }
    }

    @Test
    @DisplayName("自动配置已登记进 AutoConfiguration.imports ⇒ 凡是依赖 service/common 且用 Redis 的进程都自动生效")
    void autoConfigurationIsRegistered() throws Exception {
        Path imports = Paths.get(
                "src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");
        assertThat(Files.exists(imports)).as("imports 文件必须存在（路径相对模块目录）").isTrue();
        assertThat(Files.readString(imports))
                .as("不登记 ⇒ 自动配置根本不会被求值 ⇒ 「域侧零改动」是假的")
                .contains("com.rag.common.redis.GoStyleRedisDeadlineAutoConfiguration");
    }

    /** 异常链里有没有指定类型（含自身）。 */
    private static boolean hasCause(Throwable throwable, Class<?> type) {
        for (Throwable c = throwable; c != null; c = c.getCause()) {
            if (type.isInstance(c)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 把异常链渲染成一行（红的时候直接看出「到底是谁在拦」）。
     *
     * <p>⚠️ <b>为什么要有它</b>：断言只说「链里没有 {@code QueryTimeoutException}」时，
     * 读者还得自己去猜是「代理没接上」「载体不可见」还是「异常被翻译」⇒ ⇒ 必须把链打出来。
     */
    private static String chain(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        for (Throwable c = throwable; c != null; c = c.getCause()) {
            if (sb.length() > 0) {
                sb.append("  <-  ");
            }
            sb.append(c.getClass().getName()).append(": ").append(c.getMessage());
            if (c.getCause() == c) {
                break;
            }
        }
        return sb.toString();
    }
}
