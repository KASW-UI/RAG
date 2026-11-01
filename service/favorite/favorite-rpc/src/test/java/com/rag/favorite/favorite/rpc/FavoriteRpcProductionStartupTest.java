package com.rag.favorite.favorite.rpc;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 🔴🔴 <b>生产启动形态的守门测试</b> —— 锁住「本模块的 web 形态与观测端口」在<b>生产配置</b>里的落点。
 *
 * <h2>⚠️ 本类的断言在 2026-10-30 被<b>反转</b>过一次（AGENTS.md §3.3 ③）</h2>
 * <p><b>原断言</b>：「生产 yaml <b>必须</b>显式声明 {@code web-application-type: none}」
 * ⇒ 理由是「classpath 上有 jakarta.servlet ⇒ Boot 判定本进程是 SERVLET web 应用
 * ⇒ 而本服务没有 servlet 容器 ⇒ 启动期报
 * 「no ServletWebServerFactory bean defined」⇒ <b>真 jar 起不来</b>」。
 *
 * <p><b>反转原因</b>：本轮（2026-10-30）补了 <b>Actuator 的 servlet 载体</b>
 * ⇒ pom 加 {@code spring-boot-starter-web} ⇒ <b>容器有了</b> ⇒ 启动期不再缺
 * {@code ServletWebServerFactory} ⇒ ⇒ 而 {@code web-application-type: none}
 * 会<b>把刚补上的载体再关掉</b> ⇒ ⇒ 那条断言的**前提已不复存在**
 * ⇒ ⇒ 断言必须跟着改（否则就是 AGENTS.md §3.3 ③ 说的「拿旧断言挡住新修复」）。
 *
 * <h2>现在守的是三件事</h2>
 * <ol>
 *   <li><b>载体没被关掉</b>：生产 yaml <b>不得</b>再出现 {@code web-application-type: none}
 *       （出现 ⇒ Actuator 没出口 ⇒ 16062 那个面不存在 ⇒ 与 Go 不一致）；</li>
 *   <li><b>观测端口一字不差</b>：生产 yaml 的 {@code server.port} 默认值必须 = <b>16062</b>
 *       （= Go 的 {@code DevServer.Port}）⇒ 端口漂了就是新漂移；</li>
 *   <li><b>业务面没被挪走</b>：{@code grpc.server.port} 必须仍是 <b>8082</b>
 *       （= Go 的 {@code ListenOn}）⇒ 观测端口与业务端口<b>严格分离</b>。</li>
 * </ol>
 *
 * <h2>为什么验的是「配置项」而不是「行为」</h2>
 * <p>行为级证据在
 * {@code FavoriteRpcPrometheusCarrierGateTest} —— 它用<b>真 fat jar</b> 起一次进程，
 * 真打 {@code /actuator/prometheus}，并断言 gRPC 端口同时在监听。
 * 本类是那道**行为证据的廉价代理**：它在<b>不启动进程</b>的前提下，
 * 挡住「有人把 {@code web-application-type} / {@code server.port} 删掉或改掉」这类回归
 * —— 而那种回归会让<b>观测面整个消失</b>（且不会有任何报错）。
 *
 * <h2>Go 出处与语义等价论证</h2>
 * <p><b>Go 出处</b>：{@code service/favorite/rpc/etc/favorite.yaml:2}
 * {@code ListenOn: 0.0.0.0:8082}（业务面）＋ {@code :23-30}
 * {@code DevServer: {Enabled: true, Host: 0.0.0.0, Port: 16062, MetricsPath: /metrics,
 * HealthPath: /healthz, EnableMetrics: true, EnablePprof: true}}（观测面），
 * 判据在 {@code go-zero@v1.10.2/internal/devserver/server.go:79-82}
 * 的 {@code if !c.Enabled { return }} ⇒ <b>与 mode 无关，生产也开</b>。
 * <p><b>语义等价论证</b>：两侧的<b>业务面</b>都是纯 gRPC（8082）⇒ 一致；
 * 两侧的<b>观测面</b>都在<b>独立端口</b>上 ⇒ 一致（端口 16062 一字不差）；
 * ⚠️ <b>载体与路径不同</b>（Go = go-zero devserver 的 {@code /metrics}；
 * RAG = Tomcat 上的 {@code /actuator/prometheus}）⇒ 属「暴露形态差异」，
 * 按 {@code twoStage.md} §10 登记，<b>不与本条混谈</b>。
 */
