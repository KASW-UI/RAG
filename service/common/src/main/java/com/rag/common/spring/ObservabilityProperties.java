package com.rag.common.spring;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Observability 配置绑定（{@code application.yml}）：
 * <pre>{@code
 * observability:
 *   slow-threshold: 750ms    # Go duration 格式或纯毫秒
 *   http-timeout: 3s         # 对齐 Go rest.RestConf.Timeout
 *   rpc-timeout: 2s          # 对齐 Go zrpc.RpcServerConf.Timeout
 * }</pre>
 *
 * <p><b>优先级</b>：
 * <ol>
 *   <li>{@link #getSlowThreshold()}（application.yml / Spring properties）</li>
 *   <li>环境变量 {@code SEA_OBSERVABILITY_SLOW_THRESHOLD} / {@code SEA_OBSERVABILITY_SLOW_THRESHOLD_MS}</li>
 *   <li>默认 500ms（{@code ObservabilityConstants.DEFAULT_SLOW_THRESHOLD}）</li>
 * </ol>
 *
 * <p>timeout 两项<b>没有 env 兜底</b>，缺省即 {@link Duration#ZERO}（不限时）——
 * 这正是 Go 侧 {@code TimeoutFromMillis(ms<=0) → 0} 的语义：
 * <pre>
 *   Go observability.go:57-62
 *     func TimeoutFromMillis(ms int64) time.Duration {
 *         if ms &lt;= 0 { return 0 }
 *         return time.Duration(ms) * time.Millisecond
 *     }
 * </pre>
 *
 * <p><b>H1 修复背景</b>：本类此前<b>只有</b> {@code slowThreshold} 一个字段，
 * 而 {@link HttpMiddlewareFilter} / {@link RpcServerInterceptorFactory} 都把 timeout
 * 硬编码成 {@code Duration.ZERO} —— 等价于 Go 侧把 {@code conf.Timeout} 读出来就丢掉。
 * 超时配置连承载它的位置都不存在，503/499 分支与 gRPC deadline 因此永不触发。
 *
 * <p><b>默认值的取证</b>：Go 侧 {@code service/user/{user,admin}/{api,rpc}/etc/*.yaml}
 * <b>均未配置 {@code Timeout}</b>，实际生效值来自 go-zero v1.10.2 结构体默认值：
 * <ul>
 *   <li>{@code rest/config.go:55} {@code Timeout int64 `json:",default=3000"`} → <b>3000ms</b></li>
 *   <li>{@code zrpc/config.go:46} {@code Timeout int64 `json:",default=2000"`} → <b>2000ms</b></li>
 * </ul>
 * 即 Go 实际跑 HTTP 3s / gRPC 2s。各模块 yaml 须显式写出同值，不得留空。
 */
@Component
@ConfigurationProperties("observability")
public class ObservabilityProperties {

    /**
     * 慢调用阈值；{@code null} 时 {@code Observability.slowThreshold()} 回退到 env var 读取。
     */
    private Duration slowThreshold;

    /**
     * HTTP 请求整体超时（{@code observability.http-timeout}）。
     *
     * <p>对应 Go 版 {@code DisableNativeRestTimeout(&c.RestConf)} 的返回值：
     * Go 先关掉 go-zero 自带的 rest 超时中间件（{@code conf.Middlewares.Timeout = false}），
     * 再把 {@code conf.Timeout} 交回观测中间件接管 —— <b>超时只有一个执行者</b>，
     * 不会出现框架与观测层各判一次的情况。
     *
     * <p>{@code null} / 零值 = 不限时，与 Go 的 {@code TimeoutFromMillis(0) → 0} 一致。
     */
    private Duration httpTimeout;

    /**
     * gRPC 单次调用超时（{@code observability.rpc-timeout}）。
     *
     * <p>对应 Go 版 {@code DisableNativeRpcTimeout(&c.RpcServerConf)} 的返回值
     * （{@code conf.Timeout = 0} 后交回 {@code NewUnaryServerInterceptor}）。
     *
     * <p>{@code null} / 零值 = 不限时，与 Go 的 {@code TimeoutFromMillis(0) → 0} 一致。
     */
    private Duration rpcTimeout;

    public Duration getSlowThreshold() {
        return slowThreshold;
    }

    public void setSlowThreshold(Duration slowThreshold) {
        this.slowThreshold = slowThreshold;
    }

    public Duration getHttpTimeout() {
        return httpTimeout;
    }

    public void setHttpTimeout(Duration httpTimeout) {
        this.httpTimeout = httpTimeout;
    }

    public Duration getRpcTimeout() {
        return rpcTimeout;
    }

    public void setRpcTimeout(Duration rpcTimeout) {
        this.rpcTimeout = rpcTimeout;
    }
}