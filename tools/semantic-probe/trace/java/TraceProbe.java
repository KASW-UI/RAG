import com.rag.common.observability.GoStyleOtelAgent;
import com.rag.common.observability.GoStyleOtelConfig;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.sdk.trace.SdkTracerProvider;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

/**
 * 【global 域 · 第二十四轮】trace 链路的 **RAG 半边活体探针**。
 *
 * <p>与 {@code tools/semantic-probe/trace/go/liveprobe_test.go}（Go 真源半边）
 * 输出**同一套 {@code PROBE|<case>|<key>=<value>} 行** ⇒ 由
 * {@code run-trace-parity.sh} 按用例名配对逐条比对。
 *
 * <h2>🔴 RAG 半边必须用<b>生产代码</b>，不许自己 new 一个</h2>
 * <p>AGENTS.md §3.3 规则 2 的最小形态：「RAG 侧用项目**真实的** mapper（不是自己 new 一个）」。
 * ⇒ 本探针只调 {@link GoStyleOtelAgent} / {@link GoStyleOtelConfig} 这两个
 * <b>生产类</b>，<b>不复制</b>任何装配逻辑 ⇒ 探针红 ⇒ 生产代码真的不对。
 *
 * <h2>为什么 G8 的传播器要靠一次成功的 {@code install()} 才拿得到</h2>
 * <p>Go 侧的传播器是 {@code core/trace/propagation.go} 的 {@code init()} 装的
 * （<b>无条件</b>）⇒ 全局 {@code otel.GetTextMapPropagator()} 直接可读。
 * <p>RAG 侧的全局传播器由 {@link GoStyleOtelAgent#install} 一并装上
 * ⇒ 探针先跑完 G1（那次 install 失败 ⇒ 不会碰全局），
 * <b>最后</b>再跑一次成功的 install 来取全局传播器。
 *
 * <h2>⚠️ 只输出稳定值</h2>
 * <p>trace-id 是随机的 ⇒ 输出布尔 / 枚举，不输出原始 id ⇒ 否则两侧永远对不上。
 */
public final class TraceProbe {

    private static final String PROBE_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String PROBE_SPAN_ID = "00f067aa0ba902b7";

    private TraceProbe() {
    }

    private static void report(String caseName, String... kv) {
        StringBuilder sb = new StringBuilder("PROBE|").append(caseName);
        for (String pair : kv) {
            sb.append('|').append(pair);
        }
        System.out.println(sb);
    }

    private static String bool(boolean b) {
        return b ? "true" : "false";
    }

    /** 造一个「远端 parent」的上下文（flags 位决定 sampled）。 */
    private static Context remoteParent(boolean sampled) {
        TraceFlags flags = sampled ? TraceFlags.getSampled() : TraceFlags.getDefault();
        SpanContext sc = SpanContext.createFromRemoteParent(
                PROBE_TRACE_ID, PROBE_SPAN_ID, flags, TraceState.getDefault());
        return Context.root().with(Span.wrap(sc));
    }

    // ------------------------------------------------------------------ G1
    /** 装配失败 ⇒ 只记 ERROR、返回 null、<b>不抛</b>、全局仍是 no-op。 */
    private static void g1() {
        // ⚠️ OTel Java 的 `GlobalOpenTelemetry.get()` 会把全局**永久**占成 noop
        //    （后续 `set()` 抛 IllegalStateException）⇒ 本探针要读全局就必须先 resetForTest()
        //    ⇒ 且读完之后**再 reset 一次**，否则后面的 G8 装不上。
        GlobalOpenTelemetry.resetForTest();
        GoStyleOtelConfig cfg = new GoStyleOtelConfig(
                "liveprobe", "http://127.0.0.1:4317", "bogus-batcher", 1.0);
        OpenTelemetry installed = null;
        boolean threw = false;
        try {
            installed = GoStyleOtelAgent.install(cfg);
        } catch (RuntimeException e) {
            threw = true;
        }
        Span globalSpan = GlobalOpenTelemetry.get().getTracer("liveprobe").spanBuilder("s").startSpan();
        boolean globalStillNoop = !globalSpan.isRecording();
        report("G1_startAgent_failure",
                "install_failed=" + bool(installed == null),
                "threw=" + bool(threw),
                "global_still_noop=" + bool(globalStillNoop),
                "span_recording=" + bool(globalSpan.isRecording()),
                "span_sampled=" + bool(globalSpan.getSpanContext().isSampled()));
        // 解除 get() 造成的毒化 ⇒ 让 G8 能真正装上一次
        GlobalOpenTelemetry.resetForTest();
    }

