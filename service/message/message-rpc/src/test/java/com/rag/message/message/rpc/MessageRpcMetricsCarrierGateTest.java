package com.rag.message.message.rpc;

import com.rag.common.http.GoStylePathNormalizingAutoConfiguration;
import com.rag.message.message.proto.ConversationListReq;
import com.rag.message.message.proto.MessageServiceGrpc;
import com.rag.message.message.rpc.support.GrpcTestPort;
import com.rag.message.message.rpc.support.RealContainerPort;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.PropertyPlaceholderHelper;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 <b>指标抓取载体的守门</b>（2026-10-30 · 阶段一 · 「Go 有 DevServer、RAG 零出口」同款缺口的落码守门）
 *
 * <h2>它守的是什么</h2>
 * <p>Go 侧 {@code service/message/rpc/etc/message.yaml:19-26} 配了
 * {@code DevServer: {Enabled: true, Port: 16067, MetricsPath: /metrics, HealthPath: /healthz, EnableMetrics: true}}
 * ⇒ ⇒ {@code go-zero@v1.10.2/zrpc/server.go:63 c.SetUp()} ⇒
 * ⇒ {@code go-zero@v1.10.2/core/service/serviceconf.go:77 devserver.StartAgent(sc.DevServer)}
 * ⇒ ⇒ {@code go-zero@v1.10.2/internal/devserver/server.go:73} <b>独立端口</b> {@code http.ListenAndServe}
 * ⇒ ⇒ <b>Go 的 rpc 侧真的能被抓</b>。
 *
 * <p>而 RAG 侧（本模块）此前：{@code pom.xml} 第 5 段<b>有</b> actuator ＋ micrometer-prometheus
 * ⇒ 指标<b>注册了</b>；但<b>没有 servlet 容器</b> ⇒ {@code WebApplicationType=NONE}
 * ⇒ {@code management.endpoints.web.*} 这一族配置<b>根本不加载</b>（**不报错，是静默无效**）
 * ⇒ ⇒ <b>抓不到</b>（🔴 「Go 有、RAG 无」⇒ 跨域派单表里的一条，见
 * {@code docs/DRIFT/follow/rpc.md:289} 与 `:333}）。
 *
 * <h2>🔴 为什么必须用<b>真 fat jar</b>（而不是 {@code @SpringBootTest}）</h2>
 * <ul>
 *   <li>本缺陷的根因是<b>运行时 classpath 上没有 servlet 容器</b>
 *       ⇒ {@code service/common} 的 web starter 是 {@code optional}（<b>不传递</b>）
 *       ⇒ 必须由<b>域侧自己声明</b> {@code spring-boot-starter-web}
 *       ⇒ 只在<b>打包产物</b>里才暴露（测试 classpath 上，域侧依赖本来就在）。</li>
 *   <li>同理，{@code management.endpoints.web.exposure.include} 是不是<b>死配置</b>，
 *       也只有在<b>真进程</b>里 curl 一次才知道。</li>
 * </ul>
 * ⇒ 本类用 {@code java -jar} 起<b>真 fat jar</b>（生产 classpath ＋ 生产 yaml）。
 *
 * <h2>判据（三条，逐条对应「载体真的在」）</h2>
 * <ol>
 *   <li><b>配置层</b>：生产 yaml 的 {@code server.port} 在 env <b>未设</b>时解析出的默认值
 *       <b>必须等于 Go 的 16067</b>（端口一字不差）。</li>
 *   <li><b>运行层</b>：真进程起来后，日志里必须有 {@code Tomcat started on}
 *       ⇒ <b>web 容器真的在</b>（不是「配置里写了」）。</li>
 *   <li><b>可观察层</b>：真 {@code GET /actuator/prometheus} ⇒ <b>200</b> ＋
 *       <b>响应体含 RAG 侧指标名</b>；且本域自己的 meter
 *       （{@code message_rpc_request_total}）在<b>一次真实 gRPC 调用之后</b>也出现在响应体里
 *       ⇒ ⇒ 「<b>指标注册了但抓不到</b>」这个缺陷被机械检出。</li>
 * </ol>
 *
 * <h2>🔴 为什么必须<b>打一次真实 gRPC</b>，不能只看 {@code jvm_*} 就完事</h2>
 * <p>{@code MessageMetrics} 的 meter 是<b>懒注册</b>的
 * （{@code service/message/message-rpc/src/main/java/com/rag/message/message/rpc/metrics/MessageMetrics.java:79}
 * 的 {@code Counter.builder(...).register(registry)} 在 {@code observeRpc} <b>内部</b>）
 * ⇒ ⇒ <b>零请求的新进程</b>的 {@code /actuator/prometheus} 上<b>没有</b> {@code message_rpc_request_total}
 * ⇒ ⇒ 只断言 {@code jvm_*} 会**漏掉**「本域指标抓不到」这一半。
 * <p>⇒ 故本类真的调一次 {@code ListConversations}
 * （{@code MessageDomainService.listConversations} 的 {@code finally} 里
 * <b>必然</b>调 {@code metrics.observeRpc("ListConversations", …)} —— 成功或失败都记）
 * ⇒ ⇒ 调用<b>失败也无所谓</b>，要的是那个 meter <b>被注册</b>。
 *
 * <h2>本类<b>刻意不做</b>的事</h2>
 * <ul>
 *   <li>❌ <b>不覆盖</b> {@code grpc.client.*.address} ⇒ 生产地址原样生效
 *       （覆盖了就等于把被测对象换掉了）。</li>
 *   <li>❌ <b>不覆盖</b> Nacos / Kafka 的地址 ⇒ 只把它们指向<b>不可达</b>地址
 *       （Nacos 注册失败只记日志、不中断启动 —— 与 Go 侧 etcd 注册失败同样只打日志）。</li>
 *   <li>⚠️ 观测口用 <b>env 覆盖</b>（{@code MESSAGE_RPC_MANAGEMENT_PORT}）⇒
 *       <b>不占用默认口 16067</b> ⇒ 与其它 lane / 并行起互不撞口
 *       （本仓既有约定，见 {@code service/follow/follow-rpc/src/main/resources/application.yml:129}）。
 *       ⇒ 默认值 16067 由上面第 ① 条<b>配置层</b>断言守住。</li>
 * </ul>
 *
 * <h2>环境依赖</h2>
 * <p>需要 Docker（真 PG 容器）⇒ 无 Docker 时整类 skip
 * （Testcontainers 的 {@code disabledWithoutDocker}）。
 */
@Testcontainers(disabledWithoutDocker = true)
class MessageRpcMetricsCarrierGateTest {

    /**
     * Go 侧的观测端口 —— 真源 {@code service/message/rpc/etc/message.yaml:22} 的 {@code DevServer.Port: 16067}。
     *
     * <p>⚠️ 这是<b>本仓的 Go 真源</b>（冻结、只读），本测试**不读**那个仓
     * ⇒ ⇒ 数值写死在本类里（可 grep、可复算），出处写在 javadoc 与台账里。
     */
    private static final int GO_DEVSERVER_PORT = 16067;

    /** 生产 yaml 里 {@code server.port} 的占位符变量名（见 {@code application.yml} 的 {@code server:} 段）。 */
    private static final String MANAGEMENT_PORT_ENV = "MESSAGE_RPC_MANAGEMENT_PORT";

    /** Tomcat 启动日志的标志串（实测 {@code spring-boot-tomcat-4.0.8} 的常量 {@code "Tomcat started on \u0001\u0001"}）。 */
    private static final String TOMCAT_STARTED_MARKER = "Tomcat started on";

    /** devh 打印「gRPC server 真的起来了」那行的标志串。 */
    private static final String GRPC_STARTED_MARKER = "gRPC Server started";

    /** 本域自己的 meter 名（真源 {@code MessageMetrics.java:45} = {@code "message_rpc_request_total"}）。 */
    private static final String DOMAIN_METRIC_NAME = "message_rpc_request_total";

    /**
     * Go 侧的直方图桶边界 —— 真源
     * {@code service/message/rpc/internal/metrics/metrics.go:23} 的 {@code Buckets: prometheus.DefBuckets}
     * ⇒ {@code client_golang@v1.23.2/prometheus/histogram.go:271}
     * {@code var DefBuckets = []float64{.005, .01, .025, .05, .1, .25, .5, 1, 2.5, 5, 10}}。
     */
    private static final List<Double> GO_DEF_BUCKETS = List.of(
            0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0);

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("first_db")
            .withUsername("admin")
            .withPassword("password");

    /**
     * 本类的真容器<b>固定宿主口</b>（message 域段 22760–22789 内 ·
     * 全局段表 §4.10.19.3 / §4.10.39.2 分配）。
     *
     * <p>🔴 为什么不用 Testcontainers 的默认随机口：随机宿主口落在 <b>OS 临时段 49152–65535</b>
     * （= Docker published 段）⇒ ⇒ 与 {@code tools/difftest/ddl-parity.sh} 的固定口 55560–55569
     * 同属一个池子 ⇒ ⇒ 全局段表把该段标为<b>禁占</b>。
     */
    private static final int FIXED_HOST_PORT = 22770;

    static {
        RealContainerPort.withFixedHostPort(PG, FIXED_HOST_PORT, RealContainerPort.PG_CONTAINER_PORT);
    }

    // ==================================================================
    // ① 配置层：默认观测口必须与 Go 一字不差
    // ==================================================================

    @Test
    @DisplayName("生产 yaml 的 server.port 默认值必须等于 Go 的 DevServer.Port（16067）")
    void defaultObservationPortMatchesGoDevServerPort() throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"));
        assertTrue(!sources.isEmpty(), "classpath 上的 application.yml 读不到（生产 yaml 必须在测试 classpath 上）");

        String raw = null;
        for (PropertySource<?> ps : sources) {
            if (ps.containsProperty("server.port")) {
                raw = String.valueOf(ps.getProperty("server.port"));
            }
        }
        assertNotNull(raw, "生产 yaml 里没有 server.port ⇒ 观测口没有载体");

        // ⚠️ **用「空的环境变量集」解析** ⇒ 解析出来的就是**默认值**
        //    （env 未设 ⇒ 占位符落回默认值 ⇒ 这正是生产默认形态）。
        //    ⚠️ 用 `java.util.Properties`（不是 `PropertySources`）：本仓 Spring 版本里
        //    `PropertyPlaceholderHelper#replacePlaceholders` 只接受
        //    `Properties` / `PlaceholderResolver` 两种入参。
        Properties noEnv = new Properties();
        String resolved = new PropertyPlaceholderHelper("${", "}", ":", null, true)
                .replacePlaceholders(raw, noEnv);

        assertEquals(String.valueOf(GO_DEVSERVER_PORT), resolved,
                "生产 yaml 的 server.port 默认值 = " + resolved
                        + "，但 Go 的 DevServer.Port = " + GO_DEVSERVER_PORT
                        + "（真源 service/message/rpc/etc/message.yaml:22）⇒ 端口必须一字不差。"
                        + " 原始值：" + raw);
        assertTrue(raw.contains(MANAGEMENT_PORT_ENV),
                "server.port 应当能用 " + MANAGEMENT_PORT_ENV + " 覆盖（并行起 / 探针要换口）⇒ 原始值：" + raw);
    }

    // ==================================================================
    // ② + ③ 运行层：真进程 + 真抓取
    // ==================================================================

    @Test
    @DisplayName("真 fat jar 起来后：/actuator/prometheus 能抓到 RAG 侧指标名，且 gRPC 端口不受影响")
    void realFatJarExposesPrometheusAndKeepsGrpcAlive() throws Exception {
        Carrier carrier = bootCarrier(false);
        Process process = carrier.process;
        StringBuffer log = carrier.log;
        int grpcPort = carrier.grpcPort;
        int mgmtPort = carrier.mgmtPort;

        try {
            // ---- 判据 ②：web 容器真的在（不是「配置里写了」） ----
            boolean tomcatUp = awaitLog(process, log, TOMCAT_STARTED_MARKER, 180);
            assertTrue(tomcatUp,
                    "日志里没有「" + TOMCAT_STARTED_MARKER + "」⇒ **web 容器没起来**"
                            + " ⇒ 载体仍然不存在（多半是 pom 缺 spring-boot-starter-web）。日志尾部：\n" + tail(log.toString()));

            // ---- gRPC 端口在监听（载体的存在**不许**破坏 gRPC） ----
            boolean grpcUp = awaitPort(grpcPort, process, 120);
            assertTrue(grpcUp,
                    "gRPC 端口 " + grpcPort + " 没有在监听 ⇒ 引入载体**破坏**了 gRPC 侧。"
                            + "日志尾部：\n" + tail(log.toString()));
            assertTrue(log.toString().contains(GRPC_STARTED_MARKER),
                    "日志里没有 devh 的「" + GRPC_STARTED_MARKER + "」⇒ 不能算真 gRPC server 起来了。日志尾部：\n"
                            + tail(log.toString()));

            // ---- 判据 ③：真 GET /actuator/prometheus ⇒ 200 + 非空 + 含 RAG 侧指标名 ----
            HttpResponse<String> first = awaitPrometheus(mgmtPort, process, 120);
            assertEquals(200, first.statusCode(),
                    "/actuator/prometheus 返回 " + first.statusCode() + "（期望 200）⇒ 抓取出口仍然是坏的。日志尾部：\n"
                            + tail(log.toString()));
            assertTrue(!first.body().isBlank(),
                    "/actuator/prometheus 响应体是空的 ⇒ **载体没真正提供内容**。日志尾部：\n"
                            + tail(log.toString()));
            assertTrue(first.body().contains("jvm_memory_used_bytes"),
                    "响应体里连 JVM 指标都没有 ⇒ 这不是本进程的 Prometheus 文本。响应体前 800 字符：\n"
                            + first.body().substring(0, Math.min(800, first.body().length())));

            // ---- 🔴 关键一半：**本域自己的 meter** 也要抓得到 ----
            //     （meter 是懒注册的 ⇒ 必须先打一次真实 gRPC 调用）
            boolean domainMetricSeen = false;
            for (int attempt = 0; attempt < 20 && !domainMetricSeen; attempt++) {
                invokeOneRpc(grpcPort);
                HttpResponse<String> after = awaitPrometheus(mgmtPort, process, 30);
                domainMetricSeen = after.statusCode() == 200 && after.body().contains(DOMAIN_METRIC_NAME);
            }
            assertTrue(domainMetricSeen,
                    "打过真实 gRPC 调用之后，/actuator/prometheus 里仍**没有** " + DOMAIN_METRIC_NAME
                            + " ⇒ 「指标注册了但抓不到」这个缺陷依然存在（或本域 meter 的注册点被改坏了）。"
                            + "日志尾部：\n" + tail(log.toString()));

            // ---- 🔴 直方图桶边界必须逐个等于 Go 的 `prometheus.DefBuckets` ----
            //     （这是**语义**差异不是表达层差异：同一段 `histogram_quantile()` 两侧结果不同）
            HttpResponse<String> withBuckets = awaitPrometheus(mgmtPort, process, 30);
            assertEquals(200, withBuckets.statusCode(), "/actuator/prometheus 抓取失败");
            List<Double> observed = observedLeBoundaries(withBuckets.body(), "message_rpc_request_duration_seconds");
            assertEquals(GO_DEF_BUCKETS, observed,
                    "直方图桶边界与 Go 的 `prometheus.DefBuckets` 不一致 ⇒ 同一段 PromQL"
                            + "（`histogram_quantile(0.95, sum(rate(message_rpc_request_duration_seconds_bucket[5m])) by (le))`）"
                            + " 在两侧**给出不同的分位数** ⇒ 监控语义不等价。"
                            + " Go 真源：service/message/rpc/internal/metrics/metrics.go:23"
                            + " `Buckets: prometheus.DefBuckets`"
                            + " ⇒ client_golang@v1.23.2/prometheus/histogram.go:271");

            // ---- 两个端口**都**还活着（抓取不许把 gRPC 顶掉） ----
            assertTrue(tcpProbe(grpcPort), "抓取之后 gRPC 端口 " + grpcPort + " 不在了 ⇒ 载体影响了业务面");
            assertTrue(tcpProbe(mgmtPort), "抓取之后观测口 " + mgmtPort + " 不在了");
        } finally {
            carrier.stop();
        }
    }

    // ==================================================================
    // ④ 进程形态（2026-10-30 · 合并收口执行者派工）：rpc 侧不许挂 api 侧那套 rest 中间件
    // ==================================================================

    /**
     * Go 侧 rpc 进程上**根本不存在**的 HTTP 中间件（逐条列名）⇒ 一个都不许出现在本模块容器里。
     *
     * <p>🔴 <b>为什么这条是硬要求</b>：Go 的 rpc 侧走 {@code zrpc.MustNewServer(...)}
     * ⇒ 服务端那条链（trace / recover / stat / prometheus / breaker / shedding）是
     * <b>gRPC interceptor</b>，<b>不是</b> net/http 的 rest middleware
     * ⇒ ⇒ Go 的 rpc 进程上**不存在</b> maxConns / maxBytes / cpuShedding / breaker 这四层 HTTP 保护。
     * 而本模块补了 servlet 容器 ⇒ 共享层的 web 侧组件会在 rpc 进程里**首次激活**
     * ⇒ ⇒ 若不限定形态，观测口上会挂着 Go 侧**没有**的中间件。
     *
     * <p>⚠️ 其中 {@code GoStyleCpuSheddingFilter}（共享层 {@code service/common/.../protection/GoStyleCpuSheddingFilter.java:61}）
     * <b>默认 enabled=true</b>（同文件 {@code GoStyleProtectionProperties} 的默认值即 go-zero 的 tag 默认值）
     * ⇒ 它 {@code response.setStatus(503); return;} ⇒ ⇒ <b>直接威胁载体存在的目的</b>：
     * 抓取请求被拒 ⇒ 载体装了但抓不到。
     */
    private static final List<String> API_SIDE_HTTP_MIDDLEWARE = List.of(
            "GoStyleCpuSheddingFilter",
            "GoStyleMaxConnsFilter",
            "GoStyleMaxBytesFilter",
            "GoStyleBreakerFilter",
            "GoStyleTraceparentFilter",
            "GoStyleFormValueLimitInterceptor",
            "GoStyleFormLimitWebMvcConfig",
            "GoStyleTomcatParameterLimitCustomizer");

    /**
     * 共享层用 {@code rag.side} 门控的 api 侧<b>自动配置</b>（G4 的共享层落码：
     * {@code align/g4-service-common} 的 {@code e8cb769a}）。
     *
     * <p>⚠️ 这一组与上面那组性质不同：它们是 {@code @AutoConfiguration}
     * ⇒ <b>不受「rpc 进程不扫 {@code com.rag.common}」这条约定保护</b>
     * ⇒ 只要有 servlet 容器就注册 ⇒ ⇒ 必须由共享层的 {@code rag.side} 门来关。
     */
    private static final List<String> API_SIDE_SIDE_GATED_AUTO_CONFIGURATIONS = List.of(
            "GoStylePathNormalizingAutoConfiguration",
            "GoStyleJsonContentTypeAdvice",
            "GoStyleBreakerRouteTemplateAutoConfiguration");

    @Test
    @DisplayName("rpc 形态 ⇒ Go 侧不存在的 api 侧 HTTP 中间件（CPU 降载等）一个都不许在 rpc 容器里")
    void rpcSideMustNotCarryApiSideHttpMiddleware() throws Exception {
        Carrier carrier = bootCarrier(true);
        try {
            assertTrue(awaitLog(carrier.process, carrier.log, TOMCAT_STARTED_MARKER, 180),
                    "日志里没有「" + TOMCAT_STARTED_MARKER + "」⇒ **web 容器没起来** ⇒ 谈不上中间件形态。"
                            + "日志尾部：\n" + tail(carrier.log.toString()));

            HttpResponse<String> beans = awaitEndpoint(carrier.mgmtPort, "/actuator/beans", carrier.process, 120);
            assertEquals(200, beans.statusCode(),
                    "/actuator/beans 返回 " + beans.statusCode() + " ⇒ 读不到容器里的 Bean 清单 ⇒ 本断言无法判定。日志尾部：\n"
                            + tail(carrier.log.toString()));

            List<String> present = API_SIDE_HTTP_MIDDLEWARE.stream()
                    .filter(name -> beans.body().contains(name))
                    .toList();
            assertTrue(present.isEmpty(),
                    "🔴 rpc 观测口上挂着 Go 侧**根本不存在**的 api 侧 HTTP 中间件：" + present
                            + " ⇒ ① CPU 降载（GoStyleCpuSheddingFilter，默认 enabled=true）会返 503 拒请求"
                            + " ⇒ **直接威胁载体本身**（抓取被拒 ⇒ 装了但抓不到）；"
                            + " ② 与 Go 的「rpc 侧零 rest 中间件」**不同形**。"
                            + " 修法：共享层用 `rag.side` 门控 api 侧中间件（G4 写权面）"
                            + " ＋ 本模块声明 `rag.side: rpc`（本模块 yml，已落）。");

            // ---- 「yml 里写了」**不等于**「运行时生效」⇒ 必须读运行期属性表 ----
            HttpResponse<String> env = awaitEndpoint(carrier.mgmtPort, "/actuator/env?show-values=always",
                    carrier.process, 60);
            assertEquals(200, env.statusCode(),
                    "/actuator/env 返回 " + env.statusCode() + " ⇒ 读不到运行期属性表 ⇒ 本断言无法判定。");
            int idx = env.body().indexOf("\"rag.side\"");
            assertTrue(idx >= 0,
                    "🔴 运行期属性表里**没有** `rag.side` ⇒ 本模块的形态声明**没进运行时**"
                            + " ⇒ 共享层的门（`@ConditionalOnProperty(name=\"rag.side\")`）拿不到值"
                            + " ⇒ ⇒ 共享层的门形同虚设。");
            String fragment = env.body().substring(idx, Math.min(env.body().length(), idx + 200));
            assertTrue(fragment.contains("\"rpc\""),
                    "🔴 运行期 `rag.side` 的值**不是** `rpc` ⇒ 本模块的形态声明没生效 ⇒ 片段：" + fragment);
            assertTrue(fragment.contains("application.yml"),
                    "🔴 运行期 `rag.side` 的来源**不是生产 application.yml** ⇒ 片段：" + fragment);
        } finally {
            carrier.stop();
        }
    }

    @Test
    @DisplayName("rag.side=rpc ⇒ 共享层已门控的 api 侧自动配置不注册（共享层的门落码后自动变强制断言）")
    void apiSideAutoConfigurationsMustNotRegisterWhenSideIsRpc() throws Exception {
        if (!sharedLayerHasSideGate()) {
            Assumptions.assumeTrue(false,
                    "共享层尚未落 `rag.side` 门（那是 G4 的共享层写权面，落码在 `align/g4-service-common:e8cb769a`，"
                            + "未合入本分支）⇒ ⇒ 本断言**暂不适用**（skip ≠ 通过）"
                            + " ⇒ 已登记在 `docs/DRIFT/message/rpc.md` 的「跨 lane 依赖」小节。");
            return;
        }
        Carrier carrier = bootCarrier(true);
        try {
            HttpResponse<String> beans = awaitEndpoint(carrier.mgmtPort, "/actuator/beans", carrier.process, 120);
            assertEquals(200, beans.statusCode(), "/actuator/beans 返回 " + beans.statusCode());
            List<String> present = API_SIDE_SIDE_GATED_AUTO_CONFIGURATIONS.stream()
                    .filter(name -> beans.body().contains(name))
                    .toList();
            assertTrue(present.isEmpty(),
                    "🔴 共享层已落 `rag.side` 门，但本模块的 rpc 容器里**仍注册**了 api 侧自动配置：" + present
                            + " ⇒ ⇒ 本模块的 `rag.side: rpc` 没生效（或共享层的门没吃到这个值）。");
        } finally {
            carrier.stop();
        }
    }

    /**
     * 共享层**是否已经**落 {@code rag.side} 门（反射读注解，不读源码、不猜）。
     *
     * <p>⚠️ 为什么用反射判定而不是「直接断言」：共享层是 <b>G4 的写权面</b>
     * ⇒ 本分支上**还没有**那个门 ⇒ 若直接断言「自动配置不注册」，本分支的守门会**恒红**
     * ⇒ ⇒ 而恒红的守门等于没有守门（跑绿 ≠ 对，但恒红同样不可用）。
     * ⇒ ⇒ 这里的做法是：**先判机制在不在**（反射读 {@code @ConditionalOnProperty}）
     * ⇒ 机制在 ⇒ 断言必须成立（真断言）；机制不在 ⇒ <b>显式 skip 并写明原因</b>
     * ⇒ ⇒ 机制落进本分支的那一刻起，这条断言自动变成强制判据。
     */
    private static boolean sharedLayerHasSideGate() {
        ConditionalOnProperty cond = GoStylePathNormalizingAutoConfiguration.class
                .getAnnotation(ConditionalOnProperty.class);
        if (cond == null) {
            return false;
        }
        for (String n : cond.name()) {
            if ("rag.side".equals(n)) {
                return true;
            }
        }
        return false;
    }

    // ==================================================================
    // helpers
    // ==================================================================

    /** 真进程的运行现场（进程 + 端口 + 日志缓冲）。 */
    private static final class Carrier {
        final Process process;
        final StringBuffer log;
        final int grpcPort;
        final int mgmtPort;
        final Thread drain;

        Carrier(Process process, StringBuffer log, int grpcPort, int mgmtPort, Thread drain) {
            this.process = process;
            this.log = log;
            this.grpcPort = grpcPort;
            this.mgmtPort = mgmtPort;
            this.drain = drain;
        }

        void stop() throws InterruptedException {
            process.destroyForcibly();
            process.waitFor(30, TimeUnit.SECONDS);
            drain.join(TimeUnit.SECONDS.toMillis(10));
        }
    }

    /**
     * 起一个真 fat jar（生产 classpath ＋ 生产 yaml）。
     *
     * @param exposeBeanEndpoints 是否额外暴露 {@code /actuator/beans} 与 {@code /actuator/env}
     *                             ⇒ <b>仅测试进程</b>用（读容器里的 Bean 清单与运行期属性表）；
     *                             生产 yml 的 exposure 仍是 {@code health,info,prometheus,metrics}
     *                             ⇒ ⇒ <b>生产暴露面不因测试而扩大</b>。
     */
    private static Carrier bootCarrier(boolean exposeBeanEndpoints) throws Exception {
        Path jar = MessageRpcArtifactClasspathGateTest.fatJar();
        int grpcPort = GrpcTestPort.freePort();
        int mgmtPort = GrpcTestPort.freePort();

        List<String> command = new ArrayList<>(List.of(
                "java", "-jar", jar.toAbsolutePath().toString(),
                "--grpc.server.port=" + grpcPort));
        if (exposeBeanEndpoints) {
            command.add("--management.endpoints.web.exposure.include=health,info,prometheus,metrics,beans,env");
            // ⚠️ env 端点**默认把值打码**成 `******`（`management.endpoint.env.show-values` 默认 `never`
            //    ⇒ 实测第一次跑就踩到了：属性**在**运行期属性表里，但值读不出来）
            //    ⇒ ⇒ 这里**只在测试进程**里打开 ALWAYS ⇒ 生产 yaml 不变（生产面不扩大）。
            command.add("--management.endpoint.env.show-values=ALWAYS");
        }
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(MessageRpcArtifactClasspathGateTest.repoRoot()
                        .resolve(MessageRpcArtifactClasspathGateTest.MODULE_PATH).toFile())
                .redirectErrorStream(true);

        Map<String, String> env = builder.environment();
        env.put("POSTGRES_HOST", "127.0.0.1");
        env.put("POSTGRES_PORT", String.valueOf(PG.getFirstMappedPort()));
        env.put("POSTGRES_DB", "first_db");
        env.put("POSTGRES_USER", "admin");
        env.put("POSTGRES_PASSWORD", "password");
        // ⚠️ 下面几个指向**不可达**地址：本类只验「本模块自己的配置与 classpath 能不能起」，
        //    不验「依赖在线时的启动面」（那属于依赖面，不是载体面）。
        env.put("NACOS_ADDR", "127.0.0.1:1");
        env.put("KAFKA_BROKERS", "127.0.0.1:9092");
        env.put(MANAGEMENT_PORT_ENV, String.valueOf(mgmtPort));

        Process process = builder.start();
        StringBuffer log = new StringBuffer();
        Thread drain = new Thread(() -> drain(process.getInputStream(), log));
        drain.setDaemon(true);
        drain.start();
        return new Carrier(process, log, grpcPort, mgmtPort, drain);
    }

    /** 真 GET 某个 Actuator 端点（上限内；返回最后一次结果）。 */
    private static HttpResponse<String> awaitEndpoint(int port, String path, Process process, int timeoutSeconds)
            throws InterruptedException {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
        HttpResponse<String> last = null;
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSeconds);
        while (System.currentTimeMillis() < deadline) {
            try {
                last = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (last.statusCode() == 200) {
                    return last;
                }
            } catch (IOException retryable) {
                // 还没起来 ⇒ 继续等
            }
            if (!process.isAlive()) {
                break;
            }
            Thread.sleep(500);
        }
        return last;
    }

    /** 打一次真实 gRPC 调用（成功或失败都无所谓 —— 要的是 meter 被注册）。 */
    private static void invokeOneRpc(int grpcPort) throws Exception {
        ManagedChannel channel = io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
                .forTarget("dns:///127.0.0.1:" + grpcPort).usePlaintext().build();
        try {
            MessageServiceGrpc.newBlockingStub(channel)
                    .withDeadlineAfter(20, TimeUnit.SECONDS)
                    .listConversations(ConversationListReq.newBuilder().setOperatorId(1L).build());
        } catch (StatusRuntimeException expected) {
            // 🔴 **失败是预期内的**（DB 里没有该用户的会话 / 角色校验不过）
            //    ⇒ 关键是 `MessageDomainService.listConversations` 的 finally 里
            //    **一定会**调 `metrics.observeRpc(...)` ⇒ meter 被注册。
        } finally {
            channel.shutdownNow();
            channel.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    /** 等日志里出现某个标志串（上限内）。 */
    private static boolean awaitLog(Process process, StringBuffer log, String marker, int timeoutSeconds)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSeconds);
        while (System.currentTimeMillis() < deadline) {
            if (log.toString().contains(marker)) {
                return true;
            }
            if (!process.isAlive()) {
                break;
            }
            Thread.sleep(200);
        }
        return log.toString().contains(marker);
    }

    /** 等某个 TCP 端口在监听（上限内）。 */
    private static boolean awaitPort(int port, Process process, int timeoutSeconds)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSeconds);
        while (System.currentTimeMillis() < deadline) {
            if (tcpProbe(port)) {
                return true;
            }
            if (!process.isAlive()) {
                break;
            }
            Thread.sleep(500);
        }
        return tcpProbe(port);
    }

    /** 真 GET /actuator/prometheus（上限内；返回最后一次结果）。 */
    private static HttpResponse<String> awaitPrometheus(int port, Process process, int timeoutSeconds)
            throws InterruptedException {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/actuator/prometheus"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> last = null;
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSeconds);
        while (System.currentTimeMillis() < deadline) {
            try {
                last = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (last.statusCode() == 200) {
                    return last;
                }
            } catch (IOException retryable) {
                // 还没起来 ⇒ 继续等
            }
            if (!process.isAlive()) {
                break;
            }
            Thread.sleep(500);
        }
        return last;
    }

    /** TCP 连通性探测：连得上 ⇒ 该端口有东西在监听。 */
    private static boolean tcpProbe(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new java.net.InetSocketAddress("127.0.0.1", port), 2000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void drain(InputStream in, StringBuffer sink) {
        try (InputStream stream = in) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                sink.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
            }
        } catch (IOException ignore) {
            // 进程结束时的 IOException 属正常
        }
    }

    /**
     * 从 Prometheus 文本里抽出某个直方图 meter 的 {@code le} 边界（升序，<b>不含 +Inf</b>）。
     *
     * <p>⚠️ <b>按浮点数值比对</b>（不是按文本）：Java 的 {@code double.toString} 会把
     * {@code 1} 打成 {@code 1.0}、{@code 5} 打成 {@code 5.0} ⇒ 文本不同但
     * <b>Prometheus 按浮点解析 {@code le} ⇒ 同一个桶</b> ⇒ 语义等价
     * （这是 follow 域已登记的 P3/D 类文本差异，见
     * {@code docs/DRIFT/follow/rpc.md} 的「残余差异登记」第 4 条）。
     */
    private static List<Double> observedLeBoundaries(String prometheusText, String meterName) {
        String prefix = meterName + "_bucket{";
        List<Double> les = new ArrayList<>();
        for (String line : prometheusText.split("\n")) {
            if (!line.startsWith(prefix)) {
                continue;
            }
            int leIdx = line.indexOf(",le=\"");
            if (leIdx < 0) {
                continue;
            }
            // ⚠️ `",le=\""` 是 **5 个字符**（`,` `l` `e` `=` `"`）⇒ 取值从 `leIdx + 5` 开始。
            int end = line.indexOf('"', leIdx + 5);
            if (end < 0) {
                continue;
            }
            String le = line.substring(leIdx + 5, end);
            if ("+Inf".equals(le)) {
                continue;
            }
            les.add(Double.parseDouble(le));
        }
        List<Double> sorted = new ArrayList<>(new java.util.TreeSet<>(les));
        return sorted;
    }

    /** 日志尾部（失败时给人看的证据）。 */
    private static String tail(String text) {
        int max = 6000;
        return text.length() <= max
                ? text
                : "……（前 " + (text.length() - max) + " 字符已省略）……\n" + text.substring(text.length() - max);
    }
}
