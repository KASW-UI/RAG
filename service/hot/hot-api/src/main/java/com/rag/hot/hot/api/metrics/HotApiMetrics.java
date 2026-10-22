package com.rag.hot.hot.api.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * hot-api 的 5 个指标 —— 对应 Go {@code api/internal/metrics/metrics.go:13-43} 的 5 个
 * Prometheus collector，逐个 1:1。
 *
 * <h3>指标名为什么会多一个 {@code _total} 后缀</h3>
 * Go 侧 {@code Subsystem="hot_api"} + {@code Name="request_total"} 在 Prometheus 里渲染为
 * {@code hot_api_request_total_total}（{@code CounterVec.Name} 本身已含 {@code _total}，
 * client_golang 不做去重）。Micrometer 的 {@code Counter.builder("hot_api_request_total")}
 * 在 Prometheus registry 上同样被规范化成 {@code hot_api_request_total_total}
 * ⇒ <b>两侧 key 一致</b>，看板不会因为换语言而断。
 *
 * <h3>⚠️ 本模块的指标在 Go 侧<b>其实没有被暴露</b>（这是 Go 侧的事实，不是本类的缺陷）</h3>
 * {@code hotcenter.go:33} 确实调了 {@code metrics.InitMetrics()}，5 个 collector 注册进了
 * 默认 registry；但暴露端点不存在：
 * <ul>
 *   <li>{@code etc/hotcenter.yaml} 的 {@code Prometheus} 块<b>没有</b> {@code Host}
 *       ⇒ go-zero {@code StartAgent} 因 {@code len(c.Host)==0} 提前返回；</li>
 *   <li>{@code DevServer} 块（{@code Enabled/Port:16067/MetricsPath/HealthPath}）在
 *       go-zero v1.10.2 的 {@code rest.RestConf} 里<b>没有对应字段</b>，被 conf loader 静默忽略。</li>
 * </ul>
 * Java 侧用 Actuator 暴露 {@code /actuator/prometheus}
 * ⇒ 这是一条<b>「Java 比 Go 多暴露了指标」的单向增强</b>：不改变任何业务判定，
 * 但两侧的可观测面确实不同，排查时别拿「Go 上看不到」推断「RAG 上没有」。
 *
 * <h3>⚠️ {@code hot_api_reject_total} 在 Go 侧是<b>死指标</b></h3>
 * {@code metrics.go:65-67} 的 {@code ObserveReject} 全仓<b>零调用点</b>（grep 验证）。
 * 本类<b>照样注册</b>（否则历史看板上的指标会凭空消失），也<b>不补触发点</b> ——
 * 补触发点就是<b>新增行为</b>（Go 侧从来没有在这些点上计过数）。
 */
@Component
public class HotApiMetrics {

    // ==================== L1 标签字面量（Prometheus 维度，改名即断看板） ====================

    /**
     * Go {@code getHotArticlesLogic.go:24} 的字面量常量 {@code getHotArticlesRoute}。
     * ⚠️ 它是<b>手写的字符串</b>，不是从 Spring 的 mapping 对象推导的 ——
     * Spring 那边可以写成 {@code /hot/v1/articles}，但改路由名时这里不会跟着变，
     * 这正是 Go 侧的形态（要改就改两处）。
     */
    public static final String ROUTE = "/hot/v1/articles";

    /** Go {@code getHotArticlesLogic.go:31}：hot-rpc 的 callee 名。 */
    public static final String CALLEE_HOT_RPC = "hot_rpc_get_hot_articles";

    /** Go {@code getHotArticlesLogic.go:32}：article-rpc 的 callee 名。 */
    public static final String CALLEE_ARTICLE_RPC = "article_rpc_get_article";

    /** Go {@code getHotArticlesLogic.go:34}：{@code hotItem == nil || GetArticleId()==""}。 */
    public static final String SKIP_EMPTY_ARTICLE_ID = "empty_article_id";

    /** Go {@code getHotArticlesLogic.go:35}：article-rpc 调用报错。 */
    public static final String SKIP_ARTICLE_RPC_ERROR = "article_rpc_error";

    /** Go {@code getHotArticlesLogic.go:36}：{@code articleResp==nil || GetArticle()==nil}。 */
    public static final String SKIP_ARTICLE_MISSING = "article_missing";

    /** Go {@code getHotArticlesLogic.go:37}：{@code status != PUBLISHED}。 */
    public static final String SKIP_ARTICLE_NOT_PUBLISHED = "article_not_published";

    /**
     * Go {@code getHotArticlesLogic.go:71}：{@code hotResp == nil}。
     * ⚠️ 这个 reason 打在 <b>article skip</b> 指标上，而它<b>根本不是</b>某一篇文章被跳过，
     * 而是整个 hot-rpc 返回了空指针。这是 Go 侧既有的指标归类，按「不擅自修复 Go 行为」原样保留。
     */
    public static final String SKIP_HOT_RPC_NIL_RESPONSE = "hot_rpc_nil_response";

    /** Go {@code metrics.go:56} 的 {@code result="success"}。 */
    public static final String RESULT_SUCCESS = "success";

    /** Go {@code metrics.go:58} 的 {@code result="fail"}。 */
    public static final String RESULT_FAIL = "fail";

