package com.rag.common.protection;

import com.rag.common.protection.GoStyleRouteResolution.Kind;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🔴 <b>共享层逐类契约守门 · protection 包（补最后 8 个零专属测试的类）</b>。
 *
 * <h2>它补的是什么</h2>
 * <p>本轮（2026-10-29 · R3 轮）要把「<b>73 个类逐类核过</b>」这件事做实 ⇒ ⇒ R3 判据 ⑤
 * 要求「RAG 侧有<b>自动化测试断言该行为</b>」⇒ ⇒ 这 8 个类此前<b>零专属测试</b> ⇒ ⇒ 本门补上。
 *
 * <h2>Go 出处（逐条）</h2>
 * <ul>
 *   <li>{@code GoStyleCpuReader} ← {@code go-zero@v1.10.2/core/stat/usage.go:51-53}
 *       （{@code func CpuUsage()}）＋ {@code :22}（初值 0 ⇒ <b>永不返回负数</b>）＋ {@code :35-39}（250ms 移动平均）；</li>
 *   <li>{@code GoStyleStatusCapturingResponse} ← {@code go-zero@v1.10.2/rest/internal/response/withcoderesponsewriter.go:23-26}
 *       （{@code Code: http.StatusOK}）＋ {@code rest/handler/breakerhandler.go:32-38}
 *       （{@code cw.Code < http.StatusInternalServerError}）；</li>
 *   <li>{@code GoStyleMaxBytesFilter} ← {@code go-zero@v1.10.2/rest/config.go:53}
 *       （{@code MaxBytes int64 `json:",default=1048576"`}）＋ {@code rest/engine.go:163-164}；</li>
 *   <li>{@code GoStyleRouteResolution} / {@code GoStyleRouteTemplateSource} /
 *       {@code GoStyleRouteTemplateResolver} ← <b>Go 侧无对应物</b>（路由模板解析是 Spring MVC 的表达层）⇒ <b>表达层</b>；</li>
 *   <li>{@code GoStyleCircuitOpenException} ← {@code go-zero@v1.10.2/core/breaker/googlebreaker.go}
 *       的 {@code ErrServiceOverloaded} 语义 ⇒ <b>表达层</b>（Go 侧是返回值，Java 侧是异常）；</li>
 *   <li>{@code GoStyleProtectionProperties} ← {@code go-zero@v1.10.2/rest/config.go:53}
 *       ＋ {@code core/load/adaptiveshedder.go:18-30}（常量默认值口径）。</li>
 * </ul>
 *
 * <h2>⚠️ 证据强度</h2>
 * <p><b>单测级（L1 / E3）</b>：断言 Java 侧的契约，<b>不是</b>双侧活体对拍。
 */
class ProtectionLayerContractGateTest {

    @Test
    @DisplayName("GoStyleCpuReader：整机 CPU 千分比必须在 0..1000；不可用时必须是 -1 哨兵（Go 侧永不返回负数 ⇒ 上层必须 fail-open）")
    void cpuReaderReturnsPermilleOrUnavailableSentinel() {
        int permille = GoStyleCpuReader.systemCpuPermille();

        if (permille == GoStyleCpuReader.UNAVAILABLE) {
            // Go: core/stat/usage.go:22 初值 0 ⇒ 永不返回负数 ⇒ RAG 的 -1 是**自定哨兵**
            // ⇒ 上层必须走「< 0 ⇒ 视作未过载」这一支（见 GoStyleAdaptiveShedder#systemOverloaded）
            assertThat(permille).isEqualTo(-1);
        } else {
            assertThat(permille).as("千分比口径（Go: usage.go:51-53 的 CpuUsage() × 1000）")
                    .isBetween(0, 1000);
        }
        assertThat(GoStyleCpuReader.UNAVAILABLE).as("哨兵值固定为 -1").isEqualTo(-1);
    }

    @Test
    @DisplayName("GoStyleCpuReader：进程级千分比同样只能是 0..1000 或 -1（偏离 Go 的那一档也必须有界）")
    void processCpuReaderIsBounded() {
        int permille = GoStyleCpuReader.processCpuPermille();
        // ⚠️ 不能断言「恒为 -1」：HotSpot 提供 com.sun.management.OperatingSystemMXBean ⇒ 进程 CPU 可读
        // ⇒ ⇒ 契约只有一条：**要么 -1（不可用），要么落在 0..1000**
        assertThat(permille).as("要么不可用哨兵 -1，要么千分比 0..1000")
                .isBetween(-1, 1000);
        if (permille != GoStyleCpuReader.UNAVAILABLE) {
            assertThat(permille).isBetween(0, 1000);
        }
    }

    @Test
    @DisplayName("GoStyleStatusCapturingResponse：未显式设码时必须捕获为 200（Go: withcoderesponsewriter.go:23-26 的 Code: http.StatusOK）")
    void statusCapturingResponseDefaultsTo200() {
        MockHttpServletResponse inner = new MockHttpServletResponse();
        GoStyleStatusCapturingResponse wrapper = new GoStyleStatusCapturingResponse(inner);

        assertThat(wrapper.capturedStatus())
                .as("Go 侧初值就是 http.StatusOK ⇒ 未设码等价于成功")
                .isEqualTo(HttpServletResponse.SC_OK);
    }

    @Test
    @DisplayName("GoStyleStatusCapturingResponse：显式设码后必须捕获该码，且要透传给底层（Go: cw.Code 由 setStatus 写入）")
    void statusCapturingResponseCapturesExplicitStatus() {
        MockHttpServletResponse inner = new MockHttpServletResponse();
        GoStyleStatusCapturingResponse wrapper = new GoStyleStatusCapturingResponse(inner);

        wrapper.setStatus(503);

        assertThat(wrapper.capturedStatus()).isEqualTo(503);
        assertThat(inner.getStatus()).as("必须透传给底层（否则 breaker 看到的状态码是假的）").isEqualTo(503);
    }

