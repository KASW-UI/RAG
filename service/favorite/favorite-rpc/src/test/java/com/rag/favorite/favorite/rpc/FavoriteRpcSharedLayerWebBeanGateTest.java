package com.rag.favorite.favorite.rpc;

import com.rag.favorite.favorite.rpc.support.RealContainerHostPort;
import com.rag.favorite.favorite.rpc.support.RealGrpcPort;
import com.rag.common.http.GoStyleTomcatParameterLimitCustomizer;
import com.rag.common.protection.GoStyleBreakerFilter;
import com.rag.common.protection.GoStyleCpuSheddingFilter;
import com.rag.common.protection.GoStyleMaxBytesFilter;
import com.rag.common.protection.GoStyleMaxConnsFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 🔴🔴 <b>共享层 web 能力的「分边」守门</b>（装配层）—— 断言 <b>api 侧</b>那批 web Bean
 * <b>不在 rpc 形态的容器里</b>。
 *
 * <h2>为什么必须守</h2>
 * <p>2026-10-30 本轮给 {@code favorite-rpc} 补「指标抓取载体」⇒ pom 加了
 * {@code spring-boot-starter-web} ⇒ ⇒ <b>rpc 进程首次成为 servlet web app</b>
 * ⇒ ⇒ 共享层里那些 <b>{@code @ConditionalOnWebApplication(SERVLET)}</b> 的 Bean
 * 才有机会进容器 ⇒ ⇒ 边界必须被**运行期**钉住，不能靠 yaml 里有一行。
 * <p>⚠️ <b>其中 {@code GoStyleCpuSheddingFilter} 会拒请求</b>（CPU 降载）
 * ⇒ ⇒ 它若挂在 rpc 的观测端口上 ⇒ <b>抓取请求被拒</b> ⇒ ⇒ <b>直接威胁「载体存在的目的」</b>
 * ⇒ ⇒ 这不是「多了一个功能」，是「载体装了但抓不到」。
 * <p>Go 侧 rpc <b>一个 rest 中间件都没挂</b>（全部 {@code zrpc.MustNewServer} ⇒ 不起容器
 * ⇒ 没有 filter 链）⇒ ⇒ 边界必须是「rpc 侧 = 没有」。
 *
 * <h2>🔴 三条断言，缺一不可（否则就是空断言）</h2>
 * <ol>
 *   <li><b>属性在位</b>：生产 yml 里的 {@code rag.side} 真的进了容器的 Environment
 *       （<b>不</b>用 {@code @SpringBootTest(properties=...)} 覆盖 ⇒ 读的就是生产配置）
 *       ⇒ 「yml 里写了这一行」从此变成「运行期真的读到了这一行」；</li>
 *   <li><b>正向对照</b>：本类自带的 {@link ServletContextProbeConfiguration}
 *       （<b>只</b>带 {@code @ConditionalOnWebApplication(SERVLET)}、<b>不带</b> api 侧条件）
 *       <b>确实在容器里</b>
 *       ⇒ ⇒ 证明「servlet web app 这个前提在本上下文里成立」
 *       ⇒ ⇒ <b>否则第 ③ 条的「不在」就是空断言</b>
 *       （可能只是因为压根没有 servlet 上下文）；</li>
 *   <li><b>边界</b>：api 侧那 5 个 web Bean <b>一个都不在</b>。</li>
 * </ol>
 *
 * <h2>⚠️ 一条诚实的边界（写清楚，别误读成「门已经生效」）</h2>
 * <p><b>共享层当前还没有消费 {@code rag.side} 的条件</b>：
 * {@code grep -rn "rag.side" service/common/src/main/java/} <b>零命中</b>
 * （2026-10-30 实测）⇒ ⇒ 共享层那道门在 <b>G4</b> 侧、<b>本轮尚未落地</b>
 * ⇒ ⇒ 因此：<b>本类的第 ③ 条断言当前证明的是</b>「那 5 个 {@code @Component} 中间件
 * <b>因为 rpc 模块不扫 {@code com.rag.common}</b>（{@code @SpringBootApplication} 无
 * {@code scanBasePackages}）而不在容器里」<b>——而不是</b>「{@code rag.side} 把门挡住了」。
 * <p>⇒ ⇒ <b>为什么这条边界现在仍然是活的</b>：一旦有人给 rpc 模块加上
 * {@code scanBasePackages = "com.rag.common"} ⇒ 那 5 个 Bean 就会进容器
 * ⇒ ⇒ 第 ③ 条<b>立刻转红</b> ⇒ ⇒ 本类是一个**真会响的**边界网（不是摆设）。
 * <p>⇒ ⇒ 等 G4 把门扩到全部 api 侧中间件之后，本类**自动升级**为「{@code rag.side} 真的挡住了门」
 * 的运行期证据 ⇒ ⇒ <b>域侧现在就要把 {@code rag.side: rpc} 写进生产 yml</b>
 * （{@code matchIfMissing=true} ⇒ <b>不写 = 默认 api 侧</b> ⇒ ⇒ 那时就会挂上来）。
 *
 * <h2>Go 出处与语义等价论证</h2>
 * <p>Go 侧 rpc 侧：<b>全部</b> {@code zrpc.MustNewServer(c.RpcServerConf, …)}
 * ⇒ ⇒ <b>不起 servlet 容器</b> ⇒ ⇒ <b>没有</b> go-zero rest 那套中间件链
 * ⇒ ⇒ 「rpc 侧 = 没有 api 侧中间件」两侧<b>一致</b> ⇒ ⇒ 本类守的就是这条一致。
 */