    // ------------------------------------------------------------------ G2
    /** 非法 batcher + 端点非空 ⇒ 抛错（消息含 unknown exporter）。 */
    private static void g2() {
        String msg = "";
        boolean threw = false;
        try {
            GoStyleOtelAgent.buildProvider(new GoStyleOtelConfig(
                    "liveprobe", "http://127.0.0.1:4317", "bogus-batcher", 1.0));
        } catch (RuntimeException e) {
            threw = true;
            msg = String.valueOf(e.getMessage());
        }
        report("G2_unknown_exporter",
                "err_nil=" + bool(!threw),
                "err_contains_unknown_exporter=" + bool(msg.contains("unknown exporter")));
    }

    // ------------------------------------------------------------------ G3
    /** 非法 batcher + <b>端点为空</b> ⇒ 不校验、装得出 provider。 */
    private static void g3() {
        boolean threw = false;
        SdkTracerProvider p = null;
        try {
            p = GoStyleOtelAgent.buildProvider(new GoStyleOtelConfig(
                    "liveprobe", null, "bogus-batcher", 1.0));
        } catch (RuntimeException e) {
            threw = true;
        }
        report("G3_empty_endpoint", "err_nil=" + bool(!threw));
        if (p != null) {
            p.close();
        }
    }

    // ------------------------------------------------------------------ G4
    /** ratio=0 ⇒ 不 recording、不 sampled，但 SpanContext 仍有效。 */
    private static void g4() {
        SdkTracerProvider p = GoStyleOtelAgent.buildProvider(new GoStyleOtelConfig(
                "liveprobe", null, "grpc", 0.0));
        try {
            Span span = p.get("liveprobe").spanBuilder("root").startSpan();
            SpanContext sc = span.getSpanContext();
            report("G4_ratio_zero",
                    "recording=" + bool(span.isRecording()),
                    "sampled=" + bool(sc.isSampled()),
                    "sc_valid=" + bool(sc.isValid()),
                    "trace_id_nonempty=" + bool(!"00000000000000000000000000000000".equals(sc.getTraceId())));
            span.end();
        } finally {
            p.close();
        }
    }

    // ------------------------------------------------------------------ G5
    /** ratio=0 + 入向 sampled parent ⇒ 子 span 仍被采样（ParentBased）。 */
    private static void g5() {
        SdkTracerProvider p = GoStyleOtelAgent.buildProvider(new GoStyleOtelConfig(
                "liveprobe", null, "grpc", 0.0));
        try {
            Span span = p.get("liveprobe").spanBuilder("child").setParent(remoteParent(true)).startSpan();
            SpanContext sc = span.getSpanContext();
            report("G5_parentbased_sampled_parent",
                    "recording=" + bool(span.isRecording()),
                    "sampled=" + bool(sc.isSampled()),
                    "parent_trace_id_inherited=" + bool(PROBE_TRACE_ID.equals(sc.getTraceId())));
            span.end();
        } finally {
            p.close();
        }
    }

    // ------------------------------------------------------------------ G6
    /** ratio=1.0 + 入向 <b>未采样</b> parent ⇒ 子 span <b>不</b>采样（ParentBased）。 */
    private static void g6() {
        SdkTracerProvider p = GoStyleOtelAgent.buildProvider(new GoStyleOtelConfig(
                "liveprobe", null, "grpc", 1.0));
        try {
            Span span = p.get("liveprobe").spanBuilder("child").setParent(remoteParent(false)).startSpan();
            SpanContext sc = span.getSpanContext();
            report("G6_parentbased_unsampled_parent",
                    "recording=" + bool(span.isRecording()),
                    "sampled=" + bool(sc.isSampled()),
                    "sc_valid=" + bool(sc.isValid()));
            span.end();
        } finally {
            p.close();
        }
    }

