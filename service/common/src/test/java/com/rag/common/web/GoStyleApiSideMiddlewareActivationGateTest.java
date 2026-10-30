package com.rag.common.web;

import com.rag.common.http.GoStyleFormLimitWebMvcConfig;
import com.rag.common.http.GoStyleFormValueLimitInterceptor;
import com.rag.common.http.GoStyleJsonBodyConverterConfig;
import com.rag.common.http.GoStyleJsonContentTypeAdvice;
import com.rag.common.http.GoStylePathNormalizingFilter;
import com.rag.common.http.GoStyleTomcatParameterLimitCustomizer;
import com.rag.common.http.GoStyleTraceparentFilter;
import com.rag.common.protection.GoStyleBreakerFilter;
import com.rag.common.protection.GoStyleCpuSheddingFilter;
import com.rag.common.protection.GoStyleMaxBytesFilter;
import com.rag.common.protection.GoStyleMaxConnsFilter;
import com.rag.common.protection.GoStyleProtectionProperties;
import com.rag.common.protection.GoStyleRouteTemplateResolver;
import com.rag.common.spring.HttpMiddlewareFilter;
import com.rag.common.spring.ObservabilityProperties;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>守门：{@code service/common} 的 <b>全部 api 侧 rest 中间件</b>，<b>不会挂到 rpc 域的观测端口上</b>。</b>
 *
 * <h2>Go 出处（为什么 rpc 域不该有这些）</h2>
 * <p>Go 侧的 rpc 服务<b>全部</b>是 {@code zrpc.MustNewServer}
 * （{@code service/{article,comment,favorite,follow,hot,like,message,task}/rpc/*.go}）
 * ⇒ ⇒ <b>一个 rest 中间件都没挂</b>；{@code rest.With*} 全在 <b>api</b> 侧
 * （{@code service/<域>/api/internal/handler/routes.go}）
 * ⇒ ⇒ ⇒ <b>「rpc 域的观测端口上挂着 Go 的 api 侧中间件」= 一处真偏离</b> ⇒ ⇒ ⇒ 裁：不挂。
 *
 * <h2>🔴 2026-10-30 · 第九次订正：覆盖面从「2 个类」扩到「全部 13 个 api 侧 web Bean」</h2>
 * <p>合并收口执行者指出：上一版守门的形态④只断言了 <b>2 个</b>类
 * （{@code GoStylePathNormalizingAutoConfiguration} ＋ {@code GoStyleJsonContentTypeAdvice}），
 * 却下了「<b>api 侧中间件不注册</b>」的<b>全局结论</b>
 * ⇒ ⇒ <b>判据只覆盖了一部分、却下了全局结论 ⇒ 假绿灯</b>（与本仓反复出现的那类坑同款）
 * ⇒ ⇒ ⇒ 本版把断言扩到<b>全部 13 个</b>，并按<b>装配面</b>分成两组：
 * <table border="1">
 *   <caption>13 个 api 侧 web Bean 的两个装配面</caption>
 *   <tr><th>装配面</th><th>成员</th><th>在 rpc 域会不会活</th></tr>
 *   <tr><td><b>自动配置面</b><br>（{@code AutoConfiguration.imports} ⇒ <b>不靠扫描</b>）</td>
 *       <td>{@code GoStylePathNormalizingFilter} · {@code GoStyleJsonContentTypeAdvice} ·
 *           {@code GoStyleRouteTemplateResolver}</td>
 *       <td><b>会</b> —— 只要该进程是 servlet 形态（载体补上 HTTP 容器后即是）</td></tr>
 *   <tr><td><b>扫描面</b><br>（{@code @Component} / {@code @Configuration} ⇒ <b>靠组件扫描</b>）</td>
 *       <td>{@code GoStyleCpuSheddingFilter} · {@code GoStyleMaxConnsFilter} · {@code GoStyleMaxBytesFilter} ·
 *           {@code GoStyleBreakerFilter} · {@code GoStyleTraceparentFilter} ·
 *           {@code GoStyleTomcatParameterLimitCustomizer} · {@code HttpMiddlewareFilter} ·
 *           {@code GoStyleFormValueLimitInterceptor} · {@code GoStyleFormLimitWebMvcConfig} ·
 *           {@code GoStyleJsonBodyConverterConfig}</td>
 *       <td>此前<b>不会</b> —— 但依据是<b>域侧约定</b>（各 rpc 域的 {@code @SpringBootApplication}
 *           刻意不扫 {@code com.rag.common}）<b>，不是这些类自己的条件</b></td></tr>
 * </table>
 *
 * <h3>⇒ ⇒ 两条结论（本轮的实质）</h3>
 * <ol>
 *   <li><b>自动配置面原来漏了 1 个</b>：{@code GoStyleBreakerRouteTemplateAutoConfiguration}
 *       （它产出 {@code GoStyleRouteTemplateResolver}）<b>不在</b> {@code rag.side=api} 的覆盖里
 *       ⇒ ⇒ 它在<b>任何</b> servlet 形态的进程里都会被自动配置激活 ⇒ ⇒ <b>已补上限定</b>；</li>
 *   <li><b>扫描面那 10 个此前毫无自保</b>：它们靠域侧不扫描才没活
 *       ⇒ ⇒ <b>一旦某个 rpc 域把 {@code com.rag.common} 加进 {@code scanBasePackages}，它们会全部活起来</b>
 *       ⇒ ⇒ 而 {@code rag.side=rpc} <b>救不了它们</b>（它们不看那个属性）
 *       ⇒ ⇒ ⇒ <b>护栏已从「域侧自觉」收回「共享层自己兜住」</b>（13 个类全部挂
 *       {@link ConditionalOnApiSide}）。</li>
 * </ol>
 *
 * <h2>形态矩阵（全部<b>真起进程</b> —— Boot 4 的 {@code ApplicationContextRunner} 不支持 web type）</h2>
 * <table border="1">
 *   <caption>五形态（合成应用只扫自己的包 ⇒ 与各 rpc 域同形：不扫 {@code com.rag.common}）</caption>
 *   <tr><th>形态</th><th>主 context</th><th>扫描面 10 个是否被显式 {@code @Import}</th>
 *       <th>{@code rag.side}</th><th>自动配置面 3 个</th><th>扫描面 10 个</th></tr>
 *   <tr><td>①</td><td>non-web（rpc 现状）</td><td>否</td><td>—</td><td>全不在</td><td>全不在</td></tr>
 *   <tr><td>②</td><td>servlet</td><td>否</td><td>未设</td><td><b>全在</b></td><td>全不在</td></tr>
 *   <tr><td>③</td><td>servlet</td><td><b>是</b></td><td>未设</td><td><b>全在</b></td><td><b>全在</b></td></tr>
 *   <tr><td>④</td><td>servlet</td><td>否</td><td><b>rpc</b></td><td>全不在</td><td>全不在</td></tr>
 *   <tr><td>⑤</td><td>servlet</td><td><b>是</b></td><td><b>rpc</b></td><td>全不在</td><td><b>全不在</b></td></tr>
 * </table>
 *
 * <h3>⚠️ 为什么要有形态③ 与形态⑤（判据自检：差异必须被隔离到一个变量）</h3>
 * <ul>
 *   <li><b>形态② ⇒ 形态③</b>：只差「是否显式 {@code @Import} 扫描面那 10 个类」
 *       ⇒ ⇒ 形态③「全在」证明这 10 个类<b>在 servlet 形态下是装得上的</b>
 *       ⇒ ⇒ ⇒ 形态④/⑤ 的「全不在」<b>不是「类装不上」的假象</b>；</li>
 *   <li><b>形态③ ⇒ 形态⑤</b>：<b>只差 {@code rag.side} 一个变量</b>
 *       ⇒ ⇒ 形态⑤「扫描面全不在」证明 <b>{@code rag.side=rpc} 真的挡住了扫描面</b>
 *       ⇒ ⇒ ⇒ 这就是「护栏已从域侧约定收回共享层」的<b>运行证据</b>
 *       ⇒ ⇒ ⇒ 同时也是 <b>{@link ConditionalOnApiSide} 这个元注解真生效</b>的证据
 *       （AGENTS.md §3.3：注解存在 ≠ 机制生效 ⇒ 必须有运行证据）。</li>
 * </ul>
 *
 * <h2>⚠️ 诚实边界</h2>
 * <ul>
 *   <li>本守门用<b>合成应用</b>复刻 rpc 域形态 ⇒ ⇒ <b>不覆盖</b>「某个 rpc 域真的把
 *       {@code com.rag.common} 加进了 {@code scanBasePackages}」这一情形下的<b>逐类行为</b>
 *       ⇒ ⇒ 但那种情形下 {@code rag.side=rpc} <b>仍会挡住全部 13 个</b>（形态⑤ 即其等价形态）
 *       ⇒ ⇒ ⇒ <b>护栏不再依赖域侧自觉</b>；</li>
 *   <li>形态③/⑤ 用<b>显式 {@code @Import}</b> 代表「扫描面」 ⇒ ⇒ 它<b>等价于</b>组件扫描的效果
 *       （两者都产生同一条 Bean 定义 ⇒ 都走同一套 {@code @Conditional} 判定）;</li>
 *   <li>形态① 只覆盖「non-web 主应用」⇒ ⇒ 「用独立 management 端口承载抓取」在 non-web 主应用下
 *       <b>不成立</b>（实测 {@code management.server.port} 不生效）⇒ ⇒ 抓取出口要么让主应用变 servlet
 *       （⇒ ⇒ <b>必须配 {@code rag.side=rpc}</b>），要么改用别的载体形态；</li>
 *   <li>本守门只证<b>「注册与否」</b>，<b>不证</b> api 域中间件的<b>内容语义</b>（那是 §4.19 各条的独立证据）。</li>
 * </ul>
 */
class GoStyleApiSideMiddlewareActivationGateTest {

    /**
     * 「rpc 形态」的合成应用：<b>只扫自己的包</b>，不扫 {@code com.rag.common}
     * ⇒ ⇒ 与各 rpc 域的 {@code @SpringBootApplication} 同形（实测：10 个 rpc 域的
     * {@code @SpringBootApplication} 实参均为空 ⇒ 都不扫 {@code com.rag.common}）。
     */
    @SpringBootApplication(scanBasePackages = "com.rag.common.web")
    @Configuration(proxyBeanMethods = false)
    static class RpcStyleApp {
    }

    /**
     * 形态③/⑤ 的合成应用：rpc 形态 ＋ <b>显式 {@code @Import} 扫描面那 10 个类</b>
     * ⇒ ⇒ 等价于「某个 rpc 域真的把 {@code com.rag.common} 加进了扫描范围」这一情形。
     */
    @SpringBootApplication(scanBasePackages = "com.rag.common.web")
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({GoStyleProtectionProperties.class, ObservabilityProperties.class})
    @Import({
            GoStyleCpuSheddingFilter.class,
            GoStyleMaxConnsFilter.class,
            GoStyleMaxBytesFilter.class,
            GoStyleBreakerFilter.class,
            GoStyleTraceparentFilter.class,
            GoStyleTomcatParameterLimitCustomizer.class,
            HttpMiddlewareFilter.class,
            GoStyleFormValueLimitInterceptor.class,
            GoStyleFormLimitWebMvcConfig.class,
            GoStyleJsonBodyConverterConfig.class,
    })
    static class RpcStyleAppWithScanFaceImported {
    }

    /** 自动配置面 3 个 Bean（产出它们的自动配置在 {@code AutoConfiguration.imports} 里 ⇒ 不靠扫描）。 */
    private static final List<Class<?>> AUTO_CONFIG_FACE = List.of(
            GoStylePathNormalizingFilter.class,
            GoStyleJsonContentTypeAdvice.class,
            GoStyleRouteTemplateResolver.class);

    /** 扫描面 10 个 Bean（{@code @Component} / {@code @Configuration} ⇒ 靠组件扫描）。 */
    private static final List<Class<?>> SCAN_FACE = List.of(
            GoStyleCpuSheddingFilter.class,
            GoStyleMaxConnsFilter.class,
            GoStyleMaxBytesFilter.class,
            GoStyleBreakerFilter.class,
            GoStyleTraceparentFilter.class,
            GoStyleTomcatParameterLimitCustomizer.class,
            HttpMiddlewareFilter.class,
            GoStyleFormValueLimitInterceptor.class,
            GoStyleFormLimitWebMvcConfig.class,
            GoStyleJsonBodyConverterConfig.class);

    /** 全部 13 个 api 侧 web Bean（= 自动配置面 3 个 ＋ 扫描面 10 个）。 */
    private static final List<Class<?>> ALL_API_SIDE_WEB_BEANS = Stream
            .concat(AUTO_CONFIG_FACE.stream(), SCAN_FACE.stream())
            .toList();

    /** 形态①：主 context = non-web（rpc 现状）⇒ ⇒ <b>全部 13 个 api 侧 web Bean 一个都不注册</b>。 */
    @Test
    @DisplayName("形态①：non-web（rpc 现状）⇒ 全部 13 个 api 侧 web Bean 零注册")
    void nonWebMainContextDoesNotActivateAnyApiSideWebBean() {
        try (ConfigurableApplicationContext ctx = run(RpcStyleApp.class, WebApplicationType.NONE,
                Map.of("server.port", "0"))) {
            assertNonePresent(ctx, "形态①（non-web）");
            assertThat(commonFilters(ctx))
                    .as("🔴 更强的一格：<b>任何一个</b> com.rag.common 的 Filter 都不该出现在 rpc 进程里"
                            + "；实际名单 = " + commonFilters(ctx))
                    .isEmpty();
        }
    }

    /** 形态②：主 context = servlet 且<b>未</b>设 {@code rag.side} ⇒ ⇒ 自动配置面 3 个<b>会</b>注册（判据自检）。 */
    @Test
    @DisplayName("形态②：servlet 且未设 side ⇒ 自动配置面 3 个注册、扫描面 10 个不注册（判据自检）")
    void servletMainContextActivatesOnlyAutoConfigFace() {
        try (ConfigurableApplicationContext ctx = run(RpcStyleApp.class, WebApplicationType.SERVLET,
                Map.of("server.port", "0"))) {
            assertAllPresent(ctx, AUTO_CONFIG_FACE, "形态②（servlet · 未设 side）· 自动配置面");
            assertNonePresent(ctx, SCAN_FACE, "形态②（servlet · 未设 side）· 扫描面（未扫描 ⇒ 不注册）");
        }
    }

    /**
     * 形态③：servlet ＋ <b>显式 {@code @Import} 扫描面 10 个类</b> ＋ <b>未</b>设 {@code rag.side}
     * ⇒ ⇒ <b>13 个全在</b>。
     */
    @Test
    @DisplayName("形态③：servlet ＋ 显式 Import 扫描面 10 类 ＋ 未设 side ⇒ 13 个全注册（判据自检）")
    void servletWithScanFaceImportedActivatesAllApiSideWebBeans() {
        try (ConfigurableApplicationContext ctx = run(RpcStyleAppWithScanFaceImported.class,
                WebApplicationType.SERVLET, Map.of("server.port", "0"))) {
            assertAllPresent(ctx, ALL_API_SIDE_WEB_BEANS, "形态③（servlet ＋ Import 扫描面 · 未设 side）");
        }
    }

    /**
     * 形态④：servlet ＋ {@code rag.side=rpc} ⇒ ⇒ <b>全部 13 个</b> api 侧 web Bean <b>都不注册</b>。
     *
     * <p>⚠️ <b>这一格是本版扩大的那一格</b>：上一版只断言了 2 个类 ⇒ ⇒ 「api 侧中间件不注册」
     * 这个全局结论<b>超出</b>判据覆盖面 ⇒ ⇒ 现在<b>逐个</b>断言 13 个。
     */
    @Test
    @DisplayName("形态④：servlet ＋ rag.side=rpc ⇒ 全部 13 个 api 侧 web Bean 零注册（= 与 Go 同形）")
    void rpcSideOptOutDisablesEveryApiSideWebBean() {
        try (ConfigurableApplicationContext ctx = run(RpcStyleApp.class, WebApplicationType.SERVLET,
                Map.of("server.port", "0", "rag.side", "rpc"))) {
            assertNonePresent(ctx, "形态④（servlet ＋ rag.side=rpc）");
        }
    }

    /**
     * 形态⑤：servlet ＋ <b>显式 {@code @Import} 扫描面 10 个类</b> ＋ {@code rag.side=rpc}
     * ⇒ ⇒ <b>13 个全不在</b> ⇒ ⇒ 与形态③<b>只差 {@code rag.side} 一个变量</b>
     * ⇒ ⇒ ⇒ 证明 {@code rag.side=rpc} <b>真的挡住了扫描面</b>（护栏已从域侧约定收回共享层）。
     */
    @Test
    @DisplayName("形态⑤：servlet ＋ 显式 Import 扫描面 10 类 ＋ rag.side=rpc ⇒ 13 个全不注册"
            + "（与形态③只差 rag.side 一个变量 ⇒ 证明 rag.side 挡住了扫描面）")
    void ragSideRpcBlocksScanFaceToo() {
        try (ConfigurableApplicationContext ctx = run(RpcStyleAppWithScanFaceImported.class,
                WebApplicationType.SERVLET, Map.of("server.port", "0", "rag.side", "rpc"))) {
            assertNonePresent(ctx, "形态⑤（servlet ＋ Import 扫描面 ＋ rag.side=rpc）");
        }
    }

    // ───────────────────────────── 断言辅助 ─────────────────────────────

    /** 断言 13 个 api 侧 web Bean <b>全部不在</b>容器里。 */
    private static void assertNonePresent(ConfigurableApplicationContext ctx, String shape) {
        assertNonePresent(ctx, ALL_API_SIDE_WEB_BEANS, shape);
    }

    /** 断言给定这批 Bean <b>全部不在</b>容器里。 */
    private static void assertNonePresent(ConfigurableApplicationContext ctx, List<Class<?>> types, String shape) {
        for (Class<?> type : types) {
            assertThat(beanNames(ctx, type))
                    .as("🔴 " + shape + " ⇒ 「" + type.getSimpleName() + "」必须<b>不</b>注册"
                            + "（Go 的 rpc 服务是 zrpc.MustNewServer ⇒ 零 rest 中间件 ⇒ 挂了就是偏离）")
                    .isEmpty();
        }
    }

    /** 断言给定这批 Bean <b>全部在</b>容器里。 */
    private static void assertAllPresent(ConfigurableApplicationContext ctx, List<Class<?>> types, String shape) {
        for (Class<?> type : types) {
            assertThat(beanNames(ctx, type))
                    .as("⚠️ " + shape + " ⇒ 「" + type.getSimpleName() + "」应当<b>在</b>"
                            + "（若它不在 ⇒ 本守门没有区分力 ⇒ 下面的「不在」结论不可信）")
                    .isNotEmpty();
        }
    }

    private static ConfigurableApplicationContext run(Class<?> app, WebApplicationType type, Map<String, Object> props) {
        SpringApplication application = new SpringApplication(app);
        application.setWebApplicationType(type);
        application.setDefaultProperties(new LinkedHashMap<>(props));
        return application.run();
    }

    private static List<String> beanNames(ConfigurableApplicationContext ctx, Class<?> type) {
        return Arrays.stream(ctx.getBeanNamesForType(type)).sorted().toList();
    }

    /** 进程里所有来自 {@code com.rag.common} 的 Filter（按类包名判定 ⇒ 不依赖 Bean 名）。 */
    private static List<String> commonFilters(ConfigurableApplicationContext ctx) {
        return Arrays.stream(ctx.getBeanNamesForType(Filter.class))
                .map(ctx::getType)
                .filter(t -> t.getName().startsWith("com.rag.common."))
                .map(Class::getName)
                .sorted()
                .toList();
    }
}
