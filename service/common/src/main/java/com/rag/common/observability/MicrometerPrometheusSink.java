package com.rag.common.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * {@link GoStylePrometheusSink} 的 <b>Micrometer 实现</b> —— 共享层里<b>唯一</b>引用 Micrometer 类型的类。
 *
 * <h3>Go 出处（指标定义逐字对照）</h3>
 * <pre>
 *   go-zero@v1.10.2/zrpc/internal/serverinterceptors/prometheusinterceptor.go:13-31
 *       const serverNamespace = "rpc_server"
 *
 *       metricServerReqDur = metric.NewHistogramVec(&amp;metric.HistogramVecOpts{
 *           Namespace: serverNamespace,     // rpc_server
 *           Subsystem: "requests",           // requests
 *           Name:      "duration_ms",        // ⇒ **rpc_server_requests_duration_ms**
 *           Help:      "rpc server requests duration(ms).",
 *           Labels:    []string{"method"},
 *           Buckets:   []float64{1, 2, 5, 10, 25, 50, 100, 250, 500, 1000, 2000, 5000},
 *       })
 *
 *       metricServerReqCodeTotal = metric.NewCounterVec(&amp;metric.CounterVecOpts{
 *           Namespace: serverNamespace,     // rpc_server
 *           Subsystem: "requests",           // requests
 *           Name:      "code_total",         // ⇒ **rpc_server_requests_code_total**
 *           Help:      "rpc server requests code count.",
 *           Labels:    []string{"method", "code"},
 *       })
 * </pre>
 *
 * <h3>🔴 counter 的名字为什么<b>不能</b>写成 {@code rpc_server_requests_code_total}</h3>
 * <p>Prometheus 的<b>客户端库</b>（不是 Micrometer）会给 counter 补 {@code _total} 后缀
 * ⇒ ⇒ Micrometer 侧的 meter 名必须写成 <b>{@code rpc_server_requests_code}</b>，
 * 暴露出来的才是 Go 那个 {@code rpc_server_requests_code_total}。
 * <p>⚠️ <b>实测依据</b>（2026-10-30 · micrometer-registry-prometheus 1.16.7 ＋ prometheus-metrics 1.x）：
 * 把两个 counter 分别以 {@code rpc_server_requests_code_total} 与 {@code rpc_server_requests_code}
 * 注册进同一个 registry ⇒ 暴露出来的<b>只有一个</b> family {@code rpc_server_requests_code_total}
 * ⇒ ⇒ 即「已带 {@code _total} 的名字不会被再补一次」。
 * ⇒ ⇒ 本类仍按「<b>不带</b> {@code _total}」写 ⇒ 这样即使将来客户端改成「无条件补后缀」也不会变成
 * {@code ..._total_total}。
 *
 * <h3>⚠️ 两处<b>暴露格式</b>差异（<b>不是</b>指标语义差异）</h3>
 * <ol>
 *   <li><b>多一个 {@code *_max} gauge</b> —— Micrometer 对每个 summary/histogram 额外暴露一个最大值 gauge
 *       （Go 的 client_golang <b>没有</b>这个 family）⇒ ⇒ RAG 的暴露面比 Go <b>多一个</b> family。</li>
 *   <li><b>{@code le} 标签渲染成浮点</b> —— Micrometer 写 {@code le="1.0"}，client_golang 写 {@code le="1"}
 *       ⇒ ⇒ <b>桶边界数值相同</b>（1/2/5/10/25/50/100/250/500/1000/2000/5000），只是字面量写法不同。</li>
 * </ol>
 * ⇒ ⇒ 两者的<b>指标名 / label 键 / 桶边界 / 观测值</b>逐项一致；差异只在<b>字面渲染</b>。
 *
 * <h3>观测值的<b>单位</b>：毫秒（与 Go 同）</h3>
 * <p>Go 观测的是 {@code .Milliseconds()}（<b>整型毫秒</b>）⇒ 本类也用
 * {@link GoStylePrometheusSink#observeDurationMillis(String, long)} 收到的<b>已截断毫秒</b>，
 * 且桶边界也是毫秒 ⇒ ⇒ 桶归属与 Go 逐桶一致。
 * ⇒ ⚠️ 因此本仓<b>不用</b> Micrometer 的 {@code Timer}（它按<b>秒</b>暴露 ⇒ 名字会变成
 * {@code ..._duration_ms_seconds_bucket} ⇒ 与 Go 的对外名不一致）。
 */
public final class MicrometerPrometheusSink implements GoStylePrometheusSink {

    /** Go {@code prometheusinterceptor.go:16} 的指标名（namespace+subsystem+name 拼成）。 */
    public static final String DURATION_METRIC_NAME = "rpc_server_requests_duration_ms";

    /**
     * Go {@code prometheusinterceptor.go:25} 的指标名。
     *
     * <p>⚠️ <b>刻意<b>不</b>带 {@code _total}</b>：Prometheus 客户端会自己补 ⇒ 见本类 javadoc。
     */
    public static final String CODE_METRIC_NAME = "rpc_server_requests_code";

    /** Go {@code prometheusinterceptor.go:22} 的桶边界（毫秒）。 */
    public static final double[] BUCKETS_MS =
            {1, 2, 5, 10, 25, 50, 100, 250, 500, 1000, 2000, 5000};

    private final MeterRegistry registry;

    public MicrometerPrometheusSink(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void observeDurationMillis(String method, long millis) {
        // ⚠️ 每次请求都 register 一次：Micrometer 的 register 对同 name+tags 是**幂等**的
        //    （命中已有 meter ⇒ 返回同一个）⇒ ⇒ 与 Go 的 WithLabelValues 语义一致。
        DistributionSummary.builder(DURATION_METRIC_NAME)
                .tag("method", method)
                .serviceLevelObjectives(BUCKETS_MS)
                .register(registry)
                .record(millis);
    }

    @Override
    public void incrementCode(String method, int code) {
        Counter.builder(CODE_METRIC_NAME)
                .tag("method", method)
                .tag("code", Integer.toString(code))
                .register(registry)
                .increment();
    }
}
