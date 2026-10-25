package trace

// ============================================================================
// 【global 域 · 第二十四轮】trace 链路的 **Go 半边活体探针**
//
// 定位
// ----
// AGENTS.md §3.3 规则 2：「凡写『与 Go 一致』，必须有**两侧同时跑的探针**」
// ⇒ 本文件是 T-11（全局 OpenTelemetry）那条链路的**缺的另一半**：
//   RAG 侧有守门（GoStyleOtelSamplerProtocolGateTest），Go 侧此前只有「逐行读源码」。
//
// ⚠️ 为什么是 **package trace**（不是 trace_test）：
//    `createExporter` / `startAgent` 是**未导出**的 ⇒ 外部包根本调不到
//    ⇒ 要活体证「unknown exporter」「端点为空不校验」「四种 exporter 都能构造」，
//      只能进包内。
//    ⇒ 这**不是**「照抄真源逻辑再自己跑一遍」，而是**直接跑真源的源码**。
//
// 运行方式
// --------
// 模块缓存是**只读**的，且 `go -overlay` **不允许替换 GOMODCACHE 下的文件**
// （实测：`Files beneath GOMODCACHE must not be replaced`）
// ⇒ `run-trace-parity.sh` 的做法是：把 module cache 里的 go-zero **整份拷到临时目录**
//   → 放入本文件 → `go test ./core/trace/`。
//   拷完会做**逐字节校验**（diff -r）⇒ 确认「跑的就是缓存里那份真源」。
//
// 输出格式：`PROBE|<case>|<key>=<value>|...`
//   ⚠️ **只输出稳定值**（布尔 / 枚举）⇒ 不输出随机 trace-id
//      ⇒ 否则两侧永远对不上（trace-id 是随机的）。
// ============================================================================

import (
	"context"
	"net/http"
	"path/filepath"
	"strings"
	"testing"

	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/propagation"
	"go.opentelemetry.io/otel/trace"
)

func report(t *testing.T, caseName string, kv ...string) {
	t.Logf("PROBE|%s|%s", caseName, strings.Join(kv, "|"))
}

func carrier(kv map[string]string) propagation.HeaderCarrier {
	h := http.Header{}
	for k, v := range kv {
		h.Set(k, v)
	}
	return propagation.HeaderCarrier(h)
}

const (
	probeTraceID = "4bf92f3577b34da6a3ce929d0e0e4736"
	probeSpanID  = "00f067aa0ba902b7"
)

// ---------------------------------------------------------------------------
// G1 —— StartAgent 装配失败 ⇒ **只记 ERROR，不 panic，不阻断启动**
//
// Go 出处：core/trace/agent.go:43-52
//   once.Do(func() { if err := startAgent(c); err != nil { logx.Error(err) } })
// ---------------------------------------------------------------------------
func TestLiveProbe1_StartAgentFailureDoesNotPanic(t *testing.T) {
	// ⚠️ StartAgent 内部有 sync.Once ⇒ 全进程只能调一次 ⇒ 本测试必须是第一个。
	before := otel.GetTracerProvider()
	StartAgent(Config{
		Name:     "liveprobe",
		Endpoint: "127.0.0.1:4317",
		Sampler:  1.0,
		Batcher:  "bogus-batcher", // 非法
	})

	after := otel.GetTracerProvider()
	// 失败 ⇒ tp 全局变量没被赋值 ⇒ 全局 provider 仍是默认的 no-op
	_, span := after.Tracer("liveprobe").Start(context.Background(), "s")
	report(t, "G1_startAgent_failure",
		"install_failed="+boolStr(tp == nil),
		"threw=false",
		"global_still_noop="+boolStr(!span.IsRecording()),
		"span_recording="+boolStr(span.IsRecording()),
		"span_sampled="+boolStr(span.SpanContext().IsSampled()),
	)
	if tp != nil {
		t.Errorf("G1: 装配失败后 tp 应当仍为 nil，实际非 nil")
	}
	if before != after {
		t.Errorf("G1: 装配失败后全局 provider 不应被替换")
	}
	if span.IsRecording() {
		t.Errorf("G1: 装配失败后全局 provider 应当仍是 no-op（span 不 recording）")
	}
}

