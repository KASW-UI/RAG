package com.rag.users.admin.api;

import com.rag.users.admin.api.client.AdminRpcClient;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 🟡 UNRESOLVED-005 ② 守门 —— Micrometer counter 名在 {@code /actuator/prometheus} 上的
 * **最终形态**，以及它与 Go 侧是否同名。
 *
 * <h2>要判定什么</h2>
 * <pre>
 *   Go  service/user/admin/api/internal/metrics/metrics.go:9-14
 *       prometheus.NewCounterVec(prometheus.CounterOpts{
 *           Namespace: "", Subsystem: "admin_api", Name: "intercept_total",
 *       }, []string{"path", "result"})
 *   ⇒ Prometheus 全名 = admin_api_intercept_total
 *
 *   RAG service/users/admin/admin-api/.../metrics/AdminApiMetrics.java:111
 *       Counter.builder("admin_api_intercept_total") ...
 * </pre>
 *
 * <p>两侧写的字符串<b>看起来</b>一样，但 <b>Micrometer 有一层归一化规则</b>：
 * Prometheus 的约定是「counter 自动带 {@code _total} 后缀」，
 * 而 Micrometer 的 {@code PrometheusNamingConvention} 对 counter 会
 * <b>先把已有的 {@code _total} 后缀去掉</b>，输出时再补回去
 * ⇒ 净效果应当仍是 {@code admin_api_intercept_total}。
 *
 * <p><b>但这是推断，必须实测</b>（AGENTS.md §3.3）：
 * 归一化规则会随 Micrometer 版本、以及是否配置了自定义
 * {@code NamingConvention} 而变 ⇒ 一旦变了，最终名字会变成
 * {@code admin_api_intercept_total_total} 或 {@code admin_api_intercept}，
 * <b>而这两种都会让 Prometheus 侧的抓取/告警规则失配</b>。
 *
 * <h2>为什么用 MockMvc 打 actuator 而不是真 Tomcat</h2>
 * <p>{@code /actuator/prometheus} 的输出内容由 {@code MeterRegistry} 生成，
 * <b>与 Servlet 容器无关</b> ⇒ MockMvc 与真实 Tomcat 的输出<b>逐字相同</b>。
 * （这与 {@code /actuator/prometheus} 那种「容器会重渲染响应头」的情况不同 ——
 * 响应头会被 Tomcat 重渲染，<b>响应体不会</b>。）
 */
@SpringBootTest(
        classes = AdminApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "grpc.client.admin-rpc.address=static://127.0.0.1:1",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
@AutoConfigureMockMvc
class AdminApiPrometheusMetricNameTest {

    /** Go 侧 Prometheus 全名（Namespace="" + Subsystem="admin_api" + Name="intercept_total"）。 */
    private static final String GO_METRIC_NAME = "admin_api_intercept_total";

    @MockitoBean
    private AdminRpcClient adminRpcClient;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MeterRegistry meterRegistry;

    /**
     * Micrometer 输出里的 metric 行形如：
     * <pre>
     * admin_api_intercept_total{application="admin-api",path="/admin/login",result="rpc_error"} 1.0
     * </pre>
     * ⇒ 抓「行首的 metric 名」这一段即可。
     */
    private static final Pattern METRIC_LINE =
            Pattern.compile("^([a-zA-Z_:][a-zA-Z0-9_:]*)(\\{.*)?\\s+[0-9.eE+-]+$", Pattern.MULTILINE);

    @Test
    @DisplayName("✅ /actuator/prometheus 上 counter 名恰为 admin_api_intercept_total（与 Go 同名）")
    void prometheusExposesCounterWithSameNameAsGo() throws Exception {
        // ① 先制造一次真实计数：Micrometer 只输出**有观测值**的 meter，
        //    零计数的 counter 根本不会出现在 prometheus 输出里。
        mockMvc.perform(post("/admin/v1/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nobody\",\"password\":\"x\"}"))
                .andReturn();

        // ② 抓 prometheus 输出
        String body = mockMvc.perform(get("/actuator/prometheus"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // ③ 收集输出里出现过的所有 metric 名
        java.util.Set<String> names = new java.util.TreeSet<>();
        Matcher m = METRIC_LINE.matcher(body);
        while (m.find()) {
            names.add(m.group(1));
        }

        assertFalse(names.isEmpty(),
                "/actuator/prometheus 没有输出任何 metric —— 说明 endpoint 没开或没数据，"
                        + "本测试的判定前提不成立");

        assertTrue(names.contains(GO_METRIC_NAME),
                "prometheus 输出里**没有** " + GO_METRIC_NAME + "，实际出现的 metric 名 = " + names
                        + "。⚠️ 若出现的是 " + GO_METRIC_NAME + "_total，说明 Micrometer 的归一化规则变了；"
                        + "若是 " + GO_METRIC_NAME.replace("_total", "") + "，说明它被去掉了后缀 —— "
                        + "两种都会让 Prometheus 侧的抓取/告警规则失配。");

        // ⚠️ 负向断言：**不得**出现 Micrometer 双重加后缀的形态。
        assertFalse(names.contains(GO_METRIC_NAME + "_total"),
                "出现了 " + GO_METRIC_NAME + "_total ⇒ Micrometer 的 _total 归一化规则与预期不符");

        // ④ 与 Go 侧的 tag 集合对照：Go 是 {path, result}
        //    （client_golang 的 CounterVec 不会自动加额外 label）
        String counterLine = body.lines()
                .filter(l -> l.startsWith(GO_METRIC_NAME + "{"))
                .findFirst()
                .orElse(null);
        assertTrue(counterLine != null, "找不到 " + GO_METRIC_NAME + " 的样本行");

        // RAG 侧多一个 application tag（Spring Boot 默认注入）⇒ 只断言 Go 那两个 tag 在
        assertTrue(counterLine.contains("path=\""), "样本行缺 path label：" + counterLine);
        assertTrue(counterLine.contains("result=\""), "样本行缺 result label：" + counterLine);
    }

    @Test
    @DisplayName("✅ registry 里登记的 meter 名与 Go 同名（不依赖 prometheus 归一化）")
    void meterRegistryNameMatchesGo() {
        boolean found = meterRegistry.getMeters().stream()
                .anyMatch(m -> GO_METRIC_NAME.equals(m.getId().getName()));
        assertTrue(found,
                "MeterRegistry 里没有名为 " + GO_METRIC_NAME + " 的 meter，实际 = "
                        + meterRegistry.getMeters().stream()
                                .map(m -> m.getId().getName()).distinct().sorted().toList());
    }
}
