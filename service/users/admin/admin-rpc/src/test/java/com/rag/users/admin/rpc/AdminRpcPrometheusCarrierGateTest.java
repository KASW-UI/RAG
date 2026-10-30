package com.rag.users.admin.rpc;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

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
import com.rag.users.admin.rpc.metrics.AdminRpcMetrics;
import com.rag.users.admin.rpc.support.RealTomcatPort;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🔴 <b>载体守门</b>：admin-rpc 的 {@code /actuator/prometheus} <b>真能被外部抓到</b>
 * （2026-10-30 · G1 lane · 指标抓取出口补载体）。
 *
 * <h2>本类验的是什么（机制面，不是业务面）</h2>
 * <ol>
 *   <li><b>载体存在</b>：进程里<b>真的有</b> Tomcat（{@code spring-boot-starter-web}）
 *       ⇒ 否则 {@code WebApplicationType} 推导为 {@code NONE}
 *       ⇒ {@code management.endpoints.web.*} 那一族配置<b>永远不加载</b>
 *       （<b>不报错，是静默无效</b> —— AGENTS.md §3.3 的典型形态）；</li>
 *   <li><b>暴露面开着</b>：{@code management.endpoints.web.exposure.include} 含
 *       {@code prometheus} ＋ {@code endpoint.prometheus.enabled: true}
 *       ＋ {@code endpoints.enabled-by-default: true}；</li>
 *   <li><b>端点真的回主端口</b>：{@code management.server.port} <b>不设</b>
 *       ⇒ Boot 4 判为 {@code ManagementPortType.SAME} ⇒ 端点挂主 web 端口
 *       （<b>若写 -1 ⇒ 判为 {@code DISABLED} ⇒ 端点彻底不上线</b>）；</li>
 *   <li><b>导出的指标与 Go 逐条同形</b>：Go
 *       {@code service/user/admin/rpc/internal/metrics/metrics.go:9-21} 的两个
 *       CounterVec（{@code Namespace:""} + {@code Subsystem:"admin_rpc"}）
 *       ⇒ 指标名 {@code admin_rpc_login_total} / {@code admin_rpc_high_risk_action_logal}
 *       ＋ 标签集 {@code {result}} 与 {@code {action,result}}。</li>
 * </ol>
 *
 * <h2>⚠️ 为什么用<b>独立最小上下文</b>而不是 {@code AdminRpcApplication}</h2>
 * <p>业务启动类会拉起 {@code AdminSchemaBootstrap}（{@code @PostConstruct} 里
 * <b>真连 PG 建表</b>，失败即拒绝启动）＋ {@code AdminSeederBootstrap}
 * ⇒ ⇒ 载体守门会被<b>数据库可用性</b>绑架 ⇒ ⇒ 「载体有没有」这个问题就<b>测不到</b>
 * （起不来 ⇒ 不知道是载体缺还是 PG 缺）。
 * <p>⇒ 故本类只装<b>被测机制所需的最小件</b>：actuator ＋ web ＋ 域侧
 * {@link AdminRpcMetrics}（真生产类，不是复制品）⇒ ⇒ 测的正是「载体 + 暴露面 + 指标定义」
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
 *   <li>{@code service/user/admin/rpc/etc/admin.yaml:31-38} ——
 *       {@code DevServer: {Enabled: true, Port: 9097, MetricsPath: /metrics,
 *       HealthPath: /healthz, EnableMetrics: true}}；</li>
 *   <li>{@code service/user/admin/rpc/internal/metrics/metrics.go:9-21} ——
 *       两个 CounterVec 的 {@code Namespace:""} + {@code Subsystem:"admin_rpc"}
 *       ＋ 标签 {@code []string{"result"}} 与 {@code []string{"action","result"}}；</li>
 *   <li>{@code go-zero@v1.10.2/internal/devserver/server.go:44-49} ——
 *       {@code if s.config.EnableMetrics { prometheus.Enable();
 *       s.handleFunc(s.config.MetricsPath, promhttp.Handler().ServeHTTP) }}。</li>
 * </ul>
 */