class FavoriteRpcProductionStartupTest {

    /** 本模块目录名 —— 用于在源码树里定位本模块自己的 application.yml。 */
    private static final String MODULE_DIR = "favorite-rpc";

    /** Go 的观测端口（{@code etc/favorite.yaml:26} {@code DevServer.Port}）。 */
    private static final String GO_OBSERVATION_PORT = "16062";

    /** Go 的业务端口（{@code etc/favorite.yaml:2} {@code ListenOn: 0.0.0.0:8082}）。 */
    private static final String GO_BUSINESS_PORT = "8082";

    @Test
    @DisplayName("生产 application.yml 不得再把 web 类型钉成 none（否则刚补的 Actuator 载体被关掉 ⇒ 16062 那个面没有出口）")
    void productionDoesNotDisableTheWebApplicationType() throws IOException {
        // 🔴 **2026-10-30 修正**：本条反转后的语义是「**不得有 none**」⇒ ⇒ **键缺失是期望状态**
        //    ⇒ ⇒ 这里必须用**可选**读取器（缺失 ⇒ null）⇒
        //    ⇒ ⚠️ 用「键缺失就 fail」的读取器 ⇒ 会把**正确**的实现判成红
        //    ⇒ ⇒ 这正是 AGENTS.md §3.3 ③「先改/删错误断言，再改实现」的一个实例：
        //    ⇒ ⇒ 本轮实现是对的（键已删），错的是断言的读取器。
        String actual = readOptionalProperty(locateModuleYml(), "spring.main.web-application-type");

        assertNotEquals("none", actual,
                "生产 yaml 里仍有 `spring.main: web-application-type: none` —— "
                        + "2026-10-30 补了 `spring-boot-starter-web` 之后，容器已经有了，"
                        + "而这行会把 **Actuator 的 servlet 载体整个关掉** ⇒ "
                        + "`/actuator/prometheus` 没有出口 ⇒ **与 Go 的 16062 观测面不一致**。"
                        + "⚠️ 这行原先是「真 jar 起不来」的缓解手段"
                        + "（classpath 上有 jakarta.servlet 但没有 Tomcat），"
                        + "补上 starter 后根因已消 ⇒ 缓解手段必须一起撤。");
    }

    @Test
    @DisplayName("生产 application.yml 的观测端口必须一字不差 = Go 的 DevServer.Port（16062）")
    void productionObservationPortMatchesGo() throws IOException {
        String actual = readProperty(locateModuleYml(), "server.port");

        assertTrue(actual != null && actual.contains(GO_OBSERVATION_PORT),
                "生产 yaml 的 `server.port` 必须是 " + GO_OBSERVATION_PORT
                        + "（= Go `etc/favorite.yaml:26` 的 `DevServer.Port`），实际 = " + actual
                        + " ⇒ 端口漂了就是新漂移（抓取侧会找不到服务）。"
                        + "⚠️ 允许用 env 覆盖（探针/部署要换口），但**默认值**必须一字不差。");
    }

    @Test
    @DisplayName("观测端口与 gRPC 业务端口必须严格分离（16062 ≠ 8082）")
    void observationPortIsSeparatedFromBusinessPort() throws IOException {
        String observation = readProperty(locateModuleYml(), "server.port");
        String business = readProperty(locateModuleYml(), "grpc.server.port");

        assertTrue(observation != null && business != null,
                "生产 yaml 缺 `server.port` 或 `grpc.server.port` ⇒ 读不到两个面");
        assertNotEquals(GO_BUSINESS_PORT, stripPortNumber(observation),
                "`server.port` 落到了 gRPC 的业务端口 " + GO_BUSINESS_PORT
                        + " ⇒ 两个面撞在一起 ⇒ Go 侧是两个独立端口（8082 / 16062）");
        assertTrue(stripPortNumber(business).equals(GO_BUSINESS_PORT),
                "`grpc.server.port` 必须 = " + GO_BUSINESS_PORT
                        + "（= Go `etc/favorite.yaml:2` 的 `ListenOn`），实际 = " + business);
    }