    @Test
    @DisplayName("GoStyleStatusCapturingResponse：sendError 也必须被捕获（Go 的 WriteHeader 路径等价物）")
    void statusCapturingResponseCapturesSendError() throws Exception {
        MockHttpServletResponse inner = new MockHttpServletResponse();
        GoStyleStatusCapturingResponse wrapper = new GoStyleStatusCapturingResponse(inner);

        wrapper.sendError(404);

        assertThat(wrapper.capturedStatus()).as("sendError 也是一次显式设码").isEqualTo(404);
    }

    @Test
    @DisplayName("GoStyleMaxBytesFilter：生效值必须等于 Go 的 RestConf.MaxBytes 默认 1 MiB（rest/config.go:53 的 default=1048576）")
    void maxBytesFilterUsesGoDefault() {
        GoStyleProtectionProperties props = new GoStyleProtectionProperties();
        GoStyleMaxBytesFilter filter = new GoStyleMaxBytesFilter(props);

        assertThat(filter.maxBytes())
                .as("Go: `MaxBytes int64 `json:\",default=1048576\"`` ⇒ 1 MiB")
                .isEqualTo(1_048_576L);
    }

    @Test
    @DisplayName("GoStyleProtectionProperties：默认值必须与 Go 的 struct tag 逐条一致（rest/config.go:12-21 · :52-56）")
    void protectionPropertiesDefaultsMatchGoStructTags() {
        GoStyleProtectionProperties props = new GoStyleProtectionProperties();

        // Go: `MiddlewaresConf{ Shedding bool `json:",default=true"`; Breaker bool `json:",default=true"` }`
        //    ⇒ ⚠️ Go 侧这两个中间件是**默认开**的 ⇒ 本实现也必须默认开
        //    （🔴 本测试首版写成「默认必须全部关闭」⇒ **错** ⇒ 已订正 ⇒ 见本轮台账 §4.18.1）
        assertThat(props.getCpuShedding().isEnabled()).as("Go: Shedding default=true ⇒ 默认开").isTrue();
        assertThat(props.getBreaker().isEnabled()).as("Go: Breaker default=true ⇒ 默认开").isTrue();

        // Go: `MaxConns int `json:",default=10000"`` / `MaxBytes int64 `json:",default=1048576"``
        assertThat(props.getMaxConns()).isEqualTo(10000);
        assertThat(props.getMaxBytes()).isEqualTo(1_048_576L);

        // Go: `CpuThreshold int64 `json:",default=900,range=[0:1000)"``
        assertThat(props.getCpuShedding().getCpuThreshold()).isEqualTo(900L);
    }

    @Test
    @DisplayName("GoStyleRouteResolution：三态必须齐备 —— TEMPLATE / NOT_MATCHED / UNKNOWN（UNKNOWN 必须回落旧行为，不得静默关掉熔断）")
    void routeResolutionHasThreeStates() {
        assertThat(GoStyleRouteResolution.template("/api/v1/users/{id}").kind())
                .isEqualTo(Kind.TEMPLATE);
        assertThat(GoStyleRouteResolution.template("/api/v1/users/{id}").template())
                .isEqualTo("/api/v1/users/{id}");

        assertThat(GoStyleRouteResolution.NOT_MATCHED.kind()).isEqualTo(Kind.NOT_MATCHED);
        assertThat(GoStyleRouteResolution.NOT_MATCHED.template())
                .as("确定未命中 ⇒ 没有模板").isNull();

        assertThat(GoStyleRouteResolution.UNKNOWN.kind()).isEqualTo(Kind.UNKNOWN);
        assertThat(GoStyleRouteResolution.UNKNOWN.template()).as("判不出来 ⇒ 没有模板").isNull();
    }

    @Test
    @DisplayName("GoStyleRouteTemplateResolver：没有 handler mapping 时必须回落 UNKNOWN（绝不能静默关掉熔断保护）")
    void routeTemplateResolverFallsBackToUnknown() {
        GoStyleRouteTemplateResolver resolver = new GoStyleRouteTemplateResolver(List.of());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/42");

        GoStyleRouteResolution resolution = resolver.resolve(request);

        assertThat(resolution.kind())
                .as("判不出来 ⇒ 调用方必须回落旧行为（按 raw URI 分桶、照常进 breaker）")
                .isEqualTo(Kind.UNKNOWN);
    }

    @Test
    @DisplayName("GoStyleRouteTemplateSource：必须是接口（断路器键的来源可替换 ⇒ 域侧可提供自己的实现）")
    void routeTemplateSourceIsAnInterface() {
        assertThat(GoStyleRouteTemplateSource.class.isInterface()).isTrue();
        assertThat(GoStyleRouteTemplateSource.class.isAssignableFrom(GoStyleRouteTemplateResolver.class))
                .as("默认实现必须实现该接口")
                .isTrue();
    }

    @Test
    @DisplayName("GoStyleCircuitOpenException：必须是 RuntimeException（Go 侧 ErrServiceOverloaded 是 error ⇒ Java 侧用异常表达）")
    void circuitOpenExceptionIsUnchecked() {
        GoStyleCircuitOpenException ex = new GoStyleCircuitOpenException("circuit open");
        assertThat(ex).isInstanceOf(RuntimeException.class);
        assertThat(ex.getMessage()).isEqualTo("circuit open");
    }
}