@SpringBootTest(
        classes = AdminRpcPrometheusCarrierGateTest.MinimalCarrierApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {
                // ⚠️ 本测试用 DEFINED_PORT ⇒ **真起 web server** ⇒
                //    若开了 Nacos 自动注册，它会在 web server 启动阶段去注册
                //    ⇒ 连不上 127.0.0.1:1 ⇒ 注册抛异常 ⇒ 整个上下文起不来
                //    （与 hot-api 的 HotApiRealTomcatGateTest 同款处置）。
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "spring.cloud.nacos.discovery.register-enabled=false",
                "spring.cloud.service-registry.auto-registration.enabled=false",
                "management.otlp.tracing.export.enabled=false",
                // ⚠️ 域侧 yml 的 `spring.datasource.password: ${POSTGRES_PASSWORD}` **无默认值**
                //    （与 Go 同形：Go `os.ExpandEnv` 把它展开成空串 ⇒ 连库失败）
                //    ⇒ Spring 解析不到占位符 ⇒ **DataSource bean 创建就失败** ⇒ 上下文起不来
                //    ⇒ ⇒ 本测试要的是「载体 + 暴露面」，不需要真库 ⇒ 给一个占位值即可。
                //    ⚠️ 这**不改变**生产行为（生产仍无默认值 ⇒ 仍 fail-fast）。
                "spring.datasource.password=test-only-not-a-real-secret",
                "logging.level.com.alibaba.nacos=WARN"
        })
class AdminRpcPrometheusCarrierGateTest {

