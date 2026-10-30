package com.rag.common.observability;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

/**
 * <b>RPC 指标面（Stat ＋ Prometheus）的装配点</b>。
 *
 * <h2>装配面</h2>
 * <p>本类登记在 {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * ⇒ ⇒ <b>凡是依赖 {@code service/common} 的进程都自动生效</b>
 * ⇒ ⇒ <b>10 个 rpc 组件零改动</b>获得：
 * <ul>
 *   <li>Stat 聚合器的<b>服务名</b>（Go 的 {@code metrics.SetName(c.Name)}）；</li>
 *   <li>Prometheus 写入面（<b>仅当容器里真有 registry 时</b>）。</li>
 * </ul>
 *
 * <h2>🔴 服务名为什么取 {@code spring.application.name}</h2>
 * <pre>
 *   go-zero@v1.10.2/zrpc/server.go:37   metrics := stat.NewMetrics(c.ListenOn)
 *   go-zero@v1.10.2/zrpc/server.go:52   metrics.SetName(c.Name)
 *   真源 service/follow/rpc/etc/follow.yaml:1   Name: follow.rpc
 * </pre>
 * ⇒ Go 的 stat 报告行首是 {@code (follow.rpc) - qps: ...}。
 * <p>本仓的 rpc 服务名是 {@code spring.application.name}（{@code follow-rpc}）
 * ⇒ ⇒ 本仓的报告行首是 {@code (follow-rpc) - qps: ...}。
 * ⇒ ⚠️ <b>字符串形态不同（点 vs 横杠）</b>：那是<b>服务身份字符串</b>本身的差异
 * （Go 的服务发现名是 {@code follow.rpc}，本仓注册进 Nacos 的是 {@code follow-rpc}）
 * ⇒ ⇒ 已作为「口径差异」登记（不是行为漂移：聚合语义逐位相同）。
 *
 * <h2>⚠️ 本类<b>不</b>启动 Stat 的上报线程</h2>
 * <p>Go 的 {@code stat.Metrics} 是<b>每个 rpc server 一个</b>（{@code zrpc/server.go:37}）
 * ⇒ ⇒ 上报线程只能由「真的装了 Stat 层的进程」启动
 * ⇒ ⇒ 启动点放在 {@link GoStyleStatInterceptor} 的构造器里（与
 * {@link com.rag.common.protection.GoStyleSheddingStat#startReporter()} 同款理由）
 * ⇒ ⇒ <b>9 个 api 进程不会凭空多出每分钟一行、qps 恒为 0 的统计日志</b>
 * （Go 侧 api 走 {@code rest.Server}，根本没有 {@code stat.Metrics}）。
 */
@AutoConfiguration
@Import(GoStylePrometheusMeterRegistryBridge.class)
public class GoStyleRpcMetricsConfiguration {

    /**
     * 把服务名绑到 Stat 聚合器上（Go 的 {@code metrics.SetName(c.Name)}）。
     *
     * @param environment Spring 环境（读 {@code spring.application.name}）
     */
    @Bean
    public GoStyleStatMetricsNameInitializer goStyleStatMetricsNameInitializer(Environment environment) {
        String name = environment.getProperty("spring.application.name");
        GoStyleStatMetrics.getInstance().setName(name);
        return new GoStyleStatMetricsNameInitializer(
                name == null || name.isEmpty() ? GoStyleStatMetrics.DEFAULT_NAME : name);
    }

    /**
     * 已绑定的 Stat 服务名（<b>只读值</b>）。
     *
     * @param name 最终生效的服务名（{@code spring.application.name} 缺失时回落 {@code rpc}）
     */
    public record GoStyleStatMetricsNameInitializer(String name) {
    }
}
