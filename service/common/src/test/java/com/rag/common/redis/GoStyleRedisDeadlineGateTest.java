package com.rag.common.redis;

import com.rag.common.observability.GoStyleRequestDeadline;
import com.rag.test.redis.RedisLikeApplication;
import com.rag.test.redis.RedisStubFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * <b>守门：Redis 的「请求期限感知」在<b>装配面</b>与<b>行为面</b>上都真的生效。</b>
 *
 * <h2>钉住的是什么</h2>
 * <ul>
 *   <li><b>装配面</b>：各域注入的 {@code RedisTemplate} / {@code StringRedisTemplate}
 *       背后的 connection factory <b>确实</b>被换成了预算感知版
 *       ⇒ ⇒ <b>域侧零改动</b>即生效（否则「能力面写了但没接线」＝ PIT-COMMON-04）。</li>
 *   <li><b>行为面</b>：请求预算<b>已耗尽</b> ⇒ <b>命令没有被下发</b>（代理层拦在调用之前）
 *       ⇒ ⇒ 这正是「比 Go 更严」的那一面（Go 侧照发）。</li>
 * </ul>
 *
 * <h2>判据为什么有鉴别力（变异前必红）</h2>
 * <p>把代理里的 {@code GoStyleRedisDeadline.assertNotExhausted(...)} 去掉
 * ⇒ 「命令没有被下发」这条断言<b>立刻转红</b>（stub 记录里会多出那次调用）。
 *
 * <h2>⚠️ 证据强度（诚实声明）</h2>
 * <p>本守门用<b>真 Spring 上下文</b>（真 BPP 装配 ＋ 真 JDK 代理），
 * 但<b>Redis 服务端本身没起</b> ⇒ ⇒ 它证明的是「<b>本层自己的机制</b>真的生效」
 * （装配 ＋ 拦截 ＋ 异常类型 ＋ 不下发），<b>不是</b>「与真实 Redis 的行为一致」
 * ⇒ ⇒ 后者需要双侧活体对拍（本轮未做 ⇒ 已登记）。
 */
@SpringBootTest(
        classes = RedisLikeApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class GoStyleRedisDeadlineGateTest {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Test
    @DisplayName("装配面：RedisTemplate 的 connection factory <b>确实</b>被换成了预算感知版")
    void connectionFactoryIsWrapped() {
        assertThat(redisTemplate.getConnectionFactory())
                .as("域侧零改动的唯一前提 = 各域注入的那个 RedisTemplate 背后的 factory 已被换掉；"
                        + "没换 ⇒ 能力面写了但没接线（PIT-COMMON-04）")
                .isInstanceOf(GoStyleRedisDeadlineConnectionFactory.class);
    }

    @Test
    @DisplayName("请求预算<b>已耗尽</b> ⇒ 命令<b>没有</b>被下发（比 Go 更严的那一面）")
    void exhaustedBudgetBlocksTheCommand() {
        RedisStubFactory.reset();
        GoStyleRequestDeadline.clear();
        try (GoStyleRequestDeadline.Scope ignored = GoStyleRequestDeadline.bindAbsoluteNanos(
                System.nanoTime() - TimeUnit.SECONDS.toNanos(1))) {
            RedisConnection connection = redisTemplate.getConnectionFactory().getConnection();
            assertThatThrownBy(() -> connection.get("k".getBytes(StandardCharsets.UTF_8)))
                    .as("预算耗尽 ⇒ 必须在下发命令之前就失败（Spring 标准超时异常 ⇒ 域侧 catch(Exception) 不用改）")
                    .isInstanceOf(QueryTimeoutException.class);
        } finally {
            GoStyleRequestDeadline.clear();
        }
        assertThat(RedisStubFactory.recorded())
                .as("预算耗尽 ⇒ 那条命令<b>根本不该</b>发到 Redis（stub 记录里不许出现它）")
                .doesNotContain("get");
    }

    @Test
    @DisplayName("请求预算<b>充足</b> ⇒ 命令正常下发（不凭空加约束）")
    void aliveBudgetLetsTheCommandThrough() {
        RedisStubFactory.reset();
        GoStyleRequestDeadline.clear();
        try (GoStyleRequestDeadline.Scope ignored = GoStyleRequestDeadline.bindAbsoluteNanos(
                System.nanoTime() + TimeUnit.SECONDS.toNanos(30))) {
            RedisConnection connection = redisTemplate.getConnectionFactory().getConnection();
            connection.get("k".getBytes(StandardCharsets.UTF_8));
        } finally {
            GoStyleRequestDeadline.clear();
        }
        assertThat(RedisStubFactory.recorded())
                .as("预算充足 ⇒ 命令必须照常下发（否则就是「把正常请求也掐了」）")
                .contains("get");
    }

    @Test
    @DisplayName("**没有载体**（无请求预算）⇒ 行为与 Go 一致：照常下发，不凭空加约束")
    void noBudgetMeansNoRestriction() {
        RedisStubFactory.reset();
        GoStyleRequestDeadline.clear();
        RedisConnection connection = redisTemplate.getConnectionFactory().getConnection();
        connection.get("k".getBytes(StandardCharsets.UTF_8));
        assertThat(RedisStubFactory.recorded())
                .as("Go 侧 ctx 无 deadline ⇒ RAG 也不该凭空加约束")
                .contains("get");
    }
}
