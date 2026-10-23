package com.rag.follow.follow.api.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * follow-api 三个 metric —— Go 端 {@code service/follow/api/internal/metrics/metrics.go} 的等价物。
 *
 * <p><b>Go 出处（逐字）</b>：
 * <pre>
 * prometheus.NewCounterVec(CounterOpts{Subsystem: "follow_api", Name: "request_total"},
 *     []string{"route", "result"})
 * prometheus.NewCounterVec(CounterOpts{Subsystem: "follow_api", Name: "reject_total"},
 *     []string{"route", "reason"})
 * prometheus.NewHistogramVec(HistogramOpts{Subsystem: "follow_api",
 *     Name: "request_duration_seconds", Buckets: prometheus.DefBuckets}, []string{"route"})
 *
 * func ObserveRequest(route string, started time.Time, err error) {
 *     result := "success"
 *     if err != nil { result = "fail" }
 *     APIRequestCounter.WithLabelValues(route, result).Inc()
 *     APIRequestDuration.WithLabelValues(route).Observe(time.Since(started).Seconds())
 * }
 * func ObserveReject(route, reason string) { APIRejectCounter.WithLabelValues(route, reason).Inc() }
 * </pre>
 *
 * <p><b>为什么用「构造期不穷举 label 组合」</b>：Go 侧
 * {@code WithLabelValues} 是首次访问时注册；本类同样在<b>观测时</b>取
 * {@code registry.counter(...)}，而不是在构造器里把 8 个 route × 2 个 result 预先建出来。
 * 后者会产出一堆永远为 0 的时间序列，改变 {@code /metrics} 的输出形状（那也是可观察的）。
 *
 * <p><b>route label 的值是完整路径</b>（Go 各 logic 里的 {@code const route = "..."} 字面量），
 * 例如 {@code "/follow/v1/block/list"}。8 个字面量逐条抄自 Go 源文件，见各 Service 类。
 *
 * <p><b>埋点位置也在逐条对齐</b>：Go 侧每个 logic 方法体的头尾是
 * <pre>
 * started := time.Now()
 * const route = "..."
 * defer func() { metrics.ObserveRequest(route, started, err) }()
 * ...
 * userID, err := extractUserID(l.ctx)
 * if err != nil { metrics.ObserveReject(route, "user_id_missing"); return nil, err }
 * </pre>
 * 即 <b>无论成功失败都记一次 request_total</b>，身份缺失另外记一次 reject_total。
 * Java 侧用 try/finally 表达同一时序（见 {@code AbstractFollowApiService#invoke}）。
 */
@Component
public class FollowApiMetrics {

    /** Go {@code APIRequestCounter} 的 metric 名（子系统 + name）。 */
    private static final String REQUEST_TOTAL = "follow_api_request_total";
    /** Go {@code APIRejectCounter}。 */
    private static final String REJECT_TOTAL = "follow_api_reject_total";
    /** Go {@code APIRequestDuration}。 */
    private static final String REQUEST_DURATION_SECONDS = "follow_api_request_duration_seconds";

    private final MeterRegistry registry;

    public FollowApiMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * 对应 Go {@code ObserveRequest(route, started, err)}。
     *
     * <p><b>result 只有两个取值</b>：{@code "success"} / {@code "fail"}，
     * 取值规则与 Go 完全一致 —— Go 判的是 <b>logic 方法返回的 err 是否为 nil</b>，
     * 因此「返回了业务失败码（如 5201 自关注）」也是 {@code fail}。
     *
     * @param route       完整路径（各 Service 的 route 常量）
     * @param failed      logic 是否以错误收场
     * @param elapsedNanos 起点到收场的耗时（纳秒）
     */
    public void observeRequest(String route, boolean failed, long elapsedNanos) {
        registry.counter(REQUEST_TOTAL,
                        "route", route,
                        "result", failed ? "fail" : "success")
                .increment();
        registry.timer(REQUEST_DURATION_SECONDS, "route", route)
                .record(elapsedNanos, TimeUnit.NANOSECONDS);
    }

    /**
     * 对应 Go {@code ObserveReject(route, reason)}。
     *
     * <p><b>两个 reason 字面量</b>（Go 端全树 grep 确认，无第三个）：
     * <ul>
     *   <li>{@code "user_id_missing"} —— 8 个 logic 全部在开头调
     *       {@code extractUserID(l.ctx)} 失败时记这一条；</li>
     *   <li>{@code "forbidden_scope"} —— 仅 {@code GetBlockListLogic:47} 与
     *       {@code GetRecommendationsLogic:47} 记这一条（那两个端点才做归属校验）。</li>
     * </ul>
     */
    public void observeReject(String route, String reason) {
        registry.counter(REJECT_TOTAL, "route", route, "reason", reason).increment();
    }

    /**
     * 单个 route 的请求计数（测试与运维查询用）。
     *
     * @return 不存在时为 {@code null}（Go 侧对应「该 label 组合从未出现过」）
     */
    public Double requestCount(String route, String result) {
        Counter counter = registry.find(REQUEST_TOTAL).tag("route", route).tag("result", result).counter();
        return counter == null ? null : counter.count();
    }

    /**
     * 单个 route 的拒绝计数（测试与运维查询用）。
     *
     * @return 不存在时为 {@code null}
     */
    public Double rejectCount(String route, String reason) {
        Counter counter = registry.find(REJECT_TOTAL).tag("route", route).tag("reason", reason).counter();
        return counter == null ? null : counter.count();
    }

    /**
     * 单个 route 的耗时直方图（测试与运维查询用）。
     *
     * @return 不存在时为 {@code null}
     */
    public Timer requestDuration(String route) {
        return registry.find(REQUEST_DURATION_SECONDS).tag("route", route).timer();
    }
}