    /**
     * Go {@code prometheus.DefBuckets} 的<b>逐条转写</b>
     * （{@code client_golang/prometheus/histogram.go}）：
     * {@code .005 .01 .025 .05 .1 .25 .5 1 2.5 5 10}（单位：秒）。
     *
     * <p><b>为什么必须逐条对齐</b>：Go 的 {@code APIRequestDuration} 是
     * {@code prometheus.HistogramVec}，导出时带 {@code _bucket{le="0.005"}} 这类分桶；
     * 而 Micrometer 的 {@link io.micrometer.core.instrument.Timer} 在 Prometheus registry 上
     * <b>默认只导出 {@code _count/_sum/_max}</b>（没有 {@code le}）。
     * ⇒ 不显式开 {@code publishPercentileHistogram} + SLO 边界，dashboard 上的
     * {@code histogram_quantile} 在两侧不可比。
     * <p>⚠️ 刻意<b>不</b>走 {@code management.metrics.distribution.slo} 配置项：
     * Boot 的 {@code ServiceLevelObjectiveBoundary} 转换器解析不了小数秒
     * （{@code 2.5s} 抛 {@code NumberFormatException}，并让整个
     * {@code MetricsProperties} 绑定失败 ⇒ 上下文起不来）。写在代码里既绕开该转换器，
     * 也把「这组桶 = Go 的 DefBuckets」这条对应关系放在指标定义旁边。
     */
    private static final Duration[] GO_DEF_BUCKETS = {
            Duration.ofMillis(5), Duration.ofMillis(10), Duration.ofMillis(25),
            Duration.ofMillis(50), Duration.ofMillis(100), Duration.ofMillis(250),
            Duration.ofMillis(500), Duration.ofSeconds(1), Duration.ofMillis(2500),
            Duration.ofSeconds(5), Duration.ofSeconds(10)
    };

    // ==================== 5 个 meter ====================

    private final MeterRegistry registry;

    public HotApiMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Go {@code metrics.ObserveRequest(route, started, err)}（{@code metrics.go:55-63}）：
     * 同时打「请求计数」与「耗时直方图」。
     *
     * <p>⚠️ <b>与 Go 的一处刻意差异（记录在案）</b>：Go 用<b>命名返回值</b> {@code err} + {@code defer}，
     * panic 展开时 {@code err} 仍为 {@code nil} ⇒ Go 会把 panic 记成 {@code result="success"}
     *（一个 Go 侧既有的指标误记，客户端同时收到的是 5001）。
     * 本类按<b>客户端实际看到的结果</b>记录：异常路径记 {@link #RESULT_FAIL}。
     * 该差异只在「logic 抛未捕获异常」时可见，而本 logic 没有任何会抛的语句；
     * 换来的是「指标与响应不再互相矛盾」。
     *
     * @param startedNanos {@link System#nanoTime()} 时基下的开始时刻
     */
    public void observeRequest(String route, long startedNanos, boolean success) {
        Counter.builder("hot_api_request_total")
                .description("Total number of hot api requests")
                .tag("route", route)
                .tag("result", success ? RESULT_SUCCESS : RESULT_FAIL)
                .register(registry)
                .increment();
        Timer.builder("hot_api_request_duration_seconds")
                .description("Hot api request duration in seconds")
                .tag("route", route)
                // ⚠️ 没有这两行，Prometheus 上只有 _count/_sum/_max，没有 _bucket{le=…}；
                //    Go 侧是 HistogramVec ⇒ 带分桶，边界必须是 prometheus.DefBuckets。
                .publishPercentileHistogram()
                .serviceLevelObjectives(GO_DEF_BUCKETS)
                .register(registry)
                .record(System.nanoTime() - startedNanos, TimeUnit.NANOSECONDS);
    }

    /**
     * Go {@code metrics.ObserveRPCError(callee)}（{@code metrics.go:69-71}）。
     * 调用点：hot-rpc 失败（fatal）与 article-rpc 失败（skip）各一处。
     */
    public void observeRpcError(String callee) {
        Counter.builder("hot_api_rpc_error_total")
                .description("Total number of hot api rpc errors")
                .tag("callee", callee)
                .register(registry)
                .increment();
    }

    /** Go {@code metrics.ObserveArticleSkip(reason)}（{@code metrics.go:73-75}）。 */
    public void observeArticleSkip(String reason) {
        Counter.builder("hot_api_article_skip_total")
                .description("Total number of skipped hot articles during hydration")
                .tag("reason", reason)
                .register(registry)
                .increment();
    }

    /**
     * Go {@code metrics.ObserveReject(route, reason)}（{@code metrics.go:65-67}）。
     *
     * <p>⚠️ <b>Go 侧零调用点</b>，因此本方法<b>刻意不在业务里被调用</b>；
     * 保留它是为了让「Go 有这个 API、Java 也有」这件事可对照，
     * 将来若要补触发点，必须同时补 Go 侧或明确登记为新增行为。
     */
    public void observeReject(String route, String reason) {
        Counter.builder("hot_api_reject_total")
                .description("Total number of rejected hot api requests")
                .tag("route", route)
                .tag("reason", reason)
                .register(registry)
                .increment();
    }
}