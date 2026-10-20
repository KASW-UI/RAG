package com.rag.message.message.rpc.metrics;

import org.springframework.stereotype.Component;

/**
 * message-rpc 业务指标收集（空骨架）。
 *
 * <p>对应 Go 端 {@code service/message/rpc/internal/metrics/metrics.go}。
 * <br>指标名规范：{@code message_rpc_intercept_total{path=..., result=...}}，
 * 复刻 Go 端 {@code message_rpc_request_total}。
 *
 * <p>Phase 0：仅 {@code @Component} 占位，**不**引 micrometer 依赖（避免空骨架挂多余依赖）。
 * <br>Phase 1 引入 {@code micrometer-registry-prometheus} 依赖后，注册 Counter：
 * <ul>
 *   <li>{@code countSuccess(path)}</li>
 *   <li>{@code countRpcError(path)}</li>
 *   <li>{@code countInternalError(path)}</li>
 * </ul>
 */
@Component
public class MessageMetrics {

    // Phase 1 引入 MeterRegistry 字段 + 三个 Counter
}
