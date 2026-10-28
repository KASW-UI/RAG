package com.rag.common.protection;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.io.IOException;

/**
 * L4 idiomatization：CPU 自适应降载，对齐 Go {@code handler.SheddingHandler}。
 *
 * <p><b>Go 出处</b>：{@code rest/handler/sheddinghandler.go:22-56}
 * <pre>
 * promise, err := shedder.Allow()
 * if err != nil {
 *     metrics.AddDrop()
 *     w.WriteHeader(http.StatusServiceUnavailable)      // ← 503，无 body
 *     return
 * }
 * cw := response.NewWithCodeResponseWriter(w)
 * defer func() {
 *     if cw.Code == http.StatusServiceUnavailable { promise.Fail() }
 *     else { sheddingStat.IncrementPass(); promise.Pass() }
 * }()
 * next.ServeHTTP(cw, r)
 * </pre>
 *
 * <p><b>shedder 的共享范围</b>：Go {@code rest/engine.go:52-56} 在
 * {@code newEngine} 里<b>整个 engine 建一个</b> {@code load.NewAdaptiveShedder(...)}
 * ⇒ 全局一份，<b>不是 per-route</b>（与 breaker 相反）。
 * 本类相应地只持有一个 {@link GoStyleAdaptiveShedder}。
 *
 * <p><b>⚠️ {@code promise.Fail()} 的判据是「响应码 == 503」，不是「>= 500」</b>
 * （{@code sheddinghandler.go:45}）。这与 breaker 的 {@code < 500} 判据<b>刻意不同</b>，
 * 两处都照搬：Go 侧一个看 {@code == 503}、一个看 {@code < 500}，不是笔误。
 *
 * <p><b>链上位置</b>：Go {@code engine.go:151-153} 在 Breaker 之后、Timeout 之前
 * ⇒ 本类注册在 {@link Ordered#HIGHEST_PRECEDENCE} + 6。
 *
 * <p>⚠️ <b>2026-10-25（第十九轮 · global T-8）：由 +3 改为 +6</b>（给 traceparent filter 让出位置，
 * 相对顺序不变 —— 见 {@code GoStyleMaxConnsFilter} 的同款说明）。
 *
 * <p><b>为什么必须自定义、不能用现成件</b>：Spring Boot 4 / Actuator / Tomcat
 * <b>没有任何 CPU 负载降载机制</b>（Actuator 的 {@code HealthEndpoint} 只是被动上报，
 * 不会拒流量）。缺口具体到「在 CPU 压力下按 QPS×RT 动态收紧并发上限并概率性拒绝」，
 * 没有任何可复用的框架能力，故按 Go 算法自行实现。
 *
 * <p><b>为什么默认 {@code enabled=true} 但读不到 CPU 时 fail-open</b>：
 * Go 的 {@code systemOverloaded()} 首个分支就是「CPU 检查器返回 false ⇒ 不过载」。
 * Java 的 {@code getSystemCpuLoad()} 在部分容器里可能恒为 {@code -1}，
 * 若把 -1 当过载，<b>会把全部流量杀光</b>；若当不过载，则退化为「不降载」，
 * 与 Go 在「读不到指标」时的行为同向。⇒ 选 fail-open。
 */
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 6)
public class GoStyleCpuSheddingFilter implements Filter {

    private final boolean enabled;
    private final GoStyleAdaptiveShedder shedder;

    public GoStyleCpuSheddingFilter(GoStyleProtectionProperties props) {
        this.enabled = props.getCpuShedding().isEnabled();
        this.shedder = new GoStyleAdaptiveShedder(
                props.maxCpuPermille(), props.getCpuShedding().getCpuSource());
    }

    /** 测试注入确定性 CPU 读数。 */
    void setCpuPermilleReader(java.util.function.IntSupplier reader) {
        shedder.setCpuPermilleReader(reader);
    }

    /** 排障用：当前在途请求数。 */
    long flying() {
        return shedder.flying();
    }

    /**
     * <b>RT 的计算口径</b> —— Go {@code adaptiveshedder.go:285}：
     * <pre>
     * rt := float64(timex.Since(p.start)) / float64(time.Millisecond)
     * </pre>
     * ⇒ <b>float64 毫秒</b>，之后由 {@code math.Ceil} 向上取整。
     *
     * <p><b>⚠️ 必须是浮点除法</b>：若写成整型（{@code / 1_000_000L}）就是<b>截断</b>，
     * RT 会系统性少记最多 1ms ⇒ {@code minRt()} 偏小 ⇒
     * {@code maxFlight = maxPass * minRt * 0.01} 偏小 ⇒
     * <b>在途上限系统性偏低 ⇒ RAG 比 Go 更早降载</b>。
     *
     * <p><b>为什么提成静态方法</b>：RT 的<b>真实</b>耗时不可控，
     * 直接断言「filter 记进去的 RT」只能是 flaky 的；
     * 把口径本身提成纯函数后，<b>截断 vs 浮点</b>这一位差别就可以被<b>确定性</b>断言。
     */
    static double elapsedRtMs(long startNanos, long endNanos) {
        return (endNanos - startNanos) / 1_000_000.0D;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        if (!enabled) {
            chain.doFilter(req, resp);
            return;
        }
        HttpServletResponse response = (HttpServletResponse) resp;

        long startNanos = System.nanoTime();
        if (!shedder.allow()) {
            // sheddinghandler.go:29-35
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }

        GoStyleStatusCapturingResponse capturing = new GoStyleStatusCapturingResponse(response);
        try {
            chain.doFilter(req, capturing);
        } finally {
            // sheddinghandler.go:40-48 的 defer
            if (capturing.capturedStatus() == HttpServletResponse.SC_SERVICE_UNAVAILABLE) {
                shedder.fail();
            } else {
                double rtMs = elapsedRtMs(startNanos, System.nanoTime());
                shedder.pass(rtMs);
            }
        }
    }
}