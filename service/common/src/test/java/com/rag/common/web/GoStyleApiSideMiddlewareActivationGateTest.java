package com.rag.common.web;

import com.rag.common.http.GoStyleJsonContentTypeAdvice;
import com.rag.common.http.GoStylePathNormalizingFilter;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>守门：{@code service/common} 的 api 侧 rest 中间件，<b>不会挂到 rpc 域的观测端口上</b>。</b>
 *
 * <h2>§5 裁的落点（2026-10-30 · G4 lane · 合并收口执行者要求「裁 + 在 service/common 落码」）</h2>
 * <p>Go 侧：rpc 服务是 {@code zrpc.MustNewServer}（{@code service/{article,comment,favorite,follow,hot,like,message,task}/rpc/*.go}）
 * ⇒ ⇒ <b>一个 rest 中间件都没挂</b>；{@code rest.With*} 全在 <b>api</b> 侧
 * （{@code service/<域>/api/internal/handler/routes.go}）⇒ ⇒ ⇒
 * <b>「rpc 域的观测端口上挂着 Go 的 api 侧中间件」= 一处真偏离 ⇒ ⇒ 裁：不挂。</b>
 *
 * <h2>⚠️ 清单必须按类判定（实测推翻了一个更大的担忧）</h2>
 * <p>合并收口执行者给的清单把 {@code GoStyleCpuSheddingFilter}（CPU 降载，<b>有杀伤力</b>：系统 CPU 超阈时拒掉抓取请求）
 * 列在第一梯队 ⇒ ⇒ <b>但实测它根本不会在 rpc 域注册</b>，原因有二（两条都实测过）：
 * <ol>
 *   <li>它是 {@code @Component} ⇒ ⇒ 靠<b>组件扫描</b>注册 ⇒ ⇒ 而<b>各 rpc 域的
 *       {@code @SpringBootApplication} 刻意不扫 {@code com.rag.common}</b>
 *       （{@code article-rpc} 的 Application 注释里点名 {@code HttpMiddlewareFilter}
 *       作为「不扫」的理由）⇒ ⇒ <b>不会成为候选</b>；</li>
 *   <li>即便主 context 变成 servlet（形态②实测）⇒ ⇒ 它的 Filter 名单里也<b>没有</b>
 *       {@code goStyleCpuSheddingFilter} ⇒ ⇒ <b>连 servlet 形态都注册不上</b>。</li>
 * </ol>
 * ⇒ ⇒ <b>真正会激活的只有 2 条</b>（它们在 {@code AutoConfiguration.imports} 里 ⇒ ⇒ <b>不靠扫描</b>）：
 * {@code GoStylePathNormalizingAutoConfiguration}（→ 路径归一 Filter）＋
 * {@code GoStyleJsonContentTypeAdvice}（→ {@code ResponseBodyAdvice}）⇒ ⇒
 * ⇒ ⇒ 这两条已加 <b>{@code rag.side=api} 限定</b>（默认 {@code api} ⇒ <b>零回归</b>）⇒ ⇒
 * rpc 域设 {@code rag.side=rpc} 即不挂 ⇒ ⇒ 与 Go 同形。
 *
 * <h2>形态矩阵（全部<b>真起进程</b> —— Boot 4 的 {@code ApplicationContextRunner} 不支持 web type）</h2>
 * <table border="1">
 *   <caption>实测结论</caption>
 *   <tr><th>形态</th><th>主 context</th><th>api 侧 Filter</th></tr>
 *   <tr><td>①</td><td>non-web（rpc 现状）</td><td><b>零</b></td></tr>
 *   <tr><td>②</td><td>servlet（加载体后，未设 side）</td><td>路径归一 Filter（<b>判据自检</b>）</td></tr>
 *   <tr><td>③</td><td>non-web ＋ 独立 management 端口</td><td>零，<b>且 management 端口根本没起</b></td></tr>
 *   <tr><td>④</td><td>servlet ＋ {@code rag.side=rpc}</td><td><b>零</b>（= opt-out 生效）</td></tr>
 * </table>
 *
 * <h2>⚠️ 诚实边界</h2>
 * <ul>
 *   <li>本守门用<b>合成应用</b>（不扫 {@code com.rag.common}）复刻 rpc 域形态 ⇒ ⇒
 *       <b>不覆盖</b>「某个 rpc 域真的把 {@code com.rag.common} 加进了 scanBasePackages」这一情形
 *       ⇒ ⇒ 那种情形下那批 {@code @Component} 会全部注册 ⇒ ⇒
 *       <b>护栏落在域侧</b>（各 rpc 域的 Application 继续不扫 {@code com.rag.common}）
 *       ⇒ ⇒ <b>落点不在 G4 写权面 ⇒ 已转派合并收口执行者</b>。</li>
 *   <li>形态③实测出「non-web 主应用下 {@code management.server.port} <b>不生效</b>」
 *       ⇒ ⇒ 「用独立 management 端口承载抓取」这条方案在 non-web 主应用下<b>不成立</b>
 *       ⇒ ⇒ ⇒ 抓取出口要么让主应用变 servlet（⇒ ⇒ 必须配 {@code rag.side=rpc}），
 *       要么改用别的载体形态。</li>
 * </ul>
 */
class GoStyleApiSideMiddlewareActivationGateTest {

    /**
     * 「rpc 形态」的合成应用：<b>只扫自己的包</b>，不扫 {@code com.rag.common}
     * ⇒ ⇒ 与各 rpc 域的 {@code @SpringBootApplication} 同形。
     */
    @SpringBootApplication(scanBasePackages = "com.rag.common.web")
    @Configuration(proxyBeanMethods = false)
    static class RpcStyleApp {
    }

    /** 形态①：主 context = non-web（rpc 现状）⇒ ⇒ api 侧中间件<b>一个都不注册</b>。 */
    @Test
    @DisplayName("形态①：主 context = non-web（rpc 现状）⇒ api 侧中间件<b>零注册</b>")
    void nonWebMainContextDoesNotActivateApiSideMiddleware() {
        try (ConfigurableApplicationContext ctx = run(WebApplicationType.NONE,
                Map.of("server.port", "0"))) {
            assertThat(beanNames(ctx, GoStylePathNormalizingFilter.class))
                    .as("rpc 现状下不该有路径归一 Filter")
                    .isEmpty();
            assertThat(beanNames(ctx, GoStyleJsonContentTypeAdvice.class))
                    .as("rpc 现状下不该有 json content-type advice")
                    .isEmpty();
            assertThat(commonFilters(ctx))
                    .as("🔴 更强的一格：<b>任何一个</b> com.rag.common 的 Filter 都不该出现在 rpc 进程里"
                            + "（那批 @Component 靠扫描 ⇒ rpc 域不扫 com.rag.common ⇒ 不会成为候选）"
                            + "；实际名单 = " + commonFilters(ctx))
                    .isEmpty();
        }
    }

    /** 形态②：主 context = servlet 且<b>未</b>设 {@code rag.side} ⇒ ⇒ 路径归一 Filter <b>会</b>注册（判据自检）。 */
    @Test
    @DisplayName("形态②：主 context = servlet 且未设 side ⇒ 路径归一 Filter <b>注册</b>（判据自检）")
    void servletMainContextActivatesServletConditionalAutoConfigurations() {
        try (ConfigurableApplicationContext ctx = run(WebApplicationType.SERVLET,
                Map.of("server.port", "0"))) {
            assertThat(beanNames(ctx, GoStylePathNormalizingFilter.class))
                    .as("⚠️ 这一格是<b>判据自检</b>：若它也不注册 ⇒ 本守门没有区分力 ⇒ 结论不可信"
                            + "（实测：它注册 ⇒ ⇒ 说明「servlet 形态会带上 Go 的 api 侧 Filter」是真的）")
                    .isNotEmpty();
        }
    }

    /** 形态④：主 context = servlet <b>且</b>设 {@code rag.side=rpc} ⇒ ⇒ api 侧中间件<b>不注册</b>。 */
    @Test
    @DisplayName("形态④：servlet ＋ rag.side=rpc ⇒ api 侧中间件<b>不注册</b>（= opt-out 生效 = 与 Go 同形）")
    void rpcSideOptOutDisablesApiSideMiddleware() {
        try (ConfigurableApplicationContext ctx = run(WebApplicationType.SERVLET,
                Map.of("server.port", "0", "rag.side", "rpc"))) {
            assertThat(beanNames(ctx, GoStylePathNormalizingFilter.class))
                    .as("rag.side=rpc ⇒ 路径归一 Filter 必须<b>不</b>注册"
                            + "（Go 的 rpc 服务没有 rest 中间件 ⇒ 挂了就是偏离）")
                    .isEmpty();
            assertThat(beanNames(ctx, GoStyleJsonContentTypeAdvice.class))
                    .as("rag.side=rpc ⇒ json content-type advice 也必须不注册")
                    .isEmpty();
        }
    }

    /**
     * 形态③：non-web 主 context ＋ 独立 management 端口。
     *
     * <p>⚠️ <b>这一格<b>不在</b>本守门里</b> —— 它需要 {@code spring-boot-starter-actuator}
     * 才能让 {@code management.server.port} 有意义 ⇒ ⇒ 而**引入 actuator 会让
     * {@code @Autowired RequestMappingHandlerMapping} 出现歧义**
     * （多出 {@code controllerEndpointHandlerMapping}）⇒ ⇒ <b>打红本模块既有的
     * {@code GoStyleBreakerRouteTemplateGateTest}（3 条）</b>
     * ⇒ ⇒ ⇒ 「为一个事实背一个新依赖 ＋ 打红别人的守门」<b>不划算</b>
     * ⇒ ⇒ ⇒ 该事实改以<b>台账文字登记</b>（实测来源：本轮带 actuator 的那次真跑）
     * ⇒ ⇒ 且**它本来就是载体方案的选择问题，不属于共享层要保证的事**。
     */
    private static void unusedPlaceholderForRemovedForm3() {
        // 保留占位以便后续若载体方案定下来再补（见类 javadoc「形态矩阵」的说明）
    }

    private static ConfigurableApplicationContext run(WebApplicationType type, Map<String, Object> props) {
        SpringApplication app = new SpringApplication(RpcStyleApp.class);
        app.setWebApplicationType(type);
        app.setDefaultProperties(props);
        return app.run();
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