// ---------------------------------------------------------------------------
// G2 —— 非法 batcher + 端点非空 ⇒ createExporter 返 "unknown exporter: X"
// Go 出处：core/trace/agent.go:100-101（default 分支）
// ---------------------------------------------------------------------------
func TestLiveProbe2_UnknownExporterErrors(t *testing.T) {
	_, err := createExporter(Config{Batcher: "bogus-batcher", Endpoint: "127.0.0.1:4317"})
	msg := ""
	if err != nil {
		msg = err.Error()
	}
	report(t, "G2_unknown_exporter",
		"err_nil="+boolStr(err == nil),
		"err_contains_unknown_exporter="+boolStr(strings.Contains(msg, "unknown exporter")),
	)
	if err == nil || !strings.Contains(msg, "unknown exporter") {
		t.Errorf("G2: 期望 error 含 'unknown exporter'，实际 %q", msg)
	}
}

// ---------------------------------------------------------------------------
// G3 —— 非法 batcher + **端点为空** ⇒ startAgent **不报错**
// Go 出处：core/trace/agent.go:115-124（`if len(c.Endpoint) > 0 { createExporter(c) }`）
// ⇒ 端点为空 ⇒ 根本不看 Batcher。
// ---------------------------------------------------------------------------
func TestLiveProbe3_EmptyEndpointSkipsExporter(t *testing.T) {
	err := startAgent(Config{Name: "liveprobe", Endpoint: "", Sampler: 1.0, Batcher: "bogus-batcher"})
	report(t, "G3_empty_endpoint", "err_nil="+boolStr(err == nil))
	if err != nil {
		t.Errorf("G3: 端点为空时不应报错，实际 %v", err)
	}
}

// ---------------------------------------------------------------------------
// G4 —— Sampler=0.0 ⇒ span **不 sampled、不 recording**，但 SpanContext **仍有效**
// Go 出处：core/trace/agent.go:110（ParentBased(TraceIDRatioBased(c.Sampler)))
//           core/trace/config.go:10（Sampler float64 default=1.0）
// ---------------------------------------------------------------------------
func TestLiveProbe4_SamplerRatioZero(t *testing.T) {
	if err := startAgent(Config{Name: "liveprobe", Endpoint: "", Sampler: 0.0, Batcher: "otlpgrpc"}); err != nil {
		t.Fatalf("G4: startAgent 失败: %v", err)
	}
	_, span := otel.Tracer("liveprobe").Start(context.Background(), "root")
	sc := span.SpanContext()
	report(t, "G4_ratio_zero",
		"recording="+boolStr(span.IsRecording()),
		"sampled="+boolStr(sc.IsSampled()),
		"sc_valid="+boolStr(sc.IsValid()),
		"trace_id_nonempty="+boolStr(sc.TraceID().String() != "00000000000000000000000000000000"),
	)
	if span.IsRecording() {
		t.Errorf("G4: ratio=0 时 span 应当不 recording")
	}
	if sc.IsSampled() {
		t.Errorf("G4: ratio=0 时 span 应当不 sampled")
	}
	if !sc.IsValid() {
		t.Errorf("G4: ratio=0 时 SpanContext 应当**仍有效**（只是 sampled=false）")
	}
}

// ---------------------------------------------------------------------------
// G5 —— ParentBased：ratio=0 + **入向 sampled parent** ⇒ 子 span **仍被采样**
// Go 出处：core/trace/agent.go:110 的 ParentBased 包装
// ---------------------------------------------------------------------------
func TestLiveProbe5_ParentBasedKeepsSampledParent(t *testing.T) {
	if err := startAgent(Config{Name: "liveprobe", Endpoint: "", Sampler: 0.0, Batcher: "otlpgrpc"}); err != nil {
		t.Fatalf("G5: startAgent 失败: %v", err)
	}
	ctx := otel.GetTextMapPropagator().Extract(context.Background(), carrier(map[string]string{
		"traceparent": "00-" + probeTraceID + "-" + probeSpanID + "-01",
	}))
	_, span := otel.Tracer("liveprobe").Start(ctx, "child")
	sc := span.SpanContext()
	report(t, "G5_parentbased_sampled_parent",
		"recording="+boolStr(span.IsRecording()),
		"sampled="+boolStr(sc.IsSampled()),
		"parent_trace_id_inherited="+boolStr(sc.TraceID().String() == probeTraceID),
	)
	if !span.IsRecording() || !sc.IsSampled() {
		t.Errorf("G5: ratio=0 但入向 parent 是 sampled ⇒ 子 span 应当仍被采样")
	}
	if sc.TraceID().String() != probeTraceID {
		t.Errorf("G5: 子 span 应继承 parent 的 trace-id，实际 %s", sc.TraceID())
	}
}