@SpringBootTest(
        classes = FavoriteRpcApplication.class,
        // 🔴 **必须是 MOCK（不能是 NONE）** —— 本类的第 ③ 条断言是「**不在**容器里」
        //    ⇒ ⇒ 若上下文压根不是 servlet web app ⇒ 那 5 个 Bean 自然不在 ⇒ **断言空转**。
        //    ⇒ ⇒ MOCK = 「是 servlet web app，但不起真实服务器」⇒ 断言才有鉴别力。
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN",
                "grpc.client.user-rpc.address=static://127.0.0.1:1",
                "grpc.client.article-rpc.address=static://127.0.0.1:1",
                "spring.kafka.bootstrap-servers=127.0.0.1:1"
        })
@Testcontainers
@Import(ServletContextProbeConfiguration.class)
class FavoriteRpcSharedLayerWebBeanGateTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("favorite_rpc_webbean_it")
                    .withUsername("rag")
                    .withPassword("rag")
                    .withCreateContainerCmdModifier(
                            RealContainerHostPort.bindPg(RealContainerHostPort.SHARED_LAYER_WEB_BEAN_GATE));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("grpc.server.port", RealGrpcPort::pick);
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("① 属性在位：生产 yml 的 rag.side=rpc 真的进了容器 Environment（不靠 properties 覆盖）")
    void productionDeclaresRpcSide() {
        assertEquals("rpc", context.getEnvironment().getProperty("rag.side"),
                "容器的 Environment 里读不到 `rag.side=rpc` ⇒ 生产 application.yml 缺这一行 "
                        + "⇒ 共享层那道门是 `matchIfMissing=true` ⇒ **不写 = 默认 api 侧** "
                        + "⇒ rpc 域会挂上 api 侧的 web 中间件（其中 CPU 降载会拒抓取请求）。");
    }

    @Test
    @DisplayName("② 正向对照：上下文本身就是 servlet web app，且本类自带的「只带 servlet 条件」的探针确实在容器里 ⇒ 证明 servlet 前提成立")
    void servletConditionHoldsInThisContext() {
        assertThat(context)
                .as("上下文本身就是 servlet web app（WebApplicationContext）"
                        + " —— 若不是 ⇒ 第 ③ 条「api 侧 Bean 不在容器里」就是**空断言**（无鉴别力）")
                .isInstanceOf(WebApplicationContext.class);
        assertThat(context.getBeanNamesForType(ServletContextProbe.class))
                .as("正向对照：本类自带的那个**只带 servlet 条件**的探针配置应当**在**容器里"
                        + " —— 它不在 ⇒ 本上下文压根不是 servlet web app "
                        + "⇒ 下一条「api 侧 Bean 不在容器里」就是**空断言**（无鉴别力）")
                .isNotEmpty();
    }

    @Test
    @DisplayName("③ 边界：api 侧那 5 个 web Bean 一个都不在 rpc 容器里（含 Tomcat 参数上限定制器）")
    void apiSideWebBeansAreAbsent() {
        assertThat(context.getBeanNamesForType(GoStyleCpuSheddingFilter.class))
                .as("CPU 降载 filter 挂在观测端口上 ⇒ 抓取请求会被拒 ⇒ 载体装了也抓不到")
                .isEmpty();
        assertThat(context.getBeanNamesForType(GoStyleMaxConnsFilter.class)).isEmpty();
        assertThat(context.getBeanNamesForType(GoStyleMaxBytesFilter.class)).isEmpty();
        assertThat(context.getBeanNamesForType(GoStyleBreakerFilter.class)).isEmpty();
        assertThat(context.getBeanNamesForType(GoStyleTomcatParameterLimitCustomizer.class))
                .as("Tomcat 参数上限定制器是**容器级**的 ⇒ 它若生效 ⇒ rpc 域的 Tomcat 会被抬到 10001 "
                        + "⇒ ⇒ hot 域「段数轴已销账」那条结论会从绿变红 ⇒ ⇒ 必须钉住")
                .isEmpty();
    }
}

