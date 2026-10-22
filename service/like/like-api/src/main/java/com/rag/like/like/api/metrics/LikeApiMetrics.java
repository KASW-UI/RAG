package com.rag.like.like.api.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * like-api 的网关层拒绝计数 —— 对应 Go {@code api/internal/metrics/metrics.go}：
 * <pre>
 * ApiRejectCount = prometheus.NewCounterVec(prometheus.CounterOpts{
 *     Namespace: "", Subsystem: "like_api", Name: "reject_total",
 *     Help: "Total requests rejected at the API gateway layer",
 * }, []string{"route", "reason"})
 * </pre>
 *
 * <p><b>L1 key 名</b>：Go 的 {@code Subsystem + Name} 拼接后是
 * {@code like_api_reject_total}，Prometheus 会渲染成
 * {@code like_api_reject_total_total}（{@code CounterVec.Name} 已经含 {@code _total}）。
 * Micrometer 的 {@code Counter.builder("like_api_reject_total")} 在
 * Prometheus registry 上同样会被规范化为 {@code like_api_reject_total_total}，
 * 两侧 key 一致。
 *
 * <h3>⚠️ Go 侧的 route 标签<b>存在既有错误，本模块刻意原样保留</b></h3>
 * <p>Go 里 4 处 <b>全部</b>硬编码 {@code "/like/likeaction"}：
 * <ul>
 *   <li>{@code getlikestatelogic.go:39,46}</li>
 *   <li>{@code getuserlikelistlogic.go:39,46}</li>
 *   <li>{@code getusertotallikelogic.go:39,46}</li>
 *   <li>{@code likeactionlogic.go:39,46}</li>
 * </ul>
 * 即<b>只有 likeaction 的标签是对的</b>，另外 3 个端点的
 * {@code token_invalid} / {@code token_parse_error} 都被记到了 {@code /like/likeaction}
 * 名下。这是 Go 的<b>可观察行为</b>（Prometheus 上确实这么出现），
 * 按「不要擅自修复 Go 的行为」必须保留。
 * <p>本类的常量 {@link #ROUTE_LABEL_GO_HARDCODED} 把这个事实<b>钉死并集中</b>，
 * 将来若要修正只需改一处常量 + 同步改 Go，两边不会各改一半。
 *
 * <h3>⚠️ Go 侧 {@code InitMetrics()} 从未被调用</h3>
 * <p>{@code likecenter.go} 的 {@code main()} <b>没有</b>调
 * {@code metrics.InitMetrics()} ⇒ 该 CounterVec <b>从未注册到 Prometheus 默认 registry</b>，
 * 也就是说 Go 端 {@code :9099/metrics} <b>根本看不到</b>这个指标
 * （{@code WithLabelValues(...).Inc()} 仍可正常自增，只是没被暴露）。
 * <p>Java 侧用 Micrometer 声明即注册，指标<b>会</b>出现在
 * {@code /actuator/prometheus}。⇒ 这是一条<b>「Java 比 Go 多暴露了指标」</b>的
 * 单向增强：不改变任何业务判定，但要知道两者可观测面并不相同。
 */
@Component
public class LikeApiMetrics {

    /**
     * Go 侧 4 处硬编码的 route 标签值（见类注释）。
     * <b>包括 3 处与实际端点路径不符的错误值</b> —— 刻意保留 Go 的可观察行为。
     */
    public static final String ROUTE_LABEL_GO_HARDCODED = "/like/likeaction";

    /** Go {@code metrics.go:39}：{@code ctx userId is not json.Number} 分支。 */
    public static final String REASON_TOKEN_INVALID = "token_invalid";

    /** Go {@code metrics.go:46}：{@code userId.Int64()} 解析失败分支。 */
    public static final String REASON_TOKEN_PARSE_ERROR = "token_parse_error";

    private final MeterRegistry registry;

    public LikeApiMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * 递增 Go {@code metrics.ApiRejectCount.WithLabelValues(route, reason).Inc()}。
     *
     * @param route  {@link #ROUTE_LABEL_GO_HARDCODED}
     * @param reason {@link #REASON_TOKEN_INVALID} 或 {@link #REASON_TOKEN_PARSE_ERROR}
     */
    public void incApiReject(String route, String reason) {
        Counter.builder("like_api_reject_total")
                .description("Total requests rejected at the API gateway layer")
                .tag("route", route)
                .tag("reason", reason)
                .register(registry)
                .increment();
    }
}