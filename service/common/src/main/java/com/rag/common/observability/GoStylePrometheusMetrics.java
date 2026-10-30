package com.rag.common.observability;

/**
 * <b>Prometheus 写入面的进程级持有者</b>（静态 holder）。
 *
 * <h3>为什么必须是静态的</h3>
 * <p>拦截器链的构造点是 <b>静态</b>的：
 * {@code GoStyleRpcProtectionChain.build(business, props)} —— 它没有 Spring 上下文
 * ⇒ ⇒ 拿不到 {@code MeterRegistry} ⇒ ⇒ 只能由「装配期」的代码把实现塞进这个 holder，
 * 拦截器在<b>每次请求</b>时从 holder 取当前的 sink。
 *
 * <p>⚠️ <b>为什么按「每次请求取」而不是「构造时取一次」</b>：
 * 链是在 {@code @Bean GrpcServerConfigurer} 里构造的（启动早期），
 * 而 sink 的装配（{@link GoStylePrometheusMeterRegistryBridge}）与它的<b>先后顺序不保证</b>
 * ⇒ ⇒ 构造时取一次会拿到「永远为 null」的 sink ⇒ ⇒ 指标<b>永远写不出去</b>且<b>不报错</b>
 * （AGENTS.md §3.3 的经典形态）。
 *
 * <h3>⚠️ 静态状态的边界</h3>
 * <p>同一 JVM 里跑多个 Spring 上下文时，sink 会被<b>后一个上下文覆盖</b>（与
 * {@link GoStyleSheddingStat} 的进程级单例同款取舍）。
 * ⇒ ⇒ 上下文关闭时必须清掉（{@link GoStylePrometheusMeterRegistryBridge#destroy()}）。
 */
public final class GoStylePrometheusMetrics {

    private static volatile GoStylePrometheusSink sink;

    private GoStylePrometheusMetrics() {
    }

    /** 装上写入面（装配期调用）。 */
    public static void setSink(GoStylePrometheusSink newSink) {
        sink = newSink;
    }

    /** 当前写入面；{@code null} = <b>没有 registry ⇒ 一条都不写</b>（Go：{@code prometheus.Enabled()==false}）。 */
    public static GoStylePrometheusSink sink() {
        return sink;
    }

    /** 清掉写入面（上下文关闭 / 测试复位）。 */
    public static void clearSink() {
        sink = null;
    }
}