/**
 * 🔴 <b>测试自带探针</b>：<b>只</b>带 {@code @ConditionalOnWebApplication(SERVLET)}、
 * <b>不带</b> api 侧条件 ⇒ 它在容器里 ⟺ 本上下文**确实是 servlet web app**。
 *
 * <p>⚠️ <b>为什么不用共享层里的某个 Bean 当参照物（2026-10-31 订正）</b>：
 * 共享层 {@code 53bc05ec}（G4 lane，2026-10-30）把一批 web Bean 从「只有 servlet 条件」
 * 改成了「<b>servlet 条件 + api 侧条件</b>」（{@code ConditionalOnApiSide}）
 * ⇒ ⇒ 共享层里**已经找不到**「只带 servlet 条件」的参照物了
 * ⇒ ⇒ 本守门原先拿 {@code GoStyleJsonContentTypeAdvice} 当正向对照
 * ⇒ ⇒ 它在 2026-10-31 的回归里**转红**（它现在是 api 侧 Bean，在 rpc 容器里本就不该在）
 * ⇒ ⇒ 改用**本类自带探针** ⇒ 判据不依赖共享层某个具体 Bean 的条件组合 ⇒ 更稳。
 *
 * <p>⚠️ <b>为什么放在「文件顶层（包私有）」而不是「类的嵌套类」</b>（2026-10-31 实测）：
 * ① <b>注册面</b>：探针原先做成嵌套 {@code @TestConfiguration} ⇒ 实测<b>根本没被自动注册</b>
 *    （2026-10-31 探针始终不在容器里 ⇒ 而上下文本身确实是 {@code GenericWebApplicationContext}、
 *    {@code ApplicationServletEnvironment}、{@code session} scope 已注册 ⇒ <b>条件是匹配的</b>
 *    ⇒ ⇒ 结论是「<b>注册</b>没发生」，不是「<b>条件</b>没匹配」）
 *    ⇒ 改成文件顶层 {@code @Configuration} ＋ 测试类上显式 {@code @Import} ⇒ 注册不依赖任何自动探测机制；
 * ② <b>编译面</b>：{@code @Import(Xxx.class)} 写在<b>类自身的注解</b>上时，javac 按 JLS 作用域规则
 *    <b>解析不到本类的嵌套成员类型</b>（嵌套类型的作用域是「类体内部」，而类声明上的注解在类体之外）
 *    ⇒ ⇒ 编译报「找不到符号」⇒ ⇒ 搬到文件顶层（包私有）⇒ 包作用域可见 ⇒ {@code @Import} 可解析。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class ServletContextProbeConfiguration {

    @Bean
    ServletContextProbe probe() {
        return new ServletContextProbe();
    }
}

/** 探针本体（只作类型标记，断言「它在不在容器里」）。 */
final class ServletContextProbe {
}
