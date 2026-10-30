package com.rag.common.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🔴 <b>Prometheus 暴露面的守门</b> —— 断言<b>真正被 Prometheus 抓到的文本</b>。
 *
 * <h3>为什么必须断言「抓到的文本」而不是「registry 里有个 meter」</h3>
 * <p>§3.3 的经典形态：<b>断言 Bean / meter 存在 ≠ 断言暴露面正确</b>。
 * 真正对外可见的是 Prometheus 文本格式里的<b>指标名 / label 键 / 桶边界</b>
 * ⇒ ⇒ 必须真 scrape 一次、逐行核对。
 *
 * <h3>Go 出处（指标定义逐字对照）</h3>
 * <pre>
 *   go-zero@v1.10.2/zrpc/internal/serverinterceptors/prometheusinterceptor.go:13-31
 *       Histogram: rpc_server_requests_duration_ms   label: method
 *                  buckets = 1,2,5,10,25,50,100,250,500,1000,2000,5000
 *       Counter  : rpc_server_requests_code_total    label: method + code
 * </pre>
 *
 * <h3>⚠️ 两处<b>已知且已登记</b>的暴露格式差异（不是指标语义差异）</h3>
 * <ol>
 *   <li>Micrometer 额外暴露一个 {@code rpc_server_requests_duration_ms_max} gauge
 *       （Go 的 client_golang <b>没有</b>这个 family）⇒ RAG 的暴露面比 Go <b>多一个</b> family。</li>
 *   <li>{@code le} 标签：Micrometer 写 {@code le="1.0"}，client_golang 写 {@code le="1"}
 *       ⇒ <b>桶边界数值相同</b>，只是字面量写法不同。</li>
 * </ol>
 */
@DisplayName("🔴 Prometheus 暴露面：指标名 / label / 桶边界必须与 Go 逐项一致")
class GoStylePrometheusSinkGateTest {

    private static final String METHOD = "/message.MessageService/MarkNotificationRead";

    private static PrometheusMeterRegistry newRegistry() {
        return new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    }

