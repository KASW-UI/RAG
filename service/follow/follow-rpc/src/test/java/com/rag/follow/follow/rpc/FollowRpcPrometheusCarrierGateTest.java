package com.rag.follow.follow.rpc;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.rag.common.http.GoStyleJsonContentTypeAdvice;
import com.rag.common.http.GoStylePathNormalizingAutoConfiguration;
import com.rag.common.protection.GoStyleBreakerRouteTemplateAutoConfiguration;
import com.rag.common.http.GoStyleFormLimitWebMvcConfig;
import com.rag.common.http.GoStyleFormValueLimitInterceptor;
import com.rag.common.http.GoStyleJsonBodyConverterConfig;
import com.rag.common.http.GoStyleTomcatParameterLimitCustomizer;
import com.rag.common.http.GoStyleTraceparentFilter;
import com.rag.common.protection.GoStyleBreakerFilter;
import com.rag.common.protection.GoStyleCpuSheddingFilter;
import com.rag.common.protection.GoStyleMaxBytesFilter;
import com.rag.common.protection.GoStyleMaxConnsFilter;
import com.rag.common.spring.HttpMiddlewareFilter;
import com.rag.follow.follow.rpc.metrics.FollowRpcMetrics;
import com.rag.follow.follow.rpc.support.RealTomcatPort;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🔴 <b>载体守门</b>：follow-rpc 的 {@code /actuator/prometheus} <b>真能被外部抓到</b>
 * （2026-10-31 · G1 lane · 指标抓取出口补载体 · <b>补 follow-rpc 这一条</b>）。
 *
 * <h2>为什么这条守门比新增 user-rpc / admin-rpc 的两条更该先有</h2>
 * <p>follow-rpc 是<b>本域唯一早就存在载体</b>的 rpc 模块
 * （{@code spring-boot-starter-web} 于 DRIFT-FOLLOW-006 · 2026-10-29 落地，
 * 端口 {@code 16065} = Go 侧 DevServer 端口一字不差）⇒ ⇒ 但它<b>此前零守门</b>
 * ⇒ ⇒ 「载体哪天掉了 / 暴露面被谁改坏 / 路径不再是抓取口」都<b>没人拦得住</b>
 * ⇒ ⇒ <b>唯一已存在的载体却没有网</b> ⇒ ⇒ 本类补的就是这张网。
 *
 * <h2>本类验的是什么（机制面，不是业务面）</h2>
 * <ol>
 *   <li><b>载体存在</b>：进程里<b>真的有</b> Tomcat（{@code spring-boot-starter-web}）
 *       ⇒ 否则 {@code WebApplicationType} 推导为 {@code NONE}
 *       ⇒ {@code management.endpoints.web.*} 那一族配置<b>永远不加载</b>
 *       （<b>不报错，是静默无效</b> —— AGENTS.md §3.3 的典型形态）；</li>
 *   <li><b>暴露面开着</b>：{@code management.endpoints.web.exposure.include} 含
 *       {@code prometheus} ＋ {@code endpoint.prometheus.enabled: true}；</li>
 *   <li><b>端点真的回主端口</b>：域侧 yml <b>不设</b> {@code management.server.port}
 *       ⇒ Boot 4 判为 {@code ManagementPortType.SAME} ⇒ 端点挂主 web 端口
 *       （<b>若写 -1 ⇒ 判为 {@code DISABLED} ⇒ 端点彻底不上线</b>）；</li>
 *   <li><b>导出的指标与 Go 逐条同形</b>：Go
 *       {@code service/follow/rpc/internal/metrics/metrics.go:11-43}
 *       5 个 {@code follow_rpc_*} meter ⇒ ⇒ RAG 侧必须<b>同名同标签</b>。</li>
 * </ol>
 *
 * <h2>⚠️ 为什么用<b>独立最小上下文</b>而不是 {@code FollowRpcApplication}</h2>
 * <p>业务启动类会拉起建表 bootstrap（真连 PG）⇒ ⇒ 载体守门会被<b>数据库可用性</b>绑架
 * ⇒ ⇒ 「载体有没有」这个问题就<b>测不到</b>（起不来 ⇒ 不知道是载体缺还是 PG 缺）。
 * <p>⇒ 故本类只装<b>被测机制所需的最小件</b>：actuator ＋ web ＋ 域侧
 * {@link FollowRpcMetrics}（真生产类，不是复制品）⇒ ⇒ 测的正是「载体 + 暴露面 + 指标定义」
 * 这三件事本身。
 * <p>⇒ ⚠️ <b>诚实边界</b>：本类<b>不覆盖</b>「业务启动类在真 PG/Redis 下真起一遍」
 * ⇒ ⇒ 那属 L4 活体对拍（需 Docker），不在本类范围。
 *
 * <h2>⚠️ 配置来源</h2>
 * <p>本类<b>不自带</b> {@code management} 配置 ⇒ ⇒ 它<b>读域侧真实
 * {@code application.yml}</b>（{@code @SpringBootTest} 默认加载同包 classpath 上的
 * {@code application.yml}）⇒ ⇒ <b>改坏了配置这条测试就红</b> ⇒ ⇒
 * 断言的是「<b>真实配置</b>真生效」，不是「测试自己配的那份生效」。
 *
 * <h2>Go 出处</h2>
 * <ul>
 *   <li>{@code service/follow/rpc/etc/follow.yaml:20-23} ——
 *       {@code DevServer: {Enabled: true, Port: 16065, MetricsPath: /metrics,
 *       EnableMetrics: true}}；</li>
 *   <li>{@code service/follow/rpc/internal/metrics/metrics.go:11-43} ——
 *       5 个 meter（{@code Subsystem:"follow_rpc"}，{@code Namespace} 未设 ⇒ 空）：
 *       {@code request_total}{[method,result]} ／ {@code request_duration_seconds}{[method]}
 *       ／ {@code relation_action_total}{[action,result]} ／ {@code db_error_total}{[action,type]}
 *       ／ {@code recommendation_size}（无 label）；</li>
 *   <li>{@code go-zero@v1.10.2/internal/devserver/server.go:44-49} ——
 *       {@code if s.config.EnableMetrics { prometheus.Enable();
 *       s.handleFunc(s.config.MetricsPath, promhttp.Handler().ServeHTTP) }}。</li>
 * </ul>
 */