    /**
     * HTTP 端口走<b>域内固定段</b>（{@code 22295–22299}）—— **不用 {@code RANDOM_PORT}**。
     *
     * <p>⚠️ **为什么不能用 {@code RANDOM_PORT}**：那是 OS 盲选口（临时端口段 49152–65535
     * = Docker 随机发布容器端口用的同一段）⇒ ⇒ 域级守门 {@code BlindPortGateTest}（① 源扫）
     * <b>直接判违规</b>（2026-10-30 本轮实测：同款测试首版就是被它抓红的）⇒ ⇒ 且危害形态
     * 是「静默被抢」（邻居占更具体地址 ⇒ 我们照样绑通配 ⇒ 客户端打到邻居）。
     */
    @DynamicPropertySource
    static void httpPort(DynamicPropertyRegistry registry) {
        registry.add("server.port", RealTomcatPort::pick);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private MeterRegistry registry;

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
     * ⇒ 后者会把 {@code com.rag.users.admin.rpc} <b>整包</b>扫进来
     * ⇒ ⇒ 扫到 {@code AdminSchemaBootstrap} / {@code AdminSeederBootstrap}
     * （{@code @PostConstruct} 里<b>真连 PG 建表 + 播种</b>）
     * ⇒ ⇒ 载体守门被数据库可用性绑架 ⇒ 测不到「载体有没有」这个真问题
     * ⇒ ⇒ 这里<b>刻意不写 {@code @ComponentScan}</b>（只留 {@code @EnableAutoConfiguration}
     * ＋ 显式 {@code @Import}）⇒ ⇒ 只装被测机制需要的件。
     */
    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            // ⚠️ **必须排除 devh 的 gRPC server 自动配置**：
            //    devh 3.1.0 的 `GrpcServerAutoConfiguration` **无条件**起 gRPC server
            //    （它的 `spring-configuration-metadata.json` 里**没有** `grpc.server.enabled`
            //    ⇒ ⇒ 没有「关掉 gRPC」这个开关）⇒ ⇒ 不排除 ⇒ 它会去绑域侧业务口 8081
            //    ⇒ ⇒ 与本机已跑的服务抢口 ⇒ BindException ⇒ 上下文起不来。
            //    ⇒ ⇒ 本测试**不验 gRPC**（验的是 HTTP 载体与暴露面）⇒ ⇒ 排除掉最干净
            //    ⇒ ⇒ 且**不需要**任何 gRPC 端口（也就不会碰「OS 盲选口」那条红线）。
            net.devh.boot.grpc.server.autoconfigure.GrpcServerAutoConfiguration.class,
            // ⚠️ **还必须排除 devh 的 server 工厂自动配置**（2026-10-31 实测踩到）：
            //    `GrpcServerFactoryAutoConfiguration` 的 `@ConditionalOnClass` 认的是
            //    `io.grpc.netty.shaded.io.netty.channel.Channel` 与
            //    `io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder`
            //    ⇒ ⇒ 这两个类在本域 classpath 上（`grpc-server-spring-boot-starter` 传递引入
            //    `grpc-netty-shaded`）⇒ ⇒ 这个自动配置**照样激活**
            //    ⇒ ⇒ 它的 `shadedNettyGrpcServerFactory` bean 要 `GrpcServiceDiscoverer`
            //    ⇒ ⇒ 而那个 bean 是**被排除的**
            //    `GrpcServerAutoConfiguration.defaultGrpcServiceDiscoverer()` 提供的
            //    ⇒ ⇒ `NoSuchBeanDefinitionException` ⇒ **上下文起不来** ⇒ 4 条用例全 Error。
            //    ⇒ ⇒ 本测试**不验 gRPC** ⇒ ⇒ 排除掉最干净 ⇒ ⇒ 且不需要任何 gRPC 端口。
            net.devh.boot.grpc.server.autoconfigure.GrpcServerFactoryAutoConfiguration.class,
    })
    @Import(AdminRpcMetrics.class)
    static class MinimalCarrierApp {

        /**
         * 🔴 **必须自己补一个** {@link net.devh.boot.grpc.server.config.GrpcServerProperties} bean。
         *
         * <p>⚠️ **为什么**：被排除的 {@code GrpcServerAutoConfiguration} 里带
         * {@code @Bean defaultGrpcServerProperties()} ⇒ ⇒ 排除它 ⇒
         * {@code GrpcServerProperties} 这个 bean 就没了 ⇒ ⇒ 而 devh 的
         * {@code GrpcMetadataNacosConfiguration} 等**其它**自动配置
         * （它们**不**依赖被排除的那个）**@Autowired 这个 bean**
         * ⇒ ⇒ 少一个就 {@code NoSuchBeanDefinitionException} ⇒ **上下文起不来**
         * （2026-10-31 实测：只排除 server 自动配置 ⇒ 报「No qualifying bean of type
         * 'net.devh.boot.grpc.server.config.GrpcServerProperties}' ⇒ 4 条用例全 Error）。
         *
         * <p>⇒ ⇒ 这里补一个**空壳**（走构造器默认值，不做属性绑定）
         * ⇒ ⇒ 目的**只是**让那些自动配置能完成装配
         * ⇒ ⇒ 它**不会**被用来起 gRPC server（那个自动配置已被排除）。
         */
        @Bean
        net.devh.boot.grpc.server.config.GrpcServerProperties grpcServerProperties() {
            return new net.devh.boot.grpc.server.config.GrpcServerProperties();
        }
    }

    // ====================================================================================
    // ① 载体存在
    // ====================================================================================

    @Test
    @DisplayName("🔴 载体 —— 进程里真的有 servlet 容器（否则 actuator 那一族配置永远不加载）")
    void servletContainerIsActuallyPresent() {
        assertThat(port)
                .as("RANDOM_PORT ⇒ 真的起了 web server（不是 MockMvc）")
                .isPositive();
        // ⚠️ 判据 = **上下文确实是 web 应用上下文**（Boot 的 WebApplicationType 推导出 SERVLET）
        //    ⇒ 这正是「有 jakarta.servlet.Servlet 在 classpath 上」的直接后果
        //    ⇒ ⇒ 换个角度说：这条断言**红的条件** = 有人把 spring-boot-starter-web 删了
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
    @DisplayName("🔴 指标名 —— RAG 侧渲染 admin_rpc_login_total / admin_rpc_high_risk_action_logal_total（后者与 Go 差一个 _total）")
    void exportedMetricNamesMatchGo() throws Exception {
        String scrape = scrape();

        // Go：Namespace:"" + Subsystem:"admin_rpc" + Name:"login_total" ⇒ admin_rpc_login_total
        // ⚠️ client_golang **不去重** `_total` ⇒ Go 侧渲染出来就是 `admin_rpc_login_total`
        //    ⇒ RAG 侧 Micrometer 见名字已以 _total 结尾 ⇒ 也不追加 ⇒ **两侧逐字同形** ✅
        assertThat(scrape).contains("admin_rpc_login_total{");
        assertThat(scrape).doesNotContain("admin_rpc_login_total_total{");

        // ⚠️ Go：Name:"high_risk_action_logal"（**名字本身不带 _total**）
        //    ⇒ Go 侧渲染 = `admin_rpc_high_risk_action_logal`
        //    ⇒ RAG 侧 Micrometer 给 Counter **强制追加 `_total`** ⇒ `..._logal_total`
        //    ⇒ ⇒ **两侧指标名差一个 `_total`** —— 这是**已签字的框架限制**，
        //       不是本载体任务引入的（同族：`DEV-FAV-RPC-001`，
        //       Go `favorite_request_seconds_counter` vs RAG `favorite_request_seconds_counter_total`）。
        //    ⇒ 这里断言的是 **RAG 侧的实际形态**（把差异钉死，而不是让它悄悄漂）。
        assertThat(scrape).contains("admin_rpc_high_risk_action_logal_total{");
    }

    @Test
    @DisplayName("🔴 标签集 —— {result} 与 {action,result} 逐条同形（Go 侧无 application 全局标签）")
    void exportedLabelSetMatchesGo() throws Exception {
        String scrape = scrape();

        // Go 侧 admin_rpc_login_total 的 label = []string{"result"}
        assertThat(scrape).contains("admin_rpc_login_total{result=\"success\"}");
        // Go 侧 admin_rpc_high_risk_action_logal 的 label = []string{"action","result"}
        assertThat(scrape).contains("action=\"ban\"");
        assertThat(scrape).contains("result=\"success\"");
        // ⚠️ 反向断言：Go 侧**没有** application 这个全局标签（Namespace:"" + Subsystem 已构成全名）
        //    ⇒ 若 yml 里把 management.metrics.tags.application 加回来，这里会红。
        assertThat(scrape).doesNotContain("application=\"admin-rpc\"");
    }

    // ====================================================================================
    // ⑤ rpc 侧隔离（载体引入 web 容器后的新增风险面）
    // ====================================================================================

    /**
     * 🟢 <b>rpc 侧隔离</b> —— 10 类 api 侧中间件<b>不在容器里</b>。
     *
     * <p><b>Go 出处（真源，逐行）</b>：
     * <pre>
     * service/user/admin/rpc/admin.go:36    s := zrpc.MustNewServer(c.RpcServerConf, ...)
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
     * <p>⚠️ <b>判据写成「结果」而不是「机制」</b>（跨分支稳定的关键）：
     * <ul>
     *   <li><b>本分支</b>：共享层的 {@code @ConditionalOnApiSide}（主干 {@code 53bc05ec}）
     *       <b>不在本分支基线</b> ⇒ ⇒ 这 10 类靠<b>域侧约定</b>不活 ——
     *       {@code AdminRpcApplication} 的 {@code @ComponentScan} 刻意<b>不扫</b>
     *       {@code com.rag.common}（启动类注释写明不能加）⇒ ⇒ 不注册；</li>
     *   <li><b>主干</b>：{@code @ConditionalOnApiSide} + {@code rag.side: rpc}（{@code e86ec93b}）
     *       ⇒ ⇒ 即便有人把 {@code com.rag.common} 加进 {@code scanBasePackages} 也<b>仍不注册</b>
     *       ⇒ ⇒ 护栏强度<b>高于</b>本分支。</li>
     * </ul>
     * ⇒ ⇒ 断言写成「不在容器」⇒ ⇒ 两侧都绿；
     * ⇒ ⇒ <b>不能</b>断言成「靠 rag.side 挡住」⇒ ⇒ 那是跨分支不稳定的写法。
     */
    @Test
    @DisplayName("🟢 rpc 侧隔离 —— 10 类 api 侧中间件不在容器里（Go 侧 rpc 零 rest 中间件）")
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
     *   <li><b>本分支基线尚未含</b> {@code @ConditionalOnApiSide} ⇒ ⇒ 在本分支上
     *       这一行<b>还没有消费者</b>（护栏由「不扫 com.rag.common」兜着）；
     *       ⇒ ⇒ 主干同步后它才有消费者、这条断言才与「机制」对齐。</li>
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
