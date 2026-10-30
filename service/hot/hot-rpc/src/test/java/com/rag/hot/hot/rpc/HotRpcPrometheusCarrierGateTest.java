package com.rag.hot.hot.rpc;

import com.rag.hot.hot.rpc.support.RealGrpcPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴🔴 <b>Actuator 载体真启动守门</b>（进程级）—— 用<b>真 fat jar</b> 起本模块的<b>真进程</b>，
 * 断言<b>观测面真的存在</b>（{@code /actuator/prometheus} 真能抓出指标），
 * 且<b>业务面（gRPC）不受影响</b>（两个口同时在监听）。
 *
 * <h2>它守的是哪几类「不报错」的缺陷</h2>
 * <ol>
 *   <li><b>缺 {@code spring-boot-starter-web}</b> ⇒ {@code spring-boot-starter-actuator}
 *       只给 endpoint 与自动配置、<b>不给 servlet 容器</b> ⇒ WebApplicationType 推导出 NONE
 *       ⇒ {@code management.endpoints.web.exposure.include} 这一族配置<b>静默不加载</b>
 *       ⇒ {@code /actuator/prometheus} <b>没有出口</b> ⇒ <b>没有任何报错</b>；</li>
 *   <li><b>观测面被显式关死</b>（本模块 2026-10-30 之前正是如此：
 *       {@code management.server.port: -1} + {@code management.endpoints.enabled-by-default: false}
 *       + {@code management.prometheus.enabled: false}）⇒ 三处都是<b>静默失效</b>
 *       ⇒ ⇒ 而且第三处<b>本来就不是 Boot 的属性名</b>（Boot 里是
 *       {@code management.endpoint.prometheus.enabled} 与
 *       {@code management.prometheus.metrics.export.enabled}）⇒ 它<b>从来没起过作用</b>
 *       ⇒ ⇒ 这正是本类第 ① 条断言要抓的；</li>
 *   <li><b>观测面与业务面撞端口</b> ⇒ 业务端口绑不上 ⇒ 启动期才炸；</li>
 *   <li><b>自动 Nacos 注册被激活</b>（{@code NacosAutoServiceRegistration} 监听
 *       {@code WebServerInitializedEvent}）⇒ 它注册的是 <b>web 容器端口</b>（= 19091）
 *       ⇒ Nacos 里出现「hot-rpc → 19091」这条<b>错端口</b>实例
 *       ⇒ hot-api 连过去只拿到 {@code /actuator/**} ⇒ <b>gRPC 链路断</b>；
 *       ⇒ Nacos 不可达时更狠：{@code failFast=true} ⇒ <b>直接把启动打断</b>
 *       ⇒ ⇒ <b>这正是本轮 hot-rpc 真启动实测撞到的那一条</b>：
 *       {@code Failed to start bean 'webServerStartStop'}。</li>
 * </ol>
 *
 * <h2>为什么不能用「mvn test 的 classpath」当证据</h2>
 * <p>测试跑在 <b>test classpath</b> 上 ⇒ {@code spring-boot-starter-web} 本来就在
 * ⇒ 上面第 ① 类缺陷在测试里<b>永远测不到</b>（AGENTS.md §3.3 的「跑绿 ≠ 对」）
 * ⇒ ⇒ 所以本类用 {@code java -jar} 起<b>真 fat jar</b>（生产 classpath + 生产 yaml）。
 *
 * <h2>Go 出处（真源 · 消费点）</h2>
 * <p>{@code service/hot/rpc/etc/hot.yaml:8-11}
 * {@code Prometheus: {Host: 0.0.0.0, Port: 19091, Path: /metrics}}
 * ⇒ {@code go-zero@v1.10.2/zrpc/config.go:39} {@code RpcServerConf} <b>内嵌</b>
 * {@code service.ServiceConf}
 * ⇒ {@code go-zero@v1.10.2/core/service/serviceconf.go:38} {@code ServiceConf.Prometheus} 字段
 * ⇒ ⇒ <b>消费点</b> {@code go-zero@v1.10.2/zrpc/server.go:63} {@code c.SetUp()}
 * ⇒ ⇒ {@code core/service/serviceconf.go:62} {@code prometheus.StartAgent(sc.Prometheus)}
 * ⇒ ⇒ {@code core/prometheus/agent.go:31}（Host 为空 ⇒ 直接 return，不启动）·
 * {@code :38} {@code http.Handle(c.Path, promhttp.Handler())} ·
 * {@code :39-41} <b>独立端口</b> {@code http.ListenAndServe(addr, nil)}
 * ⇒ ⇒ Go 侧 hot-rpc <b>真的</b>在 {@code 0.0.0.0:19091} 起了一个 HTTP 服务（路径 {@code /metrics}）。
 *
 * <p>⚠️ <b>别被一个「长得像」的文件带偏</b>：{@code service/hot/rpc/internal/metrics/metrics.go}
 * 里那段起 metrics HTTP 服务的代码（硬编码 {@code :9091}）是<b>死代码</b>
 * （{@code service/hot/rpc/hot.go} 全文没调用它）⇒ hot-rpc 的观测端口是 <b>19091</b>，不是 9091。
 *
 * <h2>语义等价论证</h2>
 * <ul>
 *   <li><b>业务面</b>：两侧都是纯 gRPC，端口一字不差（Go {@code ListenOn: 0.0.0.0:8083}
 *       ↔ RAG {@code grpc.server.port: 8083}）⇒ 一致；</li>
 *   <li><b>观测面</b>：两侧都是<b>独立端口</b>上的 HTTP 服务（19091）⇒ 一致；
 *       端口<b>一字不差</b> ⇒ 抓取侧不用改端口；</li>
 *   <li>⚠️ <b>载体与路径不同</b>：Go = go-zero 自带的 {@code http.ListenAndServe} 小 server
 *       （{@code /metrics}）；RAG = Tomcat 上的 Actuator（{@code /actuator/prometheus}）
 *       ⇒ 属 twoStage.md §10「暴露形态差异」⇒ <b>不</b>在本条里当缺陷。</li>
 * </ul>
 *
 * <h2>本类<b>刻意不做</b>的事</h2>
 * <ul>
 *   <li>❌ <b>不覆盖</b> {@code grpc.client.*.address} ⇒ 生产地址原样生效；</li>
 *   <li>❌ <b>不覆盖</b> Nacos / Kafka / Redis 的地址 ⇒ 只把它们指向<b>不可达</b>的地址
 *       ⇒ <b>这正是第 ④ 类缺陷的判据</b>：自动注册若被激活，Nacos 不可达时
 *       <b>启动会被 failFast 打断</b> ⇒ 「进程照样起来」本身就是「自动注册没被激活」的证据；</li>
 *   <li>⚠️ <b>观测端口用参数覆盖</b>成空闲口（并行跑的探针要换口），
 *       <b>默认值仍由第 ② 条断言钉死</b>（= 19091）⇒ 两条合起来才完整。</li>
 * </ul>
 *
 * <h2>环境依赖</h2>
 * <p>🔴 <b>本类不需要 Docker</b>：hot-rpc 的观测面不依赖数据库 ⇒ ⇒ 无 Docker 也能跑
 * （这也是它比 article / favorite 同款门「更轻」的原因）。
 */
class HotRpcPrometheusCarrierGateTest {

    /** devh 打印「gRPC Server started」的那一行标志串（提为常量 ⇒ 等待与断言共用一个定义）。 */
    private static final String GRPC_STARTED_MARKER = "gRPC Server started";

    /** Tomcat 打印「Tomcat started on port N」的那一行标志串（证明载体真的是 servlet 容器）。 */
    private static final String TOMCAT_STARTED_MARKER = "Tomcat started on port";

    /** Go 的观测端口（{@code etc/hot.yaml:10} {@code Prometheus.Port}）。 */
    private static final int GO_OBSERVATION_PORT = 19091;

    /** Go 的业务端口（{@code etc/hot.yaml:2} {@code ListenOn: 0.0.0.0:8083}）。 */
    private static final int GO_BUSINESS_PORT = 8083;

    /** Actuator 的公共标签值（= {@code management.metrics.tags.application}）。 */
    private static final String APPLICATION_TAG = "hot-rpc";

    /** 本模块目录（相对仓库根）。 */
    private static final String MODULE_PATH = "service/hot/hot-rpc";

    // ==================================================================
    // ① 行为级：载体真的存在，且业务面不受影响
    // ==================================================================

    @Test
    @DisplayName("真 fat jar 起真 HotRpcApplication：/actuator/prometheus 真能抓出指标，且 gRPC 端口同时在监听")
    void carrierServesMetricsWhileGrpcKeepsListening() throws Exception {
        Path jar = fatJar();
        // 🔴 两个面必须占**不同的口**：观测面（Actuator 容器）与业务面（gRPC server）
        //    ⇒ 挑第二个时把第一个排除掉（否则两次 pick() 可能挑到同一个空口 ⇒ EADDRINUSE 偶发红）。
        int grpcPort = RealGrpcPort.pick();
        int observationPort = RealGrpcPort.pick(grpcPort);

        List<String> command = new ArrayList<>(List.of(
                "java", "-jar", jar.toAbsolutePath().toString(),
                "--grpc.server.port=" + grpcPort,
                "--server.port=" + observationPort));
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(repoRoot().resolve(MODULE_PATH).toFile())
                .redirectErrorStream(true);

        Map<String, String> env = builder.environment();
        // ⚠️ 三个依赖全部指向**不可达**地址 ⇒ 把「依赖在线」这个变量固定住。
        //    ⚠️ 其中 Nacos 不可达**就是**「自动注册没被激活」的判据 ——
        //    自动注册若生效，failFast 会直接把启动打断（hot-rpc 本轮实测复现过）。
        env.put("NACOS_ADDR", "127.0.0.1:1");
        env.put("KAFKA_BROKERS", "127.0.0.1:19092");
        env.put("REDIS_HOST", "127.0.0.1");
        env.put("REDIS_PORT", "6399");

        Process process = builder.start();
        // ⚠️ 用 StringBuffer（同步）而不是 StringBuilder：sink 会被「drain 线程」写、
        //    被「测试线程」读 ⇒ StringBuilder 不是线程安全的。
        StringBuffer log = new StringBuffer();
        Thread drain = new Thread(() -> drain(process.getInputStream(), log));
        drain.setDaemon(true);
        drain.start();

        String scrapeBody = null;
        boolean grpcListening = false;
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(180);
        while (System.currentTimeMillis() < deadline) {
            if (!grpcListening) {
                grpcListening = tcpProbe(grpcPort);
            }
            // 🔴 必须**真打 HTTP** —— 「端点注册了」不等于「抓得到」。
            if (scrapeBody == null && grpcListening) {
                HttpResponse<String> response = tryScrape(observationPort);
                if (response != null && response.statusCode() == 200) {
                    scrapeBody = response.body();
                }
            }
            if (scrapeBody != null && grpcListening) {
                break;
            }
            if (!process.isAlive()) {
                break;
            }
            Thread.sleep(500);
        }

        // ⚠️ 收摊前先等日志行出现（带上限）⇒ 否则 drain 线程可能还没读到那一行 ⇒ 断言偶发红。
        long logDeadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(30);
        while (System.currentTimeMillis() < logDeadline
                && process.isAlive()
                && !log.toString().contains(GRPC_STARTED_MARKER)) {
            Thread.sleep(200);
        }

        process.destroyForcibly();
        process.waitFor(30, TimeUnit.SECONDS);
        // ⚠️ 必须 join drain 线程：它可能还没把最后一段 stdout 读完
        //    ⇒ join 同时建立 happens-before（否则测试线程读到的 sink 是**残缺的**）。
        drain.join(TimeUnit.SECONDS.toMillis(10));
        String output = log.toString();

        assertTrue(grpcListening,
                "真 fat jar 起不来（gRPC 端口 " + grpcPort + " 没有在监听）—— 进程日志尾部：\n" + tail(output));
        assertTrue(output.contains(GRPC_STARTED_MARKER),
                "日志里没有 devh 的「" + GRPC_STARTED_MARKER + "」⇒ 不能算真 gRPC server 起来了。日志尾部：\n"
                        + tail(output));
        assertNotNull(scrapeBody,
                "观测端口 " + observationPort + " 上的 /actuator/prometheus **没有回 200** ⇒ "
                        + "Actuator 载体没生效（缺 spring-boot-starter-web？或 management 那三处没反转？）"
                        + "⇒ ⇒ 观测面整个不存在 ⇒ 与 Go 的 19091 那个面不一致。日志尾部：\n" + tail(output));
        assertTrue(scrapeBody.contains("# TYPE"),
                "抓到的内容里**没有任何指标族**（没有 `# TYPE` 行）⇒ 载体起来了但没吐指标。响应体前 500 字：\n"
                        + head(scrapeBody, 500));
        assertTrue(scrapeBody.contains("application=\"" + APPLICATION_TAG + "\""),
                "抓到的指标里没有公共标签 application=\"" + APPLICATION_TAG + "\" ⇒ "
                        + "抓取侧按标签过滤会一条都匹配不到。响应体前 500 字：\n" + head(scrapeBody, 500));
        assertTrue(output.contains(TOMCAT_STARTED_MARKER),
                "日志里没有「" + TOMCAT_STARTED_MARKER + "」⇒ 观测面不是跑在 servlet 容器上 ⇒ "
                        + "载体形态与预期不符。日志尾部：\n" + tail(output));
        assertFalse(output.contains("Application run failed"),
                "启动失败 ⇒ 载体副作用或配置有问题。日志尾部：\n" + tail(output));
        // ⚠️ 自动注册一旦生效，它注册的是 **web 容器端口**（= 观测端口）⇒ gRPC 链路会断。
        assertFalse(output.contains("NacosAutoServiceRegistration")
                        || output.contains("NacosServiceRegistry")
                        || output.contains("Registering service with Nacos"),
                "日志里有自动注册的痕迹 ⇒ `spring.cloud.nacos.discovery.register-enabled` 没被关掉 ⇒ "
                        + "自动注册会用**观测端口**去注册 ⇒ 同一个服务名下多一个错端口实例 ⇒ "
                        + "api 连过去只拿到 /actuator/** ⇒ gRPC 链路断。日志尾部：\n" + tail(output));
    }

    // ==================================================================
    // ② 配置级：产出的 jar 里，观测面默认值 = Go 的端口，且与业务面严格分离
    // ==================================================================

    @Test
    @DisplayName("产出的 fat jar 里：观测端口默认值必须一字不差 = Go 的 Prometheus.Port（19091），且与 gRPC 业务口分离")
    void shippedJarKeepsGoObservationPortAndSeparatesFaces() throws Exception {
        Path jar = fatJar();

        String observation = readJarProperty(jar, "server.port");
        String business = readJarProperty(jar, "grpc.server.port");

        assertNotNull(observation, "产物 jar 里读不到 `server.port` ⇒ 观测面没有配置");
        assertTrue(observation.contains(String.valueOf(GO_OBSERVATION_PORT)),
                "产物 jar 里的 `server.port` 必须是 " + GO_OBSERVATION_PORT
                        + "（= Go `etc/hot.yaml:10` 的 `Prometheus.Port`），实际 = " + observation
                        + " ⇒ 端口漂了就是新漂移（抓取侧会找不到服务）。"
                        + "⚠️ 允许用 env 覆盖（探针/部署要换口），但**默认值**必须一字不差。");
        assertNotEquals(String.valueOf(GO_BUSINESS_PORT), stripPortNumber(observation),
                "`server.port` 落到了 gRPC 的业务端口 " + GO_BUSINESS_PORT
                        + " ⇒ 两个面撞在一起 ⇒ Go 侧是两个独立端口（8083 / 19091）");
        assertNotNull(business, "产物 jar 里读不到 `grpc.server.port`");
        assertEquals(String.valueOf(GO_BUSINESS_PORT), stripPortNumber(business),
                "`grpc.server.port` 必须 = " + GO_BUSINESS_PORT
                        + "（= Go `etc/hot.yaml:2` 的 `ListenOn`），实际 = " + business);
        assertEquals("false", readJarProperty(jar, "spring.cloud.nacos.discovery.register-enabled"),
                "`spring.cloud.nacos.discovery.register-enabled` 必须显式为 false ⇒ "
                        + "自动注册会用**观测端口**注册 ⇒ gRPC 链路断");
        // ⚠️ 这三处是本模块 2026-10-30 之前把观测面「整个关死」的配置 ⇒ 必须已被反转。
        assertNotEquals("-1", readJarProperty(jar, "management.server.port"),
                "`management.server.port: -1` 会禁掉 management HTTP server ⇒ 观测面没有出口");
        assertNotEquals("false", readJarProperty(jar, "management.endpoints.enabled-by-default"),
                "`management.endpoints.enabled-by-default: false` 会让 health/prometheus 端点**压根不注册**");
        assertEquals("true", readJarProperty(jar, "management.endpoint.prometheus.enabled"),
                "`management.endpoint.prometheus.enabled` 必须为 true ⇒ 否则 /actuator/prometheus 不存在");
        assertEquals("true", readJarProperty(jar, "management.prometheus.metrics.export.enabled"),
                "`management.prometheus.metrics.export.enabled` 必须为 true ⇒ 否则没有 Prometheus 导出");
    }

    // ==================================================================
    // helpers
    // ==================================================================

    /** TCP 连通性探测：连得上 ⇒ 该端口有东西在监听（真 gRPC server 的证据）。 */
    private static boolean tcpProbe(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new java.net.InetSocketAddress("127.0.0.1", port), 2000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 真打一次 {@code /actuator/prometheus}；连不上返回 {@code null}（调用方继续等）。 */
    private static HttpResponse<String> tryScrape(int port) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3)).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + port + "/actuator/prometheus"))
                    .timeout(Duration.ofSeconds(5))
                    .GET().build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            return null;
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

    /** 日志尾部（失败时给人看的证据）。 */
    private static String tail(String text) {
        int max = 6000;
        return text.length() <= max
                ? text
                : "……（前 " + (text.length() - max) + " 字符已省略）……\n" + text.substring(text.length() - max);
    }

    /** 响应体前 N 字（失败时给人看的证据）。 */
    private static String head(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "……";
    }

    /**
     * 从配置值里取出**端口号**（env 覆盖是既有约定 ⇒ 默认值才是判据）。
     *
     * <p>🔴 <b>为什么不能只用「最后一个冒号后面那截」</b>：{@code ${ENV:default}} 形态里
     * 最后一截还带着<b>收尾的 {@code }}</b> ⇒ {@code ${HOT_RPC_GRPC_PORT:8083}} 会取出
     * {@code "8083}"} ⇒ 与 {@code "8083"} 比不相等 ⇒ <b>假红</b>
     * （本轮 2026-10-30 实测踩到：hot-rpc 的业务口是 env 形态，另两域是字面量）。
     * ⇒ ⇒ 这里先剥掉 {@code ${...}} 外壳再取冒号后的默认值。
     */
    private static String stripPortNumber(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.startsWith("${") && value.endsWith("}")) {
            int colon = value.lastIndexOf(':');
            if (colon > 0 && colon < value.length() - 1) {
                value = value.substring(colon + 1, value.length() - 1);
            }
        }
        return value;
    }

    /** 取本模块的 fat jar；缺失或比源码旧时重新构建（构建产物必须与当前源码一致）。 */
    private static Path fatJar() throws Exception {
        Path moduleDir = repoRoot().resolve(MODULE_PATH);
        Path jar = newestFatJar(moduleDir.resolve("target"));
        if (jar == null || isStale(jar, moduleDir)) {
            buildFatJar();
            jar = newestFatJar(moduleDir.resolve("target"));
        }
        assertNotNull(jar, "找不到本模块的 fat jar（" + moduleDir.resolve("target") + " 下没有 *.jar）");
        return jar;
    }

    private static Path newestFatJar(Path target) throws IOException {
        if (!Files.isDirectory(target)) {
            return null;
        }
        try (Stream<Path> files = Files.list(target)) {
            return files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .filter(p -> !p.getFileName().toString().endsWith(".jar.original"))
                    .filter(p -> !p.getFileName().toString().endsWith("-sources.jar"))
                    .filter(p -> !p.getFileName().toString().endsWith("-javadoc.jar"))
                    .max(Comparator.comparingLong(p -> p.toFile().lastModified()))
                    .orElse(null);
        }
    }

    /** jar 比 pom / 源码旧 ⇒ 构建产物是旧的 ⇒ 必须重建。 */
    private static boolean isStale(Path jar, Path moduleDir) throws IOException {
        long jarTime = jar.toFile().lastModified();
        List<Path> inputs = new ArrayList<>();
        inputs.add(moduleDir.resolve("pom.xml"));
        Path main = moduleDir.resolve("src").resolve("main");
        if (Files.isDirectory(main)) {
            try (Stream<Path> files = Files.walk(main)) {
                files.filter(Files::isRegularFile).forEach(inputs::add);
            }
        }
        // ⚠️ 共享层（service/common）改了而本模块没改时，本模块的 fat jar **不会**自动重建
        //    ⇒ fat jar 里内嵌的是旧的 common jar ⇒ ⇒ 共享层也要算进「新鲜度」。
        Path sharedLayer = repoRoot().resolve("service/common");
        inputs.add(sharedLayer.resolve("pom.xml"));
        Path sharedMain = sharedLayer.resolve("src").resolve("main");
        if (Files.isDirectory(sharedMain)) {
            try (Stream<Path> files = Files.walk(sharedMain)) {
                files.filter(Files::isRegularFile).forEach(inputs::add);
            }
        }
        for (Path input : inputs) {
            if (Files.exists(input) && Files.getLastModifiedTime(input).toMillis() > jarTime) {
                return true;
            }
        }
        return false;
    }

    private static void buildFatJar() throws Exception {
        Process process = new ProcessBuilder(List.of(
                "mvn", "-o", "-q", "package", "-Dmaven.test.skip=true", "-am", "-pl", MODULE_PATH))
                .directory(repoRoot().toFile())
                .redirectErrorStream(true)
                .start();
        String log = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = process.waitFor();
        assertEquals(0, exit, "构建 fat jar 失败（mvn package）：\n" + tail(log));
    }

    /** 向上找仓库根（要求祖先里有本模块目录）。 */
    private static Path repoRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 8 && dir != null; i++) {
            if (Files.exists(dir.resolve("pom.xml")) && Files.isDirectory(dir.resolve(MODULE_PATH))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("找不到仓库根（要求祖先里有 " + MODULE_PATH + "）");
    }

    /**
     * 读<b>产物 jar 里</b>打包的那份 {@code application.yml} 的某个键。
     *
     * <p>🔴 <b>为什么读 jar 而不是读源码树</b>：源码树是「打算发布的东西」，
     * jar 里那份才是「**真的会被发布的东西**」⇒ ⇒ 后者才是判据。
     */
    private static String readJarProperty(Path jar, String key) throws IOException {
        Path extracted = Files.createTempFile("hot-rpc-application", ".yml");
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            var entry = zip.getEntry("BOOT-INF/classes/application.yml");
            assertNotNull(entry, "产物 jar 里没有 BOOT-INF/classes/application.yml ⇒ 不是可部署的 fat jar");
            try (InputStream in = zip.getInputStream(entry)) {
                Files.copy(in, extracted, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
        try {
            List<PropertySource<?>> sources =
                    new YamlPropertySourceLoader().load("application.yml", new FileSystemResource(extracted));
            for (PropertySource<?> source : sources) {
                if (!(source instanceof EnumerablePropertySource<?> enumerable)) {
                    continue;
                }
                for (String name : enumerable.getPropertyNames()) {
                    if (name.equals(key)) {
                        Object value = source.getProperty(name);
                        return value == null ? null : value.toString();
                    }
                }
            }
            return null;
        } finally {
            Files.deleteIfExists(extracted);
        }
    }
}