    // ------------------------------------------------------------------ G7
    /**
     * Go 支持的四种 batcher，RAG 侧逐一试。
     *
     * <p>⚠️ <b>命名空间映射</b>：RAG 的 {@code otel.exporter.otlp.protocol} 用
     * {@code grpc}（= Go 的 {@code otlpgrpc}）；其余三种 Go 名字在 RAG 侧
     * <b>没有映射</b> ⇒ 直接用 Go 的名字去试 ⇒ validateBatcher 抛
     * 「unknown exporter」⇒ 这正是<b>能力缺口</b>（不是静默降级）。
     */
    private static void g7() {
        String[][] cases = {
                {"otlpgrpc", "grpc"},
                {"otlphttp", "otlphttp"},
                {"zipkin", "zipkin"},
                {"file", "file"},
        };
        for (String[] c : cases) {
            String goName = c[0];
            String ragProtocol = c[1];
            boolean threw = false;
            SdkTracerProvider p = null;
            try {
                p = GoStyleOtelAgent.buildProvider(new GoStyleOtelConfig(
                        "liveprobe", "http://127.0.0.1:4317", ragProtocol, 1.0));
            } catch (RuntimeException e) {
                threw = true;
            }
            report("G7_exporter_" + goName,
                    "err_nil=" + bool(!threw),
                    "built=" + bool(p != null));
            if (p != null) {
                p.close();
            }
        }
    }

    // ------------------------------------------------------------------ G8
    /**
     * 全局传播器 = Composite(TraceContext, Baggage)。
     *
     * <p>⚠️ 必须<b>最后</b>跑：它靠一次<b>成功</b>的 install() 才把全局传播器装上
     * （OTel Java 的 {@code GlobalOpenTelemetry.set()} 只能调一次）。
     */
    private static void g8() {
        GlobalOpenTelemetry.resetForTest();
        OpenTelemetry sdk = GoStyleOtelAgent.install(new GoStyleOtelConfig(
                "liveprobe", null, "grpc", 1.0));
        if (sdk == null) {
            System.out.println("PROBE|G8_propagation|install_failed=true");
            return;
        }
        TextMapPropagator prop = GlobalOpenTelemetry.getPropagators().getTextMapPropagator();

        Map<String, String> incoming = new LinkedHashMap<>();
        incoming.put("traceparent", "00-" + PROBE_TRACE_ID + "-" + PROBE_SPAN_ID + "-01");
        incoming.put("baggage", "k=v");

        Context extracted = prop.extract(Context.root(), incoming, MAP_GETTER);
        SpanContext sc = Span.fromContext(extracted).getSpanContext();

        Collection<String> fields = prop.fields();
        boolean hasBaggage = fields.contains("baggage");

        Map<String, String> out = new LinkedHashMap<>();
        prop.inject(extracted, out, MAP_SETTER);

        report("G8_propagation",
                "extracted_trace_id_matched=" + bool(PROBE_TRACE_ID.equals(sc.getTraceId())),
                "extracted_sampled=" + bool(sc.isSampled()),
                "fields=" + String.join(",", new ArrayList<>(fields)),
                "fields_has_baggage=" + bool(hasBaggage),
                "injected_traceparent_present=" + bool(out.containsKey("traceparent")));
    }

    private static final TextMapGetter<Map<String, String>> MAP_GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier == null ? Collections.emptyList() : carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
            return carrier == null ? null : carrier.get(key);
        }
    };

    private static final io.opentelemetry.context.propagation.TextMapSetter<Map<String, String>> MAP_SETTER =
            (carrier, key, value) -> carrier.put(key, value);

    public static void main(String[] args) {
        g1();
        g2();
        g3();
        g4();
        g5();
        g6();
        g7();
        g8();
        System.out.flush();
    }
}