// ---------------------------------------------------------------------------
// G6 —— ParentBased 的**另一半**：ratio=1.0 但入向 parent **未采样** ⇒ 子 span **不采样**
// Go 出处：core/trace/agent.go:110（ParentBased 的 remoteParentNotSampled ⇒ AlwaysOff）
// ⚠️ 这一条是本探针**挖出来**的：RAG 侧此前**没有**对应守门。
// ---------------------------------------------------------------------------
func TestLiveProbe6_ParentBasedDropsUnsampledParent(t *testing.T) {
	if err := startAgent(Config{Name: "liveprobe", Endpoint: "", Sampler: 1.0, Batcher: "otlpgrpc"}); err != nil {
		t.Fatalf("G6: startAgent 失败: %v", err)
	}
	ctx := otel.GetTextMapPropagator().Extract(context.Background(), carrier(map[string]string{
		"traceparent": "00-" + probeTraceID + "-" + probeSpanID + "-00",
	}))
	_, span := otel.Tracer("liveprobe").Start(ctx, "child")
	sc := span.SpanContext()
	report(t, "G6_parentbased_unsampled_parent",
		"recording="+boolStr(span.IsRecording()),
		"sampled="+boolStr(sc.IsSampled()),
		"sc_valid="+boolStr(sc.IsValid()),
	)
	if span.IsRecording() || sc.IsSampled() {
		t.Errorf("G6: ratio=1.0 但入向 parent 未 sampled ⇒ 子 span 应当**不**采样（ParentBased）")
	}
}

// ---------------------------------------------------------------------------
// G7 —— Go 支持的**四种** batcher 全部可构造
// Go 出处：core/trace/agent.go:60-103 + core/trace/config.go:11
// ⚠️ RAG 侧只实现了 otlpgrpc ⇒ 后三种在 RAG 侧**是能力缺口**（已在台账登记）
//    ⇒ 对拍时这三行**预期不一致**，脚本按「已知缺口」处理（不计入失败，但始终打印）。
// ---------------------------------------------------------------------------
func TestLiveProbe7_GoSupportedExporters(t *testing.T) {
	dir := t.TempDir()
	cases := []struct {
		batcher  string
		endpoint string
	}{
		{"otlpgrpc", "127.0.0.1:4317"},
		{"otlphttp", "127.0.0.1:4318"},
		{"zipkin", "http://127.0.0.1:9411/api/v2/spans"},
		{"file", filepath.Join(dir, "spans.json")},
	}
	for _, c := range cases {
		exp, err := createExporter(Config{Batcher: c.batcher, Endpoint: c.endpoint})
		report(t, "G7_exporter_"+c.batcher,
			"err_nil="+boolStr(err == nil),
			"built="+boolStr(exp != nil),
		)
		if err != nil {
			t.Errorf("G7: Go 侧合法的 batcher %q 构造失败: %v", c.batcher, err)
		}
	}
}

// ---------------------------------------------------------------------------
// G8 —— 全局传播器 = Composite(TraceContext, Baggage)
// Go 出处：core/trace/propagation.go:8-11（init 里 SetTextMapPropagator）
//           rest/handler/tracehandler.go:50-61（Extract 入向 / Inject 到**响应头**）
// ---------------------------------------------------------------------------
func TestLiveProbe8_Propagation(t *testing.T) {
	prop := otel.GetTextMapPropagator()
	ctx := prop.Extract(context.Background(), carrier(map[string]string{
		"traceparent": "00-" + probeTraceID + "-" + probeSpanID + "-01",
		"baggage":     "k=v",
	}))
	sc := trace.SpanContextFromContext(ctx)
	fields := prop.Fields()
	hasBaggage := false
	for _, f := range fields {
		if f == "baggage" {
			hasBaggage = true
		}
	}

	injectCtx, _ := otel.Tracer("liveprobe").Start(context.Background(), "inject")
	out := carrier(map[string]string{})
	prop.Inject(injectCtx, out)
	injected := out.Get("traceparent")

	report(t, "G8_propagation",
		"extracted_trace_id_matched="+boolStr(sc.TraceID().String() == probeTraceID),
		"extracted_sampled="+boolStr(sc.IsSampled()),
		"fields="+strings.Join(fields, ","),
		"fields_has_baggage="+boolStr(hasBaggage),
		"injected_traceparent_present="+boolStr(injected != ""),
	)
	if sc.TraceID().String() != probeTraceID {
		t.Errorf("G8: 传播器应能抽出 traceparent 的 trace-id，实际 %s", sc.TraceID())
	}
	if !hasBaggage {
		t.Errorf("G8: 传播器应包含 baggage")
	}
	if injected == "" {
		t.Errorf("G8: 应能注入出 traceparent")
	}
}

// ---------------------------------------------------------------------------

func boolStr(b bool) string {
	if b {
		return "true"
	}
	return "false"
}
