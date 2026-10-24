package com.rag.comment.comment.rpc.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * comment 域指标 —— 对应 Go 端 {@code service/comment/rpc/internal/metrics/metrics.go:9-38}
 * 的 5 个 Prometheus 向量，逐个复刻指标名与 label 集合。
 *
 * <h2>为什么指标名要逐字复刻</h2>
 * <p>它们是<b>运维契约</b>：告警规则、看板、Grafana 面板都按
 * {@code comment_request_total{module,action,result}} 这类名字建的。
 * Micrometer 的命名规则会把下划线保留、但 Prometheus registry 会加前缀
 * （{@code prometheus.name} 属性可覆盖），因此这里显式命名，
 * 让导出的指标名与 Go 侧一致。
 *
 * <h2>⚠️ Go 的 result 标签集合<b>不一致</b>，必须复刻</h2>
 * <p>6 个写用例的 {@code result ∈ {ok, biz_fail, sys_fail}}，
 * 但 {@code GetComment} 只有 <b>2 个值</b>（{@code get_comment_logic.go:35-38}：
 * 默认 "ok"，出错才 "sys_fail" —— 它<b>没有</b> biz_fail 这个分支，
 * 因为本用例从不构造业务错误）。
 * ⇒ 若把它并进同一套标签，就会凭空多出一个永不出现的值。
 * Micrometer 的标签值是自由字符串而不是固定集合，所以「只发两个值」天然成立 ——
 * 关键是**不要**在 GetComment 的失败分支里统一发 biz_fail。
 *
 * <h2>⚠️ Go 侧 module 标签也有不一致</h2>
 * <p>4 个请求类指标用 {@code module="comment_rpc"}，
 * 而列表长度 Gauge 用的是 {@code module="comment_list"}
 * （{@code metrics.go:47} + {@code get_comment_logic.go:147}）。照搬。
 */
@Component
public class CommentRpcMetrics {

    private static final Logger LOG = LoggerFactory.getLogger(CommentRpcMetrics.class);

    /** Go {@code metrics.go:46} 的 module 标签值。 */
    private static final String MODULE_RPC = "comment_rpc";

    /** Go {@code get_comment_logic.go:147} 的 module 标签值（与上面<b>刻意不同</b>）。 */
    private static final String MODULE_LIST = "comment_list";

    /** Go {@code metrics.go:34} 的 module 标签值。 */
    private static final String MODULE_REDIS = "comment_redis";

    /** Go {@code metrics.go:28} 的 module 标签值。 */
    private static final String MODULE_POSTGRES = "comment_postgres";

    private final MeterRegistry registry;

    /** 对应 {@code CommentListSizeGaugeMetric}；Go 是 Gauge，Java 用 AtomicInteger 持有。 */
    private final AtomicInteger lastListSize = new AtomicInteger(0);

    public CommentRpcMetrics(MeterRegistry registry) {
        this.registry = registry;

        // 注册列表长度 Gauge。Micrometer 的 Gauge 需要一个「持有者」，
        // 这里把 lastListSize 作为持有对象，读数即当前值。
        Gauge.builder("comment_list_size", lastListSize, AtomicInteger::get)
                .tags(Tags.of("module", MODULE_LIST, "action", "GetComment"))
                .description("Last returned size of comment list")
                .register(registry);
    }

    /**
     * 请求计数 + 累计耗时 —— 对应 Go 每个 logic 顶部的 {@code defer} 埋点块。
     *
     * <p>Go 的两个向量分别是：
     * <pre>
     * CommentRequestCounterMetric{module,action,result}     Inc()
     * CommentRequestSecondsCounterMetric{module,action}     Add(duration.Seconds())
     * </pre>
     * Java 侧用 Timer 的 {@code count} 与 {@code sum} 表达同一个「计数 + 累计秒数」，
     * 避免两次取时钟（Go 侧也是只取一次 {@code start := time.Now()}）。
     *
     * @param action      RPC 名，如 {@code CreateComment}
     * @param startNanos  进入用例时 {@code System.nanoTime()} 的值
     * @param resultLabel {@code ok} / {@code biz_fail} / {@code sys_fail}
     */
    public void observeRpc(String action, long startNanos, String resultLabel) {
        long elapsed = System.nanoTime() - startNanos;

        Counter.builder("comment_request_total")
                .tags("module", MODULE_RPC, "action", action, "result", resultLabel)
                .description("Total number of comment rpc requests")
                .register(registry)
                .increment();

        registry.timer("comment_request_seconds",
                "module", MODULE_RPC, "action", action)
                .record(elapsed, TimeUnit.NANOSECONDS);
    }

    /**
     * 对应 Go {@code CommentListSizeGaugeMetric{module="comment_list", action="GetComment"}.Set(n)}
     * （{@code get_comment_logic.go:146-148}）。
     */
    public void setListSize(int size) {
        lastListSize.set(size);
    }

    /**
     * 对应 Go {@code CommentPostgresErrorCounterMetric{module="comment_postgres", action, type}}。
     *
     * <p>⚠️ Go 的 {@code type} 标签有 5 个取值，逐个对应到 {@code model/comment.go} 里的调用点：
     * {@code query} / {@code insert} / {@code update} / {@code upsert} / {@code lock_query}。
     * 这里不枚举，由调用点原样传入。
     */
    public void observePostgresError(String action, String type) {
        Counter.builder("comment_postgres_error_total")
                .tags("module", MODULE_POSTGRES, "action", action, "type", type)
                .description("Total number of postgres errors in comment service")
                .register(registry)
                .increment();
    }

    /**
     * 对应 Go {@code CommentRedisErrorCounterMetric{module="comment_redis", action, type}}。
     *
     * <p>⚠️ <b>Go 侧有一处指标名与实际错误来源不符</b>，必须照搬：
     * {@code comment_subject_cache.go:52-55} 在<b>DB 回源失败</b>时打的也是
     * {@code CommentRedisErrorCounterMetric{…, type="db_fallback"}}。
     * 看着该进 postgres 指标，实际进了 redis 指标。
     * 修正它会让「redis 错误率」的看板历史曲线断裂 ⇒ 保留。
     */
    public void observeRedisError(String action, String type) {
        Counter.builder("comment_redis_error_total")
                .tags("module", MODULE_REDIS, "action", action, "type", type)
                .description("Total number of redis errors in comment service")
                .register(registry)
                .increment();
    }

    /** 便于排障：本域已注册的 comment_* 指标名清单。 */
    public java.util.List<String> registeredMetricNames() {
        return registry.getMeters().stream()
                .map(m -> m.getId().getName())
                .filter(n -> n.startsWith("comment"))
                .distinct()
                .sorted()
                .toList();
    }

    /** 取当前列表长度 Gauge 的读数（供测试断言）。 */
    public int currentListSize() {
        return lastListSize.get();
    }

    /** 仅用于避免 LOG 未使用的告警（Go 侧 {@code InitMetrics} 有对应的初始化日志）。 */
    Logger log() {
        return LOG;
    }
}