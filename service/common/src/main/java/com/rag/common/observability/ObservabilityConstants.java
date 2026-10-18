package com.rag.common.observability;

import io.opentelemetry.api.common.AttributeKey;

import java.time.Duration;

/**
 * 对应 Go 版 service/common/observability/observability.go 顶部的常量块。
 *
 * <p>Go 版两个常量块：
 * <pre>
 * const (
 *     SuccessBizCode       = 200
 *     DefaultSlowThreshold = 500 * time.Millisecond
 *     AttrError            = "error"
 *     AttrErrorKind        = "sea.error.kind"
 *     AttrBizCode          = "sea.biz_code"
 *     AttrBizMsg           = "sea.biz_msg"
 *     AttrTimeout          = "sea.timeout"
 *     AttrSlow             = "sea.slow"
 *     AttrDuration         = "sea.duration_ms"
 * )
 * const (
 *     ErrorKindBusiness = "business"
 *     ErrorKindSystem   = "system"
 *     ErrorKindTimeout  = "timeout"
 * )
 * </pre>
 *
 * <p>Java 版用 {@code public static final} 字段一一对应；{@link Duration} 替代 Go 的 {@code time.Duration}。
 *
 * <p>框架职责：
 * <ul>
 *   <li>Go 版 attribute.KeyValue 的 string key 直接写在 const；Java 版作为 {@code public static final String} 常量</li>
 *   <li>{@code SuccessBizCode = 200} 是 SEA 业务的"成功"约定码（与 HTTP 200 / gRPC OK 区分）</li>
 *   <li>{@code DefaultSlowThreshold = 500 * time.Millisecond} 是慢调用判定的兜底阈值</li>
 * </ul>
 */
public final class ObservabilityConstants {

    /**
     * 业务成功码（注意：与 HTTP 200 / gRPC OK 不同，这是 SEA 业务约定的成功码）。
     * 对应 Go 版 {@code SuccessBizCode = 200}。
     */
    public static final int SUCCESS_BIZ_CODE = 200;

    /**
     * 默认慢调用阈值（500ms）。
     * 对应 Go 版 {@code DefaultSlowThreshold = 500 * time.Millisecond}。
     */
    public static final Duration DEFAULT_SLOW_THRESHOLD = Duration.ofMillis(500);

    /**
     * 通用 error 标志的 attribute key。
     * 对应 Go 版 {@code AttrError = "error"}。
     */
    public static final String ATTR_ERROR = "error";

    /**
     * 错误种类 attribute key（sea.error.kind）。
     * 对应 Go 版 {@code AttrErrorKind = "sea.error.kind"}。
     */
    public static final String ATTR_ERROR_KIND = "sea.error.kind";

    /**
     * 业务码 attribute key（sea.biz_code）。
     * 对应 Go 版 {@code AttrBizCode = "sea.biz_code"}。
     */
    public static final String ATTR_BIZ_CODE = "sea.biz_code";

    /**
     * 业务信息 attribute key（sea.biz_msg）。
     * 对应 Go 版 {@code AttrBizMsg = "sea.biz_msg"}。
     */
    public static final String ATTR_BIZ_MSG = "sea.biz_msg";

    /**
     * 超时标记 attribute key（sea.timeout）。
     * 对应 Go 版 {@code AttrTimeout = "sea.timeout"}。
     */
    public static final String ATTR_TIMEOUT = "sea.timeout";

    /**
     * 慢调用标记 attribute key（sea.slow，字符串形式，用于通用 attribute 操作）。
     * 对应 Go 版 {@code AttrSlow = "sea.slow"}。
     */
    public static final String ATTR_SLOW = "sea.slow";

    /**
     * 慢调用标记 attribute key（强类型 AttributeKey<Boolean>，供 span.setAttribute 调用）。
     * 对应 Go 版 {@code AttrSlow = "sea.slow"} 的强类型 attribute.KeyValue 等价物。
     */
    public static final AttributeKey<Boolean> ATTR_SLOW_BOOL = AttributeKey.booleanKey(ATTR_SLOW);

    /**
     * 持续时间 attribute key（sea.duration_ms，单位毫秒）。
     * 对应 Go 版 {@code AttrDuration = "sea.duration_ms"}。
     */
    public static final String ATTR_DURATION = "sea.duration_ms";

    /**
     * 持续时间 attribute key（强类型 AttributeKey<Double>，毫秒，保留 Go 版 float64(microsec/1000) 亚毫秒精度）。
     * 对应 Go 版 {@code AttrDuration = "sea.duration_ms"} 的强类型等价物。
     */
    public static final AttributeKey<Double> ATTR_DURATION_DOUBLE = AttributeKey.doubleKey(ATTR_DURATION);

    /**
     * 超时标记 attribute key（强类型 AttributeKey<Boolean>）。
     * 对应 Go 版 {@code AttrTimeout = "sea.timeout"} 的强类型等价物。
     */
    public static final AttributeKey<Boolean> ATTR_TIMEOUT_BOOL = AttributeKey.booleanKey(ATTR_TIMEOUT);

    /**
     * 业务码 attribute key（强类型 AttributeKey<Long>）。
     * 对应 Go 版 {@code AttrBizCode = "sea.biz_code"} 的强类型等价物。
     */
    public static final AttributeKey<Long> ATTR_BIZ_CODE_LONG = AttributeKey.longKey(ATTR_BIZ_CODE);

    /**
     * 业务信息 attribute key（强类型 AttributeKey<String>）。
     * 对应 Go 版 {@code AttrBizMsg = "sea.biz_msg"} 的强类型等价物。
     */
    public static final AttributeKey<String> ATTR_BIZ_MSG_STR = AttributeKey.stringKey(ATTR_BIZ_MSG);

    /**
     * 错误种类：业务错误。
     * 对应 Go 版 {@code ErrorKindBusiness = "business"}。
     */
    public static final String ERROR_KIND_BUSINESS = "business";

    /**
     * 错误种类：系统错误。
     * 对应 Go 版 {@code ErrorKindSystem = "system"}。
     */
    public static final String ERROR_KIND_SYSTEM = "system";

    /**
     * 错误种类：超时。
     * 对应 Go 版 {@code ErrorKindTimeout = "timeout"}。
     */
    public static final String ERROR_KIND_TIMEOUT = "timeout";

    /**
     * 环境变量名（slow 阈值配置）。
     * 对应 Go 版 {@code "SEA_OBSERVABILITY_SLOW_THRESHOLD"}。
     */
    public static final String ENV_SLOW_THRESHOLD = "SEA_OBSERVABILITY_SLOW_THRESHOLD";

    /**
     * 环境变量名（slow 阈值配置，毫秒整型）。
     * 对应 Go 版 {@code "SEA_OBSERVABILITY_SLOW_THRESHOLD_MS"}。
     */
    public static final String ENV_SLOW_THRESHOLD_MS = "SEA_OBSERVABILITY_SLOW_THRESHOLD_MS";

    private ObservabilityConstants() {}
}