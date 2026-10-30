package com.rag.common.observability;

/**
 * <b>Prometheus 写入面</b> —— 共享层与「具体指标库」之间唯一的接缝。
 *
 * <h3>为什么要有这层抽象（而不是共享层直接依赖 Micrometer）</h3>
 * <p>Go 侧的写入面就是 go-zero 自己的 {@code core/metric}（prometheus client_golang 的薄封装）。
 * 本仓若让共享层<b>直接</b>依赖 Micrometer ⇒ {@code service/common} 就得把
 * {@code micrometer-core} <b>传递给全部 20 个组件</b>
 * ⇒ ⇒ 其中 <b>19 个组件今天根本没有指标 registry</b>（9 个 api 有 actuator、10 个 rpc 里只有 1 个有）
 * ⇒ ⇒ 与本仓 pom 里已写明的那条纪律冲突（「不想传递的下游依赖用 {@code optional=true}」、
 * 「共享层不得把下游不需要的框架塞给下游」）。
 * <p>⇒ 所以共享层只声明<b>能力</b>（本接口），<b>实现</b>放在
 * {@link MicrometerPrometheusSink}（唯一引用 Micrometer 类型的类），
 * 由 {@link GoStylePrometheusMeterRegistryBridge} 在<b>真有 registry</b>时装上。
 *
 * <h3>⚠️ 「没有 registry ⇒ 一条都不写」＝ Go 的 {@code prometheus.Enabled() == false}</h3>
 * <pre>
 *   go-zero@v1.10.2/core/metric/metric.go:14-20
 *       func update(fn func()) {
 *           if !prometheus.Enabled() { return }   // ← 整个写入被丢弃
 *           fn()
 *       }
 *   go-zero@v1.10.2/core/prometheus/agent.go:20-22
 *       func Enabled() bool { return enabled.Load() }
 * </pre>
 * ⇒ ⇒ 本仓的「无 registry ⇒ 无 sink ⇒ 拦截器整段跳过」是<b>同形</b>的：
 * <b>指标写不出去</b>，而不是「写到一个没人看的地方」。
 */
public interface GoStylePrometheusSink {

    /**
     * Go {@code prometheusinterceptor.go:39}：
     * {@code metricServerReqDur.Observe(timex.Since(startTime).Milliseconds(), info.FullMethod)}
     * —— 观测<b>耗时（毫秒，整数截断）</b>。
     *
     * @param method gRPC 全方法名（Go 的 {@code info.FullMethod}）
     * @param millis 耗时（毫秒，<b>整型截断</b> ⇒ 与 Go 的 {@code .Milliseconds()} 同形）
     */
    void observeDurationMillis(String method, long millis);

    /**
     * Go {@code prometheusinterceptor.go:40}：
     * {@code metricServerReqCodeTotal.Inc(info.FullMethod, strconv.Itoa(int(status.Code(err))))}
     * —— 按<b>方法 ＋ gRPC 状态码</b>计数。
     *
     * @param method gRPC 全方法名
     * @param code   gRPC 状态码<b>数字</b>（OK = 0、RESOURCE_EXHAUSTED = 8、INTERNAL = 13 …）
     */
    void incrementCode(String method, int code);

    /**
     * 一个<b>什么都不写</b>的写入面。
     *
     * <p>用途：容器里<b>没有</b> registry 时，{@link GoStylePrometheusMeterRegistryBridge} 的
     * {@code @Bean} 方法仍要有返回值 ⇒ ⇒ 返回它（但<b>不</b>装进 holder ⇒ 拦截器照旧整段跳过）。
     */
    static GoStylePrometheusSink noop() {
        return new GoStylePrometheusSink() {
            @Override
            public void observeDurationMillis(String method, long millis) {
                // 不写
            }

            @Override
            public void incrementCode(String method, int code) {
                // 不写
            }
        };
    }
}
