package com.rag.common.observability;

import org.springframework.core.env.Environment;

/**
 * Go「Telemetry」配置段在 RAG 侧的<b>投影</b> —— 对应 Go 各服务的
 * {@code etc/*.yaml} 里的 {@code Telemetry: { Name, Endpoint, Sampler, Batcher }}。
 *
 * <h2>🔴 为什么 RAG 侧要自己读，而不用 Boot 的自动配置</h2>
 * <p>Go 侧那一段的<b>唯一消费者</b>是 go-zero 的 {@code core/trace/agent.go} 的
 * {@code startAgent(c Config)}（{@code :105-135}）——
 * 它只读四个字段：{@code Name} / {@code Endpoint} / {@code Sampler} / {@code Batcher}。
 * ⇒ <b>语义面极窄</b>，不需要 Boot 那套「自动配置 + 属性绑定 + 条件化」的完整机制。
 * ⇒ 本类<b>显式</b>把这四个字段读出来（每个键都标注它对应 Go 的哪个字段），
 * 装配动作见 {@link GoStyleOtelAgent}。
 *
 * <h2>⚠️ 本仓的 {@code otel:} 键名<b>两套拼法并存</b>（实测 20 个模块）</h2>
 * <table border="1">
 *   <caption>api 模块 vs rpc 模块的 yaml 差异</caption>
 *   <tr><th>语义</th><th>api 模块（10 个）</th><th>rpc 模块（8 个）</th></tr>
 *   <tr><td>服务名</td>
 *       <td>{@code otel.resource.attributes.service.name}</td>
 *       <td>{@code otel.service.name}</td></tr>
 *   <tr><td>导出端点</td>
 *       <td>{@code otel.exporter.otlp.endpoint}（<b>带</b> {@code http://}）</td>
 *       <td>{@code otel.exporter.otlp.endpoint}（<b>不带</b> scheme）</td></tr>
 *   <tr><td>协议</td><td>{@code otel.exporter.otlp.protocol: grpc}</td><td>不写（默认 grpc）</td></tr>
 *   <tr><td>采样</td>
 *       <td>{@code otel.traces.sampler: always_on}（部分模块写 {@code parentbased_always_on}）</td>
 *       <td>{@code otel.traces.sampler: parentbased_always_on}</td></tr>
 * </table>
 * ⇒ ⇒ 两套拼法<b>都要认</b>，否则一半模块装不上（这是「配置一直是对的，缺的只是装配」的代价：
 * <b>配置本身就不统一</b>）。
 *
 * <h2>🔴 端点<b>没有 scheme</b> 时的处理</h2>
 * <p>Go 的 {@code otlptracegrpc.WithEndpoint(c.Endpoint) + WithInsecure()}
 * ⇒ 端点是<b>裸 host:port</b>，传输是<b>明文</b>。
 * 而 Java 的 {@code OtlpGrpcSpanExporter} 要求端点带 scheme（{@code http://} = 明文 h2c，
 * {@code https://} = TLS）。
 * ⇒ ⇒ <b>裸 host:port 一律补成 {@code http://}</b> ⇒ 与 Go 的 {@code WithInsecure()} 同义。
 *
 * @see GoStyleOtelAgent
 */
public final class GoStyleOtelConfig {

    /** Go {@code Telemetry.Name}；缺省时回落到 {@code spring.application.name}。 */
    private final String serviceName;
    /** Go {@code Telemetry.Endpoint}；<b>null</b> ⇒ 不装 exporter（照 Go 的 {@code len(c.Endpoint) > 0}）。 */
    private final String endpoint;
    /** Go {@code Telemetry.Batcher}；本仓只用 {@code otlpgrpc}。 */
    private final String protocol;
    /** Go {@code Telemetry.Sampler}（0.0 ~ 1.0 的比例）。 */
    private final double samplerRatio;

    public GoStyleOtelConfig(String serviceName, String endpoint, String protocol, double samplerRatio) {
        this.serviceName = serviceName;
        this.endpoint = endpoint;
        this.protocol = protocol;
        this.samplerRatio = samplerRatio;
    }

    public String serviceName() {
        return serviceName;
    }

    /** 已归一（补过 scheme）的导出端点；{@code null} ⇒ 不装 exporter。 */
    public String endpoint() {
        return endpoint;
    }