@SpringBootTest(
        classes = FollowRpcPrometheusCarrierGateTest.MinimalCarrierApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {
                // ⚠️ 本测试用 DEFINED_PORT ⇒ **真起 web server** ⇒
                //    若开了 Nacos 自动注册，它会在 web server 启动阶段去注册
                //    ⇒ 连不上 127.0.0.1:1 ⇒ 注册抛异常 ⇒ 整个上下文起不来。
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "spring.cloud.nacos.discovery.register-enabled=false",
                "spring.cloud.service-registry.auto-registration.enabled=false",
                "management.otlp.tracing.export.enabled=false",
                // ⚠️ 域侧 yml 的 `spring.datasource.password: ${POSTGRES_PASSWORD}` **无默认值**
                //    ⇒ Spring 解析不到占位符 ⇒ **DataSource bean 创建就失败** ⇒ 上下文起不来
                //    ⇒ ⇒ 本测试要的是「载体 + 暴露面」，不需要真库 ⇒ 给一个占位值即可。
                //    ⚠️ 这**不改变**生产行为（生产仍无默认值 ⇒ 仍 fail-fast）。
                "spring.datasource.password=test-only-not-a-real-secret",
                "logging.level.com.alibaba.nacos=WARN"
        })
class FollowRpcPrometheusCarrierGateTest {