    @Test
    @DisplayName("target/classes 里打包的 application.yml 必须与源码树一致（防止「构建产物是旧的」）")
    void packagedCopyMatchesSourceTree() throws IOException {
        Path source = locateModuleYml();
        Path packaged = source.getParent().getParent().getParent().getParent()
                .resolve("target").resolve("classes").resolve("application.yml");
        Assumptions.assumeTrue(Files.exists(packaged),
                "target/classes 里还没有 application.yml（本次没跑过构建）⇒ 跳过交叉核对");

        assertEquals(readOptionalProperty(source, "spring.main.web-application-type"),
                readOptionalProperty(packaged, "spring.main.web-application-type"),
                "target/classes 里打包的 application.yml 与源码树不一致 —— "
                        + "说明上一次构建产物是旧的，测试结论不可信");
        assertEquals(readProperty(source, "server.port"),
                readProperty(packaged, "server.port"),
                "target/classes 里打包的 application.yml 与源码树不一致（server.port）—— "
                        + "说明上一次构建产物是旧的，测试结论不可信");
    }

    // ==================================================================
    // 取生产配置
    // ==================================================================

    /** 从 {@code ${ENV:default}} 形态里取出**默认端口**（env 覆盖是既有约定）。 */
    private static String stripPortNumber(String raw) {
        if (raw == null) {
            return null;
        }
        // ⚠️ **2026-11-01 · Round 7 修**：原实现是 `raw.lastIndexOf(':')` ⇒ 对
        //    `${FAVORITE_RPC_PORT:8082}` 返回 **"8082}"**（带右花括号）⇒ 与 "8082" 不相等
        //    ⇒ **本门禁被 Round 1 的端口 env 化打红**（值是对的，**是解析器读不懂占位形态**）。
        //    ⇒ 本方法的 javadoc 本就声明「从 `${ENV:default}` 形态里取出默认端口」⇒ **实现与声明不符**。
        //    ⇒ 改成与其余三份同族 helper（article-rpc / favorite-rpc 的 PrometheusCarrierGateTest /
        //    hot-rpc 的 PrometheusCarrierGateTest）**逐字一致**的实现。
        //    ⇒ **断言语义不变**：仍然要求「默认值一字不差 = Go 的 ListenOn」。
        String value = raw.trim();
        if (value.startsWith("${") && value.endsWith("}")) {
            int colon = value.lastIndexOf(':');
            if (colon > 0 && colon < value.length() - 1) {
                value = value.substring(colon + 1, value.length() - 1);
            }
        }
        return value;
    }

    private static String readProperty(Path yml, String key) throws IOException {
        String value = readOptionalProperty(yml, key);
        if (value == null) {
            fail("生产 application.yml 里没有 " + key + " ⇒ " + yml);
        }
        return value;
    }

    /**
     * <b>可选</b>读取：键<b>缺失</b>时返回 {@code null}（不 fail）。
     *
     * <p>🔴 <b>为什么需要它</b>（2026-10-30 实测踩到）：本类的
     * {@code productionDoesNotDisableTheWebApplicationType} 语义是「<b>不得</b>有
     * {@code spring.main.web-application-type: none}」⇒ ⇒ <b>键缺失才是期望状态</b>
     * ⇒ ⇒ 用「键缺失就 fail」的读取器 ⇒ 会把<b>正确</b>的实现判成红
     * ⇒ ⇒ 断言「不得为 X」与「必须为 Y」要用<b>不同的读取语义</b>。
     */
    private static String readOptionalProperty(Path yml, String key) throws IOException {
        List<PropertySource<?>> sources =
                new YamlPropertySourceLoader().load("application.yml", new FileSystemResource(yml));
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
    }

    /**
     * 向上找本模块的 {@code src/main/resources/application.yml}。
     *
     * <p>Surefire 的工作目录默认是模块目录（{@code ${basedir}}），
     * 但从仓库根跑 {@code mvn -pl} 时也可能不同 ⇒ 逐级向上找，
     * 且要求命中路径里带本模块名，避免误取别的模块的 application.yml。
     */
    private static Path locateModuleYml() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 8 && dir != null; i++) {
            Path candidate = dir.resolve("src").resolve("main").resolve("resources")
                    .resolve("application.yml");
            if (Files.exists(candidate) && dir.toString().contains(MODULE_DIR)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException(
                "找不到本模块的 src/main/resources/application.yml（工作目录=" + Paths.get("").toAbsolutePath() + "）");
    }
}