    private static List<String> scrapeLines(PrometheusMeterRegistry registry) {
        List<String> lines = new ArrayList<>();
        for (String line : registry.scrape().split("\n")) {
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
        return lines;
    }

    @Test
    @DisplayName("① 暴露出的指标名必须是 Go 的那两个（rpc_server_requests_duration_ms / rpc_server_requests_code_total）")
    void metricNamesMatchGo() {
        PrometheusMeterRegistry registry = newRegistry();
        MicrometerPrometheusSink sink = new MicrometerPrometheusSink(registry);

        sink.observeDurationMillis(METHOD, 7L);
        sink.incrementCode(METHOD, 0);

        List<String> lines = scrapeLines(registry);

        assertThat(lines)
                .as("Go: prometheusinterceptor.go:16 / :25 —— namespace=rpc_server, subsystem=requests, "
                        + "name=duration_ms / code_total")
                .anyMatch(l -> l.startsWith("rpc_server_requests_duration_ms_bucket{"))
                .anyMatch(l -> l.startsWith("rpc_server_requests_code_total{"))
                .noneMatch(l -> l.startsWith("rpc_server_requests_code_total_total"));
    }

    @Test
    @DisplayName("② label 键必须是 method（直方图）与 method + code（计数器）")
    void labelKeysMatchGo() {
        PrometheusMeterRegistry registry = newRegistry();
        MicrometerPrometheusSink sink = new MicrometerPrometheusSink(registry);

        sink.observeDurationMillis(METHOD, 7L);
        sink.incrementCode(METHOD, 8);

        List<String> lines = scrapeLines(registry);

        assertThat(lines)
                .as("Go: histogram 的 Labels = [\"method\"]；counter 的 Labels = [\"method\", \"code\"]")
                .anyMatch(l -> l.contains("method=\"" + METHOD + "\"")
                        && !l.contains("code=")
                        && l.startsWith("rpc_server_requests_duration_ms_"))
                .anyMatch(l -> l.contains("method=\"" + METHOD + "\"")
                        && l.contains("code=\"8\"")
                        && l.startsWith("rpc_server_requests_code_total"));
    }

    @Test
    @DisplayName("③ 桶边界必须与 Go 的 12 个逐个一致（1,2,5,10,25,50,100,250,500,1000,2000,5000）")
    void bucketBoundariesMatchGo() {
        PrometheusMeterRegistry registry = newRegistry();
        MicrometerPrometheusSink sink = new MicrometerPrometheusSink(registry);

        sink.observeDurationMillis(METHOD, 7L);

        List<Double> observed = new ArrayList<>();
        for (String line : scrapeLines(registry)) {
            if (line.startsWith("rpc_server_requests_duration_ms_bucket{")) {
                int at = line.indexOf("le=\"");
                String le = line.substring(at + 4, line.indexOf('"', at + 4));
                // ⚠️ Prometheus 的无穷桶写作 "+Inf"（不是 "Infinity"）⇒ 手工解析。
                observed.add("+Inf".equals(le) ? Double.POSITIVE_INFINITY : Double.parseDouble(le));
            }
        }

        assertThat(observed)
                .as("Go: prometheusinterceptor.go:22 —— buckets = [1 2 5 10 25 50 100 250 500 1000 2000 5000]"
                        + "（+Inf 由客户端自动追加）")
                .containsExactly(1D, 2D, 5D, 10D, 25D, 50D, 100D, 250D, 500D, 1000D, 2000D, 5000D,
                        Double.POSITIVE_INFINITY);
    }

    @Test
    @DisplayName("④ 观测值必须是【整型毫秒】（Go: .Milliseconds() 截断）⇒ 桶归属与 Go 一致")
    void observedValueIsTruncatedMilliseconds() {
        PrometheusMeterRegistry registry = newRegistry();
        MicrometerPrometheusSink sink = new MicrometerPrometheusSink(registry);

        sink.observeDurationMillis(METHOD, 0L);      // 0.9ms 截断 ⇒ 0
        sink.observeDurationMillis(METHOD, 7L);      // 7.9ms 截断 ⇒ 7

        List<String> lines = scrapeLines(registry);

        assertThat(lines)
                .as("直方图的 _sum 必须等于「截断后的毫秒之和」")
                .anyMatch(l -> l.startsWith("rpc_server_requests_duration_ms_sum{")
                        && l.endsWith(" 7.0"));
        assertThat(lines)
                .as("le=\"5.0\" 的桶必须只有 0 那一条（7 > 5 ⇒ 不进这个桶）")
                .anyMatch(l -> l.startsWith("rpc_server_requests_duration_ms_bucket{")
                        && l.contains("le=\"5.0\"")
                        && l.endsWith(" 1"));
    }

    @Test
    @DisplayName("⑤ 状态码必须是 gRPC 的【数字码】（Go: strconv.Itoa(int(status.Code(err)))）")
    void codeLabelIsTheNumericGrpcCode() {
        PrometheusMeterRegistry registry = newRegistry();
        MicrometerPrometheusSink sink = new MicrometerPrometheusSink(registry);

        sink.incrementCode(METHOD, io.grpc.Status.Code.OK.value());                    // 0
        sink.incrementCode(METHOD, io.grpc.Status.Code.RESOURCE_EXHAUSTED.value());    // 8
        sink.incrementCode(METHOD, io.grpc.Status.Code.INTERNAL.value());              // 13

        List<String> lines = scrapeLines(registry);

        assertThat(lines).anyMatch(l -> l.contains("code=\"0\"") && l.endsWith(" 1.0"));
        assertThat(lines).anyMatch(l -> l.contains("code=\"8\"") && l.endsWith(" 1.0"));
        assertThat(lines).anyMatch(l -> l.contains("code=\"13\"") && l.endsWith(" 1.0"));
    }

    @Test
    @DisplayName("⑥ Micrometer 的 meter 名刻意【不带】_total（Prometheus 客户端会自己补）")
    void micrometerMeterNameOmitsTotalSuffix() {
        assertThat(MicrometerPrometheusSink.CODE_METRIC_NAME)
                .as("⚠️ 若这里带上 _total 且将来客户端改成「无条件补后缀」⇒ 暴露名会变成 _total_total")
                .isEqualTo("rpc_server_requests_code");

        PrometheusMeterRegistry registry = newRegistry();
        MeterRegistry plain = registry;
        new MicrometerPrometheusSink(plain).incrementCode(METHOD, 0);

        assertThat(registry.scrape())
                .contains("rpc_server_requests_code_total{");
    }
}