    /**
     * HTTP 端口走<b>域内固定段</b>（{@code 22340–22344}）—— **不用 {@code RANDOM_PORT}**。
     *
     * <p>⚠️ **为什么不能用 {@code RANDOM_PORT}**：那是 OS 盲选口（临时端口段 49152–65535
     * = Docker 随机发布容器端口用的同一段）⇒ ⇒ 域级守门 {@code BlindPortGateTest}（① 源扫，
     * 扫描面<b>含 follow-rpc</b>）<b>直接判违规</b>。
     */
    @DynamicPropertySource
    static void httpPort(DynamicPropertyRegistry registry) {
        registry.add("server.port", RealTomcatPort::pick);
        // ⚠️ **必须把 gRPC 业务口覆盖成 0**（= 随机口）——
        //   本测试**刻意不排除** devh 的 `GrpcServerAutoConfiguration`：
        //   排除它 = 连带拆掉它提供的 `GrpcServerProperties` / `GrpcServiceDiscoverer` /
        //   `GlobalServerInterceptorRegistry` 三个 bean ⇒ 而 `GrpcServerFactoryAutoConfiguration`
        //   又 @Autowired 后两个 ⇒ ⇒ 连锁 `NoSuchBeanDefinitionException` ⇒ 上下文起不来。
        //   ⚠️ **实测连着踩了三层**（2026-10-31 · 合并收口执行者跑载体门时）：
        //   ① `GrpcServerProperties` → ② `GrpcServiceDiscoverer` → ③ `GlobalServerInterceptorRegistry`
        //   ⇒ ⇒ **保持生产装配面不动**，改用「端口置 0」避开与本机已跑服务抢口。
        //   ⇒ 域侧业务口 = 8082（follow-rpc）⇒ ⇒ 置 0 ⇒ 绑随机口。
        registry.add("grpc.server.port", () -> 0);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private MeterRegistry registry;

    @Autowired
    private FollowRpcMetrics metrics;

    @Autowired
    private ApplicationContext context;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * 最小载体上下文 —— 只装被测机制需要的件。
     *
     * <p>⚠️ <b>为什么用 {@code @SpringBootConfiguration} 而不是 {@code @SpringBootApplication}</b>：
     * {@code @SpringBootApplication} = {@code @SpringBootConfiguration} ＋
     * {@code @EnableAutoConfiguration} ＋ <b>{@code @ComponentScan}</b>
     * ⇒ 后者会把 {@code com.rag.follow.follow.rpc} <b>整包</b>扫进来
     * ⇒ ⇒ 载体守门被数据库可用性绑架 ⇒ 测不到「载体有没有」这个真问题
     * ⇒ ⇒ 这里<b>刻意不写 {@code @ComponentScan}</b>（只留 {@code @EnableAutoConfiguration}
     * ＋ 显式 {@code @Import}）⇒ ⇒ 只装被测机制需要的件。
     *
     * <p>⇒ 断言的仍是「<b>域侧真实 application.yml 的 management 段</b>真生效」
     * （本类<b>不自带</b> management 配置 ⇒ 它读的是 classpath 上的真实 yml）。
     */
    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            // ⚠️ **只排除 Flyway 自动配置**（2026-10-31 实测踩到）：
            //    follow-rpc 用的是 **Flyway**（users 域两个 rpc 用 SchemaInitializer，故那边没这问题）
            //    ⇒ ⇒ classpath 上有 flyway-core + spring-boot-flyway + 域侧 yml 配了 datasource
            //    ⇒ ⇒ `FlywayAutoConfiguration` 会在上下文启动时**真连 PG**
            //    ⇒ ⇒ 本机没有 `postgres` 主机 ⇒ `UnknownHostException: postgres`
            //    ⇒ ⇒ `flywayInitializer` bean 创建失败 ⇒ **上下文起不来**
            //    ⇒ ⇒ 载体守门被数据库可用性绑架 ⇒ 测不到「载体有没有」这个真问题
            //    ⇒ ⇒ 排除掉 ⇒ 只留被测的 HTTP 载体机制。
            //    ⚠️ 这**不改变**生产行为（生产仍会跑 Flyway 迁移）。
            //
            // ⚠️ **为什么【不】排除 devh 的 gRPC server 自动配置**（本轮订正）：
            //   devh 3.1.0 的 `GrpcServerAutoConfiguration` **无条件**起 gRPC server
            //   ⇒ ⇒ 排除它 = **连带拆掉它提供的三个 bean**
            //   （`GrpcServerProperties` / `GrpcServiceDiscoverer` / `GlobalServerInterceptorRegistry`）
            //   ⇒ ⇒ 而 `GrpcServerFactoryAutoConfiguration` 又 @Autowired 后两个
            //   ⇒ ⇒ 连锁 `NoSuchBeanDefinitionException` ⇒ 上下文起不来。
            //   ⚠️ **手工补一个空壳 `GrpcServerProperties` bean 更糟**：它**不做属性绑定**
            //   ⇒ ⇒ `grpc.server.port` 在本测试上下文里**根本不生效**
            //   ⇒ ⇒ 顶掉了带绑定的那份 ⇒ ⇒ **测的就不是生产那条链了**。
            //   ⇒ ⇒ **正解 = 不排除 + 把业务口置 0**（见 `@DynamicPropertySource` 处的说明）
            //   ⇒ ⇒ **生产装配面原封不动** ⇒ ⇒ 验的仍是生产那条链。
            org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration.class
    })
    @Import(FollowRpcMetrics.class)
    static class MinimalCarrierApp {

    }

    // ====================================================================================
    // ① 载体存在
    // ====================================================================================

    @Test
    @DisplayName("🔴 载体 —— 进程里真的有 servlet 容器（否则 actuator 那一族配置永远不加载）")
    void servletContainerIsActuallyPresent() {
        assertThat(port)
                .as("DEFINED_PORT ⇒ 真的起了 web server（不是 MockMvc）")
                .isPositive();
        // ⚠️ 判据 = **上下文确实是 web 应用上下文**（Boot 的 WebApplicationType 推导出 SERVLET）
        //    ⇒ 这正是「有 jakarta.servlet.Servlet 在 classpath 上」的直接后果
        //    ⇒ ⇒ 这条断言**红的条件** = 有人把 spring-boot-starter-web 删了
        //    （那 WebApplicationType 会退回 NONE ⇒ 上下文根本不是 web 上下文 ⇒ 这里红）。
        assertThat(context)
                .as("必须是 web 应用上下文（WebApplicationType=SERVLET）⇒ 说明 servlet 容器真在 classpath 上")
                .isInstanceOf(org.springframework.boot.web.server.context.WebServerApplicationContext.class);
    }

    // ====================================================================================
    // ② + ③ 暴露面开着 + 端点回主端口 ⇒ 真抓得到
    // ====================================================================================

    @Test
    @DisplayName("🔴 暴露面 —— /actuator/prometheus 真能在主端口抓到（非 404 ⇒ 载体与暴露面都生效）")
    void prometheusEndpointIsReachableOnMainPort() throws Exception {
        HttpResponse<String> resp = http.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/actuator/prometheus"))
                        .timeout(Duration.ofSeconds(20))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(resp.statusCode())
                .as("/actuator/prometheus 必须 200（404 ⇒ 端点没上线：要么没载体，要么 exposure.include 没生效）")
                .isEqualTo(200);
        assertThat(resp.body())
                .as("响应体必须是 Prometheus 文本格式")
                .contains("# HELP")
                .contains("# TYPE");
    }

    // ====================================================================================
    // ④ 导出的指标与 Go 逐条同形
    // ====================================================================================

    @Test
    @DisplayName("🔴 指标名 —— follow_rpc_* 5 个 meter 与 Go 逐条同形")
    void exportedMetricNamesMatchGo() throws Exception {
        // 先让 meter 真的注册进 registry（域侧真生产类的真上报路径）
        metrics.observeRpc("Follow", System.nanoTime(), null);
        metrics.observeRelation("follow", null);
        metrics.observeDbError("follow", "timeout");

        String scrape = scrape();

        // Go：Subsystem:"follow_rpc" + Namespace 未设（空）⇒ 全名 = follow_rpc_<name>
        assertThat(scrape)
                .as("Go 侧 RPCRequestCounter（metrics.go:13-17）⇒ 全名 follow_rpc_request_total")
                .contains("follow_rpc_request_total{");
        assertThat(scrape)
                .as("Go 侧 RelationActionCounter（metrics.go:27-31）⇒ 全名 follow_rpc_relation_action_total")
                .contains("follow_rpc_relation_action_total{");
        assertThat(scrape)
                .as("Go 侧 DBErrorCounter（metrics.go:33-37）⇒ 全名 follow_rpc_db_error_total")
                .contains("follow_rpc_db_error_total{");
        assertThat(scrape)
                .as("Go 侧 RecommendationSizeGauge（metrics.go:39-42）⇒ 全名 follow_rpc_recommendation_size")
                .contains("follow_rpc_recommendation_size");
        assertThat(scrape)
                .as("Go 侧 RPCRequestDuration（metrics.go:19-25）⇒ 全名 follow_rpc_request_duration_seconds")
                .contains("follow_rpc_request_duration_seconds");
    }

    @Test
    @DisplayName("🔴 标签集 —— {method,result} 与 Go 的 []string{\"method\",\"result\"} 逐条同形")
    void exportedLabelSetMatchesGo() throws Exception {
        metrics.observeRpc("Follow", System.nanoTime(), null);

        String scrape = scrape();

        // Go 侧 label = []string{"method", "result"} ⇒ 抓取文本里该 series 的标签集**恰好**是这两个
        assertThat(scrape).contains("follow_rpc_request_total{method=\"Follow\",result=\"success\"}");
        // ⚠️ 反向断言：Go 侧**没有** application 这个全局标签（Namespace 未设 + Subsystem 已构成全名）
        //    ⇒ 若 yml 里把 management.metrics.tags.application 加回来，这里会红。
        assertThat(scrape).doesNotContain("application=\"follow-rpc\"");
    }

    // ====================================================================================
    // ⑤ rpc 侧隔离（载体引入 web 容器后的新增风险面）
    // ====================================================================================

    /**
     * 🟢 <b>rpc 侧隔离</b> —— 10 类 api 侧中间件<b>不在容器里</b>。
     *
     * <p><b>Go 出处（真源，逐行）</b>：
     * <pre>
     * service/follow/rpc/follow.go:37    s := zrpc.MustNewServer(c.RpcServerConf, ...)
     * </pre>
     * ⇒ ⇒ Go 侧 rpc 是 {@code zrpc.MustNewServer} ⇒ ⇒ <b>零 rest 中间件</b>
     * （{@code rest.With*} 全在 api 侧 {@code service/<域>/api/internal/handler/routes.go}）
     * ⇒ ⇒ RAG 侧 rpc 容器里<b>不得出现</b> api 侧中间件。
     *
     * <p>⚠️ <b>为什么这条在「加了 web 容器」之后才必须断言</b>：
     * 本域 rpc 原先是纯 gRPC 进程 ⇒ classpath 上<b>没有</b> spring-webmvc
     * ⇒ ⇒ 那 10 类 api 侧中间件<b>连类都加载不了</b>（{@code NoClassDefFoundError}）
     * ⇒ ⇒ 「不在容器」当时是<b>免费的</b>；载体任务给 rpc 加了 {@code spring-boot-starter-web}
     * ⇒ ⇒ classpath 上<b>有了</b> webmvc ⇒ ⇒ 这 10 类**技术上可以活**
     * ⇒ ⇒ 「它们仍然不在」才变成一个<b>需要守门的事实</b>。
     *
     * <p>⚠️ <b>13 类分两批挡</b>（本树已含共享层的 {@code @ConditionalOnApiSide}，`53bc05ec`）：
     * <ul>
     *   <li><b>10 个组件扫描类</b>（{@code @Component} / {@code @Configuration}）：
     *       {@code FollowRpcApplication} 的 {@code @ComponentScan} 刻意<b>不扫</b>
     *       {@code com.rag.common}（启动类注释写明不能加）⇒ ⇒ <b>第一道</b>：域侧约定挡住；</li>
     *   <li><b>3 个自动配置面</b>（走 {@code AutoConfiguration.imports} ⇒ <b>与组件扫描解耦</b>）：
     *       ⇒ ⇒ 「不扫 {@code com.rag.common}」<b>挡不住</b>它们
     *       ⇒ ⇒ <b>第二道</b>：{@code @ConditionalOnApiSide} + {@code rag.side: rpc}（{@code e86ec93b}）挡住；</li>
     * </ul>
     * ⇒ ⇒ 两道叠加 ⇒ ⇒ <b>13 类全部不注册</b> ⇒ ⇒ 断言可写成「13 类全不在」这一条
     * ⇒ ⇒ ⚠️ <b>与 Go 同形的依据</b>：Go 侧 rpc 是 {@code zrpc.MustNewServer} ⇒ <b>零 rest 中间件</b>
     * ⇒ ⇒ 断言写成「不在容器」⇒ ⇒ 两侧都绿；
     * ⇒ ⇒ <b>不能</b>断言成「靠 rag.side 挡住」⇒ ⇒ 那是跨分支不稳定的写法。
     */
    @Test
    @DisplayName("🟢 rpc 侧隔离 —— 13 类 api 侧 web Bean 全部不在容器里（Go 侧 rpc 零 rest 中间件）")
    void apiSideMiddlewareIsAbsentFromRpcContainer() {
        assertThat(context.getBeanNamesForType(GoStyleFormLimitWebMvcConfig.class))
                .as("GoStyleFormLimitWebMvcConfig 属 api 侧 rest 中间件 ⇒ rpc 侧不得注册")
                .isEmpty();
        assertThat(context.getBeanNamesForType(GoStyleFormValueLimitInterceptor.class))
                .as("GoStyleFormValueLimitInterceptor 属 api 侧 ⇒ rpc 侧不得注册")
                .isEmpty();
        assertThat(context.getBeanNamesForType(GoStyleJsonBodyConverterConfig.class))
                .as("GoStyleJsonBodyConverterConfig 属 api 侧 ⇒ rpc 侧不得注册")
                .isEmpty();
        assertThat(context.getBeanNamesForType(GoStyleTomcatParameterLimitCustomizer.class))
                .as("GoStyleTomcatParameterLimitCustomizer 属 api 侧 ⇒ rpc 侧不得注册")
                .isEmpty();
        assertThat(context.getBeanNamesForType(GoStyleTraceparentFilter.class))
                .as("GoStyleTraceparentFilter 属 api 侧 ⇒ rpc 侧不得注册")
                .isEmpty();
        assertThat(context.getBeanNamesForType(GoStyleBreakerFilter.class))
                .as("GoStyleBreakerFilter 属 api 侧 ⇒ rpc 侧不得注册")
                .isEmpty();
        assertThat(context.getBeanNamesForType(GoStyleCpuSheddingFilter.class))
                .as("GoStyleCpuSheddingFilter 属 api 侧 ⇒ rpc 侧不得注册")
                .isEmpty();
        assertThat(context.getBeanNamesForType(GoStyleMaxBytesFilter.class))
                .as("GoStyleMaxBytesFilter 属 api 侧 ⇒ rpc 侧不得注册")
                .isEmpty();
        assertThat(context.getBeanNamesForType(GoStyleMaxConnsFilter.class))
                .as("GoStyleMaxConnsFilter 属 api 侧 ⇒ rpc 侧不得注册")
                .isEmpty();
        assertThat(context.getBeanNamesForType(HttpMiddlewareFilter.class))
                .as("HttpMiddlewareFilter 属 api 侧 ⇒ rpc 侧不得注册")
                .isEmpty();
        // ↓ 下面 3 个是**自动配置面**（走 AutoConfiguration.imports ⇒ 与组件扫描解耦）
        //   ⇒ 它们**不靠**「rpc 不扫 com.rag.common」那道域侧约定挡住
        //   ⇒ ⇒ 只能靠共享层的 @ConditionalOnApiSide ＋ rag.side: rpc
        assertThat(context.getBeanNamesForType(GoStyleJsonContentTypeAdvice.class))
                .as("GoStyleJsonContentTypeAdvice 是自动配置面 ⇒ 靠 @ConditionalOnApiSide 挡 ⇒ rpc 侧不得注册")
                .isEmpty();
        assertThat(context.getBeanNamesForType(GoStylePathNormalizingAutoConfiguration.class))
                .as("GoStylePathNormalizingAutoConfiguration 是自动配置面 ⇒ rpc 侧不得注册")
                .isEmpty();
        assertThat(context.getBeanNamesForType(GoStyleBreakerRouteTemplateAutoConfiguration.class))
                .as("GoStyleBreakerRouteTemplateAutoConfiguration 是自动配置面 ⇒ rpc 侧不得注册")
                .isEmpty();
    }

    /**
     * 🟢 <b>进程形态</b> —— 本域必须显式声明 {@code rag.side: rpc}。
     *
     * <p>⚠️ <b>为什么这条现在有意义</b>：共享层的 {@code @ConditionalOnApiSide} 是
     * 「api 侧中间件只在 api 侧注册」的<b>单一真源</b>条件注解（主干 {@code 53bc05ec}）
     * ⇒ ⇒ {@code rag.side} 这个属性<b>必须有消费者</b>才有意义。
     * ⇒ ⇒ 本条断言守的是<b>配置面事实</b>：「本域 yml 里那行没被删、没被改成 api」。
     *
     * <p>⚠️ <b>本条的边界（如实记录，不夸大）</b>：
     * <ul>
     *   <li>本断言读的是<b>域内真实 yml</b>（本测试<b>不自带</b> {@code rag.side}
     *       ⇒ 它读的是 classpath 上 {@code target/classes/application.yml} 的真值）；</li>
     *   <li>本树<b>已含</b>共享层的 {@code @ConditionalOnApiSide}（{@code 53bc05ec}）
     *       ⇒ ⇒ 这一行<b>现在真的被读</b> ⇒ ⇒ 这条断言与「机制」<b>已对齐</b>
     *       （⚠️ 前提：编译产物必须是新的 —— {@code -pl} 不带 {@code -am} 会用
     *       {@code ~/.m2} 里的<b>旧 jar</b> ⇒ ⇒ 源码里有的注解在运行时不存在 ⇒ ⇒ 断言必红）。</li>
     * </ul>
     */
    @Test
    @DisplayName("🟢 进程形态 —— rag.side=rpc 已声明（读域内真实 yml ⇒ 不是测试自带配置）")
    void rpcSideFlagIsDeclared() {
        assertThat(context.getEnvironment().getProperty("rag.side"))
                .as("本域 yml 必须显式声明 rag.side=rpc（api 侧中间件的唯一开关）")
                .isEqualTo("rpc");
    }

    // ====================================================================================
    // helpers
    // ====================================================================================

    private String scrape() throws Exception {
        HttpResponse<String> resp = http.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/actuator/prometheus"))
                        .timeout(Duration.ofSeconds(20))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(resp.statusCode()).isEqualTo(200);
        return resp.body();
    }
}