    public String protocol() {
        return protocol;
    }

    public double samplerRatio() {
        return samplerRatio;
    }

    /**
     * 从 Spring {@link Environment} 读出 Go 的 Telemetry 四字段。
     *
     * @param env 运行环境（Spring Boot 启动早期即可拿到）
     */
    public static GoStyleOtelConfig from(Environment env) {
        // --- 服务名：两套拼法都认（见类注释的表）---
        String serviceName = firstNonBlank(
                env.getProperty("otel.service.name"),
                env.getProperty("otel.resource.attributes.service.name"),
                env.getProperty("spring.application.name"));

        // --- 端点：两套拼法同一个键；scheme 缺失时补 http://（对齐 Go 的 WithInsecure）---
        String rawEndpoint = env.getProperty("otel.exporter.otlp.endpoint");
        String endpoint = normalizeEndpoint(rawEndpoint);

        // --- 协议：Go 的 Batcher；本仓只用 otlpgrpc ---
        String protocol = env.getProperty("otel.exporter.otlp.protocol", "grpc");

        // --- 采样：Go 的 Sampler 是一个比例；Boot 侧写的是「采样器名字」⇒ 映射成比例 ---
        String samplerName = env.getProperty("otel.traces.sampler", "always_on");
        Double samplerArg = env.getProperty("otel.traces.sampler.arg", Double.class);
        double ratio = toRatio(samplerName, samplerArg);

        return new GoStyleOtelConfig(serviceName, endpoint, protocol, ratio);
    }

    /**
     * Go 的 {@code Batcher} <b>合法集合</b> ——
     * {@code core/trace/config.go:12}
     * {@code Batcher string `json:",default=otlpgrpc,options=zipkin|otlpgrpc|otlphttp|file"`}。
     *
     * <p>⚠️ Go 侧的 yaml 解析器（go-zero 的 config loader）会按 {@code options} tag <b>先校验</b>
     * ⇒ 写错值在<b>启动读配置</b>时就炸 ⇒ 根本走不到 {@code createExporter}。
     * ⇒ 但 RAG 侧没有这一层校验 ⇒ <b>必须自己补</b>（否则非法值会被静默当成 grpc）。
     */
    private static final java.util.Set<String> GO_BATCHERS =
            java.util.Set.of("zipkin", "otlpgrpc", "otlphttp", "file");

    /**
     * 🔴 <b>两侧的命名空间不一样</b>，必须显式映射：
     *
     * <table border="1">
     *   <caption>Go 的 Batcher ↔ RAG 的 otel.exporter.otlp.protocol</caption>
     *   <tr><th>Go（{@code Telemetry.Batcher}）</th><th>RAG（{@code otel.exporter.otlp.protocol}）</th>
     *       <th>本仓是否实现</th></tr>
     *   <tr><td>{@code otlpgrpc}</td><td>{@code grpc}</td><td>✅ <b>唯一实现</b>（Go 各服务 yaml 也都是它）</td></tr>
     *   <tr><td>{@code otlphttp}</td><td>—</td><td>❌ 未实现（<b>能力缺口</b>，不是静默降级）</td></tr>
     *   <tr><td>{@code zipkin}</td><td>—</td><td>❌ 未实现（同上）</td></tr>
     *   <tr><td>{@code file}</td><td>—</td><td>❌ 未实现（同上）</td></tr>
     * </table>
     *
     * <p>⚠️ 注意 Go 侧的 yaml 写的是 {@code Batcher: otlpgrpc}，
     * 而本仓 10 个 api 的 yaml 写的是 {@code otel.exporter.otlp.protocol: grpc}
     * ⇒ <b>字面量就差一个 {@code otlp} 前缀</b> ⇒ 不显式映射就会把「合法值」判成「非法值」。
     */
    private static final java.util.Map<String, String> RAG_PROTOCOL_TO_GO_BATCHER =
            java.util.Map.of("grpc", "otlpgrpc");

    /** 本仓<b>只实现了</b> OTLP/gRPC（对齐 Go 各服务 yaml 的 {@code Batcher: otlpgrpc}）。 */
    public static final String IMPLEMENTED_BATCHER = "grpc";

