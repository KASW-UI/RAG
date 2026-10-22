package com.rag.article.article.rpc.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * article-rpc 的 6 个指标 —— 逐个对应 Go 端 {@code service/article/rpc/metrics/metrics.go}。
 *
 * <p><b>L1：指标名与标签是运维可观测契约</b>
 * —— Prometheus 抓的是<b>全名</b>，任何差异都会让既有 dashboard / 告警静默失效（不会报错，只会「没有数据」）。
 * <p>⚠️ <b>Go 的 {@code Namespace = "article_service"} 是指标名的<b>前缀</b></b>（metrics.go:6），而 Micrometer 不提供前缀机制 ——
 * 它按 {@code Meter.Type} 自动补后缀（Counter → {@code _total}，Timer → {@code _seconds}）：
 * {@code io.micrometer.prometheusmetrics.PrometheusNamingConvention#name(String, Meter.Type, String)}。
 * ⇒ 本类的 meter name <b>必须写掉这个后缀</b>，才能输出 Go 的全名：
 * <table border="1">
 *   <caption>Go 输出全名 ↔ Java meter name</caption>
 *   <tr><th>Go Prometheus 全名</th><th>Java meter name（本类）</th></tr>
 *   <tr><td>{@code article_service_article_total}</td><td>{@code article_service_article}</td></tr>
 *   <tr><td>{@code article_service_article_status_total}</td><td>{@code article_service_article_status}</td></tr>
 *   <tr><td>{@code article_service_file_upload_total}</td><td>{@code article_service_file_upload}</td></tr>
 *   <tr><td>{@code article_service_minio_request_errors_total}</td><td>{@code article_service_minio_request_errors}</td></tr>
 *   <tr><td>{@code article_service_kafka_push_errors_total}</td><td>{@code article_service_kafka_push_errors}</td></tr>
 *   <tr><td>{@code article_service_minio_request_duration_seconds}</td>
 *       <td>{@code article_service_minio_request_duration}（Timer 自动补 {@code _seconds}）</td></tr>
 * </table>
 * ⚠️ <b>曾写成 {@code article_total} / {@code minio_request_duration_seconds}</b>，
 * 实际输出 {@code article_total_total} / {@code minio_request_duration_seconds_seconds} ——
 * 6 个指标<b>全部改名</b>。修正后由
 * {@code ArticleRpcMetricsPrometheusNameTest} 逐条锁定。
 * <p>另外全局贴 {@code application: article-rpc} 标签（application.yml）——
 * 它是<b>标签</b>而非前缀，不参与名字对齐，两侧都有。
 *
 * <h2>⚠️ 六个指标中只有一个<b>不完整</b>，且不许"补全"</h2>
 * {@code article_status_total} 的 Go 侧<b>只有 {@code reviewing} 有调用点</b>
 * （{@code create_article_logic.go:115}），{@code published} 与 {@code rejected}
 * <b>没有任何 +1 的地方</b>。本类<b>照搬这个不完整</b>：
 * 只在 create 时记录 reviewing，发布成功与审核拒绝<b>不记录</b>。
 * 补全它们属于「新增 Go 侧不存在的埋点」—— 会让告警规则
 * （例如"published 计数应该增长"）从不触发变成触发，这是可观察的行为变化。
 * FileTree/article/rpc.md §12.7 不通过项 #4 已把这一点登记为待决策项。
 *
 * <p><b>标签取值也照抄</b>：{@code action} 只有 create/update/delete
 * （Go 注释明写），不要加 publish/reject。
 */
@Component
public class ArticleRpcMetrics {

    /**
     * 对应 Go {@code prometheus.Namespace = "article_service"}（metrics.go:6）。
     *
     * <p>⚠️ 它是<b>已压入各 meter name 的前缀</b>（见类级说明的对照表），
     * 而<b>不是一个运行时可用的配置项</b>—— Micrometer 没有“指标名前缀”这个概念。
     * 它保留在这里只为了让上表的前缀有一个可引用的单一源。
     */
    public static final String NAMESPACE = "article_service";

    /** 对应 Go {@code MinioRequestDuration} 的 buckets（{@code metrics.go:46}），单位秒。 */
    private static final double[] MINIO_BUCKETS =
            {0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1};

    /**
     * 同一组 buckets 的 {@link Duration} 形式 —— Micrometer 的 API 只收 Duration。
     *
     * <p>由 {@link #MINIO_BUCKETS} 换算而来，不独立手写，避免两处数值漂移。
     */
    private static final java.time.Duration[] MINIO_BUCKET_DURATIONS = toDurations(MINIO_BUCKETS);

    private static java.time.Duration[] toDurations(double[] seconds) {
        java.time.Duration[] result = new java.time.Duration[seconds.length];
        for (int i = 0; i < seconds.length; i++) {
            result[i] = java.time.Duration.ofNanos((long) (seconds[i] * 1_000_000_000L));
        }
        return result;
    }

    private final MeterRegistry registry;

    public ArticleRpcMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** 对应 Go {@code ArticleTotal.WithLabelValues("create"|"update"|"delete")}。 */
    public void article(String action) {
        Counter.builder("article_service_article")
                .description("Total number of articles processed")
                .tag("action", action)
                .register(registry)
                .increment();
    }

    /**
     * 对应 Go {@code ArticleStatusTotal.WithLabelValues("reviewing")}。
     *
     * <p>⚠️ <b>全模块唯一调用点是 create</b>；见类级说明，不要补 published/rejected。
     */
    public void articleStatus(String status) {
        Counter.builder("article_service_article_status")
                .description("Total number of article status changes")
                .tag("status", status)
                .register(registry)
                .increment();
    }

    /** 对应 Go {@code FileUploadTotal.WithLabelValues("image"|"markdown")}。 */
    public void fileUpload(String type) {
        Counter.builder("article_service_file_upload")
                .description("Total number of files uploaded")
                .tag("type", type)
                .register(registry)
                .increment();
    }

    /**
     * 记录一次 MinIO 操作耗时 —— 对应 Go 的
     * {@code prometheus.NewTimer(metrics.MinioRequestDuration.WithLabelValues(op))} + {@code ObserveDuration()}。
     *
     * <p>用法：{@code metrics.observeMinio("put", elapsedNanos)}。
     * Go 的 Timer 语义是"从 NewTimer 到 ObserveDuration 的间隔"，
     * Java 侧由调用方测量区间后传入，两者是同一个量。
     *
     * <p>⚠️ <b>buckets 由 {@link #MINIO_BUCKETS} 决定</b>：Go 侧是
     * {@code Buckets: []float64{0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1}}（秒）。
     * Micrometer 的 {@code serviceLevelObjectives} 收 {@link Duration}（不是 double 秒），
     * 换算后<b>数值一致</b>，因此两侧的直方图分桶边界相同 ——
     * 这让 {@code histogram_quantile} 的告警阈值在迁移前后不变。
     */
    public void observeMinio(String operation, long durationNanos) {
        Timer.builder("article_service_minio_request_duration")
                .description("Latency of MinIO requests.")
                .tag("operation", operation)
                .publishPercentileHistogram()
                .serviceLevelObjectives(MINIO_BUCKET_DURATIONS)
                .register(registry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    /** 对应 Go {@code MinioRequestErrors.WithLabelValues(op).Inc()}。 */
    public void minioError(String operation) {
        Counter.builder("article_service_minio_request_errors")
                .description("Total number of errors for MinIO requests")
                .tag("operation", operation)
                .register(registry)
                .increment();
    }

    /**
     * 对应 Go {@code KafkaPushErrors.WithLabelValues(topic).Inc()}。
     *
     * <p>⚠️ <b>标签名是 {@code topic} 但传的其实是"用途名"</b>：
     * Go 侧的实际取值是 {@code "article_review"}（create/update 入队审核）
     * 与 {@code "article_sync_event"}（outbox relay），
     * <b>不是</b> Kafka 的 topic 字符串。这是一处命名与取值不符，
     * 但它决定了历史告警规则能否继续命中 ⇒ 照搬取值，不"修正"成真 topic。
     */
    public void kafkaPushError(String topic) {
        Counter.builder("article_service_kafka_push_errors")
                .description("Total number of errors for Kafka push")
                .tag("topic", topic)
                .register(registry)
                .increment();
    }
}