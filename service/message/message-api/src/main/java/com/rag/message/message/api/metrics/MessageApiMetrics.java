package com.rag.message.message.api.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * message-api HTTP 层指标 —— 对应 Go 端 {@code service/message/api/internal/metrics/metrics.go}。
 *
 * <p><b>Go 出处</b>（三个 metric，prometheus 子系统名 {@code message_api}）：
 * <pre>
 * APIRequestCounter  = NewCounterVec(CounterOpts{Subsystem:"message_api", Name:"request_total"},   []string{"route","result"})
 * APIRejectCounter   = NewCounterVec(CounterOpts{Subsystem:"message_api", Name:"reject_total"},    []string{"route","reason"})
 * APIRequestDuration = NewHistogramVec(HistogramOpts{Subsystem:"message_api", Name:"request_duration_seconds"}, []string{"route"})
 *
 * func ObserveRequest(route string, started time.Time, err error) {
 *     result := "success"; if err != nil { result = "fail" }
 *     APIRequestCounter.WithLabelValues(route, result).Inc()
 *     APIRequestDuration.WithLabelValues(route).Observe(time.Since(started).Seconds())
 * }
 * func ObserveReject(route, reason string) { APIRejectCounter.WithLabelValues(route, reason).Inc() }
 * </pre>
 *
 * <p><b>为什么用按需创建而不是构造期建好全部组合</b>：Go 的 {@code CounterVec} 是
 * label 值动态注册（{@code WithLabelValues} 首次调用时创建 child）。13 个端点 ×
 * 2 种 result + 3 种 reason，如果构造期穷举会得到一堆永远为 0 的时间序列；
 * Micrometer 的 {@code registry.counter(name, tags...)} 语义与 {@code WithLabelValues}
 * 一致，都是首次访问时注册。
 *
 * <p><b>route label 的值是完整路径</b>（Go 各 logic 里的 {@code const route = "..."} 字面量），
 * 例如 {@code "/message/v1/conversations/list"}、{@code "/message/v1/admin/notifications/send"}。
 * 13 个字面量逐条抄自 Go 源文件，见各 Service 类的常量。
 *
 * <p><b>埋点位置也在逐条对齐</b>：Go 侧每个 logic 方法体的头尾是
 * <pre>
 * started := time.Now()
 * const route = "..."
 * defer func() { metrics.ObserveRequest(route, started, err) }()
 * ...
 * uid, err := shared.UserIDFromContext(l.ctx)
 * if err != nil { metrics.ObserveReject(route, "user_id_missing"); return nil, err }
 * </pre>
 * 即 <b>无论成功失败都记一次 request_total</b>，身份缺失另外记一次 reject_total。
 * Java 侧用 try/finally 表达同一时序（见各 Service）。
 */
@Component
public class MessageApiMetrics {

    /** Go {@code APIRequestCounter} 的 metric 名（子系统 + name）。 */
    private static final String REQUEST_TOTAL = "message_api_request_total";
    /** Go {@code APIRejectCounter}。 */
    private static final String REJECT_TOTAL = "message_api_reject_total";
    /** Go {@code APIRequestDuration}。 */
    private static final String REQUEST_DURATION_SECONDS = "message_api_request_duration_seconds";

    private final MeterRegistry registry;

    public MessageApiMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * 对应 Go {@code ObserveRequest(route, started, err)}。
     *
     * <p><b>result 只有两个取值</b>：{@code "success"} / {@code "fail"}，
     * 取值规则与 Go 完全一致 —— Go 判的是 <b>logic 方法返回的 err 是否为 nil</b>，
     * 因此「返回了业务失败码」也是 {@code fail}。
     *
     * @param route    完整路径（各 Service 的 route 常量）
     * @param failed   logic 是否以错误收场
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
     * <p><b>三个 reason 字面量</b>（Go 端全树 grep 确认，无第四个）：
     * <ul>
     *   <li>{@code "user_id_missing"} —— 8 个 user 端点的 {@code UserIDFromContext} 失败</li>
     *   <li>{@code "admin_id_missing"} —— 5 个 admin 端点的同一处失败</li>
     *   <li>{@code "target_missing"} —— {@code SendAdminNotification} 的
     *       {@code !broadcast && target_id <= 0} 校验</li>
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