    /**
     * 🔴 校验 batcher —— 对齐 Go 的两条路径：
     *
     * <ul>
     *   <li><b>端点为空 ⇒ 根本不看 batcher</b>：Go 的 {@code core/trace/agent.go:114-121}
     *       只在 {@code len(c.Endpoint) > 0} 时才调 {@code createExporter(c)}
     *       ⇒ {@code Batcher} 写错也<b>不报错</b>（只是不装 exporter）⇒ RAG 侧同款。</li>
     *   <li><b>端点非空 + 不认识的协议 ⇒ 整个 agent 装不上</b>：Go 的 {@code createExporter} 的
     *       {@code default} 分支返回 {@code fmt.Errorf("unknown exporter: %s", c.Batcher)}
     *       ⇒ {@code startAgent} 返错 ⇒ {@code StartAgent} 只 {@code logx.Error} ⇒
     *       <b>继续启动</b>（全局 trace 不生效）。</li>
     * </ul>
     *
     * @throws IllegalStateException 协议不认识时
     */
    void validateBatcher() {
        if (endpoint == null) {
            // Go: createExporter 只在 len(c.Endpoint) > 0 时被调用 ⇒ 端点为空不看 batcher
            return;
        }
        if (!RAG_PROTOCOL_TO_GO_BATCHER.containsKey(protocol)) {
            throw new IllegalStateException(
                    "unknown exporter: " + protocol
                            + "（RAG 侧只实现了 otel.exporter.otlp.protocol=" + IMPLEMENTED_BATCHER
                            + "（= Go 的 Telemetry.Batcher=" + RAG_PROTOCOL_TO_GO_BATCHER.get(IMPLEMENTED_BATCHER)
                            + "）；Go 的合法 Batcher 集合 = " + GO_BATCHERS
                            + "，其余三种本仓未实现）");
        }
    }

    /**
     * Go 的 {@code Telemetry.Sampler} 是<b>比例</b>（{@code core/trace/config.go:9}
     * {@code Sampler float64 `json:",default=1.0"`}），
     * 而本仓 yaml 里写的是 Boot 风格的<b>采样器名字</b> ⇒ 这里做一次映射。
     *
     * <p>⚠️ <b>无论哪种名字，最终都要包在 {@code ParentBased} 里</b> ——
     * Go 的 {@code core/trace/agent.go:111} 是
     * {@code sdktrace.WithSampler(sdktrace.ParentBased(sdktrace.TraceIDRatioBased(c.Sampler)))}
     * ⇒ <b>永远</b>是 ParentBased ⇒ 「入向 parent 不采样 ⇒ 子 span 也不采样」。
     */
    static double toRatio(String samplerName, Double samplerArg) {
        String name = samplerName == null ? "" : samplerName.trim().toLowerCase();
        switch (name) {
            case "always_on":
            case "parentbased_always_on":
                return 1.0d;
            case "always_off":
            case "parentbased_always_off":
                return 0.0d;
            case "traceidratio":
            case "parentbased_traceidratio":
                return samplerArg == null ? 1.0d : samplerArg;
            default:
                throw new IllegalStateException(
                        "otel.traces.sampler=" + samplerName + " 无法映射到 Go 的 Telemetry.Sampler（比例）。"
                                + "支持：always_on / parentbased_always_on / always_off / parentbased_always_off"
                                + " / traceidratio / parentbased_traceidratio");
        }
    }

    /**
     * 端点归一：<b>裸 {@code host:port} 补成 {@code http://host:port}</b>。
     *
     * <p>Go 出处：{@code core/trace/agent.go:70-71}
     * {@code otlptracegrpc.WithEndpoint(c.Endpoint)} + {@code otlptracegrpc.WithInsecure()}
     * ⇒ Go 的端点<b>不带</b> scheme，传输<b>明文</b>。
     * ⇒ Java 侧 {@code OtlpGrpcSpanExporter} 需要 scheme ⇒ {@code http://} 即明文 h2c（等价于 WithInsecure）。
     */
    static String normalizeEndpoint(String rawEndpoint) {
        if (rawEndpoint == null) {
            return null;
        }
        String endpoint = rawEndpoint.trim();
        if (endpoint.isEmpty()) {
            return null;
        }
        String lower = endpoint.toLowerCase();
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return endpoint;
        }
        return "http://" + endpoint;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }
}
