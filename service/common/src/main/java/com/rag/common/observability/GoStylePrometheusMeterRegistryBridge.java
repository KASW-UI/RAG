package com.rag.common.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * <b>把 Micrometer registry 接成 {@link GoStylePrometheusSink}</b> —— Prometheus 写入面的<b>唯一装配点</b>。
 *
 * <h2>装配面</h2>
 * <p>本类经 {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * 登记（由 {@link GoStyleRpcMetricsConfiguration} 引入）⇒ ⇒
 * <b>凡是依赖 {@code service/common} 的进程都自动生效</b>
 * ⇒ ⇒ <b>10 个 rpc 组件零改动</b>获得「能写 Prometheus」的能力。
 *
 * <h2>🔴 没有 registry ⇒ <b>一条都不写</b>（与 Go 同形）</h2>
 * <pre>
 *   go-zero@v1.10.2/core/metric/metric.go:14-20
 *       func update(fn func()) {
 *           if !prometheus.Enabled() { return }    // ← 没有暴露面 ⇒ 写入整次丢弃
 *           fn()
 *       }
 * </pre>
 * ⇒ ⇒ 本仓的同形语义 = 「容器里<b>没有</b> {@link MeterRegistry} Bean ⇒ <b>不装</b> sink
 * ⇒ ⇒ 拦截器整段跳过（{@link GoStylePrometheusInterceptor}）⇒ <b>不产生任何指标</b>。
 * <p>⚠️ <b>这不等于「指标写错了地方」</b>，而是<b>根本没有地方可写</b> ——
 * 与 Go 的 {@code prometheus.Enabled() == false} 是同一个意思。
 *
 * <h2>⚠️ 为什么用<b>字符串</b>形式的 {@code @ConditionalOnClass}</h2>
 * <p>本类的 {@code @Bean} 方法签名引用了 {@code MeterRegistry}
 * ⇒ 若 Micrometer 不在 classpath，<b>解析这个方法</b>就会 {@code NoClassDefFoundError}
 * ⇒ ⇒ 条件必须让 Spring 用 ASM 读元数据、<b>在解析方法之前</b>完成判定
 * ⇒ ⇒ 且必须写成<b>字符串</b>形式（与 {@code GoStyleRedisDeadlineAutoConfiguration} 同款纪律）。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
public class GoStylePrometheusMeterRegistryBridge implements DisposableBean {

    /**
     * 产出写入面。
     *
     * @param registries 容器里的 registry（<b>可能没有</b> —— 没有 actuator 的进程就没有）
     */
    @Bean
    @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
    public GoStylePrometheusSink goStylePrometheusSink(ObjectProvider<MeterRegistry> registries) {
        MeterRegistry registry = registries.getIfAvailable();
        if (registry == null) {
            // ⚠️ 没有 registry ⇒ **不装 sink** ⇒ 拦截器整段跳过（Go: prometheus 未启用 ⇒ 指标全空）。
            //    这里返回的 NOOP 只是为了让本方法有个返回值，**它不会被装进 holder**。
            return GoStylePrometheusSink.noop();
        }
        GoStylePrometheusSink sink = new MicrometerPrometheusSink(registry);
        GoStylePrometheusMetrics.setSink(sink);
        return sink;
    }

    /**
     * 上下文关闭时清掉静态 holder —— 否则同一 JVM 里跑第二个上下文时会用到<b>上一个上下文的 registry</b>。
     */
    @Override
    public void destroy() {
        GoStylePrometheusMetrics.clearSink();
    }
}
