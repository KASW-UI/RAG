import com.rag.common.observability.GoStyleRequestDeadline;
import com.rag.common.redis.GoStyleRedisDeadlineConnectionFactory;

import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.concurrent.TimeUnit;

/**
 * Redis 期限「写面」RAG 半边活体探针（E66 的 RAG 侧那半 · 2026-10-31 · 合并收口执行者）。
 *
 * <h2>测什么</h2>
 * <p>「请求 deadline <b>已耗尽</b> ⇒ 命令<b>根本没下发</b>到 Redis」——
 * <p>⇒ ⇒ 观测量 = <b>读回来还是基线值</b>（<b>不是</b>靠异常类型推断）
 * ⇒ ⇒ <b>与 Go 半边用同一个观测量</b>（{@code tools/semantic-probe/redis-deadline/go/main.go}）
 * ⇒ ⇒ 两侧的 PROBE 行才可比。
 *
 * <h2>跑的是<b>生产装配面</b>（不是手工拼的 mock）</h2>
 * <ul>
 *   <li>连接工厂 = <b>生产类</b> {@link GoStyleRedisDeadlineConnectionFactory}（共享层的期限载体）</li>
 *   <li>预算来源 = <b>生产类</b> {@link GoStyleRequestDeadline}（与 gRPC/HTTP 侧同一个载体）</li>
 *   <li>底层 = <b>真 Lettuce</b>（不是 mock 连接）</li>
 * </ul>
 * ⇒ ⇒ 符合 AGENTS.md §3.3.1「框架机制必须验证实际生效」：<b>不是</b>「注解在」或「属性默认值对」。
 *
 * <h2>与既有活体守门的关系</h2>
 * <p>仓里已有 {@code GoStyleRedisDeadlineLiveRedisWriteFaceGateTest}（JUnit · Testcontainers）
 * ⇒ ⇒ 它证的是<b>RAG 侧那半</b>（且用 JUnit 断言表达）⇒ ⇒ 本探针是它的
 * <b>「可与 Go 半边逐键对拍」版本</b>（同样的观测量、同样的两格，但输出 PROBE 行）。
 *
 * <h2>输出</h2>
 * <pre>
 * PROBE|redis_deadline|form=exhausted|write_applied=&lt;bool&gt;|err_is_deadline=&lt;bool&gt;|value_after=&lt;str&gt;
 * PROBE|redis_deadline|form=alive|write_applied=&lt;bool&gt;|err_is_deadline=&lt;bool&gt;|value_after=&lt;str&gt;
 * </pre>
 */
public final class RedisDeadlineProbe {

    private static final String PROBE_KEY = "rag-e66-redis-deadline-probe";

    private RedisDeadlineProbe() {
    }

    public static void main(String[] args) {
        String addr = System.getenv("REDIS_ADDR");
        if (addr == null || addr.isBlank()) {
            addr = "127.0.0.1:6379";
        }
        int colon = addr.lastIndexOf(':');
        String host = colon > 0 ? addr.substring(0, colon) : "127.0.0.1";
        int port = colon > 0 ? Integer.parseInt(addr.substring(colon + 1)) : 6379;

        LettuceConnectionFactory real = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(host, port));
        real.afterPropertiesSet();
        try {
            RedisTemplate<String, String> budgetAware =
                    template(new GoStyleRedisDeadlineConnectionFactory(real, true));
            // ⚠️ 独立通道（**不带预算**）⇒ 只用它读回 ⇒ 「没写进去」是**读出来的**、不是推断的
            RedisTemplate<String, String> plain = template(real);

            // ── 形态①：预算耗尽 ⇒ 命令不该下发 ─────────────────────────
            budgetAware.opsForValue().set(PROBE_KEY, "baseline");

            GoStyleRequestDeadline.clear();
            boolean errIsDeadline = false;
            try (GoStyleRequestDeadline.Scope ignored = GoStyleRequestDeadline.bindAbsoluteNanos(
                    System.nanoTime() - TimeUnit.SECONDS.toNanos(1))) {
                try {
                    budgetAware.opsForValue().set(PROBE_KEY, "overwritten");
                } catch (RuntimeException e) {
                    errIsDeadline = e instanceof QueryTimeoutException
                            || e.getCause() instanceof QueryTimeoutException;
                }
            } finally {
                GoStyleRequestDeadline.clear();
            }

            String after = plain.opsForValue().get(PROBE_KEY);
            boolean writeApplied = "overwritten".equals(after);
            System.out.printf(
                    "PROBE|redis_deadline|form=exhausted|write_applied=%b|err_is_deadline=%b|value_after=%s%n",
                    writeApplied, errIsDeadline, after);

            // ── 形态②（负控）：预算充足 ⇒ 真写进去 ───────────────────────
            String aliveKey = PROBE_KEY + "-alive";
            boolean aliveApplied = false;
            boolean aliveIsDeadline = false;
            GoStyleRequestDeadline.clear();
            try (GoStyleRequestDeadline.Scope ignored = GoStyleRequestDeadline.bindAbsoluteNanos(
                    System.nanoTime() + TimeUnit.SECONDS.toNanos(30))) {
                try {
                    budgetAware.opsForValue().set(aliveKey, "written");
                    aliveApplied = "written".equals(budgetAware.opsForValue().get(aliveKey));
                } catch (RuntimeException e) {
                    aliveIsDeadline = e instanceof QueryTimeoutException;
                }
            } finally {
                GoStyleRequestDeadline.clear();
            }
            System.out.printf(
                    "PROBE|redis_deadline|form=alive|write_applied=%b|err_is_deadline=%b|value_after=%s%n",
                    aliveApplied, aliveIsDeadline, aliveApplied ? "written" : "<none>");
        } finally {
            real.destroy();
        }
    }

    private static RedisTemplate<String, String> template(RedisConnectionFactory factory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}
