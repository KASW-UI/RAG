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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 🔴🔴 <b>生产启动形态的守门测试</b> —— 锁住「纯 gRPC 进程」这件事在<b>生产配置</b>里的落点。
 *
 * <h2>它挡的是什么（2026-10-26 第十七轮实测）</h2>
 * <p>本模块的生产 jar **起不来**：
 * <pre>
 * APPLICATION FAILED TO START
 * Description:
 *   Web application could not be started as there was no
 *   org.springframework.boot.web.server.servlet.ServletWebServerFactory bean
 *   defined in the context.
 * </pre>
 * <p><b>根因</b>：classpath 上有 {@code jakarta.servlet}（pom 里为编译期引用
 * {@code jakarta.servlet.http.HttpServlet/Request/Response} 而显式声明了
 * {@code jakarta.servlet:jakarta.servlet-api}）⇒ Spring Boot 的
 * {@code WebApplicationType.deduceFromClasspath()} 判定本进程是 <b>SERVLET web 应用</b>
 * ⇒ 而本服务<b>没有</b> servlet 容器（不引 tomcat）⇒ 找不到 {@code ServletWebServerFactory}。
 *
 * <h2>为什么原有的测试都没发现</h2>
 * <p>它们一律用 {@code @SpringBootTest(webEnvironment = NONE)}
 * ⇒ <b>测试环境自己把 web 类型钉成了 NONE</b> ⇒ <b>生产配置从未被跑过</b>。
 * ⇒ 这正是 AGENTS.md §3.3 说的「<b>跑绿 ≠ 对</b>」：
 * 「测试全绿」与「服务能启动」之间隔着一个配置文件。
 * ⇒ 同型问题在 **PIT-FAV-RPC-018**（「{@code @SpringBootTest} 会把『模块能不能真起』整个掩盖掉」）
 * 已经记过一次 ⇒ 本类是把那条教训**落成机器可复跑的断言**。
 *
 * <h2>本类验的是<b>生产文件</b></h2>
 * <p>断言直接读 {@code src/main/resources/application.yml}
 * （并单独核对 {@code target/classes} 里打包进去的那份是否一致），
 * <b>不经过任何 {@code @SpringBootTest} 覆盖</b>。
 *
 * <h2>为什么断言的是「配置项」而不是「行为」</h2>
 * <p>行为级的证据在
 * {@code tools/prod-startup-probe/favorite/run.sh}
 * —— 它用<b>真 fat jar</b> + <b>生产 yaml 原样</b>（不传任何 {@code --grpc.client.*} 覆盖）
 * 真起一次进程（并做 A/B：把地址改回两斜杠 ⇒ 必须炸）。
 * <p>本类是那道**行为证据的廉价代理**：它在<b>不启动进程</b>的前提下，
 * 挡住「有人把 {@code spring.main.web-application-type} 删掉/改掉」这类回归
 * —— 而那种回归会让**生产 jar 直接起不来**。
 *
 * <h2>Go 出处与语义等价论证</h2>
 * <p><b>Go 出处</b>：{@code service/favorite/rpc/etc/favorite.yaml:2}
 * {@code ListenOn: 0.0.0.0:8082} —— Go 侧 favorite-rpc 的<b>业务面</b>只有 gRPC 8082；
 * 另有 go-zero 内置的 DevServer（{@code etc/favorite.yaml:23-30} → 0.0.0.0:16062 的
 * {@code /metrics} / {@code /healthz} / pprof，判据
 * {@code internal/devserver/server.go:79-82} 的 {@code if !c.Enabled { return }}
 * ⇒ <b>与 mode 无关，生产也开</b>），那是 go-zero <b>自带</b>的 HTTP server，
 * <b>不依赖 servlet 容器</b>。
 * <p><b>语义等价论证</b>：两侧的「业务面」都是<b>纯 gRPC</b>，RAG 侧用
 * {@code web-application-type: none} 显式表达这一点 ⇒ 行为同构。
 * ⚠️ <b>但</b>：RAG 侧因此<b>没有</b> 16062 那个面 ⇒
 * <b>这本身是一条与 Go 的偏离</b>（部署形态类）⇒ 已单独登记为域内未决项 <b>U9</b>
 * （待拍板）⇒ <b>不与本条混谈</b>：本条只保证「<b>能起来</b>」。
 */
class FavoriteRpcProductionStartupTest {

    /** 本模块目录名 —— 用于在源码树里定位本模块自己的 application.yml。 */
    private static final String MODULE_DIR = "favorite-rpc";

    /** 期望的生产 web 应用类型。 */
    private static final String EXPECTED_WEB_APPLICATION_TYPE = "none";

    @Test
    @DisplayName("生产 application.yml 必须显式声明 web-application-type: none（否则真 fat jar 起不来）")
    void productionDeclaresNonWebApplicationType() throws IOException {
        String actual = readProperty(locateModuleYml(), "spring.main.web-application-type");

        assertEquals(EXPECTED_WEB_APPLICATION_TYPE, actual,
                "生产 yaml 必须写 `spring.main: web-application-type: none` —— "
                        + "classpath 上有 jakarta.servlet（pom 里为编译期引用显式声明的）"
                        + "⇒ Boot 会判定本进程是 SERVLET web app ⇒ 没有 servlet 容器 ⇒ "
                        + "启动期报「no ServletWebServerFactory bean defined」⇒ **真 jar 起不来**。"
                        + "⚠️ 与 @SpringBootTest(webEnvironment = NONE) 无关 —— "
                        + "那正是它掩盖了生产配置的原因（见 PIT-FAV-RPC-018）。");
    }

    @Test
    @DisplayName("target/classes 里打包的 application.yml 必须与源码树一致（防止「构建产物是旧的」）")
    void packagedCopyMatchesSourceTree() throws IOException {
        Path source = locateModuleYml();
        Path packaged = source.getParent().getParent().getParent().getParent()
                .resolve("target").resolve("classes").resolve("application.yml");
        Assumptions.assumeTrue(Files.exists(packaged),
                "target/classes 里还没有 application.yml（本次没跑过构建）⇒ 跳过交叉核对");

        assertEquals(readProperty(source, "spring.main.web-application-type"),
                readProperty(packaged, "spring.main.web-application-type"),
                "target/classes 里打包的 application.yml 与源码树不一致 —— "
                        + "说明上一次构建产物是旧的，测试结论不可信");
    }

    // ==================================================================
    // 取生产配置
    // ==================================================================

    private static String readProperty(Path yml, String key) throws IOException {
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
        fail("生产 application.yml 里没有 " + key + " ⇒ " + yml);
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
